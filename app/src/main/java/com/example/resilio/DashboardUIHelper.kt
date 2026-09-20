package com.example.resilio

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.resilio.util.TimeUtils
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Date
import java.util.Locale

object DashboardUIHelper {

    fun fetchWeather(
        context: Context,
        lifecycleScope: LifecycleCoroutineScope,
        forceRefresh: Boolean = false,
        onComplete: () -> Unit
    ) {
        if (!forceRefresh && WeatherCache.isFresh()) {
            onComplete()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val lat = 14.5845
                val lon = 121.1754
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,wind_gusts_10m" +
                        "&hourly=precipitation,precipitation_probability&daily=precipitation_sum&timezone=Asia%2FSingapore"

                val response = client.newCall(Request.Builder().url(url).build()).execute()
                val json = response.body?.string() ?: return@launch
                val root = JSONObject(json)
                val current = root.getJSONObject("current")
                val hourly = root.getJSONObject("hourly")
                
                val apiTime = current.getString("time")
                val currentTimeStr = apiTime.substring(0, 13) + ":00"
                val times = hourly.getJSONArray("time")
                val probs = hourly.getJSONArray("precipitation_probability")
                val hourlyPrecip = hourly.getJSONArray("precipitation")
                
                var currentPrecipProb = 0
                var currentIndex = -1
                for (i in 0 until times.length()) {
                    if (times.getString(i).startsWith(currentTimeStr)) {
                        currentPrecipProb = probs.getInt(i)
                        currentIndex = i
                        break
                    }
                }

                // Rolling 24h total calculation
                var rollingRain24h = 0.0
                if (currentIndex != -1) {
                    val start = (currentIndex - 23).coerceAtLeast(0)
                    for (i in start..currentIndex) {
                        rollingRain24h += hourlyPrecip.getDouble(i)
                    }
                }

                val snap = WeatherSnapshot(
                    tempC = current.getDouble("temperature_2m"),
                    humidity = current.getInt("relative_humidity_2m"),
                    currentPrecipIntensity = current.getDouble("precipitation"),
                    windSpeed = current.getDouble("wind_speed_10m"),
                    windGusts = current.getDouble("wind_gusts_10m"),
                    code = current.getInt("weather_code"),
                    rain24h = rollingRain24h,
                    precipProb = currentPrecipProb,
                    apiTimeStr = apiTime,
                    fetchedAtMillis = System.currentTimeMillis()
                )

                WeatherCache.snapshot = snap
                
                withContext(Dispatchers.Main) {
                    DashboardNotificationHelper.notifyIfNecessary(context)
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun fetchEarthquakeData(
        context: Context,
        lifecycleScope: LifecycleCoroutineScope,
        onComplete: () -> Unit
    ) {
        if (EarthquakeCache.isFresh()) {
            onComplete()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&latitude=14.5845&longitude=121.1754&maxradiuskm=100&minmagnitude=2.0&orderby=time&limit=1")
                    .build()

                val response = client.newCall(request).execute()
                val json = response.body?.string() ?: return@launch
                val root = JSONObject(json)
                val features = root.getJSONArray("features")

                if (features.length() > 0) {
                    val props = features.getJSONObject(0).getJSONObject("properties")
                    val coords = features.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
                    
                    val quake = EarthquakeData(
                        id = features.getJSONObject(0).getString("id"),
                        magnitude = props.getDouble("mag"),
                        location = props.getString("place"),
                        place = props.getString("place"),
                        timeMillis = props.getLong("time"),
                        latitude = coords.getDouble(1),
                        longitude = coords.getDouble(0),
                        depth = coords.getDouble(2),
                        distanceKm = distanceBetweenKm(14.5845, 121.1754, coords.getDouble(1), coords.getDouble(0))
                    )
                    EarthquakeCache.lastQuake = quake
                    EarthquakeCache.lastFetched = System.currentTimeMillis()
                }

                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateWeatherUI(
        view: View,
        snap: WeatherSnapshot,
        headerView: View? = null
    ) {
        view.findViewById<View>(R.id.layout_weather_loading).visibility = View.GONE
        view.findViewById<View>(R.id.layout_weather_content).visibility = View.VISIBLE

        val tempText = "${snap.tempC.toInt()}°C"
        view.findViewById<TextView>(R.id.tv_weather_temp).text = tempText
        
        val condition = WeatherCache.getConditionName(snap.code)
        val intensity = snap.currentPrecipIntensity
        
        val displayCondition = when {
            intensity > 10.0 -> "Heavy Rain"
            intensity > 2.5 -> "Moderate Rain"
            intensity > 0.0 -> "Light Rain"
            else -> condition
        }
        
        view.findViewById<TextView>(R.id.tv_weather_condition).text = displayCondition
        view.findViewById<TextView>(R.id.tv_weather_label).text = "WEATHER"

        val weatherAlert = WeatherCache.getWeatherAlert(snap.code, snap.currentPrecipIntensity, snap.rain24h)
        val advisoryLayout = view.findViewById<View>(R.id.layout_weather_advisory)
        val advisoryTv = view.findViewById<TextView>(R.id.tv_weather_advisory)

        if (weatherAlert != null) {
            advisoryLayout.visibility = View.VISIBLE
            advisoryTv.text = "${weatherAlert.title}: ${weatherAlert.description}"
        } else {
            advisoryLayout.visibility = View.GONE
        }

        view.findViewById<TextView>(R.id.tv_weather_humidity).text = "Humidity   ${snap.humidity}%"
        view.findViewById<TextView>(R.id.tv_weather_wind).text = "Wind speed   ${String.format(Locale.US, "%.1f", snap.windSpeed)} km/h"
        view.findViewById<TextView>(R.id.tv_weather_precip).text = "Precipitation   ${String.format(Locale.US, "%.1f", snap.currentPrecipIntensity)} mm"
        view.findViewById<TextView>(R.id.tv_rain_24h).text = String.format(Locale.US, "24h Rain: %.1f mm", snap.rain24h)

        val intensityTv = view.findViewById<TextView>(R.id.tv_rain_intensity)
        if (snap.currentPrecipIntensity > 0) {
            intensityTv.visibility = View.VISIBLE
            intensityTv.text = String.format(Locale.US, "Rain Intensity: %.1f mm/h", snap.currentPrecipIntensity)
        } else {
            intensityTv.visibility = View.GONE
        }

        // Time forced to PH Timezone
        val updateTime = Date(snap.fetchedAtMillis)
        view.findViewById<TextView>(R.id.tv_weather_time).text = TimeUtils.formatToPhTime(updateTime, "h:mm a")
        view.findViewById<TextView>(R.id.tv_weather_day).text = TimeUtils.formatToPhTime(updateTime, "EEEE")

        view.findViewById<View>(R.id.layout_weather_container).setBackgroundColor(Color.parseColor("#F7FBFF"))
        headerView?.setBackgroundResource(R.drawable.bg_dashboard_header)

        view.findViewById<ImageView>(R.id.iv_weather_icon).setImageResource(R.drawable.ic_weather_cloud)
        view.findViewById<ImageView>(R.id.iv_weather_icon).clearColorFilter()
    }

    fun updateLandslideUI(view: View, snap: WeatherSnapshot) {
        val rain = snap.rain24h
        val assessment = WeatherCache.getLandslideAssessment(rain)
        val risk = assessment.label.uppercase(Locale.US)

        view.findViewById<TextView>(R.id.tv_landslide_status).text = risk
        view.findViewById<TextView>(R.id.tv_landslide_desc).text = "Based on rainfall & soil condition"
        
        view.findViewById<TextView>(R.id.tv_24h_rainfall).text = String.format(Locale.US, "Rainfall: %.1f mm / 24h", rain)
        
        val saturation = when {
            else -> assessment.saturation
        }
        val saturationTv = view.findViewById<TextView>(R.id.tv_soil_moisture)
        saturationTv.text = "Soil condition: $saturation"
        
        val riskColor = when (risk) {
            "CRITICAL RISK", "HIGH RISK" -> Color.parseColor("#B5332E")
            else -> Color.parseColor("#B77200")
        }
        val riskStatus = view.findViewById<TextView>(R.id.tv_landslide_status)
        riskStatus.setTextColor(riskColor)
        (riskStatus.background as? GradientDrawable)?.setColor(
            if (risk == "CRITICAL RISK" || risk == "HIGH RISK") Color.parseColor("#FFFFE0D8") else Color.parseColor("#FFFFF0CC")
        )

        // Set saturation color for better visual feedback
        val saturationColor = when (saturation) {
            "Very high" -> Color.parseColor("#B5332E")
            "High" -> Color.parseColor("#C47300")
            "Moderate" -> Color.parseColor("#B77200")
            else -> Color.parseColor("#315678")
        }
        saturationTv.setTextColor(saturationColor)
    }

    fun updateEarthquakeUI(view: View, quake: EarthquakeData?) {
        val active = quake != null
        view.findViewById<TextView>(R.id.tv_earthquake_status).text = if (active) "Recent activity detected" else "No recent earthquake"
        view.findViewById<TextView>(R.id.tv_earthquake_desc).text = quake?.location ?: "affecting the area"
        view.findViewById<TextView>(R.id.tv_latest_mag).text = quake?.let { String.format(Locale.US, "%.1f", it.magnitude) } ?: "--"
        view.findViewById<TextView>(R.id.tv_quake_distance).text = quake?.location ?: "--"
        view.findViewById<TextView>(R.id.tv_quake_time).text = quake?.let { TimeUtils.formatToPhTime(Date(it.timeMillis), "h:mm a") } ?: "--"

        val status = view.findViewById<TextView>(R.id.tv_earthquake_status)
        status.setTextColor(Color.parseColor(if (active) "#137D58" else "#687E91"))
        (status.background as? GradientDrawable)?.setColor(Color.parseColor(if (active) "#FFDFF6EA" else "#FFEDF2F6"))
    }

    private fun distanceBetweenKm(firstLatitude: Double, firstLongitude: Double, secondLatitude: Double, secondLongitude: Double): Double {
        val earthRadiusKm = 6371.0
        val latitudeDelta = Math.toRadians(secondLatitude - firstLatitude)
        val longitudeDelta = Math.toRadians(secondLongitude - firstLongitude)
        val a = kotlin.math.sin(latitudeDelta / 2) * kotlin.math.sin(latitudeDelta / 2) + kotlin.math.cos(Math.toRadians(firstLatitude)) * kotlin.math.cos(Math.toRadians(secondLatitude)) * kotlin.math.sin(longitudeDelta / 2) * kotlin.math.sin(longitudeDelta / 2)
        return earthRadiusKm * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }
}
