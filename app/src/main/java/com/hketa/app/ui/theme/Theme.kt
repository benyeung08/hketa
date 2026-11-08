package com.hketa.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

/**
 * 對齊 hkbus.app 補入「跟隨系統／淺色／深色」+「字型大小」。
 *
 * [fontScale] 只縮 Material3 Typography 嘅 fontSize，唔改 dp padding，
 * 所以唔會撐爆版面。MainActivity 由 UserPrefs 讀完傳入，
 * 改設定即刻重組，唔使重啟 Activity。
 *
 * ⚠️ 已知限制：部分舊頁面（例如 RouteMap 嘅 WebView 底圖、HomeScreen 少數卡片）
 * 仍有 hardcoded 深色底色，淺色模式下呢幾處唔會跟住轉 —— 屬既有技術債，
 * 唔喺今次功能補齊範圍，唔會影響 compile / 出包。
 */
@Composable
fun HketaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = scaledTypography(fontScale.coerceIn(0.85f, 1.40f)),
        content = content
    )
}

private fun scaledTypography(s: Float): Typography {
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.copy(fontSize = t.displayLarge.fontSize * s),
        displayMedium = t.displayMedium.copy(fontSize = t.displayMedium.fontSize * s),
        displaySmall = t.displaySmall.copy(fontSize = t.displaySmall.fontSize * s),
        headlineLarge = t.headlineLarge.copy(fontSize = t.headlineLarge.fontSize * s),
        headlineMedium = t.headlineMedium.copy(fontSize = t.headlineMedium.fontSize * s),
        headlineSmall = t.headlineSmall.copy(fontSize = t.headlineSmall.fontSize * s),
        titleLarge = t.titleLarge.copy(fontSize = t.titleLarge.fontSize * s),
        titleMedium = t.titleMedium.copy(fontSize = t.titleMedium.fontSize * s),
        titleSmall = t.titleSmall.copy(fontSize = t.titleSmall.fontSize * s),
        bodyLarge = t.bodyLarge.copy(fontSize = t.bodyLarge.fontSize * s),
        bodyMedium = t.bodyMedium.copy(fontSize = t.bodyMedium.fontSize * s),
        bodySmall = t.bodySmall.copy(fontSize = t.bodySmall.fontSize * s),
        labelLarge = t.labelLarge.copy(fontSize = t.labelLarge.fontSize * s),
        labelMedium = t.labelMedium.copy(fontSize = t.labelMedium.fontSize * s),
        labelSmall = t.labelSmall.copy(fontSize = t.labelSmall.fontSize * s)
    )
}
