package be.alterra.morphehelper;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Small persisted list of successful hand-offs. File paths are advisory: the
 * APK may be removed by retention, so the Downloads tab resolves the current
 * file separately when it opens an entry.
 */
final class DownloadHistory {

    private static final String PREFS = "download_history";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_ENTRIES = 40;

    private DownloadHistory() {
    }

    static final class Entry {
        final String id;
        final String appName;
        final String packageName;
        final String version;
        final String fileType;
        final String source;
        final String fileName;
        final String filePath;
        final long size;
        final long timestamp;

        Entry(String id, String appName, String packageName, String version,
              String fileType, String source, String fileName, String filePath,
              long size, long timestamp) {
            this.id = id;
            this.appName = appName;
            this.packageName = packageName;
            this.version = version;
            this.fileType = fileType;
            this.source = source;
            this.fileName = fileName;
            this.filePath = filePath;
            this.size = size;
            this.timestamp = timestamp;
        }

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("id", id);
                object.put("app_name", appName);
                object.put("package", packageName);
                object.put("version", version);
                object.put("file_type", fileType);
                object.put("source", source);
                object.put("file_name", fileName);
                object.put("file_path", filePath);
                object.put("size", size);
                object.put("timestamp", timestamp);
            } catch (Exception ignored) {
                // Values are plain strings and numbers; serialization cannot fail.
            }
            return object;
        }

        static Entry fromJson(JSONObject object) {
            if (object == null) {
                return null;
            }
            String fileName = object.optString("file_name");
            if (fileName.isEmpty()) {
                return null;
            }
            return new Entry(
                    object.optString("id"),
                    object.optString("app_name"),
                    object.optString("package"),
                    object.optString("version"),
                    object.optString("file_type"),
                    object.optString("source"),
                    fileName,
                    object.optString("file_path"),
                    object.optLong("size"),
                    object.optLong("timestamp"));
        }
    }

    static synchronized void record(Context context, String appName, String pkg,
                                    String version, String fileType, String source,
                                    File file) {
        if (file == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String fileName = file.getName();
        String id = now + "-" + Math.abs((pkg + "|" + fileName).hashCode());
        Entry entry = new Entry(id,
                appName == null ? "" : appName,
                pkg == null ? "" : pkg,
                version == null ? "" : version,
                fileType == null ? "" : fileType,
                source == null ? "" : source,
                fileName,
                file.getAbsolutePath(),
                file.length(),
                now);

        JSONArray previous = read(context);
        JSONArray next = new JSONArray();
        next.put(entry.toJson());
        for (int i = 0; i < previous.length() && next.length() < MAX_ENTRIES; i++) {
            Entry old = Entry.fromJson(previous.optJSONObject(i));
            if (old == null || old.id.equals(entry.id)) {
                continue;
            }
            if (old.fileName.equals(entry.fileName)
                    && old.packageName.equals(entry.packageName)) {
                continue;
            }
            next.put(previous.optJSONObject(i));
        }
        prefs(context).edit().putString(KEY_ENTRIES, next.toString()).apply();
    }

    static synchronized List<Entry> list(Context context) {
        JSONArray array = read(context);
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Entry entry = Entry.fromJson(array.optJSONObject(i));
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    static synchronized void remove(Context context, String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        JSONArray previous = read(context);
        JSONArray next = new JSONArray();
        for (int i = 0; i < previous.length(); i++) {
            Entry entry = Entry.fromJson(previous.optJSONObject(i));
            if (entry == null || entry.id.equals(id)) {
                continue;
            }
            next.put(previous.optJSONObject(i));
        }
        prefs(context).edit().putString(KEY_ENTRIES, next.toString()).apply();
    }

    static synchronized void clear(Context context) {
        prefs(context).edit().remove(KEY_ENTRIES).apply();
    }

    private static JSONArray read(Context context) {
        String raw = prefs(context).getString(KEY_ENTRIES, "");
        if (raw == null || raw.isEmpty()) {
            return new JSONArray();
        }
        try {
            return new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
