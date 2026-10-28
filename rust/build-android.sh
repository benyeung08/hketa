#!/usr/bin/env bash
#
# 本地預編譯 Rust 內核 → 產出 .so 落 app/src/main/jniLibs/
#
# 用一次之後把 .so **commit 入 repo**，CI 就會偵測到「已有預編譯 .so」
# 而直接跳過現場編譯 —— 咁就 100% 保證每個 APK 都包咗 Rust 內核，
# 唔使再靠 CI 臨場裝 Rust toolchain / NDK（嗰條路最容易失敗）。
#
# 咁就解決咗「Rust 索引層係可選」呢條限制：
#   .so 喺 repo 入面 → CI 一定包到 → App 一定行 Rust 內核
#
# 用法：
#   ./rust/build-android.sh            # 編 release，四個 ABI
#   ./rust/build-android.sh --debug    # 編 debug（快啲，用嚟試）
#
# 前置：
#   1. rustup  https://rustup.rs
#   2. Android NDK（Android Studio → SDK Manager → SDK Tools → NDK）
#      或者設 ANDROID_NDK_HOME 指向 NDK 目錄
#
set -euo pipefail

cd "$(dirname "$0")"
HERE=$(pwd)
ROOT="$(cd .. && pwd)"
OUT="$ROOT/app/src/main/jniLibs"

PROFILE=release
CARGO_FLAGS=(--release --features android)
if [[ "${1:-}" == "--debug" ]]; then
  PROFILE=debug
  CARGO_FLAGS=(--features android)
fi

echo "== HKETA Rust 內核預編譯 =="
echo "   profile : $PROFILE"
echo "   輸出    : $OUT"

# ---- 1. 搵 NDK ----
NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_HOME:-}/ndk}"
if [[ -z "${NDK_HOME}" || ! -d "${NDK_HOME}" ]]; then
  # 未設環境變數 → 搵最新嗰個
  if [[ -d "${ANDROID_HOME:-}/ndk" ]]; then
    NDK_HOME=$(ls -1d "${ANDROID_HOME}/ndk"/* 2>/dev/null | sort -V | tail -1)
  fi
fi
if [[ -z "${NDK_HOME:-}" || ! -d "${NDK_HOME:-}" ]]; then
  echo "✗ 搵唔到 Android NDK。"
  echo "  Android Studio → SDK Manager → SDK Tools → 勾 NDK (Side by side)"
  echo "  或者：export ANDROID_NDK_HOME=/path/to/ndk/26.1.10909125"
  exit 1
fi
echo "   NDK     : $NDK_HOME"
export ANDROID_NDK_HOME="$NDK_HOME"
# NDK r27+ 先完整支援 16 KB page size 對齊
NDK_VER=$(basename "$NDK_HOME" | grep -oE '^[0-9]+' || true)
NDK_VER=${NDK_VER:-0}
if (( NDK_VER < 27 )); then echo "⚠ NDK $NDK_VER < 27，建議升 r27+"; fi
# 16 KB page size 建議 NDK r27+（r26 砌得到但未完整）
NDK_VER=$(basename "$NDK_HOME" | grep -oE '^[0-9]+' || echo 0)
if [[ -z "$NDK_VER" ]]; then NDK_VER=0; fi
if (( NDK_VER < 27 )); then echo "⚠ NDK $NDK_VER < 27，建議升 r27+"; fi

# Android 17（API 37）／16 KB page size：建議 NDK r27 或以上。
# r26 都砌到，但對 16 KB ELF 對齊嘅處理未完整。
NDK_VER=$(basename "$NDK_HOME" | grep -oE '^[0-9]+' || echo "?")
if [[ "$NDK_VER" =~ ^[0-9]+$ ]] && (( NDK_VER < 27 )); then
  echo "⚠ NDK 版本 $NDK_VER < 27 —— 16 KB 對齊可能唔完整，建議升級 NDK r27+"
fi

# ---- 2. 加 target ----
echo "== 加 Android target =="
rustup target add armv7-linux-androideabi aarch64-linux-android x86_64-linux-android

# ---- 3. cargo-ndk ----
if ! command -v cargo-ndk >/dev/null 2>&1; then
  echo "== 裝 cargo-ndk（第一次會由 source 砌，要幾分鐘）=="
  cargo install cargo-ndk --locked || cargo install cargo-ndk
fi

# ---- 4. 編譯 ----
echo "== cargo ndk build =="
mkdir -p "$OUT"
cargo ndk \
  -t armeabi-v7a \
  -t arm64-v8a \
  -t x86_64 \
  -o "$OUT" \
  build "${CARGO_FLAGS[@]}"

# ---- 5. 驗證 ----
echo "== 驗證 =="
FOUND=$(find "$OUT" -name 'libhketa_store.so' | sort)
if [[ -z "$FOUND" ]]; then
  echo "✗ 搵唔到 libhketa_store.so"
  exit 1
fi
echo "$FOUND" | while read -r f; do
  echo "  ✔ ${f#$ROOT/}  ($(stat -c%s "$f") bytes)"
done

# 16 KB page size 對齊（Android 15+ 支援，Android 17 常用，Play 2025-11-01 起要求）
READELF=$(find "$NDK_HOME" -name 'llvm-readelf' -type f 2>/dev/null | head -1)
if [[ -n "$READELF" ]]; then
  echo "== 16 KB 對齊（要見到 0x4000；0x1000 = 4 KB，16 KB 裝置會載唔入）=="
  for f in $FOUND; do
    A=$("$READELF" -l "$f" 2>/dev/null | awk '/LOAD/{print $NF}' | sort -u | tr '\n' ' ')
    echo "  $(basename "$(dirname "$f")"): ${A:-讀唔到}"
  done
fi

cat <<TIP

== 搞掂 ==

而家把 .so commit 入 repo，CI 以後就唔使現場編譯：

    git add -f app/src/main/jniLibs
    git commit -m "prebuilt Rust kernel (.so)"
    git push

留意 .gitignore 唔好 ignore 咗 .so —— 本 repo 已經加咗例外：

    !app/src/main/jniLibs/**/*.so

TIP
