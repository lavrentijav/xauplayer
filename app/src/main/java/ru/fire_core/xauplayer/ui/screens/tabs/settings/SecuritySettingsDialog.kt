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
import ru.fire_core.xauplayer.core.config.AppConfig
import ru.fire_core.xauplayer.ui.viewmodel.SettingsViewModel

/**
 * Диалог параметров безопасности и оффлайн-режима.
 *
 * Содержит:
 *  - переключатель оффлайн-режима (Req 7): автовход в служебный аккаунт;
 *  - выбор времени истечения сессии в днях (Req 6);
 *  - опцию «не обновлять сессию без сервера» (Req 2);
 *  - кнопку ручного обновления сессии (Req 5).
 */
@Composable
fun SecuritySettingsDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val offlineMode by viewModel.offlineMode.collectAsState()
    val sessionExpiryDays by viewModel.sessionExpiryDays.collectAsState()
    val skipRefreshWhenOffline by viewModel.skipRefreshWhenOffline.collectAsState()
    val sessionRefreshResult by viewModel.sessionRefreshResult.collectAsState()

    var editedExpiryDays by remember(sessionExpiryDays) {
        mutableStateOf(sessionExpiryDays.toString())
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
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Безопасность",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", style = MaterialTheme.typography.headlineMedium)
                    }
                }

                HorizontalDivider()

                // Оффлайн-режим (Req 7)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Оффлайн-режим", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Автовход в служебный аккаунт для прослушивания скачанного. " +
                                "Запросы прежнего аккаунта буферизуются.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = offlineMode,
                        onCheckedChange = { viewModel.setOfflineMode(it) }
                    )
                }

                HorizontalDivider()

                // Обновление сессии (Req 5)
                Text("Сессия", fontWeight = FontWeight.SemiBold)
                Button(
                    onClick = { viewModel.refreshSession() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Обновить сессию")
                }
                sessionRefreshResult?.let { ok ->
                    Text(
                        if (ok) "Сессия обновлена" else "Не удалось обновить сессию",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                // Время истечения сессии (Req 6)
                Text("Через сколько дней истекает сессия")
                OutlinedTextField(
                    value = editedExpiryDays,
                    onValueChange = { editedExpiryDays = it.filter { c -> c.isDigit() } },
                    label = { Text("Дни") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(AppConfig.DEFAULT_SESSION_EXPIRY_DAYS.toString()) },
                    supportingText = {
                        Text(
                            "Текущее: $sessionExpiryDays дн. " +
                                "(от ${AppConfig.MIN_SESSION_EXPIRY_DAYS} до ${AppConfig.MAX_SESSION_EXPIRY_DAYS})"
                        )
                    }
                )
                editedExpiryDays.toIntOrNull()?.let { n ->
                    ru.fire_core.xauplayer.core.humor.FunPhrases.numberEgg(n)?.let { egg ->
                        Text(egg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (editedExpiryDays.toIntOrNull() != sessionExpiryDays) {
                    Button(
                        onClick = {
                            editedExpiryDays.toIntOrNull()?.let { days ->
                                scope.launch {
                                    viewModel.setSessionExpiryDays(days)
                                    editedExpiryDays = days.coerceIn(
                                        AppConfig.MIN_SESSION_EXPIRY_DAYS,
                                        AppConfig.MAX_SESSION_EXPIRY_DAYS
                                    ).toString()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editedExpiryDays.toIntOrNull()?.let { it > 0 } ?: false
                    ) {
                        Text("Сохранить время истечения")
                    }
                }

                HorizontalDivider()

                // Не обновлять сессию без сервера (Req 2)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Локальное продление сессии", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Не пытаться обновлять сессию без доступа к серверу — " +
                                "продлевать её локально.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = skipRefreshWhenOffline,
                        onCheckedChange = { viewModel.setSkipRefreshWhenOffline(it) }
                    )
                }
            }
        }
    }
}
