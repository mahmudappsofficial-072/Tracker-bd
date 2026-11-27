package com.example.util

import androidx.annotation.StringRes
import com.example.R
import kotlin.math.abs

enum class InfoHubWorkspace {
    HUB,
    CALCULATOR,
    HELPLINES,
    GRADES,
    LABOR_LAW,
    SLIP_AND_SERVICE
}

enum class InfoHubSectionFilter {
    ALL,
    CALCULATOR,
    HELPLINES,
    GRADES,
    LABOR_LAW,
    SLIP_AND_SERVICE
}

enum class HelplineCategory {
    LABOR,
    LEGAL,
    EMERGENCY,
    HEALTH
}

enum class HelplineFilterGroup {
    ALL,
    LABOR,
    LEGAL,
    EMERGENCY_HEALTH
}

data class WorkerHelpline(
    val id: String,
    val dialNumber: String,
    val category: HelplineCategory,
    @StringRes val titleRes: Int,
    @StringRes val departmentRes: Int,
    @StringRes val descriptionRes: Int,
    val isTollFree: Boolean,
    val is24x7: Boolean
)

data class RmgGradeInfo(
    val gradeNumber: Int,
    @StringRes val titleRes: Int,
    @StringRes val rolesRes: Int,
    val basicSalary: Double,
    val houseRent: Double,
    val medicalAllowance: Double,
    val conveyanceAllowance: Double,
    val foodAllowance: Double,
    val grossSalary: Double,
    val otHourlyRate: Double
)

data class LaborLawTopic(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val sectionRefRes: Int,
    @StringRes val summaryRes: Int,
    @StringRes val pointsRes: List<Int>
)

enum class SeparationType {
    RESIGNATION,
    TERMINATION
}

enum class PaySlipMatchStatus {
    EMPTY,
    MATCH,
    UNDERPAID,
    OVERPAID
}

data class PaySlipComparisonResult(
    val expectedHourlyOtRate: Double,
    val expectedOtAmount: Double,
    val expectedGross: Double,
    val expectedNetPayable: Double,
    val paidNetAmount: Double,
    val differenceAmount: Double, // paidNet - expectedNet (negative means underpaid)
    val otDifferenceAmount: Double, // paidOt - expectedOt
    val status: PaySlipMatchStatus
)

data class ServiceBenefitResult(
    val dailyBasicRate: Double,
    val dailyGrossRate: Double,
    val benefitDaysPerYear: Int,
    val serviceBenefitAmount: Double,
    val earnedLeaveEncashment: Double,
    val totalEstimatedReceivable: Double,
    val noticePeriodDays: Int
)

object RmgInfoCatalog {

    val helplines: List<WorkerHelpline> = listOf(
        WorkerHelpline(
            id = "dife_16357",
            dialNumber = "16357",
            category = HelplineCategory.LABOR,
            titleRes = R.string.helpline_dife_title,
            departmentRes = R.string.helpline_dife_dept,
            descriptionRes = R.string.helpline_dife_desc,
            isTollFree = true,
            is24x7 = true
        ),
        WorkerHelpline(
            id = "nlaso_16430",
            dialNumber = "16430",
            category = HelplineCategory.LEGAL,
            titleRes = R.string.helpline_legal_title,
            departmentRes = R.string.helpline_legal_dept,
            descriptionRes = R.string.helpline_legal_desc,
            isTollFree = true,
            is24x7 = false
        ),
        WorkerHelpline(
            id = "emergency_999",
            dialNumber = "999",
            category = HelplineCategory.EMERGENCY,
            titleRes = R.string.helpline_999_title,
            departmentRes = R.string.helpline_999_dept,
            descriptionRes = R.string.helpline_999_desc,
            isTollFree = true,
            is24x7 = true
        ),
        WorkerHelpline(
            id = "fire_16163",
            dialNumber = "16163",
            category = HelplineCategory.EMERGENCY,
            titleRes = R.string.helpline_fire_title,
            departmentRes = R.string.helpline_fire_dept,
            descriptionRes = R.string.helpline_fire_desc,
            isTollFree = false,
            is24x7 = true
        ),
        WorkerHelpline(
            id = "women_109",
            dialNumber = "109",
            category = HelplineCategory.LEGAL,
            titleRes = R.string.helpline_109_title,
            departmentRes = R.string.helpline_109_dept,
            descriptionRes = R.string.helpline_109_desc,
            isTollFree = true,
            is24x7 = true
        ),
        WorkerHelpline(
            id = "health_16263",
            dialNumber = "16263",
            category = HelplineCategory.HEALTH,
            titleRes = R.string.helpline_health_title,
            departmentRes = R.string.helpline_health_dept,
            descriptionRes = R.string.helpline_health_desc,
            isTollFree = false,
            is24x7 = true
        ),
        WorkerHelpline(
            id = "national_333",
            dialNumber = "333",
            category = HelplineCategory.LABOR,
            titleRes = R.string.helpline_333_title,
            departmentRes = R.string.helpline_333_dept,
            descriptionRes = R.string.helpline_333_desc,
            isTollFree = false,
            is24x7 = true
        )
    )

