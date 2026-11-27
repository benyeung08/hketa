# HK ETA v1.0.2 · 香港實時到站 / Hong Kong real-time ETA

> **收錄範圍：2026-11-24（今日）**｜**Scope: 2026-11-24 (today)**。
> `v1.0.2` tag build 會自動用本檔作為該版本 Release 內文（`RELEASE_NOTES_${TAG}.md` → `body_path`），唔會誤用 `v1.0.0`／`v1.0.1` 嘅說明。
> The `v1.0.2` tag build selects this file automatically through `RELEASE_NOTES_${TAG}.md` (`body_path`), so it can never reuse the `v1.0.0` / `v1.0.1` notes.

> **版本分界／Version boundary**：`RELEASE_NOTES_v1.0.0.md` 截至 2026-11-19；`RELEASE_NOTES_v1.0.1.md` 由 2026-11-20 起整理資料層、班次邏輯同發布鏈；**`v1.0.2` 收錄今日（11-24）嘅搜尋介面、定位、卡片、導航同本地資料**；**11-25 起嘅後續改動（開 App 更新線路資料、還原香港出行易式分類、代碼優化、顯示所有路線、液態玻璃、中國版 Android 17 進階材質、單一服務商顯示、循環線頂欄兩顆動作、三個圖標語義同服務時間及班次本地化）已獨立收錄於 `RELEASE_NOTES_v1.0.3.md`**。`v1.0.1` 說明中「至最新」係當時嘅歷史表述；**11-24 當日嘅現行描述以本檔為準**，舊檔保留作歷史、唔郁已簽名 APK／tag。
> `RELEASE_NOTES_v1.0.0.md` covers through 2026-11-19; `RELEASE_NOTES_v1.0.1.md` starts at 2026-11-20 with the data-layer, trip-logic and release-pipeline work; **`v1.0.2` covers today (11-24): search UI, location, cards, navigation and local data**; **the follow-up changes from 11-25 onwards (launch-time catalogue refresh, HKeMobility-style categories, code optimisation, all-routes display, liquid glass, China-ROM Android 17 advanced material, single-operator display, circular two-action header, three-icon meanings and local service-hours data) are documented separately in `RELEASE_NOTES_v1.0.3.md`**. The "through the latest" wording in the `v1.0.1` notes is a historical snapshot; **this file is the current description of 11-24**. Older files are kept as history; no signed APK or tag is touched.

> 本檔係**純文字 Release 說明**，可直接貼入 GitHub Release 描述框；`app/build.gradle.kts` 喺 `v1.0.2` 當時已 bump 到 `versionCode = 3` / `versionName = 1.0.2`（**現行已再 bump 到 `1.0.3` / `4`，見 `RELEASE_NOTES_v1.0.3.md`**），要出 `v1.0.2` 正式版仍需 push 後打 `v1.0.2` tag，由 repo Actions 產出已簽名 APK。
> This is a **plain-text Release note** you can paste straight into the GitHub Release description. `app/build.gradle.kts` was bumped to `versionCode = 3` / `versionName = 1.0.2` for `v1.0.2` (**the current tree has since been bumped again to `1.0.3` / `4`; see `RELEASE_NOTES_v1.0.3.md`**); shipping the actual `v1.0.2` APK still requires pushing and tagging `v1.0.2` so the repo Actions can build the signed release.

---

## 中文（繁體）

### 🆕 本日更新重點（2026-11-24）

#### 📅 2026-11-24 上午：搜尋介面與發布鏈

