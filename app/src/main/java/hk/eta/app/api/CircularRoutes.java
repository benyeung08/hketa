package hk.eta.app.api;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import hk.eta.app.api.EtaModels.StopOption;

/**
 * CIRCULAR_ROUTES — data-driven handling of KMB / Citybus CIRCULAR routes.
 *
 * Official context (KMB App1933, Citybus open data, route-stop APIs):
 *   - a circular route has ONE terminus and returns to it; official dest_tc
 *     carries a "(循環線)" / "(Circular)" suffix, e.g. "尖沙咀(循環線)",
 *     "OI MAN (CIRCULAR)", "勵德邨 - 荷李活道(循環線)".
 *   - in the route-stop data a circular route has NO inbound direction:
 *     only one bound, and the first and last stop are the same terminus.
 *
 * CIRCULAR_ONLY: the "each operator uses ITS OWN stop name" rule applies
 * ONLY to circular routes. Normal routes keep the existing rules
 * (joint -> KMB name, pure CTB -> Citybus name, pure KMB -> KMB name).
 * A Citybus circular route is shown the CITYBUS way, a KMB circular route
 * the KMB (App1933) way; they are NOT collapsed into one joint card.
 *
 * This class is pure Java and offline-testable. It never invents a stop
 * name: the audit only reports missing / suspicious / mismatched labels.
 */
public final class CircularRoutes {

    public static final String CIRCULAR_ZH = "循環線";
    public static final String KMB_STYLE = "九巴循環線";
    public static final String CTB_STYLE = "城巴循環線";

    /** Issue kinds reported by the stop-name audit. */
    public static final String OK = "OK";
    public static final String VERIFIED = "VERIFIED";
    public static final String MISSING = "MISSING";
    public static final String MISMATCH = "MISMATCH";
    public static final String FOREIGN = "FOREIGN_SUSPECT";

    private CircularRoutes() {
    }

    /** Only the two franchised bus operators have a circular "way". */
    public static boolean supports(String company) {
        return EtaRepository.KMB.equals(company) || EtaRepository.CTB.equals(company);
    }

    /**
     * Pure predicate on the OFFICIAL destination string. Both the Chinese
     * "(循環線)" and the English "(Circular)" forms are accepted. No route
     * number list is hard-coded.
     */
    public static boolean isCircularDest(String dest) {
        if (dest == null) {
            return false;
        }
        String d = dest.trim();
        if (d.isEmpty()) {
            return false;
        }
        String u = d.toUpperCase(Locale.US);
        return d.contains("循環線") || d.contains("循環")
                || u.contains("CIRCULAR") || u.contains("(CIRCLE)")
                || u.contains("LOOP");
    }

