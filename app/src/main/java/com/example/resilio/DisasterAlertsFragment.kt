package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.resilio.model.Announcement
import com.example.resilio.model.EmergencyAlert
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class DisasterAlertsFragment : Fragment(R.layout.fragment_disaster_alerts) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var alertsListener: ListenerRegistration? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvAlerts)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        rv.layoutManager = LinearLayoutManager(requireContext())

        alertsListener = db.collection("emergency_alerts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { value, _ ->
                if (getView() == null) return@addSnapshotListener

                val alerts = value?.toObjects(EmergencyAlert::class.java) ?: emptyList()
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
                            content = it.content,
                            type = it.type,
                            authorUid = it.authorUid,
                            timestamp = it.timestamp,
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
                        currentUserId = auth.currentUser?.uid
                    )
                }
            }
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
        }
        findNavController().navigate(R.id.createEmergencyAlertFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        alertsListener?.remove()
    }
}
