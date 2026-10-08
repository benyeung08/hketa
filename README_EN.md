# HKETA — Hong Kong Bus ETA (Native Android App)

[繁體中文](README.md) | **English**

A complete native rewrite of `benyeung08/etaapp` — a WebToApp fork whose core was nothing
but a WebView wrapper around a web page. HKETA is a real native app: **Kotlin + Jetpack
Compose**, calling the official ETA endpoints of Hong Kong transport operators directly.
**No WebView, no web shell.**

> The native rewrite is complete: everything under `app/src/main/java` now lives in
> `com.hketa.app`. The original WebToApp `assets/`, `cpp/`, `proto/`, `schemas/` and the
> WebView activity have all been removed.

## How it differs from the original project

| | Original etaapp (WebToApp fork) | HKETA |
|---|---|---|
| Rendering | WebView loading a page (e.g. hkbus.app) | Native Compose UI — lists and cards drawn by the app |
| Data fetching | Handled by the web page | App calls the official APIs directly (OkHttp) |
| Offline | Depends on the browser cache | Route/stop index cached in internal storage, searchable offline |
| Location | Requested by the web page | Fused Location (falls back to system LocationManager when GMS is absent) |
| Extensibility | Limited by the web page | Clean split between data, index and UI layers |

## Features

- **Route search** — type a route number (e.g. 1A, 6, K51) to list routes and directions across operators
- **Stop list** — tap a route to see its stops; tap a stop to see live arrivals
- **Arrival times** — minutes to arrival, destination and remarks, **auto-refreshing every 20 seconds**
- **Nearby stops (GPS)** — stops within 3 km of your location, sorted by distance, with the routes serving each stop
- **Multiple operators** — KMB, Citybus (CTB), NLB, GMB minibuses, MTR Bus
- **MTR heavy rail** — 10 lines / 98 stations (Airport Express, East Rail, Tuen Ma, Tseung Kwan O, Tung Chung, Kwun Tong, Tsuen Wan, Island, South Island, Disneyland Resort)
- **Light Rail** — 11 regular routes / 68 stops (505, 507, 610, 614, 614P, 615, 615P, 705, 706, 751, 761P)
- **Rail data bundled** — official lines, stations and coordinates ship inside the app, so they work offline with no indexing wait
- **Three UI languages** — switch between 繁體中文, 简体中文 and English from Settings, no reinstall needed
- **App1933-style home** — opens straight to live arrivals at nearby stops, in the form "route → destination · X min", with official stop codes
- **Saved stops** — tap the heart on an arrivals screen; the Saved tab refreshes ETA for every saved stop at once
- **Update check** — Settings can check for updates straight from GitHub Releases and download the APK

## Interface language

The UI ships in **Traditional Chinese**, **Simplified Chinese** and **English**; pick one at the top of
the Settings screen:

| Option | Resource folder | Behaviour |
|---|---|---|
| Follow system | — | Matches the device language (default): `zh-CN` → Simplified, `zh-TW`/`zh-HK` → Traditional, other locales → English |
| 繁體中文 | `values/` | Force Traditional Chinese |
| 简体中文 | `values-zh-rCN/` | Force Simplified Chinese |
| English | `values-en/` | Force English |

Implemented with `AppCompatDelegate.setApplicationLocales`:

- **Android 13 (API 33)+** — writes to the system per-app language setting, so it can also be changed from system Settings
- **Android 12 and below** — persisted by AppCompat itself (the manifest registers `AppLocalesMetadataHolderService` with `autoStoreLocales=true`), so the choice survives restarts

All UI text lives in three resource files:

| File | Language |
|---|---|
| `res/values/strings.xml` | Traditional Chinese (also the fallback when nothing matches) |
| `res/values-zh-rCN/strings.xml` | Simplified Chinese |
| `res/values-en/strings.xml` | English |

All three share exactly the same 98 keys. Switching languages triggers a configuration change, so
Compose recomposes automatically with no manual Activity restart.

Arrival remarks (platform, service delayed, via Racecourse, number of cars) are assembled on the fly
in the data layer. `AppViewModel` loads the templates for the current language and passes them to
`EtaRepository` (see `data/EtaText.kt`), so a Simplified UI shows 经马场 / 服务延误 rather than the
Traditional wording.

Data that comes **from the official endpoints** — stop names, route names and the like — is left in
its original form (mostly Traditional Chinese) rather than translated.

## Interface layout (modelled on App1933)

