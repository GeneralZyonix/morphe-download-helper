package be.alterra.morphehelper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM tests for the fast source preflight rules. */
public class SourceProbeRulesTest {

    @Test
    public void apkCubeNoMatchPageIsDefinitive() {
        String html = "<div hidden id='S:2'><p>No match</p>"
                + "<h2>We don&#x27;t hold that package</h2>"
                + "<p>Package isn&#x27;t in the catalog.</p></div>";
        assertTrue(SourceProbeRules.apkCubeHtmlNoMatch(html));
    }

    @Test
    public void apkCubeResultPageIsNotSkipped() {
        String html = "<p>Your file</p><a href='/download?version=1.2.3'>Download</a>";
        assertFalse(SourceProbeRules.apkCubeHtmlNoMatch(html));
    }

    @Test
    public void apkCubeVerificationPageIsNotSkipped() {
        String html = "<p>No match</p><div>Verify to download</div>";
        assertFalse(SourceProbeRules.apkCubeHtmlNoMatch(html));
    }

    @Test
    public void apkCubeEmptyPageIsNotSkipped() {
        assertFalse(SourceProbeRules.apkCubeHtmlNoMatch(""));
        assertFalse(SourceProbeRules.apkCubeHtmlNoMatch(null));
    }

    @Test
    public void apkContentTypesAreRecognized() {
        assertTrue(SourceProbeRules.looksLikeApk(
                "application/vnd.android.package-archive", null));
        assertTrue(SourceProbeRules.looksLikeApk(null,
                "attachment; filename=Example.apk"));
        assertTrue(SourceProbeRules.looksLikeApk(null,
                "attachment; filename=Example.xapk"));
        assertTrue(SourceProbeRules.looksLikeApk("application/octet-stream", null));
    }

    @Test
    public void htmlAndMissingTypesAreRejected() {
        assertFalse(SourceProbeRules.looksLikeApk("text/html; charset=utf-8", null));
        assertFalse(SourceProbeRules.looksLikeApk(null, null));
    }
}
