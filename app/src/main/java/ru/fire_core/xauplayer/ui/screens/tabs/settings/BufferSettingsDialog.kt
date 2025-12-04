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
fun BufferSettingsDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val bufferBefore by viewModel.bufferBefore.collectAsState()
    val bufferAfter by viewModel.bufferAfter.collectAsState()
    
    var editedBufferBefore by remember(bufferBefore) { mutableStateOf(bufferBefore.toString()) }
    var editedBufferAfter by remember(bufferAfter) { mutableStateOf(bufferAfter.toString()) }
    
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
                        "Буферизация аудио",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", style = MaterialTheme.typography.headlineMedium)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text("Буферизация до текущего куска")
                OutlinedTextField(
                    value = editedBufferBefore,
                    onValueChange = { editedBufferBefore = it },
                    label = { Text("Количество кусков") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("1") },
                    supportingText = { Text("Текущее значение: ${bufferBefore}") }
                )
                if (editedBufferBefore != bufferBefore.toString()) {
                    Button(
                        onClick = {
                            editedBufferBefore.toIntOrNull()?.let { count ->
                                if (count >= 0) {
                                    scope.launch {
                                        viewModel.setBufferBefore(count)
                                        editedBufferBefore = count.toString()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editedBufferBefore.toIntOrNull()?.let { it >= 0 } ?: false
                    ) {
                        Text("Сохранить буферизацию до")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Буферизация после текущего куска")
                OutlinedTextField(
                    value = editedBufferAfter,
                    onValueChange = { editedBufferAfter = it },
                    label = { Text("Количество кусков") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("1") },
                    supportingText = { Text("Текущее значение: ${bufferAfter}") }
                )
                if (editedBufferAfter != bufferAfter.toString()) {
                    Button(
                        onClick = {
                            editedBufferAfter.toIntOrNull()?.let { count ->
                                if (count >= 0) {
                                    scope.launch {
                                        viewModel.setBufferAfter(count)
                                        editedBufferAfter = count.toString()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editedBufferAfter.toIntOrNull()?.let { it >= 0 } ?: false
                    ) {
                        Text("Сохранить буферизацию после")
                    }
                }
            }
        }
    }
}

