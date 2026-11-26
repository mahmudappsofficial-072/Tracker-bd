package com.example.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.R
import com.example.data.local.AppDatabase
import com.example.util.LanguageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ReminderScheduler {

    private const val TAG = "ReminderScheduler"
    const val CHANNEL_ID = "daily_ot_reminders"
    const val REQUEST_CODE_715 = 1001
    const val REQUEST_CODE_930 = 1002
    const val EXTRA_REMINDER_TYPE = "REMINDER_TYPE"
    const val EXTRA_SCHEDULED_FOR_DATE = "SCHEDULED_FOR_DATE"

    private val schedulerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

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

    suspend fun hasEntryForDate(context: Context, date: String): Boolean {
        return try {
            val db = AppDatabase.getDatabase(context.applicationContext)
            db.overtimeDao().hasRecordForDate(date) || db.pieceRateDao().hasPieceRecordForDate(date)
        } catch (e: Exception) {
            Log.w(TAG, "Failed checking entry for date $date: ${e.message}")
            false
        }
    }

    fun scheduleDailyReminders(context: Context, langTag: String? = null) {
        val appContext = context.applicationContext
        createNotificationChannel(appContext, langTag)
        schedulerScope.launch {
            syncSmartDailyRemindersSuspend(appContext, langTag)
        }
    }

    suspend fun syncSmartDailyRemindersSuspend(context: Context, langTag: String? = null) {
        val appContext = context.applicationContext
        syncMutex.withLock {
            createNotificationChannel(appContext, langTag)
            val todayDate = ReminderSchedulePolicy.formatDate(System.currentTimeMillis())
            val hasTodayEntry = hasEntryForDate(appContext, todayDate)

            if (hasTodayEntry) {
                cancelActiveReminderNotifications(appContext)
            }

            scheduleSpecificReminder(
                context = appContext,
                hour = ReminderSchedulePolicy.FIRST_REMINDER_HOUR,
                minute = ReminderSchedulePolicy.FIRST_REMINDER_MINUTE,
                requestCode = REQUEST_CODE_715,
                type = 1,
                forceTomorrow = hasTodayEntry
            )
            scheduleSpecificReminder(
                context = appContext,
                hour = ReminderSchedulePolicy.SECOND_REMINDER_HOUR,
                minute = ReminderSchedulePolicy.SECOND_REMINDER_MINUTE,
                requestCode = REQUEST_CODE_930,
                type = 2,
                forceTomorrow = hasTodayEntry
            )
        }
    }

    fun cancelActiveReminderNotifications(context: Context) {
        try {
            val manager = NotificationManagerCompat.from(context.applicationContext)
            manager.cancel(REQUEST_CODE_715)
            manager.cancel(REQUEST_CODE_930)
        } catch (e: Exception) {
            Log.w(TAG, "Failed cancelling active reminder notifications: ${e.message}")
        }
    }

    fun scheduleNextReminder(context: Context, type: Int, forceTomorrow: Boolean = true) {
        val appContext = context.applicationContext
        if (type == 1) {
            scheduleSpecificReminder(
                context = appContext,
                hour = ReminderSchedulePolicy.FIRST_REMINDER_HOUR,
                minute = ReminderSchedulePolicy.FIRST_REMINDER_MINUTE,
                requestCode = REQUEST_CODE_715,
                type = 1,
                forceTomorrow = forceTomorrow
            )
        } else if (type == 2) {
            scheduleSpecificReminder(
                context = appContext,
                hour = ReminderSchedulePolicy.SECOND_REMINDER_HOUR,
                minute = ReminderSchedulePolicy.SECOND_REMINDER_MINUTE,
                requestCode = REQUEST_CODE_930,
                type = 2,
                forceTomorrow = forceTomorrow
            )
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
        val plan = ReminderSchedulePolicy.computeTriggerPlan(
            hour = hour,
            minute = minute,
            forceTomorrow = forceTomorrow
        )
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_TYPE, type)
            putExtra(EXTRA_SCHEDULED_FOR_DATE, plan.scheduledForDate)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    plan.triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    plan.triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "setAndAllowWhileIdle failed, falling back to set(): ${e.message}")
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    plan.triggerAtMillis,
                    pendingIntent
                )
            } catch (fallbackErr: Exception) {
                Log.w(TAG, "Fallback alarm scheduling failed: ${fallbackErr.message}")
            }
        }
    }
}
