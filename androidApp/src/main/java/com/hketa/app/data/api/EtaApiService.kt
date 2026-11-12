package com.hketa.app.data.api

import com.hketa.app.data.model.EtaResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface matching the TypeScript API routes defined in
 * `api/src/routes/eta.ts`. Swap operators via the query param to limit the
 * upstream lookup (KMB | CTB | NLB).
 */
interface EtaApiService {

    @GET("eta/{stopId}")
    suspend fun getEtas(
        @Path("stopId") stopId: String,
        @Query("operators") operators: String? = null,
    ): EtaResponse
}
