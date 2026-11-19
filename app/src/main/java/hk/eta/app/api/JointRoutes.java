package hk.eta.app.api;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import hk.eta.app.api.EtaModels.EtaItem;
import hk.eta.app.api.EtaModels.StopOption;

/**
 * JOINT_KMB_CTB — data-driven handling of jointly-operated bus routes.
 *
 * A route is treated as joint ONLY when the SAME route number appears in BOTH
 * official route catalogues (KMB / LWB and Citybus). No hard-coded route-number
 * list is invented: the catalogues themselves are the source of truth, so new
 * or withdrawn joint services are picked up after the next catalogue update.
 *
 * Two distinct things are displayed:
 *   1. the ROUTE label "城巴/九巴" (card meta / search row / detail header)
 *   2. the TRIP label "該班次：城巴" or "該班次：九巴" (each of the 3 arrivals)
 *
 * The 8-operator data layer is otherwise untouched; this class only adds a
 * best-effort partner ETA lookup for KMB/CTB stops that serve a joint route.
 */
public final class JointRoutes {

    /** UI-level marker. NOT added to EtaRepository.OPERATORS / the spinner. */
    public static final String JOINT = "JOINT";
    public static final String JOINT_LABEL = "城巴/九巴";
    public static final String JOINT_NOTE = "聯營路線 · 城巴/九巴";
    public static final String TRIP_PREFIX = "該班次：";

    private static final String KMB_BASE = "https://data.etabus.gov.hk/v1/transport/kmb";
    private static final String CTB_BASE = "https://rt.data.gov.hk/v2/transport/citybus";

    private static volatile Set<String> kmbRoutes;
    private static volatile Set<String> ctbRoutes;
    private static final Map<String, Boolean> DECISION_CACHE = new ConcurrentHashMap<>();

    private JointRoutes() {
    }

    public static boolean isJointCompany(String company) {
        return JOINT.equals(company);
    }

    public static boolean isKmbOrCtb(String company) {
        return EtaRepository.KMB.equals(company) || EtaRepository.CTB.equals(company);
    }

    /**
     * Pure, offline-testable predicate. Both sets must be present; a missing
     * catalogue must NEVER be guessed as "joint" or "not joint".
     */
    public static boolean bothContain(Set<String> kmb, Set<String> ctb, String route) {
        if (kmb == null || ctb == null || route == null) {
            return false;
        }
        String r = route.trim().toUpperCase(Locale.US);
        return !r.isEmpty() && kmb.contains(r) && ctb.contains(r);
    }

    /** Never throws. A missing / 403 catalogue simply means "unknown" -> false. */
    public static boolean isJointRoute(String route) {
        if (route == null) {
            return false;
        }
        final String r = route.trim().toUpperCase(Locale.US);
        if (r.isEmpty()) {
            return false;
        }
        Boolean cached = DECISION_CACHE.get(r);
        if (cached != null) {
            return cached;
        }
        Set<String> k = kmbRoutes;
        if (k == null) {
            k = loadRoutes(
                    RouteDataStore.KMB_ROUTES,
                    KMB_BASE + "/route/", KMB_BASE + "/route");
            if (k != null) {
                kmbRoutes = k;
            }
        }
        Set<String> c = ctbRoutes;
        if (c == null) {
            c = loadRoutes(
                    RouteDataStore.CTB_ROUTES,
                    CTB_BASE + "/route/CTB",
                    "https://rt.data.gov.hk/v1/transport/citybus-nwfb/route/CTB");
            if (c != null) {
                ctbRoutes = c;
            }
        }
        if (k == null || c == null) {
            // Do NOT cache a negative decision when a catalogue was unreadable.
            return false;
        }
        boolean joint = bothContain(k, c, r);
        DECISION_CACHE.put(r, joint);
        return joint;
    }

