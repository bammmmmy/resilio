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

class ManageReportsFragment : Fragment(R.layout.fragment_manage_reports) {

    private val viewModel: ChairmanViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvPendingAnnouncements)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        
        rv.layoutManager = LinearLayoutManager(requireContext())

        viewModel.pendingAnnouncements.observe(viewLifecycleOwner) { list ->
            if (list.isNullOrEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                rv.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                rv.visibility = View.VISIBLE
                rv.adapter = AnnouncementAdapter(
                    announcements = list,
                    showActions = true,
                    onApprove = { id ->
                        viewModel.approveAnnouncement(id)
                        Toast.makeText(requireContext(), "Announcement Approved", Toast.LENGTH_SHORT).show()
                    },
                    onReject = { id ->
                        // Rejection could update status to REJECTED
                        Toast.makeText(requireContext(), "Announcement Rejected", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        viewModel.listenToPendingAnnouncements()
    }
}
