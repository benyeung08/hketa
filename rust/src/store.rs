//! # 通用持久化 KV 儲存 —— 成個 APK 嘅「記憶體與儲存」
//!
//! 呢個模組係 HKETA 內核嘅**第四件嘢**：之前內核淨係管「索引嘅記憶體與儲存」，
//! 而 App 其餘嘅儲存（收藏、ETA 快取、班次表快取、輕鐵特別班次、Leaflet 資源包）
//! 全部各寫各嘅 —— `SharedPreferences`、JSON 檔、`File.writeText()`，五種寫法。
//!
//! 而家統一由 Rust 擁有：
//!
//! | 之前 | 而家 |
//! |---|---|
//! | `SharedPreferences` 存一條 JSON 大字串（收藏） | `store` 命名空間 `fav` |
//! | 一個 JSON 檔（輕鐵特別班次） | 命名空間 `lrsp` |
//! | 一個目錄一堆 `.json`（ETA 快取） | 命名空間 `eta` |
//! | 一個目錄一堆 `.json`（班次表快取） | 命名空間 `tt` |
//! | `filesDir/hketa_assets/*.css|js` | 命名空間 `ast` |
//!
//! ## 兩層：記憶體 ＋ 磁碟
//!
//! * **記憶體**：`HashMap<命名空間, HashMap<key, Entry>>`，全部住喺 **native heap**，
//!   Kotlin 側一個位元組都唔使揸住。呢個就係「Rust 擁有記憶體」嘅意思。
//! * **磁碟**：每個命名空間一個 `.s` 檔，行格式
//!   `key \u{1F} at_ms \u{1F} escaped_value`，**原子寫**（`.s.tmp` 再 rename）。
//!   寫到一半崩潰都唔會損壞舊檔。
//!
//! ## 逃逸
//!
//! value 入面可能有換行（Leaflet 嘅 CSS／JS 一定有），所以寫檔前做逃逸：
//! `\` → `\\`、`\n` → `\n`、`\r` → `\r`、`\u{1F}` → `\u`。
//! 讀返嗰陣反向還原。**淨係呢四個字符**，所以還原唔會有歧義。
//!
//! ## 上限
//!
//! 每個命名空間最多 [MAX_PER_NS] 條，超咗就踢**最舊寫入**嗰條（`at_ms` 最小）。
//! 呢個同 `speed.rs` 嘅約定一致 —— Kotlin 回退版都要踢最舊，唔係 LRU，
//! 否則「有冇 `.so`」會改變踢走邊個（最難 debug 嗰類 bug）。
//!
//! ## ⚠️ 安全
//!
//! * panic 跨 FFI 係 UB，所以一律唔用 `unwrap()`／`expect()`／`[]`
//! * `Mutex` 中毒（poisoned）都只係返 `false`／空值 —— Kotlin 側有回退
//! * 所有 `fs` 操作失敗都靜默返 `false`，Kotlin 側行返原本路徑

use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::{Mutex, OnceLock};

/// 欄分隔（unit separator）
const SEP: char = '\u{1F}';

/// 每個命名空間嘅條目上限；超咗踢最舊寫入嗰條
const MAX_PER_NS: usize = 4096;

// ---------------------------------------------------------------- 資料結構

/// 一條記錄：值 ＋ 寫入時間（epoch ms）
pub struct Entry {
    pub value: String,
    pub at_ms: i64,
}

/// 一個命名空間：一張表 ＋ 係咪有未落碟嘅改動
pub struct Namespace {
    pub map: HashMap<String, Entry>,
    pub dirty: bool,
}

/// 成個 store：一個根目錄 ＋ 若干命名空間
pub struct Store {
    pub root: PathBuf,
    pub ns: HashMap<String, Namespace>,
    pub puts: u64,
    pub gets: u64,
    pub hits: u64,
    pub misses: u64,
    /// 已知嘅命名空間（初始化時由磁碟掃返嚟，等 `namespaces()` 可以列）
    pub known: Vec<String>,
}

