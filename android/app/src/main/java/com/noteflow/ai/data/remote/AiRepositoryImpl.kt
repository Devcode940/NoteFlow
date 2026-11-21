package com.noteflow.ai.data.remote

import com.noteflow.ai.core.common.Result
import com.noteflow.ai.domain.model.AiGeneratorType
import com.noteflow.ai.domain.model.AiMessage
import com.noteflow.ai.domain.model.AiQuickAction
import com.noteflow.ai.domain.model.AiRole
import com.noteflow.ai.domain.repository.AiRepository
import com.noteflow.ai.domain.repository.AuthRepository
import com.noteflow.ai.domain.repository.PreferencesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Talks to the secure AI backend only. No API keys exist in this process:
 * requests carry the user's Firebase ID token (when signed in) and the
 * backend verifies it before calling Gemini with its server-side key.
 *
 * Streaming uses a raw OkHttp call to POST /api/ai/chat/stream and parses
 * Server-Sent Events (`data: ...` lines). Cancelling the coroutine closes
 * the HTTP call, which aborts generation server-side.
 */
@Singleton
class AiRepositoryImpl @Inject constructor(
    private val aiApi: AiApi,
    private val okHttpClient: OkHttpClient,
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
    @Named("io") private val ioDispatcher: CoroutineDispatcher
) : AiRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun chat(messages: List<AiMessage>, noteContext: String?, model: String): Result<String> =
        withContext(ioDispatcher) {
            runCatchingResult {
                val response = aiApi.chat(
                    ChatRequest(
                        messages = messages.map { it.toDto() },
                        systemContext = noteContext?.take(MAX_CONTEXT_CHARS),
                        model = model
                    )
                )
                preferencesRepository.recordAiUsage()
                response.content
            }
        }

    override suspend fun chatStream(
        messages: List<AiMessage>,
        noteContext: String?,
        model: String,
        onChunk: (String) -> Unit
    ): Result<String> = withContext(ioDispatcher) {
        val baseUrl = resolveBaseUrl()
        val requestBody = json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(
                messages = messages.map { it.toDto() },
                systemContext = noteContext?.take(MAX_CONTEXT_CHARS),
                model = model
            )
        ).toRequestBody("application/json".toMediaType())

        val builder = Request.Builder()
            .url(baseUrl + "api/ai/chat/stream")
            .post(requestBody)
            .header("Accept", "text/event-stream")
        authRepository.idToken()?.let { builder.header("Authorization", "Bearer $it") }

        val call = okHttpClient.newCall(builder.build())
        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                return@withContext Result.Failure(parseError(errorBody, response.code))
            }
            val source = response.body?.source()
                ?: return@withContext Result.Failure("Empty response from AI backend")
            val full = StringBuilder()
            while (!source.exhausted()) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data:")) {
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    if (payload.isNotEmpty()) {
                        full.append(payload)
                        onChunk(payload)
                    }
                }
            }
            response.close()
            if (full.isEmpty()) {
                Result.Failure("AI backend returned an empty response")
            } else {
                preferencesRepository.recordAiUsage()
                Result.Success(full.toString())
            }
        } catch (e: IOException) {
            // Includes cancellation of the underlying call.
            Result.Failure("AI request failed: ${e.message}", e)
        } finally {
            if (call.isExecuted()) call.cancel()
        }
    }

    override suspend fun runQuickAction(
        action: AiQuickAction,
        text: String,
        instructions: String?,
        model: String
    ): Result<String> = withContext(ioDispatcher) {
        runCatchingResult {
            if (text.isBlank()) throw IllegalArgumentException("No text selected for the AI action")
            if (text.length > MAX_ACTION_CHARS) {
                throw IllegalArgumentException("Selected text is too long (max ${MAX_ACTION_CHARS / 1000}k characters)")
            }
            val response = aiApi.runAction(
                ActionRequest(
                    action = action.key,
                    text = text,
                    instructions = instructions?.take(500),
                    model = model
                )
            )
            preferencesRepository.recordAiUsage()
            response.result
        }
    }

    override suspend fun generate(
        type: AiGeneratorType,
        prompt: String,
        noteContext: String?,
        model: String
    ): Result<String> = withContext(ioDispatcher) {
        runCatchingResult {
            if (prompt.isBlank()) throw IllegalArgumentException("Describe what to generate")
            val response = aiApi.generate(
                GenerateRequest(
                    type = type.key,
                    prompt = prompt.take(MAX_PROMPT_CHARS),
                    noteContext = noteContext?.take(MAX_CONTEXT_CHARS),
                    model = model
                )
            )
            preferencesRepository.recordAiUsage()
            response.result
        }
    }

    override suspend fun checkHealth(): Result<Boolean> = withContext(ioDispatcher) {
        runCatchingResult { aiApi.health().status == "healthy" }
    }

    // -- internals -----------------------------------------------------------

    private suspend fun resolveBaseUrl(): String {
        val configured = preferencesRepository.settings.first().backendUrl
        val base = configured.ifBlank { com.noteflow.ai.BuildConfig.BACKEND_URL }
        return if (base.endsWith("/")) base else "$base/"
    }

    private fun AiMessage.toDto(): ChatMessageDto = ChatMessageDto(
        role = if (role == AiRole.USER) "user" else "assistant",
        content = content
    )

    private fun parseError(body: String, code: Int): String {
        val parsed = runCatching {
            json.decodeFromString<ErrorResponse>(body).error
        }.getOrNull()
        return parsed ?: "AI backend error (HTTP $code)"
    }

    private inline fun <T> runCatchingResult(block: () -> T): Result<T> = try {
        Result.Success(block())
    } catch (e: HttpException) {
        Result.Failure(parseError(e.response()?.errorBody()?.string().orEmpty(), e.code()), e)
    } catch (e: IllegalArgumentException) {
        Result.Failure(e.message ?: "Invalid AI request", e)
    } catch (e: IOException) {
        Result.Failure("Network error: ${e.message}", e)
    } catch (e: Exception) {
        Result.Failure("AI request failed: ${e.message}", e)
    }

    companion object {
        private const val MAX_CONTEXT_CHARS = 10_000
        private const val MAX_ACTION_CHARS = 100_000
        private const val MAX_PROMPT_CHARS = 10_000
    }
}
