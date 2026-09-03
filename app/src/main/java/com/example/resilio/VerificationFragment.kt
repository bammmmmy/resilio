package com.example.resilio

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.resilio.databinding.FragmentVerificationBinding
import com.example.resilio.model.User
import com.example.resilio.model.VerificationStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class VerificationFragment : Fragment(R.layout.fragment_verification) {

    private var _binding: FragmentVerificationBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://resilio-ab61f.firebasestorage.app")
    
    private var frontUri: Uri? = null
    private var backUri: Uri? = null
    private var isPickingFront = true

    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            handleImageResult(uri)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentVerificationBinding.bind(view)

        checkCurrentVerificationStatus()

        binding.btnUploadFront.setOnClickListener {
            isPickingFront = true
            openGallery()
        }

        binding.btnUploadBack.setOnClickListener {
            isPickingFront = false
            openGallery()
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

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImage.launch(intent)
    }

    private fun handleImageResult(uri: Uri) {
        if (isPickingFront) {
            frontUri = uri
            binding.ivIdFront.setImageURI(uri)
            binding.ivIdFront.setPadding(0, 0, 0, 0)
            binding.ivIdFront.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        } else {
            backUri = uri
            binding.ivIdBack.setImageURI(uri)
            binding.ivIdBack.setPadding(0, 0, 0, 0)
            binding.ivIdBack.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
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
                
                if (user.sex == "Male") binding.toggleSex.check(R.id.btnMale)
                else if (user.sex == "Female") binding.toggleSex.check(R.id.btnFemale)

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
            binding.etAddress.text.isNullOrBlank() ||
            binding.toggleSex.checkedButtonId == View.NO_ID
        ) {
            Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return false
        }
        if (frontUri == null || backUri == null) {
            Toast.makeText(requireContext(), "Please upload both Front and Back of your ID", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun submitVerification() {
        val uid = auth.currentUser?.uid ?: return
        binding.btnSubmitVerification.isEnabled = false
        Toast.makeText(requireContext(), "Uploading verification documents...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val frontUrl = uploadImage(frontUri!!, "front")
                val backUrl = uploadImage(backUri!!, "back")
                
                if (frontUrl != null && backUrl != null) {
                    updateUserVerificationData(uid, frontUrl, backUrl)
                } else {
                    throw Exception("Upload failed")
                }
            } catch (e: Exception) {
                binding.btnSubmitVerification.isEnabled = true
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun uploadImage(uri: Uri, side: String): String? {
        val uid = auth.currentUser?.uid ?: return null
        val ref = storage.reference.child("verification_ids").child("${uid}_$side.jpg")
        
        android.util.Log.d("ResilioStorage", "Attempting upload to bucket: ${storage.reference.bucket}")

        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            val data = baos.toByteArray()
            inputStream?.close()

            kotlin.coroutines.suspendCoroutine { continuation ->
                ref.putBytes(data)
                    .addOnSuccessListener {
                        ref.downloadUrl.addOnSuccessListener { downloadUrl ->
                            continuation.resumeWith(Result.success(downloadUrl.toString()))
                        }.addOnFailureListener {
                            continuation.resumeWith(Result.failure(it))
                        }
                    }
                    .addOnFailureListener {
                        continuation.resumeWith(Result.failure(it))
                    }
            }
        } catch (e: Exception) {
            android.util.Log.e("ResilioStorage", "Error processing image", e)
            null
        }
    }

    private fun updateUserVerificationData(uid: String, frontUrl: String, backUrl: String) {
        val sex = if (binding.toggleSex.checkedButtonId == R.id.btnMale) "Male" else "Female"
        
        val updates = mapOf(
            "fullName" to binding.etFullName.text.toString().trim(),
            "birthday" to binding.etBirthday.text.toString().trim(),
            "address" to binding.etAddress.text.toString().trim(),
            "sex" to sex,
            "verificationStatus" to VerificationStatus.PENDING,
            "idImageUrl" to frontUrl,
            "idBackImageUrl" to backUrl
        )

        db.collection("users").document(uid).update(updates)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Verification submitted!", Toast.LENGTH_LONG).show()
                findNavController().navigate(R.id.action_verificationFragment_to_homeFragment)
            }
            .addOnFailureListener {
                binding.btnSubmitVerification.isEnabled = true
                Toast.makeText(requireContext(), "Error saving details", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
