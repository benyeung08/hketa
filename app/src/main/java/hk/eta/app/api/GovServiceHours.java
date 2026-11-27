package hk.eta.app.api;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GOV_SERVICE_HOURS — 「服務時間及班次由政府公開資料獲取」。
 *
 * 數據來源：運輸署「公共交通服務班次資料」(DATA.GOV.HK, dataset
 * hk-td-tis_11-pt-headway-en)，繁體中文版 GTFS 檔案，每兩星期更新。
 * 數據字典：https://static.data.gov.hk/td/pt-headway-en/dataspec/ptheadway_dataspec.pdf
 *
 * 格式老實講：呢份**唔係單一 JSON**，係 GTFS 嘅 UTF-8 CSV .txt 檔
 * （整合套裝 gtfs.zip 都得）。即時 ETA 先係 JSON。本類只讀 5 個較細嘅
 * 檔（約 6 MB），**刻意唔下載 50 MB 嘅 stop_times.txt**：官方字典講明，
 * trips 定義喺 frequencies.txt 時應忽略 stop_times 嘅絕對時間；而
 * trips.txt 嘅 trip_id 尾段本身就係官方定義嘅開車時間 HHMM，足以計
 * 頭班／尾班，唔使捏造、亦唔使揹 50 MB。
 *
 * 讀到嘅欄位（全部嚟自官方檔案）：
 *   routes.txt          route_id / agency_id / route_short_name
 *   trips.txt           route_id / service_id / trip_id (= route-bound-service-HHMM)
 *   calendar.txt        星期一至日 + start_date / end_date
 *   calendar_dates.txt  exception_type 1 加開 / 2 取消
 *   frequencies.txt     start_time / end_time / headway_secs
 *
 * 同一路線號可以同時屬於 KMB / CTB / NLB / GMB（官方 routes.txt 實測
 * 「1」就有 5 間），所以**必須按 agency_id 過濾公司**，唔撈錯隔離公司。
 * 純 Java、零 Android import、零網絡、可離線測。
 */
public final class GovServiceHours {

    public static final String DATASET_ID = "hk-td-tis_11-pt-headway-en";
    public static final String DATASET_PAGE =
            "https://data.gov.hk/tc-data/dataset/" + DATASET_ID;
    public static final String BASE_TC = "https://static.data.gov.hk/td/pt-headway-tc/";
    public static final String BASE_EN = "https://static.data.gov.hk/td/pt-headway-en/";
    public static final String LAST_UPDATED_URL = BASE_EN + "DATA_LAST_UPDATED_DATE.csv";
    public static final String DATASPEC_URL = BASE_EN + "dataspec/ptheadway_dataspec.pdf";
    public static final String GTFS_ZIP_URL = BASE_TC + "gtfs.zip";
    /** 只下載呢 5 個檔；stop_times.txt（約 50 MB）刻意唔落。 */
    public static final String[] FILES = {
            "routes.txt", "calendar.txt", "calendar_dates.txt",
            "trips.txt", "frequencies.txt"
    };
    public static final String LAST_UPDATED_FILE = "DATA_LAST_UPDATED_DATE.csv";
    public static final String SOURCE_LABEL = "運輸署 · 公共交通服務班次資料（GTFS）";
    /**
     * GOV_IN_ROUTE_UPDATE — 呢份政府班次資料而家係「線路資料更新」嘅正式
     * 來源之一：會有自己嘅 SourceStatus、計入 meta.json / Report / 設定頁
     * 「資料來源狀態」，而唔係暗中去下載。file key 唔同任何 DATASETS 檔名。
     */
    public static final String SOURCE_FILE = "service_hours_gtfs";

    private GovServiceHours() {
    }

    /** 一段官方班次間隔。 */
    public static final class Headway {
        public final String start;
        public final String end;
        public final long seconds;

        Headway(String start, String end, long seconds) {
            this.start = start;
            this.end = end;
            this.seconds = seconds;
        }

        /** "07:12–07:39 · 每 9 分鐘"；跨日時間由 hhmm() 標 (+1)。 */
        public String label() {
            long mins = Math.max(1L, Math.round(seconds / 60.0));
            return hhmm(start) + "–" + hhmm(end) + " · 每 " + mins + " 分鐘";
        }
    }

