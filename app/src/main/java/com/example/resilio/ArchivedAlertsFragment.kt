package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.resilio.model.Announcement
import com.example.resilio.model.AnnouncementStatus
import com.example.resilio.model.EmergencyAlert
import com.example.resilio.model.UserRole
import com.example.resilio.util.ProfileManager
import com.example.resilio.viewmodel.ResidentViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ArchivedAlertsFragment : Fragment(R.layout.fragment_archived_items) {

    private val viewModel: ResidentViewModel by viewModels()
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var currentUserRole: UserRole? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tvTitle).text = getString(R.string.archived_alerts_title)
        val rv = view.findViewById<RecyclerView>(R.id.rvArchived)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        rv.layoutManager = LinearLayoutManager(requireContext())

        lifecycleScope.launch {
            currentUserRole = ProfileManager.getProfile(requireContext()).first().role
            
            db.collection("emergency_alerts")
                .whereEqualTo("status", AnnouncementStatus.ARCHIVED.name)
                .addSnapshotListener { value, error ->
                    if (error != null || _binding_check() == false) return@addSnapshotListener
                    val alerts = value?.toObjects(EmergencyAlert::class.java) ?: emptyList()
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
                    val sorted = mapped.sortedByDescending { it.safeTimestamp }
                    
                    if (sorted.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                        rv.visibility = View.GONE
                    } else {
                        tvEmpty.visibility = View.GONE
                        rv.visibility = View.VISIBLE
                        rv.adapter = AnnouncementAdapter(
                            announcements = sorted,
                            onItemClick = { item -> navigateToDetail(item) },
                            onRestore = { item -> confirmRestore(item) },
                            currentUserId = auth.currentUser?.uid,
                            userRole = currentUserRole
                        )
                    }
                }
        }
    }

    private fun _binding_check(): Boolean = view != null

    private fun confirmRestore(item: Announcement) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_restore)
            .setMessage("Restore this alert to the active list?")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.action_restore) { _, _ ->
                viewModel.restoreItem(item.id, true)
                Toast.makeText(requireContext(), "Alert restored", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun navigateToDetail(item: Announcement) {
        val bundle = Bundle().apply {
            putString("id", item.id)
            putString("title", item.title)
            putString("content", item.content)
            putString("authorUid", item.authorUid)
            putString("affectedAreas", item.affectedAreas)
            putString("evacuationCenter", item.evacuationCenter)
            putBoolean("isAlert", true)
            putString("hazardType", item.type.name)
            putString("status", item.status.name)
        }
        findNavController().navigate(R.id.announcementDetailFragment, bundle)
    }
}
