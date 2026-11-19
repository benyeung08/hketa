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

- **🚌 APP1933 版面**：大字路線號＋「往 目的地」＋站名連編號＋右側大數字分鐘；底部 **主頁／收藏／搜尋／應用程式設置** 四分頁，**居中深灰黑藥丸導航欄**（56dp 高、按屏寬收窄、藍字選中）跟隨系統外觀
- **🗺 點卡片開路線詳情**：紅色頂欄＋站序時間軸＋未來三班次，**完全冇地圖**；車資／預計落車時間如實顯示「—」
- **🔍 搜尋主頁預載路線**：未輸入已列出官方目錄列；點 `12 往 海麗邨 ›` 直接開同一個路線詳情頁（帶方向）
- **⟳ ETA 自動刷新**：主頁附近路線卡片、已選車站、收藏、路線詳情全部前景定時更新；間隔 15／30／45／60 秒可揀、可關掉，唔重新定位、唔背景輪詢
- **🏷 正式版 APK 對應 Releases 版本**：`v1.0.0` tag 只會附 `hketa-v1.0.0-release.apk`；`tag ↔ build.gradle.kts ↔ 實際 APK` 三重校驗唔夾即擋低發布，並附 `VERSION_INFO.txt`／`SIGNING_FINGERPRINT.txt`
- **⬛ 路線詳情全頁文字純黑**：詳情頁係固定白底，站名**同所有原本同色嘅輔助文字**（車資、落車、分鐘單位、時鐘、標記、路線示意標籤、頁底說明）一律改純黑 `#000000`；`values-night` 保持同一純黑值，頁底背景亦釘死淺色避免黑字落黑底。深色模式唔會再變近白色睇唔到；紅色頂欄／chip 白字同藍色分鐘數字唔變
- **🚌 聯營路線顯示「城巴/九巴」**：同一路線號同時出現喺九巴／龍運同城巴兩份官方目錄先判定為聯營（**資料驅動，冇硬編路線號清單**）。主頁附近卡片、搜尋／預載結果、詳情頁「路線示意」一律顯示 `城巴/九巴`；KMB＋CTB 兩列合併成一張卡，未來三班次逐班標 `該班次：城巴`／`該班次：九巴`。主頁會按中文站名把同一實體站嘅兩家 ETA 先併埋（配唔到名就維持各自一張，行為唔變）；**聯營站名統一用九巴／龍運站名同站號**（詳情頁站序去重、主頁卡片優先用 KMB 嗰個 StopOption；完全冇 KMB 目錄先如實退回城巴名）；詳情頁只展開 KMB＋CTB 兩家，唔會撈埋其餘 5 種模式
- **🚌 只有城巴路線用城巴站名（CTB_ONLY_NAME）**：純城巴（非聯營）路線嘅站序**完全唔會**經過九巴改名邏輯，一律保留城巴官方站名同站號；聯營詳情頁入面**城巴獨有、冇九巴對應**嘅站亦保留城巴名（唔會再抄九巴用字）。主頁合併 bucket 入面，`App1933Format.resolveStopDisplay()` 逐張卡揀站名：`CTB` 卡用城巴 `StopDisplay`、`JOINT` 卡仍用九巴名（上一輪要求）、`KMB` 卡用九巴名，其餘公司沿用原本 `StopOption`。**即係只有城巴嘅卡／站先改為城巴站名，聯營同九巴唔受影響**
- **🔄 循環線各自顯示（CIRCULAR_ROUTES）**：循環線由**官方資料**判定 —— `dest_tc` 帶 `(循環線)`／`(Circular)`，或 route-stop 只有單一方向（冇 inbound）且首尾站相同，**冇硬編路線號清單**。只有循環線先套用「各家用自己站名」：城巴循環線用城巴站名＋城巴站號、九巴循環線用九巴站名＋九巴站號；**普通路線規則完全唔變**（聯營仍用九巴名、純城巴用城巴名）。循環線**唔會被合併成 `城巴/九巴` 聯營列**
- **🔄 顯示方式＋站名核對**：九巴方式 `213X 往 尖沙咀(循環線)`（App1933，保留「往」）；城巴方式 `26 勵德邨 - 荷李活道(循環線)`（唔加「往」）；卡片／搜尋列／詳情頁都有 `九巴循環線`／`城巴循環線` badge。`CircularRoutes.audit()` 對照本機官方站名表，回報 `同官方一致／不一致／缺失／疑似混用`，**冇對照表時如實講「未載入」，唔會宣稱全部正確、亦唔會捏造或自動改官方名**
- **🚌 城巴路線用城巴站名（CTB_STOP_NAME）**：同一個實體附近站可以同時有九巴同城巴兩份 `StopOption`，主頁合併 ETA 後**每張卡按自己所屬公司**顯示官方站名同站號 —— 純城巴卡顯示城巴站名／城巴站號，純九巴卡顯示九巴名，聯營卡仍按上一輪用九巴名。詳情頁標題跟返張卡嘅名，純城巴站序一律用 `safeNames(CTB)`，唔會被九巴名污染
- **未來三班次**：標記 `-`（剛到站）、`—`（沒有服務）、「預定班次」、「改道」、聯營公司；取代舊嘅單班次列表
- **路線站序＋方向切換**：輸入路線號列出沿途車站（含站編號），一鍵 `⇄` 切換去程／回程，並可即時篩選站名／站號
- **8 種查詢模式**：自動（全部）、九巴／龍運、城巴、嶼巴、綠色小巴、港鐵重鐵、輕鐵、港鐵接駁巴士
- **實時到站**：中文站名、預計分鐘／預計時間／備註，每 30 秒自動更新、下拉刷新
- **📍 附近車站定位**：**50–2000 公尺半徑喺「應用設置」揀**，按距離排序；**取消手動定位掣與「↻ 刷新」掣**，取得權限後自動定位、下拉頁面刷新，點站即睇該站全部路線 ETA
- **⭐ 收藏常用站**：連 `bound` 方向一併還原
- **⚙️ 線路資料更新**：手動／每日／每 3 日／每星期、僅 Wi-Fi、清除本機資料；10 份官方目錄原子寫入，單一來源失敗保留上一版
- **🔄 更新應用程式**：讀取 GitHub 最新 Release、24 小時節流、下載後經 `FileProvider` 交系統安裝
- **🔐 簽名一致性**：固定開發金鑰、CI 指紋鎖定、安裝前比對簽章憑證
- **🚌 彩虹巴士圖示**：5 種密度 launcher icon、專屬圓形圖示、Android 8+ adaptive icon、512px 商店預覽圖

### 📦 安裝

