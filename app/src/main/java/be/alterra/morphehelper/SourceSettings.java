package be.alterra.morphehelper;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persisted on/off switches for the APK sources used by DownloadActivity.
 */
final class SourceSettings {

    static final String FALLBACK = "fallback";
    static final String APKMIRROR = "apkmirror";
    static final String APKPURE = "apkpure";
    static final String UPTODOWN = "uptodown";
    static final String FDROID = "fdroid";
    static final String APTOIDE = "aptoide";
    static final String APPTEKA = "appteka";

    private static final String PREFS = "download_sources";

    private SourceSettings() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void ensureInitialized(Context context) {
        SharedPreferences prefs = prefs(context);
        if (prefs.contains(FALLBACK)) {
            return;
        }
        prefs.edit()
                .putBoolean(FALLBACK, true)
                .putBoolean(APKMIRROR, true)
                .putBoolean(APKPURE, true)
                .putBoolean(UPTODOWN, true)
                .putBoolean(FDROID, true)
                .putBoolean(APTOIDE, true)
                .putBoolean(APPTEKA, true)
                .apply();
    }

    static boolean isEnabled(Context context, String key) {
        return prefs(context).getBoolean(key, true);
    }

    static void setEnabled(Context context, String key, boolean enabled) {
        prefs(context).edit().putBoolean(key, enabled).apply();
    }

    static int enabledCount(Context context) {
        int count = 0;
        for (String key : new String[]{FALLBACK, APKMIRROR, APKPURE, UPTODOWN, FDROID, APTOIDE, APPTEKA}) {
            if (isEnabled(context, key)) {
                count++;
            }
        }
        return count;
    }
}