- **🔎 截圖式分類搜尋頁**：8 個交通類別 tab（港鐵／巴士／專線小巴／渡輪／電車／的士／非專線小巴／居民巴士服務）、6 個營運商 chips、綠框「路線號碼」系統 IME 即時查、我的書籤同持久化去重嘅搜尋紀錄；**10 個 `search_*` 色值同 `values-night` 零改動**
- **🧩 Kotlin 編譯修正**：補回被區塊替換 cut 走、仍被 7 處引用嘅 `permissionHint` 宣告同綁定；checker 加兩條硬閘，掃描全部單純 `<field>.<member>` receiver 逐個對照宣告
- **🧩 第二次 Kotlin 編譯修正（真實 CI 回報）**：`compileReleaseKotlin` 報 `EtaViewModel.kt:385 Unresolved reference: app`；`app` 只係主建構函數參數（無 `val`），喺成員函數入面唔可見。已喺 `loadNearbyRoutes()` 補 `val app = getApplication<Application>()`（同 `refreshNearbyCards()` 一致），並新增通用 `app.` 作用域掃描、2 條硬閘同 preflight/workflow drift，修復前版本 0 秒擋低
- **🇨🇳 中國版 Android 17 液態玻璃相容＋取消導航欄下「☆ 加入收藏」掣**：新增 `NavGlassCompat.kt`（`CN_ANDROID17_GLASS`），依 AOSP《窗口模糊處理》用 `WindowManager#isCrossWindowBlurEnabled` 查當前狀態；國內 ROM／省電模式／影片播放停用 cross-window blur 時，自動改用提高不透明度嘅實色玻璃（同一 56dp／28dp 藥丸），唔會留透明欄直接透落底層。全部反射呼叫＋`runCatching`，唔引用 SDK 34 以上嘅 `VERSION_CODES`、唔靠 GMS 或廠商私有 key、唔用下一代系統先有嘅元件級背景效果；設定頁如實顯示「系統停用視窗模糊 · 實色玻璃」。同時移除主頁列表底、懸浮導航欄下嘅「☆ 加入收藏」長條掣（`NAV_NO_FAV_BUTTON`）——**收藏功能冇取消**：路線詳情頁「收藏」動作、收藏分頁同長按移除照舊，只更新空頁文案
- **📦 Artifacts 只出正式版 APK**：移除 4 個 debug step 同 `hketa-debug-apk`；`assembleRelease` 唔再只喺 tag 觸發，push `main` 都組裝已簽名正式版，唯一 Artifact 為 `hketa-release-apk`
- **📝 CI 錯誤蒸餾**：429／5xx／timeout 先自動重試（30s／60s 退避）；`Unresolved reference`／`error:`／AAPT2 即刻 break，唔會被重試掩蓋，真正錯誤交畀 distiller 印出
- **🔐 簽章與版本閘門不變**：`tag ↔ build.gradle.kts ↔ aapt badging` 三方可校驗（連 `versionCode`／package／SDK），`apksigner` 驗簽後先發布；Release 只附一個 `.apk`
- **🛠 已發佈 Release backfill**：`workflow_dispatch` 可對舊 tag 用**該 tag 自己嘅 source** 補正式版 APK，唔郁 tag／版本／Release 內文，唔會用 main 新 code 扮舊版本
- **📄 中英雙語說明分工**：`v1.0.0` 截至 11-19、`v1.0.1` 由 11-20 起、`v1.0.2` 收錄 11-24、`v1.0.3` 收錄 11-25 起至最新嘅後續改動；`RELEASE_NOTES_${TAG}.md` 按 tag 自動選用，缺檔時用版本對應簡短內文
- **🎨 顏色零改動**：搜尋頁、詳情頁純黑文字、液態玻璃導航欄、聯營標籤全部沿用既有色盤，本日冇新增任何色值

#### 📅 2026-11-24 收尾：定位、卡片與本地資料

