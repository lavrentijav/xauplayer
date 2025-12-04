package ru.fire_core.xauplayer.ui.screens.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.fire_core.xauplayer.core.logger.LogEntry
import ru.fire_core.xauplayer.core.logger.LogLevel
import ru.fire_core.xauplayer.ui.viewmodel.LogsViewModel

@Composable
fun LogsTab(
    modifier: Modifier = Modifier,
    vm: LogsViewModel = hiltViewModel()
) {
    val logs by vm.logs.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        vm.loadLogs()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Заголовок и кнопки
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = "Логи",
                style = MaterialTheme.typography.titleLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val context = LocalContext.current
                IconButton(onClick = {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        vm.exportLogs()?.let { intent ->
                            context.startActivity(Intent.createChooser(intent, "Экспортировать логи"))
                        }
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Экспортировать логи",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { vm.clearLogs() }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Очистить логи",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Список логов
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = "Логи отсутствуют",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs) { logEntry ->
                    LogEntryItem(logEntry = logEntry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryItem(logEntry: LogEntry) {
    val backgroundColor = when (logEntry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiaryContainer
        LogLevel.INFO -> MaterialTheme.colorScheme.primaryContainer
        LogLevel.DEBUG -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = when (logEntry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        LogLevel.WARN -> MaterialTheme.colorScheme.onTertiaryContainer
        LogLevel.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = logEntry.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f)
                )
                Surface(
                    color = when (logEntry.level) {
                        LogLevel.ERROR -> Color.Red.copy(alpha = 0.3f)
                        LogLevel.WARN -> Color(0xFFFFA500).copy(alpha = 0.3f)
                        LogLevel.INFO -> Color.Blue.copy(alpha = 0.3f)
                        LogLevel.DEBUG -> Color.Gray.copy(alpha = 0.3f)
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = logEntry.level.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor
                    )
                }
            }
            Text(
                text = "[${logEntry.tag}]",
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.8f),
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = logEntry.message,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }
    }
}

