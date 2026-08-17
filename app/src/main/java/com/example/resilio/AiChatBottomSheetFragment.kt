package com.example.resilio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class AiChatBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var adapter: ChatMessageAdapter
    private val conversation = mutableListOf<ChatMessage>()
    private val systemPrompt = ChatMessage(
        "system",
        "You are a helpful assistant for Resilio, a disaster preparedness and evacuation app. " +
            "Give concise, practical answers (safety, evacuation, alerts, maps). If unsure, say so."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.ThemeOverlay_App_BottomSheetDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_ai_chat, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_chat)
        val input = view.findViewById<TextInputEditText>(R.id.input_message)
        val send = view.findViewById<MaterialButton>(R.id.button_send)

        adapter = ChatMessageAdapter()
        recycler.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        recycler.adapter = adapter

        send.setOnClickListener { trySend(view, input, send) }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                trySend(view, input, send)
                true
            } else {
                false
            }
        }
    }

    private fun trySend(root: View, input: TextInputEditText, send: MaterialButton) {
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        if (BuildConfig.OPENAI_API_KEY.isEmpty()) {
            Toast.makeText(requireContext(), R.string.ai_chat_missing_key, Toast.LENGTH_LONG).show()
            return
        }

        input.text?.clear()
        conversation.add(ChatMessage("user", text))
        adapter.replaceAll(conversation)
        scrollToBottom(root)

        send.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val payload = listOf(systemPrompt) + conversation
                val reply = OpenAiClient.chat(payload)
                conversation.add(ChatMessage("assistant", reply))
                adapter.replaceAll(conversation)
                scrollToBottom(root)
            } catch (e: Exception) {
                val msg = getString(R.string.ai_chat_error, e.message ?: e.javaClass.simpleName)
                conversation.add(ChatMessage("assistant", msg))
                adapter.replaceAll(conversation)
                scrollToBottom(root)
            } finally {
                send.isEnabled = true
            }
        }
    }

    private fun scrollToBottom(root: View) {
        val recycler = root.findViewById<RecyclerView>(R.id.recycler_chat)
        if (adapter.itemCount > 0) {
            recycler.post {
                recycler.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }
}
