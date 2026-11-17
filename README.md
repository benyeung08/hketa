# HK ETA · 香港公共交通實時到站 Android App

一個開源、免 API key 的 Android App：輸入路線／線路代碼 → 載入沿途車站／月台 → 點選 → 查看實時預計到站時間（ETA）。資料直接來自香港運輸署、各巴士公司及港鐵的開放數據。

語言分工刻意做成 **Java + Kotlin 混合**：

| 層 | 語言 | 內容 |
| --- | --- | --- |
| 資料層 | Java | HTTPS/POST 請求、org.json 解析、CSV 目錄、時間換算、資料模型 |
| UI / 狀態層 | Kotlin | Activity、ViewModel、協程並行、LiveData、RecyclerView |
| 資料來源 | API | 7 種營運商的官方 JSON / CSV API，無需註冊、無需金鑰 |

## 支援範圍

| 模式 | 營運商 | 輸入範例 |
| --- | --- | --- |
| 自動 | 一次查齊以下 7 種 | `40`、`TML`（港鐵線路另填） |
| KMB | 九巴／龍運 | `1A`、`40`、`A31` |
| CTB | 城巴（已合併新巴路線） | `720`、`5B`、`B8` |
| NLB | 新大嶼山巴士 | `1`、`2`、`23` |
| GMB | 綠色專線小巴 | `1`、`101M`、`808` |
| MTR | 港鐵重鐵（10 條線） | `TML`、`ISL`、`EAL`… |
| LRT | 輕鐵 | `505`、`751`；留空顯示全部站 |
| MTRB | 港鐵接駁巴士 | `K12`、`K51`、`506` |

功能：中文站名、來回／上下行方向、目的地、第幾班、預計分鐘／時間、備註、月台、每 30 秒自動更新、下拉刷新、常用站收藏、**📍 附近車站定位（300／500／1000／2000 m）**、**⚙ 設定頁「線路資料更新」＋「更新應用程式」＋「App 簽名一致性」**、深淺色跟隨系統。

設定頁分三部分：

| 區塊 | 更新咩 | 點更新 |
| --- | --- | --- |
| 線路資料更新 | 官方路線／站點目錄（本機快照） | 手動／自動，可設僅 Wi-Fi |
| 更新應用程式 | App 本身（GitHub Release APK） | 查最新版本 → 下载 → 系統安裝介面 |
| App 簽名一致性 | 顯示本機憑證指紋、安裝前驗簽 | 自動比對，不符即拒絕安裝 |

三者完全獨立：**實時 ETA 永遠即時向官方 API 查詢，不受任何更新設定影響**。

附近車站模式不需要輸入路線：定位後列出半徑內的站（按距離排序），點選任一站即顯示**該站全部路線**的實時 ETA。

## 使用的官方 API

| 營運商 | Base URL | 主要 endpoint |
| --- | --- | --- |
| 九巴／龍運 | `https://data.etabus.gov.hk/v1/transport/kmb` | `/route-stop/{route}/{bound}/1`、`/eta/{stop}/{route}/{service_type}`、`/stop` |
| 城巴 | `https://rt.data.gov.hk/v2/transport/citybus` | `/route-stop/CTB/{route}/{direction}`、`/eta/CTB/{stop}/{route}`、`/stop` |
| 嶼巴 | `https://rt.data.gov.hk/v2/transport/nlb` | `/route.php?action=list`、`/stop.php?action=list&routeId=`、`/stop.php?action=estimatedArrivals&routeId=...&stopId=...&language=zh` |
| 綠小巴 | `https://data.etagmb.gov.hk` | `/route`、`/route/{region}/{route}`、`/route-stop/{route_id}/{route_seq}`、`/eta/route-stop/{route_id}/{stop_id}` |
| 港鐵重鐵 | `https://rt.data.gov.hk/v1/transport/mtr` | `/getSchedule.php?line={LINE}&sta={STA}&lang=tc` |
| 輕鐵 | 同上 | `/lrt/getSchedule?station_id={ID}&with_special=1` |
| 港鐵巴士 | 同上 | `/bus/getSchedule`（**POST** JSON：`{"language":"zh","routeName":"K12"}`） |
| 港鐵目錄 | `https://opendata.mtr.com.hk/data` | `mtr_lines_and_stations.csv`、`light_rail_routes_and_stops.csv`、`mtr_bus_stops.csv` |
| 整站 ETA（九巴） | `https://data.etabus.gov.hk/v1/transport/kmb` | `/stop-eta/{stop_id}` |
| 整站 ETA（城巴／嶼巴） | `https://rt.data.gov.hk` | `/v1/transport/batch/stopeta/{company_id}/{stop_id}?lang=zh-hant`（規格與範例對 `stopeta` / `stop-eta` 寫法不一，兩者都會嘗試） |
| 整站 ETA（綠小巴） | `https://data.etagmb.gov.hk` | `/eta/stop/{stop_id}`；再用 `/route/{route_id}` 換回路線號 |

