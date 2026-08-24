package com.example.resilio.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ResilioFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushNotificationManager.saveToken(token)
        PushNotificationManager.subscribeToTopics()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"]
            ?: if (message.from?.contains("emergency") == true) {
                "emergency_alert"
            } else {
                "announcement"
            }

        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(com.example.resilio.R.string.app_name)

        val body = message.notification?.body
            ?: message.data["body"]
            ?: return

        PushNotificationManager.showNotification(
            context = applicationContext,
            title = title,
            body = body,
            type = type,
            messageId = message.messageId ?: message.data["id"],
        )
    }
}
