package be.alterra.morphehelper;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.List;

import androidx.documentfile.provider.DocumentFile;

/** General app settings and download storage helpers. */
final class AppSettings {

    static final String LOCATION_INTERNAL = "internal";
    static final String LOCATION_EXTERNAL = "external";
    static final String LOCATION_PUBLIC = "public";
    static final String LOCATION_CUSTOM = "custom";

    static final String KEEP = "keep";
    static final String HOUR_1 = "1h";
    static final String HOUR_24 = "24h";
    static final String DAY_7 = "7d";

    static final String SNAPTUBE_PACKAGE = "com.snaptube.premium";

    private static final String PREFS = "app_settings";
    private static final String KEY_LOCATION = "download_location";
    private static final String KEY_CUSTOM_TREE = "custom_tree_uri";
    private static final String KEY_RETENTION = "download_retention";
    private static final String KEY_ORDER = "source_order";
    private static final String KEY_APPTEKA_SNAPTUBE_ONLY = "appteka_snaptube_only";
    private static final String KEY_VT_ENABLED = "virustotal_enabled";
    private static final String KEY_VT_API_KEY = "virustotal_api_key";
    private static final String KEY_VT_BLOCK = "virustotal_block_detections";
    private static final String KEY_VT_QUOTA_ABORT = "virustotal_quota_abort";
    private static final String KEY_VT_UPLOAD_UNKNOWN = "virustotal_upload_unknown";
    private static final String KEY_VT_ALWAYS_UPLOAD = "virustotal_always_upload";
    private static final String KEY_VT_UPLOAD_ON_QUOTA = "virustotal_upload_on_quota";

    private static final List<String> DEFAULT_ORDER = Arrays.asList(
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

    static String customTreeUri(Context context) {
        return prefs(context).getString(KEY_CUSTOM_TREE, "");
    }

    static void setCustomTreeUri(Context context, String uri) {
        prefs(context).edit().putString(KEY_CUSTOM_TREE, uri == null ? "" : uri).apply();
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

    static boolean virusTotalBlockDetections(Context context) {
        return prefs(context).getBoolean(KEY_VT_BLOCK, true);
    }

    static void setVirusTotalBlockDetections(Context context, boolean block) {
        prefs(context).edit().putBoolean(KEY_VT_BLOCK, block).apply();
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
        String location = location(context);
        if (LOCATION_PUBLIC.equals(location)) {
            publishPublic(context, file);
        } else if (LOCATION_CUSTOM.equals(location)) {
            publishCustom(context, file);
        }
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
        int deleted = 0;
        File dir = downloadDir(context);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.delete()) {
                    deleted++;
                }
            }
        }
        if (LOCATION_PUBLIC.equals(location(context))) {
            deleted += deletePublicDownloads(context);
        } else if (LOCATION_CUSTOM.equals(location(context))) {
            deleted += deleteCustomDownloads(context);
        }
        return deleted;
    }

    private static int deletePublicDownloads(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                return context.getContentResolver().delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                        new String[]{Environment.DIRECTORY_DOWNLOADS + "/Morphe Helper/%"});
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
                if (file.isFile() && file.delete()) {
                    deleted++;
                }
            }
            return deleted;
        } catch (Exception error) {
            HelperLog.error("Could not delete Downloads copies", error);
            return 0;
        }
    }

    private static int deleteCustomDownloads(Context context) {
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
                if (child.isFile() && child.delete()) {
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


