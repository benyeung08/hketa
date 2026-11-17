package hk.eta.app.api;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import hk.eta.app.api.EtaModels.StopOption;
import hk.eta.app.api.MtrCatalog.NearbySeed;

/**
 * Builds the "nearby stops" catalogue from official open-data sources.
 *
 * KMB / CTB : full stop list JSON (lat, long)
 * NLB       : route list + stop list of every route (latitude, longitude), merged & de-duplicated
 * GMB       : /stop (all stops when the endpoint exposes a list; coordinates.wgs84)
 * MTR / LRT / MTR Bus : monthly CSV catalogues, only if they contain a coordinate column
 *
 * Anything that is unavailable is skipped silently; the UI tells the user when no
 * coordinate-bearing stops were found. Results are cached for 30 minutes.
 */
public final class GeoStops {

    private static final String KMB_BASE = "https://data.etabus.gov.hk/v1/transport/kmb";
    private static final String CTB_BASE = "https://rt.data.gov.hk/v2/transport/citybus";
    private static final String NLB_BASE = "https://rt.data.gov.hk/v2/transport/nlb";
    private static final String GMB_BASE = "https://data.etagmb.gov.hk";

    private static final long CACHE_TTL_MS = 30L * 60L * 1000L;
    private static volatile List<NearStop> cache;
    private static volatile long cacheAt;

    private GeoStops() {
    }

    public static final class NearStop {
        public final String company;
        public final String stopId;
        public final String name;
        public final String ref;
        public final String note;
        public final double lat;
        public final double lng;

        NearStop(String company, String stopId, String name, String ref,
                 String note, double lat, double lng) {
            this.company = company;
            this.stopId = stopId;
            this.name = name == null ? "" : name;
            this.ref = ref == null ? "" : ref;
            this.note = note == null ? "" : note;
            this.lat = lat;
            this.lng = lng;
        }
    }

    /** All coordinate-bearing stops, cached. Never throws. */
    public static List<NearStop> all() {
        List<NearStop> cached = cache;
        if (cached != null && System.currentTimeMillis() - cacheAt < CACHE_TTL_MS) {
            return cached;
        }
        synchronized (GeoStops.class) {
            cached = cache;
            if (cached != null && System.currentTimeMillis() - cacheAt < CACHE_TTL_MS) {
                return cached;
            }
            List<NearStop> loaded = load();
            cache = loaded;
            cacheAt = System.currentTimeMillis();
            return loaded;
        }
    }

    /** Called by RouteDataStore after a catalogue update / clear. */
    public static void invalidate() {
        cache = null;
        cacheAt = 0L;
    }

    /** Local snapshot first, live official API as fallback. */
    private static JSONObject datasetJson(String file, String url) throws Exception {
        return datasetJson(file, new String[]{url});
    }

    private static JSONObject datasetJson(String file, String... urls) throws Exception {
        return RouteDataStore.catalogueJson(file, urls);
    }

