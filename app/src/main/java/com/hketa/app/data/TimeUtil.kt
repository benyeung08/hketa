package com.hketa.app.data

import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeUtil {

    private val HK: ZoneId = ZoneId.of("Asia/Hong_Kong")
    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** 官方 ETA 時間 → 尚餘分鐘（失敗回傳 null） */
    fun etaMinutes(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val millis = parseMillis(raw) ?: return null
        return ((millis - System.currentTimeMillis()) / 60_000L).toInt()
    }

    /** 官方 ETA 時間 → HH:mm 字串（失敗回傳空字串） */
    fun etaClock(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val millis = parseMillis(raw) ?: return ""
        return runCatching {
            OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), HK)
                .toLocalTime()
                .format(CLOCK)
        }.getOrDefault("")
    }

    private fun parseMillis(raw: String): Long? {
        // 1) 完整 ISO（含時區，如 2024-05-01T12:34:56+08:00）
        runCatching { OffsetDateTime.parse(raw) }.getOrNull()?.let {
            return it.toInstant().toEpochMilli()
        }
        // 2) 港鐵巴士等純時間（HH:mm）
        runCatching { LocalTime.parse(raw) }.getOrNull()?.let { t ->
            val today = java.time.LocalDate.now(HK)
            var dt = java.time.LocalDateTime.of(today, t).atZone(HK).toInstant().toEpochMilli()
            if (dt < System.currentTimeMillis() - 60_000L) dt += 24 * 60 * 60 * 1000L
            return dt
        }
        // 3) 無時區的 ISO（視作香港時間）
        runCatching { java.time.LocalDateTime.parse(raw) }.getOrNull()?.let {
            return it.atZone(HK).toInstant().toEpochMilli()
        }
        return null
    }
}
