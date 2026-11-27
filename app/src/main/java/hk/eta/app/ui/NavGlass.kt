package hk.eta.app.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import hk.eta.app.R

/**
 * Floating "liquid glass" bottom bar whose appearance FOLLOWS SYSTEM SETTINGS.
 *
 * Re-applied from MainActivity.onCreate / onResume / onConfigurationChanged,
 * so changing Dark mode, High-contrast text or "Remove animations" in the
 * Android system Settings is picked up as soon as the user returns to the app.
 *
 * Honest scope: there is no public per-app "liquid glass" toggle whose exact
 * Android 17 key can be relied on from here, so we use only documented APIs:
 *   - Configuration.uiMode (night mode)
 *   - Settings.Secure "high_text_contrast_enabled"
 *   - Settings.Global ANIMATOR_DURATION_SCALE / TRANSITION_ANIMATION_SCALE
 *   - Window.setBackgroundBlurRadius (API 31+) for the real backdrop blur
 *   - View.setRenderEffect / View.setClipToOutline (API 21/31+) for the liquid layer
 * Everything is wrapped in runCatching and falls back to a plain translucent
 * drawable, so older / restricted devices still render a valid bar.
 *
 * CN_ANDROID17_GLASS: 中國版 Android 17 / 國內 ROM 會喺運行時停用
 * cross-window blur（省電、影片、廠商預設）。依 AOSP《窗口模糊處理》建議，
 * 而家由 [NavGlassCompat] 查 WindowManager#isCrossWindowBlurEnabled：
 *   - 可用 → 沿用半透明玻璃 + window blur；
 *   - 停用 → 改用提高 alpha 嘅實色玻璃（bg_nav_glass_no_blur*），
 *     唔會留一個透明欄直接透落底層內容。
 * 反射呼叫，唔引用 SDK 34 以上嘅 VERSION_CODES、唔用廠商私有 key、
 * 亦唔依賴 GMS。ANDROID17_BACKDROP：Android 17 QPR2 已有公開
 * `RenderNode.setBackdropRenderEffect()`，由相容層反射探測後優先套用；
 * 探測失敗照舊退回 API 31 window blur（詳情見 [NavGlassCompat]）。
 *
 * NAV_LIQUID: 喺原有半透明玻璃之上再加一層「液態」：
 *   1. 由上到下淡出嘅折射亮邊（rim）；
 *   2. 一條由左至右來回流動嘅白色高光（sheen），用 ValueAnimator 平移、
 *      API 31+ 再加 RenderEffect 輕微散射；
 *   3. 選中分頁按壓回彈（NavLiquid.springScale）。
 * 三樣都跟隨「跟隨系統外觀」：高對比文字／關閉動畫／用戶關閉跟隨 →
 * alpha 歸零、動畫 cancel、RenderEffect 清走，完全靜態，唔影響可讀性。
 * 層本身 clickable=false，觸控照舊穿透落四個分頁。
 */
object NavGlass {

    private const val ELEVATION_DP = 6f

    // NAV_GEOMETRY: smaller, centred capsule. Phones keep a wider pill (6%
    // inset per side) so four labels still fit; tablets and landscape screens
    // switch to 18% per side, matching the narrow centred capsule. Both are
    // clamped 16..256dp. Height stays 56dp.
    private const val BAR_HEIGHT_DP = 56
    private const val BAR_TOP_DP = 4
    private const val BAR_BOTTOM_DP = 8
    private const val SIDE_MIN_DP = 16f
    private const val SIDE_MAX_DP = 256f
    private const val SIDE_RATIO_PHONE = 0.06f
    private const val SIDE_RATIO_WIDE = 0.18f
    private const val WIDE_BREAKPOINT_DP = 600f

