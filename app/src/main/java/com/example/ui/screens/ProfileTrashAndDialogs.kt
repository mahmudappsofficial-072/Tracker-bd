package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.TrashItem
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager
import com.example.util.ResponsiveUtils
import com.google.firebase.auth.FirebaseUser
import kotlin.math.max

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
    val spacing = ResponsiveUtils.rememberAppSpacing()
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
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.sheetHeaderH, vertical = spacing.sheetHeaderV),
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
                            modifier = Modifier.size(24.dp)
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
                            TextButton(
                                onClick = { showClearAllConfirm = true },
                                contentPadding = spacing.compactButtonContentPadding,
                                modifier = Modifier.heightIn(min = 36.dp)
                            ) {
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
                    .padding(horizontal = spacing.sheetBodyH, vertical = spacing.sheetBodyV),
                verticalArrangement = Arrangement.spacedBy(spacing.sheetBodyGap)
            ) {
                Text(
                    text = stringResource(R.string.trash_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                if (trashItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = spacing.emptyStateVertical),
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
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                Column(modifier = Modifier.padding(spacing.listItemPadding)) {
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

                                        Surface(shape = RoundedCornerShape(4.dp), color = badgeColor) {
                                            Text(
                                                text = badgeLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeTextColor,
                                                modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
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

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { itemToDeletePermanently = item },
                                            contentPadding = spacing.compactButtonContentPadding,
                                            modifier = Modifier.heightIn(min = 36.dp)
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
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Button(
                                            onClick = { onRestore(item) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            contentPadding = spacing.compactButtonContentPadding,
                                            modifier = Modifier.heightIn(min = 36.dp)
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
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.sheetFooterH, vertical = spacing.sheetFooterV)
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
fun ProfileConfirmationDialogs(
    currentUser: FirebaseUser?,
    isOnline: Boolean,
    isSigningOut: Boolean,
    showDeleteAllConfirmDialog: Boolean,
    showOfflineSignOutAlert: Boolean,
    showSignOutConfirmDialog: Boolean,
    onDismissDeleteAll: () -> Unit,
    onConfirmDeleteAll: () -> Unit,
    onDismissOfflineSignOut: () -> Unit,
    onDismissSignOutConfirm: () -> Unit,
    onConfirmSignOut: () -> Unit
) {
    val spacing = ResponsiveUtils.rememberAppSpacing()

    if (showDeleteAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = onDismissDeleteAll,
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
                    onClick = onConfirmDeleteAll,
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
                    onClick = onDismissDeleteAll,
                    modifier = Modifier.testTag("cancel_delete_all_button")
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showOfflineSignOutAlert) {
        AlertDialog(
            onDismissRequest = onDismissOfflineSignOut,
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
                    onClick = onDismissOfflineSignOut,
                    modifier = Modifier.testTag("offline_signout_ok_button")
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    if (showSignOutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSigningOut) onDismissSignOutConfirm() },
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
                                modifier = Modifier.padding(spacing.subCardPadding),
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
                    onClick = onConfirmSignOut,
                    enabled = isOnline && !isSigningOut,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_sign_out_button")
                ) {
                    Text(stringResource(R.string.signout_action_btn))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissSignOutConfirm,
                    enabled = !isSigningOut
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
