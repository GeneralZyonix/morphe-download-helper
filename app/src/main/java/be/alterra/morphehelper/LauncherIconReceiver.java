package be.alterra.morphehelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Restores the saved launcher-icon choice after a reboot or app update.
 *
 * <p>The alias starts enabled so a fresh install always has a launcher icon.
 * It is disabled when the user turns off "Show app icon".</p>
 */
public class LauncherIconReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        HelperLog.init(context);
        HelperLog.info("LauncherIconReceiver: "
                + (intent == null ? "" : intent.getAction()));
        // Public settings may carry an explicit show_launcher_icon value after
        // an update, so apply them before restoring the component state.
        PublicSettings.apply(context);
        AppSettings.ensureLauncherIconState(context);
    }
}
