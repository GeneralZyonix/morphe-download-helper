package be.alterra.morphehelper;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class GitHubAppStore {

    /** Synthetic source key used only while a user-added repo is being tried. */
    static final String SOURCE_KEY = "github_user";

    static final class Entry {
        final String packageName;
        final String repo;
        final String label;
        final long addedAt;
        final boolean preferFirst;
        final String assetFilter;
        final boolean manual;

        Entry(String packageName, String repo, String label, long addedAt,
              boolean preferFirst, String assetFilter, boolean manual) {
            this.packageName = packageName;
            this.repo = repo;
            this.label = label;
            this.addedAt = addedAt;
            this.preferFirst = preferFirst;
            this.assetFilter = assetFilter == null ? "" : assetFilter.trim();
            this.manual = manual;
        }
    }

    private static final String PREFS = "github_apps";
    private static final String PREFIX = "app:";

    private GitHubAppStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Entry get(Context context, String packageName) {
        String raw = prefs(context).getString(PREFIX + packageName, null);
        return parse(packageName, raw);
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
        Collections.sort(entries, Comparator.comparing(entry -> entry.label.toLowerCase(Locale.US)));
        return entries;
    }

    /** Keeps the old auto-remember behaviour for successful GitHub downloads. */
    static void put(Context context, String packageName, String repo, String label) {
        Entry existing = get(context, packageName);
        if (existing != null && existing.manual) {
            // Never replace a mapping the user entered by hand.
            return;
        }
        put(context, packageName, repo, label, false, "", false);
    }

    static void put(Context context, String packageName, String repo, String label,
                    boolean preferFirst, String assetFilter, boolean manual) {
        String value = toJson(packageName, repo, label, System.currentTimeMillis(),
                preferFirst, assetFilter, manual);
        if (value != null) {
            prefs(context).edit().putString(PREFIX + packageName, value).apply();
        }
    }

    /** Replaces every user-added mapping, keeping auto-remembered entries. */
    static void replaceManual(Context context, List<Entry> entries) {
        SharedPreferences prefs = prefs(context);
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, ?> item : prefs.getAll().entrySet()) {
            if (!item.getKey().startsWith(PREFIX) || !(item.getValue() instanceof String)) {
                continue;
            }
            Entry existing = parse(item.getKey().substring(PREFIX.length()),
                    (String) item.getValue());
            if (existing != null && existing.manual) {
                editor.remove(item.getKey());
            }
        }
        for (Entry entry : entries) {
            String value = toJson(entry.packageName, entry.repo, entry.label,
                    System.currentTimeMillis(), entry.preferFirst,
                    entry.assetFilter, true);
            if (value != null) {
                editor.putString(PREFIX + entry.packageName, value);
            }
        }
        editor.apply();
    }

    static void remove(Context context, String packageName) {
        prefs(context).edit().remove(PREFIX + packageName).apply();
    }

    /** Adds entries from the public settings file without deleting local ones. */
    static void mergeMissingManual(Context context, List<Entry> entries) {
        for (Entry entry : entries) {
            Entry existing = get(context, entry.packageName);
            if (existing == null || !existing.manual) {
                put(context, entry.packageName, entry.repo, entry.label,
                        entry.preferFirst, entry.assetFilter, true);
            }
        }
    }

    private static String toJson(String packageName, String repo, String label, long addedAt,
                                 boolean preferFirst, String assetFilter, boolean manual) {
        try {
            JSONObject value = new JSONObject();
            value.put("repo", normalizeRepo(repo));
            value.put("label", label == null || label.trim().isEmpty()
                    ? packageName : label.trim());
            value.put("addedAt", addedAt);
            value.put("preferFirst", preferFirst);
            value.put("assetFilter", assetFilter == null ? "" : assetFilter.trim());
            value.put("manual", manual);
            return value.toString();
        } catch (JSONException ignored) {
            return null;
        }
    }

    /** Accepts https://github.com/owner/repo, owner/repo, and release URLs. */
    static String normalizeRepo(String value) {
        if (value == null) {
            return "";
        }
        String repo = value.trim();
        if (repo.startsWith("https://github.com/")) {
            repo = repo.substring("https://github.com/".length());
        } else if (repo.startsWith("http://github.com/")) {
            repo = repo.substring("http://github.com/".length());
        } else if (repo.startsWith("github.com/")) {
            repo = repo.substring("github.com/".length());
        }
        int query = repo.indexOf('?');
        if (query >= 0) {
            repo = repo.substring(0, query);
        }
        int hash = repo.indexOf('#');
        if (hash >= 0) {
            repo = repo.substring(0, hash);
        }
        String[] parts = repo.split("/");
        if (parts.length < 2) {
            return "";
        }
        String owner = parts[0].trim();
        String name = parts[1].trim();
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        if (owner.isEmpty() || name.isEmpty()
                || !owner.matches("[A-Za-z0-9_.-]+")
                || !name.matches("[A-Za-z0-9_.-]+")) {
            return "";
        }
        return owner + "/" + name;
    }

    private static Entry parse(String packageName, String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            JSONObject value = new JSONObject(raw);
            String repo = normalizeRepo(value.optString("repo", ""));
            if (repo.isEmpty()) {
                return null;
            }
            String label = value.optString("label", packageName);
            long addedAt = value.optLong("addedAt", 0);
            boolean preferFirst = value.optBoolean("preferFirst", false);
            String assetFilter = value.optString("assetFilter", "");
            boolean manual = value.optBoolean("manual", false);
            return new Entry(packageName, repo, label, addedAt,
                    preferFirst, assetFilter, manual);
        } catch (JSONException ignored) {
            return null;
        }
    }
}
