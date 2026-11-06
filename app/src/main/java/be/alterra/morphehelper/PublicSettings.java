package be.alterra.morphehelper;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mirrors non-secret settings to a user-editable file in the public Downloads
 * folder and applies edits when the helper starts. Secrets such as the
 * VirusTotal API key are never written here.
 */
final class PublicSettings {

    static final String FILE_NAME = "helper-settings.conf";
    private static final String FOLDER = "Morphe Helper";
    private static final String RELATIVE_PATH =
            Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER;
    private static final String MIME_TYPE = "text/plain";
    private static final String[] SOURCE_KEYS = {
            SourceSettings.APKPURE,
            SourceSettings.FALLBACK,
            SourceSettings.APKMIRROR,
            SourceSettings.UPTODOWN,
            SourceSettings.FDROID,
            SourceSettings.APTOIDE,
            SourceSettings.APPTEKA,
            SourceSettings.GITHUB,
    };

    private PublicSettings() {
    }

    /**
     * Reads the public file and applies its values. Creates the file with the
     * current settings when it does not exist yet.
     */
    static void apply(Context context) {
        SourceSettings.ensureInitialized(context);
        String text = read(context);
        if (text == null) {
            write(context);
            return;
        }
        Map<String, String> values = parse(text);
        if (values.isEmpty()) {
            return;
        }

        Boolean showLauncher = bool(values, "show_launcher_icon");
        if (showLauncher != null) {
            AppSettings.setLauncherIconHidden(context, !showLauncher);
        }

        Boolean updateChecks = bool(values, "update_checks");
        if (updateChecks != null) {
            UpdateManager.setEnabled(context, updateChecks);
        }
        String updateMode = values.get("update_mode");
        if ("notify".equalsIgnoreCase(updateMode)) {
            UpdateManager.setMode(context, UpdateManager.MODE_NOTIFY);
        } else if ("shizuku".equalsIgnoreCase(updateMode)) {
            UpdateManager.setMode(context, UpdateManager.MODE_SHIZUKU);
        }
        Boolean updatePopup = bool(values, "update_popup");
        if (updatePopup != null) {
            UpdateManager.setInAppPopupEnabled(context, updatePopup);
        }

        for (String key : SOURCE_KEYS) {
            Boolean enabled = bool(values, "source_" + key);
            if (enabled != null) {
                SourceSettings.setEnabled(context, key, enabled);
            }
        }
        String order = values.get("source_order");
        if (order != null) {
            List<String> parsed = new ArrayList<>();
            for (String key : order.split(",")) {
                String candidate = key.trim().toLowerCase(Locale.US);
                if (AppSettings.defaultOrder().contains(candidate)
                        && !parsed.contains(candidate)) {
                    parsed.add(candidate);
                }
            }
            for (String key : AppSettings.defaultOrder()) {
                if (!parsed.contains(key)) {
                    parsed.add(key);
                }
            }
            if (!parsed.isEmpty()) {
                AppSettings.setSourceOrder(context, parsed);
            }
        }

        applyBoolean(context, values, "virustotal_enabled",
                AppSettings::setVirusTotalEnabled);
        applyBoolean(context, values, "virustotal_block_detections",
                AppSettings::setVirusTotalBlockDetections);
        applyBoolean(context, values, "virustotal_upload_unknown",
                AppSettings::setVirusTotalUploadUnknown);
        applyBoolean(context, values, "virustotal_always_upload",
                AppSettings::setVirusTotalAlwaysUpload);
        applyBoolean(context, values, "virustotal_upload_on_quota",
                AppSettings::setVirusTotalUploadOnQuota);
        applyBoolean(context, values, "virustotal_abort_on_quota",
                AppSettings::setVirusTotalAbortOnQuota);
        applyBoolean(context, values, "appteka_snaptube_only",
                AppSettings::setApptekaSnapTubeOnly);
    }

    /** Rewrites the public file from the current settings. */
    static void write(Context context) {
        StringBuilder text = new StringBuilder();
        text.append("# Morphe Download Helper settings\n");
        text.append("# Edit while the helper is not running, then open the helper\n");
        text.append("# or start a download from Morphe to apply the changes.\n");
        text.append("# Secrets such as the VirusTotal API key are never written here.\n");
        text.append("# Download location, retention, and the API key stay in the app.\n\n");

        text.append("show_launcher_icon=")
                .append(!AppSettings.isLauncherIconHidden(context)).append('\n');
        text.append("update_checks=")
                .append(UpdateManager.isEnabled(context)).append('\n');
        text.append("update_mode=")
                .append(UpdateManager.mode(context)).append('\n');
        text.append("update_popup=")
                .append(UpdateManager.isInAppPopupEnabled(context)).append("\n\n");

        for (String key : SOURCE_KEYS) {
            text.append("source_").append(key).append('=')
                    .append(SourceSettings.isEnabled(context, key)).append('\n');
        }
        text.append("source_order=")
                .append(TextUtils.join(",", AppSettings.sourceOrder(context)))
                .append("\n\n");

        text.append("virustotal_enabled=")
                .append(AppSettings.virusTotalEnabled(context)).append('\n');
        text.append("virustotal_block_detections=")
                .append(AppSettings.virusTotalBlockDetections(context)).append('\n');
        text.append("virustotal_upload_unknown=")
                .append(AppSettings.virusTotalUploadUnknown(context)).append('\n');
        text.append("virustotal_always_upload=")
                .append(AppSettings.virusTotalAlwaysUpload(context)).append('\n');
        text.append("virustotal_upload_on_quota=")
                .append(AppSettings.virusTotalUploadOnQuota(context)).append('\n');
        text.append("virustotal_abort_on_quota=")
                .append(AppSettings.virusTotalAbortOnQuota(context)).append("\n\n");

        text.append("appteka_snaptube_only=")
                .append(AppSettings.apptekaSnapTubeOnly(context)).append('\n');

        writeText(context, text.toString());
    }

