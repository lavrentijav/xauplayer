package ru.fire_core.xauplayer.ui.screens.Auth

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.data.local.entities.SavedAccount
import ru.fire_core.xauplayer.data.network.LoginResponse
import ru.fire_core.xauplayer.domain.repo.AccountRepository
import ru.fire_core.xauplayer.domain.usecase.auth.DeleteAccountUseCase
import ru.fire_core.xauplayer.domain.usecase.auth.GetSavedAccountsUseCase
import ru.fire_core.xauplayer.domain.usecase.auth.LoginUseCase
import ru.fire_core.xauplayer.domain.usecase.auth.SaveTokensUseCase
import ru.fire_core.xauplayer.domain.usecase.auth.RegisterUseCase
import ru.fire_core.xauplayer.domain.usecase.auth.SelectAccountUseCase
import ru.fire_core.xauplayer.domain.usecase.auth.HasActiveSessionUseCase
import ru.fire_core.xauplayer.domain.usecase.auth.EnterServiceAccountUseCase
import ru.fire_core.xauplayer.core.logger.AppLogger
import javax.inject.Inject

sealed class AuthState { object Idle : AuthState(); object Loading : AuthState(); object Success : AuthState(); data class Error(val msg: String) : AuthState() }

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase,
    private val loginUseCase: LoginUseCase,
    private val saveTokens: SaveTokensUseCase,
    private val getSavedAccounts: GetSavedAccountsUseCase,
    private val selectAccount: SelectAccountUseCase,
    private val hasActiveSessionUseCase: HasActiveSessionUseCase,
    private val deleteAccount: DeleteAccountUseCase,
    private val enterServiceAccount: EnterServiceAccountUseCase,
    private val accountRepo: AccountRepository,
    private val logger: AppLogger
) : ViewModel() {
    private val _state = mutableStateOf<AuthState>(AuthState.Idle)
    val state: State<AuthState> = _state
    
    private val _savedAccounts = MutableStateFlow<List<SavedAccount>>(emptyList())
    val savedAccounts: StateFlow<List<SavedAccount>> = _savedAccounts

    init {
        loadSavedAccounts()
    }

    fun loadSavedAccounts() = viewModelScope.launch {
        _savedAccounts.value = getSavedAccounts()
    }

    /**
     * Уже есть активная сессия (сохранённые токены)? Если да — при запуске можно
     * сразу открыть приложение, не выполняя повторный вход и обновление токена,
     * из-за которого перезапускался сервис плеера.
     */
    suspend fun hasActiveSession(): Boolean = hasActiveSessionUseCase()

    fun register(email: String, password: String, name: String?) = viewModelScope.launch {
        _state.value = AuthState.Loading
        try {
            logger.info("AuthViewModel", "Attempting to register user: $email")
            val resp = registerUseCase(email, password, name)
            // Сохраняем токены (используем device_id как userId, так как AccountDto не содержит id)
            try {
                val accountDto = accountRepo.getAccount()
                saveTokens(resp.access_token, resp.refresh_token, resp.device_id, email, resp.device_id, accountDto.name ?: name, null)
            } catch (e: Exception) {
                logger.warn("AuthViewModel", "Failed to get account info after registration", e)
                // Если не удалось получить данные аккаунта, используем device_id как userId
                saveTokens(resp.access_token, resp.refresh_token, resp.device_id, email, resp.device_id, name, null)
            }
            loadSavedAccounts()
            _state.value = AuthState.Success
            logger.info("AuthViewModel", "Registration successful for: $email")
        } catch (e: Exception) {
            logger.error("AuthViewModel", "Registration failed for: $email", e)
            _state.value = AuthState.Error(e.message ?: "Ошибка регистрации")
        }
    }

    fun login(email: String, password: String, device: String, rememberMe: Boolean = false) = viewModelScope.launch {
        _state.value = AuthState.Loading
        try {
            logger.info("AuthViewModel", "Attempting to login user: $email, rememberMe: $rememberMe")
            val resp = loginUseCase(email, password, device)
            // Сначала сохраняем токены, чтобы они были доступны для последующих запросов
            saveTokens(resp.access_token, resp.refresh_token, resp.device_id, email, resp.device_id, null, if (rememberMe) password else null)
            
            // Даем DataStore время на обновление Flow (небольшая задержка для гарантии)
            kotlinx.coroutines.delay(ru.fire_core.xauplayer.core.config.AppConfig.SHORT_DELAY_MS)
            
            // Теперь пытаемся получить данные аккаунта с сохраненными токенами
            try {
                val accountDto = accountRepo.getAccount()
                // Обновляем имя аккаунта если оно получено
                saveTokens(resp.access_token, resp.refresh_token, resp.device_id, email, resp.device_id, accountDto.name, if (rememberMe) password else null)
            } catch (e: Exception) {
                logger.warn("AuthViewModel", "Failed to get account info after login", e)
                // Игнорируем ошибку получения аккаунта, токены уже сохранены
            }
            loadSavedAccounts()
            _state.value = AuthState.Success
            logger.info("AuthViewModel", "Login successful for: $email")
        } catch (e: Exception) {
            logger.error("AuthViewModel", "Login failed for: $email", e)
            _state.value = AuthState.Error(e.message ?: "Ошибка входа")
        }
    }

    fun selectSavedAccount(email: String) = viewModelScope.launch {
        _state.value = AuthState.Loading
        try {
            logger.info("AuthViewModel", "Selecting saved account: $email")
            val success = selectAccount(email)
            if (success) {
                _state.value = AuthState.Success
                logger.info("AuthViewModel", "Account selection successful: $email")
            } else {
                // Токены истекли или отсутствуют, нужен повторный логин
                // Не устанавливаем состояние Success, чтобы показать форму логина
                _state.value = AuthState.Idle
                logger.info("AuthViewModel", "Account tokens expired, login required: $email")
            }
        } catch (e: Exception) {
            logger.error("AuthViewModel", "Account selection failed: $email", e)
            _state.value = AuthState.Error(e.message ?: "Ошибка выбора аккаунта")
        }
    }

    fun deleteSavedAccount(email: String) = viewModelScope.launch {
        deleteAccount(email)
        loadSavedAccounts()
    }

    /**
     * Вход в служебный (оффлайн) аккаунт для прослушивания скачанного контента
     * без доступа к серверу (Req 4).
     */
    fun enterOfflineMode() = viewModelScope.launch {
        _state.value = AuthState.Loading
        try {
            logger.info("AuthViewModel", "Entering offline service account")
            enterServiceAccount()
            loadSavedAccounts()
            _state.value = AuthState.Success
        } catch (e: Exception) {
            logger.error("AuthViewModel", "Failed to enter offline service account", e)
            _state.value = AuthState.Error(e.message ?: "Не удалось войти в оффлайн-режим")
        }
    }
}


