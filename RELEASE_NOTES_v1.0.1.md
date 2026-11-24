# HK ETA v1.0.1 · 香港實時到站 / Hong Kong real-time ETA

> 中英雙語、**收錄範圍：2026-11-20 至最新**，可直接貼入 GitHub Release 描述框。
> `v1.0.1` tag build 會自動用本檔作為該版本 Release 內文（`RELEASE_NOTES_${TAG}.md` → `body_path`），唔會誤用 `v1.0.0` 嘅說明。
> **版本分界**：`RELEASE_NOTES_v1.0.0.md` 收錄至 **2026-11-19**；本檔由 **2026-11-20 起至最新**，兩份互補、**唔重複**。
> 本檔係純文字：**唔使改任何程式碼、唔使 push、唔使重跑 CI**，亦唔會改動已簽名嘅 APK；版本號已喺 `app/build.gradle.kts` bump 到 `versionCode = 2` / `versionName = 1.0.1`。
>
> Bilingual, **scope: 2026-11-20 through the latest**, paste-ready for the GitHub Release description box.
> The `v1.0.1` tag build selects this file automatically via `RELEASE_NOTES_${TAG}.md` (`body_path`), so it can never reuse the `v1.0.0` notes.
> **Version boundary**: `RELEASE_NOTES_v1.0.0.md` covers **through 2026-11-19**; this file covers **from 2026-11-20 through the latest** — complementary, **never duplicated**.
> Plain text only: **no code change, no push, no CI re-run**, and every signed APK is untouched; `app/build.gradle.kts` is already bumped to `versionCode = 2` / `versionName = 1.0.1`.


> 📌 **歷史註記（2026-11-24）**：本檔保留 `v1.0.1` 當時嘅「2026-11-20 至最新」表述；現行版本已係 `v1.0.2`（`versionCode = 3`），今日 11-24 嘅現行描述見 [RELEASE_NOTES_v1.0.2.md](RELEASE_NOTES_v1.0.2.md)。
---

## 中文（繁體）

**HK ETA** 是一個開源、免 API key 的香港實時巴士／鐵路到站查詢 App。資料直接取自運輸署及各營運商的公開開放數據；Java 負責 HTTPS／JSON／CSV 與時間換算，Kotlin 負責介面、協程並行與自動輪詢。

`v1.0.1` 係 `v1.0.0` 之後嘅**資料層與班次邏輯修正版**。本說明**只列 2026-11-20 至最新**嘅改動；APP1933 搜尋鍵盤、無地圖路線詳情、先授權後自動定位、懸浮液態玻璃導航欄、ETA 自動刷新等 11-19 及之前嘅介面還原，全部收錄喺 `RELEASE_NOTES_v1.0.0.md`，**刻意唔喺本檔重複**。

### 🆕 2026-11-20 至最新嘅更新重點

> 📅 以下按 **2026-11-20 當日／其後最新收尾** 兩段整理，方便對照批次；如只想貼其中一段，複製對應 `####` 段落即可。兩份歷史單日檔 `RELEASE_NOTES_v1.0.1_day1_2026-11-19.md`／`RELEASE_NOTES_v1.0.1_day2_2026-11-20.md` 仍保留備用，但**唔會被 workflow 自動選用**。
> 分界係按本次迭代先後順序歸類，**實際 commit／push 時間以 GitHub 為準**；若你嘅發布日期唔同，改標題日期就得，內容唔使郁。「最新」指本輪最後一次改動（恢復本地資料喺搜尋功能上），其後只有文件同步；8 營運商資料層一個都冇郁。

#### 📅 2026-11-20 · 資料層、站名與發布鏈

- **🏷 聯營路線**：同一路線同時喺九巴／城巴官方目錄出現就顯示 `城巴/九巴`，每一班標明「該班次：九巴／城巴」；主頁同站名 bucket 合併成一張卡，詳情頁站序統一用**九巴／龍運站名同站號**
- **🚏 站名規則與循環線各自顯示**：只有城巴路線／城巴獨有站用城巴官方站名；城巴循環線用城巴方式（`26 勵德邨 - 荷李活道(循環線)`）、九巴循環線用九巴方式（`213X 往 …(循環線)`），並對照本機官方站名表做「站名核對」，唔捏造、唔自動改官方名
- **📦 正式版 Release 版本對應**：每個 tag build 校驗 `tag ↔ build.gradle.kts ↔ 實際 APK badging`（連 `versionCode`／package／SDK），唔夾即擋低發布；Release 只附一個 `hketa-v1.0.1-release.apk`，並附 `VERSION_INFO.txt`／`SIGNING_FINGERPRINT.txt`
- **🧩 已發佈 Release 補正式版 APK**：新增手動 `workflow_dispatch` job，可為**已經發佈**嘅 Release 補上對應版本正式版 APK；用該 tag 自己嘅 source 構建，**唔新建 Release、唔郁 tag、唔改內文**
- **🧪 CI／編譯修正**：Maven Central 429 自動快取＋重試（真編譯錯誤唔重試）、`setup-android@v3`／Node 20／Ubuntu 26 遷移、`checkout@v5`／`setup-java@v5`、`LocationHelper.await`／`LocationListener` nullability、`MtrCatalog.lower(String[])`、`App1933Format.StopDisplay` 頂層符號、`nav_glass_hint formatted="false"`、release notes checker 過嚴

