package ru.fire_core.xauplayer.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import ru.fire_core.xauplayer.core.config.AppConfig
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.ui.MainActivity
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Foreground-сервис воспроизведения аудио. Два режима (настройка [SettingsStore.useSystemMediaPlayer]):
 *
 *  - **Системный** (по умолчанию): создаётся MediaSession Media3 и его уведомление
 *    (шторка, карточка «сейчас играет», экран блокировки, Bluetooth, Android Auto, часы).
 *  - **Классический** («как у ExoPlayer»): медиасессии НЕТ — поэтому ОС не показывает системную карточку.
 *    Сервис сам рисует обычное MediaStyle-уведомление (предыдущая / назад / пауза / вперёд / следующая,
 *    обложка, скорость в заголовке), кнопки идут обратно в сервис интентами. Кнопки гарнитуры
 *    обрабатываются через MediaButtonReceiver.
 *
 * Плеер общий (singleton в [PlayerHolder]), переключение режима «на лету» не прерывает звук.
 */
@UnstableApi
@AndroidEntryPoint
class PlayerService : MediaSessionService() {
    @Inject lateinit var holder: PlayerHolder
    @Inject lateinit var logger: AppLogger
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var settingsStore: ru.fire_core.xauplayer.data.datastore.SettingsStore
    @Inject lateinit var backgroundAdvancer: BackgroundAdvancer

    private val channelId = "xauplayer_media"
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var mediaSession: MediaSession? = null
    private lateinit var navPlayer: ChapterNavigationPlayer
    private lateinit var artworkLoader: DataSourceBitmapLoader
    private val systemProvider by lazy {
        DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(channelId)
            .setNotificationId(NOTIFICATION_ID)
            .build()
            .apply { setSmallIcon(android.R.drawable.ic_media_play) }
    }

    // Обложка для классического уведомления (грузится асинхронно, кэшируется по uri)
    private var artworkUriLoaded: android.net.Uri? = null
    private var artworkUriLoading: android.net.Uri? = null
    private var artworkBitmap: android.graphics.Bitmap? = null

    @Volatile
    private var systemMode = true
    private var classicListener: Player.Listener? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        navPlayer = ChapterNavigationPlayer(holder.player())
        // Обложки грузим через общий OkHttp (авторизация и кэш)
        artworkLoader = DataSourceBitmapLoader(
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
            OkHttpDataSource.Factory(okHttpClient).setUserAgent(AppConfig.HTTP_USER_AGENT)
        )
        setMediaNotificationProvider(systemProvider)

        // Режим читаем синхронно (DataStore отдаёт за миллисекунды) — иначе первый кадр будет не того режима
        systemMode = try { runBlocking { settingsStore.getUseSystemMediaPlayer() } } catch (e: Exception) { true }

        // Автопереход / кнопки глав / прогресс, когда экрана (ViewModel) уже нет
        backgroundAdvancer.attach()

        // Заглушка, чтобы уложиться в лимит startForeground() после startForegroundService()
        startPlaceholderForeground()
        applyMode(systemMode)

        // Переключение режима «на лету»
        serviceScope.launch {
            settingsStore.useSystemMediaPlayer.drop(1).collect { enabled ->
                if (enabled != systemMode) {
                    systemMode = enabled
                    applyMode(enabled)
                }
            }
        }

