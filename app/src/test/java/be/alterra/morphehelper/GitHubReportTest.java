package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.URLDecoder;

/** JVM tests for the GitHub report URL and log redaction. */
public class GitHubReportTest {

    @Test
    public void redactsTokensAndSignedPaths() {
        String log = "token=abc123\n"
                + "Authorization: Bearer ghp_abcdefghijklmnop\n"
                + "https://dw.uptodown.com/dwn/UlRuIA4R_WrzkAJGbqp6QvZLDuGAT326UC0QxBw=/moviebox.apk\n"
                + "https://data.winudf.com/XAPK/Y29tLmluc3RhZ3JhbS5hbmRyb2lkXzM4NTgxMTAwNg?token=secret\n";
        String redacted = GitHubReport.redact(log);
        assertFalse(redacted.contains("abc123"));
        assertFalse(redacted.contains("ghp_abcdefghijklmnop"));
        assertFalse(redacted.contains("UlRuIA4R_WrzkAJGbqp6QvZLDuGAT326UC0QxBw"));
        assertFalse(redacted.contains("secret"));
        assertTrue(redacted.contains("<redacted>"));
    }

    @Test
    public void excerptKeepsTheEndOfTheLog() {
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            log.append("line ").append(i).append('\n');
        }
        String excerpt = GitHubReport.excerpt(log.toString());
        assertTrue(excerpt.length() <= GitHubReport.MAX_LOG_FIELD_CHARS + 40);
        assertTrue(excerpt.contains("line 499"));
        assertTrue(excerpt.contains("earlier lines omitted"));
    }

    @Test
    public void extractsTheLastRequest() {
        String log = "Request pkg=com.first version=1.0 versionCodes=[1] fileType=apk\n"
                + "Request pkg=com.instagram.android app=Instagram "
                + "version=452.1.0.53.64 versionCodes=[385811006] fileType=apkm\n";
        assertEquals("Instagram 452.1.0.53.64 (com.instagram.android)",
                GitHubReport.lastRequest(log));
    }

    @Test
    public void fullLogPrefillOnlyForSmallLogs() {
        assertTrue(GitHubReport.canPrefillFullLog("small log"));
        assertFalse(GitHubReport.canPrefillFullLog(
                "x".repeat(GitHubReport.MAX_LOG_FIELD_CHARS + 1)));
    }

    @Test
    public void issueUrlCarriesAMarkdownBody() throws Exception {
        String url = GitHubReport.buildUrl(
                "2.3.1", "Google Pixel 8, Android 16", "2.6.0-dev.2",
                "Instagram 452.1.0.53.64 (com.instagram.android)",
                "log line", true);
        assertTrue(url.startsWith(GitHubReport.NEW_ISSUE));
        assertTrue(url.contains("labels=bug"));
        assertTrue(url.contains("body="));
        String body = URLDecoder.decode(
                url.substring(url.indexOf("&body=") + 6), "UTF-8");
        assertTrue(body.contains("### Helper version"));
        assertTrue(body.contains("2.3.1"));
        assertTrue(body.contains("### Morphe version"));
        assertTrue(body.contains("2.6.0-dev.2"));
        assertTrue(body.contains("Instagram 452.1.0.53.64"));
        assertTrue(body.contains("<details>"));
        assertTrue(body.contains("```text"));
        assertTrue(body.contains("log line"));
        assertTrue(url.length() <= GitHubReport.MAX_URL_LENGTH);
    }

    @Test
    public void noteInsteadOfLogDoesNotUseACodeBlock() throws Exception {
        String url = GitHubReport.buildUrl(
                "2.3.1", "Pixel", "2.6.0", "App",
                "Full log copied to the clipboard.", false);
        String body = URLDecoder.decode(
                url.substring(url.indexOf("&body=") + 6), "UTF-8");
        assertTrue(body.contains("Full log copied to the clipboard."));
        assertFalse(body.contains("```text"));
    }

    @Test
    public void hugeLogStillProducesABoundedUrl() {
        String url = GitHubReport.buildUrl(
                "2.3.1", "Pixel", "2.6.0", "App",
                "x".repeat(100_000), true);
        assertTrue(url.length() <= GitHubReport.MAX_URL_LENGTH);
        assertTrue(url.contains("body="));
    }
}
