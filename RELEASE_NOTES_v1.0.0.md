# HK ETA v1.0.0 · 香港實時到站

> 中文（繁體／香港）與 English 兩版對稱，可直接貼入 GitHub Release 描述框；
> 而家 `v1.0.0` tag build 亦會**自動用本檔作為該版本 Release 內文**（`body_path`）。
> 這是純文字說明：**不需要修改任何程式碼、不需要 push、不需要重跑 CI。**

---

## 中文（繁體）

**HK ETA** 是一個開源、免 API key 的香港實時巴士／鐵路到站查詢 App。資料直接取自運輸署及各營運商的公開開放數據；Java 負責 HTTPS／JSON／CSV 與時間換算，Kotlin 負責介面、協程並行與自動輪詢。

### ✨ 功能

- **🚌 APP1933 版面**：九巴紅色標頭、主頁附近路線卡片（大字路線號＋往目的地＋站名連編號＋右側分鐘）、底部 主頁／收藏／搜尋／更多 四分頁
- **未來三班次**：標記 `-` 剛到站、`—` 沒有服務、預定班次、改道、聯營公司
- **路線站序＋方向切換**：沿途車站含站編號、一鍵 ⇄ 切換方向、即時篩選站名／站號
- **8 種查詢模式**：自動（全部）、九巴／龍運、城巴、嶼巴、綠色小巴、港鐵重鐵、輕鐵、港鐵接駁巴士
- **實時到站**：中文站名、預計分鐘／預計時間／備註；每 30 秒自動更新、下拉刷新
- **📍 附近車站定位**：300／500／1000／2000 公尺半徑，按距離排序，點站即睇該站全部路線 ETA
- **⭐ 收藏常用站**：連 `bound` 方向一併還原
- **⚙ 線路資料更新**：手動／每日／每 3 日／每星期、僅 Wi-Fi、清除本機資料；10 份官方目錄原子寫入，單一來源失敗保留上一版
- **🔄 更新應用程式**：讀取 GitHub 最新 Release、24 小時節流、下載後經 `FileProvider` 交系統安裝
- **🔐 簽名一致性**：固定開發金鑰、CI 指紋鎖定、安裝前比對已安裝 App 與新 APK 的簽章憑證
- **🚌 彩虹巴士圖示**：5 種密度 launcher icon、圓形遮罩、Android 8+ adaptive icon、512px 商店預覽圖

### 📦 安裝

1. 下載本 Release 的 **`hketa-v1.0.0-release.apk`**（正式版、已簽名；或該次 Actions run 的 Artifact `hketa-release-apk`／`hketa-debug-apk`）
2. Android 8 或以上，首次安裝需開啟「安裝未知應用程式」
3. 覆蓋安裝會保持簽名一致；若裝置曾裝過**其他金鑰**簽的舊版，請先卸載
4. 可對照隨附的 `SIGNING_FINGERPRINT.txt` 同 `VERSION_INFO.txt` 核對憑證、APK SHA-256 與版本

**簽章憑證 SHA-256**
`FE:7B:35:E1:9D:90:F5:88:09:1B:08:1B:05:28:40:12:71:56:52:24:13:2D:9A:EB:1C:C0:53:A6:E6:20:02:57`

**需求**：Android 7.0+（minSdk 24）｜target / compile SDK 34｜不依賴 Google Play Services

### 🔧 技術

Java 17 資料層 + Kotlin 1.9.24 UI／協程｜AGP 8.5.2｜Gradle 8.7｜JDK 17｜官方開放數據，免 API key｜單次定位、不背景追蹤

### ⚠️ 已知限制

