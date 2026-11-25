package com.example.util

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Self-adaptive UI density tier classified from actual device width, height, and system font scale.
 * - [STANDARD_COMPACT]: 2/4 (50%) spacing/padding reduction baseline applied across all standard phones.
 * - [EXTRA_COMPACT]: tighter density for narrow (<365dp), short-height (<690dp), or large-font (>=1.15f) phones.
 * - [MICRO_COMPACT]: ultra-compact density for very small/short phones (<340dp width or <600dp height).
 */
enum class ScreenDensityTier {
    MICRO_COMPACT,
    EXTRA_COMPACT,
    STANDARD_COMPACT
}

@Immutable
data class AppSpacing(
    val tier: ScreenDensityTier,
    val isCompact: Boolean,
    val isShortScreen: Boolean,
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
    val minTouchTarget: Dp,
    val heroRadius: Dp,
    val cardRadius: Dp,
    val subCardRadius: Dp,
    val badgeRadius: Dp,
    val rowGapSm: Dp,
    val rowGapMd: Dp,
    val headerLogoSize: Dp,
    val avatarSize: Dp,
    val actionCircleSize: Dp,
    val actionIconSize: Dp,
    val listCircleSize: Dp,
    val iconSm: Dp,
    val iconMd: Dp,
    val iconLg: Dp,
    val progressHeight: Dp,
    val compactActionPadV: Dp,
    val compactButtonHeight: Dp
) {
    val screenContentNoFab: PaddingValues
        get() = PaddingValues(start = screenH, end = screenH, top = screenV, bottom = bottomNoFab)

    val screenContentWithFab: PaddingValues
        get() = PaddingValues(start = screenH, end = screenH, top = screenV, bottom = bottomWithFab)
}

object ResponsiveUtils {
    /**
     * Width threshold for narrow screens (e.g., 320dp - 355dp phones).
     */
    const val COMPACT_SCREEN_WIDTH_DP = 355
    const val EXTRA_COMPACT_WIDTH_DP = 365
    const val MICRO_COMPACT_WIDTH_DP = 340
    const val EXTRA_COMPACT_HEIGHT_DP = 690
    const val MICRO_COMPACT_HEIGHT_DP = 600
    const val LARGE_FONT_SCALE = 1.15f

    /**
     * Pure classifier for unit testing and composable self-inspection across width, height, and fontScale.
     */
    fun classifyDensityTier(
        widthDp: Int,
        heightDp: Int,
        fontScale: Float
    ): ScreenDensityTier {
        return when {
            widthDp < MICRO_COMPACT_WIDTH_DP ||
                heightDp < MICRO_COMPACT_HEIGHT_DP ||
                (widthDp < 360 && fontScale >= LARGE_FONT_SCALE) -> ScreenDensityTier.MICRO_COMPACT

            widthDp < EXTRA_COMPACT_WIDTH_DP ||
                heightDp < EXTRA_COMPACT_HEIGHT_DP ||
                fontScale >= LARGE_FONT_SCALE -> ScreenDensityTier.EXTRA_COMPACT

            else -> ScreenDensityTier.STANDARD_COMPACT
        }
    }

