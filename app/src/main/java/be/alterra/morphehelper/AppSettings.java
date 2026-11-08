package be.alterra.morphehelper;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.documentfile.provider.DocumentFile;

/** General app settings and download storage helpers. */
final class AppSettings {

    static final String LOCATION_INTERNAL = "internal";
    static final String LOCATION_EXTERNAL = "external";
    static final String LOCATION_PUBLIC = "public";
    static final String LOCATION_CUSTOM = "custom";

    static final String KEEP = "keep";
    static final String AFTER_HANDOFF = "handoff";
    static final String HOUR_1 = "1h";
    static final String HOUR_24 = "24h";
    static final String DAY_7 = "7d";

    /** How long a hand-off cache file stays readable before cleanup may remove it. */
    private static final long HANDOFF_GRACE_MILLIS = 10L * 60L * 1000L;

    static final String SNAPTUBE_PACKAGE = "com.snaptube.premium";

    /** Package name of the original Shizuku app; forks can be configured instead. */
    static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";

    private static final String PREFS = "app_settings";
    private static final String KEY_LOCATION = "download_location";
    private static final String KEY_CUSTOM_TREE = "custom_tree_uri";
    private static final String KEY_RETENTION = "download_retention";
    private static final String KEY_PENDING_HANDOFF = "pending_handoff_cleanup";
    private static final String KEY_ORDER = "source_order";
    private static final String KEY_SHOW_LAUNCHER_ICON = "show_launcher_icon";
    private static final String KEY_BLOCK_WEB_ADS = "block_web_ads";
    private static final String KEY_MULTI_THREAD = "multi_thread_downloads";
    private static final String KEY_LIVE_ADGUARD = "live_adguard_checks";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_THEME_ACCENT = "theme_accent";
    private static final String KEY_THEME_CUSTOM_HEX = "theme_custom_hex";
    private static final String KEY_THEME_SCHEME_HEX = "theme_scheme_hex";
    private static final String KEY_THEME_TINT_SURFACES = "theme_tint_surfaces";
    private static final String KEY_SHIZUKU_PACKAGE = "shizuku_package";
    private static final String KEY_APPTEKA_SNAPTUBE_ONLY = "appteka_snaptube_only";
    private static final String KEY_VT_ENABLED = "virustotal_enabled";
    private static final String KEY_VT_API_KEY = "virustotal_api_key";
    private static final String KEY_VT_BLOCK = "virustotal_block_detections";
    private static final String KEY_VT_DETECTION_ACTION = "virustotal_detection_action";
    private static final String KEY_VT_QUOTA_ABORT = "virustotal_quota_abort";
    private static final String KEY_VT_UPLOAD_UNKNOWN = "virustotal_upload_unknown";
    private static final String KEY_VT_ALWAYS_UPLOAD = "virustotal_always_upload";
    private static final String KEY_VT_UPLOAD_ON_QUOTA = "virustotal_upload_on_quota";

    /**
     * The order shown on the main screen and in Settings. It is also the exact
     * order used by the resolver; there is no separate hard-coded fast path.
     * Morphe's link comes first because it is curated by the patch bundle.
     */
    private static final List<String> DEFAULT_ORDER = Arrays.asList(
            SourceSettings.FALLBACK,
            SourceSettings.APKMIRROR,
            SourceSettings.APKPURE,
            SourceSettings.FDROID,
            SourceSettings.APTOIDE,
            SourceSettings.APPTEKA,
            SourceSettings.GITHUB
    );

    /**
     * The old auto-generated order. It was written into public settings files
     * even when the user never changed the order, so treat it as "default" and
     * migrate it instead of pinning those users to APKPure-first forever.
     */
    private static final List<String> LEGACY_DEFAULT_ORDER = Arrays.asList(
            SourceSettings.APKPURE,
            SourceSettings.FALLBACK,
            SourceSettings.APKMIRROR,
            SourceSettings.FDROID,
            SourceSettings.APTOIDE,
            SourceSettings.APPTEKA,
            SourceSettings.GITHUB
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
        String fallback = Build.VERSION.SDK_INT >= 29
                ? LOCATION_PUBLIC : LOCATION_INTERNAL;
        return prefs(context).getString(KEY_LOCATION, fallback);
    }

    static void setLocation(Context context, String location) {
        prefs(context).edit().putString(KEY_LOCATION, location).apply();
    }

