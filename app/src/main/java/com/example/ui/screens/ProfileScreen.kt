package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.TrashItem
import com.example.data.model.UserProfile
import com.example.ui.components.AutoFitSingleLineText
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager
import com.example.util.NetworkMonitor
import com.example.util.ResponsiveUtils
import com.google.firebase.auth.FirebaseUser

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
    val spacing = ResponsiveUtils.rememberAppSpacing()
    val isOnline by NetworkMonitor.rememberIsOnline()

    var showEditIdentitySheet by remember { mutableStateOf(false) }
    var showSalarySheet by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showTrashDialog by remember { mutableStateOf(false) }
    var showDeleteAllConfirmDialog by remember { mutableStateOf(false) }
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }
    var showOfflineSignOutAlert by remember { mutableStateOf(false) }

    var pendingSalaryHighlight by rememberSaveable { mutableStateOf<String?>(null) }
    var lastHandledTriggerId by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(highlightTriggerId) {
        if (highlightTriggerId > 0 && highlightTriggerId != lastHandledTriggerId) {
            lastHandledTriggerId = highlightTriggerId
            pendingSalaryHighlight = highlightField ?: "SALARY"
            onConsumeHighlight()
            showEditIdentitySheet = false
            showSettingsDialog = false
            showTrashDialog = false
            showSalarySheet = true
        }
    }

    // Only true window-level AlertDialogs overlap bottomBar AdmobBanner
    val hasBlockingWindowDialog = showDeleteAllConfirmDialog || showSignOutConfirmDialog || showOfflineSignOutAlert
    LaunchedEffect(hasBlockingWindowDialog) {
        onModalSheetVisibilityChanged(hasBlockingWindowDialog)
    }
    DisposableEffect(Unit) {
        onDispose { onModalSheetVisibilityChanged(false) }
    }

    val autoResult = remember(profile.basicSalary, profile.isCustomSalary) {
        if (profile.isCustomSalary) {
            LaborLawCalculator.calculateFromGrossSalary(0.0)
        } else {
            val gross = if (profile.basicSalary > 0.0) {
                LaborLawCalculator.calculateAutoAllowances(profile.basicSalary).grossSalary
            } else 0.0
            LaborLawCalculator.calculateFromGrossSalary(gross)
        }
    }
    val displayGross = if (profile.isCustomSalary) profile.basicSalary else autoResult.grossSalary
    val effectiveHourlyOtRate: Double? = if (profile.isCustomSalary) {
        profile.customOtRate.takeIf { it > 0.0 }
    } else {
        autoResult.otHourlyRate.takeIf { it > 0.0 }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("profile_screen"),
            contentPadding = spacing.screenContentNoFab,
            verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)
        ) {
            // Top Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AutoFitSingleLineText(
                        text = stringResource(R.string.tab_profile),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxFontSize = spacing.adaptiveSp(22f, 20f, 18f),
                        minFontSize = 14.sp,
                        modifier = Modifier.weight(1f).padding(end = spacing.rowGapMd)
                    )
                    FilledTonalIconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cd_settings),
                            modifier = Modifier.size(spacing.iconLg)
                        )
                    }
                }
            }

            // Card 1: Worker Identity Hero Card
            item {
                Card(
                    shape = RoundedCornerShape(spacing.heroRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(spacing.cardInnerGap)
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
                                if (!currentUser?.photoUrl?.toString().isNullOrBlank()) {
                                    AsyncImage(
                                        model = currentUser?.photoUrl,
                                        contentDescription = stringResource(R.string.cd_profile_photo),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(spacing.avatarSize).clip(CircleShape)
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(spacing.avatarSize)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(spacing.iconLg)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(spacing.rowGapMd))
                                Column {
                                    AutoFitSingleLineText(
                                        text = profile.userName.ifBlank { stringResource(R.string.your_name_placeholder) },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxFontSize = spacing.adaptiveSp(17.5f, 15.5f, 14f),
                                        minFontSize = 11.sp
                                    )
                                    AutoFitSingleLineText(
                                        text = "${profile.companyName.ifBlank { stringResource(R.string.company_placeholder) }} • ${profile.designation.ifBlank { stringResource(R.string.designation_placeholder) }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxFontSize = spacing.adaptiveSp(13.5f, 12f, 11f),
                                        minFontSize = 8.5.sp
                                    )
                                    val displayGrade = LanguageManager.formatDisplayGrade(context, profile.grade, effectiveLang)
                                        .ifBlank { stringResource(R.string.grade_fallback_label) }
                                    AutoFitSingleLineText(
                                        text = "${stringResource(R.string.id_short_label)}: ${profile.workerId.ifBlank { stringResource(R.string.not_applicable) }} • $displayGrade",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxFontSize = spacing.adaptiveSp(12.5f, 11f, 10f),
                                        minFontSize = 8.sp
                                    )
                                }
                            }
                            FilledTonalIconButton(
                                onClick = { showEditIdentitySheet = true },
                                modifier = Modifier.testTag("worker_card_edit_button")
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.cd_edit_profile),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(spacing.iconMd)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Column(verticalArrangement = Arrangement.spacedBy(spacing.gridGap)) {
                            val notSetStr = stringResource(R.string.not_set)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)
                            ) {
                                InfoChipCard(
                                    icon = Icons.Default.Business,
                                    label = stringResource(R.string.company_factory_label),
                                    value = profile.companyName.ifBlank { notSetStr },
                                    modifier = Modifier.weight(1f)
                                )
                                InfoChipCard(
                                    icon = Icons.Default.Work,
                                    label = stringResource(R.string.designation_label),
                                    value = profile.designation.ifBlank { notSetStr },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)
                            ) {
                                InfoChipCard(
                                    icon = Icons.Default.Badge,
                                    label = stringResource(R.string.worker_id_label),
                                    value = profile.workerId.ifBlank { notSetStr },
                                    modifier = Modifier.weight(1f)
                                )
                                InfoChipCard(
                                    icon = Icons.Default.Stars,
                                    label = stringResource(R.string.grade_label),
                                    value = LanguageManager.formatDisplayGrade(context, profile.grade, effectiveLang).ifBlank { notSetStr },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Card 2: Salary & OT Summary / Guided Onboarding Card (Opens SalaryStructureSheet)
            item {
                val isSalaryConfigured = displayGross > 0.0 || (effectiveHourlyOtRate ?: 0.0) > 0.0
                Card(
                    shape = RoundedCornerShape(spacing.heroRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("salary_summary_card")
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(spacing.cardInnerGap)
                    ) {
                        if (!isSalaryConfigured) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.salary_setup_card_title),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxFontSize = spacing.adaptiveSp(17.5f, 15.5f, 14f),
                                    minFontSize = 11.sp,
                                    modifier = Modifier.weight(1f).padding(end = spacing.rowGapSm)
                                )
                                Surface(
                                    shape = RoundedCornerShape(spacing.badgeRadius),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    AutoFitSingleLineText(
                                        text = stringResource(R.string.badge_setup_pending),
                                        maxFontSize = spacing.adaptiveSp(12f, 11f, 10f),
                                        minFontSize = 8.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.salary_setup_intro),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Surface(
                                shape = RoundedCornerShape(spacing.cardRadius),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(spacing.subCardPadding),
                                    verticalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
                                ) {
                                    OnboardingStepRow(
                                        stepNumber = 1,
                                        title = stringResource(R.string.salary_guide_step_1_title),
                                        subtitle = stringResource(R.string.salary_guide_step_1_desc),
                                        isBnDigits = isBnDigits
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    OnboardingStepRow(
                                        stepNumber = 2,
                                        title = stringResource(R.string.salary_guide_step_2_title),
                                        subtitle = stringResource(R.string.salary_guide_step_2_desc),
                                        isBnDigits = isBnDigits
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    OnboardingStepRow(
                                        stepNumber = 3,
                                        title = stringResource(R.string.salary_guide_step_3_title),
                                        subtitle = stringResource(R.string.salary_guide_step_3_desc),
                                        isBnDigits = isBnDigits
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    pendingSalaryHighlight = null
                                    showSalarySheet = true
                                },
                                contentPadding = spacing.compactButtonContentPadding,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = spacing.minTouchTarget)
                                    .testTag("edit_profile_button"),
                                shape = RoundedCornerShape(spacing.cardRadius)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(spacing.iconMd))
                                Spacer(modifier = Modifier.width(spacing.rowGapSm))
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.start_salary_setup_btn),
                                    fontWeight = FontWeight.Bold,
                                    maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                                    minFontSize = 10.sp
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.salary_allowances_view_title),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxFontSize = spacing.adaptiveSp(17.5f, 15.5f, 14f),
                                    minFontSize = 11.sp,
                                    modifier = Modifier.weight(1f).padding(end = spacing.rowGapSm)
                                )
                                Surface(
                                    shape = RoundedCornerShape(spacing.badgeRadius),
                                    color = if (!profile.isCustomSalary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    AutoFitSingleLineText(
                                        text = stringResource(
                                            if (!profile.isCustomSalary) R.string.badge_labor_law else R.string.badge_custom_rules
                                        ),
                                        maxFontSize = spacing.adaptiveSp(12f, 11f, 10f),
                                        minFontSize = 8.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (!profile.isCustomSalary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                                    )
                                }
                            }

                            // Highlight Metrics (Gross & OT Rate)
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(spacing.cardRadius),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(spacing.cardPadding),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = spacing.rowGapSm)) {
                                        AutoFitSingleLineText(
                                            text = stringResource(R.string.total_regular_salary_label),
                                            maxFontSize = spacing.adaptiveSp(13f, 11.5f, 10.5f),
                                            minFontSize = 8.5.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        AutoFitSingleLineText(
                                            text = LaborLawCalculator.formatCurrency(displayGross, isBnDigits),
                                            maxFontSize = spacing.adaptiveSp(21f, 19f, 16.5f),
                                            minFontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                        AutoFitSingleLineText(
                                            text = stringResource(R.string.effective_ot_rate_label),
                                            maxFontSize = spacing.adaptiveSp(13f, 11.5f, 10.5f),
                                            minFontSize = 8.5.sp,
                                            textAlign = TextAlign.End,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        AutoFitSingleLineText(
                                            text = if (effectiveHourlyOtRate != null && effectiveHourlyOtRate > 0.0) {
                                                LaborLawCalculator.formatCurrency(effectiveHourlyOtRate, isBnDigits) +
                                                    stringResource(R.string.currency_per_hour_suffix)
                                            } else {
                                                "—"
                                            },
                                            maxFontSize = spacing.adaptiveSp(19f, 17f, 15f),
                                            minFontSize = 11.sp,
                                            textAlign = TextAlign.End,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            if (!profile.isCustomSalary && autoResult.grossSalary > 0.0) {
                                Surface(
                                    shape = RoundedCornerShape(spacing.subCardRadius),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(spacing.subCardPadding),
                                        verticalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
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

                            // Benefits & Bonus Summary Chips
                            val hasBenefits = profile.attendanceBonus > 0.0 || profile.nightBillRate > 0.0 || profile.tiffinBillRate > 0.0
                            if (hasBenefits) {
                                if (profile.nightBillRate > 0.0 || profile.tiffinBillRate > 0.0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(spacing.gridGap)
                                    ) {
                                        if (profile.nightBillRate > 0.0) {
                                            BenefitChip(
                                                label = stringResource(R.string.night_bill_label),
                                                value = LaborLawCalculator.formatCurrency(profile.nightBillRate, isBnDigits) +
                                                    stringResource(R.string.currency_per_night_suffix),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (profile.tiffinBillRate > 0.0) {
                                            BenefitChip(
                                                label = stringResource(R.string.tiffin_bill_label),
                                                value = LaborLawCalculator.formatCurrency(profile.tiffinBillRate, isBnDigits) +
                                                    stringResource(R.string.currency_per_day_suffix),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                                if (profile.attendanceBonus > 0.0) {
                                    val absentDaysStr = LaborLawCalculator.formatNumber(profile.maxAbsentForBonus.coerceAtLeast(0).toDouble(), isBnDigits, 0)
                                    val leaveLimitPart = profile.maxLeaveForBonus?.takeIf { it > 0 }?.let { limit ->
                                        stringResource(
                                            R.string.leave_limit_inline_fmt,
                                            LaborLawCalculator.formatNumber(limit.toDouble(), isBnDigits, 0)
                                        )
                                    } ?: ""
                                    BenefitChip(
                                        label = stringResource(R.string.attendance_bonus),
                                        value = "${LaborLawCalculator.formatCurrency(profile.attendanceBonus, isBnDigits)} " +
                                            stringResource(R.string.bonus_limit_summary_fmt, absentDaysStr, leaveLimitPart),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    pendingSalaryHighlight = null
                                    showSalarySheet = true
                                },
                                contentPadding = spacing.compactButtonContentPadding,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = spacing.minTouchTarget)
                                    .testTag("edit_profile_button"),
                                shape = RoundedCornerShape(spacing.cardRadius)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(spacing.iconMd))
                                Spacer(modifier = Modifier.width(spacing.rowGapSm))
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.setup_edit_salary_btn),
                                    fontWeight = FontWeight.Bold,
                                    maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                                    minFontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sub-Sheet 1: Edit Personal Identity
        if (showEditIdentitySheet) {
            EditIdentitySheet(
                profile = profile,
                onDismiss = { showEditIdentitySheet = false },
                onSave = { updated ->
                    onSaveProfile(updated)
                    showEditIdentitySheet = false
                    Toast.makeText(
                        context,
                        localizedCtx.getString(R.string.identity_saved_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        // Sub-Sheet 2: Salary & Bonus Structure
        if (showSalarySheet) {
            SalaryStructureSheet(
                profile = profile,
                effectiveLang = effectiveLang,
                initialHighlightField = pendingSalaryHighlight,
                highlightTriggerId = highlightTriggerId,
                onDismiss = {
                    pendingSalaryHighlight = null
                    showSalarySheet = false
                },
                onSave = { updated ->
                    onSaveProfile(updated)
                    pendingSalaryHighlight = null
                    showSalarySheet = false
                    Toast.makeText(
                        context,
                        localizedCtx.getString(R.string.salary_saved_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        // Sub-Sheet 3: Categorized Settings Hub
        if (showSettingsDialog) {
            SettingsSheet(
                profile = profile,
                effectiveLang = effectiveLang,
                selectedSettingTag = selectedSettingTag,
                currentUser = currentUser,
                isAuthLoading = isAuthLoading,
                isSigningOut = isSigningOut,
                isOnline = isOnline,
                trashCount = trashItems.size,
                hasBlockingDialogOpen = showDeleteAllConfirmDialog || showSignOutConfirmDialog || showOfflineSignOutAlert || showTrashDialog,
                onOpenLanguagePicker = onOpenLanguagePicker,
                onSignInWithGoogle = onSignInWithGoogle,
                onRequestSignOut = {
                    if (NetworkMonitor.isStrictlyOnline(context)) {
                        showSignOutConfirmDialog = true
                    } else {
                        showOfflineSignOutAlert = true
                    }
                },
                onOpenTrash = { showTrashDialog = true },
                onRequestDeleteAll = { showDeleteAllConfirmDialog = true },
                onDismiss = { showSettingsDialog = false }
            )
        }

        // Sub-Sheet 4: Trash Management
        if (showTrashDialog) {
            TrashManagementDialog(
                trashItems = trashItems,
                effectiveLang = effectiveLang,
                onRestore = { item ->
                    onRestoreTrash(item)
                    Toast.makeText(context, localizedCtx.getString(R.string.trash_restored_toast), Toast.LENGTH_SHORT).show()
                },
                onDeletePermanently = { item ->
                    onDeleteTrashPermanently(item)
                    Toast.makeText(context, localizedCtx.getString(R.string.trash_deleted_toast), Toast.LENGTH_SHORT).show()
                },
                onClearAll = {
                    onClearAllTrash()
                    Toast.makeText(context, localizedCtx.getString(R.string.trash_emptied_toast), Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showTrashDialog = false }
            )
        }

        // Window-Level Confirmation Dialogs
        ProfileConfirmationDialogs(
            currentUser = currentUser,
            isOnline = isOnline,
            isSigningOut = isSigningOut,
            showDeleteAllConfirmDialog = showDeleteAllConfirmDialog,
            showOfflineSignOutAlert = showOfflineSignOutAlert,
            showSignOutConfirmDialog = showSignOutConfirmDialog,
            onDismissDeleteAll = { showDeleteAllConfirmDialog = false },
            onConfirmDeleteAll = {
                showDeleteAllConfirmDialog = false
                showSettingsDialog = false
                onDeleteAllData { }
            },
            onDismissOfflineSignOut = { showOfflineSignOutAlert = false },
            onDismissSignOutConfirm = { showSignOutConfirmDialog = false },
            onConfirmSignOut = {
                if (!NetworkMonitor.isStrictlyOnline(context)) {
                    showSignOutConfirmDialog = false
                    showOfflineSignOutAlert = true
                    return@ProfileConfirmationDialogs
                }
                onSignOut { _, _ -> showSignOutConfirmDialog = false }
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
    val spacing = ResponsiveUtils.rememberAppSpacing()
    Surface(
        shape = RoundedCornerShape(spacing.subCardRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(spacing.subCardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(spacing.iconMd))
            Spacer(modifier = Modifier.width(spacing.rowGapSm))
            Column(modifier = Modifier.weight(1f)) {
                AutoFitSingleLineText(
                    text = label,
                    maxFontSize = spacing.adaptiveSp(12f, 10.5f, 9.5f),
                    minFontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AutoFitSingleLineText(
                    text = value,
                    maxFontSize = spacing.adaptiveSp(14f, 12.5f, 11.5f),
                    minFontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
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
    val spacing = ResponsiveUtils.rememberAppSpacing()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutoFitSingleLineText(
            text = label,
            maxFontSize = spacing.adaptiveSp(13.5f, 12f, 11f),
            minFontSize = 8.5.sp,
            modifier = Modifier.weight(1f).padding(end = spacing.rowGapSm),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AutoFitSingleLineText(
            text = LaborLawCalculator.formatCurrency(amount, isBnDigits),
            maxFontSize = spacing.adaptiveSp(14.5f, 13f, 12f),
            minFontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
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
    val spacing = ResponsiveUtils.rememberAppSpacing()
    Surface(
        shape = RoundedCornerShape(spacing.subCardRadius),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.subCardPadding, vertical = spacing.compactActionPadV),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AutoFitSingleLineText(
                text = label,
                maxFontSize = spacing.adaptiveSp(12.5f, 11f, 10f),
                minFontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).padding(end = spacing.rowGapSm)
            )
            AutoFitSingleLineText(
                text = value,
                maxFontSize = spacing.adaptiveSp(13.5f, 12f, 11f),
                minFontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun OnboardingStepRow(
    stepNumber: Int,
    title: String,
    subtitle: String,
    isBnDigits: Boolean
) {
    val spacing = ResponsiveUtils.rememberAppSpacing()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(spacing.actionCircleSize)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = LaborLawCalculator.formatNumber(stepNumber.toDouble(), isBnDigits, 0),
                    fontSize = spacing.adaptiveSp(13.5f, 12f, 11f),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            AutoFitSingleLineText(
                text = title,
                maxFontSize = spacing.adaptiveSp(14.5f, 13f, 12f),
                minFontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = spacing.adaptiveSp(12.5f, 11f, 10.5f),
                lineHeight = spacing.adaptiveSp(17f, 15f, 14f),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
