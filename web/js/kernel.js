/**
 * HKATE 內核 —— JavaScript 版。
 *
 * 呢度係原本 `rust/src/{kernel,speed,store}.rs` + Kotlin 回退版嘅對應實作。
 * **算法刻意寫到逐位一樣**，所以「有冇 Rust」唔會改變結果：
 *
 * | 模組 | 原本喺邊 | 呢度 |
 * |---|---|---|
 * | 取樣、班次表、診斷、ETA 合併、路線號 | `rust/src/kernel.rs` | `Kernel.*` |
 * | TTL 快取（payload） | `rust/src/speed.rs` | `Speed.*` |
 * | KV 儲存（命名空間） | `rust/src/store.rs` | `Store.*` |
 *
 * ## 點解用 JS 重寫而唔係編 WASM
 *
 * WASM 要額外 toolchain 同 `.wasm` 載入流程，瀏覽器除錯亦麻煩。
 * 而呢幾條邏輯**輸入大、輸出細、邏輯純**，JS 行得夠快，
 * 而且可以直接用 Node 跑單測（`kernel.test.js`）—— 呢個係 WASM 做唔到嘅。
 *
 * ## ⚠️ 改動規則
 *
 * 改任何一條邏輯，**要同步改 `kernel.test.js` 嘅 golden values**，
 * 否則 CI 唔會出聲，但行為已經變咗。
 */
