package ru.fire_core.xauplayer.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
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
    
    // Палитра и скругления — из веб-клиента (static/tokens.css): тёмная тема, акцент — розовый #ff4d6d
    val colors = darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = accent.copy(alpha = 0.22f),
        onPrimaryContainer = Color.White,
        secondary = WebTokens.accentText,
        background = WebTokens.bg,
        onBackground = WebTokens.text,
        surface = WebTokens.surface,
        onSurface = WebTokens.text,
        surfaceVariant = WebTokens.surface2,
        onSurfaceVariant = WebTokens.muted,
        surfaceContainer = WebTokens.surface,
        surfaceContainerHigh = WebTokens.surface2,
        surfaceContainerHighest = WebTokens.surface3,
        outline = WebTokens.border,
        outlineVariant = WebTokens.border,
        error = WebTokens.danger,
    )

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),   // --radius-cover
        medium = RoundedCornerShape(16.dp), // --radius-card
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp)
    )
    
    MaterialTheme(
        colorScheme = colors,
        shapes = shapes,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}



/** Токены из static/tokens.css веб-клиента. */
object WebTokens {
    val bg = Color(0xFF1F1F1F)
    val surface = Color(0xFF2A2A2A)
    val surface2 = Color(0xFF2F2F2F)
    val surface3 = Color(0xFF3A3A3A)
    val text = Color(0xFFFFFFFF)
    val muted = Color(0xFFA6A6A6)
    val border = Color(0xFF3A3A3A)
    val accentText = Color(0xFFFF8FA3)
    val danger = Color(0xFFFF7A8A)
    val success = Color(0xFF5FD39A)
}
