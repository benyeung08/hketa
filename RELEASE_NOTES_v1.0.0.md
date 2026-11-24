# HK ETA v1.0.0 · 香港實時到站 / Hong Kong real-time ETA

> 中英雙語、**收錄範圍：截至 2026-11-19（昨天）**，可直接貼入 GitHub Release 描述框。
> `v1.0.0` tag build 會自動用本檔作為該版本 Release 內文（`RELEASE_NOTES_${TAG}.md` → `body_path`），唔會誤用其它版本嘅說明。
> 本檔係純文字：**唔使改任何程式碼、唔使 push、唔使重跑 CI**，亦唔會改動已簽名嘅 APK。
>
> Bilingual, **scope: through 2026-11-19 (yesterday)**; paste-ready for the GitHub Release description box.
> The `v1.0.0` tag build selects this file automatically via `RELEASE_NOTES_${TAG}.md` (`body_path`), so it can never reuse another version's notes.
> Plain text only: **no code change, no push, no CI re-run**, and the signed APK is untouched.

---

## 中文（繁體）

**HK ETA** 是一個開源、免 API key 的香港實時巴士／鐵路到站查詢 App。資料直接取自運輸署及各營運商的公開開放數據；Java 負責 HTTPS／JSON／CSV 與時間換算，Kotlin 負責介面、協程並行與自動輪詢。

`v1.0.0` 係首版。本說明按**截至 2026-11-19（昨天）**嘅迭代整理；2026-11-20 嘅資料層／班次邏輯改動屬 `v1.0.1`，**刻意唔收錄喺本檔**。

### 🆕 收錄至 2026-11-19 嘅更新重點

- **🔍 搜尋頁還原 APP1933**：紅色「搜尋」標題、置中「輸入路線號碼」、自訂 3×4 數字／字母鍵盤（唔彈系統 IME）、即時列出「路線號＋往 目的地＋營運商細字」；開頁**預先載入官方目錄**（最多 24 條），點結果直接開詳情頁
- **🗺 主頁卡片 → 無地圖路線詳情**：紅色頂欄 `← 路線 往 目的地 (站號)`＋三個圓形動作、左側四個 chip、站序時間軸（紅點／灰圈／紅線）、上車站展開**未來三班次**；純 XML drawable，**冇地圖 SDK、冇圖磚、冇 Google 署名**
- **📍 先授權後自動定位**：移除「重新定位」掣；開 App／下拉刷新先攞定位權限，未授權只顯示可點嘅授權提示，**唔再靜默用中環座標**扮附近；拒絕／「不再詢問」有明確引導
- **⟳ 只留下拉刷新＋距離設定**：移除 `↻ 刷新` 掣同半徑 Spinner；「定位附近路線嘅距離」搬入設定頁做成深色 `50 ▼` 大數字選擇器（50／100／200／300／500／1000／2000 m），由設定返嚟自動用新半徑重載
- **◌ 懸浮液態玻璃導航欄**：「更多」改名「應用程式設置」；64dp 近滿寬 → **56dp 高、28dp 全圓角、水平置中嘅深灰黑藥丸**，並由貼底佔位列改成**真正浮喺內容上面嘅 overlay**（離底約 12%）；跟隨深色模式／高對比／減少動態，API 31+ 用背景模糊
- **⚙ 設定頁重整**：深色版本膠囊 `v1.0.0 · 1　⟳ 🕐 ⧉`（重新檢查／更新記錄／複製），下載安裝與驗簽收喺「進階設定」抽屜；加入「關於本 App」深藍描邊卡同「跟隨系統外觀」開關
- **⏱ ETA 自動刷新**：主頁附近卡、已選車站／收藏、詳情頁上車站共用同一前景輪詢；設定頁開關＋15／30／45／60 秒選擇器，`onResume` 補刷、`onPause` 停止，重用已快取嘅 6 個站唔重新定位
- **🖤 詳情頁文字修正**：站名同所有同色輔助文字統一純黑 `#000000`，頁底固定淺底，深色模式唔再出現白字落白底
- **📦 正式版 APK 與 Release 版本對應**：每個 tag build 校驗 `tag ↔ build.gradle.kts ↔ 實際 APK badging`（連 `versionCode`／package／SDK），唔夾即擋低發布；Release 只附一個 `hketa-v1.0.0-release.apk` 正式版，並附 `VERSION_INFO.txt`／`SIGNING_FINGERPRINT.txt`

