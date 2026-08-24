package com.example.resilio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class HazardAiInfoBottomSheetFragment : BottomSheetDialogFragment() {

    private var streetViewListener: ((LatLng) -> Unit)? = null

    fun setOnStreetViewRequested(listener: (LatLng) -> Unit) {
        streetViewListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.ThemeOverlay_App_BottomSheetDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.bottom_sheet_hazard_ai_info, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val hazardType = requireArguments().getString(ARG_HAZARD_TYPE).orEmpty()
        val description = requireArguments().getString(ARG_DESCRIPTION).orEmpty()
        val address = requireArguments().getString(ARG_ADDRESS).orEmpty()
        val latitude = requireArguments().getDouble(ARG_LATITUDE)
        val longitude = requireArguments().getDouble(ARG_LONGITUDE)

        val titleView = view.findViewById<TextView>(R.id.tv_hazard_ai_title)
        val descriptionView = view.findViewById<TextView>(R.id.tv_hazard_official_description)
        val addressView = view.findViewById<TextView>(R.id.tv_hazard_official_address)
        val progress = view.findViewById<ProgressBar>(R.id.progress_hazard_ai)
        val analysisView = view.findViewById<TextView>(R.id.tv_hazard_ai_analysis)
        val streetViewButton = view.findViewById<MaterialButton>(R.id.btn_hazard_street_view)

        titleView.text = hazardTypeLabel(hazardType)
        descriptionView.text = description.ifBlank { getString(R.string.hazard_description_not_provided) }
        addressView.text = address

        val location = LatLng(latitude, longitude)
        streetViewButton.setOnClickListener {
            streetViewListener?.invoke(location)
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val result = GeminiClient.analyzeHazard(
                hazardType = hazardType,
                address = address,
                description = description,
                latitude = latitude,
                longitude = longitude,
            )
            progress.visibility = View.GONE
            analysisView.visibility = View.VISIBLE
            analysisView.text = when (result) {
                is ChatResult.Success -> result.text
                ChatResult.OffTopic -> getString(R.string.ai_chat_off_topic)
                is ChatResult.Error -> result.message
            }
        }
    }

    private fun hazardTypeLabel(key: String): String = when (key.lowercase()) {
        "flood" -> getString(R.string.filter_flood)
        "typhoon" -> getString(R.string.filter_typhoon)
        "landslide" -> getString(R.string.filter_landslide)
        "earthquake" -> getString(R.string.filter_earthquake)
        else -> key.replaceFirstChar { it.uppercase() }
    }

    companion object {
        private const val ARG_HAZARD_TYPE = "hazardType"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_ADDRESS = "address"
        private const val ARG_LATITUDE = "latitude"
        private const val ARG_LONGITUDE = "longitude"

        fun newInstance(
            hazardType: String,
            description: String,
            address: String,
            latitude: Double,
            longitude: Double,
        ): HazardAiInfoBottomSheetFragment = HazardAiInfoBottomSheetFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_HAZARD_TYPE, hazardType)
                putString(ARG_DESCRIPTION, description)
                putString(ARG_ADDRESS, address)
                putDouble(ARG_LATITUDE, latitude)
                putDouble(ARG_LONGITUDE, longitude)
            }
        }
    }
}
