package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.resilio.databinding.FragmentAnnouncementsBinding
import com.example.resilio.model.Announcement
import com.example.resilio.model.UserRole
import com.example.resilio.util.ProfileManager
import com.example.resilio.viewmodel.ResidentViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AnnouncementsFragment : Fragment(R.layout.fragment_announcements) {

    private var _binding: FragmentAnnouncementsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ResidentViewModel by viewModels()
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var currentUserRole: UserRole? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAnnouncementsBinding.bind(view)

        binding.rvAnnouncements.layoutManager = LinearLayoutManager(requireContext())
        binding.tvEmptyAnnouncements.visibility = View.GONE
        
        lifecycleScope.launch {
            currentUserRole = ProfileManager.getProfile(requireContext()).first().role
            
            if (currentUserRole == UserRole.CHAIRMAN || currentUserRole == UserRole.BDRRMO) {
                binding.btnViewArchive.visibility = View.VISIBLE
                binding.btnViewArchive.setOnClickListener {
                    findNavController().navigate(R.id.archivedAnnouncementsFragment)
                }
            }
            
            observeAnnouncements()
        }

        viewModel.listenToAnnouncements()
    }

    private fun observeAnnouncements() {
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
                    onArchive = { announcement ->
                        confirmArchive(announcement, false)
                    },
                    currentUserId = auth.currentUser?.uid,
                    userRole = currentUserRole
                )
            }
        }
    }

    private fun confirmArchive(announcement: Announcement, isAlert: Boolean) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_archive)
            .setMessage("Archive this announcement? It will be moved to the archive list and hidden from the map.")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.action_archive) { _, _ ->
                viewModel.archiveItem(announcement.id, isAlert)
                Toast.makeText(requireContext(), "Archived", Toast.LENGTH_SHORT).show()
            }
            .show()
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
                        // Also delete the linked hazard location if it exists
                        db.collection("hazardLocations").document(announcement.id).delete()

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
            putString("status", announcement.status.name)
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
            putString("edit_author_uid", announcement.authorUid)
        }
        findNavController().navigate(R.id.createAnnouncementFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
