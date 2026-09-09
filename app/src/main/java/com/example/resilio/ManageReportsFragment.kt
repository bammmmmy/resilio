package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.resilio.viewmodel.ChairmanViewModel
import androidx.navigation.fragment.findNavController

import com.example.resilio.model.AnnouncementStatus
import com.example.resilio.model.EmergencyAlert
import com.example.resilio.model.Announcement
import com.example.resilio.model.UserRole
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ManageReportsFragment : Fragment(R.layout.fragment_manage_reports) {

    private val viewModel: ChairmanViewModel by viewModels()
    private val db = FirebaseFirestore.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvAnnouncements = view.findViewById<RecyclerView>(R.id.rvPendingAnnouncements)
        val rvAlerts = view.findViewById<RecyclerView>(R.id.rvPendingAlerts)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        
        rvAnnouncements.layoutManager = LinearLayoutManager(requireContext())
        rvAlerts.layoutManager = LinearLayoutManager(requireContext())

        viewModel.pendingAnnouncements.observe(viewLifecycleOwner) { list ->
            updateEmptyState(tvEmpty)
            rvAnnouncements.adapter = AnnouncementAdapter(
                announcements = list,
                showActions = true,
                onApprove = { id ->
                    viewModel.approveAnnouncement(id)
                    Toast.makeText(requireContext(), "Approved", Toast.LENGTH_SHORT).show()
                },
                onReject = { id ->
                    viewModel.rejectAnnouncement(id)
                    Toast.makeText(requireContext(), "Rejected", Toast.LENGTH_SHORT).show()
                },
                onEdit = { announcement ->
                    navigateToEdit(announcement, false)
                },
                onDelete = { announcement ->
                    confirmDelete(announcement, false)
                },
                userRole = UserRole.CHAIRMAN
            )
        }

        viewModel.pendingAlerts.observe(viewLifecycleOwner) { list ->
            updateEmptyState(tvEmpty)
            
            val mapped = list.map { 
                Announcement(
                    id = it.id,
                    title = it.title,
                    content = it.safeContent,
                    type = it.type,
                    authorUid = it.authorUid,
                    timestamp = it.safeTimestamp,
                    affectedAreas = it.affectedAreas,
                    evacuationCenter = it.evacuationCenter
                )
            }
            
            rvAlerts.adapter = AnnouncementAdapter(
                announcements = mapped,
                showActions = true,
                onApprove = { id ->
                    viewModel.approveAlert(id)
                    Toast.makeText(requireContext(), "Alert Approved", Toast.LENGTH_SHORT).show()
                },
                onReject = { id ->
                    viewModel.rejectAlert(id)
                },
                onEdit = { announcement ->
                    navigateToEdit(announcement, true)
                },
                onDelete = { announcement ->
                    confirmDelete(announcement, true)
                },
                userRole = UserRole.CHAIRMAN
            )
        }

        viewModel.listenToPendingAnnouncements()
        viewModel.listenToPendingAlerts()
    }

    private fun navigateToEdit(announcement: Announcement, isAlert: Boolean) {
        val bundle = Bundle().apply {
            putString("edit_id", announcement.id)
            putString("edit_title", announcement.title)
            putString("edit_content", announcement.content)
            putString("edit_areas", announcement.affectedAreas)
            putString("edit_evac", announcement.evacuationCenter)
            if (isAlert) putString("edit_type", announcement.type.name)
            putString("edit_author_uid", announcement.authorUid)
        }
        val dest = if (isAlert) R.id.createEmergencyAlertFragment else R.id.createAnnouncementFragment
        findNavController().navigate(dest, bundle)
    }

    private fun confirmDelete(announcement: Announcement, isAlert: Boolean) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isAlert) "Delete Alert?" else "Delete Announcement?")
            .setMessage("Are you sure you want to delete this?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                val coll = if (isAlert) "emergency_alerts" else "announcements"
                db.collection(coll).document(announcement.id).delete()
                db.collection("hazardLocations").document(announcement.id).delete()
                Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun updateEmptyState(tvEmpty: TextView) {
        val noAnnouncements = viewModel.pendingAnnouncements.value.isNullOrEmpty()
        val noAlerts = viewModel.pendingAlerts.value.isNullOrEmpty()
        
        if (noAnnouncements && noAlerts) {
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
        }
    }
}
