package be.alterra.morphehelper;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.text.format.DateUtils;

/** Last-known health of each download source. */
final class SourceHealth {

    private static final String PREFS = "source_health";

    private SourceHealth() {
    }

    static void record(Context context, String key, boolean ok, String message) {
        String value = (ok ? "1" : "0") + "|" + System.currentTimeMillis() + "|"
                + (message == null ? "" : message);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(key, value).apply();
    }

    static String summary(Context context, String key) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(key, null);
        if (TextUtils.isEmpty(raw)) {
            return "Not tested yet";
        }
        String[] parts = raw.split("\\|", 3);
        if (parts.length < 2) {
            return "Not tested yet";
        }
        boolean ok = "1".equals(parts[0]);
        long when;
        try {
            when = Long.parseLong(parts[1]);
        } catch (Exception ignored) {
            when = System.currentTimeMillis();
        }
        String ago = DateUtils.getRelativeTimeSpanString(
                when, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString();
        String detail = parts.length > 2 ? parts[2] : "";
        return (ok ? "OK" : "Failed") + (TextUtils.isEmpty(detail) ? "" : " (" + detail + ")")
                + " \u2022 " + ago;
    }
}
