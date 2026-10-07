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
- **Bilingual UI** — switch between 繁體中文 and English from Settings, no reinstall needed

## Interface language

The UI ships in **Traditional Chinese** and **English**; pick one at the top of the Settings screen:

| Option | Behaviour |
|---|---|
| Follow system | Matches the device language (default) |
| 繁體中文 | Force Traditional Chinese |
| English | Force English |

Implemented with `AppCompatDelegate.setApplicationLocales`:

- **Android 13 (API 33)+** — writes to the system per-app language setting, so it can also be changed from system Settings
- **Android 12 and below** — persisted by AppCompat itself (the manifest registers `AppLocalesMetadataHolderService` with `autoStoreLocales=true`), so the choice survives restarts

All UI text lives in `res/values/strings.xml` (Traditional Chinese) and `res/values-en/strings.xml`
(English). Switching languages triggers a configuration change, so Compose recomposes automatically
with no manual Activity restart. Data that comes **from the official endpoints** — stop names, route
names and the like — is left in its original form (mostly Traditional Chinese) rather than translated.

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
├─ util/       AppLocale (in-app language switching)
├─ vm/         AppViewModel (index state, search, route stops, arrivals, nearby stops)
└─ ui/         AppNav and screens (Search / Rail / Route stops / Arrivals / Nearby / Settings)

res/
├─ values/strings.xml      Traditional Chinese UI text
├─ values-en/strings.xml   English UI text
└─ drawable-*/mipmap-*/    adaptive icon (foreground + background)
```

Stack: Kotlin 2.0.21, Jetpack Compose (Material 3), Navigation Compose,
OkHttp 4.12 + kotlinx.serialization, Coroutines/Flow + AndroidViewModel,
Play Services Location. minSdk 26 (Android 8.0) / targetSdk 35.

## Known limitations

- Not implemented yet: home-screen widget, favourite stops, route map
- Stop and route names returned by the official endpoints are mostly Traditional Chinese only, so they stay in their original form even when the UI is in English
- The heavy rail endpoint only covers the 10 lines listed by MTR; Light Rail lists the 11 regular routes (special 9xx services are not listed separately but still show up in arrivals)
- "Nearby stops" coverage depends on how much has been indexed: rail stations and KMB are complete by default, while Citybus and GMB need a deep index run from Settings
- NLB / GMB / MTR Bus occasionally change their response fields. Parsing is lenient, but always treat the official documentation as the source of truth.

## Privacy

The app only issues HTTPS requests to the official endpoints above and collects or uploads
no personal data. Location is used solely to sort nearby stops at that moment — it is never
stored or transmitted.
