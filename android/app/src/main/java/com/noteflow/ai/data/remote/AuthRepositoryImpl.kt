package com.noteflow.ai.data.remote

import android.content.Context
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.noteflow.ai.core.common.Logger
import com.noteflow.ai.core.common.Result
import com.noteflow.ai.domain.model.UserProfile
import com.noteflow.ai.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Authentication. The Google Sign-In credential flow lives in the
 * UI layer (SignInScreen uses Credential Manager with an Activity context,
 * which is the documented API) and hands this repository the resulting
 * Google ID token; exchanging that token for a Firebase session is here.
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

    /** Exchanges a Google ID token for a Firebase session (P0-17/P1-4 fix path). */
    override suspend fun signInWithIdToken(idToken: String): Result<UserProfile> {
        val auth = firebaseAuth
            ?: return Result.Failure(
                "Firebase is not configured. Add google-services.json (see android/README.md) to enable sign-in."
            )
        return try {
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user
                ?: return Result.Failure(FRIENDLY_SIGN_IN_FAILED)
            Result.Success(user.toProfile())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Firebase sign-in failed", e)
            Result.Failure(FRIENDLY_SIGN_IN_FAILED, e)
        }
    }

    override suspend fun signOut() {
        firebaseAuth?.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState() }
    }

    /**
     * Current Firebase ID token for backend/Supabase authorization, or null
     * when signed out. Tokens within [REFRESH_MARGIN_MS] of expiry are
     * refreshed proactively (P1-14 fix) so long-running sessions do not fail
     * with stale-token 401s.
     */
    override suspend fun idToken(): String? {
        val user = firebaseAuth?.currentUser ?: return null
        return runCatching {
            val result = user.getIdToken(false).await()
            val expiresAtMs = result.expirationTimestamp * 1000L
            if (expiresAtMs - System.currentTimeMillis() < REFRESH_MARGIN_MS) {
                user.getIdToken(true).await().token
            } else {
                result.token
            }
        }.onFailure { Logger.w(TAG, "Could not obtain ID token", it) }
            .getOrNull()
    }

    private fun FirebaseUser.toProfile(): UserProfile = UserProfile(
        uid = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl?.toString()
    )

    companion object {
        private const val TAG = "AuthRepository"
        private const val REFRESH_MARGIN_MS = 60_000L

        // User-facing message never contains raw exception text.
        private const val FRIENDLY_SIGN_IN_FAILED = "Sign-in failed. Please try again."
    }
}
