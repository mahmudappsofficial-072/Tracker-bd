package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.OvertimeRecord
import com.example.ui.components.AutoFitSingleLineText
import com.example.ui.theme.NightBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager
import com.example.util.ResponsiveUtils

@Composable
fun OtListScreen(
    selectedMonth: String,
    records: List<OvertimeRecord>,
    hourlyRate: Double,
    dailyAbsentRate: Double = 0.0,
    attendanceBonus: Double = 0.0,
    isBonusCancelled: Boolean = false,
    isLocked: Boolean = false,
    lang: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (OvertimeRecord) -> Unit,
    onDeleteClick: (Long) -> Unit,
    onSetMonthLock: (Boolean) -> Unit = {}
) {
    val isBnDigits = LanguageManager.isBengaliDigits(lang)
    val spacing = ResponsiveUtils.rememberAppSpacing()
    var recordToDelete by remember { mutableStateOf<Long?>(null) }
    var showLockedInfoDialog by remember { mutableStateOf(false) }
    var showUnlockConfirmDialog by remember { mutableStateOf(false) }

    val currentCalendarYm = remember {
        java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
    }
    val isRunningMonth = selectedMonth >= currentCalendarYm

    val totalHours = records.sumOf { it.otHours }
    val totalAmount = records.sumOf { record ->
        val itemRate = if (isRunningMonth) hourlyRate else (if (record.hourlyRateSnapshot > 0.0) record.hourlyRateSnapshot else hourlyRate)
        record.otHours * itemRate
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (!isLocked) {
                FloatingActionButton(
                    onClick = onAddClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_ot_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_ot))
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("ot_list_screen"),
            contentPadding = spacing.screenContentWithFab,
            verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)
        ) {
            // Month Switcher Header
            item {
                Card(
                    shape = RoundedCornerShape(spacing.cardRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(spacing.cardPadding)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onPreviousMonth,
                                modifier = Modifier.size(spacing.compactButtonHeight)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = stringResource(R.string.cd_previous_month),
                                    modifier = Modifier.size(spacing.iconLg)
                                )
                            }
                            val formattedMonth = LanguageManager.formatYearMonth(selectedMonth, lang)
                            AutoFitSingleLineText(
                                text = stringResource(R.string.ot_records_header_fmt, formattedMonth),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxFontSize = 15.sp,
                                minFontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = spacing.rowGapSm)
                            )
                            IconButton(
                                onClick = onNextMonth,
                                modifier = Modifier.size(spacing.compactButtonHeight)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = stringResource(R.string.cd_next_month),
                                    modifier = Modifier.size(spacing.iconLg)
                                )
                            }
                        }

                        if (!isRunningMonth) {
                            Spacer(modifier = Modifier.height(spacing.rowGapSm))
                            PastMonthLockBanner(
                                selectedMonth = selectedMonth,
                                isLocked = isLocked,
                                onUnlockClick = { showUnlockConfirmDialog = true },
                                onLockClick = { onSetMonthLock(true) }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = spacing.rowGapSm))

                        val daysUnit = stringResource(R.string.days_unit)
                        val hoursUnit = stringResource(R.string.hours_unit)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.total_days_label),
                                    maxFontSize = 10.sp,
                                    minFontSize = 7.5.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AutoFitSingleLineText(
                                    text = "${LaborLawCalculator.formatNumber(records.size.toDouble(), isBnDigits, 0)} $daysUnit",
                                    maxFontSize = 12.sp,
                                    minFontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.total_ot_hours),
                                    maxFontSize = 10.sp,
                                    minFontSize = 7.5.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AutoFitSingleLineText(
                                    text = "${LaborLawCalculator.formatNumber(totalHours, isBnDigits)} $hoursUnit",
                                    maxFontSize = 12.sp,
                                    minFontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.ot_amount_label),
                                    maxFontSize = 10.sp,
                                    minFontSize = 7.5.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AutoFitSingleLineText(
                                    text = LaborLawCalculator.formatCurrency(totalAmount, isBnDigits),
                                    maxFontSize = 12.sp,
                                    minFontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.attendance_bonus),
                                    maxFontSize = 10.sp,
                                    minFontSize = 7.5.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val isBonusEarned = attendanceBonus > 0
                                val isBonusLost = isBonusCancelled
                                AutoFitSingleLineText(
                                    text = when {
                                        isBonusEarned -> stringResource(R.string.bonus_earned_check)
                                        isBonusLost -> stringResource(R.string.bonus_deducted_cross)
                                        else -> "0"
                                    },
                                    maxFontSize = 12.sp,
                                    minFontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = if (isBonusEarned) SuccessGreen else if (isBonusLost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (records.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = spacing.emptyStateVertical),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(spacing.avatarSize),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(spacing.rowGapSm))
                            AutoFitSingleLineText(
                                text = stringResource(R.string.no_ot_records_title),
                                style = MaterialTheme.typography.titleSmall,
                                maxFontSize = 14.sp,
                                minFontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(spacing.rowGapSm))
                            Text(
                                text = stringResource(R.string.no_ot_records_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            } else {
                items(records, key = { it.id }) { record ->
                    Card(
                        shape = RoundedCornerShape(spacing.subCardRadius),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!isLocked) {
                                    onEditClick(record)
                                } else {
                                    showLockedInfoDialog = true
                                }
                            }
                    ) {
                        Column(modifier = Modifier.padding(spacing.listItemPadding)) {
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
                                    val isAbsent = record.shiftType == "ABSENT"
                                    val isLeave = record.shiftType == "LEAVE"
                                    val isWeeklyOff = record.shiftType == "WEEKLY_OFF"

                                    val badgeBg = when {
                                        isAbsent -> MaterialTheme.colorScheme.errorContainer
                                        isLeave || isWeeklyOff -> MaterialTheme.colorScheme.secondaryContainer
                                        record.isHoliday -> MaterialTheme.colorScheme.tertiaryContainer
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    }
                                    val badgeTextColor = when {
                                        isAbsent -> MaterialTheme.colorScheme.onErrorContainer
                                        isLeave || isWeeklyOff -> MaterialTheme.colorScheme.onSecondaryContainer
                                        record.isHoliday -> MaterialTheme.colorScheme.onTertiaryContainer
                                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                    val hoursUnit = stringResource(R.string.hours_unit)
                                    val badgeText = when {
                                        isAbsent -> stringResource(R.string.badge_absent)
                                        isLeave -> stringResource(R.string.badge_leave)
                                        isWeeklyOff -> stringResource(R.string.badge_off_day)
                                        else -> "${LaborLawCalculator.formatNumber(record.otHours, isBnDigits)} $hoursUnit"
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(spacing.badgeRadius),
                                        color = badgeBg,
                                        modifier = Modifier.padding(end = spacing.rowGapMd)
                                    ) {
                                        AutoFitSingleLineText(
                                            text = badgeText,
                                            fontWeight = FontWeight.Bold,
                                            maxFontSize = if (isAbsent || isLeave || isWeeklyOff) 11.sp else 12.5.sp,
                                            minFontSize = 8.5.sp,
                                            color = badgeTextColor,
                                            modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        AutoFitSingleLineText(
                                            text = record.date,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxFontSize = 13.5.sp,
                                            minFontSize = 10.sp
                                        )
                                        if (record.note.isNotBlank()) {
                                            Text(
                                                text = if (record.shiftType == "LEAVE") stringResource(R.string.leave_reason_fmt, record.note) else record.note,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                if (record.shiftType == "ABSENT") {
                                    val deductionText = if (dailyAbsentRate > 0.0) {
                                        "- " + LaborLawCalculator.formatCurrency(dailyAbsentRate, isBnDigits)
                                    } else {
                                        stringResource(R.string.badge_salary_deduction)
                                    }
                                    AutoFitSingleLineText(
                                        text = deductionText,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxFontSize = 14.sp,
                                        minFontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    val itemRate = if (isRunningMonth) hourlyRate else (if (record.hourlyRateSnapshot > 0.0) record.hourlyRateSnapshot else hourlyRate)
                                    AutoFitSingleLineText(
                                        text = LaborLawCalculator.formatCurrency(record.otHours * itemRate, isBnDigits),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxFontSize = 14.sp,
                                        minFontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(spacing.rowGapSm))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f).padding(end = spacing.rowGapSm)) {
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm),
                                        verticalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
                                    ) {
                                        val shiftType = ShiftType.fromCode(record.shiftType)
                                        Surface(
                                            shape = RoundedCornerShape(spacing.badgeRadius),
                                            color = shiftType.color.copy(alpha = 0.15f)
                                        ) {
                                            AutoFitSingleLineText(
                                                text = shiftType.getLabel(),
                                                maxFontSize = 10.sp,
                                                minFontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = shiftType.color,
                                                modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                                            )
                                        }

                                        if (record.hasNightBill) {
                                            Surface(
                                                shape = RoundedCornerShape(spacing.badgeRadius),
                                                color = NightBlue.copy(alpha = 0.15f)
                                            ) {
                                                AutoFitSingleLineText(
                                                    text = stringResource(R.string.badge_night_bill),
                                                    maxFontSize = 10.sp,
                                                    minFontSize = 8.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = NightBlue,
                                                    modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                                                )
                                            }
                                        }
                                        if (record.hasTiffinBill) {
                                            Surface(
                                                shape = RoundedCornerShape(spacing.badgeRadius),
                                                color = WarningOrange.copy(alpha = 0.15f)
                                            ) {
                                                AutoFitSingleLineText(
                                                    text = stringResource(R.string.badge_tiffin_bill),
                                                    maxFontSize = 10.sp,
                                                    minFontSize = 8.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = WarningOrange,
                                                    modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                                                )
                                            }
                                        }
                                        if (record.isHoliday) {
                                            Surface(
                                                shape = RoundedCornerShape(spacing.badgeRadius),
                                                color = MaterialTheme.colorScheme.tertiaryContainer
                                            ) {
                                                AutoFitSingleLineText(
                                                    text = stringResource(R.string.badge_holiday),
                                                    maxFontSize = 10.sp,
                                                    minFontSize = 8.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (!isLocked) {
                                    Row {
                                        IconButton(
                                            onClick = { onEditClick(record) },
                                            modifier = Modifier.size(spacing.compactButtonHeight)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = stringResource(R.string.cd_edit),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(spacing.iconMd)
                                            )
                                        }
                                        IconButton(
                                            onClick = { recordToDelete = record.id },
                                            modifier = Modifier.size(spacing.compactButtonHeight)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.cd_delete),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(spacing.iconMd)
                                            )
                                        }
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(spacing.badgeRadius),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.clickable { showLockedInfoDialog = true }
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
                                                maxFontSize = 10.sp,
                                                minFontSize = 8.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium
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

    if (recordToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text(stringResource(R.string.delete_ot_title)) },
            text = { Text(stringResource(R.string.delete_ot_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        recordToDelete?.let { onDeleteClick(it) }
                        recordToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

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
