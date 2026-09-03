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
import com.google.android.gms.location.LocationServices
import com.google.android.material.chip.Chip
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class CreateReportFragment : Fragment(R.layout.fragment_create_report) {

    private var _binding: FragmentCreateReportBinding? = null
    private val binding get() = _binding!!
    
    private var imageUri: Uri? = null
    private var imageBitmap: Bitmap? = null
    private var userLocation: Location? = null
    
    private val storage = FirebaseStorage.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            imageUri = result.data?.data
            binding.ivReportPhoto.setImageURI(imageUri)
            binding.ivReportPhoto.setPadding(0, 0, 0, 0)
            binding.ivReportPhoto.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        }
    }

    private val captureImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val photoFile = File(requireContext().cacheDir, "temp_emergency_report.jpg")
            if (photoFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                imageBitmap = bitmap
                binding.ivReportPhoto.setImageBitmap(bitmap)
                binding.ivReportPhoto.setPadding(0, 0, 0, 0)
                binding.ivReportPhoto.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            }
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
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImage.launch(intent)
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
        val photoFile = File(requireContext().cacheDir, "temp_emergency_report.jpg")
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", photoFile)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
        }
        captureImage.launch(intent)
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

        if (imageUri == null && imageBitmap == null) {
            Toast.makeText(requireContext(), "Please include a photo of the emergency", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSubmitReport.isEnabled = false
        Toast.makeText(requireContext(), "Sending emergency report...", Toast.LENGTH_LONG).show()

        lifecycleScope.launch {
            try {
                val imageUrl = uploadPhoto()
                val reportId = UUID.randomUUID().toString()
                val uid = auth.currentUser?.uid ?: "anonymous"
                val name = auth.currentUser?.displayName ?: "Resident"

                val report = EmergencyReport(
                    id = reportId,
                    senderUid = uid,
                    senderName = name,
                    type = type,
                    description = desc,
                    imageUrl = imageUrl,
                    latitude = userLocation?.latitude ?: 0.0,
                    longitude = userLocation?.longitude ?: 0.0,
                    status = ReportStatus.PENDING,
                    timestamp = Timestamp.now()
                )

                db.collection("emergency_reports").document(reportId).set(report)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Emergency reported! Help is on the way.", Toast.LENGTH_LONG).show()
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

    private suspend fun uploadPhoto(): String? {
        val reportId = UUID.randomUUID().toString()
        val ref = storage.reference.child("emergency_photos/$reportId.jpg")
        
        val baos = ByteArrayOutputStream()
        val bitmap = if (imageBitmap != null) {
            imageBitmap!!
        } else {
            val inputStream = requireContext().contentResolver.openInputStream(imageUri!!)
            BitmapFactory.decodeStream(inputStream)
        }
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        val data = baos.toByteArray()

        return kotlin.coroutines.suspendCoroutine { continuation ->
            ref.putBytes(data).continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                ref.downloadUrl
            }.addOnSuccessListener { uri ->
                continuation.resumeWith(Result.success(uri.toString()))
            }.addOnFailureListener {
                continuation.resumeWith(Result.failure(it))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
