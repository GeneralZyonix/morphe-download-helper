package be.alterra.morphehelper;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class GitHubAppStore {

    static final class Entry {
        final String packageName;
        final String repo;
        final String label;
        final long addedAt;

        Entry(String packageName, String repo, String label, long addedAt) {
            this.packageName = packageName;
            this.repo = repo;
            this.label = label;
            this.addedAt = addedAt;
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
        Collections.sort(entries, Comparator.comparing(entry -> entry.label.toLowerCase()));
        return entries;
    }

    static void put(Context context, String packageName, String repo, String label) {
        try {
            JSONObject value = new JSONObject();
            value.put("repo", repo);
            value.put("label", label == null || label.trim().isEmpty() ? packageName : label);
            value.put("addedAt", System.currentTimeMillis());
            prefs(context).edit().putString(PREFIX + packageName, value.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    static void remove(Context context, String packageName) {
        prefs(context).edit().remove(PREFIX + packageName).apply();
    }

    private static Entry parse(String packageName, String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            JSONObject value = new JSONObject(raw);
            String repo = value.optString("repo", "");
            if (repo.isEmpty()) {
                return null;
            }
            String label = value.optString("label", packageName);
            long addedAt = value.optLong("addedAt", 0);
            return new Entry(packageName, repo, label, addedAt);
        } catch (JSONException ignored) {
            return null;
        }
    }
}

