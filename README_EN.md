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
- **Stop search** — a dedicated tab for searching stops by name or official stop code (e.g. 沙田 / `KT492`), showing the routes that serve each stop
- **Route keypad** — a purpose-built keypad on the Routes tab: digits 0–9 plus round letter keys (A B C E K M N S X) and backspace, so you never switch between numeric and alphabetic layouts
- **Stop list** — tap a route to see its stops; tap a stop to see live arrivals
- **Arrival times** — minutes to arrival, destination and remarks, **auto-refreshing every 20 seconds**
- **Nearby stops (GPS)** — stops sorted by distance, with the routes serving each stop
- **Auto-locate on Home** — Home requests location and refreshes by itself every 60 seconds; no button to press. A coloured dot shows the current state (locating / located / no permission / not found / off)
- **Manual location picker** — if you would rather not grant GPS, pick one of 16 preset districts or transport hubs and Home uses that coordinate instead
- **Multiple operators** — KMB, Citybus (CTB), NLB, GMB minibuses, MTR Bus
- **MTR heavy rail** — 10 lines / 98 stations (Airport Express, East Rail, Tuen Ma, Tseung Kwan O, Tung Chung, Kwun Tong, Tsuen Wan, Island, South Island, Disneyland Resort)
- **Light Rail** — 11 regular routes / 68 stops (505, 507, 610, 614, 614P, 615, 615P, 705, 706, 751, 761P)
- **Rail data bundled** — official lines, stations and coordinates ship inside the app, so they work offline with no indexing wait
- **Three UI languages** — switch between 繁體中文, 简体中文 and English from Settings, no reinstall needed
- **App1933-style home** — opens straight to live arrivals at nearby stops, in the form "route → destination · X min", with official stop codes
- **Saved stops** — tap the heart on an arrivals screen; the Saved tab refreshes ETA for every saved stop at once
- **Route map** — "Show map" on the route-stops screen renders a real map (Leaflet + CARTO dark tiles) with basemap, streets, zoom and tappable stop names — no API key required
- **Home-screen widget** — shows the next departure at your saved stops, covering every operator
- **Update check** — Settings can check for updates straight from GitHub Releases and download the APK
- **Version history** — a bottom sheet listing every release, with an offline changelog bundled in the app
- **About screen** — version, data sources, privacy and licence in one place
- **ETA data repair** — Settings can re-download official data, or just clear the cache, when stops or arrivals go stale
- **Index diagnostics** — the stats page explains why a particular operator shows 0 instead of leaving you guessing

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

A fourth folder, `res/values-b+zh+Hans/`, uses **BCP 47 script matching** so any Simplified
locale — including `zh-SG` — resolves to Simplified rather than falling through to Traditional.

All three share exactly the same **208 keys**. Switching languages triggers a configuration change, so
Compose recomposes automatically with no manual Activity restart.

Arrival remarks (platform, service delayed, via Racecourse, number of cars) are assembled on the fly
in the data layer. `AppViewModel` loads the templates for the current language and passes them to
`EtaRepository` (see `data/EtaText.kt`), so a Simplified UI shows 经马场 / 服务延误 rather than the
Traditional wording.

Data that comes **from the official endpoints** — stop names, route names and the like — is left in
its original form (mostly Traditional Chinese) rather than translated, so it keeps matching the
official signage.

## Interface layout (modelled on App1933)

Four bottom tabs: **Home / Saved / Search / Settings**

| Tab | Contents |
|---|---|
| Home | Auto-locate → nearby stops → one card per departure (route badge + destination + minutes). Tapping a card opens that route's stop list directly |
| Saved | Saved stops; entering the tab fetches ETA for all of them concurrently |
| Search | Two independent tabs — **Routes** and **Stops** — each keeping its own query, with quick entries for Rail and Nearby at the top |
| Settings | Index management, ETA data repair, language, updates, data sources, About |

**Not included (unlike App1933)**: club1933 membership, eCoin wallet, KMB monthly pass, the games
room, the bot1933 AI assistant and live occupancy — these need KMB's private APIs or merchant
credentials. This project uses only open government data (data.gov.hk endpoints).

## Update mechanism

The Settings screen has a "Check for updates" button backed by the GitHub Releases API:

```
GET https://api.github.com/repos/benyeung08/hketa/releases/latest
```

- It compares the latest release `tag_name` against the installed version using **semantic version comparison** (so `1.0.0` correctly beats `1.0.0-beta1`), read from PackageManager so it always tracks `versionName`
- When a newer version exists it shows the version, release notes (localised with a `<!-- zh-CN -->` marker if present), a direct APK download button and a link to the release page
- Opening Settings triggers one silent check (throttled to 30 minutes); the button forces an immediate re-check
- If no release has ever been published it says so instead of showing an error

