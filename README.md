# HKETA Hybrid — 把 hketa 的功能做成 hkbus.app 形態的 APK

一句話：**用 Kotlin 做 Android 原生殼與網路／定位橋接，用 HTML + JavaScript 做 UI 與 ETA 邏輯，前端放在 GitHub Pages、APK 由 GitHub Actions 自動產出。**

`benyeung08/hketa` 是 Kotlin + Compose 的**純原生** App（README 開頭就寫明「無 WebView、無網頁外殼」）；`hkbus.app` 本身則是開源 PWA（`hkbus/hk-independent-bus-eta`，React + TypeScript + Vite + MUI，GPL-3.0，可自建、可部署 GitHub Pages）[citation:7][citation:4][citation:13]。本專案等於把 hketa 的功能「搬回」混合式架構，但**不是**直接把別人的網站塞進 WebView。

## 三種形態對照

| | hketa 原生 | 本專案 Hybrid | hkbus.app |
|---|---|---|---|
| UI | Compose | HTML/JS（內置 assets） | React PWA |
| 取資料 | OkHttp | OkHttp → JS 橋接 | 瀏覽器 + 爬蟲資料庫 |
| 離線 | 索引快取 | 前端整包離線 | Service Worker |
| 建置 | Gradle | Gradle + GitHub Actions | Vite + Pages |

## 架構

```
web/          HTML + JS + CSS  → GitHub Pages（瀏覽器版）
                 ↓ CI 複製
android/app/src/main/assets/web/   → 打包進 APK，file:// 離線載入
                 ↕ JavascriptInterface「HKETA」
android/.../EtaBridge.kt          → OkHttp 打官方 API、LocationManager 取定位、讀版本號
```

**為什麼要 Kotlin 橋接而不讓 JS 直接 fetch**：政府 ETA 接口多數不開 CORS，瀏覽器直連會被擋；由原生層發請求就沒有這問題，也能統一做網域白名單、避免 WebView 變成任意代理。這同時讓 Kotlin 不只是個殼，而是真的負責網路、定位、版本與外部連結。

**為什麼不直接 `loadUrl("https://hkbus.app/zh")`**：那是分發第三方 GPL-3.0 前端，要受其授權與商標約束，且依賴對方站台可用性、無法離線、也沒法保留 hketa 的三語／收藏／附近邏輯。若你只是想要這個效果，`MainActivity.kt` 裡把 `LOAD_MODE` 改成 `REMOTE` 即可，但請先讀 `NOTICE.md`。

## 功能（對齊 hketa v1.0.3 核心）

- 底部四頁：主頁／收藏／搜尋／設定（App1933 風格）
- 路線搜尋（1A、6、K51…）、沿途車站、到站預報、每 20 秒自動刷新
- 附近車站：KMB 全量車站座標 + 原生定位，3 公里內排序；未授權可手動選點
- 多營辦商：KMB / CTB / NLB / GMB / MTR Bus / 港鐵重鐵 / 輕鐵
- 收藏車站（localStorage）、三語介面（繁／簡／英，跟隨系統）
- 設定頁檢查 GitHub Releases 更新、資料來源說明、重建索引
- 無廣告、無追蹤；定位只用於當下排序，不儲存不上傳

## App 圖示

`assets/logo-source.png` 是你提供的彩虹巴士原圖，已產生一整套 launcher icon：

- `res/mipmap-*/ic_launcher.png`、`ic_launcher_round.png`（48–192px，Android 7.1 及舊 launcher）
- `res/mipmap-*/ic_launcher_background.png`（去主體的平滑彩虹）、`ic_launcher_foreground.png`（去背的小人＋巴士，縮到安全區）
- `res/mipmap-anydpi-v26/ic_launcher.xml`、`ic_launcher_round.xml`（Android 8+ adaptive icon，圓／方／藥丸遮罩都唔會切到主體）
- `web/icon-192.png`、`icon-512.png`、`icon-maskable-512.png`、`favicon.png`（Pages／PWA／加到主畫面）

