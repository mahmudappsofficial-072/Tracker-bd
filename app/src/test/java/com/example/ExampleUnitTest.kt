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
      "ca-app-pub-9027708775227838/6430017173",
      com.example.util.InterstitialAdGatePolicy.resolveAdUnitId(isDebug = false)
    )
    assertEquals(10_000L, com.example.util.InterstitialAdGatePolicy.AD_LOAD_TIMEOUT_MS)

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

    // Session cache TTL: valid within 50 minutes, invalid at/after 50 minutes or 0ms
    assertFalse(com.example.util.InterstitialAdGatePolicy.isCachedAdValid(0L, 1_000_000L))
    assertTrue(com.example.util.InterstitialAdGatePolicy.isCachedAdValid(1_000_000L, 1_000_000L + 49L * 60_000L))
    assertFalse(com.example.util.InterstitialAdGatePolicy.isCachedAdValid(1_000_000L, 1_000_000L + 50L * 60_000L))

    // Intent-scoped Pay Slip preload guards protect AdMob Show Rate
    assertTrue(
      com.example.util.InterstitialAdGatePolicy.shouldPreloadOnPaySlip(
        hasInternet = true,
        hasValidCache = false,
        isPreloadInFlight = false,
        nowMs = 2_000_000L,
        lastShownAtMs = 0L
      )
    )
    assertFalse(
      com.example.util.InterstitialAdGatePolicy.shouldPreloadOnPaySlip(
        hasInternet = true,
        hasValidCache = true,
        isPreloadInFlight = false,
        nowMs = 2_000_000L,
        lastShownAtMs = 0L
      )
    )
    assertFalse(
      com.example.util.InterstitialAdGatePolicy.shouldPreloadOnPaySlip(
        hasInternet = true,
        hasValidCache = false,
        isPreloadInFlight = true,
        nowMs = 2_000_000L,
        lastShownAtMs = 0L
      )
    )
    assertEquals(600L, com.example.util.InterstitialAdGatePolicy.PRELOAD_DEBOUNCE_MS)
    assertEquals(15_000L, com.example.util.InterstitialAdGatePolicy.PRELOAD_FAILURE_BACKOFF_MS)
    assertFalse(
      com.example.util.InterstitialAdGatePolicy.shouldPreloadOnPaySlip(
        hasInternet = true,
        hasValidCache = false,
        isPreloadInFlight = false,
        nowMs = 2_010_000L,
        lastShownAtMs = 0L,
        lastPreloadFailedAtMs = 2_000_000L
      )
    )
    assertTrue(
      com.example.util.InterstitialAdGatePolicy.shouldPreloadOnPaySlip(
        hasInternet = true,
        hasValidCache = false,
        isPreloadInFlight = false,
        nowMs = 2_016_000L,
        lastShownAtMs = 0L,
        lastPreloadFailedAtMs = 2_000_000L
      )
    )

    // Retry policy: 1 retry on transient network/internal errors AND first NO_FILL, never on attempt > 0
    assertTrue(
      com.example.util.InterstitialAdGatePolicy.shouldRetryTransientLoadError(
        com.google.android.gms.ads.AdRequest.ERROR_CODE_NETWORK_ERROR,
        attemptIndex = 0
      )
    )
    assertTrue(
      com.example.util.InterstitialAdGatePolicy.shouldRetryTransientLoadError(
        com.google.android.gms.ads.AdRequest.ERROR_CODE_INTERNAL_ERROR,
        attemptIndex = 0
      )
    )
    assertTrue(
      com.example.util.InterstitialAdGatePolicy.shouldRetryTransientLoadError(
        com.google.android.gms.ads.AdRequest.ERROR_CODE_NO_FILL,
        attemptIndex = 0
      )
    )
    assertFalse(
      com.example.util.InterstitialAdGatePolicy.shouldRetryTransientLoadError(
        com.google.android.gms.ads.AdRequest.ERROR_CODE_NO_FILL,
        attemptIndex = 1
      )
    )
    assertFalse(
      com.example.util.InterstitialAdGatePolicy.shouldRetryTransientLoadError(
        com.google.android.gms.ads.AdRequest.ERROR_CODE_NETWORK_ERROR,
        attemptIndex = 1
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
    val failedOutcome = com.example.util.InterstitialOutcome.LoadFailed(
      code = 3,
      message = "No fill",
      adUnitSuffix = "017173",
      responseDetail = "AdMobAdapter:3"
    )
    val debugMsg = com.example.util.InterstitialAdGatePolicy.formatDebugDiagnostic(failedOutcome, isDebug = true)
    assertNotNull(debugMsg)
    assertTrue(debugMsg!!.contains("017173"))
    assertTrue(debugMsg.contains("AdMobAdapter:3"))
    assertNull(com.example.util.InterstitialAdGatePolicy.formatDebugDiagnostic(failedOutcome, isDebug = false))
    assertNull(
      com.example.util.InterstitialAdGatePolicy.formatDebugDiagnostic(
        com.example.util.InterstitialOutcome.ShownAndDismissed,
        isDebug = true
      )
    )
  }

  @Test
  fun testSalaryOnboardingPolicy_enforcesPriorityQueueAndBlocksReminderWithoutSalary() {
    // 1. Salary configuration detection & mode guide auto-open policy across Auto and Custom modes
    assertFalse(com.example.util.SalaryOnboardingPolicy.isSalaryConfigured(com.example.data.model.UserProfile()))
    assertTrue(com.example.util.SalaryOnboardingPolicy.shouldAutoShowModeGuide(com.example.data.model.UserProfile()))
    assertTrue(
      com.example.util.SalaryOnboardingPolicy.isSalaryConfigured(
        com.example.data.model.UserProfile(basicSalary = 10000.0, isCustomSalary = false)
      )
    )
    assertFalse(
      com.example.util.SalaryOnboardingPolicy.shouldAutoShowModeGuide(
        com.example.data.model.UserProfile(basicSalary = 10000.0, isCustomSalary = false)
      )
    )
    assertTrue(
      com.example.util.SalaryOnboardingPolicy.isSalaryConfigured(
        com.example.data.model.UserProfile(basicSalary = 0.0, isCustomSalary = true, customOtRate = 75.0)
      )
    )
    assertFalse(
      com.example.util.SalaryOnboardingPolicy.shouldAutoShowModeGuide(
        com.example.data.model.UserProfile(basicSalary = 0.0, isCustomSalary = true, customOtRate = 75.0)
      )
    )
    assertTrue(
      com.example.util.SalaryOnboardingPolicy.isSalaryConfigured(
        com.example.data.model.UserProfile(basicSalary = 15000.0, isCustomSalary = true, customOtRate = 0.0)
      )
    )
    assertFalse(
      com.example.util.SalaryOnboardingPolicy.isSalaryConfigured(
        com.example.data.model.UserProfile(basicSalary = 0.0, isCustomSalary = true, customOtRate = 0.0)
      )
    )
    assertTrue(
      com.example.util.SalaryOnboardingPolicy.shouldAutoShowModeGuide(
        com.example.data.model.UserProfile(basicSalary = 0.0, isCustomSalary = true, customOtRate = 0.0)
      )
    )

    val readyUnconfigured = com.example.util.StartupPopupGateState(
      isAppReady = true,
      isProfileLoaded = true,
      isInitialUpdateCheckSettled = true,
      isAuthLoading = false,
      isSigningOut = false,
      hasUpdateDialog = false,
      hasConsentDialog = false,
      hasQuickOtTrigger = false,
      isAnySheetOrDialogOpen = false,
      currentTab = 0,
      isSalaryConfigured = false,
      hasCheckedMissingSalaryThisSession = false,
      hasCheckedMissingOtThisSession = false
    )

    // 2. Tier 0 readiness gates prevent premature false-positive popup
    assertEquals(
      com.example.util.StartupPopupAction.NONE,
      com.example.util.SalaryOnboardingPolicy.evaluateStartupPopupAction(readyUnconfigured.copy(isProfileLoaded = false))
    )
    assertEquals(
      com.example.util.StartupPopupAction.NONE,
      com.example.util.SalaryOnboardingPolicy.evaluateStartupPopupAction(readyUnconfigured.copy(isInitialUpdateCheckSettled = false))
    )
    assertEquals(
      com.example.util.StartupPopupAction.NONE,
      com.example.util.SalaryOnboardingPolicy.evaluateStartupPopupAction(readyUnconfigured.copy(isAuthLoading = true))
    )

    // 3. Tier 1 Update / Consent / QuickOT block lower tiers, and clearing Optional Update unlocks Salary Guide
    assertEquals(
      com.example.util.StartupPopupAction.NONE,
      com.example.util.SalaryOnboardingPolicy.evaluateStartupPopupAction(readyUnconfigured.copy(hasUpdateDialog = true))
    )
    assertEquals(
      com.example.util.StartupPopupAction.SHOW_SALARY_GUIDE,
      com.example.util.SalaryOnboardingPolicy.evaluateStartupPopupAction(readyUnconfigured)
    )

    // 4. Hard rule: When salary is NOT configured, Missing OT Reminder must NEVER trigger (even after dismissal or on tab switch)
    assertEquals(
      com.example.util.StartupPopupAction.NONE,
      com.example.util.SalaryOnboardingPolicy.evaluateStartupPopupAction(
        readyUnconfigured.copy(hasCheckedMissingSalaryThisSession = true, hasCheckedMissingOtThisSession = false)
      )
    )
    assertEquals(
      com.example.util.StartupPopupAction.NONE,
      com.example.util.SalaryOnboardingPolicy.evaluateStartupPopupAction(
        readyUnconfigured.copy(currentTab = 3)
      )
    )

    // 5. When salary IS configured and no update/blocker is active, Missing OT Reminder runs once
    val readyConfigured = readyUnconfigured.copy(isSalaryConfigured = true)
    assertEquals(
      com.example.util.StartupPopupAction.CHECK_MISSING_OT,
      com.example.util.SalaryOnboardingPolicy.evaluateStartupPopupAction(readyConfigured)
    )
    assertEquals(
      com.example.util.StartupPopupAction.NONE,
      com.example.util.SalaryOnboardingPolicy.evaluateStartupPopupAction(readyConfigured.copy(hasUpdateDialog = true))
    )
    assertEquals(
      com.example.util.StartupPopupAction.NONE,
      com.example.util.SalaryOnboardingPolicy.evaluateStartupPopupAction(readyConfigured.copy(hasCheckedMissingOtThisSession = true))
    )
  }

  @Test
  fun testCloudSyncPolicy_enforcesZeroCostGatesDeltaRestoreAndConditionalTimestamp() {
    // 1. Startup gate blocks offline, signed-out, and unconsented users
    val offline = com.example.util.CloudSyncPolicy.evaluateStartupSyncDecision(
      isSignedIn = true,
      hasPrivacyConsent = true,
      isOnline = false,
      hasCompletedInitialRestoreForUid = true,
      hasPendingLocalDirtyData = true
    )
    assertFalse(offline.shouldRunSync)
    assertEquals(com.example.util.StartupSyncReason.OFFLINE, offline.reason)

    val signedOut = com.example.util.CloudSyncPolicy.evaluateStartupSyncDecision(
      isSignedIn = false,
      hasPrivacyConsent = true,
      isOnline = true,
      hasCompletedInitialRestoreForUid = false,
      hasPendingLocalDirtyData = false
    )
    assertFalse(signedOut.shouldRunSync)
    assertEquals(com.example.util.StartupSyncReason.NOT_SIGNED_IN, signedOut.reason)

    val noConsent = com.example.util.CloudSyncPolicy.evaluateStartupSyncDecision(
      isSignedIn = true,
      hasPrivacyConsent = false,
      isOnline = true,
      hasCompletedInitialRestoreForUid = false,
      hasPendingLocalDirtyData = false
    )
    assertFalse(noConsent.shouldRunSync)
    assertEquals(com.example.util.StartupSyncReason.NO_CONSENT, noConsent.reason)

    // 2. Startup triggers initial restore on fresh install, pending dirty flush when queued, or 1-doc timestamp check otherwise
    assertEquals(
      com.example.util.StartupSyncReason.NEEDS_INITIAL_RESTORE,
      com.example.util.CloudSyncPolicy.evaluateStartupSyncDecision(true, true, true, false, false).reason
    )
    assertEquals(
      com.example.util.StartupSyncReason.HAS_PENDING_LOCAL_CHANGES,
      com.example.util.CloudSyncPolicy.evaluateStartupSyncDecision(true, true, true, true, true).reason
    )
    assertEquals(
      com.example.util.StartupSyncReason.SERVER_TIMESTAMP_CHECK_ONLY,
      com.example.util.CloudSyncPolicy.evaluateStartupSyncDecision(true, true, true, true, false).reason
    )

    // 3. Mid-session reconnect runs ONLY if initial restore unfinished or local dirty queue exists (0 ops when synced)
    val reconnectSynced = com.example.util.CloudSyncPolicy.evaluateReconnectSyncDecision(
      isSignedIn = true,
      hasPrivacyConsent = true,
      isOnline = true,
      hasCompletedInitialRestoreForUid = true,
      hasPendingLocalDirtyData = false
    )
    assertFalse(reconnectSynced.shouldRunSync)
    assertEquals(com.example.util.StartupSyncReason.NO_PENDING_RECONNECT_WORK, reconnectSynced.reason)

    val reconnectDirty = com.example.util.CloudSyncPolicy.evaluateReconnectSyncDecision(
      isSignedIn = true,
      hasPrivacyConsent = true,
      isOnline = true,
      hasCompletedInitialRestoreForUid = true,
      hasPendingLocalDirtyData = true
    )
    assertTrue(reconnectDirty.shouldRunSync)
    assertEquals(com.example.util.StartupSyncReason.HAS_PENDING_LOCAL_CHANGES, reconnectDirty.reason)

    // 4. Collection restore plan: FULL before initial restore, NONE when cloudSyncTime <= known, DELTA with 60s overlap when newer
    val fullPlan = com.example.util.CloudSyncPolicy.evaluateCollectionRestorePlan(
      forceFullSync = false,
      hasCompletedInitialRestore = false,
      cloudSyncTime = 1_000_000L,
      lastKnownCloudSyncTime = 1_000_000L,
      otWatermark = 900_000L,
      pieceWatermark = 900_000L,
      monthlyWatermark = 900_000L,
      deductionWatermark = 900_000L,
      trashWatermark = 900_000L
    )
    assertEquals(com.example.util.RestoreMode.FULL, fullPlan.restoreMode)
    assertNull(fullPlan.otSince)
    assertNull(fullPlan.deductionSince)

    val nonePlan = com.example.util.CloudSyncPolicy.evaluateCollectionRestorePlan(
      forceFullSync = false,
      hasCompletedInitialRestore = true,
      cloudSyncTime = 1_000_000L,
      lastKnownCloudSyncTime = 1_000_000L,
      otWatermark = 900_000L,
      pieceWatermark = 900_000L,
      monthlyWatermark = 900_000L,
      deductionWatermark = 900_000L,
      trashWatermark = 900_000L
    )
    assertEquals(com.example.util.RestoreMode.NONE, nonePlan.restoreMode)
    assertFalse(nonePlan.shouldDownloadAny)

    val deltaPlan = com.example.util.CloudSyncPolicy.evaluateCollectionRestorePlan(
      forceFullSync = false,
      hasCompletedInitialRestore = true,
      cloudSyncTime = 2_000_000L,
      lastKnownCloudSyncTime = 1_000_000L,
      otWatermark = 500_000L,
      pieceWatermark = 0L,
      monthlyWatermark = 400_000L,
      deductionWatermark = 10_000L,
      trashWatermark = 800_000L
    )
    assertEquals(com.example.util.RestoreMode.DELTA, deltaPlan.restoreMode)
    assertEquals(440_000L, deltaPlan.otSince)
    assertNull(deltaPlan.pieceSince) // uninitialized collection watermark falls back safely to full
    assertEquals(340_000L, deltaPlan.monthlySince)
    assertEquals(0L, deltaPlan.deductionSince)
    assertEquals(740_000L, deltaPlan.trashSince)

    // 5. Monotonic timestamp handles clock skew across devices
    assertEquals(2_000_001L, com.example.util.CloudSyncPolicy.computeMonotonicSyncTime(1_500_000L, 1_900_000L, 2_000_000L))
    assertEquals(3_000_000L, com.example.util.CloudSyncPolicy.computeMonotonicSyncTime(3_000_000L, 2_000_000L))

    // 6. Strict conditional "Auto Cloud Sync" UI display timestamp rule
    val previousUiTime = 1_700_000_000_000L
    assertEquals(
      previousUiTime,
      com.example.util.CloudSyncPolicy.resolveDisplayedSyncTimestamp(
        previousSyncTime = previousUiTime,
        transferOccurred = false,
        transferTimeMillis = 1_800_000_000_000L
      )
    )
    assertEquals(
      1_800_000_000_000L,
      com.example.util.CloudSyncPolicy.resolveDisplayedSyncTimestamp(
        previousSyncTime = previousUiTime,
        transferOccurred = true,
        transferTimeMillis = 1_800_000_000_000L
      )
    )

    // 7. Legacy upgrade watermark seeding for existing synced users
    assertTrue(com.example.util.CloudSyncPolicy.shouldSeedLegacyUpgradeWatermark(false, 1_700_000_000_000L))
    assertFalse(com.example.util.CloudSyncPolicy.shouldSeedLegacyUpgradeWatermark(false, 0L))
    assertFalse(com.example.util.CloudSyncPolicy.shouldSeedLegacyUpgradeWatermark(true, 1_700_000_000_000L))
  }

  @Test
  fun testResponsiveDensityTiersAndCompactSpacingScale() {
    assertEquals(
      com.example.util.ScreenDensityTier.MICRO_COMPACT,
      com.example.util.ResponsiveUtils.classifyDensityTier(320, 568, 1.0f)
    )
    assertEquals(
      com.example.util.ScreenDensityTier.MICRO_COMPACT,
      com.example.util.ResponsiveUtils.classifyDensityTier(360, 590, 1.0f)
    )
    assertEquals(
      com.example.util.ScreenDensityTier.MICRO_COMPACT,
      com.example.util.ResponsiveUtils.classifyDensityTier(350, 680, 1.2f)
    )
    assertEquals(
      com.example.util.ScreenDensityTier.EXTRA_COMPACT,
      com.example.util.ResponsiveUtils.classifyDensityTier(360, 740, 1.0f)
    )
    assertEquals(
      com.example.util.ScreenDensityTier.EXTRA_COMPACT,
      com.example.util.ResponsiveUtils.classifyDensityTier(411, 680, 1.0f)
    )
    assertEquals(
      com.example.util.ScreenDensityTier.EXTRA_COMPACT,
      com.example.util.ResponsiveUtils.classifyDensityTier(411, 890, 1.2f)
    )
    assertEquals(
      com.example.util.ScreenDensityTier.STANDARD_COMPACT,
      com.example.util.ResponsiveUtils.classifyDensityTier(411, 890, 1.0f)
    )

    val standard = com.example.util.ResponsiveUtils.spacingFor(411, 890, 1.0f)
    val extra = com.example.util.ResponsiveUtils.spacingFor(360, 680, 1.0f)
    val micro = com.example.util.ResponsiveUtils.spacingFor(320, 568, 1.0f)

    assertEquals(androidx.compose.ui.unit.Dp(12f), standard.screenH)
    assertEquals(androidx.compose.ui.unit.Dp(10f), standard.screenV)
    assertEquals(androidx.compose.ui.unit.Dp(14f), standard.heroCardPadding)
    assertEquals(androidx.compose.ui.unit.Dp(12f), standard.cardPadding)
    assertEquals(androidx.compose.ui.unit.Dp(8f), standard.pillCardPadding)
    assertEquals(androidx.compose.ui.unit.Dp(20f), standard.heroRadius)
    assertEquals(androidx.compose.ui.unit.Dp(16f), standard.cardRadius)
    assertEquals(androidx.compose.ui.unit.Dp(12f), standard.progressHeight)
    assertEquals(androidx.compose.ui.unit.Dp(44f), standard.actionCircleSize)
    assertEquals(androidx.compose.ui.unit.Dp(48f), standard.compactButtonHeight)
    assertEquals(androidx.compose.ui.unit.Dp(48f), standard.minTouchTarget)
    assertFalse(standard.isCompact)
    assertFalse(standard.isShortScreen)

    assertTrue(micro.screenH <= extra.screenH && extra.screenH <= standard.screenH)
    assertTrue(micro.heroCardPadding <= extra.heroCardPadding && extra.heroCardPadding <= standard.heroCardPadding)
    assertTrue(micro.avatarSize <= extra.avatarSize && extra.avatarSize <= standard.avatarSize)
    assertTrue(micro.progressHeight <= extra.progressHeight && extra.progressHeight <= standard.progressHeight)
    assertTrue(micro.actionCircleSize <= extra.actionCircleSize && extra.actionCircleSize <= standard.actionCircleSize)
    assertTrue(micro.compactButtonHeight <= extra.compactButtonHeight && extra.compactButtonHeight <= standard.compactButtonHeight)
    assertTrue(
      micro.adaptiveSp(22f, 18f, 15.5f).value <= extra.adaptiveSp(22f, 18f, 15.5f).value &&
        extra.adaptiveSp(22f, 18f, 15.5f).value <= standard.adaptiveSp(22f, 18f, 15.5f).value
    )
    assertTrue(com.example.util.ResponsiveUtils.spacingFor(411, 650, 1.0f).isShortScreen)
  }

  @Test
  fun testTargetProgressColorInterpolationAndCompletion() {
    val light0 = com.example.util.TargetProgressColorHelper.resolveTargetProgressColor(0.0f, false, false)
    val light22 = com.example.util.TargetProgressColorHelper.resolveTargetProgressColor(0.22f, false, false)
    val light48 = com.example.util.TargetProgressColorHelper.resolveTargetProgressColor(0.48f, false, false)
    val light75 = com.example.util.TargetProgressColorHelper.resolveTargetProgressColor(0.75f, false, false)
    val light95 = com.example.util.TargetProgressColorHelper.resolveTargetProgressColor(0.95f, false, false)
    val light100 = com.example.util.TargetProgressColorHelper.resolveTargetProgressColor(1.0f, true, false)

    assertEquals(com.example.util.TargetProgressColorHelper.LightRedStop, light0)
    assertEquals(com.example.util.TargetProgressColorHelper.LightOrangeStop, light22)
    assertEquals(com.example.util.TargetProgressColorHelper.LightAmberStop, light48)
    assertEquals(com.example.util.TargetProgressColorHelper.LightBlueStop, light75)
    assertEquals(com.example.util.TargetProgressColorHelper.LightGreenStop, light100)

    // Distinct smooth intermediate transitions (never stuck on one color)
    assertNotEquals(light0, light22)
    assertNotEquals(light22, light48)
    assertNotEquals(light48, light75)
    assertNotEquals(light75, light95)
    assertNotEquals(light95, light100)
    assertTrue(light95.blue > light95.red) // Still distinctly blue just before completion
    assertTrue(light100.green > light100.red && light100.green > light100.blue) // Green at completion

    // Dark theme uses high-contrast dark stops
    val dark0 = com.example.util.TargetProgressColorHelper.resolveTargetProgressColor(0.0f, false, true)
    val dark100 = com.example.util.TargetProgressColorHelper.resolveTargetProgressColor(1.0f, true, true)
    assertEquals(com.example.util.TargetProgressColorHelper.DarkRedStop, dark0)
    assertEquals(com.example.util.TargetProgressColorHelper.DarkGreenStop, dark100)
    assertNotEquals(light0, dark0)
    assertNotEquals(light100, dark100)
  }

  @Test
  fun testPerMonthTargetIsolation_futureRunningPastAndUntouchedMonths() {
    val defaultProfile = com.example.data.model.UserProfile(
      userName = "Worker One",
      companyName = "Apex Garments",
      basicSalary = 10400.0,
      attendanceBonus = 1000.0,
      maxAbsentForBonus = 1,
      nightBillRate = 120.0,
      tiffinBillRate = 60.0,
      monthlyOtTargetHours = 70.0
    )
    val runningYm = "2026-11"
    val octExisting = com.example.ui.viewmodel.buildTargetUpdatedSnapshot(
      targetYm = "2026-10",
      currentCalendarYm = runningYm,
      newTargetHours = 60.0,
      existingSnapshot = null,
      baselineProfile = defaultProfile,
      nowMillis = 1000L
    )
    val novFrozen = com.example.ui.viewmodel.buildTargetUpdatedSnapshot(
      targetYm = "2026-11",
      currentCalendarYm = runningYm,
      newTargetHours = 80.0,
      existingSnapshot = null,
      baselineProfile = defaultProfile,
      nowMillis = 2000L
    )

    // User navigates to future December (2026-12) and sets target to 120 hrs
    val decSnapshot = com.example.ui.viewmodel.buildTargetUpdatedSnapshot(
      targetYm = "2026-12",
      currentCalendarYm = runningYm,
      newTargetHours = 120.0,
      existingSnapshot = null,
      baselineProfile = defaultProfile,
      nowMillis = 3000L
    )

    val decSummary = com.example.ui.viewmodel.computeMonthlySummary(
      selectedYearMonth = "2026-12",
      currentCalendarYm = runningYm,
      profile = defaultProfile,
      monthSnapshot = decSnapshot,
      otList = emptyList(),
      pieceList = emptyList(),
      deductionList = emptyList()
    )
    val novSummary = com.example.ui.viewmodel.computeMonthlySummary(
      selectedYearMonth = "2026-11",
      currentCalendarYm = runningYm,
      profile = defaultProfile,
      monthSnapshot = novFrozen,
      otList = emptyList(),
      pieceList = emptyList(),
      deductionList = emptyList()
    )
    val octSummary = com.example.ui.viewmodel.computeMonthlySummary(
      selectedYearMonth = "2026-10",
      currentCalendarYm = runningYm,
      profile = defaultProfile,
      monthSnapshot = octExisting,
      otList = emptyList(),
      pieceList = emptyList(),
      deductionList = emptyList()
    )
    // Untouched January (no snapshot yet) inherits default UserProfile target
    val janSummaryUntouched = com.example.ui.viewmodel.computeMonthlySummary(
      selectedYearMonth = "2027-01",
      currentCalendarYm = runningYm,
      profile = defaultProfile,
      monthSnapshot = null,
      otList = emptyList(),
      pieceList = emptyList(),
      deductionList = emptyList()
    )

    assertEquals(120.0, decSummary.targetHours, 0.001)
    assertEquals(80.0, novSummary.targetHours, 0.001)
    assertEquals(60.0, octSummary.targetHours, 0.001)
    assertEquals(70.0, janSummaryUntouched.targetHours, 0.001)

    // Setting explicit 0.0 target in November must stay 0.0 and NOT fall back to profile's 70.0
    val novZeroSnapshot = com.example.ui.viewmodel.buildTargetUpdatedSnapshot(
      targetYm = "2026-11",
      currentCalendarYm = runningYm,
      newTargetHours = 0.0,
      existingSnapshot = novFrozen,
      baselineProfile = defaultProfile,
      nowMillis = 4000L
    )
    val novZeroSummary = com.example.ui.viewmodel.computeMonthlySummary(
      selectedYearMonth = "2026-11",
      currentCalendarYm = runningYm,
      profile = defaultProfile,
      monthSnapshot = novZeroSnapshot,
      otList = emptyList(),
      pieceList = emptyList(),
      deductionList = emptyList()
    )
    assertEquals(0.0, novZeroSummary.targetHours, 0.001)
    assertEquals(120.0, decSummary.targetHours, 0.001)
    assertEquals(60.0, octSummary.targetHours, 0.001)
  }

  @Test
  fun testPerMonthSalaryRatesBonusBillsIsolation_profileUpdateOnlyAffectsRunningMonthAndUntouchedDefault() {
    val runningYm = "2026-11"
    val oldProfile = com.example.data.model.UserProfile(
      userName = "Worker A",
      companyName = "Old Factory",
      basicSalary = 10400.0, // 100.0/hr
      isCustomSalary = false,
      attendanceBonus = 1000.0,
      maxAbsentForBonus = 1,
      nightBillRate = 100.0,
      tiffinBillRate = 50.0,
      monthlyOtTargetHours = 50.0
    )

    // Existing customized past month (2026-10) and customized future month (2026-12)
    val octSnapshot = com.example.ui.viewmodel.buildTargetUpdatedSnapshot(
      targetYm = "2026-10",
      currentCalendarYm = runningYm,
      newTargetHours = 45.0,
      existingSnapshot = null,
      baselineProfile = oldProfile,
      nowMillis = 1000L
    )
    val decCustomSnapshot = com.example.data.model.MonthlySalarySettings(
      yearMonth = "2026-12",
      basicSalary = 15600.0,
      hourlyOtRate = 95.0,
      customOtRate = 95.0,
      isCustomSalary = true,
      monthlyOtTargetHours = 110.0,
      attendanceBonus = 1500.0,
      maxAbsentForBonus = 0,
      nightBillRate = 150.0,
      tiffinBillRate = 75.0,
      userName = "Worker A",
      companyName = "Dec Contract",
      updatedAt = 2000L
    )
    val novExistingTargetSnapshot = com.example.ui.viewmodel.buildTargetUpdatedSnapshot(
      targetYm = "2026-11",
      currentCalendarYm = runningYm,
      newTargetHours = 85.0,
      existingSnapshot = null,
      baselineProfile = oldProfile,
      nowMillis = 2500L
    )

    // User updates Profile in Profile tab with raised salary, custom OT rate, higher bonus & bills
    val updatedProfile = oldProfile.copy(
      companyName = "New Raise Factory",
      basicSalary = 13000.0,
      isCustomSalary = true,
      customOtRate = 140.0,
      attendanceBonus = 2000.0,
      maxAbsentForBonus = 2,
      nightBillRate = 180.0,
      tiffinBillRate = 90.0
    )

    val novUpdatedSnapshot = com.example.ui.viewmodel.buildRunningMonthProfileUpdatedSnapshot(
      currentCalendarYm = runningYm,
      updatedProfile = updatedProfile,
      existingRunningSnapshot = novExistingTargetSnapshot,
      nowMillis = 5000L
    )

    // Running November picks up updated salary/rates/bonuses/bills while keeping November's 85h target
    assertEquals(85.0, novUpdatedSnapshot.monthlyOtTargetHours, 0.001)
    assertEquals(140.0, novUpdatedSnapshot.hourlyOtRate, 0.001)
    assertEquals(2000.0, novUpdatedSnapshot.attendanceBonus, 0.001)
    assertEquals(180.0, novUpdatedSnapshot.nightBillRate, 0.001)
    assertEquals(90.0, novUpdatedSnapshot.tiffinBillRate, 0.001)

    val novOtList = listOf(
      com.example.data.model.OvertimeRecord(
        date = "2026-11-10",
        yearMonth = "2026-11",
        otHours = 3.0,
        hasNightBill = true,
        hasTiffinBill = true,
        hourlyRateSnapshot = 140.0
      )
    )
    val decOtList = listOf(
      com.example.data.model.OvertimeRecord(
        date = "2026-12-10",
        yearMonth = "2026-12",
        otHours = 3.0,
        hasNightBill = true,
        hasTiffinBill = true,
        hourlyRateSnapshot = 95.0
      )
    )
    val octOtList = listOf(
      com.example.data.model.OvertimeRecord(
        date = "2026-10-10",
        yearMonth = "2026-10",
        otHours = 3.0,
        hasNightBill = true,
        hasTiffinBill = true,
        hourlyRateSnapshot = 100.0
      )
    )

    val novSummary = com.example.ui.viewmodel.computeMonthlySummary(
      selectedYearMonth = "2026-11",
      currentCalendarYm = runningYm,
      profile = updatedProfile,
      monthSnapshot = novUpdatedSnapshot,
      otList = novOtList,
      pieceList = emptyList(),
      deductionList = emptyList()
    )
    val decSummary = com.example.ui.viewmodel.computeMonthlySummary(
      selectedYearMonth = "2026-12",
      currentCalendarYm = runningYm,
      profile = updatedProfile,
      monthSnapshot = decCustomSnapshot,
      otList = decOtList,
      pieceList = emptyList(),
      deductionList = emptyList()
    )
    val octSummary = com.example.ui.viewmodel.computeMonthlySummary(
      selectedYearMonth = "2026-10",
      currentCalendarYm = runningYm,
      profile = updatedProfile,
      monthSnapshot = octSnapshot,
      otList = octOtList,
      pieceList = emptyList(),
      deductionList = emptyList()
    )
    val janSummaryUntouched = com.example.ui.viewmodel.computeMonthlySummary(
      selectedYearMonth = "2027-01",
      currentCalendarYm = runningYm,
      profile = updatedProfile,
      monthSnapshot = null,
      otList = emptyList(),
      pieceList = emptyList(),
      deductionList = emptyList()
    )

    // Running November reflects new rates
    assertEquals(140.0, novSummary.hourlyOtRate, 0.001)
    assertEquals(420.0, novSummary.totalOtAmount, 0.001)
    assertEquals(180.0, novSummary.nightBillTotal, 0.001)
    assertEquals(90.0, novSummary.tiffinBillTotal, 0.001)
    assertEquals(2000.0, novSummary.attendanceBonus, 0.001)
    assertEquals("New Raise Factory", novSummary.companyName)

    // Customized future December stays completely isolated from updatedProfile
    assertEquals(95.0, decSummary.hourlyOtRate, 0.001)
    assertEquals(285.0, decSummary.totalOtAmount, 0.001)
    assertEquals(150.0, decSummary.nightBillTotal, 0.001)
    assertEquals(75.0, decSummary.tiffinBillTotal, 0.001)
    assertEquals(1500.0, decSummary.attendanceBonus, 0.001)
    assertEquals(110.0, decSummary.targetHours, 0.001)
    assertEquals("Dec Contract", decSummary.companyName)

    // Past October stays completely isolated from updatedProfile
    assertEquals(100.0, octSummary.hourlyOtRate, 0.001)
    assertEquals(300.0, octSummary.totalOtAmount, 0.001)
    assertEquals(100.0, octSummary.nightBillTotal, 0.001)
    assertEquals(50.0, octSummary.tiffinBillTotal, 0.001)
    assertEquals(1000.0, octSummary.attendanceBonus, 0.001)
    assertEquals(45.0, octSummary.targetHours, 0.001)
    assertEquals("Old Factory", octSummary.companyName)

    // Untouched future January inherits updatedProfile defaults
    assertEquals(140.0, janSummaryUntouched.hourlyOtRate, 0.001)
    assertEquals(180.0, janSummaryUntouched.effectiveNightRate, 0.001)
    assertEquals(90.0, janSummaryUntouched.effectiveTiffinRate, 0.001)
    assertEquals(2000.0, janSummaryUntouched.attendanceBonus, 0.001)

    // QuickOtEntryDialog profile resolution uses snapshot rates for December instead of global profile
    val resolvedDecDialogProfile = com.example.ui.viewmodel.resolveEffectiveMonthProfile(updatedProfile, decCustomSnapshot)
    assertEquals(150.0, resolvedDecDialogProfile.nightBillRate, 0.001)
    assertEquals(75.0, resolvedDecDialogProfile.tiffinBillRate, 0.001)
    assertEquals(15600.0, resolvedDecDialogProfile.basicSalary, 0.001)
    assertEquals(95.0, com.example.ui.viewmodel.resolveEffectiveHourlyOtRate(updatedProfile, decCustomSnapshot), 0.001)
  }

  @Test
  fun testSmartDailyReminder_skipWhenEntryExistsAndRestoreOnDelete() {
    val tz = java.util.TimeZone.getTimeZone("Asia/Dhaka")
    val cal3Pm = java.util.Calendar.getInstance(tz).apply {
      set(2026, java.util.Calendar.NOVEMBER, 26, 15, 0, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }

    // 1. Entry added at 3:00 PM -> both 7:15 PM & 9:30 PM move to tomorrow (2026-11-27)
    val plan1Tomorrow = com.example.receiver.ReminderSchedulePolicy.computeTriggerPlan(
      hour = com.example.receiver.ReminderSchedulePolicy.FIRST_REMINDER_HOUR,
      minute = com.example.receiver.ReminderSchedulePolicy.FIRST_REMINDER_MINUTE,
      forceTomorrow = true,
      nowMillis = cal3Pm.timeInMillis,
      timeZone = tz
    )
    val plan2Tomorrow = com.example.receiver.ReminderSchedulePolicy.computeTriggerPlan(
      hour = com.example.receiver.ReminderSchedulePolicy.SECOND_REMINDER_HOUR,
      minute = com.example.receiver.ReminderSchedulePolicy.SECOND_REMINDER_MINUTE,
      forceTomorrow = true,
      nowMillis = cal3Pm.timeInMillis,
      timeZone = tz
    )
    assertEquals("2026-11-27", plan1Tomorrow.scheduledForDate)
    assertEquals("2026-11-27", plan2Tomorrow.scheduledForDate)

    // 2. Entry deleted at 5:00 PM (before 7:15 PM) -> both re-enabled for today (2026-11-26)
    val cal5Pm = java.util.Calendar.getInstance(tz).apply {
      set(2026, java.util.Calendar.NOVEMBER, 26, 17, 0, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val plan1RestoredBefore715 = com.example.receiver.ReminderSchedulePolicy.computeTriggerPlan(
      hour = com.example.receiver.ReminderSchedulePolicy.FIRST_REMINDER_HOUR,
      minute = com.example.receiver.ReminderSchedulePolicy.FIRST_REMINDER_MINUTE,
      forceTomorrow = false,
      nowMillis = cal5Pm.timeInMillis,
      timeZone = tz
    )
    val plan2RestoredBefore715 = com.example.receiver.ReminderSchedulePolicy.computeTriggerPlan(
      hour = com.example.receiver.ReminderSchedulePolicy.SECOND_REMINDER_HOUR,
      minute = com.example.receiver.ReminderSchedulePolicy.SECOND_REMINDER_MINUTE,
      forceTomorrow = false,
      nowMillis = cal5Pm.timeInMillis,
      timeZone = tz
    )
    assertEquals("2026-11-26", plan1RestoredBefore715.scheduledForDate)
    assertEquals("2026-11-26", plan2RestoredBefore715.scheduledForDate)

    // 3. Entry deleted at 8:00 PM (after 7:15 PM, before 9:30 PM) -> 7:15 goes tomorrow, 9:30 re-enabled tonight
    val cal8Pm = java.util.Calendar.getInstance(tz).apply {
      set(2026, java.util.Calendar.NOVEMBER, 26, 20, 0, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val plan1After715 = com.example.receiver.ReminderSchedulePolicy.computeTriggerPlan(
      hour = com.example.receiver.ReminderSchedulePolicy.FIRST_REMINDER_HOUR,
      minute = com.example.receiver.ReminderSchedulePolicy.FIRST_REMINDER_MINUTE,
      forceTomorrow = false,
      nowMillis = cal8Pm.timeInMillis,
      timeZone = tz
    )
    val plan2RestoredAfter715 = com.example.receiver.ReminderSchedulePolicy.computeTriggerPlan(
      hour = com.example.receiver.ReminderSchedulePolicy.SECOND_REMINDER_HOUR,
      minute = com.example.receiver.ReminderSchedulePolicy.SECOND_REMINDER_MINUTE,
      forceTomorrow = false,
      nowMillis = cal8Pm.timeInMillis,
      timeZone = tz
    )
    assertEquals("2026-11-27", plan1After715.scheduledForDate)
    assertEquals("2026-11-26", plan2RestoredAfter715.scheduledForDate)

    // 4. Receiver real-time delivery guard decisions
    val skipExisting = com.example.receiver.ReminderSchedulePolicy.evaluateDelivery(
      hasTodayEntry = true,
      scheduledForDate = "2026-11-26",
      todayDate = "2026-11-26"
    )
    assertFalse(skipExisting.shouldShowNotification)
    assertEquals(com.example.receiver.ReminderSchedulePolicy.SKIP_REASON_ENTRY_EXISTS_TODAY, skipExisting.skipReason)
    assertTrue(skipExisting.rescheduleForceTomorrow)

    val deliverNextDayMissing = com.example.receiver.ReminderSchedulePolicy.evaluateDelivery(
      hasTodayEntry = false,
      scheduledForDate = "2026-11-27",
      todayDate = "2026-11-27"
    )
    assertTrue(deliverNextDayMissing.shouldShowNotification)
    assertNull(deliverNextDayMissing.skipReason)
    assertTrue(deliverNextDayMissing.rescheduleForceTomorrow)

    val skipStaleDelay = com.example.receiver.ReminderSchedulePolicy.evaluateDelivery(
      hasTodayEntry = false,
      scheduledForDate = "2026-11-26",
      todayDate = "2026-11-27"
    )
    assertFalse(skipStaleDelay.shouldShowNotification)
    assertEquals(com.example.receiver.ReminderSchedulePolicy.SKIP_REASON_STALE_DATE, skipStaleDelay.skipReason)
    assertFalse(skipStaleDelay.rescheduleForceTomorrow)
  }
}



