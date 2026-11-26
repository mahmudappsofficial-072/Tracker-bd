package com.example.receiver

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ReminderTriggerPlan(
    val triggerAtMillis: Long,
    val scheduledForDate: String
)

data class ReminderDeliveryDecision(
    val shouldShowNotification: Boolean,
    val skipReason: String? = null,
    val rescheduleForceTomorrow: Boolean = true
)

object ReminderSchedulePolicy {

    const val FIRST_REMINDER_HOUR = 19
    const val FIRST_REMINDER_MINUTE = 15
    const val SECOND_REMINDER_HOUR = 21
    const val SECOND_REMINDER_MINUTE = 30

    const val SKIP_REASON_ENTRY_EXISTS_TODAY = "ENTRY_EXISTS_TODAY"
    const val SKIP_REASON_STALE_DATE = "STALE_DATE"

    fun formatDate(timeMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            this.timeZone = timeZone
        }
        return sdf.format(Date(timeMillis))
    }

    fun computeTriggerPlan(
        hour: Int,
        minute: Int,
        forceTomorrow: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): ReminderTriggerPlan {
        val triggerCalendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (forceTomorrow || timeInMillis <= nowMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return ReminderTriggerPlan(
            triggerAtMillis = triggerCalendar.timeInMillis,
            scheduledForDate = formatDate(triggerCalendar.timeInMillis, timeZone)
        )
    }

    fun evaluateDelivery(
        hasTodayEntry: Boolean,
        scheduledForDate: String?,
        todayDate: String
    ): ReminderDeliveryDecision {
        if (!scheduledForDate.isNullOrBlank() && scheduledForDate != todayDate) {
            return ReminderDeliveryDecision(
                shouldShowNotification = false,
                skipReason = SKIP_REASON_STALE_DATE,
                rescheduleForceTomorrow = hasTodayEntry
            )
        }
        if (hasTodayEntry) {
            return ReminderDeliveryDecision(
                shouldShowNotification = false,
                skipReason = SKIP_REASON_ENTRY_EXISTS_TODAY,
                rescheduleForceTomorrow = true
            )
        }
        return ReminderDeliveryDecision(
            shouldShowNotification = true,
            skipReason = null,
            rescheduleForceTomorrow = true
        )
    }
}
