package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OvertimeRecord
import com.example.ui.theme.WarningOrange
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ShiftType(
    val code: String,
    val labelBn: String,
    val labelEn: String,
    val color: Color
) {
    REGULAR("REGULAR", "নিয়মিত", "Regular", Color(0xFF059669)),
    DAY("DAY", "দিন", "Day", Color(0xFFD97706)),
    NIGHT("NIGHT", "রাত", "Night", Color(0xFF2563EB)),
    HOLIDAY("HOLIDAY", "ছুটির দিন", "Holiday", Color(0xFF9333EA)),
    WEEKLY_OFF("WEEKLY_OFF", "সাপ্তাহিক ছুটি", "Weekly Off", Color(0xFF0D9488)),
    LEAVE("LEAVE", "সাময়িক ছুটি", "Leave", Color(0xFF0284C7)),
    ABSENT("ABSENT", "অনুপস্থিত", "Absent", Color(0xFFDC2626));

    fun getLabel(isBn: Boolean) = if (isBn) labelBn else labelEn

    companion object {
        fun fromCode(code: String): ShiftType = entries.firstOrNull { it.code == code } ?: REGULAR
    }
}

internal fun parseDateToUtcMillis(dateStr: String): Long? {
    return try {
        val utcSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        utcSdf.parse(dateStr)?.time
    } catch (_: Exception) {
        null
    }
}

