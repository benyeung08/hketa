package hk.eta.app.api;

import java.util.ArrayList;
import java.util.List;

/**
 * SECOND_STOP_PREDICTIONS — 「第二站起預計到站時間」嘅單一決策層。
 *
 * 圖一：上車站（第 1 站）顯示官方即時 ETA 嘅三班次；
 * 圖二：由第 2 站起原本得返時間軸紅線、右邊全空白。
 * 呢度負責揀「要查邊啲後續站」同「點樣由官方 ETA 拎第一班」，
 * 再由 UI 用兩層顯示：第一層「×× 分鐘」、第二層「××:××」。
 *
 * 重要：呢啲數字係**官方即時 ETA**（同圖一同一個 API），唔係推算
 * 時間表、唔係按站距估出嚟。官方冇回報就交返 null，UI 如實顯示「—」。
 *
 * 純 Java、零 Android import、零網絡、可離線測。
 */
public final class PredictedArrivals {

    /** 一次最多查幾多個後續站，避免一條長路線打爆官方 429。 */
    public static final int MAX_STOPS = 18;

    private PredictedArrivals() {}

    /**
     * 由「上車站之後一站」開始，最多 max 個站嘅 index（0-based）。
     * selectedIndex < 0（未揀站）時由 0 開始，一個都唔會跳過。
     */
    public static int[] targets(int stopCount, int selectedIndex, int max) {
        if (stopCount <= 0) return new int[0];
        int start = Math.max(0, selectedIndex + 1);
        if (start >= stopCount) return new int[0];
        int end = Math.min(stopCount, start + Math.max(0, max));
        int[] out = new int[end - start];
        for (int i = start; i < end; i++) out[i - start] = i;
        return out;
    }

    /** 只認同路線嘅班次；入面揀最早嗰班。冇就 null，唔用第間路線頂上。 */
    public static EtaModels.EtaItem firstForRoute(List<EtaModels.EtaItem> etas,
                                                   String route) {
        if (etas == null || route == null) return null;
        EtaModels.EtaItem best = null;
        for (EtaModels.EtaItem e : etas) {
            if (e == null || !route.equals(e.getRoute())) continue;
            if (best == null || e.getEtaMillis() < best.getEtaMillis()) best = e;
        }
        return best;
    }

    /** 第一層：倒數分鐘。「即將到站」／「-」都係如實降級，唔捏造。 */
    public static String minutesLabel(long etaMillis, long nowMillis) {
        if (etaMillis <= 0L) return "-";
        long diff = (etaMillis - nowMillis) / 60_000L;
        if (diff <= 0L) return "即將到站";
        return Long.toString(diff);
    }

    /** 第二層：HH:mm（HKT），沿用 TimeUtils，唔另寫格式。 */
    public static String clockLabel(long etaMillis) {
        return etaMillis > 0L ? TimeUtils.hhmm(etaMillis) : "";
    }

    /** 畀 harness / log 用：由 index 列表整返一個可讀摘要。 */
    public static List<Integer> targetList(int stopCount, int selectedIndex, int max) {
        int[] a = targets(stopCount, selectedIndex, max);
        List<Integer> out = new ArrayList<Integer>(a.length);
        for (int i : a) out.add(Integer.valueOf(i));
        return out;
    }
}
