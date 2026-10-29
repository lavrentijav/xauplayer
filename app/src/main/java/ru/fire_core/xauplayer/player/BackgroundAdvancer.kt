package ru.fire_core.xauplayer.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.cache.CoverManager
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import ru.fire_core.xauplayer.data.local.entities.Book
import ru.fire_core.xauplayer.data.local.entities.Chapter
import ru.fire_core.xauplayer.data.local.entities.Progress
import ru.fire_core.xauplayer.data.network.StreamUrlResolver
import ru.fire_core.xauplayer.domain.repo.BooksRepository
import ru.fire_core.xauplayer.domain.usecase.progress.SaveProgressUseCase
import ru.fire_core.xauplayer.download.DownloadManager
import java.io.File
import javax.inject.Inject

/**
 * Управление главами, когда экрана (PlayerViewModel) уже нет.
 *
 * Плеер и сервис живут дольше активности: если приложение смахнули или UI выгрузили, а звук идёт,
 * переходить между главами, повторять сбойный поток и сохранять прогресс некому. Этот класс делает это
 * из сервиса и работает ТОЛЬКО когда ViewModel нет ([PlayerHolder.chapterNavigation] == null).
 * Какая глава играет — берём из mediaId («xau:<book>:<chapter>», ставит [PlayerHolder.prepare]).
 *
 * Что умеет: автопереход в конце главы (в т.ч. на следующую книгу серии), кнопки «следующая/предыдущая»
 * из уведомления ([fallbackNavigation]), повтор потока после ошибки, периодическое сохранение прогресса.
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
    private var switchJob: Job? = null
    private var retryJob: Job? = null
    private var progressJob: Job? = null
    private var lastAdvancedFrom: String? = null
    private var retryCount = 0

    private val headless: Boolean get() = holder.chapterNavigation == null

    private fun currentMediaId(): String? =
        holder.player().currentMediaItem?.mediaId?.takeIf { it.startsWith(MEDIA_ID_PREFIX) }

    /** Кнопки «следующая/предыдущая» из уведомления, когда ViewModel нет. */
    val fallbackNavigation = object : ChapterNavigation {
        override fun hasNext() = currentMediaId() != null
        override fun hasPrevious() = currentMediaId() != null
        override fun next() = switch(+1, userInitiated = true)
        override fun previous() {
            // Как в плеерах: если глава идёт больше 3 секунд — в её начало, иначе на предыдущую
            if (holder.player().currentPosition >= 3000L) holder.player().seekTo(0L)
            else switch(-1, userInitiated = true)
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> retryCount = 0
                Player.STATE_ENDED -> {
                    if (!headless) return
                    val mediaId = currentMediaId() ?: return
                    if (mediaId == lastAdvancedFrom) return // повторное событие для той же главы
                    lastAdvancedFrom = mediaId
                    switch(+1, userInitiated = false)
                }
                // Вышли из ENDED (перемотали, переключили главу, перезапустили ту же) — дальше снова можно переходить
                else -> lastAdvancedFrom = null
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!headless || currentMediaId() == null) return
            retryAfterError(error)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && headless) startProgressTicker() else stopProgressTicker(save = headless)
        }
    }

    fun attach() = holder.player().addListener(listener)

    fun detach() {
        holder.player().removeListener(listener)
        scope.cancel()
    }

    // --- переходы ---------------------------------------------------------------------------

    private fun switch(delta: Int, userInitiated: Boolean) {
        val mediaId = currentMediaId() ?: return
        switchJob?.cancel()
        switchJob = scope.launch {
            try {
                val ok = switchChapter(mediaId, delta)
                if (!ok && userInitiated && delta < 0) holder.player().seekTo(0L)
                if (!ok) lastAdvancedFrom = null // можно повторить при следующем событии
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastAdvancedFrom = null // сбой не должен навсегда блокировать повтор
                logger.error("BackgroundAdvancer", "Переход на главу (delta=$delta) не удался", e)
            }
        }
    }

    /** Переключает главу на соседнюю. false — переходить некуда или картина изменилась по ходу. */
    private suspend fun switchChapter(mediaId: String, delta: Int): Boolean {
        val (bookId, chapterId) = parse(mediaId) ?: return false

        var chapters = booksRepository.getChapters(bookId)
        var index = chapters.indexOfFirst { it.id == chapterId }
        if (index < 0) {
            // Кэш глав мог устареть — обновляем с сервера и читаем заново
            booksRepository.syncChapters(bookId, force = true)
            chapters = booksRepository.getChapters(bookId)
            index = chapters.indexOfFirst { it.id == chapterId }
        }
        if (index < 0) return false

        val books = booksRepository.getBooks()
        val book = books.firstOrNull { it.id == bookId } ?: return false

        var targetBook: Book = book
        var target: Chapter? = chapters.getOrNull(index + delta)
        if (target == null && delta > 0) {
            // Конец книги: следующая книга серии
            nextBookInSeries(book, books)?.let { nextBook ->
                var nextChapters = booksRepository.getChapters(nextBook.id)
                if (nextChapters.isEmpty()) {
                    booksRepository.syncChapters(nextBook.id, force = true)
                    nextChapters = booksRepository.getChapters(nextBook.id)
                }
                nextChapters.firstOrNull()?.let { targetBook = nextBook; target = it }
            }
        }
        val chapter = target ?: run {
            logger.info("BackgroundAdvancer", "После главы $chapterId переходить некуда")
            return false
        }
        return startChapter(mediaId, targetBook, chapter)
    }

    private fun nextBookInSeries(book: Book, all: List<Book>): Book? {
        val seriesId = book.seriesId ?: return null
        val series = all.filter { it.seriesId == seriesId }
            .sortedWith(compareBy({ it.seriesOrder ?: Int.MAX_VALUE }, { it.id }))
        val i = series.indexOfFirst { it.id == book.id }
        return if (i >= 0) series.getOrNull(i + 1) else null
    }

    private suspend fun resolveUrl(bookId: Long, chapterId: Long): String {
        // Только полностью скачанная глава; недокачанную играем потоком
        val local = downloadManager.getDownloadedChapter(chapterId)
            ?.takeIf { it.isComplete }?.let { File(it.localPath) }?.takeIf { it.exists() }
        return local?.let { android.net.Uri.fromFile(it).toString() } ?: streamUrlResolver.resolve(bookId, chapterId)
    }

    /** Все await'ы позади — перед подменой проверяем, что пользователь/экран не вмешались. */
    private fun stillCurrent(mediaId: String) = headless && currentMediaId() == mediaId

    private suspend fun startChapter(fromMediaId: String, book: Book, chapter: Chapter): Boolean {
        val url = resolveUrl(book.id, chapter.id)
        val cover = try { coverManager.getBookCoverUrlForDisplay(book.id) } catch (e: Exception) { null }
        val cacheKey = streamUrlResolver.cacheKey(book.id, chapter.id)
        if (!stillCurrent(fromMediaId)) {
            logger.info("BackgroundAdvancer", "Переход на главу ${chapter.id} отменён: выбор изменился")
            return false
        }
        logger.info("BackgroundAdvancer", "Фон: переходим на главу ${chapter.id} «${chapter.title}» (книга ${book.id})")
        holder.prepare(
            url = url,
            title = chapter.title,
            artist = "${book.title} - ${book.author}",
            coverUrl = cover,
            cacheKey = cacheKey,
            bookId = book.id,
            chapterId = chapter.id
        )
        holder.player().playWhenReady = true
        lastAdvancedFrom = null
        retryCount = 0
        saveProgressNow(book.id, chapter.id, 0L)
        settingsStore.setLastBookId(book.id)
        return true
    }

    // --- повтор потока после ошибки ----------------------------------------------------------

    private fun retryAfterError(error: PlaybackException) {
        val mediaId = currentMediaId() ?: return
        val (bookId, chapterId) = parse(mediaId) ?: return
        if (retryCount >= RETRY_DELAYS_MS.size) {
            logger.error("BackgroundAdvancer", "Поток главы $chapterId не открылся после $retryCount повторов: ${error.errorCodeName}")
            return
        }
        val delayMs = RETRY_DELAYS_MS[retryCount++]
        val resumeAt = holder.player().currentPosition.takeIf { it > 0 } ?: 0L
        retryJob?.cancel()
        retryJob = scope.launch {
            try {
                delay(delayMs)
                if (!stillCurrent(mediaId)) return@launch
                val book = booksRepository.getBooks().firstOrNull { it.id == bookId } ?: return@launch
                val chapter = booksRepository.getChapters(bookId).firstOrNull { it.id == chapterId } ?: return@launch
                val url = resolveUrl(bookId, chapterId) // ссылка могла протухнуть — берём свежую
                if (!stillCurrent(mediaId)) return@launch
                logger.info("BackgroundAdvancer", "Повтор потока главы $chapterId (попытка $retryCount), позиция $resumeAt мс")
                holder.prepare(
                    url = url,
                    title = chapter.title,
                    artist = "${book.title} - ${book.author}",
                    coverUrl = try { coverManager.getBookCoverUrlForDisplay(bookId) } catch (e: Exception) { null },
                    cacheKey = streamUrlResolver.cacheKey(bookId, chapterId),
                    bookId = bookId,
                    chapterId = chapterId
                )
                if (resumeAt > 0) holder.player().seekTo(resumeAt)
                holder.player().playWhenReady = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("BackgroundAdvancer", "Повтор потока не удался", e)
            }
        }
    }

    // --- прогресс в фоне ---------------------------------------------------------------------

    private fun startProgressTicker() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                if (!headless) break
                saveCurrentProgress()
            }
        }
    }

    private fun stopProgressTicker(save: Boolean) {
        progressJob?.cancel()
        progressJob = null
        if (save) scope.launch { saveCurrentProgress() }
    }

    private suspend fun saveCurrentProgress() {
        val mediaId = currentMediaId() ?: return
        val (bookId, chapterId) = parse(mediaId) ?: return
        val pos = holder.player().currentPosition
        if (pos >= 0) saveProgressNow(bookId, chapterId, pos)
    }

    private suspend fun saveProgressNow(bookId: Long, chapterId: Long, positionMs: Long) {
        try {
            saveProgress(
                Progress(
                    bookId = bookId,
                    chapterId = chapterId,
                    positionMs = positionMs,
                    speed = holder.player().playbackParameters.speed,
                    lastUpdate = java.time.Instant.now().toEpochMilli()
                )
            )
        } catch (e: Exception) {
            logger.warn("BackgroundAdvancer", "Не удалось сохранить прогресс в фоне", e)
        }
    }

    private fun parse(mediaId: String): Pair<Long, Long>? {
        val parts = mediaId.removePrefix(MEDIA_ID_PREFIX).split(":")
        val bookId = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val chapterId = parts.getOrNull(1)?.toLongOrNull() ?: return null
        return bookId to chapterId
    }

    private companion object {
        val RETRY_DELAYS_MS = longArrayOf(2_000L, 5_000L, 10_000L)
        const val PROGRESS_INTERVAL_MS = 15_000L
    }
}
