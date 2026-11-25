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
import com.example.ui.components.AutoFitSingleLineText
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
        shape = RoundedCornerShape(topStart = spacing.heroRadius, topEnd = spacing.heroRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.sheetBodyH)
                .padding(bottom = spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(spacing.avatarSize)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(spacing.iconLg)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    AutoFitSingleLineText(
                        text = stringResource(R.string.app_language_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxFontSize = spacing.adaptiveSp(17.5f, 15.5f, 14f),
                        minFontSize = 11.sp
                    )
                    Text(
                        text = stringResource(R.string.language_picker_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = spacing.rowGapSm))

            // Option 0: System Default (auto-detect with English fallback)
            val isSystemSelected = selectedSettingTag == LanguageManager.SYSTEM_TAG
            Surface(
                onClick = {
                    onSelectLanguage(LanguageManager.SYSTEM_TAG)
                    onDismiss()
                },
                shape = RoundedCornerShape(spacing.cardRadius),
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
                        .padding(horizontal = spacing.cardPadding, vertical = spacing.rowGapMd),
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
                            modifier = Modifier.size(spacing.iconLg)
                        )
                        Spacer(modifier = Modifier.width(spacing.rowGapMd))
                        Column {
                            AutoFitSingleLineText(
                                text = stringResource(R.string.system_default_title),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                maxFontSize = spacing.adaptiveSp(16f, 14.5f, 13.5f),
                                minFontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            AutoFitSingleLineText(
                                text = stringResource(R.string.system_default_subtitle_fmt, systemResolvedLabel),
                                style = MaterialTheme.typography.bodySmall,
                                maxFontSize = spacing.adaptiveSp(13.5f, 12f, 11f),
                                minFontSize = 9.sp,
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
                    shape = RoundedCornerShape(spacing.cardRadius),
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
                            .padding(horizontal = spacing.cardPadding, vertical = spacing.rowGapMd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = option.flagEmoji,
                                fontSize = 19.sp
                            )
                            Spacer(modifier = Modifier.width(spacing.rowGapMd))
                            Column {
                                AutoFitSingleLineText(
                                    text = option.nativeLabel,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxFontSize = spacing.adaptiveSp(16f, 14.5f, 13.5f),
                                    minFontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                AutoFitSingleLineText(
                                    text = option.englishLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxFontSize = spacing.adaptiveSp(13.5f, 12f, 11f),
                                    minFontSize = 9.sp,
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

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.rowGapSm)) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm),
            verticalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
        ) {
            FilterChip(
                selected = selectedDate == todayDateStr,
                onClick = {
                    hideKeyboard()
                    onDateSelected(todayDateStr)
                },
                label = { AutoFitSingleLineText(stringResource(R.string.date_chip_today), maxFontSize = 11.5.sp, minFontSize = 8.5.sp, fontWeight = FontWeight.SemiBold) },
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
                label = { AutoFitSingleLineText(stringResource(R.string.date_chip_yesterday), maxFontSize = 11.5.sp, minFontSize = 8.5.sp, fontWeight = FontWeight.SemiBold) },
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
                label = { AutoFitSingleLineText(stringResource(R.string.date_chip_calendar), maxFontSize = 11.5.sp, minFontSize = 8.5.sp, fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("date_chip_calendar")
            )
        }

        // Tappable Date Card
        Card(
            shape = RoundedCornerShape(spacing.cardRadius),
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
                    .padding(horizontal = spacing.cardPadding, vertical = spacing.listItemPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = spacing.rowGapSm)) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = stringResource(R.string.cd_select_date),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(spacing.iconMd)
                    )
                    Spacer(modifier = Modifier.width(spacing.rowGapMd))
                    Column {
                        AutoFitSingleLineText(
                            text = stringResource(R.string.selected_date_tap_label),
                            style = MaterialTheme.typography.labelSmall,
                            maxFontSize = 11.sp,
                            minFontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AutoFitSingleLineText(
                            text = formattedDisplayDate,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxFontSize = 13.5.sp,
                            minFontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(spacing.badgeRadius),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    AutoFitSingleLineText(
                        text = stringResource(R.string.change),
                        maxFontSize = 11.sp,
                        minFontSize = 8.sp,
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
                            modifier = Modifier.size(spacing.iconLg)
                        )
                        Spacer(modifier = Modifier.width(spacing.rowGapMd))
                        AutoFitSingleLineText(
                            text = stringResource(
                                if (initialRecord != null && initialRecord.id != 0L) R.string.edit_ot_entry_title
                                else R.string.quick_ot_entry_title
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxFontSize = spacing.adaptiveSp(18f, 16f, 14.5f),
                            minFontSize = 11.sp
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
                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm),
                    verticalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
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
                                AutoFitSingleLineText(
                                    text = shift.getLabel(),
                                    maxFontSize = 11.sp,
                                    minFontSize = 8.sp
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
                        shape = RoundedCornerShape(spacing.cardRadius),
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
                        horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
                    ) {
                        presetHours.take(4).forEach { h ->
                            AssistChip(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    hideKeyboard()
                                    hoursText = h.toInt().toString()
                                },
                                label = {
                                    AutoFitSingleLineText(
                                        text = "${LaborLawCalculator.formatNumber(h, isBnDigits, 0)} $hrsShort",
                                        maxFontSize = 11.sp,
                                        minFontSize = 7.5.sp,
                                        fontWeight = FontWeight.SemiBold,
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
                        horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
                    ) {
                        presetHours.drop(4).forEach { h ->
                            AssistChip(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    hideKeyboard()
                                    hoursText = h.toInt().toString()
                                },
                                label = {
                                    AutoFitSingleLineText(
                                        text = "${LaborLawCalculator.formatNumber(h, isBnDigits, 0)} $hrsShort",
                                        maxFontSize = 11.sp,
                                        minFontSize = 7.5.sp,
                                        fontWeight = FontWeight.SemiBold,
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
                        shape = RoundedCornerShape(spacing.cardRadius)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = spacing.subCardPadding, vertical = spacing.rowGapSm)) {
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
                                Spacer(modifier = Modifier.width(spacing.rowGapSm))
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
                                Spacer(modifier = Modifier.width(spacing.rowGapSm))
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
                                Spacer(modifier = Modifier.width(spacing.rowGapSm))
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
                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            hideKeyboard()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(spacing.cardRadius),
                        contentPadding = spacing.compactButtonContentPadding,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = spacing.minTouchTarget)
                    ) {
                        AutoFitSingleLineText(
                            stringResource(R.string.cancel),
                            maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                            minFontSize = 10.sp
                        )
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
                        shape = RoundedCornerShape(spacing.cardRadius),
                        contentPadding = spacing.compactButtonContentPadding,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = spacing.minTouchTarget)
                            .testTag("save_ot_button")
                    ) {
                        AutoFitSingleLineText(
                            stringResource(R.string.save),
                            maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                            minFontSize = 10.sp
                        )
                    }
                }
            }
        }
    }

    if (missingFieldWarning != null) {
        val warningType = missingFieldWarning
        AlertDialog(
            onDismissRequest = { missingFieldWarning = null },
            shape = RoundedCornerShape(spacing.heroRadius),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(spacing.iconLg)
                    )
                    Spacer(modifier = Modifier.width(spacing.rowGapMd))
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
                    },
                    shape = RoundedCornerShape(spacing.subCardRadius),
                    contentPadding = spacing.compactButtonContentPadding
                ) {
                    AutoFitSingleLineText(
                        text = stringResource(R.string.go_to_profile),
                        maxFontSize = spacing.adaptiveSp(14.5f, 13.5f, 12.5f),
                        minFontSize = 9.5.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { missingFieldWarning = null },
                    contentPadding = spacing.compactButtonContentPadding
                ) {
                    AutoFitSingleLineText(
                        text = stringResource(R.string.cancel),
                        maxFontSize = spacing.adaptiveSp(14.5f, 13.5f, 12.5f),
                        minFontSize = 9.5.sp
                    )
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
            shape = RoundedCornerShape(spacing.heroRadius),
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(spacing.avatarSize)
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
                Column(verticalArrangement = Arrangement.spacedBy(spacing.gridGap)) {
                    Text(
                        text = stringResource(R.string.overwrite_msg_fmt, date),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Surface(
                        shape = RoundedCornerShape(spacing.subCardRadius),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(spacing.subCardPadding),
                            verticalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.overwrite_current_fmt,
                                    LaborLawCalculator.formatNumber(oldRec.otHours, isBnDigits),
                                    oldShift
                                ),
                                fontSize = spacing.adaptiveSp(13f, 12f, 11f),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = stringResource(
                                    R.string.overwrite_new_fmt,
                                    LaborLawCalculator.formatNumber(finalHours, isBnDigits),
                                    newShift
                                ),
                                fontSize = spacing.adaptiveSp(13f, 12f, 11f),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.overwrite_confirm_prompt),
                        fontSize = spacing.adaptiveSp(13f, 12f, 11f),
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
                    shape = RoundedCornerShape(spacing.subCardRadius),
                    contentPadding = spacing.compactButtonContentPadding,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    AutoFitSingleLineText(
                        text = stringResource(R.string.overwrite_confirm_btn),
                        maxFontSize = spacing.adaptiveSp(14.5f, 13.5f, 12.5f),
                        minFontSize = 9.5.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingOverwriteRecord = null },
                    contentPadding = spacing.compactButtonContentPadding
                ) {
                    AutoFitSingleLineText(
                        text = stringResource(R.string.overwrite_keep_btn),
                        maxFontSize = spacing.adaptiveSp(14.5f, 13.5f, 12.5f),
                        minFontSize = 9.5.sp
                    )
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
                            modifier = Modifier.size(spacing.iconLg)
                        )
                        Spacer(modifier = Modifier.width(spacing.rowGapMd))
                        AutoFitSingleLineText(
                            text = stringResource(R.string.add_piece_work_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxFontSize = spacing.adaptiveSp(18f, 16f, 14.5f),
                            minFontSize = 11.sp
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
                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
                ) {
                    popularItems.forEach { item ->
                        AssistChip(
                            onClick = {
                                hideKeyboard()
                                itemName = item
                                itemNameError = false
                            },
                            label = { AutoFitSingleLineText(item, maxFontSize = 11.sp, minFontSize = 8.sp) }
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
                    horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)
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
                    shape = RoundedCornerShape(spacing.cardRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AutoFitSingleLineText(
                        text = stringResource(
                            R.string.total_piece_income_fmt,
                            LaborLawCalculator.formatCurrency(total, isBnDigits)
                        ),
                        modifier = Modifier.padding(spacing.subCardPadding),
                        fontWeight = FontWeight.Bold,
                        maxFontSize = spacing.adaptiveSp(16f, 14.5f, 13.5f),
                        minFontSize = 10.sp,
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
                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            hideKeyboard()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(spacing.cardRadius),
                        contentPadding = spacing.compactButtonContentPadding,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = spacing.minTouchTarget)
                    ) {
                        AutoFitSingleLineText(
                            stringResource(R.string.cancel),
                            maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                            minFontSize = 10.sp
                        )
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
                        shape = RoundedCornerShape(spacing.cardRadius),
                        contentPadding = spacing.compactButtonContentPadding,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = spacing.minTouchTarget)
                            .testTag("save_piece_button")
                    ) {
                        AutoFitSingleLineText(
                            stringResource(R.string.save),
                            maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                            minFontSize = 10.sp
                        )
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
                            modifier = Modifier.size(spacing.iconLg)
                        )
                        Spacer(modifier = Modifier.width(spacing.rowGapMd))
                        AutoFitSingleLineText(
                            text = stringResource(R.string.set_target_hours_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxFontSize = spacing.adaptiveSp(18f, 16f, 14.5f),
                            minFontSize = 11.sp
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
                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm),
                    verticalArrangement = Arrangement.spacedBy(spacing.rowGapSm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(40, 50, 60, 70, 80).forEach { preset ->
                        FilterChip(
                            selected = targetText == preset.toString(),
                            onClick = {
                                hideKeyboard()
                                targetText = preset.toString()
                            },
                            label = {
                                AutoFitSingleLineText(
                                    text = "${LaborLawCalculator.formatNumber(preset.toDouble(), isBnDigits, 0)} $hrsShort",
                                    maxFontSize = 11.5.sp,
                                    minFontSize = 8.sp
                                )
                            }
                        )
                    }
                }

                val parsedHours = LaborLawCalculator.parseLocalizedDouble(targetText)
                val estimatedEarnings = parsedHours * hourlyRate

                Surface(
                    shape = RoundedCornerShape(spacing.cardRadius),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
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
                            AutoFitSingleLineText(
                                text = stringResource(R.string.est_ot_earnings_label),
                                style = MaterialTheme.typography.bodySmall,
                                maxFontSize = 12.sp,
                                minFontSize = 8.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).padding(end = spacing.rowGapSm)
                            )
                            if (hourlyRate > 0) {
                                Surface(
                                    shape = RoundedCornerShape(spacing.badgeRadius),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    AutoFitSingleLineText(
                                        text = stringResource(
                                            R.string.rate_per_hour_fmt,
                                            LaborLawCalculator.formatCurrency(hourlyRate, isBnDigits)
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxFontSize = 10.5.sp,
                                        minFontSize = 8.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                                    )
                                }
                            }
                        }

                        AutoFitSingleLineText(
                            text = if (hourlyRate > 0) {
                                LaborLawCalculator.formatCurrency(estimatedEarnings, isBnDigits)
                            } else {
                                stringResource(R.string.salary_not_set_short)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxFontSize = spacing.adaptiveSp(18f, 16f, 14.5f),
                            minFontSize = 11.sp,
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
                            modifier = Modifier.padding(vertical = spacing.rowGapSm)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AutoFitSingleLineText(
                                text = stringResource(R.string.est_month_end_total_pay),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxFontSize = 12.5.sp,
                                minFontSize = 8.5.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = spacing.rowGapSm)
                            )
                            AutoFitSingleLineText(
                                text = formattedProjected,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxFontSize = spacing.adaptiveSp(18f, 16f, 14.5f),
                                minFontSize = 10.5.sp,
                                color = if (isNegativeProjected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
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
                                horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (totalDeductions > 0.0) {
                                    AutoFitSingleLineText(
                                        text = stringResource(
                                            R.string.deductions_summary_fmt,
                                            LaborLawCalculator.formatCurrency(totalDeductions, isBnDigits)
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxFontSize = 11.sp,
                                        minFontSize = 8.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                                if (isBonusCancelled) {
                                    Surface(
                                        shape = RoundedCornerShape(spacing.badgeRadius),
                                        color = WarningOrange.copy(alpha = 0.14f)
                                    ) {
                                        AutoFitSingleLineText(
                                            text = stringResource(R.string.bonus_lost_badge),
                                            maxFontSize = 10.sp,
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
                            onDismiss()
                        },
                        shape = RoundedCornerShape(spacing.cardRadius),
                        contentPadding = spacing.compactButtonContentPadding,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = spacing.minTouchTarget)
                    ) {
                        AutoFitSingleLineText(
                            stringResource(R.string.cancel),
                            maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                            minFontSize = 10.sp
                        )
                    }
                    Button(
                        onClick = {
                            hideKeyboard()
                            val target = LaborLawCalculator.parseLocalizedDouble(targetText)
                            onSave(target)
                        },
                        shape = RoundedCornerShape(spacing.cardRadius),
                        contentPadding = spacing.compactButtonContentPadding,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = spacing.minTouchTarget)
                            .testTag("save_target_button")
                    ) {
                        AutoFitSingleLineText(
                            stringResource(R.string.save),
                            maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                            minFontSize = 10.sp
                        )
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
        shape = RoundedCornerShape(spacing.heroRadius),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(spacing.avatarSize)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(spacing.iconLg)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(spacing.rowGapMd))
                Column(modifier = Modifier.weight(1f)) {
                    AutoFitSingleLineText(
                        text = titleText ?: stringResource(R.string.privacy_consent_title),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxFontSize = spacing.adaptiveSp(17.5f, 15.5f, 14f),
                        minFontSize = 11.sp
                    )
                    AutoFitSingleLineText(
                        text = stringResource(R.string.app_display_name),
                        style = MaterialTheme.typography.bodySmall,
                        maxFontSize = 12.sp,
                        minFontSize = 9.sp,
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
                verticalArrangement = Arrangement.spacedBy(spacing.dialogInnerGap)
            ) {
                Surface(
                    shape = RoundedCornerShape(spacing.cardRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Box(modifier = Modifier.padding(spacing.cardPadding)) {
                        Text(
                            text = annotatedText,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAgree,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = spacing.compactButtonContentPadding,
                modifier = Modifier.testTag("consent_agree_button")
            ) {
                AutoFitSingleLineText(
                    text = confirmButtonText ?: stringResource(R.string.privacy_consent_confirm_default),
                    fontWeight = FontWeight.Bold,
                    maxFontSize = spacing.adaptiveSp(14.5f, 13.5f, 12.5f),
                    minFontSize = 9.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDecline,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                contentPadding = spacing.compactButtonContentPadding,
                modifier = Modifier.testTag("consent_decline_button")
            ) {
                AutoFitSingleLineText(
                    text = dismissButtonText ?: stringResource(R.string.privacy_consent_decline_default),
                    maxFontSize = spacing.adaptiveSp(14.5f, 13.5f, 12.5f),
                    minFontSize = 9.sp
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
        shape = RoundedCornerShape(spacing.cardRadius),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.listItemPadding, vertical = spacing.rowGapMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(spacing.iconMd)
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
                        .heightIn(min = spacing.compactButtonHeight)
                        .testTag("unlock_month_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(spacing.iconSm)
                    )
                    Spacer(modifier = Modifier.width(spacing.rowGapSm))
                    AutoFitSingleLineText(
                        text = stringResource(R.string.unlock),
                        maxFontSize = 11.sp,
                        minFontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onLockClick,
                    contentPadding = spacing.compactButtonContentPadding,
                    modifier = Modifier
                        .heightIn(min = spacing.compactButtonHeight)
                        .testTag("lock_month_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(spacing.iconSm)
                    )
                    Spacer(modifier = Modifier.width(spacing.rowGapSm))
                    AutoFitSingleLineText(
                        text = stringResource(R.string.lock),
                        maxFontSize = 11.sp,
                        minFontSize = 8.sp,
                        fontWeight = FontWeight.Bold
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
    val spacing = ResponsiveUtils.rememberAppSpacing()
    if (showLockedInfo) {
        AlertDialog(
            onDismissRequest = onDismissLockedInfo,
            shape = RoundedCornerShape(spacing.heroRadius),
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.month_locked_dialog_title)) },
            text = { Text(stringResource(R.string.month_locked_dialog_msg)) },
            confirmButton = {
                Button(onClick = onDismissLockedInfo, contentPadding = spacing.compactButtonContentPadding) {
                    AutoFitSingleLineText(
                        stringResource(R.string.ok),
                        maxFontSize = spacing.adaptiveSp(14.5f, 13.5f, 12.5f),
                        minFontSize = 9.5.sp
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onRequestUnlock,
                    contentPadding = spacing.compactButtonContentPadding,
                    modifier = Modifier.testTag("dialog_unlock_month_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(spacing.iconMd)
                    )
                    Spacer(modifier = Modifier.width(spacing.rowGapSm))
                    AutoFitSingleLineText(
                        stringResource(R.string.unlock_temporarily_btn),
                        maxFontSize = spacing.adaptiveSp(14f, 13f, 12f),
                        minFontSize = 9.sp
                    )
                }
            }
        )
    }

    if (showUnlockConfirm) {
        AlertDialog(
            onDismissRequest = onDismissUnlockConfirm,
            shape = RoundedCornerShape(spacing.heroRadius),
            icon = { Icon(Icons.Default.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.unlock_confirm_dialog_title)) },
            text = { Text(stringResource(R.string.unlock_confirm_dialog_msg)) },
            confirmButton = {
                Button(
                    onClick = onConfirmUnlock,
                    contentPadding = spacing.compactButtonContentPadding,
                    modifier = Modifier.testTag("confirm_unlock_month_button")
                ) {
                    AutoFitSingleLineText(
                        stringResource(R.string.unlock),
                        maxFontSize = spacing.adaptiveSp(14.5f, 13.5f, 12.5f),
                        minFontSize = 9.5.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUnlockConfirm, contentPadding = spacing.compactButtonContentPadding) {
                    AutoFitSingleLineText(
                        stringResource(R.string.cancel),
                        maxFontSize = spacing.adaptiveSp(14.5f, 13.5f, 12.5f),
                        minFontSize = 9.5.sp
                    )
                }
            }
        )
    }
}