#### 📅 最新 · 綠小巴循環線班次與文件收尾

- **🔁 綠小巴循環線班次修正（本版最新）**：循環線首尾係同一實體站但官方 `stop_seq` 唔同；而家**第一站顯示三個唔同班次**（唔再係三個同一班次），**最後一站顯示自己嘅三個唔同班次**、同第一站零重疊；`eta_seq` 重複回報會被摺疊，判定靠官方 `stop_seq`／「(循環線)」／首尾同站，**冇硬編任何路線號**，官方得 2 班就顯示 2 班
- **📝 中英雙語 Release 說明分界**：`RELEASE_NOTES_v1.0.0.md` 純文字更新至 2026-11-19、本檔收錄 **2026-11-20 至最新**；`RELEASE_NOTES_${TAG}.md` 按 tag 各自選用，改說明**唔郁 APK／tag、唔使重跑 CI**
- **🧾 離線驗證與誠實降級**：新增純 Java 選班次層＋36 項離線 harness（首末站 disjoint、去重、無 `stop_seq` 降級、得 2 班唔捏造）；12 個 `.java` ECJ 17 實編譯、5 個既有 harness 與 38 項 backfill 檢查全過，收藏／直接查站號捷徑冇站序時退回純去重
- **🖼 搜尋頁定稿・顏色唔變（本輪最新）**：`activity_main.xml` 改為截圖分類式（8 類別 tab／6 chips／綠框「路線號碼」／我的書籤／搜尋紀錄），`MainActivity.kt` 由自訂鍵盤改接 `SearchHistoryAdapter`／`BookmarkAdapter`／`SearchHistoryPrefs`；全部 `search_*` 色值一個都冇改、`values-night` 唔覆寫，系統切深／淺色都唔會令搜尋頁變色
- **🐛 Kotlin 編譯修正（最後收尾）**：還原搜尋頁時誤刪仍被 7 處引用嘅 `permissionHint` 欄位，CI `assembleRelease` 報 `Unresolved reference`；已補回宣告＋綁定、清走兩個 dead function、並加 2 條符號檢查＋preflight／workflow drift 早期攔截
- **📦 CI Artifacts 只出正式版 APK（本輪最新）**：移除 `Assemble debug APK`／`hketa-debug-apk`；`assembleRelease` 改為所有 push 都跑並預備穩定名 `hketa-release.apk`，Actions 區唯一 APK Artifact 係 `hketa-release-apk`；tag build 額外複製帶版本名畀 Release asset。checker 加 4 條反向斷言＋preflight／workflow drift 防止 debug artifact 返嚟
- **📍 自動定位加入「更新定位位置」**：授權後喺前景註冊平台 `LocationManager` 位置更新（唔用 Play Services／WorkManager）；裝置郁動超過**半徑 1/4（最少 50 m）**且隔 **≥30 秒**先自動重新定位並重整附近路線，GPS 抖動唔會狂打 API。`onResume` 註冊、`onPause`／`onDestroy` 即刻移除，`permissionHint` 仍係「授予權限」而**唔係重新定位掣**；狀態列顯示 `· 📍 位置自動更新`。未授權／熄定位服務一律唔註冊、唔 fallback 中環；ETA 自動刷新照舊只重用已快取站點
- **🚌 自動定位加入「顯示同站更多路線」**：之前 `nearbyStops(..., 6)` 嘅 6 個配額會被 KMB／CTB 同一實體站嘅兩個 `StopOption` 食晒，近站另一半公司 ETA 直接被 cut 走。而家改為攞 24 個候選、純 Kotlin `NearbyMoreRoutes.select()` 按**實體站名**保留最近 **6 個站嘅全部公司別名**（最多 12 個並行 ETA 查詢防 429），`cardsFrom()` 本來唔 truncate 路線，全部如實出卡並按 `(距離, 路線號, 目的地)` 排，同站路線排埋一齊；狀態列顯示 `N 個站 · M 條路線 · 同站路線全部顯示`。自動定位同上一輪嘅前景位置更新共用 `loadNearbyRoutes()`，兩者一齊生效；ETA 自動刷新重用 expanded 快取，8 營運商資料層／權限閘／色值一個都冇郁
- **🚌 同路線號碼不同巴士營運商分開顯示**：之前 `cardsFrom()` 嘅分組 key 唔帶公司，九巴 12 同 嶼巴 12、城巴 1 同 綠小巴 1 呢類**同號但各自營運**會被塞入同一張卡（三班次混埋兩間公司）。新增純 Kotlin `SameRouteOperators.partition()`：**只有剛好 KMB+CTB 兩家且兩邊都非循環**先維持既有 `城巴/九巴` 聯營卡，其餘（KMB+CTB+第三家／KMB+NLB／CTB+GMB／任一邊循環）一律**按公司拆卡**，各自三班次、各自公司 meta（`九巴 / 龍運`／`城巴`／`嶼巴`／`綠色小巴`），同號兩張卡排埋一齊。循環標記改用**該公司自己嘅班次**判定，唔再由整個 bucket 誤標；拆卡 fallback 用自己 `first.stopId`，唔會繼承 KMB 站號。搜尋頁本來只合併 KMB/CTB 聯營、其餘每公司一列，唔使改；8 營運商資料層／`JointRoutes.augment()`／權限閘／位置更新／同站更多路線／色值一個都冇郁
- **🔎 取消搜尋功能嘅「進階」並將導航欄移到主搜尋頁**：移除搜尋頁 `進階：站序／方向／篩選 ›` 掣、`btnBackToSearch` 返回掣、進階模式狀態旗標同切換函式；8 模式／站序區塊永久隱藏（保留 View 只為既有資料層綁定相容，用戶冇任何入口）。之前底部懸浮液態玻璃導航欄只喺進階模式先 `restoreChrome()` 出現，主搜尋頁反而被 `hideChrome()` 設成 `GONE`；而家 `hideChrome()` 只收起頂部紅色 header／狀態列，**導航欄喺主搜尋頁常駐 VISIBLE**，`selectTab(2)` 永遠直接顯示截圖式分類搜尋主頁。設定頁「更新應用程式」嘅同名進階抽屜掣保留；搜尋頁 10 個 `search_*` 色值、`values-night`、液態玻璃幾何／4 分頁、8 營運商資料層、位置更新、同站更多路線、同號分卡全部一個都冇郁
- **💾 恢復本地資料喺搜尋功能上**：`RouteSearch` 本來就係本機快照優先、即時 API 後備，但取消「進階」後搜尋頁**睇唔到本機資料**，而且由「應用程式設置 → 線路資料更新」更新／清除快照返嚟，`onResume()` 唔會重新預載 → 畫面仍係舊一次結果，本地資料望落「冇咗」。新增純 Kotlin `SearchLocalData.kt`（指紋／來源標記／離線判定）＋搜尋頁 `tvLocalDataStatus` 狀態列：`本機資料：N/M 個來源成功 · 共 K 筆 · 點按更新`，撳一下去設定頁；預載提示如實加 `來源：本機資料`／`來源：即時 API`，唔會把 live fallback 扮成本機資料。`onResume()` 比對 `updatedAt｜來源數｜筆數｜快照檔數` 指紋，變咗即刻 `searchRoutes()` 重新預載並重整歷史。**唔加返進階掣、唔新增色值、唔改 `RouteDataStore`／`RouteSearch`／8 營運商資料層**，實時 ETA 仍永遠即時查、唔快取

