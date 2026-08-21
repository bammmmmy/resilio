package com.example.resilio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.resilio.model.EmergencyAlert

class LatestAlertsHomeAdapter(
    private val alerts: List<EmergencyAlert>,
    private val onItemClick: (EmergencyAlert) -> Unit
) : RecyclerView.Adapter<LatestAlertsHomeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_latest_alert_home, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = alerts[position]
        holder.tvTitle.text = item.title
        holder.tvContent.text = item.content
        holder.itemView.setOnClickListener { onItemClick(item) }
        
        // Hide divider for the last item
        val divider = holder.itemView.findViewById<View>(R.id.divider_line)
        if (position == alerts.size - 1) {
            divider?.visibility = View.GONE
        }
    }

    override fun getItemCount() = alerts.size
}