**To ship a new version**: bump `versionName` in `app/build.gradle.kts`, then push a `v*` tag —
CI builds the APK and attaches it to a Release, and the app picks it up.

## Getting the APK (fastest route)

This repo ships `.github/workflows/build-apk.yml`:

1. Push this project to your GitHub repo (the `main` branch)
2. Open the **Actions** tab, pick **Build HKETA APK**, then **Run workflow**
3. When it finishes, download the `hketa-release-apk` artifact
4. Transfer it to your phone and install (allow "install from unknown sources" the first time)

Pushing a `v*` tag will also attach the APK to a Release automatically. The workflow additionally
commits the APK to the `builds/` directory of the `apk` branch, so `latest.apk` is always downloadable
straight from the repo's file listing.

**Signing**: the repo ships a fixed keystore (`keystore/hketa.jks`, valid to 2056) plus a
base64 text copy (`hketa.jks.b64`) that CI restores if the binary is missing. Without a fixed key,
GitHub's fresh runner would generate a new debug key every build and users would hit
"package conflicts with an existing package" on upgrade.

## Building locally

Requirements: **JDK 17**, Android SDK (**platform 37** + **build-tools 36.0.0**), **Gradle 9.3.1**.

```bash
gradle :app:assembleDebug    # -> app/build/outputs/apk/debug/app-debug.apk
gradle :app:assembleRelease  # -> app/build/outputs/apk/release/app-release.apk
```

Or with Android Studio (Panda 3 / 2025.3.3 Patch 1 or newer — required for API 37):
File ▸ Open ▸ pick this folder → Gradle Sync → Run ▶.

The first launch builds the route index automatically (roughly 10–30 seconds depending on network).

## Support range: Android 8 – 17

| Setting | Value | Platform |
|---|---|---|
| `minSdk` | **26** | Android 8.0 Oreo (floor) |
| `targetSdk` | **37** | Android 17 Cinnamon Bun (ceiling) |
| `compileSdk` | **37** | Android 17 |

That means **Android 8, 9, 10, 11, 12, 12L, 13, 14, 15, 16 and 17 can all install it**.

**Toolchain hard minimums**: `compileSdk 37` requires **AGP ≥ 9.1.1 + Gradle ≥ 9.3.1 +
Build Tools 36.0.0 + JDK 17**. Anything less will not recognise API 37. CI is upgraded accordingly.

Two changes worth knowing:

1. **AGP 9 bundles Kotlin** — you must not declare `id("org.jetbrains.kotlin.android")` any more,
   or the build fails with `Cannot add extension with name 'kotlin'`. AGP decides the Kotlin version
   (9.1.1 → KGP 2.2.10).
2. **minSdk 26 needs no desugaring** — the project uses `java.time` (`TimeUtil`), which Android 8.0
   ships natively; enabling `coreLibraryDesugaring` would only bloat the APK. (It becomes mandatory
   if you ever lower minSdk to Android 7 or below.)

**Android 17 behaviour change**: on large screens (`sw > 600dp`) the system ignores
`screenOrientation`, so the app must adapt to any window size. The manifest keeps `portrait` —
still honoured on phones for an upright layout — while large screens become resizable automatically.
All layouts are Compose with `fillMaxSize` + scrolling, so nothing breaks at any size.

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
- **Citybus (CTB)** — no "all stops" endpoint, so stops are walked route by route. This now runs **automatically in the background** once the index is built (incremental + batched concurrent), so nearby stops pick up Citybus without any manual step
- **GMB minibus** — route list is complete; stops are fetched when you open a route (coordinates need extra requests, so they are not in nearby search by default). Also repaired automatically in the background
- **MTR Bus** — no indexing needed; entering a K-prefixed route number queries the official endpoint live
- **MTR heavy rail / Light Rail** — lines, station names and coordinates all ship in `assets/rail.json`, indexed with **zero network requests**, so nearby stops work immediately

The index is stored at `filesDir/hketa_index.json` and loaded on the next launch; you can
rebuild it any time from Settings.

## Project structure

```
app/src/main/java/com/hketa/app/
├─ data/       Models, ApiModels, Http, IndexStore, EtaRepository, RailData,
│              FavoritesStore, SpecialRoutesStore, Changelog, UpdateChecker,
│              Version, TimeUtil, SuspendCatching
│              (assets/rail.json = bundled rail station table, with official English names)
├─ location/   Location wrapper (Fused Location, falls back to LocationManager)
├─ util/       AppLocale (in-app language switching)
├─ vm/         AppViewModel (index, search, route stops, arrivals, nearby, auto-locate)
├─ widget/     HketaWidgetProvider (RemoteViews home-screen widget)
└─ ui/         AppNav, StatusBar, Format, RouteKeyboard, UpdateCenter and screens
               (Home / Saved / Search / Rail / Route stops / Arrivals / Nearby /
                Settings / About / RouteMap)

res/
├─ values/strings.xml          Traditional Chinese UI text (fallback)
├─ values-zh-rCN/strings.xml   Simplified Chinese UI text
├─ values-en/strings.xml       English UI text
├─ values-b+zh+Hans/           Simplified for any Simplified locale (BCP 47 script)
└─ drawable-*/mipmap-*/        adaptive icon (foreground + background)
```

