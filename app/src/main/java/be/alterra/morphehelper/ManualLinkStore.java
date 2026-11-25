package be.alterra.morphehelper;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * User-defined direct download links, keyed by Android package name. A link is
 * only considered for the exact package Morphe requests.
 */
final class ManualLinkStore {

    /** Synthetic source key used only while a user manual link is being tried. */
    static final String SOURCE_KEY = "manual_link";

    static final class Entry {
        final String packageName;
        final String label;
        final String url;
        final boolean preferFirst;
        final boolean enabled;
        final long addedAt;

        Entry(String packageName, String label, String url, long addedAt,
              boolean preferFirst, boolean enabled) {
            this.packageName = packageName;
            this.label = label;
            this.url = url;
            this.preferFirst = preferFirst;
            this.enabled = enabled;
            this.addedAt = addedAt;
        }
    }

    private static final String PREFS = "manual_links";
    private static final String PREFIX = "app:";

    private ManualLinkStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Entry get(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return null;
        }
        String key = PREFIX + packageName.trim();
        return parse(packageName.trim(), prefs(context).getString(key, null));
    }

    static List<Entry> all(Context context) {
        Map<String, ?> values = prefs(context).getAll();
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, ?> item : values.entrySet()) {
            if (!item.getKey().startsWith(PREFIX) || !(item.getValue() instanceof String)) {
                continue;
            }
            String packageName = item.getKey().substring(PREFIX.length());
            Entry entry = parse(packageName, (String) item.getValue());
            if (entry != null) {
                entries.add(entry);
            }
        }
        Collections.sort(entries, new Comparator<Entry>() {
            @Override
            public int compare(Entry left, Entry right) {
                int byLabel = left.label.toLowerCase(Locale.US)
                        .compareTo(right.label.toLowerCase(Locale.US));
                return byLabel != 0
                        ? byLabel : left.packageName.compareTo(right.packageName);
            }
        });
        return entries;
    }

    /** Stores one mapping. Returns false when the URL is not usable. */
    static boolean put(Context context, String packageName, String url, String label,
                       boolean preferFirst, boolean enabled) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        String normalizedUrl = normalizeUrl(url);
        if (normalizedUrl.isEmpty()) {
            return false;
        }
        String cleanedPackage = packageName.trim();
        String value = toJson(cleanedPackage, normalizedUrl, label,
                System.currentTimeMillis(), preferFirst, enabled);
        if (value == null) {
            return false;
        }
        prefs(context).edit().putString(PREFIX + cleanedPackage, value).apply();
        return true;
    }

    /** Replaces every manual link with the imported list. */
    static void replaceAll(Context context, List<Entry> entries) {
        SharedPreferences prefs = prefs(context);
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (Entry entry : entries) {
            String value = toJson(entry.packageName, normalizeUrl(entry.url), entry.label,
                    System.currentTimeMillis(), entry.preferFirst, entry.enabled);
            if (value != null) {
                editor.putString(PREFIX + entry.packageName, value);
            }
        }
        editor.apply();
    }

    static void remove(Context context, String packageName) {
        if (packageName == null) {
            return;
        }
        prefs(context).edit().remove(PREFIX + packageName.trim()).apply();
    }

    /**
     * Accepts bare hosts, protocol-relative links, and https URLs. Returns an
     * empty string for anything that is not a usable network URL. Plain http is
     * rejected because Android blocks cleartext downloads for this app.
     */
    static String normalizeUrl(String value) {
        if (value == null) {
            return "";
        }
        String url = value.trim();
        if (url.isEmpty() || url.contains(" ")) {
            return "";
        }
        if (url.startsWith("//")) {
            url = "https:" + url;
        } else if (!url.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            url = "https://" + url;
        }
        try {
            URI parsed = new URI(url);
            if (!"https".equalsIgnoreCase(parsed.getScheme())) {
                return "";
            }
            if (parsed.getHost() == null || parsed.getHost().trim().isEmpty()) {
                return "";
            }
            return url;
        } catch (URISyntaxException ignored) {
            return "";
        }
    }

    private static String toJson(String packageName, String url, String label, long addedAt,
                                 boolean preferFirst, boolean enabled) {
        try {
            JSONObject value = new JSONObject();
            value.put("url", url);
            value.put("label", label == null || label.trim().isEmpty()
                    ? packageName : label.trim());
            value.put("addedAt", addedAt);
            value.put("preferFirst", preferFirst);
            value.put("enabled", enabled);
            return value.toString();
        } catch (JSONException ignored) {
            return null;
        }
    }

    private static Entry parse(String packageName, String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            JSONObject value = new JSONObject(raw);
            String url = normalizeUrl(value.optString("url", ""));
            if (url.isEmpty()) {
                return null;
            }
            String label = value.optString("label", packageName);
            if (label.trim().isEmpty()) {
                label = packageName;
            }
            long addedAt = value.optLong("addedAt", 0);
            boolean preferFirst = value.optBoolean("preferFirst", true);
            boolean enabled = value.optBoolean("enabled", true);
            return new Entry(packageName, label, url, addedAt, preferFirst, enabled);
        } catch (JSONException ignored) {
            return null;
        }
    }
}
