package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.data.model.DeductionRecord
import com.example.data.model.OvertimeRecord
import com.example.data.model.UserProfile
import com.example.ui.theme.NightBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.MonthlySummaryState
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager
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
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToggleLanguage: () -> Unit,
    onQuickAddOtClick: () -> Unit,
    onAddDeduction: (type: String, date: String, amount: Double, desc: String) -> Unit = { _, _, _, _ -> },
    onDeleteDeduction: (DeductionRecord) -> Unit = {},
    onEditTargetClick: () -> Unit,
    onRecordClick: (OvertimeRecord) -> Unit,
    onViewAllClick: () -> Unit,
    onSetMonthLock: (Boolean) -> Unit = {},
    onModalSheetVisibilityChanged: (Boolean) -> Unit = {}
) {
    val lang = profile.language
    val isBn = lang == "bn"

    var showAdvanceDialog by remember { mutableStateOf(false) }
    var showAponBazarDialog by remember { mutableStateOf(false) }
    var selectedDeductionListType by remember { mutableStateOf<String?>(null) }
    var showLockedInfoDialog by remember { mutableStateOf(false) }
    var showUnlockConfirmDialog by remember { mutableStateOf(false) }

    val hasBlockingSheet = showAdvanceDialog || showAponBazarDialog || selectedDeductionListType != null
    LaunchedEffect(hasBlockingSheet) {
        onModalSheetVisibilityChanged(hasBlockingSheet)
    }
    DisposableEffect(Unit) {
        onDispose { onModalSheetVisibilityChanged(false) }
    }
    val todayDateStr = remember(selectedMonth) {
        com.example.util.MonthLockPolicy.getDefaultDateForMonth(selectedMonth)
    }

    val isCompactScreen = com.example.util.ResponsiveUtils.isCompactScreen()
    val screenPadding = if (isCompactScreen) 12.dp else 16.dp
    val cardPadding = if (isCompactScreen) 14.dp else 20.dp
    val pillCardPadding = if (isCompactScreen) 8.dp else 12.dp
    val pillTitleSize = if (isCompactScreen) 10.sp else 11.sp
    val pillValueSize = if (isCompactScreen) 13.sp else 14.sp

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(start = screenPadding, end = screenPadding, top = screenPadding, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(if (isCompactScreen) 12.dp else 16.dp)
    ) {
        // Top Header: User Greeting & Month Bar
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_app_logo),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (profile.userName.isNotBlank()) profile.userName else if (isBn) "ওভারটাইম ট্র্যাকার বিডি" else "Overtime Tracker BD",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subInfo = listOfNotNull(
                            profile.companyName.takeIf { it.isNotBlank() },
                            profile.designation.takeIf { it.isNotBlank() }
                        ).joinToString(" • ")
                        if (subInfo.isNotBlank()) {
                            Text(
                                text = subInfo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = if (isBn) "প্রোফাইল থেকে তথ্য সেট করুন" else "Set info from profile",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Language Chip
                    AssistChip(
                        onClick = onToggleLanguage,
                        label = { Text(if (isBn) "বাংলা" else "EN", fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("language_toggle_chip")
                    )
                }
            }
        }

        // Month Selector Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPreviousMonth,
                    modifier = Modifier.testTag("prev_month_button")
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = LanguageManager.formatYearMonth(selectedMonth, isBn),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onNextMonth,
                    modifier = Modifier.testTag("next_month_button")
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
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
                    isBn = isBn,
                    onUnlockClick = { showUnlockConfirmDialog = true },
                    onLockClick = { onSetMonthLock(true) },
                    modifier = Modifier.testTag("home_locked_banner")
                )
            }
        }

        // Hero Card: Total Income & Overtime Highlights
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                        .padding(cardPadding)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = LanguageManager.get("estimated_income", lang),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = if (summary.isCustomSalary) {
                                        if (isBn) "কাস্টম নিয়ম" else "Custom Rules"
                                    } else {
                                        if (isBn) "শ্রম আইন ২০০৬" else "Labor Law 2006"
                                    },
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Big Total Salary
                        Text(
                            text = LaborLawCalculator.formatCurrency(summary.totalEstimatedEarnings, isBn),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f))

                        // 3 Key Stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // OT Hours
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = LanguageManager.get("total_ot_hours", lang),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = if (isCompactScreen) 10.sp else 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "${LaborLawCalculator.formatNumber(summary.totalOtHours, isBn)} ${if (isBn) "ঘণ্টা" else "hrs"}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = if (isCompactScreen) 13.sp else 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }

                            // OT Amount
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = LanguageManager.get("total_ot_amount", lang),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = if (isCompactScreen) 10.sp else 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = LaborLawCalculator.formatCurrency(summary.totalOtAmount, isBn),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = if (isCompactScreen) 13.sp else 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }

                            // Hourly Rate
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isBn) "প্রতি ঘণ্টা রেট" else "Hourly Rate",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = if (isCompactScreen) 10.sp else 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = LaborLawCalculator.formatCurrency(summary.hourlyOtRate, isBn),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = if (isCompactScreen) 13.sp else 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
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
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header Row: Flag + Title & Edit Button / Locked Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = null,
                                tint = WarningOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = LanguageManager.get("ot_target", lang),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!summary.isLocked) {
                            TextButton(
                                onClick = onEditTargetClick,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("edit_target_button")
                            ) {
                                Text(if (isBn) "টার্গেট পরিবর্তন" else "Change Target", fontSize = 12.sp)
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isBn) "লকড" else "Locked",
                                        fontSize = 11.sp,
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
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (isBn)
                                        "কোনো মাসিক লক্ষ্যমাত্রা নির্ধারণ করা নেই। 'টার্গেট পরিবর্তন' চাপ দিয়ে আপনার ওটি লক্ষ্য ও সম্ভাব্য আয় সেট করুন।"
                                    else
                                        "No monthly target set. Tap 'Change Target' to set your OT target and projected earnings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        val isTargetCompleted = summary.isTargetCompleted
                        val hasExtraOt = isTargetCompleted && summary.extraOtHoursBeyondTarget > 0.0
                        val targetOtEarnings = summary.targetHours * summary.hourlyOtRate
                        val remainingOtEarnings = maxOf(0.0, summary.remainingTargetHours * summary.hourlyOtRate)

                        // Top Banner: Projected Target Earnings & Target Hours Chip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isBn) "টার্গেটের সম্ভাব্য আয়" else "Target OT Earnings",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (summary.hourlyOtRate > 0) {
                                        LaborLawCalculator.formatCurrency(targetOtEarnings, isBn)
                                    } else {
                                        LaborLawCalculator.formatNumber(summary.targetHours, isBn, 0) + (if (isBn) " ঘণ্টা ওটি" else " hrs OT")
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Timeline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${LaborLawCalculator.formatNumber(summary.targetHours, isBn, 0)} ${if (isBn) "ঘণ্টা লক্ষ্য" else "hrs target"}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        // Progress Bar & Percentage/Rate Indicators
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { summary.targetProgress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = if (isTargetCompleted) SuccessGreen else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isBn) {
                                        "অগ্রগতি: ${(summary.targetProgress * 100).toInt()}% পূরণ"
                                    } else {
                                        "Progress: ${(summary.targetProgress * 100).toInt()}% completed"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isTargetCompleted) SuccessGreen else MaterialTheme.colorScheme.primary
                                )
                                if (summary.hourlyOtRate > 0) {
                                    Text(
                                        text = if (isBn) {
                                            "রেট: ${LaborLawCalculator.formatCurrency(summary.hourlyOtRate, isBn)}/ঘণ্টা"
                                        } else {
                                            "Rate: ${LaborLawCalculator.formatCurrency(summary.hourlyOtRate, isBn)}/hr"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Left Box: বর্তমান অর্জন (before goal) OR টার্গেট পূরণ ব্যাজ (capped at target when completed)
                            if (isTargetCompleted) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = SuccessGreen.copy(alpha = 0.10f),
                                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = SuccessGreen.copy(alpha = 0.16f),
                                            modifier = Modifier.testTag("target_met_badge")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = SuccessGreen,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = if (isBn) "টার্গেট পূরণ" else "Target Met",
                                                    fontSize = if (isCompactScreen) 10.sp else 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SuccessGreen,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (isBn) "টার্গেট ওটি" else "Target OT",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (summary.hourlyOtRate > 0) {
                                                LaborLawCalculator.formatCurrency(summary.targetAchievedAmount, isBn)
                                            } else {
                                                "${LaborLawCalculator.formatNumber(summary.targetAchievedHours, isBn, 2)} ${if (isBn) "ঘণ্টা" else "hrs"}"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontSize = if (isCompactScreen) 15.sp else 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SuccessGreen,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.testTag("target_achieved_capped_value")
                                        )
                                        Text(
                                            text = "${LaborLawCalculator.formatNumber(summary.targetAchievedHours, isBn, 2)} / ${LaborLawCalculator.formatNumber(summary.targetHours, isBn, 0)} ${if (isBn) "ঘণ্টা পূর্ণ" else "hrs met"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SuccessGreen,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                            Text(
                                                text = if (isBn) "বর্তমান অর্জন" else "Achieved",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = if (summary.hourlyOtRate > 0) {
                                                LaborLawCalculator.formatCurrency(summary.totalOtAmount, isBn)
                                            } else {
                                                "${LaborLawCalculator.formatNumber(summary.totalOtHours, isBn, 2)} ${if (isBn) "ঘণ্টা" else "hrs"}"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontSize = if (isCompactScreen) 15.sp else 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${LaborLawCalculator.formatNumber(summary.totalOtHours, isBn, 2)} ${if (isBn) "ঘণ্টা অর্জিত" else "hrs earned"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Right Box: টার্গেটে বাকি (Remaining) OR শুধুমাত্র অভিনন্দন (exact target) OR শুধুমাত্র অতিরিক্ত ওটি (beyond target)
                            if (isTargetCompleted) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = SuccessGreen.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f))
                                ) {
                                    if (!hasExtraOt) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                                                modifier = Modifier.testTag("target_congrats_badge")
                                            ) {
                                                Icon(
                                                    Icons.Default.EmojiEvents,
                                                    contentDescription = null,
                                                    tint = SuccessGreen,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = SuccessGreen.copy(alpha = 0.18f)
                                                ) {
                                                    Text(
                                                        text = if (isBn) "অভিনন্দন" else "Congrats",
                                                        fontSize = if (isCompactScreen) 12.sp else 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = SuccessGreen,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(7.dp)
                                                        .clip(CircleShape)
                                                        .background(SuccessGreen)
                                                )
                                                Text(
                                                    text = if (isBn) "অতিরিক্ত ওটি" else "Extra OT",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = if (summary.hourlyOtRate > 0) {
                                                    "+${LaborLawCalculator.formatCurrency(summary.extraOtAmountBeyondTarget, isBn)}"
                                                } else {
                                                    "+${LaborLawCalculator.formatNumber(summary.extraOtHoursBeyondTarget, isBn, 2)} ${if (isBn) "ঘণ্টা" else "hrs"}"
                                                },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontSize = if (isCompactScreen) 15.sp else 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SuccessGreen,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.testTag("extra_ot_amount_value")
                                            )
                                            Text(
                                                text = "+${LaborLawCalculator.formatNumber(summary.extraOtHoursBeyondTarget, isBn, 2)} ${if (isBn) "ঘণ্টা অতিরিক্ত ওটি" else "hrs extra OT"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SuccessGreen,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
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
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(WarningOrange)
                                            )
                                            Text(
                                                text = if (isBn) "টার্গেটে বাকি" else "Remaining",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = if (summary.hourlyOtRate > 0) {
                                                LaborLawCalculator.formatCurrency(remainingOtEarnings, isBn)
                                            } else {
                                                "${LaborLawCalculator.formatNumber(summary.remainingTargetHours, isBn, 2)} ${if (isBn) "ঘণ্টা" else "hrs"}"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontSize = if (isCompactScreen) 15.sp else 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WarningOrange,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${LaborLawCalculator.formatNumber(summary.remainingTargetHours, isBn, 2)} ${if (isBn) "ঘণ্টা প্রয়োজন" else "hrs needed"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Projected Month-End Total Pay at Target (Salary + Target OT + Bonus/Bills/Piece - Deductions)
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
                            "- ${LaborLawCalculator.formatCurrency(-projectedTotal, isBn)}"
                        } else {
                            LaborLawCalculator.formatCurrency(projectedTotal, isBn)
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("projected_total_at_target_card"),
                            shape = RoundedCornerShape(12.dp),
                            color = projectedBgColor,
                            border = BorderStroke(1.dp, projectedBorderColor)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                            .padding(end = 8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.AccountBalanceWallet,
                                            contentDescription = null,
                                            tint = projectedAccentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isPastMonthTarget) {
                                                if (isBn) "টার্গেট অনুযায়ী মোট হিসাব" else "Target-Based Total"
                                            } else {
                                                if (isBn) "টার্গেট পূরণে মাস শেষে মোট আয়" else "Est. Total Pay at Target"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = formattedProjectedTotal,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontSize = if (isCompactScreen) 15.sp else 17.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = projectedAccentColor,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.testTag("projected_total_at_target_value")
                                    )
                                }

                                Text(
                                    text = when {
                                        isNegativeProjected -> if (isBn) {
                                            "কর্তন মোট আয়ের চেয়ে বেশি"
                                        } else {
                                            "Deductions exceed total earnings"
                                        }
                                        summary.grossSalary <= 0.0 -> if (isBn) {
                                            "বেতন সেট না থাকায় শুধু ওটি/বোনাস/কর্তন হিসাব"
                                        } else {
                                            "Salary not set; showing OT/bonus/deductions only"
                                        }
                                        hasExtraOt -> if (isBn) {
                                            "বেতন + টার্গেট ওটি + অতিরিক্ত ওটি + বোনাস/বিল/পিস − কর্তন"
                                        } else {
                                            "Salary + Target OT + Extra OT + Bonus/Bills/Piece − Deductions"
                                        }
                                        else -> if (isBn) {
                                            "বেতন + টার্গেট ওটি + বোনাস/বিল/পিস − কর্তন"
                                        } else {
                                            "Salary + Target OT + Bonus/Bills/Piece − Deductions"
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isNegativeProjected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (totalDeductions > 0.0 || isBonusCancelled) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (totalDeductions > 0.0) {
                                            Text(
                                                text = if (isBn) {
                                                    "কর্তন: -${LaborLawCalculator.formatCurrency(totalDeductions, true)}"
                                                } else {
                                                    "Deductions: -${LaborLawCalculator.formatCurrency(totalDeductions, false)}"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.error,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                        }
                                        if (isBonusCancelled) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = WarningOrange.copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = if (isBn) "হাজিরা বোনাস বাতিল" else "Bonus Lost",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = WarningOrange,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

        // 3 Action Buttons: আজকের ওটি, অগ্রিম কর্তন, আপন বাজার
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button 1: আজকের ওটি
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (summary.isLocked) showLockedInfoDialog = true else onQuickAddOtClick()
                        }
                        .testTag("quick_add_ot_button"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AddAlarm,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isBn) "আজকের ওটি" else "Today's OT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (isBn) "ওটি যোগ" else "Add OT",
                            fontSize = 10.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                }

                // Button 2: অগ্রিম কর্তন
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (summary.isLocked) showLockedInfoDialog = true else showAdvanceDialog = true
                        }
                        .testTag("add_advance_button"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isBn) "অগ্রিম কর্তন" else "Advance",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = if (isBn) "টাকা তুলুন" else "Withdraw",
                            fontSize = 10.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                        )
                    }
                }

                // Button 3: আপন বাজার
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (summary.isLocked) showLockedInfoDialog = true else showAponBazarDialog = true
                        }
                        .testTag("add_apon_bazar_button"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isBn) "আপন বাজার" else "Apon Bazar",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = if (isBn) "বাজার খরচ" else "Grocery",
                            fontSize = 10.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }

        // Summary Breakdown Grid (Attendance Bonus, Night Bill, Tiffin Bill, Piece Rate)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Row 1: Attendance Bonus & Night Bill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Attendance Bonus Pill
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(pillCardPadding)) {
                            val isBonusEarned = summary.attendanceBonus > 0
                            val isBonusLost = summary.isBonusCancelledDueToAbsent || summary.isBonusCancelledDueToLeave
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isBn) "হাজিরা বোনাস" else "Attendance Bonus",
                                    fontSize = pillTitleSize,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (isBonusEarned) {
                                        if (isBn) "অর্জিত" else "Earned"
                                    } else if (isBonusLost) {
                                        if (isBn) "বাতিল" else "Lost"
                                    } else "0",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = if (isBonusEarned) SuccessGreen else if (isBonusLost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = LaborLawCalculator.formatCurrency(summary.attendanceBonus, isBn),
                                fontWeight = FontWeight.Bold,
                                fontSize = pillValueSize,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isBonusEarned) SuccessGreen else if (isBonusLost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Night Bill Pill
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(pillCardPadding)) {
                            Text(
                                text = if (isBn) {
                                    "নাইট বিল (${LaborLawCalculator.formatNumber(summary.nightBillCount.toDouble(), isBn, 0)}টি)"
                                } else {
                                    "Night Bill (${LaborLawCalculator.formatNumber(summary.nightBillCount.toDouble(), isBn, 0)})"
                                },
                                fontSize = pillTitleSize,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = LaborLawCalculator.formatCurrency(summary.nightBillTotal, isBn),
                                fontWeight = FontWeight.Bold,
                                fontSize = pillValueSize,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                color = NightBlue
                            )
                        }
                    }
                }

                // Row 2: Tiffin Bill & Piece Rate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Tiffin Bill Pill
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(pillCardPadding)) {
                            Text(
                                text = if (isBn) {
                                    "টিফিন বিল (${LaborLawCalculator.formatNumber(summary.tiffinBillCount.toDouble(), isBn, 0)}টি)"
                                } else {
                                    "Tiffin Bill (${LaborLawCalculator.formatNumber(summary.tiffinBillCount.toDouble(), isBn, 0)})"
                                },
                                fontSize = pillTitleSize,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = LaborLawCalculator.formatCurrency(summary.tiffinBillTotal, isBn),
                                fontWeight = FontWeight.Bold,
                                fontSize = pillValueSize,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                color = WarningOrange
                            )
                        }
                    }

                    // Piece Rate Pill
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(pillCardPadding)) {
                            Text(
                                text = if (isBn) "পিস রেট আয়" else "Piece Rate",
                                fontSize = pillTitleSize,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = LaborLawCalculator.formatCurrency(summary.totalPieceAmount, isBn),
                                fontWeight = FontWeight.Bold,
                                fontSize = pillValueSize,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Row 3: Advance Deduction & Apon Bazar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Advance Deduction Pill
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedDeductionListType = "ADVANCE" },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(pillCardPadding)) {
                            val advanceCount = deductions.count { it.type == "ADVANCE" }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isBn) "অগ্রিম কর্তন" else "Advance Deduction",
                                    fontSize = pillTitleSize,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (advanceCount > 0) {
                                    Text(
                                        text = if (isBn) "${LaborLawCalculator.formatNumber(advanceCount.toDouble(), true, 0)}টি" else "${LaborLawCalculator.formatNumber(advanceCount.toDouble(), false, 0)}x",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (summary.advanceDeduction > 0) {
                                    "- " + LaborLawCalculator.formatCurrency(summary.advanceDeduction, isBn)
                                } else {
                                    LaborLawCalculator.formatCurrency(0.0, isBn)
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = pillValueSize,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                color = if (summary.advanceDeduction > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Apon Bazar Pill
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedDeductionListType = "APON_BAZAR" },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(pillCardPadding)) {
                            val aponCount = deductions.count { it.type == "APON_BAZAR" }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isBn) "আপন বাজার" else "Apon Bazar",
                                    fontSize = pillTitleSize,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (aponCount > 0) {
                                    Text(
                                        text = if (isBn) "${LaborLawCalculator.formatNumber(aponCount.toDouble(), true, 0)}টি" else "${LaborLawCalculator.formatNumber(aponCount.toDouble(), false, 0)}x",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (summary.aponBazarDeduction > 0) {
                                    "- " + LaborLawCalculator.formatCurrency(summary.aponBazarDeduction, isBn)
                                } else {
                                    LaborLawCalculator.formatCurrency(0.0, isBn)
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = pillValueSize,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
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
                Text(
                    text = if (isBn) "সাম্প্রতিক ওটি এন্ট্রি" else "Recent OT Entries",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onViewAllClick) {
                    Text(if (isBn) "সব দেখুন" else "View All")
                }
            }
        }

        // Recent Records or Empty State
        if (recentRecords.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.AutoMirrored.Filled.EventNote,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isBn) "এই মাসে এখনও কোনো ওটি যোগ করা হয়নি" else "No OT records added for this month yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(recentRecords.take(4), key = { it.id }) { record ->
                Card(
                    shape = RoundedCornerShape(12.dp),
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
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                isAbsent -> if (isBn) "অনুপ" else "Abs"
                                isLeave -> if (isBn) "ছুটি" else "Leave"
                                isWeeklyOff -> if (isBn) "বন্ধ" else "Off"
                                else -> "${LaborLawCalculator.formatNumber(record.otHours, isBn)}${if (isBn) "ঘ" else "h"}"
                            }

                            Surface(
                                shape = CircleShape,
                                color = circleBg,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = circleText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (isAbsent || isLeave || isWeeklyOff) 11.sp else 13.sp,
                                        color = circleTextColor
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = record.date,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val shiftType = ShiftType.fromCode(record.shiftType)
                                    Text(
                                        text = shiftType.getLabel(isBn),
                                        fontSize = 10.sp,
                                        color = shiftType.color,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (record.hasNightBill) {
                                        Text(if (isBn) "• নাইট" else "• Night", fontSize = 10.sp, color = NightBlue, fontWeight = FontWeight.Bold)
                                    }
                                    if (record.hasTiffinBill) {
                                        Text(if (isBn) "• টিফিন" else "• Tiffin", fontSize = 10.sp, color = WarningOrange, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (record.note.isNotBlank()) {
                                    Text(
                                        text = if (record.shiftType == "LEAVE") (if (isBn) "ছুটির কারণ: ${record.note}" else "Reason: ${record.note}") else record.note,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        if (record.shiftType == "ABSENT") {
                            val deductionText = if (summary.dailyAbsentRate > 0.0) {
                                "- " + LaborLawCalculator.formatCurrency(summary.dailyAbsentRate, isBn)
                            } else {
                                if (isBn) "- কর্তন" else "- Deduction"
                            }
                            Text(
                                text = deductionText,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            val currentCalendarYm = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
                            val isRunningMonth = record.yearMonth >= currentCalendarYm
                            val itemRate = if (isRunningMonth) summary.hourlyOtRate else (if (record.hourlyRateSnapshot > 0.0) record.hourlyRateSnapshot else summary.hourlyOtRate)
                            Text(
                                text = LaborLawCalculator.formatCurrency(record.otHours * itemRate, isBn),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
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
                .imePadding()
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
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isBn) "অগ্রিম কর্তন যোগ করুন" else "Add Advance Deduction",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = {
                            hideKeyboard()
                            showAdvanceDialog = false
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isBn) "বাতিল" else "Cancel"
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
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProfessionalDatePickerField(
                        selectedDate = advDate,
                        onDateSelected = { advDate = it },
                        isBn = isBn
                    )
                    OutlinedTextField(
                        value = advAmount,
                        onValueChange = {
                            advAmount = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                            advError = false
                        },
                        label = { Text(if (isBn) "টাকার পরিমাণ (৳)" else "Amount (Tk)") },
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
                            { Text(if (isBn) "সঠিক টাকার পরিমাণ দিন" else "Enter valid amount") }
                        } else null
                    )
                    OutlinedTextField(
                        value = advNote,
                        onValueChange = { advNote = it },
                        label = { Text(if (isBn) "বিবরণ / নোট (ঐচ্ছিক)" else "Note (Optional)") },
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
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                hideKeyboard()
                                showAdvanceDialog = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Text(if (isBn) "বাতিল" else "Cancel")
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
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Text(if (isBn) "সংরক্ষণ করুন" else "Save")
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
                .imePadding()
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
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isBn) "আপন বাজার খরচ যোগ করুন" else "Add Apon Bazar Expense",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = {
                            hideKeyboard()
                            showAponBazarDialog = false
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isBn) "বাতিল" else "Cancel"
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
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProfessionalDatePickerField(
                        selectedDate = aponDate,
                        onDateSelected = { aponDate = it },
                        isBn = isBn
                    )
                    OutlinedTextField(
                        value = aponAmount,
                        onValueChange = {
                            aponAmount = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                            aponError = false
                        },
                        label = { Text(if (isBn) "টাকার পরিমাণ (৳)" else "Amount (Tk)") },
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
                            { Text(if (isBn) "সঠিক টাকার পরিমাণ দিন" else "Enter valid amount") }
                        } else null
                    )
                    OutlinedTextField(
                        value = aponDesc,
                        onValueChange = { aponDesc = it },
                        label = { Text(if (isBn) "বিবরণ (কী কী নিয়েছেন)" else "Description (Items bought)") },
                        placeholder = { Text(if (isBn) "যেমন: চাল, ডাল, তেল" else "e.g. Rice, Oil, Spices") },
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
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                hideKeyboard()
                                showAponBazarDialog = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Text(if (isBn) "বাতিল" else "Cancel")
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
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Text(if (isBn) "সংরক্ষণ করুন" else "Save")
                        }
                    }
                }
            }
        }
    }

    // Deduction Details List In-Screen Sheet (Viewing where how much was added)
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
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isAdv) {
                                    if (isBn) "অগ্রিম কর্তন বিবরণ ($selectedMonth)" else "Advance Deductions ($selectedMonth)"
                                } else {
                                    if (isBn) "আপন বাজার বিবরণ ($selectedMonth)" else "Apon Bazar Details ($selectedMonth)"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { selectedDeductionListType = null }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isBn) "ঠিক আছে" else "Close"
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    if (filteredList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isBn) "চলতি মাসে কোনো এন্ট্রি নেই।" else "No entries for this month.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(filteredList, key = { it.id }) { item ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.date,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (item.description.isNotBlank()) {
                                                Text(
                                                    text = item.description,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            Text(
                                                text = "- " + LaborLawCalculator.formatCurrency(item.amount, isBn),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        if (!summary.isLocked) {
                                            IconButton(onClick = { onDeleteDeduction(item) }) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isBn) "সর্বমোট কর্তন:" else "Total Deduction:",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "- " + LaborLawCalculator.formatCurrency(totalAmt, isBn),
                                fontWeight = FontWeight.ExtraBold,
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
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Button(
                            onClick = { selectedDeductionListType = null },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text(if (isBn) "ঠিক আছে" else "Close")
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
        isBn = isBn,
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
