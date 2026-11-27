# HK ETA v1.0.3 · 香港實時到站 / Hong Kong real-time ETA

> **收錄範圍：2026-11-25 至最新（2026-11-26）**｜**Scope: 2026-11-25 through the latest (2026-11-26)**。
> 本版收錄 `v1.0.2` 之後嘅**全部後續改動**：開 App 更新一次、香港出行易式分類、代碼優化、顯示所有路線、液態玻璃加入液態、中國版 Android OS 17 進階材質、單一服務商只顯示一間、循環線頂欄兩顆動作、三個圖標語義，同埋服務時間及班次改由政府公開資料（運輸署 GTFS）獲取。
> This release covers **every follow-up change** after `v1.0.2`: refresh route data once on app launch, HKeMobility-style search categories, code optimisation, all-routes display, the liquid layer on the glass bar, China-ROM Android OS 17 advanced material, single-operator routes showing exactly one operator, circular routes showing two header actions, the three header-icon meanings, and service hours & trips now coming from government open data (Transport Department GTFS).

> **版本分界／Version boundary**：`RELEASE_NOTES_v1.0.0.md` 截至 2026-11-19；`RELEASE_NOTES_v1.0.1.md` 由 2026-11-20 起整理資料層、班次邏輯同發布鏈；`RELEASE_NOTES_v1.0.2.md` 收錄 2026-11-24 嘅搜尋介面、定位、卡片、導航、本地資料同 Kotlin 編譯修正；**`v1.0.3` 收錄 11-25 起至最新嘅九組後續改動**。`v1.0.2` 說明已移除相應 highlight，舊檔保留作歷史、唔郁已簽名 APK／tag。
> `RELEASE_NOTES_v1.0.0.md` covers through 2026-11-19; `RELEASE_NOTES_v1.0.1.md` starts at 2026-11-20 with the data-layer, trip-logic and release-pipeline work; `RELEASE_NOTES_v1.0.2.md` covers 2026-11-24 (search UI, location, cards, navigation, local data and the Kotlin compile fix); **`v1.0.3` covers the nine follow-up change groups from 11-25 through the latest**. The matching bullets were removed from the `v1.0.2` notes; older files are kept as history and no signed APK or tag is touched.

> 本檔係**純文字中英雙語 Release 說明**，可直接貼入 GitHub Release 描述框；`RELEASE_NOTES_${TAG}.md` 會由 `v1.0.3` tag build 自動選用做該版本 Release 內文。
> This is a **plain-text bilingual Release note** you can paste straight into the GitHub Release description; `RELEASE_NOTES_${TAG}.md` is selected automatically by the `v1.0.3` tag build as that version's Release body.

---

## 中文（繁體）

### 🆕 本版更新重點（2026-11-25 → 最新 2026-11-26）

#### 📅 2026-11-25：目錄更新、分類還原、代碼優化

**① 開 App 就更新線路資料一次（LAUNCH_CATALOGUE_REFRESH）**

- **🚀 每次開 App 自動更新一次**：`HkEtaApp.onCreate()` 喺 `RouteDataStore.init(this)` 之後，每個 process 觸發一次 `RouteDataStore.refresh(false)`；放喺 Application 而唔係 Activity，因為 `onCreate` 每個 process 只行一次，最對應「一次」
- **🧩 純 Kotlin process 閘**：`LaunchCatalogueRefresh.claimOnce()`（`AtomicBoolean.compareAndSet`）保證一次冷啟動只下載一次；`CatalogueRefreshRunner.runOnce()` 係 Application 同 `EtaViewModel` 共用嘅單一入口，ViewModel 只做安全後備，唔會重複下載
- **🚫 唔再受排程節流**：移除 `RouteDataPrefs.isDue()` 嘅每日／每 3 日／每星期判斷，連設定「0 = 手動」都照更新；但**尊重「僅 Wi-Fi 更新」**、`RouteDataStore.refreshBusy()` 讓路，`BUSY` 原子閘雙重保險
- **🔄 完成即時生效**：成功後寫 `markChecked`，`CatalogueRefreshNotifier`（Application 級 `LiveData`）通知 `MainActivity` 即刻 `renderLocalDataStatus()` ＋ `searchRoutes()` ＋ `refreshHistory()`，新快照唔使等下一次 `onResume`；`observe(this)` 喺 DESTROY 自動移除
- **🖐 手動更新唔受影響**：設定頁「線路資料更新」照舊隨時撳隨時更新；失敗／冇 Wi-Fi 靜默跳過、唔自動重試、唔假裝成功，唔干擾 ETA 查詢
- **📌 「一次」嘅定義**：一次冷啟動（一個 process）一次；按 Home 返前景、轉螢幕、切分頁都唔會再跑

**② 搜尋功能還原香港出行易式分類（HKE_MOBILITY_CATEGORIES）**

- **🚌 巴士公司路線分開喺所屬按鈕**：新增純 Kotlin `SearchCategoryRules.belongs()`；「所有巴士類別」只 `KMB／CTB／NLB／JOINT`，唔再 `return true` 把小巴／港鐵／輕鐵撈埋入巴士頁；城巴＝`CTB`＋聯營、九巴＝`KMB`＋`brand=KMB`、龍運＝`KMB`＋`brand=LWB`、嶼巴＝`NLB`；聯營 `城巴/九巴` 同時喺城巴同九巴兩個按鈕出現（⑩ 再按官方 GTFS 把九巴／龍運分開）
- **🚇 港鐵類別顯示返重鐵＋輕鐵＋港鐵接駁巴士**：`MtrCatalog.LRT_ROUTES` 補回官方輕鐵路線號碼表（505／507／610／614…），`RouteSearch.railPreload()` 補返 LRT 列；港鐵 tab 同時見到 `MTR`／`LRT`／`MTRB`
- **🚐 小巴路線歸小巴類別**：`CAT_MINIBUS` 只 `GMB`，小巴路線唔再喺巴士頁出現；渡輪／電車／的士／非專線小巴／居民巴士服務官方冇同類公開實時 ETA API，如實留空、唔捏造班次
- **📊 預載唔再硬 cut**：`MainActivity.refreshHistory()` 唔再硬 `take(6)`，八個 tab／六個 chips 都顯示所屬公司嘅官方列；分類行為還原，截圖式版面同色盤零改動
- **🎨 零改動保證**：10 個 `search_*` 色值、`values-night` 零覆寫、8 營運商資料層、ETA 永遠即時查、聯營／循環／同號分卡／前景位置更新、簽章與 Release 鏈全部一個都冇郁

**③ 代碼優化（CODE_OPTIMISATION，零行為改變）**

- **🔢 消除重複常數**：`RouteSearch.PRELOAD_PER_COMPANY` 做單一來源，`SearchCategoryRules` 引用佢，依賴方向修正為 ui → api，唔使改一次改兩處
- **🧹 消滅孤兒函式**：`capPerCompany()` 改 generic 並真正被 `MainActivity` 呼叫，取代原本重複嘅 `groupBy{}.flatMap{take()}`，`filter` 保官方排序
- **⚡ 每次 keystroke 唔再 new Comparator**：抽出 `PRELOAD_ORDER`／`SEARCH_ORDER` 兩個 `static final`；checker `read()` 加 memoize，同一份 source 實測 **142 ms → 125 ms（約 -12%）**，兩版輸出完全一致
- **🧪 行為等價**：15 列逐個類別對照，港鐵／所有巴士／小巴／城巴／九巴／嶼巴／全部 **7 組新舊列數同順序完全一致**；未用 import 掃描 0 個，版本同兩份 Release 說明一個字都唔使改

#### 📅 2026-11-26（最新）：所有路線、液態玻璃、進階材質

**④ 顯示所有路線（ALL_ROUTES：定位附近路線 ＋ 搜尋）**

- **📊 搜尋解除所有配額**：`RouteSearch.preload()` 由「頭 24 列／每公司 3 列」改為**全部官方目錄列**（`ALL_ROUTES_LIMIT = Integer.MAX_VALUE`，重鐵／輕鐵／接駁巴士三份官方碼表一條都唔少）；`search()` 唔再 cut 40／24 列，`EVERY official catalogue row` 都返回
- **📍 附近路線解除所有配額**：`CANDIDATE_LIMIT`／`ENTITY_LIMIT`／`MAX_QUERIES` 全部 `Int.MAX_VALUE` → 半徑內**所有站點、所有實體站、所有公司別名**；狀態列如實報「N 個站 · M 條路線」
- **🛡️ 分批防 429**：ETA 查詢改成每批 12 個逐批 `awaitAll()`、批次間讓 **120ms**；城巴官方明列 `429 Processing too many requests`，查失敗嘅站由 `safeEta` 如實略過、唔捏造班次，首次載入會慢啲
- **🔁 共用同一條鏈**：初始自動定位、`LocationTracker` 前景位置更新同 ETA 自動刷新共用 expanded 快取；`RouteSearchHarness` 預載由 15 列增至 **53 列**，`search("1")` 仍係原本 4 列