- **📍 自動定位加入更新定位位置**：前景平台 `LocationManager` 註冊（無 Play Services／WorkManager），移動 ≥ 半徑 1/4（最少 50 m）且 ≥30 秒先重整附近路線；`onPause()`／`onDestroy()` 即刻移除 listener
- **🚌 自動定位顯示同站更多路線**：攞 24 個候選 → 按實體站名保留最近 6 個站嘅全部公司別名（最多 12 個並行 ETA 查詢防 429），路線唔 truncate，狀態列報告站數＋路線數
- **🚌 同路線號碼不同巴士營運商分開顯示**：純 Kotlin `SameRouteOperators.partition()` 只有「剛好 KMB+CTB 且兩邊都非循環」維持既有 `城巴/九巴` 聯營卡；其餘按公司拆卡，各自三班次同公司 meta，同號排埋一齊
- **🔎 取消搜尋「進階」＋導航欄移到主搜尋頁**：移除進階掣／返回掣／模式切換，8 模式區塊永久隱藏；`hideChrome()` 只收起頂部 header，底部液態玻璃導航欄喺主搜尋頁常駐
- **💾 恢復本地路線資料喺搜尋功能上**：新增 `SearchLocalData.kt` 指紋／來源標記 + 搜尋頁本機資料狀態列；預載提示如實標「本機資料／即時 API」；更新／清除快照返嚟 `onResume()` 指紋變咗即刻重新預載
- **🔄 循環標記 per-company**：循環線判定改用該公司自己嘅班次，拆卡 fallback 用自己 `first.stopId`，唔會繼承 KMB 站號；搜尋頁本來只合併 KMB/CTB 聯營，其餘每公司一列
- **🧪 離線驗證與誠實降級**：preflight 136 檔、static 399 條、5 個 Java harness ＋ 38 項 backfill、43 情境 drift 全過；官方實際只有 2 班就顯示 2 班，唔捏造班次
- **🚫 唔加掣、唔改資料層**：8 營運商 `EtaRepository`／`GeoStops`／`JointRoutes.augment()`／權限三層閘、聯營／循環／GMB 循環線班次邏輯全部一個都冇郁

### ✨ 功能總覽

- **8 種交通模式**：九巴／龍運、城巴、嶼巴、綠色專線小巴、港鐵重鐵、輕鐵、港鐵接駁巴士、渡輪／電車／的士等目錄查詢
- **實時 ETA**：每站未來三班次、分鐘／時鐘／備註；前景重新整理間隔 15／30／45／60 秒可調（預設 30 秒）
- **附近路線**：先授權後自動定位、依設定半徑 50–2000 m 排序、前景位置更新、同站更多路線、同號唔同公司分卡
- **路線詳情（無地圖）**：站序、方向切換、篩選、車資／落車提示；純黑文字＋淺底，唔跟系統夜間反色
- **搜尋**：截圖式分類頁、官方目錄預載、書籤、搜尋紀錄、本機資料狀態列、點列開詳情
- **聯營與循環線**：`城巴/九巴` 逐班標公司；循環線各自用九巴／城巴站名同顯示方式；站名核對如實報告
- **收藏與更新**：長撳移除收藏；App 內檢查 GitHub Release、下載安裝、驗簽指紋比對
- **設定**：線路資料更新（手動／每日／每 3 日／每星期、僅 Wi-Fi、清除本機資料）、半徑、ETA 刷新、導航欄外觀跟隨系統
- **懸浮液態玻璃導航欄**：56dp 藥丸、28dp 圓角、離底約 12%（48–160dp）嘅 overlay，4 分頁，主搜尋頁常駐
- **圖示與簽章**：5 種密度 launcher icon、adaptive icon、512px 商店預覽圖；正式版 APK 已簽名

### 📦 安裝與版本

- 下載本 Release 的 **`hketa-v1.0.2-release.apk`**（正式版、已簽名）；Actions run 的唯一 Artifact 為 **`hketa-release-apk`**
- 版本：**`versionName = 1.0.2`／`versionCode = 3`**；`applicationId = hk.eta.app`；Android 7.0+（minSdk 24），target / compile SDK 34
- 簽章指紋：`FE:7B:35:E1…20:02:57`（`SIGNING_FINGERPRINT.txt` 隨 Release 附上）；升級前可先比對，避免裝到唔同憑證
- 由舊版升級可直接覆蓋安裝（憑證一致）；若曾裝過唔同憑證嘅包，要先解除安裝
- `RELEASE_NOTES_${TAG}.md` 會被 `v1.0.2` tag build 自動選用做 Release 內文，`VERSION_INFO.txt` 記錄實際 badging
- Java 17 資料層 + Kotlin 1.9.24 UI／協程｜AGP 8.5.2｜Gradle 8.7｜JDK 17｜官方開放數據，免 API key

### ✅ 驗證與資料來源