    static boolean isLauncherIconShown(Context context) {
        return prefs(context).getBoolean(KEY_SHOW_LAUNCHER_ICON, true);
    }

    static boolean isLauncherIconHidden(Context context) {
        return !isLauncherIconShown(context);
    }

    static void setLauncherIconHidden(Context context, boolean hidden) {
        setLauncherIconShown(context, !hidden);
    }

    static void setLauncherIconShown(Context context, boolean shown) {
        prefs(context).edit().putBoolean(KEY_SHOW_LAUNCHER_ICON, shown).apply();
        applyLauncherAlias(context, shown);
    }

    /**
     * Applies the saved choice at startup, after updates, and after a reboot.
     * The launcher alias starts disabled in the manifest so a hidden icon does
     * not leave a synthetic launcher entry behind on Android 10+.
     */
    static void ensureLauncherIconState(Context context) {
        applyLauncherAlias(context, isLauncherIconShown(context));
    }

    private static void applyLauncherAlias(Context context, boolean shown) {
        int desired = shown
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        PackageManager manager = context.getPackageManager();
        if (manager.getComponentEnabledSetting(launcherAlias(context)) != desired) {
            manager.setComponentEnabledSetting(
                    launcherAlias(context), desired, PackageManager.DONT_KILL_APP);
        }
        int state = manager.getComponentEnabledSetting(launcherAlias(context));
        HelperLog.info((shown ? "Launcher icon shown" : "Launcher icon hidden")
                + " (componentState=" + state + ")");
    }

    private static ComponentName launcherAlias(Context context) {
        return new ComponentName(context.getPackageName(),
                context.getPackageName() + ".Launcher");
    }

    static String customTreeUri(Context context) {
        return prefs(context).getString(KEY_CUSTOM_TREE, "");
    }

    static void setCustomTreeUri(Context context, String uri) {
        prefs(context).edit().putString(KEY_CUSTOM_TREE, uri == null ? "" : uri).apply();
    }

    /** True when the in-app source WebViews may block known ad/tracker hosts. */
    static boolean blockWebAds(Context context) {
        return prefs(context).getBoolean(KEY_BLOCK_WEB_ADS, true);
    }

    static void setBlockWebAds(Context context, boolean block) {
        prefs(context).edit().putBoolean(KEY_BLOCK_WEB_ADS, block).apply();
    }

    /** True when unknown WebView hosts may be checked against AdGuard DoH. */
    static boolean liveAdGuardChecks(Context context) {
        return prefs(context).getBoolean(KEY_LIVE_ADGUARD, false);
    }

