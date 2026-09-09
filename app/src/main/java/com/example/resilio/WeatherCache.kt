package com.example.resilio

import com.google.firebase.Timestamp

data class WeatherSnapshot(
    val tempC: Double,
    val code: Int,
    val humidity: Int,
    val windSpeed: Double,
    val windGusts: Double,
    val precipProb: Int,
    val rain24h: Double = 0.0,
    val currentPrecipIntensity: Double = 0.0,
    val apiTimeStr: String = "",
    val fetchedAtMillis: Long = System.currentTimeMillis(),
)

object WeatherCache {
    private const val STALE_AFTER_MS = 5 * 60 * 1000L

    @Volatile
    var snapshot: WeatherSnapshot? = null

    fun save(
        tempC: Double,
        code: Int,
        humidity: Int,
        windSpeed: Double,
        windGusts: Double,
        precipProb: Int,
        rain24h: Double,
        currentPrecipIntensity: Double,
        apiTimeStr: String = ""
    ) {
        snapshot = WeatherSnapshot(
            tempC = tempC,
            code = code,
            humidity = humidity,
            windSpeed = windSpeed,
            windGusts = windGusts,
            precipProb = precipProb,
            rain24h = rain24h,
            currentPrecipIntensity = currentPrecipIntensity,
            apiTimeStr = apiTimeStr
        )
    }

    fun getConditionName(code: Int): String {
        return when (code) {
            0 -> "Clear Sky"
            1, 2, 3 -> "Mainly Clear"
            45, 48 -> "Foggy"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rainy"
            71, 73, 75 -> "Snowy"
            77 -> "Snow Grains"
            80, 81, 82 -> "Rain Showers"
            85, 86 -> "Snow Showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunderstorm with Hail"
            else -> "Unknown"
        }
    }

    fun getIcon(code: Int): Int {
        return when (code) {
            0, 1 -> R.drawable.ic_cloud
            2, 3 -> R.drawable.ic_cloud
            else -> R.drawable.ic_alerts
        }
    }

    fun getSafetyAdvice(code: Int, rain: Double): Pair<String, String> {
        return when {
            code >= 95 -> "Thunderstorm Warning" to "Stay indoors and avoid electrical appliances."
            rain > 50 -> "Heavy Rain Warning" to "Flood risk is high. Move to higher ground."
            rain > 10 -> "Rain Advisory" to "Ground is saturated. Watch for minor flooding."
            code in 51..82 -> "Wet Weather" to "Carry an umbrella and be careful of slippery roads."
            else -> "Weather Stable" to "Conditions are currently safe."
        }
    }

    fun isFresh(): Boolean {
        val cached = snapshot ?: return false
        return System.currentTimeMillis() - cached.fetchedAtMillis < STALE_AFTER_MS
    }
}

data class EarthquakeData(
    val id: String = "",
    val magnitude: Double = 0.0,
    val location: String = "",
    val timeMillis: Long = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val depth: Double = 0.0,
    val distanceKm: Double = 0.0,
    val place: String = ""
)