### ✨ 完整功能（v1.0.1 含繼承自 v1.0.0）

- **8 種查詢模式**：自動（全部）、九巴／龍運、城巴、嶼巴、綠色小巴、港鐵重鐵、輕鐵、港鐵接駁巴士
- **實時到站**：中文站名、預計分鐘／時間／備註、`-` 剛到站／`—` 沒有服務／預定班次／改道標記；前景自動刷新（15／30／45／60 秒可調，預設 30 秒）＋下拉刷新
- **路線站序＋方向切換**：沿途車站連編號、⇄ 切去／回程、即時篩選站名／站號
- **📍 附近車站定位**：依設定半徑按距離排序，點站即睇該站全部路線 ETA
- **⭐ 收藏常用站**：連 `bound` 方向一併還原
- **⚙ 線路資料更新**：手動／每日／每 3 日／每星期、僅 Wi-Fi、清除本機資料；10 份官方目錄原子寫入，單一來源失敗保留上一版
- **🔄 更新應用程式**：讀取 GitHub 最新 Release、24 小時節流、下載後經 `FileProvider` 交系統安裝
- **🔐 簽名一致性**：固定開發金鑰、CI 指紋鎖定、安裝前比對已安裝 App 與新 APK 嘅簽章憑證
- **🚌 彩虹巴士圖示**：5 種密度 launcher icon、圓形遮罩、Android 8+ adaptive icon、512px 商店預覽圖
- **🔍 截圖分類式搜尋＋無地圖詳情頁**：8 交通類別 tab／6 營運商 chips／我的書籤／搜尋紀錄／綠框輸入、**固定深色＋綠且顏色唔隨主題改變**、目錄預載、站序時間軸、上車站展開未來三班次
- **◌ 懸浮液態玻璃導航欄＋設定頁重整**：56dp 深灰黑藥丸 overlay、跟隨系統外觀、進階設定抽屜（繼承自 `v1.0.0`）