附近車站的座標來源：九巴／城巴全量站點 JSON 的 `lat` / `long`、嶼巴各路線站序的 `latitude` / `longitude`、綠小巴 `coordinates.wgs84`、港鐵三份月度 CSV 內的座標欄（若該檔沒有座標欄位就自動略過該營運商）。站點清單會在記憶體快取 30 分鐘。

### 更新應用程式（App 版本）

App 會讀取 `GET https://api.github.com/repos/{owner}/{repo}/releases/latest`：

1. **填倉庫**：設定頁「發布來源」輸入 `owner/repo`（例如 `yourname/hketa`），或直接改 `app/src/main/res/values/strings.xml` 的 `default_github_repo` 一次打包進去。
2. **檢查更新**：比較 `versionName` 與 Release `tag_name`（容許 `v1.0.1`、`1.0.1`、`1.0.1-debug`）；有新版本就顯示版本、發布說明、APK 大小。
3. **下载並安裝**：只接受 HTTPS 且網域為 `github.com`／`objects.githubusercontent.com`／`release-assets.githubusercontent.com` 嘅 `.apk` asset，下載到 App 私人目錄 `filesDir/updates/`，經 `FileProvider` 交畀系統安裝介面。
4. **用戶確認**：Android 8+ 需要「安裝未知應用程式」權限；未開會自動跳系統設定頁，返嚟再按一次即接續安裝。**唔會靜默安裝**。
5. **自動檢查**：預設開啟，每 24 小時最多一次（App 啟動時靜默查、設定頁開啟時亦會查）；有新版本會喺主畫面狀態列顯示 🔔 提示。取消勾選就只會手動查。

**要出一個「更新」**：改 `app/build.gradle.kts` 嘅 `versionCode` / `versionName`，push 後打 tag（`git tag v1.0.1 && git push origin v1.0.1`）；workflow 會自動 `assembleRelease`、驗簽，並把 `hketa-v1.0.1.apk` 同 `SIGNING_FINGERPRINT.txt` 附到該 Release。冇 tag 就冇 Release asset，App 會顯示「最新版本沒有附帶 .apk」。

### App 簽名一致性（三層）

之前提到「runner 每次產生唔同 debug keystore」嘅問題，而家用三層處理：

**1. 固定開發金鑰（Build 層）**
`app/hketa-debug.keystore` 隨專案提交（公開、專為 sideload 用嘅開發金鑰，密碼亦寫死喺 `build.gradle.kts`，**絕對唔好拎去 Play 上架**）。所有 debug build、以及冇設 Secrets 嘅 release build 都用同一張憑證，所以 CI 每次 runner 都唔會再改變簽名。

**2. 可選正式金鑰（CI 層）**
喺 repo Settings → Secrets 加：

| Secret | 內容 |
| --- | --- |
| `HKETA_KEYSTORE_BASE64` | `base64 -w0 your-release.keystore` |
| `HKETA_KEYSTORE_PASSWORD` | keystore 密碼 |
| `HKETA_KEY_ALIAS` | key alias（預設 `hketa`） |
| `HKETA_KEY_PASSWORD` | key 密碼 |

workflow 會解碼到 `app/hketa-release.keystore`（已被 `.gitignore` 忽略），`assembleRelease` 用佢簽署。**冇設就退回第 1 層嘅開發金鑰，憑證仍然一致**。之後每個 tag 都要沿用同一張 key。

**3. 安裝前驗簽（App 層）**
`ApkSignature.kt` 喺交畀系統安裝介面之前：

