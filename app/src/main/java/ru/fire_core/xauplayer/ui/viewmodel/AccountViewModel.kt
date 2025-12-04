package ru.fire_core.xauplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.data.network.AccountDto
import ru.fire_core.xauplayer.data.local.entities.Device
import ru.fire_core.xauplayer.domain.usecase.account.AccountUseCases
import ru.fire_core.xauplayer.domain.usecase.auth.LogoutUseCase
import ru.fire_core.xauplayer.core.logger.AppLogger
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val use: AccountUseCases,
    private val logoutUseCase: LogoutUseCase,
    private val logger: AppLogger
) : ViewModel() {
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices
    private val _activity = MutableStateFlow<Map<String, Int>>(emptyMap())
    val activity: StateFlow<Map<String, Int>> = _activity
    private val _account = MutableStateFlow<AccountDto?>(null)
    val account: StateFlow<AccountDto?> = _account

    fun load() = viewModelScope.launch {
        try {
            use.refreshDevices()
            _devices.value = use.getDevices()
            _activity.value = use.getActivity()
            _account.value = use.getAccount()
        } catch (e: Exception) {
            logger.error("AccountViewModel", "Failed to load account data", e)
        }
    }

    fun revoke(id: Long) = viewModelScope.launch {
        try { 
            use.revokeDevice(id)
            load() 
        } catch (e: Exception) {
            logger.error("AccountViewModel", "Failed to revoke device $id", e)
        }
    }

    fun logout(onSuccess: () -> Unit) = viewModelScope.launch {
        try { 
            // При выходе удаляем токены (saveAccount = false)
            logoutUseCase(saveAccount = false)
            onSuccess() 
        } catch (e: Exception) {
            logger.error("AccountViewModel", "Failed to logout", e)
        }
    }
    
    fun switchAccount(onSuccess: () -> Unit) = viewModelScope.launch {
        try { 
            // При переключении аккаунта сохраняем сессию (saveAccount = true)
            logoutUseCase(saveAccount = true)
            onSuccess() 
        } catch (e: Exception) {
            logger.error("AccountViewModel", "Failed to switch account", e)
        }
    }
}


