//! # HKETA 領域核心（kernel）
//!
//! 到呢一步，Rust 唔再淨係「索引嘅儲存層」，而係**實際計數嘅核心**：
//!
//! | 以前（Kotlin） | 而家（Rust kernel） |
//! |---|---|
//! | 取樣 12 個站 | `sample_indices` |
//! | 由各站 ETA 砌班次表 | `build_timetable` |
//! | 班次間隔 | `headway` |
//! | 路線狀況診斷 | `diagnose` |
//! | ETA 合併去重排序 | `merge_eta` |
//! | 路線號正規化 | `normalize_route` |
//!
//! ## 點解要搬呢啲
//!
//! 呢幾樣嘢共同嘅特徵係：**輸入大、輸出細、邏輯純**。
//! 班次表要食 12 個站 × 每站 6 班 = 72 筆，淨係輸出最多 6 班；
//! 取樣要食成條路線嘅站序。呢類「食大吐細」嘅純邏輯最啱放喺
//! native 度做 —— JVM 側連中轉物件都唔使起。
//!
//! ## 跨邊界格式
//!
//! 跟 `lib.rs` 一樣**唔用 JSON**。
//!
//! ```text
//! SEP  = '\u{1F}'  unit separator   字段之間
//! SEP2 = '\u{1E}'  record separator 字段內部（pair 之間）
//! PAIR = ';'                        同一個欄入面嘅多個 pair
//! ```
//!
//! 分鐘數用 `Option<i32>`，跨邊界編碼做字串 `"N"` 代表「冇資料」
//! （官方有時會出「即將到站」而冇分鐘數）。
//!
//! ## panic
//!
//! panic 跨 FFI 邊界係 UB，所以呢個檔案一律唔用 `unwrap()`／`expect()`／`[]`
//! 做索引，全部用 `.get()` / `unwrap_or_default()`。任何錯誤都安靜返回空值 ——
//! Kotlin 側有**逐行對應嘅純 Kotlin 回退**，Rust 唔喺度都計到一樣嘅結果。

/// 字段分隔（unit separator）
pub const SEP: char = '\u{1F}';
/// 字段內部嘅 pair 分隔（record separator）
pub const SEP2: char = '\u{1E}';
/// 同一欄入面多個 pair 嘅分隔
pub const PAIR: char = ';';
/// 分鐘數「冇資料」嘅編碼
pub const NO_MIN: &str = "N";

/// kernel 版本（Kotlin 側用嚟確認 .so 係咪新版）
pub const KERNEL_VERSION: i32 = 1;

// ---------------------------------------------------------------- 取樣

/// 由 `n` 個站入面取樣最多 `max` 個：頭、尾 ＋ 均勻中間。
///
/// 同 Kotlin 版嘅算法**逐位一樣**（`(i * step).toInt()` 係截斷唔係四捨五入），
/// 所以兩邊結果必定相同 —— 呢個係「回退要一致」嘅前提。
pub fn sample_indices(n: usize, max: usize) -> Vec<usize> {
    if n == 0 || max == 0 {
        return Vec::new();
    }
    if n <= max {
        return (0..n).collect();
    }
    let step = (n - 1) as f64 / (max - 1) as f64;
    let mut out: Vec<usize> = Vec::with_capacity(max);
    for i in 0..max {
        let raw = (i as f64 * step) as i64;
        let idx = if raw < 0 {
            0usize
        } else if (raw as usize) > n - 1 {
            n - 1
        } else {
            raw as usize
        };
        if !out.contains(&idx) {
            out.push(idx);
        }
    }
    out
}

// ---------------------------------------------------------------- 班次表

/// 一格：某班車喺某個站嘅預計
#[derive(Clone)]
pub struct Cell {
    pub minutes: Option<i32>,
    pub clock: String,
}

impl Cell {
    pub fn empty() -> Self {
        Cell {
            minutes: None,
            clock: String::new(),
        }
    }
}

/// 解碼一個站嘅 ETA 欄：`"12\u{1E}21:10;15\u{1E}21:15"`
pub fn parse_col(s: &str) -> Vec<Cell> {
    let mut out: Vec<Cell> = Vec::new();
    if s.is_empty() {
        return out;
    }
    for pair in s.split(PAIR) {
        let mut it = pair.split(SEP2);
        let m = it.next().unwrap_or("");
        let c = it.next().unwrap_or("");
        let minutes = if m == NO_MIN || m.is_empty() {
            None
        } else {
            m.parse::<i32>().ok()
        };
        out.push(Cell {
            minutes,
            clock: c.to_string(),
        });
    }
    out
}

