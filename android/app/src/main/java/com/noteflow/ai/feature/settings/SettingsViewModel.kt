package com.noteflow.ai.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteflow.ai.domain.model.AppSettings
import com.noteflow.ai.domain.model.EditorMode
import com.noteflow.ai.domain.model.ExportFormat
import com.noteflow.ai.domain.model.ThemeMode
import com.noteflow.ai.domain.repository.AuthRepository
import com.noteflow.ai.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    private val defaultEditorMode = MutableStateFlow(EditorMode.EDIT)
    private val autoSave = MutableStateFlow(true)
    private val wordGoal = MutableStateFlow(500)
    private val aiModel = MutableStateFlow("gemini-2.5-flash")
    private val backendUrl = MutableStateFlow("")
    private val syncEnabled = MutableStateFlow(true)
    private val defaultExportFormat = MutableStateFlow(ExportFormat.MARKDOWN)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.settings,
        themeMode,
        defaultEditorMode,
        autoSave,
        wordGoal,
        aiModel,
        backendUrl,
        syncEnabled,
        defaultExportFormat,
        message,
        authRepository.currentUser
    ) { settings, tm, em, as, wg, am, bu, se, def, msg, user ->
        SettingsUiState(
            settings = settings,
            themeMode = tm,
            defaultEditorMode = em,
            autoSave = as,
            wordGoal = wg,
            aiModel = am,
            backendUrl = bu,
            syncEnabled = se,
            defaultExportFormat = def,
            message = msg,
            isSignedIn = user != null,
            userEmail = user?.email
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        // Seed UI state from persisted settings.
        preferencesRepository.settings.first().let { settings ->
            themeMode.value = settings.themeMode
            defaultEditorMode.value = settings.defaultEditorMode
            autoSave.value = settings.autoSave
            wordGoal.value = settings.wordGoal
            aiModel.value = settings.aiModel
            backendUrl.value = settings.backendUrl
            syncEnabled.value = settings.syncEnabled
            defaultExportFormat.value = settings.defaultExportFormat
        }
    }

    fun onThemeChange(mode: ThemeMode) {
        themeMode.value = mode
        preferencesRepository.setThemeMode(mode)
    }

    fun onEditorModeChange(mode: EditorMode) {
        defaultEditorMode.value = mode
        preferencesRepository.setDefaultEditorMode(mode)
    }

    fun onAutoSaveChange(enabled: Boolean) {
        autoSave.value = enabled
        preferencesRepository.setAutoSave(enabled)
    }

    fun onWordGoalChange(goal: Int) {
        wordGoal.value = goal
        preferencesRepository.setWordGoal(goal)
    }

    fun onAiModelChange(model: String) {
        aiModel.value = model
        preferencesRepository.setAiModel(model)
    }

    fun onBackendUrlChange(url: String) {
        backendUrl.value = url
        preferencesRepository.setBackendUrl(url)
    }

    fun onSyncEnabledChange(enabled: Boolean) {
        syncEnabled.value = enabled
        preferencesRepository.setSyncEnabled(enabled)
    }

    fun onExportFormatChange(format: ExportFormat) {
        defaultExportFormat.value = format
        preferencesRepository.setDefaultExportFormat(format)
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    fun consumeMessage() {
        message.value = null
    }
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultEditorMode: EditorMode = EditorMode.EDIT,
    val autoSave: Boolean = true,
    val wordGoal: Int = 500,
    val aiModel: String = "gemini-2.5-flash",
    val backendUrl: String = "",
    val syncEnabled: Boolean = true,
    val defaultExportFormat: ExportFormat = ExportFormat.MARKDOWN,
    val message: String? = null,
    val isSignedIn: Boolean = false,
    val userEmail: String? = null
)
