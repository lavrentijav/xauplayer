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
fun PlaybackSettingsDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val autoDownload by viewModel.autoDownload.collectAsState()
    val autoDelete by viewModel.autoDelete.collectAsState()
    val autoPlayNextSeriesBook by viewModel.autoPlayNextSeriesBook.collectAsState()
    val useSystemMediaPlayer by viewModel.useSystemMediaPlayer.collectAsState()
    val maxDownloadSpeed by viewModel.maxDownloadSpeed.collectAsState()
    var editedMaxDownloadSpeed by remember(maxDownloadSpeed) { 
        mutableStateOf(if (maxDownloadSpeed == 0) "" else maxDownloadSpeed.toString()) 
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
                        "Настройки воспроизведения",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", style = MaterialTheme.typography.headlineMedium)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Авто скачивание книги")
                    Switch(
                        checked = autoDownload,
                        onCheckedChange = {
                            scope.launch {
                                viewModel.setAutoDownload(it)
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Авто удаление после прослушивания")
                    Switch(
                        checked = autoDelete,
                        onCheckedChange = {
                            scope.launch {
                                viewModel.setAutoDelete(it)
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Следующая книга в серии")
                        Text(
                            "После последней главы переходить к следующей книге с той же скоростью",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoPlayNextSeriesBook,
                        onCheckedChange = {
                            scope.launch {
                                viewModel.setAutoPlayNextSeriesBook(it)
                            }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text(
                    "Интеграция с системой",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    "Использовать системный MediaStyle уведомление для отображения в шторке, на экране блокировки, Android Auto и умных часах",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Системный MediaStyle",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            if (useSystemMediaPlayer) "Включено (рекомендуется)" else "Выключено (простой режим)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useSystemMediaPlayer,
                        onCheckedChange = {
                            scope.launch {
                                viewModel.setUseSystemMediaPlayer(it)
                            }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text(
                    "Максимальная скорость загрузки",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    "Ограничить скорость загрузки (KB/s). 0 = без ограничений",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = editedMaxDownloadSpeed,
                    onValueChange = { 
                        editedMaxDownloadSpeed = it.filter { char -> char.isDigit() }
                    },
                    label = { Text("Скорость (KB/s)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("0 - без ограничений") },
                    supportingText = { 
                        Text("Текущее значение: ${if (maxDownloadSpeed == 0) "Без ограничений" else "$maxDownloadSpeed KB/s"}") 
                    }
                )

                if (editedMaxDownloadSpeed != (if (maxDownloadSpeed == 0) "" else maxDownloadSpeed.toString())) {
                    Button(
                        onClick = {
                            val speed = editedMaxDownloadSpeed.toIntOrNull()?.coerceAtLeast(0) ?: 0
                            scope.launch {
                                viewModel.setMaxDownloadSpeed(speed)
                                editedMaxDownloadSpeed = if (speed == 0) "" else speed.toString()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

