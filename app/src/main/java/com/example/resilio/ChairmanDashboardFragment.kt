package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.resilio.databinding.FragmentChairmanDashboardBinding

class ChairmanDashboardFragment : Fragment(R.layout.fragment_chairman_dashboard) {

    private var _binding: FragmentChairmanDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentChairmanDashboardBinding.bind(view)

        binding.cardDisasterAlerts.setOnClickListener {
            findNavController().navigate(R.id.createEmergencyAlertFragment)
        }

        binding.cardAnnouncements.setOnClickListener {
            findNavController().navigate(R.id.createAnnouncementFragment)
        }

        binding.cardViewAnnouncements.setOnClickListener {
            findNavController().navigate(R.id.action_chairmanDashboardFragment_to_announcementsFragment)
        }
        
        binding.cardEvacuationMonitoring.setOnClickListener {
            findNavController().navigate(R.id.action_chairmanDashboardFragment_to_alertsUpdatesFragment)
        }

        binding.cardReportApproval.setOnClickListener {
            findNavController().navigate(R.id.manageReportsFragment)
        }

        binding.cardUserManagement.setOnClickListener {
            findNavController().navigate(R.id.userManagementFragment)
        }

        binding.btnGenerateAiSummary.setOnClickListener {
            Toast.makeText(requireContext(), "Generating AI Disaster Summary...", Toast.LENGTH_SHORT).show()
        }

        binding.cardAskResilio.root.setOnClickListener {
            findNavController().navigate(R.id.action_chairmanDashboardFragment_to_aiChatFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
