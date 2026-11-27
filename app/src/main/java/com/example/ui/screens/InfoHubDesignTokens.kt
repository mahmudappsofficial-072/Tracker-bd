package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.EmeraldHeroEndDark
import com.example.ui.theme.EmeraldHeroEndLight
import com.example.ui.theme.EmeraldHeroStartDark
import com.example.ui.theme.EmeraldHeroStartLight
import com.example.ui.theme.SlateAccentDark
import com.example.ui.theme.SlateAccentLight
import com.example.ui.theme.SlateBorderDark
import com.example.ui.theme.SlateBorderLight
import com.example.ui.theme.SlateCanvasDark
import com.example.ui.theme.SlateCanvasLight
import com.example.ui.theme.SlateCardDark
import com.example.ui.theme.SlateCardLight
import com.example.ui.theme.SlateChipBgDark
import com.example.ui.theme.SlateChipBgLight
import com.example.ui.theme.SlateSubCardDark
import com.example.ui.theme.SlateSubCardLight
import com.example.ui.theme.StatusAlertRedDark
import com.example.ui.theme.StatusAlertRedLight
import com.example.ui.theme.StatusAmberDark
import com.example.ui.theme.StatusAmberLight
import com.example.ui.theme.StatusEmeraldDark
import com.example.ui.theme.StatusEmeraldLight
import com.example.util.AppSpacing

@Immutable
data class InfoHubPalette(
    val isDark: Boolean,
    val heroStart: Color,
    val heroEnd: Color,
    val heroText: Color,
    val heroSubtext: Color,
    val canvas: Color,
    val card: Color,
    val subCard: Color,
    val border: Color,
    val brandEmerald: Color,
    val slateAccent: Color,
    val chipBg: Color,
    val statusEmerald: Color,
    val statusAmber: Color,
    val statusAlertRed: Color
)

@Composable
fun rememberInfoHubPalette(): InfoHubPalette {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    return if (isDark) {
        InfoHubPalette(
            isDark = true,
            heroStart = EmeraldHeroStartDark,
            heroEnd = EmeraldHeroEndDark,
            heroText = Color.White,
            heroSubtext = Color.White.copy(alpha = 0.84f),
            canvas = SlateCanvasDark,
            card = SlateCardDark,
            subCard = SlateSubCardDark,
            border = SlateBorderDark,
            brandEmerald = colorScheme.primary,
            slateAccent = SlateAccentDark,
            chipBg = SlateChipBgDark,
            statusEmerald = StatusEmeraldDark,
            statusAmber = StatusAmberDark,
            statusAlertRed = StatusAlertRedDark
        )
    } else {
        InfoHubPalette(
            isDark = false,
            heroStart = EmeraldHeroStartLight,
            heroEnd = EmeraldHeroEndLight,
            heroText = Color.White,
            heroSubtext = Color.White.copy(alpha = 0.86f),
            canvas = SlateCanvasLight,
            card = SlateCardLight,
            subCard = SlateSubCardLight,
            border = SlateBorderLight,
            brandEmerald = colorScheme.primary,
            slateAccent = SlateAccentLight,
            chipBg = SlateChipBgLight,
            statusEmerald = StatusEmeraldLight,
            statusAmber = StatusAmberLight,
            statusAlertRed = StatusAlertRedLight
        )
    }
}

@Composable
fun InfoSectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    palette: InfoHubPalette,
    spacing: AppSpacing,
    accentColor: Color = palette.brandEmerald
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    color = palette.chipBg,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(spacing.iconMd)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SummaryCopyShareRow(
    palette: InfoHubPalette,
    copyTestTag: String,
    shareTestTag: String,
    onCopyClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onCopyClick,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag(copyTestTag),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, palette.border),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = palette.slateAccent
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.info_copy_summary_btn),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.info_copy_summary_btn),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        FilledTonalButton(
            onClick = onShareClick,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag(shareTestTag),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = palette.chipBg,
                contentColor = palette.brandEmerald
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = stringResource(R.string.info_share_summary_btn),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.info_share_summary_btn),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun copySummaryToClipboard(context: Context, label: String, summaryText: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, summaryText))
    Toast.makeText(
        context,
        context.getString(R.string.info_summary_copied_toast),
        Toast.LENGTH_SHORT
    ).show()
}

fun shareSummaryText(context: Context, summaryText: String) {
    try {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, summaryText)
        }
        val chooser = Intent.createChooser(
            sendIntent,
            context.getString(R.string.info_share_chooser_title)
        )
        context.startActivity(chooser)
    } catch (_: Exception) {
        copySummaryToClipboard(context, "summary", summaryText)
    }
}
