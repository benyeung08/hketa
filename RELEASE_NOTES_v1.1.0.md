# HKATE v1.1.0

> 路線頁新增「班次時間表」同「公告」兩個分頁，搜尋頁版面改版。
> 支援 **Android 8 – 17**。

---

## 🇭🇰 繁體中文

### ✨ 今版重點

**① 路線頁新增「班次時間表」**

**要先講清楚一件事**：香港各營辦商（九巴／城巴／嶼巴／專線小巴／港鐵／輕鐵）**都冇公開嘅班次時刻表 API** —— 官方淨係提供即時到站預報（ETA）。所以我冇辦法顯示官方紙本時刻表（例如「平日 06:00–23:30，每 15 分鐘一班」），硬做就要自己抓網頁或者編數據，兩樣我都唔會做。

**所以我改為用真實資料構建**：官方 ETA 本身已按到站先後排好，**第 N 項就係第 N 班車**。所以並發抓沿線各站嘅 ETA，把每個站嘅第 N 項抽埋一齊，就係第 N 班車行經各站嘅時間：

```
            尖沙咀東  尖東站  九龍公園徑  中港城  柯士甸站
第1班        12:05   12:08    12:11    12:14   12:17
第2班        12:20   12:23    12:26    12:29   12:32
第3班        12:35   12:38    12:41    12:44   12:47

班次間隔：約 15 分鐘
```

呢個係**真實資料，唔係估算**。班次間隔由第一個站相鄰兩班嘅分鐘差推算（只接受 1–180 分鐘，濾掉異常值）。

**② 班次表嘅設計細節**

- **最多顯示 6 班** —— 再多冇意義
- **沿線超過 12 個站會自動均勻取樣**（頭、尾、中間）—— 唔會打爆官方接口
- **懶加載** —— 切去「班次」分頁先抓，唔會開頁就打一大輪請求
- 5 分鐘內嘅時間用主色高亮，同車站頁一致
- 站多過螢幕闊就橫向捲

**③ 「觀察到嘅營運時段」—— 唔係官方頭／尾班車**

官方冇得查頭班車／尾班車，所以用**觀察累積**：每次開 App 見到嘅班次時刻會記低，慢慢累積出營運時段。

```
第1次 見到 12:05–12:35
第2次 見到 07:30–07:45  →  07:30–12:35
第3次 見到 23:10–23:25  →  07:30–23:25
第4次 見到 06:40        →  06:40–23:25
```

**開得耐就愈準**。介面會標明係「**觀察到嘅營運時段**」，唔會冒充官方公佈嘅頭／尾班車 —— 呢點好重要，因為信錯尾班車會冇車搭。

**④ 路線頁新增「公告」分頁**

兩個來源：

| 來源 | 說明 |
|---|---|
| **港鐵官方** | `getSchedule` 會一併返回 service alert（改道／延誤／暫停）—— **真係官方公告** |
| **App 公告** | GitHub releases 最近 5 條，可撳「查看詳情」開連結 |

其他營辦商嘅 ETA 接口冇公告欄位，所以只有港鐵系（重鐵／輕鐵）會有官方公告。

**⑤ 搜尋頁版面改版**

```
┌────────────────────────────┐
│ 搜尋                        │  ← 主色標題列（新增）
└────────────────────────────┘
  [路線] [車站]
  ┌────────────────────────┐
  │ 輸入路線號碼            │  ← 白底輸入框
  └────────────────────────┘
  [鐵路] [附近]

  路線
  ┌────────────────────────┐
  │ 1   往 尖沙咀碼頭      > │
  │ 1   往 竹園邨          > │
  │ 1   往 大澳 - 嶼巴     > │
  │     大嶼山巴士 (資料由  │
  │     DATA.GOV.HK提供)    │
  └────────────────────────┘

  ▄▄▄▄▄ 藍灰色鍵盤 ▄▄▄▄▄
```

