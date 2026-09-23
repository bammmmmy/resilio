package com.example.resilio

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class WeatherDetailFragment : Fragment(R.layout.fragment_weather_detail) {

    private val client = OkHttpClient()
    private val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.US)
    private val hourFormat = SimpleDateFormat("h a", Locale.US)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnWeatherBack).setOnClickListener { findNavController().navigateUp() }
        loadWeather(view)
    }

    private fun loadWeather(view: View) {
        val loading = view.findViewById<View>(R.id.weatherDetailLoading)
        val content = view.findViewById<View>(R.id.weatherDetailContent)
        val error = view.findViewById<TextView>(R.id.weatherDetailError)

        lifecycleScope.launch {
            try {
                val weather = runCatching { fetchSharedWeather() }
                    .getOrElse { withContext(Dispatchers.IO) { fetchWeather() } }
                renderWeather(view, weather)
                loading.visibility = View.GONE
                content.visibility = View.VISIBLE
            } catch (exception: Exception) {
                loading.visibility = View.GONE
                error.text = "Weather data is temporarily unavailable. Please try again later."
                error.visibility = View.VISIBLE
            }
        }
    }

    private fun fetchWeather(): JSONObject {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=14.5845&longitude=121.1754" +
            "&current=temperature_2m,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,wind_gusts_10m" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,precipitation,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,sunrise,sunset" +
            "&forecast_days=7&past_days=1&timezone=Asia%2FSingapore"
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        if (!response.isSuccessful) throw IllegalStateException("Weather request failed")
        return JSONObject(response.body?.string() ?: throw IllegalStateException("Empty weather response"))
    }

    private suspend fun fetchSharedWeather(): JSONObject = suspendCoroutine { continuation ->
        Thread {
            try {
                val response = client.newCall(Request.Builder().url(WEATHER_ENDPOINT).build()).execute()
                if (!response.isSuccessful) throw IllegalStateException("Shared weather request failed")
                continuation.resume(JSONObject(response.body?.string() ?: throw IllegalStateException("Empty shared weather response")))
            } catch (error: Exception) {
                continuation.resumeWithException(error)
            }
        }.start()
    }

    companion object { private const val WEATHER_ENDPOINT = "https://us-central1-resilio-ab61f.cloudfunctions.net/getWeatherSnapshot" }

    private fun renderWeather(view: View, root: JSONObject) {
        val current = root.getJSONObject("current")
        val hourly = root.getJSONObject("hourly")
        val daily = root.getJSONObject("daily")
        val currentCode = current.getInt("weather_code")
        val currentRain = current.getDouble("precipitation")
        val currentTemp = current.getDouble("temperature_2m")
        val currentHumidity = current.getInt("relative_humidity_2m")
        val currentWind = current.getDouble("wind_speed_10m")
        val currentGust = current.getDouble("wind_gusts_10m")
        val times = hourly.getJSONArray("time")
        val probabilities = hourly.getJSONArray("precipitation_probability")
        val hourlyPrecipitation = hourly.getJSONArray("precipitation")
        val currentIndex = (0 until times.length()).firstOrNull { times.getString(it) == current.getString("time") } ?: -1
        val rainChance = if (root.has("precipProbability")) root.optInt("precipProbability", 0) else probabilities.optInt(currentIndex.coerceAtLeast(0), 0)
        val rainfall24h = if (currentIndex >= 0) {
            val rainStart = (currentIndex - 23).coerceAtLeast(0)
            (rainStart..currentIndex).sumOf { hourlyPrecipitation.optDouble(it, 0.0) }
        } else {
            root.optDouble("rain24h", 0.0)
        }
        val alert = WeatherCache.getWeatherAlert(currentCode, currentRain, rainfall24h)

        view.findViewById<TextView>(R.id.tvWeatherDetailTemp).text = String.format(Locale.US, "%.0f°C", currentTemp)
        view.findViewById<TextView>(R.id.tvWeatherDetailCondition).text = conditionText(currentCode, currentRain)
        view.findViewById<TextView>(R.id.tvWeatherDetailMeta).text = "Precipitation ${formatMm(currentRain)}   •   Wind ${formatSpeed(currentWind)}   •   Humidity $currentHumidity%"
        view.findViewById<android.widget.ImageView>(R.id.ivWeatherDetailIcon).apply {
            setImageResource(WeatherCache.getIcon(currentCode))
            setColorFilter(Color.WHITE)
        }

        val summary = view.findViewById<android.widget.GridLayout>(R.id.weatherSummaryGrid)
        summary.removeAllViews()
        addSummary(summary, "Rain chance", "$rainChance%")
        addSummary(summary, "24h rainfall", formatMm(rainfall24h))
        addSummary(summary, "Wind gusts", String.format(Locale.US, "%.0f km/h", currentGust))
        addSummary(summary, "Condition", WeatherCache.getConditionName(currentCode))

        val hourlyList = view.findViewById<LinearLayout>(R.id.hourlyWeatherList)
        hourlyList.removeAllViews()
        val temperatures = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")
        val precipitation = hourly.getJSONArray("precipitation")
        val winds = hourly.getJSONArray("wind_speed_10m")
        val currentTime = current.getString("time")
        val currentHourIndex = (0 until times.length()).firstOrNull { times.getString(it) == currentTime } ?: 0
        val start = (currentHourIndex + 1).coerceAtMost(times.length() - 1)
        for (offset in 0 until 24) {
            val index = start + offset
            if (index >= times.length()) break
            addHourlyRow(hourlyList, times.getString(index), temperatures.getDouble(index), codes.getInt(index), probabilities.getInt(index), precipitation.getDouble(index), winds.getDouble(index))
        }

        val dailyList = view.findViewById<LinearLayout>(R.id.dailyWeatherList)
        dailyList.removeAllViews()
        val dailyTimes = daily.getJSONArray("time")
        val dailyMax = daily.getJSONArray("temperature_2m_max")
        val dailyMin = daily.getJSONArray("temperature_2m_min")
        val dailyCodes = daily.getJSONArray("weather_code")
        val dailyRainChance = daily.getJSONArray("precipitation_probability_max")
        val dailyRain = daily.getJSONArray("precipitation_sum")
        val currentDate = current.getString("time").substring(0, 10)
        val dailyStartIndex = (0 until dailyTimes.length()).firstOrNull { dailyTimes.getString(it) == currentDate } ?: 0
        for (offset in 0 until 7) {
            val index = dailyStartIndex + offset
            if (index >= dailyTimes.length()) break
            addDailyRow(dailyList, dailyTimes.getString(index), dailyCodes.getInt(index), dailyMin.getDouble(index), dailyMax.getDouble(index), dailyRainChance.getInt(index), dailyRain.getDouble(index))
        }

        val alerts = view.findViewById<LinearLayout>(R.id.weatherAlertsList)
        alerts.removeAllViews()
        if (alert == null) {
            addAlertRow(alerts, "No active weather alerts", "Current conditions do not indicate a weather advisory.", false)
        } else {
            addAlertRow(alerts, alert.title, alert.description, true)
        }
    }

    private fun addSummary(grid: android.widget.GridLayout, label: String, value: String) {
        val card = MaterialCardView(requireContext()).apply {
            radius = 10f
            setCardBackgroundColor(Color.WHITE)
            strokeColor = Color.rgb(213, 225, 237)
            strokeWidth = 1
            val params = android.widget.GridLayout.LayoutParams().apply {
                width = 0
                height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                setMargins(0, 0, 8, 8)
            }
            layoutParams = params
        }
        val content = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 14, 14, 14) }
        content.addView(TextView(requireContext()).apply { text = label; setTextColor(Color.rgb(120, 144, 167)); textSize = 10f })
        content.addView(TextView(requireContext()).apply { text = value; setTextColor(Color.rgb(49, 86, 120)); textSize = 16f; setPadding(0, 5, 0, 0) })
        card.addView(content)
        grid.addView(card)
    }

    private fun addHourlyRow(list: LinearLayout, time: String, temp: Double, code: Int, rainChance: Int, rain: Double, wind: Double) {
        val card = MaterialCardView(requireContext()).apply {
            radius = 10f
            strokeColor = Color.rgb(205, 226, 244)
            strokeWidth = 1
            setCardBackgroundColor(Color.rgb(247, 251, 255))
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(10, 10, 10, 10)
        }
        content.addView(TextView(requireContext()).apply {
            text = formatHour(time)
            setTextColor(Color.rgb(82, 113, 141))
            textSize = 10f
        })
        content.addView(android.widget.ImageView(requireContext()).apply {
            setImageResource(WeatherCache.getIcon(code))
            setColorFilter(Color.rgb(34, 121, 210))
            layoutParams = LinearLayout.LayoutParams(28, 28).apply { topMargin = 7; bottomMargin = 5 }
        })
        content.addView(TextView(requireContext()).apply {
            text = String.format(Locale.US, "%.0f°C", temp)
            setTextColor(Color.rgb(36, 74, 114))
            textSize = 16f
        })
        content.addView(TextView(requireContext()).apply {
            text = WeatherCache.getConditionName(code)
            setTextColor(Color.rgb(97, 124, 149))
            textSize = 9f
        })
        content.addView(TextView(requireContext()).apply {
            text = String.format(Locale.US, "%d%% rain", rainChance)
            setTextColor(Color.rgb(34, 121, 210))
            textSize = 9f
            setPadding(0, 5, 0, 0)
        })
        card.addView(content)
        list.addView(card, LinearLayout.LayoutParams(128, 150).apply { setMargins(0, 0, 8, 0) })
    }

    private fun addDailyRow(list: LinearLayout, date: String, code: Int, min: Double, max: Double, rainChance: Int, rain: Double) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(10, 12, 10, 12)
            setBackgroundColor(Color.WHITE)
        }
        row.addView(TextView(requireContext()).apply {
            text = formatDate(date)
            setTextColor(Color.rgb(82, 113, 141))
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(92, -2)
        })
        row.addView(android.widget.ImageView(requireContext()).apply {
            setImageResource(WeatherCache.getIcon(code))
            setColorFilter(Color.rgb(34, 121, 210))
            layoutParams = LinearLayout.LayoutParams(30, 30).apply { marginEnd = 10 }
        })
        val details = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        details.addView(TextView(requireContext()).apply {
            text = WeatherCache.getConditionName(code)
            setTextColor(Color.rgb(36, 74, 114))
            textSize = 11f
        })
        details.addView(TextView(requireContext()).apply {
            text = String.format(Locale.US, "%d%% chance of rain", rainChance)
            setTextColor(Color.rgb(82, 113, 141))
            textSize = 9f
            setPadding(0, 3, 0, 0)
        })
        row.addView(details)
        row.addView(TextView(requireContext()).apply {
            text = String.format(Locale.US, "%.0f° / %.0f°", max, min)
            setTextColor(Color.rgb(36, 74, 114))
            textSize = 11f
        })
        list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 1) })
    }

    private fun addAlertRow(list: LinearLayout, title: String, description: String, warning: Boolean) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 14)
            setBackgroundColor(if (warning) Color.rgb(255, 240, 241) else Color.rgb(239, 250, 244))
        }
        row.addView(TextView(requireContext()).apply { text = title; setTextColor(if (warning) Color.rgb(177, 50, 61) else Color.rgb(23, 116, 79)); textSize = 13f })
        row.addView(TextView(requireContext()).apply { text = description; setTextColor(Color.rgb(97, 124, 149)); textSize = 11f; setPadding(0, 5, 0, 0) })
        list.addView(row)
    }

    private fun conditionText(code: Int, rain: Double): String {
        return when {
            rain > 10 -> "Heavy Rain"
            rain > 2.5 -> "Moderate Rain"
            rain > 0 -> "Light Rain"
            else -> WeatherCache.getConditionName(code)
        }
    }

    private fun formatHour(value: String): String = runCatching { hourFormat.format(SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(value)!!) }.getOrDefault(value.takeLast(5))
    private fun formatDate(value: String): String = runCatching { dateFormat.format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)!!) }.getOrDefault(value)
    private fun formatTime(value: String): String = value.replace('T', ' ')
    private fun formatMm(value: Double): String = String.format(Locale.US, "%.1f mm", value)
    private fun formatSpeed(value: Double): String = String.format(Locale.US, "%.1f km/h", value)
}
