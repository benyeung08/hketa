# HK ETA 交通 · Hong Kong Transit ETA & Route Info

A full-stack Hong Kong public-transit app: **TypeScript API** (Node.js + Express) aggregates official open data and serves a unified JSON contract; the **Kotlin Android** client consumes it with a clean MVVM architecture.

## Features

| Module | Details |
|---|---|
| 🚌 Bus ETA | Real-time arrivals from KMB / Citybus / NLB open-data feeds |
| 🚐 Route catalogue | Bus · Minibus · MTR · LRT — fare, full route, stops, service interval, first/last service |
| 🔄 Data updates | Per-mode or one-tap "update all" with versioned manifest + SHA-256 checksums |
| ⚙️ Settings | Update screen shows version, generated-at time, payload size for every mode |

## Architecture

```
hketa-app/
├── api/                        # TypeScript API server
│   ├── src/
│   │   ├── index.ts            # Express bootstrap
│   │   ├── etaService.ts       # ETA aggregation (KMB / Citybus / NLB)
│   │   ├── routeService.ts     # Route catalogue + fare + timetable + update engine
│   │   ├── routes/
│   │   │   ├── eta.ts          # GET /api/eta/:stopId
│   │   │   └── routes.ts       # GET/POST /api/routes, /api/updates
│   │   └── types/
│   │       └── eta.ts          # Shared contract (kept identical to Kotlin models)
│   ├── package.json
│   └── tsconfig.json
│
└── androidApp/                 # Kotlin Android app (MVVM)
    └── src/main/java/com/hketa/app/
        ├── HkEtaApp.kt         # Application, holds BASE_URL
        ├── data/
        │   ├── api/            # Retrofit service interfaces
        │   ├── model/          # Domain models (mirrors TS contract)
        │   └── repository/     # Single source of truth
        └── ui/                 # Activities + ViewModels + Adapters
```

## Quick start

### 1. Run the TypeScript API

```bash
cd api
npm install
npm run dev          # http://localhost:3000
```

### 2. Build the Android APK

```bash
cd androidApp
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

## API endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/eta/:stopId` | Live ETAs (query `?operators=KMB,CTB,NLB`) |
| GET | `/api/routes` | All routes across all modes |
| GET | `/api/routes/:mode` | Filter by `BUS` / `MINIBUS` / `MTR` / `LRT` |
| GET | `/api/route/:routeId` | Full route detail incl. stops |
| GET | `/api/routes/search/:query` | Search by route name / origin / destination |
| GET | `/api/updates/manifest` | Version manifest (version, checksum, size) |
| POST | `/api/updates/:mode` | Update a single mode |
| POST | `/api/updates` | Update all modes |

### Try it

```bash
curl http://localhost:3000/api/routes/MTR
curl http://localhost:3000/api/updates/manifest
curl -X POST http://localhost:3000/api/updates/BUS
```

## Try these stop IDs for ETA

| Operator | Stop ID | Notes |
|---|---|---|
| KMB | `A60AE774B09A5E44` | Route 40, 麗港城方向 |
| CTB | `001001` | Central district |

## Connecting the app to the API

`androidApp/src/main/java/com/hketa/app/HkEtaApp.kt`:

```kotlin
const val BASE_URL = "http://10.0.2.2:3000/api/"   // emulator → host
// const val BASE_URL = "http://192.168.x.x:3000/api/"  // physical device → your LAN IP
```

> For physical devices use your computer's LAN IP and make sure the device is on the same Wi-Fi.

## Data sources

- KMB — `https://data.etabus.gov.hk` (route, fare, timetable, stop-eta)
- Citybus / NLB — `https://rt.data.gov.hk/v1/transport/citybus-nwfb`
- MTR / LRT — operator reference datasets (no public key-compatible feed)

No API key required.

## License

MIT
