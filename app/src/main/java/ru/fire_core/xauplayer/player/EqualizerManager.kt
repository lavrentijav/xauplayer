package ru.fire_core.xauplayer.player

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.Player
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerManager @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val settingsStore: SettingsStore,
    private val logger: AppLogger
) {
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var audioSessionId: Int = 0
    private var isEnabled: Boolean = false

    fun attachToPlayer(player: Player) {
        try {
            // В Media3 нет прямого audioSessionId, используем AudioManager для получения session ID
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            val sessionId = audioManager.generateAudioSessionId()
            
            if (sessionId == 0) {
                logger.warn("EqualizerManager", "Audio session ID is 0, cannot attach equalizer")
                return
            }
            
            if (audioSessionId == sessionId && equalizer != null) {
                // Already attached to this session
                return
            }
            
            release()
            audioSessionId = sessionId
            
            try {
                equalizer = Equalizer(0, audioSessionId)
                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                
                // Загружаем сохраненные настройки асинхронно
                CoroutineScope(Dispatchers.IO).launch {
                    loadSettings()
                }
                
                logger.info("EqualizerManager", "Equalizer attached to audio session: $audioSessionId")
            } catch (e: Exception) {
                logger.error("EqualizerManager", "Failed to create equalizer: ${e.message}", e)
                equalizer = null
                loudnessEnhancer = null
            }
        } catch (e: Exception) {
            logger.error("EqualizerManager", "Failed to attach equalizer: ${e.message}", e)
        }
    }

    fun release() {
        try {
            equalizer?.release()
            loudnessEnhancer?.release()
            equalizer = null
            loudnessEnhancer = null
            audioSessionId = 0
        } catch (e: Exception) {
            logger.error("EqualizerManager", "Failed to release equalizer: ${e.message}", e)
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        try {
            equalizer?.enabled = enabled
            loudnessEnhancer?.enabled = enabled
        } catch (e: Exception) {
            logger.error("EqualizerManager", "Failed to set enabled state: ${e.message}", e)
        }
    }

    fun isEnabled(): Boolean = isEnabled

    fun getNumberOfBands(): Int {
        return try {
            equalizer?.numberOfBands?.toInt() ?: 0
        } catch (e: Exception) {
            logger.error("EqualizerManager", "Failed to get number of bands: ${e.message}", e)
            0
        }
    }

    fun getBandLevelRange(): Pair<Int, Int> {
        return try {
            val range = equalizer?.bandLevelRange ?: shortArrayOf(0, 0)
            Pair(range[0].toInt(), range[1].toInt())
        } catch (e: Exception) {
            logger.error("EqualizerManager", "Failed to get band level range: ${e.message}", e)
            Pair(-1500, 1500)
        }
    }

    fun getCenterFreq(band: Int): Int {
        return try {
            val freq = equalizer?.getCenterFreq(band.toShort())
            freq?.toInt() ?: 0
        } catch (e: Exception) {
            logger.error("EqualizerManager", "Failed to get center frequency: ${e.message}", e)
            0
        }
    }

    fun getBandLevel(band: Int): Int {
        return try {
            equalizer?.getBandLevel(band.toShort())?.toInt() ?: 0
        } catch (e: Exception) {
            logger.error("EqualizerManager", "Failed to get band level: ${e.message}", e)
            0
        }
    }

    fun setBandLevel(band: Int, level: Int) {
        try {
            equalizer?.setBandLevel(band.toShort(), level.toShort())
        } catch (e: Exception) {
            logger.error("EqualizerManager", "Failed to set band level: ${e.message}", e)
        }
    }

    fun getPresetNames(): List<String> {
        return try {
            val numberOfPresets = equalizer?.numberOfPresets?.toInt() ?: 0
            (0 until numberOfPresets).map { index ->
                equalizer?.getPresetName(index.toShort()) ?: "Preset $index"
            }
        } catch (e: Exception) {
            logger.error("EqualizerManager", "Failed to get preset names: ${e.message}", e)
            emptyList()
        }
    }

    fun usePreset(preset: Int) {
        try {
            equalizer?.usePreset(preset.toShort())
        } catch (e: Exception) {
            logger.error("EqualizerManager", "Failed to use preset: ${e.message}", e)
        }
    }

    suspend fun saveSettings() {
        try {
            val bands = getNumberOfBands()
            val bandsArray = JSONArray()
            for (i in 0 until bands) {
                bandsArray.put(getBandLevel(i))
            }
            
            settingsStore.setEqualizerBands(bandsArray.toString())
            settingsStore.setEqualizerEnabled(isEnabled)
            
            logger.info("EqualizerManager", "Equalizer settings saved")
        } catch (e: Exception) {
            logger.error("EqualizerManager", "Failed to save equalizer settings: ${e.message}", e)
        }
    }

    suspend fun loadSettings() {
        try {
            val enabled = settingsStore.getEqualizerEnabled()
            setEnabled(enabled)
            
            val bandsJson = settingsStore.getEqualizerBands()
            if (bandsJson.isNotEmpty() && bandsJson != "[]") {
                val bandsArray = JSONArray(bandsJson)
                val numberOfBands = getNumberOfBands()
                val bandsToLoad = minOf(bandsArray.length(), numberOfBands)
                
                for (i in 0 until bandsToLoad) {
                    val level = bandsArray.getInt(i)
                    setBandLevel(i, level)
                }
            }
            
            logger.info("EqualizerManager", "Equalizer settings loaded")
        } catch (e: Exception) {
            logger.error("EqualizerManager", "Failed to load equalizer settings: ${e.message}", e)
        }
    }
}