**⑤ 玻璃質感加入液態（NAV_LIQUID）**

- **✨ 折射亮邊**：由上淡出嘅 `GradientDrawable` ＋ 1dp 內描邊，`ViewOutlineProvider` 裁成同一個 28dp 圓角，高光唔會漏出藥丸邊
- **🌊 流動高光**：168dp 白色漸變由左扫到右再返，`ValueAnimator` `INFINITE / REVERSE` 約 4.6 秒；API 31+ 加 `RenderEffect.createBlurEffect` 輕微散射，全部 `runCatching` 包住
- **🫧 阻尼回彈**：選中分頁 `NavLiquid.springScale` 做 `0.88 → 1.032 overshoot → 1.0`；純決策層 `NavLiquid` 零 Android import，可離線驗證
- **♿ 誠實降級**：高對比文字／減少動態／關閉「跟隨系統外觀」→ `alpha=0`、動畫 cancel、`RenderEffect` 清走；層 `clickable=false` 觸控穿透，`onPause()` 停、`onResume()` 重啟，前景 only
- **📐 幾何零改動**：56dp 高、28dp 全圓角、離底 12%（48–160dp）、左右 6%／18% 自適應、四個純文字分頁、藍字同 5 個玻璃 drawable 全部照舊

**⑥ 中國版 Android OS 17 進階材質（CN_ADVANCED_MATERIAL）**

- **🔎 只讀容錯探測**：`NavGlassCompat.materialProbe()` 查 AOSP 公開能力（`isCrossWindowBlurEnabled`、Android 17 `setBackdropRenderEffect`）、`PowerManager.isPowerSaveMode()`、動畫比例、高對比，再用候選鍵 best-effort 讀 `Settings.Global/Secure/System`；讀到就尊重、讀唔到用 AOSP 能力
- **🚫 唔編造私有 key**：品牌只排 probe 順序，唔會因為係國內 ROM 就硬開功能；**唔寫入任何系統設定、唔靠 GMS**，狀態列如實顯示 `CN_OEM_KEY`／`AOSP_BACKDROP`／`AOSP_NO_BLUR` 來源
- **🧠 純決策層**：`NavGlassMaterial.resolve()` 出 `液態／模糊／等級／原因`；省電、高對比、減少動態一律優先簡化，連用戶揀「完整液態」都唔覆寫無障礙設定；`sanitizeMode()` 把壞值回落跟隨系統
- **🎛️ 三態 Spinner**：設定頁新增「進階材質」（跟隨系統／完整液態／簡化靜態），只寫本 App `SharedPreferences`；11 情境決策模擬＋無障礙優先 3×2 全過
- **📦 零新增資源**：0 新增檔／色值／依賴／權限，當時版本 `1.0.2`／`3`（本版已 bump 到 `1.0.3`／`4`）、簽章鏈同任何已簽名 APK／tag 一個都冇郁


**⑦ 單一服務商只顯示一間（SINGLE_OPERATOR_DISPLAY）**

- **🚌 聯營先顯示兩間**：只有同一路線＋同一目的地**實際同時有 KMB 同 CTB 兩間官方公司**嘅列／班次，先合併做 `城巴/九巴` 聯營卡／搜尋列，並逐班標「該班次：城巴／九巴」（沿用你之前明確要求，唔推翻）
- **🚫 根因：目錄交疊 ≠ 兩間服務商**：`JointRoutes.isJointRoute()` 只代表兩份官方目錄都有同一路線號；舊 `markJoint()` 單靠呢個就把**只有一列 KMB（或一列 CTB）**都升級做 `JOINT`，望落「一間服務商顯示咗兩間」
- **🧠 純決策層**：`JointRoutes.bothOperatorsPresent(Set)`／`displayCompany(Set, fallback)` 定死「真兩間 → JOINT；否則 → 原公司一個 label」；`bothContain()` 目錄判定保留做聯營候選，唔捏造第二間
- **🔎 搜尋頁修正**：`RouteSearch.markJoint()` 先按 `(路線, 目的地)` 收集**實際出現**嘅公司，只有 `bothOperatorsPresent` 先合併；單一列維持自己所屬公司一個 label（當時係共用目錄標籤，⑩ 已按官方 GTFS 分開），唔加 `城巴/九巴` note；第三家（嶼巴／小巴）同號亦唔會被聯營吞走
- **📇 卡片頁一致**：`SameRouteOperators.partition()` 本來就係「單公司 → 1 張卡、KMB+CTB 非循環 → 1 張聯營卡、其餘逐間拆」；`JointRoutes.augment()` 尾加 guard，partner 冇真實回報就原封不動返 primary，唔會把單公司 ETA 升級做兩間
- **🎨 零改動**：8 營運商資料層、ETA 即時查、10 個 `search_*` 色值、`values-night` 零覆寫、當時既有 KMB/LWB 共用目錄標籤、當時版本 `1.0.2`／`3`（本版已 bump 到 `1.0.3`／`4`）同簽章鏈一個都冇郁（⑩ 之後先至按官方 GTFS 分開標籤）


**⑧ 循環線頂欄兩顆動作（CIRCULAR_TWO_ACTIONS）**

- **🔴 對返兩張參考圖**：單向路線紅頂欄係**三顆圖標（日曆／通知／⇅）**（第一張圖）；**所有循環線只有呢兩個圖標** —— ① **日曆＝服務時間及班次**、② **通知＝服務通告**（第二張圖），第三顆 ⇅ 完全唔顯示
- **🔁 官方依據**：循環線得一個總站、行返去同一個總站，官方 route-stop 資料**冇回程方向（NO inbound direction）**；第三顆「⇄ 轉換方向」對循環線係無意義嘅死掣，所以隱藏；普通路線維持三顆、一個都唔郁
- **🧠 單一來源**：`CircularRoutes.visibleHeaderActions(boolean circular)` 直接返回圖標 id 列表 —— 循環線 `[CALENDAR, NOTICE]`（**只有兩顆**）、單向 `+ DIRECTION`（三顆）；`showDirectionAction(boolean circular)` 仍保留並由佢派生，零 Android／零網絡、可離線測；`wireActions()` 按列表**顯式**設頭兩顆 `VISIBLE`、第三顆 `GONE`／`VISIBLE`
- **🆔 零資源改動**：`detailActionDirection` 個 id、`setOnClickListener`、`bg_detail_action_circle`、`@color/brand` 紅底、另外兩顆掣同四個 chip 全部保留 —— 只改可見性，**唔加／唔減 View、唔加色值、唔加 drawable、唔改點擊邏輯**
- **♿ 一致又唔會誤觸**：GONE 咗自然唔會被點到；循環線 caption 仍如實顯示 `九巴循環線`／`城巴循環線` badge，唔會因為少咗掣就扮有回程方向
- **✅ 離線實跑**：`CircularHarness` 39 → **47** 項（新增「循環線 exactly 兩顆且冇 DIRECTION」、「循環線日曆同單向係同一個服務時間及班次動作」、「官方 `(循環線)` 目的地都係兩顆」），`CircularRoutes` 其餘判定／站名核對一個都冇改


**⑨ 三個圖標語義：日曆＝服務時間及班次（DETAIL_THREE_ICONS）**

- **🔴 照返第一張參考圖**：所有**單向（非循環）路線**嘅詳情頁紅頂欄**只有呢三個圖標（三顆圓形動作）** —— ① **日曆**（服務時間及班次）／② **通知**（服務通告）／③ **切換方向**（⇅）；上一版用緊嘅 `▦`／`★`／`⇄` 字符圓掣全部換成紅色 vector 圖標（`ic_detail_calendar`／`ic_detail_notice`／`ic_detail_direction`），白圓底 `bg_detail_action_circle` 同 `@color/brand` 紅底不變
- **📅 日曆圖標＝服務時間及班次（GOV_SERVICE_HOURS）**：**由政府公開資料獲取** —— 運輸署「公共交通服務班次資料」(`hk-td-tis_11-pt-headway-en`) 繁中版 GTFS，經 `RouteDataStore.refreshGovServiceHours()` 下載 `pt-headway-tc` 嘅 `routes.txt`／`calendar.txt`／`calendar_dates.txt`／`trips.txt`／`frequencies.txt` 同 `DATA_LAST_UPDATED_DATE.csv` 到本機快取；`GovServiceHours.lookup()` 解析出**服務日、頭班／尾班、服務時段、班次間隔**（`headway_secs`），並顯示**數據修正截止日期**。日曆 dialog 仍然**唔行即時 ETA、唔快取到站時間**；政府資料未載入或冇呢條路線就如實降級去 `LocalServiceHours.lookup`／`RouteDataStore.readText` 嘅**本地路線資料**後備（方向 × 服務類型），**not invented／唔捏造時間表**，同車資「—」同一原則；循環線同一個 dialog 完全唔縮水

