package com.example.resilio

import android.location.Location
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.resilio.databinding.FragmentHazardPredictionBinding
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class HazardPredictionFragment : Fragment(R.layout.fragment_hazard_prediction) {

    private var _binding: FragmentHazardPredictionBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHazardPredictionBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        loadPrediction()
    }

    private fun loadPrediction() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvPredictionResult.text = "Generating AI-driven hazard assessment..."

        lifecycleScope.launch {
            val deterministicSummary = withContext(Dispatchers.IO) {
                buildPredictionSummary()
            }

            val resultText = runCatching {
                GeminiClient.chatWithHistory(emptyList(), deterministicSummary)
            }.getOrElse {
                ChatResult.Error("AI analysis unavailable. Showing the latest on-device assessment instead.")
            }

            val finalText = when (resultText) {
                is ChatResult.Success -> resultText.text
                ChatResult.OffTopic -> "This is outside the disaster assessment scope. Showing the live system assessment instead.\n\n$deterministicSummary"
                is ChatResult.Error -> deterministicSummary
            }

            binding.progressBar.visibility = View.GONE
            binding.tvPredictionResult.text = finalText
        }
    }

    private fun resolveResidentReferenceLocation(): Pair<Double, Double> {
        val appContext = requireContext().applicationContext
        val hasFineLocation = androidx.core.content.ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = androidx.core.content.ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation && !hasCoarseLocation) return 14.5845 to 121.1754

        val locationManager = appContext.getSystemService(android.location.LocationManager::class.java) as? android.location.LocationManager ?: return 14.5845 to 121.1754
        val providers = listOfNotNull(android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER)
        val location = providers.mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }
                .getOrNull()
        }.maxByOrNull { it.time } ?: return 14.5845 to 121.1754

        return location.latitude to location.longitude
    }

    private fun buildPredictionSummary(): String {
        val weather = WeatherCache.snapshot
        val rain24h = weather?.rain24h ?: 0.0
        val precipProbability = weather?.precipProb ?: 0
        val windSpeed = weather?.windSpeed ?: 0.0
        val windGusts = weather?.windGusts ?: 0.0
        val weatherCode = weather?.code ?: 0
        val quake = EarthquakeCache.lastQuake
        val quakeMagnitude = quake?.magnitude ?: 0.0
        val quakeDistanceKm = quake?.distanceKm ?: Double.POSITIVE_INFINITY
        val residentReferenceLocation = resolveResidentReferenceLocation()

        val reasons = mutableListOf<String>()
        var hazardType = "GENERAL_ALERT"
        var riskScore = 18
        var confidence = 0.58

        if (rain24h >= 60 || precipProbability >= 70 || listOf(61, 63, 65, 80, 81, 82).contains(weatherCode)) {
            hazardType = "LANDSLIDE"
            riskScore = 84
            confidence = 0.88
            reasons += "24h rainfall reached ${String.format(Locale.US, "%.1f", rain24h)} mm."
            reasons += "Rain probability is $precipProbability% with active wet conditions."
            if (rain24h >= 80) reasons += "Ground saturation is high enough to increase slope failure risk."
        } else if (precipProbability >= 55 || rain24h >= 35) {
            hazardType = "FLOOD"
            riskScore = 72
            confidence = 0.8
            reasons += "Rain probability is $precipProbability% and rainfall totals are elevated."
            reasons += "Accumulated rainfall indicates drainage stress in the area."
        } else if (windGusts >= 45 || windSpeed >= 30) {
            hazardType = "TYPHOON"
            riskScore = 68
            confidence = 0.74
            reasons += "Wind gusts reached ${String.format(Locale.US, "%.0f", windGusts)} km/h."
            reasons += "Strong winds indicate possible storm impact conditions."
        } else if (quakeMagnitude >= 4.5 && quakeDistanceKm.isFinite() && quakeDistanceKm <= 80.0) {
            hazardType = "EARTHQUAKE"
            riskScore = 74
            confidence = 0.79
            reasons += "Recent quake magnitude reached ${String.format(Locale.US, "%.1f", quakeMagnitude)} near the area."
            reasons += "Estimated distance is ${String.format(Locale.US, "%.1f", quakeDistanceKm)} km from the monitoring point."
        }

        if (hazardType == "GENERAL_ALERT") {
            reasons += "Current conditions are stable but should be monitored for sudden rain or gust changes."
            riskScore = 22
            confidence = 0.62
        }

        val alerts = runCatching {
            Tasks.await(FirebaseFirestore.getInstance().collection("emergency_alerts").get())
        }.getOrElse { null }?.documents.orEmpty()

        val activeAlertSummary = alerts.filter { it.getString("status") != "ARCHIVED" }.take(3)
        if (activeAlertSummary.isNotEmpty()) {
            val alertTypes = activeAlertSummary.mapNotNull { it.getString("type") }.distinct()
            if (alertTypes.isNotEmpty()) reasons += "Recent active alerts include: ${alertTypes.joinToString(", ")}."
        }

        val evacuationAreas = runCatching {
            Tasks.await(FirebaseFirestore.getInstance().collection("evacuationAreas").get())
        }.getOrElse { null }?.documents.orEmpty()

        val nearestEvacuationArea = evacuationAreas
            .mapNotNull { area ->
                val latitude = area.getDouble("latitude") ?: return@mapNotNull null
                val longitude = area.getDouble("longitude") ?: return@mapNotNull null
                val distance = calculateDistanceKm(residentReferenceLocation.first, residentReferenceLocation.second, latitude, longitude)
                Pair(area.getString("name") ?: "Unnamed area", distance)
            }
            .minByOrNull { it.second }

        val severity = when {
            riskScore >= 75 -> "HIGH"
            riskScore >= 45 -> "MODERATE"
            else -> "LOW"
        }

        val recommendedAction = when (hazardType) {
            "LANDSLIDE" -> "Avoid steep slopes and move to a safer elevated area if rainfall continues."
            "FLOOD" -> "Stay away from low-lying roads and ensure drainage routes remain clear."
            "TYPHOON" -> "Secure loose items, monitor official advisories, and prepare for possible power interruption."
            "EARTHQUAKE" -> "Check for structural damage and be ready to move to a safe open area if aftershocks continue."
            else -> "Continue monitoring the live weather and alert feeds for changes."
        }

        val nearestAreaName = nearestEvacuationArea?.first ?: "No mapped center available"
        val nearestAreaDistance = nearestEvacuationArea?.second

        return buildString {
            appendLine("Likely hazard: ${formatHazardType(hazardType)}")
            appendLine("Risk score: ${riskScore}/100")
            appendLine("Severity: $severity")
            appendLine("Confidence: ${String.format(Locale.US, "%.2f", confidence)}")
            appendLine("Nearest evacuation area: $nearestAreaName${nearestAreaDistance?.let { " (${String.format(Locale.US, "%.1f", it)} km away)" } ?: ""}")
            appendLine("Recommended response: $recommendedAction")
            appendLine()
            appendLine("AI reasoning:")
            reasons.forEach { appendLine("- $it") }
        }
    }

    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return (results[0] / 1000.0).toDouble()
    }

    private fun formatHazardType(type: String): String = when (type) {
        "LANDSLIDE" -> "Landslide"
        "FLOOD" -> "Flood"
        "TYPHOON" -> "Typhoon"
        "EARTHQUAKE" -> "Earthquake"
        else -> "General Alert"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
