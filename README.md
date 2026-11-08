# HKATE — 香港巴士到站預報（原生 Android App）

**繁體中文** | [English](README_EN.md)

把原本 `benyeung08/etaapp`（WebToApp fork，本質係「把網頁包入 WebView」嘅殼）完全換成真正嘅原生 App：
Kotlin + Jetpack Compose，直接對接香港各營辦商嘅公開 ETA 接口，**主介面無網頁外殼**；
只有「沿途車站 ▸ 顯示地圖」用 WebView 載 Leaflet 底圖（失敗會退回 Canvas 示意圖）。

> 本倉庫已完成原生重寫：`app/src/main/java` 全部換成 `com.hketa.app`，
> 原本 WebToApp 嘅 `assets/`、`cpp/`、`proto/`、`schemas/` 同 WebView Activity 已全數移除。

## 與原專案嘅差別

| | 原 etaapp（WebToApp fork） | 本專案 HKATE |
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
- **三語介面**：設定頁可隨時切換「跟隨系統／繁體中文／简体中文／English」，唔使重裝
- **App1933 風格主頁**：打開就係附近車站嘅即時到站預報，格式「路線 往 目的地　X 分鐘」，站名附官方站號
- **收藏車站**：預報頁撳心形圖示收藏，「收藏」分頁一次過 refresh 晒全部收藏站嘅 ETA
- **版本更新**：設定頁可檢查更新，直接由 GitHub Releases 讀取最新版並下載 APK

## 介面語言

App 介面支援 **繁體中文**、**简体中文** 同 **English**，喺「設定」頁頂部揀就得：

| 選項 | 資源目錄 | 行為 |
|---|---|---|
| 跟隨系統 | — | 跟住手機系統語言（預設）。系統係 zh-CN 就出簡體、zh-TW/HK 出繁體、其他英文語系出英文 |
| 繁體中文 | `values/` | 固定繁體中文 |
| 简体中文 | `values-zh-rCN/` | 固定簡體中文 |
| English | `values-en/` | 固定英文 |

實作上用 `AppCompatDelegate.setApplicationLocales`：

- **Android 13（API 33）以上**：寫入系統嘅「應用程式語言」設定，喺系統設定入面都改到
- **Android 12 及以下**：由 AppCompat 自己保存（Manifest 已註冊 `AppLocalesMetadataHolderService` + `autoStoreLocales=true`），重啟後依然生效

所有介面文字都放喺四份資源檔：

| 檔案 | 語言 |
|---|---|
| `res/values/strings.xml` | 繁體中文（同時係冇對應語言時嘅預設） |
| `res/values-zh-rCN/strings.xml` | 简体中文 |
| `res/values-en/strings.xml` | English |
| `res/values-b+zh+Hans/strings.xml` | 简体中文（BCP 47 書寫體，覆蓋 zh-SG 等） |

四份 key 完全對齊（各 **338** 條）。切換語言時會觸發 configuration change，
Compose 自動 recompose，唔使重啟 Activity。

到站預報嘅備註（月台、服務延誤、經馬場、幾多卡）係資料層即場拼出嚟嘅，
由 `AppViewModel` 讀好當前語言嘅模板再傳畀 `EtaRepository`（見 `data/EtaText.kt`），
所以簡體介面下會顯示「经马场、服务延误」而唔係繁體原文。

車站名、路線名等**來自官方接口嘅資料維持原樣**（官方多數只提供中文），唔會硬翻。

## 介面結構（參考 App1933）

底部導覽五頁：**主頁 / 收藏 / 搜尋 / 設定 / 更多**（v5 起新增「更多」分頁）

| 分頁 | 內容 |
|---|---|
| 主頁 | 定位 → 800 米內車站 → 每站列出停靠路線嘅即時預報（九巴一個請求攞晒全站） |
| 收藏 | 已收藏車站，進入即並發拉取全部 ETA |
| 搜尋 | 路線號／車站名搜尋；頂部有「鐵路」「附近」入口 |
| 設定 | 索引管理、語言、版本更新、資料來源 |
| 更多 | 車費、點對點規劃、瀏覽歷史、自訂收藏集、路線改動公告、深色／字型大小（v5 新增） |