- **🏛️ 真係政府公開資料，唔係單一 JSON**：呢份班次資料係 **GTFS UTF-8 CSV `.txt`**（整合套裝 `gtfs.zip` 都得），即時 ETA 先係 JSON；官方數據字典 `ptheadway_dataspec.pdf` 寫明 `frequencies.txt` 定義班次間隔時應忽略 `stop_times.txt` 絕對時間，而 `trips.txt` 嘅 `trip_id` 尾段本身就係官方開車時間 `HHMM`。所以只下載 5 個細檔（約 6 MB），**刻意唔揹 50 MB 嘅 `stop_times.txt`**；`24:10:00`／`25:35:00` 會顯示為 `00:10 (+1)`／`01:35 (+1)`，唔會當無效
- **🚌 唔撈錯公司 + 誠實降級**：官方 `routes.txt` 實測同一個「1」號同時有 KMB／CTB／NLB／GMB，`agencyMatches()` 按 `agency_id` 過濾（`KMB` 包埋 `LWB`、聯營 `KMB+CTB` 兩邊都認）；MTR／LRT／MTRB 喺呢個資料集用另一套 agency id，會如實顯示「政府班次資料未能提供呢條路線」而唔係靜靜拎第間公司。`GovServiceHoursHarness` 用**真實官方 GTFS 片段**（路線 1／10／102P、`frequencies.txt` 900 秒、跨午夜 25:35）**32 項全過**，包埋缺檔降級同 quoted CSV


- **🔄 正式加入更新線路資料（GOV_IN_ROUTE_UPDATE）**：政府班次資料唔再係暗中跟住下載，而係**正式來源** —— `GovServiceHours.SOURCE_FILE` 做獨立 key，`RouteDataStore.GOV_SOURCE`／`EXTRA_SOURCES = 2` 計入 `meta.json` 同 `Report`，`statuses.add(refreshGovServiceHours(...))` 令設定頁**資料來源狀態**逐項列出「N 個 GTFS 檔案 · 每兩星期更新」（`sources_gov_files`）；缺檔如實列 `sources_gov_skipped`、唔當硬失敗。「立即更新線路資料」、開 App 更新一次、清除本機資料、主頁本機資料指紋全部一齊處理，`update_hint`／`settings_footer` 同步講明服務時間及班次都係線路資料嘅一部分


- **🚫 取消搜尋頁我的書籤（SEARCH_NO_BOOKMARK）**：對返貼圖「我的書籤／沒有資料記錄」—— 搜尋分頁輸入為空時**唔再顯示**呢個成日空白嘅區塊：`activity_main.xml` 移除標題、分隔線、`tvBookmarkEmpty` 同 `bookmarkRecycler`；`MainActivity.kt` 移除 `BookmarkAdapter` 宣告／綁定、`refreshBookmarks()` 同所有 `R.id.bookmarkRecycler` 引用；`SearchAdapters.kt` 移除成個 `BookmarkAdapter` class；兩條 `search_bookmark_*` 字串同「沒有資料記錄」空態文案一齊刪。`item_bookmark.xml` 保留作停用資產（`preflight` 仍 **141** 檔），但再無任何 `R.layout.item_bookmark` 引用。**收藏功能冇取消**：底部**收藏分頁**（`favoriteAdapter`／`vm.favorites()`／`favRecycler`）、路線詳情頁左側 `chipFav`、長按移除、`Favorites.kt` 全部照舊；搜尋紀錄、8 tab／6 chips、固定 `search_*` 色盤、`values-night` 零覆寫一個都冇郁


- **🚫 取消導航欄收藏分頁（NAV_NO_FAV_TAB）**：**所有頁面**嘅懸浮液態玻璃導航欄而家**只有三分頁**（主頁／搜尋／應用程式設置），`tabFav`（★ 收藏）連同 `tab_fav` 字串、`MainActivity.kt` 嘅宣告／`findViewById`／點擊監聽全部移除；`selectTab()` 改用 `listOf(tabHome, tabSearch, tabMore)` ＋ `intArrayOf(0, 2, 3)` 對應 4 個頁面（`pageFav` 仍喺 index 1），`NavGlass.selectTab(this, tabs[i], tabPages[i] == index)` 令收藏頁顯示時三粒 tab 都唔會被誤選中。**收藏功能冇取消**：設定頁「★ 我的收藏」`btnFavList` 仍然 `selectTab(1)` 開收藏頁、長按移除、`favoriteAdapter`／`vm.favorites()` 照舊，詳情頁左側 `chipFav`／`vm.addCurrentFavorite()` 照舊；玻璃 56dp／28dp 幾何、離底 12% overlay、選中深色 pill＋藍字、液態三層、主搜尋頁常駐、10 個 `search_*` 色值同 `values-night` 零覆寫一個都冇郁

- **📢 通知圖標＝服務通告**：撳落去開「服務通告」dialog，內容**只係**官方 ETA 嘅 `remark_tc`（改道／預定班次／沒有服務等，去重）；冇備註就顯示「暫無服務通告」，**唔自己作通告**
- **⇅ 切換方向**：維持 `vm.toggleDirection()`；循環線冇回程方向，沿用 ⑧ `CircularRoutes.visibleHeaderActions(boolean)` 把第三顆設 `GONE`，頂欄剩**日曆＋通知兩顆**（第二張參考圖）。**兩顆入面嘅日曆同單向路線完全一樣，都係「服務時間及班次」同一個 `showServiceHours()` dialog**，唔會因為少咗方向掣就縮水或改功能
- **♻️ 功能唔流失**：收藏仍然由左側 `chipFav` 提供；`vm.refresh()` 手動刷新移到**通知 dialog**嘅「即時刷新通告」（通告仍係即時 ETA `remark_tc`），日曆 dialog 就改為「更新本機資料」去設定頁；四個側 chip、站序時間軸、`九巴循環線`／`城巴循環線` badge、站名核對一個都冇郁。**頂欄由頭到尾只有呢三顆，冇加第四顆、冇加圖示以外嘅掣**
- **🆔 id 對得上語義**：`detailActionTrips`／`detailActionFav` 改名 `detailActionCalendar`／`detailActionNotice`，`detailActionDirection` 保留；新增 3 個 vector drawable、`LocalServiceHours.java` 同 16 條字串，`preflight` 必要檔 **136 → 141**；`LocalServiceHoursHarness` 真寫入臨時本機快照再讀返，**23 項全過**，`detail_calendar_no_timetable` 如實講本地資料冇時間表

### ✨ 功能總覽（沿用 v1.0.2，含本版增強）

- **8 種交通模式**：九巴、龍運（按路線分開顯示）、城巴、嶼巴、綠色專線小巴、港鐵重鐵、輕鐵、港鐵接駁巴士、渡輪／電車／的士等目錄查詢
- **實時 ETA**：每站未來三班次、分鐘／時鐘／備註；前景重新整理間隔 15／30／45／60 秒可調（預設 30 秒）
- **附近路線**：先授權後自動定位、依設定半徑 50–2000 m 排序、前景位置更新、**半徑內所有路線**（分批 ETA 查詢防 429）、同號唔同公司分卡、**單公司只顯示一間／真聯營先顯示城巴/九巴**
- **搜尋**：截圖式分類頁、**全部官方目錄列**、搜尋紀錄（我的書籤已取消）、本機資料狀態列、**單一列維持自己所屬公司一個 label**、點列開詳情
- **設定**：線路資料更新（手動／僅 Wi-Fi、清除本機資料、開 App 自動一次、**含政府班次資料 GTFS**）、半徑、ETA 刷新、導航欄外觀、**進階材質三態**；詳情頁**循環線頂欄兩顆動作**；單向路線**三個圖標：日曆（服務時間及班次＝政府公開資料）／通知／切換方向**
- **懸浮液態玻璃導航欄**：56dp 藥丸、28dp 圓角、3 分頁，折射亮邊＋流動高光＋回彈，主搜尋頁常駐；中國版 Android 17 按進階材質狀態誠實降級；**路線詳情紅頂欄：單向路線三顆圖標（日曆／通知／⇅）、循環線兩顆**

### 📦 安裝與版本

