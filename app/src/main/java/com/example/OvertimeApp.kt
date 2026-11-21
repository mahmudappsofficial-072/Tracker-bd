package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OvertimeApp : Application() {
    companion object {
        lateinit var instance: OvertimeApp
            private set

        private val _isMobileAdsInitialized = MutableStateFlow(false)
        val isMobileAdsInitialized: StateFlow<Boolean> = _isMobileAdsInitialized.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
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
