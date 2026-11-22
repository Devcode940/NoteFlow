package com.noteflow.ai.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteflow.ai.domain.model.UserProfile
import com.noteflow.ai.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    val currentUser: StateFlow<UserProfile?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun signOut() = viewModelScope.launch { authRepository.signOut() }
}

/** Account overview. The host NavHost provides the screen title bar. */
@Composable
fun ProfileScreen(
    onOpenSignIn: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val user by viewModel.currentUser.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = user?.displayName?.ifBlank { null } ?: user?.email ?: "Not signed in",
            style = MaterialTheme.typography.titleLarge
        )
        if (user?.email != null && user?.displayName?.isNotBlank() == true) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = user?.email.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(32.dp))

        if (user != null) {
            ProfileAction(icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                headline = "Account settings", onClick = onOpenSettings)
            ProfileAction(icon = { Icon(Icons.Default.Logout, contentDescription = null) },
                headline = "Sign out", onClick = { viewModel.signOut() })
        } else {
            ProfileAction(icon = { Icon(Icons.Default.Login, contentDescription = null) },
                headline = "Sign in", onClick = onOpenSignIn)
        }
        ProfileAction(icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            headline = "App settings", onClick = onOpenSettings)
    }
}

@Composable
private fun ProfileAction(
    icon: @Composable () -> Unit,
    headline: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        icon()
        Spacer(modifier = Modifier.padding(end = 12.dp))
        Text(headline, style = MaterialTheme.typography.bodyLarge)
    }
}
