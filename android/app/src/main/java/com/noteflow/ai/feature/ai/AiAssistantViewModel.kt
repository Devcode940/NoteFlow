package com.noteflow.ai.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteflow.ai.core.common.Result
import com.noteflow.ai.domain.model.AiGeneratorType
import com.noteflow.ai.domain.model.AiMessage
import com.noteflow.ai.domain.model.AiQuickAction
import com.noteflow.ai.domain.model.AiRole
import com.noteflow.ai.domain.repository.AuthRepository
import com.noteflow.ai.domain.repository.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<AiMessage>>(emptyList())
    val messages: StateFlow<List<AiMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var streamingJob: kotlinx.coroutines.Job? = null

    /** The 12 quick actions. */
    val quickActions: List<AiQuickAction> = AiQuickAction.entries

    /** The 4 generators. */
    val generators: List<AiGeneratorType> = AiGeneratorType.entries

    fun addUserMessage(content: String) {
        val message = AiMessage(
            id = java.util.UUID.randomUUID().toString(),
            role = AiRole.USER,
            content = content,
            timestamp = System.currentTimeMillis()
        )
        _messages.update { it + message }
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
        val assistantId = java.util.UUID.randomUUID().toString()
        _messages.update { it + AiMessage(assistantId, AiRole.ASSISTANT, "", System.currentTimeMillis()) }

        streamingJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = aiRepository.chatStream(
                    messages = _messages.value,
                    noteContext = noteContext,
                    model = model,
                    onChunk = { chunk ->
                        _messages.update { last ->
                            val updated = last.toMutableList()
                            val idx = updated.lastIndex
                            updated[idx] = updated[idx].copy(content = updated[idx].content + chunk)
                            updated
                        }
                    }
                )
                when (result) {
                    is Result.Success -> {
                        _error.value = null
                        onComplete(result.data)
                    }
                    is Result.Failure -> {
                        _error.value = result.message
                        _messages.update { it.filterNot { it.id == assistantId } }
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Generation failed"
                _messages.update { it.filterNot { it.id == assistantId } }
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
            _error.value = "No text to process"
            return
        }
        _isGenerating.value = true
        _error.value = null
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = aiRepository.runQuickAction(action, text, instructions, model)
                when (result) {
                    is Result.Success -> onResult(result.data)
                    is Result.Failure -> _error.value = result.message
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Quick action failed"
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
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = aiRepository.generate(type, prompt, noteContext, model)
                when (result) {
                    is Result.Success -> onResult(result.data)
                    is Result.Failure -> _error.value = result.message
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Generation failed"
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
        _isGenerating.value = false
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }
}

data class AiAssistantUiState(
    val messages: List<AiMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val error: String? = null
)
