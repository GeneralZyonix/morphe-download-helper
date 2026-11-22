package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Instrumentation tests for the two update channels. */
@RunWith(AndroidJUnit4.class)
public class UpdateChannelTest {

    private Context app;
    private SharedPreferences prefs;
    private String previous;
    private int previousInterval;
    private boolean hadInterval;

    @Before
    public void setUp() {
        app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = app.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        previous = prefs.getString("update_channel", null);
        hadInterval = prefs.contains("update_interval_hours");
        previousInterval = prefs.getInt("update_interval_hours",
                AppSettings.UPDATE_INTERVAL_DEFAULT);
    }

    @After
    public void tearDown() {
        if (previous == null) {
            prefs.edit().remove("update_channel").apply();
        } else {
            prefs.edit().putString("update_channel", previous).apply();
        }
        if (hadInterval) {
            prefs.edit().putInt("update_interval_hours", previousInterval).apply();
        } else {
            prefs.edit().remove("update_interval_hours").apply();
        }
    }

    @Test
    public void channelSelectionControlsPrereleases() {
        AppSettings.setUpdateChannel(app, AppSettings.UPDATE_CHANNEL_STABLE);
        assertFalse(UpdateManager.includePrereleases(app));

        AppSettings.setUpdateChannel(app, AppSettings.UPDATE_CHANNEL_PRERELEASE);
        assertTrue(UpdateManager.includePrereleases(app));
    }

    @Test
    public void legacyAutoMigratesToTheInstalledBuildChannel() {
        prefs.edit().putString("update_channel", "auto").apply();

        String expected = BuildConfig.PRERELEASE
                ? AppSettings.UPDATE_CHANNEL_PRERELEASE
                : AppSettings.UPDATE_CHANNEL_STABLE;
        assertEquals(expected, AppSettings.updateChannel(app));
        assertEquals(expected, prefs.getString("update_channel", ""));
    }

    @Test
    public void updateIntervalAcceptsOnlySupportedValues() {
        AppSettings.setUpdateCheckIntervalHours(app, 12);
        assertEquals(12, AppSettings.updateCheckIntervalHours(app));

        AppSettings.setUpdateCheckIntervalHours(app, 999);
        assertEquals(AppSettings.UPDATE_INTERVAL_DEFAULT,
                AppSettings.updateCheckIntervalHours(app));
    }
}
