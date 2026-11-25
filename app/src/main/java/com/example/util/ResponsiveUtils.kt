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
 * - [STANDARD_COMPACT]: 2x roomy spacing/padding/height baseline applied across standard/large phones.
 * - [EXTRA_COMPACT]: balanced compact density for narrow (<365dp), short-height (<690dp), or large-font (>=1.15f) phones.
 * - [MICRO_COMPACT]: ultra-safe density floor for very small/short phones (<340dp width or <600dp height).
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

    fun adaptiveSp(standard: Float, compact: Float, micro: Float = compact): TextUnit = when (tier) {
        ScreenDensityTier.MICRO_COMPACT -> androidx.compose.ui.unit.TextUnit(micro, androidx.compose.ui.unit.TextUnitType.Sp)
        ScreenDensityTier.EXTRA_COMPACT -> androidx.compose.ui.unit.TextUnit(compact, androidx.compose.ui.unit.TextUnitType.Sp)
        ScreenDensityTier.STANDARD_COMPACT -> androidx.compose.ui.unit.TextUnit(standard, androidx.compose.ui.unit.TextUnitType.Sp)
    }
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
     * Pure spacing resolver applying a 2x roomy layout scale on [STANDARD_COMPACT]
     * while automatically stepping down on [EXTRA_COMPACT] and [MICRO_COMPACT] devices.
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
                screenH = 6.dp,
                screenV = 5.dp,
                sectionGap = 5.dp,
                heroCardPadding = 7.dp,
                heroInnerGap = 5.dp,
                cardPadding = 6.dp,
                cardInnerGap = 5.dp,
                listItemPadding = 5.dp,
                pillCardPadding = 4.dp,
                subCardPadding = 5.5.dp,
                gridGap = 4.dp,
                emptyStateVertical = 9.dp,
                bottomNoFab = 7.dp,
                bottomWithFab = 60.dp,
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
                heroRadius = 11.dp,
                cardRadius = 9.dp,
                subCardRadius = 7.dp,
                badgeRadius = 4.5.dp,
                rowGapSm = 3.dp,
                rowGapMd = 5.dp,
                headerLogoSize = 28.dp,
                avatarSize = 34.dp,
                actionCircleSize = 30.dp,
                actionIconSize = 16.dp,
                listCircleSize = 30.dp,
                iconSm = 12.dp,
                iconMd = 16.dp,
                iconLg = 20.dp,
                progressHeight = 7.dp,
                compactActionPadV = 4.dp,
                compactButtonHeight = 38.dp
            )

            ScreenDensityTier.EXTRA_COMPACT -> AppSpacing(
                tier = tier,
                isCompact = isCompactWidthOrFont,
                isShortScreen = isShort,
                screenH = 9.dp,
                screenV = 7.dp,
                sectionGap = 7.dp,
                heroCardPadding = 10.dp,
                heroInnerGap = 7.dp,
                cardPadding = 9.dp,
                cardInnerGap = 7.dp,
                listItemPadding = 7.dp,
                pillCardPadding = 6.dp,
                subCardPadding = 7.5.dp,
                gridGap = 6.dp,
                emptyStateVertical = 13.dp,
                bottomNoFab = 10.dp,
                bottomWithFab = 72.dp,
                sheetHeaderH = 9.dp,
                sheetHeaderV = 4.5.dp,
                sheetBodyH = 9.dp,
                sheetBodyV = 7.dp,
                sheetBodyGap = 6.dp,
                sheetFooterH = 9.dp,
                sheetFooterV = 6.dp,
                dialogOuterPadding = 9.dp,
                dialogCardPadding = 10.dp,
                dialogInnerGap = 7.dp,
                badgeH = 6.dp,
                badgeV = 2.dp,
                compactButtonContentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                minTouchTarget = 48.dp,
                heroRadius = 15.dp,
                cardRadius = 12.dp,
                subCardRadius = 9.dp,
                badgeRadius = 6.dp,
                rowGapSm = 4.5.dp,
                rowGapMd = 7.dp,
                headerLogoSize = 34.dp,
                avatarSize = 42.dp,
                actionCircleSize = 36.dp,
                actionIconSize = 19.dp,
                listCircleSize = 36.dp,
                iconSm = 14.dp,
                iconMd = 19.dp,
                iconLg = 23.dp,
                progressHeight = 9.dp,
                compactActionPadV = 6.dp,
                compactButtonHeight = 42.dp
            )

            ScreenDensityTier.STANDARD_COMPACT -> AppSpacing(
                tier = tier,
                isCompact = isCompactWidthOrFont,
                isShortScreen = isShort,
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
                bottomWithFab = 84.dp,
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
                compactButtonContentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp),
                minTouchTarget = 48.dp,
                heroRadius = 20.dp,
                cardRadius = 16.dp,
                subCardRadius = 12.dp,
                badgeRadius = 8.dp,
                rowGapSm = 6.dp,
                rowGapMd = 10.dp,
                headerLogoSize = 44.dp,
                avatarSize = 52.dp,
                actionCircleSize = 44.dp,
                actionIconSize = 24.dp,
                listCircleSize = 44.dp,
                iconSm = 16.dp,
                iconMd = 22.dp,
                iconLg = 26.dp,
                progressHeight = 12.dp,
                compactActionPadV = 8.dp,
                compactButtonHeight = 48.dp
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
