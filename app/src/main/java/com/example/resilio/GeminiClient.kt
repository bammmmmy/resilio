package com.example.resilio

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.QuotaExceededException
import com.google.ai.client.generativeai.type.ResponseStoppedException
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.client.generativeai.type.generationConfig
import kotlin.math.ceil
import kotlinx.coroutines.delay

sealed class ChatResult {
    data class Success(val text: String) : ChatResult()
    data object OffTopic : ChatResult()
    data class Error(val message: String) : ChatResult()
}

object GeminiClient {
    private const val OFF_TOPIC_TOKEN = "OFF_TOPIC"
    private const val MAX_RETRIES_PER_MODEL = 3
    private const val INITIAL_RETRY_DELAY_MS = 1_000L

    /** Lite model first to reduce quota usage; fall back if unavailable. */
    private val modelNames = listOf(
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash",
        "gemini-flash-latest",
    )

    /** Cap history sent to the API to control input tokens and quota usage. */
    private const val MAX_HISTORY_MESSAGES = 10

    private val retryAfterSecondsPattern = Regex("""Please retry in ([\d.]+)s""", RegexOption.IGNORE_CASE)

    private val disasterKeywords = listOf(
        "disaster", "emergency", "evacuat", "flood", "typhoon", "earthquake",
        "landslide", "storm", "thunderstorm", "thunder", "lightning", "rain",
        "bagyo", "baha", "lindol", "calamity", "alert", "signal", "habagat",
        "monsoon", "cyclone", "tornado", "wind", "flash flood", "land slide",
        "warning", "safety", "safe", "prepare", "preparedness", "shelter", "rescue",
        "antipolo", "barangay", "brgy", "go bag", "go-bag", "first aid",
        "fire", "tsunami", "volcanic", "storm surge", "stay at home", "stay home",
        "pag-ulan", "ulan", "emergency kit", "evacuation center",
        "evacuation area", "evac center", "relief", "disaster risk", "drrm",
        "philippine", "phivolcs", "ndrrmc", "red cross", "hotline", "911", "117",
        "weather", "temperature", "humidity", "precip", "advisory", "announcement",
        "anunsyo", "babala", "update", "posted", "latest", "current condition",
    )

    private val obviousOffTopicKeywords = listOf(
        "recipe", "joke", "dating", "homework", "nba", "football", "soccer",
        "bitcoin", "crypto", "stock", "movie", "netflix", "celebrity", "tiktok",
        "instagram", "song lyric", "write me a poem", "python code", "javascript",
        "programming", "translate this paragraph", "math problem", "solve for x",
        "who won the", "capital of france", "horoscope",
    )

    private val systemInstructionText = """
        You are Resilio AI, a disaster preparedness and safety assistant for users in Antipolo City.

        ALWAYS answer questions about: floods, typhoons, thunderstorms, lightning, heavy rain,
        earthquakes, landslides, fires, storms, strong winds, evacuation centers/areas, emergency
        kits, go-bags, alerts, announcements, current weather, sheltering at home, barangay safety,
        and before/during/after disaster actions. Users do NOT need to mention Antipolo—apply
        Antipolo/Philippines context when helpful.

        Reply with exactly $OFF_TOPIC_TOKEN ONLY for clearly unrelated topics (jokes, sports,
        entertainment, homework, coding, recipes, politics, shopping, etc.).

        RULES:
        1. If the question is about any hazard or safety during weather/disasters, you MUST answer.
        2. If the question is about current weather, an alert, an announcement, or a posted update, answer from LIVE APP DATA. Use the matching title and details. Do not invent posts.
        3. Reply in 2-4 short sentences. Be direct and actionable.
        4. Do not greet, apologize, or add filler. No bullet lists unless essential.
        5. When unsure, give the safest brief advice for the Philippines.
    """.trimIndent()

    private val shortGenerationConfig = generationConfig {
        maxOutputTokens = 320
        temperature = 0.3f
    }

    private val hazardAnalysisGenerationConfig = generationConfig {
        maxOutputTokens = 384
        temperature = 0.35f
    }

    private val hazardAnalysisInstruction = """
        You analyze marked hazard locations in San Jose, Antipolo City for residents and barangay responders.

        Based on the hazard type, address, description, and coordinates provided, give practical safety analysis.
        Include likely risks, who should avoid the area, immediate precautions, and when to evacuate or alert officials.
        Do not invent specific incidents beyond reasonable inference from the description.
        Reply in 4-6 clear sentences. Be direct. No bullet lists unless essential.
    """.trimIndent()

