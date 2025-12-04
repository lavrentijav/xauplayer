package ru.fire_core.xauplayer.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.local.AppDatabase
import ru.fire_core.xauplayer.data.local.entities.BufferedProgress
import ru.fire_core.xauplayer.data.local.entities.Progress
import ru.fire_core.xauplayer.domain.repo.ProgressRepository
import ru.fire_core.xauplayer.core.config.AppConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Менеджер для буферизации обновлений прогресса в offline режиме
 */
@Singleton
class OfflineProgressManager @Inject constructor(
    private val db: AppDatabase,
    private val progressRepo: ProgressRepository,
    private val logger: AppLogger
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: kotlinx.coroutines.Job? = null

    init {
        // Запускаем фоновую синхронизацию только при наличии несинхронизированных данных
        startPeriodicSync()
    }
    
    private fun startPeriodicSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (true) {
                delay(AppConfig.OFFLINE_PROGRESS_SYNC_INTERVAL_MS)
                try {
                    // Проверяем наличие несинхронизированных данных перед синхронизацией
                    val unsynced = db.bufferedProgressDao().getUnsynced()
                    if (unsynced.isNotEmpty()) {
                        syncBufferedProgress()
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Job был отменен - это нормально
                    break
                } catch (e: Exception) {
                    // Игнорируем ошибки и продолжаем цикл
                    logger.warn("OfflineProgressManager", "Error in periodic sync: ${e.message}")
                }
            }
        }
    }
    
    fun stop() {
        syncJob?.cancel()
        syncJob = null
    }

    /**
     * Добавляет обновление прогресса в буфер
     * Если уже есть несинхронизированное обновление для этой же главы - заменяет его
     */
    suspend fun bufferProgress(progress: Progress) {
        try {
            // Удаляем старое несинхронизированное обновление для этой главы
            db.bufferedProgressDao().deleteUnsyncedForChapter(progress.bookId, progress.chapterId)
            
            // Добавляем новое
            val buffered = BufferedProgress(
                bookId = progress.bookId,
                chapterId = progress.chapterId,
                positionMs = progress.positionMs,
                speed = progress.speed,
                timestamp = System.currentTimeMillis(),
                synced = false
            )
            db.bufferedProgressDao().insert(buffered)
            
            logger.info("OfflineProgressManager", "Progress buffered for book ${progress.bookId}, chapter ${progress.chapterId}")
        } catch (e: Exception) {
            logger.error("OfflineProgressManager", "Failed to buffer progress", e)
        }
    }

    /**
     * Пытается синхронизировать все буферизованные обновления с сервером
     */
    suspend fun syncBufferedProgress() {
        try {
            val unsynced = db.bufferedProgressDao().getUnsynced()
            if (unsynced.isEmpty()) return
            
            logger.info("OfflineProgressManager", "Syncing ${unsynced.size} buffered progress updates")
            
            for (buffered in unsynced) {
                try {
                    val progress = Progress(
                        bookId = buffered.bookId,
                        chapterId = buffered.chapterId,
                        positionMs = buffered.positionMs,
                        speed = buffered.speed,
                        lastUpdate = buffered.timestamp
                    )
                    
                    // Пытаемся отправить на сервер
                    progressRepo.syncToServer(progress)
                    
                    // Если успешно - помечаем как синхронизированное
                    db.bufferedProgressDao().markSynced(buffered.id)
                    
                    logger.info("OfflineProgressManager", "Synced progress for book ${buffered.bookId}")
                } catch (e: Exception) {
                    // Ошибка синхронизации - оставляем в буфере
                    logger.warn("OfflineProgressManager", "Failed to sync progress for book ${buffered.bookId}: ${e.message}")
                }
            }
            
            // Очищаем старые синхронизированные записи
            val weekAgo = System.currentTimeMillis() - AppConfig.BUFFERED_PROGRESS_CLEANUP_PERIOD_MS
            db.bufferedProgressDao().cleanupOldSynced(weekAgo)
            
        } catch (e: Exception) {
            logger.error("OfflineProgressManager", "Failed to sync buffered progress", e)
        }
    }

    /**
     * Принудительно синхронизирует конкретное обновление
     */
    suspend fun forceSyncProgress(progress: Progress): Boolean {
        return try {
            // Сначала буферизуем
            bufferProgress(progress)
            
            // Затем пытаемся сразу синхронизировать
            progressRepo.syncToServer(progress)
            
            // Если успешно - удаляем из буфера
            db.bufferedProgressDao().deleteUnsyncedForChapter(progress.bookId, progress.chapterId)
            
            true
        } catch (e: Exception) {
            // Остается в буфере для последующей синхронизации
            logger.warn("OfflineProgressManager", "Force sync failed, progress buffered: ${e.message}")
            false
        }
    }
}

