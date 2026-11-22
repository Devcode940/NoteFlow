package com.noteflow.ai.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteflow.ai.core.common.Result
import com.noteflow.ai.core.common.Logger
import com.noteflow.ai.domain.model.AiGeneratorType
import com.noteflow.ai.domain.model.AiMessage
import com.noteflow.ai.domain.model.AiQuickAction
import com.noteflow.ai.domain.model.AiRole
import com.noteflow.ai.domain.repository.AiRepository
import com.noteflow.ai.domain.repository.AuthRepository
import com.noteflow.ai.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val authRepository: AuthRepository,
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<AiMessage>>(emptyList())
    val messages: StateFlow<List<AiMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var streamingJob: Job? = null

    /** The 12 quick actions. */
    val quickActions: List<AiQuickAction> = AiQuickAction.entries

    /** The 4 generators. */
    val generators: List<AiGeneratorType> = AiGeneratorType.entries

    /** Model comes from user settings, not a hardcoded default (P2-11 fix). */
    val aiModel: StateFlow<String> = preferencesRepository.settings
        .map { it.aiModel }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "gemini-2.5-flash")

    val isSignedIn: StateFlow<Boolean> = authRepository.currentUser
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun addUserMessage(content: String) {
        val message = AiMessage(
            id = UUID.randomUUID().toString(),
            role = AiRole.USER,
            content = content,
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + message
    }

    /** Streams an assistant reply; each [onChunk] is applied to the last message. */
    fun streamReply(
        noteContext: String?,
        model: String,
        onComplete: (String) -> Unit
    ) {
        if (_messages.value.isEmpty()) return
        _isGenerating.value = true
        _error.value = null
        val assistantId = UUID.randomUUID().toString()
        _messages.value = _messages.value + AiMessage(
            assistantId, AiRole.ASSISTANT, "", System.currentTimeMillis()
        )

        streamingJob = viewModelScope.launch {
            try {
                val result = aiRepository.chatStream(
                    messages = _messages.value,
                    noteContext = noteContext,
                    model = model,
                    onChunk = { chunk ->
                        appendToLastMessage(assistantId, chunk)
                    }
                )
                when (result) {
                    is Result.Success -> {
                        _error.value = null
                        onComplete(result.data)
                    }
                    is Result.Failure -> {
                        _error.value = result.message
                        removeMessage(assistantId)
                    }
                }
            } catch (e: CancellationException) {
                removeMessage(assistantId)
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "Streaming reply failed", e)
                _error.value = "Generation failed. Please try again."
                removeMessage(assistantId)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun runQuickAction(
        action: AiQuickAction,
        text: String,
        instructions: String?,
        model: String,
        onResult: (String) -> Unit
    ) {
        if (text.isBlank()) {
            _error.value = "Type or paste some text first"
            return
        }
        _isGenerating.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val result = aiRepository.runQuickAction(action, text, instructions, model)
                when (result) {
                    is Result.Success -> onResult(result.data)
                    is Result.Failure -> _error.value = result.message
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "Quick action failed", e)
                _error.value = "Quick action failed. Please try again."
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun generate(
        type: AiGeneratorType,
        prompt: String,
        noteContext: String?,
        model: String,
        onResult: (String) -> Unit
    ) {
        if (prompt.isBlank()) {
            _error.value = "Please enter a prompt"
            return
        }
        _isGenerating.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val result = aiRepository.generate(type, prompt, noteContext, model)
                when (result) {
                    is Result.Success -> onResult(result.data)
                    is Result.Failure -> _error.value = result.message
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "Generation failed", e)
                _error.value = "Generation failed. Please try again."
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun setError(message: String?) {
        _error.value = message
    }

    fun cancel() {
        streamingJob?.cancel()
        streamingJob = null
        _isGenerating.value = false
    }

    private fun appendToLastMessage(assistantId: String, chunk: String) {
        val current = _messages.value.toMutableList()
        val index = current.indexOfFirst { it.id == assistantId }
        if (index >= 0) {
            current[index] = current[index].copy(content = current[index].content + chunk)
            _messages.value = current
        }
    }

    private fun removeMessage(id: String) {
        _messages.value = _messages.value.filterNot { it.id == id }
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }

    companion object {
        private const val TAG = "AiAssistantVM"
    }
}
