package com.example.resilio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.resilio.model.HazardLocation
import com.example.resilio.model.UserRole
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore

class DisasterAwarenessFragment : Fragment(R.layout.fragment_disaster_awareness) {

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var addHazardButton: MaterialButton
    private lateinit var hazardContainer: LinearLayout
    private lateinit var noHazardsText: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        addHazardButton = view.findViewById(R.id.btn_add_hazard_location)
        hazardContainer = view.findViewById(R.id.hazard_location_container)
        noHazardsText = view.findViewById(R.id.tv_no_hazard_locations)

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

        addHazardButton.setOnClickListener {
            findNavController().navigate(R.id.action_disasterAwarenessFragment_to_createHazardLocationFragment)
        }

        updateHazardControlsForRole()
        fetchHazardLocations()
    }

    override fun onResume() {
        super.onResume()
        updateHazardControlsForRole()
        fetchHazardLocations()
    }

    private fun canManageHazardLocations(): Boolean {
        val currentRole = (activity as? MainActivity)?.getCurrentUserRole()
        return currentRole == UserRole.BDRRMO || currentRole == UserRole.CHAIRMAN
    }

    private fun updateHazardControlsForRole() {
        addHazardButton.visibility = if (canManageHazardLocations()) View.VISIBLE else View.GONE
    }

    private fun fetchHazardLocations() {
        firestore.collection("hazardLocations")
            .get()
            .addOnSuccessListener { snapshot ->
                val hazards = snapshot.documents.mapNotNull { document ->
                    document.toObject(HazardLocation::class.java)?.copy(id = document.id)
                }
                updateHazardViews(hazards)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), R.string.hazard_location_load_failed, Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateHazardViews(hazards: List<HazardLocation>) {
        val canManage = canManageHazardLocations()
        hazardContainer.removeAllViews()
        noHazardsText.visibility = if (hazards.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(requireContext())
        hazards.forEach { hazard ->
            val itemView = inflater.inflate(R.layout.item_hazard_location, hazardContainer, false)
            itemView.findViewById<TextView>(R.id.tv_hazard_type).text = hazardTypeLabel(hazard.hazardType)
            val descriptionView = itemView.findViewById<TextView>(R.id.tv_hazard_description)
            val description = hazard.description.trim()
            if (description.isNotEmpty()) {
                descriptionView.text = description
                descriptionView.visibility = View.VISIBLE
            } else {
                descriptionView.visibility = View.GONE
            }
            itemView.findViewById<TextView>(R.id.tv_hazard_address).text = hazard.address

            itemView.findViewById<MaterialButton>(R.id.btn_view_hazard_on_map).setOnClickListener {
                openOnVrMap(hazard)
            }

            val deleteButton = itemView.findViewById<MaterialButton>(R.id.btn_delete_hazard_location)
            if (canManage) {
                deleteButton.visibility = View.VISIBLE
                deleteButton.setOnClickListener {
                    confirmDeleteHazard(hazard)
                }
            } else {
                deleteButton.visibility = View.GONE
            }

            hazardContainer.addView(itemView)
        }
    }

    private fun hazardTypeLabel(key: String): String = when (key.lowercase()) {
        "flood" -> getString(R.string.filter_flood)
        "typhoon" -> getString(R.string.filter_typhoon)
        "landslide" -> getString(R.string.filter_landslide)
        "earthquake" -> getString(R.string.filter_earthquake)
        else -> key.replaceFirstChar { it.uppercase() }
    }

    private fun confirmDeleteHazard(hazard: HazardLocation) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_hazard_location_title)
            .setMessage(
                getString(
                    R.string.delete_hazard_location_message,
                    hazardTypeLabel(hazard.hazardType),
                    hazard.address,
                ),
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_hazard_location) { _, _ ->
                deleteHazardLocation(hazard)
            }
            .show()
    }

    private fun deleteHazardLocation(hazard: HazardLocation) {
        val id = hazard.id
        if (id.isEmpty()) {
            Toast.makeText(requireContext(), R.string.hazard_location_delete_failed, Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("hazardLocations")
            .document(id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), R.string.hazard_location_deleted, Toast.LENGTH_SHORT).show()
                fetchHazardLocations()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), R.string.hazard_location_delete_failed, Toast.LENGTH_SHORT).show()
            }
    }

    private fun openOnVrMap(hazard: HazardLocation) {
        if (hazard.latitude == 0.0 && hazard.longitude == 0.0) {
            Toast.makeText(requireContext(), R.string.hazard_location_no_location, Toast.LENGTH_SHORT).show()
            return
        }

        val args = Bundle().apply {
            putFloat("focusLatitude", hazard.latitude.toFloat())
            putFloat("focusLongitude", hazard.longitude.toFloat())
        }
        findNavController().navigate(R.id.action_disasterAwarenessFragment_to_evacuationMapFragment, args)
    }

    private fun navigateToDetail(type: String) {
        val bundle = Bundle().apply {
            putString("disasterType", type)
        }
        findNavController().navigate(R.id.action_disasterAwarenessFragment_to_disasterDetailFragment, bundle)
    }
}
