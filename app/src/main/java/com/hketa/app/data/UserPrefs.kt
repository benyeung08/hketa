package com.hketa.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 對齊 hkbus.app 而補嘅「用戶側」資料：瀏覽歷史、自訂收藏集、外觀設定。
 *
 * 設計：**單一 object + SharedPreferences + StateFlow**。
 *  - 唔依賴 ViewModel，任何頁面／Activity 都讀到同一份
 *  - 改完即刻 emit，MainActivity 嘅 HketaTheme 會自動重組（唔使重啟 App）
 *  - 全部 @Serializable + ignoreUnknownKeys，舊版升級唔會 crash
 *  - 純本地，無網絡、無帳號、無追蹤（同「No ads, no tracking」一致）
 */
@Serializable
data class RecentVisit(
    /** "route" / "stop" / "journey" / "fare" */
    val kind: String = "",
    val key: String = "",
    val label: String = "",
    val atMs: Long = 0L
)

@Serializable
data class CollectionGroup(
    val name: String = "",
    /** FavoriteStop.key 列表 */
    val keys: List<String> = emptyList()
)

@Serializable
data class UserData(
    val recent: List<RecentVisit> = emptyList(),
    val groups: List<CollectionGroup> = emptyList()
)

object UserPrefs {

    private const val PREF = "hketa_user"
    private const val K_DATA = "data"
    private const val K_DARK = "dark_mode"
    private const val K_FONT = "font_scale"
    private const val MAX_RECENT = 30

    /** 0 = 跟隨系統、1 = 淺色、2 = 深色 */
    const val DARK_SYSTEM = 0
    const val DARK_LIGHT = 1
    const val DARK_NIGHT = 2

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val _darkMode = MutableStateFlow(DARK_SYSTEM)
    val darkMode: StateFlow<Int> = _darkMode.asStateFlow()

    private val _fontScale = MutableStateFlow(1.0f)
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    private val _data = MutableStateFlow(UserData())
    val data: StateFlow<UserData> = _data.asStateFlow()

    @Volatile
    private var prefs: SharedPreferences? = null

    /** 由 MainActivity.onCreate 叫一次；重複叫安全（唔會覆蓋已改嘅值） */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs = p
        _darkMode.value = p.getInt(K_DARK, DARK_SYSTEM)
        _fontScale.value = p.getFloat(K_FONT, 1.0f).coerceIn(0.85f, 1.40f)
        _data.value = runCatching {
            p.getString(K_DATA, null)?.let { json.decodeFromString<UserData>(it) }
        }.getOrNull() ?: UserData()
    }

    private fun persistData() {
        runCatching {
            prefs?.edit()?.putString(K_DATA, json.encodeToString(_data.value))?.apply()
        }
    }

    // ── 外觀 ──
    fun setDarkMode(mode: Int) {
        val m = mode.coerceIn(DARK_SYSTEM, DARK_NIGHT)
        _darkMode.value = m
        runCatching { prefs?.edit()?.putInt(K_DARK, m)?.apply() }
    }

    fun setFontScale(scale: Float) {
        val s = scale.coerceIn(0.85f, 1.40f)
        _fontScale.value = s
        runCatching { prefs?.edit()?.putFloat(K_FONT, s)?.apply() }
    }

    // ── 瀏覽歷史 ──
    fun addRecent(kind: String, key: String, label: String) {
        if (key.isBlank()) return
        val clean = label.trim().ifBlank { key }
        val list = _data.value.recent
            .filterNot { it.kind == kind && it.key == key }
            .toMutableList()
        list.add(0, RecentVisit(kind, key, clean, System.currentTimeMillis()))
        _data.value = _data.value.copy(recent = list.take(MAX_RECENT))
        persistData()
    }

    fun clearRecent() {
        _data.value = _data.value.copy(recent = emptyList())
        persistData()
    }

    // ── 自訂收藏集 ──
    fun addGroup(name: String): Boolean {
        val n = name.trim()
        if (n.isBlank() || _data.value.groups.any { it.name == n }) return false
        _data.value = _data.value.copy(
            groups = _data.value.groups + CollectionGroup(n)
        )
        persistData()
        return true
    }

    fun removeGroup(name: String) {
        _data.value = _data.value.copy(
            groups = _data.value.groups.filterNot { it.name == name }
        )
        persistData()
    }

    fun toggleInGroup(name: String, favoriteKey: String) {
        val groups = _data.value.groups.toMutableList()
        val i = groups.indexOfFirst { it.name == name }
        if (i < 0) return
        val g = groups[i]
        val keys = if (favoriteKey in g.keys) g.keys - favoriteKey else g.keys + favoriteKey
        groups[i] = g.copy(keys = keys)
        _data.value = _data.value.copy(groups = groups)
        persistData()
    }

    /** 清 vet 咗嘅收藏 key（收藏頁刪咗站之後呼叫，避免殘留） */
    fun pruneGroups(validKeys: Set<String>) {
        val next = _data.value.groups.map { it.copy(keys = it.keys.filter { k -> k in validKeys }) }
        if (next == _data.value.groups) return
        _data.value = _data.value.copy(groups = next)
        persistData()
    }
}
