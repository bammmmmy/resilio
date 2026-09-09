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
                    .url("https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&latitude=14.5995&longitude=120.9842&maxradiuskm=500&limit=1")
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
                        depth = coords.getDouble(2)
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

        val (adviceTitle, adviceDesc) = WeatherCache.getSafetyAdvice(snap.code, snap.rain24h)
        val advisoryLayout = view.findViewById<View>(R.id.layout_weather_advisory)
        val advisoryTv = view.findViewById<TextView>(R.id.tv_weather_advisory)
        
        // Show advisory if it's raining, OR high accumulation, OR a special code (Thunderstorm/Snow)
        val isNotClear = snap.code > 3
        val hasRain = snap.currentPrecipIntensity > 0
        val significantTotal = snap.rain24h > 10.0

        if (isNotClear || hasRain || significantTotal) {
            advisoryLayout.visibility = View.VISIBLE
            advisoryTv.text = "$adviceTitle: $adviceDesc"
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
        val (backgroundRes, headerRes) = when (snap.code) {
            0, 1 -> R.drawable.bg_weather_sunny to R.drawable.bg_header_sunny
            2, 3, in 45..48 -> R.drawable.bg_weather_cloudy to R.drawable.bg_header_cloudy
            in 51..65, in 80..82 -> R.drawable.bg_weather_rainy to R.drawable.bg_header_rainy
            in 71..77, 85, 86 -> R.drawable.bg_weather_snowy to R.drawable.bg_header_sunny
            95, 96, 99 -> R.drawable.bg_weather_rainy to R.drawable.bg_header_rainy
            else -> R.drawable.bg_weather_sunny to R.drawable.bg_header_sunny
        }

        view.findViewById<View>(R.id.layout_weather_container).setBackgroundResource(backgroundRes)
        headerView?.setBackgroundResource(headerRes)

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
        val (risk, advice) = when {
            rain > 100 -> "Critical" to "Extreme danger! Evacuate immediately if in slope areas."
            rain > 60 -> "High Risk" to "Landslide likely. Stay alert and prepare to move."
            rain > 30 -> "Moderate" to "Ground is saturated. Monitor for soil movement."
            else -> "Low Risk" to "Current rainfall is within safe limits for slopes."
        }

        view.findViewById<TextView>(R.id.tv_landslide_status).text = risk
        view.findViewById<TextView>(R.id.tv_landslide_desc).text = advice
        
        view.findViewById<TextView>(R.id.tv_24h_rainfall).text = String.format(Locale.US, "24h Rain: %.1f mm", rain)
        
        val saturation = when {
            rain > 80.0 -> "Very High"
            rain > 50.0 -> "High"
            rain > 20.0 -> "Moderate"
            else -> "Low"
        }
        view.findViewById<TextView>(R.id.tv_soil_moisture).text = "Soil Saturation: $saturation"
        
        val riskColor = when (risk) {
            "Critical" -> ContextCompat.getColor(view.context, R.color.emergency_red)
            "High Risk" -> ContextCompat.getColor(view.context, R.color.warning_orange)
            "Moderate" -> ContextCompat.getColor(view.context, R.color.gold_accent)
            else -> ContextCompat.getColor(view.context, R.color.primary_green)
        }
        view.findViewById<TextView>(R.id.tv_landslide_status).setTextColor(riskColor)
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
}
