package ru.fire_core.xauplayer.di

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import ru.fire_core.xauplayer.core.auth.AuthManager
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.core.logger.HttpLogger
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import ru.fire_core.xauplayer.data.datastore.TokenStore
import ru.fire_core.xauplayer.data.network.ApiService
import ru.fire_core.xauplayer.data.network.RefreshRequest
import java.util.concurrent.TimeUnit
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object NetworkInterceptors {
    
    /**
     * Создает interceptor для динамического изменения base URL из настроек
     * Это позволяет менять URL без перезапуска приложения
     */
    fun createBaseUrlInterceptor(networkCache: NetworkCache): Interceptor {
        return Interceptor { chain ->
            val originalRequest = chain.request()
            val originalUrl = originalRequest.url
            
            // Получаем текущий base URL из кэша (не блокирующий вызов)
            val baseUrl = networkCache.getBaseUrl()
            
            // Парсим новый base URL используя java.net.URI и HttpUrl.Builder
            // Это работает во всех версиях OkHttp и избегает проблем с deprecated методами
            val baseUrlHttpUrl = try {
                val uri = java.net.URI(baseUrl)
                val scheme = uri.scheme ?: "https"
                val host = uri.host ?: return@Interceptor chain.proceed(originalRequest)
                val port = if (uri.port != -1) {
                    uri.port
                } else {
                    when (scheme) {
                        "https" -> 443
                        "http" -> 80
                        else -> return@Interceptor chain.proceed(originalRequest)
                    }
                }
                HttpUrl.Builder()
                    .scheme(scheme)
                    .host(host)
                    .port(port)
                    .build()
            } catch (e: Exception) {
                // Если парсинг не удался, пропускаем запрос без изменений
                return@Interceptor chain.proceed(originalRequest)
            }
            
            // Если запрос уже идет на правильный хост (совпадает с baseUrl), не изменяем его
            // Это важно для прямых запросов (например, скачивание файлов), которые используют полные URL
            if (originalUrl.host == baseUrlHttpUrl.host && 
                originalUrl.scheme == baseUrlHttpUrl.scheme && 
                originalUrl.port == baseUrlHttpUrl.port) {
                // URL уже указывает на правильный хост, пропускаем без изменений
                return@Interceptor chain.proceed(originalRequest)
            }
            
            // Всегда заменяем scheme, host и port на значения из настроек
            // Это позволяет динамически менять сервер без перезапуска приложения
            val newUrl = originalUrl.newBuilder()
                .scheme(baseUrlHttpUrl.scheme)
                .host(baseUrlHttpUrl.host)
                .port(baseUrlHttpUrl.port)
                .build()
            
            // Создаем новый запрос с обновленным URL
            val newRequest = originalRequest.newBuilder()
                .url(newUrl)
                .build()
            
            chain.proceed(newRequest)
        }
    }
    
    /**
     * Оффлайн-интерцептор: когда активен служебный аккаунт или включён оффлайн-режим,
     * перехватывает запросы к серверу и эмулирует положительные ответы.
     * Это позволяет слушать скачанный контент без доступа к серверу и с истёкшими сессиями.
     *
     * Перехватываются только сессионные и записывающие эндпоинты (auth, progress, status,
     * account, health). Читающие эндпоинты (books, chapters, series) пропускаются, чтобы
     * приложение могло использовать локальный кэш и базу данных.
     */
    fun createOfflineInterceptor(networkCache: NetworkCache): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()

            if (!networkCache.isServiceMode()) {
                return@Interceptor chain.proceed(request)
            }

            val path = request.url.encodedPath
            val method = request.method

            val body: String? = when {
                // auth/login и auth/register НЕ эмулируем — это осознанная попытка
                // достучаться до сервера для входа в реальный аккаунт.
                path.endsWith("/auth/refresh") -> tokenJson()
                path.endsWith("/auth/logout") -> statusOkJson()
                path.contains("/progress/update") -> statusOkJson()
                path.contains("/progress/sync") -> """{"progress":[],"activity":{}}"""
                method == "PUT" && path.contains("/status/books/") -> bookStatusJson(path, request)
                method == "PUT" && path.contains("/status/series/") -> seriesStatusJson(path, request)
                method == "DELETE" && path.contains("/status/") -> statusOkJson()
                method == "DELETE" && path.contains("/account/device/") -> statusOkJson()
                path.endsWith("/account/activity") -> "{}"
                path.endsWith("/account/devices") -> "[]"
                path.endsWith("/account") -> accountJson()
                path.endsWith("/health") -> """{"status":"ok","message":"offline"}"""
                else -> null
            }

            if (body == null) {
                // Неизвестный эндпоинт — пропускаем, приложение использует кэш/БД
                return@Interceptor chain.proceed(request)
            }

            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK (offline emulated)")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Offline-Emulated", "true")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun tokenJson(): String =
        """{"access_token":"${ru.fire_core.xauplayer.core.config.AppConfig.SERVICE_ACCESS_TOKEN}","refresh_token":"${ru.fire_core.xauplayer.core.config.AppConfig.SERVICE_REFRESH_TOKEN}","token_type":"bearer","device_id":0}"""

    private fun statusOkJson(): String = """{"status":"ok"}"""

    private fun accountJson(): String =
        """{"name":"${ru.fire_core.xauplayer.core.config.AppConfig.SERVICE_ACCOUNT_NAME}","email":"${ru.fire_core.xauplayer.core.config.AppConfig.SERVICE_ACCOUNT_EMAIL}","total_time_minutes":0,"is_admin":false}"""

    private fun lastIdFromPath(path: String): Long =
        path.trimEnd('/').substringAfterLast('/').toLongOrNull() ?: 0L

    private fun statusFromRequestBody(request: okhttp3.Request): String {
        return try {
            val buffer = okio.Buffer()
            request.body?.writeTo(buffer)
            val raw = buffer.readUtf8()
            Regex("\"status\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.get(1) ?: "listening"
        } catch (e: Exception) {
            "listening"
        }
    }

    private fun bookStatusJson(path: String, request: okhttp3.Request): String {
        val id = lastIdFromPath(path)
        val status = statusFromRequestBody(request)
        return """{"book_id":$id,"status":"$status"}"""
    }

    private fun seriesStatusJson(path: String, request: okhttp3.Request): String {
        val id = lastIdFromPath(path)
        val status = statusFromRequestBody(request)
        return """{"series_id":$id,"status":"$status"}"""
    }

    fun createAuthInterceptor(networkCache: NetworkCache): Interceptor {
        return Interceptor { chain ->
            val original = chain.request()
            // Получаем токен из кэша (не блокирующий вызов)
            val access = networkCache.getAccessToken()
            val builder = original.newBuilder()
            if (!access.isNullOrBlank()) {
                builder.addHeader("Authorization", "Bearer $access")
            }
            chain.proceed(builder.build())
        }
    }
    
    fun createAuthErrorInterceptor(
        networkCache: NetworkCache,
        tokenStore: TokenStore,
        settingsStore: SettingsStore,
        logger: AppLogger,
        httpLogger: ru.fire_core.xauplayer.core.logger.HttpLogger,
        authRepository: ru.fire_core.xauplayer.domain.repo.AuthRepository? = null
    ): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            
            val isAuthEndpoint = url.contains("/auth/login") || 
                                url.contains("/auth/refresh") || 
                                url.contains("/auth/register") ||
                                url.contains("/auth/logout")
            
            val response = chain.proceed(request)
            
            // Проверяем заголовок X-Token-Expires-Soon для автоматического обновления токена
            val expiresSoon = response.header("X-Token-Expires-Soon")
            if (expiresSoon == "true" && !isAuthEndpoint) {
                try {
                    val refreshSuccess = tryRefreshToken(networkCache, tokenStore, settingsStore, logger)
                    if (refreshSuccess) {
                        response.close()
                        // Получаем новый токен из кэша (обновляется асинхронно после сохранения)
                        val newAccess = networkCache.getAccessToken()
                        val newRequest = request.newBuilder()
                            .removeHeader("Authorization")
                        if (!newAccess.isNullOrBlank()) {
                            newRequest.addHeader("Authorization", "Bearer $newAccess")
                        }
                        return@Interceptor chain.proceed(newRequest.build())
                    }
                } catch (e: Exception) {
                    httpLogger.logError(logger, request, response, e, "HTTP")
                }
            }
            
            // Обрабатываем 401 с заголовком X-Token-Expired
            if (response.code == 401 && !isAuthEndpoint) {
                val tokenExpired = response.header("X-Token-Expired")
                if (tokenExpired == "true") {
                    val refreshSuccess = tryRefreshToken(networkCache, tokenStore, settingsStore, logger)
                    
                    if (refreshSuccess) {
                        response.close()
                        // Получаем новый токен из кэша
                        val newAccess = networkCache.getAccessToken()
                        val newRequest = request.newBuilder()
                            .removeHeader("Authorization")
                        if (!newAccess.isNullOrBlank()) {
                            newRequest.addHeader("Authorization", "Bearer $newAccess")
                        }
                        return@Interceptor chain.proceed(newRequest.build())
                    } else {
                        // Refresh не удался - отключаем автовход и требуем повторного входа
                        // Используем кэшированное значение email (не блокирующий вызов)
                        val email = networkCache.getCurrentEmail()
                        logger.warn("NetworkInterceptors", "Refresh token failed, disabling auto-login and requesting manual login")
                        AuthManager.requestLogin(email)
                        return@Interceptor response
                    }
                } else {
                    // Обычная 401 ошибка (не связанная с токеном)
                    // Требуем повторного входа
                    val email = networkCache.getCurrentEmail()
                    AuthManager.requestLogin(email)
                }
            }
            
            response
        }
    }
    
    /**
     * Пытается автоматически перелогиниться используя сохраненный аккаунт
     * Возвращает true если успешно, false если не удалось
     */
    private fun tryAutoRelogin(
        email: String?,
        authRepository: ru.fire_core.xauplayer.domain.repo.AuthRepository?,
        tokenStore: TokenStore,
        networkCache: NetworkCache,
        logger: AppLogger
    ): Boolean {
        if (email.isNullOrBlank() || authRepository == null) {
            return false
        }
        
        return kotlinx.coroutines.runBlocking {
            try {
                // Пытаемся найти сохраненный аккаунт
                val savedAccount = kotlinx.coroutines.withTimeout(3000) {
                    authRepository.getSavedAccount(email)
                }
                
                if (savedAccount != null) {
                    logger.info("NetworkInterceptors", "Attempting auto-relogin for saved account: $email")
                    
                    // Пытаемся выбрать аккаунт (это восстановит токены и попытается refresh)
                    val success = kotlinx.coroutines.withTimeout(5000) {
                        authRepository.selectAccount(email)
                    }
                    
                    if (success) {
                        logger.info("NetworkInterceptors", "Auto-relogin successful for: $email")
                        // Даем время на обновление кэша
                        kotlinx.coroutines.delay(100)
                        return@runBlocking true
                    } else {
                        logger.warn("NetworkInterceptors", "Auto-relogin failed for: $email (tokens expired or invalid)")
                    }
                } else {
                    logger.warn("NetworkInterceptors", "No saved account found for auto-relogin: $email")
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                logger.warn("NetworkInterceptors", "Timeout during auto-relogin attempt for: $email")
            } catch (e: Exception) {
                logger.error("NetworkInterceptors", "Error during auto-relogin attempt for: $email", e)
            }
            
            false
        }
    }
    
    private fun tryRefreshToken(
        networkCache: NetworkCache,
        tokenStore: TokenStore,
        settingsStore: SettingsStore,
        logger: AppLogger
    ): Boolean {
        // Req 2: не пытаемся обновлять сессию без доступа к серверу.
        // В служебном/оффлайн-режиме просто продлеваем сессию локально.
        if (networkCache.isServiceMode()) {
            logger.info("NetworkInterceptors", "Service/offline mode: extending session locally without server refresh")
            try {
                runBlocking {
                    withTimeout(2000) {
                        settingsStore.setLastSessionRenew(System.currentTimeMillis())
                    }
                }
            } catch (e: Exception) {
                // Игнорируем — продление локальное, не критично
            }
            return true
        }

        val refreshClient = OkHttpClient.Builder()
            .connectTimeout(ru.fire_core.xauplayer.core.config.AppConfig.NETWORK_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(ru.fire_core.xauplayer.core.config.AppConfig.NETWORK_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        
        // Используем кэшированное значение baseUrl (не блокирующий вызов)
        val baseUrl = networkCache.getBaseUrl()
        
        val refreshRetrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(refreshClient)
            .addConverterFactory(
                MoshiConverterFactory.create(
                    Moshi.Builder()
                        .add(KotlinJsonAdapterFactory())
                        .build()
                )
            )
            .build()
        
        val refreshApi = refreshRetrofit.create(ApiService::class.java)
        
        // Для refresh token нам все еще нужно использовать runBlocking, 
        // но это происходит в фоновом потоке OkHttp, так что безопасно
        // Добавляем таймаут для предотвращения бесконечного ожидания
        return runBlocking {
            try {
                // Используем кэшированное значение, но если его нет, читаем из store с таймаутом
                val refresh = networkCache.getRefreshToken() ?: withTimeout(5000) {
                    tokenStore.refreshToken.first()
                }
                if (refresh.isNullOrBlank()) {
                    false
                } else {
                    val tokenResponse = refreshApi.refresh(RefreshRequest(refresh))
                    val email = networkCache.getCurrentEmail() ?: withTimeout(5000) {
                        tokenStore.currentEmail.first()
                    }
                    if (email != null) {
                        tokenStore.save(tokenResponse.access_token, tokenResponse.refresh_token, 0, email)
                    } else {
                        tokenStore.save(tokenResponse.access_token, tokenResponse.refresh_token, 0, null)
                    }
                    // Кэш обновится автоматически через Flow подписку
                    true
                }
            } catch (e: TimeoutCancellationException) {
                logger.warn("NetworkInterceptors", "Timeout while reading token from store")
                // Не логируем через httpLogger здесь, так как он не доступен в этом контексте
                false
            } catch (e: Exception) {
                // Не логируем через httpLogger здесь, так как он не доступен в этом контексте
                false
            }
        }
    }
    
    fun createLoggingInterceptor(): Interceptor {
        return HttpLoggingInterceptor().apply { 
            level = HttpLoggingInterceptor.Level.BASIC 
        }
    }
    
    fun createErrorLoggingInterceptor(
        logger: AppLogger,
        httpLogger: ru.fire_core.xauplayer.core.logger.HttpLogger,
        authRepository: ru.fire_core.xauplayer.domain.repo.AuthRepository? = null,
        tokenStore: TokenStore? = null
    ): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            try {
                val response = chain.proceed(request)
                if (!response.isSuccessful) {
                    httpLogger.logError(logger, request, response, null, "HTTP")
                }
                response
            } catch (e: Exception) {
                httpLogger.logError(logger, request, null, e, "HTTP")
                
                // Проверяем, является ли ошибка сетевой (потеря сети)
                val isNetworkError = e is java.net.UnknownHostException || 
                                    e is java.net.ConnectException ||
                                    e is java.net.SocketTimeoutException ||
                                    (e is java.io.IOException && e.message?.contains("Unable to resolve host") == true)
                
                // Если это сетевая ошибка и есть AuthRepository, пытаемся автовход в оффлайн-аккаунт
                if (isNetworkError && authRepository != null) {
                    try {
                        // Пытаемся найти последний сохраненный аккаунт для автовхода с таймаутом
                        val savedAccounts = runBlocking {
                            withTimeout(5000) {
                                authRepository.getSavedAccounts()
                            }
                        }
                        // Берем последний использованный аккаунт (с самым большим lastLogin)
                        val lastAccount = savedAccounts
                            .filter { it.email != ru.fire_core.xauplayer.core.config.AppConfig.SERVICE_ACCOUNT_EMAIL }
                            .maxByOrNull { it.lastLogin }
                        val hasValidTokens = lastAccount != null &&
                            !lastAccount.accessToken.isNullOrBlank() &&
                            !lastAccount.refreshToken.isNullOrBlank()

                        if (!hasValidTokens && tokenStore != null) {
                            // Req 4: нет доступа к серверу и нет валидной сессии —
                            // автоматически входим в служебный аккаунт для прослушивания скачанного
                            logger.info("NetworkInterceptors", "No valid session and no network, entering service (offline) account")
                            runBlocking {
                                withTimeout(5000) {
                                    tokenStore.setCurrentEmail(ru.fire_core.xauplayer.core.config.AppConfig.SERVICE_ACCOUNT_EMAIL)
                                    tokenStore.save(
                                        ru.fire_core.xauplayer.core.config.AppConfig.SERVICE_ACCESS_TOKEN,
                                        ru.fire_core.xauplayer.core.config.AppConfig.SERVICE_REFRESH_TOKEN,
                                        0,
                                        ru.fire_core.xauplayer.core.config.AppConfig.SERVICE_ACCOUNT_EMAIL
                                    )
                                }
                            }
                        } else if (hasValidTokens && lastAccount != null && !lastAccount.accessToken.isNullOrBlank() && !lastAccount.refreshToken.isNullOrBlank()) {
                            logger.info("NetworkInterceptors", "Network error detected, attempting auto-login to offline account: ${lastAccount.email}")
                            // Пытаемся выбрать аккаунт (это установит токены если они есть)
                            // Не пытаемся refresh токен, так как сети нет - просто восстанавливаем токены из сохраненного аккаунта
                            if (tokenStore != null) {
                                runBlocking {
                                    withTimeout(5000) {
                                        tokenStore.setCurrentEmail(lastAccount.email)
                                        tokenStore.save(lastAccount.accessToken!!, lastAccount.refreshToken!!, 0, lastAccount.email)
                                    }
                                }
                                logger.info("NetworkInterceptors", "Auto-login to offline account successful")
                            } else {
                                // Если tokenStore не доступен, используем selectAccount (но он попытается refresh, что не сработает без сети)
                                runBlocking {
                                    withTimeout(5000) {
                                        authRepository.selectAccount(lastAccount.email)
                                    }
                                }
                                logger.info("NetworkInterceptors", "Auto-login to offline account attempted")
                            }
                        }
                    } catch (ex: TimeoutCancellationException) {
                        logger.warn("NetworkInterceptors", "Timeout while attempting auto-login to offline account")
                    } catch (ex: Exception) {
                        logger.error("NetworkInterceptors", "Failed to auto-login to offline account", ex)
                    }
                }
                
                throw e
            }
        }
    }
    
    fun createCacheInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val networkRequest = request.newBuilder()
                .cacheControl(okhttp3.CacheControl.Builder()
                    .noCache()
                    .build())
                .build()
            try {
                chain.proceed(networkRequest)
            } catch (e: Exception) {
                val cacheControl = okhttp3.CacheControl.Builder()
                    .onlyIfCached()
                    .maxStale(ru.fire_core.xauplayer.core.config.AppConfig.NETWORK_CACHE_MAX_STALE_DAYS, TimeUnit.DAYS)
                    .build()
                val cachedRequest = request.newBuilder()
                    .cacheControl(cacheControl)
                    .build()
                try {
                    chain.proceed(cachedRequest)
                } catch (e2: Exception) {
                    throw e
                }
            }
        }
    }
    
    fun createNetworkCacheInterceptor(): Interceptor {
        return Interceptor { chain ->
            val response = chain.proceed(chain.request())
            response.newBuilder()
                .header("Cache-Control", "public, max-age=3600")
                .build()
        }
    }
}