    val rmgGrades: List<RmgGradeInfo> = listOf(
        createGrade(1, R.string.rmg_grade_1_title, R.string.rmg_grade_1_roles, 8390.0),
        createGrade(2, R.string.rmg_grade_2_title, R.string.rmg_grade_2_roles, 7882.0),
        createGrade(3, R.string.rmg_grade_3_title, R.string.rmg_grade_3_roles, 7400.0),
        createGrade(4, R.string.rmg_grade_4_title, R.string.rmg_grade_4_roles, 6700.0)
    )

    private fun createGrade(
        number: Int,
        @StringRes titleRes: Int,
        @StringRes rolesRes: Int,
        basic: Double
    ): RmgGradeInfo {
        val calc = LaborLawCalculator.calculateAutoAllowances(basic)
        return RmgGradeInfo(
            gradeNumber = number,
            titleRes = titleRes,
            rolesRes = rolesRes,
            basicSalary = calc.basicSalary,
            houseRent = calc.houseRent,
            medicalAllowance = calc.medicalAllowance,
            conveyanceAllowance = calc.conveyanceAllowance,
            foodAllowance = calc.foodAllowance,
            grossSalary = calc.grossSalary,
            otHourlyRate = calc.otHourlyRate
        )
    }

    val laborLawTopics: List<LaborLawTopic> = listOf(
        LaborLawTopic(
            id = "ot_hours",
            titleRes = R.string.law_topic_ot_title,
            sectionRefRes = R.string.law_topic_ot_ref,
            summaryRes = R.string.law_topic_ot_summary,
            pointsRes = listOf(
                R.string.law_topic_ot_p1,
                R.string.law_topic_ot_p2,
                R.string.law_topic_ot_p3
            )
        ),
        LaborLawTopic(
            id = "leaves",
            titleRes = R.string.law_topic_leave_title,
            sectionRefRes = R.string.law_topic_leave_ref,
            summaryRes = R.string.law_topic_leave_summary,
            pointsRes = listOf(
                R.string.law_topic_leave_p1,
                R.string.law_topic_leave_p2,
                R.string.law_topic_leave_p3,
                R.string.law_topic_leave_p4
            )
        ),
        LaborLawTopic(
            id = "maternity",
            titleRes = R.string.law_topic_maternity_title,
            sectionRefRes = R.string.law_topic_maternity_ref,
            summaryRes = R.string.law_topic_maternity_summary,
            pointsRes = listOf(
                R.string.law_topic_maternity_p1,
                R.string.law_topic_maternity_p2,
                R.string.law_topic_maternity_p3
            )
        ),
        LaborLawTopic(
            id = "bonus",
            titleRes = R.string.law_topic_bonus_title,
            sectionRefRes = R.string.law_topic_bonus_ref,
            summaryRes = R.string.law_topic_bonus_summary,
            pointsRes = listOf(
                R.string.law_topic_bonus_p1,
                R.string.law_topic_bonus_p2,
                R.string.law_topic_bonus_p3
            )
        ),
        LaborLawTopic(
            id = "notice_separation",
            titleRes = R.string.law_topic_notice_title,
            sectionRefRes = R.string.law_topic_notice_ref,
            summaryRes = R.string.law_topic_notice_summary,
            pointsRes = listOf(
                R.string.law_topic_notice_p1,
                R.string.law_topic_notice_p2,
                R.string.law_topic_notice_p3
            )
        )
    )
}