- 頂部加咗**主色「搜尋」標題列**
- 輸入框改為**白底「輸入路線號碼」**
- 結果由卡片改為**一行式：路線號 ｜ 往 XX ｜ 箭頭**
- 非九巴路線會顯示「**目的地 - 營辦商**」（例如「大澳 - 嶼巴」），下一行標示官方全名同「**資料由 DATA.GOV.HK 提供**」
- 路線專用鍵盤改為**藍灰色調**，數字鍵、字母圓鍵對比更清楚

**一個刻意嘅決定**：箭頭用咗自繪 drawable 而唔係 `material-icons-extended`，後者會**大 35MB**。

### 📦 安裝

下載下面嘅 `HKATE-release.apk` 直接安裝。

**由 v1.0.0 或以上升級可以直接覆蓋**，唔使移除舊版 —— 呢啲版本共用同一個固定簽名（有效期至 2056 年）。

> 若果你裝嘅係**更早期嘅建置**（v1.0.0 之前、機器自動生成 debug key 嗰批），簽名唔同，Android 唔會畀你直接覆蓋，請先移除舊版。

```
SHA1: 04:18:68:46:D7:62:04:C6:AC:05:7B:88:B1:7B:BB:F1:61:00:1F:46
```

### 🎯 支援範圍（v1.0.3 起）

**Android 8 – 17**（`minSdk 26` / `targetSdk 37` / `compileSdk 37`）—— Android 8、9、10、11、12、12L、13、14、15、16、17 全部裝到。

### ⚠️ 仍然存在嘅限制

- **冇官方紙本時刻表**：班次表係由即時預報構建，反映嘅係「而家」嘅班次，唔係全日時刻表
- **營運時段係觀察值**：要靠日常開 App 慢慢累積，初裝嗰陣可能顯示「仲未睇到班次」
- **官方淨係港鐵有**：其他營辦商嘅 ETA 接口冇服務公告欄位
- **班次表要聯網**：每次切去「班次」分頁都會抓一次
- **嶼巴英文名只覆蓋主要站**：109 個常用站名有官方英文對照，冷門村站維持中文
- **真實地圖第一次要聯網**：睇完之後就有離線包
- **輕鐵特別班次快取 7 日**：反映嘅係「掃描嗰刻」有冇呢個班次
- **港鐵巴士路線靠內置清單**：官方冇全量路線接口，清單以外嘅路線唔會出現

### 🔒 隱私

只向官方接口發出 HTTPS 請求；定位淨係用喺當下排序附近車站，唔儲存、唔上傳。離線瓦片包同觀察累積資料淨係存喺 app 私有目錄。

---

## 🇬🇧 English

### ✨ Highlights

**① Route page gains a trip timetable**

**One thing up front**: none of the Hong Kong operators (KMB, Citybus, NLB, GMB, MTR, Light Rail) publish a **timetable API** — they only expose live arrivals. So there is no way to show an official paper timetable (e.g. "Mon–Fri 06:00–23:30, every 15 min"); faking it would mean scraping websites or inventing data, and this app does neither.

**So the timetable is built from real data instead**: the official arrivals are already sorted by arrival order, so **entry N is trip N**. Fetch arrivals for each stop along the route concurrently and take the Nth entry at every stop — that's trip N's schedule:

```
            TST East  TST Stn  Park Ave  China HK  Austin
Trip 1       12:05    12:08     12:11    12:14    12:17
Trip 2       12:20    12:23     12:26    12:29    12:32
Trip 3       12:35    12:38     12:41    12:44    12:47

Frequency: about every 15 min
```

That's **real data, not an estimate**. The headway comes from the gap between consecutive trips at the first stop (only 1–180 min accepted, so outliers are filtered out).

**② Timetable design details**

- **Up to 6 trips** — more isn't useful
- **Routes with more than 12 stops are sampled evenly** (start, end, middle) so the official endpoints aren't hammered
- **Lazy loading** — nothing is fetched until you open the Trips tab
- Times within 5 minutes are highlighted in the primary colour, matching the stop page
- Scrolls horizontally if there are more stops than fit

