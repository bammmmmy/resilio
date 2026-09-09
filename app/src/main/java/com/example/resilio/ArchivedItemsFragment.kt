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
import com.example.resilio.model.UserRole
import com.example.resilio.util.ProfileManager
import com.example.resilio.viewmodel.ResidentViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ArchivedItemsFragment : Fragment(R.layout.fragment_archived_items) {

    private val viewModel: ResidentViewModel by viewModels()
    private val auth = FirebaseAuth.getInstance()
    private var currentUserRole: UserRole? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvArchived)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        rv.layoutManager = LinearLayoutManager(requireContext())

        lifecycleScope.launch {
            currentUserRole = ProfileManager.getProfile(requireContext()).first().role
            
            viewModel.archivedItems.observe(viewLifecycleOwner) { list ->
                if (list.isNullOrEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    rv.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    rv.visibility = View.VISIBLE
                    rv.adapter = AnnouncementAdapter(
                        announcements = list,
                        onItemClick = { item ->
                            navigateToDetail(item)
                        },
                        currentUserId = auth.currentUser?.uid,
                        userRole = currentUserRole
                    )
                }
            }
        }

        viewModel.listenToArchivedItems()
    }

    private fun navigateToDetail(item: Announcement) {
        val isAlert = item.type != com.example.resilio.model.HazardType.GENERAL_ALERT
        val bundle = Bundle().apply {
            putString("id", item.id)
            putString("title", item.title)
            putString("content", item.content)
            putString("authorUid", item.authorUid)
            putString("affectedAreas", item.affectedAreas)
            putString("evacuationCenter", item.evacuationCenter)
            putBoolean("isAlert", isAlert)
            putString("status", item.status.name)
            if (isAlert) putString("hazardType", item.type.name)
        }
        findNavController().navigate(R.id.announcementDetailFragment, bundle)
    }
}
