package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.example.resilio.databinding.FragmentCreateAnnouncementBinding
import com.example.resilio.model.Announcement
import com.example.resilio.model.AnnouncementStatus
import com.example.resilio.model.HazardType
import com.example.resilio.model.User
import com.example.resilio.model.UserRole
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class CreateAnnouncementFragment : Fragment(R.layout.fragment_create_announcement) {

    private var _binding: FragmentCreateAnnouncementBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var editId: String? = null
    private var pendingHazardLat: Double = 0.0
    private var pendingHazardLng: Double = 0.0
    private var pendingHazardRadius: Double = 0.0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCreateAnnouncementBinding.bind(view)

        setFragmentResultListener("hazard_location_request") { _, bundle ->
            pendingHazardLat = bundle.getDouble("lat")
            pendingHazardLng = bundle.getDouble("lng")
            pendingHazardRadius = bundle.getDouble("radius")
            updateMapButtonsState()
            Toast.makeText(requireContext(), "Map area set.", Toast.LENGTH_SHORT).show()
        }

        arguments?.let {
            editId = it.getString("edit_id")
            if (editId != null) {
                binding.etTitle.setText(it.getString("edit_title"))
                binding.etContent.setText(it.getString("edit_content"))
                binding.etAffectedAreas.setText(it.getString("edit_areas"))
                binding.etEvacuationCenter.setText(it.getString("edit_evac"))
                binding.btnSubmitAlert.setText(R.string.action_update_announcement)
                
                loadExistingMapArea(editId!!)
            }
        }

        binding.btnSubmitAlert.setOnClickListener {
            submitAnnouncement()
        }

        binding.btnRemoveMapArea.setOnClickListener {
            removeMapArea()
        }

        binding.btnSetVrMap.setOnClickListener {
            val content = binding.etContent.text.toString().trim()
            val address = binding.etAffectedAreas.text.toString().trim()

            if (content.isEmpty()) {
                Toast.makeText(requireContext(), "Details/Description required for map location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val args = Bundle().apply {
                putBoolean("hazardCreateMode", true)
                putString("hazardType", "general_alert")
                putString("hazardDescription", content)
                putString("hazardAddress", address)
            }
            findNavController().navigate(R.id.evacuationMapFragment, args)
        }
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
            .setMessage("This will remove the hazard circle from the map, but keep the announcement text. Continue?")
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

    private fun submitAnnouncement() {
        val title = binding.etTitle.text.toString()
        val content = binding.etContent.text.toString()
        val type = HazardType.GENERAL_ALERT
        val affectedAreas = binding.etAffectedAreas.text.toString()
        val evacuationCenter = binding.etEvacuationCenter.text.toString()
        val uid = auth.currentUser?.uid ?: return

        if (title.isEmpty() || content.isEmpty()) {
            Toast.makeText(requireContext(), "Title and content are required", Toast.LENGTH_SHORT).show()
            return
        }

        val announcementId = editId ?: UUID.randomUUID().toString()

        db.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                val user = snapshot.toObject(User::class.java)
                val isChairman = user?.role == UserRole.CHAIRMAN
                
                // If editing, keep the existing status or re-approve if chairman
                val status = if (isChairman) AnnouncementStatus.APPROVED else AnnouncementStatus.PENDING
                
                val announcement = Announcement(
                    id = announcementId,
                    title = title,
                    content = content,
                    type = type,
                    status = status,
                    authorUid = uid,
                    affectedAreas = affectedAreas,
                    evacuationCenter = evacuationCenter
                )

                db.collection("announcements").document(announcementId).set(announcement)
                    .addOnSuccessListener {
                        if (_binding == null) return@addOnSuccessListener
                        
                        // Save hazard location if set
                        if (pendingHazardLat != 0.0) {
                            val hazard = com.example.resilio.model.HazardLocation(
                                id = announcementId,
                                hazardType = "general_alert",
                                description = content,
                                address = affectedAreas,
                                latitude = pendingHazardLat,
                                longitude = pendingHazardLng,
                                radius = pendingHazardRadius,
                                createdBy = uid
                            )
                            db.collection("hazardLocations").document(announcementId).set(hazard)
                        }

                        val message = if (editId != null) {
                            getString(R.string.announcement_updated)
                        } else if (isChairman) {
                            getString(R.string.announcement_published)
                        } else {
                            getString(R.string.announcement_submitted_pending)
                        }
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                        findNavController().popBackStack()
                    }
                    .addOnFailureListener {
                        if (_binding == null) return@addOnFailureListener
                        Toast.makeText(requireContext(), "Failed to submit: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
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