Four bottom tabs: **Home / Saved / Search / Settings**

| Tab | Contents |
|---|---|
| Home | Location → stops within 800 m → live arrivals per stop (one KMB request covers every route at that stop) |
| Saved | Saved stops; entering the tab fetches ETA for all of them concurrently |
| Search | Search by route number or stop name; quick entries for Rail and Nearby at the top |
| Settings | Index management, language, updates, data sources |

**Not included (unlike App1933)**: club1933 membership, eCoin wallet, KMB monthly pass, the games
room, the bot1933 AI assistant, live occupancy and route maps — these need KMB's private APIs or
merchant credentials. This project uses only open government data (data.gov.hk endpoints).

## Update mechanism

The Settings screen has a "Check for updates" button backed by the GitHub Releases API:

```
GET https://api.github.com/repos/benyeung08/hketa/releases/latest
```

- It compares the latest release `tag_name` against the installed version (read from PackageManager, so it always tracks `versionName`)
- When a newer version exists it shows the version, release notes, a direct APK download button and a link to the release page
- Opening Settings triggers one silent check (throttled to 30 minutes); the button forces an immediate re-check
- If no release has ever been published it says so instead of showing an error

**To ship a new version**: bump `versionName` in `app/build.gradle.kts`, then push a `v*` tag —
CI builds the APK and attaches it to a Release, and the app picks it up.

## Getting the APK (fastest route)

This repo ships `.github/workflows/build-apk.yml`:

1. Push this project to your GitHub repo (the `main` branch)
2. Open the **Actions** tab, pick **Build HKETA APK**, then **Run workflow**
3. When it finishes, download the `hketa-apk` artifact (contains both debug and release APKs)
4. Transfer it to your phone and install (allow "install from unknown sources" the first time)

Pushing a `v*` tag will also attach the APK to a Release automatically.

## Building locally

Requirements: JDK 17, Android SDK (platform 35 + build-tools 35.0.0), Gradle 8.9.

```bash
gradle :app:assembleDebug    # -> app/build/outputs/apk/debug/app-debug.apk
gradle :app:assembleRelease  # -> app/build/outputs/apk/release/app-release.apk
```

Or with Android Studio (Ladybug or newer): File ▸ Open ▸ pick this folder → Gradle Sync → Run ▶.
If Studio complains about the Gradle wrapper, delete `gradle/wrapper` and re-sync so Studio
regenerates it, or point Settings ▸ Build Tools ▸ Gradle at a local Gradle 8.9 install.

The first launch builds the route index automatically (roughly 10–30 seconds depending on network).

## Data sources (official public endpoints, no API key)

| Operator | Purpose | Endpoint |
|---|---|---|
| KMB | Routes / route-stop / stops | `data.etabus.gov.hk/v1/transport/kmb/route/`, `.../route-stop/`, `.../stop` |
| KMB | Arrival times | `data.etabus.gov.hk/v1/transport/kmb/stop-eta/{stopId}` |
| Citybus (CTB) | Routes / route-stop / stops | `rt.data.gov.hk/v2/transport/citybus/route/CTB`, `.../route-stop/CTB/{route}/{inbound｜outbound}`, `.../stop/{id}` |
| Citybus (CTB) | Arrival times | `rt.data.gov.hk/v2/transport/citybus/eta/CTB/{stopId}/{route}` |
| NLB | Routes / stops / arrivals | `rt.data.gov.hk/v2/transport/nlb/route.php?action=list`, `stop.php?action=list&routeId=`, `stop.php?action=estimatedArrivals` |
| GMB minibus | Routes / route-stop / arrivals | `data.etagmb.gov.hk/route/{region}`, `route-stop/{routeId}/{seq}`, `stop-eta/{stopId}/{routeId}` |
| MTR Bus | Stops + arrivals (POST) | `rt.data.gov.hk/v1/transport/mtr/bus/getSchedule` |
| MTR heavy rail | Arrival times | `rt.data.gov.hk/v1/transport/mtr/getSchedule.php?line={AEL｜TML…}&sta={station code}&lang=TC` |
| Light Rail | Arrival times | `rt.data.gov.hk/v1/transport/mtr/lrt/getSchedule?station_id={stop id}&with_special=1` |
| MTR | Lines / stations / coordinates (bundled) | MTR open data `mtr_lines_and_stations.csv`, `light_rail_routes_and_stops.csv` |

## Indexing strategy (why some features take a moment)

