package be.alterra.morphehelper;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Small per-package icon cache. Installed apps use the system/launcher icon;
 * this cache covers apps that are not installed and would otherwise show only
 * an initial. It is filled from the source page while resolving and from the
 * downloaded APK after the file is ready.
 */
final class AppIconCache {

    private static final String DIR = "app-icons";
    private static final int SIZE_PX = 192;

    private AppIconCache() {
    }

    /** Cache key includes the requested version so an old build's icon is not reused. */
    static String key(String pkg, String version) {
        if (pkg == null) {
            return "";
        }
        String normalized = version == null ? "" : version.trim();
        return normalized.isEmpty() ? pkg : pkg + "@" + normalized;
    }

    static Drawable load(Context context, String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        File file = new File(dir(context), fileName(key));
        if (!file.isFile()) {
            return null;
        }
        return Drawable.createFromPath(file.getAbsolutePath());
    }

    /** Saves the icon from a plain APK. Split archives are skipped here. */
    static void saveFromApk(Context context, String key, File apk) {
        if (key == null || key.isEmpty() || apk == null || !apk.isFile()
                || !apk.getName().toLowerCase(Locale.US).endsWith(".apk")) {
            return;
        }
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            if (info == null || info.applicationInfo == null) {
                return;
            }
            ApplicationInfo applicationInfo = info.applicationInfo;
            applicationInfo.sourceDir = apk.getAbsolutePath();
            applicationInfo.publicSourceDir = apk.getAbsolutePath();
            save(context, key, context.getPackageManager()
                    .getApplicationIcon(applicationInfo));
        } catch (Exception ignored) {
            // A malformed or unsupported APK just means no cached icon.
        }
    }

    /** Downloads and caches a PNG/JPG icon URL. Call from a worker thread. */
    static void saveFromUrl(Context context, String key, String url) {
        if (key == null || key.isEmpty() || url == null || url.isEmpty()) {
            return;
        }
        String resolved = url.trim();
        if (resolved.startsWith("//")) {
            resolved = "https:" + resolved;
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(resolved).openConnection();
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(10_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Morphe-Helper/1.1");
            try (InputStream input = connection.getInputStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                if (bitmap != null) {
                    save(context, key, bitmap);
                }
            }
        } catch (Exception ignored) {
            // Icons are best effort; the UI keeps its placeholder.
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void save(Context context, String key, Drawable drawable) {
        if (drawable == null) {
            return;
        }
        Bitmap bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, SIZE_PX, SIZE_PX);
        drawable.draw(canvas);
        save(context, key, bitmap);
    }

    private static void save(Context context, String key, Bitmap bitmap) {
        if (key == null || key.isEmpty() || bitmap == null) {
            return;
        }
        File directory = dir(context);
        if (!directory.exists() && !directory.mkdirs()) {
            return;
        }
        File target = new File(directory, fileName(key));
        File temp = null;
        try {
            temp = File.createTempFile("icon-", ".png", directory);
            try (FileOutputStream output = new FileOutputStream(temp)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            }
            if (!temp.renameTo(target)) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                if (!temp.renameTo(target)) {
                    return;
                }
            }
        } catch (Exception ignored) {
            // Keep any previous cached icon.
        } finally {
            if (temp != null && temp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        }
    }

    private static File dir(Context context) {
        return new File(context.getCacheDir(), DIR);
    }

    private static String fileName(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                name.append(String.format(Locale.US, "%02x", hash[i]));
            }
            return name.append(".png").toString();
        } catch (Exception ignored) {
            return Integer.toHexString(key.hashCode()) + ".png";
        }
    }
}