### ✨ 完整功能

- **🚌 APP1933 版面**：九巴紅色標頭、主頁附近路線卡片（大字路線號＋往目的地＋站名連編號＋右側分鐘）、底部 主頁／收藏／搜尋／應用程式設置 四分頁
- **未來三班次**：`-` 剛到站、`—` 沒有服務、預定班次、改道標記
- **路線站序＋方向切換**：沿途車站連編號、⇄ 切去／回程、即時篩選站名／站號
- **8 種查詢模式**：自動（全部）、九巴／龍運、城巴、嶼巴、綠色小巴、港鐵重鐵、輕鐵、港鐵接駁巴士
- **實時到站**：中文站名、預計分鐘／預計時間／備註；前景自動刷新（間隔 15／30／45／60 秒可調，預設 30 秒）＋下拉刷新
- **📍 附近車站定位**：依設定半徑按距離排序，點站即睇該站全部路線 ETA
- **⭐ 收藏常用站**：連 `bound` 方向一併還原
- **⚙ 線路資料更新**：手動／每日／每 3 日／每星期、僅 Wi-Fi、清除本機資料；10 份官方目錄原子寫入，單一來源失敗保留上一版
- **🔄 更新應用程式**：讀取 GitHub 最新 Release、24 小時節流、下載後經 `FileProvider` 交系統安裝
- **🔐 簽名一致性**：固定開發金鑰、CI 指紋鎖定、安裝前比對已安裝 App 與新 APK 嘅簽章憑證
- **🚌 彩虹巴士圖示**：5 種密度 launcher icon、圓形遮罩、Android 8+ adaptive icon、512px 商店預覽圖

### 📦 安裝

1. 下載本 Release 的 **`hketa-v1.0.0-release.apk`**（正式版、已簽名；或 Actions run 的唯一 Artifact `hketa-release-apk`）
2. Android 8 或以上，首次安裝需開啟「安裝未知應用程式」
3. 覆蓋安裝會保持簽名一致；若裝置曾裝過**其他金鑰**簽的舊版，請先卸載
4. 可對照隨附的 `SIGNING_FINGERPRINT.txt` 同 `VERSION_INFO.txt` 核對憑證、APK SHA-256 與版本

**簽章憑證 SHA-256**
`FE:7B:35:E1:9D:90:F5:88:09:1B:08:1B:05:28:40:12:71:56:52:24:13:2D:9A:EB:1C:C0:53:A6:E6:20:02:57`

**需求**：Android 7.0+（minSdk 24）｜target / compile SDK 34｜不依賴 Google Play Services

### 🔧 技術

Java 17 資料層 + Kotlin 1.9.24 UI／協程｜AGP 8.5.2｜Gradle 8.7｜JDK 17｜官方開放數據，免 API key｜單次定位、不背景追蹤｜`versionCode = 1`／`versionName = 1.0.0`

### ⚠️ 已知限制

