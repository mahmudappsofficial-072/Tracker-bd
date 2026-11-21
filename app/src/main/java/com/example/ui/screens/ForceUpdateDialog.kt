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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.util.AppUpdateInfo

@Composable
fun ForceUpdateDialog(
    updateInfo: AppUpdateInfo,
    lang: String = "bn",
    onDismissOptional: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isBn = lang.startsWith("bn")
    val isForce = updateInfo.isForceUpdate

    // If force update: Back button exits app.
    // If optional update: Back button simply closes the dialog and lets user continue.
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
                    .size(64.dp)
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
                    modifier = Modifier.size(36.dp)
                )
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isBn) updateInfo.titleBn else updateInfo.titleEn,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isForce) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = if (isForce) {
                            if (isBn) "জরুরি আপডেট" else "Required Update"
                        } else {
                            if (isBn) "নতুন ভার্সন" else "New Version"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isForce) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isBn) updateInfo.descBn else updateInfo.descEn,
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
                        text = if (isForce) {
                            if (isBn)
                                "💡 আপডেট সম্পন্ন না করা পর্যন্ত অ্যাপটি ব্যবহার করা যাবে না। গুগল প্লে-স্টোর থেকে নিরাপদ ও সর্বশেষ ভার্সন ইন্সটল করুন।"
                            else
                                "💡 You must update the app to continue. Install the latest official version from Google Play Store."
                        } else {
                            if (isBn)
                                "💡 এখনই আপডেট না করলেও অ্যাপটি ব্যবহার করতে পারবেন, তবে নতুন ফিচার পেতে আপডেট করে নেওয়া উত্তম।"
                            else
                                "💡 You can continue using the app, but updating is recommended for new features and bug fixes."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    openPlayStore(context, updateInfo.getEffectiveUrl())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("force_update_play_store_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = updateInfo.getEffectiveUpdateBtnText(isBn),
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
                    .testTag("update_dialog_dismiss_button")
            ) {
                Text(
                    text = updateInfo.getEffectiveDismissBtnText(isBn),
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

private fun openPlayStore(context: Context, customUrl: String = "") {
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
            android.widget.Toast.makeText(
                context,
                "Google Play Store / Browser not found",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}
