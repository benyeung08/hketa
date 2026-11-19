#!/usr/bin/env python3
"""Preflight: verifies that every file needed by the build actually exists.

LOCAL / OPTIONAL helper. The CI workflow now carries an equivalent, fully
inlined copy of this check (marker: SELF_CONTAINED_PREFLIGHT) so that a missing
scripts/ folder can no longer hide the real list of missing files. Keep this
file for running the same check on your machine before pushing:

    python3 scripts/preflight.py && python3 scripts/check_api_fields.py

Outputs GitHub Actions ::error:: annotations and exits 1 on any problem.
Only the Python standard library is used.
"""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

REQUIRED = [
    # Build system
    ".github/workflows/build-apk.yml",
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle.properties",
    "gradlew",
    "gradle/wrapper/gradle-wrapper.properties",
    "app/build.gradle.kts",
    "app/proguard-rules.pro",
    "app/hketa-debug.keystore",          # binary, fixed signing identity
    "app/icon-source.png",                # binary, logo artwork
    "app/src/main/AndroidManifest.xml",
    "app/src/main/ic_launcher-playstore.png",
    # Scripts — ALL six are required. fix_drift.py and show_build_error.py
    # were accidentally omitted from this list before: preflight let them
    # through, so a stale/missing one only surfaced at static-check line 178.
    "scripts/check_api_fields.py",
    "scripts/preflight.py",
    "scripts/pin_signing_fingerprint.py",
    "scripts/make_launcher_icons.py",
    "scripts/fix_drift.py",
    "scripts/show_build_error.py",
    "scripts/verify_release_version.py",
    # PUBLISHED_RELEASE_BACKFILL
    "scripts/published_release.py",
    # Resources
    "app/src/main/res/xml/file_paths.xml",
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values/colors.xml",
    "app/src/main/res/values-night/colors.xml",
    "app/src/main/res/layout/activity_main.xml",
    "app/src/main/res/layout/activity_settings.xml",
    # 更新應用程式：深色版本膠囊
    "app/src/main/res/layout/update_pill.xml",
    "app/src/main/res/drawable/bg_update_pill.xml",
    "app/src/main/res/drawable/ic_pill_refresh.xml",
    "app/src/main/res/drawable/ic_pill_history.xml",
    "app/src/main/res/drawable/ic_pill_copy.xml",
    # 定位附近路線嘅距離（設定頁「50 ▼」）
    "app/src/main/res/layout/radius_picker.xml",
    "app/src/main/res/drawable/ic_pill_arrow_down.xml",
    "app/src/main/java/hk/eta/app/ui/NearbyRadiusPrefs.kt",
    # ⟳ ETA 自動刷新
    "app/src/main/java/hk/eta/app/ui/EtaRefreshPrefs.kt",
    "app/src/main/res/layout/eta_refresh_picker.xml",
    # 懸浮液態玻璃導航欄 + 設定頁免責聲明卡
    "app/src/main/java/hk/eta/app/ui/NavGlass.kt",
    "app/src/main/java/hk/eta/app/ui/NavGlassPrefs.kt",
    "app/src/main/res/layout/disclaimer_card.xml",
    "app/src/main/res/drawable/bg_disclaimer.xml",
    "app/src/main/res/drawable/bg_nav_glass.xml",
    "app/src/main/res/drawable/bg_nav_glass_dark.xml",
    "app/src/main/res/drawable/bg_nav_glass_contrast.xml",
    "app/src/main/res/drawable/bg_tab_glass_selected.xml",
    # 主頁附近路線卡片 → 路線詳情（還原截圖，無地圖）
    "app/src/main/java/hk/eta/app/ui/RouteDetailActivity.kt",
    "app/src/main/res/layout/activity_route_detail.xml",
    "app/src/main/res/layout/item_detail_stop.xml",
    "app/src/main/res/drawable/bg_detail_chip.xml",
    "app/src/main/res/drawable/bg_detail_dot_on.xml",
    "app/src/main/res/drawable/bg_detail_dot_off.xml",
    "app/src/main/res/drawable/bg_detail_action_circle.xml",
    "app/src/main/res/drawable/bg_stop_bubble.xml",
    "app/src/main/res/drawable/bg_no_map.xml",
    # APP1933-style shell
    "app/src/main/res/layout/item_route_card.xml",
    "app/src/main/res/layout/item_favorite.xml",
    # APP1933 搜尋頁：路線結果列 + 自訂鍵盤
    "app/src/main/res/layout/item_route_result.xml",
    "app/src/main/res/layout/keypad.xml",
    "app/src/main/res/drawable/bg_route_badge.xml",
    "app/src/main/res/drawable/bg_trip_chip.xml",
    "app/src/main/res/drawable/bg_card.xml",
    "app/src/main/res/drawable/bg_input.xml",
    "app/src/main/res/drawable/bg_settings.xml",
    "app/src/main/res/drawable/bg_key.xml",
    "app/src/main/res/drawable/bg_key_circle.xml",
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
]

