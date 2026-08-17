package com.example.resilio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.resilio.model.EvacuationArea
import com.example.resilio.model.HazardLocation
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.StreetViewPanorama
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.SupportStreetViewPanoramaFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EvacuationMapFragment : Fragment(R.layout.fragment_evacuation_map), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private lateinit var mapMaskOverlay: MapMaskView
    private lateinit var streetOverlay: View
    private lateinit var streetViewPin: ImageView
    private lateinit var streetViewConfirmButton: FloatingActionButton
    private lateinit var confirmEvacuationButton: MaterialButton
    private lateinit var streetViewHintOverlay: View
    private lateinit var fabStreet: FloatingActionButton
    private lateinit var fabClose: FloatingActionButton
    private lateinit var fabAi: FloatingActionButton
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
    private var pinTouchOffsetX = 0f
    private var pinTouchOffsetY = 0f
    private var evacuationMarkerIcon: BitmapDescriptor? = null
    private var hazardMarkerIcon: BitmapDescriptor? = null
    private val evacuationMarkers = mutableListOf<Marker>()
    private val hazardMarkers = mutableListOf<Marker>()

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mapMaskOverlay = view.findViewById(R.id.map_mask_overlay)
        streetOverlay = view.findViewById(R.id.street_view_container)
        streetViewPin = view.findViewById(R.id.streetview_pin)
        streetViewConfirmButton = view.findViewById(R.id.fab_confirm_street_view)
        confirmEvacuationButton = view.findViewById(R.id.btn_confirm_evacuation_area)
        streetViewHintOverlay = view.findViewById(R.id.streetview_hint_overlay)
        fabStreet = view.findViewById(R.id.fab_street_view)
        fabClose = view.findViewById(R.id.fab_close_street_view)
        fabAi = view.findViewById(R.id.fab_ai_chat)
        super.onViewCreated(view, savedInstanceState)

        evacuationCreateMode = arguments?.getBoolean("createMode", false) ?: false
        hazardCreateMode = arguments?.getBoolean("hazardCreateMode", false) ?: false
        areaName = arguments?.getString("areaName").orEmpty()
        areaAddress = arguments?.getString("areaAddress").orEmpty()
        hazardType = arguments?.getString("hazardType").orEmpty()
        hazardDescription = arguments?.getString("hazardDescription").orEmpty()
        hazardAddress = arguments?.getString("hazardAddress").orEmpty()
        focusLatitude = arguments?.getFloat("focusLatitude", 0f)?.toDouble() ?: 0.0
        focusLongitude = arguments?.getFloat("focusLongitude", 0f)?.toDouble() ?: 0.0

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
                saveHazardLocation(location)
            } else {
                saveEvacuationArea(location)
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
            AiChatBottomSheetFragment().show(childFragmentManager, "ai_chat")
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

        // Define the Brgy. San Jose polygon points (the area to keep clear)
        val sanJosePolygon = listOf(
            LatLng(14.742485845406158, 121.31765553238483),
            LatLng(14.73637059040346, 121.32354202789874),
            LatLng(14.666273096361838, 121.33538220091981),
            LatLng(14.605635467050892, 121.32619585872438),
            LatLng(14.58657164606891, 121.27142112776289),
            LatLng(14.564102509102275, 121.19973670763827),
            LatLng(14.587389952822482, 121.17013717993684),
            LatLng(14.675573485191483, 121.20950887093656),
            LatLng(14.742485845406158, 121.31765553238483)
        )

        // Restrict map panning to the entire Antipolo City area
        val antipoloCityBounds = LatLngBounds(
            LatLng(14.4272, 121.01592), // Southwest
            LatLng(14.7472, 121.33592)  // Northeast
        )
        map.setLatLngBoundsForCameraTarget(antipoloCityBounds)
        map.setMinZoomPreference(11.0f)

        // 1. Focus on San Jose at a standard zoom level
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(SAN_JOSE_CENTER, 15.0f))

        // 2. Sync the mask overlay with the camera movement
        map.setOnCameraMoveListener {
            updateMask(map, sanJosePolygon)
        }
        map.setOnCameraIdleListener {
            updateMask(map, sanJosePolygon)
        }
        updateMask(map, sanJosePolygon)

        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMapToolbarEnabled = false
        }

        map.setOnMarkerClickListener { marker ->
            animateToMarker(marker.position)
            when (val tag = marker.tag) {
                is HazardLocation -> {
                    showHazardAiInfo(tag)
                    true
                }
                else -> {
                    marker.showInfoWindow()
                    true
                }
            }
        }

        map.setOnInfoWindowClickListener { marker ->
            when (val tag = marker.tag) {
                is EvacuationArea -> openStreetViewForArea(tag)
                is HazardLocation -> showHazardAiInfo(tag)
            }
        }

        if (evacuationPinMode) {
            confirmEvacuationButton.isEnabled = true
        }

        loadMapMarkers()
    }

    private fun updateMask(map: GoogleMap, polygon: List<LatLng>) {
        val projection = map.projection
        val screenPoints = polygon.map { latLng ->
            val point = projection.toScreenLocation(latLng)
            PointF(point.x.toFloat(), point.y.toFloat())
        }
        mapMaskOverlay.updateHole(screenPoints)
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
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener
                clearHazardMarkers()

                val icon = getHazardMarkerIcon()

                snapshot.documents.forEach { document ->
                    val hazard = document.toObject(HazardLocation::class.java)
                        ?.copy(id = document.id)
                        ?: return@forEach
                    if (!hasMapLocation(hazard.latitude, hazard.longitude)) return@forEach

                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(LatLng(hazard.latitude, hazard.longitude))
                            .title(hazardMarkerTitle(hazard.hazardType))
                            .snippet(hazardMarkerSnippet(hazard))
                            .icon(icon)
                    ) ?: return@forEach

                    marker.tag = hazard
                    hazardMarkers.add(marker)
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

        focusLatitude = 0.0
        focusLongitude = 0.0

        val target = LatLng(lat, lng)
        animateToMarker(target)

        evacuationMarkers.firstOrNull { marker ->
            val area = marker.tag as? EvacuationArea ?: return@firstOrNull false
            kotlin.math.abs(area.latitude - lat) < 1e-6 && kotlin.math.abs(area.longitude - lng) < 1e-6
        }?.showInfoWindow()

        hazardMarkers.firstOrNull { marker ->
            val hazard = marker.tag as? HazardLocation ?: return@firstOrNull false
            kotlin.math.abs(hazard.latitude - lat) < 1e-6 && kotlin.math.abs(hazard.longitude - lng) < 1e-6
        }?.showInfoWindow()
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

    private fun saveHazardLocation(location: LatLng) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(requireContext(), R.string.hazard_location_save_failed, Toast.LENGTH_SHORT).show()
            return
        }

        confirmEvacuationButton.isEnabled = false

        val hazard = HazardLocation(
            hazardType = hazardType,
            description = hazardDescription,
            address = hazardAddress,
            latitude = location.latitude,
            longitude = location.longitude,
            createdBy = uid,
        )

        firestore.collection("hazardLocations")
            .add(hazard)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                Toast.makeText(requireContext(), R.string.hazard_location_saved, Toast.LENGTH_SHORT).show()
                findNavController().popBackStack(R.id.createHazardLocationFragment, true)
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                confirmEvacuationButton.isEnabled = true
                Toast.makeText(
                    requireContext(),
                    R.string.hazard_location_save_failed,
                    Toast.LENGTH_SHORT,
                ).show()
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

class MapMaskView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 30, 30, 30)
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val path = Path()
    private var holePoints: List<PointF>? = null

    fun updateHole(points: List<PointF>) {
        this.holePoints = points
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)
        holePoints?.let { pts ->
            if (pts.isNotEmpty()) {
                path.reset()
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    path.lineTo(pts[i].x, pts[i].y)
                }
                path.close()
                canvas.drawPath(path, clearPaint)
            }
        }
    }
}
