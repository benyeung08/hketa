package hk.eta.app.api;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Tiny HTTPS + org.json helper. No third-party networking library needed.
 *
 * Two real-world failure modes are handled here (both seen in the field):
 *  1. DATA.GOV.HK / KMB edge nodes sometimes answer 403 with an HTML page
 *     instead of JSON, depending on the User-Agent. We retry with a browser
 *     User-Agent and with trailing-slash URL variants.
 *  2. Some WAFs answer HTTP 200 but serve an HTML error page. Any response
 *     whose trimmed body starts with '<' is treated as a failure so it never
 *     gets written into a local snapshot as if it were JSON.
 */
public final class HttpJson {

    private static final int TIMEOUT_MS = 15000;
    private static final int SINGLE_STOP_TIMEOUT_MS = 7000;
    private static final String USER_AGENT = "hketa-android/1.0 (DATA.GOV.HK open data)";
    private static final String USER_AGENT_BROWSER =
            "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private HttpJson() {
    }

    public static JSONObject getObject(String url) throws IOException, JSONException {
        return new JSONObject(getFirst(url));
    }

    public static String get(String url) throws IOException {
        return get(url, USER_AGENT, TIMEOUT_MS, "application/json, text/plain, */*");
    }

    /** GET with an explicit User-Agent / timeout / Accept header. */
    public static String get(String url, String userAgent, int timeoutMs, String accept)
            throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", accept);
            conn.setRequestProperty("User-Agent", userAgent);
            conn.setRequestProperty("Accept-Language", "zh-HK,zh;q=0.9,en;q=0.8");
            conn.setRequestProperty("Connection", "close");
            return responseBody(conn, url);
        } finally {
            conn.disconnect();
        }
    }

    /** Plain-text GET for the MTR open-data CSV catalogues. */
    public static String getText(String url) throws IOException {
        return get(url, USER_AGENT, TIMEOUT_MS, "text/csv, text/plain, */*");
    }

    /**
     * Tries every URL with the app UA first, then a browser UA. Returns the
     * first response that is non-empty and does not look like an HTML page.
     */
    public static String getFirst(String... urls) throws IOException {
        return first(urls, false, TIMEOUT_MS);
    }

    /** Same as getFirst(), but with the CSV/text Accept header. */
    public static String getFirstText(String... urls) throws IOException {
        return first(urls, true, TIMEOUT_MS);
    }

    /**
     * One official single-stop lookup. Short timeout so a route with many
     * stops never blocks the UI for long when the operator is unreachable.
     */
    public static JSONObject singleStop(String url) throws IOException, JSONException {
        String body = first(new String[]{url}, false, SINGLE_STOP_TIMEOUT_MS);
        return new JSONObject(body);
    }

    private static String first(String[] urls, boolean text, int timeoutMs)
            throws IOException {
        IOException last = null;
        if (urls != null) {
            for (String url : urls) {
                if (url == null || url.isEmpty()) {
                    continue;
                }
                String accept = text
                        ? "text/csv, text/plain, */*"
                        : "application/json, text/plain, */*";
                for (String ua : new String[]{USER_AGENT, USER_AGENT_BROWSER}) {
                    try {
                        String body = get(url, ua, timeoutMs, accept);
                        String trimmed = body == null ? "" : body.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("<")) {
                            throw new IOException("非預期 HTML／空回應 @ " + url);
                        }
                        return body;
                    } catch (IOException e) {
                        last = e;
                    }
                }
            }
        }
        throw last == null ? new IOException("沒有可用的官方端點") : last;
    }

    /** MTR Bus is one of the few open-data endpoints that requires POST + JSON body. */
    public static JSONObject postJson(String url, String body) throws IOException, JSONException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            OutputStream out = conn.getOutputStream();
            try {
                out.write(body.getBytes("UTF-8"));
            } finally {
                out.close();
            }
            return new JSONObject(responseBody(conn, url));
        } finally {
            conn.disconnect();
        }
    }

    /** GitHub REST API GET: requires a JSON Accept header and an API version. */
    public static JSONObject getGitHub(String url) throws IOException, JSONException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            return new JSONObject(responseBody(conn, url));
        } finally {
            conn.disconnect();
        }
    }

    private static String responseBody(HttpURLConnection conn, String url) throws IOException {
        int code = conn.getResponseCode();
        InputStream stream = (code >= 200 && code < 300)
                ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(stream);
        if (code < 200 || code >= 300) {
            // Keep the first line only: a WAF HTML page is huge and useless in log.
            String head = body.trim();
            int nl = head.indexOf('\n');
            if (nl > 0) {
                head = head.substring(0, nl);
            }
            if (head.length() > 240) {
                head = head.substring(0, 240) + "…";
            }
            throw new IOException("HTTP " + code + " @ " + url + " -> " + head);
        }
        return body;
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } finally {
            reader.close();
        }
        return sb.toString();
    }

    /** Encodes one URL path segment (space becomes %20, not +). */
    public static String seg(String value) {
        String raw = value == null ? "" : value.trim();
        try {
            return java.net.URLEncoder.encode(raw, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return raw;
        }
    }

    /** Safe string read: missing / null / "null" all become "". */
    public static String str(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) {
            return "";
        }
        String v = o.optString(key, "");
        if (v == null || "null".equalsIgnoreCase(v)) {
            return "";
        }
        return v;
    }

    public static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    public static int intOr(JSONObject o, String key, int fallback) {
        if (o == null || o.isNull(key)) {
            return fallback;
        }
        try {
            return Integer.parseInt(str(o, key).trim());
        } catch (NumberFormatException e) {
            return o.optInt(key, fallback);
        }
    }
}
