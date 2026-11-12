# HK ETA 交通 · 香港公共交通到站預報與路線資訊

一個全端香港公共交通 App：**TypeScript API**（Node.js + Express）聚合官方開放數據並提供統一的 JSON 資料格式；**Kotlin Android** 客戶端以乾淨的 MVVM 架構消費這些數據。

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

## 🛠️ 技術棧

| 層級 | 技術 |
|---|---|
| 後端 | TypeScript · Node.js · Express |
| 前端 | Kotlin · Android SDK · MVVM |
| 網路 | Retrofit · OkHttp · Gson |
| 架構 | Repository Pattern · LiveData · ViewModel |

## 📝 授權

MIT License
