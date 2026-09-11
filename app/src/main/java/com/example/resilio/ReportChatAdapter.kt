package com.example.resilio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.resilio.model.ReportChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportChatAdapter(private val currentUid: String) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ReportChatMessage>()

    companion object {
        private const val VIEW_TYPE_ME = 1
        private const val VIEW_TYPE_OTHER = 2
    }

    fun submitList(newList: List<ReportChatMessage>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].senderUid == currentUid) VIEW_TYPE_ME else VIEW_TYPE_OTHER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_ME) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_user, parent, false)
            MyMessageVH(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_ai, parent, false)
            OtherMessageVH(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is MyMessageVH) {
            holder.tvMessage.text = item.message
            holder.tvTime.text = formatTime(item.safeTimestamp)
        } else if (holder is OtherMessageVH) {
            holder.tvMessage.text = item.message
            holder.tvTime.text = formatTime(item.safeTimestamp)
            holder.tvName.text = if (item.senderRole == "admin") "Admin: ${item.senderName}" else item.senderName
            holder.tvName.visibility = View.VISIBLE
        }
    }

    override fun getItemCount() = items.size

    private fun formatTime(millis: Long): String {
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
    }

    class MyMessageVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.message_text)
        val tvTime: TextView = view.findViewById(R.id.tv_timestamp)
    }

    class OtherMessageVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.message_text)
        val tvTime: TextView = view.findViewById(R.id.tv_timestamp)
        val tvName: TextView = view.findViewById(R.id.tv_name)
    }
}