- `preflight` 136 個必要檔案齊全；`static checks` 399 條全綠（含 SEARCH_LOCAL_DATA 9 條、SEARCH_NO_ADVANCED 8 條、SEPARATE_OPERATORS 10 條、KOTLIN_APP_SCOPE 2 條、CN_ANDROID17_GLASS 5 條、NAV_NO_FAV_BUTTON 2 條、LAUNCH_CATALOGUE_REFRESH 8 條、HKE_MOBILITY_CATEGORIES 8 條）
- 12 個 `.java` 以 ECJ 17 實編譯 `exit 0`（58 class）；5 個 Java harness ＋ 38 項 published-release backfill 全過
- Kotlin 符號對照 13 個相關檔 0 未定義；8 Python／12 Java／56 XML／YAML／29 Kotlin 語法、括號、資源 0 缺失
- 色盤 10 個 `search_*` 值 + `values-night` 零覆寫；inline preflight 43 情境 drift 全部 0 秒精確命中
- README 中英 23 章 125 條對稱；6 份 Release 說明中英對稱；舊 repo 用合併包覆蓋 → 全專案 hash 0 差異
- 路線／站名／座標來自香港政府官方開放數據（DATA.GOV.HK、data.etabus.gov.hk、rt.data.gov.hk、data.etagmb.gov.hk）；實時到站時間向官方 API 即時查詢

### ⚠️ 已知限制

- **實時 ETA 需要網絡**：本機快照只涵蓋路線／站名／座標目錄，離線可睇目錄同預載列，但到站時間永遠即時查、唔快取
- **聯營 partner 查詢屬 best-effort**：九巴同城巴 stopId 命名空間唔同，查唔到就只顯示主要公司；站名用字唔同會退回各自一張卡
- **站名核對如實報告**：未載入官方站名對照表會標示「未載入」，唔會宣稱全部正確、唔自動改官方名、唔捏造班次
- **前景 only 定位**：鎖屏或切去其他 App 即停，省電模式／室內可能延遲或冇回報，附近路線會維持上一批而唔假造位置
- **唔包含官方私有服務**：AI 智能問路、實時載客量、九巴月票、club1933 會員、街景／步行導航、bot1933 對話及廣告等需要私有服務或會員帳號嘅功能

---

## English

### 🆕 Highlights today (2026-11-24)

#### 📅 2026-11-24 morning: search UI and release pipeline

