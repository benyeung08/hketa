package hk.eta.app.api;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import hk.eta.app.api.EtaModels.EtaItem;
import hk.eta.app.api.EtaModels.StopOption;
import hk.eta.app.api.MtrCatalog.Station;

/**
 * Java layer that talks to the Hong Kong open-data APIs.
 *
 * KMB / LWB : https://data.etabus.gov.hk/v1/transport/kmb
 * Citybus   : https://rt.data.gov.hk/v2/transport/citybus
 * NLB       : https://rt.data.gov.hk/v2/transport/nlb
 * GMB       : https://data.etagmb.gov.hk
 * MTR       : https://rt.data.gov.hk/v1/transport/mtr
 * MTR CSV   : https://opendata.mtr.com.hk/data
 *
 * All APIs are public, key-free HTTPS + JSON (MTR Bus is POST + JSON).
 * Every method here is synchronous; the Kotlin ViewModel runs them on Dispatchers.IO.
 */
public final class EtaRepository {

    public static final String AUTO = "AUTO";
    public static final String KMB = "KMB";
    public static final String CTB = "CTB";
    public static final String NLB = "NLB";
    public static final String GMB = "GMB";
    public static final String MTR = "MTR";
    public static final String LRT = "LRT";
    public static final String MTRB = "MTRB";
    public static final String NEARBY = "NEARBY";

    private static final String BATCH_BASE = "https://rt.data.gov.hk";

    public static final String[] OPERATORS = {
            KMB, CTB, NLB, GMB, MTR, LRT, MTRB
    };

    private static final String KMB_BASE = "https://data.etabus.gov.hk/v1/transport/kmb";
    private static final String CTB_BASE = "https://rt.data.gov.hk/v2/transport/citybus";
    private static final String NLB_BASE = "https://rt.data.gov.hk/v2/transport/nlb";
    private static final String GMB_BASE = "https://data.etagmb.gov.hk";
    private static final String MTR_BASE = "https://rt.data.gov.hk/v1/transport/mtr";
    private static final String GMB_REGIONS = "HKI,KLN,NT";

    private static final Map<String, Map<String, String>> NAME_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> NLB_ROUTE_IDS = new ConcurrentHashMap<>();
    private static final Map<String, String> GMB_ROUTE_CODES = new ConcurrentHashMap<>();

    /** Called by RouteDataStore after a catalogue update / clear. */
    public static void clearCaches() {
        NAME_CACHE.clear();
        NLB_ROUTE_IDS.clear();
        GMB_ROUTE_CODES.clear();
    }

    /** Local snapshot first, live official API as fallback (with 403 retry). */
    private static JSONObject catalogueJson(String file, String url) throws Exception {
        String[] urls = (url.contains("?") || url.endsWith("/"))
                ? new String[]{url}
                : new String[]{url, url + "/"};
        return RouteDataStore.catalogueJson(file, urls);
    }

    // ============================================================ public API

    /** All stops for one operator. Used by the Kotlin ViewModel (possibly in parallel). */
    public List<StopOption> stops(String route, String company) throws Exception {
        String r = route == null ? "" : route.trim().toUpperCase(Locale.US);
        switch (company) {
            case KMB: {
                List<StopOption> out = new ArrayList<>();
                out.addAll(kmbStops(r, "outbound"));
                out.addAll(kmbStops(r, "inbound"));
                return out;
            }
            case CTB: {
                List<StopOption> out = new ArrayList<>();
                out.addAll(ctbStops(r, "outbound"));
                out.addAll(ctbStops(r, "inbound"));
                return out;
            }
            case NLB:
                return nlbStops(r);
            case GMB:
                return gmbStops(r);
            case MTR:
                return mtrStops(r);
            case LRT:
                return lrtStops(r);
            case MTRB:
                return mtrBusStops(r);
            default:
                return Collections.emptyList();
        }
    }

    /** Nearby stops sorted by distance from the device location. */
    public List<StopOption> nearbyStops(double lat, double lng, int radiusMeters,
                                        int limit) {
        return GeoStops.nearby(lat, lng, radiusMeters, limit);
    }

    public List<EtaItem> etaFor(StopOption stop) throws Exception {
        // JOINT_KMB_CTB: primary operator first, then a best-effort partner
        // lookup for jointly-operated routes so each arrival can be labelled
        // 城巴 / 九巴. A failed partner lookup never hides the primary ETA.
        List<EtaItem> primary;
        if (NEARBY.equals(stop.getBound())) {
            primary = stopEta(stop);
        } else {
            switch (stop.getCompany()) {
                case KMB:
                    primary = kmbEta(stop.getRoute(), stop.getStopId(), stop.getServiceType());
                    break;
                case CTB:
                    primary = ctbEta(stop.getRoute(), stop.getStopId());
                    break;
                case NLB:
                    primary = nlbEta(stop);
                    break;
                case GMB:
                    primary = gmbEta(stop);
                    break;
                case MTR:
                    primary = mtrEta(stop);
                    break;
                case LRT:
                    primary = lrtEta(stop);
                    break;
                case MTRB:
                    primary = mtrBusEta(stop);
                    break;
                default:
                    primary = Collections.emptyList();
            }
        }
        return JointRoutes.augment(primary, stop, this);
    }

