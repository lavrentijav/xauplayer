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
    private val settingsStore: SettingsStore,
    private val authRepository: ru.fire_core.xauplayer.domain.repo.AuthRepository
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

    val autoPlayNextSeriesBook: StateFlow<Boolean> = settingsStore.autoPlayNextSeriesBook.stateIn(
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

    val offlineMode: StateFlow<Boolean> = settingsStore.offlineMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = false
    )

    val sessionExpiryDays: StateFlow<Int> = settingsStore.sessionExpiryDays.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = AppConfig.DEFAULT_SESSION_EXPIRY_DAYS
    )

    val skipRefreshWhenOffline: StateFlow<Boolean> = settingsStore.skipRefreshWhenOffline.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
        initialValue = true
    )

    /** Результат последнего обновления сессии: null — не запускалось, true/false — успех/ошибка */
    private val _sessionRefreshResult = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)
    val sessionRefreshResult: StateFlow<Boolean?> = _sessionRefreshResult

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

    fun setAutoPlayNextSeriesBook(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setAutoPlayNextSeriesBook(enabled)
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

    /** Переключение оффлайн-режима (Req 7) */
    fun setOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            authRepository.setOfflineMode(enabled)
        }
    }

    /** Установка времени истечения сессии в днях (Req 6) */
    fun setSessionExpiryDays(days: Int) {
        viewModelScope.launch {
            settingsStore.setSessionExpiryDays(days)
        }
    }

    /** Не пытаться обновлять сессию без доступа к серверу (Req 2) */
    fun setSkipRefreshWhenOffline(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setSkipRefreshWhenOffline(enabled)
        }
    }

    /** Ручное обновление сессии (Req 5) */
    fun refreshSession() {
        viewModelScope.launch {
            _sessionRefreshResult.value = null
            val ok = try {
                authRepository.refreshSession()
            } catch (e: Exception) {
                false
            }
            _sessionRefreshResult.value = ok
        }
    }

    fun clearSessionRefreshResult() {
        _sessionRefreshResult.value = null
    }
}

