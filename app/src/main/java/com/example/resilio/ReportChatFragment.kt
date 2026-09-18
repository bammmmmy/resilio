package com.example.resilio

import android.os.Bundle
import android.app.Activity
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.resilio.databinding.FragmentReportChatBinding
import com.example.resilio.model.ReportChatMessage
import com.example.resilio.model.User
import com.example.resilio.model.UserRole
import com.example.resilio.util.ProfileManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.suspendCoroutine

class ReportChatFragment : Fragment(R.layout.fragment_report_chat) {

    private var _binding: FragmentReportChatBinding? = null
    private val binding get() = _binding!!
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: ReportChatAdapter
    private var reportId: String = ""
    private var currentUser: User? = null
    private var selectedImageUri: Uri? = null
    private val storage = FirebaseStorage.getInstance("gs://resilio-ab61f.firebasestorage.app")

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImageUri = uri
        if (uri != null) Toast.makeText(requireContext(), "Photo attached", Toast.LENGTH_SHORT).show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentReportChatBinding.bind(view)

        reportId = arguments?.getString("reportId").orEmpty()
        if (reportId.isEmpty()) {
            Toast.makeText(requireContext(), "Invalid report", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        val uid = auth.currentUser?.uid ?: return
        adapter = ReportChatAdapter(uid)
        binding.rvChat.adapter = adapter
        binding.rvChat.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        lifecycleScope.launch {
            currentUser = ProfileManager.getProfile(requireContext()).first()
            listenToMessages()
            listenToReportStatus()
        }

        binding.btnSend.setOnClickListener {
            if (isSending) return@setOnClickListener
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty() || selectedImageUri != null) {
                sendMessage(text, selectedImageUri)
            }
        }
        binding.btnAttachPhoto.setOnClickListener { imagePicker.launch("image/*") }
    }

    private fun listenToReportStatus() {
        db.collection("emergency_reports").document(reportId)
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null) return@addSnapshotListener
                val status = snapshot?.getString("status")
                if (status == "ARCHIVED") {
                    binding.inputLayout.visibility = View.GONE
                    Toast.makeText(requireContext(), "This report is archived. Chat is disabled.", Toast.LENGTH_SHORT).show()
                } else {
                    binding.inputLayout.visibility = View.VISIBLE
                }
            }
    }

    private fun listenToMessages() {
        db.collection("emergency_reports").document(reportId)
            .collection("chats")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { value, error ->
                if (error != null) return@addSnapshotListener
                val messages = value?.toObjects(ReportChatMessage::class.java) ?: emptyList()
                adapter.submitList(messages)
                if (messages.isNotEmpty()) {
                    binding.rvChat.smoothScrollToPosition(messages.size - 1)
                }
            }
    }

    private fun sendMessage(text: String, imageUri: Uri?) {
        val user = currentUser ?: return
        setSending(true)
        val role = if (user.role == UserRole.CHAIRMAN || user.role == UserRole.BDRRMO) "admin" else "user"
        lifecycleScope.launch {
            try {
                val imageUrl = imageUri?.let { uploadChatImage(it) } ?: ""
                val msg = ReportChatMessage(senderUid = auth.currentUser?.uid ?: "", senderName = user.fullName, senderRole = role, message = text, imageUrl = imageUrl)
                db.collection("emergency_reports").document(reportId).collection("chats").add(msg)
                binding.etMessage.setText("")
                selectedImageUri = null
            } catch (error: Exception) {
                Toast.makeText(requireContext(), "Unable to send message", Toast.LENGTH_SHORT).show()
            } finally {
                if (_binding != null) setSending(false)
            }
        }
    }

    private var isSending = false

    private fun setSending(sending: Boolean) {
        isSending = sending
        binding.btnSend.visibility = if (sending) View.GONE else View.VISIBLE
        binding.sendProgress.visibility = if (sending) View.VISIBLE else View.GONE
        binding.btnSend.isEnabled = !sending
        binding.btnAttachPhoto.isEnabled = !sending
        binding.etMessage.isEnabled = !sending
    }

    private suspend fun uploadChatImage(uri: Uri): String = suspendCoroutine { continuation ->
        val reference = storage.reference.child("report_chats/$reportId/${UUID.randomUUID()}.jpg")
        reference.putFile(uri).continueWithTask { reference.downloadUrl }.addOnSuccessListener { continuation.resumeWith(Result.success(it.toString())) }.addOnFailureListener { continuation.resumeWith(Result.failure(it)) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
