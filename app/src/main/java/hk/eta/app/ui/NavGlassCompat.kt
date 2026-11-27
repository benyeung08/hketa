package hk.eta.app.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Window

/**
 * CN_ANDROID17_GLASS — 「中國版 Android 17 / 國內 ROM」嘅液態玻璃相容層。
 *
 * AOSP《窗口模糊處理》講得好清楚：系統伺服器可以喺運行時停用 cross-window
 * blur（省電模式、影片播放、開發者覆寫、廠商預設），所以：
 *   1. 要用 WindowManager#isCrossWindowBlurEnabled 查當前狀態；
 *   2. 要準備「啟用模糊」同「停用模糊」兩個版本嘅背景 —— 停用時提高
 *      drawable 嘅 alpha / 加深底色，唔好留一個透明窗口直接透落底層。
 * 國內 ROM 同無 GMS 嘅裝置最常 hit 嘅就係呢個情況。
 *
 * 做法刻意保守：
 *   - **只用公共 API + 反射**：唔引用 compileSdk 34 以上先存在嘅
 *     Build.VERSION_CODES 常數（Android 17 / API 37 專屬欄位一律唔掂），
 *     亦唔用任何廠商私有 key、唔用下一代系統先有嘅元件級背景效果。
 *   - 反射呼叫 Window#setBackgroundBlurRadius(int) 同
 *     WindowManager#isCrossWindowBlurEnabled()：ROM 改簽名或冇呢個方法
 *     都只會 runCatching 吞掉，唔會 NoSuchMethodError  crash。
 *   - **Android 17 QPR2（API 37 / SDK 37.2）已正式提供**公開嘅元件級背景效果
 *     `RenderNode.setBackdropRenderEffect(RenderEffect)`（連同
 *     `SurfaceView.setBlurRegions()`）。本專案 compileSdk = 34 **編唔到**呢個
 *     符號，所以一律用「反射能力探測」：搵到 method 先用，搵唔到／hidden API
 *     限制／ROM 停用就靜默退回 API 31 window blur 或實色玻璃，絕不 crash。
 *     唔用 Compose 漸進模糊（佢只模糊元件自身，模糊唔到後方內容）。
 *   - 全部退化路徑都用「實色玻璃」drawable，外觀維持同一個
 *     56dp / 28dp 圓角藥丸，只係唔再透明。
 *
 * 純決策函數 [blurEnabled] / [blurRadius] / [modernPlatform] 唔掂 Android，
 * 可以離線等價模擬；其餘全部 runCatching 包住。
 */
object NavGlassCompat {

    /** Android 12 / S — Window.setBackgroundBlurRadius 嘅最低 API。 */
    const val API_BLUR_MIN = 31

    /**
     * Android 15+（含中國版 Android 17 / API 37）統一走同一條能力探測路徑。
     * 用數字而唔係 Build.VERSION_CODES 常數：SDK 34 編唔到 35+ 嘅新常數。
     */
    const val API_MODERN_MIN = 35

    const val BLUR_DAY = 18
    const val BLUR_NIGHT = 26

    /** Android 17 = API 37；QPR2 = SDK 37.2 先有 backdrop render effect。 */
    const val API_ANDROID17 = 37

    /**
     * AOSP《窗口模糊處理》：背景模糊 80px 已有良好磨砂效果，應避免 >150px
     * （嚴重影響效能）。呢度用中等值，夜間略深，全部遠低於 150。
     */
    const val BLUR_BACKDROP_DAY = 40
    const val BLUR_BACKDROP_NIGHT = 56
    const val BLUR_HARD_CAP = 150

    /**
     * 純決策：只有「API ≥31 ＋ 系統目前容許 cross-window blur ＋ 無減少動態
     * ＋ 無高對比」先開真模糊；任何一項唔成立就用實色 fallback。
     */
    fun blurEnabled(
        sdkInt: Int,
        crossWindowBlur: Boolean,
        reducedMotion: Boolean,
        highContrast: Boolean
    ): Boolean = sdkInt >= API_BLUR_MIN
            && crossWindowBlur
            && !reducedMotion
            && !highContrast

    fun blurRadius(night: Boolean): Int = if (night) BLUR_NIGHT else BLUR_DAY

    /** 元件級背景效果半徑（AOSP 建議區間內，夾喺 150 以下）。 */
    fun backdropRadius(night: Boolean): Int =
        (if (night) BLUR_BACKDROP_NIGHT else BLUR_BACKDROP_DAY)
            .coerceAtMost(BLUR_HARD_CAP)

