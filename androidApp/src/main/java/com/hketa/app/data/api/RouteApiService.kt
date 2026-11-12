package com.hketa.app.data.api

import com.hketa.app.data.model.*
import retrofit2.http.*

/**
 * Retrofit interface for the route / fare / timetable / update endpoints
 * exposed by the TypeScript API (see api/src/routes/routes.ts).
 */
interface RouteApiService {

    @GET("routes")
    suspend fun getAllRoutes(): RouteListResponse

    @GET("routes/{mode}")
    suspend fun getRoutesByMode(@Path("mode") mode: String): RouteListResponse

    @GET("route/{routeId}")
    suspend fun getRoute(@Path("routeId") routeId: String): RouteWithStops

    @GET("routes/search/{query}")
    suspend fun searchRoutes(@Path("query") query: String): RouteListResponse

    @GET("updates/manifest")
    suspend fun getManifest(): UpdateManifest

    @POST("updates/{mode}")
    suspend fun updateMode(@Path("mode") mode: String): UpdateResult

    @POST("updates")
    suspend fun updateAll(): UpdateSummary
}
