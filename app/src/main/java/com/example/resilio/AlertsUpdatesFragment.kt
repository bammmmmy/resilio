package com.example.resilio

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.resilio.model.EvacuationArea
import com.example.resilio.model.UserRole
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore

import java.util.Locale

class AlertsUpdatesFragment : Fragment(R.layout.fragment_alerts_updates) {

    private lateinit var evacuationAreaContainer: LinearLayout
    private lateinit var noAreaText: TextView
    private lateinit var addButton: MaterialButton
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val firestore = FirebaseFirestore.getInstance()

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            findNearestEvacuationCenter()
        } else {
            Toast.makeText(requireContext(), "Location permission is required to find the nearest center.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        evacuationAreaContainer = view.findViewById(R.id.evacuation_area_container)
        noAreaText = view.findViewById(R.id.tv_no_evacuation_areas)

        view.findViewById<MaterialButton>(R.id.btn_near_me).setOnClickListener {
            checkLocationPermissions()
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

    private fun checkLocationPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                findNearestEvacuationCenter()
            }
            else -> {
                requestLocationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun findNearestEvacuationCenter() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        Toast.makeText(requireContext(), "Finding nearest center...", Toast.LENGTH_SHORT).show()

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                fetchAreasAndFindNearest(location)
            } else {
                Toast.makeText(requireContext(), "Unable to get your current location. Make sure GPS is on.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Failed to get location.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchAreasAndFindNearest(userLocation: Location) {
        firestore.collection("evacuationAreas")
            .get()
            .addOnSuccessListener { snapshot ->
                val areas = snapshot.mapNotNull { document ->
                    document.toObject(EvacuationArea::class.java).copy(id = document.id)
                }
                
                if (areas.isEmpty()) {
                    Toast.makeText(requireContext(), "No evacuation centers available.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                var nearestArea: EvacuationArea? = null
                var minDistance = Float.MAX_VALUE

                for (area in areas) {
                    val results = FloatArray(1)
                    Location.distanceBetween(
                        userLocation.latitude, userLocation.longitude,
                        area.latitude, area.longitude,
                        results
                    )
                    val distance = results[0]
                    if (distance < minDistance) {
                        minDistance = distance
                        nearestArea = area
                    }
                }

                nearestArea?.let {
                    Toast.makeText(requireContext(), "Found: ${it.name} (${String.format(Locale.getDefault(), "%.1f", minDistance / 1000)} km away)", Toast.LENGTH_LONG).show()
                    openOnVrMap(it, showRoute = true)
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Unable to load evacuation areas.", Toast.LENGTH_SHORT).show()
            }
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
            val directionsButton = itemView.findViewById<MaterialButton>(R.id.btn_get_directions)
            val deleteButton = itemView.findViewById<MaterialButton>(R.id.btn_delete_evacuation_area)

            viewMapButton.setOnClickListener { openOnVrMap(area) }
            directionsButton.setOnClickListener { 
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Get Directions")
                    .setMessage("Do you want to see the walking route to ${area.name}?")
                    .setNegativeButton("No", null)
                    .setPositiveButton("Yes") { _, _ ->
                        openOnVrMap(area, showRoute = true)
                    }
                    .show()
            }

            if (canManageEvacuationAreas()) {
                deleteButton.visibility = View.VISIBLE
                deleteButton.setOnClickListener { confirmDeleteArea(area) }
            } else {
                deleteButton.visibility = View.GONE
            }

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

    private fun openOnVrMap(area: EvacuationArea, showRoute: Boolean = false) {
        if (area.latitude == 0.0 && area.longitude == 0.0) {
            Toast.makeText(requireContext(), R.string.evacuation_area_no_location, Toast.LENGTH_SHORT).show()
            return
        }

        val args = Bundle().apply {
            putFloat("focusLatitude", area.latitude.toFloat())
            putFloat("focusLongitude", area.longitude.toFloat())
            putBoolean("showRoute", showRoute)
        }
        findNavController().navigate(R.id.action_alertsUpdatesFragment_to_evacuationMapFragment, args)
    }
}
