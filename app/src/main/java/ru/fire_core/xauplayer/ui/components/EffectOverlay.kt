package ru.fire_core.xauplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Полноэкранный анимированный эффект для страниц книги/серии.
 *
 * @param kind "matrix" — цифровой дождь, "stars" — космос/звёзды,
 *   всё остальное ("rising") — искры/частицы, взмывающие вверх.
 * @param colorHex цвет эффекта (#RRGGBB).
 */
@Composable
fun EffectOverlay(
    kind: String,
    colorHex: String,
    modifier: Modifier = Modifier
) {
    val color = remember(colorHex) {
        try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) { Color(0xFF00E676) }
    }

    var timeMs by remember { mutableStateOf(0L) }
    LaunchedEffect(kind) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> timeMs = (now - start) / 1_000_000L }
        }
    }

    Canvas(modifier = modifier) {
        when (kind) {
            "matrix" -> drawMatrix(timeMs, color)
            "stars" -> drawStars(timeMs, color)
            else -> drawRising(timeMs, color)
        }
    }
}

private fun DrawScope.drawMatrix(timeMs: Long, color: Color) {
    val cols = 22
    val colW = size.width / cols
    val cellH = colW * 1.5f
    val t = timeMs / 1000f
    for (c in 0 until cols) {
        val seed = (c * 9301 + 49297) % 233280
        val speed = 120f + (seed % 200)
        val phase = (seed % 100) / 100f
        val headY = ((t * speed) + phase * size.height) % (size.height + 240f) - 120f
        val trail = 12
        for (tr in 0 until trail) {
            val y = headY - tr * cellH
            if (y < -cellH || y > size.height) continue
            val alpha = if (tr == 0) 0.9f else (1f - tr / trail.toFloat()) * 0.5f
            drawRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(c * colW + colW * 0.2f, y),
                size = Size(colW * 0.6f, cellH * 0.7f)
            )
        }
    }
}

private fun DrawScope.drawRising(timeMs: Long, color: Color) {
    val n = 70
    val t = timeMs / 1000f
    for (i in 0 until n) {
        val seed = (i * 73856093L) and 0xFFFFFFL
        val baseX = (seed % 1000L) / 1000f * size.width
        val speed = 0.10f + (seed % 40L) / 200f
        val prog = ((t * speed) + (seed % 100L) / 100f) % 1f
        val y = size.height * (1f - prog)
        val sway = kotlin.math.sin((t * 1.4f) + i).toFloat() * 8f
        val alpha = (1f - prog) * 0.85f
        val r = 1.5f + (seed % 3L)
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = r,
            center = Offset(baseX + sway, y)
        )
    }
}

private fun DrawScope.drawStars(timeMs: Long, color: Color) {
    val n = 90
    val t = timeMs / 1000f
    for (i in 0 until n) {
        val seed = (i * 2654435761L) and 0xFFFFFFL
        val x = (seed % 1000L) / 1000f * size.width
        val y = ((seed / 1000L) % 1000L) / 1000f * size.height
        val tw = (kotlin.math.sin((t * 2f) + i).toFloat() + 1f) / 2f
        drawCircle(
            color = Color.White.copy(alpha = 0.25f + tw * 0.6f),
            radius = 1f + tw * 1.5f,
            center = Offset(x, y)
        )
    }
    // Падающая звезда цветом эффекта
    val shoot = (t * 0.3f) % 1f
    val sx = shoot * size.width * 1.3f - size.width * 0.15f
    val sy = shoot * size.height * 0.5f
    drawLine(
        color = color.copy(alpha = 0.8f),
        start = Offset(sx, sy),
        end = Offset(sx - 70f, sy - 28f),
        strokeWidth = 3f
    )
}