/// 編碼一格
pub fn encode_cell(c: &Cell) -> String {
    let m = match c.minutes {
        Some(v) => v.to_string(),
        None => NO_MIN.to_string(),
    };
    let mut s = String::new();
    s.push_str(&m);
    s.push(SEP2);
    s.push_str(&c.clock);
    s
}

/// 一班車
pub struct Trip {
    pub seq: u32,
    pub cells: Vec<Cell>,
}

/// 由「每站一列 ETA」砌班次表。
///
/// 官方 ETA 本身已按到站先後排好，所以**第 N 項就係第 N 班車**。
/// 呢個係真實資料，唔係估算。
///
/// 返回 `(trips, headway)`。`headway` 係第一個站相鄰兩班嘅分鐘差，
/// 淨係接受 1–180 分鐘（濾走異常值）。
pub fn build_timetable(cols: &[String], max_trips: usize) -> (Vec<Trip>, Option<i32>) {
    let parsed: Vec<Vec<Cell>> = cols.iter().map(|c| parse_col(c)).collect();

    let mut trip_count = 0usize;
    for p in parsed.iter() {
        if p.len() > trip_count {
            trip_count = p.len();
        }
    }
    let n = if trip_count < max_trips {
        trip_count
    } else {
        max_trips
    };

    let mut trips: Vec<Trip> = Vec::with_capacity(n);
    for i in 0..n {
        let mut cells: Vec<Cell> = Vec::with_capacity(parsed.len());
        for p in parsed.iter() {
            match p.get(i) {
                Some(c) => cells.push(c.clone()),
                None => cells.push(Cell::empty()),
            }
        }
        trips.push(Trip {
            seq: (i + 1) as u32,
            cells,
        });
    }

    let headway = parsed
        .first()
        .and_then(|p| {
            if p.len() >= 2 {
                match (p[0].minutes, p[1].minutes) {
                    (Some(a), Some(b)) => {
                        let d = b - a;
                        if d >= 1 && d <= 180 {
                            Some(d)
                        } else {
                            None
                        }
                    }
                    _ => None,
                }
            } else {
                None
            }
        });

    (trips, headway)
}

/// 把班次表編碼做跨邊界嘅行格式
///
/// ```text
/// row 0:  "H\u{1F}<headway or N>"
/// row i:  "T\u{1F}<seq>\u{1F}<cells>"
/// ```
pub fn encode_timetable(trips: &[Trip], headway: Option<i32>) -> Vec<String> {
    let mut out: Vec<String> = Vec::with_capacity(trips.len() + 1);
    let mut h = String::from("H");
    h.push(SEP);
    match headway {
        Some(v) => h.push_str(&v.to_string()),
        None => h.push_str(NO_MIN),
    };
    out.push(h);
    for t in trips.iter() {
        let mut row = String::from("T");
        row.push(SEP);
        row.push_str(&t.seq.to_string());
        row.push(SEP);
        let cells: Vec<String> = t.cells.iter().map(encode_cell).collect();
        row.push_str(&cells.join(&PAIR.to_string()));
        out.push(row);
    }
    out
}

// ---------------------------------------------------------------- 路線狀況

/// 路線狀況診斷（對應 Kotlin `buildRouteConditionNotices`）
///
/// ```text
/// 0 = 正常
/// 1 = 全線冇班次（可能暫停／已過尾班車）
/// 2 = 部分車站冇班次資料
/// 3 = 班次間隔偏大（>= 30 分鐘）
/// 4 = 班次極疏（>= 60 分鐘）
/// ```
///
/// 順序係刻意嘅：全線冇嘢最嚴重，其次「有站冇資料」（對用戶最直接），
/// 最後先係間隔偏大（可能只係正常嘅疏班次路線）。
pub fn diagnose(total: usize, no_data: usize, max_gap: i32) -> i32 {
    if total == 0 {
        return 0;
    }
    if no_data >= total {
        return 1;
    }
    if no_data > 0 {
        return 2;
    }
    if max_gap >= 60 {
        return 4;
    }
    if max_gap >= 30 {
        return 3;
    }
    0
}

// ---------------------------------------------------------------- ETA 合併

/// 一筆 ETA（跨邊界用）
#[derive(Clone)]
pub struct EtaRow {
    pub minutes: Option<i32>,
    pub clock: String,
    pub route: String,
    pub dest: String,
}

