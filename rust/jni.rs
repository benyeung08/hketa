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