**與 App1933 嘅差異（唔會做）**：club1933 會員、eCoin 錢包、九巴月票、遊戲室、
bot1933 AI 客服、實時載客量、路線地圖 —— 呢啲要九巴私有 API 或商戶資質，
本專案只用政府公開資料（data.gov.hk 系列），所以唔提供。

## 版本更新機制

App 內「設定」頁有「檢查更新」，會打 GitHub Releases API：

```
GET https://api.github.com/repos/benyeung08/hketa/releases/latest
```

- 對比最新 Release 嘅 `tag_name` 同 App 裝咗嘅版本（由 PackageManager 讀，自動跟 `versionName`）
- 有新版本就顯示版本號、更新內容，並提供「下載更新」（直連 APK）同「查看 Release」。
  下載後依次校驗 **SHA-256**（對比 Release 入面嘅 `<apk>.sha256`）同 **簽名證書**（比對已安裝 App），
  任何一關唔過都唔會安裝；校驗檔攞唔到（非 404）會直接中止，唔會 fail-open 降級。
- 進入設定頁會靜默檢查一次（30 分鐘節流），撳掣可以即時再查
- 仲未發布過 Release 會顯示「尚未發布正式版本」，唔會當錯誤

**所以要發新版**：改 `app/build.gradle.kts` 嘅 `versionName`，然後 push `v*` 標籤，
CI 會自動建 APK 並掛上 Release —— 用戶開 App 就查到。

## 拎 APK（最快方法）

本倉庫附咗 `.github/workflows/build-apk.yml`：

1. 將呢個專案 push 上你嘅 GitHub repo（main 分支）
2. 去 repo 嘅 **Actions** 頁，揀 **Build HKATE Release APK**，撳 **Run workflow**
3. 跑完之後喺該次執行嘅 **Artifacts** 下載 **`hkate-release-apk`**（只有 release APK，連 `.sha256` 校驗檔）
4. 傳到手機安裝即可（首次安裝允許「未知來源」）

push `v*` 標籤嘅話，APK 會自動掛到 Release 上。

## 本機建置

需求：**JDK 17**、Android SDK（**platform 37** + **build-tools 36.0.0**）、**Gradle 9.3.1**（wrapper 已指向呢個版本）。AGP 9.1.1 官方相容表要求呢組版本，少一樣都認唔到 compileSdk 37。

```bash
# 首次先產生 wrapper 腳本（倉庫唔 commit gradlew，避免二進位檔走樣）
gradle wrapper --gradle-version 9.3.1

# 本機簽名：寫入 local.properties（已被 .gitignore 忽略）
#   signing.storeFile=/絕對路徑/outside-repo/hketa-release.jks
#   signing.storePassword=***
#   signing.keyAlias=hketa
#   signing.keyPassword=***
# CI 對應 Secrets：HKETA_KEYSTORE_B64 / HKETA_STORE_PASSWORD /
#                  HKETA_KEY_ALIAS / HKETA_KEY_PASSWORD
# 冇外部 key 會拒絕出 release 包（唔會靜靜用 debug key 簽）。

./gradlew :app:testDebugUnitTest   # 先跑單測
./gradlew :app:assembleDebug       # → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease     # → app/build/outputs/apk/release/app-release.apk
```

用 Android Studio 開（Ladybug 以上）：File ▸ Open ▸ 選擇本資料夾 → Gradle Sync → Run ▶。
如果 Studio 報 Gradle wrapper 相關錯誤，喺 repo 根目錄執行 `gradle wrapper --gradle-version 9.3.1`
重新生成，或者喺 Settings ▸ Build Tools ▸ Gradle 指定本機 **Gradle 9.3.1**、Gradle JDK **17**。

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
├─ util/       AppLocale（介面語言切換：繁／簡／英／跟隨系統）
├─ vm/         AppViewModel（索引狀態、搜尋、沿途車站、預報、附近車站）
└─ ui/         AppNav 與各頁面（搜尋／鐵路／沿途車站／預報／附近／設定）

