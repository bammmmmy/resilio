package com.example.resilio

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.resilio.databinding.FragmentProfileBinding
import com.example.resilio.model.User
import com.example.resilio.model.UserRole
import com.example.resilio.viewmodel.AuthViewModel

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val authViewModel: AuthViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentProfileBinding.bind(view)

        // Observe user data to display name and email
        authViewModel.userState.observe(viewLifecycleOwner) { result ->
            result?.onSuccess { user ->
                binding.tvProfileName.text = user.fullName
                binding.tvProfileEmail.text = user.email
                
                // Show specific actions based on role
                when (user.role) {
                    UserRole.BDRRMO -> {
                        binding.layoutBdrrmoActions.visibility = View.VISIBLE
                        binding.layoutChairmanActions.visibility = View.GONE
                    }
                    UserRole.CHAIRMAN -> {
                        binding.layoutChairmanActions.visibility = View.VISIBLE
                        binding.layoutBdrrmoActions.visibility = View.GONE
                    }
                    else -> {
                        binding.layoutBdrrmoActions.visibility = View.GONE
                        binding.layoutChairmanActions.visibility = View.GONE
                    }
                }
            }
        }
        authViewModel.checkAuthState()

        // BDRRMO Listeners
        binding.btnSendEmergencyAlert.setOnClickListener {
            findNavController().navigate(R.id.createEmergencyAlertFragment)
        }
        binding.btnCreateAnnouncement.setOnClickListener {
            findNavController().navigate(R.id.createAnnouncementFragment)
        }
        binding.btnViewAnnouncements.setOnClickListener {
            findNavController().navigate(R.id.announcementsFragment)
        }

        // Chairman Listeners
        binding.btnChairmanSendAlert.setOnClickListener {
            findNavController().navigate(R.id.createEmergencyAlertFragment)
        }
        binding.btnChairmanCreateAnnouncement.setOnClickListener {
            findNavController().navigate(R.id.createAnnouncementFragment)
        }
        binding.btnChairmanViewAnnouncements.setOnClickListener {
            findNavController().navigate(R.id.announcementsFragment)
        }
        binding.btnReportsApproval.setOnClickListener {
            findNavController().navigate(R.id.manageReportsFragment)
        }
        binding.btnVerifyResidents.setOnClickListener {
            findNavController().navigate(R.id.userManagementFragment)
        }

        binding.btnLogout.setOnClickListener {
            authViewModel.logout()
            findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
