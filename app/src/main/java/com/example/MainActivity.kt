package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.components.AdmobBanner
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.OvertimeRecord
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.OvertimeViewModel
import com.example.util.LanguageManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val openQuickOtTrigger = androidx.compose.runtime.mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra("OPEN_QUICK_OT", false) == true) {
            openQuickOtTrigger.value = true
        }
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                OvertimeApp(
                    openQuickOtTrigger = openQuickOtTrigger.value,
                    onResetQuickOtTrigger = { openQuickOtTrigger.value = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("OPEN_QUICK_OT", false)) {
            openQuickOtTrigger.value = true
        }
    }
}

@Composable
fun OvertimeApp(
    viewModel: OvertimeViewModel = viewModel(),
    openQuickOtTrigger: Boolean = false,
    onResetQuickOtTrigger: () -> Unit = {}
) {
    var isAppReady by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val isInitialSyncComplete by viewModel.isInitialSyncComplete.collectAsStateWithLifecycle()

    LaunchedEffect(isInitialSyncComplete) {
        if (isInitialSyncComplete) {
            isAppReady = true
        }
    }

    LaunchedEffect(Unit) {
        if (!isAppReady) {
            kotlinx.coroutines.delay(2000L)
            isAppReady = true
        }
    }

    var currentTab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) } // 0: Home, 1: OT List, 2: Piece & Slip, 3: Profile

    val selectedMonth by viewModel.selectedYearMonth.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val otRecords by viewModel.monthlyOtRecords.collectAsStateWithLifecycle()
    val allOtRecords by viewModel.allOvertimeRecords.collectAsStateWithLifecycle()
    val previousMonthSettings by viewModel.previousMonthSettings.collectAsStateWithLifecycle()
    val pieceRecords by viewModel.monthlyPieceRecords.collectAsStateWithLifecycle()
    val summary by viewModel.monthlySummary.collectAsStateWithLifecycle()
    val deductions by viewModel.monthlyDeductions.collectAsStateWithLifecycle()
    val trashItems by viewModel.trashItems.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()
    val isSigningOut by viewModel.isSigningOut.collectAsStateWithLifecycle()
    val pendingConsentUser by viewModel.pendingConsentUser.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity

    val lang = profile.language

    // Notification permission launcher for Android 13+ (API 33+)
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            com.example.receiver.ReminderScheduler.scheduleDailyReminders(context, isBn = lang == "bn")
        }
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        viewModel.uiMessage.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(lang) {
        val isBn = lang == "bn"
        com.example.receiver.ReminderScheduler.scheduleDailyReminders(context, isBn = isBn)
        try {
            com.example.receiver.MyFirebaseMessagingService.createFcmChannel(context, isBn = isBn)
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to create FCM notification channel: ${e.message}")
        }
    }

    // Dialog States
    var appUpdateInfo by remember { mutableStateOf<com.example.util.AppUpdateInfo?>(null) }
    var showQuickOtDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var hasCheckedMissingOtThisSession by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showMissingOtDialog by remember { mutableStateOf(false) }
    var missingOtDates by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingProfileHighlightField by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var profileHighlightTriggerId by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }

    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshMonthRolloverIfNeeded()
                if (appUpdateInfo == null) {
                    coroutineScope.launch {
                        val update = com.example.util.AppUpdateChecker.checkUpdate(context)
                        if (update != null) {
                            appUpdateInfo = update
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(openQuickOtTrigger) {
        if (openQuickOtTrigger) {
            currentTab = 0
            showQuickOtDialog = true
            hasCheckedMissingOtThisSession = true
            onResetQuickOtTrigger()
        }
    }

    LaunchedEffect(otRecords, allOtRecords, previousMonthSettings, openQuickOtTrigger, isAppReady) {
        if (isAppReady && !hasCheckedMissingOtThisSession && !openQuickOtTrigger) {
            kotlinx.coroutines.delay(350)
            val prevYm = com.example.util.MonthLockPolicy.getPreviousCalendarYearMonth()
            val isPrevUnlocked = !com.example.util.MonthLockPolicy.resolveIsMonthLocked(prevYm, previousMonthSettings?.isLocked)
            val missing = MissingOtDetector.getMissingDatesForReminder(
                currentMonthRecords = otRecords,
                allRecords = allOtRecords,
                isPreviousMonthUnlocked = isPrevUnlocked
            )
            if (missing.isNotEmpty()) {
                missingOtDates = missing
                showMissingOtDialog = true
            }
            hasCheckedMissingOtThisSession = true
        }
    }
    var editingRecordId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Long?>(null) }
    var editingRecordDate by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val recordToEdit = remember(editingRecordId, editingRecordDate, otRecords, allOtRecords) {
        val id = editingRecordId
        val date = editingRecordDate
        when {
            id != null -> allOtRecords.find { it.id == id } ?: otRecords.find { it.id == id }
            date != null -> OvertimeRecord(
                date = date,
                yearMonth = if (date.length >= 7) date.substring(0, 7) else "",
                otHours = 0.0
            )
            else -> null
        }
    }
    var showPieceDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showTargetDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var isHomeSheetOpen by remember { mutableStateOf(false) }
    var isProfileSheetOpen by remember { mutableStateOf(false) }

    // Back handling: Return to Home if on secondary tab when no in-screen sheet/panel is open
    BackHandler(enabled = isAppReady && currentTab != 0 && !showQuickOtDialog && !showMissingOtDialog && !showPieceDialog && !showTargetDialog) {
        currentTab = 0
    }

    val currentUpdate = appUpdateInfo
    val isAnyOverlayOccludingBanner = showQuickOtDialog ||
        showMissingOtDialog ||
        showPieceDialog ||
        showTargetDialog ||
        isHomeSheetOpen ||
        isProfileSheetOpen ||
        pendingConsentUser != null ||
        currentUpdate != null

    if (currentUpdate != null && currentUpdate.isForceUpdate) {
        // Startup Update Gate: Barrier prevents home screen or user data from rendering
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag("force_update_startup_gate"),
            contentAlignment = Alignment.Center
        ) {
            ForceUpdateDialog(
                updateInfo = currentUpdate,
                lang = lang
            )
        }
    } else {
        Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AdmobBanner(
                    modifier = Modifier.fillMaxWidth(),
                    isOccluded = isAnyOverlayOccludingBanner
                )
                val isCompactNav = com.example.util.ResponsiveUtils.isCompactScreen()
                val navLabelSize = if (isCompactNav) 10.sp else 12.sp

                NavigationBar(
                    modifier = Modifier.testTag("bottom_navigation_bar"),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = NavigationBarDefaults.Elevation
                ) {
                // Tab 0: Home
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = {
                        showQuickOtDialog = false
                        editingRecordId = null
                        editingRecordDate = null
                        showMissingOtDialog = false
                        showPieceDialog = false
                        showTargetDialog = false
                        currentTab = 0
                    },
                    icon = {
                        Icon(
                            if (currentTab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = LanguageManager.get("tab_home", lang)
                        )
                    },
                    label = {
                        Text(
                            text = LanguageManager.get("tab_home", lang),
                            fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = navLabelSize,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.testTag("tab_home_item")
                )

                // Tab 1: OT List
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = {
                        showQuickOtDialog = false
                        editingRecordId = null
                        editingRecordDate = null
                        showMissingOtDialog = false
                        showPieceDialog = false
                        showTargetDialog = false
                        currentTab = 1
                    },
                    icon = {
                        Icon(
                            if (currentTab == 1) Icons.Filled.AccessTimeFilled else Icons.Outlined.AccessTime,
                            contentDescription = LanguageManager.get("tab_ot_list", lang)
                        )
                    },
                    label = {
                        Text(
                            text = LanguageManager.get("tab_ot_list", lang),
                            fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = navLabelSize,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.testTag("tab_ot_list_item")
                )

                // Tab 2: Piece Rate & Slip
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = {
                        showQuickOtDialog = false
                        editingRecordId = null
                        editingRecordDate = null
                        showMissingOtDialog = false
                        showPieceDialog = false
                        showTargetDialog = false
                        currentTab = 2
                    },
                    icon = {
                        Icon(
                            if (currentTab == 2) Icons.AutoMirrored.Filled.ReceiptLong else Icons.AutoMirrored.Outlined.ReceiptLong,
                            contentDescription = LanguageManager.get("tab_piece_rate", lang)
                        )
                    },
                    label = {
                        Text(
                            text = LanguageManager.get("tab_piece_rate", lang),
                            fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Normal,
                            fontSize = navLabelSize,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.testTag("tab_piece_item")
                )

                // Tab 3: Profile & Settings
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = {
                        showQuickOtDialog = false
                        editingRecordId = null
                        editingRecordDate = null
                        showMissingOtDialog = false
                        showPieceDialog = false
                        showTargetDialog = false
                        currentTab = 3
                    },
                    icon = {
                        Icon(
                            if (currentTab == 3) Icons.Filled.Person else Icons.Outlined.Person,
                            contentDescription = LanguageManager.get("tab_profile", lang)
                        )
                    },
                    label = {
                        Text(
                            text = LanguageManager.get("tab_profile", lang),
                            fontWeight = if (currentTab == 3) FontWeight.Bold else FontWeight.Normal,
                            fontSize = navLabelSize,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.testTag("tab_profile_item")
                )
            }
        }
    }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            if (!isAppReady) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .testTag("app_initial_loading_screen"),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.5.dp,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("app_initial_loading_indicator")
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentTab) {
                0 -> HomeScreen(
                    selectedMonth = selectedMonth,
                    summary = summary,
                    profile = profile,
                    recentRecords = otRecords,
                    deductions = deductions,
                    onPreviousMonth = { viewModel.previousMonth() },
                    onNextMonth = { viewModel.nextMonth() },
                    onToggleLanguage = { viewModel.toggleLanguage() },
                    onQuickAddOtClick = {
                        editingRecordId = null
                        val currentCalYm = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
                        editingRecordDate = if (selectedMonth < currentCalYm) {
                            com.example.util.MonthLockPolicy.getDefaultDateForMonth(selectedMonth)
                        } else null
                        showQuickOtDialog = true
                    },
                    onAddDeduction = { type, date, amount, desc ->
                        viewModel.addDeduction(type, date, amount, desc)
                    },
                    onDeleteDeduction = { record ->
                        viewModel.moveDeductionToTrash(record)
                    },
                    onEditTargetClick = { showTargetDialog = true },
                    onRecordClick = { rec ->
                        editingRecordId = rec.id
                        editingRecordDate = null
                        showQuickOtDialog = true
                    },
                    onViewAllClick = { currentTab = 1 },
                    onSetMonthLock = { locked -> viewModel.setMonthLockStatus(selectedMonth, locked) },
                    onModalSheetVisibilityChanged = { isHomeSheetOpen = it }
                )

                1 -> OtListScreen(
                    selectedMonth = selectedMonth,
                    records = otRecords,
                    hourlyRate = summary.hourlyOtRate,
                    dailyAbsentRate = summary.dailyAbsentRate,
                    attendanceBonus = summary.attendanceBonus,
                    isBonusCancelled = summary.isBonusCancelledDueToAbsent || summary.isBonusCancelledDueToLeave,
                    isLocked = summary.isLocked,
                    lang = lang,
                    onPreviousMonth = { viewModel.previousMonth() },
                    onNextMonth = { viewModel.nextMonth() },
                    onAddClick = {
                        editingRecordId = null
                        val currentCalYm = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
                        editingRecordDate = if (selectedMonth < currentCalYm) {
                            com.example.util.MonthLockPolicy.getDefaultDateForMonth(selectedMonth)
                        } else null
                        showQuickOtDialog = true
                    },
                    onEditClick = { rec ->
                        editingRecordId = rec.id
                        editingRecordDate = null
                        showQuickOtDialog = true
                    },
                    onDeleteClick = { id ->
                        otRecords.find { it.id == id }?.let { rec ->
                            viewModel.moveOtToTrash(rec)
                        } ?: viewModel.deleteOvertime(id)
                    },
                    onSetMonthLock = { locked -> viewModel.setMonthLockStatus(selectedMonth, locked) }
                )

                2 -> PieceRateScreen(
                    selectedMonth = selectedMonth,
                    pieceRecords = pieceRecords,
                    profile = profile,
                    summary = summary,
                    lang = lang,
                    onAddPieceClick = { showPieceDialog = true },
                    onDeletePieceClick = { id ->
                        pieceRecords.find { it.id == id }?.let { rec ->
                            viewModel.movePieceToTrash(rec)
                        } ?: viewModel.deletePieceRate(id)
                    },
                    onSetMonthLock = { locked -> viewModel.setMonthLockStatus(selectedMonth, locked) }
                )

                3 -> ProfileScreen(
                    profile = profile,
                    lang = lang,
                    currentUser = currentUser,
                    isAuthLoading = isAuthLoading,
                    isSigningOut = isSigningOut,
                    onSignInWithGoogle = {
                        activity?.let { act ->
                            viewModel.signInWithGoogle(act) { err ->
                                android.widget.Toast.makeText(act, err, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onSignOut = { onResult ->
                        viewModel.signOut { success, msg ->
                            onResult(success, msg)
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    onSaveProfile = { updated -> viewModel.updateProfile(updated) },
                    onToggleLanguage = { viewModel.toggleLanguage() },
                    onSyncCloud = { callback -> viewModel.syncCloudNow(callback) },
                    trashItems = trashItems,
                    onRestoreTrash = { viewModel.restoreTrashItem(it) },
                    onDeleteTrashPermanently = { viewModel.deleteTrashItemPermanently(it) },
                    onClearAllTrash = { viewModel.clearAllTrash() },
                    onDeleteAllData = { onResult ->
                        viewModel.deleteAllData { success ->
                            onResult(success)
                            val msg = if (success) {
                                if (lang == "bn") "সকল ডেটা সফলভাবে মুছে ফেলা হয়েছে" else "All data deleted successfully"
                            } else {
                                if (lang == "bn") "ডেটা মুছতে সমস্যা হয়েছে (ইন্টারনেট সংযোগ চেক করুন)" else "Failed to delete data. Please check internet connection."
                            }
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    highlightField = pendingProfileHighlightField,
                    highlightTriggerId = profileHighlightTriggerId,
                    onConsumeHighlight = { pendingProfileHighlightField = null },
                    onModalSheetVisibilityChanged = { isProfileSheetOpen = it }
                )
                    }

                    // Missing Overtime Entry Reminder In-Screen Panel
                    if (showMissingOtDialog && !showQuickOtDialog) {
                        MissingOtReminderDialog(
                            missingDates = missingOtDates,
                            lang = lang,
                            onDismiss = { showMissingOtDialog = false },
                            onAddOtForDate = { targetDate ->
                                showMissingOtDialog = false
                                editingRecordId = null
                                editingRecordDate = targetDate
                                showQuickOtDialog = true
                            }
                        )
                    }

                    // Quick/Edit OT In-Screen Sheet
                    if (showQuickOtDialog) {
                        QuickOtEntryDialog(
                            initialRecord = recordToEdit,
                            existingRecords = allOtRecords.ifEmpty { otRecords },
                            hourlyRate = summary.hourlyOtRate,
                            profile = profile,
                            lang = lang,
                            onDismiss = {
                                showQuickOtDialog = false
                                editingRecordId = null
                                editingRecordDate = null
                            },
                            onNavigateToProfile = { targetField ->
                                showQuickOtDialog = false
                                editingRecordId = null
                                editingRecordDate = null
                                pendingProfileHighlightField = targetField
                                profileHighlightTriggerId += 1
                                currentTab = 3
                            },
                            onSave = { date, hours, nightBill, tiffinBill, isHoliday, shiftType, note ->
                                viewModel.addOrUpdateOvertime(
                                    id = recordToEdit?.id ?: 0,
                                    date = date,
                                    otHours = hours,
                                    hasNightBill = nightBill,
                                    hasTiffinBill = tiffinBill,
                                    isHoliday = isHoliday,
                                    shiftType = shiftType,
                                    note = note
                                )
                                showQuickOtDialog = false
                                editingRecordId = null
                                editingRecordDate = null
                            }
                        )
                    }
                    // Add Piece Work In-Screen Sheet
                    if (showPieceDialog) {
                        AddPieceRateDialog(
                            lang = lang,
                            defaultDate = com.example.util.MonthLockPolicy.getDefaultDateForMonth(selectedMonth),
                            onDismiss = { showPieceDialog = false },
                            onSave = { item, count, rate, date, note ->
                                viewModel.addPieceRate(
                                    itemName = item,
                                    pieceCount = count,
                                    ratePerPiece = rate,
                                    date = date,
                                    note = note
                                )
                                showPieceDialog = false
                            }
                        )
                    }

                    // Monthly Target In-Screen Sheet
                    if (showTargetDialog) {
                        TargetGoalDialog(
                            currentTarget = profile.monthlyOtTargetHours,
                            lang = lang,
                            hourlyRate = summary.hourlyOtRate,
                            currentTotalEarnings = summary.totalEstimatedEarnings,
                            currentOtHours = summary.totalOtHours,
                            grossSalary = summary.grossSalary,
                            totalDeductions = summary.advanceDeduction + summary.aponBazarDeduction + summary.absentDeduction,
                            isBonusCancelled = summary.isBonusCancelledDueToAbsent || summary.isBonusCancelledDueToLeave,
                            onDismiss = { showTargetDialog = false },
                            onSave = { newTarget ->
                                viewModel.updateTargetHours(newTarget)
                                showTargetDialog = false
                            }
                        )
                    }
                }
            }
        }
    }

    // Google Sign-In Privacy Consent Dialog (shown AFTER account selection if account hasn't consented yet)
    val pendingUser = pendingConsentUser
    if (pendingUser != null) {
        InitialPrivacyConsentDialog(
            lang = lang,
            titleText = if (lang == "bn") "গোপনীয়তা নীতিমালায় সম্মতি" else "Privacy Policy Consent",
            confirmButtonText = if (lang == "bn") "সম্মত আছি ও সাইন ইন" else "Agree & Sign In",
            dismissButtonText = if (lang == "bn") "বাতিল / অফলাইনে ব্যবহার" else "Cancel / Use Offline",
            onAgree = {
                viewModel.confirmPrivacyConsent(pendingUser)
            },
            onDecline = {
                viewModel.declinePrivacyConsent()
            },
            onViewFullPolicy = {
                val privacyUrl = "https://mahmudappsofficial-072.github.io/overtime-tracker-privacy-policy/"
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(privacyUrl))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.w("MainActivity", "Unable to open privacy policy link: ${e.message}")
                }
            }
        )
    }

    // Optional / Flexible Update Dialog (shown politely over loaded app content)
    if (currentUpdate != null && !currentUpdate.isForceUpdate) {
        ForceUpdateDialog(
            updateInfo = currentUpdate,
            lang = lang,
            onDismissOptional = {
                com.example.util.AppUpdateChecker.snoozeOptionalUpdate(context, currentUpdate.latestVersionCode)
                appUpdateInfo = null
            }
        )
    }
    }
}
