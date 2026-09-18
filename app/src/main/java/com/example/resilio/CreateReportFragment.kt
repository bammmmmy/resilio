package com.example.resilio

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.resilio.databinding.FragmentCreateReportBinding
import com.example.resilio.model.EmergencyReport
import com.example.resilio.model.ReportStatus
import com.example.resilio.util.ProfileManager
import com.google.android.gms.location.LocationServices
import com.google.android.material.chip.Chip
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class CreateReportFragment : Fragment(R.layout.fragment_create_report) {

    private var _binding: FragmentCreateReportBinding? = null
    private val binding get() = _binding!!
    
    private val imageUris = mutableListOf<Uri>()
    private var pendingCameraUri: Uri? = null
    private var userLocation: Location? = null
    
    private val storage = FirebaseStorage.getInstance("gs://resilio-ab61f.firebasestorage.app")
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val pickImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        imageUris.addAll(uris.filterNot { imageUris.contains(it) })
        refreshPhotoPreview()
    }

    private val captureImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingCameraUri?.let { imageUris.add(it) }
            refreshPhotoPreview()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            getLocation()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCreateReportBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.btnUploadPhoto.setOnClickListener {
            pickImages.launch("image/*")
        }

        binding.btnAddPhoto.setOnClickListener {
            pickImages.launch("image/*")
        }

        binding.btnCapturePhoto.setOnClickListener {
            openCamera()
        }

        binding.btnSubmitReport.setOnClickListener {
            submitReport()
        }

        checkLocationPermission()
    }

    private fun openCamera() {
        val photoFile = File(requireContext().cacheDir, "temp_emergency_report_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", photoFile)
        pendingCameraUri = uri
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
        }
        captureImage.launch(intent)
    }

    private fun refreshPhotoPreview() {
        if (imageUris.isEmpty()) {
            binding.ivReportPhoto.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.text_secondary)
            binding.ivReportPhoto.setImageResource(R.drawable.ic_camera_alt)
            binding.ivReportPhoto.setPadding(64, 64, 64, 64)
            binding.tvPhotoCount.text = "No photos selected"
            binding.btnAddPhoto.visibility = View.GONE
            return
        }
        binding.ivReportPhoto.imageTintList = null
        binding.ivReportPhoto.setImageURI(imageUris.first())
        binding.ivReportPhoto.setPadding(0, 0, 0, 0)
        binding.ivReportPhoto.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        binding.tvPhotoCount.text = "${imageUris.size} photo${if (imageUris.size == 1) "" else "s"} selected"
        binding.btnAddPhoto.visibility = View.VISIBLE
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getLocation()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    private fun getLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            userLocation = location
            if (location != null) {
                binding.tvLocationStatus.text = "Location: GPS Fixed"
                binding.tvLocationStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_green))
            } else {
                binding.tvLocationStatus.text = "Location: Unable to fix GPS. Try opening Google Maps."
            }
        }
    }

    private fun submitReport() {
        val selectedId = binding.chipGroupType.checkedChipId
        if (selectedId == View.NO_ID) {
            Toast.makeText(requireContext(), "Please select an emergency type", Toast.LENGTH_SHORT).show()
            return
        }

        val type = binding.chipGroupType.findViewById<Chip>(selectedId).text.toString()
        val desc = binding.etDescription.text.toString().trim()
        
        if (desc.isEmpty()) {
            binding.etDescription.error = "Please provide details"
            return
        }

        if (imageUris.isEmpty()) {
            Toast.makeText(requireContext(), "Please include a photo of the emergency", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSubmitReport.isEnabled = false
        Toast.makeText(requireContext(), "Sending emergency report...", Toast.LENGTH_LONG).show()

        lifecycleScope.launch {
            try {
                val reportId = UUID.randomUUID().toString()
                val imageUrls = uploadPhotos(reportId)
                val uid = auth.currentUser?.uid ?: "anonymous"
                
                val profile = ProfileManager.getProfile(requireContext()).first()
                val name = profile.fullName.ifEmpty { "Resident" }

                val report = EmergencyReport(
                    id = reportId,
                    senderUid = uid,
                    senderName = name,
                    type = type,
                    description = desc,
                    imageUrl = imageUrls.firstOrNull(),
                    imageUrls = imageUrls,
                    latitude = userLocation?.latitude ?: 0.0,
                    longitude = userLocation?.longitude ?: 0.0,
                    status = ReportStatus.PENDING,
                    timestamp = null // Let Firebase handle ServerTimestamp
                )

                db.collection("emergency_reports").document(reportId).set(report)
                    .addOnSuccessListener {
                        // Also create a hazard location for the VR Map
                        val hazard = com.example.resilio.model.HazardLocation(
                            id = reportId,
                            hazardType = type.lowercase(),
                            description = desc,
                            address = "Resident Emergency Report",
                            latitude = userLocation?.latitude ?: 0.0,
                            longitude = userLocation?.longitude ?: 0.0,
                            radius = 50.0, // Default 50m radius for emergency reports
                            createdBy = uid,
                            active = true
                        )
                        db.collection("hazardLocations").document(reportId).set(hazard)

                        Toast.makeText(requireContext(), "Report was sent", Toast.LENGTH_LONG).show()
                        findNavController().navigateUp()
                    }
                    .addOnFailureListener {
                        binding.btnSubmitReport.isEnabled = true
                        Toast.makeText(requireContext(), "Error sending report", Toast.LENGTH_SHORT).show()
                    }

            } catch (e: Exception) {
                binding.btnSubmitReport.isEnabled = true
                Toast.makeText(requireContext(), "Upload error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun uploadPhotos(reportId: String): List<String> {
        return imageUris.mapIndexed { index, uri ->
            val ref = storage.reference.child("emergency_photos/$reportId/$index.jpg")
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: throw IllegalStateException("Unable to read selected photo")
            inputStream?.close()
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            val data = baos.toByteArray()

            kotlin.coroutines.suspendCoroutine { continuation ->
                ref.putBytes(data).continueWithTask { task ->
                    if (!task.isSuccessful) task.exception?.let { throw it }
                    ref.downloadUrl
                }.addOnSuccessListener { downloadUri ->
                    continuation.resumeWith(Result.success(downloadUri.toString()))
                }.addOnFailureListener {
                    continuation.resumeWith(Result.failure(it))
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
