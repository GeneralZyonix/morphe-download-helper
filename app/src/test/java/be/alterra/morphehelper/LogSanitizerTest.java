package be.alterra.morphehelper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM tests for the log export sanitizer. */
public class LogSanitizerTest {

    @Test
    public void redactsTokensAndAuthHeaders() {
        String log = "Authorization: Bearer ghp_abcdefghijklmnop\n"
                + "Cookie: sessionid=abc123; theme=dark\n"
                + "password=hunter2\n"
                + "api_key: super-secret\n"
                + "Basic dXNlcjpwYXNz\n"
                + "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature123";
        String sanitized = LogSanitizer.sanitize(log);
        assertFalse(sanitized.contains("ghp_abcdefghijklmnop"));
        assertFalse(sanitized.contains("sessionid=abc123"));
        assertFalse(sanitized.contains("hunter2"));
        assertFalse(sanitized.contains("super-secret"));
        assertFalse(sanitized.contains("dXNlcjpwYXNz"));
        assertFalse(sanitized.contains("eyJhbGciOiJIUzI1NiJ9"));
        assertTrue(sanitized.contains("<redacted>"));
    }

    @Test
    public void redactsSignedUrlsAndPersonalIdentifiers() {
        String log = "https://user:pass@example.com/file\n"
                + "https://dw.uptodown.com/dwn/UlRuIA4R_WrzkAJGbqp6QvZLDuGAT326UC0QxBw=/app.apk\n"
                + "https://data.winudf.com/XAPK/Y29tLmluc3RhZ3JhbS5hbmRyb2lk?token=secret\n"
                + "https://cdn.example.com/file?X-Amz-Signature=deadbeef&k=feedface\n"
                + "contact someone@example.com from 192.168.1.80\n"
                + "route=host/[2606:4700:113::1]:443\n";
        String sanitized = LogSanitizer.sanitize(log);
        assertFalse(sanitized.contains("user:pass"));
        assertFalse(sanitized.contains("UlRuIA4R_WrzkAJGbqp6QvZLDuGAT326UC0QxBw"));
        assertFalse(sanitized.contains("secret"));
        assertFalse(sanitized.contains("deadbeef"));
        assertFalse(sanitized.contains("feedface"));
        assertFalse(sanitized.contains("someone@example.com"));
        assertFalse(sanitized.contains("192.168.1.80"));
        assertFalse(sanitized.contains("2606:4700:113::1"));
        assertTrue(sanitized.contains("<redacted>"));
    }

    @Test
    public void keepsUsefulDiagnostics() {
        String log = "Request pkg=com.instagram.android app=Instagram "
                + "version=452.1.0.53.64 versionCodes=[385811006] fileType=apkm\n"
                + "APKMirror page is for com.instagram.android, expected com.instagram.android\n"
                + "Version 1.2.3.4 is a normal app version\n"
                + "Download timing: dns=28 connect=86 tls=65 total=8476 ms";
        String sanitized = LogSanitizer.sanitize(log);
        assertTrue(sanitized.contains("com.instagram.android"));
        assertTrue(sanitized.contains("452.1.0.53.64"));
        assertTrue(sanitized.contains("Version 1.2.3.4"));
        assertTrue(sanitized.contains("Download timing"));
    }

    @Test
    public void exportCopyCarriesANotice() {
        String text = LogSanitizer.sanitizeForExport("token=secret\nline");
        assertTrue(text.startsWith("Sanitized helper log."));
        assertTrue(text.contains("Review before posting"));
        assertFalse(text.contains("secret"));
        assertTrue(text.contains("line"));
    }
}
