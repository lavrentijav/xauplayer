package ru.fire_core.xauplayer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.view.Window
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collect
import dagger.hilt.android.AndroidEntryPoint
import ru.fire_core.xauplayer.core.theme.XAuPlayerTheme
import ru.fire_core.xauplayer.core.auth.AuthManager
import ru.fire_core.xauplayer.ui.screens.Auth.LoginScreen
import ru.fire_core.xauplayer.ui.screens.Auth.RegisterScreen
import ru.fire_core.xauplayer.ui.screens.MainScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.download.OfflineProgressManager
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var offlineProgressManager: OfflineProgressManager
    
    override fun onPause() {
        super.onPause()
        // При закрытии приложения синхронизируем все обновления на сервер
        lifecycleScope.launch {
            try {
                offlineProgressManager.syncBufferedProgress()
            } catch (e: Exception) {
                // Игнорируем ошибки при синхронизации при закрытии
            }
        }
    }
    
    override fun onStop() {
        super.onStop()
        // При остановке активности также синхронизируем
        lifecycleScope.launch {
            try {
                offlineProgressManager.syncBufferedProgress()
            } catch (e: Exception) {
                // Игнорируем ошибки при синхронизации при остановке
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.d("MainActivity", "=== MainActivity.onCreate() START ===")
        super.onCreate(savedInstanceState)
        android.util.Log.d("MainActivity", "=== MainActivity.onCreate() после super.onCreate() ===")
        
        // Запрашиваем разрешение на уведомления для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                android.util.Log.d("MainActivity", "[MainActivity] Запрос разрешения POST_NOTIFICATIONS...")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
        
        window.requestFeature(Window.FEATURE_NO_TITLE)
        actionBar?.hide()
        enableEdgeToEdge()
        
        // Запускаем workers один раз в onCreate, а не при каждой рекомпозиции
        AppStartup.scheduleWorkers(application)
        
        setContent {
            XAuPlayerTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = androidx.compose.ui.Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                    val navController = rememberNavController()
                    
                    // Подписываемся на событие необходимости логина
                    LaunchedEffect(Unit) {
                        AuthManager.needsLogin.collect { email ->
                            // Переходим на экран логина при событии необходимости логина
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                    
                    NavHost(navController = navController, startDestination = "register") {
                        composable("register") { 
                            RegisterScreen(
                                onSuccess = { navController.navigate("main") {
                                    popUpTo("register") { inclusive = true }
                                }}, 
                                onNavigateToLogin = { navController.navigate("login") },
                                onAutoLoginSuccess = { navController.navigate("main") {
                                    popUpTo("register") { inclusive = true }
                                }},
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }
                        composable("login") { 
                            LoginScreen(
                                onSuccess = { navController.navigate("main") {
                                    popUpTo("login") { inclusive = true }
                                }},
                                onNavigateToSettings = { navController.navigate("settings") }
                            ) 
                        }
                        composable("settings") {
                            ru.fire_core.xauplayer.ui.screens.tabs.SettingsTab(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("main") {
                            MainScreen(onLogout = {
                                navController.navigate("login") {
                                    popUpTo("main") { inclusive = true }
                                }
                            })
                        }
                    }
                }
            }
        }
        android.util.Log.d("MainActivity", "=== MainActivity.onCreate() END ===")
    }
}



