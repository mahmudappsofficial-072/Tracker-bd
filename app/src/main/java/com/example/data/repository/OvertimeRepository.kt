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
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
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
    val consentTimestamp: Long = 0L
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

    internal fun getDeletedOtDates(): Set<String> = getTombstoneSet("deleted_ot_dates")
    internal fun getDeletedPieceIds(): Set<String> = getTombstoneSet("deleted_piece_ids")
    internal fun getDeletedDeductionIds(): Set<String> = getTombstoneSet("deleted_deduction_ids")
    internal fun getDeletedTrashIds(): Set<String> = getTombstoneSet("deleted_trash_ids")
    internal fun getPendingOtSyncDates(): Set<String> = getTombstoneSet("pending_ot_sync_dates")

    private fun getLocalProfileModifiedAt(): Long {
        val prefs = resolveContext()?.getSharedPreferences("overtime_sync_prefs", android.content.Context.MODE_PRIVATE)
        return prefs?.getLong("local_profile_modified_at", 0L) ?: 0L
    }

    private fun setLocalProfileModifiedAt(timestamp: Long) {
        val prefs = resolveContext()?.getSharedPreferences("overtime_sync_prefs", android.content.Context.MODE_PRIVATE)
        prefs?.edit()?.putLong("local_profile_modified_at", timestamp)?.apply()
    }

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

    suspend fun saveProfile(profile: UserProfile) {
        setLocalProfileModifiedAt(System.currentTimeMillis())
        userProfileDao.insertOrUpdateProfile(profile)
        // Automatic real-time background sync to Firebase
        launchTrackedSync {
            autoSyncProfileToCloud(profile)
        }
    }

    fun getMonthlySalarySettings(yearMonth: String): Flow<com.example.data.model.MonthlySalarySettings?> {
        return monthlySalaryDao.getSettingsForMonth(yearMonth)
    }

    suspend fun getMonthlySalarySettingsOnce(yearMonth: String): com.example.data.model.MonthlySalarySettings? {
        return monthlySalaryDao.getSettingsForMonthOnce(yearMonth)
    }

    suspend fun saveMonthlySalarySettings(settings: com.example.data.model.MonthlySalarySettings) {
        monthlySalaryDao.insertOrUpdate(settings)
        launchTrackedSync {
            autoSyncMonthlySalaryToCloud(settings)
        }
    }

    private fun buildSnapshotFromProfile(
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
            houseRent = if (profile.isCustomSalary) 0.0 else (autoResult?.houseRent ?: profile.houseRent),
            medicalAllowance = if (profile.isCustomSalary) 0.0 else (autoResult?.medicalAllowance ?: 0.0),
            conveyanceAllowance = if (profile.isCustomSalary) 0.0 else (autoResult?.conveyanceAllowance ?: profile.conveyanceAllowance),
            foodAllowance = if (profile.isCustomSalary) 0.0 else (autoResult?.foodAllowance ?: profile.foodAllowance),
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

    suspend fun setMonthLockStatus(yearMonth: String, isLocked: Boolean) {
        val now = System.currentTimeMillis()
        val lockedAt = if (isLocked) now else 0L
        val existing = monthlySalaryDao.getSettingsForMonthOnce(yearMonth)
        if (existing != null) {
            monthlySalaryDao.updateLockStatus(yearMonth, isLocked, lockedAt, now)
            val updated = monthlySalaryDao.getSettingsForMonthOnce(yearMonth)
            if (updated != null) {
                launchTrackedSync {
                    autoSyncMonthlySalaryToCloud(updated)
                }
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
            saveMonthlySalarySettings(snapshot)
        }
    }

    suspend fun autoFinalizePastMonthSnapshots(currentCalendarYm: String) {
        withContext(Dispatchers.IO) {
            try {
                val profile = getProfileOnce()
                val otMonths = overtimeDao.getAllYearMonths()
                val pieceMonths = pieceRateDao.getAllYearMonths()
                val deductionMonths = deductionDao.getAllYearMonths()
                val allMonths = (otMonths + pieceMonths + deductionMonths)
                    .filter { it < currentCalendarYm }
                    .distinct()

                val prevYm = com.example.util.MonthLockPolicy.getPreviousCalendarYearMonth()
                val isPrevInGrace = com.example.util.MonthLockPolicy.isWithinPreviousMonthGracePeriod(prevYm)

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
                        saveMonthlySalarySettings(snapshot)
                    } else if (!existing.isLocked && !isGraceMonth) {
                        setMonthLockStatus(ym, true)
                    }
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

    suspend fun saveDeduction(record: com.example.data.model.DeductionRecord): Long {
        val recordWithId = if (record.id == 0L) record.copy(id = generateUniqueLocalId()) else record
        removeFromTombstoneSet("deleted_deduction_ids", recordWithId.id.toString())
        val id = deductionDao.insertOrUpdate(recordWithId)
        val saved = recordWithId.copy(id = id)
        launchTrackedSync {
            autoSyncDeductionToCloud(saved)
        }
        return id
    }

    suspend fun getDeductionById(id: Long): com.example.data.model.DeductionRecord? {
        return deductionDao.getDeductionById(id)
    }

    suspend fun deleteDeduction(id: Long) {
        addToTombstoneSet("deleted_deduction_ids", id.toString())
        deductionDao.deleteById(id)
        launchTrackedSync {
            autoDeleteDeductionFromCloud(id)
        }
    }

    // Overtime
    fun getOvertimeRecords(yearMonth: String): Flow<List<OvertimeRecord>> {
        return overtimeDao.getRecordsByMonth(yearMonth)
    }

    suspend fun getOvertimeRecordsOnce(yearMonth: String): List<OvertimeRecord> {
        return overtimeDao.getRecordsByMonthOnce(yearMonth)
    }

    suspend fun updateRunningMonthOvertimeRates(yearMonth: String, newRate: Double) {
        try {
            val records = overtimeDao.getRecordsByMonthOnce(yearMonth)
            for (rec in records) {
                val updated = rec.copy(
                    hourlyRateSnapshot = newRate,
                    calculatedAmount = rec.otHours * newRate
                )
                overtimeDao.insertRecord(updated)
                addToTombstoneSet("pending_ot_sync_dates", updated.date)
                launchTrackedSync {
                    autoSyncOvertimeToCloud(updated)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("OvertimeRepository", "Error updating running month overtime rates: ${e.message}")
        }
    }

    fun getAllOvertimeRecords(): Flow<List<OvertimeRecord>> {
        return overtimeDao.getAllRecords()
    }

    suspend fun insertOrUpdateOvertime(record: OvertimeRecord): Long {
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
        val finalRecord = recordToSave.copy(id = savedId)
        addToTombstoneSet("pending_ot_sync_dates", finalRecord.date)

        // Automatic real-time background sync to Firebase
        launchTrackedSync {
            autoSyncOvertimeToCloud(finalRecord)
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
        launchTrackedSync {
            autoDeleteOvertimeFromCloud(existing.date)
        }
    }

    // Piece Rate
    fun getPieceRateRecords(yearMonth: String): Flow<List<PieceRateRecord>> {
        return pieceRateDao.getPieceRecordsByMonth(yearMonth)
    }

    suspend fun getPieceRateById(id: Long): PieceRateRecord? {
        return pieceRateDao.getPieceRecordById(id)
    }

    suspend fun insertPieceRate(record: PieceRateRecord): Long {
        val recordWithId = if (record.id == 0L) record.copy(id = generateUniqueLocalId()) else record
        removeFromTombstoneSet("deleted_piece_ids", recordWithId.id.toString())
        val savedId = pieceRateDao.insertPieceRecord(recordWithId)
        val finalRecord = recordWithId.copy(id = savedId)
        // Automatic real-time background sync to Firebase
        launchTrackedSync {
            autoSyncPieceRateToCloud(finalRecord)
        }
        return savedId
    }

    suspend fun deletePieceRate(id: Long) {
        addToTombstoneSet("deleted_piece_ids", id.toString())
        pieceRateDao.deletePieceRecordById(id)
        // Automatic real-time background deletion from Firebase
        launchTrackedSync {
            autoDeletePieceRateFromCloud(id)
        }
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
                language = LanguageManager.normalizeProfileLanguage(current?.language),
                hasAcceptedPrivacyPolicy = false,
                privacyPolicyAcceptedAt = 0L,
                lastCloudSyncTime = 0L,
                cloudSyncStatus = localizedString(R.string.sync_status_local_unauthenticated, "Not logged in (Local)")
            )
            setLocalProfileModifiedAt(0L)
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

                CloudConsentStatus(hasConsent = consented, consentTimestamp = resolvedTimestamp)
            } else {
                CloudConsentStatus(hasConsent = false, consentTimestamp = 0L)
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
                        "language" to "system",
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

    private suspend fun autoSyncProfileToCloud(profile: UserProfile) {
        val docId = getDocId(profile) ?: return

        // Safety guard: If profile has never been synced (fresh install) and all critical fields are empty/zero,
        // do NOT overwrite existing cloud profile!
        val isDefaultProfile = profile.lastCloudSyncTime == 0L &&
                profile.basicSalary == 0.0 &&
                profile.userName.isBlank() &&
                profile.workerId.isBlank() &&
                profile.companyName.isBlank()
        if (isDefaultProfile) {
            Log.d("OvertimeRepository", "Skipping auto-push for default empty profile")
            return
        }

        try {
            val now = System.currentTimeMillis()
            val syncedProfile = profile.copy(
                lastCloudSyncTime = now,
                cloudSyncStatus = localizedString(R.string.sync_status_active, "Auto Backup Active (Synced)")
            )
            val userMap = profileToMap(syncedProfile)
            firestore.collection("users").document(docId).set(userMap, SetOptions.merge()).awaitResult()
            userProfileDao.insertOrUpdateProfile(syncedProfile)
            setLocalProfileModifiedAt(0L)
        } catch (e: Exception) {
            Log.w("OvertimeRepository", "Auto sync profile failed: ${e.message}")
        }
    }

    private suspend fun autoSyncOvertimeToCloud(record: OvertimeRecord) {
        val currentProfile = getProfileOnce()
        val docId = getDocId(currentProfile) ?: return
        try {
            val map = overtimeToMap(record)
            firestore.collection("users").document(docId)
                .collection("overtime_records").document(record.date)
                .set(map, SetOptions.merge()).awaitResult()
            removeFromTombstoneSet("pending_ot_sync_dates", record.date)
            markProfileSynced(currentProfile)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("OvertimeRepository", "Auto sync OT record failed: ${e.message}")
        }
    }

    private suspend fun autoDeleteOvertimeFromCloud(date: String) {
        val currentProfile = getProfileOnce()
        val docId = getDocId(currentProfile) ?: return
        try {
            firestore.collection("users").document(docId)
                .collection("overtime_records").document(date)
                .delete().awaitResult()
            removeFromTombstoneSet("deleted_ot_dates", date)
            markProfileSynced(currentProfile)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("OvertimeRepository", "Auto delete OT record failed: ${e.message}")
        }
    }

    private suspend fun autoSyncPieceRateToCloud(record: PieceRateRecord) {
        val currentProfile = getProfileOnce()
        val docId = getDocId(currentProfile) ?: return
        try {
            val map = pieceRateToMap(record)
            firestore.collection("users").document(docId)
                .collection("piece_rate_records").document(record.id.toString())
                .set(map, SetOptions.merge()).awaitResult()
            markProfileSynced(currentProfile)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("OvertimeRepository", "Auto sync piece rate failed: ${e.message}")
        }
    }

    private suspend fun autoDeletePieceRateFromCloud(id: Long) {
        val currentProfile = getProfileOnce()
        val docId = getDocId(currentProfile) ?: return
        try {
            firestore.collection("users").document(docId)
                .collection("piece_rate_records").document(id.toString())
                .delete().awaitResult()
            removeFromTombstoneSet("deleted_piece_ids", id.toString())
            markProfileSynced(currentProfile)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("OvertimeRepository", "Auto delete piece rate failed: ${e.message}")
        }
    }

    private suspend fun markProfileSynced(currentProfile: UserProfile) {
        val now = System.currentTimeMillis()
        val latest = userProfileDao.getUserProfileOnce() ?: currentProfile
        userProfileDao.insertOrUpdateProfile(latest.copy(
            lastCloudSyncTime = now,
            cloudSyncStatus = localizedString(R.string.sync_status_active, "Auto Backup Active (Synced)")
        ))
        val docId = getDocId(latest)
        if (docId != null) {
            try {
                firestore.collection("users").document(docId)
                    .set(mapOf("lastSyncTime" to now), SetOptions.merge()).awaitResult()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("OvertimeRepository", "Updating cloud lastSyncTime failed: ${e.message}")
            }
        }
    }

    private suspend fun autoSyncMonthlySalaryToCloud(settings: com.example.data.model.MonthlySalarySettings) {
        val docId = getDocId() ?: return
        try {
            val map = monthlySalaryToMap(settings)
            firestore.collection("users").document(docId)
                .collection("monthly_salary_settings").document(settings.yearMonth)
                .set(map, SetOptions.merge()).awaitResult()
            Log.d("OvertimeRepository", "Auto sync monthly salary settings success: ${settings.yearMonth}")
            markProfileSynced(getProfileOnce())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("OvertimeRepository", "Auto sync monthly salary settings failed: ${e.message}")
        }
    }

    private suspend fun autoSyncDeductionToCloud(record: com.example.data.model.DeductionRecord) {
        val currentProfile = getProfileOnce()
        val docId = getDocId(currentProfile) ?: return
        try {
            val map = deductionToMap(record)
            firestore.collection("users").document(docId)
                .collection("deduction_records").document(record.id.toString())
                .set(map, SetOptions.merge()).awaitResult()
            markProfileSynced(currentProfile)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("OvertimeRepository", "Auto sync deduction failed: ${e.message}")
        }
    }

    private suspend fun autoDeleteDeductionFromCloud(id: Long) {
        val currentProfile = getProfileOnce()
        val docId = getDocId(currentProfile) ?: return
        try {
            firestore.collection("users").document(docId)
                .collection("deduction_records").document(id.toString())
                .delete().awaitResult()
            removeFromTombstoneSet("deleted_deduction_ids", id.toString())
            markProfileSynced(currentProfile)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("OvertimeRepository", "Auto delete deduction failed: ${e.message}")
        }
    }

    private suspend fun autoSyncTrashToCloud(item: TrashItem) {
        val currentProfile = getProfileOnce()
        val docId = getDocId(currentProfile) ?: return
        try {
            val map = trashToMap(item)
            firestore.collection("users").document(docId)
                .collection("trash_records").document(item.id.toString())
                .set(map, SetOptions.merge()).awaitResult()
            markProfileSynced(currentProfile)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("OvertimeRepository", "Auto sync trash failed: ${e.message}")
        }
    }

    private suspend fun autoDeleteTrashFromCloud(id: Long) {
        val currentProfile = getProfileOnce()
        val docId = getDocId(currentProfile) ?: return
        try {
            firestore.collection("users").document(docId)
                .collection("trash_records").document(id.toString())
                .delete().awaitResult()
            removeFromTombstoneSet("deleted_trash_ids", id.toString())
            markProfileSynced(currentProfile)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("OvertimeRepository", "Auto delete trash failed: ${e.message}")
        }
    }

    private suspend fun autoClearAllTrashFromCloud() {
        val currentProfile = getProfileOnce()
        val docId = getDocId(currentProfile) ?: return
        try {
            deleteCollectionInBatches(
                firestore.collection("users").document(docId).collection("trash_records")
            )
            setClearAllTrashBefore(0L)
            markProfileSynced(currentProfile)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("OvertimeRepository", "Auto clear all trash failed: ${e.message}")
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

    // Full 2-Way Sync & Restore Engine:
    // Optimized for minimum Firestore Reads & Writes with smart delta sync
    suspend fun fullAutoSyncAndRestore(forceFullSync: Boolean = false): Pair<Boolean, String> = syncMutex.withLock {
        fullAutoSyncAndRestoreLocked(forceFullSync)
    }

    private suspend fun fullAutoSyncAndRestoreLocked(forceFullSync: Boolean = false): Pair<Boolean, String> {
        var currentProfile = getProfileOnce()
        val docId = getDocId(currentProfile) ?: return Pair(false, localizedString(R.string.sync_not_logged_in, "Not logged in"))
        val now = System.currentTimeMillis()
        val lastSync = currentProfile.lastCloudSyncTime

        return try {
            val userDocRef = firestore.collection("users").document(docId)

            // 1. Sync Profile directly from server to avoid stale cached profiles
            val cloudProfileSnap = try {
                userDocRef.get(Source.SERVER).awaitResult()
            } catch (_: Exception) {
                userDocRef.get().awaitResult()
            }

            var cloudSyncTime = 0L
            val localIsDefaultOrBlank = lastSync == 0L ||
                    (currentProfile.basicSalary == 0.0 && currentProfile.companyName.isBlank() && currentProfile.workerId.isBlank())
            val localModifiedAt = getLocalProfileModifiedAt()
            var profileUploaded = false

            if (cloudProfileSnap.exists()) {
                val data = cloudProfileSnap.data
                if (data != null) {
                    val cloudBasic = getDoubleFromField(data["basicSalary"])
                    val cloudName = (data["userName"] as? String)?.trim() ?: ""
                    val cloudCompany = (data["companyName"] as? String)?.trim() ?: ""
                    val cloudDesignation = (data["designation"] as? String)?.trim() ?: ""
                    val cloudWorkerId = (data["workerId"] as? String)?.trim() ?: ""
                    val cloudGrade = (data["grade"] as? String)?.trim() ?: ""
                    val cloudHouseRent = getDoubleFromField(data["houseRent"])
                    val cloudMedical = getDoubleFromField(data["medicalAllowance"])
                    val cloudConveyance = getDoubleFromField(data["conveyanceAllowance"])
                    val cloudFood = getDoubleFromField(data["foodAllowance"])
                    val cloudAttendanceBonus = getDoubleFromField(data["attendanceBonus"])
                    val cloudNightBill = getDoubleFromField(data["nightBillRate"])
                    val cloudTiffinBill = getDoubleFromField(data["tiffinBillRate"])
                    val cloudCustomOt = getDoubleFromField(data["customOtRate"])
                    val cloudTargetHours = getDoubleFromField(data["monthlyOtTargetHours"])
                    val cloudIsCustom = getBooleanFromField(data["isCustomSalary"])
                    val cloudMaxAbsent = ((data["maxAbsentForBonus"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
                    val cloudMaxLeave = if (data.containsKey("maxLeaveForBonus")) {
                        val num = (data["maxLeaveForBonus"] as? Number)?.toInt()
                        if (num == null || num < 0) null else num
                    } else null
                    val cloudLang = (data["language"] as? String)?.trim() ?: ""
                    cloudSyncTime = (data["lastSyncTime"] as? Number)?.toLong() ?: 0L
                    val cloudConsentAudit = data["consentAudit"] as? Map<*, *>
                    val cloudHasConsent = getBooleanFromField(data["hasAcceptedPrivacyPolicy"]) ||
                            (cloudConsentAudit?.get("isConsentGiven") as? Boolean == true)
                    val cloudConsentTime = extractConsentTimestampMillis(data)
                    val mergedConsent = currentProfile.hasAcceptedPrivacyPolicy || cloudHasConsent
                    val mergedConsentTime = when {
                        currentProfile.privacyPolicyAcceptedAt > 0L && cloudConsentTime > 0L ->
                            minOf(currentProfile.privacyPolicyAcceptedAt, cloudConsentTime)
                        cloudConsentTime > 0L -> cloudConsentTime
                        else -> currentProfile.privacyPolicyAcceptedAt
                    }

                    // Check if cloud document contains meaningful user profile data
                    val cloudHasData = cloudBasic > 0.0 ||
                            (cloudName.isNotBlank() && cloudName != "মোঃ শ্রমিক ভাই") ||
                            cloudCompany.isNotBlank() ||
                            cloudWorkerId.isNotBlank() ||
                            cloudDesignation.isNotBlank() ||
                            cloudNightBill > 0.0 ||
                            cloudTiffinBill > 0.0 ||
                            cloudAttendanceBonus > 0.0 ||
                            cloudTargetHours > 0.0

                    val hasPendingLocalProfileEdit = localModifiedAt > lastSync && cloudSyncTime <= localModifiedAt

                    // Restore from cloud when local is fresh/empty and cloud has data, or when cloud data has strictly newer timestamp (unless local has newer offline edit)
                    val shouldRestore = cloudHasData && !hasPendingLocalProfileEdit && (localIsDefaultOrBlank || cloudSyncTime > lastSync || currentProfile.basicSalary == 0.0)

                    if (shouldRestore) {
                        val restoredProfile = currentProfile.copy(
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
                            maxAbsentForBonus = if (data.containsKey("maxAbsentForBonus")) cloudMaxAbsent else currentProfile.maxAbsentForBonus,
                            maxLeaveForBonus = if (data.containsKey("maxLeaveForBonus")) cloudMaxLeave else currentProfile.maxLeaveForBonus,
                            nightBillRate = if (cloudNightBill > 0.0) cloudNightBill else currentProfile.nightBillRate,
                            tiffinBillRate = if (cloudTiffinBill > 0.0) cloudTiffinBill else currentProfile.tiffinBillRate,
                            customOtRate = if (cloudCustomOt > 0.0) cloudCustomOt else currentProfile.customOtRate,
                            monthlyOtTargetHours = if (cloudTargetHours > 0.0) cloudTargetHours else currentProfile.monthlyOtTargetHours,
                            language = if (cloudLang.isNotBlank()) LanguageManager.normalizeProfileLanguage(cloudLang) else currentProfile.language,
                            hasAcceptedPrivacyPolicy = mergedConsent,
                            privacyPolicyAcceptedAt = mergedConsentTime,
                            lastCloudSyncTime = if (cloudSyncTime > 0L) cloudSyncTime else now,
                            cloudSyncStatus = localizedString(R.string.sync_status_active, "Auto Backup Active (Synced)")
                        )
                        userProfileDao.insertOrUpdateProfile(restoredProfile)
                        currentProfile = restoredProfile
                        setLocalProfileModifiedAt(0L)
                    } else if (!localIsDefaultOrBlank || hasPendingLocalProfileEdit) {
                        val toUpload = currentProfile.copy(
                            hasAcceptedPrivacyPolicy = mergedConsent,
                            privacyPolicyAcceptedAt = mergedConsentTime,
                            lastCloudSyncTime = now,
                            cloudSyncStatus = localizedString(R.string.sync_status_active, "Auto Backup Active (Synced)")
                        )
                        userDocRef.set(profileToMap(toUpload), SetOptions.merge()).awaitResult()
                        currentProfile = toUpload
                        userProfileDao.insertOrUpdateProfile(currentProfile)
                        setLocalProfileModifiedAt(0L)
                        profileUploaded = true
                    } else if (mergedConsent != currentProfile.hasAcceptedPrivacyPolicy || mergedConsentTime != currentProfile.privacyPolicyAcceptedAt) {
                        val consentSyncedProfile = currentProfile.copy(
                            hasAcceptedPrivacyPolicy = mergedConsent,
                            privacyPolicyAcceptedAt = mergedConsentTime
                        )
                        userProfileDao.insertOrUpdateProfile(consentSyncedProfile)
                        currentProfile = consentSyncedProfile
                    }
                }
            } else {
                val localHasData = currentProfile.basicSalary > 0.0 ||
                        currentProfile.userName.isNotBlank() ||
                        currentProfile.companyName.isNotBlank() ||
                        currentProfile.workerId.isNotBlank()
                if (localHasData) {
                    val toUpload = currentProfile.copy(
                        lastCloudSyncTime = now,
                        cloudSyncStatus = localizedString(R.string.sync_status_active, "Auto Backup Active (Synced)")
                    )
                    userDocRef.set(profileToMap(toUpload), SetOptions.merge()).awaitResult()
                    currentProfile = toUpload
                    userProfileDao.insertOrUpdateProfile(currentProfile)
                    setLocalProfileModifiedAt(0L)
                    profileUploaded = true
                }
            }

            // SMART DECISION FLAGS:
            // 1. Full collection download: Only needed if fresh install, manual full sync requested, or cloud has newer changes!
            val shouldDownloadCloudCollections = forceFullSync || localIsDefaultOrBlank || (cloudSyncTime > lastSync)

            // 2. Upload scope: If fresh/force full sync upload all; otherwise only upload records modified after lastSync!
            val isInitialUpload = forceFullSync || localIsDefaultOrBlank || (lastSync == 0L)
            var anyLocalDataUploaded = false

            // Flush pending local deletions FIRST so deleted items are never resurrected from cloud
            val pendingOtDeletes = getDeletedOtDates()
            if (pendingOtDeletes.isNotEmpty()) {
                for (chunk in pendingOtDeletes.toList().chunked(400)) {
                    val batch = firestore.batch()
                    for (date in chunk) {
                        batch.delete(userDocRef.collection("overtime_records").document(date))
                    }
                    batch.commit().awaitResult()
                    removeAllFromTombstoneSet("deleted_ot_dates", chunk)
                }
            }

            val pendingPieceDeletes = getDeletedPieceIds()
            if (pendingPieceDeletes.isNotEmpty()) {
                for (chunk in pendingPieceDeletes.toList().chunked(400)) {
                    val batch = firestore.batch()
                    for (pieceId in chunk) {
                        batch.delete(userDocRef.collection("piece_rate_records").document(pieceId))
                    }
                    batch.commit().awaitResult()
                    removeAllFromTombstoneSet("deleted_piece_ids", chunk)
                }
            }

            val pendingDeductionDeletes = getDeletedDeductionIds()
            if (pendingDeductionDeletes.isNotEmpty()) {
                for (chunk in pendingDeductionDeletes.toList().chunked(400)) {
                    val batch = firestore.batch()
                    for (dedId in chunk) {
                        batch.delete(userDocRef.collection("deduction_records").document(dedId))
                    }
                    batch.commit().awaitResult()
                    removeAllFromTombstoneSet("deleted_deduction_ids", chunk)
                }
            }

            val pendingTrashDeletes = getDeletedTrashIds()
            if (pendingTrashDeletes.isNotEmpty()) {
                for (chunk in pendingTrashDeletes.toList().chunked(400)) {
                    val batch = firestore.batch()
                    for (trId in chunk) {
                        batch.delete(userDocRef.collection("trash_records").document(trId))
                    }
                    batch.commit().awaitResult()
                    removeAllFromTombstoneSet("deleted_trash_ids", chunk)
                }
            }

            // 2 & 3. Overtime records
            val pendingOtSyncDates = getPendingOtSyncDates()
            val localOts = overtimeDao.getAllRecordsList()
            val otsToUpload = if (isInitialUpload) {
                localOts
            } else {
                localOts.filter { it.createdAt > lastSync || it.date in pendingOtSyncDates }
            }
            if (otsToUpload.isNotEmpty()) {
                anyLocalDataUploaded = true
                for (chunk in otsToUpload.chunked(400)) {
                    val batch = firestore.batch()
                    for (ot in chunk) {
                        batch.set(userDocRef.collection("overtime_records").document(ot.date), overtimeToMap(ot), SetOptions.merge())
                    }
                    batch.commit().awaitResult()
                    removeAllFromTombstoneSet("pending_ot_sync_dates", chunk.map { it.date })
                }
            }

            if (shouldDownloadCloudCollections) {
                val activeOtTombstones = getDeletedOtDates()
                val otQuerySnap = userDocRef.collection("overtime_records").get().awaitResult()
                for (doc in otQuerySnap.documents) {
                    val data = doc.data ?: continue
                    val remoteOt = mapToOvertime(data) ?: continue
                    if (remoteOt.date in activeOtTombstones) continue
                    val existingLocal = overtimeDao.getRecordByDate(remoteOt.date)
                    if (existingLocal == null) {
                        overtimeDao.insertRecord(remoteOt.copy(id = 0L))
                    } else {
                        overtimeDao.insertRecord(remoteOt.copy(id = existingLocal.id))
                    }
                }
                overtimeDao.cleanupDuplicateDates()
            }

            // 4 & 5. Piece Rate records
            val localPieces = pieceRateDao.getAllPieceRecordsList()
            val piecesToUpload = if (isInitialUpload) localPieces else localPieces.filter { it.createdAt > lastSync }
            if (piecesToUpload.isNotEmpty()) {
                anyLocalDataUploaded = true
                for (chunk in piecesToUpload.chunked(400)) {
                    val batch = firestore.batch()
                    for (pr in chunk) {
                        batch.set(userDocRef.collection("piece_rate_records").document(pr.id.toString()), pieceRateToMap(pr), SetOptions.merge())
                    }
                    batch.commit().awaitResult()
                }
            }

            if (shouldDownloadCloudCollections) {
                val activePieceTombstones = getDeletedPieceIds()
                val prQuerySnap = userDocRef.collection("piece_rate_records").get().awaitResult()
                for (doc in prQuerySnap.documents) {
                    val data = doc.data ?: continue
                    val remotePr = mapToPieceRate(data) ?: continue
                    val targetId = if (remotePr.id > 0L) remotePr.id else (doc.id.toLongOrNull() ?: generateUniqueLocalId())
                    if (targetId.toString() in activePieceTombstones || doc.id in activePieceTombstones) continue
                    pieceRateDao.insertPieceRecord(remotePr.copy(id = targetId))
                }
            }

            // 6 & 7. Monthly Salary Settings
            val localMonthlySettings = monthlySalaryDao.getAllSettings()
            val monthlyToUpload = if (isInitialUpload) localMonthlySettings else localMonthlySettings.filter { it.updatedAt > lastSync }
            if (monthlyToUpload.isNotEmpty()) {
                anyLocalDataUploaded = true
                for (chunk in monthlyToUpload.chunked(400)) {
                    val batch = firestore.batch()
                    for (ms in chunk) {
                        batch.set(userDocRef.collection("monthly_salary_settings").document(ms.yearMonth), monthlySalaryToMap(ms), SetOptions.merge())
                    }
                    batch.commit().awaitResult()
                }
            }

            if (shouldDownloadCloudCollections) {
                val msQuerySnap = userDocRef.collection("monthly_salary_settings").get().awaitResult()
                for (doc in msQuerySnap.documents) {
                    val data = doc.data ?: continue
                    val remoteMs = mapToMonthlySalary(data) ?: continue
                    monthlySalaryDao.insertOrUpdate(remoteMs)
                }
            }

            // 8 & 9. Deductions
            val localDeductions = deductionDao.getAllDeductions()
            val deductionsToUpload = if (isInitialUpload) localDeductions else localDeductions.filter { it.createdAt > lastSync }
            if (deductionsToUpload.isNotEmpty()) {
                anyLocalDataUploaded = true
                for (chunk in deductionsToUpload.chunked(400)) {
                    val batch = firestore.batch()
                    for (ded in chunk) {
                        batch.set(userDocRef.collection("deduction_records").document(ded.id.toString()), deductionToMap(ded), SetOptions.merge())
                    }
                    batch.commit().awaitResult()
                }
            }

            if (shouldDownloadCloudCollections) {
                val activeDedTombstones = getDeletedDeductionIds()
                val dedQuerySnap = userDocRef.collection("deduction_records").get().awaitResult()
                for (doc in dedQuerySnap.documents) {
                    val data = doc.data ?: continue
                    val remoteDed = mapToDeduction(data) ?: continue
                    val targetId = if (remoteDed.id > 0L) remoteDed.id else (doc.id.toLongOrNull() ?: generateUniqueLocalId())
                    if (targetId.toString() in activeDedTombstones || doc.id in activeDedTombstones) continue
                    deductionDao.insertOrUpdate(remoteDed.copy(id = targetId))
                }
            }

            // 10 & 11. Trash records
            val localTrash = trashDao.getAllTrashItemsOnce()
            val trashToUpload = if (isInitialUpload) localTrash else localTrash.filter { it.deletedAt > lastSync }
            if (trashToUpload.isNotEmpty()) {
                anyLocalDataUploaded = true
                for (chunk in trashToUpload.chunked(400)) {
                    val batch = firestore.batch()
                    for (tr in chunk) {
                        batch.set(userDocRef.collection("trash_records").document(tr.id.toString()), trashToMap(tr), SetOptions.merge())
                    }
                    batch.commit().awaitResult()
                }
            }

            val clearTrashBefore = getClearAllTrashBefore()
            if (shouldDownloadCloudCollections || localTrash.isNotEmpty() || clearTrashBefore > 0L) {
                val activeTrashTombstones = getDeletedTrashIds()
                val trashQuerySnap = userDocRef.collection("trash_records").get().awaitResult()
                val cutoff = now - com.example.receiver.TrashAutoDeleteScheduler.TWENTY_FOUR_HOURS_MILLIS
                val trashPurgeBatch = firestore.batch()
                var anyTrashToPurgeInCloud = false

                for (doc in trashQuerySnap.documents) {
                    val data = doc.data ?: continue
                    val remoteTrash = mapToTrash(data) ?: continue
                    val targetId = if (remoteTrash.id > 0L) remoteTrash.id else (doc.id.toLongOrNull() ?: generateUniqueLocalId())
                    val isTombstoned = (targetId.toString() in activeTrashTombstones) ||
                        (doc.id in activeTrashTombstones) ||
                        (clearTrashBefore > 0L && remoteTrash.deletedAt <= clearTrashBefore)
                    if (remoteTrash.deletedAt <= cutoff || isTombstoned) {
                        trashPurgeBatch.delete(doc.reference)
                        anyTrashToPurgeInCloud = true
                    } else {
                        trashDao.insertTrashItem(remoteTrash.copy(id = targetId))
                    }
                }
                if (anyTrashToPurgeInCloud) {
                    try {
                        trashPurgeBatch.commit().awaitResult()
                        if (clearTrashBefore > 0L) {
                            setClearAllTrashBefore(0L)
                        }
                    } catch (e: Exception) {
                        Log.w("OvertimeRepository", "Purge expired/deleted trash batch failed: ${e.message}")
                    }
                } else if (clearTrashBefore > 0L) {
                    setClearAllTrashBefore(0L)
                }
            }
            scheduleTrashAutoDelete()

            // Update timestamp on server if any local data was uploaded
            if (anyLocalDataUploaded) {
                try {
                    userDocRef.set(mapOf("lastSyncTime" to now), SetOptions.merge()).awaitResult()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w("OvertimeRepository", "Updating lastSyncTime failed: ${e.message}")
                }
            }

            // 8. Ensure profile in Room DB has latest sync timestamp and status using currentProfile
            // Preserve accurate timestamp: update lastCloudSyncTime only if actual upload or download/restore occurred, or if never synced before!
            val didSyncOccur = anyLocalDataUploaded || profileUploaded || shouldDownloadCloudCollections || (currentProfile.lastCloudSyncTime == 0L)
            val effectiveSyncTime = if (didSyncOccur) now else currentProfile.lastCloudSyncTime
            val finalProfile = currentProfile.copy(
                lastCloudSyncTime = if (effectiveSyncTime > 0L) effectiveSyncTime else now,
                cloudSyncStatus = localizedString(R.string.sync_status_active, "Auto Backup Active (Synced)")
            )
            userProfileDao.insertOrUpdateProfile(finalProfile)
            Pair(true, localizedString(R.string.sync_success_toast, "Auto cloud sync completed!"))
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            Log.e("OvertimeRepository", "Full sync error", e)
            Pair(false, localizedString(R.string.sync_connection_error_fmt, "Cloud connection error: %1\$s", e.message ?: ""))
        }
    }

    // Called by ViewModel if needed
    suspend fun syncWithCloud(): Pair<Boolean, String> {
        return fullAutoSyncAndRestore(forceFullSync = true)
    }

    /**
     * Strictly verifies active internet + server reachability, waits for in-flight background writes,
     * flushes any offline Firestore pending writes, runs a full 2-way sync, and confirms server connectivity
     * (Source.SERVER, no cache fallback) before allowing sign-out and local database wipe.
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

                // Step A: Flush any queued offline Firestore writes/deletes to the server BEFORE downloading/syncing
                val pendingFlushed = withTimeoutOrNull(12_000L) {
                    firestore.waitForPendingWrites().awaitResult()
                    true
                } ?: false
                if (!pendingFlushed) {
                    return@withLock Pair(false, syncFailMsg)
                }

                // Step B: Full bidirectional sync so all Room records (OT, piece rate, deductions, settings, profile, trash) reach cloud
                val syncResult = withTimeoutOrNull(15_000L) {
                    fullAutoSyncAndRestoreLocked(forceFullSync = true)
                } ?: return@withLock Pair(false, syncFailMsg)
                if (!syncResult.first) {
                    return@withLock Pair(false, syncResult.second.ifBlank { syncFailMsg })
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

    private fun overtimeToMap(record: OvertimeRecord): Map<String, Any> {
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
            "updatedAt" to System.currentTimeMillis()
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

    private fun pieceRateToMap(record: PieceRateRecord): Map<String, Any> {
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
            "updatedAt" to System.currentTimeMillis()
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
            removeFromTombstoneSet("deleted_trash_ids", trash.id.toString())
            launchTrackedSync { autoSyncTrashToCloud(trash) }
        }
        launchTrackedSync { autoDeleteOvertimeFromCloud(record.date) }
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
        addToTombstoneSet("deleted_piece_ids", record.id.toString())
        savedTrash?.let { trash ->
            removeFromTombstoneSet("deleted_trash_ids", trash.id.toString())
            launchTrackedSync { autoSyncTrashToCloud(trash) }
        }
        launchTrackedSync { autoDeletePieceRateFromCloud(record.id) }
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
        addToTombstoneSet("deleted_deduction_ids", record.id.toString())
        savedTrash?.let { trash ->
            removeFromTombstoneSet("deleted_trash_ids", trash.id.toString())
            launchTrackedSync { autoSyncTrashToCloud(trash) }
        }
        launchTrackedSync { autoDeleteDeductionFromCloud(record.id) }
        scheduleTrashAutoDelete()
    }

    suspend fun restoreTrashItem(item: TrashItem) = withContext(Dispatchers.IO) {
        when (item.itemType) {
            "OT" -> {
                val record = item.toOvertimeRecord()
                if (record != null) {
                    insertOrUpdateOvertime(record.copy(id = 0L))
                }
            }
            "PIECE" -> {
                val record = item.toPieceRateRecord()
                if (record != null) {
                    insertPieceRate(record.copy(id = 0L))
                }
            }
            "ADVANCE", "APON_BAZAR", "DEDUCTION" -> {
                val record = item.toDeductionRecord()
                if (record != null) {
                    saveDeduction(record.copy(id = 0L))
                }
            }
        }
        addToTombstoneSet("deleted_trash_ids", item.id.toString())
        trashDao.deleteTrashItem(item)
        launchTrackedSync { autoDeleteTrashFromCloud(item.id) }
        scheduleTrashAutoDelete()
    }

    suspend fun deleteTrashItemPermanently(item: TrashItem) = withContext(Dispatchers.IO) {
        addToTombstoneSet("deleted_trash_ids", item.id.toString())
        trashDao.deleteTrashItem(item)
        launchTrackedSync { autoDeleteTrashFromCloud(item.id) }
        scheduleTrashAutoDelete()
    }

    suspend fun clearAllTrash() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        setClearAllTrashBefore(now)
        trashDao.clearAllTrash()
        launchTrackedSync { autoClearAllTrashFromCloud() }
        scheduleTrashAutoDelete()
    }

    suspend fun purgeExpiredTrash(): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - com.example.receiver.TrashAutoDeleteScheduler.TWENTY_FOUR_HOURS_MILLIS
        val purgedCount = trashDao.purgeExpiredTrash(cutoff)
        val docId = getDocId()
        if (docId != null) {
            try {
                val trSnap = firestore.collection("users").document(docId)
                    .collection("trash_records").get().awaitResult()
                if (!trSnap.isEmpty) {
                    val batch = firestore.batch()
                    var anyExpired = false
                    for (doc in trSnap.documents) {
                        val deletedAt = (doc.data?.get("deletedAt") as? Number)?.toLong() ?: 0L
                        if (deletedAt > 0L && deletedAt <= cutoff) {
                            batch.delete(doc.reference)
                            anyExpired = true
                        }
                    }
                    if (anyExpired) {
                        batch.commit().awaitResult()
                    }
                }
            } catch (e: Exception) {
                Log.w("OvertimeRepository", "Purge expired trash from cloud failed: ${e.message}")
            }
        }
        scheduleTrashAutoDelete()
        purgedCount
    }
}
