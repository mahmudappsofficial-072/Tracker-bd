package com.example.util

enum class StartupSyncReason {
    NOT_SIGNED_IN,
    NO_CONSENT,
    OFFLINE,
    NEEDS_INITIAL_RESTORE,
    HAS_PENDING_LOCAL_CHANGES,
    SERVER_TIMESTAMP_CHECK_ONLY,
    NO_PENDING_RECONNECT_WORK
}

data class StartupSyncDecision(
    val shouldRunSync: Boolean,
    val reason: StartupSyncReason
)

enum class RestoreMode {
    NONE,
    DELTA,
    FULL
}

data class CollectionRestorePlan(
    val restoreMode: RestoreMode,
    val otSince: Long? = null,
    val pieceSince: Long? = null,
    val monthlySince: Long? = null,
    val deductionSince: Long? = null,
    val trashSince: Long? = null
) {
    val shouldDownloadAny: Boolean get() = restoreMode != RestoreMode.NONE
}

/**
 * Pure, deterministic decision engine for zero-waste Firestore sync & delta restore.
 */
object CloudSyncPolicy {

    const val DEFAULT_WATERMARK_SAFETY_OVERLAP_MS = 60_000L

    fun evaluateStartupSyncDecision(
        isSignedIn: Boolean,
        hasPrivacyConsent: Boolean,
        isOnline: Boolean,
        hasCompletedInitialRestoreForUid: Boolean,
        hasPendingLocalDirtyData: Boolean
    ): StartupSyncDecision {
        if (!isSignedIn) {
            return StartupSyncDecision(false, StartupSyncReason.NOT_SIGNED_IN)
        }
        if (!hasPrivacyConsent) {
            return StartupSyncDecision(false, StartupSyncReason.NO_CONSENT)
        }
        if (!isOnline) {
            return StartupSyncDecision(false, StartupSyncReason.OFFLINE)
        }
        return when {
            !hasCompletedInitialRestoreForUid ->
                StartupSyncDecision(true, StartupSyncReason.NEEDS_INITIAL_RESTORE)
            hasPendingLocalDirtyData ->
                StartupSyncDecision(true, StartupSyncReason.HAS_PENDING_LOCAL_CHANGES)
            else ->
                // Check ONLY the 1 root user document to detect changes from another device.
                StartupSyncDecision(true, StartupSyncReason.SERVER_TIMESTAMP_CHECK_ONLY)
        }
    }

    /**
     * When connectivity returns mid-session, only flush if initial restore hasn't completed
     * or offline local changes are queued — toggling Wi-Fi with zero dirty work costs 0 reads/writes.
     */
    fun evaluateReconnectSyncDecision(
        isSignedIn: Boolean,
        hasPrivacyConsent: Boolean,
        isOnline: Boolean,
        hasCompletedInitialRestoreForUid: Boolean,
        hasPendingLocalDirtyData: Boolean
    ): StartupSyncDecision {
        if (!isSignedIn) return StartupSyncDecision(false, StartupSyncReason.NOT_SIGNED_IN)
        if (!hasPrivacyConsent) return StartupSyncDecision(false, StartupSyncReason.NO_CONSENT)
        if (!isOnline) return StartupSyncDecision(false, StartupSyncReason.OFFLINE)

        return when {
            !hasCompletedInitialRestoreForUid ->
                StartupSyncDecision(true, StartupSyncReason.NEEDS_INITIAL_RESTORE)
            hasPendingLocalDirtyData ->
                StartupSyncDecision(true, StartupSyncReason.HAS_PENDING_LOCAL_CHANGES)
            else ->
                StartupSyncDecision(false, StartupSyncReason.NO_PENDING_RECONNECT_WORK)
        }
    }

    fun evaluateCollectionRestorePlan(
        forceFullSync: Boolean,
        hasCompletedInitialRestore: Boolean,
        cloudSyncTime: Long,
        lastKnownCloudSyncTime: Long,
        otWatermark: Long,
        pieceWatermark: Long,
        monthlyWatermark: Long,
        deductionWatermark: Long,
        trashWatermark: Long,
        safetyOverlapMs: Long = DEFAULT_WATERMARK_SAFETY_OVERLAP_MS
    ): CollectionRestorePlan {
        if (forceFullSync || !hasCompletedInitialRestore) {
            return CollectionRestorePlan(
                restoreMode = RestoreMode.FULL,
                otSince = null,
                pieceSince = null,
                monthlySince = null,
                deductionSince = null,
                trashSince = null
            )
        }

        if (cloudSyncTime <= lastKnownCloudSyncTime) {
            return CollectionRestorePlan(restoreMode = RestoreMode.NONE)
        }

        fun deltaSince(watermark: Long): Long? {
            if (watermark <= 0L) return null
            return (watermark - safetyOverlapMs).coerceAtLeast(0L)
        }

        return CollectionRestorePlan(
            restoreMode = RestoreMode.DELTA,
            otSince = deltaSince(otWatermark),
            pieceSince = deltaSince(pieceWatermark),
            monthlySince = deltaSince(monthlyWatermark),
            deductionSince = deltaSince(deductionWatermark),
            trashSince = deltaSince(trashWatermark)
        )
    }

    /**
     * Generates a monotonically increasing timestamp even if another device's clock is slightly ahead.
     */
    fun computeMonotonicSyncTime(nowMillis: Long, vararg knownTimestamps: Long): Long {
        val maxKnown = knownTimestamps.maxOrNull() ?: 0L
        return if (maxKnown > 0L) maxOf(nowMillis, maxKnown + 1L) else nowMillis
    }

    /**
     * Enforces the user rule: "Auto Cloud Sync" time updates ONLY when an actual upload/delete
     * or actual cloud restore/merge occurred; pure check-only passes leave it untouched.
     */
    fun resolveDisplayedSyncTimestamp(
        previousSyncTime: Long,
        transferOccurred: Boolean,
        transferTimeMillis: Long
    ): Long {
        if (!transferOccurred) return previousSyncTime
        return if (transferTimeMillis > 0L) transferTimeMillis else previousSyncTime
    }

    /**
     * Prevents an already-synced existing user who updates the app from incurring an unnecessary
     * full collection re-download on their first launch after update.
     */
    fun shouldSeedLegacyUpgradeWatermark(
        hasInitialRestoreKeyRecorded: Boolean,
        localLastCloudSyncTime: Long
    ): Boolean {
        return !hasInitialRestoreKeyRecorded && localLastCloudSyncTime > 0L
    }
}
