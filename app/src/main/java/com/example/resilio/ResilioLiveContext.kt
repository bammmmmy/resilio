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
        appendLine("LIVE APP DATA (use this to answer user questions about weather, landslides, earthquakes, alerts, or announcements):")
        appendLine()
        append(weatherBlock())
        appendLine()
        append(landslideBlock())
        appendLine()
        append(earthquakeBlock())
        appendLine()
        append(alertsBlock())
        appendLine()
        append(announcementsBlock())
    }

    private fun refreshWeatherIfNeeded() {
        if (!WeatherCache.isFresh()) {
            refreshWeather()
        }
        if (!EarthquakeCache.isFresh()) {
            refreshEarthquake()
        }
    }

    private fun refreshWeather() {
        runCatching {
            val url =
                "https://api.open-meteo.com/v1/forecast?latitude=$WEATHER_LAT&longitude=$WEATHER_LON" +
                    "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_gusts_10m,precipitation" +
                    "&hourly=precipitation,precipitation_probability&past_days=1&timezone=auto"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val jsonObject = JSONObject(response.body?.string().orEmpty())
                val current = jsonObject.getJSONObject("current")
                val hourly = jsonObject.getJSONObject("hourly")
                val times = hourly.getJSONArray("time")
                val precipitation = hourly.getJSONArray("precipitation")
                val currentTimeStr = current.getString("time").substring(0, 13) + ":00"
                val currentRainIntensity = current.optDouble("precipitation", 0.0)
                
                var precipProb = 0
                var currentIndex = -1
                for (i in 0 until times.length()) {
                    if (times.getString(i).startsWith(currentTimeStr)) {
                        precipProb = hourly.getJSONArray("precipitation_probability").getInt(i)
                        currentIndex = i
                        break
                    }
                }
                
                var rain24h = 0.0
                if (currentIndex >= 24) {
                    for (i in (currentIndex - 23)..currentIndex) {
                        rain24h += precipitation.getDouble(i)
                    }
                }

                WeatherCache.save(
                    tempC = current.getDouble("temperature_2m"),
                    code = current.getInt("weather_code"),
                    humidity = current.getInt("relative_humidity_2m"),
                    windSpeed = current.getDouble("wind_speed_10m"),
                    windGusts = current.getDouble("wind_gusts_10m"),
                    precipProb = precipProb,
                    rain24h = rain24h,
                    currentPrecipIntensity = currentRainIntensity,
                    apiTimeStr = current.getString("time"),
                )
            }
        }.onFailure { error ->
            Log.w(TAG, "Weather refresh failed: ${error.message}")
        }
    }

    private fun refreshEarthquake() {
        runCatching {
            val url = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&latitude=$WEATHER_LAT&longitude=$WEATHER_LON&maxradiuskm=100&minmagnitude=2.0&orderby=time&limit=1"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val jsonData = response.body?.string() ?: return
                val jsonObject = JSONObject(jsonData)
                val features = jsonObject.getJSONArray("features")
                
                if (features.length() > 0) {
                    val first = features.getJSONObject(0)
                    val props = first.getJSONObject("properties")
                    val mag = props.getDouble("mag")
                    val place = props.getString("place")
                    val time = props.getLong("time")
                    
                    val geometry = first.getJSONObject("geometry")
                    val coords = geometry.getJSONArray("coordinates")
                    val qLon = coords.getDouble(0)
                    val qLat = coords.getDouble(1)
                    
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(WEATHER_LAT, WEATHER_LON, qLat, qLon, results)
                    val distanceKm = results[0] / 1000.0

                    EarthquakeCache.save(mag, place, time, distanceKm)
                } else {
                    EarthquakeCache.save(0.0, "No recent activity in 100km", System.currentTimeMillis() - (86400000L * 2), 999.0)
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "Earthquake refresh failed: ${error.message}")
        }
    }

    private fun weatherBlock(): String {
        val weather = WeatherCache.snapshot ?: return """
            WEATHER CARD DATA:
            Not loaded yet.
        """.trimIndent()

        val condition = weatherDescription(weather.code)
        val intensity = weather.currentPrecipIntensity
        val upgradedCondition = when {
            intensity > 30.0 -> "Violent Rain"
            intensity > 7.6 -> "Heavy Rain"
            intensity > 2.5 -> "Moderate Rain"
            else -> condition
        }
        
        val advisory = weatherAdvisory(weather.code) ?: if (intensity > 15.0) "Heavy Rainfall Warning: Seek Shelter" else "None"
        val timeLabel = formatApiTime(weather.fetchedAtMillis)

        return buildString {
            appendLine("WEATHER CARD DATA:")
            appendLine("- Current Temperature: ${weather.tempC.toInt()}°C")
            appendLine("- Condition: $upgradedCondition")
            appendLine("- Rain Intensity: ${String.format(Locale.US, "%.1f", intensity)} mm/h")
            appendLine("- Humidity: ${weather.humidity}%")
            appendLine("- Wind Speed: ${weather.windSpeed.toInt()} km/h")
            appendLine("- Wind Gusts: ${weather.windGusts.toInt()} km/h")
            appendLine("- Precipitation Probability: ${weather.precipProb}%")
            appendLine("- Advisory: $advisory")
            appendLine("- Last Observed: $timeLabel")
        }
    }

    private fun landslideBlock(): String {
        val weather = WeatherCache.snapshot ?: return """
            LANDSLIDE RISK CARD DATA:
            Not loaded yet.
        """.trimIndent()

        val rain = weather.rain24h
        val (status, desc) = when {
            rain > 100.0 -> "CRITICAL" to "Extremely high risk! Cumulative rainfall has exceeded 100mm. Evacuate if in high-risk zones."
            rain > 60.0 -> "HIGH RISK" to "High risk of landslides due to heavy saturation. Monitor slopes and follow BDRRMO advice."
            rain > 30.0 -> "MODERATE" to "Moderate risk. Ground is saturated. Avoid landslide-prone areas in San Jose."
            else -> "LOW RISK" to "Low risk based on current rainfall. Stay alert for any updates during rainy seasons."
        }
        
        val saturation = when {
            rain > 80.0 -> "Very High"
            rain > 50.0 -> "High"
            rain > 20.0 -> "Moderate"
            else -> "Low"
        }

        return buildString {
            appendLine("LANDSLIDE RISK CARD DATA:")
            appendLine("- Status: $status")
            appendLine("- Description: $desc")
            appendLine("- 24h Cumulative Rainfall: ${String.format(Locale.US, "%.1f", rain)}mm")
            appendLine("- Soil Saturation: $saturation")
        }
    }

    private fun earthquakeBlock(): String {
        val quake = EarthquakeCache.lastQuake ?: return """
            EARTHQUAKE MONITOR CARD DATA:
            Not loaded yet.
        """.trimIndent()
        
        if (quake.magnitude == 0.0) {
            return """
                EARTHQUAKE MONITOR CARD DATA:
                - Status: NO RECENT QUAKES
                - Description: No significant earthquakes detected within 100km of Antipolo City in the last 24 hours.
                - Latest Magnitude: ---
                - Distance: ---
            """.trimIndent()
        }

        val isRecent = (System.currentTimeMillis() - quake.timeMillis) < (24 * 60 * 60 * 1000L)
        val isNearby = quake.distanceKm < 100.0
        val isSignificant = quake.magnitude > 2.5
        
        val (status, desc) = when {
            isRecent && isSignificant && isNearby -> "DANGER: NEARBY QUAKE" to "A significant earthquake occurred very close to Antipolo recently. Expect aftershocks."
            isRecent && isNearby -> "RECENT LOCAL QUAKE" to "A light earthquake was detected nearby. Monitor for local advisories."
            isRecent -> "RECENT REGIONAL QUAKE" to "Recent activity detected in the region, but not immediately threatening to Antipolo."
            else -> "NO RECENT QUAKES" to "No significant earthquakes detected within 100km of Antipolo City in the last 24 hours."
        }
        
        val timeLabel = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(quake.timeMillis))
        
        return buildString {
            appendLine("EARTHQUAKE MONITOR CARD DATA:")
            appendLine("- Status: $status")
            appendLine("- Description: $desc")
            appendLine("- Latest Magnitude: M ${quake.magnitude}")
            appendLine("- Distance: ${quake.distanceKm.toInt()} km away")
            appendLine("- Location: ${quake.place}")
            appendLine("- Time of Event: $timeLabel")
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

    private fun formatApiTime(fetchedAtMillis: Long): String {
        val date = Date(fetchedAtMillis)
        return SimpleDateFormat("EEEE h:mm a", Locale.getDefault()).format(date)
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
