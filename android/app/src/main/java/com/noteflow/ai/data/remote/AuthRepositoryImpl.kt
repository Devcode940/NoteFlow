package com.noteflow.ai.data.remote

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetGoogleIdOption
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.playservices.GoogleIdTokenCredential
import androidx.credentials.playservices.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.noteflow.ai.BuildConfig
import com.noteflow.ai.core.common.Result
import com.noteflow.ai.domain.model.UserProfile
import com.noteflow.ai.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Authentication with Google Sign-In via Credential Manager.
 *
 * If google-services.json is absent (offline/local build), Firebase is not
 * initialized: [isFirebaseConfigured] is false, sign-in returns a clear
 * failure, and the app runs in local-only mode. No insecure shortcuts are
 * taken — there is no way to "sign in" without real Firebase credentials.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AuthRepository {

    private val firebaseAuth: FirebaseAuth? = runCatching { Firebase.auth }.getOrNull()

    override val isFirebaseConfigured: Boolean get() = firebaseAuth != null

    override val currentUser: Flow<UserProfile?> = callbackFlow {
        val auth = firebaseAuth
        if (auth == null) {
            send(null)
            close()
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { firebase ->
            trySend(firebase.currentUser?.toProfile())
        }
        auth.addAuthStateListener(listener)
        send(auth.currentUser?.toProfile())
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signInWithGoogle(): Result<UserProfile> {
        val auth = firebaseAuth
            ?: return Result.Failure(
                "Firebase is not configured. Add google-services.json (see android/README.md) to enable sign-in."
            )
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (webClientId.isBlank()) {
            return Result.Failure(
                "Google Sign-In is not configured. Set noteflow.googleWebClientId in gradle.properties."
            )
        }
        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()
            val request = GetCredentialRequest(googleIdOption)
            val response = credentialManager.getCredential(request)
            val credential = response.credential
            if (credential !is GoogleIdTokenCredential) {
                return Result.Failure("Unexpected credential type from Google Sign-In")
            }
            val firebaseCredential = GoogleAuthProvider.getCredential(credential.idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user
                ?: return Result.Failure("Firebase sign-in returned no user")
            Result.Success(user.toProfile())
        } catch (e: GetCredentialCancellationException) {
            Result.Failure("Sign-in cancelled", e)
        } catch (e: NoCredentialException) {
            Result.Failure("No Google account found on this device", e)
        } catch (e: GetCredentialProviderException) {
            Result.Failure("Google Sign-In provider error: ${e.message}", e)
        } catch (e: GoogleIdTokenParsingException) {
            Result.Failure("Could not parse Google ID token", e)
        } catch (e: Exception) {
            Result.Failure("Sign-in failed: ${e.message}", e)
        }
    }

    override suspend fun signOut() {
        firebaseAuth?.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState() }
    }

    override suspend fun idToken(): String? {
        val user = firebaseAuth?.currentUser ?: return null
        return runCatching { user.getIdToken(false).await().token }.getOrNull()
    }

    private fun FirebaseUser.toProfile(): UserProfile = UserProfile(
        uid = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl?.toString()
    )
}
