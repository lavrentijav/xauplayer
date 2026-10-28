package ru.fire_core.xauplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.fire_core.xauplayer.core.config.AppConfig

/**
 * Полоса прогресса главы: ползунок сверху, времена строго под ним.
 * Раньше времена шли отдельной строкой над Slider и ползунок (минимум 48dp по высоте) налезал на подписи —
 * теперь это один Column без наложений. Пока пользователь тянет ползунок, позиция берётся из жеста,
 * а seek отправляется один раз при отпускании.
 *
 * @param speed скорость воспроизведения: подписи показывают реальное время прослушивания.
 */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.outline
) {
    if (durationMs <= 0) return
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val position = positionMs.coerceIn(0, durationMs)
    val fraction = dragFraction ?: (position.toFloat() / durationMs.toFloat())
    val shownPosition = (fraction * durationMs).toLong()
    val s = speed.coerceAtLeast(0.1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * durationMs).toLong()) }
                dragFraction = null
            },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = inactiveColor
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime((shownPosition / s).toLong()), style = MaterialTheme.typography.bodySmall, color = textColor)
            Text("-" + formatTime(((durationMs - shownPosition) / s).toLong()), style = MaterialTheme.typography.bodySmall, color = textColor)
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = AppConfig.msToSeconds(ms.coerceAtLeast(0))
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%d:%02d", minutes, seconds)
}