- **🔎 Screenshot-style category search**: 8 transport tabs (MTR / Bus / GMB / Ferry / Tram / Taxi / Non-franchised bus / Residents' bus), 6 operator chips, a green "Route number" field backed by the system IME with instant lookup, bookmarks and de-duplicated persistent history; **all 10 `search_*` colours and `values-night` are unchanged**
- **🧩 Kotlin compile fix**: restored the `permissionHint` declaration and binding that a block replacement had removed while 7 call sites still used it; the checker now adds two hard gates and scans every plain `<field>.<member>` receiver against declarations
- **🧩 Second Kotlin compile fix (from a real CI log)**: `compileReleaseKotlin` failed with `EtaViewModel.kt:385 Unresolved reference: app`; `app` is a primary-constructor parameter without `val` and is invisible inside member functions. `loadNearbyRoutes()` now declares `val app = getApplication<Application>()` (matching `refreshNearbyCards()`), backed by a generic `app.` scope scan, 2 hard gates and preflight/workflow drift that reject the broken version in 0 seconds
- **🇨🇳 China-ROM Android 17 glass compatibility + REMOVE the "☆ 加入收藏" button under the bar**: `NavGlassCompat.kt` (`CN_ANDROID17_GLASS`) follows AOSP's window-blur guidance by querying `WindowManager#isCrossWindowBlurEnabled`; when a domestic ROM, battery saver or video playback disables cross-window blur, the bar switches to higher-alpha opaque glass (same 56dp / 28dp pill) instead of leaving a transparent strip over content. Every call is reflective + `runCatching`, with no SDK>34 `VERSION_CODES`, no GMS / vendor-private key and no next-generation component-level blur; Settings honestly reports "system blur disabled · opaque glass". The `☆ 加入收藏` strip below the floating nav is removed (`NAV_NO_FAV_BUTTON`) — **favourites are NOT removed**: the detail-page Favourite action, the favourites tab and long-press removal all remain, with an updated empty-state hint
- **📦 Artifacts now contain the production APK only**: 4 debug steps and `hketa-debug-apk` removed; `assembleRelease` is no longer tag-gated, so a push to `main` also builds the signed release and the sole Artifact is `hketa-release-apk`
- **📝 CI error distillation**: only 429 / 5xx / timeout are retried (30s / 60s back-off); `Unresolved reference`, `error:` and AAPT2 failures break immediately instead of being hidden, and the real error is printed by the distiller
- **🔐 Signing and version gate unchanged**: `tag ↔ build.gradle.kts ↔ aapt badging` are cross-checked (including `versionCode`, package and SDK), `apksigner` runs before publishing, and the Release carries exactly one `.apk` asset
- **🛠 Published-Release backfill**: a `workflow_dispatch` job can attach a signed release APK to an older tag using **that tag's own source**, without touching the tag, version or Release body — never disguising newer `main` code as an old build
- **📄 Bilingual release-note split**: `v1.0.0` covers through 11-19, `v1.0.1` starts at 11-20, `v1.0.2` covers 11-24, `v1.0.3` covers the follow-up changes from 11-25 through the latest; `RELEASE_NOTES_${TAG}.md` is selected per tag, with a short version-matched fallback body when a file is missing
- **🎨 Zero colour changes**: the search page, pure-black detail text, liquid-glass nav and joint-route labels all reuse the existing palette; no colour value was added today

#### 📅 2026-11-24 wrap-up: location, cards and local data

- **📍 Auto-locate now UPDATES the device position**: a foreground platform `LocationManager` listener (no Play Services / WorkManager) reloads nearby routes only after moving ≥ 1/4 of the radius (min 50 m) and ≥ 30 s; listeners are removed in `onPause()` / `onDestroy()`
- **🚌 Auto-locate shows MORE routes per stop**: 24 candidates → all company aliases of the nearest 6 physical stops grouped by stop name (max 12 parallel ETA queries to avoid 429); routes are never truncated and the status line reports stop and route counts
- **🚌 Same route number, different bus operators shown separately**: pure-Kotlin `SameRouteOperators.partition()` keeps the existing `城巴/九巴` joint card **only for an exact KMB+CTB non-circular pair**; every other combination is split per operator with its own three trips and operator meta, sorted adjacent
- **🔎 Search "Advanced" removed + nav bar moved to the main search page**: advanced button / back button / mode switch removed, the 8-mode block stays permanently hidden; `hideChrome()` collapses only the top header so the liquid-glass nav stays visible on search home
- **💾 Local route data restored on the search screen**: `SearchLocalData.kt` signature / source tag plus an on-page local-data status row; the preload hint honestly says "local data" or "live API", and `onResume()` re-preloads immediately when the snapshot fingerprint changes after an update / clear
- **🔄 Circular flag is per-company**: circular detection uses that operator's own arrivals and a split card falls back to its own `first.stopId` instead of inheriting the KMB id; search rows already only collapse the KMB/CTB joint pair
- **🧪 Offline verification, honest degradation**: preflight 136 files, 399 static checks, 5 Java harnesses + 38 backfill checks and 43 drift scenarios all pass; if the official feed reports only 2 trips, 2 are shown — nothing is invented
- **🚫 No new buttons, no data-layer changes**: the 8-operator `EtaRepository` / `GeoStops` / `JointRoutes.augment()`, the three-layer permission gate and the joint / circular / GMB-circular trip logic are all untouched

### ✨ Feature overview

- **8 transport modes**: KMB/LWB, Citybus, NLB, green minibus, MTR heavy rail, Light Rail, MTR feeder bus, plus ferry / tram / taxi catalogue entries
- **Real-time ETA**: up to three upcoming trips per stop with minutes / clock / remarks; foreground refresh at 15 / 30 / 45 / 60 s (default 30 s)
- **Nearby routes**: consent-first auto-location, configurable 50–2000 m radius, foreground position updates, more routes per stop, same-number operator split
- **Route detail (map-free)**: stop sequence, direction switch, filter, fare / alight hints; pure-black text on a light background, deliberately not inverted at night
- **Search**: screenshot-style category page, official catalogue preload, bookmarks, history, local-data status row, tap-through to detail
- **Joint and circular routes**: `城巴/九巴` with per-trip operator labels; each circular line keeps its own KMB / Citybus stop names and display style; stop-name audits report honestly
- **Favourites and updates**: long-press to remove; in-app GitHub Release check, download / install and signing-fingerprint comparison
- **Settings**: route catalogue update (manual / daily / every 3 days / weekly, Wi-Fi-only, clear local data), radius, ETA refresh and system-following nav appearance
- **Floating liquid-glass nav**: 56dp pill, 28dp radius, overlay ~12% above the bottom (48–160dp), 4 tabs, now always visible on the search page
- **Icons and signing**: launcher icons in 5 densities, adaptive icon, 512px store preview; the production APK is signed

### 📦 Install and version

- Download **`hketa-v1.0.2-release.apk`** from this Release (production, signed); the sole Actions Artifact is **`hketa-release-apk`**
- Version: **`versionName = 1.0.2` / `versionCode = 3`**; `applicationId = hk.eta.app`; Android 7.0+ (minSdk 24), target / compile SDK 34
- Signing fingerprint: `FE:7B:35:E1…20:02:57` (`SIGNING_FINGERPRINT.txt` is attached); compare it before upgrading to avoid installing a differently signed build
- Upgrading from an older build installs over the top (same certificate); if a differently signed build was installed before, uninstall it first
- `RELEASE_NOTES_${TAG}.md` is selected automatically by the `v1.0.2` tag build as the Release body; `VERSION_INFO.txt` records the actual badging
- Java 17 data layer + Kotlin 1.9.24 UI / coroutines | AGP 8.5.2 | Gradle 8.7 | JDK 17 | official open data, no API key

### ✅ Verification and data sources

- `preflight`: all 136 required files present; 399 static checks green (including 9 SEARCH_LOCAL_DATA, 8 SEARCH_NO_ADVANCED, 10 SEPARATE_OPERATORS, 2 KOTLIN_APP_SCOPE, 5 CN_ANDROID17_GLASS, 2 NAV_NO_FAV_BUTTON, 8 LAUNCH_CATALOGUE_REFRESH and 8 HKE_MOBILITY_CATEGORIES)
- 12 `.java` files compiled with ECJ 17, `exit 0` (58 classes); 5 Java harnesses + 38 published-Release backfill checks pass
- Kotlin symbol cross-check across 13 related files: 0 undefined; 8 Python / 12 Java / 56 XML / YAML / 29 Kotlin syntax, brackets and resources: 0 missing
- 10 `search_*` colours with zero `values-night` overrides; all 43 inline-preflight drift scenarios detected in 0 seconds
- README: 23 sections / 125 bullets symmetric in Chinese and English; 6 bilingual Release-note files symmetric; overlaying the merged pack on an old repo gives 0 hash differences across the project
- Routes / stop names / coordinates come from Hong Kong government open data (DATA.GOV.HK, data.etabus.gov.hk, rt.data.gov.hk, data.etagmb.gov.hk); live ETAs are queried from the official APIs

### ⚠️ Known limitations

- **Live ETA needs network**: local snapshots cover route / stop-name / coordinate catalogues only; catalogues and preload rows work offline but arrival times are always queried live and never cached
- **Joint partner lookup is best-effort**: KMB and Citybus stop IDs live in different namespaces, so an unanswered partner shows only the primary operator; differently worded stop names fall back to separate cards
- **Stop-name audits report honestly**: when the official name table is not loaded the UI says "not loaded" — it never claims everything is correct, never rewrites official names and never invents trips
- **Foreground-only location**: updates stop on lock / app switch; power saving or indoors may delay or omit fixes, and nearby routes then keep the previous batch instead of faking a position
- **No official private services**: AI route chat, real-time occupancy, KMB monthly pass, club1933 membership, street view / walking navigation, bot1933 chat and ads require private services or member accounts

---

> 📌 **改說明唔等於改 APK／Editing notes never touches the APK / tag**：改 Release 描述唔使重跑 CI，亦唔會改動已簽名嘅 APK；實際功能由對應 tag 嘅 source 構建。Editing a Release body needs no CI re-run and changes no signed APK; the actual features come from that tag's own source build.
> 📌 11-25 起嘅後續 App 改動已移往 `RELEASE_NOTES_v1.0.3.md`；本檔只保留 11-24 嘅收錄範圍。The 11-25-and-later app changes now live in `RELEASE_NOTES_v1.0.3.md`; this file keeps its 11-24 scope only.
