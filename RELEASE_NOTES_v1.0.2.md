# HKETA v1.0.2

> **維護 + 體驗版本。** 主頁定位改為全自動，更新介面改版（移植自 code-to-app），並修好咗一批編譯問題。

---

## 🇭🇰 繁體中文

### ✨ 主頁自動定位

以前要撳「用定位搵附近路線」個掣先會搵站，**而家一開主頁就自動跑**：

```
┌────────────────────────────────────┐
● 用定位搵附近路線   已定位   [重整] │
└────────────────────────────────────┘
   ↑撳圓點可開關        ↑狀態
```

- **每 60 秒**自動重新定位 + 重整主頁
- 圓點顏色即時反映狀態：

| 顏色 | 意思 |
|---|---|
| 🟢 綠 | 已定位 |
| 🔵 藍 | 定位中… |
| 🟠 橙 | 未授權定位 |
| 🔴 紅 | 搵唔到定位 |
| ⚪ 灰 | 已關閉 / 待命 |

- 撳圓點或文字可以隨時開關；關咗即刻停，唔會喺背景空轉
- 右邊「重整」掣保留，想即刻更新就撳

### 🔄 更新介面改版

移植自 [code-to-app](https://github.com/benyeung08/code-to-app)，比上一版更完整：

**① 檢查更新對話框**

```
        ⟳  檢查更新
   ─────────────────────
   發現新版本 v1.0.3
   新版本：v1.0.3
   目前版本：v1.0.2
   大小約 8.3 MB
   ─────────────────────
   更新內容
   • 主頁自動定位…
   • 更新介面改版…
        [下載更新] [關閉]
```

**② 版本歷史（BottomSheet）**

```
🕘 版本歷史
├ v1.0.2  2026-10-08  ▸   ← 撳開睇內容
├ v1.0.1  2026-10-08  ▸
└ v1.0.0  2026-10-08  ▸   ← 標「已安裝」
```

- 撳一下先展開嗰個版本嘅內容（唔係全部一次攤開）
- 目前安裝嘅版本會標 **「已安裝」** 並用主色
- 每個舊版本都可以獨立下載 APK

### 🏷 兩個技術改進

**語義化版本比較**
正式版永遠排喺任何預覽版之上：`1.0.0 > 1.0.0-beta1`，而且 `beta1 < beta2 < beta10`。
（舊做法會把 `-beta1` 壓成 `1.0.0`，同正式版撞名、兩條都標成「目前版本」。）

**Release Notes 中英自動切換**
只要喺 Release notes 中間加一行分隔：

```markdown
English part…

<!-- zh-CN -->

繁體中文部分…
```

呢行喺 GitHub 網頁上渲染成空白（讀者睇到「英文 → 中文」），但 **App 會按介面語言自動揀其中一半**。

### 🐛 修正

修好咗 17 個編譯錯誤 —— 全部源於漏咗 import（`LocatePhase`、`Box`、`size`、`width`、`clip`）同一個舊版檔案。

### 📦 安裝

下載 `HKETA-release.apk` 安裝。**由 v1.0.0 或 v1.0.1 升級可以直接覆蓋**，唔使移除舊版 —— 共用同一個固定簽名。

```
SHA1: 04:18:68:46:D7:62:04:C6:AC:05:7B:88:B1:7B:BB:F1:61:00:1F:46
```

> 若你係由**更早期嘅建置**升級（v1.0.0 之前，用機器自動產生 debug key 嗰批），仍須先移除舊版。

### ⚠️ 已知限制（同前版）

- 鐵路站名冇官方英文（官方 CSV 只提供中文），線名已經係英文
- 輕鐵 9xx 特別班次唔獨立列線，但照樣出現喺預報入面
- 城巴／小巴要逐條路線抓取，深度修復建議喺 Wi-Fi 下執行
- 路線地圖係示意圖（冇底圖），要真地圖要接 Maps SDK
- 自動定位需要授權；未授權時會顯示「未授權定位」而唔係空白

---

## 🇬🇧 English

### ✨ Auto-locate on Home

You used to tap "Find nearby routes by location"; **now it runs automatically on launch**:

```
┌────────────────────────────────────┐
● Find nearby routes   Located  [Refresh] │
└────────────────────────────────────┘
   ↑tap dot to toggle      ↑status
```

- Re-locates and refreshes Home **every 60 seconds**
- The dot colour reflects live status:

| Colour | Meaning |
|---|---|
| 🟢 Green | Located |
| 🔵 Blue | Locating… |
| 🟠 Amber | Location not granted |
| 🔴 Red | Location unavailable |
| ⚪ Grey | Off / Standby |

- Tap the dot or the label to toggle; turning it off cancels the job immediately — nothing runs in the background
- The "Refresh" button stays for an instant update

### 🔄 Update UI redesigned

Ported from [code-to-app](https://github.com/benyeung08/code-to-app) — more complete than before:

**① Check-for-updates dialog**

```
        ⟳  Check for updates
   ─────────────────────
   New version v1.0.3 available
   New version: v1.0.3
   Current version: v1.0.2
   Size ≈ 8.3 MB
   ─────────────────────
   Release notes
   • Auto-locate on home…
   • Update UI redesigned…
        [Download] [Close]
```

**② Version history (bottom sheet)**

```
🕘 Version history
├ v1.0.2  2026-10-08  ▸   ← tap to expand
├ v1.0.1  2026-10-08  ▸
└ v1.0.0  2026-10-08  ▸   ← tagged "installed"
```

- Tap a release to expand its notes (not all expanded at once)
- The installed version is tagged **"installed"** in the accent colour
- Older releases can be downloaded individually

### 🏷 Two technical improvements

**Semantic version comparison**
A stable release always ranks above any pre-release: `1.0.0 > 1.0.0-beta1`, and `beta1 < beta2 < beta10`.
(The old logic collapsed `-beta1` into `1.0.0`, colliding with the stable build and tagging both as "current".)

**Bilingual release notes**
Just split the notes with a marker:

```markdown
English part…

<!-- zh-CN -->

繁體中文部分…
```

That line renders as nothing on the GitHub page (readers see English then Chinese), but **the app picks the half matching the interface language**.

### 🐛 Fixes

Fixed 17 compile errors — all from missing imports (`LocatePhase`, `Box`, `size`, `width`, `clip`) plus one stale file.

### 📦 Install

Download `HKETA-release.apk` and install. **Upgrading from v1.0.0 or v1.0.1 installs straight over the top** — same fixed signing key.

```
SHA1: 04:18:68:46:D7:62:04:C6:AC:05:7B:88:B1:7B:BB:F1:61:00:1F:46
```

> If you're upgrading from an **earlier build** (pre-v1.0.0, built with a machine-generated debug key), uninstall that first.

### ⚠️ Known limitations (unchanged)

- Rail station names have no official English (the official CSV is Chinese-only); line names are in English
- Light Rail 9xx special trips aren't listed as routes, but they do show up in arrivals
- Citybus / GMB are crawled route by route — run a deep repair on Wi-Fi
- The route map is schematic (no basemap); a real map needs Maps SDK
- Auto-locate needs the permission; without it you'll see "Location not granted" rather than a blank page

---

## 🏷 Tag this release

```bash
git tag v1.0.2
git push origin v1.0.2
```

CI will build the release APK and attach it to this release automatically.