### 📦 安裝

1. 下載本 Release 的 **`hketa-v1.0.1-release.apk`**（正式版、已簽名；或 Actions run 的唯一 Artifact `hketa-release-apk`）
2. Android 8 或以上，首次安裝需開啟「安裝未知應用程式」
3. 由 `v1.0.0` 覆蓋安裝會保持簽名一致；若裝置曾裝過**其他金鑰**簽的舊版，請先卸載
4. 可對照隨附的 `SIGNING_FINGERPRINT.txt` 同 `VERSION_INFO.txt` 核對憑證、APK SHA-256 與版本

**簽章憑證 SHA-256**
`FE:7B:35:E1:9D:90:F5:88:09:1B:08:1B:05:28:40:12:71:56:52:24:13:2D:9A:EB:1C:C0:53:A6:E6:20:02:57`

**需求**：Android 7.0+（minSdk 24）｜target / compile SDK 34｜不依賴 Google Play Services

### 🔧 技術

Java 17 資料層 + Kotlin 1.9.24 UI／協程｜AGP 8.5.2｜Gradle 8.7｜JDK 17｜官方開放數據，免 API key｜單次定位、不背景追蹤｜`versionCode = 2`／`versionName = 1.0.1`

### ⚠️ 已知限制

- **實時 ETA 永遠即時向官方 API 查詢**，不會因「線路資料更新」而變舊，亦**不能離線查 ETA**；離線只可讀已快取的路線／站名／座標目錄
- ETA 自動刷新只喺**前景**：鎖屏或切去第個 App 期間唔更新，返嚟補一次；官方有 429 速率限制，最密 15 秒
- 紅色小巴、電車、渡輪、愉景灣及馬灣巴士目前沒有同類公開實時 ETA API，未納入；新巴已併入城巴
- 若港鐵月度 CSV 沒有座標欄，附近站列表不會顯示重鐵／輕鐵／港鐵接駁巴士（路線模式仍可查詢）
- 綠小巴官方只有 `/stop/{stop_id}` 單站端點；全量站點係選用來源，失敗會自動略過，附近站覆蓋可能只限查過嘅站
- 城巴官方沒有全量站點端點；站名改由 `/stop/{stop_id}` 按需快取，九巴全量站點被 403 擋亦自動退返單站 API
- 公開 ETA API **冇車資、冇預計落車時間、冇地圖路線圖**，詳情頁如實顯示 `車資 —`／`落車 —`，唔捏造
- 綠小巴循環線若官方只回報 2 班就顯示 2 班；收藏／直接輸入站號捷徑得 `stop_id` 冇站序，退回純去重（三個唔同班次），唔做首末站分離
- 循環判定靠官方首尾同站／「(循環線)」／雙 `stop_seq`，**冇硬編路線號**；若官方完全冇循環標記會當普通路線純去重
- 驗簽比對的是**簽章憑證**，不是 APK 檔案雜湊；`VERSION_INFO.txt` 提供 APK SHA-256 供人手核對，但 App 下載後仍未自動校驗檔案雜湊
- adaptive icon 以整張圖作為背景，沒有視差層，亦未提供 Android 13 單色 themed icon
- 只還原 APP1933 版面與到站格式；AI 智能問路、實時載客量、預計落車時間、九巴月票／club1933／遊戲室／bot1933、街景步行導航等專有功能不做，亦沒有灰色佔位按鈕
- App 內更新使用 `REQUEST_INSTALL_PACKAGES`，定位為 GitHub sideload，**不是 Play 商店上架版本**
- 到站時間來自官方開放數據，僅供參考

---

## English

