package hk.eta.app.api;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * LOCAL_SERVICE_HOURS — 「服務時間及班次由本地路線資料獲取」。
 *
 * 日曆圖標嘅 dialog 而家**只讀本機官方目錄快照**（RouteDataStore.readText /
 * readJson），**完全唔行網絡**：刻意唔用會喺快照缺失時 fallback 去即時
 * 官方 API 嘅合併讀取方法，咁先算真係「由本地路線資料獲取」。
 *
 * 對應官方目錄實際有嘅欄位（唔捏造、冇就唔顯示）：
 *   KMB / CTB : data[] { route, bound, service_type, orig_tc, dest_tc }
 *   NLB       : routes[] { routeNo, bound, specialType, journeyTime }
 *   GMB       : data.routes.<region>[] { route_code, description_tc, journey_time }
 *   MTR/LRT/MTRB : 得線路／車站 CSV，官方目錄**冇**服務時間同班次欄位
 *
 * 所以「班次」喺呢度係指**本地資料收錄嘅路線班次（方向 × 服務類型）**，
 * 而唔係具體開車時間。頭班／尾班／班次間隔官方目錄一律冇提供，
 * Info.noTimetable() 會如實講清楚，唔編時間表。
 *
 * 純 Java、零 Android import、零網絡、可離線測（配合 RouteDataStore.init()
 * 同 -Dhketa.files 指向嘅臨時 filesDir）。
 */
public final class LocalServiceHours {

    /** 一條本地官方目錄列 = 一個本地收錄嘅路線班次。 */
    public static final class Row {
        public final String route;
        public final String orig;
        public final String dest;
        public final String desc;
        public final String bound;
        public final String serviceType;
        public final String journeyTime;

        Row(String route, String orig, String dest, String desc,
            String bound, String serviceType, String journeyTime) {
            this.route = route == null ? "" : route;
            this.orig = orig == null ? "" : orig;
            this.dest = dest == null ? "" : dest;
            this.desc = desc == null ? "" : desc;
            this.bound = bound == null ? "" : bound;
            this.serviceType = serviceType == null ? "" : serviceType;
            this.journeyTime = journeyTime == null ? "" : journeyTime;
        }

        /** 只串接本地真實存在嘅欄位，冇嘅部分唔顯示、唔補假值。 */
        public String label() {
            StringBuilder b = new StringBuilder();
            if (!dest.isEmpty()) {
                b.append("往 ").append(dest);
            } else if (!desc.isEmpty()) {
                b.append(desc);
            } else if (!orig.isEmpty()) {
                b.append(orig);
            } else {
                b.append(route);
            }
            if (!bound.isEmpty()) {
                b.append(" · 方向 ").append(bound);
            }
            if (!serviceType.isEmpty()) {
                b.append(" · 服務類型 ").append(serviceType);
            }
            if (!journeyTime.isEmpty()) {
                b.append(" · 車程 ").append(journeyTime).append(" 分鐘");
            }
            return b.toString();
        }
    }

    public static final class Info {
        public final String company;
        public final String route;
        public final String sourceFile;
        public final String sourceLabel;
        public final boolean hasSnapshot;
        /** 本地資料得線路／車站 CSV，冇服務時間同班次欄位。 */
        public final boolean csvOnly;
        public final long updatedAt;
        public final int totalRecords;
        public final List<Row> rows;

        Info(String company, String route, String sourceFile, String sourceLabel,
             boolean hasSnapshot, boolean csvOnly, long updatedAt,
             int totalRecords, List<Row> rows) {
            this.company = company;
            this.route = route;
            this.sourceFile = sourceFile;
            this.sourceLabel = sourceLabel;
            this.hasSnapshot = hasSnapshot;
            this.csvOnly = csvOnly;
            this.updatedAt = updatedAt;
            this.totalRecords = totalRecords;
            this.rows = rows;
        }

        /** 如實聲明：本地官方目錄冇頭班／尾班／班次間隔。 */
        public boolean noTimetable() {
            return true;
        }
    }

    private LocalServiceHours() {
    }

    /** 公司 -> 本機目錄檔；null 代表呢個公司唔係 JSON 路線目錄。 */
    public static String fileFor(String company) {
        if (EtaRepository.KMB.equals(company)) {
            return RouteDataStore.KMB_ROUTES;
        }
        if (EtaRepository.CTB.equals(company)) {
            return RouteDataStore.CTB_ROUTES;
        }
        if (EtaRepository.NLB.equals(company)) {
            return RouteDataStore.NLB_ROUTES;
        }
        if (EtaRepository.GMB.equals(company)) {
            return RouteDataStore.GMB_ROUTES;
        }
        if (EtaRepository.MTR.equals(company)) {
            return RouteDataStore.MTR_LINES;
        }
        if (EtaRepository.LRT.equals(company)) {
            return RouteDataStore.LRT_STOPS;
        }
        if (EtaRepository.MTRB.equals(company)) {
            return RouteDataStore.MTR_BUS_STOPS;
        }
        return null;
    }

