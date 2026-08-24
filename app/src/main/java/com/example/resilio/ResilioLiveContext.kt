package com.example.resilio

import android.util.Log
import com.example.resilio.model.Announcement
import com.example.resilio.model.AnnouncementStatus
import com.example.resilio.model.EmergencyAlert
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object ResilioLiveContext {
    private const val TAG = "ResilioLiveContext"
    private const val CACHE_MS = 60_000L
    private const val QUERY_TIMEOUT_SEC = 8L
    private const val MAX_ITEMS = 3
    private const val MAX_BODY_CHARS = 400
    private const val WEATHER_LAT = 14.5845
    private const val WEATHER_LON = 121.1754
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedText: String? = null

    @Volatile
    private var cachedAtMillis: Long = 0L

    suspend fun loadForAi(): String {
        val now = System.currentTimeMillis()
        val existing = cachedText
        if (existing != null && now - cachedAtMillis < CACHE_MS) return existing

        val text = withContext(Dispatchers.IO) {
            refreshWeatherIfNeeded()
            buildFresh()
        }
        cachedText = text
        cachedAtMillis = now
        return text
    }

    private fun buildFresh(): String = buildString {
        appendLine("LIVE APP DATA (use this when the user asks about weather, alerts, announcements, or a posted update):")
        appendLine()
        append(weatherBlock())
        appendLine()
        append(alertsBlock())
        appendLine()
        append(announcementsBlock())
    }

    private fun refreshWeatherIfNeeded() {
        if (WeatherCache.isFresh()) return
        runCatching {
            val url =
                "https://api.open-meteo.com/v1/forecast?latitude=$WEATHER_LAT&longitude=$WEATHER_LON" +
                    "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_gusts_10m,precipitation" +
                    "&hourly=precipitation_probability&timezone=auto"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val jsonObject = JSONObject(response.body?.string().orEmpty())
                val current = jsonObject.getJSONObject("current")
                val hourly = jsonObject.getJSONObject("hourly")
                val times = hourly.getJSONArray("time")
                val currentTimeStr = current.getString("time").substring(0, 13) + ":00"
                var precipProb = 0
                for (i in 0 until times.length()) {
                    if (times.getString(i).startsWith(currentTimeStr)) {
                        precipProb = hourly.getJSONArray("precipitation_probability").getInt(i)
                        break
                    }
                }
                WeatherCache.save(
                    tempC = current.getDouble("temperature_2m"),
                    code = current.getInt("weather_code"),
                    humidity = current.getInt("relative_humidity_2m"),
                    windSpeed = current.getDouble("wind_speed_10m"),
                    windGusts = current.getDouble("wind_gusts_10m"),
                    precipProb = precipProb,
                    apiTimeStr = current.getString("time"),
                )
            }
        }.onFailure { error ->
            Log.w(TAG, "Weather refresh failed: ${error.message}")
        }
    }

    private fun weatherBlock(): String {
        val weather = WeatherCache.snapshot ?: return """
            CURRENT WEATHER (weather status card):
            Not loaded yet. If asked, say current weather is unavailable and give general Antipolo safety advice.
        """.trimIndent()

        val condition = weatherDescription(weather.code)
        val advisory = weatherAdvisory(weather.code) ?: "None"
        val timeLabel = formatApiTime(weather.apiTimeStr)
        return buildString {
            appendLine("CURRENT WEATHER IN ANTIPOLO (same as the weather status card):")
            appendLine("- Condition: $condition")
            appendLine("- Temperature: ${weather.tempC.toInt()}°C")
            appendLine("- Humidity: ${weather.humidity}%")
            appendLine("- Wind: ${weather.windSpeed.toInt()} km/h (gusts ${weather.windGusts.toInt()} km/h)")
            appendLine("- Rain chance: ${weather.precipProb}%")
            appendLine("- Observed: $timeLabel")
            appendLine("- Advisory shown on the card: $advisory")
        }
    }

    private fun alertsBlock(): String {
        val alerts = runCatching { fetchLatestAlerts() }.getOrElse { error ->
            Log.w(TAG, "Failed to load alerts: ${error.message}")
            return "LATEST EMERGENCY ALERTS: Unable to load right now."
        }
        if (alerts.isEmpty()) {
            return "LATEST EMERGENCY ALERTS: None posted right now."
        }
        return buildString {
            appendLine("LATEST EMERGENCY ALERTS (same posts as Latest Alerts on home):")
            alerts.forEachIndexed { index, alert ->
                appendLine("${index + 1}. Title: ${alert.title.ifBlank { "(no title)" }}")
                appendLine("   Type: ${alert.type}")
                appendLine("   Posted: ${formatTimestamp(alert.safeTimestamp)}")
                if (alert.affectedAreas.isNotBlank()) appendLine("   Affected areas: ${alert.affectedAreas}")
                if (alert.evacuationCenter.isNotBlank()) appendLine("   Evacuation center: ${alert.evacuationCenter}")
                appendLine("   Details: ${trimBody(alert.content)}")
            }
        }
    }

    private fun announcementsBlock(): String {
        val announcements = runCatching { fetchLatestAnnouncements() }.getOrElse { error ->
            Log.w(TAG, "Failed to load announcements: ${error.message}")
            return "LATEST ANNOUNCEMENTS: Unable to load right now."
        }
        if (announcements.isEmpty()) {
            return "LATEST ANNOUNCEMENTS: None posted right now."
        }
        return buildString {
            appendLine("LATEST ANNOUNCEMENTS (same posts as Latest Announcements on home):")
            announcements.forEachIndexed { index, announcement ->
                appendLine("${index + 1}. Title: ${announcement.title.ifBlank { "(no title)" }}")
                appendLine("   Type: ${announcement.type}")
                appendLine("   Posted: ${formatTimestamp(announcement.safeTimestamp)}")
                if (announcement.affectedAreas.isNotBlank()) {
                    appendLine("   Affected areas: ${announcement.affectedAreas}")
                }
                if (announcement.evacuationCenter.isNotBlank()) {
                    appendLine("   Evacuation center: ${announcement.evacuationCenter}")
                }
                appendLine("   Details: ${trimBody(announcement.content)}")
            }
        }
    }

    private fun fetchLatestAlerts(): List<EmergencyAlert> {
        val snapshot = Tasks.await(
            FirebaseFirestore.getInstance()
                .collection("emergency_alerts")
                .get(),
            QUERY_TIMEOUT_SEC,
            TimeUnit.SECONDS,
        )
        return snapshot.toObjects(EmergencyAlert::class.java)
            .sortedByDescending { it.safeTimestamp }
            .take(MAX_ITEMS)
    }

    private fun fetchLatestAnnouncements(): List<Announcement> {
        val snapshot = Tasks.await(
            FirebaseFirestore.getInstance()
                .collection("announcements")
                .get(),
            QUERY_TIMEOUT_SEC,
            TimeUnit.SECONDS,
        )
        return snapshot.toObjects(Announcement::class.java)
            .filter { it.status == AnnouncementStatus.APPROVED }
            .sortedByDescending { it.safeTimestamp }
            .take(MAX_ITEMS)
    }

    private fun trimBody(text: String): String {
        val cleaned = text.trim().ifBlank { "(no details)" }
        return if (cleaned.length <= MAX_BODY_CHARS) cleaned
        else cleaned.take(MAX_BODY_CHARS).trimEnd() + "…"
    }

    private fun formatTimestamp(timestamp: Timestamp): String =
        SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(timestamp.toDate())

    private fun formatApiTime(apiTimeStr: String): String = try {
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(apiTimeStr) ?: Date()
        SimpleDateFormat("EEEE h:mm a", Locale.getDefault()).format(parsed)
    } catch (_: Exception) {
        apiTimeStr
    }

    fun weatherDescription(code: Int): String = when (code) {
        0 -> "Clear Sky"
        1 -> "Mainly Clear"
        2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51 -> "Light Drizzle"
        53 -> "Moderate Drizzle"
        55 -> "Dense Drizzle"
        61 -> "Light Rain"
        63 -> "Moderate Rain"
        65 -> "Heavy Rain"
        80 -> "Light Rain Showers"
        81 -> "Moderate Rain Showers"
        82 -> "Violent Rain Showers"
        95 -> "Scattered Thunderstorms"
        96 -> "Thunderstorms with Hail"
        99 -> "Heavy Thunderstorms"
        else -> "Cloudy"
    }

    fun weatherAdvisory(code: Int): String? = when (code) {
        51, 53, 55, 61, 80 -> "Rain Advisory: Prepare for Wet Conditions"
        63, 81 -> "Moderate Rain Advisory: Watch for Rising Water"
        65, 82 -> "Violent Rain Advisory: Stay Indoors"
        95, 96, 99 -> "Severe Thunderstorm Warning: Seek Shelter"
        else -> null
    }
}
