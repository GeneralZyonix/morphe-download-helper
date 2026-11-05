package be.alterra.morphehelper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

/** Keeps the helper foreground while a download is running. */
public class DownloadService extends Service {

    static final String ACTION_START = "be.alterra.morphehelper.DOWNLOAD_START";
    static final String ACTION_UPDATE = "be.alterra.morphehelper.DOWNLOAD_UPDATE";
    static final String ACTION_STOP = "be.alterra.morphehelper.DOWNLOAD_STOP";
    static final String EXTRA_TEXT = "text";
    static final String EXTRA_PROGRESS = "progress";
    static final String EXTRA_INDETERMINATE = "indeterminate";

    private static final String CHANNEL = "downloads";
    private static final int NOTIFICATION_ID = 41;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, getString(R.string.notif_channel_downloads),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.notif_channel_downloads_desc));
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        String text = intent.getStringExtra(EXTRA_TEXT);
        int progress = intent.getIntExtra(EXTRA_PROGRESS, 0);
        boolean indeterminate = intent.getBooleanExtra(EXTRA_INDETERMINATE, true);
        startForeground(NOTIFICATION_ID, notification(text, progress, indeterminate));
        return START_NOT_STICKY;
    }

    private Notification notification(String text, int progress, boolean indeterminate) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_logo_mark)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text == null
                        ? getString(R.string.download_notification_default) : text)
                .setContentIntent(pending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        if (indeterminate) {
            builder.setProgress(0, 0, true);
        } else {
            builder.setProgress(100, Math.max(0, Math.min(100, progress)), false);
        }
        return builder.build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
