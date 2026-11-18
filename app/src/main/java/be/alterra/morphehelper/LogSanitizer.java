package be.alterra.morphehelper;

import java.util.regex.Pattern;

/**
 * Redacts secrets and personal identifiers from diagnostic logs before they are
 * copied, shared, saved, or attached to a public report.
 *
 * <p>The raw log stays on the device for debugging. Every export path must go
 * through {@link #sanitize(String)} or {@link #sanitizeForExport(String)}.</p>
 */
final class LogSanitizer {

    static final String REDACTED = "<redacted>";

    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----",
            Pattern.DOTALL);
    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b");
    private static final Pattern GITHUB_TOKEN = Pattern.compile(
            "(?i)(gh[pousr]_[A-Za-z0-9_]{10,}|github_pat_[A-Za-z0-9_]{10,})");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)(Bearer\\s+)[A-Za-z0-9._~+/-]+=*");
    private static final Pattern BASIC_AUTH = Pattern.compile(
            "(?i)(Basic\\s+)[A-Za-z0-9+/=]+");
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(authorization\\s*:\\s*)[^\\r\\n]+");
    private static final Pattern COOKIE = Pattern.compile(
            "(?i)((?:set-)?cookie\\s*:\\s*)[^\\r\\n]+");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)((?:password|passwd|pwd|secret|token|api[_-]?key|"
                    + "access[_-]?token|refresh[_-]?token|id[_-]?token|"
                    + "client[_-]?secret|private[_-]?key|auth|"
                    + "authorization)\\s*[\"']?\\s*[=:]\\s*[\"']?)"
                    + "[^\\s,;}\"']+");
    private static final Pattern QUERY_SECRET = Pattern.compile(
            "(?i)([?&](?:token|key|signature|sig|hmac|expires|access_token|"
                    + "refresh_token|id_token|jwt|auth|apikey|api_key|policy|"
                    + "googleaccessid|k|x-amz-[^=&]+|x-goog-[^=&]+)=)[^&\\s]+");
    private static final Pattern UPTODOWN_SIGNED_PATH = Pattern.compile(
            "(?i)(/dwn/)[^\\s/]{24,}");
    private static final Pattern WINUDF_SIGNED_PATH = Pattern.compile(
            "(?i)(data\\.winudf\\.com/[A-Za-z0-9]+/)[^\\s?/]{24,}");
    private static final Pattern URL_USERINFO = Pattern.compile(
            "(?i)(https?://)[^/\\s@]+@");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern IPV4 = Pattern.compile(
            "\\b(?:10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
                    + "|192\\.168\\.\\d{1,3}\\.\\d{1,3}"
                    + "|172\\.(?:1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3}"
                    + "|127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
                    + "|169\\.254\\.\\d{1,3}\\.\\d{1,3})\\b");
    private static final Pattern BRACKETED_IPV6 = Pattern.compile(
            "\\[[0-9A-Fa-f:]+\\]");
    private static final Pattern LOCAL_IPV6 = Pattern.compile(
            "(?i)(from /)[0-9a-f:]{6,}");

    private LogSanitizer() {
    }

    /** Redacts secrets, signed URL parameters, and personal identifiers. */
    static String sanitize(String log) {
        if (log == null || log.isEmpty()) {
            return "";
        }
        String value = PRIVATE_KEY.matcher(log)
                .replaceAll("<redacted-private-key>");
        value = JWT.matcher(value).replaceAll("<redacted-jwt>");
        value = GITHUB_TOKEN.matcher(value).replaceAll(REDACTED);
        value = BEARER.matcher(value).replaceAll("$1" + REDACTED);
        value = BASIC_AUTH.matcher(value).replaceAll("$1" + REDACTED);
        value = AUTHORIZATION.matcher(value).replaceAll("$1" + REDACTED);
        value = COOKIE.matcher(value).replaceAll("$1" + REDACTED);
        value = SECRET_ASSIGNMENT.matcher(value).replaceAll("$1" + REDACTED);
        value = QUERY_SECRET.matcher(value).replaceAll("$1" + REDACTED);
        value = UPTODOWN_SIGNED_PATH.matcher(value).replaceAll("$1" + REDACTED);
        value = WINUDF_SIGNED_PATH.matcher(value).replaceAll("$1" + REDACTED);
        value = URL_USERINFO.matcher(value).replaceAll("$1" + REDACTED + "@");
        value = EMAIL.matcher(value).replaceAll("<redacted-email>");
        value = IPV4.matcher(value).replaceAll("<redacted-ip>");
        value = BRACKETED_IPV6.matcher(value).replaceAll("[<redacted-ip>]");
        value = LOCAL_IPV6.matcher(value).replaceAll("$1<redacted-ip>");
        return value;
    }

    /**
     * Sanitizes a log and prepends a short notice that makes it clear the copy
     * is safe to review but still worth checking before posting.
     */
    static String sanitizeForExport(String log) {
        String sanitized = sanitize(log);
        if (sanitized.trim().isEmpty()) {
            return "";
        }
        return "Sanitized helper log. Tokens, cookies, auth headers, signed URL "
                + "parameters, local IP addresses, and email addresses are redacted.\n"
                + "Review before posting or sharing.\n\n"
                + sanitized;
    }
}
