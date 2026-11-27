package hk.eta.app.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Static + downloadable catalogues for the MTR family.
 *
 * Heavy-rail station codes and MTR Bus stop names come from the monthly CSV files at
 * https://opendata.mtr.com.hk/data/ . The CSV headers are detected by keyword so the
 * app keeps working when MTR renames a column. If the CSV is unreachable we fall back
 * to the bundled seed list below (common stations / raw busStopId).
 *
 * Light Rail station IDs are taken from the official
 * "Data Dictionary for Light Rail Next Train API" station ID table.
 */
public final class MtrCatalog {

    public static final String[] LINE_CODES = {
            "AEL", "TCL", "TML", "TKL", "EAL", "SIL", "TWL", "ISL", "KTL", "DRL"
    };

    public static final String[] LINE_NAMES = {
            "機場快綫", "東涌綫", "屯馬綫", "將軍澳綫", "東鐵綫",
            "南港島綫", "荃灣綫", "港島綫", "觀塘綫", "迪士尼綫"
    };

    public static final String[] MTR_BUS_ROUTES = {
            "506", "K12", "K14", "K17", "K18", "K51", "K51A", "K52", "K52A",
            "K53", "K54", "K54A", "K58", "K65", "K65A", "K66", "K68", "K73",
            "K74", "K75A", "K75P", "K75S", "K76", "K76S"
    };

    /**
     * HKE_MOBILITY_CATEGORIES: 官方輕鐵路線號碼表，同 LINE_CODES／
     * MTR_BUS_ROUTES 一樣係項目自有嘅官方碼表（唔係即時 API、唔捏造班次）。
     * 之前 railPreload() 只出重鐵＋港鐵接駁巴士，港鐵類別見唔到輕鐵；
     * 而家預載會補返輕鐵列，令「港鐵（重鐵、輕鐵路線）類別」顯示返。
     */
    public static final String[] LRT_ROUTES = {
            "505", "507", "610", "614", "614P", "615", "615P",
            "705", "706", "751", "751P", "761P"
    };

    private static final String HEAVY_CSV =
            "https://opendata.mtr.com.hk/data/mtr_lines_and_stations.csv";
    private static final String LRT_CSV =
            "https://opendata.mtr.com.hk/data/light_rail_routes_and_stops.csv";
    private static final String BUS_STOPS_CSV =
            "https://opendata.mtr.com.hk/data/mtr_bus_stops.csv";

    private static volatile Map<String, List<Station>> heavyCache;
    private static volatile Map<String, String> busStopNameCache;

    private MtrCatalog() {
    }

    /** Called by RouteDataStore after a catalogue update / clear. */
    public static void invalidateCaches() {
        heavyCache = null;
        busStopNameCache = null;
    }

    /** Local snapshot first, live monthly CSV as fallback. */
    private static String catalogCsv(String file, String url) throws Exception {
        String local = RouteDataStore.readText(file);
        if (local != null && !local.trim().isEmpty()) {
            return local;
        }
        return HttpJson.getText(url);
    }

    public static final class Station {
        public final String code;
        public final String nameTc;
        public final String nameEn;

        public Station(String code, String nameTc, String nameEn) {
            this.code = code;
            this.nameTc = nameTc == null ? "" : nameTc;
            this.nameEn = nameEn == null ? "" : nameEn;
        }
    }

    /** A coordinate-bearing stop parsed from one of the monthly CSV catalogues. */
    public static final class NearbySeed {
        public final String company;
        public final String id;
        public final String name;
        public final String ref;   // line code / route name / "ALL"
        public final String note;
        public final double lat;
        public final double lng;

        NearbySeed(String company, String id, String name, String ref,
                   String note, double lat, double lng) {
            this.company = company;
            this.id = id;
            this.name = name == null ? "" : name;
            this.ref = ref == null ? "" : ref;
            this.note = note == null ? "" : note;
            this.lat = lat;
            this.lng = lng;
        }
    }

    public static String lineName(String code) {
        for (int i = 0; i < LINE_CODES.length; i++) {
            if (LINE_CODES[i].equalsIgnoreCase(code)) {
                return LINE_NAMES[i];
            }
        }
        return code;
    }

