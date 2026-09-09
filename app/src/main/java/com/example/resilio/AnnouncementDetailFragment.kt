package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.resilio.databinding.FragmentAnnouncementDetailBinding
import com.example.resilio.model.AnnouncementStatus
import com.example.resilio.model.UserRole
import com.example.resilio.util.ProfileManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AnnouncementDetailFragment : Fragment(R.layout.fragment_announcement_detail) {

    private var _binding: FragmentAnnouncementDetailBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAnnouncementDetailBinding.bind(view)

        val id = arguments?.getString("id").orEmpty()
        val title = arguments?.getString("title").orEmpty()
        val content = arguments?.getString("content").orEmpty()
        val authorUid = arguments?.getString("authorUid").orEmpty()
        val affectedAreas = arguments?.getString("affectedAreas").orEmpty()
        val evacuationCenter = arguments?.getString("evacuationCenter").orEmpty()
        val isAlert = arguments?.getBoolean("isAlert", false) ?: false
        val hazardTypeName = arguments?.getString("hazardType").orEmpty()
        val statusName = arguments?.getString("status").orEmpty()

        binding.tvAnnouncementDetailTitle.text = title
        binding.tvAnnouncementDetailContent.text = content
        binding.tvAnnouncementDetailAuthor.text = "Posted by: Loading..."

        lifecycleScope.launch {
            val user = ProfileManager.getProfile(requireContext()).first()
            val isManagement = user.role == UserRole.CHAIRMAN || user.role == UserRole.BDRRMO
            val isAuthor = authorUid == auth.currentUser?.uid
            val isChairman = user.role == UserRole.CHAIRMAN
            val isArchived = statusName == AnnouncementStatus.ARCHIVED.name

            if (isAuthor || isChairman) {
                binding.layoutFabActions.visibility = View.VISIBLE
                binding.fabEdit.visibility = if (isArchived) View.GONE else View.VISIBLE
                binding.fabDelete.visibility = View.VISIBLE
                
                checkLinkedHazard(id)

                binding.fabEdit.setOnClickListener {
                    val bundle = Bundle().apply {
                        putString("edit_id", id)
                        putString("edit_title", title)
                        putString("edit_content", content)
                        putString("edit_areas", affectedAreas)
                        putString("edit_evac", evacuationCenter)
                        putString("edit_type", hazardTypeName)
                        putString("edit_author_uid", authorUid)
                    }
                    val destination = if (isAlert) R.id.createEmergencyAlertFragment else R.id.createAnnouncementFragment
                    findNavController().navigate(destination, bundle)
                }

                binding.fabDelete.setOnClickListener {
                    confirmDelete(id, isAlert)
                }
            } else {
                binding.fabEdit.visibility = View.GONE
                binding.fabDelete.visibility = View.GONE
            }

            if (isManagement) {
                binding.layoutFabActions.visibility = View.VISIBLE
                if (isArchived) {
                    binding.fabArchive.visibility = View.GONE
                    binding.fabRestore.visibility = View.VISIBLE
                    binding.fabRestore.setOnClickListener {
                        confirmRestore(id, isAlert)
                    }
                } else {
                    binding.fabArchive.visibility = View.VISIBLE
                    binding.fabRestore.visibility = View.GONE
                    binding.fabArchive.setOnClickListener {
                        confirmArchive(id, isAlert)
                    }
                }
            } else {
                binding.fabArchive.visibility = View.GONE
                binding.fabRestore.visibility = View.GONE
            }
        }

        if (authorUid.isNotBlank()) {
            FirebaseFirestore.getInstance().collection("users").document(authorUid).get()
                .addOnSuccessListener { doc ->
                    if (_binding == null) return@addOnSuccessListener
                    val name = doc?.getString("fullName").orEmpty().ifEmpty { "Unknown" }
                    binding.tvAnnouncementDetailAuthor.text = "Posted by: $name"
                }
                .addOnFailureListener {
                    if (_binding == null) return@addOnFailureListener
                    binding.tvAnnouncementDetailAuthor.text = "Posted by: Unknown"
                }
        } else {
            binding.tvAnnouncementDetailAuthor.text = "Posted by: Unknown"
        }

        if (affectedAreas.isNotBlank()) {
            binding.tvAnnouncementDetailAffectedAreas.text = "Affected Areas: $affectedAreas"
            binding.tvAnnouncementDetailAffectedAreas.visibility = View.VISIBLE
        }

        if (evacuationCenter.isNotBlank()) {
            binding.tvAnnouncementDetailEvacuationCenter.text = "Evacuation Center: $evacuationCenter"
            binding.tvAnnouncementDetailEvacuationCenter.visibility = View.VISIBLE
        }
    }

    private fun checkLinkedHazard(id: String) {
        db.collection("hazardLocations").document(id).get()
            .addOnSuccessListener { doc ->
                if (_binding == null) return@addOnSuccessListener
                if (doc.exists()) {
                    binding.btnClearMapArea.visibility = View.VISIBLE
                    binding.btnClearMapArea.setOnClickListener {
                        confirmClearMapArea(id)
                    }
                }
            }
    }

    private fun confirmClearMapArea(id: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear Map Area?")
            .setMessage("Remove the hazard circle from the VR map? The announcement text will remain.")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("Clear") { _, _ ->
                db.collection("hazardLocations").document(id).delete()
                    .addOnSuccessListener {
                        if (_binding == null) return@addOnSuccessListener
                        Toast.makeText(requireContext(), "Map area cleared.", Toast.LENGTH_SHORT).show()
                        binding.btnClearMapArea.visibility = View.GONE
                    }
            }
            .show()
    }

    private fun confirmArchive(id: String, isAlert: Boolean) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_archive)
            .setMessage("Archive this item? It will be moved to the archive list and hidden from the map.")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.action_archive) { _, _ ->
                val collection = if (isAlert) "emergency_alerts" else "announcements"
                db.collection(collection).document(id).update("status", AnnouncementStatus.ARCHIVED)
                db.collection("hazardLocations").document(id).update("active", false)
                Toast.makeText(requireContext(), "Archived", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
            .show()
    }

    private fun confirmRestore(id: String, isAlert: Boolean) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_restore)
            .setMessage("Restore this item to the active list?")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.action_restore) { _, _ ->
                val collection = if (isAlert) "emergency_alerts" else "announcements"
                db.collection(collection).document(id).update("status", AnnouncementStatus.APPROVED)
                db.collection("hazardLocations").document(id).update("active", true)
                Toast.makeText(requireContext(), "Restored", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
            .show()
    }

    private fun confirmDelete(id: String, isAlert: Boolean) {
        val ctx = context ?: return
        MaterialAlertDialogBuilder(ctx)
            .setTitle(if (isAlert) R.string.delete_alert_title else R.string.delete_announcement_title)
            .setMessage(R.string.delete_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_evacuation_area) { _, _ ->
                val collection = if (isAlert) "emergency_alerts" else "announcements"
                db.collection(collection).document(id).delete()
                    .addOnSuccessListener {
                        // Also delete the linked hazard location
                        db.collection("hazardLocations").document(id).delete()

                        if (_binding == null) return@addOnSuccessListener
                        Toast.makeText(context, R.string.deleted_success, Toast.LENGTH_SHORT).show()
                        if (isAdded) {
                            findNavController().popBackStack()
                        }
                    }
                    .addOnFailureListener {
                        if (_binding == null) return@addOnFailureListener
                        Toast.makeText(context, R.string.delete_failed, Toast.LENGTH_SHORT).show()
                    }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
