package com.example.resilio.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.resilio.MainActivity
import com.example.resilio.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

object PushNotificationManager {

    const val TOPIC_EMERGENCY_ALERTS = "emergency_alerts"
    const val TOPIC_ANNOUNCEMENTS = "announcements"

    /** Reserved for a future weather advisory feature. */
    private const val TOPIC_WEATHER_ADVISORIES = "weather_advisories"

    const val CHANNEL_EMERGENCY = "emergency_alerts"
    const val CHANNEL_ANNOUNCEMENTS = "announcements"

    const val EXTRA_NOTIFICATION_TYPE = "notification_type"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_EMERGENCY,
                context.getString(R.string.notification_channel_emergency),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_emergency_desc)
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_ANNOUNCEMENTS,
                context.getString(R.string.notification_channel_announcements),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_announcements_desc)
            },
        )

        manager.createNotificationChannels(channels)
    }

    fun registerForPush(context: Context) {
        createChannels(context)
        subscribeToTopics()
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                saveToken(token)
            }
    }

    fun unregisterFromPush() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("fcmToken", com.google.firebase.firestore.FieldValue.delete())
        }

        listOf(TOPIC_EMERGENCY_ALERTS, TOPIC_ANNOUNCEMENTS, TOPIC_WEATHER_ADVISORIES).forEach { topic ->
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
        }
    }

    fun saveToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .update("fcmToken", token)
    }

    fun subscribeToTopics() {
        val messaging = FirebaseMessaging.getInstance()
        messaging.subscribeToTopic(TOPIC_EMERGENCY_ALERTS)
        messaging.subscribeToTopic(TOPIC_ANNOUNCEMENTS)
        // Weather push is disabled for now; unsubscribe so older installs stop receiving it.
        messaging.unsubscribeFromTopic(TOPIC_WEATHER_ADVISORIES)
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun showNotification(
        context: Context,
        title: String,
        body: String,
        type: String,
        messageId: String?,
    ) {
        if (!canPostNotifications(context)) return

        val channelId = when (type) {
            "emergency_alert" -> CHANNEL_EMERGENCY
            else -> CHANNEL_ANNOUNCEMENTS
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NOTIFICATION_TYPE, type)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (messageId ?: type).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (type == "announcement") {
                    NotificationCompat.PRIORITY_DEFAULT
                } else {
                    NotificationCompat.PRIORITY_HIGH
                },
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(
            (messageId ?: "${type}_${System.currentTimeMillis()}").hashCode(),
            notification,
        )
    }
}
