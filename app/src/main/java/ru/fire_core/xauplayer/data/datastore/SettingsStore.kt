package ru.fire_core.xauplayer.data.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.fire_core.xauplayer.data.network.ApiUrlBuilder
import java.io.File

private val Context.settingsDataStore by preferencesDataStore(name = "xau_settings")

class SettingsStore(private val context: Context) {
    private val keyBaseUrl = stringPreferencesKey("base_url")
    private val keyUpdateUrl = stringPreferencesKey("update_url")
    private val keyShowLogs = booleanPreferencesKey("show_logs")
    private val keyLastBooksSync = longPreferencesKey("last_books_sync")
    private val keyLastChaptersSync = longPreferencesKey("last_chapters_sync")
    private val keyLastUpdateCheck = longPreferencesKey("last_update_check")
    private val keyAutoDownload = booleanPreferencesKey("auto_download")
    private val keyAutoDelete = booleanPreferencesKey("auto_delete")
    private val keyAutoPlayNextSeriesBook = booleanPreferencesKey("auto_play_next_series_book")
    private val keyDefaultSpeed = floatPreferencesKey("default_speed")
    private val keyRewindSeconds = intPreferencesKey("rewind_seconds")
    private val keyForwardSeconds = intPreferencesKey("forward_seconds")
    private val keyBufferBefore = intPreferencesKey("buffer_before")
    private val keyBufferAfter = intPreferencesKey("buffer_after")
    private val keyAccentColor = stringPreferencesKey("accent_color")
    private val keyPlayerColor = stringPreferencesKey("player_color")
    private val keyLastBookId = longPreferencesKey("last_book_id")
    private val keyPositionUpdateInterval = intPreferencesKey("position_update_interval") // Интервал обновления позиции в миллисекундах
    private val keyProgressSyncInterval = intPreferencesKey("progress_sync_interval") // Интервал синхронизации прогресса в миллисекундах
    private val keyEqualizerEnabled = booleanPreferencesKey("equalizer_enabled")
    private val keyEqualizerBands = stringPreferencesKey("equalizer_bands") // JSON массив значений полос
    private val keyMediaCacheSize = longPreferencesKey("media_cache_size") // Размер кэша медиа в байтах
    private val keyCoverCacheSize = longPreferencesKey("cover_cache_size") // Максимальный размер кэша обложек в байтах
    private val keyMaxDownloadSpeed = intPreferencesKey("max_download_speed") // Максимальная скорость загрузки в KB/s (0 = без ограничений)
    private val keyMaxConcurrentDownloads = intPreferencesKey("max_concurrent_downloads") // Максимальное количество параллельных загрузок
    private val keyUseSystemMediaPlayer = booleanPreferencesKey("use_system_media_player") // Использовать системный MediaStyle уведомление (по умолчанию true)
    private val keyOfflineMode = booleanPreferencesKey("offline_mode") // Оффлайн-режим: автовход в служебный аккаунт, буферизация запросов
    private val keySessionExpiryDays = intPreferencesKey("session_expiry_days") // Через сколько дней истекает локальная сессия
    private val keySkipRefreshWhenOffline = booleanPreferencesKey("skip_refresh_when_offline") // Не пытаться обновлять сессию без доступа к серверу
    private val keyLastSessionRenew = longPreferencesKey("last_session_renew") // Время последнего локального продления сессии

    val baseUrl: Flow<String> = context.settingsDataStore.data.map {
        ApiUrlBuilder.normalizeApiBaseUrl(
            it[keyBaseUrl] ?: ru.fire_core.xauplayer.core.config.AppConfig.DEFAULT_API_BASE_URL
        )
    }
    
    val updateUrl: Flow<String> = context.settingsDataStore.data.map {
        it[keyUpdateUrl] ?: ru.fire_core.xauplayer.core.config.AppConfig.DEFAULT_UPDATE_URL
    }
    
    val showLogs: Flow<Boolean> = context.settingsDataStore.data.map { 
        it[keyShowLogs] ?: false 
    }
    
