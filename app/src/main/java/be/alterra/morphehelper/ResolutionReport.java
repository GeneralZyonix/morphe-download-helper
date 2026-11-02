package be.alterra.morphehelper;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ResolutionReport {

    static final class Attempt {
        final String key;
        final String label;
        final boolean success;
        final String detail;
        final long at;

        Attempt(String key, String label, boolean success, String detail, long at) {
            this.key = key;
            this.label = label;
            this.success = success;
            this.detail = detail;
            this.at = at;
        }
    }

    private final String appName;
    private final String pkg;
    private final String version;
    private final String fileType;
    private final String abi;
    private final long startedAt = System.currentTimeMillis();
    private final List<Attempt> attempts = new ArrayList<>();
    private final Set<String> failedKeys = new LinkedHashSet<>();

    private boolean finished;
    private boolean success;
    private String failureReason;
    private File file;

    ResolutionReport(String appName, String pkg, String version, String fileType, String abi) {
        this.appName = appName;
        this.pkg = pkg;
        this.version = version;
        this.fileType = fileType;
        this.abi = abi;
    }

    synchronized void record(String key, boolean ok, String detail) {
        attempts.add(new Attempt(key, AppSettings.sourceLabel(key), ok,
                friendly(detail), System.currentTimeMillis()));
        if (ok) {
            failedKeys.remove(key);
        } else {
            failedKeys.add(key);
        }
    }

    synchronized void finishSuccess(File file) {
        this.file = file;
        this.success = true;
        this.finished = true;
    }

    synchronized void finishFailure(String reason) {
        this.failureReason = friendly(reason);
        this.success = false;
        this.finished = true;
    }

    synchronized List<Attempt> attempts() {
        return new ArrayList<>(attempts);
    }

    synchronized Set<String> failedKeys() {
        return new LinkedHashSet<>(failedKeys);
    }

    synchronized boolean isFinished() {
        return finished;
    }

    synchronized boolean isSuccess() {
        return success;
    }

    synchronized String failureReason() {
        return failureReason;
    }

    String headerBlock() {
        StringBuilder text = new StringBuilder();
        text.append("========== DOWNLOAD REQUEST ==========\n");
        text.append("App: ").append(value(appName)).append('\n');
        text.append("Package: ").append(value(pkg)).append('\n');
        text.append("Requested: ").append(value(version)).append('\n');
        text.append("Format: ").append(value(fileType)).append('\n');
        text.append("ABI: ").append(value(abi)).append('\n');
        return text.toString();
    }

    synchronized String resultBlock() {
        StringBuilder text = new StringBuilder();
        text.append("========== DOWNLOAD RESULT ==========\n");
        text.append("Request: ").append(value(pkg)).append(" ").append(value(version)).append('\n');
        text.append("Result: ").append(success ? "SUCCESS" : "FAILED").append('\n');
        text.append("Duration: ").append(durationText()).append('\n');
        text.append("Sources tried:\n");
        if (attempts.isEmpty()) {
            text.append("  None\n");
        } else {
            for (int i = 0; i < attempts.size(); i++) {
                Attempt attempt = attempts.get(i);
                text.append("  ").append(i + 1).append(". ").append(attempt.label)
                        .append(" - ").append(attempt.success ? "OK" : "FAILED");
                if (attempt.detail != null && !attempt.detail.isEmpty()) {
                    text.append(" - ").append(attempt.detail);
                }
                text.append('\n');
            }
        }
        if (success && file != null) {
            text.append("File: ").append(file.getName()).append('\n');
            text.append("Size: ").append(sizeText(file.length())).append('\n');
        } else {
            text.append("Reason: ").append(value(failureReason)).append('\n');
            text.append("What you can do:\n");
            text.append("  - Tap Retry failed sources.\n");
            text.append("  - Check that this version is available.\n");
            text.append("  - Change the source order in Settings.\n");
        }
        text.append("========== END REQUEST ==========\n");
        return text.toString();
    }

    private String durationText() {
        long millis = Math.max(0, System.currentTimeMillis() - startedAt);
        if (millis < 1000) {
            return millis + "ms";
        }
        return String.format(Locale.US, "%.1fs", millis / 1000.0);
    }

    private String sizeText(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    static String friendly(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "Unknown error";
        }
        String value = raw.replaceAll("https?://\\S+", "link").trim();
        String lower = value.toLowerCase(Locale.US);
        String prefix = "";
        if (lower.startsWith("direct:")) {
            prefix = "Direct: ";
            value = value.substring(7).trim();
            lower = lower.substring(7).trim();
        } else if (lower.startsWith("api:")) {
            prefix = "API: ";
            value = value.substring(4).trim();
            lower = lower.substring(4).trim();
        }
        if (lower.contains("http 404")) return prefix + "Not found (HTTP 404)";
        if (lower.contains("http 403")) return prefix + "Access blocked (HTTP 403)";
        if (lower.contains("http 429")) return prefix + "Rate limited (HTTP 429)";
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return prefix + "Connection timed out";
        }
        if (lower.contains("html")) return prefix + "Source returned a web page instead of a file";
        if (lower.contains("requested version") && lower.contains("not found")) {
            return prefix + "Requested version not found";
        }
        if (lower.contains("no matching")) return prefix + "No matching version found";
        if (lower.contains("search returned no app")) return prefix + "No matching app found";
        if (lower.contains("no enabled download source")) return prefix + "No source found a matching APK";
        if (lower.contains("cancelled")) return prefix + "Cancelled";
        if (value.length() > 140) {
            return prefix + value.substring(0, 137) + "...";
        }
        return prefix + value;
    }

    private static String value(String value) {
        return value == null || value.trim().isEmpty() ? "unknown" : value;
    }
}