    // NAV_OVERLAY_POSITION: the bar is NO LONGER an occupying bottom row
    // pinned 8dp from the edge. It is a real FrameLayout overlay, lifted off
    // the bottom so content is visible behind AND below it (reference: the
    // blue install buttons show through, and the next app row sits below).
    // Bottom margin = 12% of screen height, clamped 48..160dp.
    private const val BAR_BOTTOM_RATIO = 0.12f
    private const val BAR_BOTTOM_MIN_DP = 48f
    private const val BAR_BOTTOM_MAX_DP = 160f
    private const val BAR_BOTTOM_DEFAULT_DP = 96

    // NAV_LIQUID: the sheen is wider than the bar's own highlight stripe so
    // the sweep reads as a slow liquid flow rather than a blinking flash.
    // Width lives in NavLiquid.SHEEN_WIDTH_DP (single source of truth).
    private const val RIM_STROKE_DP = 1f

    /** The single running sheen animator; cancelled on apply(), onPause and destroy. */
    private var sheenAnimator: ValueAnimator? = null

    private fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    private fun highContrast(context: Context): Boolean = runCatching {
        Settings.Secure.getInt(
            context.contentResolver, "high_text_contrast_enabled", 0
        ) == 1
    }.getOrDefault(false)

    private fun reducedMotion(context: Context): Boolean = runCatching {
        val cr = context.contentResolver
        val animator = Settings.Global.getFloat(
            cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        )
        val transition = Settings.Global.getFloat(
            cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f
        )
        animator == 0f || transition == 0f
    }.getOrDefault(false)