> 📌 **Historical note (2026-11-24)**: this file keeps the `v1.0.1` "2026-11-20 through the latest" wording as published; the current release is `v1.0.2` (`versionCode = 3`) and today's 11-24 description lives in [RELEASE_NOTES_v1.0.2.md](RELEASE_NOTES_v1.0.2.md).

**HK ETA** is an open-source, API-key-free real-time Hong Kong bus / rail arrival app. Data comes directly from public open data published by the Transport Department, the bus operators and the MTR; Java handles HTTPS / JSON / CSV parsing and time conversion, while Kotlin handles the UI, coroutine parallelism and auto-refresh polling.

`v1.0.1` is the **data-layer and trip-logic fix release** after `v1.0.0`. These notes list **only the work from 2026-11-20 through the latest**; the APP1933 search keypad, map-free route detail, permission-first auto-locate, floating liquid-glass navigation bar and ETA auto-refresh from 11-19 and earlier live in `RELEASE_NOTES_v1.0.0.md` and are **deliberately not repeated here**.

### 🆕 Highlights from 2026-11-20 through the latest

> 📅 Grouped below into **2026-11-20 itself / the latest close-out** so the batches can be compared side by side; to paste just one, copy the matching `####` section. The two historical single-day files `RELEASE_NOTES_v1.0.1_day1_2026-11-19.md` / `RELEASE_NOTES_v1.0.1_day2_2026-11-20.md` remain available but are **NOT auto-selected by the workflow**.
> The split follows the order of this iteration; **the actual commit / push times are whatever GitHub shows**. If your release dates differ, change the heading dates only — the content stays valid. "Latest" means the final change of this round (local data restored on the search screen); only documentation was synced afterwards, and the 8-operator data layer was untouched.

#### 📅 2026-11-20 · data layer, stop names & release pipeline

- **🏷 Joint routes**: a route appearing in BOTH the KMB and Citybus official catalogues is shown as `城巴/九巴`, with every trip labelled "該班次：九巴 / 城巴"; Home buckets of the same physical stop merge into one card, and the detail stop sequence uniformly uses **KMB / LWB stop names and ids**
- **🚏 Stop-name rules & per-operator circular display**: only Citybus routes / Citybus-only stops use Citybus official names; a Citybus circular line is shown the Citybus way (`26 勵德邨 - 荷李活道(循環線)`) and a KMB circular line the KMB way (`213X 往 …(循環線)`), with a "stop-name audit" against the local official name table — nothing invented, official names never auto-rewritten
- **📦 Production Release version matching**: every tag build verifies `tag ↔ build.gradle.kts ↔ actual APK badging` (plus `versionCode` / package / SDK) and refuses to publish on mismatch; the Release attaches exactly one `hketa-v1.0.1-release.apk` plus `VERSION_INFO.txt` / `SIGNING_FINGERPRINT.txt`
- **🧩 Backfill for an already-published Release**: a new manual `workflow_dispatch` job can attach the matching signed production APK to an **already-published** Release; it builds from **that tag's own source** and **never creates a Release, moves a tag or rewrites the body**
- **🧪 CI / compile fixes**: Maven Central 429 caching + retry (real compile errors are never retried), `setup-android@v3` / Node 20 / Ubuntu 26 migration, `checkout@v5` / `setup-java@v5`, `LocationHelper.await` / `LocationListener` nullability, `MtrCatalog.lower(String[])`, the `App1933Format.StopDisplay` top-level symbol, `nav_glass_hint formatted="false"`, and an over-strict release-notes checker

#### 📅 Latest · GMB circular trips & documentation close-out