for density in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
    for name in ("ic_launcher.png", "ic_launcher_round.png",
                 "ic_launcher_background.png", "ic_launcher_foreground.png"):
        REQUIRED.append(f"app/src/main/res/mipmap-{density}/{name}")

# Every Java / Kotlin source we ship.
REQUIRED += [
    "app/src/main/java/hk/eta/app/HkEtaApp.kt",
    "app/src/main/java/hk/eta/app/api/HttpJson.java",
    "app/src/main/java/hk/eta/app/api/EtaModels.java",
    "app/src/main/java/hk/eta/app/api/EtaRepository.java",
    "app/src/main/java/hk/eta/app/api/GeoStops.java",
    "app/src/main/java/hk/eta/app/api/MtrCatalog.java",
    "app/src/main/java/hk/eta/app/api/RouteDataStore.java",
    "app/src/main/java/hk/eta/app/api/RouteSearch.java",
    # JOINT_KMB_CTB: catalogue-driven joint-route detection
    "app/src/main/java/hk/eta/app/api/JointRoutes.java",
    # CIRCULAR_ROUTES: operator-specific circular lines + stop-name audit
    "app/src/main/java/hk/eta/app/api/CircularRoutes.java",
    "app/src/main/java/hk/eta/app/api/AppUpdate.java",
    "app/src/main/java/hk/eta/app/api/TimeUtils.java",
    "app/src/main/java/hk/eta/app/ui/MainActivity.kt",
    "app/src/main/java/hk/eta/app/ui/SettingsActivity.kt",
    "app/src/main/java/hk/eta/app/ui/EtaViewModel.kt",
    "app/src/main/java/hk/eta/app/ui/Adapters.kt",
    "app/src/main/java/hk/eta/app/ui/App1933Format.kt",
    "app/src/main/java/hk/eta/app/ui/CardAdapters.kt",
    "app/src/main/java/hk/eta/app/ui/Favorites.kt",
    "app/src/main/java/hk/eta/app/ui/LocationHelper.kt",
    "app/src/main/java/hk/eta/app/ui/RouteDataPrefs.kt",
    "app/src/main/java/hk/eta/app/ui/AppUpdatePrefs.kt",
    "app/src/main/java/hk/eta/app/ui/ApkInstaller.kt",
    "app/src/main/java/hk/eta/app/ui/ApkSignature.kt",
]


