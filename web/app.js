/* HKETA Hybrid — UI 與 ETA 邏輯（純 JavaScript，無框架） */
(function () {
  'use strict';

  // ---- 官方公開接口（與 hketa 原專案相同來源）----
  var KMB = 'https://data.etabus.gov.hk/v1/transport/kmb/';
  var CTB = 'https://rt.data.gov.hk/v2/transport/citybus/';
  var NLB = 'https://rt.data.gov.hk/v2/transport/nlb/';
  var GMB = 'https://data.etagmb.gov.hk/';
  var MTR = 'https://rt.data.gov.hk/v1/transport/mtr/';

  // ---- 三語 ----
  var I18N = {
    'zh-Hant': {
      home: '主頁', fav: '收藏', search: '搜尋', settings: '設定',
      nearby: '附近車站', building: '建立索引中…', ready: '就緒',
      noFav: '仲未收藏車站，去預報頁撳心形啦', noResult: '搵唔到路線',
      hint: '輸入路線號，例如 1A、6、K51', refresh: '重新整理',
      min: '分鐘', noEta: '暫無班次', noGps: '未授權定位，可去設定手動選點',
      lang: '介面語言', rebuild: '重建索引', update: '檢查更新',
      repo: '更新用 GitHub repo（owner/name）', source: '資料來源',
      manual: '手動選點', saved: '已收藏', removed: '已移除收藏',
      routes: '路線', stops: '沿途車站', eta: '到站預報',
      ver: '版本', official: '官方公開接口，免授權',
      latest: '已經係最新版', newVer: '有新版本', noRelease: '尚未發布正式版本',
      openRelease: '查看 Release', autoRefresh: '每 20 秒自動更新',
      browser: '瀏覽器版受 CORS 限制，請用 APK 或自設代理',
      loadErr: '載入失敗', back: '返回'
    },
    'zh-Hans': {
      home: '主页', fav: '收藏', search: '搜索', settings: '设置',
      nearby: '附近车站', building: '建立索引中…', ready: '就绪',
      noFav: '还没有收藏车站，去预报页点心形吧', noResult: '找不到路线',
      hint: '输入路线号，例如 1A、6、K51', refresh: '刷新',
      min: '分钟', noEta: '暂无班次', noGps: '未授权定位，可去设置手动选点',
      lang: '界面语言', rebuild: '重建索引', update: '检查更新',
      repo: '更新用 GitHub repo（owner/name）', source: '数据来源',
      manual: '手动选点', saved: '已收藏', removed: '已移除收藏',
      routes: '路线', stops: '沿途车站', eta: '到站预报',
      ver: '版本', official: '官方公开接口，免授权',
      latest: '已经是最新版', newVer: '有新版本', noRelease: '尚未发布正式版本',
      openRelease: '查看 Release', autoRefresh: '每 20 秒自动更新',
      browser: '浏览器版受 CORS 限制，请用 APK 或自设代理',
      loadErr: '加载失败', back: '返回'
    },
    en: {
      home: 'Home', fav: 'Saved', search: 'Search', settings: 'Settings',
      nearby: 'Nearby stops', building: 'Building index…', ready: 'Ready',
      noFav: 'No saved stops yet — tap the heart on an ETA page', noResult: 'No routes found',
      hint: 'Enter a route, e.g. 1A, 6, K51', refresh: 'Refresh',
      min: 'min', noEta: 'No service', noGps: 'Location not granted — pick a point in Settings',
      lang: 'Language', rebuild: 'Rebuild index', update: 'Check update',
      repo: 'GitHub repo for updates (owner/name)', source: 'Data sources',
      manual: 'Pick a point', saved: 'Saved', removed: 'Removed',
      routes: 'Routes', stops: 'Stops', eta: 'Arrivals',
      ver: 'Version', official: 'Official open APIs, no licence needed',
      latest: 'You are up to date', newVer: 'Update available', noRelease: 'No release published yet',
      openRelease: 'View release', autoRefresh: 'Auto refresh every 20s',
      browser: 'Browser build is CORS-limited — use the APK or your own proxy',
      loadErr: 'Load failed', back: 'Back'
    }
  };

  var MANUAL_POINTS = [
    { tc: '中環', en: 'Central', lat: 22.2819, lng: 114.1577 },
    { tc: '旺角', en: 'Mong Kok', lat: 22.3193, lng: 114.1694 },
    { tc: '尖沙咀', en: 'Tsim Sha Tsui', lat: 22.2939, lng: 114.1708 },
    { tc: '銅鑼灣', en: 'Causeway Bay', lat: 22.2796, lng: 114.1868 },
    { tc: '沙田市中心', en: 'Sha Tin Centre', lat: 22.3794, lng: 114.1893 },
    { tc: '屯門市中心', en: 'Tuen Mun Centre', lat: 22.3910, lng: 113.9775 },
    { tc: '荃灣', en: 'Tsuen Wan', lat: 22.3707, lng: 114.1140 },
    { tc: '觀塘', en: 'Kwun Tong', lat: 22.3107, lng: 114.2253 },
    { tc: '香港站', en: 'Hong Kong Station', lat: 22.2850, lng: 114.1577 },
    { tc: '機場', en: 'Airport', lat: 22.3080, lng: 113.9185 }
  ];

  // 鐵路／輕鐵精簡站表；把 hketa 的 assets/rail.json 放到 web/rail.json 可自動擴充
  var MINI_RAIL = {
    lines: [
      { code: 'AEL', tc: '機場快線', en: 'Airport Express', stops: [{ id: 'HOK', tc: '香港', en: 'Hong Kong' }, { id: 'AIR', tc: '機場', en: 'Airport' }] },
      { code: 'TML', tc: '屯馬線', en: 'Tuen Ma Line', stops: [{ id: 'TUM', tc: '屯門', en: 'Tuen Mun' }, { id: 'HUH', tc: '紅磡', en: 'Hung Hom' }] },
      { code: 'ISL', tc: '港島線', en: 'Island Line', stops: [{ id: 'CEN', tc: '中環', en: 'Central' }, { id: 'CWB', tc: '銅鑼灣', en: 'Causeway Bay' }] }
    ],
    lrt: [{ id: '001', tc: '屯門碼頭', en: 'Tuen Mun Ferry Pier' }]
  };

  // ---- 原生橋接 ----
  var cbs = new Map(), seq = 0;
  window.__hketaCb = {
    resolve: function (id, payload) { var c = cbs.get(id); if (c) { cbs.delete(id); c.ok(payload); } },
    reject: function (id, msg) { var c = cbs.get(id); if (c) { cbs.delete(id); c.fail(new Error(msg)); } }
  };
  function nativeCall(name) {
    var args = Array.prototype.slice.call(arguments, 1);
    return new Promise(function (resolve, reject) {
      var id = 'c' + (++seq);
      cbs.set(id, { ok: resolve, fail: reject });
      try { window.HKETA[name].apply(window.HKETA, args.concat(id)); }
      catch (e) { cbs.delete(id); reject(e); }
      setTimeout(function () { if (cbs.has(id)) { cbs.delete(id); reject(new Error('native timeout')); } }, 20000);
    });
  }
  var NATIVE = !!(window.HKETA && window.HKETA.fetchJson);

  function api(url, method, body) {
    method = method || 'GET';
    if (NATIVE) return nativeCall('fetchJson', url, method, body || '').then(function (s) { return JSON.parse(s); });
    // 瀏覽器降級：政府接口多半不開 CORS，僅供介面預覽
    return fetch(url, {
      method: method,
      headers: method === 'POST' ? { 'Content-Type': 'application/json' } : {},
      body: method === 'POST' ? (body || '{}') : undefined
    }).then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); });
  }

  // ---- 狀態 ----
  var state = {
    tab: 'home',
    lang: localStorage.getItem('hketa_lang') || '',
    index: null,
    favs: JSON.parse(localStorage.getItem('hketa_favs') || '[]'),
    repo: localStorage.getItem('hketa_repo') || 'benyeung08/hketa',
    point: JSON.parse(localStorage.getItem('hketa_point') || 'null'),
    route: null,
    stop: null,
    timer: null,
    rail: MINI_RAIL
  };
  if (!state.lang) {
    var l = (navigator.language || 'zh-Hant').toLowerCase();
    state.lang = l.indexOf('hans') >= 0 || l.indexOf('zh-cn') >= 0 || l.indexOf('zh-sg') >= 0
      ? 'zh-Hans' : (l.indexOf('zh') === 0 ? 'zh-Hant' : 'en');
  }
  function t(k) { return (I18N[state.lang] || I18N['zh-Hant'])[k] || k; }
  function L(tc, en) { return state.lang === 'en' ? (en || tc) : tc; }
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }

  var $view = document.getElementById('view');
  var $status = document.getElementById('status');
  var $toast = document.getElementById('toast');
  function setStatus(s) { $status.textContent = s; }
  var toastTimer;
  function toast(s) {
    $toast.textContent = s; $toast.classList.add('show');
    clearTimeout(toastTimer); toastTimer = setTimeout(function () { $toast.classList.remove('show'); }, 2200);
  }
  function applyI18n() {
    document.documentElement.lang = state.lang;
    document.querySelectorAll('[data-i18n]').forEach(function (el) { el.textContent = t(el.dataset.i18n); });
    document.getElementById('brandSub').textContent = t('eta');
  }
  function clearTimer() { if (state.timer) { clearInterval(state.timer); state.timer = null; } }

  // ---- 索引 ----
  var INDEX_KEY = 'hketa_index_v1';
  var INDEX_TTL = 24 * 60 * 60 * 1000; // 24 小時後自動重建，避免路線改動永遠不更新
  function legacyIndex() {
    var c = localStorage.getItem(INDEX_KEY);
    if (!c) return null;
    try { return JSON.parse(c); } catch (e) { return null; }
  }
  // 索引動輒數 MB，WebView localStorage 約 5MB 就爆；APK 版改存原生 filesDir
  function readIndex() {
    if (NATIVE && window.HKETA.readIndex) {
      return nativeCall('readIndex').then(function (s) {
        if (s) { try { return JSON.parse(s); } catch (e) {} }
        return legacyIndex();
      }).catch(function () { return legacyIndex(); });
    }
    return Promise.resolve(legacyIndex());
  }
  function saveIndex() {
    var txt = JSON.stringify(state.index);
    if (NATIVE && window.HKETA.writeIndex) {
      return nativeCall('writeIndex', txt).catch(function () {
        try { localStorage.setItem(INDEX_KEY, txt); } catch (e) { toast(t('loadErr')); }
      });
    }
    try { localStorage.setItem(INDEX_KEY, txt); } catch (e) { toast(t('loadErr')); }
    return Promise.resolve();
  }
  function buildIndex(force) {
    return readIndex().then(function (cached) {
      if (!force && cached && cached.routes && Date.now() - (cached.builtAt || 0) < INDEX_TTL) {
        state.index = cached; setStatus(t('ready')); return null;
      }
      setStatus(t('building'));
      return Promise.all([api(KMB + 'route/'), api(KMB + 'route-stop/'), api(KMB + 'stop')])
        .then(function (res) {
          var r = res[0], rs = res[1], s = res[2];
          var stops = {};
          (s.data || []).forEach(function (x) {
            stops[x.stop] = { id: x.stop, tc: x.name_tc, en: x.name_en, lat: +x.lat, lng: +x.long, co: 'KMB' };
          });
          var byRoute = {};
          (rs.data || []).forEach(function (x) {
            var k = x.route + '|' + x.bound + '|' + x.service_type;
            (byRoute[k] = byRoute[k] || []).push(x);
          });
          var routes = (r.data || []).map(function (x) {
            var k = x.route + '|' + x.bound + '|' + (x.service_type || '01');
            var seqs = (byRoute[k] || []).sort(function (a, b) { return a.seq - b.seq; }).map(function (y) { return y.stop; });
            return {
              co: 'KMB', route: x.route, bound: x.bound, serviceType: x.service_type || '01',
              orig: x.orig_tc, dest: x.dest_tc, origEn: x.orig_en, destEn: x.dest_en, stops: seqs
            };
          });
          state.index = { routes: routes, stops: stops, ctbRoutes: [], builtAt: Date.now() };
          saveIndex(); setStatus(t('ready'));
          // 補城巴路線清單
          return api(CTB + 'route/CTB').then(function (j) {
            state.index.ctbRoutes = (j.data || []).map(function (x) {
              return { co: 'CTB', route: x.route, bound: x.bound, orig: x.orig_tc, dest: x.dest_tc, origEn: x.orig_en, destEn: x.dest_en, stops: [] };
            });
            saveIndex();
          }).catch(function () {});
        })
        .catch(function (e) {
          setStatus(t('loadErr')); toast(e.message || t('loadErr'));
          state.index = state.index || { routes: [], stops: {}, ctbRoutes: [], builtAt: 0 };
        });
    });
  }

  function loadRail() {
    var get = NATIVE
      ? nativeCall('readAsset', 'rail.json').then(function (s) { return JSON.parse(s); })
      : fetch('./rail.json').then(function (r) { return r.json(); });
    return get.then(function (j) { if (j && j.lines) state.rail = j; }).catch(function () { /* 用內置精簡站表 */ });
  }

  // ---- ETA 解析 ----
  function pick(o) {
    for (var i = 1; i < arguments.length; i++) if (o && o[arguments[i]] != null && o[arguments[i]] !== '') return o[arguments[i]];
    return '';
  }
  function toMinutes(v) {
    if (v == null || v === '') return null;
    if (typeof v === 'number') return Math.max(0, Math.round(v));
    var s = String(v).trim();
    if (/^\d{4}-\d{2}-\d{2}/.test(s) || s.indexOf('T') > 0) {
      var d = new Date(s.replace(' ', 'T')).getTime();
      if (!isNaN(d)) return Math.max(0, Math.round((d - Date.now()) / 60000));
    }
    var n = parseFloat(s.replace(/[^\d.\-]/g, ''));
    return isNaN(n) ? null : Math.max(0, Math.round(n));
  }
  function normalizeEta(co, j) {
    if (!j || typeof j !== 'object') return [];
    var raw = [];
    if (Array.isArray(j.data)) raw.push.apply(raw, j.data);
    else if (j.data && typeof j.data === 'object') Object.keys(j.data).forEach(function (k) {
      var v = j.data[k]; if (Array.isArray(v)) raw.push.apply(raw, v); else if (v && typeof v === 'object') raw.push(v);
    });
    if (Array.isArray(j.estimatedArrivals)) raw.push.apply(raw, j.estimatedArrivals);
    if (Array.isArray(j.platform_list)) j.platform_list.forEach(function (p) {
      (p.route_list || p.routes || []).forEach(function (r) {
        raw.push({ eta: r.time_en != null ? r.time_en : r.eta, dest_tc: r.dest_ch || r.dest_tc, rmk_tc: r.rmk_ch || '' });
      });
    });
    return raw.map(function (x) {
      return {
        min: toMinutes(pick(x, 'eta', 'estimatedArrivalTime', 'arrivalTime', 'time', 'min')),
        dest: pick(x, 'dest_tc', 'dest_ch', 'dest', 'destination', 'dest_en'),
        rmk: pick(x, 'rmk_tc', 'rmk_ch', 'remark', 'rmk_en'),
        route: pick(x, 'route', 'route_no', 'routeName', 'lineRef')
      };
    }).filter(function (x) { return x.min !== null || x.dest; }).slice(0, 6);
  }

  function etaUrl(stop) {
    var co = stop.co || 'KMB', id = encodeURIComponent(stop.id), rt = encodeURIComponent(stop.route || '');
    if (co === 'KMB') return [KMB + 'stop-eta/' + id, 'GET', ''];
    if (co === 'CTB') return [CTB + 'eta/CTB/' + id + '/' + rt, 'GET', ''];
    if (co === 'GMB') return [GMB + 'stop-eta/' + id + '/' + rt, 'GET', ''];
    if (co === 'NLB') return [NLB + 'stop.php?action=estimatedArrivals&routeId=' + rt + '&stopId=' + id, 'GET', ''];
    if (co === 'MTR') return [MTR + 'getSchedule.php?line=' + rt + '&sta=' + id + '&lang=TC', 'GET', ''];
    if (co === 'LRT') return [MTR + 'lrt/getSchedule?station_id=' + id + '&with_special=1', 'GET', ''];
    // 官方 MTR Bus API v1.13 規定 POST body 必須是 {language, routeName}
    if (co === 'MTRB') return [MTR + 'bus/getSchedule', 'POST', JSON.stringify({ language: 'zh', routeName: stop.route })];
    return [KMB + 'stop-eta/' + id, 'GET', ''];
  }
  function fetchEta(stop) {
    var u = etaUrl(stop);
    return api(u[0], u[1], u[2]).then(function (j) {
      var list = normalizeEta(stop.co || 'KMB', j);
      // KMB stop-eta 會回傳「全站所有路線」；預報頁標題是單一路線，必須過濾
      if (stop.route) list = list.filter(function (e) {
        return !e.route || String(e.route).toUpperCase() === String(stop.route).toUpperCase();
      });
      return list;
    });
  }

  // 限制同時在途的請求數，避免收藏一多就一次打爆 API（429）
  function mapLimit(items, limit, fn) {
    return new Promise(function (resolve) {
      var i = 0, running = 0, done = 0;
      if (!items.length) return resolve();
      function next() {
        while (running < limit && i < items.length) {
          var idx = i++;
          running++;
          Promise.resolve(fn(items[idx], idx)).catch(function () {}).then(function () {
            running--; done++;
            if (done === items.length) resolve(); else next();
          });
        }
      }
      next();
    });
  }

  // ---- 距離 ----
  function dist(a, b, c, d) {
    var R = 6371, p = Math.PI / 180;
    var x = (c - a) * p, y = (d - b) * p;
    var h = Math.sin(x / 2) * Math.sin(x / 2) + Math.cos(a * p) * Math.cos(c * p) * Math.sin(y / 2) * Math.sin(y / 2);
    return 2 * R * Math.asin(Math.sqrt(h));
  }
  function getPoint() {
    if (state.point) return Promise.resolve(state.point);
    if (!NATIVE) return Promise.reject(new Error(t('noGps')));
    return nativeCall('getLocation').then(function (s) { return JSON.parse(s); });
  }
  function favKey(stop) { return (stop.co || 'KMB') + ':' + stop.id + ':' + (stop.route || ''); }
  function isFav(stop) { return state.favs.some(function (f) { return favKey(f) === favKey(stop); }); }

  // ---- 畫面 ----
  function etaHtml(list) {
    if (!list.length) return '<div class="sub">' + esc(t('noEta')) + '</div>';
    return list.map(function (e) {
      return '<div class="eta-line"><span class="min">' + (e.min === null ? '—' : e.min) +
        '<small>' + esc(t('min')) + '</small></span><span class="dest">' + esc(e.dest) +
        (e.rmk ? ' <span class="sub">· ' + esc(e.rmk) + '</span>' : '') + '</span></div>';
    }).join('');
  }
  function badge(co) { return '<span class="badge ' + esc(co.toLowerCase()) + '">' + esc(co) + '</span>'; }

  function renderHome() {
    if (!state.index) {
      $view.innerHTML = '<div class="empty">' + esc(t('loadErr')) + '</div>';
      return Promise.resolve();
    }
    $view.innerHTML = '<h2>' + esc(t('nearby')) + ' <span class="spin"></span></h2><div id="homeList"></div>';
    var box = document.getElementById('homeList');
    return getPoint().then(function (p) {
      var near = Object.keys(state.index.stops).map(function (k) { return state.index.stops[k]; })
        .filter(function (s) { return s.lat && s.lng; })
        .map(function (s) { return { s: s, d: dist(p.lat, p.lng, s.lat, s.lng) }; })
        .filter(function (x) { return x.d <= 3; }).sort(function (a, b) { return a.d - b.d; }).slice(0, 6);
      if (!near.length) { box.innerHTML = '<div class="empty">' + esc(t('noResult')) + '</div>'; return; }
      box.innerHTML = near.map(function (x) {
        return '<div class="card" data-id="' + esc(x.s.id) + '"><div class="row"><div><div class="dest">' +
          esc(L(x.s.tc, x.s.en)) + '</div><div class="sub">' + badge('KMB') + ' ' + esc(x.s.id) +
          ' · ' + x.d.toFixed(1) + ' km</div></div><div class="min" data-eta>—</div></div></div>';
      }).join('');
      // 每站只查一條路線，慳請求（與 hketa 主頁策略相同）
      return Promise.all(near.slice(0, 5).map(function (x) {
        return fetchEta({ co: 'KMB', id: x.s.id, route: '' })
          .then(function (l) {
            var sid = window.CSS && CSS.escape ? CSS.escape(x.s.id) : String(x.s.id).replace(/["\\]/g, '');
            var el = box.querySelector('[data-id="' + sid + '"] [data-eta]');
            if (el) el.innerHTML = l.length && l[0].min !== null ? l[0].min + '<small>' + esc(t('min')) + '</small>' : '—';
          }).catch(function () {});
      }));
    }).catch(function (e) {
      box.innerHTML = '<div class="empty">' + esc(e.message || t('noGps')) + '</div>';
    }).then(function () { setStatus(t('ready')); });
  }

  function renderSearch(q, fromInput) {
    var input = document.getElementById('q');
    if (!input) {
      $view.innerHTML = '<div class="btn-row"><input id="q" class="input" placeholder="' + esc(t('hint')) + '"></div>' +
        '<div id="searchResults"></div>';
      input = document.getElementById('q');
      input.addEventListener('input', function () { renderSearchResults(input.value); });
      input.addEventListener('keydown', function (e) { if (e.key === 'Enter') input.blur(); });
    }
    if (!fromInput) input.value = q || '';
    renderSearchResults(input.value);
  }
  function renderSearchResults(raw) {
    var q = (raw || '').trim().toUpperCase();
    var box = document.getElementById('searchResults');
    if (!box) return;
    var all = (state.index.routes || []).concat(state.index.ctbRoutes || []);
    var list = q ? all.filter(function (r) {
      return r.route.toUpperCase().indexOf(q) >= 0 || String(r.dest || '').indexOf(q) >= 0 || String(r.orig || '').indexOf(q) >= 0;
    }) : all.slice(0, 30);
    var rail = q ? (state.rail.lines || []).filter(function (l) { return l.code.indexOf(q) >= 0 || String(l.en).toUpperCase().indexOf(q) >= 0; }) : [];
    var html = '<h2>' + esc(t('routes')) + ' · ' + list.length + '</h2>';
    if (!list.length && !rail.length) html += '<div class="empty">' + esc(t('noResult')) + '</div>';
    html += list.slice(0, 60).map(function (r, i) {
      return '<div class="card tap" data-act="route" data-co="' + esc(r.co) + '" data-i="' + i + '" data-q="' + esc(q) + '">' +
        '<div class="row"><span class="route-no">' + esc(r.route) + '</span><span class="dest">' +
        esc(L(r.orig, r.origEn)) + ' → ' + esc(L(r.dest, r.destEn)) + '</span>' + badge(r.co) + '</div></div>';
    }).join('');
    if (rail.length) html += '<h2>MTR</h2>' + rail.map(function (l) {
      return '<div class="card"><div class="row"><span class="route-no">' + esc(l.code) + '</span><span class="dest">' +
        esc(L(l.tc, l.en)) + '</span>' + badge('MTR') + '</div></div>';
    }).join('');
    // 只更新結果區，不再重建輸入框：避免掉焦點、中斷中文輸入法、重複渲染
    box.innerHTML = html;
  }

  function renderRoute(r) {
    var stops = (r.stops || []).map(function (id) { return state.index.stops[id]; }).filter(Boolean);
    var html = '<div class="btn-row"><button class="btn" data-act="back">' + esc(t('back')) + '</button>' +
      '<button class="btn primary" data-act="refresh">' + esc(t('refresh')) + '</button></div>' +
      '<h2>' + esc(r.route) + ' · ' + esc(L(r.orig, r.origEn)) + ' → ' + esc(L(r.dest, r.destEn)) + '</h2>';
    if (!stops.length) html += '<div class="empty">' + esc(t('noEta')) + '</div>';
    html += stops.map(function (s, i) {
      return '<div class="card tap" data-act="stop" data-i="' + i + '"><div class="row"><span class="badge">' + (i + 1) + '</span>' +
        '<span class="dest">' + esc(L(s.tc, s.en)) + '</span><span class="sub">' + esc(s.id) + '</span></div></div>';
    }).join('');
    $view.innerHTML = html;
    state.route = { r: r, stops: stops };
  }

  function renderEta(stop) {
    state.stop = stop; clearTimer();
    var head = '<div class="btn-row"><button class="btn" data-act="back">' + esc(t('back')) + '</button>' +
      '<button class="btn primary" data-act="refresh">' + esc(t('refresh')) + '</button>' +
      '<button class="fav-btn ' + (isFav(stop) ? 'on' : '') + '" data-act="fav">' + (isFav(stop) ? '♥' : '♡') + '</button></div>' +
      '<h2>' + esc(L(stop.tc, stop.en)) + ' <span class="sub">' + badge(stop.co || 'KMB') + ' ' + esc(stop.route || '') + '</span></h2>' +
      '<div class="sub" style="margin-bottom:10px">' + esc(t('autoRefresh')) + '</div><div id="etaBox" class="card"><span class="spin"></span></div>';
    $view.innerHTML = head;
    function tick() {
      if (document.hidden) return; // 背景不發請求，配合 MainActivity 的 pauseTimers 省電
      var box = document.getElementById('etaBox'); if (!box) return;
      fetchEta(stop).then(function (l) { if (box.isConnected) box.innerHTML = etaHtml(l); })
        .catch(function (e) { if (box.isConnected) box.innerHTML = '<div class="sub">' + esc(e.message) + '</div>'; });
    }
    tick();
    state.timer = setInterval(tick, 20000);
  }

  function renderFav() {
    clearTimer();
    if (!state.favs.length) { $view.innerHTML = '<div class="empty">' + esc(t('noFav')) + '</div>'; return; }
    $view.innerHTML = '<h2>' + esc(t('fav')) + ' · ' + state.favs.length + '</h2><div id="favList"></div>';
    var box = document.getElementById('favList');
    box.innerHTML = state.favs.map(function (f, i) {
      return '<div class="card tap" data-act="favopen" data-i="' + i + '"><div class="row"><div><div class="dest">' +
        esc(L(f.tc, f.en)) + '</div><div class="sub">' + badge(f.co) + ' ' + esc(f.route || '') + '</div></div>' +
        '<div class="min" data-eta>—</div></div></div>';
    }).join('');
    mapLimit(state.favs, 3, function (f, i) {
      return fetchEta(f).then(function (l) {
        var el = box.querySelector('[data-i="' + i + '"] [data-eta]');
        if (el) el.innerHTML = l.length && l[0].min !== null ? l[0].min + '<small>' + esc(t('min')) + '</small>' : '—';
      });
    });
  }

  function renderSettings() {
    clearTimer();
    var v = '—';
    if (NATIVE) nativeCall('getVersion').then(function (s) {
      var el = document.getElementById('verVal'); if (el) el.textContent = s;
    }).catch(function () {});
    $view.innerHTML =
      '<h2>' + esc(t('settings')) + '</h2>' +
      '<div class="card"><div class="sub">' + esc(t('lang')) + '</div><div class="btn-row">' +
      ['zh-Hant', 'zh-Hans', 'en'].map(function (l) {
        return '<button class="btn ' + (state.lang === l ? 'primary' : '') + '" data-act="lang" data-v="' + l + '">' +
          (l === 'zh-Hant' ? '繁體中文' : l === 'zh-Hans' ? '简体中文' : 'English') + '</button>';
      }).join('') + '</div></div>' +
      '<div class="card"><div class="sub">' + esc(t('manual')) + '</div><div class="btn-row">' +
      MANUAL_POINTS.slice(0, 6).map(function (p, i) {
        return '<button class="btn" data-act="point" data-i="' + i + '">' + esc(L(p.tc, p.en)) + '</button>';
      }).join('') + '</div></div>' +
      '<div class="card"><div class="row"><span class="dest">' + esc(t('rebuild')) + '</span>' +
      '<button class="btn" data-act="rebuild">' + esc(t('refresh')) + '</button></div>' +
      '<div class="sub" style="margin-top:8px">' + esc(t('ver')) + ' <span id="verVal">' + esc(v) + '</span></div></div>' +
      '<div class="card"><div class="sub">' + esc(t('repo')) + '</div>' +
      '<input id="repo" class="input" style="margin-top:6px" placeholder="owner/hketa" value="' + esc(state.repo) + '">' +
      '<div class="btn-row"><button class="btn primary" data-act="update">' + esc(t('update')) + '</button></div>' +
      '<div id="updateBox" class="sub"></div></div>' +
      '<div class="card"><div class="dest">' + esc(t('source')) + '</div><div class="sub" style="margin-top:6px">' +
      esc(t('official')) + '：data.etabus.gov.hk · rt.data.gov.hk · data.etagmb.gov.hk · data.gov.hk</div></div>';
    document.getElementById('repo').addEventListener('change', function (e) {
      state.repo = e.target.value.trim(); localStorage.setItem('hketa_repo', state.repo);
    });
  }

  function checkUpdate() {
    var box = document.getElementById('updateBox'); if (!box) return;
    if (!state.repo || state.repo.indexOf('/') < 0) { box.textContent = t('repo'); return; }
    box.innerHTML = '<span class="spin"></span>';
    Promise.all([
      api('https://api.github.com/repos/' + state.repo + '/releases/latest'),
      NATIVE ? nativeCall('getVersion') : Promise.resolve('0.0.0')
    ]).then(function (r) {
      var j = r[0], cur = String(r[1] || '').replace(/^v/, ''), latest = String(j.tag_name || '').replace(/^v/, '');
      if (j.message === 'Not Found') { box.textContent = t('noRelease'); return; }
      if (latest && latest !== cur) {
        box.innerHTML = '<b>' + esc(t('newVer')) + ' ' + esc(j.tag_name) + '</b><br>' +
          '<button class="btn" style="margin-top:8px" data-act="release" data-url="' + esc(j.html_url) + '">' + esc(t('openRelease')) + '</button>';
      } else box.textContent = t('latest') + ' (' + esc(cur) + ')';
    }).catch(function (e) { box.textContent = e.message || t('loadErr'); });
  }

  function render() {
    clearTimer(); applyI18n();
    document.querySelectorAll('.tab').forEach(function (b) { b.classList.toggle('active', b.dataset.tab === state.tab); });
    if (state.tab === 'home') return renderHome();
    if (state.tab === 'search') return renderSearch('');
    if (state.tab === 'fav') return renderFav();
    return renderSettings();
  }

  // ---- 事件 ----
  document.addEventListener('click', function (e) {
    var tab = e.target.closest('[data-tab]');
    if (tab) { state.tab = tab.dataset.tab; state.route = null; state.stop = null; render(); return; }
    var el = e.target.closest('[data-act]'); if (!el) return;
    var act = el.dataset.act;
    if (act === 'route') {
      var q = el.dataset.q || '', all = (state.index.routes || []).concat(state.index.ctbRoutes || []);
      var list = q ? all.filter(function (r) {
        return r.route.toUpperCase().indexOf(q.toUpperCase()) >= 0 || String(r.dest || '').indexOf(q) >= 0 || String(r.orig || '').indexOf(q) >= 0;
      }) : all.slice(0, 30);
      var r = list[+el.dataset.i]; if (r) renderRoute(r);
    } else if (act === 'stop') {
      var s = state.route.stops[+el.dataset.i];
      renderEta({ co: state.route.r.co, id: s.id, tc: s.tc, en: s.en, route: state.route.r.route });
    } else if (act === 'fav') {
      var k = favKey(state.stop);
      if (isFav(state.stop)) { state.favs = state.favs.filter(function (f) { return favKey(f) !== k; }); toast(t('removed')); }
      else { state.favs.push(state.stop); toast(t('saved')); }
      localStorage.setItem('hketa_favs', JSON.stringify(state.favs)); renderEta(state.stop);
    } else if (act === 'favopen') {
      renderEta(state.favs[+el.dataset.i]);
    } else if (act === 'refresh') {
      if (state.stop) renderEta(state.stop); else if (state.route) renderRoute(state.route.r); else render();
    } else if (act === 'back') {
      if (state.stop) { state.stop = null; renderRoute(state.route.r); }
      else { state.route = null; state.tab = 'search'; render(); }
    } else if (act === 'lang') {
      state.lang = el.dataset.v; localStorage.setItem('hketa_lang', state.lang); render();
    } else if (act === 'point') {
      state.point = MANUAL_POINTS[+el.dataset.i];
      localStorage.setItem('hketa_point', JSON.stringify(state.point)); state.tab = 'home'; render();
    } else if (act === 'rebuild') {
      buildIndex(true).then(render);
    } else if (act === 'update') {
      checkUpdate();
    } else if (act === 'release') {
      if (NATIVE) nativeCall('openExternal', el.dataset.url).catch(function () {});
      else window.open(el.dataset.url, '_blank');
    }
  });

  // ---- 啟動 ----
  if ('serviceWorker' in navigator && location.protocol === 'https:') {
    navigator.serviceWorker.register('sw.js').catch(function () {});
  }
  applyI18n();
  if (!NATIVE) setStatus(t('browser'));
  // MainActivity 在定位授權完成後派發此事件，主頁立即重算附近車站
  document.addEventListener('hketa:location', function () {
    if (state.tab === 'home') render();
  });
  Promise.all([buildIndex(false), loadRail()]).then(function () { render(); }).catch(function () { render(); });
})();
