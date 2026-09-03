package com.example.resilio

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
import com.bumptech.glide.Glide
import com.example.resilio.databinding.FragmentEditProfileBinding
import com.example.resilio.model.User
import com.example.resilio.util.ProfileManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

import androidx.fragment.app.viewModels
import com.example.resilio.viewmodel.AuthViewModel

class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!
    private val authViewModel: AuthViewModel by viewModels()
    
    private var selectedImageUri: Uri? = null
    private var tempCameraUri: Uri? = null
    private var currentUser: User? = null

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleImageSelected(uri)
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            tempCameraUri?.let { uri ->
                handleImageSelected(uri)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentEditProfileBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        loadProfileData()

        binding.fabChangePhoto.setOnClickListener {
            showImagePickerOptions()
        }

        binding.btnSaveChanges.setOnClickListener {
            saveChanges()
        }
    }

    private fun loadProfileData() {
        authViewModel.userState.observe(viewLifecycleOwner) { result ->
            result?.onSuccess { user ->
                currentUser = user
                binding.etFullName.setText(user.fullName)
                binding.etPosition.setText(user.position)
                binding.etBarangayName.setText(user.barangayName)
                binding.etContactNumber.setText(user.contactNumber)
                binding.etEmail.setText(user.email)
                binding.etAddress.setText(user.address)
                binding.etAbout.setText(user.about)
                
                user.profileImageUrl?.let {
                    Glide.with(this@EditProfileFragment)
                        .load(it)
                        .placeholder(R.drawable.logog)
                        .into(binding.ivEditProfileImage)
                }
            }
        }
        authViewModel.checkAuthState()
    }

    private fun showImagePickerOptions() {
        val options = arrayOf("Choose from Gallery", "Take Photo")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Profile Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openGallery()
                    1 -> checkCameraPermission()
                }
            }
            .show()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        val photoFile = File(requireContext().cacheDir, "temp_profile_image.jpg")
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", photoFile)
        tempCameraUri = uri
        
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
        }
        cameraLauncher.launch(intent)
    }

    private fun handleImageSelected(uri: Uri) {
        selectedImageUri = uri
        Glide.with(this)
            .load(uri)
            .into(binding.ivEditProfileImage)
    }

    private fun saveChanges() {
        val fullName = binding.etFullName.text.toString().trim()
        if (fullName.isEmpty()) {
            binding.etFullName.error = "Full Name is required"
            return
        }

        val email = binding.etEmail.text.toString().trim()
        if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Invalid email format"
            return
        }

        binding.btnSaveChanges.isEnabled = false
        Toast.makeText(requireContext(), "Syncing with cloud...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val current = currentUser ?: return@launch
            
            var profileImageUrl = current.profileImageUrl
            selectedImageUri?.let { uri ->
                val uploadResult = uploadImageToFirebase(uri)
                if (uploadResult != null) {
                    profileImageUrl = uploadResult
                }
            }

            val updatedUser = current.copy(
                fullName = fullName,
                position = binding.etPosition.text.toString().trim(),
                barangayName = binding.etBarangayName.text.toString().trim(),
                contactNumber = binding.etContactNumber.text.toString().trim(),
                email = email,
                address = binding.etAddress.text.toString().trim(),
                about = binding.etAbout.text.toString().trim(),
                profileImageUrl = profileImageUrl
            )

            authViewModel.updateProfile(updatedUser) { result ->
                if (result.isSuccess) {
                    // Also update local cache for offline/instant loading
                    lifecycleScope.launch {
                        ProfileManager.saveProfile(requireContext(), updatedUser)
                        Toast.makeText(requireContext(), "Profile synced successfully", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                } else {
                    binding.btnSaveChanges.isEnabled = true
                    Toast.makeText(requireContext(), "Cloud sync failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun uploadImageToFirebase(uri: Uri): String? {
        return kotlin.coroutines.suspendCoroutine { continuation ->
            authViewModel.uploadProfileImage(uri) { result ->
                continuation.resumeWith(Result.success(result.getOrNull()))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
