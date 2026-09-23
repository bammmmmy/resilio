package com.example.resilio

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.resilio.model.Announcement
import com.example.resilio.util.TimeUtils

class LatestAnnouncementsHomeAdapter(
    private var announcements: List<Announcement>,
    private val onItemClick: (Announcement) -> Unit
) : RecyclerView.Adapter<LatestAnnouncementsHomeAdapter.ViewHolder>() {

    fun updateItems(nextAnnouncements: List<Announcement>) {
        announcements = nextAnnouncements
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
        val tvType: TextView = view.findViewById(R.id.tvAlertType)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val ivAlertIcon: View = view.findViewById(R.id.ivAlertIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_latest_announcement_home, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = announcements[position]
        holder.tvTitle.text = item.title
        holder.tvContent.text = item.safeContent
        holder.tvType.text = "Announcement"
        holder.tvTimestamp.text = TimeUtils.formatToPhTime(item.safeTimestamp, "MMM d, yyyy, h:mm a")
        holder.tvTitle.setTextColor(Color.parseColor("#244A72"))
        
        holder.itemView.setOnClickListener { onItemClick(item) }
        
        val divider = holder.itemView.findViewById<View>(R.id.divider_line)
        if (position == announcements.size - 1) {
            divider?.visibility = View.GONE
        }
    }

    override fun getItemCount() = announcements.size
}
