package be.alterra.morphehelper;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Build;

import java.io.File;

import rikka.shizuku.Shizuku;

final class ShizukuHelper {

    static final int NOT_INSTALLED = 0;
    static final int NOT_RUNNING = 1;
    static final int PERMISSION_REQUIRED = 2;
    static final int READY = 3;
    static final int UNSUPPORTED = 4;

    interface Callback {
        void done(boolean ok, String message);
    }

    private ShizukuHelper() {
    }

    static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(
                    "moe.shizuku.privileged.api", 0);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    static int status(Context context) {
        if (!isInstalled(context)) {
            return NOT_INSTALLED;
        }
        try {
            if (!Shizuku.pingBinder()) {
                return NOT_RUNNING;
            }
            if (Shizuku.isPreV11()) {
                return UNSUPPORTED;
            }
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    ? READY : PERMISSION_REQUIRED;
        } catch (Exception ignored) {
            return NOT_RUNNING;
        }
    }

    static void requestPermission(int requestCode) {
        try {
            Shizuku.requestPermission(requestCode);
        } catch (Exception ignored) {
        }
    }

    static String statusLabel(Context context, int status) {
        switch (status) {
            case READY:
                return context.getString(R.string.shizuku_ready);
            case PERMISSION_REQUIRED:
                return context.getString(R.string.shizuku_permission_required);
            case NOT_RUNNING:
                return context.getString(R.string.shizuku_not_running);
            case NOT_INSTALLED:
                return context.getString(R.string.shizuku_not_installed);
            default:
                return context.getString(R.string.shizuku_unsupported);
        }
    }

    static void install(Context context, File apk, Callback callback) {
        int status = status(context);
        if (status != READY) {
            callback.done(false, context.getString(
                    R.string.shizuku_not_ready, statusLabel(context, status)));
            return;
        }

        Handler main = new Handler(Looper.getMainLooper());
        final boolean[] finished = {false};
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                new ComponentName(context, UpdateUserService.class))
                .daemon(false)
                .version(1)
                .processNameSuffix("update");

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                if (finished[0]) {
                    return;
                }
                try {
                    IUpdateService updateService = IUpdateService.Stub.asInterface(service);
                    HelperLog.info("Shizuku installing " + apk.getAbsolutePath());
                    int result = updateService.install(apk.getAbsolutePath());
                    HelperLog.info("Shizuku install result=" + result);
                    finished[0] = true;
                    Shizuku.unbindUserService(args, this, true);
                    main.post(() -> callback.done(result == 0,
                            result == 0
                                    ? context.getString(R.string.shizuku_update_installed)
                                    : context.getString(R.string.shizuku_install_exit, result)));
                } catch (Exception error) {
                    finished[0] = true;
                    Shizuku.unbindUserService(args, this, true);
                    main.post(() -> callback.done(false, String.valueOf(error.getMessage())));
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };

        try {
            Shizuku.bindUserService(args, connection);
            main.postDelayed(() -> {
                if (finished[0]) {
                    return;
                }
                finished[0] = true;
                try {
                    Shizuku.unbindUserService(args, connection, true);
                } catch (Exception ignored) {
                }
                callback.done(false, context.getString(R.string.shizuku_install_timeout));
            }, 30_000);
        } catch (Exception error) {
            callback.done(false, String.valueOf(error.getMessage()));
        }
    }
}


