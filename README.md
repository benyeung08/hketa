# HKETA · 正式版（signed release APK）

**繁體中文** · [English](README.en.md)

以 **GitHub + JavaScript + TypeScript + Kotlin + 開放 API** 重做 `shamough1792/HKETA` 的香港公共交通到站預報，交付 **v1.0.0 正式版專案**：只產出**已簽名的 release APK**，不含 debug 流程。

資料涵蓋 KMB／LWB、Citybus、NLB、專線小巴、港鐵、輕鐵；UI 繁中／EN 雙語，附站點地圖與螢幕常亮。**「設定」頁內建線上應用更新**：向 GitHub Releases 檢查新版本、下載 APK、顯示進度並交由系統安裝。

原專案是 Cordova + JavaScript（MIT）；本專案同樣 MIT，但**不複製原專案 `www/` 內容**，UI 與 API 層重新撰寫。

> ⚠️ **簽名檔是你的，不是我能生成的。** 這裡提供的是「設定好就能出正式版」的專案；必須由你產生 keystore、把 4 個值放進 GitHub Secrets，Actions 才會編出 `HKETA-release.apk`。本回合沒有 Android SDK／Gradle，也沒有你的私鑰，因此**沒有附上實體 .apk 檔**。

---

## 技術分工

| 你點名的東西 | 在這個專案裡做的事 |
| --- | --- |
| GitHub | `.github/workflows/build-apk.yml`：main push / `v*` tag / 手動觸發 → 裝 SDK 36 → 型別檢查 → **release 簽名打包** → `apksigner verify` + SDK 範圍檢查 → 上傳 Artifact；打 tag 時附加到 Release（同時也是 App 的更新來源） |
| JavaScript | `native-plugin/hketa-native/www/hketaNative.js`（Cordova JS bridge）、`scripts/build-web.mjs`、`scripts/verify-kotlin.js` |
| TypeScript | `src/`：型別、6 家 API client、正規化、UI、原生橋接 wrapper、**`update-panel.ts` 設定頁與更新流程** → `www/js/app.js` |
| Kotlin | `HkEtaNative.kt`：裝置／版本資訊、`geo:` 地圖、Toast、螢幕常亮、**`DownloadManager` 下載 + 安裝 Intent + 未知來源權限** |
| API | `data.etabus.gov.hk` / `rt.data.gov.hk` / `data.etagmb.gov.hk` 即時到站 JSON，加上 **`api.github.com/repos/{owner}/{repo}/releases/latest`** |

底層 **Cordova CLI 13 + cordova-android 15.1.0（Kotlin 2.1.21）**；Kotlin 以 Cordova plugin 形式由 TS 經 `cordova.exec` 呼叫，`config.xml` 開 `GradlePluginKotlinEnabled` 即自帶 Kotlin Gradle 支援。**裝置支援範圍 Android 8.0–17（API 26–37）**，詳見下節。

---

## 目錄

```
hketa-apk/
├── .github/workflows/build-apk.yml    # 只產 signed release APK（也是更新來源）
├── README.md / README.en.md           # 繁中 / English
├── config.xml                         # v1.0.0 / min 26 / target 36 / Kotlin 開關
├── build.json.example                 # 本機簽名設定範本（build.json 不進 git）
├── package.json / package-lock.json / tsconfig.json / .npmrc
├── scripts/  build-web.mjs · verify-kotlin.js · generate-icons.py
├── src/
│   ├── types / native / ui / main / version
│   ├── update-panel.ts                # 設定頁 + 線上更新流程
│   └── api/  http / kmb / ctb / nlb / gmb / mtr / lrt / index / update
├── www/      index.html · css/app.css · js/app.js · img/app-logo.png
├── native-plugin/hketa-native/        # JS bridge + Kotlin + plugin.xml
├── plugins/                           # Cordova 安裝產物（不進 git）
└── res/android/                       # logo-source.png + icons/{mdpi..xxxhdpi}/ + 預覽
```

