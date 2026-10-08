# HKETA v1.0.3

> **限制清掃版本。** 之前嗰份「已知限制」清單入面嘅項目，大部分已經解決或改善。

---

## 🇭🇰 繁體中文

### ✅ 已經解決嘅限制

**① 地圖係示意圖 → 而家係真實地圖**

之前淨係用 Canvas 按座標畫條折線，冇底圖冇街道。而家改用：

- **Leaflet + CARTO 深色瓦片**（`dark_all`）—— 有底圖、有街道名、可以縮放、撳站名會彈出
- **唔使 API key、唔使引入 Maps SDK**（零新增依賴）
- 深色瓦片配深色介面，唔會突然一塊光
- **離線／CDN 唔通會自動退回示意圖**，唔會留低一塊空白
- 睇過嘅路線有快取，**離線都出到圖**（第一次睇某條路線先要上網）

**② 鐵路站名冇英文 → 內置官方英文站名**

之前官方 CSV 只提供中文，英文介面下鐵路站名維持中文。而家 `rail.json` 內置咗：

- 重鐵 **98 個站**、輕鐵 **68 個站**，**全部齊、零遺漏**
- 用嘅係**官方英文站名**（Hong Kong、Tsim Sha Tsui、Tuen Mun Ferry Pier…），**唔係自行翻譯**
- 英文介面下鐵路線名同站名都係英文

**③ 未授權定位就冇嘢睇 → 手動選點**

唔想授權 GPS 都用得：主頁會出現「**手動選點**」，內置 **16 個預設位置**（中環、旺角、沙田、屯門、元朗、機場…），揀一個之後主頁照樣用嗰個座標搵附近車站。

**④ 輕鐵特別班次（9xx）→ 掃描搵返出嚟**

官方冇提供特別班次清單，所以唔使硬編碼 9xx：鐵路頁加咗「**掃描特別班次**」，掃各站嘅到站預報、減走常規 11 條線，剩低嘅就係特別班次。官方加減班次都跟到。掃描結果**存落本地快取**，掃一次之後開頁即刻顯示。

**⑤ 城巴／小巴要人手撳修復 → 自動背景修復**

以前唔撳「修復城巴／修復小巴」，附近車站就永遠淨係得九巴。而家**索引一建好就自動喺背景補**（增量 + 並發、唔彈訊息、唔阻住用），做過一次就記低，唔會每次開 App 重做。手動掣仲喺度。

**⑥ Widget 唔查城巴／嶼巴／小巴 → 全部支援**

之前歸因係「呢三家要逐條路線發請求，Widget 唔會咁嘈」。**查落其實唔係** —— 真正原因係**收藏嗰陣根本冇記低路線**（兩處收藏呼叫都傳咗 `null`）。而家：

- 預報頁收藏 → 記低當前路線
- 主頁收藏 → 記低最快嗰班嘅路線
- Widget 淨係查嗰一條路線（**1 個請求**）

所以 Widget 而家支援**全部營辦商**。

### 🔄 版面改動

**⑦ 附近車站改為「一條班次一張卡」**

- 一個站做小標題（站名 + 距離 + 收藏），下面每條班次一張獨立卡
- 卡片版面：路線號膠囊 +「往 XX」+ 站名（細字）+ 分鐘數
- **撳任何一張卡 → 直接打開嗰條路線嘅沿途車站**，唔使再經站嘅預報頁
- 冇班次（例如過咗尾班車）嘅路線照樣出卡，撳落去睇沿途車站
- 5 分鐘內嘅班次分鐘數用主色強調

### 📱 支援範圍：Android 8 – 17

呢個版本把支援範圍定為 **Android 8 到 Android 17**：

| 項目 | 值 | 對應系統 |
|---|---|---|
| `minSdk` | **26** | Android 8.0 Oreo（下線） |
| `targetSdk` | **37** | Android 17 Cinnamon Bun（上線） |
| `compileSdk` | **37** | Android 17 |

即係 **Android 8、9、10、11、12、12L、13、14、15、16、17 全部裝到**。

