package com.noteflow.ai.core.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses Server-Sent Events (`data: ...` lines) from the AI backend.
 *
 * The backend emits a strict JSON envelope:
 *   data: {"type":"start"}
 *   data: {"type":"delta","text":"..."}
 *   data: {"type":"error","message":"..."}
 *   data: [DONE]
 *
 * Non-`data:` lines (comments, keep-alives) and malformed payloads are
 * ignored rather than surfaced as text — this guards against the historical
 * defect where raw JSON fragments were appended verbatim to chat output.
 */
object SseParser {

    sealed interface Event {
        data object Start : Event
        data class Delta(val text: String) : Event
        data class Error(val message: String) : Event
        data object Done : Event
    }

    @Serializable
    private data class Envelope(
        val type: String = "",
        val text: String = "",
        val message: String = ""
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Returns the event encoded in [line], or null when the line carries no event. */
    fun parse(line: String): Event? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(":")) return null
        if (!trimmed.startsWith("data:")) return null
        val payload = trimmed.removePrefix("data:").trim()
        if (payload.isEmpty()) return null
        if (payload == "[DONE]") return Event.Done
        val envelope = runCatching { json.decodeFromString(Envelope.serializer(), payload) }
            .getOrNull()
            ?: return null // malformed payload — never append raw fragments
        return when (envelope.type) {
            "start" -> Event.Start
            "delta" -> if (envelope.text.isEmpty()) null else Event.Delta(envelope.text)
            "error" -> Event.Error(envelope.message.ifBlank { "AI backend error" })
            "done" -> Event.Done
            else -> null
        }
    }
}
