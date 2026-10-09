# HKETA v1.0.4

> 嶼巴站名補返英文、地圖有離線包、輕鐵特別班次自動掃描、版本介面統一。
> 支援 **Android 8 – 17**。

---

## 🇭🇰 繁體中文

### ✨ 今版重點

**① 嶼巴站名終於有英文**

官方 `nlb` 接口**冇 `name_en` 欄位**（九巴／城巴／小巴／鐵路全部都有，淨係嶼巴冇），所以英文介面下嶼巴站名一直維持中文。

今版內置咗 `data/NlbStopNames.kt` —— **109 個**大嶼山站名嘅中英對照：

- 覆蓋碼頭（梅窩、東涌、大澳）、沙灘（貝澳、長沙、塘福）、村落（伯公坳、水口、石壁）、屋苑（映灣園、東環、滿東邨）、景點（寶蓮寺、天壇大佛、亞洲國際博覽館）
- 用嘅係**官方英文名**（政府地名 + 嶼巴站牌），**唔係機器翻譯**
- 三層查法：完全命中 → 去括號 → 部分包含
- **查唔到一律維持中文**，唔會亂砌一個似是而非嘅英文名

> 點解要內置而唔係翻譯：自行拼出嚟嘅英文名會同官方站牌對唔上，反而難搵車。

**② 地圖有離線包 —— 飛機模式都出到真實地圖**

之前靠 WebView 快取，**系統會清、唔可靠**。今版新增 `data/TileCache.kt` 自己管：

```
顯示路線地圖
   → 背景預取 zoom 12–16 嘅瓦片
   → 存落 filesDir/hketa_tiles/{z}/{x}/{y}.png
   → WebView 攞瓦片時由 shouldInterceptRequest 攔截，本地有就用本地
```

所以**睇過一次嘅路線，飛機模式都出到真實地圖**。一條巴士線約幾十到百幾枚瓦片（每枚 10–30KB），幾秒下載完。瓦片來源係 CARTO dark_all，同在線嗰個一模一樣。

**③ 輕鐵特別班次自動掃描**

官方冇特別班次（9xx）清單介面，所以只能掃描各站預報先搵到。之前要撳掣，今版**開「鐵路」分頁就自動喺背景掃**：

- 有快取且未過 **7 日** → 即刻顯示，**唔發任何請求**
- 冇快取／已過期 → 自動掃（唔彈訊息、唔阻住用）
- 手動即時重掃嘅掣照樣保留

因為係動態掃描而唔係硬編碼 9xx 清單，**官方加減班次都跟到**。

**④ 版本介面統一為膠囊**

「版本更新」區塊改為 code-to-app 嗰種 `VersionPill`：

```
版本更新

┌────────────────────────────────────┐
│ v1.0.4 · 5   ● 0        ⟳   ◷   ▤ │
└────────────────────────────────────┘
        已經係最新版本
```

- 撳膠囊 ＝ 檢查更新
- ⟳ 檢查更新／◷ 版本歷史／▤ 複製版本號（`HKETA v1.0.4 (5)`）
- 底部狀態欄、設定頁、關於頁**三處統一為同一個共用元件**

**⑤ 主頁唔會再「無聲淨係得九巴」**

背景補充城巴／小巴資料嗰陣，主頁頂部會顯示提示，解釋「點解暫時淨係見到九巴」。

### 📦 安裝

下載下面嘅 `HKETA-release.apk` 直接安裝。

**由 v1.0.0 或以上升級可以直接覆蓋**，唔使移除舊版 —— 呢啲版本共用同一個固定簽名（有效期至 2056 年）。

> 若果你裝嘅係**更早期嘅建置**（v1.0.0 之前、機器自動生成 debug key 嗰批），簽名唔同，Android 唔會畀你直接覆蓋，請先移除舊版。

```
SHA1: 04:18:68:46:D7:62:04:C6:AC:05:7B:88:B1:7B:BB:F1:61:00:1F:46
```

### 🎯 支援範圍（v1.0.3 起）

**Android 8 – 17**（`minSdk 26` / `targetSdk 37` / `compileSdk 37`）—— Android 8、9、10、11、12、12L、13、14、15、16、17 全部裝到。

### ⚠️ 仍然存在嘅限制

- **嶼巴英文名只覆蓋主要站**：109 個常用站名有官方英文對照，冷門村站可能查唔到 —— 查唔到就維持中文，唔會亂砌
- **真實地圖第一次要聯網**：預取係「睇嗰陣先下載」，所以第一次睇某條路線仍然要上網攞瓦片同 Leaflet；睇完之後就有離線包
- **輕鐵特別班次結果有時效**：快取 7 日，反映嘅係「掃描嗰刻」有冇呢個班次