/// 全域唯一嘅 store。`OnceLock<Mutex<Option<Store>>>` —— 唔用 `static mut`，
/// 所以**完全冇 unsafe**；`Mutex` 中毒只係返 `None`，唔會 panic。
fn global() -> &'static Mutex<Option<Store>> {
    static S: OnceLock<Mutex<Option<Store>>> = OnceLock::new();
    S.get_or_init(|| Mutex::new(None))
}

// ---------------------------------------------------------------- 逃逸

/// 寫檔前逃逸：`\` `\n` `\r` `\u{1F}` 四個字符
pub fn esc(s: &str) -> String {
    let mut out = String::with_capacity(s.len() + 8);
    for c in s.chars() {
        match c {
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\u{1F}' => out.push_str("\\u"),
            _ => out.push(c),
        }
    }
    out
}

/// 讀檔後還原。[esc] 嘅逆運算。
pub fn unesc(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut it = s.chars();
    while let Some(c) = it.next() {
        if c != '\\' {
            out.push(c);
            continue;
        }
        match it.next() {
            Some('\\') => out.push('\\'),
            Some('n') => out.push('\n'),
            Some('r') => out.push('\r'),
            Some('u') => out.push('\u{1F}'),
            Some(other) => {
                out.push('\\');
                out.push(other);
            }
            None => out.push('\\'),
        }
    }
    out
}

// ---------------------------------------------------------------- 讀寫檔

fn path_of(root: &Path, ns: &str) -> PathBuf {
    root.join(format!("{}.s", ns))
}

fn load_ns(root: &Path, ns: &str) -> Namespace {
    let mut map: HashMap<String, Entry> = HashMap::new();
    if let Ok(raw) = fs::read_to_string(path_of(root, ns)) {
        for line in raw.split('\n') {
            if line.is_empty() {
                continue;
            }
            let mut parts = line.splitn(3, SEP);
            let k = match parts.next() {
                Some(v) => v,
                None => continue,
            };
            if k.is_empty() {
                continue;
            }
            let at_ms = match parts.next() {
                Some(v) => v.parse::<i64>().unwrap_or(0),
                None => 0,
            };
            let value = match parts.next() {
                Some(v) => unesc(v),
                None => String::new(),
            };
            map.insert(k.to_string(), Entry { value, at_ms });
        }
    }
    Namespace { map, dirty: false }
}

/// 原子寫：`.s.tmp` 寫完先 rename。
fn save_ns(root: &Path, ns: &str, map: &HashMap<String, Entry>) -> bool {
    // 排序等輸出穩定（排唔排序功能都一樣，但 diff 同 debug 會易睇好多）
    let mut keys: Vec<&String> = map.keys().collect();
    keys.sort();

    let mut buf = String::new();
    for k in keys {
        let e = match map.get(k) {
            Some(v) => v,
            None => continue,
        };
        buf.push_str(k);
        buf.push(SEP);
        buf.push_str(&e.at_ms.to_string());
        buf.push(SEP);
        buf.push_str(&esc(&e.value));
        buf.push('\n');
    }

    let final_p = path_of(root, ns);
    let tmp_p = root.join(format!("{}.s.tmp", ns));
    if fs::write(&tmp_p, buf.as_bytes()).is_err() {
        return false;
    }
    if fs::rename(&tmp_p, &final_p).is_err() {
        let _ = fs::remove_file(&tmp_p);
        return false;
    }
    true
}

/// 踢走最舊寫入嗰條（[MAX_PER_NS] 用）
fn evict_oldest(n: &mut Namespace) {
    if n.map.len() <= MAX_PER_NS {
        return;
    }
    let mut oldest_key: Option<String> = None;
    let mut oldest_at: i64 = i64::MAX;
    for (k, e) in n.map.iter() {
        if e.at_ms < oldest_at {
            oldest_at = e.at_ms;
            oldest_key = Some(k.clone());
        }
    }
    if let Some(k) = oldest_key {
        n.map.remove(&k);
        n.dirty = true;
    }
}

// ---------------------------------------------------------------- Store

impl Store {
    /// 拎一個命名空間；未載入過就由磁碟讀返嚟
    fn ns_mut(&mut self, ns: &str) -> Option<&mut Namespace> {
        if !self.ns.contains_key(ns) {
            let n = load_ns(&self.root, ns);
            self.ns.insert(ns.to_string(), n);
        }
        self.ns.get_mut(ns)
    }
}

