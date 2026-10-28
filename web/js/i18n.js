/**
 * 四語文案 —— 對應 `res/values*/strings.xml`。
 *
 * | 目錄 | 語言 | 呢度嘅 key |
 * |---|---|---|
 * | `values/` | 繁中（預設） | `zh-Hant` |
 * | `values-zh-rCN/` | 簡中 | `zh-Hans` |
 * | `values-en/` | 英文 | `en` |
 * | `values-b+zh+Hans/` | 簡中（BCP-47 後備） | `zh-Hans` |
 *
 * 語言由 `navigator.language` 決定，設定頁可以手動揀（存落 localStorage，優先過系統）。
 * 次序同 Android 一致：**手動 ＞ 系統 ＞ 繁中**。
 */
(function (root) {
  'use strict';

  var STRINGS = {
    'zh-Hant': {
      app_name: 'HKATE',
      tab_home: '主頁', tab_search: '搜尋', tab_fav: '收藏', tab_settings: '設定',
      routes: '路線', stops: '車站',
      input_route: '輸入路線號碼', input_stop: '輸入車站名稱',
      rail: '鐵路', nearby: '附近',
      loading: '載入中…', empty: '暫時冇嘢', retry: '重試',
      minutes: '分鐘', arriving: '即將到站', offline: '離線', no_eta: '暫時冇班次',
      route_stops: '沿途車站', stop_eta: '到站預報',
      nearby_title: '附近車站', meters: 'm',
      fav_title: '收藏車站', fav_empty: '重未收藏任何車站',
      settings_title: '設定', index_stats: '索引統計',
      rebuild: '重建索引', repair: '修復', download_stops: '下載車站資料',
      clear_cache: '清除 ETA 快取', auto_refresh: '自動更新',
      kernel: '核心', speed: '加速層', store: '儲存', backend: '索引格式',
      check_update: '檢查更新', version_history: '版本歷史', about: '關於',
      up_to_date: '已經係最新版', new_version: '發現新版本',
      download: '下載更新', install: '安裝',
      direction: '方向', all_directions: '全部方向',
      op_stats: '路線／車站', favorites_added: '已加入收藏', favorites_removed: '已移除收藏',
      refresh: '更新', close: '關閉',
      op_kmb: '九巴', op_ctb: '城巴', op_nlb: '嶼巴', op_gmb: '專線小巴',
      op_mtr_bus: '港鐵巴士', op_mtr_hr: '港鐵', op_lrt: '輕鐵',
      timetable: '班次表', timetable_empty: '暫時砌唔到班次表',
      headway: '班次間隔', service_until: '表列服務到',
      locate_fail: '定位唔到，用咗上次嘅位置',
      locate_denied: '冇定位權限', network_fail: '網絡連唔上',
      cors_hint: '瀏覽器版可能會被 CORS 擋住 —— 去設定頁填個代理',
      kernel_js: 'JavaScript', kernel_wasm: 'WebAssembly',
      index_ready: '索引就緒', index_building: '索引建立中…',
      tile_pack: '離線地圖瓦片包', tile_download: '下載', tile_stop: '停止'
    },

    'zh-Hans': {
      app_name: 'HKATE',
      tab_home: '主页', tab_search: '搜寻', tab_fav: '收藏', tab_settings: '设置',
      routes: '路线', stops: '车站',
      input_route: '输入路线号码', input_stop: '输入车站名称',
      rail: '铁路', nearby: '附近',
      loading: '载入中…', empty: '暂时没东西', retry: '重试',
      minutes: '分钟', arriving: '即将到站', offline: '离线', no_eta: '暂时没班次',
      route_stops: '沿途车站', stop_eta: '到站预报',
      nearby_title: '附近车站', meters: 'm',
      fav_title: '收藏车站', fav_empty: '还未收藏任何车站',
      settings_title: '设置', index_stats: '索引统计',
      rebuild: '重建索引', repair: '修复', download_stops: '下载车站资料',
      clear_cache: '清除 ETA 缓存', auto_refresh: '自动更新',
      kernel: '核心', speed: '加速层', store: '储存', backend: '索引格式',
      check_update: '检查更新', version_history: '版本历史', about: '关于',
      up_to_date: '已经是最新版', new_version: '发现新版本',
      download: '下载更新', install: '安装',
      direction: '方向', all_directions: '全部方向',
      op_stats: '路线／车站', favorites_added: '已加入收藏', favorites_removed: '已移除收藏',
      refresh: '更新', close: '关闭',
      op_kmb: '九巴', op_ctb: '城巴', op_nlb: '屿巴', op_gmb: '专线小巴',
      op_mtr_bus: '港铁巴士', op_mtr_hr: '港铁', op_lrt: '轻铁',
      timetable: '班次表', timetable_empty: '暂时砌不出班次表',
      headway: '班次间隔', service_until: '表列服务到',
      locate_fail: '定位不到，用了上次的位置',
      locate_denied: '没定位权限', network_fail: '网络连不上',
      cors_hint: '浏览器版可能会被 CORS 挡住 —— 去设置页填个代理',
      kernel_js: 'JavaScript', kernel_wasm: 'WebAssembly',
      index_ready: '索引就绪', index_building: '索引建立中…',
      tile_pack: '离线地图瓦片包', tile_download: '下载', tile_stop: '停止'
    },

    'en': {
      app_name: 'HKATE',
      tab_home: 'Home', tab_search: 'Search', tab_fav: 'Favourites', tab_settings: 'Settings',
      routes: 'Routes', stops: 'Stops',
      input_route: 'Enter route number', input_stop: 'Enter stop name',
      rail: 'Rail', nearby: 'Nearby',
      loading: 'Loading…', empty: 'Nothing here', retry: 'Retry',
      minutes: 'min', arriving: 'Arriving', offline: 'Offline', no_eta: 'No departures',
      route_stops: 'Route stops', stop_eta: 'Arrival times',
      nearby_title: 'Nearby stops', meters: 'm',
      fav_title: 'Saved stops', fav_empty: 'No saved stops yet',
      settings_title: 'Settings', index_stats: 'Index stats',
      rebuild: 'Rebuild index', repair: 'Repair', download_stops: 'Download stop data',
      clear_cache: 'Clear ETA cache', auto_refresh: 'Auto refresh',
      kernel: 'Kernel', speed: 'Speed layer', store: 'Storage', backend: 'Index format',
      check_update: 'Check for updates', version_history: 'Version history', about: 'About',
      up_to_date: 'Already up to date', new_version: 'New version found',
      download: 'Download', install: 'Install',
      direction: 'Direction', all_directions: 'All directions',
      op_stats: 'Routes / stops', favorites_added: 'Added to favourites',
      favorites_removed: 'Removed from favourites',
      refresh: 'Refresh', close: 'Close',
      op_kmb: 'KMB', op_ctb: 'Citybus', op_nlb: 'NLB', op_gmb: 'GMB',
      op_mtr_bus: 'MTR Bus', op_mtr_hr: 'MTR', op_lrt: 'Light Rail',
      timetable: 'Timetable', timetable_empty: 'Could not build timetable',
      headway: 'Headway', service_until: 'Listed service until',
      locate_fail: 'Location unavailable, using last known',
      locate_denied: 'Location permission denied', network_fail: 'Network unreachable',
      cors_hint: 'Browser build may be blocked by CORS — set a proxy in Settings',
      kernel_js: 'JavaScript', kernel_wasm: 'WebAssembly',
      index_ready: 'Index ready', index_building: 'Building index…',
      tile_pack: 'Offline map tiles', tile_download: 'Download', tile_stop: 'Stop'
    }
  };

  var LS_KEY = 'hkate_lang';
  var current = null;

  /** 由 `navigator.language` 揀：`zh-TW`/`zh-HK` → 繁，`zh-CN`/`zh-SG` → 簡 */
  function detect() {
    var nav = (root.navigator && root.navigator.language) || '';
    var s = String(nav).toLowerCase();
    if (s.indexOf('zh-hans') >= 0 || s.indexOf('zh-cn') >= 0 ||
        s.indexOf('zh-sg') >= 0 || s === 'zh') return 'zh-Hans';
    if (s.indexOf('zh') >= 0) return 'zh-Hant';
    return 'en';
  }

  /** 次序：手動 ＞ 系統 ＞ 繁中（同 Android 一致） */
  function resolve() {
    if (current) return current;
    var saved = null;
    try { saved = root.localStorage ? root.localStorage.getItem(LS_KEY) : null; } catch (e) { /* 忽略 */ }
    if (saved && STRINGS[saved]) { current = saved; return current; }
    current = detect();
    return current;
  }

  function set(lang) {
    if (!STRINGS[lang]) return;
    current = lang;
    try { if (root.localStorage) root.localStorage.setItem(LS_KEY, lang); } catch (e) { /* 忽略 */ }
    if (root.document) root.document.documentElement.lang = lang;
  }

  /** 攞文案；冇嗰條就返 key 本身，方便發現漏翻譯 */
  function t(key) {
    var lang = resolve();
    var pack = STRINGS[lang] || STRINGS['zh-Hant'];
    var v = pack[key];
    if (v == null) v = (STRINGS['zh-Hant'][key] != null) ? STRINGS['zh-Hant'][key] : key;
    return v;
  }

  function isEnglish() { return resolve() === 'en'; }
  function isSimplified() { return resolve() === 'zh-Hans'; }

  var I18n = {
    STRINGS: STRINGS,
    t: t,
    set: set,
    detect: detect,
    resolve: resolve,
    isEnglish: isEnglish,
    isSimplified: isSimplified,
    langs: [
      { id: 'zh-Hant', label: '繁體中文' },
      { id: 'zh-Hans', label: '简体中文' },
      { id: 'en', label: 'English' }
    ]
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = I18n;
  root.I18n = I18n;
})(typeof globalThis !== 'undefined' ? globalThis : this);
