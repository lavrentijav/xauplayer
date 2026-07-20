package ru.fire_core.xauplayer.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.fire_core.xauplayer.core.logger.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Подключает [MediaController] к [PlayerService] (Media3 MediaSessionService).
 *
 * Само по себе создание MediaSession не заставляет сервис показывать системное
 * уведомление — Media3 публикует его только когда к сессии подключается контроллер.
 * Приложение управляет плеером напрямую через [PlayerHolder], поэтому контроллер
 * нужен исключительно для «активации» системного уведомления / «сейчас играет».
 */
@UnstableApi
@Singleton
class MediaControllerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    /** Подключает контроллер к сервису (идемпотентно). */
    fun connect() {
        if (controllerFuture != null) return
        try {
            val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            controllerFuture = future
            future.addListener({
                try {
                    controller = future.get()
                    logger.info("MediaControllerConnection", "MediaController подключён к PlayerService")
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

    /** Освобождает контроллер. */
    fun release() {
        try {
            controllerFuture?.let { MediaController.releaseFuture(it) }
        } catch (e: Exception) {
            logger.warn("MediaControllerConnection", "release() ошибка: ${e.message}", e)
        }
        controllerFuture = null
        controller = null
    }
}
