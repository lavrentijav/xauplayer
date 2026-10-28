package ru.fire_core.xauplayer.ui.screens.tabs.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import ru.fire_core.xauplayer.data.admin.AdminOp
import ru.fire_core.xauplayer.ui.viewmodel.AdminViewModel

/**
 * Админка. Список групп/операций и формы строятся из манифеста, который отдаёт сервер админ-сессии:
 * в приложении нет ни одного админского пути.
 */
@Composable
fun AdminDialog(
    defaultEmail: String = "",
    viewModel: AdminViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Администрирование", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row {
                        if (state.loggedIn) TextButton(onClick = { viewModel.logout() }) { Text("Выйти") }
                        TextButton(onClick = onDismiss) { Text("Закрыть") }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                when {
                    !state.loggedIn -> LoginForm(defaultEmail, state.loading, state.error) { e, p -> viewModel.login(e, p) }
                    state.selected != null -> OpForm(state.selected!!, state.result, state.error, state.loading,
                        onBack = { viewModel.select(null) }, onRun = { viewModel.run(state.selected!!, it) })
                    state.group != null -> OpList(
                        title = state.group!!,
                        ops = state.ops.filter { it.group == state.group },
                        onBack = { viewModel.openGroup(null) },
                        onSelect = { viewModel.select(it) }
                    )
                    else -> GroupList(state.ops.map { it.group }.distinct().sorted()) { viewModel.openGroup(it) }
                }
            }
        }
    }
}

@Composable
private fun LoginForm(defaultEmail: String, loading: Boolean, error: String?, onLogin: (String, String) -> Unit) {
    var email by remember { mutableStateOf(defaultEmail) }
    var password by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Войдите учётной записью администратора", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            password, { password = it }, label = { Text("Пароль") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onLogin(email.trim(), password) }, enabled = !loading && email.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()) { Text("Войти") }
    }
}

@Composable
private fun GroupList(groups: List<String>, onOpen: (String) -> Unit) {
    if (groups.isEmpty()) {
        Text("Сервер не вернул доступных операций", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { g ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(g) }) {
                Text(g, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun OpList(title: String, ops: List<AdminOp>, onBack: () -> Unit, onSelect: (AdminOp) -> Unit) {
    TextButton(onClick = onBack) { Text("← $title") }
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ops.forEach { op ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(op) }) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(op.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        op.method + if (op.danger) " · осторожно" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (op.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    op.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                }
            }
        }
    }
}

@Composable
private fun OpForm(
    op: AdminOp, result: String?, error: String?, loading: Boolean,
    onBack: () -> Unit, onRun: (Map<String, String>) -> Unit
) {
    val values = remember(op.id) { mutableStateMapOf<String, String>() }
    var confirm by remember(op.id) { mutableStateOf(false) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text(op.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        op.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        op.fields.forEach { f ->
            OutlinedTextField(
                value = values[f.name] ?: (f.default ?: ""),
                onValueChange = { values[f.name] = it },
                label = { Text(f.name + if (f.required) " *" else "") },
                supportingText = {
                    val hint = listOfNotNull(f.type, f.enum?.joinToString("|"), f.description).joinToString(" · ")
                    if (hint.isNotBlank()) Text(hint)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Button(
            onClick = { if (op.danger) confirm = true else onRun(values.toMap()) },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Выполнить") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        result?.let {
            SelectionContainer {
                Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Подтвердите действие") },
            text = { Text("«${op.title}» — потенциально необратимая операция. Выполнить?") },
            confirmButton = { TextButton(onClick = { confirm = false; onRun(values.toMap()) }) { Text("Выполнить") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } }
        )
    }
}
