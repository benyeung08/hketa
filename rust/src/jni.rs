//! # JNI 綁定
//!
//! 對應 Kotlin `com.hketa.app.data.RustKernel` —— **唯一**嘅 Kotlin 入口。
//!
//! 索引（儲存 ＋ 常駐記憶體）、內核（計數）、加速層（TTL 快取）三組 JNI
//! 全部掛喺 `RustKernel` 呢個 class 名下（`nativeIndex*` / `native*` / `nativeSpeed*`）。
//!
//! ## 跨邊界嘅資料格式
//!
//! **唔用 JSON** —— 7 MB 嘅索引解析一次要好幾百毫秒，咁就冇咗用 Rust 嘅意義。
//!
//! 改為傳**字串表 ＋ 整數索引**：
//!
//! * `nativeStrings()` 一次過傳晒去重後嘅字串表（25770 條）
//! * 其餘全部係 `IntArray`／`DoubleArray`（扁平化，一個記錄接一個記錄）
//! * Kotlin 側用「字串表 ＋ 索引」砌物件，**所有記錄共用同一個 `String` 實例**
//!
//! 呢個就係慳記憶體嘅關鍵：63150 筆路線-車站對應入面，`route` 同 `stopId`
//! 實際淨係得幾千個唔同值，但 JSON 反序列化會為每一筆起一個獨立 `String`。
//!
//! ## panic 嘅處理
//!
//! panic 跨 FFI 邊界係 UB，所以呢度一律唔用 `unwrap()`／`expect()`／`[]`，
//! 全部用 `unwrap_or_default()` / `.get()`，任何錯誤都安靜咁返回空值 ——
//! Kotlin 側有純 Kotlin 回退，唔會影響 App。

use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jdouble, jdoubleArray, jint, jintArray, jlong, jobjectArray};
use jni::JNIEnv;

use crate::{Index, OPS};

fn jstr(env: &mut JNIEnv, s: &JString) -> String {
    match env.get_string(s) {
        Ok(v) => v.into(),
        Err(_) => String::new(),
    }
}

fn new_ints(env: &mut JNIEnv, data: &[jint]) -> jintArray {
    match env.new_int_array(data.len() as i32) {
        Ok(arr) => {
            let _ = env.set_int_array_region(&arr, 0, data);
            arr.as_raw()
        }
        Err(_) => std::ptr::null_mut(),
    }
}

fn new_doubles(env: &mut JNIEnv, data: &[jdouble]) -> jdoubleArray {
    match env.new_double_array(data.len() as i32) {
        Ok(arr) => {
            let _ = env.set_double_array_region(&arr, 0, data);
            arr.as_raw()
        }
        Err(_) => std::ptr::null_mut(),
    }
}

