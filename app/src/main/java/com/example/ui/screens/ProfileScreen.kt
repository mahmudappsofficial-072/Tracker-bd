package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.ui.theme.SuccessGreen
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
                    Text(
                        text = stringResource(R.string.tab_profile),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    FilledTonalIconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                }
            }

            // Card 1: Worker Identity Hero Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
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
                                        modifier = Modifier.size(48.dp).clip(CircleShape)
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = profile.userName.ifBlank { stringResource(R.string.your_name_placeholder) },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${profile.companyName.ifBlank { stringResource(R.string.company_placeholder) }} • ${profile.designation.ifBlank { stringResource(R.string.designation_placeholder) }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val displayGrade = LanguageManager.formatDisplayGrade(context, profile.grade, effectiveLang)
                                        .ifBlank { stringResource(R.string.grade_fallback_label) }
                                    Text(
                                        text = "${stringResource(R.string.id_short_label)}: ${profile.workerId.ifBlank { stringResource(R.string.not_applicable) }} • $displayGrade",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
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
                                    modifier = Modifier.size(20.dp)
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

            // Card 2: Salary & OT Summary Card (Opens SalaryStructureSheet)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("salary_summary_card")
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(spacing.cardInnerGap)
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
                                color = if (!profile.isCustomSalary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = stringResource(
                                        if (!profile.isCustomSalary) R.string.badge_labor_law else R.string.badge_custom_rules
                                    ),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (!profile.isCustomSalary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                                )
                            }
                        }

                        // Highlight Metrics (Gross & OT Rate)
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(spacing.cardPadding),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.total_regular_salary_label),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
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

                        if (!profile.isCustomSalary && autoResult.grossSalary > 0.0) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(spacing.subCardPadding),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    SalaryBreakdownRow(stringResource(R.string.row_basic_salary), autoResult.basicSalary, isBnDigits)
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    SalaryBreakdownRow(stringResource(R.string.row_house_rent), autoResult.houseRent, isBnDigits)
                                    SalaryBreakdownRow(stringResource(R.string.row_medical_allowance), 750.0, isBnDigits)
                                    SalaryBreakdownRow(stringResource(R.string.row_conveyance), autoResult.conveyanceAllowance, isBnDigits)
                                    SalaryBreakdownRow(stringResource(R.string.row_food_allowance), autoResult.foodAllowance, isBnDigits)
                                }
                            }
                        } else if (displayGross <= 0.0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.salary_not_configured_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(spacing.subCardPadding)
                                )
                            }
                        }

                        // Benefits & Bonus Summary Chips
                        val hasBenefits = profile.attendanceBonus > 0.0 || profile.nightBillRate > 0.0 || profile.tiffinBillRate > 0.0
                        if (hasBenefits) {
                            if (profile.nightBillRate > 0.0 || profile.tiffinBillRate > 0.0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = spacing.minTouchTarget)
                                .testTag("edit_profile_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.setup_edit_salary_btn),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Card 3: Account & App Menu Card (Clean Hub)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("account_card")
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(spacing.cardInnerGap)
                    ) {
                        Text(
                            text = stringResource(R.string.account_and_app_section),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )

                        // Google Account Row
                        if (currentUser == null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(spacing.listItemPadding),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.AccountCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = stringResource(R.string.google_account_title),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = stringResource(R.string.sign_in_sync_subtitle),
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
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(stringResource(R.string.signing_in_label), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                            } else {
                                                Box(
                                                    modifier = Modifier.size(22.dp).background(Color(0xFF4285F4), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("G", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
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
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(spacing.listItemPadding),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(22.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = currentUser.email ?: currentUser.displayName ?: stringResource(R.string.google_user_fallback),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = LanguageManager.formatSyncTimestamp(context, profile.lastCloudSyncTime, effectiveLang),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SuccessGreen
                                            )
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
                                        contentPadding = spacing.compactButtonContentPadding,
                                        modifier = Modifier.testTag("sign_out_button")
                                    ) {
                                        Text(
                                            text = stringResource(R.string.sign_out_btn),
                                            color = if (isOnline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Recycle Bin Row
                        HubMenuRow(
                            icon = Icons.Default.DeleteSweep,
                            iconBg = MaterialTheme.colorScheme.errorContainer,
                            iconTint = MaterialTheme.colorScheme.error,
                            title = stringResource(R.string.trash_bin_title),
                            subtitle = stringResource(R.string.trash_bin_subtitle),
                            badgeText = if (trashItems.isNotEmpty()) stringResource(R.string.items_count_fmt, trashItems.size) else null,
                            onClick = { showTrashDialog = true },
                            testTag = "trash_bin_button"
                        )

                        // Settings Row
                        HubMenuRow(
                            icon = Icons.Default.Settings,
                            iconBg = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = stringResource(R.string.cd_settings),
                            subtitle = stringResource(R.string.settings_subtitle),
                            onClick = { showSettingsDialog = true },
                            testTag = "settings_menu_row"
                        )
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
private fun HubMenuRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    testTag: String,
    onClick: () -> Unit
) {
    val spacing = ResponsiveUtils.rememberAppSpacing()
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minTouchTarget)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.listItemPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!badgeText.isNullOrBlank()) {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.error) {
                        Text(
                            text = badgeText,
                            color = MaterialTheme.colorScheme.onError,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
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
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(spacing.subCardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
    val spacing = ResponsiveUtils.rememberAppSpacing()
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.subCardPadding, vertical = 5.dp),
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
