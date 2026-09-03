package com.example.resilio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatMessageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ChatMessage>()
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }

    fun replaceAll(list: List<ChatMessage>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun addMessage(message: ChatMessage) {
        items.add(message)
        notifyItemInserted(items.size - 1)
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].role == "user") VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_USER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_user, parent, false)
            UserVH(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_ai, parent, false)
            AiVH(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = items[position]
        val timestamp = timeFormat.format(Date(msg.timestamp))

        if (holder is UserVH) {
            holder.text.text = msg.content
            holder.tvTimestamp.text = timestamp
        } else if (holder is AiVH) {
            holder.text.text = msg.content
            holder.tvTimestamp.text = timestamp
            
            // Show avatar only if it's the first message from AI in a group
            val isFirstInGroup = position == 0 || items[position - 1].role == "user"
            holder.ivAvatar.visibility = if (isFirstInGroup) View.VISIBLE else View.INVISIBLE
        }
    }

    override fun getItemCount(): Int = items.size

    class UserVH(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.message_text)
        val tvTimestamp: TextView = view.findViewById(R.id.tv_timestamp)
    }

    class AiVH(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.message_text)
        val ivAvatar: ImageView = view.findViewById(R.id.iv_avatar)
        val tvTimestamp: TextView = view.findViewById(R.id.tv_timestamp)
    }
}
