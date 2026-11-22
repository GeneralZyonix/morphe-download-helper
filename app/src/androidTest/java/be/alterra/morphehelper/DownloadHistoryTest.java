package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

/** Instrumentation tests for the persisted download history. */
@RunWith(AndroidJUnit4.class)
public class DownloadHistoryTest {

    private Context app;
    private File file;

    @Before
    public void setUp() throws Exception {
        app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DownloadHistory.clear(app);
        file = File.createTempFile("history-", ".apk", app.getCacheDir());
    }

    @After
    public void tearDown() {
        DownloadHistory.clear(app);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    @Test
    public void sourceUrlSurvivesSerialization() {
        DownloadHistory.record(app, "Example", "com.example.app", "1.0",
                "apk", "APKMirror", "https://example.com/app.apk", file);
        List<DownloadHistory.Entry> entries = DownloadHistory.list(app);
        assertEquals(1, entries.size());
        assertEquals("https://example.com/app.apk", entries.get(0).sourceUrl);
        assertEquals("com.example.app", entries.get(0).packageName);
    }
}
