#!/usr/bin/env bash
#
# 本地預編譯 Rust 內核 → 產出 .so 落 app/src/main/jniLibs/
#
# 用一次之後把 .so **commit 入 repo**，CI 就會偵測到「已有預編譯 .so」
# 而直接跳過現場編譯 —— 咁就 100% 保證每個 APK 都包咗 Rust 內核。
#
# 用法：
#   ./rust/build-android.sh            # 編 release，四個 ABI
#   ./rust/build-android.sh --debug    # 編 debug（快啲，用嚟試）
#
# 前置：
#   1. rustup  https://rustup.rs
#   2. Android NDK r27+（16 KB page 對齊硬要求）
#      設 ANDROID_NDK_HOME 或 ANDROID_HOME/ndk/<ver>
#   3. rust/Cargo.lock 必須存在並 commit（第一次跑會自動生成）
#
set -euo pipefail

cd "$(dirname "$0")"
HERE=$(pwd)
ROOT="$(cd .. && pwd)"
OUT="$ROOT/app/src/main/jniLibs"
LOCK="$HERE/Cargo.lock"

PROFILE=release
CARGO_FLAGS=(--release --features android --locked)
if [[ "${1:-}" == "--debug" ]]; then
  PROFILE=debug
  CARGO_FLAGS=(--features android --locked)
fi

