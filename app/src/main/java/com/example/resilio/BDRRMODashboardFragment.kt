package com.example.resilio

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.resilio.databinding.FragmentBdrrmoDashboardBinding
import com.example.resilio.viewmodel.BDRRMOViewModel

class BDRRMODashboardFragment : Fragment(R.layout.fragment_bdrrmo_dashboard) {

    private var _binding: FragmentBdrrmoDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BDRRMOViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentBdrrmoDashboardBinding.bind(view)

        // Observe My Announcements
        binding.rvBdrrmoAnnouncements.layoutManager = LinearLayoutManager(requireContext())
        viewModel.myAnnouncements.observe(viewLifecycleOwner) { list ->
            binding.rvBdrrmoAnnouncements.adapter = AnnouncementAdapter(list, onItemClick = { announcement ->
                val bundle = Bundle().apply {
                    putString("title", announcement.title)
                    putString("content", announcement.content)
                    putString("authorUid", announcement.authorUid)
                    putString("affectedAreas", announcement.affectedAreas)
                    putString("evacuationCenter", announcement.evacuationCenter)
                }
                findNavController().navigate(R.id.announcementDetailFragment, bundle)
            })
        }
        viewModel.listenToMyAnnouncements()

        // Navigation
        binding.cardSendAlert.setOnClickListener {
            findNavController().navigate(R.id.action_bdrrmoDashboardFragment_to_createEmergencyAlertFragment)
        }

        binding.cardCreateAnnouncement.setOnClickListener {
            findNavController().navigate(R.id.action_bdrrmoDashboardFragment_to_createAnnouncementFragment)
        }

        binding.cardViewAnnouncements.setOnClickListener {
            findNavController().navigate(R.id.action_bdrrmoDashboardFragment_to_announcementsFragment)
        }

        binding.cardEvacuation.setOnClickListener {
            findNavController().navigate(R.id.action_bdrrmoDashboardFragment_to_alertsUpdatesFragment)
        }
        
        binding.cardHotline.setOnClickListener {
            findNavController().navigate(R.id.emergencyContactsFragment)
        }

        binding.cardAskResilio.root.setOnClickListener {
            findNavController().navigate(R.id.action_bdrrmoDashboardFragment_to_aiChatFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
