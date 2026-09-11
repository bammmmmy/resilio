package com.example.resilio

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReportChatFragment : Fragment(R.layout.fragment_report_chat) {

    private var _binding: FragmentReportChatBinding? = null
    private val binding get() = _binding!!
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: ReportChatAdapter
    private var reportId: String = ""
    private var currentUser: User? = null

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
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                binding.etMessage.setText("")
            }
        }
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

    private fun sendMessage(text: String) {
        val user = currentUser ?: return
        val role = if (user.role == UserRole.CHAIRMAN || user.role == UserRole.BDRRMO) "admin" else "user"
        
        val msg = ReportChatMessage(
            senderUid = auth.currentUser?.uid ?: "",
            senderName = user.fullName,
            senderRole = role,
            message = text
        )

        db.collection("emergency_reports").document(reportId)
            .collection("chats")
            .add(msg)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
