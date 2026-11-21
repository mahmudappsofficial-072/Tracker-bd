package com.example.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ResponsiveUtils {
    /**
     * Threshold for compact/small screens (typically 320dp - 355dp width, such as Symphony, Walton, itel, Galaxy A01 Core).
     */
    const val COMPACT_SCREEN_WIDTH_DP = 355

    @Composable
    fun isCompactScreen(): Boolean {
        val screenWidth = LocalConfiguration.current.screenWidthDp
        return screenWidth < COMPACT_SCREEN_WIDTH_DP
    }

    @Composable
    fun adaptiveDp(compact: Dp, default: Dp): Dp {
        return if (isCompactScreen()) compact else default
    }

    @Composable
    fun adaptiveFontSize(compact: TextUnit, default: TextUnit): TextUnit {
        return if (isCompactScreen()) compact else default
    }
}
