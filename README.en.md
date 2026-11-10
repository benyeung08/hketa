# HKETA · Kotlin / JSON Rebuild

[繁體中文 README](README.md) · **English**

A **100% Kotlin** native Android rebuild of [shamough1792/HKETA](https://github.com/shamough1792/HKETA) (originally Cordova + JavaScript). Routes, stops, fares and service schedules are all driven by one **`hketa.json`**, and **GitHub Actions** builds the APK automatically.

## Features

- **Four transport modes** — Bus (KMB / CTB / NLB), Green Minibus (GMB), Metro (MTR), Light Rail (LRT): 30 demo routes and 355 stops
- **Complete route data** — every route has **fares** (full / child / senior / section fares), outbound & inbound stops, and **service schedules** (first & last departure, peak / off-peak headway, journey time)
- **Search + two-level filtering** — search by route number or Chinese/English origin/destination; mode chips (All / Bus / Minibus / Metro / Light Rail) plus operator chips
- **Route details** — stop list, direction switch, bilingual termini, fare & schedule summary and a full detail dialog
- **ETA** — per-stop departure times, minutes to arrival, destination, scheduled-service remark, MTR / LRT platform
- **Bilingual UI** — one-tap 中文 / EN switch, synced across screens
- **Map** — opens any installed map app via `geo:`
- **Refresh** with last-updated time
- **Settings → Route data update** — online update of fares / routes / schedules for all four modes, validated then cached
- **Settings** — signature consistency, full app version, in-app APK update, **online release history** (the only version-history source), About
- **Signature-consistent updates** — new APKs from GitHub Releases are verified against the permanent key before installation is offered

> ⚠️ The bundled JSON is **demo data, not live arrival times**; stop coordinates are approximate. See [Connecting real data](#connecting-real-live-eta-and-route-data).

---

## Supported range: Android 8 – 17

| Android | API | Status |
|---|---|---|
| 8.0 / 8.1 Oreo | 26 / 27 | ✅ Minimum supported |
| 9 – 16 | 28 – 36 | ✅ |
| 17 Cinnamon Bun | 37 | ✅ `targetSdk` |

`minSdk = 26`, `targetSdk = 37`, `compileSdk = 37`. Android 7.1 and below (≤ API 25) are refused by the system / Play.

**No `maxSdk` is set**: future Android 18 devices can still install (untested), so users are not locked out after an OS upgrade. Uncomment `maxSdk = 37` in `app/build.gradle.kts` if you really want a hard ceiling.

Because the minimum is already API 26, **every supported device uses the adaptive icon**; `mipmap-*/ic_launcher*.png` are kept only as fallback for a few OEM launchers. The toolchain was upgraded accordingly to AGP 9.1.1 (the first stable AGP supporting API 37).

---

## Permanent signing consistency (one-time setup)

Android requires that **every new version of the same package name is signed with the same key**, otherwise installation fails with `INSTALL_FAILED_UPDATE_INCONSISTENT_CERTIFICATES`. The default CI `debug.keystore` differs per machine, so without a fixed key every Actions build could produce a different certificate and installed users could never upgrade.

Two layers are implemented:

**① One key for every APK** — `debug`, `release` and GitHub Releases all bind to the same `signingConfigs.permanent`. The keystore itself never enters the repo; it is stored as base64 in GitHub Secrets and restored to the runner's temp directory at build time.

**② In-app self-check** — CI extracts the certificate SHA-256 from the keystore and injects it into `R.string.expected_signing_cert_sha256`. On every launch the app compares its own signing certificate; a mismatch blocks loading and shows a warning, preventing repackaging / re-signing. **Settings → Signature consistency** shows live status, both fingerprints and a re-verify button.

### Setup (once)

```bash
export HKETA_KEYSTORE_PASSWORD='a long store password'
export HKETA_KEY_PASSWORD='another long key password'
bash scripts/gen_keystore.sh
```

The script generates `hketa-release.jks` (RSA 4096, **valid for 36,500 days ≈ 100 years**) and `hketa-release.jks.base64.txt`. Then go to **Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret | Value |
|---|---|
| `HKETA_KEYSTORE_BASE64` | contents of `.base64.txt` |
| `HKETA_KEYSTORE_PASSWORD` | store password |
| `HKETA_KEY_ALIAS` | `hketa` (default) |
| `HKETA_KEY_PASSWORD` | key password |

⚠️ If any one is missing the workflow **fails fast** with the missing name; it never silently falls back to a temporary debug key. `.jks`, `.p12` and `.base64.txt` are in `.gitignore`.

### What CI does on every run

1. Restores the keystore from Secrets → derives the cert SHA-256 with `keytool -list -v` → injects `HKETA_SIGNING_CERT_SHA256` (Gradle writes it into an app resource).
2. Signs both `assembleDebug` and `assembleRelease` with the same key.
3. Reads both APK fingerprints back with `apksigner verify --print-certs`, compares them with the keystore value, **fails on mismatch**, and writes the fingerprint into the Actions Step Summary.

Manual check:

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs HKETA-debug.apk
# Signer #1 certificate SHA-256 digest: must match "Expected cert" in the app
```

> ⚠️ **The key is the identity.** Back up the `.jks` and both passwords offline. Losing or regenerating it means installed users can never upgrade; the only recovery is changing `applicationId` and republishing. No certificate is literally permanent, but a 100-year validity plus safe custody is effectively permanent. On Google Play, also enable Play App Signing and treat this key as the upload key.
>
> For local development without the environment variables, Gradle keeps the AGP default (debug key / unsigned release) and `expected_signing_cert_sha256` is empty → **the in-app check is disabled** so no false alarm appears.

---

## In-app settings & updates

Tap **Settings** at the bottom of the home screen to open `SettingsActivity`; the adjacent **Data update** button opens the same page scrolled straight to the Route data update card (`focus=data`). The five UI cards are described below in six parts.

### ① Signature consistency

- Live status: `✅ Consistent, safe to upgrade` / `⚠️ Not set (dev build)` / `❌ Mismatch, possibly repackaged`
- Actual and expected certificate SHA-256, selectable for copying
- "Re-verify signature" re-checks immediately

> There is deliberately **no "edit expected fingerprint"** option. The expected value comes only from the permanent key at build time; letting anyone change it in-app would bypass the whole anti-tamper mechanism.

### ② App version (top of the update card)

Read locally from `PackageManager` — no network needed:

| Field | Source |
|---|---|
| Version name | `versionName` |
| `versionCode` | `longVersionCode` on API 28+, `versionCode` on 26–27 |
| First installed / Last updated | `firstInstallTime` / `lastUpdateTime` |
| Signing cert | first 12 chars of the actual SHA-256 |

Below it, "Current version" is compared with "Latest version".

### ③ APK update (the signature-consistent updater)

```
Tap "Check GitHub for updates"
  → GET api.github.com/repos/<OWNER>/<REPO>/releases/latest
  → numeric comparison of tag_name against current versionName
  → newer + release has an .apk → show "Download & verify signature"
     → download to cacheDir/updates/hketa-update.apk (progress %)
     → getPackageArchiveInfo reads candidate packageName + signing cert
     → triple check:
         (a) packageName == hk.eta.app
         (b) candidate cert == currently installed cert
         (c) candidate cert == build-time expected fingerprint (if set)
     → only if all pass: "Signature OK — install update"
     → ACTION_VIEW + content:// (FLAG_GRANT_READ_URI_PERMISSION) to system installer
  → any mismatch → delete download, show reason, no install button appears
```

In short: **a different key, wrong package name, unsigned or corrupt APK can never be installed**, so users never end up with a version that cannot be upgraded later.

### ④ Settings → Route data update (Bus / Minibus / Metro / Light Rail)

One `hketa.json` holds all four modes and is swapped in a single operation:

| Mode | `mode` | Operators | Bundled demo routes |
|---|---|---|---|
| Bus | `bus` | KMB / CTB / NLB | 12 |
| Green Minibus | `minibus` | GMB | 4 |
| Metro | `metro` | MTR | 10 |
| Light Rail | `light_rail` | LRT | 4 |

Every route must carry three groups: **fares** (`fare`: full / child / senior, payment method, `sections`), **route** (`bounds` directions + `stopIds` resolving into `stops`), and **service schedules** (`services`: days, first/last, peak / off-peak headway, journey time). The route detail screen shows a summary plus a full dialog.

Update flow:

```
Tap "Check & update route data"
  → GET raw.githubusercontent.com/.../hketa.json (Cache-Control: no-cache)
  → org.json parse
  → RouteDataUpdate.validate():
      (a) all four modes present, each with at least one route
      (b) every route.co / route.mode valid, number non-empty
      (c) every stopId exists in stops
      (d) every route has a valid fare.full > 0 and >= 1 service with first/last
  → any failure → reject, list every problem, existing data untouched
  → all pass → write filesDir/route-data.json (data cache)
             → update AppState, effective immediately
             → show vOld → vNew, route/stop/fare/schedule diff, per-mode stats
```

The card shows the current data version, source (bundled / online cached), generation time, and per-mode "routes · stops · fare coverage · schedule coverage". "Restore bundled data" (with confirmation) deletes the cache and switches back to the APK's bundled JSON.

> **Difference from "Online release history":** the cache here is **the route data itself**, needed offline — not an activity log. After the earlier merge, **version history still has exactly one online source (GitHub Releases)** and the app writes no update log at all. At startup the app prefers a validated data cache and falls back to bundled JSON if missing or corrupt. Home-screen "Refresh" re-fetches and re-validates when online data is in use.

To change data: edit `hketa.json` in the repo and push (no new APK), then tap update in the app. If `OWNER/REPO` in `AppConfig.kt` are unset, a hint is shown instead of hitting a placeholder URL. See [Connecting real data](#connecting-real-live-eta-and-route-data); `RouteDataUpdate.validate()` is the gate that prevents partial or malformed data from landing.

### ⑤ Online release history (the only version-history source)

The former "live release history" and "on-device update log" were merged into **one online source**: every record is fetched live from `GET /repos/<OWNER>/<REPO>/releases?per_page=30` with `Cache-Control: no-cache` — **no cache, nothing written on-device, no update log**. It loads automatically when Settings opens; "Reload online history" re-fetches manually.

Two status lines:

- `● Online · GitHub Releases (no cache) · N releases` / `● Connecting…` / `● Connection failed: …` (green / blue / red)
- `Last sync HH:mm · source api.github.com/.../releases`

Each release shows:

- `tag_name`, release name, publish time (GitHub UTC string), `.apk` filename and size
- badges comparing against the installed `versionName`: `[Latest]`, `[Installed]`, `[Newer]`, `[Older]`, `[Pre-release]`, `[No APK]`
- first line of the notes; **tap to expand / collapse full release notes**, then "→ Open GitHub release page"

`draft` releases are filtered out; `prerelease` is kept and labelled. No releases / API failure shows a clear status instead of a blank screen. Update-check, download, verification and install results are still shown live in section ③, but are **never written to any on-device log**.

> ⚠️ Unauthenticated GitHub API allows **60 requests per IP per hour**. Opening Settings costs one request; normal use is fine, but hammering it on the same network yields `HTTP 403` — wait an hour or use an authenticated token. Staying online-only means no cache; that is the trade-off.

### ⑥ About

Android 8–17 support, demo-data notice, permanent-signing note.

### One thing to configure

`app/src/main/java/hk/eta/app/AppConfig.kt`:

```kotlin
const val OWNER = "your-github-account"
const val REPO  = "HKETA-Kotlin"
```

`REMOTE_JSON_URL` (data), `RELEASES_API` (APK update) and `RELEASES_HISTORY_API` (online history) are all derived from these two values. Until they are filled in, update / history actions show a hint and never call a placeholder URL.

### Publishing an update users actually receive

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts` (e.g. `2` / `1.1.0`).
2. `git tag v1.1.0 && git push origin v1.1.0`.
3. The workflow builds, verifies signatures and attaches **`HKETA-release.apk`** to that Release (the body also includes the cert fingerprint).
4. Users: open app → Settings → Check for updates → Download → Verify → Install, upgrading in place.

> A Release must contain an `.apk` asset. Pushing a branch without a `v*` tag makes `/releases/latest` return 404 and the app explains it. The workflow deliberately renames the release APK to `HKETA-release.apk` so the updater can find it.

### Permissions & compatibility

- `REQUEST_INSTALL_PACKAGES`: required from Android 8+ to launch an install Intent. Users must still enable "Install unknown apps" for HKETA in system settings; otherwise a hint points them to GitHub Releases.
- `ApkFileProvider`: a pure-framework `ContentProvider` serving only APKs in `cacheDir/updates/`, `exported=false` + `grantUriPermissions=true`. Since `file://` is forbidden from API 26 and the project intentionally avoids AndroidX, this replaces `androidx.core.content.FileProvider`.
- Certificate reading branches by platform: `GET_SIGNATURES` on API 26–27, `GET_SIGNING_CERTIFICATES` on 28+ (`SignatureVerifier` handles both installed and archive APKs).

---

## Getting the APK (no Android Studio needed)

1. Complete the four signing Secrets above, then push this folder to GitHub (default branch `main`).
2. Open **Actions**, wait for `Build HKETA APK` (about 3–6 minutes).
3. Download `HKETA-debug.apk` (or `HKETA-release`) from that run's **Artifacts** and install on an Android phone. Both artifacts share the same permanent key, so they can replace each other; the Step Summary lists the fingerprint.

The workflow installs JDK 17, the Android 17 SDK (`platforms;android-37`, `build-tools;36.0.0`) and Gradle 9.3.1, restores the permanent keystore from Secrets, runs `json.tool` and `scripts/validate.py` (four-mode data, fares / schedules, XML, `R.id`, signing and update settings), then `assembleDebug` / `assembleRelease`, and finally compares both APK fingerprints with `apksigner`. The repo contains **no binaries** (not even `gradle-wrapper.jar` or a `.jks`), so a fork runs immediately.

Push a `v*` tag, e.g. `git tag v1.0.0 && git push origin v1.0.0`, to also attach the APK to a GitHub Release.

---

## Local build

Requires JDK 17 + Android SDK (`platforms;android-37`, `build-tools;36.0.0`) and Android Studio Panda 3 (2025.3.3) or newer:

```bash
# If Gradle is not installed locally, install 9.3.1; otherwise skip
gradle wrapper --gradle-version 9.3.1     # optional: generate wrapper for Android Studio
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

To produce a locally signed APK with the same identity, export the same four variables before building (plus the derived fingerprint):

```bash
export HKETA_KEYSTORE_FILE=/path/to/hketa-release.jks
export HKETA_KEYSTORE_PASSWORD='...'
export HKETA_KEY_ALIAS=hketa
export HKETA_KEY_PASSWORD='...'
export HKETA_SIGNING_CERT_SHA256=$(keytool -list -v -keystore "$HKETA_KEYSTORE_FILE" \
  -storepass "$HKETA_KEYSTORE_PASSWORD" -alias "$HKETA_KEY_ALIAS" \
  | grep -m1 'SHA256:' | sed 's/.*SHA256: //' | tr -d ':' | tr 'A-Z' 'a-z' | tr -d '[:space:]')
gradle assembleRelease
```

Without them you get a dev build with verification disabled — do not distribute it.

### Tech stack (deliberately minimal)

| Item | Version / choice |
|---|---|
| Language | Kotlin (built into AGP 9, KGP 2.2.10) |
| AGP / Gradle | 9.1.1 / 9.3.1 |
| SDK | compile 37, min 26, target 37 |
| UI | Native Android Views + XML layouts (**no** Compose / AndroidX) |
| JSON | Android built-in `org.json` (**no** Gson / Moshi / kotlinx.serialization) |
| APK update | `HttpURLConnection` + `getPackageArchiveInfo` + custom `ContentProvider` (**no** AndroidX FileProvider / OkHttp) |
| Route data update | `org.json` + `RouteDataUpdate.validate()` + `filesDir` data cache (**no** SQLite / Room / third-party JSON lib) |
| Version history | Live GitHub `/releases` only (**no** SharedPreferences / Room / DataStore / any on-device log) |

API 37 requires AGP 9.1.1+. AGP 9 bundles Kotlin, so `org.jetbrains.kotlin.android` and `kotlinOptions {}` are gone; `jvmTarget` follows `compileOptions`. `dependencies` is **empty** — Kotlin stdlib comes from the built-in KGP, so Actions never fails on third-party downloads.

> Troubleshooting: in the rare case the built-in Kotlin does not provide stdlib, add one line to `app/build.gradle.kts` (matching AGP 9.1.1's KGP 2.2.10):
> `dependencies { implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.10") }`

After local edits, run `python3 scripts/validate.py` to check JSON references, XML resources, `R.id` bindings, the workflow and toolchain versions in one go (Python 3; PyYAML additionally validates the YAML).

---

## JSON is the database

`app/src/main/assets/hketa.json` defines the entire app's content. Changing data needs no Kotlin changes and no new APK (when using the online update).

```jsonc
{
  "version": "2.0.0",
  "generatedTimestamp": "2026-11-10T19:00:00+08:00",
  "demo": true,
  "note": { "zh": "示範資料，非即時", "en": "Demo data, not live" },
  "fareDisclaimer": { "zh": "收費只作參考", "en": "Fares are indicative; refer to operators" },
  "apiDocs": { "kmb_eta_spec": "https://..." },
  "modes": [
    { "id": "bus",        "zh": "巴士",     "en": "Bus" },
    { "id": "minibus",    "zh": "專線小巴", "en": "Minibus" },
    { "id": "metro",      "zh": "地鐵",     "en": "Metro" },
    { "id": "light_rail", "zh": "輕鐵",     "en": "Light Rail" }
  ],
  "operators": [ { "id": "KMB", "zh": "九巴／龍運", "en": "KMB & LWB" } ],
  "stops": {
    "KMB1_O_01": {
      "name": { "zh": "竹園邨總站", "en": "CHUK YUEN ESTATE BUS TERMINUS" },
      "lat": 22.3417, "lng": 114.1947
    }
  },
  "routes": [{
    "id": "KMB_1", "co": "KMB", "mode": "bus", "number": "1",
    "name": { "zh": "竹園邨 ↔ 尖沙咀碼頭", "en": "Chuk Yuen ↔ Star Ferry" },
    "bounds": [{
      "dir": "O",                       // O outbound / I inbound; MTR also uses UT / DT
      "label": { "zh": "去程", "en": "Outbound" },
      "orig":  { "zh": "竹園邨", "en": "CHUK YUEN ESTATE" },
      "dest":  { "zh": "尖沙咀碼頭", "en": "STAR FERRY" },
      "stopIds": ["KMB1_O_01", "KMB1_O_02"]
    }],
    "fare": {                            // Fares in HKD
      "currency": "HKD", "full": 6.7, "child": 3.4, "senior": 2.0,
      "method": { "zh": "八達通／現金", "en": "Octopus / cash" },
      "note":   { "zh": "分段收費另計", "en": "Section fares apply" },
      "sections": [
        { "from": { "zh": "竹園邨", "en": "Chuk Yuen" },
          "to":   { "zh": "旺角", "en": "Mong Kok" }, "full": 4.2 }
      ]
    },
    "services": [{                       // Schedules (multiple: weekday / weekend)
      "days":     { "zh": "星期一至五", "en": "Mon–Fri" },
      "first": "05:30", "last": "00:20",
      "peakMin": 5, "offPeakMin": 10, "journeyMin": 45,
      "note": { "zh": "繁忙時間加密", "en": "More frequent at peak" }
    }]
  }],
  "etas": {
    "KMB1_O_01": {
      "generatedTimestamp": "2026-11-09T18:40:00+08:00",
      "etas": [{
        "seq": 1, "time": "18:46", "minutes": 5,
        "dest":   { "zh": "尖沙咀碼頭", "en": "STAR FERRY" },
        "remark": { "zh": "原定班次", "en": "Scheduled Bus" },
        "platform": "1", "scheduled": true
      }]
    }
  }
}
```

Field names follow DATA.GOV.HK conventions: `co`, `dir` = `O`/`I`, `seq`, `eta` timestamp, `rmk` remark — so switching to real APIs needs little model change. Stops without ETA show "No arrival data".

**`modes` / `fare` / `services` are mandatory for online updates.** `RouteDataUpdate.validate()` checks each route, so a half-updated dataset with missing fares or schedules can never be applied. Settings → Route data update simply consumes this JSON.

---

## Updating route data without a new APK

1. Set `OWNER` / `REPO` in `app/src/main/java/hk/eta/app/AppConfig.kt` (shared by data source, APK update and online history; `EtaRepository` reads `AppConfig.REMOTE_JSON_URL`).
2. Edit `app/src/main/assets/hketa.json` (four modes / fares / schedules) and push.
3. On the phone tap home-screen **Data update** → **Check & update route data**; once validated it applies. Subsequent **Refresh** re-fetches.

To revert instantly: Settings → **Restore bundled data**. Fetch or validation failures keep the existing data, so the screen never goes blank. gh-pages also works: point `AppConfig.REMOTE_JSON_URL` at `https://<OWNER>.github.io/<REPO>/hketa.json`.

---

## Connecting real live ETA and route data

`Fmt.kt` defines the `LiveEtaProvider` interface; `DemoEtaProvider` currently reads the JSON. For production, implement it with `HttpURLConnection` against the operator APIs and swap the one line in `EtaActivity.rebuild()`.

Official references (**always check the latest data dictionary for endpoints**):

- DATA.GOV.HK open data portal: https://data.gov.hk/en/
- KMB / LWB ETA API spec: https://data.etabus.gov.hk/datagovhk/kmb_eta_api_specification.pdf
- Citybus / NLB Stop-ETA API spec: https://static.data.gov.hk/opendata/eta/bus-route-list-and-eta-specific-stop-api-specifications.pdf
- Citybus / NLB Stop-ETA data dictionary: https://static.data.gov.hk/ogcio/datagovhk/opendata/eta/bus-route-list-and-eta-specific-stop-api-data-dictionary.pdf
- Multi-operator open-source reference: https://github.com/hkbus/hk-bus-eta

Typical response fields: `co`, `route`, `dir`, `seq`, `stop`, `dest_en/tc`, `eta_seq`, `eta` (ISO 8601), `rmk_en/tc`; MTR adds `plat` (platform) and `time`. Map `eta` into `Eta.time`, compute `minutes`, and the UI needs no change.

**Routes / fares / schedules** follow the same pattern: write an aggregator that converts bus, minibus and MTR (metro + light rail) route lists, fare tables and timetables into the `hketa.json` schema above (`modes` / `fare` / `services`), pass it through `RouteDataUpdate.validate()`, and distribute it via Settings → Route data update. Actual endpoints and licensing terms are governed by **DATA.GOV.HK and the operators' latest publications**; the bundled 30 routes and all fares are illustrative values, not official prices or timetables.

---

## Project structure

```
.github/workflows/build-apk.yml   # builds APK on push / v* tag
README.md                        # Traditional Chinese README
README.en.md                     # this English README
app/src/main/assets/hketa.json   # single data source
app/src/main/java/hk/eta/app/
  Models.kt            # data classes + AppState (language / data)
  JsonParser.kt        # org.json → data classes
  EtaRepository.kt     # bundled assets / GitHub raw sources
  RouteDataUpdate.kt   # 4-mode validation, stats, online update, cache, restore
  DataUpdater.kt       # shared "Refresh" entry point (same validation)
  Fmt.kt               # formatting (fares / schedules) + LiveEtaProvider hook
  AppConfig.kt         # GitHub OWNER / REPO (data + APK update + online history)
  MainActivity.kt      # search, filters, route list, startup signature check, settings entry
  SignatureVerifier.kt # SHA-256 check for installed / archive APKs
  UpdateChecker.kt     # app version, Releases / online history, download, cert check, install Intent
  ApkFileProvider.kt   # pure-framework content:// provider (API 26+ installs)
  SettingsActivity.kt  # signature / app version / APK update / route data / online history / About
  RouteDetailActivity.kt  # directions, stops, map
  EtaActivity.kt       # departure list, refresh
app/src/main/res/
  layout/ values/ drawable/           # XML layouts / colors / styles / chip backgrounds
  mipmap-mdpi … mipmap-xxxhdpi/       # ic_launcher.png + round (OEM fallback)
  drawable-nodpi/                     # adaptive foreground / background (512×512)
  mipmap-anydpi-v26/                  # adaptive icon (all devices from minSdk 26)
art/logo_source.png                  # original logo artwork, not packaged
art/icon_preview.png                 # icon preview, not packaged
scripts/gen_icon.py                  # regenerate all icon resources after replacing artwork
scripts/gen_keystore.sh              # one-time permanent signing key generation
scripts/validate.py                  # local consistency checks
```

---

## App logo

The launcher icon uses the supplied rainbow-bus artwork (original 1080×1080 in `art/logo_source.png`):

- **All supported devices (API 26+)**: the adaptive icon in `mipmap-anydpi-v26/`. Since the system shows only the central 72dp of a 108dp canvas, the script scales the artwork to 66.7% and centres it as the foreground over a full rainbow gradient background. Circular / squircle masks keep the robot head and wheels intact with no white edges; the right half of `art/icon_preview.png` is a simulated preview.
- **Fallback**: `mipmap-*/ic_launcher.png` (48 / 72 / 96 / 144 / 192 px) and `ic_launcher_round.png` for the few OEM launchers that ignore adaptive XML; `AndroidManifest.xml` also sets `android:roundIcon`. Normally unused at minSdk 26.

To replace it: overwrite `art/logo_source.png` with a square image, run `python3 scripts/gen_icon.py` from the repo root (requires Pillow), and all densities plus adaptive resources are rebuilt; `art/icon_preview.png` shows the result. Push, and the next Actions APK uses the new logo.

---

## Verification status & known limitations

- `python3 scripts/validate.py` passes locally: 30 routes / 355 stops / 18 ETA stops, four modes (`bus:12 / minibus:4 / metro:10 / light_rail:4`), XML resources, `R.id` bindings, toolchain pins, signing / update settings and the workflow are all consistent. CI runs the same check before building.
- **No compiled `.apk` is included in this repo**, and the authoring environment had no Android SDK, so the AGP 9 toolchain, `ContentProvider` install flow, dynamic lists and data cache have **not been compiled or run on a device here**. The first real build is the GitHub Actions run after you push.
- Bundled JSON is demo data: not live ETA, approximate coordinates, indicative fares and schedules. Not for travel planning.
- Online features need network access and a configured repo. Unauthenticated GitHub API: 60 requests / IP / hour.
- In-app install needs "Install unknown apps" enabled for HKETA in system settings.

---

## Credits & license

- Feature set adapted from [shamough1792/HKETA](https://github.com/shamough1792/HKETA) (MIT)
- Data-model concepts from [hkbus/hk-bus-eta](https://github.com/hkbus/hk-bus-eta) and DATA.GOV.HK data dictionaries
- This project is MIT — see `LICENSE`

Live transport data belongs to the Transport Department, the operators and the MTR Corporation. Routes, stops, schedules and coordinates in the demo JSON exist only to demonstrate the UI and must not be used as travel information.
