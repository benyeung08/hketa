# HK ETA v1.0.3 · 香港實時到站 / Hong Kong real-time ETA

> **收錄範圍：2026-11-25（今日）**｜**Scope: 2026-11-25 (today)**。
> 本版只收錄 `v1.0.2` 之後嘅**兩個改動**：開 App 就更新線路資料一次、搜尋功能還原香港出行易式分類。
> This release covers only the **two changes** made after `v1.0.2`: refreshing route data once on app launch, and restoring HKeMobility-style search categories.

> **版本分界／Version boundary**：`RELEASE_NOTES_v1.0.0.md` 截至 2026-11-19；`RELEASE_NOTES_v1.0.1.md` 由 2026-11-20 起整理資料層、班次邏輯同發布鏈；`RELEASE_NOTES_v1.0.2.md` 收錄 2026-11-24 嘅搜尋介面、定位、卡片、導航、本地資料同 Kotlin 編譯修正；**`v1.0.3` 收錄 2026-11-25 嘅上述兩個改動**。`v1.0.2` 說明已移除呢兩條，舊檔保留作歷史、唔郁已簽名 APK／tag。
> `RELEASE_NOTES_v1.0.0.md` covers through 2026-11-19; `RELEASE_NOTES_v1.0.1.md` starts at 2026-11-20 with the data-layer, trip-logic and release-pipeline work; `RELEASE_NOTES_v1.0.2.md` covers 2026-11-24 (search UI, location, cards, navigation, local data and the Kotlin compile fix); **`v1.0.3` covers the two changes above from 2026-11-25**. Those two bullets were removed from the `v1.0.2` notes; older files are kept as history and no signed APK or tag is touched.

> 本檔係**純文字中英雙語 Release 說明**，可直接貼入 GitHub Release 描述框；`RELEASE_NOTES_${TAG}.md` 會由 `v1.0.3` tag build 自動選用做該版本 Release 內文。
> This is a **plain-text bilingual Release note** you can paste straight into the GitHub Release description; `RELEASE_NOTES_${TAG}.md` is selected automatically by the `v1.0.3` tag build as that version's Release body.

---

## 中文（繁體）

### 🆕 本版更新重點（2026-11-25）

#### 📅 改動一：開 App 就更新線路資料一次

- **🚀 每次開 App 自動更新一次**：`HkEtaApp.onCreate()` 喺 `RouteDataStore.init(this)` 之後，每個 process 觸發一次 `RouteDataStore.refresh(false)`；放喺 Application 而唔係 Activity，因為 `onCreate` 每個 process 只行一次，最對應「一次」
- **🧩 純 Kotlin process 閘**：新增 `LaunchCatalogueRefresh.claimOnce()`（`AtomicBoolean.compareAndSet`），保證一次冷啟動只下載一次；`CatalogueRefreshRunner.runOnce()` 係 Application 同 `EtaViewModel` 共用嘅單一入口，ViewModel 只做安全後備，唔會同 Application 重複下載
- **🚫 唔再受排程節流**：移除 `RouteDataPrefs.isDue()` 嘅每日／每 3 日／每星期判斷，連設定「0 = 手動」都照更新；但**尊重「僅 Wi-Fi 更新」**、`RouteDataStore.refreshBusy()` 讓路，`BUSY` 原子閘雙重保險
- **🔄 完成即時生效**：成功後寫 `markChecked`，`CatalogueRefreshNotifier`（Application 級 `LiveData`）通知 `MainActivity` 即刻 `renderLocalDataStatus()` ＋ `searchRoutes()` ＋ `refreshHistory()`，新快照唔使等下一次 `onResume`；`observe(this)` 喺 DESTROY 自動移除，唔洩漏 Activity
- **🖐 手動更新唔受影響**：設定頁「線路資料更新」照舊隨時撳隨時更新；失敗／冇 Wi-Fi 靜默跳過、唔自動重試、唔假裝成功，唔干擾 ETA 查詢
- **📌 「一次」嘅定義**：一次冷啟動（一個 process）一次；按 Home 返前景、轉螢幕、切分頁都唔會再跑

