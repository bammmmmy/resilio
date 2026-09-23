package com.example.resilio

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.resilio.model.EmergencyAlert
import com.example.resilio.util.TimeUtils
import android.graphics.drawable.GradientDrawable

class LatestAlertsHomeAdapter(
    private var alerts: List<EmergencyAlert>,
    private val onItemClick: (EmergencyAlert) -> Unit
) : RecyclerView.Adapter<LatestAlertsHomeAdapter.ViewHolder>() {

    fun updateItems(nextAlerts: List<EmergencyAlert>) {
        alerts = nextAlerts
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
        val tvType: TextView = view.findViewById(R.id.tvAlertType)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_latest_alert_home, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = alerts[position]
        holder.tvTitle.text = item.title
        holder.tvContent.text = item.safeContent
        holder.tvType.text = alertTypeLabel(item.type.name)
        holder.tvTimestamp.text = TimeUtils.formatToPhTime(item.safeTimestamp, "MMM d, yyyy, h:mm a")
        val typeColor = when (item.type.name) {
            "TYPHOON", "EARTHQUAKE" -> "#EF4050"
            "FLOOD", "LANDSLIDE" -> "#F58C18"
            else -> "#2279D2"
        }
        (holder.tvType.background as? GradientDrawable)?.setColor(Color.parseColor(typeColor))
        holder.itemView.setOnClickListener { onItemClick(item) }
        
        // Hide divider for the last item
        val divider = holder.itemView.findViewById<View>(R.id.divider_line)
        if (position == alerts.size - 1) {
            divider?.visibility = View.GONE
        }
    }

    override fun getItemCount() = alerts.size

    private fun alertTypeLabel(type: String): String = when (type) {
        "TYPHOON" -> "Typhoon"
        "EARTHQUAKE" -> "Earthquake"
        "LANDSLIDE" -> "Landslide"
        "FLOOD" -> "Flood"
        else -> "Alert"
    }
}
