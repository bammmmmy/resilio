package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.resilio.databinding.FragmentAnnouncementDetailBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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

        binding.tvAnnouncementDetailTitle.text = title
        binding.tvAnnouncementDetailContent.text = content
        binding.tvAnnouncementDetailAuthor.text = "Posted by: Loading..."

        if (authorUid == auth.currentUser?.uid) {
            binding.layoutFabActions.visibility = View.VISIBLE
            
            binding.fabEdit.setOnClickListener {
                val bundle = Bundle().apply {
                    putString("edit_id", id)
                    putString("edit_title", title)
                    putString("edit_content", content)
                    putString("edit_areas", affectedAreas)
                    putString("edit_evac", evacuationCenter)
                    putString("edit_type", hazardTypeName)
                }
                val destination = if (isAlert) R.id.createEmergencyAlertFragment else R.id.createAnnouncementFragment
                findNavController().navigate(destination, bundle)
            }

            binding.fabDelete.setOnClickListener {
                confirmDelete(id, isAlert)
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
