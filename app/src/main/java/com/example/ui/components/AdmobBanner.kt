package com.example.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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

private fun checkNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/**
 * AdMob Persistent Anchored Adaptive Banner Composable.
 * Features:
 * 1. Anchored Adaptive Banner sizing without rounded-corner or 1px height clipping.
 * 2. Waits for MobileAds initialization, active window focus (no modal dialog occluding),
 *    and internet connectivity before sending ad requests.
 * 3. Android Lifecycle & focus awareness (pause/resume) to maximize viewability & Show Rate.
 */
@Composable
fun AdmobBanner(
    modifier: Modifier = Modifier,
    isOccluded: Boolean = false,
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
    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val isBannerVisible = isWindowFocused && !isImeVisible && !isOccluded
    val isMobileAdsInitialized by OvertimeApp.isMobileAdsInitialized.collectAsStateWithLifecycle()

    val screenWidthDp = configuration.screenWidthDp
    val adSize = remember(screenWidthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, screenWidthDp)
    }
    val adHeightDp = remember(adSize) {
        val h = adSize.height
        if (h > 0) h.dp else 50.dp
    }

    var isAdLoaded by remember(screenWidthDp) { mutableStateOf(false) }
    var hasRequestedAd by remember(screenWidthDp) { mutableStateOf(false) }
    var adViewRef by remember(screenWidthDp) { mutableStateOf<AdView?>(null) }
    var isOnline by remember { mutableStateOf(checkNetworkAvailable(context)) }
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

    // Load only when SDK is ready, window has focus, soft keyboard is closed, and device is online
    LaunchedEffect(adViewRef, isMobileAdsInitialized, isBannerVisible, isOnline) {
        val adView = adViewRef ?: return@LaunchedEffect
        if (!hasRequestedAd && isMobileAdsInitialized && isBannerVisible && isOnline) {
            hasRequestedAd = true
            adView.loadAd(AdRequest.Builder().build())
        }
    }

    // Pause banner timers/refresh when occluded by a modal dialog or soft keyboard; resume when visible again
    LaunchedEffect(adViewRef, isBannerVisible) {
        val adView = adViewRef ?: return@LaunchedEffect
        if (isBannerVisible) {
            adView.resume()
        } else {
            adView.pause()
        }
    }

    // Lifecycle observer to pause/resume ad loading and prevent unviewed background requests
    DisposableEffect(lifecycleOwner, adViewRef) {
        val adView = adViewRef
        if (adView == null) {
            onDispose {}
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> adView.pause()
                    Lifecycle.Event.ON_RESUME -> if (latestIsBannerVisible) adView.resume()
                    Lifecycle.Event.ON_DESTROY -> adView.destroy()
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
            .defaultMinSize(minHeight = adHeightDp)
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
                    .defaultMinSize(minHeight = adHeightDp),
                contentAlignment = Alignment.Center
            ) {
                key(screenWidthDp) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .defaultMinSize(minHeight = adHeightDp),
                        factory = { ctx ->
                            AdView(ctx).apply {
                                setAdSize(adSize)
                                this.adUnitId = adUnitId
                                adListener = object : AdListener() {
                                    override fun onAdLoaded() {
                                        isAdLoaded = true
                                    }

                                    override fun onAdFailedToLoad(error: LoadAdError) {
                                        isAdLoaded = false
                                        if (error.code == AdRequest.ERROR_CODE_NETWORK_ERROR ||
                                            error.code == AdRequest.ERROR_CODE_INTERNAL_ERROR
                                        ) {
                                            hasRequestedAd = false
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
