package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.resilio.databinding.FragmentCreateAnnouncementBinding
import com.example.resilio.model.Announcement
import com.example.resilio.model.AnnouncementStatus
import com.example.resilio.model.HazardType
import com.example.resilio.model.User
import com.example.resilio.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class CreateAnnouncementFragment : Fragment(R.layout.fragment_create_announcement) {

    private var _binding: FragmentCreateAnnouncementBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var editId: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCreateAnnouncementBinding.bind(view)

        arguments?.let {
            editId = it.getString("edit_id")
            if (editId != null) {
                binding.etTitle.setText(it.getString("edit_title"))
                binding.etContent.setText(it.getString("edit_content"))
                binding.etAffectedAreas.setText(it.getString("edit_areas"))
                binding.etEvacuationCenter.setText(it.getString("edit_evac"))
                binding.btnSubmitAlert.setText(R.string.action_update_announcement)
            }
        }

        binding.btnSubmitAlert.setOnClickListener {
            submitAnnouncement()
        }

        binding.btnSetVrMap.setOnClickListener {
            val content = binding.etContent.text.toString().trim()
            val address = binding.etAffectedAreas.text.toString().trim()

            if (content.isEmpty()) {
                Toast.makeText(requireContext(), "Details/Description required for map location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (address.isEmpty()) {
                Toast.makeText(requireContext(), "Address required for map location", Toast.LENGTH_SHORT).show()
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