/// 解碼一筆 ETA：`"minutes\u{1E}clock\u{1E}route\u{1E}dest"`
pub fn parse_eta(s: &str) -> Option<EtaRow> {
    let parts: Vec<&str> = s.split(SEP2).collect();
    if parts.len() < 4 {
        return None;
    }
    let m = parts[0];
    let minutes = if m == NO_MIN || m.is_empty() {
        None
    } else {
        m.parse::<i32>().ok()
    };
    Some(EtaRow {
        minutes,
        clock: parts[1].to_string(),
        route: parts[2].to_string(),
        dest: parts[3].to_string(),
    })
}

pub fn encode_eta(r: &EtaRow) -> String {
    let m = match r.minutes {
        Some(v) => v.to_string(),
        None => NO_MIN.to_string(),
    };
    let mut s = String::new();
    s.push_str(&m);
    s.push(SEP2);
    s.push_str(&r.clock);
    s.push(SEP2);
    s.push_str(&r.route);
    s.push(SEP2);
    s.push_str(&r.dest);
    s
}

/// 合併去重 ＋ 排序（分鐘數昇冪，冇資料排最後）
pub fn merge_eta(rows: &[String], cap: usize) -> Vec<String> {
    let mut parsed: Vec<EtaRow> = Vec::new();
    let mut seen: Vec<String> = Vec::new();
    for s in rows.iter() {
        match parse_eta(s) {
            Some(r) => {
                let mut key = String::new();
                key.push_str(&r.route);
                key.push(SEP2);
                key.push_str(&r.dest);
                key.push(SEP2);
                key.push_str(&r.clock);
                key.push(SEP2);
                match r.minutes {
                    Some(v) => key.push_str(&v.to_string()),
                    None => key.push_str(NO_MIN),
                };
                if seen.contains(&key) {
                    continue;
                }
                seen.push(key);
                parsed.push(r);
            }
            None => {}
        }
    }

    // 穩定排序：分鐘數昇冪，None 排最後
    parsed.sort_by(|a, b| match (a.minutes, b.minutes) {
        (Some(x), Some(y)) => x.cmp(&y),
        (Some(_), None) => std::cmp::Ordering::Less,
        (None, Some(_)) => std::cmp::Ordering::Greater,
        (None, None) => std::cmp::Ordering::Equal,
    });

    let n = if parsed.len() < cap {
        parsed.len()
    } else {
        cap
    };
    parsed[0..n].iter().map(encode_eta).collect()
}

// ---------------------------------------------------------------- 路線號

/// 路線號正規化：去前後空格 ＋ 轉大寫
pub fn normalize_route(q: &str) -> String {
    q.trim().to_uppercase()
}

// ---------------------------------------------------------------- 自測

/// 純邏輯自測（Kotlin 側冇用到，畀 `cargo test` 用）
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sampling_matches_kotlin() {
        // n=40, max=12 → step = 39/11 = 3.545 → 0,3,7,10,14,17,21,24,28,31,35,39
        assert_eq!(sample_indices(40, 12)[0], 0);
        assert_eq!(*sample_indices(40, 12).last().unwrap_or(&0), 39);
        assert_eq!(sample_indices(5, 12), vec![0, 1, 2, 3, 4]);
        assert_eq!(sample_indices(0, 12).len(), 0);
    }

    #[test]
    fn timetable_picks_nth() {
        let cols = vec![
            format!("12{}21:10;27{}21:25", SEP2, SEP2),
            format!("15{}21:13;30{}21:28", SEP2, SEP2),
        ];
        let (trips, hw) = build_timetable(&cols, 6);
        assert_eq!(trips.len(), 2);
        assert_eq!(trips[0].cells[0].minutes, Some(12));
        assert_eq!(trips[1].cells[1].minutes, Some(30));
        assert_eq!(hw, Some(15));
    }

    #[test]
    fn headway_rejects_outliers() {
        let cols = vec![format!("0{}a;500{}b", SEP2, SEP2)];
        let (_, hw) = build_timetable(&cols, 6);
        assert_eq!(hw, None);
    }

    #[test]
    fn diagnose_order() {
        assert_eq!(diagnose(8, 8, 0), 1);
        assert_eq!(diagnose(8, 2, 0), 2);
        assert_eq!(diagnose(8, 0, 70), 4);
        assert_eq!(diagnose(8, 0, 40), 3);
        assert_eq!(diagnose(8, 0, 15), 0);
    }
}
