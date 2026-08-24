package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.resilio.databinding.FragmentAnnouncementsBinding
import com.example.resilio.model.Announcement
import com.example.resilio.viewmodel.ResidentViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AnnouncementsFragment : Fragment(R.layout.fragment_announcements) {

    private var _binding: FragmentAnnouncementsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ResidentViewModel by viewModels()
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAnnouncementsBinding.bind(view)

        binding.rvAnnouncements.layoutManager = LinearLayoutManager(requireContext())
        binding.tvEmptyAnnouncements.visibility = View.GONE

        viewModel.announcements.observe(viewLifecycleOwner) { list ->
            if (_binding == null) return@observe
            if (list.isNullOrEmpty()) {
                binding.tvEmptyAnnouncements.visibility = View.VISIBLE
                binding.rvAnnouncements.visibility = View.GONE
            } else {
                binding.tvEmptyAnnouncements.visibility = View.GONE
                binding.rvAnnouncements.visibility = View.VISIBLE
                binding.rvAnnouncements.adapter = AnnouncementAdapter(
                    list,
                    onItemClick = { announcement ->
                        navigateToDetail(announcement)
                    },
                    onEdit = { announcement ->
                        navigateToEdit(announcement)
                    },
                    onDelete = { announcement ->
                        confirmDelete(announcement, false)
                    },
                    currentUserId = auth.currentUser?.uid
                )
            }
        }

        viewModel.listenToAnnouncements()
    }

    private fun confirmDelete(announcement: Announcement, isAlert: Boolean) {
        val ctx = context ?: return
        MaterialAlertDialogBuilder(ctx)
            .setTitle(if (isAlert) R.string.delete_alert_title else R.string.delete_announcement_title)
            .setMessage(R.string.delete_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_evacuation_area) { _, _ ->
                val collection = if (isAlert) "emergency_alerts" else "announcements"
                db.collection(collection).document(announcement.id).delete()
                    .addOnSuccessListener {
                        if (isAdded) {
                            Toast.makeText(requireContext(), R.string.deleted_success, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        if (isAdded) {
                            Toast.makeText(requireContext(), R.string.delete_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            .show()
    }

    private fun navigateToDetail(announcement: Announcement) {
        val bundle = Bundle().apply {
            putString("id", announcement.id)
            putString("title", announcement.title)
            putString("content", announcement.content)
            putString("authorUid", announcement.authorUid)
            putString("affectedAreas", announcement.affectedAreas)
            putString("evacuationCenter", announcement.evacuationCenter)
            putBoolean("isAlert", false)
        }
        findNavController().navigate(R.id.action_announcementsFragment_to_announcementDetailFragment, bundle)
    }

    private fun navigateToEdit(announcement: Announcement) {
        val bundle = Bundle().apply {
            putString("edit_id", announcement.id)
            putString("edit_title", announcement.title)
            putString("edit_content", announcement.content)
            putString("edit_areas", announcement.affectedAreas)
            putString("edit_evac", announcement.evacuationCenter)
        }
        findNavController().navigate(R.id.createAnnouncementFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
