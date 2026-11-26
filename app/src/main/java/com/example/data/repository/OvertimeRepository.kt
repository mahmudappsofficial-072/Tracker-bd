package com.example.data.repository

import android.util.Log
import androidx.annotation.StringRes
import androidx.room.withTransaction
import com.example.BuildConfig
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.model.OvertimeRecord
import com.example.data.model.PieceRateRecord
import com.example.data.model.TrashItem
import com.example.data.model.UserProfile
import com.example.util.CloudSyncPolicy
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager
import com.example.util.NetworkMonitor
import com.example.util.RestoreMode
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

data class CloudConsentStatus(
    val hasConsent: Boolean,
    val consentTimestamp: Long = 0L,
    val rootExists: Boolean = false,
    val rootData: Map<String, Any?>? = null
)

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> cont.resumeWith(Result.success(result)) }
        addOnFailureListener { exception -> cont.resumeWith(Result.failure(exception)) }
        addOnCanceledListener { cont.cancel() }
    }

class OvertimeRepository(private val database: AppDatabase, private val context: android.content.Context? = null) {

    private val overtimeDao = database.overtimeDao()
    private val pieceRateDao = database.pieceRateDao()
    private val userProfileDao = database.userProfileDao()
    private val monthlySalaryDao = database.monthlySalaryDao()
    private val trashDao = database.trashDao()
    private val deductionDao = database.deductionDao()

    private fun localizedString(@StringRes resId: Int, fallback: String, vararg args: Any): String {
        val ctx = resolveContext() ?: return if (args.isEmpty()) fallback else String.format(Locale.US, fallback, *args)
        return try {
            val lctx = LanguageManager.getLocalizedContext(ctx)
            if (args.isEmpty()) lctx.getString(resId) else lctx.getString(resId, *args)
        } catch (_: Exception) {
            if (args.isEmpty()) fallback else String.format(Locale.US, fallback, *args)
        }
    }

