package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
public class GitHubSourceImporterTest {

    private Context context;
    private Map<String, ?> savedEntries;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        savedEntries = new HashMap<>(prefs().getAll());
        prefs().edit().clear().commit();
    }

    @After
    public void tearDown() {
        SharedPreferences.Editor editor = prefs().edit().clear();
        for (Map.Entry<String, ?> entry : savedEntries.entrySet()) {
            if (entry.getValue() instanceof String) {
                editor.putString(entry.getKey(), (String) entry.getValue());
            }
        }
        editor.commit();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences("github_apps", Context.MODE_PRIVATE);
    }

    @Test
    public void importsGitHubEntriesAndSkipsOthers() {
        String json = "["
                + "{\"id\":\"com.example.one\",\"url\":\"https://github.com/owner/one\","
                + "\"name\":\"One\"},"
                + "{\"id\":\"com.example.two\",\"url\":\"https://gitlab.com/owner/two\","
                + "\"name\":\"Two\"},"
                + "{\"id\":\"invalid\",\"url\":\"https://github.com/owner/three\"}"
                + "]";

        GitHubSourceImporter.Result result =
                GitHubSourceImporter.importJson(context, json);

        assertEquals(1, result.imported);
        assertEquals(2, result.skipped);
        GitHubAppStore.Entry entry = GitHubAppStore.get(context, "com.example.one");
        assertNotNull(entry);
        assertEquals("owner/one", entry.repo);
        assertEquals("One", entry.label);
        assertTrue(entry.manual);
    }

    @Test
    public void importsWrappedExportWithoutReplacingExistingManualMapping() {
        GitHubAppStore.put(context, "com.example.keep", "keep/repo", "Keep",
                true, "", true);
        String json = "{\"apps\":["
                + "{\"id\":\"com.example.keep\",\"url\":\"https://github.com/file/repo\"},"
                + "{\"packageName\":\"com.example.new\","
                + "\"sourceUrl\":\"https://github.com/new/repo\",\"label\":\"New\"}"
                + "]}";

        GitHubSourceImporter.Result result =
                GitHubSourceImporter.importJson(context, json);

        assertEquals(2, result.imported);
        GitHubAppStore.Entry kept = GitHubAppStore.get(context, "com.example.keep");
        assertNotNull(kept);
        assertEquals("keep/repo", kept.repo);
        GitHubAppStore.Entry added = GitHubAppStore.get(context, "com.example.new");
        assertNotNull(added);
        assertEquals("new/repo", added.repo);
        assertEquals("New", added.label);
        assertTrue(added.manual);
    }

    @Test
    public void findsRepoAndPackageInSharedText() {
        String text = "Try https://github.com/owner/repo/releases/tag/v1.0 "
                + "for com.example.app";
        assertEquals("owner/repo", GitHubSourceImporter.findGitHubRepo(text));
        assertEquals("com.example.app", GitHubSourceImporter.findPackageName(text));

        String play = "https://play.google.com/store/apps/details?id=com.example.play "
                + "https://github.com/owner/repo";
        assertEquals("com.example.play", GitHubSourceImporter.findPackageName(play));
        assertEquals("owner/repo", GitHubSourceImporter.findGitHubRepo(play));
    }

    @Test
    public void ignoresExportsWithoutGitHubEntries() {
        GitHubSourceImporter.Result result = GitHubSourceImporter.importJson(context,
                "{\"apps\":[{\"id\":\"com.example.app\","
                        + "\"url\":\"https://gitlab.com/owner/repo\"}]}");
        assertEquals(0, result.imported);
        assertEquals(1, result.skipped);
    }
}
