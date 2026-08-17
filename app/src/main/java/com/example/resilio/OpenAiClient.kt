package com.example.resilio

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object OpenAiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun chat(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val key = BuildConfig.OPENAI_API_KEY
        if (key.isEmpty()) {
            throw IllegalStateException("Missing API key")
        }

        val arr = JSONArray()
        messages.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("role", m.role)
                    put("content", m.content)
                }
            )
        }

        val body = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", arr)
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errMsg = try {
                    JSONObject(bodyString).optJSONObject("error")?.optString("message")
                } catch (_: Exception) {
                    null
                }
                throw IOException(errMsg ?: bodyString.ifEmpty { "HTTP ${response.code}" })
            }
            val json = JSONObject(bodyString)
            val choices = json.getJSONArray("choices")
            val message = choices.getJSONObject(0).getJSONObject("message")
            message.getString("content")
        }
    }
}