    static void setLiveAdGuardChecks(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_LIVE_ADGUARD, enabled).apply();
    }

    static boolean multiThreadDownloads(Context context) {
        return prefs(context).getBoolean(KEY_MULTI_THREAD, false);
    }

    static void setMultiThreadDownloads(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_MULTI_THREAD, enabled).apply();
    }

    static String themeMode(Context context) {
        return prefs(context).getString(KEY_THEME_MODE, ThemeManager.MODE_SYSTEM);
    }

    static void setThemeMode(Context context, String mode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode).apply();
    }

    static String themeAccent(Context context) {
        return prefs(context).getString(KEY_THEME_ACCENT, ThemeManager.ACCENT_AURORA);
    }

    static void setThemeAccent(Context context, String accent) {
        prefs(context).edit().putString(KEY_THEME_ACCENT, accent).apply();
    }

    static String themeCustomHex(Context context) {
        return prefs(context).getString(KEY_THEME_CUSTOM_HEX, "");
    }

    static void setThemeCustomHex(Context context, String hex) {
        prefs(context).edit().putString(KEY_THEME_CUSTOM_HEX,
                hex == null ? "" : hex).apply();
    }

    static String themeSchemeHex(Context context) {
        return prefs(context).getString(KEY_THEME_SCHEME_HEX, "");
    }

    static void setThemeSchemeHex(Context context, String hex) {
        prefs(context).edit().putString(KEY_THEME_SCHEME_HEX,
                hex == null ? "" : hex).apply();
    }

    static boolean themeTintSurfaces(Context context) {
        return prefs(context).getBoolean(KEY_THEME_TINT_SURFACES, true);
    }

    static void setThemeTintSurfaces(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_THEME_TINT_SURFACES, enabled).apply();
    }

    /**
     * Package name of the Shizuku manager. Renamed forks such as Shizuku Next
     * use their own package, so this is configurable.
     */
    static String shizukuPackage(Context context) {
        String value = prefs(context).getString(KEY_SHIZUKU_PACKAGE, "");
        value = value == null ? "" : value.trim();
        return value.isEmpty() ? SHIZUKU_PACKAGE : value;
    }

    static void setShizukuPackage(Context context, String packageName) {
        String value = packageName == null ? "" : packageName.trim();
        if (value.isEmpty() || SHIZUKU_PACKAGE.equals(value)) {
            prefs(context).edit().remove(KEY_SHIZUKU_PACKAGE).apply();
        } else {
            prefs(context).edit().putString(KEY_SHIZUKU_PACKAGE, value).apply();
        }
    }

    static boolean isValidPackageName(String packageName) {
        if (packageName == null) {
            return false;
        }
        String value = packageName.trim();
        if (value.length() < 3 || value.length() > 255) {
            return false;
        }
        String[] segments = value.split("\\.", -1);
        if (segments.length < 2) {
            return false;
        }
        for (String segment : segments) {
            if (segment.isEmpty()) {
                return false;
            }
            char first = segment.charAt(0);
            if (!Character.isLetter(first) && first != '_') {
                return false;
            }
            for (int i = 1; i < segment.length(); i++) {
                char c = segment.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '_') {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean virusTotalEnabled(Context context) {
        return prefs(context).getBoolean(KEY_VT_ENABLED, false);
    }

    static void setVirusTotalEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_VT_ENABLED, enabled).apply();
    }

    static String virusTotalApiKey(Context context) {
        return prefs(context).getString(KEY_VT_API_KEY, "");
    }

    static void setVirusTotalApiKey(Context context, String apiKey) {
        prefs(context).edit().putString(KEY_VT_API_KEY,
                apiKey == null ? "" : apiKey.trim()).apply();
    }

    static final String VT_ACTION_ASK = "ask";
    static final String VT_ACTION_BLOCK = "block";
    static final String VT_ACTION_CONTINUE = "continue";

    static boolean virusTotalBlockDetections(Context context) {
        return VT_ACTION_BLOCK.equals(virusTotalDetectionAction(context));
    }

    static void setVirusTotalBlockDetections(Context context, boolean block) {
        setVirusTotalDetectionAction(context,
                block ? VT_ACTION_BLOCK : VT_ACTION_CONTINUE);
    }

    static String virusTotalDetectionAction(Context context) {
        SharedPreferences preferences = prefs(context);
        String value = preferences.getString(KEY_VT_DETECTION_ACTION, "");
        if (VT_ACTION_ASK.equals(value)
                || VT_ACTION_BLOCK.equals(value)
                || VT_ACTION_CONTINUE.equals(value)) {
            return value;
        }
        String migrated = preferences.contains(KEY_VT_BLOCK)
                ? (preferences.getBoolean(KEY_VT_BLOCK, true)
                        ? VT_ACTION_BLOCK : VT_ACTION_CONTINUE)
                : VT_ACTION_ASK;
        preferences.edit().putString(KEY_VT_DETECTION_ACTION, migrated).apply();
        return migrated;
    }

    static void setVirusTotalDetectionAction(Context context, String action) {
        String value = VT_ACTION_BLOCK.equals(action) || VT_ACTION_CONTINUE.equals(action)
                ? action : VT_ACTION_ASK;
        prefs(context).edit().putString(KEY_VT_DETECTION_ACTION, value).apply();
    }

    static boolean virusTotalAbortOnQuota(Context context) {
        return prefs(context).getBoolean(KEY_VT_QUOTA_ABORT, false);
    }

    static void setVirusTotalAbortOnQuota(Context context, boolean abort) {
        prefs(context).edit().putBoolean(KEY_VT_QUOTA_ABORT, abort).apply();
    }

    static boolean virusTotalUploadUnknown(Context context) {
        return prefs(context).getBoolean(KEY_VT_UPLOAD_UNKNOWN, false);
    }

    static void setVirusTotalUploadUnknown(Context context, boolean upload) {
        prefs(context).edit().putBoolean(KEY_VT_UPLOAD_UNKNOWN, upload).apply();
    }

    static boolean virusTotalAlwaysUpload(Context context) {
        return prefs(context).getBoolean(KEY_VT_ALWAYS_UPLOAD, false);
    }

    static void setVirusTotalAlwaysUpload(Context context, boolean upload) {
        prefs(context).edit().putBoolean(KEY_VT_ALWAYS_UPLOAD, upload).apply();
    }

    static boolean virusTotalUploadOnQuota(Context context) {
        return prefs(context).getBoolean(KEY_VT_UPLOAD_ON_QUOTA, false);
    }

    static void setVirusTotalUploadOnQuota(Context context, boolean upload) {
        prefs(context).edit().putBoolean(KEY_VT_UPLOAD_ON_QUOTA, upload).apply();
    }

    static String locationLabel(Context context) {
        String location = location(context);
        if (LOCATION_EXTERNAL.equals(location)) {
            return context.getString(R.string.location_external);
        }
        if (LOCATION_PUBLIC.equals(location)) {
            return "Downloads/Morphe Helper";
        }
        if (LOCATION_CUSTOM.equals(location)) {
            String uri = customTreeUri(context);
            if (!uri.isEmpty()) {
                try {
                    DocumentFile folder = DocumentFile.fromTreeUri(
                            context, Uri.parse(uri));
                    if (folder != null && folder.getName() != null) {
                        return context.getString(R.string.location_custom, folder.getName());
                    }
                } catch (Exception ignored) {
                }
            }
            return context.getString(R.string.location_custom_folder);
        }
        return context.getString(R.string.location_internal);
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

    static void publishDownload(Context context, File file) {
        if (file == null || !file.isFile()) {
            return;
        }
        if (AFTER_HANDOFF.equals(retention(context))) {
            // Morphe reads the FileProvider cache URI, not the public copy.
            // Skip the copy entirely and remove the cache file after a short
            // grace period so a slow Morphe read cannot break the hand-off.
            markHandoffCleanup(context, file);
            HelperLog.info("After hand-off: keeping only the temporary cache copy");
            return;
        }
        String location = location(context);
        if (LOCATION_PUBLIC.equals(location)) {
            publishPublic(context, file);
        } else if (LOCATION_CUSTOM.equals(location)) {
            publishCustom(context, file);
        }
    }

    /** Records a cache file to delete after the hand-off grace period. */
    private static void markHandoffCleanup(Context context, File file) {
        long now = System.currentTimeMillis();
        // Refresh the timestamp so the generic age sweep cannot delete the file
        // before the hand-off grace period has passed.
        //noinspection ResultOfMethodCallIgnored
        file.setLastModified(now);
        Set<String> pending = new HashSet<>(prefs(context).getStringSet(
                KEY_PENDING_HANDOFF, new HashSet<>()));
        pending.add(file.getAbsolutePath() + "|" + now);
        prefs(context).edit().putStringSet(KEY_PENDING_HANDOFF, pending).apply();
    }

    private static void publishPublic(Context context, File file) {
        String mime = mimeFor(file.getName());
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver resolver = context.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, file.getName());
                values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/Morphe Helper");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    throw new Exception("Could not create file in Downloads");
                }
                try {
                    try (InputStream input = new FileInputStream(file);
                         OutputStream output = resolver.openOutputStream(uri)) {
                        if (output == null) {
                            throw new Exception("Could not open Downloads file");
                        }
                        copy(input, output);
                    }
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    resolver.update(uri, done, null, null);
                } catch (Exception error) {
                    resolver.delete(uri, null, null);
                    throw error;
                }
            } else {
                File dir = new File(
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS),
                        "Morphe Helper");
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new Exception("Could not create Downloads/Morphe Helper");
                }
                File target = uniqueFile(dir, file.getName());
                try (InputStream input = new FileInputStream(file);
                     OutputStream output = new FileOutputStream(target)) {
                    copy(input, output);
                }
            }
            HelperLog.info("Saved a copy in Downloads/Morphe Helper");
        } catch (Exception error) {
            HelperLog.error("Could not copy download to Downloads", error);
        }
    }

    private static void publishCustom(Context context, File file) {
        String tree = customTreeUri(context);
        if (tree.isEmpty()) {
            return;
        }
        try {
            DocumentFile root = DocumentFile.fromTreeUri(context, Uri.parse(tree));
            if (root == null || !root.canWrite()) {
                throw new Exception("Custom download folder is not writable");
            }
            DocumentFile target = root.createFile(mimeFor(file.getName()), file.getName());
            if (target == null) {
                throw new Exception("Could not create file in custom folder");
            }
            try (InputStream input = new FileInputStream(file);
                 OutputStream output = context.getContentResolver()
                         .openOutputStream(target.getUri())) {
                if (output == null) {
                    throw new Exception("Could not open custom folder file");
                }
                copy(input, output);
            }
            HelperLog.info("Saved a copy in the custom download folder");
        } catch (Exception error) {
            HelperLog.error("Could not copy download to custom folder", error);
        }
    }

    private static void copy(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    private static String mimeFor(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".apk")) {
            return "application/vnd.android.package-archive";
        }
        return "application/octet-stream";
    }

    private static File uniqueFile(File dir, String name) {
        File file = new File(dir, name);
        if (!file.exists()) {
            return file;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; ; i++) {
            File candidate = new File(dir, base + " (" + i + ")" + extension);
            if (!candidate.exists()) {
                return candidate;
            }
        }
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
        // Pending hand-off files are always removed, even if the user later
        // switches back to Keep. They were promised to be temporary.
        cleanupPendingHandoffs(context);

        String retention = retention(context);
        if (KEEP.equals(retention)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (AFTER_HANDOFF.equals(retention)) {
            // Remove the public/custom copies immediately on the next launch.
            // Cache files get a short grace period so Morphe can finish reading
            // the URI even if it is slow.
            long graceCutoff = now - HANDOFF_GRACE_MILLIS;
            int cache = deleteCacheFiles(context, graceCutoff);
            int publicCopies = deletePublicDownloads(context, 0L);
            int customCopies = deleteCustomDownloads(context, 0L);
            HelperLog.info("After hand-off cleanup: cache=" + cache
                    + " public=" + publicCopies + " custom=" + customCopies);
            return;
        }

        long retentionMs = retentionMillis(context);
        if (retentionMs <= 0) {
            return;
        }
        long cutoff = now - retentionMs;
        int cache = deleteCacheFiles(context, cutoff);
        int publicCopies = deletePublicDownloads(context, cutoff);
        int customCopies = deleteCustomDownloads(context, cutoff);
        if (cache + publicCopies + customCopies > 0) {
            HelperLog.info("Retention cleanup: cache=" + cache
                    + " public=" + publicCopies + " custom=" + customCopies);
        }
    }

    private static int deleteCacheFiles(Context context, long cutoff) {
        // Clean both possible cache locations, even if the user switched the
        // download folder after the files were created.
        int deleted = deleteCacheFilesIn(
                new File(context.getCacheDir(), "downloads"), cutoff);
        File externalRoot = context.getExternalFilesDir(null);
        if (externalRoot != null) {
            deleted += deleteCacheFilesIn(
                    new File(externalRoot, "downloads"), cutoff);
        }
        return deleted;
    }

    private static int deleteCacheFilesIn(File dir, long cutoff) {
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        int deleted = 0;
        for (File file : files) {
            if (file.isFile()
                    && (cutoff <= 0 || file.lastModified() < cutoff)
                    && file.delete()) {
                deleted++;
            }
        }
        return deleted;
    }

    private static void cleanupPendingHandoffs(Context context) {
        SharedPreferences prefs = prefs(context);
        Set<String> pending = prefs.getStringSet(KEY_PENDING_HANDOFF, null);
        if (pending == null || pending.isEmpty()) {
            return;
        }
        long cutoff = System.currentTimeMillis() - HANDOFF_GRACE_MILLIS;
        Set<String> keep = new HashSet<>();
        for (String entry : pending) {
            int separator = entry.lastIndexOf('|');
            if (separator <= 0) {
                continue;
            }
            long marked;
            try {
                marked = Long.parseLong(entry.substring(separator + 1));
            } catch (NumberFormatException ignored) {
                continue;
            }
            File file = new File(entry.substring(0, separator));
            if (!isCacheFile(context, file)) {
                continue;
            }
            if (!file.exists()) {
                continue;
            }
            if (marked > cutoff) {
                keep.add(entry);
            } else if (file.delete()) {
                HelperLog.info("Deleted hand-off cache file " + file.getName());
            } else {
                keep.add(entry);
            }
        }
        prefs.edit().putStringSet(KEY_PENDING_HANDOFF, keep).apply();
    }

    private static boolean isCacheFile(Context context, File file) {
        try {
            String path = file.getCanonicalPath();
            File internal = new File(context.getCacheDir(), "downloads");
            if (path.startsWith(internal.getCanonicalPath() + File.separator)) {
                return true;
            }
            File externalRoot = context.getExternalFilesDir(null);
            return externalRoot != null
                    && path.startsWith(new File(externalRoot, "downloads")
                    .getCanonicalPath() + File.separator);
        } catch (Exception ignored) {
            return false;
        }
    }

    static int deleteAllDownloads(Context context) {
        int deleted = deleteCacheFiles(context, 0L);
        deleted += deletePublicDownloads(context, 0L);
        deleted += deleteCustomDownloads(context, 0L);
        return deleted;
    }

    private static int deletePublicDownloads(Context context, long cutoff) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                String selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? AND "
                        + MediaStore.MediaColumns.DISPLAY_NAME + " NOT LIKE ?";
                List<String> args = new ArrayList<>();
                args.add("%Morphe Helper/%");
                args.add("helper-settings%");
                if (cutoff > 0) {
                    selection += " AND " + MediaStore.MediaColumns.DATE_MODIFIED + " < ?";
                    args.add(String.valueOf(cutoff / 1000L));
                }
                return context.getContentResolver().delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        selection,
                        args.toArray(new String[0]));
            }
            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS),
                    "Morphe Helper");
            File[] files = dir.listFiles();
            if (files == null) {
                return 0;
            }
            int deleted = 0;
            for (File file : files) {
                if (file.isFile()
                        && !PublicSettings.isSettingsFileName(file.getName())
                        && (cutoff == 0 || file.lastModified() < cutoff)
                        && file.delete()) {
                    deleted++;
                }
            }
            return deleted;
        } catch (Exception error) {
            HelperLog.error("Could not delete Downloads copies", error);
            return 0;
        }
    }

    private static int deleteCustomDownloads(Context context, long cutoff) {
        String tree = customTreeUri(context);
        if (tree.isEmpty()) {
            return 0;
        }
        try {
            DocumentFile root = DocumentFile.fromTreeUri(context, Uri.parse(tree));
            if (root == null) {
                return 0;
            }
            int deleted = 0;
            for (DocumentFile child : root.listFiles()) {
                if (!child.isFile()
                        || PublicSettings.isSettingsFileName(child.getName())) {
                    continue;
                }
                long modified = child.lastModified();
                if ((cutoff == 0 || (modified > 0 && modified < cutoff))
                        && child.delete()) {
                    deleted++;
                }
            }
            return deleted;
        } catch (Exception error) {
            HelperLog.error("Could not delete custom downloads", error);
            return 0;
        }
    }

    static List<String> sourceOrder(Context context) {
        String raw = prefs(context).getString(KEY_ORDER, "");
        List<String> saved = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            for (String key : raw.split(",")) {
                if (DEFAULT_ORDER.contains(key) && !saved.contains(key)) {
                    saved.add(key);
                }
            }
        }
        // Older builds wrote the default order to the public file as if it were
        // a custom choice. Ignore that value so the new default applies.
        if (saved.equals(LEGACY_DEFAULT_ORDER)) {
            saved.clear();
        }
        for (String key : DEFAULT_ORDER) {
            if (!saved.contains(key)) {
                saved.add(key);
            }
        }
        return saved;
    }

    static boolean isLegacyDefaultOrder(List<String> order) {
        return order != null && order.equals(LEGACY_DEFAULT_ORDER);
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

    static void moveSourceTo(Context context, String key, int target) {
        List<String> order = sourceOrder(context);
        int index = order.indexOf(key);
        if (index < 0) {
            return;
        }
        int clamped = Math.max(0, Math.min(target, order.size() - 1));
        if (index == clamped) {
            return;
        }
        order.remove(index);
        order.add(clamped, key);
        setSourceOrder(context, order);
    }

    static String sourceLabel(Context context, String key) {
        switch (key) {
            case SourceSettings.APKPURE:
                return context.getString(R.string.source_apkpure);
            case SourceSettings.FALLBACK:
                return context.getString(R.string.source_morphe_link);
            case SourceSettings.APKMIRROR:
                return context.getString(R.string.source_apkmirror);
            case SourceSettings.FDROID:
                return context.getString(R.string.source_fdroid);
            case SourceSettings.APTOIDE:
                return context.getString(R.string.source_aptoide);
            case SourceSettings.UPTODOWN:
                return context.getString(R.string.source_uptodown);
            case SourceSettings.APPTEKA:
                return context.getString(R.string.source_appteka);
            case SourceSettings.GITHUB:
                return context.getString(R.string.source_github_releases);
            default:
                return key;
        }
    }
}


