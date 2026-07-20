package ru.fire_core.xauplayer.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import ru.fire_core.xauplayer.core.logger.AppLogger
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.datasource.okhttp.OkHttpDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import ru.fire_core.xauplayer.core.config.AppConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val logger: AppLogger,
    private val settingsStore: SettingsStore
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Инициализируем плеер сразу, а не через lazy, чтобы избежать блокировок при первом обращении
    private val exo: ExoPlayer = ExoPlayer.Builder(context)
        .setHandleAudioBecomingNoisy(true) // Пауза при отключении наушников
        .setSeekBackIncrementMs(AppConfig.DEFAULT_SEEK_BACK_INCREMENT_MS)
        .setSeekForwardIncrementMs(AppConfig.DEFAULT_SEEK_FORWARD_INCREMENT_MS)
        .setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .build(),
            true // handleAudioFocus = true - автоматически обрабатывает аудио-фокус
        )
        .build()
    
    private var playerListener: Player.Listener? = null

    // Явно задаём аудио-сессию плеера, чтобы эквалайзер мог прикрепиться именно к ней.
    private var _audioSessionId: Int = 0

    /** ID аудио-сессии, на которую выводит звук плеер (для эквалайзера). */
    fun audioSessionId(): Int = _audioSessionId

    init {
        // Задаём стабильный audio session id для ExoPlayer (нужно эквалайзеру)
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val sid = am.generateAudioSessionId()
            if (sid != android.media.AudioManager.ERROR && sid != 0) {
                exo.setAudioSessionId(sid)
                _audioSessionId = sid
                logger.info("PlayerHolder", "Player audio session id set: $sid")
            }
        } catch (e: Exception) {
            logger.warn("PlayerHolder", "Failed to set audio session id: ${e.message}", e)
        }

        // Добавляем обработчик ошибок для автоматического retry
        playerListener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                logger.error(
                    tag = "PlayerHolder",
                    message = "Player error: ${error.message}",
                    throwable = error
                )
                // ExoPlayer автоматически повторяет попытки, но можно настроить
                // Не даем приложению упасть, просто логируем ошибку
            }

            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        logger.debug("PlayerHolder", "Buffering...")
                    }
                    Player.STATE_READY -> {
                        logger.debug("PlayerHolder", "Ready")
                    }
                    Player.STATE_ENDED -> {
                        logger.debug("PlayerHolder", "Ended")
                    }
                    Player.STATE_IDLE -> {
                        logger.debug("PlayerHolder", "Idle")
                    }
                }
            }
        }
        playerListener?.let { exo.addListener(it) }
        
        // Инициализируем кэш асинхронно
        scope.launch {
            try {
                val cacheSize = settingsStore.mediaCacheSize.first()
                val db = StandaloneDatabaseProvider(context)
                _cache.value = SimpleCache(
                    settingsStore.basepath.resolve("media_cache"),
                    LeastRecentlyUsedCacheEvictor(cacheSize),
                    db
                )
            } catch (e: Exception) {
                logger.error("PlayerHolder", "Failed to initialize cache", e)
                // Используем дефолтный размер кэша
                val db = StandaloneDatabaseProvider(context)
                val defaultCacheSize = AppConfig.DEFAULT_MEDIA_CACHE_SIZE_BYTES
                _cache.value = SimpleCache(
                    settingsStore.basepath.resolve(AppConfig.MEDIA_CACHE_DIR),
                    LeastRecentlyUsedCacheEvictor(defaultCacheSize),
                    db
                )
            }
        }
    }
    
    private val _cache = MutableStateFlow<SimpleCache?>(null)
    
    private val cache: SimpleCache?
        get() = _cache.value

    private val httpDataSourceFactory: DataSource.Factory = 
        OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(AppConfig.HTTP_USER_AGENT)

    private fun getCacheDataSourceFactory(): CacheDataSource.Factory? {
        val currentCache = cache ?: return null
        return CacheDataSource.Factory()
            .setCache(currentCache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    // Настройка политики обработки ошибок для повторных попыток
    private val loadErrorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            // Увеличиваем задержку между попытками, но продолжаем повторять
            val errorCount = loadErrorInfo.errorCount
            return if (errorCount <= AppConfig.RETRY_DELAYS_MS.size) {
                AppConfig.RETRY_DELAYS_MS[errorCount - 1]
            } else {
                AppConfig.RETRY_DELAYS_MS.last()
            }
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int {
            // Продолжаем попытки до бесконечности
            return AppConfig.MAX_RETRY_ATTEMPTS
        }
    }

    fun prepare(url: String, title: String = "XAuPlayer", artist: String? = null, coverUrl: String? = null) {
        require(url.isNotBlank()) { "URL cannot be blank" }
        
        val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .apply {
                artist?.let { setArtist(it) }
                coverUrl?.let { 
                    try {
                        setArtworkUri(android.net.Uri.parse(it))
                    } catch (e: Exception) {
                        logger.warn("PlayerHolder", "Failed to parse cover URL: $it", e)
                    }
                }
            }
            .build()
        
        val uri = try {
            android.net.Uri.parse(url)
        } catch (e: Exception) {
            logger.error("PlayerHolder", "Invalid URL: $url", e)
            throw IllegalArgumentException("Invalid URL: $url", e)
        }
        
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(mediaMetadata)
            .build()
        
        // Для локальных файлов используем DefaultDataSourceFactory, для HTTP - cacheDataSourceFactory
        val isLocalFile = url.startsWith("file://") || url.startsWith("/")
        val dataSourceFactory: DataSource.Factory = if (isLocalFile) {
            logger.info("PlayerHolder", "Using DefaultDataSourceFactory for local file: $url")
            // DefaultDataSourceFactory автоматически определяет тип источника (файл, HTTP и т.д.)
            DefaultDataSource.Factory(context)
        } else {
            logger.info("PlayerHolder", "Using CacheDataSourceFactory for remote URL: $url")
            // Если кэш еще не инициализирован, используем обычный DataSource
            getCacheDataSourceFactory() ?: run {
                logger.warn("PlayerHolder", "Cache not initialized, using DefaultDataSourceFactory")
                DefaultDataSource.Factory(context)
            }
        }
            
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            .createMediaSource(mediaItem)
        exo.setMediaSource(mediaSource)
        exo.prepare()
    }

    fun player(): ExoPlayer = exo
    
    /**
     * Освобождает ресурсы плеера. Должен вызываться при уничтожении компонента.
     */
    fun release() {
        playerListener?.let { exo.removeListener(it) }
        exo.release()
        cache?.release()
        scope.cancel()
    }
}
