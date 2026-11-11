# hketa-native

HKETA 的 Cordova plugin，負責 `window.HkEtaNative` 與 Android Kotlin 之間的橋接。

| Action | 參數 | 用途 |
| --- | --- | --- |
| `deviceInfo` | 無 | 型號、Android 版本、SDK、包名 |
| `appVersion` | 無 | `versionName` + `longVersionCode`，給更新比對用 |
| `openMap` | `lat, lng, label` | `geo:` Intent 開外部地圖 |
| `toast` | `message` | 原生 Toast |
| `keepAwake` | `boolean` | `FLAG_KEEP_SCREEN_ON` |
| `canInstallUnknown` | 無 | 回 `{ allowed }`；Android 8+ 查 `canRequestPackageInstalls()` |
| `requestInstallPermission` | 無 | 開 `ACTION_MANAGE_UNKNOWN_APP_SOURCES` 設定頁 |
| `downloadApk` | `url, fileName` | 系統 `DownloadManager` 下載到 Downloads，回 `{ id }`；只接受 `https://` |
| `downloadProgress` | `id` | 回 `{ status, state, progress, bytes, total, reason, uri }`；`state` 為語意字串，TS 不需硬編碼 Android 常數 |
| `installApk` | `id` | 用 `getUriForDownloadedFile()` 的 content URI 發 `ACTION_VIEW` 安裝 Intent |
| `openUrl` | `url` | 瀏覽器開 Release 頁（安裝失敗時的 fallback） |

### Kotlin 怎麼被編進 APK

`config.xml` 設定：

```xml
<preference name="GradlePluginKotlinEnabled" value="true" />
<preference name="GradlePluginKotlinVersion" value="2.1.21" />
```

cordova-android 15 會自動加入 Kotlin Gradle Plugin、`kotlin-android`、stdlib 與 `kotlinOptions`。
`plugin.xml` 的 `target-dir` 用 `app/src/main/java/...`；舊文件的 `src/...` 前綴對 `.kt` 會落到錯誤目錄。

`scripts/verify-kotlin.js`（`before_build` hook）會檢查 Kotlin 有被套用，並驗證
`cdv-gradle-config.json` 的 `MIN_SDK_VERSION=26` / `SDK_VERSION=36` / `COMPILE_SDK_VERSION=36`，
且不得設 `MAX_SDK_VERSION`；不符就讓 build 失敗。min 26 擋掉 Android 7 及更早，
不設上限讓 Android 17（API 37）與更新版本都能安裝。

### 更新功能用到的權限

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

`REQUEST_INSTALL_PACKAGES` 是 Android 8+ 從 App 內啟動安裝 Intent 的必要宣告，且使用者要在本機設定裡授權「安裝未知應用程式」。
**Google Play 對此權限有政策限制**：側載／自架發布適用，上架 Play 應改用 Play In-App Updates 並移除應用內安裝。

用 `DownloadManager` 自帶的 `content://downloads/...` URI 安裝，因此**不需要 FileProvider、不需要額外 AndroidX 依賴**。

### Android 11+ 套件可見性（API 30）

`plugin.xml` 在 `/manifest` 下宣告 `<queries>`，涵蓋 `geo:`、`https:` 與 `application/vnd.android.package-archive` 三種 `ACTION_VIEW` intent。少了它，targetSdk ≥ 30 時 `resolveActivity()` 會被系統過濾而永遠回 `null`，地圖按鈕與 APK 安裝會靜默失效。

Kotlin 端另外採**雙保險**：`openMap` / `installApk` / `openUrl` 不再先問 `resolveActivity()`，而是直接 `startActivity()` 並 catch `ActivityNotFoundException`，讓 TS 能走錯誤處理與「開啟下載頁」fallback。

### 跨版本分支（Android 8–17）

| API | Kotlin 分支 |
| --- | --- |
| 26+ | `PackageManager.canRequestPackageInstalls()`；以下直接回 `true` |
| 28+ | `PackageInfo.longVersionCode`；以下用 `versionCode` |
| 33+ | `getPackageInfo(..., PackageInfoFlags.of(0))`；以下用舊 overload |
| 全版本 | `DownloadManager` + content URI 安裝（系統服務，避開 Scoped Storage / FileProvider） |
