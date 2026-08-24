package com.example.resilio

import android.app.Application
import com.example.resilio.notifications.PushNotificationManager

class ResilioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PushNotificationManager.createChannels(this)
    }
}
