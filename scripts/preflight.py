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
    # 懸浮液態玻璃導航欄 + 設定頁免責聲明卡
    "app/src/main/java/hk/eta/app/ui/NavGlass.kt",
    "app/src/main/java/hk/eta/app/ui/NavGlassPrefs.kt",
    "app/src/main/res/layout/disclaimer_card.xml",
    "app/src/main/res/drawable/bg_disclaimer.xml",
    "app/src/main/res/drawable/bg_nav_glass.xml",
    "app/src/main/res/drawable/bg_nav_glass_dark.xml",
    "app/src/main/res/drawable/bg_nav_glass_contrast.xml",
    "app/src/main/res/drawable/bg_tab_glass_selected.xml",
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
         "GLASS_NAV",
         "activity_main.xml 仍係舊版（導航欄未懸浮玻璃／仍叫「更多」）",
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
