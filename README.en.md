# HKETA · Signed Release APK (v1.0.0)

[繁體中文](README.md) · **English**

An Android **release-only** project that rebuilds the Hong Kong public transport ETA app [`shamough1792/HKETA`](https://github.com/shamough1792/HKETA) using **GitHub Actions + JavaScript + TypeScript + Kotlin + open APIs**. It produces **only a signed release APK** — no debug APK, no debug artifact, no PR build.

Covers KMB/LWB, Citybus, NLB, green minibuses (GMB), MTR and Light Rail. Bilingual UI (Traditional Chinese / English), a stop map opened through a native `geo:` intent, keep-screen-awake, and an **in-app updater in the Settings tab**: check GitHub Releases, download the APK, show progress, and hand it to the system installer.

The original project is Cordova + JavaScript (MIT); this project is also MIT but **does not copy its `www/` assets** — the UI and API layer are written from scratch.

> ⚠️ **The signing key is yours, not something I can generate.** This is a project that is *configured* to produce a release build; you must create the keystore yourself and add four values to GitHub Secrets before Actions can emit `HKETA-release.apk`. No Android SDK/Gradle and no private key were available when this was prepared, so **no physical `.apk` is attached**.

---

## Stack

| What you asked for | What it does here |
| --- | --- |
| GitHub | `.github/workflows/build-apk.yml`: push to `main` / `v*` tag / manual dispatch → install SDK 36 → typecheck → **signed release build** → `apksigner verify` + SDK range assertion → upload Artifact; a tag also attaches the APK to a GitHub Release (**which doubles as the app's update source**) |
| JavaScript | `native-plugin/hketa-native/www/hketaNative.js` (Cordova JS bridge), `scripts/build-web.mjs`, `scripts/verify-kotlin.js` |
| TypeScript | `src/`: types, six API clients, normalisation, UI, native-bridge wrapper, **`update-panel.ts` (Settings + updater)** → `www/js/app.js` via esbuild |
| Kotlin | `HkEtaNative.kt`: device/app info, `geo:` map intent, Toast, keep-awake, **`DownloadManager` download + install intent + unknown-source permission** |
| API | Live ETA JSON from `data.etabus.gov.hk`, `rt.data.gov.hk`, `data.etagmb.gov.hk`, plus **`api.github.com/repos/{owner}/{repo}/releases/latest`** |

Under the hood: **Cordova CLI 13 + cordova-android 15.1.0 (Kotlin 2.1.21)**. Kotlin is a Cordova plugin called from TypeScript via `cordova.exec`. cordova-android 15 ships Kotlin Gradle support; enabling `GradlePluginKotlinEnabled` in `config.xml` is enough. **Device support: Android 8.0–17 (API 26–37)** — see below.

---

## Layout

```
hketa-apk/
├── .github/workflows/build-apk.yml    # signed release APK only (also the update source)
├── README.md / README.en.md           # zh-Hant / English
├── config.xml                         # v1.0.0 / min 26 / target 36 / Kotlin switch
├── build.json.example                 # local signing template (build.json is gitignored)
├── package.json / package-lock.json / tsconfig.json / .npmrc
├── scripts/  build-web.mjs · verify-kotlin.js · generate-icons.py
├── src/
│   ├── types / native / ui / main / version
│   ├── update-panel.ts                # Settings screen + updater flow
│   └── api/  http / kmb / ctb / nlb / gmb / mtr / lrt / index / update
├── www/      index.html · css/app.css · js/app.js · img/app-logo.png
├── native-plugin/hketa-native/        # JS bridge + Kotlin + plugin.xml
├── plugins/                           # Cordova install output (gitignored)
└── res/android/                       # logo-source.png + icons/{mdpi..xxxhdpi}/ + preview
```

> `native-plugin/` is deliberately **not** inside `plugins/`: `cordova plugin add` writes into `plugins/`, so keeping the source there would overwrite itself.

---

## Quick start

### 1. Browser preview

```bash
npm install
npm run typecheck
npm run build
npm run dev
```

Browser mode uses a fallback native bridge: the map button becomes a console hint, update checks hit the real GitHub API, but **download/install reports that the Android build is required** and falls back to opening the release page.

### 2. Build the APK locally (JDK 17 + Node 22 + Android SDK 36 + Build Tools 36.0.0)

```bash
npm run android:add   # add platform + install ./native-plugin/hketa-native
npm run apk
# output: platforms/android/app/build/outputs/apk/release/app-release.apk
```

> The local Kotlin plugin is deliberately **not** declared as a `<plugin>` in `config.xml`, to stop Cordova's restore mechanism from writing it back into `package.json` devDependencies. It is installed explicitly with `cordova plugin add ./native-plugin/hketa-native`.
>
> cordova-android 15.1.0's bundled AGP 8.10.1 / Gradle 8.14.2 / Kotlin 2.1.21 is all you need — no toolchain overrides.

### 3. Let GitHub produce the APK (recommended)

```bash
git init && git add . && git commit -m "HKETA v1.0.0 release"
git remote add origin https://github.com/<you>/hketa-apk.git
git push -u origin main
```

**Actions → Build HKETA signed release APK → latest run → Artifacts** → `HKETA-release.apk`.
Pushing a `v1.0.0` tag also attaches the APK to a GitHub Release — **this step creates the update source for the app**.

### 4. Configure release signing

```bash
keytool -genkeypair -v \
  -keystore release.keystore -alias hketa \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype PKCS12
```

Repo → **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `HKETA_KEYSTORE_BASE64` | `base64 -w0 release.keystore` (macOS: `base64 -i release.keystore \| tr -d '\n'`) |
| `HKETA_KEYSTORE_PASSWORD` | Keystore password |
| `HKETA_KEY_ALIAS` | `hketa` |
| `HKETA_KEY_PASSWORD` | Key password |
| `HKETA_KEYSTORE_TYPE` | Optional, defaults to `PKCS12` |

If any required value is missing, the workflow fails immediately and names the missing secret — it never silently emits an unsigned file. For local builds: `cp build.json.example build.json` and fill in real values (gitignored).

---

## What "release" means here

- `version="1.0.0"`, `android-versionCode="1"`
- `cordova build android --release --buildConfig=build.json` (`debuggable=false`)
- The workflow runs `apksigner verify --verbose`; an unsigned result fails the job
- All `--debug` / `app-debug.apk` / debug artifacts / PR triggers removed
- Single output name: `HKETA-release.apk`

---

## Supported range: Android 8.0 – 17

**This describes devices: the APK installs and runs on Android 8.0 through Android 17.** It does **not** mean `targetSdkVersion=37` — see below for the difference.

| Setting | Value | Means |
| --- | --- | --- |
| `android-minSdkVersion` | **26** | Android 8.0 Oreo (oldest installable) |
| `android-targetSdkVersion` | **36** | Android 16 Baklava (cordova-android 15 official ceiling) |
| `android-compileSdkVersion` | **36** | Compiled against API 36 |
| `android-maxSdkVersion` | **not set** | see below |

Android version → API: 8.0 = 26, 8.1 = 27, 9 = 28, 10 = 29, 11 = 30, 12 = 31/32, 13 = 33, 14 = 34, 15 = 35, 16 = 36, 17 = 37 ([uses-sdk table](https://developer.android.com/guide/topics/manifest/uses-sdk-element), [AOSP codenames](https://source.android.com/docs/setup/about/build-numbers)).

**Why no `maxSdk=37`?** Google explicitly discourages `maxSdkVersion`: Android is forward compatible, and a hard cap can cause the OS to remove the app after the user upgrades to Android 18; Play also uses it as a filter ([official `<uses-sdk>` doc](https://developer.android.com/guide/topics/manifest/uses-sdk-element#maxsdk)). So the project uses **min 26 to block Android 7 and older, and no upper cap** — Android 17 (API 37) and later remain installable. On `SDK > 37` the Settings screen shows a "beyond verified range" notice instead of blocking the app.

### Why target stays at 36, not 37

`targetSdk` selects which behaviour changes apply; Android 17 devices are **backward compatible**, so a target-36 app still installs and runs there — it simply does not opt into API 37 behaviours. This satisfies Google Play's current rule (new apps and updates must target ≥ 36 from 2026-08-31; API 37 becomes mandatory around August 2027).

Forcing 37 today breaks the toolchain, not just one config line:

- The [Cordova Android Platform Guide](https://cordova.apache.org/docs/en/latest/guide/platforms/android/index.html) lists **cordova-android 15.x as officially supporting API 24–36**; in [issue #1982](https://github.com/apache/cordova-android/issues/1982) a maintainer reports 37.x builds but "not extensively tested".
- **AGP 8.13 officially tops out at API 36.1** ([AGP 8.13 release notes](https://developer.android.com/studio/releases/gradle-plugin#8-13-0)). compileSdk 37 needs AGP 9.x + Gradle 9.x + JDK 21, while cordova-android 15 is built on AGP 8.10.1 / Gradle 8.14.2 / JDK 17 — swapping the whole chain is high risk.
- The API 37 platform package is `platforms;android-37.0` (with a minor version), but Cordova's `check_android_target` looks for `android-37`; that `37.0` vs `37` mismatch is exactly what [issue #1982](https://github.com/apache/cordova-android/issues/1982) reports.

So this release **stays on the officially buildable 36** and treats API 37 as "runs on the device, not targeted". When cordova-android ships 37 support, bump `config.xml` target/compile to 37, switch the workflow to `platforms;android-37.0` + `build-tools;37.0.0`, sync `scripts/verify-kotlin.js` and the aapt2 assertion, and test the Android 17 behaviour changes (mandatory large-screen resizability, Certificate Transparency, `ACCESS_LOCAL_NETWORK`, etc.).

> You can experiment by re-adding `AndroidGradlePluginVersion` / `GradleVersion` / `android-buildToolsVersion` overrides, but **CI success is not guaranteed**, so they are not enabled by default.

### Per-version compatibility handling

| API | Android | What this project does |
| --- | --- | --- |
| 26 | 8.0 | `minSdk`; `canRequestPackageInstalls()` + `REQUEST_INSTALL_PACKAGES` for in-app installs |
| 28 | 9 | `PackageInfo.longVersionCode`, falls back to `versionCode` |
| 29 | 10 | `DownloadManager` writes into Downloads (system service, exempt from Scoped Storage) |
| 30 | 11 | `<queries>` in `plugin.xml` (geo / https / APK mime); Kotlin launches implicit intents directly and catches `ActivityNotFoundException`, so package visibility filtering cannot break them |
| 33 | 13 | `PackageManager.PackageInfoFlags`; download notifications come from the system DownloadManager, no `POST_NOTIFICATIONS` needed |
| 34 | 14 | Install intent uses `FLAG_GRANT_READ_URI_PERMISSION` with a content URI, never a file URI |
| 35 | 15 | Edge-to-edge: `viewport-fit=cover` + responsive CSS |
| 36 | 16 | `targetSdk` / `compileSdk` — cordova-android 15's validated ceiling |
| 37 | 17 | Runs via platform backward compatibility; shown in the Settings "Supported range" row |

**Two gates**

- `scripts/verify-kotlin.js` (`before_build` hook) reads `cdv-gradle-config.json` and fails the build unless `MIN_SDK_VERSION=26`, `SDK_VERSION`/`COMPILE_SDK_VERSION=36`, and **no maxSdk**
- The workflow runs `aapt2 dump badging` on the produced APK, asserts `sdkVersion:'26'` / `targetSdkVersion:'36'`, and rejects any `maxSdkVersion`

---

## App icon

The rainbow-bus image you supplied is now the launcher icon; the source is kept at `res/android/logo-source.png` (1080×1080).

| Layer | Contents | Size |
| --- | --- | --- |
| `ic_launcher_background` | Clean rainbow gradient with the subject removed | 108dp × 5 densities |
| `ic_launcher_foreground` | Cut-out bus + driver on transparency, centred at 78% height | same |
| `ic_launcher_monochrome` | White silhouette for Android 13+ themed icons | same |
| `ic_launcher` | Legacy square fallback | 48 / 72 / 96 / 144 / 192 px |

`<platform name="android">` in `config.xml` declares `src` / `background` / `foreground` / `monochrome` for `mdpi` through `xxxhdpi`; cordova-android 15 generates `mipmap-*-v26/ic_launcher.xml` automatically — **no Gradle or AndroidManifest edits**. Since `minSdk=26`, every supported device gets the adaptive icon and every OEM mask (circle, rounded square, squircle) crops correctly.

Also included: `logo-playstore-512.png` (store listing), `www/img/app-logo.png` (shown in the header and Settings), and **`res/android/logo-preview.png`** — a four-up preview of all launcher appearances.

To swap the artwork or resize: `scripts/generate-icons.py` (needs Pillow / numpy / opencv-python; the PNGs are committed, so a normal build needs no Python). Details in `res/android/README.md`.

> ⚠️ The image was supplied by you for use as the icon; the code is MIT but that **does not cover the image's licence**. Confirm you own the rights to use and adapt it before publishing.

---

## In-app update (Settings tab)

The header has two tabs: **ETA / Settings**. Settings contains:

| Section | Contents |
| --- | --- |
| Application | Current version (`PackageInfo.versionName` + build), language, keep-awake, device info |
| In-app update | GitHub owner / repo, check-on-launch toggle, check button, release card, progress bar, download & install, allow installs, open download page |

**Flow**

1. `GET https://api.github.com/repos/{owner}/{repo}/releases/latest`
2. Strip the leading `v` from `tag_name` and compare three numeric segments against the installed version
3. Find the `HKETA-release.apk` asset (fallback: first `.apk`; if none, only "Open download page" is offered)
4. TypeScript calls Kotlin `downloadApk()` → system `DownloadManager` downloads into Downloads with a visible notification
5. TypeScript polls `downloadProgress()` every 900 ms and updates the bar; Kotlin returns a semantic `state` (`running` / `success` / `failed`), so no Android integer constants are hardcoded in JS
6. On completion, check `canInstallUnknown()`. On Android 8+ without permission, open `ACTION_MANAGE_UNKNOWN_APP_SOURCES`; installation resumes automatically when the user returns
7. `installApk()` fires an `ACTION_VIEW` install intent using `DownloadManager.getUriForDownloadedFile()`'s content URI; on failure it falls back to opening the release page in a browser

A small green dot appears on the Settings tab when an update is available. Preferences (repo, auto-check, language, keep-awake) persist in `localStorage`.

**Before first use, change these**

- `src/version.ts`: `DEFAULT_OWNER` / `DEFAULT_REPO` (now points at `benyeung08/hketa`; change after forking)
- `config.xml`: `<author href="...">` and email (the email is still a placeholder — replace it before publishing)
- Or skip the code edits and just enter owner / repo in the app's Settings screen, then press **Save**

> The unauthenticated API allows 60 requests/hour; auto-check runs once per launch only.

**Two hard constraints**

- 🔑 **The new APK must be signed with the SAME keystore** or Android rejects the install with a signature mismatch — this is why the Secrets must be kept long-term.
- 📱 `REQUEST_INSTALL_PACKAGES` enables sideload / self-distribution; **Google Play restricts this permission**. For Play, use Play In-App Updates and remove in-app installation (route to the store listing via `openUrl` instead).

---

## Publishing a new version

1. Edit `config.xml`: `version="1.1.0"`, `android-versionCode="2"` (**versionCode must increase**)
2. Update `package.json` and `FALLBACK_APP_VERSION` in `src/version.ts` to `1.1.0`
3. Update any version text in `www/index.html`
4. `git commit` → `git tag v1.1.0` → `git push origin main --tags`
5. The workflow creates the Release + `HKETA-release.apk`
6. Older installs now see `v1.1.0` under **Settings → Check for updates** and can download it

> Use `v1.1.0` style tags; the leading `v` is stripped before comparison.

---

## API endpoints

| Source | Endpoint | Key params |
| --- | --- | --- |
| KMB / LWB | `https://data.etabus.gov.hk/v1/transport/kmb/eta/{stop_id}/{route}/{service_type}` | Normal service is `service_type=1`; also `/stop-eta/{stop_id}`, `/route/`, `/stop/{stop_id}` |
| Citybus | `https://rt.data.gov.hk/v2/transport/citybus/eta/CTB/{stop_id}/{route}` | `company_id` is always uppercase `CTB` (NWFB routes merged in) |
| NLB | `https://rt.data.gov.hk/v2/transport/nlb/stop.php?action=estimatedArrivals` | `routeId`, `stopId`, `language=zh` |
| GMB | `https://data.etagmb.gov.hk/eta/route-stop/{route_id}/{stop_id}` | Or `/eta/stop/{stop_id}`; returns `timestamp` / `diff` / `remarks_tc` |
| MTR | `https://rt.data.gov.hk/v1/transport/mtr/getSchedule.php?line=&sta=&lang=` | `lang=TC` / `EN`; returns `UP` / `DOWN` |
| Light Rail | `https://rt.data.gov.hk/v1/transport/mtr/lrt/getSchedule?station_id=&with_special=1` | Returns `platform_list[].route_list[]` |
| Update | `https://api.github.com/repos/{owner}/{repo}/releases/latest` | Unauthenticated: 60 req/hr |

Official specs: [KMB/LWB](https://data.etabus.gov.hk/datagovhk/kmb_eta_api_specification.pdf) · [NLB](https://www.nlb.com.hk/datagovhk/BusServiceOpenAPIDocumentation2.0.pdf) · [GMB](https://data.etagmb.gov.hk/static/GMB_ETA_API_Specification.pdf) · [MTR](https://opendata.mtr.com.hk/doc/Next_Train_API_Spec_v1.7.pdf) · [Light Rail](https://opendata.mtr.com.hk/doc/LR_Next_Train_API_Spec_v1.1.pdf). The Citybus v2.01 spec is provided on its DATA.GOV.HK dataset page.

Every client normalises its own format into a single `EtaItem` (route, destination, minutes, remark, platform, scheduled flag); the UI only knows that type.

---

## Try it

Pre-filled on launch: `KMB` / `40` / `647AB0AD358F10DF` / `1` (Mei Foo).
Others: MTR `TML` + `ADM` + `TC` · Light Rail: blank route + `600` + `1` · Citybus `11` + `001145` + `O`.

---

## Verification status

Checked while preparing this project: `tsc --noEmit` ✅ · esbuild bundle ✅ · `config.xml` / `plugin.xml` XML ✅ · `package.json` / `tsconfig.json` / `package-lock.json` / `build.json.example` JSON ✅ · workflow YAML ✅ · plugin `source-file` path and `.kt` present ✅ · `<queries>` declared ✅ · i18n dictionaries match ✅ · `config.xml` min/target/compile = 26/36/36 with no maxSdk ✅ · `verify-kotlin.js` and aapt2 assertions in sync ✅ · 5 icon declarations and 20 PNGs (paths, dimensions, alpha) ✅ · launcher preview visually reviewed under four masks ✅ · no debug leftovers ✅.

**Not done:** the sandbox has no Android SDK/Gradle and no keystore, so no physical APK was compiled and `DownloadManager` + the install intent were not verified on a real device (some OEMs handle `content://downloads/...` install URIs differently — a browser fallback is included). Signing and packaging are handled by the GitHub Actions workflow above.

---

## Known limitations

- Queries use official `route` / `stop ID` directly; route/stop-name search, full stop lists, inbound/outbound toggles and bookmarks are not built yet.
- Live API calls with **no offline cache**; HTTP `429` and overnight no-service periods surface as an error or an empty list.
- A "Scheduled Bus" is not the same as a GPS-tracked arrival; the card labels it in the remark.
- MTR / LRT use official line and station codes, not Chinese station names; common codes live in `src/api/mtr.ts`.
- A launcher icon is provided, but **no splash screen** yet (Android 12+ uses the system splash); add a `<splash>` to `config.xml` if needed.
- `targetSdkVersion=36` (Android 16), **not 37**; Android 17 devices run it through platform backward compatibility. Google Play requires target ≥ 36 for new apps/updates from 2026-08-31, so this complies; API 37 becomes mandatory around August 2027 — confirm the current requirement before publishing ([Play target API policy](https://support.google.com/googleplay/android-developer/answer/11926878)).
- **API 37 is not targeted yet**: see the toolchain constraints above; bump once cordova-android officially supports it.
- Android 17 behaviours that only fire at target 37 (mandatory large-screen resizability, CT, `ACCESS_LOCAL_NETWORK`, etc.) are untested because this build does not target 37.
- `minSdk=26` excludes Android 7.x and older — the cost of "8–17 only".
- In-app installation needs `REQUEST_INSTALL_PACKAGES` plus user consent and is **not suitable for Play** (see above).

---

## License & sources

- Code: MIT (see `LICENSE`).
- Functional reference: [shamough1792/HKETA](https://github.com/shamough1792/HKETA) (Cordova / JavaScript / MIT).
- Concept reference: [DATA.GOV.HK — HKETA](https://data.gov.hk/tc/application/hketa).
- Live data: HKSAR Transport Department / DATA.GOV.HK / respective operators. Observe each source's terms of use and attribution requirements.
- Update source: [GitHub Releases API](https://docs.github.com/en/rest/releases/releases).