- **實時 ETA 永遠即時向官方 API 查詢**，不會因「線路資料更新」而變舊，亦**不能離線查 ETA**；離線只可讀已快取的路線／站名／座標目錄
- 紅色小巴、電車、渡輪、愉景灣及馬灣巴士目前沒有同類公開實時 ETA API，未納入；新巴已併入城巴
- 若港鐵月度 CSV 沒有座標欄，附近站列表不會顯示重鐵／輕鐵／港鐵接駁巴士（路線模式仍可查詢）
- 「自動（全部）」會同時向多間營運商發請求，官方有速率限制（HTTP 429），請避免密集輪詢
- 拒絕定位權限／沒有定位服務／逾時會退回中環預設位置，並在狀態列標示
- App 內更新使用 `REQUEST_INSTALL_PACKAGES`，定位為 GitHub sideload，**不是 Play 商店上架版本**
- 驗簽比對的是**簽章憑證**，不是 APK 檔案雜湊；`VERSION_INFO.txt` 提供 APK SHA-256 供人手核對，但 App 下載後仍未自動校驗檔案雜湊
- adaptive icon 以整張圖作為背景，沒有視差層，亦未提供 Android 13 單色 themed icon
- 只還原 APP1933 版面與到站格式；AI 智能問路、實時載客量、預計落車時間、九巴月票／club1933／遊戲室／bot1933、街景步行導航等專有功能不做，亦沒有灰色佔位按鈕
- 城巴官方沒有全量站點端點；站名改由 /stop/{stop_id} 單站 API 按需快取。九巴全量站點若被 HTTP 403 擋亦自動改用單站 API，站序仍顯示中文站名，線路資料更新會把選用來源標為「略過」而唔係「失敗」
- 搜尋頁改為 APP1933 全螢幕：紅色「搜尋」標題、置中「輸入路線號碼」、自訂數字／字母鍵盤、即時列出「路線號＋往 目的地＋營運商細字」；點結果直接載入該方向站序，原有站號／方向／篩選功能全部保留
- 主頁「重新定位」按鈕已移除，改為自動定位；開 App／下拉刷新會先取得定位權限，未授權期間只顯示可點嘅授權提示，唔會靜默用中環預設座標查附近路線
- 設定頁「更新應用程式」改成深色版本膠囊 `v1.0.0 · 1　⟳ 🕐 ⧉`：重新檢查、更新記錄、複製版本資訊三個捷徑；下載／安裝／簽名驗證等全部功能收喺「進階設定」抽屜，發現新版本會自動展開
- 主頁「↻ 刷新」掣與半徑 Spinner 移除，改為下拉頁面刷新；「定位附近路線嘅距離」搬入設定頁，按截图做成深色「50 ▼」大數字選擇器，提供 50–2000 m 共 7 檔，改完返主頁自動重新定位
- 底部「更多」改名「應用程式設置」並改為懸浮液態玻璃導航欄（跟隨系統深色／高對比／減少動態效果，API 31+ 背景模糊）；對照截圖嘅深色圓角白描邊免責聲明卡由「更多」搬入設定頁底部
- 撳主頁「附近路線」卡片會開全螢幕路線詳情：紅色頂欄、站序時間軸、未來三班次，還原截圖但**完全冇地圖**（純 XML 路線示意取代 Google 地圖）；車資／預計落車時間如實顯示「—」，唔捏造 $5.6／15:31
- 搜尋主頁原本嘅深色空白位改為**預先載入官方路線目錄**（最多 24 條，App 啟動即背景下載），未撳鍵已見到 `12 往 海麗邨 ›`；點搜尋結果列會帶埋 `O／I／1` 方向開同一個「路線詳情（無地圖）」頁
- 新增 **ETA 自動刷新**：主頁附近路線卡片、已選車站／收藏、路線詳情頁全部前景定時向官方 API 更新；設定頁有開關（預設開）＋ 15／30／45／60 秒間隔選擇器，狀態列顯示上次自動更新時間。只喺前景輪詢、唔重新定位、未授權或無資料唔打 API，下拉手動刷新保留
- 對照截圖更改**導航欄大小與位置**：由 64dp 近滿寬藍灰條改為 56dp 高、水平置中、貼底 8dp、左右按屏寬自動收窄（手機每邊 6%、平板／横屏 18%，夾喺 16–256dp）嘅深灰黑全圓角藥丸；選中分頁改成內嵌深色 pill＋藍色字，三種系統外觀共用同一幾何，旋轉螢幕即時重新置中
- **正式版 APK 與 Release 版本對應**：每個 tag build 都會校驗 `tag ↔ app/build.gradle.kts ↔ 實際 APK badging`（連 `versionCode`／package／SDK），唔夾即擋低發布；Release 只附一個 `hketa-v1.0.0-release.apk` 正式版，並附上 `VERSION_INFO.txt`／`SIGNING_FINGERPRINT.txt`
- 到站時間以官方開放數據為準，僅供參考

---

## English

