package be.alterra.morphehelper;

import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Reverse-engineered Uptodown mobile API client.
 *
 * <p>Uptodown's download page uses a Cloudflare Turnstile challenge that needs
 * a real user tap. The Android app talks to the same catalogue through a
 * versioned API that returns exact file IDs and a signed CDN URL, so this class
 * is used as the fast path. If it fails, the caller keeps the existing WebView
 * fallback.</p>
 */
final class UptodownApi {

    private static final String API_BASE = "https://www.uptodown.app/eapi";
    private static final String CLIENT_UA =
            "Dalvik/2.1.0 (Linux; U; Android 16; Pixel 8 Pro Build/BP4A.260205.001)";
    private static final String CLIENT_VERSION = "739";
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";
    private static final String HMAC_KEY =
            "MDGMXUMdvHJBG/vjdFgmqX6LUdy7ecfwvYNd0gyfOCs=";
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private static String token;
    private static long tokenExpiresAt;

    private UptodownApi() {
    }

    static final class App {
        final String appId;
        final String slug;
        final String name;
        final String icon;
        final String packageName;
        final String lastVersion;

        App(String appId, String slug, String name, String icon,
            String packageName, String lastVersion) {
            this.appId = appId;
            this.slug = slug;
            this.name = name;
            this.icon = icon;
            this.packageName = packageName;
            this.lastVersion = lastVersion;
        }
    }

    static final class Release {
        final String version;
        final long fileId;
        final String kindFile;
        final String minSdk;

        Release(String version, long fileId, String kindFile, String minSdk) {
            this.version = version;
            this.fileId = fileId;
            this.kindFile = kindFile;
            this.minSdk = minSdk;
        }
    }

    static final class Download {
        final String url;
        final String sha256;

        Download(String url, String sha256) {
            this.url = url;
            this.sha256 = sha256;
        }
    }

    static App resolve(String pkg) throws IOException {
        if (TextUtils.isEmpty(pkg)) {
            throw new IOException("Missing package name");
        }
        JSONObject lookup = json(apiGet(
                "/apps/byPackagename/" + URLEncoder.encode(pkg, "UTF-8")));
        JSONObject data = lookup.optJSONObject("data");
        String appId = data == null ? "" : data.optString("appID", "");
        if (TextUtils.isEmpty(appId)) {
            throw new IOException("Uptodown has no app for " + pkg);
        }
        JSONObject detail = json(apiGet("/v3/apps/" + appId + "/device/0?countryIsoCode=US"));
        JSONObject app = detail.optJSONObject("data");
        if (app == null) {
            throw new IOException("Uptodown detail request returned no app");
        }
        String slug = app.optString("urlShare", "");
        if (TextUtils.isEmpty(slug)) {
            throw new IOException("Uptodown returned no app page");
        }
        return new App(appId, slug,
                app.optString("name", ""),
                app.optString("icon", ""),
                app.optString("packagename", pkg),
                app.optString("lastVersion", ""));
    }

    static Release findRelease(App app, String version, String fileType)
            throws IOException {
        if (app == null || TextUtils.isEmpty(app.slug)) {
            throw new IOException("Uptodown app page missing");
        }
        String base = app.slug.endsWith("/")
                ? app.slug.substring(0, app.slug.length() - 1) : app.slug;
        String requested = version == null ? "" : version.trim();
        String wantedKind = normalizeKind(fileType);
        Release fallback = null;
        for (int page = 1; page <= 50; page++) {
            String url = base + "/apps/" + app.appId + "/versions/" + page;
            String body;
            try {
                body = browserGet(url);
            } catch (IOException error) {
                if (page == 1) {
                    throw error;
                }
                break;
            }
            JSONObject root = json(body);
            JSONArray items = root.optJSONArray("data");
            if (items == null || items.length() == 0) {
                break;
            }
            Release picked = pickRelease(items, requested, wantedKind);
            if (picked != null) {
                if (!TextUtils.isEmpty(requested)
                        && requested.equalsIgnoreCase(picked.version)) {
                    return picked;
                }
                if (TextUtils.isEmpty(requested) && fallback == null) {
                    fallback = picked;
                }
            }
        }
        if (fallback != null) {
            return fallback;
        }
        throw new IOException(TextUtils.isEmpty(requested)
                ? "No Uptodown release found"
                : "Requested version " + requested + " not found on Uptodown");
    }

