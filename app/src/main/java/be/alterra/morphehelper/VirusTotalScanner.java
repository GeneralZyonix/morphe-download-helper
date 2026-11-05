package be.alterra.morphehelper;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

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
        ERROR
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
        if (!AppSettings.virusTotalEnabled(context)) {
            return new Result(Status.DISABLED, "", 0, 0, "Scanning is off.");
        }
        String apiKey = AppSettings.virusTotalApiKey(context).trim();
        if (apiKey.isEmpty()) {
            return new Result(Status.NO_KEY, "", 0, 0,
                    "No VirusTotal API key configured.");
        }

        String hash;
        try {
            hash = sha256(file);
        } catch (Exception error) {
            HelperLog.error("Could not hash file for VirusTotal", error);
            return new Result(Status.ERROR, "", 0, 0,
                    "Could not calculate the file hash.");
        }

        Result lookup = lookup(hash, apiKey);
        if (lookup.status != Status.UNKNOWN || !AppSettings.virusTotalUploadUnknown(context)) {
            return lookup;
        }
        return uploadAndAnalyze(context, file, hash, apiKey);
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
                                           String apiKey) {
        if (file.length() > MAX_UPLOAD_LIMIT) {
            return new Result(Status.UNKNOWN, hash, 0, 0,
                    "The APK is too large for VirusTotal's 650 MB upload limit.");
        }
        HelperLog.info("VirusTotal hash unknown; uploading "
                + file.getName() + " for analysis");

        String uploadUrl = API + "files";
        if (file.length() > DIRECT_UPLOAD_LIMIT) {
            Result urlResult = uploadUrl(apiKey);
            if (urlResult != null) {
                return urlResult;
            }
            // uploadUrl() writes the URL through a small holder to keep the
            // result types simple. If it succeeded, it is stored below.
            uploadUrl = largeUploadUrl;
        }

        RequestBody fileBody = RequestBody.create(OCTET_STREAM, file);
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
        try (Response response = UPLOAD_HTTP.newCall(request).execute()) {
            String content = bodyString(response);
            int code = response.code();
            if (code == 401 || code == 403) {
                return denied(hash, content, code);
            }
            if (code == 429) {
                return quota(hash, content);
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
            return waitForAnalysis(hash, analysisId, apiKey);
        } catch (Exception error) {
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

    private static Result waitForAnalysis(String hash, String analysisId, String apiKey) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Thread.sleep(attempt == 0 ? 2000L : 3000L);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                break;
            }
            Request request = new Request.Builder()
                    .url(API + "analyses/" + analysisId)
                    .header("x-apikey", apiKey)
                    .header("Accept", "application/json")
                    .build();
            try (Response response = LOOKUP_HTTP.newCall(request).execute()) {
                String content = bodyString(response);
                int code = response.code();
                if (code == 401 || code == 403) {
                    return denied(hash, content, code);
                }
                if (code == 429) {
                    return quota(hash, content);
                }
                if (!response.isSuccessful()) {
                    continue;
                }
                JSONObject root = new JSONObject(content);
                JSONObject data = root.optJSONObject("data");
                JSONObject attributes = data == null
                        ? null : data.optJSONObject("attributes");
                if (attributes == null) {
                    continue;
                }
                String state = attributes.optString("status", "");
                if ("completed".equalsIgnoreCase(state)) {
                    JSONObject stats = attributes.optJSONObject("stats");
                    if (stats != null) {
                        return statsResult(hash, stats);
                    }
                }
            } catch (Exception error) {
                HelperLog.error("VirusTotal analysis poll failed", error);
            }
        }
        return new Result(Status.UPLOAD_PENDING, hash, 0, 0,
                "APK uploaded; VirusTotal analysis is still pending.");
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
            return statsResult(hash, stats);
        } catch (Exception error) {
            return new Result(Status.ERROR, hash, 0, 0,
                    "VirusTotal response could not be parsed.");
        }
    }

    private static Result statsResult(String hash, JSONObject stats) {
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
        HelperLog.info("VirusTotal " + hash + ": " + summary);
        return new Result(detections > 0 ? Status.DETECTED : Status.CLEAN,
                hash, malicious, suspicious, summary);
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
