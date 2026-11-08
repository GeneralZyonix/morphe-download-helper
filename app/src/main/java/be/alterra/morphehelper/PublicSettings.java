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

    /**
     * Canonical name for new files. Android's MediaStore appends {@code .txt} to
     * {@code text/plain} files whose name does not already match the MIME type,
     * which is how earlier builds ended up with {@code helper-settings.conf.txt}.
     */
    static final String FILE_NAME = "helper-settings.txt";
    private static final String LEGACY_CONF = "helper-settings.conf";
    private static final String LEGACY_CONF_TXT = "helper-settings.conf.txt";
    private static final String[] FILE_NAMES = {
            FILE_NAME, LEGACY_CONF_TXT, LEGACY_CONF
    };
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
        String text;
        try {
            text = read(context);
        } catch (Exception error) {
            HelperLog.error("Could not read public settings; leaving the file unchanged", error);
            return;
        }
        if (text == null) {
            HelperLog.info("Public settings file not found; creating it");
            write(context);
            return;
        }
        applyText(context, text);
    }

    /** Applies settings from an imported configuration file. */
    static void applyText(Context context, String text) {
        SourceSettings.ensureInitialized(context);
        if (text == null || text.trim().isEmpty()) {
            HelperLog.info("Imported settings are empty");
            return;
        }
        applyValues(context, text);
    }

    private static void applyValues(Context context, String text) {
        Map<String, String> values = parse(text);
        if (values.isEmpty()) {
            HelperLog.info("Public settings file is empty");
            return;
        }
        HelperLog.info("Public settings: applying " + values.size() + " values");

        Boolean showLauncher = bool(values, "show_launcher_icon");
        if (showLauncher == null) {
            showLauncher = bool(values, "show_launcher");
        }
        Boolean hideLauncher = bool(values, "hide_launcher_icon");
        if (hideLauncher != null) {
            showLauncher = !hideLauncher;
        }
        if (showLauncher != null) {
            HelperLog.info("Public settings: show_launcher_icon=" + showLauncher
                    + ", currently hidden=" + AppSettings.isLauncherIconHidden(context));
            AppSettings.setLauncherIconHidden(context, !showLauncher);
        } else {
            HelperLog.info("Public settings: show_launcher_icon not set");
        }

        String themeMode = values.get("theme_mode");
        if (themeMode != null) {
            if (ThemeManager.MODE_AMOLED.equalsIgnoreCase(themeMode)) {
                AppSettings.setThemeMode(context, ThemeManager.MODE_AMOLED);
            } else if (ThemeManager.MODE_LIGHT.equalsIgnoreCase(themeMode)) {
                AppSettings.setThemeMode(context, ThemeManager.MODE_LIGHT);
            } else if (ThemeManager.MODE_SYSTEM.equalsIgnoreCase(themeMode)) {
                AppSettings.setThemeMode(context, ThemeManager.MODE_SYSTEM);
            } else if (ThemeManager.MODE_DARK.equalsIgnoreCase(themeMode)) {
                AppSettings.setThemeMode(context, ThemeManager.MODE_DARK);
            }
        }
        String themeAccent = values.get("theme_accent");
        if (themeAccent != null) {
            String normalized = themeAccent.trim().toLowerCase(Locale.US);
            if (ThemeManager.presets().contains(normalized)
                    || ThemeManager.ACCENT_CUSTOM.equals(normalized)) {
                AppSettings.setThemeAccent(context, normalized);
            }
        }
        String themeHex = values.get("theme_custom_hex");
        if (themeHex != null) {
            AppSettings.setThemeCustomHex(context,
                    ThemeManager.normalizeHex(themeHex));
        }
        String themeSchemeHex = values.get("theme_scheme_hex");
        if (themeSchemeHex != null) {
            AppSettings.setThemeSchemeHex(context,
                    ThemeManager.normalizeHex(themeSchemeHex));
        }
        Boolean themeTint = bool(values, "theme_tint_surfaces");
        if (themeTint != null) {
            AppSettings.setThemeTintSurfaces(context, themeTint);
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
        String shizukuPackage = values.get("shizuku_package");
        if (shizukuPackage != null) {
            String trimmed = shizukuPackage.trim();
            if (trimmed.isEmpty() || AppSettings.isValidPackageName(trimmed)) {
                AppSettings.setShizukuPackage(context, trimmed);
            } else {
                HelperLog.info("Ignoring invalid public setting shizuku_package=" + trimmed);
            }
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
            if (parsed.isEmpty()
                    || AppSettings.isLegacyDefaultOrder(parsed)
                    || parsed.equals(AppSettings.defaultOrder())) {
                // An auto-generated default is not a user choice. Reset it so a
                // future default-order change can migrate again.
                HelperLog.info("Public settings: using default source order");
                AppSettings.resetSourceOrder(context);
            } else {
                for (String key : AppSettings.defaultOrder()) {
                    if (!parsed.contains(key)) {
                        parsed.add(key);
                    }
                }
                if (!parsed.isEmpty()) {
                    AppSettings.setSourceOrder(context, parsed);
                }
            }
        }

        String apiKey = values.get("virustotal_api_key");
        if (apiKey != null) {
            AppSettings.setVirusTotalApiKey(context, apiKey.trim());
        }

        String location = values.get("download_location");
        if (AppSettings.LOCATION_PUBLIC.equals(location)
                || AppSettings.LOCATION_INTERNAL.equals(location)
                || AppSettings.LOCATION_EXTERNAL.equals(location)
                || AppSettings.LOCATION_CUSTOM.equals(location)) {
            AppSettings.setLocation(context, location);
        }
        String locationUri = values.get("download_location_uri");
        if (locationUri != null) {
            AppSettings.setCustomTreeUri(context, locationUri.trim());
        }
        String retention = values.get("download_retention");
        if (AppSettings.KEEP.equals(retention)
                || AppSettings.AFTER_HANDOFF.equals(retention)
                || AppSettings.HOUR_1.equals(retention)
                || AppSettings.HOUR_24.equals(retention)
                || AppSettings.DAY_7.equals(retention)) {
            AppSettings.setRetention(context, retention);
        }

        applyBoolean(context, values, "block_web_ads",
                AppSettings::setBlockWebAds);
        applyBoolean(context, values, "multi_thread_downloads",
                AppSettings::setMultiThreadDownloads);
        applyBoolean(context, values, "live_adguard_checks",
                AppSettings::setLiveAdGuardChecks);
        applyBoolean(context, values, "virustotal_enabled",
                AppSettings::setVirusTotalEnabled);
        String detectionAction = values.get("virustotal_detection_action");
        if (detectionAction != null) {
            AppSettings.setVirusTotalDetectionAction(context, detectionAction);
        } else {
            applyBoolean(context, values, "virustotal_block_detections",
                    AppSettings::setVirusTotalBlockDetections);
        }
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
        writeText(context, buildText(context, false));
    }

    /** Builds configuration text, optionally including API secrets. */
    static String exportText(Context context, boolean includeSecrets) {
        return buildText(context, includeSecrets);
    }

    private static String buildText(Context context, boolean includeSecrets) {
        StringBuilder text = new StringBuilder();
        text.append("# Morphe Download Helper settings\n");
        text.append("# Edit while the helper is not running, then open the helper\n");
        text.append("# or start a download from Morphe to apply the changes.\n");
        text.append("# Download location and retention are included below.\n");
        text.append(includeSecrets
                ? "# Includes the VirusTotal API key in plain text.\n\n"
                : "# Secrets such as the VirusTotal API key are not included.\n\n");

        text.append("show_launcher_icon=")
                .append(!AppSettings.isLauncherIconHidden(context)).append('\n');
        text.append("theme_mode=").append(AppSettings.themeMode(context)).append('\n');
        text.append("theme_accent=").append(AppSettings.themeAccent(context)).append('\n');
        text.append("theme_custom_hex=").append(AppSettings.themeCustomHex(context)).append('\n');
        text.append("theme_scheme_hex=").append(AppSettings.themeSchemeHex(context)).append('\n');
        text.append("theme_tint_surfaces=")
                .append(AppSettings.themeTintSurfaces(context)).append('\n');
        text.append("update_checks=")
                .append(UpdateManager.isEnabled(context)).append('\n');
        text.append("update_mode=")
                .append(UpdateManager.mode(context)).append('\n');
        text.append("update_popup=")
                .append(UpdateManager.isInAppPopupEnabled(context)).append('\n');
        text.append("shizuku_package=")
                .append(AppSettings.shizukuPackage(context)).append("\n\n");

        for (String key : SOURCE_KEYS) {
            text.append("source_").append(key).append('=')
                    .append(SourceSettings.isEnabled(context, key)).append('\n');
        }
        text.append("source_order=")
                .append(TextUtils.join(",", AppSettings.sourceOrder(context)))
                .append("\n\n");

        text.append("block_web_ads=")
                .append(AppSettings.blockWebAds(context)).append('\n');
        text.append("multi_thread_downloads=")
                .append(AppSettings.multiThreadDownloads(context)).append('\n');
        text.append("live_adguard_checks=")
                .append(AppSettings.liveAdGuardChecks(context)).append('\n');
        text.append("virustotal_enabled=")
                .append(AppSettings.virusTotalEnabled(context)).append('\n');
        text.append("virustotal_detection_action=")
                .append(AppSettings.virusTotalDetectionAction(context)).append('\n');
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

        text.append("download_location=")
                .append(AppSettings.location(context)).append('\n');
        if (AppSettings.LOCATION_CUSTOM.equals(AppSettings.location(context))) {
            text.append("download_location_uri=")
                    .append(AppSettings.customTreeUri(context)).append('\n');
        }
        text.append("download_retention=")
                .append(AppSettings.retention(context)).append('\n');

        if (includeSecrets) {
            String apiKey = AppSettings.virusTotalApiKey(context);
            if (!apiKey.isEmpty()) {
                text.append("\nvirustotal_api_key=").append(apiKey).append('\n');
            }
        }

        return text.toString();
    }

    private static String read(Context context) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            Uri uri = findFile(context);
            if (uri == null) {
                return null;
            }
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                return readAll(input);
            }
        }
        File file = legacyFile();
        if (!file.isFile()) {
            return null;
        }
        try (InputStream input = new FileInputStream(file)) {
            return readAll(input);
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
        File target = legacyFile();
        try (FileOutputStream output = new FileOutputStream(target, false)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
            HelperLog.info("Wrote public settings file " + target.getName());
        } catch (Exception error) {
            HelperLog.error("Could not write public settings", error);
        }
    }

    private static void writeMediaStore(Context context, String text) {
        ContentResolver resolver = context.getContentResolver();
        Uri existing;
        try {
            existing = findFile(context);
        } catch (IOException error) {
            HelperLog.error("Could not locate public settings file", error);
            return;
        }
        if (existing != null) {
            try (OutputStream output = resolver.openOutputStream(existing, "wt")) {
                if (output == null) {
                    throw new IOException("Could not open public settings file");
                }
                output.write(text.getBytes(StandardCharsets.UTF_8));
                HelperLog.info("Updated public settings file");
                return;
            } catch (Exception error) {
                HelperLog.error("Could not update public settings; leaving the file unchanged",
                        error);
                return;
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
            HelperLog.info("Created public settings file");
        } catch (Exception error) {
            try {
                resolver.delete(uri, null, null);
            } catch (Exception ignored) {
            }
            HelperLog.error("Could not write public settings", error);
        }
    }

    private static Uri findFile(Context context) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        // MediaStore turns a text/plain name of "helper-settings.conf" into
        // "helper-settings.conf.txt", so earlier builds never found the file
        // they had just created and inserted "helper-settings.conf (1).txt".
        // Accept every name we have shipped and prefer the newest copy in the
        // Morphe Helper folder.
        try {
            Uri newest = findNewest(resolver, FILE_NAMES, true);
            if (newest != null) {
                return newest;
            }
            Uri outsideFolder = findNewest(resolver, FILE_NAMES, false);
            if (outsideFolder != null) {
                HelperLog.info("Public settings file found outside Morphe Helper");
                return outsideFolder;
            }
            // If only a renamed duplicate such as "helper-settings.conf (1).txt"
            // exists, use it instead of creating yet another file.
            Uri duplicate = findDuplicate(resolver);
            if (duplicate != null) {
                HelperLog.info("Using duplicate public settings file; please delete"
                        + " extra helper-settings copies");
            }
            return duplicate;
        } catch (Exception error) {
            throw new IOException("Could not query public settings file", error);
        }
    }

    private static Uri findNewest(ContentResolver resolver, String[] names,
                                  boolean inFolder) throws Exception {
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + " IN ("
                + TextUtils.join(",", java.util.Collections.nCopies(names.length, "?"))
                + ")";
        Uri best = null;
        long bestModified = -1;
        try (Cursor cursor = resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        MediaStore.MediaColumns.DATE_MODIFIED
                },
                selection,
                names,
                MediaStore.MediaColumns.DATE_MODIFIED + " DESC")) {
            if (cursor == null) {
                return null;
            }
            while (cursor.moveToNext()) {
                String relativePath = cursor.getString(2);
                boolean isInFolder = relativePath != null
                        && relativePath.toLowerCase(Locale.US).contains("morphe helper");
                if (isInFolder != inFolder) {
                    continue;
                }
                long modified = cursor.getLong(3);
                if (modified > bestModified) {
                    bestModified = modified;
                    best = ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0));
                    HelperLog.info("Public settings file found: "
                            + (relativePath == null ? "" : relativePath)
                            + cursor.getString(1));
                }
            }
        }
        return best;
    }

    private static Uri findDuplicate(ContentResolver resolver) throws Exception {
        Uri best = null;
        long bestModified = -1;
        try (Cursor cursor = resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        MediaStore.MediaColumns.DATE_MODIFIED
                },
                MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?",
                new String[]{"helper-settings%"},
                MediaStore.MediaColumns.DATE_MODIFIED + " DESC")) {
            if (cursor == null) {
                return null;
            }
            while (cursor.moveToNext()) {
                String name = cursor.getString(1);
                if (!isSettingsFileName(name)) {
                    continue;
                }
                long modified = cursor.getLong(3);
                if (modified > bestModified) {
                    bestModified = modified;
                    best = ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0));
                }
            }
        }
        return best;
    }

    /** True for the canonical file, legacy names, and MediaStore duplicates. */
    static boolean isSettingsFileName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.US);
        if (!lower.endsWith(".txt") && !lower.endsWith(".conf")) {
            return false;
        }
        int dot = lower.lastIndexOf('.');
        String base = dot > 0 ? lower.substring(0, dot) : lower;
        return base.equals("helper-settings")
                || base.equals("helper-settings.conf")
                || base.matches("helper-settings(\\.conf)? \\(\\d+\\)");
    }

    private static File legacyFile() {
        File dir = new File(
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS),
                FOLDER);
        File newest = null;
        for (String name : FILE_NAMES) {
            File candidate = new File(dir, name);
            if (candidate.isFile()
                    && (newest == null
                    || candidate.lastModified() > newest.lastModified())) {
                newest = candidate;
            }
        }
        return newest != null ? newest : new File(dir, FILE_NAME);
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