internal fun formatUtcMillisToDate(millis: Long): String {
    val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = millis
    }
    return String.format(
        Locale.US,
        "%04d-%02d-%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalDatePickerField(
    selectedDate: String, // "YYYY-MM-DD"
    onDateSelected: (String) -> Unit,
    isBn: Boolean,
    modifier: Modifier = Modifier
) {
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val hideKeyboard = {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val todayDateStr = remember { sdf.format(Date()) }
    val yesterdayDateStr = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        sdf.format(cal.time)
    }

    // Format display string
    val formattedDisplayDate = remember(selectedDate, isBn) {
        try {
            val parsed = sdf.parse(selectedDate)
            if (parsed != null) {
                val cal = Calendar.getInstance().apply { time = parsed }
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val dayName = when (dayOfWeek) {
                    Calendar.SATURDAY -> if (isBn) "শনিবার" else "Saturday"
                    Calendar.SUNDAY -> if (isBn) "রবিবার" else "Sunday"
                    Calendar.MONDAY -> if (isBn) "সোমবার" else "Monday"
                    Calendar.TUESDAY -> if (isBn) "মঙ্গলবার" else "Tuesday"
                    Calendar.WEDNESDAY -> if (isBn) "বুধবার" else "Wednesday"
                    Calendar.THURSDAY -> if (isBn) "বৃহস্পতিবার" else "Thursday"
                    Calendar.FRIDAY -> if (isBn) "শুক্রবার" else "Friday"
                    else -> ""
                }
                val formatted = SimpleDateFormat("dd MMMM yyyy", if (isBn) Locale.forLanguageTag("bn-BD") else Locale.US).format(parsed)
                val display = if (isBn) LaborLawCalculator.toBengaliDigits(formatted) else formatted
                val suffix = when (selectedDate) {
                    todayDateStr -> if (isBn) " • আজকের দিন" else " • Today"
                    yesterdayDateStr -> if (isBn) " • গতকাল" else " • Yesterday"
                    else -> ""
                }
                "$display ($dayName)$suffix"
            } else selectedDate
        } catch (e: Exception) {
            selectedDate
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Quick Action Chips: [ 📌 আজ ], [ ⏪ গতকাল ], [ 📅 ক্যালেন্ডার ]
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedDate == todayDateStr,
                onClick = {
                    hideKeyboard()
                    onDateSelected(todayDateStr)
                },
                label = { Text(if (isBn) "📌 আজ" else "📌 Today", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.testTag("date_chip_today")
            )

            FilterChip(
                selected = selectedDate == yesterdayDateStr,
                onClick = {
                    hideKeyboard()
                    onDateSelected(yesterdayDateStr)
                },
                label = { Text(if (isBn) "⏪ গতকাল" else "⏪ Yesterday", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.testTag("date_chip_yesterday")
            )

            AssistChip(
                onClick = {
                    hideKeyboard()
                    showDatePickerDialog = true
                },
                label = { Text(if (isBn) "📅 ক্যালেন্ডার" else "📅 Calendar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("date_chip_calendar")
            )
        }

        // Tappable Date Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    hideKeyboard()
                    showDatePickerDialog = true
                }
                .testTag("tappable_date_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Select Date",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isBn) "নির্বাচিত তারিখ (চাপুন):" else "Selected Date (Tap):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formattedDisplayDate,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (isBn) "বদলান" else "Change",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = parseDateToUtcMillis(selectedDate) ?: System.currentTimeMillis()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            onDateSelected(formatUtcMillisToDate(millis))
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text(if (isBn) "ঠিক আছে" else "OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text(if (isBn) "বাতিল" else "Cancel")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        text = if (isBn) "তারিখ বেছে নিন" else "Select Date",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    }
}

@Composable
fun QuickOtEntryDialog(
    initialRecord: OvertimeRecord? = null,
    existingRecords: List<OvertimeRecord> = emptyList(),
    hourlyRate: Double,
    profile: com.example.data.model.UserProfile? = null,
    lang: String,
    onDismiss: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
    onSave: (date: String, hours: Double, nightBill: Boolean, tiffinBill: Boolean, isHoliday: Boolean, shiftType: String, note: String) -> Unit
) {
    val isBn = lang == "bn"
    val todayStr = remember {
        initialRecord?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    var date by remember { mutableStateOf(todayStr) }
    var selectedShift by remember {
        mutableStateOf(initialRecord?.shiftType ?: "REGULAR")
    }
    var hoursText by remember {
        mutableStateOf(
            if (initialRecord != null) LaborLawCalculator.formatEditableNumber(initialRecord.otHours) else ""
        )
    }
    var hasNightBill by remember { mutableStateOf(initialRecord?.hasNightBill ?: false) }
    var hasTiffinBill by remember { mutableStateOf(initialRecord?.hasTiffinBill ?: false) }
    var isHoliday by remember { mutableStateOf(initialRecord?.isHoliday ?: false) }
    var note by remember { mutableStateOf(initialRecord?.note ?: "") }

    // Warning dialog type: "SALARY", "NIGHT_BILL", "TIFFIN_BILL" or null
    var missingFieldWarning by remember { mutableStateOf<String?>(null) }
    // Overwrite confirmation dialog holds the existing record that would be overwritten
    var pendingOverwriteRecord by remember { mutableStateOf<OvertimeRecord?>(null) }

    val presetHours = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0)
    val parsedHours = LaborLawCalculator.parseLocalizedDouble(hoursText)
    val calculatedAmount = parsedHours * hourlyRate

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

    BackHandler(enabled = missingFieldWarning == null && pendingOverwriteRecord == null) {
        hideKeyboard()
        onDismiss()
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
            .testTag("quick_ot_entry_sheet"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
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
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (initialRecord != null && initialRecord.id != 0L) (if (isBn) "ওটি তথ্য সম্পাদন" else "Edit OT Entry") else LanguageManager.get("quick_ot_entry", lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = {
                            hideKeyboard()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("close_quick_ot_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = LanguageManager.get("cancel", lang)
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
                // Professional Date Picker Field with Quick Chips
                ProfessionalDatePickerField(
                    selectedDate = date,
                    onDateSelected = { date = it },
                    isBn = isBn
                )

                // Shift Selector Section
                Text(
                    text = if (isBn) "শিফট / উপস্থিতির ধরন:" else "Shift / Attendance Type:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        ShiftType.REGULAR,
                        ShiftType.DAY,
                        ShiftType.NIGHT,
                        ShiftType.HOLIDAY,
                        ShiftType.WEEKLY_OFF,
                        ShiftType.LEAVE,
                        ShiftType.ABSENT
                    ).forEach { shift ->
                        val isSelected = selectedShift == shift.code
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                hideKeyboard()
                                selectedShift = shift.code
                                when (shift) {
                                    ShiftType.NIGHT -> {
                                        if ((profile?.nightBillRate ?: 0.0) <= 0.0) {
                                            hasNightBill = false
                                            missingFieldWarning = "NIGHT_BILL"
                                        } else {
                                            hasNightBill = true
                                        }
                                    }
                                    ShiftType.HOLIDAY -> isHoliday = true
                                    ShiftType.DAY -> {
                                        isHoliday = false
                                    }
                                    ShiftType.REGULAR -> {
                                        isHoliday = false
                                    }
                                    ShiftType.WEEKLY_OFF -> isHoliday = true
                                    ShiftType.LEAVE, ShiftType.ABSENT -> {
                                        hoursText = "0.0"
                                        hasNightBill = false
                                        hasTiffinBill = false
                                    }
                                }
                            },
                            label = {
                                Text(
                                    text = shift.getLabel(isBn),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = shift.color.copy(alpha = 0.2f),
                                selectedLabelColor = shift.color
                            )
                        )
                    }
                }

                // When shift is Weekly Off, Leave, or Absent, hide all OT inputs and show status info
                val isOffOrLeaveShift = selectedShift == ShiftType.WEEKLY_OFF.code ||
                        selectedShift == ShiftType.LEAVE.code ||
                        selectedShift == ShiftType.ABSENT.code

                if (isOffOrLeaveShift) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when (selectedShift) {
                            ShiftType.ABSENT.code -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            ShiftType.LEAVE.code -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = when (selectedShift) {
                                ShiftType.ABSENT.code -> if (isBn)
                                    "⚠️ আজকের দিনটি অনুপস্থিত হিসেবে রেকর্ড হবে (দৈনিক বেতন কর্তন হবে এবং শর্তানুযায়ী হাজিরা বোনাস হিসাব হবে)।"
                                else
                                    "⚠️ Marked as Absent (Daily salary deducted; attendance bonus evaluated per policy)."
                                ShiftType.LEAVE.code -> if (isBn)
                                    "🏖️ আজকের দিনটি ছুটি হিসেবে গণ্য হবে (হাজিরা কাটা যাবে না)।"
                                else
                                    "🏖️ Marked as Leave (Attendance bonus preserved)."
                                else -> if (isBn)
                                    "🌴 আজকের দিনটি সাপ্তাহিক ছুটি হিসেবে সংরক্ষিত হবে (ওটি প্রযোজ্য নয়)।"
                                else
                                    "🌴 Marked as Weekly Off (No OT applicable)."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = when (selectedShift) {
                                ShiftType.ABSENT.code -> MaterialTheme.colorScheme.onErrorContainer
                                ShiftType.LEAVE.code -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(14.dp)
                        )
                    }

                    if (selectedShift == ShiftType.LEAVE.code) {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text(if (isBn) "ছুটির কারণ" else "Reason for Leave") },
                            placeholder = { Text(if (isBn) "যেমন: অসুস্থতা, গ্রামের বাড়ি যাওয়া, পারিবারিক কাজ" else "e.g. Sick leave, Personal emergency") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("leave_reason_input"),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { hideKeyboard() }),
                            singleLine = false,
                            maxLines = 3
                        )
                    }
                } else {
                    // Regular OT Inputs (Visible only for regular/work shifts)

                    // Preset hour chips
                    Text(
                        text = if (isBn) "দ্রুত ওটি ঘণ্টা নির্বাচন:" else "Quick OT Hours Selection:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        presetHours.take(4).forEach { h ->
                            AssistChip(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    hideKeyboard()
                                    hoursText = h.toInt().toString()
                                },
                                label = {
                                    Text(
                                        text = "${LaborLawCalculator.formatNumber(h, isBn, 0)} ${if (isBn) "ঘ" else "h"}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (parsedHours == h) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        presetHours.drop(4).forEach { h ->
                            AssistChip(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    hideKeyboard()
                                    hoursText = h.toInt().toString()
                                },
                                label = {
                                    Text(
                                        text = "${LaborLawCalculator.formatNumber(h, isBn, 0)} ${if (isBn) "ঘ" else "h"}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (parsedHours == h) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    }

                    // Hours custom input
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { hoursText = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true) },
                        label = { Text(LanguageManager.get("hours", lang)) },
                        placeholder = { Text("0") },
                        modifier = Modifier.fillMaxWidth().testTag("ot_hours_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { hideKeyboard() }),
                        leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                        supportingText = {
                            Text(
                                text = if (isBn) "আনুমানিক ওটি টাকা: ${LaborLawCalculator.formatCurrency(calculatedAmount, isBn)}" else "Est. OT Amount: ${LaborLawCalculator.formatCurrency(calculatedAmount, isBn)}",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )

                    // Additional Checkboxes
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        hideKeyboard()
                                        if (!hasNightBill) {
                                            if ((profile?.nightBillRate ?: 0.0) <= 0.0) {
                                                missingFieldWarning = "NIGHT_BILL"
                                            } else {
                                                hasNightBill = true
                                            }
                                        } else {
                                            hasNightBill = false
                                        }
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = hasNightBill,
                                    onCheckedChange = { checked ->
                                        hideKeyboard()
                                        if (checked) {
                                            if ((profile?.nightBillRate ?: 0.0) <= 0.0) {
                                                missingFieldWarning = "NIGHT_BILL"
                                            } else {
                                                hasNightBill = true
                                            }
                                        } else {
                                            hasNightBill = false
                                        }
                                    },
                                    modifier = Modifier.testTag("night_bill_checkbox")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(LanguageManager.get("night_bill", lang), style = MaterialTheme.typography.bodyMedium)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        hideKeyboard()
                                        if (!hasTiffinBill) {
                                            if ((profile?.tiffinBillRate ?: 0.0) <= 0.0) {
                                                missingFieldWarning = "TIFFIN_BILL"
                                            } else {
                                                hasTiffinBill = true
                                            }
                                        } else {
                                            hasTiffinBill = false
                                        }
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = hasTiffinBill,
                                    onCheckedChange = { checked ->
                                        hideKeyboard()
                                        if (checked) {
                                            if ((profile?.tiffinBillRate ?: 0.0) <= 0.0) {
                                                missingFieldWarning = "TIFFIN_BILL"
                                            } else {
                                                hasTiffinBill = true
                                            }
                                        } else {
                                            hasTiffinBill = false
                                        }
                                    },
                                    modifier = Modifier.testTag("tiffin_bill_checkbox")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(LanguageManager.get("tiffin_bill", lang), style = MaterialTheme.typography.bodyMedium)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        hideKeyboard()
                                        isHoliday = !isHoliday
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = isHoliday,
                                    onCheckedChange = {
                                        hideKeyboard()
                                        isHoliday = it
                                    },
                                    modifier = Modifier.testTag("holiday_checkbox")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(LanguageManager.get("holiday_ot", lang), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    // Note input
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(LanguageManager.get("note", lang)) },
                        modifier = Modifier.fillMaxWidth().testTag("ot_note_input"),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { hideKeyboard() }),
                        singleLine = true
                    )
                }
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
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(LanguageManager.get("cancel", lang))
                    }
                    Button(
                        onClick = {
                            hideKeyboard()
                            val finalHours = LaborLawCalculator.parseLocalizedDouble(hoursText)
                            // Validation 1: If hours > 0 but basic salary or hourlyRate <= 0
                            if (finalHours > 0.0 && hourlyRate <= 0.0 && (profile?.basicSalary ?: 0.0) <= 0.0) {
                                missingFieldWarning = "SALARY"
                                return@Button
                            }
                            // Validation 2: If night bill selected but rate <= 0
                            if (hasNightBill && (profile?.nightBillRate ?: 0.0) <= 0.0) {
                                missingFieldWarning = "NIGHT_BILL"
                                return@Button
                            }
                            // Validation 3: If tiffin bill selected but rate <= 0
                            if (hasTiffinBill && (profile?.tiffinBillRate ?: 0.0) <= 0.0) {
                                missingFieldWarning = "TIFFIN_BILL"
                                return@Button
                            }

                            if (finalHours >= 0.0 && date.isNotBlank()) {
                                // Check if an existing different record exists on the chosen date
                                val existingOnDate = existingRecords.find { it.date == date }
                                if (existingOnDate != null && initialRecord?.id != existingOnDate.id) {
                                    pendingOverwriteRecord = existingOnDate
                                    return@Button
                                }

                                onSave(date, finalHours, hasNightBill, hasTiffinBill, isHoliday, selectedShift, note)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("save_ot_button")
                    ) {
                        Text(LanguageManager.get("save", lang))
                    }
                }
            }
        }
    }

    // Interactive Alert Dialog when required rates are missing in profile
    if (missingFieldWarning != null) {
        val warningType = missingFieldWarning
        AlertDialog(
            onDismissRequest = { missingFieldWarning = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (warningType) {
                            "NIGHT_BILL" -> if (isBn) "নাইট বিল রেট যোগ করা নেই!" else "Night Bill Rate Missing!"
                            "TIFFIN_BILL" -> if (isBn) "টিফিন বিল রেট যোগ করা নেই!" else "Tiffin Bill Rate Missing!"
                            else -> if (isBn) "বেতন কাঠামো যোগ করা নেই!" else "Salary Setup Missing!"
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Text(
                    text = when (warningType) {
                        "NIGHT_BILL" -> if (isBn)
                            "আপনার প্রোফাইলে এখনও প্রতি রাতের নাইট বিল রেট যোগ করা হয়নি। দয়া করে প্রোফাইলে গিয়ে নাইট বিলের রেট বসিয়ে নিন।"
                        else
                            "Night bill rate is not configured in your profile. Please add your night bill rate in profile settings."
                        "TIFFIN_BILL" -> if (isBn)
                            "আপনার প্রোফাইলে এখনও প্রতি দিনের টিফিন বিল রেট যোগ করা হয়নি। দয়া করে প্রোফাইলে গিয়ে টিফিন বিল রেট বসিয়ে নিন।"
                        else
                            "Tiffin bill rate is not configured in your profile. Please add your tiffin bill rate in profile settings."
                        else -> if (isBn)
                            "আপনার প্রোফাইলে এখনও মোট বেতন বা ওটি রেট যোগ করা হয়নি। ওটি যোগ করার আগে দয়া করে প্রোফাইলে গিয়ে বেতন কাঠামো নির্ধারণ করুন, অন্যথায় ওটি টাকা হিসাব করা যাবে না।"
                        else
                            "Salary or OT rate is not configured in your profile. Please set up your salary in profile settings first."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = warningType ?: "SALARY"
                        missingFieldWarning = null
                        onNavigateToProfile(target)
                    }
                ) {
                    Text(if (isBn) "প্রোফাইলে যান" else "Go to Profile")
                }
            },
            dismissButton = {
                TextButton(onClick = { missingFieldWarning = null }) {
                    Text(if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }

    // Overwrite Confirmation Alert Dialog
    if (pendingOverwriteRecord != null) {
        val oldRec = pendingOverwriteRecord!!
        val oldShift = ShiftType.fromCode(oldRec.shiftType).getLabel(isBn)
        val newShift = ShiftType.fromCode(selectedShift).getLabel(isBn)
        val finalHours = LaborLawCalculator.parseLocalizedDouble(hoursText)

        AlertDialog(
            onDismissRequest = { pendingOverwriteRecord = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (isBn) "এই তারিখে ইতিমধ্যে ওটি তথ্য আছে!" else "Entry already exists for this date!",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isBn)
                            "আপনি $date তারিখের পূর্ববর্তী তথ্য পরিবর্তন করতে যাচ্ছেন:"
                        else
                            "You are about to overwrite existing data for $date:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (isBn)
                                    "• আগের তথ্য: ${LaborLawCalculator.formatNumber(oldRec.otHours, isBn)} ঘণ্টা ($oldShift)"
                                else
                                    "• Current: ${oldRec.otHours} hrs ($oldShift)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = if (isBn)
                                    "• নতুন তথ্য: ${LaborLawCalculator.formatNumber(finalHours, isBn)} ঘণ্টা ($newShift)"
                                else
                                    "• New: $finalHours hrs ($newShift)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = if (isBn)
                            "আপনি কি নিশ্চিতভাবে আগের তথ্যটি পরিবর্তন (ওভাররাইট) করতে চান?"
                        else
                            "Are you sure you want to overwrite the previous entry?",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingOverwriteRecord = null
                        onSave(date, finalHours, hasNightBill, hasTiffinBill, isHoliday, selectedShift, note)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isBn) "পরিবর্তন নিশ্চিত করুন" else "Confirm Overwrite")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOverwriteRecord = null }) {
                    Text(if (isBn) "বাতিল (আগেরটি রাখুন)" else "Cancel (Keep Current)")
                }
            }
        )
    }
}

@Composable
fun AddPieceRateDialog(
    lang: String,
    defaultDate: String? = null,
    onDismiss: () -> Unit,
    onSave: (itemName: String, pieceCount: Int, ratePerPiece: Double, date: String, note: String) -> Unit
) {
    var date by remember {
        mutableStateOf(defaultDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    }
    var itemName by remember { mutableStateOf("") }
    var countText by remember { mutableStateOf("") }
    var rateText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var itemNameError by remember { mutableStateOf(false) }
    var countError by remember { mutableStateOf(false) }
    var rateError by remember { mutableStateOf(false) }

    val count = LaborLawCalculator.parseLocalizedInt(countText)
    val rate = LaborLawCalculator.parseLocalizedDouble(rateText)
    val total = count * rate

    val isBn = lang == "bn"
    val popularItems = if (isBn) {
        listOf("কলার তৈরি", "পকেট জয়েন্ট", "স্লিভ এটাচ", "বটম হেম", "বাটন স্টিচ")
    } else {
        listOf("Collar Making", "Pocket Joint", "Sleeve Attach", "Bottom Hem", "Button Stitch")
    }

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
        onDismiss()
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
            .testTag("add_piece_rate_sheet"),
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
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = LanguageManager.get("add_piece_work", lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = {
                        hideKeyboard()
                        onDismiss()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = LanguageManager.get("cancel", lang)
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Professional Date Picker Field with Quick Chips
                ProfessionalDatePickerField(
                    selectedDate = date,
                    onDateSelected = { date = it },
                    isBn = isBn
                )

                // Quick item suggestions
                Text(if (isBn) "জনপ্রিয় কাজের নাম:" else "Popular Task Suggestions:", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    popularItems.take(3).forEach { item ->
                        AssistChip(
                            onClick = {
                                hideKeyboard()
                                itemName = item
                                itemNameError = false
                            },
                            label = { Text(item, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = itemName,
                    onValueChange = {
                        itemName = it
                        itemNameError = false
                    },
                    label = { Text(LanguageManager.get("item_name", lang)) },
                    modifier = Modifier.fillMaxWidth().testTag("piece_name_input"),
                    placeholder = { Text(if (isBn) "যেমন: শার্ট কলার জয়েন্ট" else "e.g. Shirt Collar Joint") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    singleLine = true,
                    isError = itemNameError,
                    supportingText = if (itemNameError) {
                        { Text(if (isBn) "কাজের নাম লিখুন" else "Enter item name") }
                    } else null
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = countText,
                        onValueChange = {
                            countText = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = false)
                            countError = false
                        },
                        label = { Text(LanguageManager.get("piece_count", lang)) },
                        placeholder = { Text("0") },
                        modifier = Modifier.weight(1f).testTag("piece_count_input"),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                        singleLine = true,
                        isError = countError,
                        supportingText = if (countError) {
                            { Text(if (isBn) "পিস সংখ্যা দিন" else "Enter count") }
                        } else null
                    )
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = {
                            rateText = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                            rateError = false
                        },
                        label = { Text(LanguageManager.get("rate_per_piece", lang)) },
                        placeholder = { Text("0") },
                        modifier = Modifier.weight(1f).testTag("piece_rate_input"),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                        singleLine = true,
                        isError = rateError,
                        supportingText = if (rateError) {
                            { Text(if (isBn) "সঠিক দর দিন" else "Enter rate") }
                        } else null
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isBn) "মোট পিস রেট আয়: ${LaborLawCalculator.formatCurrency(total, true)}" else "Total Piece Income: ${LaborLawCalculator.formatCurrency(total, false)}",
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(LanguageManager.get("note", lang)) },
                    modifier = Modifier.fillMaxWidth().testTag("piece_note_input"),
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
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(LanguageManager.get("cancel", lang))
                    }
                    Button(
                        onClick = {
                            hideKeyboard()
                            val validName = itemName.isNotBlank()
                            val validCount = count > 0
                            val validRate = rate > 0.0
                            itemNameError = !validName
                            countError = !validCount
                            rateError = !validRate
                            if (validName && validCount && validRate) {
                                onSave(itemName.trim(), count, rate, date, note)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("save_piece_button")
                    ) {
                        Text(LanguageManager.get("save", lang))
                    }
                }
            }
        }
    }
}

@Composable
fun TargetGoalDialog(
    currentTarget: Double,
    lang: String,
    hourlyRate: Double = 0.0,
    currentTotalEarnings: Double = 0.0,
    currentOtHours: Double = 0.0,
    grossSalary: Double = 0.0,
    totalDeductions: Double = 0.0,
    isBonusCancelled: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    val isBn = lang == "bn"
    var targetText by remember { mutableStateOf(LaborLawCalculator.formatEditableNumber(currentTarget)) }

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
        onDismiss()
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
            .testTag("target_goal_sheet"),
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
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = LanguageManager.get("target_hours", lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = {
                        hideKeyboard()
                        onDismiss()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = LanguageManager.get("cancel", lang)
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
                Text(
                    text = if (isBn)
                        "মাসে আপনি মোট কত ঘণ্টা ওভারটাইম করার পরিকল্পনা করছেন? লক্ষ্য নির্ধারণ করলে প্রতিদিনের হিসাব সহজ হয়।"
                    else
                        "How many hours of overtime do you plan to do this month? Setting a target helps track daily progress.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true) },
                    label = { Text(if (isBn) "লক্ষ্যমাত্রা (ঘণ্টা)" else "Target (Hours)") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { hideKeyboard() }),
                    modifier = Modifier.fillMaxWidth().testTag("target_hours_input"),
                    singleLine = true
                )
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(40, 50, 60, 70, 80).forEach { preset ->
                        FilterChip(
                            selected = targetText == preset.toString(),
                            onClick = {
                                hideKeyboard()
                                targetText = preset.toString()
                            },
                            label = { Text("${LaborLawCalculator.formatNumber(preset.toDouble(), isBn, 0)} ${if (isBn) "ঘ" else "h"}") }
                        )
                    }
                }

                // Live calculation card
                val parsedHours = LaborLawCalculator.parseLocalizedDouble(targetText)
                val estimatedEarnings = parsedHours * hourlyRate

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isBn) "সম্ভাব্য ওটি আয়" else "Est. OT Earnings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hourlyRate > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = if (isBn) "রেট: ${LaborLawCalculator.formatCurrency(hourlyRate, isBn)}/ঘণ্টা"
                                        else "Rate: ${LaborLawCalculator.formatCurrency(hourlyRate, isBn)}/hr",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (hourlyRate > 0) {
                                LaborLawCalculator.formatCurrency(estimatedEarnings, isBn)
                            } else {
                                if (isBn) "বেতন সেট করা নেই" else "Basic salary not set"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (hourlyRate > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = if (hourlyRate > 0) {
                                if (isBn) {
                                    "${LaborLawCalculator.formatNumber(parsedHours, isBn, 0)} ঘণ্টা ওটি সম্পন্ন করলে এই টাকা আয় হবে।"
                                } else {
                                    "Earning for completing ${LaborLawCalculator.formatNumber(parsedHours, isBn, 0)} OT hours."
                                }
                            } else {
                                if (isBn) {
                                    "প্রোফাইলে মূল বেতন সেট করলে ওটি আয়ের হিসাব দেখা যাবে।"
                                } else {
                                    "Set basic salary in profile to see earnings calculation."
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val projectedMonthEndTotal = com.example.ui.viewmodel.calculateProjectedTotalAtTarget(
                            totalEstimatedEarnings = currentTotalEarnings,
                            targetHours = parsedHours,
                            currentOtHours = currentOtHours,
                            hourlyOtRate = hourlyRate
                        )
                        val isNegativeProjected = projectedMonthEndTotal < 0.0
                        val formattedProjected = if (isNegativeProjected) {
                            "- ${LaborLawCalculator.formatCurrency(-projectedMonthEndTotal, isBn)}"
                        } else {
                            LaborLawCalculator.formatCurrency(projectedMonthEndTotal, isBn)
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isBn) "সব মিলিয়ে মাস শেষে সম্ভাব্য আয়" else "Est. Month-End Total Pay",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Text(
                                text = formattedProjected,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isNegativeProjected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.testTag("dialog_projected_total_value")
                            )
                        }

                        Text(
                            text = when {
                                isNegativeProjected -> if (isBn) {
                                    "সতর্কতা: বর্তমান কর্তন মোট আয়ের চেয়ে বেশি।"
                                } else {
                                    "Note: Current deductions exceed total earnings."
                                }
                                grossSalary <= 0.0 -> if (isBn) {
                                    "বেতন সেট না থাকায় কেবল ওটি, বোনাস ও কর্তনের হিসাব দেখাচ্ছে।"
                                } else {
                                    "Salary not set; showing OT, bonus & deductions only."
                                }
                                else -> if (isBn) {
                                    "বেতন + টার্গেট ওটি + বোনাস/বিল/পিস − কর্তন"
                                } else {
                                    "Salary + Target OT + Bonus/Bills/Piece − Deductions"
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isNegativeProjected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
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
                                        overflow = TextOverflow.Ellipsis
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
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
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
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(LanguageManager.get("cancel", lang))
                    }
                    Button(
                        onClick = {
                            hideKeyboard()
                            val target = LaborLawCalculator.parseLocalizedDouble(targetText)
                            onSave(target)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("save_target_button")
                    ) {
                        Text(LanguageManager.get("save", lang))
                    }
                }
            }
        }
    }
}

@Composable
fun InitialPrivacyConsentDialog(
    lang: String,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onViewFullPolicy: () -> Unit,
    titleText: String? = null,
    confirmButtonText: String? = null,
    dismissButtonText: String? = null
) {
    val isBn = lang == "bn"

    AlertDialog(
        onDismissRequest = onDecline,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = titleText ?: if (isBn) "গোপনীয়তা নীতিমালায় সম্মতি" else "Privacy Policy Consent",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (isBn) "ওভারটাইম ট্র্যাকার বিডি" else "Overtime Tracker BD",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            val annotatedText = androidx.compose.ui.text.buildAnnotatedString {
                if (isBn) {
                    append("সাইন ইন করার মাধ্যমে আপনি আমাদের ")
                    val link = androidx.compose.ui.text.LinkAnnotation.Clickable(
                        tag = "privacy_policy",
                        styles = androidx.compose.ui.text.TextLinkStyles(
                            style = androidx.compose.ui.text.SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                            )
                        ),
                        linkInteractionListener = { _ -> onViewFullPolicy() }
                    )
                    pushLink(link)
                    append("গোপনীয়তা নীতিমালা")
                    pop()
                    append(" পড়েছেন এবং তাতে সম্মতি দিচ্ছেন।")
                } else {
                    append("By signing in, you confirm that you have read and agree to our ")
                    val link = androidx.compose.ui.text.LinkAnnotation.Clickable(
                        tag = "privacy_policy",
                        styles = androidx.compose.ui.text.TextLinkStyles(
                            style = androidx.compose.ui.text.SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                            )
                        ),
                        linkInteractionListener = { _ -> onViewFullPolicy() }
                    )
                    pushLink(link)
                    append("Privacy Policy")
                    pop()
                    append(".")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = annotatedText,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAgree,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("consent_agree_button")
            ) {
                Text(
                    text = confirmButtonText ?: if (isBn) "সম্মত আছি ও প্রবেশ করুন" else "Agree & Continue",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDecline,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("consent_decline_button")
            ) {
                Text(
                    text = dismissButtonText ?: if (isBn) "বাতিল (বেরিয়ে যান)" else "Decline & Exit"
                )
            }
        }
    )
}

@Composable
fun PastMonthLockBanner(
    selectedMonth: String,
    isLocked: Boolean,
    isBn: Boolean,
    onUnlockClick: () -> Unit,
    onLockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isGracePeriod = com.example.util.MonthLockPolicy.isWithinPreviousMonthGracePeriod(selectedMonth)
    val containerColor = if (isLocked) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
    }
    val contentColor = if (isLocked) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = when {
                    isLocked -> if (isBn) "বিগত মাসের হিসাব ও স্ন্যাপশট সংরক্ষিত (লকড)" else "Past month records & snapshot locked"
                    isGracePeriod -> if (isBn) "বকেয়া এন্ট্রির জন্য ৫ তারিখ পর্যন্ত খোলা আছে" else "Open until the 5th for missed entries"
                    else -> if (isBn) "সাময়িক আনলকড — কাজ শেষে লক করুন" else "Temporarily unlocked — lock when done"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            if (isLocked) {
                OutlinedButton(
                    onClick = onUnlockClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .testTag("unlock_month_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isBn) "আনলক" else "Unlock",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onLockClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .testTag("lock_month_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isBn) "লক করুন" else "Lock",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
fun MonthLockedInfoAndUnlockDialogs(
    showLockedInfo: Boolean,
    showUnlockConfirm: Boolean,
    isBn: Boolean,
    onDismissLockedInfo: () -> Unit,
    onRequestUnlock: () -> Unit,
    onDismissUnlockConfirm: () -> Unit,
    onConfirmUnlock: () -> Unit
) {
    if (showLockedInfo) {
        AlertDialog(
            onDismissRequest = onDismissLockedInfo,
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(if (isBn) "মাসিক হিসাব লকড" else "Month Record Locked") },
            text = {
                Text(
                    if (isBn) "মাস সমাপ্ত হওয়ায় এই মাসের ওটি ও বেতনের চূড়ান্ত স্ন্যাপশট সংরক্ষিত ও লকড করা হয়েছে। বাদ পড়া ওটি বা তথ্য যোগ/সংশোধন করতে চাইলে সাময়িকভাবে আনলক করতে পারেন।"
                    else "Since the month has ended, this month's OT and salary snapshot is locked. You can temporarily unlock it if you need to add or fix a missed entry."
                )
            },
            confirmButton = {
                Button(onClick = onDismissLockedInfo) {
                    Text(if (isBn) "ঠিক আছে" else "OK")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onRequestUnlock,
                    modifier = Modifier.testTag("dialog_unlock_month_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isBn) "সাময়িকভাবে আনলক করুন" else "Unlock Temporarily")
                }
            }
        )
    }

    if (showUnlockConfirm) {
        AlertDialog(
            onDismissRequest = onDismissUnlockConfirm,
            icon = { Icon(Icons.Default.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(if (isBn) "মাসটি আনলক করবেন?" else "Unlock This Month?") },
            text = {
                Text(
                    if (isBn) "বাদ পড়া ওটি বা তথ্য যোগ/সংশোধনের জন্য মাসটি সাময়িকভাবে আনলক হবে। ওই মাসের মূল বেতন ও ওটি রেট অপরিবর্তিত থাকবে। কাজ শেষে নিরাপত্তার জন্য আবার লক করে দিন।"
                    else "This month will be temporarily unlocked for adding or editing missed entries. The month's original salary and OT rate remain preserved. Please lock it again when finished."
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmUnlock,
                    modifier = Modifier.testTag("confirm_unlock_month_button")
                ) {
                    Text(if (isBn) "আনলক করুন" else "Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUnlockConfirm) {
                    Text(if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }
}


