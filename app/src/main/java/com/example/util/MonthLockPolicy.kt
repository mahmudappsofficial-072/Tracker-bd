package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MonthLockPolicy {

    const val GRACE_PERIOD_MAX_DAY = 5

    fun getPreviousCalendarYearMonth(referenceDate: Date = Date()): String {
        val cal = Calendar.getInstance().apply {
            time = referenceDate
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -1)
        }
        return SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
    }

    fun getLastDateOfMonth(yearMonth: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
            val parsed = sdf.parse(yearMonth) ?: return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val cal = Calendar.getInstance().apply {
                time = parsed
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        } catch (_: Exception) {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        }
    }

    fun getDefaultDateForMonth(yearMonth: String, referenceDate: Date = Date()): String {
        val currentYm = SimpleDateFormat("yyyy-MM", Locale.US).format(referenceDate)
        return if (yearMonth == currentYm) {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(referenceDate)
        } else {
            getLastDateOfMonth(yearMonth)
        }
    }

    fun isWithinPreviousMonthGracePeriod(yearMonth: String, referenceDate: Date = Date()): Boolean {
        val cal = Calendar.getInstance().apply { time = referenceDate }
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        if (dayOfMonth > GRACE_PERIOD_MAX_DAY) return false
        return yearMonth == getPreviousCalendarYearMonth(referenceDate)
    }

    fun resolveIsMonthLocked(
        yearMonth: String,
        monthSnapshotLocked: Boolean?,
        referenceDate: Date = Date()
    ): Boolean {
        val currentYm = SimpleDateFormat("yyyy-MM", Locale.US).format(referenceDate)
        if (yearMonth >= currentYm) return false
        if (monthSnapshotLocked != null) return monthSnapshotLocked
        return !isWithinPreviousMonthGracePeriod(yearMonth, referenceDate)
    }
}
