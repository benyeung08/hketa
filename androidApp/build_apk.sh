#!/bin/bash
# HK ETA Android APK 建置腳本
# 支援 Android 7.0 (API 24) ~ Android 16 (API 36)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🚌 HK ETA - Android APK Builder"
echo "================================"
echo ""

# 檢查 JDK
echo "☕ 檢查 JDK 版本..."
if ! command -v java &> /dev/null; then
    echo "❌ 未找到 Java，請先安裝 JDK 17+"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ 需要 JDK 17 或以上，當前版本: $JAVA_VERSION"
    exit 1
fi
echo "✅ JDK $JAVA_VERSION OK"
echo ""

# 檢查 ANDROID_HOME
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "⚠️ 未設定 ANDROID_HOME 或 ANDROID_SDK_ROOT"
    echo "   請先安裝 Android SDK 並設定環境變數"
    echo "   需要: platforms;android-36, build-tools;36.0.0, platform-tools"
    exit 1
fi
echo "✅ Android SDK OK"
echo ""

# 賦權 gradlew
chmod +x gradlew 2>/dev/null || true

# 建置 Debug APK
echo "🔨 開始建置 Debug APK..."
echo "   compileSdk: 36 (Android 16)"
echo "   minSdk: 24 (Android 7.0)"
echo ""
./gradlew assembleDebug --no-daemon

# 檢查輸出
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
    echo ""
    echo "✅ APK 建置成功！"
    echo "   📁 路徑: $SCRIPT_DIR/$APK_PATH"
    echo "   📦 大小: $SIZE"
else
    echo ""
    echo "❌ APK 建置失敗，未找到輸出檔案"
    exit 1
fi