#### 📅 改動二：搜尋功能還原香港出行易式分類

- **🚌 巴士公司路線分開喺所屬按鈕**：新增純 Kotlin `SearchCategoryRules.belongs()`；「所有巴士類別」只 `KMB／CTB／NLB／JOINT`，唔再 `return true` 把小巴／港鐵／輕鐵撈埋入巴士頁；城巴＝`CTB`＋聯營、九巴／龍運＝`KMB`＋聯營、嶼巴＝`NLB`；聯營 `城巴/九巴` 同時喺城巴同九巴兩個按鈕出現（官方兩間都有營運）
- **🚇 港鐵類別顯示返重鐵＋輕鐵＋港鐵接駁巴士**：`MtrCatalog.LRT_ROUTES` 補回官方輕鐵路線號碼表（505／507／610／614…），`RouteSearch.railPreload()` 補返 LRT 列；港鐵 tab 同時見到 `MTR`／`LRT`／`MTRB`
- **🚐 小巴路線歸小巴類別**：`CAT_MINIBUS` 只 `GMB`，小巴路線唔再喺巴士頁出現；渡輪／電車／的士／非專線小巴／居民巴士服務官方冇同類公開實時 ETA API，如實留空、唔捏造班次
- **📊 顯示全部官方路線（ALL_ROUTES）**：`RouteSearch.preload()` 由「頭 24 列／每公司 3 列」改為**全部官方目錄列**（`ALL_ROUTES_LIMIT = Integer.MAX_VALUE`，重鐵／輕鐵／接駁巴士三份官方碼表一條都唔少）；`search()` 唔再 cut 40／24 列；`MainActivity.refreshHistory()` 唔再硬 `take(6)`，八個 tab／六個 chips 都顯示所屬公司嘅全部路線
- **🎨 版面同色盤零改動**：截圖式 8 tab／6 chips 嘅 View 同 id、10 個 `search_*` 色值、`values-night` 零覆寫、8 營運商資料層、ETA 永遠即時查、聯營／循環／同號分卡／前景位置更新、簽章與 Release 鏈全部一個都冇郁

### ✨ 功能總覽（沿用 v1.0.2）

- **8 種交通模式**：九巴／龍運、城巴、嶼巴、綠色專線小巴、港鐵重鐵、輕鐵、港鐵接駁巴士、渡輪／電車／的士等目錄查詢
- **實時 ETA**：每站未來三班次、分鐘／時鐘／備註；前景重新整理間隔 15／30／45／60 秒可調（預設 30 秒）
- **附近路線**：先授權後自動定位、依設定半徑 50–2000 m 排序、前景位置更新、**半徑內所有路線**（分批 ETA 查詢防 429）、同號唔同公司分卡
- **搜尋**：截圖式分類頁、官方目錄預載、書籤、搜尋紀錄、本機資料狀態列、點列開詳情
- **設定**：線路資料更新（手動／僅 Wi-Fi、清除本機資料）、半徑、ETA 刷新、導航欄外觀跟隨系統
- **懸浮液態玻璃導航欄**：56dp 藥丸、28dp 圓角、4 分頁，主搜尋頁常駐；中國版 Android 17 停用視窗模糊時自動用實色玻璃

### 📦 安裝與版本

- **⚠️ 版本號待 bump**：本說明係 `v1.0.3` Release 預備；`app/build.gradle.kts` 目前仍係 **`versionName = 1.0.2`／`versionCode = 3`**，實際出 `v1.0.3` 正式版前需改為 **`1.0.3`／`4`**，並同步 `update_pill.xml` 同 Release 版本閘門
- 下載對應 Release 的 **`hketa-v1.0.3-release.apk`**（正式版、已簽名）；Actions run 的唯一 Artifact 為 **`hketa-release-apk`**
- `applicationId = hk.eta.app`；Android 7.0+（minSdk 24），target / compile SDK 34；簽章指紋 `FE:7B:35:E1…20:02:57`（`SIGNING_FINGERPRINT.txt` 隨 Release 附上）
- 由舊版升級可直接覆蓋安裝（憑證一致）；若曾裝過唔同憑證嘅包，要先解除安裝
- `RELEASE_NOTES_${TAG}.md` 會被 `v1.0.3` tag build 自動選用做 Release 內文，`VERSION_INFO.txt` 記錄實際 badging
- Java 17 資料層 + Kotlin 1.9.24 UI／協程｜AGP 8.5.2｜Gradle 8.7｜JDK 17｜官方開放數據，免 API key

