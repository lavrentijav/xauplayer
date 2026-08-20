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

    /**
     * Параметры, по которым узнаётся ссылка на объект хранилища, подписанная
     * по AWS SigV4/SigV2 (S3, MinIO, совместимые CDN).
     */
    private val SIGNED_URL_PARAMS = listOf(
        "X-Amz-Signature",
        "X-Amz-Credential",
        "X-Amz-Algorithm",
        "AWSAccessKeyId"
    )

    /**
     * Подписанная ссылка на объект хранилища.
     *
     * Такие URL самодостаточны и недолговечны: подпись покрывает хост, путь и query,
     * поэтому им нельзя ни подменять адрес сервера, ни добавлять заголовок
     * Authorization — хранилище ответит ошибкой, а наш API — 404 на чужой путь.
     */
    fun isSignedStorageUrl(url: String): Boolean {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return false
        return query.split('&').any { param ->
            val name = param.substringBefore('=')
            SIGNED_URL_PARAMS.any { it.equals(name, ignoreCase = true) }
        }
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
