package hk.eta.app.api;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * In-app application update: reads the latest GitHub Release and downloads its APK.
 *
 * Endpoint: GET https://api.github.com/repos/{owner}/{repo}/releases/latest
 * (unauthenticated: 60 requests / hour per IP). Only HTTPS URLs on GitHub's own
 * release-asset hosts are accepted, and the APK is written into the app's private
 * filesDir so a FileProvider can expose it safely (no world-writable storage).
 *
 * Real-time ETA and route-catalogue code are completely unaffected by this class.
 */
public final class AppUpdate {

    private static final String API = "https://api.github.com/repos/";
    private static final String LATEST = "/releases/latest";
    private static final long MAX_APK_BYTES = 120L * 1024L * 1024L;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;

    private static final String[] SAFE_HOSTS = {
            "api.github.com",
            "github.com",
            "www.github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com"
    };

    private AppUpdate() {
    }

    public static final class Release {
        public final String tag;
        public final String name;
        public final String notes;
        public final String htmlUrl;
        public final String publishedAt;
        public final boolean prerelease;
        public final String apkName;
        public final String apkUrl;
        public final long apkSize;

        Release(String tag, String name, String notes, String htmlUrl,
                String publishedAt, boolean prerelease,
                String apkName, String apkUrl, long apkSize) {
            this.tag = tag;
            this.name = name;
            this.notes = notes;
            this.htmlUrl = htmlUrl;
            this.publishedAt = publishedAt;
            this.prerelease = prerelease;
            this.apkName = apkName;
            this.apkUrl = apkUrl;
            this.apkSize = apkSize;
        }

        public boolean hasApk() {
            return !apkUrl.isEmpty();
        }
    }

    public interface Progress {
        void onProgress(long downloaded, long total);
    }

    /** Validates "owner/repo" so it can never escape the api.github.com path. */
    public static boolean validRepo(String ownerRepo) {
        if (ownerRepo == null) {
            return false;
        }
        return ownerRepo.trim().matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    }

