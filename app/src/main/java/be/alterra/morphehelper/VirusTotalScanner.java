package be.alterra.morphehelper;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

/**
 * Optional VirusTotal SHA-256 lookup, with opt-in upload when a hash is unknown.
 * The APK is uploaded only when the user explicitly enables that setting.
 */
final class VirusTotalScanner {

    enum Status {
        DISABLED,
        NO_KEY,
        CLEAN,
        DETECTED,
        UNKNOWN,
        UPLOAD_PENDING,
        QUOTA_EXCEEDED,
        DENIED,
        CANCELLED,
        ERROR
    }

    interface ProgressCallback {
        /** Percent is -1 for an indeterminate step. */
        void onProgress(String message, int percent, Stage stage);
    }

    enum Stage {
        CHECKING,
        UPLOADING,
        WAITING
    }

    interface CancelCheck {
        boolean isCancelled();
    }

    static final class Result {
        final Status status;
        final String hash;
        final int malicious;
        final int suspicious;
        final String message;

        Result(Status status, String hash, int malicious, int suspicious,
               String message) {
            this.status = status;
            this.hash = hash;
            this.malicious = malicious;
            this.suspicious = suspicious;
            this.message = message;
        }

        boolean isDetection() {
            return status == Status.DETECTED;
        }
    }

    private static final String API = "https://www.virustotal.com/api/v3/";
    private static final String FILES_API = API + "files/";
    private static final String UPLOAD_URL_API = API + "files/upload_url";
    private static final long DIRECT_UPLOAD_LIMIT = 32L * 1024L * 1024L;
    private static final long MAX_UPLOAD_LIMIT = 650L * 1024L * 1024L;
    private static final long ANALYSIS_TIMEOUT_MS = 5L * 60L * 1000L;
    private static final long ANALYSIS_FIRST_POLL_MS = 5_000L;
    private static final long ANALYSIS_POLL_MS = 15_000L;
    private static final long ANALYSIS_FILE_REPORT_GRACE_MS = 60_000L;
    private static final MediaType OCTET_STREAM =
            MediaType.parse("application/octet-stream");

    private static final OkHttpClient LOOKUP_HTTP = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build();

    private static final OkHttpClient UPLOAD_HTTP = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(300, TimeUnit.SECONDS)
            .build();

    private VirusTotalScanner() {
    }

    static Result scan(Context context, File file) {
        return scan(context, file, null, null);
    }

    static Result scan(Context context, File file, ProgressCallback callback) {
        return scan(context, file, callback, null);
    }

    static Result scan(Context context, File file, ProgressCallback callback,
                       CancelCheck cancel) {
        if (!AppSettings.virusTotalEnabled(context)) {
            return new Result(Status.DISABLED, "", 0, 0, "Scanning is off.");
        }
        if (isCancelled(cancel)) {
            return cancelled("");
        }
        String apiKey = AppSettings.virusTotalApiKey(context).trim();
        if (apiKey.isEmpty()) {
            return new Result(Status.NO_KEY, "", 0, 0,
                    "No VirusTotal API key configured.");
        }

        progress(callback, context.getString(R.string.virustotal_checking), -1,
                Stage.CHECKING);
        String hash;
        try {
            hash = sha256(file);
        } catch (Exception error) {
            HelperLog.error("Could not hash file for VirusTotal", error);
            return new Result(Status.ERROR, "", 0, 0,
                    "Could not calculate the file hash.");
        }

        if (isCancelled(cancel)) {
            return cancelled(hash);
        }
        if (AppSettings.virusTotalAlwaysUpload(context)) {
            HelperLog.info("VirusTotal always-upload setting is enabled");
            return uploadAndAnalyze(context, file, hash, apiKey, callback, cancel);
        }

        Result lookup = lookup(hash, apiKey);
        if (isCancelled(cancel)) {
            return cancelled(hash);
        }
        if (lookup.status == Status.QUOTA_EXCEEDED
                && AppSettings.virusTotalUploadOnQuota(context)) {
            HelperLog.info("VirusTotal quota exceeded; using upload fallback");
            return uploadAndAnalyze(context, file, hash, apiKey, callback, cancel);
        }
        if (lookup.status != Status.UNKNOWN || !AppSettings.virusTotalUploadUnknown(context)) {
            return lookup;
        }
        return uploadAndAnalyze(context, file, hash, apiKey, callback, cancel);
    }

