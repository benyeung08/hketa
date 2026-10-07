package com.hketa.app.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * 介面語言管理：繁體中文 / 简体中文 / English，或跟隨系統。
 *
 * Android 13（API 33）以上交畀系統「應用程式語言」設定；以下由 AppCompat 自行保存，
 * 兩者都透過 AppCompatDelegate.setApplicationLocales 統一處理，重啟後依然生效。
 */
object AppLocale {

    /** 空字串 = 跟隨系統 */
    const val FOLLOW_SYSTEM = ""
    const val ZH_HANT = "zh-TW"
    const val ZH_HANS = "zh-CN"
    const val EN = "en"

    private const val PREF = "hketa_locale"
    private const val KEY = "ui_lang"

    /**
     * 用戶實際揀嘅選項（跟隨系統時係 [FOLLOW_SYSTEM]），存喺 SharedPreferences。
     * 同 current() 嘅分別：current() 係「而家生效緊邊種語言」。
     */
    fun choice(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, FOLLOW_SYSTEM) ?: FOLLOW_SYSTEM

    /**
     * 目前生效嘅語言標籤（zh-TW / zh-CN / en）。
     */
    fun current(): String {
        val tags = AppCompatDelegate.getApplicationLocales()
        val actual = if (tags.isEmpty) {
            LocaleListCompat.getDefault()[0] ?: Locale.getDefault()
        } else {
            tags[0]!!
        }
        return normalize(actual.toLanguageTag())
    }

    /** true = 目前係英文介面 */
    fun isEnglish(): Boolean = current() == EN

    /**
     * 把任意語言標籤歸一化成本 App 支援嘅值之一。
     * 繁簡判斷同時睇 region（TW/HK/MO ↔ CN/SG）同 script（Hant ↔ Hans）。
     */
    fun normalize(tag: String): String {
        if (tag.isBlank()) return FOLLOW_SYSTEM
        val t = tag.replace('_', '-')
        if (!t.startsWith("zh", ignoreCase = true)) {
            return if (t.startsWith("en", ignoreCase = true)) EN else FOLLOW_SYSTEM
        }
        val upper = t.uppercase()
        val isSimplified = upper.contains("HANS") ||
            upper.contains("-CN") || upper.contains("-SG") || upper.contains("-MY")
        return if (isSimplified) ZH_HANS else ZH_HANT
    }

    /**
     * 設定語言並記低用戶選擇。傳 [FOLLOW_SYSTEM] 即跟隨系統。
     * 唔使手動 recreate Activity —— AppCompatDelegate 會自己重建。
     */
    fun apply(context: Context, tag: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit {
            putString(KEY, tag)
        }
        val locales = if (tag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** 設定頁用：語言標籤 → 顯示名。語言名一律用該語言自稱，只有「跟隨系統」跟介面語言。 */
    fun options(context: Context): List<Pair<String, String>> = listOf(
        FOLLOW_SYSTEM to context.getString(R.string.settings_language_follow),
        ZH_HANT to context.getString(R.string.settings_language_zh_hant),
        ZH_HANS to context.getString(R.string.settings_language_zh_hans),
        EN to context.getString(R.string.settings_language_en)
    )
}
