package hk.eta.app.api;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the timestamps returned by the Hong Kong open-data APIs. */
public final class TimeUtils {

    private static final String HKT = "Asia/Hong_Kong";
    private static final Pattern DIGITS = Pattern.compile("(\\d+)");

    private TimeUtils() {
    }

    /**
     * ISO-8601 examples:
     *   2021-03-04T17:25:51+08:00
     *   2020-12-28T15:20:00.000+08:00
     *   2021-03-04T09:25:51Z
     *   2021-03-04T17:25:51 (assumed HKT)
     */
    public static long parseHkIso(String iso) {
        if (iso == null || iso.isEmpty()) {
            return 0L;
        }
        String s = iso.trim();
        if (s.endsWith("Z")) {
            s = s.substring(0, s.length() - 1) + "+0000";
        } else {
            s = s.replaceFirst("([+-]\\d{2}):(\\d{2})$", "$1$2");
        }
        s = s.replaceFirst("\\.\\d+", "");

        try {
            if (s.matches(".*[+-]\\d{4}$")) {
                SimpleDateFormat f =
                        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
                f.setTimeZone(TimeZone.getTimeZone(HKT));
                return f.parse(s).getTime();
            }
            SimpleDateFormat f =
                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone(HKT));
            return f.parse(s).getTime();
        } catch (ParseException | IllegalArgumentException e) {
            return 0L;
        }
    }

    /** "yyyy-MM-dd HH:mm:ss" / "yyyy/MM/dd HH:mm" used by NLB and MTR. */
    public static long parseHkCompact(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        String s = value.trim().replace('/', '-').replace('T', ' ');
        s = s.replaceFirst("\\.\\d+", "");
        String[] formats = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"};
        for (String format : formats) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(format, Locale.US);
                f.setTimeZone(TimeZone.getTimeZone(HKT));
                return f.parse(s).getTime();
            } catch (ParseException | IllegalArgumentException ignored) {
                // try next format
            }
        }
        return 0L;
    }

    /** "5 分鐘" / "5 min" / "-" -> minutes; "-" and unparsable text -> 0. */
    public static int minutesFromText(String text) {
        if (text == null) {
            return 0;
        }
        Matcher m = DIGITS.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    public static long nowPlusSeconds(String seconds) {
        try {
            long v = Long.parseLong(seconds == null ? "0" : seconds.trim());
            // MTR Bus uses 108000 as a "no arrival estimate" sentinel.
            if (v <= 0L || v >= 100000L) {
                return 0L;
            }
            return System.currentTimeMillis() + v * 1000L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static long nowPlusMinutes(int minutes) {
        return System.currentTimeMillis() + Math.max(0, minutes) * 60_000L;
    }

    public static String hhmm(long millis) {
        if (millis <= 0L) {
            return "--:--";
        }
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone(HKT));
        return f.format(new Date(millis));
    }
}
