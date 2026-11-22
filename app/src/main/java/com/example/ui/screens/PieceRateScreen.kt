package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.PieceRateRecord
import com.example.data.model.UserProfile
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.MonthlySummaryState
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager
import com.example.util.ResponsiveUtils
import kotlinx.coroutines.launch

@Composable
fun PieceRateScreen(
    selectedMonth: String,
    pieceRecords: List<PieceRateRecord>,
    profile: UserProfile,
    summary: MonthlySummaryState,
    lang: String,
    onAddPieceClick: () -> Unit,
    onDeletePieceClick: (Long) -> Unit,
    onSetMonthLock: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val spacing = ResponsiveUtils.rememberAppSpacing()
    val isBnDigits = LanguageManager.isBengaliDigits(lang)
    var selectedTab by remember { mutableStateOf(0) } // 0: Piece Rate, 1: Pay Slip
    var showUnlockConfirmDialog by remember { mutableStateOf(false) }
    val currentCalendarYm = remember {
        java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
    }
    val isPastMonth = selectedMonth < currentCalendarYm

    val totalPieces = pieceRecords.sumOf { it.pieceCount }
    val totalPieceAmount = pieceRecords.sumOf { it.totalAmount }
    var itemToDelete by remember { mutableStateOf<PieceRateRecord?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var isGeneratingPdf by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (selectedTab == 0 && !summary.isLocked) {
                FloatingActionButton(
                    onClick = onAddPieceClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_piece_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_piece))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("piece_rate_screen")
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tab_piece_rate_ledger), fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                    modifier = Modifier.testTag("piece_tab_button")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.tab_pay_slip), fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) },
                    modifier = Modifier.testTag("slip_tab_button")
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = spacing.screenContentWithFab,
                    verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)
                ) {
                    if (isPastMonth) {
                        item {
                            PastMonthLockBanner(
                                selectedMonth = selectedMonth,
                                isLocked = summary.isLocked,
                                onUnlockClick = { showUnlockConfirmDialog = true },
                                onLockClick = { onSetMonthLock(true) }
                            )
                        }
                    }

                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(spacing.cardPadding)) {
                                Text(
                                    text = stringResource(R.string.piece_rate_summary_fmt, selectedMonth),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.total_produced_pieces),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = "${LaborLawCalculator.formatNumber(totalPieces.toDouble(), isBnDigits, 0)} ${stringResource(R.string.pieces_unit)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = stringResource(R.string.total_piece_income),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = LaborLawCalculator.formatCurrency(totalPieceAmount, isBnDigits),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (pieceRecords.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = spacing.emptyStateVertical),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Layers,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.no_piece_records_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.no_piece_records_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    } else {
                        items(pieceRecords, key = { it.id }) { item ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(spacing.listItemPadding),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.itemName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.piece_item_meta_fmt,
                                                item.date,
                                                LaborLawCalculator.formatNumber(item.pieceCount.toDouble(), isBnDigits, 0),
                                                LaborLawCalculator.formatCurrency(item.ratePerPiece, isBnDigits)
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (item.note.isNotBlank()) {
                                            Text(
                                                text = item.note,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = LaborLawCalculator.formatCurrency(item.totalAmount, isBnDigits),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (!summary.isLocked) {
                                            IconButton(onClick = { itemToDelete = item }) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = stringResource(R.string.cd_delete),
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = spacing.badgeH, vertical = spacing.badgeV),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.Lock,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(
                                                        text = stringResource(R.string.locked),
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val effHouseRent = if (summary.houseRent > 0.0) summary.houseRent else if (summary.isCustomSalary) profile.houseRent else (summary.basicSalary * 0.50)
                val effMedical = if (summary.medicalAllowance > 0.0) summary.medicalAllowance else if (summary.isCustomSalary) profile.medicalAllowance else 750.0
                val effFood = if (summary.foodAllowance > 0.0) summary.foodAllowance else if (summary.isCustomSalary) profile.foodAllowance else (if (summary.basicSalary > 0.0) 1250.0 else 0.0)

                val defaultCompanyName = stringResource(R.string.default_company_name)
                val displayCompany = if (summary.companyName.isNotBlank()) summary.companyName
                else if (profile.companyName.isNotBlank()) profile.companyName
                else defaultCompanyName
                val displayUserName = if (summary.userName.isNotBlank()) summary.userName else profile.userName
                val displayDesignation = if (summary.designation.isNotBlank()) summary.designation else profile.designation
                val displayWorkerId = if (summary.workerId.isNotBlank()) summary.workerId else profile.workerId
                val displayGrade = if (summary.grade.isNotBlank()) summary.grade else profile.grade

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = spacing.screenContentNoFab,
                    verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)
                ) {
                    if (isPastMonth) {
                        item {
                            PastMonthLockBanner(
                                selectedMonth = selectedMonth,
                                isLocked = summary.isLocked,
                                onUnlockClick = { showUnlockConfirmDialog = true },
                                onLockClick = { onSetMonthLock(true) }
                            )
                        }
                    }

                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                        ) {
                            Column(
                                modifier = Modifier.padding(spacing.heroCardPadding),
                                verticalArrangement = Arrangement.spacedBy(spacing.heroInnerGap)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(id = R.drawable.ic_app_logo),
                                        contentDescription = stringResource(R.string.cd_app_logo),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = displayCompany,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = stringResource(R.string.pay_slip_subtitle_fmt, selectedMonth),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                HorizontalDivider()

                                val formattedGrade = remember(displayGrade, lang) {
                                    LanguageManager.formatDisplayGrade(context, displayGrade, lang)
                                }
                                val designationLine = if (formattedGrade.isNotBlank()) {
                                    stringResource(R.string.designation_with_grade_fmt, displayDesignation, formattedGrade)
                                } else {
                                    stringResource(R.string.designation_colon_fmt, displayDesignation)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.worker_name_colon_fmt, displayUserName),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = designationLine,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (displayWorkerId.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = stringResource(R.string.card_id_colon_fmt, displayWorkerId),
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = SuccessGreen.copy(alpha = 0.14f),
                                    border = BorderStroke(1.5.dp, SuccessGreen),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = spacing.cardPadding, vertical = 9.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.net_payable_salary),
                                            fontWeight = FontWeight.Bold,
                                            color = SuccessGreen,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = LaborLawCalculator.formatCurrency(summary.totalEstimatedEarnings, isBnDigits),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp,
                                            color = SuccessGreen
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                if (!isGeneratingPdf) {
                                    coroutineScope.launch {
                                        isGeneratingPdf = true
                                        try {
                                            com.example.util.PdfSlipGenerator.generateAndDownloadPdf(
                                                context = context,
                                                profile = profile,
                                                summary = summary,
                                                selectedMonth = selectedMonth,
                                                langTag = lang
                                            )
                                        } finally {
                                            isGeneratingPdf = false
                                        }
                                    }
                                }
                            },
                            enabled = !isGeneratingPdf,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = spacing.minTouchTarget)
                                .testTag("download_pdf_button")
                        ) {
                            if (isGeneratingPdf) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.download_pdf),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(spacing.subCardPadding),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.pdf_info_note),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = {
                                val lctx = LanguageManager.getLocalizedContext(context, lang)
                                val gradePart = LanguageManager.formatDisplayGrade(context, displayGrade, lang)
                                    .let { if (it.isNotBlank()) " ($it)" else "" }
                                val metaIdDesignation = if (displayWorkerId.isNotBlank()) {
                                    lctx.getString(R.string.slip_share_id_designation_fmt, displayWorkerId, displayDesignation, gradePart)
                                } else {
                                    lctx.getString(R.string.slip_share_designation_fmt, displayDesignation, gradePart)
                                }
                                val salarySection = if (summary.isCustomSalary) {
                                    lctx.getString(
                                        R.string.slip_share_total_salary_fmt,
                                        LaborLawCalculator.formatCurrency(summary.basicSalary, isBnDigits)
                                    )
                                } else {
                                    listOf(
                                        lctx.getString(
                                            R.string.slip_share_basic_salary_fmt,
                                            LaborLawCalculator.formatCurrency(summary.basicSalary, isBnDigits)
                                        ),
                                        lctx.getString(
                                            R.string.slip_share_house_rent_fmt,
                                            LaborLawCalculator.formatCurrency(effHouseRent, isBnDigits)
                                        ),
                                        lctx.getString(
                                            R.string.slip_share_medical_food_fmt,
                                            LaborLawCalculator.formatCurrency(effMedical + effFood, isBnDigits)
                                        )
                                    ).joinToString("\n")
                                }

                                val lines = mutableListOf(
                                    "📄 *$displayCompany*",
                                    lctx.getString(R.string.slip_share_subtitle_fmt, selectedMonth),
                                    "-------------------------------",
                                    lctx.getString(R.string.slip_share_worker_fmt, displayUserName),
                                    metaIdDesignation,
                                    "-------------------------------",
                                    salarySection,
                                    lctx.getString(
                                        R.string.slip_share_ot_fmt,
                                        LaborLawCalculator.formatNumber(summary.totalOtHours, isBnDigits),
                                        LaborLawCalculator.formatCurrency(summary.totalOtAmount, isBnDigits)
                                    ),
                                    lctx.getString(
                                        R.string.slip_share_piece_fmt,
                                        LaborLawCalculator.formatCurrency(summary.totalPieceAmount, isBnDigits)
                                    ),
                                    lctx.getString(
                                        R.string.slip_share_night_tiffin_fmt,
                                        LaborLawCalculator.formatCurrency(summary.nightBillTotal + summary.tiffinBillTotal, isBnDigits)
                                    ),
                                    lctx.getString(
                                        R.string.slip_share_bonus_fmt,
                                        LaborLawCalculator.formatCurrency(summary.attendanceBonus, isBnDigits)
                                    )
                                )
                                if (summary.absentDeduction > 0) {
                                    lines.add(
                                        lctx.getString(
                                            R.string.slip_share_absent_deduction_fmt,
                                            summary.absentDays,
                                            LaborLawCalculator.formatCurrency(summary.absentDeduction, isBnDigits)
                                        )
                                    )
                                }
                                if (summary.advanceDeduction > 0) {
                                    lines.add(
                                        lctx.getString(
                                            R.string.slip_share_advance_deduction_fmt,
                                            LaborLawCalculator.formatCurrency(summary.advanceDeduction, isBnDigits)
                                        )
                                    )
                                }
                                if (summary.aponBazarDeduction > 0) {
                                    lines.add(
                                        lctx.getString(
                                            R.string.slip_share_apon_deduction_fmt,
                                            LaborLawCalculator.formatCurrency(summary.aponBazarDeduction, isBnDigits)
                                        )
                                    )
                                }
                                lines.add("-------------------------------")
                                lines.add(
                                    lctx.getString(
                                        R.string.slip_share_net_payable_fmt,
                                        LaborLawCalculator.formatCurrency(summary.totalEstimatedEarnings, isBnDigits)
                                    )
                                )
                                lines.add(lctx.getString(R.string.slip_share_footer))

                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, lines.joinToString("\n"))
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(
                                    sendIntent,
                                    lctx.getString(R.string.share_slip_chooser)
                                )
                                context.startActivity(shareIntent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = spacing.minTouchTarget)
                                .testTag("share_slip_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.share_pay_slip))
                        }
                    }
                }
            }
        }
    }

    if (itemToDelete != null) {
        val deleting = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.delete_piece_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.delete_piece_message_fmt,
                        deleting.itemName,
                        deleting.date
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePieceClick(deleting.id)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    MonthLockedInfoAndUnlockDialogs(
        showLockedInfo = false,
        showUnlockConfirm = showUnlockConfirmDialog,
        onDismissLockedInfo = {},
        onRequestUnlock = {},
        onDismissUnlockConfirm = { showUnlockConfirmDialog = false },
        onConfirmUnlock = {
            showUnlockConfirmDialog = false
            onSetMonthLock(false)
        }
    )
}
