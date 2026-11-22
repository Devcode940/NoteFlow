package com.noteflow.ai.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * DTOs for the secure AI backend (repo root /server.ts).
 * The backend holds GEMINI_API_KEY server-side; the app only sends prompts.
 */
@Serializable
data class ChatRequest(
    val messages: List<ChatMessageDto>,
    val systemContext: String? = null,
    val model: String? = null
)

@Serializable
data class ChatMessageDto(val role: String, val content: String)

@Serializable
data class ChatResponse(val content: String)

@Serializable
data class ActionRequest(
    val action: String,
    val text: String,
    val instructions: String? = null,
    val model: String? = null
)

@Serializable
data class ActionResponse(val result: String)

@Serializable
data class GenerateRequest(
    val type: String,
    val prompt: String,
    val noteContext: String? = null,
    val model: String? = null
)

@Serializable
data class GenerateResponse(val result: String)

@Serializable
data class HealthResponse(
    val status: String,
    @SerialName("timestamp") val timestamp: String? = null
)

@Serializable
data class ErrorResponse(val error: String)

/**
 * Retrofit interface for the AI backend. Streaming chat uses a raw OkHttp
 * call (see AiRepositoryImpl) because Retrofit does not stream SSE lines.
 */
interface AiApi {
    @POST("api/ai/chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse

    @POST("api/ai/action")
    suspend fun runAction(@Body request: ActionRequest): ActionResponse

    @POST("api/ai/generate")
    suspend fun generate(@Body request: GenerateRequest): GenerateResponse

    @GET("api/health")
    suspend fun health(): HealthResponse
}
