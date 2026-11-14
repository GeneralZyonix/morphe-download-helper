package be.alterra.morphehelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Restores the saved launcher-icon choice after a reboot or app update.
 *
 * <p>The launcher alias starts enabled in the manifest so a fresh install or
 * sideload update always has a launcher entry. The receiver disables it again
 * when the saved setting says the icon should be hidden.</p>
 */
public class LauncherIconReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // Public settings may carry an explicit show_launcher_icon value after
        // an update, so apply them before restoring the component state.
        PublicSettings.apply(context);
        AppSettings.ensureLauncherIconState(context);
    }
}
