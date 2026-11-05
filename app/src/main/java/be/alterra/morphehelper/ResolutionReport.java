package be.alterra.morphehelper;

import android.content.Context;

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

    private final Context context;
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

    ResolutionReport(Context context, String appName, String pkg, String version,
                     String fileType, String abi) {
        this.context = context.getApplicationContext();
        this.appName = appName;
        this.pkg = pkg;
        this.version = version;
        this.fileType = fileType;
        this.abi = abi;
    }

    synchronized void record(String key, boolean ok, String detail) {
        attempts.add(new Attempt(key, AppSettings.sourceLabel(context, key), ok,
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
        text.append(context.getString(R.string.report_result_title)).append('\n');
        text.append(context.getString(
                R.string.report_request, value(pkg), value(version))).append('\n');
        text.append(context.getString(R.string.report_result, context.getString(
                success ? R.string.report_success : R.string.report_failed))).append('\n');
        text.append(context.getString(
                R.string.report_duration, durationText())).append('\n');
        text.append(context.getString(R.string.report_sources_tried)).append('\n');
        if (attempts.isEmpty()) {
            text.append(context.getString(R.string.report_none)).append('\n');
        } else {
            for (int i = 0; i < attempts.size(); i++) {
                Attempt attempt = attempts.get(i);
                String status = context.getString(
                        attempt.success ? R.string.common_ok : R.string.common_failed);
                text.append(context.getString(
                        R.string.report_source_line, i + 1, attempt.label, status));
                if (attempt.detail != null && !attempt.detail.isEmpty()) {
                    text.append(" - ").append(attempt.detail);
                }
                text.append('\n');
            }
        }
        if (success && file != null) {
            text.append(context.getString(
                    R.string.report_file, file.getName())).append('\n');
            text.append(context.getString(
                    R.string.report_size, sizeText(file.length()))).append('\n');
        } else {
            text.append(context.getString(
                    R.string.report_reason, value(failureReason))).append('\n');
            text.append(context.getString(R.string.report_what_you_can_do)).append('\n');
            text.append(context.getString(R.string.report_action_retry)).append('\n');
            text.append(context.getString(
                    R.string.report_action_check_version)).append('\n');
            text.append(context.getString(
                    R.string.report_action_change_order)).append('\n');
        }
        text.append(context.getString(R.string.report_end)).append('\n');
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

    private String friendly(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return context.getString(R.string.error_unknown);
        }
        String value = raw.replaceAll("https?://\\S+", "link").trim();
        String lower = value.toLowerCase(Locale.US);
        String prefix = "";
        if (lower.startsWith("direct:")) {
            prefix = context.getString(R.string.error_prefix_direct);
            value = value.substring(7).trim();
            lower = lower.substring(7).trim();
        } else if (lower.startsWith("api:")) {
            prefix = context.getString(R.string.error_prefix_api);
            value = value.substring(4).trim();
            lower = lower.substring(4).trim();
        }
        if (lower.contains("http 404")) {
            return prefix + context.getString(R.string.error_http_404);
        }
        if (lower.contains("http 403")) {
            return prefix + context.getString(R.string.error_http_403);
        }
        if (lower.contains("http 429")) {
            return prefix + context.getString(R.string.error_http_429);
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return prefix + context.getString(R.string.error_timeout);
        }
        if (lower.contains("html")) {
            return prefix + context.getString(R.string.error_html);
        }
        if (lower.contains("requested version") && lower.contains("not found")) {
            return prefix + context.getString(R.string.error_version_not_found);
        }
        if (lower.contains("no matching")) {
            return prefix + context.getString(R.string.error_no_matching_version);
        }
        if (lower.contains("search returned no app")) {
            return prefix + context.getString(R.string.error_no_matching_app);
        }
        if (lower.contains("no enabled download source")) {
            return prefix + context.getString(R.string.error_no_source);
        }
        if (lower.contains("cancelled")) {
            return prefix + context.getString(R.string.error_cancelled);
        }
        if (value.length() > 140) {
            return prefix + value.substring(0, 137) + "...";
        }
        return prefix + value;
    }

    private String value(String value) {
        return value == null || value.trim().isEmpty()
                ? context.getString(R.string.report_unknown) : value;
    }
}
