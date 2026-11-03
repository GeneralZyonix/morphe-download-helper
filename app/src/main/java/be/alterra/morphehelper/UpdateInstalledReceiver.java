package be.alterra.morphehelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class UpdateInstalledReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String version = intent == null
                ? null : intent.getStringExtra(UpdateManager.EXTRA_VERSION);
        if (version == null || version.isEmpty()) {
            return;
        }
        String installed = UpdateChecker.installedVersion(context);
        if (UpdateInfo.compareVersions(installed, version) >= 0) {
            UpdateManager.notifyInstalled(context, version);
        }
    }
}
