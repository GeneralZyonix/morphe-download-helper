package be.alterra.morphehelper;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class UpdateManager {

    static final String MODE_NOTIFY = "notify";
    static final String MODE_SHIZUKU = "shizuku";

    static final String EXTRA_VERSION = "version";

    interface Callback {
        void done(String message);
    }

    interface InfoCallback {
        void done(UpdateInfo info, String error);
    }

    private static final String PREFS = "update_settings";
    private static final String KEY_MODE = "mode";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_SNOOZE_UNTIL = "snooze_until";
    private static final String KEY_SKIPPED_VERSION = "skipped_version";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_VERSION = "latest_version";
    private static final String KEY_RELEASE_URL = "release_url";
    private static final String KEY_ASSET_URL = "asset_url";
    private static final String KEY_ASSET_NAME = "asset_name";
    private static final String KEY_ASSET_SIZE = "asset_size";
    private static final String KEY_NOTES = "latest_notes";

    private static final String CHANNEL = "updates";
    private static final int NOTIFICATION_ID = 73;
    private static final long CHECK_INTERVAL = 6L * 60L * 60L * 1000L;

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final AtomicBoolean checking = new AtomicBoolean(false);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UpdateManager() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String mode(Context context) {
        return prefs(context).getString(KEY_MODE, MODE_NOTIFY);
    }

    static void setMode(Context context, String mode) {
        prefs(context).edit().putString(KEY_MODE, mode).apply();
    }

    static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, true);
    }

    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (!enabled) {
            cancelNotification(context);
        }
    }

    static void remindLater(Context context) {
        long when = System.currentTimeMillis() + 24L * 60L * 60L * 1000L;
        prefs(context).edit().putLong(KEY_SNOOZE_UNTIL, when).apply();
        cancelNotification(context);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            return;
        }
        Intent intent = new Intent(context, UpdateReminderReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, 42, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= 23) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
        } else {
            manager.set(AlarmManager.RTC_WAKEUP, when, pending);
        }
    }

    static void scheduleInstalledCheck(Context context, String version) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            return;
        }
        Intent intent = new Intent(context, UpdateInstalledReceiver.class)
                .putExtra(EXTRA_VERSION, version);
        PendingIntent pending = PendingIntent.getBroadcast(context, 43, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // The app process is normally killed while replacing its own package. This delayed
        // check lets the updated app report success after the package manager finishes.
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 15_000L, pending);
    }

    static void skipVersion(Context context) {
        UpdateInfo info = cachedLatest(context);
        if (info != null) {
            prefs(context).edit().putString(KEY_SKIPPED_VERSION, info.version).apply();
        }
        cancelNotification(context);
    }

    static void clearReminder(Context context) {
        prefs(context).edit().remove(KEY_SNOOZE_UNTIL).apply();
    }

    static long lastCheck(Context context) {
        return prefs(context).getLong(KEY_LAST_CHECK, 0);
    }

    static UpdateInfo cachedLatest(Context context) {
        String version = prefs(context).getString(KEY_VERSION, "");
        if (version.isEmpty()) {
            return null;
        }
        return new UpdateInfo(
                version,
                prefs(context).getString(KEY_RELEASE_URL, ""),
                prefs(context).getString(KEY_ASSET_URL, ""),
                prefs(context).getString(KEY_ASSET_NAME, ""),
                prefs(context).getLong(KEY_ASSET_SIZE, 0),
                prefs(context).getString(KEY_NOTES, "")
        );
    }

    private static void cacheInfo(Context context, UpdateInfo info) {
        prefs(context).edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .putString(KEY_VERSION, info.version)
                .putString(KEY_RELEASE_URL, info.releaseUrl)
                .putString(KEY_ASSET_URL, info.assetUrl)
                .putString(KEY_ASSET_NAME, info.assetName)
                .putLong(KEY_ASSET_SIZE, info.assetSize)
                .putString(KEY_NOTES, info.notes)
                .apply();
    }

    static void fetchLatestAsync(Context context, InfoCallback callback) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                UpdateInfo info = UpdateChecker.check();
                cacheInfo(app, info);
                MAIN.post(() -> callback.done(info, null));
            } catch (Exception error) {
                HelperLog.error("Could not load release notes", error);
                MAIN.post(() -> callback.done(null, error.getMessage()));
            }
        }).start();
    }

    static void cleanupStagedUpdates(Context context) {
        File dir = context.getExternalFilesDir("updates");
        if (dir != null) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }
        if (Build.VERSION.SDK_INT < 29) {
            return;
        }
        try {
            context.getContentResolver().delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                    new String[]{Environment.DIRECTORY_DOWNLOADS
                            + "/Morphe Helper/updates/%"});
        } catch (Exception error) {
            HelperLog.error("Could not clear staged updates", error);
        }
    }

    static void maybeCheckOnLaunch(Context context) {
        if (!isEnabled(context)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < prefs(context).getLong(KEY_SNOOZE_UNTIL, 0)) {
            return;
        }
        if (now - lastCheck(context) < CHECK_INTERVAL) {
            return;
        }
        checkAsync(context, false, null);
    }

    static void checkAsync(Context context, boolean manual, Callback callback) {
        if (!manual && !isEnabled(context)) {
            callbackDone(callback, "Update checks are disabled.");
            return;
        }
        if (!checking.compareAndSet(false, true)) {
            callbackDone(callback, "Update check already running.");
            return;
        }
        Context app = context.getApplicationContext();
        new Thread(() -> {
            String message;
            try {
                UpdateInfo info = UpdateChecker.check();
                cacheInfo(app, info);

                String skipped = prefs(app).getString(KEY_SKIPPED_VERSION, "");
                if (!info.isNewerThan(UpdateChecker.installedVersion(app))) {
                    cancelNotification(app);
                    message = "You are up to date (v" + info.version + ").";
                } else if (info.version.equals(skipped)) {
                    cancelNotification(app);
                    message = "Update v" + info.version + " is skipped.";
                } else if (!manual && System.currentTimeMillis()
                        < prefs(app).getLong(KEY_SNOOZE_UNTIL, 0)) {
                    message = "Update reminder is snoozed.";
                } else if (MODE_SHIZUKU.equals(mode(app))
                        && ShizukuHelper.status(app) == ShizukuHelper.READY
                        && !info.assetUrl.isEmpty()) {
                    message = "Update v" + info.version + " found. Installing with Shizuku...";
                    installUpdate(app, info);
                } else {
                    notifyUpdate(app, info);
                    message = "Update v" + info.version + " available.";
                }
            } catch (Exception error) {
                HelperLog.error("Update check failed", error);
                message = "Update check failed: " + error.getMessage();
            } finally {
                checking.set(false);
            }
            callbackDone(callback, message);
        }).start();
    }

    private static void installUpdate(Context context, UpdateInfo info) {
        new Thread(() -> {
            DownloadedUpdate update = null;
            try {
                update = download(context, info);
                PackageInfo archive = context.getPackageManager()
                        .getPackageArchiveInfo(update.localFile.getAbsolutePath(), 0);
                if (archive == null
                        || !context.getPackageName().equals(archive.packageName)
                        || !info.version.equals(archive.versionName)) {
                    throw new IOException("Downloaded update does not match the helper");
                }
                DownloadedUpdate finalUpdate = update;
                scheduleInstalledCheck(context, info.version);
                ShizukuHelper.install(context, finalUpdate.installFile, (ok, message) -> {
                    if (ok) {
                        notifyInstalled(context, info);
                    } else {
                        HelperLog.error("Shizuku update failed: " + message, null);
                        notifyUpdate(context, info);
                    }
                    finalUpdate.delete(context);
                });
            } catch (Exception error) {
                HelperLog.error("Update download failed", error);
                if (update != null) {
                    update.delete(context);
                }
                notifyUpdate(context, info);
            }
        }).start();
    }

    static DownloadedUpdate download(Context context, UpdateInfo info) throws IOException {
        File dir = context.getExternalFilesDir("updates");
        if (dir == null) {
            dir = new File(context.getFilesDir(), "updates");
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create update directory");
        }
        File local = new File(dir, "update.apk");
        //noinspection ResultOfMethodCallIgnored
        local.delete();
        Request request = new Request.Builder()
                .url(info.assetUrl)
                .header("User-Agent", UpdateChecker.USER_AGENT)
                .build();
        try (Response response = HTTP.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("Update download HTTP " + response.code());
            }
            try (InputStream input = body.byteStream();
                 FileOutputStream stream = new FileOutputStream(local)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    stream.write(buffer, 0, read);
                }
            }
        }

        Uri publicUri = null;
        File installFile = local;
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                StagedFile staged = stageInDownloads(context, local, info);
                if (staged != null) {
                    publicUri = staged.uri;
                    installFile = staged.file;
                    HelperLog.info("Update staged for Shizuku at " + installFile);
                }
            } catch (Exception error) {
                HelperLog.error("Could not stage update in Downloads", error);
            }
        }
        return new DownloadedUpdate(local, installFile, publicUri);
    }

    private static StagedFile stageInDownloads(Context context, File local, UpdateInfo info)
            throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String displayName = "MorpheHelper-v" + info.version + ".apk";
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE,
                "application/vnd.android.package-archive");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/Morphe Helper/updates");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("Could not create public update file");
        }
        try {
            try (InputStream input = new java.io.FileInputStream(local);
                 java.io.OutputStream output = resolver.openOutputStream(uri)) {
                if (output == null) {
                    throw new IOException("Could not open public update file");
                }
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, done, null, null);

            String actualName = displayName;
            try (Cursor cursor = resolver.query(uri,
                    new String[]{MediaStore.MediaColumns.DISPLAY_NAME},
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    actualName = cursor.getString(0);
                }
            }
            File publicDir = new File(
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS),
                    "Morphe Helper/updates");
            return new StagedFile(new File(publicDir, actualName), uri);
        } catch (Exception error) {
            try {
                resolver.delete(uri, null, null);
            } catch (Exception ignored) {
            }
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException("Could not copy update to Downloads", error);
        }
    }

    static final class DownloadedUpdate {
        final File localFile;
        final File installFile;
        private final Uri publicUri;

        DownloadedUpdate(File localFile, File installFile, Uri publicUri) {
            this.localFile = localFile;
            this.installFile = installFile;
            this.publicUri = publicUri;
        }

        void delete(Context context) {
            //noinspection ResultOfMethodCallIgnored
            localFile.delete();
            if (publicUri != null) {
                try {
                    context.getContentResolver().delete(publicUri, null, null);
                } catch (Exception ignored) {
                }
            } else if (!installFile.equals(localFile)) {
                //noinspection ResultOfMethodCallIgnored
                installFile.delete();
            }
        }
    }

    private static final class StagedFile {
        final File file;
        final Uri uri;

        StagedFile(File file, Uri uri) {
            this.file = file;
            this.uri = uri;
        }
    }

    private static void notifyInstalled(Context context, UpdateInfo info) {
        notifyInstalled(context, info.version);
    }

    static void notifyInstalled(Context context, String version) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        createChannel(manager);
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(context, 20, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = "Morphe Download Helper was updated to version " + version + ".";
        Notification.Builder builder = notificationBuilder(context)
                .setContentTitle("Morphe Download Helper updated")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true);
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private static void notifyUpdate(Context context, UpdateInfo info) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        createChannel(manager);
        Intent content = new Intent(context, UpdateActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentPending = PendingIntent.getActivity(context, 10, content,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = "Morphe Download Helper v" + info.version
                + " is available. Tap to see what's new and choose how to install it.";
        Notification.Builder builder = notificationBuilder(context)
                .setContentTitle("Update available")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(contentPending)
                .setAutoCancel(true);
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private static Notification.Builder notificationBuilder(Context context) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(context, CHANNEL);
        } else {
            builder = new Notification.Builder(context);
        }
        return builder.setSmallIcon(R.drawable.ic_logo_mark)
                .setPriority(Notification.PRIORITY_LOW);
    }

    private static void createChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, "Updates", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Morphe Download Helper updates");
            manager.createNotificationChannel(channel);
        }
    }

    private static void cancelNotification(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

    private static void callbackDone(Callback callback, String message) {
        if (callback != null) {
            MAIN.post(() -> callback.done(message));
        }
    }
}






