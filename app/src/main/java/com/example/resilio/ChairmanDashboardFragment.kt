package com.example.resilio

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.resilio.databinding.FragmentChairmanDashboardBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChairmanDashboardFragment : Fragment(R.layout.fragment_chairman_dashboard) {

    private var _binding: FragmentChairmanDashboardBinding? = null
    private val binding get() = _binding!!
    private val client = OkHttpClient()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var lastWeatherAdvisory: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            getCurrentLocationAndFetchWeather()
        } else {
            // Fallback to default location
            fetchWeather(14.5845, 121.1754)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentChairmanDashboardBinding.bind(view)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        binding.cardAskResilio.root.setOnClickListener {
            findNavController().navigate(R.id.action_chairmanDashboardFragment_to_aiChatFragment)
        }

        checkLocationPermission()
        startRealTimeClock()
    }

    private fun startRealTimeClock() {
        lifecycleScope.launch {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEE MM-dd", Locale.getDefault())
            while (isActive) {
                val now = Date()
                if (_binding != null) {
                    binding.weatherCard.tvWeatherTime.text = timeFormat.format(now)
                    binding.weatherCard.tvWeatherDay.text = dateFormat.format(now).uppercase(Locale.getDefault())
                }
                delay(1000)
            }
        }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocationAndFetchWeather()
            }
            else -> {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun getCurrentLocationAndFetchWeather() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            fetchWeather(14.5845, 121.1754)
            return
        }

        lifecycleScope.launch {
            try {
                val location = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    null
                ).await()

                if (location != null) {
                    fetchWeather(location.latitude, location.longitude)
                } else {
                    // Fallback if location is null
                    fetchWeather(14.5845, 121.1754)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                fetchWeather(14.5845, 121.1754)
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

    private fun fetchWeather(lat: Double, lon: Double) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_gusts_10m,precipitation&hourly=precipitation_probability&timezone=auto"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Get city name from coordinates
                val cityName = try {
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    addresses?.firstOrNull()?.locality ?: addresses?.firstOrNull()?.subAdminArea ?: "Unknown Location"
                } catch (e: Exception) {
                    "Your Location"
                }

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

                    val apiTimeStr = current.getString("time")

                    withContext(Dispatchers.Main) {
                        updateWeatherUI(tempC, code, humidity, windSpeed, windGusts, currentPrecipProb, apiTimeStr, cityName)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateWeatherUI(tempC: Double, code: Int, humidity: Int, windSpeed: Double, windGusts: Double, precipProb: Int, apiTimeStr: String, cityName: String) {
        if (_binding == null) return

        binding.weatherCard.weatherLoadingProgress.visibility = View.GONE
        binding.weatherCard.tvWeatherLocation.text = cityName

        val tempF = (tempC * 9/5) + 32
        // Show only Celsius degree in large format like the image
        binding.weatherCard.tvWeatherTemp.text = getString(R.string.temp_format_single, tempC.toInt())
        binding.weatherCard.tvWeatherCondition.text = getWeatherDescription(code)

        // Hidden fields for logic
        binding.weatherCard.tvWeatherHumidity.text = getString(R.string.humidity_format, humidity)
        binding.weatherCard.tvWeatherWind.text = getString(R.string.wind_format, windSpeed)
        binding.weatherCard.tvWeatherPrecip.text = getString(R.string.precip_format, precipProb)

        // Time and Date are updated in real-time by startRealTimeClock()

        // Apply background and icon based on weather code
        val (bgRes, iconRes) = when (code) {
            0, 1 -> R.drawable.bg_weather_sunny to R.drawable.ic_sun
            2, 3, 45, 48 -> R.drawable.bg_weather_cloudy to R.drawable.ic_cloud
            in 51..55, 61, 80 -> R.drawable.bg_weather_rainy to R.drawable.ic_drizzle
            63, 65, 81, 82, 95, 96, 99 -> R.drawable.bg_weather_rainy to R.drawable.ic_storm
            in 71..77, 85, 86 -> R.drawable.bg_weather_snowy to R.drawable.ic_cloud
            else -> R.drawable.bg_weather_sunny to R.drawable.ic_sun
        }

        binding.weatherCard.layoutWeatherContainer.setBackgroundResource(bgRes)
        binding.weatherCard.ivWeatherIcon.setImageResource(iconRes)

        // Ensure text is white as per redesign
        binding.weatherCard.tvWeatherCondition.setTextColor(Color.WHITE)
        binding.weatherCard.ivWeatherIcon.setColorFilter(Color.WHITE)

        lastWeatherAdvisory = getAutoAdvisory(code)
        updateAdvisoryBanner()
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
