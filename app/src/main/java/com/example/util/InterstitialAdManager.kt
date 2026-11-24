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

sealed class InterstitialOutcome {
    data object ShownAndDismissed : InterstitialOutcome()
    data object SkippedOffline : InterstitialOutcome()
    data object SkippedCooldown : InterstitialOutcome()
    data object SkippedNotResumed : InterstitialOutcome()
    data class LoadFailed(val code: Int, val message: String) : InterstitialOutcome()
    data object LoadTimedOut : InterstitialOutcome()
    data class ShowFailed(val code: Int, val message: String) : InterstitialOutcome()
}

/**
 * Pure, testable AdMob + Play Console policy gate for on-demand Interstitial ads.
 */
object InterstitialAdGatePolicy {
    const val DEBUG_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val RELEASE_GENERAL_AD_UNIT_ID = "ca-app-pub-9027708775227838/5097750056"
    const val AD_LOAD_TIMEOUT_MS = 8_000L
    const val INTERSTITIAL_COOLDOWN_MS = 60_000L

    fun resolveAdUnitId(isDebug: Boolean = BuildConfig.DEBUG): String {
        return if (isDebug) DEBUG_TEST_AD_UNIT_ID else RELEASE_GENERAL_AD_UNIT_ID
    }

    fun shouldRequestInterstitial(
        hasInternet: Boolean,
        nowMs: Long,
        lastShownAtMs: Long,
        cooldownMs: Long = INTERSTITIAL_COOLDOWN_MS
    ): Boolean {
        if (!hasInternet) return false
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

    /**
     * Returns developer diagnostic text ONLY in Debug builds so emulator/Play Services
     * restrictions or No-Fill responses are immediately visible during testing.
     */
    fun formatDebugDiagnostic(outcome: InterstitialOutcome, isDebug: Boolean = BuildConfig.DEBUG): String? {
        if (!isDebug) return null
        return when (outcome) {
            InterstitialOutcome.ShownAndDismissed -> null
            InterstitialOutcome.SkippedOffline -> "Debug AdInfo: Offline (no internet capability)"
            InterstitialOutcome.SkippedCooldown -> "Debug AdInfo: 60s ad cooldown active"
            InterstitialOutcome.SkippedNotResumed -> null
            InterstitialOutcome.LoadTimedOut -> "Debug AdInfo: Ad request timed out (8s) — check emulator Play Services/network"
            is InterstitialOutcome.LoadFailed -> "Debug AdInfo: Load failed (code ${outcome.code}) — ${outcome.message}"
            is InterstitialOutcome.ShowFailed -> "Debug AdInfo: Show failed (code ${outcome.code}) — ${outcome.message}"
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * On-demand Interstitial Ad coordinator ("General-Interstitial") for Pay Slip PDF downloads.
 * - Never preloads upon entering screens; loads strictly on explicit button tap when internet capability exists.
 * - Recovers on-demand if MobileAds boot-time initialization missed or lagged.
 * - Uses bounded 8s load timeout, no-fill pass-through, foreground lifecycle checks,
 *   single-use FullScreenContentCallback cleanup, and 60s anti-spam cooldown.
 */
object GeneralInterstitialAdManager {
    private const val TAG = "GeneralInterstitialAd"

    private sealed class LoadAttempt {
        data class Loaded(val ad: InterstitialAd) : LoadAttempt()
        data class Failed(val code: Int, val message: String) : LoadAttempt()
    }

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
    ): InterstitialOutcome {
        val hasInternet = NetworkMonitor.hasAnyInternetConnection(context)
        if (!hasInternet) {
            return InterstitialOutcome.SkippedOffline
        }

        val nowMs = System.currentTimeMillis()
        if (!InterstitialAdGatePolicy.shouldRequestInterstitial(
                hasInternet = true,
                nowMs = nowMs,
                lastShownAtMs = lastAdShownAtMs
            )
        ) {
            return InterstitialOutcome.SkippedCooldown
        }

        val activity = context.findActivity() ?: return InterstitialOutcome.SkippedNotResumed
        if (!isActivityAliveAndResumed(activity)) return InterstitialOutcome.SkippedNotResumed

        onPreparingChanged(true)
        val loadAttempt: LoadAttempt? = try {
            // Ensure SDK had a chance to initialize if boot-time init ran offline or hung
            OvertimeApp.ensureMobileAdsInitialized(context.applicationContext, timeoutMs = 2_000L)
            withTimeoutOrNull(InterstitialAdGatePolicy.AD_LOAD_TIMEOUT_MS) {
                loadSingleInterstitial(
                    activity = activity,
                    adUnitId = InterstitialAdGatePolicy.resolveAdUnitId()
                )
            }
        } finally {
            onPreparingChanged(false)
        }

        val loadedAd = when (loadAttempt) {
            null -> return InterstitialOutcome.LoadTimedOut
            is LoadAttempt.Failed -> return InterstitialOutcome.LoadFailed(loadAttempt.code, loadAttempt.message)
            is LoadAttempt.Loaded -> loadAttempt.ad
        }

        if (!InterstitialAdGatePolicy.canShowLoadedAd(
                isActivityAlive = !activity.isFinishing && !activity.isDestroyed,
                isActivityResumed = isActivityAliveAndResumed(activity),
                isTimedOutOrCancelled = false
            )
        ) {
            return InterstitialOutcome.SkippedNotResumed
        }

        return showAndAwaitDismiss(activity = activity, interstitialAd = loadedAd)
    }

    private suspend fun loadSingleInterstitial(
        activity: Activity,
        adUnitId: String
    ): LoadAttempt = withContext(Dispatchers.Main.immediate) {
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
                                if (cont.isActive) cont.resume(LoadAttempt.Loaded(interstitialAd))
                            } else {
                                Log.d(TAG, "Discarded late interstitial after timeout/cancellation.")
                            }
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            Log.w(TAG, "Interstitial failed to load (${loadAdError.code}): ${loadAdError.message}")
                            if (finished.compareAndSet(false, true) && cont.isActive) {
                                cont.resume(LoadAttempt.Failed(loadAdError.code, loadAdError.message))
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown exception"
                Log.w(TAG, "Interstitial load threw exception: $msg")
                if (finished.compareAndSet(false, true) && cont.isActive) {
                    cont.resume(LoadAttempt.Failed(-1, msg))
                }
            }
        }
    }

    private suspend fun showAndAwaitDismiss(
        activity: Activity,
        interstitialAd: InterstitialAd
    ): InterstitialOutcome = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { cont ->
            val resumed = AtomicBoolean(false)
            fun finishOnce(outcome: InterstitialOutcome) {
                interstitialAd.fullScreenContentCallback = null
                if (resumed.compareAndSet(false, true) && cont.isActive) {
                    cont.resume(outcome)
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
                    finishOnce(InterstitialOutcome.ShownAndDismissed)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "Interstitial failed to show (${adError.code}): ${adError.message}")
                    finishOnce(InterstitialOutcome.ShowFailed(adError.code, adError.message))
                }
            }

            try {
                if (isActivityAliveAndResumed(activity)) {
                    interstitialAd.show(activity)
                } else {
                    finishOnce(InterstitialOutcome.SkippedNotResumed)
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Show exception"
                Log.w(TAG, "Interstitial show threw exception: $msg")
                finishOnce(InterstitialOutcome.ShowFailed(-1, msg))
            }
        }
    }
}
