package com.example.resilio

import android.content.Context
import androidx.core.content.edit
import com.example.resilio.notifications.PushNotificationManager

object DashboardNotificationHelper {

    private const val PREFS_NAME = "dashboard_notifications"
    private const val KEY_LAST_WEATHER = "last_weather_alert"
    private const val KEY_LAST_LANDSLIDE = "last_landslide_risk"
    private const val KEY_LAST_QUAKE_TIME = "last_quake_time"

    fun notifyIfNecessary(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        checkWeather(context, prefs)
        checkLandslide(context, prefs)
        checkEarthquake(context, prefs)
    }

    private fun checkWeather(context: Context, prefs: android.content.SharedPreferences) {
        val snap = WeatherCache.snapshot ?: return
        val advisory = WeatherCache.getWeatherAlert(snap.code, snap.currentPrecipIntensity, snap.rain24h)
        val alertKey = advisory?.title ?: ""
        val lastAlert = prefs.getString(KEY_LAST_WEATHER, "")
        if (advisory != null && alertKey != lastAlert) {
            PushNotificationManager.showNotification(
                context,
                advisory.title,
                advisory.description,
                "weather_advisory",
                "weather_${alertKey}"
            )
            prefs.edit { putString(KEY_LAST_WEATHER, alertKey) }
        } else if (advisory == null) {
            prefs.edit { putString(KEY_LAST_WEATHER, "") }
        }
    }

    private fun checkLandslide(context: Context, prefs: android.content.SharedPreferences) {
        val snap = WeatherCache.snapshot ?: return
        val rain = snap.rain24h
        val assessment = WeatherCache.getLandslideAssessment(rain)
        val risk = assessment.label

        val lastRisk = prefs.getString(KEY_LAST_LANDSLIDE, "")
        if (risk != "Low risk" && risk != lastRisk) {
            PushNotificationManager.showNotification(
                context,
                risk,
                assessment.copy,
                "landslide_alert",
                "landslide_$risk"
            )
            prefs.edit { putString(KEY_LAST_LANDSLIDE, risk) }
        } else if (risk == "Low risk") {
            prefs.edit { putString(KEY_LAST_LANDSLIDE, "Low risk") }
        }
    }

    private fun checkEarthquake(context: Context, prefs: android.content.SharedPreferences) {
        val snap = EarthquakeCache.lastQuake ?: return
        if (snap.magnitude == 0.0) return

        val isRecent = (System.currentTimeMillis() - snap.timeMillis) < (24 * 60 * 60 * 1000L)
        val isSignificant = snap.magnitude >= 2.0
        val isNearby = snap.distanceKm < 100.0

        val lastTime = prefs.getLong(KEY_LAST_QUAKE_TIME, 0L)
        if (isRecent && isSignificant && isNearby && snap.timeMillis != lastTime) {
            PushNotificationManager.showNotification(
                context,
                "Earthquake Alert",
                "Magnitude ${String.format(java.util.Locale.US, "%.1f", snap.magnitude)} - ${snap.place}",
                "earthquake_alert",
                "quake_${snap.timeMillis}"
            )
            prefs.edit { putLong(KEY_LAST_QUAKE_TIME, snap.timeMillis) }
        }
    }

}