    public static boolean isLineCode(String value) {
        for (String code : LINE_CODES) {
            if (code.equalsIgnoreCase(value == null ? "" : value.trim())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------- heavy rail

    public static List<Station> heavyRailStations(String lineCode) {
        Map<String, List<Station>> all = loadHeavyCsv();
        List<Station> list = all.get(lineCode.toUpperCase(Locale.US));
        if (list != null && !list.isEmpty()) {
            return list;
        }
        return fallbackStations(lineCode);
    }

    private static Map<String, List<Station>> loadHeavyCsv() {
        Map<String, List<Station>> cached = heavyCache;
        if (cached != null) {
            return cached;
        }
        Map<String, List<Station>> out = new HashMap<>();
        try {
            List<String[]> rows = parseCsv(catalogCsv(
                    RouteDataStore.MTR_LINES, HEAVY_CSV));
            if (rows.size() > 1) {
                List<String> h = lower(rows.get(0));
                int lineCol = firstWithAll(h, "line", "code");
                if (lineCol < 0) {
                    lineCol = firstWithAny(h, "line");
                }
                int codeCol = firstWithAll(h, "station", "code");
                if (codeCol < 0) {
                    codeCol = firstWithAll(h, "station", "id");
                }
                if (codeCol < 0) {
                    codeCol = firstWithAny(h, "stationcode", "code");
                }
                int tcCol = firstWithAll(h, "name", "ch");
                if (tcCol < 0) {
                    tcCol = firstWithAll(h, "name", "tc");
                }
                if (tcCol < 0) {
                    tcCol = firstWithAny(h, "chinese", "中文");
                }
                int enCol = firstWithAll(h, "name", "en");
                if (enCol < 0) {
                    enCol = firstWithAny(h, "english", "name");
                }
                if (lineCol >= 0 && codeCol >= 0) {
                    for (int i = 1; i < rows.size(); i++) {
                        String[] r = rows.get(i);
                        String line = cell(r, lineCol).toUpperCase(Locale.US);
                        String code = cell(r, codeCol).toUpperCase(Locale.US);
                        if (line.isEmpty() || code.isEmpty()) {
                            continue;
                        }
                        String tc = tcCol >= 0 ? cell(r, tcCol) : "";
                        String en = enCol >= 0 ? cell(r, enCol) : "";
                        List<Station> list = out.get(line);
                        if (list == null) {
                            list = new ArrayList<>();
                            out.put(line, list);
                        }
                        boolean duplicate = false;
                        for (Station existing : list) {
                            if (existing.code.equals(code)) {
                                duplicate = true;
                                break;
                            }
                        }
                        if (!duplicate) {
                            list.add(new Station(code, tc, en));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback list below is used.
        }
        heavyCache = out;
        return out;
    }

    // ------------------------------------------------------------- MTR Bus

    public static String busStopName(String busStopId) {
        Map<String, String> cached = busStopNameCache;
        if (cached == null) {
            cached = loadBusStopCsv();
            busStopNameCache = cached;
        }
        String name = cached.get(busStopId);
        return name == null ? "" : name;
    }

    private static Map<String, String> loadBusStopCsv() {
        Map<String, String> out = new HashMap<>();
        try {
            List<String[]> rows = parseCsv(catalogCsv(
                    RouteDataStore.MTR_BUS_STOPS, BUS_STOPS_CSV));
            if (rows.size() > 1) {
                List<String> h = lower(rows.get(0));
                int idCol = firstWithAll(h, "stop", "id");
                if (idCol < 0) {
                    idCol = firstWithAny(h, "stopid", "stop");
                }
                int nameCol = firstWithAll(h, "name", "c");
                if (nameCol < 0) {
                    nameCol = firstWithAll(h, "name", "ch");
                }
                if (nameCol < 0) {
                    nameCol = firstWithAll(h, "name", "tc");
                }
                if (nameCol < 0) {
                    nameCol = firstWithAny(h, "name", "中文");
                }
                if (idCol >= 0) {
                    for (int i = 1; i < rows.size(); i++) {
                        String[] r = rows.get(i);
                        String id = cell(r, idCol);
                        if (id.isEmpty()) {
                            continue;
                        }
                        String name = nameCol >= 0 ? cell(r, nameCol) : "";
                        if (!name.isEmpty()) {
                            out.put(id, name);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // busStopId remains the display label.
        }
        return out;
    }

    // ------------------------------------------------------------- Light Rail

    /** "id|繁中|English", from the official LR Next Train data dictionary. */
    private static final String[] LRT_STATIONS = {
            "1|屯門碼頭|Tuen Mun Ferry Pier",
            "10|美樂|Melody Garden",
            "15|蝴蝶|Butterfly",
            "20|輕鐵車廠|Light Rail Depot",
            "30|龍門|Lung Mun",
            "40|青山村|Tsing Shan Tsuen",
            "50|青雲|Tsing Wun",
            "60|建安|Kin On",
            "70|河田|Ho Tin",
            "75|蔡意橋|Choy Yee Bridge",
            "80|澤豐|Affluence",
            "90|屯門醫院|Tuen Mun Hospital",
            "100|兆康|Siu Hong",
            "110|麒麟|Kei Lun",
            "120|青松|Ching Chung",
            "130|建生|Kin Sang",
            "140|田景|Tin King",
            "150|良景|Leung King",
            "160|新圍|San Wai",
            "170|石排|Shek Pai",
            "180|山景(北)|Shan King (North)",
            "190|山景(南)|Shan King (South)",
            "200|鳴琴|Ming Kum",
            "212|大興(北)|Tai Hing (North)",
            "220|大興(南)|Tai Hing (South)",
            "230|銀圍|Ngan Wai",
            "240|兆禧|Siu Hei",
            "250|海皇路(原屯門泳池)|Hoi Wong Road",
            "260|豐景園|Goodview Garden",
            "265|兆麟|Siu Lun",
            "270|安定|On Ting",
            "275|友愛|Yau Oi",
            "280|市中心|Town Centre",
            "295|屯門|Tuen Mun",
            "300|杯渡|Pui To",
            "310|何福堂|Hoh Fuk Tong",
            "320|新墟|San Hui",
            "330|景峰|Prime View",
            "340|鳳地|Fung Tei",
            "350|藍地|Lam Tei",
            "360|泥圍|Nai Wai",
            "370|鐘屋村|Chung Uk Tsuen",
            "380|洪水橋|Hung Shui Kiu",
            "390|塘坊村|Tong Fong Tsuen",
            "400|屏山|Ping Shan",
            "425|坑尾村|Hang Mei Tsuen",
            "430|天水圍|Tin Shui Wai",
            "435|天慈|Tin Tsz",
            "445|天耀|Tin Yiu",
            "448|樂湖|Locwood",
            "450|天湖|Tin Wu",
            "455|銀座|Ginza",
            "460|天瑞|Tin Shui",
            "468|頌富|Chung Fu",
            "480|天富|Tin Fu",
            "490|翠湖|Chestwood",
            "500|天榮|Tin Wing",
            "510|天悅|Tin Yuet",
            "520|天秀|Tin Sau",
            "530|濕地公園|Wetland Park",
            "540|天恒|Tin Heng",
            "550|天逸|Tin Yat",
            "560|水邊圍|Shui Pin Wai",
            "570|豐年路|Fung Nin Road",
            "580|康樂路|Hong Lok Road",
            "590|大棠路|Tai Tong Road",
            "600|元朗|Yuen Long",
            "920|三聖|Sam Shing"
    };

    public static List<Station> lrtStations() {
        List<Station> out = new ArrayList<>();
        for (String row : LRT_STATIONS) {
            String[] p = row.split("\\|", 3);
            out.add(new Station(p[0], p.length > 1 ? p[1] : "", p.length > 2 ? p[2] : ""));
        }
        return out;
    }

    // ------------------------------------------------------------- fallback seed

    private static final Map<String, String> FALLBACK = new LinkedHashMap<>();

    static {
        FALLBACK.put("ISL", "KET|堅尼地城|Kennedy Town;HKU|香港大學|HKU;SYP|西營盤|Sai Ying Pun;"
                + "SHW|上環|Sheung Wan;CEN|中環|Central;ADM|金鐘|Admiralty;WAC|灣仔|Wan Chai;"
                + "CAB|銅鑼灣|Causeway Bay;NOP|北角|North Point;QUB|鰂魚涌|Quarry Bay;CHW|柴灣|Chai Wan");
        FALLBACK.put("TWL", "CEN|中環|Central;ADM|金鐘|Admiralty;TST|尖沙咀|Tsim Sha Tsui;"
                + "JOR|佐敦|Jordan;YMT|油麻地|Yau Ma Tei;MOK|旺角|Mong Kok;PRE|太子|Prince Edward;"
                + "MEF|美孚|Mei Foo;LAK|荔景|Lai King;TSW|荃灣|Tsuen Wan");
        FALLBACK.put("KTL", "WHA|黃埔|Whampoa;HOM|何文田|Ho Man Tin;YMT|油麻地|Yau Ma Tei;"
                + "MOK|旺角|Mong Kok;PRE|太子|Prince Edward;KOT|九龍塘|Kowloon Tong;"
                + "DIH|鑽石山|Diamond Hill;CHH|彩虹|Choi Hung;KWT|觀塘|Kwun Tong;TIK|調景嶺|Tiu Keng Leng");
        FALLBACK.put("TKL", "NOP|北角|North Point;QUB|鰂魚涌|Quarry Bay;YAT|油塘|Yau Tong;"
                + "TIK|調景嶺|Tiu Keng Leng;TKO|將軍澳|Tseung Kwan O;HAH|坑口|Hang Hau;"
                + "POA|寶琳|Po Lam;LHP|康城|LOHAS Park");
        FALLBACK.put("TCL", "HOK|香港|Hong Kong;KOW|九龍|Kowloon;OLY|奧運|Olympic;"
                + "NAC|南昌|Nam Cheong;LAK|荔景|Lai King;TSY|青衣|Tsing Yi;SUN|欣澳|Sunny Bay;"
                + "TUC|東涌|Tung Chung");
        FALLBACK.put("DRL", "SUN|欣澳|Sunny Bay;DIS|迪士尼|Disneyland Resort");
        FALLBACK.put("AEL", "HOK|香港|Hong Kong;KOW|九龍|Kowloon;TSY|青衣|Tsing Yi;"
                + "AIR|機場|Airport;AWE|博覽館|AsiaWorld-Expo");
        FALLBACK.put("EAL", "ADM|金鐘|Admiralty;EXH|會展|Exhibition Centre;HUH|紅磡|Hung Hom;"
                + "MKK|旺角東|Mong Kok East;KOT|九龍塘|Kowloon Tong;TAW|大圍|Tai Wai;"
                + "SHT|沙田|Sha Tin;FOT|火炭|Fo Tan;UNI|大學|University;TAP|大埔墟|Tai Po Market;"
                + "FAN|粉嶺|Fanling;SHS|上水|Sheung Shui;LOW|羅湖|Lo Wu;LMC|落馬洲|Lok Ma Chau");
        FALLBACK.put("TML", "WKS|烏溪沙|Wu Kai Sha;MOS|馬鞍山|Ma On Shan;TAW|大圍|Tai Wai;"
                + "DIH|鑽石山|Diamond Hill;HOM|何文田|Ho Man Tin;HUH|紅磡|Hung Hom;"
                + "ETS|尖東|East Tsim Sha Tsui;AUS|柯士甸|Austin;NAC|南昌|Nam Cheong;"
                + "MEF|美孚|Mei Foo;TWW|荃灣西|Tsuen Wan West;YUL|元朗|Yuen Long;"
                + "LOP|朗屏|Long Ping;TIS|天水圍|Tin Shui Wai;SIH|兆康|Siu Hong;TUM|屯門|Tuen Mun");
        FALLBACK.put("SIL", "ADM|金鐘|Admiralty;OCP|海洋公園|Ocean Park;WCH|黃竹坑|Wong Chuk Hang;"
                + "LET|利東|Lei Tung;SOH|海怡半島|South Horizons");
    }

    private static List<Station> fallbackStations(String line) {
        String seed = FALLBACK.get(line.toUpperCase(Locale.US));
        if (seed == null || seed.isEmpty()) {
            return Collections.emptyList();
        }
        List<Station> out = new ArrayList<>();
        for (String item : seed.split(";")) {
            String[] p = item.split("\\|", 3);
            out.add(new Station(p[0], p.length > 1 ? p[1] : "", p.length > 2 ? p[2] : ""));
        }
        return out;
    }

    // ------------------------------------------------------------- nearby CSV

    /**
     * Reads the three monthly CSV catalogues and returns only rows that carry a
     * usable latitude / longitude column. Header names are detected by keyword,
     * so a column rename does not break the app. Files without coordinates simply
     * contribute nothing (the nearby list then shows bus/minibus stops only).
     */
    public static List<NearbySeed> nearbySeeds() {
        List<NearbySeed> out = new ArrayList<>();
        out.addAll(csvNearby(RouteDataStore.MTR_LINES, HEAVY_CSV,
                EtaRepository.MTR, "LINE"));
        out.addAll(csvNearby(RouteDataStore.LRT_STOPS, LRT_CSV,
                EtaRepository.LRT, "ALL"));
        out.addAll(csvNearby(RouteDataStore.MTR_BUS_STOPS, BUS_STOPS_CSV,
                EtaRepository.MTRB, "ROUTE"));
        return out;
    }

    private static List<NearbySeed> csvNearby(String file, String url,
                                              String company, String refMode) {
        List<NearbySeed> out = new ArrayList<>();
        try {
            List<String[]> rows = parseCsv(catalogCsv(file, url));
            if (rows.size() < 2) {
                return out;
            }
            List<String> h = lower(rows.get(0));
            int idCol = firstWithAll(h, "station", "code");
            if (idCol < 0) {
                idCol = firstWithAll(h, "stop", "id");
            }
            if (idCol < 0) {
                idCol = firstWithAny(h, "stopid", "stationid", "code");
            }
            int latCol = firstWithAny(h, "latitude", "lat");
            int lngCol = firstWithAny(h, "longitude", "long", "lng", "lon");
            if (idCol < 0 || latCol < 0 || lngCol < 0) {
                return out;
            }
            int nameCol = firstWithAll(h, "name", "c");
            if (nameCol < 0) {
                nameCol = firstWithAll(h, "name", "ch");
            }
            if (nameCol < 0) {
                nameCol = firstWithAll(h, "name", "tc");
            }
            if (nameCol < 0) {
                nameCol = firstWithAny(h, "chinese", "name");
            }
            int lineCol = firstWithAny(h, "linecode", "line", "route");
            Set<String> seen = new java.util.HashSet<>();
            for (int i = 1; i < rows.size(); i++) {
                String[] r = rows.get(i);
                String id = cell(r, idCol);
                double lat = parseCoordinate(cell(r, latCol));
                double lng = parseCoordinate(cell(r, lngCol));
                if (id.isEmpty() || lat == 0.0 || lng == 0.0) {
                    continue;
                }
                String ref = "";
                String note = "";
                if ("LINE".equals(refMode)) {
                    ref = lineCol >= 0 ? cell(r, lineCol).toUpperCase(Locale.US) : "";
                    if (!isLineCode(ref)) {
                        continue;   // heavy-rail ETA needs a valid line code
                    }
                    note = lineName(ref);
                } else if ("ROUTE".equals(refMode)) {
                    ref = lineCol >= 0 ? cell(r, lineCol) : "";
                    if (ref.isEmpty()) {
                        continue;   // MTR Bus ETA needs a routeName
                    }
                    note = ref;
                } else {
                    ref = "ALL";
                    note = "輕鐵";
                }
                if (!seen.add(company + "|" + id + "|" + ref)) {
                    continue;
                }
                String name = nameCol >= 0 ? cell(r, nameCol) : "";
                out.add(new NearbySeed(company, id, name, ref, note, lat, lng));
            }
        } catch (Exception ignored) {
            // CSV unavailable: this operator is omitted from the nearby list.
        }
        return out;
    }

    private static double parseCoordinate(String raw) {
        try {
            double v = Double.parseDouble(raw.trim());
            return (v > -180.0 && v < 180.0 && v != 0.0) ? v : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    // ------------------------------------------------------------- CSV helpers

    private static List<String[]> parseCsv(String text) {
        List<String[]> rows = new ArrayList<>();
        if (text == null) {
            return rows;
        }
        for (String line : text.split("\\r?\\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            rows.add(splitCsvLine(line).toArray(new String[0]));
        }
        return rows;
    }

    private static List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                cells.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        cells.add(sb.toString().trim());
        return cells;
    }

    /**
     * parseCsv() stores each row as a String[], while firstWithAll / firstWithAny
     * work on a List<String>. Bridge the two so the three header lookups compile
     * (previously `lower(rows.get(0))` was a String[] passed to a List<String>
     * parameter -> "incompatible types: String[] cannot be converted to
     * List<String>" at compileDebugJavaWithJavac).
     */
    private static List<String> lower(String[] cells) {
        return lower(Arrays.asList(cells));
    }

    private static List<String> lower(List<String> cells) {
        List<String> out = new ArrayList<>();
        for (String c : cells) {
            String cleaned = c.replace("\ufeff", "")
                    .toLowerCase(Locale.US)
                    .replace(" ", "")
                    .replace("_", "")
                    .replace("-", "")
                    .replace(".", "");
            out.add(cleaned);
        }
        return out;
    }

    private static int firstWithAll(List<String> header, String... terms) {
        for (int i = 0; i < header.size(); i++) {
            boolean ok = true;
            for (String term : terms) {
                if (!header.get(i).contains(term)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return i;
            }
        }
        return -1;
    }

    private static int firstWithAny(List<String> header, String... terms) {
        for (int i = 0; i < header.size(); i++) {
            for (String term : terms) {
                if (header.get(i).contains(term)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String cell(String[] row, int index) {
        return index >= 0 && index < row.length ? row[index] : "";
    }
}
