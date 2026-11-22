package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Overtime Tracker BD"

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: ""

        if (body.isNotBlank() || title.isNotBlank()) {
            showNotification(title, body)
        }
    }

    private fun showNotification(title: String, body: String) {
        createFcmChannel(applicationContext)

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getActivity(this, System.currentTimeMillis().toInt(), launchIntent, flags)

        val builder = NotificationCompat.Builder(this, FCM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val notificationId = (System.currentTimeMillis() % 100000).toInt() + 2000
                NotificationManagerCompat.from(this).notify(notificationId, builder.build())
            } catch (e: Exception) {
                Log.w("FCM", "Failed to post FCM notification: ${e.message}")
            }
        }
    }

    companion object {
        const val FCM_CHANNEL_ID = "fcm_announcements_channel"

        fun createFcmChannel(context: Context, langTag: String? = null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val localizedCtx = com.example.util.LanguageManager.getLocalizedContext(context, langTag)
                val name = localizedCtx.getString(R.string.notif_channel_fcm_name)
                val descriptionText = localizedCtx.getString(R.string.notif_channel_fcm_desc)
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(FCM_CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableVibration(true)
                }
                val notificationManager: NotificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}
