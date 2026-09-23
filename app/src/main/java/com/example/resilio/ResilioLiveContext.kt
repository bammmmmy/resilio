package com.example.resilio

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.resilio.model.Announcement
import com.example.resilio.model.AnnouncementStatus
import com.example.resilio.model.EmergencyAlert
import com.example.resilio.model.EmergencyReport
import com.example.resilio.model.EvacuationArea
import com.example.resilio.model.User
import com.example.resilio.util.TimeUtils
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
        appendLine("LIVE APP DATA (use this to answer user questions about weather, landslides, earthquakes, alerts, announcements, resident verification, emergency reports, and nearby evacuation areas):")
        appendLine()
        append(residentContextBlock())
        appendLine()
        append(weatherBlock())
        appendLine()
        append(forecastBlock())
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
                if (currentIndex != -1) {
                    val start = (currentIndex - 23).coerceAtLeast(0)
                    for (i in start..currentIndex) {
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
                    apiTimeStr = current.getString("time")
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

                    EarthquakeCache.lastQuake = EarthquakeData(
                        id = first.getString("id"),
                        magnitude = mag,
                        location = place,
                        place = place,
                        timeMillis = time,
                        latitude = qLat,
                        longitude = qLon,
                        distanceKm = distanceKm
                    )
                    EarthquakeCache.lastFetched = System.currentTimeMillis()
                } else {
                    EarthquakeCache.lastQuake = EarthquakeData(magnitude = 0.0, location = "No recent activity in 100km")
                    EarthquakeCache.lastFetched = System.currentTimeMillis()
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
        val timeLabel = TimeUtils.formatToPhTime(weather.fetchedAtMillis)

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
            rain >= 20.0 -> "MODERATE" to "Moderate risk. Ground is saturated. Avoid landslide-prone areas in San Jose."
            else -> "LOW RISK" to "Low risk based on current rainfall. Stay alert for any updates during rainy seasons."
        }
        
        val saturation = when {
            rain > 80.0 -> "Very High"
            rain > 50.0 -> "High"
            rain >= 20.0 -> "Moderate"
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
        
        val timeLabel = TimeUtils.formatToPhTime(quake.timeMillis)
        
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

    private fun currentResidentLocation(): Pair<Double, Double>? {
        val context = ResilioApp.instance
        val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation && !hasCoarseLocation) return null

        val locationManager = context.getSystemService(LocationManager::class.java) as? LocationManager ?: return null
        val providers = listOfNotNull(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

        return providers
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }
                    .getOrNull()
            }
            .maxByOrNull { it.time }
            ?.let { it.latitude to it.longitude }
    }

    private fun residentContextBlock(): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return "RESIDENT CONTEXT: User is not signed in."
        val db = FirebaseFirestore.getInstance()

        val resident = runCatching {
            val snapshot = Tasks.await(
                db.collection("users").document(uid).get(),
                QUERY_TIMEOUT_SEC,
                TimeUnit.SECONDS,
            )
            if (!snapshot.exists()) null else snapshot.toObject(User::class.java)
        }.getOrElse { null }

        val latestReport = runCatching {
            val snapshot = Tasks.await(
                db.collection("emergency_reports")
                    .whereEqualTo("senderUid", uid)
                    .get(),
                QUERY_TIMEOUT_SEC,
                TimeUnit.SECONDS,
            )
            snapshot.toObjects(EmergencyReport::class.java)
                .sortedByDescending { it.safeTimestamp.toDate().time }
                .firstOrNull()
        }.getOrElse { null }

        val currentLocation = currentResidentLocation()
        val latestReportLocation = latestReport?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }?.let { it.latitude to it.longitude }
        val referenceLocation = currentLocation ?: latestReportLocation ?: (WEATHER_LAT to WEATHER_LON)
        val referenceLat = referenceLocation.first
        val referenceLng = referenceLocation.second
        val nearestArea = runCatching { fetchNearestEvacuationArea(referenceLat, referenceLng) }.getOrElse { null }
        val nearbyReports = runCatching { fetchNearbyResidentReports(referenceLat, referenceLng) }.getOrElse { emptyList() }

        val name = resident?.fullName?.ifBlank { "Resident" } ?: "Resident"
        val verification = resident?.verificationStatus?.toString()?.replace('_', ' ')?.lowercase(Locale.US) ?: "unknown"
        val reportStatus = latestReport?.status?.toString()?.replace('_', ' ')?.lowercase(Locale.US) ?: "no submitted report"
        val reportType = latestReport?.type?.ifBlank { "Emergency" } ?: "Emergency"
        val locationSource = when {
            currentLocation != null -> "current device location"
            latestReportLocation != null -> "latest emergency report location"
            else -> "default Antipolo monitoring point"
        }
        val reportLocationText = when {
            currentLocation != null -> "lat ${currentLocation.first}, lng ${currentLocation.second}"
            latestReportLocation != null -> "lat ${latestReportLocation.first}, lng ${latestReportLocation.second}"
            else -> "no precise location available"
        }

        return buildString {
            appendLine("RESIDENT CONTEXT:")
            appendLine("- Resident: $name")
            appendLine("- Verification status: $verification")
            appendLine("- Latest emergency report: $reportType — status $reportStatus")
            appendLine("- Resident location source: $locationSource")
            appendLine("- Resident location: $reportLocationText")
            if (nearestArea != null) {
                appendLine("- Nearest evacuation area: ${nearestArea.name} (${nearestArea.address}) — ${String.format(Locale.US, "%.1f", nearestAreaDistanceKm(referenceLat, referenceLng, nearestArea.latitude, nearestArea.longitude))} km away")
            } else {
                appendLine("- Nearest evacuation area: no evacuation area available in the database")
            }
            if (nearbyReports.isNotEmpty()) {
                appendLine("- Nearby emergency reports: ${nearbyReports.size} report(s) within 5 km of the resident's current reference location")
            } else {
                appendLine("- Nearby emergency reports: none within 5 km of the resident's current reference location")
            }
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
                appendLine("   Posted: ${TimeUtils.formatToPhTime(alert.safeTimestamp, "MMM d, yyyy h:mm a")}")
                if (alert.affectedAreas.isNotBlank()) appendLine("   Affected areas: ${alert.affectedAreas}")
                if (alert.evacuationCenter.isNotBlank()) appendLine("   Evacuation center: ${alert.evacuationCenter}")
                appendLine("   Details: ${trimBody(alert.content)}")
            }
        }
    }

    private fun forecastBlock(): String {
        val forecast = runCatching { fetchWeatherForecast() }.getOrElse { error ->
            Log.w(TAG, "Failed to load weather forecast: ${error.message}")
            return "WEATHER FORECAST: Unable to load right now."
        }

        if (forecast == null) {
            return "WEATHER FORECAST: Not available right now."
        }

        return buildString {
            appendLine("WEATHER FORECAST (resident area):")
            appendLine("- Next 6 hours: ${forecast.hourly.joinToString(" | ")}")
            appendLine("- Next 5 days: ${forecast.daily.joinToString(" | ")}")
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
                appendLine("   Posted: ${TimeUtils.formatToPhTime(announcement.safeTimestamp, "MMM d, yyyy h:mm a")}")
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

    private fun fetchNearestEvacuationArea(lat: Double, lng: Double): EvacuationArea? {
        val snapshot = Tasks.await(
            FirebaseFirestore.getInstance()
                .collection("evacuation_areas")
                .get(),
            QUERY_TIMEOUT_SEC,
            TimeUnit.SECONDS,
        )
        return snapshot.toObjects(EvacuationArea::class.java)
            .filter { it.latitude != 0.0 || it.longitude != 0.0 }
            .minByOrNull { nearestAreaDistanceKm(lat, lng, it.latitude, it.longitude) }
    }

    private fun fetchNearbyResidentReports(lat: Double, lng: Double): List<EmergencyReport> {
        val snapshot = Tasks.await(
            FirebaseFirestore.getInstance()
                .collection("emergency_reports")
                .get(),
            QUERY_TIMEOUT_SEC,
            TimeUnit.SECONDS,
        )
        return snapshot.toObjects(EmergencyReport::class.java)
            .filter { it.latitude != 0.0 || it.longitude != 0.0 }
            .filter { nearestAreaDistanceKm(lat, lng, it.latitude, it.longitude) <= 5.0 }
            .sortedByDescending { it.safeTimestamp }
            .take(3)
    }

    private fun nearestAreaDistanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val result = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lng1, lat2, lng2, result)
        return result[0] / 1000.0
    }

    private fun fetchWeatherForecast(): ForecastSnapshot? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$WEATHER_LAT&longitude=$WEATHER_LON" +
            "&hourly=temperature_2m,weather_code,precipitation_probability&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=5&timezone=auto"
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null
        val jsonObject = JSONObject(response.body?.string().orEmpty())

        val hourlyObj = jsonObject.optJSONObject("hourly") ?: return null
        val dailyObj = jsonObject.optJSONObject("daily") ?: return null

        val hourlyTimes = hourlyObj.optJSONArray("time") ?: return null
        val hourlyTemps = hourlyObj.optJSONArray("temperature_2m") ?: return null
        val hourlyCodes = hourlyObj.optJSONArray("weather_code") ?: return null
        val hourlyRain = hourlyObj.optJSONArray("precipitation_probability") ?: return null

        val dailyTimes = dailyObj.optJSONArray("time") ?: return null
        val dailyHigh = dailyObj.optJSONArray("temperature_2m_max") ?: return null
        val dailyLow = dailyObj.optJSONArray("temperature_2m_min") ?: return null
        val dailyRain = dailyObj.optJSONArray("precipitation_probability_max") ?: return null
        val dailyCodes = dailyObj.optJSONArray("weather_code") ?: return null

        val hourlySummary = mutableListOf<String>()
        val hourlyFormatter = java.text.SimpleDateFormat("MMM d, h a", Locale.US)
        hourlyFormatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
        for (i in 0 until minOf(hourlyTimes.length(), 6)) {
            val timeStr = hourlyTimes.getString(i)
            val hourLabel = try {
                val date = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(timeStr)
                if (date != null) hourlyFormatter.format(date) else timeStr
            } catch (_: Exception) {
                timeStr
            }
            val temp = hourlyTemps.optDouble(i, 0.0)
            val code = hourlyCodes.optInt(i, 0)
            val rain = hourlyRain.optInt(i, 0)
            hourlySummary.add("$hourLabel: ${temp.toInt()}°C, ${weatherDescription(code)}, $rain% rain")
        }

        val dailySummary = mutableListOf<String>()
        val dayFormatter = java.text.SimpleDateFormat("EEE", Locale.US)
        dayFormatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
        for (i in 0 until minOf(dailyTimes.length(), 5)) {
            val dayLabel = try {
                val date = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dailyTimes.getString(i))
                if (date != null) dayFormatter.format(date) else dailyTimes.getString(i)
            } catch (_: Exception) {
                dailyTimes.getString(i)
            }
            val high = dailyHigh.optDouble(i, 0.0)
            val low = dailyLow.optDouble(i, 0.0)
            val rain = dailyRain.optInt(i, 0)
            val code = dailyCodes.optInt(i, 0)
            dailySummary.add("$dayLabel: ${high.toInt()}°/${low.toInt()}°, ${weatherDescription(code)}, $rain% rain")
        }

        return ForecastSnapshot(hourlySummary, dailySummary)
    }

    private data class ForecastSnapshot(
        val hourly: List<String>,
        val daily: List<String>,
    )

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
