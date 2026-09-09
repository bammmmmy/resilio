package com.example.resilio

import android.content.Context
import androidx.core.content.edit
import com.example.resilio.notifications.PushNotificationManager

object DashboardNotificationHelper {

    private const val PREFS_NAME = "dashboard_notifications"
    private const val KEY_LAST_WEATHER = "last_weather_code"
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
        
        // Align with UI logic: Show advisory for any rain or high accumulation
        val autoAdvisory = getAutoAdvisory(snap.code)
        val intensityAdvisory = if (snap.currentPrecipIntensity > 15.0) "Heavy Rainfall Warning: Seek Shelter" 
                                else if (snap.currentPrecipIntensity > 0) "Rain Advisory: Carry an umbrella"
                                else null
        val accumulationAdvisory = if (snap.rain24h > 50) "Flash Flood Warning: High rainfall detected"
                                   else if (snap.rain24h > 10) "Flood Advisory: Saturated ground conditions"
                                   else null
                                   
        val advisory = autoAdvisory ?: intensityAdvisory ?: accumulationAdvisory
        
        val lastCode = prefs.getInt(KEY_LAST_WEATHER, -1)
        // We only notify if the condition has changed to avoid spamming the user
        if (advisory != null && snap.code != lastCode) {
            PushNotificationManager.showNotification(
                context,
                "Weather Advisory",
                advisory,
                "weather_advisory",
                "weather_${snap.code}"
            )
            prefs.edit { putInt(KEY_LAST_WEATHER, snap.code) }
        } else if (advisory == null) {
            prefs.edit { putInt(KEY_LAST_WEATHER, -1) }
        }
    }

    private fun checkLandslide(context: Context, prefs: android.content.SharedPreferences) {
        val snap = WeatherCache.snapshot ?: return
        val rain = snap.rain24h
        val risk = when {
            rain > 100.0 -> "CRITICAL"
            rain > 60.0 -> "HIGH RISK"
            rain >= 20.0 -> "MODERATE"
            else -> "LOW RISK"
        }

        val lastRisk = prefs.getString(KEY_LAST_LANDSLIDE, "")
        if (risk != "LOW RISK" && risk != lastRisk) {
            val desc = when (risk) {
                "CRITICAL" -> "Extremely high risk! Evacuate if in high-risk zones."
                "HIGH RISK" -> "High risk of landslides. Monitor slopes."
                else -> "Moderate risk. Avoid landslide-prone areas."
            }
            PushNotificationManager.showNotification(
                context,
                "Landslide Risk: $risk",
                desc,
                "landslide_alert",
                "landslide_$risk"
            )
            prefs.edit { putString(KEY_LAST_LANDSLIDE, risk) }
        } else if (risk == "LOW RISK") {
            prefs.edit { putString(KEY_LAST_LANDSLIDE, "LOW RISK") }
        }
    }

    private fun checkEarthquake(context: Context, prefs: android.content.SharedPreferences) {
        val snap = EarthquakeCache.lastQuake ?: return
        if (snap.magnitude == 0.0) return

        val isRecent = (System.currentTimeMillis() - snap.timeMillis) < (60 * 60 * 1000L)
        val isSignificant = snap.magnitude > 2.5
        val isNearby = snap.distanceKm < 100.0

        val lastTime = prefs.getLong(KEY_LAST_QUAKE_TIME, 0L)
        if (isRecent && isSignificant && isNearby && snap.timeMillis != lastTime) {
            PushNotificationManager.showNotification(
                context,
                "Earthquake Detected",
                "M ${snap.magnitude} earthquake detected ${snap.distanceKm.toInt()} km from Antipolo.",
                "earthquake_alert",
                "quake_${snap.timeMillis}"
            )
            prefs.edit { putLong(KEY_LAST_QUAKE_TIME, snap.timeMillis) }
        }
    }

    private fun getAutoAdvisory(code: Int): String? {
        return when (code) {
            51, 53, 55, 61, 80 -> "Rain Advisory: Prepare for Wet Conditions"
            63, 81 -> "Moderate Rain Advisory: Watch for Rising Water"
            65, 82 -> "Violent Rain Advisory: Stay Indoors"
            95, 96, 99 -> "Severe Thunderstorm Warning: Seek Shelter"
            else -> null
        }
    }
}
