package com.example.resilio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.resilio.model.Announcement

class LatestAnnouncementsHomeAdapter(
    private val announcements: List<Announcement>,
    private val onItemClick: (Announcement) -> Unit
) : RecyclerView.Adapter<LatestAnnouncementsHomeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_latest_alert_home, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = announcements[position]
        holder.tvTitle.text = item.title
        holder.tvContent.text = item.safeContent
        
        // Use a gold color for announcements to distinguish from red alerts
        holder.tvTitle.setTextColor(holder.itemView.context.getColor(R.color.gold_accent))
        
        holder.itemView.setOnClickListener { onItemClick(item) }
        
        val divider = holder.itemView.findViewById<View>(R.id.divider_line)
        if (position == announcements.size - 1) {
            divider?.visibility = View.GONE
        }
    }

    override fun getItemCount() = announcements.size
}