- **KMB** — three requests build the full index (routes, route-stop mapping, all stops with coordinates) → best coverage for "nearby stops"
- **NLB** — few routes, so stops are fetched during indexing
- **Citybus (CTB)** — no "all stops" endpoint, so stops are fetched on demand; to include Citybus in "nearby stops", run **Deep-index Citybus** in Settings (walks every route for route-stop data and coordinates — a few minutes, Wi-Fi recommended)
- **GMB minibus** — route list is complete; stops are fetched when you open a route (coordinates need extra requests, so they are not in nearby search by default)
- **MTR Bus** — no indexing needed; entering a K-prefixed route number queries the official endpoint live
- **MTR heavy rail / Light Rail** — lines, station names and coordinates all ship in `assets/rail.json`, indexed with **zero network requests**, so nearby stops work immediately

The index is stored at `filesDir/hketa_index.json` and loaded on the next launch; you can
rebuild it any time from Settings.

## Project structure

```
app/src/main/java/com/hketa/app/
├─ data/       Models, ApiModels, Http, IndexStore, EtaRepository, RailData, TimeUtil
│              (assets/rail.json = bundled rail station table)
├─ location/   Location wrapper (Fused Location, falls back to LocationManager)
├─ util/       AppLocale (in-app language switching: Traditional / Simplified / English / system)
├─ vm/         AppViewModel (index state, search, route stops, arrivals, nearby stops)
└─ ui/         AppNav and screens (Search / Rail / Route stops / Arrivals / Nearby / Settings)

res/
├─ values/strings.xml          Traditional Chinese UI text (fallback)
├─ values-zh-rCN/strings.xml   Simplified Chinese UI text
├─ values-en/strings.xml       English UI text
└─ drawable-*/mipmap-*/        adaptive icon (foreground + background)
```

Stack: Kotlin 2.0.21, Jetpack Compose (Material 3), Navigation Compose,
OkHttp 4.12 + kotlinx.serialization, Coroutines/Flow + AndroidViewModel,
Play Services Location. minSdk 26 (Android 8.0) / targetSdk 35.

## Known limitations

### Limitations that have been fixed

| Previous limitation | Now |
|---|---|
| No home-screen widget | **Added**: `widget/HketaWidgetProvider` (RemoteViews, no new dependency). Shows the next departure at your saved stops, with a sync icon for instant refresh and a 30-minute system update |
| No saved stops | **Added**: tap the heart on an arrivals screen; the Saved tab fetches ETA for every saved stop concurrently. Stored locally in SharedPreferences |
| No route map | **Added**: "Show map" on the route-stops screen draws the path on a Canvas from stop coordinates (termini highlighted). No dependency, no API key; routes without coordinates say so |
| Stop names stayed Chinese in English | **Fixed**: reads the official `name_en` field (present in the KMB, Citybus and GMB APIs), so English mode shows the operator's own English names |
| Rail line names were Chinese only | **Fixed**: all 10 heavy rail lines now carry official English names (Tuen Ma Line, Island Line…) in `rail.json` as `nameEn` |
| `zh-SG` fell back to Traditional | **Fixed**: added `values-b+zh+Hans/` (BCP 47 script matching), which ignores region and hits Simplified for any Simplified locale |
| Nearby ETA only worked for KMB | **Improved**: the home screen now queries the first route at Citybus / NLB / GMB stops too (one request each), instead of listing route numbers only |

### Limitations that remain

- **No English rail station names**: the official station table (`mtr_lines_and_stations.csv`) is Chinese-only, so station names stay Chinese in English mode. Line names are English.
- **Light Rail specials (9xx)**: not listed as separate routes in the Rail tab, but they **do appear in the arrivals list** for any Light Rail stop you query.
- **NLB has no English stop names**: the official endpoint is Chinese-only.
- **Nearby-stop coverage**: rail stops and KMB are complete; Citybus and GMB need a deep index from Settings first.
- **The map is schematic**: coordinates are projected onto a Canvas with no basemap or streets — good for seeing the shape and relative position, but a real map would need a Maps SDK and API key.
- **The widget skips Citybus / NLB / GMB**: those need one request per route, which is too chatty for a widget. It shows ETA for saved KMB, Light Rail and heavy rail stops, and just the route number for the rest.

## Privacy

The app only issues HTTPS requests to the official endpoints above and collects or uploads
no personal data. Location is used solely to sort nearby stops at that moment — it is never
stored or transmitted.