    public static String labelFor(String company) {
        if (EtaRepository.KMB.equals(company)) {
            return "九巴／龍運路線清單";
        }
        if (EtaRepository.CTB.equals(company)) {
            return "城巴路線清單";
        }
        if (EtaRepository.NLB.equals(company)) {
            return "嶼巴路線清單";
        }
        if (EtaRepository.GMB.equals(company)) {
            return "綠小巴路線清單";
        }
        if (EtaRepository.MTR.equals(company)) {
            return "港鐵重鐵線路及車站 CSV";
        }
        if (EtaRepository.LRT.equals(company)) {
            return "輕鐵路線及車站 CSV";
        }
        if (EtaRepository.MTRB.equals(company)) {
            return "港鐵接駁巴士車站 CSV";
        }
        return "本機路線資料";
    }

    /**
     * 只讀本機快照。冇快照 / 唔係 JSON 目錄 / 搵唔到路線，都如實返回，
     * 唔 fallback 去即時 API、唔捏造班次。
     */
    public static Info lookup(String company, String route) {
        String file = fileFor(company);
        String label = labelFor(company);
        long updated = RouteDataStore.meta().updatedAt;
        String wanted = route == null ? "" : route.trim();

        if (file == null) {
            return new Info(company, wanted, "", label, false, false,
                    updated, 0, new ArrayList<Row>());
        }
        String text = RouteDataStore.readText(file);
        boolean has = text != null && !text.trim().isEmpty();
        if (!has) {
            return new Info(company, wanted, file, label, false, false,
                    updated, 0, new ArrayList<Row>());
        }
        JSONObject root = RouteDataStore.readJson(file);
        if (root == null) {
            // MTR / LRT / MTRB 係 CSV，唔係 JSON 目錄。
            return new Info(company, wanted, file, label, true, true,
                    updated, 0, new ArrayList<Row>());
        }

        List<JSONObject> all = rowsOf(root);
        List<Row> matched = new ArrayList<>();
        for (JSONObject o : all) {
            String r = firstOf(o, "route", "routeNo", "route_code", "routeId");
            if (!sameRoute(r, wanted)) {
                continue;
            }
            matched.add(new Row(
                    r,
                    firstOf(o, "orig_tc", "orig_en", "orig_sc"),
                    firstOf(o, "dest_tc", "dest_en", "dest_sc"),
                    firstOf(o, "description_tc", "routeName_c", "routeName_e"),
                    firstOf(o, "bound", "dir", "direction"),
                    firstOf(o, "service_type", "specialType", "serviceType"),
                    firstOf(o, "journey_time", "journeyTime")));
        }
        return new Info(company, wanted, file, label, true, false,
                updated, all.size(), matched);
    }

    /** 兼容四家官方 JSON 嘅幾種裝載形狀，唔改寫資料。 */
    private static List<JSONObject> rowsOf(JSONObject root) {
        List<JSONObject> out = new ArrayList<>();
        JSONArray data = root.optJSONArray("data");
        if (data != null) {
            collect(data, out);
        }
        JSONObject dataObj = root.optJSONObject("data");
        if (dataObj != null) {
            JSONArray routesArr = dataObj.optJSONArray("routes");
            if (routesArr != null) {
                collect(routesArr, out);
            }
            JSONObject routesObj = dataObj.optJSONObject("routes");
            if (routesObj != null) {
                Iterator<String> it = routesObj.keys();
                while (it.hasNext()) {
                    JSONArray a = routesObj.optJSONArray(it.next());
                    if (a != null) {
                        collect(a, out);
                    }
                }
            }
        }
        JSONArray routes = root.optJSONArray("routes");
        if (routes != null) {
            collect(routes, out);
        }
        return out;
    }

    private static void collect(JSONArray a, List<JSONObject> out) {
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) {
                out.add(o);
            }
        }
    }

    private static String firstOf(JSONObject o, String... keys) {
        for (String k : keys) {
            String v = o.optString(k, "");
            if (v != null) {
                v = v.trim();
                if (!v.isEmpty()) {
                    return v;
                }
            }
        }
        return "";
    }

    /** 路線號大小寫／前後空格兼容，唔做模糊配對以免撈錯線。 */
    private static boolean sameRoute(String a, String b) {
        return a != null && b != null && !b.isEmpty()
                && a.trim().equalsIgnoreCase(b.trim());
    }
}
