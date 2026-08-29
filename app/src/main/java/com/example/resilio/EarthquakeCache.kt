package com.example.resilio

data class EarthquakeSnapshot(
    val magnitude: Double,
    val place: String,
    val timeMillis: Long,
    val distanceKm: Double,
    val fetchedAtMillis: Long = System.currentTimeMillis()
)

object EarthquakeCache {
    private const val STALE_AFTER_MS = 15 * 60 * 1000L // 15 mins

    @Volatile
    var lastQuake: EarthquakeSnapshot? = null
        private set

    fun save(magnitude: Double, place: String, timeMillis: Long, distanceKm: Double) {
        lastQuake = EarthquakeSnapshot(magnitude, place, timeMillis, distanceKm)
    }

    fun isFresh(): Boolean {
        val cached = lastQuake ?: return false
        return System.currentTimeMillis() - cached.fetchedAtMillis < STALE_AFTER_MS
    }
}
