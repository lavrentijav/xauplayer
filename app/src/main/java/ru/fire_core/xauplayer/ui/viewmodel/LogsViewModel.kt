package ru.fire_core.xauplayer.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.core.logger.LogEntry
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val logger: AppLogger,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadLogs(limit: Int = 500) = viewModelScope.launch {
        _isLoading.value = true
        try {
            _logs.value = logger.getLogs(limit)
        } catch (e: Exception) {
            // Логируем ошибку чтения логов
            logger.error("LogsViewModel", "Failed to load logs", e)
        } finally {
            _isLoading.value = false
        }
    }

    fun clearLogs() = viewModelScope.launch {
        try {
            logger.clearLogs()
            _logs.value = emptyList()
        } catch (e: Exception) {
            logger.error("LogsViewModel", "Failed to clear logs", e)
        }
    }

    suspend fun exportLogs(): Intent? {
        return try {
            val exportFile = logger.exportLogsToFile()
            if (exportFile != null) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    exportFile
                )
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "XAuPlayer Logs")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("LogsViewModel", "Failed to export logs", e)
            null
        }
    }
}

