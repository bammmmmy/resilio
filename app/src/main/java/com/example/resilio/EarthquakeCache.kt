package com.example.resilio

object EarthquakeCache {
    private const val STALE_AFTER_MS = 15 * 60 * 1000L // 15 mins

    @Volatile
    var lastQuake: EarthquakeData? = null

    var lastFetched: Long = 0

    fun isFresh(): Boolean {
        return System.currentTimeMillis() - lastFetched < STALE_AFTER_MS
    }
}
