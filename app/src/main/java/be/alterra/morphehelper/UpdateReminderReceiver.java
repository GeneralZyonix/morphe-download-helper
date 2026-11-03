package be.alterra.morphehelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class UpdateReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        UpdateManager.clearReminder(context);
        if (UpdateManager.isEnabled(context)) {
            UpdateManager.checkAsync(context, true, null);
        }
    }
}
