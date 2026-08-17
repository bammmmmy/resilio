package com.example.resilio

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.resilio.databinding.FragmentVerificationBinding
import com.example.resilio.model.User
import com.example.resilio.model.VerificationStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream

class VerificationFragment : Fragment(R.layout.fragment_verification) {

    private var _binding: FragmentVerificationBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var imageUri: Uri? = null
    private var imageBitmap: Bitmap? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            imageUri = result.data?.data
            binding.ivIdPreview.setImageURI(imageUri)
            binding.ivIdPreview.setPadding(0, 0, 0, 0)
            binding.ivIdPreview.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            binding.btnSubmitVerification.isEnabled = true
        }
    }

    private val captureImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            imageBitmap = result.data?.extras?.let { extras ->
                BundleCompat.getParcelable(extras, "data", Bitmap::class.java)
            }
            val bitmap = imageBitmap ?: return@registerForActivityResult
            binding.ivIdPreview.setImageBitmap(bitmap)
            binding.ivIdPreview.setPadding(0, 0, 0, 0)
            binding.ivIdPreview.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            binding.btnSubmitVerification.isEnabled = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentVerificationBinding.bind(view)

        checkCurrentVerificationStatus()

        binding.btnUpload.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImage.launch(intent)
        }

        binding.btnCapture.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            captureImage.launch(intent)
        }

        binding.btnSubmitVerification.setOnClickListener {
            if (validateInputs()) {
                submitVerification()
            }
        }

        binding.btnSkipVerification.setOnClickListener {
            findNavController().navigate(R.id.action_verificationFragment_to_homeFragment)
        }
    }

    private fun checkCurrentVerificationStatus() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val user = doc.toObject(User::class.java)
            if (user != null) {
                binding.etFullName.setText(user.fullName)
                binding.etAddress.setText(user.address)
                binding.etBirthday.setText(user.birthday)
                binding.etIdNumber.setText(user.idNumber)

                if (user.verificationStatus == VerificationStatus.PENDING ||
                    user.verificationStatus == VerificationStatus.APPROVED
                ) {
                    findNavController().navigate(R.id.action_verificationFragment_to_homeFragment)
                }
            }
        }
    }

    private fun validateInputs(): Boolean {
        if (binding.etFullName.text.isNullOrBlank() ||
            binding.etBirthday.text.isNullOrBlank() ||
            binding.etIdNumber.text.isNullOrBlank() ||
            binding.etAddress.text.isNullOrBlank()
        ) {
            Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return false
        }
        if (imageUri == null && imageBitmap == null) {
            Toast.makeText(requireContext(), "Please provide an ID photo", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun submitVerification() {
        val uid = auth.currentUser?.uid ?: return
        binding.btnSubmitVerification.isEnabled = false
        Toast.makeText(requireContext(), "Processing verification data...", Toast.LENGTH_SHORT).show()

        try {
            // Get bitmap from Uri if needed
            val bitmap = if (imageBitmap != null) {
                imageBitmap!!
            } else {
                val inputStream = requireContext().contentResolver.openInputStream(imageUri!!)
                BitmapFactory.decodeStream(inputStream)
            }

            // Compress and convert to Base64 String (completely free way to store images in DB)
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos) // Compress to 50% quality to save space
            val imageBytes = baos.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)

            updateUserVerificationData(uid, base64Image)

        } catch (e: Exception) {
            binding.btnSubmitVerification.isEnabled = true
            Toast.makeText(requireContext(), "Failed to process image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUserVerificationData(uid: String, base64Image: String) {
        val updates = mapOf(
            "fullName" to binding.etFullName.text.toString().trim(),
            "birthday" to binding.etBirthday.text.toString().trim(),
            "idNumber" to binding.etIdNumber.text.toString().trim(),
            "address" to binding.etAddress.text.toString().trim(),
            "verificationStatus" to VerificationStatus.PENDING,
            "idImageUrl" to base64Image // Storing the image text directly in the database
        )

        db.collection("users").document(uid).update(updates)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Verification details submitted!", Toast.LENGTH_LONG).show()
                findNavController().navigate(R.id.action_verificationFragment_to_homeFragment)
            }
            .addOnFailureListener {
                binding.btnSubmitVerification.isEnabled = true
                Toast.makeText(requireContext(), "Error saving data", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
