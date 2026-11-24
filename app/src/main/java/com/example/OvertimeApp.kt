package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class OvertimeApp : Application() {
    companion object {
        lateinit var instance: OvertimeApp
            private set

        private val _isMobileAdsInitialized = MutableStateFlow(false)
        val isMobileAdsInitialized: StateFlow<Boolean> = _isMobileAdsInitialized.asStateFlow()

        /**
         * Ensures MobileAds SDK has been given a chance to initialize even if boot-time
         * initialization ran while offline or was delayed on slow devices.
         */
        suspend fun ensureMobileAdsInitialized(context: Context, timeoutMs: Long = 2_000L): Boolean {
            if (_isMobileAdsInitialized.value) return true
            val result = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    try {
                        com.google.android.gms.ads.MobileAds.initialize(context.applicationContext) {
                            _isMobileAdsInitialized.value = true
                            if (cont.isActive) cont.resume(true)
                        }
                    } catch (e: Exception) {
                        Log.w("OvertimeApp", "On-demand AdMob initialization failed: ${e.message}")
                        if (cont.isActive) cont.resume(false)
                    }
                }
            }
            return result ?: _isMobileAdsInitialized.value
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            com.example.util.LanguageManager.ensureDefaultLanguageOnFirstInstall(this)
        } catch (e: Exception) {
            Log.w("OvertimeApp", "Default language initialization failed: ${e.message}")
        }
        try {
            com.example.receiver.TrashAutoDeleteScheduler.scheduleNextAutoDelete(this)
        } catch (e: Exception) {
            Log.w("OvertimeApp", "Trash auto-delete scheduling failed: ${e.message}")
        }
        try {
            FirebaseApp.initializeApp(this)
            AppCheckInstaller.install()
        } catch (e: Exception) {
            Log.w("OvertimeApp", "Firebase App Check initialization failed: ${e.message}")
        }
        try {
            com.google.android.gms.ads.MobileAds.initialize(this) {
                _isMobileAdsInitialized.value = true
            }
        } catch (e: Exception) {
            Log.w("OvertimeApp", "AdMob initialization failed: ${e.message}")
        }
    }
}
