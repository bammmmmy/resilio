package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView

class DisasterAwarenessFragment : Fragment(R.layout.fragment_disaster_awareness) {

    private lateinit var cardFlood: MaterialCardView
    private lateinit var cardTyphoon: MaterialCardView
    private lateinit var cardLandslide: MaterialCardView
    private lateinit var cardEarthquake: MaterialCardView

    private lateinit var detailLayout: LinearLayout
    private lateinit var tvDetailTitle: TextView
    private lateinit var tvDisasterDescription: TextView
    private lateinit var tvDisasterLongDesc: TextView
    private lateinit var tvSection1Title: TextView
    private lateinit var tvSection1Content: TextView
    private lateinit var tvSection2Title: TextView
    private lateinit var tvSection2Content: TextView
    private lateinit var tvSection3Title: TextView
    private lateinit var tvSection3Content: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cardFlood = view.findViewById(R.id.card_flood)
        cardTyphoon = view.findViewById(R.id.card_typhoon)
        cardLandslide = view.findViewById(R.id.card_landslide)
        cardEarthquake = view.findViewById(R.id.card_earthquake)

        detailLayout = view.findViewById(R.id.layout_disaster_details)
        tvDetailTitle = view.findViewById(R.id.tv_detail_title)
        tvDisasterDescription = view.findViewById(R.id.tv_disaster_description)
        tvDisasterLongDesc = view.findViewById(R.id.tv_disaster_long_desc)
        tvSection1Title = view.findViewById(R.id.tv_section1_title)
        tvSection1Content = view.findViewById(R.id.tv_section1_content)
        tvSection2Title = view.findViewById(R.id.tv_section2_title)
        tvSection2Content = view.findViewById(R.id.tv_section2_content)
        tvSection3Title = view.findViewById(R.id.tv_section3_title)
        tvSection3Content = view.findViewById(R.id.tv_section3_content)

        cardFlood.setOnClickListener {
            showDisasterDetail("flood")
        }

        cardTyphoon.setOnClickListener {
            showDisasterDetail("typhoon")
        }

        cardLandslide.setOnClickListener {
            showDisasterDetail("landslide")
        }

        cardEarthquake.setOnClickListener {
            showDisasterDetail("earthquake")
        }
    }

    private fun showDisasterDetail(type: String) {
        detailLayout.visibility = View.VISIBLE
        resetCardHighlights()

        when (type) {
            "flood" -> {
                highlightCard(cardFlood)
                tvDetailTitle.text = "🌊 FLOOD"
                tvDisasterDescription.text = getString(R.string.what_is_flood)
                tvDisasterLongDesc.text = getString(R.string.flood_long_desc)
                
                tvSection1Title.text = "BEFORE A FLOOD"
                tvSection1Content.text = getString(R.string.flood_before_bullets)
                
                tvSection2Title.text = "DURING A FLOOD"
                tvSection2Content.text = getString(R.string.flood_during_bullets)
                
                tvSection3Title.text = "AFTER A FLOOD"
                tvSection3Content.text = getString(R.string.flood_after_bullets)
            }
            "typhoon" -> {
                highlightCard(cardTyphoon)
                tvDetailTitle.text = "🌀 TYPHOON"
                tvDisasterDescription.text = getString(R.string.what_is_typhoon)
                tvDisasterLongDesc.text = getString(R.string.typhoon_long_desc)
                
                tvSection1Title.text = "BEFORE A TYPHOON"
                tvSection1Content.text = getString(R.string.typhoon_before_bullets)
                
                tvSection2Title.text = "DURING A TYPHOON"
                tvSection2Content.text = getString(R.string.typhoon_during_bullets)
                
                tvSection3Title.text = "AFTER A TYPHOON"
                tvSection3Content.text = getString(R.string.typhoon_after_bullets)
            }
            "landslide" -> {
                highlightCard(cardLandslide)
                tvDetailTitle.text = "▲ LANDSLIDE"
                tvDisasterDescription.text = getString(R.string.what_is_landslide)
                tvDisasterLongDesc.text = getString(R.string.landslide_long_desc)
                
                tvSection1Title.text = "BEFORE A LANDSLIDE"
                tvSection1Content.text = getString(R.string.landslide_before_bullets)
                
                tvSection2Title.text = "DURING A LANDSLIDE"
                tvSection2Content.text = getString(R.string.landslide_during_bullets)
                
                tvSection3Title.text = "AFTER A LANDSLIDE"
                tvSection3Content.text = getString(R.string.landslide_after_bullets)
            }
            "earthquake" -> {
                highlightCard(cardEarthquake)
                tvDetailTitle.text = "🌎 EARTHQUAKE"
                tvDisasterDescription.text = getString(R.string.what_is_earthquake)
                tvDisasterLongDesc.text = getString(R.string.earthquake_long_desc)
                
                tvSection1Title.text = "BEFORE AN EARTHQUAKE"
                tvSection1Content.text = getString(R.string.earthquake_before_bullets)
                
                tvSection2Title.text = "DURING AN EARTHQUAKE"
                tvSection2Content.text = getString(R.string.earthquake_during_bullets)
                
                tvSection3Title.text = "AFTER AN EARTHQUAKE"
                tvSection3Content.text = getString(R.string.earthquake_after_bullets)
            }
        }
        
        // Smooth scroll to details
        detailLayout.post {
            var parent = detailLayout.parent
            while (parent != null && parent !is androidx.core.widget.NestedScrollView) {
                parent = parent.parent
            }
            if (parent is androidx.core.widget.NestedScrollView) {
                parent.smoothScrollTo(0, detailLayout.top)
            }
        }
    }

    private fun resetCardHighlights() {
        val cards = listOf(cardFlood, cardTyphoon, cardLandslide, cardEarthquake)
        for (card in cards) {
            card.setStrokeColor(ContextCompat.getColorStateList(requireContext(), R.color.divider_color))
            card.setCardBackgroundColor(ContextCompat.getColorStateList(requireContext(), R.color.surface))
            card.strokeWidth = 1
        }
    }

    private fun highlightCard(card: MaterialCardView) {
        card.setStrokeColor(ContextCompat.getColorStateList(requireContext(), R.color.gold_accent))
        card.setCardBackgroundColor("#FFFBF0".toColorInt()) // Very light gold/yellow
        card.strokeWidth = 4
    }
}
