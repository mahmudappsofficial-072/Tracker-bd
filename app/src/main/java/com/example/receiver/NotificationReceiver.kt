package com.example.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.util.LanguageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val reminderType = intent?.getIntExtra("REMINDER_TYPE", 1) ?: 1

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profileLang = try {
                    val db = com.example.data.local.AppDatabase.getDatabase(context)
                    db.userProfileDao().getUserProfileOnce()?.language
                } catch (e: Exception) {
                    Log.w("NotificationReceiver", "Failed reading profile language: ${e.message}")
                    null
                }

                val effectiveTag = LanguageManager.getEffectiveLanguageTag(context, profileLang)
                val localizedCtx = LanguageManager.getLocalizedContext(context, effectiveTag)

                ReminderScheduler.createNotificationChannel(context, effectiveTag)

                val (title, body, subText, reqCode, openQuickOt) = when (reminderType) {
                    1 -> Tuple(
                        localizedCtx.getString(R.string.notif_reminder_1_title),
                        localizedCtx.getString(R.string.notif_reminder_1_body),
                        localizedCtx.getString(R.string.notif_reminder_1_subtext),
                        1001,
                        true
                    )
                    else -> Tuple(
                        localizedCtx.getString(R.string.notif_reminder_2_title),
                        localizedCtx.getString(R.string.notif_reminder_2_body),
                        localizedCtx.getString(R.string.notif_reminder_2_subtext),
                        1002,
                        false
                    )
                }

                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    if (openQuickOt) {
                        putExtra("OPEN_QUICK_OT", true)
                    } else {
                        putExtra("OPEN_HOME", true)
                    }
                }

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                val pendingIntent = PendingIntent.getActivity(context, reqCode, launchIntent, flags)

                val builder = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setSubText(subText)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        NotificationManagerCompat.from(context).notify(reqCode, builder.build())
                    } catch (e: Exception) {
                        Log.w("NotificationReceiver", "Failed to show reminder notification: ${e.message}")
                    }
                }

                // Schedule next reminder for the same time tomorrow
                ReminderScheduler.scheduleNextReminder(context, reminderType)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private data class Tuple(
        val title: String,
        val body: String,
        val subText: String,
        val reqCode: Int,
        val openQuickOt: Boolean
    )
}
