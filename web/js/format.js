/**
 * 顯示格式化 —— 對應 `ui/Format.kt`。
 *
 * 全部係**純函數**（唔掂 DOM、唔讀語言 state），方便單測。
 * 語言由呼叫方傳入，所以唔使 `@Composable`。
 */
(function (root) {
  'use strict';

  var PAIRS = [
    ['（', '）'],   // 全角（要先試，因為全角括號入面可能有半角）
    ['(', ')'],
    ['［', '］'],
    ['[', ']']
  ];

  /**
   * 剝走站名入面**全部**括號段（連括號一齊）。
   *
   * 「附近」嘅卡位得咁上下闊，站號（如 `海麗邨巴士總站 (SS667)` 嗰個 SS667）
   * 只會阻地方；而同一個站唔同營辦商有唔同站號，顯示邊一個都唔啱。
   *
   * 半角 `()`、全角 `（）`、`[]`、`［］` 四種都處理，可以剝多段。
   * 車站頁同 ETA 卡仍然顯示完整站名連站號 —— 想對返官方站牌嗰陣有得睇。
   */
  function stripBrackets(name) {
    if (name == null) return '';
    var s = String(name).trim();
    if (s === '') return '';
    var original = s;

    // 逐段剝：重複到冇得剝為止（上限 6 段，避免 pathological 輸入）
    for (var round = 0; round < 6; round++) {
      var before = s;
      for (var p = 0; p < PAIRS.length; p++) {
        var l = PAIRS[p][0], r = PAIRS[p][1];
        var i = s.indexOf(l);
        var j = i < 0 ? -1 : s.indexOf(r, i + l.length);
        if (i >= 0 && j > i) {
          s = (s.substring(0, i) + s.substring(j + r.length)).trim();
        }
      }
      if (s === before) break;
    }
    return s === '' ? original : s;
  }

  /**
   * 車站顯示名：英文介面且官方有提供英文站名就用英文，否則用中文原名。
   *
   * 九巴、城巴、專線小巴接口都有 name_en；嶼巴同鐵路只提供中文，
   * 嗰啲會照舊顯示中文 —— 唔會自己硬翻，避免同官方站牌對唔上。
   */
  function displayName(stop, en) {
    if (!stop) return '';
    if (en && stop.nameEn && String(stop.nameEn).trim() !== '') return stop.nameEn;
    return stop.name || '';
  }

  /**
   * 顯示用嘅「站號」。
   *
   * 有啲路徑（例如由路線–站對應兜底出嚟嘅站）會把 routeId
   * （如 `296E43213D6DB624`）當咗做 stop id。呢啲唔係站號，
   * 顯示出嚟只會令畫面出現「一串數字」，而且攞去查 ETA 一定失敗。
   */
  function codeLabel(stop) {
    if (!stop || stop.id == null) return '';
    var c = String(stop.id).trim();
    if (c === '') return '';
    if (c.length > 12) return '';   // 明顯係 routeId，唔係站號
    return c;
  }

  /** 站名（連站號一次過），唔會重複出現括號站號 */
  function titleWithCode(stop, en) {
    var n = displayName(stop, en);
    var c = codeLabel(stop);
    if (c === '') return n;
    if (n.indexOf('(' + c + ')') >= 0 || n.indexOf('（' + c + '）') >= 0) return n;
    return n + '（' + c + '）';
  }

  /** 「附近」卡片用嘅淨站名（連括號站號都剝走） */
  function plainName(stop, en) {
    return stripBrackets(displayName(stop, en));
  }

  /** 距離顯示：1000 米以下用米，否則用公里 */
  function distanceText(m) {
    if (m == null || isNaN(m)) return '';
    if (m < 1000) return Math.round(m) + ' m';
    return (m / 1000).toFixed(1) + ' km';
  }

  /** 分鐘數顯示 */
  function etaText(minutes, t) {
    if (minutes == null) return (t && t.no_eta) ? t.no_eta : '暫時冇班次';
    if (minutes <= 0) return (t && t.arriving) ? t.arriving : '即將到站';
    return minutes + ' ' + ((t && t.minutes) ? t.minutes : '分鐘');
  }

  /**
   * 合併同一個實體站嘅路線號，並排序。
   *
   * 排序要**數字行先、其次字母**，而且係「自然排序」
   * （`6` 要排喺 `12` 前面，唔可以純字串排成 `12` < `6`）。
   */
  function sortRoutes(list) {
    var seen = Object.create(null);
    var out = [];
    for (var i = 0; i < list.length; i++) {
      var r = String(list[i] == null ? '' : list[i]).trim().toUpperCase();
      if (r === '' || seen[r]) continue;
      seen[r] = true;
      out.push(r);
    }
    out.sort(function (a, b) {
      var na = /^\d/.test(a), nb = /^\d/.test(b);
      if (na && !nb) return -1;
      if (!na && nb) return 1;
      var va = parseInt(a, 10), vb = parseInt(b, 10);
      if (!isNaN(va) && !isNaN(vb) && va !== vb) return va - vb;
      return a < b ? -1 : (a > b ? 1 : 0);
    });
    return out;
  }

  var Format = {
    stripBrackets: stripBrackets,
    displayName: displayName,
    codeLabel: codeLabel,
    titleWithCode: titleWithCode,
    plainName: plainName,
    distanceText: distanceText,
    etaText: etaText,
    sortRoutes: sortRoutes
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = Format;
  root.Format = Format;
})(typeof globalThis !== 'undefined' ? globalThis : this);
