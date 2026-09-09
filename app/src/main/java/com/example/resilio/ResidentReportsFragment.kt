package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.resilio.model.ReportStatus
import com.example.resilio.viewmodel.ChairmanViewModel

class ResidentReportsFragment : Fragment(R.layout.fragment_resident_reports) {

    private val viewModel: ChairmanViewModel by viewModels()
    private var isShowingArchive = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvEmergencyReports)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val btnToggle = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnToggleArchive)
        
        rv.layoutManager = LinearLayoutManager(requireContext())

        viewModel.emergencyReports.observe(viewLifecycleOwner) { list ->
            updateList(list, rv, tvEmpty)
        }

        btnToggle.setOnClickListener {
            isShowingArchive = !isShowingArchive
            btnToggle.text = if (isShowingArchive) "SHOW ACTIVE" else "SHOW ARCHIVE"
            updateList(viewModel.emergencyReports.value, rv, tvEmpty)
        }

        viewModel.listenToEmergencyReports()
    }

    private fun updateList(list: List<com.example.resilio.model.EmergencyReport>?, rv: RecyclerView, tvEmpty: TextView) {
        val filtered = if (isShowingArchive) {
            list?.filter { it.status == ReportStatus.ARCHIVED }
        } else {
            list?.filter { it.status != ReportStatus.ARCHIVED }
        }

        if (filtered.isNullOrEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = if (isShowingArchive) "No archived reports" else "No active emergency reports"
            rv.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rv.visibility = View.VISIBLE
            rv.adapter = EmergencyReportAdapter(
                reports = filtered,
                onUpdateStatus = { id, status ->
                    viewModel.updateReportStatus(id, status)
                    val msg = if (status == ReportStatus.ARCHIVED) "Report Archived" else "Status Updated: $status"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                },
                onViewOnMap = { lat, lng ->
                    val args = Bundle().apply {
                        putFloat("focusLatitude", lat.toFloat())
                        putFloat("focusLongitude", lng.toFloat())
                    }
                    findNavController().navigate(R.id.evacuationMapFragment, args)
                }
            )
        }
    }
}
