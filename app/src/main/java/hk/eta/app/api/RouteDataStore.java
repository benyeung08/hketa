package hk.eta.app.api;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local snapshot of the official route / stop catalogues.
 *
 * The Settings screen calls refresh() ("線路資料更新"). Once a snapshot exists the
 * Java data layer prefers it (fast, works offline for catalogue lookups) and only
 * falls back to live HTTP when the file is missing. Real-time ETA is NEVER cached:
 * those endpoints are always requested live, every 30 seconds.
 *
 * Files live in the app's private filesDir/route_data/ and are written atomically
 * (.tmp then rename), so a failed download never destroys the previous snapshot.
 */
public final class RouteDataStore {

    public static final String KMB_ROUTES = "kmb_routes.json";
    public static final String KMB_STOPS = "kmb_stops.json";
    public static final String CTB_ROUTES = "ctb_routes.json";
    public static final String CTB_STOPS = "ctb_stops.json";
    public static final String NLB_ROUTES = "nlb_routes.json";
    public static final String NLB_STOPS = "nlb_stops.json";   // derived, see buildNlbStops()
    public static final String GMB_ROUTES = "gmb_routes.json";
    public static final String GMB_STOPS = "gmb_stops.json";   // optional bulk resource
    public static final String MTR_LINES = "mtr_lines_and_stations.csv";
    public static final String LRT_STOPS = "light_rail_routes_and_stops.csv";
    public static final String MTR_BUS_STOPS = "mtr_bus_stops.csv";
    public static final String META = "meta.json";

    private static final String MTR_DATA = "https://opendata.mtr.com.hk/data/";
    private static final String DIR = "route_data";
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    private static volatile Context appContext;

