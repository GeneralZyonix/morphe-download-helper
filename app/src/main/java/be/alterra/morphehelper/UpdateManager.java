package be.alterra.morphehelper;

import android.app.Activity;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
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

    interface ReleasesCallback {
        void done(List<UpdateInfo> releases, String error);
    }

    private static final String PREFS = "update_settings";
    private static final String KEY_MODE = "mode";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_IN_APP_POPUP = "in_app_popup";
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
            // See UpdateChecker: HTTP/2 streams from GitHub are occasionally
            // reset with REFUSED_STREAM on mobile networks.
            .protocols(Collections.singletonList(Protocol.HTTP_1_1))
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final AtomicBoolean checking = new AtomicBoolean(false);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static String popupVersionShown;

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

    static boolean isInAppPopupEnabled(Context context) {
        return prefs(context).getBoolean(KEY_IN_APP_POPUP, false);
    }

    static void setInAppPopupEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_IN_APP_POPUP, enabled).apply();
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
                UpdateInfo info = UpdateChecker.check(
                        UpdateChecker.installedVersion(app));
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

    static void fetchReleasesAsync(ReleasesCallback callback) {
        new Thread(() -> {
            try {
                List<UpdateInfo> releases = UpdateChecker.releases(true);
                MAIN.post(() -> callback.done(releases, null));
            } catch (Exception error) {
                HelperLog.error("Could not load release history", error);
                MAIN.post(() -> callback.done(null, error.getMessage()));
            }
        }).start();
    }

    static void maybeCheckOnLaunch(Context context) {
        maybeCheckOnLaunch(context, null);
    }

    static void maybeCheckOnLaunch(Context context, Runnable after) {
        if (!isEnabled(context)) {
            runAfter(after);
            return;
        }
        long now = System.currentTimeMillis();
        if (now < prefs(context).getLong(KEY_SNOOZE_UNTIL, 0)) {
            runAfter(after);
            return;
        }
        if (now - lastCheck(context) < CHECK_INTERVAL) {
            runAfter(after);
            return;
        }
        checkAsync(context, false, message -> runAfter(after));
    }

    /**
     * Shows an in-app update dialog when the user opted in and an update is
     * available. This complements the system notification, which can be missed.
     */
    static void showInAppUpdatePopupIfAvailable(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (!isEnabled(activity) || !isInAppPopupEnabled(activity)) {
            return;
        }
        UpdateInfo info = cachedLatest(activity);
        if (info == null || !info.isNewerThan(UpdateChecker.installedVersion(activity))) {
            return;
        }
        if (info.version.equals(prefs(activity).getString(KEY_SKIPPED_VERSION, ""))) {
            return;
        }
        if (System.currentTimeMillis()
                < prefs(activity).getLong(KEY_SNOOZE_UNTIL, 0)) {
            return;
        }
        if (MODE_SHIZUKU.equals(mode(activity))) {
            // Automatic Shizuku updates replace the notification and popup flow.
            return;
        }
        if (info.version.equals(popupVersionShown)) {
            return;
        }
        popupVersionShown = info.version;
        String current = UpdateChecker.installedVersion(activity);
        ThemeManager.dialog(activity)
                .setTitle(R.string.update_popup_title)
                .setMessage(activity.getString(
                        R.string.update_popup_message, info.version, current))
                .setPositiveButton(R.string.update_popup_now, (dialog, which) -> {
                    cancelNotification(activity);
                    activity.startActivity(new Intent(activity, UpdateActivity.class));
                })
                .setNeutralButton(R.string.update_popup_later,
                        (dialog, which) -> remindLater(activity))
                .setNegativeButton(R.string.update_popup_skip,
                        (dialog, which) -> skipVersion(activity))
                .setOnCancelListener(dialog -> remindLater(activity))
                .show();
    }

    private static void runAfter(Runnable after) {
        if (after != null) {
            MAIN.post(after);
        }
    }

    static void checkAsync(Context context, boolean manual, Callback callback) {
        if (!manual && !isEnabled(context)) {
            callbackDone(callback, context.getString(R.string.update_msg_checks_disabled));
            return;
        }
        if (!checking.compareAndSet(false, true)) {
            callbackDone(callback, context.getString(R.string.update_msg_check_running));
            return;
        }
        Context app = context.getApplicationContext();
        new Thread(() -> {
            String message;
            try {
                String current = UpdateChecker.installedVersion(app);
                UpdateInfo info = UpdateChecker.check(current);
                cacheInfo(app, info);

                String skipped = prefs(app).getString(KEY_SKIPPED_VERSION, "");
                if (!info.isNewerThan(current)) {
                    cancelNotification(app);
                    message = app.getString(R.string.update_msg_up_to_date, current);
                } else if (info.version.equals(skipped)) {
                    cancelNotification(app);
                    message = app.getString(R.string.update_msg_skipped, info.version);
                } else if (!manual && System.currentTimeMillis()
                        < prefs(app).getLong(KEY_SNOOZE_UNTIL, 0)) {
                    message = app.getString(R.string.update_msg_snoozed);
                } else if (MODE_SHIZUKU.equals(mode(app))
                        && ShizukuHelper.status(app) == ShizukuHelper.READY
                        && !info.assetUrl.isEmpty()) {
                    message = app.getString(R.string.update_msg_found_installing, info.version);
                    installUpdate(app, info);
                } else {
                    notifyUpdate(app, info);
                    message = app.getString(R.string.update_msg_available, info.version);
                }
            } catch (Exception error) {
                HelperLog.error("Update check failed", error);
                message = app.getString(R.string.update_msg_failed, error.getMessage());
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
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            //noinspection ResultOfMethodCallIgnored
            local.delete();
            Request request = new Request.Builder()
                    .url(info.assetUrl)
                    .header("User-Agent", UpdateChecker.USER_AGENT)
                    .header("Accept", "application/octet-stream")
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
                last = null;
                break;
            } catch (IOException error) {
                last = error;
                HelperLog.info("Update download attempt " + (attempt + 1)
                        + " failed: " + error.getMessage());
                if (attempt < 2) {
                    try {
                        Thread.sleep(1500L * (attempt + 1));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw error;
                    }
                }
            }
        }
        if (last != null) {
            throw last;
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
        createChannel(context, manager);
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(context, 20, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = context.getString(R.string.notif_updated_text, version);
        Notification.Builder builder = notificationBuilder(context)
                .setContentTitle(context.getString(R.string.notif_updated_title))
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
        createChannel(context, manager);
        Intent content = new Intent(context, UpdateActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentPending = PendingIntent.getActivity(context, 10, content,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = context.getString(R.string.notif_update_text, info.version);
        Notification.Builder builder = notificationBuilder(context)
                .setContentTitle(context.getString(R.string.notif_update_title))
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

    private static void createChannel(Context context, NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, context.getString(R.string.notif_channel_updates),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(context.getString(R.string.notif_channel_updates_desc));
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






