package com.example.resilio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.resilio.model.EmergencyAlert
import java.text.SimpleDateFormat
import java.util.Locale

class EmergencyAlertApprovalAdapter(
    private val alerts: List<EmergencyAlert>,
    private val onApprove: (String) -> Unit,
    private val onReject: (String) -> Unit
) : RecyclerView.Adapter<EmergencyAlertApprovalAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvType: TextView = view.findViewById(R.id.tvType)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val btnApprove: View = view.findViewById(R.id.btnApprove)
        val btnReject: View = view.findViewById(R.id.btnReject)
        val layoutActions: View = view.findViewById(R.id.layoutActions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_announcement, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alert = alerts[position]
        holder.tvTitle.text = alert.title
        holder.tvType.text = alert.type.name
        holder.tvContent.text = alert.safeContent
        
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        holder.tvTimestamp.text = sdf.format(alert.safeTimestamp.toDate())

        holder.layoutActions.visibility = View.VISIBLE
        holder.btnApprove.setOnClickListener { onApprove(alert.id) }
        holder.btnReject.setOnClickListener { onReject(alert.id) }
    }

    override fun getItemCount() = alerts.size
}
