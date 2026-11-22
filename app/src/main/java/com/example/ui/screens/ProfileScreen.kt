package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.TrashItem
import com.example.data.model.UserProfile
import com.example.ui.theme.SuccessGreen
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager
import com.example.util.NetworkMonitor
import com.example.util.ResponsiveUtils
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.max
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
    effectiveLang: String = LanguageManager.resolveSupportedTag(profile.language),
    selectedSettingTag: String = LanguageManager.SYSTEM_TAG,
    currentUser: FirebaseUser? = null,
    isAuthLoading: Boolean = false,
    isSigningOut: Boolean = false,
    onSignInWithGoogle: () -> Unit = {},
    onSignOut: ((Boolean, String) -> Unit) -> Unit = { it(false, "") },
    onSaveProfile: (UserProfile) -> Unit,
    onOpenLanguagePicker: () -> Unit = {},
    onSyncCloud: ((Boolean, String) -> Unit) -> Unit = {},
    trashItems: List<TrashItem> = emptyList(),
    onRestoreTrash: (TrashItem) -> Unit = {},
    onDeleteTrashPermanently: (TrashItem) -> Unit = {},
    onClearAllTrash: () -> Unit = {},
    onDeleteAllData: ((Boolean) -> Unit) -> Unit = { it(true) },
    highlightField: String? = null,
    highlightTriggerId: Int = 0,
    onConsumeHighlight: () -> Unit = {},
    onModalSheetVisibilityChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val localizedCtx = remember(context, effectiveLang) {
        LanguageManager.getLocalizedContext(context, effectiveLang)
    }
    val isBnDigits = LanguageManager.isBengaliDigits(effectiveLang)
    val isCompactScreen = ResponsiveUtils.isCompactScreen()
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

            delay(80L)
            try {
                listState.animateScrollToItem(index = 3)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}

            withFrameNanos { }
            delay(50L)
            val requester = when (target) {
                "NIGHT_BILL" -> nightBringIntoViewRequester
                "TIFFIN_BILL" -> tiffinBringIntoViewRequester
                else -> salaryBringIntoViewRequester
            }
            try {
                requester.bringIntoView()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}

            try {
                repeat(3) {
                    if (activeHighlightField != target) return@LaunchedEffect
                    blinkPhaseOn = false
                    delay(160L)
                    if (activeHighlightField != target) return@LaunchedEffect
                    blinkPhaseOn = true
                    delay(200L)
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
    val isOnline by NetworkMonitor.rememberIsOnline()

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
                        text = stringResource(R.string.profile_header_title),
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
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit_profile))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        FilledTonalIconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings))
                        }
                    }
                }
            }

            // Account Card (Google Authentication)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("account_card")
                ) {
                    if (currentUser == null) {
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
                                        text = stringResource(R.string.google_account_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.signed_out_label),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, Color(0xFFDADCE0)),
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
                                            text = stringResource(R.string.signing_in_label),
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
                                            text = stringResource(R.string.sign_in_with_google),
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    } else {
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
                                    AsyncImage(
                                        model = currentUser.photoUrl,
                                        contentDescription = stringResource(R.string.cd_profile_photo),
                                        contentScale = ContentScale.Crop,
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
                                        text = currentUser.displayName ?: stringResource(R.string.google_user_fallback),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentUser.email ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = SuccessGreen.copy(alpha = 0.12f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.active_account_badge),
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
                                    if (NetworkMonitor.isStrictlyOnline(context)) {
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
                                        contentDescription = stringResource(R.string.cd_offline),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = stringResource(R.string.sign_out_btn),
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
                                        text = userName.ifBlank { stringResource(R.string.your_name_placeholder) },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${companyName.ifBlank { stringResource(R.string.company_placeholder) }} • ${designation.ifBlank { stringResource(R.string.designation_placeholder) }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val displayGrade = LanguageManager.formatDisplayGrade(context, grade, effectiveLang)
                                        .ifBlank { stringResource(R.string.grade_fallback_label) }
                                    Text(
                                        text = "${stringResource(R.string.id_short_label)}: ${workerId.ifBlank { stringResource(R.string.not_applicable) }} • $displayGrade",
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
                                        contentDescription = stringResource(R.string.cd_edit_profile),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        if (isEditing) {
                            OutlinedTextField(
                                value = userName,
                                onValueChange = { userName = it },
                                label = { Text(stringResource(R.string.worker_name_label)) },
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
                                        label = { Text(stringResource(R.string.company_factory_label)) },
                                        modifier = Modifier.fillMaxWidth().testTag("profile_company_input"),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = designation,
                                        onValueChange = { designation = it },
                                        label = { Text(stringResource(R.string.designation_label)) },
                                        modifier = Modifier.fillMaxWidth().testTag("profile_designation_input"),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = workerId,
                                        onValueChange = { workerId = it },
                                        label = { Text(stringResource(R.string.worker_id_label)) },
                                        modifier = Modifier.fillMaxWidth().testTag("profile_id_input"),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = grade,
                                        onValueChange = { grade = it },
                                        label = { Text(stringResource(R.string.grade_label)) },
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
                                        label = { Text(stringResource(R.string.company_factory_label)) },
                                        modifier = Modifier.weight(1f).testTag("profile_company_input"),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = designation,
                                        onValueChange = { designation = it },
                                        label = { Text(stringResource(R.string.designation_label)) },
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
                                        label = { Text(stringResource(R.string.worker_id_label)) },
                                        modifier = Modifier.weight(1f).testTag("profile_id_input"),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = grade,
                                        onValueChange = { grade = it },
                                        label = { Text(stringResource(R.string.grade_label)) },
                                        modifier = Modifier.weight(1f).testTag("profile_grade_input"),
                                        singleLine = true
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val notSetStr = stringResource(R.string.not_set)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    InfoChipCard(
                                        icon = Icons.Default.Business,
                                        label = stringResource(R.string.company_factory_label),
                                        value = companyName.ifBlank { notSetStr },
                                        modifier = Modifier.weight(1f)
                                    )
                                    InfoChipCard(
                                        icon = Icons.Default.Work,
                                        label = stringResource(R.string.designation_label),
                                        value = designation.ifBlank { notSetStr },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    InfoChipCard(
                                        icon = Icons.Default.Badge,
                                        label = stringResource(R.string.worker_id_label),
                                        value = workerId.ifBlank { notSetStr },
                                        modifier = Modifier.weight(1f)
                                    )
                                    InfoChipCard(
                                        icon = Icons.Default.Stars,
                                        label = stringResource(R.string.grade_label),
                                        value = LanguageManager.formatDisplayGrade(context, grade, effectiveLang).ifBlank { notSetStr },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Salary Setup Mode Switcher / Summary Card
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
                                text = stringResource(R.string.salary_structure_title),
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
                                            text = stringResource(R.string.mode_auto_tab),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
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
                                            text = stringResource(R.string.mode_custom_tab),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
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
                                        text = stringResource(R.string.mode_auto_note),
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
                                        text = stringResource(R.string.mode_custom_note),
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            if (!isCustomSalary) {
                                OutlinedTextField(
                                    value = grossSalaryText,
                                    onValueChange = {
                                        val normalized = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                                        grossSalaryText = normalized
                                        if (normalized.isNotEmpty() && activeHighlightField == "SALARY") {
                                            activeHighlightField = null
                                        }
                                    },
                                    label = { Text(stringResource(R.string.gross_salary_input_label)) },
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
                                OutlinedTextField(
                                    value = basicSalaryText,
                                    onValueChange = {
                                        val normalized = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true)
                                        basicSalaryText = normalized
                                        if (normalized.isNotEmpty() && activeHighlightField == "SALARY") {
                                            activeHighlightField = null
                                        }
                                    },
                                    label = { Text(stringResource(R.string.total_salary_input_label)) },
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

                            // Allowances Section (Only in Automatic mode)
                            if (!isCustomSalary) {
                                Text(
                                    text = stringResource(R.string.auto_breakdown_locked_title),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )

                                OutlinedTextField(
                                    value = autoResult.basicSalary.roundToInt().toString(),
                                    onValueChange = {},
                                    label = { Text(stringResource(R.string.basic_salary_locked_label)) },
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
                                            label = { Text(stringResource(R.string.house_rent_label)) },
                                            enabled = false,
                                            modifier = Modifier.fillMaxWidth().testTag("house_rent_input"),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = "750",
                                            onValueChange = {},
                                            label = { Text(stringResource(R.string.medical_allowance_fixed_750)) },
                                            enabled = false,
                                            modifier = Modifier.fillMaxWidth().testTag("medical_input"),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = autoResult.conveyanceAllowance.roundToInt().toString(),
                                            onValueChange = {},
                                            label = { Text(stringResource(R.string.conveyance_allowance_label)) },
                                            enabled = false,
                                            modifier = Modifier.fillMaxWidth().testTag("conveyance_input"),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = autoResult.foodAllowance.roundToInt().toString(),
                                            onValueChange = {},
                                            label = { Text(stringResource(R.string.food_allowance_label)) },
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
                                            label = { Text(stringResource(R.string.house_rent_label)) },
                                            enabled = false,
                                            modifier = Modifier.weight(1f).testTag("house_rent_input"),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = "750",
                                            onValueChange = {},
                                            label = { Text(stringResource(R.string.medical_allowance_fixed_750)) },
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
                                            label = { Text(stringResource(R.string.conveyance_allowance_label)) },
                                            enabled = false,
                                            modifier = Modifier.weight(1f).testTag("conveyance_input"),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = autoResult.foodAllowance.roundToInt().toString(),
                                            onValueChange = {},
                                            label = { Text(stringResource(R.string.food_allowance_label)) },
                                            enabled = false,
                                            modifier = Modifier.weight(1f).testTag("food_input"),
                                            singleLine = true
                                        )
                                    }
                                }
                            }

                            // Night & Tiffin Rates
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
                                        label = { Text(stringResource(R.string.night_bill_rate_label)) },
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
                                        label = { Text(stringResource(R.string.tiffin_bill_rate_label)) },
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
                                        label = { Text(stringResource(R.string.night_bill_rate_label)) },
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
                                        label = { Text(stringResource(R.string.tiffin_bill_rate_label)) },
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
                                label = { Text(stringResource(R.string.attendance_bonus_label)) },
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
                                label = { Text(stringResource(R.string.bonus_absent_threshold_label)) },
                                placeholder = { Text("0") },
                                modifier = Modifier.fillMaxWidth().testTag("max_absent_bonus_input"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                supportingText = {
                                    Text(
                                        text = stringResource(R.string.bonus_absent_threshold_hint),
                                        fontSize = 11.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )

                            if (isCustomSalary) {
                                OutlinedTextField(
                                    value = customOtRateText,
                                    onValueChange = { customOtRateText = LaborLawCalculator.normalizeNumericInput(it, allowDecimal = true) },
                                    label = { Text(stringResource(R.string.custom_ot_rate_label)) },
                                    placeholder = { Text("0") },
                                    modifier = Modifier.fillMaxWidth().testTag("custom_ot_rate_input"),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    supportingText = {
                                        Text(stringResource(R.string.custom_ot_rate_hint))
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
                                            text = stringResource(R.string.effective_ot_rate_label),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = if (effectiveHourlyOtRate != null && effectiveHourlyOtRate > 0.0) {
                                                LaborLawCalculator.formatCurrency(effectiveHourlyOtRate, isBnDigits) +
                                                    stringResource(R.string.currency_per_hour_suffix)
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
                                            text = stringResource(R.string.total_regular_salary_label),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        val calculatedGross = if (isCustomSalary) {
                                            LaborLawCalculator.parseLocalizedDouble(basicSalaryText)
                                        } else {
                                            autoResult.grossSalary
                                        }
                                        Text(
                                            text = LaborLawCalculator.formatCurrency(calculatedGross, isBnDigits),
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
                                    text = stringResource(R.string.salary_allowances_view_title),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (!isCustomSalary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = if (!isCustomSalary) {
                                            stringResource(R.string.badge_labor_law)
                                        } else {
                                            stringResource(R.string.badge_custom_rules)
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
                                            text = stringResource(R.string.total_regular_salary_label),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        val displayGross = if (isCustomSalary) {
                                            LaborLawCalculator.parseLocalizedDouble(basicSalaryText)
                                        } else {
                                            autoResult.grossSalary
                                        }
                                        Text(
                                            text = LaborLawCalculator.formatCurrency(displayGross, isBnDigits),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = stringResource(R.string.effective_ot_rate_label),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = if (effectiveHourlyOtRate != null && effectiveHourlyOtRate > 0.0) {
                                                LaborLawCalculator.formatCurrency(effectiveHourlyOtRate, isBnDigits) +
                                                    stringResource(R.string.currency_per_hour_suffix)
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
                                    text = stringResource(R.string.allowances_breakdown_view_title),
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
                                        SalaryBreakdownRow(stringResource(R.string.row_basic_salary), autoResult.basicSalary, isBnDigits)
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                        SalaryBreakdownRow(stringResource(R.string.row_house_rent), autoResult.houseRent, isBnDigits)
                                        SalaryBreakdownRow(stringResource(R.string.row_medical_allowance), 750.0, isBnDigits)
                                        SalaryBreakdownRow(stringResource(R.string.row_conveyance), autoResult.conveyanceAllowance, isBnDigits)
                                        SalaryBreakdownRow(stringResource(R.string.row_food_allowance), autoResult.foodAllowance, isBnDigits)
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
                                    text = stringResource(R.string.other_allowances_bills_title),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (nightVal > 0.0) {
                                        BenefitChip(
                                            label = stringResource(R.string.night_bill_label),
                                            value = LaborLawCalculator.formatCurrency(nightVal, isBnDigits) +
                                                stringResource(R.string.currency_per_night_suffix),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (tiffinVal > 0.0) {
                                        BenefitChip(
                                            label = stringResource(R.string.tiffin_bill_label),
                                            value = LaborLawCalculator.formatCurrency(tiffinVal, isBnDigits) +
                                                stringResource(R.string.currency_per_day_suffix),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                if (bonusVal > 0.0) {
                                    val absentDaysNum = LaborLawCalculator.parseLocalizedInt(maxAbsentForBonusText).coerceAtLeast(0)
                                    val absentDaysStr = LaborLawCalculator.formatNumber(absentDaysNum.toDouble(), isBnDigits, 0)
                                    val leaveLimitPart = profile.maxLeaveForBonus?.takeIf { it > 0 }?.let { limit ->
                                        stringResource(
                                            R.string.leave_limit_inline_fmt,
                                            LaborLawCalculator.formatNumber(limit.toDouble(), isBnDigits, 0)
                                        )
                                    } ?: ""
                                    BenefitChip(
                                        label = stringResource(R.string.attendance_bonus),
                                        value = "${LaborLawCalculator.formatCurrency(bonusVal, isBnDigits)} " +
                                            stringResource(R.string.bonus_limit_summary_fmt, absentDaysStr, leaveLimitPart),
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
                                Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
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
                                val maxAbsentThreshold = LaborLawCalculator.parseLocalizedInt(maxAbsentForBonusText).coerceAtLeast(0)
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
                                Toast.makeText(
                                    context,
                                    localizedCtx.getString(R.string.profile_saved_toast),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .weight(if (isProfileConfigured) 1.4f else 1f)
                                .testTag("save_profile_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.save_all_settings), fontWeight = FontWeight.Bold)
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
                            text = stringResource(R.string.edit_profile_salary_settings),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // Settings & Support Card
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
                        Text(
                            text = stringResource(R.string.settings_support_title),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )

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
                                            text = stringResource(R.string.trash_bin_title),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = stringResource(R.string.trash_bin_subtitle),
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
                                                text = stringResource(R.string.items_count_fmt, trashItems.size),
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
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
                                } catch (_: Exception) {
                                    Toast.makeText(
                                        context,
                                        localizedCtx.getString(R.string.whatsapp_error_fmt, whatsappUrl),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("whatsapp_help_button")
                        ) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, tint = SuccessGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.whatsapp_support_btn),
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Privacy Policy Button
                        TextButton(
                            onClick = {
                                val privacyUrl = "https://mahmudappsofficial-072.github.io/overtime-tracker-privacy-policy/"
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(
                                        context,
                                        localizedCtx.getString(R.string.browser_open_error),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("privacy_policy_button")
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.view_privacy_policy))
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
                                    painter = painterResource(id = R.drawable.ic_app_logo),
                                    contentDescription = stringResource(R.string.cd_app_logo),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.cd_settings),
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
                                    contentDescription = stringResource(R.string.close)
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
                        // Language Picker Row
                        val currentLangDisplay = if (selectedSettingTag == LanguageManager.SYSTEM_TAG) {
                            "${stringResource(R.string.system_default_title)} (${LanguageManager.getDisplayName(LanguageManager.SYSTEM_TAG, effectiveLang)})"
                        } else {
                            LanguageManager.getDisplayName(effectiveLang, effectiveLang)
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    hideKeyboard()
                                    onOpenLanguagePicker()
                                }
                                .testTag("settings_language_row")
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
                                    Text(
                                        text = stringResource(R.string.app_language_label),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(R.string.current_language_fmt, currentLangDisplay),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(onClick = {
                                    hideKeyboard()
                                    onOpenLanguagePicker()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.switch_language_btn))
                                }
                            }
                        }

                        // Cloud Sync Section
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val syncDateStr = LanguageManager.formatSyncTimestamp(
                                    context,
                                    profile.lastCloudSyncTime,
                                    effectiveLang
                                )

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
                                            text = stringResource(R.string.auto_cloud_sync_title),
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
                                    border = BorderStroke(
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
                                        text = stringResource(R.string.delete_all_data_btn),
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )

                                // Allowed Leave Days for Attendance Bonus
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
                                                text = stringResource(R.string.allowed_leave_days_title),
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
                                                    contentDescription = stringResource(R.string.cd_edit_leave_limit),
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
                                                        stringResource(
                                                            R.string.leave_limit_allowed_fmt,
                                                            LaborLawCalculator.formatNumber(profile.maxLeaveForBonus.toDouble(), isBnDigits, 0)
                                                        )
                                                    } else {
                                                        stringResource(R.string.leave_limit_not_set)
                                                    },
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (profile.maxLeaveForBonus != null && profile.maxLeaveForBonus > 0) {
                                                        SuccessGreen.copy(alpha = 0.15f)
                                                    } else {
                                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                    }
                                                ) {
                                                    Text(
                                                        text = if (profile.maxLeaveForBonus != null && profile.maxLeaveForBonus > 0) {
                                                            stringResource(R.string.badge_bonus_protected)
                                                        } else {
                                                            stringResource(R.string.badge_default)
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
                                                    text = stringResource(R.string.leave_days_placeholder),
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
                                                            contentDescription = stringResource(R.string.clear),
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
                                                Text(stringResource(R.string.cancel), fontSize = 12.sp)
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
                                                        localizedCtx.getString(R.string.leave_limit_saved_toast),
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
                                                Text(stringResource(R.string.save), fontSize = 12.sp)
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
                                Text(stringResource(R.string.done))
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
                        text = stringResource(R.string.delete_all_dialog_title),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Text(
                        text = if (currentUser != null) {
                            stringResource(R.string.delete_all_dialog_msg_cloud)
                        } else {
                            stringResource(R.string.delete_all_dialog_msg_local)
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
                            text = stringResource(R.string.delete_all_confirm_btn),
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
                        Text(stringResource(R.string.cancel))
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
                        text = stringResource(R.string.offline_signout_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(text = stringResource(R.string.offline_signout_msg))
                },
                confirmButton = {
                    Button(
                        onClick = { showOfflineSignOutAlert = false },
                        modifier = Modifier.testTag("offline_signout_ok_button")
                    ) {
                        Text(stringResource(R.string.ok))
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
                properties = DialogProperties(
                    dismissOnBackPress = !isSigningOut,
                    dismissOnClickOutside = !isSigningOut
                ),
                title = {
                    Text(
                        text = stringResource(R.string.confirm_signout_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = stringResource(R.string.confirm_signout_msg))
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
                                        text = stringResource(R.string.no_internet_signout_warning),
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
                                    text = stringResource(R.string.syncing_verifying_cloud),
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
                            if (!NetworkMonitor.isStrictlyOnline(context)) {
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
                        Text(stringResource(R.string.signout_action_btn))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showSignOutConfirmDialog = false },
                        enabled = !isSigningOut
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showTrashDialog) {
            TrashManagementDialog(
                trashItems = trashItems,
                effectiveLang = effectiveLang,
                onRestore = { item ->
                    onRestoreTrash(item)
                    Toast.makeText(
                        context,
                        localizedCtx.getString(R.string.trash_restored_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onDeletePermanently = { item ->
                    onDeleteTrashPermanently(item)
                    Toast.makeText(
                        context,
                        localizedCtx.getString(R.string.trash_deleted_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onClearAll = {
                    onClearAllTrash()
                    Toast.makeText(
                        context,
                        localizedCtx.getString(R.string.trash_emptied_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onDismiss = { showTrashDialog = false }
            )
        }
    }
}

@Composable
fun TrashManagementDialog(
    trashItems: List<TrashItem>,
    effectiveLang: String = "en",
    onRestore: (TrashItem) -> Unit,
    onDeletePermanently: (TrashItem) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val isBnDigits = LanguageManager.isBengaliDigits(effectiveLang)
    var itemToDeletePermanently by remember { mutableStateOf<TrashItem?>(null) }
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
                            text = stringResource(R.string.trash_bin_title),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (trashItems.isNotEmpty()) {
                            TextButton(onClick = { showClearAllConfirm = true }) {
                                Text(
                                    text = stringResource(R.string.empty_all),
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.close)
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
                    text = stringResource(R.string.trash_description),
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
                                text = stringResource(R.string.trash_empty_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.trash_empty_subtitle),
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
                            val remainingHours = max(0, 24 - (elapsedMillis / (1000 * 60 * 60)).toInt())

                            val decodedOt = remember(item.id, item.jsonData) { item.toOvertimeRecord() }
                            val decodedPiece = remember(item.id, item.jsonData) { item.toPieceRateRecord() }
                            val decodedDeduction = remember(item.id, item.jsonData) { item.toDeductionRecord() }

                            val displayTitle = when {
                                decodedOt != null -> stringResource(R.string.trash_ot_title_fmt, decodedOt.date)
                                decodedPiece != null -> stringResource(R.string.trash_piece_title_fmt, decodedPiece.itemName)
                                decodedDeduction != null -> stringResource(
                                    if (decodedDeduction.type == "APON_BAZAR") R.string.trash_apon_title_fmt
                                    else R.string.trash_advance_title_fmt,
                                    decodedDeduction.date
                                )
                                else -> item.title
                            }

                            val displaySubtitle = when {
                                decodedOt != null -> stringResource(
                                    R.string.trash_ot_subtitle_fmt,
                                    LaborLawCalculator.formatNumber(decodedOt.otHours, isBnDigits),
                                    LaborLawCalculator.formatCurrency(decodedOt.calculatedAmount, isBnDigits)
                                )
                                decodedPiece != null -> stringResource(
                                    R.string.trash_piece_subtitle_fmt,
                                    decodedPiece.date,
                                    LaborLawCalculator.formatNumber(decodedPiece.pieceCount.toDouble(), isBnDigits, 0),
                                    LaborLawCalculator.formatCurrency(decodedPiece.totalAmount, isBnDigits)
                                )
                                decodedDeduction != null -> {
                                    val amtStr = LaborLawCalculator.formatCurrency(decodedDeduction.amount, isBnDigits)
                                    if (decodedDeduction.description.isNotBlank()) "$amtStr • ${decodedDeduction.description}" else amtStr
                                }
                                else -> item.subtitle
                            }

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
                                            "OT" -> stringResource(R.string.trash_type_ot)
                                            "PIECE" -> stringResource(R.string.trash_type_piece)
                                            "ADVANCE" -> stringResource(R.string.trash_type_advance)
                                            "APON_BAZAR" -> stringResource(R.string.trash_type_apon)
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
                                            text = stringResource(R.string.trash_hours_left_fmt, remainingHours),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
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
                                            Text(
                                                text = stringResource(R.string.delete_permanently),
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 12.sp
                                            )
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
                                            Text(stringResource(R.string.restore), fontSize = 12.sp)
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
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }

    // Permanent delete confirmation dialog
    if (itemToDeletePermanently != null) {
        AlertDialog(
            onDismissRequest = { itemToDeletePermanently = null },
            title = { Text(stringResource(R.string.trash_delete_perm_title)) },
            text = { Text(stringResource(R.string.trash_delete_perm_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        itemToDeletePermanently?.let { onDeletePermanently(it) }
                        itemToDeletePermanently = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete_permanently))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDeletePermanently = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Clear all confirm
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(stringResource(R.string.trash_empty_confirm_title)) },
            text = { Text(stringResource(R.string.trash_empty_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showClearAllConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.empty_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun InfoChipCard(
    icon: ImageVector,
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
    isBnDigits: Boolean
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
            text = LaborLawCalculator.formatCurrency(amount, isBnDigits),
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
                textAlign = TextAlign.End
            )
        }
    }
}
