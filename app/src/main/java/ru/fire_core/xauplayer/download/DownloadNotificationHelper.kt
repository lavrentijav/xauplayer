package ru.fire_core.xauplayer.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val channelId = "xauplayer_downloads"
    private val notificationId = 2 // PlayerService использует ID 1

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Загрузки",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Прогресс загрузки глав"
                setShowBadge(false)
                setSound(null, null)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun showDownloadProgress(
        chapterTitle: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speed: Long,
        currentChapterIndex: Int? = null,
        totalChapters: Int? = null,
        totalDownloadedBytes: Long? = null,
        totalBookBytes: Long? = null,
        downloadedChaptersCount: Int? = null
    ) {
        val progress = if (totalBytes > 0) {
            ((downloadedBytes.toFloat() / totalBytes.toFloat()) * 100).toInt()
        } else {
            0
        }

        val speedMBps = speed / (1024.0 * 1024.0)
        val downloadedMB = downloadedBytes / (1024.0 * 1024.0)
        val totalMB = totalBytes / (1024.0 * 1024.0)

        // Формируем текст уведомления в свернутом виде: "Загружено аудиокниг: X из Y"
        val contentTitle = if (downloadedChaptersCount != null && totalChapters != null) {
            "Загружено аудиокниг: $downloadedChaptersCount из $totalChapters"
        } else {
            "Загрузка книги"
        }
        
        val contentText = if (currentChapterIndex != null && totalChapters != null) {
            "Глава ${currentChapterIndex + 1}/$totalChapters: $chapterTitle"
        } else {
            chapterTitle
        }
        
        val bigText = buildString {
            append("Текущая глава: $chapterTitle\n")
            append("${String.format("%.1f", downloadedMB)} / ${String.format("%.1f", totalMB)} МБ\n")
            if (totalDownloadedBytes != null && totalBookBytes != null && totalBookBytes > 0) {
                val totalDownloadedMB = totalDownloadedBytes / (1024.0 * 1024.0)
                val totalBookMB = totalBookBytes / (1024.0 * 1024.0)
                append("Общий прогресс: ${String.format("%.1f", totalDownloadedMB)} / ${String.format("%.1f", totalBookMB)} МБ")
            }
            append("\nСкорость: ${String.format("%.2f", speedMBps)} МБ/с")
        }

        // Вычисляем общий прогресс для всей книги
        val bookProgress = if (totalBookBytes != null && totalBookBytes > 0 && totalDownloadedBytes != null) {
            ((totalDownloadedBytes.toFloat() / totalBookBytes.toFloat()) * 100).toInt()
        } else {
            progress
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, bookProgress, false)
            .setOngoing(true)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Нет разрешения на уведомления
        }
    }

    fun showDownloadComplete(chapterTitle: String) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Загрузка завершена")
            .setContentText(chapterTitle)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Нет разрешения на уведомления
        }
    }

    fun showDownloadError(chapterTitle: String, error: String) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Ошибка загрузки")
            .setContentText("$chapterTitle: $error")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Нет разрешения на уведомления
        }
    }

    fun cancelNotification() {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}

