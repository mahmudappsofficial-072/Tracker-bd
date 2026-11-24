package com.example.util

import com.example.data.model.UserProfile

enum class StartupPopupAction {
    NONE,
    SHOW_SALARY_GUIDE,
    CHECK_MISSING_OT
}

data class StartupPopupGateState(
    val isAppReady: Boolean,
    val isProfileLoaded: Boolean,
    val isInitialUpdateCheckSettled: Boolean,
    val isAuthLoading: Boolean,
    val isSigningOut: Boolean,
    val hasUpdateDialog: Boolean,
    val hasConsentDialog: Boolean,
    val hasQuickOtTrigger: Boolean,
    val isAnySheetOrDialogOpen: Boolean,
    val currentTab: Int,
    val isSalaryConfigured: Boolean,
    val hasCheckedMissingSalaryThisSession: Boolean,
    val hasCheckedMissingOtThisSession: Boolean
)

object SalaryOnboardingPolicy {

    /**
     * Single source of truth for whether a user's salary & OT structure has been configured.
     * Matches ProfileScreen calculation so Home, Profile, and Startup Gate stay strictly consistent.
     */
    fun isSalaryConfigured(profile: UserProfile): Boolean {
        val displayGross = if (profile.isCustomSalary) {
            profile.basicSalary
        } else if (profile.basicSalary > 0.0) {
            LaborLawCalculator.calculateAutoAllowances(profile.basicSalary).grossSalary
        } else {
            0.0
        }

        val effectiveHourlyOtRate = if (profile.isCustomSalary) {
            profile.customOtRate
        } else if (profile.basicSalary > 0.0) {
            LaborLawCalculator.calculateFromGrossSalary(displayGross).otHourlyRate
        } else {
            0.0
        }

        return displayGross > 0.0 || effectiveHourlyOtRate > 0.0
    }

    /**
     * Auto-opens the Auto vs Custom Mode Guide popup whenever a user enters SalaryStructureSheet
     * without an existing salary configuration. Once configured, users can still open it manually via (?).
     */
    fun shouldAutoShowModeGuide(profile: UserProfile): Boolean {
        return !isSalaryConfigured(profile)
    }

    /**
     * Strict priority arbiter for startup in-app popups:
     * Tier 0: Wait for Room first profile load, initial cloud sync/readiness, and initial update check.
     * Tier 1: Update dialogs (Force or Optional), Privacy Consent, Quick-OT deep-link, or active overlays
     *         hold higher priority without consuming lower-tier session flags—so dismissing an Optional Update
     *         immediately unlocks the Salary Setup Guide or Missing OT Reminder.
     * Tier 2: Missing Salary Guide (once per session when !isSalaryConfigured and not on Profile tab).
     *         HARD RULE: Missing OT Reminder NEVER triggers when !isSalaryConfigured.
     * Tier 3: Missing OT Reminder (only eligible when isSalaryConfigured == true).
     */
    fun evaluateStartupPopupAction(state: StartupPopupGateState): StartupPopupAction {
        if (!state.isAppReady || !state.isProfileLoaded || !state.isInitialUpdateCheckSettled) {
            return StartupPopupAction.NONE
        }
        if (state.isAuthLoading || state.isSigningOut) {
            return StartupPopupAction.NONE
        }
        if (state.hasUpdateDialog || state.hasConsentDialog || state.hasQuickOtTrigger || state.isAnySheetOrDialogOpen) {
            return StartupPopupAction.NONE
        }

        if (!state.isSalaryConfigured) {
            return if (!state.hasCheckedMissingSalaryThisSession && state.currentTab != 3) {
                StartupPopupAction.SHOW_SALARY_GUIDE
            } else {
                StartupPopupAction.NONE
            }
        }

        return if (!state.hasCheckedMissingOtThisSession) {
            StartupPopupAction.CHECK_MISSING_OT
        } else {
            StartupPopupAction.NONE
        }
    }
}
