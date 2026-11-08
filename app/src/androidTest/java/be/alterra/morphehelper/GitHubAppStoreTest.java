package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class GitHubAppStoreTest {

    private Context context;
    private Map<String, ?> savedEntries;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        savedEntries = new HashMap<>(prefs().getAll());
        clear();
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

    private void clear() {
        prefs().edit().clear().commit();
    }

    @Test
    public void normalizeRepoAcceptsCommonForms() {
        assertEquals("owner/repo", GitHubAppStore.normalizeRepo("owner/repo"));
        assertEquals("owner/repo",
                GitHubAppStore.normalizeRepo("https://github.com/owner/repo"));
        assertEquals("owner/repo",
                GitHubAppStore.normalizeRepo(
                        "https://github.com/owner/repo/releases/tag/v1.0"));
        assertEquals("owner/repo",
                GitHubAppStore.normalizeRepo("github.com/owner/repo.git"));
        assertEquals("", GitHubAppStore.normalizeRepo("owner"));
        assertEquals("", GitHubAppStore.normalizeRepo(" \n "));
    }

    @Test
    public void autoRememberDoesNotReplaceManualMapping() {
        GitHubAppStore.put(context, "com.example.app", "manual/repo", "Manual",
                true, "universal", true);

        GitHubAppStore.put(context, "com.example.app", "auto/repo", "Auto");

        GitHubAppStore.Entry entry = GitHubAppStore.get(context, "com.example.app");
        assertNotNull(entry);
        assertEquals("manual/repo", entry.repo);
        assertEquals("Manual", entry.label);
        assertTrue(entry.preferFirst);
        assertEquals("universal", entry.assetFilter);
        assertTrue(entry.manual);
    }

    @Test
    public void exportIncludesOnlyManualMappingsAndImportRestoresThem() {
        GitHubAppStore.put(context, "com.example.manual", "owner/manual", "Manual app",
                true, "arm64", true);
        GitHubAppStore.put(context, "com.example.auto", "owner/auto", "Auto app");

        String export = PublicSettings.exportText(context, false);
        assertTrue(export.contains("github_apps=1"));
        assertTrue(export.contains("github_app_1_package=com.example.manual"));
        assertTrue(export.contains("github_app_1_repo=owner/manual"));
        assertTrue(export.contains("github_app_1_label=Manual app"));
        assertTrue(export.contains("github_app_1_filter=arm64"));
        assertTrue(export.contains("github_app_1_prefer_first=true"));
        assertFalse(export.contains("com.example.auto"));

        clear();
        PublicSettings.applyText(context, export);

        GitHubAppStore.Entry manual = GitHubAppStore.get(context, "com.example.manual");
        assertNotNull(manual);
        assertEquals("owner/manual", manual.repo);
        assertEquals("Manual app", manual.label);
        assertEquals("arm64", manual.assetFilter);
        assertTrue(manual.preferFirst);
        assertTrue(manual.manual);
        assertNull(GitHubAppStore.get(context, "com.example.auto"));
    }

    @Test
    public void startupMergeKeepsLocalMappingsAndAddsMissingOnes() {
        GitHubAppStore.put(context, "com.example.keep", "keep/repo", "Keep",
                true, "", true);

        List<GitHubAppStore.Entry> imported = new ArrayList<>();
        imported.add(new GitHubAppStore.Entry(
                "com.example.keep", "file/repo", "From file",
                System.currentTimeMillis(), false, "", true));
        imported.add(new GitHubAppStore.Entry(
                "com.example.new", "new/repo", "New app",
                System.currentTimeMillis(), false, "foss", true));
        GitHubAppStore.mergeMissingManual(context, imported);

        GitHubAppStore.Entry kept = GitHubAppStore.get(context, "com.example.keep");
        assertNotNull(kept);
        assertEquals("keep/repo", kept.repo);
        assertTrue(kept.preferFirst);

        GitHubAppStore.Entry added = GitHubAppStore.get(context, "com.example.new");
        assertNotNull(added);
        assertEquals("new/repo", added.repo);
        assertEquals("foss", added.assetFilter);
        assertFalse(added.preferFirst);
        assertTrue(added.manual);
    }

    @Test
    public void importTreatsEveryEntryAsUserAdded() {
        String text = "github_apps=1\n"
                + "github_app_1_package=com.example.shared\n"
                + "github_app_1_repo=https://github.com/someone/repo\n"
                + "github_app_1_label=Shared app\n"
                + "github_app_1_filter=\n"
                + "github_app_1_prefer_first=false\n";

        PublicSettings.applyText(context, text);

        GitHubAppStore.Entry entry = GitHubAppStore.get(context, "com.example.shared");
        assertNotNull(entry);
        assertEquals("someone/repo", entry.repo);
        assertEquals("Shared app", entry.label);
        assertFalse(entry.preferFirst);
        assertTrue(entry.manual);
    }
}
