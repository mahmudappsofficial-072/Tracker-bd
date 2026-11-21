package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AuthManager {

    private const val TAG = "AuthManager"
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        try {
            _currentUser.value = auth.currentUser
            auth.addAuthStateListener { firebaseAuth ->
                _currentUser.value = firebaseAuth.currentUser
            }
        } catch (e: Exception) {
            Log.w(TAG, "Auth state listener setup failed: ${e.message}")
        }
    }

    fun getServerClientId(context: Context): String {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (resId != 0) {
            context.getString(resId)
        } else {
            "259151216518-9dc92cd0por9n5emi280be9enc5ki75v.apps.googleusercontent.com"
        }
    }

    fun signInWithGoogle(
        activity: Activity,
        scope: CoroutineScope,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val serverClientId = getServerClientId(activity)
        _isLoading.value = true

        scope.launch(Dispatchers.Main) {
            try {
                val credentialManager = CredentialManager.create(activity)

                val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = activity
                )

                val credential = result.credential
                if (credential is androidx.credentials.CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    val authCredential = GoogleAuthProvider.getCredential(idToken, null)

                    auth.signInWithCredential(authCredential)
                        .addOnSuccessListener { authResult ->
                            _isLoading.value = false
                            val user = authResult.user
                            if (user != null) {
                                _currentUser.value = user
                                onSuccess(user)
                            } else {
                                onError("Authentication failed")
                            }
                        }
                        .addOnFailureListener { e ->
                            _isLoading.value = false
                            onError(e.localizedMessage ?: "Sign-in failed")
                        }
                } else {
                    _isLoading.value = false
                    onError("Invalid credential format")
                }
            } catch (e: GetCredentialCancellationException) {
                _isLoading.value = false
                onCancel()
            } catch (e: Exception) {
                _isLoading.value = false
                onError(e.localizedMessage ?: "Sign-in error")
            }
        }
    }

    suspend fun signOutSuspend(context: Context? = null) = withContext(NonCancellable) {
        try {
            auth.signOut()
            _currentUser.value = null
            context?.let { ctx ->
                withContext(Dispatchers.IO) {
                    try {
                        val credentialManager = CredentialManager.create(ctx)
                        credentialManager.clearCredentialState(ClearCredentialStateRequest())
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Failed clearing credential state: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Sign-out failed: ${e.message}")
        }
    }

    fun signOut(context: Context? = null) {
        try {
            auth.signOut()
            _currentUser.value = null
            context?.let { ctx ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val credentialManager = CredentialManager.create(ctx)
                        credentialManager.clearCredentialState(ClearCredentialStateRequest())
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed clearing credential state: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sign-out failed: ${e.message}")
        }
    }
}
