package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.ui.theme.NightBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.MonthlySummaryState
import com.example.util.AppSpacing
import com.example.util.InfoHubSectionFilter
import com.example.util.LaborLawCalculator
import com.example.util.ResponsiveUtils
import kotlinx.coroutines.launch

@Composable
fun InfoHubScreen(
    profile: UserProfile,
    summary: MonthlySummaryState,
    effectiveLang: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spacing = ResponsiveUtils.rememberAppSpacing()
    val isBengali = effectiveLang == "bn" || effectiveLang.startsWith("bn")
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var selectedFilter by rememberSaveable { mutableStateOf(InfoHubSectionFilter.ALL) }
    var calcSalaryInput by rememberSaveable {
        val initialBasic = summary.basicSalary.takeIf { it > 0.0 } ?: profile.basicSalary
        mutableStateOf(
            if (initialBasic > 0.0) LaborLawCalculator.formatEditableNumber(initialBasic) else "7882"
        )
    }
    var calcIsGrossMode by rememberSaveable { mutableStateOf(false) }

    val visibleSections = when (selectedFilter) {
        InfoHubSectionFilter.ALL -> listOf(
            InfoHubSectionFilter.CALCULATOR,
            InfoHubSectionFilter.HELPLINES,
            InfoHubSectionFilter.GRADES,
            InfoHubSectionFilter.LABOR_LAW,
            InfoHubSectionFilter.SLIP_AND_SERVICE
        )
        else -> listOf(selectedFilter)
    }

    fun jumpToSection(target: InfoHubSectionFilter) {
        selectedFilter = InfoHubSectionFilter.ALL
        coroutineScope.launch {
            val sectionIndex = when (target) {
                InfoHubSectionFilter.CALCULATOR -> 3
                InfoHubSectionFilter.HELPLINES -> 4
                InfoHubSectionFilter.GRADES -> 5
                InfoHubSectionFilter.LABOR_LAW -> 6
                InfoHubSectionFilter.SLIP_AND_SERVICE -> 7
                InfoHubSectionFilter.ALL -> 0
            }
            listState.animateScrollToItem(sectionIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("info_hub_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            contentPadding = spacing.screenContentNoFab,
            verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)
        ) {
            // Item 0: Hero Banner
            item(key = "header") {
                InfoHubHeroHeader(spacing = spacing)
            }

            // Item 1: Quick Action Buttons (2x2 clean grid)
            item(key = "quick_actions") {
                QuickActionsSection(
                    spacing = spacing,
                    onQuickOtClick = { jumpToSection(InfoHubSectionFilter.CALCULATOR) },
                    onQuickCall16357Click = { launchSafeDialer(context, "16357") },
                    onQuickGradesClick = { jumpToSection(InfoHubSectionFilter.GRADES) },
                    onQuickSlipServiceClick = { jumpToSection(InfoHubSectionFilter.SLIP_AND_SERVICE) }
                )
            }

            // Item 2: Category Filter Chips (Clean Section Isolation)
            item(key = "filter_chips") {
                val filterChips = listOf(
                    InfoHubSectionFilter.ALL to R.string.info_filter_all,
                    InfoHubSectionFilter.CALCULATOR to R.string.info_filter_calculator,
                    InfoHubSectionFilter.HELPLINES to R.string.info_filter_helplines,
                    InfoHubSectionFilter.GRADES to R.string.info_filter_grades,
                    InfoHubSectionFilter.LABOR_LAW to R.string.info_filter_labor_law,
                    InfoHubSectionFilter.SLIP_AND_SERVICE to R.string.info_filter_slip_service
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("info_filter_chips_row"),
                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(filterChips, key = { it.first.name }) { (filter, labelRes) ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(stringResource(labelRes)) },
                            modifier = Modifier.testTag("info_filter_${filter.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // Separated Content Sections
            visibleSections.forEach { section ->
                when (section) {
                    InfoHubSectionFilter.CALCULATOR -> item(key = "sec_calc") {
                        QuickOtCalculatorSection(
                            profile = profile,
                            summary = summary,
                            isBengali = isBengali,
                            spacing = spacing,
                            salaryInput = calcSalaryInput,
                            onSalaryInputChange = { calcSalaryInput = it },
                            isGrossMode = calcIsGrossMode,
                            onModeChange = { calcIsGrossMode = it }
                        )
                    }

                    InfoHubSectionFilter.HELPLINES -> item(key = "sec_helplines") {
                        HelplineDirectorySection(
                            isBengali = isBengali,
                            spacing = spacing
                        )
                    }

                    InfoHubSectionFilter.GRADES -> item(key = "sec_grades") {
                        RmgGradeStructureSection(
                            isBengali = isBengali,
                            spacing = spacing,
                            onLoadGradeInCalculator = { grade ->
                                calcIsGrossMode = false
                                calcSalaryInput = LaborLawCalculator.formatEditableNumber(grade.basicSalary)
                                jumpToSection(InfoHubSectionFilter.CALCULATOR)
                            }
                        )
                    }

                    InfoHubSectionFilter.LABOR_LAW -> item(key = "sec_law") {
                        LaborLawGuideSection(spacing = spacing)
                    }

                    InfoHubSectionFilter.SLIP_AND_SERVICE -> item(key = "sec_slip_service") {
                        PaySlipAndServiceSection(
                            profile = profile,
                            summary = summary,
                            isBengali = isBengali,
                            spacing = spacing
                        )
                    }

                    InfoHubSectionFilter.ALL -> Unit
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun InfoHubHeroHeader(spacing: AppSpacing) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        )
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("info_hub_hero_card"),
        shape = RoundedCornerShape(spacing.heroRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(spacing.heroCardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(spacing.iconLg)
                    )
                    Text(
                        text = stringResource(R.string.info_hub_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Surface(
                    shape = RoundedCornerShape(spacing.badgeRadius),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OfflinePin,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.info_offline_badge),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.info_hub_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
            )
        }
    }
}

@Composable
private fun QuickActionsSection(
    spacing: AppSpacing,
    onQuickOtClick: () -> Unit,
    onQuickCall16357Click: () -> Unit,
    onQuickGradesClick: () -> Unit,
    onQuickSlipServiceClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("info_quick_actions_section"),
        verticalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
    ) {
        Text(
            text = stringResource(R.string.info_quick_actions_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)
        ) {
            QuickActionButtonCard(
                label = stringResource(R.string.info_quick_ot_btn),
                icon = Icons.Filled.Calculate,
                accentColor = MaterialTheme.colorScheme.primary,
                spacing = spacing,
                onClick = onQuickOtClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("info_quick_btn_ot")
            )
            QuickActionButtonCard(
                label = stringResource(R.string.info_quick_call_16357_btn),
                icon = Icons.Filled.Call,
                accentColor = SuccessGreen,
                spacing = spacing,
                onClick = onQuickCall16357Click,
                modifier = Modifier
                    .weight(1f)
                    .testTag("info_quick_btn_16357")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)
        ) {
            QuickActionButtonCard(
                label = stringResource(R.string.info_quick_grades_btn),
                icon = Icons.Filled.Badge,
                accentColor = NightBlue,
                spacing = spacing,
                onClick = onQuickGradesClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("info_quick_btn_grades")
            )
            QuickActionButtonCard(
                label = stringResource(R.string.info_quick_slip_service_btn),
                icon = Icons.Filled.AccountBalanceWallet,
                accentColor = MaterialTheme.colorScheme.tertiary,
                spacing = spacing,
                onClick = onQuickSlipServiceClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("info_quick_btn_slip_service")
            )
        }
    }
}

@Composable
private fun QuickActionButtonCard(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    spacing: AppSpacing,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(spacing.subCardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(accentColor.copy(alpha = 0.14f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