        // Если ничего не играет и в плеере пусто — не держим сервис с «пустым» уведомлением
        handler.postDelayed({
            val p = holder.player()
            if (p.mediaItemCount == 0 && !p.isPlaying) stopSelf()
        }, IDLE_STOP_DELAY_MS)
    }

    // --- режимы ---------------------------------------------------------------------------

    private fun applyMode(system: Boolean) {
        logger.info("PlayerService", "Режим уведомления: ${if (system) "системный" else "классический"}")
        if (system) {
            stopClassic()
            ensureSession()
        } else {
            dropSession()
            startClassic()
        }
    }

    private fun ensureSession() {
        if (mediaSession != null) return
        try {
            // Кнопки перемотки для системного уведомления: в custom layout Media3 показывает только кнопки
            // с кастомной SessionCommand, поэтому перемотка идёт через кастомные команды в Callback.
            val rewindButton = CommandButton.Builder(CommandButton.ICON_REWIND)
                .setSessionCommand(SessionCommand(ACTION_SEEK_BACK, Bundle.EMPTY))
                .setDisplayName("Назад")
                .build()
            val fastForwardButton = CommandButton.Builder(CommandButton.ICON_FAST_FORWARD)
                .setSessionCommand(SessionCommand(ACTION_SEEK_FORWARD, Bundle.EMPTY))
                .setDisplayName("Вперёд")
                .build()
            val session = MediaSession.Builder(this, navPlayer)
                .setBitmapLoader(artworkLoader)
                .setSessionActivity(openAppIntent())
                .setCallback(MediaSessionCallback())
                .setCustomLayout(ImmutableList.of(rewindButton, fastForwardButton))
                .build()
            mediaSession = session
            // Приложение играет через общий ExoPlayer напрямую и не создаёт MediaController, поэтому без
            // addSession сервис не следил бы за плеером и не показал уведомление.
            addSession(session)
        } catch (e: Exception) {
            logger.error("PlayerService", "Не удалось создать медиасессию: ${e.message}", e)
        }
    }

    private fun dropSession() {
        val session = mediaSession ?: return
        mediaSession = null
        try {
            removeSession(session)
            session.release()
        } catch (e: Exception) {
            logger.warn("PlayerService", "Ошибка при снятии медиасессии", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    // --- классический режим ----------------------------------------------------------------

    private fun startClassic() {
        if (classicListener == null) {
            val l = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = postClassic()
            }
            classicListener = l
            holder.player().addListener(l)
        }
        // Media3 при снятии сессии убирает своё уведомление (тот же id) — рисуем свежее чуть позже
        handler.postDelayed({ postClassic() }, 300)
    }

    private fun stopClassic() {
        classicListener?.let { holder.player().removeListener(it) }
        classicListener = null
    }

    private fun postClassic() {
        if (systemMode) return
        val player = holder.player()
        if (player.mediaItemCount == 0) return
        val notification = buildClassicNotification(player)
        val playing = player.playWhenReady &&
            player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
        if (playing) {
            startForegroundCompat(notification)
        } else {
            // На паузе уведомление можно смахнуть, но управление остаётся
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
            stopForeground(STOP_FOREGROUND_DETACH)
        }
    }

    private fun serviceAction(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, PlayerService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildClassicNotification(player: Player): android.app.Notification {
        val isPlaying = player.playWhenReady &&
            player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
        val metadata: MediaMetadata? = player.currentMediaItem?.mediaMetadata
        val baseTitle = metadata?.title?.toString()
        val speed = player.playbackParameters.speed
        val title = when {
            baseTitle == null -> "XAuPlayer"
            speed != 1.0f -> "$baseTitle (${"%.2f".format(speed)}x)"
            else -> baseTitle
        }
        val text = metadata?.artist?.toString() ?: if (isPlaying) "Воспроизведение" else "Пауза"

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppIntent())
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Предыдущая глава", serviceAction(ACTION_PREVIOUS, 1))
            .addAction(android.R.drawable.ic_media_rew, "Назад", serviceAction(ACTION_REWIND, 2))
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Пауза" else "Воспроизвести",
                serviceAction(ACTION_PLAY_PAUSE, 3)
            )
            .addAction(android.R.drawable.ic_media_ff, "Вперёд", serviceAction(ACTION_FORWARD, 4))
            .addAction(android.R.drawable.ic_media_next, "Следующая глава", serviceAction(ACTION_NEXT, 5))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 2, 4))

        // Обложка: если уже загружена — ставим, иначе грузим и обновляем уведомление
        val artworkUri = metadata?.artworkUri
        if (artworkUri != null) {
            if (artworkUri == artworkUriLoaded) {
                builder.setLargeIcon(artworkBitmap)
            } else if (artworkUri != artworkUriLoading) {
                artworkUriLoading = artworkUri
                Futures.addCallback(
                    artworkLoader.loadBitmap(artworkUri),
                    object : FutureCallback<android.graphics.Bitmap> {
                        override fun onSuccess(result: android.graphics.Bitmap?) {
                            artworkBitmap = result
                            artworkUriLoaded = artworkUri
                            artworkUriLoading = null
                            postClassic()
                        }

                        override fun onFailure(t: Throwable) {
                            artworkUriLoading = null
                            logger.warn("PlayerService", "Не удалось загрузить обложку для уведомления: $artworkUri", t)
                        }
                    },
                    androidx.core.content.ContextCompat.getMainExecutor(this)
                )
            }
        }
        return builder.build()
    }

    /** Кнопки классического уведомления и гарнитуры приходят сюда интентами. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> navPlayer.seekToNext()
            ACTION_PREVIOUS -> navPlayer.seekToPrevious()
            ACTION_REWIND -> navPlayer.seekBack()
            ACTION_FORWARD -> navPlayer.seekForward()
            Intent.ACTION_MEDIA_BUTTON -> if (!systemMode) handleMediaButton(intent)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun togglePlayPause() {
        val p = holder.player()
        if (p.playWhenReady && p.playbackState != Player.STATE_ENDED) p.pause() else p.play()
    }

    @Suppress("DEPRECATION")
    private fun handleMediaButton(intent: Intent) {
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        if (event.action != KeyEvent.ACTION_DOWN) return
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> togglePlayPause()
            KeyEvent.KEYCODE_MEDIA_PLAY -> holder.player().play()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> holder.player().pause()
            KeyEvent.KEYCODE_MEDIA_NEXT -> navPlayer.seekToNext()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> navPlayer.seekToPrevious()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> navPlayer.seekForward()
            KeyEvent.KEYCODE_MEDIA_REWIND -> navPlayer.seekBack()
        }
    }

    // --- жизненный цикл --------------------------------------------------------------------

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Смахнули приложение: если не играет — останавливаем сервис, если играет — продолжаем в фоне
        val player = holder.player()
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopClassic()
        backgroundAdvancer.detach()
        serviceScope.cancel()
        dropSession()
        super.onDestroy()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Воспроизведение аудио", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Уведомления о воспроизведении аудио"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                }
            )
        }
    }

    private fun startPlaceholderForeground() {
        val notification = NotificationCompat.Builder(this, channelId)
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
        startForegroundCompat(notification)
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    // --- сессия (системный режим) -----------------------------------------------------------

    /** Команды перемотки для системного уведомления (в custom layout — только кастомные SessionCommand). */
    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_SEEK_FORWARD, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SEEK_BACK, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_SEEK_FORWARD -> {
                    session.player.seekForward()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_SEEK_BACK -> {
                    session.player.seekBack()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    /**
     * Обёртка над общим плеером: даёт «следующая/предыдущая глава» через [ChapterNavigation]
     * (экран жив — PlayerViewModel, иначе BackgroundAdvancer). Остальное уходит в ExoPlayer без изменений.
     */
    private inner class ChapterNavigationPlayer(player: Player) : ForwardingPlayer(player) {
        private val nav: ChapterNavigation? get() = holder.chapterNavigation ?: backgroundAdvancer.fallbackNavigation

        override fun getAvailableCommands(): Player.Commands {
            val builder = super.getAvailableCommands().buildUpon()
            if (nav?.hasNext() == true) {
                builder.add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            } else {
                builder.remove(Player.COMMAND_SEEK_TO_NEXT).remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            }
            if (nav?.hasPrevious() == true) {
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
        private const val IDLE_STOP_DELAY_MS = 8000L
        private const val ACTION_SEEK_FORWARD = "ru.fire_core.xauplayer.SEEK_FORWARD"
        private const val ACTION_SEEK_BACK = "ru.fire_core.xauplayer.SEEK_BACK"
        private const val ACTION_PLAY_PAUSE = "ru.fire_core.xauplayer.PLAY_PAUSE"
        private const val ACTION_NEXT = "ru.fire_core.xauplayer.NEXT"
        private const val ACTION_PREVIOUS = "ru.fire_core.xauplayer.PREVIOUS"
        private const val ACTION_REWIND = "ru.fire_core.xauplayer.REWIND"
        private const val ACTION_FORWARD = "ru.fire_core.xauplayer.FORWARD"
    }
}
