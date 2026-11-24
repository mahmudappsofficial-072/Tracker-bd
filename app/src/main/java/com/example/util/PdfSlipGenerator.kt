package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import com.example.R
import com.example.data.model.UserProfile
import com.example.ui.viewmodel.MonthlySummaryState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object PdfSlipGenerator {

    private enum class BoxAlign {
        LEFT,
        RIGHT,
        CENTER
    }

    suspend fun generateAndDownloadPdf(
        context: Context,
        profile: UserProfile,
        summary: MonthlySummaryState,
        selectedMonth: String,
        langTag: String
    ): Uri? {
        val resolvedTag = LanguageManager.resolveSupportedTag(langTag)
        val lctx = LanguageManager.getLocalizedContext(context, resolvedTag)
        val isRtl = resolvedTag == "ar"

        var savedUri: Uri? = null
        var fileName = "Salary_Slip_$selectedMonth.pdf"
        var errorMessage: String? = null

        withContext(Dispatchers.IO) {
            val pdfDocument = PdfDocument()
            try {
                val pageWidth = 595 // A4 standard width in points
                val pageHeight = 842 // A4 standard height in points
                var pageNumber = 1
                var page = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                var canvas = page.canvas

                val paint = Paint().apply {
                    isAntiAlias = true
                }
                val textPaint = TextPaint(paint)

                fun drawBoxText(
                    c: Canvas,
                    text: String,
                    boxLeftX: Float,
                    baselineY: Float,
                    boxWidth: Float,
                    visualAlign: BoxAlign = if (isRtl) BoxAlign.RIGHT else BoxAlign.LEFT
                ) {
                    if (text.isEmpty()) return
                    textPaint.set(paint)
                    textPaint.textAlign = Paint.Align.LEFT
                    val safeWidth = boxWidth.coerceAtLeast(24f).toInt()
                    val ellipsized = TextUtils.ellipsize(
                        text,
                        textPaint,
                        safeWidth.toFloat(),
                        TextUtils.TruncateAt.END
                    )
                    val layoutAlign = when (visualAlign) {
                        BoxAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
                        BoxAlign.LEFT -> if (isRtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
                        BoxAlign.RIGHT -> if (isRtl) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE
                    }
                    val dir = if (isRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
                    val layout = StaticLayout.Builder.obtain(
                        ellipsized,
                        0,
                        ellipsized.length,
                        textPaint,
                        safeWidth
                    )
                        .setAlignment(layoutAlign)
                        .setTextDirection(dir)
                        .setLineSpacing(0f, 1f)
                        .setIncludePad(false)
                        .setMaxLines(1)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .build()

                    val firstLineBaseline = layout.getLineBaseline(0).toFloat()
                    c.save()
                    c.translate(boxLeftX, baselineY - firstLineBaseline)
                    layout.draw(c)
                    c.restore()
                }

                fun drawFooter(c: Canvas) {
                    val savedColor = paint.color
                    val savedSize = paint.textSize
                    val savedTypeface = paint.typeface
                    paint.color = Color.rgb(100, 116, 139)
                    paint.textSize = 9f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    drawBoxText(
                        c = c,
                        text = lctx.getString(R.string.pdf_generated_by),
                        boxLeftX = 44f,
                        baselineY = pageHeight - 36f,
                        boxWidth = pageWidth - 88f,
                        visualAlign = BoxAlign.CENTER
                    )
                    paint.color = savedColor
                    paint.textSize = savedSize
                    paint.typeface = savedTypeface
                }

                // Background card
                paint.color = Color.rgb(248, 250, 252)
                canvas.drawRect(24f, 24f, pageWidth - 24f, pageHeight - 24f, paint)

                // Header Background
                paint.color = Color.rgb(5, 150, 105) // Brand Primary
                canvas.drawRect(24f, 24f, pageWidth - 24f, 110f, paint)

                val headerTextLeft = if (isRtl) 112f else 44f
                val headerTextWidth = pageWidth - 156f
                val headerAlign = if (isRtl) BoxAlign.RIGHT else BoxAlign.LEFT

                // Company Name
                paint.color = Color.WHITE
                paint.textSize = 20f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val company = summary.companyName.ifBlank { lctx.getString(R.string.default_company_name) }
                drawBoxText(canvas, company, headerTextLeft, 60f, headerTextWidth, headerAlign)

                // Title
                paint.textSize = 12f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val headerSubtitle = lctx.getString(R.string.pay_slip_subtitle_fmt, selectedMonth)
                drawBoxText(canvas, headerSubtitle, headerTextLeft, 85f, headerTextWidth, headerAlign)

                // Brand Logo on corner of PDF Header (recycled safely)
                var logoBmp: Bitmap? = null
                var scaledLogo: Bitmap? = null
                try {
                    logoBmp = BitmapFactory.decodeResource(context.resources, R.drawable.ic_app_logo)
                    if (logoBmp != null) {
                        scaledLogo = Bitmap.createScaledBitmap(logoBmp, 60, 60, true)
                        val logoX = if (isRtl) 36f else pageWidth - 96f
                        canvas.drawBitmap(scaledLogo, logoX, 37f, paint)
                    }
                } catch (e: Exception) {
                    Log.w("PdfSlipGenerator", "Failed to draw PDF header logo: ${e.message}")
                } finally {
                    if (scaledLogo != null && !scaledLogo.isRecycled) {
                        scaledLogo.recycle()
                    }
                    if (logoBmp != null && logoBmp !== scaledLogo && !logoBmp.isRecycled) {
                        logoBmp.recycle()
                    }
                }

                var y = 140f

                fun ensureSpace(neededHeight: Float) {
                    if (y + neededHeight > pageHeight - 60f) {
                        drawFooter(canvas)
                        pdfDocument.finishPage(page)
                        pageNumber++
                        page = pdfDocument.startPage(
                            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        )
                        canvas = page.canvas
                        paint.color = Color.rgb(248, 250, 252)
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(24f, 24f, pageWidth - 24f, pageHeight - 24f, paint)
                        y = 56f
                    }
                }

                // Employee Meta Box
                paint.color = Color.rgb(255, 255, 255)
                canvas.drawRect(44f, y - 16f, pageWidth - 44f, y + 60f, paint)

                val displayUserName = summary.userName
                val displayDesignation = summary.designation
                val displayGrade = summary.grade
                val displayWorkerId = summary.workerId

                val leftColX = 56f
                val rightColX = 312f
                val leftMaxWidth = rightColX - leftColX - 12f
                val rightMaxWidth = (pageWidth - 56f) - rightColX

                val primaryColX = if (isRtl) rightColX else leftColX
                val primaryColWidth = if (isRtl) rightMaxWidth else leftMaxWidth
                val secondaryColX = if (isRtl) leftColX else rightColX
                val secondaryColWidth = if (isRtl) leftMaxWidth else rightMaxWidth
                val colAlign = if (isRtl) BoxAlign.RIGHT else BoxAlign.LEFT

                paint.color = Color.rgb(30, 41, 59)
                paint.textSize = 12f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val workerNameText = lctx.getString(R.string.worker_name_colon_fmt, displayUserName)
                drawBoxText(canvas, workerNameText, primaryColX, y + 8f, primaryColWidth, colAlign)

                val formattedGrade = LanguageManager.formatDisplayGrade(context, displayGrade, resolvedTag)
                val designationLine = if (formattedGrade.isNotBlank()) {
                    lctx.getString(R.string.designation_with_grade_fmt, displayDesignation, formattedGrade)
                } else {
                    lctx.getString(R.string.designation_colon_fmt, displayDesignation)
                }
                drawBoxText(canvas, designationLine, secondaryColX, y + 8f, secondaryColWidth, colAlign)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val workerIdText = lctx.getString(R.string.card_id_colon_fmt, displayWorkerId)
                drawBoxText(canvas, workerIdText, primaryColX, y + 36f, primaryColWidth, colAlign)

                val totalDays = summary.regularDutyDays + summary.dayShiftDays + summary.nightShiftDays
                val dutyLine = lctx.getString(R.string.pdf_total_duty_fmt, totalDays, summary.nightShiftDays)
                drawBoxText(canvas, dutyLine, secondaryColX, y + 36f, secondaryColWidth, colAlign)

                y += 90f

                // Section: Attendance Summary
                ensureSpace(70f)
                paint.color = Color.rgb(5, 150, 105)
                paint.textSize = 13f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                drawBoxText(
                    canvas,
                    lctx.getString(R.string.pdf_attendance_shift_title),
                    44f,
                    y,
                    pageWidth - 88f,
                    colAlign
                )
                y += 20f

                paint.color = Color.rgb(71, 85, 105)
                paint.textSize = 11f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val weeklyAndHolidays = summary.weeklyOffDays + summary.holidayDutyDays
                val leaveStatusText = if (summary.isBonusCancelledDueToLeave) {
                    lctx.getString(R.string.pdf_bonus_cancelled_paren)
                } else ""
                val weeklyLine = lctx.getString(R.string.pdf_weekly_holiday_fmt, weeklyAndHolidays)
                val leaveLine = lctx.getString(R.string.pdf_leave_days_fmt, summary.leaveDays, leaveStatusText)
                drawBoxText(canvas, weeklyLine, primaryColX, y, primaryColWidth, colAlign)
                drawBoxText(canvas, leaveLine, secondaryColX, y, secondaryColWidth, colAlign)
                y += 18f

                val bonusStatusText = if (summary.attendanceBonus > 0) {
                    lctx.getString(R.string.pdf_bonus_earned_paren)
                } else if (summary.isBonusCancelledDueToAbsent) {
                    lctx.getString(R.string.pdf_bonus_deducted_paren)
                } else {
                    ""
                }
                val absentLine = lctx.getString(R.string.pdf_absent_days_fmt, summary.absentDays, bonusStatusText)
                drawBoxText(canvas, absentLine, 56f, y, pageWidth - 112f, colAlign)
                y += 20f

                // Divider
                paint.color = Color.rgb(226, 232, 240)
                paint.strokeWidth = 1f
                canvas.drawLine(44f, y, pageWidth - 44f, y, paint)
                y += 24f

                // Section: Basic & Allowances
                ensureSpace(45f)
                paint.color = Color.rgb(5, 150, 105)
                paint.textSize = 13f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val sectionTitle = lctx.getString(
                    if (summary.isCustomSalary) R.string.pdf_total_salary_allowances_title
                    else R.string.pdf_basic_fixed_allowances_title
                )
                drawBoxText(canvas, sectionTitle, 44f, y, pageWidth - 88f, colAlign)
                y += 20f

                fun drawPdfRow(label: String, amount: Double, isBold: Boolean = false, isDeduction: Boolean = false) {
                    ensureSpace(22f)
                    paint.color = if (isDeduction) Color.rgb(220, 38, 38) else if (isBold) Color.rgb(15, 23, 42) else Color.rgb(71, 85, 105)
                    paint.textSize = 11f
                    paint.typeface = if (isBold || isDeduction) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    val valStr = if (isDeduction) {
                        String.format(Locale.US, "- Tk %.2f", kotlin.math.abs(amount))
                    } else {
                        String.format(Locale.US, "Tk %.2f", amount)
                    }
                    val valWidth = paint.measureText(valStr) + 24f
                    val maxLabelWidth = (pageWidth - 112f) - valWidth - 12f
                    if (isRtl) {
                        drawBoxText(canvas, valStr, 56f, y, valWidth, BoxAlign.LEFT)
                        drawBoxText(canvas, label, 56f + valWidth + 12f, y, maxLabelWidth, BoxAlign.RIGHT)
                    } else {
                        drawBoxText(canvas, label, 56f, y, maxLabelWidth, BoxAlign.LEFT)
                        drawBoxText(canvas, valStr, pageWidth - 56f - valWidth, y, valWidth, BoxAlign.RIGHT)
                    }
                    y += 18f
                }

                if (summary.isCustomSalary) {
                    drawPdfRow(lctx.getString(R.string.pdf_total_salary_row), summary.basicSalary)
                } else {
                    drawPdfRow(lctx.getString(R.string.pdf_basic_salary_row), summary.basicSalary)
                    drawPdfRow(lctx.getString(R.string.pdf_house_rent_row), summary.houseRent)
                    drawPdfRow(lctx.getString(R.string.pdf_medical_allowance_row), summary.medicalAllowance)
                    drawPdfRow(lctx.getString(R.string.pdf_conveyance_allowance_row), summary.conveyanceAllowance)
                    drawPdfRow(lctx.getString(R.string.pdf_food_allowance_row), summary.foodAllowance)
                }

                val attendanceLabel = if (summary.attendanceBonus > 0) {
                    if (summary.absentDays > 0) {
                        lctx.getString(R.string.pdf_attendance_bonus_allowed_fmt, summary.absentDays)
                    } else {
                        lctx.getString(R.string.pdf_attendance_bonus_regular)
                    }
                } else if (summary.isBonusCancelledDueToAbsent && summary.isBonusCancelledDueToLeave) {
                    lctx.getString(R.string.pdf_attendance_bonus_cancelled_both)
                } else if (summary.isBonusCancelledDueToLeave) {
                    lctx.getString(R.string.pdf_attendance_bonus_cancelled_leave_fmt, summary.leaveDays)
                } else if (summary.isBonusCancelledDueToAbsent) {
                    lctx.getString(R.string.pdf_attendance_bonus_cancelled_absent_fmt, summary.absentDays)
                } else {
                    lctx.getString(R.string.pdf_attendance_bonus_row)
                }
                drawPdfRow(attendanceLabel, summary.attendanceBonus)

                y += 6f
                paint.color = Color.rgb(226, 232, 240)
                canvas.drawLine(44f, y, pageWidth - 44f, y, paint)
                y += 20f

                // Section: Overtime & Extra
                ensureSpace(45f)
                paint.color = Color.rgb(5, 150, 105)
                paint.textSize = 13f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                drawBoxText(
                    canvas,
                    lctx.getString(R.string.pdf_overtime_extra_title),
                    44f,
                    y,
                    pageWidth - 88f,
                    colAlign
                )
                y += 20f

                drawPdfRow(
                    lctx.getString(
                        R.string.pdf_overtime_income_fmt,
                        String.format(Locale.US, "%.1f", summary.totalOtHours),
                        String.format(Locale.US, "%.2f", summary.hourlyOtRate)
                    ),
                    summary.totalOtAmount,
                    isBold = true
                )
                if (summary.nightBillTotal > 0) {
                    val nightRate = summary.effectiveNightRate
                    drawPdfRow(
                        lctx.getString(
                            R.string.pdf_night_bill_fmt,
                            summary.nightBillCount,
                            String.format(Locale.US, "%.0f", nightRate)
                        ),
                        summary.nightBillTotal
                    )
                }
                if (summary.tiffinBillTotal > 0) {
                    val tiffinRate = summary.effectiveTiffinRate
                    drawPdfRow(
                        lctx.getString(
                            R.string.pdf_tiffin_bill_fmt,
                            summary.tiffinBillCount,
                            String.format(Locale.US, "%.0f", tiffinRate)
                        ),
                        summary.tiffinBillTotal
                    )
                }
                drawPdfRow(lctx.getString(R.string.pdf_piece_rate_row), summary.totalPieceAmount, isBold = summary.totalPieceAmount > 0)

                // Section: Deductions
                val hasDeductions = summary.absentDeduction > 0 || summary.advanceDeduction > 0 || summary.aponBazarDeduction > 0
                if (hasDeductions) {
                    ensureSpace(50f)
                    y += 6f
                    paint.color = Color.rgb(226, 232, 240)
                    canvas.drawLine(44f, y, pageWidth - 44f, y, paint)
                    y += 20f

                    paint.color = Color.rgb(220, 38, 38)
                    paint.textSize = 13f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    drawBoxText(canvas, lctx.getString(R.string.pdf_deductions_title), 44f, y, pageWidth - 88f, colAlign)
                    y += 20f

                    if (summary.absentDeduction > 0) {
                        drawPdfRow(
                            lctx.getString(
                                R.string.pdf_absent_deduction_fmt,
                                summary.absentDays,
                                String.format(Locale.US, "%.2f", summary.dailyAbsentRate)
                            ),
                            summary.absentDeduction,
                            isDeduction = true
                        )
                    }
                    if (summary.advanceDeduction > 0) {
                        drawPdfRow(lctx.getString(R.string.pdf_advance_deduction_row), summary.advanceDeduction, isDeduction = true)
                    }
                    if (summary.aponBazarDeduction > 0) {
                        drawPdfRow(lctx.getString(R.string.pdf_apon_bazar_deduction_row), summary.aponBazarDeduction, isDeduction = true)
                    }
                }

                y += 10f
                ensureSpace(140f)

                // Net Payable Box
                paint.color = Color.rgb(236, 253, 245)
                canvas.drawRect(44f, y, pageWidth - 44f, y + 46f, paint)
                paint.color = Color.rgb(5, 150, 105)
                paint.strokeWidth = 1.5f
                paint.style = Paint.Style.STROKE
                canvas.drawRect(44f, y, pageWidth - 44f, y + 46f, paint)
                paint.style = Paint.Style.FILL

                paint.color = Color.rgb(5, 150, 105)
                paint.textSize = 14f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val netLabel = lctx.getString(R.string.net_payable_salary)
                paint.textSize = 16f
                val netStr = String.format(Locale.US, "Tk %.2f", summary.totalEstimatedEarnings)

                if (isRtl) {
                    paint.textSize = 16f
                    drawBoxText(canvas, netStr, 56f, y + 28f, 220f, BoxAlign.LEFT)
                    paint.textSize = 14f
                    drawBoxText(canvas, netLabel, 279f, y + 28f, pageWidth - 56f - 279f, BoxAlign.RIGHT)
                } else {
                    paint.textSize = 14f
                    drawBoxText(canvas, netLabel, 56f, y + 28f, 260f, BoxAlign.LEFT)
                    paint.textSize = 16f
                    drawBoxText(canvas, netStr, pageWidth - 56f - 220f, y + 28f, 220f, BoxAlign.RIGHT)
                }

                y += 70f

                // Signatures area
                paint.color = Color.rgb(100, 116, 139)
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.strokeWidth = 1f

                val leftSigText = if (isRtl) lctx.getString(R.string.pdf_authority_signature) else lctx.getString(R.string.pdf_worker_signature)
                val rightSigText = if (isRtl) lctx.getString(R.string.pdf_worker_signature) else lctx.getString(R.string.pdf_authority_signature)

                canvas.drawLine(56f, y + 40f, 196f, y + 40f, paint)
                drawBoxText(
                    c = canvas,
                    text = leftSigText,
                    boxLeftX = 46f,
                    baselineY = y + 54f,
                    boxWidth = 160f,
                    visualAlign = BoxAlign.CENTER
                )

                canvas.drawLine(pageWidth - 204f, y + 40f, pageWidth - 48f, y + 40f, paint)
                drawBoxText(
                    c = canvas,
                    text = rightSigText,
                    boxLeftX = pageWidth - 216f,
                    baselineY = y + 54f,
                    boxWidth = 180f,
                    visualAlign = BoxAlign.CENTER
                )

                // Footer note
                drawFooter(canvas)

                pdfDocument.finishPage(page)

                // Save to Phone Downloads off the Main Thread
                val cleanWorkerId = if (displayWorkerId.isNotBlank()) displayWorkerId.replace("/", "_").replace("\\", "_") else "Worker"
                fileName = "Salary_Slip_${cleanWorkerId}_${selectedMonth}.pdf"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { outStream ->
                            pdfDocument.writeTo(outStream)
                        }
                        savedUri = uri
                    }
                } else {
                    val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                    if (!targetDir.exists()) targetDir.mkdirs()
                    val file = File(targetDir, fileName)
                    FileOutputStream(file).use { outStream ->
                        pdfDocument.writeTo(outStream)
                    }
                    savedUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PdfSlipGenerator", "Failed to generate/save PDF: ${e.message}", e)
                errorMessage = e.localizedMessage ?: "Unknown error"
            } finally {
                pdfDocument.close()
            }
        }

        withContext(Dispatchers.Main) {
            if (errorMessage != null) {
                Toast.makeText(
                    context,
                    lctx.getString(R.string.pdf_save_error_fmt, errorMessage),
                    Toast.LENGTH_SHORT
                ).show()
            } else if (savedUri != null) {
                Toast.makeText(
                    context,
                    lctx.getString(R.string.pdf_downloaded_fmt, fileName),
                    Toast.LENGTH_LONG
                ).show()

                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(savedUri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(openIntent, lctx.getString(R.string.pdf_open_chooser)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(chooser)
                } catch (_: Exception) {
                    Toast.makeText(
                        context,
                        lctx.getString(R.string.pdf_no_viewer),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        return savedUri
    }
}
