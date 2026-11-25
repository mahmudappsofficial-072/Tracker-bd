package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccessTimeFilled
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.OvertimeRecord
import com.example.receiver.MyFirebaseMessagingService
import com.example.receiver.ReminderScheduler
import com.example.ui.components.AdmobBanner
import com.example.ui.components.AutoFitSingleLineText
import com.example.ui.screens.AddPieceRateDialog
import com.example.ui.screens.ForceUpdateDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InitialPrivacyConsentDialog
import com.example.ui.screens.LanguagePickerBottomSheet
import com.example.ui.screens.MissingOtDetector
import com.example.ui.screens.MissingOtReminderDialog
import com.example.ui.screens.OtListScreen
import com.example.ui.screens.PieceRateScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.QuickOtEntryDialog
import com.example.ui.screens.SalarySetupGuideDialog
import com.example.ui.screens.TargetGoalDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.OvertimeViewModel
import com.example.util.AppUpdateChecker
import com.example.util.AppUpdateInfo
import com.example.util.LanguageManager
import com.example.util.MonthLockPolicy
import com.example.util.ResponsiveUtils
import com.example.util.SalaryOnboardingPolicy
import com.example.util.ScreenDensityTier
import com.example.util.StartupPopupAction
import com.example.util.StartupPopupGateState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val openQuickOtTrigger = mutableStateOf(false)

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
    var isAppReady by rememberSaveable { mutableStateOf(false) }
    val isInitialSyncComplete by viewModel.isInitialSyncComplete.collectAsStateWithLifecycle()
    val isProfileLoaded by viewModel.isProfileLoaded.collectAsStateWithLifecycle()
    var isInitialUpdateCheckSettled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isInitialSyncComplete) {
        if (isInitialSyncComplete) {
            isAppReady = true
        }
    }

    LaunchedEffect(Unit) {
        if (!isAppReady) {
            delay(2000L)
            isAppReady = true
        }
        delay(600L)
        if (!isInitialUpdateCheckSettled) {
            isInitialUpdateCheckSettled = true
        }
    }

    var currentTab by rememberSaveable { mutableIntStateOf(0) } // 0: Home, 1: OT List, 2: Piece & Slip, 3: Profile

    val selectedMonth by viewModel.selectedYearMonth.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val isSalaryConfigured = remember(profile) { SalaryOnboardingPolicy.isSalaryConfigured(profile) }
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
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current

    // One-time legacy locale handoff once Room/initial sync finishes
    var hasRunLocaleMigration by remember { mutableStateOf(false) }
    LaunchedEffect(isInitialSyncComplete, profile, allOtRecords.size) {
        if (isInitialSyncComplete && !hasRunLocaleMigration) {
            hasRunLocaleMigration = true
            val hasConfiguredData = profile.basicSalary > 0.0 ||
                profile.userName.isNotBlank() ||
                profile.lastCloudSyncTime > 0L ||
                allOtRecords.isNotEmpty()
            val migrated = LanguageManager.migrateLegacyLocaleIfNeeded(
                context = context,
                legacyProfileLanguage = profile.language,
                hasConfiguredData = hasConfiguredData
            )
            if (migrated != profile.language) {
                viewModel.syncSystemAppLocaleToProfile(migrated)
            }
        }
    }

    val selectedSettingTag = remember(configuration, profile.language) {
        LanguageManager.getSelectedSettingTag(context)
    }
    val effectiveLangTag = remember(configuration, selectedSettingTag) {
        LanguageManager.getEffectiveLanguageTag(context, fallbackProfileLang = null)
    }
    val latestEffectiveLang by rememberUpdatedState(effectiveLangTag)
    val latestProfileLanguage by rememberUpdatedState(profile.language)

    // Notification permission launcher for Android 13+ (API 33+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            ReminderScheduler.scheduleDailyReminders(context, langTag = latestEffectiveLang)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        viewModel.uiMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(effectiveLangTag) {
        ReminderScheduler.scheduleDailyReminders(context, langTag = effectiveLangTag)
        try {
            MyFirebaseMessagingService.createFcmChannel(context, langTag = effectiveLangTag)
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to create FCM notification channel: ${e.message}")
        }
    }

    // Dialog States
    var appUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showQuickOtDialog by rememberSaveable { mutableStateOf(false) }
    var hasCheckedMissingSalaryThisSession by rememberSaveable { mutableStateOf(false) }
    var showMissingSalaryDialog by remember { mutableStateOf(false) }
    var hasCheckedMissingOtThisSession by rememberSaveable { mutableStateOf(false) }
    var showMissingOtDialog by remember { mutableStateOf(false) }
    var missingOtDates by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingProfileHighlightField by rememberSaveable { mutableStateOf<String?>(null) }
    var profileHighlightTriggerId by rememberSaveable { mutableIntStateOf(0) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshMonthRolloverIfNeeded()
                // Keep Android 13+ system App Language changes synced into UserProfile
                val currentSystemAppSetting = LanguageManager.getSelectedSettingTag(context)
                if (hasRunLocaleMigration && currentSystemAppSetting != latestProfileLanguage) {
                    viewModel.syncSystemAppLocaleToProfile(currentSystemAppSetting)
                }
                if (appUpdateInfo == null) {
                    coroutineScope.launch {
                        try {
                            val update = AppUpdateChecker.checkUpdate(context)
                            if (update != null) {
                                appUpdateInfo = update
                            }
                        } finally {
                            isInitialUpdateCheckSettled = true
                        }
                    }
                } else {
                    isInitialUpdateCheckSettled = true
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
            showMissingSalaryDialog = false
            showQuickOtDialog = true
            hasCheckedMissingOtThisSession = true
            onResetQuickOtTrigger()
        }
    }

    LaunchedEffect(isSalaryConfigured, currentTab) {
        if (isSalaryConfigured) {
            showMissingSalaryDialog = false
            hasCheckedMissingSalaryThisSession = true
        } else {
            showMissingOtDialog = false
            if (currentTab == 3) {
                showMissingSalaryDialog = false
            }
        }
    }

    var editingRecordId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingRecordDate by rememberSaveable { mutableStateOf<String?>(null) }
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
    var showPieceDialog by rememberSaveable { mutableStateOf(false) }
    var showTargetDialog by rememberSaveable { mutableStateOf(false) }
    var isHomeSheetOpen by remember { mutableStateOf(false) }
    var isProfileSheetOpen by remember { mutableStateOf(false) }

    // Back handling: Return to Home if on secondary tab when no in-screen sheet/panel is open
    BackHandler(enabled = isAppReady && currentTab != 0 && !showQuickOtDialog && !showMissingOtDialog && !showMissingSalaryDialog && !showPieceDialog && !showTargetDialog && !showLanguagePicker) {
        currentTab = 0
    }

    val currentUpdate = appUpdateInfo
    val isAnyStartupBlockerOrOverlayOpen = showQuickOtDialog ||
        showMissingOtDialog ||
        showMissingSalaryDialog ||
        showPieceDialog ||
        showTargetDialog ||
        showLanguagePicker ||
        isHomeSheetOpen ||
        isProfileSheetOpen

    LaunchedEffect(
        isAppReady,
        isProfileLoaded,
        isInitialUpdateCheckSettled,
        isAuthLoading,
        isSigningOut,
        currentUpdate,
        pendingConsentUser,
        openQuickOtTrigger,
        isAnyStartupBlockerOrOverlayOpen,
        currentTab,
        isSalaryConfigured,
        hasCheckedMissingSalaryThisSession,
        hasCheckedMissingOtThisSession,
        otRecords,
        allOtRecords,
        previousMonthSettings
    ) {
        val action = SalaryOnboardingPolicy.evaluateStartupPopupAction(
            StartupPopupGateState(
                isAppReady = isAppReady,
                isProfileLoaded = isProfileLoaded,
                isInitialUpdateCheckSettled = isInitialUpdateCheckSettled,
                isAuthLoading = isAuthLoading,
                isSigningOut = isSigningOut,
                hasUpdateDialog = currentUpdate != null,
                hasConsentDialog = pendingConsentUser != null,
                hasQuickOtTrigger = openQuickOtTrigger,
                isAnySheetOrDialogOpen = isAnyStartupBlockerOrOverlayOpen,
                currentTab = currentTab,
                isSalaryConfigured = isSalaryConfigured,
                hasCheckedMissingSalaryThisSession = hasCheckedMissingSalaryThisSession,
                hasCheckedMissingOtThisSession = hasCheckedMissingOtThisSession
            )
        )

        when (action) {
            StartupPopupAction.SHOW_SALARY_GUIDE -> {
                delay(250)
                hasCheckedMissingSalaryThisSession = true
                // Hard rule: never show Missing OT Reminder when salary isn't configured,
                // and avoid ambushing fresh-install users in the same session right after setup.
                hasCheckedMissingOtThisSession = true
                showMissingOtDialog = false
                showMissingSalaryDialog = true
            }
            StartupPopupAction.CHECK_MISSING_OT -> {
                delay(350)
                val prevYm = MonthLockPolicy.getPreviousCalendarYearMonth()
                val isPrevUnlocked = !MonthLockPolicy.resolveIsMonthLocked(prevYm, previousMonthSettings?.isLocked)
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
            StartupPopupAction.NONE -> Unit
        }
    }

    // Only true window-level dialogs and modal bottom sheets cover the bottom banner;
    // in-screen content panels (Salary Guide, Quick OT, Missing OT, Piece, Target) stay above bottomBar.
    val isAnyOverlayOccludingBanner = showLanguagePicker ||
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
                lang = effectiveLangTag
            )
        }
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val imeBottomPx = WindowInsets.ime.getBottom(density)
            val navBarBottomPx = WindowInsets.navigationBars.getBottom(density)
            val isImeVisible = imeBottomPx > navBarBottomPx
            val imeBottomDp = with(density) { imeBottomPx.toDp() }
            val remainingAboveImeDp = maxHeight - imeBottomDp
            val hideBannerForTightIme = isImeVisible && remainingAboveImeDp < 240.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = if (!isImeVisible) NavigationBarDefaults.Elevation else 1.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(
                                    WindowInsets.ime
                                        .union(WindowInsets.navigationBars)
                                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                                )
                        ) {
                            if (!hideBannerForTightIme) {
                                AdmobBanner(
                                    modifier = Modifier.fillMaxWidth(),
                                    isOccluded = isAnyOverlayOccludingBanner,
                                    canLoadAd = isAppReady
                                )
                            }
                            val spacing = ResponsiveUtils.rememberAppSpacing()
                            val navBarHeight = when (spacing.tier) {
                                ScreenDensityTier.MICRO_COMPACT -> 58.dp
                                ScreenDensityTier.EXTRA_COMPACT -> 64.dp
                                ScreenDensityTier.STANDARD_COMPACT -> 72.dp
                            }
                            val navLabelMaxSp = spacing.adaptiveSp(
                                standard = 12f,
                                compact = 11f,
                                micro = 10f
                            )

                            AnimatedVisibility(
                                visible = !isImeVisible,
                                enter = fadeIn(tween(150)) + expandVertically(tween(180)),
                                exit = fadeOut(tween(120)) + shrinkVertically(tween(150))
                            ) {
                                NavigationBar(
                                    modifier = Modifier
                                        .height(navBarHeight)
                                        .testTag("bottom_navigation_bar"),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 0.dp,
                                    windowInsets = WindowInsets(0, 0, 0, 0)
                                ) {
                        // Tab 0: Home
                        val tabHomeLabel = stringResource(R.string.tab_home)
                        NavigationBarItem(
                            selected = currentTab == 0,
                            onClick = {
                                showQuickOtDialog = false
                                editingRecordId = null
                                editingRecordDate = null
                                showMissingOtDialog = false
                                showMissingSalaryDialog = false
                                showPieceDialog = false
                                showTargetDialog = false
                                currentTab = 0
                            },
                            icon = {
                                Icon(
                                    if (currentTab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                                    contentDescription = tabHomeLabel,
                                    modifier = Modifier.size(spacing.iconLg)
                                )
                            },
                            label = {
                                AutoFitSingleLineText(
                                    text = tabHomeLabel,
                                    fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    maxFontSize = navLabelMaxSp,
                                    minFontSize = 7.5.sp
                                )
                            },
                            modifier = Modifier.testTag("tab_home_item")
                        )

                        // Tab 1: OT List
                        val tabOtLabel = stringResource(R.string.tab_ot_list)
                        NavigationBarItem(
                            selected = currentTab == 1,
                            onClick = {
                                showQuickOtDialog = false
                                editingRecordId = null
                                editingRecordDate = null
                                showMissingOtDialog = false
                                showMissingSalaryDialog = false
                                showPieceDialog = false
                                showTargetDialog = false
                                currentTab = 1
                            },
                            icon = {
                                Icon(
                                    if (currentTab == 1) Icons.Filled.AccessTimeFilled else Icons.Outlined.AccessTime,
                                    contentDescription = tabOtLabel,
                                    modifier = Modifier.size(spacing.iconLg)
                                )
                            },
                            label = {
                                AutoFitSingleLineText(
                                    text = tabOtLabel,
                                    fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    maxFontSize = navLabelMaxSp,
                                    minFontSize = 7.5.sp
                                )
                            },
                            modifier = Modifier.testTag("tab_ot_list_item")
                        )

                        // Tab 2: Piece Rate & Slip
                        val tabPieceLabel = stringResource(R.string.tab_piece_rate)
                        NavigationBarItem(
                            selected = currentTab == 2,
                            onClick = {
                                showQuickOtDialog = false
                                editingRecordId = null
                                editingRecordDate = null
                                showMissingOtDialog = false
                                showMissingSalaryDialog = false
                                showPieceDialog = false
                                showTargetDialog = false
                                currentTab = 2
                            },
                            icon = {
                                Icon(
                                    if (currentTab == 2) Icons.AutoMirrored.Filled.ReceiptLong else Icons.AutoMirrored.Outlined.ReceiptLong,
                                    contentDescription = tabPieceLabel,
                                    modifier = Modifier.size(spacing.iconLg)
                                )
                            },
                            label = {
                                AutoFitSingleLineText(
                                    text = tabPieceLabel,
                                    fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Normal,
                                    maxFontSize = navLabelMaxSp,
                                    minFontSize = 7.5.sp
                                )
                            },
                            modifier = Modifier.testTag("tab_piece_item")
                        )

                        // Tab 3: Profile & Settings
                        val tabProfileLabel = stringResource(R.string.tab_profile)
                        NavigationBarItem(
                            selected = currentTab == 3,
                            onClick = {
                                showQuickOtDialog = false
                                editingRecordId = null
                                editingRecordDate = null
                                showMissingOtDialog = false
                                showMissingSalaryDialog = false
                                showPieceDialog = false
                                showTargetDialog = false
                                currentTab = 3
                            },
                            icon = {
                                Icon(
                                    if (currentTab == 3) Icons.Filled.Person else Icons.Outlined.Person,
                                    contentDescription = tabProfileLabel,
                                    modifier = Modifier.size(spacing.iconLg)
                                )
                            },
                            label = {
                                AutoFitSingleLineText(
                                    text = tabProfileLabel,
                                    fontWeight = if (currentTab == 3) FontWeight.Bold else FontWeight.Normal,
                                    maxFontSize = navLabelMaxSp,
                                    minFontSize = 7.5.sp
                                )
                            },
                                modifier = Modifier.testTag("tab_profile_item")
                                )
                            }
                        }
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
                                effectiveLang = effectiveLangTag,
                                selectedSettingTag = selectedSettingTag,
                                onPreviousMonth = { viewModel.previousMonth() },
                                onNextMonth = { viewModel.nextMonth() },
                                onOpenLanguagePicker = { showLanguagePicker = true },
                                onQuickAddOtClick = {
                                    editingRecordId = null
                                    val currentCalYm = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
                                    editingRecordDate = if (selectedMonth < currentCalYm) {
                                        MonthLockPolicy.getDefaultDateForMonth(selectedMonth)
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
                                lang = effectiveLangTag,
                                onPreviousMonth = { viewModel.previousMonth() },
                                onNextMonth = { viewModel.nextMonth() },
                                onAddClick = {
                                    editingRecordId = null
                                    val currentCalYm = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
                                    editingRecordDate = if (selectedMonth < currentCalYm) {
                                        MonthLockPolicy.getDefaultDateForMonth(selectedMonth)
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
                                lang = effectiveLangTag,
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
                                effectiveLang = effectiveLangTag,
                                selectedSettingTag = selectedSettingTag,
                                currentUser = currentUser,
                                isAuthLoading = isAuthLoading,
                                isSigningOut = isSigningOut,
                                onSignInWithGoogle = {
                                    activity?.let { act ->
                                        viewModel.signInWithGoogle(act) { err ->
                                            Toast.makeText(act, err, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onSignOut = { onResult ->
                                    viewModel.signOut { success, msg ->
                                        onResult(success, msg)
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                },
                                onSaveProfile = { updated -> viewModel.updateProfile(updated) },
                                onOpenLanguagePicker = { showLanguagePicker = true },
                                trashItems = trashItems,
                                onRestoreTrash = { viewModel.restoreTrashItem(it) },
                                onDeleteTrashPermanently = { viewModel.deleteTrashItemPermanently(it) },
                                onClearAllTrash = { viewModel.clearAllTrash() },
                                onDeleteAllData = { onResult ->
                                    viewModel.deleteAllData { success ->
                                        onResult(success)
                                        val lctx = LanguageManager.getLocalizedContext(context, latestEffectiveLang)
                                        val msg = if (success) {
                                            lctx.getString(R.string.delete_all_success_toast)
                                        } else {
                                            lctx.getString(R.string.delete_all_failed_toast)
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                },
                                highlightField = pendingProfileHighlightField,
                                highlightTriggerId = profileHighlightTriggerId,
                                onConsumeHighlight = { pendingProfileHighlightField = null },
                                onModalSheetVisibilityChanged = { isProfileSheetOpen = it }
                            )
                        }

                        // Missing Salary Onboarding Guide In-Screen Panel (Above bottomBar so AdmobBanner stays active)
                        AnimatedVisibility(
                            visible = showMissingSalaryDialog && !isSalaryConfigured && currentUpdate == null && pendingConsentUser == null,
                            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220)),
                            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.95f, animationSpec = tween(160))
                        ) {
                            SalarySetupGuideDialog(
                                lang = effectiveLangTag,
                                onDismiss = { showMissingSalaryDialog = false },
                                onStartSetup = {
                                    showMissingSalaryDialog = false
                                    hasCheckedMissingSalaryThisSession = true
                                    pendingProfileHighlightField = "SALARY"
                                    profileHighlightTriggerId += 1
                                    currentTab = 3
                                }
                            )
                        }

                        // Missing Overtime Entry Reminder In-Screen Panel (Strictly suppressed when salary not configured)
                        if (showMissingOtDialog && !showQuickOtDialog && !showMissingSalaryDialog && isSalaryConfigured) {
                            MissingOtReminderDialog(
                                missingDates = missingOtDates,
                                lang = effectiveLangTag,
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
                                lang = effectiveLangTag,
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
                                lang = effectiveLangTag,
                                defaultDate = MonthLockPolicy.getDefaultDateForMonth(selectedMonth),
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
                                currentTarget = summary.targetHours,
                                lang = effectiveLangTag,
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
        }

        // Language Selection Bottom Sheet (System Default + EN/BN/HI/TA/AR)
        if (showLanguagePicker) {
            LanguagePickerBottomSheet(
                selectedSettingTag = selectedSettingTag,
                onSelectLanguage = { tag -> viewModel.setLanguage(tag) },
                onDismiss = { showLanguagePicker = false }
            )
        }

        // Google Sign-In Privacy Consent Dialog
        val pendingUser = pendingConsentUser
        if (pendingUser != null) {
            InitialPrivacyConsentDialog(
                titleText = stringResource(R.string.privacy_consent_title),
                confirmButtonText = stringResource(R.string.privacy_consent_confirm_signin),
                dismissButtonText = stringResource(R.string.privacy_consent_decline_offline),
                onAgree = {
                    viewModel.confirmPrivacyConsent(pendingUser)
                },
                onDecline = {
                    viewModel.declinePrivacyConsent()
                },
                onViewFullPolicy = {
                    val privacyUrl = "https://mahmudappsofficial-072.github.io/overtime-tracker-privacy-policy/"
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.w("MainActivity", "Unable to open privacy policy link: ${e.message}")
                    }
                }
            )
        }

        // Optional / Flexible Update Dialog
        if (currentUpdate != null && !currentUpdate.isForceUpdate) {
            ForceUpdateDialog(
                updateInfo = currentUpdate,
                lang = effectiveLangTag,
                onDismissOptional = {
                    AppUpdateChecker.snoozeOptionalUpdate(context, currentUpdate.latestVersionCode)
                    appUpdateInfo = null
                }
            )
        }
    }
}
