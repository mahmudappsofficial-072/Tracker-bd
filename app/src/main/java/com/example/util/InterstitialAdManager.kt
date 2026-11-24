package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.example.BuildConfig
import com.example.OvertimeApp
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Pure, testable AdMob + Play Console policy gate for on-demand Interstitial ads.
 */
object InterstitialAdGatePolicy {
    const val DEBUG_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val RELEASE_GENERAL_AD_UNIT_ID = "ca-app-pub-9027708775227838/5097750056"
    const val AD_LOAD_TIMEOUT_MS = 10_000L
    const val INTERSTITIAL_COOLDOWN_MS = 60_000L

    fun resolveAdUnitId(isDebug: Boolean = BuildConfig.DEBUG): String {
        return if (isDebug) DEBUG_TEST_AD_UNIT_ID else RELEASE_GENERAL_AD_UNIT_ID
    }

    fun shouldRequestInterstitial(
        isOnline: Boolean,
        isMobileAdsInitialized: Boolean,
        nowMs: Long,
        lastShownAtMs: Long,
        cooldownMs: Long = INTERSTITIAL_COOLDOWN_MS
    ): Boolean {
        if (!isOnline || !isMobileAdsInitialized) return false
        if (lastShownAtMs > 0L && (nowMs - lastShownAtMs) in 0 until cooldownMs) return false
        return true
    }

    fun canShowLoadedAd(
        isActivityAlive: Boolean,
        isActivityResumed: Boolean,
        isTimedOutOrCancelled: Boolean
    ): Boolean {
        return isActivityAlive && isActivityResumed && !isTimedOutOrCancelled
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * On-demand Interstitial Ad coordinator ("General-Interstitial") for Pay Slip PDF downloads.
 * - Never preloads upon entering screens; loads strictly on explicit button tap when online.
 * - Uses bounded 10s load timeout, no-fill pass-through, foreground lifecycle checks,
 *   single-use FullScreenContentCallback cleanup, and 60s anti-spam cooldown.
 */
object GeneralInterstitialAdManager {
    private const val TAG = "GeneralInterstitialAd"

    @Volatile
    private var lastAdShownAtMs: Long = 0L

    private fun isActivityAliveAndResumed(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        val lifecycleOwner = activity as? LifecycleOwner ?: return true
        return lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }

    suspend fun showIfEligibleAndAwaitDismiss(
        context: Context,
        onPreparingChanged: (Boolean) -> Unit
    ) {
        val eligible = InterstitialAdGatePolicy.shouldRequestInterstitial(
            isOnline = NetworkMonitor.isStrictlyOnline(context),
            isMobileAdsInitialized = OvertimeApp.isMobileAdsInitialized.value,
            nowMs = System.currentTimeMillis(),
            lastShownAtMs = lastAdShownAtMs
        )
        if (!eligible) return

        val activity = context.findActivity() ?: return
        if (!isActivityAliveAndResumed(activity)) return

        onPreparingChanged(true)
        val loadedAd: InterstitialAd? = try {
            withTimeoutOrNull(InterstitialAdGatePolicy.AD_LOAD_TIMEOUT_MS) {
                loadSingleInterstitial(
                    activity = activity,
                    adUnitId = InterstitialAdGatePolicy.resolveAdUnitId()
                )
            }
        } finally {
            onPreparingChanged(false)
        }

        if (loadedAd == null) return
        if (!InterstitialAdGatePolicy.canShowLoadedAd(
                isActivityAlive = !activity.isFinishing && !activity.isDestroyed,
                isActivityResumed = isActivityAliveAndResumed(activity),
                isTimedOutOrCancelled = false
            )
        ) {
            return
        }

        showAndAwaitDismiss(activity = activity, interstitialAd = loadedAd)
    }

    private suspend fun loadSingleInterstitial(
        activity: Activity,
        adUnitId: String
    ): InterstitialAd? = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { cont ->
            val finished = AtomicBoolean(false)
            cont.invokeOnCancellation {
                finished.set(true)
            }

            try {
                InterstitialAd.load(
                    activity,
                    adUnitId,
                    AdRequest.Builder().build(),
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(interstitialAd: InterstitialAd) {
                            if (finished.compareAndSet(false, true)) {
                                if (cont.isActive) cont.resume(interstitialAd)
                            } else {
                                Log.d(TAG, "Discarded late interstitial after timeout/cancellation.")
                            }
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            Log.w(TAG, "Interstitial failed to load (${loadAdError.code}): ${loadAdError.message}")
                            if (finished.compareAndSet(false, true) && cont.isActive) {
                                cont.resume(null)
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Interstitial load threw exception: ${e.message}")
                if (finished.compareAndSet(false, true) && cont.isActive) {
                    cont.resume(null)
                }
            }
        }
    }

    private suspend fun showAndAwaitDismiss(
        activity: Activity,
        interstitialAd: InterstitialAd
    ) = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { cont ->
            val resumed = AtomicBoolean(false)
            fun finishOnce() {
                interstitialAd.fullScreenContentCallback = null
                if (resumed.compareAndSet(false, true) && cont.isActive) {
                    cont.resume(Unit)
                }
            }

            cont.invokeOnCancellation {
                interstitialAd.fullScreenContentCallback = null
                resumed.set(true)
            }

            interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    lastAdShownAtMs = System.currentTimeMillis()
                }

                override fun onAdDismissedFullScreenContent() {
                    finishOnce()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "Interstitial failed to show (${adError.code}): ${adError.message}")
                    finishOnce()
                }
            }

            try {
                if (isActivityAliveAndResumed(activity)) {
                    interstitialAd.show(activity)
                } else {
                    finishOnce()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Interstitial show threw exception: ${e.message}")
                finishOnce()
            }
        }
    }
}
