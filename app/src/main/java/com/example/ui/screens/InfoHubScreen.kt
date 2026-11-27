package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.UserProfile
import com.example.ui.viewmodel.MonthlySummaryState
import com.example.util.AppSpacing
import com.example.util.InfoHubWorkspace
import com.example.util.LaborLawCalculator
import com.example.util.ResponsiveUtils

@Composable
fun InfoHubScreen(
    profile: UserProfile,
    summary: MonthlySummaryState,
    effectiveLang: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spacing = ResponsiveUtils.rememberAppSpacing()
    val palette = rememberInfoHubPalette()
    val isBengali = effectiveLang == "bn" || effectiveLang.startsWith("bn")

    var activeWorkspace by rememberSaveable { mutableStateOf(InfoHubWorkspace.HUB) }
    var calcSalaryInput by rememberSaveable {
        val initialBasic = summary.basicSalary.takeIf { it > 0.0 } ?: profile.basicSalary
        mutableStateOf(
            if (initialBasic > 0.0) LaborLawCalculator.formatEditableNumber(initialBasic) else "7882"
        )
    }
    var calcIsGrossMode by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = activeWorkspace != InfoHubWorkspace.HUB) {
        activeWorkspace = InfoHubWorkspace.HUB
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.canvas)
            .testTag("info_hub_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedContent(
            targetState = activeWorkspace,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "InfoHubWorkspaceTransition",
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) { workspace ->
            if (workspace == InfoHubWorkspace.HUB) {
                InfoHubDashboard(
                    profile = profile,
                    summary = summary,
                    isBengali = isBengali,
                    spacing = spacing,
                    palette = palette,
                    onOpenWorkspace = { activeWorkspace = it },
                    onQuickDial = { number -> launchSafeDialer(context, number) }
                )
            } else {
                DedicatedWorkspaceShell(
                    activeWorkspace = workspace,
                    profile = profile,
                    summary = summary,
                    isBengali = isBengali,
                    spacing = spacing,
                    palette = palette,
                    calcSalaryInput = calcSalaryInput,
                    onCalcSalaryInputChange = { calcSalaryInput = it },
                    calcIsGrossMode = calcIsGrossMode,
                    onCalcModeChange = { calcIsGrossMode = it },
                    onSelectWorkspace = { activeWorkspace = it },
                    onBackToHub = { activeWorkspace = InfoHubWorkspace.HUB }
                )
            }
        }
    }
}

