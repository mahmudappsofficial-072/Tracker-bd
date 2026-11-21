package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.ui.theme.NightBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

internal fun shouldShowMissingFieldHighlight(
    activeHighlightField: String?,
    targetField: String,
    inputText: String
): Boolean {
    return activeHighlightField == targetField && inputText.isBlank()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(
    profile: UserProfile,
    lang: String,
    currentUser: com.google.firebase.auth.FirebaseUser? = null,
    isAuthLoading: Boolean = false,
    isSigningOut: Boolean = false,
    onSignInWithGoogle: () -> Unit = {},
    onSignOut: ((Boolean, String) -> Unit) -> Unit = { it(false, "") },
    onSaveProfile: (UserProfile) -> Unit,
    onToggleLanguage: () -> Unit,
    onSyncCloud: ((Boolean, String) -> Unit) -> Unit = {},
    trashItems: List<com.example.data.model.TrashItem> = emptyList(),
    onRestoreTrash: (com.example.data.model.TrashItem) -> Unit = {},
    onDeleteTrashPermanently: (com.example.data.model.TrashItem) -> Unit = {},
    onClearAllTrash: () -> Unit = {},
    onDeleteAllData: ((Boolean) -> Unit) -> Unit = { it(true) },
    highlightField: String? = null,
    highlightTriggerId: Int = 0,
    onConsumeHighlight: () -> Unit = {},
    onModalSheetVisibilityChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val isBn = lang == "bn"
    val isCompactScreen = com.example.util.ResponsiveUtils.isCompactScreen()
    var showTrashDialog by remember { mutableStateOf(false) }
    var showDeleteAllConfirmDialog by remember { mutableStateOf(false) }

    fun computeGrossText(p: UserProfile): String = if (p.basicSalary > 0.0) {
        if (p.isCustomSalary) {
            LaborLawCalculator.formatEditableNumber(p.basicSalary)
        } else {
            LaborLawCalculator.formatEditableNumber(LaborLawCalculator.calculateAutoAllowances(p.basicSalary).grossSalary)
        }
    } else {
        ""
    }

    fun computeMedicalText(p: UserProfile): String = when {
        p.isCustomSalary -> "0"
        p.medicalAllowance == 0.0 && p.basicSalary == 0.0 -> "0"
        p.medicalAllowance == 0.0 || p.medicalAllowance == 1250.0 -> "750"
        else -> p.medicalAllowance.toInt().toString()
    }

    // Profile state
    var userName by remember { mutableStateOf(profile.userName) }
    var companyName by remember { mutableStateOf(profile.companyName) }
    var designation by remember { mutableStateOf(profile.designation) }
    var workerId by remember { mutableStateOf(profile.workerId) }
    var grade by remember { mutableStateOf(profile.grade) }

    // Salary state
    var isCustomSalary by remember { mutableStateOf(profile.isCustomSalary) }
    var grossSalaryText by remember { mutableStateOf(computeGrossText(profile)) }

    var basicSalaryText by remember {
        mutableStateOf(LaborLawCalculator.formatEditableNumber(profile.basicSalary))
    }
    var houseRentText by remember {
        mutableStateOf(if (profile.isCustomSalary || profile.houseRent == 0.0) "" else LaborLawCalculator.formatEditableNumber(profile.houseRent))
    }
    var medicalText by remember {
        mutableStateOf(computeMedicalText(profile))
    }
    var conveyanceText by remember {
        mutableStateOf(if (profile.isCustomSalary || profile.conveyanceAllowance == 0.0) "" else LaborLawCalculator.formatEditableNumber(profile.conveyanceAllowance))
    }
    var foodText by remember {
        mutableStateOf(if (profile.isCustomSalary || profile.foodAllowance == 0.0) "" else LaborLawCalculator.formatEditableNumber(profile.foodAllowance))
    }
    var attendanceBonusText by remember {
        mutableStateOf(LaborLawCalculator.formatEditableNumber(profile.attendanceBonus))
    }
    var maxAbsentForBonusText by remember {
        mutableStateOf(if (profile.maxAbsentForBonus <= 0) "" else profile.maxAbsentForBonus.toString())
    }
    var maxLeaveForBonusText by remember {
        mutableStateOf(if (profile.maxLeaveForBonus == null) "" else profile.maxLeaveForBonus.toString())
    }
    var nightBillRateText by remember {
        mutableStateOf(LaborLawCalculator.formatEditableNumber(profile.nightBillRate))
    }
    var tiffinBillRateText by remember {
        mutableStateOf(LaborLawCalculator.formatEditableNumber(profile.tiffinBillRate))
    }
    var customOtRateText by remember {
        mutableStateOf(LaborLawCalculator.formatEditableNumber(profile.customOtRate))
    }

    val isProfileConfigured = profile.basicSalary > 0.0 || profile.userName.isNotBlank()
    var isEditing by remember { mutableStateOf(!isProfileConfigured) }

    val syncFormFromProfile: (UserProfile) -> Unit = { p ->
        userName = p.userName
        companyName = p.companyName
        designation = p.designation
        workerId = p.workerId
        grade = p.grade
        isCustomSalary = p.isCustomSalary
        grossSalaryText = computeGrossText(p)
        basicSalaryText = LaborLawCalculator.formatEditableNumber(p.basicSalary)
        houseRentText = if (p.isCustomSalary || p.houseRent == 0.0) "" else LaborLawCalculator.formatEditableNumber(p.houseRent)
        medicalText = computeMedicalText(p)
        conveyanceText = if (p.isCustomSalary || p.conveyanceAllowance == 0.0) "" else LaborLawCalculator.formatEditableNumber(p.conveyanceAllowance)
        foodText = if (p.isCustomSalary || p.foodAllowance == 0.0) "" else LaborLawCalculator.formatEditableNumber(p.foodAllowance)
        attendanceBonusText = LaborLawCalculator.formatEditableNumber(p.attendanceBonus)
        maxAbsentForBonusText = if (p.maxAbsentForBonus <= 0) "" else p.maxAbsentForBonus.toString()
        maxLeaveForBonusText = if (p.maxLeaveForBonus == null) "" else p.maxLeaveForBonus.toString()
        nightBillRateText = LaborLawCalculator.formatEditableNumber(p.nightBillRate)
        tiffinBillRateText = LaborLawCalculator.formatEditableNumber(p.tiffinBillRate)
        customOtRateText = LaborLawCalculator.formatEditableNumber(p.customOtRate)
    }

    var activeHighlightField by rememberSaveable { mutableStateOf<String?>(null) }
    var lastHandledTriggerId by rememberSaveable { mutableIntStateOf(0) }
    var blinkPhaseOn by remember { mutableStateOf(true) }

    val resetForm = {
        syncFormFromProfile(profile)
        activeHighlightField = null
        isEditing = false
    }

    BackHandler(enabled = isEditing && isProfileConfigured) {
        resetForm()
    }

    LaunchedEffect(profile, isEditing) {
        if (!isEditing) {
            syncFormFromProfile(profile)
        }
    }

    LaunchedEffect(profile.maxLeaveForBonus) {
        maxLeaveForBonusText = if (profile.maxLeaveForBonus == null) "" else profile.maxLeaveForBonus.toString()
    }

    LaunchedEffect(isProfileConfigured) {
        if (!isProfileConfigured) {
            isEditing = true
            syncFormFromProfile(profile)
        } else if (profile.lastCloudSyncTime > 0L && userName.isBlank() && (basicSalaryText.isBlank() || basicSalaryText == "0")) {
            syncFormFromProfile(profile)
            isEditing = false
        }
    }

    val listState = rememberLazyListState()
    val salaryBringIntoViewRequester = remember { BringIntoViewRequester() }
    val nightBringIntoViewRequester = remember { BringIntoViewRequester() }
    val tiffinBringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(highlightTriggerId) {
        if (highlightTriggerId > 0 && highlightTriggerId != lastHandledTriggerId) {
            lastHandledTriggerId = highlightTriggerId
            val target = highlightField ?: "SALARY"
            isEditing = true
            activeHighlightField = target
            blinkPhaseOn = true
            onConsumeHighlight()

            kotlinx.coroutines.delay(80L)
            try {
                listState.animateScrollToItem(index = 3)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {}

            withFrameNanos { }
            kotlinx.coroutines.delay(50L)
            val requester = when (target) {
                "NIGHT_BILL" -> nightBringIntoViewRequester
                "TIFFIN_BILL" -> tiffinBringIntoViewRequester
                else -> salaryBringIntoViewRequester
            }
            try {
                requester.bringIntoView()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {}

            try {
                repeat(3) {
                    if (activeHighlightField != target) return@LaunchedEffect
                    blinkPhaseOn = false
                    kotlinx.coroutines.delay(160L)
                    if (activeHighlightField != target) return@LaunchedEffect
                    blinkPhaseOn = true
                    kotlinx.coroutines.delay(200L)
                }
            } finally {
                blinkPhaseOn = true
            }
        }
    }

    // Dialog flags
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }
    var showOfflineSignOutAlert by remember { mutableStateOf(false) }
    val isOnline by com.example.util.NetworkMonitor.rememberIsOnline()

    val hasBlockingSheet = showSettingsDialog || showTrashDialog || showDeleteAllConfirmDialog || showSignOutConfirmDialog
    LaunchedEffect(hasBlockingSheet) {
        onModalSheetVisibilityChanged(hasBlockingSheet)
    }
    DisposableEffect(Unit) {
        onDispose { onModalSheetVisibilityChanged(false) }
    }

    // Auto-calculate allowances from Gross Salary if mode is Automatic
    val currentGross = LaborLawCalculator.parseLocalizedDouble(grossSalaryText)
    val autoResult = remember(currentGross) {
        LaborLawCalculator.calculateFromGrossSalary(currentGross)
    }

    val effectiveHourlyOtRate: Double? = if (isCustomSalary) {
        val customRate = LaborLawCalculator.parseLocalizedDouble(customOtRateText)
        if (customRate > 0.0) customRate else null
    } else {
        autoResult.otHourlyRate
    }

    val errorRed = MaterialTheme.colorScheme.error
    val defaultOutline = MaterialTheme.colorScheme.outline
    val defaultPrimary = MaterialTheme.colorScheme.primary
    val defaultLabel = MaterialTheme.colorScheme.onSurfaceVariant

    val activeSalaryInputText = if (isCustomSalary) basicSalaryText else grossSalaryText
    val isSalaryHighlighted = shouldShowMissingFieldHighlight(activeHighlightField, "SALARY", activeSalaryInputText)
    val isNightHighlighted = shouldShowMissingFieldHighlight(activeHighlightField, "NIGHT_BILL", nightBillRateText)
    val isTiffinHighlighted = shouldShowMissingFieldHighlight(activeHighlightField, "TIFFIN_BILL", tiffinBillRateText)

    val salaryBorderColor by animateColorAsState(
        targetValue = when {
            !isSalaryHighlighted -> defaultOutline
            blinkPhaseOn -> errorRed
            else -> defaultOutline.copy(alpha = 0.25f)
        },
        animationSpec = tween(durationMillis = 90),
        label = "salaryBorderColor"
    )
    val salaryFocusedBorderColor by animateColorAsState(
        targetValue = when {
            !isSalaryHighlighted -> defaultPrimary
            blinkPhaseOn -> errorRed
            else -> defaultOutline.copy(alpha = 0.25f)
        },
        animationSpec = tween(durationMillis = 90),
        label = "salaryFocusedBorderColor"
    )
    val nightBorderColor by animateColorAsState(
        targetValue = when {
            !isNightHighlighted -> defaultOutline
            blinkPhaseOn -> errorRed
            else -> defaultOutline.copy(alpha = 0.25f)
        },
        animationSpec = tween(durationMillis = 90),
        label = "nightBorderColor"
    )
    val nightFocusedBorderColor by animateColorAsState(
        targetValue = when {
            !isNightHighlighted -> defaultPrimary
            blinkPhaseOn -> errorRed
            else -> defaultOutline.copy(alpha = 0.25f)
        },
        animationSpec = tween(durationMillis = 90),
        label = "nightFocusedBorderColor"
    )
    val tiffinBorderColor by animateColorAsState(
        targetValue = when {
            !isTiffinHighlighted -> defaultOutline
            blinkPhaseOn -> errorRed
            else -> defaultOutline.copy(alpha = 0.25f)
        },
        animationSpec = tween(durationMillis = 90),
        label = "tiffinBorderColor"
    )
    val tiffinFocusedBorderColor by animateColorAsState(
        targetValue = when {
            !isTiffinHighlighted -> defaultPrimary
            blinkPhaseOn -> errorRed
            else -> defaultOutline.copy(alpha = 0.25f)
        },
        animationSpec = tween(durationMillis = 90),
        label = "tiffinFocusedBorderColor"
    )

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Action Bar with Settings Icon
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBn) "প্রোফাইল ও বেতন সেটিংস" else "Profile & Salary Setup",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isEditing && isProfileConfigured) {
                        FilledTonalIconButton(
                            onClick = { isEditing = true },
                            modifier = Modifier.testTag("top_edit_profile_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Profile")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    FilledTonalIconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            }
        }

        // Account Card (International Google Authentication System)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().testTag("account_card")
            ) {
                if (currentUser == null) {
                    // State: Unauthenticated
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isBn) "গুগল অ্যাকাউন্ট" else "Google Account",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isBn) "লগইন করা নেই" else "Signed out",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Minimalist international Google Sign-In button
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDADCE0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable(enabled = !isAuthLoading) { onSignInWithGoogle() }
                                .testTag("google_sign_in_button")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isAuthLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (isBn) "সাইন-ইন হচ্ছে..." else "Signing in...",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .background(Color(0xFF4285F4), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "G",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (isBn) "Sign in with Google" else "Sign in with Google",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // State: Authenticated
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (!currentUser.photoUrl?.toString().isNullOrBlank()) {
                                coil.compose.AsyncImage(
                                    model = currentUser.photoUrl,
                                    contentDescription = "Profile Photo",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = (currentUser.displayName?.take(1) ?: "U").uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = currentUser.displayName ?: (if (isBn) "গুগল ইউজার" else "Google User"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentUser.email ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SuccessGreen.copy(alpha = 0.12f),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = if (isBn) "সক্রিয় অ্যাকাউন্ট" else "Active Account",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SuccessGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = {
                                if (isSigningOut) return@TextButton
                                if (com.example.util.NetworkMonitor.isStrictlyOnline(context)) {
                                    showSignOutConfirmDialog = true
                                } else {
                                    showOfflineSignOutAlert = true
                                }
                            },
                            enabled = !isSigningOut,
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            if (!isOnline) {
                                Icon(
                                    imageVector = Icons.Default.WifiOff,
                                    contentDescription = if (isBn) "অফলাইন" else "Offline",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = if (isBn) "লগআউট" else "Sign out",
                                color = if (isOnline) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Worker Identity Header Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = userName.ifBlank { if (isBn) "আপনার নাম" else "Your Name" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${companyName.ifBlank { if (isBn) "ফ্যাক্টরি" else "Company" }} • ${designation.ifBlank { if (isBn) "পদবি" else "Designation" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val displayGrade = formatDisplayGrade(grade, isBn).ifBlank { if (isBn) "গ্রেড" else "Grade" }
                                Text(
                                    text = "${if (isBn) "আইডি" else "ID"}: ${workerId.ifBlank { "N/A" }} • $displayGrade",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (!isEditing && isProfileConfigured) {
                            IconButton(
                                onClick = { isEditing = true },
                                modifier = Modifier.testTag("worker_card_edit_button")
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Profile",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    if (isEditing) {
                        // Editable Personal Info
                        OutlinedTextField(
                            value = userName,
                            onValueChange = { userName = it },
                            label = { Text(if (isBn) "শ্রমিকের নাম" else "Worker Name") },
                            modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) }
                        )

                        if (isCompactScreen) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = companyName,
                                    onValueChange = { companyName = it },
                                    label = { Text(if (isBn) "কোম্পানি / ফ্যাক্টরি" else "Company / Factory") },
                                    modifier = Modifier.fillMaxWidth().testTag("profile_company_input"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = designation,
                                    onValueChange = { designation = it },
                                    label = { Text(if (isBn) "পদবি" else "Designation") },
                                    modifier = Modifier.fillMaxWidth().testTag("profile_designation_input"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = workerId,
                                    onValueChange = { workerId = it },
                                    label = { Text(if (isBn) "আইডি নম্বর" else "Worker ID") },
                                    modifier = Modifier.fillMaxWidth().testTag("profile_id_input"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = grade,
                                    onValueChange = { grade = it },
                                    label = { Text(if (isBn) "গ্রেড" else "Grade") },
                                    modifier = Modifier.fillMaxWidth().testTag("profile_grade_input"),
                                    singleLine = true
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = companyName,
                                    onValueChange = { companyName = it },
                                    label = { Text(if (isBn) "কোম্পানি / ফ্যাক্টরি" else "Company / Factory") },
                                    modifier = Modifier.weight(1f).testTag("profile_company_input"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = designation,
                                    onValueChange = { designation = it },
                                    label = { Text(if (isBn) "পদবি" else "Designation") },
                                    modifier = Modifier.weight(1f).testTag("profile_designation_input"),
                                    singleLine = true
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = workerId,
                                    onValueChange = { workerId = it },
                                    label = { Text(if (isBn) "আইডি নম্বর" else "Worker ID") },
                                    modifier = Modifier.weight(1f).testTag("profile_id_input"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = grade,
                                    onValueChange = { grade = it },
                                    label = { Text(if (isBn) "গ্রেড" else "Grade") },
                                    modifier = Modifier.weight(1f).testTag("profile_grade_input"),
                                    singleLine = true
                                )
                            }
                        }
                    } else {
                        // View Mode Information Grid
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                InfoChipCard(
                                    icon = Icons.Default.Business,
                                    label = if (isBn) "কোম্পানি / ফ্যাক্টরি" else "Company / Factory",
                                    value = companyName.ifBlank { if (isBn) "সেট করা নেই" else "Not set" },
                                    modifier = Modifier.weight(1f)
                                )
                                InfoChipCard(
                                    icon = Icons.Default.Work,
                                    label = if (isBn) "পদবি" else "Designation",
                                    value = designation.ifBlank { if (isBn) "সেট করা নেই" else "Not set" },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                InfoChipCard(
                                    icon = Icons.Default.Badge,
                                    label = if (isBn) "আইডি নম্বর" else "Worker ID",
                                    value = workerId.ifBlank { if (isBn) "সেট করা নেই" else "Not set" },
                                    modifier = Modifier.weight(1f)
                                )
                                InfoChipCard(
                                    icon = Icons.Default.Stars,
                                    label = if (isBn) "গ্রেড" else "Grade",
                                    value = formatDisplayGrade(grade, isBn).ifBlank { if (isBn) "সেট করা নেই" else "Not set" },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Salary Setup Mode Switcher Card
        item {
            if (isEditing) {
                Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = if (isBn) "বেতন কাঠামো ও হিসাব পদ্ধতি" else "Salary Structure & Calculation",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Mode Toggle: Automatic vs Custom
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (!isCustomSalary) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    isCustomSalary = false
                                    // sync auto values
                                    basicSalaryText = LaborLawCalculator.formatEditableNumber(autoResult.basicSalary)
                                    houseRentText = LaborLawCalculator.formatEditableNumber(autoResult.houseRent)
                                    medicalText = "750"
                                    conveyanceText = LaborLawCalculator.formatEditableNumber(autoResult.conveyanceAllowance)
                                    foodText = LaborLawCalculator.formatEditableNumber(autoResult.foodAllowance)
                                    customOtRateText = ""
                                }
                                .testTag("mode_auto_tab")
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isBn) "স্বয়ংক্রিয় (আইন অনুযায়ী)" else "Automatic (Labor Law)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    maxLines = 2,
                                    color = if (!isCustomSalary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCustomSalary) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    isCustomSalary = true
                                    // carry over gross as total salary in custom mode if not already set
                                    if (basicSalaryText.isBlank() || basicSalaryText == "0") {
                                        basicSalaryText = if (currentGross > 0.0) grossSalaryText else LaborLawCalculator.formatEditableNumber(autoResult.grossSalary)
                                    }
                                }
                                .testTag("mode_custom_tab")
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isBn) "কাস্টম (নিজস্ব নিয়ম)" else "Custom Rules",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    maxLines = 2,
                                    color = if (isCustomSalary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Explanatory note
                    if (!isCustomSalary) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = if (isBn) {
                                    "⚖️ সরকারি নিয়ম অনুযায়ী মোট বেতন দিলে মূল বেতন, বাড়িভাড়া ও ভাতাসমূহ স্বয়ংক্রিয়ভাবে হিসাব হবে। ওটি হার = মূল বেতন ÷ ১০৪ (দ্বিগুণ হার)।"
                                } else {
                                    "⚖️ Under labor law, basic salary, house rent & allowances are automatically calculated from gross salary. OT Rate = Basic ÷ 104 (2x rate)."
                                },
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = if (isBn) {
                                    "⚙️ কাস্টম মোড: আপনার কারখানার নিয়ম অনুযায়ী মোট বেতন, নাইট/টিফিন বিল, বোনাস ও প্রতি ঘণ্টার ওটি রেট নিজের মতো বসিয়ে নিতে পারবেন (অনুপস্থিতি কর্তন: মোট বেতন ÷ ৩০ দিন)।"
                                } else {
                                    "⚙️ Custom mode: Configure total salary, night/tiffin bill, bonus and hourly OT rate per your company rules (absent deduction: Total Salary ÷ 30 days)."
                                },
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (!isCustomSalary) {
                        // In Automatic mode: User enters Gross Salary
                        OutlinedTextField(
                            value = grossSalaryText,
                            onValueChange = {
                                val normalized = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                                grossSalaryText = normalized
                                if (normalized.isNotEmpty() && activeHighlightField == "SALARY") {
                                    activeHighlightField = null
                                }
                            },
                            label = { Text(if (isBn) "মোট বেতন (Gross Salary ৳)" else "Gross Salary (৳)") },
                            placeholder = { Text("0") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(salaryBringIntoViewRequester)
                                .testTag("gross_salary_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = if (isSalaryHighlighted) errorRed else defaultPrimary
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = salaryFocusedBorderColor,
                                unfocusedBorderColor = salaryBorderColor,
                                focusedLabelColor = if (isSalaryHighlighted) errorRed else defaultPrimary,
                                unfocusedLabelColor = if (isSalaryHighlighted) errorRed else defaultLabel
                            )
                        )
                    } else {
                        // In Custom mode: User enters Total Salary
                        OutlinedTextField(
                            value = basicSalaryText,
                            onValueChange = {
                                val normalized = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                                basicSalaryText = normalized
                                if (normalized.isNotEmpty() && activeHighlightField == "SALARY") {
                                    activeHighlightField = null
                                }
                            },
                            label = { Text(if (isBn) "মোট বেতন (৳)" else "Total Salary (৳)") },
                            placeholder = { Text("0") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(salaryBringIntoViewRequester)
                                .testTag("basic_salary_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = if (isSalaryHighlighted) errorRed else defaultPrimary
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = salaryFocusedBorderColor,
                                unfocusedBorderColor = salaryBorderColor,
                                focusedLabelColor = if (isSalaryHighlighted) errorRed else defaultPrimary,
                                unfocusedLabelColor = if (isSalaryHighlighted) errorRed else defaultLabel
                            )
                        )
                    }

                    // Allowances Section (Only shown in Automatic mode as locked legal breakdown)
                    if (!isCustomSalary) {
                        Text(
                            text = if (isBn) "সরকারি নিয়মে স্বয়ংক্রিয় হিসাব (লকড):" else "Automatic Breakdown (Locked):",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )

                        // Basic Salary (Locked in Auto mode)
                        OutlinedTextField(
                            value = autoResult.basicSalary.roundToInt().toString(),
                            onValueChange = {},
                            label = { Text(if (isBn) "মূল বেতন (লকড)" else "Basic Salary (Locked)") },
                            enabled = false,
                            modifier = Modifier.fillMaxWidth().testTag("locked_basic_salary_input"),
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
                            singleLine = true
                        )

                        if (isCompactScreen) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = autoResult.houseRent.roundToInt().toString(),
                                    onValueChange = {},
                                    label = { Text(if (isBn) "বাড়িভাড়া" else "House Rent") },
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth().testTag("house_rent_input"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = "750",
                                    onValueChange = {},
                                    label = { Text(if (isBn) "চিকিৎসা ভাতা (স্থির ৭৫০)" else "Medical Allowance (Fixed 750)") },
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth().testTag("medical_input"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = autoResult.conveyanceAllowance.roundToInt().toString(),
                                    onValueChange = {},
                                    label = { Text(if (isBn) "যাতায়াত ভাতা" else "Conveyance Allowance") },
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth().testTag("conveyance_input"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = autoResult.foodAllowance.roundToInt().toString(),
                                    onValueChange = {},
                                    label = { Text(if (isBn) "খাদ্য ভাতা" else "Food Allowance") },
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth().testTag("food_input"),
                                    singleLine = true
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = autoResult.houseRent.roundToInt().toString(),
                                    onValueChange = {},
                                    label = { Text(if (isBn) "বাড়িভাড়া" else "House Rent") },
                                    enabled = false,
                                    modifier = Modifier.weight(1f).testTag("house_rent_input"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = "750",
                                    onValueChange = {},
                                    label = { Text(if (isBn) "চিকিৎসা ভাতা (স্থির ৭৫০)" else "Medical Allowance (Fixed 750)") },
                                    enabled = false,
                                    modifier = Modifier.weight(1f).testTag("medical_input"),
                                    singleLine = true
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = autoResult.conveyanceAllowance.roundToInt().toString(),
                                    onValueChange = {},
                                    label = { Text(if (isBn) "যাতায়াত ভাতা" else "Conveyance Allowance") },
                                    enabled = false,
                                    modifier = Modifier.weight(1f).testTag("conveyance_input"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = autoResult.foodAllowance.roundToInt().toString(),
                                    onValueChange = {},
                                    label = { Text(if (isBn) "খাদ্য ভাতা" else "Food Allowance") },
                                    enabled = false,
                                    modifier = Modifier.weight(1f).testTag("food_input"),
                                    singleLine = true
                                )
                            }
                        }
                    }

                    // Night & Tiffin Rates & Attendance Bonus
                    if (isCompactScreen) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = nightBillRateText,
                                onValueChange = {
                                    val normalized = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                                    nightBillRateText = normalized
                                    if (normalized.isNotEmpty() && activeHighlightField == "NIGHT_BILL") {
                                        activeHighlightField = null
                                    }
                                },
                                label = { Text(if (isBn) "নাইট বিল (প্রতি রাত ৳)" else "Night Bill (Per Night ৳)") },
                                placeholder = { Text("0") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(nightBringIntoViewRequester)
                                    .testTag("night_rate_input"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = nightFocusedBorderColor,
                                    unfocusedBorderColor = nightBorderColor,
                                    focusedLabelColor = if (isNightHighlighted) errorRed else defaultPrimary,
                                    unfocusedLabelColor = if (isNightHighlighted) errorRed else defaultLabel
                                )
                            )
                            OutlinedTextField(
                                value = tiffinBillRateText,
                                onValueChange = {
                                    val normalized = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                                    tiffinBillRateText = normalized
                                    if (normalized.isNotEmpty() && activeHighlightField == "TIFFIN_BILL") {
                                        activeHighlightField = null
                                    }
                                },
                                label = { Text(if (isBn) "টিফিন বিল (প্রতি দিন ৳)" else "Tiffin Bill (Per Day ৳)") },
                                placeholder = { Text("0") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(tiffinBringIntoViewRequester)
                                    .testTag("tiffin_rate_input"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = tiffinFocusedBorderColor,
                                    unfocusedBorderColor = tiffinBorderColor,
                                    focusedLabelColor = if (isTiffinHighlighted) errorRed else defaultPrimary,
                                    unfocusedLabelColor = if (isTiffinHighlighted) errorRed else defaultLabel
                                )
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = nightBillRateText,
                                onValueChange = {
                                    val normalized = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                                    nightBillRateText = normalized
                                    if (normalized.isNotEmpty() && activeHighlightField == "NIGHT_BILL") {
                                        activeHighlightField = null
                                    }
                                },
                                label = { Text(if (isBn) "নাইট বিল (প্রতি রাত ৳)" else "Night Bill (Per Night ৳)") },
                                placeholder = { Text("0") },
                                modifier = Modifier
                                    .weight(1f)
                                    .bringIntoViewRequester(nightBringIntoViewRequester)
                                    .testTag("night_rate_input"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = nightFocusedBorderColor,
                                    unfocusedBorderColor = nightBorderColor,
                                    focusedLabelColor = if (isNightHighlighted) errorRed else defaultPrimary,
                                    unfocusedLabelColor = if (isNightHighlighted) errorRed else defaultLabel
                                )
                            )
                            OutlinedTextField(
                                value = tiffinBillRateText,
                                onValueChange = {
                                    val normalized = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                                    tiffinBillRateText = normalized
                                    if (normalized.isNotEmpty() && activeHighlightField == "TIFFIN_BILL") {
                                        activeHighlightField = null
                                    }
                                },
                                label = { Text(if (isBn) "টিফিন বিল (প্রতি দিন ৳)" else "Tiffin Bill (Per Day ৳)") },
                                placeholder = { Text("0") },
                                modifier = Modifier
                                    .weight(1f)
                                    .bringIntoViewRequester(tiffinBringIntoViewRequester)
                                    .testTag("tiffin_rate_input"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = tiffinFocusedBorderColor,
                                    unfocusedBorderColor = tiffinBorderColor,
                                    focusedLabelColor = if (isTiffinHighlighted) errorRed else defaultPrimary,
                                    unfocusedLabelColor = if (isTiffinHighlighted) errorRed else defaultLabel
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = attendanceBonusText,
                        onValueChange = { attendanceBonusText = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true) },
                        label = { Text(if (isBn) "হাজিরা বোনাস (৳)" else "Attendance Bonus (৳)") },
                        placeholder = { Text("0") },
                        modifier = Modifier.fillMaxWidth().testTag("attendance_bonus_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = maxAbsentForBonusText,
                        onValueChange = { input ->
                            maxAbsentForBonusText = LaborLawCalculator.normalizeNumericInput(input, allowDecimal = false)
                        },
                        label = { Text(if (isBn) "হাজিরা বোনাস কাটার শর্ত (অনুপস্থিতির দিন)" else "Bonus Deduction Threshold (Absent Days)") },
                        placeholder = { Text("0") },
                        modifier = Modifier.fillMaxWidth().testTag("max_absent_bonus_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        supportingText = {
                            Text(
                                text = if (isBn) {
                                    "আপনার অফিসের নিয়মানুযায়ী লিখুন (যেমন: ৩ দিন দিলে ৩ দিন পর্যন্ত বোনাস অক্ষত থাকবে, ৪র্থ দিন থেকে বাতিল হবে)।"
                                } else {
                                    "Enter according to company policy (e.g. 3 days: bonus remains intact up to 3 absent days, cancelled on 4th day)."
                                },
                                fontSize = 11.sp
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    )

                    // Custom OT Rate if in custom mode
                    if (isCustomSalary) {
                        OutlinedTextField(
                            value = customOtRateText,
                            onValueChange = { customOtRateText = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true) },
                            label = { Text(if (isBn) "কাস্টম ওটি রেট (প্রতি ঘণ্টা ৳)" else "Custom OT Rate (Per Hour ৳)") },
                            placeholder = { Text("0") },
                            modifier = Modifier.fillMaxWidth().testTag("custom_ot_rate_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            supportingText = {
                                Text(
                                    if (isBn) "প্রতি ঘণ্টার ওটি রেট লিখুন (ইনপুট না দিলে কার্যকর রেট খালি থাকবে)"
                                    else "Enter hourly OT rate (effective rate remains blank until entered)"
                                )
                            }
                        )
                    }

                    // Live Calculated Rate Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isBn) "কার্যকর ওটি রেট:" else "Effective OT Rate:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (effectiveHourlyOtRate != null && effectiveHourlyOtRate > 0.0) {
                                        LaborLawCalculator.formatCurrency(effectiveHourlyOtRate, isBn) + (if (isBn) " / ঘণ্টা" else " / hr")
                                    } else {
                                        "—"
                                    },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isBn) "মোট সাধারণ বেতন:" else "Total Regular Salary:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                val calculatedGross = if (isCustomSalary) {
                                    LaborLawCalculator.parseLocalizedDouble(basicSalaryText)
                                } else {
                                    autoResult.grossSalary
                                }
                                Text(
                                    text = LaborLawCalculator.formatCurrency(calculatedGross, isBn),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        } else {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("salary_summary_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isBn) "বেতন ও ভাতা কাঠামো" else "Salary & Allowances",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (!isCustomSalary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = if (!isCustomSalary) {
                                        if (isBn) "⚖️ সরকারি আইন অনুযায়ী" else "⚖️ Labor Law"
                                    } else {
                                        if (isBn) "⚙️ কাস্টম নিয়ম" else "⚙️ Custom Rules"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (!isCustomSalary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Highlight Metrics (Gross & OT Rate)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (isBn) "মোট সাধারণ বেতন:" else "Total Regular Salary:",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    val displayGross = if (isCustomSalary) {
                                        LaborLawCalculator.parseLocalizedDouble(basicSalaryText)
                                    } else {
                                        autoResult.grossSalary
                                    }
                                    Text(
                                        text = LaborLawCalculator.formatCurrency(displayGross, isBn),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (isBn) "কার্যকর ওটি রেট:" else "Effective OT Rate:",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = if (effectiveHourlyOtRate != null && effectiveHourlyOtRate > 0.0) {
                                            LaborLawCalculator.formatCurrency(effectiveHourlyOtRate, isBn) + (if (isBn) " / ঘণ্টা" else " / hr")
                                        } else {
                                            "—"
                                        },
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Allowances breakdown if automatic
                        if (!isCustomSalary && autoResult.grossSalary > 0.0) {
                            Text(
                                text = if (isBn) "সরকারি নিয়মে ভাতার বিভাজন:" else "Allowances Breakdown (Labor Law):",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    SalaryBreakdownRow(if (isBn) "মূল বেতন (Basic)" else "Basic Salary", autoResult.basicSalary, isBn)
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    SalaryBreakdownRow(if (isBn) "বাড়িভাড়া (House Rent)" else "House Rent", autoResult.houseRent, isBn)
                                    SalaryBreakdownRow(if (isBn) "চিকিৎসা ভাতা (Medical)" else "Medical Allowance", 750.0, isBn)
                                    SalaryBreakdownRow(if (isBn) "যাতায়াত ভাতা (Conveyance)" else "Conveyance", autoResult.conveyanceAllowance, isBn)
                                    SalaryBreakdownRow(if (isBn) "খাদ্য ভাতা (Food)" else "Food Allowance", autoResult.foodAllowance, isBn)
                                }
                            }
                        }

                        // Additional benefits list
                        val nightVal = LaborLawCalculator.parseLocalizedDouble(nightBillRateText)
                        val tiffinVal = LaborLawCalculator.parseLocalizedDouble(tiffinBillRateText)
                        val bonusVal = LaborLawCalculator.parseLocalizedDouble(attendanceBonusText)
                        val hasBenefits = bonusVal > 0.0 || nightVal > 0.0 || tiffinVal > 0.0
                        if (hasBenefits) {
                            Text(
                                text = if (isBn) "অন্যান্য সুযোগ-সুবিধা ও বিল:" else "Other Allowances & Bills:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (nightVal > 0.0) {
                                    BenefitChip(
                                        label = if (isBn) "নাইট বিল" else "Night Bill",
                                        value = LaborLawCalculator.formatCurrency(nightVal, isBn) + (if (isBn) " /রাত" else " /night"),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (tiffinVal > 0.0) {
                                    BenefitChip(
                                        label = if (isBn) "টিফিন বিল" else "Tiffin Bill",
                                        value = LaborLawCalculator.formatCurrency(tiffinVal, isBn) + (if (isBn) " /দিন" else " /day"),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            if (bonusVal > 0.0) {
                                val absentDays = maxAbsentForBonusText.ifBlank { "0" }
                                val leaveLimitPartBn = profile.maxLeaveForBonus?.takeIf { it > 0 }?.let {
                                    ", ছুটি সীমা: ${LaborLawCalculator.toBengaliDigits(it.toString())} দিন"
                                } ?: ""
                                val leaveLimitPartEn = profile.maxLeaveForBonus?.takeIf { it > 0 }?.let {
                                    ", Max leave: $it days"
                                } ?: ""
                                BenefitChip(
                                    label = if (isBn) "হাজিরা বোনাস" else "Attendance Bonus",
                                    value = "${LaborLawCalculator.formatCurrency(bonusVal, isBn)} " +
                                            (if (isBn) "(অনুপস্থিতি সীমা: ${LaborLawCalculator.toBengaliDigits(absentDays)} দিন$leaveLimitPartBn)"
                                            else "(Max absent: $absentDays days$leaveLimitPartEn)"),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Action Buttons: Save/Cancel in Edit Mode, or Edit Button in View Mode
        item {
            if (isEditing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isProfileConfigured) {
                        OutlinedButton(
                            onClick = { resetForm() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cancel_edit_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isBn) "বাতিল" else "Cancel", fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            val basic = if (isCustomSalary) {
                                LaborLawCalculator.parseLocalizedDouble(basicSalaryText)
                            } else {
                                autoResult.basicSalary
                            }
                            val house = if (isCustomSalary) 0.0 else autoResult.houseRent
                            val medical = if (isCustomSalary) 0.0 else 750.0
                            val conveyance = if (isCustomSalary) 0.0 else autoResult.conveyanceAllowance
                            val food = if (isCustomSalary) 0.0 else autoResult.foodAllowance
                            val attendance = LaborLawCalculator.parseLocalizedDouble(attendanceBonusText)
                            val maxAbsentThreshold = (LaborLawCalculator.parseLocalizedInt(maxAbsentForBonusText)).coerceAtLeast(0)
                            val night = LaborLawCalculator.parseLocalizedDouble(nightBillRateText)
                            val tiffin = LaborLawCalculator.parseLocalizedDouble(tiffinBillRateText)
                            val customOt = LaborLawCalculator.parseLocalizedDouble(customOtRateText)

                            val updated = profile.copy(
                                userName = userName.trim(),
                                companyName = companyName.trim(),
                                designation = designation.trim(),
                                workerId = workerId.trim(),
                                grade = grade.trim(),
                                isCustomSalary = isCustomSalary,
                                basicSalary = basic,
                                houseRent = house,
                                medicalAllowance = medical,
                                conveyanceAllowance = conveyance,
                                foodAllowance = food,
                                attendanceBonus = attendance,
                                maxAbsentForBonus = maxAbsentThreshold,
                                maxLeaveForBonus = LaborLawCalculator.parseLocalizedIntOrNull(maxLeaveForBonusText),
                                nightBillRate = night,
                                tiffinBillRate = tiffin,
                                customOtRate = customOt
                            )
                            onSaveProfile(updated)
                            activeHighlightField = null
                            isEditing = false
                            Toast.makeText(context, if (isBn) "প্রোফাইল ও বেতন সেটিংস সংরক্ষিত হয়েছে!" else "Profile & salary settings saved!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(if (isProfileConfigured) 1.4f else 1f)
                            .testTag("save_profile_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isBn) "সকল সেটিংস সংরক্ষণ করুন" else "Save All Settings", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = { isEditing = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBn) "প্রোফাইল ও বেতন সেটিংস পরিবর্তন করুন" else "Edit Profile & Salary Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // Help & Privacy Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(if (isBn) "সেটিংস ও সহায়তা (Settings & Support)" else "Settings & Support", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                    // Recycle Bin / Trash Option
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTrashDialog = true }
                            .testTag("trash_bin_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.errorContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (isBn) "রিসাইকেল বিন (Trash)" else "Recycle Bin (Trash)",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = if (isBn) "ডিলিট করা রেকর্ড ১ দিন জমা থাকে" else "Deleted records are kept for 1 day",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                if (trashItems.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.error
                                    ) {
                                        Text(
                                            text = if (isBn) "${trashItems.size}টি" else "${trashItems.size} items",
                                            color = MaterialTheme.colorScheme.onError,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // WhatsApp Direct Help Button
                    OutlinedButton(
                        onClick = {
                            val whatsappUrl = "https://wa.me/8801988697470"
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, if (isBn) "হোয়াটসঅ্যাপ ওপেন করা যায়নি। লিংক: $whatsappUrl" else "Could not open WhatsApp. Link: $whatsappUrl", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("whatsapp_help_button")
                    ) {
                        Icon(Icons.Default.SupportAgent, contentDescription = null, tint = SuccessGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isBn) "হোয়াটসঅ্যাপে সরাসরি সাহায্য নিন" else "Get Direct WhatsApp Support", color = SuccessGreen, fontWeight = FontWeight.Bold)
                    }

                    // Privacy Policy Button
                    TextButton(
                        onClick = {
                            val privacyUrl = "https://mahmudappsofficial-072.github.io/overtime-tracker-privacy-policy/"
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(
                                    context,
                                    if (isBn) "ব্রাউজার খুলতে সমস্যা হয়েছে" else "Unable to open browser",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("privacy_policy_button")
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isBn) "গোপনীয়তা নীতিমালা (Privacy Policy) দেখুন" else "View Privacy Policy")
                    }
                }
            }
        }
    }

    // Settings In-Screen Sheet
    if (showSettingsDialog) {
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val hideKeyboard = {
            focusManager.clearFocus()
            keyboardController?.hide()
        }

        DisposableEffect(Unit) {
            onDispose {
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        }

        BackHandler(enabled = !showDeleteAllConfirmDialog) {
            hideKeyboard()
            showSettingsDialog = false
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { hideKeyboard() }
                )
                .testTag("settings_sheet"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_app_logo),
                                contentDescription = "App Logo",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = LanguageManager.get("settings", lang),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = {
                            hideKeyboard()
                            showSettingsDialog = false
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isBn) "বন্ধ করুন" else "Close"
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Language Switcher Row
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(if (isBn) "অ্যাপের ভাষা" else "App Language", fontWeight = FontWeight.SemiBold)
                                Text(if (isBn) "বর্তমান: বাংলা" else "Current: English", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(onClick = {
                                hideKeyboard()
                                onToggleLanguage()
                            }) {
                                Text(if (isBn) "Switch to English" else "বাংলায় পরিবর্তন")
                            }
                        }
                    }

                    // Cloud Sync Section (স্বয়ংক্রিয় ক্লাউড ব্যাকআপ)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val syncDateStr = if (profile.lastCloudSyncTime > 0L) {
                                if (isBn) {
                                    val cal = Calendar.getInstance().apply { timeInMillis = profile.lastCloudSyncTime }
                                    val day = String.format(Locale.US, "%02d", cal.get(Calendar.DAY_OF_MONTH))
                                    val monthNamesBn = arrayOf("জানু", "ফেব", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টে", "অক্টো", "নভে", "ডিসে")
                                    val monthStr = monthNamesBn[cal.get(Calendar.MONTH)]
                                    var hour = cal.get(Calendar.HOUR)
                                    if (hour == 0) hour = 12
                                    val hourStr = String.format(Locale.US, "%02d", hour)
                                    val minuteStr = String.format(Locale.US, "%02d", cal.get(Calendar.MINUTE))
                                    val isPm = cal.get(Calendar.AM_PM) == Calendar.PM
                                    val amPmStr = if (isPm) "PM" else "AM"
                                    val bnDay = LaborLawCalculator.toBengaliDigits(day)
                                    val bnTime = LaborLawCalculator.toBengaliDigits("$hourStr:$minuteStr")
                                    "$bnDay $monthStr, $bnTime $amPmStr"
                                } else {
                                    SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date(profile.lastCloudSyncTime))
                                }
                            } else {
                                if (isBn) "সিঙ্ক হয়নি" else "Not Synced"
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = SuccessGreen)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isBn) "স্বয়ংক্রিয় ক্লাউড ব্যাকআপ" else "Auto Cloud Sync",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (profile.lastCloudSyncTime > 0L) SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = syncDateStr,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (profile.lastCloudSyncTime > 0L) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        maxLines = 1
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Delete All Button
                            OutlinedButton(
                                onClick = {
                                    hideKeyboard()
                                    showDeleteAllConfirmDialog = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("delete_all_data_button"),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isBn) "Delete All (সকল ডেটা মুছুন)" else "Delete All Data",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Allowed Leave Days for Attendance Bonus (View Mode vs Edit Mode)
                            var isEditingLeaveLimit by remember { mutableStateOf(false) }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isBn) "হাজিরা বোনাসে ছুটির অনুমোদিত সীমা" else "Allowed Leave Days for Bonus",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (!isEditingLeaveLimit) {
                                        IconButton(
                                            onClick = {
                                                hideKeyboard()
                                                isEditingLeaveLimit = true
                                            },
                                            modifier = Modifier.testTag("edit_leave_limit_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Leave Limit",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                if (!isEditingLeaveLimit) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (profile.maxLeaveForBonus != null && profile.maxLeaveForBonus > 0) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (profile.maxLeaveForBonus != null && profile.maxLeaveForBonus > 0) {
                                                    if (isBn) "সর্বোচ্চ ${LaborLawCalculator.toBengaliDigits(profile.maxLeaveForBonus.toString())} দিন পর্যন্ত ছুটি নেওয়া যাবে"
                                                    else "Allowed up to ${profile.maxLeaveForBonus} leave days"
                                                } else {
                                                    if (isBn) "নির্ধারণ করা নেই (সীমাহীন)" else "Not set (No limit)"
                                                },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (profile.maxLeaveForBonus != null && profile.maxLeaveForBonus > 0) SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            ) {
                                                Text(
                                                    text = if (profile.maxLeaveForBonus != null && profile.maxLeaveForBonus > 0) {
                                                        if (isBn) "বোনাস অক্ষত" else "Bonus Protected"
                                                    } else {
                                                        if (isBn) "সাধারণ" else "Default"
                                                    },
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (profile.maxLeaveForBonus != null && profile.maxLeaveForBonus > 0) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = maxLeaveForBonusText,
                                        onValueChange = { input ->
                                            maxLeaveForBonusText = LaborLawCalculator.normalizeNumericInput(input, allowDecimal = false)
                                        },
                                        placeholder = {
                                            Text(
                                                text = if (isBn) "দিন সংখ্যা (যেমন: ২)" else "Days (e.g. 2)",
                                                fontSize = 12.sp
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("max_leave_bonus_input"),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(onDone = { hideKeyboard() }),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        trailingIcon = {
                                            if (maxLeaveForBonusText.isNotEmpty()) {
                                                IconButton(onClick = { maxLeaveForBonusText = "" }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Clear,
                                                        contentDescription = "Clear",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                hideKeyboard()
                                                maxLeaveForBonusText = profile.maxLeaveForBonus?.toString() ?: ""
                                                isEditingLeaveLimit = false
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(if (isBn) "বাতিল" else "Cancel", fontSize = 12.sp)
                                        }

                                        Button(
                                            onClick = {
                                                hideKeyboard()
                                                val parsedLimit = LaborLawCalculator.parseLocalizedIntOrNull(maxLeaveForBonusText)
                                                val updated = profile.copy(maxLeaveForBonus = parsedLimit)
                                                onSaveProfile(updated)
                                                isEditingLeaveLimit = false
                                                Toast.makeText(
                                                    context,
                                                    if (isBn) "সাময়িক ছুটির সীমা সংরক্ষিত হয়েছে!" else "Leave limit saved!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .testTag("save_leave_limit_button"),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Save,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (isBn) "সংরক্ষণ করুন" else "Save", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Button(
                            onClick = {
                                hideKeyboard()
                                showSettingsDialog = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text(if (isBn) "সম্পন্ন" else "Done")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirmDialog = false },
            shape = RoundedCornerShape(16.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (isBn) "সব ডেটা মুছে ফেলতে চান?" else "Delete All Data?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = if (currentUser != null) {
                        if (isBn)
                            "সতর্কতা: এটি নিশ্চিত করলে আপনার ফোনের লোকাল ডেটাবেজ এবং গুগল ক্লাউড (Firestore)-এর সকল ওভারটাইম, পিস রেট ও বেতনের হিসাব স্থায়ীভাবে মুছে শূন্য (0) হয়ে যাবে। এটি আর ফিরিয়ে আনা সম্ভব হবে না। আপনি কি নিশ্চিত?"
                        else
                            "Warning: Confirming this will permanently delete all your overtime, piece rate, and salary settings from both local device storage and Google Cloud (Firestore). This action cannot be undone. Are you sure?"
                    } else {
                        if (isBn)
                            "সতর্কতা: এটি নিশ্চিত করলে আপনার ফোনের লোকাল ডেটাবেজে সংরক্ষিত সকল ওভারটাইম, পিস রেট ও বেতনের হিসাব স্থায়ীভাবে মুছে শূন্য (0) হয়ে যাবে। এটি আর ফিরিয়ে আনা সম্ভব হবে না। আপনি কি নিশ্চিত?"
                        else
                            "Warning: Confirming this will permanently delete all your local overtime, piece rate, and salary settings. This action cannot be undone. Are you sure?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllConfirmDialog = false
                        showSettingsDialog = false
                        onDeleteAllData { success ->
                            if (success) {
                                syncFormFromProfile(UserProfile())
                                customOtRateText = ""
                                isEditing = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_all_button")
                ) {
                    Text(
                        text = if (isBn) "হ্যাঁ, নিশ্চিত করুন" else "Yes, Delete All",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteAllConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_delete_all_button")
                ) {
                    Text(if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }

    // Offline Sign-Out Blocked Alert Dialog
    if (showOfflineSignOutAlert) {
        AlertDialog(
            onDismissRequest = { showOfflineSignOutAlert = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (isBn) "অফলাইনে লগআউট নিষিদ্ধ" else "Sign Out Blocked Offline",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isBn)
                        "আপনি এখন অফলাইনে আছেন। ইন্টারনেট সংযোগ ছাড়া লগআউট করলে আপনার অফলাইনে রাখা সব হিসাব (ওটি, পিস রেট, কর্তন ও সেটিংস) মুছে যাবে এবং ক্লাউডে ব্যাকআপ হবে না।\n\nনিরাপদে লগআউট করতে অনুগ্রহ করে আগে ইন্টারনেট সংযোগ চালু করুন।"
                    else
                        "You are currently offline. Signing out without internet would erase your unsynced offline records (OT, piece rate, deductions, and settings) without backing them up.\n\nPlease connect to the internet first to safely sign out."
                )
            },
            confirmButton = {
                Button(
                    onClick = { showOfflineSignOutAlert = false },
                    modifier = Modifier.testTag("offline_signout_ok_button")
                ) {
                    Text(if (isBn) "ঠিক আছে" else "OK")
                }
            }
        )
    }

    // Sign Out Confirmation Dialog
    if (showSignOutConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSigningOut) showSignOutConfirmDialog = false
            },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = !isSigningOut,
                dismissOnClickOutside = !isSigningOut
            ),
            title = {
                Text(
                    text = if (isBn) "লগআউট নিশ্চিতকরণ" else "Confirm Sign Out",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isBn)
                            "আপনি কি নিশ্চিত যে আপনার গুগল অ্যাকাউন্ট থেকে লগআউট করতে চান? লগআউট করার আগে সব ডেটা ক্লাউডে সিঙ্ক ও যাচাই করা হবে।"
                        else
                            "Are you sure you want to sign out of your Google account? All data will be synced and verified in the cloud before signing out."
                    )
                    if (!isOnline && !isSigningOut) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isBn)
                                        "ইন্টারনেট সংযোগ বিচ্ছিন্ন! লগআউট করতে ইন্টারনেট চালু করুন।"
                                    else
                                        "No internet connection! Connect to sign out.",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    if (isSigningOut) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isBn) "ক্লাউডে ডেটা সিঙ্ক ও যাচাই হচ্ছে..." else "Syncing & verifying cloud backup...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!com.example.util.NetworkMonitor.isStrictlyOnline(context)) {
                            showSignOutConfirmDialog = false
                            showOfflineSignOutAlert = true
                            return@Button
                        }
                        onSignOut { _, _ ->
                            showSignOutConfirmDialog = false
                        }
                    },
                    enabled = isOnline && !isSigningOut,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_sign_out_button")
                ) {
                    Text(if (isBn) "লগআউট করুন" else "Sign Out")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSignOutConfirmDialog = false },
                    enabled = !isSigningOut
                ) {
                    Text(if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }

    if (showTrashDialog) {
        TrashManagementDialog(
            trashItems = trashItems,
            isBn = isBn,
            onRestore = { item ->
                onRestoreTrash(item)
                Toast.makeText(context, if (isBn) "রেকর্ডটি সফলভাবে রিস্টোর করা হয়েছে!" else "Record restored successfully!", Toast.LENGTH_SHORT).show()
            },
            onDeletePermanently = { item ->
                onDeleteTrashPermanently(item)
                Toast.makeText(context, if (isBn) "রেকর্ডটি স্থায়ীভাবে মুছে ফেলা হয়েছে" else "Record permanently deleted", Toast.LENGTH_SHORT).show()
            },
            onClearAll = {
                onClearAllTrash()
                Toast.makeText(context, if (isBn) "রিসাইকেল বিন সম্পূর্ণ খালি করা হয়েছে" else "Recycle bin emptied completely", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showTrashDialog = false }
        )
    }
    }
}

@Composable
fun TrashManagementDialog(
    trashItems: List<com.example.data.model.TrashItem>,
    isBn: Boolean = true,
    onRestore: (com.example.data.model.TrashItem) -> Unit,
    onDeletePermanently: (com.example.data.model.TrashItem) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var itemToDeletePermanently by remember { mutableStateOf<com.example.data.model.TrashItem?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = itemToDeletePermanently == null && !showClearAllConfirm) {
        onDismiss()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .testTag("trash_management_sheet"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isBn) "রিসাইকেল বিন (Trash)" else "Recycle Bin (Trash)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (trashItems.isNotEmpty()) {
                            TextButton(onClick = { showClearAllConfirm = true }) {
                                Text(if (isBn) "সব মুছুন" else "Empty All", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isBn) "বন্ধ করুন" else "Close"
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isBn) {
                        "ডিলিট করা রেকর্ডগুলো এখানে ১ দিন (২৪ ঘণ্টা) জমা থাকে। এর মধ্যে রিস্টোর না করলে তা স্বয়ংক্রিয়ভাবে চিরতরে মুছে যাবে।"
                    } else {
                        "Deleted records are stored here for 1 day (24 hours). If not restored within this time, they will be permanently deleted."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                if (trashItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isBn) "রিসাইকেল বিন সম্পূর্ণ খালি!" else "Recycle Bin is completely empty!",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isBn) "কোনো ডিলিট করা ওটি বা পিস রেট নেই" else "No deleted overtime or piece rate records",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(trashItems.size, key = { trashItems[it].id }) { index ->
                            val item = trashItems[index]
                            val now = System.currentTimeMillis()
                            val elapsedMillis = now - item.deletedAt
                            val remainingHours = kotlin.math.max(0, 24 - (elapsedMillis / (1000 * 60 * 60)).toInt())

                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val badgeColor = when (item.itemType) {
                                            "OT" -> MaterialTheme.colorScheme.primaryContainer
                                            "PIECE" -> MaterialTheme.colorScheme.secondaryContainer
                                            "ADVANCE" -> MaterialTheme.colorScheme.tertiaryContainer
                                            "APON_BAZAR" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                        val badgeTextColor = when (item.itemType) {
                                            "OT" -> MaterialTheme.colorScheme.onPrimaryContainer
                                            "PIECE" -> MaterialTheme.colorScheme.onSecondaryContainer
                                            "ADVANCE" -> MaterialTheme.colorScheme.onTertiaryContainer
                                            "APON_BAZAR" -> MaterialTheme.colorScheme.onErrorContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        val badgeLabel = when (item.itemType) {
                                            "OT" -> if (isBn) "ওভারটাইম" else "Overtime"
                                            "PIECE" -> if (isBn) "পিস রেট" else "Piece Rate"
                                            "ADVANCE" -> if (isBn) "অগ্রিম কর্তন" else "Advance"
                                            "APON_BAZAR" -> if (isBn) "আপন বাজার" else "Apon Bazar"
                                            else -> item.itemType
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = badgeColor
                                        ) {
                                            Text(
                                                text = badgeLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeTextColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Text(
                                            text = if (isBn) "বাকি: $remainingHours ঘণ্টা" else "$remainingHours hrs left",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    val displayTitle = if (isBn) {
                                        item.title
                                    } else {
                                        item.title
                                            .replace("ওভারটাইম:", "Overtime:")
                                            .replace("পিস রেট:", "Piece Rate:")
                                            .replace("অগ্রিম কর্তন:", "Advance:")
                                            .replace("আপন বাজার:", "Apon Bazar:")
                                    }
                                    val displaySubtitle = if (isBn) item.subtitle else item.subtitle.replace("ঘণ্টা", "hrs").replace("পিস", "pcs")
                                    Text(
                                        text = displayTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = displaySubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { itemToDeletePermanently = item }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteForever,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isBn) "স্থায়ী মুছুন" else "Delete Permanently", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = { onRestore(item) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Restore,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isBn) "রিস্টোর" else "Restore", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(if (isBn) "বন্ধ করুন" else "Close")
                    }
                }
            }
        }
    }

    // Permanent delete confirmation dialog
    if (itemToDeletePermanently != null) {
        AlertDialog(
            onDismissRequest = { itemToDeletePermanently = null },
            title = { Text(if (isBn) "স্থায়ীভাবে মুছে ফেলবেন?" else "Delete permanently?") },
            text = { Text(if (isBn) "এই রেকর্ডটি চিরতরে মুছে ফেলা হবে এবং আর ফিরিয়ে আনা যাবে না।" else "This record will be permanently deleted and cannot be restored.") },
            confirmButton = {
                Button(
                    onClick = {
                        itemToDeletePermanently?.let { onDeletePermanently(it) }
                        itemToDeletePermanently = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isBn) "স্থায়ী মুছুন" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDeletePermanently = null }) {
                    Text(if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }

    // Clear all confirm
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(if (isBn) "রিসাইকেল বিন খালি করবেন?" else "Empty Recycle Bin?") },
            text = { Text(if (isBn) "ট্র্যাশের সকল রেকর্ড চিরতরে মুছে যাবে। আপনি কি নিশ্চিত?" else "All records in trash will be permanently deleted. Are you sure?") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showClearAllConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isBn) "সব মুছুন" else "Empty All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }
}

private fun formatDisplayGrade(grade: String, isBn: Boolean): String {
    val clean = grade.trim()
    if (clean.isBlank()) return ""
    val lower = clean.lowercase()
    val hasGradeWord = lower.contains("grade") || clean.contains("গ্রেড")
    return if (hasGradeWord) {
        if (isBn) clean.replace("Grade", "গ্রেড", ignoreCase = true)
        else clean.replace("গ্রেড", "Grade")
    } else {
        if (isBn) "গ্রেড $clean" else "Grade $clean"
    }
}

@Composable
private fun InfoChipCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SalaryBreakdownRow(
    label: String,
    amount: Double,
    isBn: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = LaborLawCalculator.formatCurrency(amount, isBn),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BenefitChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
    }
}