    /** 純決策：一次「更新線路資料」入面政府班次來源嘅結果，可離線測。 */
    public static final class SourceOutcome {
        /** 5 個核心 GTFS 檔全部下載成功。 */
        public final boolean ok;
        /** 成功寫入嘅檔數（包埋 DATA_LAST_UPDATED_DATE.csv）。 */
        public final int count;
        /** 失敗檔名；冇就空字串。如實報，唔捏造。 */
        public final String error;
        /** 一個核心檔都拎唔到 -> 當選用來源略過，唔當硬失敗。 */
        public final boolean skipped;

        SourceOutcome(boolean ok, int count, String error, boolean skipped) {
            this.ok = ok;
            this.count = count;
            this.error = error == null ? "" : error;
            this.skipped = skipped;
        }
    }

    /** okCore = 成功嘅 5 個核心檔數；okTotal = 連截止日期 CSV 在內成功檔數。 */
    public static SourceOutcome outcome(int okCore, int okTotal,
                                        List<String> failedNames) {
        List<String> failed = failedNames == null
                ? new ArrayList<String>() : failedNames;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < failed.size() && i < 3; i++) {
            if (b.length() > 0) {
                b.append("、");
            }
            b.append(failed.get(i));
        }
        String err = b.length() == 0 ? "" : "缺 " + b.toString()
                + (failed.size() > 3 ? " 等 " + failed.size() + " 檔" : "");
        return new SourceOutcome(okCore == FILES.length, okTotal, err,
                okCore == 0);
    }

    public static final class Info {
        public final boolean available;
        public final String reason;
        public final String lastUpdated;
        public final String serviceDays;
        public final String firstTrip;
        public final String lastTrip;
        public final String serviceStart;
        public final String serviceEnd;
        public final List<Headway> headways;
        public final int routeCount;
        public final int tripCount;
        public final int specialDates;

        Info(boolean available, String reason, String lastUpdated,
             String serviceDays, String firstTrip, String lastTrip,
             String serviceStart, String serviceEnd, List<Headway> headways,
             int routeCount, int tripCount, int specialDates) {
            this.available = available;
            this.reason = reason == null ? "" : reason;
            this.lastUpdated = lastUpdated == null ? "" : lastUpdated;
            this.serviceDays = serviceDays == null ? "" : serviceDays;
            this.firstTrip = firstTrip == null ? "" : firstTrip;
            this.lastTrip = lastTrip == null ? "" : lastTrip;
            this.serviceStart = serviceStart == null ? "" : serviceStart;
            this.serviceEnd = serviceEnd == null ? "" : serviceEnd;
            this.headways = headways == null ? new ArrayList<Headway>() : headways;
            this.routeCount = routeCount;
            this.tripCount = tripCount;
            this.specialDates = specialDates;
        }

        public static Info unavailable(String reason) {
            return new Info(false, reason, "", "", "", "", "", "",
                    new ArrayList<Headway>(), 0, 0, 0);
        }
    }

    /** 由本機快取讀取；缺任何一個核心檔就如實降級，唔捏造。 */
    public static Info lookup(File dir, String company, String route, String bound) {
        if (dir == null || !dir.isDirectory()) {
            return Info.unavailable("政府班次資料未載入（尚未建立本機快取）");
        }
        Map<String, String> texts = new HashMap<String, String>();
        for (String f : FILES) {
            File file = new File(dir, f);
            if (!file.isFile()) {
                return Info.unavailable("政府班次資料未載入（缺 " + f + "）");
            }
            String body = read(file);
            if (body == null || body.isEmpty()) {
                return Info.unavailable("政府班次資料未載入（" + f + " 空白）");
            }
            texts.put(f, body);
        }
        String updated = read(new File(dir, LAST_UPDATED_FILE));
        return parse(texts, company, route, bound,
                updated == null ? "" : updated.trim());
    }

    /** 純解析，供離線 harness 直接用真實官方片段測試。 */
    public static Info parse(Map<String, String> texts, String company,
                             String route, String bound, String lastUpdated) {
        String r = route == null ? "" : route.trim();
        String c = company == null ? "" : company.trim();
        if (r.isEmpty()) {
            return Info.unavailable("冇路線號");
        }
        List<Map<String, String>> routeRows = table(texts.get("routes.txt"));
        Set<String> routeIds = new LinkedHashSet<String>();
        for (Map<String, String> row : routeRows) {
            if (!r.equals(get(row, "route_short_name").trim())) {
                continue;
            }
            if (!agencyMatches(c, get(row, "agency_id"))) {
                continue;
            }
            routeIds.add(get(row, "route_id"));
        }
        // 公司完全對唔上，但官方有呢個路線號：如實講，唔靜靜撈第間公司。
        boolean anyRouteNumber = false;
        for (Map<String, String> row : routeRows) {
            if (r.equals(get(row, "route_short_name").trim())) {
                anyRouteNumber = true;
                break;
            }
        }
        if (routeIds.isEmpty()) {
            return Info.unavailable(anyRouteNumber
                    ? "政府班次資料有路線 " + r + "，但冇 " + (c.isEmpty() ? "本路線" : c)
                      + " 嘅 agency 記錄"
                    : "政府班次資料未有路線 " + r);
        }

        List<Map<String, String>> tripRows = table(texts.get("trips.txt"));
        Set<String> tripIds = new LinkedHashSet<String>();
        Set<String> serviceIds = new LinkedHashSet<String>();
        for (Map<String, String> row : tripRows) {
            if (routeIds.contains(get(row, "route_id"))) {
                tripIds.add(get(row, "trip_id"));
                serviceIds.add(get(row, "service_id"));
            }
        }
        if (tripIds.isEmpty()) {
            return Info.unavailable("政府班次資料有路線 " + r + "，但冇班次記錄");
        }

        // ---- 服務日（calendar.txt + calendar_dates.txt）----
        List<String> dayLabels = new ArrayList<String>();
        int special = 0;
        for (Map<String, String> row : table(texts.get("calendar.txt"))) {
            if (!serviceIds.contains(get(row, "service_id"))) {
                continue;
            }
            String label = weekdayLabel(row);
            if (!label.isEmpty() && !dayLabels.contains(label)) {
                dayLabels.add(label);
            }
        }
        for (Map<String, String> row : table(texts.get("calendar_dates.txt"))) {
            if (serviceIds.contains(get(row, "service_id"))) {
                special++;
            }
        }

        // ---- 班次間隔（frequencies.txt）----
        List<Headway> headways = new ArrayList<Headway>();
        String svcStart = "";
        String svcEnd = "";
        for (Map<String, String> row : table(texts.get("frequencies.txt"))) {
            if (!tripIds.contains(get(row, "trip_id"))) {
                continue;
            }
            String s = get(row, "start_time");
            String e = get(row, "end_time");
            long secs = parseLong(get(row, "headway_secs"), 0L);
            if (s.isEmpty() || e.isEmpty() || secs <= 0L) {
                continue;
            }
            headways.add(new Headway(s, e, secs));
            if (svcStart.isEmpty() || minutes(s) < minutes(svcStart)) {
                svcStart = s;
            }
            if (svcEnd.isEmpty() || minutes(e) > minutes(svcEnd)) {
                svcEnd = e;
            }
        }
        Collections.sort(headways, new Comparator<Headway>() {
            @Override
            public int compare(Headway a, Headway b) {
                return Long.compare(minutes(a.start), minutes(b.start));
            }
        });

        // ---- 頭班／尾班：官方 trip_id 尾段 HHMM ----
        String first = "";
        String last = "";
        int firstMin = Integer.MAX_VALUE;
        int lastMin = -1;
        for (String id : tripIds) {
            int m = tripDepartureMinutes(id);
            if (m < 0) {
                continue;
            }
            if (m < firstMin) {
                firstMin = m;
                first = id.substring(id.lastIndexOf('-') + 1);
            }
            if (m > lastMin) {
                lastMin = m;
                last = id.substring(id.lastIndexOf('-') + 1);
            }
        }

        return new Info(true, "", cleanDate(lastUpdated),
                joinLabels(dayLabels), hhmmCompact(first), hhmmCompact(last),
                hhmm(svcStart), hhmm(svcEnd), headways,
                routeIds.size(), tripIds.size(), special);
    }

    /** KMB API 嘅 "KMB" 官方上包埋 LWB；聯營 "KMB+CTB" 兩邊都要認。 */
    static boolean agencyMatches(String company, String agency) {
        String a = agency == null ? "" : agency.trim();
        if (a.isEmpty()) {
            return false;
        }
        if (company.isEmpty()) {
            return true;
        }
        if (EtaRepository.KMB.equals(company)) {
            return a.equals("KMB") || a.equals("LWB")
                    || a.contains("KMB") || a.contains("LWB");
        }
        if (EtaRepository.CTB.equals(company)) {
            return a.equals("CTB") || a.contains("CTB");
        }
        if (EtaRepository.NLB.equals(company)) {
            return a.equals("NLB");
        }
        if (EtaRepository.GMB.equals(company)) {
            return a.equals("GMB");
        }
        // MTR / LRT / MTRB / 渡輪等：資料集用緊另一套 agency id，如實唔配。
        return a.equalsIgnoreCase(company);
    }

    /** GTFS 時間 "25:35:00" -> "01:35 (+1)"；"05:35:00" -> "05:35"。 */
    public static String hhmm(String gtfsTime) {
        int m = minutes(gtfsTime);
        if (m < 0) {
            return gtfsTime == null ? "" : gtfsTime;
        }
        int day = m / 1440;
        int rest = m % 1440;
        String s = two(rest / 60) + ":" + two(rest % 60);
        return day > 0 ? s + " (+" + day + ")" : s;
    }

    /** trip_id 尾段 "0535" / "2410" -> "05:35" / "00:10 (+1)"。 */
    public static String hhmmCompact(String hhmm) {
        if (hhmm == null || hhmm.length() != 4) {
            return hhmm == null ? "" : hhmm;
        }
        try {
            int h = Integer.parseInt(hhmm.substring(0, 2));
            int m = Integer.parseInt(hhmm.substring(2, 4));
            int day = h / 24;
            int hh = h % 24;
            String s = two(hh) + ":" + two(m);
            return day > 0 ? s + " (+" + day + ")" : s;
        } catch (NumberFormatException e) {
            return hhmm;
        }
    }

    static int tripDepartureMinutes(String tripId) {
        if (tripId == null) {
            return -1;
        }
        int i = tripId.lastIndexOf('-');
        if (i < 0 || i + 5 != tripId.length()) {
            return -1;
        }
        String tail = tripId.substring(i + 1);
        try {
            int h = Integer.parseInt(tail.substring(0, 2));
            int m = Integer.parseInt(tail.substring(2, 4));
            return h * 60 + m;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static int minutes(String gtfsTime) {
        if (gtfsTime == null) {
            return -1;
        }
        String[] p = gtfsTime.trim().split(":");
        if (p.length < 2) {
            return -1;
        }
        try {
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String weekdayLabel(Map<String, String> row) {
        String[] keys = {"monday", "tuesday", "wednesday", "thursday",
                "friday", "saturday", "sunday"};
        String[] zh = {"一", "二", "三", "四", "五", "六", "日"};
        StringBuilder on = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            if ("1".equals(get(row, keys[i]).trim())) {
                on.append(zh[i]);
            }
        }
        if (on.length() == 0) {
            return "";
        }
        String s = on.toString();
        if (s.equals("一二三四五")) {
            return "星期一至五";
        }
        if (s.equals("六日")) {
            return "星期六、日";
        }
        if (s.equals("一二三四五六日")) {
            return "每天";
        }
        StringBuilder b = new StringBuilder("星期");
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) {
                b.append("、");
            }
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    private static String joinLabels(List<String> labels) {
        if (labels.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (String s : labels) {
            if (b.length() > 0) {
                b.append(" / ");
            }
            b.append(s);
        }
        return b.toString();
    }

    private static String cleanDate(String csv) {
        if (csv == null) {
            return "";
        }
        String[] lines = csv.replace("\r", "").split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String s = lines[i].replace("\uFEFF", "").trim();
            if (s.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                return s;
            }
        }
        return "";
    }

    /** 官方 GTFS 係 UTF-8 CSV，欄位可含引號／逗號；第一行為欄位名。 */
    public static List<Map<String, String>> table(String body) {
        List<Map<String, String>> out = new ArrayList<Map<String, String>>();
        if (body == null) {
            return out;
        }
        String[] lines = body.replace("\r", "").split("\n");
        if (lines.length < 2) {
            return out;
        }
        List<String> header = splitCsv(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty()) {
                continue;
            }
            List<String> cells = splitCsv(lines[i]);
            Map<String, String> row = new HashMap<String, String>();
            for (int k = 0; k < header.size() && k < cells.size(); k++) {
                row.put(header.get(k).replace("\uFEFF", "").trim(), cells.get(k));
            }
            out.add(row);
        }
        return out;
    }

    /** RFC4180 風格：支援 "a,b" 同 "" 轉義。 */
    public static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String get(Map<String, String> row, String key) {
        String v = row.get(key);
        return v == null ? "" : v;
    }

    private static long parseLong(String s, long fallback) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String two(int n) {
        return (n < 10 ? "0" : "") + n;
    }

    private static String read(File f) {
        if (!f.isFile()) {
            return null;
        }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                if (b.length() > 0) {
                    b.append('\n');
                }
                b.append(line);
            }
            return b.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * KMB_LWB_SPLIT — 九巴路線唔顯示龍運，龍運路線唔顯示九巴。
     *
     * 判定唯一來源：運輸署「公共交通服務班次資料」GTFS 嘅 routes.txt
     * `agency_id`（資料驅動，**冇硬編任何路線號清單**）：
     *   KMB / KMB+CTB      -> 九巴
     *   LWB / LWB+CTB      -> 龍運
     *   同一路線號同時出現兩邊、或官方冇記錄 -> UNKNOWN
     *
     * KMB 官方 /route/ 目錄同時收錄九巴同龍運路線，但**冇公司欄位**，
     * 所以唔可以靠 route 號前綴估（A/E/S/R/N 都會撞）。GTFS agency_id
     * 係官方唯一可靠嘅歸屬欄位。UNKNOWN 一律**唔顯示公司名**，唔會
     * 把九巴路線標成龍運、亦唔會把龍運路線標成九巴；政府快取未載入
     * 或特別路線（R／SP／PB 等）冇 GTFS 記錄時如實降級。
     *
     * 快取同「更新線路資料」共用：refreshGovServiceHours() 會 invalidate()，
     * 下次查詢由新落盤嘅 routes.txt 重建。純 Java、零 Android import、
     * 可離線測（loadFrom(File) 直接餵測試目錄）。
     */
    public static final class Brand {
        public static final String KMB = "KMB";
        public static final String LWB = "LWB";
        public static final String UNKNOWN = "";

        private static final Map<String, String> CACHE = new HashMap<String, String>();
        private static boolean loaded = false;
        private static int rows = 0;
        private static File overrideDir = null;

        private Brand() {
        }

        /** 純解析：route_short_name -> brand；衝突／無記錄一律 UNKNOWN。 */
        public static Map<String, String> index(String routesTxt) {
            Map<String, String> out = new HashMap<String, String>();
            Set<String> conflict = new HashSet<String>();
            for (Map<String, String> row : table(routesTxt)) {
                String name = row.get("route_short_name");
                String agency = row.get("agency_id");
                if (name == null || agency == null) {
                    continue;
                }
                String n = name.trim().toUpperCase(Locale.US);
                String b = ofAgency(agency);
                if (n.isEmpty() || b.isEmpty()) {
                    continue;
                }
                String prev = out.get(n);
                if (prev == null) {
                    out.put(n, b);
                } else if (!prev.equals(b)) {
                    conflict.add(n);
                }
            }
            for (String c : conflict) {
                out.put(c, UNKNOWN);
            }
            return out;
        }

        private static String ofAgency(String agency) {
            String a = agency == null ? "" : agency.trim().toUpperCase(Locale.US);
            if (a.equals("KMB") || a.equals("KMB+CTB")) {
                return KMB;
            }
            if (a.equals("LWB") || a.equals("LWB+CTB")) {
                return LWB;
            }
            return UNKNOWN;
        }

        /** GTFS 更新完要清快取，唔好繼續用上一份 routes.txt。 */
        public static synchronized void invalidate() {
            CACHE.clear();
            loaded = false;
            rows = 0;
            overrideDir = null;
        }

        /** 離線 harness 用：直接餵一個含 routes.txt 嘅目錄。 */
        public static synchronized void loadFrom(File dir) {
            invalidate();
            overrideDir = dir;
            ensure();
        }

        private static void ensure() {
            if (loaded) {
                return;
            }
            loaded = true;
            File dir = overrideDir != null ? overrideDir
                    : RouteDataStore.serviceHoursDir();
            if (dir == null) {
                return;
            }
            File f = new File(dir, "routes.txt");
            if (!f.isFile()) {
                return;
            }
            try {
                String body = read(f);
                if (body == null || body.isEmpty()) {
                    return;
                }
                Map<String, String> m = index(body);
                CACHE.putAll(m);
                rows = m.size();
            } catch (Exception ignored) {
                // 快取壞咗就當未載入，唔捏造歸屬。
            }
        }

        public static synchronized String brand(String route) {
            if (route == null) {
                return UNKNOWN;
            }
            ensure();
            String b = CACHE.get(route.trim().toUpperCase(Locale.US));
            return b == null ? UNKNOWN : b;
        }

        public static synchronized boolean hasData() {
            ensure();
            return rows > 0;
        }

        /**
         * STRICT_OPERATOR_SEPARATION / 用家選擇「保留混合聯營標籤」：
         * 真聯營線先可以有兩間公司名，而且要按官方 GTFS 講清楚係
         * 「城巴/九巴」定「城巴/龍運」；判唔到就只寫「聯營路線」，
         * **唔會靜靜用「城巴/九巴」把龍運聯營線標錯做九巴**。
         */
        public static String jointLabel(String route) {
            String b = brand(route);
            if (LWB.equals(b)) {
                return "城巴/龍運";
            }
            if (KMB.equals(b)) {
                return JointRoutes.JOINT_LABEL;      // 城巴/九巴
            }
            return "聯營路線";                       // UNKNOWN：唔估公司
        }

        public static String jointNote(String route) {
            String label = jointLabel(route);
            return "聯營路線".equals(label)
                    ? label : "聯營路線 · " + label;
        }

        /** ROUTE 級標籤：單一公司路線只寫自己，永遠唔撈第二間公司名。 */
        public static String label(String company, String route) {
            if (JointRoutes.isJointCompany(company)) {
                return jointLabel(route);
            }
            if (EtaRepository.KMB.equals(company)) {
                String b = brand(route);
                if (KMB.equals(b)) {
                    return "九巴";
                }
                if (LWB.equals(b)) {
                    return "龍運";
                }
                return UNKNOWN;
            }
            return JointRoutes.operatorLabel(company);
        }

        /** 搜尋列左側細 badge；UNKNOWN 留空或用中性 J/C，唔估。 */
        public static String badge(String company, String route) {
            if (JointRoutes.isJointCompany(company)) {
                String b = brand(route);
                if (LWB.equals(b)) {
                    return "L/C";
                }
                if (KMB.equals(b)) {
                    return "K/C";
                }
                return "J/C";
            }
            if (EtaRepository.KMB.equals(company)) {
                return brand(route);
            }
            if (EtaRepository.CTB.equals(company)) {
                return "CTB";
            }
            if (EtaRepository.NLB.equals(company)) {
                return "NLB";
            }
            if (EtaRepository.GMB.equals(company)) {
                return "GMB";
            }
            if (EtaRepository.MTR.equals(company)) {
                return "MTR";
            }
            if (EtaRepository.LRT.equals(company)) {
                return "LRT";
            }
            if (EtaRepository.MTRB.equals(company)) {
                return "MTRB";
            }
            return company == null ? UNKNOWN : company;
        }

        /** 逐班次「該班次：…」；判唔到歸屬就留空，唔估九巴／龍運。 */
        public static String tripLabel(String company, String route) {
            String b = brand(route);
            if (EtaRepository.KMB.equals(company)) {
                if (LWB.equals(b)) {
                    return "龍運";
                }
                if (KMB.equals(b)) {
                    return "九巴";
                }
                return UNKNOWN;                     // 特別路線：唔顯示公司名
            }
            if (JointRoutes.isJointCompany(company)) {
                return LWB.equals(b) ? "龍運" : UNKNOWN;
            }
            return JointRoutes.operatorLabel(company);
        }

        /** 循環線 badge；龍運循環線唔再寫「九巴循環線」。 */
        public static String circularStyle(String company, String route) {
            if (EtaRepository.KMB.equals(company)) {
                String b = brand(route);
                if (LWB.equals(b)) {
                    return "龍運循環線";
                }
                if (KMB.equals(b)) {
                    return CircularRoutes.KMB_STYLE;
                }
                return "循環線";
            }
            return CircularRoutes.styleLabel(company);
        }
    }
}