### 🔒 隱私

只向官方接口發出 HTTPS 請求；定位淨係用喺當下排序附近車站，唔儲存、唔上傳。離線瓦片包淨係存喺 app 私有目錄。

---

## 🇬🇧 English

### ✨ Highlights

**① NLB stop names now have English**

The official `nlb` endpoint has **no `name_en` field** (KMB, Citybus, GMB and rail all have one — only NLB doesn't), so NLB stops stayed Chinese in the English interface.

This release bundles `data/NlbStopNames.kt` with **109** Lantau stop names:

- Covers piers (Mui Wo, Tung Chung, Tai O), beaches (Pui O, Cheung Sha, Tong Fuk), villages (Pak Kung Au, Shui Hau, Shek Pik), estates (Caribbean Coast, Century Link, Mun Tung) and attractions (Po Lin Monastery, Tian Tan Buddha, AsiaWorld-Expo)
- Uses **official English names** (government gazetteer + NLB signage) — **not machine-translated**
- Three-tier lookup: exact → strip brackets → substring match
- **Anything unmatched stays Chinese** rather than getting an invented translation

> Why bundled rather than translated: invented English names won't match the official signs, which makes finding your stop harder.

**② Offline map pack — real maps in airplane mode**

This used to rely on the WebView cache, which the system can clear at any time. Now `data/TileCache.kt` manages it directly:

```
Show a route map
   → prefetch zoom 12–16 tiles in the background
   → store in filesDir/hketa_tiles/{z}/{x}/{y}.png
   → the WebView's shouldInterceptRequest serves tiles from disk
```

So **a route you've viewed once renders a real map even in airplane mode**. A bus route is roughly a few dozen to a couple hundred tiles (10–30 KB each) and downloads in seconds. Tiles come from CARTO dark_all — identical to what you see online.

**③ Light Rail specials scan automatically**

There's no official special-trips (9xx) endpoint, so they can only be discovered by scanning stop arrivals. That used to need a button press; now **opening the Rail tab scans in the background**:

- Cache present and younger than **7 days** → shown instantly, **zero requests**
- No cache / stale → scans automatically (silent, non-blocking)
- The manual rescan button remains

Because it's a dynamic scan rather than a hardcoded 9xx list, **it tracks whatever the operator actually runs**.

**④ Version UI unified as a pill**

The version-update block is now a code-to-app style `VersionPill`:

```
Check for updates

┌────────────────────────────────────┐
│ v1.0.4 · 5   ● 0        ⟳   ◷   ▤ │
└────────────────────────────────────┘
        Already up to date
```

- Tap the pill → check for updates
- ⟳ check / ◷ version history / ▤ copy version (`HKETA v1.0.4 (5)`)
- The bottom bar, Settings and About now share **one component**, so all three look identical

**⑤ Home no longer silently shows KMB only**

While Citybus / GMB stops are being added in the background, Home shows a note explaining why you're mostly seeing KMB for now.

### 📦 Install

Download `HKETA-release.apk` below and install.

**Updating from v1.0.0 or later installs straight over the top** — those share one fixed signing key (valid to 2056).

> If you're on an **earlier build** (pre-v1.0.0, built with a machine-generated debug key), the signature differs and Android will refuse to overwrite — uninstall the old one first.

```
SHA1: 04:18:68:46:D7:62:04:C6:AC:05:7B:88:B1:7B:BB:F1:61:00:1F:46
```

### 🎯 Support range (since v1.0.3)

**Android 8 – 17** (`minSdk 26` / `targetSdk 37` / `compileSdk 37`) — installs on Android 8, 9, 10, 11, 12, 12L, 13, 14, 15, 16 and 17.

### ⚠️ Known limitations

- **NLB English covers major stops only**: 109 common Lantau stops have official English names; obscure village stops may not match — unmatched names stay Chinese rather than being invented
- **The real map needs a network on first view**: tiles are prefetched *while you view*, so seeing a route for the first time still needs network for tiles and Leaflet; after that the offline pack exists
- **Light Rail special results age**: cached for 7 days, reflecting what was running at scan time

### 🔒 Privacy

Only HTTPS requests to the official endpoints. Location is used solely to sort nearby stops at that moment — never stored or uploaded. The offline tile pack lives in the app's private storage.

---

## 🏷 Tag this release

```bash
git tag v1.0.4
git push origin v1.0.4
```

CI will build the release APK and attach it to this release automatically.