    /** Haversine distance in metres. */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        final double earth = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * earth * Math.asin(Math.sqrt(a));
    }

    /** Turns nearby stops into the same StopOption used by the route-mode list. */
    public static List<StopOption> nearby(double lat, double lng, int radiusMeters, int limit) {
        List<NearStop> candidates = new ArrayList<>();
        for (NearStop s : all()) {
            double distance = distanceMeters(lat, lng, s.lat, s.lng);
            if (distance <= radiusMeters) {
                candidates.add(s);
            }
        }
        final double originLat = lat;
        final double originLng = lng;
        Collections.sort(candidates, new Comparator<NearStop>() {
            @Override
            public int compare(NearStop a, NearStop b) {
                return Double.compare(
                        distanceMeters(originLat, originLng, a.lat, a.lng),
                        distanceMeters(originLat, originLng, b.lat, b.lng));
            }
        });

        List<StopOption> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (NearStop s : candidates) {
            String key = s.company + "|" + s.stopId;
            if (!seen.add(key)) {
                continue;
            }
            int metres = (int) Math.round(distanceMeters(lat, lng, s.lat, s.lng));
            String note = s.note.isEmpty() ? (metres + " m") : (s.note + " · " + metres + " m");
            out.add(new StopOption(s.company, "附近", EtaRepository.NEARBY, metres,
                    s.stopId, s.name, 1, s.ref, note));
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ loading

    private static List<NearStop> load() {
        List<NearStop> out = new ArrayList<>();
        safeList("KMB", new Callable<List<NearStop>>() {
            @Override
            public List<NearStop> call() throws Exception {
                return parseBusStopList(RouteDataStore.KMB_STOPS,
                        new String[]{KMB_BASE + "/stop", KMB_BASE + "/stop/"},
                        EtaRepository.KMB, "");
            }
        }, out);
        safeList("CTB", new Callable<List<NearStop>>() {
            @Override
            public List<NearStop> call() throws Exception {
                // Citybus v2 has NO full stop-list endpoint. Only the
                // incremental cache built from /stop/{stop_id} is usable.
                return parseBusStopList(RouteDataStore.CTB_STOPS,
                        new String[0], EtaRepository.CTB, "");
            }
        }, out);
        safeList("NLB", new Callable<List<NearStop>>() {
            @Override
            public List<NearStop> call() throws Exception {
                return nlbStops();
            }
        }, out);
        safeList("GMB", new Callable<List<NearStop>>() {
            @Override
            public List<NearStop> call() throws Exception {
                return gmbStops();
            }
        }, out);
        safeList("MTR CSV", new Callable<List<NearStop>>() {
            @Override
            public List<NearStop> call() throws Exception {
                return mtrCsvStops();
            }
        }, out);
        return dedupe(out);
    }

    private static void safeList(String label, Callable<List<NearStop>> task,
                                 List<NearStop> out) {
        try {
            List<NearStop> part = task.call();
            if (part != null) {
                out.addAll(part);
            }
        } catch (Exception ignored) {
            // One provider being down must not hide the others.
        }
    }

    private static List<NearStop> parseBusStopList(String file, String[] urls,
                                                   String company,
                                                   String defaultRef) throws Exception {
        JSONArray data = datasetJson(file, urls).optJSONArray("data");
        List<NearStop> out = new ArrayList<>();
        if (data == null) {
            return out;
        }
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String id = HttpJson.str(o, "stop");
            String name = HttpJson.firstNonEmpty(
                    HttpJson.str(o, "name_tc"),
                    HttpJson.str(o, "name_en"),
                    HttpJson.str(o, "name_sc"));
            double lat = number(o, "lat", "latitude");
            double lng = number(o, "long", "longitude");
            if (id.isEmpty() || lat == 0.0 || lng == 0.0) {
                continue;
            }
            out.add(new NearStop(company, id, name, defaultRef, "", lat, lng));
        }
        return out;
    }

    private static List<NearStop> nlbStops() throws Exception {
        JSONObject snapshot = RouteDataStore.readJson(RouteDataStore.NLB_STOPS);
        if (snapshot != null) {
            JSONArray merged = snapshot.optJSONArray("stops");
            List<NearStop> local = new ArrayList<>();
            if (merged != null) {
                for (int i = 0; i < merged.length(); i++) {
                    JSONObject s = merged.optJSONObject(i);
                    if (s == null) {
                        continue;
                    }
                    String id = HttpJson.str(s, "stopId");
                    String name = HttpJson.firstNonEmpty(
                            HttpJson.str(s, "stopName_c"),
                            HttpJson.str(s, "stopName_e"));
                    double lat = number(s, "latitude", "lat");
                    double lng = number(s, "longitude", "long");
                    if (id.isEmpty() || lat == 0.0 || lng == 0.0) {
                        continue;
                    }
                    local.add(new NearStop(EtaRepository.NLB, id, name,
                            HttpJson.str(s, "routeId"),
                            HttpJson.str(s, "routeNo"), lat, lng));
                }
            }
            if (!local.isEmpty()) {
                return local;
            }
        }
        JSONObject root = datasetJson(RouteDataStore.NLB_ROUTES,
                NLB_BASE + "/route.php?action=list");
        JSONArray routes = root.optJSONArray("routes");
        List<NearStop> out = new ArrayList<>();
        if (routes == null || routes.length() == 0) {
            return out;
        }
        final List<String[]> jobs = new ArrayList<>();
        for (int i = 0; i < routes.length(); i++) {
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
        try {
            List<Callable<List<NearStop>>> tasks = new ArrayList<>();
            for (final String[] job : jobs) {
                tasks.add(new Callable<List<NearStop>>() {
                    @Override
                    public List<NearStop> call() throws Exception {
                        String url = NLB_BASE + "/stop.php?action=list&routeId="
                                + HttpJson.seg(job[0]);
                        JSONArray stops = HttpJson.getObject(url).optJSONArray("stops");
                        List<NearStop> part = new ArrayList<>();
                        if (stops == null) {
                            return part;
                        }
                        for (int j = 0; j < stops.length(); j++) {
                            JSONObject s = stops.optJSONObject(j);
                            if (s == null) {
                                continue;
                            }
                            String stopId = HttpJson.str(s, "stopId");
                            String name = HttpJson.firstNonEmpty(
                                    HttpJson.str(s, "stopName_c"),
                                    HttpJson.str(s, "stopName_e"));
                            double lat = number(s, "latitude", "lat");
                            double lng = number(s, "longitude", "long");
                            if (stopId.isEmpty() || lat == 0.0 || lng == 0.0) {
                                continue;
                            }
                            part.add(new NearStop(EtaRepository.NLB, stopId, name,
                                    job[0], job[1], lat, lng));
                        }
                        return part;
                    }
                });
            }
            List<Future<List<NearStop>>> futures =
                    pool.invokeAll(tasks, 25L, TimeUnit.SECONDS);
            for (Future<List<NearStop>> f : futures) {
                if (f.isCancelled()) {
                    continue;
                }
                try {
                    out.addAll(f.get());
                } catch (Exception ignored) {
                    // skip this route
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return out;
    }

    private static List<NearStop> gmbStops() throws Exception {
        JSONObject root = datasetJson(RouteDataStore.GMB_STOPS, GMB_BASE + "/stop");
        JSONArray data = root.optJSONArray("data");
        if (data == null) {
            JSONObject wrapper = root.optJSONObject("data");
            if (wrapper != null) {
                data = wrapper.optJSONArray("stops");
            }
        }
        List<NearStop> out = new ArrayList<>();
        if (data == null) {
            // The documented Stop API is /stop/{stop_id}; no bulk list is guaranteed.
            return out;
        }
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String id = HttpJson.firstNonEmpty(
                    HttpJson.str(o, "stop_id"), HttpJson.str(o, "stop"));
            String name = HttpJson.firstNonEmpty(
                    HttpJson.str(o, "name_tc"), HttpJson.str(o, "name_en"));
            JSONObject coordinates = o.optJSONObject("coordinates");
            JSONObject wgs = coordinates == null ? null : coordinates.optJSONObject("wgs84");
            if (wgs == null) {
                wgs = o.optJSONObject("wgs84");
            }
            double lat = wgs == null ? number(o, "latitude", "lat")
                    : wgs.optDouble("latitude", 0.0);
            double lng = wgs == null ? number(o, "longitude", "long")
                    : wgs.optDouble("longitude", 0.0);
            if (id.isEmpty() || lat == 0.0 || lng == 0.0) {
                continue;
            }
            out.add(new NearStop(EtaRepository.GMB, id, name, "", "綠小巴", lat, lng));
        }
        return out;
    }

    private static List<NearStop> mtrCsvStops() {
        List<NearStop> out = new ArrayList<>();
        for (NearbySeed seed : MtrCatalog.nearbySeeds()) {
            if (seed.lat == 0.0 || seed.lng == 0.0 || seed.id.isEmpty()) {
                continue;
            }
            out.add(new NearStop(seed.company, seed.id, seed.name, seed.ref,
                    seed.note, seed.lat, seed.lng));
        }
        return out;
    }

    private static double number(JSONObject o, String... keys) {
        for (String key : keys) {
            String raw = HttpJson.str(o, key);
            if (raw.isEmpty()) {
                continue;
            }
            try {
                double v = Double.parseDouble(raw);
                if (v != 0.0) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
                // try next alias
            }
        }
        return 0.0;
    }

    private static List<NearStop> dedupe(List<NearStop> input) {
        List<NearStop> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (NearStop s : input) {
            if (seen.add(s.company + "|" + s.stopId)) {
                out.add(s);
            }
        }
        return out;
    }
}