- **實時 ETA 永遠即時向官方 API 查詢**，不會因「線路資料更新」而變舊，亦**不能離線查 ETA**；離線只可讀已快取的路線／站名／座標目錄
- ETA 自動刷新只喺**前景**：鎖屏或切去第個 App 期間唔更新，返嚟補一次；官方有 429 速率限制，最密 15 秒
- 紅色小巴、電車、渡輪、愉景灣及馬灣巴士目前沒有同類公開實時 ETA API，未納入；新巴已併入城巴
- 若港鐵月度 CSV 沒有座標欄，附近站列表不會顯示重鐵／輕鐵／港鐵接駁巴士（路線模式仍可查詢）
- 綠小巴官方只有 `/stop/{stop_id}` 單站端點；全量站點係選用來源，失敗會自動略過，附近站覆蓋可能只限查過嘅站
- 城巴官方沒有全量站點端點；站名改由 `/stop/{stop_id}` 按需快取，九巴全量站點被 403 擋亦自動退返單站 API
- 公開 ETA API **冇車資、冇預計落車時間、冇地圖路線圖**，詳情頁如實顯示 `車資 —`／`落車 —`，唔捏造 $5.6／15:31
- 未獲定位權限時**唔會查附近路線、唔會退回中環預設座標**；只顯示可點嘅授權提示
- 驗簽比對的是**簽章憑證**，不是 APK 檔案雜湊；`VERSION_INFO.txt` 提供 APK SHA-256 供人手核對，但 App 下載後仍未自動校驗檔案雜湊
- adaptive icon 以整張圖作為背景，沒有視差層，亦未提供 Android 13 單色 themed icon
- 只還原 APP1933 版面與到站格式；AI 智能問路、實時載客量、預計落車時間、九巴月票／club1933／遊戲室／bot1933、街景步行導航等專有功能不做，亦沒有灰色佔位按鈕
- App 內更新使用 `REQUEST_INSTALL_PACKAGES`，定位為 GitHub sideload，**不是 Play 商店上架版本**
- 到站時間來自官方開放數據，僅供參考

---

## English

**HK ETA** is an open-source, API-key-free real-time Hong Kong bus / rail arrival app. Data comes directly from public open data published by the Transport Department, the bus operators and the MTR; Java handles HTTPS / JSON / CSV parsing and time conversion, while Kotlin handles the UI, coroutine parallelism and auto-refresh polling.

`v1.0.0` is the first release. These notes are compiled **through 2026-11-19 (yesterday)**; the data-layer / trip-logic work of 2026-11-20 belongs to `v1.0.1` and is **deliberately excluded here**.

### 🆕 Highlights through 2026-11-19

- **🔍 Search restored to APP1933**: red 搜尋 header, centred route-number field, custom 3×4 number / letter keypad (no system IME), live "route + 往 destination + operator attribution" rows; the page **preloads official catalogues** (up to 24 rows) on open, and tapping a result opens route detail directly
- **🗺 Home card → map-free route detail**: red header `← route 往 destination (stop id)` + three round actions, four left-hand chips, a stop timeline (red dots / grey rings / red line), and the **next three arrivals** expanded at the boarding stop; pure XML drawables — **no map SDK, no tiles, no Google attribution**
- **📍 Permission first, then auto-locate**: the "relocate" button is gone; launch / pull-to-refresh requests location permission first, and while denied the app shows only a tappable consent hint — **it no longer silently pretends to be nearby using a Central, HK coordinate**; denial / "don't ask again" have explicit guidance
- **⟳ Pull-to-refresh only + radius setting**: the `↻ refresh` button and radius Spinner are removed; "nearby route radius" moves into Settings as a dark `50 ▼` big-number picker (50 / 100 / 200 / 300 / 500 / 1000 / 2000 m), and returning from Settings reloads with the new radius automatically
- **◌ Floating liquid-glass navigation bar**: "More" is renamed "App settings"; the 64dp near-full-width bar becomes a **56dp-high, 28dp fully-rounded, horizontally centred dark charcoal pill**, and changes from a bottom-anchored placeholder row to a **real overlay floating above content** (~12% from the bottom); it follows Dark mode / High contrast / Reduced motion, with background blur on API 31+
- **⚙ Settings reorganised**: dark version pill `v1.0.0 · 1　⟳ 🕐 ⧉` (re-check / history / copy), with download, install and signature verification inside an "Advanced settings" drawer; a dark-blue outlined "About this app" card and a "Follow system appearance" switch are added
- **⏱ ETA auto-refresh**: Home nearby cards, the selected stop / favourites, and the route-detail boarding stop share one foreground timer; Settings has an ON/OFF switch + 15 / 30 / 45 / 60-second picker, `onResume` catches up, `onPause` stops, and the 6 cached stops are reused without re-locating
- **🖤 Route-detail text fix**: stop names and every same-colour auxiliary label are uniformly pure black `#000000` with a fixed light footer, so Dark mode can no longer produce white-on-white text
- **📦 Production APK matches its Release version**: every tag build verifies `tag ↔ build.gradle.kts ↔ actual APK badging` (plus `versionCode` / package / SDK) and refuses to publish on mismatch; the Release attaches exactly one signed `hketa-v1.0.0-release.apk` plus `VERSION_INFO.txt` / `SIGNING_FINGERPRINT.txt`