@Composable
private fun InfoHubDashboard(
    profile: UserProfile,
    summary: MonthlySummaryState,
    isBengali: Boolean,
    spacing: AppSpacing,
    palette: InfoHubPalette,
    onOpenWorkspace: (InfoHubWorkspace) -> Unit,
    onQuickDial: (String) -> Unit
) {
    val activeBasic = summary.basicSalary.takeIf { it > 0.0 } ?: profile.basicSalary
    val activeOtRate = if (activeBasic > 0.0) {
        LaborLawCalculator.calculateAutoAllowances(activeBasic).otHourlyRate
    } else 0.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("info_hub_dashboard"),
        contentPadding = spacing.screenContentNoFab,
        verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)
    ) {
        item(key = "hero") {
            InfoHubHeroHeader(
                activeOtRate = activeOtRate,
                isBengali = isBengali,
                spacing = spacing,
                palette = palette
            )
        }

        item(key = "emergency_strip") {
            PriorityEmergencyBanner(
                isBengali = isBengali,
                spacing = spacing,
                palette = palette,
                onCall16357 = { onQuickDial("16357") },
                onCall999 = { onQuickDial("999") },
                trailingAction = {
                    TextButton(
                        onClick = { onOpenWorkspace(InfoHubWorkspace.HELPLINES) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("info_hub_view_all_helplines_btn")
                    ) {
                        Text(
                            text = stringResource(R.string.info_hub_view_all_helplines),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.brandEmerald
                        )
                    }
                }
            )
        }

        item(key = "directory_header") {
            Text(
                text = stringResource(R.string.info_hub_directory_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        item(key = "grid_row_1") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)
            ) {
                DirectoryTileCard(
                    title = stringResource(R.string.info_quick_ot_btn),
                    subtitle = stringResource(R.string.info_hub_tile_calc_sub),
                    icon = Icons.Filled.Calculate,
                    palette = palette,
                    spacing = spacing,
                    onClick = { onOpenWorkspace(InfoHubWorkspace.CALCULATOR) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("info_hub_tile_calc")
                )
                DirectoryTileCard(
                    title = stringResource(R.string.info_filter_helplines),
                    subtitle = stringResource(R.string.info_hub_tile_helpline_sub),
                    icon = Icons.Filled.PhoneInTalk,
                    palette = palette,
                    spacing = spacing,
                    onClick = { onOpenWorkspace(InfoHubWorkspace.HELPLINES) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("info_hub_tile_helplines")
                )
            }
        }

        item(key = "grid_row_2") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)
            ) {
                DirectoryTileCard(
                    title = stringResource(R.string.info_quick_grades_btn),
                    subtitle = stringResource(R.string.info_hub_tile_grades_sub),
                    icon = Icons.Filled.Badge,
                    palette = palette,
                    spacing = spacing,
                    onClick = { onOpenWorkspace(InfoHubWorkspace.GRADES) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("info_hub_tile_grades")
                )
                DirectoryTileCard(
                    title = stringResource(R.string.info_filter_labor_law),
                    subtitle = stringResource(R.string.info_hub_tile_law_sub),
                    icon = Icons.Filled.Gavel,
                    palette = palette,
                    spacing = spacing,
                    onClick = { onOpenWorkspace(InfoHubWorkspace.LABOR_LAW) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("info_hub_tile_law")
                )
            }
        }

        item(key = "slip_service_wide_tile") {
            Surface(
                onClick = { onOpenWorkspace(InfoHubWorkspace.SLIP_AND_SERVICE) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp)
                    .testTag("info_hub_tile_slip_service"),
                shape = RoundedCornerShape(spacing.cardRadius),
                color = palette.card,
                border = BorderStroke(1.dp, palette.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.cardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(palette.chipBg, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountBalanceWallet,
                            contentDescription = null,
                            tint = palette.brandEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.info_slip_service_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.info_hub_tile_slip_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = palette.slateAccent
                    )
                }
            }
        }

        item(key = "hub_bottom_spacer") {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InfoHubHeroHeader(
    activeOtRate: Double,
    isBengali: Boolean,
    spacing: AppSpacing,
    palette: InfoHubPalette
) {
    val gradientBrush = Brush.linearGradient(colors = listOf(palette.heroStart, palette.heroEnd))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("info_hub_hero_card"),
        shape = RoundedCornerShape(spacing.heroRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(spacing.heroCardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = stringResource(R.string.tab_info),
                        tint = Color.White,
                        modifier = Modifier.size(spacing.iconLg)
                    )
                    Text(
                        text = stringResource(R.string.info_hub_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.heroText
                    )
                }

                Surface(
                    shape = RoundedCornerShape(spacing.badgeRadius),
                    color = Color.White.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OfflinePin,
                            contentDescription = null,
                            tint = Color(0xFFA7F3D0),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.info_offline_badge),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.info_hub_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = palette.heroSubtext
            )

            // Live Profile Rate Strip
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Speed,
                        contentDescription = null,
                        tint = Color(0xFFA7F3D0),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (activeOtRate > 0.0) {
                            stringResource(
                                R.string.info_hub_live_rate_fmt,
                                LaborLawCalculator.formatCurrency(activeOtRate, isBengali)
                            )
                        } else {
                            stringResource(R.string.info_hub_no_rate_prompt)
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectoryTileCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    palette: InfoHubPalette,
    spacing: AppSpacing,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 112.dp),
        shape = RoundedCornerShape(spacing.cardRadius),
        color = palette.card,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(palette.chipBg, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = palette.brandEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = palette.slateAccent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DedicatedWorkspaceShell(
    activeWorkspace: InfoHubWorkspace,
    profile: UserProfile,
    summary: MonthlySummaryState,
    isBengali: Boolean,
    spacing: AppSpacing,
    palette: InfoHubPalette,
    calcSalaryInput: String,
    onCalcSalaryInputChange: (String) -> Unit,
    calcIsGrossMode: Boolean,
    onCalcModeChange: (Boolean) -> Unit,
    onSelectWorkspace: (InfoHubWorkspace) -> Unit,
    onBackToHub: () -> Unit
) {
    val workspaceTabs = listOf(
        InfoHubWorkspace.CALCULATOR to R.string.info_filter_calculator,
        InfoHubWorkspace.HELPLINES to R.string.info_filter_helplines,
        InfoHubWorkspace.GRADES to R.string.info_filter_grades,
        InfoHubWorkspace.LABOR_LAW to R.string.info_filter_labor_law,
        InfoHubWorkspace.SLIP_AND_SERVICE to R.string.info_filter_slip_service
    )

    val activeTitleRes = when (activeWorkspace) {
        InfoHubWorkspace.CALCULATOR -> R.string.info_calc_section_title
        InfoHubWorkspace.HELPLINES -> R.string.info_helpline_section_title
        InfoHubWorkspace.GRADES -> R.string.info_grades_section_title
        InfoHubWorkspace.LABOR_LAW -> R.string.info_law_section_title
        InfoHubWorkspace.SLIP_AND_SERVICE -> R.string.info_slip_service_title
        InfoHubWorkspace.HUB -> R.string.info_hub_title
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("info_workspace_shell")
    ) {
        // Sticky Workspace Bar & Segmented Switcher
        Surface(
            color = palette.card,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackToHub,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("info_workspace_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.info_back_to_hub),
                            tint = palette.brandEmerald
                        )
                    }
                    Text(
                        text = stringResource(activeTitleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.chipBg,
                        modifier = Modifier
                            .clickable(onClick = onBackToHub)
                            .padding(end = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.info_back_to_hub),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.brandEmerald,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("info_workspace_switcher"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    items(workspaceTabs, key = { it.first.name }) { (ws, labelRes) ->
                        FilterChip(
                            selected = activeWorkspace == ws,
                            onClick = { onSelectWorkspace(ws) },
                            label = {
                                Text(
                                    text = stringResource(labelRes),
                                    fontWeight = if (activeWorkspace == ws) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            modifier = Modifier.testTag("info_ws_tab_${ws.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.brandEmerald,
                                selectedLabelColor = Color.White,
                                containerColor = palette.subCard,
                                labelColor = palette.slateAccent
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = activeWorkspace == ws,
                                borderColor = palette.border,
                                selectedBorderColor = palette.brandEmerald
                            )
                        )
                    }
                }

                HorizontalDivider(color = palette.border)
            }
        }

        // Scrollable Focused Workspace Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = spacing.screenContentNoFab,
            verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)
        ) {
            item(key = activeWorkspace.name) {
                when (activeWorkspace) {
                    InfoHubWorkspace.CALCULATOR -> QuickOtCalculatorSection(
                        profile = profile,
                        summary = summary,
                        isBengali = isBengali,
                        spacing = spacing,
                        palette = palette,
                        salaryInput = calcSalaryInput,
                        onSalaryInputChange = onCalcSalaryInputChange,
                        isGrossMode = calcIsGrossMode,
                        onModeChange = onCalcModeChange
                    )

                    InfoHubWorkspace.HELPLINES -> HelplineDirectorySection(
                        isBengali = isBengali,
                        spacing = spacing,
                        palette = palette
                    )

                    InfoHubWorkspace.GRADES -> RmgGradeStructureSection(
                        isBengali = isBengali,
                        spacing = spacing,
                        palette = palette,
                        onLoadGradeInCalculator = { grade ->
                            onCalcModeChange(false)
                            onCalcSalaryInputChange(LaborLawCalculator.formatEditableNumber(grade.basicSalary))
                            onSelectWorkspace(InfoHubWorkspace.CALCULATOR)
                        }
                    )

                    InfoHubWorkspace.LABOR_LAW -> LaborLawGuideSection(
                        spacing = spacing,
                        palette = palette
                    )

                    InfoHubWorkspace.SLIP_AND_SERVICE -> PaySlipAndServiceSection(
                        profile = profile,
                        summary = summary,
                        isBengali = isBengali,
                        spacing = spacing,
                        palette = palette
                    )

                    InfoHubWorkspace.HUB -> Unit
                }
            }

            item(key = "ws_bottom_spacer") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
