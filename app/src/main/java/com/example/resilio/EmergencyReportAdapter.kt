package com.example.resilio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.app.Dialog
import android.graphics.Color
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.resilio.model.EmergencyReport
import com.example.resilio.model.ReportStatus
import com.example.resilio.util.TimeUtils
import com.google.android.material.button.MaterialButton

class EmergencyReportAdapter(
    private val reports: List<EmergencyReport>,
    private val isAdmin: Boolean = false,
    private val onUpdateStatus: ((String, ReportStatus) -> Unit)? = null,
    private val onViewOnMap: (Double, Double) -> Unit,
    private val onChat: (String) -> Unit
) : RecyclerView.Adapter<EmergencyReportAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tvType)
        val tvSender: TextView = view.findViewById(R.id.tvSender)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val ivPhoto: ImageView = view.findViewById(R.id.ivPhoto)
        val photoGallery: LinearLayout = view.findViewById(R.id.photoGallery)
        val btnArchive: MaterialButton = view.findViewById(R.id.btnArchive)
        val btnUnarchive: MaterialButton = view.findViewById(R.id.btnUnarchive)
        val btnViewMap: MaterialButton = view.findViewById(R.id.btnViewMap)
        val btnChat: MaterialButton = view.findViewById(R.id.btnChat)
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
        
        holder.tvTime.text = TimeUtils.formatToPhTime(report.safeTimestamp)

        holder.ivPhoto.visibility = View.GONE
        holder.photoGallery.removeAllViews()
        report.allImageUrls.forEach { imageUrl ->
            val imageView = ImageView(holder.itemView.context).apply {
                layoutParams = LinearLayout.LayoutParams(96.dp(holder.itemView.context), 96.dp(holder.itemView.context)).apply {
                    marginEnd = 8.dp(holder.itemView.context)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_bottom_sheet)
                setOnClickListener { showFullPhoto(holder.itemView.context, imageUrl) }
            }
            Glide.with(holder.itemView.context).load(imageUrl).placeholder(R.drawable.logog).into(imageView)
            holder.photoGallery.addView(imageView)
        }

        if (isAdmin) {
            setupAdminControls(holder, report)
        } else {
            setupResidentControls(holder, report)
        }

        holder.btnViewMap.setOnClickListener { onViewOnMap(report.latitude, report.longitude) }
        holder.btnChat.setOnClickListener { onChat(report.id) }
    }

    private fun setupAdminControls(holder: ViewHolder, report: EmergencyReport) {
        when (report.status) {
            ReportStatus.PENDING -> {
                holder.statusBadge.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.emergency_red))
                holder.btnArchive.visibility = View.VISIBLE
                holder.btnUnarchive.visibility = View.GONE
            }
            ReportStatus.RESPONDING -> {
                holder.statusBadge.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.warning_orange))
                holder.btnArchive.visibility = View.VISIBLE
                holder.btnUnarchive.visibility = View.GONE
            }
            ReportStatus.RESOLVED -> {
                holder.statusBadge.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.primary_green))
                holder.btnArchive.visibility = View.VISIBLE
                holder.btnUnarchive.visibility = View.GONE
            }
            ReportStatus.ARCHIVED -> {
                holder.statusBadge.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.text_secondary))
                holder.btnArchive.visibility = View.GONE
                holder.btnUnarchive.visibility = View.VISIBLE
            }
        }
        
        holder.btnArchive.setOnClickListener { onUpdateStatus?.invoke(report.id, ReportStatus.ARCHIVED) }
        holder.btnUnarchive.setOnClickListener { onUpdateStatus?.invoke(report.id, ReportStatus.RESOLVED) }
    }

    private fun setupResidentControls(holder: ViewHolder, report: EmergencyReport) {
        holder.btnArchive.visibility = View.GONE
        holder.btnUnarchive.visibility = View.GONE
        
        val color = when (report.status) {
            ReportStatus.PENDING -> R.color.emergency_red
            ReportStatus.RESPONDING -> R.color.warning_orange
            ReportStatus.RESOLVED -> R.color.primary_green
            ReportStatus.ARCHIVED -> R.color.text_secondary
        }
        holder.statusBadge.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, color))
    }

    override fun getItemCount() = reports.size

    private fun showFullPhoto(context: android.content.Context, imageUrl: String) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val imageView = ImageView(context).apply {
            setBackgroundColor(Color.BLACK)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener { dialog.dismiss() }
        }
        dialog.setContentView(imageView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        Glide.with(context).load(imageUrl).into(imageView)
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun Int.dp(context: android.content.Context): Int = (this * context.resources.displayMetrics.density).toInt()
}