### ✨ Full feature set

- **🚌 APP1933 layout**: KMB-red header, nearby-route cards (large route no. + destination + stop with id + big minutes), Home / Favourites / Search / App settings bottom tabs
- **Next three arrivals**: markers for `-` (just arrived), `—` (no service), scheduled trips and diversions
- **Stop sequence + direction switch**: stops with ids, ⇄ inbound / outbound toggle, live stop-name / stop-id filter
- **8 lookup modes**: Auto (all), KMB / LWB, Citybus, NLB, Green Minibus, MTR Heavy Rail, Light Rail, MTR Bus
- **Real-time ETA**: Chinese stop names, minutes / clock time / remarks; foreground auto-refresh (15 / 30 / 45 / 60 s, default 30 s) plus pull-to-refresh
- **📍 Nearby stops**: sorted by distance using the configured radius; tap a stop to see ETA for **all** routes serving it
- **⭐ Favourites**: frequently used stops, including the `bound` direction
- **⚙ Route catalogue update**: manual / daily / every 3 days / weekly, Wi-Fi-only, clear local data; 10 official catalogues written atomically, a failed source keeps the previous snapshot
- **🔄 In-app app update**: checks the latest GitHub Release, 24-hour throttle, installs via `FileProvider`
- **🔐 Signing consistency**: fixed development keystore, CI-pinned fingerprint, pre-install comparison of the installed app and downloaded APK certificates
- **🚌 Rainbow-bus icon**: launcher icons in 5 densities, dedicated round icon, Android 8+ adaptive icon, 512px store preview

### 📦 Install

1. Download **`hketa-v1.0.0-release.apk`** (signed production build) from this Release, or the single `hketa-release-apk` Artifact of an Actions run
2. On Android 8+, enable "Install unknown apps" for the first install
3. Overlay installs stay signature-consistent; **uninstall first** if the device previously had a build signed with a different key
4. Cross-check the certificate, APK SHA-256 and version against the attached `SIGNING_FINGERPRINT.txt` and `VERSION_INFO.txt`

**Signing certificate SHA-256**
`FE:7B:35:E1:9D:90:F5:88:09:1B:08:1B:05:28:40:12:71:56:52:24:13:2D:9A:EB:1C:C0:53:A6:E6:20:02:57`

**Requirements**: Android 7.0+ (minSdk 24) | target / compile SDK 34 | no Google Play Services dependency

### 🔧 Tech

Java 17 data layer + Kotlin 1.9.24 UI / coroutines | AGP 8.5.2 | Gradle 8.7 | JDK 17 | official open data, no API key | one-shot location, no background tracking | `versionCode = 1` / `versionName = 1.0.0`

### ⚠️ Known limitations

