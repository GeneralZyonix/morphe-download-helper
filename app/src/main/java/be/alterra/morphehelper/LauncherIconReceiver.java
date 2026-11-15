package be.alterra.morphehelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Re-applies this build's fixed launcher-icon state after a reboot or update.
 *
 * <p>The icon build always enables the alias; the no-icon build always keeps it
 * disabled. Neither build transitions from shown to hidden at runtime.</p>
 */
public class LauncherIconReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        HelperLog.init(context);
        HelperLog.info("LauncherIconReceiver: "
                + (intent == null ? "" : intent.getAction()));
        // Apply public settings after an update, then this build's fixed
        // launcher state.
        PublicSettings.apply(context);
        AppSettings.ensureLauncherIconState(context);
    }
}
