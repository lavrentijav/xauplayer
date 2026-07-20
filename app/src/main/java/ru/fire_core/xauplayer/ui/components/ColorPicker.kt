package ru.fire_core.xauplayer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.*
import android.graphics.Color as AndroidColor

/**
 * Компонент для выбора цвета с упрощенным интерфейсом
 */
@Composable
fun ColorPicker(
    currentColor: String,
    onColorChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val color = remember(currentColor) {
        try {
            Color(AndroidColor.parseColor(currentColor))
        } catch (e: Exception) {
            Color(0xFF1DB954)
        }
    }
    
    var hue by remember { mutableStateOf(colorToHsv(color).h) }
    var saturation by remember { mutableStateOf(colorToHsv(color).s) }
    var brightness by remember { mutableStateOf(colorToHsv(color).v) }

    // Локальный текст HEX-поля, чтобы можно было свободно вводить код цвета вручную.
    // Раньше поле было привязано к вычисляемому значению и «сбрасывало» ввод на каждом
    // символе — код цвета невозможно было ввести. Теперь держим отдельное состояние.
    var hexText by remember { mutableStateOf(colorToHex(color)) }

    // Обновляем значения при изменении цвета извне
    LaunchedEffect(currentColor) {
        val newColor = try {
            Color(AndroidColor.parseColor(currentColor))
        } catch (e: Exception) {
            Color(0xFF1DB954)
        }
        val hsv = colorToHsv(newColor)
        hue = hsv.h
        saturation = hsv.s
        brightness = hsv.v
        hexText = colorToHex(newColor)
    }

    val selectedColor = remember(hue, saturation, brightness) {
        hsvToColor(hue, saturation, brightness)
    }

    // Когда цвет меняется ползунками/пресетами/извне — синхронизируем текст поля.
    LaunchedEffect(selectedColor) {
        val normalized = colorToHex(selectedColor)
        if (!hexText.equals(normalized, ignoreCase = true)) {
            hexText = normalized
        }
    }

    // Пасхалка: для некоторых цветов показываем фразу сверху.
    val colorEgg = ru.fire_core.xauplayer.core.humor.FunPhrases.colorEasterEgg(colorToHex(selectedColor))

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Пасхалка на цвет (появляется только для особых цветов)
        colorEgg?.let { egg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = selectedColor.copy(alpha = 0.18f)
                )
            ) {
                Text(
                    egg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Предпросмотр выбранного цвета
        Card(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = selectedColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize())
        }
        
        // Hex значение — можно вводить код цвета вручную
        val hexValid = hexText.matches(Regex("^#[0-9A-Fa-f]{6}$"))
        OutlinedTextField(
            value = hexText,
            onValueChange = { raw ->
                // Нормализуем ввод: гарантируем ведущий '#', оставляем только hex-символы,
                // приводим к верхнему регистру, ограничиваем длину 7 (#RRGGBB).
                var t = raw.uppercase()
                if (!t.startsWith("#")) t = "#$t"
                t = "#" + t.drop(1).filter { it in '0'..'9' || it in 'A'..'F' }
                if (t.length > 7) t = t.substring(0, 7)
                hexText = t

                // Применяем только когда код полный и корректный.
                if (t.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                    try {
                        val newColor = Color(AndroidColor.parseColor(t))
                        val hsv = colorToHsv(newColor)
                        hue = hsv.h
                        saturation = hsv.s
                        brightness = hsv.v
                        onColorChanged(t)
                    } catch (e: Exception) {
                        // Игнорируем неверный формат
                    }
                }
            },
            label = { Text("Цвет (HEX)") },
            placeholder = { Text("#1DB954") },
            isError = hexText.isNotEmpty() && !hexValid,
            supportingText = {
                if (hexText.isNotEmpty() && !hexValid) {
                    Text("Введите код в формате #RRGGBB")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        // Слайдер оттенка (Hue) с визуализацией
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Оттенок",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    for (x in 0..width.toInt() step 2) {
                        val h = x / width
                        val color = hsvToColor(h, 1f, 1f)
                        drawLine(
                            color = color,
                            start = Offset(x.toFloat(), 0f),
                            end = Offset(x.toFloat(), size.height),
                            strokeWidth = 2f
                        )
                    }
                }
                Slider(
                    value = hue,
                    onValueChange = { newHue ->
                        hue = newHue
                        val newColor = hsvToColor(hue, saturation, brightness)
                        onColorChanged(colorToHex(newColor))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                )
            }
        }
        
        // Слайдер насыщенности
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Насыщенность",
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = saturation,
                onValueChange = { newSaturation ->
                    saturation = newSaturation
                    val newColor = hsvToColor(hue, saturation, brightness)
                    onColorChanged(colorToHex(newColor))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Слайдер яркости
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Яркость",
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = brightness,
                onValueChange = { newBrightness ->
                    brightness = newBrightness
                    val newColor = hsvToColor(hue, saturation, brightness)
                    onColorChanged(colorToHex(newColor))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Предустановленные цвета
        Text(
            text = "Предустановленные цвета",
            style = MaterialTheme.typography.labelMedium
        )
        val presetColors = listOf(
            "#1DB954", // Spotify green
            "#FF6B6B", // Red
            "#4ECDC4", // Teal
            "#45B7D1", // Blue
            "#FFA07A", // Light Salmon
            "#98D8C8", // Mint
            "#F7DC6F", // Yellow
            "#BB8FCE", // Purple
            "#85C1E2", // Sky Blue
            "#F8B739", // Orange
            "#E74C3C", // Dark Red
            "#2ECC71", // Green
            "#3498DB", // Bright Blue
            "#9B59B6", // Purple
            "#E67E22", // Orange
            "#1ABC9C" // Turquoise
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(presetColors) { presetColor ->
                val isSelected = presetColor.equals(currentColor, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {
                            onColorChanged(presetColor)
                        }
                ) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(
                            containerColor = Color(AndroidColor.parseColor(presetColor))
                        ),
                        border = if (isSelected) {
                            BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        }
                    ) {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

data class HSV(val h: Float, val s: Float, val v: Float)

fun colorToHsv(color: Color): HSV {
    val r = color.red
    val g = color.green
    val b = color.blue
    
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    
    val v = max
    val s = if (max != 0f) delta / max else 0f
    
    val h = when {
        delta == 0f -> 0f
        max == r -> ((g - b) / delta + (if (g < b) 6 else 0)) / 6f
        max == g -> ((b - r) / delta + 2) / 6f
        else -> ((r - g) / delta + 4) / 6f
    }
    
    return HSV(h, s, v)
}

fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val c = v * s
    val x = c * (1 - abs((h * 6) % 2 - 1))
    val m = v - c
    
    val (r, g, b) = when {
        h < 1f / 6 -> Triple(c, x, 0f)
        h < 2f / 6 -> Triple(x, c, 0f)
        h < 3f / 6 -> Triple(0f, c, x)
        h < 4f / 6 -> Triple(0f, x, c)
        h < 5f / 6 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    
    return Color(r + m, g + m, b + m)
}

fun colorToHex(color: Color): String {
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    return String.format("#%02X%02X%02X", r, g, b)
}

