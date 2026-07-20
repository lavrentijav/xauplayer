package ru.fire_core.xauplayer.ui.screens.tabs.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.ui.viewmodel.UpdateViewModel

@Composable
fun UpdateDialog(
    updateViewModel: UpdateViewModel,
    onDismiss: () -> Unit
) {
    val updateState by updateViewModel.updateProgress.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    
    // Проверяем разрешение на установку (обновляем при каждом изменении состояния)
    var canInstall by remember { mutableStateOf(updateViewModel.canInstallPackages()) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    // Обновляем проверку разрешения при изменении состояния диалога
    LaunchedEffect(updateState.state) {
        canInstall = updateViewModel.canInstallPackages()
    }

    LaunchedEffect(Unit) {
        scope.launch {
            updateViewModel.checkForUpdate()
        }
    }

    Dialog(
        onDismissRequest = { if (updateState.state != ru.fire_core.xauplayer.update.UpdateState.DOWNLOADING) onDismiss() },
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
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        "Обновление приложения",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (updateState.state != ru.fire_core.xauplayer.update.UpdateState.DOWNLOADING) {
                        IconButton(onClick = onDismiss) {
                            Text("✕", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Статусбар с описанием текущего действия
                updateState.statusMessage?.let { statusMsg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = statusMsg,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Прикольная фраза (актуально / вы в прошлом / вы из будущего)
                updateState.funMessage?.let { fun2 ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = fun2,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                when (updateState.state) {
                    ru.fire_core.xauplayer.update.UpdateState.CHECKING -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Проверка обновлений...")
                    }
                    ru.fire_core.xauplayer.update.UpdateState.UPDATE_AVAILABLE -> {
                        updateState.updateInfo?.let { info ->
                            Text(
                                "Доступна новая версия: ${info.versionName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            info.description?.let { desc ->
                                Text(desc)
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            Button(
                                onClick = {
                                    // Проверяем разрешение перед началом загрузки
                                    val hasPermission = updateViewModel.canInstallPackages()
                                    if (!hasPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        // Показываем диалог с просьбой разрешить установку
                                        showPermissionDialog = true
                                    } else {
                                        // Разрешение есть или не требуется - начинаем загрузку
                                        scope.launch {
                                            updateViewModel.downloadAndInstall()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Обновить")
                            }
                        }
                    }
                    ru.fire_core.xauplayer.update.UpdateState.DOWNLOADING -> {
                        Column {
                            Text(
                                "Загрузка обновления...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            // Прогрессбар с процентами
                            LinearProgressIndicator(
                                progress = { updateState.progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    "${updateState.progress}%",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                val mbDownloaded = updateState.downloadedBytes / (1024.0 * 1024.0)
                                val mbTotal = updateState.totalBytes / (1024.0 * 1024.0)
                                if (mbTotal > 0) {
                                    Text(
                                        "${"%.1f".format(mbDownloaded)} MB / ${"%.1f".format(mbTotal)} MB",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                    ru.fire_core.xauplayer.update.UpdateState.DOWNLOADED -> {
                        Text("Обновление загружено!")
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Показываем предупреждение, если нет разрешения на установку
                        if (!canInstall && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "Требуется разрешение на установку приложений",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Нажмите кнопку ниже, чтобы открыть настройки и предоставить разрешение",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (activity != null) {
                                        updateViewModel.requestInstallPermission(activity)
                                    } else {
                                        // Если Activity недоступна, открываем настройки напрямую
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                            data = android.net.Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Открыть настройки разрешений")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    // Проверяем разрешение перед установкой
                                    if (updateViewModel.canInstallPackages()) {
                                        updateViewModel.installUpdate()
                                    } else {
                                        // Если разрешения нет, показываем сообщение
                                        if (activity != null) {
                                            updateViewModel.requestInstallPermission(activity)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canInstall || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O
                        ) {
                            Text(if (canInstall || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) "Установить" else "Требуется разрешение")
                        }
                    }
                    ru.fire_core.xauplayer.update.UpdateState.INSTALLING -> {
                        Text(
                            "Установка запущена",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Установка продолжается в фоне",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Вы можете закрыть это окно и приложение. Установка продолжится в системном установщике. Проверьте уведомления системы для отслеживания прогресса.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Закрыть")
                        }
                    }
                    ru.fire_core.xauplayer.update.UpdateState.ERROR -> {
                        Text(
                            "Ошибка: ${updateState.error ?: "Неизвестная ошибка"}",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Закрыть")
                        }
                    }
                    ru.fire_core.xauplayer.update.UpdateState.IDLE -> {
                        Text("Обновления не найдены")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    updateViewModel.checkForUpdate()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Проверить снова")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Закрыть")
                        }
                    }
                }
            }
        }
    }
    
    // Диалог с просьбой разрешить установку приложений
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = {
                Text("Требуется разрешение")
            },
            text = {
                Text("Для установки обновлений необходимо разрешить установку приложений из неизвестных источников. Нажмите \"Открыть настройки\" и включите разрешение для этого приложения.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        if (activity != null) {
                            updateViewModel.requestInstallPermission(activity)
                        } else {
                            // Если Activity недоступна, открываем настройки напрямую
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    }
                ) {
                    Text("Открыть настройки")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

