package com.hketa.app.data.model

/* =====================================================================
 * Route / fare / timetable / update models.
 * Shapes MUST stay identical to api/src/types/eta.ts.
 * =================================================================== */

data class RouteInfo(
    val id: String,
    val mode: String,            // BUS | MINIBUS | MTR | LRT
    val operator: String,
    val name: String,
    val origin: String,
    val destination: String,
    val fare: Double,
    val fareCurrency: String = "HKD",
    val fullFare: Double,
    val interval: Double,        // service interval in minutes
    val firstDeparture: String,  // HH:mm local HK
    val lastDeparture: String,
    val updatedAt: String,       // ISO-8601
)

data class RouteStop(
    val routeId: String,
    val seq: Int,
    val stopId: String,
    val stopName: String,
    val distanceM: Double? = null,
)

data class RouteWithStops(
    val id: String,
    val mode: String,
    val operator: String,
    val name: String,
    val origin: String,
    val destination: String,
    val fare: Double,
    val fareCurrency: String = "HKD",
    val fullFare: Double,
    val interval: Double,
    val firstDeparture: String,
    val lastDeparture: String,
    val updatedAt: String,
    val stops: List<RouteStop>,
)

data class DataVersion(
    val mode: String,
    val version: Int,
    val generatedAt: String,
    val checksum: String,
    val size: Int,
)

data class UpdateManifest(
    val generatedAt: String,
    val versions: List<DataVersion>,
)

data class UpdateResult(
    val mode: String,
    val status: String,          // up-to-date | updated | failed
    val fromVersion: Int,
    val toVersion: Int,
    val message: String? = null,
)

data class UpdateSummary(
    val generatedAt: String,
    val results: List<UpdateResult>,
)

data class RouteListResponse(
    val generatedAt: String,
    val routes: List<RouteInfo>,
)