res/
├─ values/strings.xml          繁體中文介面文字（預設）
├─ values-zh-rCN/strings.xml   简体中文介面文字
├─ values-en/strings.xml       英文介面文字
├─ values-b+zh+Hans/strings.xml  簡體（BCP 47 書寫體）
└─ drawable-*/mipmap-*/        自適應圖示（前景 + 背景）
```

技術棧：Kotlin 2.2.10（AGP 9.1.1 內置）、Jetpack Compose（Material 3）、
Navigation Compose、OkHttp 4.12 + kotlinx.serialization、
Coroutines/Flow + AndroidViewModel、Play Services Location。

**支援範圍（v1.2.21）：Android 8 – 17**

| 項目 | 值 | 對應系統 |
|---|---|---|
| `minSdk` | **26** | Android 8.0 Oreo（下線） |
| `targetSdk` | **37** | Android 17 Cinnamon Bun（上線） |
| `compileSdk` | **37** | Android 17 |

即係 **Android 8、9、10、11、12、12L、13、14、15、16、17 全部裝到**。

**工具鏈硬門檻**：`compileSdk 37` 要求 **AGP ≥ 9.1.1 + Gradle ≥ 9.3.1 + Build Tools 36.0.0 + JDK 17**，
少一樣都認唔到。CI 已一併升級。

**兩點必須知道嘅配套改動**

1. **AGP 9 內置咗 Kotlin** —— 唔可以再寫 `id("org.jetbrains.kotlin.android")`，
   否則會報 `Cannot add extension with name 'kotlin'`。Kotlin 版本由 AGP 決定（9.1.1 → KGP 2.2.10）。
2. **minSdk 26 唔使開 desugaring** —— 項目用咗 `java.time`（`TimeUtil`），
   Android 8.0 起系統本身就有呢個 API；開咗反而白白加大 APK。
   （若日後要降到 Android 7 或以下，就必須開 `coreLibraryDesugaring`。）

**Android 17 行為變更**：大屏裝置（sw > 600dp）上系統會忽略 `screenOrientation`，
App 必須自適應任何視窗尺寸。Manifest 保留 `portrait`：
手機上仍然生效（保持直向），大屏則由系統自動轉為可調整大小 —— 版面用 Compose + 捲動，任何尺寸都唔會爆。

## 已知限制

### 已經解決嘅限制

| 原本嘅限制 | 而家嘅做法 |
|---|---|
| 桌面 Widget 未實作 | **已加**：`widget/HketaWidgetProvider`（RemoteViews，零新依賴）。桌面加入後顯示收藏車站嘅下一班車，撳同步圖示即時 refresh；系統每 30 分鐘亦會自動更新一次 |
| 收藏車站未實作 | **已加**：預報頁撳心形收藏；「收藏」分頁並發拉晒全部收藏站嘅 ETA；資料存本地 SharedPreferences |
| 路線地圖未實作 | **已加**：沿途車站頁「顯示地圖」，用 Canvas 按車站經緯度畫走線（頭尾站換色）。零依賴、唔使 API key；冇座標嘅路線會如實提示 |
| 英文介面下車站名仍係中文 | **已修**：改讀官方 `name_en`（九巴、城巴、專線小巴接口都有）。英文介面下直接用官方英文站名，同官方站牌對得上 |
| 鐵路線名得中文 | **已修**：重鐵 10 條線補咗官方英文名（Tuen Ma Line、Island Line…），寫喺 `rail.json` 嘅 `nameEn` |
| `zh-SG` 等簡體語系會跌落繁體 | **已修**：新增 `values-b+zh+Hans/`（用 BCP 47 書寫體判別），唔睇 region，任何簡體語系都命中簡體 |
| 附近淨係得九巴有 ETA | **已改善**：主頁對城巴／嶼巴／小巴站會查頭一條路線嘅 ETA（只查一條，慳請求），唔再淨係列路線號 |
| 鐵路站名冇英文 | **已修**：`rail.json` 內置咗官方英文站名（重鐵 98 個 + 輕鐵 68 個，全部齊）。英文介面下顯示官方英文名（Hong Kong、Tuen Mun Ferry Pier…），唔係自行翻譯 |
| 輕鐵特別班次（9xx） | **已修**：「鐵路」分頁加咗「掃描特別班次」。官方冇特別班次清單，所以掃描各站預報、減走常規 11 條線，動態搵返出嚟（唔使硬編碼 9xx，官方加減班次都跟到） |
| 地圖係示意圖 | **已修**：沿途車站頁「顯示地圖」改用 WebView + Leaflet + CARTO 深色瓦片，**有底圖、有街道、可縮放撳站名**，而且唔使 API key、唔使引入 Maps SDK。載入失敗（冇網／CDN 唔通）會自動退回示意圖，唔會留低空白 |
| 城巴／小巴修復一定要 Wi-Fi | **已改善**：深度修復改咗**分批並發**（每批 8 條）兼**增量**（跳過已抓過嘅路線），時間同請求數都大幅減少，唔再一定需要 Wi-Fi |
| 未授權定位就冇嘢睇 | **已修**：加咗「手動選點」。唔想授權定位嗰陣，可以自己揀一個區／交通樞紐（16 個預設），主頁照樣用嗰個座標搵附近車站 |
| Widget 唔查城巴／嶼巴／小巴 | **已修**：之前唔係因為「要逐條路線查」，而係**收藏嗰陣根本冇記低路線**。而家收藏會記低路線（預報頁記當前路線、主頁記最快嗰班），Widget 淨係查嗰一條（1 個請求），**全部營辦商都支援** |
| 城巴／小巴要人手撳修復 | **已修**：索引一建好就**自動喺背景補**（增量 + 並發、唔彈訊息、唔阻住用）。做過一次就記低，唔會每次開 App 重做。想手動再跑都可以，掣仲喺度 |
| 輕鐵特別班次要人手撳掃描 | **已改善**：掃描結果**存落本地快取**（`filesDir/hketa_lr_special.json`）。掃一次之後開鐵路頁即刻顯示，唔使每次掃；想更新先再撳掣 |
| 睇過嘅地圖離線變空白 | **已改善**：WebView 改咗 `LOAD_CACHE_ELSE_NETWORK`。睇過嘅路線（Leaflet + 瓦片）有快取，**離線都出到圖**；完全未睇過嘅先要上網 |
| 地圖離線靠唔穩嘅 WebView 快取 | **已修**：新增 `data/TileCache.kt` **自己管離線瓦片包**。顯示地圖時自動喺背景預取 zoom 12–16 嘅瓦片存落 `filesDir/hketa_tiles/`，WebView 攞瓦片時由 `shouldInterceptRequest` 攔截、本地有就用本地 —— **睇過一次嘅路線，飛機模式都出到真實地圖** |
| 嶼巴冇英文站名 | **已修**：官方接口冇 `name_en`，所以內置咗 `data/NlbStopNames.kt` —— 約 90 個大嶼山站名嘅**官方英文對照**（政府地名 + 嶼巴站牌英文名），**唔係機器翻譯**。查唔到一律維持中文，唔會亂砌 |
| 輕鐵特別班次要人手撳掃描 | **已修**：開「鐵路」分頁會**自動喺背景掃**（快取 7 日，過期自動重掃），唔使撳掣都有；手動即時重掃嘅掣照樣保留 |

### 仍然存在嘅限制

- **嶼巴英文名只覆蓋主要站**：內置約 90 個大嶼山常用站名嘅官方英文對照，
  冷門村站（例如深山小路嘅小站）可能查唔到 —— **查唔到就維持中文**，唔會亂砌一個似是而非嘅英文名。
  九巴／城巴／小巴／鐵路全部用官方 `name_en`，只有嶼巴靠呢份表。
- **真實地圖第一次要聯網**：預取係「睇嗰陣先下載」，所以**第一次睇某條路線**仍然要上網攞瓦片同 Leaflet；
  睇完之後就有離線包，飛機模式都出到。要「未睇過都離線」就要預先打包成個大嶼山／全港瓦片，APK 會大好多，所以暫時唔做。
- **輕鐵特別班次結果有時效**：掃描結果快取 7 日，反映嘅係「掃描嗰刻」有冇呢個班次；
  官方冇特別班次清單介面，所以只能靠掃描得知。
- **附近車站完整度**：自動背景修復做完之後城巴／小巴都會納入，但係**第一次建索引嗰陣**會先出九巴，城巴／小巴要等背景修復跑完（通常一兩分鐘）。

## 隱私

ETA 資料全部經 HTTPS 向上述政府／公共接口取得，唔收集、唔上傳任何個人資料；
定位只用於當下排序附近車站，唔會儲存或傳送。

實際會連到嘅域名（如實列出）：
`data.etabus.gov.hk`、`rt.data.gov.hk`、`data.etagmb.gov.hk`、`static.data.gov.hk`、
`data.hkbus.app`、`h2-app-rr.hkemobility.gov.hk`、`api.github.com`（檢查更新）、
`a.basemaps.cartocdn.com`（地圖瓦片）。
Leaflet JS／CSS 已打包入 APK `assets/hketa_offline/`，**唔再連 `unpkg.com`**。
`allowBackup="false"`，崩潰記錄只寫私有目錄並保留最近 5 次。

## 與 hkbus.app/zh 嘅功能對齊（v5）

保留原生 Compose 架構，**唔係 WebView 包網頁**。底部導覽新增「更多」分頁，一次過補入
之前對照缺嘅六類功能：

| 功能 | 做法 | 資料來源 |
|---|---|---|
| 車費 | 由現有 hkbus 聚合檔多抽 `fare` 欄位；容錯食 array／object／primitive | `data.hkbus.app/routeFareList.min.json` |
| 點對點規劃 | 純 Kotlin 反向索引，直達 + 一次轉乘，靠 `seq` 判斷方向 | 本機 `hketa_index.json`，零網絡 |
| 瀏覽歷史 | 自動記低開過嘅路線／車站（最多 30 筆） | 本地 SharedPreferences |
| 自訂收藏集 | 分組 + 勾選加入／移出，删收藏自動 prune | 本地 SharedPreferences |
| 路線改動公告 | 三個開源來源逐一試，失敗顯示「暫無」唔崩 | hkbus 開源 `public/notice.json` |
| 深色／字型 | 跟隨系統／淺色／深色 + 0.85–1.40× 字型 | 本地設定，即刻重組 |

另外主介面文字由 338 增至 **388** 個 key，四語（繁／簡 region／簡 BCP47／英）完全對齊；
新增 6 個純 JVM 單元測試驗規劃演算法（直達、一次轉乘、無路線、同站、空索引、倒頭車）。

**誠實邊界**
- 點對點只做「直達 + 一次轉乘」，唔做多段轉乘／行車時間優化／轉乘優惠，
  同 hkbus.app 後端完整 journey planner 唔同等。
- 公告來源 URL 要實測；全部唔通就顯示「暫無改動公告」，唔會阻塞出包。
- 深色模式只切 Material3 配色；部分舊頁面仍有 hardcoded 深色底（RouteMap 底圖、
  少數卡片），唔會跟住轉，屬既有技術債。
- 車費只作參考，實際以營辦商為準。

### Rust 索引內核（可選加速層）

`rust/` 編譯出 `libhketa_store.so` 落 `app/src/main/jniLibs/{abi}/`，四個 ABI
（armeabi-v7a／arm64-v8a／x86／x86_64）都要齊。依賴由 **`rust/Cargo.lock`**
鎖定，CI 同本地一律用 `--locked`，並跑 `cargo audit`；NDK 必須 **r27+** 以
確保 16 KB page 對齊。呢幾關任何一關唔過，release build 會直接失敗，唔會
再出一個「編譯成功但喺 Android 17 載唔入、靜默退化純 Kotlin」嘅 APK。
第一次設好：`./rust/build-android.sh` → commit `Cargo.lock` + 四個 `.so`。
