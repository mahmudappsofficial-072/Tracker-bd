package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.data.model.PieceRateRecord
import com.example.data.model.UserProfile
import com.example.ui.components.AutoFitSingleLineText
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.MonthlySummaryState
import com.example.util.GeneralInterstitialAdManager
import com.example.util.InterstitialAdGatePolicy
import com.example.util.LaborLawCalculator
import com.example.util.LanguageManager
import com.example.util.ResponsiveUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    var isPreparingPdfAd by remember { mutableStateOf(false) }
    var activePdfDownloadJob by remember { mutableStateOf<Job?>(null) }
    var debugAdDiagnostic by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            delay(InterstitialAdGatePolicy.PRELOAD_DEBOUNCE_MS)
            GeneralInterstitialAdManager.preloadOnPaySlipIfEligible(context)
        }
    }

    DisposableEffect(Unit) {
        onDispose { activePdfDownloadJob?.cancel() }
    }

    BackHandler(enabled = isPreparingPdfAd) {
        activePdfDownloadJob?.cancel()
        isPreparingPdfAd = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    text = {
                        AutoFitSingleLineText(
                            text = stringResource(R.string.tab_piece_rate_ledger),
                            fontWeight = FontWeight.Bold,
                            maxFontSize = spacing.adaptiveSp(14.5f, 13f, 11.5f),
                            minFontSize = 8.5.sp
                        )
                    },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(spacing.iconLg)) },
                    modifier = Modifier.testTag("piece_tab_button")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        AutoFitSingleLineText(
                            text = stringResource(R.string.tab_pay_slip),
                            fontWeight = FontWeight.Bold,
                            maxFontSize = spacing.adaptiveSp(14.5f, 13f, 11.5f),
                            minFontSize = 8.5.sp
                        )
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(spacing.iconLg)) },
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
                            shape = RoundedCornerShape(spacing.heroRadius),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(spacing.cardPadding)) {
                                AutoFitSingleLineText(
                                    text = stringResource(R.string.piece_rate_summary_fmt, selectedMonth),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxFontSize = spacing.adaptiveSp(17f, 15f, 13.5f),
                                    minFontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(spacing.rowGapSm))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.rowGapMd)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        AutoFitSingleLineText(
                                            text = stringResource(R.string.total_produced_pieces),
                                            maxFontSize = spacing.adaptiveSp(12.5f, 11f, 10f),
                                            minFontSize = 8.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                        AutoFitSingleLineText(
                                            text = "${LaborLawCalculator.formatNumber(totalPieces.toDouble(), isBnDigits, 0)} ${stringResource(R.string.pieces_unit)}",
                                            fontWeight = FontWeight.Bold,
                                            maxFontSize = spacing.adaptiveSp(18f, 16f, 14.5f),
                                            minFontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                        AutoFitSingleLineText(
                                            text = stringResource(R.string.total_piece_income),
                                            maxFontSize = spacing.adaptiveSp(12.5f, 11f, 10f),
                                            minFontSize = 8.sp,
                                            textAlign = TextAlign.End,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                        AutoFitSingleLineText(
                                            text = LaborLawCalculator.formatCurrency(totalPieceAmount, isBnDigits),
                                            fontWeight = FontWeight.Bold,
                                            maxFontSize = spacing.adaptiveSp(18f, 16f, 14.5f),
                                            minFontSize = 10.sp,
                                            textAlign = TextAlign.End,
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
                                        modifier = Modifier.size(spacing.avatarSize),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                    Spacer(modifier = Modifier.height(spacing.rowGapMd))
                                    Text(
                                        text = stringResource(R.string.no_piece_records_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(spacing.rowGapSm))
                                    Text(
                                        text = stringResource(R.string.no_piece_records_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(pieceRecords, key = { it.id }) { item ->
                            Card(
                                shape = RoundedCornerShape(spacing.cardRadius),
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
                                    Column(modifier = Modifier.weight(1f).padding(end = spacing.rowGapSm)) {
                                        AutoFitSingleLineText(
                                            text = item.itemName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxFontSize = spacing.adaptiveSp(16.5f, 14.5f, 13.5f),
                                            minFontSize = 10.sp
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
                                        AutoFitSingleLineText(
                                            text = LaborLawCalculator.formatCurrency(item.totalAmount, isBnDigits),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxFontSize = spacing.adaptiveSp(16.5f, 14.5f, 13.5f),
                                            minFontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (!summary.isLocked) {
                                            IconButton(onClick = { itemToDelete = item }) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = stringResource(R.string.cd_delete),
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(spacing.iconLg)
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.width(spacing.rowGapSm))
                                            Surface(
                                                shape = RoundedCornerShape(spacing.badgeRadius),
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
                                                        modifier = Modifier.size(spacing.iconSm)
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    AutoFitSingleLineText(
                                                        text = stringResource(R.string.locked),
                                                        maxFontSize = 10.sp,
                                                        minFontSize = 7.5.sp,
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
                val effHouseRent = summary.houseRent
                val effMedical = summary.medicalAllowance
                val effFood = summary.foodAllowance

                val defaultCompanyName = stringResource(R.string.default_company_name)
                val displayCompany = summary.companyName.ifBlank { defaultCompanyName }
                val displayUserName = summary.userName
                val displayDesignation = summary.designation
                val displayWorkerId = summary.workerId
                val displayGrade = summary.grade

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
                            shape = RoundedCornerShape(spacing.heroRadius),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(spacing.heroRadius)
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
                                            .size(spacing.avatarSize)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.height(spacing.rowGapSm))
                                    AutoFitSingleLineText(
                                        text = displayCompany,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxFontSize = spacing.adaptiveSp(20f, 18f, 16f),
                                        minFontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center
                                    )
                                    AutoFitSingleLineText(
                                        text = stringResource(R.string.pay_slip_subtitle_fmt, selectedMonth),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxFontSize = spacing.adaptiveSp(14.5f, 13f, 11.5f),
                                        minFontSize = 9.5.sp,
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
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AutoFitSingleLineText(
                                        text = stringResource(R.string.worker_name_colon_fmt, displayUserName),
                                        fontWeight = FontWeight.Bold,
                                        maxFontSize = 13.sp,
                                        minFontSize = 9.sp,
                                        modifier = Modifier.weight(1f).padding(end = spacing.rowGapSm)
                                    )
                                    AutoFitSingleLineText(
                                        text = designationLine,
                                        maxFontSize = 13.sp,
                                        minFontSize = 8.5.sp,
                                        textAlign = TextAlign.End,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                                if (displayWorkerId.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        AutoFitSingleLineText(
                                            text = stringResource(R.string.card_id_colon_fmt, displayWorkerId),
                                            maxFontSize = 12.5.sp,
                                            minFontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(spacing.subCardRadius),
                                    color = SuccessGreen.copy(alpha = 0.14f),
                                    border = BorderStroke(1.5.dp, SuccessGreen),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = spacing.cardPadding, vertical = spacing.listItemPadding),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AutoFitSingleLineText(
                                            text = stringResource(R.string.net_payable_salary),
                                            fontWeight = FontWeight.Bold,
                                            color = SuccessGreen,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxFontSize = spacing.adaptiveSp(16f, 14.5f, 13.5f),
                                            minFontSize = 10.sp,
                                            modifier = Modifier.weight(1f).padding(end = spacing.rowGapSm)
                                        )
                                        AutoFitSingleLineText(
                                            text = LaborLawCalculator.formatCurrency(summary.totalEstimatedEarnings, isBnDigits),
                                            fontWeight = FontWeight.ExtraBold,
                                            maxFontSize = spacing.adaptiveSp(19f, 17f, 15.5f),
                                            minFontSize = 11.sp,
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
                                if (!isGeneratingPdf && !isPreparingPdfAd) {
                                    activePdfDownloadJob = coroutineScope.launch {
                                        try {
                                            val outcome = GeneralInterstitialAdManager.showIfEligibleAndAwaitDismiss(
                                                context = context,
                                                onPreparingChanged = { preparing -> isPreparingPdfAd = preparing }
                                            )
                                            val diagMsg = InterstitialAdGatePolicy.formatDebugDiagnostic(outcome)
                                            debugAdDiagnostic = diagMsg
                                            diagMsg?.let {
                                                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                                            }
                                            isGeneratingPdf = true
                                            com.example.util.PdfSlipGenerator.generateAndDownloadPdf(
                                                context = context,
                                                profile = profile,
                                                summary = summary,
                                                selectedMonth = selectedMonth,
                                                langTag = lang
                                            )
                                        } catch (_: CancellationException) {
                                            // Cancelled via Back press or navigation while loading ad
                                        } finally {
                                            isPreparingPdfAd = false
                                            isGeneratingPdf = false
                                            activePdfDownloadJob = null
                                        }
                                    }
                                }
                            },
                            enabled = !isGeneratingPdf && !isPreparingPdfAd,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(spacing.cardRadius),
                            contentPadding = spacing.compactButtonContentPadding,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = spacing.minTouchTarget)
                                .testTag("download_pdf_button")
                        ) {
                            if (isGeneratingPdf) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(spacing.iconMd),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(spacing.iconMd))
                            }
                            Spacer(modifier = Modifier.width(spacing.rowGapSm))
                            AutoFitSingleLineText(
                                text = stringResource(R.string.download_pdf),
                                fontWeight = FontWeight.Bold,
                                maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                                minFontSize = 10.sp
                            )
                        }
                        if (BuildConfig.DEBUG && debugAdDiagnostic != null) {
                            Spacer(modifier = Modifier.height(spacing.rowGapSm))
                            Text(
                                text = debugAdDiagnostic ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("debug_ad_diagnostic_text")
                            )
                        }
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(spacing.subCardRadius),
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
                                    modifier = Modifier.size(spacing.iconMd)
                                )
                                Spacer(modifier = Modifier.width(spacing.rowGapMd))
                                Text(
                                    text = stringResource(R.string.pdf_info_note),
                                    fontSize = spacing.adaptiveSp(12.5f, 11f, 10.5f),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = spacing.adaptiveSp(17f, 15f, 14f)
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
                            shape = RoundedCornerShape(spacing.cardRadius),
                            contentPadding = spacing.compactButtonContentPadding,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = spacing.minTouchTarget)
                                .testTag("share_slip_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(spacing.iconMd))
                            Spacer(modifier = Modifier.width(spacing.rowGapSm))
                            AutoFitSingleLineText(
                                text = stringResource(R.string.share_pay_slip),
                                maxFontSize = spacing.adaptiveSp(15f, 14f, 13f),
                                minFontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }

        AnimatedVisibility(
            visible = isPreparingPdfAd,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(120))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(spacing.dialogOuterPadding)
                    .testTag("pdf_ad_loading_overlay"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(spacing.heroRadius),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 360.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.dialogCardPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.dialogInnerGap)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(spacing.avatarSize),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        AutoFitSingleLineText(
                            text = stringResource(R.string.pdf_preparing_ad_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxFontSize = 16.sp,
                            minFontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.pdf_preparing_ad_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
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
