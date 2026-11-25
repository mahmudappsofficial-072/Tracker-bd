package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.AutoFitSingleLineText
import com.example.util.ResponsiveUtils

@Composable
fun SalaryModeGuideDialog(
    isCustomSalarySelected: Boolean,
    onSelectAuto: () -> Unit,
    onSelectCustom: () -> Unit,
    onDismiss: () -> Unit,
    cardModifier: Modifier = Modifier
) {
    val spacing = ResponsiveUtils.rememberAppSpacing()

    // In-screen overlay strictly inside SalaryStructureSheet's Box so bottom AdmobBanner remains active.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(spacing.dialogOuterPadding),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = cardModifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .clip(RoundedCornerShape(spacing.heroRadius))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .testTag("salary_mode_guide_dialog"),
            shape = RoundedCornerShape(spacing.heroRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.dialogCardPadding),
                verticalArrangement = Arrangement.spacedBy(spacing.dialogInnerGap)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
                ) {
                    Box(
                        modifier = Modifier
                            .size(spacing.avatarSize)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Balance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(spacing.iconLg)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        AutoFitSingleLineText(
                            text = stringResource(R.string.salary_mode_guide_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            minFontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.salary_mode_guide_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(spacing.minTouchTarget)
                            .testTag("salary_mode_guide_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Scrollable Interactive Auto vs Custom Cards
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(spacing.cardInnerGap)
                ) {
                    ModeSelectionOptionCard(
                        title = stringResource(R.string.mode_auto_tab),
                        badgeText = stringResource(R.string.salary_mode_auto_badge),
                        forLabel = stringResource(R.string.salary_mode_auto_for_label),
                        forDescription = stringResource(R.string.salary_mode_auto_for_desc),
                        howLabel = stringResource(R.string.salary_mode_how_label),
                        howDescription = stringResource(R.string.salary_mode_auto_why_desc),
                        ctaText = stringResource(R.string.salary_mode_auto_select_btn),
                        icon = Icons.Default.AutoAwesome,
                        isSelected = !isCustomSalarySelected,
                        accentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                        onAccentContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        testTag = "salary_mode_guide_auto_card",
                        onClick = onSelectAuto
                    )

                    ModeSelectionOptionCard(
                        title = stringResource(R.string.mode_custom_tab),
                        badgeText = stringResource(R.string.salary_mode_custom_badge),
                        forLabel = stringResource(R.string.salary_mode_auto_for_label),
                        forDescription = stringResource(R.string.salary_mode_custom_for_desc),
                        howLabel = stringResource(R.string.salary_mode_how_label),
                        howDescription = stringResource(R.string.salary_mode_custom_why_desc),
                        ctaText = stringResource(R.string.salary_mode_custom_select_btn),
                        icon = Icons.Default.Tune,
                        isSelected = isCustomSalarySelected,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.38f),
                        onAccentContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        testTag = "salary_mode_guide_custom_card",
                        onClick = onSelectCustom
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeSelectionOptionCard(
    title: String,
    badgeText: String,
    forLabel: String,
    forDescription: String,
    howLabel: String,
    howDescription: String,
    ctaText: String,
    icon: ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    containerColor: Color,
    onAccentContentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val spacing = ResponsiveUtils.rememberAppSpacing()
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(spacing.cardRadius),
        color = containerColor,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.cardInnerGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(spacing.avatarSize)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(spacing.iconMd)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(spacing.badgeRadius),
                        color = accentColor.copy(alpha = 0.16f)
                    ) {
                        AutoFitSingleLineText(
                            text = badgeText,
                            maxFontSize = 10.sp,
                            minFontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = onAccentContentColor,
                            modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                        )
                    }
                    AutoFitSingleLineText(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        minFontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(spacing.iconLg)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.rowGapSm)) {
                Text(
                    text = "$forLabel $forDescription",
                    fontSize = 11.5.sp,
                    lineHeight = 15.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$howLabel $howDescription",
                    fontSize = 11.5.sp,
                    lineHeight = 15.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = accentColor.copy(alpha = 0.18f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.compactButtonHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AutoFitSingleLineText(
                    text = ctaText,
                    maxFontSize = 13.sp,
                    minFontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = spacing.rowGapSm)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(spacing.iconMd)
                )
            }
        }
    }
}