object InfoHubCalculator {

    fun comparePaySlip(
        basicSalary: Double,
        grossSalary: Double,
        otHours: Double,
        bonusAmount: Double,
        deductionsAmount: Double,
        paidOtAmount: Double,
        paidNetSalary: Double,
        customOtRate: Double? = null
    ): PaySlipComparisonResult {
        val safeBasic = basicSalary.coerceAtLeast(0.0)
        val safeHours = otHours.coerceAtLeast(0.0)
        val safeBonus = bonusAmount.coerceAtLeast(0.0)
        val safeDeductions = deductionsAmount.coerceAtLeast(0.0)
        val resolvedGross = if (grossSalary > 0.0) {
            grossSalary
        } else if (safeBasic > 0.0) {
            LaborLawCalculator.calculateAutoAllowances(safeBasic).grossSalary
        } else {
            0.0
        }

        val hourlyOtRate = if (customOtRate != null && customOtRate > 0.0) {
            customOtRate
        } else if (safeBasic > 0.0) {
            (safeBasic / LaborLawCalculator.STANDARD_MONTHLY_HOURS) * 2.0
        } else {
            0.0
        }

        val expectedOt = hourlyOtRate * safeHours
        val expectedNet = (resolvedGross + expectedOt + safeBonus - safeDeductions).coerceAtLeast(0.0)

        if (resolvedGross <= 0.0 && expectedOt <= 0.0 && paidNetSalary <= 0.0 && paidOtAmount <= 0.0) {
            return PaySlipComparisonResult(
                expectedHourlyOtRate = hourlyOtRate,
                expectedOtAmount = 0.0,
                expectedGross = 0.0,
                expectedNetPayable = 0.0,
                paidNetAmount = 0.0,
                differenceAmount = 0.0,
                otDifferenceAmount = 0.0,
                status = PaySlipMatchStatus.EMPTY
            )
        }

        val otDiff = if (paidOtAmount > 0.0) paidOtAmount - expectedOt else 0.0
        val effectivePaidNet = if (paidNetSalary > 0.0) {
            paidNetSalary
        } else if (paidOtAmount > 0.0) {
            (resolvedGross + paidOtAmount + safeBonus - safeDeductions).coerceAtLeast(0.0)
        } else {
            0.0
        }

        val diff = if (effectivePaidNet > 0.0) effectivePaidNet - expectedNet else 0.0
        val status = when {
            effectivePaidNet <= 0.0 && paidOtAmount <= 0.0 -> PaySlipMatchStatus.EMPTY
            abs(diff) <= 5.0 -> PaySlipMatchStatus.MATCH
            diff < 0.0 -> PaySlipMatchStatus.UNDERPAID
            else -> PaySlipMatchStatus.OVERPAID
        }

        return PaySlipComparisonResult(
            expectedHourlyOtRate = hourlyOtRate,
            expectedOtAmount = expectedOt,
            expectedGross = resolvedGross,
            expectedNetPayable = expectedNet,
            paidNetAmount = effectivePaidNet,
            differenceAmount = diff,
            otDifferenceAmount = otDiff,
            status = status
        )
    }