    /**
     * Pure spacing resolver implementing a 2/4 (50%) reduction from legacy defaults on [STANDARD_COMPACT]
     * and progressively tighter geometry on [EXTRA_COMPACT] / [MICRO_COMPACT].
     */
    fun spacingFor(
        widthDp: Int,
        heightDp: Int,
        fontScale: Float
    ): AppSpacing {
        val tier = classifyDensityTier(widthDp, heightDp, fontScale)
        val isCompactWidthOrFont = widthDp < COMPACT_SCREEN_WIDTH_DP || fontScale >= LARGE_FONT_SCALE
        val isShort = heightDp < EXTRA_COMPACT_HEIGHT_DP

        return when (tier) {
            ScreenDensityTier.MICRO_COMPACT -> AppSpacing(
                tier = tier,
                isCompact = isCompactWidthOrFont,
                isShortScreen = isShort,
                screenH = 4.dp,
                screenV = 3.dp,
                sectionGap = 3.dp,
                heroCardPadding = 5.dp,
                heroInnerGap = 3.dp,
                cardPadding = 4.dp,
                cardInnerGap = 3.dp,
                listItemPadding = 3.dp,
                pillCardPadding = 3.dp,
                subCardPadding = 3.5.dp,
                gridGap = 2.5.dp,
                emptyStateVertical = 6.dp,
                bottomNoFab = 5.dp,
                bottomWithFab = 48.dp,
                sheetHeaderH = 4.dp,
                sheetHeaderV = 2.dp,
                sheetBodyH = 4.dp,
                sheetBodyV = 3.dp,
                sheetBodyGap = 2.5.dp,
                sheetFooterH = 4.dp,
                sheetFooterV = 2.5.dp,
                dialogOuterPadding = 4.dp,
                dialogCardPadding = 5.dp,
                dialogInnerGap = 3.dp,
                badgeH = 3.dp,
                badgeV = 1.dp,
                compactButtonContentPadding = PaddingValues(horizontal = 4.dp, vertical = 1.dp),
                minTouchTarget = 48.dp,
                heroRadius = 8.dp,
                cardRadius = 6.dp,
                subCardRadius = 4.dp,
                badgeRadius = 3.dp,
                rowGapSm = 2.dp,
                rowGapMd = 3.dp,
                headerLogoSize = 22.dp,
                avatarSize = 26.dp,
                actionCircleSize = 18.dp,
                actionIconSize = 11.dp,
                listCircleSize = 24.dp,
                iconSm = 10.dp,
                iconMd = 13.dp,
                iconLg = 16.dp,
                progressHeight = 4.dp,
                compactActionPadV = 2.5.dp,
                compactButtonHeight = 36.dp
            )

            ScreenDensityTier.EXTRA_COMPACT -> AppSpacing(
                tier = tier,
                isCompact = isCompactWidthOrFont,
                isShortScreen = isShort,
                screenH = 5.dp,
                screenV = 4.dp,
                sectionGap = 4.dp,
                heroCardPadding = 6.dp,
                heroInnerGap = 4.dp,
                cardPadding = 5.dp,
                cardInnerGap = 4.dp,
                listItemPadding = 4.dp,
                pillCardPadding = 3.5.dp,
                subCardPadding = 4.dp,
                gridGap = 3.dp,
                emptyStateVertical = 7.dp,
                bottomNoFab = 6.dp,
                bottomWithFab = 52.dp,
                sheetHeaderH = 5.dp,
                sheetHeaderV = 2.5.dp,
                sheetBodyH = 5.dp,
                sheetBodyV = 4.dp,
                sheetBodyGap = 3.dp,
                sheetFooterH = 5.dp,
                sheetFooterV = 3.dp,
                dialogOuterPadding = 5.dp,
                dialogCardPadding = 6.dp,
                dialogInnerGap = 4.dp,
                badgeH = 3.5.dp,
                badgeV = 1.dp,
                compactButtonContentPadding = PaddingValues(horizontal = 5.dp, vertical = 1.dp),
                minTouchTarget = 48.dp,
                heroRadius = 9.dp,
                cardRadius = 7.dp,
                subCardRadius = 5.dp,
                badgeRadius = 3.5.dp,
                rowGapSm = 2.5.dp,
                rowGapMd = 4.dp,
                headerLogoSize = 24.dp,
                avatarSize = 28.dp,
                actionCircleSize = 20.dp,
                actionIconSize = 12.dp,
                listCircleSize = 26.dp,
                iconSm = 11.dp,
                iconMd = 14.dp,
                iconLg = 18.dp,
                progressHeight = 5.dp,
                compactActionPadV = 3.dp,
                compactButtonHeight = 38.dp
            )

            ScreenDensityTier.STANDARD_COMPACT -> AppSpacing(
                tier = tier,
                isCompact = isCompactWidthOrFont,
                isShortScreen = isShort,
                screenH = 6.dp,
                screenV = 5.dp,
                sectionGap = 5.dp,
                heroCardPadding = 7.dp,
                heroInnerGap = 5.dp,
                cardPadding = 6.dp,
                cardInnerGap = 5.dp,
                listItemPadding = 5.dp,
                pillCardPadding = 4.dp,
                subCardPadding = 5.dp,
                gridGap = 4.dp,
                emptyStateVertical = 9.dp,
                bottomNoFab = 7.dp,
                bottomWithFab = 56.dp,
                sheetHeaderH = 6.dp,
                sheetHeaderV = 3.dp,
                sheetBodyH = 6.dp,
                sheetBodyV = 5.dp,
                sheetBodyGap = 4.dp,
                sheetFooterH = 6.dp,
                sheetFooterV = 4.dp,
                dialogOuterPadding = 6.dp,
                dialogCardPadding = 7.dp,
                dialogInnerGap = 5.dp,
                badgeH = 4.dp,
                badgeV = 1.5.dp,
                compactButtonContentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.5.dp),
                minTouchTarget = 48.dp,
                heroRadius = 10.dp,
                cardRadius = 8.dp,
                subCardRadius = 6.dp,
                badgeRadius = 4.dp,
                rowGapSm = 3.dp,
                rowGapMd = 5.dp,
                headerLogoSize = 26.dp,
                avatarSize = 32.dp,
                actionCircleSize = 22.dp,
                actionIconSize = 13.dp,
                listCircleSize = 28.dp,
                iconSm = 12.dp,
                iconMd = 15.dp,
                iconLg = 20.dp,
                progressHeight = 6.dp,
                compactActionPadV = 4.dp,
                compactButtonHeight = 40.dp
            )
        }
    }

    @Composable
    fun isCompactScreen(): Boolean {
        val config = LocalConfiguration.current
        val fontScale = LocalDensity.current.fontScale
        return classifyDensityTier(
            widthDp = config.screenWidthDp,
            heightDp = config.screenHeightDp,
            fontScale = fontScale
        ) != ScreenDensityTier.STANDARD_COMPACT
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
        val config = LocalConfiguration.current
        val fontScale = LocalDensity.current.fontScale
        return spacingFor(
            widthDp = config.screenWidthDp,
            heightDp = config.screenHeightDp,
            fontScale = fontScale
        )
    }
}