    /** NAV_LIQUID: respect the system animator scale for the flow speed. */
    private fun animatorScale(context: Context): Float = runCatching {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        )
    }.getOrDefault(1f)

    /**
     * Window#setBackgroundBlurRadius 交畀相容層用反射呼叫：SDK<31、
     * ROM 停用 cross-window blur、減少動態／高對比、反射失敗全部安全退化。
     */
    private fun backdropBlur(activity: Activity, bar: View, enabled: Boolean, night: Boolean) {
        // ANDROID17_BACKDROP: Android 17 QPR2 優先試元件級背景效果。
        // 成功 → 唔使再落 window blur；失敗／舊機 → 照舊 API 31 window blur，
        // 再唔得就由 apply() 揀實色玻璃 drawable，三層都唔會 crash。
        val backdropOn = enabled && NavGlassCompat.hasBackdropRenderEffect()
        val applied = if (backdropOn) {
            NavGlassCompat.applyBackdropEffect(activity, bar, true, night)
        } else {
            NavGlassCompat.applyBackdropEffect(activity, bar, false, night)
            false
        }
        if (!applied) NavGlassCompat.applyWindowBlur(activity, enabled, night)
    }

    /** Pure, offline-testable: lifted bottom margin in dp for a height in dp. */
    fun bottomMarginDp(heightDp: Float): Float =
        (heightDp * BAR_BOTTOM_RATIO)
            .coerceIn(BAR_BOTTOM_MIN_DP, BAR_BOTTOM_MAX_DP)

    /**
     * Size + position of the bar, re-computed on every apply() so rotating
     * the device (or using a tablet) immediately re-centres the capsule.
     * NAV_OVERLAY_POSITION: the bar now uses FrameLayout.LayoutParams as a
     * true overlay (gravity BOTTOM|CENTER_HORIZONTAL) with a lifted bottom
     * margin; a legacy LinearLayout parent still works with the old 8dp.
     * NAV_LIQUID: the same geometry is applied to the liquid overlay layer so
     * the sheen / rim stay perfectly aligned with the glass pill.
     */
    fun geometry(activity: Activity, bar: View) {
        val dm = activity.resources.displayMetrics
        val density = dm.density
        val widthDp = dm.widthPixels / density
        val heightDp = dm.heightPixels / density
        val ratio = if (widthDp >= WIDE_BREAKPOINT_DP) {
            SIDE_RATIO_WIDE
        } else {
            SIDE_RATIO_PHONE
        }
        val sidePx = (dm.widthPixels * ratio)
            .coerceIn(SIDE_MIN_DP * density, SIDE_MAX_DP * density).toInt()
        val bottomPx = (heightDp * BAR_BOTTOM_RATIO)
            .coerceIn(BAR_BOTTOM_MIN_DP, BAR_BOTTOM_MAX_DP)
        val heightPx = (BAR_HEIGHT_DP * density).toInt()
        val topPx = (BAR_TOP_DP * density).toInt()
        when (val lp = bar.layoutParams) {
            is android.widget.FrameLayout.LayoutParams -> {
                lp.height = heightPx
                lp.topMargin = topPx
                lp.bottomMargin = (bottomPx * density).toInt()
                lp.marginStart = sidePx
                lp.marginEnd = sidePx
                lp.gravity = android.view.Gravity.BOTTOM or
                        android.view.Gravity.CENTER_HORIZONTAL
                bar.layoutParams = lp
            }
            is android.widget.LinearLayout.LayoutParams -> {
                lp.height = heightPx
                lp.topMargin = topPx
                lp.bottomMargin = (BAR_BOTTOM_DP * density).toInt()
                lp.marginStart = sidePx
                lp.marginEnd = sidePx
                lp.gravity = android.view.Gravity.CENTER_HORIZONTAL
                bar.layoutParams = lp
            }
            else -> return
        }
    }

    /**
     * NAV_LIQUID: build the two gradient drawables in code (no new XML
     * resources), clip the overlay to the same 28dp pill and start / stop
     * the flowing sheen. All system-setting decisions live in [NavLiquid].
     */
    private fun liquid(
        activity: Activity,
        follow: Boolean,
        night: Boolean,
        contrast: Boolean,
        reduced: Boolean,
        noBlur: Boolean,
        material: NavGlassMaterial.Decision
    ) {
        val layer = activity.findViewById<View>(R.id.navLiquidLayer) as? FrameLayout ?: return
        val rim = activity.findViewById<View>(R.id.navLiquidRim) ?: return
        val sheen = activity.findViewById<View>(R.id.navLiquidSheen) ?: return
        geometry(activity, layer)

        val density = activity.resources.displayMetrics.density
        val radiusPx = 28f * density
        runCatching {
            layer.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                }
            }
            layer.clipToOutline = true
        }
        layer.isClickable = false

        val sheenDrawable = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0x00FFFFFF, 0x2BFFFFFF, 0x00FFFFFF)
        ).apply { cornerRadius = radiusPx }
        val rimDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x30FFFFFF, 0x001A1A1E)
        ).apply {
            cornerRadius = radiusPx
            setStroke((RIM_STROKE_DP * density).toInt(), 0x1AFFFFFF)
        }
        sheen.background = sheenDrawable
        rim.background = rimDrawable

        val baseOn = NavLiquid.enabled(follow, reduced, contrast)
        // CN_ADVANCED_MATERIAL: 系統／用戶話唔用進階材質 → 即刻靜止透明。
        val on = baseOn && material.liquid
        val sheenBase = NavLiquid.sheenAlpha(follow, reduced, contrast, night, noBlur)
        val rimBase = NavLiquid.rimAlpha(follow, reduced, contrast, noBlur)
        sheen.alpha = if (material.liquid) sheenBase else 0f
        rim.alpha = if (material.liquid) rimBase else 0f

        // API 31+ only; any failure just leaves a crisp sheen (still fine).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                sheen.setRenderEffect(
                    if (on) {
                        val r = 6f * density
                        RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
                    } else {
                        null
                    }
                )
            }
        }

        sheenAnimator?.cancel()
        sheenAnimator = null
        if (!on) {
            sheen.translationX = 0f
            return
        }

        fun startFlow() {
            if (!layer.isAttachedToWindow) return
            val barW = layer.width.toFloat()
            val sheenW = sheen.width.toFloat().coerceAtLeast(1f)
            if (barW <= 0f) {
                layer.post { startFlow() }
                return
            }
            val travel = NavLiquid.travelPx(barW, sheenW)
            val a = ValueAnimator.ofFloat(-sheenW, travel)
            a.duration = NavLiquid.durationMs(
                follow, reduced, contrast, animatorScale(activity)
            )
            a.repeatCount = ValueAnimator.INFINITE
            a.repeatMode = ValueAnimator.REVERSE
            a.interpolator = AccelerateDecelerateInterpolator()
            a.addUpdateListener { sheen.translationX = it.animatedValue as Float }
            sheenAnimator = a
            a.start()
        }
        startFlow()
    }

    /** NAV_LIQUID: foreground-only; MainActivity.onPause() calls this. */
    fun stopLiquid() {
        sheenAnimator?.cancel()
        sheenAnimator = null
    }

    /** Applies geometry, glass drawable, elevation, window blur + liquid layer. */
    fun apply(activity: Activity, bar: View) {
        geometry(activity, bar)
        val follow = NavGlassPrefs.followSystem(activity)
        val night = follow && isNight(activity)
        val contrast = follow && highContrast(activity)
        val reduced = follow && reducedMotion(activity)

        // CN_ANDROID17_GLASS: 先問系統而家係咪真係容許 cross-window blur。
        // 國內 ROM / 省電模式 / 影片播放停用時，用實色玻璃而唔係透明欄。
        // CN_ADVANCED_MATERIAL: 只讀探測中國版 Android OS 17 嘅進階材質狀態
        // （AOSP 能力 + 省電 + 容錯候選鍵），再交畀純決策層出結果。
        val crossWindow = NavGlassCompat.crossWindowBlurEnabled(activity)
        val probe = NavGlassCompat.materialProbe(activity)
        val material = NavGlassMaterial.resolve(
            mode = NavGlassPrefs.materialMode(activity),
            reducedMotion = reduced,
            highContrast = contrast,
            powerSave = probe.powerSave,
            crossWindowBlur = crossWindow,
            backdropEffect = probe.backdropEffect,
            oemValue = probe.oemValue,
            followSystem = follow
        )
        val blurOn = NavGlassCompat.blurEnabled(
            Build.VERSION.SDK_INT,
            crossWindow,
            reduced,
            contrast
        ) && material.blur
        val bg = when {
            contrast -> R.drawable.bg_nav_glass_contrast
            !blurOn && night -> R.drawable.bg_nav_glass_no_blur_dark
            !blurOn -> R.drawable.bg_nav_glass_no_blur
            night -> R.drawable.bg_nav_glass_dark
            else -> R.drawable.bg_nav_glass
        }
        bar.setBackgroundResource(bg)
        bar.elevation = if (reduced) 0f else {
            ELEVATION_DP * activity.resources.displayMetrics.density
        }
        backdropBlur(activity, bar, blurOn, night)
        liquid(activity, follow, night, contrast, reduced, !blurOn, material)
    }

    /**
     * NAV_GEOMETRY: the selected tab is ONE darker inner pill with a blue
     * label (as in the reference), not a frosted highlight over the glass.
     * NAV_LIQUID: selecting gives a short liquid spring (0.88 -> 1.0 with a
     * damped wave); reduced motion / high contrast / fixed appearance snap.
     */
    fun selectTab(context: Context, tab: View, selected: Boolean) {
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_tab_glass_selected)
        } else {
            tab.background = null
        }
        val color = ContextCompat.getColor(
            context,
            if (selected) R.color.nav_tab_active else R.color.nav_tab_muted
        )
        (tab as? android.widget.TextView)?.setTextColor(color)
        if (selected && NavLiquid.enabled(
                NavGlassPrefs.followSystem(context),
                reducedMotion(context),
                highContrast(context)
            ) && NavGlassPrefs.materialMode(context) != NavGlassMaterial.MODE_SIMPLE
        ) {
            val a = ValueAnimator.ofFloat(0f, 1f)
            a.duration = 260L
            a.interpolator = AccelerateDecelerateInterpolator()
            a.addUpdateListener {
                val s = NavLiquid.springScale(it.animatedValue as Float)
                tab.scaleX = s
                tab.scaleY = s
            }
            a.start()
        } else {
            tab.scaleX = 1f
            tab.scaleY = 1f
        }
    }

    /** CN_ADVANCED_MATERIAL: 如實顯示用戶三態 + 探測來源，唔扮連上私有開關。 */
    private fun materialStatus(context: Context): String {
        val mode = NavGlassPrefs.materialMode(context)
        val label = context.getString(
            when (mode) {
                NavGlassMaterial.MODE_FULL -> R.string.nav_glass_material_full
                NavGlassMaterial.MODE_SIMPLE -> R.string.nav_glass_material_simple
                else -> R.string.nav_glass_material_follow
            }
        )
        val probe = NavGlassCompat.materialProbe(context)
        return context.getString(
            R.string.nav_glass_material_status,
            label,
            NavGlassCompat.materialSourceLabel(probe)
        )
    }

    /** Human-readable state shown in 應用程式設置. */
    fun status(context: Context): String {
        val mat = materialStatus(context)
        if (!NavGlassPrefs.followSystem(context)) {
            return context.getString(R.string.nav_glass_state_fixed) + " · " + mat
        }
        val crossWindow = NavGlassCompat.crossWindowBlurEnabled(context)
        val inner = when {
            highContrast(context) -> context.getString(R.string.nav_glass_state_contrast)
            reducedMotion(context) -> context.getString(R.string.nav_glass_state_reduced)
            NavGlassCompat.hasBackdropRenderEffect() && crossWindow ->
                context.getString(R.string.nav_glass_state_backdrop)
            !crossWindow -> context.getString(R.string.nav_glass_state_no_blur)
            isNight(context) -> context.getString(R.string.nav_glass_state_night)
            else -> context.getString(R.string.nav_glass_state_day)
        }
        return context.getString(R.string.nav_glass_state_follow, inner) + " · " + mat
    }
}

