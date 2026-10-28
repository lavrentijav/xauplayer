package ru.fire_core.xauplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.data.admin.AdminClient
import ru.fire_core.xauplayer.data.admin.AdminOp
import javax.inject.Inject

data class AdminUiState(
    val loggedIn: Boolean = false,
    val loading: Boolean = false,
    val ops: List<AdminOp> = emptyList(),
    val group: String? = null,
    val selected: AdminOp? = null,
    val result: String? = null,
    val error: String? = null
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val client: AdminClient
) : ViewModel() {
    private val _state = MutableStateFlow(AdminUiState(loggedIn = client.isLoggedIn))
    val state: StateFlow<AdminUiState> = _state

    fun login(email: String, password: String) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val ops = client.login(email, password)
                _state.value = AdminUiState(loggedIn = true, ops = ops, error = client.lastNotice)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Ошибка входа")
            }
        }
    }

    fun logout() {
        client.logout()
        _state.value = AdminUiState()
    }

    fun openGroup(group: String?) {
        _state.value = _state.value.copy(group = group, selected = null, result = null, error = null)
    }

    fun select(op: AdminOp?) {
        _state.value = _state.value.copy(selected = op, result = null, error = null)
    }

    fun run(op: AdminOp, values: Map<String, String>) {
        _state.value = _state.value.copy(loading = true, result = null, error = null)
        viewModelScope.launch {
            try {
                val r = client.execute(op, values)
                _state.value = _state.value.copy(loading = false, result = "HTTP ${r.code}\n" + pretty(r.body))
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Ошибка запроса")
            }
        }
    }

    private fun pretty(body: String): String = try {
        val t = body.trim()
        when {
            t.startsWith("{") -> org.json.JSONObject(t).toString(2)
            t.startsWith("[") -> org.json.JSONArray(t).toString(2)
            else -> body
        }
    } catch (e: Exception) {
        body
    }.take(20000)
}