1. 由 [Releases](https://github.com/benyeung08/hketa/releases) 嘅**對應版本**（例如 `v1.0.0`）下載 **`hketa-v1.0.0-release.apk`**（正式版、已簽名）；或由該次 Actions run 下載 Artifact `hketa-release-apk`（tag build）／`hketa-debug-apk`（開發版）
2. Android 8+ 首次安裝需開啟「安裝未知應用程式」
3. 覆蓋安裝會保持簽名一致；若裝置曾裝過**其他金鑰**簽嘅版本，請先卸載
4. 可對照隨附嘅 `SIGNING_FINGERPRINT.txt` 同 `VERSION_INFO.txt` 核對憑證、APK SHA-256 同版本

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

推送到 `main` 後，`.github/workflows/build-apk.yml` 會自動打包並上傳 Artifact `hketa-debug-apk`。打 `v*` tag 先會組裝**正式版（signed release）APK**並發布到該版本嘅 Release：

```bash
# 1) 先 bump 兩個版本欄位
#    app/build.gradle.kts: versionCode = 2 / versionName = "1.0.1"
git add app/build.gradle.kts && git commit -m "Bump to 1.0.1" && git push origin main
# 2) tag 必須同 versionName 對應
git tag v1.0.1 && git push origin v1.0.1
```

### 🏷 正式版 APK 與 Releases 版本對應

每個 tag build 都會跑 **`RELEASE_VERSION_GATE`**，保證 Release 入面嗰個 APK 真係嗰個版本：

- **三重校驗**：`tag`（`v1.0.1`）＝ `app/build.gradle.kts` 嘅 `versionName` ＝ **`aapt dump badging` 讀到嘅實際 APK `versionName`**；另外 `versionCode`、`package`、`minSdk`、`targetSdk` 亦要比對。任何一項唔夾，workflow **喺建立 Release 之前直接 fail**，唔會上傳錯版本 APK
- **只附一個 `.apk`**：`hketa-v1.0.1-release.apk`（正式版、已簽名）。App 內更新會攞最新 Release 嘅第一個 `.apk` asset，所以開發版永遠唔會混入 Release
- **隨附兩份清單**：`VERSION_INFO.txt`（tag／versionName／versionCode／minSdk／targetSdk／APK SHA-256／簽章指紋／keystore 來源／commit／構建時間）同 `SIGNING_FINGERPRINT.txt`
- **說明對應版本**：`v1.0.0` 自動用 `RELEASE_NOTES_v1.0.0.md` 做 Release 內文；未有專屬 notes 嘅新版退回簡短說明，**唔會誤用其它版本**；`tag_name`／Release 標題顯式綁定 `github.ref_name`，tag 帶 `-` 後綴會標成 prerelease
- **同時上傳 Artifact** `hketa-release-apk`，方便喺 Actions 直接拎正式版；`generate_release_notes` 改為 `false`，想再加 GitHub 自動 "What's Changed" 可改回 `true`

`AppUpdate.compareVersions()` 比較 `tag_name` 同已裝版本嘅 `versionName`；呢個閘門正正令 `v1.0.1` ↔ `1.0.1` 成立，唔會出現「tag 新但 APK 舊」嘅假更新。

### ⚙️ 設定頁

底部第四個分頁已由「更多」改名 **「應用程式設置」**，入面係六個獨立區塊：**定位附近路線嘅距離**（50–2000 m）、**ETA 自動刷新**、**線路資料更新**（只快取目錄，ETA 永遠即時查）、**更新應用程式**（內建 repo `benyeung08/hketa`）、**App 簽名一致性**、**導航欄外觀**；頁底係對照截圖嘅深色免責聲明卡。

### 🔄 更新應用程式（版本膠囊）

對照截圖把整區改成一條深色圓角 bar：`v1.0.0 · 1　⟳　🕐　⧉`。

- **左邊兩組數字**：`v1.0.0` 係 `versionName`（統一補 `v` 前綴），`1` 係 `versionCode`／build number，全部由 `PackageInfo` 動態讀，唔寫死
- **⟳ 重新檢查**：即時打 `api.github.com/repos/{owner}/{repo}/releases/latest`；檢查中圖示變半透明，完成後更新下面嗰行極細狀態
- **🕐 更新記錄**：唯讀對話框，列出目前版本／build、發布來源、自動檢查開關、上次檢查時間（HKT）、快取版本同狀態
- **⧉ 複製**：把 `v1.0.0 · 1 · benyeung08/hketa` 放入剪貼簿並 Toast，方便回報問題
- **功能冇砍**：repo 輸入、`自動檢查`、檢查／下載並安裝／Release 頁、進度條、Release 說明、24 小時節流、`FileProvider` 安裝同**簽名一致性驗證**全部保留，收喺「⌄ 進階設定」抽屜；發現新版本或開始下載會自動展開
- **三個圖示係 vector drawable**（線條白色、圓角深灰底），唔用 emoji，唔受裝置字型影響

### 📍 距離設定與下拉刷新

- **主頁「↻ 刷新」掣已移除**：只保留 `SwipeRefreshLayout` 下拉頁面刷新；已點選車站嘅 ETA 仍然每 30 秒自動更新
- **主頁半徑 Spinner 已移除**：「定位附近路線嘅距離」搬入 ⚙ 設定頁第一個區塊，主頁唔再有任何下拉或按鈕
- **對照截圖「50 ▼」**：深色圓角 bar、左邊大號白色數字＋細 `m`、右邊灰色 ▼；按成條 bar 彈單選對話框
- **7 檔半徑**：`50 / 100 / 200 / 300 / 500 / 1000 / 2000 m`，預設 `500 m`；`50 m` 係為咗對應截圖而加，數值由 `NearbyRadiusPrefs` 統一存
- **改完即生效**：`MainActivity.onResume()` 比對 `lastRadius`，由設定頁返嚟會用新半徑自動重新定位，唔使手動刷新

### ⟳ ETA 自動刷新

之前只有「已點選車站」硬編碼每 30 秒更新一次，主頁附近路線卡片同詳情頁唔會自己郁；而家全部可見 ETA 都由同一個前景輪詢統一刷新。

- **一個 loop 管晒**：`EtaViewModel` 按設定間隔呼叫 `refreshVisibleEta()`，間隔由 `EtaRefreshPrefs.intervalMs()` 讀，唔再寫死 `delay(30_000L)`
- **四個畫面一齊更新**：主頁附近路線卡片（重用已快取嘅 6 個站，唔重新定位）、已選車站／收藏、路線詳情頁上車站、站序模式嘅 ETA
- **設定頁**：「自動刷新到站時間」開關（預設開）＋對照「30 ▼」嘅深色大數字選擇器，15／30／45／60 秒；關掉時 picker 變灰，改完返主頁 `onResume()` 即時重啟輪詢
- **狀態列**：主頁同詳情頁頁底顯示 `⟳ ETA 自動更新 · 每 30 秒 · 15:42 已更新`；關掉就顯示「已關閉 · 可下拉頁面手動刷新」
- **只喺前景**：`onResume()` 重啟並喺已過一個完整間隔時補一次即時 ETA，`onPause()` 即刻停；**無背景 Service／WorkManager**，離開 App 唔會靜默打官方 API
- **唔重複、唔空轉**：未載入資料或未獲定位權限時直接 return 唔打 API；附近卡片刷新只對快取站點重新查 ETA，唔重新下載站點清單，下拉手動刷新仍然可用

### 🗺 主頁卡片 → 路線詳情（無地圖）

撳主頁「附近路線」任一張卡片，會開一個**全螢幕路線詳情**，還原截圖嘅紅色頂欄同下半部站序，**但完全冇地圖**。

- **紅色頂欄**：`←` 返回＋`12 往 尖沙咀東（麼地道）(YT927)`（路線／目的地／站號）＋三個白圓動作 `▦` 刷新三班次、`★` 收藏、`⇄` 轉換方向
- **唔載入地圖**：原本 Google 地圖嗰格換成一條淺色紙面＋紅色路線＋站名氣泡，純 XML drawable，**無地圖 SDK、無圖磚、無網絡地圖請求、無 Google 署名**
- **左側四個 chip 全部有實際功能**：上車（選第一站）／鄰近（返主頁）／收藏／更多（開應用程式設置）；唔做假掣
- **站序時間軸**：`1. 站名 (站號)`、紅點／灰圈、紅色直線；只有「上車」站展開**未來三班次**（大藍色分鐘＋時間＋`預定班次`／`剛到站`／`改道`／`沒有服務`標記），資料由 `App1933Format.cardsFrom()` 嚟
- **誠實佔位**：`車資 —` 同 `落車 —` —— 公開 ETA API **唔包含車資同預計落車時間**，所以唔會捏造 `$5.6`／`15:31`，頁底有明確說明
- **資料層冇改**：站序用 `EtaViewModel.loadStops()`、ETA 用 `selectStop()`＋`repo.etaFor()`，30 秒自動更新照舊；呢頁**唔需要定位權限**
- 搜尋分頁嘅結果列仍然用原本嘅站內站序，唔受影響

### ◌ 懸浮液態玻璃導航欄

- **大小與位置（對照截圖，NAV_OVERLAY_POSITION）**：由 `64dp` 高、四邊 `12dp` 嘅近滿寬藍灰條，改為 **高 `56dp`、水平置中、左右各 `28dp`** 嘅深灰黑**藥丸**；圓角 `28dp`（＝高度一半，真 capsule），elevation 降到 `6dp`，描邊改極淡。**位置再改咗**：唔再係「貼底 `8dp` 嘅佔位列」，而係 `FrameLayout` 根布局上嘅**真正懸浮 overlay**（`layout_gravity=bottom|center_horizontal`），由 `NavGlass.geometry()` 按螢幕高度上移到**離底約 12%（48–160dp）**；所以藍色「安裝」掣會喺半透明膠囊後面透出，膠囊**下面仍然見到下一列內容**，同截圖一致。各捲動頁加咗 `180dp`／`200dp` 底部預留，最後一筆資料仍捲得到
- **按螢幕寬度自適應**：`NavGlass.geometry()` 每次 `apply()` 重算左右留白 —— 手機每邊約屏寬 `6%`（88% 闊，四個分頁仍放得落），平板／横屏 `≥600dp` 轉 `18%`（約 64% 闊，對應截圖嘅窄膠囊），夾喺 `16–256dp`；旋轉螢幕即時重新置中；**離底距離同時按螢幕高度重算**（`heightDp × 0.12`，夾喺 `48–160dp`），旋轉／平板都唔會變返貼底
- **選中態**：一個**內嵌深色 pill**（`#141416`、`24dp` 圓角）包住選中分頁，配**藍色字** `#4A9EFF`；其餘分頁淺灰 `#A8B0BA`，唔再係半透明白磨砂
- **跟隨系統設定**：`NavGlass` 讀 `Configuration.uiMode`（深色 → 更深藥丸）、`Settings.Secure "high_text_contrast_enabled"`（高對比 → 實色＋`2dp` 白邊）、`Settings.Global ANIMATOR_DURATION_SCALE / TRANSITION_ANIMATION_SCALE`（減少動態 → 收起陰影同模糊）；三種外觀共用同一 `28dp` 幾何
- **真背景模糊**：API 31+ 用 `Window.setBackgroundBlurRadius()`，全部 `runCatching` 包住；舊機／受限裝置退回純半透明 drawable，唔會 crash
- **即時更新**：`onCreate`／`onResume`／`onConfigurationChanged` 都會重新 `apply()`（連幾何）；喺系統設定改完或旋轉螢幕返到 App 就見到
- **用戶可覆蓋**：設定頁「導航欄外觀」有「跟隨系統外觀」開關（預設開），關掉只固定外觀，**大小與位置不變**，並顯示目前狀態
- **只改位置、唔改功能**：四個分頁、選中態、玻璃外觀、跟隨系統設定、全螢幕搜尋時隱藏導航欄全部保留；`JOINT`／ETA／簽名／更新等資料層一行都冇郁

### 🚌 APP1933 版面對照

| 分頁 | 還原咩 | 底層 |
| --- | --- | --- |
| **主頁** | 附近路線卡片：`路線號`／`往 目的地`／`站名 (KT123)`／右側大數字分鐘；每張卡三個班次 chip；**冇定位掣、冇刷新掣，授權後自動載入、下拉刷新** | `EtaRepository.nearbyStops()` ＋ `stopEta()` 並行查頭 6 個站 |
| **收藏** | 收藏車站列表，點選查看、長按移除 | `Favorites` |
| **搜尋** | 一入頁預載官方路線列、`12 往 海麗邨 ›` 結果列、自訂鍵盤；點列開**路線詳情（無地圖）**；站序模式保留 8 模式、`⇄` 方向、篩選、站號直接查 | `RouteSearch.preload()` / `search()` ＋ `RouteDetailActivity` |
| **應用程式設置** | ⚙ 設定入口＋六個區塊＋深色免責聲明卡 | `SettingsActivity` |

**只換介面，唔換資料**：`App1933Format.kt` 係純格式化層，把同一份 `EtaItem` 依 `(company, route, stop, dest)` 分組、取最早三班；`CardAdapters.kt` 只負責畫卡片。全部 8 個營運商、10 份官方目錄、`RouteDataStore`、`ApkSignature` 一行都冇改。

**明確不做**（需九巴私有 API／會員帳號／Play Services）：AI 智能問路、實時載客量、預計落車時間、九巴月票、club1933 會員、遊戲室、bot1933 實時對話、街景／步行導航、寵物巴士、廣告。**冇灰色佔位按鈕、冇假數據**。

### 🔍 搜尋頁（APP1933 全螢幕）

還原你提供嘅截圖版式，而唔係舊版「一個輸入框＋一堆按鈕」：

- **紅色標題列**置中「搜尋」，左下 `⌂` 返回主頁；全螢幕時隱藏共用標題與底部 4 分頁
- **置中「輸入路線號碼」**輸入框，配 **自訂鍵盤**：左邊 3×4 白色圓角數字鍵（1–9、0、`⌫`、`↵`），右邊白色圓形字母鍵（`A B / C E`）連 `▲▼` 換頁；**完全唔彈系統 IME**
- **一入搜尋主頁就預先載入路線**：原本深色空白＋「輸入路線號碼…」嗰格，而家由 `RouteSearch.preload()` 預載官方目錄列（最多 24 條），未撳任何鍵已經見到 `12 往 海麗邨 ›` 呢類結果；App 啟動時已開始背景下載，切去搜尋分頁即時有嘢
- 預載同樣只用官方目錄：九巴／城巴路線＋方向、嶼巴兩端、綠小巴路線號（標明「目錄預載（未查方向）」）、港鐵線路代碼表＋港鐵接駁巴士代碼表；**目錄完全讀唔到先顯示更新提示**，唔會捏造目的地
- 每按一鍵即時（180 ms debounce）改用 `RouteSearch.search()` 篩選，結果列係**大路線號＋「往 目的地」＋`›`**；嶼巴／綠小巴／港鐵等附細字 `大嶼山巴士 (資料由DATA.GOV.HK提供)`
- 輸入 `1` 會列出 `往 尖沙咀碼頭`、`往 竹園邨`、`往 大澳 - 嶼巴`、`往 梅窩碼頭 - 嶼巴`；精確符合優先、前綴符合最多 4 條；清空輸入會返去預載清單
- **點任一結果列 → 開同一個「路線詳情（無地圖）」全螢幕**（紅色頂欄＋站序時間軸＋未來三班次），並帶埋該列嘅 `O／I／1` 方向；同主頁附近路線卡片一致，`ALL` 會轉成「全部方向」
- 原有 8 模式選擇、站號直接查、方向切換、站名／站號篩選保留喺站序模式；站序模式按「← 返回路線搜尋」切回鍵盤＋預載清單
- 目的地全部讀 `RouteDataStore` 嘅官方目錄（本機快照優先、403 自動重試），**冇假數據、冇私有 API**；綠小巴方向會補一次官方 `/route/{region}/{route}`

### 📍 定位權限與自動定位

- **手動定位掣已移除**：主頁唔再有任何「重新定位」按鈕，唔使撳就自動做
- **先授權、後自動定位**：開 App／下拉刷新先檢查 `ACCESS_FINE_LOCATION`／`ACCESS_COARSE_LOCATION`；已授權即刻自動定位並載入附近路線
- **未授權唔假定位**：第一次會先彈系統權限對話框；拒絕期間清空卡片、顯示「⚠ 需要定位權限才能顯示附近路線 · 按此授予」可點提示，**唔會**靜默用中環預設座標打 API
- **雙重閘門**：`MainActivity.autoLocateIfAllowed()` 同 `EtaViewModel.loadNearbyRoutes()` 都 call 同一個 `LocationHelper.hasPermission()`；`onResume()` 由系統設定開啟權限返嚟會補做一次
- **30 秒輪詢維持喺已選車站**：附近路線喺開 App／授權後／下拉刷新／由設定返嚟自動載入；已點選車站嘅 ETA 仍然每 30 秒更新

### 🚏 城巴站名與 403 後備

實測問題：城巴站序只顯示 `002891` 呢啲純站號；設定頁報「已更新 8/11 個來源（部分失敗：九巴／龍運全量站點、城巴全量站點、綠小巴站點索引）」，log 係 `HTTP 403 @ https://data.etabus.gov.hk/v1/transport/kmb/stop => <html xmlns='http://www.w3.org/1999/xhtml'>`。兩個問題同源——全量站點快照冇建立，站名就查唔到。

- **城巴根本冇全量端點**：官方 v2 規格只有 `/stop/{stop_id}`，舊 code 打嘅 `/v2/transport/citybus/stop` 唔存在，所以必定失敗。已改成「按需快取」來源，唔再當成下載失敗。
- **單站 API 補名**：載入路線站序時，對冇名嘅站並行打官方單站 API（3 線程／12 秒上限／最多 30 站），取 `name_tc` 連 `lat/long` 寫入本機 `partial` 快照；下次離線都有名，附近站定位亦用返呢啲座標。
- **403／HTML 後備**：`HttpJson` 對每個 URL 試 app UA 同瀏覽器 UA、試 trailing-slash 變體，並把 HTML WAF 頁（就算 HTTP 200）當失敗；九巴全量站點改列「選用」，被 403 擋時自動改用單站 API。
- **匯報唔再誤報**：成功時寫「已更新 N 個來源 · 共 N 筆（另有 M 個選用／按需來源略過）」；逐個來源會標「選用／按需略過」而唔係「失敗」。

> 已用本地 HTTP server 模擬 `403 + XHTML`、`200 + HTML WAF`、官方單站 JSON：11 項功能檢查全過，包括「站序顯示中文名」同「403 全量站點變成 skip 而唔係 failure」。

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
├── .github/workflows/build-apk.yml   # CI：預檢、打包、簽章驗證、版本對應、Release
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
│   ├── pin_signing_fingerprint.py    # 把 keystore 指紋寫入 strings.xml
│   └── verify_release_version.py     # tag ↔ gradle ↔ APK badging 三重校驗
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
| `one-shot drift repair script exists and is idempotent` / `distiller warns when old stacktrace-shaped log is seen` | `scripts/fix_drift.py` 或 `scripts/show_build_error.py` 漏傳／仍係舊版 → **全量 push `scripts/` 七個 `.py`** |
| `tag 與 Gradle 版本唔對應` / `APK 內容與 Gradle 版本唔對應` | 正式版發布閘門擋低：先 bump `app/build.gradle.kts` 嘅 `versionCode`／`versionName`，push 後重新打 tag |
| `Release is explicitly tied to the matching tag/version` | checker 曾硬要求 `RELEASE_NOTES_v1.0.0.md` 存在，但 workflow 本身有 fallback —— **已修正為選用**；只需覆蓋 `scripts/check_api_fields.py` 一個檔。若 log 見到 `缺以下 N 項字串`，先再覆蓋 `.github/workflows/build-apk.yml` |
| `輔助文字未跟站名一齊改純黑` / `footer 黑底會令純黑字睇唔到` / `仍係深灰 #374151` | 全黑改動未同步：由完整 zip 覆蓋兩個詳情 layout ＋ `values/` 同 `values-night/` 嘅 `colors.xml`（共 4 個檔；合併包一次過包齊） |
| `Received status code 429 ... Too Many Requests`（例如 `jsr305-3.0.2.pom`） | **Maven Central 限流，唔係 code 錯、唔係上傳唔完整**：`setup-java` 已開 Gradle 依賴快取，兩個 assemble step 自動重試 3 次（30s／60s 退避）；若 3 次都失敗，等幾分鐘再 **Re-run jobs**，唔使改任何檔 |
| `JointRoutes.java 唔見咗` / `聯營會拆做兩張卡` / `班次 chip 無「該班次」` / `缺九巴站名優先邏輯` / `聯營站名未用九巴` / `導航欄仲係貼底佔位列` / `位置未改成按屏高上移` / `城巴卡會沿用九巴站名` / `附近站未傳各公司站名對照` / `城巴獨有站仍會被改成九巴站名` / `未逐張卡分辨城巴／聯營站名` / `循環線未標記` / `循環線站序仍會被九巴化` | 聯營／導航欄／城巴站名／循環線改動未同步：由完整 zip 覆蓋新檔 `JointRoutes.java` ＋其餘 8 個聯營相關檔（合併包一次過包齊），唔好逐個 upload |
| `Could not resolve` / `Read timed out` / 5xx | 同屬暫時性依賴下載失敗：直接 **Re-run jobs** |
| 仍然係 `at org.gradle.internal...` 收尾 | 新 workflow 已移除 `--stacktrace`；代表 `.github/workflows/build-apk.yml` 未上傳 |

失敗時會多一個 `Show real Gradle error` step（用 `::error::` 印出真正錯誤＋針對性建議）同 Artifact `hketa-gradle-logs`。tag build 另會跑 `Verify release version matches tag`，通過先至建立／更新對應版本嘅 Release，並上傳 `hketa-release-apk` Artifact。

### ⚠️ 已知限制

- **實時 ETA 永遠即時向官方 API 查**，唔會因「線路資料更新」變舊，亦**唔可以離線查 ETA**；離線只可讀已快取嘅路線／站名／座標目錄
- 紅色小巴、電車、渡輪、愉景灣及馬灣巴士冇同類公開實時 ETA API，未納入；新巴已併入城巴
- 港鐵月度 CSV 若冇座標欄，附近站列表唔會顯示重鐵／輕鐵／港鐵接駁巴士（路線模式仍可查）
- 「自動（全部）」同時打多間營運商，官方有速率限制（HTTP 429），請避免密集輪詢
- 未獲定位權限時**唔會**查附近路線、亦唔會用中環假座標；已授權但冇定位服務／逾時先退回中環並喺狀態列標示；只攞一次定位，唔背景追蹤
- **液態玻璃用嘅係公開 API，唔係 Android 17 私有開關**：目前跟隨深色／高對比／減少動態三項官方設定，並用 API 31+ 背景模糊；Android 17 若有專屬「液態玻璃」系統開關，要等正式 SDK 先能接上，唔會編造 key
- **ETA 自動刷新只喺 App 前景運作**：冇背景 Service／推播／定時鬧鐘，鎖屏或切去第個 App 期間到站時間唔會自己更新；返嚟會即時補一次。官方有 429 速率限制，15 秒係最密，唔建議再加密
- App 內更新用 `REQUEST_INSTALL_PACKAGES`，定位係 **GitHub sideload**，唔係 Play 上架版本
- 驗簽比對嘅係**簽章憑證**而唔係 APK 檔案雜湊；Release 嘅 `VERSION_INFO.txt` 會提供 APK SHA-256 供人手核對，但 **App 下載 APK 後仍未自動校驗檔案雜湊**
- adaptive icon 以整張圖做背景，冇視差層，亦未提供 Android 13 單色 themed icon
- **只還原 APP1933 嘅版面與到站格式**：AI 智能問路、實時載客量、預計落車時間、月票／會員／遊戲室／bot1933、街景步行導航、**Google 地圖路線圖**等專有功能全部唔做；路線詳情頁用純 XML 路線示意取代地圖，車資同預計落車時間顯示「—」而唔係假數字
- 主頁附近站 ETA 係由公開站級 API 即時查，唔係官方嘅大數據預測
- 若九巴全量站點持續被 403 擋，「附近車站」嘅九巴／城巴覆蓋會局限於已查過路線嘅站（有座標嘅 `partial` 快取）；**站序嘅中文站名唔受影響**
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

- **🚌 APP1933 layout**: large route number + "往 destination" + stop name with id + big minutes on the right; **Home / Favourites / Search / App settings** bottom tabs in a **centred dark charcoal pill** (56dp high, screen-width-aware inset, blue selected label) that follows system appearance
- **🗺 Tap a card for route detail**: red header + stop timeline + next three arrivals, **with no map at all**; fare / predicted alighting time honestly shown as "—"
- **🔍 Search home preloads routes**: official catalogue rows appear before typing; tapping `12 往 海麗邨 ›` opens the same route detail (with direction)
- **⟳ ETA auto-refresh**: Home nearby cards, the selected stop, favourites and route detail all poll in the foreground; 15 / 30 / 45 / 60 s selectable, switchable off, no re-location, no background polling
- **🏷 Production APK matches its Release version**: a `v1.0.0` tag attaches only `hketa-v1.0.0-release.apk`; a `tag ↔ build.gradle.kts ↔ actual APK` three-way gate blocks publishing on mismatch, with `VERSION_INFO.txt` / `SIGNING_FINGERPRINT.txt` attached
- **⬛ Every route-detail label is pure black**: the detail screen has a fixed white background, so stop names AND every label that used to share their colour (fare, alight, minutes unit, clock, tags, map-strip caption, footer note) are now pure `#000000`. `values-night` keeps the SAME pure black, and the footer background is pinned pale so black text is never on black. Dark mode no longer turns them into near-white invisible text; the red header / dark chips keep white text and the big minute numbers stay blue
- **🚌 Joint routes show "城巴/九巴"**: a route is joint ONLY when the SAME route number appears in BOTH official catalogues (KMB/LWB and Citybus) — catalogue-driven, no hard-coded route-number list. Home nearby cards, search / preload rows and the detail-screen caption show `城巴/九巴`; the KMB + CTB rows collapse into ONE card and each of the next three trips is tagged `該班次：城巴` / `該班次：九巴`. Home merges both operators' ETA for the same physical stop by Chinese stop name (unmatched names keep the old per-stop behaviour); **joint routes always display the KMB / LWB stop name and stop id** (the detail sequence is de-duplicated and home cards prefer the KMB StopOption; only when KMB has no catalogue data at all does it honestly fall back to the CTB name); the detail page expands KMB + CTB only, never the other 5 modes
- **🚌 ONLY Citybus routes use Citybus stop names (CTB_ONLY_NAME)**: a pure Citybus (non-joint) stop sequence never passes through the KMB-renaming logic at all — every stop keeps its official Citybus name and stop id. On a joint detail page, a stop that exists ONLY in the Citybus sequence also keeps the Citybus wording instead of copying any KMB name. Inside a merged home bucket, `App1933Format.resolveStopDisplay()` picks the label per card: a `CTB` card gets the Citybus `StopDisplay`, a `JOINT` card still gets the KMB label (previous request), a `KMB` card gets the KMB label, and every other company keeps its original `StopOption`. **Only Citybus cards / stops change to Citybus wording; joint and KMB routes are unaffected**
- **🔄 Circular routes shown separately (CIRCULAR_ROUTES)**: a circular line is detected from OFFICIAL data — `dest_tc` carrying `(循環線)` / `(Circular)`, or route-stop data with a single bound (no inbound) and the same first/last terminus. **No hard-coded route-number list.** ONLY circular lines use each operator's OWN stop wording: a Citybus circular line uses Citybus names + stop ids, a KMB circular line uses KMB names + stop ids; **normal-route rules are unchanged** (joint still KMB, pure CTB still Citybus). Circular rows are **never collapsed into a `城巴/九巴` joint row**
- **🔄 Operator ways + stop-name audit**: the KMB way is `213X 往 尖沙咀(循環線)` (App1933, keeps 往); the Citybus way is `26 勵德邨 - 荷李活道(循環線)` (no 往). Cards / search rows / the detail caption carry a `九巴循環線` / `城巴循環線` badge. `CircularRoutes.audit()` compares displayed stop names with the local official name table and reports `verified / mismatch / missing / foreign-suspect`; **with no table it honestly says so — it never claims everything is correct, never invents a name and never rewrites an official one**
- **🚌 Citybus routes use Citybus stop names (CTB_STOP_NAME)**: one physical nearby stop may have BOTH a KMB and a Citybus `StopOption`. After the home page merges the two ETA lists, **each card shows the official name and stop id of its OWN operator** — a pure Citybus card shows the Citybus name / Citybus stop id, a pure KMB card keeps the KMB name, and a joint card still uses the KMB name (previous request). The detail-page title follows the card label; pure Citybus stop sequences always come from `safeNames(CTB)` and are never rewritten with KMB wording
- **Next three arrivals**: markers for `-` (just arrived), `—` (no service), "預定班次" (scheduled), diversion and joint operators — replacing the old single-arrival list
- **Stop sequence + direction switch**: route number lists all stops (with stop ids), one-tap `⇄` bound toggle, live stop-name / stop-id filter
- **8 lookup modes**: Auto (all), KMB/LWB, Citybus, NLB, Green Minibus, MTR Heavy Rail, Light Rail, MTR Bus
- **Real-time ETA**: Chinese stop names, ETA in minutes / clock time / remarks, 30-second auto-refresh and pull-to-refresh
- **📍 Nearby stops**: **50–2000 m radius chosen in Settings**, sorted by distance; **manual locate and "↻ refresh" buttons removed** — auto-locates after permission is granted, pull-to-refresh only; tap a stop to see ETA for **all** routes serving it
- **⭐ Favourites**: frequently used stops, including the `bound` direction
- **⚙️ Route catalogue update**: manual / daily / every 3 days / weekly, Wi-Fi-only toggle, clear local data; 10 official catalogues written atomically, a failed source keeps the previous snapshot
- **🔄 In-app app update**: checks the latest GitHub Release, 24-hour throttle, installs via `FileProvider`
- **🔐 Signing consistency**: fixed development keystore, CI-pinned fingerprint, pre-install certificate comparison
- **🚌 Rainbow-bus icon**: launcher icons in 5 densities, dedicated round icon, Android 8+ adaptive icon, 512px store preview

### 📦 Install

1. From the **matching version** on [Releases](https://github.com/benyeung08/hketa/releases) (e.g. `v1.0.0`), download **`hketa-v1.0.0-release.apk`** (signed production build); alternatively grab the `hketa-release-apk` artifact from a tag run, or `hketa-debug-apk` for the debug build
2. On Android 8+, enable "Install unknown apps" for the first install
3. Overlay installs stay signature-consistent; **uninstall first** if the device previously had a build signed with a different key
4. Cross-check certificate, APK SHA-256 and version against the attached `SIGNING_FINGERPRINT.txt` and `VERSION_INFO.txt`

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

Pushing to `main` triggers `.github/workflows/build-apk.yml`, which builds and uploads the `hketa-debug-apk` artifact. A `v*` tag build assembles the **signed production (release) APK** and publishes it to that version's Release:

```bash
# 1) bump BOTH version fields first
#    app/build.gradle.kts: versionCode = 2 / versionName = "1.0.1"
git add app/build.gradle.kts && git commit -m "Bump to 1.0.1" && git push origin main
# 2) the tag must match versionName
git tag v1.0.1 && git push origin v1.0.1
```

### 🏷 Production APK ↔ matching Release version

Every tag build runs **`RELEASE_VERSION_GATE`** so the APK inside a Release really IS that version:

- **Three-way check**: `tag` (`v1.0.1`) = `versionName` in `app/build.gradle.kts` = **the actual APK's `versionName` read by `aapt dump badging`**; `versionCode`, `package`, `minSdk` and `targetSdk` are cross-checked too. Any mismatch **fails the workflow BEFORE the Release is created** — a wrong-version APK is never uploaded
- **Exactly one `.apk` asset**: `hketa-v1.0.1-release.apk` (signed production). The in-app updater takes the first `.apk` asset of the latest Release, so a debug build can never slip into Releases
- **Two manifests attached**: `VERSION_INFO.txt` (tag / versionName / versionCode / minSdk / targetSdk / APK SHA-256 / signing fingerprint / keystore source / commit / build time) and `SIGNING_FINGERPRINT.txt`
- **Version-matched notes**: `v1.0.0` automatically uses `RELEASE_NOTES_v1.0.0.md` as the Release body; a newer tag without its own notes falls back to a short body instead of reusing another version's notes. `tag_name` and the Release title are explicitly bound to `github.ref_name`, and a tag with a `-` suffix is marked prerelease
- **Also uploaded as the `hketa-release-apk` Actions artifact**; `generate_release_notes` is now `false` — set it back to `true` if you want GitHub's automatic "What's Changed" section appended

`AppUpdate.compareVersions()` compares `tag_name` with the installed `versionName`; this gate is what makes `v1.0.1` ↔ `1.0.1` hold, so a "new tag but stale APK" false update cannot happen.

### ⚙️ Settings

The fourth bottom tab is renamed from **More** to **App settings**. It opens six independent sections: **Nearby route radius** (50–2000 m), **ETA auto-refresh**, **Route catalogue update** (caches catalogues only — ETA is always live), **Update application** (repo `benyeung08/hketa` is built in), **App signing consistency**, and **Navigation bar appearance**; the dark disclaimer card from the screenshot sits at the bottom of the page.

### 🔄 Update application (version pill)

The whole section now reproduces the screenshot's dark rounded bar: `v1.0.0 · 1　⟳　🕐　⧉`.

- **Two numbers on the left**: `v1.0.0` is `versionName` (always prefixed with `v`), `1` is `versionCode` / build number — both read dynamically from `PackageInfo`, never hard-coded
- **⟳ Re-check**: immediately calls `api.github.com/repos/{owner}/{repo}/releases/latest`; the icon dims while checking and the thin status line below updates on completion
- **🕐 Update history**: a read-only dialog with current version / build, release repo, auto-check switch, last check time (HKT), cached tag and state
- **⧉ Copy**: puts `v1.0.0 · 1 · benyeung08/hketa` on the clipboard and shows a Toast, handy for bug reports
- **No feature removed**: repo field, auto-check switch, check / download & install / Release page, progress bar, release notes, 24-hour throttle, `FileProvider` install and **signing-consistency verification** all survive inside the 「⌄ 進階設定」 drawer; it expands automatically when a newer release is found or a download starts
- **Three vector icons** (white strokes on the dark pill), not emoji — so they look identical on every device font

### 📍 Radius setting & pull-to-refresh

- **Home "↻ refresh" button removed**: only `SwipeRefreshLayout` pull-to-refresh remains; a selected stop's ETA still refreshes every 30 seconds
- **Home radius Spinner removed**: "定位附近路線嘅距離" moves into the first block of ⚙ Settings; the Home page has no dropdown or button left
- **Matches the "50 ▼" screenshot**: dark rounded bar, big white number + small `m` on the left, grey ▼ on the right; tapping the whole bar opens a single-choice dialog
- **7 radii**: `50 / 100 / 200 / 300 / 500 / 1000 / 2000 m`, default `500 m`; `50 m` was added for the screenshot, all values stored in one `NearbyRadiusPrefs`
- **Takes effect immediately**: `MainActivity.onResume()` compares `lastRadius` and re-locates with the new radius when returning from Settings — no manual refresh needed

### ⟳ ETA auto-refresh

Previously only the currently selected stop was polled on a hard-coded 30-second timer; Home nearby cards and the route-detail screen never updated by themselves. Now every visible ETA shares one foreground loop.

- **One loop for all**: `EtaViewModel` calls `refreshVisibleEta()` at the configured interval read from `EtaRefreshPrefs.intervalMs()` — no more hard-coded `delay(30_000L)`
- **Four surfaces update together**: Home nearby-route cards (reusing the cached 6 stops, no re-location), the selected stop / favourites, the boarding stop in route detail, and stop-sequence ETA
- **Settings**: an "自動刷新到站時間" switch (ON by default) plus the dark big-number "30 ▼" picker with 15 / 30 / 45 / 60 s; the picker greys out when OFF, and returning Home re-reads it in `onResume()` and restarts the loop immediately
- **Status line**: Home and the route-detail footer show `⟳ ETA 自動更新 · 每 30 秒 · 15:42 已更新`; when OFF it reads "已關閉 · 可下拉頁面手動刷新"
- **Foreground only**: `onResume()` restarts and catches up with one immediate ETA fetch if a full interval has elapsed; `onPause()` stops it. **No background Service / WorkManager** — leaving the app never silently calls the official API
- **No duplicate or idle calls**: with nothing loaded or without location consent the loop returns without any API call; nearby-card refresh only re-queries the cached stops' ETA, never re-downloads the stop catalogue, and pull-to-refresh remains available

### 🗺 Home card → route detail (no map)

Tapping any nearby-route card on the Home page opens a **full-screen route detail** that reproduces the screenshot's red header and lower stop-sequence list, **with no map at all**.

- **Red header**: `←` back + `12 往 尖沙咀東（麼地道）(YT927)` (route / destination / stop id) + three white circular actions: `▦` refresh the three arrivals, `★` favourite, `⇄` switch direction
- **No map loaded**: the Google Maps area becomes a pale paper strip with a red route line and a stop-name bubble — a plain XML drawable, **no map SDK, no tiles, no network map request, no Google attribution**
- **All four side chips do real work**: board (select first stop) / nearby (back to Home) / favourite / more (open App settings); no dead buttons
- **Stop timeline**: `1. stop (stop id)`, red dot / grey circles, red vertical line; only the boarding stop expands the **next three arrivals** (big blue minutes + clock + `預定班次` / `剛到站` / `改道` / `沒有服務` marks), formatted by `App1933Format.cardsFrom()`
- **Honest placeholders**: `車資 —` and `落車 —` — the public ETA APIs contain **neither fare nor predicted alighting time**, so no invented `$5.6` / `15:31`; an explicit footer explains it
- **Data layer unchanged**: stop sequence from `EtaViewModel.loadStops()`, ETA from `selectStop()` + `repo.etaFor()`, 30-second auto-refresh intact; this screen **does not need location permission**
- Search-tab result rows still use the original in-tab stop sequence, unaffected

### ◌ Floating liquid-glass navigation bar

- **Size & position (from the screenshot, NAV_OVERLAY_POSITION)**: the old near-full-width `64dp` blue-grey bar with `12dp` margins on all sides becomes a **dark charcoal capsule `56dp` high, horizontally centred, `28dp` inset on each side**; corners are `28dp` (half the height → a true pill), elevation drops to `6dp` and the rim becomes barely visible. **The position then changed again**: instead of an occupying bottom row `8dp` from the edge, it is now a **true overlay child of a `FrameLayout` root** (`layout_gravity=bottom|center_horizontal`), lifted by `NavGlass.geometry()` to **~12% of screen height above the bottom (clamped `48–160dp`)**. The blue install buttons therefore show through behind the translucent capsule and the next content row remains visible **below** it, matching the screenshot. Scrollable pages carry `180dp` / `200dp` bottom padding so the last item is still reachable
- **Scales with screen width**: `NavGlass.geometry()` recomputes the side inset on every `apply()` — phones use ~`6%` per side (88% wide, so four labels still fit), while tablets / landscape `≥600dp` switch to `18%` per side (~64% wide, matching the screenshot's narrow capsule), clamped to `16–256dp`; rotating the device re-centres it immediately; the **bottom lift is recomputed from screen height too** (`heightDp × 0.12`, clamped `48–160dp`), so rotation / tablets never snap back to the bottom edge
- **Selected state**: ONE darker inner pill (`#141416`, `24dp` corners) wraps the selected tab with a **blue label** `#4A9EFF`; the other tabs are light grey `#A8B0BA` — no more frosted white highlight
- **Follows system settings**: `NavGlass` reads `Configuration.uiMode` (night → deeper charcoal), `Settings.Secure "high_text_contrast_enabled"` (high contrast → solid bar with a `2dp` white rim) and `Settings.Global ANIMATOR_DURATION_SCALE / TRANSITION_ANIMATION_SCALE` (reduced motion → no shadow, no blur); all three appearances share the same `28dp` geometry
- **Real backdrop blur**: `Window.setBackgroundBlurRadius()` on API 31+, fully wrapped in `runCatching`; older or restricted devices fall back to the plain translucent drawable instead of crashing
- **Live updates**: `onCreate` / `onResume` / `onConfigurationChanged` all re-`apply()` (geometry included); changing a system setting or rotating the screen is reflected on return
- **User override**: the Settings page has a "Follow system appearance" switch (ON by default); turning it off pins only the appearance — **size and position are unchanged** — with the current state shown underneath
- **Position-only change**: the four tabs, selected-state pill, glass variants, system-setting following and hiding the bar during full-screen search are all kept; the joint-route / ETA / signing / updater data layer is untouched

### 🚌 APP1933 layout mapping

| Tab | What is reproduced | Under the hood |
| --- | --- | --- |
| **Home** | Nearby route cards: `route` / `往 destination` / `stop (KT123)` / big minutes; three arrival chips per card; **no locate / refresh buttons, auto-loads after consent, pull-to-refresh** | `EtaRepository.nearbyStops()` + parallel `stopEta()` for the nearest 6 stops |
| **Favourites** | Saved stops, tap to open, long-press to remove | `Favorites` |
| **Search** | Official routes PRELOADED on entry, `12 往 海麗邨 ›` rows, custom keypad; tapping a row opens **route detail (no map)**; stop-sequence mode keeps 8 modes, `⇄` toggle, filter, direct stop-id lookup | `RouteSearch.preload()` / `search()` + `RouteDetailActivity` |
| **App settings** | ⚙ Settings entry + six sections + dark disclaimer card | `SettingsActivity` |

**UI only, data unchanged**: `App1933Format.kt` is a pure formatting layer that groups the same `EtaItem` list by `(company, route, stop, dest)` and keeps the three earliest arrivals; `CardAdapters.kt` only draws the cards. All 8 operators, the 10 official catalogues, `RouteDataStore` and `ApkSignature` are untouched.

**Deliberately NOT implemented** (they need KMB private APIs / membership accounts / Play Services): AI route planning, real-time occupancy, predicted alighting time, KMB monthly pass, club1933 membership, game room, bot1933 chat, Street View / walking navigation, pet bus and ads. **No grey placeholder buttons, no fake data.**

### 🔍 Search screen (APP1933 full-screen)

Reproduces the supplied screenshot instead of the old "one input box plus a stack of buttons":

- **Red header** with a centred 搜尋 title; `⌂` on the left returns to Home. The shared header and the four bottom tabs are hidden in this mode.
- **Centred 輸入路線號碼 field** driven by a **custom keypad**: a 3×4 grid of white rounded number keys (1–9, 0, `⌫`, `↵`) on the left and round white letter keys (`A B / C E`) with `▲▼` paging on the right. **The system IME never appears.**
- **The search home PRELOADS routes on entry**: the dark empty area that used to show only "輸入路線號碼…" is filled by `RouteSearch.preload()` with official catalogue rows (capped at 24), so `12 往 海麗邨 ›` style rows are visible before a single key is pressed. Preloading starts in the background at app launch.
- Preload uses the same official catalogues: KMB / CTB routes and directions, both NLB ends, GMB route codes (explicitly marked "目錄預載（未查方向）"), plus the project's MTR line-code and MTR Bus route-code tables. If no catalogue can be read, an update hint is shown instead of inventing destinations.
- Every keystroke then switches to `RouteSearch.search()` (180 ms debounce). Rows show a **large route number + 往 destination + `›`**; NLB / GMB / MTR rows add a small `大嶼山巴士 (資料由DATA.GOV.HK提供)` attribution line.
- Typing `1` lists `往 尖沙咀碼頭`, `往 竹園邨`, `往 大澳 - 嶼巴`, `往 梅窩碼頭 - 嶼巴`; exact matches first, at most four prefix matches. Clearing the input returns to the preloaded list.
- **Tapping any row opens the same full-screen "route detail (no map)"** — red header + stop timeline + next three arrivals — carrying that row's `O / I / 1` direction across, exactly like a Home nearby card; `ALL` becomes "all directions".
- The original 8 operator modes, direct stop-ID lookup, direction switch and stop-name/id filter stay in stop-sequence mode; 「← 返回路線搜尋」 returns to the keypad + preloaded list.
- Destinations come only from the `RouteDataStore` official catalogues (local snapshot first, 403 retry). **No fake data, no private APIs**; GMB directions add one official `/route/{region}/{route}` lookup.

### 📍 Location permission & auto-locate

- **Manual locate button removed**: the Home page has no "relocate" control at all — it happens automatically
- **Consent first, then auto-locate**: launch / pull-to-refresh checks `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`; if already granted it locates and loads nearby routes immediately
- **No fake location without consent**: the first run shows the system permission dialog; while denied it clears the cards and shows a tappable 「⚠ 需要定位權限才能顯示附近路線 · 按此授予」 hint — it **never** silently queries the API with the Central fallback coordinates
- **Two gates, one check**: both `MainActivity.autoLocateIfAllowed()` and `EtaViewModel.loadNearbyRoutes()` call the same `LocationHelper.hasPermission()`; `onResume()` retries once after the user enables the permission in system Settings
- **30-second polling stays on a selected stop**: nearby routes load on launch / after consent / pull-to-refresh / returning from Settings; a selected stop's ETA still refreshes every 30 seconds

### 🚏 Citybus stop names & 403 fallback

Observed issue: Citybus stop sequences showed bare ids such as `002891`, while Settings reported "已更新 8/11 個來源（部分失敗：九巴／龍運全量站點、城巴全量站點、綠小巴站點索引）" and the log showed `HTTP 403 @ https://data.etabus.gov.hk/v1/transport/kmb/stop => <html xmlns='http://www.w3.org/1999/xhtml'>`. Both symptoms share one cause: the bulk stop snapshot was never built, so names could not be resolved.

- **Citybus has NO bulk endpoint**: the official v2 spec only provides `/stop/{stop_id}`. The old `/v2/transport/citybus/stop` URL does not exist and therefore always failed. It is now an on-demand derived source, no longer a failed download.
- **Single-stop name resolution**: when a route's stop sequence loads, unnamed stops are resolved in parallel through the official single-stop API (3 threads / 12 s cap / max 30 stops). `name_tc` plus `lat/long` are written to a local `partial` snapshot, so names work offline afterwards and the nearby-stop index gains coordinates too.
- **403 / HTML fallback**: `HttpJson` retries every URL with both an app and a browser User-Agent, tries trailing-slash variants, and treats HTML WAF pages as failures even on HTTP 200. The KMB bulk stop list is now optional; when it 403s the app automatically uses the single-stop API.
- **Honest reporting**: success now reads "已更新 N 個來源 · 共 N 筆（另有 M 個選用／按需來源略過）", and per-source status shows "選用／按需略過" instead of "失敗".

> Verified with a local HTTP server reproducing `403 + XHTML`, `200 + HTML WAF` and official single-stop JSON: all 11 functional checks pass, including "stop sequence shows the Chinese name" and "403 bulk list becomes a skip, not a failure".

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
├── .github/workflows/build-apk.yml   # CI: preflight, build, signing verify, version gate, Release
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
│   ├── pin_signing_fingerprint.py    # writes the keystore fingerprint into strings.xml
│   └── verify_release_version.py     # tag ↔ gradle ↔ APK badging three-way gate
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
| `one-shot drift repair script exists and is idempotent` / `distiller warns when old stacktrace-shaped log is seen` | `scripts/fix_drift.py` or `scripts/show_build_error.py` is missing / stale → **full-sync all seven `.py` files under `scripts/`** |
| `tag 與 Gradle 版本唔對應` / `APK 內容與 Gradle 版本唔對應` (version mismatch) | The release gate stopped it: bump `versionCode` / `versionName` in `app/build.gradle.kts`, push, then re-tag |
| `Release is explicitly tied to the matching tag/version` | The checker used to hard-require `RELEASE_NOTES_v1.0.0.md`, contradicting the workflow's own fallback — **now optional**; just overwrite `scripts/check_api_fields.py`. If the log shows `缺以下 N 項字串`, also overwrite `.github/workflows/build-apk.yml` |
| `輔助文字未跟站名一齊改純黑` / `footer 黑底會令純黑字睇唔到` / `仍係深灰 #374151` (stale all-black detail colours) | Overwrite the two route-detail layouts plus `colors.xml` in both `values/` and `values-night/` (4 files) from the full zip / merged pack |
| `Received status code 429 ... Too Many Requests` (e.g. `jsr305-3.0.2.pom`) | **Maven Central rate-limit, NOT a code / upload / signing bug**: `setup-java` now caches Gradle dependencies and both assemble steps retry 3 times (30s / 60s backoff). If all 3 fail, wait a few minutes and **Re-run jobs** — no file changes needed |
| `JointRoutes.java 唔見咗` / `聯營會拆做兩張卡` / `班次 chip 無「該班次」` / `缺九巴站名優先邏輯` / `導航欄仲係貼底佔位列` / `城巴獨有站仍會被改成九巴站名` / `循環線未標記` (stale joint / nav / CTB-name / circular files) | Overwrite the new `JointRoutes.java` plus the other 8 joint-related files from the full zip / merged pack; do not upload one by one |
| `Could not resolve` / `Read timed out` / 5xx | Same class of transient dependency-download failure: simply **Re-run jobs** |
| Still ends with `at org.gradle.internal...` | The new workflow removed `--stacktrace`; this means `.github/workflows/build-apk.yml` was not uploaded |

On failure, an extra `Show real Gradle error` step prints the real cause as `::error::` annotations with a targeted hint, and the full log is uploaded as the `hketa-gradle-logs` artifact. Tag builds additionally run `Verify release version matches tag`; only after it passes is the matching Release created / updated and the `hketa-release-apk` artifact uploaded.

### ⚠️ Known limitations

- **Live ETA is always fetched from the official API** — the catalogue updater never serves stale arrival times, and **ETA does not work offline**. Only route / stop / coordinate catalogues are cached.
- Red minibuses, trams, ferries, Discovery Bay and Ma Wan buses have no equivalent public real-time ETA API and are not included; NWFB has been merged into Citybus.
- If an MTR monthly CSV has no coordinate column, heavy rail / Light Rail / MTR Bus stops will not appear in the nearby list (route mode still works).
- "Auto (all)" queries several operators in parallel; official rate limits apply (HTTP 429) — avoid rapid polling.
- Without location permission the app does **not** query nearby routes and does **not** fake a Central position; the Central fallback is only reachable after consent when the provider is unavailable / times out, clearly labelled in the status bar; one-shot location only, no background tracking.
- **The liquid glass uses public APIs, not a private Android 17 toggle**: it currently follows Dark mode / High-contrast text / Reduced motion plus API 31+ backdrop blur. A dedicated Android 17 "liquid glass" system switch would require the official SDK before it can be wired up — no invented keys.
- **ETA auto-refresh runs in the foreground only**: no background Service, push or alarm; arrivals do not update while the screen is locked or another app is open, and one catch-up fetch happens on return. Official rate limits (HTTP 429) apply — 15 s is the tightest option, do not shorten it further.
- In-app updates use `REQUEST_INSTALL_PACKAGES`; this project targets **GitHub sideload**, not the Play Store.
- Signature verification compares the **signing certificate**, not an APK file hash; the Release's `VERSION_INFO.txt` provides an APK SHA-256 for manual checking, but **the app still does not verify the downloaded file's hash automatically**.
- The adaptive icon uses the full artwork as its background: no parallax layer, and no Android 13 monochrome themed icon.
- **Only the APP1933 layout and arrival format are reproduced**: AI route planning, real-time occupancy, predicted alighting time, monthly pass / membership / game room / bot1933, Street View walking navigation and the **Google Maps route view** are not implemented. The route-detail screen replaces the map with a plain XML route strip, and fare / predicted alighting time show "—" instead of fake numbers
- Home-page nearby ETAs come from public stop-level APIs, not KMB's proprietary prediction engine
- If the KMB bulk stop list keeps returning 403, nearby-stop coverage for KMB / CTB is limited to stops already visited (the `partial` cache with coordinates); **Chinese names in route stop sequences are unaffected**.
- Arrival times come from official open data and are indicative only.

### 🔗 Related

- Repo: https://github.com/benyeung08/hketa
- Release notes: [RELEASE_NOTES_v1.0.0.md](RELEASE_NOTES_v1.0.0.md)
- Data sources: [DATA.GOV.HK](https://data.gov.hk), operator open-data feeds, [MTR Open Data](https://opendata.mtr.com.hk)

### 📄 License & disclaimer

[MIT](LICENSE). This is an unofficial project, not affiliated with any operator or government department. Arrival data remains the property of its original providers.

> This README is documentation only; editing it does **not** affect CI or the APK build.
