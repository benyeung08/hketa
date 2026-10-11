//! # JNI 綁定
//!
//! 對應 Kotlin `com.hketa.app.data.RustIndex`。
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeLoad<'local>(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeFromJson<'local>(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeSave<'local>(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeFree(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeVersion(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    1
}

// ---------------------------------------------------------------- 讀取

/// `[routes, stops, routeStops, strings]`
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeStats(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeBuiltAt(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeStrings<'local>(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeRoutes(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeStops(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeStopsLatLon(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeRouteStops(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeNearby(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeRoutesAtStop<'local>(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeOpen<'local>(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeOpenJson<'local>(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeClose(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeResidentCount(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    crate::resident_len() as jint
}

/// 把常駐索引寫落碟（原子寫：`.tmp` 再 rename）
#[no_mangle]
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeSaveResident<'local>(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeStatsResident(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeBuiltAtResident(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeSearchRoutes<'local>(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeSearchStops<'local>(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeRouteStopsRows<'local>(
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
pub extern "system" fn Java_com_hketa_app_data_RustIndex_nativeNearbyRows(
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