    /**
     * Bangladesh Labour Act 2006 (Sections 26 & 27):
     * - Resignation: 5 to <10 completed years = 14 days basic wages per year; >=10 years = 30 days basic wages per year; <5 years = 0.
     * - Termination by Employer / Retrenchment (>=1 completed year): 30 days basic wages per year.
     * - Earned Leave Encashment: remaining EL days * (daily wage based on gross / 30).
     */
    fun calculateServiceBenefit(
        basicSalary: Double,
        grossSalary: Double,
        completedYears: Int,
        earnedLeaveDays: Double,
        separationType: SeparationType
    ): ServiceBenefitResult {
        val safeBasic = basicSalary.coerceAtLeast(0.0)
        val resolvedGross = if (grossSalary > 0.0) {
            grossSalary
        } else if (safeBasic > 0.0) {
            LaborLawCalculator.calculateAutoAllowances(safeBasic).grossSalary
        } else {
            0.0
        }
        val safeYears = completedYears.coerceAtLeast(0)
        val safeElDays = earnedLeaveDays.coerceAtLeast(0.0)

        val dailyBasic = safeBasic / 30.0
        val dailyGross = resolvedGross / 30.0

        val daysPerYear = when (separationType) {
            SeparationType.RESIGNATION -> when {
                safeYears >= 10 -> 30
                safeYears >= 5 -> 14
                else -> 0
            }
            SeparationType.TERMINATION -> if (safeYears >= 1) 30 else 0
        }

        val serviceBenefit = dailyBasic * daysPerYear * safeYears
        val elEncashment = dailyGross * safeElDays
        val total = serviceBenefit + elEncashment
        val noticeDays = if (separationType == SeparationType.RESIGNATION) 60 else 30

        return ServiceBenefitResult(
            dailyBasicRate = dailyBasic,
            dailyGrossRate = dailyGross,
            benefitDaysPerYear = daysPerYear,
            serviceBenefitAmount = serviceBenefit,
            earnedLeaveEncashment = elEncashment,
            totalEstimatedReceivable = total,
            noticePeriodDays = noticeDays
        )
    }

    fun filterHelplines(group: HelplineFilterGroup): List<WorkerHelpline> {
        return when (group) {
            HelplineFilterGroup.ALL -> RmgInfoCatalog.helplines
            HelplineFilterGroup.LABOR -> RmgInfoCatalog.helplines.filter { it.category == HelplineCategory.LABOR }
            HelplineFilterGroup.LEGAL -> RmgInfoCatalog.helplines.filter { it.category == HelplineCategory.LEGAL }
            HelplineFilterGroup.EMERGENCY_HEALTH -> RmgInfoCatalog.helplines.filter {
                it.category == HelplineCategory.EMERGENCY || it.category == HelplineCategory.HEALTH
            }
        }
    }

    fun matchesLaborLawSearch(
        searchableChunks: List<String>,
        query: String
    ): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        val normalizedQuery = LaborLawCalculator.toEnglishDigits(trimmed).lowercase()
        return searchableChunks.any { chunk ->
            val normalizedChunk = LaborLawCalculator.toEnglishDigits(chunk).lowercase()
            normalizedChunk.contains(normalizedQuery)
        }
    }
}

object InfoHubShareFormatter {

    fun formatQuickOtSummary(
        isBengali: Boolean,
        basicSalary: Double,
        grossSalary: Double,
        otHours: Double,
        otRate: Double,
        otTotal: Double,
        grandTotal: Double
    ): String {
        val hrsText = LaborLawCalculator.formatEditableNumber(otHours).let {
            if (isBengali) LaborLawCalculator.toBengaliDigits(it) else it
        }
        return if (isBengali) {
            buildString {
                appendLine("কুইক ওটি ও মজুরি হিসাব")
                appendLine("--------------------")
                appendLine("মূল বেতন (বেসিক): ${LaborLawCalculator.formatCurrency(basicSalary, true)}")
                appendLine("মোট গ্রস বেতন: ${LaborLawCalculator.formatCurrency(grossSalary, true)}")
                appendLine("ওটি রেট/ঘণ্টা: ${LaborLawCalculator.formatCurrency(otRate, true)}")
                appendLine("ওটি সময়: $hrsText ঘণ্টা")
                appendLine("মোট ওটি আয়: ${LaborLawCalculator.formatCurrency(otTotal, true)}")
                append("গ্রস + ওটি মোট: ${LaborLawCalculator.formatCurrency(grandTotal, true)}")
            }
        } else {
            buildString {
                appendLine("Quick OT & Wage Summary")
                appendLine("--------------------")
                appendLine("Basic Salary: ${LaborLawCalculator.formatCurrency(basicSalary, false)}")
                appendLine("Gross Salary: ${LaborLawCalculator.formatCurrency(grossSalary, false)}")
                appendLine("Legal OT Rate/hr: ${LaborLawCalculator.formatCurrency(otRate, false)}")
                appendLine("OT Hours: ${hrsText}h")
                appendLine("Total OT Earnings: ${LaborLawCalculator.formatCurrency(otTotal, false)}")
                append("Gross + OT Total: ${LaborLawCalculator.formatCurrency(grandTotal, false)}")
            }
        }
    }

