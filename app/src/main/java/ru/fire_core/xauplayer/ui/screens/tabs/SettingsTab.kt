package ru.fire_core.xauplayer.ui.screens.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.fire_core.xauplayer.ui.viewmodel.SettingsViewModel
import ru.fire_core.xauplayer.ui.screens.tabs.settings.*

enum class SettingsCategory(val title: String) {
    MAIN("Основные"),
    PLAYBACK("Воспроизведение"),
    APPEARANCE("Внешний вид"),
    ADVANCED("Дополнительно")
}

@Composable
fun SettingsTab(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    accountViewModel: ru.fire_core.xauplayer.ui.viewmodel.AccountViewModel = hiltViewModel(),
    updateViewModel: ru.fire_core.xauplayer.ui.viewmodel.UpdateViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null
) {
    var showServerSettings by remember { mutableStateOf(false) }
    var showPlaybackSettings by remember { mutableStateOf(false) }
    var showSpeedSettings by remember { mutableStateOf(false) }
    var showSyncSettings by remember { mutableStateOf(false) }
    var showBufferSettings by remember { mutableStateOf(false) }
    var showAppearanceSettings by remember { mutableStateOf(false) }
    var showDebugSettings by remember { mutableStateOf(false) }
    var showSecuritySettings by remember { mutableStateOf(false) }
    var showDevicesSettings by remember { mutableStateOf(false) }
    var showAboutSettings by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showLogsScreen by remember { mutableStateOf(false) }
    
    var selectedCategory by remember { mutableStateOf(SettingsCategory.MAIN) }
    
    val showLogs by viewModel.showLogs.collectAsState()

    if (showLogsScreen) {
        // Показываем экран логов
        LogsTab(modifier = Modifier.fillMaxSize())
        
        // Кнопка "Назад" через FloatingActionButton
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = { showLogsScreen = false },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("✕", style = MaterialTheme.typography.headlineMedium)
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize()
        ) {
            // Кнопка "Назад" если есть onBack
            onBack?.let {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextButton(onClick = it) {
                        Text("← Назад")
                    }
                }
            }
            
            Text(
                "Настройки",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // Кнопка проверки обновлений вне вкладок
            Button(
                onClick = { showUpdateDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Проверить обновление")
            }

            HorizontalDivider()

            // Вкладки категорий
            TabRow(selectedTabIndex = selectedCategory.ordinal) {
                SettingsCategory.values().forEach { category ->
                    Tab(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        text = { Text(category.title) }
                    )
                }
            }

            // Содержимое выбранной вкладки
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedCategory) {
                    SettingsCategory.MAIN -> {
                        // Основные настройки (самое нужное)
                        SettingsButton(
                            title = "Сервер",
                            description = "Адрес сервера",
                            onClick = { showServerSettings = true }
                        )

                        SettingsButton(
                            title = "Воспроизведение",
                            description = "Авто скачивание, авто удаление",
                            onClick = { showPlaybackSettings = true }
                        )

                        SettingsButton(
                            title = "Безопасность",
                            description = "Оффлайн-режим, сессия, истечение сессии",
                            onClick = { showSecuritySettings = true }
                        )
                    }
                    SettingsCategory.PLAYBACK -> {
                        // Настройки воспроизведения
                        SettingsButton(
                            title = "Скорость и перемотка",
                            description = "Скорость, перемотка назад/вперед",
                            onClick = { showSpeedSettings = true }
                        )

                        SettingsButton(
                            title = "Буферизация",
                            description = "Настройки буферизации аудио",
                            onClick = { showBufferSettings = true }
                        )

                        SettingsButton(
                            title = "Синхронизация",
                            description = "Интервалы синхронизации",
                            onClick = { showSyncSettings = true }
                        )
                    }
                    SettingsCategory.APPEARANCE -> {
                        // Настройки внешнего вида
                        SettingsButton(
                            title = "Внешний вид",
                            description = "Цвета, темы",
                            onClick = { showAppearanceSettings = true }
                        )
                    }
                    SettingsCategory.ADVANCED -> {
                        // Дополнительные настройки
                        SettingsButton(
                            title = "Отладка",
                            description = "Логи, диагностика",
                            onClick = { showDebugSettings = true }
                        )

                        SettingsButton(
                            title = "Устройства",
                            description = "Вошедшие устройства",
                            onClick = { showDevicesSettings = true }
                        )

                        SettingsButton(
                            title = "О приложении",
                            description = "Версия, информация",
                            onClick = { showAboutSettings = true }
                        )
                    }
                }
            }
        }

        // Диалоги настроек
        if (showServerSettings) {
            ServerSettingsDialog(
                viewModel = viewModel,
                onDismiss = { showServerSettings = false }
            )
        }

        if (showPlaybackSettings) {
            PlaybackSettingsDialog(
                viewModel = viewModel,
                onDismiss = { showPlaybackSettings = false }
            )
        }

        if (showSpeedSettings) {
            SpeedSettingsDialog(
                viewModel = viewModel,
                onDismiss = { showSpeedSettings = false }
            )
        }

        if (showSyncSettings) {
            SyncSettingsDialog(
                viewModel = viewModel,
                onDismiss = { showSyncSettings = false }
            )
        }

        if (showBufferSettings) {
            BufferSettingsDialog(
                viewModel = viewModel,
                onDismiss = { showBufferSettings = false }
            )
        }

        if (showAppearanceSettings) {
            AppearanceSettingsDialog(
                viewModel = viewModel,
                onDismiss = { showAppearanceSettings = false }
            )
        }

        if (showDebugSettings) {
            DebugSettingsDialog(
                viewModel = viewModel,
                onDismiss = { showDebugSettings = false },
                onShowLogs = { showLogsScreen = true }
            )
        }

        if (showSecuritySettings) {
            SecuritySettingsDialog(
                viewModel = viewModel,
                onDismiss = { showSecuritySettings = false }
            )
        }

        if (showDevicesSettings) {
            DevicesSettingsDialog(
                accountViewModel = accountViewModel,
                onDismiss = { showDevicesSettings = false }
            )
        }

        if (showAboutSettings) {
            AboutSettingsDialog(
                onDismiss = { showAboutSettings = false }
            )
        }

        if (showUpdateDialog) {
            ru.fire_core.xauplayer.ui.screens.tabs.settings.UpdateDialog(
                updateViewModel = updateViewModel,
                onDismiss = { showUpdateDialog = false }
            )
        }
    }
}

@Composable
private fun SettingsButton(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("→", style = MaterialTheme.typography.titleLarge)
        }
    }
}

