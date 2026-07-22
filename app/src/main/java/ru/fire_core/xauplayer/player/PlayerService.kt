package ru.fire_core.xauplayer.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.CacheBitmapLoader
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.ui.MainActivity
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Foreground-сервис воспроизведения аудио.
 *
 * Уведомление плеера управляется целиком самим [MediaSessionService] через
 * [MediaNotification.Provider] — это устраняет старую проблему, когда заглушка
 * «Инициализация плеера…» зависала навсегда (раньше её должен был заменять
 * отдельный PlayerNotificationManager, что не всегда срабатывало).
 *
 * Поведение уведомления зависит от настройки [SettingsStore.useSystemMediaPlayer]:
 *  - включено  → системное MediaStyle-уведомление (шторка, экран блокировки,
 *                Android Auto, умные часы) через [DefaultMediaNotificationProvider];
 *  - выключено → обычное «простое» уведомление (заголовок, тап для открытия,
 *                кнопка Play/Pause), без MediaStyle и без медиа-контролов на
 *                экране блокировки.
 */
@UnstableApi
@AndroidEntryPoint
class PlayerService : MediaSessionService() {
    @Inject lateinit var holder: PlayerHolder
    @Inject lateinit var logger: AppLogger
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var settingsStore: ru.fire_core.xauplayer.data.datastore.SettingsStore

    private val mediaChannelId = "xauplayer_media"
    private val simpleChannelId = "xauplayer_simple"

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Текущий режим уведомления. Читается провайдером при каждой отрисовке. */
    @Volatile
    private var useSystemMediaPlayer = true

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Провайдер уведомления, который сам MediaSessionService использует для
        // отображения и обновления единственного foreground-уведомления.
        setMediaNotificationProvider(AdaptiveNotificationProvider())

        // Плеер создаётся заранее в PlayerHolder, поэтому сессию можно построить
        // синхронно. Загружаем обложки через тот же авторизованный OkHttpClient.
        try {
            val player = holder.player()
            val bitmapLoader = CacheBitmapLoader(
                DataSourceBitmapLoader(
                    MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
                    OkHttpDataSource.Factory(okHttpClient)
                )
            )
            mediaSession = MediaSession.Builder(this, player)
                .setBitmapLoader(bitmapLoader)
                .build()
            // Явно регистрируем сессию в сервисе. Приложение играет через общий
            // ExoPlayer напрямую и не создаёт MediaController, поэтому без addSession
            // сервис не начал бы отслеживать плеер и не показал бы уведомление.
            mediaSession?.let { addSession(it) }
        } catch (e: Exception) {
            logger.error("PlayerService", "Error building media session: ${e.message}", e)
        }

        // Заглушка, чтобы уложиться в лимит startForeground() для startForegroundService().
        // Как только у плеера появляется медиа, провайдер заменяет её реальным
        // уведомлением с тем же NOTIFICATION_ID.
        startPlaceholderForeground()

        // Следим за настройкой: при изменении режим применяется к следующей
        // перерисовке уведомления (play/pause, смена главы).
        serviceScope.launch {
            useSystemMediaPlayer = settingsStore.useSystemMediaPlayer.first()
        }
        serviceScope.launch {
            settingsStore.useSystemMediaPlayer.collect { enabled ->
                useSystemMediaPlayer = enabled
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)

        // Канал системного MediaStyle-уведомления: видно на экране блокировки.
        if (nm.getNotificationChannel(mediaChannelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    mediaChannelId,
                    "Воспроизведение аудио",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Уведомления о воспроизведении аудио"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                }
            )
        }

        // Канал «простого» режима: не показываем содержимое на экране блокировки.
        if (nm.getNotificationChannel(simpleChannelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    simpleChannelId,
                    "Плеер (простой режим)",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Простое уведомление плеера без медиа-контролов"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
                    setSound(null, null)
                }
            )
        }
    }

    private fun startPlaceholderForeground() {
        val notification = NotificationCompat.Builder(this, mediaChannelId)
            .setContentTitle("XAuPlayer")
            .setContentText("Готово к воспроизведению")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

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

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /**
     * Единый провайдер, который в зависимости от настройки отдаёт либо системное
     * MediaStyle-уведомление, либо простое обычное уведомление.
     */
    private inner class AdaptiveNotificationProvider : MediaNotification.Provider {

        private val systemProvider: DefaultMediaNotificationProvider =
            DefaultMediaNotificationProvider.Builder(this@PlayerService)
                .setChannelId(mediaChannelId)
                .setNotificationId(NOTIFICATION_ID)
                .build()
                .apply { setSmallIcon(android.R.drawable.ic_media_play) }

        override fun createNotification(
            mediaSession: MediaSession,
            customLayout: ImmutableList<androidx.media3.session.CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            onNotificationChangedCallback: MediaNotification.Provider.Callback
        ): MediaNotification {
            return if (useSystemMediaPlayer) {
                systemProvider.createNotification(
                    mediaSession,
                    customLayout,
                    actionFactory,
                    onNotificationChangedCallback
                )
            } else {
                createSimpleNotification(mediaSession, actionFactory)
            }
        }

        override fun handleCustomCommand(
            session: MediaSession,
            action: String,
            extras: android.os.Bundle
        ): Boolean = systemProvider.handleCustomCommand(session, action, extras)
    }

    /** Простое уведомление без MediaStyle: заголовок, тап для открытия и Play/Pause. */
    private fun createSimpleNotification(
        mediaSession: MediaSession,
        actionFactory: MediaNotification.ActionFactory
    ): MediaNotification {
        val player = mediaSession.player
        val isPlaying = player.playWhenReady &&
            player.playbackState != Player.STATE_IDLE &&
            player.playbackState != Player.STATE_ENDED

        val title = player.currentMediaItem?.mediaMetadata?.title?.toString() ?: "XAuPlayer"
        val text = player.currentMediaItem?.mediaMetadata?.artist?.toString()
            ?: if (isPlaying) "Воспроизведение" else "Пауза"

        val playPauseAction = actionFactory.createMediaAction(
            mediaSession,
            IconCompat.createWithResource(
                this,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            ),
            if (isPlaying) "Пауза" else "Воспроизвести",
            Player.COMMAND_PLAY_PAUSE
        )

        val notification = NotificationCompat.Builder(this, simpleChannelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppIntent())
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .addAction(playPauseAction)
            .build()

        return MediaNotification(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}
