/**
 * 官方資料接口 —— 對應 `data/EtaRepository.kt` + `data/Http.kt`。
 *
 * ## ⚠️ CORS（瀏覽器版最大嘅限制）
 *
 * `data.gov.hk` 系列接口**未必**返 `Access-Control-Allow-Origin`，
 * 即係瀏覽器會直接擋住。APK 版（WebView / OkHttp）冇呢個問題，
 * 但**純瀏覽器版會**。所以呢度提供三級策略：
 *
 * 1. **直連**（預設）：`proxy = ''`，適合接口本身有 CORS 嘅情況
 * 2. **自設代理**：設定頁填 `https://your-proxy/?url=`，所有請求經佢轉發
 * 3. **打包進 APK**：用 code-to-app 打包後，可以改行原生請求（見下）
 *
 * ## 打包成 APK 之後點樣 bypass
 *
 * `code-to-app` 打包出嚟嘅係 WebView。可以喺 `app.js` 入面
 * 註冊一個 `HkateFetch` 橋（`window.NativeFetch`），有就優先行原生請求。
 * 呢度已經預留咗 `pickTransport()`。
 */
(function (root) {
  'use strict';

  // ---------------------------------------------------------------- 傳輸層

  /**
   * 揀傳輸方式：原生橋 ＞ 代理 ＞ 直連。
   *
   * 打包成 APK 後只要注入 `window.NativeFetch`，所有請求就會行原生
   * （冇 CORS、可以用系統憑證、亦快啲）。
   */
  function pickTransport(proxy) {
    if (root.NativeFetch && typeof root.NativeFetch.get === 'function') return 'native';
    if (proxy && String(proxy).trim() !== '') return 'proxy';
    return 'direct';
  }

  function applyProxy(url, proxy) {
    var p = String(proxy).trim();
    if (p === '') return url;
    // 兩種寫法都支援：`…?url=` 同 `…/`
    if (p.indexOf('?') >= 0) return p + encodeURIComponent(url);
    return p.replace(/\/$/, '') + '/' + url;
  }

  /**
   * 發一個請求，返 JSON。
   *
   * 所有官方接口都係 GET ＋ JSON，所以呢度統一處理：
   * * 超時（預設 12 秒）
   * * HTTP 唔係 2xx → throw
   * * 返嚟唔係 JSON → throw
   */
  function getJSON(url, opts) {
    opts = opts || {};
    var proxy = opts.proxy || '';
    var timeout = opts.timeout || 12000;
    var mode = pickTransport(proxy);

    if (mode === 'native') {
      return root.NativeFetch.get({ url: url, timeout: timeout })
        .then(function (r) {
          if (r && r.body) return typeof r.body === 'string' ? JSON.parse(r.body) : r.body;
          throw new Error('原生請求冇返內容');
        });
    }

    var finalUrl = mode === 'proxy' ? applyProxy(url, proxy) : url;

    var ctrl = null;
    if (root.AbortController) { ctrl = new root.AbortController(); }
    var timer = setTimeout(function () { if (ctrl) ctrl.abort(); }, timeout);

    return fetch(finalUrl, {
      method: 'GET',
      headers: { 'Accept': 'application/json' },
      signal: ctrl ? ctrl.signal : undefined
    }).then(function (res) {
      clearTimeout(timer);
      if (!res.ok) throw new Error('HTTP ' + res.status + '（' + url + '）');
      return res.text();
    }).then(function (text) {
      try {
        return JSON.parse(text);
      } catch (e) {
        throw new Error('返嚟唔係 JSON（' + url + '）');
      }
    }).catch(function (e) {
      clearTimeout(timer);
      if (e && e.name === 'AbortError') throw new Error('超時（' + url + '）');
      throw e;
    });
  }

  // ---------------------------------------------------------------- 端點

  var EP = {
    kmb: {
      route: 'https://data.etabus.gov.hk/v1/transport/kmb/route/',
      stop: 'https://data.etabus.gov.hk/v1/transport/kmb/stop/',
      routeStop: function (route, bound, serviceType) {
        return 'https://data.etabus.gov.hk/v1/transport/kmb/route-stop/' +
          encodeURIComponent(route) + '/' + encodeURIComponent(bound) + '/' +
          encodeURIComponent(serviceType);
      },
      stopList: 'https://data.etabus.gov.hk/v1/transport/kmb/stop/',
      eta: function (stop, route, serviceType) {
        return 'https://data.etabus.gov.hk/v1/transport/kmb/eta/' +
          encodeURIComponent(stop) + '/' + encodeURIComponent(route) + '/' +
          encodeURIComponent(serviceType);
      }
    },
    ctb: {
      route: 'https://rt.data.gov.hk/v2/transport/citybus/route/CTB',
      stop: function (id) {
        return 'https://rt.data.gov.hk/v2/transport/citybus/stop/' + encodeURIComponent(id);
      },
      routeStop: function (route, dir) {
        return 'https://rt.data.gov.hk/v2/transport/citybus/route-stop/CTB/' +
          encodeURIComponent(route) + '/' + encodeURIComponent(dir);
      },
      eta: function (stop, route) {
        return 'https://rt.data.gov.hk/v2/transport/citybus/eta/CTB/' +
          encodeURIComponent(stop) + '/' + encodeURIComponent(route);
      }
    },
    nlb: {
      route: 'https://rt.data.gov.hk/v2/transport/nlb/route.php?action=list',
      stop: function (routeId) {
        return 'https://rt.data.gov.hk/v2/transport/nlb/stop.php?action=list&routeId=' +
          encodeURIComponent(routeId);
      },
      eta: function (routeId, stopId) {
        return 'https://rt.data.gov.hk/v2/transport/nlb/eta.php?action=list&routeId=' +
          encodeURIComponent(routeId) + '&stopId=' + encodeURIComponent(stopId);
      }
    },
    gmb: {
      /** 專線小巴要分三個地區嚟拉 */
      regions: ['HKI', 'KLN', 'NT'],
      route: function (region) {
        return 'https://data.etagmb.gov.hk/route/' + encodeURIComponent(region);
      },
      stop: function (routeId) {
        return 'https://data.etagmb.gov.hk/route-stop/' + encodeURIComponent(routeId);
      },
      eta: function (stopId) {
        return 'https://data.etagmb.gov.hk/eta/stop/' + encodeURIComponent(stopId);
      }
    },
    mtr: {
      /** 港鐵重鐵：`line` 同 `sta` 都要用官方代碼 */
      schedule: function (line, sta) {
        return 'https://rt.data.gov.hk/v1/transport/mtr/getSchedule.php?line=' +
          encodeURIComponent(line) + '&sta=' + encodeURIComponent(sta);
      }
    },
    lrt: {
      schedule: function (stopId) {
        return 'https://rt.data.gov.hk/v1/transport/mtr/lrt/getSchedule?stop_id=' +
          encodeURIComponent(stopId);
      }
    },
    /** 運輸署 GTFS 班次資料（`data/TdHeadway.kt` 用） */
    td: {
      gtfs: 'https://static.data.gov.hk/td/pt-headway/gtfs.zip'
    },
    /** 第三方聚合：`hkbus/hk-bus-crawling`（補城巴 0 個車站等） */
    hkbus: {
      all: 'https://data.hkbus.app/routeFareList.min.json',
      v2: 'https://data.hkbus.app/v2/routeFareList.min.json'
    }
  };

  // ---------------------------------------------------------------- ETA 正規化

  /**
   * 九巴 ETA → 統一格式。
   *
   * 官方 `data` 入面每筆有 `eta`（ISO 時間或 null）、`seq`、`dest_tc` 等。
   * 分鐘數由 `eta` 減現在計出嚟 —— **唔可以**直接用官方冇提供嘅分鐘欄。
   */
  function parseKmbEta(json, route) {
    var out = [];
    var data = (json && json.data) || [];
    for (var i = 0; i < data.length; i++) {
      var d = data[i];
      var minutes = minutesFromIso(d.eta);
      out.push({
        route: (d.route || route || '').trim(),
        dest: d.dest_tc || '',
        destEn: d.dest_en || '',
        minutes: minutes,
        clock: clockFromIso(d.eta),
        seq: d.seq || 0,
        remark: d.rmk_tc || '',
        op: 'KMB'
      });
    }
    return out;
  }

  /** 城巴 ETA —— 官方直接畀 `eta`（ISO）同 `dest_tc` */
  function parseCtbEta(json, route) {
    var out = [];
    var data = (json && json.data) || [];
    for (var i = 0; i < data.length; i++) {
      var d = data[i];
      var minutes = minutesFromIso(d.eta);
      out.push({
        route: (d.route || route || '').trim(),
        dest: d.dest_tc || '',
        destEn: d.dest_en || '',
        minutes: minutes,
        clock: clockFromIso(d.eta),
        seq: d.seq || 0,
        remark: d.rmk_tc || '',
        op: 'CTB'
      });
    }
    return out;
  }

  /**
   * 嶼巴 ETA —— 官方淨畀**分鐘**，冇 ISO 時間。
   * 所以 `clock` 要由「現在 ＋ 分鐘」推返出嚟（呢個係推算，唔係官方資料）。
   */
  function parseNlbEta(json, route) {
    var out = [];
    var stops = (json && json.stops) || [];
    for (var i = 0; i < stops.length; i++) {
      var s = stops[i];
      var m = parseInt(s.eta, 10);
      var minutes = isNaN(m) ? null : m;
      out.push({
        route: route || '',
        dest: '',
        destEn: '',
        minutes: minutes,
        clock: minutes == null ? '' : clockAfter(minutes),
        seq: 0,
        remark: '',
        op: 'NLB'
      });
    }
    return out;
  }

  /** 專線小巴 —— 官方 `eta` 係 ISO */
  function parseGmbEta(json, route) {
    var out = [];
    var data = (json && json.data) || [];
    for (var i = 0; i < data.length; i++) {
      var d = data[i];
      var minutes = minutesFromIso(d.eta);
      out.push({
        route: (d.routeCode || route || '').trim(),
        dest: d.dest_tc || '',
        destEn: d.dest_en || '',
        minutes: minutes,
        clock: clockFromIso(d.eta),
        seq: 0,
        remark: '',
        op: 'GMB'
      });
    }
    return out;
  }

  // ---------------------------------------------------------------- 時間小工具

  function minutesFromIso(iso) {
    if (!iso) return null;
    var t = new Date(iso).getTime();
    if (isNaN(t)) return null;
    var diff = t - Date.now();
    // 官方有時會返已經過咗嘅時間（車啱啱走咗）
    if (diff < -60000) return null;
    return Math.max(0, Math.round(diff / 60000));
  }

  function clockFromIso(iso) {
    if (!iso) return '';
    var d = new Date(iso);
    if (isNaN(d.getTime())) return '';
    return pad(d.getHours()) + ':' + pad(d.getMinutes());
  }

  function clockAfter(minutes) {
    var d = new Date(Date.now() + minutes * 60000);
    return pad(d.getHours()) + ':' + pad(d.getMinutes());
  }

  function pad(n) { return (n < 10 ? '0' : '') + n; }

  // ---------------------------------------------------------------- 對外

  var API = {
    EP: EP,
    getJSON: getJSON,
    pickTransport: pickTransport,
    applyProxy: applyProxy,
    parseKmbEta: parseKmbEta,
    parseCtbEta: parseCtbEta,
    parseNlbEta: parseNlbEta,
    parseGmbEta: parseGmbEta,
    minutesFromIso: minutesFromIso,
    clockFromIso: clockFromIso
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = API;
  root.ApiClient = API;
})(typeof globalThis !== 'undefined' ? globalThis : this);