    private RouteDataStore() {
    }

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        File dir = new File(appContext.getFilesDir(), DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    // ------------------------------------------------------------------ datasets

    private static final class Dataset {
        final String file;
        final String label;
        /** Tried in order; each URL is retried with a browser User-Agent too. */
        final String[] urls;
        final boolean json;
        final boolean optional;

        Dataset(String file, String label, boolean json, boolean optional, String... urls) {
            this.file = file;
            this.label = label;
            this.urls = urls;
            this.json = json;
            this.optional = optional;
        }
    }

    /**
     * IMPORTANT: Citybus v2 has NO full stop-list endpoint in the official spec
     * (only /stop/{stop_id}). The old entry pointed at /v2/transport/citybus/stop
     * and therefore ALWAYS failed with an HTML 403 page. CTB_STOPS is now a
     * derived, on-demand cache built from single-stop lookups — no download.
     *
     * KMB's /stop full list IS official, but edge nodes sometimes 403 it. It is
     * marked optional: when it fails, EtaRepository falls back to the official
     * /stop/{stop_id} API and the app still shows Chinese stop names.
     */
    private static final Dataset[] DATASETS = {
            new Dataset(KMB_ROUTES, "九巴／龍運路線清單", true, false,
                    "https://data.etabus.gov.hk/v1/transport/kmb/route/",
                    "https://data.etabus.gov.hk/v1/transport/kmb/route"),
            new Dataset(KMB_STOPS, "九巴／龍運全量站點（選用）", true, true,
                    "https://data.etabus.gov.hk/v1/transport/kmb/stop",
                    "https://data.etabus.gov.hk/v1/transport/kmb/stop/"),
            new Dataset(CTB_ROUTES, "城巴路線清單", true, false,
                    "https://rt.data.gov.hk/v2/transport/citybus/route/CTB",
                    "https://rt.data.gov.hk/v1/transport/citybus-nwfb/route/CTB"),
            // urls is deliberately empty: derived from single-stop API, see stopCacheStatus().
            new Dataset(CTB_STOPS, "城巴站名快取（單站 API 按需）", true, true),
            new Dataset(NLB_ROUTES, "嶼巴路線清單", true, false,
                    "https://rt.data.gov.hk/v2/transport/nlb/route.php?action=list"),
            new Dataset(GMB_ROUTES, "綠小巴路線清單", true, false,
                    "https://data.etagmb.gov.hk/route",
                    "https://data.etagmb.gov.hk/route/"),
            new Dataset(GMB_STOPS, "綠小巴站點索引（選用）", true, true,
                    "https://data.etagmb.gov.hk/stop"),
            new Dataset(MTR_LINES, "港鐵重鐵線路及車站 CSV", false, false,
                    MTR_DATA + MTR_LINES),
            new Dataset(LRT_STOPS, "輕鐵路線及車站 CSV", false, false,
                    MTR_DATA + LRT_STOPS),
            new Dataset(MTR_BUS_STOPS, "港鐵接駁巴士車站 CSV", false, false,
                    MTR_DATA + MTR_BUS_STOPS)
    };

    // ------------------------------------------------------------------ results

    public static final class SourceStatus {
        public final String file;
        public final String label;
        public final boolean ok;
        public final int count;
        public final String error;
        public final long at;
        /** Optional / derived source that was skipped: NOT a failure. */
        public final boolean skipped;

        SourceStatus(String file, String label, boolean ok, int count,
                     String error, long at) {
            this(file, label, ok, count, error, at, false);
        }

        SourceStatus(String file, String label, boolean ok, int count,
                     String error, long at, boolean skipped) {
            this.file = file;
            this.label = label;
            this.ok = ok;
            this.count = count;
            this.error = error == null ? "" : error;
            this.at = at;
            this.skipped = skipped;
        }
    }

    public static final class Report {
        public final boolean busy;
        public final boolean skipped;
        public final String message;
        public final long updatedAt;
        public final int okCount;
        public final int total;
        public final int records;
        public final List<SourceStatus> sources;

        Report(boolean busy, boolean skipped, String message, long updatedAt,
               int okCount, int total, int records, List<SourceStatus> sources) {
            this.busy = busy;
            this.skipped = skipped;
            this.message = message;
            this.updatedAt = updatedAt;
            this.okCount = okCount;
            this.total = total;
            this.records = records;
            this.sources = sources;
        }

        static Report busy() {
            return new Report(true, false, "已有更新正在進行中", 0L, 0, 0, 0,
                    new ArrayList<SourceStatus>());
        }

        static Report skipped(String reason) {
            return new Report(false, true, reason, 0L, 0, 0, 0,
                    new ArrayList<SourceStatus>());
        }

        static Report empty() {
            return new Report(false, false, "尚未更新", 0L, 0, DATASETS.length + 1, 0,
                    new ArrayList<SourceStatus>());
        }
    }

    // ------------------------------------------------------------------ public API

    public static boolean refreshBusy() {
        return BUSY.get();
    }

    public static boolean onWifi() {
        Context ctx = appContext;
        if (ctx == null) {
            return false;
        }
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }
            Network network = cm.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Exception e) {
            return false;
        }
    }

    public static long lastUpdated() {
        return meta().updatedAt;
    }

    public static Report meta() {
        File dir = dirOrNull();
        if (dir == null) {
            return Report.empty();
        }
        String text = readTextFrom(new File(dir, META));
        if (text == null) {
            return Report.empty();
        }
        try {
            JSONObject root = new JSONObject(text);
            JSONObject sourcesObj = root.optJSONObject("sources");
            List<SourceStatus> list = new ArrayList<>();
            int ok = 0;
            int records = 0;
            if (sourcesObj != null) {
                for (String file : keySet(sourcesObj)) {
                    JSONObject o = sourcesObj.optJSONObject(file);
                    if (o == null) {
                        continue;
                    }
                    boolean good = o.optBoolean("ok", false);
                    int count = o.optInt("count", 0);
                    if (good) {
                        ok++;
                        records += count;
                    }
                    list.add(new SourceStatus(file,
                            o.optString("label", file), good, count,
                            o.optString("error", ""), o.optLong("at", 0L)));
                }
            }
            int total = root.optInt("total", DATASETS.length + 1);
            return new Report(false, false, root.optString("message", ""),
                    root.optLong("updated_at", 0L), ok, total, records, list);
        } catch (Exception e) {
            return Report.empty();
        }
    }

    /** Manual / scheduled refresh. wifiOnly = true skips when not on Wi-Fi. */
    public static Report refresh(boolean wifiOnly) {
        if (!BUSY.compareAndSet(false, true)) {
            return Report.busy();
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Context ctx = appContext;
            if (ctx == null) {
                return Report.skipped("尚未初始化，請重新開啟 App");
            }
            if (wifiOnly && !onWifi()) {
                return Report.skipped("已設為僅 Wi-Fi 更新，目前沒有 Wi-Fi 連線");
            }
            final File dir = new File(ctx.getFilesDir(), DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                return Report.skipped("無法建立本機資料目錄");
            }

            List<Callable<SourceStatus>> tasks = new ArrayList<>();
            for (final Dataset d : DATASETS) {
                tasks.add(new Callable<SourceStatus>() {
                    @Override
                    public SourceStatus call() {
                        return download(d, dir);
                    }
                });
            }
            List<Future<SourceStatus>> futures = pool.invokeAll(tasks, 120L, TimeUnit.SECONDS);
            List<SourceStatus> statuses = new ArrayList<>();
            for (Future<SourceStatus> f : futures) {
                if (f.isCancelled()) {
                    continue;
                }
                try {
                    statuses.add(f.get());
                } catch (Exception e) {
                    // treated as a failed source below
                }
            }
            for (Dataset d : DATASETS) {
                if (!containsStatus(statuses, d.file)) {
                    statuses.add(new SourceStatus(d.file, d.label, false, 0,
                            "更新逾時", System.currentTimeMillis()));
                }
            }
            statuses.add(buildNlbStops(dir));

            int ok = 0;
            int records = 0;
            int failedRequired = 0;
            List<String> failures = new ArrayList<>();
            List<String> skippedLabels = new ArrayList<>();
            JSONObject sourcesObj = new JSONObject();
            long now = System.currentTimeMillis();
            for (SourceStatus s : statuses) {
                JSONObject o = new JSONObject();
                try {
                    o.put("label", s.label);
                    o.put("ok", s.ok);
                    o.put("skipped", s.skipped);
                    o.put("count", s.count);
                    o.put("error", s.error);
                    o.put("at", s.at > 0L ? s.at : now);
                    sourcesObj.put(s.file, o);
                } catch (Exception ignored) {
                    // keep going
                }
                if (s.ok) {
                    ok++;
                    records += s.count;
                    if (s.skipped) {
                        skippedLabels.add(s.label);
                    }
                } else if (s.skipped) {
                    // Optional / derived source unavailable: expected, not an error.
                    skippedLabels.add(s.label);
                } else {
                    failures.add(s.label);
                    Dataset d = find(s.file);
                    if (d != null && !d.optional) {
                        failedRequired++;
                    }
                }
            }

            String message;
            if (ok == 0) {
                message = "全部來源更新失敗，請檢查網絡後重試";
            } else if (failedRequired > 0) {
                message = "已更新 " + ok + "/" + statuses.size() + " 個來源（部分失敗："
                        + join(failures, 3) + "）";
            } else {
                message = "已更新 " + ok + " 個來源 · 共 " + records + " 筆資料";
                if (!skippedLabels.isEmpty()) {
                    message += "（另有 " + skippedLabels.size()
                            + " 個選用／按需來源略過，唔影響站名顯示）";
                }
            }

            JSONObject root = new JSONObject();
            try {
                root.put("updated_at", now);
                root.put("total", statuses.size());
                root.put("message", message);
                root.put("sources", sourcesObj);
                writeAtomic(new File(dir, META), root.toString());
            } catch (Exception ignored) {
                // Snapshot files are still valid even if meta write fails.
            }

            invalidateDownstream();
            return new Report(false, false, message, now, ok,
                    statuses.size(), records, statuses);
        } catch (Exception e) {
            return Report.skipped("更新失敗：" + e.getMessage());
        } finally {
            pool.shutdownNow();
            BUSY.set(false);
        }
    }

    /** Removes every snapshot; the app immediately falls back to live official APIs. */
    public static void clear() {
        File dir = dirOrNull();
        if (dir != null) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
        invalidateDownstream();
    }

    /** Returns the local JSON snapshot, or null when it has not been downloaded. */
    public static JSONObject readJson(String file) {
        String text = readText(file);
        if (text == null) {
            return null;
        }
        try {
            return new JSONObject(text);
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns a local CSV / JSON snapshot as text, or null. */
    public static String readText(String file) {
        File dir = dirOrNull();
        if (dir == null) {
            return null;
        }
        return readTextFrom(new File(dir, file));
    }

    /**
     * Local snapshot first, live official API as fallback.
     * A snapshot marked "partial" (built from single-stop lookups) is merged
     * with the full live list when that list becomes reachable again; if the
     * full list still 403s, the partial snapshot is still usable for names.
     */
    public static JSONObject catalogueJson(String file, String... urls) throws Exception {
        JSONObject local = readJson(file);
        boolean partial = local != null && local.optBoolean("partial", false);
        if (local != null && !partial) {
            return local;
        }
        if (urls != null && urls.length > 0) {
            try {
                JSONObject live = new JSONObject(HttpJson.getFirst(urls));
                if (local == null) {
                    return live;
                }
                return mergeStops(local, live);
            } catch (Exception ignored) {
                // Full list unavailable (e.g. HTTP 403 HTML page). Fall through.
            }
        }
        if (local != null) {
            return local;
        }
        throw new IOException("無法讀取 " + file + " 且官方端點不可用");
    }

    /** Keeps cached single-stop entries that are missing from a full list. */
    private static JSONObject mergeStops(JSONObject partial, JSONObject live) {
        JSONArray merged = live.optJSONArray("data");
        if (merged == null) {
            return live;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < merged.length(); i++) {
            JSONObject o = merged.optJSONObject(i);
            if (o != null) {
                seen.add(HttpJson.str(o, "stop"));
            }
        }
        JSONArray extra = partial.optJSONArray("data");
        if (extra != null) {
            for (int i = 0; i < extra.length(); i++) {
                JSONObject o = extra.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String id = HttpJson.str(o, "stop");
                if (!id.isEmpty() && seen.add(id)) {
                    merged.put(o);
                }
            }
        }
        try {
            live.remove("partial");
            live.put("data", merged);
        } catch (Exception ignored) {
            // keep live as-is
        }
        return live;
    }

    /**
     * Persists one stop resolved through the official single-stop API so the
     * next query (and the nearby-stop index) needs no network at all.
     * The file is deliberately flagged "partial": it must never suppress a
     * successful full-list download.
     */
    public static synchronized void cacheStop(String file, String stopId,
                                              String nameTc, String nameEn,
                                              String nameSc, double lat, double lng) {
        File dir = dirOrNull();
        if (dir == null || stopId == null || stopId.isEmpty()) {
            return;
        }
        try {
            JSONObject root = readJson(file);
            boolean isNew = root == null;
            if (root == null) {
                root = new JSONObject();
                root.put("type", "StopList");
                root.put("version", "1.0");
                root.put("generated_timestamp",
                        new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",
                                Locale.US).format(new java.util.Date()));
            }
            // Adding one stop to an already-complete full-list snapshot must
            // not downgrade it to "partial" (that would force a live download
            // on every later lookup).
            boolean keepPartial = isNew || root.optBoolean("partial", false);
            JSONArray data = root.optJSONArray("data");
            if (data == null) {
                data = new JSONArray();
            }
            boolean found = false;
            for (int i = 0; i < data.length(); i++) {
                JSONObject o = data.optJSONObject(i);
                if (o != null && stopId.equals(HttpJson.str(o, "stop"))) {
                    fillStop(o, nameTc, nameEn, nameSc, lat, lng);
                    found = true;
                    break;
                }
            }
            if (!found) {
                JSONObject o = new JSONObject();
                o.put("stop", stopId);
                fillStop(o, nameTc, nameEn, nameSc, lat, lng);
                data.put(o);
            }
            if (keepPartial) {
                root.put("partial", true);
            } else {
                root.remove("partial");
            }
            root.put("data", data);
            if (!dir.exists() && !dir.mkdirs()) {
                return;
            }
            writeAtomic(new File(dir, file), root.toString());
        } catch (Exception ignored) {
            // A cache write failure must never break an ETA query.
        }
    }

    private static void fillStop(JSONObject o, String tc, String en, String sc,
                                 double lat, double lng) throws Exception {
        o.put("name_tc", tc);
        o.put("name_en", en);
        o.put("name_sc", sc);
        if (lat != 0.0) {
            o.put("lat", lat);
        }
        if (lng != 0.0) {
            o.put("long", lng);
        }
    }

    public static void invalidateDownstream() {
        MtrCatalog.invalidateCaches();
        GeoStops.invalidate();
        EtaRepository.clearCaches();
    }

    // ------------------------------------------------------------------ download

    private static SourceStatus download(Dataset d, File dir) {
        long now = System.currentTimeMillis();
        // No official bulk endpoint exists for this file (Citybus stops). The
        // snapshot is built incrementally by single-stop lookups instead.
        if (d.urls.length == 0) {
            return stopCacheStatus(d, now);
        }
        String body = null;
        String error = "";
        try {
            // getFirst() retries every URL with both an app and a browser UA,
            // and rejects the HTML WAF pages that caused the HTTP 403 report.
            body = d.json ? HttpJson.getFirst(d.urls) : HttpJson.getFirstText(d.urls);
        } catch (Exception first) {
            error = first.getMessage() == null ? "連線失敗" : first.getMessage();
        }
        if (body == null || body.trim().isEmpty()) {
            String reason = error.isEmpty() ? "空回應" : error;
            return new SourceStatus(d.file, d.label, false, 0,
                    d.optional ? "選用來源略過：" + reason : reason, now, d.optional);
        }
        int count = countRecords(body, d.json);
        if (d.json && count < 0) {
            return new SourceStatus(d.file, d.label, false, 0,
                    d.optional ? "選用來源略過：JSON 格式錯誤" : "JSON 格式錯誤",
                    now, d.optional);
        }
        try {
            writeAtomic(new File(dir, d.file), body);
            return new SourceStatus(d.file, d.label, true, count, "", now);
        } catch (IOException e) {
            return new SourceStatus(d.file, d.label, false, 0, "寫入失敗", now);
        }
    }

    /** Status of the derived Citybus stop-name cache (no bulk endpoint exists). */
    private static SourceStatus stopCacheStatus(Dataset d, long now) {
        JSONObject root = readJson(d.file);
        JSONArray data = root == null ? null : root.optJSONArray("data");
        int count = data == null ? 0 : data.length();
        String note = count == 0
                ? "官方無全量端點；查路線時用 /stop/{stop_id} 自動建立"
                : "按需快取 " + count + " 個站（單站 API）";
        return new SourceStatus(d.file, d.label, count > 0, count, note, now, count == 0);
    }

    /**
     * Merges all NLB route stop lists into one local file so the nearby-stop
     * index does not need ~64 live requests on every cold start.
     */
    private static SourceStatus buildNlbStops(File dir) {
        long now = System.currentTimeMillis();
        File routesFile = new File(dir, NLB_ROUTES);
        String routesText = readTextFrom(routesFile);
        if (routesText == null) {
            return new SourceStatus(NLB_STOPS, "嶼巴站點座標（合併）", false, 0,
                    "缺少嶼巴路線清單", now);
        }
        try {
            JSONObject root = new JSONObject(routesText);
            JSONArray routes = root.optJSONArray("routes");
            if (routes == null || routes.length() == 0) {
                return new SourceStatus(NLB_STOPS, "嶼巴站點座標（合併）", false, 0,
                        "嶼巴路線清單為空", now);
            }
            final List<String[]> jobs = new ArrayList<>();
            for (int i = 0; i < routes.length() && i < 90; i++) {
                JSONObject r = routes.optJSONObject(i);
                if (r == null) {
                    continue;
                }
                String id = HttpJson.str(r, "routeId");
                String no = HttpJson.str(r, "routeNo");
                if (!id.isEmpty()) {
                    jobs.add(new String[]{id, no});
                }
            }
            ExecutorService pool = Executors.newFixedThreadPool(6);
            final JSONArray merged = new JSONArray();
            final Set<String> seen = new HashSet<>();
            try {
                List<Callable<Void>> tasks = new ArrayList<>();
                for (final String[] job : jobs) {
                    tasks.add(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            String url = "https://rt.data.gov.hk/v2/transport/nlb/"
                                    + "stop.php?action=list&routeId=" + HttpJson.seg(job[0]);
                            JSONArray stops = HttpJson.getObject(url).optJSONArray("stops");
                            if (stops == null) {
                                return null;
                            }
                            synchronized (merged) {
                                for (int j = 0; j < stops.length(); j++) {
                                    JSONObject s = stops.optJSONObject(j);
                                    if (s == null) {
                                        continue;
                                    }
                                    String stopId = HttpJson.str(s, "stopId");
                                    if (stopId.isEmpty() || !seen.add(stopId)) {
                                        continue;
                                    }
                                    JSONObject copy = new JSONObject(s.toString());
                                    copy.put("routeId", job[0]);
                                    copy.put("routeNo", job[1]);
                                    merged.put(copy);
                                }
                            }
                            return null;
                        }
                    });
                }
                pool.invokeAll(tasks, 90L, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
            }
            int count = merged.length();
            if (count == 0) {
                return new SourceStatus(NLB_STOPS, "嶼巴站點座標（合併）", false, 0,
                        "嶼巴站序暫不可用", now);
            }
            JSONObject out = new JSONObject();
            out.put("stops", merged);
            out.put("generated_timestamp",
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
                            .format(new java.util.Date(now)));
            writeAtomic(new File(dir, NLB_STOPS), out.toString());
            return new SourceStatus(NLB_STOPS, "嶼巴站點座標（合併）", true, count, "", now);
        } catch (Exception e) {
            return new SourceStatus(NLB_STOPS, "嶼巴站點座標（合併）", false, 0,
                    e.getMessage() == null ? "合併失敗" : e.getMessage(), now);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static int countRecords(String body, boolean json) {
        if (!json) {
            int lines = 0;
            for (String line : body.split("\\r?\\n")) {
                if (!line.trim().isEmpty()) {
                    lines++;
                }
            }
            return Math.max(0, lines - 1);
        }
        try {
            JSONObject root = new JSONObject(body);
            JSONArray data = root.optJSONArray("data");
            if (data != null) {
                return data.length();
            }
            JSONArray routes = root.optJSONArray("routes");
            if (routes != null) {
                return routes.length();
            }
            JSONObject dataObj = root.optJSONObject("data");
            if (dataObj != null) {
                JSONObject routesObj = dataObj.optJSONObject("routes");
                if (routesObj != null) {
                    int total = 0;
                    for (String region : new String[]{"HKI", "KLN", "NT"}) {
                        JSONArray a = routesObj.optJSONArray(region);
                        if (a != null) {
                            total += a.length();
                        }
                    }
                    return total;
                }
                JSONArray rs = dataObj.optJSONArray("route_stops");
                if (rs != null) {
                    return rs.length();
                }
            }
            JSONArray stops = root.optJSONArray("stops");
            return stops == null ? 0 : stops.length();
        } catch (Exception e) {
            return -1;
        }
    }

    private static File dirOrNull() {
        Context ctx = appContext;
        if (ctx == null) {
            return null;
        }
        return new File(ctx.getFilesDir(), DIR);
    }

    private static String readTextFrom(File file) {
        if (!file.exists() || file.length() == 0L) {
            return null;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
    }

    private static void writeAtomic(File target, String body) throws IOException {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tmp);
            out.write(body.getBytes("UTF-8"));
            out.flush();
        } finally {
            if (out != null) {
                out.close();
            }
        }
        if (!tmp.renameTo(target)) {
            tmp.delete();
            throw new IOException("rename failed");
        }
    }

    private static Dataset find(String file) {
        for (Dataset d : DATASETS) {
            if (d.file.equals(file)) {
                return d;
            }
        }
        return null;
    }

    private static boolean containsStatus(List<SourceStatus> list, String file) {
        for (SourceStatus s : list) {
            if (s.file.equals(file)) {
                return true;
            }
        }
        return false;
    }

    private static String join(List<String> values, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size() && i < max; i++) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(values.get(i));
        }
        if (values.size() > max) {
            sb.append(" 等 ").append(values.size()).append(" 項");
        }
        return sb.toString();
    }

    private static List<String> keySet(JSONObject object) {
        List<String> out = new ArrayList<>();
        java.util.Iterator<String> it = object.keys();
        while (it.hasNext()) {
            out.add(it.next());
        }
        java.util.Collections.sort(out);
        return out;
    }
}