配套嘅工具鏈升級（**硬門檻，冇得揀**）：AGP 8.7.3 → **9.1.1**、Gradle 8.9 → **9.3.1**、
Kotlin 2.0.21 → **2.2.10**、Build Tools 35 → **36.0.0**。

兩個必須知道嘅改動：

1. **AGP 9 內置咗 Kotlin** —— 唔可以再寫 `id("org.jetbrains.kotlin.android")`。
2. **minSdk 26 唔使開 desugaring** —— 項目用咗 `java.time`，Android 8.0 起系統本身就有；開咗反而白白加大 APK。

大屏方面：Android 17 喺 `sw > 600dp`（平板／摺疊機）會忽略 `screenOrientation`，
App 必須自適應任何視窗尺寸。Manifest 保留 `portrait`（手機上仍生效、保持直向），
大屏則由系統自動轉為可調整大小 —— 版面用 Compose + 捲動，任何尺寸都唔會爆。

### ⚠️ 仍然存在嘅限制

- **嶼巴冇英文站名**：官方接口（`rt.data.gov.hk/.../nlb`）只提供中文站名，冇 `name_en` 欄位。
  呢個**唔會硬翻譯** —— 自行拼嘅英文名會同官方站牌對唔上，反而難搵車。
  英文介面下嶼巴站名維持中文（九巴／城巴／小巴／鐵路全部有官方英文）。
- **真實地圖第一次要聯網**：睇過嘅路線有快取、離線出到圖，但第一次睇某條路線仍然要上網攞瓦片同 Leaflet。
  要「完全離線、第一次都唔使網」就要打包離線瓦片，APK 會大好多，所以暫時唔做。
- **輕鐵特別班次要掃描先有**：官方冇特別班次清單介面，第一次要撳掣掃；結果只反映「掃描嗰刻」有冇呢個班次。
- **附近車站完整度**：自動背景修復做完之後城巴／小巴都會納入，但**第一次建索引嗰陣**會先出九巴，城巴／小巴要等背景修復跑完（通常一兩分鐘）。

### 📦 安裝

下載 `HKETA-release.apk` 安裝。**由 v1.0.0 起任何版本升級都可以直接覆蓋**，唔使移除舊版（共用同一個固定簽名）。

```
SHA1: 04:18:68:46:D7:62:04:C6:AC:05:7B:88:B1:7B:BB:F1:61:00:1F:46
```

> 若你係由**更早期嘅建置**升級（v1.0.0 之前，用機器自動產生 debug key 嗰批），仍須先移除舊版。

---

## 🇬🇧 English

### ✅ Limitations resolved

**① Schematic map → a real map**

Previously the map was just a Canvas-drawn polyline with no basemap. Now:

- **Leaflet + CARTO dark tiles** (`dark_all`) — real basemap, street names, zoom, and tappable stop popups
- **No API key, no Maps SDK** (zero new dependencies)
- Dark tiles match the dark theme — no glaring white panel
- **Falls back to the schematic view** when offline or if the CDN is unreachable, so you never get a blank box
- Routes you've already viewed are **cached and render offline** (only the first view needs the network)

**② No English rail station names → official English names built in**

The official CSV only ships Chinese names. `rail.json` now carries:

- **98 heavy-rail** and **68 Light Rail** stops — **complete, nothing missing**
- These are the **official English station names** (Hong Kong, Tsim Sha Tsui, Tuen Mun Ferry Pier…) — **not translations**
- Both line and station names appear in English under the English interface

**③ "Location not granted" dead end → manual location picker**

Works fine without GPS: Home offers **"Pick a location manually"** with **16 presets** (Central, Mong Kok, Sha Tin, Tuen Mun, Yuen Long, Airport…). Pick one and Home finds nearby stops from that coordinate.

**④ Light Rail special trips (9xx) → discovered by scanning**

There's no official list of special trips, so nothing is hardcoded: the Rail tab has **"Scan for special trips"**, which reads arrivals across stops and subtracts the 11 regular routes — whatever remains is a special trip. It keeps up as the operator adds or removes trips. Results are **cached locally**, so the Rail tab shows them immediately after one scan.

