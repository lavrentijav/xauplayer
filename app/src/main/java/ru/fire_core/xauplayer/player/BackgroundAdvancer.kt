package ru.fire_core.xauplayer.player

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.cache.CoverManager
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import ru.fire_core.xauplayer.data.local.entities.Progress
import ru.fire_core.xauplayer.data.network.StreamUrlResolver
import ru.fire_core.xauplayer.domain.repo.BooksRepository
import ru.fire_core.xauplayer.domain.usecase.progress.SaveProgressUseCase
import ru.fire_core.xauplayer.download.DownloadManager
import java.io.File
import javax.inject.Inject

/**
 * Автопереход на следующую главу, когда экрана (PlayerViewModel) уже нет.
 *
 * Обычно переход делает ViewModel. Но плеер и сервис живут дольше активности: если приложение смахнули
 * или систему выгрузила UI, а звук продолжает идти, в конце главы переходить некому — воспроизведение
 * молча останавливалось. Этот класс слушает общий плеер из сервиса и срабатывает ТОЛЬКО когда
 * ViewModel нет ([PlayerHolder.chapterNavigation] == null). Какая глава играет, берём из mediaId
 * («xau:<book>:<chapter>», его ставит [PlayerHolder.prepare]).
 */
@UnstableApi
class BackgroundAdvancer @Inject constructor(
    private val holder: PlayerHolder,
    private val booksRepository: BooksRepository,
    private val downloadManager: DownloadManager,
    private val streamUrlResolver: StreamUrlResolver,
    private val coverManager: CoverManager,
    private val saveProgress: SaveProgressUseCase,
    private val settingsStore: SettingsStore,
    private val logger: AppLogger
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var advancing = false
    private var lastAdvancedFrom: String? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            if (holder.chapterNavigation != null) return // экран жив — переход делает он
            val mediaId = holder.player().currentMediaItem?.mediaId ?: return
            if (!mediaId.startsWith(MEDIA_ID_PREFIX) || mediaId == lastAdvancedFrom || advancing) return
            lastAdvancedFrom = mediaId
            advancing = true
            scope.launch {
                try {
                    advance(mediaId)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("BackgroundAdvancer", "Автопереход в фоне не удался", e)
                } finally {
                    advancing = false
                }
            }
        }
    }

    fun attach() = holder.player().addListener(listener)

    fun detach() {
        holder.player().removeListener(listener)
        scope.cancel()
    }

    private suspend fun advance(mediaId: String) {
        val parts = mediaId.removePrefix(MEDIA_ID_PREFIX).split(":")
        val bookId = parts.getOrNull(0)?.toLongOrNull() ?: return
        val chapterId = parts.getOrNull(1)?.toLongOrNull() ?: return

        val chapters = booksRepository.getChapters(bookId)
        val index = chapters.indexOfFirst { it.id == chapterId }
        if (index < 0 || index >= chapters.size - 1) {
            logger.info("BackgroundAdvancer", "Глава $chapterId последняя в книге $bookId — переход не нужен")
            return
        }
        val next = chapters[index + 1]
        val book = booksRepository.getBooks().firstOrNull { it.id == bookId } ?: return

        val local = downloadManager.getDownloadedChapter(next.id)
            ?.let { File(it.localPath) }?.takeIf { it.exists() }
        val url = local?.let { android.net.Uri.fromFile(it).toString() }
            ?: streamUrlResolver.resolve(bookId, next.id)
        val cover = try { coverManager.getBookCoverUrlForDisplay(bookId) } catch (e: Exception) { null }

        logger.info("BackgroundAdvancer", "Фон: переходим на главу ${next.id} «${next.title}» (книга $bookId)")
        holder.prepare(
            url = url,
            title = next.title,
            artist = "${book.title} - ${book.author}",
            coverUrl = cover,
            cacheKey = streamUrlResolver.cacheKey(bookId, next.id),
            bookId = bookId,
            chapterId = next.id
        )
        holder.player().playWhenReady = true

        // Запоминаем, где остановились: после возврата в приложение откроется эта глава
        saveProgress(
            Progress(
                bookId = bookId,
                chapterId = next.id,
                positionMs = 0L,
                speed = holder.player().playbackParameters.speed,
                lastUpdate = java.time.Instant.now().toEpochMilli()
            )
        )
        settingsStore.setLastBookId(bookId)
    }
}
