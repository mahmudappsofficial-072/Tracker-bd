package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.OvertimeRecord
import com.example.ui.theme.WarningOrange
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager
import com.example.util.ResponsiveUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ShiftType(
    val code: String,
    @param:StringRes val labelRes: Int,
    val color: Color
) {
    REGULAR("REGULAR", R.string.shift_regular, Color(0xFF059669)),
    DAY("DAY", R.string.shift_day, Color(0xFFD97706)),
    NIGHT("NIGHT", R.string.shift_night, Color(0xFF2563EB)),
    HOLIDAY("HOLIDAY", R.string.shift_holiday, Color(0xFF9333EA)),
    WEEKLY_OFF("WEEKLY_OFF", R.string.shift_weekly_off, Color(0xFF0D9488)),
    LEAVE("LEAVE", R.string.shift_leave, Color(0xFF0284C7)),
    ABSENT("ABSENT", R.string.shift_absent, Color(0xFFDC2626));

    @Composable
    fun getLabel(): String = stringResource(labelRes)

    fun getLabel(context: Context, langTag: String): String =
        LanguageManager.getLocalizedContext(context, langTag).getString(labelRes)

    companion object {
        fun fromCode(code: String): ShiftType = entries.firstOrNull { it.code == code } ?: REGULAR
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerBottomSheet(
    selectedSettingTag: String,
    onSelectLanguage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val systemEffectiveTag = remember { LanguageManager.getSystemEffectiveTag() }
    val systemResolvedLabel = remember(systemEffectiveTag) {
        LanguageManager.getDisplayName(LanguageManager.SYSTEM_TAG, systemEffectiveTag)
    }
    val spacing = ResponsiveUtils.rememberAppSpacing()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("language_picker_sheet"),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.sheetBodyH)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_language_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.language_picker_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // Option 0: System Default (auto-detect with English fallback)
            val isSystemSelected = selectedSettingTag == LanguageManager.SYSTEM_TAG
            Surface(
                onClick = {
                    onSelectLanguage(LanguageManager.SYSTEM_TAG)
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                color = if (isSystemSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = if (isSystemSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.minTouchTarget)
                    .testTag("language_option_system")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = if (isSystemSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.system_default_title),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.system_default_subtitle_fmt, systemResolvedLabel),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    RadioButton(
                        selected = isSystemSelected,
                        onClick = {
                            onSelectLanguage(LanguageManager.SYSTEM_TAG)
                            onDismiss()
                        }
                    )
                }
            }

            // Explicit Language Options: EN, BN, HI, TA, AR
            LanguageManager.SupportedLanguage.entries.forEach { option ->
                val isSelected = selectedSettingTag == option.tag
                Surface(
                    onClick = {
                        onSelectLanguage(option.tag)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = spacing.minTouchTarget)
                        .testTag("language_option_${option.tag}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = option.flagEmoji,
                                fontSize = 22.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = option.nativeLabel,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = option.englishLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onSelectLanguage(option.tag)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
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
    lang: String,
    modifier: Modifier = Modifier
) {
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val spacing = ResponsiveUtils.rememberAppSpacing()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val hideKeyboard = {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val resolvedTag = LanguageManager.resolveSupportedTag(lang)
    val isBnDigits = resolvedTag == "bn"
    val displayLocale = remember(resolvedTag) { LanguageManager.localeForTag(resolvedTag) }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val todayDateStr = remember { sdf.format(Date()) }
    val yesterdayDateStr = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        sdf.format(cal.time)
    }

    val todaySuffix = stringResource(R.string.suffix_today)
    val yesterdaySuffix = stringResource(R.string.suffix_yesterday)

    val formattedDisplayDate = remember(selectedDate, resolvedTag, todaySuffix, yesterdaySuffix) {
        try {
            val parsed = sdf.parse(selectedDate)
            if (parsed != null) {
                val dayName = SimpleDateFormat("EEEE", displayLocale).format(parsed)
                val formatted = SimpleDateFormat("dd MMMM yyyy", displayLocale).format(parsed)
                val display = if (isBnDigits) {
                    LaborLawCalculator.toBengaliDigits(formatted)
                } else {
                    LaborLawCalculator.toEnglishDigits(formatted)
                }
                val suffix = when (selectedDate) {
                    todayDateStr -> todaySuffix
                    yesterdayDateStr -> yesterdaySuffix
                    else -> ""
                }
                "$display ($dayName)$suffix"
            } else selectedDate
        } catch (_: Exception) {
            selectedDate
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = selectedDate == todayDateStr,
                onClick = {
                    hideKeyboard()
                    onDateSelected(todayDateStr)
                },
                label = { Text(stringResource(R.string.date_chip_today), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
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
                label = { Text(stringResource(R.string.date_chip_yesterday), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
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
                label = { Text(stringResource(R.string.date_chip_calendar), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("date_chip_calendar")
            )
        }

        // Tappable Date Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = spacing.minTouchTarget)
                .clickable {
                    hideKeyboard()
                    showDatePickerDialog = true
                }
                .testTag("tappable_date_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.cardPadding, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = stringResource(R.string.cd_select_date),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.selected_date_tap_label),
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
                        text = stringResource(R.string.change),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
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
                    Text(stringResource(R.string.ok), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        text = stringResource(R.string.select_date_dialog_title),
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp),
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
    val isBnDigits = LanguageManager.isBengaliDigits(lang)
    val spacing = ResponsiveUtils.rememberAppSpacing()
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
                        .padding(horizontal = spacing.sheetHeaderH, vertical = spacing.sheetHeaderV),
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
                            text = stringResource(
                                if (initialRecord != null && initialRecord.id != 0L) R.string.edit_ot_entry_title
                                else R.string.quick_ot_entry_title
                            ),
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
                    selectedDate = date,
                    onDateSelected = { date = it },
                    lang = lang
                )

                Text(
                    text = stringResource(R.string.shift_type_label),
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
                    ShiftType.entries.forEach { shift ->
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
                                    ShiftType.DAY -> isHoliday = false
                                    ShiftType.REGULAR -> isHoliday = false
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
                                    text = shift.getLabel(),
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(
                                when (selectedShift) {
                                    ShiftType.ABSENT.code -> R.string.shift_info_absent
                                    ShiftType.LEAVE.code -> R.string.shift_info_leave
                                    else -> R.string.shift_info_weekly_off
                                }
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = when (selectedShift) {
                                ShiftType.ABSENT.code -> MaterialTheme.colorScheme.onErrorContainer
                                ShiftType.LEAVE.code -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(spacing.subCardPadding)
                        )
                    }

                    if (selectedShift == ShiftType.LEAVE.code) {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text(stringResource(R.string.leave_reason_label)) },
                            placeholder = { Text(stringResource(R.string.leave_reason_placeholder)) },
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
                    Text(
                        text = stringResource(R.string.quick_ot_hours_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val hrsShort = stringResource(R.string.hours_short)
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
                                        text = "${LaborLawCalculator.formatNumber(h, isBnDigits, 0)} $hrsShort",
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
                                        text = "${LaborLawCalculator.formatNumber(h, isBnDigits, 0)} $hrsShort",
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

                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { hoursText = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true) },
                        label = { Text(stringResource(R.string.hours_input_label)) },
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
                                text = stringResource(
                                    R.string.est_ot_amount_fmt,
                                    LaborLawCalculator.formatCurrency(calculatedAmount, isBnDigits)
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = spacing.minTouchTarget)
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
                                Text(stringResource(R.string.night_bill_check), style = MaterialTheme.typography.bodyMedium)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = spacing.minTouchTarget)
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
                                Text(stringResource(R.string.tiffin_bill_check), style = MaterialTheme.typography.bodyMedium)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = spacing.minTouchTarget)
                                    .clickable {
                                        hideKeyboard()
                                        isHoliday = !isHoliday
                                    }
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
                                Text(stringResource(R.string.holiday_ot_check), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.note_label)) },
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
                        .padding(horizontal = spacing.sheetFooterH, vertical = spacing.sheetFooterV),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            hideKeyboard()
                            val finalHours = LaborLawCalculator.parseLocalizedDouble(hoursText)
                            if (finalHours > 0.0 && hourlyRate <= 0.0 && (profile?.basicSalary ?: 0.0) <= 0.0) {
                                missingFieldWarning = "SALARY"
                                return@Button
                            }
                            if (hasNightBill && (profile?.nightBillRate ?: 0.0) <= 0.0) {
                                missingFieldWarning = "NIGHT_BILL"
                                return@Button
                            }
                            if (hasTiffinBill && (profile?.tiffinBillRate ?: 0.0) <= 0.0) {
                                missingFieldWarning = "TIFFIN_BILL"
                                return@Button
                            }

                            if (finalHours >= 0.0 && date.isNotBlank()) {
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
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }

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
                        text = stringResource(
                            when (warningType) {
                                "NIGHT_BILL" -> R.string.missing_night_bill_title
                                "TIFFIN_BILL" -> R.string.missing_tiffin_bill_title
                                else -> R.string.missing_salary_title
                            }
                        ),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Text(
                    text = stringResource(
                        when (warningType) {
                            "NIGHT_BILL" -> R.string.missing_night_bill_msg
                            "TIFFIN_BILL" -> R.string.missing_tiffin_bill_msg
                            else -> R.string.missing_salary_msg
                        }
                    ),
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
                    Text(stringResource(R.string.go_to_profile))
                }
            },
            dismissButton = {
                TextButton(onClick = { missingFieldWarning = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (pendingOverwriteRecord != null) {
        val oldRec = pendingOverwriteRecord!!
        val oldShift = ShiftType.fromCode(oldRec.shiftType).getLabel()
        val newShift = ShiftType.fromCode(selectedShift).getLabel()
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
                    text = stringResource(R.string.overwrite_title),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.overwrite_msg_fmt, date),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(
                                    R.string.overwrite_current_fmt,
                                    LaborLawCalculator.formatNumber(oldRec.otHours, isBnDigits),
                                    oldShift
                                ),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = stringResource(
                                    R.string.overwrite_new_fmt,
                                    LaborLawCalculator.formatNumber(finalHours, isBnDigits),
                                    newShift
                                ),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.overwrite_confirm_prompt),
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
                    Text(stringResource(R.string.overwrite_confirm_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOverwriteRecord = null }) {
                    Text(stringResource(R.string.overwrite_keep_btn))
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

    val isBnDigits = LanguageManager.isBengaliDigits(lang)
    val spacing = ResponsiveUtils.rememberAppSpacing()
    val popularItems = listOf(
        stringResource(R.string.piece_suggestion_collar),
        stringResource(R.string.piece_suggestion_pocket),
        stringResource(R.string.piece_suggestion_sleeve)
    )

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
                        .padding(horizontal = spacing.sheetHeaderH, vertical = spacing.sheetHeaderV),
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
                            text = stringResource(R.string.add_piece_work_title),
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
                    selectedDate = date,
                    onDateSelected = { date = it },
                    lang = lang
                )

                Text(stringResource(R.string.popular_tasks_label), style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    popularItems.forEach { item ->
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
                    label = { Text(stringResource(R.string.item_name_label)) },
                    modifier = Modifier.fillMaxWidth().testTag("piece_name_input"),
                    placeholder = { Text(stringResource(R.string.item_name_placeholder)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    singleLine = true,
                    isError = itemNameError,
                    supportingText = if (itemNameError) {
                        { Text(stringResource(R.string.item_name_error)) }
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
                        label = { Text(stringResource(R.string.piece_count_label)) },
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
                            { Text(stringResource(R.string.piece_count_error)) }
                        } else null
                    )
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = {
                            rateText = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                            rateError = false
                        },
                        label = { Text(stringResource(R.string.rate_per_piece_label)) },
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
                            { Text(stringResource(R.string.rate_per_piece_error)) }
                        } else null
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.total_piece_income_fmt,
                            LaborLawCalculator.formatCurrency(total, isBnDigits)
                        ),
                        modifier = Modifier.padding(spacing.subCardPadding),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.note_label)) },
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
                        .padding(horizontal = spacing.sheetFooterH, vertical = spacing.sheetFooterV),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                        Text(stringResource(R.string.cancel))
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
                        Text(stringResource(R.string.save))
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
    val isBnDigits = LanguageManager.isBengaliDigits(lang)
    val spacing = ResponsiveUtils.rememberAppSpacing()
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
                        .padding(horizontal = spacing.sheetHeaderH, vertical = spacing.sheetHeaderV),
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
                            text = stringResource(R.string.set_target_hours_title),
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
                Text(
                    text = stringResource(R.string.target_goal_intro),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true) },
                    label = { Text(stringResource(R.string.target_hours_input_label)) },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { hideKeyboard() }),
                    modifier = Modifier.fillMaxWidth().testTag("target_hours_input"),
                    singleLine = true
                )
                val hrsShort = stringResource(R.string.hours_short)
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
                            label = { Text("${LaborLawCalculator.formatNumber(preset.toDouble(), isBnDigits, 0)} $hrsShort") }
                        )
                    }
                }

                val parsedHours = LaborLawCalculator.parseLocalizedDouble(targetText)
                val estimatedEarnings = parsedHours * hourlyRate

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.subCardPadding),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.est_ot_earnings_label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hourlyRate > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.rate_per_hour_fmt,
                                            LaborLawCalculator.formatCurrency(hourlyRate, isBnDigits)
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (hourlyRate > 0) {
                                LaborLawCalculator.formatCurrency(estimatedEarnings, isBnDigits)
                            } else {
                                stringResource(R.string.salary_not_set_short)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (hourlyRate > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = if (hourlyRate > 0) {
                                stringResource(
                                    R.string.target_hours_earning_fmt,
                                    LaborLawCalculator.formatNumber(parsedHours, isBnDigits, 0)
                                )
                            } else {
                                stringResource(R.string.target_salary_unset_hint)
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
                            "- ${LaborLawCalculator.formatCurrency(-projectedMonthEndTotal, isBnDigits)}"
                        } else {
                            LaborLawCalculator.formatCurrency(projectedMonthEndTotal, isBnDigits)
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.est_month_end_total_pay),
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
                            text = stringResource(
                                when {
                                    isNegativeProjected -> R.string.target_negative_warning
                                    grossSalary <= 0.0 -> R.string.target_salary_unset_short_hint
                                    else -> R.string.target_formula_summary
                                }
                            ),
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
                                        text = stringResource(
                                            R.string.deductions_summary_fmt,
                                            LaborLawCalculator.formatCurrency(totalDeductions, isBnDigits)
                                        ),
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
                                            text = stringResource(R.string.bonus_lost_badge),
                                            fontSize = 10.sp,
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.sheetFooterH, vertical = spacing.sheetFooterV),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                        Text(stringResource(R.string.cancel))
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
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

@Composable
fun InitialPrivacyConsentDialog(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onViewFullPolicy: () -> Unit,
    titleText: String? = null,
    confirmButtonText: String? = null,
    dismissButtonText: String? = null
) {
    val prefix = stringResource(R.string.privacy_consent_prefix)
    val linkText = stringResource(R.string.privacy_consent_link_text)
    val suffix = stringResource(R.string.privacy_consent_suffix)
    val primaryColor = MaterialTheme.colorScheme.primary
    val spacing = ResponsiveUtils.rememberAppSpacing()

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
                        text = titleText ?: stringResource(R.string.privacy_consent_title),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.app_display_name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            val annotatedText = remember(prefix, linkText, suffix, primaryColor) {
                buildAnnotatedString {
                    append(prefix)
                    val link = LinkAnnotation.Clickable(
                        tag = "privacy_policy",
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = primaryColor,
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline
                            )
                        ),
                        linkInteractionListener = { _ -> onViewFullPolicy() }
                    )
                    pushLink(link)
                    append(linkText)
                    pop()
                    append(suffix)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Box(modifier = Modifier.padding(spacing.cardPadding)) {
                        Text(
                            text = annotatedText,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
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
                    text = confirmButtonText ?: stringResource(R.string.privacy_consent_confirm_default),
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
                    text = dismissButtonText ?: stringResource(R.string.privacy_consent_decline_default)
                )
            }
        }
    )
}

@Composable
fun PastMonthLockBanner(
    selectedMonth: String,
    isLocked: Boolean,
    onUnlockClick: () -> Unit,
    onLockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isGracePeriod = com.example.util.MonthLockPolicy.isWithinPreviousMonthGracePeriod(selectedMonth)
    val spacing = ResponsiveUtils.rememberAppSpacing()
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
                .padding(horizontal = spacing.listItemPadding, vertical = 6.dp),
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
                text = stringResource(
                    when {
                        isLocked -> R.string.past_month_locked_banner
                        isGracePeriod -> R.string.past_month_grace_banner
                        else -> R.string.past_month_temp_unlocked_banner
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            if (isLocked) {
                OutlinedButton(
                    onClick = onUnlockClick,
                    contentPadding = spacing.compactButtonContentPadding,
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
                        text = stringResource(R.string.unlock),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onLockClick,
                    contentPadding = spacing.compactButtonContentPadding,
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
                        text = stringResource(R.string.lock),
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
    onDismissLockedInfo: () -> Unit,
    onRequestUnlock: () -> Unit,
    onDismissUnlockConfirm: () -> Unit,
    onConfirmUnlock: () -> Unit
) {
    if (showLockedInfo) {
        AlertDialog(
            onDismissRequest = onDismissLockedInfo,
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.month_locked_dialog_title)) },
            text = { Text(stringResource(R.string.month_locked_dialog_msg)) },
            confirmButton = {
                Button(onClick = onDismissLockedInfo) {
                    Text(stringResource(R.string.ok))
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
                    Text(stringResource(R.string.unlock_temporarily_btn))
                }
            }
        )
    }

    if (showUnlockConfirm) {
        AlertDialog(
            onDismissRequest = onDismissUnlockConfirm,
            icon = { Icon(Icons.Default.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.unlock_confirm_dialog_title)) },
            text = { Text(stringResource(R.string.unlock_confirm_dialog_msg)) },
            confirmButton = {
                Button(
                    onClick = onConfirmUnlock,
                    modifier = Modifier.testTag("confirm_unlock_month_button")
                ) {
                    Text(stringResource(R.string.unlock))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUnlockConfirm) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