- **✅ 版本已 bump（Version bump complete）**：`app/build.gradle.kts` 現為 **`versionName = 1.0.3`／`versionCode = 4`**（`1.0.3` / `4`），`update_pill.xml` 設計預覽同步 `v1.0.3 · 4`，兩個 drift gate 同 `RELEASE_VERSION_GATE` 都指向 `v1.0.3`；`RELEASE_NOTES_${TAG}.md` 會由 `v1.0.3` tag build 自動選用。實際正式版 APK 仍要全量 push 後 `git tag v1.0.3 && git push origin v1.0.3`，由 repo Actions 產出 `hketa-v1.0.3-release.apk`；本輪唔郁任何已簽名 APK／舊 tag。
- 下載對應 Release 的 **`hketa-v1.0.3-release.apk`**（正式版、已簽名）；Actions run 的唯一 Artifact 為 **`hketa-release-apk`**
- `applicationId = hk.eta.app`；Android 7.0+（minSdk 24），target / compile SDK 34；簽章指紋 `FE:7B:35:E1…20:02:57`（`SIGNING_FINGERPRINT.txt` 隨 Release 附上）
- 由舊版升級可直接覆蓋安裝（憑證一致）；若曾裝過唔同憑證嘅包，要先解除安裝
- `RELEASE_NOTES_${TAG}.md` 會被 `v1.0.3` tag build 自動選用做 Release 內文，`VERSION_INFO.txt` 記錄實際 badging
- Java 17 資料層 + Kotlin 1.9.24 UI／協程｜AGP 8.5.2｜Gradle 8.7｜JDK 17｜官方開放數據，免 API key

### ✅ 驗證與資料來源

- `preflight` **141 個必要檔案**齊全；`static checks` **425 條全綠**（含 `LAUNCH_CATALOGUE_REFRESH` 8 條、`HKE_MOBILITY_CATEGORIES` 8 條、`ALL_ROUTES` 3 條、`NAV_LIQUID`、`CN_ADVANCED_MATERIAL`、`SINGLE_OPERATOR_DISPLAY`、`CIRCULAR_TWO_ACTIONS`、`DETAIL_THREE_ICONS` 、`LOCAL_SERVICE_HOURS` 與 `GOV_SERVICE_HOURS` 斷言）
- 決策等價模擬：`claimOnce()` 一次冷啟動一次、8 組分類所屬、`NavLiquid` 6 情境、`NavGlassMaterial` **11 情境**＋無障礙優先 3×2、`springScale` 端點同 overshoot 全過
- 14 個 `.java` 以 ECJ 17 實編譯 `exit 0`（68 class）；**7 個 Java harness（Circular 39→47、LocalServiceHours 23、GovServiceHours **36→66**、Joint 52）＋ 38 項 published-release backfill 全過**；`RouteSearchHarness` 預載 **53 列**含 `LRT 505`／`GMB 20M`／`CTB 2`／`MTR AEL`／`MTRB 506`，`search("1")` 仍係原本 4 列
- Kotlin 符號對照 15 個關鍵檔／78 個 receiver／0 未定義；8 Python／13 Java／59 XML·YAML／30 bash／29 Kotlin 語法、括號、`R.*` 資源 0 缺失；色盤 10 個 `search_*` 值 + `values-night` 零覆寫
- inline preflight **188 情境** drift 全部 0 秒精確命中（All **188** inline-preflight drift scenarios detected in 0 seconds）；`scripts/preflight.py` 另有 **185 情境**；README 中英 23 章 125 條對稱；**6 份** Release 說明中英對稱（**6** bilingual Release-note files symmetric）；舊 repo 用合併包覆蓋 → 全專案 hash 0 差異
- 路線／站名／座標來自香港政府官方開放數據（DATA.GOV.HK、data.etabus.gov.hk、rt.data.gov.hk、data.etagmb.gov.hk）；實時到站時間向官方 API 即時查詢

**⑩ 九巴路線不顯示龍運、龍運路線不顯示九巴（KMB_LWB_SPLIT）**

- **🚌 九巴路線只顯示九巴，龍運路線只顯示龍運；城巴路線不顯示九巴/龍運，九巴及龍運路線各不顯示城巴；大嶼山巴士不顯示龍運/城巴（STRICT_OPERATOR_SEPARATION）**：KMB 官方 `/route/` 目錄同時收錄兩間公司但**冇公司欄位**，所以唔靠 `A／E／S／R／N` 前綴估（會撞）。新增 `GovServiceHours.Brand`（嵌喺政府 GTFS 類別入面），**只讀 `routes.txt` 嘅 `agency_id`**：`KMB`／`KMB+CTB` → 九巴、`LWB`／`LWB+CTB` → 龍運；同一路線號兩邊都出現或官方冇記錄 → UNKNOWN。所有**單一公司路線**（九巴／龍運／城巴／嶼巴／綠小巴／港鐵／輕鐵／港鐵巴士）嘅路線級標籤（主頁卡片 meta、搜尋結果列、站序／ETA 列、左側 badge、逐班次「該班次：…」、詳情頁 caption）都只寫自己一間，**任何第二間公司名都唔會出現**；`JointRoutes.routeCompanyLabel()` 唔再一律回傳混合標籤。**用家決定：保留混合聯營標籤** —— 真聯營線（兩份官方目錄都有）繼續顯示 `城巴/九巴`，龍運聯營（R8／S1）顯示 `城巴/龍運`，判唔到歸屬只寫「聯營路線」，唔會把龍運聯營線誤標成九巴。**實測官方目錄 770 條路線：575 條九巴、55 條龍運、88 條九巴聯營、2 條龍運聯營，49 條特別路線 GTFS 冇記錄如實降級**。**DIRECTION_TITLE_SYNC**：撳 ⇅ 切換方向後，紅頂「1 往 尖沙咀碼頭」而家會跟住轉做「1 往 竹園邨」——`RouteSearch.destFor()` 只讀本機官方目錄嘅方向級 `dest_tc`（KMB／JOINT 用 KMB 目錄、CTB 用 CTB 目錄，**永不落網絡**），冇方向級目的地就用官方站序終點站名降級，零捏造
- **🔘 公司按鈕都分開＋誠實降級**：`SearchCategoryRules.belongs()` 加 `brand` 參數，九巴 chip 只收 `brand=KMB`、龍運 chip 只收 `brand=LWB`，城巴 chip 只收 `CTB`（或真聯營）、嶼巴 chip 只收 `NLB`，判唔到嘅特別路線只留喺「所有巴士類別」，**唔會兩邊都出現**。`Brand.jointLabel(route)` 令聯營 note／後備 note／搜尋列 note／詳情頁 caption 全部按路線歸屬：龍運聯營（R8／S1）顯示 `城巴/龍運`、逐班次寫「該班次：龍運」、搜尋 badge `L/C`；龍運循環線 badge 改 `龍運循環線`，唔再寫九巴。`RouteDataStore.refreshGovServiceHours()` 會 `Brand.invalidate()`，更新線路資料後即刻用新 `routes.txt` 重建歸屬。GTFS 未載入時兩個 chip 顯示 `search_brand_unknown` 提示去設定頁更新，UNKNOWN 路線**唔顯示公司名、badge 留空或中性 `J/C`、唔捏造**。`EtaViewModel.toggleDirection()` 一齊計 `activeDest`，`RouteDetailActivity.syncDirectionTitle()` 同步 `dest`／`tvTitle`／caption（`buildCaption()` 抽出共用）；循環線冇 ⇅ 掣、維持兩顆圖標；`RouteSearchHarness` 用官方格式快照實測 O／I 兩方向目的地。8 營運商 ETA 資料層、10 個 `search_*` 色值、`values-night` 零覆寫、版本 `1.0.2`／`3` 同簽章鏈一個都冇郁

### ⚠️ 已知限制

