package be.alterra.morphehelper;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class UpdateChecker {

    private static final String LATEST_RELEASE =
            "https://api.github.com/repos/GeneralZyonix/morphe-download-helper/releases/latest";
    private static final String RELEASES =
            "https://api.github.com/repos/GeneralZyonix/morphe-download-helper/releases?per_page=30";
    static final String USER_AGENT = "Morphe-Download-Helper/1.6.3";

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build();

    private UpdateChecker() {
    }

    static UpdateInfo check() throws IOException {
        Request request = new Request.Builder()
                .url(LATEST_RELEASE)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build();

        String body;
        try (Response response = HTTP.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            body = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                throw new IOException("GitHub update check HTTP " + response.code());
            }
        }

        try {
            return parseRelease(new JSONObject(body));
        } catch (JSONException error) {
            throw new IOException("GitHub update response parse failed", error);
        }
    }

    static List<UpdateInfo> releases() throws IOException {
        Request request = new Request.Builder()
                .url(RELEASES)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build();
        String body;
        try (Response response = HTTP.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            body = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                throw new IOException("GitHub releases HTTP " + response.code());
            }
        }

        List<UpdateInfo> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(body);
            for (int i = 0; i < array.length(); i++) {
                JSONObject release = array.optJSONObject(i);
                if (release == null || release.optBoolean("draft", false)
                        || release.optBoolean("prerelease", false)) {
                    continue;
                }
                result.add(parseRelease(release));
            }
            return result;
        } catch (JSONException error) {
            throw new IOException("GitHub releases response parse failed", error);
        }
    }

    private static UpdateInfo parseRelease(JSONObject release) {
        String tag = release.optString("tag_name", "");
        String version = tag.startsWith("v") || tag.startsWith("V")
                ? tag.substring(1) : tag;
        String releaseUrl = release.optString("html_url", "");
        String notes = release.optString("body", "");
        JSONArray assets = release.optJSONArray("assets");
        String assetUrl = "";
        String assetName = "";
        long assetSize = 0;
        if (assets != null) {
            String fallbackUrl = "";
            String fallbackName = "";
            long fallbackSize = 0;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) {
                    continue;
                }
                String name = asset.optString("name", "");
                String url = asset.optString("browser_download_url", "");
                if (!name.toLowerCase().endsWith(".apk") || !url.startsWith("https://")) {
                    continue;
                }
                if (fallbackUrl.isEmpty()) {
                    fallbackUrl = url;
                    fallbackName = name;
                    fallbackSize = asset.optLong("size", 0);
                }
                if (name.toLowerCase().contains("universal")) {
                    assetUrl = url;
                    assetName = name;
                    assetSize = asset.optLong("size", 0);
                    break;
                }
            }
            if (assetUrl.isEmpty()) {
                assetUrl = fallbackUrl;
                assetName = fallbackName;
                assetSize = fallbackSize;
            }
        }
        return new UpdateInfo(version, releaseUrl, assetUrl, assetName, assetSize, notes);
    }

    static String installedVersion(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "";
        }
    }
}









