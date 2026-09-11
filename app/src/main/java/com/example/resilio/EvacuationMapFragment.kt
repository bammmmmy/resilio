package com.example.resilio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.location.Location
import android.net.Uri
import android.content.Intent
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.core.graphics.toColorInt
import com.example.resilio.model.EvacuationArea
import com.example.resilio.model.HazardLocation
import com.example.resilio.util.PolylineDecoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.StreetViewPanorama
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.SupportStreetViewPanoramaFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

class EvacuationMapFragment : Fragment(R.layout.fragment_evacuation_map), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private lateinit var streetOverlay: View
    private lateinit var streetViewPin: ImageView
    private lateinit var streetViewConfirmButton: FloatingActionButton
    private lateinit var confirmEvacuationButton: MaterialButton
    private lateinit var streetViewHintOverlay: View
    private lateinit var fabStreet: FloatingActionButton
    private lateinit var fabClose: FloatingActionButton
    private lateinit var fabAi: FloatingActionButton
    private lateinit var btnGetDirectionsOverlay: MaterialButton
    
    private lateinit var cardRadiusControl: View
    private lateinit var tvRadiusLabel: TextView
    private lateinit var sliderRadius: Slider
    
    private var streetViewSelectionMode = false
    private var evacuationPinMode = false
    private var evacuationCreateMode = false
    private var hazardCreateMode = false
    private var areaName = ""
    private var areaAddress = ""
    private var hazardType = ""
    private var hazardDescription = ""
    private var hazardAddress = ""
    private var focusLatitude = 0.0
    private var focusLongitude = 0.0
    private var showRoute = false
    private var currentPolyline: Polyline? = null
    private val httpClient = OkHttpClient()
    private var pinTouchOffsetX = 0f
    private var pinTouchOffsetY = 0f
    private var evacuationMarkerIcon: BitmapDescriptor? = null
    private var hazardMarkerIcon: BitmapDescriptor? = null
    private val evacuationMarkers = mutableListOf<Marker>()
    private val hazardMarkers = mutableListOf<Marker>()
    private var selectedEvacuationArea: EvacuationArea? = null
    private val hazardCircles = mutableListOf<Circle>()
    private var previewHazardCircle: Circle? = null

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var pendingDestination: LatLng? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            googleMap?.let {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        it.isMyLocationEnabled = true
                    } catch (e: SecurityException) {
                        Log.e("EvacuationMap", "SecurityException setting isMyLocationEnabled", e)
                    }
                }
            }
            pendingDestination?.let { calculateAndDrawRoute(it) }
            pendingDestination = null
        } else {
            Toast.makeText(requireContext(), "Location permission is required for directions", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        streetOverlay = view.findViewById(R.id.street_view_container)
        streetViewPin = view.findViewById(R.id.streetview_pin)
        streetViewConfirmButton = view.findViewById(R.id.fab_confirm_street_view)
        confirmEvacuationButton = view.findViewById(R.id.btn_confirm_evacuation_area)
        streetViewHintOverlay = view.findViewById(R.id.streetview_hint_overlay)
        fabStreet = view.findViewById(R.id.fab_street_view)
        fabClose = view.findViewById(R.id.fab_close_street_view)
        fabAi = view.findViewById(R.id.fab_ai_chat)
        btnGetDirectionsOverlay = view.findViewById(R.id.btn_get_directions_overlay)
        
        cardRadiusControl = view.findViewById(R.id.card_radius_control)
        tvRadiusLabel = view.findViewById<TextView>(R.id.tv_radius_label)
        sliderRadius = view.findViewById(R.id.slider_radius)

        sliderRadius.addOnChangeListener { _, value, _ ->
            tvRadiusLabel.text = getString(R.string.hazard_area_radius, value.toInt())
            updatePreviewCircle()
        }

        super.onViewCreated(view, savedInstanceState)

        evacuationCreateMode = arguments?.getBoolean("createMode", false) ?: false
        hazardCreateMode = arguments?.getBoolean("hazardCreateMode", false) ?: false
        
        if (hazardCreateMode) {
            cardRadiusControl.visibility = View.VISIBLE
        }
        areaName = arguments?.getString("areaName").orEmpty()
        areaAddress = arguments?.getString("areaAddress").orEmpty()
        hazardType = arguments?.getString("hazardType").orEmpty()
        hazardDescription = arguments?.getString("hazardDescription").orEmpty()
        hazardAddress = arguments?.getString("hazardAddress").orEmpty()
        focusLatitude = arguments?.getFloat("focusLatitude", 0f)?.toDouble() ?: 0.0
        focusLongitude = arguments?.getFloat("focusLongitude", 0f)?.toDouble() ?: 0.0
        showRoute = arguments?.getBoolean("showRoute", false) ?: false

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        if (evacuationCreateMode || hazardCreateMode) {
            enterPinPlacementMode()
            confirmEvacuationButton.text = if (hazardCreateMode) {
                getString(R.string.confirm_hazard_location)
            } else {
                getString(R.string.confirm_evacuation_area)
            }
            streetViewPin.contentDescription = getString(
                if (hazardCreateMode) R.string.hazard_pin_content_description
                else R.string.evacuation_pin_content_description,
            )
        }

        streetViewPin.setOnTouchListener { v, event ->
            if (!isPinPlacementActive()) return@setOnTouchListener false
            val parent = v.parent as? View ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pinTouchOffsetX = event.x
                    pinTouchOffsetY = event.y
                    v.performClick()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val location = IntArray(2)
                    parent.getLocationOnScreen(location)
                    val newX = event.rawX - pinTouchOffsetX - location[0]
                    val newY = event.rawY - pinTouchOffsetY - location[1]
                    v.x = newX.coerceIn(0f, parent.width - v.width.toFloat())
                    v.y = newY.coerceIn(0f, parent.height - v.height.toFloat())
                    true
                }
                else -> false
            }
        }

        streetViewConfirmButton.setOnClickListener {
            val location = pinLocation()

            streetOverlay.visibility = View.VISIBLE
            streetViewSelectionMode = false
            streetViewConfirmButton.visibility = View.GONE
            streetViewPin.visibility = View.GONE
            fabStreet.visibility = View.GONE
            fabClose.visibility = View.VISIBLE
            streetViewHintOverlay.visibility = View.VISIBLE

            streetOverlay.post {
                val svFragment =
                    childFragmentManager.findFragmentById(R.id.street_view) as SupportStreetViewPanoramaFragment
                svFragment.getStreetViewPanoramaAsync { panorama ->
                    openStreetViewAt(panorama, location)
                }
            }
        }

        confirmEvacuationButton.setOnClickListener {
            val location = pinLocation()
            if (hazardCreateMode) {
                val result = Bundle().apply {
                    putDouble("lat", location.latitude)
                    putDouble("lng", location.longitude)
                    putDouble("radius", sliderRadius.value.toDouble())
                }
                setFragmentResult("hazard_location_request", result)
                findNavController().popBackStack()
            } else {
                saveEvacuationArea(location)
            }
        }

        btnGetDirectionsOverlay.setOnClickListener {
            selectedEvacuationArea?.let { area ->
                calculateAndDrawRoute(LatLng(area.latitude, area.longitude))
                btnGetDirectionsOverlay.visibility = View.GONE
            }
        }

        fabStreet.setOnClickListener {
            enterStreetViewSelectionMode()
        }

        fabClose.setOnClickListener {
            when {
                streetViewSelectionMode -> exitStreetViewSelectionMode()
                evacuationPinMode -> findNavController().navigateUp()
                else -> {
                    streetOverlay.visibility = View.GONE
                    streetViewHintOverlay.visibility = View.GONE
                    fabStreet.visibility = View.VISIBLE
                    fabClose.visibility = View.GONE
                }
            }
        }

        fabAi.setOnClickListener {
            findNavController().navigate(R.id.aiChatFragment)
        }
    }

    override fun onResume() {
        super.onResume()
        if (googleMap != null) {
            loadMapMarkers()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.mapType = GoogleMap.MAP_TYPE_NORMAL

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                map.isMyLocationEnabled = true
            } catch (e: SecurityException) {
                Log.e("EvacuationMap", "SecurityException setting isMyLocationEnabled", e)
            }
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        // Restrict map panning to the entire Antipolo City area
        val antipoloCityBounds = LatLngBounds(
            LatLng(14.4272, 121.01592), // Southwest
            LatLng(14.7472, 121.33592)  // Northeast
        )
        map.setLatLngBoundsForCameraTarget(antipoloCityBounds)
        map.setMinZoomPreference(11.0f)

        // 1. Focus on San Jose at a standard zoom level
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(SAN_JOSE_CENTER, 15.0f))

        // 2. Sync preview circle with the camera movement
        map.setOnCameraMoveListener {
            updatePreviewCircle()
        }
        map.setOnCameraIdleListener {
            updatePreviewCircle()
        }

        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMapToolbarEnabled = false
        }

        map.setOnMarkerClickListener { marker ->
            animateToMarker(marker.position)
            when (val tag = marker.tag) {
                is HazardLocation -> {
                    selectedEvacuationArea = EvacuationArea(
                        name = tag.hazardType.replaceFirstChar { it.uppercase() } + " Hazard",
                        latitude = tag.latitude,
                        longitude = tag.longitude
                    )
                    btnGetDirectionsOverlay.text = getString(R.string.get_directions_to, selectedEvacuationArea?.name)
                    btnGetDirectionsOverlay.visibility = View.VISIBLE
                    showHazardAiInfo(tag)
                    true
                }
                is EvacuationArea -> {
                    selectedEvacuationArea = tag
                    btnGetDirectionsOverlay.text = getString(R.string.get_directions_to, tag.name)
                    btnGetDirectionsOverlay.visibility = View.VISIBLE
                    marker.showInfoWindow()
                    true
                }
                else -> {
                    btnGetDirectionsOverlay.visibility = View.GONE
                    selectedEvacuationArea = null
                    marker.showInfoWindow()
                    true
                }
            }
        }

        map.setOnMapClickListener {
            btnGetDirectionsOverlay.visibility = View.GONE
            selectedEvacuationArea = null
            currentPolyline?.remove()
            currentPolyline = null
        }

        map.setOnInfoWindowClickListener { marker ->
            when (val tag = marker.tag) {
                is EvacuationArea -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Get Directions")
                        .setMessage("Do you want to see the walking route to ${tag.name}?")
                        .setNegativeButton("No") { _, _ -> openStreetViewForArea(tag) }
                        .setPositiveButton("Yes") { _, _ ->
                            calculateAndDrawRoute(LatLng(tag.latitude, tag.longitude))
                        }
                        .show()
                }
                is HazardLocation -> showHazardAiInfo(tag)
            }
        }

        if (evacuationPinMode) {
            confirmEvacuationButton.isEnabled = true
        }

        loadMapMarkers()
    }

    private fun updatePreviewCircle() {
        if (!hazardCreateMode) return
        val map = googleMap ?: return
        val center = pinLocation()
        val radius = sliderRadius.value.toDouble()
        val color = getHazardColor(hazardType)

        if (previewHazardCircle == null) {
            previewHazardCircle = map.addCircle(
                CircleOptions()
                    .center(center)
                    .radius(radius)
                    .strokeWidth(3f)
                    .strokeColor(color)
                    .fillColor(adjustAlpha(color, 0.25f))
            )
        } else {
            previewHazardCircle?.apply {
                this.center = center
                this.radius = radius
                this.strokeColor = color
                this.fillColor = adjustAlpha(color, 0.25f)
            }
        }
    }

    private fun getHazardColor(type: String): Int {
        return when (type.lowercase()) {
            "flood" -> Color.BLUE
            "typhoon" -> Color.RED
            "landslide" -> Color.parseColor("#8B4513") // Brown
            "earthquake" -> Color.parseColor("#FF8C00") // Orange
            else -> Color.YELLOW // General Announcement / General Alert
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Math.round(Color.alpha(color) * factor)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }

    private fun isPinPlacementActive(): Boolean =
        streetViewSelectionMode || evacuationPinMode

    private fun centerPinOnMap() {
        streetViewPin.post {
            val pin = streetViewPin
            val parent = pin.parent as? View ?: return@post
            pin.x = (parent.width - pin.width) / 2f
            pin.y = (parent.height - pin.height) / 2f
        }
    }

    private fun pinLocation(): LatLng {
        val map = googleMap ?: return SAN_JOSE_CENTER
        val pin = streetViewPin
        val pinCenterX = (pin.x + pin.width / 2).toInt()
        val pinCenterY = (pin.y + pin.height / 2).toInt()
        return map.projection.fromScreenLocation(Point(pinCenterX, pinCenterY))
    }

    private fun enterPinPlacementMode() {
        evacuationPinMode = true
        streetViewPin.visibility = View.VISIBLE
        confirmEvacuationButton.visibility = View.VISIBLE
        confirmEvacuationButton.isEnabled = googleMap != null
        fabClose.visibility = View.VISIBLE
        fabStreet.visibility = View.GONE
        streetViewConfirmButton.visibility = View.GONE
        fabAi.visibility = View.GONE
        centerPinOnMap()
    }

    private fun enterStreetViewSelectionMode() {
        if (evacuationCreateMode || hazardCreateMode) return
        streetViewSelectionMode = true
        streetViewPin.visibility = View.VISIBLE
        streetViewConfirmButton.visibility = View.VISIBLE
        fabClose.visibility = View.VISIBLE
        fabStreet.visibility = View.GONE
        streetViewHintOverlay.visibility = View.GONE
        centerPinOnMap()
    }

    private fun exitStreetViewSelectionMode() {
        streetViewSelectionMode = false
        streetViewPin.visibility = View.GONE
        streetViewConfirmButton.visibility = View.GONE
        fabClose.visibility = View.GONE
        fabStreet.visibility = View.VISIBLE
    }

    private fun loadMapMarkers() {
        loadEvacuationAreaMarkers()
        loadHazardLocationMarkers()
    }

    private fun loadEvacuationAreaMarkers() {
        val map = googleMap ?: return

        firestore.collection("evacuationAreas")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener
                clearEvacuationMarkers()

                val icon = getEvacuationMarkerIcon()

                snapshot.documents.forEach { document ->
                    val area = document.toObject(EvacuationArea::class.java)
                        ?.copy(id = document.id)
                        ?: return@forEach
                    if (!hasMapLocation(area.latitude, area.longitude)) return@forEach

                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(LatLng(area.latitude, area.longitude))
                            .title(area.name)
                            .snippet(area.address)
                            .icon(icon)
                    ) ?: return@forEach

                    marker.tag = area
                    evacuationMarkers.add(marker)
                }

                focusOnRequestedLocation()
            }
    }

    private fun loadHazardLocationMarkers() {
        val map = googleMap ?: return

        firestore.collection("hazardLocations")
            .whereEqualTo("active", true)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener
                clearHazardMarkers()
                clearHazardCircles()

                val icon = getHazardMarkerIcon()

                snapshot.documents.forEach { document ->
                    val hazard = document.toObject(HazardLocation::class.java)
                        ?.copy(id = document.id)
                        ?: return@forEach
                    if (!hasMapLocation(hazard.latitude, hazard.longitude)) return@forEach

                    val latLng = LatLng(hazard.latitude, hazard.longitude)
                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title(hazardMarkerTitle(hazard.hazardType))
                            .snippet(hazardMarkerSnippet(hazard))
                            .icon(icon)
                    ) ?: return@forEach

                    marker.tag = hazard
                    hazardMarkers.add(marker)

                    if (hazard.radius > 0) {
                        val color = getHazardColor(hazard.hazardType)
                        val circle = map.addCircle(
                            CircleOptions()
                                .center(latLng)
                                .radius(hazard.radius)
                                .strokeWidth(3f)
                                .strokeColor(color)
                                .fillColor(adjustAlpha(color, 0.25f))
                        )
                        hazardCircles.add(circle)
                    }
                }

                focusOnRequestedLocation()
            }
    }

    private fun hazardMarkerSnippet(hazard: HazardLocation): String {
        val description = hazard.description.trim()
        if (description.isNotEmpty()) {
            return if (description.length <= 80) description else "${description.take(77)}..."
        }
        return hazard.address
    }

    private fun showHazardAiInfo(hazard: HazardLocation) {
        val sheet = HazardAiInfoBottomSheetFragment.newInstance(
            hazardType = hazard.hazardType,
            description = hazard.description,
            address = hazard.address,
            latitude = hazard.latitude,
            longitude = hazard.longitude,
        )
        sheet.setOnStreetViewRequested { location ->
            openStreetViewAtLocation(location)
        }
        sheet.show(childFragmentManager, "hazard_ai_info")
    }

    private fun hazardMarkerTitle(hazardType: String): String {
        val label = when (hazardType.lowercase()) {
            "flood" -> getString(R.string.filter_flood)
            "typhoon" -> getString(R.string.filter_typhoon)
            "landslide" -> getString(R.string.filter_landslide)
            "earthquake" -> getString(R.string.filter_earthquake)
            else -> hazardType.replaceFirstChar { it.uppercase() }
        }
        return getString(R.string.hazard_marker_title, label)
    }

    private fun getEvacuationMarkerIcon(): BitmapDescriptor {
        evacuationMarkerIcon?.let { return it }
        if (!isAdded) return BitmapDescriptorFactory.defaultMarker()
        val descriptor = bitmapDescriptorFromVector(R.drawable.ic_evacuation_marker, MARKER_ICON_SIZE_DP)
        evacuationMarkerIcon = descriptor
        return descriptor
    }

    private fun getHazardMarkerIcon(): BitmapDescriptor {
        hazardMarkerIcon?.let { return it }
        if (!isAdded) return BitmapDescriptorFactory.defaultMarker()
        val descriptor = bitmapDescriptorFromVector(R.drawable.ic_hazard_marker, MARKER_ICON_SIZE_DP)
        hazardMarkerIcon = descriptor
        return descriptor
    }

    private fun bitmapDescriptorFromVector(vectorResId: Int, sizeDp: Int): BitmapDescriptor {
        val context = context ?: return BitmapDescriptorFactory.defaultMarker()
        val drawable = ContextCompat.getDrawable(context, vectorResId)
            ?: return BitmapDescriptorFactory.defaultMarker()
        val sizePx = (sizeDp * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        drawable.setBounds(0, 0, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun clearEvacuationMarkers() {
        evacuationMarkers.forEach { it.remove() }
        evacuationMarkers.clear()
    }

    private fun clearHazardMarkers() {
        hazardMarkers.forEach { it.remove() }
        hazardMarkers.clear()
    }

    private fun clearHazardCircles() {
        hazardCircles.forEach { it.remove() }
        hazardCircles.clear()
        previewHazardCircle?.remove()
        previewHazardCircle = null
    }

    private fun hasMapLocation(latitude: Double, longitude: Double): Boolean =
        latitude != 0.0 || longitude != 0.0

    private fun animateToMarker(target: LatLng) {
        val map = googleMap ?: return
        val cameraPosition = CameraPosition.Builder()
            .target(target)
            .zoom(18.5f) // Standard close zoom
            .tilt(0f)    // Flat top-down view
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private fun focusOnRequestedLocation() {
        val lat = focusLatitude
        val lng = focusLongitude
        if (lat == 0.0 && lng == 0.0) return

        val shouldDrawRoute = showRoute
        focusLatitude = 0.0
        focusLongitude = 0.0
        showRoute = false

        val target = LatLng(lat, lng)
        animateToMarker(target)

        if (shouldDrawRoute) {
            calculateAndDrawRoute(target)
        }

        evacuationMarkers.firstOrNull { marker ->
            val area = marker.tag as? EvacuationArea ?: return@firstOrNull false
            kotlin.math.abs(area.latitude - lat) < 1e-6 && kotlin.math.abs(area.longitude - lng) < 1e-6
        }?.showInfoWindow()

        hazardMarkers.firstOrNull { marker ->
            val hazard = marker.tag as? HazardLocation ?: return@firstOrNull false
            kotlin.math.abs(hazard.latitude - lat) < 1e-6 && kotlin.math.abs(hazard.longitude - lng) < 1e-6
        }?.showInfoWindow()
    }

    private fun calculateAndDrawRoute(destination: LatLng) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingDestination = destination
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val origin = LatLng(location.latitude, location.longitude)
                fetchRoute(origin, destination)
            } else {
                // Fallback: If internal routing fails due to null location, use External Intent
                openGoogleMapsApp(destination)
            }
        }.addOnFailureListener {
            openGoogleMapsApp(destination)
        }
    }

    private fun openGoogleMapsApp(destination: LatLng) {
        Toast.makeText(requireContext(), "Opening Google Maps for navigation...", Toast.LENGTH_SHORT).show()
        val gmmIntentUri = Uri.parse("google.navigation:q=${destination.latitude},${destination.longitude}&mode=w")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        
        try {
            startActivity(mapIntent)
        } catch (e: Exception) {
            // Fallback for browsers or if Maps is missing
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${destination.latitude},${destination.longitude}&travelmode=walking"))
            startActivity(webIntent)
        }
    }

    private fun fetchRoute(origin: LatLng, destination: LatLng) {
        val apiKey = try {
            val appInfo = requireContext().packageManager.getApplicationInfo(requireContext().packageName, PackageManager.GET_META_DATA)
            appInfo.metaData.getString("com.google.android.geo.API_KEY")
        } catch (e: Exception) {
            null
        } ?: ""

        Log.d("EvacuationMap", "Fetching route with API Key starting with: ${apiKey.take(5)}...")

        val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=${origin.latitude},${origin.longitude}" +
                "&destination=${destination.latitude},${destination.longitude}" +
                "&mode=walking" +
                "&key=$apiKey"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    val jsonData = response.body?.string() ?: return@use
                    val jsonObject = JSONObject(jsonData)
                    
                    val status = jsonObject.optString("status")
                    Log.d("EvacuationMap", "Directions API Status: $status")

                    if (status == "OK") {
                        val routes = jsonObject.getJSONArray("routes")
                        val route = routes.getJSONObject(0)
                        val legs = route.getJSONArray("legs")
                        val leg = legs.getJSONObject(0)
                        val distanceText = leg.getJSONObject("distance").getString("text")
                        val durationText = leg.getJSONObject("duration").getString("text")
                        
                        val overviewPolyline = route.getJSONObject("overview_polyline")
                        val encodedPoints = overviewPolyline.getString("points")
                        val points = PolylineDecoder.decode(encodedPoints)

                        withContext(Dispatchers.Main) {
                            drawPolyline(points)
                            Toast.makeText(
                                requireContext(),
                                "Estimated: $distanceText ($durationText walk)",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        val errorMsg = jsonObject.optString("error_message", "Unknown error")
                        Log.e("EvacuationMap", "Directions API Error: $errorMsg")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Routing error: $status", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("EvacuationMap", "Network error fetching route", e)
            }
        }
    }

    private fun drawPolyline(points: List<LatLng>) {
        currentPolyline?.remove()
        val options = PolylineOptions()
            .addAll(points)
            .width(12f)
            .color("#4285F4".toColorInt()) // Google Blue
            .geodesic(true)
            .clickable(false)
        
        currentPolyline = googleMap?.addPolyline(options)
        
        // Adjust camera to fit the whole route
        if (points.isNotEmpty()) {
            val boundsBuilder = LatLngBounds.Builder()
            for (point in points) {
                boundsBuilder.include(point)
            }
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 150))
        }
    }

    private fun openStreetViewForArea(area: EvacuationArea) {
        if (!hasMapLocation(area.latitude, area.longitude)) return
        openStreetViewAtLocation(LatLng(area.latitude, area.longitude))
    }

    private fun openStreetViewAtLocation(location: LatLng) {
        streetOverlay.visibility = View.VISIBLE
        streetViewSelectionMode = false
        streetViewConfirmButton.visibility = View.GONE
        streetViewPin.visibility = View.GONE
        fabStreet.visibility = View.GONE
        fabClose.visibility = View.VISIBLE
        streetViewHintOverlay.visibility = View.VISIBLE

        streetOverlay.post {
            val svFragment =
                childFragmentManager.findFragmentById(R.id.street_view) as SupportStreetViewPanoramaFragment
            svFragment.getStreetViewPanoramaAsync { panorama ->
                openStreetViewAt(panorama, location)
            }
        }
    }

    private fun saveEvacuationArea(location: LatLng) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(requireContext(), R.string.evacuation_area_save_failed, Toast.LENGTH_SHORT).show()
            return
        }

        confirmEvacuationButton.isEnabled = false

        val area = EvacuationArea(
            name = areaName,
            address = areaAddress,
            latitude = location.latitude,
            longitude = location.longitude,
            createdBy = uid
        )

        firestore.collection("evacuationAreas")
            .add(area)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                Toast.makeText(requireContext(), R.string.evacuation_area_saved, Toast.LENGTH_SHORT).show()
                findNavController().popBackStack(R.id.createEvacuationAreaFragment, true)
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                confirmEvacuationButton.isEnabled = true
                Toast.makeText(
                    requireContext(),
                    getString(R.string.evacuation_area_save_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun openStreetViewAt(panorama: StreetViewPanorama, pos: LatLng) {
        panorama.setPosition(pos, STREET_VIEW_SEARCH_RADIUS_METERS)
    }

    companion object {
        private val SAN_JOSE_CENTER = LatLng(14.585331999115473, 121.18225227090538)
        private const val STREET_VIEW_SEARCH_RADIUS_METERS = 150
        private const val MARKER_ICON_SIZE_DP = 48
    }
}
