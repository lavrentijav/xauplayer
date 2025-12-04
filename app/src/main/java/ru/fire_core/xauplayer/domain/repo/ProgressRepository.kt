package ru.fire_core.xauplayer.domain.repo

import ru.fire_core.xauplayer.data.local.AppDatabase
import ru.fire_core.xauplayer.data.local.entities.Progress
import ru.fire_core.xauplayer.data.network.ApiService
import ru.fire_core.xauplayer.data.network.ProgressRequest
import ru.fire_core.xauplayer.core.logger.AppLogger
import javax.inject.Inject

interface ProgressRepository {
    suspend fun saveLocal(progress: Progress)
    suspend fun getLocal(bookId: Long): Progress?
    suspend fun getAllLocal(): List<Progress>
    suspend fun syncToServer(progress: Progress)
    suspend fun syncFromServer(): Map<String, Int>
}

class ProgressRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val db: AppDatabase,
    private val logger: AppLogger
) : ProgressRepository {
    override suspend fun saveLocal(progress: Progress) { db.progressDao().upsert(progress) }
    override suspend fun getLocal(bookId: Long): Progress? = db.progressDao().get(bookId)
    override suspend fun getAllLocal(): List<Progress> = db.progressDao().getAll()
    
    override suspend fun syncToServer(progress: Progress) {
        try {
            // Используем UTC время для отправки на сервер
            val iso = java.time.Instant.ofEpochMilli(progress.lastUpdate).toString()
            api.updateProgress(
                ProgressRequest(
                    book_id = progress.bookId,
                    chapter_id = progress.chapterId,
                    position_ms = progress.positionMs,
                    playback_speed = progress.speed,
                    last_update = iso
                )
            )
        } catch (e: Exception) {
            // Логируем ошибки сети при синхронизации, но не прерываем выполнение
            logger.warn("ProgressRepository", "Failed to sync progress to server for book ${progress.bookId}, chapter ${progress.chapterId}", e)
        }
    }
    
    override suspend fun syncFromServer(): Map<String, Int> {
        return try {
            val response = api.syncProgress()
            val localProgressMap = db.progressDao().getAll().associateBy { it.bookId }
            
            val mapped = response.progress.map { serverProgress ->
                val serverLastUpdate = try {
                    // Парсим UTC время с сервера
                    java.time.Instant.parse(serverProgress.last_update).toEpochMilli()
                } catch (e: Exception) {
                    // Если не удалось распарсить, используем текущее UTC время
                    logger.warn("ProgressRepository", "Failed to parse server last_update: ${serverProgress.last_update}", e)
                    java.time.Instant.now().toEpochMilli()
                }
                
                val localProgress = localProgressMap[serverProgress.book_id]
                
                // Сравниваем даты: если на сервере старше, отправляем локальную версию на сервер
                // Если локальная старше или равна, используем серверную версию
                if (localProgress != null && localProgress.lastUpdate > serverLastUpdate) {
                    // Локальная версия новее - отправляем на сервер
                    syncToServer(localProgress)
                    // Используем локальную версию
                    localProgress
                } else {
                    // Серверная версия новее или равная - используем её
                    Progress(
                        bookId = serverProgress.book_id,
                        chapterId = serverProgress.chapter_id,
                        positionMs = serverProgress.position_ms,
                        speed = serverProgress.playback_speed,
                        lastUpdate = serverLastUpdate
                    )
                }
            }
            
            // Сохраняем все прогрессы локально
            mapped.forEach { db.progressDao().upsert(it) }
            
            // Отправляем на сервер все локальные прогрессы, которых нет на сервере или которые новее
            localProgressMap.values.forEach { localProgress ->
                val serverProgress = response.progress.find { it.book_id == localProgress.bookId }
                val serverLastUpdate = if (serverProgress != null) {
                    try {
                        java.time.Instant.parse(serverProgress.last_update).toEpochMilli()
                    } catch (e: Exception) {
                        logger.warn("ProgressRepository", "Failed to parse server last_update: ${serverProgress.last_update}", e)
                        java.time.Instant.now().toEpochMilli()
                    }
                } else {
                    0L
                }
                
                if (serverProgress == null || localProgress.lastUpdate > serverLastUpdate) {
                    // Локальный прогресс новее или отсутствует на сервере - отправляем
                    syncToServer(localProgress)
                }
            }
            
            response.activity
        } catch (e: Exception) {
            // Логируем ошибки сети, но возвращаем пустую карту
            logger.error("ProgressRepository", "Failed to sync progress from server", e)
            emptyMap()
        }
    }
}
