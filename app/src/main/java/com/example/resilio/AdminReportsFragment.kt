package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.resilio.model.ReportStatus
import com.example.resilio.model.EmergencyReport
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class AdminReportsFragment : Fragment(R.layout.fragment_resident_reports) {

    private val db = FirebaseFirestore.getInstance()
    private var isShowingArchive = false
    private var reportsListener: ListenerRegistration? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvEmergencyReports)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val btnToggle = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnToggleArchive)
        
        tvTitle.text = "Resident Emergency Reports"
        tvTitle.setTextColor(resources.getColor(R.color.emergency_red, null))
        btnToggle.visibility = View.VISIBLE
        btnToggle.text = if (isShowingArchive) "SHOW ACTIVE" else "SHOW ARCHIVE"
        
        rv.layoutManager = LinearLayoutManager(requireContext())

        btnToggle.setOnClickListener {
            isShowingArchive = !isShowingArchive
            btnToggle.text = if (isShowingArchive) "SHOW ACTIVE" else "SHOW ARCHIVE"
            listenToAllReports(rv, tvEmpty)
        }

        listenToAllReports(rv, tvEmpty)
    }

    private fun listenToAllReports(rv: RecyclerView, tvEmpty: TextView) {
        reportsListener?.remove()
        
        reportsListener = db.collection("emergency_reports")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { value, error ->
                if (error != null || view == null) return@addSnapshotListener
                
                val reports = value?.toObjects(EmergencyReport::class.java) ?: emptyList()
                val filtered = if (isShowingArchive) {
                    reports.filter { it.status == ReportStatus.ARCHIVED }
                } else {
                    reports.filter { it.status != ReportStatus.ARCHIVED }
                }

                if (filtered.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = if (isShowingArchive) "No archived reports" else "No active emergency reports"
                    rv.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    rv.visibility = View.VISIBLE
                    rv.adapter = EmergencyReportAdapter(
                        reports = filtered,
                        isAdmin = true,
                        onUpdateStatus = { id, status ->
                            db.collection("emergency_reports").document(id).update("status", status)
                            
                            // If archived, deactivate hazard location marker
                            if (status == ReportStatus.ARCHIVED) {
                                db.collection("hazardLocations").document(id).update("active", false)
                            } else if (status == ReportStatus.RESOLVED || status == ReportStatus.RESPONDING || status == ReportStatus.PENDING) {
                                // If restored or active, ensure marker is active (if we want that behavior)
                                db.collection("hazardLocations").document(id).update("active", true)
                            }

                            Toast.makeText(requireContext(), "Status Updated", Toast.LENGTH_SHORT).show()
                        },
                        onViewOnMap = { lat, lng ->
                            val args = Bundle().apply {
                                putFloat("focusLatitude", lat.toFloat())
                                putFloat("focusLongitude", lng.toFloat())
                            }
                            findNavController().navigate(R.id.evacuationMapFragment, args)
                        },
                        onChat = { reportId ->
                            val args = Bundle().apply { putString("reportId", reportId) }
                            findNavController().navigate(R.id.reportChatFragment, args)
                        }
                    )
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        reportsListener?.remove()
    }
}
