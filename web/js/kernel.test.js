/**
 * 內核單測 —— 對應 `app/src/test/java/com/hketa/app/data/KernelParityTest.kt`。
 *
 * 呢啲數字**唔係隨便寫**：全部嚟自 Kotlin 回退版嘅實際輸出，
 * 所以「Kotlin 版」同「JS 版」只要有一個對唔上，就代表搬嗰陣搬錯咗。
 *
 * 跑法：`node js/kernel.test.js`
 */
(function () {
  'use strict';

  var K = require('./kernel.js');

  var pass = 0, fail = 0;

  function ok(cond, name, extra) {
    if (cond) { pass++; }
    else { fail++; console.error('  FAIL  ' + name + (extra ? '  → ' + extra : '')); }
  }

  function eq(actual, expected, name) {
    var a = JSON.stringify(actual), b = JSON.stringify(expected);
    ok(a === b, name, '實際 ' + a + ' ／ 預期 ' + b);
  }

  // ---------------------------------------------------------------- 取樣

  console.log('取樣 sampleIndices');

  // Kotlin 原版嘅 golden value：40 個站取 12 個
  eq(K.sampleIndices(40, 12), [0, 3, 7, 10, 14, 17, 21, 24, 28, 31, 35, 39],
    'n=40 max=12（截斷唔係四捨五入）');

  // 邊界
  eq(K.sampleIndices(0, 12), [], 'n=0 → 空');
  eq(K.sampleIndices(10, 0), [], 'max=0 → 空');
  eq(K.sampleIndices(-5, 12), [], 'n<0 → 空');
  eq(K.sampleIndices(5, 12), [0, 1, 2, 3, 4], 'n<=max → 全部');
  eq(K.sampleIndices(5, 5), [0, 1, 2, 3, 4], 'n==max → 全部');
  eq(K.sampleIndices(1, 12), [0], 'n=1 → [0]');

  // max=1 嘅 NaN 陷阱：(n-1)/0 = Infinity，0*Infinity = NaN
  // Rust 嘅 float→int 係 saturating cast（NaN→0），Kotlin NaN.toInt() 亦係 0
  // JS Math.floor(NaN) 係 NaN —— 所以呢度要靠 coerceIn 清走
  var one = K.sampleIndices(40, 1);
  ok(one.length === 1 && one[0] === 0, 'max=1 → [0]（NaN 要清走）', JSON.stringify(one));

  // 逐位對齊：n / max 交叉 40 組，全部要同 Kotlin 一樣
  var ns = [1, 2, 3, 5, 7, 10, 13, 17, 40, 99];
  var maxes = [1, 2, 3, 12];
  var mismatch = 0;
  ns.forEach(function (n) {
    maxes.forEach(function (m) {
      var got = K.sampleIndices(n, m);
      // 用同一條公式獨立再算一次（Kotlin 寫法直譯）
      var want;
      if (n <= 0 || m <= 0) want = [];
      else if (n <= m) { want = []; for (var i = 0; i < n; i++) want.push(i); }
      else {
        want = [];
        var step = (n - 1) / (m - 1);
        for (var j = 0; j < m; j++) {
          var idx = Math.floor(j * step);
          if (!(idx >= 0)) idx = 0;
          if (idx > n - 1) idx = n - 1;
          if (want.indexOf(idx) < 0) want.push(idx);
        }
      }
      if (JSON.stringify(got) !== JSON.stringify(want)) mismatch++;
    });
  });
  ok(mismatch === 0, '40 組交叉全部一致', mismatch + ' 組唔同');

  // 頭尾一定要喺入面（呢個係取樣嘅核心語義）
  var s = K.sampleIndices(100, 10);
  ok(s[0] === 0, '第一個係 0');
  ok(s[s.length - 1] === 99, '最後一個係 n-1', JSON.stringify(s));

  // ---------------------------------------------------------------- 診斷

  console.log('診斷 diagnose');

  eq(K.diagnose(0, 0, 0), 0, '冇站 → 0 正常');
  eq(K.diagnose(10, 10, 0), 1, '全線冇班次 → 1');
  eq(K.diagnose(10, 11, 0), 1, 'noData > total → 都係 1');
  eq(K.diagnose(10, 3, 0), 2, '部分站冇資料 → 2');
  eq(K.diagnose(10, 0, 70), 4, '間隔 70 分 → 4');
  eq(K.diagnose(10, 0, 60), 4, '間隔 60 分 → 4（邊界）');
  eq(K.diagnose(10, 0, 59), 3, '間隔 59 分 → 3');
  eq(K.diagnose(10, 0, 30), 3, '間隔 30 分 → 3（邊界）');
  eq(K.diagnose(10, 0, 29), 0, '間隔 29 分 → 0');
  eq(K.diagnose(10, 0, 15), 0, '間隔 15 分 → 0');

  // 順序：全線冇嘢要贏過「部分冇資料」
  eq(K.diagnose(10, 10, 70), 1, '全線冇 ＋ 間隔大 → 1（全線冇優先）');
  eq(K.diagnose(10, 3, 70), 2, '部分冇 ＋ 間隔大 → 2（部分冇優先）');

  // ---------------------------------------------------------------- 路線號

  console.log('路線號 normalizeRoute');

  eq(K.normalizeRoute('  1a  '), '1A', '去空格 ＋ 大寫');
  eq(K.normalizeRoute('K51'), 'K51', '已經大寫 → 不變');
  eq(K.normalizeRoute(''), '', '空字串');
  eq(K.normalizeRoute(null), '', 'null → 空字串（唔好摔）');
  eq(K.normalizeRoute('n691'), 'N691', '小寫 n → N');

  // ---------------------------------------------------------------- ETA 合併

  console.log('ETA 合併 mergeEta');

  var rows = [
    K.encodeEta(15, '21:15', '6', '尖沙咀'),
    K.encodeEta(3, '21:03', '6', '尖沙咀'),
    K.encodeEta(null, '', '6', '尖沙咀'),
    K.encodeEta(3, '21:03', '6', '尖沙咀'),   // 重複，要被去
    K.encodeEta(8, '21:08', '1A', '尖沙咀')
  ];
  var merged = K.mergeEta(rows, 10);
  eq(merged.length, 4, '去重後 4 筆（原本 5 筆）');
  eq(merged[0].minutes, 3, '最快嗰班排第一');
  eq(merged[1].minutes, 8, '第二係 8 分');
  eq(merged[2].minutes, 15, '第三係 15 分');
  ok(merged[3].minutes === null, '冇資料排最後');
  eq(K.mergeEta(rows, 2).length, 2, 'cap=2 截到 2 筆');
  eq(K.mergeEta([], 10), null, '空 → null');
  eq(K.mergeEta(rows, 0), null, 'cap=0 → null');

  // ---------------------------------------------------------------- 班次表

  console.log('班次表 buildTimetable');

  // 兩個站，每站三班：12/15 分 → 27/30 分，間隔 15
  var cols = [
    K.encodeCol([{ minutes: 12, clock: '21:12' }, { minutes: 27, clock: '21:27' }]),
    K.encodeCol([{ minutes: 15, clock: '21:15' }, { minutes: 30, clock: '21:30' }])
  ];
  var tt = K.buildTimetable(cols, 6);
  ok(tt != null, '砌到班次表');
  eq(tt.trips.length, 2, '兩班車');
  eq(tt.trips[0].cells[0].minutes, 12, '第1班 第1站 12 分');
  eq(tt.trips[0].cells[1].minutes, 15, '第1班 第2站 15 分');
  eq(tt.trips[1].cells[0].minutes, 27, '第2班 第1站 27 分');
  eq(tt.trips[1].cells[1].minutes, 30, '第2班 第2站 30 分');
  eq(tt.headwayMin, 15, '班次間隔 15 分');

  eq(K.buildTimetable([], 6), null, '冇欄 → null');
  eq(K.buildTimetable(cols, 0), null, 'maxTrips=0 → null');

  // 冇資料嘅欄唔好污染間隔計算
  var colsN = [
    K.encodeCol([{ minutes: null, clock: '' }, { minutes: 27, clock: '21:27' }]),
    K.encodeCol([{ minutes: 15, clock: '21:15' }, { minutes: 30, clock: '21:30' }])
  ];
  var ttN = K.buildTimetable(colsN, 6);
  eq(ttN.headwayMin, 15, '第一格冇資料 → 用第二個站計間隔 15');

  // ---------------------------------------------------------------- 快取（speed.rs）

  console.log('TTL 快取 Memo');

  var m = new K.Memo(1000, 3);
  m.put('a', 1);
  eq(m.get('a'), 1, 'put 之後 get 到');
  eq(m.hits, 1, '命中計 1');
  eq(m.misses, 0, '未中 0');
  eq(m.get('zzz'), null, '冇嗰條 → null');
  eq(m.misses, 1, '未中計 1');

  // 過期
  var m2 = new K.Memo(10, 4);
  m2.put('k', 'v');
  m2.now = function () { return Date.now() + 1000; };  // 模擬過咗 1 秒
  eq(m2.get('k'), null, '過期 → null');
  eq(m2.peek('k'), 'v', 'peek 過期照出（stale-while-revalidate）');
  eq(m2.hits, 0, 'peek 唔計命中');

  // 踢走最舊寫入（唔係 LRU！）
  var m3 = new K.Memo(100000, 3);
  m3.put('k0', 0);
  m3.put('k1', 1);
  m3.put('k2', 2);
  m3.get('k0');            // 摸一摸 k0 —— 如果係 LRU 就唔會踢佢
  m3.put('k3', 3);         // 超容量 → 要踢「最舊寫入」即 k0
  eq(m3.get('k0'), null, '踢走最舊寫入 k0（摸過都照踢）');
  eq(m3.get('k1'), 1, 'k1 留低');
  eq(m3.get('k2'), 2, 'k2 留低');
  eq(m3.get('k3'), 3, 'k3 留低');
  eq(m3.size(), 3, '容量維持 3');

  // TTL 常量要同 Kotlin 對齊
  eq(K.TTL.ETA, 20000, 'ETA TTL 20 秒');
  eq(K.TTL.TIMETABLE, 90000, '班次表 TTL 90 秒');
  eq(K.TTL.NEARBY, 45000, '附近 TTL 45 秒');
  eq(K.TTL.ROUTE_STOPS, 600000, '路線車站 TTL 10 分鐘');

  // ---------------------------------------------------------------- 儲存（store.rs）

  console.log('KV 儲存 Store');

  var st = new K.Store('test_');
  st.put('fav', 'a', { x: 1 });
  eq(st.get('fav', 'a').x, 1, 'put 之後 get 到');
  eq(st.get('fav', 'b'), null, '冇嗰條 → null');
  eq(st.keys('fav').length, 1, '一個 key');
  st.remove('fav', 'a');
  eq(st.get('fav', 'a'), null, 'remove 之後冇咗');
  eq(st.stats().entries, 0, '清空後 0 條');

  // 命名空間之間要隔離
  st.put('ns1', 'k', 1);
  st.put('ns2', 'k', 2);
  eq(st.get('ns1', 'k'), 1, 'ns1 獨立');
  eq(st.get('ns2', 'k'), 2, 'ns2 獨立');
  eq(st.stats().namespaces, 2, '兩個命名空間');

  // ---------------------------------------------------------------- 距離

  console.log('距離 distanceM');

  var d = K.distanceM(22.3, 114.17, 22.31, 114.18);
  ok(d > 1000 && d < 2000, '約 1.5 公里（量級合理）', d.toFixed(0) + ' m');
  eq(Math.round(K.distanceM(22.3, 114.17, 22.3, 114.17)), 0, '同一點 → 0 米');

  // ---------------------------------------------------------------- 總結

  console.log('');
  console.log('通過 ' + pass + ' 項，失敗 ' + fail + ' 項');
  if (fail > 0) process.exit(1);
  console.log('全部通過 ✔');
})();
