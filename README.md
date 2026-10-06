# HKETA — 香港巴士到站預報（原生 Android App）

把原本 `benyeung08/etaapp`（WebToApp 的 fork，本質是「把網頁包進 WebView」的殼）**完全換成真正的原生 App**：
Kotlin + Jetpack Compose，直接對接香港各營辦商的公開 ETA 接口，沒有 WebView、沒有網頁外殼。

## 與原專案的差別

| | 原 etaapp（WebToApp fork） | 本專案 HKETA |
|---|---|---|
| 呈現方式 | WebView 載入網頁（hkbus.app 之類） | 原生 Compose UI，自行繪製列表與卡片 |
| 資料取得 | 由網頁端處理 | App 直接呼叫官方 API（OkHttp） |
| 離線能力 | 依賴網頁快取 | 路線 / 車站索引快取於內部儲存，可離線搜尋 |
| 定位 | 由網頁請求 | 原生 FusedLocationProvider，附近車站距離排序 |
| 擴充性 | 受限於網頁 | 資料層、索引層、UI 層分離，可加 Widget / 通知 / 地圖 |

## 功能

- **路線搜尋**：輸入路線號（如 `1A`、`6`、`K51`）即列出各營辦商路線與方向
- **車站列表**：點路線看沿途車站，點車站看到站預報
- **到站預報**：顯示未來班次分鐘數、目的地、備註，**每 20 秒自動刷新**
- **附近車站（GPS）**：以定位為中心，列出 3 公里內車站與距離，點進去看停靠路線
- **多營辦商**：九巴 KMB、城巴 CTB、新大嶼山巴士 NLB、專線小巴 GMB、港鐵巴士 MTR Bus

## 技術棧

- Kotlin 2.0.21、Jetpack Compose（Material 3）、Navigation Compose
- OkHttp 4.12 + kotlinx.serialization
- Coroutines / Flow、AndroidViewModel + State
- Google Play Services Location（Fused Location）
- minSdk 26（Android 8.0）、targetSdk 35

## 編譯方式

```bash
# 需求：Android Studio Hedgehog (2023.1.1) 以上、JDK 17
./gradlew assembleDebug      # 產出 app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug       # 直接安裝到已連線的裝置
```

或用 Android Studio：`File ▸ Open ▸ 選擇本資料夾` → 等待 Gradle Sync → Run ▶。
首次啟動會自動建立路線索引（約需 10–30 秒，視網絡而定）。

## 資料來源（官方公開接口，免授權）

| 營辦商 | 用途 | 接口 |
|---|---|---|
| 九巴 KMB | 路線 / 路線-站 / 車站 | `data.etabus.gov.hk/v1/transport/kmb/route/`、`.../route-stop/`、`.../stop` |
| 九巴 KMB | 到站預報 | `data.etabus.gov.hk/v1/transport/kmb/stop-eta/{stopId}` |
| 城巴 CTB | 路線 / 路線-站 / 車站 | `rt.data.gov.hk/v2/transport/citybus/route/CTB`、`.../route-stop/CTB/{route}/{dir}`、`.../stop/{id}` |
| 城巴 CTB | 到站預報 | `rt.data.gov.hk/v2/transport/citybus/eta/CTB/{stopId}/{route}` |
| 嶼巴 NLB | 路線 / 車站 / 預報 | `rt.data.gov.hk/v2/transport/nlb/route.php?action=list`、`stop.php?action=list&routeId=`、`stop.php?action=estimatedArrivals` |
| 專線小巴 GMB | 路線 / 路線-站 / 預報 | `data.etagmb.gov.hk/route/{region}`、`route-stop/{routeId}/{seq}`、`eta/stop/{stopId}` |
| 港鐵巴士 | 車站 + 預報（POST） | `rt.data.gov.hk/v1/transport/mtr/bus/getSchedule` |

## 索引策略（為什麼有些功能要等一下）

- **九巴**：三個請求即可建立完整索引（路線、路線-站對應、全港車站含座標）→ 附近車站最佳
- **嶼巴**：路線不多，索引時一併抓取車站
- **城巴**：官方沒有「全量車站」接口，採按需抓取；想讓附近車站涵蓋城巴，請到「設定」執行**深度索引城巴**（逐條路線抓 route-stop 與座標，約數分鐘，建議在 Wi-Fi 下執行）
- **專線小巴**：路線清單全量，車站在點開路線時抓取（座標需額外請求，故不納入附近搜尋）
- **港鐵巴士**：無需索引，輸入 K 字頭路線號即即時向官方接口查詢

索引會存成 `filesDir/hketa_index.json`，下次啟動直接讀取；可在「設定」隨時重建。

## 專案結構

```
app/src/main/java/com/hketa/app/
├─ data/        資料層：Models、ApiModels、Http、IndexStore、EtaRepository、TimeUtil
├─ location/    定位封裝（Fused Location + suspend）
├─ vm/          AppViewModel（索引狀態、搜尋、預報）
└─ ui/          AppNav 與各頁面（搜尋 / 車站 / 預報 / 附近 / 設定）
```

## 已知限制與後續可做

- 尚未實作：桌面 Widget、收藏車站、路線地圖、港鐵重鐵與輕鐵（接口已確認，需站碼表）
- 「附近車站」的完整度取決於已建立的車站索引（預設以九巴最完整）
- 專線小巴車站座標未索引，故不出現在附近搜尋
- GMB / NLB 接口偶有欄位調整，解析已做寬鬆容錯，但仍建議以官方文件為準

## 隱私

只向上述官方接口發出 HTTPS 請求，不蒐集、不上傳任何個人資料；定位只用於當下排序附近車站，不會儲存或傳送。
