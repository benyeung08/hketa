package hk.eta.app.ui

import android.content.Context

/** Lightweight favourites store: "COMPANY|ROUTE|STOP_ID". */
class Favorites(context: Context) {

    private val sp = context.getSharedPreferences("hketa_favorites", Context.MODE_PRIVATE)

    fun all(): List<String> =
        (sp.getStringSet(KEY, emptySet()) ?: emptySet()).toList().sorted()

    fun contains(key: String): Boolean = all().contains(key)

    fun add(key: String) {
        val updated = (sp.getStringSet(KEY, emptySet()) ?: emptySet()).toMutableSet()
        updated.add(key)
        sp.edit().putStringSet(KEY, updated).apply()
    }

    fun remove(key: String) {
        val updated = (sp.getStringSet(KEY, emptySet()) ?: emptySet()).toMutableSet()
        updated.remove(key)
        sp.edit().putStringSet(KEY, updated).apply()
    }

    private companion object {
        const val KEY = "keys"
    }
}
