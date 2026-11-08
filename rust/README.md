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

## 安全設計（第三輪修訂）

* **一定會回退**：`.so` 唔存在／加載失敗 → `RustIndex.available == false` →
  `IndexStore` 行返原本嘅 JSON 路徑，**功能完全一樣**
* **JNI 入口唔用 `unwrap()`／`expect()`／`[]`**：`jni.rs`／`kernel.rs` 有做到。
  但 `index.rs::read_from()` 解析二進制檔時仍有 `try_into().unwrap()` 同切片
  `rest[o-4..o]` —— 檔被截斷／損壞會 panic。而 `Cargo.toml` 設咗
  `panic = "abort"`，**panic 唔會變 Java exception，會直接殺 process**。
  建議喺每個 `extern "system"` 入口包 `std::panic::catch_unwind` +
  `env.throw_new()`（見第三輪報告；未改，因為改 Rust 要實機編譯驗證）。
* **CI 唔再靜默**：之前 `Build Rust kernel`／`Verify 16KB`／`Verify .so`
  三個 step 全部 `continue-on-error: true`，連一個 .so 都冇照樣出綠色 APK。
  而家三個都改咗 hard fail，NDK < 27、Cargo.lock 缺失、16 KB 對齊唔合格、
  缺任何一個 ABI 都會直接紅。
* **依賴鎖定**：`rust/Cargo.lock` 必須 commit；CI 用 `--locked`，唔會再出現
  `Locking 37 packages to highest compatible versions`。另加 `cargo audit`。

即係：本地 build 冇裝 Rust toolchain 仍可（用已 commit 嘅預編譯 .so），
但**發布用嘅 release 包一定要通過以上檢查**，唔可以靜靜退化。

## 點樣本地構建

**最簡單：直接跑腳本**（會自動 generate-lockfile、裝 cargo-ndk、編四個 ABI、
驗 16 KB 對齊，任何一關唔過都 `exit 1`）：

```bash
./rust/build-android.sh
git add rust/Cargo.lock
git add -f app/src/main/jniLibs
git commit -m "lock deps + prebuilt Rust kernel"
```

等價嘅手動指令（四個 ABI，**一定要 `--locked`**）：

```bash
cd rust
cargo generate-lockfile                       # 第一次；之後 commit 佢
rustup target add armv7-linux-androideabi aarch64-linux-android \
                  i686-linux-android x86_64-linux-android
cargo install cargo-ndk --locked
cargo ndk -t armeabi-v7a -t arm64-v8a -t x86 -t x86_64 \
  -o ../app/src/main/jniLibs build --release --features android --locked
```

四個 ABI 要同 `rust/.cargo/config.toml` 嘅 `[target.*]` 一致。
AGP 會自動打包 `app/src/main/jniLibs/{abi}/*.so`，**唔使改 `build.gradle.kts`**
（`AndroidManifest.xml` 已有 `android:extractNativeLibs="false"`）。

`rust/Cargo.lock` 亦可以由 CI 嘅 `cargo-lock` artifact 下載（只喺 build 失敗時上傳），
但**唔好只靠 CI**：本機 generate 一次再 commit 先係可重現嘅做法。

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
* **Rust 程式碼已喺 CI 編譯成功**：三個 ABI（armeabi-v7a／arm64-v8a／x86_64）
  都 `Finished release profile`、零 warning（見 `cargo-build.log`）。
  但舊設定只編 3 個 ABI，已補埋 `x86`；四個都要重新出過 .so
* **`cargo-build.log` 出現 `Locking 37 packages to highest compatible versions`**
  = 當次冇 Cargo.lock。已改為 `--locked` + hard fail，唔會再漂移
* `jni v0.21.1`（上游已有 0.22.4）、`syn v2` + `syn v3` 雙份、
  `jni-sys v0.3` + `v0.4` 雙份、`zmij v1.0.23` 等較新 crate：
  加咗 `cargo audit`，但**未做人手 advisory 覆核**
* Kotlin 側仍然持有完整 `IndexData`（UI 要用），所以慳到嘅主要係
  **磁碟 ＋ 字串實例 ＋ 載入時間**，唔係全部 heap
