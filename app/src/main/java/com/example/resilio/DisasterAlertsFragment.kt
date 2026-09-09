package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.resilio.model.Announcement
import com.example.resilio.model.AnnouncementStatus
import com.example.resilio.model.EmergencyAlert
import com.example.resilio.model.UserRole
import com.example.resilio.util.ProfileManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DisasterAlertsFragment : Fragment(R.layout.fragment_disaster_alerts) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var alertsListener: ListenerRegistration? = null
    private var currentUserRole: UserRole? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvAlerts)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val btnViewArchive = view.findViewById<MaterialButton>(R.id.btn_view_archive)
        
        rv.layoutManager = LinearLayoutManager(requireContext())

        lifecycleScope.launch {
            currentUserRole = ProfileManager.getProfile(requireContext()).first().role
            
            if (currentUserRole == UserRole.CHAIRMAN || currentUserRole == UserRole.BDRRMO) {
                btnViewArchive.visibility = View.VISIBLE
                btnViewArchive.setOnClickListener {
                    findNavController().navigate(R.id.archivedAlertsFragment)
                }
            }
            
            setupAlertsListener(rv, tvEmpty)
        }
    }

    private fun setupAlertsListener(rv: RecyclerView, tvEmpty: TextView) {
        alertsListener = db.collection("emergency_alerts")
            .addSnapshotListener { value, _ ->
                if (getView() == null) return@addSnapshotListener

                val allAlerts = value?.toObjects(EmergencyAlert::class.java) ?: emptyList()
                
                // Show only approved alerts (non-archived)
                val alerts = allAlerts.filter { it.status == AnnouncementStatus.APPROVED }
                    .sortedByDescending { it.safeTimestamp }
                
                if (alerts.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    rv.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    rv.visibility = View.VISIBLE
                    
                    val mapped = alerts.map { 
                        Announcement(
                            id = it.id,
                            title = it.title,
                            content = it.safeContent,
                            type = it.type,
                            status = it.status,
                            authorUid = it.authorUid,
                            timestamp = it.safeTimestamp,
                            affectedAreas = it.affectedAreas,
                            evacuationCenter = it.evacuationCenter
                        )
                    }
                    rv.adapter = AnnouncementAdapter(
                        mapped,
                        onItemClick = { announcement ->
                            navigateToDetail(announcement)
                        },
                        onEdit = { announcement ->
                            navigateToEdit(announcement)
                        },
                        onDelete = { announcement ->
                            confirmDelete(announcement)
                        },
                        onArchive = { announcement ->
                            confirmArchive(announcement)
                        },
                        currentUserId = auth.currentUser?.uid,
                        userRole = currentUserRole
                    )
                }
            }
    }

    private fun confirmArchive(announcement: Announcement) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_archive)
            .setMessage("Archive this alert? It will be moved to the archive list and hidden from the map.")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.action_archive) { _, _ ->
                db.collection("emergency_alerts").document(announcement.id)
                    .update("status", AnnouncementStatus.ARCHIVED)
                db.collection("hazardLocations").document(announcement.id)
                    .update("active", false)
                Toast.makeText(requireContext(), "Archived", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun confirmDelete(announcement: Announcement) {
        val ctx = context ?: return
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.delete_alert_title)
            .setMessage(R.string.delete_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_evacuation_area) { _, _ ->
                db.collection("emergency_alerts").document(announcement.id).delete()
                    .addOnSuccessListener {
                        // Also delete the linked hazard location
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
            putBoolean("isAlert", true)
            putString("hazardType", announcement.type.name)
            putString("status", announcement.status.name)
        }
        findNavController().navigate(R.id.announcementDetailFragment, bundle)
    }

    private fun navigateToEdit(announcement: Announcement) {
        val bundle = Bundle().apply {
            putString("edit_id", announcement.id)
            putString("edit_title", announcement.title)
            putString("edit_content", announcement.content)
            putString("edit_type", announcement.type.name)
            putString("edit_areas", announcement.affectedAreas)
            putString("edit_evac", announcement.evacuationCenter)
            putString("edit_author_uid", announcement.authorUid)
        }
        findNavController().navigate(R.id.createEmergencyAlertFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        alertsListener?.remove()
    }
}
