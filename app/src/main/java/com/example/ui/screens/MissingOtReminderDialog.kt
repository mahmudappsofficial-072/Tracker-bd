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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.ui.components.AutoFitSingleLineText
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager
import com.example.util.ResponsiveUtils
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
    val spacing = ResponsiveUtils.rememberAppSpacing()
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
            .padding(spacing.dialogOuterPadding),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(spacing.heroRadius))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .testTag("missing_ot_reminder_dialog"),
            shape = RoundedCornerShape(spacing.heroRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.dialogCardPadding),
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
                            .size(spacing.avatarSize)
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
                            modifier = Modifier.size(spacing.iconLg)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.dialogInnerGap))

                // Title
                AutoFitSingleLineText(
                    text = stringResource(R.string.missing_ot_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxFontSize = spacing.adaptiveSp(20f, 18f, 16f),
                    minFontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(spacing.rowGapSm))

                // Descriptive Sub-text
                val subText = if (isSingleDate) {
                    stringResource(
                        R.string.missing_ot_single_msg_fmt,
                        MissingOtDetector.formatDisplayDate(context, primaryMissingDate, lang)
                    )
                } else {
                    stringResource(R.string.missing_ot_multi_msg_fmt, missingDates.size)
                }

                // Missing items list or single item banner
                if (isSingleDate) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = subText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(spacing.dialogInnerGap))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(spacing.cardRadius)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(spacing.subCardPadding),
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
                                        modifier = Modifier.size(spacing.iconMd)
                                    )
                                    Spacer(modifier = Modifier.width(spacing.rowGapMd))
                                    AutoFitSingleLineText(
                                        text = MissingOtDetector.formatDisplayDate(context, primaryMissingDate, lang),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxFontSize = spacing.adaptiveSp(17f, 15.5f, 14f),
                                        minFontSize = 10.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(spacing.rowGapMd))

                                Button(
                                    onClick = { onAddOtForDate(primaryMissingDate) },
                                    contentPadding = spacing.compactButtonContentPadding,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = spacing.minTouchTarget)
                                        .testTag("add_missing_ot_btn"),
                                    shape = RoundedCornerShape(spacing.cardRadius),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(spacing.iconMd)
                                    )
                                    Spacer(modifier = Modifier.width(spacing.rowGapSm))
                                    AutoFitSingleLineText(
                                        text = stringResource(R.string.add_overtime_btn),
                                        fontWeight = FontWeight.Bold,
                                        maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                                        minFontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = subText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(spacing.dialogInnerGap))

                        // Multiple dates scrollable list
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(max = if (spacing.isShortScreen) 180.dp else 240.dp),
                            verticalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
                        ) {
                            items(missingDates, key = { it }) { dateStr ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(spacing.cardRadius)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = spacing.minTouchTarget)
                                            .padding(horizontal = spacing.listItemPadding, vertical = spacing.rowGapMd),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = spacing.rowGapSm)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CalendarMonth,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(spacing.iconMd)
                                            )
                                            Spacer(modifier = Modifier.width(spacing.rowGapMd))
                                            AutoFitSingleLineText(
                                                text = MissingOtDetector.formatDisplayDate(context, dateStr, lang),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                                                minFontSize = 9.5.sp
                                            )
                                        }

                                        Button(
                                            onClick = { onAddOtForDate(dateStr) },
                                            contentPadding = spacing.compactButtonContentPadding,
                                            shape = RoundedCornerShape(spacing.subCardRadius),
                                            modifier = Modifier
                                                .heightIn(min = spacing.compactButtonHeight)
                                                .testTag("add_missing_date_${dateStr}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(spacing.iconSm)
                                            )
                                            Spacer(modifier = Modifier.width(spacing.rowGapSm))
                                            AutoFitSingleLineText(
                                                text = stringResource(R.string.add),
                                                maxFontSize = spacing.adaptiveSp(14f, 13f, 12f),
                                                minFontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.dialogInnerGap))

                // Bottom dismiss button
                OutlinedButton(
                    onClick = onDismiss,
                    contentPadding = spacing.compactButtonContentPadding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = spacing.minTouchTarget)
                        .testTag("dismiss_missing_ot_btn"),
                    shape = RoundedCornerShape(spacing.cardRadius)
                ) {
                    AutoFitSingleLineText(
                        text = stringResource(R.string.later_dismiss_btn),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxFontSize = spacing.adaptiveSp(14.5f, 13.5f, 12.5f),
                        minFontSize = 9.5.sp
                    )
                }
            }
        }
    }
}
