package com.example.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.OvertimeApp
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.delay

private fun checkNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/**
 * AdMob Persistent Anchored Adaptive Banner Composable.
 * Features:
 * 1. Exact pixel-matched Anchored Adaptive Banner sizing + requestLayout() on load so Compose
 *    never clips or measures 0px during async WebView inflation.
 * 2. Waits for app readiness, MobileAds initialization, non-occluded banner space, and connectivity.
 * 3. Exponential backoff (15s -> 30s -> 60s, max 3 retries) on transient errors only; avoids NO_FILL spam.
 */
@Composable
fun AdmobBanner(
    modifier: Modifier = Modifier,
    isOccluded: Boolean = false,
    canLoadAd: Boolean = true,
    adUnitId: String = if (com.example.BuildConfig.DEBUG) {
        "ca-app-pub-3940256099942544/6300978111"
    } else {
        "ca-app-pub-9027708775227838/5740981993"
    }
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val isBannerVisible = canLoadAd && !isImeVisible && !isOccluded
    val isMobileAdsInitialized by OvertimeApp.isMobileAdsInitialized.collectAsStateWithLifecycle()

    val screenWidthDp = configuration.screenWidthDp.coerceAtLeast(320)
    val adSize = remember(screenWidthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, screenWidthDp)
    }
    val adHeightPx = remember(adSize) {
        adSize.getHeightInPixels(context)
    }
    val adHeightDp = remember(adHeightPx, adSize, density) {
        if (adHeightPx > 0) {
            with(density) { adHeightPx.toDp() }
        } else {
            val h = adSize.height
            if (h > 0) h.dp else 50.dp
        }
    }

    var isAdLoaded by remember(screenWidthDp) { mutableStateOf(false) }
    var hasRequestedAd by remember(screenWidthDp) { mutableStateOf(false) }
    var failedRetryCount by remember(screenWidthDp) { mutableIntStateOf(0) }
    var pendingRetryAttempt by remember(screenWidthDp) { mutableIntStateOf(0) }
    var adViewRef by remember(screenWidthDp) { mutableStateOf<AdView?>(null) }
    var isOnline by remember { mutableStateOf(checkNetworkAvailable(context)) }
    var isLifecycleResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val latestIsBannerVisible by rememberUpdatedState(isBannerVisible)

    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val mainHandler = Handler(Looper.getMainLooper())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainHandler.post { isOnline = true }
            }

            override fun onLost(network: Network) {
                mainHandler.post { isOnline = checkNetworkAvailable(context) }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                mainHandler.post { isOnline = hasInternet }
            }
        }
        val registered = try {
            cm?.registerDefaultNetworkCallback(callback)
            cm != null
        } catch (e: Exception) {
            Log.w("AdmobBanner", "Failed to register network callback: ${e.message}")
            false
        }
        onDispose {
            if (registered) {
                try {
                    cm?.unregisterNetworkCallback(callback)
                } catch (e: Exception) {
                    Log.w("AdmobBanner", "Failed to unregister network callback: ${e.message}")
                }
            }
        }
    }

    // Initial ad request: only fire once main app UI is ready, SDK is initialized, banner is visible, and online
    LaunchedEffect(adViewRef, isMobileAdsInitialized, isBannerVisible, isOnline, canLoadAd, isLifecycleResumed) {
        val adView = adViewRef ?: return@LaunchedEffect
        if (!hasRequestedAd && canLoadAd && isMobileAdsInitialized && isBannerVisible && isOnline && isLifecycleResumed) {
            hasRequestedAd = true
            adView.loadAd(AdRequest.Builder().build())
        }
    }

    // Rate-limited exponential backoff retry for transient network/internal errors only
    LaunchedEffect(adViewRef, pendingRetryAttempt, isMobileAdsInitialized, isBannerVisible, isOnline, canLoadAd, isLifecycleResumed) {
        val adView = adViewRef ?: return@LaunchedEffect
        if (pendingRetryAttempt > 0 && canLoadAd && isMobileAdsInitialized && isBannerVisible && isOnline && isLifecycleResumed) {
            val backoffMs = when (pendingRetryAttempt) {
                1 -> 15_000L
                2 -> 30_000L
                else -> 60_000L
            }
            delay(backoffMs)
            pendingRetryAttempt = 0
            adView.loadAd(AdRequest.Builder().build())
        }
    }

    // Pause banner timers/refresh only when actually occluded by IME/bottom-covering modals or backgrounded
    LaunchedEffect(adViewRef, isBannerVisible, isLifecycleResumed) {
        val adView = adViewRef ?: return@LaunchedEffect
        if (isBannerVisible && isLifecycleResumed) {
            adView.resume()
        } else {
            adView.pause()
        }
    }

    // Lifecycle observer synced cleanly with Activity ON_RESUME / ON_PAUSE / ON_DESTROY
    DisposableEffect(lifecycleOwner, adViewRef) {
        val adView = adViewRef
        if (adView == null) {
            onDispose {}
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        isLifecycleResumed = false
                        adView.pause()
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        isLifecycleResumed = true
                        if (latestIsBannerVisible) {
                            adView.resume()
                        }
                    }
                    Lifecycle.Event.ON_DESTROY -> {
                        isLifecycleResumed = false
                        adView.destroy()
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("admob_banner_card"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isAdLoaded) 1.dp else 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(adHeightDp),
                contentAlignment = Alignment.Center
            ) {
                key(screenWidthDp) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(adHeightDp),
                        factory = { ctx ->
                            AdView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    if (adHeightPx > 0) adHeightPx else ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                                setAdSize(adSize)
                                this.adUnitId = adUnitId
                                adListener = object : AdListener() {
                                    override fun onAdLoaded() {
                                        isAdLoaded = true
                                        failedRetryCount = 0
                                        pendingRetryAttempt = 0
                                        post { requestLayout() }
                                    }

                                    override fun onAdFailedToLoad(error: LoadAdError) {
                                        isAdLoaded = false
                                        val isTransientError =
                                            error.code == AdRequest.ERROR_CODE_NETWORK_ERROR ||
                                                error.code == AdRequest.ERROR_CODE_INTERNAL_ERROR
                                        if (isTransientError && failedRetryCount < 3) {
                                            failedRetryCount += 1
                                            pendingRetryAttempt = failedRetryCount
                                        }
                                    }
                                }
                                adViewRef = this
                            }
                        },
                        onRelease = { adView ->
                            adView.destroy()
                            adViewRef = null
                        }
                    )
                }
            }
        }
    }
}