- **呢邊冇真跑 Kotlin 編譯**：冇 `kotlinc`／Android SDK／Gradle／repo 權限；Kotlin 只做符號對照同靜態檢查，Java 有 ECJ 實編譯，**冇交付已編譯 APK、冇代為 push**；實機結果以下一次 CI run 同裝置為準
- **「開 App」＝冷啟動**：按 Home 返前景唔會再更新；想「每次返前景都更新」要改 `onResume()` 並放棄 process 閘（會明顯增加 API 請求，官方有 429 速率限制）
- **設定頁更新頻率對自動更新唔再生效**：「每日／每 3 日／每星期」只影響歷史排程描述；「僅 Wi-Fi」同「清除本機資料」仍然生效
- **「還原」按分類行為理解**：截圖式版面同色盤原封不動；小巴 tab 仍叫「專線小巴」（香港出行易官方用字）；愉景灣／馬灣、渡輪、電車、的士、非專線小巴、居民巴士服務如實留空
- **所有路線＝更多請求**：官方實際只有 1 條就顯示 1 條、查失敗就略過，**唔捏造**；分批＋讓路已做，必要時喺設定調長 ETA 自動更新間隔或關掉自動更新
- **進階材質係容錯探測**：中國版 ROM 私有開關讀唔到會顯示 `AOSP_*` 來源並按 AOSP 能力降級，屬設計；Android 17 專屬系統開關要等正式 SDK，唔編造 key
- **實時 ETA 需要網絡**：本機快照只涵蓋路線／站名／座標目錄，到站時間永遠即時查、唔快取
- **⑩ 已解決舊限制**：`九巴 / 龍運` 原本係 KMB 官方共用目錄嘅單一標籤（唔係兩間聯營）；而家靠政府 GTFS `agency_id` 逐條路線分開。但官方 GTFS 冇收錄 49 條特別路線（R／SP／PB／X 等），呢啲列**唔顯示公司名**、只留喺「所有巴士類別」，唔估、唔捏造 **API_AUDIT**：實打 30+ 個官方端點後修復 6 個真實 API 相容錯誤 —— 城巴 `route/CTB/` 同 `route/CTB` 兩個斜線變體都做 fallback（實測兩個都會間歇 timeout）、嶼巴 `routeName_c` 嘅 `" > "` 分隔符、城巴目錄冇 `dir/bound` 唔再硬編 `O`、KMB `service_type` 特別班次完整載入、`stop-eta` 有效路徑排先＋HTTP 422 唔再重試 UA、GMB／鐵路唔再捏造「總站／沿線」；並加 `DEST_CACHE`／GMB 方向快取、8KB 錯誤回應上限、`downloadVerified()` size＋`sha256` 校驗、Gradle 8.7 硬編 SHA-256。 **JOINT_EVERY_TRIP_OPERATOR**：聯營路線「6 分鐘 11:27」後面**每一個班次**都加「該班次：九巴／城巴／龍運」；由 ETA 批次自己零 IO 判定聯營（唔受目的地用字／拆卡／partner 只回一邊影響），`tripLabel` 判唔到就用 ETA 嘅 company code 如實降級，唔留空、唔估第二間；詳情列用 ` · ` 分隔、主頁 chip 同步受惠、`tripTag` 改 `layout_weight=1` 單行省略。static **423**、inline **188** 情境（`scripts/preflight.py` 185）。
- **紅頂欄要還原做三個圖標**：日曆／通知／⇅ 而家係本 App 嘅正式 vector 圖標（⑨），唔再係字符掣；主搜尋頁嘅「⇄ 轉換路線方向」文字按鈕同其他版面一個都冇郁，循環線仍只顯示兩顆
- **政府班次資料係 GTFS、唔係即時**：服務時間及班次由運輸署公開 GTFS 提供（**每兩星期更新**、有「數據修正截止日期」），涵蓋服務日／頭班／尾班／班次間隔；但**唔係即時到站**、唔反映臨時改道，臨時安排要睇營辦商公布。資料集用另一套 agency id 嘅模式（MTR／LRT／MTRB 等）或政府快取未載入時，dialog 如實降級去 `LocalServiceHours.lookup`／`RouteDataStore.readText` 本機目錄列，**唔捏造時間表、唔行即時 ETA**；實時到站班次仍由三個 ETA chip 提供、唔快取。通知 dialog 只列官方 `remark_tc`，冇就寫「暫無」，**唔自己作通告**

---

## English

### 🆕 Highlights (2026-11-25 → latest 2026-11-26)

#### 📅 2026-11-25: catalogue refresh, category restore, code optimisation

**① Refresh route data once on app launch (LAUNCH_CATALOGUE_REFRESH)**

- **🚀 One automatic update on every launch**: `HkEtaApp.onCreate()` triggers one `RouteDataStore.refresh(false)` per process right after `RouteDataStore.init(this)`; it lives in the Application rather than an Activity because `onCreate` runs exactly once per process, which is what "once" means
- **🧩 Pure-Kotlin process gate**: `LaunchCatalogueRefresh.claimOnce()` (`AtomicBoolean.compareAndSet`) guarantees one download per cold start; `CatalogueRefreshRunner.runOnce()` is the single entry shared by the Application and `EtaViewModel`, with the ViewModel only a safe fallback so the two never duplicate a download
- **🚫 No more schedule throttling**: the `RouteDataPrefs.isDue()` daily / 3-day / weekly check is removed, so even "0 = manual" still updates on launch; the **Wi-Fi-only preference is still honoured**, `RouteDataStore.refreshBusy()` is skipped and the `BUSY` atomic gate is a second safeguard
- **🔄 Completion takes effect immediately**: on success `markChecked` is written and `CatalogueRefreshNotifier` (Application-level `LiveData`) makes `MainActivity` run `renderLocalDataStatus()` + `searchRoutes()` + `refreshHistory()` at once, so the new snapshot does not wait for the next `onResume()`; `observe(this)` is removed on DESTROY
- **🖐 Manual updates are unaffected**: the Settings "route data update" button still works any time; failures / no Wi-Fi are skipped silently, never retried and never reported as success, and never disturb ETA queries
- **📌 What "once" means**: one cold start (one process); returning from Home, rotating the screen or switching tabs does not trigger another run

**② HKeMobility-style search categories restored (HKE_MOBILITY_CATEGORIES)**

- **🚌 Bus-company routes appear under their own buttons**: pure-Kotlin `SearchCategoryRules.belongs()` keeps "All bus categories" to `KMB / CTB / NLB / JOINT` only, no longer returning `true` and mixing minibus / rail rows into the bus page; Citybus = `CTB` + joint, KMB / LWB = `KMB` + joint, NLB = `NLB`; the `城巴/九巴` joint row appears under BOTH Citybus and KMB
- **🚇 MTR category shows heavy rail + Light Rail + MTR Bus again**: `MtrCatalog.LRT_ROUTES` adds the official LRT route codes (505 / 507 / 610 / 614…) and `RouteSearch.railPreload()` emits LRT rows, so the MTR tab shows `MTR` / `LRT` / `MTRB` together
- **🚐 Minibus routes belong to the minibus category**: `CAT_MINIBUS` is `GMB` only, so minibus rows no longer show on the bus page; ferry / tram / taxi / non-franchised minibus / residents' bus have no equivalent public real-time ETA API and stay honestly empty — no invented trips
- **📊 Preload no longer hard-cuts**: `MainActivity.refreshHistory()` no longer hard-`take(6)`, so all 8 tabs / 6 chips show their own operator's official rows; category behaviour is restored with zero layout or palette changes
- **🎨 Zero-change guarantee**: the 10 `search_*` colours, zero `values-night` overrides, the 8-operator data layer, always-live ETA, joint / circular / same-number split cards, foreground location updates, signing and the Release pipeline are all untouched

**③ Code optimisation (CODE_OPTIMISATION, zero behaviour change)**

- **🔢 One source of truth for the constant**: `RouteSearch.PRELOAD_PER_COMPANY` becomes the single source and `SearchCategoryRules` references it, fixing the dependency direction to ui → api instead of editing two places
- **🧹 Orphan function removed**: `capPerCompany()` is made generic and actually called by `MainActivity`, replacing the duplicated `groupBy{}.flatMap{take()}`; `filter` keeps the official ordering
- **⚡ No new Comparator per keystroke**: `PRELOAD_ORDER` / `SEARCH_ORDER` are extracted as `static final`; the checker `read()` is memoised, measured on the same source **142 ms → 125 ms (about -12%)** with byte-identical output
- **🧪 Behaviour-equivalent**: 15 rows compared category by category — heavy rail / all bus / minibus / Citybus / KMB / NLB / all **7 groups have identical counts and order**; 0 unused imports, and neither the version nor either Release note needed a single edit

#### 📅 2026-11-26 (latest): all routes, liquid glass, advanced material

**④ ALL routes shown (ALL_ROUTES: nearby + search)**

- **📊 Search quotas removed**: `RouteSearch.preload()` moves from a first-24 / 3-per-operator cap to **EVERY official catalogue row** (`ALL_ROUTES_LIMIT = Integer.MAX_VALUE`; all three official MTR / LRT / MTR Bus code tables are complete); `search()` no longer cuts 40 / 24 rows and returns EVERY official catalogue row
- **📍 Nearby quotas removed**: `CANDIDATE_LIMIT` / `ENTITY_LIMIT` / `MAX_QUERIES` all become `Int.MAX_VALUE` → **every stop, every physical station and every operator alias** inside the radius; the status line honestly reports "N stops · M routes"
- **🛡️ Batched against 429**: ETA queries run in batches of 12 with **120ms** between batches; Citybus officially documents `429 Processing too many requests`, so failed stops are honestly skipped by `safeEta` — nothing is invented and the first load is slower
- **🔁 One shared chain**: initial auto-location, `LocationTracker` foreground updates and ETA auto-refresh share the expanded cache; `RouteSearchHarness` preload grows from 15 to **53 rows** while `search("1")` still returns the original 4 rows

**⑤ Liquid layer on the glass (NAV_LIQUID)**

