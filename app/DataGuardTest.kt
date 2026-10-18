package com.hketa.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [DataGuard] 嘅單測 —— 呢啲**唔係**為咗應付覆蓋率，而係鎖死
 * 「壞資料唔可以覆蓋好資料」呢條底線。
 *
 * 每一個 [Test] 都對應一個真實會發生嘅事故場景，註解有寫邊個。
 */
class DataGuardTest {

    // ─────────────────────────── shouldReject ───────────────────────────

    /** 連一個站都冇 —— 連 `stops.isEmpty()` 嗰關都未過 */
    @Test
    fun `next 係零要拒絕`() {
        assertNotNull(DataGuard.shouldReject(1000, 500, 0, 0))
        assertNotNull(DataGuard.shouldReject(1000, 500, -1, 0))
    }

    /**
     * 第一次落：機上冇舊資料可以比，改用絕對下限。
     * 全港三個營辦商加埋係幾千個站，200 係非常保守嘅線。
     */
    @Test
    fun `第一次落要夠多先寫`() {
        assertNull("200 個站應該要接受", DataGuard.shouldReject(0, 0, 200, 0))
        assertNull("5000 個站當然接受", DataGuard.shouldReject(0, 0, 5000, 0))
        assertNotNull("得 50 個站似係聚合壞咗", DataGuard.shouldReject(0, 0, 50, 0))
    }

    /**
     * 日常跌幅：官方會刪線合併路線，通常幾個 %，
     * 呢啲**一定要照寫**，否則會永久卡住喺一份舊資料。
     */
    @Test
    fun `小幅下跌要照寫`() {
        assertNull(DataGuard.shouldReject(1000, 0, 1000, 0))   // 冇變
        assertNull(DataGuard.shouldReject(1000, 0, 950, 0))     // 跌 5%
        assertNull(DataGuard.shouldReject(1000, 0, 900, 0))     // 跌 10%
        assertNotNull(DataGuard.shouldReject(1000, 0, 500, 0))  // 跌 50% —— 腰斬
    }

    /** 30% 就係條線：floor = prev * 0.7，所以 700 接受、699 拒絕 */
    @Test
    fun `跌幅門檻係 30 個百分比`() {
        assertNull("跌到啱啱好 30% 應該仲接受", DataGuard.shouldReject(1000, 0, 700, 0))
        assertNotNull("過咗 30% 就要拒絕", DataGuard.shouldReject(1000, 0, 699, 0))
    }

    /** 資料變多係好事，絕對唔可以擋 */
    @Test
    fun `變多一定要寫`() {
        assertNull(DataGuard.shouldReject(1000, 0, 1500, 0))
        assertNull(DataGuard.shouldReject(1000, 0, 9999, 0))
    }

    /**
     * 英文名對照係後加嘅（解決「嶼巴英文名」限制），
     * 所以**舊快取本來就係 0** —— 嗰陣唔可以因為「今次都係 0」就拒絕，
     * 否則升級咗嘅用戶永遠補唔到英文名。
     */
    @Test
    fun `舊快取冇英文名時唔會誤判`() {
        assertNull("上次 0、今次 0 —— 係正常（舊格式）", DataGuard.shouldReject(1000, 0, 1000, 0))
        assertNull("上次 0、今次有 —— 係升級，照寫", DataGuard.shouldReject(1000, 0, 1000, 800))
        assertNull("上次有、今次有 —— 正常", DataGuard.shouldReject(1000, 800, 1000, 790))
        assertNotNull("上次有、今次無 —— 係倒退", DataGuard.shouldReject(1000, 800, 1000, 0))
    }

    // ─────────────────────────── fieldSanity ───────────────────────────

    /** 樣本太少唔可以亂講 —— 否則小巴單一條線會誤報 */
    @Test
    fun `樣本唔夠就放行`() {
        assertNull(DataGuard.fieldSanity(0, 0, 0))
        assertNull(DataGuard.fieldSanity(10, 0, 0))
        assertNull(DataGuard.fieldSanity(49, 0, 0))
    }

    /** 正常情況：兩個欄位都有命中 */
    @Test
    fun `欄位正常就放行`() {
        assertNull(DataGuard.fieldSanity(5000, 4900, 4800))
        assertNull(DataGuard.fieldSanity(5000, 1, 1))  // 命中率低但唔係零
    }

    /**
     * 呢個就係要捉嘅嘢：`name.en` 改咗名做 `nameEn`，
     * 解析器唔會報錯，只係每條拎到空字串 —— 英文名會全部消失，
     * 靜靜雞降級去內置嗰 154 個，用戶永遠唔會發現。
     */
    @Test
    fun `英文名欄位消失要出聲`() {
        assertNotNull(DataGuard.fieldSanity(5000, 0, 4800))
    }

    /** 同理：`location.lat` 改名 → 座標全零 → 「附近」會全部廢咗 */
    @Test
    fun `座標欄位消失要出聲`() {
        assertNotNull(DataGuard.fieldSanity(5000, 4900, 0))
    }

    // ─────────────────────────── verdict ───────────────────────────

    @Test
    fun `verdict 正常時係 OK`() {
        assertEquals("OK", DataGuard.verdict(1000, 500, 1000, 500))
    }
}
