package com.noteflow.ai.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.noteflow.ai.domain.repository.AuthRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    val isFirebaseConfigured: Boolean = authRepository.isFirebaseConfigured
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun signIn() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = authRepository.signInWithGoogle()) {
                is com.noteflow.ai.core.common.Result.Success -> {
                    // Signed in; host navigates back.
                }
                is com.noteflow.ai.core.common.Result.Failure -> {
                    _error.value = result.message
                }
            }
            _loading.value = false
        }
    }
}

data class SignInUiState(
    val loading: Boolean = false,
    val error: String? = null
)

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel()
) {
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isFirebaseConfigured = viewModel.isFirebaseConfigured

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        androidx.compose.material3.TopAppBar(
            title = { Text("Sign in") },
            navigationIcon = {
                androidx.compose.material3.IconButton(onClick = { /* handled by host */ }) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            androidx.compose.material.icons.Icons.Default.Email?.let {
                androidx.compose.material3.Icon(it, contentDescription = null, modifier = Modifier.size(48.dp))
            }
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Sign in with Google",
            style = MaterialTheme.typography.titleLarge
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your notes sync across devices and AI requests go through a secure backend.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
        if (!isFirebaseConfigured) {
            Text(
                text = "Firebase is not configured. Running in local-only mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        }
        Button(
            onClick = { viewModel.signIn() },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator()
            } else {
                Text("Continue with Google")
            }
        }
        error?.let {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

