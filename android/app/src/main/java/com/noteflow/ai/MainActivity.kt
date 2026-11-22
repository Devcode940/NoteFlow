package com.noteflow.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteflow.ai.core.designsystem.NoteFlowTheme
import com.noteflow.ai.domain.model.AppSettings
import com.noteflow.ai.domain.model.ThemeMode
import com.noteflow.ai.domain.repository.PreferencesRepository
import com.noteflow.ai.navigation.NoteFlowNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsState()
            NoteFlowTheme(themeMode = settings.themeMode) {
                NoteFlowNavHost()
            }
        }
    }
}

/** Minimal ViewModel exposing app settings (theme) to MainActivity. */
class SettingsViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = preferencesRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
}
