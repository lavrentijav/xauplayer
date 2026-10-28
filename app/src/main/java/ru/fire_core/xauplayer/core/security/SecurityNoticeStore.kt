package ru.fire_core.xauplayer.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Уведомление безопасности от сервера (например, «токен утёк, смените пароль»), показывается один раз. */
@Singleton
class SecurityNoticeStore @Inject constructor() {
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    fun post(text: String?) {
        if (!text.isNullOrBlank()) _notice.value = text
    }

    fun dismiss() {
        _notice.value = null
    }
}
