package be.alterra.morphehelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Runs an automatic update check scheduled by {@link UpdateManager}. */
public class UpdateCheckReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        UpdateManager.runScheduledCheck(context);
    }
}
