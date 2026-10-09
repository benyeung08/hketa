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
            version = "1.0.5",
            date = "2026-10-09",
            titleZhHant = "改名 HKATE、版本膠囊去徽章、背景修復收緊",
            titleZhHans = "改名 HKATE、版本胶囊去徽章、背景修复收紧",
            titleEn = "Renamed to HKATE, cleaner version pill, tighter background repair",
            itemsZhHant = listOf(
                "應用程式改名為 HKATE（顯示名「HKATE 巴士到站預報」），關於頁同複製版本號都跟埋改",
                "版本膠囊跟足 code-to-app 原版：移除圓點徽章，版面變成「v1.0.5 · 6 + 三個掣」；有新版本時 versionCode 數字會轉紅，唔使靠徽章都睇得出",
                "底部狀態欄、設定頁、關於頁三處膠囊繼續共用同一個元件，改一處三處都改",
                "修好「背景補充緊城巴／小巴資料」提示永久掛住唔消失嘅問題：而家淨係「而家補緊」先顯示，補完即收",
                "更重要：修好城巴／小巴車站其實從來冇自動補過嘅 bug —— 以前淨係第一次裝 App（重建索引）先會補，第二次開 App 起索引由磁碟載入就直接跳過，所以附近車站永遠淨係得九巴",
                "背景修復改為靜默模式：唔彈訊息、唔掝忙碌狀態，唔會蓋住你自己開嘅動作",
                "補齊簡體中文（values-b+zh+Hans）缺咗嘅 76 個字串，四份語言檔而家完全一致"
            ),
            itemsZhHans = listOf(
                "应用程序改名为 HKATE（显示名「HKATE 巴士到站预报」），关于页同复制版本号都跟着改",
                "版本胶囊跟足 code-to-app 原版：移除圆点徽章，版面变成「v1.0.5 · 6 + 三个按钮」；有新版本时 versionCode 数字会转红，不用靠徽章都看得出",
                "底部状态栏、设置页、关于页三处胶囊继续共用同一个元件，改一处三处都改",
                "修好「背景补充紧城巴／小巴资料」提示永久挂着不消失的问题：现在只有「正在补充」才显示，补充完即收",
                "更重要：修好城巴／小巴车站其实从来没有自动补过的 bug —— 以前只有第一次装 App（重建索引）才会补，第二次开 App 起索引由磁盘载入就直接跳过，所以附近车站永远只有九巴",
                "背景修复改为静默模式：不弹消息、不设置忙碌状态，不会盖住你自己开的动作",
                "补齐简体中文（values-b+zh+Hans）缺了的 76 个字符串，四份语言文件现在完全一致"
            ),
            itemsEn = listOf(
                "The app is now called HKATE (display name \u201cHKATE bus ETA\u201d), including the About page and the copy-version text",
                "The version pill now matches code-to-app exactly: the dot badge is gone, so it reads \u201cv1.0.5 \u00b7 6\u201d plus three icons; when an update exists the version number turns red, so the badge isn\u2019t needed",
                "The bottom bar, Settings and About still share one pill component, so a change lands in all three at once",
                "Fixed the \u201cadding Citybus / GMB stops in the background\u201d note never going away: it now shows only while a repair is actually running",
                "More importantly, fixed a bug where Citybus / GMB stops were never actually added: it only ran on first install (when the index was rebuilt) and was skipped on every later launch, so nearby stops were KMB-only forever",
                "Background repair is now silent: no toast and no busy flag, so it won\u2019t interrupt whatever you started yourself",
                "Filled in 76 strings missing from the Simplified Chinese (values-b+zh+Hans) file - all four language files now match"
            )
        ),
        Entry(
            version = "1.0.4",
            date = "2026-10-09",
            titleZhHant = "嶼巴英文名、地圖離線包、輕鐵自動掃描",
            titleZhHans = "屿巴英文名、地图离线包、轻铁自动扫描",
            titleEn = "NLB English names, offline map pack, auto Light Rail scan",
            itemsZhHant = listOf(
                "嶼巴站名補返英文：官方接口冇 name_en，所以內置咗約 90 個大嶼山站名嘅官方英文對照（政府地名 + 嶼巴站牌），唔係機器翻譯；查唔到一律維持中文",
                "地圖離線包：睇過嘅路線會自動預取 zoom 12–16 嘅瓦片存落本地，WebView 攞瓦片時由本地攔截供給，飛機模式都出到真實地圖（唔再靠唔可靠嘅 WebView 快取）",
                "輕鐵特別班次自動掃描：開「鐵路」分頁會自動喺背景掃（快取 7 日，過期自動重掃），唔使撳掣都有；手動即時重掃嘅掣照樣保留",
                "主頁會提示「背景補充緊城巴／小巴資料」，唔會再無聲無息淨係出九巴",
                "開 App 全自動：索引、ETA 資料修復、定位搵附近路線三樣都唔使再自己撳 —— 一開 App 就自動做",
                "ETA 資料修復新增逐個營辦商修復：九巴／城巴／嶼巴／專線小巴／港鐵巴士／港鐵／輕鐵各有自己嘅修復掣，淨係換嗰個營辦商嘅資料，唔使成個索引重建",
                "自動體檢：索引冇嘢／過期（超過 14 日）會開 App 自動重建，唔使去設定頁撳「ETA 資料修復」",
                "定位權限統一喺開 App 嗰陣請求一次，主頁唔會再彈多次對話框",
                "「版本更新」區塊改為 code-to-app 嗰種 VersionPill 膠囊：撳膠囊檢查更新，右邊三個掣係檢查更新／版本歷史／複製版本號",
                "底部狀態欄、設定頁、關於頁三處嘅版本膠囊統一為同一個共用元件，樣式完全一致"
            ),
            itemsZhHans = listOf(
                "屿巴站名补回英文：官方接口没有 name_en，所以内置了约 90 个大屿山站名的官方英文对照（政府地名 + 屿巴站牌），不是机器翻译；查不到一律维持中文",
                "地图离线包：看过的路线会自动预取 zoom 12–16 的瓦片存到本地，WebView 取瓦片时由本地拦截供给，飞行模式都能出真实地图（不再靠不可靠的 WebView 缓存）",
                "轻铁特别班次自动扫描：开「铁路」分页会自动在后台扫（缓存 7 天，过期自动重扫），不用按键都有；手动即时重扫的按钮照样保留",
                "主页会提示「背景补充紧城巴／小巴资料」，不会再无声无息只有九巴",
                "开 App 全自动：索引、ETA 资料修复、定位找附近路线三样都不用再自己按 —— 一开 App 就自动做",
                "ETA 资料修复新增逐个运营商修复：九巴／城巴／屿巴／专线小巴／港铁巴士／港铁／轻铁各有自己的修复按钮，净系换那个运营商的资料，唔使成个索引重建",
                "自动体检：索引没东西／过期（超过 14 天）会开 App 自动重建，不用去设置页按「ETA 资料修复」",
                "定位权限统一在开 App 时请求一次，主页不会再弹多次对话框",
                "「版本更新」区块改为 code-to-app 那种 VersionPill 胶囊：按胶囊检查更新，右边三个按钮是检查更新／版本历史／复制版本号",
                "底部状态栏、设置页、关于页三处的版本胶囊统一为同一个共用组件，样式完全一致"
            ),
            itemsEn = listOf(
                "NLB stops now have English names: the API has no name_en, so ~90 Lantau stop names ship with their official English names (government gazetteer + NLB signage) \u2014 not machine-translated; anything unmatched stays Chinese",
                "Offline map pack: viewed routes prefetch zoom 12\u201316 tiles to disk and the WebView serves them locally, so real maps render even in airplane mode (no longer reliant on the unreliable WebView cache)",
                "Light Rail special trips scan automatically: opening the Rail tab scans in the background (cached 7 days, rescans when stale) \u2014 no button press needed; the manual rescan button remains",
                "Home now says when Citybus / GMB stops are being added in the background, instead of silently showing KMB only",
                "Fully automatic on launch: index, ETA data repair and nearby-route location all run on startup - no buttons to press",
                "ETA data repair now has a per-operator button for KMB / Citybus / NLB / GMB / MTR Bus / MTR / Light Rail - it swaps just that operator\u2019s data instead of rebuilding the whole index",
                "Auto health check: an empty or stale index (older than 14 days) is rebuilt on startup, so you never need Settings then ETA data repair",
                "Location permission is requested once at startup instead of the Home tab prompting repeatedly",
                "The version-update block is now a code-to-app style VersionPill: tap the pill to check for updates, and the three icons are check / version history / copy version",
                "The version pill in the bottom bar, Settings and About is now one shared component, so all three look identical"
            )
        ),
        Entry(
            version = "1.0.3",
            date = "2026-10-08",
            titleZhHant = "已知限制大清掃：真實地圖、鐵路英文名、自動修復、版面重做",
            titleZhHans = "已知限制大清扫：真实地图、铁路英文名、自动修复、版面重做",
            titleEn = "Known-limitations cleanup: real map, rail English names, auto-repair, redesigned nearby",
            itemsZhHant = listOf(
                "路線地圖改用真實地圖：Leaflet + CARTO 深色瓦片，有底圖有街道、可縮放撳站名，唔使 API key；離線自動退回示意圖",
                "睇過嘅地圖有快取，離線都出到圖（第一次睇某條路線先要上網）",
                "鐵路站名加咗官方英文名：重鐵 98 個 + 輕鐵 68 個全部齊，英文介面顯示官方站名（唔係自行翻譯）",
                "鐵路頁線名喺英文介面下顯示官方英文名（如 Tuen Ma Line）",
                "新增手動選點：唔想授權定位都可以自己揀區／交通樞紐（16 個預設），主頁照樣搵到附近車站",
                "輕鐵特別班次（9xx）：鐵路頁加咗「掃描特別班次」，掃各站預報動態搵返出嚟，唔使硬編碼；結果有快取，掃一次之後開頁即刻顯示",
                "城巴／小巴深度修復改咗分批並發 + 增量，而且索引一建好就自動喺背景補，唔使人手撳「修復城巴／修復小巴」",
                "Widget 支援全部營辦商：之前係收藏冇記低路線，而家會記錄，Widget 淨係查嗰一條（1 個請求）",
                "附近車站版面重做：一個站做小標題，下面每條班次一張卡（路線號膠囊 + 目的地 + 分鐘數）",
                "撳任何一張班次卡 → 直接打開嗰條路線嘅沿途車站，唔使再經站嘅預報頁",
                "冇班次（例如過咗尾班車）嘅路線照樣出卡，撳落去睇沿途車站",
                "支援 Android 17（API 37）：targetSdk 升到 37，面向最新系統",
                "支援範圍：Android 8 – 17（minSdk 26 / targetSdk 37），Android 8 到 17 全部裝到",
                "工具鏈升級：AGP 9.1.1 + Gradle 9.3.1 + Kotlin 2.2.10（compileSdk 37 嘅硬門檻）",
                "大屏自適應：Android 17 喺平板上會自動轉為可調整大小，版面唔會爆",
                "嶼巴站名補返英文：官方接口冇 name_en，內置咗約 90 個大嶼山站名嘅官方英文對照（唔係機器翻譯），查唔到維持中文",
                "地圖離線包：睇過嘅路線自動預取瓦片存落本地，飛機模式都出到真實地圖（唔再靠 WebView 快取）",
                "輕鐵特別班次自動掃描：開鐵路頁自動喺背景掃（快取 7 日），唔使撳掣；照樣可手動即時重掃",
                "主頁會提示「背景補充緊城巴／小巴資料」，唔會再無聲淨係得九巴"
            ),
            itemsZhHans = listOf(
                "路线地图改用真实地图：Leaflet + CARTO 深色瓦片，有底图有街道、可缩放点站名，不需 API key；离线自动退回示意图",
                "看过的地图有缓存，离线也能出图（第一次看某条路线才需要上网）",
                "铁路站名加了官方英文名：重铁 98 个 + 轻铁 68 个全部齐，英文界面显示官方站名（不是自行翻译）",
                "铁路页线名在英文界面下显示官方英文名（如 Tuen Ma Line）",
                "新增手动选点：不想授权定位也能自己选区／交通枢纽（16 个预设），主页照样找得到附近车站",
                "轻铁特别班次（9xx）：铁路页加了「扫描特别班次」，扫各站预报动态找出来，不需硬编码；结果有缓存，扫一次之后开页立即显示",
                "城巴／小巴深度修复改成分批并发 + 增量，而且索引一建好就自动在背景补，不用手动点「修复城巴／修复小巴」",
                "Widget 支持全部运营商：之前是收藏没记录路线，现在会记录，Widget 只查那一条（1 个请求）",
                "附近车站版面重做：一个站做小标题，下面每条班次一张卡（路线号胶囊 + 目的地 + 分钟数）",
                "点任何一张班次卡 → 直接打开那条路线的沿途车站，不用再经站的预报页",
                "没有班次（例如过了末班车）的路线照样出卡，点进去看沿途车站",
                "支持 Android 17（API 37）：targetSdk 升到 37，面向最新系统",
                "支持范围：Android 8 – 17（minSdk 26 / targetSdk 37），Android 8 到 17 都能装",
                "工具链升级：AGP 9.1.1 + Gradle 9.3.1 + Kotlin 2.2.10（compileSdk 37 的硬门槛）",
                "大屏自适应：Android 17 在平板上会自动转为可调整大小，版面不会爆",
                "屿巴站名补回英文：官方接口没有 name_en，内置了约 90 个大屿山站名的官方英文对照（不是机器翻译），查不到维持中文",
                "地图离线包：看过的路线自动预取瓦片存到本地，飞行模式都能出真实地图（不再靠 WebView 缓存）",
                "轻铁特别班次自动扫描：开铁路页自动在后台扫（缓存 7 天），不用按键；照样可手动即时重扫",
                "主页会提示「背景补充紧城巴／小巴资料」，不会再无声只有九巴"
            ),
            itemsEn = listOf(
                "Route map now shows a real map: Leaflet + CARTO dark tiles with basemap, streets, zoom and tappable stop names — no API key. Falls back to the schematic view offline",
                "Maps you've already viewed are cached, so they render offline (only the first view of a route needs the network)",
                "Official English station names added: all 98 heavy-rail and 68 Light Rail stops, shown in the English interface (official names, not translations)",
                "Rail line names show their official English names (e.g. Tuen Ma Line) in the English interface",
                "New manual location picker: if you'd rather not grant GPS, pick a district or hub (16 presets) and Home still finds nearby stops",
                "Light Rail special trips (9xx): the Rail tab can scan stop arrivals to discover them dynamically — no hardcoded list; results are cached so one scan is enough",
                "Citybus / GMB repair is now batched-concurrent and incremental, and also runs automatically in the background once the index is built — no manual \"Repair Citybus / Repair GMB\"",
                "Widget now supports every operator: bookmarks never stored a route before, now they do, so the widget queries just that one route (a single request)",
                "Nearby stops redesigned: each stop is a small header, with one card per departure (route badge + destination + minutes)",
                "Tap any departure card to open that route's stop list directly — no detour through the stop's arrivals page",
                "Routes with no departures (e.g. after last bus) still get a card so you can view the stop list",
                "Android 17 (API 37) support: targetSdk raised to 37, targeting the newest platform",
                "Support range: Android 8 – 17 (minSdk 26 / targetSdk 37) — installs on everything from Android 8 to 17",
                "Toolchain upgrade: AGP 9.1.1 + Gradle 9.3.1 + Kotlin 2.2.10 — the hard minimum for compileSdk 37",
                "Large-screen adaptive: Android 17 makes the app resizable on tablets, and the layout holds up",
                "NLB stops now have English names: the API has no name_en, so ~90 Lantau stops ship with their official English names (not machine-translated); unmatched names stay Chinese",
                "Offline map pack: viewed routes prefetch their tiles locally, so real maps render even in airplane mode (no longer reliant on the WebView cache)",
                "Light Rail special trips scan automatically: opening the Rail tab scans in the background (cached 7 days) - no button needed, and you can still rescan manually",
                "Home now says when Citybus / GMB stops are being added in the background instead of silently showing KMB only"
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
