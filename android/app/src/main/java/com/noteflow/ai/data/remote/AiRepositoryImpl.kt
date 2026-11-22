package com.noteflow.ai.data.remote

import com.noteflow.ai.BuildConfig
import com.noteflow.ai.core.common.Logger
import com.noteflow.ai.core.common.Result
import com.noteflow.ai.core.common.SseParser
import com.noteflow.ai.domain.model.AiGeneratorType
import com.noteflow.ai.domain.model.AiMessage
import com.noteflow.ai.domain.model.AiQuickAction
import com.noteflow.ai.domain.model.AiRole
import com.noteflow.ai.domain.repository.AiRepository
import com.noteflow.ai.domain.repository.AuthRepository
import com.noteflow.ai.domain.repository.PreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
 * All AI traffic (streaming and non-streaming) targets the same base URL:
 * requests use the compile-time default and [BackendUrlInterceptor] rewrites
 * them to the user-configured backend when one is set in settings, so the
 * two paths can never diverge.
 *
 * Streaming uses a raw OkHttp call to POST /api/ai/chat/stream and parses
 * Server-Sent Events via [SseParser]. Cancelling the coroutine closes the
 * HTTP call, which aborts generation server-side.
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
            runCatchingResult(TAG) {
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
        val requestBody = json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(
                messages = messages.map { it.toDto() },
                systemContext = noteContext?.take(MAX_CONTEXT_CHARS),
                model = model
            )
        ).toRequestBody("application/json".toMediaType())

        val builder = Request.Builder()
            // BackendUrlInterceptor rewrites this to the user-configured
            // backend when one is set, keeping stream/non-stream consistent.
            .url(BuildConfig.BACKEND_URL + "api/ai/chat/stream")
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
                ?: return@withContext Result.Failure(FRIENDLY_EMPTY)
            val full = StringBuilder()
            while (!source.exhausted()) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line() ?: break
                when (val event = SseParser.parse(line)) {
                    is SseParser.Event.Delta -> {
                        full.append(event.text)
                        onChunk(event.text)
                    }
                    is SseParser.Event.Error -> return@withContext Result.Failure(event.message)
                    SseParser.Event.Done -> break
                    else -> Unit // start / comment / keep-alive / malformed line
                }
            }
            response.close()
            if (full.isEmpty()) {
                Result.Failure(FRIENDLY_EMPTY)
            } else {
                preferencesRepository.recordAiUsage()
                Result.Success(full.toString())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // Includes cancellation of the underlying call.
            Logger.w(TAG, "Stream transport failure", e)
            Result.Failure(FRIENDLY_NETWORK, e)
        } finally {
            runCatching { call.cancel() }
        }
    }

    override suspend fun runQuickAction(
        action: AiQuickAction,
        text: String,
        instructions: String?,
        model: String
    ): Result<String> = withContext(ioDispatcher) {
        runCatchingResult(TAG) {
            if (text.isBlank()) throw IllegalArgumentException("Select some text first")
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
        runCatchingResult(TAG) {
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
        runCatchingResult(TAG) { aiApi.health().status == "healthy" }
    }

    // -- internals -----------------------------------------------------------

    private fun AiMessage.toDto(): ChatMessageDto = ChatMessageDto(
        role = if (role == AiRole.USER) "user" else "assistant",
        content = content
    )

    /** Extracts the server-provided friendly error, or a generic message. Never raw exception text. */
    private fun parseError(body: String, code: Int): String {
        val parsed = runCatching {
            json.decodeFromString<ErrorResponse>(body).error
        }.getOrNull()
        return parsed ?: when (code) {
            401 -> "Please sign in to use the AI features"
            403 -> "AI access is not allowed for this account"
            429 -> "AI rate limit reached — try again in a minute"
            in 500..599 -> "The AI service is temporarily unavailable. Try again shortly."
            else -> "AI request failed (HTTP $code)"
        }
    }

    private inline fun <T> runCatchingResult(tag: String, block: () -> T): Result<T> = try {
        Result.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Result.Failure(parseError(e.response()?.errorBody()?.string().orEmpty(), e.code()), e)
    } catch (e: IllegalArgumentException) {
        Result.Failure(e.message ?: "Invalid AI request", e)
    } catch (e: IOException) {
        Logger.w(tag, "AI network failure", e)
        Result.Failure(FRIENDLY_NETWORK, e)
    } catch (e: Exception) {
        Logger.e(tag, "Unexpected AI failure", e)
        Result.Failure(FRIENDLY_UNEXPECTED, e)
    }

    companion object {
        private const val TAG = "AiRepository"
        private const val MAX_CONTEXT_CHARS = 10_000
        private const val MAX_ACTION_CHARS = 100_000
        private const val MAX_PROMPT_CHARS = 10_000

        // User-facing messages never contain raw exception text (P2-13 fix).
        private const val FRIENDLY_NETWORK = "Network unavailable. Check your connection and try again."
        private const val FRIENDLY_EMPTY = "The AI service returned an empty response. Try again."
        private const val FRIENDLY_UNEXPECTED = "Something went wrong. Please try again."
    }
}
