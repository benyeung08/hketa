# HKATE v1.1.1

> 版本介面全面移植 code-to-app、App 內下載安裝、班次表打直、公告改為路線公告、修好搜尋鍵盤。
> 支援 **Android 8 – 17**。

---

## 🇭🇰 繁體中文

### ✨ 今版重點

**① 版本介面全面跟足 code-to-app 原版 `VersionPill`**

對照原碼（`AboutScreen.kt` 第 386–457 行）逐個數值校正：

| 項目 | 之前 | 原版 |
|---|---|---|
| 膠囊 padding | 12 / 6 dp | **14 / 6 dp** |
| 版本→掣間距 | 10 dp | **8 dp** |
| 掣與掣之間 | 2 dp | **10 dp** |
| Sync icon | 16 dp 白 | **14 dp primary** |
| History icon | 16 dp 白 | **15 dp primary** |
| Copy icon | 16 dp 白 | **14 dp onSurfaceVariant** |
| 底色 | `0xFF1E1E1E @ 85%` | **`surfaceContainerHigh @ 80%`** |
| 圓點徽章 `● 0` | 有 | **無（原版冇）** |

「有新版本」改為**版本號數字轉紅**表示 —— 版面同原版完全一致，同時唔會唔見咗提示。

底部狀態欄、設定頁、關於頁三處繼續共用同一個元件，改一處三處都改。

**② 移植 code-to-app 嘅「App 內下載 + 安裝」**

之前撳「下載」會開瀏覽器。而家跟原版 `ApkUpdateInstaller`：

```
撳「下載」→ App 內下載（進度條 % + MB）
        → 校驗 SHA-256（CI 自動生成 .sha256）
        → 撳「安裝」→ FileProvider → 系統安裝器
```

掣面文字跟足原版邏輯：

| 狀態 | 掣面 |
|---|---|
| 下載完成 | 安裝 |
| 下載失敗 | 重試 |
| 下載中／校驗中 | 取消 |
| 有新版本 | 下載 |
| 其他 | 關閉 |

配套加咗三樣必要嘅嘢：

- `REQUEST_INSTALL_PACKAGES` 權限（Android 8+ 要先授權「允許安裝未知來源」）
- `FileProvider`（Android 7+ 唔可以直接畀 `file://`，否則會抛 `FileUriExposedException`）
- `res/xml/file_paths.xml`（FileProvider 路徑白名單）

CI 亦加咗：
```bash
sha256sum HKATE-release.apk > HKATE-release.apk.sha256
```

**③ 班次時間表改為打直，淨顯示最快開出嗰班**

| 項目 | 之前 | 而家 |
|---|---|---|
| 排列 | 打橫（一格一站，要橫向捾） | **打直（一張卡一站）** |
| 顯示幾班 | 最多 6 班 | **淨第 1 班** |
| 站名 | 淨站名 | **站名 + 站號** |

```
第1班 · 最快開出
班次間隔：約 15 分鐘
────────────────────────────
1  海麗邨巴士總站 (SS667)    2 分鐘
   預計 21:10 到站
2  泓景臺 (SS280)            4 分鐘
   預計 21:12 到站
3  碧海藍天 (SS283)          7 分鐘
   預計 21:15 到站
```

「第 1 班」點樣嚟：官方 ETA 本身已按到站先後排好，**第 1 項就係最快開出嗰班** —— 係真實資料，唔係估算。

班次間隔仍然計到：內部仍然會抓齊各站全部班次，靠相鄰兩班嘅分鐘差推算，所以「約 15 分鐘」係計返出嚟嘅。

**④ 公告改為路線公告，唔再顯示 App 更新公告**

要先講清楚：**香港各巴士營辦商嘅官方 ETA 接口冇公告欄位**，淨係港鐵系（`getSchedule`）會一併返 service alert。所以「官方巴士路線公告」係冇得攞嘅。硬做就係自己抓網頁或編數據，兩樣我都唔會做。

所以分兩類處理：

| 來源 | 內容 | 標籤 |
|---|---|---|
| 港鐵系 | 真·官方服務提示（改道／延誤／暫停） | 港鐵官方 |
| 全部營辦商 | 由官方即時數據推導嘅路線狀況 | **由官方數據推導** |

推導出嚟嘅四種狀況：

- 全線冇班次（可能暫停服務／已過尾班車）
- 部分車站冇班次資料（會列出邊幾個站）
- 班次間隔偏大（≥30 分鐘／≥60 分鐘分兩級）
- 路線運作正常（明確講「查過」，唔會留空白）

**推導出嚟嘅一律標明「由官方數據推導」，絕唔會冒充官方公告。** 頁頂亦有一段說明解釋點解。

**⑤ 修好搜尋鍵盤顯示唔到**

搜尋頁個鍵盤**其實一直喺度，但被推咗出畫面外**。

原因係結果列表冇 `weight`：

```kotlin
LazyColumn(Modifier.fillMaxWidth())   // ← 冇 weight，想要幾高有幾高
RouteKeyboard(...)                    // ← 被推到螢幕外
```

