package com.example.resilio

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.resilio.databinding.FragmentAiChatBinding
import kotlinx.coroutines.launch

class AiChatFragment : Fragment(R.layout.fragment_ai_chat) {

    private var _binding: FragmentAiChatBinding? = null
    private val binding get() = _binding!!

    private val chatAdapter = ChatMessageAdapter()
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var historyStore: AiChatHistoryStore

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAiChatBinding.bind(view)
        historyStore = AiChatHistoryStore(requireContext())

        binding.rvChat.adapter = chatAdapter
        binding.rvChat.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                binding.etMessage.setText("")
            }
        }

        restoreChatHistory()
    }

    private fun restoreChatHistory() {
        val saved = historyStore.load()
        if (saved.isNotEmpty()) {
            messages.clear()
            messages.addAll(saved)
            chatAdapter.replaceAll(messages)
            scrollToLatest()
        } else {
            addMessage(ChatMessage("assistant", getString(R.string.ai_chat_initial_greeting)))
        }
    }

    private fun sendMessage(userText: String) {
        addMessage(ChatMessage("user", userText))
        
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSend.isEnabled = false

        lifecycleScope.launch {
            try {
                val reply = OpenAiClient.chat(messages)
                binding.progressBar.visibility = View.GONE
                binding.btnSend.isEnabled = true
                addMessage(ChatMessage("assistant", reply))
            } catch (_: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnSend.isEnabled = true
                addMessage(ChatMessage("assistant", getString(R.string.ai_chat_error_message)))
            }
        }
    }

    private fun addMessage(message: ChatMessage) {
        messages.add(message)
        chatAdapter.replaceAll(messages)
        historyStore.save(messages)
        scrollToLatest()
    }

    private fun scrollToLatest() {
        if (messages.isEmpty()) return
        binding.rvChat.post {
            binding.rvChat.scrollToPosition(messages.size - 1)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
