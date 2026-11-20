package hk.eta.app.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GMB_CIRCULAR_ETA — green-minibus CIRCULAR routes.
 *
 * On a circular GMB route the FIRST and LAST stop are the SAME physical stop
 * (identical stop_id) but occupy DIFFERENT stop_seq positions. The old
 * /eta/route-stop/{route_id}/{stop_id} URL carries no stop_seq, so both ends
 * fetched the identical payload and the code flattened EVERY occurrence
 * without de-duplicating. Result:
 *
 *   1. the first stop showed three copies of the SAME trip
 *      ("三個同一班次") instead of three DIFFERENT trips;
 *   2. the last stop showed exactly the same three trips as the first stop.
 *
 * This class is pure Java (no Android, no org.json) so the whole decision can
 * be exercised offline. Detection is DATA-DRIVEN: circularity comes from the
 * actual stop_seq values, never from a hard-coded list of route numbers.
 * Nothing is invented — when the operator reports fewer than three distinct
 * trips we show what it gave us and say so in the README.
 */
public final class GmbCircularEta {

    /** APP1933 shows three upcoming arrivals. */
    public static final int MAX_TRIPS = 3;

    /** One raw ETA line from ONE official occurrence (before normalisation). */
    public static final class Raw {
        /** Route identity for grouping (GMB route_id). */
        public final String group;
        public final int routeSeq;
        /** Official stop_seq; <= 0 when the payload omits it. */
        public final int stopSeq;
        public final String etaIso;
        public final long millis;
        public final int etaSeq;
        public final String remark;

        public Raw(String group, int routeSeq, int stopSeq, String etaIso,
                   long millis, int etaSeq, String remark) {
            this.group = group == null ? "" : group;
            this.routeSeq = routeSeq;
            this.stopSeq = stopSeq;
            this.etaIso = etaIso == null ? "" : etaIso;
            this.millis = millis;
            this.etaSeq = etaSeq;
            this.remark = remark == null ? "" : remark;
        }
    }

    private GmbCircularEta() {
    }

    /**
     * Canonical identity of ONE physical trip. Two lines with the same key are
     * the same minibus / same predicted arrival reported twice, NOT two
     * different trips. etaSeq is excluded on purpose (the same bus is often
     * repeated with eta_seq 1/2/3 but one identical timestamp); stopSeq is
     * excluded on purpose so a trip reported at BOTH termini can be recognised
     * and kept out of the last-stop set.
     */
    public static String tripKey(Raw r) {
        String iso = r.etaIso.trim();
        String basis = iso.isEmpty() ? ("ms:" + r.millis) : iso;
        return r.group + "|" + r.routeSeq + "|" + basis + "|"
                + r.remark.trim();
    }

    /** Sorted distinct stop_seq values actually present in the payload. */
    public static List<Integer> stopSeqs(List<Raw> all) {
        Set<Integer> s = new LinkedHashSet<>();
        if (all != null) {
            for (Raw r : all) {
                if (r != null && r.stopSeq > 0) {
                    s.add(r.stopSeq);
                }
            }
        }
        List<Integer> out = new ArrayList<>(s);
        Collections.sort(out);
        return out;
    }

    public static int firstSeq(List<Raw> all) {
        List<Integer> s = stopSeqs(all);
        return s.isEmpty() ? 0 : s.get(0);
    }

    public static int lastSeq(List<Raw> all) {
        List<Integer> s = stopSeqs(all);
        return s.isEmpty() ? 0 : s.get(s.size() - 1);
    }

    /** True when the payload shows this stop at >= 2 positions. */
    public static boolean isCircularPayload(List<Raw> all) {
        return stopSeqs(all).size() >= 2;
    }

    /** Chronological, stable order (equal millis keep API order). */
    public static List<Raw> sortByMillis(List<Raw> in) {
        List<Raw> out = in == null ? new ArrayList<Raw>() : new ArrayList<>(in);
        Collections.sort(out, new Comparator<Raw>() {
            @Override
            public int compare(Raw a, Raw b) {
                return Long.compare(a.millis, b.millis);
            }
        });
        return out;
    }

    /** Collapse duplicated lines of the SAME physical trip, keep first order. */
    public static List<Raw> distinct(List<Raw> in) {
        Map<String, Raw> seen = new LinkedHashMap<>();
        if (in != null) {
            for (Raw r : in) {
                if (r != null) {
                    String k = tripKey(r);
                    if (!seen.containsKey(k)) {
                        seen.put(k, r);
                    }
                }
            }
        }
        return new ArrayList<>(seen.values());
    }

    /**
     * Occurrences belonging to ONE tapped stop position. Exact stop_seq match
     * is preferred; when the ETA payload numbers the two passes differently
     * from the route-stop list (e.g. 1/2 instead of 1/42) map by rank. If
     * nothing resolves, return ALL so no real arrival is silently dropped.
     */
    public static List<Raw> forStop(List<Raw> all, int wantedSeq) {
        List<Raw> source = all == null ? Collections.<Raw>emptyList() : all;
        if (wantedSeq <= 0 || !isCircularPayload(source)) {
            return new ArrayList<>(source);
        }
        List<Raw> exact = new ArrayList<>();
        for (Raw r : source) {
            if (r != null && r.stopSeq == wantedSeq) {
                exact.add(r);
            }
        }
        if (!exact.isEmpty()) {
            return exact;
        }
        List<Integer> seqs = stopSeqs(source);
        int mapped = wantedSeq <= seqs.get(0)
                ? seqs.get(0) : seqs.get(seqs.size() - 1);
        List<Raw> ranked = new ArrayList<>();
        for (Raw r : source) {
            if (r != null && r.stopSeq == mapped) {
                ranked.add(r);
            }
        }
        return ranked.isEmpty() ? new ArrayList<>(source) : ranked;
    }