    /** All-route ETA for one nearby stop (no route number required). */
    public List<EtaItem> stopEta(StopOption stop) throws Exception {
        switch (stop.getCompany()) {
            case KMB: {
                String url = KMB_BASE + "/stop-eta/" + HttpJson.seg(stop.getStopId());
                return parseBusEta(HttpJson.getObject(url), KMB, "附近",
                        stop.getStopId(), true);
            }
            case CTB:
                return batchStopEta(CTB, stop.getStopId());
            case NLB:
                return batchStopEta(NLB, stop.getStopId());
            case GMB:
                return gmbStopEta(stop);
            case MTR:
                return mtrEta(stop);
            case LRT:
                return lrtEta(stop);
            case MTRB:
                return mtrBusEta(stop);
            default:
                return Collections.emptyList();
        }
    }

    /**
     * DATA.GOV.HK batch Stop-ETA (CTB + NLB). The published specification and its
     * sample request disagree on "stopeta" vs "stop-eta", so both are attempted.
     */
    private List<EtaItem> batchStopEta(String company, String stopId) throws Exception {
        String[] paths = {
                "/v1/transport/batch/stopeta/",
                "/v1/transport/batch/stop-eta/"
        };
        IOException last = null;
        for (String path : paths) {
            String url = BATCH_BASE + path + HttpJson.seg(company) + "/"
                    + HttpJson.seg(stopId) + "?lang=zh-hant";
            try {
                return parseBusEta(HttpJson.getObject(url), company, "附近",
                        stopId, true);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last == null ? new IOException("整站 ETA 暫不可用") : last;
    }

    /** GET /eta/stop/{stop_id} returns every GMB route serving the stop. */
    private List<EtaItem> gmbStopEta(StopOption stop) throws Exception {
        String url = GMB_BASE + "/eta/stop/" + HttpJson.seg(stop.getStopId());
        JSONObject root = HttpJson.getObject(url);
        JSONArray routes = root.optJSONArray("data");
        List<EtaItem> out = new ArrayList<>();
        if (routes == null) {
            return out;
        }
        String generated = HttpJson.str(root, "generated_timestamp");
        for (int i = 0; i < routes.length(); i++) {
            JSONObject route = routes.optJSONObject(i);
            if (route == null || !route.optBoolean("enabled", false)) {
                continue;
            }
            String routeId = HttpJson.str(route, "route_id");
            String label = gmbRouteCode(routeId);
            int routeSeq = route.optInt("route_seq", 1);
            JSONArray etas = route.optJSONArray("eta");
            if (etas == null) {
                continue;
            }
            for (int j = 0; j < etas.length(); j++) {
                JSONObject e = etas.optJSONObject(j);
                if (e == null) {
                    continue;
                }
                String iso = HttpJson.str(e, "timestamp");
                long millis = TimeUtils.parseHkIso(iso);
                if (millis <= 0L) {
                    millis = TimeUtils.nowPlusMinutes(e.optInt("diff", 0));
                }
                if (millis <= 0L) {
                    continue;
                }
                String remark = HttpJson.firstNonEmpty(
                        HttpJson.str(e, "remarks_tc"),
                        HttpJson.str(e, "remarks_en"),
                        "路線 ID " + routeId);
                out.add(new EtaItem(GMB, label, stop.getStopId(),
                        "綠小巴 · 第 " + routeSeq + " 方向", iso, millis,
                        e.optInt("eta_seq", j + 1), remark, "ALL", generated));
            }
        }
        return out;
    }

    private String gmbRouteCode(String routeId) {
        if (routeId.isEmpty()) {
            return "小巴";
        }
        String cached = GMB_ROUTE_CODES.get(routeId);
        if (cached != null) {
            return cached;
        }
        String code = routeId;
        try {
            JSONObject root = HttpJson.getObject(GMB_BASE + "/route/" + HttpJson.seg(routeId));
            JSONArray data = root.optJSONArray("data");
            if (data != null && data.length() > 0) {
                JSONObject first = data.optJSONObject(0);
                String found = HttpJson.str(first, "route_code");
                if (!found.isEmpty()) {
                    code = found;
                }
            }
        } catch (Exception ignored) {
            // route_id remains the label
        }
        GMB_ROUTE_CODES.put(routeId, code);
        return code;
    }

    /** Builds a StopOption for the "known stop ID" shortcut. */
    public StopOption directSelection(String route, String stopId, String company) throws Exception {
        String r = route == null ? "" : route.trim().toUpperCase(Locale.US);
        String s = stopId == null ? "" : stopId.trim();
        String c = company;
        if (AUTO.equals(c)) {
            c = guessCompany(r, s);
        }
        switch (c) {
            case KMB:
                return new StopOption(KMB, r, "", 0, s, "", 1, "", "");
            case CTB:
                return new StopOption(CTB, r, "", 0, s, "", 1, "", "");
            case NLB: {
                String id = nlbRouteId(r);
                return new StopOption(NLB, r, "ALL", 0, s, "", 1, id, "嶼巴");
            }
            case GMB: {
                List<GmbVariant> variants = gmbVariants(r);
                if (variants.isEmpty()) {
                    throw new IOException("找不到綠小巴路線 " + r);
                }
                GmbVariant v = variants.get(0);
                return new StopOption(GMB, r, String.valueOf(v.routeSeq), 0,
                        s, "", 1, v.routeId, v.note);
            }
            case MTR:
                if (!MtrCatalog.isLineCode(r)) {
                    throw new IOException("港鐵重鐵請輸入線路代碼（如 TML、ISL、EAL）");
                }
                return new StopOption(MTR, r, "ALL", 0, s, "", 1, r,
                        MtrCatalog.lineName(r));
            case LRT:
                return new StopOption(LRT, r.isEmpty() ? "ALL" : r, "ALL", 0,
                        s, "", 1, r.isEmpty() ? "ALL" : r, "輕鐵");
            case MTRB:
                return new StopOption(MTRB, r, "ALL", 0, s, "", 1, r, "港鐵接駁巴士");
            default:
                throw new IOException("不支援的營運商：" + c);
        }
    }

    public Map<String, String> stopNames(String company) throws Exception {
        Map<String, String> cached = NAME_CACHE.get(company);
        if (cached != null) {
            return cached;
        }
        String file = KMB.equals(company)
                ? RouteDataStore.KMB_STOPS : RouteDataStore.CTB_STOPS;
        JSONObject root;
        if (KMB.equals(company)) {
            // KMB does publish a full stop list, but edge nodes sometimes 403
            // it. RouteDataStore falls back to the partial single-stop cache.
            root = RouteDataStore.catalogueJson(file,
                    KMB_BASE + "/stop", KMB_BASE + "/stop/");
        } else {
            // Citybus v2 has NO full stop-list endpoint. Only the incremental
            // cache we build from /stop/{stop_id} is available.
            root = RouteDataStore.readJson(file);
            if (root == null) {
                root = new JSONObject();
            }
        }
        Map<String, String> map = new ConcurrentHashMap<>();
        JSONArray data = root.optJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject o = data.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String id = HttpJson.str(o, "stop");
                if (id.isEmpty()) {
                    continue;
                }
                String name = HttpJson.firstNonEmpty(
                        HttpJson.str(o, "name_tc"),
                        HttpJson.str(o, "name_en"),
                        HttpJson.str(o, "name_sc"));
                if (!name.isEmpty()) {
                    map.put(id, name);
                }
            }
        }
        NAME_CACHE.put(company, map);
        return map;
    }

