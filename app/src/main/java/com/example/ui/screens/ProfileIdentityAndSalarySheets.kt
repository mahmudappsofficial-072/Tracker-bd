package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.UserProfile
import com.example.ui.components.AutoFitSingleLineText
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager
import com.example.util.ResponsiveUtils
import com.example.util.SalaryOnboardingPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal enum class SalaryFieldHelpTopic(
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    @StringRes val exampleRes: Int,
    val icon: ImageVector,
    val buttonTestTag: String
) {
    GROSS_SALARY(
        titleRes = R.string.help_gross_salary_title,
        descRes = R.string.help_gross_salary_desc,
        exampleRes = R.string.help_gross_salary_example,
        icon = Icons.Default.Payments,
        buttonTestTag = "gross_salary_help_btn"
    ),
    CUSTOM_BASIC_SALARY(
        titleRes = R.string.help_custom_salary_title,
        descRes = R.string.help_custom_salary_desc,
        exampleRes = R.string.help_custom_salary_example,
        icon = Icons.Default.Payments,
        buttonTestTag = "basic_salary_help_btn"
    ),
    CUSTOM_OT_RATE(
        titleRes = R.string.help_custom_ot_rate_title,
        descRes = R.string.help_custom_ot_rate_desc,
        exampleRes = R.string.help_custom_ot_rate_example,
        icon = Icons.Default.Schedule,
        buttonTestTag = "custom_ot_rate_help_btn"
    ),
    NIGHT_BILL_RATE(
        titleRes = R.string.help_night_bill_title,
        descRes = R.string.help_night_bill_desc,
        exampleRes = R.string.help_night_bill_example,
        icon = Icons.Default.NightsStay,
        buttonTestTag = "night_rate_help_btn"
    ),
    TIFFIN_BILL_RATE(
        titleRes = R.string.help_tiffin_bill_title,
        descRes = R.string.help_tiffin_bill_desc,
        exampleRes = R.string.help_tiffin_bill_example,
        icon = Icons.Default.Restaurant,
        buttonTestTag = "tiffin_rate_help_btn"
    ),
    ATTENDANCE_BONUS(
        titleRes = R.string.help_attendance_bonus_title,
        descRes = R.string.help_attendance_bonus_desc,
        exampleRes = R.string.help_attendance_bonus_example,
        icon = Icons.Default.EmojiEvents,
        buttonTestTag = "attendance_bonus_help_btn"
    ),
    MAX_ABSENT_BONUS(
        titleRes = R.string.help_max_absent_title,
        descRes = R.string.help_max_absent_desc,
        exampleRes = R.string.help_max_absent_example,
        icon = Icons.Default.EventBusy,
        buttonTestTag = "max_absent_bonus_help_btn"
    ),
    MAX_LEAVE_BONUS(
        titleRes = R.string.help_max_leave_title,
        descRes = R.string.help_max_leave_desc,
        exampleRes = R.string.help_max_leave_example,
        icon = Icons.Default.DateRange,
        buttonTestTag = "max_leave_bonus_help_btn"
    )
}

internal fun shouldShowMissingFieldHighlight(
    activeHighlightField: String?,
    targetField: String,
    inputText: String
): Boolean {
    return activeHighlightField == targetField && inputText.isBlank()
}

