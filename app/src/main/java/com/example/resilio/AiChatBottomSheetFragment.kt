package com.example.resilio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class AiChatBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var rvChat: RecyclerView
    private lateinit var rvSuggestions: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var progressBar: ProgressBar

    private val chatAdapter = ChatMessageAdapter()
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var historyStore: AiChatHistoryStore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_ai_chat_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        historyStore = AiChatHistoryStore(requireContext())

        rvChat = view.findViewById(R.id.rv_chat)
        rvSuggestions = view.findViewById(R.id.rv_suggestions)
        etMessage = view.findViewById(R.id.et_message)
        btnSend = view.findViewById(R.id.btn_send)
        progressBar = view.findViewById(R.id.progress_bar)

        rvChat.adapter = chatAdapter
        rvChat.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }

        rvSuggestions.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                etMessage.setText("")
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
        
        progressBar.visibility = View.VISIBLE
        btnSend.isEnabled = false

        lifecycleScope.launch {
            val result = GeminiClient.chatWithHistory(messages.dropLast(1), userText)
            progressBar.visibility = View.GONE
            btnSend.isEnabled = true

            val reply = when (result) {
                is ChatResult.Success -> result.text
                ChatResult.OffTopic -> getString(R.string.ai_chat_off_topic)
                is ChatResult.Error -> result.message
            }
            addMessage(ChatMessage("assistant", reply))
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
        rvChat.post {
            rvChat.scrollToPosition(messages.size - 1)
        }
    }

    companion object {
        const val TAG = "AiChatBottomSheet"
    }
}
