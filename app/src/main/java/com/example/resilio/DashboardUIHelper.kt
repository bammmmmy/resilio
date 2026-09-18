package com.example.resilio

import android.content.Context
import android.graphics.Color
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

        val weatherAlert = WeatherCache.getWeatherAlert(snap.code, snap.currentPrecipIntensity, snap.rain24h)
        val advisoryLayout = view.findViewById<View>(R.id.layout_weather_advisory)
        val advisoryTv = view.findViewById<TextView>(R.id.tv_weather_advisory)

        if (weatherAlert != null) {
            advisoryLayout.visibility = View.VISIBLE
            advisoryTv.text = "${weatherAlert.title}: ${weatherAlert.description}"
        } else {
            advisoryLayout.visibility = View.GONE
        }

        view.findViewById<TextView>(R.id.tv_weather_humidity).text = "Humidity: ${snap.humidity}%"
        
        view.findViewById<TextView>(R.id.tv_weather_wind).text = if (snap.windGusts > snap.windSpeed * 1.5) {
            "Wind: ${snap.windSpeed.toInt()} km/h (Gusts: ${snap.windGusts.toInt()})"
        } else {
            "Wind Speed: ${snap.windSpeed.toInt()} km/h"
        }

        view.findViewById<TextView>(R.id.tv_weather_precip).text = "Precipitation: ${snap.precipProb}%"
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

        // Condition-based assets
        val backgroundRes = when (snap.code) {
            0, 1 -> R.drawable.bg_weather_sunny
            2, 3, in 45..48 -> R.drawable.bg_weather_cloudy
            in 51..65, in 80..82 -> R.drawable.bg_weather_rainy
            in 71..77, 85, 86 -> R.drawable.bg_weather_snowy
            95, 96, 99 -> R.drawable.bg_weather_rainy
            else -> R.drawable.bg_weather_sunny
        }

        view.findViewById<View>(R.id.layout_weather_container).setBackgroundResource(backgroundRes)
        headerView?.setBackgroundResource(R.drawable.bg_dashboard_header)

        val conditionColor = when (snap.code) {
            0, 1 -> Color.parseColor("#FFD600")
            2, 3, in 45..48 -> Color.parseColor("#90A4AE")
            51, 53, 55, 61, 80 -> Color.parseColor("#FFEB3B")
            else -> Color.parseColor("#EF5350")
        }

        view.findViewById<ImageView>(R.id.iv_weather_icon).setImageResource(WeatherCache.getIcon(snap.code))
        view.findViewById<ImageView>(R.id.iv_weather_icon).setColorFilter(conditionColor)
    }

    fun updateLandslideUI(view: View, snap: WeatherSnapshot) {
        val rain = snap.rain24h
        val assessment = WeatherCache.getLandslideAssessment(rain)
        val risk = assessment.label

        view.findViewById<TextView>(R.id.tv_landslide_status).text = risk
        view.findViewById<TextView>(R.id.tv_landslide_desc).text = assessment.copy
        
        view.findViewById<TextView>(R.id.tv_24h_rainfall).text = String.format(Locale.US, "24h Rain: %.1f mm", rain)
        
        val saturation = when {
            else -> assessment.saturation
        }
        val saturationTv = view.findViewById<TextView>(R.id.tv_soil_moisture)
        saturationTv.text = "Soil Saturation: $saturation"
        
        val riskColor = when (risk) {
            "Critical risk" -> ContextCompat.getColor(view.context, R.color.emergency_red)
            "High risk" -> ContextCompat.getColor(view.context, R.color.warning_orange)
            "Moderate risk" -> ContextCompat.getColor(view.context, R.color.gold_accent)
            else -> Color.WHITE // Changed from primary_green to avoid clash with brown/blue
        }
        view.findViewById<TextView>(R.id.tv_landslide_status).setTextColor(riskColor)

        // Set saturation color for better visual feedback
        val saturationColor = when (saturation) {
            "Very high" -> ContextCompat.getColor(view.context, R.color.emergency_red)
            "High" -> ContextCompat.getColor(view.context, R.color.warning_orange)
            "Moderate" -> ContextCompat.getColor(view.context, R.color.gold_accent)
            else -> Color.WHITE
        }
        saturationTv.setTextColor(saturationColor)
    }

    fun updateEarthquakeUI(view: View, quake: EarthquakeData) {
        view.findViewById<TextView>(R.id.tv_latest_mag).text = String.format(Locale.US, "Latest: M %.1f", quake.magnitude)
        view.findViewById<TextView>(R.id.tv_earthquake_desc).text = quake.location
        
        val color = when {
            quake.magnitude >= 6.0 -> "#B71C1C"
            quake.magnitude >= 5.0 -> "#EF5350"
            quake.magnitude >= 4.0 -> "#FF9800"
            else -> "#4CAF50"
        }
        view.findViewById<TextView>(R.id.tv_latest_mag).setTextColor(Color.parseColor(color))
    }

    private fun distanceBetweenKm(firstLatitude: Double, firstLongitude: Double, secondLatitude: Double, secondLongitude: Double): Double {
        val earthRadiusKm = 6371.0
        val latitudeDelta = Math.toRadians(secondLatitude - firstLatitude)
        val longitudeDelta = Math.toRadians(secondLongitude - firstLongitude)
        val a = kotlin.math.sin(latitudeDelta / 2) * kotlin.math.sin(latitudeDelta / 2) + kotlin.math.cos(Math.toRadians(firstLatitude)) * kotlin.math.cos(Math.toRadians(secondLatitude)) * kotlin.math.sin(longitudeDelta / 2) * kotlin.math.sin(longitudeDelta / 2)
        return earthRadiusKm * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }
}