- **Live ETA is always fetched from the official API** — the catalogue updater never serves stale arrival times, and **ETA does not work offline**; offline use is limited to cached route / stop / coordinate catalogues
- ETA auto-refresh runs **in the foreground only**: it does not update while the screen is locked or another app is active, and catches up on return; official rate limits apply (HTTP 429), 15 seconds is the densest interval
- Red minibuses, trams, ferries, Discovery Bay and Ma Wan buses have no equivalent public real-time ETA API and are not included; NWFB has been merged into Citybus
- If an MTR monthly CSV has no coordinate column, heavy rail / Light Rail / MTR Bus stops will not appear in the nearby list (route mode still works)
- GMB officially exposes only `/stop/{stop_id}` per stop; the bulk stop list is an optional source that is skipped on failure, so nearby coverage may be limited to stops already queried
- Citybus has no bulk stop-list endpoint; names are cached on demand via `/stop/{stop_id}`, and a 403 on the KMB bulk list automatically falls back to the single-stop API
- The public ETA APIs carry **no fare, no predicted alighting time and no map route**, so the detail page honestly shows `車資 —` / `落車 —` instead of inventing $5.6 / 15:31
- Without location permission the app **does not query nearby routes and does not fall back to a Central, HK coordinate**; it shows only a tappable consent hint
- Signature verification compares the **signing certificate**, not an APK file hash; `VERSION_INFO.txt` provides an APK SHA-256 for manual checking, but the app still does not auto-verify the downloaded file hash
- The adaptive icon uses the full artwork as its background: no parallax layer, and no Android 13 monochrome themed icon
- Only the APP1933 layout and arrival format are reproduced; AI route planning, real-time occupancy, predicted alighting time, KMB monthly pass / club1933 / game room / bot1933, street-view walking navigation and similar proprietary features are not implemented — and there are no fake greyed-out buttons
- In-app updates use `REQUEST_INSTALL_PACKAGES`; this project targets **GitHub sideload**, not the Play Store
- Arrival times come from official open data and are indicative only

---

### 維護者備註 / Maintainer notes

- 本檔係**已發佈 `v1.0.0`** 嘅 Release 內文；更新純文字，**唔會改 APK、唔會改 tag、唔使重跑 CI**。workflow 設 `generate_release_notes: false`，以 `RELEASE_NOTES_${TAG}.md` 對應版本。
- `v1.0.0` tag 當初嘅 source 係早期版本；若用 `Actions → Run workflow` 嘅 backfill 補正式版 APK，job 會用該 tag 自己嘅 source 構建，**唔會用 main 嘅新 code 扮舊版本**。想實際攞到本說明所列全部功能，正確做法係出新版本：`app/build.gradle.kts` 已 bump 到 `versionCode = 2`／`versionName = 1.0.1`。
- 2026-11-20 嘅聯營路線／站名規則／綠小巴循環線班次／已發佈 Release backfill／CI 編譯修正，一律收錄喺 `RELEASE_NOTES_v1.0.1.md`（另有 `_day1_2026-11-19.md`／`_day2_2026-11-20.md` 兩份單日檔），本檔刻意唔收。
- This file is the published `v1.0.0` Release body; editing it changes **text only** — not the APK, not the tag, and no CI re-run is needed. The workflow sets `generate_release_notes: false` and selects `RELEASE_NOTES_${TAG}.md` per tag.
- The `v1.0.0` tag points at early source. The `Actions → Run workflow` backfill job builds from **that tag's own source**, never from newer `main` code pretending to be the old version. To actually ship every feature listed here, publish a new version: `app/build.gradle.kts` is already bumped to `versionCode = 2` / `versionName = 1.0.1`.
- The 2026-11-20 work (joint routes, stop-name rules, the GMB circular trip fix, published-Release backfill and CI / compile fixes) lives in `RELEASE_NOTES_v1.0.1.md` (plus the `_day1_2026-11-19.md` / `_day2_2026-11-20.md` standalone notes) and is deliberately excluded here.
