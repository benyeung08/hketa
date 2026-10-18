package com.hketa.app.data

/**
 * **外部聚合資料嘅「防倒退」守門員。**
 *
 * 呢個 object 係刻意寫成**零 Android 依賴**嘅純函數 ——
 * 唔 import `Context`、唔 import `R`、唔掂任何 `Models` 入面嘅類型，
 * 淨係食 `Int` 同吐 `String?`。原因係要可以直接喺 JVM 單測入面跑
 * （見 `app/src/test/.../DataGuardTest.kt`），唔使开 Robolectric。
 *
 * ## 點解要有呢個
 *
 * 車站資料係由 GitHub 上每日聚合嘅一份檔（`data.hkbus.app`）補返嚟嘅
 * （見 [HkbusIndex]）。呢份檔**唔係我哋控制嘅** —— 佢哋聚合到一半、
 * 欄位改名、或者當日 upstream 掛咗，都會出一份「數量正常但內容殘」嘅檔。
 *
 * 如果照單全收：
 *
 * * 一次壞資料就會**覆蓋掉機上嗰份好嘅快取**
 * * 之後重建索引 → 城巴車站由幾千變零
 * * 而且**冇任何提示**，用戶只會覺得「點解突然冇咗」
 *
 * 所以落碟之前要過兩關：
 *
 * 1. [shouldReject] —— 同上一次比，條目數**暴跌**就唔寫
 * 2. [fieldSanity] —— 欄位命中率，關鍵欄位一個都冇就當格式變咗
 */
object DataGuard {

    /**
     * 跌幅超過呢個比例就當壞資料。
     *
     * 揀 30% 而唔係更細，係因為官方真係會刪線合併路線（例如城巴重組），
     * 日常跌幅通常係幾個 %；但聚合到一半一定係腰斬級（>50%）。
     * 30% 係兩者之間一條安全線。
     */
    private const val DROP_RATIO = 0.30

    /**
     * 第一次落（機上冇舊資料可以比）嗰陣嘅**絕對下限**。
     *
     * 全港城巴 ＋ 專線小巴 ＋ 港鐵巴士嘅車站加埋係幾千個量級，
     * 所以要求最少 200 係非常保守 —— 聚合壞咗通常係零或者幾十。
     */
    private const val MIN_FIRST_RUN_STOPS = 200

    /** 要判斷「欄位係咪消失咗」，最少要睇到咁多個站先算數 */
    private const val MIN_SAMPLES_FOR_FIELD = 50

    /**
     * 條目數係咪要拒絕寫入。
     *
     * @param prevStops 上一次落咗幾多個站；**0 代表機上冇舊資料**（第一次落）
     * @param prevNames 上一次落咗幾多條中英對照
     * @param nextStops 今次解析到幾多個站
     * @param nextNames 今次解析到幾多條中英對照
     * @return `null` 代表 OK 可以寫；有嘢返就係**拒絕嘅原因**（直接畀用戶睇）
     */
    fun shouldReject(
        prevStops: Int,
        prevNames: Int,
        nextStops: Int,
        nextNames: Int
    ): String? {
        // 完全冇嘢 —— 連 `stops.isEmpty()` 嗰關都未過，唔使講
        if (nextStops <= 0) return "解析唔到車站（可能格式變咗）"

        // 第一次落：冇得比，改用絕對下限
        if (prevStops <= 0) {
            return if (nextStops < MIN_FIRST_RUN_STOPS) {
                "第一次落只解析到 $nextStops 個站（少過 $MIN_FIRST_RUN_STOPS），" +
                    "似係聚合壞咗 —— 未寫落機"
            } else null
        }

        val floor = (prevStops * (1.0 - DROP_RATIO)).toInt()
        if (nextStops < floor) {
            return "今次得 $nextStops 個站，上次係 $prevStops（跌咗 " +
                "${dropPct(prevStops, nextStops)}%）—— 似係聚合壞咗，沿用上次嗰份"
        }

        // 英文名對照係後加嘅（解決「嶼巴英文名」限制），所以舊快取可能係 0。
        // 淨係喺「上次有、今次無」嗰陣先拒絕。
        if (prevNames > 0 && nextNames == 0) {
            return "今次一條英文名對照都冇（上次有 $prevNames 條）—— 沿用上次嗰份"
        }

        return null
    }

    /** 跌幅百分比（整數，方便顯示） */
    private fun dropPct(prev: Int, next: Int): Int {
        if (prev <= 0) return 0
        val d = ((prev - next).toDouble() / prev.toDouble()) * 100.0
        return d.toInt().coerceIn(0, 100)
    }

    /**
     * 欄位命中率係咪正常。
     *
     * 呢個係捉**靜默降級**嘅：假如 `stopList[x].name.en` 改咗名做
     * `nameEn`，解析器唔會報錯，只係每條都拎到空字串 ——
     * 英文名會全部消失、靜靜雞降級去內置嗰 154 個，用戶永遠唔會發現。
     *
     * 所以寧願**大聲失敗**：睇咗咁多站而一個都拎唔到，即係格式變咗。
     *
     * @param stopsSeen 解析器睇咗幾多個站
     * @param nameEn 當中有幾多個拎到官方英文名
     * @param locLat 當中有幾多個拎到緯度
     * @return `null` 代表正常；有嘢返就係**格式出事嘅原因**
     */
    fun fieldSanity(stopsSeen: Int, nameEn: Int, locLat: Int): String? {
        if (stopsSeen < MIN_SAMPLES_FOR_FIELD) return null

        if (nameEn == 0) {
            return "睇咗 $stopsSeen 個站但一個官方英文名都拎唔到 —— " +
                "`stopList` 嘅 `name.en` 欄位可能改咗名"
        }
        if (locLat == 0) {
            return "睇咗 $stopsSeen 個站但一個座標都拎唔到 —— " +
                "`stopList` 嘅 `location.lat` 欄位可能改咗名"
        }
        return null
    }

    /**
     * 畀設定頁顯示用：呢份資料**係咪健全**。
     *
     * 唔使落嘢嗰陣都可以 call —— 淨係睇數字。
     */
    fun verdict(prevStops: Int, prevNames: Int, nextStops: Int, nextNames: Int): String =
        shouldReject(prevStops, prevNames, nextStops, nextNames) ?: "OK"
}
