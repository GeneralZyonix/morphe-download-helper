package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/** Smoke tests for the v3 Morphe shell prototype. */
@RunWith(AndroidJUnit4.class)
public class MorphePreviewTest {

    @Test
    public void shellShowsTheFourDestinationsAndHero() {
        try (ActivityScenario<MorphePreviewActivity> scenario =
                     ActivityScenario.launch(MorphePreviewActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                assertTrue(containsText(root, "Home"));
                assertTrue(containsText(root, "Downloads"));
                assertTrue(containsText(root, "Sources"));
                assertTrue(containsText(root, "Settings"));
                assertTrue(containsText(root, "Fetch the original APK."));
            });
        }
    }

    @Test
    public void downloadsTabShowsTheLocalApkList() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DownloadHistory.clear(context);
        try (ActivityScenario<MorphePreviewActivity> scenario =
                     ActivityScenario.launch(MorphePreviewActivity.class)) {
            scenario.onActivity(activity -> {
                clickTab(activity, "Downloads");
                assertTrue(containsText(activity.getWindow().getDecorView(),
                        "Recent hand-offs and kept APKs"));
            });
        }
    }

    @Test
    public void downloadHistoryPersistsAndRemovesEntries() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DownloadHistory.clear(context);
        File first = new File(context.getCacheDir(), "history-one.apk");
        File second = new File(context.getCacheDir(), "history-two.apk");
        DownloadHistory.record(context, "History One", "com.example.one",
                "1.0", "apk", "Test source", first);
        DownloadHistory.record(context, "History Two", "com.example.two",
                "2.0", "apk", "Test source", second);

        assertEquals(2, DownloadHistory.list(context).size());
        String firstId = DownloadHistory.list(context).get(1).id;
        DownloadHistory.remove(context, firstId);
        assertEquals(1, DownloadHistory.list(context).size());
        DownloadHistory.clear(context);
        assertTrue(DownloadHistory.list(context).isEmpty());
    }

    @Test
    public void removedHistoryEntryStillAppearsInDownloads() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DownloadHistory.clear(context);
        File missing = new File(context.getCacheDir(), "missing-history.apk");
        DownloadHistory.record(context, "Missing History App", "com.example.missing",
                "3.0", "apk", "Test source", missing);
        try (ActivityScenario<MorphePreviewActivity> scenario =
                     ActivityScenario.launch(MorphePreviewActivity.class)) {
            scenario.onActivity(activity -> {
                clickTab(activity, "Downloads");
                View root = activity.getWindow().getDecorView();
                assertTrue(containsText(root, "Missing History App"));
                assertTrue(containsPartialText(root, "Removed"));
            });
        } finally {
            DownloadHistory.clear(context);
        }
    }

    @Test
    public void sourcesTabEmbedsTheSourceControls() {
        try (ActivityScenario<MorphePreviewActivity> scenario =
                     ActivityScenario.launch(MorphePreviewActivity.class)) {
            scenario.onActivity(activity -> {
                clickTab(activity, "Sources");
                View root = activity.getWindow().getDecorView();
                assertTrue(containsText(root, "Changes apply immediately."));
                assertTrue(containsText(root, "Morphe link"));
            });
        }
    }

    @Test
    public void settingsTabShowsSectionShortcuts() {
        try (ActivityScenario<MorphePreviewActivity> scenario =
                     ActivityScenario.launch(MorphePreviewActivity.class)) {
            scenario.onActivity(activity -> {
                clickTab(activity, "Settings");
                View root = activity.getWindow().getDecorView();
                assertTrue(containsText(root,
                        "Open a section in the full settings screen."));
                assertTrue(containsText(root, "Storage"));
            });
        }
    }

    private static void clickTab(MorphePreviewActivity activity, String label) {
        TextView view = findText(activity.getWindow().getDecorView(), label);
        assertNotNull("Missing tab label: " + label, view);
        View target = view;
        while (target != null && !target.isClickable()) {
            target = target.getParent() instanceof View
                    ? (View) target.getParent() : null;
        }
        assertNotNull("Tab label is not clickable: " + label, target);
        target.performClick();
    }

    private static boolean containsText(View view, String text) {
        return findText(view, text) != null;
    }

    private static boolean containsPartialText(View view, String text) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && value.toString().contains(text)) {
                return true;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsPartialText(group.getChildAt(i), text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static TextView findText(View view, String text) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && text.contentEquals(value)) {
                return (TextView) view;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView match = findText(group.getChildAt(i), text);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}
