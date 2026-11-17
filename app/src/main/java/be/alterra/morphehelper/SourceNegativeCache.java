package be.alterra.morphehelper;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Short-lived memory of definitive "not found" results. It lets repeated
 * requests skip a source that cannot serve the package without repeating the
 * whole search. Retry actions bypass it, and entries expire automatically.
 */
final class SourceNegativeCache {

    private static final String PREFS = "source_negative_cache";
    private static final String KEY_ENTRIES = "entries";
    private static final long TTL_MS = 2L * 60L * 60L * 1000L;
    private static final int MAX_ENTRIES = 60;

    private SourceNegativeCache() {
    }

    static boolean isCacheableSource(String source) {
        return SourceSettings.APKMIRROR.equals(source)
                || SourceSettings.APKPURE.equals(source)
                || SourceSettings.APKCUBE.equals(source)
                || SourceSettings.UPTODOWN.equals(source)
                || SourceSettings.FDROID.equals(source)
                || SourceSettings.APTOIDE.equals(source)
                || SourceSettings.APPTEKA.equals(source);
    }

    /** Only cache failures that mean "this source does not have it". */
    static boolean isDefinitive(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.US);
        String[] transientMarkers = {"timeout", "timed out", "http 403", "http 429",
                "cloudflare", "blocked", "rate limit", "invalid json",
                "parse", "cancelled", "connection", "network", "unknown host",
                "ssl", "interrupted", "empty file", "checksum", "sha-256 mismatch"};
        for (String marker : transientMarkers) {
            if (lower.contains(marker)) {
                return false;
            }
        }
        String[] definitive = {"not found", "could not find", "couldn't find",
                "cannot find", "no matching", "no app", "no versions",
                "no split archive", "not available", "no release",
                "different package", "is for ", "http 404", "no results",
                "does not have", "isn't in the catalog", "not in the catalog",
                "not trusted"};
        for (String marker : definitive) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    static void record(Context context, String source, String pkg, String version,
                       long[] codes, String fileType, String reason) {
        if (!isCacheableSource(source) || !isDefinitive(reason)) {
            return;
        }
        String key = key(source, pkg, version, codes, fileType);
        if (key == null) {
            return;
        }
        synchronized (SourceNegativeCache.class) {
            try {
                JSONObject entries = read(context);
                prune(entries);
                JSONObject entry = new JSONObject();
                entry.put("reason", trim(reason));
                entry.put("time", System.currentTimeMillis());
                entries.put(key, entry);
                while (entries.length() > MAX_ENTRIES) {
                    Iterator<String> keys = entries.keys();
                    if (!keys.hasNext()) {
                        break;
                    }
                    entries.remove(keys.next());
                }
                write(context, entries);
            } catch (Exception error) {
                HelperLog.error("Could not record source negative cache", error);
            }
        }
    }

    static String get(Context context, String source, String pkg, String version,
                      long[] codes, String fileType) {
        if (!isCacheableSource(source)) {
            return null;
        }
        String key = key(source, pkg, version, codes, fileType);
        if (key == null) {
            return null;
        }
        synchronized (SourceNegativeCache.class) {
            try {
                JSONObject entries = read(context);
                long now = System.currentTimeMillis();
                JSONObject entry = entries.optJSONObject(key);
                if (entry != null) {
                    long time = entry.optLong("time", 0L);
                    if (now - time <= TTL_MS) {
                        return entry.optString("reason", "");
                    }
                    entries.remove(key);
                    write(context, entries);
                }
            } catch (Exception error) {
                HelperLog.error("Could not read source negative cache", error);
            }
        }
        return null;
    }

    static void clear(Context context) {
        prefs(context).edit().remove(KEY_ENTRIES).apply();
    }

    private static String key(String source, String pkg, String version,
                              long[] codes, String fileType) {
        if (pkg == null || pkg.isEmpty()) {
            return null;
        }
        boolean hasVersion = version != null && !version.trim().isEmpty()
                && !"any".equalsIgnoreCase(version.trim());
        boolean hasCodes = codes != null && codes.length > 0;
        if (!hasVersion && !hasCodes) {
            return null;
        }
        List<Long> sorted = new ArrayList<>();
        if (codes != null) {
            for (long code : codes) {
                sorted.add(code);
            }
        }
        sorted.sort(Long::compareTo);
        StringBuilder key = new StringBuilder();
        key.append(BuildConfig.VERSION_NAME).append('|')
                .append(source).append('|')
                .append(pkg).append('|')
                .append(normalize(version)).append('|');
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                key.append(',');
            }
            key.append(sorted.get(i));
        }
        key.append('|').append(normalize(fileType));
        return key.toString();
    }

    private static void prune(JSONObject entries) {
        long cutoff = System.currentTimeMillis() - TTL_MS;
        List<String> remove = new ArrayList<>();
        Iterator<String> keys = entries.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject entry = entries.optJSONObject(key);
            if (entry == null || entry.optLong("time", 0L) < cutoff) {
                remove.add(key);
            }
        }
        for (String key : remove) {
            entries.remove(key);
        }
    }

    private static JSONObject read(Context context) {
        String raw = prefs(context).getString(KEY_ENTRIES, "{}");
        try {
            return new JSONObject(raw);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static void write(Context context, JSONObject entries) {
        prefs(context).edit().putString(KEY_ENTRIES, entries.toString()).apply();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static String trim(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() > 180 ? text.substring(0, 177) + "..." : text;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