    fun modernPlatform(sdkInt: Int): Boolean = sdkInt >= API_MODERN_MIN

    /**
     * 純決策、可離線模擬：只有 Android 17+ ＋ 反射搵到 method ＋ 系統容許
     * cross-window blur ＋ 無減少動態 ＋ 無高對比，先啟用元件級背景模糊。
     * 任何一項唔成立都退回 API 31 window blur，再唔得就實色玻璃。
     */
    fun backdropEffectEnabled(
        sdkInt: Int,
        hasBackdropMethod: Boolean,
        crossWindowBlur: Boolean,
        reducedMotion: Boolean,
        highContrast: Boolean
    ): Boolean = sdkInt >= API_ANDROID17
            && hasBackdropMethod
            && crossWindowBlur
            && !reducedMotion
            && !highContrast

    /** 純字串決策，方便設定頁如實顯示，亦方便離線測試。 */
    fun backgroundName(blurOn: Boolean, night: Boolean, contrast: Boolean): String =
        when {
            contrast -> "bg_nav_glass_contrast"
            !blurOn && night -> "bg_nav_glass_no_blur_dark"
            !blurOn -> "bg_nav_glass_no_blur"
            night -> "bg_nav_glass_dark"
            else -> "bg_nav_glass"
        }

    /**
     * AOSP: WindowManager#isCrossWindowBlurEnabled（API 31+）。
     * 反射搵 public 無參方法（包括 interface default method），任何失敗都
     * 當「停用」→ 安全用實色玻璃，唔會把透明欄放喺內容上面。
     */
    fun crossWindowBlurEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < API_BLUR_MIN) return false
        return runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE)
            if (wm == null) {
                false
            } else {
                val m = wm.javaClass.methods.firstOrNull {
                    it.name == "isCrossWindowBlurEnabled" && it.parameterCount == 0
                }
                if (m == null) false else (m.invoke(wm) as? Boolean ?: false)
            }
        }.getOrDefault(false)
    }

    /**
     * 反射呼叫 Window#setBackgroundBlurRadius(int)；停用時傳 0。
     * 中國版 ROM / 未來 Android 改咗簽名都唔會炸，只係冇模糊效果。
     */
    fun applyWindowBlur(activity: Activity, enabled: Boolean, night: Boolean) {
        if (Build.VERSION.SDK_INT < API_BLUR_MIN) return
        runCatching {
            val m = Window::class.java.getMethod(
                "setBackgroundBlurRadius", Int::class.javaPrimitiveType!!
            )
            m.invoke(activity.window, if (enabled) blurRadius(night) else 0)
        }
    }

    /**
     * Android 17 QPR2 公開 API：RenderNode#setBackdropRenderEffect(RenderEffect)
     * ＋ RenderEffect#createBlurEffect(float, float, Shader.TileMode)。
     *
     * 全部用反射：compileSdk 34 冇 RenderNode 嘅新符號，亦唔假設
     * View#getRenderNode() 一定可達（Android 9+ hidden API 限制有機會擋）。
     * 回傳 true 代表「真係成功套用」，NavGlass 就唔使再落 window blur；
     * 回傳 false 代表安全退化，由呼叫方照舊用 API 31 window blur。
     */
    fun hasBackdropRenderEffect(): Boolean =
        if (Build.VERSION.SDK_INT < API_ANDROID17) false
        else runCatching {
            Class.forName("android.graphics.RenderNode")
                .methods.any { it.name == "setBackdropRenderEffect" && it.parameterCount == 1 }
        }.getOrDefault(false)

    fun applyBackdropEffect(
        activity: Activity,
        bar: android.view.View,
        enabled: Boolean,
        night: Boolean
    ): Boolean {
        if (Build.VERSION.SDK_INT < API_ANDROID17) return false
        return runCatching {
            val renderNode = runCatching {
                android.view.View::class.java
                    .getDeclaredMethod("getRenderNode")
                    .apply { isAccessible = true }
                    .invoke(bar)
            }.getOrNull() ?: runCatching {
                val f = android.view.View::class.java
                    .getDeclaredField("mRenderNode")
                    .apply { isAccessible = true }
                f.get(bar)
            }.getOrNull() ?: return false

            val effect = if (enabled) {
                val r = backdropRadius(night).toFloat()
                val effectClass = Class.forName("android.graphics.RenderEffect")
                val tileMode = Class.forName("android.graphics.Shader\$TileMode")
                    .getField("CLAMP").get(null)
                effectClass
                    .getMethod(
                        "createBlurEffect",
                        java.lang.Float.TYPE, java.lang.Float.TYPE,
                        Class.forName("android.graphics.Shader\$TileMode")
                    )
                    .invoke(null, r, r, tileMode)
            } else null

            renderNode.javaClass
                .getMethod("setBackdropRenderEffect", Class.forName("android.graphics.RenderEffect"))
                .invoke(renderNode, effect)
            enabled
        }.getOrDefault(false)
    }

    // ------------------------------------------------------------------
    // CN_ADVANCED_MATERIAL: 中國版 Android OS 17「進階／高級材質」設定
    // ------------------------------------------------------------------
    //
    // 老實講：國內 ROM 嘅「高級材質／進階視覺效果」多數係廠商私有設定，
    // AOSP 冇一個可以可靠讀取嘅公開 key。所以呢度唔編造「官方 key」，而係：
    //   1. 先讀 AOSP 公開能力（cross-window blur、Android 17 backdrop effect、
    //      省電模式、動畫比例、高對比文字）；
    //   2. 再用一組「容錯候選鍵」best-effort 讀 Settings.Global / Secure /
    //      System —— 讀到就尊重，讀唔到／解析唔到就當未知，唔硬猜；
    //   3. 品牌只用嚟排 probe 順序同顯示狀態，唔會因為係國內 ROM 就硬開功能；
    //   4. 用戶可以喺「應用程式設置」三態覆蓋（跟隨／完整／簡化）。
    //
    // 全部只讀、全部 runCatching；寫入系統設定一律唔做。

    /** 容錯候選鍵：全部係 best-effort，唔聲稱對應任何廠商官方設定。 */
    val MATERIAL_CANDIDATE_KEYS = arrayOf(
        "advanced_material_enabled", "advanced_material",
        "material_advanced_enabled", "high_level_material_enabled",
        "window_blur_enabled", "material_effect_enabled"
    )

    /** 反向語義嘅容錯候選鍵：讀到 1/true 代表系統要求關閉進階材質。 */
    val MATERIAL_DISABLE_KEYS = arrayOf("disable_window_blur", "disable_material_effect")

    val CN_ROM_BRANDS = arrayOf(
        "xiaomi", "redmi", "miui", "hyperos", "oppo", "oneplus", "realme",
        "vivo", "iqoo", "huawei", "honor", "meizu", "harmony"
    )

    data class MaterialProbe(
        val chinaRom: Boolean,
        val crossWindowBlur: Boolean,
        val backdropEffect: Boolean,
        val powerSave: Boolean,
        val oemKey: String?,
        /** -1 = 要求關閉；0 = 未知／讀唔到；1 = 要求開啟。 */
        val oemValue: Int,
        val source: String
    )

    private fun parseFlag(raw: String?): Int = when (raw?.trim()?.lowercase()) {
        "1", "true", "on", "yes", "enable", "enabled" -> 1
        "0", "false", "off", "no", "disable", "disabled" -> -1
        else -> 0
    }

    private fun readSetting(context: Context, table: Int, key: String): String? =
        runCatching {
            val cr = context.contentResolver
            when (table) {
                0 -> Settings.Global.getString(cr, key)
                1 -> Settings.Secure.getString(cr, key)
                else -> Settings.System.getString(cr, key)
            }
        }.getOrDefault(null)

    /**
     * 只讀探測。順序：AOSP 能力 → 省電 → 反向鍵 → 正向鍵。
     * 任何一步失敗都繼續，最終一定回傳一個有效 MaterialProbe。
     */
    fun materialProbe(context: Context): MaterialProbe {
        val chinaRom = runCatching {
            val id = "${Build.MANUFACTURER} ${Build.BRAND} ${Build.PRODUCT}".lowercase()
            CN_ROM_BRANDS.any { it in id }
        }.getOrDefault(false)

        val crossWindow = crossWindowBlurEnabled(context)
        val backdrop = hasBackdropRenderEffect()
        val powerSave = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isPowerSaveMode ?: false
        }.getOrDefault(false)

        // 反向鍵優先：系統明確要求關閉時，唔好被另一個正向鍵覆寫。
        for (key in MATERIAL_DISABLE_KEYS) {
            for (table in 0..2) {
                val v = parseFlag(readSetting(context, table, key))
                if (v == 1) {
                    return MaterialProbe(
                        chinaRom, crossWindow, backdrop, powerSave,
                        key, -1, "CN_DISABLE_KEY"
                    )
                }
            }
        }
        for (key in MATERIAL_CANDIDATE_KEYS) {
            for (table in 0..2) {
                val v = parseFlag(readSetting(context, table, key))
                if (v != 0) {
                    return MaterialProbe(
                        chinaRom, crossWindow, backdrop, powerSave,
                        key, v, "CN_OEM_KEY"
                    )
                }
            }
        }
        val source = when {
            backdrop -> "AOSP_BACKDROP"
            crossWindow -> "AOSP_CROSS_WINDOW"
            !crossWindow -> "AOSP_NO_BLUR"
            else -> "AOSP_DEFAULT"
        }
        return MaterialProbe(chinaRom, crossWindow, backdrop, powerSave, null, 0, source)
    }

    /** 狀態列用：如實講讀到邊度，唔會扮「已連上廠商開關」。 */
    fun materialSourceLabel(probe: MaterialProbe): String = when (probe.source) {
        "CN_DISABLE_KEY" -> "CN_DISABLE_KEY"
        "CN_OEM_KEY" -> "CN_OEM_KEY"
        "AOSP_BACKDROP" -> "AOSP_BACKDROP"
        "AOSP_CROSS_WINDOW" -> "AOSP_CROSS_WINDOW"
        "AOSP_NO_BLUR" -> "AOSP_NO_BLUR"
        else -> "AOSP_DEFAULT"
    }
}