- 讀取**下載 APK**嘅簽章憑證（`getPackageArchiveInfo`）同**已安裝 App**嘅憑證（`GET_SIGNING_CERTIFICATES` / `GET_SIGNATURES`），計 SHA-256 指紋
- 檢查 APK `packageName` 係咪 `hk.eta.app`
- 接受 Android 簽章輪替（API 28+ `signingCertificateHistory`）
- 若 `strings.xml` 嘅 `expected_signing_sha256` 非空，雙重鎖定：APK 同本機都要等於該指紋
- **唔一致就刪除 APK、拒絕安裝**，並喺設定頁列出兩邊指紋，唔會等到系統彈「簽名衝突」

workflow 亦會用 `apksigner verify --print-certs` 核對 release APK 憑證，同 `scripts/pin_signing_fingerprint.py` 自動寫入嘅 `expected_signing_sha256` 比對，**唔夾就直接 fail 個 release**；指紋會寫入 `SIGNING_FINGERPRINT.txt` 附到 Release 同 Step Summary。

設定頁「App 簽名一致性」會顯示本機指紋、最近一次驗證結果同兩邊憑證對照，方便手動核對 CI 印出嘅值。

**限制（要老實講）**
- **換 key 要過渡**：一旦改用 Secrets 嘅正式金鑰，憑證就同舊嘅開發金鑰唔同；已裝開發版嘅裝置要卸載一次（或用 Android 簽章輪替 lineage）。驗簽會明確拒絕，唔會靜靜地裝到一半先失敗。
- **指紋鎖定會擋輪替**：`expected_signing_sha256` 非空時，即使 Android 接受 lineage 簽章，App 都會要求指紋完全相同；換 key 要同時改呢個值再出新版本。
- **GitHub API 限流**：未登入每 IP 每小時 60 次；超限會提示稍後再試。
- **Play 政策**：`REQUEST_INSTALL_PACKAGES` 屬高風險權限，Google Play 會要求申報，而且原則上唔容許 App 自我更新。呢個專案定位係 GitHub sideload／自行散佈，唔係畀 Play 上架用。
- 只讀**最新一個** Release，唔列歷史版本；預先發布（prerelease）會標示但仍照樣顯示。
- 驗簽比對嘅係**簽章憑證**，唔係 APK 檔案雜湊；信任基礎係你自己的 repo、HTTPS 同 CI。要更嚴可再加 asset digest 比對。

### 設定頁下載的目錄檔案

| 本機檔案 | 來源 |
| --- | --- |
| `kmb_routes.json` / `kmb_stops.json` | `/v1/transport/kmb/route/`、`/v1/transport/kmb/stop` |
| `ctb_routes.json` / `ctb_stops.json` | `/v2/transport/citybus/route/CTB`（失敗退回 v1 `citybus-nwfb`）、`/v2/transport/citybus/stop` |
| `nlb_routes.json` + `nlb_stops.json` | `/v2/transport/nlb/route.php?action=list`；再並行抓各路線 `stop.php?action=list` 合併去重 |
| `gmb_routes.json` / `gmb_stops.json` | `data.etagmb.gov.hk/route`、`data.etagmb.gov.hk/stop`（後者為選用，失敗不影響整體） |
| `mtr_lines_and_stations.csv`、`light_rail_routes_and_stops.csv`、`mtr_bus_stops.csv` | 港鐵月度開放數據 CSV |
| `meta.json` | 每個來源的成功／失敗、筆數與時間戳 |

寫入用 `.tmp` → `rename` 原子更新；單一來源失敗會保留上一版檔案，不會清空整個快照。更新完成後會清掉記憶體快取（`EtaRepository` / `MtrCatalog` / `GeoStops`），下次查詢立即用新資料。

預設值：每日檢查一次、僅 Wi-Fi 自動下載（首次啟動在 Wi-Fi 會靜默更新）；手動按「立即更新」不受 Wi-Fi 限制。`HkEtaApp` 在 App 啟動時初始化私人目錄，`EtaViewModel` 依 `RouteDataPrefs` 判斷是否到期。

重鐵有效線路代碼：`AEL TCL TML TKL EAL SIL TWL ISL KTL DRL`。輕鐵站 ID 表內建（取自港鐵 Light Rail Next Train 數據字典）；重鐵站碼與港鐵巴士站名會在執行時讀取官方 CSV，CSV 讀不到時退回內建的常用站碼種子表，或直接顯示 `busStopId`。

官方文件：

