package com.example.resilio

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.resilio.databinding.FragmentHomeBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val client = OkHttpClient()

    private var lastWeatherAdvisory: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        setupNavigation()
        setupCallButtons()
        fetchWeather()
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
                    
                    // Find correct index for current hour in precipitation probability
                    val hourly = jsonObject.getJSONObject("hourly")
                    val times = hourly.getJSONArray("time")
                    
                    // Open-Meteo current time string is exact, but hourly array is on the hour.
                    // We need to match the hourly slot (e.g. 12:15 current matches 12:00 hourly)
                    val currentTimeStr = current.getString("time").substring(0, 13) + ":00"
                    var currentPrecipProb = 0
                    
                    for (i in 0 until times.length()) {
                        if (times.getString(i).startsWith(currentTimeStr)) {
                            currentPrecipProb = hourly.getJSONArray("precipitation_probability").getInt(i)
                            break
                        }
                    }
                    
                    val apiTimeStr = current.getString("time") // ISO format "2023-10-25T14:30"
                    
                    withContext(Dispatchers.Main) {
                        updateWeatherUI(tempC, code, humidity, windSpeed, windGusts, currentPrecipProb, apiTimeStr)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateWeatherUI(tempC: Double, code: Int, humidity: Int, windSpeed: Double, windGusts: Double, precipProb: Int, apiTimeStr: String) {
        if (_binding == null) return
        
        // Hide loading indicator and restore alpha
        binding.weatherCard.weatherLoadingProgress.visibility = View.GONE
        binding.weatherCard.layoutWeatherData.alpha = 1.0f
        
        val tempF = (tempC * 9/5) + 32
        binding.weatherCard.tvWeatherTemp.text = getString(R.string.temp_format_dual, tempC.toInt(), tempF.toInt())
        binding.weatherCard.tvWeatherCondition.text = getWeatherDescription(code)
        binding.weatherCard.tvWeatherHumidity.text = getString(R.string.humidity_format, humidity)
        
        // Show wind speed and gusts for better accuracy of "what it feels like"
        binding.weatherCard.tvWeatherWind.text = if (windGusts > windSpeed * 1.5) {
            "Wind: ${windSpeed.toInt()}-${windGusts.toInt()} km/h"
        } else {
            getString(R.string.wind_format, windSpeed)
        }

        binding.weatherCard.tvWeatherPrecip.text = getString(R.string.precip_format, precipProb)
        
        // Parse time directly from API for 100% accuracy
        try {
            val apiFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            val weatherDate = apiFormat.parse(apiTimeStr) ?: Date()
            
            val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            
            binding.weatherCard.tvWeatherDay.text = dayFormat.format(weatherDate)
            binding.weatherCard.tvWeatherTime.text = timeFormat.format(weatherDate)
        } catch (e: Exception) {
            // Fallback to device time if parsing fails
            val now = Date()
            binding.weatherCard.tvWeatherDay.text = SimpleDateFormat("EEEE", Locale.getDefault()).format(now)
            binding.weatherCard.tvWeatherTime.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)
        }

        // Color coding for weather condition text based on severity (PAGASA-inspired)
        val conditionColor = when (code) {
            in 51..55, 61, 80 -> Color.parseColor("#FFEB3B") // Yellow: Awareness (Light rain/Drizzle)
            63, 81 -> Color.parseColor("#FF9800")           // Orange: Preparedness (Moderate rain)
            65, 82, 95, 96, 99 -> Color.parseColor("#FF5252") // Red: Emergency (Heavy rain/Storms)
            else -> Color.WHITE                              // Default for Clear/Cloudy
        }
        binding.weatherCard.tvWeatherCondition.setTextColor(conditionColor)
        binding.weatherCard.ivWeatherIcon.setColorFilter(conditionColor)

        // Auto-advisory based on weather code
        lastWeatherAdvisory = getAutoAdvisory(code)
        updateAdvisoryBanner()
        
        // Update icon based on code and severity category
        val iconRes = when (code) {
            0, 1 -> R.drawable.ic_sun                 // Clear (White)
            2, 3, in 45..48 -> R.drawable.ic_cloud   // Cloudy/Fog (White)
            in 51..55, 61, 80 -> R.drawable.ic_drizzle // Light Rain (Yellow)
            63, 81 -> R.drawable.ic_rain              // Moderate Rain (Orange)
            65, 82, 95, 96, 99 -> R.drawable.ic_storm // Heavy Rain/Storms (Red)
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

    private fun setupNavigation() {
        binding.buttonAnnouncements.setOnClickListener {
            findNavController().navigate(R.id.announcementsFragment)
        }
        binding.cardAskResilio.root.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_aiChatFragment)
        }
    }

    private fun setupCallButtons() {
        // Brgy San Jose
        setupRow(binding.contactBrgy1.root, getString(R.string.num_brgy_landline_1))
        setupRow(binding.contactBrgy2.root, getString(R.string.num_brgy_landline_2))
        setupRow(binding.contactBrgy3.root, getString(R.string.num_brgy_mobile_1))
        setupRow(binding.contactBrgy4.root, getString(R.string.num_brgy_mobile_2))

        // CDRRMO
        setupRow(binding.contactCdrrmo1.root, getString(R.string.num_cdrrmo_1_val))
        setupRow(binding.contactCdrrmo2.root, getString(R.string.num_cdrrmo_2_val))

        // Police
        setupRow(binding.contactPolice1.root, getString(R.string.num_police_1_val))
        setupRow(binding.contactPolice2.root, getString(R.string.num_police_2_val))

        // Fire
        setupRow(binding.contactFire1.root, getString(R.string.num_fire_1_val))
        setupRow(binding.contactFire2.root, getString(R.string.num_fire_2_val))

        // Medical
        setupRow(binding.contactMedical1.root, getString(R.string.num_medical_1_val))
        setupRow(binding.contactMedical2.root, getString(R.string.num_medical_2_val))
    }

    private fun setupRow(view: View, number: String) {
        view.findViewById<TextView>(R.id.text_number).text = number
        view.findViewById<MaterialButton>(R.id.btn_call).setOnClickListener {
            dialNumber(number)
        }
    }

    private fun dialNumber(number: String) {
        val cleanNumber = number.replace(Regex("[^0-9]"), "")
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$cleanNumber")
        }
        startActivity(intent)
    }

    private fun getAutoAdvisory(code: Int): String? {
        return when (code) {
            // Drizzle & Light Rain Group
            51, 53, 55, 61, 80 -> "Rain Advisory: Prepare for Wet Conditions"
            
            // Moderate Rain Group
            63, 81 -> "Moderate Rain Advisory: Watch for Rising Water"
            
            // Heavy & Violent Rain Group
            65, 82 -> "Violent Rain Advisory: Stay Indoors"
            
            // Thunderstorm Group
            95, 96, 99 -> "Severe Thunderstorm Warning: Seek Shelter"
            
            // Clear, Cloudy, Fog (No advisory needed)
            else -> null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
