# HK ETA 交通 · 香港公共交通到站預報與路線資訊

![GitHub Actions Build](https://github.com/your-username/hketa-app/actions/workflows/build-apk.yml/badge.svg)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)
[![Android API](https://img.shields.io/badge/Android%20API-24--38-brightgreen.svg)](https://developer.android.com/about/versions)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-blue.svg)](https://kotlinlang.org)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.0-blue.svg)](https://www.typescriptlang.org)

一個全端香港公共交通 App：**TypeScript API**（Node.js + Express）聚合官方開放數據並提供統一的 JSON 資料格式；**Kotlin Android** 客戶端以乾淨的 MVVM 架構消費這些數據。

> 📦 **GitHub Actions 自動建置 APK** — 每次 push 到 `main` 分支都會自動編譯並在 [Actions 頁面](../../actions) 產出 APK 下載。
> 詳見 [.github/workflows/build-apk.yml](.github/workflows/build-apk.yml)。

## ✨ 功能特色

| 模組 | 說明 |
|---|---|
| 🚌 即時到站預報 | 整合 KMB / Citybus / NLB 官方開放數據，提供即時到站時間 |
| 🚐 路線資料庫 | 巴士 · 小巴 · 地鐵 · 輕鐵 — 收費、完整路線、沿途車站、服務班次、首末班車時間 |
| 🔄 資料更新 | 支援個別模式更新或一鍵「全部更新」，附版本清單 + SHA-256 校驗碼 |
| ⚙️ 設定頁面 | 顯示各模式的版本號、更新時間、資料大小 |

## 🏗️ 系統架構

```
hketa-app/
├── api/                        # TypeScript API 伺服器
│   ├── src/
│   │   ├── index.ts            # Express 啟動入口
│   │   ├── etaService.ts       # 到站預報聚合（KMB / Citybus / NLB）
│   │   ├── routeService.ts     # 路線資料庫 + 收費 + 班次 + 更新引擎
│   │   ├── routes/
│   │   │   ├── eta.ts          # GET /api/eta/:stopId
│   │   │   └── routes.ts       # GET/POST /api/routes, /api/updates
│   │   └── types/
│   │       └── eta.ts          # 共用資料契約（與 Kotlin 端保持一致）
│   ├── package.json
│   └── tsconfig.json
│
└── androidApp/                 # Kotlin Android App（MVVM）
    └── src/main/java/com/hketa/app/
        ├── HkEtaApp.kt         # Application，存放 BASE_URL
        ├── data/
        │   ├── api/            # Retrofit 服務介面
        │   ├── model/          # 領域模型（鏡像 TS 契約）
        │   └── repository/     # 單一資料來源
        └── ui/                 # Activities + ViewModels + Adapters
```

## 🚀 快速開始

### 1. 啟動 TypeScript API

```bash
cd api
npm install
npm run dev          # http://localhost:3000
```

### 2. 建置 Android APK

```bash
cd androidApp
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

## 📡 API 端點

| 方法 | 路徑 | 用途 |
|---|---|---|
| GET | `/api/eta/:stopId` | 即時到站預報（可用 `?operators=KMB,CTB,NLB` 篩選） |
| GET | `/api/routes` | 所有交通模式的路線 |
| GET | `/api/routes/:mode` | 按模式篩選：`BUS` / `MINIBUS` / `MTR` / `LRT` |
| GET | `/api/route/:routeId` | 路線詳情（含沿途車站） |
| GET | `/api/routes/search/:query` | 搜尋路線名稱 / 起點 / 終點 |
| GET | `/api/updates/manifest` | 版本清單（版本號、校驗碼、大小） |
| POST | `/api/updates/:mode` | 更新單一模式 |
| POST | `/api/updates` | 更新全部模式 |

### 試用範例

```bash
curl http://localhost:3000/api/routes/MTR
curl http://localhost:3000/api/updates/manifest
curl -X POST http://localhost:3000/api/updates/BUS
```

## 🧪 試用站點編號

| 營運商 | 站點編號 | 備註 |
|---|---|---|
| KMB | `A60AE774B09A5E44` | 40 號線，麗港城方向 |
| CTB | `001001` | 中環區 |

## 📱 連接 App 與 API

編輯 `androidApp/src/main/java/com/hketa/app/HkEtaApp.kt`：

```kotlin
const val BASE_URL = "http://10.0.2.2:3000/api/"   // 模擬器 → 主機
// const val BASE_URL = "http://192.168.x.x:3000/api/"  // 實機 → 你的區網 IP
```

> 使用實機時請填入電腦的區網 IP，並確保手機與電腦在同一 Wi-Fi 下。

## ⚙️ 設定 — 資料更新功能

App 內「設定」頁面提供完整的資料更新管理：

1. **一鍵更新全部** — 同時更新四種交通模式的資料
2. **個別模式更新** — 點擊清單中任一模式單獨更新
3. **版本資訊展示** — 顯示當前版本號、最後更新時間、資料大小
4. **完整性校驗** — API 端計算 SHA-256 checksum 確保資料未被損壞
5. **更新結果回饋** — 即時顯示「已更新 / 已最新 / 失敗」狀態

## 📊 資料來源

- **巴士（KMB）** — `https://data.etabus.gov.hk`（路線、收費、班次、到站）
- **城巴 / 新巴 / 新大嶼山** — `https://rt.data.gov.hk/v1/transport/citybus-nwfb`
- **地鐵 / 輕鐵** — 港鐵官方參考資料集
- **小巴** — 綠色小巴參考資料集

> 無需 API Key，所有資料均來自公開開放平台。

## 📱 Android 版本支援

本 App 最低支援 **Android 7.0（Nougat, API 24）**，涵蓋 2016 年至今的絕大部分 Android 裝置。

| 項目 | 版本 |
|---|---|
| 最低支援 | Android 7.0（API 24） |
| 目標版本 | Android 14（API 34） |
| 測試範圍 | Android 7.0 ~ 14 |

### 為 Android 7 所做的相容性處理

- **Desugaring（核心庫脫糖）**：透過 `com.android.tools:desugar_jdk_libs` 將 `java.time` 等新 API 向下相容至 API 24
- **明文 HTTP 支援**：加入 `network_security_config.xml`，Android 9+ 預設禁止 HTTP，此設定允許連接本地 TypeScript API 伺服器
- **日期解析相容**：使用 `SimpleDateFormat` + 手動 ISO-8601 解析，避免 `java.time.Instant`（需 API 26+）
- **Library 版本鎖定**：所有 AndroidX / Material / Lifecycle 依賴均選用支援 API 24 的版本
- **MultiDex 啟用**：避免方法數超限（API 24 以下需要手動啟用）

> 若僅用於 Android 8+ 環境，可移除 `network_security_config.xml` 中的明文設定，改為 HTTPS 端點。

## 🔐 長期簽名一致性

為確保用戶能**直接覆蓋安裝**每次更新（無需卸載舊版），本專案採用長期一致的簽名機制。

### 運作原理

```
首次建置（無 keystore）
  │
  ├─ GitHub Actions 自動生成 4096-bit RSA 簽名金鑰
  ├─ 有效期：30 年（10950 天）
  ├─ 自動上傳為 Artifact 備份
  └─ 建議手動設定為 GitHub Secrets（永久保存）

後續建置
  │
  ├─ 從 GitHub Secrets 讀取同一個 keystore
  ├─ 每次建置使用相同簽名 → 可直接覆蓋安裝
  └─ Version Code 自動遞增（基於 git commit 數）
```

### 設定方式（推薦）

1. **首次建置後**，下載 `signing-keystore-backup` Artifact
2. 將 keystore 編碼為 Base64：
   ```bash
   base64 -w0 release-key.jks > keystore_b64.txt
   ```
3. 在 GitHub Repo → **Settings** → **Secrets and variables** → **Actions** 加入：
   | Secret 名稱 | 值 |
   |---|---|
   | `SIGNING_KEYSTORE` | `keystore_b64.txt` 的內容 |
   | `SIGNING_STORE_PASSWORD` | Keystore 密碼 |
   | `SIGNING_KEY_ALIAS` | `hketa` |
   | `SIGNING_KEY_PASSWORD` | Keystore 密碼 |
4. 之後每次建置都會自動使用此簽名

### 本地開發

```bash
# 1. 生成 keystore
keytool -genkeypair -v -keystore hketa-release-key.jks \
  -storepass 你的密碼 -keypass 你的密碼 \
  -alias hketa -keyalg RSA -keysize 4096 -validity 10950

# 2. 複製設定範例並填入資訊
cp androidApp/keystore.properties.example androidApp/keystore.properties
# 編輯 keystore.properties，填入你的 keystore 路徑和密碼

# 3. 建置簽名 APK
cd androidApp && ./gradlew assembleRelease
```

### 重要提醒

> ⚠️ **簽名金鑰一旦設定就不要更換**，否則：
> - 用戶必須先卸載舊版才能安裝新版
> - 所有已安裝用戶的 App 資料會遺失
> - Google Play / 第三方商店可能拒絕更新

> 💡 **備份建議**：將 `keystore.properties` 中的資訊（或 jks 檔案本身）存放在密碼管理器中，確保即使換電腦也不會遺失。

## 🔄 GitHub Actions — 自動建置 APK

本專案已配置 GitHub Actions，push 到 `main` 分支後會**自動建置並產出 APK**。

### 工作流程

```
Push to main ──▶ GitHub Actions 觸發 ──▶ 自動編譯 ──▶ 產出 APK Artifact
                                              │
                                              ▼
                                    自動建立 GitHub Release
```

### 建置流程

1. **Checkout** — 拉取最新程式碼
2. **Setup JDK 17** — 安裝 Temurin JDK 17
3. **Setup Android SDK** — 配置 Android 建置環境
4. **Gradle Build** — 編譯 Debug + Release APK
5. **Upload Artifact** — 上傳 APK 到 Actions 產物頁
6. **Auto Release**（僅 main 分支）— 自動建立 Release 並附上 APK

### 使用方式

1. Fork 或 Clone 此 repo 到你的 GitHub 帳號
2. 確保 GitHub Actions 已啟用（`Settings` → `Actions` → `General` → `Allow all actions`）
3. Push 到 `main` 分支，Actions 會自動開始建置
4. 建置完成後，在 `Actions` 頁面下載 APK，或到 `Releases` 頁面取得最新版本

### 手動觸發建置

進入 `Actions` → `Build Android APK` → 點擊 `Run workflow` → 選擇分支 → `Run workflow`

### 工作流檔案

| 檔案 | 用途 |
|---|---|
| `.github/workflows/build-apk.yml` | 自動建置 APK + 自動 Release |
| `.github/workflows/deploy-api.yml` | 部署 API 到 Cloudflare Workers |

> 💡 **簽名設定**：如需 Release APK 簽名，請在 `Settings` → `Secrets and variables` → `Actions` 中設定：
> - `SIGNING_KEYSTORE` — Base64 編碼的 keystore 檔案
> - `SIGNING_STORE_PASSWORD` — keystore 密碼
> - `SIGNING_KEY_ALIAS` — 金鑰別名（預設 `hketa`）
> - `SIGNING_KEY_PASSWORD` — 金鑰密碼
>
> 詳見上方「🔐 長期簽名一致性」章節。

## 🛠️ 技術棧

| 層級 | 技術 |
|---|---|
| 後端 | TypeScript · Node.js · Express |
| 前端 | Kotlin · Android SDK · MVVM |
| 網路 | Retrofit · OkHttp · Gson |
| 架構 | Repository Pattern · LiveData · ViewModel |
| CI/CD | GitHub Actions · Gradle |

## 📝 授權

MIT License
