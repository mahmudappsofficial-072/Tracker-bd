package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class AppUpdateInfo(
    val isForceUpdate: Boolean,
    val minVersionCode: Long,
    val latestVersionCode: Long,
    val localizedTitles: Map<String, String> = emptyMap(),
    val localizedDescs: Map<String, String> = emptyMap(),
    val localizedUpdateBtns: Map<String, String> = emptyMap(),
    val localizedDismissBtns: Map<String, String> = emptyMap(),
    val updateUrl: String = "",
    val playStoreUrl: String = ""
) {
    fun getEffectiveUrl(): String {
        return updateUrl.ifBlank { playStoreUrl }.trim()
    }

    fun getEffectiveTitle(context: Context, langTag: String): String {
        val tag = LanguageManager.resolveSupportedTag(langTag)
        val remote = localizedTitles[tag]?.takeIf { it.isNotBlank() }
        if (remote != null) return remote
        val lctx = LanguageManager.getLocalizedContext(context, tag)
        return lctx.getString(
            if (isForceUpdate) R.string.update_force_title_fallback else R.string.update_optional_title_fallback
        )
    }

    fun getEffectiveDesc(context: Context, langTag: String): String {
        val tag = LanguageManager.resolveSupportedTag(langTag)
        val remote = localizedDescs[tag]?.takeIf { it.isNotBlank() }
        if (remote != null) return remote
        val lctx = LanguageManager.getLocalizedContext(context, tag)
        return lctx.getString(
            if (isForceUpdate) R.string.update_force_desc_fallback else R.string.update_optional_desc_fallback
        )
    }

    fun getEffectiveUpdateBtnText(context: Context, langTag: String): String {
        val tag = LanguageManager.resolveSupportedTag(langTag)
        val remote = localizedUpdateBtns[tag]?.takeIf { it.isNotBlank() }
        if (remote != null) return remote
        val lctx = LanguageManager.getLocalizedContext(context, tag)
        return lctx.getString(
            if (isForceUpdate) R.string.update_btn_play_store_fallback else R.string.update_btn_now_fallback
        )
    }

    fun getEffectiveDismissBtnText(context: Context, langTag: String): String {
        val tag = LanguageManager.resolveSupportedTag(langTag)
        val lctx = LanguageManager.getLocalizedContext(context, tag)
        if (isForceUpdate) {
            return lctx.getString(R.string.update_btn_exit_app)
        }
        val remote = localizedDismissBtns[tag]?.takeIf { it.isNotBlank() }
        return remote ?: lctx.getString(R.string.update_btn_not_now_fallback)
    }
}

