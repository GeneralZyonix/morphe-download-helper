package be.alterra.morphehelper;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Optional VirusTotal lookup by SHA-256. The APK is never uploaded.
 */
final class VirusTotalScanner {

    enum Status {
        DISABLED,
        NO_KEY,
        CLEAN,
        DETECTED,
        UNKNOWN,
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

    private static final String API = "https://www.virustotal.com/api/v3/files/";
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
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

        Request request = new Request.Builder()
                .url(API + hash)
                .header("x-apikey", apiKey)
                .header("Accept", "application/json")
                .build();
        try (Response response = HTTP.newCall(request).execute()) {
            ResponseBody body = response.body();
            String content = body == null ? "" : body.string();
            int code = response.code();
            if (code == 404) {
                HelperLog.info("VirusTotal has no report for " + hash);
                return new Result(Status.UNKNOWN, hash, 0, 0,
                        "This APK is not known to VirusTotal.");
            }
            if (code == 401 || code == 403) {
                String detail = apiError(content);
                HelperLog.error("VirusTotal rejected the API key (HTTP " + code + ")", null);
                return new Result(Status.DENIED, hash, 0, 0,
                        "VirusTotal denied the API key"
                                + (detail.isEmpty() ? "." : ": " + detail));
            }
            if (code == 429) {
                String detail = apiError(content);
                HelperLog.error("VirusTotal quota or rate limit reached", null);
                return new Result(Status.QUOTA_EXCEEDED, hash, 0, 0,
                        "VirusTotal quota exceeded"
                                + (detail.isEmpty() ? "." : ": " + detail));
            }
            if (!response.isSuccessful()) {
                HelperLog.error("VirusTotal HTTP " + code, null);
                return new Result(Status.ERROR, hash, 0, 0,
                        "VirusTotal returned HTTP " + code + ".");
            }

            JSONObject root = new JSONObject(content);
            JSONObject data = root.optJSONObject("data");
            JSONObject attributes = data == null ? null : data.optJSONObject("attributes");
            JSONObject stats = attributes == null
                    ? null : attributes.optJSONObject("last_analysis_stats");
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
            HelperLog.info("VirusTotal " + hash + ": " + summary);
            return new Result(detections > 0 ? Status.DETECTED : Status.CLEAN,
                    hash, malicious, suspicious, summary);
        } catch (Exception error) {
            HelperLog.error("VirusTotal lookup failed", error);
            return new Result(Status.ERROR, hash, 0, 0,
                    "VirusTotal lookup failed: " + error.getMessage());
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
