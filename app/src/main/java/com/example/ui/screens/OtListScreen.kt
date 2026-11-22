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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(spacing.cardPadding)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onPreviousMonth) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = stringResource(R.string.cd_previous_month)
                                )
                            }
                            val formattedMonth = LanguageManager.formatYearMonth(selectedMonth, lang)
                            Text(
                                text = stringResource(R.string.ot_records_header_fmt, formattedMonth),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = onNextMonth) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = stringResource(R.string.cd_next_month)
                                )
                            }
                        }

                        if (!isRunningMonth) {
                            Spacer(modifier = Modifier.height(4.dp))
                            PastMonthLockBanner(
                                selectedMonth = selectedMonth,
                                isLocked = isLocked,
                                onUnlockClick = { showUnlockConfirmDialog = true },
                                onLockClick = { onSetMonthLock(true) }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                        val daysUnit = stringResource(R.string.days_unit)
                        val hoursUnit = stringResource(R.string.hours_unit)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.total_days_label),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${LaborLawCalculator.formatNumber(records.size.toDouble(), isBnDigits, 0)} $daysUnit",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.total_ot_hours),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${LaborLawCalculator.formatNumber(totalHours, isBnDigits)} $hoursUnit",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.ot_amount_label),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = LaborLawCalculator.formatCurrency(totalAmount, isBnDigits),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.attendance_bonus),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val isBonusEarned = attendanceBonus > 0
                                val isBonusLost = isBonusCancelled
                                Text(
                                    text = when {
                                        isBonusEarned -> stringResource(R.string.bonus_earned_check)
                                        isBonusLost -> stringResource(R.string.bonus_deducted_cross)
                                        else -> "0"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
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
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.no_ot_records_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.no_ot_records_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            } else {
                items(records, key = { it.id }) { record ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                        shape = RoundedCornerShape(8.dp),
                                        color = badgeBg,
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = badgeText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = if (isAbsent || isLeave || isWeeklyOff) 12.sp else 14.sp,
                                            color = badgeTextColor,
                                            modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = 4.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = record.date,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        if (record.note.isNotBlank()) {
                                            Text(
                                                text = if (record.shiftType == "LEAVE") stringResource(R.string.leave_reason_fmt, record.note) else record.note,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    Text(
                                        text = deductionText,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    val itemRate = if (isRunningMonth) hourlyRate else (if (record.hourlyRateSnapshot > 0.0) record.hourlyRateSnapshot else hourlyRate)
                                    Text(
                                        text = LaborLawCalculator.formatCurrency(record.otHours * itemRate, isBnDigits),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val shiftType = ShiftType.fromCode(record.shiftType)
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = shiftType.color.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = shiftType.getLabel(),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                color = shiftType.color,
                                                modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                                            )
                                        }

                                        if (record.hasNightBill) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = NightBlue.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.badge_night_bill),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    color = NightBlue,
                                                    modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                                                )
                                            }
                                        }
                                        if (record.hasTiffinBill) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = WarningOrange.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.badge_tiffin_bill),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    color = WarningOrange,
                                                    modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                                                )
                                            }
                                        }
                                        if (record.isHoliday) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.badge_holiday),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    softWrap = false,
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
                                            onClick = { onEditClick(record) }
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = stringResource(R.string.cd_edit),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { recordToDelete = record.id }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.cd_delete),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
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
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = stringResource(R.string.locked),
                                                fontSize = 11.sp,
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
