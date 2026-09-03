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
import com.example.resilio.viewmodel.ChairmanViewModel

class ManageReportsFragment : Fragment(R.layout.fragment_manage_reports) {

    private val viewModel: ChairmanViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvEmergencyReports)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        
        rv.layoutManager = LinearLayoutManager(requireContext())

        viewModel.emergencyReports.observe(viewLifecycleOwner) { list ->
            if (list.isNullOrEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                rv.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                rv.visibility = View.VISIBLE
                rv.adapter = EmergencyReportAdapter(
                    reports = list,
                    onUpdateStatus = { id, status ->
                        viewModel.updateReportStatus(id, status)
                        Toast.makeText(requireContext(), "Status Updated: $status", Toast.LENGTH_SHORT).show()
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

        viewModel.listenToEmergencyReports()
    }
}