- 九巴及龍運實時到站數據：https://data.gov.hk/tc-data/dataset/hk-td-tis_21-etakmb
- 城巴實時抵站時間：https://data.gov.hk/tc-data/dataset/ctb-eta-transport-realtime-eta
- 綠小巴實時到站數據：https://data.gov.hk/en-data/dataset/hk-td-sm_7-real-time-arrival-data-of-gmb
- 嶼巴 API 文檔：https://www.nlb.com.hk/datagovhk/BusServiceOpenAPIDocumentation2.0.pdf
- 港鐵 Next Train / Light Rail / MTR Bus 規格：https://opendata.mtr.com.hk/doc/
- 港鐵路線車費及無障礙設施 CSV：https://data.gov.hk/tc-data/dataset/mtr-data-routes-fares-barrier-free-facilities

`scripts/check_api_fields.py` 會用各官方規格的 sample response 靜態核對 Java 層讀取的欄位名稱、端點、版本與資源參照。

## 專案結構

```
hketa/
├── .github/workflows/build-apk.yml   # 固定金鑰、驗簽、打包 + Release
├── scripts/check_api_fields.py      # CI 靜態檢查
├── scripts/pin_signing_fingerprint.py # 把 keystore 指紋寫入 App 資源
├── app/hketa-debug.keystore         # 公開開發用固定簽名金鑰（非 Play 用）
├── app/src/main/java/hk/eta/app/
│   ├── HkEtaApp.kt                  # Application：初始化本機線路資料目錄
│   ├── api/                         # Java：HttpJson / EtaRepository / EtaModels /
│   │                                #       TimeUtils / MtrCatalog / GeoStops /
│   │                                #       RouteDataStore（目錄快照）/
│   │                                #       AppUpdate（GitHub Release + APK 下載）
│   ├── res/xml/file_paths.xml       # FileProvider 只暴露 filesDir/updates/
│   └── ui/                          # Kotlin：MainActivity / SettingsActivity /
│                                    #       EtaViewModel / RouteDataPrefs /
│                                    #       AppUpdatePrefs / ApkInstaller /
│                                    #       ApkSignature（安裝前驗簽）/
│                                    #       Adapters / Favorites / LocationHelper
├── app/src/main/res/                # layouts、strings、colors、theme、launcher icon
├── build.gradle.kts / settings.gradle.kts / gradle.properties
└── README.md / LICENSE
```

## 本機執行

需求：Android Studio（Koala 或以上）、JDK 17、Android SDK 34。

1. 用 Android Studio 開啟 `hketa/`，等待 Gradle Sync。
2. 接上手機或啟動模擬器，按 Run，或執行 `./gradlew assembleDebug`。
3. APK 位置：`app/build/outputs/apk/debug/app-debug.apk`。

> 本機同 CI 嘅 debug build 都用同一張 `app/hketa-debug.keystore`，所以本機裝嘅版本可以直接被 CI 產出嘅 APK 覆蓋安裝，唔會出現簽名衝突。

> 本 repo 不含 `gradle/wrapper/gradle-wrapper.jar`（避免提交來路不明的二進位檔）。Android Studio 會自動補齊；若只用命令列，先執行 `gradle wrapper --gradle-version 8.7`。CI 會自行下載官方 Gradle 8.7 發行版並直接用 `gradle assembleDebug` 打包。

## 放到 GitHub，讓 Actions 幫你產出 APK

在 `hketa/` 目錄執行：

```bash
git init
git add .
git commit -m "Multi-operator HK ETA Android app"

# 已安裝 GitHub CLI：
gh repo create hketa --public --source=. --push

# 或手動：
git remote add origin https://github.com/<你的帳號>/hketa.git
git branch -M main
git push -u origin main
```

推送後到 **Actions** 等 `Build HK ETA APK` 完成，在該次 run 的 **Artifacts** 下載 `hketa-debug-apk`。打 `v1.0.0` tag 會自動 `assembleRelease`、核對簽章指紋，再把 `hketa-v1.0.0.apk` 同 `SIGNING_FINGERPRINT.txt` 附加到 GitHub Release；也可按 **Run workflow** 手動重打包。

> 想用自己嘅正式簽名金鑰，先喺 repo Secrets 加 `HKETA_KEYSTORE_BASE64` 等四項（見「App 簽名一致性」），否則所有版本都用隨專案提交嘅固定開發金鑰，憑證一樣穩定可覆蓋安裝。

## 使用方式

