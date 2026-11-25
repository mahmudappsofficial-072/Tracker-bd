package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.UserProfile
import com.example.ui.components.AutoFitSingleLineText
import com.example.ui.theme.SuccessGreen
import com.example.util.LanguageManager
import com.example.util.ResponsiveUtils
import com.google.firebase.auth.FirebaseUser

@Composable
fun SettingsSheet(
    profile: UserProfile,
    effectiveLang: String,
    selectedSettingTag: String,
    currentUser: FirebaseUser?,
    isAuthLoading: Boolean,
    isSigningOut: Boolean,
    isOnline: Boolean,
    trashCount: Int,
    hasBlockingDialogOpen: Boolean,
    onOpenLanguagePicker: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    onRequestSignOut: () -> Unit,
    onOpenTrash: () -> Unit,
    onRequestDeleteAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val localizedCtx = remember(context, effectiveLang) {
        LanguageManager.getLocalizedContext(context, effectiveLang)
    }
    val spacing = ResponsiveUtils.rememberAppSpacing()

    BackHandler(enabled = !hasBlockingDialogOpen) {
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
            .testTag("settings_sheet"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.sheetHeaderH, vertical = spacing.sheetHeaderV),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = stringResource(R.string.cd_app_logo),
                            modifier = Modifier
                                .size(spacing.avatarSize)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(spacing.rowGapMd))
                        AutoFitSingleLineText(
                            text = stringResource(R.string.cd_settings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxFontSize = spacing.adaptiveSp(18f, 16f, 14.5f),
                            minFontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            // Categorized Body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.sheetBodyH, vertical = spacing.sheetBodyV),
                verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)
            ) {
                // 1. General Section
                SettingsSectionHeader(title = stringResource(R.string.settings_general_section))
                Card(
                    shape = RoundedCornerShape(spacing.cardRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val currentLangDisplay = if (selectedSettingTag == LanguageManager.SYSTEM_TAG) {
                        "${stringResource(R.string.system_default_title)} (${LanguageManager.getDisplayName(LanguageManager.SYSTEM_TAG, effectiveLang)})"
                    } else {
                        LanguageManager.getDisplayName(effectiveLang, effectiveLang)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = spacing.minTouchTarget)
                            .clickable { onOpenLanguagePicker() }
                            .padding(spacing.listItemPadding)
                            .testTag("settings_language_row"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = spacing.rowGapMd)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(spacing.iconMd)
                            )
                            Spacer(modifier = Modifier.width(spacing.rowGapMd))
                            Column(modifier = Modifier.weight(1f)) {
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.app_language_label),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    minFontSize = 10.sp
                                )
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.current_language_fmt, currentLangDisplay),
                                    maxFontSize = 12.sp,
                                    minFontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(spacing.iconLg)
                        )
                    }
                }

                // 2. Account & Cloud Backup Section
                SettingsSectionHeader(title = stringResource(R.string.settings_account_backup_section))
                Card(
                    shape = RoundedCornerShape(spacing.cardRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.listItemPadding),
                        verticalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
                    ) {
                        if (currentUser == null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(spacing.avatarSize)
                                )
                                Spacer(modifier = Modifier.width(spacing.rowGapMd))
                                Column(modifier = Modifier.weight(1f)) {
                                    AutoFitSingleLineText(
                                        text = stringResource(R.string.google_account_title),
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        minFontSize = 10.sp
                                    )
                                    Text(
                                        text = stringResource(R.string.sign_in_sync_subtitle),
                                        fontSize = 11.5.sp,
                                        lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(spacing.heroRadius),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, Color(0xFFDADCE0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = spacing.minTouchTarget)
                                    .clickable(enabled = !isAuthLoading) { onSignInWithGoogle() }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = spacing.cardPadding, vertical = spacing.rowGapSm),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isAuthLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(spacing.iconMd), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(spacing.rowGapMd))
                                        AutoFitSingleLineText(
                                            text = stringResource(R.string.signing_in_label),
                                            maxFontSize = spacing.adaptiveSp(14.5f, 13f, 12f),
                                            minFontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(spacing.iconLg)
                                                .background(Color(0xFF4285F4), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("G", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                                        }
                                        Spacer(modifier = Modifier.width(spacing.rowGapMd))
                                        AutoFitSingleLineText(
                                            text = stringResource(R.string.sign_in_with_google),
                                            maxFontSize = spacing.adaptiveSp(14.5f, 13f, 12f),
                                            minFontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = spacing.rowGapSm)
                                ) {
                                    if (!currentUser.photoUrl?.toString().isNullOrBlank()) {
                                        AsyncImage(
                                            model = currentUser.photoUrl,
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
                                                Text(
                                                    text = (currentUser.displayName?.take(1) ?: "U").uppercase(),
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(spacing.rowGapMd))
                                    Column(modifier = Modifier.weight(1f)) {
                                        AutoFitSingleLineText(
                                            text = currentUser.displayName ?: stringResource(R.string.google_user_fallback),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            minFontSize = 10.sp
                                        )
                                        AutoFitSingleLineText(
                                            text = currentUser.email ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            minFontSize = 9.sp
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { if (!isSigningOut) onRequestSignOut() },
                                    enabled = !isSigningOut,
                                    contentPadding = spacing.compactButtonContentPadding,
                                    modifier = Modifier
                                        .heightIn(min = spacing.compactButtonHeight)
                                        .testTag("sign_out_button")
                                ) {
                                    if (!isOnline) {
                                        Icon(
                                            imageVector = Icons.Default.WifiOff,
                                            contentDescription = stringResource(R.string.cd_offline),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(spacing.iconSm)
                                        )
                                        Spacer(modifier = Modifier.width(spacing.rowGapSm))
                                    }
                                    AutoFitSingleLineText(
                                        text = stringResource(R.string.sign_out_btn),
                                        color = if (isOnline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.SemiBold,
                                        maxFontSize = spacing.adaptiveSp(14f, 13f, 12f),
                                        minFontSize = 9.5.sp
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

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
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = spacing.rowGapSm)
                                ) {
                                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(spacing.iconMd))
                                    Spacer(modifier = Modifier.width(spacing.rowGapMd))
                                    AutoFitSingleLineText(
                                        text = stringResource(R.string.auto_cloud_sync_title),
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodySmall,
                                        minFontSize = 9.sp
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(spacing.badgeRadius),
                                    color = if (profile.lastCloudSyncTime > 0L) SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    AutoFitSingleLineText(
                                        text = syncDateStr,
                                        maxFontSize = 11.sp,
                                        minFontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (profile.lastCloudSyncTime > 0L) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Data, Support & Legal Section
                SettingsSectionHeader(title = stringResource(R.string.settings_data_support_section))
                Card(
                    shape = RoundedCornerShape(spacing.cardRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsActionRow(
                            icon = Icons.Default.DeleteSweep,
                            iconTint = MaterialTheme.colorScheme.error,
                            title = stringResource(R.string.trash_bin_title),
                            subtitle = stringResource(R.string.trash_bin_subtitle),
                            badgeText = if (trashCount > 0) stringResource(R.string.items_count_fmt, trashCount) else null,
                            onClick = onOpenTrash
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        SettingsActionRow(
                            icon = Icons.Default.SupportAgent,
                            iconTint = SuccessGreen,
                            title = stringResource(R.string.whatsapp_support_btn),
                            onClick = {
                                val whatsappUrl = "https://wa.me/8801988697470"
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUrl)))
                                } catch (_: Exception) {
                                    Toast.makeText(
                                        context,
                                        localizedCtx.getString(R.string.whatsapp_error_fmt, whatsappUrl),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            testTag = "whatsapp_help_button"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        SettingsActionRow(
                            icon = Icons.Default.Security,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = stringResource(R.string.view_privacy_policy),
                            onClick = {
                                val privacyUrl = "https://mahmudappsofficial-072.github.io/overtime-tracker-privacy-policy/"
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl)))
                                } catch (_: Exception) {
                                    Toast.makeText(
                                        context,
                                        localizedCtx.getString(R.string.browser_open_error),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            testTag = "privacy_policy_button"
                        )
                    }
                }

                // 4. Danger Zone (Isolated cleanly from Cloud Sync)
                SettingsSectionHeader(
                    title = stringResource(R.string.settings_danger_zone_section),
                    color = MaterialTheme.colorScheme.error
                )
                Card(
                    shape = RoundedCornerShape(spacing.cardRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(spacing.listItemPadding)) {
                        OutlinedButton(
                            onClick = onRequestDeleteAll,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = spacing.minTouchTarget)
                                .testTag("delete_all_data_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                            shape = RoundedCornerShape(spacing.cardRadius),
                            contentPadding = spacing.compactButtonContentPadding
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(spacing.iconMd)
                            )
                            Spacer(modifier = Modifier.width(spacing.rowGapMd))
                            AutoFitSingleLineText(
                                text = stringResource(R.string.delete_all_data_btn),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                maxFontSize = spacing.adaptiveSp(14.5f, 13f, 12f),
                                minFontSize = 9.5.sp
                            )
                        }
                    }
                }
            }

            // Sticky Done Footer
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.sheetFooterH, vertical = spacing.sheetFooterV)
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(spacing.cardRadius),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = spacing.minTouchTarget)
                    ) {
                        AutoFitSingleLineText(
                            text = stringResource(R.string.done),
                            fontWeight = FontWeight.SemiBold,
                            maxFontSize = spacing.adaptiveSp(15.5f, 14f, 13f),
                            minFontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val spacing = ResponsiveUtils.rememberAppSpacing()
    AutoFitSingleLineText(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = color,
        maxFontSize = spacing.adaptiveSp(15f, 13.5f, 12.5f),
        minFontSize = 9.5.sp,
        modifier = Modifier.padding(horizontal = spacing.badgeH)
    )
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    testTag: String? = null,
    onClick: () -> Unit
) {
    val spacing = ResponsiveUtils.rememberAppSpacing()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minTouchTarget)
            .let { if (testTag != null) it.testTag(testTag) else it }
            .clickable(onClick = onClick)
            .padding(spacing.listItemPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(end = spacing.rowGapMd)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(spacing.iconMd)
            )
            Spacer(modifier = Modifier.width(spacing.rowGapMd))
            Column(modifier = Modifier.weight(1f)) {
                AutoFitSingleLineText(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxFontSize = spacing.adaptiveSp(15.5f, 14f, 13f),
                    minFontSize = 10.sp
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!badgeText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(spacing.badgeRadius),
                    color = MaterialTheme.colorScheme.error
                ) {
                    AutoFitSingleLineText(
                        text = badgeText,
                        color = MaterialTheme.colorScheme.onError,
                        maxFontSize = 11.sp,
                        minFontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                    )
                }
                Spacer(modifier = Modifier.width(spacing.rowGapSm))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(spacing.iconLg)
            )
        }
    }
}
