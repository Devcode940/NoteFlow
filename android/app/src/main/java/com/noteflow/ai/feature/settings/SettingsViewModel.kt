package com.noteflow.ai.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteflow.ai.core.common.Logger
import com.noteflow.ai.domain.model.AppSettings
import com.noteflow.ai.domain.model.EditorMode
import com.noteflow.ai.domain.model.ExportFormat
import com.noteflow.ai.domain.model.ThemeMode
import com.noteflow.ai.domain.repository.AuthRepository
import com.noteflow.ai.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single source of truth for settings is DataStore ([preferencesRepository]);
 * the ViewModel exposes it directly instead of mirroring it into parallel
 * MutableStateFlows. All mutations persist immediately and report failures
 * through [SettingsUiState.message].
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.settings,
        authRepository.currentUser,
        message
    ) { settings, user, msg ->
        SettingsUiState(
            settings = settings,
            isSignedIn = user != null,
            userEmail = user?.email,
            message = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onThemeChange(mode: ThemeMode) = persist("theme") { preferencesRepository.setThemeMode(mode) }

    fun onEditorModeChange(mode: EditorMode) = persist("editor mode") {
        preferencesRepository.setDefaultEditorMode(mode)
    }

    fun onAutoSaveChange(enabled: Boolean) = persist("auto-save") {
        preferencesRepository.setAutoSave(enabled)
    }

    fun onWordGoalChange(goal: Int) = persist("word goal") {
        preferencesRepository.setWordGoal(goal.coerceIn(0, 100_000))
    }

    fun onAiModelChange(model: String) = persist("AI model") {
        preferencesRepository.setAiModel(model.trim())
    }

    fun onBackendUrlChange(url: String) = persist("backend URL") {
        val normalized = url.trim().trimEnd('/')
        if (normalized.isNotBlank() && !normalized.startsWith("https://") && !normalized.startsWith("http://")) {
            message.value = "Backend URL must start with http:// or https://"
            return@persist
        }
        preferencesRepository.setBackendUrl(normalized)
    }

    fun onSyncEnabledChange(enabled: Boolean) = persist("sync") {
        preferencesRepository.setSyncEnabled(enabled)
    }

    fun onExportFormatChange(format: ExportFormat) = persist("export format") {
        preferencesRepository.setDefaultExportFormat(format)
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                authRepository.signOut()
                message.value = "Signed out"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "Sign out failed", e)
                message.value = "Could not sign out. Please try again."
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    private fun persist(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(TAG, "Persisting $label failed", e)
                message.value = "Could not save $label"
            }
        }
    }

    companion object {
        private const val TAG = "SettingsVM"
    }
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isSignedIn: Boolean = false,
    val userEmail: String? = null,
    val message: String? = null
)
