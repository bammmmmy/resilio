package com.example.resilio

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView

class DisasterAwarenessFragment : Fragment(R.layout.fragment_disaster_awareness) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialCardView>(R.id.card_flood).setOnClickListener {
            navigateToDetail("flood")
        }

        view.findViewById<MaterialCardView>(R.id.card_typhoon).setOnClickListener {
            navigateToDetail("typhoon")
        }

        view.findViewById<MaterialCardView>(R.id.card_landslide).setOnClickListener {
            navigateToDetail("landslide")
        }

        view.findViewById<MaterialCardView>(R.id.card_earthquake).setOnClickListener {
            navigateToDetail("earthquake")
        }
    }

    private fun navigateToDetail(type: String) {
        val bundle = Bundle().apply {
            putString("disasterType", type)
        }
        findNavController().navigate(R.id.action_disasterAwarenessFragment_to_disasterDetailFragment, bundle)
    }
}
