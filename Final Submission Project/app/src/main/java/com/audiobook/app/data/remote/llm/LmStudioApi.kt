package com.audiobook.app.data.remote.llm

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit service for LM Studio's OpenAI-compatible local server.
 *
 * LM Studio exposes the same surface as the OpenAI API (default
 * http://localhost:1234), so these DTOs follow the OpenAI chat-completions
 * schema. Keeping them here, separate from [ChatMessage], means the wire format
 * is an implementation detail of the LM Studio provider only.
 */
interface LmStudioApi {

    @POST("v1/chat/completions")
    suspend fun chatCompletions(@Body request: ChatCompletionRequest): ChatCompletionResponse

    @GET("v1/models")
    suspend fun listModels(): ModelsResponse
}

// ──────────────────── request/response DTOs ────────────────────

data class ChatCompletionRequest(
    val model: String,
    val messages: List<WireMessage>,
    val temperature: Float = 0.7f,
    val stream: Boolean = false
)

data class WireMessage(
    val role: String,
    val content: String
)

data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList()
)

data class Choice(
    val message: WireMessage? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class ModelsResponse(
    val data: List<ModelInfo> = emptyList()
)

data class ModelInfo(
    val id: String = ""
)

// Streaming (SSE) chunk shape: {"choices":[{"delta":{"content":"..."}}]}

data class ChatStreamChunk(
    val choices: List<StreamChoice> = emptyList()
)

data class StreamChoice(
    val delta: Delta? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class Delta(
    val role: String? = null,
    val content: String? = null
)
