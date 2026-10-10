package com.hketa.app.data

/**
 * 語義化版本號，支援 pre-release 後綴（如 `1.0.0-beta1`）。
 *
 * 移植自 code-to-app 嘅 `UpdateChecker.Version`。
 * 舊嘅「只取每段開頭數字」做法會把 `-beta1` 之類嘅後綴直接丟掉，
 * 令 `v1.0.0-beta1` 被壓成 `1.0.0` —— 同正式版撞名、
 * 而且兩條都會被標成「目前版本」。
 *
 * 排序規則（semver）：
 *   - 正式版永遠排喺任何 pre-release 之上（1.0.0 > 1.0.0-beta1）
 *   - 帶數字嘅 pre-release 按數值排（beta1 < beta2 < beta10）
 */
data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** 第 4 段（可選）：畀 `1.1.3.1` 呢類版本用；缺省 = 0 */
    val build: Int = 0,
    val pre: String = ""
) : Comparable<Version> {

    override fun toString(): String =
        "$major.$minor.$patch" +
            (if (build > 0) ".$build" else "") +
            if (pre.isNotBlank()) "-$pre" else ""

    override fun compareTo(other: Version): Int {
        if (major != other.major) return major - other.major
        if (minor != other.minor) return minor - other.minor
        if (patch != other.patch) return patch - other.patch
        // ★ 第 4 段：冇呢個比較嘅話，`1.1.3.1` 會被當成 `1.1.3`，
        //   結果「有新版本」永遠唔會彈 —— 用戶攞唔到更新
        if (build != other.build) return build - other.build
        if (pre == other.pre) return 0
        // 同一核心版本下，正式版永遠排喺任何預發布版之上
        if (pre.isBlank()) return 1
        if (other.pre.isBlank()) return -1
        val (n1, x1) = splitPre(pre)
        val (n2, x2) = splitPre(other.pre)
        if (n1 != n2) return n1.compareTo(n2)
        return x1.compareTo(x2)
    }

    companion object {

        /** 把 `beta2` 拆成 ("beta", 2)，令帶數字嘅預發布版按數值排序 */
        private fun splitPre(pre: String): Pair<String, Int> {
            val m = Regex("^(.*?)(\\d+)$").find(pre)
            return if (m != null) {
                m.groupValues[1] to (m.groupValues[2].toIntOrNull() ?: 0)
            } else {
                pre to 0
            }
        }

        /** 解析 `v1.2.3` / `1.2.3` / `v1.0.0-beta1`；解析唔到返 null */
        fun parse(raw: String): Version? {
            val trimmed = raw.trim()
                .removePrefix("v").removePrefix("V")
                .substringBefore('+')
            val core = trimmed.substringBefore('-')
            val pre = trimmed.substringAfter('-', "")
            val parts = core.split('.')
            val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val build = parts.getOrNull(3)?.toIntOrNull() ?: 0
            return Version(major, minor, patch, build, pre)
        }

        /** latest 係咪新過 current */
        fun isNewer(latestRaw: String, currentRaw: String): Boolean {
            val a = parse(latestRaw) ?: return false
            val b = parse(currentRaw) ?: return false
            return a > b
        }

        /** 兩個版本係唔係同一個（忽略 v 前綴同長度差異：1.0 vs 1.0.0） */
        fun same(latestRaw: String, currentRaw: String): Boolean {
            val a = parse(latestRaw) ?: return false
            val b = parse(currentRaw) ?: return false
            return a.compareTo(b) == 0
        }
    }
}
