package com.hketa.app.data.repository

import com.hketa.app.data.api.RouteApiService
import com.hketa.app.data.model.*

/**
 * Single source of truth for transit-route data. Wraps the remote TypeScript
 * API; a Room-backed cache can be slotted in here without touching the UI.
 */
class RouteRepository(private val api: RouteApiService) {

    suspend fun getAllRoutes(): List<RouteInfo> = api.getAllRoutes().routes

    suspend fun getRoutesByMode(mode: String): List<RouteInfo> = api.getRoutesByMode(mode).routes

    suspend fun getRoute(routeId: String): RouteWithStops = api.getRoute(routeId)

    suspend fun searchRoutes(query: String): List<RouteInfo> = api.searchRoutes(query).routes

    suspend fun getManifest(): UpdateManifest = api.getManifest()

    suspend fun updateMode(mode: String): UpdateResult = api.updateMode(mode)

    suspend fun updateAll(): UpdateSummary = api.updateAll()
}
