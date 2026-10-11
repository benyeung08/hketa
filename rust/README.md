# HKETA Rust 索引庫（`rust/`）

> **可選加速層** —— 冇咗佢 App 完全正常，只係享受唔到慳位同加速。

## 點解

索引規模：**2099 條路線 / 7250 個車站 / 63150 筆路線-車站對應**。

純 Kotlin 用 JSON 存會有兩個問題：

1. **字串重複**：`kotlinx.serialization` 反序列化 JSON 時**唔會**做 string interning，
   所以同一個站名出現 100 次就有 100 個獨立 `String` 實例
2. **物件開銷**：每筆記錄都係一個 JVM 物件（16 bytes header）＋ 幾個 `String` 引用

## 做咗啲咩

* **字串去重（interning）**：全索引淨係得一份字串表，記錄用 `u32` 指向佢
* **緊湊二進制格式** ＋ varint（LEB128）
* **查詢放喺 Rust 側**：附近車站（bounding box 預篩 ＋ haversine）、車站→路線索引

## 實測（用真實 7.20 MB 索引）

| | 純 Kotlin / JSON | Rust |
|---|---|---|
| 磁碟大小 | 7.20 MB | **1.23 MB**（17%） |
| 字串條數 | 每筆獨立實例 | **25771 條共用** |
| round-trip | — | 抽樣全對（含 lat/lon 1e-12 精度） |
| 附近車站 | — | 同 Kotlin 版結果**完全一致**（尖沙咀 9 站／中環 11 站／屯門 43 站） |
| routes_at_stop | — | 抽樣 12 站全對 |

> 大小同查詢正確性係用 Python 原型（同一套格式／算法）喺真實索引上實測出嚟嘅。
> 記憶體數字係按資料結構估算，未喺真機量過。

## 檔案格式

```text
"HKIX"           4 bytes  magic
version          u32 LE
built_at         i64 LE
strings          u32 count, then for each: u16 len + UTF-8 bytes
routes           u32 count, then for each:
                   op u8, 7 × varint (route/routeId/orig/origEn/dest/destEn/serviceType), bound u8
stops            u32 count, then for each:
                   op u8, 3 × varint (id/name/nameEn), lat f64 LE, lon f64 LE
route_stops      u32 count, then for each:
                   op u8, 4 × varint (route/serviceType/stopId/routeId), bound u8, seq u16 LE
```

## 跨 JNI 邊界：**唔用 JSON**

7 MB JSON 解析一次要幾百毫秒，咁就冇咗用 Rust 嘅意義。改為：

* `nativeStrings()` 一次過攞去重後嘅字串表
* 其餘全部係扁平化 `IntArray` / `DoubleArray`
* Kotlin 側用「字串表 ＋ 索引」砌物件 → **所有記錄共用同一個 `String` 實例**

## 安全設計

* **一定會回退**：`.so` 唔存在／加載失敗 → `RustIndex.available == false` →
  `IndexStore` 行返原本嘅 JSON 路徑，**功能完全一樣**
* **panic 唔會跨 FFI**：JNI 層一律唔用 `unwrap()`／`[]`，任何錯誤安靜返回空值
* **CI 失敗唔會紅**：Rust 步驟設咗 `continue-on-error: true`

即係：本地 build 冇裝 Rust toolchain 都冇問題。

## 點樣本地構建

```bash
rustup target add armv7-linux-androideabi aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk
cd rust
cargo ndk -t armeabi-v7a -t arm64-v8a -t x86_64 \
  -o ../app/src/main/jniLibs build --release --features android
```

AGP 會自動打包 `app/src/main/jniLibs/{abi}/*.so`，**唔使改 `build.gradle.kts`**。

## 點樣確認生效

設定頁 → 「索引統計」下面會顯示：

```
索引格式：Rust / binary     ← Rust 生效
索引格式：Kotlin / JSON     ← 行緊回退
```

## 單元測試（唔使 Android）

```bash
cd rust
cargo test
```

會驗 round-trip（含 lat/lon 精度）、附近車站、車站→路線。

## 已知限制

* **記憶體數字係估算**：未喺真機量 RSS
* **Rust 程式碼未經 CI 編譯驗證過**（寫呢份嘢嗰陣沙盒冇 Rust toolchain）——
  第一次 CI 跑要留意 `Build Rust index store (optional)` 嗰步嘅輸出
* Kotlin 側仍然持有完整 `IndexData`（UI 要用），所以慳到嘅主要係
  **磁碟 ＋ 字串實例 ＋ 載入時間**，唔係全部 heap