### ✅ 驗證與資料來源

- `preflight` **136 個必要檔案**齊全；`static checks` **399 條全綠**（含 `LAUNCH_CATALOGUE_REFRESH` 8 條、`HKE_MOBILITY_CATEGORIES` 8 條）
- 決策等價模擬：`claimOnce()` 一次冷啟動一次、4 組 Wi-Fi×busy、4 組 busy×skipped×okCount、8 組分類所屬、新舊預載配額對照全過
- 12 個 `.java` 以 ECJ 17 實編譯 `exit 0`（58 class）；**5 個 Java harness ＋ 38 項 published-release backfill 全過**；`RouteSearchHarness` 預載 **53 列**（全部官方碼表）含 `LRT 505`／`GMB 20M`／`CTB 2`／`MTR AEL`／`MTRB 506`，`search("1")` 仍係原本 4 列
- Kotlin 符號對照 13 個相關檔 0 未定義、`app.` 作用域 0 違規；8 Python／12 Java／56 XML／YAML／29 Kotlin 語法、括號、資源 0 缺失；色盤 10 個 `search_*` 值 + `values-night` 零覆寫
- inline preflight **52 情境** drift 全部 0 秒精確命中；README 中英 23 章 125 條對稱；**6 份** Release 說明中英對稱；舊 repo 用合併包覆蓋 → 全專案 hash 0 差異
- 路線／站名／座標來自香港政府官方開放數據（DATA.GOV.HK、data.etabus.gov.hk、rt.data.gov.hk、data.etagmb.gov.hk）；實時到站時間向官方 API 即時查詢

### ⚠️ 已知限制

- **呢邊冇真跑 Kotlin 編譯**：冇 `kotlinc`／Android SDK／Gradle／repo 權限；Kotlin 只做符號對照同靜態檢查，Java 有 ECJ 實編譯，**冇交付已編譯 APK、冇代為 push**；實機結果以下一次 CI run 同裝置為準
- **「開 App」＝冷啟動**：按 Home 返前景唔會再更新；想「每次返前景都更新」要改 `onResume()` 並放棄 process 閘（會明顯增加 API 請求，官方有 429 速率限制）
- **設定頁更新頻率對自動更新唔再生效**：「每日／每 3 日／每星期」只影響歷史排程描述；「僅 Wi-Fi」同「清除本機資料」仍然生效
- **「還原」按分類行為理解**：截圖式版面同色盤原封不動；小巴 tab 仍叫「專線小巴」（香港出行易官方用字）；愉景灣／馬灣、渡輪、電車、的士、非專線小巴、居民巴士服務如實留空
- **實時 ETA 需要網絡**：本機快照只涵蓋路線／站名／座標目錄，到站時間永遠即時查、唔快取；官方實際只有 1 條就顯示 1 條，唔捏造

---

## English

### 🆕 Highlights (2026-11-25)

#### 📅 Change 1: refresh route data once on app launch