    val autoDownload: Flow<Boolean> = context.settingsDataStore.data.map {
        it[keyAutoDownload] ?: false
    }
    
    val autoDelete: Flow<Boolean> = context.settingsDataStore.data.map {
        it[keyAutoDelete] ?: false
    }

    val autoPlayNextSeriesBook: Flow<Boolean> = context.settingsDataStore.data.map {
        it[keyAutoPlayNextSeriesBook] ?: false
    }
    
    val defaultSpeed: Flow<Float> = context.settingsDataStore.data.map {
        it[keyDefaultSpeed] ?: ru.fire_core.xauplayer.core.config.AppConfig.DEFAULT_PLAYBACK_SPEED
    }
    
    val rewindSeconds: Flow<Int> = context.settingsDataStore.data.map {
        it[keyRewindSeconds] ?: ru.fire_core.xauplayer.core.config.AppConfig.DEFAULT_REWIND_SECONDS
    }
    
    val forwardSeconds: Flow<Int> = context.settingsDataStore.data.map {
        it[keyForwardSeconds] ?: ru.fire_core.xauplayer.core.config.AppConfig.DEFAULT_FORWARD_SECONDS
    }
    
    val bufferBefore: Flow<Int> = context.settingsDataStore.data.map {
        it[keyBufferBefore] ?: ru.fire_core.xauplayer.core.config.AppConfig.DEFAULT_BUFFER_BEFORE_SECONDS
    }
    
    val bufferAfter: Flow<Int> = context.settingsDataStore.data.map {
        it[keyBufferAfter] ?: ru.fire_core.xauplayer.core.config.AppConfig.DEFAULT_BUFFER_AFTER_SECONDS
    }
    
    val accentColor: Flow<String> = context.settingsDataStore.data.map {
        it[keyAccentColor] ?: ru.fire_core.xauplayer.core.config.AppConfig.DEFAULT_ACCENT_COLOR
    }
    
    val playerColor: Flow<String> = context.settingsDataStore.data.map {
        it[keyPlayerColor] ?: ru.fire_core.xauplayer.core.config.AppConfig.DEFAULT_PLAYER_COLOR
    }
    
    val positionUpdateInterval: Flow<Int> = context.settingsDataStore.data.map {
        it[keyPositionUpdateInterval] ?: ru.fire_core.xauplayer.core.config.AppConfig.DEFAULT_POSITION_UPDATE_INTERVAL_MS.toInt()
    }
    
    val progressSyncInterval: Flow<Int> = context.settingsDataStore.data.map {
        it[keyProgressSyncInterval] ?: ru.fire_core.xauplayer.core.config.AppConfig.DEFAULT_PROGRESS_SYNC_INTERVAL_MS.toInt()
    }
    
    val equalizerEnabled: Flow<Boolean> = context.settingsDataStore.data.map {
        it[keyEqualizerEnabled] ?: false
    }
    
    val equalizerBands: Flow<String> = context.settingsDataStore.data.map {
        it[keyEqualizerBands] ?: "[]" // Пустой JSON массив по умолчанию
    }
    
    val mediaCacheSize: Flow<Long> = context.settingsDataStore.data.map {
        it[keyMediaCacheSize] ?: (50L * 1024L * 1024L) // 50 MB по умолчанию
    }
    
    val coverCacheSize: Flow<Long> = context.settingsDataStore.data.map {
        it[keyCoverCacheSize] ?: (200L * 1024L * 1024L) // 200 MB по умолчанию
    }

    val maxDownloadSpeed: Flow<Int> = context.settingsDataStore.data.map {
        it[keyMaxDownloadSpeed] ?: 0 // 0 = без ограничений по умолчанию
    }

    val maxConcurrentDownloads: Flow<Int> = context.settingsDataStore.data.map {
        it[keyMaxConcurrentDownloads] ?: 4 // 4 параллельные загрузки по умолчанию
    }
    
