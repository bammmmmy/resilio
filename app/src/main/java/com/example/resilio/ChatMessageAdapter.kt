package com.example.resilio

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
        val bg = if (isUser) R.drawable.chat_bubble_user else R.drawable.chat_bubble_assistant
        holder.text.background = ContextCompat.getDrawable(holder.itemView.context, bg)
        holder.text.setTextColor(if (isUser) ContextCompat.getColor(holder.itemView.context, R.color.background_dark) else ContextCompat.getColor(holder.itemView.context, R.color.text_primary))
        val params = holder.text.layoutParams as FrameLayout.LayoutParams
        params.gravity = if (isUser) Gravity.END else Gravity.START
        holder.text.layoutParams = params
    }

    override fun getItemCount(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.message_text)
    }
}