- **🚀 One automatic update on every launch**: `HkEtaApp.onCreate()` triggers one `RouteDataStore.refresh(false)` per process right after `RouteDataStore.init(this)`; it lives in the Application rather than an Activity because `onCreate` runs exactly once per process, which is what "once" means
- **🧩 Pure-Kotlin process gate**: `LaunchCatalogueRefresh.claimOnce()` (`AtomicBoolean.compareAndSet`) guarantees one download per cold start; `CatalogueRefreshRunner.runOnce()` is the single entry shared by the Application and `EtaViewModel`, with the ViewModel only a safe fallback so the two never duplicate a download
- **🚫 No more schedule throttling**: the `RouteDataPrefs.isDue()` daily / 3-day / weekly check is removed, so even "0 = manual" still updates on launch; the **Wi-Fi-only preference is still honoured**, `RouteDataStore.refreshBusy()` is skipped and the `BUSY` atomic gate is a second safeguard
- **🔄 Completion takes effect immediately**: on success `markChecked` is written and `CatalogueRefreshNotifier` (Application-level `LiveData`) makes `MainActivity` run `renderLocalDataStatus()` + `searchRoutes()` + `refreshHistory()` at once, so the new snapshot does not wait for the next `onResume()`; `observe(this)` is removed on DESTROY and never leaks the Activity
- **🖐 Manual updates are unaffected**: the Settings "route data update" button still works any time; failures / no Wi-Fi are skipped silently, never retried and never reported as success, and never disturb ETA queries
- **📌 What "once" means**: one cold start (one process); returning from Home, rotating the screen or switching tabs does not trigger another run

#### 📅 Change 2: HKeMobility-style search categories restored

- **🚌 Bus-company routes appear under their own buttons**: pure-Kotlin `SearchCategoryRules.belongs()` keeps "All bus categories" to `KMB / CTB / NLB / JOINT` only, no longer returning `true` and mixing minibus / rail rows into the bus page; Citybus = `CTB` + joint, KMB / LWB = `KMB` + joint, NLB = `NLB`; the `城巴/九巴` joint row appears under BOTH Citybus and KMB (both operators officially run it)
- **🚇 MTR category shows heavy rail + Light Rail + MTR Bus again**: `MtrCatalog.LRT_ROUTES` adds the official LRT route codes (505 / 507 / 610 / 614…) and `RouteSearch.railPreload()` emits LRT rows, so the MTR tab shows `MTR` / `LRT` / `MTRB` together
- **🚐 Minibus routes belong to the minibus category**: `CAT_MINIBUS` is `GMB` only, so minibus rows no longer show on the bus page; ferry / tram / taxi / non-franchised minibus / residents' bus have no equivalent public real-time ETA API and stay honestly empty — no invented trips
- **📊 ALL official routes shown (ALL_ROUTES)**: `RouteSearch.preload()` moves from a first-24 / 3-per-operator cap to **EVERY official catalogue row** (`ALL_ROUTES_LIMIT = Integer.MAX_VALUE`; all three official MTR / LRT / MTR Bus code tables are complete); `search()` no longer cuts 40 / 24 rows; `MainActivity.refreshHistory()` no longer hard-`take(6)`, so all 8 tabs / 6 chips show every route of their own operator
- **🎨 Zero layout or colour changes**: the screenshot-style 8 tabs / 6 chips views and ids, the 10 `search_*` colours, zero `values-night` overrides, the 8-operator data layer, always-live ETA, joint / circular / same-number split cards, foreground location updates, signing and the Release pipeline are all untouched

### ✨ Feature overview (unchanged from v1.0.2)

- **8 transport modes**: KMB/LWB, Citybus, NLB, green minibus, MTR heavy rail, Light Rail, MTR feeder bus, plus ferry / tram / taxi catalogue entries
- **Real-time ETA**: up to three upcoming trips per stop with minutes / clock / remarks; foreground refresh at 15 / 30 / 45 / 60 s (default 30 s)
- **Nearby routes**: consent-first auto-location, configurable 50–2000 m radius, foreground position updates, more routes per stop, same-number operator split
- **Search**: screenshot-style category page, official catalogue preload, bookmarks, history, local-data status row, tap-through to detail
- **Settings**: route catalogue update (manual / Wi-Fi-only, clear local data), radius, ETA refresh and system-following nav appearance
- **Floating liquid-glass nav**: 56dp pill, 28dp radius, 4 tabs, always visible on the search page; opaque glass fallback when China-ROM Android 17 disables window blur

### 📦 Install and version

