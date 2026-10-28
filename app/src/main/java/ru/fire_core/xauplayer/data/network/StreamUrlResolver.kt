package ru.fire_core.xauplayer.data.network

import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Разрешает URL аудиопотока согласно API.md:
 * 1. GET /books/{id}/stream/{chapter_id} + Accept: application/json → url со stream_token
 * 2. Fallback: прямой stream URL с JWT (через OkHttp interceptor)
 *
 * Для проигрывания всегда выбирается ссылка на наш сервер: эндпоинт
 * `/books/{id}/stream/{chapter_id}` отдаёт аудио с поддержкой HTTP Range —
 * и для локальных файлов, и проксируя S3. Прямые presigned-ссылки на хранилище
 * (`"type": "direct"`) для стриминга не годятся: они живут ~час, а плеер
 * дозагружает главу диапазонами всё время прослушивания и переподключается
 * при перемотке — после истечения подписи такой запрос падает, глава
 * «зависает» и не доигрывает до конца. Скачиванию (один запрос целиком)
 * presigned-ссылка не мешает, поэтому DownloadManager продолжает её использовать.
 */
@Singleton
class StreamUrlResolver @Inject constructor(
    private val api: ApiService,
    private val settingsStore: SettingsStore,
    private val logger: AppLogger
) {
    suspend fun resolve(bookId: Long, chapterId: Long): String {
        val baseUrl = settingsStore.baseUrl.first()
        return try {
            val info = api.getStreamInfo(bookId, chapterId)
            val url = info.url
            when {
                // Ссылка на наш сервер (в том числе на отдельный медиа-сервер MEDIA_SERVER_URL)
                !url.isNullOrBlank() && isServerStreamUrl(url, baseUrl) -> {
                    logger.debug("StreamUrlResolver", "Using stream URL from API JSON response")
                    ApiUrlBuilder.resolveAbsolute(baseUrl, url)
                }
                // Есть короткоживущий токен — собираем same-origin ссылку сами
                !info.stream_token.isNullOrBlank() -> {
                    val path = "books/$bookId/stream/$chapterId?stream_token=${info.stream_token}"
                    ApiUrlBuilder.join(baseUrl, path)
                }
                // Сервер отдал presigned-ссылку хранилища: играем через прокси сервера,
                // чтобы подпись не протухла посреди главы
                else -> {
                    logger.info(
                        "StreamUrlResolver",
                        "Storage URL (type=${info.type}) is not used for playback, streaming via server proxy"
                    )
                    fallbackUrl(bookId, chapterId)
                }
            }
        } catch (e: Exception) {
            logger.warn("StreamUrlResolver", "Failed to get stream info, using JWT fallback", e)
            fallbackUrl(bookId, chapterId)
        }
    }

    /**
     * Стабильный ключ для медиа-кэша ExoPlayer. Привязан к главе, а не к URL:
     * у подписанных ссылок query меняется при каждом запросе, и кэш по URL
     * никогда не переиспользуется. Хост входит в ключ, чтобы при смене сервера
     * одинаковые id глав не подтянули чужое аудио из кэша.
     */
    suspend fun cacheKey(bookId: Long, chapterId: Long): String {
        val host = ApiUrlBuilder.normalizeApiBaseUrl(settingsStore.baseUrl.first())
            .toHttpUrlOrNull()?.host ?: "unknown"
        return "$host/book_$bookId/chapter_$chapterId"
    }

    /**
     * Ссылка ведёт на наш сервер (тот же хост, что и API), а не на хранилище/CDN.
     * Относительные пути считаются нашими — они разворачиваются от base URL.
     */
    private fun isServerStreamUrl(url: String, baseUrl: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return true // относительный путь
        if (ApiUrlBuilder.isSignedStorageUrl(url)) return false
        val apiHost = ApiUrlBuilder.normalizeApiBaseUrl(baseUrl).toHttpUrlOrNull()?.host
        return parsed.host == apiHost
    }

    private suspend fun fallbackUrl(bookId: Long, chapterId: Long): String {
        val baseUrl = settingsStore.baseUrl.first()
        return ApiUrlBuilder.join(baseUrl, "books/$bookId/stream/$chapterId")
    }
}
