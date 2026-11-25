package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.AutoFitSingleLineText
import com.example.util.ResponsiveUtils

@Composable
fun SalarySetupGuideDialog(
    @Suppress("UNUSED_PARAMETER") lang: String = "en",
    onDismiss: () -> Unit,
    onStartSetup: () -> Unit
) {
    val spacing = ResponsiveUtils.rememberAppSpacing()

    BackHandler { onDismiss() }

    // Full-content in-screen overlay rendered inside MainActivity's Scaffold content Box
    // so bottomBar (AdmobBanner + NavigationBar) remains completely visible, bright, and unpaused.
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
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(spacing.heroRadius))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .testTag("missing_salary_guide_dialog"),
            shape = RoundedCornerShape(spacing.heroRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.dialogCardPadding),
                verticalArrangement = Arrangement.spacedBy(spacing.dialogInnerGap)
            ) {
                // Header Row: Wallet Badge + "Setup Needed" Pill + Title + Close (×) Button
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
                                        MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(spacing.iconLg)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(spacing.badgeRadius),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            AutoFitSingleLineText(
                                text = stringResource(R.string.badge_setup_pending),
                                maxFontSize = 10.5.sp,
                                minFontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                            )
                        }
                        Spacer(modifier = Modifier.height(spacing.rowGapSm))
                        AutoFitSingleLineText(
                            text = stringResource(R.string.salary_popup_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            minFontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(spacing.minTouchTarget)
                            .testTag("missing_salary_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Primary & Secondary Action Buttons (min 48dp touch targets)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
                ) {
                    Button(
                        onClick = onStartSetup,
                        contentPadding = spacing.compactButtonContentPadding,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = spacing.minTouchTarget)
                            .testTag("missing_salary_setup_btn"),
                        shape = RoundedCornerShape(spacing.cardRadius)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(spacing.iconMd)
                        )
                        Spacer(modifier = Modifier.width(spacing.rowGapMd))
                        AutoFitSingleLineText(
                            text = stringResource(R.string.start_salary_setup_btn),
                            fontWeight = FontWeight.Bold,
                            maxFontSize = 14.sp,
                            minFontSize = 9.5.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        contentPadding = spacing.compactButtonContentPadding,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = spacing.minTouchTarget)
                            .testTag("missing_salary_later_btn"),
                        shape = RoundedCornerShape(spacing.cardRadius)
                    ) {
                        AutoFitSingleLineText(
                            text = stringResource(R.string.salary_popup_later_btn),
                            fontWeight = FontWeight.SemiBold,
                            maxFontSize = 14.sp,
                            minFontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
