package ru.fire_core.xauplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import ru.fire_core.xauplayer.core.config.AppConfig
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore
) : ViewModel() {

    val baseUrl: StateFlow<String> = settingsStore.baseUrl.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(ru.fire_core.xauplayer.core.config.AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = ru.fire_core.xauplayer.core.config.AppConfig.DEFAULT_API_BASE_URL
    )

    val updateUrl: StateFlow<String> = settingsStore.updateUrl.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = AppConfig.DEFAULT_UPDATE_URL
    )

    val showLogs: StateFlow<Boolean> = settingsStore.showLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = false
    )
    
    val autoDownload: StateFlow<Boolean> = settingsStore.autoDownload.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = false
    )
    
    val autoDelete: StateFlow<Boolean> = settingsStore.autoDelete.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = false
    )
    
    val defaultSpeed: StateFlow<Float> = settingsStore.defaultSpeed.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = AppConfig.DEFAULT_PLAYBACK_SPEED
    )
    
    val rewindSeconds: StateFlow<Int> = settingsStore.rewindSeconds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = AppConfig.DEFAULT_REWIND_SECONDS
    )
    
    val forwardSeconds: StateFlow<Int> = settingsStore.forwardSeconds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = AppConfig.DEFAULT_FORWARD_SECONDS
    )
    
    val bufferBefore: StateFlow<Int> = settingsStore.bufferBefore.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = AppConfig.DEFAULT_BUFFER_BEFORE_SECONDS
    )
    
    val bufferAfter: StateFlow<Int> = settingsStore.bufferAfter.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = AppConfig.DEFAULT_BUFFER_AFTER_SECONDS
    )
    
    val accentColor: StateFlow<String> = settingsStore.accentColor.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = AppConfig.DEFAULT_ACCENT_COLOR
    )
    
    val playerColor: StateFlow<String> = settingsStore.playerColor.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = AppConfig.DEFAULT_PLAYER_COLOR
    )
    
    val progressSyncInterval: StateFlow<Int> = settingsStore.progressSyncInterval.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = AppConfig.DEFAULT_PROGRESS_SYNC_INTERVAL_MS.toInt()
    )

    val maxDownloadSpeed: StateFlow<Int> = settingsStore.maxDownloadSpeed.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = AppConfig.DEFAULT_MAX_DOWNLOAD_SPEED_KBPS
    )

    val maxConcurrentDownloads: StateFlow<Int> = settingsStore.maxConcurrentDownloads.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = AppConfig.DEFAULT_MAX_CONCURRENT_DOWNLOADS
    )
    
    val useSystemMediaPlayer: StateFlow<Boolean> = settingsStore.useSystemMediaPlayer.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = true // По умолчанию используем системный MediaStyle
    )

    fun setBaseUrl(url: String) {
        require(url.isNotBlank()) { "URL cannot be blank" }
        require(url.startsWith("http://") || url.startsWith("https://")) { "URL must start with http:// or https://" }
        viewModelScope.launch {
            settingsStore.setBaseUrl(url)
        }
    }

    fun setUpdateUrl(url: String) {
        require(url.isNotBlank()) { "URL cannot be blank" }
        require(url.startsWith("http://") || url.startsWith("https://")) { "URL must start with http:// or https://" }
        viewModelScope.launch {
            settingsStore.setUpdateUrl(url)
        }
    }

    fun setShowLogs(show: Boolean) {
        viewModelScope.launch {
            settingsStore.setShowLogs(show)
        }
    }
    
    fun setAutoDownload(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setAutoDownload(enabled)
        }
    }
    
    fun setAutoDelete(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setAutoDelete(enabled)
        }
    }
    
    fun setDefaultSpeed(speed: Float) {
        viewModelScope.launch {
            settingsStore.setDefaultSpeed(speed)
        }
    }
    
    fun setRewindSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsStore.setRewindSeconds(seconds)
        }
    }
    
    fun setForwardSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsStore.setForwardSeconds(seconds)
        }
    }
    
    fun setBufferBefore(count: Int) {
        viewModelScope.launch {
            settingsStore.setBufferBefore(count)
        }
    }
    
    fun setBufferAfter(count: Int) {
        viewModelScope.launch {
            settingsStore.setBufferAfter(count)
        }
    }
    
    fun setAccentColor(color: String) {
        viewModelScope.launch {
            settingsStore.setAccentColor(color)
        }
    }
    
    fun setPlayerColor(color: String) {
        viewModelScope.launch {
            settingsStore.setPlayerColor(color)
        }
    }
    
    fun setProgressSyncInterval(intervalMs: Int) {
        viewModelScope.launch {
            settingsStore.setProgressSyncInterval(intervalMs)
        }
    }

    fun setMaxDownloadSpeed(speedKBps: Int) {
        viewModelScope.launch {
            settingsStore.setMaxDownloadSpeed(speedKBps)
        }
    }

    fun setMaxConcurrentDownloads(count: Int) {
        viewModelScope.launch {
            settingsStore.setMaxConcurrentDownloads(count)
        }
    }
    
    fun setUseSystemMediaPlayer(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setUseSystemMediaPlayer(enabled)
        }
    }
}

