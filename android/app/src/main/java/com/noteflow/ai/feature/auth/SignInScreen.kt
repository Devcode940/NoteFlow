package com.noteflow.ai.feature.auth

import android.app.Activity
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetGoogleIdOption
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.noteflow.ai.BuildConfig
import com.noteflow.ai.core.common.Logger
import kotlinx.coroutines.launch

/**
 * Google Sign-In via Credential Manager.
 *
 * The account-selection flow lives here because Credential Manager needs an
 * Activity context (documented API); the resulting Google ID token is handed
 * to [SignInViewModel] which exchanges it for a Firebase session. On success
 * the screen invokes [onSignedIn] exactly once (P1-4 fix — previously the
 * callback was never called and the user was stranded on this screen).
 */
@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel()
) {
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val signedIn by viewModel.signedIn.collectAsState()
    val isFirebaseConfigured = viewModel.isFirebaseConfigured
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(signedIn) {
        if (signedIn) onSignedIn()
    }

    fun startSignIn() {
        val activity = context as? Activity
        if (activity == null) {
            viewModel.reportError("Sign-in is not available right now")
            return
        }
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (webClientId.isBlank()) {
            viewModel.reportError(
                "Google Sign-In is not configured. Set noteflow.googleWebClientId in gradle.properties."
            )
            return
        }
        viewModel.setLoading(true)
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()
        val request = GetCredentialRequest(googleIdOption)
        scope.launch {
            try {
                val response = credentialManager.getCredential(activity, request)
                val idToken = androidx.credentials.playservices.GoogleIdTokenCredential
                    .createFrom(response.credential.data)
                    .idToken
                viewModel.signInWithIdToken(idToken)
            } catch (e: GetCredentialCancellationException) {
                viewModel.setLoading(false) // user dismissed the dialog — not an error
            } catch (e: GetCredentialException) {
                Logger.w("SignIn", "Credential Manager error", e)
                viewModel.setLoading(false)
                viewModel.reportError("Google Sign-In failed. Please try again.")
            } catch (e: Exception) {
                Logger.e("SignIn", "Unexpected sign-in error", e)
                viewModel.setLoading(false)
                viewModel.reportError("Sign-in failed. Please try again.")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Icon(
            Icons.Default.Login,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Sign in with Google",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your notes sync across devices and AI requests go through a secure backend.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (!isFirebaseConfigured) {
            Text(
                text = "Firebase is not configured. Running in local-only mode — add google-services.json (see android/README.md) to enable sign-in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        Button(
            onClick = { startSignIn() },
            enabled = !loading && isFirebaseConfigured,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Continue with Google")
            }
        }
        error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
