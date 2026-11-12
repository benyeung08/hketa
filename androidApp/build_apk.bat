@echo off
REM HK ETA Android APK Builder (Windows)
REM 支援 Android 7.0 (API 24) ~ Android 17 (API 37)

chcp 65001 >nul
echo 🚌 HK ETA - Android APK Builder
echo ================================
echo.

REM 檢查 JDK
echo ☕ 檢查 JDK 版本...
java -version 2>&1 | findstr /i "version" >nul
if errorlevel 1 (
    echo ❌ 未找到 Java，請先安裝 JDK 17+
    pause
    exit /b 1
)

REM 檢查 ANDROID_HOME
if "%ANDROID_HOME%"=="" (
    if "%ANDROID_SDK_ROOT%"=="" (
        echo ⚠️ 未設定 ANDROID_HOME 或 ANDROID_SDK_ROOT
        echo    請先安裝 Android SDK 並設定環境變數
        echo    需要: platforms;android-37, build-tools;37.0.0, platform-tools
        pause
        exit /b 1
    )
)
echo ✅ Android SDK OK
echo.

REM 建置 Debug APK
echo 🔨 開始建置 Debug APK...
echo    compileSdk: 37 (Android 17)
echo    minSdk: 24 (Android 7.0)
echo.
call gradlew.bat assembleDebug --no-daemon

if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo.
    echo ✅ APK 建置成功！
    echo    📁 路徑: %cd%\app\build\outputs\apk\debug\app-debug.apk
) else (
    echo.
    echo ❌ APK 建置失敗，未找到輸出檔案
    pause
    exit /b 1
)
