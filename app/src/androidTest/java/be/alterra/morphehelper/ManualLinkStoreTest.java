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
public class ManualLinkStoreTest {

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
        return context.getSharedPreferences("manual_links", Context.MODE_PRIVATE);
    }

    private void clear() {
        prefs().edit().clear().commit();
    }

    @Test
    public void normalizeUrlAcceptsCommonForms() {
        assertEquals("https://example.com/app.apk",
                ManualLinkStore.normalizeUrl("example.com/app.apk"));
        assertEquals("https://example.com/app.apk",
                ManualLinkStore.normalizeUrl("//example.com/app.apk"));
        assertEquals("https://example.com/app.apk?token=abc",
                ManualLinkStore.normalizeUrl(" https://example.com/app.apk?token=abc "));
        assertEquals("", ManualLinkStore.normalizeUrl("http://example.com/app.apk"));
        assertEquals("", ManualLinkStore.normalizeUrl(""));
        assertEquals("", ManualLinkStore.normalizeUrl("ftp://example.com/app.apk"));
        assertEquals("", ManualLinkStore.normalizeUrl("javascript:alert(1)"));
        assertEquals("", ManualLinkStore.normalizeUrl("https://"));
        assertEquals("", ManualLinkStore.normalizeUrl("not a url"));
    }

    @Test
    public void putUpdateAndRemoveMapping() {
        assertTrue(ManualLinkStore.put(context, "com.example.app",
                "example.com/app.apk", "Example", true, true));

        ManualLinkStore.Entry entry = ManualLinkStore.get(context, "com.example.app");
        assertNotNull(entry);
        assertEquals("Example", entry.label);
        assertEquals("https://example.com/app.apk", entry.url);
        assertTrue(entry.preferFirst);
        assertTrue(entry.enabled);

        assertTrue(ManualLinkStore.put(context, "com.example.app",
                "https://cdn.example.com/app.apk", "", false, false));
        entry = ManualLinkStore.get(context, "com.example.app");
        assertNotNull(entry);
        assertEquals("com.example.app", entry.label);
        assertFalse(entry.preferFirst);
        assertFalse(entry.enabled);

        ManualLinkStore.remove(context, "com.example.app");
        assertNull(ManualLinkStore.get(context, "com.example.app"));
    }

    @Test
    public void exportOmitsPrivateLinksUnlessSecretsAreIncluded() {
        ManualLinkStore.put(context, "com.example.app",
                "https://example.com/app.apk?token=secret", "Example", true, true);

        String normal = PublicSettings.exportText(context, false);
        assertFalse(normal.contains("token=secret"));
        assertFalse(normal.contains("manual_links="));

        String withSecrets = PublicSettings.exportText(context, true);
        assertTrue(withSecrets.contains("manual_links=1"));
        assertTrue(withSecrets.contains("manual_link_1_package=com.example.app"));
        assertTrue(withSecrets.contains("manual_link_1_url="
                + "https://example.com/app.apk?token=secret"));
        assertTrue(withSecrets.contains("manual_link_1_prefer_first=true"));
        assertTrue(withSecrets.contains("manual_link_1_enabled=true"));
    }

    @Test
    public void importRestoresLinksAndTreatsThemAsLocal() {
        String text = "manual_links=2\n"
                + "manual_link_1_package=com.example.first\n"
                + "manual_link_1_label=First app\n"
                + "manual_link_1_url=https://example.com/first.apk\n"
                + "manual_link_1_prefer_first=false\n"
                + "manual_link_1_enabled=false\n"
                + "manual_link_2_package=com.example.second\n"
                + "manual_link_2_label=Second app\n"
                + "manual_link_2_url=https://example.com/second.xapk\n"
                + "manual_link_2_prefer_first=true\n"
                + "manual_link_2_enabled=true\n";

        PublicSettings.applyText(context, text);

        ManualLinkStore.Entry first = ManualLinkStore.get(context, "com.example.first");
        assertNotNull(first);
        assertEquals("First app", first.label);
        assertFalse(first.preferFirst);
        assertFalse(first.enabled);

        ManualLinkStore.Entry second = ManualLinkStore.get(context, "com.example.second");
        assertNotNull(second);
        assertEquals("https://example.com/second.xapk", second.url);
        assertTrue(second.preferFirst);
        assertTrue(second.enabled);
    }

    @Test
    public void replaceAllRemovesOldEntries() {
        ManualLinkStore.put(context, "com.example.old",
                "https://example.com/old.apk", "Old", true, true);

        List<ManualLinkStore.Entry> imported = new ArrayList<>();
        imported.add(new ManualLinkStore.Entry(
                "com.example.old", "From import",
                "https://example.com/import.apk", 0L, false, true));
        imported.add(new ManualLinkStore.Entry(
                "com.example.new", "New",
                "https://example.com/new.apk", 0L, true, true));

        ManualLinkStore.replaceAll(context, imported);
        ManualLinkStore.Entry old = ManualLinkStore.get(context, "com.example.old");
        assertNotNull(old);
        assertEquals("From import", old.label);
        assertEquals("https://example.com/import.apk", old.url);
        assertNotNull(ManualLinkStore.get(context, "com.example.new"));
    }

    @Test
    public void invalidStoredJsonIsIgnored() {
        prefs().edit().putString("app:com.example.bad", "not json").commit();
        assertNull(ManualLinkStore.get(context, "com.example.bad"));
        assertTrue(ManualLinkStore.all(context).isEmpty());
    }
}