    private static Set<String> loadRoutes(String file, String... urls) {
        try {
            JSONObject root = RouteDataStore.catalogueJson(file, urls);
            JSONArray data = root == null ? null : root.optJSONArray("data");
            if (data == null) {
                return null;
            }
            Set<String> out = new HashSet<>();
            for (int i = 0; i < data.length(); i++) {
                JSONObject o = data.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String route = HttpJson.str(o, "route");
                if (!route.isEmpty()) {
                    out.add(route.trim().toUpperCase(Locale.US));
                }
            }
            return out.isEmpty() ? null : out;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * JOINT_KMB_NAME — for a jointly-operated route, the stop SEQUENCE and
     * every displayed stop label use the KMB / LWB name and stop id.
     *
     * Both operators publish the same physical stop under DIFFERENT stop-id
     * namespaces. The KMB row is kept as-is; a CTB row is dropped when the same
     * (bound, seq) or the same Chinese stop name already has a KMB row.
     *
     * CTB_ONLY_NAME — ONLY Citybus stop names stay Citybus:
     *   * a stop that exists ONLY in the Citybus sequence keeps BOTH its
     *     official Citybus name and its Citybus stop id (needed by the ETA
     *     lookup). It is NEVER rewritten to KMB wording.
     *   * a wholly Citybus sequence (no KMB rows) is returned untouched.
     *   * a pure Citybus ROUTE never calls this method at all — see
     *     EtaViewModel.loadStops(): preferKmbStops() is gated by
     *     isJointCompany(company), so CTB-only routes keep 100% CTB naming.
     * Joint-route HOME cards follow the same rule via CTB_STOP_NAME:
     * a card whose company is CTB gets the CTB StopDisplay, while a JOINT
     * card keeps the KMB one (previous request).
     */
    public static List<StopOption> preferKmbStops(List<StopOption> stops) {
        if (stops == null) {
            return Collections.emptyList();
        }
        if (stops.isEmpty()) {
            return stops;
        }
        Set<String> kmbNames = new HashSet<>();
        boolean hasKmb = false;
        for (StopOption s : stops) {
            if (EtaRepository.KMB.equals(s.getCompany())) {
                hasKmb = true;
                String n = s.getStopName().trim();
                if (!n.isEmpty()) {
                    kmbNames.add(s.getBound() + "|" + n);
                }
            }
        }
        // CTB_ONLY_NAME: zero KMB rows => 100% Citybus route / sequence.
        // Return the ORIGINAL list so EVERY stop name and stop id is the
        // official Citybus wording. No KMB name is invented or copied.
        if (!hasKmb) {
            return stops;
        }
        List<StopOption> out = new ArrayList<>();
        Set<String> claimedSeq = new HashSet<>();
        for (StopOption s : stops) {
            if (EtaRepository.KMB.equals(s.getCompany())) {
                out.add(s);
                claimedSeq.add(s.getBound() + "|" + s.getSeq());
            }
        }
        for (StopOption s : stops) {
            if (!EtaRepository.CTB.equals(s.getCompany())) {
                continue;
            }
            String k = s.getBound() + "|" + s.getSeq();
            if (claimedSeq.contains(k)
                    || kmbNames.contains(s.getBound() + "|" + s.getStopName().trim())) {
                continue;   // same physical stop already shown with its KMB name
            }
            // CTB_ONLY_NAME: this is a Citybus-only stop. Keep its OWN
            // Citybus name (and Citybus stop id for the ETA lookup) instead
            // of copying any KMB wording. Only joint-route rows that already
            // have a KMB counterpart were dropped above.
            out.add(new StopOption(s.getCompany(), s.getRoute(), s.getBound(),
                    s.getSeq(), s.getStopId(), s.getStopName(), s.getServiceType(),
                    s.getRef(), s.getNote()));
            claimedSeq.add(k);
        }
        for (StopOption s : stops) {
            if (!EtaRepository.KMB.equals(s.getCompany())
                    && !EtaRepository.CTB.equals(s.getCompany())) {
                out.add(s);
            }
        }
        Collections.sort(out, new Comparator<StopOption>() {
            @Override
            public int compare(StopOption a, StopOption b) {
                int c = a.getBound().compareTo(b.getBound());
                if (c != 0) {
                    return c;
                }
                c = Integer.compare(a.getSeq(), b.getSeq());
                return c != 0 ? c : a.getCompany().compareTo(b.getCompany());
            }
        });
        return out;
    }

    /** Short label for ONE trip: 城巴 / 九巴 / 嶼巴 ... */
    public static String operatorLabel(String company) {
        if (EtaRepository.KMB.equals(company)) {
            return "九巴";
        }
        if (EtaRepository.CTB.equals(company)) {
            return "城巴";
        }
        if (EtaRepository.NLB.equals(company)) {
            return "嶼巴";
        }
        if (EtaRepository.GMB.equals(company)) {
            return "綠色小巴";
        }
        if (EtaRepository.MTR.equals(company)) {
            return "港鐵重鐵";
        }
        if (EtaRepository.LRT.equals(company)) {
            return "輕鐵";
        }
        if (EtaRepository.MTRB.equals(company)) {
            return "港鐵接駁巴士";
        }
        if (JOINT.equals(company)) {
            return JOINT_LABEL;
        }
        return company == null ? "" : company;
    }

    /** Label for the ROUTE (card meta): JOINT -> 城巴/九巴, KMB -> 九巴 / 龍運. */
    public static String routeCompanyLabel(String company) {
        if (JOINT.equals(company)) {
            return JOINT_LABEL;
        }
        if (EtaRepository.KMB.equals(company)) {
            return "九巴 / 龍運";
        }
        return operatorLabel(company);
    }

    /**
     * Best-effort: for a KMB / CTB stop, also query the PARTNER operator's ETA
     * for every joint route found in the primary response, using the SAME stop
     * id. Public ETA endpoints simply return an empty data array when the id
     * belongs to the other operator's namespace, so this can only ADD correctly
     * labelled trips — it never invents or re-labels an arrival.
     *
     * At most one extra request per distinct joint route per stop.
     */
    public static List<EtaItem> augment(List<EtaItem> base, StopOption stop,
                                        EtaRepository repo) {
        if (base == null || base.isEmpty() || stop == null || repo == null
                || !isKmbOrCtb(stop.getCompany())) {
            return base == null ? Collections.<EtaItem>emptyList() : base;
        }
        Set<String> jointRoutes = new LinkedHashSet<>();
        for (EtaItem e : base) {
            if (isJointRoute(e.getRoute())) {
                jointRoutes.add(e.getRoute().trim().toUpperCase(Locale.US));
            }
        }
        if (jointRoutes.isEmpty()) {
            return base;
        }
        String partner = EtaRepository.KMB.equals(stop.getCompany())
                ? EtaRepository.CTB : EtaRepository.KMB;
        List<EtaItem> out = new ArrayList<>(base);
        Set<String> seen = new HashSet<>();
        for (EtaItem e : base) {
            seen.add(dedupKey(e));
        }
        for (String route : jointRoutes) {
            try {
                List<EtaItem> extra;
                if (EtaRepository.KMB.equals(partner)) {
                    extra = repo.kmbEta(route, stop.getStopId(), stop.getServiceType());
                } else {
                    extra = repo.ctbEta(route, stop.getStopId());
                }
                for (EtaItem e : extra) {
                    if (seen.add(dedupKey(e))) {
                        out.add(e);
                    }
                }
            } catch (Exception ignored) {
                // Partner unreachable / id not in its namespace: keep primary only.
            }
        }
        if (out.size() == base.size()) {
            return base;
        }
        Collections.sort(out, new Comparator<EtaItem>() {
            @Override
            public int compare(EtaItem a, EtaItem b) {
                return Long.compare(a.getEtaMillis(), b.getEtaMillis());
            }
        });
        return out;
    }

    private static String dedupKey(EtaItem e) {
        return e.getCompany() + "|" + e.getRoute() + "|" + e.getStopId()
                + "|" + e.getEtaIso() + "|" + e.getEtaSeq();
    }

    /** Exposed for the offline functional test: reset the in-memory cache. */
    public static void resetCacheForTest() {
        DECISION_CACHE.clear();
        kmbRoutes = null;
        ctbRoutes = null;
    }
}
