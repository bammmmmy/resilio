package com.example.resilio

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.resilio.databinding.FragmentProfileBinding
import com.example.resilio.model.UserRole
import com.example.resilio.util.ProfileManager
import com.example.resilio.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val authViewModel: AuthViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentProfileBinding.bind(view)

        loadProfileData()

        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }

        binding.fabEditProfileImage.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }

        authViewModel.userState.observe(viewLifecycleOwner) { result ->
            result?.onSuccess { user ->
                // Basic fallback for name if local profile is empty
                if (binding.tvProfileName.text == "Juan Dela Cruz") {
                    binding.tvProfileName.text = user.fullName
                }

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
        binding.btnChairmanSendEmergencyAlert.setOnClickListener {
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

    private fun loadProfileData() {
        authViewModel.userState.observe(viewLifecycleOwner) { result ->
            result?.onSuccess { user ->
                binding.tvProfileName.text = user.fullName.ifBlank { "Barangay Chairman" }
                binding.tvProfilePosition.text = user.position.ifBlank { "Barangay Chairman" }
                binding.tvProfileBarangay.text = user.barangayName.ifBlank { "Barangay San Jose" }
                binding.tvProfileAbout.text = user.about.ifBlank { "No description provided." }
                
                user.profileImageUrl?.let {
                    Glide.with(this@ProfileFragment)
                        .load(it)
                        .placeholder(R.drawable.logog)
                        .into(binding.ivProfileImage)
                }

                // Update local cache to match cloud data
                lifecycleScope.launch {
                    ProfileManager.saveProfile(requireContext(), user)
                }
            }
        }
        authViewModel.checkAuthState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
