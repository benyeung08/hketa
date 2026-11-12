package com.hketa.app.data.repository

import com.hketa.app.data.api.EtaApiService
import com.hketa.app.data.model.EtaEntry

/**
 * Repository: the single source of truth consumed by the ViewModel / UI.
 * Today it only reads from the remote TypeScript API; cache / Room persistence
 * can be slotted in here without touching the UI.
 */
class EtaRepository(private val api: EtaApiService) {

    suspend fun getEtas(stopId: String, operators: String? = null): List<EtaEntry> =
        api.getEtas(stopId, operators).entries
}
