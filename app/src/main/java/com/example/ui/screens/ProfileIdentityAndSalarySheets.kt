package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager
import com.example.util.ResponsiveUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
            .imePadding()
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
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.edit_identity_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            hideKeyboard()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = spacing.minTouchTarget)
                            .testTag("cancel_edit_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.cancel), fontWeight = FontWeight.SemiBold)
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
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = spacing.minTouchTarget)
                            .testTag("save_identity_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.save_identity_btn), fontWeight = FontWeight.Bold)
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
    var blinkPhaseOn by remember { mutableStateOf(true) }
    val salaryBringIntoViewRequester = remember { BringIntoViewRequester() }
    val nightBringIntoViewRequester = remember { BringIntoViewRequester() }
    val tiffinBringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(highlightTriggerId, initialHighlightField) {
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

    BackHandler {
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

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
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
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.salary_structure_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
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
                // Mode Toggle: Automatic vs Custom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
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
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.mode_auto_tab),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                color = if (!isCustomSalary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
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
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.mode_custom_tab),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                color = if (isCustomSalary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (!isCustomSalary) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = stringResource(if (!isCustomSalary) R.string.mode_auto_note else R.string.mode_custom_note),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = if (!isCustomSalary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(spacing.subCardPadding)
                    )
                }

                if (!isCustomSalary) {
                    OutlinedTextField(
                        value = grossSalaryText,
                        onValueChange = {
                            val normalized = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                            grossSalaryText = normalized
                            if (normalized.isNotEmpty() && activeHighlightField == "SALARY") {
                                activeHighlightField = null
                            }
                        },
                        label = { Text(stringResource(R.string.gross_salary_input_label)) },
                        placeholder = { Text("0") },
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = salaryFocusedBorderColor,
                            unfocusedBorderColor = salaryBorderColor,
                            focusedLabelColor = if (isSalaryHighlighted) errorRed else defaultPrimary,
                            unfocusedLabelColor = if (isSalaryHighlighted) errorRed else defaultLabel
                        )
                    )

                    Text(
                        text = stringResource(R.string.auto_breakdown_locked_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                } else {
                    OutlinedTextField(
                        value = basicSalaryText,
                        onValueChange = {
                            val normalized = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                            basicSalaryText = normalized
                            if (normalized.isNotEmpty() && activeHighlightField == "SALARY") {
                                activeHighlightField = null
                            }
                        },
                        label = { Text(stringResource(R.string.total_salary_input_label)) },
                        placeholder = { Text("0") },
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = salaryFocusedBorderColor,
                            unfocusedBorderColor = salaryBorderColor,
                            focusedLabelColor = if (isSalaryHighlighted) errorRed else defaultPrimary,
                            unfocusedLabelColor = if (isSalaryHighlighted) errorRed else defaultLabel
                        )
                    )
                    OutlinedTextField(
                        value = customOtRateText,
                        onValueChange = { customOtRateText = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true) },
                        label = { Text(stringResource(R.string.custom_ot_rate_label)) },
                        placeholder = { Text("0") },
                        modifier = Modifier.fillMaxWidth().testTag("custom_ot_rate_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        supportingText = { Text(stringResource(R.string.custom_ot_rate_hint)) }
                    )
                }

                // Night & Tiffin Rates
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
                        label = { Text(stringResource(R.string.night_bill_rate_label)) },
                        placeholder = { Text("0") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(nightBringIntoViewRequester)
                            .testTag("night_rate_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
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
                        label = { Text(stringResource(R.string.tiffin_bill_rate_label)) },
                        placeholder = { Text("0") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(tiffinBringIntoViewRequester)
                            .testTag("tiffin_rate_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = tiffinFocusedBorderColor,
                            unfocusedBorderColor = tiffinBorderColor,
                            focusedLabelColor = if (isTiffinHighlighted) errorRed else defaultPrimary,
                            unfocusedLabelColor = if (isTiffinHighlighted) errorRed else defaultLabel
                        )
                    )
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = nightBillRateText,
                            onValueChange = {
                                val normalized = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                                nightBillRateText = normalized
                                if (normalized.isNotEmpty() && activeHighlightField == "NIGHT_BILL") {
                                    activeHighlightField = null
                                }
                            },
                            label = { Text(stringResource(R.string.night_bill_rate_label)) },
                            placeholder = { Text("0") },
                            modifier = Modifier
                                .weight(1f)
                                .bringIntoViewRequester(nightBringIntoViewRequester)
                                .testTag("night_rate_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
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
                            label = { Text(stringResource(R.string.tiffin_bill_rate_label)) },
                            placeholder = { Text("0") },
                            modifier = Modifier
                                .weight(1f)
                                .bringIntoViewRequester(tiffinBringIntoViewRequester)
                                .testTag("tiffin_rate_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
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
                OutlinedTextField(
                    value = attendanceBonusText,
                    onValueChange = { attendanceBonusText = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true) },
                    label = { Text(stringResource(R.string.attendance_bonus_label)) },
                    placeholder = { Text("0") },
                    modifier = Modifier.fillMaxWidth().testTag("attendance_bonus_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                OutlinedTextField(
                    value = maxAbsentForBonusText,
                    onValueChange = { maxAbsentForBonusText = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = false) },
                    label = { Text(stringResource(R.string.bonus_absent_threshold_label)) },
                    placeholder = { Text("0") },
                    modifier = Modifier.fillMaxWidth().testTag("max_absent_bonus_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.bonus_absent_threshold_hint), fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )

                OutlinedTextField(
                    value = maxLeaveForBonusText,
                    onValueChange = { maxLeaveForBonusText = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = false) },
                    label = { Text(stringResource(R.string.allowed_leave_days_title)) },
                    placeholder = { Text(stringResource(R.string.leave_days_placeholder)) },
                    modifier = Modifier.fillMaxWidth().testTag("max_leave_bonus_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (maxLeaveForBonusText.isNotEmpty()) {
                            IconButton(onClick = { maxLeaveForBonusText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )

                // Live Calculated Rate Preview
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.cardPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.effective_ot_rate_label),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (effectiveHourlyOtRate != null && effectiveHourlyOtRate > 0.0) {
                                    LaborLawCalculator.formatCurrency(effectiveHourlyOtRate, isBnDigits) +
                                        stringResource(R.string.currency_per_hour_suffix)
                                } else {
                                    "—"
                                },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(R.string.total_regular_salary_label),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            val calculatedGross = if (isCustomSalary) {
                                LaborLawCalculator.parseLocalizedDouble(basicSalaryText)
                            } else {
                                autoResult.grossSalary
                            }
                            Text(
                                text = LaborLawCalculator.formatCurrency(calculatedGross, isBnDigits),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.sheetFooterH, vertical = spacing.sheetFooterV),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            hideKeyboard()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = spacing.minTouchTarget),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.cancel), fontWeight = FontWeight.SemiBold)
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
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = spacing.minTouchTarget)
                            .testTag("save_profile_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.save_salary_btn), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