- **✨ Refraction rim**: a top-fading `GradientDrawable` with a 1dp inner stroke, clipped by a `ViewOutlineProvider` to the same 28dp pill so the highlight never leaks past the edge
- **🌊 Flowing sheen**: a 168dp white gradient sweeps left → right → back with `ValueAnimator` `INFINITE / REVERSE` over ~4.6 s; API 31+ adds a light `RenderEffect.createBlurEffect` scatter, all wrapped in `runCatching`
- **🫧 Damped spring**: the selected tab uses `NavLiquid.springScale` for `0.88 → 1.032 overshoot → 1.0`; the pure decision layer `NavLiquid` has zero Android imports and is offline-testable
- **♿ Honest degradation**: high-contrast text / reduced motion / "follow system" off → `alpha=0`, animator cancelled, `RenderEffect` cleared; the layer is `clickable=false` so touches pass through, stopped in `onPause()` and restarted in `onResume()` — foreground only
- **📐 Geometry untouched**: 56dp high, 28dp full radius, 12% (48–160dp) bottom lift, 6% / 18% side inset, four text tabs, the blue label and all 5 glass drawables stay exactly as they were

**⑥ China-ROM Android OS 17 advanced material (CN_ADVANCED_MATERIAL)**

- **🔎 Read-only tolerant probe**: `NavGlassCompat.materialProbe()` checks public AOSP capabilities (`isCrossWindowBlurEnabled`, Android 17 `setBackdropRenderEffect`), `PowerManager.isPowerSaveMode()`, animator scale and high-contrast text, then best-effort reads a tolerant candidate-key set from `Settings.Global/Secure/System` — honour a value if present, fall back to AOSP capability otherwise
- **🚫 No invented private keys**: brand only orders the probes and never force-enables a feature just because it is a China ROM; **no writes to system settings, no GMS**, and the status line honestly shows `CN_OEM_KEY` / `AOSP_BACKDROP` / `AOSP_NO_BLUR` sources
- **🧠 Pure decision layer**: `NavGlassMaterial.resolve()` returns `liquid / blur / level / reason`; power-save, high-contrast and reduced-motion always win, even against a user "Full liquid" choice; `sanitizeMode()` folds bad values back to Follow system
- **🎛️ Three-state spinner**: Settings adds "Advanced material" (Follow system / Full liquid / Simplified static), stored in this app's `SharedPreferences` only; 11 decision scenarios + 3×2 accessibility-priority cases all pass
- **📦 Zero new resources**: 0 new files / colours / dependencies / permissions; the then-current version `1.0.2` / `3` (since bumped to `1.0.3` / `4` for this release), the signing chain and every signed APK / tag are untouched


**⑦ Single-operator routes show exactly one operator (SINGLE_OPERATOR_DISPLAY)**

- **🚌 Two labels only for a real joint service**: only when the SAME route + destination **actually has BOTH KMB and CTB official rows / arrivals** do they merge into one `城巴/九巴` joint card / search row with per-trip "該班次：城巴／九巴" (your earlier explicit requirement is preserved)
- **🚫 Root cause: catalogue overlap ≠ two operators**: `JointRoutes.isJointRoute()` only means both official catalogues list that route number; the old `markJoint()` upgraded **a single KMB row (or a single CTB row)** to `JOINT` on that basis alone, so one operator looked like two
- **🧠 Pure decision layer**: `JointRoutes.bothOperatorsPresent(Set)` / `displayCompany(Set, fallback)` fix the rule "real two → JOINT; otherwise → that company's own single label"; `bothContain()` stays as the catalogue-level joint candidate, no second operator is invented
- **🔎 Search fixed**: `RouteSearch.markJoint()` first collects the companies **actually present** per `(route, destination)` and merges only when `bothOperatorsPresent`; a lone row keeps its own single-company label (then the shared KMB/LWB label; ⑩ splits it by official GTFS) with no `城巴/九巴` note, and a third operator (NLB / GMB) sharing the number is never swallowed by the joint row
- **📇 Cards stay consistent**: `SameRouteOperators.partition()` already gives "1 company → 1 card, non-circular KMB+CTB → 1 joint card, everything else split per company"; `JointRoutes.augment()` now guards at the end so an unreported partner returns the primary untouched instead of upgrading a single-company ETA into two
- **🎨 Zero changes**: the 8-operator data layer, always-live ETA, the 10 `search_*` colours, zero `values-night` overrides, the then-existing KMB/LWB shared-catalogue label, the then-current version `1.0.2` / `3` (since bumped to `1.0.3` / `4` for this release) and the signing chain are all (⑩ later splits the label by official GTFS) untouched


**⑧ Circular routes show exactly TWO header actions (CIRCULAR_TWO_ACTIONS)**

- **🔴 Matching the two reference images**: the old **three white circular actions** became **three icons (calendar / notice / ⇅)** on a one-way route (first image); **EVERY circular route shows only these TWO** — ① **calendar = service hours & trips**, ② **notice = service notices** (second image), with the third ⇅ not displayed at all
- **🔁 Official basis**: a circular route has ONE terminus and returns to it, so the official route-stop data has **NO inbound direction**; the third `⇄` switch-direction circle is a meaningless dead control for such a line and is hidden. Normal routes keep all three — nothing else changes
- **🧠 Single source of truth**: `CircularRoutes.visibleHeaderActions(boolean circular)` returns the icon-id list directly — a circular route gets `[CALENDAR, NOTICE]` (**exactly two**), a one-way route adds `DIRECTION`; `showDirectionAction(boolean circular)` is kept and derives from it, with zero Android / network imports and offline-testable; `wireActions()` **explicitly** sets the first two to `VISIBLE` and the third to `GONE` / `VISIBLE`
- **🆔 Zero resource changes**: the `detailActionDirection` id, its `setOnClickListener`, `bg_detail_action_circle`, the `@color/brand` red header, the other two circles and all four side chips are kept — visibility only, **no View added or removed, no colour, no drawable, no click logic changed**
- **♿ Consistent and untappable**: GONE cannot be tapped; the circular caption still honestly shows the `九巴循環線` / `城巴循環線` badge and never pretends an inbound direction exists just because a control was removed
- **✅ Offline run**: `CircularHarness` grows from 39 to **47** checks (added: "circular = exactly two icons, no DIRECTION", "the circular calendar is the SAME service-hours action as one-way", "an official `(循環線)` destination also yields two"); every other `CircularRoutes` predicate and the stop-name audit are untouched


**⑨ Three header icons: calendar = service hours & trips (DETAIL_THREE_ICONS)**

- **🔴 Matching the first reference image**: EVERY **one-way (non-circular) route** shows **exactly these three icons** in the detail red header — ① **calendar** (service hours & trips) / ② **notice** (service notices) / ③ **switch direction** (⇅). The old `▦` / `★` / `⇄` character circles are replaced by red vector icons (`ic_detail_calendar` / `ic_detail_notice` / `ic_detail_direction`); the white `bg_detail_action_circle` and the `@color/brand` red header are unchanged
- **📅 Calendar = service hours & trips (GOV_SERVICE_HOURS)**: **read from government open data** — the Transport Department "Headway information of public transport services" (`hk-td-tis_11-pt-headway-en`) Traditional-Chinese GTFS; `RouteDataStore.refreshGovServiceHours()` caches `pt-headway-tc` `routes.txt` / `calendar.txt` / `calendar_dates.txt` / `trips.txt` / `frequencies.txt` plus `DATA_LAST_UPDATED_DATE.csv`, and `GovServiceHours.lookup()` derives **service days, first / last bus, service span and headways** (`headway_secs`) with the **data revision cut-off date**. The calendar still uses **no live ETA** and never caches arrivals; when the government feed is missing or has no such route it honestly falls back to the **local route catalogue** via `LocalServiceHours.lookup` / `RouteDataStore.readText` (direction × service type), with **nothing invented / not invented**, same rule as the "—" fare placeholder; circular routes share the very same dialog

- **🏛️ Real government open data, NOT a single JSON**: this headway dataset is **GTFS UTF-8 CSV `.txt`** (or the aggregate `gtfs.zip`); only real-time ETA is JSON. The official `ptheadway_dataspec.pdf` states that when `frequencies.txt` defines headways the absolute `stop_times.txt` times should be ignored, and that a `trips.txt` `trip_id` already ends with the official departure `HHMM`. We therefore fetch only 5 small files (~6 MB) and **deliberately skip the 50 MB `stop_times.txt`**; `24:10:00` / `25:35:00` render as `00:10 (+1)` / `01:35 (+1)` instead of being dropped
- **🚌 No cross-operator mixing + honest degradation**: the official `routes.txt` really has KMB / CTB / NLB / GMB rows all numbered "1"; `agencyMatches()` filters on `agency_id` (`KMB` also covers `LWB`, joint `KMB+CTB` matches both). MTR / LRT / MTRB use a different agency scheme in this feed, so the dialog says "government headway data unavailable for this route" rather than silently borrowing another operator. `GovServiceHoursHarness` runs **32 checks on REAL official GTFS slices** (routes 1 / 10 / 102P, a 900-second `frequencies.txt` row, 25:35 overnight), including missing-file degradation and quoted CSV


