# HKETA v1.0.1

> **維護版本。** 修正到站預報頁嘅站號顯示問題，並把預報頁改版成卡片式版面。

---

## 🇭🇰 繁體中文

### 🐛 修正

**站號位顯示咗 routeId（主要修正）**
之前到站預報頁會出現類似咁嘅畫面：

```
海麗邨巴士總站 (SS667) (296E43213D6DB624)
九巴 296E43213D6DB624 每20秒自動更新
```

`296E43213D6DB624` 係九巴嘅**路線 ID**，唔係站號 —— 站號係 `SS667`。呢個唔單止令畫面出現「一串數字」，更會因為**攞 routeId 去查班次**而查唔到嘢，顯示「暫時冇班次資料」。

而家：
- 站號長度超過 12 位一律當唔係站號，**唔顯示**（九巴站號最長約 8 位）
- 查詢層加咗保護：站號異常時自動改用索引入面同名同營辦商嘅站，班次照查得到
- 主頁 / 收藏 / 車站搜尋統一用新嘅顯示邏輯，唔會重複括號

**版本歷史對話框**
修好喺簡體中文同英文介面下嘅顯示問題；本地 changelog 而家會跟住介面語言顯示對應語系。

**Compose 編譯問題**
修好 `titleWithCode` 缺 `@Composable` 標註導致嘅問題。

### ✨ 改版：到站預報頁

改成同「沿途車站」列表一致嘅**卡片式版面**：

```
┌──────────────────────────────┐
│ 海麗邨巴士總站            ♡  │   站名（大字）
│                              │
│ SS667                        │   站號（主色，獨立一行）
│ 九巴                         │   營辦商
│ 每20秒自動更新                │   提示
└──────────────────────────────┘

[ 37 ]  往 葵盛(中)                 路線膠囊 + 目的地
```

- 站名、站號、營辦商**各佔一行**，唔再擠埋一齊
- 路線號做成**膠囊標籤**（主色底 + 圓角）
- 收藏掣縮細放喺卡片右上角，唔搶焦點

### 📦 安裝

下載 `HKETA-release.apk` 安裝。**由 v1.0.0 升級可以直接覆蓋**，唔使移除舊版 —— 兩個版本用同一個固定簽名。

```
SHA1: 04:18:68:46:D7:62:04:C6:AC:05:7B:88:B1:7B:BB:F1:61:00:1F:46
```

> 若你係由**更早期嘅建置**升級（v1.0.0 之前，用機器自動產生 debug key 嗰批），仍須先移除舊版。

### ⚠️ 已知限制（同 v1.0.0）

- 鐵路站名冇官方英文（官方 CSV 只提供中文），線名已經係英文
- 輕鐵 9xx 特別班次唔獨立列線，但照樣出現喺預報入面
- 城巴／小巴要逐條路線抓取，深度修復建議喺 Wi-Fi 下執行
- 路線地圖係示意圖（冇底圖），要真地圖要接 Maps SDK

---

## 🇬🇧 English

### 🐛 Fixes

**The stop-code slot was showing a routeId (main fix)**
The arrivals page used to display something like:

```
Hoi Lai Estate Bus Terminus (SS667) (296E43213D6DB624)
KMB 296E43213D6DB624 · auto-refresh every 20s
```

`296E43213D6DB624` is a KMB **route ID**, not a stop code — the code is `SS667`. Besides the ugly "string of digits", this also meant **querying arrivals with a routeId**, which always fails and shows "no departures".

Now:
- Stop codes longer than 12 characters are treated as invalid and **hidden** (KMB codes are ~8 chars max)
- Query-layer guard: when the code looks wrong, fall back to the same-named stop in the index so arrivals still resolve
- Home / Favourites / stop search share one formatter — no repeated parentheses

**Version history dialog**
Fixed its display under Simplified Chinese and English; the local changelog now follows the interface language.

**Compose issue**
Fixed `titleWithCode` missing the `@Composable` annotation.

### ✨ Redesign: arrivals page

Now uses the same **card layout** as the route-stop list:

```
┌──────────────────────────────┐
│ Hoi Lai Estate Bus Terminus ♡│   stop name (large)
│                              │
│ SS667                        │   stop code (accent, own line)
│ KMB                          │   operator
│ Auto-refresh every 20s       │   hint
└──────────────────────────────┘

[ 37 ]  to Kwai Chung (Central)     route pill + destination
```

- Stop name, code and operator each get **their own line**
- Route shown as a **pill badge** (accent background, rounded)
- Bookmark button shrunk into the card's top-right corner

### 📦 Install

Download `HKETA-release.apk` and install. **Upgrading from v1.0.0 installs straight over the top** — both builds share the same fixed signing key.

```
SHA1: 04:18:68:46:D7:62:04:C6:AC:05:7B:88:B1:7B:BB:F1:61:00:1F:46
```

> If you're upgrading from an **earlier build** (pre-v1.0.0, built with a machine-generated debug key), uninstall that first.

### ⚠️ Known limitations (same as v1.0.0)

- Rail station names have no official English (the official CSV is Chinese-only); line names are in English
- Light Rail 9xx special trips aren't listed as routes, but they do show up in arrivals
- Citybus / GMB are crawled route by route — run a deep repair on Wi-Fi
- The route map is schematic (no basemap); a real map needs Maps SDK

---

## 🏷 Tag this release

```bash
git tag v1.0.1
git push origin v1.0.1
```

CI will build the release APK and attach it to this release automatically.
