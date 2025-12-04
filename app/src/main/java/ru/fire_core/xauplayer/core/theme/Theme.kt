package ru.fire_core.xauplayer.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import android.graphics.Color as AndroidColor
import ru.fire_core.xauplayer.ui.viewmodel.SettingsViewModel

@Composable
fun XAuPlayerTheme(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val accentColor by settingsViewModel.accentColor.collectAsState()
    
    val accent = Color(AndroidColor.parseColor(accentColor))
    
    val colors = darkColorScheme(
        primary = accent,
        onPrimary = Color.Black,
        primaryContainer = accent.copy(alpha = 0.2f),
        background = Color(0xFF0A0A0A),
        surface = Color(0xFF111111),
        surfaceVariant = Color(0xFF1E1E1E),
    )
    
    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}


