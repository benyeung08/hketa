# HKETA · Kotlin / JSON 重製版

[English README](README.en.md) · **繁體中文**

把 GitHub 上的 [HKETA](https://github.com/shamough1792/HKETA)（原為 Cordova + JavaScript）用 **100% Kotlin** 重寫成原生 Android App，路線、站位、收費、服務班次全部由一份 **`hketa.json`** 驅動，並用 **GitHub Actions** 自動產出 APK。

原專案功能對照，本版都有：

- **四類交通**：巴士（KMB／CTB／NLB）、專線小巴 GMB、地鐵 MTR、輕鐵 LRT，共 30 條示範路線、355 個站位
- **完整路線資料**：每條都有**收費**（全程／小童／長者／分段）、去回程站位、**服務班次**（首尾車、繁忙／非繁忙間隔、全程時間）
- **路線搜尋 + 雙層篩選**：輸入路線號／中英起終點；模式 chip（全部／巴士／小巴／地鐵／輕鐵）＋營運商 chip
- **路線詳情**：站位列表、去程／回程切換、中英起終點、收費與服務班次摘要及完整對話框
- **ETA 到站時間**：逐站顯示班次時間、分鐘數、目的地、原定班次、港鐵／輕鐵月台
- **雙語介面**：右上角一鍵切 中／EN，各頁同步
- **地圖**：以 `geo:` 開啟手機內任何地圖 App
- **重新整理** + 最後更新時間
- **設定頁 → 路線資料更新**：由 GitHub 線上更新四類嘅收費／路線／服務班次，驗證後套用並快取，見下文
- **設定頁**：簽名一致性、App 版本、APK 檢查更新、**線上版本紀錄**（唯一版本紀錄來源）、關於
- **簽名一致性更新**：由 GitHub Releases 抓新版 APK，下載後先核對簽名先至允許安裝

> ⚠️ 內建 JSON 是**示範資料，不是即時到站時間**；站位座標為約略值。接真實 API 的方法見下文。

---

## 支援範圍：Android 8 – 17

| 系統 | API | 狀態 |
|---|---|---|
| 8.0 / 8.1 Oreo | 26 / 27 | ✅ 最低支援 |
| 9 – 16 | 28 – 36 | ✅ |
| 17 Cinnamon Bun | 37 | ✅ `targetSdk` |

`minSdk = 26`、`targetSdk = 37`、`compileSdk = 37`。Android 7.1 或以下（≤ API 25）會被系統／Play 拒絕安裝。

**唔設 `maxSdk`**：Android 18 推出後仍可安裝，只係未實測，避免日後用戶升級就裝唔到。若真要硬性封頂，喺 `app/build.gradle.kts` 取消註解 `maxSdk = 37` 即可。

因最低已經係 API 26，**所有支援裝置都行 adaptive icon**；`mipmap-*/ic_launcher*.png` 只保留作少數 OEM launcher 嘅 fallback。工具鏈亦因此升級到支援 API 37 嘅 AGP 9.1.1（見下表）。

---

## 永久簽名一致性（一次性設定）

Android 規定：**同一個 package name 嘅新版，必須用同一把 key 簽名先可以覆蓋升級**，否則會彈 `INSTALL_FAILED_UPDATE_INCONSISTENT_CERTIFICATES`。CI 預設嘅 `debug.keystore` 每部機都唔同，如果唔固定，每次 Actions 出嘅 APK 簽名都可能唔一樣，用戶裝咗就永遠更新唔到。

所以呢個專案做咗兩層：

**① 出 APK 用同一把 key** — `debug`、`release`、GitHub Release 全部綁同一個 `signingConfigs.permanent`。keystore 本體唔入 repo，只以 base64 放喺 GitHub Secrets，build 時還原到 runner 臨時目錄。

**② App 內自我核對** — CI 由 keystore 抽出憑證 SHA-256，自動寫入 `R.string.expected_signing_cert_sha256`；App 每次啟動比對自身簽署憑證，唔夾就拒絕載入並提示，防止被重新打包／二次簽名。喺「**設定 → 簽名一致性**」可隨時睇狀態、實際／預期指紋同重新驗證。

### 設定步驟（只做一次）

```bash
export HKETA_KEYSTORE_PASSWORD='一個長密碼'
export HKETA_KEY_PASSWORD='另一個長密碼'
bash scripts/gen_keystore.sh
```

腳本會生成 `hketa-release.jks`（RSA 4096、**有效期 36500 天 ≈ 100 年**）同 `hketa-release.jks.base64.txt`。然後去 GitHub repo → **Settings → Secrets and variables → Actions → New repository secret**，加呢 4 個：

| Secret | 值 |
|---|---|
| `HKETA_KEYSTORE_BASE64` | `.base64.txt` 嘅內容 |
| `HKETA_KEYSTORE_PASSWORD` | store 密碼 |
| `HKETA_KEY_ALIAS` | `hketa`（預設） |
| `HKETA_KEY_PASSWORD` | key 密碼 |

⚠️ 少任何一個，workflow 會 **fail fast** 並講清楚缺邊個，唔會靜靜用返臨時 debug key。`.jks`、`.p12`、`.base64.txt` 已加進 `.gitignore`。

### CI 每次會自動做

1. 由 Secret 還原 keystore → `keytool -list -v` 抽出憑證 SHA-256，注入 `HKETA_SIGNING_CERT_SHA256`（Gradle 再寫入 App 資源）。
2. `assembleDebug` / `assembleRelease` 都用同一把 key 簽名。
3. 用 `apksigner verify --print-certs` 反讀兩個 APK 嘅指紋，同 keystore 值比對，**唔夾就 fail**，並把指紋寫入 Actions 嘅 Step Summary。

本機想手動核對：

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs HKETA-debug.apk
# Signer #1 certificate SHA-256 digest: 應與 App 內「預期憑證」完全一致
```

> ⚠️ **金鑰即身份**：請離線備份 `.jks` 同兩個密碼。遺失或重建 = 已安裝用戶永久無法升級，只能改 `applicationId` 重新上架。技術上冇「永久」證書，但 100 年有效期 + 妥善保管就等同永久。若上 Google Play，建議另外啟用 Play App Signing，把呢把當 upload key。
>
> 本機開發若冇設環境變數，Gradle 會沿用 AGP 預設（debug key / release unsigned），`expected_signing_cert_sha256` 為空 → **App 內校驗自動關閉**，唔會誤彈警告。

---

## App 內設定與更新（簽名一致性）

首頁底部撳 **「設定」** 會開 `SettingsActivity`；撳隔離嘅 **「資料更新」** 會開同一頁並自動捲去「路線資料更新」區（`focus=data`）。下面按六部分講（UI 上係五張卡：簽名／檢查更新／路線資料更新／線上版本紀錄／關於）：

**① 簽名一致性**
- 即時狀態：`✅ 一致，可安全升級` / `⚠️ 未設定（開發版）` / `❌ 不一致，可能被重新打包`
- 顯示「實際憑證」同「預期憑證」SHA-256，可長按選取複製
- 「重新驗證簽名」即時再核對一次

> 刻意**唔提供「修改預期指紋」**：預期值只由 build 時嘅永久金鑰注入。若喺 App 內可改，任何人撳一下就 bypass 咗防篡改，成個機制就失效。

**② 檢查更新 → App 版本**（更新功能頂部嘅灰底小卡）

即時由 `PackageManager` 讀出，唔使上網：

| 欄位 | 來源 |
|---|---|
| 版本名稱 | `versionName` |
| `versionCode` | API 28+ 用 `longVersionCode`，26–27 用 `versionCode` |
| 首次安裝 / 最後更新 | `firstInstallTime` / `lastUpdateTime` |
| 簽名憑證 | 實際憑證 SHA-256 頭 12 位（對照上一區完整指紋） |

下面另有「目前版本」對照「最新版本」，一眼睇到差幾遠。

**③ 檢查更新**（呢個就係「簽名一致性嘅更新功能」）

```
撳「檢查 GitHub 更新」
  → GET api.github.com/repos/<OWNER>/<REPO>/releases/latest
  → 由 tag_name 同目前 versionName 做數字比對
  → 有新版 + Release 有 .apk → 出現「下載並驗證簽名」
     → 下載到 cacheDir/updates/hketa-update.apk（顯示 % 進度）
     → getPackageArchiveInfo 讀候選 APK 嘅 packageName + 簽名憑證
     → 三重核對：
         (a) packageName == hk.eta.app
         (b) 候選憑證 == 目前已安裝版本嘅憑證
         (c) 候選憑證 == build 時注入嘅預期指紋（如有）
     → 全部通過先出現「簽名一致，安裝更新」
     → ACTION_VIEW + content:// （FLAG_GRANT_READ_URI_PERMISSION）交畀系統安裝程式
  → 任何一步唔夾 → 即刻剷走下載檔、顯示原因、唔會出現安裝掣
```

即係話：**換咗 key、package name 唔同、APK 無簽名或損毀，都一律裝唔到**，唔會搞到用戶裝咗個以後升唔到級嘅版本。

**④ 設定 → 路線資料更新**（巴士／專線小巴／地鐵／輕鐵）

首頁底部「資料更新」掣會直接開設定頁並捲去呢區（`focus=data`）。一份 `hketa.json` 裝住四類資料，更新時一次過換：

| 類別 | mode | 營運商 | 內建示範路線 |
|---|---|---|---|
| 巴士 | `bus` | KMB／CTB／NLB | 12 |
| 專線小巴 | `minibus` | GMB | 4 |
| 地鐵 | `metro` | MTR | 10 |
| 輕鐵 | `light_rail` | LRT | 4 |

每條路線必備三組資料：**收費**（`fare`：全程／小童／長者、付款方式、分段收費 `sections`）、**路線**（`bounds` 去回程 + `stopIds` 對應 `stops`）、**服務班次**（`services`：日子、首尾車、繁忙／非繁忙間隔、全程時間）。路線詳情頁頂部有摘要，撳「收費／服務班次詳情」睇完整內容。

更新流程：

```
撳「檢查並更新路線資料」
  → GET raw.githubusercontent.com/.../hketa.json（Cache-Control: no-cache）
  → org.json parse
  → RouteDataUpdate.validate()：
      (a) modes 四類齊全、每類至少 1 條路線
      (b) 每條 route.co / route.mode 合法、number 非空
      (c) 所有 stopIds 都喺 stops 入面
      (d) 每條都有有效 fare.full > 0 同埋至少一筆 services（有首尾車）
  → 任何一項唔過 → 拒絕套用，逐條列出問題，原有資料完全唔郁
  → 全部通過 → 寫入 filesDir/route-data.json（資料快取）
              → 更新 AppState，即時生效
              → 顯示 v舊 → v新、路線／站位／收費／班次差異、四類統計
```

卡片本身會顯示目前資料版本、來源（內建／線上已快取）、產生時間，以及四類各自嘅「路線數 · 站位數 · 收費覆蓋 · 班次覆蓋」。另有「還原內建資料」（有確認對話框）刪除快取，改返 APK 內建 JSON。

> **同「線上版本紀錄」嘅分別**：呢度快取嘅係**路線資料本身**（離線要用），唔係操作紀錄；上一版合併後嘅**版本紀錄仍然只有 GitHub Releases 一個線上來源**，App 唔會寫入任何更新 log。開 App 時會優先讀已驗證嘅資料快取，冇／壞咗就自動落內建 JSON。首頁「重新整理」若目前用緊線上資料，會重新抓一次並行同一份驗證。

要改資料：改 repo 入面嘅 `hketa.json` 並 push（唔使出新版 APK），再喺 App 撳更新。未填 `AppConfig.kt` 嘅 `OWNER/REPO` 會提示，唔會打去假網址。接真實官方資料嘅擴充點見「接真實即時 ETA」一節；`RouteDataUpdate.validate()` 就係防止半份／錯格式資料落地嘅關卡。

**⑤ 線上版本紀錄**（唯一嘅版本紀錄）

原本「實時版本紀錄」＋「本機更新紀錄」已合併成**一個線上來源**：所有紀錄都即時由 `GET /repos/<OWNER>/<REPO>/releases?per_page=30` 讀取（帶 `Cache-Control: no-cache`），**唔快取、唔寫入本機**，裝置上唔再留低任何更新 log。一開設定頁自動載入，亦可撳「重新載入線上紀錄」。

頂部有兩行線上狀態：

- `● 已連線 · GitHub Releases（線上，唔快取）· 共 N 筆`／`● 線上查詢中…`／`● 連線失敗：xxx`（綠／藍／紅）
- `最後同步 HH:mm · 來源 api.github.com/.../releases`

每筆線上 Release 顯示：

- `tag_name`、Release 名稱、發布時間（GitHub UTC 字串）、`.apk` 檔名同大小
- 對照本機 `versionName` 標示：`[最新]`、`[目前安裝]`、`[可更新]`、`[較舊]`、`[預覽]`、`[無 APK]`
- 更新說明第一列；**撳一下展開／收合完整更新說明**，展開後可再撳「→ 開啟 GitHub Release 頁」

`draft` 會被過濾，`prerelease` 保留但標示。冇 Release／API 失敗會顯示線上連線狀態同原因，唔會白畫面。檢查更新、下載、驗簽、安裝嘅結果仍然照樣喺第 ③ 區即時顯示，但**唔再寫入任何本機紀錄**——版本紀錄以線上為準。

> ⚠️ GitHub 未認證 API 係 **每 IP 每小時 60 次**。開一次設定頁打一次，正常用唔會撞；若同一網絡短時間狂開，會見到 `HTTP 403`，等一小時或改用認證 token 即可。要「線上」就唔 cache，呢個係取捨。

**⑥ 關於**：Android 8–17 支援範圍、示範資料提示、永久簽名說明。

### 啟用前要改一處

`app/src/main/java/hk/eta/app/AppConfig.kt`：

```kotlin
const val OWNER = "你的GitHub帳號"
const val REPO  = "HKETA-Kotlin"
```

`REMOTE_JSON_URL`（資料來源）、`RELEASES_API`（檢查更新）、`RELEASES_HISTORY_API`（線上版本紀錄）都由呢兩個值組成，改一次就三邊同步。未改之前撳更新／載入紀錄會提示「尚未設定 GitHub 座標」，唔會打去假網址。

### 點樣出一個用戶收得到嘅更新

1. 改 `app/build.gradle.kts` 嘅 `versionCode` / `versionName`（例如 `2` / `1.1.0`）。
2. `git tag v1.1.0 && git push origin v1.1.0`。
3. Workflow 會自動 build、驗簽名，並把 **`HKETA-release.apk`** 貼去該 Release（Release body 亦附憑證指紋）。
4. 用戶開 App → 設定 → 檢查更新 → 下載 → 驗證 → 安裝，直接覆蓋升級。

> Release 一定要有 `.apk` asset；如果只 push 分支冇打 tag，`/releases/latest` 會 404，App 會顯示「請確認 repo 有 Release」。Workflow 已固定把 release APK 改名 `HKETA-release.apk` 並 attach，就係為咗俾呢個功能認出。

### 權限與相容

- `REQUEST_INSTALL_PACKAGES`：Android 8+ 由 App 內啟動安裝 Intent 必須聲明；用戶仍要喺系統設定畀 HKETA「允許安裝未知來源」，否則會彈提示引導去 GitHub Releases 手動下載。
- `ApkFileProvider`：純 framework `ContentProvider`，只 serve `cacheDir/updates/` 嘅 APK，`exported=false` + `grantUriPermissions=true`。因為 API 26 起唔可以用 `file://`，而專案刻意唔引 AndroidX，所以自己寫一個而唔用 `androidx.core.content.FileProvider`。
- 憑證讀取按系統分支：API 26–27 用 `GET_SIGNATURES`、28+ 用 `GET_SIGNING_CERTIFICATES`（`SignatureVerifier` 同時照顧已安裝 APK 同未安裝嘅 archive APK）。

---

## 點樣攞到 APK（唔使裝 Android Studio）

1. 先完成上面「永久簽名一致性」嘅 4 個 Secrets，再將呢個資料夾全部檔案 push 上 GitHub（預設分支 `main`）。
2. 去 **Actions** 頁，等 `Build HKETA APK` 跑完（約 3–6 分鐘）。
3. 入該次 run 嘅 **Artifacts**，下載 `HKETA-debug.apk`（或 `HKETA-release`），傳去 Android 手機安裝即可。兩個 artifact **共用同一把永久 key**，之後互換版本都可以直接覆蓋升級；Step Summary 會列出憑證指紋。

Workflow 會自己裝 JDK 17、Android 17 SDK（API 37、`build-tools;36.0.0`）、Gradle 9.3.1，由 Secrets 還原永久 keystore，先跑 `json.tool` 同 `scripts/validate.py`（檢查四類路線／收費／班次、XML、`R.id`、簽名與更新設定）再 `assembleDebug` / `assembleRelease`，最後用 `apksigner` 核對兩個 APK 嘅簽名指紋。Repo 入面**冇任何二進位檔**（連 `gradle-wrapper.jar`、`.jks` 都唔使），fork 完直接跑。

想出 Release：push 一個 `v*` tag，例如 `git tag v1.0.0 && git push origin v1.0.0`，APK 會自動貼去 GitHub Release。

---

## 本地建置

需要 JDK 17 + Android SDK（`platforms;android-37`、`build-tools;36.0.0`），並用 Android Studio Panda 3（2025.3.3）或更新版本：

```bash
# 若本機沒有 Gradle，先裝 9.3.1；有則跳過
gradle wrapper --gradle-version 9.3.1     # 可選：產生 wrapper 方便 Android Studio 開啟
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle assembleDebug
# 產物：app/build/outputs/apk/debug/app-debug.apk
```

用 Android Studio 開啟前，先執行上面嘅 `gradle wrapper` 一步，IDE 就認得個 project。

想本機都出「同一簽名」嘅 APK，喺 build 前 export 呢 4 個變數（同 CI 一致）：

```bash
export HKETA_KEYSTORE_FILE=/path/to/hketa-release.jks
export HKETA_KEYSTORE_PASSWORD='...'
export HKETA_KEY_ALIAS=hketa
export HKETA_KEY_PASSWORD='...'
export HKETA_SIGNING_CERT_SHA256=$(keytool -list -v -keystore "$HKETA_KEYSTORE_FILE" \
  -storepass "$HKETA_KEYSTORE_PASSWORD" -alias "$HKETA_KEY_ALIAS" \
  | grep -m1 'SHA256:' | sed 's/.*SHA256: //' | tr -d ':' | tr 'A-Z' 'a-z' | tr -d '[:space:]')
gradle assembleRelease
```

冇 export 就得返未啟用校驗嘅開發版，唔好攞去派發。

技術棧刻意保持最細：

| 項目 | 版本／選擇 |
|---|---|
| 語言 | Kotlin（AGP 9 內建，KGP 2.2.10） |
| AGP / Gradle | 9.1.1 / 9.3.1 |
| SDK | compile 37、min 26、target 37 |
| UI | 原生 Android View + XML layout（**不用** Compose／AndroidX） |
| JSON | Android 內建 `org.json`（**不用** Gson／Moshi／serialization） |
| APK 更新 | `HttpURLConnection` + `getPackageArchiveInfo` + 自建 `ContentProvider`（**不用** AndroidX FileProvider／OkHttp） |
| 路線資料更新 | `org.json` 解析 + `RouteDataUpdate.validate()` + `filesDir` 資料快取（**不用** SQLite／Room／第三方 JSON 庫） |
| 版本紀錄 | GitHub `/releases` 線上即時查詢，唯一來源（**不用** SharedPreferences／Room／DataStore／任何本機 log） |

API 37 必須用 AGP 9.1.1+（官方相容表嘅最低版本），而 AGP 9 已內建 Kotlin，所以唔再套用 `org.jetbrains.kotlin.android`、`kotlinOptions {}` 亦由 `compileOptions` 自動帶出 `jvmTarget`。`dependencies` 係**空嘅**：Kotlin stdlib 由內建 Kotlin 自動提供，Actions 唔會因為下載第三方庫而失敗。

> 排錯：若極少數環境下內建 Kotlin 冇自動帶入 stdlib，喺 `app/build.gradle.kts` 補一行即可（版本對應 AGP 9.1.1 內建嘅 KGP 2.2.10）：
> `dependencies { implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.10") }`

本機改完設定，可跑 `python3 scripts/validate.py` 一次過檢查 JSON 參照、XML 資源、`R.id` 對應、workflow 同工具鏈版本是否一致（Python 3；有 PyYAML 會順便校驗 YAML）。

---

## JSON 就係資料庫

`app/src/main/assets/hketa.json` 一個檔決定成個 App 嘅內容，改資料唔使改 Kotlin、唔使重新出 APK（如果用 GitHub 託管模式）。

```jsonc
{
  "version": "2.0.0",
  "generatedTimestamp": "2026-11-10T19:00:00+08:00",
  "demo": true,
  "note": { "zh": "示範資料，非即時", "en": "Demo data, not live" },
  "fareDisclaimer": { "zh": "收費只作參考，以營運商公布為準", "en": "Fares are indicative; refer to operators" },
  "apiDocs": { "kmb_eta_spec": "https://..." },
  "modes": [
    { "id": "bus",        "zh": "巴士",     "en": "Bus" },
    { "id": "minibus",    "zh": "專線小巴", "en": "Minibus" },
    { "id": "metro",      "zh": "地鐵",     "en": "Metro" },
    { "id": "light_rail", "zh": "輕鐵",     "en": "Light Rail" }
  ],
  "operators": [ { "id": "KMB", "zh": "九巴／龍運", "en": "KMB & LWB" } ],
  "stops": {
    "KMB1_O_01": {
      "name": { "zh": "竹園邨總站", "en": "CHUK YUEN ESTATE BUS TERMINUS" },
      "lat": 22.3417, "lng": 114.1947
    }
  },
  "routes": [{
    "id": "KMB_1", "co": "KMB", "mode": "bus", "number": "1",
    "name": { "zh": "竹園邨 ↔ 尖沙咀碼頭", "en": "Chuk Yuen ↔ Star Ferry" },
    "bounds": [{
      "dir": "O",                       // O 去程 / I 回程；港鐵亦可用 UT / DT
      "label": { "zh": "去程", "en": "Outbound" },
      "orig":  { "zh": "竹園邨", "en": "CHUK YUEN ESTATE" },
      "dest":  { "zh": "尖沙咀碼頭", "en": "STAR FERRY" },
      "stopIds": ["KMB1_O_01", "KMB1_O_02"]
    }],
    "fare": {                            // 收費（HKD）
      "currency": "HKD", "full": 6.7, "child": 3.4, "senior": 2.0,
      "method": { "zh": "八達通／現金", "en": "Octopus / cash" },
      "note":   { "zh": "分段收費另計", "en": "Section fares apply" },
      "sections": [
        { "from": { "zh": "竹園邨", "en": "Chuk Yuen" },
          "to":   { "zh": "旺角", "en": "Mong Kok" }, "full": 4.2 }
      ]
    },
    "services": [{                       // 服務班次（可多筆：平日／週末假日）
      "days":     { "zh": "星期一至五", "en": "Mon–Fri" },
      "first": "05:30", "last": "00:20",
      "peakMin": 5, "offPeakMin": 10, "journeyMin": 45,
      "note": { "zh": "繁忙時間加密", "en": "More frequent at peak" }
    }]
  }],
  "etas": {
    "KMB1_O_01": {
      "generatedTimestamp": "2026-11-09T18:40:00+08:00",
      "etas": [{
        "seq": 1, "time": "18:46", "minutes": 5,
        "dest":   { "zh": "尖沙咀碼頭", "en": "STAR FERRY" },
        "remark": { "zh": "原定班次", "en": "Scheduled Bus" },
        "platform": "1", "scheduled": true
      }]
    }
  }
}
```

幾個欄位習慣沿用返 DATA.GOV.HK：`co`、`dir` = `O`/`I`、`seq`、`eta` 時間、`rmk` 備註，日後轉真實 API 唔使大改 model。冇 ETA 嘅站會顯示「暫無到站資料」。

**`modes`／`fare`／`services` 係線上更新嘅必要欄位**：`RouteDataUpdate.validate()` 會逐條檢查，缺一不可，所以唔會出現「更新完但冇收費／冇班次」嘅半份資料。設定頁嘅「路線資料更新」就直接改呢份 JSON，唔使改 Kotlin、唔使重新出 APK。

---

## 改路線資料唔使出新版 APK

1. 改 `app/src/main/java/hk/eta/app/AppConfig.kt` 嘅 `OWNER` / `REPO`（資料來源、APK 檢查更新、線上版本紀錄三邊共用；`EtaRepository` 讀 `AppConfig.REMOTE_JSON_URL`）。
2. 改 `app/src/main/assets/hketa.json`（四類路線／收費／服務班次）並 push 上 GitHub。
3. 手機撳首頁「**資料更新**」→ 設定頁「**檢查並更新路線資料**」，驗證通過即套用；之後「重新整理」亦會重新抓。

想即時還原：設定頁「還原內建資料」。讀取／驗證失敗會沿用原有資料，唔會白畫面。改用 gh-pages 亦可，把 `AppConfig.REMOTE_JSON_URL` 改成 `https://<OWNER>.github.io/<REPO>/hketa.json` 就得。

---

## 接真實即時 ETA

`Fmt.kt` 入面有 `LiveEtaProvider` 介面，`DemoEtaProvider` 而家讀 JSON。上線只需寫一個新實作（用 `HttpURLConnection` 打 API），再喺 `EtaActivity.rebuild()` 換掉嗰一行。

`apiDocs` 同以下官方文件對應（**請以資料字典最新版本的 endpoint 為準**）：

- DATA.GOV.HK 開放資料入口：https://data.gov.hk/en/
- 九巴／龍運 ETA API 規範：https://data.etabus.gov.hk/datagovhk/kmb_eta_api_specification.pdf
- 城巴／嶼巴 Stop-ETA API 規範：https://static.data.gov.hk/opendata/eta/bus-route-list-and-eta-specific-stop-api-specifications.pdf
- 城巴／嶼巴 Stop-ETA 資料字典：https://static.data.gov.hk/ogcio/datagovhk/opendata/eta/bus-route-list-and-eta-specific-stop-api-data-dictionary.pdf
- 已整合多營運商嘅開源參考：https://github.com/hkbus/hk-bus-eta

常見回傳欄位：`co`、`route`、`dir`、`seq`、`stop`、`dest_en/tc`、`eta_seq`、`eta`（ISO 8601）、`rmk_en/tc`；港鐵列車另有 `plat`（月台）同 `time`。將 `eta` 轉成 `Eta.time` 並計出 `minutes` 就得，UI 完全唔使改。

**路線／收費／服務班次**亦係同一思路：寫一個 aggregator，把巴士、專線小巴、港鐵（地鐵＋輕鐵）嘅 route-list、fare table、timetable 轉成上面 `hketa.json` 嘅 schema（`modes` / `fare` / `services`），再交畀 `RouteDataUpdate.validate()` 把關，最後由設定頁「路線資料更新」派發。各類資料嘅實際 endpoint 同授權條款請以 **DATA.GOV.HK 及營運商最新公佈為準**；內建 30 條路線同收費全部係示範約值，唔代表官方票價或班次。

---

## 目錄

```
.github/workflows/build-apk.yml   # push 即出 APK
README.md                        # 繁體中文說明（本檔）
README.en.md                     # English README
app/src/main/assets/hketa.json    # 唯一資料來源
app/src/main/java/hk/eta/app/
  Models.kt            # data class + AppState（語系／資料）
  JsonParser.kt        # org.json → data class
  EtaRepository.kt     # assets / GitHub raw 兩種來源
  RouteDataUpdate.kt   # 四類資料驗證、統計、線上更新、快取、還原
  DataUpdater.kt       # 首頁「重新整理」共用入口（行同一份驗證）
  Fmt.kt               # 顯示格式（收費／班次）+ LiveEtaProvider 擴充點
  AppConfig.kt         # GitHub OWNER / REPO（資料來源 + 檢查更新 + 線上版本紀錄共用）
  MainActivity.kt      # 搜尋、篩選、路線列表、啟動時簽名校驗、設定入口
  SignatureVerifier.kt # 已安裝／候選 APK 嘅憑證 SHA-256 核對
  UpdateChecker.kt     # App 版本、Releases 查詢／線上歷史、下載、簽名比對、安裝 Intent
  ApkFileProvider.kt   # 純 framework content:// provider（API 26+ 安裝用）
  SettingsActivity.kt  # 設定頁：簽名 / App 版本 / APK 更新 / 路線資料更新 / 線上版本紀錄 / 關於
  RouteDetailActivity.kt  # 去回程、站位、地圖
  EtaActivity.kt       # 班次列表、重新整理
app/src/main/res/
  layout/ values/ drawable/           # XML layout / colors / styles / chip 背景
  mipmap-mdpi … mipmap-xxxhdpi/       # ic_launcher.png + round（OEM fallback）
  drawable-nodpi/                     # adaptive 前景／背景（512×512）
  mipmap-anydpi-v26/                  # adaptive icon（minSdk 26 起全部裝置都用）
art/logo_source.png                  # 原始 logo 素材，不進 APK
art/icon_preview.png                 # logo 預覽，不進 APK
scripts/gen_icon.py                  # 換圖後重新產生全部 icon 資源
scripts/gen_keystore.sh              # 一次性產生永久簽名金鑰
scripts/validate.py                  # 本機一鍵檢查設定／資源一致性
```

---

## App Logo

Launcher icon 用你提供嘅彩虹巴士圖（原檔 1080×1080，保留喺 `art/logo_source.png`）：

- **全部支援裝置（API 26+）**：`mipmap-anydpi-v26/` 嘅 adaptive icon。因系統只顯示 108dp 畫布中央嘅 72dp，腳本會把原圖等比縮到 66.7% 先置中做前景，背景用純彩虹漸層填滿；圓形／方圓遮罩落去，機械人頭同車輪都完整保留，亦唔會露白邊。`art/icon_preview.png` 右邊就係模擬效果。
- **Fallback**：`mipmap-*/ic_launcher.png`（48／72／96／144／192 px 五種密度）同 `ic_launcher_round.png` 保留畀少數唔讀 adaptive XML 嘅 OEM launcher；`AndroidManifest.xml` 已設 `android:roundIcon`。minSdk 26 之下正常唔會用到。

想換圖：把新圖（正方形）覆蓋 `art/logo_source.png`，喺 repo 根目錄執行 `python3 scripts/gen_icon.py`（需要 Pillow），就會自動重建全部尺寸同 adaptive 資源，`art/icon_preview.png` 可即刻睇效果。改完 push，Actions 出嚟嘅 APK 就會用新 logo。

---

## 鳴謝與授權

- 功能參照 [shamough1792/HKETA](https://github.com/shamough1792/HKETA)（MIT）
- 資料格式概念參考 [hkbus/hk-bus-eta](https://github.com/hkbus/hk-bus-eta) 及 DATA.GOV.HK 資料字典
- 本專案 MIT，見 `LICENSE`

即時交通資料版權屬運輸署、各營運商及港鐵公司；示範 JSON 內嘅路線、站位、班次、座標僅供介面演示，不應作乘車依據。