// ---------------------------------------------------------------- 對外 API

/// 初始化：建目錄 ＋ 掃描已存在嘅 `.s` 檔，記低命名空間名單。
/// **內容係懶載入**（第一次 `get`/`put` 嗰個 ns 先讀），所以開 App 唔會一口氣讀晒。
pub fn init(root: &str) -> bool {
    if root.is_empty() {
        return false;
    }
    let root = PathBuf::from(root);
    if fs::create_dir_all(&root).is_err() {
        return false;
    }

    let mut known: Vec<String> = Vec::new();
    if let Ok(rd) = fs::read_dir(&root) {
        for e in rd.flatten() {
            let p = e.path();
            if !p.is_file() {
                continue;
            }
            let name = match p.file_name().and_then(|s| s.to_str()) {
                Some(v) => v.to_string(),
                None => continue,
            };
            // 用 trim_end_matches 而唔係切 byte —— 切 byte 遇到非 ASCII 會
            // 切落字符中間直接 panic，而 panic 跨 FFI 係 UB。
            if name.ends_with(".s") {
                let ns = name.trim_end_matches(".s");
                if !ns.is_empty() {
                    known.push(ns.to_string());
                }
            }
        }
    }
    known.sort();

    let st = Store {
        root,
        ns: HashMap::new(),
        puts: 0,
        gets: 0,
        hits: 0,
        misses: 0,
        known,
    };

    match global().lock() {
        Ok(mut g) => {
            *g = Some(st);
            true
        }
        Err(_) => false,
    }
}

/// 寫一條。會標 dirty（落碟由 [flush] 做，唔會每次 put 都寫檔）。
pub fn put(ns: &str, key: &str, value: &str, now_ms: i64) -> bool {
    if ns.is_empty() || key.is_empty() {
        return false;
    }
    match global().lock() {
        Ok(mut g) => match g.as_mut() {
            Some(st) => {
                st.puts += 1;
                match st.ns_mut(ns) {
                    Some(n) => {
                        n.map.insert(
                            key.to_string(),
                            Entry {
                                value: value.to_string(),
                                at_ms: now_ms,
                            },
                        );
                        n.dirty = true;
                        evict_oldest(n);
                        true
                    }
                    None => false,
                }
            }
            None => false,
        },
        Err(_) => false,
    }
}

/// 讀一條。冇／未初始化就返 `None`（Kotlin 側會回退）。
pub fn get(ns: &str, key: &str) -> Option<String> {
    if ns.is_empty() || key.is_empty() {
        return None;
    }
    match global().lock() {
        Ok(mut g) => match g.as_mut() {
            Some(st) => {
                // 先拎結果（owned），等 ns_mut 嘅借用結束咗先改計數 ——
                // 否則 `st` 同時被可變借兩次，borrow checker 會鬧。
                let r = match st.ns_mut(ns) {
                    Some(n) => match n.map.get(key) {
                        Some(e) => Some(e.value.clone()),
                        None => None,
                    },
                    None => None,
                };
                st.gets += 1;
                if r.is_some() {
                    st.hits += 1;
                } else {
                    st.misses += 1;
                }
                r
            }
            None => None,
        },
        Err(_) => None,
    }
}

/// 寫入時間（epoch ms）；冇就返 0
pub fn at(ns: &str, key: &str) -> i64 {
    if ns.is_empty() || key.is_empty() {
        return 0;
    }
    match global().lock() {
        Ok(mut g) => match g.as_mut() {
            Some(st) => match st.ns_mut(ns) {
                Some(n) => match n.map.get(key) {
                    Some(e) => e.at_ms,
                    None => 0,
                },
                None => 0,
            },
            None => 0,
        },
        Err(_) => 0,
    }
}

