# HKATE v1.0.5

> 改名 HKATE、版本膠囊跟足原版、修好「城巴／小巴車站永遠唔會自動補」嘅 bug。
> 支援 **Android 8 – 17**。

---

## 🇭🇰 繁體中文

### ✨ 今版重點

**① 改名 HKATE**

應用程式的顯示名改為 **HKATE**：

- Launcher / 標題：`HKATE 巴士到站預報`
- 英文介面：`HKATE — HK Transport ETA`
- 關於頁、複製版本號（`HKATE v1.0.5 (6)`）都跟埋改
- APK 輸出名亦改為 `HKATE-release.apk`

> 內部 package（`com.hketa.app`）**刻意保持不變** —— 改咗就變咗第二個 app，你又要解除安裝重裝、收藏會冇咗。

**② 版本膠囊跟足 code-to-app 原版**

移除咗圓點徽章，版面變成同原版 `VersionPill` 一模一樣：

```
v1.0.5 · 6        ⟳   ◷   ▤
```

- 撳膠囊 ＝ 檢查更新
- ⟳ 檢查更新／◷ 版本歷史／▤ 複製版本號

有新版本嗰陣，**`versionCode` 嗰個數字會轉紅** —— 所以唔靠徽章都睇得出，版面又乾淨。

底部狀態欄、設定頁、關於頁三處繼續共用同一個元件。

**③ 修好一個重要 bug：城巴／小巴車站其實從來冇自動補過**

呢個係今版最重要嘅修復。以前嘅邏輯：

```kotlin
suspend fun ensureIndex(force: Boolean = false) {
    val cached = if (force) null else store.load()
    if (cached != null && ...) {
        _index.value = cached
        return          // ← 快取命中直接 return，永遠唔會補
    }
    buildIndex()        // ← 得呢條路先會叫 maybeAutoRepair()
}
```

即係：

- **第一次裝 App**（要重建索引）→ 會補
- **由第二次開始**（索引由磁碟載入）→ 直接 return → `maybeAutoRepair()` 從來冇跑到

後果有兩個：

1. 城巴／小巴車站**永遠係 0**，附近車站淨係得九巴
2. 主頁嗰句「背景補充緊城巴／小巴資料」**永久掛住唔消失**（因為「補完」嘅狀態永遠唔會設）

而家兩樣都修好咗：

| 項目 | 修法 |
|---|---|
| 快取路徑唔補 | `ensureIndex()` 快取命中都叫 `maybeAutoRepair()` |
| 提示唔消失 | 新增 `autoRepairing` 狀態，淨係「而家補緊」先顯示；成功定失敗都會喺 `finally` 收尾 |

**④ 背景修復改為靜默**

唔彈訊息、唔掝忙碌狀態 —— 唔會再蓋住你自己開嘅動作（例如你手動撳「修復城巴」，唔會被背景嗰個影響）。

**⑤ 簡體中文補齊 76 個字串**

`values-b+zh+Hans/strings.xml` 之前缺咗 76 個 key。四份語言檔（`values` / `values-zh-rCN` / `values-en` / `values-b+zh+Hans`）而家**完全一致**。

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

- **嶼巴英文名只覆蓋主要站**：109 個常用站名有官方英文對照，冷門村站可能查唔到 —— 查唔到就維持中文，唔會亂砌
- **真實地圖第一次要聯網**：預取係「睇嗰陣先下載」，睇完之後就有離線包
- **輕鐵特別班次結果有時效**：快取 7 日，反映嘅係「掃描嗰刻」有冇呢個班次
- **港鐵巴士路線靠內置清單**：官方冇全量路線接口，所以用已知清單（K12–K76）逐條查；清單以外嘅路線唔會出現

### 🔒 隱私

只向官方接口發出 HTTPS 請求；定位淨係用喺當下排序附近車站，唔儲存、唔上傳。離線瓦片包淨係存喺 app 私有目錄。

---

## 🇬🇧 English

### ✨ Highlights

**① Renamed to HKATE**

The app's display name is now **HKATE**:

- Launcher / title: `HKATE 巴士到站預報`
- English UI: `HKATE — HK Transport ETA`
- About page and copy-version text (`HKATE v1.0.5 (6)`) follow suit
- APK output renamed to `HKATE-release.apk`

> The internal package (`com.hketa.app`) is **deliberately unchanged** — changing it would make this a different app, forcing an uninstall that wipes your bookmarks.

**② Version pill now matches code-to-app exactly**

The dot badge is gone, so it reads exactly like the original `VersionPill`:

```
v1.0.5 · 6        ⟳   ◷   ▤
```

- Tap the pill → check for updates
- ⟳ check / ◷ version history / ▤ copy version

When an update exists, **the version number turns red** — you still see it without needing a badge, and the layout stays clean.

The bottom bar, Settings and About still share one component.

**③ Fixed a significant bug: Citybus / GMB stops were never actually added**

This is the most important fix in this release. The old logic:

```kotlin
suspend fun ensureIndex(force: Boolean = false) {
    val cached = if (force) null else store.load()
    if (cached != null && ...) {
        _index.value = cached
        return          // ← cache hit returned early, never repaired
    }
    buildIndex()        // ← only this path called maybeAutoRepair()
}
```

Meaning:

- **First install** (index gets rebuilt) → repair ran
- **Every later launch** (index loaded from disk) → returned early → `maybeAutoRepair()` never ran

Two consequences:

1. Citybus / GMB stops were **always 0**, so nearby stops were KMB-only
2. The "adding Citybus / GMB stops in the background" note **never went away** (the "done" state was never set)

Both are fixed:

| Issue | Fix |
|---|---|
| Cache path never repaired | `ensureIndex()` now calls `maybeAutoRepair()` on a cache hit too |
| Note never cleared | New `autoRepairing` state: the note shows only while repairing, and `finally` clears it on success **or** failure |

**④ Background repair is now silent**

No toast, no busy flag — it won't interrupt an action you started yourself (e.g. if you tap "Repair Citybus", the background job won't stomp on it).

**⑤ Simplified Chinese: 76 strings filled in**

`values-b+zh+Hans/strings.xml` was missing 76 keys. All four language files (`values` / `values-zh-rCN` / `values-en` / `values-b+zh+Hans`) now **match exactly**.

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

- **NLB English covers major stops only**: 109 common Lantau stops have official English names; obscure village stops may not match — unmatched names stay Chinese rather than being invented
- **The real map needs a network on first view**: tiles are prefetched *while you view*; after that the offline pack exists
- **Light Rail special results age**: cached for 7 days, reflecting what was running at scan time
- **MTR Bus routes come from a bundled list**: there's no bulk-route API, so a known list (K12–K76) is queried route by route; routes outside that list won't appear

### 🔒 Privacy

Only HTTPS requests to the official endpoints. Location is used solely to sort nearby stops at that moment — never stored or uploaded. The offline tile pack lives in the app's private storage.

---

## 🏷 Tag this release

```bash
git tag v1.0.5
git push origin v1.0.5
```

CI will build the release APK and attach it to this release automatically.
