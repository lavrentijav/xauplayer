package ru.fire_core.xauplayer.ui.screens.tabs.settings

import androidx.compose.foundation.clickable
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
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.ui.viewmodel.UpdateViewModel

@Composable
fun AboutSettingsDialog(
    updateViewModel: UpdateViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    var showUpdateDialog by remember { mutableStateOf(false) }
    // Пасхалка: считаем нажатия на версию
    var versionTaps by remember { mutableStateOf(0) }
    val tapMessage = ru.fire_core.xauplayer.core.humor.FunPhrases.versionTap(versionTaps)
    // Пасхалка «стань разработчиком» — нажатия на номер сборки (как в Android)
    var buildTaps by remember { mutableStateOf(0) }
    val buildTapMessage = ru.fire_core.xauplayer.core.humor.FunPhrases.buildNumberTap(buildTaps)
    // Случайная отсылка на фильмы/сериалы/аниме (выбирается один раз при открытии)
    val reference = remember { ru.fire_core.xauplayer.core.humor.FunPhrases.aboutReference() }
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
                        "О приложении",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", style = MaterialTheme.typography.headlineMedium)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Text(
                    "XAuPlayer v${ru.fire_core.xauplayer.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { versionTaps++ }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Аудиоплеер для прослушивания книг",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Номер сборки — тыкаем, чтобы «стать разработчиком» (как в Android)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Номер сборки: ${ru.fire_core.xauplayer.BuildConfig.VERSION_CODE}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { buildTaps++ }
                )

                // Прикольная отсылка
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    reference,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Пасхалки с нажатиями (на версию и на номер сборки)
                (tapMessage ?: buildTapMessage)?.let { msg ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            msg,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { showUpdateDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Проверить обновления")
                }
            }
        }
    }

    if (showUpdateDialog) {
        UpdateDialog(
            updateViewModel = updateViewModel,
            onDismiss = { showUpdateDialog = false }
        )
    }
}