`LazyColumn` 冇 `weight` 會按**內容高度**撐開，前面幾項已經占滿螢幕，鍵盤就落到畫面外。

改法係加 `weight(1f)`：

```kotlin
LazyColumn(Modifier.fillMaxWidth().weight(1f))
```

Compose 嘅測量順序係：**先測冇 weight 嘅子項**（鍵盤保住自然高度）→ **剩嘅空間先分畀 weight**。所以鍵盤一定有固定高度，列表食剩餘位並可捲動。

實測四款機：

```
大機 800dp → 列表 238dp，鍵盤 ✔
中機 700dp → 列表 138dp，鍵盤 ✔
細機 640dp → 列表  78dp，鍵盤 ✔
極細 600dp → 列表  38dp，鍵盤 ✔
```

字母圓鍵紫色亦由 `primary @ 30%` 加深到 **55%**，對比清楚好多。

### 📦 安裝

下載下面嘅 `HKATE-release.apk` 直接安裝。

**由 v1.0.0 或以上升級可以直接覆蓋**，唔使移除舊版 —— 呢啲版本共用同一個固定簽名（有效期至 2056 年）。

> 若果你裝嘅係**更早期嘅建置**（v1.0.0 之前、機器自動生成 debug key 嗰批），簽名唔同，Android 唔會畀你直接覆蓋，請先移除舊版。

**首次用 App 內下載要授權一次**：Android 8+ 會先跳去「允許安裝未知來源」設定頁，授權完返嚟再撳一次「下載」就得。呢個係系統限制，唔係 bug。

```
SHA1: 04:18:68:46:D7:62:04:C6:AC:05:7B:88:B1:7B:BB:F1:61:00:1F:46
```

### 🎯 支援範圍（v1.0.3 起）

**Android 8 – 17**（`minSdk 26` / `targetSdk 37` / `compileSdk 37`）—— Android 8、9、10、11、12、12L、13、14、15、16、17 全部裝到。

### ⚠️ 已知限制

- **班次表唔係官方紙本時刻表**：官方冇公開時刻表 API，所以係由即時到站預報構建（第 N 班 = 各站預報第 N 項）
- **「觀察到嘅營運時段」係觀察值**：官方冇公開頭／尾班車接口，靠日常開 App 慢慢累積，**唔係官方公佈** —— 唔好靠佢趕尾班車
- **官方公告淨係港鐵系有**：其他營辦商嘅 ETA 接口冇公告欄位，佢哋只會顯示「由官方數據推導」嘅路線狀況
- **班次表要聯網**：要抓各站即時預報先構建到
- **嶼巴英文名只覆蓋主要站**：109 個常用站名有官方英文對照，冷門村站可能查唔到 —— 查唔到就維持中文，唔會亂砌
- **真實地圖第一次要聯網**：預取係「睇嗰陣先下載」，睇完之後就有離線包
- **港鐵巴士路線靠內置清單**：官方冇全量路線接口，用已知清單（K12–K76）逐條查，清單以外唔會出現

### 🔒 隱私

只向官方接口發出 HTTPS 請求；定位淨係用喺當下排序附近車站，唔儲存、唔上傳。離線瓦片包同下載嘅 APK 淨係存喺 app 私有目錄。

---

## 🇬🇧 English

### ✨ Highlights

**① Version pill now matches code-to-app's `VersionPill` exactly**

Every value corrected against the original source (`AboutScreen.kt`, lines 386–457):

| Item | Before | Original |
|---|---|---|
| Pill padding | 12 / 6 dp | **14 / 6 dp** |
| Label → icons gap | 10 dp | **8 dp** |
| Icon-to-icon gap | 2 dp | **10 dp** |
| Sync icon | 16 dp white | **14 dp primary** |
| History icon | 16 dp white | **15 dp primary** |
| Copy icon | 16 dp white | **14 dp onSurfaceVariant** |
| Background | `0xFF1E1E1E @ 85%` | **`surfaceContainerHigh @ 80%`** |
| Dot badge `● 0` | present | **absent (not in the original)** |

"Update available" is now shown by **turning the version number red** — the layout stays identical to the original while the signal survives.

The bottom bar, Settings and About still share one component, so a single edit updates all three.

**② Ported code-to-app's in-app download and install**

Downloading used to open a browser. Now it follows the original `ApkUpdateInstaller`:

```
Tap Download → fetch inside the app (progress bar, % and MB)
             → verify SHA-256 (a .sha256 file CI generates)
             → tap Install → FileProvider → system installer
```

The button label follows the original logic:

| State | Button |
|---|---|
| Download finished | Install |
| Download failed | Retry |
| Downloading / verifying | Cancel |
| Update available | Download |
| Otherwise | Close |

Three things were required to make that work:

- `REQUEST_INSTALL_PACKAGES` permission (Android 8+ needs "install unknown apps" granted first)
- `FileProvider` (Android 7+ throws `FileUriExposedException` if you hand out a bare `file://`)
- `res/xml/file_paths.xml` (the FileProvider allow-list)

