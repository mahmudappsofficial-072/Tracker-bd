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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * Pure, testable AdMob + Play Console policy gate for Pay Slip Interstitial ads.
 * Combines Show-Rate-safe intent preloading, 50-minute session cache TTL,
 * 60-second anti-spam cooldown, and transient-error retry guards.
 */
object InterstitialAdGatePolicy {
    const val DEBUG_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val RELEASE_GENERAL_AD_UNIT_ID = "ca-app-pub-9027708775227838/6430017173"
    const val AD_LOAD_TIMEOUT_MS = 10_000L
    const val INTERSTITIAL_COOLDOWN_MS = 60_000L
    const val PRELOAD_DEBOUNCE_MS = 1_500L
    const val PRELOAD_FAILURE_BACKOFF_MS = 60_000L
    const val AD_CACHE_MAX_AGE_MS = 50L * 60L * 1000L // 50 minutes (inside AdMob's 60m expiry)
    const val TRANSIENT_RETRY_DELAY_MS = 800L

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

    fun isCachedAdValid(
        loadedAtMs: Long,
        nowMs: Long,
        maxAgeMs: Long = AD_CACHE_MAX_AGE_MS
    ): Boolean {
        if (loadedAtMs <= 0L) return false
        val ageMs = nowMs - loadedAtMs
        return ageMs in 0L until maxAgeMs
    }

    /**
     * Guards intent-scoped preloading on the Pay Slip tab so AdMob Show Rate remains high:
     * - Skips offline devices or active 60s post-show cooldown.
     * - Skips if an unexpired ad is already cached or a request is already in-flight.
     * - Throttles repeated background preloads after a failed preload attempt.
     */
    fun shouldPreloadOnPaySlip(
        hasInternet: Boolean,
        hasValidCache: Boolean,
        isPreloadInFlight: Boolean,
        nowMs: Long,
        lastShownAtMs: Long,
        lastPreloadFailedAtMs: Long = 0L,
        cooldownMs: Long = INTERSTITIAL_COOLDOWN_MS,
        failureBackoffMs: Long = PRELOAD_FAILURE_BACKOFF_MS
    ): Boolean {
        if (!shouldRequestInterstitial(hasInternet, nowMs, lastShownAtMs, cooldownMs)) return false
        if (hasValidCache || isPreloadInFlight) return false
        if (lastPreloadFailedAtMs > 0L && (nowMs - lastPreloadFailedAtMs) in 0L until failureBackoffMs) {
            return false
        }
        return true
    }

