package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class SegmentedDownloadTest {

    private Context context;
    private Map<String, ?> savedSettings;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        savedSettings = new HashMap<>(prefs().getAll());
    }

    @After
    public void tearDown() {
        SharedPreferences.Editor editor = prefs().edit().clear();
        for (Map.Entry<String, ?> entry : savedSettings.entrySet()) {
            if (entry.getValue() instanceof String) {
                editor.putString(entry.getKey(), (String) entry.getValue());
            } else if (entry.getValue() instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) entry.getValue());
            } else if (entry.getValue() instanceof Integer) {
                editor.putInt(entry.getKey(), (Integer) entry.getValue());
            } else if (entry.getValue() instanceof Long) {
                editor.putLong(entry.getKey(), (Long) entry.getValue());
            }
        }
        editor.commit();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
    }

    @Test
    public void connectionCountCapsBySizeAndSetting() {
        long mib = 1024L * 1024L;
        assertEquals(0, DownloadActivity.segmentedConnectionCount(4 * mib, 32));
        assertEquals(2, DownloadActivity.segmentedConnectionCount(5 * mib, 4));
        assertEquals(2, DownloadActivity.segmentedConnectionCount(10 * mib, 4));
        assertEquals(4, DownloadActivity.segmentedConnectionCount(20 * mib, 4));
        assertEquals(8, DownloadActivity.segmentedConnectionCount(40 * mib, 8));
        assertEquals(25, DownloadActivity.segmentedConnectionCount(100 * mib, 32));
        assertEquals(2, DownloadActivity.segmentedConnectionCount(100 * mib, 2));
        assertEquals(32, DownloadActivity.segmentedConnectionCount(200 * mib, 32));
    }

    @Test
    public void maxConnectionsClampsToAllowedOptions() {
        assertEquals(2, AppSettings.clampMaxConnections(1));
        assertEquals(2, AppSettings.clampMaxConnections(2));
        assertEquals(4, AppSettings.clampMaxConnections(7));
        assertEquals(8, AppSettings.clampMaxConnections(9));
        assertEquals(32, AppSettings.clampMaxConnections(40));
    }

    @Test
    public void maxConnectionsRoundTripsThroughPublicSettings() {
        AppSettings.setMaxConnections(context, 16);
        String export = PublicSettings.exportText(context, false);
        assertTrue(export.contains("max_connections=16"));

        AppSettings.setMaxConnections(context, 2);
        PublicSettings.applyText(context, export);
        assertEquals(16, AppSettings.maxConnections(context));
    }
}
