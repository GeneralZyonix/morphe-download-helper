package be.alterra.morphehelper;

import java.util.Locale;

/** Small, testable rules used by the fast source probes. */
final class SourceProbeRules {

    private SourceProbeRules() {
    }

    /** True when a probe response looks like an APK/XAPK rather than an HTML page. */
    static boolean looksLikeApk(String contentType, String disposition) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.US);
        String name = disposition == null ? "" : disposition.toLowerCase(Locale.US);
        if (name.contains(".apk") || name.contains(".xapk")
                || name.contains(".apks") || name.contains(".apkm")) {
            return true;
        }
        return type.contains("android.package-archive")
                || type.contains("application/octet-stream")
                || type.contains("binary/octet-stream");
    }

    /**
     * True when APKCube's server-rendered HTML is a definitive no-match page.
     * A verification/download marker makes the page ambiguous, so it is left
     * to the normal WebView flow.
     */
    static boolean apkCubeHtmlNoMatch(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        String lower = html.toLowerCase(Locale.US);
        boolean hasDownloadMarker = lower.contains("/download?")
                || lower.contains("download.php")
                || lower.contains("verify to download")
                || lower.contains("checking your browser")
                || lower.contains("turnstile")
                || lower.contains("captcha");
        if (hasDownloadMarker) {
            return false;
        }
        return lower.contains("no match")
                || lower.contains("hold that package")
                || lower.contains("in the catalog");
    }

    /**
     * True when an APKMirror HTTP failure should hand off to the WebView.
     * Cloudflare can challenge or block the HTTP client even when the WebView
     * can still load the verification page; timeouts and connection failures
     * are treated the same way so the user gets a chance to verify.
     */
    static boolean apkMirrorWebFallback(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }
        if (errorMessage.contains("HTTP 403")) {
            return true;
        }
        String lower = errorMessage.toLowerCase(Locale.US);
        return lower.contains("timeout") || lower.contains("timed out")
                || lower.contains("connection") || lower.contains("connect")
                || lower.contains("network") || lower.contains("socket")
                || lower.contains("ssl") || lower.contains("unknown host")
                || lower.contains("unknownhost")
                || lower.contains("unable to resolve host");
    }
}
