package com.example.resilio

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DashboardUIHelper {
    private val client = OkHttpClient()

    fun fetchWeather(
        context: Context,
        lifecycleScope: LifecycleCoroutineScope,
        forceRefresh: Boolean = false,
        onSuccess: () -> Unit
    ) {
        if (!forceRefresh && WeatherCache.isFresh()) {
            onSuccess()
            return
        }

        val lat = 14.5845
        val lon = 121.1754
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_gusts_10m,precipitation" +
                "&hourly=precipitation,precipitation_probability&past_days=1&timezone=auto"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use

                    val jsonData = response.body?.string() ?: return@use
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
                    if (currentIndex >= 24) {
                        for (i in (currentIndex - 23)..currentIndex) {
                            rain24h += precipitation.getDouble(i)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        WeatherCache.save(tempC, code, humidity, windSpeed, windGusts, currentPrecipProb, rain24h, currentRainIntensity, apiTimeStr)
                        DashboardNotificationHelper.notifyIfNecessary(context)
                        onSuccess()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun fetchEarthquakeData(
        context: Context,
        lifecycleScope: LifecycleCoroutineScope,
        onSuccess: () -> Unit
    ) {
        if (EarthquakeCache.isFresh()) {
            onSuccess()
            return
        }

        val lat = 14.5845
        val lon = 121.1754
        val url = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&latitude=$lat&longitude=$lon&maxradiuskm=100&minmagnitude=2.0&orderby=time&limit=1"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val jsonData = response.body?.string() ?: return@use
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
                        val distanceKm = results[0] / 1000.0

                        withContext(Dispatchers.Main) {
                            EarthquakeCache.save(mag, place, time, distanceKm)
                            DashboardNotificationHelper.notifyIfNecessary(context)
                            onSuccess()
                        }
                    } else {
                        // Task 3: Handle empty features gracefully
                        withContext(Dispatchers.Main) {
                            EarthquakeCache.save(0.0, "No recent activity in 100km", System.currentTimeMillis() - (86400000L * 2), 999.0)
                            DashboardNotificationHelper.notifyIfNecessary(context)
                            onSuccess()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateWeatherUI(
        view: View,
        snap: WeatherSnapshot,
        headerView: View? = null,
        statusTitle: TextView? = null,
        statusDesc: TextView? = null
    ) {
        view.findViewById<View>(R.id.layout_weather_loading).visibility = View.GONE
        view.findViewById<View>(R.id.layout_weather_content).visibility = View.VISIBLE

        val tempText = "${snap.tempC.toInt()}°C"
        view.findViewById<TextView>(R.id.tv_weather_temp).text = tempText

        var condition = getWeatherDescription(snap.code)

        val intensity = snap.currentPrecipIntensity
        var isHeavyRain = false
        if (intensity > 0.0) {
            val intensityView = view.findViewById<TextView>(R.id.tv_rain_intensity)
            intensityView.visibility = View.VISIBLE
            intensityView.text = String.format(Locale.US, "Rain Intensity: %.1f mm/h", intensity)

            if (intensity > 7.6) {
                condition = if (intensity > 30.0) "Violent Rain" else "Heavy Rain"
                isHeavyRain = true
            } else if (intensity > 2.5) {
                condition = "Moderate Rain"
            }
        } else {
            view.findViewById<View>(R.id.tv_rain_intensity).visibility = View.GONE
        }

        view.findViewById<TextView>(R.id.tv_weather_condition).text = condition
        view.findViewById<TextView>(R.id.tv_weather_humidity).text = "Humidity: ${snap.humidity}%"

        view.findViewById<TextView>(R.id.tv_weather_wind).text = if (snap.windGusts > snap.windSpeed * 1.5) {
            "Wind: ${snap.windSpeed.toInt()}-${snap.windGusts.toInt()} km/h"
        } else {
            "Wind: ${snap.windSpeed.toInt()} km/h"
        }

        view.findViewById<TextView>(R.id.tv_weather_precip).text = "Precipitation: ${snap.precipProb}%"
        view.findViewById<TextView>(R.id.tv_rain_24h).text = String.format(Locale.US, "24h Rain: %.1f mm", snap.rain24h)

        try {
            val apiFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            val weatherDate = apiFormat.parse(snap.apiTimeStr) ?: Date()
            view.findViewById<TextView>(R.id.tv_weather_time).text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(weatherDate)
            view.findViewById<TextView>(R.id.tv_weather_day).text = SimpleDateFormat("EEEE", Locale.getDefault()).format(weatherDate)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        var (backgroundRes, headerRes) = when (snap.code) {
            0, 1 -> R.drawable.bg_weather_sunny to R.drawable.bg_header_sunny
            2, 3, in 45..48 -> R.drawable.bg_weather_cloudy to R.drawable.bg_header_cloudy
            in 51..65, in 80..82 -> R.drawable.bg_weather_rainy to R.drawable.bg_header_rainy
            in 71..77, 85, 86 -> R.drawable.bg_weather_snowy to R.drawable.bg_header_sunny
            95, 96, 99 -> R.drawable.bg_weather_rainy to R.drawable.bg_header_rainy
            else -> R.drawable.bg_weather_sunny to R.drawable.bg_header_sunny
        }

        if (isHeavyRain) {
            backgroundRes = R.drawable.bg_weather_rainy
            headerRes = R.drawable.bg_header_rainy
        }

        view.findViewById<View>(R.id.layout_weather_container).setBackgroundResource(backgroundRes)
        headerView?.setBackgroundResource(headerRes)

        var conditionColor = when (snap.code) {
            51, 53, 55, 61, 80 -> Color.parseColor("#FFEB3B")
            63, 81 -> Color.parseColor("#FF9800")
            65, 82, 95, 96, 99 -> Color.parseColor("#FF5252")
            else -> Color.WHITE
        }

        if (isHeavyRain) {
            conditionColor = Color.parseColor("#FF5252")
        }

        view.findViewById<TextView>(R.id.tv_weather_condition).setTextColor(conditionColor)
        view.findViewById<ImageView>(R.id.iv_weather_icon).setColorFilter(conditionColor)

        statusTitle?.setTextColor(Color.WHITE)
        statusDesc?.setTextColor(Color.parseColor("#E0E0E0"))

        val advisory = getAutoAdvisory(snap.code) ?: if (intensity > 15.0) "Heavy Rainfall Warning: Seek Shelter" else null

        val advisoryBanner = view.findViewById<View>(R.id.layout_weather_advisory)
        val advisoryText = view.findViewById<TextView>(R.id.tv_weather_advisory)
        if (advisory != null) {
            advisoryBanner.visibility = View.VISIBLE
            advisoryText.text = advisory
        } else {
            advisoryBanner.visibility = View.GONE
        }

        var iconRes = when (snap.code) {
            0, 1 -> R.drawable.ic_sun
            2, 3, in 45..48 -> R.drawable.ic_cloud
            in 51..55, 61, 80 -> R.drawable.ic_drizzle
            63, 81 -> R.drawable.ic_rain
            65, 82, 95, 96, 99 -> R.drawable.ic_storm
            else -> R.drawable.ic_cloud
        }

        if (isHeavyRain) {
            iconRes = R.drawable.ic_storm
        }

        view.findViewById<ImageView>(R.id.iv_weather_icon).setImageResource(iconRes)
    }

    fun updateLandslideUI(view: View, snap: WeatherSnapshot) {
        val rain = snap.rain24h
        val (status, color, desc) = when {
            rain > 100.0 -> Triple("CRITICAL", "#F44336", "Extremely high risk! Cumulative rainfall has exceeded 100mm. Evacuate if in high-risk zones.")
            rain > 60.0 -> Triple("HIGH RISK", "#FF9800", "High risk of landslides due to heavy saturation. Monitor slopes and follow BDRRMO advice.")
            rain > 30.0 -> Triple("MODERATE", "#FBC02D", "Moderate risk. Ground is saturated. Avoid landslide-prone areas in San Jose.")
            else -> Triple("LOW RISK", "#4CAF50", "Low risk based on current rainfall. Stay alert for any updates during rainy seasons.")
        }

        view.findViewById<View>(R.id.layout_landslide_container).setBackgroundColor(Color.parseColor(color))
        view.findViewById<TextView>(R.id.tv_landslide_status).text = status
        view.findViewById<TextView>(R.id.tv_landslide_desc).text = desc
        view.findViewById<TextView>(R.id.tv_24h_rainfall).text = "24h Rain: ${String.format(Locale.US, "%.1f", rain)}mm"

        val saturation = when {
            rain > 80.0 -> "Very High"
            rain > 50.0 -> "High"
            rain > 20.0 -> "Moderate"
            else -> "Low"
        }
        view.findViewById<TextView>(R.id.tv_soil_moisture).text = "Soil Saturation: $saturation"
    }

    fun updateEarthquakeUI(view: View, snap: EarthquakeSnapshot) {
        val isRecent = (System.currentTimeMillis() - snap.timeMillis) < (24 * 60 * 60 * 1000L)
        val isNearby = snap.distanceKm < 100.0 // Increased radius for testing
        val isSignificant = snap.magnitude > 2.5 // Lowered threshold for testing
        
        // Task 3: Handle default/empty case (magnitude 0.0)
        if (snap.magnitude == 0.0) {
            view.findViewById<View>(R.id.layout_earthquake_container).setBackgroundColor(Color.parseColor("#1B5E20"))
            view.findViewById<TextView>(R.id.tv_earthquake_status).text = "NO RECENT QUAKES"
            view.findViewById<TextView>(R.id.tv_earthquake_desc).text = "No significant earthquakes detected within 100km of Antipolo City in the last 24 hours."
            view.findViewById<TextView>(R.id.tv_latest_mag).text = "Latest: ---"
            view.findViewById<TextView>(R.id.tv_quake_distance).text = "Distance: ---"
            view.findViewById<ImageView>(R.id.iv_earthquake_icon).setImageResource(R.drawable.ic_done)
            return
        }

        val (status, color, desc) = when {
            isRecent && isSignificant && isNearby -> Triple("DANGER: NEARBY QUAKE", "#B71C1C", "A significant earthquake occurred very close to Antipolo recently. Expect aftershocks.")
            isRecent && isNearby -> Triple("RECENT LOCAL QUAKE", "#E65100", "A light earthquake was detected nearby. Monitor for local advisories.")
            isRecent -> Triple("RECENT REGIONAL QUAKE", "#2E7D32", "Recent activity detected in the region, but not immediately threatening to Antipolo.")
            else -> Triple("NO RECENT QUAKES", "#1B5E20", "No significant earthquakes detected within 100km of Antipolo City in the last 24 hours.")
        }

        view.findViewById<View>(R.id.layout_earthquake_container).setBackgroundColor(Color.parseColor(color))
        view.findViewById<TextView>(R.id.tv_earthquake_status).text = status
        view.findViewById<TextView>(R.id.tv_earthquake_desc).text = desc
        view.findViewById<TextView>(R.id.tv_latest_mag).text = "Latest: M ${String.format(Locale.US, "%.1f", snap.magnitude)}"
        view.findViewById<TextView>(R.id.tv_quake_distance).text = "Distance: ${snap.distanceKm.toInt()} km"

        val iconRes = if (isRecent && (isNearby || isSignificant)) R.drawable.ic_alerts else R.drawable.ic_done
        view.findViewById<ImageView>(R.id.iv_earthquake_icon).setImageResource(iconRes)
    }

    private fun getWeatherDescription(code: Int): String {
        return when (code) {
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
