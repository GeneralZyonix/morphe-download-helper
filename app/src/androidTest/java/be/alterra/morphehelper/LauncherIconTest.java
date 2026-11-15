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

/** Verifies the launcher alias follows this build's fixed variant. */
@RunWith(AndroidJUnit4.class)
public class LauncherIconTest {

    @Test
    public void launcherAliasMatchesTheBuildVariant() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        boolean shown = context.getResources().getBoolean(R.bool.launcher_icon_enabled);
        ComponentName alias = new ComponentName(context.getPackageName(),
                context.getPackageName() + ".LauncherEntry");
        PackageManager manager = context.getPackageManager();

        AppSettings.ensureLauncherIconState(context);

        assertEquals(shown
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                manager.getComponentEnabledSetting(alias));
    }

    @Test
    public void notificationIconIsAvailable() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertNotNull(context.getDrawable(R.drawable.ic_stat_morphe));
    }
}
