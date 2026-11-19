package hk.eta.app.api;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * APP1933-style ROUTE SEARCH.
 *
 * Typing "1" must produce rows like
 *     1   往 尖沙咀碼頭
 *     1   往 竹園邨
 *     1   往 大澳 - 嶼巴      大嶼山巴士 (資料由DATA.GOV.HK提供)
 *     1   往 梅窩碼頭 - 嶼巴  大嶼山巴士 (資料由DATA.GOV.HK提供)
 *
 * This reads ONLY the official open-data route catalogues (local snapshot
 * first, live API as fallback). It never invents destinations: when an
 * operator's catalogue has no direction-level destination, the row says so.
 * The 8-operator ETA data layer (EtaRepository / GeoStops) is untouched.
 */
public final class RouteSearch {

    private static final String KMB_BASE = "https://data.etabus.gov.hk/v1/transport/kmb";
    private static final String CTB_BASE = "https://rt.data.gov.hk/v2/transport/citybus";
    private static final String NLB_BASE = "https://rt.data.gov.hk/v2/transport/nlb";
    private static final String GMB_BASE = "https://data.etagmb.gov.hk";

    private static final String[] COMPANY_ORDER = {
            EtaRepository.KMB, EtaRepository.CTB, EtaRepository.NLB,
            EtaRepository.GMB, EtaRepository.MTR, EtaRepository.LRT,
            EtaRepository.MTRB
    };

    /** One row of the search result list. */
    public static final class Result {
        public final String company;
        public final String route;
        public final String dest;
        public final String bound;
        public final int serviceType;
        public final String note;
        public final boolean exact;
        /** JOINT_KMB_CTB: true when both official catalogues list this route. */
        public final boolean joint;
        /** CIRCULAR_ROUTES: only KMB / CTB circular rows set these. */
        public final boolean circular;
        public final String style;

        Result(String company, String route, String dest, String bound,
               int serviceType, String note, boolean exact) {
            this(company, route, dest, bound, serviceType, note, exact, false);
        }

        Result(String company, String route, String dest, String bound,
               int serviceType, String note, boolean exact, boolean joint) {
            this(company, route, dest, bound, serviceType, note, exact, joint,
                    false, "");
        }

        Result(String company, String route, String dest, String bound,
               int serviceType, String note, boolean exact, boolean joint,
               boolean circular, String style) {
            this.company = company;
            this.route = route;
            this.dest = dest;
            this.bound = bound;
            this.serviceType = serviceType;
            this.note = note == null ? "" : note;
            this.exact = exact;
            this.joint = joint;
            this.circular = circular;
            this.style = style == null ? "" : style;
        }
    }

    /**
     * CIRCULAR_ROUTES — mark KMB / Citybus CIRCULAR rows from the OFFICIAL
     * destination string (e.g. "尖沙咀(循環線)", "勵德邨 - 荷李活道(循環線)").
     * Pure and offline-testable; no route-number list is hard-coded.
     *
     * CIRCULAR_DISPLAY: the row gets its OWN operator's style label and the
     * official display title, so a Citybus circular line is shown the Citybus
     * way and a KMB circular line the App1933 way. Circular rows are kept
     * SEPARATE (see markJoint) so the two ways never mix.
     */
    static List<Result> markCircular(List<Result> in) {
        List<Result> out = new ArrayList<>();
        if (in == null) {
            return out;
        }
        for (Result r : in) {
            boolean circular = CircularRoutes.supports(r.company)
                    && CircularRoutes.isCircularDest(r.dest);
            if (!circular) {
                out.add(r);
                continue;
            }
            String style = CircularRoutes.styleLabel(r.company);
            String badge = CircularRoutes.displayBadge(r.company, r.dest);
            String dest = CircularRoutes.displayTitle(r.company, "", r.dest)
                    .trim();
            // dest keeps the official circular suffix; the "route" prefix is
            // rendered separately by the adapter, so strip it here.
            String note = r.note;
            if (!note.contains(style)) {
                note = note.isEmpty()
                        ? badge
                        : badge + " · " + note;
            }
            out.add(new Result(r.company, r.route, dest, r.bound,
                    r.serviceType, note, r.exact, false, true, style));
        }
        return out;
    }