CI now also emits:
```bash
sha256sum HKATE-release.apk > HKATE-release.apk.sha256
```

**③ Trip timetable is vertical, showing only the next departure**

| Item | Before | Now |
|---|---|---|
| Layout | horizontal (one stop per cell, scroll sideways) | **vertical (one card per stop)** |
| Trips shown | up to 6 | **trip 1 only** |
| Stop name | name only | **name + stop code** |

```
Trip 1 · next departure
Headway: about 15 min
────────────────────────────
1  Hoi Lai Estate Bus Terminus (SS667)    2 min
   Arrives around 21:10
2  Banyan Garden (SS280)                  4 min
   Arrives around 21:12
3  Aqua Marine (SS283)                    7 min
   Arrives around 21:15
```

Where "trip 1" comes from: the official arrivals are already sorted by arrival time, so **item 1 is the next departure** — real data, not an estimate.

The headway still works: every stop's full arrival list is fetched and the gap between consecutive trips is derived from it, so "about 15 min" is genuinely computed.

**④ Notices are route notices now, not app announcements**

Worth stating up front: **the bus operators' arrival APIs have no notice field** — only the MTR family (`getSchedule`) returns a service alert. So official bus route notices simply aren't available. Faking it would mean scraping web pages or inventing data, and I won't do either.

Two categories instead:

| Source | Content | Label |
|---|---|---|
| MTR family | genuine official service alerts (diversion / delay / suspension) | MTR official |
| All operators | route condition derived from live official data | **Derived from official data** |

Four derived conditions:

- No trips on the whole route (possibly suspended / past the last trip)
- Some stops returned no arrivals (the stops are listed)
- Large gap between trips (two tiers: ≥30 min / ≥60 min)
- Route running normally (explicitly stated, so the tab is never blank)

**Anything derived is labelled "Derived from official data" and never passes itself off as an official notice.** A short explanation at the top of the tab says why.

**⑤ Fixed the search keyboard not appearing**

The keyboard **was always there — it was pushed off-screen.**

The results list had no `weight`:

```kotlin
LazyColumn(Modifier.fillMaxWidth())   // ← no weight, wants any height it likes
RouteKeyboard(...)                    // ← pushed outside the viewport
```

Without `weight`, `LazyColumn` measures at its **content height**; the items above already filled the screen, so the keyboard ended up below the fold.

The fix is `weight(1f)`:

```kotlin
LazyColumn(Modifier.fillMaxWidth().weight(1f))
```

Compose measures **non-weight children first** (so the keyboard keeps its natural height), then hands **whatever's left** to the weighted child. The keyboard therefore always has room and the list scrolls.

Measured on four screen sizes:

```
800 dp → list 238 dp, keyboard ✔
700 dp → list 138 dp, keyboard ✔
640 dp → list  78 dp, keyboard ✔
600 dp → list  38 dp, keyboard ✔
```

The round letter keys also went from `primary @ 30%` to **55%**, a much clearer contrast.

### 📦 Install

Download `HKATE-release.apk` below and install.

**Updating from v1.0.0 or later installs straight over the top** — those share one fixed signing key (valid to 2056).

> If you're on an **earlier build** (pre-v1.0.0, built with a machine-generated debug key), the signature differs and Android will refuse to overwrite — uninstall the old one first.

**In-app download needs one-time permission**: Android 8+ first opens the "install unknown apps" page; grant it, come back and tap Download again. That's a system requirement, not a bug.

```
SHA1: 04:18:68:46:D7:62:04:C6:AC:05:7B:88:B1:7B:BB:F1:61:00:1F:46
```

### 🎯 Support range (since v1.0.3)

**Android 8 – 17** (`minSdk 26` / `targetSdk 37` / `compileSdk 37`) — installs on Android 8, 9, 10, 11, 12, 12L, 13, 14, 15, 16 and 17.

### ⚠️ Known limitations

- **The trip table isn't an official paper timetable**: there's no public timetable API, so it's built from live arrivals (trip N = the Nth prediction at each stop)
- **The "observed service window" is an observation**: there's no public first/last-bus API, so it accumulates as you use the app and is **not official** — don't rely on it to catch the last bus
- **Official notices exist only for the MTR family**: other operators' arrival APIs have no notice field, so they only show derived route condition
- **The trip table needs a network**: it has to fetch live arrivals from each stop
- **NLB English covers major stops only**: 109 common Lantau stops have official English names; obscure village stops may not match and stay Chinese rather than being invented
- **The real map needs a network on first view**: tiles are prefetched *while you view*; after that the offline pack exists
- **MTR Bus routes come from a bundled list**: there's no bulk-route API, so a known list (K12–K76) is queried route by route

### 🔒 Privacy

Only HTTPS requests to the official endpoints. Location is used solely to sort nearby stops at that moment — never stored or uploaded. The offline tile pack and downloaded APKs stay in the app's private storage.

---

## 🏷 Tag this release

```bash
git tag v1.1.1
git push origin v1.1.1
```

CI will build the release APK and attach it to this release automatically.
