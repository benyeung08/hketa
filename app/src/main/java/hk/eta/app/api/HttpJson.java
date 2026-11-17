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

/** Tiny HTTPS + org.json helper. No third-party networking library needed. */
public final class HttpJson {

    private static final int TIMEOUT_MS = 15000;
    private static final String USER_AGENT = "hketa-android/1.0 (DATA.GOV.HK open data)";

    private HttpJson() {
    }

    public static JSONObject getObject(String url) throws IOException, JSONException {
        return new JSONObject(get(url));
    }

    public static String get(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            return responseBody(conn, url);
        } finally {
            conn.disconnect();
        }
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

    /** Plain-text GET for the MTR open-data CSV catalogues. */
    public static String getText(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", "text/csv, text/plain, */*");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            return responseBody(conn, url);
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
            throw new IOException("HTTP " + code + " @ " + url + " -> " + body);
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