---

## 快速開始

### 1. 本機預覽（純瀏覽器）

```bash
npm install
npm run typecheck
npm run build
npm run dev
```

瀏覽器模式用 fallback native bridge：地圖按鈕退化為 console 提示，檢查更新可正常打 GitHub API，但**下載／安裝會提示需要 Android 版**並改開下載頁。

### 2. 本機出 APK（需要 JDK 17 + Node 22 + Android SDK 36 + Build Tools 36.0.0）

```bash
npm run android:add   # 加 platform + 明確安裝 ./native-plugin/hketa-native
npm run apk
# 產物：platforms/android/app/build/outputs/apk/release/app-release.apk
```

> 本機 Kotlin plugin 刻意不寫進 `config.xml` 的 `<plugin>`，避免 Cordova 的 restore 機制把它寫回 `package.json` 的 devDependencies；由 `cordova plugin add ./native-plugin/hketa-native` 明確安裝最穩。
>
> 用 cordova-android 15.1.0 自帶的 AGP 8.10.1 / Gradle 8.14.2 / Kotlin 2.1.21 即可，不需要任何工具鏈覆寫。

### 3. 交給 GitHub 自動出 APK（推薦）

```bash
git init && git add . && git commit -m "HKETA v1.0.0 release"
git remote add origin https://github.com/<你>/hketa-apk.git
git push -u origin main
```

到 **Actions → Build HKETA signed release APK → 最新 run → Artifacts** 下載 `HKETA-release.apk`。
打 `v1.0.0` tag 會自動把同一個 APK 掛到 GitHub Release —— **這一步同時建立了 App 的更新來源**。

### 4. 設定正式版簽名

```bash
keytool -genkeypair -v \
  -keystore release.keystore -alias hketa \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype PKCS12
```

Repo → **Settings → Secrets and variables → Actions**：

| Secret | 內容 |
| --- | --- |
| `HKETA_KEYSTORE_BASE64` | `base64 -w0 release.keystore`（macOS：`base64 -i release.keystore | tr -d '\n'`） |
| `HKETA_KEYSTORE_PASSWORD` | keystore 密碼 |
| `HKETA_KEY_ALIAS` | `hketa` |
| `HKETA_KEY_PASSWORD` | key 密碼 |
| `HKETA_KEYSTORE_TYPE` | 選填，預設 `PKCS12` |

少任何必填值，workflow 直接報錯並列出缺哪個，不會默默產出未簽名檔。本機版：`cp build.json.example build.json` 後填真實值（已 gitignore）。

---

## 正式版做了什麼

- `version="1.0.0"` + `android-versionCode="1"`
- `cordova build android --release --buildConfig=build.json`（`debuggable=false`）
- workflow 用 `apksigner verify --verbose` 驗章，沒簽成功就 fail
- 移除所有 `--debug`、`app-debug.apk`、debug artifact 與 PR 觸發
- 產出統一命名 `HKETA-release.apk`

---

## 支援範圍：Android 8.0 – 17

**這句話指的是「裝置」：APK 可安裝並執行於 Android 8.0 到 Android 17 的裝置。** 它不等於 `targetSdkVersion=37`，兩者差別見下。

| 項目 | 值 | 對應 |
| --- | --- | --- |
| `android-minSdkVersion` | **26** | Android 8.0 Oreo（最低可安裝） |
| `android-targetSdkVersion` | **36** | Android 16 Baklava（cordova-android 15 官方驗證上限） |
| `android-compileSdkVersion` | **36** | 用 API 36 編譯 |
| `android-maxSdkVersion` | **不設** | 見下 |

