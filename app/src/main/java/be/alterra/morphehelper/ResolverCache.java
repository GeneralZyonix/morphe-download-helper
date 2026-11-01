package be.alterra.morphehelper;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

/** Short-lived cache of Morphe resolver links. */
final class ResolverCache {

    private static final String PREFS = "resolver_cache";
    private static final long TTL_MS = 3 * 60 * 60 * 1000L;

    private ResolverCache() {
    }

    static String get(Context context, String key) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(key, null);
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        int split = raw.indexOf('|');
        if (split <= 0) {
            return null;
        }
        try {
            long saved = Long.parseLong(raw.substring(0, split));
            if (System.currentTimeMillis() - saved > TTL_MS) {
                prefs.edit().remove(key).apply();
                return null;
            }
        } catch (Exception ignored) {
            return null;
        }
        return raw.substring(split + 1);
    }

    static void put(Context context, String key, String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(key, System.currentTimeMillis() + "|" + url)
                .apply();
    }
}
