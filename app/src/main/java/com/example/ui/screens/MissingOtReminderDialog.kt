package com.example.ui.screens

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.OvertimeRecord
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MissingOtDetector {
    /**
     * Scans for any dates in the current running month (from day 1 up to yesterday)
     * that do not have an OvertimeRecord entered.
     * Returns dates in descending order (yesterday first).
     */
    fun getMissingDatesForCurrentMonth(
        existingRecords: List<OvertimeRecord>,
        referenceDate: Date = Date()
    ): List<String> {
        val cal = Calendar.getInstance().apply { time = referenceDate }
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) // 0-based
        val todayDay = cal.get(Calendar.DAY_OF_MONTH)

        // If today is day 1 of the month, yesterday was in previous month -> return empty
        if (todayDay <= 1) return emptyList()

        val existingDateSet = existingRecords.map { it.date }.toSet()
        val missingList = mutableListOf<String>()

        // Check from yesterday down to day 1
        for (day in (todayDay - 1) downTo 1) {
            val dateStr = String.format(Locale.US, "%04d-%02d-%02d", currentYear, currentMonth + 1, day)
            if (!existingDateSet.contains(dateStr)) {
                missingList.add(dateStr)
            }
        }
        return missingList
    }

    /**
     * Scans current month up to yesterday, and during the 1st-5th grace period also checks
     * if the last day of the previous month was missed (when previous month has records and is unlocked).
     */
    fun getMissingDatesForReminder(
        currentMonthRecords: List<OvertimeRecord>,
        allRecords: List<OvertimeRecord>,
        isPreviousMonthUnlocked: Boolean,
        referenceDate: Date = Date()
    ): List<String> {
        val missingList = getMissingDatesForCurrentMonth(currentMonthRecords, referenceDate).toMutableList()
        val prevYm = com.example.util.MonthLockPolicy.getPreviousCalendarYearMonth(referenceDate)
        if (isPreviousMonthUnlocked &&
            com.example.util.MonthLockPolicy.isWithinPreviousMonthGracePeriod(prevYm, referenceDate) &&
            allRecords.any { it.yearMonth == prevYm }
        ) {
            val lastDayOfPrevMonth = com.example.util.MonthLockPolicy.getLastDateOfMonth(prevYm)
            if (allRecords.none { it.date == lastDayOfPrevMonth } && !missingList.contains(lastDayOfPrevMonth)) {
                missingList.add(lastDayOfPrevMonth)
            }
        }
        return missingList
    }

    fun formatDisplayDate(dateStr: String, isBn: Boolean): String {
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr) ?: return dateStr
            val cal = Calendar.getInstance().apply { time = parsed }
            val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val isYesterday = cal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)

            val locale = if (isBn) Locale.forLanguageTag("bn-BD") else Locale.US
            val formattedDate = SimpleDateFormat("dd MMMM, EEEE", locale).format(parsed)

            if (isYesterday) {
                if (isBn) "গতকাল ($formattedDate)" else "Yesterday ($formattedDate)"
            } else {
                formattedDate
            }
        } catch (_: Exception) {
            dateStr
        }
    }

    fun formatDisplayDate(context: Context, dateStr: String, langTag: String): String {
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr) ?: return dateStr
            val cal = Calendar.getInstance().apply { time = parsed }
            val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val isYesterday = cal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)

            val resolvedTag = LanguageManager.resolveSupportedTag(langTag)
            val locale = LanguageManager.localeForTag(resolvedTag)
            val rawFormatted = SimpleDateFormat("dd MMMM, EEEE", locale).format(parsed)
            val formattedDate = if (resolvedTag == "bn") {
                LaborLawCalculator.toBengaliDigits(rawFormatted)
            } else {
                LaborLawCalculator.toEnglishDigits(rawFormatted)
            }

            if (isYesterday) {
                val lctx = LanguageManager.getLocalizedContext(context, resolvedTag)
                lctx.getString(R.string.yesterday_fmt, formattedDate)
            } else {
                formattedDate
            }
        } catch (_: Exception) {
            dateStr
        }
    }
}

@Composable
fun MissingOtReminderDialog(
    missingDates: List<String>,
    lang: String = "en",
    onDismiss: () -> Unit,
    onAddOtForDate: (String) -> Unit
) {
    if (missingDates.isEmpty()) return

    val context = LocalContext.current
    val isSingleDate = missingDates.size == 1
    val primaryMissingDate = missingDates.first()

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .testTag("missing_ot_reminder_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top close button and alert badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventBusy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Title
                Text(
                    text = stringResource(R.string.missing_ot_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Descriptive Sub-text
                val subText = if (isSingleDate) {
                    stringResource(
                        R.string.missing_ot_single_msg_fmt,
                        MissingOtDetector.formatDisplayDate(context, primaryMissingDate, lang)
                    )
                } else {
                    stringResource(R.string.missing_ot_multi_msg_fmt, missingDates.size)
                }

                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Missing items list or single item banner
                if (isSingleDate) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = MissingOtDetector.formatDisplayDate(context, primaryMissingDate, lang),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = { onAddOtForDate(primaryMissingDate) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("add_missing_ot_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.add_overtime_btn),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                } else {
                    // Multiple dates scrollable list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(missingDates, key = { it }) { dateStr ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = MissingOtDetector.formatDisplayDate(context, dateStr, lang),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = { onAddOtForDate(dateStr) },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 12.dp,
                                            vertical = 6.dp
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("add_missing_date_${dateStr}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.add),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom dismiss button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dismiss_missing_ot_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.later_dismiss_btn),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