    private static String read(Context context) {
        if (Build.VERSION.SDK_INT >= 29) {
            Uri uri = findFile(context);
            if (uri == null) {
                return null;
            }
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                return readAll(input);
            } catch (Exception error) {
                HelperLog.error("Could not read public settings", error);
                return null;
            }
        }
        File file = legacyFile();
        if (!file.isFile()) {
            return null;
        }
        try (InputStream input = new FileInputStream(file)) {
            return readAll(input);
        } catch (Exception error) {
            HelperLog.error("Could not read public settings", error);
            return null;
        }
    }

    private static void writeText(Context context, String text) {
        if (Build.VERSION.SDK_INT >= 29) {
            writeMediaStore(context, text);
            return;
        }
        if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            HelperLog.info("Cannot write public settings without storage permission");
            return;
        }
        File dir = new File(
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS),
                FOLDER);
        if (!dir.exists() && !dir.mkdirs()) {
            HelperLog.error("Cannot create public settings folder", null);
            return;
        }
        try (FileOutputStream output = new FileOutputStream(
                new File(dir, FILE_NAME), false)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            HelperLog.error("Could not write public settings", error);
        }
    }

    private static void writeMediaStore(Context context, String text) {
        ContentResolver resolver = context.getContentResolver();
        Uri existing = findFile(context);
        if (existing != null) {
            try (OutputStream output = resolver.openOutputStream(existing, "wt")) {
                if (output != null) {
                    output.write(text.getBytes(StandardCharsets.UTF_8));
                    return;
                }
            } catch (Exception error) {
                HelperLog.error("Could not update public settings", error);
            }
            try {
                resolver.delete(existing, null, null);
            } catch (Exception ignored) {
                // Recreate below if the provider allows it.
            }
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME);
        values.put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            HelperLog.error("Could not create public settings file", null);
            return;
        }
        try (OutputStream output = resolver.openOutputStream(uri)) {
            if (output == null) {
                throw new IOException("Could not open public settings file");
            }
            output.write(text.getBytes(StandardCharsets.UTF_8));
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, done, null, null);
        } catch (Exception error) {
            try {
                resolver.delete(uri, null, null);
            } catch (Exception ignored) {
            }
            HelperLog.error("Could not write public settings", error);
        }
    }

    private static Uri findFile(Context context) {
        ContentResolver resolver = context.getContentResolver();
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
        String[] args = {FILE_NAME, RELATIVE_PATH + "%"};
        try (Cursor cursor = resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.MediaColumns._ID},
                selection,
                args,
                MediaStore.MediaColumns.DATE_MODIFIED + " DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                return ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0));
            }
        } catch (Exception error) {
            HelperLog.error("Could not find public settings file", error);
        }
        return null;
    }

    private static File legacyFile() {
        return new File(
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS),
                FOLDER + "/" + FILE_NAME);
    }

    private static String readAll(InputStream input) throws IOException {
        if (input == null) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append('\n');
            }
        }
        return result.toString();
    }

    private static Map<String, String> parse(String text) {
        Map<String, String> values = new HashMap<>();
        for (String rawLine : text.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).trim().toLowerCase(Locale.US);
            String value = line.substring(separator + 1).trim();
            if (!key.isEmpty()) {
                values.put(key, value);
            }
        }
        return values;
    }

    private static Boolean bool(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) {
            return null;
        }
        if ("true".equalsIgnoreCase(value) || "1".equals(value)
                || "yes".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value)
                || "no".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)) {
            return false;
        }
        HelperLog.info("Ignoring invalid public setting " + key + "=" + value);
        return null;
    }

    private interface BooleanSetter {
        void set(Context context, boolean value);
    }

    private static void applyBoolean(Context context, Map<String, String> values,
                                     String key, BooleanSetter setter) {
        Boolean value = bool(values, key);
        if (value != null) {
            setter.set(context, value);
        }
    }
}