@Composable
fun EditIdentitySheet(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    val spacing = ResponsiveUtils.rememberAppSpacing()
    val isCompactScreen = spacing.isCompact
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val hideKeyboard = {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    var userName by remember(profile) { mutableStateOf(profile.userName) }
    var companyName by remember(profile) { mutableStateOf(profile.companyName) }
    var designation by remember(profile) { mutableStateOf(profile.designation) }
    var workerId by remember(profile) { mutableStateOf(profile.workerId) }
    var grade by remember(profile) { mutableStateOf(profile.grade) }

    DisposableEffect(Unit) {
        onDispose { hideKeyboard() }
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
            .testTag("edit_identity_sheet"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
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
                            imageVector = Icons.Default.Badge,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(spacing.iconLg)
                        )
                        Spacer(modifier = Modifier.width(spacing.rowGapMd))
                        AutoFitSingleLineText(
                            text = stringResource(R.string.edit_identity_title),
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
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
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
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text(stringResource(R.string.worker_name_label)) },
                    modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                )

                if (isCompactScreen) {
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text(stringResource(R.string.company_factory_label)) },
                        modifier = Modifier.fillMaxWidth().testTag("profile_company_input"),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) }
                    )
                    OutlinedTextField(
                        value = designation,
                        onValueChange = { designation = it },
                        label = { Text(stringResource(R.string.designation_label)) },
                        modifier = Modifier.fillMaxWidth().testTag("profile_designation_input"),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Work, contentDescription = null) }
                    )
                    OutlinedTextField(
                        value = workerId,
                        onValueChange = { workerId = it },
                        label = { Text(stringResource(R.string.worker_id_label)) },
                        modifier = Modifier.fillMaxWidth().testTag("profile_id_input"),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) }
                    )
                    OutlinedTextField(
                        value = grade,
                        onValueChange = { grade = it },
                        label = { Text(stringResource(R.string.grade_label)) },
                        modifier = Modifier.fillMaxWidth().testTag("profile_grade_input"),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Stars, contentDescription = null) }
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)
                    ) {
                        OutlinedTextField(
                            value = companyName,
                            onValueChange = { companyName = it },
                            label = { Text(stringResource(R.string.company_factory_label)) },
                            modifier = Modifier.weight(1f).testTag("profile_company_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = designation,
                            onValueChange = { designation = it },
                            label = { Text(stringResource(R.string.designation_label)) },
                            modifier = Modifier.weight(1f).testTag("profile_designation_input"),
                            singleLine = true
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)
                    ) {
                        OutlinedTextField(
                            value = workerId,
                            onValueChange = { workerId = it },
                            label = { Text(stringResource(R.string.worker_id_label)) },
                            modifier = Modifier.weight(1f).testTag("profile_id_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = grade,
                            onValueChange = { grade = it },
                            label = { Text(stringResource(R.string.grade_label)) },
                            modifier = Modifier.weight(1f).testTag("profile_grade_input"),
                            singleLine = true
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.sheetFooterH, vertical = spacing.sheetFooterV),
                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
                ) {
                    OutlinedButton(
                        onClick = {
                            hideKeyboard()
                            onDismiss()
                        },
                        contentPadding = spacing.compactButtonContentPadding,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = spacing.minTouchTarget)
                            .testTag("cancel_edit_button"),
                        shape = RoundedCornerShape(spacing.cardRadius)
                    ) {
                        AutoFitSingleLineText(
                            stringResource(R.string.cancel),
                            fontWeight = FontWeight.SemiBold,
                            maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                            minFontSize = 10.sp
                        )
                    }
                    Button(
                        onClick = {
                            hideKeyboard()
                            onSave(
                                profile.copy(
                                    userName = userName.trim(),
                                    companyName = companyName.trim(),
                                    designation = designation.trim(),
                                    workerId = workerId.trim(),
                                    grade = grade.trim()
                                )
                            )
                        },
                        contentPadding = spacing.compactButtonContentPadding,
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = spacing.minTouchTarget)
                            .testTag("save_identity_button"),
                        shape = RoundedCornerShape(spacing.cardRadius)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(spacing.iconMd))
                        Spacer(modifier = Modifier.width(spacing.rowGapSm))
                        AutoFitSingleLineText(
                            stringResource(R.string.save_identity_btn),
                            fontWeight = FontWeight.Bold,
                            maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                            minFontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SalaryStructureSheet(
    profile: UserProfile,
    effectiveLang: String,
    initialHighlightField: String? = null,
    highlightTriggerId: Int = 0,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    val isBnDigits = LanguageManager.isBengaliDigits(effectiveLang)
    val spacing = ResponsiveUtils.rememberAppSpacing()
    val isCompactScreen = spacing.isCompact
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val hideKeyboard = {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    var isCustomSalary by remember(profile) { mutableStateOf(profile.isCustomSalary) }
    var grossSalaryText by remember(profile) {
        mutableStateOf(
            if (profile.basicSalary > 0.0) {
                if (profile.isCustomSalary) {
                    LaborLawCalculator.formatEditableNumber(profile.basicSalary)
                } else {
                    LaborLawCalculator.formatEditableNumber(
                        LaborLawCalculator.calculateAutoAllowances(profile.basicSalary).grossSalary
                    )
                }
            } else ""
        )
    }
    var basicSalaryText by remember(profile) {
        mutableStateOf(LaborLawCalculator.formatEditableNumber(profile.basicSalary))
    }
    var nightBillRateText by remember(profile) {
        mutableStateOf(LaborLawCalculator.formatEditableNumber(profile.nightBillRate))
    }
    var tiffinBillRateText by remember(profile) {
        mutableStateOf(LaborLawCalculator.formatEditableNumber(profile.tiffinBillRate))
    }
    var attendanceBonusText by remember(profile) {
        mutableStateOf(LaborLawCalculator.formatEditableNumber(profile.attendanceBonus))
    }
    var maxAbsentForBonusText by remember(profile) {
        mutableStateOf(if (profile.maxAbsentForBonus <= 0) "" else profile.maxAbsentForBonus.toString())
    }
    var maxLeaveForBonusText by remember(profile) {
        mutableStateOf(profile.maxLeaveForBonus?.toString() ?: "")
    }
    var customOtRateText by remember(profile) {
        mutableStateOf(LaborLawCalculator.formatEditableNumber(profile.customOtRate))
    }

    var activeHighlightField by remember(highlightTriggerId) { mutableStateOf(initialHighlightField) }
    var activeHelpTopic by remember { mutableStateOf<SalaryFieldHelpTopic?>(null) }
    var showModeGuideDialog by rememberSaveable {
        mutableStateOf(SalaryOnboardingPolicy.shouldAutoShowModeGuide(profile))
    }
    var blinkPhaseOn by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val salaryBringIntoViewRequester = remember { BringIntoViewRequester() }
    val nightBringIntoViewRequester = remember { BringIntoViewRequester() }
    val tiffinBringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(showModeGuideDialog) {
        if (showModeGuideDialog) {
            hideKeyboard()
        }
    }

    LaunchedEffect(highlightTriggerId, initialHighlightField, showModeGuideDialog) {
        if (showModeGuideDialog) return@LaunchedEffect
        val target = initialHighlightField ?: return@LaunchedEffect
        activeHighlightField = target
        blinkPhaseOn = true
        withFrameNanos { }
        delay(60L)
        val requester = when (target) {
            "NIGHT_BILL" -> nightBringIntoViewRequester
            "TIFFIN_BILL" -> tiffinBringIntoViewRequester
            else -> salaryBringIntoViewRequester
        }
        try {
            requester.bringIntoView()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}

        try {
            repeat(3) {
                if (activeHighlightField != target) return@LaunchedEffect
                blinkPhaseOn = false
                delay(160L)
                if (activeHighlightField != target) return@LaunchedEffect
                blinkPhaseOn = true
                delay(200L)
            }
        } finally {
            blinkPhaseOn = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { hideKeyboard() }
    }

    BackHandler(enabled = activeHelpTopic == null && !showModeGuideDialog) {
        hideKeyboard()
        onDismiss()
    }

    val currentGross = LaborLawCalculator.parseLocalizedDouble(grossSalaryText)
    val autoResult = remember(currentGross) {
        LaborLawCalculator.calculateFromGrossSalary(currentGross)
    }
    val effectiveHourlyOtRate: Double? = if (isCustomSalary) {
        LaborLawCalculator.parseLocalizedDouble(customOtRateText).takeIf { it > 0.0 }
    } else {
        autoResult.otHourlyRate
    }

    val errorRed = MaterialTheme.colorScheme.error
    val defaultOutline = MaterialTheme.colorScheme.outline
    val defaultPrimary = MaterialTheme.colorScheme.primary
    val defaultLabel = MaterialTheme.colorScheme.onSurfaceVariant

    val activeSalaryInputText = if (isCustomSalary) basicSalaryText else grossSalaryText
    val isSalaryHighlighted = shouldShowMissingFieldHighlight(activeHighlightField, "SALARY", activeSalaryInputText)
    val isNightHighlighted = shouldShowMissingFieldHighlight(activeHighlightField, "NIGHT_BILL", nightBillRateText)
    val isTiffinHighlighted = shouldShowMissingFieldHighlight(activeHighlightField, "TIFFIN_BILL", tiffinBillRateText)

    val salaryBorderColor by animateColorAsState(
        targetValue = if (!isSalaryHighlighted) defaultOutline else if (blinkPhaseOn) errorRed else defaultOutline.copy(alpha = 0.25f),
        animationSpec = tween(90),
        label = "salaryBorderColor"
    )
    val salaryFocusedBorderColor by animateColorAsState(
        targetValue = if (!isSalaryHighlighted) defaultPrimary else if (blinkPhaseOn) errorRed else defaultOutline.copy(alpha = 0.25f),
        animationSpec = tween(90),
        label = "salaryFocusedBorderColor"
    )
    val nightBorderColor by animateColorAsState(
        targetValue = if (!isNightHighlighted) defaultOutline else if (blinkPhaseOn) errorRed else defaultOutline.copy(alpha = 0.25f),
        animationSpec = tween(90),
        label = "nightBorderColor"
    )
    val nightFocusedBorderColor by animateColorAsState(
        targetValue = if (!isNightHighlighted) defaultPrimary else if (blinkPhaseOn) errorRed else defaultOutline.copy(alpha = 0.25f),
        animationSpec = tween(90),
        label = "nightFocusedBorderColor"
    )
    val tiffinBorderColor by animateColorAsState(
        targetValue = if (!isTiffinHighlighted) defaultOutline else if (blinkPhaseOn) errorRed else defaultOutline.copy(alpha = 0.25f),
        animationSpec = tween(90),
        label = "tiffinBorderColor"
    )
    val tiffinFocusedBorderColor by animateColorAsState(
        targetValue = if (!isTiffinHighlighted) defaultPrimary else if (blinkPhaseOn) errorRed else defaultOutline.copy(alpha = 0.25f),
        animationSpec = tween(90),
        label = "tiffinFocusedBorderColor"
    )

    var lastHelpTopic by remember { mutableStateOf(SalaryFieldHelpTopic.GROSS_SALARY) }
    LaunchedEffect(activeHelpTopic) {
        activeHelpTopic?.let { lastHelpTopic = it }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { hideKeyboard() }
                )
                .testTag("salary_structure_sheet"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
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
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(spacing.iconLg)
                        )
                        Spacer(modifier = Modifier.width(spacing.rowGapMd))
                        AutoFitSingleLineText(
                            text = stringResource(R.string.salary_structure_title),
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
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
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
                // Step 1: Calculation Mode
                SheetStepHeader(
                    title = stringResource(R.string.sheet_step_1_title),
                    badgeText = stringResource(R.string.badge_required),
                    isRequired = true,
                    helpContent = {
                        IconButton(
                            onClick = {
                                hideKeyboard()
                                showModeGuideDialog = true
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("salary_mode_help_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                contentDescription = stringResource(R.string.cd_salary_mode_help),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(spacing.iconMd)
                            )
                        }
                    }
                )

                // Mode Toggle: Automatic vs Custom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(spacing.cardRadius)
                        )
                        .padding(spacing.rowGapSm),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(spacing.subCardRadius),
                        color = if (!isCustomSalary) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = spacing.minTouchTarget)
                            .clickable {
                                isCustomSalary = false
                                customOtRateText = ""
                            }
                            .testTag("mode_auto_tab")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = spacing.subCardPadding, vertical = spacing.compactActionPadV),
                            contentAlignment = Alignment.Center
                        ) {
                            AutoFitSingleLineText(
                                text = stringResource(R.string.mode_auto_tab),
                                maxFontSize = spacing.adaptiveSp(13f, 11.5f, 10.5f),
                                minFontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = if (!isCustomSalary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(spacing.subCardRadius),
                        color = if (isCustomSalary) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = spacing.minTouchTarget)
                            .clickable {
                                isCustomSalary = true
                                if (basicSalaryText.isBlank() || basicSalaryText == "0") {
                                    basicSalaryText = if (currentGross > 0.0) grossSalaryText else LaborLawCalculator.formatEditableNumber(autoResult.grossSalary)
                                }
                            }
                            .testTag("mode_custom_tab")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = spacing.subCardPadding, vertical = spacing.compactActionPadV),
                            contentAlignment = Alignment.Center
                        ) {
                            AutoFitSingleLineText(
                                text = stringResource(R.string.mode_custom_tab),
                                maxFontSize = spacing.adaptiveSp(13f, 11.5f, 10.5f),
                                minFontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = if (isCustomSalary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(spacing.subCardRadius),
                    color = if (!isCustomSalary) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = stringResource(if (!isCustomSalary) R.string.mode_auto_note else R.string.mode_custom_note),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = if (!isCustomSalary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(spacing.subCardPadding)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                // Step 2: Monthly Salary & Instant OT Rate Preview
                SheetStepHeader(
                    title = stringResource(R.string.sheet_step_2_title),
                    badgeText = stringResource(R.string.badge_required),
                    isRequired = true
                )

                if (!isCustomSalary) {
                    val grossLabel = stringResource(R.string.gross_salary_input_label)
                    OutlinedTextField(
                        value = grossSalaryText,
                        onValueChange = {
                            val normalized = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                            grossSalaryText = normalized
                            if (normalized.isNotEmpty() && activeHighlightField == "SALARY") {
                                activeHighlightField = null
                            }
                        },
                        label = { Text(grossLabel) },
                        placeholder = { Text(stringResource(R.string.example_gross_salary)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(salaryBringIntoViewRequester)
                            .testTag("gross_salary_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Payments,
                                contentDescription = null,
                                tint = if (isSalaryHighlighted) errorRed else defaultPrimary
                            )
                        },
                        trailingIcon = {
                            FieldHelpIconButton(
                                topic = SalaryFieldHelpTopic.GROSS_SALARY,
                                fieldLabel = grossLabel,
                                onClick = {
                                    hideKeyboard()
                                    activeHelpTopic = SalaryFieldHelpTopic.GROSS_SALARY
                                }
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = salaryFocusedBorderColor,
                            unfocusedBorderColor = salaryBorderColor,
                            focusedLabelColor = if (isSalaryHighlighted) errorRed else defaultPrimary,
                            unfocusedLabelColor = if (isSalaryHighlighted) errorRed else defaultLabel
                        )
                    )
                } else {
                    val customSalaryLabel = stringResource(R.string.total_salary_input_label)
                    OutlinedTextField(
                        value = basicSalaryText,
                        onValueChange = {
                            val normalized = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                            basicSalaryText = normalized
                            if (normalized.isNotEmpty() && activeHighlightField == "SALARY") {
                                activeHighlightField = null
                            }
                        },
                        label = { Text(customSalaryLabel) },
                        placeholder = { Text(stringResource(R.string.example_gross_salary)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(salaryBringIntoViewRequester)
                            .testTag("basic_salary_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Payments,
                                contentDescription = null,
                                tint = if (isSalaryHighlighted) errorRed else defaultPrimary
                            )
                        },
                        trailingIcon = {
                            FieldHelpIconButton(
                                topic = SalaryFieldHelpTopic.CUSTOM_BASIC_SALARY,
                                fieldLabel = customSalaryLabel,
                                onClick = {
                                    hideKeyboard()
                                    activeHelpTopic = SalaryFieldHelpTopic.CUSTOM_BASIC_SALARY
                                }
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = salaryFocusedBorderColor,
                            unfocusedBorderColor = salaryBorderColor,
                            focusedLabelColor = if (isSalaryHighlighted) errorRed else defaultPrimary,
                            unfocusedLabelColor = if (isSalaryHighlighted) errorRed else defaultLabel
                        )
                    )
                    val customOtLabel = stringResource(R.string.custom_ot_rate_label)
                    OutlinedTextField(
                        value = customOtRateText,
                        onValueChange = { customOtRateText = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true) },
                        label = { Text(customOtLabel) },
                        placeholder = { Text(stringResource(R.string.example_custom_ot_rate)) },
                        modifier = Modifier.fillMaxWidth().testTag("custom_ot_rate_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        trailingIcon = {
                            FieldHelpIconButton(
                                topic = SalaryFieldHelpTopic.CUSTOM_OT_RATE,
                                fieldLabel = customOtLabel,
                                onClick = {
                                    hideKeyboard()
                                    activeHelpTopic = SalaryFieldHelpTopic.CUSTOM_OT_RATE
                                }
                            )
                        },
                        supportingText = { Text(stringResource(R.string.custom_ot_rate_hint)) }
                    )
                }

                // Live Calculated Rate Preview (Placed right after salary input for instant clarity)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(spacing.cardRadius),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.cardPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = spacing.rowGapSm)) {
                            AutoFitSingleLineText(
                                text = stringResource(R.string.effective_ot_rate_label),
                                maxFontSize = spacing.adaptiveSp(13f, 11.5f, 10.5f),
                                minFontSize = 8.5.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            AutoFitSingleLineText(
                                text = if (effectiveHourlyOtRate != null && effectiveHourlyOtRate > 0.0) {
                                    LaborLawCalculator.formatCurrency(effectiveHourlyOtRate, isBnDigits) +
                                        stringResource(R.string.currency_per_hour_suffix)
                                } else {
                                    "—"
                                },
                                maxFontSize = spacing.adaptiveSp(19f, 17f, 15f),
                                minFontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            AutoFitSingleLineText(
                                text = stringResource(R.string.total_regular_salary_label),
                                maxFontSize = spacing.adaptiveSp(13f, 11.5f, 10.5f),
                                minFontSize = 8.5.sp,
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            val calculatedGross = if (isCustomSalary) {
                                LaborLawCalculator.parseLocalizedDouble(basicSalaryText)
                            } else {
                                autoResult.grossSalary
                            }
                            AutoFitSingleLineText(
                                text = LaborLawCalculator.formatCurrency(calculatedGross, isBnDigits),
                                maxFontSize = spacing.adaptiveSp(17.5f, 15.5f, 14f),
                                minFontSize = 10.5.sp,
                                textAlign = TextAlign.End,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                if (!isCustomSalary) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.auto_breakdown_locked_title),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = stringResource(R.string.auto_breakdown_no_edit_hint),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = autoResult.basicSalary.roundToInt().toString(),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.basic_salary_locked_label)) },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth().testTag("locked_basic_salary_input"),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
                        singleLine = true
                    )
                    if (isCompactScreen) {
                        OutlinedTextField(
                            value = autoResult.houseRent.roundToInt().toString(),
                            onValueChange = {},
                            label = { Text(stringResource(R.string.house_rent_label)) },
                            enabled = false,
                            modifier = Modifier.fillMaxWidth().testTag("house_rent_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = "750",
                            onValueChange = {},
                            label = { Text(stringResource(R.string.medical_allowance_fixed_750)) },
                            enabled = false,
                            modifier = Modifier.fillMaxWidth().testTag("medical_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = autoResult.conveyanceAllowance.roundToInt().toString(),
                            onValueChange = {},
                            label = { Text(stringResource(R.string.conveyance_allowance_label)) },
                            enabled = false,
                            modifier = Modifier.fillMaxWidth().testTag("conveyance_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = autoResult.foodAllowance.roundToInt().toString(),
                            onValueChange = {},
                            label = { Text(stringResource(R.string.food_allowance_label)) },
                            enabled = false,
                            modifier = Modifier.fillMaxWidth().testTag("food_input"),
                            singleLine = true
                        )
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)) {
                            OutlinedTextField(
                                value = autoResult.houseRent.roundToInt().toString(),
                                onValueChange = {},
                                label = { Text(stringResource(R.string.house_rent_label)) },
                                enabled = false,
                                modifier = Modifier.weight(1f).testTag("house_rent_input"),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = "750",
                                onValueChange = {},
                                label = { Text(stringResource(R.string.medical_allowance_fixed_750)) },
                                enabled = false,
                                modifier = Modifier.weight(1f).testTag("medical_input"),
                                singleLine = true
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)) {
                            OutlinedTextField(
                                value = autoResult.conveyanceAllowance.roundToInt().toString(),
                                onValueChange = {},
                                label = { Text(stringResource(R.string.conveyance_allowance_label)) },
                                enabled = false,
                                modifier = Modifier.weight(1f).testTag("conveyance_input"),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = autoResult.foodAllowance.roundToInt().toString(),
                                onValueChange = {},
                                label = { Text(stringResource(R.string.food_allowance_label)) },
                                enabled = false,
                                modifier = Modifier.weight(1f).testTag("food_input"),
                                singleLine = true
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                // Step 3: Optional Bills & Attendance Bonus
                SheetStepHeader(
                    title = stringResource(R.string.sheet_step_3_title),
                    badgeText = stringResource(R.string.badge_optional),
                    isRequired = false
                )

                Surface(
                    shape = RoundedCornerShape(spacing.subCardRadius),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.sheet_step_3_hint),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(spacing.subCardPadding)
                    )
                }

                // Night & Tiffin Rates
                val nightRateLabel = stringResource(R.string.night_bill_rate_label)
                val tiffinRateLabel = stringResource(R.string.tiffin_bill_rate_label)
                if (isCompactScreen) {
                    OutlinedTextField(
                        value = nightBillRateText,
                        onValueChange = {
                            val normalized = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                            nightBillRateText = normalized
                            if (normalized.isNotEmpty() && activeHighlightField == "NIGHT_BILL") {
                                activeHighlightField = null
                            }
                        },
                        label = { Text(nightRateLabel) },
                        placeholder = { Text(stringResource(R.string.example_night_bill)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(nightBringIntoViewRequester)
                            .testTag("night_rate_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        trailingIcon = {
                            FieldHelpIconButton(
                                topic = SalaryFieldHelpTopic.NIGHT_BILL_RATE,
                                fieldLabel = nightRateLabel,
                                onClick = {
                                    hideKeyboard()
                                    activeHelpTopic = SalaryFieldHelpTopic.NIGHT_BILL_RATE
                                }
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = nightFocusedBorderColor,
                            unfocusedBorderColor = nightBorderColor,
                            focusedLabelColor = if (isNightHighlighted) errorRed else defaultPrimary,
                            unfocusedLabelColor = if (isNightHighlighted) errorRed else defaultLabel
                        )
                    )
                    OutlinedTextField(
                        value = tiffinBillRateText,
                        onValueChange = {
                            val normalized = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                            tiffinBillRateText = normalized
                            if (normalized.isNotEmpty() && activeHighlightField == "TIFFIN_BILL") {
                                activeHighlightField = null
                            }
                        },
                        label = { Text(tiffinRateLabel) },
                        placeholder = { Text(stringResource(R.string.example_tiffin_bill)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(tiffinBringIntoViewRequester)
                            .testTag("tiffin_rate_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        trailingIcon = {
                            FieldHelpIconButton(
                                topic = SalaryFieldHelpTopic.TIFFIN_BILL_RATE,
                                fieldLabel = tiffinRateLabel,
                                onClick = {
                                    hideKeyboard()
                                    activeHelpTopic = SalaryFieldHelpTopic.TIFFIN_BILL_RATE
                                }
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = tiffinFocusedBorderColor,
                            unfocusedBorderColor = tiffinBorderColor,
                            focusedLabelColor = if (isTiffinHighlighted) errorRed else defaultPrimary,
                            unfocusedLabelColor = if (isTiffinHighlighted) errorRed else defaultLabel
                        )
                    )
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)) {
                        OutlinedTextField(
                            value = nightBillRateText,
                            onValueChange = {
                                val normalized = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                                nightBillRateText = normalized
                                if (normalized.isNotEmpty() && activeHighlightField == "NIGHT_BILL") {
                                    activeHighlightField = null
                                }
                            },
                            label = { Text(nightRateLabel) },
                            placeholder = { Text(stringResource(R.string.example_night_bill)) },
                            modifier = Modifier
                                .weight(1f)
                                .bringIntoViewRequester(nightBringIntoViewRequester)
                                .testTag("night_rate_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            trailingIcon = {
                                FieldHelpIconButton(
                                    topic = SalaryFieldHelpTopic.NIGHT_BILL_RATE,
                                    fieldLabel = nightRateLabel,
                                    onClick = {
                                        hideKeyboard()
                                        activeHelpTopic = SalaryFieldHelpTopic.NIGHT_BILL_RATE
                                    }
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = nightFocusedBorderColor,
                                unfocusedBorderColor = nightBorderColor,
                                focusedLabelColor = if (isNightHighlighted) errorRed else defaultPrimary,
                                unfocusedLabelColor = if (isNightHighlighted) errorRed else defaultLabel
                            )
                        )
                        OutlinedTextField(
                            value = tiffinBillRateText,
                            onValueChange = {
                                val normalized = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                                tiffinBillRateText = normalized
                                if (normalized.isNotEmpty() && activeHighlightField == "TIFFIN_BILL") {
                                    activeHighlightField = null
                                }
                            },
                            label = { Text(tiffinRateLabel) },
                            placeholder = { Text(stringResource(R.string.example_tiffin_bill)) },
                            modifier = Modifier
                                .weight(1f)
                                .bringIntoViewRequester(tiffinBringIntoViewRequester)
                                .testTag("tiffin_rate_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            trailingIcon = {
                                FieldHelpIconButton(
                                    topic = SalaryFieldHelpTopic.TIFFIN_BILL_RATE,
                                    fieldLabel = tiffinRateLabel,
                                    onClick = {
                                        hideKeyboard()
                                        activeHelpTopic = SalaryFieldHelpTopic.TIFFIN_BILL_RATE
                                    }
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = tiffinFocusedBorderColor,
                                unfocusedBorderColor = tiffinBorderColor,
                                focusedLabelColor = if (isTiffinHighlighted) errorRed else defaultPrimary,
                                unfocusedLabelColor = if (isTiffinHighlighted) errorRed else defaultLabel
                            )
                        )
                    }
                }

                // Attendance Bonus & Eligibility Thresholds (Absent + Allowed Leave Days together!)
                val attendanceBonusLabel = stringResource(R.string.attendance_bonus_label)
                OutlinedTextField(
                    value = attendanceBonusText,
                    onValueChange = { attendanceBonusText = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true) },
                    label = { Text(attendanceBonusLabel) },
                    placeholder = { Text(stringResource(R.string.example_attendance_bonus)) },
                    modifier = Modifier.fillMaxWidth().testTag("attendance_bonus_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    trailingIcon = {
                        FieldHelpIconButton(
                            topic = SalaryFieldHelpTopic.ATTENDANCE_BONUS,
                            fieldLabel = attendanceBonusLabel,
                            onClick = {
                                hideKeyboard()
                                activeHelpTopic = SalaryFieldHelpTopic.ATTENDANCE_BONUS
                            }
                        )
                    }
                )

                val maxAbsentLabel = stringResource(R.string.bonus_absent_threshold_label)
                OutlinedTextField(
                    value = maxAbsentForBonusText,
                    onValueChange = { maxAbsentForBonusText = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = false) },
                    label = { Text(maxAbsentLabel) },
                    placeholder = { Text(stringResource(R.string.example_absent_days)) },
                    modifier = Modifier.fillMaxWidth().testTag("max_absent_bonus_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.bonus_absent_threshold_hint), fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        FieldHelpIconButton(
                            topic = SalaryFieldHelpTopic.MAX_ABSENT_BONUS,
                            fieldLabel = maxAbsentLabel,
                            onClick = {
                                hideKeyboard()
                                activeHelpTopic = SalaryFieldHelpTopic.MAX_ABSENT_BONUS
                            }
                        )
                    }
                )

                val maxLeaveLabel = stringResource(R.string.allowed_leave_days_title)
                OutlinedTextField(
                    value = maxLeaveForBonusText,
                    onValueChange = { maxLeaveForBonusText = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = false) },
                    label = { Text(maxLeaveLabel) },
                    placeholder = { Text(stringResource(R.string.leave_days_placeholder)) },
                    modifier = Modifier.fillMaxWidth().testTag("max_leave_bonus_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (maxLeaveForBonusText.isNotEmpty()) {
                                IconButton(
                                    onClick = { maxLeaveForBonusText = "" },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear), modifier = Modifier.size(spacing.iconSm))
                                }
                            }
                            FieldHelpIconButton(
                                topic = SalaryFieldHelpTopic.MAX_LEAVE_BONUS,
                                fieldLabel = maxLeaveLabel,
                                onClick = {
                                    hideKeyboard()
                                    activeHelpTopic = SalaryFieldHelpTopic.MAX_LEAVE_BONUS
                                }
                            )
                        }
                    }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.sheetFooterH, vertical = spacing.sheetFooterV),
                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
                ) {
                    OutlinedButton(
                        onClick = {
                            hideKeyboard()
                            onDismiss()
                        },
                        contentPadding = spacing.compactButtonContentPadding,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = spacing.minTouchTarget),
                        shape = RoundedCornerShape(spacing.cardRadius)
                    ) {
                        AutoFitSingleLineText(
                            stringResource(R.string.cancel),
                            fontWeight = FontWeight.SemiBold,
                            maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                            minFontSize = 10.sp
                        )
                    }
                    Button(
                        onClick = {
                            hideKeyboard()
                            val basic = if (isCustomSalary) {
                                LaborLawCalculator.parseLocalizedDouble(basicSalaryText)
                            } else {
                                autoResult.basicSalary
                            }
                            val updated = profile.copy(
                                isCustomSalary = isCustomSalary,
                                basicSalary = basic,
                                houseRent = if (isCustomSalary) 0.0 else autoResult.houseRent,
                                medicalAllowance = if (isCustomSalary) 0.0 else 750.0,
                                conveyanceAllowance = if (isCustomSalary) 0.0 else autoResult.conveyanceAllowance,
                                foodAllowance = if (isCustomSalary) 0.0 else autoResult.foodAllowance,
                                attendanceBonus = LaborLawCalculator.parseLocalizedDouble(attendanceBonusText),
                                maxAbsentForBonus = LaborLawCalculator.parseLocalizedInt(maxAbsentForBonusText).coerceAtLeast(0),
                                maxLeaveForBonus = LaborLawCalculator.parseLocalizedIntOrNull(maxLeaveForBonusText),
                                nightBillRate = LaborLawCalculator.parseLocalizedDouble(nightBillRateText),
                                tiffinBillRate = LaborLawCalculator.parseLocalizedDouble(tiffinBillRateText),
                                customOtRate = LaborLawCalculator.parseLocalizedDouble(customOtRateText)
                            )
                            onSave(updated)
                        },
                        contentPadding = spacing.compactButtonContentPadding,
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = spacing.minTouchTarget)
                            .testTag("save_profile_button"),
                        shape = RoundedCornerShape(spacing.cardRadius)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(spacing.iconMd))
                        Spacer(modifier = Modifier.width(spacing.rowGapSm))
                        AutoFitSingleLineText(
                            stringResource(R.string.save_salary_btn),
                            fontWeight = FontWeight.Bold,
                            maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                            minFontSize = 10.sp
                        )
                    }
                }
            }
        }
        }

        if (showModeGuideDialog && activeHelpTopic == null) {
            BackHandler {
                showModeGuideDialog = false
            }
        }

        if (activeHelpTopic != null) {
            BackHandler {
                activeHelpTopic = null
            }
        }

        AnimatedVisibility(
            visible = showModeGuideDialog && activeHelpTopic == null,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(130))
        ) {
            SalaryModeGuideDialog(
                isCustomSalarySelected = isCustomSalary,
                onSelectAuto = {
                    isCustomSalary = false
                    customOtRateText = ""
                    showModeGuideDialog = false
                    coroutineScope.launch {
                        delay(80L)
                        try {
                            salaryBringIntoViewRequester.bringIntoView()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {}
                    }
                },
                onSelectCustom = {
                    isCustomSalary = true
                    if (basicSalaryText.isBlank() || basicSalaryText == "0") {
                        basicSalaryText = if (currentGross > 0.0) {
                            grossSalaryText
                        } else {
                            LaborLawCalculator.formatEditableNumber(autoResult.grossSalary)
                        }
                    }
                    showModeGuideDialog = false
                    coroutineScope.launch {
                        delay(80L)
                        try {
                            salaryBringIntoViewRequester.bringIntoView()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {}
                    }
                },
                onDismiss = { showModeGuideDialog = false },
                cardModifier = Modifier.animateEnterExit(
                    enter = scaleIn(animationSpec = tween(180), initialScale = 0.94f),
                    exit = scaleOut(animationSpec = tween(130), targetScale = 0.96f)
                )
            )
        }

        AnimatedVisibility(
            visible = activeHelpTopic != null,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(120))
        ) {
            SalaryFieldHelpDialog(
                topic = activeHelpTopic ?: lastHelpTopic,
                onDismiss = { activeHelpTopic = null },
                cardModifier = Modifier.animateEnterExit(
                    enter = scaleIn(animationSpec = tween(150), initialScale = 0.94f),
                    exit = scaleOut(animationSpec = tween(120), targetScale = 0.96f)
                )
            )
        }
    }
}

