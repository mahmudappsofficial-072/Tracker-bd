package com.example.util

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

@Immutable
data class AppSpacing(
    val isCompact: Boolean,
    val screenH: Dp,
    val screenV: Dp,
    val sectionGap: Dp,
    val heroCardPadding: Dp,
    val heroInnerGap: Dp,
    val cardPadding: Dp,
    val cardInnerGap: Dp,
    val listItemPadding: Dp,
    val pillCardPadding: Dp,
    val subCardPadding: Dp,
    val gridGap: Dp,
    val emptyStateVertical: Dp,
    val bottomNoFab: Dp,
    val bottomWithFab: Dp,
    val sheetHeaderH: Dp,
    val sheetHeaderV: Dp,
    val sheetBodyH: Dp,
    val sheetBodyV: Dp,
    val sheetBodyGap: Dp,
    val sheetFooterH: Dp,
    val sheetFooterV: Dp,
    val dialogOuterPadding: Dp,
    val dialogCardPadding: Dp,
    val dialogInnerGap: Dp,
    val badgeH: Dp,
    val badgeV: Dp,
    val compactButtonContentPadding: PaddingValues,
    val minTouchTarget: Dp
) {
    val screenContentNoFab: PaddingValues
        get() = PaddingValues(start = screenH, end = screenH, top = screenV, bottom = bottomNoFab)

    val screenContentWithFab: PaddingValues
        get() = PaddingValues(start = screenH, end = screenH, top = screenV, bottom = bottomWithFab)
}

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

    @Composable
    fun rememberAppSpacing(): AppSpacing {
        val compact = isCompactScreen()
        return if (compact) {
            AppSpacing(
                isCompact = true,
                screenH = 10.dp,
                screenV = 10.dp,
                sectionGap = 8.dp,
                heroCardPadding = 12.dp,
                heroInnerGap = 8.dp,
                cardPadding = 10.dp,
                cardInnerGap = 8.dp,
                listItemPadding = 9.dp,
                pillCardPadding = 6.dp,
                subCardPadding = 8.dp,
                gridGap = 6.dp,
                emptyStateVertical = 14.dp,
                bottomNoFab = 12.dp,
                bottomWithFab = 74.dp,
                sheetHeaderH = 12.dp,
                sheetHeaderV = 6.dp,
                sheetBodyH = 12.dp,
                sheetBodyV = 10.dp,
                sheetBodyGap = 8.dp,
                sheetFooterH = 12.dp,
                sheetFooterV = 8.dp,
                dialogOuterPadding = 12.dp,
                dialogCardPadding = 14.dp,
                dialogInnerGap = 10.dp,
                badgeH = 6.dp,
                badgeV = 2.dp,
                compactButtonContentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                minTouchTarget = 48.dp
            )
        } else {
            AppSpacing(
                isCompact = false,
                screenH = 12.dp,
                screenV = 10.dp,
                sectionGap = 10.dp,
                heroCardPadding = 14.dp,
                heroInnerGap = 10.dp,
                cardPadding = 12.dp,
                cardInnerGap = 10.dp,
                listItemPadding = 10.dp,
                pillCardPadding = 8.dp,
                subCardPadding = 10.dp,
                gridGap = 8.dp,
                emptyStateVertical = 18.dp,
                bottomNoFab = 14.dp,
                bottomWithFab = 76.dp,
                sheetHeaderH = 12.dp,
                sheetHeaderV = 6.dp,
                sheetBodyH = 12.dp,
                sheetBodyV = 10.dp,
                sheetBodyGap = 8.dp,
                sheetFooterH = 12.dp,
                sheetFooterV = 8.dp,
                dialogOuterPadding = 12.dp,
                dialogCardPadding = 14.dp,
                dialogInnerGap = 10.dp,
                badgeH = 8.dp,
                badgeV = 3.dp,
                compactButtonContentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp),
                minTouchTarget = 48.dp
            )
        }
    }
}
