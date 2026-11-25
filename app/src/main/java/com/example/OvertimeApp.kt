package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

class OvertimeApp : Application() {
    companion object {
        lateinit var instance: OvertimeApp
            private set

        private val _isMobileAdsInitialized = MutableStateFlow(false)
        val isMobileAdsInitialized: StateFlow<Boolean> = _isMobileAdsInitialized.asStateFlow()
        private val isMobileAdsInitStarted = AtomicBoolean(false)

        /**
         * Ensures MobileAds SDK is initialized once and awaits completion up to [timeoutMs]
         * without firing conflicting duplicate initialize() calls.
         */
        suspend fun ensureMobileAdsInitialized(context: Context, timeoutMs: Long = 3_500L): Boolean {
            if (_isMobileAdsInitialized.value) return true
            if (isMobileAdsInitStarted.compareAndSet(false, true)) {
                withContext(Dispatchers.Main.immediate) {
                    try {
                        com.google.android.gms.ads.MobileAds.initialize(context.applicationContext) {
                            _isMobileAdsInitialized.value = true
                        }
                    } catch (e: Exception) {
                        isMobileAdsInitStarted.set(false)
                        Log.w("OvertimeApp", "On-demand AdMob initialization failed: ${e.message}")
                    }
                }
            }
            val ready = withTimeoutOrNull(timeoutMs) {
                _isMobileAdsInitialized.first { it }
            }
            return ready == true || _isMobileAdsInitialized.value
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
            isMobileAdsInitStarted.set(true)
            com.google.android.gms.ads.MobileAds.initialize(this) {
                _isMobileAdsInitialized.value = true
            }
        } catch (e: Exception) {
            isMobileAdsInitStarted.set(false)
            Log.w("OvertimeApp", "AdMob initialization failed: ${e.message}")
        }
    }
}
