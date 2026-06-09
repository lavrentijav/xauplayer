package ru.fire_core.xauplayer.data.network

/**
 * Утилита для безопасной сборки URL API без поломки схемы https://
 */
object ApiUrlBuilder {

    private const val API_V1_SUFFIX = "/api/v1"

    /**
     * Приводит base URL к виду https://host[:port]/api/v1/
     * Поддерживает ввод с префиксом и без него.
     */
    fun normalizeApiBaseUrl(url: String): String {
        var normalized = url.trim().trimEnd('/')
        val markerIndex = normalized.lastIndexOf(API_V1_SUFFIX)
        normalized = if (markerIndex >= 0) {
            normalized.substring(0, markerIndex + API_V1_SUFFIX.length)
        } else {
            "$normalized$API_V1_SUFFIX"
        }
        return "$normalized/"
    }

    fun join(baseUrl: String, path: String): String {
        val normalizedBase = normalizeApiBaseUrl(baseUrl).trimEnd('/')
        val normalizedPath = path.trimStart('/')
        return "$normalizedBase/$normalizedPath"
    }

    fun resolveAbsolute(baseUrl: String, urlOrPath: String): String {
        if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
            return urlOrPath
        }
        return join(baseUrl, urlOrPath)
    }

    /**
     * Базовый URL для version/release эндпоинтов.
     * Поддерживает как корневые (/version), так и /api/v1/version.
     */
    fun resolveVersionBaseUrl(updateUrl: String, apiBaseUrl: String): String {
        val trimmedUpdate = updateUrl.trimEnd('/')
        if (trimmedUpdate.endsWith("/release")) {
            return trimmedUpdate.removeSuffix("/release")
        }
        val trimmedApi = apiBaseUrl.trimEnd('/')
        return if (trimmedApi.endsWith("/api/v1")) {
            trimmedApi.removeSuffix("/api/v1")
        } else {
            trimmedApi
        }
    }
}
