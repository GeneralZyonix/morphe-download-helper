package be.alterra.morphehelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Restores the saved launcher-icon choice after a reboot or app update.
 *
 * <p>The launcher alias starts disabled in the manifest so Android does not
 * leave a synthetic icon behind when the user hides it. The alias is enabled
 * again when the saved setting says the icon should be visible.</p>
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