- **🔄 Formally added to route catalogue update (GOV_IN_ROUTE_UPDATE)**: government headway data is no longer a silent side download but a **formal source** — `GovServiceHours.SOURCE_FILE` is its own key, `RouteDataStore.GOV_SOURCE` / `EXTRA_SOURCES = 2` feed `meta.json` and `Report`, and `statuses.add(refreshGovServiceHours(...))` makes the **source-status list** show "N GTFS files · bi-weekly" (`sources_gov_files`); missing files are honestly listed via `sources_gov_skipped` and never treated as a hard failure. "Update route catalogue now", the one launch-time refresh, "clear local data" and the main-page local-data fingerprint all handle it together, with `update_hint` / `settings_footer` updated to state that service hours & trips are part of the route catalogue


- **🚫 bookmarks removed from search (SEARCH_NO_BOOKMARK)**: matching the screenshot "我的書籤 / 沒有資料記錄" — the empty **bookmarkRecycler** section is gone from the search page: `activity_main.xml` drops the title, divider, `tvBookmarkEmpty` and `bookmarkRecycler`; `MainActivity.kt` drops the `BookmarkAdapter` field/binding, `refreshBookmarks()` and every `R.id.bookmarkRecycler` reference; `SearchAdapters.kt` drops the whole `BookmarkAdapter` class; both `search_bookmark_*` strings and the "no records" empty text are deleted. `item_bookmark.xml` stays as a retired asset (`preflight` still **141** files) with no remaining `R.layout.item_bookmark` reference. **Favourites are NOT removed**: the bottom **favourites tab** (`favoriteAdapter` / `vm.favorites()` / `favRecycler`), the detail-page `chipFav`, long-press removal and `Favorites.kt` all stay; search history, 8 tabs / 6 chips, the locked `search_*` palette and the zero `values-night` override are untouched


- **🚫 Favourites tab removed from nav (NAV_NO_FAV_TAB)**: the floating liquid-glass bar on **every page** now has **three tabs** (Home / Search / Settings); `tabFav` (★ 收藏), the `tab_fav` string and its declaration / `findViewById` / click listener in `MainActivity.kt` are all removed. `selectTab()` maps `listOf(tabHome, tabSearch, tabMore)` via `intArrayOf(0, 2, 3)` onto the 4 pages (`pageFav` still at index 1), and `NavGlass.selectTab(this, tabs[i], tabPages[i] == index)` keeps all three tabs unselected while the favourites page is shown. **Favourites are NOT removed**: the Settings-page "★ 我的收藏" `btnFavList` still calls `selectTab(1)`, long-press removal, `favoriteAdapter` / `vm.favorites()` stay, and the detail `chipFav` / `vm.addCurrentFavorite()` stay; the 56dp / 28dp geometry, 12% bottom overlay, selected pill + blue label, three liquid layers, always-on search page, 10 `search_*` colours and zero `values-night` override are untouched

- **📢 Notice = service notices**: tapping it opens a "service notices" dialog whose content is **only** the official ETA `remark_tc` (diversion / scheduled trip / no service, de-duplicated); with none it says "no service notices" and **never invents an announcement**
- **⇅ Switch direction**: still `vm.toggleDirection()`; a circular route has no inbound direction, so ⑧ `CircularRoutes.visibleHeaderActions(boolean)` sets the third circle to `GONE`, leaving **calendar + notice only** (second reference image). **The calendar among those two is EXACTLY the same "service hours & trips" `showServiceHours()` dialog as on a one-way route** — removing the direction control never reduces or changes it
- **♻️ No function lost**: favourite remains on the `chipFav` side chip; manual `vm.refresh()` moves to the **notice dialog** as "refresh notices now" (notices are still live ETA `remark_tc`), while the calendar dialog gets "update local data" opening Settings; the four side chips, stop timeline, `九巴循環線` / `城巴循環線` badges and stop-name audit are untouched. **The header still has exactly three circles on normal routes — no fourth control added**
- **🆔 Ids match the meaning**: `detailActionTrips` / `detailActionFav` become `detailActionCalendar` / `detailActionNotice`, `detailActionDirection` stays; 3 new vector drawables, `LocalServiceHours.java` and 16 strings are added, and `preflight` required files go **136 → 141**; `LocalServiceHoursHarness` really writes a temporary local snapshot then reads it back — **23 checks pass** — and `detail_calendar_no_timetable` states honestly that the local data has no timetable

### ✨ Feature overview (from v1.0.2, with this release's enhancements)

- **8 transport modes**: KMB and LWB (shown separately per route), Citybus, NLB, green minibus, MTR heavy rail, Light Rail, MTR feeder bus, plus ferry / tram / taxi catalogue entries
- **Real-time ETA**: up to three upcoming trips per stop with minutes / clock / remarks; foreground refresh at 15 / 30 / 45 / 60 s (default 30 s)
- **Nearby routes**: consent-first auto-location, configurable 50–2000 m radius, foreground position updates, **all routes inside the radius** (batched ETA against 429), same-number operator split, **one label for one operator / 城巴/九巴 only for a real joint service**
- **Search**: screenshot-style category page, **EVERY official catalogue row**, history (bookmarks removed), local-data status row, **a lone row keeps its own single-company label**, tap-through to detail
- **Settings**: route catalogue update (manual / Wi-Fi-only, clear local data, one launch-time refresh, **including government GTFS headway data**), radius, ETA refresh, nav appearance, **advanced material three-state spinner**; the detail header shows **three icons on one-way routes (calendar = service hours & trips from government open data / notice / ⇅) and two on circular routes**
- **Floating liquid-glass nav**: 56dp pill, 28dp radius, 3 tabs, refraction rim + flowing sheen + spring, always visible on the search page; China-ROM Android 17 degrades honestly per the advanced-material state; **detail red header: three icons on one-way routes (calendar = service hours & trips / notice / ⇅), two on circular routes**

### 📦 Install and version

- **✅ Version bump complete**: `app/build.gradle.kts` is now **`versionName = 1.0.3` / `versionCode = 4`** (`1.0.3` / `4`); the `update_pill.xml` design-time preview matches `v1.0.3 · 4`, and both drift gates plus `RELEASE_VERSION_GATE` target `v1.0.3`. `RELEASE_NOTES_${TAG}.md` is selected automatically by the `v1.0.3` tag build. Shipping the actual signed APK still needs a full push followed by `git tag v1.0.3 && git push origin v1.0.3`, so repo Actions can produce `hketa-v1.0.3-release.apk`; no signed APK or old tag is touched here.
- Download **`hketa-v1.0.3-release.apk`** from the matching Release (production, signed); the sole Actions Artifact is **`hketa-release-apk`**
- `applicationId = hk.eta.app`; Android 7.0+ (minSdk 24), target / compile SDK 34; signing fingerprint `FE:7B:35:E1…20:02:57` (`SIGNING_FINGERPRINT.txt` is attached)
- Upgrading from an older build installs over the top (same certificate); if a differently signed build was installed before, uninstall it first
- `RELEASE_NOTES_${TAG}.md` is selected automatically by the `v1.0.3` tag build as the Release body; `VERSION_INFO.txt` records the actual badging
- Java 17 data layer + Kotlin 1.9.24 UI / coroutines | AGP 8.5.2 | Gradle 8.7 | JDK 17 | official open data, no API key

### ✅ Verification and data sources

- `preflight`: **141 required files** present; **425 static checks** green (including 8 `LAUNCH_CATALOGUE_REFRESH`, 8 `HKE_MOBILITY_CATEGORIES`, 3 `ALL_ROUTES`, plus the `NAV_LIQUID`, `CN_ADVANCED_MATERIAL`, `SINGLE_OPERATOR_DISPLAY`, `CIRCULAR_TWO_ACTIONS`, `DETAIL_THREE_ICONS` `LOCAL_SERVICE_HOURS` and `GOV_SERVICE_HOURS` assertions)
- Decision-equivalence simulations: `claimOnce()` once per cold start, 8 category-membership cases, 6 `NavLiquid` scenarios, **11** `NavGlassMaterial` scenarios + 3×2 accessibility-priority cases, and `springScale` endpoints / overshoot all pass
- 14 `.java` files compiled with ECJ 17, `exit 0` (68 classes); **7 Java harnesses (Circular 39→47, LocalServiceHours 23, GovServiceHours **36→66**, Joint 52) + 38 published-Release backfill checks pass**; `RouteSearchHarness` preloads **53 rows** including `LRT 505` / `GMB 20M` / `CTB 2` / `MTR AEL` / `MTRB 506`, while `search("1")` still returns the original 4 rows
- Kotlin symbol cross-check across 15 key files / 78 receivers / 0 undefined; 8 Python / 13 Java / 59 XML·YAML / 30 bash / 29 Kotlin syntax, brackets and `R.*` resources: 0 missing; 10 `search_*` colours with zero `values-night` overrides
- All **188** inline-preflight drift scenarios detected in 0 seconds by both gates; `scripts/preflight.py` covers **185** drift scenarios; README 23 sections / 125 bullets symmetric in Chinese and English; **6** bilingual Release-note files symmetric; overlaying the merged pack on an old repo gives 0 hash differences across the project
- Routes / stop names / coordinates come from Hong Kong government open data (DATA.GOV.HK, data.etabus.gov.hk, rt.data.gov.hk, data.etagmb.gov.hk); live ETAs are queried from the official APIs