**③ "Observed service window" — not an official first/last bus**

There's no API for first/last bus times, so the window is **accumulated from observations**: every time you use the app, the trip times seen are recorded and the window widens.

```
1st visit  12:05–12:35
2nd visit  07:30–07:45  →  07:30–12:35
3rd visit  23:10–23:25  →  07:30–23:25
4th visit  06:40        →  06:40–23:25
```

**It gets more accurate the longer you use it.** The UI labels it as the "**observed service window**" and never presents it as the official first/last bus — that matters, because trusting a wrong last-bus time means being stranded.

**④ Route page gains a Notices tab**

Two sources:

| Source | Notes |
|---|---|
| **Official MTR** | `getSchedule` returns service alerts (diversion / delay / suspension) — **genuinely official** |
| **App announcements** | The 5 most recent GitHub releases, each with a Details link |

The other operators' arrival APIs have no notice field, so only the MTR family (heavy rail / Light Rail) can show official notices.

**⑤ Search page redesigned**

```
┌────────────────────────────┐
│ Search                      │  ← primary title bar (new)
└────────────────────────────┘
  [Routes] [Stops]
  ┌────────────────────────┐
  │ Enter a route number    │  ← white field
  └────────────────────────┘
  [Rail]  [Nearby]

  Routes
  ┌────────────────────────┐
  │ 1   to Tsim Sha Tsui  > │
  │ 1   to Chuk Yuen      > │
  │ 1   to Tai O - NLB    > │
  │     New Lantao Bus      │
  │     (data from DATA.GOV.HK) │
  └────────────────────────┘

  ▄▄▄▄▄ blue-grey keypad ▄▄▄▄▄
```

- A **primary-coloured Search title bar** at the top
- The field is now **white, reading "Enter a route number"**
- Results changed from cards to **one row each: number, destination, chevron**
- Non-KMB routes show "**destination - operator**" (e.g. "Tai O - NLB") with the official name and the **DATA.GOV.HK credit** underneath
- The route keypad is now **blue-grey** with clearer contrast on the digit and round letter keys

**One deliberate choice**: the chevron is a hand-drawn drawable rather than from `material-icons-extended`, which would add **35 MB**.

### 📦 Install

Download `HKATE-release.apk` below and install.

**Updating from v1.0.0 or later installs straight over the top** — those share one fixed signing key (valid to 2056).

> If you're on an **earlier build** (pre-v1.0.0, built with a machine-generated debug key), the signature differs and Android will refuse to overwrite — uninstall the old one first.

```
SHA1: 04:18:68:46:D7:62:04:C6:AC:05:7B:88:B1:7B:BB:F1:61:00:1F:46
```

### 🎯 Support range (since v1.0.3)

**Android 8 – 17** (`minSdk 26` / `targetSdk 37` / `compileSdk 37`) — installs on Android 8, 9, 10, 11, 12, 12L, 13, 14, 15, 16 and 17.

### ⚠️ Known limitations

- **No official paper timetable**: the trip table is built from live arrivals, so it reflects trips running *now*, not a full-day schedule
- **The service window is observed**: it accumulates as you use the app, and a fresh install may show "no trips seen yet"
- **Official notices are MTR-only**: the other operators' arrival APIs have no notice field
- **The timetable needs a network**: it re-fetches each time you open the Trips tab
- **NLB English covers major stops only**: 109 common Lantau stops have official English names; obscure village stops stay Chinese
- **The real map needs a network on first view**: after that the offline pack exists
- **Light Rail specials are cached for 7 days**: reflecting what was running at scan time
- **MTR Bus routes come from a bundled list**: there's no bulk-route API, so routes outside that list won't appear

### 🔒 Privacy

Only HTTPS requests to the official endpoints. Location is used solely to sort nearby stops at that moment — never stored or uploaded. The offline tile pack and observation data live in the app's private storage.

---

## 🏷 Tag this release

```bash
git tag v1.1.0
git push origin v1.1.0
```

CI will build the release APK and attach it to this release automatically.
