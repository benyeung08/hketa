# HKETA — 香港巴士到站預報（原生 Android App）

把原本 `benyeung08/etaapp`（WebToApp fork，本質係「把網頁包入 WebView」嘅殼）完全換成真正嘅原生 App：
Kotlin + Jetpack Compose，直接對接香港各營辦商嘅公開 ETA 接口，**無 WebView、無網頁外殼**。

> 本倉庫已完成原生重寫：`app/src/main/java` 全部換成 `com.hketa.app`，
> 原本 WebToApp 嘅 `assets/`、`cpp/`、`proto/`、`schemas/` 同 WebView Activity 已全數移除。

## 與原專案嘅差別

| | 原 etaapp（WebToApp fork） | 本專案 HKETA |
|---|---|---|
| 呈現方式 | WebView 載入網頁（hkbus.app 之類） | 原生 Compose UI，自行繪製列表與卡片 |
| 資料取得 | 由網頁端處理 | App 直接呼叫官方 API（OkHttp） |
| 離線能力 | 依賴網頁快取 | 路線／車站索引快取於內部儲存，可離線搜尋 |
| 定位 | 由網頁請求 | Fused Location（無 GMS 時自動退回系統 LocationManager） |
| 擴充性 | 受限於網頁 | 資料層、索引層、UI 層分離 |

## 功能

- **路線搜尋**：輸入路線號（如 1A、6、K51）即列出各營辦商路線與方向
- **車站列表**：點路線睇沿途車站，點車站睇到站預報
- **到站預報**：顯示未來班次分鐘數、目的地、備註，**每 20 秒自動刷新**
- **附近車站（GPS）**：以定位為中心，列出 3 公里內車站與距離，點入去睇停靠路線
- **多營辦商**：九巴 KMB、城巴 CTB、新大嶼山巴士 NLB、專線小巴 GMB、港鐵巴士 MTR Bus
- **港鐵重鐵**：10 條線 98 個站（機場快線／東鐵線／屯馬線／將軍澳線／東涌線／觀塘線／荃灣線／港島線／南港島線／迪士尼線）
- **輕鐵**：11 條常規線 68 個站（505／507／610／614／614P／615／615P／705／706／751／761P）
- **鐵路站表內置**：官方路線與車站（含座標）打包進 App，離線可用、唔使等索引

## 拎 APK（最快方法）

本倉庫附咗 `.github/workflows/build-apk.yml`：

1. 將呢個專案 push 上你嘅 GitHub repo（main 分支）
2. 去 repo 嘅 **Actions** 頁，揀 **Build HKETA APK**，撳 **Run workflow**
3. 跑完之後喺該次執行嘅 **Artifacts** 下載 `hketa-apk`（內有 debug 與 release APK）
4. 傳到手機安裝即可（首次安裝允許「未知來源」）

push `v*` 標籤嘅話，APK 會自動掛到 Release 上。

## 本機建置

需求：JDK 17、Android SDK（platform 35 + build-tools 35.0.0）、Gradle 8.9。

```bash
gradle :app:assembleDebug    # 産出 app/build/outputs/apk/debug/app-debug.apk
gradle :app:assembleRelease  # 産出 app/build/outputs/apk/release/app-release.apk
```

用 Android Studio 開（Ladybug 以上）：File ▸ Open ▸ 選擇本資料夾 → Gradle Sync → Run ▶。
如果 Studio 報 Gradle wrapper 相關錯誤，刪咗 `gradle/wrapper` 再 Sync 讓 Studio 重新生成，
或者喺 Settings ▸ Build Tools ▸ Gradle 指定本機 Gradle 8.9。

首次啟動會自動建立路線索引（約 10–30 秒，視網絡而定）。

## 資料來源（官方公開接口，免授權）

