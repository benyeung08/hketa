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

目前版本 **`v1.0.2`**（`versionCode = 3`）｜Android 7.0+（minSdk 24）｜target / compile SDK 34。
中英雙語 Release 說明：[RELEASE_NOTES_v1.0.2.md](RELEASE_NOTES_v1.0.2.md)（今日現行）、[RELEASE_NOTES_v1.0.1.md](RELEASE_NOTES_v1.0.1.md)（歷史）、[RELEASE_NOTES_v1.0.0.md](RELEASE_NOTES_v1.0.0.md)（首版）。

### ✨ 功能

- **🚌 APP1933 版面**：大字路線號＋「往 目的地」＋站名連編號＋右側大數字分鐘；底部 **主頁／收藏／搜尋／應用程式設置** 四分頁，**居中深灰黑藥丸導航欄**（56dp 高、按屏寬收窄、藍字選中）跟隨系統外觀
- **🗺 點卡片開路線詳情**：紅色頂欄＋站序時間軸＋未來三班次，**完全冇地圖**；車資／預計落車時間如實顯示「—」
- **🔍 搜尋主頁預載路線**：未輸入已列出官方目錄列；點 `12 往 海麗邨 ›` 直接開同一個路線詳情頁（帶方向）
- **⟳ ETA 自動刷新**：主頁附近路線卡片、已選車站、收藏、路線詳情全部前景定時更新；間隔 15／30／45／60 秒可揀、可關掉，唔重新定位、唔背景輪詢
- **📦 已發佈版本都可補正式版**：`workflow_dispatch` 揀一個**已發佈** Release，用該 tag 自己嘅 source 重建已簽名正式版 APK，清走衝突 `.apk` 後只留一個 `-release.apk`；唔新建 Release、唔改 tag／內文（PUBLISHED_RELEASE_BACKFILL）
- **🐛 CI 編譯修正（本輪最新）**：上一版改搜尋頁時，連仍被 7 處引用嘅 `permissionHint` 欄位一齊 cut 走，Kotlin 報 `Unresolved reference` 令 `assembleRelease` 失敗；已加返宣告＋`findViewById` 綁定，並新增 2 條符號檢查＋preflight drift，同類錯誤而家 0 秒報出
- **💾 本輪最新：恢復本地資料喺搜尋功能上**：`RouteSearch` 本來就係本機快照優先、即時 API 後備，但取消「進階」後搜尋頁**睇唔到本機資料**，而且由「應用程式設置 → 線路資料更新」更新／清除快照返嚟，`onResume()` 唔會重新預載 → 畫面仍係舊一次結果，本地資料望落「冇咗」。新增純 Kotlin `SearchLocalData.kt`（指紋／來源標記／離線判定）＋搜尋頁 `tvLocalDataStatus` 狀態列：`本機資料：N/M 個來源成功 · 共 K 筆 · 點按更新`，撳一下去設定頁；預載提示如實加 `來源：本機資料`／`來源：即時 API`，唔會把 live fallback 扮成本機資料。`onResume()` 比對 `updatedAt｜來源數｜筆數｜快照檔數` 指紋，變咗即刻 `searchRoutes()` 重新預載並重整歷史。**唔加返進階掣、唔新增色值、唔改 `RouteDataStore`／`RouteSearch`／8 營運商資料層**，實時 ETA 仍永遠即時查、唔快取
- **🔎 本輪最新：取消搜尋功能嘅「進階」並將導航欄移到主搜尋頁**：移除搜尋頁 `進階：站序／方向／篩選 ›` 掣、`btnBackToSearch` 返回掣、進階模式狀態旗標同切換函式；8 模式／站序區塊永久隱藏（保留 View 只為既有資料層綁定相容，用戶冇任何入口）。之前底部懸浮液態玻璃導航欄只喺進階模式先 `restoreChrome()` 出現，主搜尋頁反而被 `hideChrome()` 設成 `GONE`；而家 `hideChrome()` 只收起頂部紅色 header／狀態列，**導航欄喺主搜尋頁常駐 VISIBLE**，`selectTab(2)` 永遠直接顯示截圖式分類搜尋主頁。設定頁「更新應用程式」嘅同名進階抽屜掣保留；搜尋頁 10 個 `search_*` 色值、`values-night`、液態玻璃幾何／4 分頁、8 營運商資料層、位置更新、同站更多路線、同號分卡全部一個都冇郁
- **🚌 本輪最新：同路線號碼不同巴士營運商分開顯示**：之前 `cardsFrom()` 嘅分組 key 唔帶公司，九巴 12 同 嶼巴 12、城巴 1 同 綠小巴 1 呢類**同號但各自營運**會被塞入同一張卡（三班次混埋兩間公司）。新增純 Kotlin `SameRouteOperators.partition()`：**只有剛好 KMB+CTB 兩家且兩邊都非循環**先維持既有 `城巴/九巴` 聯營卡，其餘（KMB+CTB+第三家／KMB+NLB／CTB+GMB／任一邊循環）一律**按公司拆卡**，各自三班次、各自公司 meta（`九巴 / 龍運`／`城巴`／`嶼巴`／`綠色小巴`），同號兩張卡排埋一齊。循環標記改用**該公司自己嘅班次**判定，唔再由整個 bucket 誤標；拆卡 fallback 用自己 `first.stopId`，唔會繼承 KMB 站號。搜尋頁本來只合併 KMB/CTB 聯營、其餘每公司一列，唔使改；8 營運商資料層／`JointRoutes.augment()`／權限閘／位置更新／同站更多路線／色值一個都冇郁
- **🚌 本輪最新：自動定位加入「顯示同站更多路線」**：之前 `nearbyStops(..., 6)` 嘅 6 個配額會被 KMB／CTB 同一實體站嘅兩個 `StopOption` 食晒，近站另一半公司 ETA 直接被 cut 走。而家改為攞 24 個候選、純 Kotlin `NearbyMoreRoutes.select()` 按**實體站名**保留最近 **6 個站嘅全部公司別名**（最多 12 個並行 ETA 查詢防 429），`cardsFrom()` 本來唔 truncate 路線，全部如實出卡並按 `(距離, 路線號, 目的地)` 排，同站路線排埋一齊；狀態列顯示 `N 個站 · M 條路線 · 同站路線全部顯示`。自動定位同上一輪嘅前景位置更新共用 `loadNearbyRoutes()`，兩者一齊生效；ETA 自動刷新重用 expanded 快取，8 營運商資料層／權限閘／色值一個都冇郁
- **📍 本輪最新：自動定位加入「更新定位位置」**：授權後喺前景註冊平台 `LocationManager` 位置更新（唔用 Play Services／WorkManager）；裝置郁動超過**半徑 1/4（最少 50 m）**且隔 **≥30 秒**先自動重新定位並重整附近路線，GPS 抖動唔會狂打 API。`onResume` 註冊、`onPause`／`onDestroy` 即刻移除，`permissionHint` 仍係「授予權限」而**唔係重新定位掣**；狀態列顯示 `· 📍 位置自動更新`。未授權／熄定位服務一律唔註冊、唔 fallback 中環；ETA 自動刷新照舊只重用已快取站點
- **📦 本輪最新：CI Artifacts 只出正式版 APK**：移除 `Assemble debug APK`／`hketa-debug-apk` 兩組 step；`assembleRelease` 改為**所有 push 都跑**（唔再局限 tag），新增 `hketa-release.apk` 穩定名，唯一 Artifact `hketa-release-apk`；tag build 額外複製帶版本名畀 Release asset。checker 加 4 條反向斷言、preflight／workflow 加 drift，唔准再出 debug APK
- **🖼 本輪：搜尋頁定稿＋顏色唔變**：`activity_main.xml` 改為截圖分類式（8 tab／6 chips／書籤／紀錄），`MainActivity.kt` 由自訂鍵盤改接新介面；全部 `search_*` 色值一個都冇郁、`values-night` 唔覆寫，純 UI＋檢查同步
- **📅 歷史單日說明檔（備用）**：`_day1_2026-11-19.md`（11-19 介面還原）／`_day2_2026-11-20.md`（20 號至最新改動）兩份可直接貼；純文字，唔被 workflow 自動選用、唔影響 CI
- **📝 v1.0.0 雙語說明（截至 2026-11-19）**：`RELEASE_NOTES_v1.0.0.md` 中英各 5 章 33 條對稱，收錄 APP1933 搜尋／無地圖詳情／定位授權／懸浮導航欄／ETA 自動刷新，明確唔收 11-20 改動；純文字，唔使 push code
- **📝 v1.0.2 雙語說明（今日 2026-11-24）**：`RELEASE_NOTES_v1.0.2.md` 中英各 **5 章 43 條**完全對稱、2 段 `#### 📅` 分組（上午／收尾），收錄截圖式搜尋頁、`permissionHint` 編譯修正、只出正式版 APK、前景位置更新、同站更多路線、同號分卡、取消進階＋導航欄移位、恢復本地資料；`app/build.gradle.kts` 同步 bump 到 `versionCode = 3`／`versionName = 1.0.2`，`RELEASE_NOTES_${TAG}.md` 自動對應 `v1.0.2` tag，舊兩份說明加歷史註記、唔郁已簽名 APK
- **📝 v1.0.1 雙語說明（本輪：2026-11-20 至最新）**：`RELEASE_NOTES_v1.0.1.md` 重寫為**只列 20 號至最新**，中英各 5 章 33 條對稱；移除「昨天／今天」舊分組同重複條目，完整收錄聯營／站名規則／正式版對應／backfill／CI 修正／GMB 循環線班次／說明分界／離線驗證
- **🧩 三份說明互補、舊檔保留歷史**：`v1.0.0` 截至 11-19、`v1.0.1` 由 11-20 起（當時「至最新」係歷史表述）、**`v1.0.2` 收錄今日 11-24**；`RELEASE_NOTES_${TAG}.md` 按 tag 各自選用，改說明唔郁 APK／tag、唔使重跑 CI
- **📝 v1.0.1 中英雙語 Release 說明**：`RELEASE_NOTES_v1.0.1.md` 兩版各 5 章、33 條要點完全對稱，`RELEASE_NOTES_${TAG}.md` 自動對應 tag，唔會誤用 v1.0.0 內文
- **🆕 本輪交付**：`HK_ETA_v1.0.1_Release_說明_中英雙語_2026-11-20至最新.md` 可直接貼；另附最小包、合併包與完整專案 zip
- **🔁 綠小巴循環線班次修正**：第一站顯示三個**唔同**班次而唔係三個同一班次；最後一站顯示**自己**嘅三個唔同班次、唔再同第一站重複（資料驅動、唔捏造班次）
- **🏷 正式版 APK 對應 Releases 版本**：`v1.0.0` tag 只會附 `hketa-v1.0.0-release.apk`；`tag ↔ build.gradle.kts ↔ 實際 APK` 三重校驗唔夾即擋低發布，並附 `VERSION_INFO.txt`／`SIGNING_FINGERPRINT.txt`
- **⬛ 路線詳情全頁文字純黑**：詳情頁係固定白底，站名**同所有原本同色嘅輔助文字**（車資、落車、分鐘單位、時鐘、標記、路線示意標籤、頁底說明）一律改純黑 `#000000`；`values-night` 保持同一純黑值，頁底背景亦釘死淺色避免黑字落黑底。深色模式唔會再變近白色睇唔到；紅色頂欄／chip 白字同藍色分鐘數字唔變
- **🚌 聯營路線顯示「城巴/九巴」**：同一路線號同時出現喺九巴／龍運同城巴兩份官方目錄先判定為聯營（**資料驅動，冇硬編路線號清單**）。主頁附近卡片、搜尋／預載結果、詳情頁「路線示意」一律顯示 `城巴/九巴`；KMB＋CTB 兩列合併成一張卡，未來三班次逐班標 `該班次：城巴`／`該班次：九巴`。主頁會按中文站名把同一實體站嘅兩家 ETA 先併埋（配唔到名就維持各自一張，行為唔變）；**聯營站名統一用九巴／龍運站名同站號**（詳情頁站序去重、主頁卡片優先用 KMB 嗰個 StopOption；完全冇 KMB 目錄先如實退回城巴名）；詳情頁只展開 KMB＋CTB 兩家，唔會撈埋其餘 5 種模式
- **🚌 只有城巴路線用城巴站名（CTB_ONLY_NAME）**：純城巴（非聯營）路線嘅站序**完全唔會**經過九巴改名邏輯，一律保留城巴官方站名同站號；聯營詳情頁入面**城巴獨有、冇九巴對應**嘅站亦保留城巴名（唔會再抄九巴用字）。主頁合併 bucket 入面，`App1933Format.resolveStopDisplay()` 逐張卡揀站名：`CTB` 卡用城巴 `StopDisplay`、`JOINT` 卡仍用九巴名（上一輪要求）、`KMB` 卡用九巴名，其餘公司沿用原本 `StopOption`。**即係只有城巴嘅卡／站先改為城巴站名，聯營同九巴唔受影響**
- **🔄 循環線各自顯示（CIRCULAR_ROUTES）**：循環線由**官方資料**判定 —— `dest_tc` 帶 `(循環線)`／`(Circular)`，或 route-stop 只有單一方向（冇 inbound）且首尾站相同，**冇硬編路線號清單**。只有循環線先套用「各家用自己站名」：城巴循環線用城巴站名＋城巴站號、九巴循環線用九巴站名＋九巴站號；**普通路線規則完全唔變**（聯營仍用九巴名、純城巴用城巴名）。循環線**唔會被合併成 `城巴/九巴` 聯營列**
- **🔄 顯示方式＋站名核對**：九巴方式 `213X 往 尖沙咀(循環線)`（App1933，保留「往」）；城巴方式 `26 勵德邨 - 荷李活道(循環線)`（唔加「往」）；卡片／搜尋列／詳情頁都有 `九巴循環線`／`城巴循環線` badge。`CircularRoutes.audit()` 對照本機官方站名表，回報 `同官方一致／不一致／缺失／疑似混用`，**冇對照表時如實講「未載入」，唔會宣稱全部正確、亦唔會捏造或自動改官方名**
- **🐞 真實 kotlinc 修正（KOTLIN_TOPLEVEL）**：`StopDisplay`／`Trip`／`RouteCard` 係 `App1933Format.kt` 嘅**頂層 data class**，唔係 `App1933Format` object 成員；`EtaViewModel` 兩處 `App1933Format.StopDisplay(...)` 改為頂層 `StopDisplay(...)`（同 package 免 import）。另外 `nav_glass_hint` 含 `12%／6%／18%` 字面百分比但無 format 參數，加 `formatted="false"` 清走 aapt 警告。CI 新增「所有 `App1933Format.*` 必須解析到 object 成員」通用檢查，防止下次再靠真實 build 先發現
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

