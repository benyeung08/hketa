# HKATE v1.1.2

> 效能大優化：開 App 唔再卡，附近車站計算快約 58 倍。
> 支援 **Android 8 – 17**。

呢版**淨係做效能** —— 冇加新功能、冇改介面。兩個改動都係同一類問題：**主線程做咗重活**。

---

## 🇭🇰 繁體中文

### ✨ 今版重點

**① 索引讀寫移出主線程**

索引 JSON 實測約 **7 MB**（2099 條路線 / 7250 個車站 / 63150 條路線-車站對應）：

| 動作 | 實測（伺服 CPU） | 低階手機估計（×5） |
|---|---|---|
| load（讀 + 解析） | 187 ms | ~0.9 秒 |
| save（序列化 + 寫） | 342 ms | ~1.7 秒 |
| **合計** | **529 ms** | **~2.6 秒** |

以前全部喺主線程做（`viewModelScope` 預設 `Dispatchers.Main`），即係**開 App 會硬生生卡住呢段時間**，低端機甚至接近 ANR。

而家：

| 改動 | 說明 |
|---|---|
| **IO 線程** | `withContext(Dispatchers.IO)`，主線程完全唔阻塞 |
| **流式讀寫** | `decodeFromStream` / `encodeToStream`，唔使先砌一個 7 MB 嘅 String |
| **原子替換** | 寫落 `.tmp` 再 rename —— 寫到一半崩潰都唔會損壞原本嘅索引 |
| **單線程序列化** | 避免兩個協程同時寫同一個檔互相覆蓋 |

**② 附近車站：O(n×m) → O(n+m)**

以前每揾一個站都要掃晒全部 `routeStops`：

```kotlin
val routes = idx.routeStops          // 63150 條
    .filter { it.op == st.op && it.stopId == st.id }
```

近處 50 個站 = **50 × 63150 ≈ 315 萬次遍歷**，而且喺主線程 —— 開 App 同每次自動定位重整（每 60 秒）都跑一次。

而家：

| 改動 | 說明 |
|---|---|
| **建成一次索引表** | `stopId → 路線`，之後每站 O(1) 查表 |
| **bounding box 預篩** | 先用加減法嘅經緯度方框濾走遠嘅站，淨係對候選做 Haversine |
| **移到 IO 線程** | 主線程唔再計呢啲嘢 |

實測（7250 站 / 63150 條對應 / 3 km 半徑）：

| 指標 | 之前 | 而家 |
|---|---|---|
| 路線掃描次數 | 4,546,800 | **63,150** |
| 三角函數次數 | 7,250 | **94** |
| 純計算耗時（首次） | 352 ms | 189 ms |
| **純計算耗時（快取命中）** | 352 ms | **6 ms** |

即係**快約 58 倍**。因為索引表以 `IndexData` 嘅物件身份做快取 key，索引一日唔換，重整就一定命中快取 —— 所以係**每次 60 秒重整都慳返約 346 ms**。

### 📦 安裝

下載下面嘅 `HKATE-release.apk` 直接安裝。

**由 v1.0.0 或以上升級可以直接覆蓋**，唔使移除舊版 —— 呢啲版本共用同一個固定簽名（有效期至 2056 年）。

> 若果你裝嘅係**更早期嘅建置**（v1.0.0 之前、機器自動生成 debug key 嗰批），簽名唔同，Android 唔會畀你直接覆蓋，請先移除舊版。

```
SHA1: 04:18:68:46:D7:62:04:C6:AC:05:7B:88:B1:7B:BB:F1:61:00:1F:46
```

**首次用 App 內下載要授權一次**：Android 8+ 會先跳去「允許安裝未知來源」設定頁，授權完返嚟再撳一次「下載」就得。呢個係系統限制，唔係 bug。

### 🎯 支援範圍（v1.0.3 起）

**Android 8 – 17**（`minSdk 26` / `targetSdk 37` / `compileSdk 37`）—— Android 8、9、10、11、12、12L、13、14、15、16、17 全部裝到。

### ⚠️ 已知限制

呢版冇改動任何功能，所以限制同 v1.1.1 一樣：

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

**① Index I/O moved off the main thread**

The index JSON measures about **7 MB** (2,099 routes / 7,250 stops / 63,150 route-stop rows):

| Operation | Measured (server CPU) | Est. low-end phone (x5) |
|---|---|---|
| load (read + parse) | 187 ms | ~0.9 s |
| save (serialise + write) | 342 ms | ~1.7 s |
| **Total** | **529 ms** | **~2.6 s** |

All of that used to happen on the main thread (`viewModelScope` defaults to `Dispatchers.Main`), so **app start stalled for exactly that long** — close to ANR territory on a low-end device.

Now:

| Change | What it does |
|---|---|
| **IO thread** | `withContext(Dispatchers.IO)` — the main thread never blocks |
| **Streaming codecs** | `decodeFromStream` / `encodeToStream` avoid materialising a 7 MB String |
| **Atomic replace** | write `.tmp`, then rename — a crash mid-write can no longer corrupt the index |
| **Single-thread serialisation** | stops two coroutines writing the same file at once |

**② Nearby stops: O(n×m) → O(n+m)**

Every stop used to rescan all of `routeStops`:

```kotlin
val routes = idx.routeStops          // 63,150 rows
    .filter { it.op == st.op && it.stopId == st.id }
```

50 nearby stops = **50 × 63,150 ≈ 3.15 M iterations**, on the main thread, on every launch and on every auto-locate refresh (every 60 s).

Now:

| Change | What it does |
|---|---|
| **Lookup built once** | `stopId → routes`, then each stop resolves in O(1) |
| **Bounding-box pre-filter** | a lat/lon box of plain additions/subtractions discards distant stops first; Haversine runs only on survivors |
| **Moved to IO thread** | the main thread no longer does this math |

Measured (7,250 stops / 63,150 rows / 3 km radius):

| Metric | Before | Now |
|---|---|---|
| Route rows scanned | 4,546,800 | **63,150** |
| Trig calls | 7,250 | **94** |
| Compute time (first run) | 352 ms | 189 ms |
| **Compute time (cache warm)** | 352 ms | **6 ms** |

That's roughly **58x faster**. The table is cached against the `IndexData` object identity, so as long as the index doesn't change every refresh hits the cache — saving **about 346 ms on every 60-second refresh**.

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

No functional changes, so the limitations are the same as v1.1.1:

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
git tag v1.1.2
git push origin v1.1.2
```

CI will build the release APK and attach it to this release automatically.
