package ru.fire_core.xauplayer.ui.screens.tabs.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.ui.components.ColorPicker
import ru.fire_core.xauplayer.ui.viewmodel.SettingsViewModel

@Composable
fun AppearanceSettingsDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val accentColor by viewModel.accentColor.collectAsState()
    val playerColor by viewModel.playerColor.collectAsState()
    
    var showAccentColorPicker by remember { mutableStateOf(false) }
    var showPlayerColorPicker by remember { mutableStateOf(false) }
    
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
                        "Внешний вид",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", style = MaterialTheme.typography.headlineMedium)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showAccentColorPicker = !showAccentColorPicker }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Акцентный цвет (кнопки, рамки)")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        Color(AndroidColor.parseColor(accentColor)),
                                        CircleShape
                                    )
                            )
                            Text(accentColor)
                        }
                    }
                }
                if (showAccentColorPicker) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ColorPicker(
                                currentColor = accentColor,
                                onColorChanged = { color ->
                                    scope.launch {
                                        viewModel.setAccentColor(color)
                                    }
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showPlayerColorPicker = !showPlayerColorPicker }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Цвет плеера")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        Color(AndroidColor.parseColor(playerColor)),
                                        CircleShape
                                    )
                            )
                            Text(playerColor)
                        }
                    }
                }
                if (showPlayerColorPicker) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ColorPicker(
                                currentColor = playerColor,
                                onColorChanged = { color ->
                                    scope.launch {
                                        viewModel.setPlayerColor(color)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