(function (root) {
  'use strict';

  var SEP = '\u001F';   // 字段分隔（unit separator）
  var SEP2 = '\u001E';  // 字段內部嘅 pair 分隔（record separator）
  var PAIR = ';';       // 同一欄入面多個 pair
  var NO_MIN = 'N';     // 分鐘數「冇資料」嘅編碼

  // ------------------------------------------------------------------ 取樣

  /**
   * 由 `n` 個站入面取樣最多 `max` 個：頭、尾 ＋ 均勻中間。
   *
   * 同 Rust 版**逐位一樣**：`(i * step).toInt()` 係**截斷**唔係四捨五入，
   * 所以 JS 呢邊都要用 `Math.floor`（對非負數等同截斷），**唔可以**用 `Math.round`。
   *
   * Kotlin 原版：
   * ```
   * val step = (n - 1).toDouble() / (max - 1).toDouble()
   * val idx = (i * step).toInt().coerceIn(0, n - 1)
   * ```
   */
  function sampleIndices(n, max) {
    if (n <= 0 || max <= 0) return [];
    if (n <= max) {
      var all = [];
      for (var k = 0; k < n; k++) all.push(k);
      return all;
    }
    var step = (n - 1) / (max - 1);
    var out = [];
    for (var i = 0; i < max; i++) {
      var idx = Math.floor(i * step);
      // ⚠️ max=1 嗰陣 step = (n-1)/0 = Infinity，而 0 * Infinity = NaN。
      // Rust 嘅 float→int 係 saturating cast（NaN→0）、Kotlin `NaN.toInt()` 亦係 0，
      // 但 JS `Math.floor(NaN)` 係 NaN —— 所以呢度要自己清走，否則兩邊行為唔同。
      if (isNaN(idx)) idx = 0;
      if (idx < 0) idx = 0;
      if (idx > n - 1) idx = n - 1;
      if (out.indexOf(idx) < 0) out.push(idx);
    }
    return out;
  }

  // ------------------------------------------------------------------ 班次表

  /**
   * 由「每站一列 ETA」砌班次表。
   *
   * 官方 ETA 本身已按到站先後排好，所以**第 N 項就係第 N 班車** ——
   * 呢個係真實資料，唔係估算。
   *
   * 每欄格式：`"12\u001E21:10;15\u001E21:15"`（分鐘數冇資料用 `N`）。
   */
  function buildTimetable(cols, maxTrips) {
    if (maxTrips <= 0 || !cols || cols.length === 0) return null;

    // 先解碼：每欄 → [{minutes, clock}]
    var decoded = [];
    for (var c = 0; c < cols.length; c++) {
      var col = [];
      var s = String(cols[c] == null ? '' : cols[c]);
      if (s.length > 0) {
        var pairs = s.split(PAIR);
        for (var p = 0; p < pairs.length; p++) {
          var q = pairs[p].split(SEP2);
          var m = q[0] == null ? '' : q[0];
          col.push({
            minutes: (m === NO_MIN || m.trim() === '') ? null : parseIntOf(m),
            clock: q[1] == null ? '' : q[1],
            stopIndex: c
          });
        }
      }
      decoded.push(col);
    }

    // 第 N 班車 = 每欄嘅第 N 項
    var trips = [];
    for (var t = 0; t < maxTrips; t++) {
      var cells = [];
      var any = false;
      for (var c2 = 0; c2 < decoded.length; c2++) {
        var cell = decoded[c2][t];
        if (cell) {
          cells.push(cell);
          any = true;
        } else {
          cells.push({ minutes: null, clock: '', stopIndex: c2 });
        }
      }
      if (!any) break;
      trips.push({ seq: t + 1, cells: cells });
    }
    if (trips.length === 0) return null;

    return { trips: trips, headwayMin: headwayOf(trips) };
  }

  /**
   * 班次間隔：揾**第一個「第一班同第二班都有資料」嘅站**嚟計。
   *
   * ⚠️ 一定要係**同一個站**。如果分開揾「第一班第一個有資料嘅站」同
   * 「第二班第一個有資料嘅站」，兩個可能唔係同一個站 —— 咁計出嚟嘅
   * 係兩班**唔同車**嘅時間差，完全冇意義。
   *
   * 例：站0 第一班冇資料（只有第二班 27 分）、站1 有 15/30 分
   *   - 錯：站0 第二班 27 − 站1 第一班 15 = 12（兩班唔同車）
   *   - 對：站1 30 − 15 = 15
   */
  function headwayOf(trips) {
    if (trips.length < 2) return null;
    var a = trips[0].cells || [];
    var b = trips[1].cells || [];
    var n = Math.min(a.length, b.length);
    for (var i = 0; i < n; i++) {
      var x = a[i] == null ? null : a[i].minutes;
      var y = b[i] == null ? null : b[i].minutes;
      if (x != null && y != null) {
        var d = y - x;
        return d > 0 ? d : null;
      }
    }
    return null;
  }

  function firstMinutes(trip) {
    if (!trip || !trip.cells) return null;
    for (var i = 0; i < trip.cells.length; i++) {
      var m = trip.cells[i].minutes;
      if (m != null) return m;
    }
    return null;
  }

  function parseIntOf(s) {
    var v = parseInt(s, 10);
    return isNaN(v) ? null : v;
  }

  /** 把一格編碼做跨邊界格式 */
  function encodeCell(minutes, clock) {
    return (minutes == null ? NO_MIN : String(minutes)) + SEP2 + (clock == null ? '' : clock);
  }

  /** 把一個站嘅 ETA 清單編碼做一欄 */
  function encodeCol(list) {
    var parts = [];
    for (var i = 0; i < list.length; i++) {
      parts.push(encodeCell(list[i].minutes, list[i].clock));
    }
    return parts.join(PAIR);
  }

  // ------------------------------------------------------------------ 路線狀況

  /**
   * 路線狀況診斷
   *
   * ```text
   * 0 = 正常
   * 1 = 全線冇班次（可能暫停／已過尾班車）
   * 2 = 部分車站冇班次資料
   * 3 = 班次間隔偏大（>= 30 分鐘）
   * 4 = 班次極疏（>= 60 分鐘）
   * ```
   *
   * 順序係刻意嘅：全線冇嘢最嚴重，其次「有站冇資料」（對用戶最直接），
   * 最後先係間隔偏大（可能只係正常嘅疏班次路線）。
   */
  function diagnose(total, noData, maxGap) {
    if (total === 0) return 0;
    if (noData >= total) return 1;
    if (noData > 0) return 2;
    if (maxGap >= 60) return 4;
    if (maxGap >= 30) return 3;
    return 0;
  }

  // ------------------------------------------------------------------ ETA 合併

  /** 一筆 ETA 嘅跨邊界編碼 */
  function encodeEta(minutes, clock, route, dest) {
    return (minutes == null ? NO_MIN : String(minutes)) +
      SEP2 + (clock || '') + SEP2 + (route || '') + SEP2 + (dest || '');
  }

  /**
   * ETA 合併去重 ＋ 排序（分鐘數昇冪，冇資料排最後）。
   *
   * 去重鍵係「路線 ＋ 分鐘數」 —— 同一條線同一個分鐘數只會留一班。
   */
  function mergeEta(rows, cap) {
    if (cap <= 0 || !rows || rows.length === 0) return null;

    var seen = Object.create(null);
    var out = [];
    for (var i = 0; i < rows.length; i++) {
      var f = String(rows[i]).split(SEP2);
      var minutes = (f[0] === NO_MIN || f[0] === '') ? null : parseIntOf(f[0]);
      var key = (f[2] || '') + '|' + (minutes == null ? 'x' : minutes);
      if (seen[key]) continue;
      seen[key] = true;
      out.push({
        minutes: minutes,
        clock: f[1] || '',
        route: f[2] || '',
        dest: f[3] || '',
        raw: rows[i]
      });
    }

    out.sort(function (a, b) {
      if (a.minutes == null && b.minutes == null) return 0;
      if (a.minutes == null) return 1;   // 冇資料排最後
      if (b.minutes == null) return -1;
      return a.minutes - b.minutes;
    });

    if (out.length > cap) out = out.slice(0, cap);
    return out;
  }

  // ------------------------------------------------------------------ 路線號

  /** 路線號正規化：去前後空格 ＋ 轉大寫 */
  function normalizeRoute(q) {
    return String(q == null ? '' : q).trim().toUpperCase();
  }

  // ------------------------------------------------------------------ 距離

  /** 兩個經緯度之間嘅距離（米，Haversine） */
  function distanceM(lat1, lon1, lat2, lon2) {
    var R = 6371000;
    var dLat = (lat2 - lat1) * Math.PI / 180;
    var dLon = (lon2 - lon1) * Math.PI / 180;
    var a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
      Math.sin(dLon / 2) * Math.sin(dLon / 2);
    var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }

  // ------------------------------------------------------------------ 快取（speed.rs）

  /**
   * TTL 快取 —— 對應 `rust/src/speed.rs`。
   *
   * 同 Rust 版一致嘅三點（改任何一點都要同步改 Rust）：
   *
   * 1. **踢走最舊寫入**（`min_by_key(at_ms)`），**唔係 LRU**
   *    —— 呢個係最容易寫錯嘅地方：access-order 會令「有冇 .so」改變踢走邊個
   * 2. `peek` **過期照出**（stale-while-revalidate 用），而且**唔計命中**
   * 3. 容量上限：ETA 128、班次表 32、附近 16、路線車站 64
   */
  function Memo(ttlMs, max) {
    this.ttlMs = ttlMs;
    this.max = max;
    this.map = Object.create(null);
    this.hits = 0;
    this.misses = 0;
  }

  Memo.prototype.now = function () { return Date.now(); };

  Memo.prototype.put = function (key, value) {
    if (this.map[key] == null && Object.keys(this.map).length >= this.max) this.evictOldest();
    this.map[key] = { at: this.now(), value: value };
  };

  /** 踢走最舊寫入嗰條 —— 同 Rust `min_by_key(at_ms)` 一致 */
  Memo.prototype.evictOldest = function () {
    var oldest = null;
    for (var k in this.map) {
      if (oldest == null || this.map[k].at < this.map[oldest].at) oldest = k;
    }
    if (oldest != null) delete this.map[oldest];
  };

  /** 未過期先出；過期或冇就 null */
  Memo.prototype.get = function (key) {
    var e = this.map[key];
    if (e == null) { this.misses++; return null; }
    if (this.now() - e.at > this.ttlMs) { this.misses++; return null; }
    this.hits++;
    return e.value;
  };

  /**
   * 過期都照出（stale-while-revalidate）。
   * **唔計命中** —— 因為佢代表「要再抓」，計入命中會令命中率假高。
   */
  Memo.prototype.peek = function (key) {
    var e = this.map[key];
    return e == null ? null : e.value;
  };

  Memo.prototype.at = function (key) {
    var e = this.map[key];
    return e == null ? null : e.at;
  };

  Memo.prototype.remove = function (key) { delete this.map[key]; };
  Memo.prototype.clear = function () { this.map = Object.create(null); };
  Memo.prototype.size = function () { return Object.keys(this.map).length; };

  // ------------------------------------------------------------------ 儲存（store.rs）

  /**
   * KV 儲存 —— 對應 `rust/src/store.rs`。
   *
   * 行為同 Rust 版一致：
   * * 每個命名空間一個「檔」，落碟時**原子寫**（寫 `.tmp` 再 rename）
   * * 容量 4096 條，超咗**踢最舊寫入**
   * * **懶載入**：淨係用嗰個命名空間先讀，開 app 唔會一次過讀晒
   *
   * 瀏覽器冇真正嘅 rename，所以落 `localStorage` 時用「先寫 tmp key、
   * 再搬去正式 key、最後刪 tmp」模擬 —— 中途斷電頂多留低一個 tmp key，
   * 唔會損壞正式資料。
   */
  function Store(prefix) {
    this.prefix = prefix || 'hkate_store_';
    this.ns = Object.create(null);
    this.loaded = Object.create(null);
  }

  Store.prototype.CAP = 4096;

  Store.prototype.keyOf = function (ns) {
    return this.prefix + ns;
  };

  Store.prototype._load = function (ns) {
    if (this.loaded[ns]) return;
    this.loaded[ns] = true;
    var raw = null;
    try {
      raw = root.localStorage ? root.localStorage.getItem(this.keyOf(ns)) : null;
    } catch (e) {
      raw = null; // 無痕模式 / 空間唔夠 —— 退化做純記憶體
    }
    this.ns[ns] = Object.create(null);
    if (!raw) return;
    try {
      var obj = JSON.parse(raw);
      for (var k in obj) {
        if (Object.prototype.hasOwnProperty.call(obj, k)) {
          this.ns[ns][k] = { at: obj[k].at || 0, value: obj[k].value };
        }
      }
    } catch (e) {
      this.ns[ns] = Object.create(null); // 壞咗就當空，唔好損壞其餘命名空間
    }
  };

  Store.prototype.get = function (ns, key) {
    this._load(ns);
    var e = this.ns[ns][key];
    return e == null ? null : e.value;
  };

  Store.prototype.put = function (ns, key, value) {
    this._load(ns);
    var m = this.ns[ns];
    if (m[key] == null && Object.keys(m).length >= this.CAP) {
      var oldest = null;
      for (var k in m) {
        if (oldest == null || m[k].at < m[oldest].at) oldest = k;
      }
      if (oldest != null) delete m[oldest];
    }
    m[key] = { at: Date.now(), value: value };
    this._flush(ns);
  };

  Store.prototype.remove = function (ns, key) {
    this._load(ns);
    delete this.ns[ns][key];
    this._flush(ns);
  };

  Store.prototype.clear = function (ns) {
    this.ns[ns] = Object.create(null);
    this.loaded[ns] = true;
    this._flush(ns);
  };

  Store.prototype.keys = function (ns) {
    this._load(ns);
    return Object.keys(this.ns[ns]);
  };

  /** 原子寫：寫 tmp → 搬正式 key → 刪 tmp */
  Store.prototype._flush = function (ns) {
    if (!root.localStorage) return;
    var main = this.keyOf(ns);
    var tmp = main + '.tmp';
    try {
      root.localStorage.setItem(tmp, JSON.stringify(this.ns[ns]));
      root.localStorage.setItem(main, root.localStorage.getItem(tmp));
      root.localStorage.removeItem(tmp);
    } catch (e) {
      try { root.localStorage.removeItem(tmp); } catch (e2) { /* 冇得救就算 */ }
      // 空間唔夠：留喺記憶體，唔好摔
    }
  };

  /**
   * 統計。
   *
   * **淨數有條目嘅命名空間** —— 清空咗嘅命名空間仲喺 `this.ns` 入面，
   * 但設定頁顯示「3 個命名空間」其中一個係空嘅會令人誤會。
   */
  Store.prototype.stats = function () {
    var namespaces = 0, entries = 0, bytes = 0;
    for (var ns in this.ns) {
      if (!Object.prototype.hasOwnProperty.call(this.ns, ns)) continue;
      var n = Object.keys(this.ns[ns]).length;
      if (n === 0) continue;
      namespaces++;
      entries += n;
      try { bytes += JSON.stringify(this.ns[ns]).length; } catch (e) { /* 忽略 */ }
    }
    return { namespaces: namespaces, entries: entries, bytes: bytes };
  };

  // ------------------------------------------------------------------ 匯出

  var Kernel = {
    SEP: SEP, SEP2: SEP2, PAIR: PAIR, NO_MIN: NO_MIN,

    sampleIndices: sampleIndices,
    buildTimetable: buildTimetable,
    diagnose: diagnose,
    mergeEta: mergeEta,
    normalizeRoute: normalizeRoute,
    distanceM: distanceM,
    encodeCell: encodeCell,
    encodeCol: encodeCol,
    encodeEta: encodeEta,

    Memo: Memo,
    Store: Store,

    /** TTL（毫秒）—— 同 `SpeedLayer.kt` 逐個對齊 */
    TTL: {
      ETA: 20 * 1000,
      TIMETABLE: 90 * 1000,
      NEARBY: 45 * 1000,
      ROUTE_STOPS: 600 * 1000
    },
    /** 容量上限 —— 同 `speed.rs` / `SpeedLayer.kt` 對齊 */
    CAP: { ETA: 128, TIMETABLE: 32, NEARBY: 16, ROUTE_STOPS: 64 }
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = Kernel;
  root.Kernel = Kernel;
})(typeof globalThis !== 'undefined' ? globalThis : this);
