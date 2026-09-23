package com.example.resilio

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.resilio.notifications.PushNotificationManager
import java.util.concurrent.TimeUnit

class ResilioApp : Application() {
    companion object {
        lateinit var instance: ResilioApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        PushNotificationManager.createChannels(this)
        scheduleDashboardWork()
    }

    private fun scheduleDashboardWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<DashboardWorker>(15L, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DashboardSafetyCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
