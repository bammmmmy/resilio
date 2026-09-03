package com.example.resilio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.resilio.model.EmergencyReport
import com.example.resilio.model.ReportStatus
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Locale

class EmergencyReportAdapter(
    private val reports: List<EmergencyReport>,
    private val onUpdateStatus: (String, ReportStatus) -> Unit,
    private val onViewOnMap: (Double, Double) -> Unit
) : RecyclerView.Adapter<EmergencyReportAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tvType)
        val tvSender: TextView = view.findViewById(R.id.tvSender)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val ivPhoto: ImageView = view.findViewById(R.id.ivPhoto)
        val btnRespond: MaterialButton = view.findViewById(R.id.btnRespond)
        val btnResolve: MaterialButton = view.findViewById(R.id.btnResolve)
        val btnViewMap: MaterialButton = view.findViewById(R.id.btnViewMap)
        val statusBadge: View = view.findViewById(R.id.statusBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_emergency_report, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val report = reports[position]
        
        holder.tvType.text = report.type.uppercase()
        holder.tvSender.text = "From: ${report.senderName}"
        holder.tvDescription.text = report.description
        
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        holder.tvTime.text = sdf.format(report.timestamp.toDate())

        Glide.with(holder.itemView.context)
            .load(report.imageUrl)
            .placeholder(R.drawable.logog)
            .into(holder.ivPhoto)

        when (report.status) {
            ReportStatus.PENDING -> {
                holder.statusBadge.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.emergency_red))
                holder.btnRespond.visibility = View.VISIBLE
                holder.btnResolve.visibility = View.GONE
            }
            ReportStatus.RESPONDING -> {
                holder.statusBadge.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.warning_orange))
                holder.btnRespond.visibility = View.GONE
                holder.btnResolve.visibility = View.VISIBLE
            }
            ReportStatus.RESOLVED -> {
                holder.statusBadge.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.primary_green))
                holder.btnRespond.visibility = View.GONE
                holder.btnResolve.visibility = View.GONE
            }
        }

        holder.btnRespond.setOnClickListener { onUpdateStatus(report.id, ReportStatus.RESPONDING) }
        holder.btnResolve.setOnClickListener { onUpdateStatus(report.id, ReportStatus.RESOLVED) }
        holder.btnViewMap.setOnClickListener { onViewOnMap(report.latitude, report.longitude) }
    }

    override fun getItemCount() = reports.size
}
