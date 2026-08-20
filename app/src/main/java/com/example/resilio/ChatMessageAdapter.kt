package com.example.resilio

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class ChatMessageAdapter : RecyclerView.Adapter<ChatMessageAdapter.VH>() {

    private val items = mutableListOf<ChatMessage>()

    fun replaceAll(list: List<ChatMessage>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = items[position]
        holder.text.text = msg.content
        val isUser = msg.role == "user"
        
        val bg = if (isUser) R.drawable.bg_chat_bubble_user_v2 else R.drawable.bg_chat_bubble_ai
        holder.text.background = ContextCompat.getDrawable(holder.itemView.context, bg)
        holder.text.setTextColor(if (isUser) ContextCompat.getColor(holder.itemView.context, R.color.white) else ContextCompat.getColor(holder.itemView.context, R.color.text_primary))
        
        val layoutParams = holder.text.layoutParams as LinearLayout.LayoutParams
        layoutParams.gravity = if (isUser) Gravity.END else Gravity.START
        holder.text.layoutParams = layoutParams

        val showAvatar = !isUser && (position == 0 || items[position - 1].role == "user")
        holder.ivAvatar.visibility = if (showAvatar) View.VISIBLE else View.GONE
    }

    override fun getItemCount(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.message_text)
        val ivAvatar: ImageView = view.findViewById(R.id.iv_avatar)
    }
}
