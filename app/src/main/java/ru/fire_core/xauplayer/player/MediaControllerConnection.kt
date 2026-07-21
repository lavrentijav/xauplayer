package ru.fire_core.xauplayer.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Коннектор между приложением и [PlayerService] (Media3 MediaSessionService).
 *
 * Подключает [MediaController] к сервису ТОЛЬКО в системном режиме уведомления:
 * именно подключённый контроллер заставляет MediaSessionService показать своё
 * системное уведомление («сейчас играет» / экран блокировки). В кастомном режиме
 * контроллер НЕ подключается, иначе сервис публиковал бы своё уведомление поверх
 * PlayerNotificationManager (дубль).
 *
 * Коннектор сам следит за настройкой [SettingsStore.useSystemMediaPlayer] и за тем,
 * началось ли воспроизведение, и приводит состояние подключения в соответствие —
 * поэтому смена режима применяется на лету, без выхода из приложения.
 */
@UnstableApi
@Singleton
class MediaControllerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val logger: AppLogger
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private var systemMode: Boolean = true
    private var playbackActive: Boolean = false

    init {
        // Следим за режимом уведомления и применяем изменения на лету.
        settingsStore.useSystemMediaPlayer
            .onEach { system ->
                systemMode = system
                reconcile()
            }
            .launchIn(scope)
    }

    /** Вызывается при старте воспроизведения (когда сервис уже запущен). */
    fun onPlaybackStarted() {
        playbackActive = true
        reconcile()
    }

    /** Приводит подключение контроллера в соответствие с режимом и активностью. */
    private fun reconcile() {
        if (playbackActive && systemMode) {
            connect()
        } else {
            release()
        }
    }

    private fun connect() {
        val existing = controller
        when {
            existing != null && existing.isConnected -> return // уже подключён
            existing != null -> release() // устаревший — пересоздаём
            controllerFuture != null -> return // подключение уже идёт
        }
        try {
            val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            controllerFuture = future
            future.addListener({
                try {
                    controller = future.get()
                    logger.info("MediaControllerConnection", "MediaController подключён к PlayerService")
                    // Режим мог смениться, пока шло подключение
                    if (!(playbackActive && systemMode)) release()
                } catch (e: Exception) {
                    logger.warn("MediaControllerConnection", "Не удалось подключить MediaController: ${e.message}", e)
                    controllerFuture = null
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            logger.warn("MediaControllerConnection", "connect() ошибка: ${e.message}", e)
            controllerFuture = null
        }
    }

    private fun release() {
        try {
            controllerFuture?.let { MediaController.releaseFuture(it) }
        } catch (e: Exception) {
            logger.warn("MediaControllerConnection", "release() ошибка: ${e.message}", e)
        }
        controllerFuture = null
        controller = null
    }
}
