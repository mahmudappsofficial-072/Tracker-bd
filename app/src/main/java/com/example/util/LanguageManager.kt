package com.example.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LanguageManager {

    const val SYSTEM_TAG = "system"
    const val FALLBACK_TAG = "en"

    val SUPPORTED_LANG_TAGS = setOf("en", "bn", "hi", "ta", "ar")
    val VALID_PROFILE_LANGUAGES = SUPPORTED_LANG_TAGS + SYSTEM_TAG

    private const val PREFS_NAME = "overtime_locale_prefs"
    private const val KEY_V2_MIGRATED = "locale_v2_migrated"

    enum class SupportedLanguage(
        val tag: String,
        val nativeLabel: String,
        val englishLabel: String,
        val flagEmoji: String
    ) {
        EN("en", "English", "English", "🇬🇧"),
        BN("bn", "বাংলা", "Bengali", "🇧🇩"),
        HI("hi", "हिन्दी", "Hindi", "🇮🇳"),
        TA("ta", "தமிழ்", "Tamil", "🇮🇳"),
        AR("ar", "العربية", "Arabic", "🇸🇦");

        companion object {
            fun fromTag(tag: String?): SupportedLanguage? {
                val primary = extractPrimarySubtag(tag)
                return entries.firstOrNull { it.tag == primary }
            }
        }
    }

    @Deprecated("Use SupportedLanguage enum instead", ReplaceWith("SupportedLanguage"))
    enum class Lang(val code: String, val displayName: String) {
        BN("bn", "বাংলা"),
        EN("en", "English"),
        HI("hi", "हिन्दी"),
        TA("ta", "தமிழ்"),
        AR("ar", "العربية")
    }

    private fun extractPrimarySubtag(rawTag: String?): String {
        val clean = rawTag?.trim()?.lowercase()?.replace('_', '-') ?: ""
        if (clean.isBlank()) return ""
        return clean.substringBefore('-')
    }

    fun resolveSupportedTag(rawTag: String?): String {
        val primary = extractPrimarySubtag(rawTag)
        return if (primary in SUPPORTED_LANG_TAGS) primary else FALLBACK_TAG
    }

    fun normalizeProfileLanguage(rawTag: String?): String {
        val trimmed = rawTag?.trim()?.lowercase() ?: ""
        if (trimmed.isBlank() || trimmed == SYSTEM_TAG) return SYSTEM_TAG
        val primary = extractPrimarySubtag(trimmed)
        return if (primary in SUPPORTED_LANG_TAGS) primary else SYSTEM_TAG
    }

    fun localeForTag(tag: String): Locale {
        return when (resolveSupportedTag(tag)) {
            "bn" -> Locale.forLanguageTag("bn-BD")
            "hi" -> Locale.forLanguageTag("hi-IN")
            "ta" -> Locale.forLanguageTag("ta-IN")
            "ar" -> Locale.forLanguageTag("ar")
            else -> Locale.US
        }
    }

    fun isBengaliDigits(langTag: String): Boolean = resolveSupportedTag(langTag) == "bn"

    fun getSystemEffectiveTag(): String {
        return try {
            val systemLocales = Resources.getSystem().configuration.locales
            val primaryLocale = if (!systemLocales.isEmpty) systemLocales[0] else Locale.getDefault()
            resolveSupportedTag(primaryLocale?.language)
        } catch (_: Exception) {
            resolveSupportedTag(Locale.getDefault().language)
        }
    }

    fun getSelectedSettingTag(context: Context? = null): String {
        return try {
            val appLocales = AppCompatDelegate.getApplicationLocales()
            if (!appLocales.isEmpty) {
                resolveSupportedTag(appLocales[0]?.toLanguageTag())
            } else {
                SYSTEM_TAG
            }
        } catch (_: Exception) {
            SYSTEM_TAG
        }
    }

    fun getEffectiveLanguageTag(
        context: Context? = null,
        fallbackProfileLang: String? = null
    ): String {
        try {
            val appLocales = AppCompatDelegate.getApplicationLocales()
            if (!appLocales.isEmpty) {
                return resolveSupportedTag(appLocales[0]?.toLanguageTag())
            }
        } catch (_: Exception) {
        }

        val normalizedProfile = fallbackProfileLang?.trim()?.lowercase()
        if (normalizedProfile != null && normalizedProfile in SUPPORTED_LANG_TAGS) {
            return normalizedProfile
        }

        if (context != null) {
            try {
                val cfgLocales = context.resources.configuration.locales
                if (!cfgLocales.isEmpty) {
                    return resolveSupportedTag(cfgLocales[0]?.language)
                }
            } catch (_: Exception) {
            }
        }

        return getSystemEffectiveTag()
    }

    fun getLocalizedContext(context: Context, explicitTag: String? = null): Context {
        val tag = if (explicitTag != null && explicitTag != SYSTEM_TAG) {
            resolveSupportedTag(explicitTag)
        } else {
            getEffectiveLanguageTag(context, explicitTag)
        }
        val locale = localeForTag(tag)
        val config = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(config)
    }

    fun applyAppLanguage(tag: String) {
        val normalized = normalizeProfileLanguage(tag)
        val localeList = if (normalized == SYSTEM_TAG) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(normalized)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun migrateLegacyLocaleIfNeeded(
        context: Context,
        legacyProfileLanguage: String?,
        hasConfiguredData: Boolean
    ): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentAppSetting = getSelectedSettingTag(context)
        if (currentAppSetting != SYSTEM_TAG) {
            prefs.edit().putBoolean(KEY_V2_MIGRATED, true).apply()
            return currentAppSetting
        }
        if (prefs.getBoolean(KEY_V2_MIGRATED, false)) {
            return SYSTEM_TAG
        }
        prefs.edit().putBoolean(KEY_V2_MIGRATED, true).apply()

        val normalizedLegacy = normalizeProfileLanguage(legacyProfileLanguage)
        return if (hasConfiguredData && normalizedLegacy in SUPPORTED_LANG_TAGS) {
            applyAppLanguage(normalizedLegacy)
            normalizedLegacy
        } else {
            SYSTEM_TAG
        }
    }

    fun getDisplayName(tag: String, systemTag: String = getSystemEffectiveTag()): String {
        if (tag == SYSTEM_TAG) {
            val resolved = SupportedLanguage.fromTag(systemTag)?.nativeLabel ?: SupportedLanguage.EN.nativeLabel
            return resolved
        }
        return SupportedLanguage.fromTag(tag)?.nativeLabel ?: SupportedLanguage.EN.nativeLabel
    }

    fun formatYearMonth(yearMonth: String, langTag: String): String {
        return try {
            val resolvedTag = resolveSupportedTag(langTag)
            val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
            val date = sdf.parse(yearMonth) ?: return yearMonth
            val outSdf = SimpleDateFormat("MMMM yyyy", localeForTag(resolvedTag))
            val formatted = outSdf.format(date)
            if (resolvedTag == "bn") LaborLawCalculator.toBengaliDigits(formatted) else formatted
        } catch (_: Exception) {
            yearMonth
        }
    }

    fun formatYearMonth(yearMonth: String, isBn: Boolean): String {
        return formatYearMonth(yearMonth, if (isBn) "bn" else "en")
    }

    fun formatSyncTimestamp(context: Context, timeMillis: Long, langTag: String): String {
        val localizedCtx = getLocalizedContext(context, langTag)
        if (timeMillis <= 0L) return localizedCtx.getString(R.string.not_synced)
        val resolvedTag = resolveSupportedTag(langTag)
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", localeForTag(resolvedTag))
        val formatted = sdf.format(Date(timeMillis))
        return if (resolvedTag == "bn") LaborLawCalculator.toBengaliDigits(formatted) else formatted
    }

    fun formatDisplayGrade(context: Context, grade: String, langTag: String): String {
        val clean = grade.trim()
        if (clean.isBlank()) return ""
        val localizedCtx = getLocalizedContext(context, langTag)
        val gradeWord = localizedCtx.getString(R.string.grade_fallback_label)
        val lower = clean.lowercase()
        val hasGradeWord = lower.contains("grade") || clean.contains("গ্রেড") || clean.contains(gradeWord)
        return if (hasGradeWord) {
            val stripped = clean
                .replace("Grade", "", ignoreCase = true)
                .replace("গ্রেড", "")
                .replace(gradeWord, "")
                .trim()
            if (stripped.isBlank()) gradeWord else localizedCtx.getString(R.string.grade_value_format, stripped)
        } else {
            localizedCtx.getString(R.string.grade_value_format, clean)
        }
    }
}
