package com.hketa.app.data

import com.hketa.app.util.AppLocale

/**
 * 內置版本歷史（Changelog）—— 中英雙語。
 *
 * GitHub Releases 而家仲未出過，所以呢份係「離線」紀錄 —— 唔使聯網都睇到，
 * 且唔會因為 API 失敗而顯示空白。App 入面會先顯示呢份，
 * 若然成功由 GitHub 拉到 Release，再顯示「線上版本」嗰一段。
 *
 * 每筆記錄同時提供繁中、簡中、英文三份文案，
 * 顯示時用 [forLang] 依目前介面語言挑選。
 */
object Changelog {

    /** 一筆版本紀錄（三語） */
    data class Entry(
        val version: String,
        val date: String,               // YYYY-MM-DD
        val titleZhHant: String,
        val titleZhHans: String,
        val titleEn: String,
        val itemsZhHant: List<String>,
        val itemsZhHans: List<String>,
        val itemsEn: List<String>
    )

    /** 依語言解析後嘅單語紀錄 */
    data class Resolved(
        val version: String,
        val date: String,
        val title: String,
        val items: List<String>
    )

    /** 由新到舊 */
    val entries: List<Entry> = listOf(
        Entry(
            version = "1.0.4",
            date = "2026-10-08",
            titleZhHant = "附近車站改為「一條班次一張卡」，撳落去睇沿途車站",
            titleZhHans = "附近车站改为「一条班次一张卡」，点进去看沿途车站",
            titleEn = "Nearby stops: one card per departure, tap to see the route",
            itemsZhHant = listOf(
                "附近車站版面重做：一個站做小標題，下面每條班次一張獨立卡片（路線號膠囊 + 目的地 + 分鐘數）",
                "撳任何一張班次卡 → 直接打開嗰條路線嘅沿途車站，唔使再經站嘅預報頁",
                "冇班次（例如過咗尾班車）嘅路線照樣出卡，撳落去睇沿途車站",
                "5 分鐘內嘅班次分鐘數用主色強調"
            ),
            itemsZhHans = listOf(
                "附近车站版面重做：一个站做小标题，下面每条班次一张独立卡片（路线号胶囊 + 目的地 + 分钟数）",
                "点任何一张班次卡 → 直接打开那条路线的沿途车站，不用再经站的预报页",
                "没有班次（例如过了末班车）的路线照样出卡，点进去看沿途车站",
                "5 分钟内的班次分钟数用主色强调"
            ),
            itemsEn = listOf(
                "Nearby stops redesigned: each stop is a small header, with one card per departure (route badge + destination + minutes)",
                "Tap any departure card to open that route's stop list directly — no detour through the stop's arrivals page",
                "Routes with no departures (e.g. after last bus) still get a card so you can view the stop list",
                "Arrivals within 5 minutes are highlighted in the accent colour"
            )
        ),
        Entry(
            version = "1.0.3",
            date = "2026-10-08",
            titleZhHant = "已知限制大清掃：真實地圖、鐵路英文名、手動選點",
            titleZhHans = "已知限制大清扫：真实地图、铁路英文名、手动选点",
            titleEn = "Known-limitations cleanup: real map, rail English names, manual location",
            itemsZhHant = listOf(
                "路線地圖改用真實地圖：Leaflet + CARTO 深色瓦片，有底圖有街道、可縮放撳站名，唔使 API key；離線自動退回示意圖",
                "鐵路站名加咗官方英文名：重鐵 98 個 + 輕鐵 68 個全部齊，英文介面顯示官方站名（唔係自行翻譯）",
                "新增手動選點：唔想授權定位都可以自己揀區／交通樞紐（16 個預設），主頁照樣搵到附近車站",
                "輕鐵特別班次（9xx）：鐵路頁加咗「掃描特別班次」，掃各站預報動態搵返出嚟，唔使硬編碼",
                "城巴／小巴深度修復改咗分批並發 + 增量，時間同請求數大幅減少，唔再一定需要 Wi-Fi",
                "鐵路頁線名喺英文介面下顯示官方英文名（如 Tuen Ma Line）"
            ),
            itemsZhHans = listOf(
                "路线地图改用真实地图：Leaflet + CARTO 深色瓦片，有底图有街道、可缩放点站名，不需 API key；离线自动退回示意图",
                "铁路站名加了官方英文名：重铁 98 个 + 轻铁 68 个全部齐，英文界面显示官方站名（不是自行翻译）",
                "新增手动选点：不想授权定位也能自己选区／交通枢纽（16 个预设），主页照样找得到附近车站",
                "轻铁特别班次（9xx）：铁路页加了「扫描特别班次」，扫各站预报动态找出来，不需硬编码",
                "城巴／小巴深度修复改成分批并发 + 增量，时间和请求数大幅减少，不再一定需要 Wi-Fi",
                "铁路页线名在英文界面下显示官方英文名（如 Tuen Ma Line）"
            ),
            itemsEn = listOf(
                "Route map now shows a real map: Leaflet + CARTO dark tiles with basemap, streets, zoom and tappable stop names — no API key. Falls back to the schematic view offline",
                "Official English station names added: all 98 heavy-rail and 68 Light Rail stops, shown in the English interface (official names, not translations)",
                "New manual location picker: if you'd rather not grant GPS, pick a district or hub (16 presets) and Home still finds nearby stops",
                "Light Rail special trips (9xx): the Rail tab can now scan stop arrivals to discover them dynamically — no hardcoded list",
                "Citybus / GMB deep repair is now batched-concurrent and incremental, so it's much faster and no longer requires Wi-Fi",
                "Rail line names show their official English names (e.g. Tuen Ma Line) in the English interface"
            )
        ),
        Entry(
            version = "1.0.2",
            date = "2026-10-08",
            titleZhHant = "主頁自動定位 + 更新介面改版",
            titleZhHans = "主页自动定位 + 更新界面改版",
            titleEn = "Auto-locate on home + redesigned update UI",
            itemsZhHant = listOf(
                "主頁自動定位：一開波就搵附近路線，每 60 秒自動重整，唔使再撳掣",
                "自動定位狀態列：圓點顏色顯示「定位中／已定位／未授權／搵唔到」，撳圓點可開關",
                "更新介面改版：移植自 code-to-app，顯示「新版本 → 目前版本 → 大小」同可展開嘅 Release Notes",
                "版本歷史改用 BottomSheet：每個版本可撳開睇內容，目前版本會標「已安裝」，舊版本可獨立下載",
                "語義化版本比較：正式版永遠排喺預覽版之上（1.0.0 > 1.0.0-beta1），唔會再撞名",
                "Release Notes 中英自動切換：用 <!-- zh-CN --> 分隔，App 會按介面語言揀其中一半",
                "修好 17 個編譯錯誤（缺 LocatePhase / Box / size / width import）"
            ),
            itemsZhHans = listOf(
                "主页自动定位：一开就找附近路线，每 60 秒自动重整，不用再点按钮",
                "自动定位状态栏：圆点颜色显示「定位中／已定位／未授权／找不到」，点圆点可开关",
                "更新界面改版：移植自 code-to-app，显示「新版本 → 当前版本 → 大小」与可展开的 Release Notes",
                "版本历史改用 BottomSheet：每个版本可点开看内容，当前版本会标「已安装」，旧版本可独立下载",
                "语义化版本比较：正式版永远排在预览版之上（1.0.0 > 1.0.0-beta1），不会再撞名",
                "Release Notes 中英自动切换：用 <!-- zh-CN --> 分隔，App 会按界面语言选其中一半",
                "修好 17 个编译错误（缺 LocatePhase / Box / size / width import）"
            ),
            itemsEn = listOf(
                "Auto-locate on Home: finds nearby routes on launch and refreshes every 60s — no button needed",
                "Auto-locate status bar: dot colour shows Locating / Located / Not granted / Unavailable; tap the dot to toggle",
                "Update dialog redesigned, ported from code-to-app: shows New version → Current version → Size plus expandable release notes",
                "Version history moved to a bottom sheet: tap any release to expand its notes, current version tagged \"installed\", older builds downloadable individually",
                "Semantic version comparison: a stable release always ranks above any pre-release (1.0.0 > 1.0.0-beta1) — no more name collisions",
                "Bilingual release notes: split with <!-- zh-CN --> and the app picks the half matching the interface language",
                "Fixed 17 compile errors (missing LocatePhase / Box / size / width imports)"
            )
        ),
        Entry(
            version = "1.0.1",
            date = "2026-10-08",
            titleZhHant = "到站預報頁改版 + 站號顯示修正",
            titleZhHans = "到站预报页改版 + 站号显示修正",
            titleEn = "Arrivals page redesigned + stop-code fixes",
            itemsZhHant = listOf(
                "到站預報頁改為卡片式：站名大字、站號獨立一行、營辦商分行，路線號做成膠囊標籤",
                "修好站號位顯示 routeId：長度超過 12 位一律唔顯示，唔會再出現兩組括號同一串數字",
                "查詢層加保護：站號異常時自動改用索引入面同名同營辦商嘅站，避免查唔到班次",
                "主頁 / 收藏 / 車站搜尋統一用新嘅站名顯示，唔會重複括號",
                "修好版本歷史對話框喺簡體同英文介面下嘅顯示問題",
                "修好 Compose 編譯問題（titleWithCode 缺 @Composable 標註）"
            ),
            itemsZhHans = listOf(
                "到站预报页改为卡片式：站名大字、站号独立一行、营运商分行，路线号做成胶囊标签",
                "修好站号位显示 routeId：长度超过 12 位一律不显示，不会再出现两组括号同一串数字",
                "查询层加保护：站号异常时自动改用索引里同名同营运商的站，避免查不到班次",
                "主页 / 收藏 / 车站搜索统一用新的站名显示，不会重复括号",
                "修好版本历史对话框在简体与英文界面下的显示问题",
                "修好 Compose 编译问题（titleWithCode 缺 @Composable 标注）"
            ),
            itemsEn = listOf(
                "Arrivals page redesigned as a card: large stop name, stop code on its own line, operator on a separate line, route shown as a pill badge",
                "Fixed the stop-code slot showing a routeId: anything over 12 chars is hidden, so no more double parentheses and a string of digits",
                "Query-layer guard: when the stop code looks wrong, fall back to the same-named stop in the index so arrivals still resolve",
                "Home / Favourites / stop search now share one stop-name formatter — no repeated parentheses",
                "Fixed the version-history dialog under Simplified Chinese and English",
                "Fixed a Compose issue (titleWithCode was missing the @Composable annotation)"
            )
        ),
        Entry(
            version = "1.0.0",
            date = "2026-10-08",
            titleZhHant = "第一個正式版本：原生化完成 + 路線鍵盤",
            titleZhHans = "第一个正式版本：原生化完成 + 路线键盘",
            titleEn = "First stable release: fully native + route keypad",
            itemsZhHant = listOf(
                "路線專用鍵盤：數字 0–9 + 圓形字母鍵（A B C E K M N S X），唔使切換系統鍵盤版面",
                "索引診斷：統計頁講清楚邊個營辦商點解係 0（如城巴冇全量車站接口）",
                "小巴索引加後備：三個 region 都抓唔到會自動試官方全量路徑 /route",
                "底部狀態欄：版本 · 計數徽章 + 重新整理 / 版本歷史 / 檢查更新",
                "版本歷史：本地 changelog + GitHub Releases（有新資料就並列顯示）",
                "搜尋頁拆成「路線」/「車站」兩個獨立分頁，各自記住關鍵字",
                "「路線索引」改名為「ETA 資料修復」，加入「淨係清除快取」",
                "固定簽名（keystore/hketa.jks，有效期至 2056），唔會再出現「套件衝突」",
                "CI 只輸出一份正式版 APK"
            ),
            itemsZhHans = listOf(
                "路线专用键盘：数字 0–9 + 圆形字母键（A B C E K M N S X），不用切换系统键盘版面",
                "索引诊断：统计页讲清楚哪个营运商为什么是 0（如城巴没有全量车站接口）",
                "小巴索引加后备：三个 region 都抓不到会自动试官方全量路径 /route",
                "底部状态栏：版本 · 计数徽章 + 刷新 / 版本历史 / 检查更新",
                "版本历史：本地 changelog + GitHub Releases（有新资料就并列显示）",
                "搜索页拆成「路线」/「车站」两个独立分页，各自记住关键字",
                "「路线索引」改名为「ETA 数据修复」，加入「仅清除缓存」",
                "固定签名（keystore/hketa.jks，有效期至 2056），不会再出现「套件冲突」",
                "CI 只输出一份正式版 APK"
            ),
            itemsEn = listOf(
                "Route keypad: digits 0–9 + round letter keys (A B C E K M N S X) — no layout switching",
                "Index diagnostics: the stats page now explains why an operator shows 0 (e.g. Citybus has no bulk-stop API)",
                "GMB fallback: if all three regions return nothing, the full /route endpoint is tried automatically",
                "Bottom status bar: version · count badge + refresh / version history / check for updates",
                "Version history: local changelog + GitHub Releases (shown side by side when available)",
                "Search split into separate Routes / Stops tabs, each keeping its own query",
                "\"Route index\" renamed to \"ETA data repair\", plus a \"Clear cache only\" option",
                "Fixed signing key (keystore/hketa.jks, valid to 2056) — no more \"package conflicts\"",
                "CI now emits a single release APK"
            )
        ),
        Entry(
            version = "0.9.0",
            date = "2026-10-07",
            titleZhHant = "ETA 資料修復與介面重整",
            titleZhHans = "ETA 数据修复与界面重整",
            titleEn = "ETA data repair and UI overhaul",
            itemsZhHant = listOf(
                "修好城巴 / 小巴「暫時冇車站資料」：改用分批並發 + 站號兜底",
                "介面改為 App1933 風格：主頁顯示附近車站即時預報",
                "新增收藏車站，收藏頁並發刷新全部 ETA",
                "桌面小工具（Widget）：顯示收藏站下一班車",
                "路線地圖：沿途車站頁可顯示 Canvas 走線圖",
                "英文介面改用官方 name_en 站名；鐵路線補官方英文名",
                "新增 values-b+zh+Hans，zh-SG 等簡體語系唔會再跌落繁體"
            ),
            itemsZhHans = listOf(
                "修好城巴 / 小巴「暂时没有车站资料」：改用分批并发 + 站号兜底",
                "界面改为 App1933 风格：主页显示附近车站即时预报",
                "新增收藏车站，收藏页并发刷新全部 ETA",
                "桌面小工具（Widget）：显示收藏站下一班车",
                "路线地图：沿途车站页可显示 Canvas 走线图",
                "英文界面改用官方 name_en 站名；铁路线补官方英文名",
                "新增 values-b+zh+Hans，zh-SG 等简体语系不会再跌落繁体"
            ),
            itemsEn = listOf(
                "Fixed Citybus / GMB \"no stop data\": batched concurrency + stop-code fallback",
                "UI reworked in App1933 style: home shows live arrivals for nearby stops",
                "Bookmarked stops, with the Favourites tab refreshing all of them concurrently",
                "Home-screen widget showing the next departure at bookmarked stops",
                "Route map: Canvas-drawn polyline on the stop list screen",
                "English UI now uses official name_en stop names; rail lines got official English names",
                "Added values-b+zh+Hans so zh-SG and other Simplified locales no longer fall back to Traditional"
            )
        ),
        Entry(
            version = "0.8.0",
            date = "2026-10-06",
            titleZhHant = "加入港鐵重鐵、輕鐵與專線小巴",
            titleZhHans = "加入港铁重铁、轻铁与专线小巴",
            titleEn = "MTR heavy rail, Light Rail and GMB minibus",
            itemsZhHant = listOf(
                "港鐵重鐵 10 條線 98 個站（離線內置，含官方座標）",
                "輕鐵 11 條常規線 68 個站",
                "專線小巴深度索引，附近車站納入小巴站",
                "底欄改為四個分頁：主頁 / 收藏 / 搜尋 / 設定"
            ),
            itemsZhHans = listOf(
                "港铁重铁 10 条线 98 个站（离线内置，含官方坐标）",
                "轻铁 11 条常规线 68 个站",
                "专线小巴深度索引，附近车站纳入小巴站",
                "底栏改为四个分页：主页 / 收藏 / 搜索 / 设置"
            ),
            itemsEn = listOf(
                "MTR heavy rail: 10 lines, 98 stations (bundled offline, with official coordinates)",
                "Light Rail: 11 regular routes, 68 stops",
                "GMB deep indexing — minibus stops now appear in Nearby",
                "Bottom bar reworked into four tabs: Home / Favourites / Search / Settings"
            )
        ),
        Entry(
            version = "0.7.0",
            date = "2026-10-05",
            titleZhHant = "三語介面與版本更新檢查",
            titleZhHans = "三语界面与版本更新检查",
            titleEn = "Trilingual UI and update checker",
            itemsZhHant = listOf(
                "繁體中文 / 简体中文 / English 三語切換，跟隨系統",
                "設定頁加入 GitHub Releases 版本更新檢查",
                "到站預報備註（月台、經馬場、服務延誤）跟隨語言"
            ),
            itemsZhHans = listOf(
                "繁体中文 / 简体中文 / English 三语切换，跟随系统",
                "设置页加入 GitHub Releases 版本更新检查",
                "到站预报备注（月台、经马场、服务延误）跟随语言"
            ),
            itemsEn = listOf(
                "Traditional / Simplified Chinese / English switching, follows the system",
                "Settings gained a GitHub Releases update checker",
                "Arrival remarks (platform, via Racecourse, service delay) are localised"
            )
        ),
        Entry(
            version = "0.6.0",
            date = "2026-10-04",
            titleZhHant = "原生化重寫",
            titleZhHans = "原生化重写",
            titleEn = "Native rewrite",
            itemsZhHant = listOf(
                "由 WebToApp（WebView 外殼）完全改寫為 Kotlin + Jetpack Compose 原生 App",
                "直接對接九巴 / 城巴 / 嶼巴 / 專線小巴 / 港鐵巴士官方 API",
                "路線搜尋、沿途車站、到站預報、附近車站（GPS）",
                "換上專屬 App 圖示（Adaptive Icon）"
            ),
            itemsZhHans = listOf(
                "由 WebToApp（WebView 外壳）完全改写为 Kotlin + Jetpack Compose 原生 App",
                "直接对接九巴 / 城巴 / 屿巴 / 专线小巴 / 港铁巴士官方 API",
                "路线搜索、沿途车站、到站预报、附近车站（GPS）",
                "换上专属 App 图标（Adaptive Icon）"
            ),
            itemsEn = listOf(
                "Fully rewritten from the WebToApp WebView shell into a native Kotlin + Jetpack Compose app",
                "Talks directly to the KMB / Citybus / NLB / GMB / MTR Bus official APIs",
                "Route search, route stops, arrivals, nearby stops (GPS)",
                "New Adaptive Icon"
            )
        )
    )

