package com.example.resilio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.resilio.databinding.FragmentHomeBinding
import com.google.android.material.button.MaterialButton

class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        setupNavigation()
        setupCallButtons()
    }

    private fun setupNavigation() {
        binding.cardDisasterInfo.setOnClickListener {
            findNavController().navigate(R.id.disasterAwarenessFragment)
        }
        binding.cardEvacuation.setOnClickListener {
            findNavController().navigate(R.id.alertsUpdatesFragment)
        }
        binding.cardPreparedness.setOnClickListener {
            findNavController().navigate(R.id.disasterAwarenessFragment)
        }
        binding.cardEmergencyContacts.setOnClickListener {
            findNavController().navigate(R.id.emergencyContactsFragment)
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
