package be.alterra.morphehelper;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * On-demand patch names and version targets for one source/app pair.
 *
 * <p>The details file is larger than the main index, so it is fetched only when
 * the user asks to inspect a source and is cached with an ETag.</p>
 */
final class PatchDetailsCatalog {

    static final String DETAILS_URL =
            "https://raw.githubusercontent.com/GeneralZyonix/morphe-download-helper/"
                    + "main/docs/data/patch-details.json";
    private static final String CACHE_NAME = "patch-details.json";
    private static final String ETAG_NAME = "patch-details.etag";
    private static final String USER_AGENT = "Morphe-Download-Helper/3.2";
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    interface Callback {
        void onDetails(List<Detail> details);

        void onError(String message);
    }

    static final class Detail {
        final String name;
        final List<String> versions;

        Detail(String name, List<String> versions) {
            this.name = name;
            this.versions = versions;
        }
    }

    private PatchDetailsCatalog() {
    }

    static void load(Context context, String repo, String packageName,
                     Callback callback) {
        Context appContext = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            File cache = new File(appContext.getCacheDir(), CACHE_NAME);
            String cached = null;
            try {
                if (cache.isFile()) {
                    cached = readText(cache);
                }
            } catch (IOException ignored) {
                // Replace corrupt cache from the network if possible.
            }

            Request.Builder request = new Request.Builder()
                    .url(DETAILS_URL)
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-cache")
                    .header("User-Agent", USER_AGENT);
            String etag = readTextQuietly(
                    new File(appContext.getCacheDir(), ETAG_NAME));
            if (!TextUtils.isEmpty(etag)) {
                request.header("If-None-Match", etag);
            }

            String json = null;
            try (Response response = HTTP.newCall(request.build()).execute()) {
                if (response.code() == 304 && cached != null) {
                    json = cached;
                } else {
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP " + response.code());
                    }
                    ResponseBody body = response.body();
                    if (body == null) {
                        throw new IOException("Empty response");
                    }
                    json = body.string();
                    writeText(cache, json);
                    String newEtag = response.header("ETag");
                    if (!TextUtils.isEmpty(newEtag)) {
                        writeText(new File(appContext.getCacheDir(), ETAG_NAME),
                                newEtag);
                    }
                }
            } catch (Exception error) {
                HelperLog.error("Patch details failed", error);
                if (cached != null) {
                    json = cached;
                }
            }

            if (json == null) {
                main.post(() -> callback.onError(
                        "Could not load patch details."));
                return;
            }
            try {
                List<Detail> details = extract(json, repo, packageName);
                main.post(() -> callback.onDetails(details));
            } catch (Exception error) {
                HelperLog.error("Could not parse patch details", error);
                main.post(() -> callback.onError(
                        "Could not parse patch details."));
            }
        }, "patch-details").start();
    }

    private static List<Detail> extract(String json, String repo,
                                        String packageName) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject repos = root.optJSONObject("repos");
        if (repos == null) {
            return Collections.emptyList();
        }
        JSONObject repoDetails = repos.optJSONObject(repo);
        if (repoDetails == null) {
            return Collections.emptyList();
        }
        JSONArray entries = repoDetails.optJSONArray(packageName);
        if (entries == null) {
            return Collections.emptyList();
        }
        List<Detail> details = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONArray values = entries.optJSONArray(i);
            if (values == null || values.length() < 2) {
                continue;
            }
            String name = values.optString(0, "");
            JSONArray versionArray = values.optJSONArray(1);
            List<String> versions = new ArrayList<>();
            if (versionArray != null) {
                for (int j = 0; j < versionArray.length(); j++) {
                    String version = versionArray.optString(j, "");
                    if (!version.isEmpty()) {
                        versions.add(version);
                    }
                }
            }
            if (!name.isEmpty()) {
                details.add(new Detail(name, versions));
            }
        }
        return details;
    }

    private static String readTextQuietly(File file) {
        try {
            return file.isFile() ? readText(file) : "";
        } catch (IOException ignored) {
            return "";
        }
    }

    private static String readText(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void writeText(File file, String value) throws IOException {
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
        if (!temporary.renameTo(file)) {
            temporary.delete();
            throw new IOException("Could not replace " + file.getName());
        }
    }
}