**⑩ KMB routes show only KMB, LWB routes show only LWB (KMB_LWB_SPLIT)**

- **🚌 KMB routes show only KMB, LWB routes show only LWB; CTB routes never show KMB/LWB, KMB and LWB routes never show CTB, NLB never shows LWB/CTB (STRICT_OPERATOR_SEPARATION)**: the official KMB `/route/` catalogue covers both companies but has **no company field**, so `A / E / S / R / N` prefixes are NOT used as a guess (they collide). A new `GovServiceHours.Brand` reads **only `agency_id` from GTFS `routes.txt`**: `KMB` / `KMB+CTB` -> KMB, `LWB` / `LWB+CTB` -> LWB; a route number present on both sides, or absent from GTFS, is UNKNOWN. **Every single-company route** (KMB / LWB / CTB / NLB / GMB / MTR / LRT / MTRB) now shows only its own name in home card meta, search rows, stop / ETA rows, the left badge, per-trip "該班次：…" and the detail caption — **no second company name ever appears**; `JointRoutes.routeCompanyLabel()` no longer returns a mixed label. **User decision: keep the mixed joint label** — genuine joint routes (present in BOTH official catalogues) still show `城巴/九巴`, LWB joint routes (R8 / S1) show `城巴/龍運`, and an undeterminable one says only "聯營路線" rather than mislabelling LWB as KMB. **Measured on the live catalogue: 770 routes — 575 KMB, 55 LWB, 88 KMB joint, 2 LWB joint, 49 special routes with no GTFS row degrade honestly**. **DIRECTION_TITLE_SYNC**: pressing ⇅ now switches the red header from "1 往 尖沙咀碼頭" to "1 往 竹園邨" too — `RouteSearch.destFor()` reads ONLY the local official catalogue's direction-level `dest_tc` (KMB / JOINT use the KMB catalogue, CTB the CTB one, **never a network call**); operators without direction-level destinations fall back to the official terminal stop name from the route-stop sequence, nothing invented
- **🔘 Operator chips split too + honest degradation**: `SearchCategoryRules.belongs()` takes a `brand`; the KMB chip only admits `brand=KMB`, the LWB chip only `brand=LWB`, the CTB chip only `CTB` (or a genuine joint row), the NLB chip only `NLB`, and unclassifiable special routes stay solely under "All bus categories" — **never on both**. `Brand.jointLabel(route)` makes the joint note / fallback note / search-row note / detail caption route-aware: LWB joint routes (R8 / S1) show `城巴/龍運` with "該班次：龍運" and badge `L/C`, and an LWB circular badge becomes `龍運循環線` instead of KMB. `RouteDataStore.refreshGovServiceHours()` calls `Brand.invalidate()` so a catalogue update immediately rebuilds ownership from the new `routes.txt`. If GTFS is not loaded both chips show `search_brand_unknown` pointing to Settings; UNKNOWN routes show **no company label**, a blank or neutral `J/C` badge, and **nothing is invented**. `EtaViewModel.toggleDirection()` now also computes `activeDest`, and `RouteDetailActivity.syncDirectionTitle()` syncs `dest`, the title and the caption (a shared `buildCaption()`); circular routes keep their two icons with no ⇅ button, and `RouteSearchHarness` verifies both O / I destinations against official-shaped snapshots. The 8-operator ETA data layer, 10 `search_*` colours, zero `values-night` overrides, version `1.0.2` / `3` and the signing chain are untouched

### ⚠️ Known limitations

- **Kotlin was not actually compiled here**: no `kotlinc` / Android SDK / Gradle / repo access; Kotlin received symbol and static checks only, Java was really compiled with ECJ, and **no compiled APK was delivered and no push was made** — real-device behaviour depends on the next CI run and the device
- **"Launch" means a cold start**: returning from Home does not refresh again; refreshing on every foreground return requires moving to `onResume()` and dropping the process gate (which would noticeably increase API calls against the official 429 rate limit)
- **The Settings update interval no longer governs auto-update**: the daily / 3-day / weekly wording is historical only; "Wi-Fi only" and "clear local data" still apply
- **"Restored" refers to category behaviour**: the screenshot layout and palette are untouched; the minibus tab is still labelled "專線小巴" (the official HKeMobility wording); Discovery Bay / Ma Wan, ferry, tram, taxi, non-franchised minibus and residents' bus stay honestly empty
- **All routes means more requests**: if the official feed reports only 1 trip, 1 is shown and failed queries are skipped — **nothing is invented**; batching + pacing is in place, and the ETA auto-refresh interval can be lengthened or disabled in Settings
- **Advanced material is a tolerant probe**: when a China-ROM private toggle cannot be read, the status shows an `AOSP_*` source and degrades by AOSP capability by design; a dedicated Android 17 system switch needs the official SDK — no invented keys
- **Live ETA needs network**: local snapshots cover route / stop-name / coordinate catalogues only; arrival times are always queried live and never cached
- **⑩ resolves the old limitation**: `九巴 / 龍運` was the single label of the shared KMB/LWB catalogue (not a joint service); official GTFS `agency_id` now splits it per route. 49 special routes (R / SP / PB / X) have no GTFS row: they show **no company label**, stay only under "All bus categories", and are never guessed deliberately left untouched here — nothing invented **API_AUDIT**: after probing 30+ live official endpoints, six real-API compatibility bugs were fixed — both Citybus `route/CTB/` and `route/CTB` slash variants as fallbacks (both time out intermittently), NLB `routeName_c` `" > "` separator, no invented `O` bound in the CTB catalogue, full KMB `service_type` coverage, valid `stop-eta` first with no HTTP 422 UA retry, and no invented GMB/rail terminal names; plus `DEST_CACHE` / GMB direction caches, an 8KB error-body cap, `downloadVerified()` size + `sha256` checks and a pinned Gradle 8.7 SHA-256. **JOINT_EVERY_TRIP_OPERATOR**: on joint routes every one of the three arrivals now appends its own operator after "6 分鐘 11:27" ("該班次：九巴／城巴／龍運"). Joint routes are detected from the ETA batch itself with zero IO (immune to destination wording, split cards or a one-sided partner reply); an unknown `tripLabel` degrades honestly to the ETA company code, never blank and never a guessed second operator. Detail rows use ` · `, home chips benefit too, and `tripTag` now uses `layout_weight=1` with single-line ellipsis. **423 static checks**, **188 inline scenarios** (scripts/preflight.py: 185).
- **The three icons are now real app assets**: calendar / notice / ⇅ are official vector drawables in this app (⑨), no longer character glyphs; the main search page's `⇄` direction button and every other screen are untouched, and circular routes still show only two
- **Government headway data is GTFS, not live**: service hours & trips come from the Transport Department open GTFS (**bi-weekly**, with a data revision cut-off date) covering service days / first & last bus / headways — it is **NOT live arrival data** and does not reflect temporary diversions, which remain the operators' notices. Modes using a different agency scheme (MTR / LRT / MTRB) or a missing government cache honestly fall back to the on-device catalogue via `LocalServiceHours.lookup` / `RouteDataStore.readText`, **nothing is invented, no live ETA**; real-time arrivals stay on the three ETA chips and are never cached. The notice dialog lists only official `remark_tc` and says "no service notices" when empty, **no announcement is fabricated**

---

> 📌 **改說明唔等於改 APK／Editing notes never touches the APK / tag**：改 Release 描述唔使重跑 CI，亦唔會改動已簽名嘅 APK；實際功能由對應 tag 嘅 source 構建。Editing a Release body needs no CI re-run and changes no signed APK; the actual features come from that tag's own source build.
> 📌 `v1.0.2` 之後嘅全部九組後續改動現行描述以本檔為準；`RELEASE_NOTES_v1.0.2.md` 已移除相應 highlight。All nine follow-up change groups after `v1.0.2` are described here; the matching bullets were removed from `RELEASE_NOTES_v1.0.2.md`.
