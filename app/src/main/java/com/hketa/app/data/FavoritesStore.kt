package com.hketa.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 一筆收藏：車站 + 可選嘅指定路線（唔揀路線就顯示嗰個站全部路線） */
@Serializable
data class FavoriteStop(
    val op: Operator = Operator.KMB,
    val id: String = "",
    val name: String = "",
    val nameEn: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val route: String = "",       // 空 = 全部路線
    val routeId: String = "",
    val dest: String = ""
) {
    val key: String get() = "${op.name}|$id|${route.ifBlank { "*" }}"
}

/** 收藏車站：存 SharedPreferences，唔使網絡、唔使登入 */
class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("hketa_favorites", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * 優先由 **Rust store**（native heap）讀；Rust 唔喺度就用 `SharedPreferences`。
     *
     * Rust 有但係冇資料嗰陣，會**順手搬遷**舊嘅 SharedPreferences 資料
     * —— 所以升級上嚟嘅用戶唔會唔見咗收藏。
     */
    fun all(): List<FavoriteStop> {
        if (RustStore.available) {
            val raw = RustStore.get(NS, KEY)
            if (raw != null) {
                return runCatching { json.decodeFromString<List<FavoriteStop>>(raw) }
                    .getOrDefault(emptyList())
            }
            val old = runCatching {
                prefs.getString(KEY, null)?.let { json.decodeFromString<List<FavoriteStop>>(it) }
            }.getOrNull()
            if (!old.isNullOrEmpty()) {
                save(old)          // 搬遷
                return old
            }
            return emptyList()
        }
        return runCatching {
            val raw = prefs.getString(KEY, null) ?: return emptyList()
            json.decodeFromString<List<FavoriteStop>>(raw)
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<FavoriteStop>) {
        if (RustStore.available) {
            // 收藏係用戶關鍵資料 —— 寫完即刻落碟，唔好等 flush
            if (RustStore.put(NS, KEY, json.encodeToString(list))) RustStore.flush()
            return
        }
        runCatching { prefs.edit().putString(KEY, json.encodeToString(list)).apply() }
    }

    fun contains(key: String): Boolean = all().any { it.key == key }

    fun add(f: FavoriteStop) {
        val list = all().toMutableList()
        if (list.none { it.key == f.key }) {
            list.add(0, f)   // 新收藏排最前
            save(list)
        }
    }

    fun remove(key: String) {
        save(all().filterNot { it.key == key })
    }

    fun toggle(f: FavoriteStop) {
        if (contains(f.key)) remove(f.key) else add(f)
    }

    fun moveToTop(key: String) {
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.key == key }
        if (i > 0) {
            val item = list.removeAt(i)
            list.add(0, item)
            save(list)
        }
    }

    fun clear() = save(emptyList())

    companion object {
        private const val KEY = "favorites"
        /** Rust store 嘅命名空間 */
        private const val NS = RustStore.NS_FAVORITES

        fun from(stop: StopDef, route: RouteDef? = null): FavoriteStop = FavoriteStop(
            op = stop.op,
            id = stop.id,
            name = stop.name,
            nameEn = stop.nameEn,
            lat = stop.lat,
            lon = stop.lon,
            route = route?.route.orEmpty(),
            routeId = route?.routeId.orEmpty(),
            dest = route?.dest.orEmpty()
        )
    }
}
