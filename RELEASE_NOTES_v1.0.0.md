# HKETA v1.0.0

> **第一個正式版本。** 由原本的 WebView 外殼（`benyeung08/etaapp`，WebToApp fork）完全改寫為原生 Android App。

---

## 🇭🇰 繁體中文

HKETA 係一個香港巴士到站預報 App，直接對接各營辦商的官方公開資料接口，冇 WebView、冇網頁外殼、冇追蹤、唔收個人資料。

### ✨ 主要功能

**主頁（App1933 風格）**
- 打開就顯示附近車站的即時到站預報，站名附官方站號（如 `KT492`）
- 5 分鐘內嘅班次會用主色強調
- 撳心形可以收藏車站，收藏頁會並發刷新全部收藏站嘅班次

**搜尋**
- 分「**路線**」／「**車站**」兩個獨立分頁，各自記住自己嘅關鍵字
- 路線分頁有**專用鍵盤**（數字 0–9 + 圓形字母鍵 A B C E K M N S X + 刪除），唔使喺數字／英文版面之間切換
- 車站分頁會順便顯示該站停靠嘅路線號

**鐵路**
- 港鐵重鐵 10 條線 98 個站（機場快線／東鐵／屯馬／將軍澳／東涌／觀塘／荃灣／港島／南港島／迪士尼）
- 輕鐵 11 條常規線 68 個站（505／507／610／614／614P／615／615P／705／706／751／761P）
- 兩者都係**離線內置**（含官方座標），唔使等索引下載

**其他**
- 沿途車站 + 到站預報（每 20 秒自動刷新）
- 路線地圖（Canvas 繪製車站走線，唔使 API key）
- 桌面小工具：顯示收藏站的下一班車
- 附近車站（GPS，按距離排序）

### 🌐 支援營辦商

九巴 KMB、城巴 CTB、新大嶼山巴士 NLB、專線小巴 GMB、港鐵巴士 MTR Bus、港鐵重鐵、輕鐵

### 🈶 三語介面

繁體中文／**简体中文**／English，跟隨系統；亦可以喺設定手動切換。
到站預報嘅備註（月台、經馬場、服務延誤）都會跟住轉。

> 車站名同路線名維持官方原文，唔做硬翻譯 —— 否則會搵唔到對應站。

### 🛠 資料修復

設定頁有「**ETA 資料修復**」：遇到「暫時冇車站資料」、查唔到預報、或者站名變咗一串數字，可以重新下載官方資料；另有「淨係清除快取」。
統計頁會顯示「索引診斷」，講清楚邊個營辦商點解係 0（例如城巴官方冇全量車站接口）。

### 📦 安裝

下載下面嘅 `HKETA-release.apk` 直接安裝。

**若果之前裝過舊版，請先移除舊版** —— 舊版用嘅係機器自動產生嘅 debug key，簽名唔同，Android 唔會畀你直接覆蓋安裝。
由 v1.0.0 開始用固定簽名（有效期至 2056 年），以後每次更新都可以直接覆蓋。

```
SHA1: 04:18:68:46:D7:62:04:C6:AC:05:7B:88:B1:7B:BB:F1:61:00:1F:46
```

### ⚠️ 已知限制

- 鐵路站名冇官方英文（官方 CSV 只提供中文），線名已經係英文
- 輕鐵 9xx 特別班次唔獨立列線，但照樣出現喺預報入面
- 城巴／小巴要逐條路線抓取，深度修復建議喺 Wi-Fi 下執行
- 路線地圖係示意圖（冇底圖），要真地圖要接 Maps SDK

### 🔒 隱私

只向官方接口發出 HTTPS 請求；定位淨係用喺當下排序附近車站，唔儲存、唔上傳。

---

## 🇬🇧 English

HKETA is a Hong Kong bus arrival app that talks to the operators' official open-data endpoints directly — no WebView, no web wrapper, no tracking, no personal data collected.

### ✨ Features

**Home (App1933-style)**
- Nearby stops with live arrivals on launch, with official stop codes (e.g. `KT492`)
- Arrivals within 5 minutes are highlighted
- Tap the heart to bookmark a stop; the Favourites tab refreshes all bookmarked stops concurrently

**Search**
- Two separate tabs: **Routes** / **Stops**, each keeping its own query
- Routes tab has a **dedicated keypad** (digits 0–9 + round letter keys A B C E K M N S X + backspace) — no switching between numeric and alphabetic layouts
- Stops tab also lists the routes serving each stop

**Rail**
- MTR heavy rail: 10 lines, 98 stations (Airport Express, East Rail, Tuen Ma, Tseung Kwan O, Tung Chung, Kwun Tong, Tsuen Wan, Island, South Island, Disneyland Resort)
- Light Rail: 11 regular routes, 68 stops (505/507/610/614/614P/615/615P/705/706/751/761P)
- Both are **bundled offline** (with official coordinates) — no index download needed

**Also**
- Route stop list + arrivals (auto-refresh every 20s)
- Route map (Canvas-drawn stop polyline, no API key)
- Home-screen widget showing the next departure at your bookmarked stops
- Nearby stops via GPS, sorted by distance

### 🌐 Operators

KMB, Citybus (CTB), NLB, GMB minibus, MTR Bus, MTR heavy rail, Light Rail

### 🈶 Three languages

Traditional Chinese / **Simplified Chinese** / English — follows the system, or pick manually in Settings.
Arrival remarks (platform, via Racecourse, service delay) are localised too.

> Stop and route names stay in the official original text rather than being machine-translated, so they keep matching the official data.

### 🛠 Data repair

Settings has **"ETA data repair"**: if you hit "no stop data", get no arrivals, or see stop names as a string of digits, re-download the official data there. There's also "Clear cache only".
The stats page shows **Diagnostics** explaining why an operator shows 0 (e.g. Citybus has no bulk-stop API).

### 📦 Install

Download `HKETA-release.apk` below and install.

**If you installed an earlier build, uninstall it first** — those used a machine-generated debug key, so the signature differs and Android will refuse to overwrite.
From v1.0.0 the APK uses a fixed signing key (valid to 2056), so future updates install over the top.

```
SHA1: 04:18:68:46:D7:62:04:C6:AC:05:7B:88:B1:7B:BB:F1:61:00:1F:46
```

### ⚠️ Known limitations

- Rail station names have no official English (the official CSV is Chinese-only); line names are in English
- Light Rail 9xx special trips aren't listed as routes, but they do show up in arrivals
- Citybus / GMB are crawled route by route — run a deep repair on Wi-Fi
- The route map is schematic (no basemap); a real map needs Maps SDK

### 🔒 Privacy

Only HTTPS requests to the official endpoints. Location is used solely to sort nearby stops at that moment — never stored or uploaded.

---

## 🏷 Tag this release

```bash
git tag v1.0.0
git push origin v1.0.0
```

CI will build the release APK and attach it to this release automatically.