**HK ETA** is an open-source, API-key-free real-time arrival app for Hong Kong buses and rail. Data comes directly from official open-data feeds (Transport Department and operators). Java handles HTTPS / JSON / CSV parsing and time conversion; Kotlin powers the UI, coroutines and auto-refresh.

### ✨ Features

- **🚌 APP1933 layout**: KMB-red header, nearby-route cards (large route no. + destination + stop with id + big minutes), Home / Favourites / Search / More bottom tabs
- **Next three arrivals**: markers for `-` (just arrived), `—` (no service), scheduled trips, diversion and joint operators
- **Stop sequence + direction switch**: stops with ids, one-tap ⇄ bound toggle, live stop filter
- **8 lookup modes**: Auto (all), KMB/LWB, Citybus, NLB, Green Minibus, MTR Heavy Rail, Light Rail, MTR Bus
- **Real-time ETA**: Chinese stop names, ETA in minutes / clock time / remarks; 30-second auto-refresh and pull-to-refresh
- **📍 Nearby stops**: 300 / 500 / 1000 / 2000 m radius, sorted by distance; tap a stop to see ETA for **all** routes serving it
- **⭐ Favourites**: frequently used stops, including the `bound` direction
- **⚙ Route catalogue update**: manual / daily / every 3 days / weekly, Wi-Fi-only toggle, clear local data; 10 official catalogues written atomically, a failed source keeps the previous snapshot
- **🔄 In-app app update**: checks the latest GitHub Release, 24-hour throttle, installs via `FileProvider`
- **🔐 Signing consistency**: fixed development keystore, CI-pinned fingerprint, pre-install comparison of the installed app and downloaded APK certificates
- **🚌 Rainbow-bus icon**: launcher icons in 5 densities, dedicated round icon, Android 8+ adaptive icon, 512px store preview

### 📦 Install

1. Download **`hketa-v1.0.0-release.apk`** (signed production build) from this Release, or the `hketa-release-apk` / `hketa-debug-apk` artifact from the Actions run
2. On Android 8+, enable "Install unknown apps" for the first install
3. Overlay installs stay signature-consistent; **uninstall first** if the device previously had a build signed with a different key
4. Cross-check certificate, APK SHA-256 and version against the attached `SIGNING_FINGERPRINT.txt` and `VERSION_INFO.txt`

**Signing certificate SHA-256**
`FE:7B:35:E1:9D:90:F5:88:09:1B:08:1B:05:28:40:12:71:56:52:24:13:2D:9A:EB:1C:C0:53:A6:E6:20:02:57`

**Requirements**: Android 7.0+ (minSdk 24) | target / compile SDK 34 | no Google Play Services dependency

### 🔧 Tech

Java 17 data layer + Kotlin 1.9.24 UI/coroutines | AGP 8.5.2 | Gradle 8.7 | JDK 17 | official open data, no API key | one-shot location, no background tracking

### ⚠️ Known limitations

