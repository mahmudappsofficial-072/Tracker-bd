package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.OvertimeRecord
import com.example.data.model.PieceRateRecord
import com.example.data.repository.OvertimeRepository
import com.example.ui.screens.resolveSafePlayStoreUri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Overtime Tracker BD", appName)
  }

  @Test
  fun `verify Bangladesh Labor Law double overtime rate calculation`() {
    val basicSalary = 10400.0
    val autoResult = com.example.util.LaborLawCalculator.calculateAutoAllowances(basicSalary)
    // Basic 10,400 / 104 = 100.0 per hour
    assertEquals(100.0, autoResult.otHourlyRate, 0.01)
    // House rent is 50%
    assertEquals(5200.0, autoResult.houseRent, 0.01)
  }

  @Test
  fun `resolveSafePlayStoreUri enforces market or Play Store host`() {
    val pkg = "com.overtime.bd"
    assertEquals(
      "market://details?id=com.overtime.bd",
      resolveSafePlayStoreUri("market://details?id=com.overtime.bd", pkg).toString()
    )
    assertEquals(
      "https://play.google.com/store/apps/details?id=com.overtime.bd",
      resolveSafePlayStoreUri("https://play.google.com/store/apps/details?id=com.overtime.bd", pkg).toString()
    )
    // Untrusted external or script URIs must fall back to safe market:// URI
    assertEquals(
      "market://details?id=com.overtime.bd",
      resolveSafePlayStoreUri("https://evil.example.com/update.apk", pkg).toString()
    )
    assertEquals(
      "market://details?id=com.overtime.bd",
      resolveSafePlayStoreUri("javascript:alert(1)", pkg).toString()
    )
    assertEquals(
      "market://details?id=com.overtime.bd",
      resolveSafePlayStoreUri("   ", pkg).toString()
    )
  }

  @Test
  fun `moveToTrash atomically moves records records tombstones and restore assigns fresh id`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("overtime_tombstones", Context.MODE_PRIVATE).edit().clear().commit()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val repo = OvertimeRepository(db, context)

      val ot = OvertimeRecord(
        date = "2026-11-12",
        yearMonth = "2026-11",
        otHours = 4.0,
        hourlyRateSnapshot = 100.0,
        calculatedAmount = 400.0
      )
      repo.insertOrUpdateOvertime(ot)
      val savedOt = db.overtimeDao().getRecordByDate("2026-11-12")!!

      val piece = PieceRateRecord(
        date = "2026-11-12",
        yearMonth = "2026-11",
        itemName = "Collar",
        pieceCount = 50,
        ratePerPiece = 5.0,
        totalAmount = 250.0
      )
      val pieceId = repo.insertPieceRate(piece)
      val savedPiece = db.pieceRateDao().getPieceRecordById(pieceId)!!

      // Move both to trash
      repo.moveOvertimeToTrash(savedOt)
      repo.movePieceRateToTrash(savedPiece)

      assertEquals(null, db.overtimeDao().getRecordByDate("2026-11-12"))
      assertEquals(null, db.pieceRateDao().getPieceRecordById(pieceId))
      val trash = db.trashDao().getAllTrashItems().first()
      assertEquals(2, trash.size)
      assertTrue(repo.getDeletedOtDates().contains("2026-11-12"))
      assertTrue(repo.getDeletedPieceIds().contains(pieceId.toString()))

      // Restore from trash -> tombstones cleared, piece restored without reusing stale id=pieceId
      trash.forEach { repo.restoreTrashItem(it) }
      assertTrue(db.trashDao().getAllTrashItems().first().isEmpty())
      val restoredOt = db.overtimeDao().getRecordByDate("2026-11-12")
      assertEquals(4.0, restoredOt?.otHours ?: 0.0, 0.001)
      assertFalse(repo.getDeletedOtDates().contains("2026-11-12"))

      val restoredPieces = db.pieceRateDao().getPieceRecordsByMonth("2026-11").first()
      assertEquals(1, restoredPieces.size)
      assertNotEquals(pieceId, restoredPieces[0].id)
      assertTrue(repo.getDeletedPieceIds().contains(pieceId.toString()))
      assertFalse(repo.getDeletedPieceIds().contains(restoredPieces[0].id.toString()))

      assertTrue(repo.awaitPendingCloudWrites(timeoutMs = 5_000L))
    } finally {
      db.close()
    }
  }

  @Test
  fun `updateRunningMonthOvertimeRates preserves createdAt and tracks pending delta sync dates`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("overtime_tombstones", Context.MODE_PRIVATE).edit().clear().commit()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val repo = OvertimeRepository(db, context)
      val originalCreatedAt = 1700000000000L
      db.overtimeDao().insertRecord(
        OvertimeRecord(
          date = "2026-11-18",
          yearMonth = "2026-11",
          otHours = 2.0,
          hourlyRateSnapshot = 80.0,
          calculatedAmount = 160.0,
          createdAt = originalCreatedAt
        )
      )

      repo.updateRunningMonthOvertimeRates("2026-11", 120.0)

      val updated = db.overtimeDao().getRecordByDate("2026-11-18")!!
      assertEquals(120.0, updated.hourlyRateSnapshot, 0.001)
      assertEquals(240.0, updated.calculatedAmount, 0.001)
      assertEquals(originalCreatedAt, updated.createdAt)
      assertTrue(repo.getPendingOtSyncDates().contains("2026-11-18"))
      assertTrue(repo.awaitPendingCloudWrites(timeoutMs = 5_000L))
    } finally {
      db.close()
    }
  }

  @Test
  fun `localized context loads all 5 languages and falls back to English for unsupported locales`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    val enCtx = com.example.util.LanguageManager.getLocalizedContext(context, "en")
    val bnCtx = com.example.util.LanguageManager.getLocalizedContext(context, "bn")
    val hiCtx = com.example.util.LanguageManager.getLocalizedContext(context, "hi")
    val taCtx = com.example.util.LanguageManager.getLocalizedContext(context, "ta")
    val arCtx = com.example.util.LanguageManager.getLocalizedContext(context, "ar")
    val frFallbackCtx = com.example.util.LanguageManager.getLocalizedContext(context, "fr")

    assertEquals("Home", enCtx.getString(R.string.tab_home))
    assertEquals("হোম", bnCtx.getString(R.string.tab_home))
    assertEquals("होम", hiCtx.getString(R.string.tab_home))
    assertEquals("முகப்பு", taCtx.getString(R.string.tab_home))
    assertEquals("الرئيسية", arCtx.getString(R.string.tab_home))
    assertEquals(enCtx.getString(R.string.tab_home), frFallbackCtx.getString(R.string.tab_home))

    // Format strings work safely across all 5 languages without FormatConversionException
    assertFalse(enCtx.getString(R.string.leave_limit_inline_fmt, "2").isBlank())
    assertFalse(bnCtx.getString(R.string.leave_limit_inline_fmt, "২").isBlank())
    assertFalse(hiCtx.getString(R.string.leave_limit_inline_fmt, "2").isBlank())
    assertFalse(taCtx.getString(R.string.leave_limit_inline_fmt, "2").isBlank())
    assertFalse(arCtx.getString(R.string.leave_limit_inline_fmt, "2").isBlank())

    // Format grade strips legacy words and localizes cleanly
    assertEquals("Grade 4", com.example.util.LanguageManager.formatDisplayGrade(context, "গ্রেড 4", "en"))
    assertEquals("গ্রেড 4", com.example.util.LanguageManager.formatDisplayGrade(context, "Grade 4", "bn"))
    assertEquals("ग्रेड 4", com.example.util.LanguageManager.formatDisplayGrade(context, "Grade 4", "hi"))
  }
}

