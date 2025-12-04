package ru.fire_core.xauplayer.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.media3.common.Player
import ru.fire_core.xauplayer.XAuPlayerApp
import ru.fire_core.xauplayer.core.logger.AppLogger

/**
 * BroadcastReceiver для обработки звонков
 * Ставит плеер на паузу при входящем звонке и возобновляет после завершения
 */
class PhoneStateReceiver : BroadcastReceiver() {
    
    companion object {
        private var wasPlayingBeforeCall = false
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            return
        }
        
        try {
            val app = context.applicationContext as? XAuPlayerApp
            val holder = app?.getPlayerHolder() ?: return
            val logger = app.getLogger() ?: return
            
            val phoneState = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val player = holder.player()
            
            when (phoneState) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    // Входящий звонок - ставим на паузу
                    if (player.isPlaying) {
                        wasPlayingBeforeCall = true
                        player.pause()
                        logger.info("PhoneStateReceiver", "Paused playback due to incoming call")
                    }
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    // Звонок активен - ставим на паузу (если еще не на паузе)
                    if (player.isPlaying) {
                        wasPlayingBeforeCall = true
                        player.pause()
                        logger.info("PhoneStateReceiver", "Paused playback due to active call")
                    }
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    // Звонок завершен - возобновляем воспроизведение, если оно было до звонка
                    if (wasPlayingBeforeCall && !player.isPlaying) {
                        player.play()
                        wasPlayingBeforeCall = false
                        logger.info("PhoneStateReceiver", "Resumed playback after call ended")
                    }
                }
            }
        } catch (e: Exception) {
            // Игнорируем ошибки, чтобы не крашить приложение
            android.util.Log.e("PhoneStateReceiver", "Error handling phone state", e)
        }
    }
}

