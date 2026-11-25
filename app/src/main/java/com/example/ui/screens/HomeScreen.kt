package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.DeductionRecord
import com.example.data.model.OvertimeRecord
import com.example.data.model.UserProfile
import com.example.ui.components.AutoFitSingleLineText
import com.example.ui.theme.NightBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.MonthlySummaryState
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager
import com.example.util.MonthLockPolicy
import com.example.util.ResponsiveUtils
import com.example.util.TargetProgressColorHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    selectedMonth: String,
    summary: MonthlySummaryState,
    profile: UserProfile,
    recentRecords: List<OvertimeRecord>,
    deductions: List<DeductionRecord> = emptyList(),
    effectiveLang: String = LanguageManager.resolveSupportedTag(profile.language),
    selectedSettingTag: String = LanguageManager.SYSTEM_TAG,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenLanguagePicker: () -> Unit,
    onQuickAddOtClick: () -> Unit,
    onAddDeduction: (type: String, date: String, amount: Double, desc: String) -> Unit = { _, _, _, _ -> },
    onDeleteDeduction: (DeductionRecord) -> Unit = {},
    onEditTargetClick: () -> Unit,
    onRecordClick: (OvertimeRecord) -> Unit,
    onViewAllClick: () -> Unit,
    onSetMonthLock: (Boolean) -> Unit = {},
    onModalSheetVisibilityChanged: (Boolean) -> Unit = {}
) {
    val isBnDigits = LanguageManager.isBengaliDigits(effectiveLang)

    var showAdvanceDialog by remember { mutableStateOf(false) }
    var showAponBazarDialog by remember { mutableStateOf(false) }
    var selectedDeductionListType by remember { mutableStateOf<String?>(null) }
    var showLockedInfoDialog by remember { mutableStateOf(false) }
    var showUnlockConfirmDialog by remember { mutableStateOf(false) }

    // Only true window-level AlertDialogs overlap bottomBar AdmobBanner;
    // in-screen deduction sheets resize cleanly above bottomBar.
    val hasBlockingSheet = showLockedInfoDialog || showUnlockConfirmDialog
    LaunchedEffect(hasBlockingSheet) {
        onModalSheetVisibilityChanged(hasBlockingSheet)
    }
    DisposableEffect(Unit) {
        onDispose { onModalSheetVisibilityChanged(false) }
    }
    val todayDateStr = remember(selectedMonth) {
        MonthLockPolicy.getDefaultDateForMonth(selectedMonth)
    }

    val spacing = ResponsiveUtils.rememberAppSpacing()
    val pillCardPadding = spacing.pillCardPadding
    val pillTitleSize = spacing.adaptiveSp(standard = 12.5f, compact = 11f, micro = 10f)
    val pillValueSize = spacing.adaptiveSp(standard = 15.5f, compact = 13.5f, micro = 12.5f)

    val chipLabel = if (selectedSettingTag == LanguageManager.SYSTEM_TAG) {
        "${stringResource(R.string.lang_chip_system_short)} (${effectiveLang.uppercase(Locale.US)})"
    } else {
        LanguageManager.SupportedLanguage.fromTag(effectiveLang)?.nativeLabel ?: "EN"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_screen"),
            contentPadding = spacing.screenContentNoFab,
            verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)
        ) {
            // Top Header: User Greeting & Language Chip
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(spacing.cardRadius),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.cardPadding, vertical = spacing.rowGapMd),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = stringResource(R.string.cd_app_logo),
                            modifier = Modifier
                                .size(spacing.headerLogoSize)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(spacing.rowGapMd))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = spacing.rowGapSm)
                        ) {
                            AutoFitSingleLineText(
                                text = summary.userName.ifBlank { stringResource(R.string.app_display_name) },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxFontSize = spacing.adaptiveSp(17f, 15f, 14f),
                                minFontSize = 10.sp
                            )
                            val subInfo = listOfNotNull(
                                summary.companyName.takeIf { it.isNotBlank() },
                                summary.designation.takeIf { it.isNotBlank() }
                            ).joinToString(" • ")
                            AutoFitSingleLineText(
                                text = subInfo.ifBlank { stringResource(R.string.set_info_from_profile) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxFontSize = spacing.adaptiveSp(12.5f, 11f, 10f),
                                minFontSize = 8.sp
                            )
                        }

                        // Language Picker Chip
                        AssistChip(
                            onClick = onOpenLanguagePicker,
                            label = {
                                AutoFitSingleLineText(
                                    text = chipLabel,
                                    fontWeight = FontWeight.Bold,
                                    maxFontSize = spacing.adaptiveSp(12.5f, 11f, 10f),
                                    minFontSize = 8.5.sp
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(spacing.iconSm))
                            },
                            modifier = Modifier
                                .heightIn(min = spacing.compactButtonHeight)
                                .testTag("language_toggle_chip")
                        )
                    }
                }
            }

            // Month Selector Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.rowGapSm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPreviousMonth,
                        modifier = Modifier
                            .size(spacing.compactButtonHeight)
                            .testTag("prev_month_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.cd_previous_month),
                            modifier = Modifier.size(spacing.iconLg)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = spacing.rowGapSm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(spacing.iconMd)
                        )
                        Spacer(modifier = Modifier.width(spacing.rowGapSm))
                        AutoFitSingleLineText(
                            text = LanguageManager.formatYearMonth(selectedMonth, effectiveLang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxFontSize = spacing.adaptiveSp(17f, 15f, 13.5f),
                            minFontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = onNextMonth,
                        modifier = Modifier
                            .size(spacing.compactButtonHeight)
                            .testTag("next_month_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.cd_next_month),
                            modifier = Modifier.size(spacing.iconLg)
                        )
                    }
                }
            }

            // Lock Status / Grace Period Banner for Past Months
            val currentCalendarYm = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            if (selectedMonth < currentCalendarYm) {
                item {
                    PastMonthLockBanner(
                        selectedMonth = selectedMonth,
                        isLocked = summary.isLocked,
                        onUnlockClick = { showUnlockConfirmDialog = true },
                        onLockClick = { onSetMonthLock(true) },
                        modifier = Modifier.testTag("home_locked_banner")
                    )
                }
            }

            // Hero Card: Total Income & Overtime Highlights
            item {
                Card(
                    shape = RoundedCornerShape(spacing.heroRadius),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                    )
                                )
                            )
                            .padding(spacing.heroCardPadding)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.heroInnerGap)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.estimated_income),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                    maxFontSize = spacing.adaptiveSp(14f, 12f, 11f),
                                    minFontSize = 9.sp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = spacing.rowGapSm)
                                )
                                Surface(
                                    shape = RoundedCornerShape(spacing.badgeRadius),
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                                ) {
                                    AutoFitSingleLineText(
                                        text = if (summary.isCustomSalary) {
                                            stringResource(R.string.rule_custom)
                                        } else {
                                            stringResource(R.string.rule_labor_law_2006)
                                        },
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        maxFontSize = spacing.adaptiveSp(11.5f, 10f, 9f),
                                        minFontSize = 7.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                                    )
                                }
                            }

                            // Big Total Salary
                            AutoFitSingleLineText(
                                text = LaborLawCalculator.formatCurrency(summary.totalEstimatedEarnings, isBnDigits),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxFontSize = spacing.adaptiveSp(29f, 24.5f, 22f),
                                minFontSize = 15.sp,
                                modifier = Modifier.fillMaxWidth()
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f))

                            // 3 Key Stats
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)
                            ) {
                                // OT Hours
                                Column(modifier = Modifier.weight(1f)) {
                                    AutoFitSingleLineText(
                                        text = stringResource(R.string.total_ot_hours),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxFontSize = spacing.adaptiveSp(12.5f, 10.5f, 9.5f),
                                        minFontSize = 7.5.sp,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    )
                                    AutoFitSingleLineText(
                                        text = "${LaborLawCalculator.formatNumber(summary.totalOtHours, isBnDigits)} ${stringResource(R.string.hours_unit)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxFontSize = spacing.adaptiveSp(17f, 14f, 12.5f),
                                        minFontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }

                                // OT Amount
                                Column(modifier = Modifier.weight(1f)) {
                                    AutoFitSingleLineText(
                                        text = stringResource(R.string.total_ot_amount),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxFontSize = spacing.adaptiveSp(12.5f, 10.5f, 9.5f),
                                        minFontSize = 7.5.sp,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    )
                                    AutoFitSingleLineText(
                                        text = LaborLawCalculator.formatCurrency(summary.totalOtAmount, isBnDigits),
                                        style = MaterialTheme.typography.titleMedium,
                                        maxFontSize = spacing.adaptiveSp(17f, 14f, 12.5f),
                                        minFontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }

                                // Hourly Rate
                                Column(modifier = Modifier.weight(1f)) {
                                    AutoFitSingleLineText(
                                        text = stringResource(R.string.hourly_rate),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxFontSize = spacing.adaptiveSp(12.5f, 10.5f, 9.5f),
                                        minFontSize = 7.5.sp,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    )
                                    AutoFitSingleLineText(
                                        text = LaborLawCalculator.formatCurrency(summary.hourlyOtRate, isBnDigits),
                                        style = MaterialTheme.typography.titleMedium,
                                        maxFontSize = spacing.adaptiveSp(17f, 14f, 12.5f),
                                        minFontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Monthly Target Goal Tracker Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(spacing.cardRadius),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(spacing.cardInnerGap)
                    ) {
                        // Header Row: Flag + Title & Edit Button / Locked Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = spacing.rowGapSm)
                            ) {
                                Icon(
                                    Icons.Default.Flag,
                                    contentDescription = null,
                                    tint = WarningOrange,
                                    modifier = Modifier.size(spacing.iconMd)
                                )
                                Spacer(modifier = Modifier.width(spacing.rowGapSm))
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.ot_target),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxFontSize = spacing.adaptiveSp(15.5f, 13.5f, 12.5f),
                                    minFontSize = 10.sp
                                )
                            }

                            if (!summary.isLocked) {
                                TextButton(
                                    onClick = onEditTargetClick,
                                    contentPadding = spacing.compactButtonContentPadding,
                                    modifier = Modifier
                                        .heightIn(min = spacing.compactButtonHeight)
                                        .testTag("edit_target_button")
                                ) {
                                    AutoFitSingleLineText(
                                        text = stringResource(R.string.change_target),
                                        maxFontSize = spacing.adaptiveSp(13f, 11.5f, 10.5f),
                                        minFontSize = 9.sp
                                    )
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(spacing.badgeRadius),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(spacing.iconSm)
                                        )
                                        Spacer(modifier = Modifier.width(spacing.rowGapSm))
                                        AutoFitSingleLineText(
                                            text = stringResource(R.string.locked),
                                            maxFontSize = spacing.adaptiveSp(11.5f, 10f, 9f),
                                            minFontSize = 8.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        if (summary.targetHours <= 0) {
                            // Empty State when no target is set
                            Surface(
                                shape = RoundedCornerShape(spacing.subCardRadius),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(spacing.subCardPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(spacing.iconMd)
                                    )
                                    Text(
                                        text = stringResource(R.string.no_target_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = spacing.adaptiveSp(12.5f, 11f, 10.5f),
                                        lineHeight = spacing.adaptiveSp(17f, 15f, 14f),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            val isTargetCompleted = summary.isTargetCompleted
                            val hasExtraOt = isTargetCompleted && summary.extraOtHoursBeyondTarget > 0.0
                            val targetOtEarnings = summary.targetHours * summary.hourlyOtRate
                            val remainingOtEarnings = maxOf(0.0, summary.remainingTargetHours * summary.hourlyOtRate)
                            val clampedProgress = summary.targetProgress.coerceIn(0f, 1f)
                            val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                            val rawProgressColor = remember(clampedProgress, isTargetCompleted, isDarkSurface) {
                                TargetProgressColorHelper.resolveTargetProgressColor(
                                    progress = clampedProgress,
                                    isCompleted = isTargetCompleted,
                                    isDarkTheme = isDarkSurface
                                )
                            }
                            val animatedProgress by animateFloatAsState(
                                targetValue = clampedProgress,
                                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                                label = "monthlyTargetProgress"
                            )
                            val animatedProgressColor by animateColorAsState(
                                targetValue = rawProgressColor,
                                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                                label = "monthlyTargetColor"
                            )

                            // Top Banner: Projected Target Earnings & Target Hours Chip
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = spacing.rowGapSm)
                                ) {
                                    AutoFitSingleLineText(
                                        text = stringResource(R.string.target_ot_earnings),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxFontSize = spacing.adaptiveSp(12f, 10.5f, 9.5f),
                                        minFontSize = 8.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    AutoFitSingleLineText(
                                        text = if (summary.hourlyOtRate > 0) {
                                            LaborLawCalculator.formatCurrency(targetOtEarnings, isBnDigits)
                                        } else {
                                            stringResource(
                                                R.string.target_hrs_ot_fmt,
                                                LaborLawCalculator.formatNumber(summary.targetHours, isBnDigits, 0)
                                            )
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxFontSize = spacing.adaptiveSp(20f, 18f, 16f),
                                        minFontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(spacing.badgeRadius),
                                    color = animatedProgressColor.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, animatedProgressColor.copy(alpha = 0.30f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
                                    ) {
                                        Icon(
                                            Icons.Default.Timeline,
                                            contentDescription = null,
                                            tint = animatedProgressColor,
                                            modifier = Modifier.size(spacing.iconSm)
                                        )
                                        AutoFitSingleLineText(
                                            text = stringResource(
                                                R.string.target_hours_chip_fmt,
                                                LaborLawCalculator.formatNumber(summary.targetHours, isBnDigits, 0)
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            maxFontSize = spacing.adaptiveSp(12.5f, 11f, 10f),
                                            minFontSize = 8.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = animatedProgressColor
                                        )
                                    }
                                }
                            }

                            // Progress Bar & Percentage/Rate Indicators
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.rowGapSm)) {
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(spacing.progressHeight)
                                        .clip(RoundedCornerShape(spacing.progressHeight / 2)),
                                    color = animatedProgressColor,
                                    trackColor = animatedProgressColor.copy(alpha = 0.16f)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AutoFitSingleLineText(
                                        text = stringResource(
                                            R.string.progress_percent_fmt,
                                            (summary.targetProgress * 100).toInt()
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxFontSize = spacing.adaptiveSp(12f, 10.5f, 9.5f),
                                        minFontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = animatedProgressColor
                                    )
                                    if (summary.hourlyOtRate > 0) {
                                        AutoFitSingleLineText(
                                            text = stringResource(
                                                R.string.rate_per_hour_fmt,
                                                LaborLawCalculator.formatCurrency(summary.hourlyOtRate, isBnDigits)
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxFontSize = spacing.adaptiveSp(12f, 10.5f, 9.5f),
                                            minFontSize = 8.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Dual Split Metric Cards (Achieved / Target Met vs Remaining / Congrats / Extra OT)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)
                            ) {
                                // Left Box
                                if (isTargetCompleted) {
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        shape = RoundedCornerShape(spacing.subCardRadius),
                                        color = SuccessGreen.copy(alpha = 0.10f),
                                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(spacing.subCardPadding),
                                            verticalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(spacing.badgeRadius),
                                                color = SuccessGreen.copy(alpha = 0.16f),
                                                modifier = Modifier.testTag("target_met_badge")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
                                                ) {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = SuccessGreen,
                                                        modifier = Modifier.size(spacing.iconSm)
                                                    )
                                                    AutoFitSingleLineText(
                                                        text = stringResource(R.string.target_met),
                                                        maxFontSize = spacing.adaptiveSp(12f, 10.5f, 9.5f),
                                                        minFontSize = 7.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = SuccessGreen
                                                    )
                                                }
                                            }
                                            AutoFitSingleLineText(
                                                text = stringResource(R.string.target_ot_label),
                                                style = MaterialTheme.typography.labelSmall,
                                                maxFontSize = spacing.adaptiveSp(11.5f, 10f, 9f),
                                                minFontSize = 7.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            AutoFitSingleLineText(
                                                text = if (summary.hourlyOtRate > 0) {
                                                    LaborLawCalculator.formatCurrency(summary.targetAchievedAmount, isBnDigits)
                                                } else {
                                                    "${LaborLawCalculator.formatNumber(summary.targetAchievedHours, isBnDigits, 2)} ${stringResource(R.string.hours_unit)}"
                                                },
                                                style = MaterialTheme.typography.titleMedium,
                                                maxFontSize = spacing.adaptiveSp(18f, 15f, 13.5f),
                                                minFontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SuccessGreen,
                                                modifier = Modifier.testTag("target_achieved_capped_value")
                                            )
                                            AutoFitSingleLineText(
                                                text = stringResource(
                                                    R.string.target_hrs_met_fmt,
                                                    LaborLawCalculator.formatNumber(summary.targetAchievedHours, isBnDigits, 2),
                                                    LaborLawCalculator.formatNumber(summary.targetHours, isBnDigits, 0)
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                maxFontSize = spacing.adaptiveSp(11.5f, 10f, 9f),
                                                minFontSize = 7.5.sp,
                                                color = SuccessGreen,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                } else {
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        shape = RoundedCornerShape(spacing.subCardRadius),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(spacing.subCardPadding),
                                            verticalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(if (spacing.isCompact) 6.dp else 8.dp)
                                                        .clip(CircleShape)
                                                        .background(animatedProgressColor)
                                                )
                                                AutoFitSingleLineText(
                                                    text = stringResource(R.string.achieved_label),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxFontSize = spacing.adaptiveSp(11.5f, 10f, 9f),
                                                    minFontSize = 7.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            AutoFitSingleLineText(
                                                text = if (summary.hourlyOtRate > 0) {
                                                    LaborLawCalculator.formatCurrency(summary.totalOtAmount, isBnDigits)
                                                } else {
                                                    "${LaborLawCalculator.formatNumber(summary.totalOtHours, isBnDigits, 2)} ${stringResource(R.string.hours_unit)}"
                                                },
                                                style = MaterialTheme.typography.titleMedium,
                                                maxFontSize = spacing.adaptiveSp(18f, 15f, 13.5f),
                                                minFontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            AutoFitSingleLineText(
                                                text = stringResource(
                                                    R.string.hrs_earned_fmt,
                                                    LaborLawCalculator.formatNumber(summary.totalOtHours, isBnDigits, 2)
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                maxFontSize = spacing.adaptiveSp(11.5f, 10f, 9f),
                                                minFontSize = 7.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Right Box
                                if (isTargetCompleted) {
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        shape = RoundedCornerShape(spacing.subCardRadius),
                                        color = SuccessGreen.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f))
                                    ) {
                                        if (!hasExtraOt) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(spacing.subCardPadding),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(spacing.rowGapSm, Alignment.CenterVertically),
                                                    modifier = Modifier.testTag("target_congrats_badge")
                                                ) {
                                                    Icon(
                                                        Icons.Default.EmojiEvents,
                                                        contentDescription = null,
                                                        tint = SuccessGreen,
                                                        modifier = Modifier.size(spacing.iconLg)
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(spacing.badgeRadius),
                                                        color = SuccessGreen.copy(alpha = 0.18f)
                                                    ) {
                                                        AutoFitSingleLineText(
                                                            text = stringResource(R.string.congrats),
                                                            maxFontSize = spacing.adaptiveSp(13.5f, 11.5f, 10.5f),
                                                            minFontSize = 8.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = SuccessGreen,
                                                            modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(spacing.subCardPadding),
                                                verticalArrangement = Arrangement.spacedBy(spacing.rowGapSm, Alignment.CenterVertically)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(if (spacing.isCompact) 6.dp else 8.dp)
                                                            .clip(CircleShape)
                                                            .background(SuccessGreen)
                                                    )
                                                    AutoFitSingleLineText(
                                                        text = stringResource(R.string.extra_ot),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxFontSize = spacing.adaptiveSp(11.5f, 10f, 9f),
                                                        minFontSize = 7.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                AutoFitSingleLineText(
                                                    text = if (summary.hourlyOtRate > 0) {
                                                        "+${LaborLawCalculator.formatCurrency(summary.extraOtAmountBeyondTarget, isBnDigits)}"
                                                    } else {
                                                        "+${LaborLawCalculator.formatNumber(summary.extraOtHoursBeyondTarget, isBnDigits, 2)} ${stringResource(R.string.hours_unit)}"
                                                    },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxFontSize = spacing.adaptiveSp(18f, 15f, 13.5f),
                                                    minFontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SuccessGreen,
                                                    modifier = Modifier.testTag("extra_ot_amount_value")
                                                )
                                                AutoFitSingleLineText(
                                                    text = stringResource(
                                                        R.string.hrs_extra_ot_fmt,
                                                        LaborLawCalculator.formatNumber(summary.extraOtHoursBeyondTarget, isBnDigits, 2)
                                                    ),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxFontSize = spacing.adaptiveSp(11.5f, 10f, 9f),
                                                    minFontSize = 7.5.sp,
                                                    color = SuccessGreen,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.testTag("extra_ot_hours_value")
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        shape = RoundedCornerShape(spacing.subCardRadius),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(spacing.subCardPadding),
                                            verticalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(if (spacing.isCompact) 6.dp else 8.dp)
                                                        .clip(CircleShape)
                                                        .background(WarningOrange)
                                                )
                                                AutoFitSingleLineText(
                                                    text = stringResource(R.string.remaining_label),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxFontSize = spacing.adaptiveSp(11.5f, 10f, 9f),
                                                    minFontSize = 7.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            AutoFitSingleLineText(
                                                text = if (summary.hourlyOtRate > 0) {
                                                    LaborLawCalculator.formatCurrency(remainingOtEarnings, isBnDigits)
                                                } else {
                                                    "${LaborLawCalculator.formatNumber(summary.remainingTargetHours, isBnDigits, 2)} ${stringResource(R.string.hours_unit)}"
                                                },
                                                style = MaterialTheme.typography.titleMedium,
                                                maxFontSize = spacing.adaptiveSp(18f, 15f, 13.5f),
                                                minFontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = WarningOrange
                                            )
                                            AutoFitSingleLineText(
                                                text = stringResource(
                                                    R.string.hrs_needed_fmt,
                                                    LaborLawCalculator.formatNumber(summary.remainingTargetHours, isBnDigits, 2)
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                maxFontSize = spacing.adaptiveSp(11.5f, 10f, 9f),
                                                minFontSize = 7.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // Projected Month-End Total Pay at Target
                            val isPastMonthTarget = selectedMonth < currentCalendarYm
                            val projectedTotal = summary.projectedTotalAtTarget
                            val isNegativeProjected = projectedTotal < 0.0
                            val totalDeductions = summary.advanceDeduction + summary.aponBazarDeduction + summary.absentDeduction
                            val isBonusCancelled = summary.isBonusCancelledDueToAbsent || summary.isBonusCancelledDueToLeave

                            val projectedBgColor = when {
                                isNegativeProjected -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                                isTargetCompleted -> SuccessGreen.copy(alpha = 0.12f)
                                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            }
                            val projectedBorderColor = when {
                                isNegativeProjected -> MaterialTheme.colorScheme.error.copy(alpha = 0.40f)
                                isTargetCompleted -> SuccessGreen.copy(alpha = 0.35f)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                            }
                            val projectedAccentColor = when {
                                isNegativeProjected -> MaterialTheme.colorScheme.error
                                isTargetCompleted -> SuccessGreen
                                else -> MaterialTheme.colorScheme.primary
                            }
                            val formattedProjectedTotal = if (isNegativeProjected) {
                                "- ${LaborLawCalculator.formatCurrency(-projectedTotal, isBnDigits)}"
                            } else {
                                LaborLawCalculator.formatCurrency(projectedTotal, isBnDigits)
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("projected_total_at_target_card"),
                                shape = RoundedCornerShape(spacing.subCardRadius),
                                color = projectedBgColor,
                                border = BorderStroke(1.dp, projectedBorderColor)
                            ) {
                                Column(
                                    modifier = Modifier.padding(spacing.subCardPadding),
                                    verticalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = spacing.rowGapSm)
                                        ) {
                                            Icon(
                                                Icons.Default.AccountBalanceWallet,
                                                contentDescription = null,
                                                tint = projectedAccentColor,
                                                modifier = Modifier.size(spacing.iconMd)
                                            )
                                            Spacer(modifier = Modifier.width(spacing.rowGapSm))
                                            AutoFitSingleLineText(
                                                text = if (isPastMonthTarget) {
                                                    stringResource(R.string.target_based_total)
                                                } else {
                                                    stringResource(R.string.est_total_pay_at_target)
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                maxFontSize = spacing.adaptiveSp(13f, 11.5f, 10.5f),
                                                minFontSize = 8.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        AutoFitSingleLineText(
                                            text = formattedProjectedTotal,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxFontSize = spacing.adaptiveSp(18f, 15f, 13.5f),
                                            minFontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = projectedAccentColor,
                                            modifier = Modifier.testTag("projected_total_at_target_value")
                                        )
                                    }

                                    Text(
                                        text = when {
                                            isNegativeProjected -> stringResource(R.string.deductions_exceed_earnings)
                                            summary.grossSalary <= 0.0 -> stringResource(R.string.salary_not_set_note)
                                            hasExtraOt -> stringResource(R.string.formula_with_extra_ot)
                                            else -> stringResource(R.string.formula_standard)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = spacing.adaptiveSp(11.5f, 10f, 9.5f),
                                        lineHeight = spacing.adaptiveSp(15.5f, 13.5f, 12.5f),
                                        color = if (isNegativeProjected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (totalDeductions > 0.0 || isBonusCancelled) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(spacing.gridGap),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (totalDeductions > 0.0) {
                                                AutoFitSingleLineText(
                                                    text = stringResource(
                                                        R.string.deductions_summary_fmt,
                                                        LaborLawCalculator.formatCurrency(totalDeductions, isBnDigits)
                                                    ),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxFontSize = spacing.adaptiveSp(11.5f, 10f, 9.5f),
                                                    minFontSize = 8.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            if (isBonusCancelled) {
                                                Surface(
                                                    shape = RoundedCornerShape(spacing.badgeRadius),
                                                    color = WarningOrange.copy(alpha = 0.14f)
                                                ) {
                                                    AutoFitSingleLineText(
                                                        text = stringResource(R.string.bonus_lost_badge),
                                                        maxFontSize = spacing.adaptiveSp(11f, 9.5f, 9f),
                                                        minFontSize = 7.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = WarningOrange,
                                                        modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3 Action Buttons: Today's OT, Advance, Apon Bazar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)
                ) {
                    // Button 1: Today's OT
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(spacing.cardRadius),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = spacing.minTouchTarget)
                            .clickable {
                                if (summary.isLocked) showLockedInfoDialog = true else onQuickAddOtClick()
                            }
                            .testTag("quick_add_ot_button"),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = spacing.compactActionPadV, horizontal = spacing.gridGap),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(spacing.actionCircleSize)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.AddAlarm,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(spacing.actionIconSize)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(spacing.rowGapSm))
                            AutoFitSingleLineText(
                                text = stringResource(R.string.action_todays_ot),
                                fontWeight = FontWeight.Bold,
                                maxFontSize = spacing.adaptiveSp(13f, 11.5f, 10.5f),
                                minFontSize = 8.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            )
                            AutoFitSingleLineText(
                                text = stringResource(R.string.action_add_ot),
                                maxFontSize = spacing.adaptiveSp(11f, 9.5f, 9f),
                                minFontSize = 7.5.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Button 2: Advance Deduction
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(spacing.cardRadius),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = spacing.minTouchTarget)
                            .clickable {
                                if (summary.isLocked) showLockedInfoDialog = true else showAdvanceDialog = true
                            }
                            .testTag("add_advance_button"),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = spacing.compactActionPadV, horizontal = spacing.gridGap),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(spacing.actionCircleSize)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Payments,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.size(spacing.actionIconSize)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(spacing.rowGapSm))
                            AutoFitSingleLineText(
                                text = stringResource(R.string.action_advance),
                                fontWeight = FontWeight.Bold,
                                maxFontSize = spacing.adaptiveSp(13f, 11.5f, 10.5f),
                                minFontSize = 8.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.fillMaxWidth()
                            )
                            AutoFitSingleLineText(
                                text = stringResource(R.string.action_withdraw),
                                maxFontSize = spacing.adaptiveSp(11f, 9.5f, 9f),
                                minFontSize = 7.5.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Button 3: Apon Bazar
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(spacing.cardRadius),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = spacing.minTouchTarget)
                            .clickable {
                                if (summary.isLocked) showLockedInfoDialog = true else showAponBazarDialog = true
                            }
                            .testTag("add_apon_bazar_button"),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = spacing.compactActionPadV, horizontal = spacing.gridGap),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(spacing.actionCircleSize)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier.size(spacing.actionIconSize)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(spacing.rowGapSm))
                            AutoFitSingleLineText(
                                text = stringResource(R.string.action_apon_bazar),
                                fontWeight = FontWeight.Bold,
                                maxFontSize = spacing.adaptiveSp(13f, 11.5f, 10.5f),
                                minFontSize = 8.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            )
                            AutoFitSingleLineText(
                                text = stringResource(R.string.action_grocery),
                                maxFontSize = spacing.adaptiveSp(11f, 9.5f, 9f),
                                minFontSize = 7.5.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Summary Breakdown Grid (Attendance Bonus, Night Bill, Tiffin Bill, Piece Rate)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.gridGap)) {
                    // Row 1: Attendance Bonus & Night Bill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)
                    ) {
                        // Attendance Bonus Pill
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(spacing.subCardRadius)
                        ) {
                            Column(modifier = Modifier.padding(pillCardPadding)) {
                                val isBonusEarned = summary.attendanceBonus > 0
                                val isBonusLost = summary.isBonusCancelledDueToAbsent || summary.isBonusCancelledDueToLeave
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AutoFitSingleLineText(
                                        text = stringResource(R.string.attendance_bonus),
                                        maxFontSize = pillTitleSize,
                                        minFontSize = 7.5.sp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = spacing.rowGapSm),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    AutoFitSingleLineText(
                                        text = when {
                                            isBonusEarned -> stringResource(R.string.bonus_earned_short)
                                            isBonusLost -> stringResource(R.string.bonus_lost_short)
                                            else -> LaborLawCalculator.formatNumber(0.0, isBnDigits, 0)
                                        },
                                        maxFontSize = spacing.adaptiveSp(11f, 9.5f, 9f),
                                        minFontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBonusEarned) SuccessGreen else if (isBonusLost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(spacing.rowGapSm))
                                AutoFitSingleLineText(
                                    text = LaborLawCalculator.formatCurrency(summary.attendanceBonus, isBnDigits),
                                    fontWeight = FontWeight.Bold,
                                    maxFontSize = pillValueSize,
                                    minFontSize = 9.sp,
                                    color = if (isBonusEarned) SuccessGreen else if (isBonusLost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Night Bill Pill
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(spacing.subCardRadius)
                        ) {
                            Column(modifier = Modifier.padding(pillCardPadding)) {
                                AutoFitSingleLineText(
                                    text = stringResource(
                                        R.string.night_bill_count_fmt,
                                        LaborLawCalculator.formatNumber(summary.nightBillCount.toDouble(), isBnDigits, 0)
                                    ),
                                    maxFontSize = pillTitleSize,
                                    minFontSize = 7.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(spacing.rowGapSm))
                                AutoFitSingleLineText(
                                    text = LaborLawCalculator.formatCurrency(summary.nightBillTotal, isBnDigits),
                                    fontWeight = FontWeight.Bold,
                                    maxFontSize = pillValueSize,
                                    minFontSize = 9.sp,
                                    color = NightBlue
                                )
                            }
                        }
                    }

                    // Row 2: Tiffin Bill & Piece Rate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)
                    ) {
                        // Tiffin Bill Pill
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(spacing.subCardRadius)
                        ) {
                            Column(modifier = Modifier.padding(pillCardPadding)) {
                                AutoFitSingleLineText(
                                    text = stringResource(
                                        R.string.tiffin_bill_count_fmt,
                                        LaborLawCalculator.formatNumber(summary.tiffinBillCount.toDouble(), isBnDigits, 0)
                                    ),
                                    maxFontSize = pillTitleSize,
                                    minFontSize = 7.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(spacing.rowGapSm))
                                AutoFitSingleLineText(
                                    text = LaborLawCalculator.formatCurrency(summary.tiffinBillTotal, isBnDigits),
                                    fontWeight = FontWeight.Bold,
                                    maxFontSize = pillValueSize,
                                    minFontSize = 9.sp,
                                    color = WarningOrange
                                )
                            }
                        }

                        // Piece Rate Pill
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(spacing.subCardRadius)
                        ) {
                            Column(modifier = Modifier.padding(pillCardPadding)) {
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.piece_rate_earnings),
                                    maxFontSize = pillTitleSize,
                                    minFontSize = 7.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(spacing.rowGapSm))
                                AutoFitSingleLineText(
                                    text = LaborLawCalculator.formatCurrency(summary.totalPieceAmount, isBnDigits),
                                    fontWeight = FontWeight.Bold,
                                    maxFontSize = pillValueSize,
                                    minFontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Row 3: Advance Deduction & Apon Bazar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)
                    ) {
                        // Advance Deduction Pill
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = spacing.minTouchTarget)
                                .clickable { selectedDeductionListType = "ADVANCE" },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(spacing.subCardRadius)
                        ) {
                            Column(modifier = Modifier.padding(pillCardPadding)) {
                                val advanceCount = deductions.count { it.type == "ADVANCE" }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AutoFitSingleLineText(
                                        text = stringResource(R.string.advance_deduction),
                                        maxFontSize = pillTitleSize,
                                        minFontSize = 7.5.sp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = spacing.rowGapSm),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (advanceCount > 0) {
                                        AutoFitSingleLineText(
                                            text = stringResource(
                                                R.string.count_times_fmt,
                                                LaborLawCalculator.formatNumber(advanceCount.toDouble(), isBnDigits, 0)
                                            ),
                                            maxFontSize = spacing.adaptiveSp(11f, 9.5f, 9f),
                                            minFontSize = 7.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(spacing.rowGapSm))
                                AutoFitSingleLineText(
                                    text = if (summary.advanceDeduction > 0) {
                                        "- " + LaborLawCalculator.formatCurrency(summary.advanceDeduction, isBnDigits)
                                    } else {
                                        LaborLawCalculator.formatCurrency(0.0, isBnDigits)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    maxFontSize = pillValueSize,
                                    minFontSize = 9.sp,
                                    color = if (summary.advanceDeduction > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Apon Bazar Pill
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = spacing.minTouchTarget)
                                .clickable { selectedDeductionListType = "APON_BAZAR" },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(spacing.subCardRadius)
                        ) {
                            Column(modifier = Modifier.padding(pillCardPadding)) {
                                val aponCount = deductions.count { it.type == "APON_BAZAR" }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AutoFitSingleLineText(
                                        text = stringResource(R.string.apon_bazar),
                                        maxFontSize = pillTitleSize,
                                        minFontSize = 7.5.sp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = spacing.rowGapSm),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (aponCount > 0) {
                                        AutoFitSingleLineText(
                                            text = stringResource(
                                                R.string.count_times_fmt,
                                                LaborLawCalculator.formatNumber(aponCount.toDouble(), isBnDigits, 0)
                                            ),
                                            maxFontSize = spacing.adaptiveSp(11f, 9.5f, 9f),
                                            minFontSize = 7.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(spacing.rowGapSm))
                                AutoFitSingleLineText(
                                    text = if (summary.aponBazarDeduction > 0) {
                                        "- " + LaborLawCalculator.formatCurrency(summary.aponBazarDeduction, isBnDigits)
                                    } else {
                                        LaborLawCalculator.formatCurrency(0.0, isBnDigits)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    maxFontSize = pillValueSize,
                                    minFontSize = 9.sp,
                                    color = if (summary.aponBazarDeduction > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Recent Overtime Records Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AutoFitSingleLineText(
                        text = stringResource(R.string.recent_ot_entries),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxFontSize = spacing.adaptiveSp(16f, 14f, 13f),
                        minFontSize = 10.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = spacing.rowGapSm)
                    )
                    TextButton(
                        onClick = onViewAllClick,
                        contentPadding = spacing.compactButtonContentPadding,
                        modifier = Modifier.heightIn(min = spacing.compactButtonHeight)
                    ) {
                        AutoFitSingleLineText(
                            text = stringResource(R.string.view_all),
                            maxFontSize = spacing.adaptiveSp(13.5f, 12f, 11f),
                            minFontSize = 9.sp
                        )
                    }
                }
            }

            // Recent Records or Empty State
            if (recentRecords.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(spacing.subCardRadius),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = spacing.emptyStateVertical, horizontal = spacing.cardPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.AutoMirrored.Filled.EventNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(spacing.iconLg),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(spacing.rowGapSm))
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.no_ot_this_month),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxFontSize = spacing.adaptiveSp(13.5f, 12f, 11f),
                                    minFontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(recentRecords.take(4), key = { it.id }) { record ->
                    Card(
                        shape = RoundedCornerShape(spacing.subCardRadius),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (summary.isLocked) showLockedInfoDialog = true else onRecordClick(record)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = spacing.minTouchTarget)
                                .padding(spacing.listItemPadding),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = spacing.rowGapSm)
                            ) {
                                val isAbsent = record.shiftType == "ABSENT"
                                val isLeave = record.shiftType == "LEAVE"
                                val isWeeklyOff = record.shiftType == "WEEKLY_OFF"

                                val circleBg = when {
                                    isAbsent -> MaterialTheme.colorScheme.errorContainer
                                    isLeave || isWeeklyOff -> MaterialTheme.colorScheme.secondaryContainer
                                    record.isHoliday -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                }
                                val circleTextColor = when {
                                    isAbsent -> MaterialTheme.colorScheme.onErrorContainer
                                    isLeave || isWeeklyOff -> MaterialTheme.colorScheme.onSecondaryContainer
                                    record.isHoliday -> MaterialTheme.colorScheme.onTertiaryContainer
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                                val circleText = when {
                                    isAbsent -> stringResource(R.string.short_abs)
                                    isLeave -> stringResource(R.string.short_leave)
                                    isWeeklyOff -> stringResource(R.string.short_off)
                                    else -> "${LaborLawCalculator.formatNumber(record.otHours, isBnDigits)}${stringResource(R.string.hours_short)}"
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = circleBg,
                                    modifier = Modifier.size(spacing.listCircleSize)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    ) {
                                        AutoFitSingleLineText(
                                            text = circleText,
                                            fontWeight = FontWeight.Bold,
                                            maxFontSize = if (isAbsent || isLeave || isWeeklyOff) spacing.adaptiveSp(11f, 9.5f, 8.5f) else spacing.adaptiveSp(12.5f, 11f, 10f),
                                            minFontSize = 7.sp,
                                            textAlign = TextAlign.Center,
                                            color = circleTextColor
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(spacing.rowGapMd))
                                Column(modifier = Modifier.weight(1f)) {
                                    AutoFitSingleLineText(
                                        text = record.date,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxFontSize = spacing.adaptiveSp(14f, 12.5f, 11.5f),
                                        minFontSize = 9.5.sp
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm)) {
                                        val shiftType = ShiftType.fromCode(record.shiftType)
                                        AutoFitSingleLineText(
                                            text = shiftType.getLabel(),
                                            maxFontSize = spacing.adaptiveSp(11f, 9.5f, 8.5f),
                                            minFontSize = 7.5.sp,
                                            color = shiftType.color,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (record.hasNightBill) {
                                            AutoFitSingleLineText(
                                                text = stringResource(R.string.badge_night_dot),
                                                maxFontSize = spacing.adaptiveSp(11f, 9.5f, 8.5f),
                                                minFontSize = 7.5.sp,
                                                color = NightBlue,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (record.hasTiffinBill) {
                                            AutoFitSingleLineText(
                                                text = stringResource(R.string.badge_tiffin_dot),
                                                maxFontSize = spacing.adaptiveSp(11f, 9.5f, 8.5f),
                                                minFontSize = 7.5.sp,
                                                color = WarningOrange,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    if (record.note.isNotBlank()) {
                                        AutoFitSingleLineText(
                                            text = if (record.shiftType == "LEAVE") {
                                                stringResource(R.string.leave_reason_fmt, record.note)
                                            } else {
                                                record.note
                                            },
                                            maxFontSize = spacing.adaptiveSp(11.5f, 10f, 9f),
                                            minFontSize = 8.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (record.shiftType == "ABSENT") {
                                val deductionText = if (summary.dailyAbsentRate > 0.0) {
                                    "- " + LaborLawCalculator.formatCurrency(summary.dailyAbsentRate, isBnDigits)
                                } else {
                                    stringResource(R.string.short_deduction_prefix)
                                }
                                AutoFitSingleLineText(
                                    text = deductionText,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxFontSize = spacing.adaptiveSp(16f, 14f, 13f),
                                    minFontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                val currentCalendarYm = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
                                val isRunningMonth = record.yearMonth >= currentCalendarYm
                                val itemRate = if (isRunningMonth) summary.hourlyOtRate else (if (record.hourlyRateSnapshot > 0.0) record.hourlyRateSnapshot else summary.hourlyOtRate)
                                AutoFitSingleLineText(
                                    text = LaborLawCalculator.formatCurrency(record.otHours * itemRate, isBnDigits),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxFontSize = spacing.adaptiveSp(16f, 14f, 13f),
                                    minFontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Advance Deduction In-Screen Sheet
        if (showAdvanceDialog) {
            var advDate by remember { mutableStateOf(todayDateStr) }
            var advAmount by remember { mutableStateOf("") }
            var advNote by remember { mutableStateOf("") }
            var advError by remember { mutableStateOf(false) }

            val focusManager = LocalFocusManager.current
            val keyboardController = LocalSoftwareKeyboardController.current
            val hideKeyboard = {
                focusManager.clearFocus()
                keyboardController?.hide()
            }

            DisposableEffect(Unit) {
                onDispose {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            }

            BackHandler {
                hideKeyboard()
                showAdvanceDialog = false
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { hideKeyboard() }
                    )
                    .testTag("advance_deduction_sheet"),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.sheetHeaderH, vertical = spacing.sheetHeaderV),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(spacing.iconLg)
                                )
                                Spacer(modifier = Modifier.width(spacing.rowGapMd))
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.add_advance_deduction_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxFontSize = spacing.adaptiveSp(17f, 15f, 14f),
                                    minFontSize = 11.sp
                                )
                            }
                            IconButton(onClick = {
                                hideKeyboard()
                                showAdvanceDialog = false
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cancel)
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = spacing.sheetBodyH, vertical = spacing.sheetBodyV),
                        verticalArrangement = Arrangement.spacedBy(spacing.sheetBodyGap)
                    ) {
                        ProfessionalDatePickerField(
                            selectedDate = advDate,
                            onDateSelected = { advDate = it },
                            lang = effectiveLang
                        )
                        OutlinedTextField(
                            value = advAmount,
                            onValueChange = {
                                advAmount = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                                advError = false
                            },
                            label = { Text(stringResource(R.string.amount_currency_label)) },
                            placeholder = { Text("0") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                            singleLine = true,
                            isError = advError,
                            supportingText = if (advError) {
                                { Text(stringResource(R.string.enter_valid_amount)) }
                            } else null
                        )
                        OutlinedTextField(
                            value = advNote,
                            onValueChange = { advNote = it },
                            label = { Text(stringResource(R.string.note_optional_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { hideKeyboard() }),
                            singleLine = true
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.sheetFooterH, vertical = spacing.sheetFooterV),
                            horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    hideKeyboard()
                                    showAdvanceDialog = false
                                },
                                shape = RoundedCornerShape(spacing.cardRadius),
                                contentPadding = spacing.compactButtonContentPadding,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = spacing.minTouchTarget)
                            ) {
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.cancel),
                                    maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                                    minFontSize = 9.5.sp
                                )
                            }
                            Button(
                                onClick = {
                                    hideKeyboard()
                                    val amt = LaborLawCalculator.parseLocalizedDouble(advAmount)
                                    if (amt > 0.0) {
                                        onAddDeduction("ADVANCE", advDate.trim().ifBlank { todayDateStr }, amt, advNote.trim())
                                        showAdvanceDialog = false
                                    } else {
                                        advError = true
                                    }
                                },
                                shape = RoundedCornerShape(spacing.cardRadius),
                                contentPadding = spacing.compactButtonContentPadding,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = spacing.minTouchTarget)
                            ) {
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.save),
                                    maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                                    minFontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Apon Bazar In-Screen Sheet
        if (showAponBazarDialog) {
            var aponDate by remember { mutableStateOf(todayDateStr) }
            var aponAmount by remember { mutableStateOf("") }
            var aponDesc by remember { mutableStateOf("") }
            var aponError by remember { mutableStateOf(false) }

            val focusManager = LocalFocusManager.current
            val keyboardController = LocalSoftwareKeyboardController.current
            val hideKeyboard = {
                focusManager.clearFocus()
                keyboardController?.hide()
            }

            DisposableEffect(Unit) {
                onDispose {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            }

            BackHandler {
                hideKeyboard()
                showAponBazarDialog = false
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { hideKeyboard() }
                    )
                    .testTag("apon_bazar_sheet"),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.sheetHeaderH, vertical = spacing.sheetHeaderV),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(spacing.iconLg)
                                )
                                Spacer(modifier = Modifier.width(spacing.rowGapMd))
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.add_apon_bazar_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxFontSize = spacing.adaptiveSp(17f, 15f, 14f),
                                    minFontSize = 11.sp
                                )
                            }
                            IconButton(onClick = {
                                hideKeyboard()
                                showAponBazarDialog = false
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cancel)
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = spacing.sheetBodyH, vertical = spacing.sheetBodyV),
                        verticalArrangement = Arrangement.spacedBy(spacing.sheetBodyGap)
                    ) {
                        ProfessionalDatePickerField(
                            selectedDate = aponDate,
                            onDateSelected = { aponDate = it },
                            lang = effectiveLang
                        )
                        OutlinedTextField(
                            value = aponAmount,
                            onValueChange = {
                                aponAmount = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                                aponError = false
                            },
                            label = { Text(stringResource(R.string.amount_currency_label)) },
                            placeholder = { Text("0") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                            singleLine = true,
                            isError = aponError,
                            supportingText = if (aponError) {
                                { Text(stringResource(R.string.enter_valid_amount)) }
                            } else null
                        )
                        OutlinedTextField(
                            value = aponDesc,
                            onValueChange = { aponDesc = it },
                            label = { Text(stringResource(R.string.apon_bazar_desc_label)) },
                            placeholder = { Text(stringResource(R.string.apon_bazar_desc_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { hideKeyboard() }),
                            maxLines = 2
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.sheetFooterH, vertical = spacing.sheetFooterV),
                            horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    hideKeyboard()
                                    showAponBazarDialog = false
                                },
                                shape = RoundedCornerShape(spacing.cardRadius),
                                contentPadding = spacing.compactButtonContentPadding,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = spacing.minTouchTarget)
                            ) {
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.cancel),
                                    maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                                    minFontSize = 9.5.sp
                                )
                            }
                            Button(
                                onClick = {
                                    hideKeyboard()
                                    val amt = LaborLawCalculator.parseLocalizedDouble(aponAmount)
                                    if (amt > 0.0) {
                                        onAddDeduction("APON_BAZAR", aponDate.trim().ifBlank { todayDateStr }, amt, aponDesc.trim())
                                        showAponBazarDialog = false
                                    } else {
                                        aponError = true
                                    }
                                },
                                shape = RoundedCornerShape(spacing.cardRadius),
                                contentPadding = spacing.compactButtonContentPadding,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = spacing.minTouchTarget)
                            ) {
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.save),
                                    maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                                    minFontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Deduction Details List In-Screen Sheet
        if (selectedDeductionListType != null) {
            val currentType = selectedDeductionListType!!
            val isAdv = currentType == "ADVANCE"
            val filteredList = deductions.filter { it.type == currentType }
            val totalAmt = filteredList.sumOf { it.amount }

            BackHandler { selectedDeductionListType = null }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .testTag("deduction_details_sheet"),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.sheetHeaderH, vertical = spacing.sheetHeaderV),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    if (isAdv) Icons.Default.Payments else Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = if (isAdv) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(spacing.iconLg)
                                )
                                Spacer(modifier = Modifier.width(spacing.rowGapMd))
                                AutoFitSingleLineText(
                                    text = if (isAdv) {
                                        stringResource(R.string.advance_details_title_fmt, selectedMonth)
                                    } else {
                                        stringResource(R.string.apon_bazar_details_title_fmt, selectedMonth)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxFontSize = spacing.adaptiveSp(17f, 15f, 14f),
                                    minFontSize = 11.sp
                                )
                            }
                            IconButton(onClick = { selectedDeductionListType = null }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.close)
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = spacing.sheetBodyH, vertical = spacing.sheetBodyV)
                    ) {
                        if (filteredList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(spacing.cardPadding),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.no_entries_this_month),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(spacing.gridGap),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(filteredList, key = { it.id }) { item ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(spacing.subCardRadius),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(spacing.listItemPadding),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.date,
                                                    fontSize = spacing.adaptiveSp(12f, 10.5f, 10f),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (item.description.isNotBlank()) {
                                                    Text(
                                                        text = item.description,
                                                        fontSize = spacing.adaptiveSp(13.5f, 12f, 11f),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                                AutoFitSingleLineText(
                                                    text = "- " + LaborLawCalculator.formatCurrency(item.amount, isBnDigits),
                                                    fontWeight = FontWeight.Bold,
                                                    maxFontSize = spacing.adaptiveSp(15f, 13f, 12f),
                                                    minFontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            if (!summary.isLocked) {
                                                IconButton(onClick = { onDeleteDeduction(item) }) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = stringResource(R.string.cd_delete),
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(spacing.iconMd)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = spacing.rowGapSm))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.total_deduction_label),
                                    fontWeight = FontWeight.Bold,
                                    maxFontSize = spacing.adaptiveSp(15f, 13f, 12f),
                                    minFontSize = 10.sp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = spacing.rowGapSm)
                                )
                                AutoFitSingleLineText(
                                    text = "- " + LaborLawCalculator.formatCurrency(totalAmt, isBnDigits),
                                    fontWeight = FontWeight.ExtraBold,
                                    maxFontSize = spacing.adaptiveSp(16f, 14f, 13f),
                                    minFontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.sheetFooterH, vertical = spacing.sheetFooterV)
                        ) {
                            Button(
                                onClick = { selectedDeductionListType = null },
                                shape = RoundedCornerShape(spacing.cardRadius),
                                contentPadding = spacing.compactButtonContentPadding,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = spacing.minTouchTarget)
                            ) {
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.close),
                                    maxFontSize = spacing.adaptiveSp(15f, 13.5f, 12.5f),
                                    minFontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Month Locked Info & Unlock Confirmation Dialogs
    MonthLockedInfoAndUnlockDialogs(
        showLockedInfo = showLockedInfoDialog,
        showUnlockConfirm = showUnlockConfirmDialog,
        onDismissLockedInfo = { showLockedInfoDialog = false },
        onRequestUnlock = {
            showLockedInfoDialog = false
            showUnlockConfirmDialog = true
        },
        onDismissUnlockConfirm = { showUnlockConfirmDialog = false },
        onConfirmUnlock = {
            showUnlockConfirmDialog = false
            onSetMonthLock(false)
        }
    )
}
