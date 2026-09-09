package com.example.resilio

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class DashboardWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    private val client = OkHttpClient()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            fetchWeatherSync()
            fetchEarthquakeSync()
            
            withContext(Dispatchers.Main) {
                DashboardNotificationHelper.notifyIfNecessary(applicationContext)
            }
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun fetchWeatherSync() {
            val lat = 14.5845
        val lon = 121.1754
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_gusts_10m,precipitation" +
                "&hourly=precipitation,precipitation_probability&past_days=1&timezone=auto"

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            
            val jsonData = response.body?.string() ?: return
            val jsonObject = JSONObject(jsonData)
            
            val current = jsonObject.getJSONObject("current")
            val tempC = current.getDouble("temperature_2m")
            val humidity = current.getInt("relative_humidity_2m")
            val windSpeed = current.getDouble("wind_speed_10m")
            val windGusts = current.getDouble("wind_gusts_10m")
            val code = current.getInt("weather_code")
            val apiTimeStr = current.getString("time")
            val currentRainIntensity = current.optDouble("precipitation", 0.0)
            
            val hourly = jsonObject.getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val precipitation = hourly.getJSONArray("precipitation")
            val currentTimeStr = current.getString("time").substring(0, 13) + ":00"
            
            var currentPrecipProb = 0
            var currentIndex = -1
            for (i in 0 until times.length()) {
                if (times.getString(i).startsWith(currentTimeStr)) {
                    currentPrecipProb = hourly.getJSONArray("precipitation_probability").getInt(i)
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

            WeatherCache.snapshot = WeatherSnapshot(
                tempC = tempC,
                code = code,
                humidity = humidity,
                windSpeed = windSpeed,
                windGusts = windGusts,
                precipProb = currentPrecipProb,
                rain24h = rain24h,
                currentPrecipIntensity = currentRainIntensity,
                apiTimeStr = apiTimeStr
            )
        }
    }

    private fun fetchEarthquakeSync() {
        val lat = 14.5845
        val lon = 121.1754
        val url = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&latitude=$lat&longitude=$lon&maxradiuskm=100&minmagnitude=2.0&orderby=time&limit=1"

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
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
                android.location.Location.distanceBetween(lat, lon, qLat, qLon, results)
                val distanceKm = (results[0] / 1000.0).toDouble()

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
    }
}