// ---------------------------------------------------------------- 生命週期

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexLoad<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
) -> jlong {
    let p = jstr(&mut env, &path);
    match Index::load(&p) {
        Ok(idx) => Box::into_raw(Box::new(idx)) as jlong,
        Err(_) => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexFromJson<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    json: JString<'local>,
) -> jlong {
    let j = jstr(&mut env, &json);
    match Index::from_json(&j) {
        Ok(idx) => Box::into_raw(Box::new(idx)) as jlong,
        Err(_) => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexSave<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    path: JString<'local>,
) -> jboolean {
    if handle == 0 {
        return 0;
    }
    let p = jstr(&mut env, &path);
    let idx = unsafe { &*(handle as *const Index) };
    match idx.save(&p) {
        Ok(_) => 1,
        Err(_) => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexFree(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe {
            drop(Box::from_raw(handle as *mut Index));
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexVersion(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    1
}

// ---------------------------------------------------------------- 讀取

/// `[routes, stops, routeStops, strings]`
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexStats(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jintArray {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let idx = unsafe { &*(handle as *const Index) };
    let v = [
        idx.routes.len() as jint,
        idx.stops.len() as jint,
        idx.route_stops.len() as jint,
        idx.strings.len() as jint,
    ];
    new_ints(&mut env, &v)
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexBuiltAt(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    if handle == 0 {
        return 0;
    }
    let idx = unsafe { &*(handle as *const Index) };
    idx.built_at
}

/// 去重後嘅字串表 —— Kotlin 側會當做共用嘅 `String` 池
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexStrings<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jobjectArray {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let idx = unsafe { &*(handle as *const Index) };
    let n = idx.strings.len();
    let arr = match env.new_object_array(n as i32, "java/lang/String", jni::objects::JObject::null())
    {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };
    for (i, s) in idx.strings.iter().enumerate() {
        if let Ok(js) = env.new_string(s.as_str()) {
            let _ = env.set_object_array_element(&arr, i as i32, js);
        }
    }
    arr.as_raw()
}

/// 每條路線 9 個 int：`op, bound, route, routeId, orig, origEn, dest, destEn, serviceType`
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexRoutes(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jintArray {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let idx = unsafe { &*(handle as *const Index) };
    let mut v: Vec<jint> = Vec::with_capacity(idx.routes.len() * 9);
    for r in &idx.routes {
        v.push(r.op as jint);
        v.push(r.bound as jint);
        v.push(r.route as jint);
        v.push(r.route_id as jint);
        v.push(r.orig as jint);
        v.push(r.orig_en as jint);
        v.push(r.dest as jint);
        v.push(r.dest_en as jint);
        v.push(r.service_type as jint);
    }
    new_ints(&mut env, &v)
}

/// 每個站 4 個 int：`op, id, name, nameEn`（座標另外用 `nativeStopsLatLon`）
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexStops(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jintArray {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let idx = unsafe { &*(handle as *const Index) };
    let mut v: Vec<jint> = Vec::with_capacity(idx.stops.len() * 4);
    for st in &idx.stops {
        v.push(st.op as jint);
        v.push(st.id as jint);
        v.push(st.name as jint);
        v.push(st.name_en as jint);
    }
    new_ints(&mut env, &v)
}

/// `lat, lon` 交替
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexStopsLatLon(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jdoubleArray {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let idx = unsafe { &*(handle as *const Index) };
    let mut v: Vec<jdouble> = Vec::with_capacity(idx.stops.len() * 2);
    for st in &idx.stops {
        v.push(st.lat);
        v.push(st.lon);
    }
    new_doubles(&mut env, &v)
}

/// 每筆 7 個 int：`op, bound, seq, route, serviceType, stopId, routeId`
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexRouteStops(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jintArray {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let idx = unsafe { &*(handle as *const Index) };
    let mut v: Vec<jint> = Vec::with_capacity(idx.route_stops.len() * 6);
    for rs in &idx.route_stops {
        v.push(rs.op as jint);
        v.push(rs.bound as jint);
        v.push(rs.seq as jint);
        v.push(rs.route as jint);
        v.push(rs.service_type as jint);
        v.push(rs.stop_id as jint);
        v.push(rs.route_id as jint);
    }
    new_ints(&mut env, &v)
}

// ---------------------------------------------------------------- 查詢

/// 半徑內嘅車站，按距離排。每項 2 個 int：`stop 下標, 距離米`
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexNearby(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    lat: jdouble,
    lon: jdouble,
    radius_m: jdouble,
) -> jintArray {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let idx = unsafe { &*(handle as *const Index) };
    let r = idx.nearby(lat, lon, radius_m);
    let mut v: Vec<jint> = Vec::with_capacity(r.len() * 2);
    for (i, d) in r {
        v.push(i as jint);
        v.push(d);
    }
    new_ints(&mut env, &v)
}

/// 某個站有邊啲路線 —— 返回字串表索引
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexRoutesAtStop<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    op: JString<'local>,
    stop_id: JString<'local>,
) -> jintArray {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let op_s = jstr(&mut env, &op);
    let sid_s = jstr(&mut env, &stop_id);
    let op_i = match OPS.iter().position(|o| *o == op_s.as_str()) {
        Some(i) => i as u8,
        None => return new_ints(&mut env, &[]),
    };
    let idx = unsafe { &mut *(handle as *mut Index) };
    let r = idx.routes_at_stop(op_i, &sid_s);
    let v: Vec<jint> = r.iter().map(|x| *x as jint).collect();
    new_ints(&mut env, &v)
}

// ================================================================
// Rust 完全擁有：常駐 handle ＋ 查詢結果先至過界
// ================================================================
//
// 呢度嘅 handle 同上面嗰啲唔同：上面係 `Box::into_raw`（Kotlin 要自己
// `nativeFree`），呢度係 **Rust 常駐**（`resident_insert`，索引用嘅記憶體
// 由 Rust 擁有，Kotlin 淨係揸住一個 `Long`）。
//
// 行格式用 `\u{1F}` 分隔，見 `lib.rs` 嘅 `SEP`。

use jni::objects::JObject;

fn new_strings(env: &mut JNIEnv, rows: &[String]) -> jobjectArray {
    let cls = match env.find_class("java/lang/String") {
        Ok(c) => c,
        Err(_) => return std::ptr::null_mut(),
    };
    match env.new_object_array(rows.len() as i32, cls, JObject::null()) {
        Ok(arr) => {
            for (i, r) in rows.iter().enumerate() {
                if let Ok(s) = env.new_string(r.as_str()) {
                    let _ = env.set_object_array_element(&arr, i as i32, JObject::from(s));
                }
            }
            arr.as_raw()
        }
        Err(_) => std::ptr::null_mut(),
    }
}

/// 由碟上嘅二進制索引開一個常駐 handle；失敗返回 0
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexOpen<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
) -> jlong {
    let p = jstr(&mut env, &path);
    match Index::load(&p) {
        Ok(idx) => crate::resident_insert(idx) as jlong,
        Err(_) => 0,
    }
}

/// 由 JSON 建一個常駐 handle（第一次建索引嗰陣用）
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexOpenJson<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    json: JString<'local>,
) -> jlong {
    let j = jstr(&mut env, &json);
    match Index::from_json(&j) {
        Ok(idx) => crate::resident_insert(idx) as jlong,
        Err(_) => 0,
    }
}

/// 收返常駐 handle，並釋放 native heap 上面嘅索引
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexClose(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        crate::resident_remove(handle as u64);
    }
}

/// 常駐索引嘅數量（診斷用，正常應該係 1）
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexResidentCount(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    crate::resident_len() as jint
}

/// 把常駐索引寫落碟（原子寫：`.tmp` 再 rename）
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexSaveResident<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    path: JString<'local>,
) -> jboolean {
    if handle == 0 {
        return 0;
    }
    let p = jstr(&mut env, &path);
    match crate::resident_get(handle as u64) {
        Some(idx) => match idx.save(&p) {
            Ok(_) => 1,
            Err(_) => 0,
        },
        None => 0,
    }
}

/// `[routes, stops, routeStops, strings]`（常駐版）
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexStatsResident(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jintArray {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    match crate::resident_get(handle as u64) {
        Some(idx) => {
            let v = [
                idx.routes.len() as jint,
                idx.stops.len() as jint,
                idx.route_stops.len() as jint,
                idx.strings.len() as jint,
            ];
            new_ints(&mut env, &v)
        }
        None => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexBuiltAtResident(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    if handle == 0 {
        return 0;
    }
    match crate::resident_get(handle as u64) {
        Some(idx) => idx.built_at,
        None => 0,
    }
}

/// 搵路線。每行：`op|route|bound|serviceType|routeId|orig|dest`
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexSearchRoutes<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    query: JString<'local>,
    limit: jint,
) -> jobjectArray {
    if handle == 0 || limit <= 0 {
        return new_strings(&mut env, &[]);
    }
    let q = jstr(&mut env, &query);
    match crate::resident_get(handle as u64) {
        Some(idx) => {
            let rows = idx.search_routes(&q, limit as usize);
            new_strings(&mut env, &rows)
        }
        None => new_strings(&mut env, &[]),
    }
}

/// 搵車站。每行：`op|id|name|nameEn|lat|lon|route1,route2,…`
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexSearchStops<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    query: JString<'local>,
    limit: jint,
) -> jobjectArray {
    if handle == 0 || limit <= 0 {
        return new_strings(&mut env, &[]);
    }
    let q = jstr(&mut env, &query);
    match crate::resident_get(handle as u64) {
        Some(idx) => {
            let rows = idx.search_stops(&q, limit as usize);
            new_strings(&mut env, &rows)
        }
        None => new_strings(&mut env, &[]),
    }
}

/// 某條路線嘅車站（按 seq）。每行：`op|stopId|seq|name|nameEn|lat|lon`
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexRouteStopsRows<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    op: JString<'local>,
    route: JString<'local>,
    bound: JString<'local>,
    service_type: JString<'local>,
) -> jobjectArray {
    if handle == 0 {
        return new_strings(&mut env, &[]);
    }
    let op_s = jstr(&mut env, &op);
    let route_s = jstr(&mut env, &route);
    let bound_s = jstr(&mut env, &bound);
    let svc_s = jstr(&mut env, &service_type);
    let op_i = match OPS.iter().position(|o| *o == op_s.as_str()) {
        Some(i) => i as u8,
        None => return new_strings(&mut env, &[]),
    };
    match crate::resident_get(handle as u64) {
        Some(idx) => {
            let rows = idx.route_stops_rows(op_i, &route_s, &bound_s, &svc_s);
            new_strings(&mut env, &rows)
        }
        None => new_strings(&mut env, &[]),
    }
}

/// 半徑內嘅車站。每行：`op|id|name|nameEn|lat|lon|distM`
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeIndexNearbyRows(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    lat: jdouble,
    lon: jdouble,
    radius_m: jdouble,
    limit: jint,
) -> jobjectArray {
    if handle == 0 || limit <= 0 {
        return new_strings(&mut env, &[]);
    }
    match crate::resident_get(handle as u64) {
        Some(idx) => {
            let rows = idx.nearby_rows(lat, lon, radius_m, limit as usize);
            new_strings(&mut env, &rows)
        }
        None => new_strings(&mut env, &[]),
    }
}

// ================================================================
// kernel：領域核心（計數，唔係儲存）
//
// 對應 Kotlin `com.hketa.app.data.RustKernel`。
// 每個函數都有逐行對應嘅純 Kotlin 回退，所以 .so 唔喺度都計到一樣嘅結果。
//
// 呢度全部用 `.get()` / `unwrap_or_default()`，一律唔 panic ——
// panic 跨 FFI 邊界係 UB。
//
// ⚠️ 唔好再喺度寫 `use jni::objects::JObject;` —— 檔案頭（第 336 行）已經
// import 咗，再寫一次會報：
//   error[E0252]: the name `JObject` is defined multiple times
// （同一個 module 入面，`use` 只能有一次。）

use jni::sys::{jobject, jsize};

/// 讀一個 `String[]` 入嚟；失敗就當空陣列
///
/// ⚠️ jni 0.21 只實現咗 `From<JObject> for JObjectArray`，**冇**
/// `From<*mut _jobject>` —— 所以唔可以寫 `JObjectArray::from(a.clone())`
/// （`.clone()` 會 deref 落 raw pointer，就報：
/// `the trait bound JObjectArray<'_>: From<*mut _jobject> is not satisfied`）。
/// 要先用 `JObject::from_raw` 包返個 `JObject` 出嚟。
fn read_strings(env: &mut JNIEnv, arr: jobjectArray) -> Vec<String> {
    if arr.is_null() {
        return Vec::new();
    }
    let a = unsafe { JObject::from_raw(arr as jobject) };
    let ja = jni::objects::JObjectArray::from(a);
    let len = match env.get_array_length(&ja) {
        Ok(n) => n as usize,
        Err(_) => {
            std::mem::forget(ja);
            return Vec::new();
        }
    };
    let mut out: Vec<String> = Vec::with_capacity(len);
    for i in 0..len {
        let v = match env.get_object_array_element(&ja, i as jsize) {
            Ok(o) => o,
            Err(_) => continue,
        };
        let s = match env.get_string(&JString::from(v)) {
            Ok(s) => s.into(),
            Err(_) => continue,
        };
        out.push(s);
    }
    // `ja` 係由 Java 傳入嘅 local ref 借返嚟 —— drop 佢會 delete 咗個
    // local ref（JVM 自己會管），所以一定要 forget。
    std::mem::forget(ja);
    out
}

/// kernel 版本；.so 唔喺度會係 0
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeKernelVersion(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    crate::kernel::KERNEL_VERSION
}

/// 取樣索引：`IntArray`
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeSampleIndices(
    mut env: JNIEnv,
    _class: JClass,
    n: jint,
    max: jint,
) -> jintArray {
    if n <= 0 || max <= 0 {
        return new_ints(&mut env, &[]);
    }
    let idx = crate::kernel::sample_indices(n as usize, max as usize);
    let out: Vec<jint> = idx.iter().map(|v| *v as jint).collect();
    new_ints(&mut env, &out)
}

/// 砌班次表。輸入每站一欄，輸出見 `kernel::encode_timetable`
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeBuildTimetable(
    mut env: JNIEnv,
    _class: JClass,
    cols: jobjectArray,
    max_trips: jint,
) -> jobjectArray {
    if max_trips <= 0 {
        return new_strings(&mut env, &[]);
    }
    let c = read_strings(&mut env, cols);
    if c.is_empty() {
        return new_strings(&mut env, &[]);
    }
    let (trips, hw) = crate::kernel::build_timetable(&c, max_trips as usize);
    let rows = crate::kernel::encode_timetable(&trips, hw);
    new_strings(&mut env, &rows)
}

/// 路線狀況診斷
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeDiagnose(
    _env: JNIEnv,
    _class: JClass,
    total: jint,
    no_data: jint,
    max_gap: jint,
) -> jint {
    crate::kernel::diagnose(total as usize, no_data as usize, max_gap)
}

/// ETA 合併去重排序
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeMergeEta(
    mut env: JNIEnv,
    _class: JClass,
    rows: jobjectArray,
    cap: jint,
) -> jobjectArray {
    if cap <= 0 {
        return new_strings(&mut env, &[]);
    }
    let r = read_strings(&mut env, rows);
    if r.is_empty() {
        return new_strings(&mut env, &[]);
    }
    let out = crate::kernel::merge_eta(&r, cap as usize);
    new_strings(&mut env, &out)
}

/// 路線號正規化
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeNormalizeRoute<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    q: JString<'local>,
) -> jobjectArray {
    let s = jstr(&mut env, &q);
    new_strings(&mut env, &[crate::kernel::normalize_route(&s)])
}

// ---------------------------------------------------------------- 加速層

/// 由 `String` 造一個 `jstring`；失敗就返 null（Kotlin 側當 miss）
fn new_jstring(env: &mut JNIEnv, s: &str) -> jni::sys::jobject {
    match env.new_string(s) {
        Ok(v) => JObject::from(v).as_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// 加速層：寫入。成功返 1，失敗返 0（Kotlin 側會改行 Kotlin 版 `Memo`）
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeSpeedPut<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ns: jint,
    key: JString<'local>,
    payload: JString<'local>,
    now_ms: jlong,
) -> jboolean {
    let k = jstr(&mut env, &key);
    let v = jstr(&mut env, &payload);
    if crate::speed::put(ns, &k, &v, now_ms) {
        1
    } else {
        0
    }
}

/// 加速層：淨係返未過期嘅；過期／冇就返 null
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeSpeedGet<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ns: jint,
    key: JString<'local>,
    now_ms: jlong,
) -> jni::sys::jobject {
    let k = jstr(&mut env, &key);
    match crate::speed::get(ns, &k, now_ms) {
        Some(s) => new_jstring(&mut env, &s),
        None => std::ptr::null_mut(),
    }
}

/// 加速層：唔理過期照返（stale-while-revalidate 用）
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeSpeedPeek<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ns: jint,
    key: JString<'local>,
) -> jni::sys::jobject {
    let k = jstr(&mut env, &key);
    match crate::speed::peek(ns, &k) {
        Some(s) => new_jstring(&mut env, &s),
        None => std::ptr::null_mut(),
    }
}

/// 加速層：呢條 key 幾時抓落嚟（冇就 0）
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeSpeedAt<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ns: jint,
    key: JString<'local>,
) -> jlong {
    let k = jstr(&mut env, &key);
    crate::speed::at(ns, &k)
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeSpeedRemove<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ns: jint,
    key: JString<'local>,
) {
    let k = jstr(&mut env, &key);
    crate::speed::remove(ns, &k);
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeSpeedClear(
    _env: JNIEnv,
    _class: JClass,
    ns: jint,
) {
    crate::speed::clear(ns);
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeSpeedClearAll(
    _env: JNIEnv,
    _class: JClass,
) {
    crate::speed::clear_all();
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeSpeedHits(
    _env: JNIEnv,
    _class: JClass,
    ns: jint,
) -> jlong {
    crate::speed::hits(ns)
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeSpeedMisses(
    _env: JNIEnv,
    _class: JClass,
    ns: jint,
) -> jlong {
    crate::speed::misses(ns)
}

// ---------------------------------------------------------------- 儲存（store）
//
// 成個 APK 嘅「記憶體與儲存」統一由 `crate::store` 擁有：收藏、ETA 快取、
// 班次表快取、輕鐵特別班次、Leaflet 資源包 —— 五個命名空間。
// Kotlin 側 `RustStore` 係唯一入口；`.so` 唔喺度就一律回退原本嘅 Kotlin 實作。

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeStoreInit<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    root: JString<'local>,
) -> jboolean {
    let r = jstr(&mut env, &root);
    if crate::store::init(&r) {
        1
    } else {
        0
    }
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeStoreReady<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jboolean {
    if crate::store::ready() {
        1
    } else {
        0
    }
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeStorePut<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ns: JString<'local>,
    key: JString<'local>,
    value: JString<'local>,
    now_ms: jlong,
) -> jboolean {
    let n = jstr(&mut env, &ns);
    let k = jstr(&mut env, &key);
    let v = jstr(&mut env, &value);
    if crate::store::put(&n, &k, &v, now_ms) {
        1
    } else {
        0
    }
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeStoreGet<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ns: JString<'local>,
    key: JString<'local>,
) -> jni::sys::jobject {
    let n = jstr(&mut env, &ns);
    let k = jstr(&mut env, &key);
    match crate::store::get(&n, &k) {
        Some(s) => new_jstring(&mut env, &s),
        None => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeStoreAt<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ns: JString<'local>,
    key: JString<'local>,
) -> jlong {
    let n = jstr(&mut env, &ns);
    let k = jstr(&mut env, &key);
    crate::store::at(&n, &k)
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeStoreDel<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ns: JString<'local>,
    key: JString<'local>,
) -> jboolean {
    let n = jstr(&mut env, &ns);
    let k = jstr(&mut env, &key);
    if crate::store::del(&n, &k) {
        1
    } else {
        0
    }
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeStoreKeys<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ns: JString<'local>,
) -> jobjectArray {
    let n = jstr(&mut env, &ns);
    new_strings(&mut env, &crate::store::keys(&n))
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeStoreClear<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ns: JString<'local>,
) -> jboolean {
    let n = jstr(&mut env, &ns);
    if crate::store::clear(&n) {
        1
    } else {
        0
    }
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeStoreCount<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ns: JString<'local>,
) -> jlong {
    let n = jstr(&mut env, &ns);
    crate::store::count(&n)
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeStoreBytes<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ns: JString<'local>,
) -> jlong {
    let n = jstr(&mut env, &ns);
    crate::store::bytes(&n)
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeStoreFlush<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jboolean {
    if crate::store::flush() {
        1
    } else {
        0
    }
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeStoreNamespaces<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jobjectArray {
    new_strings(&mut env, &crate::store::namespaces())
}

#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustKernel_nativeStoreStats<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jni::sys::jobject {
    new_jstring(&mut env, &crate::store::stats())
}
