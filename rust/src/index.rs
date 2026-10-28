//! # 索引：儲存 ＋ 常駐記憶體（由 Rust 內核擁有）
//!
//! 呢個模組原本擺喺 `lib.rs`，而家併入內核（`RustKernel`）：
//! **索引嘅儲存格式同常駐記憶體，都係 Rust 內核嘅一部分**，
//! 同 `kernel`（計數）、`speed`（TTL 快取）一齊由同一個 `.so` 提供。
//!
//! Kotlin 側淨係得一個入口：`RustKernel`。
//! 對外嘅 JNI 全部係 `Java_com_hketa_app_data_RustKernel_nativeIndex*`。
//!
//! # HKETA 索引儲存（Rust）
//!
//! ## 點解要用 Rust
//!
//! 索引規模：2099 條路線 / 7250 個車站 / 63150 筆路線-車站對應。
//!
//! 放喺 JVM heap 會有兩個問題：
//!
//! 1. **物件開銷**：每個 `RouteStopDef` 係一個物件（16 bytes header）＋ 4 個
//!    `String` 引用，而每個 `String` 又係一個物件（header ＋ `byte[]`）→
//!    63150 筆實際佔用遠大於佢嘅資料量
//! 2. **字串重複**：`kotlinx.serialization` 反序列化 JSON 時**唔會**幫你
//!    interning，所以同一個站名出現 100 次就有 100 個獨立 `String` 實例
//!
//! ## 呢度做咗咩
//!
//! * **字串去重（interning）**：全索引淨係得一份字串表，所有記錄用 `u32`
//!   索引指向佢 → JVM 側可以共用同一個 `String` 實例
//! * **緊湊二進制格式**：自訂格式 ＋ varint，7.20 MB JSON → 1.04 MB
//! * **查詢放喺 Rust 側**：附近車站（bounding box 預篩 ＋ haversine）、
//!   車站→路線索引，都唔使喺 JVM 度遍歷物件
//!
//! ## 檔案格式
//!
//! ```text
//! "HKIX"           4 bytes  magic
//! version          u32 LE
//! built_at         i64 LE
//! strings          u32 count, then for each: u16 len + UTF-8 bytes
//! routes           u32 count, then for each:
//!                    op u8, 7 × varint (route/routeId/orig/origEn/dest/destEn/serviceType), bound u8
//! stops            u32 count, then for each:
//!                    op u8, 3 × varint (id/name/nameEn), lat f64 LE, lon f64 LE
//! route_stops      u32 count, then for each:
//!                    op u8, 4 × varint (route/serviceType/stopId/routeId), bound u8, seq u16 LE
//! ```
//!
//! Varint 係標準 LEB128（7 bits/byte，高位 continuation）。

use std::collections::HashMap;
use std::io::{self, Read, Write};

use serde::Deserialize;

pub const MAGIC: [u8; 4] = *b"HKIX";
pub const FORMAT_VERSION: u32 = 2;

/// 營辦商順序要同 Kotlin `Operator` enum 一致
pub const OPS: [&str; 7] = ["KMB", "CTB", "NLB", "GMB", "MTR_BUS", "MTR_HR", "LRT"];

pub fn op_index(name: &str) -> u8 {
    match OPS.iter().position(|o| *o == name) {
        Some(i) => i as u8,
        None => 0,
    }
}

/// Kotlin `bound` 係 String（"O" / "I" / ""），壓縮成 u8
pub fn bound_u8(b: &str) -> u8 {
    match b {
        "O" => 1,
        "I" => 2,
        _ => 0,
    }
}

pub fn bound_str(b: u8) -> &'static str {
    match b {
        1 => "O",
        2 => "I",
        _ => "",
    }
}

// ---------------------------------------------------------------- 資料結構

/// 全部欄位都係 `Copy` —— 可以放喺一條連續嘅 `Vec` 入面，冇指標開銷
#[derive(Clone, Copy)]
pub struct Route {
    pub op: u8,
    pub bound: u8,
    pub route: u32,
    pub route_id: u32,
    pub orig: u32,
    pub orig_en: u32,
    pub dest: u32,
    pub dest_en: u32,
    pub service_type: u32,
}

#[derive(Clone, Copy)]
pub struct Stop {
    pub op: u8,
    pub id: u32,
    pub name: u32,
    pub name_en: u32,
    pub lat: f64,
    pub lon: f64,
}

#[derive(Clone, Copy)]
pub struct RouteStop {
    pub op: u8,
    pub bound: u8,
    pub seq: u16,
    pub route: u32,
    pub service_type: u32,
    pub stop_id: u32,
    /// NLB / GMB 內部路線 id（Kotlin `RouteStopDef.routeId`）
    pub route_id: u32,
}

