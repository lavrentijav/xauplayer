package ru.fire_core.xauplayer.ui.screens.tabs.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.ui.viewmodel.SettingsViewModel

@Composable
fun SyncSettingsDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val progressSyncInterval by viewModel.progressSyncInterval.collectAsState()
    var editedProgressSyncInterval by remember(progressSyncInterval) { 
        mutableStateOf((progressSyncInterval / 1000).toString()) 
    }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Настройки синхронизации",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", style = MaterialTheme.typography.headlineMedium)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text("Интервал синхронизации прогресса (секунды)")
                OutlinedTextField(
                    value = editedProgressSyncInterval,
                    onValueChange = { editedProgressSyncInterval = it },
                    label = { Text("Секунды") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("30") },
                    supportingText = { 
                        Text("Текущее значение: ${progressSyncInterval / 1000} сек (${progressSyncInterval} мс)") 
                    }
                )
                if (editedProgressSyncInterval.toIntOrNull()?.let { it * 1000 } != progressSyncInterval) {
                    Button(
                        onClick = {
                            editedProgressSyncInterval.toIntOrNull()?.let { seconds ->
                                if (seconds > 0) {
                                    scope.launch {
                                        viewModel.setProgressSyncInterval(seconds * 1000)
                                        editedProgressSyncInterval = seconds.toString()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editedProgressSyncInterval.toIntOrNull()?.let { it > 0 } ?: false
                    ) {
                        Text("Сохранить интервал синхронизации")
                    }
                }
            }
        }
    }
}

