package com.example.util

import com.example.data.model.UserProfile
import java.text.DecimalFormat
import java.util.Locale

object LaborLawCalculator {

    const val STANDARD_MONTHLY_HOURS = 208.0 // 26 days * 8 hours

    /**
     * Calculate hourly overtime rate according to Bangladesh Labor Act 2006 (Section 108)
     * Regular hourly rate = Basic / 208
     * Overtime hourly rate = (Basic / 208) * 2 = Basic / 104
     */
    fun getHourlyOtRate(profile: UserProfile): Double {
        return if (profile.isCustomSalary) {
            if (profile.customOtRate > 0.0) profile.customOtRate else 0.0
        } else {
            (profile.basicSalary / STANDARD_MONTHLY_HOURS) * 2.0
        }
    }

    /**
     * Standard RMG minimum wage gazette breakdown from Basic Salary
     */
    fun calculateAutoAllowances(basicSalary: Double): AutoAllowanceResult {
        val houseRent = basicSalary * 0.50
        val medical = 750.0 // Standard 750.0 Tk as per BD RMG Labor Gazette
        val conveyance = if (basicSalary > 0.0) 450.0 else 0.0
        val food = if (basicSalary > 0.0) 1250.0 else 0.0
        val gross = if (basicSalary > 0.0) (basicSalary + houseRent + medical + conveyance + food) else 0.0
        val otRate = if (basicSalary > 0.0) ((basicSalary / STANDARD_MONTHLY_HOURS) * 2.0) else 0.0

        return AutoAllowanceResult(
            basicSalary = basicSalary,
            houseRent = houseRent,
            medicalAllowance = medical,
            conveyanceAllowance = conveyance,
            foodAllowance = food,
            grossSalary = gross,
            otHourlyRate = otRate
        )
    }

    /**
     * Standard RMG minimum wage gazette breakdown from Gross Salary:
     * Fixed Allowances = Medical (750) + Conveyance (450) + Food (1250) = 2450
     * Gross = Basic + (Basic * 0.50) + 2450 = 1.5 * Basic + 2450
     * Basic = (Gross - 2450) / 1.5
     */
    fun calculateFromGrossSalary(grossSalary: Double): AutoAllowanceResult {
        if (grossSalary <= 0.0) {
            return AutoAllowanceResult(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }
        val fixedAllowances = 2450.0 // 750 + 450 + 1250
        val basic = if (grossSalary > fixedAllowances) {
            (grossSalary - fixedAllowances) / 1.5
        } else {
            grossSalary / 1.5
        }
        val houseRent = basic * 0.50
        val medical = 750.0
        val conveyance = 450.0
        val food = 1250.0
        val otRate = (basic / STANDARD_MONTHLY_HOURS) * 2.0

        return AutoAllowanceResult(
            basicSalary = basic,
            houseRent = houseRent,
            medicalAllowance = medical,
            conveyanceAllowance = conveyance,
            foodAllowance = food,
            grossSalary = grossSalary,
            otHourlyRate = otRate
        )
    }

    /**
     * Calculate OT amount for given hours and rate
     */
    fun calculateOtAmount(hours: Double, hourlyRate: Double): Double {
        return hours * hourlyRate
    }

    /**
     * Format number to Bengali or English string with currency symbol
     */
    fun formatCurrency(amount: Double, isBengali: Boolean = true): String {
        val df = DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols(Locale.US))
        val formatted = df.format(amount)
        val withSymbol = "৳ $formatted"
        return if (isBengali) toBengaliDigits(withSymbol) else withSymbol
    }

    fun formatNumber(number: Double, isBengali: Boolean = true, decimalPlaces: Int = 1): String {
        val pattern = if (decimalPlaces == 0) "#,##0" else "#,##0.${"#".repeat(decimalPlaces)}"
        val df = DecimalFormat(pattern, java.text.DecimalFormatSymbols(Locale.US))
        val formatted = df.format(number)
        return if (isBengali) toBengaliDigits(formatted) else formatted
    }

    fun toBengaliDigits(input: String): String {
        val banglaDigits = mapOf(
            '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
            '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯'
        )
        val sb = StringBuilder()
        for (ch in input) {
            sb.append(banglaDigits[ch] ?: ch)
        }
        return sb.toString()
    }

    /**
     * Convert localized numerals (Bengali ০-৯, Arabic-Indic ٠-٩ / ۰-۹, Devanagari ०-९) to English ASCII digits (0-9)
     */
    fun toEnglishDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '০'..'৯' -> sb.append(('0'.code + (ch.code - '০'.code)).toChar())
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                in '०'..'९' -> sb.append(('0'.code + (ch.code - '०'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Safely parse Double from either English or Bengali numeral inputs
     */
    fun parseLocalizedDouble(input: String?, default: Double = 0.0): Double {
        if (input.isNullOrBlank()) return default
        val normalized = toEnglishDigits(input).replace(",", "").trim()
        return normalized.toDoubleOrNull() ?: default
    }

    fun parseLocalizedDoubleOrNull(input: String?): Double? {
        if (input.isNullOrBlank()) return null
        val normalized = toEnglishDigits(input).replace(",", "").trim()
        return normalized.toDoubleOrNull()
    }

    /**
     * Safely parse Int from either English or Bengali numeral inputs
     */
    fun parseLocalizedInt(input: String?, default: Int = 0): Int {
        if (input.isNullOrBlank()) return default
        val normalized = toEnglishDigits(input).replace(",", "").trim()
        return normalized.toIntOrNull() ?: default
    }

    fun parseLocalizedIntOrNull(input: String?): Int? {
        if (input.isNullOrBlank()) return null
        val normalized = toEnglishDigits(input).replace(",", "").trim()
        return normalized.toIntOrNull()
    }

    /**
     * Format a positive number cleanly for editable text fields without trailing .0;
     * returns empty string when value is zero or negative so users never have to erase "0" or "0.0".
     */
    fun formatEditableNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite() || value <= 1e-9) return ""
        val df = DecimalFormat("0.##", java.text.DecimalFormatSymbols(Locale.US))
        val formatted = df.format(value)
        return if (formatted == "0") "" else formatted
    }

    /**
     * Filter numeric text input (supporting English 0-9 and Bengali ০-৯ digits)
     * and collapse redundant leading zeros (e.g. "05" -> "5", "00" -> "0") while preserving "0.5".
     */
    fun normalizeNumericInput(input: String, allowDecimal: Boolean = true): String {
        val sb = StringBuilder()
        var hasDot = false
        for (ch in input) {
            when {
                ch in '0'..'9' || ch in '০'..'৯' -> sb.append(ch)
                allowDecimal && ch == '.' && !hasDot -> {
                    sb.append(ch)
                    hasDot = true
                }
            }
        }
        var cleaned = sb.toString()
        while (cleaned.length > 1 && (cleaned[0] == '0' || cleaned[0] == '০')) {
            val second = cleaned[1]
            if (second == '0' || second == '০' || second in '1'..'9' || second in '১'..'৯') {
                cleaned = cleaned.substring(1)
            } else {
                break
            }
        }
        return cleaned
    }
}

data class AutoAllowanceResult(
    val basicSalary: Double,
    val houseRent: Double,
    val medicalAllowance: Double,
    val conveyanceAllowance: Double,
    val foodAllowance: Double,
    val grossSalary: Double,
    val otHourlyRate: Double
)
