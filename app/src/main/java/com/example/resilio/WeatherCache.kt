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

data class WeatherAlert(
    val title: String,
    val description: String,
)

data class LandslideAssessment(
    val label: String,
    val copy: String,
    val saturation: String,
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
            1 -> "Mainly clear"
            2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Foggy"
            51 -> "Light drizzle"
            53 -> "Drizzle"
            55 -> "Heavy drizzle"
            61 -> "Light rain"
            63 -> "Rain"
            65 -> "Heavy rain"
            71 -> "Light snow"
            73 -> "Snow"
            75 -> "Heavy snow"
            77 -> "Snow grains"
            80 -> "Light showers"
            81 -> "Showers"
            82 -> "Heavy showers"
            85, 86 -> "Snow showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunderstorm"
            else -> "Unknown"
        }
    }

    fun getIcon(code: Int): Int {
        return when (code) {
            0 -> R.drawable.ic_weather_sun
            1 -> R.drawable.ic_weather_sun
            2 -> R.drawable.ic_weather_partly_cloudy
            3 -> R.drawable.ic_weather_cloud
            45, 48 -> R.drawable.ic_weather_fog
            51, 53, 55 -> R.drawable.ic_weather_drizzle
            61, 63, 65, 80, 81, 82 -> R.drawable.ic_weather_rain
            71, 73, 75, 77, 85, 86 -> R.drawable.ic_weather_snow
            95, 96, 99 -> R.drawable.ic_weather_thunderstorm
            else -> R.drawable.ic_weather_cloud
        }
    }

    fun getWeatherAlert(code: Int, currentRain: Double, rain24h: Double): WeatherAlert? {
        return when {
            code == 95 || code == 96 || code == 99 -> WeatherAlert("Severe Thunderstorm Warning", "Seek shelter indoors and monitor official updates.")
            rain24h > 50 -> WeatherAlert("Flash Flood Warning", "High rainfall has been detected. Avoid low-lying areas and follow evacuation guidance.")
            code == 65 || code == 82 || currentRain > 15 -> WeatherAlert("Heavy Rainfall Warning", "Heavy rain is possible. Watch for flooding in low-lying areas.")
            code == 63 || code == 81 || rain24h > 10 -> WeatherAlert("Rain Advisory", "Rain is expected. Prepare for wet conditions and possible rising water.")
            code in listOf(51, 53, 55, 61, 80) -> WeatherAlert("Rain Advisory", "Light rain is expected. Prepare for wet conditions.")
            else -> null
        }
    }

    fun getLandslideAssessment(rain24h: Double): LandslideAssessment {
        return when {
            rain24h > 100 -> LandslideAssessment("Critical risk", "Severe rainfall conditions may trigger slope failure. Prepare for immediate action.", "Very high")
            rain24h > 60 -> LandslideAssessment("High risk", "Heavy rainfall is increasing slope instability. Monitor vulnerable areas closely.", "High")
            rain24h >= 20 -> LandslideAssessment("Moderate risk", "Conditions are favorable for landslides in some areas. Keep monitoring and be prepared.", "Moderate")
            else -> LandslideAssessment("Low risk", "Rainfall and soil conditions are currently stable. Continue routine monitoring.", "Low")
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
