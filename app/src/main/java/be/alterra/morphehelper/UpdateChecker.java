package be.alterra.morphehelper;

import android.content.Context;
import android.os.Build;
import android.text.Html;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class UpdateChecker {

    private static final String REPO = "GeneralZyonix/morphe-download-helper";
    private static final String LATEST_RELEASE =
            "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String RELEASES =
            "https://api.github.com/repos/" + REPO + "/releases?per_page=30";
    /**
     * Served by github.com instead of api.github.com. Manual GitHub downloads
     * can work while the API is unavailable, so the feed is a useful fallback.
     */
    private static final String FEED =
            "https://github.com/" + REPO + "/releases.atom";
    private static final String ASSET_PREFIX = "MorpheDownloadHelper-v";
    private static final String ASSET_SUFFIX = "-universal.apk";
    static final String USER_AGENT = "Morphe-Download-Helper/1.6.10";

    // GitHub's HTTP/2 edge occasionally resets streams with REFUSED_STREAM on
    // mobile networks. HTTP/1.1 avoids that failure mode entirely and GitHub
    // supports it for both the API and release assets.
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .protocols(Collections.singletonList(Protocol.HTTP_1_1))
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build();

    private UpdateChecker() {
    }

    /**
     * Returns the newest release for the installed channel. Stable installs
     * only see stable releases; an installed pre-release may also be compared
     * with newer pre-releases, so testers are not told an older stable build
     * is the latest version.
     */
    static UpdateInfo check(String currentVersion) throws IOException {
        UpdateInfo stable = check();
        if (!UpdateInfo.isPrerelease(currentVersion)) {
            return stable;
        }
        try {
            UpdateInfo best = stable;
            for (UpdateInfo candidate : releases(true)) {
                if (candidate.isNewerThan(best.version)) {
                    best = candidate;
                }
            }
            return best;
        } catch (IOException error) {
            HelperLog.error("Could not check the pre-release channel; "
                    + "using the stable release", error);
            return stable;
        }
    }

    static UpdateInfo check() throws IOException {
        IOException apiError = null;
        try {
            String body = fetch(LATEST_RELEASE, "update check",
                    "application/vnd.github+json", 2);
            return parseRelease(new JSONObject(body));
        } catch (IOException | JSONException error) {
            apiError = asIOException(error);
            HelperLog.error("GitHub API update check failed; trying release feed",
                    apiError);
        }

        try {
            UpdateInfo best = null;
            for (UpdateInfo candidate : feedReleases(false)) {
                if (best == null || candidate.isNewerThan(best.version)) {
                    best = candidate;
                }
            }
            if (best != null) {
                return best;
            }
            throw new IOException("Release feed contains no stable release");
        } catch (IOException feedError) {
            if (apiError != null) {
                feedError.addSuppressed(apiError);
            }
            throw new IOException("GitHub update services are not responding; "
                    + "try again later", feedError);
        }
    }

    static List<UpdateInfo> releases() throws IOException {
        return releases(false);
    }

    static List<UpdateInfo> releases(boolean includePrereleases) throws IOException {
        IOException apiError = null;
        try {
            String body = fetch(RELEASES, "releases",
                    "application/vnd.github+json", 2);
            return parseReleases(body, includePrereleases);
        } catch (IOException | JSONException error) {
            apiError = asIOException(error);
            HelperLog.error("GitHub API release list failed; trying release feed",
                    apiError);
        }

        try {
            return feedReleases(includePrereleases);
        } catch (IOException feedError) {
            if (apiError != null) {
                feedError.addSuppressed(apiError);
            }
            throw new IOException("GitHub release services are not responding; "
                    + "try again later", feedError);
        }
    }

    private static List<UpdateInfo> parseReleases(String body,
                                                  boolean includePrereleases)
            throws JSONException {
        List<UpdateInfo> result = new ArrayList<>();
        JSONArray array = new JSONArray(body);
        for (int i = 0; i < array.length(); i++) {
            JSONObject release = array.optJSONObject(i);
            if (release == null || release.optBoolean("draft", false)
                    || (!includePrereleases
                    && release.optBoolean("prerelease", false))) {
                continue;
            }
            result.add(parseRelease(release));
        }
        return result;
    }

    private static List<UpdateInfo> feedReleases(boolean includePrereleases)
            throws IOException {
        String xml = fetch(FEED, "release feed", "application/atom+xml", 2);
        return parseFeed(xml, includePrereleases);
    }

    static List<UpdateInfo> parseFeed(String xml, boolean includePrereleases)
            throws IOException {
        List<UpdateInfo> result = new ArrayList<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(new StringReader(xml));

            String id = null;
            String link = null;
            String content = null;
            boolean inEntry = false;
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if ("entry".equals(name)) {
                        inEntry = true;
                        id = null;
                        link = null;
                        content = null;
                    } else if (inEntry && "id".equals(name)) {
                        id = parser.nextText();
                    } else if (inEntry && "link".equals(name)) {
                        String href = parser.getAttributeValue(null, "href");
                        if (href != null && href.contains("/releases/tag/")) {
                            link = href;
                        }
                    } else if (inEntry && "content".equals(name)) {
                        content = parser.nextText();
                    }
                } else if (event == XmlPullParser.END_TAG
                        && "entry".equals(parser.getName())) {
                    UpdateInfo info = feedEntry(id, link, content);
                    if (info != null
                            && (includePrereleases
                            || !UpdateInfo.isPrerelease(info.version))) {
                        result.add(info);
                    }
                    inEntry = false;
                }
                event = parser.next();
            }
        } catch (Exception error) {
            throw new IOException("GitHub release feed parse failed", error);
        }
        return result;
    }

    private static UpdateInfo feedEntry(String id, String link, String content) {
        if (id == null) {
            return null;
        }
        int slash = id.lastIndexOf('/');
        String tag = (slash >= 0 ? id.substring(slash + 1) : id).trim();
        String version = tag.startsWith("v") || tag.startsWith("V")
                ? tag.substring(1) : tag;
        if (version.isEmpty()) {
            return null;
        }
        String releaseUrl = link != null ? link
                : "https://github.com/" + REPO + "/releases/tag/" + tag;
        String assetName = ASSET_PREFIX + version + ASSET_SUFFIX;
        String assetUrl = "https://github.com/" + REPO
                + "/releases/download/" + tag + "/" + assetName;
        return new UpdateInfo(version, releaseUrl, assetUrl, assetName, 0L,
                feedNotes(content));
    }

    @SuppressWarnings("deprecation")
    private static String feedNotes(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        CharSequence text = Build.VERSION.SDK_INT >= 24
                ? Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
                : Html.fromHtml(html);
        return text.toString().trim();
    }

    private static IOException asIOException(Exception error) {
        return error instanceof IOException
                ? (IOException) error
                : new IOException(error.getMessage(), error);
    }

    private static String fetch(String url, String label, String accept,
                                int attempts) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", accept)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build();

        IOException last = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try (Response response = HTTP.newCall(request).execute()) {
                ResponseBody body = response.body();
                String text = body == null ? "" : body.string();
                if (!response.isSuccessful()) {
                    throw new IOException("GitHub " + label + " HTTP "
                            + response.code());
                }
                return text;
            } catch (IOException error) {
                last = error;
                HelperLog.info("GitHub " + label + " attempt " + (attempt + 1)
                        + " failed: " + error.getMessage());
                if (attempt + 1 < attempts) {
                    try {
                        Thread.sleep(1000L * (attempt + 1));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw error;
                    }
                }
            }
        }
        throw last == null ? new IOException("GitHub " + label + " failed") : last;
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