/// 刪一條
pub fn del(ns: &str, key: &str) -> bool {
    if ns.is_empty() || key.is_empty() {
        return false;
    }
    match global().lock() {
        Ok(mut g) => match g.as_mut() {
            Some(st) => match st.ns_mut(ns) {
                Some(n) => {
                    let removed = n.map.remove(key).is_some();
                    if removed {
                        n.dirty = true;
                    }
                    removed
                }
                None => false,
            },
            None => false,
        },
        Err(_) => false,
    }
}

/// 一個命名空間嘅全部 key（排序後）
pub fn keys(ns: &str) -> Vec<String> {
    if ns.is_empty() {
        return Vec::new();
    }
    match global().lock() {
        Ok(mut g) => match g.as_mut() {
            Some(st) => match st.ns_mut(ns) {
                Some(n) => {
                    let mut v: Vec<String> = n.map.keys().cloned().collect();
                    v.sort();
                    v
                }
                None => Vec::new(),
            },
            None => Vec::new(),
        },
        Err(_) => Vec::new(),
    }
}

/// 清空一個命名空間（記憶體 ＋ 磁碟）
pub fn clear(ns: &str) -> bool {
    if ns.is_empty() {
        return false;
    }
    match global().lock() {
        Ok(mut g) => match g.as_mut() {
            Some(st) => {
                let root = st.root.clone();
                let done = match st.ns_mut(ns) {
                    Some(n) => {
                        n.map.clear();
                        n.dirty = true;
                        true
                    }
                    None => false,
                };
                if done {
                    let _ = fs::remove_file(path_of(&root, ns));
                }
                done
            }
            None => false,
        },
        Err(_) => false,
    }
}

/// 條目數
pub fn count(ns: &str) -> i64 {
    match global().lock() {
        Ok(mut g) => match g.as_mut() {
            Some(st) => match st.ns_mut(ns) {
                Some(n) => n.map.len() as i64,
                None => 0,
            },
            None => 0,
        },
        Err(_) => 0,
    }
}

/// value 嘅總位元組數（唔計 key 同分隔符）
pub fn bytes(ns: &str) -> i64 {
    match global().lock() {
        Ok(mut g) => match g.as_mut() {
            Some(st) => match st.ns_mut(ns) {
                Some(n) => {
                    let mut total: i64 = 0;
                    for e in n.map.values() {
                        total += e.value.len() as i64;
                    }
                    total
                }
                None => 0,
            },
            None => 0,
        },
        Err(_) => 0,
    }
}

/// 把所有 dirty 嘅命名空間落碟。App 退到背景／儲存收藏呢啲關鍵時機會 call。
pub fn flush() -> bool {
    match global().lock() {
        Ok(mut g) => match g.as_mut() {
            Some(st) => {
                let root = st.root.clone();
                let names: Vec<String> = st.ns.keys().cloned().collect();
                let mut ok = true;
                for ns in names {
                    let dirty = match st.ns.get(&ns) {
                        Some(n) => n.dirty,
                        None => false,
                    };
                    if !dirty {
                        continue;
                    }
                    let written = match st.ns.get(&ns) {
                        Some(n) => save_ns(&root, &ns, &n.map),
                        None => false,
                    };
                    if written {
                        if let Some(n) = st.ns.get_mut(&ns) {
                            n.dirty = false;
                        }
                        if !st.known.contains(&ns) {
                            st.known.push(ns.clone());
                        }
                    } else {
                        ok = false;
                    }
                }
                ok
            }
            None => false,
        },
        Err(_) => false,
    }
}

/// 已知嘅命名空間名單（初始化時掃磁碟 ＋ 曾經 flush 過嘅）
pub fn namespaces() -> Vec<String> {
    match global().lock() {
        Ok(mut g) => match g.as_mut() {
            Some(st) => st.known.clone(),
            None => Vec::new(),
        },
        Err(_) => Vec::new(),
    }
}

