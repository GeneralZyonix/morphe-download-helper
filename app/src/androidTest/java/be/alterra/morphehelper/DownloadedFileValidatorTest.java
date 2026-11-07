package be.alterra.morphehelper;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Fixture tests for DownloadedFileValidator.
 *
 * <p>The fixtures are tiny APKs generated from minimal manifests. Each test
 * builds a tiny XAPK in the cache directory from those APKs and a matching or
 * contradicting manifest.json.</p>
 */
@RunWith(AndroidJUnit4.class)
public class DownloadedFileValidatorTest {

    private Context app;
    private Context test;
    private File workDir;

    @Before
    public void setUp() {
        app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        test = InstrumentationRegistry.getInstrumentation().getContext();
        workDir = new File(app.getCacheDir(), "validator-fixtures");
        deleteRecursively(workDir);
        //noinspection ResultOfMethodCallIgnored
        workDir.mkdirs();
    }

    @After
    public void tearDown() {
        deleteRecursively(workDir);
    }

    @Test
    public void correctManifestAndApkPasses() throws Exception {
        File archive = archive("correct.xapk",
                manifest("com.example.fixture", "1.2.3", 123),
                Map.of("base.apk", asset("correct.apk")));
        validate(archive, "com.example.fixture", "1.2.3", new long[]{123});
    }

    @Test
    public void manifestOnlyArchiveIsRejected() throws Exception {
        File archive = archive("manifest-only.xapk",
                manifest("com.example.fixture", "1.2.3", 123),
                Map.of());
        assertThrows(IOException.class, () ->
                validate(archive, "com.example.fixture", "1.2.3", new long[]{123}));
    }

    @Test
    public void readableApkPackageBeatsManifest() throws Exception {
        File archive = archive("wrong-package.xapk",
                manifest("com.example.fixture", "1.2.3", 123),
                Map.of("base.apk", asset("wrong-package.apk")));
        assertThrows(IOException.class, () ->
                validate(archive, "com.example.fixture", "1.2.3", new long[]{123}));
    }

    @Test
    public void readableApkVersionBeatsManifest() throws Exception {
        File archive = archive("wrong-version.xapk",
                manifest("com.example.fixture", "1.2.3", 123),
                Map.of("base.apk", asset("wrong-version.apk")));
        assertThrows(IOException.class, () ->
                validate(archive, "com.example.fixture", "1.2.3", new long[]{123}));
    }

    @Test
    public void manifestVersionContradictingApkIsRejected() throws Exception {
        File archive = archive("lying-manifest.xapk",
                manifest("com.example.fixture", "9.9.9", 999),
                Map.of("base.apk", asset("correct.apk")));
        assertThrows(IOException.class, () ->
                validate(archive, "com.example.fixture", "1.2.3", new long[]{123}));
    }

    @Test
    public void unversionedRequestStillChecksPackage() throws Exception {
        File archive = archive("wrong-unversioned.xapk", null,
                Map.of("base.apk", asset("wrong-package.apk")));
        assertThrows(IOException.class, () ->
                validate(archive, "com.example.fixture", null, null));
    }

    @Test
    public void unversionedRequestForCorrectPackagePasses() throws Exception {
        File archive = archive("correct-unversioned.xapk", null,
                Map.of("base.apk", asset("correct.apk")));
        validate(archive, "com.example.fixture", null, null);
    }

    @Test
    public void manifestWithoutVersionNameUsesApkMetadata() throws Exception {
        File archive = archive("manifest-no-version-name.xapk",
                manifest("com.example.fixture", null, 123),
                Map.of("base.apk", asset("correct.apk")));
        validate(archive, "com.example.fixture", "1.2.3", new long[]{123});
    }

    @Test
    public void manifestFallbackStillWorksWhenApkCannotBeParsed() throws Exception {
        File archive = archive("unparseable-split.xapk",
                manifest("com.example.fixture", "1.2.3", 123),
                Map.of("base.apk", "this is not an apk".getBytes(StandardCharsets.UTF_8)));
        validate(archive, "com.example.fixture", "1.2.3", new long[]{123});
    }

    @Test
    public void unversionedWrongPackageWithOnlyUnparseableApkIsRejected()
            throws Exception {
        File archive = archive("unparseable-unversioned.xapk",
                manifest("com.example.other", "1.2.3", 123),
                Map.of("base.apk", "this is not an apk".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () ->
                validate(archive, "com.example.fixture", null, null));
    }

    private void validate(File file, String pkg, String version, long[] codes)
            throws Exception {
        Class<?> type = Class.forName("be.alterra.morphehelper.DownloadedFileValidator");
        Method method = type.getDeclaredMethod("validate", Context.class, File.class,
                String.class, String.class, long[].class);
        method.setAccessible(true);
        try {
            method.invoke(null, app, file, pkg, version, codes);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw error;
        }
    }

    private byte[] asset(String name) throws Exception {
        try (InputStream input = test.getAssets().open("fixtures/" + name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private File archive(String name, String manifestJson,
                         Map<String, byte[]> entries) throws Exception {
        File file = new File(workDir, name);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            if (manifestJson != null) {
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(manifestJson.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return file;
    }

    private static String manifest(String pkg, String versionName, long versionCode) {
        StringBuilder json = new StringBuilder("{\"package_name\":\"")
                .append(pkg).append("\",\"version_code\":").append(versionCode)
                .append(",\"min_sdk_version\":23");
        if (versionName != null) {
            json.append(",\"version_name\":\"").append(versionName).append('"');
        }
        return json.append('}').toString();
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
