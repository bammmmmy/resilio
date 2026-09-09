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

import com.example.resilio.model.AnnouncementStatus
import com.example.resilio.model.EmergencyAlert

class ManageReportsFragment : Fragment(R.layout.fragment_manage_reports) {

    private val viewModel: ChairmanViewModel by viewModels()

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
                }
            )
        }

        viewModel.pendingAlerts.observe(viewLifecycleOwner) { list ->
            updateEmptyState(tvEmpty)
            rvAlerts.adapter = EmergencyAlertApprovalAdapter(
                alerts = list,
                onApprove = { id ->
                    viewModel.approveAlert(id)
                    Toast.makeText(requireContext(), "Alert Approved", Toast.LENGTH_SHORT).show()
                },
                onReject = { id ->
                    viewModel.rejectAlert(id)
                }
            )
        }

        viewModel.listenToPendingAnnouncements()
        viewModel.listenToPendingAlerts()
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
