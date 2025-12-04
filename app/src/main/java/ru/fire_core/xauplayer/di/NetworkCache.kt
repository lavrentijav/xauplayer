package ru.fire_core.xauplayer.di

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import ru.fire_core.xauplayer.data.datastore.TokenStore
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Кэш для сетевых настроек, чтобы избежать блокирующих вызовов DataStore в interceptors.
 * Значения обновляются асинхронно через Flow, а interceptors используют кэшированные значения.
 */
@Singleton
class NetworkCache @Inject constructor(
    settingsStore: SettingsStore,
    tokenStore: TokenStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Кэшированные значения с дефолтными значениями
    private val _baseUrl = AtomicReference<String>("https://api.xau.fire-core.ru/")
    private val _accessToken = AtomicReference<String?>(null)
    private val _refreshToken = AtomicReference<String?>(null)
    private val _currentEmail = AtomicReference<String?>(null)
    
    // Публичные методы для получения кэшированных значений (не блокирующие)
    fun getBaseUrl(): String = _baseUrl.get()
    fun getAccessToken(): String? = _accessToken.get()
    fun getRefreshToken(): String? = _refreshToken.get()
    fun getCurrentEmail(): String? = _currentEmail.get()
    
    init {
        Log.d("NetworkCache", "init START")
        // Загружаем начальные значения в фоновом потоке
        scope.launch {
            Log.d("NetworkCache", "Загрузка начальных значений из DataStore...")
            try {
                _baseUrl.set(settingsStore.baseUrl.first())
                Log.d("NetworkCache", "baseUrl загружен: ${_baseUrl.get()}")
            } catch (e: Exception) {
                Log.w("NetworkCache", "Ошибка загрузки baseUrl: ${e.message}", e)
                // Используем дефолтное значение
            }
            
            try {
                _accessToken.set(tokenStore.accessToken.first())
                Log.d("NetworkCache", "accessToken загружен: ${if (_accessToken.get() != null) "есть" else "нет"}")
            } catch (e: Exception) {
                Log.w("NetworkCache", "Ошибка загрузки accessToken: ${e.message}", e)
                // Оставляем null
            }
            
            try {
                _refreshToken.set(tokenStore.refreshToken.first())
                Log.d("NetworkCache", "refreshToken загружен: ${if (_refreshToken.get() != null) "есть" else "нет"}")
            } catch (e: Exception) {
                Log.w("NetworkCache", "Ошибка загрузки refreshToken: ${e.message}", e)
                // Оставляем null
            }
            
            try {
                _currentEmail.set(tokenStore.currentEmail.first())
                Log.d("NetworkCache", "currentEmail загружен: ${_currentEmail.get() ?: "нет"}")
            } catch (e: Exception) {
                Log.w("NetworkCache", "Ошибка загрузки currentEmail: ${e.message}", e)
                // Оставляем null
            }
            Log.d("NetworkCache", "Начальные значения загружены")
        }
        
        Log.d("NetworkCache", "Подписка на обновления Flow...")
        // Подписываемся на обновления через Flow
        settingsStore.baseUrl
            .onEach { _baseUrl.set(it) }
            .launchIn(scope)
        
        tokenStore.accessToken
            .onEach { _accessToken.set(it) }
            .launchIn(scope)
        
        tokenStore.refreshToken
            .onEach { _refreshToken.set(it) }
            .launchIn(scope)
        
        tokenStore.currentEmail
            .onEach { _currentEmail.set(it) }
            .launchIn(scope)
        Log.d("NetworkCache", "init END")
    }
}