    suspend fun analyzeHazard(
        hazardType: String,
        address: String,
        description: String,
        latitude: Double,
        longitude: Double,
    ): ChatResult {
        if (BuildConfig.GEMINI_API_KEY.isEmpty() || BuildConfig.GEMINI_API_KEY.contains("YOUR_")) {
            return ChatResult.Error("API Key is missing. Add GEMINI_API_KEY to local.properties and sync.")
        }

        val userPrompt = buildString {
            appendLine("Hazard type: $hazardType")
            appendLine("Address: $address")
            appendLine(
                if (description.isNotBlank()) "Description: $description"
                else "Description: (not provided)",
            )
            if (latitude != 0.0 || longitude != 0.0) {
                appendLine("Coordinates: $latitude, $longitude")
            }
            appendLine()
            append("Provide a detailed safety analysis for this hazard area in Antipolo.")
        }

        return try {
            val raw = requestWithFallback(
                history = emptyList(),
                nextMessage = userPrompt,
                systemInstruction = hazardAnalysisInstruction,
                generationConfig = hazardAnalysisGenerationConfig,
            )
            when {
                raw.isEmpty() -> ChatResult.Error("Empty response from AI.")
                else -> ChatResult.Success(raw.trim())
            }
        } catch (e: Exception) {
            Log.e("GeminiClient", "Hazard analysis error: ${e.message}", e)
            ChatResult.Error(userFacingError(e))
        }
    }

    suspend fun chatWithHistory(history: List<ChatMessage>, nextMessage: String): ChatResult {
        if (BuildConfig.GEMINI_API_KEY.isEmpty() || BuildConfig.GEMINI_API_KEY.contains("YOUR_")) {
            Log.e("GeminiClient", "API Key is missing or invalid in local.properties")
            return ChatResult.Error("API Key is missing. Add GEMINI_API_KEY to local.properties and sync.")
        }

        if (isClearlyOffTopic(nextMessage)) {
            return ChatResult.OffTopic
        }

        val filteredHistory = history
            .filter { it.role == "user" || (it.role == "assistant" && !isStoredOffTopicRejection(it.content)) }
            .dropWhile { it.role != "user" }
            .takeLast(MAX_HISTORY_MESSAGES)
            .map {
                content(role = if (it.role == "user") "user" else "model") {
                    text(it.content)
                }
            }

        val liveContext = runCatching { ResilioLiveContext.loadForAi() }
            .getOrElse { error ->
                Log.w("GeminiClient", "Live context unavailable: ${error.message}")
                "LIVE APP DATA: unavailable right now."
            }

        return try {
            val raw = requestWithFallback(
                history = filteredHistory,
                nextMessage = nextMessage,
                systemInstruction = "$systemInstructionText\n\n$liveContext",
                generationConfig = shortGenerationConfig,
            )
            when {
                raw.isEmpty() -> ChatResult.Error("Empty response from AI.")
                isOffTopicResponse(raw) -> ChatResult.OffTopic
                else -> ChatResult.Success(enforceShortReply(raw))
            }
        } catch (e: Exception) {
            Log.e("GeminiClient", "Chat error: ${e.message}", e)
            ChatResult.Error(userFacingError(e))
        }
    }

    private suspend fun requestWithFallback(
        history: List<Content>,
        nextMessage: String,
        systemInstruction: String = systemInstructionText,
        generationConfig: GenerationConfig = shortGenerationConfig,
    ): String {
        var lastError: Exception? = null

        for (modelName in modelNames) {
            val model = createModel(modelName, systemInstruction, generationConfig)
            repeat(MAX_RETRIES_PER_MODEL) { attempt ->
                try {
                    val chat = model.startChat(history = history)
                    val text = chat.sendMessage(nextMessage).text?.trim().orEmpty()
                    if (attempt > 0 || modelName != modelNames.first()) {
                        Log.i("GeminiClient", "Succeeded with $modelName (attempt ${attempt + 1})")
                    }
                    return text
                } catch (e: ResponseStoppedException) {
                    val partial = e.response?.text?.trim().orEmpty()
                    if (partial.isNotEmpty()) {
                        Log.w("GeminiClient", "$modelName stopped early; using partial response")
                        return partial
                    }
                    lastError = e
                    Log.w(
                        "GeminiClient",
                        "$modelName attempt ${attempt + 1} stopped: ${e.message?.take(120)}",
                    )
                    if (attempt < MAX_RETRIES_PER_MODEL - 1) {
                        delay(INITIAL_RETRY_DELAY_MS * (attempt + 1))
                    }
                } catch (e: Exception) {
                    lastError = e
                    if (isQuotaExceeded(e)) {
                        Log.w(
                            "GeminiClient",
                            "$modelName quota exceeded (attempt ${attempt + 1})",
                        )
                        if (attempt < MAX_RETRIES_PER_MODEL - 1) {
                            delay(quotaRetryDelayMs(e))
                        }
                        return@repeat
                    }
                    if (!isRetryable(e)) throw e
                    Log.w(
                        "GeminiClient",
                        "$modelName attempt ${attempt + 1} failed: ${e.message?.take(120)}",
                    )
                    if (attempt < MAX_RETRIES_PER_MODEL - 1) {
                        delay(INITIAL_RETRY_DELAY_MS * (attempt + 1))
                    }
                }
            }
        }

        throw lastError ?: IllegalStateException("No models available")
    }

