package ru.fire_core.xauplayer.ui.screens.Auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onSuccess: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    var showNewLogin by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val savedAccounts by viewModel.savedAccounts.collectAsState()
    val lastEmail by ru.fire_core.xauplayer.core.auth.AuthManager.lastEmail.collectAsState()

    // Подставляем email из AuthManager если пришли после ошибки 401
    LaunchedEffect(lastEmail) {
        lastEmail?.let {
            email = it
            showNewLogin = true // Показываем форму логина
            ru.fire_core.xauplayer.core.auth.AuthManager.clearLastEmail()
        }
    }

    LaunchedEffect(viewModel.state.value) {
        when (val s = viewModel.state.value) {
            is AuthState.Success -> {
                loading = false
                onSuccess()
            }
            is AuthState.Error -> { 
                error = s.msg
                loading = false
            }
            is AuthState.Loading -> loading = true
            is AuthState.Idle -> {
                // Если после выбора аккаунта состояние Idle, показываем форму логина
                loading = false
                if (savedAccounts.isNotEmpty() && email.isNotBlank()) {
                    showNewLogin = true
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(Color(0xFF0A3D2E))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Кнопка настроек в правом верхнем углу
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Настройки",
                    tint = Color(0xFF1DB954)
                )
            }
        }
        
        Text(
            "XAuPlayer",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        
        if (savedAccounts.isNotEmpty() && !showNewLogin) {
            // Показываем список сохраненных аккаунтов
            Text(
                "Выберите аккаунт",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(savedAccounts) { account ->
                    SavedAccountItem(
                        account = account,
                        onSelect = {
                            email = account.email
                            viewModel.selectSavedAccount(account.email)
                        },
                        onDelete = {
                            viewModel.deleteSavedAccount(account.email)
                        }
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Button(
                onClick = { showNewLogin = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Войти в другой аккаунт")
            }
        } else {
            // Форма входа
            if (savedAccounts.isNotEmpty()) {
                TextButton(
                    onClick = { showNewLogin = false },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text("← Назад к списку", color = Color.White)
                }
            }
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rememberMe,
                    onCheckedChange = { rememberMe = it }
                )
                Text(
                    "Запомнить меня",
                    color = Color.White,
                    modifier = Modifier.clickable { rememberMe = !rememberMe }
                )
            }
            
            Button(
                onClick = {
                    loading = true
                    val device = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                    viewModel.login(email, password, device, rememberMe)
                },
                enabled = !loading && email.isNotBlank() && password.length >= 4,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                else Text("Войти")
            }
            
            error?.let {
                Text(
                    it,
                    color = Color.Red,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // Оффлайн-режим: вход в служебный аккаунт для прослушивания скачанного (Req 4)
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = Color(0xFF1A4A3A)
        )
        OutlinedButton(
            onClick = { viewModel.enterOfflineMode() },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Слушать оффлайн (без входа)", color = Color.White)
        }
        Text(
            "Служебный аккаунт даёт доступ к уже скачанным книгам без интернета и сервера.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun SavedAccountItem(
    account: ru.fire_core.xauplayer.data.local.entities.SavedAccount,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A4A3A)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    account.email,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                account.name?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = Color.Red
                )
            }
        }
    }
}


