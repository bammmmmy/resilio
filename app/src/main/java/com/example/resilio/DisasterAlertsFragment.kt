package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.resilio.model.Announcement
import com.example.resilio.model.EmergencyAlert
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class DisasterAlertsFragment : Fragment(R.layout.fragment_disaster_alerts) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvAlerts)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        rv.layoutManager = LinearLayoutManager(requireContext())

        FirebaseFirestore.getInstance().collection("emergency_alerts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { value, _ ->
                val alerts = value?.toObjects(EmergencyAlert::class.java) ?: emptyList()
                if (alerts.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    rv.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    rv.visibility = View.VISIBLE
                    
                    // Reusing AnnouncementAdapter for simplicity as EmergencyAlert has similar fields
                    val mapped = alerts.map { 
                        Announcement(
                            title = it.title,
                            content = it.content,
                            type = it.type,
                            timestamp = it.timestamp
                        )
                    }
                    rv.adapter = AnnouncementAdapter(mapped)
                }
            }
    }
}
