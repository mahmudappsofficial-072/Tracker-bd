package com.example.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.R
import com.example.util.LanguageManager
import java.util.Calendar

object ReminderScheduler {

    private const val TAG = "ReminderScheduler"
    const val CHANNEL_ID = "daily_ot_reminders"
    const val REQUEST_CODE_715 = 1001
    const val REQUEST_CODE_930 = 1002

    fun createNotificationChannel(context: Context, langTag: String? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val localizedCtx = LanguageManager.getLocalizedContext(context, langTag)
            val name = localizedCtx.getString(R.string.notif_channel_daily_name)
            val descriptionText = localizedCtx.getString(R.string.notif_channel_daily_desc)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleDailyReminders(context: Context, langTag: String? = null) {
        createNotificationChannel(context, langTag)
        scheduleSpecificReminder(context, 19, 15, REQUEST_CODE_715, 1)
        scheduleSpecificReminder(context, 21, 30, REQUEST_CODE_930, 2)
    }

    fun scheduleNextReminder(context: Context, type: Int) {
        if (type == 1) {
            scheduleSpecificReminder(context, 19, 15, REQUEST_CODE_715, 1, forceTomorrow = true)
        } else if (type == 2) {
            scheduleSpecificReminder(context, 21, 30, REQUEST_CODE_930, 2, forceTomorrow = true)
        }
    }

    private fun scheduleSpecificReminder(
        context: Context,
        hour: Int,
        minute: Int,
        requestCode: Int,
        type: Int,
        forceTomorrow: Boolean = false
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("REMINDER_TYPE", type)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

        val now = Calendar.getInstance()
        val triggerCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (forceTomorrow || before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerCalendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerCalendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "setAndAllowWhileIdle failed, falling back to set(): ${e.message}")
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerCalendar.timeInMillis,
                    pendingIntent
                )
            } catch (fallbackErr: Exception) {
                Log.w(TAG, "Fallback alarm scheduling failed: ${fallbackErr.message}")
            }
        }
    }
}
