package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OvertimeRecord
import com.example.ui.theme.NightBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager

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
    val isBn = lang == "bn"
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
                    Icon(Icons.Default.Add, contentDescription = "Add OT")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("ot_list_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Month Switcher Header
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onPreviousMonth) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                            }
                            val formattedMonth = LanguageManager.formatYearMonth(selectedMonth, isBn)
                            Text(
                                text = if (isBn) "ওটি খাতা • $formattedMonth" else "OT Records • $formattedMonth",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = onNextMonth) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                            }
                        }

                        if (!isRunningMonth) {
                            Spacer(modifier = Modifier.height(6.dp))
                            PastMonthLockBanner(
                                selectedMonth = selectedMonth,
                                isLocked = isLocked,
                                isBn = isBn,
                                onUnlockClick = { showUnlockConfirmDialog = true },
                                onLockClick = { onSetMonthLock(true) }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Monthly Summary Stats in List
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (isBn) "মোট দিন" else "Total Days",
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${LaborLawCalculator.formatNumber(records.size.toDouble(), isBn, 0)} ${if (isBn) "দিন" else "days"}",
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
                                    text = if (isBn) "মোট ওটি ঘণ্টা" else "Total OT Hours",
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${LaborLawCalculator.formatNumber(totalHours, isBn)} ${if (isBn) "ঘণ্টা" else "hrs"}",
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
                                    text = if (isBn) "ওটি টাকা" else "OT Amount",
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = LaborLawCalculator.formatCurrency(totalAmount, isBn),
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
                                    text = if (isBn) "হাজিরা বোনাস" else "Attendance Bonus",
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
                                    text = if (isBonusEarned) {
                                        if (isBn) "অর্জিত ✅" else "Earned ✅"
                                    } else if (isBonusLost) {
                                        if (isBn) "কর্তন ❌" else "Deducted ❌"
                                    } else "0",
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
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isBn) "এই মাসে এখনও কোনো ওটি রেকর্ড নেই" else "No OT records found for this month",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isBn) "নিচের '+' বাটনে ট্যাপ করে নতুন ওটি যোগ করুন" else "Tap the '+' button below to add a new OT record",
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
                        Column(modifier = Modifier.padding(14.dp)) {
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
                                    val badgeText = when {
                                        isAbsent -> if (isBn) "অনুপস্থিত" else "Absent"
                                        isLeave -> if (isBn) "ছুটি" else "Leave"
                                        isWeeklyOff -> if (isBn) "সাপ্তাহিক ছুটি" else "Off Day"
                                        else -> "${LaborLawCalculator.formatNumber(record.otHours, isBn)} ${if (isBn) "ঘণ্টা" else "hrs"}"
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = badgeBg,
                                        modifier = Modifier.padding(end = 10.dp)
                                    ) {
                                        Text(
                                            text = badgeText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = if (isAbsent || isLeave || isWeeklyOff) 12.sp else 14.sp,
                                            color = badgeTextColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
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
                                                text = if (record.shiftType == "LEAVE") (if (isBn) "ছুটির কারণ: ${record.note}" else "Reason: ${record.note}") else record.note,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (record.shiftType == "ABSENT") {
                                    val deductionText = if (dailyAbsentRate > 0.0) {
                                        "- " + LaborLawCalculator.formatCurrency(dailyAbsentRate, isBn)
                                    } else {
                                        if (isBn) "- বেতন কর্তন" else "- Deduction"
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
                                        text = LaborLawCalculator.formatCurrency(record.otHours * itemRate, isBn),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Badges
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
                                                text = shiftType.getLabel(isBn),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                color = shiftType.color,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        if (record.hasNightBill) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = NightBlue.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = if (isBn) "🌙 নাইট বিল" else "🌙 Night Bill",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    color = NightBlue,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (record.hasTiffinBill) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = WarningOrange.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = if (isBn) "☕ টিফিন বিল" else "☕ Tiffin Bill",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    color = WarningOrange,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (record.isHoliday) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer
                                            ) {
                                                Text(
                                                    text = if (isBn) "🎉 ছুটির দিন" else "🎉 Holiday",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Actions: Edit & Delete
                                if (!isLocked) {
                                    Row {
                                        IconButton(
                                            onClick = { onEditClick(record) }
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { recordToDelete = record.id }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
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
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                                                text = if (isBn) "লকড" else "Locked",
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

    // Delete Confirmation Dialog
    if (recordToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text(if (isBn) "ওটি রেকর্ড মুছবেন?" else "Delete OT Record?") },
            text = { Text(if (isBn) "এই ওটি রেকর্ডটি রিসাইকেল বিন (Trash)-এ চলে যাবে। সেখানে এটি ১ দিন জমা থাকবে এবং আপনি চাইলে যেকোনো সময় ফিরিয়ে আনতে পারবেন।" else "This OT record will be moved to Recycle Bin (Trash), where it will be stored for 1 day and can be restored anytime.") },
            confirmButton = {
                Button(
                    onClick = {
                        recordToDelete?.let { onDeleteClick(it) }
                        recordToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isBn) "মুছে ফেলুন" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text(if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
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
