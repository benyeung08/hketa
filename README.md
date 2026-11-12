# 香港巴士到站預報 · HK Bus ETA

用 **GitHub + JSON + HTML + JavaScript + CSS** 重新做一個功能對齊 [hkbus.app/zh](https://hkbus.app/zh) 的網站。

**沒有框架、沒有 npm 套件、沒有 Python、沒有後端、沒有建置步驟、沒有廣告、沒有追蹤。**
整個網站就是瀏覽器直接開啟的靜態檔案；GitHub Actions 只負責每天用 Node.js 產生一份 JSON。

---

## 一、技術棧與檔案

| 技術 | 用在哪裡 |
|---|---|
| **HTML** | `index.html` — 單一頁面，所有畫面由 JS 切換 |
| **CSS** | `css/style.css` — 深淺色主題、手機暨桌面版面 |
| **JavaScript** | `js/*.js`、`scripts/build-data.js` — 前端邏輯與資料建置，全原生 |
| **JSON** | `data/routes.json`（路線／車站索引）、`data/versions.json`（版本號） |
| **GitHub** | Pages 託管 + Actions 每日自動更新 |

```
hk-bus-eta/
├── index.html              ← HTML
├── css/style.css           ← CSS
├── js/
│   ├── config.js            設定（資料來源、代理、間隔）
│   ├── api.js               ETA API（九巴／城巴／小巴／嶼巴）
│   ├── rail.js              港鐵／輕鐵／港鐵巴士／渡輪
│   ├── data.js              JSON 索引、搜尋、版本熱更新
│   ├── planner.js           點對點行程規劃
│   └── app.js               UI 與狀態
├── scripts/build-data.js   ← JavaScript（Node.js 內建模組，零依賴）
├── data/                   ← JSON（由 Actions 產生，不進 git）
│   ├── routes.json
│   └── versions.json
├── .github/workflows/pages.yml
├── manifest.webmanifest    PWA（可選，純 JSON）
├── sw.js                   PWA 殼層快取（可選，純 JS）
└── .nojekyll
```

---

## 二、三分鐘上線

1. 在 GitHub 開一個新 repository，**Public**。
2. 把本目錄所有檔案上傳（保留 `.github/`、`scripts/`、`.nojekyll`）：
   ```bash
   git init
   git add -A
   git commit -m "init HK Bus ETA"
   git branch -M main
   git remote add origin https://github.com/<帳號>/hk-bus-eta.git
   git push -u origin main
   ```
3. **Settings → Pages → Build and deployment → Source** 選 **GitHub Actions** → Save。

`pages.yml` 會在第一次 push 時自動執行 `node scripts/build-data.js` 產生 `data/*.json`，再部署整站。網址：`https://<帳號>.github.io/hk-bus-eta/`

> **不想用 Actions 也可以**：Settings → Pages 改選 `Deploy from a branch → main / root`，網站照樣能跑（只是不會每日自動重抓 JSON，前端會自動改用上游公開 JSON）。

本機預覽（不要用 `file://`，瀏覽器會擋 fetch）：

```bash
node scripts/build-data.js     # 選用：先產生 data/*.json
npx serve .                    # 或任何靜態伺服器（VS Code Live Server 也可）
# 開 http://localhost:3000
```

### ⚠️ 第一次部署紅燈：`Get Pages site failed ... Not Found`

不是程式碼問題，是 Pages 站點還沒建立。用**瀏覽器**（手機請切桌面版）開 **Settings → Pages**，Source 選 **GitHub Actions** → Save → 回 **Actions → Re-run all jobs**。

別加 `enablement: true`：它要 admin 權限，內建 `GITHUB_TOKEN` 拿不到，只會換成另一個錯。另外確認 repo 是 **Public**、**Actions → General → Workflow permissions** 選 **Read and write**。

---

## 三、功能對照

| 功能 | 有 | 說明 |
|---|---|---|
| 巴士／小巴／鐵路／渡輪分頁 | ✅ | 各自過濾搜尋與附近車站 |
| 路線號／起訖點／站名搜尋 | ✅ | 繁中＋英文，含途經站名 |
| 即時到站（多班、剩餘分鐘） | ✅ | 30 秒自動刷新、捲到才查 |
| 九巴龍運、城巴、嶼巴、小巴 | ✅ | 瀏覽器直打政府開放 API |
| 港鐵重鐵、輕鐵、港鐵巴士 | ✅ | 月台／車廂數／預定班次 |
| 渡輪（新渡輪／富裕小輪／港九小輪） | ✅ | 官方 API 有 CORS 限制，改由班次表產生「預定班次」 |
| 附近車站 + 地圖 | ✅ | 600m 內，Leaflet／OSM 按需載入 |
| 站牌預報、篩選、排序 | ✅ | 依到站時間或路線號 |
| 路線總覽 | ✅ | 方向切換、完整站序、全程／假日收費、服務時間、行車時間、特別班次 |
| 點對點行程規劃 | ✅ | 直達 + 轉乘一次，比車費／步行／站數 |
| 排序與「同站轉車」篩選 | ✅ | 車費最低／步行最短／站數最少 |
| 收藏、瀏覽紀錄 | ✅ | 本機儲存，不登入 |
| 自訂群組 | ✅ | 收納路線、車站、行程 |
| 大字站牌 | ✅ | 多站合併、全螢幕站柱（網頁版替代原生 Widget） |
| 繁中／英文、深淺色主題 | ✅ | 跟隨系統或手動切換 |
| 鍵盤快捷鍵 | ✅ | `/` `g` `n` `b` `?` `Esc` |
| PWA 可安裝 | ✅ | 可選，純 JSON + JS |
| 交通消息／改道提示 | ✅ | 可選，接 `NOTICE_URL` |
| 每日自動更新路線資料 | ✅ | Actions 每天兩次 + 前端版本熱更新 |
| 手機／手錶原生 Widget、Apple Watch、動態島 | ❌ | 網頁做不到，用大字站牌替代 |
| 社群打卡、Tauri 桌面包、SEO 預渲染 | ❌ | 非到站預報核心 |

---

## 四、JSON 是怎麼來的

分兩層，刻意拆開：

| 層 | 內容 | 來源 |
|---|---|---|
| **靜態 JSON 索引** | 路線、方向、站序、站名、座標、收費、服務時間、行車時間、假期表、站點對照 | `data/routes.json`，由 Actions 每日自動產生；上游是每日更新的公開 JSON，原始資料來自 DATA.GOV.HK |
| **即時預報** | 每一班車的預計到站時間 | 瀏覽器直接打政府／營辦商開放 API，不經任何後端 |

### 即時 ETA 端點

| 營辦商 | 端點 | 檔案 |
|---|---|---|
| 九巴／龍運 | `data.etabus.gov.hk/v1/transport/kmb/eta/...`、`/stop-eta/{stop}` | `js/api.js` |
| 城巴 | `rt.data.gov.hk/v2/transport/citybus/eta/CTB/...`、`/batch/stop-eta/...` | `js/api.js` |
| 專線小巴 | `data.etagmb.gov.hk/eta/route-stop/...`、`/eta/stop/...` | `js/api.js` |
| 嶼巴 | `rt.data.gov.hk/v2/transport/nlb/stop.php?action=estimatedArrivals` | `js/api.js` |
| 港鐵重鐵 | `rt.data.gov.hk/v1/transport/mtr/getSchedule.php?line=&sta=` | `js/rail.js` |
| 輕鐵 | `rt.data.gov.hk/v1/transport/mtr/lrt/getSchedule?station_id=` | `js/rail.js` |
| 港鐵巴士 | `POST .../mtr/bus/getSchedule` | `js/rail.js` |
| 渡輪 | 由 `freq` 班次表產生 | `js/rail.js` |

**索引掛掉也不會白畫面**：`data/routes.json` → 上游兩個公開 JSON → 全部失敗才進「政府 API 精簡模式」（頂部會標示）。

---

## 五、每日自動更新（純 JavaScript）

### 觸發時機

| 時機 | 說明 |
|---|---|
| `push` 到 `main` | 改程式碼時順便更新 |
| 每天 06:00 / 18:00 HKT | `cron`：UTC `22:00` / `10:00` |
| 手動 | Actions → **Run workflow** |

### `scripts/build-data.js` 做什麼

用 Node.js 內建的 `fetch` / `crypto` / `fs`，**不需要 `npm install`**：

1. 依序嘗試 4 個公開來源，附重試。
2. **結構校驗**：必須是 JSON、含 `routeList`／`stopList`、數量合理、抽樣檢查欄位，擋掉 403 頁面或被截斷的檔。
3. **精簡成 schema 2**：只保留前端用到的欄位，含 `freq`、`jt`、`faresHoliday`、`holidays`、`serviceDayMap`、`stopMap`。
4. **穩定序列化**（物件 key 排序）後取 **SHA-256 前 16 碼**當版本號；上游沒改，版本就不變。
5. 寫出 `data/routes.json` 與 `data/versions.json`，接著部署。

```json
{"schema":2,"status":"ok","version":"a1b2c3d4e5f60718",
 "generated_at":"2026-11-09T06:00:12+08:00","source":"...","routes":1234,"stops":5678,"bytes":2345678}
```

### 前端怎麼感知（`js/data.js`）

- 開頁先讀小小的 `versions.json` 比對本機版本；不同就清掉舊 Cache Storage 再抓新索引。
- 之後每 5 分鐘檢查一次；由背景切回前景也會即時檢查。
- 版本變了就熱換索引並重繪當前頁面，頂部短暫顯示「🔄 路線資料已自動更新」。

| 失敗情況 | 結果 |
|---|---|
| 單一來源 403／超時 | 自動換下一個 |
| 全部來源失敗 | **不中斷部署**，保留上一次成功的 `routes.json` |
| 首次部署就全失敗 | 寫 `status: unavailable`，前端降級 |
| `versions.json` 讀不到 | 視為不確定，不誤清快取 |

### 常用指令

```bash
node scripts/build-data.js                                   # 本機重抓
node scripts/build-data.js --source https://your.domain/x.json   # 換來源（可重複）
node scripts/build-data.js --fixture ./sample.json --out ./tmp  # 離線驗證
node scripts/build-data.js --strict                          # 全失敗時讓 CI 亮紅燈
node scripts/build-data.js --help
```

改更新頻率：編輯 `pages.yml` 的 `cron`（UTC）。改輪詢頻率：`js/config.js` 的 `VERSION_CHECK_MS`。

---

## 六、設定（`js/config.js`）

| 項目 | 說明 |
|---|---|
| `INDEX_URLS` | JSON 索引來源，依序嘗試。第一項是本站 `data/routes.json`。想完全換上游，改 `scripts/build-data.js` 的 `SOURCES`。 |
| `VERSIONS_URL` / `VERSION_CHECK_MS` | 版本檔與檢查間隔（5 分鐘）。 |
| `NOTICE_URL` | 可選交通消息 JSON：`[{"zh":"...","en":"...","level":"info\|warn"}]`。 |
| `PROXY` | 留空＝直連。若瀏覽器擋跨域，填轉發前綴（只套用即時 ETA）。 |
| `REFRESH_MS` | 到站預報刷新，30 秒。 |
| `INDEX_CACHE_MS` | 索引快取 6 小時（版本變更立即失效）。 |
| `PLANNER_LIMIT` / `BIG_BOARD_LIMIT` | 行程方案數、大字板車站數。 |
| `NEARBY_RADIUS_M` / `NEARBY_LIMIT` | 附近車站半徑與數量。 |

設定檔用 `document.currentScript.src` 推算根目錄，放在 GitHub Pages 專案子路徑也正確。

### 可選：跨域轉發（Cloudflare Worker）

看到「資料連線失敗」才需要。**港鐵巴士用 POST**，Worker 要把 method 與 body 一併轉發：

```js
export default {
  async fetch(request) {
    const target = new URL(request.url).searchParams.get('url');
    const allow = [
      'https://data.etabus.gov.hk/',
      'https://rt.data.gov.hk/',
      'https://data.etagmb.gov.hk/'
    ];
    if (!target || !allow.some(p => target.startsWith(p)))
      return new Response('forbidden', { status: 403 });
    const res = await fetch(target, {
      method: request.method,
      headers: {
        'Accept': 'application/json',
        ...(request.method === 'POST' ? { 'Content-Type': 'application/json' } : {})
      },
      body: request.method === 'POST' ? await request.text() : undefined
    });
    return new Response(res.body, {
      status: res.status,
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'Access-Control-Allow-Origin': '*',
        'Cache-Control': 'no-store'
      }
    });
  }
};
```

---

## 七、使用小貼士

- **底部導覽**：首頁／行程規劃／附近／群組／大字板。
- **行程規劃**：輸入「中環」「尖沙咀」等站名，列出直達與轉乘一次；可排序、勾「只顯示同站轉車」，結果可收藏或收進群組。
- **大字站牌**：常用車站加進去，按「全螢幕」當智慧站柱，適合舊手機長期插電。
- **路線頁**：可切換去／回程，看服務時間、全程與假日收費、行車時間；站序「捲到才查」預報，按 ↻ 重刷。
- **快捷鍵**：`/` 搜尋、`g` 行程規劃、`n` 附近、`b` 大字板、`?` 提示、`Esc` 返回。
- 收藏、群組、行程、語言、主題都存在瀏覽器本機，不上傳、不登入。
- 小巴與渡輪的「預定班次」是**班次表預估**，不是車輛 GPS 即時位置，介面會原樣標示。

---

## 八、已知限制

- 覆蓋：九巴／龍運、城巴、嶼巴、綠色專線小巴、港鐵重鐵、輕鐵、港鐵巴士、三家渡輪；**不含**電車、紅色小巴、山頂纜車、高鐵、部分離島渡輪。
- 渡輪／部分港鐵巴士的官方 API 有 CORS／POST preflight 限制，會降級為班次表或「暫無預報」；設 `PROXY` 可改善。
- 港鐵 Next Train 公開端點只涵蓋機場快線、東涌線、屯馬線、將軍澳線、東鐵線。
- 行程規劃只做**直達與轉乘一次**，不含步行導航、即時 ETA 加權、跨模式完整路徑；轉乘步行上限 350m。
- 嶼巴、鐵路、渡輪沒有「整站一次查」API，站牌頁逐線查詢，班次少時顯示較慢。
- 每日更新依賴上游公開索引；上游改欄位結構時校驗會擋下並保留舊索引，不會部署壞資料。
- **網頁做不到**：iOS／Android 原生 Widget、Apple Watch、動態島、Tauri 桌面包、社群打卡、SEO 預渲染。需要這些請直接用原站或 fork 其 GPL-3.0 官方倉庫。
- 預報只供參考，實際服務受交通及營運情況影響；臨時改道請以營辦商公告為準。

---

## 九、授權與鳴謝

- 本專案**程式碼**為全新撰寫，採 MIT License（見 `LICENSE`）。
- 路線／車站資料來自 `hkbus/hk-bus-crawling` 每日產生的公開 JSON（該專案 GPL-3.0），原始資料來自 DATA.GOV.HK。本專案**不複製其程式碼**，但自動更新會把該資料鏡像並精簡後放到自己的 GitHub Pages；**若要商用，請先確認資料授權與再散佈條款**，或改用 `SOURCES` 指向自行產生的 JSON。
- 即時到站資料來自運輸署「資料一站通」及各營辦商開放 API，須遵守 [DATA.GOV.HK 使用條款](https://data.gov.hk/tc/terms-and-conditions)；港鐵／輕鐵資料另須遵守 MTR Corporation 開放資料條款。
- 本站為非官方服務，與運輸署及各交通營辦商並無關聯。
- 功能範圍與無廣告理念參考 hkbus.app；如需完整原生功能，建議直接使用原站或其官方倉庫。
