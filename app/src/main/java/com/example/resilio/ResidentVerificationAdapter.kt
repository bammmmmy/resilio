package com.example.resilio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.resilio.model.User

class ResidentVerificationAdapter(
    private val residents: List<User>,
    private val onApprove: (String) -> Unit,
    private val onReject: (String) -> Unit
) : RecyclerView.Adapter<ResidentVerificationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFullName: TextView = view.findViewById(R.id.tvFullName)
        val tvDetails: TextView = view.findViewById(R.id.tvDetails)
        val tvAddress: TextView = view.findViewById(R.id.tvAddress)
        val ivIdFront: ImageView = view.findViewById(R.id.ivIdFront)
        val ivIdBack: ImageView = view.findViewById(R.id.ivIdBack)
        val btnApprove: View = view.findViewById(R.id.btnApprove)
        val btnReject: View = view.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_resident_verification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = residents[position]
        holder.tvFullName.text = user.fullName
        holder.tvAddress.text = "Address: ${user.address}"
        holder.tvDetails.text = "DOB: ${user.birthday} | Sex: ${user.sex}"
        
        Glide.with(holder.itemView.context)
            .load(user.idImageUrl)
            .placeholder(R.drawable.logog)
            .into(holder.ivIdFront)

        Glide.with(holder.itemView.context)
            .load(user.idBackImageUrl)
            .placeholder(R.drawable.logog)
            .into(holder.ivIdBack)

        holder.btnApprove.setOnClickListener { onApprove(user.uid) }
        holder.btnReject.setOnClickListener { onReject(user.uid) }
    }

    override fun getItemCount() = residents.size
}
