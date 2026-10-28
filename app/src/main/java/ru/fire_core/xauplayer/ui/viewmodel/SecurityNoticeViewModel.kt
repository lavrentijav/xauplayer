package ru.fire_core.xauplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ru.fire_core.xauplayer.core.security.SecurityNoticeStore
import javax.inject.Inject

@HiltViewModel
class SecurityNoticeViewModel @Inject constructor(val store: SecurityNoticeStore) : ViewModel()
