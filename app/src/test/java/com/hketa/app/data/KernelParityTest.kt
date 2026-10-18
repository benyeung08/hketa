package com.hketa.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **Rust ⇄ Kotlin 一致性嘅「Kotlin 側」鎖。**
 *
 * ## 呢個測試做乜
 *
 * `RustKernel` 每一個函數都有**逐行對應嘅純 Kotlin 回退**
 * （見 `RustKernel` 嘅文檔：「兩邊結果必定相同」）。
 * 呢個測試就係鎖住 Kotlin 側嗰份嘅行為。
 *
 * JVM 單測入面**冇 `.so`**，所以 `available == false`，
 * 所有呼叫都會行 Kotlin 回退版 —— 即係呢個測試測嘅正係
 * 「Rust 唔喺度嗰陣用戶會見到嘅結果」。
 *
 * ## ⚠️ 淨係鎖咗一半
 *
 * 呢個測試**證實唔到** Rust 側一樣 —— 要證實就要喺
 * instrumented test（有 `.so`）入面再跑同一組 golden values。
 *
 * 所以：**改 Rust 嗰邊之後，要手動對返呢度每一個數字**。
 * 數字唔夾即係兩邊唔一致，會出現「有冇 Rust 行為唔同」
 * 呢類最難 debug 嘅 bug。
 *
 * 呢度每一個 golden value 都係照住 `rust/src/kernel.rs` 嘅算法
 * 逐位推出嚟（尤其 `(i * step).toInt()` 係**截斷**唔係四捨五入）。
 */
class KernelParityTest {

    // ─────────────────────────── 取樣 sampleIndices ───────────────────────────

    /**
     * 主例：40 個站取 12 個。
     *
     * `step = 39 / 11 = 3.5454...`，逐個 `i * step` 截斷：
     * 0, 3, 7, 10, 14, 17, 21, 24, 28, 31, 35, 39
     */
    @Test
    fun `取樣 40 個站取 12 個`() {
        assertEquals(
            listOf(0, 3, 7, 10, 14, 17, 21, 24, 28, 31, 35, 39),
            RustKernel.sampleIndices(40, 12)
        )
    }

    /** 站數啱啱好等於上限 —— 全部都要出，唔可以做取樣 */
    @Test
    fun `站數等於上限就全出`() {
        assertEquals((0 until 12).toList(), RustKernel.sampleIndices(12, 12))
        assertEquals((0 until 5).toList(), RustKernel.sampleIndices(5, 12))
        assertEquals(listOf(0), RustKernel.sampleIndices(1, 12))
    }

    /** 邊界：n 係 0 或負數 → 空；max 係 0 或負數 → 空 */
    @Test
    fun `取樣嘅空輸入`() {
        assertEquals(emptyList<Int>(), RustKernel.sampleIndices(0, 12))
        assertEquals(emptyList<Int>(), RustKernel.sampleIndices(-5, 12))
        assertEquals(emptyList<Int>(), RustKernel.sampleIndices(10, 0))
        assertEquals(emptyList<Int>(), RustKernel.sampleIndices(10, -1))
    }

    /**
     * ⚠️ 邊界：`max == 1` 嗰陣 `step = (n-1) / 0 = +∞`，
     * 而 `0 * ∞ = NaN`。Rust 嘅 float→int 係 saturating cast（NaN → 0），
     * Kotlin 嘅 `NaN.toInt()` 亦係 0 —— **兩邊一樣**，所以結果係 `[0]`。
     * 呢個 case 係刻意留喺度：唔好俾人「順手」改咗做除零保護而令兩邊分叉。
     */
    @Test
    fun `max 係 1 時唔會 NaN 分叉`() {
        assertEquals(listOf(0), RustKernel.sampleIndices(3, 1))
        assertEquals(listOf(0), RustKernel.sampleIndices(10, 1))
        assertEquals(listOf(0), RustKernel.sampleIndices(2, 1))
    }

    /** 其餘取樣組合，用嚟捉「改咗截斷做四捨五入」呢類手痕 */
    @Test
    fun `取樣其他組合`() {
        assertEquals(listOf(0, 49, 99), RustKernel.sampleIndices(100, 3))
        assertEquals(listOf(0, 1, 3, 4, 6), RustKernel.sampleIndices(7, 5))
        assertEquals(listOf(0, 1), RustKernel.sampleIndices(2, 5))
        assertEquals(listOf(0, 2, 5), RustKernel.sampleIndices(6, 3))
    }

    /** 取樣結果一定要去重、而且是升冪、而且唔會越界 */
    @Test
    fun `取樣必定去重升冪唔越界`() {
        for (n in 1..60) {
            for (max in 1..12) {
                val got = RustKernel.sampleIndices(n, max)
                assert(got.size <= max) { "n=$n max=$max 出咗 ${got.size} 個" }
                assert(got == got.distinct()) { "n=$n max=$max 有重複" }
                assert(got == got.sorted()) { "n=$n max=$max 唔係升冪" }
                assert(got.all { it in 0 until n }) { "n=$n max=$max 越界" }
            }
        }
    }

    // ─────────────────────────── 診斷 diagnose ───────────────────────────

    /**
     * 五種狀態，順序係刻意嘅：
     * 全線冇嘢最嚴重 → 其次「有站冇資料」 → 最後先係間隔偏大。
     */
    @Test
    fun `診斷五種狀態`() {
        assertEquals(1, RustKernel.diagnose(10, 10, 0))   // 全線冇班次
        assertEquals(2, RustKernel.diagnose(10, 3, 0))    // 部分站冇資料
        assertEquals(4, RustKernel.diagnose(10, 0, 70))   // 極疏 >= 60
        assertEquals(3, RustKernel.diagnose(10, 0, 40))   // 偏大 >= 30
        assertEquals(0, RustKernel.diagnose(10, 0, 15))   // 正常
    }

    /** 冇站嗰陣要當正常（0），唔可以報「全線冇班次」嚇親人 */
    @Test
    fun `診斷冇站當正常`() {
        assertEquals(0, RustKernel.diagnose(0, 0, 0))
        assertEquals(0, RustKernel.diagnose(0, 5, 99))
    }

    /** 門檻係 >= 而唔係 > ：30 同 60 都算入「偏大／極疏」 */
    @Test
    fun `診斷門檻係大過等於`() {
        assertEquals(3, RustKernel.diagnose(10, 0, 30))
        assertEquals(4, RustKernel.diagnose(10, 0, 60))
        assertEquals(0, RustKernel.diagnose(10, 0, 29))
        assertEquals(3, RustKernel.diagnose(10, 0, 59))
    }

    /** 「有站冇資料」優先過「間隔偏大」—— 對用戶最直接嗰個行先 */
    @Test
    fun `診斷有站冇資料行先`() {
        assertEquals(2, RustKernel.diagnose(10, 1, 90))
    }

    // ─────────────────────────── 路線號正規化 ───────────────────────────

    @Test
    fun `路線號去空格轉大寫`() {
        assertEquals("1A", RustKernel.normalizeRoute(" 1a "))
        assertEquals("1A", RustKernel.normalizeRoute("1a"))
        assertEquals("K51", RustKernel.normalizeRoute("k51"))
        assertEquals("", RustKernel.normalizeRoute("   "))
        assertEquals("N691", RustKernel.normalizeRoute("\tn691\n"))
    }
}
