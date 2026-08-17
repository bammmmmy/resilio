package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton

class DisasterDetailFragment : Fragment(R.layout.fragment_disaster_detail) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val disasterType = arguments?.getString("disasterType") ?: ""
        
        val titleText = view.findViewById<TextView>(R.id.text_disaster_title)
        val beforeText = view.findViewById<TextView>(R.id.text_before_content)
        val duringText = view.findViewById<TextView>(R.id.text_during_content)
        val afterText = view.findViewById<TextView>(R.id.text_after_content)
        val centersText = view.findViewById<TextView>(R.id.text_centers_content)
        val highRiskText = view.findViewById<TextView>(R.id.text_high_risk_content)

        when (disasterType) {
            "flood" -> {
                titleText.text = getString(R.string.filter_flood)
                beforeText.text = getString(R.string.flood_before)
                duringText.text = getString(R.string.flood_during)
                afterText.text = getString(R.string.flood_after)
                centersText.text = getString(R.string.flood_centers)
                highRiskText.text = getString(R.string.flood_high_risk)
            }
            "typhoon" -> {
                titleText.text = getString(R.string.filter_typhoon)
                beforeText.text = getString(R.string.typhoon_before)
                duringText.text = getString(R.string.typhoon_during)
                afterText.text = getString(R.string.typhoon_after)
                centersText.text = getString(R.string.typhoon_centers)
                highRiskText.text = getString(R.string.typhoon_high_risk)
            }
            "landslide" -> {
                titleText.text = getString(R.string.filter_landslide)
                beforeText.text = getString(R.string.landslide_before)
                duringText.text = getString(R.string.landslide_during)
                afterText.text = getString(R.string.landslide_after)
                centersText.text = getString(R.string.landslide_centers)
                highRiskText.text = getString(R.string.landslide_high_risk)
            }
            "earthquake" -> {
                titleText.text = getString(R.string.filter_earthquake)
                beforeText.text = getString(R.string.earthquake_before)
                duringText.text = getString(R.string.earthquake_during)
                afterText.text = getString(R.string.earthquake_after)
                centersText.text = getString(R.string.earthquake_centers)
                highRiskText.text = getString(R.string.earthquake_high_risk)
            }
        }

        view.findViewById<MaterialButton>(R.id.btn_back_home).setOnClickListener {
            findNavController().navigateUp()
        }
    }
}
