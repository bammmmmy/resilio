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
            val result = GeminiClient.chatWithHistory(messages.dropLast(1), userText)
            binding.progressBar.visibility = View.GONE
            binding.btnSend.isEnabled = true

            val reply = when (result) {
                is ChatResult.Success -> result.text
                ChatResult.OffTopic -> getString(R.string.ai_chat_off_topic)
                is ChatResult.Error -> result.message
            }
            addMessage(ChatMessage("assistant", reply))
        }
    }

    private fun addMessage(message: ChatMessage) {
        // Ensure every new message gets a completely fresh current timestamp
        val freshMessage = if (message.timestamp == 0L) {
            message.copy(timestamp = System.currentTimeMillis())
        } else {
            message
        }
        messages.add(freshMessage)
        chatAdapter.addMessage(freshMessage)
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