- **Live ETA is always fetched from the official API** — the catalogue updater never serves stale arrival times, and **ETA does not work offline**. Only route / stop / coordinate catalogues are cached offline.
- Red minibuses, trams, ferries, Discovery Bay and Ma Wan buses have no equivalent public real-time ETA API and are not included; NWFB has been merged into Citybus.
- If an MTR monthly CSV has no coordinate column, heavy rail / Light Rail / MTR Bus stops will not appear in the nearby list (route mode still works).
- "Auto (all)" queries several operators in parallel; official rate limits apply (HTTP 429) — avoid rapid polling.
- Denied permission / disabled location / timeout falls back to a Central, HK default position, clearly labelled in the status bar.
- In-app updates use `REQUEST_INSTALL_PACKAGES`; this project targets **GitHub sideload**, not the Play Store.
- Signature verification compares the **signing certificate**, not an APK file hash; `VERSION_INFO.txt` provides an APK SHA-256 for manual checking, but the app still does not verify the downloaded file's hash automatically.
- The adaptive icon uses the full artwork as its background: no parallax layer, and no Android 13 monochrome themed icon.
- Only the APP1933 layout and arrival format are reproduced; AI route planning, real-time occupancy, predicted alighting time, KMB monthly pass / club1933 / game room / bot1933 and Street View navigation are not implemented — no grey placeholder buttons either.
- Citybus has no bulk stop-list endpoint; stop names are cached on demand via /stop/{stop_id}. When the KMB bulk list returns HTTP 403 the app falls back to the single-stop API, so Chinese stop names still show and the catalogue updater marks optional sources as "skipped" rather than "failed".
- The Search tab is now an APP1933 full-screen keypad: red 搜尋 header, centred route-number field, custom number/letter keys, and live "route + 往 destination + operator attribution" rows. Tapping a result opens that direction's stop sequence; all original stop-ID / direction / filter controls remain.
- The Home "relocate" button is removed in favour of auto-locate. Launch / pull-to-refresh requests location permission first; while denied the app only shows a tappable consent hint and never queries nearby routes with the Central fallback coordinates.
- The Settings "Update application" section is now a dark version pill `v1.0.0 · 1　⟳ 🕐 ⧉` with re-check / history / copy shortcuts. Download, install and signature verification all remain in an "Advanced" drawer that expands automatically when a newer release is found.
- The Home "↻ refresh" button and radius Spinner are removed in favour of pull-to-refresh; "定位附近路線嘅距離" moves into Settings as a dark "50 ▼" big-number picker with 7 radii (50–2000 m), and returning Home re-locates automatically.
- The bottom "More" tab is renamed "App settings" and the bar becomes a floating liquid-glass navigation bar that follows system Dark mode / High contrast / Reduced motion (API 31+ backdrop blur); the dark rounded white-rim disclaimer card from the screenshot moves into the bottom of Settings.
- Tapping a Home nearby-route card opens a full-screen route detail: red header, stop timeline and the next three arrivals, reproducing the screenshot **without any map** (a plain XML route strip replaces Google Maps); fare / predicted alighting time honestly show "—" instead of invented $5.6 / 15:31.
- The dark empty area of the Search home now **preloads official route catalogues** (up to 24 rows, fetched in the background at app launch), so `12 往 海麗邨 ›` is visible before typing; tapping a search row opens the same "route detail (no map)" screen with its `O / I / 1` direction carried across.
- Added **ETA auto-refresh**: Home nearby cards, the selected stop / favourites and route detail all re-query the official API on a foreground timer; Settings has an ON-by-default switch plus a 15 / 30 / 45 / 60 s interval picker, and a status line shows the last automatic update. Foreground only, no re-location, no API call without data or location consent, pull-to-refresh retained.
- **Nav bar size & position changed to match the screenshot**: the 64dp near-full-width blue-grey bar becomes a 56dp-high, horizontally centred, 8dp-from-bottom dark charcoal full-pill capsule with screen-width-aware side insets (6% per side on phones, 18% on tablets / landscape, clamped 16–256dp); the selected tab is now a darker inner pill with a blue label, all three system appearances share the same geometry, and rotation re-centres it immediately.
- **Production APK matches its Release version**: every tag build verifies `tag ↔ app/build.gradle.kts ↔ actual APK badging` (plus `versionCode` / package / SDK levels) and refuses to publish on mismatch; the Release attaches exactly one signed `hketa-v1.0.0-release.apk` plus `VERSION_INFO.txt` / `SIGNING_FINGERPRINT.txt`.
- Arrival times come from official open data and are indicative only.

---

### 維護者備註 / Maintainer notes

- workflow 設 `generate_release_notes: false` 並以本檔（`RELEASE_NOTES_v1.0.0.md`）作為 `body_path`，所以 Release 內文就係上面嘅中英雙語說明；想要 GitHub 自動 "What's Changed" 可改回 `true`。
- 產生 Release：先 bump `app/build.gradle.kts` 嘅 `versionCode`／`versionName` 並 push，再 `git tag v1.0.1 && git push origin v1.0.1`；tag build 會跑版本對應閘門，通過後先附上正式版 APK、`VERSION_INFO.txt` 與 `SIGNING_FINGERPRINT.txt`，並上傳 `hketa-release-apk` Artifact。一般 push 只產出 debug Artifact。
- To publish: bump both `versionCode` and `versionName` in `app/build.gradle.kts`, push, then `git tag v1.0.1 && git push origin v1.0.1`. The tag build runs `RELEASE_VERSION_GATE` first; only after it passes does it attach the signed release APK, `VERSION_INFO.txt` and `SIGNING_FINGERPRINT.txt`, and upload the `hketa-release-apk` artifact. Regular pushes produce only the debug artifact.
- Current version: `versionCode = 1`, `versionName = 1.0.0`. The workflow now fails the release if the tag does not match these fields, so a stale APK can never be published under a new version tag.