`AndroidManifest.xml` 已設 `android:icon` 與 `android:roundIcon`。換圖時覆蓋 `assets/logo-source.png` 再跑：

```bash
python3 tools/make_icons.py            # 或 python3 tools/make_icons.py 新圖.png
```

腳本會自動去背、重建彩虹背景、輸出全部密度，並更新 `assets/icon-preview.png` 預覽圖。需要 `numpy / Pillow / scipy`（本機工具鏈用，App 本身零依賴）。

## 快速開始（推薦：完全在 GitHub 上）

1. 把本目錄 push 到你的 GitHub repo（`main` 分支）。
2. Actions → **Build HKETA Hybrid APK** → Run workflow。
3. 跑完在 Artifacts 下載 `hketa-hybrid-apk`（含 debug 與 release APK）。
4. push `v*` 標籤會自動把 APK 掛到 Release，App 內「檢查更新」就讀得到。
5. 同一個 workflow 會把 `web/` 部署到 GitHub Pages，得到瀏覽器版。

> CI 產出的 release APK 預設是 **unsigned**。要正式簽名，在 repo Secrets 加 `HKETA_KEYSTORE_B64`（keystore 的 base64）、`HKETA_KEYSTORE_PASSWORD`、`HKETA_KEY_ALIAS`、`HKETA_KEY_PASSWORD`；workflow 會解碼並由 `app/build.gradle.kts` 自動套用。個人使用可直接裝 debug APK。

## 本機建置

需求：JDK 17、Android SDK 35、Gradle 8.9。

```bash
mkdir -p android/app/src/main/assets/web
cp -R web/. android/app/src/main/assets/web/
cd android && gradle wrapper --gradle-version 8.9 && ./gradlew :app:assembleDebug
# 產出 app/build/outputs/apk/debug/app-debug.apk
```

前端也可單獨跑：`cd web && python3 -m http.server 8080`，但瀏覽器直連官方 API 會遇到 CORS，完整功能請用 APK。

## 官方資料來源（免授權，HTTPS）

| 營辦商 | 接口 |
|---|---|
| 九巴 | `data.etabus.gov.hk/v1/transport/kmb/route/`、`route-stop/`、`stop`、`stop-eta/{stopId}` |
| 城巴 | `rt.data.gov.hk/v2/transport/citybus/route/CTB`、`route-stop/CTB/{route}/{inbound｜outbound}`、`stop/{id}`、`eta/CTB/{stopId}/{route}` |
| 嶼巴 | `rt.data.gov.hk/v2/transport/nlb/route.php?action=list`、`stop.php` |
| 小巴 | `data.etagmb.gov.hk/route/{region}`、`route-stop/{routeId}/{seq}`、`stop-eta/{stopId}/{routeId}` |
| 港鐵 | `rt.data.gov.hk/v1/transport/mtr/bus/getSchedule`（POST）、`getSchedule.php`、`lrt/getSchedule` |

完整對照見 hketa 原 README。路線／車站索引快取在 `localStorage`，鐵路與輕鐵完整站表可把 hketa 的 `assets/rail.json` 放到 `web/rail.json`，`app.js` 會自動載入（未放時用內置精簡站表）。

## 已知限制

- 瀏覽器／Pages 版受 CORS 限制，ETA 需自設代理；APK 版無此問題。
- `normalizeEta()` 對 KMB／CTB 做明確解析，其餘營辦商用通用容錯解析；官方改格式時改這一個函式即可。
- 城巴／小巴無「全量車站」接口，附近車站以九巴為主（與 hketa 相同策略），其餘靠按需抓取。
- 本專案未在實機跑過 Gradle 建置，CI 是唯一驗證點；JS 語法已用 `node --check` 檢查。

## 授權

自行撰寫的殼與前端可自由使用；但若你切換成內嵌／複製 hkbus 的程式碼，即為 GPL-3.0 衍生作品。詳見 `NOTICE.md`。
