# HK ETA — Hong Kong Public Transport ETA & Route Info App

> **HK ETA** is an open-source Android application that provides **real-time Estimated Time of Arrival (ETA)** and comprehensive route information for Hong Kong's public transport system, covering **Bus, Minibus, MTR, and Light Rail**.

---

## Table of Contents

- [Features](#features)
- [Screenshots / UI Overview](#screenshots--ui-overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Data Sources](#data-sources)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Step 1 — Run the TypeScript API](#step-1--run-the-typescript-api)
  - [Step 2 — Build the Android APK](#step-2--build-the-android-apk)
  - [Step 3 — Connect the App to the API](#step-3--connect-the-app-to-the-api)
- [API Endpoints](#api-endpoints)
- [Settings — Data Updates](#settings--data-updates)
- [Contributing](#contributing)
- [License](#license)

---

## Features

### Real-Time ETA
- Query live bus arrival times by stop ID
- Aggregates data from **KMB, Citybus, and NLB** into a unified response format
- No API key required — powered by the Hong Kong Government [DATA.GOV.HK](https://data.gov.hk/) open data platform

### Route Information (4 Transport Modes)

| Mode | What's Included |
|---|---|
| 🚌 **Bus** | Fares, full stop list, service frequency (headway / first & last departure), real-time updates |
| 🚐 **Minibus** | Route details, fares, service schedule |
| 🚇 **MTR** | All lines, fares, station list, frequency, service hours |
| 🚊 **Light Rail** | All routes, fares, stops, frequency |

### In-App Data Updates (Settings)
- **Update All** with one tap
- **Update individual modes** separately
- **Version display** — current version, last updated timestamp, data size
- **SHA-256 checksum verification** for data integrity
- **Update status feedback** — shows *Updated / Already up-to-date / Failed*

---

## Screenshots / UI Overview

| Screen | Description |
|---|---|
| **Main** | Search bar + transport-mode filter chips + route list |
| **Route Detail** | Stop list with fares and service frequency |
| **Settings** | Data update center — update all or per-mode, view version info |

---

## Architecture

```
┌──────────────────────────────────────────────┐
│         Android App (Kotlin)                 │
│  ┌─────────┐  ┌────────────┐  ┌──────────┐  │
│  │  View   │→ │ ViewModel  │→ │Repository│  │
│  └─────────┘  └────────────┘  └────┬─────┘  │
│                                      │        │
│                               Retrofit (HTTP) │
└──────────────────────────────────────┼────────┘
                                       │
                                       ▼
┌──────────────────────────────────────────────┐
│      TypeScript API (Node.js + Express)      │
│                                              │
│  ┌──────────────┐    ┌───────────────────┐   │
│  │  ETA Service  │    │  Route Service    │   │
│  │ KMB/CB/NLB    │    │ BUS/MINI/MTR/LRT  │   │
│  └──────┬───────┘    └─────────┬─────────┘   │
│         │                       │             │
│         ▼                       ▼             │
│  DATA.GOV.HK  +  Local Reference Datasets     │
└──────────────────────────────────────────────┘
```

The app follows **MVVM** architecture with a clear separation of concerns:
- **View** (Activity) — UI rendering & user interaction
- **ViewModel** — State management & business logic
- **Repository** — Single source of truth for data
- **Retrofit** — HTTP client for API communication

---

## Tech Stack

| Layer | Technology |
|---|---|
| Mobile | **Kotlin**, Android SDK (minSdk 24, targetSdk 34) |
| Architecture | MVVM, ViewModel, LiveData |
| Networking | Retrofit 2, OkHttp 4, Gson |
| Async | Kotlin Coroutines |
| API | **TypeScript**, Node.js, Express, ts-node |
| Build | Gradle (Kotlin DSL), npm |

---

## Project Structure

```
hketa-app/
├── README.md                 # Chinese documentation
├── README.en.md              # English documentation
├── LICENSE
│
├── api/                      # TypeScript aggregation API
│   ├── package.json
│   ├── tsconfig.json
│   └── src/
│       ├── index.ts          # Entry point, Express server
│       ├── etaService.ts     # ETA aggregation (KMB/Citybus/NLB)
│       ├── routeService.ts   # Route info service (4 modes)
│       ├── routes/
│       │   ├── eta.ts        # ETA REST endpoints
│       │   └── routes.ts     # Route REST endpoints
│       └── types/
│           └── eta.ts        # Shared TypeScript types
│
└── androidApp/               # Native Android (Kotlin)
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── gradle.properties
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/hketa/app/
        │   ├── HkEtaApp.kt               # Application class
        │   ├── data/
        │   │   ├── api/
        │   │   │   ├── EtaApiService.kt  # Retrofit API interface
        │   │   │   └── RouteApiService.kt
        │   │   ├── model/
        │   │   │   ├── EtaModels.kt      # ETA data classes
        │   │   │   └── RouteModels.kt     # Route data classes
        │   │   └── repository/
        │   │       ├── EtaRepository.kt
        │   │       └── RouteRepository.kt
        │   └── ui/
        │       ├── MainActivity.kt        # Home: search + route list
        │       ├── RouteDetailActivity.kt # Route detail view
        │       ├── SettingsActivity.kt    # Data update center
        │       ├── RouteViewModel.kt
        │       ├── RouteAdapter.kt
        │       └── UpdateAdapter.kt
        └── res/
            ├── layout/        # XML layouts
            ├── menu/          # Toolbar menus
            ├── values/        # Strings & themes
            └── drawable/      # Icons & graphics
```

---

## Data Sources

All data is fetched from publicly available open-data sources:

| Operator | Source |
|---|---|
| **KMB** (九巴) | [DATA.GOV.HK — KMB ETA API](https://data.gov.hk/) |
| **Citybus** (城巴) | [DATA.GOV.HK — Citybus ETA API](https://data.gov.hk/) |
| **NLB** (新大嶼山巴士) | [DATA.GOV.HK — NLB ETA API](https://data.gov.hk/) |
| **MTR** (港鐵) | [MTR Station Facilities & Route Data](https://www.mtr.com.hk/) |
| **Light Rail** | [MTR Light Rail Route Data](https://www.mtr.com.hk/) |
| **Minibus** | Public reference datasets |

> No API key is required for the ETA endpoints. The TypeScript API acts as an aggregation layer that normalizes responses from multiple providers into a single consistent format.

---

## Getting Started

### Prerequisites

| Tool | Version |
|---|---|
| **Node.js** | ≥ 18.x |
| **npm** | ≥ 9.x |
| **JDK** | 17 (Temurin / OpenJDK recommended) |
| **Android SDK** | API 34 platform installed |
| **Gradle** | 8.4 (wrapper included) |

### Step 1 — Run the TypeScript API

```bash
cd api
npm install
npm run dev
```

The API server will start on **http://localhost:3000**.

### Step 2 — Build the Android APK

```bash
cd androidApp
./gradlew assembleDebug
```

The APK will be generated at:

```
androidApp/app/build/outputs/apk/debug/app-debug.apk
```

### Step 3 — Connect the App to the API

Open `EtaApiService.kt` (or the `BASE_URL` constant in the app) and update the base URL:

| Environment | BASE_URL |
|---|---|
| **Android Emulator** | `http://10.0.2.2:3000/api/` |
| **Physical Device** (same Wi-Fi) | `http://<YOUR_COMPUTER_LAN_IP>:3000/api/` |

> ⚠️ **Physical device note:** Use your computer's LAN IP address (e.g. `192.168.1.100`). Also ensure your computer's firewall allows inbound connections on port 3000.

---

## API Endpoints

### ETA

```
GET /api/eta/:stopId              # Real-time ETA for a stop
GET /api/eta/:stopId?operators=KMB # Filter by operator(s)
```

**Example response:**

```json
{
  "stopId": "A60AE774B09A5E44",
  "etas": [
    {
      "route": "104",
      "destination": "堅尼地城 Kennedy Town",
      "etaTime": "3 分鐘 / 3 minutes",
      "operator": "KMB"
    }
  ]
}
```

### Routes

```
GET  /api/routes                  # All routes across all modes
GET  /api/routes/BUS              # Filter by mode (BUS/MINIBUS/MTR/LRT)
GET  /api/route/:routeId          # Route detail with full stop list
```

**Example response (route detail):**

```json
{
  "routeId": "KMB-104",
  "name": "104",
  "mode": "BUS",
  "origin": "白田邨",
  "destination": "堅尼地城",
  "fare": "$6.40",
  "frequency": "6-10 分鐘 / 6-10 minutes",
  "serviceHours": "05:40 - 00:15",
  "stops": [
    { "name": "白田邨", "fare": "$6.40" },
    { "name": "石硤尾街市", "fare": "$6.40" }
  ]
}
```

### Data Updates

```
GET  /api/updates/manifest        # Version manifest for all modes
POST /api/updates/:mode          # Update a single mode
POST /api/updates                # Update all modes
```

---

## Settings — Data Updates

The **Settings** screen (`SettingsActivity`) is the central hub for keeping route data up to date:

1. **Update All** — fetches the latest data for all 4 transport modes in one tap
2. **Per-Mode Update** — tap any mode in the list to update it individually
3. **Version Info** — shows current version, last updated timestamp, and data size for each mode
4. **Integrity Check** — API returns a SHA-256 checksum after each update
5. **Status Feedback** — each item displays one of three states:
   - ✅ **Updated** — new data was downloaded and applied
   - ℹ️ **Already up-to-date** — no newer version available
   - ❌ **Failed** — network or server error (retry available)

---

## Example Stop IDs for Testing ETA

| Stop ID | Location |
|---|---|
| `A60AE774B09A5E44` | 彌敦道 Nathan Road |
| `7E99CAC9E7A3636B` | 旺角 Mong Kok |
| `FE23185ABAE7B3FA` | 中環 Central |

---

## Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes (`git commit -m 'Add some feature'`)
4. Push to the branch (`git push origin feature/your-feature`)
5. Open a Pull Request

For bug reports or feature requests, please open an issue on GitHub.

---

## License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

<div align="center">
  <p>Made with ❤️ for Hong Kong commuters</p>
</div>