    val allTrashItems: Flow<List<TrashItem>> = trashDao.getAllTrashItems()

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeSyncJobs = ConcurrentHashMap.newKeySet<Job>()
    private val syncMutex = Mutex()
    private val tombstoneLock = Any()
    private val inMemoryTombstoneSets = mutableMapOf<String, MutableSet<String>>()
    @Volatile
    private var inMemoryClearTrashBefore: Long = 0L
    private val inMemoryLongPrefs = mutableMapOf<String, Long>()
    private val inMemoryBoolPrefs = mutableMapOf<String, Boolean>()
    private val idCounter = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis() * 1000L)

    private fun launchTrackedSync(block: suspend () -> Unit): Job {
        val job = syncScope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("OvertimeRepository", "Tracked background sync job failed: ${e.message}")
            }
        }
        activeSyncJobs.add(job)
        job.invokeOnCompletion { activeSyncJobs.remove(job) }
        job.start()
        return job
    }

    suspend fun awaitPendingCloudWrites(timeoutMs: Long = 10_000L): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            while (true) {
                val snapshot = activeSyncJobs.toList()
                if (snapshot.isEmpty()) break
                snapshot.joinAll()
            }
            true
        } ?: false
    }

    private fun generateUniqueLocalId(): Long {
        while (true) {
            val prev = idCounter.get()
            val nowBase = System.currentTimeMillis() * 1000L
            val next = if (nowBase > prev) nowBase else prev + 1L
            if (idCounter.compareAndSet(prev, next)) return next
        }
    }

    private fun resolveContext(): android.content.Context? =
        context ?: try { com.example.OvertimeApp.instance } catch (_: Exception) { null }

    private fun tombstonePrefs(): android.content.SharedPreferences? =
        resolveContext()?.getSharedPreferences("overtime_tombstones", android.content.Context.MODE_PRIVATE)

    private fun getTombstoneSet(key: String): Set<String> = synchronized(tombstoneLock) {
        val prefs = tombstonePrefs()
        if (prefs != null) {
            prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
        } else {
            inMemoryTombstoneSets[key]?.toSet() ?: emptySet()
        }
    }

    private fun addToTombstoneSet(key: String, value: String) {
        synchronized(tombstoneLock) {
            if (value.isBlank()) return
            val prefs = tombstonePrefs()
            if (prefs != null) {
                val updated = HashSet(prefs.getStringSet(key, emptySet()) ?: emptySet())
                updated.add(value)
                prefs.edit().putStringSet(key, updated).apply()
            } else {
                inMemoryTombstoneSets.getOrPut(key) { mutableSetOf() }.add(value)
            }
        }
    }

    private fun removeFromTombstoneSet(key: String, value: String) {
        synchronized(tombstoneLock) {
            if (value.isBlank()) return
            val prefs = tombstonePrefs()
            if (prefs != null) {
                val existing = prefs.getStringSet(key, null) ?: return
                if (value in existing) {
                    val updated = HashSet(existing)
                    updated.remove(value)
                    prefs.edit().putStringSet(key, updated).apply()
                }
            } else {
                inMemoryTombstoneSets[key]?.remove(value)
            }
        }
    }

    private fun removeAllFromTombstoneSet(key: String, values: Collection<String>) {
        synchronized(tombstoneLock) {
            if (values.isEmpty()) return
            val prefs = tombstonePrefs()
            if (prefs != null) {
                val existing = prefs.getStringSet(key, null) ?: return
                val updated = HashSet(existing)
                if (updated.removeAll(values.toSet())) {
                    prefs.edit().putStringSet(key, updated).apply()
                }
            } else {
                inMemoryTombstoneSets[key]?.removeAll(values.toSet())
            }
        }
    }

    private fun getClearAllTrashBefore(): Long = synchronized(tombstoneLock) {
        tombstonePrefs()?.getLong("clear_all_trash_before", inMemoryClearTrashBefore) ?: inMemoryClearTrashBefore
    }

    private fun setClearAllTrashBefore(timestamp: Long) = synchronized(tombstoneLock) {
        inMemoryClearTrashBefore = timestamp
        tombstonePrefs()?.edit()?.putLong("clear_all_trash_before", timestamp)?.apply()
    }

    private fun clearAllTombstones() = synchronized(tombstoneLock) {
        inMemoryTombstoneSets.clear()
        inMemoryClearTrashBefore = 0L
        tombstonePrefs()?.edit()?.clear()?.apply()
    }

    private fun isStrictlyOnlineNow(): Boolean {
        val ctx = resolveContext() ?: return false
        return NetworkMonitor.isStrictlyOnline(ctx)
    }

    private fun syncPrefs(): android.content.SharedPreferences? =
        resolveContext()?.getSharedPreferences("overtime_sync_prefs", android.content.Context.MODE_PRIVATE)

    private fun getBoolSyncPref(key: String, default: Boolean = false): Boolean = synchronized(tombstoneLock) {
        val prefs = syncPrefs()
        prefs?.getBoolean(key, default) ?: (inMemoryBoolPrefs[key] ?: default)
    }

    private fun hasSyncPrefKey(key: String): Boolean = synchronized(tombstoneLock) {
        val prefs = syncPrefs()
        prefs?.contains(key) ?: inMemoryBoolPrefs.containsKey(key)
    }

    private fun setBoolSyncPref(key: String, value: Boolean) = synchronized(tombstoneLock) {
        inMemoryBoolPrefs[key] = value
        syncPrefs()?.edit()?.putBoolean(key, value)?.apply()
    }

    private fun getLongSyncPref(key: String, default: Long = 0L): Long = synchronized(tombstoneLock) {
        val prefs = syncPrefs()
        prefs?.getLong(key, default) ?: (inMemoryLongPrefs[key] ?: default)
    }

    private fun setLongSyncPref(key: String, value: Long) = synchronized(tombstoneLock) {
        inMemoryLongPrefs[key] = value
        syncPrefs()?.edit()?.putLong(key, value)?.apply()
    }

    private fun clearAllSyncPrefs() = synchronized(tombstoneLock) {
        inMemoryLongPrefs.clear()
        inMemoryBoolPrefs.clear()
        syncPrefs()?.edit()?.clear()?.apply()
    }

    private fun hasCompletedInitialRestore(uid: String): Boolean =
        getBoolSyncPref("initial_restore_done_$uid", false)

    private fun hasInitialRestoreKeyRecorded(uid: String): Boolean =
        hasSyncPrefKey("initial_restore_done_$uid")

    private fun setInitialRestoreCompleted(uid: String, done: Boolean) =
        setBoolSyncPref("initial_restore_done_$uid", done)

    private fun getLastKnownCloudSyncTime(uid: String): Long =
        getLongSyncPref("last_known_cloud_sync_time_$uid", 0L)

    private fun setLastKnownCloudSyncTime(uid: String, timestamp: Long) =
        setLongSyncPref("last_known_cloud_sync_time_$uid", timestamp)

    private fun getCollectionWatermark(uid: String, collectionKey: String): Long =
        getLongSyncPref("watermark_${collectionKey}_$uid", 0L)

    private fun setCollectionWatermark(uid: String, collectionKey: String, timestamp: Long) =
        setLongSyncPref("watermark_${collectionKey}_$uid", timestamp)

    private fun isPendingProfileSync(): Boolean = getBoolSyncPref("pending_profile_sync", false)

    private fun setPendingProfileSync(pending: Boolean) = setBoolSyncPref("pending_profile_sync", pending)

    internal fun getDeletedOtDates(): Set<String> = getTombstoneSet("deleted_ot_dates")
    internal fun getDeletedPieceIds(): Set<String> = getTombstoneSet("deleted_piece_ids")
    internal fun getDeletedDeductionIds(): Set<String> = getTombstoneSet("deleted_deduction_ids")
    internal fun getDeletedTrashIds(): Set<String> = getTombstoneSet("deleted_trash_ids")
    internal fun getPendingOtSyncDates(): Set<String> = getTombstoneSet("pending_ot_sync_dates")
    internal fun getPendingPieceSyncIds(): Set<String> = getTombstoneSet("pending_piece_sync_ids")
    internal fun getPendingDeductionSyncIds(): Set<String> = getTombstoneSet("pending_deduction_sync_ids")
    internal fun getPendingMonthlySyncYms(): Set<String> = getTombstoneSet("pending_monthly_sync_yms")
    internal fun getPendingTrashSyncIds(): Set<String> = getTombstoneSet("pending_trash_sync_ids")

    fun hasPendingLocalChanges(): Boolean {
        return isPendingProfileSync() ||
            getPendingOtSyncDates().isNotEmpty() ||
            getPendingPieceSyncIds().isNotEmpty() ||
            getPendingDeductionSyncIds().isNotEmpty() ||
            getPendingMonthlySyncYms().isNotEmpty() ||
            getPendingTrashSyncIds().isNotEmpty() ||
            getDeletedOtDates().isNotEmpty() ||
            getDeletedPieceIds().isNotEmpty() ||
            getDeletedDeductionIds().isNotEmpty() ||
            getDeletedTrashIds().isNotEmpty() ||
            getClearAllTrashBefore() > 0L
    }

    fun hasCompletedInitialRestoreForCurrentUser(): Boolean {
        val uid = getDocId() ?: return false
        return hasCompletedInitialRestore(uid)
    }

    private fun getLocalProfileModifiedAt(): Long = getLongSyncPref("local_profile_modified_at", 0L)

    private fun setLocalProfileModifiedAt(timestamp: Long) = setLongSyncPref("local_profile_modified_at", timestamp)

    init {
        launchTrackedSync {
            try {
                overtimeDao.cleanupDuplicateDates()
                purgeExpiredTrash()
                val current = userProfileDao.getUserProfileOnce()
                if (current != null) {
                    val isDummyProfile = current.userName == "মোঃ শ্রমিক ভাই" ||
                                          current.companyName == "আমান টেক্সটাইল লিমিটেড" ||
                                          current.workerId == "EMP-8492"

                    if (isDummyProfile) {
                        val cleaned = current.copy(
                            userName = "",
                            companyName = "",
                            designation = "",
                            workerId = "",
                            grade = "",
                            basicSalary = 0.0,
                            houseRent = 0.0,
                            medicalAllowance = 750.0,
                            conveyanceAllowance = 0.0,
                            foodAllowance = 0.0,
                            attendanceBonus = 0.0,
                            nightBillRate = 0.0,
                            tiffinBillRate = 0.0,
                            monthlyOtTargetHours = 0.0
                        )
                        userProfileDao.insertOrUpdateProfile(cleaned)
                    } else if (!current.isCustomSalary && current.medicalAllowance == 1250.0) {
                        userProfileDao.insertOrUpdateProfile(current.copy(medicalAllowance = 750.0))
                    }
                }
                val currentCalendarYm = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
                autoFinalizePastMonthSnapshots(currentCalendarYm)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("OvertimeRepository", "Repository init housekeeping failed: ${e.message}")
            }
        }
    }

    val userProfile: Flow<UserProfile?> = userProfileDao.getUserProfile()

    suspend fun getProfileOnce(): UserProfile {
        return userProfileDao.getUserProfileOnce() ?: UserProfile()
    }

    fun triggerPendingSync() {
        if (!hasPendingLocalChanges()) return
        if (!isStrictlyOnlineNow()) return
        if (getDocId() == null) return
        launchTrackedSync {
            syncPendingMutationsIfOnline()
        }
    }

    suspend fun saveProfile(profile: UserProfile, autoSync: Boolean = true) {
        setLocalProfileModifiedAt(System.currentTimeMillis())
        setPendingProfileSync(true)
        userProfileDao.insertOrUpdateProfile(profile)
        if (autoSync) {
            triggerPendingSync()
        }
    }

    fun getMonthlySalarySettings(yearMonth: String): Flow<com.example.data.model.MonthlySalarySettings?> {
        return monthlySalaryDao.getSettingsForMonth(yearMonth)
    }

    suspend fun getMonthlySalarySettingsOnce(yearMonth: String): com.example.data.model.MonthlySalarySettings? {
        return monthlySalaryDao.getSettingsForMonthOnce(yearMonth)
    }

    suspend fun saveMonthlySalarySettings(settings: com.example.data.model.MonthlySalarySettings, autoSync: Boolean = true) {
        monthlySalaryDao.insertOrUpdate(settings)
        addToTombstoneSet("pending_monthly_sync_yms", settings.yearMonth)
        if (autoSync) {
            triggerPendingSync()
        }
    }

    fun hasSnapshotWorthyProfile(profile: UserProfile): Boolean {
        return profile.basicSalary > 0.0 ||
            profile.customOtRate > 0.0 ||
            profile.nightBillRate > 0.0 ||
            profile.tiffinBillRate > 0.0 ||
            profile.attendanceBonus > 0.0 ||
            profile.monthlyOtTargetHours > 0.0 ||
            profile.userName.isNotBlank() ||
            profile.companyName.isNotBlank() ||
            profile.designation.isNotBlank() ||
            profile.workerId.isNotBlank() ||
            profile.grade.isNotBlank()
    }

    fun buildSnapshotFromProfile(
        ym: String,
        profile: UserProfile,
        isLocked: Boolean,
        lockedAt: Long,
        updatedAt: Long
    ): com.example.data.model.MonthlySalarySettings {
        val otRate = LaborLawCalculator.getHourlyOtRate(profile)
        val autoResult = if (!profile.isCustomSalary && profile.basicSalary > 0.0) {
            LaborLawCalculator.calculateAutoAllowances(profile.basicSalary)
        } else null
        val allowances = if (autoResult != null) {
            autoResult.houseRent + autoResult.medicalAllowance + autoResult.conveyanceAllowance + autoResult.foodAllowance
        } else {
            0.0
        }
        val gross = if (profile.basicSalary > 0.0) profile.basicSalary + allowances else 0.0

        return com.example.data.model.MonthlySalarySettings(
            yearMonth = ym,
            basicSalary = profile.basicSalary,
            houseRent = if (profile.isCustomSalary || profile.basicSalary <= 0.0) 0.0 else (autoResult?.houseRent ?: 0.0),
            medicalAllowance = if (profile.isCustomSalary || profile.basicSalary <= 0.0) 0.0 else (autoResult?.medicalAllowance ?: 0.0),
            conveyanceAllowance = if (profile.isCustomSalary || profile.basicSalary <= 0.0) 0.0 else (autoResult?.conveyanceAllowance ?: 0.0),
            foodAllowance = if (profile.isCustomSalary || profile.basicSalary <= 0.0) 0.0 else (autoResult?.foodAllowance ?: 0.0),
            grossSalary = gross,
            hourlyOtRate = otRate,
            customOtRate = profile.customOtRate,
            monthlyOtTargetHours = profile.monthlyOtTargetHours,
            attendanceBonus = profile.attendanceBonus,
            maxAbsentForBonus = profile.maxAbsentForBonus,
            maxLeaveForBonus = profile.maxLeaveForBonus,
            nightBillRate = profile.nightBillRate,
            tiffinBillRate = profile.tiffinBillRate,
            isCustomSalary = profile.isCustomSalary,
            userName = profile.userName,
            companyName = profile.companyName,
            designation = profile.designation,
            workerId = profile.workerId,
            grade = profile.grade,
            isLocked = isLocked,
            lockedAt = lockedAt,
            updatedAt = updatedAt
        )
    }

    suspend fun ensureMonthSnapshotExists(
        ym: String,
        profile: UserProfile,
        currentCalendarYm: String = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
    ): com.example.data.model.MonthlySalarySettings? {
        val existing = monthlySalaryDao.getSettingsForMonthOnce(ym)
        if (existing != null) return existing
        if (!hasSnapshotWorthyProfile(profile)) return null

        val now = System.currentTimeMillis()
        val isLocked = com.example.util.MonthLockPolicy.resolveIsMonthLocked(ym, null)
        val snapshot = buildSnapshotFromProfile(
            ym = ym,
            profile = profile,
            isLocked = isLocked && ym < currentCalendarYm,
            lockedAt = if (isLocked && ym < currentCalendarYm) now else 0L,
            updatedAt = now
        )
        saveMonthlySalarySettings(snapshot)
        return snapshot
    }

    suspend fun setMonthLockStatus(yearMonth: String, isLocked: Boolean, autoSync: Boolean = true) {
        val now = System.currentTimeMillis()
        val lockedAt = if (isLocked) now else 0L
        val existing = monthlySalaryDao.getSettingsForMonthOnce(yearMonth)
        if (existing != null) {
            monthlySalaryDao.updateLockStatus(yearMonth, isLocked, lockedAt, now)
            addToTombstoneSet("pending_monthly_sync_yms", yearMonth)
            if (autoSync) {
                triggerPendingSync()
            }
        } else {
            val profile = getProfileOnce()
            val snapshot = buildSnapshotFromProfile(
                ym = yearMonth,
                profile = profile,
                isLocked = isLocked,
                lockedAt = lockedAt,
                updatedAt = now
            )
            saveMonthlySalarySettings(snapshot, autoSync = autoSync)
        }
    }

    suspend fun freezeMonthSnapshotIfMissing(
        ym: String,
        profile: UserProfile,
        isLocked: Boolean,
        now: Long = System.currentTimeMillis(),
        autoSync: Boolean = true
    ): com.example.data.model.MonthlySalarySettings? {
        val existing = monthlySalaryDao.getSettingsForMonthOnce(ym)
        if (existing != null) return existing
        if (!hasSnapshotWorthyProfile(profile)) return null

        val snapshot = buildSnapshotFromProfile(
            ym = ym,
            profile = profile,
            isLocked = isLocked,
            lockedAt = if (isLocked) now else 0L,
            updatedAt = now
        )
        saveMonthlySalarySettings(snapshot, autoSync = autoSync)
        return snapshot
    }

    suspend fun autoFinalizePastMonthSnapshots(currentCalendarYm: String, autoSync: Boolean = true) {
        withContext(Dispatchers.IO) {
            try {
                val profile = getProfileOnce()
                val otMonths = overtimeDao.getAllYearMonths()
                val pieceMonths = pieceRateDao.getAllYearMonths()
                val deductionMonths = deductionDao.getAllYearMonths()
                val snapshotMonths = monthlySalaryDao.getAllSettings().map { it.yearMonth }
                val prevYm = com.example.util.MonthLockPolicy.getPreviousCalendarYearMonth()
                val baseMonths = otMonths + pieceMonths + deductionMonths + snapshotMonths
                val allMonths = (if (prevYm < currentCalendarYm && hasSnapshotWorthyProfile(profile)) {
                    baseMonths + prevYm
                } else {
                    baseMonths
                })
                    .filter { it < currentCalendarYm }
                    .distinct()

                val isPrevInGrace = com.example.util.MonthLockPolicy.isWithinPreviousMonthGracePeriod(prevYm)
                var anyChanged = false

                for (ym in allMonths) {
                    val existing = monthlySalaryDao.getSettingsForMonthOnce(ym)
                    val isGraceMonth = isPrevInGrace && ym == prevYm
                    val now = System.currentTimeMillis()
                    if (existing == null) {
                        val shouldLock = !isGraceMonth
                        val snapshot = buildSnapshotFromProfile(
                            ym = ym,
                            profile = profile,
                            isLocked = shouldLock,
                            lockedAt = if (shouldLock) now else 0L,
                            updatedAt = now
                        )
                        saveMonthlySalarySettings(snapshot, autoSync = false)
                        anyChanged = true
                    } else if (!existing.isLocked && !isGraceMonth) {
                        setMonthLockStatus(ym, true, autoSync = false)
                        anyChanged = true
                    }
                }
                if (anyChanged && autoSync) {
                    triggerPendingSync()
                }
            } catch (e: Exception) {
                Log.w("OvertimeRepository", "autoFinalizePastMonthSnapshots failed: ${e.message}")
            }
        }
    }

    // Deductions (Advance & Apon Bazar)
    fun getDeductionsForMonth(yearMonth: String): Flow<List<com.example.data.model.DeductionRecord>> {
        return deductionDao.getDeductionsForMonth(yearMonth)
    }

    suspend fun saveDeduction(record: com.example.data.model.DeductionRecord, autoSync: Boolean = true): Long {
        val recordWithId = if (record.id == 0L) record.copy(id = generateUniqueLocalId()) else record
        val idStr = recordWithId.id.toString()
        removeFromTombstoneSet("deleted_deduction_ids", idStr)
        val id = deductionDao.insertOrUpdate(recordWithId)
        addToTombstoneSet("pending_deduction_sync_ids", id.toString())
        if (autoSync) {
            triggerPendingSync()
        }
        return id
    }

    suspend fun getDeductionById(id: Long): com.example.data.model.DeductionRecord? {
        return deductionDao.getDeductionById(id)
    }

    suspend fun deleteDeduction(id: Long) {
        val idStr = id.toString()
        removeFromTombstoneSet("pending_deduction_sync_ids", idStr)
        addToTombstoneSet("deleted_deduction_ids", idStr)
        deductionDao.deleteById(id)
        triggerPendingSync()
    }

    // Overtime
    fun getOvertimeRecords(yearMonth: String): Flow<List<OvertimeRecord>> {
        return overtimeDao.getRecordsByMonth(yearMonth)
    }

    suspend fun getOvertimeRecordsOnce(yearMonth: String): List<OvertimeRecord> {
        return overtimeDao.getRecordsByMonthOnce(yearMonth)
    }

    suspend fun updateRunningMonthOvertimeRates(yearMonth: String, newRate: Double, autoSync: Boolean = true) {
        try {
            val records = overtimeDao.getRecordsByMonthOnce(yearMonth)
            if (records.isEmpty()) return
            var anyUpdated = false
            database.withTransaction {
                for (rec in records) {
                    val newCalc = rec.otHours * newRate
                    if (rec.hourlyRateSnapshot != newRate || rec.calculatedAmount != newCalc) {
                        val updated = rec.copy(
                            hourlyRateSnapshot = newRate,
                            calculatedAmount = newCalc
                        )
                        overtimeDao.insertRecord(updated)
                        removeFromTombstoneSet("deleted_ot_dates", updated.date)
                        addToTombstoneSet("pending_ot_sync_dates", updated.date)
                        anyUpdated = true
                    }
                }
            }
            if (anyUpdated && autoSync) {
                triggerPendingSync()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("OvertimeRepository", "Error updating running month overtime rates: ${e.message}")
        }
    }

    fun getAllOvertimeRecords(): Flow<List<OvertimeRecord>> {
        return overtimeDao.getAllRecords()
    }

    suspend fun insertOrUpdateOvertime(record: OvertimeRecord, autoSync: Boolean = true): Long {
        val existing = overtimeDao.getRecordByDate(record.date)
        val targetId = if (record.id != 0L) {
            record.id
        } else if (existing != null) {
            existing.id
        } else {
            0L
        }
        val recordToSave = record.copy(id = targetId)
        removeFromTombstoneSet("deleted_ot_dates", recordToSave.date)
        val savedId = overtimeDao.insertRecord(recordToSave)
        addToTombstoneSet("pending_ot_sync_dates", recordToSave.date)
        if (autoSync) {
            triggerPendingSync()
        }
        return savedId
    }

    suspend fun getOvertimeById(id: Long): OvertimeRecord? {
        return overtimeDao.getRecordById(id)
    }

    suspend fun getOvertimeByDate(date: String): OvertimeRecord? {
        return overtimeDao.getRecordByDate(date)
    }

    suspend fun deleteOvertime(id: Long) {
        val existing = overtimeDao.getRecordById(id) ?: return
        removeFromTombstoneSet("pending_ot_sync_dates", existing.date)
        addToTombstoneSet("deleted_ot_dates", existing.date)
        overtimeDao.deleteRecordById(id)
        triggerPendingSync()
    }

    // Piece Rate
    fun getPieceRateRecords(yearMonth: String): Flow<List<PieceRateRecord>> {
        return pieceRateDao.getPieceRecordsByMonth(yearMonth)
    }

    suspend fun getPieceRateById(id: Long): PieceRateRecord? {
        return pieceRateDao.getPieceRecordById(id)
    }

    suspend fun insertPieceRate(record: PieceRateRecord, autoSync: Boolean = true): Long {
        val recordWithId = if (record.id == 0L) record.copy(id = generateUniqueLocalId()) else record
        removeFromTombstoneSet("deleted_piece_ids", recordWithId.id.toString())
        val savedId = pieceRateDao.insertPieceRecord(recordWithId)
        addToTombstoneSet("pending_piece_sync_ids", savedId.toString())
        if (autoSync) {
            triggerPendingSync()
        }
        return savedId
    }

    suspend fun deletePieceRate(id: Long) {
        val idStr = id.toString()
        removeFromTombstoneSet("pending_piece_sync_ids", idStr)
        addToTombstoneSet("deleted_piece_ids", idStr)
        pieceRateDao.deletePieceRecordById(id)
        triggerPendingSync()
    }

    suspend fun cleanupDuplicateDates() {
        try {
            overtimeDao.cleanupDuplicateDates()
        } catch (e: Exception) {
            Log.w("OvertimeRepository", "Error cleaning duplicate dates: ${e.message}")
        }
    }

    suspend fun clearAllLocalData(): Boolean {
        return try {
            overtimeDao.deleteAllRecords()
            pieceRateDao.deleteAllPieceRecords()
            monthlySalaryDao.deleteAll()
            deductionDao.deleteAll()
            trashDao.clearAllTrash()
            clearAllTombstones()
            clearAllSyncPrefs()
            scheduleTrashAutoDelete()

            val current = userProfileDao.getUserProfileOnce()
            val cleanProfile = UserProfile(
                id = 1,
                userName = "",
                companyName = "",
                designation = "",
                workerId = "",
                grade = "",
                isCustomSalary = false,
                basicSalary = 0.0,
                houseRent = 0.0,
                medicalAllowance = 0.0,
                conveyanceAllowance = 0.0,
                foodAllowance = 0.0,
                attendanceBonus = 0.0,
                maxAbsentForBonus = 0,
                maxLeaveForBonus = null,
                nightBillRate = 0.0,
                tiffinBillRate = 0.0,
                customOtRate = 0.0,
                monthlyOtTargetHours = 0.0,
                language = if (current != null) LanguageManager.normalizeProfileLanguage(current.language) else LanguageManager.DEFAULT_INSTALL_TAG,
                hasAcceptedPrivacyPolicy = false,
                privacyPolicyAcceptedAt = 0L,
                lastCloudSyncTime = 0L,
                cloudSyncStatus = localizedString(R.string.sync_status_local_unauthenticated, "Not logged in (Local)")
            )
            userProfileDao.insertOrUpdateProfile(cleanProfile)
            true
        } catch (e: Exception) {
            Log.e("OvertimeRepository", "Error clearing local data", e)
            false
        }
    }

    companion object {
        const val PRIVACY_POLICY_VERSION = "2026-10-30"
        private const val CONSENT_DECLARATION =
            "User agreed to Terms and Privacy Policy before storing salary and overtime data in Firestore."

        fun formatConsentDateTimeBD(millis: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Dhaka")
            }
            return "${sdf.format(Date(millis))} (Asia/Dhaka, UTC+06:00)"
        }

        fun formatConsentDateTimeIso(millis: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return sdf.format(Date(millis))
        }

        fun buildConsentAuditMap(
            isGiven: Boolean,
            consentMillis: Long,
            includeServerTimestamp: Boolean,
            existingServerTimestamp: Any? = null
        ): HashMap<String, Any> {
            val audit = hashMapOf<String, Any>(
                "isConsentGiven" to isGiven,
                "policyVersion" to PRIVACY_POLICY_VERSION,
                "appVersion" to BuildConfig.VERSION_NAME,
                "consentDeclaration" to CONSENT_DECLARATION
            )
            if (consentMillis > 0L) {
                audit["consentTimestamp"] = consentMillis
                audit["consentDateTimeBD"] = formatConsentDateTimeBD(consentMillis)
                audit["consentDateTimeIso"] = formatConsentDateTimeIso(consentMillis)
            }
            if (includeServerTimestamp) {
                audit["consentServerTimestamp"] = FieldValue.serverTimestamp()
            } else if (existingServerTimestamp != null) {
                audit["consentServerTimestamp"] = existingServerTimestamp
            }
            return audit
        }
    }

    private fun extractConsentTimestampMillis(data: Map<String, Any?>?): Long {
        if (data == null) return 0L
        val directMillis = (data["privacyPolicyAcceptedAt"] as? Number)?.toLong() ?: 0L
        if (directMillis > 0L) return directMillis
        val consentAudit = data["consentAudit"] as? Map<*, *>
        val auditMillis = (consentAudit?.get("consentTimestamp") as? Number)?.toLong() ?: 0L
        if (auditMillis > 0L) return auditMillis
        val serverTs = consentAudit?.get("consentServerTimestamp") as? Timestamp
        val serverMillis = serverTs?.toDate()?.time ?: 0L
        return if (serverMillis > 0L) serverMillis else 0L
    }

    suspend fun checkUserConsentInCloud(uid: String): Boolean =
        checkUserConsentDetailsInCloud(uid).hasConsent

    suspend fun checkUserConsentDetailsInCloud(uid: String): CloudConsentStatus = withContext(Dispatchers.IO) {
        if (!isStrictlyOnlineNow()) {
            return@withContext CloudConsentStatus(hasConsent = false, consentTimestamp = 0L)
        }
        try {
            val snap = firestore.collection("users").document(uid).get().awaitResult()
            if (snap.exists()) {
                val data = snap.data
                val hasConsent = data?.get("hasAcceptedPrivacyPolicy") as? Boolean ?: false
                val consentAudit = data?.get("consentAudit") as? Map<*, *>
                val auditConsent = consentAudit?.get("isConsentGiven") as? Boolean ?: false
                val consented = hasConsent || auditConsent

                var resolvedTimestamp = extractConsentTimestampMillis(data)

                if (consented) {
                    val missingDirectTimestamp = ((data?.get("privacyPolicyAcceptedAt") as? Number)?.toLong() ?: 0L) <= 0L
                    val missingAuditTimestamp = ((consentAudit?.get("consentTimestamp") as? Number)?.toLong() ?: 0L) <= 0L
                    val missingServerTimestamp = consentAudit?.get("consentServerTimestamp") == null
                    val missingDateTimeBD = (consentAudit?.get("consentDateTimeBD") as? String).isNullOrBlank()
                    val missingDateTimeIso = (consentAudit?.get("consentDateTimeIso") as? String).isNullOrBlank()
                    val missingAppVersion = (consentAudit?.get("appVersion") as? String).isNullOrBlank()
                    val missingPolicyVersion = (consentAudit?.get("policyVersion") as? String).isNullOrBlank()
                    val missingDeclaration = (consentAudit?.get("consentDeclaration") as? String).isNullOrBlank()

                    if (missingDirectTimestamp || missingAuditTimestamp || missingServerTimestamp ||
                        missingDateTimeBD || missingDateTimeIso || missingAppVersion ||
                        missingPolicyVersion || missingDeclaration || !hasConsent || !auditConsent
                    ) {
                        val effectiveTime = if (resolvedTimestamp > 0L) resolvedTimestamp else System.currentTimeMillis()
                        if (resolvedTimestamp <= 0L) {
                            resolvedTimestamp = effectiveTime
                        }

                        val backfillAudit = hashMapOf<String, Any>()
                        if (!auditConsent) backfillAudit["isConsentGiven"] = true
                        if (missingAuditTimestamp) backfillAudit["consentTimestamp"] = effectiveTime
                        if (missingServerTimestamp) backfillAudit["consentServerTimestamp"] = FieldValue.serverTimestamp()
                        if (missingDateTimeBD) backfillAudit["consentDateTimeBD"] = formatConsentDateTimeBD(effectiveTime)
                        if (missingDateTimeIso) backfillAudit["consentDateTimeIso"] = formatConsentDateTimeIso(effectiveTime)
                        if (missingPolicyVersion) backfillAudit["policyVersion"] = PRIVACY_POLICY_VERSION
                        if (missingAppVersion) backfillAudit["appVersion"] = BuildConfig.VERSION_NAME
                        if (missingDeclaration) backfillAudit["consentDeclaration"] = CONSENT_DECLARATION

                        val backfillMap = hashMapOf<String, Any>()
                        if (!hasConsent) backfillMap["hasAcceptedPrivacyPolicy"] = true
                        if (missingDirectTimestamp) backfillMap["privacyPolicyAcceptedAt"] = effectiveTime
                        if (backfillAudit.isNotEmpty()) backfillMap["consentAudit"] = backfillAudit

                        if (backfillMap.isNotEmpty()) {
                            try {
                                snap.reference.set(backfillMap, SetOptions.merge()).awaitResult()
                            } catch (backfillErr: Exception) {
                                Log.w("OvertimeRepository", "Consent backfill failed: ${backfillErr.message}")
                            }
                        }
                    }
                }

                CloudConsentStatus(
                    hasConsent = consented,
                    consentTimestamp = resolvedTimestamp,
                    rootExists = true,
                    rootData = data
                )
            } else {
                CloudConsentStatus(
                    hasConsent = false,
                    consentTimestamp = 0L,
                    rootExists = false,
                    rootData = null
                )
            }
        } catch (e: Exception) {
            Log.w("OvertimeRepository", "Error checking user consent in cloud: ${e.message}")
            CloudConsentStatus(hasConsent = false, consentTimestamp = 0L)
        }
    }

    suspend fun saveConsentToCloud(uid: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        try {
            val consentMap = hashMapOf<String, Any>(
                "hasAcceptedPrivacyPolicy" to true,
                "privacyPolicyAcceptedAt" to now,
                "consentAudit" to buildConsentAuditMap(
                    isGiven = true,
                    consentMillis = now,
                    includeServerTimestamp = true
                )
            )
            firestore.collection("users").document(uid).set(consentMap, SetOptions.merge()).awaitResult()
            markConsentGivenLocally(now)
            Log.d("OvertimeRepository", "Privacy consent recorded successfully in Firestore for uid: $uid")
        } catch (e: Exception) {
            Log.w("OvertimeRepository", "Error saving consent to cloud: ${e.message}")
            markConsentGivenLocally(now)
        }
    }

    suspend fun markConsentGivenLocally(timestamp: Long = System.currentTimeMillis()) {
        val current = userProfileDao.getUserProfileOnce() ?: UserProfile()
        userProfileDao.insertOrUpdateProfile(
            current.copy(
                hasAcceptedPrivacyPolicy = true,
                privacyPolicyAcceptedAt = timestamp
            )
        )
    }

    private suspend fun deleteCollectionInBatches(query: Query) {
        val snap = query.get().awaitResult()
        if (!snap.isEmpty) {
            for (chunk in snap.documents.chunked(400)) {
                val batch = firestore.batch()
                for (doc in chunk) {
                    batch.delete(doc.reference)
                }
                batch.commit().awaitResult()
            }
        }
    }

    suspend fun deleteAllUserData(isCloudSignedIn: Boolean): Boolean = withContext(Dispatchers.IO) {
        var cloudSuccess = true

        if (isCloudSignedIn) {
            val uid = auth.currentUser?.uid
            if (!uid.isNullOrBlank()) {
                val userDocRef = firestore.collection("users").document(uid)

                // STEP 1 (PROFILE WIPE WITH CONSENT PRESERVED IN CLOUD):
                // Reset and wipe all personal records (factory name, title, id, grade, salary, bonus, bills, etc.)
                // BUT preserve the Privacy Policy Consent record in Firestore so the user is never asked again.
                try {
                    val existingCloudData = try {
                        userDocRef.get().awaitResult().data
                    } catch (_: Exception) {
                        null
                    }
                    val existingCloudAudit = existingCloudData?.get("consentAudit") as? Map<*, *>
                    val existingServerTimestamp = existingCloudAudit?.get("consentServerTimestamp")
                    val existingCloudTime = extractConsentTimestampMillis(existingCloudData)

                    val currentLocal = userProfileDao.getUserProfileOnce()
                    val localConsentTime = currentLocal?.privacyPolicyAcceptedAt?.takeIf { it > 0L } ?: 0L
                    val consentTime = when {
                        existingCloudTime > 0L && localConsentTime > 0L -> minOf(existingCloudTime, localConsentTime)
                        existingCloudTime > 0L -> existingCloudTime
                        else -> localConsentTime
                    }

                    val preservedLanguage = currentLocal?.language?.takeIf { it.isNotBlank() }
                        ?.let { LanguageManager.normalizeProfileLanguage(it) }
                        ?: LanguageManager.DEFAULT_INSTALL_TAG

                    val emptyProfileWithConsent = hashMapOf<String, Any>(
                        "userName" to "",
                        "companyName" to "",
                        "designation" to "",
                        "workerId" to "",
                        "grade" to "",
                        "isCustomSalary" to false,
                        "basicSalary" to 0.0,
                        "houseRent" to 0.0,
                        "medicalAllowance" to 0.0,
                        "conveyanceAllowance" to 0.0,
                        "foodAllowance" to 0.0,
                        "attendanceBonus" to 0.0,
                        "maxAbsentForBonus" to 0,
                        "maxLeaveForBonus" to -1,
                        "nightBillRate" to 0.0,
                        "tiffinBillRate" to 0.0,
                        "customOtRate" to 0.0,
                        "monthlyOtTargetHours" to 0.0,
                        "language" to preservedLanguage,
                        "lastSyncTime" to 0L,
                        // CONSENT IS PERMANENTLY PRESERVED IN FIRESTORE PER USER REQUIREMENT:
                        "hasAcceptedPrivacyPolicy" to true,
                        "consentAudit" to buildConsentAuditMap(
                            isGiven = true,
                            consentMillis = consentTime,
                            includeServerTimestamp = (existingServerTimestamp == null),
                            existingServerTimestamp = existingServerTimestamp
                        )
                    )
                    if (consentTime > 0L) {
                        emptyProfileWithConsent["privacyPolicyAcceptedAt"] = consentTime
                    }
                    userDocRef.set(emptyProfileWithConsent).awaitResult()
                    Log.d("OvertimeRepository", "Root profile wiped while preserving consent in Firestore")
                } catch (e: Exception) {
                    cloudSuccess = false
                    Log.e("OvertimeRepository", "Failed wiping profile fields in root user doc: ${e.message}", e)
                }

                // STEP 2: Delete subcollections using safe batched writes (400 items per batch)
                try {
                    deleteCollectionInBatches(userDocRef.collection("overtime_records"))
                    deleteCollectionInBatches(userDocRef.collection("piece_rate_records"))
                    deleteCollectionInBatches(userDocRef.collection("monthly_salary_settings"))
                    deleteCollectionInBatches(userDocRef.collection("deduction_records"))
                    deleteCollectionInBatches(userDocRef.collection("trash_records"))
                } catch (e: Exception) {
                    cloudSuccess = false
                    Log.e("OvertimeRepository", "Failed deleting cloud subcollections: ${e.message}", e)
                }
            } else {
                cloudSuccess = false
            }
        }

        if (isCloudSignedIn && !cloudSuccess) {
            // Cloud wipe failed (e.g. no internet). Do NOT wipe local data to prevent silent desync!
            return@withContext false
        }

        // Clear all local data in Room database
        val localSuccess = clearAllLocalData()

        localSuccess
    }

    // --- Automatic Firebase Sync Engine ---

    private fun getDocId(profile: UserProfile? = null): String? {
        val uid = auth.currentUser?.uid
        if (!uid.isNullOrBlank()) return uid
        return null
    }

    private sealed class PendingBatchOp {
        abstract val docRef: DocumentReference
        abstract val onCommitted: () -> Unit

        data class SetMerge(
            override val docRef: DocumentReference,
            val data: Map<String, Any>,
            override val onCommitted: () -> Unit
        ) : PendingBatchOp()

        data class DeleteDoc(
            override val docRef: DocumentReference,
            override val onCommitted: () -> Unit
        ) : PendingBatchOp()
    }

    private fun isBlankDefaultProfile(profile: UserProfile): Boolean {
        return profile.lastCloudSyncTime == 0L &&
            profile.basicSalary == 0.0 &&
            profile.customOtRate == 0.0 &&
            profile.userName.isBlank() &&
            profile.workerId.isBlank() &&
            profile.companyName.isBlank() &&
            profile.nightBillRate == 0.0 &&
            profile.tiffinBillRate == 0.0 &&
            profile.attendanceBonus == 0.0 &&
            profile.monthlyOtTargetHours == 0.0
    }

    /**
     * Flushes ONLY queued dirty upserts & tombstones to Firestore in atomic 400-op WriteBatches.
     * Clears each chunk's dirty keys ONLY after its WriteBatch commit succeeds.
     * Piggybacks `lastSyncTime = writeSyncTime` inside the final batch so there are zero double writes.
     */
    private suspend fun flushPendingDirtyToCloudLocked(
        docId: String,
        userDocRef: DocumentReference,
        writeSyncTime: Long,
        allowDefaultProfilePush: Boolean = false
    ): Boolean {
        val ops = mutableListOf<PendingBatchOp>()

        // 1. Queued Deletes (always flush deletes first so items are never resurrected)
        for (date in getDeletedOtDates()) {
            ops.add(
                PendingBatchOp.DeleteDoc(
                    docRef = userDocRef.collection("overtime_records").document(date),
                    onCommitted = { removeFromTombstoneSet("deleted_ot_dates", date) }
                )
            )
        }
        for (pieceId in getDeletedPieceIds()) {
            ops.add(
                PendingBatchOp.DeleteDoc(
                    docRef = userDocRef.collection("piece_rate_records").document(pieceId),
                    onCommitted = { removeFromTombstoneSet("deleted_piece_ids", pieceId) }
                )
            )
        }
        for (dedId in getDeletedDeductionIds()) {
            ops.add(
                PendingBatchOp.DeleteDoc(
                    docRef = userDocRef.collection("deduction_records").document(dedId),
                    onCommitted = { removeFromTombstoneSet("deleted_deduction_ids", dedId) }
                )
            )
        }
        for (trId in getDeletedTrashIds()) {
            ops.add(
                PendingBatchOp.DeleteDoc(
                    docRef = userDocRef.collection("trash_records").document(trId),
                    onCommitted = { removeFromTombstoneSet("deleted_trash_ids", trId) }
                )
            )
        }

        // Handle explicit "Clear All Trash" request once cleanly (only when clearAllTrashBefore > 0L)
        val clearTrashBefore = getClearAllTrashBefore()
        var resetClearAllFlagPending = false
        if (clearTrashBefore > 0L) {
            val expiredOrClearedSnap = userDocRef.collection("trash_records")
                .whereLessThanOrEqualTo("deletedAt", clearTrashBefore)
                .get()
                .awaitResult()
            if (expiredOrClearedSnap.isEmpty) {
                setClearAllTrashBefore(0L)
            } else {
                resetClearAllFlagPending = true
                for (doc in expiredOrClearedSnap.documents) {
                    ops.add(
                        PendingBatchOp.DeleteDoc(
                            docRef = doc.reference,
                            onCommitted = {}
                        )
                    )
                }
            }
        }

        // 2. Queued Dirty Upserts
        val pendingOtDates = getPendingOtSyncDates()
        for (date in pendingOtDates) {
            val rec = overtimeDao.getRecordByDate(date)
            if (rec != null) {
                ops.add(
                    PendingBatchOp.SetMerge(
                        docRef = userDocRef.collection("overtime_records").document(rec.date),
                        data = overtimeToMap(rec, updateTime = writeSyncTime),
                        onCommitted = { removeFromTombstoneSet("pending_ot_sync_dates", date) }
                    )
                )
            } else {
                removeFromTombstoneSet("pending_ot_sync_dates", date)
            }
        }

        val pendingPieceIds = getPendingPieceSyncIds()
        for (idStr in pendingPieceIds) {
            val id = idStr.toLongOrNull()
            val rec = if (id != null) pieceRateDao.getPieceRecordById(id) else null
            if (rec != null) {
                ops.add(
                    PendingBatchOp.SetMerge(
                        docRef = userDocRef.collection("piece_rate_records").document(rec.id.toString()),
                        data = pieceRateToMap(rec, updateTime = writeSyncTime),
                        onCommitted = { removeFromTombstoneSet("pending_piece_sync_ids", idStr) }
                    )
                )
            } else {
                removeFromTombstoneSet("pending_piece_sync_ids", idStr)
            }
        }

        val pendingDeductionIds = getPendingDeductionSyncIds()
        for (idStr in pendingDeductionIds) {
            val id = idStr.toLongOrNull()
            val rec = if (id != null) deductionDao.getDeductionById(id) else null
            if (rec != null) {
                ops.add(
                    PendingBatchOp.SetMerge(
                        docRef = userDocRef.collection("deduction_records").document(rec.id.toString()),
                        data = deductionToMap(rec),
                        onCommitted = { removeFromTombstoneSet("pending_deduction_sync_ids", idStr) }
                    )
                )
            } else {
                removeFromTombstoneSet("pending_deduction_sync_ids", idStr)
            }
        }

        val pendingMonthlyYms = getPendingMonthlySyncYms()
        for (ym in pendingMonthlyYms) {
            val ms = monthlySalaryDao.getSettingsForMonthOnce(ym)
            if (ms != null) {
                val effectiveMs = if (ms.updatedAt < writeSyncTime) ms.copy(updatedAt = writeSyncTime) else ms
                ops.add(
                    PendingBatchOp.SetMerge(
                        docRef = userDocRef.collection("monthly_salary_settings").document(ms.yearMonth),
                        data = monthlySalaryToMap(effectiveMs),
                        onCommitted = { removeFromTombstoneSet("pending_monthly_sync_yms", ym) }
                    )
                )
            } else {
                removeFromTombstoneSet("pending_monthly_sync_yms", ym)
            }
        }

        val pendingTrashIds = getPendingTrashSyncIds()
        if (pendingTrashIds.isNotEmpty()) {
            val localTrashMap = trashDao.getAllTrashItemsOnce().associateBy { it.id.toString() }
            for (idStr in pendingTrashIds) {
                val tr = localTrashMap[idStr]
                if (tr != null) {
                    ops.add(
                        PendingBatchOp.SetMerge(
                            docRef = userDocRef.collection("trash_records").document(tr.id.toString()),
                            data = trashToMap(tr),
                            onCommitted = { removeFromTombstoneSet("pending_trash_sync_ids", idStr) }
                        )
                    )
                } else {
                    removeFromTombstoneSet("pending_trash_sync_ids", idStr)
                }
            }
        }

        // 3. Profile (Piggybacks lastSyncTime inside users/{uid})
        var profileWritten = false
        if (isPendingProfileSync()) {
            val latestProfile = getProfileOnce()
            if (!allowDefaultProfilePush && isBlankDefaultProfile(latestProfile)) {
                setPendingProfileSync(false)
                setLocalProfileModifiedAt(0L)
            } else {
                val profileForMap = latestProfile.copy(lastCloudSyncTime = writeSyncTime)
                ops.add(
                    PendingBatchOp.SetMerge(
                        docRef = userDocRef,
                        data = profileToMap(profileForMap),
                        onCommitted = {
                            setPendingProfileSync(false)
                            setLocalProfileModifiedAt(0L)
                        }
                    )
                )
                profileWritten = true
            }
        }

        if (ops.isEmpty()) {
            return false
        }

        if (!profileWritten) {
            ops.add(
                PendingBatchOp.SetMerge(
                    docRef = userDocRef,
                    data = mapOf("lastSyncTime" to writeSyncTime),
                    onCommitted = {}
                )
            )
        }

        for (chunk in ops.chunked(400)) {
            val batch = firestore.batch()
            for (op in chunk) {
                when (op) {
                    is PendingBatchOp.SetMerge -> batch.set(op.docRef, op.data, SetOptions.merge())
                    is PendingBatchOp.DeleteDoc -> batch.delete(op.docRef)
                }
            }
            batch.commit().awaitResult()
            for (op in chunk) {
                op.onCommitted()
            }
        }

        if (resetClearAllFlagPending) {
            setClearAllTrashBefore(0L)
        }
        return true
    }

    /**
     * Immediate batch sync for online user actions & automatic flush on reconnect.
     * Performs 0 Firestore reads when initial restore has already completed for this account.
     */
    suspend fun syncPendingMutationsIfOnline(): Boolean = withContext(Dispatchers.IO) {
        if (!hasPendingLocalChanges()) return@withContext false
        if (!isStrictlyOnlineNow()) return@withContext false
        val docId = getDocId() ?: return@withContext false

        syncMutex.withLock {
            if (!hasPendingLocalChanges() || !isStrictlyOnlineNow()) return@withLock false
            val currentProfile = getProfileOnce()
            if (!currentProfile.hasAcceptedPrivacyPolicy) return@withLock false

            // Ensure initial cloud restore runs before advancing sync watermarks on a fresh install
            if (!hasCompletedInitialRestore(docId) && !CloudSyncPolicy.shouldSeedLegacyUpgradeWatermark(hasInitialRestoreKeyRecorded(docId), currentProfile.lastCloudSyncTime)) {
                val res = fullAutoSyncAndRestoreLocked(forceFullSync = false)
                return@withLock res.first
            }

            try {
                val userDocRef = firestore.collection("users").document(docId)
                val now = System.currentTimeMillis()
                val knownCloudTime = maxOf(getLastKnownCloudSyncTime(docId), currentProfile.lastCloudSyncTime)
                val writeSyncTime = CloudSyncPolicy.computeMonotonicSyncTime(now, knownCloudTime)

                val didWrite = flushPendingDirtyToCloudLocked(
                    docId = docId,
                    userDocRef = userDocRef,
                    writeSyncTime = writeSyncTime,
                    allowDefaultProfilePush = false
                )
                if (didWrite) {
                    setLastKnownCloudSyncTime(docId, writeSyncTime)
                    val refreshedProfile = getProfileOnce()
                    userProfileDao.insertOrUpdateProfile(
                        refreshedProfile.copy(
                            lastCloudSyncTime = CloudSyncPolicy.resolveDisplayedSyncTimestamp(
                                previousSyncTime = refreshedProfile.lastCloudSyncTime,
                                transferOccurred = true,
                                transferTimeMillis = now
                            ),
                            cloudSyncStatus = localizedString(R.string.sync_status_active, "Auto Backup Active (Synced)")
                        )
                    )
                }
                didWrite
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("OvertimeRepository", "Pending batch sync deferred (offline/transient): ${e.message}")
                false
            }
        }
    }

    private fun getDoubleFromField(value: Any?): Double {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun getBooleanFromField(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is String -> value.toBoolean()
            else -> false
        }
    }

    // Full 2-Way Sync & Delta Restore Engine:
    // Checks ONLY 1 root doc (`users/{uid}`) on launch -> 0 collection reads & 0 writes when up-to-date.
    suspend fun fullAutoSyncAndRestore(
        forceFullSync: Boolean = false,
        prefetchedRootExists: Boolean? = null,
        prefetchedRootData: Map<String, Any?>? = null
    ): Pair<Boolean, String> = syncMutex.withLock {
        fullAutoSyncAndRestoreLocked(
            forceFullSync = forceFullSync,
            prefetchedRootExists = prefetchedRootExists,
            prefetchedRootData = prefetchedRootData
        )
    }

    private suspend fun fullAutoSyncAndRestoreLocked(
        forceFullSync: Boolean = false,
        prefetchedRootExists: Boolean? = null,
        prefetchedRootData: Map<String, Any?>? = null
    ): Pair<Boolean, String> {
        var currentProfile = getProfileOnce()
        val docId = getDocId(currentProfile)
            ?: return Pair(false, localizedString(R.string.sync_not_logged_in, "Not logged in"))

        if (!isStrictlyOnlineNow()) {
            return Pair(false, localizedString(R.string.sync_connection_error_fmt, "Cloud connection error: %1\$s", "Offline"))
        }

        val now = System.currentTimeMillis()
        val userDocRef = firestore.collection("users").document(docId)

        return try {
            // 1. Read ONLY 1 root user document (unless already prefetched during sign-in consent check)
            val rootExists: Boolean
            val rootData: Map<String, Any?>?
            if (prefetchedRootExists != null) {
                rootExists = prefetchedRootExists
                rootData = prefetchedRootData
            } else {
                val cloudProfileSnap = try {
                    userDocRef.get(Source.SERVER).awaitResult()
                } catch (_: Exception) {
                    userDocRef.get().awaitResult()
                }
                rootExists = cloudProfileSnap.exists()
                rootData = cloudProfileSnap.data
            }

            var cloudSyncTime = 0L
            var profileRestored = false
            var consentLocallyUpdated = false

            // Legacy app-upgrade seeding: existing signed-in users keep their existing watermarks
            // without triggering a redundant full re-download on first run after this update.
            if (CloudSyncPolicy.shouldSeedLegacyUpgradeWatermark(
                    hasInitialRestoreKeyRecorded = hasInitialRestoreKeyRecorded(docId),
                    localLastCloudSyncTime = currentProfile.lastCloudSyncTime
                )
            ) {
                val seedTime = currentProfile.lastCloudSyncTime
                setInitialRestoreCompleted(docId, true)
                setLastKnownCloudSyncTime(docId, seedTime)
                setCollectionWatermark(docId, "ot", seedTime)
                setCollectionWatermark(docId, "piece", seedTime)
                setCollectionWatermark(docId, "monthly", seedTime)
                setCollectionWatermark(docId, "deduction", seedTime)
                setCollectionWatermark(docId, "trash", seedTime)
            }

            val hasCompletedInitial = hasCompletedInitialRestore(docId)
            val preSyncKnownCloudSyncTime = getLastKnownCloudSyncTime(docId)
            val localModifiedAt = getLocalProfileModifiedAt()

            if (rootExists && rootData != null) {
                val cloudBasic = getDoubleFromField(rootData["basicSalary"])
                val cloudName = (rootData["userName"] as? String)?.trim() ?: ""
                val cloudCompany = (rootData["companyName"] as? String)?.trim() ?: ""
                val cloudDesignation = (rootData["designation"] as? String)?.trim() ?: ""
                val cloudWorkerId = (rootData["workerId"] as? String)?.trim() ?: ""
                val cloudGrade = (rootData["grade"] as? String)?.trim() ?: ""
                val cloudHouseRent = getDoubleFromField(rootData["houseRent"])
                val cloudMedical = getDoubleFromField(rootData["medicalAllowance"])
                val cloudConveyance = getDoubleFromField(rootData["conveyanceAllowance"])
                val cloudFood = getDoubleFromField(rootData["foodAllowance"])
                val cloudAttendanceBonus = getDoubleFromField(rootData["attendanceBonus"])
                val cloudNightBill = getDoubleFromField(rootData["nightBillRate"])
                val cloudTiffinBill = getDoubleFromField(rootData["tiffinBillRate"])
                val cloudCustomOt = getDoubleFromField(rootData["customOtRate"])
                val cloudTargetHours = getDoubleFromField(rootData["monthlyOtTargetHours"])
                val cloudIsCustom = getBooleanFromField(rootData["isCustomSalary"])
                val cloudMaxAbsent = ((rootData["maxAbsentForBonus"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
                val cloudMaxLeave = if (rootData.containsKey("maxLeaveForBonus")) {
                    val num = (rootData["maxLeaveForBonus"] as? Number)?.toInt()
                    if (num == null || num < 0) null else num
                } else null
                val cloudLang = (rootData["language"] as? String)?.trim() ?: ""
                cloudSyncTime = (rootData["lastSyncTime"] as? Number)?.toLong() ?: 0L

                val cloudConsentAudit = rootData["consentAudit"] as? Map<*, *>
                val cloudHasConsent = getBooleanFromField(rootData["hasAcceptedPrivacyPolicy"]) ||
                    (cloudConsentAudit?.get("isConsentGiven") as? Boolean == true)
                val cloudConsentTime = extractConsentTimestampMillis(rootData)
                val mergedConsent = currentProfile.hasAcceptedPrivacyPolicy || cloudHasConsent
                val mergedConsentTime = when {
                    currentProfile.privacyPolicyAcceptedAt > 0L && cloudConsentTime > 0L ->
                        minOf(currentProfile.privacyPolicyAcceptedAt, cloudConsentTime)
                    cloudConsentTime > 0L -> cloudConsentTime
                    else -> currentProfile.privacyPolicyAcceptedAt
                }

                if (mergedConsent != currentProfile.hasAcceptedPrivacyPolicy || mergedConsentTime != currentProfile.privacyPolicyAcceptedAt) {
                    currentProfile = currentProfile.copy(
                        hasAcceptedPrivacyPolicy = mergedConsent,
                        privacyPolicyAcceptedAt = mergedConsentTime
                    )
                    userProfileDao.insertOrUpdateProfile(currentProfile)
                    consentLocallyUpdated = true
                }

                if (!mergedConsent) {
                    return Pair(true, localizedString(R.string.sync_success_toast, "Auto cloud sync completed!"))
                }

                val cloudHasData = cloudBasic > 0.0 ||
                    cloudCustomOt > 0.0 ||
                    (cloudName.isNotBlank() && cloudName != "মোঃ শ্রমিক ভাই") ||
                    cloudCompany.isNotBlank() ||
                    cloudWorkerId.isNotBlank() ||
                    cloudDesignation.isNotBlank() ||
                    cloudNightBill > 0.0 ||
                    cloudTiffinBill > 0.0 ||
                    cloudAttendanceBonus > 0.0 ||
                    cloudTargetHours > 0.0

                val hasPendingLocalProfileEdit = isPendingProfileSync() ||
                    (localModifiedAt > preSyncKnownCloudSyncTime && cloudSyncTime <= localModifiedAt)

                val shouldCheckProfileRestore = cloudHasData && !hasPendingLocalProfileEdit &&
                    (!hasCompletedInitial || cloudSyncTime > preSyncKnownCloudSyncTime || forceFullSync)

                if (shouldCheckProfileRestore) {
                    val restoredCandidate = currentProfile.copy(
                        userName = if (cloudName.isNotBlank()) cloudName else currentProfile.userName,
                        companyName = if (cloudCompany.isNotBlank()) cloudCompany else currentProfile.companyName,
                        designation = if (cloudDesignation.isNotBlank()) cloudDesignation else currentProfile.designation,
                        workerId = if (cloudWorkerId.isNotBlank()) cloudWorkerId else currentProfile.workerId,
                        grade = if (cloudGrade.isNotBlank()) cloudGrade else currentProfile.grade,
                        isCustomSalary = cloudIsCustom,
                        basicSalary = if (cloudBasic > 0.0) cloudBasic else currentProfile.basicSalary,
                        houseRent = if (cloudIsCustom) 0.0 else if (cloudHouseRent > 0.0) cloudHouseRent else currentProfile.houseRent,
                        medicalAllowance = if (cloudIsCustom) 0.0 else if (cloudMedical > 0.0) cloudMedical else 750.0,
                        conveyanceAllowance = if (cloudIsCustom) 0.0 else if (cloudConveyance > 0.0) cloudConveyance else currentProfile.conveyanceAllowance,
                        foodAllowance = if (cloudIsCustom) 0.0 else if (cloudFood > 0.0) cloudFood else currentProfile.foodAllowance,
                        attendanceBonus = if (cloudAttendanceBonus > 0.0) cloudAttendanceBonus else currentProfile.attendanceBonus,
                        maxAbsentForBonus = if (rootData.containsKey("maxAbsentForBonus")) cloudMaxAbsent else currentProfile.maxAbsentForBonus,
                        maxLeaveForBonus = if (rootData.containsKey("maxLeaveForBonus")) cloudMaxLeave else currentProfile.maxLeaveForBonus,
                        nightBillRate = if (cloudNightBill > 0.0) cloudNightBill else currentProfile.nightBillRate,
                        tiffinBillRate = if (cloudTiffinBill > 0.0) cloudTiffinBill else currentProfile.tiffinBillRate,
                        customOtRate = if (cloudCustomOt > 0.0) cloudCustomOt else currentProfile.customOtRate,
                        monthlyOtTargetHours = if (cloudTargetHours > 0.0) cloudTargetHours else currentProfile.monthlyOtTargetHours,
                        language = if (cloudLang.isNotBlank()) LanguageManager.normalizeProfileLanguage(cloudLang) else currentProfile.language
                    )
                    if (restoredCandidate != currentProfile) {
                        currentProfile = restoredCandidate
                        userProfileDao.insertOrUpdateProfile(currentProfile)
                        setLocalProfileModifiedAt(0L)
                        setPendingProfileSync(false)
                        profileRestored = true
                    }
                } else if (!hasCompletedInitial && !isBlankDefaultProfile(currentProfile)) {
                    // Fresh sign-in carrying un-synced local profile while cloud had no profile data
                    setPendingProfileSync(true)
                }
            } else {
                if (!currentProfile.hasAcceptedPrivacyPolicy) {
                    return Pair(true, localizedString(R.string.sync_success_toast, "Auto cloud sync completed!"))
                }
                if (!isBlankDefaultProfile(currentProfile)) {
                    setPendingProfileSync(true)
                }
            }

            // If first-time sign-in/install has un-tracked local Room data, mark it dirty so initial migration uploads cleanly
            if (!hasCompletedInitial) {
                val localOtsInit = overtimeDao.getAllRecordsList()
                if (localOtsInit.isNotEmpty()) {
                    for (ot in localOtsInit) addToTombstoneSet("pending_ot_sync_dates", ot.date)
                }
                val localPieceInit = pieceRateDao.getAllPieceRecordsList()
                if (localPieceInit.isNotEmpty()) {
                    for (pr in localPieceInit) addToTombstoneSet("pending_piece_sync_ids", pr.id.toString())
                }
                val localMsInit = monthlySalaryDao.getAllSettings()
                if (localMsInit.isNotEmpty()) {
                    for (ms in localMsInit) addToTombstoneSet("pending_monthly_sync_yms", ms.yearMonth)
                }
                val localDedInit = deductionDao.getAllDeductions()
                if (localDedInit.isNotEmpty()) {
                    for (ded in localDedInit) addToTombstoneSet("pending_deduction_sync_ids", ded.id.toString())
                }
                val localTrInit = trashDao.getAllTrashItemsOnce()
                if (localTrInit.isNotEmpty()) {
                    for (tr in localTrInit) addToTombstoneSet("pending_trash_sync_ids", tr.id.toString())
                }
            }

            val hasPendingLocal = hasPendingLocalChanges()
            val restorePlan = CloudSyncPolicy.evaluateCollectionRestorePlan(
                forceFullSync = forceFullSync,
                hasCompletedInitialRestore = hasCompletedInitial,
                cloudSyncTime = cloudSyncTime,
                lastKnownCloudSyncTime = preSyncKnownCloudSyncTime,
                otWatermark = getCollectionWatermark(docId, "ot"),
                pieceWatermark = getCollectionWatermark(docId, "piece"),
                monthlyWatermark = getCollectionWatermark(docId, "monthly"),
                deductionWatermark = getCollectionWatermark(docId, "deduction"),
                trashWatermark = getCollectionWatermark(docId, "trash")
            )

            // Fast exit when nothing changed anywhere: 1 root doc read total, 0 writes, timestamp UNTOUCHED
            if (!hasPendingLocal && !restorePlan.shouldDownloadAny && !profileRestored) {
                if (cloudSyncTime > preSyncKnownCloudSyncTime) {
                    setLastKnownCloudSyncTime(docId, cloudSyncTime)
                }
                if (consentLocallyUpdated && currentProfile.cloudSyncStatus.isBlank()) {
                    userProfileDao.insertOrUpdateProfile(
                        currentProfile.copy(cloudSyncStatus = localizedString(R.string.sync_status_active, "Auto Backup Active (Synced)"))
                    )
                }
                return Pair(true, localizedString(R.string.sync_success_toast, "Auto cloud sync completed!"))
            }

            // 2. Flush dirty local deletes & edits in atomic WriteBatches FIRST
            val writeSyncTime = CloudSyncPolicy.computeMonotonicSyncTime(now, cloudSyncTime, preSyncKnownCloudSyncTime)
            val wroteToCloud = if (hasPendingLocal) {
                flushPendingDirtyToCloudLocked(
                    docId = docId,
                    userDocRef = userDocRef,
                    writeSyncTime = writeSyncTime,
                    allowDefaultProfilePush = false
                )
            } else {
                false
            }

            // 3. Download FULL (initial/legacy safety) or DELTA (only records changed since watermark)
            var anyRecordRestored = false
            if (restorePlan.shouldDownloadAny) {
                // 3a. Overtime records
                val activeOtTombstones = getDeletedOtDates()
                val pendingOtDates = getPendingOtSyncDates()
                val otColRef = userDocRef.collection("overtime_records")
                val otQuerySnap = if (restorePlan.otSince == null) {
                    otColRef.get().awaitResult()
                } else {
                    otColRef.whereGreaterThan("updatedAt", restorePlan.otSince).get().awaitResult()
                }
                var maxOtAt = getCollectionWatermark(docId, "ot")
                var otInserted = false
                for (doc in otQuerySnap.documents) {
                    val data = doc.data ?: continue
                    val docUpdated = (data["updatedAt"] as? Number)?.toLong()
                        ?: (data["createdAt"] as? Number)?.toLong() ?: 0L
                    if (docUpdated > maxOtAt) maxOtAt = docUpdated
                    val remoteOt = mapToOvertime(data) ?: continue
                    if (remoteOt.date in activeOtTombstones || remoteOt.date in pendingOtDates) continue
                    val existingLocal = overtimeDao.getRecordByDate(remoteOt.date)
                    if (existingLocal == null) {
                        overtimeDao.insertRecord(remoteOt.copy(id = 0L))
                        anyRecordRestored = true
                        otInserted = true
                    } else {
                        val target = remoteOt.copy(id = existingLocal.id)
                        if (target != existingLocal) {
                            overtimeDao.insertRecord(target)
                            anyRecordRestored = true
                        }
                    }
                }
                if (otInserted) overtimeDao.cleanupDuplicateDates()
                setCollectionWatermark(docId, "ot", maxOf(maxOtAt, cloudSyncTime))

                // 3b. Piece Rate records
                val activePieceTombstones = getDeletedPieceIds()
                val pendingPieceIds = getPendingPieceSyncIds()
                val prColRef = userDocRef.collection("piece_rate_records")
                val prQuerySnap = if (restorePlan.pieceSince == null) {
                    prColRef.get().awaitResult()
                } else {
                    prColRef.whereGreaterThan("updatedAt", restorePlan.pieceSince).get().awaitResult()
                }
                var maxPieceAt = getCollectionWatermark(docId, "piece")
                for (doc in prQuerySnap.documents) {
                    val data = doc.data ?: continue
                    val docUpdated = (data["updatedAt"] as? Number)?.toLong()
                        ?: (data["createdAt"] as? Number)?.toLong() ?: 0L
                    if (docUpdated > maxPieceAt) maxPieceAt = docUpdated
                    val remotePr = mapToPieceRate(data) ?: continue
                    val targetId = if (remotePr.id > 0L) remotePr.id else (doc.id.toLongOrNull() ?: generateUniqueLocalId())
                    val idStr = targetId.toString()
                    if (idStr in activePieceTombstones || doc.id in activePieceTombstones || idStr in pendingPieceIds) continue
                    val candidate = remotePr.copy(id = targetId)
                    val existingLocal = pieceRateDao.getPieceRecordById(targetId)
                    if (existingLocal == null || candidate != existingLocal) {
                        pieceRateDao.insertPieceRecord(candidate)
                        anyRecordRestored = true
                    }
                }
                setCollectionWatermark(docId, "piece", maxOf(maxPieceAt, cloudSyncTime))

                // 3c. Monthly Salary Settings
                val pendingMonthlyYms = getPendingMonthlySyncYms()
                val msColRef = userDocRef.collection("monthly_salary_settings")
                val msQuerySnap = if (restorePlan.monthlySince == null) {
                    msColRef.get().awaitResult()
                } else {
                    msColRef.whereGreaterThan("updatedAt", restorePlan.monthlySince).get().awaitResult()
                }
                var maxMonthlyAt = getCollectionWatermark(docId, "monthly")
                for (doc in msQuerySnap.documents) {
                    val data = doc.data ?: continue
                    val remoteMs = mapToMonthlySalary(data) ?: continue
                    if (remoteMs.updatedAt > maxMonthlyAt) maxMonthlyAt = remoteMs.updatedAt
                    if (remoteMs.yearMonth in pendingMonthlyYms) continue
                    val existingLocal = monthlySalaryDao.getSettingsForMonthOnce(remoteMs.yearMonth)
                    if (existingLocal == null || (remoteMs.updatedAt >= existingLocal.updatedAt && remoteMs != existingLocal)) {
                        monthlySalaryDao.insertOrUpdate(remoteMs)
                        anyRecordRestored = true
                    }
                }
                setCollectionWatermark(docId, "monthly", maxOf(maxMonthlyAt, cloudSyncTime))

                // 3d. Deductions
                val activeDedTombstones = getDeletedDeductionIds()
                val pendingDedIds = getPendingDeductionSyncIds()
                val dedColRef = userDocRef.collection("deduction_records")
                val dedQuerySnap = if (restorePlan.deductionSince == null) {
                    dedColRef.get().awaitResult()
                } else {
                    dedColRef.whereGreaterThan("createdAt", restorePlan.deductionSince).get().awaitResult()
                }
                var maxDedAt = getCollectionWatermark(docId, "deduction")
                for (doc in dedQuerySnap.documents) {
                    val data = doc.data ?: continue
                    val remoteDed = mapToDeduction(data) ?: continue
                    if (remoteDed.createdAt > maxDedAt) maxDedAt = remoteDed.createdAt
                    val targetId = if (remoteDed.id > 0L) remoteDed.id else (doc.id.toLongOrNull() ?: generateUniqueLocalId())
                    val idStr = targetId.toString()
                    if (idStr in activeDedTombstones || doc.id in activeDedTombstones || idStr in pendingDedIds) continue
                    val candidate = remoteDed.copy(id = targetId)
                    val existingLocal = deductionDao.getDeductionById(targetId)
                    if (existingLocal == null || candidate != existingLocal) {
                        deductionDao.insertOrUpdate(candidate)
                        anyRecordRestored = true
                    }
                }
                setCollectionWatermark(docId, "deduction", maxOf(maxDedAt, cloudSyncTime))

                // 3e. Trash records + mirror remote deletes locally
                val activeTrashTombstones = getDeletedTrashIds()
                val pendingTrashIds = getPendingTrashSyncIds()
                val clearTrashBefore = getClearAllTrashBefore()
                val trColRef = userDocRef.collection("trash_records")
                val trashQuerySnap = if (restorePlan.trashSince == null) {
                    trColRef.get().awaitResult()
                } else {
                    trColRef.whereGreaterThan("deletedAt", restorePlan.trashSince).get().awaitResult()
                }
                val cutoff = now - com.example.receiver.TrashAutoDeleteScheduler.TWENTY_FOUR_HOURS_MILLIS
                val localTrashById = trashDao.getAllTrashItemsOnce().associateBy { it.id }
                val trashPurgeBatch = firestore.batch()
                var anyTrashToPurgeInCloud = false
                var maxTrashAt = getCollectionWatermark(docId, "trash")

                for (doc in trashQuerySnap.documents) {
                    val data = doc.data ?: continue
                    val remoteTrash = mapToTrash(data) ?: continue
                    if (remoteTrash.deletedAt > maxTrashAt) maxTrashAt = remoteTrash.deletedAt
                    val targetId = if (remoteTrash.id > 0L) remoteTrash.id else (doc.id.toLongOrNull() ?: generateUniqueLocalId())
                    val idStr = targetId.toString()
                    val isTombstoned = (idStr in activeTrashTombstones) ||
                        (doc.id in activeTrashTombstones) ||
                        (clearTrashBefore > 0L && remoteTrash.deletedAt <= clearTrashBefore)
                    if (remoteTrash.deletedAt <= cutoff || isTombstoned) {
                        trashPurgeBatch.delete(doc.reference)
                        anyTrashToPurgeInCloud = true
                    } else if (idStr !in pendingTrashIds) {
                        val candidate = remoteTrash.copy(id = targetId)
                        val existingTrash = localTrashById[targetId]
                        if (existingTrash == null || candidate != existingTrash) {
                            trashDao.insertTrashItem(candidate)
                            anyRecordRestored = true
                        }
                        // Mirror remote Trash move onto local active tables so another device's deletion applies here too
                        when (remoteTrash.itemType) {
                            "OT" -> if (remoteTrash.date.isNotBlank() && remoteTrash.date !in pendingOtDates) {
                                val existingOt = overtimeDao.getRecordByDate(remoteTrash.date)
                                if (existingOt != null && existingOt.createdAt <= remoteTrash.deletedAt) {
                                    overtimeDao.deleteRecordById(existingOt.id)
                                    anyRecordRestored = true
                                }
                            }
                            "PIECE" -> if (remoteTrash.originalId > 0L && remoteTrash.originalId.toString() !in pendingPieceIds) {
                                val existingPiece = pieceRateDao.getPieceRecordById(remoteTrash.originalId)
                                if (existingPiece != null && existingPiece.createdAt <= remoteTrash.deletedAt) {
                                    pieceRateDao.deletePieceRecordById(existingPiece.id)
                                    anyRecordRestored = true
                                }
                            }
                            "ADVANCE", "APON_BAZAR", "DEDUCTION" -> if (remoteTrash.originalId > 0L && remoteTrash.originalId.toString() !in pendingDedIds) {
                                val existingDed = deductionDao.getDeductionById(remoteTrash.originalId)
                                if (existingDed != null && existingDed.createdAt <= remoteTrash.deletedAt) {
                                    deductionDao.deleteById(existingDed.id)
                                    anyRecordRestored = true
                                }
                            }
                        }
                    }
                }
                if (anyTrashToPurgeInCloud) {
                    try {
                        trashPurgeBatch.commit().awaitResult()
                    } catch (e: Exception) {
                        Log.w("OvertimeRepository", "Purge expired/deleted trash batch failed: ${e.message}")
                    }
                }
                setCollectionWatermark(docId, "trash", maxOf(maxTrashAt, cloudSyncTime))
                setInitialRestoreCompleted(docId, true)
                scheduleTrashAutoDelete()
            }

            // 4. Advance known server watermark
            val latestServerSyncTime = maxOf(
                if (wroteToCloud) writeSyncTime else 0L,
                cloudSyncTime,
                preSyncKnownCloudSyncTime
            )
            if (latestServerSyncTime > preSyncKnownCloudSyncTime) {
                setLastKnownCloudSyncTime(docId, latestServerSyncTime)
            }

            // 5. Conditional Auto Cloud Sync display timestamp:
            // Updates ONLY if data was genuinely written or genuinely restored/merged!
            val didRealCloudTransfer = wroteToCloud || profileRestored || anyRecordRestored
            val nextDisplayedSyncTime = CloudSyncPolicy.resolveDisplayedSyncTimestamp(
                previousSyncTime = currentProfile.lastCloudSyncTime,
                transferOccurred = didRealCloudTransfer,
                transferTimeMillis = now
            )

            val refreshedProfile = getProfileOnce()
            val activeStatus = localizedString(R.string.sync_status_active, "Auto Backup Active (Synced)")
            if (nextDisplayedSyncTime != refreshedProfile.lastCloudSyncTime ||
                refreshedProfile.cloudSyncStatus != activeStatus
            ) {
                userProfileDao.insertOrUpdateProfile(
                    refreshedProfile.copy(
                        lastCloudSyncTime = nextDisplayedSyncTime,
                        cloudSyncStatus = activeStatus
                    )
                )
            }

            Pair(true, localizedString(R.string.sync_success_toast, "Auto cloud sync completed!"))
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            Log.e("OvertimeRepository", "Full sync error", e)
            Pair(false, localizedString(R.string.sync_connection_error_fmt, "Cloud connection error: %1\$s", e.message ?: ""))
        }
    }

    /**
     * Strictly verifies active internet + server reachability, waits for in-flight background writes,
     * flushes pending dirty local changes/deletes (without wastefully re-downloading collections right before local wipe),
     * and confirms server connectivity (Source.SERVER) before allowing sign-out.
     */
    suspend fun prepareAndVerifyCloudBeforeSignOut(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val offlineMsg = localizedString(
            R.string.signout_offline_blocked,
            "Cannot sign out while offline. Please connect to the internet first to keep your data safe."
        )
        val syncFailMsg = localizedString(
            R.string.signout_sync_incomplete,
            "Cloud data sync could not complete. Sign-out cancelled to protect your data."
        )
        val serverUnreachableMsg = localizedString(
            R.string.signout_server_unreachable,
            "Unable to verify Firebase server connection. Sign-out cancelled to protect your data."
        )

        val ctx = resolveContext()
        if (ctx != null && !com.example.util.NetworkMonitor.isStrictlyOnline(ctx)) {
            return@withContext Pair(false, offlineMsg)
        }

        val docId = getDocId()
            ?: return@withContext Pair(
                false,
                localizedString(R.string.signout_no_account, "No signed-in account found")
            )

        // Barrier: Wait for any in-flight tracked background writes to enqueue/finish before acquiring syncMutex
        awaitPendingCloudWrites(10_000L)

        syncMutex.withLock {
            try {
                if (ctx != null && !com.example.util.NetworkMonitor.isStrictlyOnline(ctx)) {
                    return@withLock Pair(false, offlineMsg)
                }

                // Wait again inside mutex for any tail sync tasks spawned right before lock acquisition
                awaitPendingCloudWrites(5_000L)

                // Step A: Flush any queued dirty local changes/deletes directly in atomic WriteBatch
                if (hasPendingLocalChanges()) {
                    val userDocRef = firestore.collection("users").document(docId)
                    val currentProfile = getProfileOnce()
                    val knownCloudTime = maxOf(getLastKnownCloudSyncTime(docId), currentProfile.lastCloudSyncTime)
                    val writeSyncTime = CloudSyncPolicy.computeMonotonicSyncTime(System.currentTimeMillis(), knownCloudTime)
                    val flushedDirty = withTimeoutOrNull(15_000L) {
                        flushPendingDirtyToCloudLocked(
                            docId = docId,
                            userDocRef = userDocRef,
                            writeSyncTime = writeSyncTime,
                            allowDefaultProfilePush = false
                        )
                        true
                    } ?: false
                    if (!flushedDirty || hasPendingLocalChanges()) {
                        return@withLock Pair(false, syncFailMsg)
                    }
                    setLastKnownCloudSyncTime(docId, writeSyncTime)
                }

                // Step B: Wait for Firestore SDK pending writes buffer to drain
                val pendingFlushed = withTimeoutOrNull(12_000L) {
                    firestore.waitForPendingWrites().awaitResult()
                    true
                } ?: false
                if (!pendingFlushed) {
                    return@withLock Pair(false, syncFailMsg)
                }

                // Step C: Strict server-only verification (never fallback to local cache)
                val serverReachable = withTimeoutOrNull(10_000L) {
                    firestore.collection("users").document(docId).get(Source.SERVER).awaitResult()
                    true
                } ?: false
                if (!serverReachable) {
                    return@withLock Pair(false, serverUnreachableMsg)
                }

                if (ctx != null && !com.example.util.NetworkMonitor.isStrictlyOnline(ctx)) {
                    return@withLock Pair(false, offlineMsg)
                }

                Pair(
                    true,
                    localizedString(R.string.signout_backup_verified, "Cloud backup verified")
                )
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    Pair(false, syncFailMsg)
                } else {
                    throw e
                }
            } catch (e: Exception) {
                Log.w("OvertimeRepository", "Pre-sign-out cloud verification failed: ${e.message}", e)
                Pair(false, serverUnreachableMsg)
            }
        }
    }

    private fun profileToMap(profile: UserProfile): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "userName" to profile.userName,
            "companyName" to profile.companyName,
            "designation" to profile.designation,
            "workerId" to profile.workerId,
            "grade" to profile.grade,
            "isCustomSalary" to profile.isCustomSalary,
            "basicSalary" to profile.basicSalary,
            "houseRent" to profile.houseRent,
            "medicalAllowance" to profile.medicalAllowance,
            "conveyanceAllowance" to profile.conveyanceAllowance,
            "foodAllowance" to profile.foodAllowance,
            "attendanceBonus" to profile.attendanceBonus,
            "maxAbsentForBonus" to profile.maxAbsentForBonus,
            "maxLeaveForBonus" to (profile.maxLeaveForBonus ?: -1),
            "nightBillRate" to profile.nightBillRate,
            "tiffinBillRate" to profile.tiffinBillRate,
            "customOtRate" to profile.customOtRate,
            "monthlyOtTargetHours" to profile.monthlyOtTargetHours,
            "language" to profile.language,
            "lastSyncTime" to (if (profile.lastCloudSyncTime > 0) profile.lastCloudSyncTime else System.currentTimeMillis())
        )
        if (profile.hasAcceptedPrivacyPolicy) {
            map["hasAcceptedPrivacyPolicy"] = true
            if (profile.privacyPolicyAcceptedAt > 0L) {
                map["privacyPolicyAcceptedAt"] = profile.privacyPolicyAcceptedAt
                map["consentAudit"] = buildConsentAuditMap(
                    isGiven = true,
                    consentMillis = profile.privacyPolicyAcceptedAt,
                    includeServerTimestamp = false
                )
            }
        }
        return map
    }

    private fun overtimeToMap(record: OvertimeRecord, updateTime: Long = System.currentTimeMillis()): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "id" to record.id,
            "date" to record.date,
            "yearMonth" to record.yearMonth,
            "otHours" to record.otHours,
            "hasNightBill" to record.hasNightBill,
            "hasTiffinBill" to record.hasTiffinBill,
            "isHoliday" to record.isHoliday,
            "shiftType" to record.shiftType,
            "note" to record.note,
            "hourlyRateSnapshot" to record.hourlyRateSnapshot,
            "calculatedAmount" to record.calculatedAmount,
            "createdAt" to record.createdAt,
            "updatedAt" to updateTime
        )
        return map
    }

    private fun mapToOvertime(map: Map<String, Any?>): OvertimeRecord? {
        val date = map["date"] as? String ?: return null
        val yearMonth = map["yearMonth"] as? String ?: if (date.length >= 7) date.substring(0, 7) else ""
        return OvertimeRecord(
            id = (map["id"] as? Number)?.toLong() ?: 0L,
            date = date,
            yearMonth = yearMonth,
            otHours = (map["otHours"] as? Number)?.toDouble() ?: 0.0,
            hasNightBill = map["hasNightBill"] as? Boolean ?: false,
            hasTiffinBill = map["hasTiffinBill"] as? Boolean ?: false,
            isHoliday = map["isHoliday"] as? Boolean ?: false,
            shiftType = map["shiftType"] as? String ?: "REGULAR",
            note = map["note"] as? String ?: "",
            hourlyRateSnapshot = (map["hourlyRateSnapshot"] as? Number)?.toDouble() ?: 0.0,
            calculatedAmount = (map["calculatedAmount"] as? Number)?.toDouble() ?: 0.0,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    private fun pieceRateToMap(record: PieceRateRecord, updateTime: Long = System.currentTimeMillis()): Map<String, Any> {
        val map = hashMapOf<String, Any>(
            "id" to record.id,
            "date" to record.date,
            "yearMonth" to record.yearMonth,
            "itemName" to record.itemName,
            "pieceCount" to record.pieceCount,
            "ratePerPiece" to record.ratePerPiece,
            "totalAmount" to record.totalAmount,
            "note" to record.note,
            "createdAt" to record.createdAt,
            "updatedAt" to updateTime
        )
        return map
    }

    private fun mapToPieceRate(map: Map<String, Any?>): PieceRateRecord? {
        val date = map["date"] as? String ?: return null
        val yearMonth = map["yearMonth"] as? String ?: if (date.length >= 7) date.substring(0, 7) else ""
        val itemName = map["itemName"] as? String ?: ""
        return PieceRateRecord(
            id = (map["id"] as? Number)?.toLong() ?: 0L,
            date = date,
            yearMonth = yearMonth,
            itemName = itemName,
            pieceCount = (map["pieceCount"] as? Number)?.toInt() ?: 0,
            ratePerPiece = (map["ratePerPiece"] as? Number)?.toDouble() ?: 0.0,
            totalAmount = (map["totalAmount"] as? Number)?.toDouble() ?: 0.0,
            note = map["note"] as? String ?: "",
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    private fun monthlySalaryToMap(settings: com.example.data.model.MonthlySalarySettings): Map<String, Any> {
        return hashMapOf(
            "yearMonth" to settings.yearMonth,
            "basicSalary" to settings.basicSalary,
            "houseRent" to settings.houseRent,
            "medicalAllowance" to settings.medicalAllowance,
            "conveyanceAllowance" to settings.conveyanceAllowance,
            "foodAllowance" to settings.foodAllowance,
            "grossSalary" to settings.grossSalary,
            "hourlyOtRate" to settings.hourlyOtRate,
            "attendanceBonus" to settings.attendanceBonus,
            "maxAbsentForBonus" to settings.maxAbsentForBonus,
            "maxLeaveForBonus" to (settings.maxLeaveForBonus ?: -1),
            "nightBillRate" to settings.nightBillRate,
            "tiffinBillRate" to settings.tiffinBillRate,
            "isCustomSalary" to settings.isCustomSalary,
            "userName" to settings.userName,
            "companyName" to settings.companyName,
            "designation" to settings.designation,
            "workerId" to settings.workerId,
            "grade" to settings.grade,
            "customOtRate" to settings.customOtRate,
            "monthlyOtTargetHours" to settings.monthlyOtTargetHours,
            "isLocked" to settings.isLocked,
            "lockedAt" to settings.lockedAt,
            "updatedAt" to settings.updatedAt
        )
    }

    private fun mapToMonthlySalary(map: Map<String, Any?>): com.example.data.model.MonthlySalarySettings? {
        val yearMonth = map["yearMonth"] as? String ?: return null
        return com.example.data.model.MonthlySalarySettings(
            yearMonth = yearMonth,
            basicSalary = getDoubleFromField(map["basicSalary"]),
            houseRent = getDoubleFromField(map["houseRent"]),
            medicalAllowance = getDoubleFromField(map["medicalAllowance"]),
            conveyanceAllowance = getDoubleFromField(map["conveyanceAllowance"]),
            foodAllowance = getDoubleFromField(map["foodAllowance"]),
            grossSalary = getDoubleFromField(map["grossSalary"]),
            hourlyOtRate = getDoubleFromField(map["hourlyOtRate"]),
            attendanceBonus = getDoubleFromField(map["attendanceBonus"]),
            maxAbsentForBonus = ((map["maxAbsentForBonus"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
            maxLeaveForBonus = if (map.containsKey("maxLeaveForBonus")) {
                val num = (map["maxLeaveForBonus"] as? Number)?.toInt()
                if (num == null || num < 0) null else num
            } else null,
            nightBillRate = getDoubleFromField(map["nightBillRate"]),
            tiffinBillRate = getDoubleFromField(map["tiffinBillRate"]),
            isCustomSalary = getBooleanFromField(map["isCustomSalary"]),
            userName = (map["userName"] as? String)?.trim() ?: "",
            companyName = (map["companyName"] as? String)?.trim() ?: "",
            designation = (map["designation"] as? String)?.trim() ?: "",
            workerId = (map["workerId"] as? String)?.trim() ?: "",
            grade = (map["grade"] as? String)?.trim() ?: "",
            customOtRate = getDoubleFromField(map["customOtRate"]),
            monthlyOtTargetHours = getDoubleFromField(map["monthlyOtTargetHours"]),
            isLocked = getBooleanFromField(map["isLocked"]),
            lockedAt = (map["lockedAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    private fun deductionToMap(record: com.example.data.model.DeductionRecord): Map<String, Any> {
        return hashMapOf(
            "id" to record.id,
            "type" to record.type,
            "date" to record.date,
            "yearMonth" to record.yearMonth,
            "amount" to record.amount,
            "description" to record.description,
            "createdAt" to record.createdAt
        )
    }

    private fun mapToDeduction(map: Map<String, Any?>): com.example.data.model.DeductionRecord? {
        val type = map["type"] as? String ?: return null
        val date = map["date"] as? String ?: return null
        val ym = map["yearMonth"] as? String ?: (if (date.length >= 7) date.substring(0, 7) else return null)
        val amount = getDoubleFromField(map["amount"])
        val desc = map["description"] as? String ?: ""
        val id = (map["id"] as? Number)?.toLong() ?: 0L
        val createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        return com.example.data.model.DeductionRecord(
            id = id,
            type = type,
            date = date,
            yearMonth = ym,
            amount = amount,
            description = desc,
            createdAt = createdAt
        )
    }

    private fun trashToMap(item: TrashItem): Map<String, Any> {
        val expireAt = item.deletedAt + com.example.receiver.TrashAutoDeleteScheduler.TWENTY_FOUR_HOURS_MILLIS
        return hashMapOf(
            "id" to item.id,
            "itemType" to item.itemType,
            "originalId" to item.originalId,
            "title" to item.title,
            "subtitle" to item.subtitle,
            "date" to item.date,
            "jsonData" to item.jsonData,
            "deletedAt" to item.deletedAt,
            "expireAt" to expireAt
        )
    }

    private fun mapToTrash(map: Map<String, Any?>): TrashItem? {
        val itemType = map["itemType"] as? String ?: return null
        val title = map["title"] as? String ?: ""
        val subtitle = map["subtitle"] as? String ?: ""
        val date = map["date"] as? String ?: ""
        val jsonData = map["jsonData"] as? String ?: return null
        val id = (map["id"] as? Number)?.toLong() ?: 0L
        val originalId = (map["originalId"] as? Number)?.toLong() ?: 0L
        val deletedAt = (map["deletedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        return TrashItem(
            id = id,
            itemType = itemType,
            originalId = originalId,
            title = title,
            subtitle = subtitle,
            date = date,
            jsonData = jsonData,
            deletedAt = deletedAt
        )
    }

    private fun scheduleTrashAutoDelete() {
        try {
            resolveContext()?.let {
                com.example.receiver.TrashAutoDeleteScheduler.scheduleNextAutoDelete(it)
            }
        } catch (e: Exception) {
            Log.w("OvertimeRepository", "Failed scheduling trash auto-delete: ${e.message}")
        }
    }

    suspend fun moveOvertimeToTrash(record: OvertimeRecord) = withContext(Dispatchers.IO) {
        var savedTrash: TrashItem? = null
        database.withTransaction {
            val rawTrash = TrashItem.fromOvertimeRecord(record)
            val trash = if (rawTrash.id == 0L) rawTrash.copy(id = generateUniqueLocalId()) else rawTrash
            val id = trashDao.insertTrashItem(trash)
            savedTrash = trash.copy(id = id)
            overtimeDao.deleteRecordById(record.id)
        }
        removeFromTombstoneSet("pending_ot_sync_dates", record.date)
        addToTombstoneSet("deleted_ot_dates", record.date)
        savedTrash?.let { trash ->
            val trIdStr = trash.id.toString()
            removeFromTombstoneSet("deleted_trash_ids", trIdStr)
            addToTombstoneSet("pending_trash_sync_ids", trIdStr)
        }
        triggerPendingSync()
        scheduleTrashAutoDelete()
    }

    suspend fun movePieceRateToTrash(record: PieceRateRecord) = withContext(Dispatchers.IO) {
        var savedTrash: TrashItem? = null
        database.withTransaction {
            val rawTrash = TrashItem.fromPieceRateRecord(record)
            val trash = if (rawTrash.id == 0L) rawTrash.copy(id = generateUniqueLocalId()) else rawTrash
            val id = trashDao.insertTrashItem(trash)
            savedTrash = trash.copy(id = id)
            pieceRateDao.deletePieceRecordById(record.id)
        }
        val pieceIdStr = record.id.toString()
        removeFromTombstoneSet("pending_piece_sync_ids", pieceIdStr)
        addToTombstoneSet("deleted_piece_ids", pieceIdStr)
        savedTrash?.let { trash ->
            val trIdStr = trash.id.toString()
            removeFromTombstoneSet("deleted_trash_ids", trIdStr)
            addToTombstoneSet("pending_trash_sync_ids", trIdStr)
        }
        triggerPendingSync()
        scheduleTrashAutoDelete()
    }

    suspend fun moveDeductionToTrash(record: com.example.data.model.DeductionRecord) = withContext(Dispatchers.IO) {
        var savedTrash: TrashItem? = null
        database.withTransaction {
            val rawTrash = TrashItem.fromDeductionRecord(record)
            val trash = if (rawTrash.id == 0L) rawTrash.copy(id = generateUniqueLocalId()) else rawTrash
            val id = trashDao.insertTrashItem(trash)
            savedTrash = trash.copy(id = id)
            deductionDao.deleteById(record.id)
        }
        val dedIdStr = record.id.toString()
        removeFromTombstoneSet("pending_deduction_sync_ids", dedIdStr)
        addToTombstoneSet("deleted_deduction_ids", dedIdStr)
        savedTrash?.let { trash ->
            val trIdStr = trash.id.toString()
            removeFromTombstoneSet("deleted_trash_ids", trIdStr)
            addToTombstoneSet("pending_trash_sync_ids", trIdStr)
        }
        triggerPendingSync()
        scheduleTrashAutoDelete()
    }

    suspend fun restoreTrashItem(item: TrashItem) = withContext(Dispatchers.IO) {
        when (item.itemType) {
            "OT" -> {
                val record = item.toOvertimeRecord()
                if (record != null) {
                    insertOrUpdateOvertime(record.copy(id = 0L), autoSync = false)
                }
            }
            "PIECE" -> {
                val record = item.toPieceRateRecord()
                if (record != null) {
                    insertPieceRate(record.copy(id = 0L), autoSync = false)
                }
            }
            "ADVANCE", "APON_BAZAR", "DEDUCTION" -> {
                val record = item.toDeductionRecord()
                if (record != null) {
                    saveDeduction(record.copy(id = 0L, createdAt = System.currentTimeMillis()), autoSync = false)
                }
            }
        }
        val trIdStr = item.id.toString()
        removeFromTombstoneSet("pending_trash_sync_ids", trIdStr)
        addToTombstoneSet("deleted_trash_ids", trIdStr)
        trashDao.deleteTrashItem(item)
        triggerPendingSync()
        scheduleTrashAutoDelete()
    }

    suspend fun deleteTrashItemPermanently(item: TrashItem) = withContext(Dispatchers.IO) {
        val trIdStr = item.id.toString()
        removeFromTombstoneSet("pending_trash_sync_ids", trIdStr)
        addToTombstoneSet("deleted_trash_ids", trIdStr)
        trashDao.deleteTrashItem(item)
        triggerPendingSync()
        scheduleTrashAutoDelete()
    }

    suspend fun clearAllTrash() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existingIds = trashDao.getAllTrashItemsOnce().map { it.id.toString() }
        for (idStr in existingIds) {
            removeFromTombstoneSet("pending_trash_sync_ids", idStr)
            addToTombstoneSet("deleted_trash_ids", idStr)
        }
        setClearAllTrashBefore(now)
        trashDao.clearAllTrash()
        triggerPendingSync()
        scheduleTrashAutoDelete()
    }

    /**
     * Zero-read expired trash purge: queries only expired local trash IDs and deletes
     * those exact IDs from Firestore in batch (no full collection read on startup).
     */
    suspend fun purgeExpiredTrash(): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - com.example.receiver.TrashAutoDeleteScheduler.TWENTY_FOUR_HOURS_MILLIS
        val expiredIds = trashDao.getExpiredTrashIds(cutoff)
        val purgedCount = if (expiredIds.isNotEmpty()) {
            for (id in expiredIds) {
                val idStr = id.toString()
                removeFromTombstoneSet("pending_trash_sync_ids", idStr)
                addToTombstoneSet("deleted_trash_ids", idStr)
            }
            trashDao.purgeExpiredTrash(cutoff)
        } else {
            0
        }
        if (expiredIds.isNotEmpty()) {
            triggerPendingSync()
        }
        scheduleTrashAutoDelete()
        purgedCount
    }
}