- **⚠️ Version bump pending**: these notes prepare the `v1.0.3` Release; `app/build.gradle.kts` is still **`versionName = 1.0.2` / `versionCode = 3`** and must move to **`1.0.3` / `4`** (plus `update_pill.xml` and the Release version gate) before the actual `v1.0.3` APK ships
- Download **`hketa-v1.0.3-release.apk`** from the matching Release (production, signed); the sole Actions Artifact is **`hketa-release-apk`**
- `applicationId = hk.eta.app`; Android 7.0+ (minSdk 24), target / compile SDK 34; signing fingerprint `FE:7B:35:E1…20:02:57` (`SIGNING_FINGERPRINT.txt` is attached)
- Upgrading from an older build installs over the top (same certificate); if a differently signed build was installed before, uninstall it first
- `RELEASE_NOTES_${TAG}.md` is selected automatically by the `v1.0.3` tag build as the Release body; `VERSION_INFO.txt` records the actual badging
- Java 17 data layer + Kotlin 1.9.24 UI / coroutines | AGP 8.5.2 | Gradle 8.7 | JDK 17 | official open data, no API key

### ✅ Verification and data sources

- `preflight`: **136 required files** present; **399 static checks** green (including 8 `LAUNCH_CATALOGUE_REFRESH` and 8 `HKE_MOBILITY_CATEGORIES`)
- Decision-equivalence simulations: `claimOnce()` once per cold start, 4 Wi-Fi×busy cases, 4 busy×skipped×okCount cases, 8 category-membership cases and an all-routes catalogue equivalence comparison all pass
- 12 `.java` files compiled with ECJ 17, `exit 0` (58 classes); **5 Java harnesses + 38 published-Release backfill checks pass**; `RouteSearchHarness` preloads **53 rows** (full official code tables) including `LRT 505` / `GMB 20M` / `CTB 2` / `MTR AEL` / `MTRB 506`, while `search("1")` still returns the original 4 rows
- Kotlin symbol cross-check across 13 related files: 0 undefined, 0 `app.` scope violations; 8 Python / 12 Java / 56 XML / YAML / 29 Kotlin syntax, brackets and resources: 0 missing; 10 `search_*` colours with zero `values-night` overrides
- All **52** inline-preflight drift scenarios detected in 0 seconds; README 23 sections / 125 bullets symmetric in Chinese and English; **6** bilingual Release-note files symmetric; overlaying the merged pack on an old repo gives 0 hash differences across the project
- Routes / stop names / coordinates come from Hong Kong government open data (DATA.GOV.HK, data.etabus.gov.hk, rt.data.gov.hk, data.etagmb.gov.hk); live ETAs are queried from the official APIs

### ⚠️ Known limitations

- **Kotlin was not actually compiled here**: no `kotlinc` / Android SDK / Gradle / repo access; Kotlin received symbol and static checks only, Java was really compiled with ECJ, and **no compiled APK was delivered and no push was made** — real-device behaviour depends on the next CI run and the device
- **"Launch" means a cold start**: returning from Home does not refresh again; refreshing on every foreground return requires moving to `onResume()` and dropping the process gate (which would noticeably increase API calls against the official 429 rate limit)
- **The Settings update interval no longer governs auto-update**: the daily / 3-day / weekly wording is historical only; "Wi-Fi only" and "clear local data" still apply
- **"Restored" refers to category behaviour**: the screenshot layout and palette are untouched; the minibus tab is still labelled "專線小巴" (the official HKeMobility wording); Discovery Bay / Ma Wan, ferry, tram, taxi, non-franchised minibus and residents' bus stay honestly empty
- **Live ETA needs network**: local snapshots cover route / stop-name / coordinate catalogues only; arrival times are always queried live and never cached, and if the official feed reports only 1 trip, 1 is shown — nothing is invented

---

> 📌 **改說明唔等於改 APK／Editing notes never touches the APK / tag**：改 Release 描述唔使重跑 CI，亦唔會改動已簽名嘅 APK；實際功能由對應 tag 嘅 source 構建。Editing a Release body needs no CI re-run and changes no signed APK; the actual features come from that tag's own source build.
> 📌 `v1.0.2` 之後只有呢兩個改動；`RELEASE_NOTES_v1.0.2.md` 已移除相應兩條，佢哋嘅現行描述以本檔為準。Only these two changes followed `v1.0.2`; the matching bullets were removed from `RELEASE_NOTES_v1.0.2.md`, so this file is the current description.