    private static Result lookup(String hash, String apiKey) {
        Request request = new Request.Builder()
                .url(FILES_API + hash)
                .header("x-apikey", apiKey)
                .header("Accept", "application/json")
                .build();
        try (Response response = LOOKUP_HTTP.newCall(request).execute()) {
            String content = bodyString(response);
            int code = response.code();
            if (code == 404) {
                HelperLog.info("VirusTotal has no report for " + hash);
                return new Result(Status.UNKNOWN, hash, 0, 0,
                        "This APK is not known to VirusTotal.");
            }
            if (code == 401 || code == 403) {
                return denied(hash, content, code);
            }
            if (code == 429) {
                return quota(hash, content);
            }
            if (!response.isSuccessful()) {
                HelperLog.error("VirusTotal HTTP " + code, null);
                return new Result(Status.ERROR, hash, 0, 0,
                        "VirusTotal returned HTTP " + code + ".");
            }
            return statsResult(hash, content);
        } catch (Exception error) {
            HelperLog.error("VirusTotal lookup failed", error);
            return new Result(Status.ERROR, hash, 0, 0,
                    "VirusTotal lookup failed: " + error.getMessage());
        }
    }

    private static Result uploadAndAnalyze(Context context, File file, String hash,
                                           String apiKey, ProgressCallback callback,
                                           CancelCheck cancel) {
        if (isCancelled(cancel)) {
            return cancelled(hash);
        }
        if (file.length() > MAX_UPLOAD_LIMIT) {
            return new Result(Status.UNKNOWN, hash, 0, 0,
                    "The APK is too large for VirusTotal's 650 MB upload limit.");
        }
        HelperLog.info("VirusTotal hash unknown; uploading "
                + file.getName() + " for analysis");

        String uploadUrl = API + "files";
        if (file.length() > DIRECT_UPLOAD_LIMIT) {
            progress(callback, context.getString(R.string.virustotal_preparing_upload), -1,
                Stage.UPLOADING);
            Result urlResult = uploadUrl(apiKey);
            if (urlResult != null) {
                return urlResult;
            }
            // uploadUrl() writes the URL through a small holder to keep the
            // result types simple. If it succeeded, it is stored below.
            uploadUrl = largeUploadUrl;
        }
        if (isCancelled(cancel)) {
            return cancelled(hash);
        }

        progress(callback, context.getString(R.string.virustotal_uploading, 0), 0,
                Stage.UPLOADING);
        RequestBody fileBody = new ProgressRequestBody(context, file,
                RequestBody.create(OCTET_STREAM, file), file.length(), callback, cancel);
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), fileBody)
                .build();
        Request request = new Request.Builder()
                .url(uploadUrl)
                .header("x-apikey", apiKey)
                .header("Accept", "application/json")
                .post(body)
                .build();
        long uploadStartedAt = System.currentTimeMillis();
        try (Response response = UPLOAD_HTTP.newCall(request).execute()) {
            String content = bodyString(response);
            int code = response.code();
            if (code == 401 || code == 403) {
                return denied(hash, content, code);
            }
            if (code == 429) {
                return quota(hash, content);
            }
            if (code == 409) {
                HelperLog.info("VirusTotal already has this file queued; waiting for its report");
                return waitForAnalysis(context, hash, "", apiKey, uploadStartedAt,
                        callback, cancel);
            }
            if (!response.isSuccessful()) {
                HelperLog.error("VirusTotal upload HTTP " + code, null);
                return new Result(Status.ERROR, hash, 0, 0,
                        "VirusTotal upload returned HTTP " + code + ".");
            }
            String analysisId = analysisId(content);
            if (analysisId.isEmpty()) {
                return new Result(Status.UPLOAD_PENDING, hash, 0, 0,
                        "APK uploaded, but VirusTotal returned no analysis ID.");
            }
            return waitForAnalysis(context, hash, analysisId, apiKey, uploadStartedAt,
                    callback, cancel);
        } catch (Exception error) {
            if (isCancelled(cancel)) {
                return cancelled(hash);
            }
            HelperLog.error("VirusTotal upload failed", error);
            return new Result(Status.ERROR, hash, 0, 0,
                    "VirusTotal upload failed: " + error.getMessage());
        }
    }

    private static String largeUploadUrl;

    /**
     * Returns null when a large-upload URL was obtained, or a failure Result.
     * The URL is placed in {@link #largeUploadUrl} for the caller.
     */
    private static Result uploadUrl(String apiKey) {
        Request request = new Request.Builder()
                .url(UPLOAD_URL_API)
                .header("x-apikey", apiKey)
                .header("Accept", "application/json")
                .build();
        try (Response response = LOOKUP_HTTP.newCall(request).execute()) {
            String content = bodyString(response);
            int code = response.code();
            if (code == 401 || code == 403) {
                return denied("", content, code);
            }
            if (code == 429) {
                return quota("", content);
            }
            if (!response.isSuccessful()) {
                return new Result(Status.ERROR, "", 0, 0,
                        "VirusTotal upload URL returned HTTP " + code + ".");
            }
            String url = new JSONObject(content).optString("data", "");
            if (!url.startsWith("https://")) {
                return new Result(Status.ERROR, "", 0, 0,
                        "VirusTotal did not return an upload URL.");
            }
            largeUploadUrl = url;
            return null;
        } catch (Exception error) {
            HelperLog.error("VirusTotal upload URL request failed", error);
            return new Result(Status.ERROR, "", 0, 0,
                    "VirusTotal upload URL request failed.");
        }
    }

    private static Result waitForAnalysis(Context context, String hash, String analysisId,
                                          String apiKey, long uploadStartedAt,
                                          ProgressCallback callback, CancelCheck cancel) {
        progress(callback, context.getString(R.string.virustotal_waiting_analysis), -1,
                Stage.WAITING);
        long startedAt = System.currentTimeMillis();
        long deadline = startedAt + ANALYSIS_TIMEOUT_MS;
        int attempt = 0;
        boolean checkFileReport = true;

        // VirusTotal's analysis endpoint can lag behind the file report. Check
        // both, alternating so the public API limit of 4 requests/minute is
        // respected. A file report older than this upload is ignored until the
        // grace period ends.
        while (System.currentTimeMillis() < deadline) {
            if (isCancelled(cancel)) {
                return cancelled(hash);
            }
            long waitMs = attempt == 0 ? ANALYSIS_FIRST_POLL_MS : ANALYSIS_POLL_MS;
            if (!waitWithProgress(context, callback, cancel, waitMs, startedAt)) {
                if (isCancelled(cancel)) {
                    return cancelled(hash);
                }
                break;
            }
            attempt++;

            long elapsed = System.currentTimeMillis() - startedAt;
            Result result;
            if (checkFileReport) {
                result = fileReport(hash, apiKey, uploadStartedAt,
                        elapsed >= ANALYSIS_FILE_REPORT_GRACE_MS);
            } else if (analysisId != null && !analysisId.isEmpty()) {
                result = analysisStatus(hash, analysisId, apiKey);
            } else {
                result = null;
            }
            checkFileReport = !checkFileReport;

            if (result != null) {
                return result;
            }
        }
        return new Result(Status.UPLOAD_PENDING, hash, 0, 0,
                "APK uploaded; VirusTotal analysis is still pending.");
    }

    /**
     * Sleeps in one-second slices and refreshes the elapsed-time counter each
     * second. Returns false when the scan was cancelled or the thread was
     * interrupted.
     */
    private static boolean waitWithProgress(Context context, ProgressCallback callback,
                                            CancelCheck cancel, long durationMs,
                                            long startedAt) {
        long end = System.currentTimeMillis() + durationMs;
        while (System.currentTimeMillis() < end) {
            if (isCancelled(cancel)) {
                return false;
            }
            long remaining = end - System.currentTimeMillis();
            try {
                Thread.sleep(Math.min(1000L, Math.max(1L, remaining)));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (isCancelled(cancel)) {
                return false;
            }
            long elapsed = (System.currentTimeMillis() - startedAt) / 1000L;
            progress(callback, context.getString(
                    R.string.virustotal_waiting_analysis_seconds, elapsed), -1,
                    Stage.WAITING);
        }
        return true;
    }

    /**
     * Reads an existing file report. Returns null while the report is not
     * available yet, and a result when it contains analysis statistics.
     */
    private static Result fileReport(String hash, String apiKey, long uploadStartedAt,
                                     boolean allowStale) {
        Request request = new Request.Builder()
                .url(FILES_API + hash)
                .header("x-apikey", apiKey)
                .header("Accept", "application/json")
                .build();
        try (Response response = LOOKUP_HTTP.newCall(request).execute()) {
            int code = response.code();
            String content = bodyString(response);
            if (code == 404) {
                return null;
            }
            if (code == 401 || code == 403) {
                return denied(hash, content, code);
            }
            if (code == 429) {
                return quota(hash, content);
            }
            if (!response.isSuccessful()) {
                return null;
            }
            JSONObject root = new JSONObject(content);
            JSONObject data = root.optJSONObject("data");
            JSONObject attributes = data == null
                    ? null : data.optJSONObject("attributes");
            JSONObject stats = attributes == null
                    ? null : attributes.optJSONObject("last_analysis_stats");
            if (stats == null) {
                return null;
            }
            long lastAnalysis = attributes.optLong("last_analysis_date", 0L) * 1000L;
            if (!allowStale && lastAnalysis > 0L
                    && lastAnalysis + 5_000L < uploadStartedAt) {
                HelperLog.info("VirusTotal file report is older than this upload");
                return null;
            }
            return statsResult(hash, attributes);
        } catch (Exception error) {
            HelperLog.error("VirusTotal file report poll failed", error);
            return null;
        }
    }

    /**
     * Reads the upload's analysis object. Returns null while it is queued or
     * still running, and a result once it completes.
     */
    private static Result analysisStatus(String hash, String analysisId, String apiKey) {
        Request request = new Request.Builder()
                .url(API + "analyses/" + analysisId)
                .header("x-apikey", apiKey)
                .header("Accept", "application/json")
                .build();
        try (Response response = LOOKUP_HTTP.newCall(request).execute()) {
            int code = response.code();
            String content = bodyString(response);
            if (code == 401 || code == 403) {
                return denied(hash, content, code);
            }
            if (code == 429) {
                return quota(hash, content);
            }
            if (!response.isSuccessful()) {
                return null;
            }
            JSONObject root = new JSONObject(content);
            JSONObject data = root.optJSONObject("data");
            JSONObject attributes = data == null
                    ? null : data.optJSONObject("attributes");
            if (attributes == null) {
                return null;
            }
            String state = attributes.optString("status", "");
            HelperLog.info("VirusTotal analysis status: " + state);
            if ("completed".equalsIgnoreCase(state)) {
                JSONObject stats = attributes.optJSONObject("stats");
                if (stats != null) {
                    return statsResult(hash, attributes);
                }
            }
            return null;
        } catch (Exception error) {
            HelperLog.error("VirusTotal analysis poll failed", error);
            return null;
        }
    }

    private static Result statsResult(String hash, String content) {
        try {
            JSONObject root = new JSONObject(content);
            JSONObject data = root.optJSONObject("data");
            JSONObject attributes = data == null ? null : data.optJSONObject("attributes");
            JSONObject stats = attributes == null
                    ? null : attributes.optJSONObject("last_analysis_stats");
            if (stats == null) {
                return new Result(Status.UNKNOWN, hash, 0, 0,
                        "VirusTotal returned no analysis yet.");
            }
            return statsResult(hash, attributes);
        } catch (Exception error) {
            return new Result(Status.ERROR, hash, 0, 0,
                    "VirusTotal response could not be parsed.");
        }
    }

    static Result statsResult(String hash, JSONObject attributes) {
        JSONObject stats = attributes == null
                ? null : attributes.optJSONObject("last_analysis_stats");
        if (stats == null && attributes != null) {
            stats = attributes.optJSONObject("stats");
        }
        if (stats == null) {
            return new Result(Status.UNKNOWN, hash, 0, 0,
                    "VirusTotal returned no analysis yet.");
        }
        int malicious = stats.optInt("malicious", 0);
        int suspicious = stats.optInt("suspicious", 0);
        int harmless = stats.optInt("harmless", 0);
        int undetected = stats.optInt("undetected", 0);
        int detections = malicious + suspicious;
        String summary = detections > 0
                ? detections + " detection(s): " + malicious + " malicious, "
                        + suspicious + " suspicious"
                : "No detections (" + harmless + " harmless, "
                        + undetected + " undetected)";
        if (detections > 0) {
            String engines = engineDetails(attributes);
            if (!engines.isEmpty()) {
                summary += "\n" + engines;
            }
        }
        HelperLog.info("VirusTotal " + hash + ": " + summary);
        return new Result(detections > 0 ? Status.DETECTED : Status.CLEAN,
                hash, malicious, suspicious, summary);
    }

    private static String engineDetails(JSONObject attributes) {
        JSONObject results = attributes == null
                ? null : attributes.optJSONObject("last_analysis_results");
        if (results == null && attributes != null) {
            results = attributes.optJSONObject("results");
        }
        if (results == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        int count = 0;
        Iterator<String> keys = results.keys();
        while (keys.hasNext() && count < 6) {
            String engine = keys.next();
            JSONObject entry = results.optJSONObject(engine);
            if (entry == null) {
                continue;
            }
            String category = entry.optString("category", "");
            if (!"malicious".equals(category) && !"suspicious".equals(category)) {
                continue;
            }
            if (count > 0) {
                text.append(", ");
            }
            text.append(engine);
            String result = entry.optString("result", "").trim();
            if (!result.isEmpty()) {
                text.append(": ").append(result);
            }
            count++;
        }
        return text.length() == 0 ? "" : "Flagged by: " + text;
    }

    private static Result denied(String hash, String content, int code) {
        String detail = apiError(content);
        HelperLog.error("VirusTotal denied the API key (HTTP " + code + ")", null);
        return new Result(Status.DENIED, hash, 0, 0,
                "VirusTotal denied the API key"
                        + (detail.isEmpty() ? "." : ": " + detail));
    }

    private static Result quota(String hash, String content) {
        String detail = apiError(content);
        HelperLog.error("VirusTotal quota or rate limit reached", null);
        return new Result(Status.QUOTA_EXCEEDED, hash, 0, 0,
                "VirusTotal quota exceeded"
                        + (detail.isEmpty() ? "." : ": " + detail));
    }

    private static String analysisId(String content) {
        try {
            JSONObject root = new JSONObject(content);
            JSONObject data = root.optJSONObject("data");
            return data == null ? "" : data.optString("id", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String apiError(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        try {
            JSONObject root = new JSONObject(content);
            JSONObject error = root.optJSONObject("error");
            if (error == null) {
                return "";
            }
            String code = error.optString("code", "");
            String message = error.optString("message", "");
            if (code.isEmpty()) {
                return message;
            }
            if (message.isEmpty()) {
                return code;
            }
            return code + ": " + message;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String bodyString(Response response) throws Exception {
        ResponseBody body = response.body();
        return body == null ? "" : body.string();
    }

    private static boolean isCancelled(CancelCheck cancel) {
        return cancel != null && cancel.isCancelled();
    }

    private static Result cancelled(String hash) {
        return new Result(Status.CANCELLED, hash, 0, 0,
                "VirusTotal scan skipped.");
    }

    private static void progress(ProgressCallback callback, String message,
                                 int percent, Stage stage) {
        if (callback != null) {
            callback.onProgress(message, percent, stage);
        }
    }

    private static final class ProgressRequestBody extends RequestBody {
        private final Context context;
        private final File file;
        private final RequestBody delegate;
        private final long length;
        private final ProgressCallback callback;
        private final CancelCheck cancel;

        ProgressRequestBody(Context context, File file, RequestBody delegate, long length,
                            ProgressCallback callback, CancelCheck cancel) {
            this.context = context;
            this.file = file;
            this.delegate = delegate;
            this.length = length;
            this.callback = callback;
            this.cancel = cancel;
        }

        @Override
        public MediaType contentType() {
            return delegate.contentType();
        }

        @Override
        public long contentLength() {
            return length;
        }

        @Override
        public void writeTo(BufferedSink sink) throws java.io.IOException {
            int lastPercent = -1;
            long uploaded = 0;
            try (Source source = Okio.source(file)) {
                Buffer buffer = new Buffer();
                long read;
                while ((read = source.read(buffer, 16 * 1024)) != -1) {
                    if (isCancelled(cancel)) {
                        throw new java.io.IOException("VirusTotal scan cancelled");
                    }
                    sink.write(buffer, read);
                    uploaded += read;
                    int percent = length <= 0 ? 0
                            : (int) Math.min(100, uploaded * 100 / length);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        progress(callback,
                                context.getString(R.string.virustotal_uploading, percent),
                                percent, Stage.UPLOADING);
                    }
                }
            }
            sink.flush();
            progress(callback, context.getString(R.string.virustotal_uploading, 100), 100,
                    Stage.UPLOADING);
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) {
            result.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return result.toString();
    }
}