Android 版本對 API：8.0 = 26、8.1 = 27、9 = 28、10 = 29、11 = 30、12 = 31/32、13 = 33、14 = 34、15 = 35、16 = 36、17 = 37（[uses-sdk 對照表](https://developer.android.com/guide/topics/manifest/uses-sdk-element)、[AOSP 代號表](https://source.android.com/docs/setup/about/build-numbers)）。

**為什麼不設 maxSdk=37？** 官方明確不建議 `maxSdkVersion`：Android 設計上向前相容，硬設上限會讓使用者日後升級到 Android 18 時被系統移除 App，Play 也拿它當過濾條件（[官方 `<uses-sdk>` 說明](https://developer.android.com/guide/topics/manifest/uses-sdk-element#maxsdk)）。所以做法是 **min 26 擋掉 Android 7 及更早、不設上限**；Android 17（API 37）與之後的版本都能裝。設定頁在 `SDK > 37` 時顯示「超出已驗證範圍」提示，但不阻擋執行。

### 為什麼 target 停在 36，而不是 37

`targetSdk` 決定要啟用哪一版的行為變更；Android 17 裝置**向後相容**，target 36 的 App 照樣能裝能跑，只是不啟用 API 37 的新行為。這符合 Google Play 現行規定（2026-08-31 起新 App／更新需 target ≥ 36；API 37 要到 2027-08 才強制）。

硬上 37 目前會卡在工具鏈，不是改一行數字就好：

- [Cordova Android Platform Guide](https://cordova.apache.org/docs/en/latest/guide/platforms/android/index.html) 標示 **cordova-android 15.x 官方支援 API 24–36**；[issue #1982](https://github.com/apache/cordova-android/issues/1982) 裡維護者說 37.x「可編但沒完整驗證」。
- **AGP 8.13 官方標明最高只支援到 API 36.1**（[AGP 8.13 release notes](https://developer.android.com/studio/releases/gradle-plugin#8-13-0)）。要 compileSdk 37 得往 AGP 9.x + Gradle 9.x + JDK 21 走，而 cordova-android 15 的基準是 AGP 8.10.1 / Gradle 8.14.2 / JDK 17，整套換掉風險很高。
- API 37 的 SDK 套件名是 `platforms;android-37.0`（帶 minor），Cordova 的 `check_android_target` 找的是 `android-37`，[issue #1982](https://github.com/apache/cordova-android/issues/1982) 正是這個 `37.0` vs `37` 的檢查失敗。

所以這個版本**保持在官方可編過的 36**，並把 API 37 視為「裝置可跑、尚未 target」。等 cordova-android 出支援 37 的版本再升，改法是：`config.xml` 的 target/compile 改 37、workflow 改裝 `platforms;android-37.0` + `build-tools;37.0.0`、同步 `scripts/verify-kotlin.js` 與 aapt2 檢查值，並實測 Android 17 的行為變更（大螢幕強制可調整大小、Certificate Transparency、`ACCESS_LOCAL_NETWORK` 等）。

> 想實驗可自行加回 `AndroidGradlePluginVersion` / `GradleVersion` / `android-buildToolsVersion` 覆寫，但**不保證 CI 編得過**，這裡不預設啟用。

### 各版本的相容性處理

| API | Android | 專案裡的對應處理 |
| --- | --- | --- |
| 26 | 8.0 | `minSdk`；`canRequestPackageInstalls()` + `REQUEST_INSTALL_PACKAGES` 才准應用內安裝 |
| 28 | 9 | `PackageInfo.longVersionCode`，舊版退回 `versionCode` |
| 29 | 10 | `DownloadManager` 寫入 Downloads（系統服務不受 Scoped Storage 限制） |
| 30 | 11 | `plugin.xml` 加 `<queries>`（geo / https / APK mime）；Kotlin 隱式 Intent 直接啟動並 catch `ActivityNotFoundException`，避免 `resolveActivity()` 被包可見性過濾 |
| 33 | 13 | `PackageManager.PackageInfoFlags`；下載通知由系統 DownloadManager 自管，不需 `POST_NOTIFICATIONS` |
| 34 | 14 | 安裝 Intent 帶 `FLAG_GRANT_READ_URI_PERMISSION`，用 content URI 而非 file URI |
| 35 | 15 | Edge-to-edge：`viewport-fit=cover` + 響應式 CSS |
| 36 | 16 | `targetSdk` / `compileSdk`，cordova-android 15 官方驗證上限 |
| 37 | 17 | 裝置端向後相容執行；設定頁顯示於「支援範圍」 |

**雙重把關**

- `scripts/verify-kotlin.js`（`before_build` hook）讀 `cdv-gradle-config.json`，`MIN_SDK_VERSION=26`、`SDK_VERSION/COMPILE_SDK_VERSION=36`、且**不得設 maxSdk**，不符就讓 build 失敗
- workflow 用 `aapt2 dump badging` 檢查實際 APK 的 `sdkVersion:'26'`、`targetSdkVersion:'36'`，並確認沒有 `maxSdkVersion`

---

## App 圖示

你提供的彩虹巴士圖已做成 launcher icon，來源存於 `res/android/logo-source.png`（1080×1080）。

| 層 | 內容 | 尺寸 |
| --- | --- | --- |
| `ic_launcher_background` | 重建的乾淨彩虹漸層（移除主體） | 108dp × 5 種密度 |
| `ic_launcher_foreground` | 去背的巴士＋司機，透明底，置中於 78% 高度 | 同上 |
| `ic_launcher_monochrome` | 白色剪影，Android 13+ 主題圖示跟隨系統配色 | 同上 |
| `ic_launcher` | legacy 方形 fallback | 48 / 72 / 96 / 144 / 192 px |

`config.xml` 的 `<platform name="android">` 已宣告 `mdpi`～`xxxhdpi` 五組 `src` / `background` / `foreground` / `monochrome`；cordova-android 15 會自動產生 `mipmap-*-v26/ic_launcher.xml`，**不用改 Gradle 或 AndroidManifest**。因為 `minSdk=26`，所有支援裝置都拿 adaptive 版本，各種 OEM 遮罩（圓形、圓角方、squircle）都能正確裁切。

另外附上 `logo-playstore-512.png`（商店圖）、`www/img/app-logo.png`（頁首與設定頁），以及 **`res/android/logo-preview.png`** —— 四種外觀的預覽，可直接看效果。

換圖或調大小：`scripts/generate-icons.py`（需 Pillow / numpy / opencv-python，資產已隨專案附上，一般 build 不用 Python）。細節見 `res/android/README.md`。

> ⚠️ 圖片由你提供並指定作為圖示；專案程式碼是 MIT，**不代表圖片的授權**。公開或上架前請確認你擁有使用／改作權利。

---

## 線上應用更新（設定頁）

App 頂部有 **到站預報 / 設定** 兩個分頁。設定頁包含：

| 區塊 | 內容 |
| --- | --- |
| 應用程式 | 目前版本（`PackageInfo.versionName` + build）、介面語言、螢幕常亮、裝置資訊 |
| 線上更新 | GitHub owner / repo、啟動時自動檢查、檢查更新、Release 資訊卡、下載進度條、下載並安裝、允許安裝、開啟下載頁 |

**流程**

1. `GET https://api.github.com/repos/{owner}/{repo}/releases/latest`
2. 取 `tag_name` 去掉 `v` 後與本機版本做三段數字比對
3. 找 `HKETA-release.apk` 附件（找不到就取第一個 `.apk`；都沒有就只給「開啟下載頁」）
4. TS 呼叫 Kotlin `downloadApk()` → 系統 `DownloadManager` 下載到 Downloads，通知列可見
5. TS 每 900ms 輪詢 `downloadProgress()`，更新進度條；Kotlin 回傳語意化 `state`（`running` / `success` / `failed`），不用在 JS 硬編碼 Android 常數
6. 完成後檢查 `canInstallUnknown()`；Android 8+ 未授權就開 `ACTION_MANAGE_UNKNOWN_APP_SOURCES`，使用者授權回到 App 會自動繼續
7. `installApk()` 用 `DownloadManager.getUriForDownloadedFile()` 的 content URI 發 `ACTION_VIEW` 安裝 Intent；失敗自動 fallback 開瀏覽器到 Release 頁

有新版本時「設定」分頁會亮一個小綠點；偏好（repo、自動檢查、語言、螢幕常亮）存 `localStorage`。

**第一次啟用前改這三處**

- `src/version.ts`：`DEFAULT_OWNER` / `DEFAULT_REPO`（預設 `yourname/hketa-apk`）
- `config.xml`：`<author href="https://github.com/yourname/hketa-apk">`
- 或直接不改程式，在 App「設定」頁輸入 owner / repo 後按「儲存設定」

> API 未認證每小時限 60 次；自動檢查只在啟動時跑一次。

**兩個硬限制**

- 🔑 **新 APK 必須用同一把 keystore 簽名**，否則 Android 會以 signature mismatch 拒絕安裝 —— 這也是為什麼 Secrets 要長期保存。
- 📱 `REQUEST_INSTALL_PACKAGES` 讓側載／自架發布可用；**Google Play 對此權限有政策限制**，上架 Play 請改用 Play In-App Updates 並移除應用內安裝（改走 `openUrl` 到商店頁）。

---

## 發布新版本（讓舊 App 查得到）

1. 改 `config.xml`：`version="1.1.0"`、`android-versionCode="2"`（**versionCode 一定要遞增**）
2. 改 `package.json` 與 `src/version.ts` 的 `FALLBACK_APP_VERSION` 為 `1.1.0`
3. 更新 `www/index.html` 若有版本相關文案
4. `git commit` → `git tag v1.1.0` → `git push origin main --tags`
5. workflow 自動產生 Release + `HKETA-release.apk`
6. 舊版 App 到「設定 → 檢查更新」就會看到 `v1.1.0` 並可下載安裝

> `tag_name` 建議用 `v1.1.0`；比對時會自動去掉前導 `v`。

---

## API 對照

| 營辦商 | ETA 端點 | 主要參數 |
| --- | --- | --- |
| KMB / LWB | `https://data.etabus.gov.hk/v1/transport/kmb/eta/{stop_id}/{route}/{service_type}` | 正線 `service_type=1`；另用 `/stop-eta/{stop_id}`、`/route/`、`/stop/{stop_id}` |
| Citybus | `https://rt.data.gov.hk/v2/transport/citybus/eta/CTB/{stop_id}/{route}` | `company_id` 固定大寫 `CTB`（新巴路線已併入） |
| NLB | `https://rt.data.gov.hk/v2/transport/nlb/stop.php?action=estimatedArrivals` | `routeId`、`stopId`、`language=zh` |
| GMB | `https://data.etagmb.gov.hk/eta/route-stop/{route_id}/{stop_id}` | 亦可用 `/eta/stop/{stop_id}`；回 `timestamp` / `diff` / `remarks_tc` |
| MTR | `https://rt.data.gov.hk/v1/transport/mtr/getSchedule.php?line=&sta=&lang=` | `lang=TC`／`EN`；回 `UP` / `DOWN` |
| Light Rail | `https://rt.data.gov.hk/v1/transport/mtr/lrt/getSchedule?station_id=&with_special=1` | 回 `platform_list[].route_list[]` |
| 更新 | `https://api.github.com/repos/{owner}/{repo}/releases/latest` | 未認證 60 req/hr |

官方規格：[KMB/LWB](https://data.etabus.gov.hk/datagovhk/kmb_eta_api_specification.pdf) · [NLB](https://www.nlb.com.hk/datagovhk/BusServiceOpenAPIDocumentation2.0.pdf) · [GMB](https://data.etagmb.gov.hk/static/GMB_ETA_API_Specification.pdf) · [MTR](https://opendata.mtr.com.hk/doc/Next_Train_API_Spec_v1.7.pdf) · [輕鐵](https://opendata.mtr.com.hk/doc/LR_Next_Train_API_Spec_v1.1.pdf) · Citybus v2.01 由 DATA.GOV.HK 資料集頁面提供。

所有 client 正規化成同一個 `EtaItem`，UI 只認這個型別。

---

## 試一組會動的參數

App 預填：`KMB` / `40` / `647AB0AD358F10DF` / `1`（美孚）。
其他：MTR `TML`+`ADM`+`TC`｜輕鐵 路線留空 + `600` + `1`｜城巴 `11`+`001145`+`O`。

---

## 驗證狀態

本回合已跑過：`tsc --noEmit` ✅、esbuild 打包 ✅、`config.xml` / `plugin.xml` XML ✅、`package.json` / `tsconfig.json` / `package-lock.json` / `build.json.example` JSON ✅、workflow YAML ✅、plugin `source-file` 路徑與 `.kt` 存在 ✅、`<queries>` 宣告 ✅、i18n 字典一致 ✅、`config.xml` 的 min/target/compile = 26/36/36 且無 maxSdk ✅、`verify-kotlin.js` 與 aapt2 檢查值同步 ✅、5 組 icon 宣告與 20 張 PNG 路徑／尺寸／透明度 ✅、四種遮罩預覽目視檢查 ✅、無 debug 殘留 ✅。

**未做**：沙盒無 Android SDK／Gradle、也無你的 keystore，因此沒有編出實體 APK，也無法在真機驗證 `DownloadManager` + 安裝 Intent（部分 OEM 對 `content://downloads/...` 的 installer 支援不一，已做瀏覽器 fallback）。簽名與打包交給上面的 GitHub Actions。

---

## 已知限制

- 目前以官方 `route` / `stop ID` 直接查詢；站名搜尋、完整站點列表、去回程切換、書籤尚待補。
- 資料直接打官方 API，無離線快取；`429`、夜間無班次會顯示錯誤或空列表。
- 「預定班次 / Scheduled Bus」與 GPS 即時到站是兩件事，卡片會以備註標示。
- MTR／LRT 用官方線路與車站代碼，非中文站名；`src/api/mtr.ts` 附常用代碼。
- 有 launcher icon，但**尚未做 splash screen**（Android 12+ 會用系統預設啟動畫面）；需要時可在 `config.xml` 補 `<splash>`。
- `targetSdkVersion=36`（Android 16），**不是 37**；Android 17 裝置靠平台向後相容執行。Google Play 現行要求 target ≥ 36（2026-08-31 起），已符合；API 37 預計 2027-08 才強制，上架前請確認當下規定（[Play 目標 API 規定](https://support.google.com/googleplay/android-developer/answer/11926878)）。
- **API 37 尚未 target**：見上節工具鏈限制，等 cordova-android 官方支援再升。
- `minSdk=26` 會排除 Android 7.x 及更早裝置；這正是「只支援 8–17」的代價。
- Android 17 對 target 37 才啟用的行為（大螢幕強制可調整大小、CT、`ACCESS_LOCAL_NETWORK` 等）尚未驗證，因為本版本不 target 37。
- 應用內安裝需 `REQUEST_INSTALL_PACKAGES` 與使用者授權，且不適用 Play 上架情境（見上節）。

---

## 授權與資料來源

- 程式碼：MIT（見 `LICENSE`）。
- 功能參考：[shamough1792/HKETA](https://github.com/shamough1792/HKETA)（Cordova / JavaScript / MIT）。
- 概念參考：[DATA.GOV.HK — HKETA](https://data.gov.hk/tc/application/hketa)。
- 即時資料：香港運輸署／DATA.GOV.HK／各營辦商開放資料，請遵守各自使用條款與標註要求。
- 更新來源：[GitHub Releases API](https://docs.github.com/en/rest/releases/releases)。
