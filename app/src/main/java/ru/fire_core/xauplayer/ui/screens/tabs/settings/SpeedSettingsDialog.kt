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
fun SpeedSettingsDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val defaultSpeed by viewModel.defaultSpeed.collectAsState()
    val rewindSeconds by viewModel.rewindSeconds.collectAsState()
    val forwardSeconds by viewModel.forwardSeconds.collectAsState()
    val maxConcurrentDownloads by viewModel.maxConcurrentDownloads.collectAsState()
    
    var editedDefaultSpeed by remember(defaultSpeed) { mutableStateOf(defaultSpeed.toString()) }
    var editedRewindSeconds by remember(rewindSeconds) { mutableStateOf(rewindSeconds.toString()) }
    var editedForwardSeconds by remember(forwardSeconds) { mutableStateOf(forwardSeconds.toString()) }
    var editedMaxConcurrentDownloads by remember(maxConcurrentDownloads) { mutableStateOf(maxConcurrentDownloads.toString()) }
    
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
                        "Скорость и перемотка",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", style = MaterialTheme.typography.headlineMedium)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text("Скорость воспроизведения по умолчанию")
                OutlinedTextField(
                    value = editedDefaultSpeed,
                    onValueChange = { editedDefaultSpeed = it },
                    label = { Text("Скорость (0.5 - 3.0)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("1.0") },
                    supportingText = { Text("Текущее значение: ${"%.2f".format(defaultSpeed)}x") }
                )
                if (editedDefaultSpeed != defaultSpeed.toString()) {
                    Button(
                        onClick = {
                            editedDefaultSpeed.toFloatOrNull()?.let { speed ->
                                if (speed in 0.5f..3.0f) {
                                    scope.launch {
                                        viewModel.setDefaultSpeed(speed)
                                        editedDefaultSpeed = speed.toString()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editedDefaultSpeed.toFloatOrNull()?.let { it in 0.5f..3.0f } ?: false
                    ) {
                        Text("Сохранить скорость")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Перемотка назад (секунды)")
                OutlinedTextField(
                    value = editedRewindSeconds,
                    onValueChange = { editedRewindSeconds = it },
                    label = { Text("Секунды") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("30") },
                    supportingText = { Text("Текущее значение: ${rewindSeconds} сек") }
                )
                if (editedRewindSeconds != rewindSeconds.toString()) {
                    Button(
                        onClick = {
                            editedRewindSeconds.toIntOrNull()?.let { seconds ->
                                if (seconds > 0) {
                                    scope.launch {
                                        viewModel.setRewindSeconds(seconds)
                                        editedRewindSeconds = seconds.toString()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editedRewindSeconds.toIntOrNull()?.let { it > 0 } ?: false
                    ) {
                        Text("Сохранить перемотку назад")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Перемотка вперед (секунды)")
                OutlinedTextField(
                    value = editedForwardSeconds,
                    onValueChange = { editedForwardSeconds = it },
                    label = { Text("Секунды") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("15") },
                    supportingText = { Text("Текущее значение: ${forwardSeconds} сек") }
                )
                if (editedForwardSeconds != forwardSeconds.toString()) {
                    Button(
                        onClick = {
                            editedForwardSeconds.toIntOrNull()?.let { seconds ->
                                if (seconds > 0) {
                                    scope.launch {
                                        viewModel.setForwardSeconds(seconds)
                                        editedForwardSeconds = seconds.toString()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editedForwardSeconds.toIntOrNull()?.let { it > 0 } ?: false
                    ) {
                        Text("Сохранить перемотку вперед")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Максимальное количество параллельных загрузок")
                OutlinedTextField(
                    value = editedMaxConcurrentDownloads,
                    onValueChange = { editedMaxConcurrentDownloads = it },
                    label = { Text("Количество потоков (1-10)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("4") },
                    supportingText = { Text("Текущее значение: $maxConcurrentDownloads потоков") }
                )
                if (editedMaxConcurrentDownloads != maxConcurrentDownloads.toString()) {
                    Button(
                        onClick = {
                            editedMaxConcurrentDownloads.toIntOrNull()?.let { count ->
                                if (count in 1..10) {
                                    scope.launch {
                                        viewModel.setMaxConcurrentDownloads(count)
                                        editedMaxConcurrentDownloads = count.toString()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editedMaxConcurrentDownloads.toIntOrNull()?.let { it in 1..10 } ?: false
                    ) {
                        Text("Сохранить количество потоков")
                    }
                }
            }
        }
    }
}