- **🔁 GMB circular trip fix (newest in this release)**: the first and last stops are the SAME physical stop but carry different official `stop_seq`; the **first stop now shows three DISTINCT trips** (instead of three copies of one) and the **last stop shows its OWN three distinct trips** with zero overlap with the first; repeated `eta_seq` reports are collapsed, detection uses official `stop_seq` / "(循環線)" / identical terminal ids, **no hard-coded route numbers**, and if the operator reports only 2 trips we show 2
- **📝 Bilingual release-note boundary**: `RELEASE_NOTES_v1.0.0.md` was updated as plain text through 2026-11-19, while this file covers **from 2026-11-20 through the latest**; `RELEASE_NOTES_${TAG}.md` selects the matching file per tag, and editing notes **never touches the APK / tag and needs no CI re-run**
- **🧾 Offline verification & honest degradation**: a pure-Java trip selector plus 36 offline harness checks (disjoint termini, de-duplication, missing-`stop_seq` fallback, never inventing a 3rd trip); all 12 `.java` files compile under ECJ 17, and the 5 existing harnesses plus 38 published-release checks pass. Favourites / direct stop-id shortcuts have no sequence and degrade to plain de-duplication
- **🖼 Search page locked, colours unchanged (latest this round)**: `activity_main.xml` now matches the screenshot categories (8 tabs / 6 chips / green-outlined route field / bookmarks / search history) and `MainActivity.kt` was rewired from the custom keypad to `SearchHistoryAdapter` / `BookmarkAdapter` / `SearchHistoryPrefs`; not one `search_*` value was edited, `values-night` never overrides them, and switching system Dark / Light mode cannot recolour the search page
- **🐛 Kotlin compile fix (final close-out)**: rewiring the search page accidentally removed the `permissionHint` field still used at 7 call sites, so CI `assembleRelease` failed with `Unresolved reference`; the declaration + binding are restored, two dead functions removed, and 2 symbol checks plus preflight / workflow drift rules now catch the same mistake early
- **📦 CI Artifacts expose ONLY the production APK (latest this round)**: the `Assemble debug APK` / `hketa-debug-apk` steps are removed; `assembleRelease` now runs on every push, prepares a stable `hketa-release.apk`, and `hketa-release-apk` is the single APK Artifact. Tag builds additionally copy a versioned name for the Release asset. 4 new negative checks plus preflight / workflow drift rules keep debug artifacts out
- **📍 Auto-locate now UPDATES the device position**: once permission is granted, a foreground platform `LocationManager` listener is registered (no Play Services / WorkManager). Nearby routes are re-located and rebuilt only after the device moves more than **1/4 of the chosen radius (min 50 m)** AND at least **30 s** have passed, so GPS jitter cannot hammer the API. Registered in `onResume`, removed in `onPause` / `onDestroy`; `permissionHint` is still a **permission-consent hint, not a relocate button**. The status line shows `· 📍 位置自動更新`. Nothing is registered without consent or with location services off, and there is no Central fallback; ETA auto-refresh still reuses the cached stops only
- **🚌 Auto-locate now shows MORE routes per stop**: the old `nearbyStops(..., 6)` quota was eaten by the two `StopOption` rows (KMB / CTB) of the same physical stop, so the other operator's ETA for the nearest stop was silently cut. Now 24 candidates are fetched and a pure-Kotlin `NearbyMoreRoutes.select()` keeps **ALL company aliases of the nearest 6 physical stops**, grouped by stop name (capped at 12 parallel ETA queries to avoid 429). `cardsFrom()` never truncates routes, so every official route becomes a card, sorted by `(distance, route, destination)` to keep one stop's routes together; the status line reads `N stops · M routes · all routes per stop`. Both the initial auto-locate and last round's foreground position updates share `loadNearbyRoutes()`, and ETA auto-refresh reuses the expanded cache. The 8-operator data layer, permission gate and every colour are untouched
- **🚌 SAME route number, DIFFERENT bus operators are shown separately**: `cardsFrom()` grouped by `route|stop|dest` without company, so independently-operated same-number routes (KMB 12 vs NLB 12, CTB 1 vs GMB 1) ended up in ONE card with trips from two companies mixed. A pure-Kotlin `SameRouteOperators.partition()` now keeps the existing `城巴/九巴` joint card **only for an exact KMB+CTB pair where both sides are non-circular**; every other combination (KMB+CTB+third, KMB+NLB, CTB+GMB, either side circular) is **split into one card per operator**, with its own three trips and its own operator meta (`九巴 / 龍運` / `城巴` / `嶼巴` / `綠色小巴`), sorted adjacent. The circular flag is now derived from THAT company's own arrivals instead of the whole bucket, and a split card falls back to its own `first.stopId` rather than inheriting the KMB id. Search rows already only collapse the KMB/CTB joint pair, so they needed no change. The 8-operator data layer, `JointRoutes.augment()`, permission gate, position updates, more-routes-per-stop and every colour are untouched
- **🔎 Search "Advanced" removed + nav bar moved to the main search page**: the `進階：站序／方向／篩選 ›` button, the back-to-search button, the advanced-mode flag and its switching function are all removed; the 8-mode / stop-sequence block stays permanently hidden (its Views are kept only for existing data-layer bindings, with no user entry point). Previously the floating liquid-glass bottom nav only appeared in advanced mode via `restoreChrome()` while the main search page hid it with `GONE`; now `hideChrome()` only collapses the red top header / status lines and the **nav bar stays VISIBLE on the main search page**, with `selectTab(2)` always showing the screenshot-style category search home. The identically-named advanced drawer button on the Settings screen is kept. The 10 `search_*` colours, `values-night`, glass geometry / 4 tabs, 8-operator data layer, position updates, more-routes-per-stop and same-number operator split are all untouched
- **💾 RESTORE local data on the search screen**: `RouteSearch` was already local-snapshot-first with live API fallback, but after removing "Advanced" the search page showed **no local-data state at all**, and returning from Settings → route-data refresh/clear never re-preloaded, so the screen kept the previous (possibly live-fallback) rows and local data looked gone. A pure-Kotlin `SearchLocalData.kt` (signature / source tag / offline check) plus a `tvLocalDataStatus` row now shows `本機資料：N/M sources · K records · tap to update`, opens Settings on tap, and the preload hint is honestly tagged `來源：本機資料` / `來源：即時 API` — a live fallback is never dressed up as local data. `onResume()` compares the `updatedAt|sources|records|snapshot-files` signature and re-runs `searchRoutes()` + history when it changed. **No Advanced button restored, no new colour, no change to `RouteDataStore` / `RouteSearch` / the 8-operator data layer**; real-time ETA is still always live and never cached

