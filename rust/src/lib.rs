//! # `libhketa_store` —— HKETA 嘅 Rust 內核
//!
//! 一個 `.so`，四件事，全部由 **Rust 內核**擁有：
//!
//! | 模組 | 負責 | Kotlin 入口 |
//! |---|---|---|
//! | [`index`] | **儲存**（緊湊二進制）＋ **常駐記憶體**（`Box::leak`，native heap） | `RustKernel.nativeIndex*` |
//! | [`kernel`] | **計數**（取樣、班次表、診斷、ETA 合併） | `RustKernel.nativeSampleIndices` 等 |
//! | [`speed`] | **TTL 快取**（payload 放 native heap） | `RustKernel.nativeSpeed*` |
//! | [`store`] | **成個 APK 嘅記憶體與儲存**（持久化 KV，native heap ＋ 原子落碟） | `RustKernel.nativeStore*` |
//!
//! Kotlin 側嘅入口得一個 `object RustKernel` —— 所以「內存與儲存」唔再
//! 分開一個 `RustIndex`，而係**併入內核**，共用同一個 `System.loadLibrary`
//! 同一個 `available` 判斷。
//!
//! ## 安全設計
//!
//! * `.so` 唔存在／載入失敗 → Kotlin 側一律回退純 Kotlin 路徑，功能一樣
//! * panic **唔會**跨 FFI（`unwrap`／`expect`／越界索引一律唔用）

pub mod index;
pub mod kernel;
pub mod speed;
pub mod store;

#[cfg(feature = "android")]
pub mod jni;

// jni.rs 同對外嘅 API 直接用 `crate::Index`、`crate::resident_*` 等，
// 所以呢度重新導出，等搬模組唔使改嗰堆引用。
pub use index::*;
