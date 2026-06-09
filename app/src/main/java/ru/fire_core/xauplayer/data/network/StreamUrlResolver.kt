package ru.fire_core.xauplayer.data.network

import kotlinx.coroutines.flow.first
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Разрешает URL аудиопотока согласно API.md:
 * 1. GET /books/{id}/stream/{chapter_id} + Accept: application/json → url со stream_token
 * 2. Fallback: прямой stream URL с JWT (через OkHttp interceptor)
 */
@Singleton
class StreamUrlResolver @Inject constructor(
    private val api: ApiService,
    private val settingsStore: SettingsStore,
    private val logger: AppLogger
) {
    suspend fun resolve(bookId: Long, chapterId: Long): String {
        return try {
            val info = api.getStreamInfo(bookId, chapterId)
            when {
                info.url.isNotBlank() -> {
                    logger.debug("StreamUrlResolver", "Using stream URL from API JSON response")
                    ApiUrlBuilder.resolveAbsolute(settingsStore.baseUrl.first(), info.url)
                }
                !info.stream_token.isNullOrBlank() -> {
                    val baseUrl = settingsStore.baseUrl.first()
                    val path = "books/$bookId/stream/$chapterId?stream_token=${info.stream_token}"
                    ApiUrlBuilder.join(baseUrl, path)
                }
                else -> fallbackUrl(bookId, chapterId)
            }
        } catch (e: Exception) {
            logger.warn("StreamUrlResolver", "Failed to get stream info, using JWT fallback", e)
            fallbackUrl(bookId, chapterId)
        }
    }

    private suspend fun fallbackUrl(bookId: Long, chapterId: Long): String {
        val baseUrl = settingsStore.baseUrl.first()
        return ApiUrlBuilder.join(baseUrl, "books/$bookId/stream/$chapterId")
    }
}