### ✨ Full feature set (v1.0.1, including what is inherited from v1.0.0)

- **8 lookup modes**: Auto (all), KMB / LWB, Citybus, NLB, Green Minibus, MTR Heavy Rail, Light Rail, MTR Bus
- **Real-time ETA**: Chinese stop names, minutes / clock time / remarks, `-` just arrived / `—` no service / scheduled-trip / diversion markers; foreground auto-refresh (15 / 30 / 45 / 60 s, default 30 s) plus pull-to-refresh
- **Stop sequence + direction switch**: stops with ids, ⇄ inbound / outbound toggle, live stop-name / stop-id filter
- **📍 Nearby stops**: sorted by distance using the configured radius; tap a stop to see ETA for **all** routes serving it
- **⭐ Favourites**: frequently used stops, including the `bound` direction
- **⚙ Route catalogue update**: manual / daily / every 3 days / weekly, Wi-Fi-only, clear local data; 10 official catalogues written atomically, a failed source keeps the previous snapshot
- **🔄 In-app app update**: checks the latest GitHub Release, 24-hour throttle, installs via `FileProvider`
- **🔐 Signing consistency**: fixed development keystore, CI-pinned fingerprint, pre-install comparison of the installed app and downloaded APK certificates
- **🚌 Rainbow-bus icon**: launcher icons in 5 densities, dedicated round icon, Android 8+ adaptive icon, 512px store preview
- **🔍 Screenshot-category search + map-free detail**: 8 transport tabs / 6 operator chips / bookmarks / history / green-outlined field, a **fixed dark + green palette that never changes with the theme**, catalogue preload, stop timeline and the next three arrivals at the boarding stop
- **◌ Floating liquid-glass nav bar + reorganised Settings**: 56dp dark charcoal pill overlay, follows system appearance, "Advanced settings" drawer (inherited from `v1.0.0`)

### 📦 Install

1. Download **`hketa-v1.0.1-release.apk`** (signed production build) from this Release, or the single `hketa-release-apk` Artifact of an Actions run
2. On Android 8+, enable "Install unknown apps" for the first install
3. Overwriting `v1.0.0` keeps signing consistent; **uninstall first** if the device previously had a build signed with a different key
4. Cross-check the certificate, APK SHA-256 and version against the attached `SIGNING_FINGERPRINT.txt` and `VERSION_INFO.txt`

**Signing certificate SHA-256**
`FE:7B:35:E1:9D:90:F5:88:09:1B:08:1B:05:28:40:12:71:56:52:24:13:2D:9A:EB:1C:C0:53:A6:E6:20:02:57`

**Requirements**: Android 7.0+ (minSdk 24) | target / compile SDK 34 | no Google Play Services dependency

### 🔧 Tech

Java 17 data layer + Kotlin 1.9.24 UI / coroutines | AGP 8.5.2 | Gradle 8.7 | JDK 17 | official open data, no API key | one-shot location, no background tracking | `versionCode = 2` / `versionName = 1.0.1`

### ⚠️ Known limitations