Stack: Kotlin 2.2.10 (bundled in AGP 9.1.1), Jetpack Compose (Material 3), Navigation Compose,
OkHttp 4.12 + kotlinx.serialization, Coroutines/Flow + AndroidViewModel, Play Services Location.

## Known limitations

### Limitations that have been fixed

| Previous limitation | Now |
|---|---|
| No home-screen widget | **Added**: `widget/HketaWidgetProvider` (RemoteViews, no new dependency). Shows the next departure at your saved stops, with a sync icon for instant refresh and a 30-minute system update |
| No saved stops | **Added**: tap the heart on an arrivals screen; the Saved tab fetches ETA for every saved stop concurrently. Stored locally in SharedPreferences |
| No route map | **Added**: "Show map" on the route-stops screen. Now a **real map** — WebView + Leaflet + CARTO dark tiles, with basemap, streets, zoom and tappable stop names, and no API key or Maps SDK. Falls back to the Canvas schematic if loading fails |
| Stop names stayed Chinese in English | **Fixed**: reads the official `name_en` field (present in the KMB, Citybus and GMB APIs), so English mode shows the operator's own English names |
| Rail line names were Chinese only | **Fixed**: all 10 heavy rail lines carry official English names (Tuen Ma Line, Island Line…) in `rail.json` as `nameEn` |
| `zh-SG` fell back to Traditional | **Fixed**: added `values-b+zh+Hans/` (BCP 47 script matching), which ignores region and hits Simplified for any Simplified locale |
| Nearby ETA only worked for KMB | **Improved**: the home screen queries the first route at Citybus / NLB / GMB stops too (one request each), instead of listing route numbers only |
| No English rail station names | **Fixed**: `rail.json` bundles official English station names — all 98 heavy rail and 68 Light Rail stops, zero missing. These are the operator's own names, not translations |
| Light Rail specials (9xx) | **Fixed**: the Rail tab can "Scan for special trips". There is no official list, so it scans stop arrivals and subtracts the 11 regular routes to discover them dynamically — no hardcoded 9xx list, so it tracks whatever the operator runs |
| Citybus / GMB repair needed Wi-Fi | **Improved**: repair is now **batched concurrent** (8 routes per batch) and **incremental** (skips routes already fetched), cutting time and request count substantially |
| Nothing to see without location permission | **Fixed**: added a **manual location picker** — choose one of 16 preset districts or hubs and Home still finds nearby stops |
| The widget skipped Citybus / NLB / GMB | **Fixed**: the real cause wasn't "one request per route" — bookmarks simply never stored a route (both call sites passed `null`). Bookmarks now record the route, so the widget queries just that one route (a single request) and **covers every operator** |
| Citybus / GMB needed a manual repair | **Fixed**: repair now runs **automatically in the background** as soon as the index is built (incremental + concurrent, no toast, doesn't block you). It runs once and remembers; the manual buttons remain |
| Light Rail specials needed rescanning | **Improved**: scan results are **cached locally** (`filesDir/hketa_lr_special.json`), so the Rail tab shows them immediately after one scan |
| Viewed maps went blank offline | **Improved**: the WebView uses `LOAD_CACHE_ELSE_NETWORK`, so routes you've already viewed (Leaflet + tiles) render offline |

### Limitations that remain

- **NLB has no English stop names**: the official endpoint (`rt.data.gov.hk/.../nlb`) returns Chinese names only, with no `name_en` field.
  We **won't machine-translate these** — invented English names won't match the official signs, which makes finding your stop harder.
  NLB stop names stay Chinese in the English interface (KMB, Citybus, GMB and rail all have official English names).
- **The real map needs a network on first view**: viewed routes are cached and render offline, but viewing a route for the very first time still needs to fetch tiles and Leaflet.
  Fully offline maps would mean bundling tiles and a much larger APK, so that's not planned.
- **Light Rail specials need a scan first**: there's no official list endpoint, so the first scan is manual, and results reflect only what was running at scan time.
- **Nearby-stop coverage**: after the automatic background repair, Citybus and GMB stops are included. On a very first index build you'll see KMB first, with Citybus / GMB appearing once the background repair finishes (usually a minute or two).

## Privacy

The app only issues HTTPS requests to the official endpoints above and collects or uploads
no personal data. Location is used solely to sort nearby stops at that moment — it is never
stored or transmitted.