1. 由 [Releases](https://github.com/benyeung08/hketa/releases) 嘅**對應版本**（例如 `v1.0.0`）下載 **`hketa-v1.0.0-release.apk`**（正式版、已簽名）；或由任何一次 Actions run 下載唯一 Artifact `hketa-release-apk`（已簽名正式版）
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
gradle assembleRelease --no-daemon
# 輸出：app/build/outputs/apk/release/app-release.apk（已簽名；冇注入 release keystore 時自動用 repo 內置 dev key）
```

> ⚠️ Repo **刻意唔提交 `gradle-wrapper.jar`**，所以 `./gradlew` 會失敗；本機請用已安裝嘅 Gradle 8.7，或自行補回 wrapper jar。CI 直接下載官方 Gradle 8.7 發行版。

推送到 `main` **或**打 `v*` tag，`.github/workflows/build-apk.yml` 都會組裝**正式版（signed release）APK**，Actions「Artifacts」區從此只有一個 `hketa-release-apk`；**唔再組裝／上傳 debug APK**。打 tag 時額外跑版本三重校驗並發布到該版本嘅 Release：

```bash
# 1) 先 bump 兩個版本欄位
#    app/build.gradle.kts: versionCode = 3 / versionName = "1.0.2"
git add app/build.gradle.kts && git commit -m "Bump to 1.0.2" && git push origin main
# 2) tag 必須同 versionName 對應
git tag v1.0.2 && git push origin v1.0.2
```

### 🏷 正式版 APK 與 Releases 版本對應

每個 tag build 都會跑 **`RELEASE_VERSION_GATE`**，保證 Release 入面嗰個 APK 真係嗰個版本：

- **三重校驗**：`tag`（`v1.0.1`）＝ `app/build.gradle.kts` 嘅 `versionName` ＝ **`aapt dump badging` 讀到嘅實際 APK `versionName`**；另外 `versionCode`、`package`、`minSdk`、`targetSdk` 亦要比對。任何一項唔夾，workflow **喺建立 Release 之前直接 fail**，唔會上傳錯版本 APK
- **只附一個 `.apk`**：`hketa-v1.0.1-release.apk`（正式版、已簽名）。App 內更新會攞最新 Release 嘅第一個 `.apk` asset，所以開發版永遠唔會混入 Release
- **隨附兩份清單**：`VERSION_INFO.txt`（tag／versionName／versionCode／minSdk／targetSdk／APK SHA-256／簽章指紋／keystore 來源／commit／構建時間）同 `SIGNING_FINGERPRINT.txt`
- **說明對應版本**：`v1.0.0`／`v1.0.1` 各自自動用 `RELEASE_NOTES_v${版本}.md` 中英雙語內文；未有專屬 notes 嘅新版退回簡短說明，**唔會誤用其它版本**；`tag_name`／Release 標題顯式綁定 `github.ref_name`，tag 帶 `-` 後綴會標成 prerelease
- **同時上傳 Artifact** `hketa-release-apk`，方便喺 Actions 直接拎正式版；`generate_release_notes` 改為 `false`，想再加 GitHub 自動 "What's Changed" 可改回 `true`

`AppUpdate.compareVersions()` 比較 `tag_name` 同已裝版本嘅 `versionName`；呢個閘門正正令 `v1.0.1` ↔ `1.0.1` 成立，唔會出現「tag 新但 APK 舊」嘅假更新。



### 🔁 綠色小巴循環線：三個不同班次 + 首末站唔重複（GMB_CIRCULAR_ETA）

循環線嘅**第一站同最後一站係同一個實體站**（同一 `stop_id`，但官方 `stop_seq` 唔同）。舊版 `/eta/route-stop/{route_id}/{stop_id}` 嘅 URL 唔帶 `stop_seq`，程式又攤平晒所有 occurrence 冇去重，所以出现兩個問題：**第一站顯示三個一模一樣嘅班次**、**最後一站顯示同第一站完全相同嗰三班**。而家喺 Java 資料層加咗純函數選班次層：

- **三個不同班次**：用 `route_id + route_seq + timestamp + 備註` 做班次身份，`eta_seq` 刻意唔計入（官方會把同一部車用 `eta_seq 1/2/3` 重複回報）。去重後按時間排序，最多顯示 3 班**唔同**班次
- **最後一站唔重用第一站班次**：官方 ETA payload 若同時有兩個 `stop_seq`（例如 1 同 42），就各自取自己所屬 occurrence 嘅班次，並把第一站已顯示嘅班次由最後一站集合中移除
- **循環判定係資料驅動**：由官方 `route-stop` 嘅首尾 `stop_id` 相同／目的地帶「(循環線)」／ETA payload 有兩個 `stop_seq` 判定，**冇硬編任何路線號**；`StopOption.loopLastSeq` 由 `gmbStops()` 用官方最大 `stop_seq` 填入
- **誠實降級**：若官方只回報單一 `stop_seq`，最後一站取「扣除第一站後嘅下一組真實班次」；若官方實際只有 2 班，就顯示 2 班，**唔會捏造第 3 班**；收藏／直接輸入站號因得 `stop_id` 冇站序，退回純去重
- **唔影響其它營運商**：只有 `company == GMB` 走呢條路；`JointRoutes.augment()` 本來就只處理 KMB／CTB

`key()` 刻意唔包 `loopLastSeq`，已有收藏嘅 key 完全相容，唔使重新加入。

### 📦 為「已發佈嘅版本」補上正式版 APK（PUBLISHED_RELEASE_BACKFILL）

之前嘅流程只喺**新打 tag** 時附正式版；但一個早前已發佈嘅 Release（例如 `v1.0.0`）可能仍殘留舊 debug APK、甚至冇 APK（CI 而家已唔再產 debug artifact），而 App 內更新會攞最新 Release 嘅**第一個 `.apk`**，舊／debug asset 會靜靜贏。新增一個手動 workflow 專門補呢個位：

- **點用**：GitHub → **Actions → Build HK ETA APK → Run workflow**；`tag` 留空＝自動揀最新已發佈 Release，亦可填 `v1.0.0`；`include_prerelease` 預設關
- **用該 tag 自己嘅 source**：`git worktree` 由已發佈 tag 取出原始碼，CI 腳本則由 default branch 複製過去；**唔會用 main 嘅新 code 扮舊版本**。照樣跑 `RELEASE_VERSION_GATE`（`tag ↔ build.gradle.kts ↔ aapt badging`）同 `apksigner` 驗簽
- **只留一個正式版**：先刪走其它衝突 `.apk` assets，再 `gh release upload --clobber` 上傳 `hketa-v1.0.0-release.apk` ＋ `VERSION_INFO.txt`／`SIGNING_FINGERPRINT.txt`，最後檢查 Release 得返**一個** `-release.apk`
- **完全唔郁既有發布**：唔新建 Release、唔郁 tag、唔 bump 版本、**唔改 Release 內文**（`v1.0.0` 中英雙語說明原封不動）；非 `.apk` assets 亦保留
- **失敗即停**：tag 唔存在／係 draft／只有 prerelease／JSON 解析失敗，都會 `::error::` 列出可用版本並停止，唔會上傳任何 APK

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
| **搜尋** | 截圖分類式：8 交通類別 tab＋6 營運商 chips＋綠框「路線號碼」＋我的書籤／搜尋紀錄；**固定深色＋綠，顏色唔隨主題改變**；點列開**路線詳情（無地圖）**；進階保留 8 模式、`⇄`、篩選、站號直接查 | `SearchAdapters.kt` / `SearchHistory.kt` ＋ `RouteSearch.preload()` / `search()` |
| **應用程式設置** | ⚙ 設定入口＋六個區塊＋深色免責聲明卡 | `SettingsActivity` |

**只換介面，唔換資料**：`App1933Format.kt` 係純格式化層，把同一份 `EtaItem` 依 `(company, route, stop, dest)` 分組、取最早三班；`CardAdapters.kt` 只負責畫卡片。全部 8 個營運商、10 份官方目錄、`RouteDataStore`、`ApkSignature` 一行都冇改。

**明確不做**（需九巴私有 API／會員帳號／Play Services）：AI 智能問路、實時載客量、預計落車時間、九巴月票、club1933 會員、遊戲室、bot1933 實時對話、街景／步行導航、寵物巴士、廣告。**冇灰色佔位按鈕、冇假數據**。

### 🔍 搜尋頁（截圖分類式・顏色固定）

依你提供嘅截圖定稿；**顏色一律唔改**，只係版面同互動接上：

- **固定深色＋綠色，顏色唔變**：成頁 `#1D1D1F` 底、`#3DDC84` 綠重點；`values-night` 刻意唔覆寫 `@color/search_*`，系統切深／淺色都唔會變色，本輪**冇新增／冇改任何色值**
- **頂部 8 個交通類別 tab**：港鐵／**巴士**（預設選中，綠字＋綠底線）／專線小巴／渡輪／電車／的士／非專線小巴／居民巴士服務，可橫向捲動；冇同類公開實時 ETA API 嘅類別如實提示、**唔捏造班次**
- **第二排營運商 chips**：所有巴士類別（淺綠底深綠字）／城巴／九巴／龍運巴士／新大嶼山巴士／愉景灣/馬灣巴士；選中只切既有 drawable 同既有色，唔改色值
- **綠框「路線號碼」輸入框**：放大鏡 icon＋灰 hint，用系統 IME 即時查（唔再係自訂鍵盤）；`keypad.xml` 保留作舊檔，但已**唔被 include**
- **我的書籤／搜尋紀錄**：書籤空時顯示「沒有資料記錄」；紀錄列有營運商標記、綠色時鐘（第一條）、路線＋目的地＋備註、右側 ✕ 清除，寫入 `SearchHistoryPrefs`（最多 8 條、去重）
- **一入頁預載官方目錄**：`RouteSearch.preload()` 背景載入；冇歷史時如實標「目錄預載」顯示（唔當係搜尋紀錄、唔俾刪除）；點列記入歷史並開「路線詳情（無地圖）」，帶埋 `O／I／1` 方向
- **類別／chip 過濾只揀官方目錄已有公司**：港鐵→重鐵／輕鐵／接駁巴士、專線小巴→綠小巴、龍運共用九巴目錄並如實提示；愉景灣／馬灣冇 API 就留空
- **全螢幕**：進搜尋分頁隱藏紅色共用標題、狀態列同底部懸浮導航欄，頂部直接係分類 tab；其他分頁外觀跟隨系統設定**唔變**
- **進階保留原有功能**：8 模式、站號直接查、`⇄` 方向、站名／站號篩選收喺「進階」入口；「← 返回路線搜尋」返截圖式首頁；目的地全部讀官方目錄，**冇假數據、冇私有 API**

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
├── RELEASE_NOTES_v1.0.0.md           # v1.0.0 Release 中英雙語說明（收錄至 2026-11-19）
├── RELEASE_NOTES_v1.0.1.md           # v1.0.1 Release 中英雙語說明（現行，2026-11-20 至最新）
├── RELEASE_NOTES_v1.0.1_day1_2026-11-19.md  # 11-19 單日說明（備用，可直接貼）
└── RELEASE_NOTES_v1.0.1_day2_2026-11-20.md  # 20 號至最新單日說明（備用，可直接貼）
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
| `JointRoutes.java 唔見咗` / `聯營會拆做兩張卡` / `班次 chip 無「該班次」` / `缺九巴站名優先邏輯` / `聯營站名未用九巴` / `導航欄仲係貼底佔位列` / `位置未改成按屏高上移` / `城巴卡會沿用九巴站名` / `附近站未傳各公司站名對照` / `城巴獨有站仍會被改成九巴站名` / `未逐張卡分辨城巴／聯營站名` / `循環線未標記` / `循環線站序仍會被九巴化` / `App1933Format.StopDisplay 無法解析` / `無法補已發佈版本正式版 APK` / `綠小巴循環線班次去重／首末站分離失效` | 聯營／導航欄／城巴站名／循環線／Kotlin 符號／backfill／GMB 班次改動未同步：由完整 zip 覆蓋新檔 `JointRoutes.java` ＋其餘 8 個聯營相關檔（合併包一次過包齊），唔好逐個 upload |
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
- Release 說明：[RELEASE_NOTES_v1.0.1.md](RELEASE_NOTES_v1.0.1.md)（現行）／[RELEASE_NOTES_v1.0.0.md](RELEASE_NOTES_v1.0.0.md)
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

Current version **`v1.0.2`** (`versionCode = 3`) | Android 7.0+ (minSdk 24) | target / compile SDK 34.
Bilingual release notes: [RELEASE_NOTES_v1.0.2.md](RELEASE_NOTES_v1.0.2.md) (today / current), [RELEASE_NOTES_v1.0.1.md](RELEASE_NOTES_v1.0.1.md) (history), [RELEASE_NOTES_v1.0.0.md](RELEASE_NOTES_v1.0.0.md) (first release).

### ✨ Features

- **🚌 APP1933 layout**: large route number + "往 destination" + stop name with id + big minutes on the right; **Home / Favourites / Search / App settings** bottom tabs in a **centred dark charcoal pill** (56dp high, screen-width-aware inset, blue selected label) that follows system appearance
- **🗺 Tap a card for route detail**: red header + stop timeline + next three arrivals, **with no map at all**; fare / predicted alighting time honestly shown as "—"
- **🔍 Search home preloads routes**: official catalogue rows appear before typing; tapping `12 往 海麗邨 ›` opens the same route detail (with direction)
- **⟳ ETA auto-refresh**: Home nearby cards, the selected stop, favourites and route detail all poll in the foreground; 15 / 30 / 45 / 60 s selectable, switchable off, no re-location, no background polling
- **📦 Backfill an already-published Release**: a `workflow_dispatch` run rebuilds the signed production APK from THAT tag's own source, prunes conflicting `.apk` assets and leaves exactly one `-release.apk` — no new Release, no tag move, no body rewrite (PUBLISHED_RELEASE_BACKFILL)
- **🐛 CI compile fix (latest this round)**: rewiring the search page also cut the `permissionHint` field that 7 call sites still used, so Kotlin reported `Unresolved reference` and `assembleRelease` failed; the declaration + `findViewById` binding are restored, with 2 new symbol checks and a preflight drift rule so the same mistake is caught in 0 seconds
- **💾 Latest this round: RESTORE local data on the search screen**: `RouteSearch` was already local-snapshot-first with live API fallback, but after removing "Advanced" the search page showed **no local-data state at all**, and returning from Settings → route-data refresh/clear never re-preloaded, so the screen kept the previous (possibly live-fallback) rows and local data looked gone. A pure-Kotlin `SearchLocalData.kt` (signature / source tag / offline check) plus a `tvLocalDataStatus` row now shows `本機資料：N/M sources · K records · tap to update`, opens Settings on tap, and the preload hint is honestly tagged `來源：本機資料` / `來源：即時 API` — a live fallback is never dressed up as local data. `onResume()` compares the `updatedAt|sources|records|snapshot-files` signature and re-runs `searchRoutes()` + history when it changed. **No Advanced button restored, no new colour, no change to `RouteDataStore` / `RouteSearch` / the 8-operator data layer**; real-time ETA is still always live and never cached
- **🔎 Latest this round: REMOVE the search "Advanced" entry and MOVE the nav bar onto the main search page**: the `進階：站序／方向／篩選 ›` button, the back-to-search button, the advanced-mode flag and its switching function are all removed; the 8-mode / stop-sequence block stays permanently hidden (its Views are kept only for existing data-layer bindings, with no user entry point). Previously the floating liquid-glass bottom nav only appeared in advanced mode via `restoreChrome()` while the main search page hid it with `GONE`; now `hideChrome()` only collapses the red top header / status lines and the **nav bar stays VISIBLE on the main search page**, with `selectTab(2)` always showing the screenshot-style category search home. The identically-named advanced drawer button on the Settings screen is kept. The 10 `search_*` colours, `values-night`, glass geometry / 4 tabs, 8-operator data layer, position updates, more-routes-per-stop and same-number operator split are all untouched
- **🚌 Latest this round: SAME route number, DIFFERENT bus operators are shown separately**: `cardsFrom()` grouped by `route|stop|dest` without company, so independently-operated same-number routes (KMB 12 vs NLB 12, CTB 1 vs GMB 1) ended up in ONE card with trips from two companies mixed. A pure-Kotlin `SameRouteOperators.partition()` now keeps the existing `城巴/九巴` joint card **only for an exact KMB+CTB pair where both sides are non-circular**; every other combination (KMB+CTB+third, KMB+NLB, CTB+GMB, either side circular) is **split into one card per operator**, with its own three trips and its own operator meta (`九巴 / 龍運` / `城巴` / `嶼巴` / `綠色小巴`), sorted adjacent. The circular flag is now derived from THAT company's own arrivals instead of the whole bucket, and a split card falls back to its own `first.stopId` rather than inheriting the KMB id. Search rows already only collapse the KMB/CTB joint pair, so they needed no change. The 8-operator data layer, `JointRoutes.augment()`, permission gate, position updates, more-routes-per-stop and every colour are untouched
- **🚌 Latest this round: auto-locate now shows MORE routes per stop**: the old `nearbyStops(..., 6)` quota was eaten by the two `StopOption` rows (KMB / CTB) of the same physical stop, so the other operator's ETA for the nearest stop was silently cut. Now 24 candidates are fetched and a pure-Kotlin `NearbyMoreRoutes.select()` keeps **ALL company aliases of the nearest 6 physical stops**, grouped by stop name (capped at 12 parallel ETA queries to avoid 429). `cardsFrom()` never truncates routes, so every official route becomes a card, sorted by `(distance, route, destination)` to keep one stop's routes together; the status line reads `N stops · M routes · all routes per stop`. Both the initial auto-locate and last round's foreground position updates share `loadNearbyRoutes()`, and ETA auto-refresh reuses the expanded cache. The 8-operator data layer, permission gate and every colour are untouched
- **📍 Latest this round: auto-locate now UPDATES the device position**: once permission is granted, a foreground platform `LocationManager` listener is registered (no Play Services / WorkManager). Nearby routes are re-located and rebuilt only after the device moves more than **1/4 of the chosen radius (min 50 m)** AND at least **30 s** have passed, so GPS jitter cannot hammer the API. Registered in `onResume`, removed in `onPause` / `onDestroy`; `permissionHint` is still a **permission-consent hint, not a relocate button**. The status line shows `· 📍 位置自動更新`. Nothing is registered without consent or with location services off, and there is no Central fallback; ETA auto-refresh still reuses the cached stops only
- **📦 Latest this round: CI Artifacts expose ONLY the production APK**: the `Assemble debug APK` / `hketa-debug-apk` steps are gone; `assembleRelease` now runs on **every push** (no longer tag-only), a stable `hketa-release.apk` is prepared, and `hketa-release-apk` is the single APK artifact. Tag builds additionally copy a versioned name for the Release asset. 4 new negative checks plus preflight / workflow drift rules forbid any debug artifact from returning
- **🖼 This round: search page locked, colours unchanged**: `activity_main.xml` now matches the screenshot categories (8 tabs / 6 chips / bookmarks / history) and `MainActivity.kt` was rewired from the custom keypad; every `search_*` value is untouched, `values-night` never overrides them, UI + checks only
- **📅 Historical single-day notes (standby)**: `_day1_2026-11-19.md` (11-19 layout work) / `_day2_2026-11-20.md` (20th-through-latest work) are paste-ready; plain text, never auto-selected by the workflow, no CI impact
- **📝 Bilingual v1.0.0 notes (through 2026-11-19)**: `RELEASE_NOTES_v1.0.0.md` is fully symmetric at 5 sections / 33 bullets per language, covering the APP1933 search, map-free detail, location consent, floating nav bar and ETA auto-refresh, and explicitly excludes the 11-20 work; text-only, no code push needed
- **📝 Bilingual v1.0.2 notes (today 2026-11-24)**: `RELEASE_NOTES_v1.0.2.md` is fully symmetric at **5 sections / 43 bullets per language** with 2 `#### 📅` groups (morning / wrap-up), covering the screenshot-style search page, the `permissionHint` compile fix, production-APK-only Artifacts, foreground position updates, more routes per stop, same-number operator split, Advanced removal + nav relocation and restored local data; `app/build.gradle.kts` is bumped to `versionCode = 3` / `versionName = 1.0.2`, `RELEASE_NOTES_${TAG}.md` matches the `v1.0.2` tag automatically, and the two older note files get a historical note without touching any signed APK
- **📝 Bilingual v1.0.1 notes (this round: 2026-11-20 through the latest)**: `RELEASE_NOTES_v1.0.1.md` is rewritten to list **only 20th-through-latest**, fully symmetric at 5 sections / 33 bullets per language; the old "Yesterday / Today" split and duplicated bullets are removed, and joint routes / stop-name rules / version matching / backfill / CI fixes / the GMB circular-trip fix / the notes boundary / offline verification are all recorded
- **🧩 Three notes, old ones kept as history**: `v1.0.0` stops at 11-19, `v1.0.1` starts at 11-20 (its "through the latest" wording is a historical snapshot), **`v1.0.2` covers today 11-24**; `RELEASE_NOTES_${TAG}.md` picks the matching file per tag, and editing notes never touches the APK / tag or needs a CI re-run
- **📝 Bilingual v1.0.1 release notes**: `RELEASE_NOTES_v1.0.1.md` is fully symmetric in both languages (5 sections, 33 bullets each); `RELEASE_NOTES_${TAG}.md` matches the tag automatically and never reuses the v1.0.0 body
- **🆕 Delivered this round**: `HK_ETA_v1.0.1_Release_說明_中英雙語_2026-11-20至最新.md` is paste-ready, plus a minimal pack, a merged pack and the full-project zip
- **🔁 GMB circular trips fixed**: the first stop shows three DISTINCT trips instead of three copies of one, and the last stop shows its OWN three distinct trips instead of repeating the first (data-driven, never fabricated)
- **🏷 Production APK matches its Release version**: a `v1.0.0` tag attaches only `hketa-v1.0.0-release.apk`; a `tag ↔ build.gradle.kts ↔ actual APK` three-way gate blocks publishing on mismatch, with `VERSION_INFO.txt` / `SIGNING_FINGERPRINT.txt` attached
- **⬛ Every route-detail label is pure black**: the detail screen has a fixed white background, so stop names AND every label that used to share their colour (fare, alight, minutes unit, clock, tags, map-strip caption, footer note) are now pure `#000000`. `values-night` keeps the SAME pure black, and the footer background is pinned pale so black text is never on black. Dark mode no longer turns them into near-white invisible text; the red header / dark chips keep white text and the big minute numbers stay blue
- **🚌 Joint routes show "城巴/九巴"**: a route is joint ONLY when the SAME route number appears in BOTH official catalogues (KMB/LWB and Citybus) — catalogue-driven, no hard-coded route-number list. Home nearby cards, search / preload rows and the detail-screen caption show `城巴/九巴`; the KMB + CTB rows collapse into ONE card and each of the next three trips is tagged `該班次：城巴` / `該班次：九巴`. Home merges both operators' ETA for the same physical stop by Chinese stop name (unmatched names keep the old per-stop behaviour); **joint routes always display the KMB / LWB stop name and stop id** (the detail sequence is de-duplicated and home cards prefer the KMB StopOption; only when KMB has no catalogue data at all does it honestly fall back to the CTB name); the detail page expands KMB + CTB only, never the other 5 modes
- **🚌 ONLY Citybus routes use Citybus stop names (CTB_ONLY_NAME)**: a pure Citybus (non-joint) stop sequence never passes through the KMB-renaming logic at all — every stop keeps its official Citybus name and stop id. On a joint detail page, a stop that exists ONLY in the Citybus sequence also keeps the Citybus wording instead of copying any KMB name. Inside a merged home bucket, `App1933Format.resolveStopDisplay()` picks the label per card: a `CTB` card gets the Citybus `StopDisplay`, a `JOINT` card still gets the KMB label (previous request), a `KMB` card gets the KMB label, and every other company keeps its original `StopOption`. **Only Citybus cards / stops change to Citybus wording; joint and KMB routes are unaffected**
- **🔄 Circular routes shown separately (CIRCULAR_ROUTES)**: a circular line is detected from OFFICIAL data — `dest_tc` carrying `(循環線)` / `(Circular)`, or route-stop data with a single bound (no inbound) and the same first/last terminus. **No hard-coded route-number list.** ONLY circular lines use each operator's OWN stop wording: a Citybus circular line uses Citybus names + stop ids, a KMB circular line uses KMB names + stop ids; **normal-route rules are unchanged** (joint still KMB, pure CTB still Citybus). Circular rows are **never collapsed into a `城巴/九巴` joint row**
- **🔄 Operator ways + stop-name audit**: the KMB way is `213X 往 尖沙咀(循環線)` (App1933, keeps 往); the Citybus way is `26 勵德邨 - 荷李活道(循環線)` (no 往). Cards / search rows / the detail caption carry a `九巴循環線` / `城巴循環線` badge. `CircularRoutes.audit()` compares displayed stop names with the local official name table and reports `verified / mismatch / missing / foreign-suspect`; **with no table it honestly says so — it never claims everything is correct, never invents a name and never rewrites an official one**
- **🐞 Real kotlinc fix (KOTLIN_TOPLEVEL)**: `StopDisplay` / `Trip` / `RouteCard` are TOP-LEVEL data classes in `App1933Format.kt`, not members of the `App1933Format` object. Two `App1933Format.StopDisplay(...)` calls in `EtaViewModel` now use the top-level `StopDisplay(...)` (same package, no import). `nav_glass_hint` also gets `formatted="false"` because its literal `12% / 6% / 18%` were misread by aapt as format substitutions. CI now asserts that EVERY `App1933Format.*` reference resolves to an object member, so the next such mistake is caught before a real build
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

1. From the **matching version** on [Releases](https://github.com/benyeung08/hketa/releases) (e.g. `v1.0.0`), download **`hketa-v1.0.0-release.apk`** (signed production build); alternatively grab the single `hketa-release-apk` artifact (signed production build) from ANY Actions run
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
gradle assembleRelease --no-daemon
# Output: app/build/outputs/apk/release/app-release.apk (signed; falls back to the repo dev key when no release keystore is injected)
```

> ⚠️ The repo **deliberately does not ship `gradle-wrapper.jar`**, so `./gradlew` will fail. Use an installed Gradle 8.7 locally (or restore the wrapper jar). CI downloads the official Gradle 8.7 distribution instead.

Pushing to `main` **or** a `v*` tag triggers `.github/workflows/build-apk.yml`, which assembles the **signed production (release) APK**; the Actions "Artifacts" section now contains exactly one `hketa-release-apk` and **no debug APK is built or uploaded at all**. Tag builds additionally run the three-way version gate and publish to that version's Release:

```bash
# 1) bump BOTH version fields first
#    app/build.gradle.kts: versionCode = 3 / versionName = "1.0.2"
git add app/build.gradle.kts && git commit -m "Bump to 1.0.2" && git push origin main
# 2) the tag must match versionName
git tag v1.0.2 && git push origin v1.0.2
```

### 🏷 Production APK ↔ matching Release version

Every tag build runs **`RELEASE_VERSION_GATE`** so the APK inside a Release really IS that version:

- **Three-way check**: `tag` (`v1.0.1`) = `versionName` in `app/build.gradle.kts` = **the actual APK's `versionName` read by `aapt dump badging`**; `versionCode`, `package`, `minSdk` and `targetSdk` are cross-checked too. Any mismatch **fails the workflow BEFORE the Release is created** — a wrong-version APK is never uploaded
- **Exactly one `.apk` asset**: `hketa-v1.0.1-release.apk` (signed production). The in-app updater takes the first `.apk` asset of the latest Release, so a debug build can never slip into Releases
- **Two manifests attached**: `VERSION_INFO.txt` (tag / versionName / versionCode / minSdk / targetSdk / APK SHA-256 / signing fingerprint / keystore source / commit / build time) and `SIGNING_FINGERPRINT.txt`
- **Version-matched notes**: `v1.0.0` and `v1.0.1` each automatically use their own bilingual `RELEASE_NOTES_v*.md` as the Release body; a newer tag without its own notes falls back to a short body instead of reusing another version's notes. `tag_name` and the Release title are explicitly bound to `github.ref_name`, and a tag with a `-` suffix is marked prerelease
- **Also uploaded as the `hketa-release-apk` Actions artifact**; `generate_release_notes` is now `false` — set it back to `true` if you want GitHub's automatic "What's Changed" section appended

`AppUpdate.compareVersions()` compares `tag_name` with the installed `versionName`; this gate is what makes `v1.0.1` ↔ `1.0.1` hold, so a "new tag but stale APK" false update cannot happen.



### 🔁 GMB circular routes: three distinct trips + disjoint termini (GMB_CIRCULAR_ETA)

On a circular green-minibus route the **FIRST and LAST stop are the SAME physical stop** (identical `stop_id`, different official `stop_seq`). The old `/eta/route-stop/{route_id}/{stop_id}` URL carries no `stop_seq` and the code flattened every occurrence without de-duplicating, so the first stop showed **three copies of ONE trip** and the last stop showed **exactly the same three trips**. A pure selector in the Java data layer now fixes both:

- **Three DIFFERENT trips**: trip identity is `route_id + route_seq + timestamp + remark`; `eta_seq` is deliberately excluded because the operator repeats one minibus under `eta_seq 1/2/3`. After de-duplication the list is sorted chronologically and capped at 3 distinct trips
- **Last stop never reuses first-stop trips**: when the official ETA payload carries two `stop_seq` values (e.g. 1 and 42), each terminus takes its OWN occurrence and the first stop's trips are removed from the last stop's set
- **Data-driven detection**: circularity comes from the official `route-stop` first/last `stop_id`, a "(循環線)" destination, or two `stop_seq` values in the ETA payload — **no hard-coded route numbers**. `StopOption.loopLastSeq` is filled by `gmbStops()` from the official maximum `stop_seq`
- **Honest degradation**: with only one `stop_seq` in the payload the last stop takes the NEXT real arrivals after excluding the first stop's set; if the operator reports only 2 trips we show 2, **never inventing a 3rd**. Favourites / direct stop-id shortcuts have no sequence and degrade to plain de-duplication
- **No impact on other operators**: only `company == GMB` uses this path; `JointRoutes.augment()` only ever handled KMB / CTB

`loopLastSeq` is deliberately kept OUT of `key()`, so existing favourites stay byte-for-byte compatible.

### 📦 Add the production APK to an ALREADY-PUBLISHED Release (PUBLISHED_RELEASE_BACKFILL)

The tagged-release job only attaches the signed APK when a NEW `v*` tag is pushed. A Release published earlier (e.g. `v1.0.0`) may still carry a legacy debug APK — or none (CI no longer produces debug artifacts) — and the in-app updater takes the FIRST `.apk` asset of the latest Release, so a stale/debug asset would silently win. A manual workflow closes that gap:

- **How**: GitHub → **Actions → Build HK ETA APK → Run workflow**. Leave `tag` empty for the newest published Release, or type `v1.0.0`; `include_prerelease` is OFF by default
- **Builds from THAT tag's own source**: a `git worktree` materialises the published tag while the CI scripts are copied from the default branch. **main's newer code is never built as v1.0.0.** The same `RELEASE_VERSION_GATE` (`tag ↔ build.gradle.kts ↔ aapt badging`) and `apksigner` check still run
- **Exactly ONE production APK**: conflicting `.apk` assets are removed first, then `gh release upload --clobber` attaches `hketa-v1.0.0-release.apk` plus `VERSION_INFO.txt` / `SIGNING_FINGERPRINT.txt`; a final audit asserts the Release has exactly one `-release.apk`
- **Nothing else changes**: no new Release, no tag move, no version bump, **no rewrite of the Release body** (the bilingual v1.0.0 notes stay intact); non-`.apk` assets are preserved
- **Fails closed**: unknown tag / draft / prerelease-only / malformed JSON prints `::error::` with the available tags and uploads nothing

### ⚙️ Settings

The fourth bottom tab is renamed from **More** to **App settings**. It opens six independent sections: **Nearby route radius** (50–2000 m), **ETA auto-refresh**, **Route catalogue update** (caches catalogues only — ETA is always live), **Update application** (repo `benyeung08/hketa` is built in), **App signing consistency**, and **Navigation bar appearance**; the dark disclaimer card from the screenshot sits at the bottom of the page.

### 🔄 Update application (version pill)

The whole section now reproduces the screenshot's dark rounded bar: `v1.0.1 · 2　⟳　🕐　⧉`.

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

### 🔍 Search screen (screenshot categories, fixed palette)

Locked to the supplied screenshot; **no colour is changed**, only the layout and wiring:

- **Fixed dark + green, colours never change**: `#1D1D1F` page with a `#3DDC84` accent; `values-night` deliberately does NOT override `@color/search_*`, so switching system Dark / Light mode changes nothing — **no colour value was added or edited this round**
- **8 transport category tabs on top**: 港鐵 / **巴士** (selected by default, green label + green underline) / 專線小巴 / 渡輪 / 電車 / 的士 / 非專線小巴 / 居民巴士服務, horizontally scrollable; categories without an equivalent public real-time ETA API show an honest notice and **never fabricate trips**
- **Second row of operator chips**: 所有巴士類別 (light-green fill, dark-green text) / 城巴 / 九巴 / 龍運巴士 / 新大嶼山巴士 / 愉景灣/馬灣巴士; selecting one only swaps existing drawables and existing colours
- **Green-outlined 路線號碼 field**: magnifier icon + grey hint, queried live with the system IME (the custom keypad is gone); `keypad.xml` is kept as a legacy file but is **no longer included**
- **我的書籤 / 搜尋紀錄**: an empty bookmark list shows 「沒有資料記錄」; history rows carry an operator badge, a green clock (first row), route + destination + note and a right-hand ✕ to remove, persisted in `SearchHistoryPrefs` (max 8, de-duplicated)
- **Official catalogues preload on entry**: `RouteSearch.preload()` runs in the background; with no history the rows are honestly tagged 「目錄預載」 (not treated as history, not removable); tapping one records it and opens "route detail (no map)" with its `O / I / 1` direction
- **Category / chip filters only pick companies present in the official catalogues**: 港鐵 → heavy rail / Light Rail / MTR Bus, 專線小巴 → GMB, 龍運 shares the KMB catalogue with an honest hint; 愉景灣 / 馬灣 has no API so it stays empty
- **Full-screen**: entering Search hides the shared red header, status lines and the floating bottom bar, so the category tabs sit right at the top; every other tab keeps its system-following appearance **unchanged**
- **Advanced keeps every original control**: 8 modes, direct stop-ID lookup, `⇄` direction and stop-name/id filter live behind the "進階" entry; 「← 返回路線搜尋」 returns to the screenshot home; destinations come only from official catalogues, **no fake data, no private APIs**

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
├── RELEASE_NOTES_v1.0.0.md           # v1.0.0 bilingual release notes (through 2026-11-19)
├── RELEASE_NOTES_v1.0.1.md           # v1.0.1 bilingual release notes (current, 2026-11-20 through the latest)
├── RELEASE_NOTES_v1.0.1_day1_2026-11-19.md  # standalone 11-19 note (paste-ready)
└── RELEASE_NOTES_v1.0.1_day2_2026-11-20.md  # standalone 20th-through-latest note (paste-ready)
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
| `JointRoutes.java 唔見咗` / `聯營會拆做兩張卡` / `班次 chip 無「該班次」` / `缺九巴站名優先邏輯` / `導航欄仲係貼底佔位列` / `城巴獨有站仍會被改成九巴站名` / `循環線未標記` / `App1933Format.StopDisplay 無法解析` / `無法補已發佈版本正式版 APK` / `綠小巴循環線班次去重／首末站分離失效` (stale joint / nav / CTB-name / circular / Kotlin / backfill / GMB-trip files) | Overwrite the new `JointRoutes.java` plus the other 8 joint-related files from the full zip / merged pack; do not upload one by one |
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
- Release notes: [RELEASE_NOTES_v1.0.1.md](RELEASE_NOTES_v1.0.1.md) (current) / [RELEASE_NOTES_v1.0.0.md](RELEASE_NOTES_v1.0.0.md)
- Data sources: [DATA.GOV.HK](https://data.gov.hk), operator open-data feeds, [MTR Open Data](https://opendata.mtr.com.hk)

### 📄 License & disclaimer

[MIT](LICENSE). This is an unofficial project, not affiliated with any operator or government department. Arrival data remains the property of its original providers.

> This README is documentation only; editing it does **not** affect CI or the APK build.