| 營辦商 | 用途 | 接口 |
|---|---|---|
| 九巴 KMB | 路線／路線-站／車站 | `data.etabus.gov.hk/v1/transport/kmb/route/`、`.../route-stop/`、`.../stop` |
| 九巴 KMB | 到站預報 | `data.etabus.gov.hk/v1/transport/kmb/stop-eta/{stopId}` |
| 城巴 CTB | 路線／路線-站／車站 | `rt.data.gov.hk/v2/transport/citybus/route/CTB`、`.../route-stop/CTB/{route}/{inbound｜outbound}`、`.../stop/{id}` |
| 城巴 CTB | 到站預報 | `rt.data.gov.hk/v2/transport/citybus/eta/CTB/{stopId}/{route}` |
| 嶼巴 NLB | 路線／車站／預報 | `rt.data.gov.hk/v2/transport/nlb/route.php?action=list`、`stop.php?action=list&routeId=`、`stop.php?action=estimatedArrivals` |
| 專線小巴 GMB | 路線／路線-站／預報 | `data.etagmb.gov.hk/route/{region}`、`route-stop/{routeId}/{seq}`、`stop-eta/{stopId}/{routeId}` |
| 港鐵巴士 | 車站 + 預報（POST） | `rt.data.gov.hk/v1/transport/mtr/bus/getSchedule` |
| 港鐵重鐵 | 到站預報 | `rt.data.gov.hk/v1/transport/mtr/getSchedule.php?line={AEL｜TML…}&sta={站碼}&lang=TC` |
| 輕鐵 | 到站預報 | `rt.data.gov.hk/v1/transport/mtr/lrt/getSchedule?station_id={站號}&with_special=1` |
| 港鐵 | 路線／車站／座標（內置） | 港鐵開放資料 `mtr_lines_and_stations.csv`、`light_rail_routes_and_stops.csv` |

## 索引策略（點解有啲功能要等一陣）

- **九巴**：三個請求即可建立完整索引（路線、路線-站對應、全港車站含座標）→ 附近車站最佳
- **嶼巴**：路線唔多，索引時一併抓取車站
- **城巴**：官方冇「全量車站」接口，採按需抓取；想讓附近車站涵蓋城巴，去「設定」執行**深度索引城巴**（逐條路線抓 route-stop 與座標，約數分鐘，建議 Wi-Fi 下執行）
- **專線小巴**：路線清單全量，車站喺點開路線時抓取（座標需額外請求，故唔納入附近搜尋）
- **港鐵巴士**：唔使索引，輸入 K 字頭路線號即即時向官方接口查詢
- **港鐵重鐵／輕鐵**：路線、站名、座標全部內置喺 `assets/rail.json`，**零網絡請求**就完成索引，附近車站即刻用到

索引會存成 `filesDir/hketa_index.json`，下次啟動直接讀取；可喺「設定」隨時重建。

## 專案結構

```
app/src/main/java/com/hketa/app/
├─ data/       Models、ApiModels、Http、IndexStore、EtaRepository、RailData、TimeUtil
│              （assets/rail.json = 內置鐵路站表）
├─ location/   定位封裝（Fused Location，無 GMS 時退回 LocationManager）
├─ vm/         AppViewModel（索引狀態、搜尋、沿途車站、預報、附近車站）
└─ ui/         AppNav 與各頁面（搜尋／鐵路／沿途車站／預報／附近／設定）
```

技術棧：Kotlin 2.0.21、Jetpack Compose（Material 3）、Navigation Compose、
OkHttp 4.12 + kotlinx.serialization、Coroutines/Flow + AndroidViewModel、
Play Services Location，minSdk 26（Android 8.0）／targetSdk 35。

## 已知限制

- 尚未實作：桌面 Widget、收藏車站、路線地圖
- 重鐵接口只支援官方列明嘅 10 條線；輕鐵只列 11 條常規線（特別班次 9xx 唔單獨列出，但仍會喺預報入面出現）
- 「附近車站」完整度取決於已建立嘅車站索引：鐵路站與九巴最完整，城巴同專線小巴要去「設定」跑深度索引
- NLB / GMB / 港鐵巴士接口偶有欄位調整，解析已做寬鬆容錯，但仍以官方文件為準

## 隱私

只向上述官方接口發出 HTTPS 請求，唔收集、唔上傳任何個人資料；
定位只用於當下排序附近車站，唔會儲存或傳送。
