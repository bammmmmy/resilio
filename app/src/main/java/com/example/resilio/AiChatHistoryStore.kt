package com.example.resilio

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

class AiChatHistoryStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val auth = FirebaseAuth.getInstance()

    fun load(): List<ChatMessage> {
        val json = prefs.getString(storageKey(), null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        ChatMessage(
                            role = obj.getString(KEY_ROLE),
                            content = obj.getString(KEY_CONTENT),
                            timestamp = obj.optLong(KEY_TIMESTAMP, System.currentTimeMillis())
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(messages: List<ChatMessage>) {
        val toSave = if (messages.size > MAX_MESSAGES) messages.takeLast(MAX_MESSAGES) else messages
        val array = JSONArray()
        for (message in toSave) {
            array.put(
                JSONObject().apply {
                    put(KEY_ROLE, message.role)
                    put(KEY_CONTENT, message.content)
                    put(KEY_TIMESTAMP, message.timestamp)
                },
            )
        }
        prefs.edit().putString(storageKey(), array.toString()).apply()
    }

    private fun storageKey(): String {
        val uid = auth.currentUser?.uid
        return if (uid != null) "chat_$uid" else "chat_guest"
    }

    companion object {
        private const val PREFS_NAME = "ai_chat_history"
        private const val KEY_ROLE = "role"
        private const val KEY_CONTENT = "content"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val MAX_MESSAGES = 200
    }
}
