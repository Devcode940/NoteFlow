package com.noteflow.ai.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noteflow.ai.core.common.Logger
import com.noteflow.ai.core.common.Result
import com.noteflow.ai.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sign-in state holder. The UI performs the Credential Manager account
 * selection (it needs an Activity context) and hands the resulting Google ID
 * token to [signInWithIdToken]; this ViewModel owns loading/error state and
 * emits [signedIn] exactly once on success so navigation can proceed (P1-4
 * fix — previously the success callback was never invoked).
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    val isFirebaseConfigured: Boolean get() = authRepository.isFirebaseConfigured

    /** Exchanges the Google ID token for a Firebase session. */
    fun signInWithIdToken(idToken: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                when (val result = authRepository.signInWithIdToken(idToken)) {
                    is Result.Success -> _signedIn.value = true
                    is Result.Failure -> _error.value = result.message
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, "Sign-in exchange failed", e)
                _error.value = "Sign-in failed. Please try again."
            } finally {
                _loading.value = false
            }
        }
    }

    fun setLoading(value: Boolean) {
        _loading.value = value
    }

    fun reportError(message: String?) {
        _error.value = message
    }

    fun consumeError() {
        _error.value = null
    }

    companion object {
        private const val TAG = "SignInVM"
    }
}
