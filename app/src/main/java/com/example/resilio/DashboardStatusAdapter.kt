package com.example.resilio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class DashboardStatusAdapter(
    private val onWeatherBind: (View) -> Unit,
    private val onLandslideBind: (View) -> Unit,
    private val onEarthquakeBind: (View) -> Unit
) : RecyclerView.Adapter<DashboardStatusAdapter.ViewHolder>() {
/**/
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemViewType(position: Int): Int = position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when (viewType) {
            0 -> R.layout.include_card_weather
            1 -> R.layout.include_card_landslide
            else -> R.layout.include_card_earthquake
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (position) {
            0 -> onWeatherBind(holder.itemView)
            1 -> onLandslideBind(holder.itemView)
            2 -> onEarthquakeBind(holder.itemView)
        }
    }

    override fun getItemCount(): Int = 3
}