    val useSystemMediaPlayer: Flow<Boolean> = context.settingsDataStore.data.map {
        it[keyUseSystemMediaPlayer] ?: true // По умолчанию используем системный MediaStyle
    }

    /** Оффлайн-режим: приложение автоматически входит в служебный аккаунт */
    val offlineMode: Flow<Boolean> = context.settingsDataStore.data.map {
        it[keyOfflineMode] ?: false
    }

    /** Через сколько дней истекает локальная сессия (параметр безопасности) */
    val sessionExpiryDays: Flow<Int> = context.settingsDataStore.data.map {
        it[keySessionExpiryDays] ?: ru.fire_core.xauplayer.core.config.AppConfig.DEFAULT_SESSION_EXPIRY_DAYS
    }

    /** Не пытаться обновлять сессию без доступа к серверу (локальное продление) */
    val skipRefreshWhenOffline: Flow<Boolean> = context.settingsDataStore.data.map {
        it[keySkipRefreshWhenOffline] ?: true
    }

    /** Время последнего локального продления сессии */
    val lastSessionRenew: Flow<Long> = context.settingsDataStore.data.map {
        it[keyLastSessionRenew] ?: 0L
    }

    val basepath: File = context.filesDir

    suspend fun getLastBooksSync(): Long {
        return context.settingsDataStore.data.first()[keyLastBooksSync] ?: 0L
    }

    suspend fun getLastChaptersSync(bookId: Long): Long {
        // Используем общий ключ для всех глав, можно расширить для каждой книги отдельно
        return context.settingsDataStore.data.first()[keyLastChaptersSync] ?: 0L
    }

    suspend fun setBaseUrl(url: String) {
        require(url.isNotBlank()) { "URL cannot be blank" }
        require(url.startsWith("http://") || url.startsWith("https://")) { "URL must start with http:// or https://" }
        val normalized = ApiUrlBuilder.normalizeApiBaseUrl(url)
        context.settingsDataStore.edit { preferences ->
            preferences[keyBaseUrl] = normalized
        }
    }

    suspend fun setUpdateUrl(url: String) {
        require(url.isNotBlank()) { "URL cannot be blank" }
        require(url.startsWith("http://") || url.startsWith("https://")) { "URL must start with http:// or https://" }
        context.settingsDataStore.edit { preferences ->
            preferences[keyUpdateUrl] = url
        }
    }

