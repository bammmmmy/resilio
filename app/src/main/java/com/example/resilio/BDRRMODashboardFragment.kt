package com.example.resilio

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.resilio.databinding.FragmentBdrrmoDashboardBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BDRRMODashboardFragment : Fragment(R.layout.fragment_bdrrmo_dashboard) {

    private var _binding: FragmentBdrrmoDashboardBinding? = null
    private val binding get() = _binding!!
    private val client = OkHttpClient()

    private var lastWeatherAdvisory: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentBdrrmoDashboardBinding.bind(view)

        binding.cardAskResilio.root.setOnClickListener {
            findNavController().navigate(R.id.action_bdrrmoDashboardFragment_to_aiChatFragment)
        }

        fetchWeather()
        startRealTimeClock()
    }

    private fun startRealTimeClock() {
        lifecycleScope.launch {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
            while (isActive) {
                val now = Date()
                if (_binding != null) {
                    binding.weatherCard.tvWeatherTime.text = timeFormat.format(now)
                    binding.weatherCard.tvWeatherDay.text = dayFormat.format(now)
                }
                delay(1000)
            }
        }
    }

    private fun updateAdvisoryBanner() {
        if (_binding == null) return

        val displayAdvisory = lastWeatherAdvisory
        if (displayAdvisory != null) {
            binding.weatherCard.layoutWeatherAdvisory.visibility = View.VISIBLE
            binding.weatherCard.tvWeatherAdvisory.text = displayAdvisory
        } else {
            binding.weatherCard.layoutWeatherAdvisory.visibility = View.GONE
        }
    }

    private fun fetchWeather() {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=14.5845&longitude=121.1754&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_gusts_10m,precipitation&hourly=precipitation_probability&timezone=auto"
        
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
                    
                    val hourly = jsonObject.getJSONObject("hourly")
                    val times = hourly.getJSONArray("time")
                    val currentTimeStr = current.getString("time").substring(0, 13) + ":00"
                    var currentPrecipProb = 0
                    
                    for (i in 0 until times.length()) {
                        if (times.getString(i).startsWith(currentTimeStr)) {
                            currentPrecipProb = hourly.getJSONArray("precipitation_probability").getInt(i)
                            break
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        updateWeatherUI(tempC, code, humidity, windSpeed, windGusts, currentPrecipProb)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateWeatherUI(tempC: Double, code: Int, humidity: Int, windSpeed: Double, windGusts: Double, precipProb: Int) {
        if (_binding == null) return
        
        binding.weatherCard.weatherLoadingProgress.visibility = View.GONE
        binding.weatherCard.layoutWeatherData.alpha = 1.0f
        
        val tempF = (tempC * 9/5) + 32
        binding.weatherCard.tvWeatherTemp.text = getString(R.string.temp_format_dual, tempC.toInt(), tempF.toInt())
        binding.weatherCard.tvWeatherCondition.text = getWeatherDescription(code)
        binding.weatherCard.tvWeatherHumidity.text = getString(R.string.humidity_format, humidity)
        
        binding.weatherCard.tvWeatherWind.text = if (windGusts > windSpeed * 1.5) {
            "Wind: ${windSpeed.toInt()}-${windGusts.toInt()} km/h"
        } else {
            getString(R.string.wind_format, windSpeed)
        }

        binding.weatherCard.tvWeatherPrecip.text = getString(R.string.precip_format, precipProb)
        
        // Time and Day are updated in real-time by startRealTimeClock()

        val conditionColor = when (code) {
            in 51..55, 61, 80 -> Color.parseColor("#FFEB3B")
            63, 81 -> Color.parseColor("#FF9800")
            65, 82, 95, 96, 99 -> Color.parseColor("#FF5252")
            else -> Color.WHITE
        }
        binding.weatherCard.tvWeatherCondition.setTextColor(conditionColor)
        binding.weatherCard.ivWeatherIcon.setColorFilter(conditionColor)

        lastWeatherAdvisory = getAutoAdvisory(code)
        updateAdvisoryBanner()
        
        val iconRes = when (code) {
            0, 1 -> R.drawable.ic_sun
            2, 3, in 45..48 -> R.drawable.ic_cloud
            in 51..55, 61, 80 -> R.drawable.ic_drizzle
            63, 81 -> R.drawable.ic_rain
            65, 82, 95, 96, 99 -> R.drawable.ic_storm
            else -> R.drawable.ic_cloud
        }
        binding.weatherCard.ivWeatherIcon.setImageResource(iconRes)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
