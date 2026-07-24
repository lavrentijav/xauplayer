package ru.fire_core.xauplayer.ui.screens.Auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onAutoLoginSuccess: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var autoLoginAttempted by remember { mutableStateOf(false) }
    var showUI by remember { mutableStateOf(false) }
    
    val savedAccounts by viewModel.savedAccounts.collectAsState()

    // Попытка автологина при запуске
    LaunchedEffect(Unit) {
        if (!autoLoginAttempted) {
            autoLoginAttempted = true

            // Быстрый путь: если сессия уже активна (токены сохранены с прошлого
            // запуска) — сразу открываем приложение, не выполняя повторный вход и
            // обновление токена. Иначе на каждом запуске переоткрывалась сессия и
            // перезапускался сервис плеера. Истёкший токен обновится лениво по 401.
            if (viewModel.hasActiveSession()) {
                onAutoLoginSuccess()
                return@LaunchedEffect
            }

            viewModel.loadSavedAccounts()

            // Даем время загрузить аккаунты
            kotlinx.coroutines.delay(ru.fire_core.xauplayer.core.config.AppConfig.ANIMATION_DELAY_MS)

            // Проверяем наличие последнего успешного входа
            if (savedAccounts.isNotEmpty()) {
                val lastAccount = savedAccounts.maxByOrNull { it.lastLogin }
                
                if (lastAccount != null && !lastAccount.accessToken.isNullOrBlank() && !lastAccount.refreshToken.isNullOrBlank()) {
                    // Есть валидные токены - пытаемся войти автоматически
                    // selectSavedAccount проверит refresh token и вернет false, если токены истекли
                    loading = true
                    viewModel.selectSavedAccount(lastAccount.email)
                    
                    // Даем время на вход и проверку refresh token
                    kotlinx.coroutines.delay(ru.fire_core.xauplayer.core.config.AppConfig.MEDIUM_DELAY_MS)
                    
                    when (viewModel.state.value) {
                        is AuthState.Success -> {
                            // Автовход успешен - токены валидны
                            onAutoLoginSuccess()
                            return@LaunchedEffect
                        }
                        else -> {
                            // Автологин не удался (refresh token не работает или токены истекли)
                            // Показываем UI для повторного входа
                            loading = false
                            showUI = true
                        }
                    }
                } else {
                    // Нет валидных токенов, показываем UI регистрации
                    showUI = true
                }
            } else {
                // Нет сохраненных аккаунтов, показываем UI регистрации
                showUI = true
            }
        }
    }

    LaunchedEffect(viewModel.state.value) {
        if (showUI) {
            when (val s = viewModel.state.value) {
                is AuthState.Success -> onSuccess()
                is AuthState.Error -> { error = s.msg; loading = false }
                is AuthState.Loading -> loading = true
                else -> {}
            }
        }
    }
    
    // Показываем индикатор загрузки во время автологина
    if (!showUI) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(Color(0xFF0A3D2E))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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
        
        Text("XAuPlayer", fontSize = 36.sp, color = Color(0xFF1DB954), fontWeight = FontWeight.Black)
        Spacer(Modifier.height(48.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color(0xFF1DB954),
                unfocusedIndicatorColor = Color.Gray,
                focusedLabelColor = Color(0xFF1DB954),
                cursorColor = Color(0xFF1DB954)
            )
        )

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Имя (необязательно)") }
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                loading = true
                viewModel.register(email, password, name.ifBlank { null })
            },
            enabled = !loading && email.isNotBlank() && password.length >= 4,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            else Text("Зарегистрироваться", fontSize = 18.sp)
        }
        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = Color.Red, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = { onNavigateToLogin() }) {
            Text("Уже есть аккаунт? Войти", color = Color(0xFF1DB954))
        }
    }
}