- **Live ETA is always fetched from the official API** — the catalogue updater never serves stale arrival times, and **ETA does not work offline**; offline use is limited to cached route / stop / coordinate catalogues
- ETA auto-refresh runs **in the foreground only**: it does not update while the screen is locked or another app is active, and catches up on return; official rate limits apply (HTTP 429), 15 seconds is the densest interval
- Red minibuses, trams, ferries, Discovery Bay and Ma Wan buses have no equivalent public real-time ETA API and are not included; NWFB has been merged into Citybus
- If an MTR monthly CSV has no coordinate column, heavy rail / Light Rail / MTR Bus stops will not appear in the nearby list (route mode still works)
- GMB officially exposes only `/stop/{stop_id}` per stop; the bulk stop list is an optional source that is skipped on failure, so nearby coverage may be limited to stops already queried
- Citybus has no bulk stop-list endpoint; names are cached on demand via `/stop/{stop_id}`, and a 403 on the KMB bulk list automatically falls back to the single-stop API
- The public ETA APIs carry **no fare, no predicted alighting time and no map route**, so the detail page honestly shows `車資 —` / `落車 —` instead of inventing them
- A GMB circular line with only 2 reported trips shows 2; favourites / direct stop-id shortcuts have only `stop_id` and no sequence, so they degrade to plain de-duplication (three distinct trips) without first/last separation
- Circularity is detected from official identical terminal ids / "(循環線)" / two `stop_seq` values, **never a hard-coded route list**; with no circular marker at all the route is treated as ordinary and simply de-duplicated
- Signature verification compares the **signing certificate**, not an APK file hash; `VERSION_INFO.txt` provides an APK SHA-256 for manual checking, but the app still does not auto-verify the downloaded file hash
- The adaptive icon uses the full artwork as its background: no parallax layer, and no Android 13 monochrome themed icon
- Only the APP1933 layout and arrival format are reproduced; AI route planning, real-time occupancy, predicted alighting time, KMB monthly pass / club1933 / game room / bot1933, street-view walking navigation and similar proprietary features are not implemented — and there are no fake greyed-out buttons
- In-app updates use `REQUEST_INSTALL_PACKAGES`; this project targets **GitHub sideload**, not the Play Store
- Arrival times come from official open data and are indicative only

---

### 維護者備註 / Maintainer notes

- workflow 設 `generate_release_notes: false`，以 `RELEASE_NOTES_${TAG}.md` 對應版本；`v1.0.1` 自動用**本檔（2026-11-20 至最新）**，`v1.0.0` 用 `RELEASE_NOTES_v1.0.0.md`（截至 2026-11-19），兩份**互補、唔重複**。
- 產生 Release：確認 `app/build.gradle.kts` 已 bump 到 `versionCode = 2`／`versionName = "1.0.1"` 並 push，再 `git tag v1.0.1 && git push origin v1.0.1`；tag build 跑 `RELEASE_VERSION_GATE`，通過後先附正式版 APK、`VERSION_INFO.txt` 同 `SIGNING_FINGERPRINT.txt`。
- 若仍需為舊嘅 `v1.0.0` 補正式版 APK，用 `Actions → Run workflow` 嘅 `tag` 輸入；backfill job 用該 tag 自己嘅 source 構建，唔會用 main 嘅新 code 扮舊版本。重新打 `v1.0.0` tag 會被版本閘門擋低。
- 兩份歷史單日檔 `_day1_2026-11-19.md`／`_day2_2026-11-20.md` 保留作可直接貼嘅備用說明，**唔會被 workflow 自動選用**；日期分界按迭代順序整理，實際 commit／push 時間以 GitHub 為準。
- 本檔係純文字：改說明**唔使 push code、唔使重跑 CI、唔郁 APK／tag**；「最新」之後只有文件同步，資料層冇郁。位置更新只喺前景、未授權唔註冊，室內或熄咗定位服務時系統可能冇回報。
- The workflow sets `generate_release_notes: false` and selects `RELEASE_NOTES_${TAG}.md` per tag: `v1.0.1` uses **this file (2026-11-20 through the latest)**, `v1.0.0` uses `RELEASE_NOTES_v1.0.0.md` (through 2026-11-19) — complementary, **never duplicated**.
- To publish: confirm `app/build.gradle.kts` is bumped to `versionCode = 2` / `versionName = "1.0.1"`, push, then `git tag v1.0.1 && git push origin v1.0.1`. The tag build runs `RELEASE_VERSION_GATE` and fails the release on any tag / Gradle / APK mismatch.
- To backfill an older published `v1.0.0`, use `Actions → Run workflow` with the `tag` input; that job builds from the tag's own source, never from newer `main` code. Re-tagging `v1.0.0` is rejected by the version gate.
- The two historical single-day files `_day1_2026-11-19.md` / `_day2_2026-11-20.md` remain paste-ready but are **NOT auto-selected by the workflow**; the split follows iteration order and actual commit / push times are whatever GitHub shows.
- Current version: `versionCode = 2`, `versionName = 1.0.1`. These notes are plain text: **no code push, no CI re-run, no APK / tag change**; only documentation was synced after the latest feature (data layer untouched). Position updates are foreground-only, never registered without consent, and the platform may report no fix indoors or with location services off.
