package be.alterra.morphehelper;

import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the pre-filled GitHub bug report URL and prepares log text for public
 * reports.
 *
 * <p>GitHub's issue-form field IDs are not reliably applied from a URL, so the
 * report uses the documented {@code title}, {@code labels}, and {@code body}
 * query parameters instead. The body is Markdown and puts the log in a
 * collapsible code block.</p>
 */
final class GitHubReport {

    static final String NEW_ISSUE =
            "https://github.com/GeneralZyonix/morphe-download-helper/issues/new";

    /** Keep the URL below what browsers and GitHub reliably accept. */
    static final int MAX_URL_LENGTH = 8_000;
    static final int MAX_LOG_FIELD_CHARS = 2_200;

    private static final Pattern LAST_REQUEST = Pattern.compile(
            "Request pkg=(\\S+) app=(.*?) version=(\\S+) versionCodes=\\[[^\\]]*\\]"
                    + " fileType=(\\S+)");

    private GitHubReport() {
    }

    /** Returns a short tail of the log for the pre-filled issue body. */
    static String excerpt(String log) {
        if (log == null) {
            return "";
        }
        String value = log.trim();
        if (value.length() <= MAX_LOG_FIELD_CHARS) {
            return value;
        }
        int start = value.length() - MAX_LOG_FIELD_CHARS;
        int newline = value.indexOf('\n', start);
        if (newline >= 0) {
            start = newline + 1;
        }
        return "… earlier lines omitted …\n" + value.substring(start);
    }

    /** True when the full log is small enough to prefill without a huge URL. */
    static boolean canPrefillFullLog(String log) {
        return log != null && !log.isEmpty()
                && log.length() <= MAX_LOG_FIELD_CHARS;
    }

    /**
     * Extracts the last helper request from the log so the issue can show the
     * app and version that were being patched.
     */
    static String lastRequest(String log) {
        if (log == null) {
            return "";
        }
        Matcher matcher = LAST_REQUEST.matcher(log);
        String result = "";
        while (matcher.find()) {
            String app = matcher.group(2) == null ? "" : matcher.group(2).trim();
            String version = matcher.group(3) == null ? "" : matcher.group(3).trim();
            String pkg = matcher.group(1) == null ? "" : matcher.group(1).trim();
            result = (app.isEmpty() ? pkg : app)
                    + (version.isEmpty() ? "" : " " + version)
                    + (pkg.isEmpty() ? "" : " (" + pkg + ")");
        }
        return result;
    }

    /**
     * Removes tokens and signed URL fragments before anything is copied or
     * posted publicly. This is deliberately conservative; users should still
     * review the report before submitting it.
     */
    static String redact(String log) {
        return LogSanitizer.sanitize(log);
    }

    /**
     * Builds the issue URL with a Markdown body. If the log does not fit in the
     * URL, the log is left out so the caller can copy or attach it.
     *
     * @param logIsCode true when {@code logField} is actual log text, false
     *                  when it is an instruction to paste or attach the log
     */
    static String buildUrl(String helperVersion, String androidDevice,
                           String morpheVersion, String targetApp,
                           String logField, boolean logIsCode) {
        String title = encode("[Bug]: Download issue");
        String prefix = NEW_ISSUE + "?title=" + title + "&labels=bug";
        String body = buildBody(helperVersion, androidDevice, morpheVersion,
                targetApp, logField, logIsCode);
        String url = prefix + "&body=" + encode(body);
        if (url.length() <= MAX_URL_LENGTH) {
            return url;
        }
        // The log is the only large part. Drop it rather than producing a URL
        // that GitHub may reject; the caller has already copied or saved it.
        String fallback = buildBody(helperVersion, androidDevice, morpheVersion,
                targetApp, "", false);
        return prefix + "&body=" + encode(fallback);
    }

    private static String buildBody(String helperVersion, String androidDevice,
                                    String morpheVersion, String targetApp,
                                    String logField, boolean logIsCode) {
        StringBuilder body = new StringBuilder();
        body.append("### Helper version\n").append(value(helperVersion)).append("\n\n");
        body.append("### Android version and device\n")
                .append(value(androidDevice)).append("\n\n");
        body.append("### Morphe version\n").append(value(morpheVersion)).append("\n\n");
        body.append("### App and version being patched\n")
                .append(value(targetApp)).append("\n\n");
        body.append("### Steps to reproduce\n")
                .append("1. Open Morphe\n")
                .append("2. Patch the app\n")
                .append("3. Choose \"Use a helper app\"\n")
                .append("4. Try the download\n\n");
        body.append("### Expected behavior\n")
                .append("The helper should download the exact build and hand the ")
                .append("correct APK to Morphe.\n\n");
        body.append("### Actual behavior\n")
                .append("The download failed or the wrong file was returned.\n\n");
        if (logField != null && !logField.isEmpty()) {
            body.append("### Log\n\n");
            if (logIsCode) {
                String fence = logField.contains("```") ? "````" : "```";
                body.append("<details>\n<summary>Helper log</summary>\n\n")
                        .append(fence).append("text\n")
                        .append(logField).append('\n')
                        .append(fence).append("\n\n</details>\n");
            } else {
                body.append(logField).append('\n');
            }
        }
        return body.toString();
    }

    private static String value(String value) {
        return value == null || value.trim().isEmpty() ? "Not supplied" : value.trim();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception ignored) {
            return "";
        }
    }
}
