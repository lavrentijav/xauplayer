package ru.fire_core.xauplayer.core.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Глобальный менеджер для событий аутентификации
 * Используется для уведомления о необходимости перехода на экран логина
 */
object AuthManager {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val _needsLogin = MutableSharedFlow<String?>(replay = 0)
    val needsLogin: SharedFlow<String?> = _needsLogin.asSharedFlow()
    
    private val _lastEmail = MutableStateFlow<String?>(null)
    val lastEmail: StateFlow<String?> = _lastEmail.asStateFlow()

    /**
     * Отправляет событие о необходимости перехода на экран логина
     * @param email - email для подстановки (если был сохранен)
     */
    fun requestLogin(email: String? = null) {
        scope.launch {
            _lastEmail.value = email
            _needsLogin.emit(email)
        }
    }
    
    /**
     * Очищает сохраненный email
     */
    fun clearLastEmail() {
        _lastEmail.value = null
    }
}

