package com.hketa.app.data.model

/**
 * Domain models — kept identical in shape to the TypeScript contract so the
 * Android client and the Node.js API speak the same language.
 */

data class EtaEntry(
    val operator: String,
    val route: String,
    val direction: String,
    val destination: String,
    val eta: String?,
    val remark: String?,
    val seq: Int,
)

data class EtaResponse(
    val stopId: String,
    val generatedAt: String,
    val entries: List<EtaEntry>,
)
