//! # 加速層（TTL memo）—— 由 Rust 擁有
//!
//! 到呢一步，加速層唔再係「純 Kotlin」：TTL 判斷、踢走最舊、命中率統計
//! 同 payload 全部放喺 native heap，JVM 側淨係喺**要顯示嗰一刻**先砌物件。
//!
//! ## 點解要搬
//!
//! `Memo<T>` 以前喺 Kotlin 度持有**成個物件圖**：
//!
//! * `eta` 係 `List<EtaEntry>` —— 每次 `get` 攞到嘅係同一批物件
//! * `nearby` 係 `List<NearbyStop>` —— 入面仲包住 `StopDef` 同 `List<String>`
//!
//! 即係快取入面嘅嘢一樣會畀 GC 掃到、一樣佔住 heap。搬落 Rust 之後，
//! payload 係一段**唔透明嘅字串**，JVM 唔使為佢起任何物件。
//!
//! ## 點解用「唔透明字串」而唔係 typed struct
//!
//! Rust 唔使知道 ETA／班次表／附近車站嘅形狀 —— 佢淨係負責
//! 「呢條 key 幾時入嚟、仲新唔新、要唔要踢走」。形狀由 Kotlin 側
//! 自己 encode／decode，所以加一種新嘅快取唔使改 Rust。
//!
//! 解碼成本亦好低：ETA 最多廿幾筆，附近車站最多 80 筆，砌字串係微秒級。
//!
//! ## TTL 點解係呢啲數
//!
//! | ns | TTL | 點解 |
//! |---|---|---|
//! | ETA | 20 秒 | 官方 ETA 本身就係每 20–30 秒先變一次，再密冇意義 |
//! | 班次表 | 90 秒 | 要打 12 個站，成本最高；切分頁返嚟應該即出 |
//! | 附近車站 | 45 秒 | 自動定位每 60 秒重整一次，45 秒唔會用到過期嘢 |
//! | 路線車站 | 10 分鐘 | 路線嘅站序幾乎唔會變 |
//!
//! ## panic
//!
//! 同 `kernel.rs` 一樣：panic 跨 FFI 係 UB，所以一律唔用 `unwrap()`／`expect()`／`[]`，
//! `Mutex` 中毒亦用 `into_inner()` 照行。

use std::collections::HashMap;
use std::sync::{Mutex, MutexGuard, OnceLock};

// ---------------------------------------------------------------- namespace

/// ETA（車站頁）
pub const NS_ETA: i32 = 0;
/// 班次表
pub const NS_TIMETABLE: i32 = 1;
/// 附近車站
pub const NS_NEARBY: i32 = 2;
/// 路線嘅車站（站序）
pub const NS_ROUTE_STOPS: i32 = 3;

/// namespace 數量
pub const NS_COUNT: usize = 4;

/// 每個 namespace 嘅 TTL（毫秒）
const TTL_MS: [i64; NS_COUNT] = [20_000, 90_000, 45_000, 600_000];

/// 每個 namespace 嘅容量（超過就踢最舊）
const MAX_ENTRIES: [usize; NS_COUNT] = [128, 32, 16, 64];

// ---------------------------------------------------------------- 儲存

struct Slot {
    payload: String,
    at_ms: i64,
}

struct Bucket {
    map: HashMap<String, Slot>,
    hits: i64,
    misses: i64,
}

impl Bucket {
    fn new() -> Self {
        Bucket {
            map: HashMap::new(),
            hits: 0,
            misses: 0,
        }
    }
}

struct Store {
    buckets: Vec<Bucket>,
}

impl Store {
    fn new() -> Self {
        Store {
            buckets: (0..NS_COUNT).map(|_| Bucket::new()).collect(),
        }
    }
}

/// 全域唯一嘅加速層。`OnceLock` 唔使加依賴（`lazy_static!` 要額外 crate）。
fn store() -> &'static Mutex<Store> {
    static S: OnceLock<Mutex<Store>> = OnceLock::new();
    S.get_or_init(|| Mutex::new(Store::new()))
}

/// 攞 lock；中毒（`poisoned`）都照用 —— 快取壞咗唔值得令 App 崩
fn lock() -> MutexGuard<'static, Store> {
    match store().lock() {
        Ok(g) => g,
        Err(p) => p.into_inner(),
    }
}