**⑤ Citybus / GMB needed a manual repair → automatic background repair**

Previously, if you never tapped "Repair Citybus / Repair GMB", nearby stops would only ever show KMB. Now it **repairs automatically in the background once the index is built** (incremental + concurrent, no toast, doesn't block you). It runs once and remembers. The manual buttons are still there.

**⑥ Widget skipped Citybus / NLB / GMB → now supports all of them**

We'd blamed this on "those three need one request per route, too noisy for a widget". **That wasn't the real cause** — bookmarks simply **never stored a route** (both bookmark call sites passed `null`). Now:

- Bookmarking from the arrivals page stores the current route
- Bookmarking from Home stores the soonest departure's route
- The widget queries just that one route (**a single request**)

So the widget now covers **every operator**.

### 🔄 Layout change

**⑦ Nearby stops: one card per departure**

- Each stop is a small header (name + distance + bookmark), with one card per departure below
- Card layout: route badge + "to XX" + stop name (small) + minutes
- **Tap any card to open that route's stop list directly** — no detour through the stop's arrivals page
- Routes with no departures (e.g. after last bus) still get a card, so you can view the stop list
- Arrivals within 5 minutes are highlighted in the accent colour

### 📱 Support range: Android 8 – 17

This release targets **Android 8 through Android 17**:

| Setting | Value | Platform |
|---|---|---|
| `minSdk` | **26** | Android 8.0 Oreo (floor) |
| `targetSdk` | **37** | Android 17 Cinnamon Bun (ceiling) |
| `compileSdk` | **37** | Android 17 |

That means **Android 8, 9, 10, 11, 12, 12L, 13, 14, 15, 16 and 17 can all install it**.

The toolchain moves with it (these are **hard minimums**): AGP 8.7.3 → **9.1.1**,
Gradle 8.9 → **9.3.1**, Kotlin 2.0.21 → **2.2.10**, Build Tools 35 → **36.0.0**.

Two changes worth knowing:

1. **AGP 9 bundles Kotlin** — you must not declare `id("org.jetbrains.kotlin.android")` any more.
2. **minSdk 26 needs no desugaring** — the project uses `java.time`, which Android 8.0 ships natively; enabling desugaring would only bloat the APK.

On large screens: Android 17 ignores `screenOrientation` above `sw > 600dp` (tablets, foldables),
so the app must adapt to any window size. The manifest keeps `portrait` (still honoured on phones
for an upright layout), while large screens become resizable automatically — the Compose layouts
scroll and fill, so nothing breaks at any size.

### ⚠️ Limitations that remain

- **NLB has no English stop names**: the official endpoint (`rt.data.gov.hk/.../nlb`) returns Chinese names only, with no `name_en` field.
  We **won't machine-translate these** — invented English names won't match the official signs, which makes finding your stop harder.
  NLB stop names stay Chinese in the English interface (KMB, Citybus, GMB and rail all have official English names).
- **The real map needs a network on first view**: viewed routes are cached and render offline, but viewing a route for the very first time still needs to fetch tiles and Leaflet.
  Fully offline maps would mean bundling tiles and a much larger APK, so that's not planned for now.
- **Light Rail special trips need a scan first**: there's no official list endpoint, so the first scan is manual, and results reflect only what was running at scan time.
- **Nearby-stop completeness**: after the automatic background repair, Citybus and GMB stops are included — but on a very first index build you'll see KMB first, with Citybus / GMB appearing once the background repair finishes (usually a minute or two).

### 📦 Install

Download `HKETA-release.apk` and install. **Any upgrade from v1.0.0 onwards installs straight over the top** — same fixed signing key.

```
SHA1: 04:18:68:46:D7:62:04:C6:AC:05:7B:88:B1:7B:BB:F1:61:00:1F:46
```

> If you're upgrading from an **earlier build** (pre-v1.0.0, built with a machine-generated debug key), uninstall that first.

---

## 🏷 Tag this release

```bash
git tag v1.0.3
git push origin v1.0.3
```

CI will build the release APK and attach it to this release automatically.
