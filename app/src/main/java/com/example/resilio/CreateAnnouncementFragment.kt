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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCreateAnnouncementBinding.bind(view)

        binding.btnSubmitAlert.setOnClickListener {
            submitAnnouncement()
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

        val announcementId = UUID.randomUUID().toString()

        db.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                val user = snapshot.toObject(User::class.java)
                val isChairman = user?.role == UserRole.CHAIRMAN
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
                        val message = if (isChairman) {
                            getString(R.string.announcement_published)
                        } else {
                            getString(R.string.announcement_submitted_pending)
                        }
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                        findNavController().popBackStack()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Failed to submit: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
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
