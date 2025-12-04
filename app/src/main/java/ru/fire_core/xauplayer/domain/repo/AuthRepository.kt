package ru.fire_core.xauplayer.domain.repo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import ru.fire_core.xauplayer.data.datastore.TokenStore
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import ru.fire_core.xauplayer.data.local.AppDatabase
import ru.fire_core.xauplayer.data.local.entities.SavedAccount
import ru.fire_core.xauplayer.data.network.*
import java.io.File
import javax.inject.Inject

interface AuthRepository {
    suspend fun register(email: String, password: String, name: String?): LoginResponse
    suspend fun login(email: String, password: String, deviceName: String): LoginResponse
    suspend fun refreshToken(): Boolean
    suspend fun tryAutoRelogin(): Boolean
    suspend fun saveTokens(access: String, refresh: String, deviceId: Long, email: String, userId: Long, name: String?, passwordHash: String? = null)
    suspend fun getSavedAccounts(): List<SavedAccount>
    suspend fun getSavedAccount(email: String): SavedAccount?
    suspend fun selectAccount(email: String): Boolean
    suspend fun deleteAccount(email: String)
    suspend fun logout(saveAccount: Boolean = false)
}

class AuthRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val tokenStore: TokenStore,
    private val settingsStore: SettingsStore,
    private val db: AppDatabase,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) : AuthRepository {
    override suspend fun register(email: String, password: String, name: String?): LoginResponse {
        require(email.isNotBlank()) { "Email cannot be blank" }
        require(email.contains("@")) { "Email must be valid" }
        require(password.isNotBlank()) { "Password cannot be blank" }
        require(password.length >= ru.fire_core.xauplayer.core.config.AppConfig.MIN_PASSWORD_LENGTH) { "Password must be at least ${ru.fire_core.xauplayer.core.config.AppConfig.MIN_PASSWORD_LENGTH} characters" }
        
        val reg = api.register(RegisterRequest(email, password, name))
        // Some backends return tokens on register; here we login after register
        val deviceName = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
        return api.login(LoginRequest(email, password, deviceName))
    }

    override suspend fun login(email: String, password: String, deviceName: String): LoginResponse {
        require(email.isNotBlank()) { "Email cannot be blank" }
        require(email.contains("@")) { "Email must be valid" }
        require(password.isNotBlank()) { "Password cannot be blank" }
        require(password.length >= ru.fire_core.xauplayer.core.config.AppConfig.MIN_PASSWORD_LENGTH) { "Password must be at least ${ru.fire_core.xauplayer.core.config.AppConfig.MIN_PASSWORD_LENGTH} characters" }
        require(deviceName.isNotBlank()) { "Device name cannot be blank" }
        
        return api.login(LoginRequest(email, password, deviceName))
    }

    override suspend fun refreshToken(): Boolean {
        try {
            val refresh = tokenStore.refreshToken.first()
            if (refresh.isNullOrBlank()) return false
            
            val response = api.refresh(RefreshRequest(refresh))
            val email = tokenStore.currentEmail.first()
            if (email != null) {
                tokenStore.save(response.access_token, response.refresh_token, 0, email)
            } else {
                tokenStore.save(response.access_token, response.refresh_token, 0, null)
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Пытается автоматически перелогиниться используя сохраненный аккаунт
     * Возвращает true если успешно, false если не удалось
     */
    override suspend fun tryAutoRelogin(): Boolean {
        try {
            val email = tokenStore.currentEmail.first()
            if (email.isNullOrBlank()) return false
            
            val savedAccount = getSavedAccount(email)
            // Пароль не сохраняется, поэтому полный автоматический перелогин невозможен
            // Но можно попытаться refresh токен (уже сделано выше)
            // Если refresh не работает, возвращаем false
            return false
        } catch (e: Exception) {
            return false
        }
    }

    override suspend fun saveTokens(access: String, refresh: String, deviceId: Long, email: String, userId: Long, name: String?, passwordHash: String?) {
        tokenStore.save(access, refresh, deviceId, email)
        
        // Хэшируем пароль если передан
        val hash = passwordHash?.let { 
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(it.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
        
        // Сохраняем аккаунт в базу
        val savedAccount = SavedAccount(
            email = email,
            name = name,
            userId = userId,
            lastLogin = System.currentTimeMillis(),
            accessToken = access,
            refreshToken = refresh,
            passwordHash = hash
        )
        db.savedAccountDao().upsert(savedAccount)
    }

    override suspend fun getSavedAccounts(): List<SavedAccount> {
        return db.savedAccountDao().getAll()
    }

    override suspend fun getSavedAccount(email: String): SavedAccount? {
        return db.savedAccountDao().getByEmail(email)
    }

    override suspend fun selectAccount(email: String): Boolean {
        val account = db.savedAccountDao().getByEmail(email)
        account?.let {
            tokenStore.setCurrentEmail(email)
            if (!it.accessToken.isNullOrBlank() && !it.refreshToken.isNullOrBlank()) {
                // Сначала пытаемся восстановить токены
                tokenStore.save(it.accessToken!!, it.refreshToken!!, 0, email)
                
                // Пытаемся обновить токен, чтобы проверить, не истекли ли они
                val refreshSuccess = refreshToken()
                if (refreshSuccess) {
                    return true
                }
            }
            // Если токены отсутствуют или истекли, возвращаем false
            // Это означает, что нужен повторный логин
            return false
        }
        return false
    }

    override suspend fun deleteAccount(email: String) {
        db.savedAccountDao().delete(email)
    }

    override suspend fun logout(saveAccount: Boolean) {
        val email = tokenStore.currentEmail.first()
        val refresh = tokenStore.refreshToken.first()
        
        try {
            refresh?.let { api.logout(it) }
        } catch (e: Exception) {
            // Игнорируем ошибки при выходе, если нет сети
        }
        
        // Очищаем токены
        tokenStore.clear()
        
        if (email != null) {
            val account = db.savedAccountDao().getByEmail(email)
            if (saveAccount && account != null) {
                // Обновляем аккаунт без токенов, но сохраняем passwordHash если есть
                val updatedAccount = account.copy(
                    accessToken = null,
                    refreshToken = null,
                    lastLogin = System.currentTimeMillis()
                )
                db.savedAccountDao().upsert(updatedAccount)
            } else if (account != null && account.passwordHash != null) {
                // Если есть passwordHash, сохраняем аккаунт без токенов для "запомнить меня"
                val updatedAccount = account.copy(
                    accessToken = null,
                    refreshToken = null,
                    lastLogin = System.currentTimeMillis()
                )
                db.savedAccountDao().upsert(updatedAccount)
            } else {
                // Удаляем аккаунт, если нет passwordHash и не сохраняем
                db.savedAccountDao().delete(email)
            }
        }
        
        // Очищаем все кэши
        clearAllCaches(false) // Не сохраняем данные при выходе
    }
    
    /**
     * Очищает все кэши приложения
     * @param keepAccounts - сохранять ли сохраненные аккаунты в базе
     */
    private suspend fun clearAllCaches(keepAccounts: Boolean) {
        try {
            // 1. Очищаем HTTP кэш OkHttp
            okHttpClient.cache?.evictAll()
            
            // 2. Очищаем базу данных (кроме SavedAccount если нужно сохранить)
            // Сохраняем аккаунты перед очисткой если нужно
            val savedAccounts = if (keepAccounts) {
                db.savedAccountDao().getAll()
            } else {
                emptyList()
            }
            
            // Очищаем все таблицы
            db.clearAllTables()
            
            // Восстанавливаем аккаунты если нужно
            if (keepAccounts && savedAccounts.isNotEmpty()) {
                savedAccounts.forEach { account ->
                    db.savedAccountDao().upsert(account)
                }
            }
            
            // 3. Очищаем медиа кэш плеера
            val mediaCacheDir = File(settingsStore.basepath, "media_cache")
            if (mediaCacheDir.exists()) {
                mediaCacheDir.deleteRecursively()
            }
            
            // 4. Очищаем общий кэш приложения
            val httpCacheDir = File(settingsStore.basepath, "http_cache")
            if (httpCacheDir.exists()) {
                httpCacheDir.deleteRecursively()
            }
            
            // 5. DataStore уже очищен через tokenStore.clear() выше
            // SettingsStore не трогаем - настройки сохраняются между сессиями
            
        } catch (e: Exception) {
            // Игнорируем ошибки при очистке кэша
            e.printStackTrace()
        }
    }
}


