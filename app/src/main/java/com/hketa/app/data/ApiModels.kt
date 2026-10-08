package com.hketa.app.data

import kotlinx.serialization.Serializable

/** 各營辦商 JSON 的通用外殼：{ "data": [...] } */
@Serializable
data class ApiList<T>(val data: List<T> = emptyList())

// ---------- 九巴 KMB ----------
@Serializable
data class KmbRoute(
    val route: String = "",
    val bound: String = "",
    val service_type: String = "",
    val orig_tc: String = "",
    val dest_tc: String = ""
)

@Serializable
data class KmbRouteStop(
    val route: String = "",
    val bound: String = "",
    val service_type: String = "",
    val seq: String = "",
    val stop: String = ""
)

@Serializable
data class KmbStop(
    val stop: String = "",
    val name_tc: String = "",
    val name_en: String = "",
    val lat: String = "",
    val long: String = ""
)

@Serializable
data class KmbEta(
    val route: String = "",
    val service_type: String = "",
    val eta_seq: String = "",
    val eta: String? = null,
    val rmk_tc: String = "",
    val dest_tc: String = ""
)

// ---------- 城巴 CTB ----------
@Serializable
data class CtbRoute(
    val route: String = "",
    val bound: String = "",
    val orig_tc: String = "",
    val dest_tc: String = ""
)

@Serializable
data class CtbRouteStop(
    val stop: String = "",
    val seq: String = ""
)

@Serializable
data class CtbStop(
    val stop: String = "",
    val name_tc: String = "",
    val name_en: String = "",
    val lat: String = "",
    val long: String = ""
)

@Serializable
data class CtbEta(
    val route: String = "",
    val dir: String = "",
    val eta: String? = null,
    val rmk_tc: String = "",
    val dest_tc: String = ""
)

// ---------- 港鐵巴士 MTR Bus ----------
@Serializable
data class MtrBusStop(
    val stopId: String = "",
    val nameTc: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

@Serializable
data class MtrBusEta(
    val routeName: String = "",
    val destination: String = "",
    val eta: String = "",
    val remark: String = ""
)
