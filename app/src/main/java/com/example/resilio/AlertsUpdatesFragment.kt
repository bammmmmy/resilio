package com.example.resilio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.resilio.model.EvacuationArea
import com.example.resilio.model.UserRole
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore

class AlertsUpdatesFragment : Fragment(R.layout.fragment_alerts_updates) {

    private lateinit var evacuationAreaContainer: LinearLayout
    private lateinit var noAreaText: TextView
    private lateinit var addButton: MaterialButton
    private val firestore = FirebaseFirestore.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        evacuationAreaContainer = view.findViewById(R.id.evacuation_area_container)
        noAreaText = view.findViewById(R.id.tv_no_evacuation_areas)

        view.findViewById<MaterialButton>(R.id.btn_near_me).setOnClickListener {
            Toast.makeText(requireContext(), "Finding nearest center in San Jose...", Toast.LENGTH_SHORT).show()
        }

        addButton = view.findViewById(R.id.btn_add_evacuation_area)
        updateAddButtonVisibility()

        addButton.setOnClickListener {
            findNavController().navigate(R.id.action_alertsUpdatesFragment_to_createEvacuationAreaFragment)
        }

        fetchEvacuationAreas()
    }

    override fun onResume() {
        super.onResume()
        updateAddButtonVisibility()
        fetchEvacuationAreas()
    }

    private fun updateAddButtonVisibility() {
        addButton.visibility = if (canManageEvacuationAreas()) View.VISIBLE else View.GONE
    }

    private fun canManageEvacuationAreas(): Boolean {
        val currentRole = (activity as? MainActivity)?.getCurrentUserRole()
        return currentRole == UserRole.BDRRMO || currentRole == UserRole.CHAIRMAN
    }

    private fun fetchEvacuationAreas() {
        firestore.collection("evacuationAreas")
            .get()
            .addOnSuccessListener { snapshot ->
                val areas = snapshot.mapNotNull { document ->
                    document.toObject(EvacuationArea::class.java).copy(id = document.id)
                }
                updateEvacuationAreaViews(areas)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Unable to load evacuation areas.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateEvacuationAreaViews(areas: List<EvacuationArea>) {
        evacuationAreaContainer.removeAllViews()

        if (areas.isEmpty()) {
            noAreaText.visibility = View.VISIBLE
            return
        }

        noAreaText.visibility = View.GONE

        val inflater = LayoutInflater.from(requireContext())
        areas.forEach { area ->
            val itemView = inflater.inflate(R.layout.item_evacuation_area, evacuationAreaContainer, false)
            itemView.findViewById<TextView>(R.id.tv_evacuation_name).text = area.name
            itemView.findViewById<TextView>(R.id.tv_evacuation_address).text = area.address
            val viewMapButton = itemView.findViewById<MaterialButton>(R.id.btn_navigate_area)
            viewMapButton.setOnClickListener { openOnVrMap(area) }

            val deleteButton = itemView.findViewById<MaterialButton>(R.id.btn_delete_evacuation_area)
            val viewMapParams = viewMapButton.layoutParams as LinearLayout.LayoutParams
            if (canManageEvacuationAreas()) {
                deleteButton.visibility = View.VISIBLE
                deleteButton.setOnClickListener { confirmDeleteArea(area) }
                viewMapParams.width = 0
                viewMapParams.weight = 1f
            } else {
                deleteButton.visibility = View.GONE
                viewMapParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                viewMapParams.weight = 0f
            }
            viewMapButton.layoutParams = viewMapParams

            evacuationAreaContainer.addView(itemView)
        }
    }

    private fun confirmDeleteArea(area: EvacuationArea) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_evacuation_area_title)
            .setMessage(getString(R.string.delete_evacuation_area_message, area.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_evacuation_area) { _, _ ->
                deleteEvacuationArea(area)
            }
            .show()
    }

    private fun deleteEvacuationArea(area: EvacuationArea) {
        if (area.id.isEmpty()) {
            Toast.makeText(requireContext(), R.string.evacuation_area_delete_failed, Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("evacuationAreas")
            .document(area.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), R.string.evacuation_area_deleted, Toast.LENGTH_SHORT).show()
                fetchEvacuationAreas()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), R.string.evacuation_area_delete_failed, Toast.LENGTH_SHORT).show()
            }
    }

    private fun openOnVrMap(area: EvacuationArea) {
        if (area.latitude == 0.0 && area.longitude == 0.0) {
            Toast.makeText(requireContext(), R.string.evacuation_area_no_location, Toast.LENGTH_SHORT).show()
            return
        }

        val args = Bundle().apply {
            putFloat("focusLatitude", area.latitude.toFloat())
            putFloat("focusLongitude", area.longitude.toFloat())
        }
        findNavController().navigate(R.id.action_alertsUpdatesFragment_to_evacuationMapFragment, args)
    }
}
