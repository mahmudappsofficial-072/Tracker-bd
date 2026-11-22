package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.util.AppUpdateInfo
import com.example.util.ResponsiveUtils

@Composable
fun ForceUpdateDialog(
    updateInfo: AppUpdateInfo,
    lang: String = "en",
    onDismissOptional: () -> Unit = {}
) {
    val context = LocalContext.current
    val spacing = ResponsiveUtils.rememberAppSpacing()
    val activity = context as? Activity
    val isForce = updateInfo.isForceUpdate

    BackHandler {
        if (isForce) {
            activity?.finish()
        } else {
            onDismissOptional()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isForce) {
                onDismissOptional()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isForce,
            dismissOnClickOutside = !isForce
        ),
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (isForce) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isForce) Icons.Default.SystemUpdate else Icons.Default.NewReleases,
                    contentDescription = null,
                    tint = if (isForce) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = updateInfo.getEffectiveTitle(context, lang),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isForce) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = stringResource(
                            if (isForce) R.string.update_required_badge else R.string.update_new_version_badge
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isForce) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = updateInfo.getEffectiveDesc(context, lang),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            if (isForce) R.string.update_force_hint else R.string.update_optional_hint
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(spacing.subCardPadding),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    openPlayStore(context, updateInfo.getEffectiveUrl(), lang)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.minTouchTarget)
                    .testTag("force_update_play_store_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = updateInfo.getEffectiveUpdateBtnText(context, lang),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isForce) {
                        activity?.finish()
                    } else {
                        onDismissOptional()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.minTouchTarget)
                    .testTag("update_dialog_dismiss_button")
            ) {
                Text(
                    text = updateInfo.getEffectiveDismissBtnText(context, lang),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

internal fun resolveSafePlayStoreUri(customUrl: String, packageName: String): Uri {
    val fallback = Uri.parse("market://details?id=$packageName")
    val trimmed = customUrl.trim()
    if (trimmed.isBlank()) return fallback
    return try {
        val parsed = Uri.parse(trimmed)
        val scheme = parsed.scheme?.lowercase() ?: ""
        val host = parsed.host?.lowercase() ?: ""
        when {
            scheme == "market" -> parsed
            (scheme == "https" || scheme == "http") && (host == "play.google.com" || host == "www.play.google.com") -> parsed
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

private fun openPlayStore(context: Context, customUrl: String = "", langTag: String = "en") {
    val packageName = context.packageName
    val targetUri = resolveSafePlayStoreUri(customUrl, packageName)

    try {
        val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (_: Exception) {
            val lctx = com.example.util.LanguageManager.getLocalizedContext(context, langTag)
            android.widget.Toast.makeText(
                context,
                lctx.getString(R.string.play_store_not_found_toast),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}
