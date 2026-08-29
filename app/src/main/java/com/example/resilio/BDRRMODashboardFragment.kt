package com.example.resilio

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.resilio.databinding.FragmentBdrrmoDashboardBinding
import com.example.resilio.model.Announcement
import com.example.resilio.model.AnnouncementStatus
import com.example.resilio.model.EmergencyAlert
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
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

    private var announcementsListener: ListenerRegistration? = null
    private var alertsListener: ListenerRegistration? = null
    
    private var weatherView: View? = null
    private var landslideView: View? = null
    private var earthquakeView: View? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentBdrrmoDashboardBinding.bind(view)

        binding.fabAskResilio.setOnClickListener {
            findNavController().navigate(R.id.action_bdrrmoDashboardFragment_to_aiChatFragment)
        }

        setupStatusPager()
        startDataRefreshLoop()

        setupLatestAlerts()
        setupLatestAnnouncements()
    }

    private fun startDataRefreshLoop() {
        val context = requireContext().applicationContext
        lifecycleScope.launch {
            while (true) {
                DashboardUIHelper.fetchWeather(context, lifecycleScope, forceRefresh = true) {
                    WeatherCache.snapshot?.let { 
                        weatherView?.let { v -> DashboardUIHelper.updateWeatherUI(v, it, binding.layoutHeader, binding.tvStatusTitle, binding.tvStatusDesc) }
                        landslideView?.let { v -> DashboardUIHelper.updateLandslideUI(v, it) }
                    }
                }
                
                DashboardUIHelper.fetchEarthquakeData(context, lifecycleScope) {
                    EarthquakeCache.lastQuake?.let {
                        earthquakeView?.let { v -> DashboardUIHelper.updateEarthquakeUI(v, it) }
                    }
                }
                
                kotlinx.coroutines.delay(5 * 60 * 1000L) // Refresh every 5 minutes
            }
        }
    }

    private fun setupStatusPager() {
        binding.statusPager.adapter = DashboardStatusAdapter(
            onWeatherBind = { view -> 
                weatherView = view
                WeatherCache.snapshot?.let { DashboardUIHelper.updateWeatherUI(view, it, binding.layoutHeader, binding.tvStatusTitle, binding.tvStatusDesc) }
            },
            onLandslideBind = { view -> 
                landslideView = view
                WeatherCache.snapshot?.let { DashboardUIHelper.updateLandslideUI(view, it) }
            },
            onEarthquakeBind = { view ->
                earthquakeView = view
                EarthquakeCache.lastQuake?.let { DashboardUIHelper.updateEarthquakeUI(view, it) }
            }
        )
        
        TabLayoutMediator(binding.statusIndicator, binding.statusPager) { _, _ -> }.attach()
    }

    private fun setupLatestAnnouncements() {
        binding.layoutLatestAnnouncements.rvLatestAnnouncements.layoutManager = LinearLayoutManager(requireContext())
        
        announcementsListener = FirebaseFirestore.getInstance().collection("announcements")
            .addSnapshotListener { value, error ->
                if (_binding == null || error != null) return@addSnapshotListener
                
                val allAnnouncements = value?.toObjects(Announcement::class.java) ?: emptyList()
                val announcements = allAnnouncements
                    .filter { it.status == AnnouncementStatus.APPROVED }
                    .sortedByDescending { it.safeTimestamp }
                    .take(3)

                if (announcements.isEmpty()) {
                    binding.layoutLatestAnnouncements.root.visibility = View.GONE
                } else {
                    binding.layoutLatestAnnouncements.root.visibility = View.VISIBLE
                    binding.layoutLatestAnnouncements.rvLatestAnnouncements.adapter = LatestAnnouncementsHomeAdapter(announcements) { announcement ->
                        val bundle = Bundle().apply {
                            putString("id", announcement.id)
                            putString("title", announcement.title)
                            putString("content", announcement.safeContent)
                            putString("authorUid", announcement.authorUid)
                            putString("affectedAreas", announcement.affectedAreas)
                            putString("evacuationCenter", announcement.evacuationCenter)
                            putBoolean("isAlert", false)
                        }
                        findNavController().navigate(R.id.announcementDetailFragment, bundle)
                    }
                }
            }

        binding.layoutLatestAnnouncements.tvViewAllAnnouncements.setOnClickListener {
            findNavController().navigate(R.id.action_bdrrmoDashboardFragment_to_announcementsFragment)
        }
    }

    private fun setupLatestAlerts() {
        binding.layoutLatestAlerts.rvLatestAlerts.layoutManager = LinearLayoutManager(requireContext())
        
        alertsListener = FirebaseFirestore.getInstance().collection("emergency_alerts")
            .addSnapshotListener { value, _ ->
                if (_binding == null) return@addSnapshotListener

                val allAlerts = value?.toObjects(EmergencyAlert::class.java) ?: emptyList()
                val alerts = allAlerts
                    .sortedByDescending { it.safeTimestamp }
                    .take(3)

                if (alerts.isEmpty()) {
                    binding.layoutLatestAlerts.root.visibility = View.GONE
                } else {
                    binding.layoutLatestAlerts.root.visibility = View.VISIBLE
                    binding.layoutLatestAlerts.rvLatestAlerts.adapter = LatestAlertsHomeAdapter(alerts) { alert ->
                        val bundle = Bundle().apply {
                            putString("id", alert.id)
                            putString("title", alert.title)
                            putString("content", alert.safeContent)
                            putString("authorUid", alert.authorUid)
                            putString("affectedAreas", alert.affectedAreas)
                            putString("evacuationCenter", alert.evacuationCenter)
                            putBoolean("isAlert", true)
                            putString("hazardType", alert.type.name)
                        }
                        findNavController().navigate(R.id.announcementDetailFragment, bundle)
                    }
                }
            }

        binding.layoutLatestAlerts.tvViewAllAlerts.setOnClickListener {
            findNavController().navigate(R.id.action_bdrrmoDashboardFragment_to_disasterAlertsFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        announcementsListener?.remove()
        alertsListener?.remove()
        weatherView = null
        landslideView = null
        earthquakeView = null
        _binding = null
    }
}