@Composable
private fun FieldHelpIconButton(
    topic: SalaryFieldHelpTopic,
    fieldLabel: String,
    onClick: () -> Unit
) {
    val spacing = ResponsiveUtils.rememberAppSpacing()
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(topic.buttonTestTag)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = stringResource(R.string.cd_field_help_fmt, fieldLabel),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(spacing.iconMd)
        )
    }
}

@Composable
private fun SalaryFieldHelpDialog(
    topic: SalaryFieldHelpTopic,
    onDismiss: () -> Unit,
    cardModifier: Modifier = Modifier
) {
    val spacing = ResponsiveUtils.rememberAppSpacing()
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
            modifier = cardModifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .clip(RoundedCornerShape(spacing.heroRadius))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .testTag("salary_field_help_dialog"),
            shape = RoundedCornerShape(spacing.heroRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.dialogCardPadding),
                verticalArrangement = Arrangement.spacedBy(spacing.dialogInnerGap)
            ) {
                Row(
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
                                imageVector = topic.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(spacing.iconLg)
                            )
                        }
                    }
                    AutoFitSingleLineText(
                        text = stringResource(topic.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxFontSize = spacing.adaptiveSp(17.5f, 15.5f, 14f),
                        minFontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(spacing.cardInnerGap)
                ) {
                    Text(
                        text = stringResource(topic.descRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(spacing.cardRadius),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(spacing.subCardPadding),
                            verticalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(spacing.badgeRadius),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.salary_help_example_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxFontSize = 10.5.sp,
                                    minFontSize = 8.sp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                                )
                            }
                            Text(
                                text = stringResource(topic.exampleRes),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        contentPadding = spacing.compactButtonContentPadding,
                        modifier = Modifier
                            .heightIn(min = spacing.minTouchTarget)
                            .testTag("salary_field_help_close_btn"),
                        shape = RoundedCornerShape(spacing.cardRadius)
                    ) {
                        AutoFitSingleLineText(
                            text = stringResource(R.string.salary_help_got_it),
                            fontWeight = FontWeight.SemiBold,
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
private fun SheetStepHeader(
    title: String,
    badgeText: String,
    isRequired: Boolean,
    helpContent: (@Composable () -> Unit)? = null
) {
    val spacing = ResponsiveUtils.rememberAppSpacing()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutoFitSingleLineText(
            text = title,
            maxFontSize = spacing.adaptiveSp(15.5f, 14f, 13f),
            minFontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).padding(end = spacing.rowGapSm)
        )
        if (helpContent != null) {
            helpContent()
        }
        Surface(
            shape = RoundedCornerShape(spacing.badgeRadius),
            color = if (isRequired) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
        ) {
            AutoFitSingleLineText(
                text = badgeText,
                maxFontSize = 10.5.sp,
                minFontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isRequired) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
            )
        }
    }
}
