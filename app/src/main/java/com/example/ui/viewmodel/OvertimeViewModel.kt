package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.model.DeductionRecord
import com.example.data.model.MonthlySalarySettings
import com.example.data.model.OvertimeRecord
import com.example.data.model.PieceRateRecord
import com.example.data.model.TrashItem
import com.example.data.model.UserProfile
import com.example.data.repository.OvertimeRepository
import com.example.receiver.MyFirebaseMessagingService
import com.example.receiver.ReminderScheduler
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class OvertimeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: OvertimeRepository
    private val _pendingConsentUser = MutableStateFlow<FirebaseUser?>(null)
    val pendingConsentUser: StateFlow<FirebaseUser?> = _pendingConsentUser.asStateFlow()

    private val _isInitialSyncComplete = MutableStateFlow(false)
    val isInitialSyncComplete: StateFlow<Boolean> = _isInitialSyncComplete.asStateFlow()

    private val _isSigningOut = MutableStateFlow(false)
    val isSigningOut: StateFlow<Boolean> = _isSigningOut.asStateFlow()

    private val _uiMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    @Volatile
    private var isManualSignInActive = false

    private fun localizedStr(@StringRes resId: Int, vararg args: Any): String {
        val lctx = LanguageManager.getLocalizedContext(getApplication(), userProfile.value.language)
        return if (args.isEmpty()) lctx.getString(resId) else lctx.getString(resId, *args)
    }

    private fun lockedMonthMessage(): String {
        return localizedStr(R.string.msg_past_month_locked)
    }

    private suspend fun isYearMonthLocked(ym: String): Boolean {
        val settings = repository.getMonthlySalarySettingsOnce(ym)
        return com.example.util.MonthLockPolicy.resolveIsMonthLocked(ym, settings?.isLocked)
    }

    init {
        val db = AppDatabase.getDatabase(application)
        repository = OvertimeRepository(db, application)
        viewModelScope.launch {
            repository.cleanupDuplicateDates()
            var isFirstEmission = true
            com.example.data.auth.AuthManager.currentUser.collect { user ->
                try {
                    if (user != null) {
                        if (!isManualSignInActive) {
                            val localProfile = repository.getProfileOnce()
                            val hasLocalConsent = localProfile.hasAcceptedPrivacyPolicy
                            val isOnline = com.example.util.NetworkMonitor.isStrictlyOnline(getApplication())
                            val cloudConsent = if (!hasLocalConsent && isOnline) {
                                repository.checkUserConsentDetailsInCloud(user.uid)
                            } else {
                                null
                            }
                            val alreadyConsented = if (hasLocalConsent) true else (cloudConsent?.hasConsent == true)

                            if (alreadyConsented && !hasLocalConsent) {
                                val cloudConsentTime = cloudConsent?.consentTimestamp ?: 0L
                                if (cloudConsentTime > 0L) {
                                    repository.markConsentGivenLocally(cloudConsentTime)
                                } else {
                                    repository.markConsentGivenLocally()
                                }
                            }

                            val startupDecision = com.example.util.CloudSyncPolicy.evaluateStartupSyncDecision(
                                isSignedIn = true,
                                hasPrivacyConsent = alreadyConsented,
                                isOnline = isOnline,
                                hasCompletedInitialRestoreForUid = repository.hasCompletedInitialRestoreForCurrentUser(),
                                hasPendingLocalDirtyData = repository.hasPendingLocalChanges()
                            )

                            if (startupDecision.shouldRunSync) {
                                repository.fullAutoSyncAndRestore(
                                    forceFullSync = false,
                                    prefetchedRootExists = cloudConsent?.rootExists,
                                    prefetchedRootData = cloudConsent?.rootData
                                )
                                val syncedProfile = repository.getProfileOnce()
                                val normalizedLang = LanguageManager.normalizeProfileLanguage(syncedProfile.language)
                                if (normalizedLang != LanguageManager.getSelectedSettingTag(getApplication())) {
                                    withContext(Dispatchers.Main.immediate) {
                                        LanguageManager.applyAppLanguage(normalizedLang)
                                    }
                                }
                            }
                        }
                    } else {
                        repository.getProfileOnce()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("OvertimeViewModel", "Initial auth/sync observer error: ${e.message}", e)
                } finally {
                    if (isFirstEmission) {
                        isFirstEmission = false
                        delay(50)
                        _isInitialSyncComplete.value = true
                    }
                }
            }
        }
        viewModelScope.launch {
            var wasOnline: Boolean? = null
            com.example.util.NetworkMonitor.observeIsOnline(getApplication()).collect { isOnline ->
                val transitionedToOnline = (wasOnline == false && isOnline)
                wasOnline = isOnline
                if (!transitionedToOnline || isManualSignInActive) return@collect

                val user = com.example.data.auth.AuthManager.currentUser.value ?: return@collect
                val localProfile = repository.getProfileOnce()
                val reconnectDecision = com.example.util.CloudSyncPolicy.evaluateReconnectSyncDecision(
                    isSignedIn = true,
                    hasPrivacyConsent = localProfile.hasAcceptedPrivacyPolicy,
                    isOnline = true,
                    hasCompletedInitialRestoreForUid = repository.hasCompletedInitialRestoreForCurrentUser(),
                    hasPendingLocalDirtyData = repository.hasPendingLocalChanges()
                )
                if (!reconnectDecision.shouldRunSync) return@collect

                try {
                    when (reconnectDecision.reason) {
                        com.example.util.StartupSyncReason.NEEDS_INITIAL_RESTORE ->
                            repository.fullAutoSyncAndRestore(forceFullSync = false)
                        com.example.util.StartupSyncReason.HAS_PENDING_LOCAL_CHANGES ->
                            repository.syncPendingMutationsIfOnline()
                        else -> Unit
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("OvertimeViewModel", "Reconnect sync error (${user.uid}): ${e.message}")
                }
            }
        }
    }

    val trashItems: StateFlow<List<TrashItem>> = repository.allTrashItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    private val _selectedYearMonth = MutableStateFlow(currentMonthStr)
    val selectedYearMonth: StateFlow<String> = _selectedYearMonth.asStateFlow()

    private val _isProfileLoaded = MutableStateFlow(false)
    val isProfileLoaded: StateFlow<Boolean> = _isProfileLoaded.asStateFlow()

    val userProfile: StateFlow<UserProfile> = repository.userProfile
        .onEach { _isProfileLoaded.value = true }
        .map { it ?: UserProfile() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserProfile()
        )

    val allOvertimeRecords: StateFlow<List<OvertimeRecord>> = repository.getAllOvertimeRecords()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val previousMonthSettings: StateFlow<com.example.data.model.MonthlySalarySettings?> = repository
        .getMonthlySalarySettings(com.example.util.MonthLockPolicy.getPreviousCalendarYearMonth())
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val monthlyOtRecords: StateFlow<List<OvertimeRecord>> = _selectedYearMonth
        .flatMapLatest { ym -> repository.getOvertimeRecords(ym) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val monthlyPieceRecords: StateFlow<List<PieceRateRecord>> = _selectedYearMonth
        .flatMapLatest { ym -> repository.getPieceRateRecords(ym) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentMonthSettings: StateFlow<com.example.data.model.MonthlySalarySettings?> = _selectedYearMonth
        .flatMapLatest { ym -> repository.getMonthlySalarySettings(ym) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val monthlyDeductions: StateFlow<List<com.example.data.model.DeductionRecord>> = _selectedYearMonth
        .flatMapLatest { ym -> repository.getDeductionsForMonth(ym) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private data class MonthlyFlowBundle(
        val profile: UserProfile,
        val monthSnapshot: MonthlySalarySettings?,
        val otList: List<OvertimeRecord>,
        val pieceList: List<PieceRateRecord>,
        val deductionList: List<DeductionRecord>
    )

    // Summary calculation state - Reactively combines selectedYearMonth, userProfile, month snapshot settings, OT, piece, and deduction records
    val monthlySummary = combine(
        _selectedYearMonth,
        combine(
            userProfile,
            currentMonthSettings,
            monthlyOtRecords,
            monthlyPieceRecords,
            monthlyDeductions
        ) { profile, monthSnapshot, otList, pieceList, deductionList ->
            MonthlyFlowBundle(profile, monthSnapshot, otList, pieceList, deductionList)
        }
    ) { yearMonth, bundle ->
        val currentCalendarYm = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        computeMonthlySummary(
            selectedYearMonth = yearMonth,
            currentCalendarYm = currentCalendarYm,
            profile = bundle.profile,
            monthSnapshot = bundle.monthSnapshot,
            otList = bundle.otList,
            pieceList = bundle.pieceList,
            deductionList = bundle.deductionList
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MonthlySummaryState()
    )

    fun setYearMonth(yearMonth: String) {
        _selectedYearMonth.value = yearMonth
    }

    fun refreshMonthRolloverIfNeeded() {
        viewModelScope.launch {
            val currentCalendarYm = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            repository.autoFinalizePastMonthSnapshots(currentCalendarYm)
        }
    }

    fun addDeduction(type: String, date: String, amount: Double, description: String = "") {
        viewModelScope.launch {
            val ym = if (date.length >= 7) date.substring(0, 7) else _selectedYearMonth.value
            if (isYearMonthLocked(ym)) {
                _uiMessage.tryEmit(lockedMonthMessage())
                return@launch
            }
            val currentCalendarYm = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            repository.ensureMonthSnapshotExists(ym, repository.getProfileOnce(), currentCalendarYm)
            val record = com.example.data.model.DeductionRecord(
                type = type,
                date = date,
                yearMonth = ym,
                amount = amount,
                description = description.trim()
            )
            repository.saveDeduction(record)
        }
    }

    fun deleteDeduction(id: Long) {
        viewModelScope.launch {
            val existing = repository.getDeductionById(id)
            if (existing != null && isYearMonthLocked(existing.yearMonth)) {
                _uiMessage.tryEmit(lockedMonthMessage())
                return@launch
            }
            repository.deleteDeduction(id)
        }
    }

    fun moveDeductionToTrash(record: com.example.data.model.DeductionRecord) {
        viewModelScope.launch {
            if (isYearMonthLocked(record.yearMonth)) {
                _uiMessage.tryEmit(lockedMonthMessage())
                return@launch
            }
            repository.moveDeductionToTrash(record)
        }
    }

    fun previousMonth() {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(_selectedYearMonth.value) ?: Date()
        cal.add(Calendar.MONTH, -1)
        _selectedYearMonth.value = sdf.format(cal.time)
    }

    fun nextMonth() {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(_selectedYearMonth.value) ?: Date()
        cal.add(Calendar.MONTH, 1)
        _selectedYearMonth.value = sdf.format(cal.time)
    }

    fun setLanguage(tag: String) {
        val normalized = LanguageManager.normalizeProfileLanguage(tag)
        viewModelScope.launch {
            val current = repository.getProfileOnce()
            if (current.language != normalized) {
                repository.saveProfile(current.copy(language = normalized))
            }
            withContext(Dispatchers.Main.immediate) {
                LanguageManager.applyAppLanguage(normalized)
                val ctx = getApplication<Application>()
                ReminderScheduler.createNotificationChannel(ctx)
                MyFirebaseMessagingService.createFcmChannel(ctx)
            }
        }
    }

    fun syncSystemAppLocaleToProfile(settingTag: String) {
        val normalized = LanguageManager.normalizeProfileLanguage(settingTag)
        viewModelScope.launch {
            val current = repository.getProfileOnce()
            if (current.language != normalized) {
                repository.saveProfile(current.copy(language = normalized))
            }
            val ctx = getApplication<Application>()
            ReminderScheduler.createNotificationChannel(ctx)
            MyFirebaseMessagingService.createFcmChannel(ctx)
        }
    }

    fun toggleLanguage() {
        val currentEff = LanguageManager.getEffectiveLanguageTag(getApplication(), userProfile.value.language)
        setLanguage(if (currentEff == "bn") "en" else "bn")
    }

    fun acceptPrivacyPolicy() {
        viewModelScope.launch {
            val current = userProfile.value
            val now = System.currentTimeMillis()
            val updated = current.copy(
                hasAcceptedPrivacyPolicy = true,
                privacyPolicyAcceptedAt = now
            )
            repository.saveProfile(updated)
        }
    }

    fun updateProfile(updated: UserProfile) {
        viewModelScope.launch {
            val currentCalendarYm = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            // Finalize any unlocked past months using the previous profile before overwriting it
            repository.autoFinalizePastMonthSnapshots(currentCalendarYm, autoSync = false)
            repository.saveProfile(updated, autoSync = false)
            // Only update selected month if it is current or future; never overwrite past months
            val ym = if (_selectedYearMonth.value >= currentCalendarYm) _selectedYearMonth.value else currentCalendarYm
            val existingYm = repository.getMonthlySalarySettingsOnce(ym)
            val now = System.currentTimeMillis()
            val otRate = LaborLawCalculator.getHourlyOtRate(updated)
            val snapshot = repository.buildSnapshotFromProfile(
                ym = ym,
                profile = updated,
                isLocked = existingYm?.isLocked ?: (ym < currentCalendarYm),
                lockedAt = existingYm?.lockedAt ?: (if (ym < currentCalendarYm) now else 0L),
                updatedAt = now
            )
            repository.saveMonthlySalarySettings(snapshot, autoSync = false)
            // Synchronize running month records with the new rate and amount in Room and batch-sync once
            repository.updateRunningMonthOvertimeRates(currentCalendarYm, otRate, autoSync = false)
            if (ym > currentCalendarYm) {
                repository.updateRunningMonthOvertimeRates(ym, otRate, autoSync = false)
            }
            repository.triggerPendingSync()
        }
    }

    fun setMonthLockStatus(yearMonth: String, isLocked: Boolean) {
        viewModelScope.launch {
            repository.setMonthLockStatus(yearMonth, isLocked)
            val msg = if (isLocked) {
                localizedStr(R.string.msg_month_locked_toast)
            } else {
                localizedStr(R.string.msg_month_unlocked_toast)
            }
            _uiMessage.tryEmit(msg)
        }
    }

    fun addOrUpdateOvertime(
        id: Long = 0,
        date: String,
        otHours: Double,
        hasNightBill: Boolean,
        hasTiffinBill: Boolean,
        isHoliday: Boolean,
        shiftType: String = "REGULAR",
        note: String
    ) {
        viewModelScope.launch {
            val ym = if (date.length >= 7) date.substring(0, 7) else _selectedYearMonth.value
            if (isYearMonthLocked(ym)) {
                _uiMessage.tryEmit(lockedMonthMessage())
                return@launch
            }
            if (id != 0L) {
                val existingRecord = repository.getOvertimeById(id)
                if (existingRecord != null && isYearMonthLocked(existingRecord.yearMonth)) {
                    _uiMessage.tryEmit(lockedMonthMessage())
                    return@launch
                }
            }
            val currentCalendarYm = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            val isRunningMonth = ym >= currentCalendarYm
            val profile = repository.getProfileOnce()

            // Ensure a snapshot for this month exists (including custom/zero-basic profiles)
            val existingMonthSettings = repository.ensureMonthSnapshotExists(ym, profile, currentCalendarYm)

            // For running month, always use the current active profile rate.
            // For past month, prioritize that past month's saved snapshot rate.
            val effectiveRate = if (isRunningMonth) {
                LaborLawCalculator.getHourlyOtRate(profile)
            } else if (existingMonthSettings != null && (existingMonthSettings.hourlyOtRate > 0.0 || existingMonthSettings.isCustomSalary || existingMonthSettings.updatedAt > 0L)) {
                existingMonthSettings.hourlyOtRate
            } else {
                LaborLawCalculator.getHourlyOtRate(profile)
            }

            val record = OvertimeRecord(
                id = id,
                date = date,
                yearMonth = ym,
                otHours = otHours,
                hasNightBill = hasNightBill,
                hasTiffinBill = hasTiffinBill,
                isHoliday = isHoliday,
                shiftType = shiftType,
                note = note,
                hourlyRateSnapshot = effectiveRate,
                calculatedAmount = otHours * effectiveRate
            )
            repository.insertOrUpdateOvertime(record)
        }
    }

    fun deleteOvertime(id: Long) {
        viewModelScope.launch {
            val existing = repository.getOvertimeById(id)
            if (existing != null && isYearMonthLocked(existing.yearMonth)) {
                _uiMessage.tryEmit(lockedMonthMessage())
                return@launch
            }
            repository.deleteOvertime(id)
        }
    }

    fun addPieceRate(
        itemName: String,
        pieceCount: Int,
        ratePerPiece: Double,
        date: String,
        note: String = ""
    ) {
        viewModelScope.launch {
            val ym = if (date.length >= 7) date.substring(0, 7) else _selectedYearMonth.value
            if (isYearMonthLocked(ym)) {
                _uiMessage.tryEmit(lockedMonthMessage())
                return@launch
            }
            val currentCalendarYm = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            repository.ensureMonthSnapshotExists(ym, repository.getProfileOnce(), currentCalendarYm)
            val total = pieceCount * ratePerPiece
            val record = PieceRateRecord(
                date = date,
                yearMonth = ym,
                itemName = itemName,
                pieceCount = pieceCount,
                ratePerPiece = ratePerPiece,
                totalAmount = total,
                note = note
            )
            repository.insertPieceRate(record)
        }
    }

    fun deletePieceRate(id: Long) {
        viewModelScope.launch {
            val existing = repository.getPieceRateById(id)
            if (existing != null && isYearMonthLocked(existing.yearMonth)) {
                _uiMessage.tryEmit(lockedMonthMessage())
                return@launch
            }
            repository.deletePieceRate(id)
        }
    }

    fun moveOtToTrash(record: OvertimeRecord) {
        viewModelScope.launch {
            if (isYearMonthLocked(record.yearMonth)) {
                _uiMessage.tryEmit(lockedMonthMessage())
                return@launch
            }
            repository.moveOvertimeToTrash(record)
        }
    }

    fun movePieceToTrash(record: PieceRateRecord) {
        viewModelScope.launch {
            if (isYearMonthLocked(record.yearMonth)) {
                _uiMessage.tryEmit(lockedMonthMessage())
                return@launch
            }
            repository.movePieceRateToTrash(record)
        }
    }

    fun restoreTrashItem(item: TrashItem) {
        viewModelScope.launch {
            val ym = when (item.itemType) {
                "OT" -> item.toOvertimeRecord()?.yearMonth
                "PIECE" -> item.toPieceRateRecord()?.yearMonth
                "ADVANCE", "APON_BAZAR", "DEDUCTION" -> item.toDeductionRecord()?.yearMonth
                else -> null
            } ?: if (item.date.length >= 7) item.date.substring(0, 7) else null
            if (ym != null && isYearMonthLocked(ym)) {
                _uiMessage.tryEmit(lockedMonthMessage())
                return@launch
            }
            if (ym != null) {
                val currentCalendarYm = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
                repository.ensureMonthSnapshotExists(ym, repository.getProfileOnce(), currentCalendarYm)
            }
            repository.restoreTrashItem(item)
        }
    }

    fun deleteTrashItemPermanently(item: TrashItem) {
        viewModelScope.launch {
            repository.deleteTrashItemPermanently(item)
        }
    }

    fun clearAllTrash() {
        viewModelScope.launch {
            repository.clearAllTrash()
        }
    }

    fun purgeExpiredTrash() {
        viewModelScope.launch {
            repository.purgeExpiredTrash()
        }
    }

    fun updateTargetHours(target: Double) {
        viewModelScope.launch {
            val currentCalendarYm = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            repository.autoFinalizePastMonthSnapshots(currentCalendarYm, autoSync = false)
            val updated = repository.getProfileOnce().copy(monthlyOtTargetHours = target)
            repository.saveProfile(updated, autoSync = false)
            val ym = if (_selectedYearMonth.value >= currentCalendarYm) _selectedYearMonth.value else currentCalendarYm
            val existingYm = repository.getMonthlySalarySettingsOnce(ym)
            val now = System.currentTimeMillis()
            val snapshot = repository.buildSnapshotFromProfile(
                ym = ym,
                profile = updated,
                isLocked = existingYm?.isLocked ?: (ym < currentCalendarYm),
                lockedAt = existingYm?.lockedAt ?: (if (ym < currentCalendarYm) now else 0L),
                updatedAt = now
            )
            repository.saveMonthlySalarySettings(snapshot, autoSync = false)
            repository.triggerPendingSync()
        }
    }

    val currentUser = com.example.data.auth.AuthManager.currentUser
    val isAuthLoading = com.example.data.auth.AuthManager.isLoading

    fun signInWithGoogle(activity: android.app.Activity, onError: (String) -> Unit) {
        isManualSignInActive = true
        com.example.data.auth.AuthManager.signInWithGoogle(
            activity = activity,
            scope = viewModelScope,
            onSuccess = { user ->
                viewModelScope.launch {
                    try {
                        val cloudConsent = repository.checkUserConsentDetailsInCloud(user.uid)
                        if (cloudConsent.hasConsent) {
                            val currentLocal = repository.getProfileOnce()
                            val effectiveConsentTime = when {
                                currentLocal.privacyPolicyAcceptedAt > 0L && cloudConsent.consentTimestamp > 0L ->
                                    minOf(currentLocal.privacyPolicyAcceptedAt, cloudConsent.consentTimestamp)
                                cloudConsent.consentTimestamp > 0L -> cloudConsent.consentTimestamp
                                currentLocal.privacyPolicyAcceptedAt > 0L -> currentLocal.privacyPolicyAcceptedAt
                                else -> System.currentTimeMillis()
                            }
                            repository.markConsentGivenLocally(effectiveConsentTime)
                            completePostSignIn(user, cloudConsent)
                            isManualSignInActive = false
                        } else {
                            _pendingConsentUser.value = user
                        }
                    } catch (e: Exception) {
                        isManualSignInActive = false
                        onError(e.localizedMessage ?: "Sign-in failed")
                    }
                }
            },
            onError = { err ->
                isManualSignInActive = false
                onError(err)
            },
            onCancel = {
                isManualSignInActive = false
            }
        )
    }

    private suspend fun completePostSignIn(
        user: FirebaseUser,
        cloudConsent: com.example.data.repository.CloudConsentStatus? = null
    ) {
        // Restore profile, overtime, piece-rate, deduction, monthly, and trash data without double-reading root doc
        repository.fullAutoSyncAndRestore(
            forceFullSync = false,
            prefetchedRootExists = cloudConsent?.rootExists,
            prefetchedRootData = cloudConsent?.rootData
        )

        // Only update the user name with Google display name if the restored profile's name is still empty
        val profileAfterSync = repository.getProfileOnce()
        val restoredLang = LanguageManager.normalizeProfileLanguage(profileAfterSync.language)
        if (restoredLang != LanguageManager.getSelectedSettingTag(getApplication())) {
            withContext(Dispatchers.Main.immediate) {
                LanguageManager.applyAppLanguage(restoredLang)
            }
        }
        val newName = user.displayName
        if (!newName.isNullOrBlank() && (profileAfterSync.userName.isBlank() || profileAfterSync.userName == "মোঃ শ্রমিক ভাই")) {
            val updated = profileAfterSync.copy(userName = newName)
            repository.saveProfile(updated)
        }
    }

    fun confirmPrivacyConsent(user: FirebaseUser) {
        viewModelScope.launch {
            try {
                repository.saveConsentToCloud(user.uid)
                _pendingConsentUser.value = null
                completePostSignIn(user)
            } finally {
                isManualSignInActive = false
            }
        }
    }

    fun declinePrivacyConsent() {
        viewModelScope.launch {
            try {
                _pendingConsentUser.value = null
                com.example.data.auth.AuthManager.signOutSuspend(getApplication())
            } finally {
                isManualSignInActive = false
            }
        }
    }

    fun signOut(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        if (_isSigningOut.value) return
        val offlineMsg = localizedStr(R.string.signout_offline_blocked)
        val lostMsg = localizedStr(R.string.signout_connection_lost)
        val doneMsg = localizedStr(R.string.signout_success)
        val lctx = LanguageManager.getLocalizedContext(getApplication(), userProfile.value.language)
        viewModelScope.launch {
            _isSigningOut.value = true
            try {
                val (success, msg) = executeGuardedSignOutLocalized(
                    offlineMsg = offlineMsg,
                    connectionLostMsg = lostMsg,
                    successMsg = doneMsg,
                    failedMsgBuilder = { err -> lctx.getString(R.string.signout_failed_fmt, err) },
                    isOnline = { com.example.util.NetworkMonitor.isStrictlyOnline(getApplication()) },
                    verifyAndSyncCloud = { repository.prepareAndVerifyCloudBeforeSignOut() },
                    performAuthSignOut = { com.example.data.auth.AuthManager.signOutSuspend(getApplication()) },
                    clearLocalData = { repository.clearAllLocalData() }
                )
                onComplete(success, msg)
            } finally {
                _isSigningOut.value = false
            }
        }
    }

    fun deleteAllData(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val isCloud = currentUser.value != null
                if (isCloud && !com.example.util.NetworkMonitor.isStrictlyOnline(getApplication())) {
                    onComplete(false)
                    return@launch
                }
                val success = repository.deleteAllUserData(isCloud)
                if (isCloud && success) {
                    com.example.data.auth.AuthManager.signOutSuspend(getApplication())
                }
                val currentCalendarYm = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
                _selectedYearMonth.value = currentCalendarYm
                onComplete(success)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("OvertimeViewModel", "Failed to delete all data: ${e.message}", e)
                onComplete(false)
            }
        }
    }
}

internal fun computeMonthlySummary(
    selectedYearMonth: String,
    currentCalendarYm: String,
    profile: UserProfile,
    monthSnapshot: MonthlySalarySettings?,
    otList: List<OvertimeRecord>,
    pieceList: List<PieceRateRecord>,
    deductionList: List<DeductionRecord>
): MonthlySummaryState {
    val isRunningMonth = selectedYearMonth >= currentCalendarYm
    val hasSnapshot = !isRunningMonth && monthSnapshot != null

    // For the running month, prioritize active profile so edits are live immediately.
    // For past months, prioritize saved monthSnapshot so historical months stay isolated (including explicit 0.0 values).
    val isCustom = if (hasSnapshot) monthSnapshot!!.isCustomSalary else profile.isCustomSalary
    val baseBasic = if (hasSnapshot) {
        if (monthSnapshot!!.basicSalary > 0.0 || monthSnapshot.updatedAt > 0L) monthSnapshot.basicSalary else profile.basicSalary
    } else {
        profile.basicSalary
    }
    val otRate = if (hasSnapshot) {
        if (monthSnapshot!!.hourlyOtRate > 0.0 || monthSnapshot.isCustomSalary || monthSnapshot.updatedAt > 0L) {
            monthSnapshot.hourlyOtRate
        } else {
            LaborLawCalculator.getHourlyOtRate(profile)
        }
    } else {
        LaborLawCalculator.getHourlyOtRate(profile)
    }

    var totalOtHours = 0.0
    var totalOtAmount = 0.0
    var nightBillCount = 0
    var tiffinBillCount = 0

    for (rec in otList) {
        totalOtHours += rec.otHours
        val itemRate = if (isRunningMonth) otRate else (if (rec.hourlyRateSnapshot > 0.0) rec.hourlyRateSnapshot else otRate)
        totalOtAmount += rec.otHours * itemRate
        if (rec.hasNightBill) nightBillCount++
        if (rec.hasTiffinBill) tiffinBillCount++
    }

    val effectiveNightRate = if (hasSnapshot) monthSnapshot!!.nightBillRate else profile.nightBillRate
    val effectiveTiffinRate = if (hasSnapshot) monthSnapshot!!.tiffinBillRate else profile.tiffinBillRate

    val nightBillTotal = nightBillCount * effectiveNightRate
    val tiffinBillTotal = tiffinBillCount * effectiveTiffinRate

    var totalPieceAmount = 0.0
    for (p in pieceList) {
        totalPieceAmount += p.totalAmount
    }

    var regularDutyDays = 0
    var dayShiftDays = 0
    var nightShiftDays = 0
    var holidayDutyDays = 0
    var weeklyOffDays = 0
    var leaveDays = 0
    var absentDays = 0

    for (rec in otList) {
        when (rec.shiftType) {
            "REGULAR" -> regularDutyDays++
            "DAY" -> dayShiftDays++
            "NIGHT" -> nightShiftDays++
            "HOLIDAY" -> holidayDutyDays++
            "WEEKLY_OFF" -> weeklyOffDays++
            "LEAVE" -> leaveDays++
            "ABSENT" -> absentDays++
            else -> regularDutyDays++
        }
    }

    val effHouseRent: Double
    val effMedical: Double
    val effConveyance: Double
    val effFood: Double

    val allowances = if (baseBasic <= 0.0 || isCustom) {
        effHouseRent = 0.0
        effMedical = 0.0
        effConveyance = 0.0
        effFood = 0.0
        0.0
    } else if (hasSnapshot && monthSnapshot!!.houseRent > 0.0) {
        effHouseRent = monthSnapshot.houseRent
        effMedical = monthSnapshot.medicalAllowance
        effConveyance = monthSnapshot.conveyanceAllowance
        effFood = monthSnapshot.foodAllowance
        effHouseRent + effMedical + effConveyance + effFood
    } else {
        val auto = LaborLawCalculator.calculateAutoAllowances(baseBasic)
        effHouseRent = auto.houseRent
        effMedical = auto.medicalAllowance
        effConveyance = auto.conveyanceAllowance
        effFood = auto.foodAllowance
        effHouseRent + effMedical + effConveyance + effFood
    }

    val grossSalary = if (hasSnapshot && monthSnapshot!!.grossSalary > 0.0) {
        monthSnapshot.grossSalary
    } else if (baseBasic > 0.0) {
        baseBasic + allowances
    } else {
        0.0
    }

    val snapshotHasAnyIdentity = hasSnapshot && (
        monthSnapshot!!.userName.isNotBlank() ||
            monthSnapshot.companyName.isNotBlank() ||
            monthSnapshot.designation.isNotBlank() ||
            monthSnapshot.workerId.isNotBlank() ||
            monthSnapshot.grade.isNotBlank()
        )
    val effUserName = if (snapshotHasAnyIdentity) monthSnapshot!!.userName else profile.userName
    val effCompanyName = if (snapshotHasAnyIdentity) monthSnapshot!!.companyName else profile.companyName
    val effDesignation = if (snapshotHasAnyIdentity) monthSnapshot!!.designation else profile.designation
    val effWorkerId = if (snapshotHasAnyIdentity) monthSnapshot!!.workerId else profile.workerId
    val effGrade = if (snapshotHasAnyIdentity) monthSnapshot!!.grade else profile.grade

    val allowedAbsents = if (hasSnapshot) monthSnapshot!!.maxAbsentForBonus else profile.maxAbsentForBonus
    val allowedLeaves = if (hasSnapshot) monthSnapshot!!.maxLeaveForBonus else profile.maxLeaveForBonus
    val bonusRate = if (hasSnapshot) monthSnapshot!!.attendanceBonus else profile.attendanceBonus
    val isAbsentPenalty = absentDays > allowedAbsents
    val isLeavePenalty = allowedLeaves != null && leaveDays > allowedLeaves
    val isBonusCancelledDueToAbsent = (baseBasic > 0.0 && bonusRate > 0.0) && isAbsentPenalty
    val isBonusCancelledDueToLeave = (baseBasic > 0.0 && bonusRate > 0.0) && isLeavePenalty
    val attendanceBonus = if (baseBasic > 0.0 && !isAbsentPenalty && !isLeavePenalty) bonusRate else 0.0

    // Labor law absent deduction: calculated from month's basic salary (Basic / 30)
    val dailyAbsentRate = if (baseBasic > 0.0) baseBasic / 30.0 else 0.0
    val absentDeduction = if (dailyAbsentRate > 0.0 && absentDays > 0) {
        dailyAbsentRate * absentDays
    } else {
        0.0
    }

    val advanceDeduction = deductionList.filter { it.type == "ADVANCE" }.sumOf { it.amount }
    val aponBazarDeduction = deductionList.filter { it.type == "APON_BAZAR" }.sumOf { it.amount }

    val totalEstimatedEarnings = (grossSalary - absentDeduction).coerceAtLeast(0.0) +
        totalOtAmount + nightBillTotal + tiffinBillTotal + totalPieceAmount + attendanceBonus -
        advanceDeduction - aponBazarDeduction

    val targetHours = if (hasSnapshot) monthSnapshot!!.monthlyOtTargetHours else profile.monthlyOtTargetHours
    val progress = if (targetHours > 0) (totalOtHours / targetHours).coerceIn(0.0, 1.0).toFloat() else 0f
    val targetBreakdown = calculateTargetBreakdown(
        targetHours = targetHours,
        currentOtHours = totalOtHours,
        totalOtAmount = totalOtAmount,
        hourlyOtRate = otRate
    )
    val projectedTotalAtTarget = calculateProjectedTotalAtTarget(
        totalEstimatedEarnings = totalEstimatedEarnings,
        targetHours = targetHours,
        currentOtHours = totalOtHours,
        hourlyOtRate = otRate
    )
    val isLocked = com.example.util.MonthLockPolicy.resolveIsMonthLocked(selectedYearMonth, monthSnapshot?.isLocked)
    val effCustomOtRate = if (hasSnapshot) monthSnapshot!!.customOtRate else profile.customOtRate

    return MonthlySummaryState(
        hourlyOtRate = otRate,
        totalOtHours = totalOtHours,
        totalOtAmount = totalOtAmount,
        nightBillCount = nightBillCount,
        nightBillTotal = nightBillTotal,
        tiffinBillCount = tiffinBillCount,
        tiffinBillTotal = tiffinBillTotal,
        totalPieceAmount = totalPieceAmount,
        basicSalary = baseBasic,
        allowances = allowances,
        grossSalary = grossSalary,
        attendanceBonus = attendanceBonus,
        dailyAbsentRate = dailyAbsentRate,
        absentDeduction = absentDeduction,
        advanceDeduction = advanceDeduction,
        aponBazarDeduction = aponBazarDeduction,
        totalEstimatedEarnings = totalEstimatedEarnings,
        targetHours = targetHours,
        targetProgress = progress,
        remainingTargetHours = targetBreakdown.remainingTargetHours,
        isTargetCompleted = targetBreakdown.isTargetCompleted,
        targetAchievedHours = targetBreakdown.targetAchievedHours,
        targetAchievedAmount = targetBreakdown.targetAchievedAmount,
        extraOtHoursBeyondTarget = targetBreakdown.extraOtHoursBeyondTarget,
        extraOtAmountBeyondTarget = targetBreakdown.extraOtAmountBeyondTarget,
        projectedTotalAtTarget = projectedTotalAtTarget,
        regularDutyDays = regularDutyDays,
        dayShiftDays = dayShiftDays,
        nightShiftDays = nightShiftDays,
        holidayDutyDays = holidayDutyDays,
        weeklyOffDays = weeklyOffDays,
        leaveDays = leaveDays,
        absentDays = absentDays,
        isBonusCancelledDueToLeave = isBonusCancelledDueToLeave,
        isBonusCancelledDueToAbsent = isBonusCancelledDueToAbsent,
        maxAllowedLeaveForBonus = allowedLeaves,
        effectiveNightRate = effectiveNightRate,
        effectiveTiffinRate = effectiveTiffinRate,
        userName = effUserName,
        companyName = effCompanyName,
        designation = effDesignation,
        workerId = effWorkerId,
        grade = effGrade,
        houseRent = effHouseRent,
        medicalAllowance = effMedical,
        conveyanceAllowance = effConveyance,
        foodAllowance = effFood,
        isCustomSalary = isCustom,
        customOtRate = effCustomOtRate,
        isLocked = isLocked
    )
}

data class MonthlySummaryState(
    val hourlyOtRate: Double = 0.0,
    val totalOtHours: Double = 0.0,
    val totalOtAmount: Double = 0.0,
    val nightBillCount: Int = 0,
    val nightBillTotal: Double = 0.0,
    val tiffinBillCount: Int = 0,
    val tiffinBillTotal: Double = 0.0,
    val totalPieceAmount: Double = 0.0,
    val basicSalary: Double = 0.0,
    val allowances: Double = 0.0,
    val grossSalary: Double = 0.0,
    val attendanceBonus: Double = 0.0,
    val dailyAbsentRate: Double = 0.0,
    val absentDeduction: Double = 0.0,
    val advanceDeduction: Double = 0.0,
    val aponBazarDeduction: Double = 0.0,
    val totalEstimatedEarnings: Double = 0.0,
    val targetHours: Double = 0.0,
    val targetProgress: Float = 0f,
    val remainingTargetHours: Double = 0.0,
    val isTargetCompleted: Boolean = false,
    val targetAchievedHours: Double = 0.0,
    val targetAchievedAmount: Double = 0.0,
    val extraOtHoursBeyondTarget: Double = 0.0,
    val extraOtAmountBeyondTarget: Double = 0.0,
    val projectedTotalAtTarget: Double = 0.0,
    val regularDutyDays: Int = 0,
    val dayShiftDays: Int = 0,
    val nightShiftDays: Int = 0,
    val holidayDutyDays: Int = 0,
    val weeklyOffDays: Int = 0,
    val leaveDays: Int = 0,
    val absentDays: Int = 0,
    val isBonusCancelledDueToLeave: Boolean = false,
    val isBonusCancelledDueToAbsent: Boolean = false,
    val maxAllowedLeaveForBonus: Int? = null,
    val effectiveNightRate: Double = 0.0,
    val effectiveTiffinRate: Double = 0.0,
    val userName: String = "",
    val companyName: String = "",
    val designation: String = "",
    val workerId: String = "",
    val grade: String = "",
    val houseRent: Double = 0.0,
    val medicalAllowance: Double = 0.0,
    val conveyanceAllowance: Double = 0.0,
    val foodAllowance: Double = 0.0,
    val isCustomSalary: Boolean = false,
    val customOtRate: Double = 0.0,
    val isLocked: Boolean = false
)

data class TargetBreakdown(
    val isTargetCompleted: Boolean = false,
    val remainingTargetHours: Double = 0.0,
    val targetAchievedHours: Double = 0.0,
    val targetAchievedAmount: Double = 0.0,
    val extraOtHoursBeyondTarget: Double = 0.0,
    val extraOtAmountBeyondTarget: Double = 0.0
)

fun calculateTargetBreakdown(
    targetHours: Double,
    currentOtHours: Double,
    totalOtAmount: Double,
    hourlyOtRate: Double
): TargetBreakdown {
    val safeOtHours = currentOtHours.coerceAtLeast(0.0)
    val safeTotalAmount = totalOtAmount.coerceAtLeast(0.0)
    if (targetHours <= 0.0) {
        return TargetBreakdown(
            isTargetCompleted = false,
            remainingTargetHours = 0.0,
            targetAchievedHours = safeOtHours,
            targetAchievedAmount = safeTotalAmount,
            extraOtHoursBeyondTarget = 0.0,
            extraOtAmountBeyondTarget = 0.0
        )
    }
    val eps = 1e-6
    val isCompleted = (targetHours - safeOtHours) <= eps
    if (!isCompleted) {
        val remaining = (targetHours - safeOtHours).coerceAtLeast(0.0)
        return TargetBreakdown(
            isTargetCompleted = false,
            remainingTargetHours = if (remaining <= eps) 0.0 else remaining,
            targetAchievedHours = safeOtHours,
            targetAchievedAmount = safeTotalAmount,
            extraOtHoursBeyondTarget = 0.0,
            extraOtAmountBeyondTarget = 0.0
        )
    }
    val cappedTargetAmount = if (hourlyOtRate > 0.0) {
        minOf(targetHours * hourlyOtRate, safeTotalAmount)
    } else {
        0.0
    }
    val rawExtraHours = (safeOtHours - targetHours).coerceAtLeast(0.0)
    val extraHours = if (rawExtraHours <= eps) 0.0 else rawExtraHours
    val rawExtraAmount = if (extraHours > 0.0) {
        (safeTotalAmount - cappedTargetAmount).coerceAtLeast(0.0)
    } else {
        0.0
    }
    val extraAmount = if (rawExtraAmount <= eps) 0.0 else rawExtraAmount
    return TargetBreakdown(
        isTargetCompleted = true,
        remainingTargetHours = 0.0,
        targetAchievedHours = targetHours,
        targetAchievedAmount = cappedTargetAmount,
        extraOtHoursBeyondTarget = extraHours,
        extraOtAmountBeyondTarget = extraAmount
    )
}

fun calculateProjectedTotalAtTarget(
    totalEstimatedEarnings: Double,
    targetHours: Double,
    currentOtHours: Double,
    hourlyOtRate: Double
): Double {
    if (targetHours <= 0.0) return totalEstimatedEarnings
    val remainingHours = (targetHours - currentOtHours).coerceAtLeast(0.0)
    val remainingOtEarnings = if (hourlyOtRate > 0.0) remainingHours * hourlyOtRate else 0.0
    return totalEstimatedEarnings + remainingOtEarnings
}

suspend fun executeGuardedSignOut(
    isBn: Boolean,
    isOnline: () -> Boolean,
    verifyAndSyncCloud: suspend () -> Pair<Boolean, String>,
    performAuthSignOut: suspend () -> Unit,
    clearLocalData: suspend () -> Boolean
): Pair<Boolean, String> {
    val offlineMsg = if (isBn) {
        "অফলাইনে লগআউট করা যাবে না। আপনার অফলাইন ডেটা সুরক্ষিত রাখতে প্রথমে ইন্টারনেট সংযোগ চালু করুন।"
    } else {
        "Cannot sign out while offline. Please connect to the internet first to keep your data safe."
    }
    if (!isOnline()) {
        return Pair(false, offlineMsg)
    }
    return try {
        val (syncOk, syncMsg) = verifyAndSyncCloud()
        if (!syncOk) {
            return Pair(false, syncMsg)
        }
        if (!isOnline()) {
            val lostMsg = if (isBn) {
                "ইন্টারনেট সংযোগ বিচ্ছিন্ন হয়েছে। ডেটা সুরক্ষায় লগআউট বাতিল করা হয়েছে।"
            } else {
                "Internet connection lost. Sign-out cancelled to protect your data."
            }
            return Pair(false, lostMsg)
        }
        performAuthSignOut()
        clearLocalData()
        val doneMsg = if (isBn) {
            "ক্লাউডে ডেটা সিঙ্ক করে সফলভাবে লগআউট করা হয়েছে"
        } else {
            "Data synced to cloud and signed out successfully"
        }
        Pair(true, doneMsg)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        val errMsg = if (isBn) {
            "ক্লাউড সিঙ্ক ব্যর্থ হয়েছে (${e.localizedMessage ?: "নেটওয়ার্ক ত্রুটি"})। ডেটা সুরক্ষায় লগআউট বাতিল করা হয়েছে।"
        } else {
            "Cloud sync failed (${e.localizedMessage ?: "Network error"}). Sign-out cancelled to protect your data."
        }
        Pair(false, errMsg)
    }
}

suspend fun executeGuardedSignOutLocalized(
    offlineMsg: String,
    connectionLostMsg: String,
    successMsg: String,
    failedMsgBuilder: (String) -> String,
    isOnline: () -> Boolean,
    verifyAndSyncCloud: suspend () -> Pair<Boolean, String>,
    performAuthSignOut: suspend () -> Unit,
    clearLocalData: suspend () -> Boolean
): Pair<Boolean, String> {
    if (!isOnline()) {
        return Pair(false, offlineMsg)
    }
    return try {
        val (syncOk, syncMsg) = verifyAndSyncCloud()
        if (!syncOk) {
            return Pair(false, syncMsg)
        }
        if (!isOnline()) {
            return Pair(false, connectionLostMsg)
        }
        performAuthSignOut()
        clearLocalData()
        Pair(true, successMsg)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Pair(false, failedMsgBuilder(e.localizedMessage ?: "Network error"))
    }
}