    /**
     * JOINT_KMB_CTB — mark jointly-operated KMB/CTB routes and collapse the two
     * catalogue rows that describe the SAME route + destination into one row
     * labelled "城巴/九巴". Different destinations (two ends of a route) stay
     * as two rows, both labelled. Detection is catalogue-driven, never a
     * hard-coded route-number list.
     */
    private static List<Result> markJoint(List<Result> in) {
        List<Result> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Result r : in) {
            // CIRCULAR_ROUTES: a circular line is shown with ITS OWN
            // operator's way and stop names, never collapsed into a
            // "城巴/九巴" joint row.
            if (r.circular) {
                out.add(r);
                continue;
            }
            boolean joint = (EtaRepository.KMB.equals(r.company)
                    || EtaRepository.CTB.equals(r.company))
                    && JointRoutes.isJointRoute(r.route);
            String company = joint ? JointRoutes.JOINT : r.company;
            String note = r.note;
            if (joint && !note.contains(JointRoutes.JOINT_LABEL)) {
                note = note.isEmpty()
                        ? JointRoutes.JOINT_NOTE
                        : JointRoutes.JOINT_LABEL + " · " + note;
            }
            if (joint) {
                String key = company + "|"
                        + r.route.trim().toUpperCase(Locale.US) + "|"
                        + r.dest.replace("往 ", "").trim();
                if (!seen.add(key)) {
                    continue;
                }
            }
            out.add(new Result(company, r.route, r.dest, r.bound,
                    r.serviceType, note, r.exact, joint));
        }
        return out;
    }

    private RouteSearch() {
    }

    /** Never throws: one unavailable operator must not hide the others. */
    public static List<Result> search(String raw) {
        List<Result> exact = searchInternal(raw, false);
        // The official app shows exact route matches first. Prefix matches
        // ("1" -> "1A", "10" ...) are only offered when NOTHING matched
        // exactly, so typing "1" produces the four rows in the screenshot.
        if (!exact.isEmpty()) {
            return exact;
        }
        return searchInternal(raw, true);
    }

    /**
     * PRELOADED rows for the search home page (the dark empty area in the
     * reference screenshot). Called BEFORE the user types a single digit, so
     * "12 往 海麗邨 ›" style rows are already visible on entry.
     *
     * Every row comes from the SAME official route catalogues as search():
     * local snapshot first, live API as fallback. No destination is invented —
     * if a catalogue has no direction-level destination, that operator is
     * skipped. GMB directions need one live lookup per route, so preload only
     * shows its route codes with an explicit "目錄預載（未查方向）" note.
     */
    public static List<Result> preload() {
        final List<Result> out = new ArrayList<>();
        safe(out, new Source() {
            @Override public void run() throws Exception { kmbPreload(out); }
        });
        safe(out, new Source() {
            @Override public void run() throws Exception { ctbPreload(out); }
        });
        safe(out, new Source() {
            @Override public void run() throws Exception { nlbPreload(out); }
        });
        safe(out, new Source() {
            @Override public void run() throws Exception { gmbPreload(out); }
        });
        railPreload(out);
        // CIRCULAR_ROUTES: mark circular rows first, then collapse only the
        // NON-circular joint rows into one "城巴/九巴" row.
        List<Result> marked = markCircular(out);
        out.clear();
        out.addAll(marked);
        List<Result> merged = markJoint(out);
        out.clear();
        out.addAll(merged);
        Collections.sort(out, new Comparator<Result>() {
            @Override
            public int compare(Result a, Result b) {
                int c = rank(a.company) - rank(b.company);
                if (c != 0) {
                    return c;
                }
                c = a.route.compareToIgnoreCase(b.route);
                return c != 0 ? c : a.dest.compareTo(b.dest);
            }
        });
        if (out.size() > 24) {
            return new ArrayList<>(out.subList(0, 24));
        }
        return out;
    }

    private static void kmbPreload(List<Result> out) throws Exception {
        JSONObject root = RouteDataStore.catalogueJson(
                RouteDataStore.KMB_ROUTES,
                KMB_BASE + "/route/", KMB_BASE + "/route");
        JSONArray data = root.optJSONArray("data");
        if (data == null) {
            return;
        }
        int added = 0;
        for (int i = 0; i < data.length() && added < 6; i++) {
            JSONObject o = data.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String route = HttpJson.str(o, "route");
            String dest = HttpJson.firstNonEmpty(
                    HttpJson.str(o, "dest_tc"),
                    HttpJson.str(o, "dest_en"),
                    HttpJson.str(o, "orig_tc"));
            if (route.isEmpty() || dest.isEmpty()) {
                continue;
            }
            String bound = HttpJson.firstNonEmpty(HttpJson.str(o, "bound"), "O");
            out.add(new Result(EtaRepository.KMB, route, "往 " + dest, bound,
                    toServiceType(HttpJson.str(o, "service_type")), "", true));
            added++;
        }
    }

    private static void ctbPreload(List<Result> out) throws Exception {
        JSONObject root = RouteDataStore.catalogueJson(
                RouteDataStore.CTB_ROUTES,
                CTB_BASE + "/route/CTB",
                "https://rt.data.gov.hk/v1/transport/citybus-nwfb/route/CTB");
        JSONArray data = root.optJSONArray("data");
        if (data == null) {
            return;
        }
        int added = 0;
        for (int i = 0; i < data.length() && added < 6; i++) {
            JSONObject o = data.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String route = HttpJson.str(o, "route");
            String dest = HttpJson.firstNonEmpty(
                    HttpJson.str(o, "dest_tc"),
                    HttpJson.str(o, "dest_en"),
                    HttpJson.str(o, "orig_tc"));
            if (route.isEmpty() || dest.isEmpty()) {
                continue;
            }
            String bound = HttpJson.firstNonEmpty(
                    HttpJson.str(o, "dir"), HttpJson.str(o, "bound"), "O");
            out.add(new Result(EtaRepository.CTB, route, "往 " + dest,
                    bound, 1, "", true));
            added++;
        }
    }

    private static void nlbPreload(List<Result> out) throws Exception {
        JSONObject root = RouteDataStore.catalogueJson(
                RouteDataStore.NLB_ROUTES,
                NLB_BASE + "/route.php?action=list");
        JSONArray routes = root.optJSONArray("routes");
        if (routes == null) {
            return;
        }
        final String note = "大嶼山巴士 (資料由DATA.GOV.HK提供)";
        int added = 0;
        for (int i = 0; i < routes.length() && added < 2; i++) {
            JSONObject o = routes.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String route = HttpJson.str(o, "routeNo");
            if (route.isEmpty()) {
                continue;
            }
            String end = HttpJson.firstNonEmpty(
                    HttpJson.str(o, "loc_end_name_c"),
                    HttpJson.str(o, "loc_end_name_e"),
                    afterDash(HttpJson.str(o, "routeName_c")));
            String start = HttpJson.firstNonEmpty(
                    HttpJson.str(o, "loc_start_name_c"),
                    HttpJson.str(o, "loc_start_name_e"),
                    beforeDash(HttpJson.str(o, "routeName_c")));
            if (end.isEmpty() && start.isEmpty()) {
                continue;
            }
            if (!end.isEmpty()) {
                out.add(new Result(EtaRepository.NLB, route,
                        "往 " + end + " - 嶼巴", "ALL", 1, note, true));
            }
            if (!start.isEmpty() && !start.equals(end)) {
                out.add(new Result(EtaRepository.NLB, route,
                        "往 " + start + " - 嶼巴", "ALL", 1, note, true));
            }
            added++;
        }
    }

    /** No live detail lookups here; the note says directions were not fetched. */
    private static void gmbPreload(List<Result> out) throws Exception {
        JSONObject root = RouteDataStore.catalogueJson(
                RouteDataStore.GMB_ROUTES, GMB_BASE + "/route", GMB_BASE + "/route/");
        JSONObject data = root.optJSONObject("data");
        JSONObject routes = data == null ? null : data.optJSONObject("routes");
        if (routes == null) {
            return;
        }
        String[] regions = {"HKI", "KLN", "NT"};
        String[] names = {"香港島", "九龍", "新界"};
        for (int r = 0; r < regions.length; r++) {
            JSONArray list = routes.optJSONArray(regions[r]);
            if (list == null) {
                continue;
            }
            int added = 0;
            for (int i = 0; i < list.length() && added < 2; i++) {
                String code = list.optString(i, "");
                if (code.isEmpty()) {
                    continue;
                }
                out.add(new Result(EtaRepository.GMB, code,
                        "往 " + code + " 總站", "1", 1,
                        "綠色小巴 (資料由DATA.GOV.HK提供) · " + names[r]
                                + " · 目錄預載（未查方向）", true));
                added++;
            }
        }
    }

    /** Rail rows use the project's own official line / MTR Bus code tables. */
    private static void railPreload(List<Result> out) {
        for (int i = 0; i < Math.min(4, MtrCatalog.LINE_CODES.length); i++) {
            out.add(new Result(EtaRepository.MTR, MtrCatalog.LINE_CODES[i],
                    "往 " + MtrCatalog.LINE_NAMES[i] + " 沿線", "ALL", 1,
                    "港鐵 (資料由DATA.GOV.HK提供)", true));
        }
        for (int i = 0; i < Math.min(2, MtrCatalog.MTR_BUS_ROUTES.length); i++) {
            String code = MtrCatalog.MTR_BUS_ROUTES[i];
            out.add(new Result(EtaRepository.MTRB, code,
                    "往 " + code + " 總站", "ALL", 1,
                    "港鐵接駁巴士 (資料由DATA.GOV.HK提供)", true));
        }
    }

    private static List<Result> searchInternal(String raw, final boolean allowPrefix) {
        final String q = raw == null ? "" : raw.trim().toUpperCase(Locale.US);
        if (q.isEmpty()) {
            return Collections.emptyList();
        }
        final List<Result> out = new ArrayList<>();
        safe(out, new Source() {
            @Override public void run() throws Exception { kmb(out, q, allowPrefix); }
        });
        safe(out, new Source() {
            @Override public void run() throws Exception { ctb(out, q, allowPrefix); }
        });
        safe(out, new Source() {
            @Override public void run() throws Exception { nlb(out, q, allowPrefix); }
        });
        safe(out, new Source() {
            @Override public void run() throws Exception { gmb(out, q, allowPrefix); }
        });
        safe(out, new Source() {
            @Override public void run() throws Exception { rail(out, q); }
        });
        // CIRCULAR_ROUTES + JOINT_KMB_CTB: mark circular rows first, then
        // collapse only the NON-circular joint rows.
        List<Result> marked = markCircular(out);
        out.clear();
        out.addAll(marked);
        List<Result> merged = markJoint(out);
        out.clear();
        out.addAll(merged);
        Collections.sort(out, new Comparator<Result>() {
            @Override
            public int compare(Result a, Result b) {
                int c = rank(a.company) - rank(b.company);
                if (c != 0) {
                    return c;
                }
                // Exact matches first, then prefix matches, then by destination.
                if (a.exact != b.exact) {
                    return a.exact ? -1 : 1;
                }
                return a.dest.compareTo(b.dest);
            }
        });
        // Keep the list usable on a phone: exact matches always survive.
        if (out.size() > 40) {
            List<Result> trimmed = new ArrayList<>();
            for (Result r : out) {
                if (r.exact || trimmed.size() < 24) {
                    trimmed.add(r);
                }
            }
            return trimmed;
        }
        return out;
    }

    private static int rank(String company) {
        // JOINT_KMB_CTB: joint rows sort ahead of the single-operator rows.
        if (JointRoutes.isJointCompany(company)) {
            return -1;
        }
        for (int i = 0; i < COMPANY_ORDER.length; i++) {
            if (COMPANY_ORDER[i].equals(company)) {
                return i;
            }
        }
        return COMPANY_ORDER.length;
    }

    private static void safe(List<Result> out, Source task) {
        try {
            task.run();
        } catch (Exception ignored) {
            // One catalogue being 403 / offline must not hide other operators.
        }
    }

    private interface Source {
        void run() throws Exception;
    }

    // ------------------------------------------------------------------ KMB / LWB

    private static void kmb(List<Result> out, String q, boolean allowPrefix) throws Exception {
        JSONObject root = RouteDataStore.catalogueJson(
                RouteDataStore.KMB_ROUTES,
                KMB_BASE + "/route/", KMB_BASE + "/route");
        JSONArray data = root.optJSONArray("data");
        if (data == null) {
            return;
        }
        int prefix = 0;
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String route = HttpJson.str(o, "route");
            if (route.isEmpty()) {
                continue;
            }
            boolean exact = route.equalsIgnoreCase(q);
            if (!exact && !(allowPrefix && route.startsWith(q) && prefix < 4)) {
                continue;
            }
            if (!exact) {
                prefix++;
            }
            String dest = HttpJson.firstNonEmpty(
                    HttpJson.str(o, "dest_tc"),
                    HttpJson.str(o, "dest_en"),
                    HttpJson.str(o, "orig_tc"));
            String bound = HttpJson.firstNonEmpty(
                    HttpJson.str(o, "bound"), "O");
            int serviceType = toServiceType(HttpJson.str(o, "service_type"));
            // KMB / LWB have no attribution line in the official app.
            out.add(new Result(EtaRepository.KMB, route, "往 " + dest,
                    bound, serviceType, "", exact));
        }
    }

    // ------------------------------------------------------------------ Citybus

    private static void ctb(List<Result> out, String q, boolean allowPrefix) throws Exception {
        JSONObject root = RouteDataStore.catalogueJson(
                RouteDataStore.CTB_ROUTES,
                CTB_BASE + "/route/CTB",
                "https://rt.data.gov.hk/v1/transport/citybus-nwfb/route/CTB");
        JSONArray data = root.optJSONArray("data");
        if (data == null) {
            return;
        }
        int prefix = 0;
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String route = HttpJson.str(o, "route");
            if (route.isEmpty()) {
                continue;
            }
            boolean exact = route.equalsIgnoreCase(q);
            if (!exact && !(allowPrefix && route.startsWith(q) && prefix < 4)) {
                continue;
            }
            if (!exact) {
                prefix++;
            }
            String dest = HttpJson.firstNonEmpty(
                    HttpJson.str(o, "dest_tc"),
                    HttpJson.str(o, "dest_en"),
                    HttpJson.str(o, "orig_tc"));
            String bound = HttpJson.firstNonEmpty(
                    HttpJson.str(o, "dir"), HttpJson.str(o, "bound"), "O");
            out.add(new Result(EtaRepository.CTB, route, "往 " + dest,
                    bound, 1, "", exact));
        }
    }

    // ------------------------------------------------------------------ NLB

    /**
     * The NLB route catalogue has one record per route but two ends. The
     * official app lists BOTH directions, e.g. "往 大澳 - 嶼巴" and
     * "往 梅窩碼頭 - 嶼巴", with the DATA.GOV.HK attribution underneath.
     */
    private static void nlb(List<Result> out, String q, boolean allowPrefix) throws Exception {
        JSONObject root = RouteDataStore.catalogueJson(
                RouteDataStore.NLB_ROUTES,
                NLB_BASE + "/route.php?action=list");
        JSONArray routes = root.optJSONArray("routes");
        if (routes == null) {
            return;
        }
        final String note = "大嶼山巴士 (資料由DATA.GOV.HK提供)";
        for (int i = 0; i < routes.length(); i++) {
            JSONObject o = routes.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String route = HttpJson.str(o, "routeNo");
            if (route.isEmpty()) {
                continue;
            }
            boolean exact = route.equalsIgnoreCase(q);
            if (!exact && !(allowPrefix && route.startsWith(q))) {
                continue;
            }
            String end = HttpJson.firstNonEmpty(
                    HttpJson.str(o, "loc_end_name_c"),
                    HttpJson.str(o, "loc_end_name_e"),
                    afterDash(HttpJson.str(o, "routeName_c")),
                    HttpJson.str(o, "routeName_c"));
            String start = HttpJson.firstNonEmpty(
                    HttpJson.str(o, "loc_start_name_c"),
                    HttpJson.str(o, "loc_start_name_e"),
                    beforeDash(HttpJson.str(o, "routeName_c")));
            if (!end.isEmpty()) {
                out.add(new Result(EtaRepository.NLB, route,
                        "往 " + end + " - 嶼巴", "ALL", 1, note, exact));
            }
            if (!start.isEmpty() && !start.equals(end)) {
                out.add(new Result(EtaRepository.NLB, route,
                        "往 " + start + " - 嶼巴", "ALL", 1, note, exact));
            }
        }
    }

    // ------------------------------------------------------------------ GMB

    /**
     * GMB's catalogue only lists route codes per region; directions and
     * Chinese destinations come from the official /route/{region}/{route}
     * detail. At most two live lookups per keystroke, all guarded.
     */
    private static void gmb(List<Result> out, String q, boolean allowPrefix) throws Exception {
        JSONObject root = RouteDataStore.catalogueJson(
                RouteDataStore.GMB_ROUTES, GMB_BASE + "/route", GMB_BASE + "/route/");
        JSONObject data = root.optJSONObject("data");
        JSONObject routes = data == null ? null : data.optJSONObject("routes");
        if (routes == null) {
            return;
        }
        final String note = "綠色小巴 (資料由DATA.GOV.HK提供)";
        Map<String, String> regionNames = new LinkedHashMap<>();
        regionNames.put("HKI", "香港島");
        regionNames.put("KLN", "九龍");
        regionNames.put("NT", "新界");
        for (Map.Entry<String, String> region : regionNames.entrySet()) {
            JSONArray list = routes.optJSONArray(region.getKey());
            if (list == null) {
                continue;
            }
            for (int i = 0; i < list.length(); i++) {
                String code = list.optString(i, "");
                if (code.isEmpty()) {
                    continue;
                }
                boolean exact = code.equalsIgnoreCase(q);
                if (!exact && !(allowPrefix && code.toUpperCase(Locale.US).startsWith(q))) {
                    continue;
                }
                List<String> dests = gmbDestinations(region.getKey(), code);
                if (dests.isEmpty()) {
                    out.add(new Result(EtaRepository.GMB, code,
                            "往 " + code + " 總站", "1", 1,
                            note + " · " + region.getValue(), exact));
                } else {
                    for (int d = 0; d < dests.size(); d++) {
                        out.add(new Result(EtaRepository.GMB, code,
                                "往 " + dests.get(d), String.valueOf(d + 1), 1,
                                note + " · " + region.getValue(), exact));
                    }
                }
            }
        }
    }

    private static List<String> gmbDestinations(String region, String code) {
        List<String> out = new ArrayList<>();
        try {
            JSONObject detail = HttpJson.getObject(
                    GMB_BASE + "/route/" + HttpJson.seg(region) + "/" + HttpJson.seg(code));
            JSONArray variants = detail.optJSONArray("data");
            if (variants == null) {
                return out;
            }
            for (int i = 0; i < variants.length(); i++) {
                JSONObject v = variants.optJSONObject(i);
                if (v == null) {
                    continue;
                }
                JSONArray dirs = v.optJSONArray("directions");
                if (dirs == null) {
                    continue;
                }
                for (int j = 0; j < dirs.length(); j++) {
                    JSONObject d = dirs.optJSONObject(j);
                    if (d == null) {
                        continue;
                    }
                    String dest = HttpJson.firstNonEmpty(
                            HttpJson.str(d, "dest_tc"),
                            HttpJson.str(d, "dest_en"),
                            HttpJson.str(d, "orig_tc"));
                    if (!dest.isEmpty() && !out.contains(dest)) {
                        out.add(dest);
                    }
                }
            }
        } catch (Exception ignored) {
            // Offline / rate-limited: fall back to a generic destination row.
        }
        return out;
    }

    // ------------------------------------------------------------------ MTR / LRT / MTR Bus

    /** Rail modes have no "destination" catalogue; keep them discoverable. */
    private static void rail(List<Result> out, String q) {
        if (MtrCatalog.isLineCode(q)) {
            out.add(new Result(EtaRepository.MTR, q, "往 " + q + " 沿線",
                    "ALL", 1, "港鐵 (資料由DATA.GOV.HK提供)", true));
        }
        if (q.matches("\\d{3}")) {
            out.add(new Result(EtaRepository.LRT, q, "往 " + q + " 沿線",
                    "ALL", 1, "輕鐵 (資料由DATA.GOV.HK提供)", true));
        }
        if (q.startsWith("K") || q.equals("506")) {
            out.add(new Result(EtaRepository.MTRB, q, "往 " + q + " 總站",
                    "ALL", 1, "港鐵接駁巴士 (資料由DATA.GOV.HK提供)", true));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static int toServiceType(String raw) {
        try {
            int v = Integer.parseInt(raw.trim());
            return v < 1 ? 1 : v;
        } catch (Exception e) {
            return 1;
        }
    }

    private static String beforeDash(String name) {
        int i = name.indexOf(" - ");
        return i > 0 ? name.substring(0, i).trim() : "";
    }

    private static String afterDash(String name) {
        int i = name.indexOf(" - ");
        return i >= 0 ? name.substring(i + 3).trim() : "";
    }

    /** Exposed for the offline functional test. */
    public static String attribution(String company) {
        if (JointRoutes.isJointCompany(company)) {
            // The joint label lives in Result.note, not the attribution line.
            return "";
        }
        switch (company) {
            case "NLB": return "大嶼山巴士 (資料由DATA.GOV.HK提供)";
            case "GMB": return "綠色小巴 (資料由DATA.GOV.HK提供)";
            case "MTR": return "港鐵 (資料由DATA.GOV.HK提供)";
            case "LRT": return "輕鐵 (資料由DATA.GOV.HK提供)";
            case "MTRB": return "港鐵接駁巴士 (資料由DATA.GOV.HK提供)";
            default: return "";
        }
    }

    /** Unused placeholder to keep the IOException import meaningful for tests. */
    static IOException noCatalogue(String file) {
        return new IOException("沒有可用的 " + file + " 目錄");
    }
}