    /** True when any stop's note/destination hint carries a circular mark. */
    public static boolean anyCircularNote(List<StopOption> stops) {
        if (stops == null) {
            return false;
        }
        for (StopOption s : stops) {
            if (isCircularDest(s.getNote())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Structural, catalogue-independent check: a circular route has ONE
     * company, ONE route, ONE bound (no inbound stops) and the first / last
     * stop are the same terminus. Based on the official route-stop shape.
     */
    public static boolean isCircularSequence(List<StopOption> stops) {
        if (stops == null || stops.size() < 2) {
            return false;
        }
        String company = "";
        String route = "";
        String bound = "";
        for (StopOption s : stops) {
            if (company.isEmpty()) {
                company = s.getCompany();
                route = s.getRoute();
                bound = s.getBound();
            } else {
                if (!company.equals(s.getCompany())
                        || !route.equals(s.getRoute())
                        || !bound.equals(s.getBound())) {
                    return false;   // mixed operators / directions = not circular
                }
            }
        }
        StopOption a = stops.get(0);
        StopOption z = stops.get(stops.size() - 1);
        String an = a.getStopName().trim();
        String zn = z.getStopName().trim();
        if (!an.isEmpty() && an.equals(zn)) {
            return true;
        }
        return !a.getStopId().isEmpty() && a.getStopId().equals(z.getStopId());
    }

    /** Combined decision used by the UI layer. */
    public static boolean isCircular(String company, String dest,
                                     List<StopOption> stops) {
        if (!supports(company)) {
            return false;
        }
        return isCircularDest(dest) || isCircularSequence(stops)
                || anyCircularNote(stops);
    }

    /**
     * CIRCULAR_DISPLAY — the two operators show circular routes differently:
     *   KMB / App1933 : "213X 往 尖沙咀(循環線)"  (keeps 往 + circular suffix)
     *   Citybus       : "26 勵德邨 - 荷李活道(循環線)" (origin - dest, no 往)
     * The suffix is taken from the official dest string, never invented.
     */
    public static String displayTitle(String company, String route, String dest) {
        String d = cleanDest(dest);
        if (EtaRepository.CTB.equals(company)) {
            return route + " " + d;
        }
        return route + " 往 " + d;
    }

    /** Short badge shown on cards / search rows / detail caption. */
    public static String styleLabel(String company) {
        if (EtaRepository.KMB.equals(company)) {
            return KMB_STYLE;
        }
        if (EtaRepository.CTB.equals(company)) {
            return CTB_STYLE;
        }
        return "";
    }

    /** e.g. "九巴循環線 · 往 尖沙咀(循環線)" / "城巴循環線 · 荷李活道(循環線)" */
    public static String displayBadge(String company, String dest) {
        String label = styleLabel(company);
        if (label.isEmpty()) {
            return "";
        }
        String d = cleanDest(dest);
        if (d.isEmpty()) {
            return label;
        }
        return label + (EtaRepository.KMB.equals(company) ? " · 往 " : " · ") + d;
    }

    /** Strip a leading "往 " while keeping the official "(循環線)" suffix. */
    public static String cleanDest(String dest) {
        if (dest == null) {
            return "";
        }
        String d = dest.trim();
        if (d.startsWith("往 ")) {
            d = d.substring(2).trim();
        } else if (d.startsWith("往")) {
            d = d.substring(1).trim();
        }
        return d;
    }

    /**
     * CIRCULAR_ONLY — ONLY a circular route picks its OWN operator's stop
     * wording. A Citybus circular line never shows a KMB name and vice versa.
     * Returns "" for non-bus companies / normal routes so callers keep the
     * existing behaviour unchanged.
     */
    public static String stopNameFor(String company, String kmbName,
                                     String ctbName) {
        if (EtaRepository.CTB.equals(company)) {
            return ctbName == null ? "" : ctbName;
        }
        if (EtaRepository.KMB.equals(company)) {
            return kmbName == null ? "" : kmbName;
        }
        return "";
    }

    public static String stopIdFor(String company, String kmbId, String ctbId) {
        if (EtaRepository.CTB.equals(company)) {
            return ctbId == null ? "" : ctbId;
        }
        if (EtaRepository.KMB.equals(company)) {
            return kmbId == null ? "" : kmbId;
        }
        return "";
    }

    // ------------------------------------------------------------------ audit

    /** One stop-name finding. `kind` is one of the public constants above. */
    public static final class Issue {
        public final String stopId;
        public final String displayName;
        public final String officialName;
        public final String kind;

        Issue(String stopId, String displayName, String officialName, String kind) {
            this.stopId = stopId == null ? "" : stopId;
            this.displayName = displayName == null ? "" : displayName;
            this.officialName = officialName == null ? "" : officialName;
            this.kind = kind;
        }
    }

    /** Result of checking whether the displayed line stop names are correct. */
    public static final class Audit {
        public final int total;
        public final int verified;
        public final int ok;
        public final int missing;
        public final int mismatch;
        public final int foreign;
        public final List<Issue> issues;

        Audit(List<Issue> issues) {
            this.issues = issues == null ? Collections.<Issue>emptyList() : issues;
            int t = 0, v = 0, o = 0, m = 0, x = 0, f = 0;
            for (Issue i : this.issues) {
                t++;
                if (VERIFIED.equals(i.kind)) v++;
                else if (OK.equals(i.kind)) o++;
                else if (MISSING.equals(i.kind)) m++;
                else if (MISMATCH.equals(i.kind)) x++;
                else if (FOREIGN.equals(i.kind)) f++;
            }
            total = t; verified = v; ok = o; missing = m; mismatch = x; foreign = f;
        }

        /** Honest: without an official name table we do NOT claim "correct". */
        public String summary() {
            if (total == 0) {
                return "站名核對：冇站序資料";
            }
            String s = "站名核對：" + verified + "/" + total + " 同官方一致"
                    + " · 疑似混用 " + foreign
                    + " · 不一致 " + mismatch
                    + " · 缺失 " + missing;
            if (verified == 0) {
                s += "（未載入官方站名對照表，其餘只屬格式檢查）";
            }
            return s;
        }
    }

    private static String norm(String s) {
        if (s == null) {
            return "";
        }
        return s.replace(" ", "").replace("\u3000", "")
                .replace("，", ",").replace("（", "(").replace("）", ")")
                .trim().toLowerCase(Locale.US);
    }

    /**
     * Heuristic ONLY — never a hard verdict. Citybus stop names commonly use
     * a comma ("砵典乍街, 德輔道中"); KMB circular stops normally do not.
     * KMB stop ids look like "KT596"; Citybus ids are numeric ("002891").
     */
    public static boolean foreignSuspect(String company, String name, String stopId) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (EtaRepository.KMB.equals(company)) {
            return name.contains(",") || name.contains("，");
        }
        if (EtaRepository.CTB.equals(company)) {
            String n = name.toUpperCase(Locale.US);
            return n.matches(".*\\([A-Z]{1,3}\\d{2,4}\\).*")
                    || (stopId != null && stopId.matches("[A-Z]{1,3}\\d{2,4}"));
        }
        return false;
    }

    /**
     * CIRCULAR_NAME_CHECK — checks the displayed stop names of a line.
     * `officialNames` maps stopId -> official name_tc from the local snapshot
     * (see officialNames(company)); an empty map is allowed and is reported
     * honestly as "no official name table loaded".
     */
    public static Audit audit(List<StopOption> stops, String company,
                              Map<String, String> officialNames) {
        List<Issue> out = new ArrayList<>();
        if (stops == null) {
            return new Audit(out);
        }
        Map<String, String> official = officialNames == null
                ? Collections.<String, String>emptyMap() : officialNames;
        Set<String> seen = new HashSet<>();
        for (StopOption s : stops) {
            String id = s.getStopId();
            if (!seen.add(company + "|" + id)) {
                continue;
            }
            String name = s.getStopName();
            String off = official.get(id);
            String kind;
            if (name == null || name.trim().isEmpty()
                    || norm(name).equals(norm(id))) {
                kind = MISSING;
            } else if (off != null && !off.isEmpty()
                    && !norm(off).equals(norm(name))) {
                kind = MISMATCH;
            } else if (off != null && !off.isEmpty()) {
                kind = VERIFIED;
            } else if (foreignSuspect(company, name, id)) {
                kind = FOREIGN;
            } else {
                kind = OK;
            }
            out.add(new Issue(id, name, off == null ? "" : off, kind));
        }
        return new Audit(out);
    }

    /** Best-effort official name_tc table from the local stop snapshot. */
    public static Map<String, String> officialNames(String company) {
        Map<String, String> out = new HashMap<>();
        String file;
        if (EtaRepository.KMB.equals(company)) {
            file = RouteDataStore.KMB_STOPS;
        } else if (EtaRepository.CTB.equals(company)) {
            file = RouteDataStore.CTB_STOPS;
        } else {
            return out;
        }
        try {
            JSONObject root = RouteDataStore.readJson(file);
            if (root == null) {
                return out;
            }
            JSONArray data = root.optJSONArray("data");
            if (data == null) {
                return out;
            }
            for (int i = 0; i < data.length(); i++) {
                JSONObject o = data.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String id = HttpJson.firstNonEmpty(
                        HttpJson.str(o, "stop"),
                        HttpJson.str(o, "stop_id"),
                        HttpJson.str(o, "stopId"));
                String name = HttpJson.firstNonEmpty(
                        HttpJson.str(o, "name_tc"),
                        HttpJson.str(o, "name_en"));
                if (!id.isEmpty() && !name.isEmpty()) {
                    out.put(id, name);
                }
            }
        } catch (Exception ignored) {
            // No snapshot -> audit still works, but reports no VERIFIED rows.
        }
        return out;
    }
}