/// 統計（設定頁顯示）：`ns=.. keys=.. bytes=.. hits=.. misses=..`
///
/// **淨係數已載入嘅命名空間** —— 未載入嘅唔會為咗統計而讀成個檔。
/// 另外呢度**唔會 call [count]／[bytes]**（佢哋會再 lock 一次），
/// 因為 `std::sync::Mutex` 唔係可重入，重入會直接 deadlock。
pub fn stats() -> String {
    match global().lock() {
        Ok(mut g) => match g.as_mut() {
            Some(st) => {
                let mut keys: i64 = 0;
                let mut bytes: i64 = 0;
                for n in st.ns.values() {
                    keys += n.map.len() as i64;
                    for e in n.map.values() {
                        bytes += e.value.len() as i64;
                    }
                }
                format!(
                    "ns={} keys={} bytes={} hits={} misses={}",
                    st.known.len(),
                    keys,
                    bytes,
                    st.hits,
                    st.misses
                )
            }
            None => String::new(),
        },
        Err(_) => String::new(),
    }
}

/// store 係咪已經初始化
pub fn ready() -> bool {
    match global().lock() {
        Ok(g) => g.is_some(),
        Err(_) => false,
    }
}

// ---------------------------------------------------------------- 測試

#[cfg(test)]
mod tests {
    use super::*;

    // 注意：store 係**全域單例**，而 Rust 嘅測試預設係並行行，
    // 所以全部 case 合埋做一個 #[test] 順序行 —— 分開寫會互相覆蓋 root。

    #[test]
    fn esc_roundtrip() {
        for s in [
            "",
            "a",
            "line1\nline2\n",
            "cr\rhere",
            "back\\slash",
            "sep\u{1F}here",
            "mix\\\n\r\u{1F}end",
        ] {
            assert_eq!(unesc(&esc(s)), s);
        }
    }

    #[test]
    fn all_ordered() {
        // 每次行都用乾淨目錄
        let root = "/tmp/hketa_store_test_rs";
        let _ = fs::remove_dir_all(root);
        assert!(init(root));

        // --- 基本 put / get / at ---
        assert!(put("unit", "k1", "v1", 1000));
        assert_eq!(get("unit", "k1"), Some("v1".to_string()));
        assert_eq!(at("unit", "k1"), 1000);
        assert_eq!(get("unit", "nope"), None);
        assert_eq!(at("unit", "nope"), 0);

        // --- 多行 value（Leaflet CSS/JS 一定有換行）---
        let css = ".leaflet-foo {\n  color: red;\r\n}\n";
        assert!(put("unit", "css", css, 1));
        assert_eq!(get("unit", "css"), Some(css.to_string()));

        // --- keys / count / bytes / del ---
        assert!(put("unit", "k2", "v2", 2000));
        assert!(put("unit", "k3", "v3", 3000));
        let ks = keys("unit");
        assert_eq!(ks, vec!["css", "k1", "k2", "k3"]);
        assert_eq!(count("unit"), 4);
        assert!(del("unit", "k3"));
        assert!(!del("unit", "k3"));
        assert_eq!(count("unit"), 3);

        // --- 落碟 ＋ 冷啟動重讀 ---
        assert!(flush());
        assert!(init(root));
        assert_eq!(get("unit", "k1"), Some("v1".to_string()));
        assert_eq!(get("unit", "css"), Some(css.to_string()));
        assert_eq!(at("unit", "k2"), 2000);

        // --- 踢最舊（MAX_PER_NS）---
        for i in 0..(MAX_PER_NS + 8) {
            assert!(put("evict", &format!("k{}", i), "v", i as i64));
        }
        assert_eq!(count("evict") as usize, MAX_PER_NS);
        assert_eq!(get("evict", "k0"), None); // 最舊被踢
        assert_eq!(
            get("evict", &format!("k{}", MAX_PER_NS + 7)),
            Some("v".to_string())
        ); // 最新仲喺度

        // --- 空輸入要拒絕（唔可以寫壞格式）---
        assert!(!put("", "k", "v", 0));
        assert!(!put("ns", "", "v", 0));
        assert_eq!(get("", "k"), None);
        assert_eq!(get("ns", ""), None);

        // --- clear ---
        assert!(put("bye", "a", "1", 1));
        assert!(clear("bye"));
        assert_eq!(count("bye"), 0);

        // --- stats 唔可以 deadlock，而且要數到嘢 ---
        let st = stats();
        assert!(st.starts_with("ns="), "stats = {}", st);
        assert!(ready());

        let _ = fs::remove_dir_all(root);
    }
}