object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"
    private const val SERVER_TIMEOUT_MILLIS = 2000L
    private const val CACHE_TIMEOUT_MILLIS = 1000L
    private const val PREFS_NAME = "app_update_prefs"
    private const val KEY_DISMISSED_VERSION = "dismissed_version_code"
    private const val KEY_DISMISSED_TIME = "dismissed_timestamp"
    private const val SNOOZE_DURATION_MILLIS = 24 * 60 * 60 * 1000L // 24 hours snooze for optional update
    private val isCheckingInProgress = java.util.concurrent.atomic.AtomicBoolean(false)

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            networkInfo != null && networkInfo.isConnected
        }
    }

    fun isOptionalUpdateSnoozed(context: Context, targetVersionCode: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dismissedVersion = prefs.getLong(KEY_DISMISSED_VERSION, -1L)
        val dismissedTime = prefs.getLong(KEY_DISMISSED_TIME, 0L)
        if (dismissedVersion == targetVersionCode) {
            val now = System.currentTimeMillis()
            if (now - dismissedTime < SNOOZE_DURATION_MILLIS) {
                return true
            }
        }
        return false
    }

    fun snoozeOptionalUpdate(context: Context, targetVersionCode: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_DISMISSED_VERSION, targetVersionCode)
            .putLong(KEY_DISMISSED_TIME, System.currentTimeMillis())
            .apply()
    }

    private const val KEY_LAST_CHECK_TIME = "last_update_check_time"
    private const val UPDATE_CHECK_COOLDOWN_MILLIS = 2 * 60 * 60 * 1000L // 2 hours cooldown between version control reads

    fun shouldCheckUpdate(context: Context, force: Boolean = false): Boolean {
        if (force) return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        val now = System.currentTimeMillis()
        return (now - lastCheck) >= UPDATE_CHECK_COOLDOWN_MILLIS
    }

    private fun markUpdateChecked(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply()
    }

    /**
     * Checks Firebase Firestore for remote version control.
     * Evaluates both Force Update and Optional Update.
     * If device is offline or checked recently, skips read to save quota.
     */
    suspend fun checkUpdate(
        context: Context,
        ignoreSnooze: Boolean = false,
        forceCheck: Boolean = false
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        // If user is offline, skip check immediately to let app operate normally
        if (!isNetworkAvailable(context)) {
            Log.d(TAG, "Device is offline. Bypassing update check for normal offline usage.")
            return@withContext null
        }

        if (!forceCheck && !shouldCheckUpdate(context)) {
            Log.d(TAG, "Update check skipped (checked recently within 2 hours).")
            return@withContext null
        }

        if (!isCheckingInProgress.compareAndSet(false, true)) {
            return@withContext null
        }

        try {
            val db = FirebaseFirestore.getInstance()
            val docRef = db.collection("app_config").document("version_control")
            val serverDoc = try {
                withTimeoutOrNull(SERVER_TIMEOUT_MILLIS) {
                    docRef.get(Source.SERVER).await()
                }
            } catch (_: Exception) {
                null
            }
            val doc = serverDoc ?: try {
                withTimeoutOrNull(CACHE_TIMEOUT_MILLIS) {
                    docRef.get().await()
                }
            } catch (_: Exception) {
                null
            }

            if (serverDoc != null) {
                markUpdateChecked(context)
            }

            if (doc != null && doc.exists()) {
                val minVersionCode = when (val v = doc.get("min_version_code")) {
                    is Number -> v.toLong()
                    is String -> v.trim().toLongOrNull() ?: 0L
                    else -> 0L
                }
                val latestVersionCode = when (val v = doc.get("latest_version_code")) {
                    is Number -> v.toLong()
                    is String -> v.trim().toLongOrNull() ?: minVersionCode
                    else -> minVersionCode
                }
                val isForceUpdateFlag = when (val f = doc.get("force_update")) {
                    is Boolean -> f
                    is String -> f.trim().equals("true", ignoreCase = true)
                    is Number -> f.toInt() == 1
                    else -> false
                }

                val currentVersionCode = BuildConfig.VERSION_CODE.toLong()

                Log.d(TAG, "Installed: $currentVersionCode, MinRequired: $minVersionCode, Latest: $latestVersionCode, ForceFlag: $isForceUpdateFlag")

                // 1. Mandatory / Force update condition
                val isMandatory = (currentVersionCode < minVersionCode) || (isForceUpdateFlag && currentVersionCode < latestVersionCode)

                // 2. Optional / Flexible update condition
                val isOptional = !isMandatory && (currentVersionCode < latestVersionCode)

                if (isMandatory || isOptional) {
                    if (isOptional && !ignoreSnooze && isOptionalUpdateSnoozed(context, latestVersionCode)) {
                        Log.d(TAG, "Optional update for version $latestVersionCode is snoozed. Skipping.")
                        return@withContext null
                    }

                    val titles = mutableMapOf<String, String>()
                    val descs = mutableMapOf<String, String>()
                    val updateBtns = mutableMapOf<String, String>()
                    val dismissBtns = mutableMapOf<String, String>()

                    for (tag in LanguageManager.SUPPORTED_LANG_TAGS) {
                        doc.getString("update_title_$tag")?.takeIf { it.isNotBlank() }?.let { titles[tag] = it }
                        doc.getString("update_desc_$tag")?.takeIf { it.isNotBlank() }?.let { descs[tag] = it }
                        doc.getString("update_btn_text_$tag")?.takeIf { it.isNotBlank() }?.let { updateBtns[tag] = it }
                        doc.getString("dismiss_btn_text_$tag")?.takeIf { it.isNotBlank() }?.let { dismissBtns[tag] = it }
                    }

                    val updateUrl = doc.getString("update_url") ?: ""
                    val playStoreUrl = doc.getString("play_store_url") ?: ""

                    return@withContext AppUpdateInfo(
                        isForceUpdate = isMandatory,
                        minVersionCode = minVersionCode,
                        latestVersionCode = latestVersionCode,
                        localizedTitles = titles,
                        localizedDescs = descs,
                        localizedUpdateBtns = updateBtns,
                        localizedDismissBtns = dismissBtns,
                        updateUrl = updateUrl,
                        playStoreUrl = playStoreUrl
                    )
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check update from Firestore: ${e.message}")
            null
        } finally {
            isCheckingInProgress.set(false)
        }
    }
}
