package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class CreateHazardLocationFragment : Fragment(R.layout.fragment_create_hazard_location) {

    private val hazardTypeKeys = listOf("flood", "typhoon", "landslide", "earthquake")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val typeInput = view.findViewById<AutoCompleteTextView>(R.id.input_hazard_type)
        val descriptionInput = view.findViewById<TextInputEditText>(R.id.input_hazard_description)
        val addressInput = view.findViewById<TextInputEditText>(R.id.input_hazard_address)
        val setMapButton = view.findViewById<MaterialButton>(R.id.btn_set_hazard_in_vr_map)

        val labels = hazardTypeKeys.map { key -> hazardTypeLabel(key) }
        typeInput.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels),
        )
        if (labels.isNotEmpty()) {
            typeInput.setText(labels.first(), false)
        }

        setMapButton.setOnClickListener {
            val selectedLabel = typeInput.text.toString().trim()
            val hazardType = hazardTypeKeys.getOrElse(labels.indexOf(selectedLabel)) { hazardTypeKeys.first() }
            val description = descriptionInput.text.toString().trim()
            val address = addressInput.text.toString().trim()

            if (selectedLabel.isEmpty()) {
                Toast.makeText(requireContext(), R.string.hazard_type_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (description.isEmpty()) {
                Toast.makeText(requireContext(), R.string.hazard_description_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (address.isEmpty()) {
                Toast.makeText(requireContext(), R.string.hazard_address_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val args = Bundle().apply {
                putBoolean("hazardCreateMode", true)
                putString("hazardType", hazardType)
                putString("hazardDescription", description)
                putString("hazardAddress", address)
            }
            findNavController().navigate(
                R.id.action_createHazardLocationFragment_to_evacuationMapFragment,
                args,
            )
        }
    }

    private fun hazardTypeLabel(key: String): String = when (key) {
        "flood" -> getString(R.string.filter_flood)
        "typhoon" -> getString(R.string.filter_typhoon)
        "landslide" -> getString(R.string.filter_landslide)
        "earthquake" -> getString(R.string.filter_earthquake)
        else -> key.replaceFirstChar { it.uppercase() }
    }
}
