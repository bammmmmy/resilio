package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.resilio.util.TimeUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ActivityLogFragment : Fragment(R.layout.fragment_activity_log) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val list = view.findViewById<RecyclerView>(R.id.rv_activity_log)
        val empty = view.findViewById<TextView>(R.id.tv_activity_empty)
        list.layoutManager = LinearLayoutManager(requireContext())
        FirebaseFirestore.getInstance().collection("activity_logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val rows = snapshot.documents.map { document ->
                    val data = document.data.orEmpty()
                    ActivityLogRow(
                        action = data["action"] as? String ?: "updated",
                        entityName = data["entityName"] as? String ?: "Record",
                        details = data["details"] as? String ?: "",
                        actorName = data["actorName"] as? String ?: "Admin",
                        timestamp = data["timestamp"] as? com.google.firebase.Timestamp
                    )
                }
                empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                list.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
                list.adapter = ActivityLogAdapter(rows)
            }
    }
}

private data class ActivityLogRow(val action: String, val entityName: String, val details: String, val actorName: String, val timestamp: com.google.firebase.Timestamp?)

private class ActivityLogAdapter(private val rows: List<ActivityLogRow>) : RecyclerView.Adapter<ActivityLogAdapter.Holder>() {
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val action: TextView = view.findViewById(R.id.tv_activity_action)
        val entity: TextView = view.findViewById(R.id.tv_activity_entity)
        val details: TextView = view.findViewById(R.id.tv_activity_details)
        val meta: TextView = view.findViewById(R.id.tv_activity_meta)
    }
    override fun onCreateViewHolder(parent: android.view.ViewGroup, type: Int): Holder = Holder(android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_activity_log, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        holder.action.text = row.action.replace('_', ' ').replaceFirstChar { it.uppercase() }
        holder.entity.text = row.entityName
        holder.details.text = row.details
        holder.meta.text = "${row.actorName} · ${row.timestamp?.let { TimeUtils.formatToPhTime(it) } ?: "Just now"}"
    }
    override fun getItemCount(): Int = rows.size
}