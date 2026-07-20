package ru.fire_core.xauplayer.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import kotlinx.coroutines.flow.first
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.ui.MainActivity
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class PlayerService : MediaSessionService() {
    @Inject lateinit var holder: PlayerHolder
    @Inject lateinit var logger: AppLogger
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var settingsStore: ru.fire_core.xauplayer.data.datastore.SettingsStore
    private val channelId = "xauplayer_media"
    private var mediaSession: MediaSession? = null
    private var notificationManager: PlayerNotificationManager? = null
    private var useSystemMediaPlayer = true

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Загружаем настройку использования системного MediaStyle
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            useSystemMediaPlayer = settingsStore.useSystemMediaPlayer.first()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                setupMediaSession()
            }
        }
        
        // ВАЖНО: Для MediaSessionService onStartCommand может не вызываться
        // Поэтому вызываем startForeground() в onCreate() чтобы избежать ForegroundServiceDidNotStartInTimeException
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("XAuPlayer")
            .setContentText("Инициализация плеера...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            // MediaStyle уведомления автоматически создаются PlayerNotificationManager
            // LOW приоритет для медиа - не издает звуки, но видно в шторке
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Отключаем звук уведомления
            .setSound(null)
            .setOnlyAlertOnce(true)
            .build()
        
        // Для Android 14+ (API 34+) требуется явно указывать тип foreground service
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        // Убеждаемся, что MediaSession настроен (если еще не настроен)
        // Это безопасно, так как setupMediaSession проверяет на null
        if (mediaSession == null) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                setupMediaSession()
            }
        }
        
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            // Используем IMPORTANCE_LOW для медиа-канала
            // Это правильный приоритет для медиа-уведомлений, которые не должны издавать звуки
            // но должны быть видны в шторке и на экране блокировки
            val channel = NotificationChannel(
                channelId,
                "Воспроизведение аудио",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомления о воспроизведении аудио"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                // Разрешаем показывать уведомление даже на заблокированном экране
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                // Устанавливаем звук в null - медиа-уведомления не должны издавать звуки
                setSound(null, null)
            }
            // Проверяем, не создан ли уже канал
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(channel)
            }
        }
    }

    @Synchronized
    private fun setupMediaSession() {
        if (mediaSession != null) return // Уже настроен
        
        try {
            // Получаем плеер - lazy инициализация может занять время, но не должна блокировать
            // Плеер уже должен быть создан в PlayerHolder
            val player = holder.player()
            
            // Создаем MediaSession - кнопки гарнитуры обрабатываются автоматически через Player
            // MediaSession автоматически интегрируется с системой Android
            // MediaSession активируется автоматически при создании через Builder
            mediaSession = MediaSession.Builder(this, player).build()

            if (useSystemMediaPlayer) {
                // СИСТЕМНЫЙ режим: встроенное уведомление MediaSessionService, связанное с
                // сессией — появляется в системных медиа-контролах («сейчас играет» /
                // экран блокировки). Работает благодаря корректной регистрации сервиса в
                // манифесте (intent-filter androidx.media3.session.MediaSessionService).
                // Задаём тот же id/канал, что и у стартовой заглушки, чтобы заменить её.
                setMediaNotificationProvider(
                    androidx.media3.session.DefaultMediaNotificationProvider.Builder(this)
                        .setNotificationId(NOTIFICATION_ID)
                        .setChannelId(channelId)
                        .build()
                )
                logger.info("PlayerService", "MediaSession: системное уведомление (DefaultMediaNotificationProvider)")
            } else {
                // Кастомный режим: собственное уведомление через PlayerNotificationManager.
                notificationManager = PlayerNotificationManager.Builder(
                    this,
                    NOTIFICATION_ID,
                    channelId
                ).apply {
                    setMediaDescriptionAdapter(MediaDescriptionAdapter())
                    setNotificationListener(NotificationListener())
                }.build().apply {
                    setUsePlayPauseActions(true)
                    setUseStopAction(false)
                    setUseFastForwardAction(true)
                    setUseRewindAction(true)
                    setUseNextAction(true)
                    setUsePreviousAction(true)
                    setPlayer(player)
                }
                logger.info("PlayerService", "MediaSession: кастомное уведомление (PlayerNotificationManager)")
            }
            
        } catch (e: Exception) {
            logger.error("PlayerService", "Error setting up media session: ${e.message}", e)
            // Не падаем при ошибке, просто логируем
            // MediaSession будет настроен позже через onGetSession
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // Настраиваем MediaSession если еще не настроен
        // НЕ вызываем setupMediaSession синхронно, чтобы не блокировать
        if (mediaSession == null) {
            // Настраиваем асинхронно, чтобы не блокировать вызов
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                setupMediaSession()
            }
        }
        return mediaSession
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationManager?.setPlayer(null)
        mediaSession?.release()
        mediaSession = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private inner class MediaDescriptionAdapter : PlayerNotificationManager.MediaDescriptionAdapter {
        override fun getCurrentContentTitle(player: Player): CharSequence {
            val mediaItem = player.currentMediaItem
            val title = mediaItem?.mediaMetadata?.title?.toString()
            // Если есть скорость, добавляем её к названию
            val speed = player.playbackParameters.speed
            return if (title != null && speed != 1.0f) {
                "$title (${"%.2f".format(speed)}x)"
            } else {
                title ?: "XAuPlayer"
            }
        }

        override fun getCurrentContentText(player: Player): CharSequence {
            val mediaItem = player.currentMediaItem
            val artist = mediaItem?.mediaMetadata?.artist?.toString()
            return artist ?: if (player.isPlaying) {
                "Воспроизведение"
            } else {
                "Пауза"
            }
        }

        override fun getCurrentSubText(player: Player): CharSequence? {
            return null
        }

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback
        ): android.graphics.Bitmap? {
            // Получаем обложку из метаданных
            val mediaItem = player.currentMediaItem
            val artworkUri = mediaItem?.mediaMetadata?.artworkUri
            
            if (artworkUri != null) {
                // Загружаем обложку асинхронно через URL
                loadArtworkFromUrl(artworkUri.toString(), callback)
                // Возвращаем null, так как загрузка асинхронная
                return null
            }
            
            return null
        }
        
        private fun loadArtworkFromUrl(
            url: String,
            callback: PlayerNotificationManager.BitmapCallback
        ) {
            // Используем корутину вместо Thread для лучшего управления жизненным циклом
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val request = Request.Builder().url(url).build()
                    // Используем инжектированный OkHttpClient из внешнего класса
                    val response = this@PlayerService.okHttpClient.newCall(request).execute()
                    val inputStream = response.body?.byteStream()
                    if (inputStream != null) {
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream.close()
                        // Вызываем callback в главном потоке
                        withContext(Dispatchers.Main) {
                            callback.onBitmap(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    // Игнорируем ошибки загрузки обложки
                }
            }
        }

        override fun createCurrentContentIntent(player: Player): android.app.PendingIntent? {
            return PendingIntent.getActivity(
                this@PlayerService,
                0,
                Intent(this@PlayerService, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }

    private inner class NotificationListener : PlayerNotificationManager.NotificationListener {
        override fun onNotificationCancelled(
            notificationId: Int,
            dismissedByUser: Boolean
        ) {
            if (dismissedByUser) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }

        override fun onNotificationPosted(
            notificationId: Int,
            notification: android.app.Notification,
            ongoing: Boolean
        ) {
            if (ongoing) {
                // PlayerNotificationManager автоматически вызывает startForeground
                // Для Android 14+ (API 34+) требуется явно указывать тип foreground service
                if (Build.VERSION.SDK_INT >= 34) {
                    ServiceCompat.startForeground(
                        this@PlayerService,
                        notificationId,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(notificationId, notification)
                }
            }
        }
    }

    // MediaSession.Callback в Media3 автоматически обрабатывает действия через Player
    // Не нужно переопределять методы, так как MediaSession уже связан с Player через Builder
    // Кнопки гарнитуры будут работать автоматически через стандартные действия MediaSession

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}


