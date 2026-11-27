package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.util.AppSpacing
import com.example.util.HelplineCategory
import com.example.util.HelplineFilterGroup
import com.example.util.InfoHubCalculator
import com.example.util.LaborLawCalculator
import com.example.util.LaborLawTopic
import com.example.util.RmgGradeInfo
import com.example.util.RmgInfoCatalog
import com.example.util.WorkerHelpline

fun launchSafeDialer(context: Context, phoneNumber: String) {
    val cleanNumber = phoneNumber.trim()
    try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber"))
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            context.getString(R.string.info_helpline_dialer_unavailable),
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun copyPhoneNumber(context: Context, number: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("helpline", number))
    Toast.makeText(
        context,
        context.getString(R.string.info_helpline_copied_fmt, number),
        Toast.LENGTH_SHORT
    ).show()
}

@Composable
fun HelplineDirectorySection(
    isBengali: Boolean,
    spacing: AppSpacing,
    palette: InfoHubPalette,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedGroup by rememberSaveable { mutableStateOf(HelplineFilterGroup.ALL) }
    val filteredHelplines = InfoHubCalculator.filterHelplines(selectedGroup)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("info_helplines_card"),
        verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)
    ) {
        // Pinned Priority Emergency Card
        PriorityEmergencyBanner(
            isBengali = isBengali,
            spacing = spacing,
            palette = palette,
            onCall16357 = { launchSafeDialer(context, "16357") },
            onCall999 = { launchSafeDialer(context, "999") }
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(spacing.cardRadius),
            colors = CardDefaults.cardColors(containerColor = palette.card),
            border = BorderStroke(1.dp, palette.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.cardPadding),
                verticalArrangement = Arrangement.spacedBy(spacing.cardInnerGap)
            ) {
                InfoSectionHeader(
                    icon = Icons.Filled.PhoneInTalk,
                    title = stringResource(R.string.info_helpline_section_title),
                    subtitle = stringResource(R.string.info_helpline_section_subtitle),
                    palette = palette,
                    spacing = spacing,
                    accentColor = palette.brandEmerald
                )

                val filterGroups = listOf(
                    HelplineFilterGroup.ALL to R.string.info_helpline_filter_all,
                    HelplineFilterGroup.LABOR to R.string.info_helpline_filter_labor,
                    HelplineFilterGroup.LEGAL to R.string.info_helpline_filter_legal,
                    HelplineFilterGroup.EMERGENCY_HEALTH to R.string.info_helpline_filter_emergency
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("info_helpline_filter_row"),
                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
                ) {
                    items(filterGroups, key = { it.first.name }) { (group, labelRes) ->
                        FilterChip(
                            selected = selectedGroup == group,
                            onClick = { selectedGroup = group },
                            label = { Text(stringResource(labelRes)) },
                            modifier = Modifier.testTag("info_helpline_filter_${group.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.chipBg,
                                selectedLabelColor = palette.brandEmerald
                            )
                        )
                    }
                }

                filteredHelplines.forEach { helpline ->
                    HelplineItemCard(
                        helpline = helpline,
                        isBengali = isBengali,
                        spacing = spacing,
                        palette = palette,
                        onCallClick = { launchSafeDialer(context, helpline.dialNumber) },
                        onCopyClick = { copyPhoneNumber(context, helpline.dialNumber) }
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(spacing.subCardRadius),
                    color = palette.subCard,
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.subCardPadding),
                        horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = palette.brandEmerald,
                            modifier = Modifier.size(spacing.iconMd)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.info_helpline_checklist_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.info_helpline_checklist_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PriorityEmergencyBanner(
    isBengali: Boolean,
    spacing: AppSpacing,
    palette: InfoHubPalette,
    onCall16357: () -> Unit,
    onCall999: () -> Unit,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null
) {
    val num16357 = if (isBengali) LaborLawCalculator.toBengaliDigits("16357") else "16357"
    val num999 = if (isBengali) LaborLawCalculator.toBengaliDigits("999") else "999"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("info_pinned_emergency_banner"),
        shape = RoundedCornerShape(spacing.subCardRadius),
        color = palette.card,
        border = BorderStroke(1.dp, palette.statusAlertRed.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Emergency,
                        contentDescription = null,
                        tint = palette.statusAlertRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.info_hub_emergency_strip_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StatusTagBadge(
                        text = stringResource(R.string.info_helpline_pinned_badge),
                        color = palette.statusAlertRed
                    )
                }
                if (trailingAction != null) {
                    trailingAction()
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCall16357,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("info_pinned_call_16357"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.brandEmerald,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "DIFE $num16357",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onCall999,
                    modifier = Modifier
                        .weight(0.85f)
                        .heightIn(min = 48.dp)
                        .testTag("info_pinned_call_999"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, palette.statusAlertRed.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.statusAlertRed),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "SOS $num999",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun HelplineItemCard(
    helpline: WorkerHelpline,
    isBengali: Boolean,
    spacing: AppSpacing,
    palette: InfoHubPalette,
    onCallClick: () -> Unit,
    onCopyClick: () -> Unit
) {
    val accentColor = when (helpline.category) {
        HelplineCategory.LABOR -> palette.brandEmerald
        HelplineCategory.LEGAL -> palette.slateAccent
        HelplineCategory.EMERGENCY -> palette.statusAlertRed
        HelplineCategory.HEALTH -> palette.statusEmerald
    }
    val displayNumber = if (isBengali) {
        LaborLawCalculator.toBengaliDigits(helpline.dialNumber)
    } else {
        helpline.dialNumber
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("info_helpline_item_${helpline.id}"),
        shape = RoundedCornerShape(spacing.subCardRadius),
        color = palette.subCard,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.subCardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(helpline.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(helpline.departmentRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = palette.chipBg
                ) {
                    Text(
                        text = displayNumber,
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = stringResource(helpline.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (helpline.isTollFree) {
                        StatusTagBadge(
                            text = stringResource(R.string.info_helpline_toll_free),
                            color = palette.statusEmerald
                        )
                    }
                    StatusTagBadge(
                        text = stringResource(
                            if (helpline.is24x7) R.string.info_helpline_24x7 else R.string.info_helpline_office_hours
                        ),
                        color = palette.slateAccent
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCopyClick,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("info_helpline_copy_${helpline.dialNumber}"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, palette.border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.slateAccent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.info_helpline_copy_btn),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.info_helpline_copy_btn),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Button(
                        onClick = onCallClick,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("info_helpline_call_${helpline.dialNumber}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (helpline.category == HelplineCategory.EMERGENCY) {
                                palette.statusAlertRed
                            } else {
                                palette.brandEmerald
                            },
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Call,
                            contentDescription = stringResource(R.string.info_helpline_call_btn),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.info_helpline_call_btn),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RmgGradeStructureSection(
    isBengali: Boolean,
    spacing: AppSpacing,
    palette: InfoHubPalette,
    onLoadGradeInCalculator: (RmgGradeInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedGrade by rememberSaveable { mutableStateOf(4) }
    val gradesOrder = RmgInfoCatalog.rmgGrades.sortedByDescending { it.gradeNumber } // Grade 4 -> 1

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("info_grades_card"),
        shape = RoundedCornerShape(spacing.cardRadius),
        colors = CardDefaults.cardColors(containerColor = palette.card),
        border = BorderStroke(1.dp, palette.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.cardInnerGap)
        ) {
            InfoSectionHeader(
                icon = Icons.Filled.Badge,
                title = stringResource(R.string.info_grades_section_title),
                subtitle = stringResource(R.string.info_grades_section_subtitle),
                palette = palette,
                spacing = spacing,
                accentColor = palette.brandEmerald
            )

            // Comparative Salary Matrix Card (Grades 4 to 1 side-by-side)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("info_grade_matrix_table"),
                shape = RoundedCornerShape(spacing.subCardRadius),
                color = palette.subCard,
                border = BorderStroke(1.dp, palette.border)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Matrix Title & Header Row
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.chipBg)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.info_grade_table_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.brandEmerald
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.subCard)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.info_grade_col_grade),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(0.95f)
                        )
                        Text(
                            text = stringResource(R.string.info_grade_col_basic),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.info_grade_col_gross),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.05f)
                        )
                        Text(
                            text = stringResource(R.string.info_grade_col_ot),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    HorizontalDivider(color = palette.border)

                    gradesOrder.forEachIndexed { idx, grade ->
                        val isSelected = selectedGrade == grade.gradeNumber
                        val gradeLabel = if (isBengali) {
                            "গ্রেড ${LaborLawCalculator.toBengaliDigits(grade.gradeNumber.toString())}"
                        } else {
                            "Grade ${grade.gradeNumber}"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) palette.chipBg.copy(alpha = 0.72f) else palette.card
                                )
                                .clickable { selectedGrade = grade.gradeNumber }
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("info_grade_item_${grade.gradeNumber}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = gradeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSelected) palette.brandEmerald else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(0.95f)
                            )
                            Text(
                                text = LaborLawCalculator.formatCurrency(grade.basicSalary, isBengali),
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = LaborLawCalculator.formatCurrency(grade.grossSalary, isBengali),
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Bold,
                                color = palette.brandEmerald,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1.05f)
                            )
                            Text(
                                text = LaborLawCalculator.formatCurrency(grade.otHourlyRate, isBengali),
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.SemiBold,
                                color = palette.slateAccent,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.9f)
                            )
                        }
                        if (idx < gradesOrder.lastIndex) {
                            HorizontalDivider(color = palette.border.copy(alpha = 0.65f))
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.info_grade_tap_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Active Grade Detailed Allowance Breakdown Card
            val activeGrade = gradesOrder.find { it.gradeNumber == selectedGrade } ?: gradesOrder.first()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("info_grade_detail_card"),
                shape = RoundedCornerShape(spacing.subCardRadius),
                color = palette.subCard,
                border = BorderStroke(1.dp, palette.brandEmerald.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.subCardPadding),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(activeGrade.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.brandEmerald
                    )
                    Text(
                        text = stringResource(activeGrade.rolesRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(
                        color = palette.border,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    KeyValueRow(
                        stringResource(R.string.info_grade_basic_label),
                        LaborLawCalculator.formatCurrency(activeGrade.basicSalary, isBengali),
                        palette = palette
                    )
                    KeyValueRow(
                        stringResource(R.string.info_grade_house_rent_label),
                        LaborLawCalculator.formatCurrency(activeGrade.houseRent, isBengali),
                        palette = palette
                    )
                    KeyValueRow(
                        stringResource(R.string.info_grade_medical_label),
                        LaborLawCalculator.formatCurrency(activeGrade.medicalAllowance, isBengali),
                        palette = palette
                    )
                    KeyValueRow(
                        stringResource(R.string.info_grade_transport_label),
                        LaborLawCalculator.formatCurrency(activeGrade.conveyanceAllowance, isBengali),
                        palette = palette
                    )
                    KeyValueRow(
                        stringResource(R.string.info_grade_food_label),
                        LaborLawCalculator.formatCurrency(activeGrade.foodAllowance, isBengali),
                        palette = palette
                    )
                    KeyValueRow(
                        stringResource(R.string.info_grade_gross_label),
                        LaborLawCalculator.formatCurrency(activeGrade.grossSalary, isBengali),
                        highlight = true,
                        palette = palette
                    )
                    KeyValueRow(
                        stringResource(R.string.info_grade_ot_rate_label),
                        "${LaborLawCalculator.formatCurrency(activeGrade.otHourlyRate, isBengali)}/hr",
                        highlight = true,
                        palette = palette
                    )

                    Button(
                        onClick = { onLoadGradeInCalculator(activeGrade) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(top = 4.dp)
                            .testTag("info_grade_load_calc_${activeGrade.gradeNumber}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.brandEmerald,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.info_grade_use_in_calc_btn),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LaborLawGuideSection(
    spacing: AppSpacing,
    palette: InfoHubPalette,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTopicFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedTopicId by rememberSaveable { mutableStateOf<String?>("ot_hours") }

    val filteredTopics = RmgInfoCatalog.laborLawTopics.filter { topic ->
        val matchesChip = selectedTopicFilter == null || topic.id == selectedTopicFilter
        if (!matchesChip) return@filter false
        if (searchQuery.isBlank()) return@filter true

        val chunks = buildList {
            add(context.getString(topic.titleRes))
            add(context.getString(topic.sectionRefRes))
            add(context.getString(topic.summaryRes))
            topic.pointsRes.forEach { add(context.getString(it)) }
        }
        InfoHubCalculator.matchesLaborLawSearch(chunks, searchQuery)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("info_labor_law_card"),
        shape = RoundedCornerShape(spacing.cardRadius),
        colors = CardDefaults.cardColors(containerColor = palette.card),
        border = BorderStroke(1.dp, palette.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.cardInnerGap)
        ) {
            InfoSectionHeader(
                icon = Icons.Filled.Gavel,
                title = stringResource(R.string.info_law_section_title),
                subtitle = stringResource(R.string.info_law_section_subtitle),
                palette = palette,
                spacing = spacing,
                accentColor = palette.brandEmerald
            )

            // Live Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.info_law_search_hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = palette.slateAccent
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.testTag("info_law_search_clear")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("info_law_search_input")
            )

            // Topic Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("info_law_filter_row"),
                horizontalArrangement = Arrangement.spacedBy(spacing.rowGapSm)
            ) {
                item(key = "all_topics") {
                    FilterChip(
                        selected = selectedTopicFilter == null,
                        onClick = { selectedTopicFilter = null },
                        label = { Text(stringResource(R.string.info_law_filter_all)) },
                        modifier = Modifier.testTag("info_law_filter_all"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.chipBg,
                            selectedLabelColor = palette.brandEmerald
                        )
                    )
                }
                items(RmgInfoCatalog.laborLawTopics, key = { it.id }) { topic ->
                    FilterChip(
                        selected = selectedTopicFilter == topic.id,
                        onClick = {
                            selectedTopicFilter = if (selectedTopicFilter == topic.id) null else topic.id
                            expandedTopicId = topic.id
                        },
                        label = { Text(stringResource(topic.titleRes)) },
                        modifier = Modifier.testTag("info_law_filter_${topic.id}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.chipBg,
                            selectedLabelColor = palette.brandEmerald
                        )
                    )
                }
            }

            if (filteredTopics.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("info_law_empty_state"),
                    shape = RoundedCornerShape(spacing.subCardRadius),
                    color = palette.subCard,
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SearchOff,
                            contentDescription = null,
                            tint = palette.slateAccent,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = stringResource(R.string.info_law_no_results),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.info_law_no_results_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                filteredTopics.forEach { topic ->
                    val forceExpanded = searchQuery.isNotBlank() || selectedTopicFilter != null
                    LaborLawAccordionItem(
                        topic = topic,
                        isExpanded = forceExpanded || expandedTopicId == topic.id,
                        spacing = spacing,
                        palette = palette,
                        onToggle = {
                            expandedTopicId = if (expandedTopicId == topic.id) null else topic.id
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LaborLawAccordionItem(
    topic: LaborLawTopic,
    isExpanded: Boolean,
    spacing: AppSpacing,
    palette: InfoHubPalette,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("info_law_topic_${topic.id}"),
        shape = RoundedCornerShape(spacing.subCardRadius),
        color = if (isExpanded) palette.card else palette.subCard,
        border = BorderStroke(
            1.dp,
            if (isExpanded) palette.brandEmerald.copy(alpha = 0.4f) else palette.border
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .heightIn(min = 48.dp)
                    .padding(spacing.subCardPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = stringResource(topic.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = palette.chipBg
                    ) {
                        Text(
                            text = stringResource(topic.sectionRefRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.brandEmerald,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = stringResource(topic.summaryRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = palette.slateAccent
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = spacing.subCardPadding,
                            end = spacing.subCardPadding,
                            bottom = spacing.subCardPadding
                        ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = palette.border)
                    topic.pointsRes.forEach { pointRes ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.brandEmerald
                            )
                            Text(
                                text = stringResource(pointRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTagBadge(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun KeyValueRow(
    label: String,
    value: String,
    palette: InfoHubPalette,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            color = if (highlight) palette.brandEmerald else MaterialTheme.colorScheme.onSurface
        )
    }
}
