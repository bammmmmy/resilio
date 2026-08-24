package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.example.resilio.databinding.FragmentCreateEmergencyAlertBinding
import com.example.resilio.model.EmergencyAlert
import com.example.resilio.model.HazardType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class CreateEmergencyAlertFragment : Fragment(R.layout.fragment_create_emergency_alert) {

    private var _binding: FragmentCreateEmergencyAlertBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var editId: String? = null
    private var pendingHazardLat: Double = 0.0
    private var pendingHazardLng: Double = 0.0
    private var pendingHazardRadius: Double = 0.0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCreateEmergencyAlertBinding.bind(view)

        setFragmentResultListener("hazard_location_request") { _, bundle ->
            pendingHazardLat = bundle.getDouble("lat")
            pendingHazardLng = bundle.getDouble("lng")
            pendingHazardRadius = bundle.getDouble("radius")
            updateMapButtonsState()
            Toast.makeText(requireContext(), "Map area set.", Toast.LENGTH_SHORT).show()
        }

        setupSpinner()

        arguments?.let {
            editId = it.getString("edit_id")
            if (editId != null) {
                binding.etTitle.setText(it.getString("edit_title"))
                binding.etContent.setText(it.getString("edit_content"))
                binding.etAffectedAreas.setText(it.getString("edit_areas"))
                binding.etEvacuationCenter.setText(it.getString("edit_evac"))
                
                val typeName = it.getString("edit_type")
                val types = HazardType.values().map { t -> t.name }
                val index = types.indexOf(typeName)
                if (index != -1) {
                    binding.spinnerHazardType.setSelection(index)
                }
                
                binding.btnSubmitEmergencyAlert.setText(R.string.action_update_alert)
                
                loadExistingMapArea(editId!!)
            }
        }

        binding.btnSubmitEmergencyAlert.setOnClickListener {
            submitEmergencyAlert()
        }

        binding.btnRemoveMapArea.setOnClickListener {
            removeMapArea()
        }

        binding.btnSetVrMap.setOnClickListener {
            val title = binding.etTitle.text.toString().trim()
            val content = binding.etContent.text.toString().trim()
            val type = binding.spinnerHazardType.selectedItem.toString().lowercase()
            val address = binding.etAffectedAreas.text.toString().trim()

            if (content.isEmpty()) {
                Toast.makeText(requireContext(), "Details/Description required for map location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val args = Bundle().apply {
                putBoolean("hazardCreateMode", true)
                putString("hazardType", type)
                putString("hazardDescription", content)
                putString("hazardAddress", address)
            }
            findNavController().navigate(R.id.evacuationMapFragment, args)
        }
    }

    private fun setupSpinner() {
        val types = HazardType.values().map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerHazardType.adapter = adapter
    }

    private fun loadExistingMapArea(id: String) {
        db.collection("hazardLocations").document(id).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    pendingHazardLat = doc.getDouble("latitude") ?: 0.0
                    pendingHazardLng = doc.getDouble("longitude") ?: 0.0
                    pendingHazardRadius = doc.getDouble("radius") ?: 0.0
                    updateMapButtonsState()
                }
            }
    }

    private fun updateMapButtonsState() {
        if (pendingHazardLat != 0.0) {
            binding.btnSetVrMap.setText(R.string.action_change_map_area)
            binding.btnRemoveMapArea.visibility = View.VISIBLE
        } else {
            binding.btnSetVrMap.setText(R.string.set_in_vr_map)
            binding.btnRemoveMapArea.visibility = View.GONE
        }
    }

    private fun removeMapArea() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove Map Area?")
            .setMessage("This will remove the hazard circle from the map, but keep the alert text. Continue?")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("Remove") { _, _ ->
                pendingHazardLat = 0.0
                pendingHazardLng = 0.0
                pendingHazardRadius = 0.0
                
                editId?.let { id ->
                    db.collection("hazardLocations").document(id).delete()
                }
                
                updateMapButtonsState()
                Toast.makeText(requireContext(), R.string.map_area_removed, Toast.LENGTH_SHORT).show()
            }
            .show()
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

        val alertId = editId ?: UUID.randomUUID().toString()
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
                if (_binding == null) return@addOnSuccessListener
                
                // Save hazard location if set
                if (pendingHazardLat != 0.0) {
                    val hazard = com.example.resilio.model.HazardLocation(
                        id = alertId,
                        hazardType = type.name.lowercase(),
                        description = content,
                        address = affectedAreas,
                        latitude = pendingHazardLat,
                        longitude = pendingHazardLng,
                        radius = pendingHazardRadius,
                        createdBy = uid
                    )
                    db.collection("hazardLocations").document(alertId).set(hazard)
                }

                val messageId = if (editId != null) R.string.alert_updated else R.string.alert_submitted
                Toast.makeText(requireContext(), messageId, Toast.LENGTH_LONG).show()
                findNavController().popBackStack()
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                Toast.makeText(requireContext(), "Failed to submit: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
