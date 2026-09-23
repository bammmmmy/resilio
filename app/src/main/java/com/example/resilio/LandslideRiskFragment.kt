package com.example.resilio

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
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

class LandslideRiskFragment : Fragment(R.layout.fragment_landslide_risk) {
    private val client = OkHttpClient()
    private val dateFormat = SimpleDateFormat("EEE", Locale.US)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnLandslideBack).setOnClickListener { findNavController().navigateUp() }
        loadAssessment(view)
    }

    private fun loadAssessment(view: View) {
        val loading = view.findViewById<View>(R.id.landslideLoading)
        val content = view.findViewById<View>(R.id.landslideContent)
        val error = view.findViewById<TextView>(R.id.landslideError)
        lifecycleScope.launch {
            try {
                val forecast = runCatching { fetchForecast() }.getOrElse { withContext(Dispatchers.IO) { fetchDirectForecast() } }
                render(view, forecast)
                loading.visibility = View.GONE
                content.visibility = View.VISIBLE
            } catch (_: Exception) {
                loading.visibility = View.GONE
                error.text = "Landslide assessment is temporarily unavailable. Please try again later."
                error.visibility = View.VISIBLE
            }
        }
    }

    private suspend fun fetchForecast(): JSONObject = suspendCoroutine { continuation ->
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

    private fun fetchDirectForecast(): JSONObject {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=14.5845&longitude=121.1754" +
            "&current=precipitation&hourly=precipitation,precipitation_probability&daily=precipitation_sum&forecast_days=7&past_days=1&timezone=Asia%2FSingapore"
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        if (!response.isSuccessful) throw IllegalStateException("Forecast request failed")
        return JSONObject(response.body?.string() ?: throw IllegalStateException("Empty forecast response"))
    }

    private fun findCurrentHourlyIndex(hourlyTimes: org.json.JSONArray, currentTime: String): Int {
        val exactIndex = (0 until hourlyTimes.length()).firstOrNull { hourlyTimes.getString(it) == currentTime }
        return exactIndex ?: (hourlyTimes.length() - 1).coerceAtLeast(0)
    }

    private fun calculateRainfall24h(hourlyTimes: org.json.JSONArray, hourlyRain: org.json.JSONArray, currentTime: String): Double {
        val currentIndex = findCurrentHourlyIndex(hourlyTimes, currentTime)
        val rainfallStart = (currentIndex - 23).coerceAtLeast(0)
        return (rainfallStart..currentIndex).sumOf { hourlyRain.optDouble(it, 0.0) }
    }

    private fun render(view: View, root: JSONObject) {
        val hourly = root.getJSONObject("hourly")
        val daily = root.getJSONObject("daily")
        val hourlyRain = hourly.getJSONArray("precipitation")
        val hourlyTimes = hourly.getJSONArray("time")
        val currentTime = root.getJSONObject("current").getString("time")
        val currentIndex = findCurrentHourlyIndex(hourlyTimes, currentTime)
        val rainfall24h = calculateRainfall24h(hourlyTimes, hourlyRain, currentTime)
        val recentStart = (currentIndex - 11).coerceAtLeast(0)
        val recentValues = (recentStart..currentIndex).map { hourlyRain.optDouble(it, 0.0) }
        val risk = riskFor(rainfall24h)
        val saturation = when {
            rainfall24h > 80 -> "Very High"
            rainfall24h > 50 -> "High"
            rainfall24h >= 20 -> "Moderate"
            else -> "Low"
        }

        view.findViewById<TextView>(R.id.tvLandslideRiskLevel).apply {
            text = risk.label
            setTextColor(risk.color)
        }
        view.findViewById<TextView>(R.id.tvLandslideRiskCopy).text = risk.copy
        view.findViewById<TextView>(R.id.tvLandslideRainfallTotal).text = String.format(Locale.US, "%.1f mm", rainfall24h)
        view.findViewById<TextView>(R.id.tvLandslideRainfallNote).text = if (rainfall24h >= 20) {
            "ⓘ  Moderate to heavy rain is increasing landslide risk."
        } else {
            "ⓘ  Light rainfall is currently being observed."
        }

        renderSummary(view.findViewById(R.id.landslideSummaryStats), rainfall24h, saturation)
        renderBars(view.findViewById(R.id.landslideRainfallBars), recentValues)
        renderAxis(view.findViewById(R.id.landslideRainfallAxis))
        renderTrend(view.findViewById(R.id.landslideTrendRows), daily.getJSONArray("time"), daily.getJSONArray("precipitation_sum"))
        renderGuidance(view.findViewById(R.id.landslideGuidanceList))
    }

    private fun renderSummary(container: LinearLayout, rainfall: Double, soil: String) {
        container.removeAllViews()
        addStat(container, "Rainfall", String.format(Locale.US, "%.1f mm", rainfall))
        addStat(container, "Soil condition", soil)
        addStat(container, "Terrain", "Steep slopes")
    }

    private fun addStat(container: LinearLayout, label: String, value: String) {
        val item = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        item.addView(TextView(requireContext()).apply { text = label; setTextColor(Color.rgb(82, 113, 141)); textSize = 10f })
        item.addView(TextView(requireContext()).apply { text = value; setTextColor(Color.rgb(23, 61, 110)); textSize = 15f; setPadding(0, 5, 0, 0) })
        container.addView(item)
    }

    private fun renderBars(container: LinearLayout, values: List<Double>) {
        container.removeAllViews()
        val peak = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        values.forEach { value ->
            val bar = View(requireContext()).apply { setBackgroundColor(Color.rgb(55, 145, 229)) }
            container.addView(bar, LinearLayout.LayoutParams(0, (18 + (value / peak * 95)).toInt(), 1f).apply { setMargins(3, 0, 3, 0) })
        }
    }

    private fun renderAxis(container: LinearLayout) {
        container.removeAllViews()
        listOf("12 AM", "6 AM", "12 PM", "6 PM").forEach { label ->
            container.addView(TextView(requireContext()).apply { text = label; setTextColor(Color.rgb(120, 144, 167)); textSize = 9f; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun renderTrend(container: LinearLayout, dates: org.json.JSONArray, rainfall: org.json.JSONArray) {
        container.removeAllViews()

        val entries = (0 until minOf(7, dates.length())).map { index ->
            val day = formatDay(dates.getString(index)).takeIf { it.isNotBlank() } ?: "Day"
            val value = rainfall.optDouble(index, 0.0).toFloat().coerceIn(0f, 80f)
            day to value
        }

        val chart = RiskTrendChartView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (220 * resources.displayMetrics.density).toInt()
            )
            setData(entries)
        }

        container.addView(chart)
    }

    private fun renderGuidance(container: LinearLayout) {
        container.removeAllViews()
        val heading = TextView(requireContext()).apply { text = "Preparedness guidance\nWhat to watch"; setTextColor(Color.rgb(23, 61, 110)); textSize = 19f; setPadding(0, 0, 0, 12) }
        container.addView(heading)
        addGuidance(container, "Rainfall accumulation", "Watch for sustained rain and sudden increases in the 24-hour total.", Color.rgb(34, 121, 210))
        addGuidance(container, "Slope movement", "Report cracks, leaning trees, muddy water, or falling rocks immediately.", Color.rgb(240, 140, 10))
        addGuidance(container, "Preparedness status", "Keep evacuation routes clear and review nearby safe areas.", Color.rgb(25, 145, 97))
    }

    private fun addGuidance(container: LinearLayout, title: String, copy: String, color: Int) {
        val row = LinearLayout(requireContext()).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 7, 0, 7) }
        val badge = TextView(requireContext()).apply { text = "●"; gravity = Gravity.CENTER; setTextColor(color); textSize = 22f; layoutParams = LinearLayout.LayoutParams(42, 42) }
        row.addView(badge)
        val copyLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f); setPadding(10, 0, 0, 0) }
        copyLayout.addView(TextView(requireContext()).apply { text = title; setTextColor(Color.rgb(36, 74, 114)); textSize = 12f })
        copyLayout.addView(TextView(requireContext()).apply { text = copy; setTextColor(Color.rgb(82, 113, 141)); textSize = 10f; setPadding(0, 3, 0, 0) })
        row.addView(copyLayout)
        container.addView(row)
    }

    private fun riskFor(rainfall: Double): Risk {
        return when {
            rainfall > 100 -> Risk("CRITICAL RISK", "Severe rainfall conditions may trigger slope failure. Prepare for immediate action.", Color.rgb(181, 51, 46))
            rainfall > 60 -> Risk("HIGH RISK", "Heavy rainfall is increasing slope instability. Monitor vulnerable areas closely.", Color.rgb(181, 51, 46))
            rainfall >= 20 -> Risk("MODERATE RISK", "Conditions are favorable for landslides in some areas. Keep monitoring and be prepared.", Color.rgb(183, 114, 0))
            else -> Risk("LOW RISK", "Rainfall and soil conditions are currently stable. Continue routine monitoring.", Color.rgb(23, 116, 79))
        }
    }

    private fun formatDay(value: String): String = runCatching { dateFormat.format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)!!) }.getOrDefault(value)
    private data class Risk(val label: String, val copy: String, val color: Int)
}