1. 在「營運商」選單選模式（預設「自動（全部）」）。
2. 依提示輸入路線號／線路代碼，按「載入車站」。
   - 港鐵重鐵要填 **線路代碼**（例如 `TML`），不是車站名。
   - 輕鐵可留空顯示全部 68 個站，或填 `505` 只顯示該路線班次。
   - 港鐵巴士填 `K12`、`506` 等路線名。
3. 點選車站／月台，下方顯示班次；每 30 秒自動刷新，亦可下拉。
4. 已知站號／車站代碼時，可直接填「站號」再按「查 ETA」。
5. 按「☆ 加入收藏」保存，之後用「★ 我的收藏」一鍵查詢（附近站收藏後仍會查整站 ETA）。
6. 按「📍 附近車站」並選擇半徑，首次會要求定位權限；拒絕或定位逾時會改用中環預設位置並在狀態列註明。點選列表中的站即可看該站所有路線的班次。
7. 按右上角「⚙ 設定」→「立即更新線路資料」下载官方目錄；亦可設檢查頻率與「僅 Wi-Fi」。「清除本機線路資料」會改回每次直接打官方 API。
8. 同一頁「更新應用程式」填入 `owner/repo` →「檢查更新」→ 有新版本按「下载並安裝 APK」；App 會先自動驗簽（結果睇「App 簽名一致性」），通過後先至喺系統安裝介面確認。亦可「在瀏覽器開啟 Release 頁」手動下載。
9. 對照 Release 附件 `SIGNING_FINGERPRINT.txt` 同設定頁顯示嘅「本機簽章 SHA-256」；要雙重鎖定就把指紋填入 `app/src/main/res/values/strings.xml` 嘅 `expected_signing_sha256`。

## 注意事項

- 非服務時間、臨時改道、特別班次安排或 API 暫停時 `data` 可能為空；App 會顯示提示而非當機。港鐵 `status=0` 會直接顯示官方警報訊息。
- 綠小巴一條路線可有多個 `route_id`／`route_seq` 變體，App 會全部列出；ETA 用 `route_id + stop_id` 查詢。
- 嶼巴／綠小巴／港鐵巴士都需先解析內部 ID（`routeId`、`route_id`、`busStopId`），已做記憶體快取。
- 港鐵重鐵月台／終點站以 3 字母代碼顯示（例如 `POA` = 寶琳）；完整站名以官方 CSV 為準。
- **附近車站的限制**：座標必須來自官方全量站點資料。九巴／城巴／嶼巴最完整；綠小巴官方規格只有 `/stop/{stop_id}` 單站查詢，App 會嘗試 `/stop` 全量資源，若該端點不提供列表就自動略過綠小巴站；港鐵三份 CSV 若不含座標欄位，重鐵／輕鐵／港鐵巴士也不會出現在附近列表（仍可用路線模式查）。狀態列會說明使用的是 GPS 還是中環預設位置。
- 定位用 Android 平台 `LocationManager`，不依賴 Google Play Services；只取單次定位，不背景追蹤。
- **線路資料更新的範圍**：只快取「目錄類」資料（路線清單、站名、站點座標、港鐵 CSV）。ETA／到站時間、港鐵 Next Train、輕鐵月台、港鐵巴士班次**全部仍即時連線**；所以更新設定不會令到站時間變舊，亦不能離線查 ETA。
- 未有本機快照時（首次安裝／已清除），App 會直接向官方 API 查詢；有快照時優先讀本機，官方路線改動要等下一次更新才會反映。目錄類資料官方多數每日 05:00 或每月更新，無需頻繁手動按。
- 城巴路線清單同時準備了 v1 `citybus-nwfb` 後備端點；綠小巴 `/stop` 全量站點為選用來源，官方規格只保證 `/stop/{stop_id}`，失敗時附近列表會自動略過綠小巴站。
- 新巴 NWFB 已併入城巴 CTB，直接查 CTB 即可。愉景灣巴士、馬灣巴士、紅色小巴、電車、渡輪目前沒有同類公開實時 ETA API，未納入。
- CI 產出的是 **debug 簽章 APK**，適合自用與測試；上架 Play 需另建 release keystore 並改用 `assembleRelease` / AAB。
- 官方 API 有速率限制（429），「自動」模式會並行查 7 家，請避免惡意密集輪詢。

## 授權

MIT License — 見 [LICENSE](LICENSE)。開放資料來源為香港政府「資料一線通」、各巴士公司及港鐵公司，使用時請遵守其使用條款。本專案為獨立開源作品，並非任何機構的官方 App。
