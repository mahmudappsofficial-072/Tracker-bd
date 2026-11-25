package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.ui.theme.SuccessGreen

/**
 * Pure progress-driven color resolver for the Monthly Target Goal Tracker.
 * Smoothly interpolates through Red/Orange -> Amber -> Blue -> Success Green
 * as target completion progresses from 0% to 100%, with Light and Dark theme stops
 * tuned for strong contrast on card surfaces and tinted chips.
 */
object TargetProgressColorHelper {
    // Light theme stops (high contrast on white/light surfaces)
    val LightRedStop = Color(0xFFDC2626)
    val LightOrangeStop = Color(0xFFEA580C)
    val LightAmberStop = Color(0xFFD97706)
    val LightBlueStop = Color(0xFF2563EB)
    val LightDeepBlueStop = Color(0xFF0284C7)
    val LightGreenStop = SuccessGreen // 0xFF16A34A

    // Dark theme stops (vivid, legible on dark surfaces)
    val DarkRedStop = Color(0xFFF87171)
    val DarkOrangeStop = Color(0xFFFB923C)
    val DarkAmberStop = Color(0xFFFBBF24)
    val DarkBlueStop = Color(0xFF60A5FA)
    val DarkDeepBlueStop = Color(0xFF38BDF8)
    val DarkGreenStop = Color(0xFF4ADE80)

    fun resolveTargetProgressColor(
        progress: Float,
        isCompleted: Boolean,
        isDarkTheme: Boolean
    ): Color {
        val clamped = progress.coerceIn(0f, 1f)
        val greenStop = if (isDarkTheme) DarkGreenStop else LightGreenStop
        if (isCompleted || clamped >= 1f) {
            return greenStop
        }

        val redStop = if (isDarkTheme) DarkRedStop else LightRedStop
        val orangeStop = if (isDarkTheme) DarkOrangeStop else LightOrangeStop
        val amberStop = if (isDarkTheme) DarkAmberStop else LightAmberStop
        val blueStop = if (isDarkTheme) DarkBlueStop else LightBlueStop
        val deepBlueStop = if (isDarkTheme) DarkDeepBlueStop else LightDeepBlueStop

        return when {
            clamped <= 0.22f -> lerp(redStop, orangeStop, (clamped / 0.22f).coerceIn(0f, 1f))
            clamped <= 0.48f -> lerp(orangeStop, amberStop, ((clamped - 0.22f) / 0.26f).coerceIn(0f, 1f))
            clamped <= 0.75f -> lerp(amberStop, blueStop, ((clamped - 0.48f) / 0.27f).coerceIn(0f, 1f))
            else -> lerp(blueStop, deepBlueStop, ((clamped - 0.75f) / 0.24f).coerceIn(0f, 1f))
        }
    }
}
