package ru.fire_core.xauplayer.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.OkHttpClient
import ru.fire_core.xauplayer.R
import ru.fire_core.xauplayer.core.config.AppConfig
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.ui.MainActivity
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Системный медиаплеер: MediaSession + штатное MediaStyle-уведомление Media3
 * (шторка, экран блокировки, гарнитура, Bluetooth, Android Auto, часы).
 *
 * Плеер общий (singleton в [PlayerHolder]) и играет одну главу; кнопки «следующая/предыдущая» системного
 * плеера переключают главы через [ChapterNavigation] (его выставляет PlayerViewModel).
 */
@UnstableApi
@AndroidEntryPoint
class PlayerService : MediaSessionService() {
    @Inject lateinit var holder: PlayerHolder
    @Inject lateinit var logger: AppLogger
    @Inject lateinit var okHttpClient: OkHttpClient

    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate() // здесь Hilt внедряет зависимости
        createNotificationChannel()

        val sessionActivity = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Сессия создаётся синхронно: контроллеры (шторка, гарнитура) подключаются сразу после старта сервиса.
        // Обложки грузим через общий OkHttp (с авторизацией и кэшем).
        mediaSession = MediaSession.Builder(this, ChapterNavigationPlayer(holder.player()))
            .setSessionActivity(sessionActivity)
            .setBitmapLoader(
                DataSourceBitmapLoader(
                    MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
                    OkHttpDataSource.Factory(okHttpClient).setUserAgent(AppConfig.HTTP_USER_AGENT)
                )
            )
            .build()

        // Уведомление рисует сам Media3 на том же id и канале; плейсхолдер ниже лишь выполняет обязательство
        // startForeground() после startForegroundService() и тут же заменяется настоящим MediaStyle-уведомлением.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(NOTIFICATION_ID)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.player_channel_name)
                .build()
        )
        startForegroundPlaceholder(sessionActivity)

        // Если ничего не играет и в плеере пусто — не держим сервис с «пустым» уведомлением
        handler.postDelayed({
            val player = mediaSession?.player
            if (player == null || (player.mediaItemCount == 0 && !player.isPlaying)) stopSelf()
        }, IDLE_STOP_DELAY_MS)

        logger.info("PlayerService", "MediaSession создан")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Смахнули приложение: если не играет — останавливаем сервис, если играет — продолжаем в фоне
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        // Сам ExoPlayer общий (PlayerHolder) — его не освобождаем, только сессию
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun startForegroundPlaceholder(contentIntent: PendingIntent) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("XAuPlayer")
            .setContentText("Подготовка плеера…")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.player_channel_name),
                        NotificationManager.IMPORTANCE_LOW // без звука, но видно в шторке и на экране блокировки
                    ).apply {
                        description = "Управление воспроизведением аудиокниг"
                        setShowBadge(false)
                        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    }
                )
            }
        }
    }

    /**
     * Обёртка над общим плеером: добавляет системным контролам «следующая/предыдущая глава».
     * Остальное (play/pause, seek, скорость, метаданные) прозрачно уходит в ExoPlayer.
     */
    private inner class ChapterNavigationPlayer(player: Player) : ForwardingPlayer(player) {
        private val nav: ChapterNavigation? get() = holder.chapterNavigation

        override fun getAvailableCommands(): Player.Commands {
            val builder = super.getAvailableCommands().buildUpon()
            val hasNext = nav?.hasNext() == true
            val hasPrev = nav?.hasPrevious() == true
            if (hasNext) {
                builder.add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            } else {
                builder.remove(Player.COMMAND_SEEK_TO_NEXT).remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            }
            if (hasPrev) {
                builder.add(Player.COMMAND_SEEK_TO_PREVIOUS).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            } else {
                builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS).remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            }
            return builder.build()
        }

        override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

        override fun hasNextMediaItem(): Boolean = nav?.hasNext() == true
        override fun hasPreviousMediaItem(): Boolean = nav?.hasPrevious() == true

        override fun seekToNext() { nav?.next() }
        override fun seekToNextMediaItem() { nav?.next() }
        override fun seekToPrevious() { nav?.previous() }
        override fun seekToPreviousMediaItem() { nav?.previous() }
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "xauplayer_media"
        private const val IDLE_STOP_DELAY_MS = 8000L
    }
}
