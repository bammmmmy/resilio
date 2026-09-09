package com.example.resilio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.resilio.model.Announcement
import com.example.resilio.model.AnnouncementStatus
import com.example.resilio.model.UserRole
import com.example.resilio.util.TimeUtils

class AnnouncementAdapter(
    private val announcements: List<Announcement>,
    private val onItemClick: ((Announcement) -> Unit)? = null,
    private val showActions: Boolean = false,
    private val onApprove: ((String) -> Unit)? = null,
    private val onReject: ((String) -> Unit)? = null,
    private val onEdit: ((Announcement) -> Unit)? = null,
    private val onDelete: ((Announcement) -> Unit)? = null,
    private val onArchive: ((Announcement) -> Unit)? = null,
    private val onRestore: ((Announcement) -> Unit)? = null,
    private val currentUserId: String? = null,
    private val userRole: UserRole? = null
) : RecyclerView.Adapter<AnnouncementAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvType: TextView = view.findViewById(R.id.tvType)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
        val layoutActions: View = view.findViewById(R.id.layoutActions)
        val btnApprove: View = view.findViewById(R.id.btnApprove)
        val btnReject: View = view.findViewById(R.id.btnReject)
        val btnEdit: View = view.findViewById(R.id.btnEdit)
        val btnDelete: View = view.findViewById(R.id.btnDelete)
        val btnArchive: View = view.findViewById(R.id.btnArchive)
        val btnRestore: View = view.findViewById(R.id.btnRestore)
        val layoutAuthorActions: View = view.findViewById(R.id.layoutAuthorActions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_announcement, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = announcements[position]
        holder.tvTitle.text = item.title
        holder.tvType.text = item.type.name
        holder.tvTimestamp.text = TimeUtils.formatToPhTime(item.safeTimestamp)
        holder.tvContent.text = item.safeContent

        holder.itemView.setOnClickListener { onItemClick?.invoke(item) }

        if (showActions) {
            holder.layoutActions.visibility = View.VISIBLE
            holder.btnApprove.setOnClickListener { onApprove?.invoke(item.id) }
            holder.btnReject.setOnClickListener { onReject?.invoke(item.id) }
        } else {
            holder.layoutActions.visibility = View.GONE
        }

        val isAuthor = currentUserId != null && item.authorUid == currentUserId
        val isChairman = userRole == UserRole.CHAIRMAN
        val isBdrrmo = userRole == UserRole.BDRRMO
        val isManagement = isChairman || isBdrrmo
        val isArchived = item.status == AnnouncementStatus.ARCHIVED

        // Control visibility of author actions (Edit/Delete)
        if ((isAuthor || isChairman) && !isArchived) {
            holder.layoutAuthorActions.visibility = View.VISIBLE
            holder.btnEdit.visibility = View.VISIBLE
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnEdit.setOnClickListener { onEdit?.invoke(item) }
            holder.btnDelete.setOnClickListener { onDelete?.invoke(item) }
        } else {
            holder.btnEdit.visibility = View.GONE
            holder.btnDelete.visibility = View.GONE
        }

        // Control Archive/Restore visibility for management
        if (isManagement) {
            holder.layoutAuthorActions.visibility = View.VISIBLE
            if (isArchived) {
                holder.btnArchive.visibility = View.GONE
                holder.btnRestore.visibility = View.VISIBLE
                holder.btnRestore.setOnClickListener { onRestore?.invoke(item) }
            } else {
                holder.btnArchive.visibility = View.VISIBLE
                holder.btnRestore.visibility = View.GONE
                holder.btnArchive.setOnClickListener { onArchive?.invoke(item) }
            }
        } else {
            holder.btnArchive.visibility = View.GONE
            holder.btnRestore.visibility = View.GONE
            // If not author/chairman, and not management, hide the entire actions layout
            if (!isAuthor && !isChairman) {
                holder.layoutAuthorActions.visibility = View.GONE
            }
        }
    }

    override fun getItemCount() = announcements.size
}