# 四個 ABI，同 .cargo/config.toml 嘅 [target.*] 一一對應。
# 唔好淨編 3 個：universal APK 冇 abiFilters，少咗 x86 嗰個 ABI 嘅裝置
# 會 loadLibrary 失敗 → 靜默行純 Kotlin（唔崩，但用戶唔知慢咗）。
ABIS=(armeabi-v7a arm64-v8a x86 x86_64)
EXPECTED=${#ABIS[@]}

echo "== HKETA Rust 內核預編譯 =="
echo "   profile : $PROFILE"
echo "   ABI     : ${ABIS[*]}（$EXPECTED 個）"
echo "   輸出    : $OUT"

# ---- 1. 搵 NDK（r27+ 硬要求，唔再「警告但照出」）----
NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_HOME:-}/ndk}"
if [[ -z "${NDK_HOME}" || ! -d "${NDK_HOME}" ]]; then
  if [[ -d "${ANDROID_HOME:-}/ndk" ]]; then
    NDK_HOME=$(ls -1d "${ANDROID_HOME}/ndk"/* 2>/dev/null | sort -V | tail -1)
  fi
fi
if [[ -z "${NDK_HOME:-}" || ! -d "${NDK_HOME:-}" ]]; then
  echo "✗ 搵唔到 Android NDK。Android Studio → SDK Manager → SDK Tools → NDK (Side by side)"
  echo "  或者：export ANDROID_NDK_HOME=/path/to/ndk/27.2.12479018"
  exit 1
fi
NDK_VER=$(basename "$NDK_HOME" | grep -oE '^[0-9]+' || echo 0)
NDK_VER=${NDK_VER:-0}
if (( NDK_VER < 27 )); then
  echo "✗ NDK major $NDK_VER < 27 —— 16 KB ELF 對齊未完整，砌出嚟嘅 .so 喺 Android 17 會 dlopen 失敗。"
  echo "  請裝 ndk;27.2.12479018 或更新。拒絕出一個「編譯成功但載唔入」嘅檔。"
  exit 1
fi
echo "   NDK     : $NDK_HOME（major $NDK_VER ✔）"
export ANDROID_NDK_HOME="$NDK_HOME"

# ---- 2. Cargo.lock（🔴 供應鏈：冇 lock = 每次解析到最高兼容版本）----
if [[ ! -f "$LOCK" ]]; then
  echo "== rust/Cargo.lock 唔存在，首次生成 =="
  cargo generate-lockfile
  echo "⚠ 已生成 Cargo.lock。請一定要 commit 佢："
  echo "    git add rust/Cargo.lock && git commit -m 'lock rust deps'"
  echo "  CI 用 --locked，冇呢個檔會直接紅，唔會再靜靜重新解析依賴。"
fi
echo "   lock    : $(grep -c '^\[\[package\]\]' "$LOCK") 個套件"

# ---- 3. 加 target ----
echo "== 加 Android target =="
rustup target add \
  armv7-linux-androideabi aarch64-linux-android \
  i686-linux-android x86_64-linux-android

# ---- 4. cargo-ndk ----
if ! command -v cargo-ndk >/dev/null 2>&1; then
  echo "== 裝 cargo-ndk（第一次會由 source 砌，要幾分鐘）=="
  cargo install cargo-ndk --locked
fi

# ---- 5. 可選：RustSec 審計 ----
if command -v cargo-audit >/dev/null 2>&1; then
  echo "== cargo audit =="
  cargo audit || echo "⚠ RustSec advisory 命中（唔阻塞本地 build，發布前要處理）"
else
  echo "   （冇裝 cargo-audit；CI 會跑。想本機驗：cargo install cargo-audit --locked）"
fi

# ---- 6. 編譯 ----
echo "== cargo ndk build（--locked）=="
mkdir -p "$OUT"
ARGS=()
for a in "${ABIS[@]}"; do ARGS+=(-t "$a"); done
cargo ndk "${ARGS[@]}" -o "$OUT" build "${CARGO_FLAGS[@]}"

# ---- 7. 驗證：四個 ABI 一個都唔可以少 ----
echo "== 驗證 .so =="
FOUND=$(find "$OUT" -name 'libhketa_store.so' | sort)
COUNT=$(echo "$FOUND" | grep -c . || true)
if [[ "$COUNT" -lt "$EXPECTED" ]]; then
  echo "✗ 預期 $EXPECTED 個 ABI，實際得 $COUNT 個。唔可以當成功。"
  echo "$FOUND"
  exit 1
fi
echo "$FOUND" | while read -r f; do
  echo "  ✔ ${f#$ROOT/}  ($(stat -c%s "$f") bytes)"
done

# ---- 8. 16 KB page 對齊：唔合格就 exit 1，唔好只印警告 ----
READELF=$(find "$NDK_HOME" -name 'llvm-readelf' -type f 2>/dev/null | head -1)
if [[ -z "$READELF" ]]; then
  echo "✗ 搵唔到 llvm-readelf，冇得驗 16 KB 對齊 —— 拒絕當通過"
  exit 1
fi
echo "== 16 KB 對齊（要見到 0x4000；0x1000 = 4 KB 會載唔入）=="
BAD=0
for f in $FOUND; do
  # llvm-readelf -l 嘅 LOAD 行，最後一欄就係 Align。
  # 唔好用 $(i-1)=="Align" ——「Align」只喺表頭出現，資料行冇，會恆空 → 假陽性。
  A=$("$READELF" -l "$f" 2>/dev/null | awk '/LOAD/{print $NF}' | sort -u | tr '\n' ' ')
  echo "  $(basename "$(dirname "$f")"): ${A:-（讀唔到）}"
  if [[ -z "${A// /}" ]] || echo "$A" | grep -q '0x1000'; then
    echo "    ✗ 呢個 .so 未做 16 KB 對齊"
    BAD=1
  fi
done
if (( BAD )); then
  echo "✗ 有 .so 未做 16 KB 對齊。確認 rust/.cargo/config.toml 有冇上傳、NDK 係咪 r27+"
  exit 1
fi
echo "   16 KB 對齊檢查通過 ✔"

cat <<TIP

== 搞掂 ==

而家把 lock + .so commit 入 repo，CI 以後就唔使現場編譯：

    git add rust/Cargo.lock
    git add -f app/src/main/jniLibs
    git commit -m "lock deps + prebuilt Rust kernel (.so)"
    git push

.gitignore 已加顯式例外，唔會漏咗 .so：

    !app/src/main/jniLibs/**/*.so

⚠ 升級依賴嘅正確姿勢（唔好手改 Cargo.lock）：
    cd rust && cargo update && cargo audit
    ./rust/build-android.sh          # 重新出四個 .so
    git add rust/Cargo.lock rust/Cargo.toml app/src/main/jniLibs

TIP