    suspend fun setShowLogs(show: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyShowLogs] = show
        }
    }

    suspend fun setLastBooksSync(time: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyLastBooksSync] = time
        }
    }

    suspend fun setLastChaptersSync(time: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyLastChaptersSync] = time
        }
    }
    
    suspend fun setAutoDownload(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyAutoDownload] = enabled
        }
    }
    
    suspend fun setAutoDelete(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyAutoDelete] = enabled
        }
    }

    suspend fun setAutoPlayNextSeriesBook(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyAutoPlayNextSeriesBook] = enabled
        }
    }
    
    suspend fun setDefaultSpeed(speed: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyDefaultSpeed] = speed
        }
    }
    
    suspend fun setRewindSeconds(seconds: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyRewindSeconds] = seconds
        }
    }
    
    suspend fun setForwardSeconds(seconds: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyForwardSeconds] = seconds
        }
    }
    
    suspend fun setBufferBefore(count: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyBufferBefore] = count
        }
    }
    
    suspend fun setBufferAfter(count: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyBufferAfter] = count
        }
    }
    
    suspend fun setAccentColor(color: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyAccentColor] = color
        }
    }
    
    suspend fun setPlayerColor(color: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyPlayerColor] = color
        }
    }
    
    suspend fun getLastBookId(): Long {
        return context.settingsDataStore.data.first()[keyLastBookId] ?: 0L
    }
    
    suspend fun setLastBookId(bookId: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyLastBookId] = bookId
        }
    }
    
    suspend fun setPositionUpdateInterval(intervalMs: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyPositionUpdateInterval] = intervalMs
        }
    }
    
    suspend fun setProgressSyncInterval(intervalMs: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyProgressSyncInterval] = intervalMs
        }
    }
    
    suspend fun setEqualizerEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyEqualizerEnabled] = enabled
        }
    }
    
    suspend fun setEqualizerBands(bandsJson: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyEqualizerBands] = bandsJson
        }
    }
    
    suspend fun getEqualizerBands(): String {
        return context.settingsDataStore.data.first()[keyEqualizerBands] ?: "[]"
    }
    
    suspend fun getEqualizerEnabled(): Boolean {
        return context.settingsDataStore.data.first()[keyEqualizerEnabled] ?: false
    }
    
    suspend fun setMediaCacheSize(sizeBytes: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyMediaCacheSize] = sizeBytes
        }
    }
    
    suspend fun setCoverCacheSize(sizeBytes: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyCoverCacheSize] = sizeBytes
        }
    }
    
    suspend fun getMediaCacheSize(): Long {
        return context.settingsDataStore.data.first()[keyMediaCacheSize] ?: (50L * 1024L * 1024L)
    }
    
    suspend fun getCoverCacheSize(): Long {
        return context.settingsDataStore.data.first()[keyCoverCacheSize] ?: (200L * 1024L * 1024L)
    }

    suspend fun setMaxDownloadSpeed(speedKBps: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyMaxDownloadSpeed] = speedKBps.coerceAtLeast(0)
        }
    }

    suspend fun getMaxDownloadSpeed(): Int {
        return context.settingsDataStore.data.first()[keyMaxDownloadSpeed] ?: 0
    }

    suspend fun setMaxConcurrentDownloads(count: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyMaxConcurrentDownloads] = count.coerceIn(1, 10) // Ограничиваем от 1 до 10
        }
    }

    suspend fun getMaxConcurrentDownloads(): Int {
        return context.settingsDataStore.data.first()[keyMaxConcurrentDownloads] ?: 4
    }
    
    suspend fun getLastUpdateCheck(): Long {
        return context.settingsDataStore.data.first()[keyLastUpdateCheck] ?: 0L
    }
    
    suspend fun setLastUpdateCheck(time: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyLastUpdateCheck] = time
        }
    }
    
    suspend fun getUseSystemMediaPlayer(): Boolean {
        return context.settingsDataStore.data.first()[keyUseSystemMediaPlayer] ?: true
    }
    
    suspend fun setUseSystemMediaPlayer(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyUseSystemMediaPlayer] = enabled
        }
    }

    suspend fun setOfflineMode(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyOfflineMode] = enabled
        }
    }

    suspend fun getOfflineMode(): Boolean {
        return context.settingsDataStore.data.first()[keyOfflineMode] ?: false
    }

    suspend fun setSessionExpiryDays(days: Int) {
        val clamped = days.coerceIn(
            ru.fire_core.xauplayer.core.config.AppConfig.MIN_SESSION_EXPIRY_DAYS,
            ru.fire_core.xauplayer.core.config.AppConfig.MAX_SESSION_EXPIRY_DAYS
        )
        context.settingsDataStore.edit { preferences ->
            preferences[keySessionExpiryDays] = clamped
        }
    }

    suspend fun getSessionExpiryDays(): Int {
        return context.settingsDataStore.data.first()[keySessionExpiryDays]
            ?: ru.fire_core.xauplayer.core.config.AppConfig.DEFAULT_SESSION_EXPIRY_DAYS
    }

    suspend fun setSkipRefreshWhenOffline(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[keySkipRefreshWhenOffline] = enabled
        }
    }

    suspend fun getSkipRefreshWhenOffline(): Boolean {
        return context.settingsDataStore.data.first()[keySkipRefreshWhenOffline] ?: true
    }

    suspend fun setLastSessionRenew(time: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[keyLastSessionRenew] = time
        }
    }

    suspend fun getLastSessionRenew(): Long {
        return context.settingsDataStore.data.first()[keyLastSessionRenew] ?: 0L
    }
}