    /** 語言代碼 → 解析後嘅單語列表 */
    fun forLang(lang: String): List<Resolved> {
        val zhHans = lang.equals(AppLocale.ZH_HANS, ignoreCase = true) ||
            lang.startsWith("zh", ignoreCase = true) && !lang.contains("TW", true) &&
            !lang.contains("HK", true) && !lang.contains("Hant", true)
        val en = lang.equals(AppLocale.EN, ignoreCase = true) ||
            lang.startsWith("en", ignoreCase = true)

        return entries.map { e ->
            when {
                en -> Resolved(e.version, e.date, e.titleEn, e.itemsEn)
                zhHans -> Resolved(e.version, e.date, e.titleZhHans, e.itemsZhHans)
                else -> Resolved(e.version, e.date, e.titleZhHant, e.itemsZhHant)
            }
        }
    }

    /**
     * 由 GitHub Release 轉成本地紀錄（用嚟顯示線上嗰一段）。
     * Release notes 本身係開發者寫嘅原文，所以三語欄位全部填同一份。
     */
    fun fromRelease(r: GitHubRelease): Resolved {
        val items = r.body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.removePrefix("- ").removePrefix("* ").trim() }
            .filter { it.isNotBlank() }
            .take(12)
            .toList()
            .ifEmpty { listOf(r.body.take(200).ifBlank { "（冇更新說明）" }) }
        return Resolved(
            version = r.tag_name.removePrefix("v").removePrefix("V").ifBlank { r.name },
            date = r.published_at.take(10),
            title = r.name.ifBlank { r.tag_name },
            items = items
        )
    }
}
