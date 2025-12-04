package ru.fire_core.xauplayer.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Информация о загруженной главе
 */
@Entity(
    tableName = "downloaded_chapters",
    indices = [
        Index(value = ["chapterId"]),
        Index(value = ["bookId"])
    ]
)
data class DownloadedChapter(
    @PrimaryKey
    val chapterId: Long,
    val bookId: Long,
    val localPath: String, // Путь к локальному файлу
    val downloadedAt: Long, // Время загрузки
    val fileSize: Long, // Размер файла в байтах
    val isComplete: Boolean = true // Загрузка завершена
)

/**
 * Буферизованное обновление прогресса (для offline)
 */
@Entity(tableName = "progress_buffer")
data class BufferedProgress(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val chapterId: Long,
    val positionMs: Long,
    val speed: Float,
    val timestamp: Long, // Время создания записи
    val synced: Boolean = false // Синхронизировано с сервером
)