/**
 * CN_ADVANCED_MATERIAL — PURE decision layer（零 Android import、零 View）。
 *
 * mode：0 = 跟隨系統進階材質（預設）／1 = 強制完整液態／2 = 簡化靜態。
 * 無障礙設定（減少動態、高對比文字）永遠優先，即使用戶揀「完整」都唔會
 * 用動畫去干擾可讀性 —— 呢個係刻意嘅安全選擇，並會喺 UI 如實顯示。
 */
object NavGlassMaterial {

    const val MODE_FOLLOW = 0
    const val MODE_FULL = 1
    const val MODE_SIMPLE = 2

    data class Decision(
        val liquid: Boolean,
        val blur: Boolean,
        val level: Int,
        val reason: String
    )

    fun resolve(
        mode: Int,
        reducedMotion: Boolean,
        highContrast: Boolean,
        powerSave: Boolean,
        crossWindowBlur: Boolean,
        backdropEffect: Boolean,
        oemValue: Int,
        followSystem: Boolean
    ): Decision {
        // 無障礙先行：任何模式都唔覆寫。
        if (highContrast) return Decision(false, false, 0, "HIGH_CONTRAST")
        if (reducedMotion) return Decision(false, false, 0, "REDUCED_MOTION")

        if (mode == MODE_SIMPLE) {
            return Decision(false, false, 0, "USER_SIMPLE")
        }
        if (mode == MODE_FULL) {
            return Decision(true, crossWindowBlur, 2, "USER_FULL")
        }
        if (!followSystem) {
            return Decision(false, false, 1, "FIXED_APPEARANCE")
        }
        if (powerSave) {
            // 省電：保留極淡靜態折射邊，關流動高光同真模糊。
            return Decision(false, false, 0, "POWER_SAVE")
        }
        if (oemValue < 0) {
            return Decision(false, false, 0, "OEM_DISABLED")
        }
        if (oemValue > 0) {
            return Decision(true, crossWindowBlur, 2, "OEM_ENABLED")
        }
        if (backdropEffect && crossWindowBlur) {
            return Decision(true, true, 2, "AOSP_BACKDROP")
        }
        if (crossWindowBlur) {
            return Decision(true, true, 1, "AOSP_CROSS_WINDOW")
        }
        // ROM 停用模糊：實色玻璃 + 低 alpha 液態，唔留透明欄。
        return Decision(true, false, 1, "AOSP_NO_BLUR")
    }

    /** 0/1/2 以外一律回落跟隨系統，避免 SharedPreferences 壞值。 */
    fun sanitizeMode(mode: Int): Int = when (mode) {
        MODE_FULL, MODE_SIMPLE -> mode
        else -> MODE_FOLLOW
    }
}
