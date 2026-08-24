package com.example.resilio

data class WeatherSnapshot(
    val tempC: Double,
    val code: Int,
    val humidity: Int,
    val windSpeed: Double,
    val windGusts: Double,
    val precipProb: Int,
    val apiTimeStr: String,
    val fetchedAtMillis: Long = System.currentTimeMillis(),
)

object WeatherCache {
    private const val STALE_AFTER_MS = 10 * 60 * 1000L

    @Volatile
    var snapshot: WeatherSnapshot? = null
        private set

    fun save(
        tempC: Double,
        code: Int,
        humidity: Int,
        windSpeed: Double,
        windGusts: Double,
        precipProb: Int,
        apiTimeStr: String,
    ) {
        snapshot = WeatherSnapshot(
            tempC = tempC,
            code = code,
            humidity = humidity,
            windSpeed = windSpeed,
            windGusts = windGusts,
            precipProb = precipProb,
            apiTimeStr = apiTimeStr,
        )
    }

    fun isFresh(): Boolean {
        val cached = snapshot ?: return false
        return System.currentTimeMillis() - cached.fetchedAtMillis < STALE_AFTER_MS
    }
}
