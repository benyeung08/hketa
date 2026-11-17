# HK ETA · 香港實時到站 / Hong Kong Real-time ETA

**語言 / Language：** [中文（繁體）](#zh) · [English](#en)

> 開源、免 API key 的香港巴士與鐵路實時到站 App。資料直接取自官方開放數據。
> Open-source, API-key-free real-time arrival app for Hong Kong buses and rail, built on official open data.

---

<a id="zh"></a>

## 中文（繁體）

### 簡介

**HK ETA** 用 **Java 17**（HTTPS／JSON／CSV 解析、時間換算）加 **Kotlin 1.9.24**（介面、協程並行、自動輪詢）寫成，直接串運輸署、各巴士營運商同港鐵嘅公開實時到站 API，**唔需要申請 API key**，亦**唔依賴 Google Play Services**。

介面由 `v1.0.0` 起採用 **APP1933 風格**：九巴紅色標頭、主頁附近路線卡片、底部四分頁、每條路線顯示未來三班次。**資料層完全冇改**——八種營運商、線路資料更新、App 內更新同簽名一致性全部保留。

版本 `v1.0.0`（`versionCode = 1`）｜Android 7.0+（minSdk 24）｜target / compile SDK 34。

### ✨ 功能

- **🚌 APP1933 版面**：大字路線號＋「往 目的地」＋站名連編號＋右側大數字分鐘；底部 **主頁／收藏／搜尋／更多** 四分頁
- **未來三班次**：標記 `-`（剛到站）、`—`（沒有服務）、「預定班次」、「改道」、聯營公司；取代舊嘅單班次列表
- **路線站序＋方向切換**：輸入路線號列出沿途車站（含站編號），一鍵 `⇄` 切換去程／回程，並可即時篩選站名／站號
- **8 種查詢模式**：自動（全部）、九巴／龍運、城巴、嶼巴、綠色小巴、港鐵重鐵、輕鐵、港鐵接駁巴士
- **實時到站**：中文站名、預計分鐘／預計時間／備註，每 30 秒自動更新、下拉刷新
- **📍 附近車站定位**：300／500／1000／2000 公尺半徑，按距離排序，點站即睇該站全部路線 ETA
- **⭐ 收藏常用站**：連 `bound` 方向一併還原
- **⚙️ 線路資料更新**：手動／每日／每 3 日／每星期、僅 Wi-Fi、清除本機資料；10 份官方目錄原子寫入，單一來源失敗保留上一版
- **🔄 更新應用程式**：讀取 GitHub 最新 Release、24 小時節流、下載後經 `FileProvider` 交系統安裝
- **🔐 簽名一致性**：固定開發金鑰、CI 指紋鎖定、安裝前比對簽章憑證
- **🚌 彩虹巴士圖示**：5 種密度 launcher icon、專屬圓形圖示、Android 8+ adaptive icon、512px 商店預覽圖

### 📦 安裝

1. 由 [Releases](https://github.com/benyeung08/hketa/releases) 下載 `hketa-v1.0.0.apk`，或由該次 Actions run 下載 Artifact `hketa-debug-apk`（入面係 `app-debug.apk`）
2. Android 8+ 首次安裝需開啟「安裝未知應用程式」
3. 覆蓋安裝會保持簽名一致；若裝置曾裝過**其他金鑰**簽嘅版本，請先卸載
4. 可對照隨附嘅 `SIGNING_FINGERPRINT.txt` 核對憑證

**簽章憑證 SHA-256**

```
FE:7B:35:E1:9D:90:F5:88:09:1B:08:1B:05:28:40:12:71:56:52:24:13:2D:9A:EB:1C:C0:53:A6:E6:20:02:57
```

### 🛠 由原始碼建置

需要 **JDK 17**、**Android SDK 34 / Build Tools 34.0.0**、**Gradle 8.7**。

```bash
gradle assembleDebug --no-daemon
# 輸出：app/build/outputs/apk/debug/app-debug.apk
```

> ⚠️ Repo **刻意唔提交 `gradle-wrapper.jar`**，所以 `./gradlew` 會失敗；本機請用已安裝嘅 Gradle 8.7，或自行補回 wrapper jar。CI 直接下載官方 Gradle 8.7 發行版。

推送到 `main` 後，`.github/workflows/build-apk.yml` 會自動打包並上傳 Artifact `hketa-debug-apk`。打 tag 先會附加簽名 Release APK 同 `SIGNING_FINGERPRINT.txt`：

```bash
git tag v1.0.0 && git push origin v1.0.0
```

出新版前請先 bump `app/build.gradle.kts` 嘅 `versionCode` 同 `versionName`，App 內更新先偵測到。

### ⚙️ 設定頁

⚙ 設定而家收喺底部 **「更多」** 分頁，入面仍然係三個獨立區塊：**線路資料更新**（只快取目錄，ETA 永遠即時查）、**更新應用程式**（內建 repo `benyeung08/hketa`）、**App 簽名一致性**（顯示本機指紋與驗證結果）。

### 🚌 APP1933 版面對照

| 分頁 | 還原咩 | 底層 |
| --- | --- | --- |
| **主頁** | 附近路線卡片：`路線號`／`往 目的地`／`站名 (KT123)`／右側大數字分鐘；每張卡三個班次 chip | `EtaRepository.nearbyStops()` ＋ `stopEta()` 並行查頭 6 個站 |
| **收藏** | 收藏車站列表，點選查看、長按移除 | `Favorites` |
| **搜尋** | 路線號＋8 模式、沿途站序（含站編號）、`⇄ 轉換路線方向`、站名／站號即時篩選、已知站號直接查 | `EtaRepository.stops()` / `etaFor()` |
| **更多** | ⚙ 設定入口＋「不包含專有功能」聲明 | `SettingsActivity` |

**只換介面，唔換資料**：`App1933Format.kt` 係純格式化層，把同一份 `EtaItem` 依 `(company, route, stop, dest)` 分組、取最早三班；`CardAdapters.kt` 只負責畫卡片。全部 8 個營運商、10 份官方目錄、`RouteDataStore`、`ApkSignature` 一行都冇改。

**明確不做**（需九巴私有 API／會員帳號／Play Services）：AI 智能問路、實時載客量、預計落車時間、九巴月票、club1933 會員、遊戲室、bot1933 實時對話、街景／步行導航、寵物巴士、廣告。**冇灰色佔位按鈕、冇假數據**。

### 🔐 簽名一致性（三層）

| 層 | 做法 |
| --- | --- |
| Build | 提交 `app/hketa-debug.keystore`，所有 debug 同無 Secrets 嘅 release 都用同一張憑證 |
| CI | tag build 用 `apksigner verify --print-certs` 對照 `expected_signing_sha256`，唔夾即 fail |
| App | 安裝前比對下載 APK 同已安裝 App 嘅簽章憑證 SHA-256，唔一致即刪除並拒絕安裝 |

正式發布可用 Secrets 注入 `app/hketa-release.keystore`；換金鑰要同步更新指紋，舊裝置需卸載一次或走 Android 簽章輪替。

### 📁 專案結構

```
hketa/
├── .github/workflows/build-apk.yml   # CI：預檢、打包、簽章驗證、Release
├── app/
│   ├── build.gradle.kts              # AGP 8.5.2 / Kotlin 1.9.24 / signingConfigs
│   ├── hketa-debug.keystore          # 固定開發金鑰（公開、僅供 sideload）
│   ├── icon-source.png               # 圖示原始圖
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/hk/eta/app/api/      # Java：HttpJson, EtaRepository, MtrCatalog,
│       │                            #       RouteDataStore, GeoStops, AppUpdate …
│       ├── java/hk/eta/app/ui/       # Kotlin：MainActivity, SettingsActivity,
│       │                            #   App1933Format, CardAdapters, Adapters,
│       │                            #   LocationHelper, ApkSignature …
│       └── res/                      # layout / values / mipmap-* / xml
│           └── layout/               # activity_main (4 tabs), item_route_card,
│                                     # item_favorite, item_stop …
├── scripts/
│   ├── preflight.py                  # 本機上傳完整性與二進位檔預檢
│   ├── fix_drift.py                  # 一鍵修正 3 個常見舊檔（冪等）
│   ├── show_build_error.py           # 由 Gradle log 抽出真正錯誤原因
│   ├── check_api_fields.py           # 官方欄位／版本／資源靜態檢查
│   ├── make_launcher_icons.py        # 由 icon-source.png 產生全部密度圖示
│   └── pin_signing_fingerprint.py    # 把 keystore 指紋寫入 strings.xml
├── LICENSE                           # MIT
├── README.md                         # 本檔（中英雙語）
└── RELEASE_NOTES_v1.0.0.md           # v1.0.0 Release 中英雙語說明
```

### 🧪 CI 與疑難排解

CI 第一步係**自包含預檢**（唔依賴 repo 入面任何 `.py`），會檢查缺檔、多咗一層資料夾、檔案存在但內容仍係舊版，以及 keystore／PNG 嘅二進位格式。

| Log 見到 | 動作 |
| --- | --- |
| `檔案存在但內容仍係舊版：…` | **全量同步**：`git add -A && git commit && git push`（`-A` 會清走舊檔）；或跑 `python3 scripts/fix_drift.py` |
| `Unresolved reference: await` / `overrides nothing` | 已修於 `LocationHelper.kt`（刪走唔存在嘅 coroutines import、修正 `LocationListener` nullability） |
| `String[] cannot be converted to List<String>` | 已修於 `MtrCatalog.java`（加 `lower(String[])` 多載） |
| `activity_main.xml 仍係舊單頁版面（缺 APP1933 四分頁）` | 由完整 zip 覆蓋 `activity_main.xml`，並一齊傳 `App1933Format.kt`／`CardAdapters.kt`／兩個新 layout／兩個 drawable |
| `Could not resolve` / `Read timed out` / 5xx | 直接 **Re-run jobs** |
| 仍然係 `at org.gradle.internal...` 收尾 | 新 workflow 已移除 `--stacktrace`；代表 `.github/workflows/build-apk.yml` 未上傳 |

失敗時會多一個 `Show real Gradle error` step（用 `::error::` 印出真正錯誤＋針對性建議）同 Artifact `hketa-gradle-logs`。

### ⚠️ 已知限制

- **實時 ETA 永遠即時向官方 API 查**，唔會因「線路資料更新」變舊，亦**唔可以離線查 ETA**；離線只可讀已快取嘅路線／站名／座標目錄
- 紅色小巴、電車、渡輪、愉景灣及馬灣巴士冇同類公開實時 ETA API，未納入；新巴已併入城巴
- 港鐵月度 CSV 若冇座標欄，附近站列表唔會顯示重鐵／輕鐵／港鐵接駁巴士（路線模式仍可查）
- 「自動（全部）」同時打多間營運商，官方有速率限制（HTTP 429），請避免密集輪詢
- 拒絕定位／冇定位服務／逾時會退回中環預設位置，並喺狀態列標示；只攞一次定位，唔背景追蹤
- App 內更新用 `REQUEST_INSTALL_PACKAGES`，定位係 **GitHub sideload**，唔係 Play 上架版本
- 驗簽比對嘅係**簽章憑證**而唔係 APK 檔案雜湊；目前未做 APK asset SHA-256 校驗
- adaptive icon 以整張圖做背景，冇視差層，亦未提供 Android 13 單色 themed icon
- **只還原 APP1933 嘅版面與到站格式**：AI 智能問路、實時載客量、預計落車時間、月票／會員／遊戲室／bot1933、街景步行導航等專有功能全部唔做；主頁附近站 ETA 係由公開站級 API 即時查，唔係官方嘅大數據預測
- 到站時間以官方開放數據為準，僅供參考

### 🔗 相關

- Repo：https://github.com/benyeung08/hketa
- Release 說明：[RELEASE_NOTES_v1.0.0.md](RELEASE_NOTES_v1.0.0.md)
- 資料來源：[DATA.GOV.HK](https://data.gov.hk) 及各營運商開放數據、[MTR Open Data](https://opendata.mtr.com.hk)

### 📄 授權與聲明

[MIT](LICENSE)。本專案係非官方作品，同各營運商及政府部門無關；到站資料版權屬原提供者所有。

> 本 README 純文件，改動**唔影響 CI 或 APK 打包**。

---

<a id="en"></a>

## English

### Overview

**HK ETA** is written in **Java 17** (HTTPS / JSON / CSV parsing, time conversion) and **Kotlin 1.9.24** (UI, coroutines, auto-refresh). It talks directly to the public real-time arrival APIs of the Transport Department, bus operators and MTR — **no API key required** and **no Google Play Services dependency**.

Since `v1.0.0` the shell follows the **APP1933** layout: KMB-red header, nearby-route cards, four bottom tabs, and the next three arrivals per route. **The data layer is untouched** — all eight operators, the catalogue updater, the in-app updater and signature consistency remain.

Version `v1.0.0` (`versionCode = 1`) | Android 7.0+ (minSdk 24) | target / compile SDK 34.

### ✨ Features

- **🚌 APP1933 layout**: large route number + "往 destination" + stop name with id + big minutes on the right; **Home / Favourites / Search / More** bottom tabs
- **Next three arrivals**: markers for `-` (just arrived), `—` (no service), "預定班次" (scheduled), diversion and joint operators — replacing the old single-arrival list
- **Stop sequence + direction switch**: route number lists all stops (with stop ids), one-tap `⇄` bound toggle, live stop-name / stop-id filter
- **8 lookup modes**: Auto (all), KMB/LWB, Citybus, NLB, Green Minibus, MTR Heavy Rail, Light Rail, MTR Bus
- **Real-time ETA**: Chinese stop names, ETA in minutes / clock time / remarks, 30-second auto-refresh and pull-to-refresh
- **📍 Nearby stops**: 300 / 500 / 1000 / 2000 m radius, sorted by distance; tap a stop to see ETA for **all** routes serving it
- **⭐ Favourites**: frequently used stops, including the `bound` direction
- **⚙️ Route catalogue update**: manual / daily / every 3 days / weekly, Wi-Fi-only toggle, clear local data; 10 official catalogues written atomically, a failed source keeps the previous snapshot
- **🔄 In-app app update**: checks the latest GitHub Release, 24-hour throttle, installs via `FileProvider`
- **🔐 Signing consistency**: fixed development keystore, CI-pinned fingerprint, pre-install certificate comparison
- **🚌 Rainbow-bus icon**: launcher icons in 5 densities, dedicated round icon, Android 8+ adaptive icon, 512px store preview

### 📦 Install

1. Download `hketa-v1.0.0.apk` from [Releases](https://github.com/benyeung08/hketa/releases), or the `hketa-debug-apk` artifact (`app-debug.apk`) from an Actions run
2. On Android 8+, enable "Install unknown apps" for the first install
3. Overlay installs stay signature-consistent; **uninstall first** if the device previously had a build signed with a different key
4. Cross-check the certificate against the attached `SIGNING_FINGERPRINT.txt`

**Signing certificate SHA-256**

```
FE:7B:35:E1:9D:90:F5:88:09:1B:08:1B:05:28:40:12:71:56:52:24:13:2D:9A:EB:1C:C0:53:A6:E6:20:02:57
```

### 🛠 Build from source

Requires **JDK 17**, **Android SDK 34 / Build Tools 34.0.0**, **Gradle 8.7**.

```bash
gradle assembleDebug --no-daemon
# Output: app/build/outputs/apk/debug/app-debug.apk
```

> ⚠️ The repo **deliberately does not ship `gradle-wrapper.jar`**, so `./gradlew` will fail. Use an installed Gradle 8.7 locally (or restore the wrapper jar). CI downloads the official Gradle 8.7 distribution instead.

Pushing to `main` triggers `.github/workflows/build-apk.yml`, which builds and uploads the `hketa-debug-apk` artifact. Only tag builds attach the signed Release APK and `SIGNING_FINGERPRINT.txt`:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Before the next release, bump **both** `versionCode` and `versionName` in `app/build.gradle.kts` so the in-app updater detects it.

### ⚙️ Settings

The ⚙ entry now lives in the bottom **More** tab. It still opens the same three independent sections: **Route catalogue update** (caches catalogues only — ETA is always live), **Update application** (repo `benyeung08/hketa` is built in), and **App signing consistency** (shows the local fingerprint and verification result).

### 🚌 APP1933 layout mapping

| Tab | What is reproduced | Under the hood |
| --- | --- | --- |
| **Home** | Nearby route cards: `route` / `往 destination` / `stop (KT123)` / big minutes; three arrival chips per card | `EtaRepository.nearbyStops()` + parallel `stopEta()` for the nearest 6 stops |
| **Favourites** | Saved stops, tap to open, long-press to remove | `Favorites` |
| **Search** | Route number + 8 modes, stop sequence with ids, `⇄` direction toggle, live stop filter, direct stop-id lookup | `EtaRepository.stops()` / `etaFor()` |
| **More** | ⚙ Settings entry + "proprietary features not included" notice | `SettingsActivity` |

**UI only, data unchanged**: `App1933Format.kt` is a pure formatting layer that groups the same `EtaItem` list by `(company, route, stop, dest)` and keeps the three earliest arrivals; `CardAdapters.kt` only draws the cards. All 8 operators, the 10 official catalogues, `RouteDataStore` and `ApkSignature` are untouched.

**Deliberately NOT implemented** (they need KMB private APIs / membership accounts / Play Services): AI route planning, real-time occupancy, predicted alighting time, KMB monthly pass, club1933 membership, game room, bot1933 chat, Street View / walking navigation, pet bus and ads. **No grey placeholder buttons, no fake data.**

### 🔐 Signing consistency (three layers)

| Layer | Mechanism |
| --- | --- |
| Build | Committed `app/hketa-debug.keystore`; every debug build and any release without Secrets uses the same certificate |
| CI | Tag builds run `apksigner verify --print-certs` against `expected_signing_sha256`; a mismatch fails the release |
| App | Before install, compares the downloaded APK and installed app certificate SHA-256; a mismatch deletes the APK and refuses to install |

For production, inject `app/hketa-release.keystore` via Secrets. Rotating the key requires updating the pinned fingerprint too, and existing devices must uninstall once (or use Android signing certificate lineage).

### 📁 Project structure

```
hketa/
├── .github/workflows/build-apk.yml   # CI: preflight, build, signing verify, Release
├── app/
│   ├── build.gradle.kts              # AGP 8.5.2 / Kotlin 1.9.24 / signingConfigs
│   ├── hketa-debug.keystore          # fixed dev key (public, sideload only)
│   ├── icon-source.png               # launcher artwork source
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/hk/eta/app/api/      # Java: HttpJson, EtaRepository, MtrCatalog,
│       │                            #       RouteDataStore, GeoStops, AppUpdate …
│       ├── java/hk/eta/app/ui/       # Kotlin: MainActivity, SettingsActivity,
│       │                            #       LocationHelper, ApkSignature …
│       └── res/                      # layout / values / mipmap-* / xml
├── scripts/
│   ├── preflight.py                  # local upload + binary integrity check
│   ├── fix_drift.py                  # one-shot repair of 3 stale files (idempotent)
│   ├── show_build_error.py           # distils the real error from Gradle logs
│   ├── check_api_fields.py           # official fields / versions / resources checks
│   ├── make_launcher_icons.py        # generates all icon densities
│   └── pin_signing_fingerprint.py    # writes the keystore fingerprint into strings.xml
├── LICENSE                           # MIT
├── README.md                         # this file (bilingual)
└── RELEASE_NOTES_v1.0.0.md           # v1.0.0 bilingual release notes
```

### 🧪 CI & troubleshooting

The first CI step is a **self-contained preflight** (it needs no repo `.py` file). It checks missing files, an extra nesting folder, files that exist but still contain old content, and binary signatures of the keystore / PNGs.

| Log shows | Action |
| --- | --- |
| `檔案存在但內容仍係舊版：…` (stale content) | **Full sync**: `git add -A && git commit && git push` (`-A` also removes stale files); or run `python3 scripts/fix_drift.py` |
| `Unresolved reference: await` / `overrides nothing` | Fixed in `LocationHelper.kt` (removed a non-existent coroutines import, corrected `LocationListener` nullability) |
| `String[] cannot be converted to List<String>` | Fixed in `MtrCatalog.java` (added a `lower(String[])` overload) |
| `activity_main.xml 仍係舊單頁版面（缺 APP1933 四分頁）` (stale single-page layout) | Overwrite `activity_main.xml` from the full zip, together with `App1933Format.kt`, `CardAdapters.kt`, the two new layouts and two drawables |
| `Could not resolve` / `Read timed out` / 5xx | Simply **Re-run jobs** |
| Still ends with `at org.gradle.internal...` | The new workflow removed `--stacktrace`; this means `.github/workflows/build-apk.yml` was not uploaded |

On failure, an extra `Show real Gradle error` step prints the real cause as `::error::` annotations with a targeted hint, and the full log is uploaded as the `hketa-gradle-logs` artifact.

### ⚠️ Known limitations

- **Live ETA is always fetched from the official API** — the catalogue updater never serves stale arrival times, and **ETA does not work offline**. Only route / stop / coordinate catalogues are cached.
- Red minibuses, trams, ferries, Discovery Bay and Ma Wan buses have no equivalent public real-time ETA API and are not included; NWFB has been merged into Citybus.
- If an MTR monthly CSV has no coordinate column, heavy rail / Light Rail / MTR Bus stops will not appear in the nearby list (route mode still works).
- "Auto (all)" queries several operators in parallel; official rate limits apply (HTTP 429) — avoid rapid polling.
- Denied permission / disabled location / timeout falls back to a Central, HK default position, clearly labelled in the status bar; one-shot location only, no background tracking.
- In-app updates use `REQUEST_INSTALL_PACKAGES`; this project targets **GitHub sideload**, not the Play Store.
- Signature verification compares the **signing certificate**, not an APK file hash; APK asset SHA-256 digest checking is not implemented.
- The adaptive icon uses the full artwork as its background: no parallax layer, and no Android 13 monochrome themed icon.
- **Only the APP1933 layout and arrival format are reproduced**: AI route planning, real-time occupancy, predicted alighting time, monthly pass / membership / game room / bot1933, Street View walking navigation and other proprietary features are not implemented. Home-page nearby ETAs come from public stop-level APIs, not KMB's proprietary prediction engine.
- Arrival times come from official open data and are indicative only.

### 🔗 Related

- Repo: https://github.com/benyeung08/hketa
- Release notes: [RELEASE_NOTES_v1.0.0.md](RELEASE_NOTES_v1.0.0.md)
- Data sources: [DATA.GOV.HK](https://data.gov.hk), operator open-data feeds, [MTR Open Data](https://opendata.mtr.com.hk)

### 📄 License & disclaimer

[MIT](LICENSE). This is an unofficial project, not affiliated with any operator or government department. Arrival data remains the property of its original providers.

> This README is documentation only; editing it does **not** affect CI or the APK build.