    fun formatPaySlipSummary(
        isBengali: Boolean,
        basicSalary: Double,
        otHours: Double,
        comparison: PaySlipComparisonResult
    ): String {
        val hrsText = LaborLawCalculator.formatEditableNumber(otHours).let {
            if (isBengali) LaborLawCalculator.toBengaliDigits(it) else it
        }
        val statusLine = when (comparison.status) {
            PaySlipMatchStatus.MATCH -> if (isBengali) "অবস্থা: হিসাব সঠিক আছে" else "Status: Matches Expected Amount"
            PaySlipMatchStatus.UNDERPAID -> {
                val diff = LaborLawCalculator.formatCurrency(abs(comparison.differenceAmount), isBengali)
                if (isBengali) "অবস্থা: প্রাপ্যের চেয়ে $diff কম দেওয়া হয়েছে" else "Status: Short by $diff vs Expected"
            }
            PaySlipMatchStatus.OVERPAID -> {
                val diff = LaborLawCalculator.formatCurrency(abs(comparison.differenceAmount), isBengali)
                if (isBengali) "অবস্থা: প্রাপ্যের চেয়ে $diff বেশি হয়েছে" else "Status: Higher by $diff vs Expected"
            }
            PaySlipMatchStatus.EMPTY -> if (isBengali) "অবস্থা: তুলনামূলক তথ্য অসম্পূর্ণ" else "Status: Pending Paid Amount"
        }
        return if (isBengali) {
            buildString {
                appendLine("বেতন-স্লিপ যাচাই সারাংশ")
                appendLine("--------------------")
                appendLine("মূল বেতন: ${LaborLawCalculator.formatCurrency(basicSalary, true)}")
                appendLine("মোট ওটি: $hrsText ঘণ্টা")
                appendLine("প্রাপ্য ওটি: ${LaborLawCalculator.formatCurrency(comparison.expectedOtAmount, true)}")
                appendLine("প্রাপ্য নিট বেতন: ${LaborLawCalculator.formatCurrency(comparison.expectedNetPayable, true)}")
                if (comparison.paidNetAmount > 0.0) {
                    appendLine("কারখানা প্রদত্ত নিট: ${LaborLawCalculator.formatCurrency(comparison.paidNetAmount, true)}")
                }
                append(statusLine)
            }
        } else {
            buildString {
                appendLine("Pay Slip Audit Summary")
                appendLine("--------------------")
                appendLine("Basic Salary: ${LaborLawCalculator.formatCurrency(basicSalary, false)}")
                appendLine("OT Hours: ${hrsText}h")
                appendLine("Expected Legal OT: ${LaborLawCalculator.formatCurrency(comparison.expectedOtAmount, false)}")
                appendLine("Expected Net Payable: ${LaborLawCalculator.formatCurrency(comparison.expectedNetPayable, false)}")
                if (comparison.paidNetAmount > 0.0) {
                    appendLine("Factory Paid Net: ${LaborLawCalculator.formatCurrency(comparison.paidNetAmount, false)}")
                }
                append(statusLine)
            }
        }
    }
}

