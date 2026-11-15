package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Verifies the launcher alias state machine and the notification artwork. */
@RunWith(AndroidJUnit4.class)
public class LauncherIconTest {

    @Test
    public void launcherAliasFollowsTheSavedChoice() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        boolean original = AppSettings.isLauncherIconShown(context);
        ComponentName alias = new ComponentName(context.getPackageName(),
                context.getPackageName() + ".LauncherEntry");
        PackageManager manager = context.getPackageManager();
        try {
            AppSettings.setLauncherIconShown(context, false);
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    manager.getComponentEnabledSetting(alias));

            AppSettings.setLauncherIconShown(context, true);
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    manager.getComponentEnabledSetting(alias));
        } finally {
            AppSettings.setLauncherIconShown(context, original);
        }
    }

    @Test
    public void notificationIconIsAvailable() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertNotNull(context.getDrawable(R.drawable.ic_stat_morphe));
    }
}
