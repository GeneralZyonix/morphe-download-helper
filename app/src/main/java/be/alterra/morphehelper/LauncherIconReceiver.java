package be.alterra.morphehelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Restores the saved launcher-icon choice after a reboot or app update.
 *
 * <p>The launcher alias starts disabled in the manifest so Android 10+ does not
 * insert a synthetic "app info" shortcut after the user hides the icon. The
 * alias is enabled when the app runs, after an update, and after a reboot.</p>
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
