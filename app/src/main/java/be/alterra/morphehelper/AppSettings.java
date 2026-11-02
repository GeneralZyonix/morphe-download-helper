package be.alterra.morphehelper;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** General app settings and download storage helpers. */
final class AppSettings {

    static final String LOCATION_INTERNAL = "internal";
    static final String LOCATION_EXTERNAL = "external";

    static final String KEEP = "keep";
    static final String HOUR_1 = "1h";
    static final String HOUR_24 = "24h";
    static final String DAY_7 = "7d";

    static final String SNAPTUBE_PACKAGE = "com.snaptube.premium";

    private static final String PREFS = "app_settings";
    private static final String KEY_LOCATION = "download_location";
    private static final String KEY_RETENTION = "download_retention";
    private static final String KEY_ORDER = "source_order";
    private static final String KEY_APPTEKA_SNAPTUBE_ONLY = "appteka_snaptube_only";

    private static final List<String> DEFAULT_ORDER = Arrays.asList(
            SourceSettings.APKPURE,
            SourceSettings.FALLBACK,
            SourceSettings.APKMIRROR,
            SourceSettings.FDROID,
            SourceSettings.APTOIDE,
            SourceSettings.APPTEKA
    );

    private AppSettings() {
    }

    static List<String> defaultOrder() {
        return new ArrayList<>(DEFAULT_ORDER);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String location(Context context) {
        return prefs(context).getString(KEY_LOCATION, LOCATION_INTERNAL);
    }

    static void setLocation(Context context, String location) {
        prefs(context).edit().putString(KEY_LOCATION, location).apply();
    }

    static String retention(Context context) {
        return prefs(context).getString(KEY_RETENTION, KEEP);
    }

    static void setRetention(Context context, String retention) {
        prefs(context).edit().putString(KEY_RETENTION, retention).apply();
    }

    static boolean apptekaSnapTubeOnly(Context context) {
        return prefs(context).getBoolean(KEY_APPTEKA_SNAPTUBE_ONLY, true);
    }

    static void setApptekaSnapTubeOnly(Context context, boolean snapTubeOnly) {
        prefs(context).edit().putBoolean(KEY_APPTEKA_SNAPTUBE_ONLY, snapTubeOnly).apply();
    }

    static boolean apptekaAllowed(Context context, String pkg) {
        return SNAPTUBE_PACKAGE.equals(pkg) || !apptekaSnapTubeOnly(context);
    }

    static File downloadDir(Context context) {
        File dir;
        if (LOCATION_EXTERNAL.equals(location(context))) {
            dir = new File(context.getExternalFilesDir(null), "downloads");
        } else {
            dir = new File(context.getCacheDir(), "downloads");
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    static long retentionMillis(Context context) {
        switch (retention(context)) {
            case HOUR_1:
                return 60L * 60L * 1000L;
            case HOUR_24:
                return 24L * 60L * 60L * 1000L;
            case DAY_7:
                return 7L * 24L * 60L * 60L * 1000L;
            default:
                return 0;
        }
    }

    static void cleanup(Context context) {
        long retention = retentionMillis(context);
        if (retention <= 0) {
            return;
        }
        File dir = downloadDir(context);
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - retention;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < cutoff) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    static int deleteAllDownloads(Context context) {
        File dir = downloadDir(context);
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        int deleted = 0;
        for (File file : files) {
            if (file.isFile() && file.delete()) {
                deleted++;
            }
        }
        return deleted;
    }

    static List<String> sourceOrder(Context context) {
        String raw = prefs(context).getString(KEY_ORDER, "");
        List<String> order = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            for (String key : raw.split(",")) {
                if (DEFAULT_ORDER.contains(key) && !order.contains(key)) {
                    order.add(key);
                }
            }
        }
        for (String key : DEFAULT_ORDER) {
            if (!order.contains(key)) {
                order.add(key);
            }
        }
        return order;
    }

    static void setSourceOrder(Context context, List<String> order) {
        prefs(context).edit().putString(KEY_ORDER, android.text.TextUtils.join(",", order)).apply();
    }

    static void resetSourceOrder(Context context) {
        prefs(context).edit().remove(KEY_ORDER).apply();
    }

    static void moveSource(Context context, String key, int delta) {
        List<String> order = sourceOrder(context);
        int index = order.indexOf(key);
        int target = index + delta;
        if (index < 0 || target < 0 || target >= order.size()) {
            return;
        }
        order.remove(index);
        order.add(target, key);
        setSourceOrder(context, order);
    }

    static String sourceLabel(String key) {
        switch (key) {
            case SourceSettings.APKPURE:
                return "APKPure";
            case SourceSettings.FALLBACK:
                return "Morphe link";
            case SourceSettings.APKMIRROR:
                return "APKMirror";
            case SourceSettings.FDROID:
                return "F-Droid";
            case SourceSettings.APTOIDE:
                return "Aptoide";
            case SourceSettings.APPTEKA:
                return "Appteka";
            default:
                return key;
        }
    }
}


