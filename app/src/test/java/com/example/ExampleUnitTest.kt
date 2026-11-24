package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testMissingOtDetector_findsYesterdayWhenMissing() {
    val cal = java.util.Calendar.getInstance()
    cal.set(2026, java.util.Calendar.OCTOBER, 19) // Oct 19, 2026

    val existing = listOf(
      com.example.data.model.OvertimeRecord(date = "2026-10-17", yearMonth = "2026-10", otHours = 2.0)
    )

    val missing = com.example.ui.screens.MissingOtDetector.getMissingDatesForCurrentMonth(
      existingRecords = existing,
      referenceDate = cal.time
    )

    // Yesterday (2026-10-18) should be the first missing date
    assertTrue(missing.contains("2026-10-18"))
    assertEquals("2026-10-18", missing[0])
    // 2026-10-17 is already entered, so it must not be in missing
    assertFalse(missing.contains("2026-10-17"))
  }

  @Test
  fun testMissingOtDetector_returnsEmptyOnFirstDayOfMonth() {
    val cal = java.util.Calendar.getInstance()
    cal.set(2026, java.util.Calendar.NOVEMBER, 1) // Nov 1, 2026

    val missing = com.example.ui.screens.MissingOtDetector.getMissingDatesForCurrentMonth(
      existingRecords = emptyList(),
      referenceDate = cal.time
    )

    assertTrue(missing.isEmpty())
  }

  @Test
  fun testCustomSalary_returnsCustomOtRate() {
    val customProfile = com.example.data.model.UserProfile(
      basicSalary = 12000.0,
      isCustomSalary = true,
      customOtRate = 85.0
    )
    assertEquals(85.0, com.example.util.LaborLawCalculator.getHourlyOtRate(customProfile), 0.001)

    val customZeroOtProfile = customProfile.copy(customOtRate = 0.0)
    assertEquals(0.0, com.example.util.LaborLawCalculator.getHourlyOtRate(customZeroOtProfile), 0.001)
  }

  @Test
  fun testAutoAllowances_rmgBreakdownAndMedical750() {
    val result = com.example.util.LaborLawCalculator.calculateAutoAllowances(10000.0)
    assertEquals(5000.0, result.houseRent, 0.001)
    assertEquals(750.0, result.medicalAllowance, 0.001)
    assertEquals(450.0, result.conveyanceAllowance, 0.001)
    assertEquals(1250.0, result.foodAllowance, 0.001)
    assertEquals(17450.0, result.grossSalary, 0.001)
    assertEquals((10000.0 / 208.0) * 2.0, result.otHourlyRate, 0.001)

    val fromGross = com.example.util.LaborLawCalculator.calculateFromGrossSalary(17450.0)
    assertEquals(10000.0, fromGross.basicSalary, 0.001)
    assertEquals(750.0, fromGross.medicalAllowance, 0.001)
  }

  @Test
  fun testUtcDatePicker_roundTripAcrossTimeZones() {
    val originalTz = java.util.TimeZone.getDefault()
    try {
      java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Dhaka"))
      val utcMillis = com.example.ui.screens.parseDateToUtcMillis("2026-11-15")
      assertNotNull(utcMillis)
      assertEquals("2026-11-15", com.example.ui.screens.formatUtcMillisToDate(utcMillis!!))

      java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
      val utcMillisUs = com.example.ui.screens.parseDateToUtcMillis("2026-01-01")
      assertNotNull(utcMillisUs)
      assertEquals("2026-01-01", com.example.ui.screens.formatUtcMillisToDate(utcMillisUs!!))
    } finally {
      java.util.TimeZone.setDefault(originalTz)
    }
  }

  @Test
  fun testLocaleFormatting_bengaliAndEnglish() {
    val originalLocale = java.util.Locale.getDefault()
    try {
      java.util.Locale.setDefault(java.util.Locale.forLanguageTag("bn-BD"))
      assertEquals("৳ 1,250.50", com.example.util.LaborLawCalculator.formatCurrency(1250.5, isBengali = false))
      assertEquals("৳ ১,২৫০.৫০", com.example.util.LaborLawCalculator.formatCurrency(1250.5, isBengali = true))
      val bnMonth = com.example.util.LanguageManager.formatYearMonth("2026-11", isBn = true)
      assertTrue(bnMonth.contains("২০২৬"))
      assertFalse(bnMonth.contains("2026"))
    } finally {
      java.util.Locale.setDefault(originalLocale)
    }
  }

  @Test
  fun testMonthLockPolicy_gracePeriodAndManualOverrides() {
    val nov1 = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.NOVEMBER, 1, 10, 0) }.time
    val nov5 = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.NOVEMBER, 5, 23, 0) }.time
    val nov6 = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.NOVEMBER, 6, 0, 1) }.time

    // Days 1-5: Immediate previous month (2026-10) is unlocked when no snapshot exists
    assertFalse(com.example.util.MonthLockPolicy.resolveIsMonthLocked("2026-10", null, nov1))
    assertFalse(com.example.util.MonthLockPolicy.resolveIsMonthLocked("2026-10", null, nov5))
    // 2+ months older (2026-09) stays locked even on Nov 1
    assertTrue(com.example.util.MonthLockPolicy.resolveIsMonthLocked("2026-09", null, nov1))
    // Day 6: Grace period ends -> locked by default
    assertTrue(com.example.util.MonthLockPolicy.resolveIsMonthLocked("2026-10", null, nov6))
    // User manually locks on Nov 1 -> stays locked
    assertTrue(com.example.util.MonthLockPolicy.resolveIsMonthLocked("2026-10", true, nov1))
    // User manually unlocks on Nov 6 -> stays unlocked until re-locked
    assertFalse(com.example.util.MonthLockPolicy.resolveIsMonthLocked("2026-10", false, nov6))
    // Running month always unlocked
    assertFalse(com.example.util.MonthLockPolicy.resolveIsMonthLocked("2026-11", true, nov6))
  }

  @Test
  fun testMissingOtDetector_detectsPreviousMonthLastDayOnDay1() {
    val nov1 = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.NOVEMBER, 1, 9, 0) }.time
    val allRecs = listOf(
      com.example.data.model.OvertimeRecord(date = "2026-10-30", yearMonth = "2026-10", otHours = 2.0)
    )
    val missing = com.example.ui.screens.MissingOtDetector.getMissingDatesForReminder(
      currentMonthRecords = emptyList(),
      allRecords = allRecs,
      isPreviousMonthUnlocked = true,
      referenceDate = nov1
    )
    assertEquals(listOf("2026-10-31"), missing)

    // Locked previous month must NOT prompt
    val missingWhenLocked = com.example.ui.screens.MissingOtDetector.getMissingDatesForReminder(
      currentMonthRecords = emptyList(),
      allRecords = allRecs,
      isPreviousMonthUnlocked = false,
      referenceDate = nov1
    )
    assertTrue(missingWhenLocked.isEmpty())
  }

  @Test
  fun testProjectedTotalAtTarget_includesSalaryBonusPieceAndSubtractsDeductions() {
    // Gross 17,450 + Bonus 1,000 + Piece 500 + Current OT (20h * 100 = 2,000) - Advance 1,500 - Apon Bazar 500 = 18,950
    val currentTotal = 18950.0
    // Target 60h (remaining 40h * 100 = +4,000) -> 22,950
    val projected = com.example.ui.viewmodel.calculateProjectedTotalAtTarget(
      totalEstimatedEarnings = currentTotal,
      targetHours = 60.0,
      currentOtHours = 20.0,
      hourlyOtRate = 100.0
    )
    assertEquals(22950.0, projected, 0.001)

    // After taking another 1,000 Advance -> drops immediately by 1,000
    val projectedAfterDeduction = com.example.ui.viewmodel.calculateProjectedTotalAtTarget(
      totalEstimatedEarnings = currentTotal - 1000.0,
      targetHours = 60.0,
      currentOtHours = 20.0,
      hourlyOtRate = 100.0
    )
    assertEquals(21950.0, projectedAfterDeduction, 0.001)
  }

  @Test
  fun testProjectedTotalAtTarget_whenTargetExceeded_equalsActualEarnings() {
    val currentTotal = 24500.0 // already did 70h > 60h target
    val projected = com.example.ui.viewmodel.calculateProjectedTotalAtTarget(
      totalEstimatedEarnings = currentTotal,
      targetHours = 60.0,
      currentOtHours = 70.0,
      hourlyOtRate = 100.0
    )
    assertEquals(24500.0, projected, 0.001)
  }

  @Test
  fun testProjectedTotalAtTarget_whenDeductionsExceedEarnings_preservesNegativeNet() {
    val currentTotal = -1200.0 // salary not set or large advance
    val projected = com.example.ui.viewmodel.calculateProjectedTotalAtTarget(
      totalEstimatedEarnings = currentTotal,
      targetHours = 40.0,
      currentOtHours = 0.0,
      hourlyOtRate = 0.0
    )
    assertEquals(-1200.0, projected, 0.001)
  }

  @Test
  fun testConsentDateTimeFormatting_fixedDhakaAndUtcAcrossLocalesAndTimezones() {
    val originalTz = java.util.TimeZone.getDefault()
    val originalLocale = java.util.Locale.getDefault()
    try {
      // 2026-11-15T11:41:03Z -> 2026-11-15 05:41:03 PM (Asia/Dhaka, UTC+06:00)
      val fixedMillis = 1794742863000L

      java.util.Locale.setDefault(java.util.Locale.forLanguageTag("bn-BD"))
      java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"))

      assertEquals(
        "2026-11-15 05:41:03 PM (Asia/Dhaka, UTC+06:00)",
        com.example.data.repository.OvertimeRepository.formatConsentDateTimeBD(fixedMillis)
      )
      assertEquals(
        "2026-11-15T11:41:03Z",
        com.example.data.repository.OvertimeRepository.formatConsentDateTimeIso(fixedMillis)
      )
    } finally {
      java.util.TimeZone.setDefault(originalTz)
      java.util.Locale.setDefault(originalLocale)
    }
  }

  @Test
  fun testTargetBreakdown_capsTargetAchievedAndIsolatesExtraOt() {
    // 1. Below target: 35h of 50h at 100/hr -> achieved 3500, remaining 15h, extra 0
    val below = com.example.ui.viewmodel.calculateTargetBreakdown(
      targetHours = 50.0,
      currentOtHours = 35.0,
      totalOtAmount = 3500.0,
      hourlyOtRate = 100.0
    )
    assertFalse(below.isTargetCompleted)
    assertEquals(15.0, below.remainingTargetHours, 0.001)
    assertEquals(35.0, below.targetAchievedHours, 0.001)
    assertEquals(3500.0, below.targetAchievedAmount, 0.001)
    assertEquals(0.0, below.extraOtHoursBeyondTarget, 0.001)
    assertEquals(0.0, below.extraOtAmountBeyondTarget, 0.001)

    // 2. Exact match: 50h of 50h at 100/hr -> Target Met, extra 0
    val exact = com.example.ui.viewmodel.calculateTargetBreakdown(
      targetHours = 50.0,
      currentOtHours = 50.0,
      totalOtAmount = 5000.0,
      hourlyOtRate = 100.0
    )
    assertTrue(exact.isTargetCompleted)
    assertEquals(0.0, exact.remainingTargetHours, 0.001)
    assertEquals(50.0, exact.targetAchievedHours, 0.001)
    assertEquals(5000.0, exact.targetAchievedAmount, 0.001)
    assertEquals(0.0, exact.extraOtHoursBeyondTarget, 0.001)
    assertEquals(0.0, exact.extraOtAmountBeyondTarget, 0.001)

    // 3. Exceeded target with decimal extra hours: 65.25h of 50h at 100/hr -> left capped at 5000, right shows +15.25h / +1525
    val exceeded = com.example.ui.viewmodel.calculateTargetBreakdown(
      targetHours = 50.0,
      currentOtHours = 65.25,
      totalOtAmount = 6525.0,
      hourlyOtRate = 100.0
    )
    assertTrue(exceeded.isTargetCompleted)
    assertEquals(0.0, exceeded.remainingTargetHours, 0.001)
    assertEquals(50.0, exceeded.targetAchievedHours, 0.001)
    assertEquals(5000.0, exceeded.targetAchievedAmount, 0.001)
    assertEquals(15.25, exceeded.extraOtHoursBeyondTarget, 0.001)
    assertEquals(1525.0, exceeded.extraOtAmountBeyondTarget, 0.001)
    assertEquals(6525.0, exceeded.targetAchievedAmount + exceeded.extraOtAmountBeyondTarget, 0.001)
  }

  @Test
  fun testTargetBreakdown_handlesFloatingPointEpsilonZeroRateAndPastMonthSnapshot() {
    // Floating-point edge: 49.99999999999999 treated as completed with 0 extra
    val fpBoundary = com.example.ui.viewmodel.calculateTargetBreakdown(
      targetHours = 50.0,
      currentOtHours = 50.0 - 1e-12,
      totalOtAmount = 5000.0,
      hourlyOtRate = 100.0
    )
    assertTrue(fpBoundary.isTargetCompleted)
    assertEquals(0.0, fpBoundary.remainingTargetHours, 0.0)
    assertEquals(0.0, fpBoundary.extraOtHoursBeyondTarget, 0.0)
    assertEquals(0.0, fpBoundary.extraOtAmountBeyondTarget, 0.0)

    // Zero hourly OT rate (salary not set): hours still split accurately
    val zeroRate = com.example.ui.viewmodel.calculateTargetBreakdown(
      targetHours = 40.0,
      currentOtHours = 52.5,
      totalOtAmount = 0.0,
      hourlyOtRate = 0.0
    )
    assertTrue(zeroRate.isTargetCompleted)
    assertEquals(40.0, zeroRate.targetAchievedHours, 0.001)
    assertEquals(0.0, zeroRate.targetAchievedAmount, 0.001)
    assertEquals(12.5, zeroRate.extraOtHoursBeyondTarget, 0.001)
    assertEquals(0.0, zeroRate.extraOtAmountBeyondTarget, 0.001)

    // Past month snapshot difference: left + right always equals totalOtAmount
    val pastSnapshot = com.example.ui.viewmodel.calculateTargetBreakdown(
      targetHours = 50.0,
      currentOtHours = 60.0,
      totalOtAmount = 5850.0,
      hourlyOtRate = 100.0
    )
    assertTrue(pastSnapshot.isTargetCompleted)
    assertEquals(5000.0, pastSnapshot.targetAchievedAmount, 0.001)
    assertEquals(850.0, pastSnapshot.extraOtAmountBeyondTarget, 0.001)
    assertEquals(5850.0, pastSnapshot.targetAchievedAmount + pastSnapshot.extraOtAmountBeyondTarget, 0.001)
  }

  @Test
  fun testEditableNumberFormattingAndNormalization_removesZeroDefaultsAndLeadingZeros() {
    // 1. Zero or negative values format to empty string so users never erase "0" or "0.0"
    assertEquals("", com.example.util.LaborLawCalculator.formatEditableNumber(0.0))
    assertEquals("", com.example.util.LaborLawCalculator.formatEditableNumber(-10.0))
    assertEquals("", com.example.util.LaborLawCalculator.formatEditableNumber(0.0001))

    // 2. Positive integers omit ".0", decimals preserve fractional precision
    assertEquals("2", com.example.util.LaborLawCalculator.formatEditableNumber(2.0))
    assertEquals("15000", com.example.util.LaborLawCalculator.formatEditableNumber(15000.0))
    assertEquals("2.5", com.example.util.LaborLawCalculator.formatEditableNumber(2.5))
    assertEquals("85.25", com.example.util.LaborLawCalculator.formatEditableNumber(85.25))

    // 3. Leading zero normalization preserves "0." / "0.5" while collapsing "05" -> "5" (EN & BN)
    assertEquals("5", com.example.util.LaborLawCalculator.normalizeNumericInput("05", allowDecimal = true))
    assertEquals("500", com.example.util.LaborLawCalculator.normalizeNumericInput("00500", allowDecimal = true))
    assertEquals("0", com.example.util.LaborLawCalculator.normalizeNumericInput("00", allowDecimal = true))
    assertEquals("0.", com.example.util.LaborLawCalculator.normalizeNumericInput("0.", allowDecimal = true))
    assertEquals("0.5", com.example.util.LaborLawCalculator.normalizeNumericInput("0.5", allowDecimal = true))
    assertEquals("0.5", com.example.util.LaborLawCalculator.normalizeNumericInput("00.5", allowDecimal = true))
    assertEquals("৫", com.example.util.LaborLawCalculator.normalizeNumericInput("০৫", allowDecimal = true))
    assertEquals("০.৫", com.example.util.LaborLawCalculator.normalizeNumericInput("০.৫", allowDecimal = true))

    // 4. Integer-only fields reject decimals and non-digits
    assertEquals("125", com.example.util.LaborLawCalculator.normalizeNumericInput("012.5a", allowDecimal = false))
    assertEquals("12.34", com.example.util.LaborLawCalculator.normalizeNumericInput("12.3.4", allowDecimal = true))
  }

  @Test
  fun testNetworkMonitor_requiresBothInternetAndValidatedCapabilities() {
    assertTrue(com.example.util.NetworkMonitor.hasValidatedInternet(hasInternetCapability = true, hasValidatedCapability = true))
    assertFalse(com.example.util.NetworkMonitor.hasValidatedInternet(hasInternetCapability = true, hasValidatedCapability = false))
    assertFalse(com.example.util.NetworkMonitor.hasValidatedInternet(hasInternetCapability = false, hasValidatedCapability = true))
    assertFalse(com.example.util.NetworkMonitor.hasValidatedInternet(hasInternetCapability = false, hasValidatedCapability = false))
  }

  @Test
  fun testGuardedSignOut_forbidsLogoutWhenOfflineOrSyncFails() = kotlinx.coroutines.test.runTest {
    // 1. Offline: strictly forbidden, must not sync, sign out, or wipe local data
    var syncCalled = false
    var authSignedOut = false
    var localWiped = false
    val offlineRes = com.example.ui.viewmodel.executeGuardedSignOut(
      isBn = true,
      isOnline = { false },
      verifyAndSyncCloud = { syncCalled = true; Pair(true, "ok") },
      performAuthSignOut = { authSignedOut = true },
      clearLocalData = { localWiped = true; true }
    )
    assertFalse(offlineRes.first)
    assertFalse(syncCalled)
    assertFalse(authSignedOut)
    assertFalse(localWiped)

    // 2. Online, but cloud sync / server verification fails -> must NOT sign out or wipe local data
    authSignedOut = false
    localWiped = false
    val syncFailRes = com.example.ui.viewmodel.executeGuardedSignOut(
      isBn = false,
      isOnline = { true },
      verifyAndSyncCloud = { Pair(false, "Server unreachable") },
      performAuthSignOut = { authSignedOut = true },
      clearLocalData = { localWiped = true; true }
    )
    assertFalse(syncFailRes.first)
    assertEquals("Server unreachable", syncFailRes.second)
    assertFalse(authSignedOut)
    assertFalse(localWiped)

    // 3. Online, but sync throws exception -> must NOT sign out or wipe local data
    authSignedOut = false
    localWiped = false
    val exceptionRes = com.example.ui.viewmodel.executeGuardedSignOut(
      isBn = false,
      isOnline = { true },
      verifyAndSyncCloud = { throw RuntimeException("Timeout") },
      performAuthSignOut = { authSignedOut = true },
      clearLocalData = { localWiped = true; true }
    )
    assertFalse(exceptionRes.first)
    assertFalse(authSignedOut)
    assertFalse(localWiped)

    // 4. Online during sync, then connection drops right before commit -> must NOT sign out or wipe local data
    var checks = 0
    authSignedOut = false
    localWiped = false
    val droppedRes = com.example.ui.viewmodel.executeGuardedSignOut(
      isBn = true,
      isOnline = { checks++ == 0 },
      verifyAndSyncCloud = { Pair(true, "ok") },
      performAuthSignOut = { authSignedOut = true },
      clearLocalData = { localWiped = true; true }
    )
    assertFalse(droppedRes.first)
    assertFalse(authSignedOut)
    assertFalse(localWiped)

    // 5. Online throughout AND cloud sync + verification succeed -> signs out and clears local data
    authSignedOut = false
    localWiped = false
    val successRes = com.example.ui.viewmodel.executeGuardedSignOut(
      isBn = true,
      isOnline = { true },
      verifyAndSyncCloud = { Pair(true, "verified") },
      performAuthSignOut = { authSignedOut = true },
      clearLocalData = { localWiped = true; true }
    )
    assertTrue(successRes.first)
    assertTrue(authSignedOut)
    assertTrue(localWiped)
  }

  @Test
  fun testMissingFieldHighlight_staysRedUntilInputEntered() {
    // Stays red while empty and target matches
    assertTrue(com.example.ui.screens.shouldShowMissingFieldHighlight("SALARY", "SALARY", ""))
    assertTrue(com.example.ui.screens.shouldShowMissingFieldHighlight("SALARY", "SALARY", "   "))
    assertTrue(com.example.ui.screens.shouldShowMissingFieldHighlight("NIGHT_BILL", "NIGHT_BILL", ""))
    assertTrue(com.example.ui.screens.shouldShowMissingFieldHighlight("TIFFIN_BILL", "TIFFIN_BILL", ""))

    // Clears immediately once any digit/input is entered
    assertFalse(com.example.ui.screens.shouldShowMissingFieldHighlight("SALARY", "SALARY", "1"))
    assertFalse(com.example.ui.screens.shouldShowMissingFieldHighlight("SALARY", "SALARY", "15000"))

    // Does not highlight unrelated fields or when cleared (null)
    assertFalse(com.example.ui.screens.shouldShowMissingFieldHighlight("SALARY", "NIGHT_BILL", ""))
    assertFalse(com.example.ui.screens.shouldShowMissingFieldHighlight(null, "SALARY", ""))
  }

  @Test
  fun testComputeMonthlySummary_pastMonthSnapshotZeroValuesDoNotLeakProfile() {
    val activeProfile = com.example.data.model.UserProfile(
      userName = "Updated Worker",
      basicSalary = 15000.0,
      attendanceBonus = 1500.0,
      nightBillRate = 200.0,
      tiffinBillRate = 100.0,
      monthlyOtTargetHours = 80.0
    )
    val pastSnapshot = com.example.data.model.MonthlySalarySettings(
      yearMonth = "2026-09",
      basicSalary = 10400.0,
      grossSalary = 17850.0,
      hourlyOtRate = 100.0,
      attendanceBonus = 0.0,
      nightBillRate = 0.0,
      tiffinBillRate = 0.0,
      monthlyOtTargetHours = 0.0,
      isLocked = true,
      updatedAt = 1790000000000L
    )
    val otRecords = listOf(
      com.example.data.model.OvertimeRecord(
        id = 1L,
        date = "2026-09-10",
        yearMonth = "2026-09",
        otHours = 3.0,
        hasNightBill = true,
        hasTiffinBill = true,
        hourlyRateSnapshot = 100.0
      )
    )

    val pastSummary = com.example.ui.viewmodel.computeMonthlySummary(
      selectedYearMonth = "2026-09",
      currentCalendarYm = "2026-11",
      profile = activeProfile,
      monthSnapshot = pastSnapshot,
      otList = otRecords,
      pieceList = emptyList(),
      deductionList = emptyList()
    )

    assertEquals(10400.0, pastSummary.basicSalary, 0.001)
    assertEquals(100.0, pastSummary.hourlyOtRate, 0.001)
    assertEquals(300.0, pastSummary.totalOtAmount, 0.001)
    assertEquals(0.0, pastSummary.attendanceBonus, 0.001)
    assertEquals(0.0, pastSummary.effectiveNightRate, 0.001)
    assertEquals(0.0, pastSummary.nightBillTotal, 0.001)
    assertEquals(0.0, pastSummary.effectiveTiffinRate, 0.001)
    assertEquals(0.0, pastSummary.tiffinBillTotal, 0.001)
    assertEquals(0.0, pastSummary.targetHours, 0.001)
    assertTrue(pastSummary.isLocked)

    // Same compute function for running month uses live activeProfile
    val runningSummary = com.example.ui.viewmodel.computeMonthlySummary(
      selectedYearMonth = "2026-11",
      currentCalendarYm = "2026-11",
      profile = activeProfile,
      monthSnapshot = null,
      otList = emptyList(),
      pieceList = emptyList(),
      deductionList = emptyList()
    )
    assertEquals(15000.0, runningSummary.basicSalary, 0.001)
    assertEquals(1500.0, runningSummary.attendanceBonus, 0.001)
    assertEquals(200.0, runningSummary.effectiveNightRate, 0.001)
    assertEquals(80.0, runningSummary.targetHours, 0.001)
    assertFalse(runningSummary.isLocked)
  }

  @Test
  fun testLanguageManager_supportedTagsAndEnglishFallback() {
    assertEquals("en", com.example.util.LanguageManager.resolveSupportedTag("en"))
    assertEquals("en", com.example.util.LanguageManager.resolveSupportedTag("en-US"))
    assertEquals("bn", com.example.util.LanguageManager.resolveSupportedTag("bn-BD"))
    assertEquals("hi", com.example.util.LanguageManager.resolveSupportedTag("hi-IN"))
    assertEquals("ta", com.example.util.LanguageManager.resolveSupportedTag("ta-IN"))
    assertEquals("ar", com.example.util.LanguageManager.resolveSupportedTag("ar-SA"))
    // Unsupported locales fall back to English
    assertEquals("en", com.example.util.LanguageManager.resolveSupportedTag("fr-FR"))
    assertEquals("en", com.example.util.LanguageManager.resolveSupportedTag("es"))
    assertEquals("en", com.example.util.LanguageManager.resolveSupportedTag(""))
    assertEquals("en", com.example.util.LanguageManager.resolveSupportedTag(null))

    // Profile language normalization
    assertEquals("system", com.example.util.LanguageManager.normalizeProfileLanguage("system"))
    assertEquals("system", com.example.util.LanguageManager.normalizeProfileLanguage("fr"))
    assertEquals("bn", com.example.util.LanguageManager.normalizeProfileLanguage("bn-BD"))
    assertEquals("hi", com.example.util.LanguageManager.normalizeProfileLanguage("HI"))
    assertEquals("ta", com.example.util.LanguageManager.normalizeProfileLanguage("ta"))
    assertEquals("ar", com.example.util.LanguageManager.normalizeProfileLanguage("ar-EG"))

    // Bengali digits only for bn
    assertTrue(com.example.util.LanguageManager.isBengaliDigits("bn"))
    assertFalse(com.example.util.LanguageManager.isBengaliDigits("en"))
    assertFalse(com.example.util.LanguageManager.isBengaliDigits("hi"))
    assertFalse(com.example.util.LanguageManager.isBengaliDigits("ta"))
    assertFalse(com.example.util.LanguageManager.isBengaliDigits("ar"))
  }

  @Test
  fun testLanguageManager_formatYearMonthAllFiveLanguages() {
    val en = com.example.util.LanguageManager.formatYearMonth("2026-11", "en")
    val bn = com.example.util.LanguageManager.formatYearMonth("2026-11", "bn")
    val hi = com.example.util.LanguageManager.formatYearMonth("2026-11", "hi")
    val ta = com.example.util.LanguageManager.formatYearMonth("2026-11", "ta")
    val ar = com.example.util.LanguageManager.formatYearMonth("2026-11", "ar")
    val fallback = com.example.util.LanguageManager.formatYearMonth("2026-11", "fr")

    assertTrue(en.contains("2026"))
    assertTrue(bn.contains("২০২৬"))
    assertTrue(hi.contains("2026"))
    assertTrue(ta.contains("2026"))
    assertTrue(ar.contains("2026"))
    assertEquals(en, fallback)
  }

  @Test
  fun testLanguageManager_bengaliFirstInstallDefaultAndUpgradeSafety() {
    assertEquals("bn", com.example.util.LanguageManager.DEFAULT_INSTALL_TAG)
    assertEquals("en", com.example.util.LanguageManager.FALLBACK_TAG)
    assertEquals("bn", com.example.data.model.UserProfile().language)

    // All 5 languages remain supported with Bengali listed first
    val tags = com.example.util.LanguageManager.SupportedLanguage.entries.map { it.tag }
    assertEquals(listOf("bn", "en", "hi", "ta", "ar"), tags)

    // True only on brand-new fresh install
    assertTrue(
      com.example.util.LanguageManager.shouldApplyDefaultInstallLanguage(
        currentAppSetting = "system",
        hasDefaultInitializedFlag = false,
        hasLegacyV2MigratedFlag = false,
        hasExistingDatabase = false
      )
    )

    // Existing v26+ user on System Default must remain untouched
    assertFalse(
      com.example.util.LanguageManager.shouldApplyDefaultInstallLanguage(
        currentAppSetting = "system",
        hasDefaultInitializedFlag = false,
        hasLegacyV2MigratedFlag = true,
        hasExistingDatabase = false
      )
    )

    // Existing legacy user with local database must remain untouched
    assertFalse(
      com.example.util.LanguageManager.shouldApplyDefaultInstallLanguage(
        currentAppSetting = "system",
        hasDefaultInitializedFlag = false,
        hasLegacyV2MigratedFlag = false,
        hasExistingDatabase = true
      )
    )

    // User who later switches from Bengali back to System Default must remain on System Default
    assertFalse(
      com.example.util.LanguageManager.shouldApplyDefaultInstallLanguage(
        currentAppSetting = "system",
        hasDefaultInitializedFlag = true,
        hasLegacyV2MigratedFlag = true,
        hasExistingDatabase = true
      )
    )

    // Existing explicit language selection must remain untouched
    assertFalse(
      com.example.util.LanguageManager.shouldApplyDefaultInstallLanguage(
        currentAppSetting = "en",
        hasDefaultInitializedFlag = false,
        hasLegacyV2MigratedFlag = false,
        hasExistingDatabase = false
      )
    )
  }

  @Test
  fun testComputeMonthlySummary_pastSnapshotIdentityIsolationAndLegacyFallback() {
    val activeProfile = com.example.data.model.UserProfile(
      userName = "Rahim Updated",
      companyName = "New Knitwear Ltd",
      designation = "Senior Operator",
      workerId = "EMP-999",
      grade = "Grade 2",
      basicSalary = 15000.0
    )

    // Snapshot with identity captured where grade/workerId/designation were intentionally left blank
    val isolatedPastSnapshot = com.example.data.model.MonthlySalarySettings(
      yearMonth = "2026-10",
      basicSalary = 12000.0,
      grossSalary = 20450.0,
      hourlyOtRate = 115.38,
      userName = "Rahim",
      companyName = "Old Factory",
      designation = "",
      workerId = "",
      grade = "",
      isLocked = true,
      updatedAt = 1791000000000L
    )

    val isolatedSummary = com.example.ui.viewmodel.computeMonthlySummary(
      selectedYearMonth = "2026-10",
      currentCalendarYm = "2026-11",
      profile = activeProfile,
      monthSnapshot = isolatedPastSnapshot,
      otList = emptyList(),
      pieceList = emptyList(),
      deductionList = emptyList()
    )

    assertEquals("Rahim", isolatedSummary.userName)
    assertEquals("Old Factory", isolatedSummary.companyName)
    assertEquals("", isolatedSummary.designation)
    assertEquals("", isolatedSummary.workerId)
    assertEquals("", isolatedSummary.grade)

    // Legacy pre-v9 snapshot where all 5 identity fields were never populated
    val legacyPastSnapshot = com.example.data.model.MonthlySalarySettings(
      yearMonth = "2026-08",
      basicSalary = 10400.0,
      grossSalary = 17850.0,
      hourlyOtRate = 100.0,
      isLocked = true,
      updatedAt = 1786000000000L
    )

    val legacySummary = com.example.ui.viewmodel.computeMonthlySummary(
      selectedYearMonth = "2026-08",
      currentCalendarYm = "2026-11",
      profile = activeProfile,
      monthSnapshot = legacyPastSnapshot,
      otList = emptyList(),
      pieceList = emptyList(),
      deductionList = emptyList()
    )

    assertEquals("Rahim Updated", legacySummary.userName)
    assertEquals("New Knitwear Ltd", legacySummary.companyName)
    assertEquals("Senior Operator", legacySummary.designation)
    assertEquals("EMP-999", legacySummary.workerId)
    assertEquals("Grade 2", legacySummary.grade)
  }

  @Test
  fun testComputeMonthlySummary_customZeroBasicSnapshotStaysIsolated() {
    val activeLaborProfile = com.example.data.model.UserProfile(
      userName = "Karim",
      isCustomSalary = false,
      basicSalary = 15000.0,
      medicalAllowance = 750.0,
      customOtRate = 0.0,
      nightBillRate = 250.0,
      monthlyOtTargetHours = 90.0
    )
    val zeroBasicCustomSnapshot = com.example.data.model.MonthlySalarySettings(
      yearMonth = "2026-10",
      basicSalary = 0.0,
      houseRent = 0.0,
      medicalAllowance = 0.0,
      conveyanceAllowance = 0.0,
      foodAllowance = 0.0,
      grossSalary = 0.0,
      hourlyOtRate = 95.0,
      customOtRate = 95.0,
      nightBillRate = 120.0,
      monthlyOtTargetHours = 40.0,
      isCustomSalary = true,
      userName = "Karim",
      isLocked = true,
      updatedAt = 1791000000000L
    )

    val summary = com.example.ui.viewmodel.computeMonthlySummary(
      selectedYearMonth = "2026-10",
      currentCalendarYm = "2026-11",
      profile = activeLaborProfile,
      monthSnapshot = zeroBasicCustomSnapshot,
      otList = emptyList(),
      pieceList = emptyList(),
      deductionList = emptyList()
    )

    assertTrue(summary.isCustomSalary)
    assertEquals(0.0, summary.basicSalary, 0.001)
    assertEquals(0.0, summary.houseRent, 0.001)
    assertEquals(0.0, summary.medicalAllowance, 0.001)
    assertEquals(0.0, summary.conveyanceAllowance, 0.001)
    assertEquals(0.0, summary.foodAllowance, 0.001)
    assertEquals(0.0, summary.grossSalary, 0.001)
    assertEquals(95.0, summary.hourlyOtRate, 0.001)
    assertEquals(95.0, summary.customOtRate, 0.001)
    assertEquals(120.0, summary.effectiveNightRate, 0.001)
    assertEquals(40.0, summary.targetHours, 0.001)
  }

  @Test
  fun testSalaryFieldHelpTopic_coversAllEightEditableFieldsWithUniqueTagsAndResources() {
    val topics = com.example.ui.screens.SalaryFieldHelpTopic.entries
    assertEquals(8, topics.size)

    val titles = topics.map { it.titleRes }
    val descs = topics.map { it.descRes }
    val examples = topics.map { it.exampleRes }
    val tags = topics.map { it.buttonTestTag }

    assertEquals(8, titles.distinct().size)
    assertEquals(8, descs.distinct().size)
    assertEquals(8, examples.distinct().size)
    assertEquals(8, tags.distinct().size)

    val snakeCaseRegex = Regex("^[a-z0-9_]+$")
    topics.forEach { topic ->
      assertTrue(topic.titleRes != 0)
      assertTrue(topic.descRes != 0)
      assertTrue(topic.exampleRes != 0)
      assertTrue("Expected snake_case testTag for ${topic.name}", snakeCaseRegex.matches(topic.buttonTestTag))
    }
  }

  @Test
  fun testInterstitialAdGatePolicy_enforcesOnlineInitCooldownDebugIdAndShowGuards() {
    // Debug uses Google test interstitial ID; Release uses General-Interstitial production ID
    assertEquals(
      "ca-app-pub-3940256099942544/1033173712",
      com.example.util.InterstitialAdGatePolicy.resolveAdUnitId(isDebug = true)
    )
    assertEquals(
      "ca-app-pub-9027708775227838/5097750056",
      com.example.util.InterstitialAdGatePolicy.resolveAdUnitId(isDebug = false)
    )
    assertEquals(8_000L, com.example.util.InterstitialAdGatePolicy.AD_LOAD_TIMEOUT_MS)

    // Banner-parity internet capability check works even when VALIDATED flag is false
    assertTrue(com.example.util.NetworkMonitor.hasInternetCapability(true))
    assertFalse(com.example.util.NetworkMonitor.hasInternetCapability(false))

    // Offline -> skip ad request completely and allow direct PDF download
    assertFalse(
      com.example.util.InterstitialAdGatePolicy.shouldRequestInterstitial(
        hasInternet = false,
        nowMs = 1_000_000L,
        lastShownAtMs = 0L
      )
    )

    // Online + first click -> eligible (SDK recovers on-demand if boot init lagged)
    assertTrue(
      com.example.util.InterstitialAdGatePolicy.shouldRequestInterstitial(
        hasInternet = true,
        nowMs = 1_000_000L,
        lastShownAtMs = 0L
      )
    )

    // Within 60s cooldown -> blocked to prevent repetitive interstitial policy violation
    assertFalse(
      com.example.util.InterstitialAdGatePolicy.shouldRequestInterstitial(
        hasInternet = true,
        nowMs = 1_030_000L,
        lastShownAtMs = 1_000_000L
      )
    )

    // After 60s cooldown -> eligible again
    assertTrue(
      com.example.util.InterstitialAdGatePolicy.shouldRequestInterstitial(
        hasInternet = true,
        nowMs = 1_060_000L,
        lastShownAtMs = 1_000_000L
      )
    )

    // Loaded ad show guards: must be alive, resumed, and not timed out / cancelled
    assertTrue(
      com.example.util.InterstitialAdGatePolicy.canShowLoadedAd(
        isActivityAlive = true,
        isActivityResumed = true,
        isTimedOutOrCancelled = false
      )
    )
    assertFalse(
      com.example.util.InterstitialAdGatePolicy.canShowLoadedAd(
        isActivityAlive = true,
        isActivityResumed = false,
        isTimedOutOrCancelled = false
      )
    )
    assertFalse(
      com.example.util.InterstitialAdGatePolicy.canShowLoadedAd(
        isActivityAlive = false,
        isActivityResumed = true,
        isTimedOutOrCancelled = false
      )
    )
    assertFalse(
      com.example.util.InterstitialAdGatePolicy.canShowLoadedAd(
        isActivityAlive = true,
        isActivityResumed = true,
        isTimedOutOrCancelled = true
      )
    )

    // Diagnostics appear ONLY in Debug builds, never in Release builds
    val failedOutcome = com.example.util.InterstitialOutcome.LoadFailed(3, "No fill")
    assertNotNull(com.example.util.InterstitialAdGatePolicy.formatDebugDiagnostic(failedOutcome, isDebug = true))
    assertNull(com.example.util.InterstitialAdGatePolicy.formatDebugDiagnostic(failedOutcome, isDebug = false))
    assertNull(
      com.example.util.InterstitialAdGatePolicy.formatDebugDiagnostic(
        com.example.util.InterstitialOutcome.ShownAndDismissed,
        isDebug = true
      )
    )
  }
}