    public static Release latest(String ownerRepo) throws IOException, org.json.JSONException {
        String repo = ownerRepo == null ? "" : ownerRepo.trim();
        if (!validRepo(repo)) {
            throw new IOException("請填寫正確的 GitHub 倉庫格式，例如 username/hketa");
        }
        String url = API + repo + LATEST;
        JSONObject root = HttpJson.getGitHub(url);

        // 404 / rate-limit responses carry a "message" instead of a release.
        if (!root.has("tag_name") && root.has("message")) {
            throw new IOException("GitHub：" + HttpJson.str(root, "message"));
        }

        String tag = HttpJson.str(root, "tag_name");
        String name = HttpJson.firstNonEmpty(HttpJson.str(root, "name"), tag);
        String notes = HttpJson.str(root, "body");
        String htmlUrl = HttpJson.str(root, "html_url");
        String publishedAt = HttpJson.str(root, "published_at");
        boolean prerelease = root.optBoolean("prerelease", false);

        String apkName = "";
        String apkUrl = "";
        long apkSize = 0L;
        JSONArray assets = root.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a == null) {
                    continue;
                }
                String assetName = HttpJson.str(a, "name");
                String download = HttpJson.str(a, "browser_download_url");
                String contentType = HttpJson.str(a, "content_type");
                if (!assetName.toLowerCase(Locale.US).endsWith(".apk")) {
                    continue;
                }
                if (!"application/vnd.android.package-archive".equals(contentType)
                        && !contentType.isEmpty()
                        && !"application/octet-stream".equals(contentType)
                        && !"binary/octet-stream".equals(contentType)) {
                    continue;
                }
                if (!isSafeUrl(download)) {
                    continue;
                }
                apkName = assetName;
                apkUrl = download;
                apkSize = a.optLong("size", 0L);
                break;
            }
        }
        if (tag.isEmpty()) {
            throw new IOException("GitHub 回應沒有 tag_name");
        }
        return new Release(tag, name, notes, htmlUrl, publishedAt,
                prerelease, apkName, apkUrl, apkSize);
    }

    /**
     * Returns 1 when "latest" is newer than "current", 0 when equal, -1 when older.
     * Handles v-prefixes (v1.0.1), differing lengths (1.0 vs 1.0.0) and suffixes
     * (1.1.0-debug). Non-numeric tags are treated as 0 so they never trigger an update.
     */
    public static int compareVersions(String current, String latest) {
        String[] a = numericParts(current);
        String[] b = numericParts(latest);
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            long va = i < a.length ? leadingDigits(a[i]) : 0L;
            long vb = i < b.length ? leadingDigits(b[i]) : 0L;
            if (vb > va) {
                return 1;
            }
            if (vb < va) {
                return -1;
            }
        }
        return 0;
    }

    private static String[] numericParts(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        int dash = v.indexOf('-');
        if (dash >= 0) {
            v = v.substring(0, dash);
        }
        String cleaned = v.replaceAll("[^0-9.]", ".").replaceAll("\\.+", ".");
        if (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }
        return cleaned.isEmpty() ? new String[0] : cleaned.split("\\.");
    }

    private static long leadingDigits(String part) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < part.length() && Character.isDigit(part.charAt(i)); i++) {
            digits.append(part.charAt(i));
        }
        if (digits.length() == 0) {
            return 0L;
        }
        try {
            return Long.parseLong(digits.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static boolean isSafeUrl(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        try {
            URL url = new URL(raw);
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                return false;
            }
            String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.US);
            for (String allowed : SAFE_HOSTS) {
                if (host.equals(allowed)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Downloads the APK into dir, atomically. Redirects are followed only inside GitHub. */
    public static File download(String url, File dir, String fileName,
                                Progress progress) throws IOException {
        if (!isSafeUrl(url)) {
            throw new IOException("下載來源不是 GitHub HTTPS 網址，已拒絕");
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("無法建立更新目錄");
        }
        String safeName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!safeName.endsWith(".apk")) {
            safeName = safeName + ".apk";
        }
        File target = new File(dir, safeName);
        File tmp = new File(dir, safeName + ".tmp");

        String current = url;
        for (int hop = 0; hop < 6; hop++) {
            if (!isSafeUrl(current)) {
                throw new IOException("重定向到非 GitHub 網址，已拒絕");
            }
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setInstanceFollowRedirects(false);
            try {
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setUseCaches(false);
                conn.setRequestProperty("Accept",
                        "application/vnd.android.package-archive, application/octet-stream, */*");
                conn.setRequestProperty("User-Agent", "hketa-android/1.0 (GitHub Release)");
                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 303
                        || code == 307 || code == 308) {
                    String location = conn.getHeaderField("Location");
                    if (location == null || location.isEmpty()) {
                        throw new IOException("重定向沒有 Location");
                    }
                    if (location.startsWith("/")) {
                        location = "https://" + new URL(current).getHost() + location;
                    }
                    current = location;
                    continue;
                }
                if (code < 200 || code >= 300) {
                    throw new IOException("HTTP " + code + " 下載失敗");
                }
                long total = conn.getHeaderFieldLong("Content-Length", -1L);
                if (total > MAX_APK_BYTES) {
                    throw new IOException("APK 超過 120 MB，已拒絕");
                }
                return copyBody(conn, tmp, target, total, progress);
            } finally {
                conn.disconnect();
            }
        }
        throw new IOException("重定向次數過多");
    }

    private static File copyBody(HttpURLConnection conn, File tmp, File target,
                                 long total, Progress progress) throws IOException {
        InputStream in = conn.getInputStream();
        OutputStream out = new FileOutputStream(tmp);
        long copied = 0L;
        try {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) > 0) {
                copied += n;
                if (copied > MAX_APK_BYTES) {
                    throw new IOException("APK 超過 120 MB，已拒絕");
                }
                out.write(buffer, 0, n);
                if (progress != null) {
                    progress.onProgress(copied, total);
                }
            }
            out.flush();
        } finally {
            try {
                out.close();
            } catch (IOException ignored) {
                // ignore
            }
            try {
                in.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
        if (copied < 1024L) {
            tmp.delete();
            throw new IOException("下載內容過小，可能不是 APK");
        }
        if (!tmp.renameTo(target)) {
            tmp.delete();
            throw new IOException("寫入 APK 失敗");
        }
        return target;
    }

    public static String humanSize(long bytes) {
        if (bytes <= 0L) {
            return "大小未知";
        }
        if (bytes < 1024L * 1024L) {
            return (bytes / 1024L) + " KB";
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
