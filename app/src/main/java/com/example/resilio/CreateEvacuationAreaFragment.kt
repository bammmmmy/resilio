package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class CreateEvacuationAreaFragment : Fragment(R.layout.fragment_create_evacuation_area) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nameInput = view.findViewById<EditText>(R.id.input_evacuation_name)
        val addressInput = view.findViewById<EditText>(R.id.input_evacuation_address)
        val setMapButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_set_in_vr_map)

        setMapButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val address = addressInput.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter evacuation area name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (address.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter evacuation area address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val args = Bundle().apply {
                putBoolean("createMode", true)
                putString("areaName", name)
                putString("areaAddress", address)
            }

            findNavController().navigate(R.id.action_createEvacuationAreaFragment_to_evacuationMapFragment, args)
        }
    }
}
