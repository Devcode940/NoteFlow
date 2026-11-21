package com.noteflow.ai.feature.editor

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Voice dictation via Android SpeechRecognizer.
 *
 * Dictation is interactive only: it starts when the user taps the microphone
 * button and stops on tap or after the recognizer finishes. No continuous or
 * background recording is performed. Microphone permission must be granted
 * by the caller (see NoteEditorScreen).
 */
class VoiceDictation(private val context: Context) {

    interface Listener {
        fun onResult(text: String)
        fun onError(message: String)
    }

    private var recognizer: SpeechRecognizer? = null
    private var listener: Listener? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    /** Starts dictation. Returns false when no recognition service exists. */
    fun start(listener: Listener): Boolean {
        if (!isAvailable) {
            listener.onError("Speech recognition is not available on this device")
            return false
        }
        this.listener = listener
        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speechRecognizer
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out. Try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Microphone permission is required for dictation."
                    SpeechRecognizer.ERROR_NETWORK -> "Network error during recognition."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Recognition timed out."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy. Try again."
                    else -> "Speech recognition failed (error $error)."
                }
                listener.onError(message)
                destroy()
            }
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    listener.onResult(text)
                } else {
                    listener.onError("No speech detected. Try again.")
                }
                destroy()
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        return try {
            speechRecognizer.startListening(intent)
            true
        } catch (e: Exception) {
            listener.onError("Could not start speech recognition: ${e.message}")
            destroy()
            false
        }
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
        destroy()
    }

    fun destroy() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        listener = null
    }
}
