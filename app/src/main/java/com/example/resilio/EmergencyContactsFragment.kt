package com.example.resilio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton

class EmergencyContactsFragment : Fragment(R.layout.fragment_emergency_contacts) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Brgy San Jose
        setupRow(view.findViewById(R.id.row_brgy_1), getString(R.string.num_brgy_landline_1))
        setupRow(view.findViewById(R.id.row_brgy_2), getString(R.string.num_brgy_landline_2))
        setupRow(view.findViewById(R.id.row_brgy_3), getString(R.string.num_brgy_mobile_1))
        setupRow(view.findViewById(R.id.row_brgy_4), getString(R.string.num_brgy_mobile_2))

        // CDRRMO
        setupRow(view.findViewById(R.id.row_cdrrmo_1), getString(R.string.num_cdrrmo_1_val))
        setupRow(view.findViewById(R.id.row_cdrrmo_2), getString(R.string.num_cdrrmo_2_val))

        // Police
        setupRow(view.findViewById(R.id.row_police_1), getString(R.string.num_police_1_val))
        setupRow(view.findViewById(R.id.row_police_2), getString(R.string.num_police_2_val))

        // Fire
        setupRow(view.findViewById(R.id.row_fire_1), getString(R.string.num_fire_1_val))
        setupRow(view.findViewById(R.id.row_fire_2), getString(R.string.num_fire_2_val))

        // Medical
        setupRow(view.findViewById(R.id.row_medical_1), getString(R.string.num_medical_1_val))
        setupRow(view.findViewById(R.id.row_medical_2), getString(R.string.num_medical_2_val))

        view.findViewById<MaterialButton>(R.id.btn_back_home).setOnClickListener {
            // Fix: Use navigateUp() to go back to the caller dashboard instead of a hardcoded fragment
            if (!findNavController().navigateUp()) {
                // Fallback if no back stack (unlikely)
                activity?.onBackPressedDispatcher?.onBackPressed()
            }
        }
    }

    private fun setupRow(rowView: View, number: String) {
        rowView.findViewById<TextView>(R.id.text_number).text = number
        rowView.findViewById<MaterialButton>(R.id.btn_call).setOnClickListener {
            dialNumber(number)
        }
    }

    private fun dialNumber(number: String) {
        val cleanNumber = number.replace(Regex("[^0-9]"), "")
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$cleanNumber")
        }
        startActivity(intent)
    }
}
