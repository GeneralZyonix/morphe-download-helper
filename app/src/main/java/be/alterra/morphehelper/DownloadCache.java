package be.alterra.morphehelper;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Locale;

/**
 * Small index of files downloaded and validated by the helper. It allows an
 * exact repeat request to be served from the helper's own cache without
 * resolving or downloading the APK again.
 */
final class DownloadCache {

    private static final String PREFS = "download_cache";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_ENTRIES = 20;

    private DownloadCache() {
    }

    /** Records a validated file for an exact package/version/build request. */
    static void record(Context context, File file, String pkg, String version,
                       long[] codes, String fileType) {
        if (file == null || !file.isFile() || pkg == null || pkg.isEmpty()) {
            return;
        }
        String normalizedVersion = normalize(version);
        boolean hasVersion = !normalizedVersion.isEmpty()
                && !"any".equals(normalizedVersion);
        boolean hasCodes = codes != null && codes.length > 0;
        if (!hasVersion && !hasCodes) {
            return;
        }
        try {
            JSONArray existing = read(context);
            JSONArray updated = new JSONArray();
            for (int i = 0; i < existing.length(); i++) {
                JSONObject entry = existing.optJSONObject(i);
                if (entry == null || sameRequest(entry, pkg, normalizedVersion,
                        codes, fileType)) {
                    continue;
                }
                updated.put(entry);
            }
            JSONObject entry = new JSONObject();
            entry.put("pkg", pkg);
            entry.put("version", normalizedVersion);
            entry.put("codes", codesToJson(codes));
            entry.put("fileType", normalize(fileType));
            entry.put("path", file.getAbsolutePath());
            entry.put("size", file.length());
            entry.put("time", System.currentTimeMillis());
            updated.put(entry);
            while (updated.length() > MAX_ENTRIES) {
                updated.remove(0);
            }
            write(context, updated);
            HelperLog.info("Cached validated download " + file.getName());
        } catch (Exception error) {
            HelperLog.error("Could not update download cache", error);
        }
    }

    /**
     * Returns an exact validated cached file, or null. Entries whose file is
     * missing or no longer matches the request are dropped.
     */
    static File find(Context context, String pkg, String version, long[] codes,
                     String fileType) {
        if (pkg == null || pkg.isEmpty()) {
            return null;
        }
        String normalizedVersion = normalize(version);
        boolean hasVersion = !normalizedVersion.isEmpty()
                && !"any".equals(normalizedVersion);
        boolean hasCodes = codes != null && codes.length > 0;
        if (!hasVersion && !hasCodes) {
            return null;
        }
        try {
            JSONArray entries = read(context);
            JSONArray keep = new JSONArray();
            File found = null;
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                File file = new File(entry.optString("path", ""));
                if (!file.isFile() || file.length() != entry.optLong("size", -1)) {
                    continue;
                }
                if (found == null && matchesRequest(
                        entry, pkg, normalizedVersion, codes, fileType)) {
                    try {
                        DownloadedFileValidator.validate(
                                context, file, pkg, version, codes);
                        found = file;
                        keep.put(entry);
                        continue;
                    } catch (Exception invalid) {
                        HelperLog.info("Dropping invalid cached file "
                                + file.getName() + ": " + invalid.getMessage());
                        continue;
                    }
                }
                keep.put(entry);
            }
            write(context, keep);
            return found;
        } catch (Exception error) {
            HelperLog.error("Could not read download cache", error);
            return null;
        }
    }

    static void clear(Context context) {
        prefs(context).edit().remove(KEY_ENTRIES).apply();
    }

    private static boolean matchesRequest(JSONObject entry, String pkg,
                                          String version, long[] codes,
                                          String fileType) {
        if (!pkg.equals(entry.optString("pkg", ""))) {
            return false;
        }
        if (!normalize(fileType).isEmpty()
                && !normalize(fileType).equals(normalize(
                entry.optString("fileType", "")))) {
            return false;
        }
        if (!version.isEmpty()
                && !version.equals(normalize(entry.optString("version", "")))) {
            return false;
        }
        long[] entryCodes = jsonToCodes(entry.optJSONArray("codes"));
        return codes == null || codes.length == 0 || contains(entryCodes, codes);
    }

    private static boolean sameRequest(JSONObject entry, String pkg, String version,
                                       long[] codes, String fileType) {
        return pkg.equals(entry.optString("pkg", ""))
                && normalize(version).equals(normalize(entry.optString("version", "")))
                && normalize(fileType).equals(normalize(entry.optString("fileType", "")))
                && sameCodes(jsonToCodes(entry.optJSONArray("codes")), codes);
    }

    private static boolean sameCodes(long[] left, long[] right) {
        if ((left == null || left.length == 0)
                && (right == null || right.length == 0)) {
            return true;
        }
        return contains(left, right);
    }

    private static JSONArray read(Context context) {
        String raw = prefs(context).getString(KEY_ENTRIES, "[]");
        try {
            return new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static void write(Context context, JSONArray entries) {
        prefs(context).edit().putString(KEY_ENTRIES, entries.toString()).apply();
    }

    private static JSONArray codesToJson(long[] codes) {
        JSONArray result = new JSONArray();
        if (codes != null) {
            for (long code : codes) {
                result.put(code);
            }
        }
        return result;
    }

    private static long[] jsonToCodes(JSONArray array) {
        if (array == null || array.length() == 0) {
            return new long[0];
        }
        long[] result = new long[array.length()];
        for (int i = 0; i < array.length(); i++) {
            result[i] = array.optLong(i);
        }
        return result;
    }

    private static boolean contains(long[] values, long[] wanted) {
        if (values == null || wanted == null) {
            return false;
        }
        for (long value : values) {
            for (long candidate : wanted) {
                if (value == candidate) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
