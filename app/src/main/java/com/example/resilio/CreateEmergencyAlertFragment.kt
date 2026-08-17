package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.resilio.databinding.FragmentCreateEmergencyAlertBinding
import com.example.resilio.model.EmergencyAlert
import com.example.resilio.model.HazardType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class CreateEmergencyAlertFragment : Fragment(R.layout.fragment_create_emergency_alert) {

    private var _binding: FragmentCreateEmergencyAlertBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCreateEmergencyAlertBinding.bind(view)

        setupSpinner()

        binding.btnSubmitEmergencyAlert.setOnClickListener {
            submitEmergencyAlert()
        }
    }

    private fun setupSpinner() {
        val types = HazardType.values().map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerHazardType.adapter = adapter
    }

    private fun submitEmergencyAlert() {
        val title = binding.etTitle.text.toString()
        val content = binding.etContent.text.toString()
        val type = HazardType.valueOf(binding.spinnerHazardType.selectedItem.toString())
        val affectedAreas = binding.etAffectedAreas.text.toString()
        val evacuationCenter = binding.etEvacuationCenter.text.toString()
        val uid = auth.currentUser?.uid ?: return

        if (title.isEmpty() || content.isEmpty()) {
            Toast.makeText(requireContext(), "Title and content are required", Toast.LENGTH_SHORT).show()
            return
        }

        val alertId = UUID.randomUUID().toString()
        val alert = EmergencyAlert(
            id = alertId,
            title = title,
            content = content,
            type = type,
            authorUid = uid,
            affectedAreas = affectedAreas,
            evacuationCenter = evacuationCenter
        )

        db.collection("emergency_alerts").document(alertId).set(alert)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Emergency alert submitted.", Toast.LENGTH_LONG).show()
                findNavController().popBackStack()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to submit: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
