package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ShizukuPackageTest {

    private Context app;

    @Before
    public void setUp() {
        app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppSettings.setShizukuPackage(app, "");
    }

    @After
    public void tearDown() {
        AppSettings.setShizukuPackage(app, "");
    }

    @Test
    public void customPackageIsStoredAndReset() {
        assertEquals(AppSettings.SHIZUKU_PACKAGE, AppSettings.shizukuPackage(app));

        AppSettings.setShizukuPackage(app, "  com.example.shizuku.next  ");
        assertEquals("com.example.shizuku.next", AppSettings.shizukuPackage(app));

        AppSettings.setShizukuPackage(app, "");
        assertEquals(AppSettings.SHIZUKU_PACKAGE, AppSettings.shizukuPackage(app));
    }

    @Test
    public void packageValidationAcceptsForksAndRejectsInvalidNames() {
        assertTrue(AppSettings.isValidPackageName("moe.shizuku.privileged.api"));
        assertTrue(AppSettings.isValidPackageName("com.example.shizuku_next"));
        assertTrue(AppSettings.isValidPackageName("io.github.fork2.shizuku"));

        assertFalse(AppSettings.isValidPackageName(""));
        assertFalse(AppSettings.isValidPackageName("shizuku"));
        assertFalse(AppSettings.isValidPackageName("com..shizuku"));
        assertFalse(AppSettings.isValidPackageName("com.example.1shizuku"));
        assertFalse(AppSettings.isValidPackageName("com.example.shizuku-next"));
    }

    @Test
    public void exportedSettingsContainConfiguredPackage() {
        AppSettings.setShizukuPackage(app, "com.example.shizuku.next");
        String text = PublicSettings.exportText(app, false);
        assertTrue(text.contains("shizuku_package=com.example.shizuku.next"));
    }

    @Test
    public void importedSettingsApplyConfiguredPackage() {
        PublicSettings.applyText(app, "shizuku_package=com.example.shizuku.next\n");
        assertEquals("com.example.shizuku.next", AppSettings.shizukuPackage(app));

        PublicSettings.applyText(app, "shizuku_package=not a package\n");
        assertEquals("com.example.shizuku.next", AppSettings.shizukuPackage(app));
    }
}