/**
 * NAV_LIQUID: PURE decision layer for the liquid overlay — no Android imports,
 * no colour resources, no View references. Offline-testable so the static
 * checker can prove the accessibility / reduced-motion degradation is honest.
 */
object NavLiquid {

    const val BASE_DURATION_MS = 4600L
    const val SHEEN_WIDTH_DP = 168f

    /** Follow-system + motion allowed + no high-contrast text. */
    fun enabled(follow: Boolean, reduced: Boolean, contrast: Boolean): Boolean =
        follow && !reduced && !contrast

    /** 0 means "no animation". The system animator scale stretches the flow. */
    fun durationMs(
        follow: Boolean, reduced: Boolean, contrast: Boolean, animatorScale: Float
    ): Long = if (!enabled(follow, reduced, contrast)) {
        0L
    } else {
        (BASE_DURATION_MS / animatorScale.coerceIn(0.25f, 2f)).toLong()
    }

    fun sheenAlpha(
        follow: Boolean, reduced: Boolean, contrast: Boolean,
        night: Boolean, noBlur: Boolean
    ): Float = when {
        !enabled(follow, reduced, contrast) -> 0f
        noBlur -> 0.28f
        night -> 0.42f
        else -> 0.55f
    }

    fun rimAlpha(
        follow: Boolean, reduced: Boolean, contrast: Boolean, noBlur: Boolean
    ): Float = when {
        !enabled(follow, reduced, contrast) -> 0f
        noBlur -> 0.32f
        else -> 0.70f
    }

    /** From just off the left edge to just past the right edge. */
    fun travelPx(barWidthPx: Float, sheenWidthPx: Float): Float =
        barWidthPx + sheenWidthPx

    /** 0.88 -> 1.0 with one damped wave; endpoints are exact. */
    fun springScale(frame: Float): Float {
        val t = frame.coerceIn(0f, 1f)
        val wave = kotlin.math.sin(t * kotlin.math.PI.toFloat()) * (1f - t) * 0.18f
        return 0.88f + 0.12f * t + wave
    }
}
