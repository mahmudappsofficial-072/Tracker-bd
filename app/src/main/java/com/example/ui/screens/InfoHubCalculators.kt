package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.UserProfile
import com.example.ui.theme.NightBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.MonthlySummaryState
import com.example.util.AppSpacing
import com.example.util.InfoHubCalculator
import com.example.util.LaborLawCalculator
import com.example.util.PaySlipMatchStatus
import com.example.util.SeparationType
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickOtCalculatorSection(
    profile: UserProfile,
    summary: MonthlySummaryState,
    isBengali: Boolean,
    spacing: AppSpacing,
    salaryInput: String,
    onSalaryInputChange: (String) -> Unit,
    isGrossMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var otHoursInput by rememberSaveable { mutableStateOf("2") }

    val parsedSalary = remember(salaryInput) { LaborLawCalculator.parseLocalizedDouble(salaryInput, 0.0) }
    val parsedHours = remember(otHoursInput) { LaborLawCalculator.parseLocalizedDouble(otHoursInput, 0.0) }

    val allowanceResult = remember(parsedSalary, isGrossMode) {
        if (isGrossMode) {
            LaborLawCalculator.calculateFromGrossSalary(parsedSalary)
        } else {
            LaborLawCalculator.calculateAutoAllowances(parsedSalary)
        }
    }
    val otTotal = remember(parsedHours, allowanceResult.otHourlyRate) {
        LaborLawCalculator.calculateOtAmount(parsedHours, allowanceResult.otHourlyRate)
    }
    val grandTotal = allowanceResult.grossSalary + otTotal

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("info_quick_ot_calculator_card"),
        shape = RoundedCornerShape(spacing.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.cardInnerGap)
        ) {
            InfoSectionHeader(
                icon = Icons.Filled.Calculate,
                title = stringResource(R.string.info_calc_section_title),
                subtitle = stringResource(R.string.info_calc_section_subtitle),
                accentColor = MaterialTheme.colorScheme.primary,
                spacing = spacing
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !isGrossMode,
                    onClick = { onModeChange(false) },
                    label = { Text(stringResource(R.string.info_calc_mode_basic)) },
                    modifier = Modifier.testTag("info_calc_mode_basic_chip"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = isGrossMode,
                    onClick = { onModeChange(true) },
                    label = { Text(stringResource(R.string.info_calc_mode_gross)) },
                    modifier = Modifier.testTag("info_calc_mode_gross_chip"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }

            OutlinedButton(
                onClick = {
                    val targetAmount = if (isGrossMode) {
                        summary.grossSalary.takeIf { it > 0.0 }
                            ?: if (profile.basicSalary > 0.0) {
                                LaborLawCalculator.calculateAutoAllowances(profile.basicSalary).grossSalary
                            } else 0.0
                    } else {
                        summary.basicSalary.takeIf { it > 0.0 } ?: profile.basicSalary
                    }
                    if (targetAmount > 0.0) {
                        onSalaryInputChange(LaborLawCalculator.formatEditableNumber(targetAmount))
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.info_calc_no_profile_salary),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("info_calc_fill_profile_button"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(spacing.iconSm)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.info_calc_fill_profile_btn),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
            ) {
                OutlinedTextField(
                    value = salaryInput,
                    onValueChange = { raw ->
                        onSalaryInputChange(LaborLawCalculator.normalizeNumericInput(raw, allowDecimal = true))
                    },
                    label = {
                        Text(
                            stringResource(
                                if (isGrossMode) R.string.info_calc_gross_label else R.string.info_calc_basic_label
                            )
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.35f)
                        .testTag("info_calc_salary_input")
                )

                OutlinedTextField(
                    value = otHoursInput,
                    onValueChange = { raw ->
                        otHoursInput = LaborLawCalculator.normalizeNumericInput(raw, allowDecimal = true)
                    },
                    label = { Text(stringResource(R.string.info_calc_ot_hours_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("info_calc_ot_hours_input")
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("1", "2", "3", "4", "8", "12").forEach { hr ->
                    val hrDisplay = if (isBengali) LaborLawCalculator.toBengaliDigits("${hr}h") else "${hr}h"
                    FilterChip(
                        selected = LaborLawCalculator.toEnglishDigits(otHoursInput) == hr,
                        onClick = { otHoursInput = hr },
                        label = { Text(hrDisplay) },
                        modifier = Modifier.testTag("info_calc_hour_chip_$hr")
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(spacing.subCardRadius),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.subCardPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricColumn(
                            label = stringResource(R.string.info_calc_hourly_rate_label),
                            value = LaborLawCalculator.formatCurrency(allowanceResult.otHourlyRate, isBengali),
                            valueColor = NightBlue,
                            modifier = Modifier.weight(1f)
                        )
                        MetricColumn(
                            label = stringResource(R.string.info_calc_ot_earnings_label),
                            value = LaborLawCalculator.formatCurrency(otTotal, isBengali),
                            valueColor = SuccessGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricColumn(
                            label = stringResource(R.string.info_calc_gross_total_label),
                            value = LaborLawCalculator.formatCurrency(allowanceResult.grossSalary, isBengali),
                            valueColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        MetricColumn(
                            label = stringResource(R.string.info_calc_total_with_ot_label),
                            value = LaborLawCalculator.formatCurrency(grandTotal, isBengali),
                            valueColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = stringResource(
                            R.string.info_calc_allowances_breakdown,
                            LaborLawCalculator.formatCurrency(allowanceResult.houseRent, isBengali),
                            LaborLawCalculator.formatCurrency(allowanceResult.medicalAllowance, isBengali),
                            LaborLawCalculator.formatCurrency(allowanceResult.conveyanceAllowance, isBengali),
                            LaborLawCalculator.formatCurrency(allowanceResult.foodAllowance, isBengali)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PaySlipAndServiceSection(
    profile: UserProfile,
    summary: MonthlySummaryState,
    isBengali: Boolean,
    spacing: AppSpacing,
    modifier: Modifier = Modifier
) {
    var activeSubTab by rememberSaveable { mutableIntStateOf(0) } // 0: Pay Slip Verifier, 1: Service Benefit

    // Pay Slip Verifier state
    var slipBasicInput by rememberSaveable { mutableStateOf("") }
    var slipOtHoursInput by rememberSaveable { mutableStateOf("") }
    var slipBonusInput by rememberSaveable { mutableStateOf("") }
    var slipDeductionsInput by rememberSaveable { mutableStateOf("") }
    var slipPaidOtInput by rememberSaveable { mutableStateOf("") }
    var slipPaidNetInput by rememberSaveable { mutableStateOf("") }

    // Service Benefit state
    var serviceBasicInput by rememberSaveable { mutableStateOf("") }
    var serviceYearsInput by rememberSaveable { mutableStateOf("5") }
    var serviceElDaysInput by rememberSaveable { mutableStateOf("0") }
    var isTermination by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("info_slip_service_card"),
        shape = RoundedCornerShape(spacing.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.cardInnerGap)
        ) {
            InfoSectionHeader(
                icon = Icons.Filled.AccountBalanceWallet,
                title = stringResource(R.string.info_slip_service_title),
                subtitle = stringResource(R.string.info_slip_service_subtitle),
                accentColor = MaterialTheme.colorScheme.tertiary,
                spacing = spacing
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
            ) {
                FilterChip(
                    selected = activeSubTab == 0,
                    onClick = { activeSubTab = 0 },
                    label = { Text(stringResource(R.string.info_subtab_slip_verify)) },
                    modifier = Modifier.testTag("info_subtab_slip_verify"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = activeSubTab == 1,
                    onClick = { activeSubTab = 1 },
                    label = { Text(stringResource(R.string.info_subtab_service_benefit)) },
                    modifier = Modifier.testTag("info_subtab_service_benefit"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }

            if (activeSubTab == 0) {
                val context = LocalContext.current
                OutlinedButton(
                    onClick = {
                        val basic = summary.basicSalary.takeIf { it > 0.0 } ?: profile.basicSalary
                        if (basic <= 0.0 && summary.totalOtHours <= 0.0) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.info_calc_no_profile_salary),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            slipBasicInput = LaborLawCalculator.formatEditableNumber(basic)
                            slipOtHoursInput = LaborLawCalculator.formatEditableNumber(summary.totalOtHours)
                            val bonus = if (summary.isBonusCancelledDueToAbsent || summary.isBonusCancelledDueToLeave) {
                                0.0
                            } else {
                                summary.attendanceBonus
                            }
                            slipBonusInput = LaborLawCalculator.formatEditableNumber(bonus)
                            val totalDed = summary.absentDeduction + summary.advanceDeduction + summary.aponBazarDeduction
                            slipDeductionsInput = LaborLawCalculator.formatEditableNumber(totalDed)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("info_slip_load_month_btn"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(spacing.iconSm))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.info_slip_load_month_btn))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
                ) {
                    OutlinedTextField(
                        value = slipBasicInput,
                        onValueChange = { slipBasicInput = LaborLawCalculator.normalizeNumericInput(it) },
                        label = { Text(stringResource(R.string.info_calc_basic_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1.2f).testTag("info_slip_basic_input")
                    )
                    OutlinedTextField(
                        value = slipOtHoursInput,
                        onValueChange = { slipOtHoursInput = LaborLawCalculator.normalizeNumericInput(it) },
                        label = { Text(stringResource(R.string.info_calc_ot_hours_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("info_slip_ot_hours_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
                ) {
                    OutlinedTextField(
                        value = slipBonusInput,
                        onValueChange = { slipBonusInput = LaborLawCalculator.normalizeNumericInput(it) },
                        label = { Text(stringResource(R.string.info_slip_bonus_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("info_slip_bonus_input")
                    )
                    OutlinedTextField(
                        value = slipDeductionsInput,
                        onValueChange = { slipDeductionsInput = LaborLawCalculator.normalizeNumericInput(it) },
                        label = { Text(stringResource(R.string.info_slip_deductions_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("info_slip_deductions_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
                ) {
                    OutlinedTextField(
                        value = slipPaidOtInput,
                        onValueChange = { slipPaidOtInput = LaborLawCalculator.normalizeNumericInput(it) },
                        label = { Text(stringResource(R.string.info_slip_paid_ot_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("info_slip_paid_ot_input")
                    )
                    OutlinedTextField(
                        value = slipPaidNetInput,
                        onValueChange = { slipPaidNetInput = LaborLawCalculator.normalizeNumericInput(it) },
                        label = { Text(stringResource(R.string.info_slip_paid_net_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("info_slip_paid_net_input")
                    )
                }

                val comparison = remember(
                    slipBasicInput,
                    slipOtHoursInput,
                    slipBonusInput,
                    slipDeductionsInput,
                    slipPaidOtInput,
                    slipPaidNetInput
                ) {
                    InfoHubCalculator.comparePaySlip(
                        basicSalary = LaborLawCalculator.parseLocalizedDouble(slipBasicInput, 0.0),
                        grossSalary = 0.0,
                        otHours = LaborLawCalculator.parseLocalizedDouble(slipOtHoursInput, 0.0),
                        bonusAmount = LaborLawCalculator.parseLocalizedDouble(slipBonusInput, 0.0),
                        deductionsAmount = LaborLawCalculator.parseLocalizedDouble(slipDeductionsInput, 0.0),
                        paidOtAmount = LaborLawCalculator.parseLocalizedDouble(slipPaidOtInput, 0.0),
                        paidNetSalary = LaborLawCalculator.parseLocalizedDouble(slipPaidNetInput, 0.0)
                    )
                }

                val statusTint = when (comparison.status) {
                    PaySlipMatchStatus.MATCH -> SuccessGreen
                    PaySlipMatchStatus.UNDERPAID -> MaterialTheme.colorScheme.error
                    PaySlipMatchStatus.OVERPAID -> NightBlue
                    PaySlipMatchStatus.EMPTY -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(spacing.subCardRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, statusTint.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.subCardPadding),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricColumn(
                                label = stringResource(R.string.info_slip_expected_ot_label),
                                value = LaborLawCalculator.formatCurrency(comparison.expectedOtAmount, isBengali),
                                valueColor = NightBlue,
                                modifier = Modifier.weight(1f)
                            )
                            MetricColumn(
                                label = stringResource(R.string.info_slip_expected_net_label),
                                value = LaborLawCalculator.formatCurrency(comparison.expectedNetPayable, isBengali),
                                valueColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = when (comparison.status) {
                                    PaySlipMatchStatus.MATCH -> Icons.Filled.CheckCircle
                                    PaySlipMatchStatus.UNDERPAID -> Icons.Filled.Warning
                                    else -> Icons.Filled.Info
                                },
                                contentDescription = null,
                                tint = statusTint,
                                modifier = Modifier.size(spacing.iconMd)
                            )
                            Text(
                                text = when (comparison.status) {
                                    PaySlipMatchStatus.MATCH -> stringResource(R.string.info_slip_status_match)
                                    PaySlipMatchStatus.UNDERPAID -> stringResource(
                                        R.string.info_slip_status_underpaid_fmt,
                                        LaborLawCalculator.formatCurrency(abs(comparison.differenceAmount), isBengali)
                                    )
                                    PaySlipMatchStatus.OVERPAID -> stringResource(
                                        R.string.info_slip_status_overpaid_fmt,
                                        LaborLawCalculator.formatCurrency(abs(comparison.differenceAmount), isBengali)
                                    )
                                    PaySlipMatchStatus.EMPTY -> stringResource(R.string.info_slip_status_hint)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = statusTint
                            )
                        }

                        if (abs(comparison.otDifferenceAmount) > 0.5 && LaborLawCalculator.parseLocalizedDouble(slipPaidOtInput) > 0.0) {
                            val sign = if (comparison.otDifferenceAmount >= 0) "+" else "-"
                            Text(
                                text = stringResource(
                                    R.string.info_slip_ot_diff_fmt,
                                    "$sign${LaborLawCalculator.formatCurrency(abs(comparison.otDifferenceAmount), isBengali)}"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // Service Benefit & Earned Leave Encashment Calculator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
                ) {
                    FilterChip(
                        selected = !isTermination,
                        onClick = { isTermination = false },
                        label = { Text(stringResource(R.string.info_service_resignation)) },
                        modifier = Modifier.testTag("info_service_resignation_chip")
                    )
                    FilterChip(
                        selected = isTermination,
                        onClick = { isTermination = true },
                        label = { Text(stringResource(R.string.info_service_termination)) },
                        modifier = Modifier.testTag("info_service_termination_chip")
                    )
                }

                val defaultBasic = summary.basicSalary.takeIf { it > 0.0 } ?: profile.basicSalary
                val effectiveServiceBasicStr = if (serviceBasicInput.isEmpty() && defaultBasic > 0.0) {
                    LaborLawCalculator.formatEditableNumber(defaultBasic)
                } else {
                    serviceBasicInput
                }

                OutlinedTextField(
                    value = effectiveServiceBasicStr,
                    onValueChange = { serviceBasicInput = LaborLawCalculator.normalizeNumericInput(it) },
                    label = { Text(stringResource(R.string.info_calc_basic_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("info_service_basic_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
                ) {
                    OutlinedTextField(
                        value = serviceYearsInput,
                        onValueChange = { serviceYearsInput = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = false) },
                        label = { Text(stringResource(R.string.info_service_years_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("info_service_years_input")
                    )
                    OutlinedTextField(
                        value = serviceElDaysInput,
                        onValueChange = { serviceElDaysInput = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true) },
                        label = { Text(stringResource(R.string.info_service_el_days_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("info_service_el_days_input")
                    )
                }

                val years = LaborLawCalculator.parseLocalizedInt(serviceYearsInput, 0)
                val elDays = LaborLawCalculator.parseLocalizedDouble(serviceElDaysInput, 0.0)
                val basicVal = LaborLawCalculator.parseLocalizedDouble(effectiveServiceBasicStr, 0.0)
                val separationType = if (isTermination) SeparationType.TERMINATION else SeparationType.RESIGNATION

                val serviceResult = remember(basicVal, years, elDays, separationType) {
                    InfoHubCalculator.calculateServiceBenefit(
                        basicSalary = basicVal,
                        grossSalary = 0.0,
                        completedYears = years,
                        earnedLeaveDays = elDays,
                        separationType = separationType
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(spacing.subCardRadius),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.subCardPadding),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.info_service_rate_per_year_fmt, serviceResult.benefitDaysPerYear),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricColumn(
                                label = stringResource(R.string.info_service_benefit_amount_label),
                                value = LaborLawCalculator.formatCurrency(serviceResult.serviceBenefitAmount, isBengali),
                                valueColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            MetricColumn(
                                label = stringResource(R.string.info_service_el_encashment_label),
                                value = LaborLawCalculator.formatCurrency(serviceResult.earnedLeaveEncashment, isBengali),
                                valueColor = NightBlue,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        MetricColumn(
                            label = stringResource(R.string.info_service_total_receivable_label),
                            value = LaborLawCalculator.formatCurrency(serviceResult.totalEstimatedReceivable, isBengali),
                            valueColor = SuccessGreen,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = if (!isTermination && years in 1..4) {
                                stringResource(R.string.info_service_under_5y_note)
                            } else {
                                stringResource(R.string.info_service_notice_hint_fmt, serviceResult.noticePeriodDays)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    spacing: AppSpacing
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = accentColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(spacing.iconMd)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