fn idx(ns: i32) -> Option<usize> {
    if ns >= 0 && (ns as usize) < NS_COUNT {
        Some(ns as usize)
    } else {
        None
    }
}

// ---------------------------------------------------------------- API

/// 寫入。`now_ms` 由 Kotlin 側傳入（`System.currentTimeMillis()`），
/// 唔好喺 Rust 度自己攞時間 —— 兩邊時鐘要一致，否則 TTL 會錯。
pub fn put(ns: i32, key: &str, payload: &str, now_ms: i64) -> bool {
    let i = match idx(ns) {
        Some(v) => v,
        None => return false,
    };
    if key.is_empty() || payload.is_empty() {
        return false;
    }
    let mut g = lock();
    let b = match g.buckets.get_mut(i) {
        Some(v) => v,
        None => return false,
    };
    b.map.insert(
        key.to_string(),
        Slot {
            payload: payload.to_string(),
            at_ms: now_ms,
        },
    );

    // 超過容量就踢最舊嗰個（容量最多 128，線性掃唔會慢）
    let max = MAX_ENTRIES[i];
    while b.map.len() > max {
        let oldest = match b.map.iter().min_by_key(|(_, s)| s.at_ms) {
            Some((k, _)) => k.clone(),
            None => break,
        };
        b.map.remove(&oldest);
    }
    true
}

/// 淨係返**未過期**嘅；過期當 miss（並計數）
pub fn get(ns: i32, key: &str, now_ms: i64) -> Option<String> {
    let i = match idx(ns) {
        Some(v) => v,
        None => return None,
    };
    let mut g = lock();
    let b = match g.buckets.get_mut(i) {
        Some(v) => v,
        None => return None,
    };

    // 先決定結果再計數：否則 `map.get()` 嘅借用會同 `hits += 1` 撞車
    let hit = match b.map.get(key) {
        Some(s) => {
            let fresh = now_ms - s.at_ms <= TTL_MS[i];
            if fresh {
                Some(s.payload.clone())
            } else {
                None
            }
        }
        None => None,
    };
    if hit.is_some() {
        b.hits += 1;
    } else {
        b.misses += 1;
    }
    hit
}

/// 唔理過期照返（用嚟「先出舊嘅、再背景刷新」）。**唔計數** ——
/// 佢唔係一次命中，只係借用舊資料頂住畫面。
pub fn peek(ns: i32, key: &str) -> Option<String> {
    let i = match idx(ns) {
        Some(v) => v,
        None => return None,
    };
    let g = lock();
    match g.buckets.get(i).and_then(|b| b.map.get(key)) {
        Some(s) => Some(s.payload.clone()),
        None => None,
    }
}

/// 呢條 key 嘅資料係幾時抓落嚟（冇就 0）
pub fn at(ns: i32, key: &str) -> i64 {
    let i = match idx(ns) {
        Some(v) => v,
        None => return 0,
    };
    let g = lock();
    match g.buckets.get(i).and_then(|b| b.map.get(key)) {
        Some(s) => s.at_ms,
        None => 0,
    }
}

pub fn remove(ns: i32, key: &str) {
    let i = match idx(ns) {
        Some(v) => v,
        None => return,
    };
    let mut g = lock();
    if let Some(b) = g.buckets.get_mut(i) {
        b.map.remove(key);
    }
}

/// 清一個 namespace（例如改語言／重建索引）
pub fn clear(ns: i32) {
    let i = match idx(ns) {
        Some(v) => v,
        None => return,
    };
    let mut g = lock();
    if let Some(b) = g.buckets.get_mut(i) {
        b.map.clear();
    }
}

pub fn clear_all() {
    let mut g = lock();
    for b in g.buckets.iter_mut() {
        b.map.clear();
    }
}

pub fn hits(ns: i32) -> i64 {
    let i = match idx(ns) {
        Some(v) => v,
        None => return 0,
    };
    let g = lock();
    match g.buckets.get(i) {
        Some(b) => b.hits,
        None => 0,
    }
}

pub fn misses(ns: i32) -> i64 {
    let i = match idx(ns) {
        Some(v) => v,
        None => return 0,
    };
    let g = lock();
    match g.buckets.get(i) {
        Some(b) => b.misses,
        None => 0,
    }
}
