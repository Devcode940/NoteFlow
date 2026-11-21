package com.noteflow.ai.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    val currentUser = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun signOut() = viewModelScope.launch { authRepository.signOut() }
}

@Composable
fun ProfileScreen(
    onOpenSignIn: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val user by viewModel.currentUser.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        androidx.compose.material3.TopAppBar(
            title = { Text("User Profile") },
            navigationIcon = {
                IconButton(onClick = { /* handled by host */ }) {
                    Icon(ArrowBack, contentDescription = "Back")
                }
            }
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(80.dp)
            )
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                text = user?.displayName ?: user?.email ?: "Not signed in",
                style = MaterialTheme.typography.titleLarge
            )
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                text = user?.email ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))
        if (user != null) {
            ListItem(
                headline = { Text("Account settings") },
                onClick = onOpenSettings,
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
            )
            ListItem(
                headline = { Text("Sign out") },
                leadingIcon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null) },
                onClick = { viewModel.signOut() }
            )
        } else {
            ListItem(
                headline = { Text("Sign in") },
                leadingIcon = { Icon(Icons.Default.Login, contentDescription = null) },
                onClick = onOpenSignIn
            )
        }
        ListItem(
            headline = { Text("App settings") },
            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = onOpenSettings
        )
    }
}