    fun shouldRetryTransientLoadError(errorCode: Int, attemptIndex: Int): Boolean {
        if (attemptIndex != 0) return false
        return errorCode == AdRequest.ERROR_CODE_NETWORK_ERROR ||
            errorCode == AdRequest.ERROR_CODE_INTERNAL_ERROR
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
            InterstitialOutcome.LoadTimedOut -> "Debug AdInfo: Ad request timed out (10s) — cached when ready"
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
 * Show-Rate-safe Interstitial Ad coordinator ("General-Interstitial") for Pay Slip PDF downloads.
 * - Zero preload on Piece Rate Ledger tab; debounced intent preload ONLY on Pay Slip tab.
 * - Holds at most 1 cached InterstitialAd (50m TTL) using Application Context to avoid Activity leaks.
 * - Deduplicates in-flight preload requests when user taps Download PDF.
 * - Retries transient network/internal errors once on explicit user tap.
 * - Recovers late-loaded ads into session cache instead of discarding matched impressions.
 */
object GeneralInterstitialAdManager {
    private const val TAG = "GeneralInterstitialAd"

    private sealed class LoadAttempt {
        data class Loaded(val ad: InterstitialAd) : LoadAttempt()
        data class Failed(val code: Int, val message: String) : LoadAttempt()
    }

    private data class CachedInterstitial(
        val ad: InterstitialAd,
        val loadedAtMs: Long
    )

    private val adScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stateLock = Any()

    @Volatile
    private var cachedInterstitial: CachedInterstitial? = null

    @Volatile
    private var inFlightLoad: CompletableDeferred<LoadAttempt>? = null

    @Volatile
    private var lastAdShownAtMs: Long = 0L

    @Volatile
    private var lastPreloadFailedAtMs: Long = 0L

    private fun isActivityAliveAndResumed(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        val lifecycleOwner = activity as? LifecycleOwner ?: return true
        return lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }

    private fun peekValidCachedAdLocked(nowMs: Long): InterstitialAd? {
        val current = cachedInterstitial ?: return null
        return if (InterstitialAdGatePolicy.isCachedAdValid(current.loadedAtMs, nowMs)) {
            current.ad
        } else {
            cachedInterstitial = null
            null
        }
    }

    private fun consumeValidCachedAdLocked(nowMs: Long): InterstitialAd? {
        val valid = peekValidCachedAdLocked(nowMs)
        if (valid != null) {
            cachedInterstitial = null
        }
        return valid
    }

    /**
     * Preloads at most one interstitial ad when the user opens the Pay Slip tab.
     * Safe to invoke across re-compositions; deduplicates in-flight work and reuses valid cache.
     */
    suspend fun preloadOnPaySlipIfEligible(context: Context) {
        val hasInternet = NetworkMonitor.hasAnyInternetConnection(context)
        val nowMs = System.currentTimeMillis()
        val canPreload = synchronized(stateLock) {
            val validCache = peekValidCachedAdLocked(nowMs) != null
            val activeFlight = inFlightLoad?.isActive == true
            InterstitialAdGatePolicy.shouldPreloadOnPaySlip(
                hasInternet = hasInternet,
                hasValidCache = validCache,
                isPreloadInFlight = activeFlight,
                nowMs = nowMs,
                lastShownAtMs = lastAdShownAtMs,
                lastPreloadFailedAtMs = lastPreloadFailedAtMs
            )
        }
        if (!canPreload) return

        val appContext = context.applicationContext
        OvertimeApp.ensureMobileAdsInitialized(appContext, timeoutMs = 3_500L)
        val deferred = getOrStartInFlightLoad(appContext, retryOnTransientError = false)
        val result = deferred.await()
        if (result is LoadAttempt.Failed) {
            lastPreloadFailedAtMs = System.currentTimeMillis()
        } else if (result is LoadAttempt.Loaded) {
            lastPreloadFailedAtMs = 0L
        }
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

        // Fast path: show already-preloaded ad immediately with 0ms wait and no loading overlay
        val readyCachedAd = synchronized(stateLock) { consumeValidCachedAdLocked(nowMs) }
        if (readyCachedAd != null) {
            return showAndAwaitDismiss(activity = activity, interstitialAd = readyCachedAd)
        }

        val appContext = context.applicationContext
        onPreparingChanged(true)
        val loadAttempt: LoadAttempt? = try {
            OvertimeApp.ensureMobileAdsInitialized(appContext, timeoutMs = 3_500L)
            val deferred = getOrStartInFlightLoad(appContext, retryOnTransientError = true)
            withTimeoutOrNull(InterstitialAdGatePolicy.AD_LOAD_TIMEOUT_MS) {
                deferred.await()
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
            // Keep loadedAd in session cache rather than wasting a matched ad
            return InterstitialOutcome.SkippedNotResumed
        }

        // Consume matching reference from cache so the same single-use ad cannot be shown twice
        synchronized(stateLock) {
            if (cachedInterstitial?.ad === loadedAd) {
                cachedInterstitial = null
            }
        }
        return showAndAwaitDismiss(activity = activity, interstitialAd = loadedAd)
    }

    private fun getOrStartInFlightLoad(
        appContext: Context,
        retryOnTransientError: Boolean
    ): CompletableDeferred<LoadAttempt> {
        synchronized(stateLock) {
            val nowMs = System.currentTimeMillis()
            val validCache = peekValidCachedAdLocked(nowMs)
            if (validCache != null) {
                return CompletableDeferred(LoadAttempt.Loaded(validCache))
            }
            val existing = inFlightLoad
            if (existing != null && existing.isActive) {
                return existing
            }

            val created = CompletableDeferred<LoadAttempt>()
            inFlightLoad = created
            adScope.launch {
                val adUnitId = InterstitialAdGatePolicy.resolveAdUnitId()
                var attempt = loadSingleInterstitial(appContext, adUnitId)
                if (attempt is LoadAttempt.Failed &&
                    retryOnTransientError &&
                    InterstitialAdGatePolicy.shouldRetryTransientLoadError(attempt.code, attemptIndex = 0)
                ) {
                    delay(InterstitialAdGatePolicy.TRANSIENT_RETRY_DELAY_MS)
                    attempt = loadSingleInterstitial(appContext, adUnitId)
                }

                synchronized(stateLock) {
                    if (attempt is LoadAttempt.Loaded) {
                        // Store in cache even if caller already timed out (Zero-Waste Late-Load Recovery)
                        cachedInterstitial = CachedInterstitial(
                            ad = attempt.ad,
                            loadedAtMs = System.currentTimeMillis()
                        )
                        lastPreloadFailedAtMs = 0L
                    }
                    if (inFlightLoad === created) {
                        inFlightLoad = null
                    }
                }
                created.complete(attempt)
            }
            return created
        }
    }

    private suspend fun loadSingleInterstitial(
        appContext: Context,
        adUnitId: String
    ): LoadAttempt = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { cont ->
            val finished = AtomicBoolean(false)
            cont.invokeOnCancellation {
                finished.set(true)
            }

            try {
                InterstitialAd.load(
                    appContext,
                    adUnitId,
                    AdRequest.Builder().build(),
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(interstitialAd: InterstitialAd) {
                            if (finished.compareAndSet(false, true) && cont.isActive) {
                                cont.resume(LoadAttempt.Loaded(interstitialAd))
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