    /** Package-private for unit tests. */
    static Release pickRelease(JSONArray items, String requested, String wantedKind) {
        boolean hasRequested = requested != null && !requested.isEmpty();
        boolean hasKind = wantedKind != null && !wantedKind.isEmpty();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String version = item.optString("version", "").trim();
            long fileId = item.optLong("fileID", 0);
            if (fileId <= 0) {
                continue;
            }
            String kind = normalizeKind(item.optString("kindFile", "apk"));
            if (hasKind && !wantedKind.equals(kind)) {
                continue;
            }
            Release release = new Release(version, fileId, kind,
                    item.optString("minSDK", ""));
            if (hasRequested && !requested.equalsIgnoreCase(version)) {
                continue;
            }
            return release;
        }
        return null;
    }

    static Download resolveDownload(App app, long fileId) throws IOException {
        if (app == null || TextUtils.isEmpty(app.appId) || fileId <= 0) {
            throw new IOException("Invalid Uptodown file");
        }
        JSONObject root = json(apiGet("/apps/" + app.appId + "/file/" + fileId + "/downloadUrl?update=0"));
        JSONObject data = root.optJSONObject("data");
        String url = data == null ? "" : data.optString("downloadURL", "");
        if (TextUtils.isEmpty(url)) {
            throw new IOException("Uptodown returned no download URL");
        }
        return new Download(url, data.optString("sha256", ""));
    }

    static boolean verifySha256(File file, String expected) {
        if (file == null || !file.isFile()
                || TextUtils.isEmpty(expected)) {
            return false;
        }
        try (InputStream input = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            String actual = hex(digest.digest());
            return expected.trim().equalsIgnoreCase(actual);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String normalizeKind(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.trim().toLowerCase(Locale.US);
        if (lower.equals("xapk") || lower.equals("apks") || lower.equals("apkm")) {
            return "xapk";
        }
        return lower.isEmpty() ? "" : "apk";
    }

    private static JSONObject json(String text) throws IOException {
        try {
            return new JSONObject(text);
        } catch (Exception error) {
            throw new IOException("Invalid Uptodown JSON", error);
        }
    }

    private static String apiGet(String path) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            String current = authorize(attempt > 0);
            Request request = new Request.Builder()
                    .url(path.startsWith("http") ? path : API_BASE + path)
                    .header("User-Agent", CLIENT_UA)
                    .header("Identificador", "Uptodown_Android")
                    .header("Identificador-Version", CLIENT_VERSION)
                    .header("Authorization", "Bearer " + current)
                    .header("Accept", "application/json")
                    .build();
            try (Response response = HTTP.newCall(request).execute()) {
                if (response.code() == 401 && attempt == 0) {
                    last = new IOException("Uptodown API HTTP 401");
                    continue;
                }
                ResponseBody body = response.body();
                String text = body == null ? "" : body.string();
                if (!response.isSuccessful()) {
                    throw new IOException("Uptodown API HTTP " + response.code());
                }
                return text;
            } catch (IOException error) {
                last = error;
                if (attempt > 0) {
                    throw error;
                }
            }
        }
        throw last == null ? new IOException("Uptodown API request failed") : last;
    }

    private static String browserGet(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Accept", "application/json")
                .build();
        try (Response response = HTTP.newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            if (!response.isSuccessful()) {
                throw new IOException("Uptodown versions HTTP " + response.code());
            }
            return text;
        }
    }

    private static synchronized String authorize(boolean force) throws IOException {
        long now = System.currentTimeMillis();
        if (!force && token != null && now < tokenExpiresAt - 60_000L) {
            return token;
        }
        String timestamp = String.valueOf(now / 1000L);
        String identifier = randomHex(8);
        String signature;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HMAC_KEY.getBytes("UTF-8"), "HmacSHA256"));
            signature = hex(mac.doFinal(timestamp.getBytes("UTF-8")));
        } catch (Exception error) {
            throw new IOException("Could not sign Uptodown request", error);
        }
        FormBody form = new FormBody.Builder()
                .add("identifier", identifier)
                .add("id_plataforma", "13")
                .add("lang", "en")
                .add("unixtime", timestamp)
                .add("hmac", signature)
                .build();
        Request request = new Request.Builder()
                .url(API_BASE + "/auth/token?identifier=" + identifier)
                .header("User-Agent", CLIENT_UA)
                .header("Identificador", "Uptodown_Android")
                .header("Identificador-Version", CLIENT_VERSION)
                .post(form)
                .build();
        try (Response response = HTTP.newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            if (!response.isSuccessful()) {
                throw new IOException("Uptodown auth HTTP " + response.code());
            }
            String value = json(text).optString("token", "");
            if (TextUtils.isEmpty(value)) {
                throw new IOException("Uptodown auth returned no token");
            }
            token = value;
            tokenExpiresAt = jwtExpiry(value, now + 30 * 60_000L);
            return token;
        }
    }

    private static long jwtExpiry(String jwt, long fallback) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return fallback;
            }
            byte[] payload = Base64.decode(parts[1],
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            long exp = new JSONObject(new String(payload, "UTF-8"))
                    .optLong("exp", 0);
            return exp > 0 ? exp * 1000L : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String randomHex(int bytes) {
        byte[] data = new byte[bytes];
        new SecureRandom().nextBytes(data);
        return hex(data);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(String.format(Locale.US, "%02x", value));
        }
        return out.toString();
    }
}
