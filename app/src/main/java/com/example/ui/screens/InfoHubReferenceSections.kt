package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.NightBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import com.example.util.AppSpacing
import com.example.util.HelplineCategory
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("info_helplines_card"),
        shape = RoundedCornerShape(spacing.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                accentColor = SuccessGreen,
                spacing = spacing
            )

            RmgInfoCatalog.helplines.forEach { helpline ->
                HelplineItemCard(
                    helpline = helpline,
                    isBengali = isBengali,
                    spacing = spacing,
                    onCallClick = { launchSafeDialer(context, helpline.dialNumber) },
                    onCopyClick = { copyPhoneNumber(context, helpline.dialNumber) }
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(spacing.subCardRadius),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
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
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(spacing.iconMd)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.info_helpline_checklist_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(R.string.info_helpline_checklist_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                        )
                    }
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
    onCallClick: () -> Unit,
    onCopyClick: () -> Unit
) {
    val accentColor = when (helpline.category) {
        HelplineCategory.LABOR -> MaterialTheme.colorScheme.primary
        HelplineCategory.LEGAL -> NightBlue
        HelplineCategory.EMERGENCY -> WarningOrange
        HelplineCategory.HEALTH -> SuccessGreen
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = displayNumber,
                        style = MaterialTheme.typography.titleMedium,
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
                            color = SuccessGreen
                        )
                    }
                    StatusTagBadge(
                        text = stringResource(
                            if (helpline.is24x7) R.string.info_helpline_24x7 else R.string.info_helpline_office_hours
                        ),
                        color = accentColor
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCopyClick,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("info_helpline_copy_${helpline.dialNumber}"),
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
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
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
    onLoadGradeInCalculator: (RmgGradeInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedGrade by rememberSaveable { mutableStateOf<Int?>(4) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("info_grades_card"),
        shape = RoundedCornerShape(spacing.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                accentColor = NightBlue,
                spacing = spacing
            )

            RmgInfoCatalog.rmgGrades.forEach { grade ->
                val isExpanded = expandedGrade == grade.gradeNumber
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("info_grade_item_${grade.gradeNumber}"),
                    shape = RoundedCornerShape(spacing.subCardRadius),
                    color = if (isExpanded) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isExpanded) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedGrade = if (isExpanded) null else grade.gradeNumber
                                }
                                .heightIn(min = 48.dp)
                                .padding(spacing.subCardPadding),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(grade.titleRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(grade.rolesRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = LaborLawCalculator.formatCurrency(grade.grossSalary, isBengali),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${LaborLawCalculator.formatCurrency(grade.otHourlyRate, isBengali)}/hr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NightBlue
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = isExpanded) {
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
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                KeyValueRow(
                                    stringResource(R.string.info_grade_basic_label),
                                    LaborLawCalculator.formatCurrency(grade.basicSalary, isBengali)
                                )
                                KeyValueRow(
                                    stringResource(R.string.info_grade_house_rent_label),
                                    LaborLawCalculator.formatCurrency(grade.houseRent, isBengali)
                                )
                                KeyValueRow(
                                    stringResource(R.string.info_grade_medical_label),
                                    LaborLawCalculator.formatCurrency(grade.medicalAllowance, isBengali)
                                )
                                KeyValueRow(
                                    stringResource(R.string.info_grade_transport_label),
                                    LaborLawCalculator.formatCurrency(grade.conveyanceAllowance, isBengali)
                                )
                                KeyValueRow(
                                    stringResource(R.string.info_grade_food_label),
                                    LaborLawCalculator.formatCurrency(grade.foodAllowance, isBengali)
                                )
                                KeyValueRow(
                                    stringResource(R.string.info_grade_gross_label),
                                    LaborLawCalculator.formatCurrency(grade.grossSalary, isBengali),
                                    highlight = true
                                )
                                KeyValueRow(
                                    stringResource(R.string.info_grade_ot_rate_label),
                                    "${LaborLawCalculator.formatCurrency(grade.otHourlyRate, isBengali)}/hr",
                                    highlight = true
                                )
                                OutlinedButton(
                                    onClick = { onLoadGradeInCalculator(grade) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .padding(top = 4.dp)
                                        .testTag("info_grade_load_calc_${grade.gradeNumber}")
                                ) {
                                    Text(stringResource(R.string.info_grade_use_in_calc_btn))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LaborLawGuideSection(
    spacing: AppSpacing,
    modifier: Modifier = Modifier
) {
    var expandedTopicId by rememberSaveable { mutableStateOf<String?>("ot_hours") }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("info_labor_law_card"),
        shape = RoundedCornerShape(spacing.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                accentColor = MaterialTheme.colorScheme.secondary,
                spacing = spacing
            )

            RmgInfoCatalog.laborLawTopics.forEach { topic ->
                LaborLawAccordionItem(
                    topic = topic,
                    isExpanded = expandedTopicId == topic.id,
                    spacing = spacing,
                    onToggle = {
                        expandedTopicId = if (expandedTopicId == topic.id) null else topic.id
                    }
                )
            }
        }
    }
}

@Composable
private fun LaborLawAccordionItem(
    topic: LaborLawTopic,
    isExpanded: Boolean,
    spacing: AppSpacing,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("info_law_topic_${topic.id}"),
        shape = RoundedCornerShape(spacing.subCardRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isExpanded) 0.5f else 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(topic.titleRes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = stringResource(topic.sectionRefRes),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    topic.pointsRes.forEach { pointRes ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
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
        color = color.copy(alpha = 0.14f)
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
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
