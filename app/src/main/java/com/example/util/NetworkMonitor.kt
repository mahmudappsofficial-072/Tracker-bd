package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object NetworkMonitor {

    /**
     * Pure capability evaluation helper: requires both INTERNET and VALIDATED
     * so captive portals or SIM/Wi-Fi connections without actual internet are treated as offline.
     */
    fun hasValidatedInternet(hasInternetCapability: Boolean, hasValidatedCapability: Boolean): Boolean {
        return hasInternetCapability && hasValidatedCapability
    }

    fun hasInternetCapability(hasInternet: Boolean): Boolean = hasInternet

    /**
     * Lightweight internet capability check matching AdmobBanner: allows AdMob requests
     * even when NET_CAPABILITY_VALIDATED lags behind on mobile proxies, VPNs, or emulators.
     */
    fun hasAnyInternetConnection(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            hasInternetCapability(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        } catch (_: Exception) {
            false
        }
    }

    fun isStrictlyOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            hasValidatedInternet(
                hasInternetCapability = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                hasValidatedCapability = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            )
        } catch (_: Exception) {
            false
        }
    }

    fun observeIsOnline(context: Context): Flow<Boolean> = callbackFlow {
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isStrictlyOnline(appContext))
            }

            override fun onLost(network: Network) {
                trySend(isStrictlyOnline(appContext))
            }

            override fun onUnavailable() {
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val online = hasValidatedInternet(
                    hasInternetCapability = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    hasValidatedCapability = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                )
                trySend(online)
            }
        }

        trySend(isStrictlyOnline(appContext))
        val registered = try {
            cm?.registerDefaultNetworkCallback(callback)
            cm != null
        } catch (_: Exception) {
            false
        }

        awaitClose {
            if (registered) {
                try {
                    cm?.unregisterNetworkCallback(callback)
                } catch (_: Exception) {}
            }
        }
    }.distinctUntilChanged()

    @Composable
    fun rememberIsOnline(context: Context = LocalContext.current): State<Boolean> {
        val isOnlineState = remember(context) { mutableStateOf(isStrictlyOnline(context)) }

        DisposableEffect(context) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val mainHandler = Handler(Looper.getMainLooper())
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    mainHandler.post { isOnlineState.value = isStrictlyOnline(context) }
                }

                override fun onLost(network: Network) {
                    mainHandler.post { isOnlineState.value = isStrictlyOnline(context) }
                }

                override fun onUnavailable() {
                    mainHandler.post { isOnlineState.value = false }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val online = hasValidatedInternet(
                        hasInternetCapability = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                        hasValidatedCapability = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    )
                    mainHandler.post { isOnlineState.value = online }
                }
            }

            isOnlineState.value = isStrictlyOnline(context)
            val registered = try {
                cm?.registerDefaultNetworkCallback(callback)
                cm != null
            } catch (_: Exception) {
                false
            }

            onDispose {
                if (registered) {
                    try {
                        cm?.unregisterNetworkCallback(callback)
                    } catch (_: Exception) {}
                }
            }
        }

        return isOnlineState
    }
}
