package hk.eta.app.api;

/**
 * Plain Java models shared between the Java open-data layer and the Kotlin UI layer.
 * Keeping them dependency-free also makes the parsing logic easy to unit-check.
 */
public final class EtaModels {

    private EtaModels() {
    }

    /** One stop on one direction of a route / one station of a rail line. */
    public static final class StopOption {
        private final String company;   // KMB, CTB, NLB, GMB, MTR, LRT, MTRB
        private final String route;     // route no, line code or LR route filter
        private final String bound;     // O / I / 1 / 2 / UP / DOWN / ALL
        private final int seq;
        private final String stopId;
        private final String stopName;
        private final int serviceType;  // KMB service type, others always 1
        private final String ref;       // operator-specific id: NLB/GMB routeId, MTR line, MTRB routeName
        private final String note;      // destination / platform / status hint
        // GMB_CIRCULAR_ETA: stop_seq of the LAST stop of a circular route.
        // 0 = not circular / unknown. Kept OUT of key() so favourites stay
        // byte-for-byte compatible with earlier releases.
        private final int loopLastSeq;

        public StopOption(String company, String route, String bound, int seq,
                          String stopId, String stopName, int serviceType,
                          String ref, String note) {
            this(company, route, bound, seq, stopId, stopName, serviceType,
                    ref, note, 0);
        }

        public StopOption(String company, String route, String bound, int seq,
                          String stopId, String stopName, int serviceType,
                          String ref, String note, int loopLastSeq) {
            this.company = company;
            this.route = route;
            this.bound = bound;
            this.seq = seq;
            this.stopId = stopId;
            this.stopName = stopName == null ? "" : stopName;
            this.serviceType = serviceType < 1 ? 1 : serviceType;
            this.ref = ref == null ? "" : ref;
            this.note = note == null ? "" : note;
            this.loopLastSeq = loopLastSeq > 0 ? loopLastSeq : 0;
        }

        public int getLoopLastSeq() {
            return loopLastSeq;
        }

        public String getCompany() {
            return company;
        }

        public String getRoute() {
            return route;
        }

        public String getBound() {
            return bound;
        }

        public int getSeq() {
            return seq;
        }

        public String getStopId() {
            return stopId;
        }

        public String getStopName() {
            return stopName;
        }

        public int getServiceType() {
            return serviceType;
        }

        public String getRef() {
            return ref;
        }

        public String getNote() {
            return note;
        }

        /** Stable key used by the favourites store. */
        public String key() {
            return company + "|" + route + "|" + stopId + "|" + ref + "|" + bound;
        }
    }

    /** One predicted arrival, normalised across every operator. */
    public static final class EtaItem {
        private final String company;
        private final String route;
        private final String stopId;
        private final String destTc;
        private final String etaIso;
        private final long etaMillis;
        private final int etaSeq;
        private final String remarkTc;
        private final String dir;
        private final String dataTimestampIso;

        public EtaItem(String company, String route, String stopId, String destTc,
                       String etaIso, long etaMillis, int etaSeq, String remarkTc,
                       String dir, String dataTimestampIso) {
            this.company = company;
            this.route = route;
            this.stopId = stopId;
            this.destTc = destTc == null ? "" : destTc;
            this.etaIso = etaIso == null ? "" : etaIso;
            this.etaMillis = etaMillis;
            this.etaSeq = etaSeq;
            this.remarkTc = remarkTc == null ? "" : remarkTc;
            this.dir = dir == null ? "" : dir;
            this.dataTimestampIso = dataTimestampIso == null ? "" : dataTimestampIso;
        }

        public String getCompany() {
            return company;
        }

        public String getRoute() {
            return route;
        }

        public String getStopId() {
            return stopId;
        }

        public String getDestTc() {
            return destTc;
        }

        public String getEtaIso() {
            return etaIso;
        }

        public long getEtaMillis() {
            return etaMillis;
        }

        public int getEtaSeq() {
            return etaSeq;
        }

        public String getRemarkTc() {
            return remarkTc;
        }

        public String getDir() {
            return dir;
        }

        public String getDataTimestampIso() {
            return dataTimestampIso;
        }
    }
}
