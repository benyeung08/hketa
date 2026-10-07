package com.hketa.app.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class IndexStore(context: Context) {

    private val file = File(context.filesDir, "hketa_index.json")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    val exists: Boolean get() = file.exists() && file.length() > 0

    val lastModified: Long get() = if (file.exists()) file.lastModified() else 0L

    fun load(): IndexData? = runCatching {
        if (!exists) return null
        json.decodeFromString<IndexData>(file.readText())
    }.getOrNull()

    fun save(data: IndexData) {
        runCatching { file.writeText(json.encodeToString(data)) }
    }

    fun clear() {
        runCatching { if (file.exists()) file.delete() }
    }

    fun sizeKb(): Long = if (file.exists()) file.length() / 1024 else 0L
}