def main() -> int:
    problems = []

    # Most common "Add files via upload" mistake: the outer hketa/ folder was
    # dragged in too, so everything sits one level too deep.
    if (ROOT / "hketa/app/build.gradle.kts").exists() \
            and not (ROOT / "app/build.gradle.kts").exists():
        problems.append(
            "偵測到多了一層 hketa/ 資料夾：請把 hketa/ *裡面* 的檔案直接放喺 repo 根目錄，"
            "唔好連外層資料夾一併上傳。"
        )

    missing = [rel for rel in REQUIRED if not (ROOT / rel).is_file()]
    empty = [rel for rel in REQUIRED
             if (ROOT / rel).is_file() and (ROOT / rel).stat().st_size == 0]

    if missing:
        problems.append(f"缺少 {len(missing)} 個檔案（最常見係 binary 檔冇上傳）：")
        for rel in missing[:25]:
            problems.append(f"  - {rel}")
        if len(missing) > 25:
            problems.append(f"  ... 另外 {len(missing) - 25} 個")
    if empty:
        problems.append("以下檔案存在但係空檔：")
        for rel in empty[:15]:
            problems.append(f"  - {rel}")

    # A partially uploaded repo often has the workflow but no sources at all.
    java_kt = list((ROOT / "app/src/main/java").rglob("*.java")) + \
              list((ROOT / "app/src/main/java").rglob("*.kt"))
    if len(java_kt) < 15:
        problems.append(
            f"原始碼數量異常（得 {len(java_kt)} 個 .java/.kt，預期 >= 15），"
            "可能只上傳了部分檔案。"
        )

    # Content drift: the file exists but is still an OLD version. This is what
    # happened when the new check_api_fields.py was uploaded together with an
    # old .gitignore and old AndroidManifest.xml (2 FAILs at line ~140).
    drift = [
        (".gitignore", "/app/hketa-release.keystore",
         ".gitignore 缺 /app/hketa-release.keystore",
         "加一行：/app/hketa-release.keystore"),
        ("app/src/main/AndroidManifest.xml",
         'android:roundIcon="@mipmap/ic_launcher_round"',
         "AndroidManifest.xml roundIcon 未指向 ic_launcher_round",
         '改成：android:roundIcon="@mipmap/ic_launcher_round"'),
        ("app/build.gradle.kts", "hketaDev",
         "app/build.gradle.kts 缺固定 signingConfigs",
         "加入 signingConfigs { create(\"hketaDev\") { ... } } 並讓 debug/release 使用"),
        ("app/src/main/res/values/strings.xml",
         '<string name="default_github_repo">benyeung08/hketa</string>',
         "strings.xml 缺內建 default_github_repo",
         '改成：<string name="default_github_repo">benyeung08/hketa</string>'),
        # 城巴站名 / 403 後備修復
        ("app/src/main/java/hk/eta/app/api/HttpJson.java",
         "USER_AGENT_BROWSER",
         "HttpJson.java 仍係舊版（缺 403／HTML 重試）",
         "由完整 zip 覆蓋 HttpJson.java"),
        ("app/src/main/java/hk/eta/app/api/RouteDataStore.java",
         "stopCacheStatus",
         "RouteDataStore.java 仍係舊版（仍打唔存在嘅城巴全量站點 URL）",
         "由完整 zip 覆蓋 RouteDataStore.java"),
        ("app/src/main/java/hk/eta/app/api/EtaRepository.java",
         "fetchStopName",
         "EtaRepository.java 仍係舊版（城巴會繼續顯示純站號）",
         "由完整 zip 覆蓋 EtaRepository.java"),
        ("app/src/main/java/hk/eta/app/ui/SettingsActivity.kt",
         "s.skipped",
         "SettingsActivity.kt 仍係舊版（選用來源會誤報失敗）",
         "由完整 zip 覆蓋 SettingsActivity.kt"),
        # APP1933 全螢幕搜尋頁
        ("app/src/main/res/layout/activity_main.xml",
         'android:id="@+id/routeInput"',
         "activity_main.xml 仍係舊搜尋頁（缺紅 bar／路線結果／鍵盤）",
         "由完整 zip 覆蓋 activity_main.xml（同時要傳 item_route_result.xml、keypad.xml）"),
        ("app/src/main/java/hk/eta/app/ui/MainActivity.kt",
         "bindKeypad",
         "MainActivity.kt 仍係舊版（缺自訂鍵盤與結果模式）",
         "由完整 zip 覆蓋 MainActivity.kt"),
        ("app/src/main/java/hk/eta/app/ui/EtaViewModel.kt",
         "fun searchRoutes",
         "EtaViewModel.kt 仍係舊版（缺即時路線搜尋）",
         "由完整 zip 覆蓋 EtaViewModel.kt"),
        ("app/src/main/java/hk/eta/app/ui/CardAdapters.kt",
         "class RouteResultAdapter",
         "CardAdapters.kt 仍係舊版（缺路線結果 adapter）",
         "由完整 zip 覆蓋 CardAdapters.kt"),
        # 自動定位：移除手動定位掣 + 先授權後自動定位
        ("app/src/main/res/layout/activity_main.xml",
         'android:id="@+id/permissionHint"',
         "activity_main.xml 仍係舊版（仍有 btnNearby／缺權限提示）",
         "由完整 zip 覆蓋 activity_main.xml（移除 btnNearby，加 permissionHint）"),
        ("app/src/main/java/hk/eta/app/ui/MainActivity.kt",
         "fun autoLocateIfAllowed",
         "MainActivity.kt 仍係舊版（仍撳掣定位／未先攞權限就自動定位）",
         "由完整 zip 覆蓋 MainActivity.kt"),
        ("app/src/main/java/hk/eta/app/ui/EtaViewModel.kt",
         "fun awaitLocationPermission",
         "EtaViewModel.kt 仍係舊版（未授權都會查附近路線）",
         "由完整 zip 覆蓋 EtaViewModel.kt"),
        ("app/src/main/java/hk/eta/app/ui/LocationHelper.kt",
         "fun hasPermission(context: Context): Boolean",
         "LocationHelper.kt 仍係舊版（權限判斷未公開共用）",
         "由完整 zip 覆蓋 LocationHelper.kt"),
        ("app/src/main/res/values/strings.xml",
         "location_permission_hint",
         "strings.xml 仍係舊版（仍有「重新定位」字串）",
         "由完整 zip 覆蓋 strings.xml"),
        # 更新應用程式：深色版本膠囊
        ("app/src/main/res/layout/activity_settings.xml",
         "android:id=\"@+id/advancedAppUpdate\"",
         "activity_settings.xml 仍係舊版（缺版本膠囊／進階抽屜）",
         "由完整 zip 覆蓋 activity_settings.xml（同時要傳 update_pill.xml 與 3 個 ic_pill_*.xml）"),
        ("app/src/main/java/hk/eta/app/ui/SettingsActivity.kt",
         "fun copyVersionInfo",
         "SettingsActivity.kt 仍係舊版（缺 ⟳／🕐／⧉ 三個捷徑）",
         "由完整 zip 覆蓋 SettingsActivity.kt"),
        ("app/src/main/res/values/strings.xml",
         "pill_advanced_open",
         "strings.xml 仍係舊版（缺版本膠囊字串）",
         "由完整 zip 覆蓋 strings.xml"),
        ("app/src/main/res/values/colors.xml",
         "pill_bg",
         "colors.xml 仍係舊版（缺版本膠囊配色）",
         "由完整 zip 覆蓋 colors.xml"),
        # 主頁移除刷新掣 / 半徑 Spinner；距離搬入設定頁
        ("app/src/main/res/layout/activity_main.xml",
         "HOME_CLEAN",
         "activity_main.xml 仍係舊版（仍有 btnRefresh／radiusSpinner）",
         "由完整 zip 覆蓋 activity_main.xml（主頁只留下拉刷新）"),
        ("app/src/main/res/layout/activity_settings.xml",
         "NEARBY_RADIUS_SECTION",
         "activity_settings.xml 仍係舊版（缺「定位附近路線嘅距離」區塊）",
         "由完整 zip 覆蓋 activity_settings.xml（同時要傳 radius_picker.xml）"),
        ("app/src/main/java/hk/eta/app/ui/SettingsActivity.kt",
         "fun setupRadiusSection",
         "SettingsActivity.kt 仍係舊版（缺距離選擇器對話框）",
         "由完整 zip 覆蓋 SettingsActivity.kt"),
        ("app/src/main/java/hk/eta/app/ui/MainActivity.kt",
         "NearbyRadiusPrefs.radiusMeters(this)",
         "MainActivity.kt 仍係舊版（半徑仍由主頁 Spinner 讀）",
         "由完整 zip 覆蓋 MainActivity.kt"),
        ("app/src/main/res/values/strings.xml",
         "nearby_radius_section",
         "strings.xml 仍係舊版（缺距離選擇器字串／仍有 ↻ 刷新）",
         "由完整 zip 覆蓋 strings.xml"),
        # 懸浮液態玻璃導航欄 + 免責聲明卡搬入設定頁
        ("app/src/main/res/layout/activity_main.xml",
         "NAV_GEOMETRY",
         "activity_main.xml 仍係舊版（導航欄未改成新幾何／仍叫「更多」）",
         "由完整 zip 覆蓋 activity_main.xml（同時要傳 4 個 bg_nav_glass*.xml）"),
        ("app/src/main/res/layout/activity_settings.xml",
         "DISCLAIMER_CARD",
         "activity_settings.xml 仍係舊版（缺深色免責聲明卡／玻璃外觀區塊）",
         "由完整 zip 覆蓋 activity_settings.xml（同時要傳 disclaimer_card.xml）"),
        ("app/src/main/java/hk/eta/app/ui/MainActivity.kt",
         "NavGlass.apply(this, bottomTabs)",
         "MainActivity.kt 仍係舊版（導航欄未跟隨系統外觀）",
         "由完整 zip 覆蓋 MainActivity.kt"),
        ("app/src/main/java/hk/eta/app/ui/SettingsActivity.kt",
         "fun setupNavGlassSection",
         "SettingsActivity.kt 仍係舊版（缺導航欄外觀開關）",
         "由完整 zip 覆蓋 SettingsActivity.kt"),
        ("app/src/main/res/values/strings.xml",
         "nav_glass_section",
         "strings.xml 仍係舊版（缺玻璃外觀／兩段免責聲明字串）",
         "由完整 zip 覆蓋 strings.xml"),
        # 主頁卡片 → 路線詳情（無地圖）
        ("app/src/main/AndroidManifest.xml",
         'android:name=".ui.RouteDetailActivity"',
         "AndroidManifest.xml 仍係舊版（未註冊路線詳情 Activity）",
         "由完整 zip 覆蓋 AndroidManifest.xml"),
        ("app/src/main/java/hk/eta/app/ui/MainActivity.kt",
         "Intent(this, RouteDetailActivity::class.java)",
         "MainActivity.kt 仍係舊版（點主頁卡片仍跳去搜尋分頁）",
         "由完整 zip 覆蓋 MainActivity.kt（同時要傳 RouteDetailActivity.kt）"),
        ("app/src/main/res/values/strings.xml",
         "detail_footer_honest",
         "strings.xml 仍係舊版（缺路線詳情字串）",
         "由完整 zip 覆蓋 strings.xml"),
        ("app/src/main/res/values/colors.xml",
         "detail_minutes",
         "colors.xml 仍係舊版（缺三班次分鐘藍色）",
         "由完整 zip 覆蓋 colors.xml"),
        # 搜尋主頁預載 + 搜尋結果列開路線詳情
        ("app/src/main/java/hk/eta/app/api/RouteSearch.java",
         "public static List<Result> preload()",
         "RouteSearch.java 仍係舊版（缺搜尋主頁預載官方路線）",
         "由完整 zip 覆蓋 RouteSearch.java"),
        ("app/src/main/java/hk/eta/app/ui/EtaViewModel.kt",
         "RouteSearch.preload()",
         "EtaViewModel.kt 仍係舊版（空白輸入仍清空搜尋結果）",
         "由完整 zip 覆蓋 EtaViewModel.kt"),
        ("app/src/main/java/hk/eta/app/ui/MainActivity.kt",
         "RouteDetailActivity.EXTRA_BOUND, result.bound",
         "MainActivity.kt 仍係舊版（撳搜尋結果仍跳站內站序、未預載）",
         "由完整 zip 覆蓋 MainActivity.kt"),
        ("app/src/main/java/hk/eta/app/ui/RouteDetailActivity.kt",
         "EXTRA_BOUND",
         "RouteDetailActivity.kt 仍係舊版（未接收搜尋列嘅方向）",
         "由完整 zip 覆蓋 RouteDetailActivity.kt"),
        ("app/src/main/res/values/strings.xml",
         "search_preload_empty",
         "strings.xml 仍係舊版（缺預載提示字串）",
         "由完整 zip 覆蓋 strings.xml"),
        # ⟳ ETA 自動刷新
        ("app/src/main/java/hk/eta/app/ui/EtaViewModel.kt",
         "AUTO_REFRESH_ETA",
         "EtaViewModel.kt 仍係舊版（ETA 只硬編碼 30 秒刷新已選車站）",
         "由完整 zip 覆蓋 EtaViewModel.kt（同時要傳 EtaRefreshPrefs.kt）"),
        ("app/src/main/java/hk/eta/app/ui/MainActivity.kt",
         "vm.onForeground()",
         "MainActivity.kt 仍係舊版（前景未重啟 ETA 自動刷新／缺 onPause）",
         "由完整 zip 覆蓋 MainActivity.kt"),
        ("app/src/main/java/hk/eta/app/ui/RouteDetailActivity.kt",
         "vm.onForeground()",
         "RouteDetailActivity.kt 仍係舊版（詳情頁 ETA 唔會自動刷新）",
         "由完整 zip 覆蓋 RouteDetailActivity.kt"),
        ("app/src/main/java/hk/eta/app/ui/SettingsActivity.kt",
         "fun setupEtaRefreshSection",
         "SettingsActivity.kt 仍係舊版（缺 ETA 自動刷新開關／間隔）",
         "由完整 zip 覆蓋 SettingsActivity.kt"),
        ("app/src/main/res/layout/activity_settings.xml",
         "ETA_AUTO_REFRESH_SECTION",
         "activity_settings.xml 仍係舊版（缺 ETA 自動刷新區塊）",
         "由完整 zip 覆蓋 activity_settings.xml（同時要傳 eta_refresh_picker.xml）"),
        ("app/src/main/res/values/strings.xml",
         "eta_refresh_status_on",
         "strings.xml 仍係舊版（缺 ETA 自動刷新字串／間隔陣列）",
         "由完整 zip 覆蓋 strings.xml"),
        # 導航欄大小與位置（對照截圖深灰黑藥丸）
        ("app/src/main/res/layout/activity_main.xml",
         "NAV_GEOMETRY",
         "activity_main.xml 仍係舊版（導航欄仍係 64dp／四邊 12dp 寬條）",
         "由完整 zip 覆蓋 activity_main.xml"),
        ("app/src/main/java/hk/eta/app/ui/NavGlass.kt",
         "fun geometry(",
         "NavGlass.kt 仍係舊版（缺按螢幕寬度調整大小與位置）",
         "由完整 zip 覆蓋 NavGlass.kt"),
        ("app/src/main/res/drawable/bg_nav_glass.xml",
         'android:radius="28dp"',
         "bg_nav_glass.xml 仍係舊版（30dp 圓角／藍灰玻璃）",
         "由完整 zip 覆蓋 bg_nav_glass.xml（連 dark／contrast 兩個變體）"),
        ("app/src/main/res/values/colors.xml",
         "nav_tab_active",
         "colors.xml 仍係舊版（缺藍色選中／灰字 tab 配色）",
         "由完整 zip 覆蓋 colors.xml"),
        ("app/src/main/res/values/strings.xml",
         "高 56dp",
         "strings.xml 仍係舊版（缺導航欄新幾何說明）",
         "由完整 zip 覆蓋 strings.xml"),
        # 正式版 APK 對應版本發布
        (".github/workflows/build-apk.yml",
         "RELEASE_VERSION_GATE",
         "build-apk.yml 仍係舊版（Release 未做版本對應校驗／只附普通 APK）",
         "由完整 zip 覆蓋 .github/workflows/build-apk.yml"),
        (".github/workflows/build-apk.yml",
         "hketa-release-apk",
         "build-apk.yml 仍係舊版（正式版 APK 冇上傳成 Actions Artifact）",
         "由完整 zip 覆蓋 .github/workflows/build-apk.yml"),
        (".github/workflows/build-apk.yml",
         "body_path: RELEASE_BODY.md",
         "build-apk.yml 仍係舊版（Release 說明未對應版本 notes）",
         "由完整 zip 覆蓋 .github/workflows/build-apk.yml"),
        ("scripts/verify_release_version.py",
         "RELEASE_VERSION_GATE",
         "scripts/verify_release_version.py 唔見咗或仍係舊版（版本三重校驗）",
         "由完整 zip 覆蓋 scripts/verify_release_version.py"),
        # 路線詳情：站名 + 所有同色輔助文字統一純黑（DETAIL_ALL_BLACK）
        ("app/src/main/res/layout/item_detail_stop.xml",
         "DETAIL_ALL_BLACK",
         "item_detail_stop.xml 仍係舊版（輔助文字未跟站名一齊改純黑）",
         "由完整 zip 覆蓋 item_detail_stop.xml"),
        ("app/src/main/res/layout/activity_route_detail.xml",
         "DETAIL_ALL_BLACK",
         "activity_route_detail.xml 仍係舊版（站名氣泡／標籤未改純黑）",
         "由完整 zip 覆蓋 activity_route_detail.xml"),
        ("app/src/main/res/layout/activity_route_detail.xml",
         'android:background="#F4F7FB"',
         "activity_route_detail.xml 仍係舊版（footer 黑底會令純黑字睇唔到）",
         "由完整 zip 覆蓋 activity_route_detail.xml（footer 固定淺底）"),
        ("app/src/main/res/values/colors.xml",
         '<color name="detail_aux_text">#000000</color>',
         "colors.xml 仍係舊版（輔助文字仍係深灰 #374151，未改純黑）",
         "由完整 zip 覆蓋 colors.xml"),
        ("app/src/main/res/values-night/colors.xml",
         '<color name="detail_aux_text">#000000</color>',
         "values-night/colors.xml 仍係舊版（夜間輔助文字未改純黑）",
         "由完整 zip 覆蓋 values-night/colors.xml"),
        # Maven Central 429「Too Many Requests」自動重試 + Gradle 依賴快取
        ('.github/workflows/build-apk.yml',
         'MAVEN_429_RETRY',
         'build-apk.yml 仍係舊版（未加 429 自動重試／Gradle 依賴快取）',
         '由完整 zip 覆蓋 .github/workflows/build-apk.yml'),
        ('scripts/show_build_error.py',
         'MAVEN_429_RETRY',
         'show_build_error.py 仍係舊版（唔識辨認 429 Too Many Requests）',
         '由完整 zip 覆蓋 scripts/show_build_error.py'),
        # 聯營巴士路線：城巴/九巴 + 每班次公司標籤（JOINT_KMB_CTB）
        ('app/src/main/java/hk/eta/app/api/JointRoutes.java',
         'JOINT_KMB_CTB',
         'JointRoutes.java 唔見咗或仍係舊版（缺聯營路線目錄判斷）',
         '由完整 zip 覆蓋 JointRoutes.java（新增檔）'),
        ('app/src/main/java/hk/eta/app/api/RouteSearch.java',
         'private static List<Result> markJoint',
         'RouteSearch.java 仍係舊版（搜尋／預載未合併聯營列）',
         '由完整 zip 覆蓋 RouteSearch.java'),
        ('app/src/main/java/hk/eta/app/api/EtaRepository.java',
         'JointRoutes.augment',
         'EtaRepository.java 仍係舊版（未合併另一間公司嘅班次）',
         '由完整 zip 覆蓋 EtaRepository.java'),
        ('app/src/main/java/hk/eta/app/ui/App1933Format.kt',
         'val operatorText: String',
         'App1933Format.kt 仍係舊版（聯營會拆做兩張卡／班次無公司）',
         '由完整 zip 覆蓋 App1933Format.kt'),
        ('app/src/main/java/hk/eta/app/ui/CardAdapters.kt',
         'App1933Format.tripOperatorText(t)',
         'CardAdapters.kt 仍係舊版（班次 chip 無「該班次：城巴／九巴」）',
         '由完整 zip 覆蓋 CardAdapters.kt'),
        ('app/src/main/java/hk/eta/app/ui/EtaViewModel.kt',
         'JointRoutes.isJointCompany(company)',
         'EtaViewModel.kt 仍係舊版（聯營詳情頁未展開 KMB+CTB）',
         '由完整 zip 覆蓋 EtaViewModel.kt'),
        ('app/src/main/java/hk/eta/app/ui/RouteDetailActivity.kt',
         'tvNoMapLabel',
         'RouteDetailActivity.kt 仍係舊版（詳情頁無「城巴/九巴」標籤）',
         '由完整 zip 覆蓋 RouteDetailActivity.kt'),
        ('app/src/main/res/layout/activity_route_detail.xml',
         'android:id="@+id/tvNoMapLabel"',
         'activity_route_detail.xml 仍係舊版（路線示意標籤無 id）',
         '由完整 zip 覆蓋 activity_route_detail.xml'),
        ('app/src/main/res/values/strings.xml',
         'name="joint_route_label"',
         'strings.xml 仍係舊版（缺聯營路線字串）',
         '由完整 zip 覆蓋 strings.xml'),
        # 主頁附近卡片都要合併聯營（JOINT_KMB_CTB_MERGE）
        ('app/src/main/java/hk/eta/app/ui/EtaViewModel.kt',
         'JOINT_KMB_CTB_MERGE',
         'EtaViewModel.kt 仍係舊版（主頁附近卡片仍拆做 KMB／CTB 兩張）',
         '由完整 zip 覆蓋 EtaViewModel.kt'),
        ('app/src/main/java/hk/eta/app/ui/App1933Format.kt',
         'val groupStop = if (stopId.isNotEmpty()) stopId else e.stopId',
         'App1933Format.kt 仍係舊版（分組仍用各公司 stopId，無法合併）',
         '由完整 zip 覆蓋 App1933Format.kt'),
        # 聯營站名統一用九巴站名（JOINT_KMB_NAME）
        ('app/src/main/java/hk/eta/app/api/JointRoutes.java',
         'preferKmbStops',
         'JointRoutes.java 仍係舊版（缺九巴站名優先邏輯）',
         '由完整 zip 覆蓋 JointRoutes.java'),
        ('app/src/main/java/hk/eta/app/ui/EtaViewModel.kt',
         'JOINT_KMB_NAME',
         'EtaViewModel.kt 仍係舊版（詳情頁／主頁聯營站名未用九巴）',
         '由完整 zip 覆蓋 EtaViewModel.kt'),
        # 液態玻璃導航欄位置：真正懸浮 overlay（NAV_OVERLAY_POSITION）
        ('app/src/main/res/layout/activity_main.xml',
         'NAV_OVERLAY_POSITION',
         'activity_main.xml 仍係舊版（導航欄仲係貼底佔位列，唔係懸浮 overlay）',
         '由完整 zip 覆蓋 activity_main.xml'),
        ('app/src/main/java/hk/eta/app/ui/NavGlass.kt',
         'BAR_BOTTOM_RATIO',
         'NavGlass.kt 仍係舊版（位置未改成按屏高上移嘅 overlay）',
         '由完整 zip 覆蓋 NavGlass.kt'),
        ('app/src/main/res/values/strings.xml',
         '離底約 12%',
         'strings.xml 仍係舊版（設定頁仍寫「貼底 8dp」）',
         '由完整 zip 覆蓋 strings.xml'),
        # 城巴路線站名用返城巴名（CTB_STOP_NAME）
        ('app/src/main/java/hk/eta/app/ui/App1933Format.kt',
         'CTB_STOP_NAME',
         'App1933Format.kt 仍係舊版（城巴卡會沿用九巴站名）',
         '由完整 zip 覆蓋 App1933Format.kt'),
        ('app/src/main/java/hk/eta/app/ui/EtaViewModel.kt',
         'CTB_STOP_NAME',
         'EtaViewModel.kt 仍係舊版（附近站未傳各公司站名對照）',
         '由完整 zip 覆蓋 EtaViewModel.kt'),
        # 只有城巴路線／城巴獨有站用城巴站名（CTB_ONLY_NAME）
        ('app/src/main/java/hk/eta/app/api/JointRoutes.java',
         'CTB_ONLY_NAME',
         'JointRoutes.java 仍係舊版（城巴獨有站仍會被改成九巴站名）',
         '由完整 zip 覆蓋 JointRoutes.java'),
        ('app/src/main/java/hk/eta/app/ui/App1933Format.kt',
         'fun resolveStopDisplay(',
         'App1933Format.kt 仍係舊版（未逐張卡分辨城巴／聯營站名）',
         '由完整 zip 覆蓋 App1933Format.kt'),
        # 循環線各家方式 + 站名核對（CIRCULAR_ROUTES）
        ('app/src/main/java/hk/eta/app/api/RouteSearch.java',
         'markCircular',
         'RouteSearch.java 仍係舊版（循環線未標記／仍被合併做聯營）',
         '由完整 zip 覆蓋 RouteSearch.java'),
        ('app/src/main/java/hk/eta/app/ui/EtaViewModel.kt',
         'CircularRoutes.isCircularSequence',
         'EtaViewModel.kt 仍係舊版（循環線站序仍會被九巴化／未做站名核對）',
         '由完整 zip 覆蓋 EtaViewModel.kt'),
        ('app/src/main/java/hk/eta/app/ui/RouteDetailActivity.kt',
         'CircularRoutes.displayTitle',
         'RouteDetailActivity.kt 仍係舊版（詳情頁未用各家循環線顯示方式）',
         '由完整 zip 覆蓋 RouteDetailActivity.kt'),
        # KOTLIN_TOPLEVEL：真實 kotlinc 會爆 Unresolved reference: StopDisplay
        ('app/src/main/java/hk/eta/app/ui/EtaViewModel.kt',
         'LinkedHashMap<String, StopDisplay>()',
         'EtaViewModel.kt 仍係舊版（App1933Format.StopDisplay 無法解析）',
         '由完整 zip 覆蓋 EtaViewModel.kt（改用頂層 StopDisplay）'),
        # PUBLISHED_RELEASE_BACKFILL
        ('scripts/published_release.py',
         'PUBLISHED_RELEASE_BACKFILL',
         'published_release.py 唔見咗或仍係舊版（無法補已發佈版本正式版 APK）',
         '由完整 zip 覆蓋 scripts/published_release.py'),
        ('.github/workflows/build-apk.yml',
         'backfill-published-release',
         'build-apk.yml 仍係舊版（冇 workflow_dispatch 補正式版 job）',
         '由完整 zip 覆蓋 .github/workflows/build-apk.yml'),
    ]
    for rel, needle, msg, fix in drift:
        p = ROOT / rel
        if p.is_file() and needle not in p.read_text(encoding="utf-8"):
            problems.append(f"檔案存在但內容仍係舊版：{msg} → 修復：{fix}")

    # Binary integrity: existence + non-empty is not enough for a keystore or
    # a PNG that may have been mangled by a web-upload / text editor.
    ks = ROOT / "app/hketa-debug.keystore"
    if ks.is_file():
        raw = ks.read_bytes()
        if raw[:2] != b"\x30\x82" and raw[:4] != b"\xfe\xed\xfe\xed":
            problems.append(
                "app/hketa-debug.keystore 唔係有效 PKCS12/JKS 二進位檔"
                "（可能被當文字上傳／損毀）→ 由 zip 原檔重新覆蓋"
            )
        if len(raw) < 1000:
            problems.append(
                f"app/hketa-debug.keystore 得 {len(raw)} bytes，疑似空檔／佔位檔"
            )
    pngs = [ROOT / "app/icon-source.png",
            ROOT / "app/src/main/ic_launcher-playstore.png"]
    pngs += sorted((ROOT / "app/src/main/res").glob("mipmap-*/ic_launcher*.png"))
    for p in pngs:
        if p.is_file() and p.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
            problems.append(
                str(p.relative_to(ROOT)) + " 唔係有效 PNG（可能被當文字上傳／損毀）"
            )

    if problems:
        print("::error::HK ETA 上傳完整性檢查失敗")
        for line in problems:
            print(f"::error::{line}")
        print("\n建議：改用 git push 上傳整個資料夾，唔好用 GitHub 網頁「Add files via upload」逐個拖：")
        print("  git add . && git commit -m 'Update' && git push")
        return 1

    print(f"preflight OK：{len(REQUIRED)} 個必要檔案齊全，{len(java_kt)} 個原始碼檔案。")
    print("提示：app/hketa-release.keystore 係刻意唔上傳（由 CI Secrets 注入）。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