    private fun createModel(
        modelName: String,
        systemInstruction: String,
        generationConfig: GenerationConfig,
    ) = GenerativeModel(
        modelName = modelName,
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig,
        systemInstruction = content { text(systemInstruction) },
    )

    private fun isQuotaExceeded(e: Exception): Boolean =
        e is QuotaExceededException ||
            e.message.orEmpty().contains("quota", ignoreCase = true) ||
            e.message.orEmpty().contains("RESOURCE_EXHAUSTED")

    private fun quotaRetryDelayMs(e: Exception): Long {
        val parsed = parseRetryAfterSeconds(e.message.orEmpty())
        val seconds = parsed ?: 30.0
        return (seconds * 1_000).toLong().coerceIn(1_000L, 60_000L)
    }

    private fun parseRetryAfterSeconds(message: String): Double? {
        val match = retryAfterSecondsPattern.find(message) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    private fun isRetryable(e: Exception): Boolean {
        if (isQuotaExceeded(e)) return false
        val msg = e.message.orEmpty()
        return msg.contains("503") ||
            msg.contains("UNAVAILABLE") ||
            msg.contains("429") ||
            msg.contains("MAX_TOKENS") ||
            msg.contains("high demand", ignoreCase = true) ||
            msg.contains("overloaded", ignoreCase = true) ||
            e is ResponseStoppedException
    }

    private fun isClearlyOffTopic(message: String): Boolean {
        val lower = message.lowercase().trim()
        if (lower.length < 2) return true
        if (disasterKeywords.any { lower.contains(it) }) return false
        return obviousOffTopicKeywords.any { lower.contains(it) }
    }

    private fun isOffTopicResponse(raw: String): Boolean {
        val normalized = raw.trim().uppercase()
        return normalized == OFF_TOPIC_TOKEN ||
            normalized.startsWith("$OFF_TOPIC_TOKEN ") ||
            normalized.endsWith(" $OFF_TOPIC_TOKEN")
    }

    /** Drop prior false rejections so they do not bias the next model reply. */
    private fun isStoredOffTopicRejection(content: String): Boolean =
        isOffTopicResponse(content) ||
            content.contains("I can only help with disasters", ignoreCase = true)

    private fun enforceShortReply(text: String): String {
        val withoutToken = text.replace(OFF_TOPIC_TOKEN, "", ignoreCase = true).trim()
        val sentences = withoutToken.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        return when {
            sentences.size <= 4 -> withoutToken
            else -> sentences.take(4).joinToString(" ").trim()
        }
    }

    private fun userFacingError(e: Exception): String {
        val msg = e.message.orEmpty()
        return when {
            isQuotaExceeded(e) -> quotaExceededMessage(msg)
            isRetryable(e) ->
                "The AI is busy right now. Please wait a moment and try again."
            msg.contains("404") || msg.contains("NOT_FOUND") ->
                "The AI assistant is temporarily unavailable. Please try again later."
            msg.contains("403") || msg.contains("API key", ignoreCase = true) ->
                "AI service is not configured. Add a valid GEMINI_API_KEY to local.properties."
            else ->
                "Sorry, I'm having trouble connecting right now. Please try again."
        }
    }

    private fun quotaExceededMessage(apiMessage: String): String {
        val retrySeconds = parseRetryAfterSeconds(apiMessage)
        return if (retrySeconds != null && retrySeconds <= 120) {
            val waitSec = ceil(retrySeconds).toInt().coerceAtLeast(1)
            "Free AI quota reached. Please wait about $waitSec seconds and try again."
        } else {
            "Free AI quota reached for now. Try again later, or create a new API key at Google AI Studio (aistudio.google.com)."
        }
    }
}