    /** Remove every trip whose key appears in {@code banned}. */
    public static List<Raw> without(List<Raw> in, Set<String> banned) {
        List<Raw> out = new ArrayList<>();
        if (in != null) {
            for (Raw r : in) {
                if (r != null && !banned.contains(tripKey(r))) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    public static Set<String> keys(List<Raw> in) {
        Set<String> s = new LinkedHashSet<>();
        if (in != null) {
            for (Raw r : in) {
                if (r != null) {
                    s.add(tripKey(r));
                }
            }
        }
        return s;
    }

    /** Drop trips that are ALSO reported at the FIRST terminus. */
    public static List<Raw> excludeFirstStopTrips(List<Raw> lastStop,
                                                   List<Raw> firstStop) {
        return without(lastStop, keys(firstStop));
    }

    private static List<Raw> take(List<Raw> in, int n) {
        if (in == null || in.size() <= n) {
            return in == null ? new ArrayList<Raw>() : new ArrayList<>(in);
        }
        return new ArrayList<>(in.subList(0, n));
    }

    private static List<Raw> slice(List<Raw> in, int from, int to) {
        List<Raw> out = new ArrayList<>();
        if (in == null || from >= in.size()) {
            return out;
        }
        out.addAll(in.subList(from, Math.min(to, in.size())));
        return out;
    }

    /**
     * Full selection for ONE tapped stop of a GMB route.
     *
     * @param all         every ETA line of the /eta/route-stop payload
     * @param wantedSeq   stop_seq of the tapped stop (0 = unknown shortcut)
     * @param loopLastSeq stop_seq of the route's LAST stop, supplied by the
     *                    official route-stop list (0 = not a circular route /
     *                    caller has no sequence, e.g. a favourite)
     *
     * Guarantees:
     *   - the returned trips are DISTINCT (no "三個同一班次");
     *   - for a circular line the LAST stop never reuses the FIRST stop's
     *     trips — it takes its own occurrence, or the next disjoint window of
     *     real arrivals when the payload does not split by stop_seq;
     *   - at most {@link #MAX_TRIPS} trips, never fabricated.
     */
    public static List<Raw> select(List<Raw> all, int wantedSeq,
                                   int loopLastSeq) {
        List<Raw> deduped = distinct(sortByMillis(all));
        if (deduped.isEmpty()) {
            return new ArrayList<>();
        }

        boolean payloadSplit = isCircularPayload(all);
        int pFirst = firstSeq(all);
        int pLast = lastSeq(all);
        int firstSeq = pFirst > 0 ? pFirst : 1;
        int lastSeq = loopLastSeq > 0 ? loopLastSeq : pLast;
        boolean circular = (loopLastSeq > 0 && loopLastSeq > firstSeq)
                || payloadSplit;

        boolean atFirst = wantedSeq <= 0 || (circular && wantedSeq <= firstSeq);
        boolean atLast = circular && wantedSeq > 0
                && wantedSeq == lastSeq && lastSeq > firstSeq;

        // Trips shown at the FIRST terminus (the reference set).
        List<Raw> firstPool = payloadSplit
                ? distinct(sortByMillis(forStop(all, pFirst)))
                : take(deduped, MAX_TRIPS);

        if (atFirst) {
            return take(firstPool, MAX_TRIPS);
        }

        if (atLast) {
            List<Raw> lastPool = payloadSplit
                    ? distinct(sortByMillis(forStop(all, pLast)))
                    : slice(deduped, MAX_TRIPS, MAX_TRIPS * 2);
            List<Raw> candidates = excludeFirstStopTrips(lastPool, firstPool);
            if (candidates.size() < MAX_TRIPS) {
                // Honest top-up: any OTHER real arrival, still disjoint.
                candidates = without(deduped, keys(firstPool));
            }
            return take(candidates, MAX_TRIPS);
        }

        // Middle stop, non-circular route, or unknown sequence: the payload
        // is already scoped to (route_id, stop_id), so just de-duplicate.
        return take(deduped, MAX_TRIPS);
    }

    /** Human-readable audit line for diagnostics (never fabricated). */
    public static String audit(List<Raw> all, int wantedSeq,
                               int loopLastSeq, List<Raw> selected) {
        int total = all == null ? 0 : all.size();
        int chosen = selected == null ? 0 : selected.size();
        boolean circular = (loopLastSeq > 0 && loopLastSeq > 1)
                || isCircularPayload(all);
        if (!circular) {
            return "綠小巴 · " + chosen + " 班不同班次（去重 " + total + " 筆）";
        }
        int a = firstSeq(all) > 0 ? firstSeq(all) : 1;
        int z = loopLastSeq > 0 ? loopLastSeq : lastSeq(all);
        String pos = wantedSeq >= z && z > a ? "最後一站" : "第一站";
        return "綠小巴循環線 · " + pos + "（stop_seq " + a + "→" + z
                + "）· " + chosen + " 班不同班次，已避開另一總站重複班次";
    }
}