    /**
     * Resolves ONE Chinese stop name through the official single-stop API.
     * This is the fix for Citybus showing bare ids such as "002891": when the
     * bulk stop list is unavailable (or does not exist), every stop in the
     * route sequence is looked up individually, in parallel, and cached both
     * in memory and in the local snapshot for offline reuse.
     */
    public static String fetchStopName(String company, String stopId) {
        if (stopId == null || stopId.isEmpty()) {
            return "";
        }
        String url;
        String file;
        if (KMB.equals(company)) {
            url = KMB_BASE + "/stop/" + HttpJson.seg(stopId);
            file = RouteDataStore.KMB_STOPS;
        } else if (CTB.equals(company)) {
            url = CTB_BASE + "/stop/" + HttpJson.seg(stopId);
            file = RouteDataStore.CTB_STOPS;
        } else if (GMB.equals(company)) {
            url = GMB_BASE + "/stop/" + HttpJson.seg(stopId);
            file = "";
        } else {
            return "";
        }
        return fetchStopNameAt(company, stopId, url, file);
    }

    /** Package-visible so the resolution logic can be tested without a live API. */
    static String fetchStopNameAt(String company, String stopId, String url, String file) {
        if (stopId == null || stopId.isEmpty() || url == null || url.isEmpty()) {
            return "";
        }
        try {
            JSONObject root = HttpJson.singleStop(url);
            JSONObject d = root.optJSONObject("data");
            if (d == null) {
                JSONArray arr = root.optJSONArray("data");
                if (arr != null && arr.length() > 0) {
                    d = arr.optJSONObject(0);
                }
            }
            if (d == null) {
                return "";
            }
            String name = HttpJson.firstNonEmpty(
                    HttpJson.str(d, "name_tc"),
                    HttpJson.str(d, "name_en"),
                    HttpJson.str(d, "name_sc"));
            if (name.isEmpty()) {
                return "";
            }
            Map<String, String> map = NAME_CACHE.get(company);
            if (map != null) {
                map.put(stopId, name);
            }
            if (!file.isEmpty()) {
                double lat = parseCoord(HttpJson.str(d, "lat"));
                double lng = parseCoord(HttpJson.str(d, "long"));
                RouteDataStore.cacheStop(file, stopId,
                        HttpJson.str(d, "name_tc"), HttpJson.str(d, "name_en"),
                        HttpJson.str(d, "name_sc"), lat, lng);
            }
            return name;
        } catch (Exception e) {
            return "";
        }
    }