// ---------------------------------------------------------------- JSON 輸入

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct JsonIndex {
    #[serde(default)]
    version: u32,
    #[serde(default)]
    built_at: i64,
    #[serde(default)]
    routes: Vec<JsonRoute>,
    #[serde(default)]
    stops: Vec<JsonStop>,
    #[serde(default)]
    route_stops: Vec<JsonRouteStop>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct JsonRoute {
    op: String,
    #[serde(default)]
    route: Option<String>,
    #[serde(default)]
    bound: Option<String>,
    #[serde(default)]
    service_type: Option<String>,
    #[serde(default)]
    route_id: Option<String>,
    #[serde(default)]
    orig: Option<String>,
    #[serde(default)]
    orig_en: Option<String>,
    #[serde(default)]
    dest: Option<String>,
    #[serde(default)]
    dest_en: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct JsonStop {
    op: String,
    #[serde(default)]
    id: Option<String>,
    #[serde(default)]
    name: Option<String>,
    #[serde(default)]
    name_en: Option<String>,
    #[serde(default)]
    lat: f64,
    #[serde(default)]
    lon: f64,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct JsonRouteStop {
    op: String,
    #[serde(default)]
    route_id: Option<String>,
    #[serde(default)]
    route: Option<String>,
    #[serde(default)]
    bound: Option<String>,
    #[serde(default)]
    service_type: Option<String>,
    #[serde(default)]
    stop_id: Option<String>,
    #[serde(default)]
    seq: u16,
}

// ---------------------------------------------------------------- Index

pub struct Index {
    pub version: u32,
    pub built_at: i64,
    /// 字串表（去重）—— 所有記錄用 u32 指向呢度
    pub strings: Vec<String>,
    pub routes: Vec<Route>,
    pub stops: Vec<Stop>,
    pub route_stops: Vec<RouteStop>,
    /// `(op, stop_id_string_idx) -> route_name_string_idx list`
    /// key 打包成 u64：`(op as u64) << 32 | stop_id as u64`
    routes_by_stop: Option<HashMap<u64, Vec<u32>>>,
    /// `(op, stop_id) -> stops 下標`（懶建）—— 用嚟由路線-車站對應查出車站資料
    stops_by_id: Option<HashMap<u64, u32>>,
    /// 字串 → 索引嘅反查表（懶建）—— 唔係嘅話查一個站要線性掃 25770 條字串
    string_index: Option<HashMap<String, u32>>,
}

/// 字串去重：淨係保存一份，返回索引
fn intern_key(v: &str, intern: &mut HashMap<String, u32>, strings: &mut Vec<String>) -> u32 {
    if let Some(i) = intern.get(v) {
        return *i;
    }
    let i = strings.len() as u32;
    strings.push(v.to_string());
    intern.insert(v.to_string(), i);
    i
}

fn s(opt: &Option<String>) -> &str {
    match opt {
        Some(v) => v.as_str(),
        None => "",
    }
}

impl Index {
    pub fn empty() -> Self {
        Index {
            version: FORMAT_VERSION,
            built_at: 0,
            strings: Vec::new(),
            routes: Vec::new(),
            stops: Vec::new(),
            route_stops: Vec::new(),
            routes_by_stop: None,
            string_index: None,
            stops_by_id: None,
        }
    }

    /// 由 Kotlin 側嘅 JSON 建立（第一次建索引嗰陣用；之後一律行二進制）
    pub fn from_json(json: &str) -> Result<Index, String> {
        let j: JsonIndex =
            serde_json::from_str(json).map_err(|e| format!("json parse failed: {e}"))?;
        let mut idx = Index::empty();
        idx.version = if j.version == 0 { FORMAT_VERSION } else { j.version };
        idx.built_at = j.built_at;

        let mut intern: HashMap<String, u32> = HashMap::with_capacity(j.stops.len() * 4);
        let mut strings: Vec<String> = Vec::new();

        idx.routes.reserve(j.routes.len());
        for r in &j.routes {
            idx.routes.push(Route {
                op: op_index(&r.op),
                bound: bound_u8(s(&r.bound)),
                route: intern_key(s(&r.route), &mut intern, &mut strings),
                route_id: intern_key(s(&r.route_id), &mut intern, &mut strings),
                orig: intern_key(s(&r.orig), &mut intern, &mut strings),
                orig_en: intern_key(s(&r.orig_en), &mut intern, &mut strings),
                dest: intern_key(s(&r.dest), &mut intern, &mut strings),
                dest_en: intern_key(s(&r.dest_en), &mut intern, &mut strings),
                service_type: intern_key(s(&r.service_type), &mut intern, &mut strings),
            });
        }

        idx.stops.reserve(j.stops.len());
        for st in &j.stops {
            idx.stops.push(Stop {
                op: op_index(&st.op),
                id: intern_key(s(&st.id), &mut intern, &mut strings),
                name: intern_key(s(&st.name), &mut intern, &mut strings),
                name_en: intern_key(s(&st.name_en), &mut intern, &mut strings),
                lat: st.lat,
                lon: st.lon,
            });
        }

        idx.route_stops.reserve(j.route_stops.len());
        for rs in &j.route_stops {
            idx.route_stops.push(RouteStop {
                op: op_index(&rs.op),
                bound: bound_u8(s(&rs.bound)),
                seq: rs.seq,
                route: intern_key(s(&rs.route), &mut intern, &mut strings),
                service_type: intern_key(s(&rs.service_type), &mut intern, &mut strings),
                stop_id: intern_key(s(&rs.stop_id), &mut intern, &mut strings),
                route_id: intern_key(s(&rs.route_id), &mut intern, &mut strings),
            });
        }

        idx.strings = strings;
        Ok(idx)
    }

    // ------------------------------------------------------------ 二進制

    pub fn write_to<W: Write>(&self, w: &mut W) -> io::Result<()> {
        w.write_all(&MAGIC)?;
        w.write_all(&FORMAT_VERSION.to_le_bytes())?;
        w.write_all(&self.built_at.to_le_bytes())?;

        w.write_all(&(self.strings.len() as u32).to_le_bytes())?;
        for st in &self.strings {
            let b = st.as_bytes();
            w.write_all(&(b.len() as u16).to_le_bytes())?;
            w.write_all(b)?;
        }

        w.write_all(&(self.routes.len() as u32).to_le_bytes())?;
        for r in &self.routes {
            w.write_all(&[r.op])?;
            put_varint(w, r.route)?;
            put_varint(w, r.route_id)?;
            put_varint(w, r.orig)?;
            put_varint(w, r.orig_en)?;
            put_varint(w, r.dest)?;
            put_varint(w, r.dest_en)?;
            put_varint(w, r.service_type)?;
            w.write_all(&[r.bound])?;
        }

        w.write_all(&(self.stops.len() as u32).to_le_bytes())?;
        for st in &self.stops {
            w.write_all(&[st.op])?;
            put_varint(w, st.id)?;
            put_varint(w, st.name)?;
            put_varint(w, st.name_en)?;
            w.write_all(&st.lat.to_le_bytes())?;
            w.write_all(&st.lon.to_le_bytes())?;
        }

        w.write_all(&(self.route_stops.len() as u32).to_le_bytes())?;
        for rs in &self.route_stops {
            w.write_all(&[rs.op])?;
            put_varint(w, rs.route)?;
            put_varint(w, rs.service_type)?;
            put_varint(w, rs.stop_id)?;
            put_varint(w, rs.route_id)?;
            w.write_all(&[rs.bound])?;
            w.write_all(&rs.seq.to_le_bytes())?;
        }
        Ok(())
    }

    pub fn save(&self, path: &str) -> io::Result<()> {
        // 寫 .tmp 再 rename —— 寫到一半崩潰都唔會損壞原有索引
        let tmp = format!("{path}.tmp");
        {
            let f = std::fs::File::create(&tmp)?;
            let mut bw = std::io::BufWriter::new(f);
            self.write_to(&mut bw)?;
            bw.flush()?;
        }
        std::fs::rename(&tmp, path)
    }

    pub fn read_from<R: Read>(r: &mut R) -> Result<Index, String> {
        let mut magic = [0u8; 4];
        r.read_exact(&mut magic).map_err(|e| e.to_string())?;
        if magic != MAGIC {
            return Err("bad magic".to_string());
        }
        let mut b4 = [0u8; 4];
        let mut b8 = [0u8; 8];
        r.read_exact(&mut b4).map_err(|e| e.to_string())?;
        let version = u32::from_le_bytes(b4);
        r.read_exact(&mut b8).map_err(|e| e.to_string())?;
        let built_at = i64::from_le_bytes(b8);

        // 一次讀晒入 memory 再解析 —— 比逐個 read_exact 快好多
        let mut rest = Vec::new();
        r.read_to_end(&mut rest).map_err(|e| e.to_string())?;
        let mut o = 0usize;
        let need = |n: usize, o: &mut usize| -> Result<(), String> {
            if *o + n > rest.len() {
                return Err("truncated".to_string());
            }
            *o += n;
            Ok(())
        };

        // ★ 防止損壞／惡意嘅索引檔 OOM：
        //   n_str 係由檔入面讀出嚟嘅 u32，最大可以去到 4,294,967,295。
        //   直接 `Vec::with_capacity(n_str)` 會即刻嘗試 allocate 幾十 GB
        //   → allocation failure → **abort**，而 abort 喺 native 層係冇得
        //   被 Kotlin catch 嘅，App 會直接閃退（唔係 exception）。
        //   而家按「剩餘位元組 ÷ 每項最少佔幾多個 byte」封頂：
        //   一個 String 最少都要 2 byte 長度 + 內容，所以 n 唔可能大過 rest.len()。
        let cap = |n: usize, min_bytes: usize| -> usize {
            if min_bytes == 0 { n } else { n.min(rest.len() / min_bytes + 1) }
        };

        need(4, &mut o)?;
        let n_str = u32::from_le_bytes(rest[o - 4..o].try_into().unwrap()) as usize;
        let n_str = cap(n_str, 2);
        let mut strings: Vec<String> = Vec::with_capacity(n_str);
        for _ in 0..n_str {
            need(2, &mut o)?;
            let l = u16::from_le_bytes(rest[o - 2..o].try_into().unwrap()) as usize;
            need(l, &mut o)?;
            strings.push(
                String::from_utf8_lossy(&rest[o - l..o]).into_owned(),
            );
        }

        need(4, &mut o)?;
        let n_routes = u32::from_le_bytes(rest[o - 4..o].try_into().unwrap()) as usize;
        // 每筆 Route 最少 1(op) + 7×varint(各 ≥1) + 1(bound) = 9 byte
        let n_routes = cap(n_routes, 9);
        let mut routes: Vec<Route> = Vec::with_capacity(n_routes);
        for _ in 0..n_routes {
            need(1, &mut o)?;
            let op = rest[o - 1];
            let mut ids = [0u32; 7];
            for k in 0..7 {
                let (v, no) = read_varint(&rest, o)?;
                o = no;
                ids[k] = v;
            }
            need(1, &mut o)?;
            routes.push(Route {
                op,
                bound: rest[o - 1],
                route: ids[0],
                route_id: ids[1],
                orig: ids[2],
                orig_en: ids[3],
                dest: ids[4],
                dest_en: ids[5],
                service_type: ids[6],
            });
        }

        need(4, &mut o)?;
        let n_stops = u32::from_le_bytes(rest[o - 4..o].try_into().unwrap()) as usize;
        // 每筆 Stop 最少 1(op) + 3×varint + 16(lat/lon) = 20 byte
        let n_stops = cap(n_stops, 20);
        let mut stops: Vec<Stop> = Vec::with_capacity(n_stops);
        for _ in 0..n_stops {
            need(1, &mut o)?;
            let op = rest[o - 1];
            let (id, no) = read_varint(&rest, o)?;
            o = no;
            let (name, no) = read_varint(&rest, o)?;
            o = no;
            let (name_en, no) = read_varint(&rest, o)?;
            o = no;
            need(16, &mut o)?;
            let lat = f64::from_le_bytes(rest[o - 16..o - 8].try_into().unwrap());
            let lon = f64::from_le_bytes(rest[o - 8..o].try_into().unwrap());
            stops.push(Stop { op, id, name, name_en, lat, lon });
        }

        need(4, &mut o)?;
        let n_rs = u32::from_le_bytes(rest[o - 4..o].try_into().unwrap()) as usize;
        // 每筆 RouteStop 最少 1(op) + 4×varint + 2(seq) + 1(bound) = 7 byte
        let n_rs = cap(n_rs, 7);
        let mut route_stops: Vec<RouteStop> = Vec::with_capacity(n_rs);
        for _ in 0..n_rs {
            need(1, &mut o)?;
            let op = rest[o - 1];
            let (route, no) = read_varint(&rest, o)?;
            o = no;
            let (service_type, no) = read_varint(&rest, o)?;
            o = no;
            let (stop_id, no) = read_varint(&rest, o)?;
            o = no;
            let (route_id, no) = read_varint(&rest, o)?;
            o = no;
            need(3, &mut o)?;
            let bound = rest[o - 3];
            let seq = u16::from_le_bytes(rest[o - 2..o].try_into().unwrap());
            route_stops.push(RouteStop { op, bound, seq, route, service_type, stop_id, route_id });
        }

        Ok(Index {
            version,
            built_at,
            strings,
            routes,
            stops,
            route_stops,
            routes_by_stop: None,
            string_index: None,
            stops_by_id: None,
        })
    }

    pub fn load(path: &str) -> Result<Index, String> {
        let f = std::fs::File::open(path).map_err(|e| e.to_string())?;
        let mut r = std::io::BufReader::new(f);
        Index::read_from(&mut r)
    }

    // ------------------------------------------------------------ 查詢

    /// 建車站→路線索引（懶建，第一次查嗰陣先起）
    fn ensure_routes_by_stop(&mut self) {
        if self.routes_by_stop.is_some() {
            return;
        }
        let mut m: HashMap<u64, Vec<u32>> = HashMap::with_capacity(self.stops.len() * 2);
        for rs in &self.route_stops {
            let key = ((rs.op as u64) << 32) | (rs.stop_id as u64);
            let v = m.entry(key).or_insert_with(Vec::new);
            if !v.contains(&rs.route) {
                v.push(rs.route);
            }
        }
        {
            // 用獨立作用域攞 &self.strings —— 唔係嘅話排序閉包會一路借住 self，
            // 下面 `self.routes_by_stop = Some(m)` 就會撞借用檢查
            let strings = &self.strings;
            for v in m.values_mut() {
                // 同 Kotlin 版一致：先長度後字典序
                v.sort_by(|a, b| {
                    let sa = strings.get(*a as usize).map(|x| x.as_str()).unwrap_or("");
                    let sb = strings.get(*b as usize).map(|x| x.as_str()).unwrap_or("");
                    sa.len().cmp(&sb.len()).then_with(|| sa.cmp(sb))
                });
            }
        }
        self.routes_by_stop = Some(m);
    }

    /// 建字串反查表（懶建）
    fn ensure_string_index(&mut self) {
        if self.string_index.is_some() {
            return;
        }
        let mut m: HashMap<String, u32> = HashMap::with_capacity(self.strings.len() * 2);
        for (i, st) in self.strings.iter().enumerate() {
            m.entry(st.clone()).or_insert(i as u32);
        }
        self.string_index = Some(m);
    }

    /// 某個站有邊啲路線（返回字串表索引）
    pub fn routes_at_stop(&mut self, op: u8, stop_id: &str) -> Vec<u32> {
        self.ensure_string_index();
        let sid = match self.string_index.as_ref().and_then(|m| m.get(stop_id).copied()) {
            Some(i) => i,
            None => return Vec::new(),
        };
        self.ensure_routes_by_stop();
        let key = ((op as u64) << 32) | (sid as u64);
        match &self.routes_by_stop {
            Some(m) => m.get(&key).cloned().unwrap_or_default(),
            None => Vec::new(),
        }
    }

    /// 半徑內嘅車站，按距離排（返回 `(stop 下標, 距離米)`）
    ///
    /// bounding box 預篩：先用加減法濾走絕大部分站，
    /// 淨係對候選做 haversine（sin/cos/atan2/sqrt）
    pub fn nearby(&self, lat: f64, lon: f64, radius_m: f64) -> Vec<(u32, i32)> {
        let d_lat = radius_m / 111_320.0;
        let cos_lat = (lat.to_radians().cos()).max(0.01);
        let d_lon = radius_m / (111_320.0 * cos_lat);
        let min_lat = lat - d_lat;
        let max_lat = lat + d_lat;
        let min_lon = lon - d_lon;
        let max_lon = lon + d_lon;

        let mut out: Vec<(u32, i32)> = Vec::with_capacity(64);
        for (i, st) in self.stops.iter().enumerate() {
            // 冇座標嘅站（例如未補到經緯度嘅）直接跳過
            if st.lat == 0.0 && st.lon == 0.0 {
                continue;
            }
            if st.lat < min_lat || st.lat > max_lat {
                continue;
            }
            if st.lon < min_lon || st.lon > max_lon {
                continue;
            }
            let d = haversine(lat, lon, st.lat, st.lon);
            if d <= radius_m {
                out.push((i as u32, d as i32));
            }
        }
        out.sort_by_key(|x| x.1);
        out
    }
}

pub fn haversine(lat1: f64, lon1: f64, lat2: f64, lon2: f64) -> f64 {
    let r = 6_371_000.0;
    let d_lat = (lat2 - lat1).to_radians();
    let d_lon = (lon2 - lon1).to_radians();
    let a = (d_lat / 2.0).sin().powi(2)
        + lat1.to_radians().cos() * lat2.to_radians().cos() * (d_lon / 2.0).sin().powi(2);
    2.0 * r * a.sqrt().atan2((1.0 - a).sqrt())
}

// ---------------------------------------------------------------- varint

pub fn put_varint<W: Write>(w: &mut W, mut v: u32) -> io::Result<()> {
    loop {
        let b = (v & 0x7f) as u8;
        v >>= 7;
        if v != 0 {
            w.write_all(&[b | 0x80])?;
        } else {
            w.write_all(&[b])?;
            return Ok(());
        }
    }
}

pub fn read_varint(b: &[u8], mut o: usize) -> Result<(u32, usize), String> {
    let mut v: u32 = 0;
    let mut shift = 0;
    loop {
        if o >= b.len() {
            return Err("varint out of bounds".to_string());
        }
        let x = b[o];
        o += 1;
        v |= ((x & 0x7f) as u32) << shift;
        if x & 0x80 == 0 {
            return Ok((v, o));
        }
        shift += 7;
        if shift > 28 {
            return Err("varint too long".to_string());
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn demo() -> Index {
        let json = r#"{
            "version": 2, "builtAt": 1759900000000,
            "routes": [{"op":"KMB","route":"1A","bound":"O","serviceType":"01","routeId":"1","orig":"A","origEn":"A","dest":"B","destEn":"B"}],
            "stops": [{"op":"KMB","id":"S1","name":"站一","nameEn":"Stop 1","lat":22.3,"lon":114.2},
                      {"op":"KMB","id":"S2","name":"站二","nameEn":"Stop 2","lat":22.31,"lon":114.21}],
            "routeStops": [{"op":"KMB","route":"1A","bound":"O","serviceType":"01","stopId":"S1","seq":1}]
        }"#;
        Index::from_json(json).unwrap()
    }

    #[test]
    fn round_trip() {
        let idx = demo();
        let mut buf = Vec::new();
        idx.write_to(&mut buf).unwrap();
        let back = Index::read_from(&mut std::io::Cursor::new(buf)).unwrap();
        assert_eq!(back.routes.len(), 1);
        assert_eq!(back.stops.len(), 2);
        assert_eq!(back.route_stops.len(), 1);
        assert_eq!(back.strings[back.routes[0].route as usize], "1A");
        assert!((back.stops[0].lat - 22.3).abs() < 1e-12);
    }

    #[test]
    fn nearby_matches() {
        let idx = demo();
        let r = idx.nearby(22.3, 114.2, 2000.0);
        assert_eq!(r.len(), 2);
        assert_eq!(r[0].0, 0); // 自己最近
    }

    #[test]
    fn routes_at_stop_works() {
        let mut idx = demo();
        let r = idx.routes_at_stop(op_index("KMB"), "S1");
        assert_eq!(r.len(), 1);
        assert_eq!(idx.strings[r[0] as usize], "1A");
        assert!(idx.routes_at_stop(op_index("KMB"), "NOPE").is_empty());
    }
}


// ================================================================
// Rust 完全擁有：常駐記憶體 ＋ 查詢
// ================================================================
//
// 上面嗰個版本淨係做「儲存」：Kotlin 攞完 handle 會砌返一個 `IndexData`
// 放落 JVM heap，即係 JVM 仍然持有一份完整副本。
//
// 呢度做嘅係「Rust 完全擁有」：
//
// * **常駐**：索引放喺 Rust 嘅 `REGISTRY`（native heap），Kotlin 淨係揸住一個
//   `Long` handle。每次查詢都唔使重新讀碟。
// * **查詢喺 Rust 側做**：搜尋路線、搜尋車站、搵路線嘅車站 —— Kotlin 唔使
//   遍歷 JVM 物件。
// * **結果先至過界**：Rust 淨係把「要顯示嗰幾十筆」傳過去。JVM heap 淨係持有
//   **暫時性、結果咁大**嘅物件，而唔係成個索引。
//
// 行格式用 `\u{1F}`（unit separator）分隔 —— 站名唔會有呢個字符，
// 亦唔使自訂二進制格式（少一個出錯位）。

pub const SEP: char = '\u{1F}';

impl Index {
    pub fn str_at(&self, i: u32) -> &str {
        match self.strings.get(i as usize) {
            Some(s) => s.as_str(),
            None => "",
        }
    }

    fn ensure_stops_by_id(&mut self) {
        if self.stops_by_id.is_some() {
            return;
        }
        let mut m: HashMap<u64, u32> = HashMap::with_capacity(self.stops.len() * 2);
        for (i, st) in self.stops.iter().enumerate() {
            let key = ((st.op as u64) << 32) | (st.id as u64);
            m.entry(key).or_insert(i as u32);
        }
        self.stops_by_id = Some(m);
    }

    /// 搵路線。行格式：`op|route|bound|serviceType|routeId|orig|dest`
    ///
    /// 排序同純 Kotlin 版一致：前綴命中 → 包含 → 起點／終點命中，
    /// 每組入面按路線號長度再按字典序。
    pub fn search_routes(&self, q: &str, limit: usize) -> Vec<String> {
        if q.is_empty() || limit == 0 {
            return Vec::new();
        }
        let qq = q.trim().to_uppercase();
        if qq.is_empty() {
            return Vec::new();
        }

        let mut prefix: Vec<u32> = Vec::new();
        let mut substr: Vec<u32> = Vec::new();
        let mut ends: Vec<u32> = Vec::new();

        for (i, r) in self.routes.iter().enumerate() {
            let name = self.str_at(r.route).to_uppercase();
            let rid = self.str_at(r.route_id).to_uppercase();
            if name.starts_with(&qq) || rid.starts_with(&qq) {
                prefix.push(i as u32);
            } else if name.contains(&qq) || rid.contains(&qq) {
                substr.push(i as u32);
            } else if self.str_at(r.orig).to_uppercase().contains(&qq)
                || self.str_at(r.dest).to_uppercase().contains(&qq)
                || self.str_at(r.orig_en).to_uppercase().contains(&qq)
                || self.str_at(r.dest_en).to_uppercase().contains(&qq)
            {
                ends.push(i as u32);
            }
        }

        // 每組入面：路線號短嘅排先，再按字典序
        let sort_group = |v: &mut Vec<u32>, idx: &Index| {
            v.sort_by(|a, b| {
                let ra = &idx.routes[*a as usize];
                let rb = &idx.routes[*b as usize];
                let la = idx.str_at(ra.route).len();
                let lb = idx.str_at(rb.route).len();
                la.cmp(&lb)
                    .then_with(|| idx.str_at(ra.route).cmp(idx.str_at(rb.route)))
                    .then_with(|| ra.op.cmp(&rb.op))
            });
        };
        sort_group(&mut prefix, self);
        sort_group(&mut substr, self);
        sort_group(&mut ends, self);

        let mut out: Vec<String> = Vec::with_capacity(limit);
        for i in prefix.into_iter().chain(substr).chain(ends).take(limit) {
            let r = &self.routes[i as usize];
            out.push(format!(
                "{}{}{}{}{}{}{}{}{}{}{}{}{}",
                OPS.get(r.op as usize).copied().unwrap_or("KMB"), SEP,
                self.str_at(r.route), SEP,
                bound_str(r.bound), SEP,
                self.str_at(r.service_type), SEP,
                self.str_at(r.route_id), SEP,
                self.str_at(r.orig), SEP,
                self.str_at(r.dest)
            ));
        }
        out
    }

    /// 搵車站。行格式：`op|id|name|nameEn|lat|lon|route1,route2,…`
    ///
    /// 排序同純 Kotlin 版一致：站名開頭 → 站名包含 → 英文名開頭 →
    /// 英文名包含 → 淨係站號命中。
    pub fn search_stops(&mut self, q: &str, limit: usize) -> Vec<String> {
        if q.is_empty() || limit == 0 {
            return Vec::new();
        }
        let key = q.trim();
        let upper = key.to_uppercase();
        if upper.is_empty() {
            return Vec::new();
        }

        self.ensure_string_index();
        self.ensure_routes_by_stop();

        let mut scored: Vec<(u8, u32)> = Vec::new();
        for (i, st) in self.stops.iter().enumerate() {
            let name = self.str_at(st.name);
            let name_en = self.str_at(st.name_en);
            let id = self.str_at(st.id);
            let rank: u8 = if name.to_uppercase().starts_with(&upper) {
                0
            } else if name.to_uppercase().contains(&upper) {
                1
            } else if name_en.to_uppercase().starts_with(&upper) {
                2
            } else if name_en.to_uppercase().contains(&upper) {
                3
            } else if id.to_uppercase().contains(&upper) {
                4
            } else {
                continue;
            };
            scored.push((rank, i as u32));
        }

        scored.sort_by(|a, b| {
            a.0.cmp(&b.0)
                .then_with(|| {
                    let la = self.str_at(self.stops[a.1 as usize].name).len();
                    let lb = self.str_at(self.stops[b.1 as usize].name).len();
                    la.cmp(&lb)
                })
                .then_with(|| {
                    self.str_at(self.stops[a.1 as usize].name)
                        .cmp(self.str_at(self.stops[b.1 as usize].name))
                })
        });

        let mut out: Vec<String> = Vec::with_capacity(limit);
        for (_, i) in scored.into_iter().take(limit) {
            let st = &self.stops[i as usize];
            let key64 = ((st.op as u64) << 32) | (st.id as u64);
            let mut routes: Vec<&str> = Vec::new();
            if let Some(m) = &self.routes_by_stop {
                if let Some(v) = m.get(&key64) {
                    for r in v {
                        let name = self.str_at(*r);
                        if !name.is_empty() && !routes.contains(&name) {
                            routes.push(name);
                        }
                    }
                }
            }
            routes.sort_by(|a, b| a.len().cmp(&b.len()).then_with(|| a.cmp(b)));
            out.push(format!(
                "{}{}{}{}{}{}{}{}{}{}{}{}{}",
                OPS.get(st.op as usize).copied().unwrap_or("KMB"), SEP,
                self.str_at(st.id), SEP,
                self.str_at(st.name), SEP,
                self.str_at(st.name_en), SEP,
                st.lat, SEP,
                st.lon, SEP,
                routes.join(",")
            ));
        }
        out
    }

    /// 某條路線（某方向）嘅車站，按 `seq` 排。
    /// 行格式：`op|stopId|seq|name|nameEn|lat|lon`
    pub fn route_stops_rows(&mut self, op: u8, route: &str, bound: &str, svc: &str) -> Vec<String> {
        self.ensure_stops_by_id();
        let mut hits: Vec<(u16, u32)> = Vec::new();
        for (i, rs) in self.route_stops.iter().enumerate() {
            if rs.op != op {
                continue;
            }
            if self.str_at(rs.route) != route {
                continue;
            }
            let b = bound_str(rs.bound);
            if !bound.is_empty() && b != bound {
                continue;
            }
            if !svc.is_empty() && self.str_at(rs.service_type) != svc {
                continue;
            }
            hits.push((rs.seq, i as u32));
        }
        hits.sort_by_key(|x| x.0);

        let mut out: Vec<String> = Vec::with_capacity(hits.len());
        for (seq, i) in hits {
            let rs = &self.route_stops[i as usize];
            let key = ((rs.op as u64) << 32) | (rs.stop_id as u64);
            let si = match &self.stops_by_id {
                Some(m) => m.get(&key).copied(),
                None => None,
            };
            let (name, name_en, lat, lon) = match si {
                Some(s) => {
                    let st = &self.stops[s as usize];
                    (
                        self.str_at(st.name),
                        self.str_at(st.name_en),
                        st.lat,
                        st.lon,
                    )
                }
                None => ("", "", 0.0_f64, 0.0_f64),
            };
            out.push(format!(
                "{}{}{}{}{}{}{}{}{}{}{}{}{}",
                OPS.get(rs.op as usize).copied().unwrap_or("KMB"), SEP,
                self.str_at(rs.stop_id), SEP,
                seq, SEP,
                name, SEP,
                name_en, SEP,
                lat, SEP,
                lon
            ));
        }
        out
    }

    /// 半徑內嘅車站，按距離排。行格式：`op|id|name|nameEn|lat|lon|distM`
    pub fn nearby_rows(&self, lat: f64, lon: f64, radius_m: f64, limit: usize) -> Vec<String> {
        let r = self.nearby(lat, lon, radius_m);
        let mut out: Vec<String> = Vec::with_capacity(limit);
        for (i, d) in r.into_iter().take(limit) {
            let st = match self.stops.get(i as usize) {
                Some(s) => s,
                None => continue,
            };
            out.push(format!(
                "{}{}{}{}{}{}{}{}{}{}{}{}{}",
                OPS.get(st.op as usize).copied().unwrap_or("KMB"), SEP,
                self.str_at(st.id), SEP,
                self.str_at(st.name), SEP,
                self.str_at(st.name_en), SEP,
                st.lat, SEP,
                st.lon, SEP,
                d
            ));
        }
        out
    }
}

// ---------------------------------------------------------------- 常駐登錄表

use std::ptr;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Mutex, OnceLock};

/// `ptr::NonNull<Index>` 本身**唔係 `Send`**（raw pointer 預設唔 Send），
/// 但 `static` 要求 `Sync`，而 `Mutex<T>: Sync` 又要 `T: Send` —— 所以直接
/// 擺 `NonNull` 入 `static` 會報：
///
/// ```text
/// error[E0277]: `NonNull<Index>` cannot be sent between threads safely
///     --> src/index.rs:949:15
/// ```
///
/// 呢度嘅指標全部係 `Box::leak` 出嚟：地址永遠唔會變（唔會因為 `HashMap`
/// rehash 而搬走），活到 `resident_remove` 先回收，而且**所有存取都喺
/// `Mutex` 入面排隊** —— 所以標記 `Send` 係啱嘅。
#[derive(Clone, Copy)]
struct SendPtr(ptr::NonNull<Index>);

// SAFETY: 見上面 `SendPtr` 嘅註解：指標由 `Box::leak` 嚟、地址穩定、
// 全部存取都喺 `Mutex` 入面。
unsafe impl Send for SendPtr {}

/// `handle -> *mut Index`
///
/// 入面放嘅係 **`Box::leak` 出嚟嘅指標**：`Index` 本體喺 native heap，
/// 地址永遠唔會變（唔會因為 `HashMap` rehash 而搬走），所以攞到嘅係
/// `&'static mut Index`，**唔使任何 unsafe 轉型**。
fn registry() -> &'static Mutex<HashMap<u64, SendPtr>> {
    static R: OnceLock<Mutex<HashMap<u64, SendPtr>>> = OnceLock::new();
    R.get_or_init(|| Mutex::new(HashMap::new()))
}

static NEXT_ID: AtomicU64 = AtomicU64::new(1);

/// 把索引交畀 Rust 長期持有（native heap），返回 handle。
///
/// Kotlin 淨係揸住呢個 `Long` —— 索引本身嘅記憶體**由 Rust 擁有**，
/// 唔喺 JVM heap，亦唔使每次查詢重新讀碟。
pub fn resident_insert(idx: Index) -> u64 {
    let leaked: &'static mut Index = Box::leak(Box::new(idx));
    let id = NEXT_ID.fetch_add(1, Ordering::Relaxed);
    match registry().lock() {
        Ok(mut m) => {
            match ptr::NonNull::new(leaked as *mut Index) {
                Some(p) => {
                    m.insert(id, SendPtr(p));
                    id
                }
                None => 0,
            }
        }
        Err(_) => 0,
    }
}

pub fn resident_get(id: u64) -> Option<&'static mut Index> {
    let p = match registry().lock() {
        Ok(m) => match m.get(&id) {
            Some(p) => p.0,
            None => return None,
        },
        Err(_) => return None,
    };
    // SAFETY: 指標由 `Box::leak` 嚟，地址穩定、活到 process 結束（或者
    // `resident_remove` 嗰陣先回收）。Kotlin 側唔會同時揸住兩個可變引用。
    Some(unsafe { &mut *p.as_ptr() })
}

/// 收返 handle，並**真正釋放** native heap 上面嘅 `Index`
pub fn resident_remove(id: u64) {
    let p = match registry().lock() {
        Ok(mut m) => m.remove(&id),
        Err(_) => None,
    };
    if let Some(p) = p {
        unsafe {
            drop(Box::from_raw(p.0.as_ptr()));
        }
    }
}

pub fn resident_len() -> usize {
    match registry().lock() {
        Ok(m) => m.len(),
        Err(_) => 0,
    }
}

