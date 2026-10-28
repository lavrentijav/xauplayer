package ru.fire_core.xauplayer.data.cache

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import ru.fire_core.xauplayer.data.local.AppDatabase
import ru.fire_core.xauplayer.player.PlayerHolder
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единая точка управления кэшем.
 *
 * Все кэши лежат в `cacheDir`, поэтому «Очистить кэш» в настройках ОС их сбрасывает. Кэш библиотеки
 * (книги, серии, главы) живёт в Room, который ОС не чистит, — для него в `cacheDir` лежит файл-штамп:
 * нет штампа (ОС очистила кэш или первый запуск) → библиотечные таблицы сбрасываются при старте.
 * Прогресс, заметки, статусы, аккаунты и скачанные главы кэшем не считаются и не затрагиваются.
 */
@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settingsStore: SettingsStore,
    private val okHttpClient: OkHttpClient,
    private val playerHolder: PlayerHolder,
    private val coverManager: CoverManager,
    private val logger: AppLogger
) {
    private val stamp: File get() = File(context.cacheDir, STAMP_NAME)

    /** Вызывать при старте приложения. */
    suspend fun onAppStart() = withContext(Dispatchers.IO) {
        try {
            deleteLegacyCacheDirs()
            if (!stamp.exists()) {
                logger.info("CacheManager", "Штамп кэша отсутствует (ОС очистила кэш?) — сбрасываем кэш библиотеки")
                clearLibraryCache()
                stamp.writeText(System.currentTimeMillis().toString())
            }
        } catch (e: Exception) {
            logger.error("CacheManager", "onAppStart failed", e)
        }
    }

    /** Сбрасывает кэш библиотеки; книги со скачанными главами и их главы остаются. */
    suspend fun clearLibraryCache() = withContext(Dispatchers.IO) {
        val downloadedBooks = db.downloadedChapterDao().getBookIdsWithDownloads().toSet()
        val downloadedChapters = db.downloadedChapterDao().getAllChapterIds().toSet()

        db.bookDao().getAllIds().filter { it !in downloadedBooks }
            .chunked(500).forEach { db.bookDao().deleteByIds(it) }

        db.chapterDao().getAllIds().filter { it !in downloadedChapters }
            .chunked(500).forEach { db.chapterDao().deleteByIds(it) }

        // Серии, на которые не осталось книг, — тоже кэш
        val usedSeries = db.bookDao().getAll().mapNotNull { it.seriesId }.toSet()
        db.seriesDao().getAllIds().filter { it !in usedSeries }
            .chunked(500).forEach { db.seriesDao().deleteByIds(it) }

        settingsStore.setLastBooksSync(0L)
        settingsStore.setLastChaptersSync(0L)
        logger.info("CacheManager", "Кэш библиотеки сброшен")
    }

    /** «Очистить кэш» из приложения: HTTP-кэш, обложки, кэш медиа и кэш библиотеки. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        try { okHttpClient.cache?.evictAll() } catch (e: Exception) { logger.warn("CacheManager", "HTTP cache evict failed", e) }
        try { coverManager.clearCache() } catch (e: Exception) { logger.warn("CacheManager", "Covers clear failed", e) }
        try { playerHolder.clearMediaCache() } catch (e: Exception) { logger.warn("CacheManager", "Media cache clear failed", e) }
        clearLibraryCache()
        stamp.writeText(System.currentTimeMillis().toString())
    }

    /** Кэши раньше лежали в filesDir (ОС их не чистила) — убираем старые каталоги. */
    private fun deleteLegacyCacheDirs() {
        listOf("http_cache", "media_cache", "covers").forEach { name ->
            File(settingsStore.basepath, name).takeIf { it.exists() }?.deleteRecursively()
        }
    }

    private companion object {
        const val STAMP_NAME = "library_cache.stamp"
    }
}
