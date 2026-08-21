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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.resilio.databinding.FragmentHomeBinding
import com.example.resilio.model.Announcement
import com.example.resilio.model.AnnouncementStatus
import com.example.resilio.model.EmergencyAlert
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
        setupLatestAlerts()
        setupLatestAnnouncements()
    }

    private fun setupLatestAnnouncements() {
        binding.layoutLatestAnnouncements.rvLatestAnnouncements.layoutManager = LinearLayoutManager(requireContext())
        
        FirebaseFirestore.getInstance().collection("announcements")
            .addSnapshotListener { value, error ->
                if (error != null) return@addSnapshotListener
                
                val allAnnouncements = value?.toObjects(Announcement::class.java) ?: emptyList()
                val announcements = allAnnouncements
                    .filter { it.status == AnnouncementStatus.APPROVED }
                    .sortedByDescending { it.timestamp }
                    .take(3)

                if (announcements.isEmpty()) {
                    binding.layoutLatestAnnouncements.root.visibility = View.GONE
                } else {
                    binding.layoutLatestAnnouncements.root.visibility = View.VISIBLE
                    binding.layoutLatestAnnouncements.rvLatestAnnouncements.adapter = LatestAnnouncementsHomeAdapter(announcements) { announcement ->
                        val bundle = Bundle().apply {
                            putString("title", announcement.title)
                            putString("content", announcement.content)
                            putString("authorUid", announcement.authorUid)
                            putString("affectedAreas", announcement.affectedAreas)
                            putString("evacuationCenter", announcement.evacuationCenter)
                        }
                        findNavController().navigate(R.id.announcementDetailFragment, bundle)
                    }
                }
            }

        binding.layoutLatestAnnouncements.tvViewAllAnnouncements.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_announcementsFragment)
        }
    }

    private fun setupLatestAlerts() {
        binding.layoutLatestAlerts.rvLatestAlerts.layoutManager = LinearLayoutManager(requireContext())
        
        FirebaseFirestore.getInstance().collection("emergency_alerts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(3)
            .addSnapshotListener { value, _ ->
                val alerts = value?.toObjects(EmergencyAlert::class.java) ?: emptyList()
                if (alerts.isEmpty()) {
                    binding.layoutLatestAlerts.root.visibility = View.GONE
                } else {
                    binding.layoutLatestAlerts.root.visibility = View.VISIBLE
                    binding.layoutLatestAlerts.rvLatestAlerts.adapter = LatestAlertsHomeAdapter(alerts) { alert ->
                        val bundle = Bundle().apply {
                            putString("title", alert.title)
                            putString("content", alert.content)
                            putString("authorUid", alert.authorUid)
                            putString("affectedAreas", alert.affectedAreas)
                            putString("evacuationCenter", alert.evacuationCenter)
                        }
                        findNavController().navigate(R.id.announcementDetailFragment, bundle)
                    }
                }
            }

        binding.layoutLatestAlerts.tvViewAllAlerts.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_disasterAlertsFragment)
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
        // Hardcoded Antipolo Coordinates
        val lat = 14.5845
        val lon = 121.1754
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_gusts_10m,precipitation&hourly=precipitation_probability&timezone=auto"
        
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
        
        // Switch visibility from Loading UI to Weather Content
        binding.weatherCard.layoutWeatherLoading.visibility = View.GONE
        binding.weatherCard.layoutWeatherContent.visibility = View.VISIBLE
        
        binding.weatherCard.tvWeatherTemp.text = getString(R.string.temp_format_user, tempC.toInt())
        
        val condition = getWeatherDescription(code)
        binding.weatherCard.tvWeatherCondition.text = condition
        
        binding.weatherCard.tvWeatherHumidity.text = getString(R.string.humidity_format, humidity)
        
        // Show wind speed and gusts for better accuracy
        binding.weatherCard.tvWeatherWind.text = if (windGusts > windSpeed * 1.5) {
            "Wind: ${windSpeed.toInt()}-${windGusts.toInt()} km/h"
        } else {
            getString(R.string.wind_format, windSpeed)
        }

        binding.weatherCard.tvWeatherPrecip.text = getString(R.string.precip_format, precipProb)
        
        // Set time and day from API
        try {
            val apiFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            val weatherDate = apiFormat.parse(apiTimeStr) ?: Date()
            binding.weatherCard.tvWeatherTime.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(weatherDate)
            binding.weatherCard.tvWeatherDay.text = SimpleDateFormat("EEEE", Locale.getDefault()).format(weatherDate)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Background and Header selection
        val (backgroundRes, headerRes) = when (code) {
            0, 1 -> R.drawable.bg_weather_sunny to R.drawable.bg_header_sunny
            2, 3, in 45..48 -> R.drawable.bg_weather_cloudy to R.drawable.bg_header_cloudy
            in 51..65, in 80..82 -> R.drawable.bg_weather_rainy to R.drawable.bg_header_rainy
            in 71..77, 85, 86 -> R.drawable.bg_weather_snowy to R.drawable.bg_header_sunny
            95, 96, 99 -> R.drawable.bg_weather_rainy to R.drawable.bg_header_rainy
            else -> R.drawable.bg_weather_sunny to R.drawable.bg_header_sunny
        }
        
        binding.weatherCard.layoutWeatherContainer.setBackgroundResource(backgroundRes)
        binding.layoutHeader.setBackgroundResource(headerRes)

        // Color coding for condition text and icon
        val conditionColor = when (code) {
            51, 53, 55, 61, 80 -> Color.parseColor("#FFEB3B")
            63, 81 -> Color.parseColor("#FF9800")
            65, 82, 95, 96, 99 -> Color.parseColor("#FF5252")
            else -> Color.WHITE
        }
        binding.weatherCard.tvWeatherCondition.setTextColor(conditionColor)
        binding.weatherCard.ivWeatherIcon.setColorFilter(conditionColor)

        // Reset header text colors to white
        binding.tvStatusTitle.setTextColor(Color.WHITE)
        binding.tvStatusDesc.setTextColor(Color.parseColor("#E0E0E0"))

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

    private fun setupNavigation() {
        binding.fabAskResilio.setOnClickListener {
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