    private static double parseCoord(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public String stopName(String company, String stopId) {
        Map<String, String> map = NAME_CACHE.get(company);
        if (map == null) {
            return "";
        }
        String name = map.get(stopId);
        return name == null ? "" : name;
    }

    // ============================================================ KMB / CTB

    public List<StopOption> kmbStops(String route, String direction) throws Exception {
        String url = KMB_BASE + "/route-stop/"
                + HttpJson.seg(route) + "/" + HttpJson.seg(direction) + "/1";
        return parseRouteStops(HttpJson.getObject(url), KMB, route, direction,
                safeNames(KMB), true);
    }

    public List<StopOption> ctbStops(String route, String direction) throws Exception {
        String url = CTB_BASE + "/route-stop/CTB/"
                + HttpJson.seg(route) + "/" + HttpJson.seg(direction);
        return parseRouteStops(HttpJson.getObject(url), CTB, route, direction,
                safeNames(CTB), false);
    }

    public List<EtaItem> kmbEta(String route, String stopId, int serviceType) throws Exception {
        String url = KMB_BASE + "/eta/" + HttpJson.seg(stopId) + "/"
                + HttpJson.seg(route) + "/" + (serviceType < 1 ? 1 : serviceType);
        return parseBusEta(HttpJson.getObject(url), KMB, route, stopId, false);
    }

    public List<EtaItem> ctbEta(String route, String stopId) throws Exception {
        String url = CTB_BASE + "/eta/CTB/"
                + HttpJson.seg(stopId) + "/" + HttpJson.seg(route);
        return parseBusEta(HttpJson.getObject(url), CTB, route, stopId, false);
    }

    // ============================================================ NLB

    private String nlbRouteId(String route) throws Exception {
        String cached = NLB_ROUTE_IDS.get(route);
        if (cached != null) {
            return cached;
        }
        JSONObject root = catalogueJson(RouteDataStore.NLB_ROUTES,
                NLB_BASE + "/route.php?action=list");
        JSONArray routes = root.optJSONArray("routes");
        if (routes == null) {
            throw new IOException("嶼巴路線清單暫不可用");
        }
        for (int i = 0; i < routes.length(); i++) {
            JSONObject o = routes.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String no = HttpJson.str(o, "routeNo");
            String id = HttpJson.str(o, "routeId");
            if (!no.isEmpty() && !id.isEmpty()) {
                NLB_ROUTE_IDS.put(no.toUpperCase(Locale.US), id);
            }
        }
        String id = NLB_ROUTE_IDS.get(route);
        if (id == null) {
            throw new IOException("找不到嶼巴路線 " + route);
        }
        return id;
    }

    private List<StopOption> nlbStops(String route) throws Exception {
        String routeId = nlbRouteId(route);
        String url = NLB_BASE + "/stop.php?action=list&routeId=" + HttpJson.seg(routeId);
        JSONObject root = HttpJson.getObject(url);
        JSONArray stops = root.optJSONArray("stops");
        List<StopOption> out = new ArrayList<>();
        if (stops == null) {
            return out;
        }
        for (int i = 0; i < stops.length(); i++) {
            JSONObject o = stops.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String id = HttpJson.str(o, "stopId");
            if (id.isEmpty()) {
                continue;
            }
            String name = HttpJson.firstNonEmpty(
                    HttpJson.str(o, "stopName_c"),
                    HttpJson.str(o, "stopName_e"),
                    HttpJson.str(o, "stopName_s"));
            String location = HttpJson.str(o, "stopLocation_c");
            out.add(new StopOption(NLB, route, "ALL", i + 1, id, name, 1,
                    routeId, location));
        }
        return out;
    }

    private List<EtaItem> nlbEta(StopOption stop) throws Exception {
        String url = NLB_BASE + "/stop.php?action=estimatedArrivals&routeId="
                + HttpJson.seg(stop.getRef()) + "&stopId="
                + HttpJson.seg(stop.getStopId()) + "&language=zh";
        JSONObject root = HttpJson.getObject(url);
        JSONArray arrivals = root.optJSONArray("estimatedArrivals");
        List<EtaItem> out = new ArrayList<>();
        if (arrivals == null) {
            return out;
        }
        for (int i = 0; i < arrivals.length(); i++) {
            JSONObject o = arrivals.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String rawTime = HttpJson.str(o, "estimatedArrivalTime");
            long millis = TimeUtils.parseHkCompact(rawTime);
            if (millis <= 0L) {
                continue;
            }
            String variant = HttpJson.str(o, "routeVariantName");
            String remark = variant.isEmpty()
                    ? (o.optInt("departed", 0) == 1 ? "已開出" : "實時預計到站")
                    : variant;
            out.add(new EtaItem(NLB, stop.getRoute(), stop.getStopId(),
                    stop.getNote(), rawTime.replace(' ', 'T') + "+08:00", millis,
                    i + 1, remark, "ALL", HttpJson.str(o, "generateTime")));
        }
        return out;
    }

    // ============================================================ GMB

    public static final class GmbVariant {
        public final String routeId;
        public final int routeSeq;
        public final String note;

        GmbVariant(String routeId, int routeSeq, String note) {
            this.routeId = routeId;
            this.routeSeq = routeSeq;
            this.note = note;
        }
    }

    private List<GmbVariant> gmbVariants(String route) throws Exception {
        List<GmbVariant> out = new ArrayList<>();
        JSONObject all = catalogueJson(RouteDataStore.GMB_ROUTES, GMB_BASE + "/route");
        JSONObject data = all.optJSONObject("data");
        JSONObject routes = data == null ? null : data.optJSONObject("routes");
        if (routes == null) {
            return out;
        }
        for (String region : GMB_REGIONS.split(",")) {
            JSONArray list = routes.optJSONArray(region);
            if (list == null || !containsString(list, route)) {
                continue;
            }
            JSONObject detail = HttpJson.getObject(
                    GMB_BASE + "/route/" + HttpJson.seg(region) + "/" + HttpJson.seg(route));
            JSONArray variants = detail.optJSONArray("data");
            if (variants == null) {
                continue;
            }
            for (int i = 0; i < variants.length(); i++) {
                JSONObject v = variants.optJSONObject(i);
                if (v == null) {
                    continue;
                }
                String routeId = HttpJson.str(v, "route_id");
                String description = HttpJson.str(v, "description_tc");
                JSONArray dirs = v.optJSONArray("directions");
                if (routeId.isEmpty() || dirs == null) {
                    continue;
                }
                for (int j = 0; j < dirs.length(); j++) {
                    JSONObject d = dirs.optJSONObject(j);
                    if (d == null) {
                        continue;
                    }
                    int seq = d.optInt("route_seq", j + 1);
                    String note = HttpJson.str(d, "orig_tc") + " → "
                            + HttpJson.str(d, "dest_tc")
                            + (description.isEmpty() ? "" : " · " + description);
                    out.add(new GmbVariant(routeId, seq, note));
                }
            }
        }
        return out;
    }

    private List<StopOption> gmbStops(String route) throws Exception {
        List<StopOption> out = new ArrayList<>();
        for (GmbVariant v : gmbVariants(route)) {
            String url = GMB_BASE + "/route-stop/"
                    + HttpJson.seg(v.routeId) + "/" + v.routeSeq;
            JSONObject root = HttpJson.getObject(url);
            JSONObject data = root.optJSONObject("data");
            JSONArray stops = data == null ? null : data.optJSONArray("route_stops");
            if (stops == null) {
                continue;
            }
            for (int i = 0; i < stops.length(); i++) {
                JSONObject o = stops.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String id = HttpJson.str(o, "stop_id");
                if (id.isEmpty()) {
                    continue;
                }
                String name = HttpJson.firstNonEmpty(
                        HttpJson.str(o, "name_tc"),
                        HttpJson.str(o, "name_en"),
                        HttpJson.str(o, "name_sc"));
                int seq = o.optInt("stop_seq", i + 1);
                out.add(new StopOption(GMB, route, String.valueOf(v.routeSeq), seq,
                        id, name, 1, v.routeId, v.note));
            }
        }
        return out;
    }

    private List<EtaItem> gmbEta(StopOption stop) throws Exception {
        String url = GMB_BASE + "/eta/route-stop/"
                + HttpJson.seg(stop.getRef()) + "/" + HttpJson.seg(stop.getStopId());
        JSONObject root = HttpJson.getObject(url);
        JSONArray occurrences = root.optJSONArray("data");
        List<EtaItem> out = new ArrayList<>();
        if (occurrences == null) {
            return out;
        }
        String generated = HttpJson.str(root, "generated_timestamp");
        for (int i = 0; i < occurrences.length(); i++) {
            JSONObject occurrence = occurrences.optJSONObject(i);
            if (occurrence == null || !occurrence.optBoolean("enabled", false)) {
                continue;
            }
            int routeSeq = occurrence.optInt("route_seq", 0);
            JSONArray etas = occurrence.optJSONArray("eta");
            if (etas == null) {
                continue;
            }
            for (int j = 0; j < etas.length(); j++) {
                JSONObject e = etas.optJSONObject(j);
                if (e == null) {
                    continue;
                }
                String iso = HttpJson.str(e, "timestamp");
                long millis = TimeUtils.parseHkIso(iso);
                if (millis <= 0L) {
                    millis = TimeUtils.nowPlusMinutes(e.optInt("diff", 0));
                }
                if (millis <= 0L) {
                    continue;
                }
                String remark = HttpJson.firstNonEmpty(
                        HttpJson.str(e, "remarks_tc"),
                        HttpJson.str(e, "remarks_en"),
                        "實時預計到站");
                out.add(new EtaItem(GMB, stop.getRoute(), stop.getStopId(),
                        stop.getNote(), iso, millis, e.optInt("eta_seq", j + 1),
                        remark, String.valueOf(routeSeq), generated));
            }
        }
        return out;
    }

    // ============================================================ MTR heavy rail

    private List<StopOption> mtrStops(String line) throws Exception {
        List<StopOption> out = new ArrayList<>();
        if (!MtrCatalog.isLineCode(line)) {
            return out;
        }
        List<Station> stations = MtrCatalog.heavyRailStations(line);
        for (int i = 0; i < stations.size(); i++) {
            Station s = stations.get(i);
            out.add(new StopOption(MTR, line, "ALL", i + 1, s.code,
                    s.nameTc, 1, line, MtrCatalog.lineName(line)));
        }
        return out;
    }

    private List<EtaItem> mtrEta(StopOption stop) throws Exception {
        String line = stop.getRef().isEmpty() ? stop.getRoute() : stop.getRef();
        String url = MTR_BASE + "/getSchedule.php?line=" + HttpJson.seg(line)
                + "&sta=" + HttpJson.seg(stop.getStopId()) + "&lang=tc";
        JSONObject root = HttpJson.getObject(url);
        if (root.optInt("status", 1) == 0) {
            throw new IOException(HttpJson.firstNonEmpty(
                    HttpJson.str(root, "message"), "港鐵暫無班次資料"));
        }
        JSONObject data = root.optJSONObject("data");
        JSONObject schedule = data == null ? null
                : data.optJSONObject(line + "-" + stop.getStopId());
        List<EtaItem> out = new ArrayList<>();
        if (schedule == null) {
            return out;
        }
        String[] dirs = {"UP", "DOWN"};
        for (String dir : dirs) {
            JSONArray trains = schedule.optJSONArray(dir);
            if (trains == null) {
                continue;
            }
            for (int i = 0; i < trains.length(); i++) {
                JSONObject t = trains.optJSONObject(i);
                if (t == null) {
                    continue;
                }
                String raw = HttpJson.str(t, "time");
                long millis = TimeUtils.parseHkCompact(raw);
                if (millis <= 0L) {
                    millis = TimeUtils.nowPlusMinutes(HttpJson.intOr(t, "ttnt", 0));
                }
                if (millis <= 0L) {
                    continue;
                }
                String platform = HttpJson.str(t, "plat");
                String dest = HttpJson.str(t, "dest");
                String remark = (platform.isEmpty() ? "" : platform + " 號月台 · ")
                        + "第 " + t.optInt("seq", i + 1) + " 班";
                out.add(new EtaItem(MTR, line, stop.getStopId(),
                        dest.isEmpty() ? "" : "往 " + dest,
                        raw.replace(' ', 'T') + "+08:00", millis,
                        t.optInt("seq", i + 1), remark, dir,
                        HttpJson.str(root, "curr_time")));
            }
        }
        return out;
    }

    // ============================================================ Light Rail

    private List<StopOption> lrtStops(String route) throws Exception {
        List<StopOption> out = new ArrayList<>();
        // In AUTO mode a bus route number must not flood the list with all LR stops.
        if (!route.isEmpty() && !"ALL".equalsIgnoreCase(route)
                && !route.matches("\\d{3}P?")) {
            return out;
        }
        String filter = route.isEmpty() ? "ALL" : route;
        List<Station> stations = MtrCatalog.lrtStations();
        for (int i = 0; i < stations.size(); i++) {
            Station s = stations.get(i);
            out.add(new StopOption(LRT, filter, "ALL", i + 1, s.code,
                    s.nameTc, 1, filter, "輕鐵"));
        }
        return out;
    }

    private List<EtaItem> lrtEta(StopOption stop) throws Exception {
        String url = MTR_BASE + "/lrt/getSchedule?station_id="
                + HttpJson.seg(stop.getStopId()) + "&with_special=1";
        JSONObject root = HttpJson.getObject(url);
        JSONArray platforms = root.optJSONArray("platform_list");
        List<EtaItem> out = new ArrayList<>();
        if (platforms == null) {
            return out;
        }
        String filter = stop.getRef();
        for (int p = 0; p < platforms.length(); p++) {
            JSONObject platform = platforms.optJSONObject(p);
            if (platform == null) {
                continue;
            }
            String platformId = HttpJson.str(platform, "platform_id");
            JSONArray routes = platform.optJSONArray("route_list");
            if (routes == null) {
                continue;
            }
            for (int i = 0; i < routes.length(); i++) {
                JSONObject r = routes.optJSONObject(i);
                if (r == null) {
                    continue;
                }
                String routeNo = HttpJson.str(r, "route_no");
                if (!filter.isEmpty() && !"ALL".equalsIgnoreCase(filter)
                        && !filter.equalsIgnoreCase(routeNo)) {
                    continue;
                }
                int minutes = TimeUtils.minutesFromText(
                        HttpJson.firstNonEmpty(HttpJson.str(r, "time_ch"),
                                HttpJson.str(r, "time_en")));
                long millis = minutes > 0
                        ? TimeUtils.nowPlusMinutes(minutes)
                        : System.currentTimeMillis();
                String remark = (platformId.isEmpty() ? "" : platformId + " 號月台")
                        + (r.optInt("special", 0) == 1 ? " · 特別班次" : "");
                String extra = HttpJson.firstNonEmpty(
                        HttpJson.str(r, "routeRemarkChi2"),
                        HttpJson.str(r, "additionalInfo1"));
                if (!extra.isEmpty()) {
                    remark += " · " + extra;
                }
                out.add(new EtaItem(LRT, routeNo, stop.getStopId(),
                        HttpJson.str(r, "dest_ch"),
                        HttpJson.firstNonEmpty(HttpJson.str(r, "time_en"),
                                HttpJson.str(r, "time_ch")),
                        millis, i + 1, remark, "ALL",
                        HttpJson.str(root, "generated_timestamp")));
            }
        }
        return out;
    }

    // ============================================================ MTR Bus

    private JSONObject postSchedule(String route) throws Exception {
        String body = "{\"language\":\"zh\",\"routeName\":\"" + jsonEscape(route) + "\"}";
        return HttpJson.postJson(MTR_BASE + "/bus/getSchedule", body);
    }

    private List<StopOption> mtrBusStops(String route) throws Exception {
        List<StopOption> out = new ArrayList<>();
        if (!isMtrBusRoute(route)) {
            return out;
        }
        JSONObject root = postSchedule(route);
        JSONArray busStops = root.optJSONArray("busStop");
        if (busStops == null) {
            return out;
        }
        for (int i = 0; i < busStops.length(); i++) {
            JSONObject o = busStops.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String id = HttpJson.str(o, "busStopId");
            if (id.isEmpty()) {
                continue;
            }
            String name = MtrCatalog.busStopName(id);
            if (name.isEmpty()) {
                name = HttpJson.str(o, "busStopRemark");
            }
            if (name.isEmpty()) {
                name = id;
            }
            out.add(new StopOption(MTRB, route, "ALL", i + 1, id, name, 1,
                    route, HttpJson.str(o, "busStopRemark")));
        }
        return out;
    }

    private List<EtaItem> mtrBusEta(StopOption stop) throws Exception {
        JSONObject root = postSchedule(
                stop.getRef().isEmpty() ? stop.getRoute() : stop.getRef());
        JSONArray busStops = root.optJSONArray("busStop");
        List<EtaItem> out = new ArrayList<>();
        if (busStops == null) {
            return out;
        }
        for (int i = 0; i < busStops.length(); i++) {
            JSONObject s = busStops.optJSONObject(i);
            if (s == null || !stop.getStopId().equals(HttpJson.str(s, "busStopId"))) {
                continue;
            }
            JSONArray buses = s.optJSONArray("bus");
            if (buses == null) {
                continue;
            }
            for (int j = 0; j < buses.length(); j++) {
                JSONObject b = buses.optJSONObject(j);
                if (b == null) {
                    continue;
                }
                String arrive = HttpJson.str(b, "arrivalTimeInSecond");
                String seconds = "108000".equals(arrive)
                        ? HttpJson.str(b, "departureTimeInSecond") : arrive;
                long millis = TimeUtils.nowPlusSeconds(seconds);
                if (millis <= 0L) {
                    continue;
                }
                String remark = HttpJson.firstNonEmpty(
                        HttpJson.str(b, "busRemark"),
                        "1".equals(HttpJson.str(b, "isScheduled")) ? "預定班次" : "",
                        HttpJson.str(b, "arrivalTimeText"),
                        HttpJson.str(b, "departureTimeText"),
                        "實時預計到站");
                out.add(new EtaItem(MTRB, stop.getRoute(), stop.getStopId(),
                        stop.getNote(), "", millis, j + 1, remark, "ALL",
                        HttpJson.str(root, "routeStatusTime")));
            }
        }
        return out;
    }

    // ============================================================ internals

    private static boolean isMtrBusRoute(String route) {
        for (String r : MtrCatalog.MTR_BUS_ROUTES) {
            if (r.equalsIgnoreCase(route)) {
                return true;
            }
        }
        return false;
    }

    private static String guessCompany(String route, String stopId) {
        if (MtrCatalog.isLineCode(route)) {
            return MTR;
        }
        if (stopId.length() == 6 && stopId.matches("\\d+")) {
            return CTB;
        }
        if (stopId.length() <= 3 && stopId.matches("\\d+")) {
            return LRT;
        }
        if (isMtrBusRoute(route)) {
            return MTRB;
        }
        return KMB;
    }

    private static boolean containsString(JSONArray array, String value) {
        for (int i = 0; i < array.length(); i++) {
            if (value.equalsIgnoreCase(array.optString(i, ""))) {
                return true;
            }
        }
        return false;
    }

    private static String jsonEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Map<String, String> safeNames(String company) {
        try {
            return stopNames(company);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static JSONObject fetchWithSlashFallback(String url) throws Exception {
        try {
            return HttpJson.getObject(url);
        } catch (IOException first) {
            try {
                return HttpJson.getObject(url + "/");
            } catch (IOException second) {
                throw first;
            }
        }
    }

    private List<StopOption> parseRouteStops(JSONObject root, String company,
                                             String route, String direction,
                                             Map<String, String> names,
                                             boolean kmbServiceType) {
        List<StopOption> out = new ArrayList<>();
        JSONArray data = root == null ? null : root.optJSONArray("data");
        if (data == null) {
            return out;
        }
        // First pass: collect the stop ids. Second pass builds StopOption with
        // resolved names, so the final name is immutable in the model.
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String stopId = HttpJson.str(o, "stop");
            if (!stopId.isEmpty()) {
                ids.add(stopId);
            }
        }
        Map<String, String> effective = new HashMap<>(names);
        resolveMissingNames(company, ids, effective);

        String fallbackDir = "outbound".equals(direction) ? "O" : "I";
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String stopId = HttpJson.str(o, "stop");
            if (stopId.isEmpty()) {
                continue;
            }
            String dir = HttpJson.firstNonEmpty(
                    HttpJson.str(o, "bound"), HttpJson.str(o, "dir"), fallbackDir);
            int seq = o.optInt("seq", i + 1);
            int serviceType = 1;
            if (kmbServiceType) {
                serviceType = toServiceType(o.optString("service_type", "1"));
            }
            String name = effective.containsKey(stopId) ? effective.get(stopId) : "";
            out.add(new StopOption(company, route, dir, seq, stopId, name,
                    serviceType, "", ""));
        }
        return out;
    }

    /**
     * Fills in names missing from the bulk snapshot using the official
     * /stop/{stop_id} API. Bounded (3 threads, 12 s, max 30 stops) so an
     * unreachable operator can never hang the stop list. Resolved names are
     * persisted, so this cost is paid only once per stop.
     */
    private void resolveMissingNames(final String company, List<String> ids,
                                     Map<String, String> names) {
        final int MAX_LOOKUPS = 30;
        List<String> missing = new ArrayList<>();
        for (String id : ids) {
            if (!names.containsKey(id) && !missing.contains(id)
                    && missing.size() < MAX_LOOKUPS) {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<Callable<String[]>> tasks = new ArrayList<>();
            for (final String id : missing) {
                tasks.add(new Callable<String[]>() {
                    @Override
                    public String[] call() {
                        return new String[]{id, fetchStopName(company, id)};
                    }
                });
            }
            List<Future<String[]>> futures = pool.invokeAll(tasks, 12L, TimeUnit.SECONDS);
            for (Future<String[]> f : futures) {
                if (f.isCancelled()) {
                    continue;
                }
                try {
                    String[] pair = f.get();
                    if (pair[1] != null && !pair[1].isEmpty()) {
                        names.put(pair[0], pair[1]);
                    }
                } catch (Exception ignored) {
                    // one unreachable stop must not hide the others
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
    }

    private static int toServiceType(String raw) {
        if (raw == null) {
            return 1;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v < 1 ? 1 : v;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Handles KMB, Citybus and the DATA.GOV.HK batch Stop-ETA payloads.
     * When routeFromData is true, each ETA object supplies its own route number.
     */
    private static List<EtaItem> parseBusEta(JSONObject root, String company,
                                             String defaultRoute, String stopId,
                                             boolean routeFromData) {
        List<EtaItem> out = new ArrayList<>();
        JSONArray data = root == null ? null : root.optJSONArray("data");
        if (data == null) {
            return out;
        }
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String etaIso = HttpJson.str(o, "eta");
            long millis = TimeUtils.parseHkIso(etaIso);
            if (millis <= 0L) {
                continue;
            }
            String route = routeFromData
                    ? HttpJson.firstNonEmpty(HttpJson.str(o, "route"), defaultRoute)
                    : defaultRoute;
            String dest = HttpJson.firstNonEmpty(
                    HttpJson.str(o, "dest_tc"),
                    HttpJson.str(o, "dest"),
                    HttpJson.str(o, "dest_en"),
                    HttpJson.str(o, "dest_sc"));
            String remark = HttpJson.firstNonEmpty(
                    HttpJson.str(o, "rmk_tc"),
                    HttpJson.str(o, "rmk"),
                    HttpJson.str(o, "rmk_en"),
                    HttpJson.str(o, "rmk_sc"),
                    HttpJson.str(o, "routeVariantName"));
            int etaSeq = o.optInt("eta_seq", i + 1);
            String dir = HttpJson.str(o, "dir");
            String dataTimestamp = HttpJson.str(o, "data_timestamp");
            out.add(new EtaItem(company, route, stopId, dest, etaIso, millis,
                    etaSeq, remark, dir, dataTimestamp));
        }
        Collections.sort(out, new Comparator<EtaItem>() {
            @Override
            public int compare(EtaItem a, EtaItem b) {
                return Long.compare(a.getEtaMillis(), b.getEtaMillis());
            }
        });
        return out;
    }
}
