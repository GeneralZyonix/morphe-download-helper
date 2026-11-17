package be.alterra.morphehelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.text.TextUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class GitHubReleaseResolver {

    static final class Candidate {
        final String repo;
        final String tag;
        final String assetName;
        final String assetUrl;
        final String fileType;

        Candidate(String repo, String tag, String assetName, String assetUrl, String fileType) {
            this.repo = repo;
            this.tag = tag;
            this.assetName = assetName;
            this.assetUrl = assetUrl;
            this.fileType = fileType;
        }
    }

    private static final String API = "https://api.github.com";
    private static final String USER_AGENT = "Morphe-Download-Helper/1.6.10";

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build();

    private GitHubReleaseResolver() {
    }

    static List<Candidate> search(String appName, String pkg, String version,
                                  String fileType, String[] abis) throws IOException {
        Set<String> repos = new LinkedHashSet<>();
        // Search the package name in READMEs and the app name in repository
        // names together. Apps distributed only on GitHub often mention the
        // package in the README, and the repository may have been renamed since
        // the patch was written (Myspoti -> Orpheon).
        ExecutorService searchPool = Executors.newFixedThreadPool(2);
        try {
            Future<List<String>> packageSearch = null;
            Future<List<String>> nameSearch = null;
            if (pkg != null && !pkg.trim().isEmpty()) {
                packageSearch = searchPool.submit(
                        () -> searchRepos(pkg + " in:readme"));
            }
            if (appName != null && !appName.trim().isEmpty()) {
                nameSearch = searchPool.submit(
                        () -> searchRepos(appName + " in:name"));
            }
            if (packageSearch != null) {
                try {
                    repos.addAll(packageSearch.get());
                } catch (Exception error) {
                    HelperLog.error("GitHub package search failed", error);
                }
            }
            if (nameSearch != null) {
                try {
                    repos.addAll(nameSearch.get());
                } catch (Exception error) {
                    HelperLog.error("GitHub name search failed", error);
                }
            }
        } finally {
            searchPool.shutdownNow();
        }
        if (repos.isEmpty() && appName != null && !appName.trim().isEmpty()) {
            repos.addAll(searchRepos(appName + " in:readme"));
        }
        HelperLog.info("GitHub search repos: " + TextUtils.join(", ", repos));

        List<Candidate> candidates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String repo : repos) {
            if (!seen.add(repo)) {
                continue;
            }
            try {
                candidates.addAll(findInRepo(repo, version, fileType, abis));
            } catch (Exception error) {
                HelperLog.error("GitHub repo lookup failed: " + repo, error);
            }
            if (candidates.size() >= 8) {
                break;
            }
        }
        return candidates;
    }

    static List<Candidate> findInRepo(String repo, String version, String fileType,
                                      String[] abis) throws IOException {
        return findInRepo(repo, version, fileType, abis, "");
    }

    static List<Candidate> findInRepo(String repo, String version, String fileType,
                                      String[] abis, String assetFilter) throws IOException {
        String url = API + "/repos/" + repo + "/releases?per_page=100";
        JSONArray releases = getArray(url);
        List<Candidate> candidates = new ArrayList<>();
        String filter = assetFilter == null ? "" : assetFilter.trim().toLowerCase(Locale.US);
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.optJSONObject(i);
            if (release == null || release.optBoolean("draft", false)) {
                continue;
            }
            if (!matchesVersion(release.optString("tag_name"),
                    release.optString("name"), version)) {
                continue;
            }
            Candidate candidate = assetFor(release, repo, fileType, abis, filter);
            if (candidate != null) {
                candidates.add(candidate);
                if (noVersion(version)) {
                    break;
                }
            }
        }
        return candidates;
    }

    private static Candidate assetFor(JSONObject release, String repo, String fileType,
                                      String[] abis, String assetFilter) {
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) {
            return null;
        }
        String requested = fileType == null ? "" : fileType.toLowerCase(Locale.US);
        String[] order;
        if ("apks".equals(requested)) {
            order = new String[]{"apks", "xapk", "apkm", "apk"};
        } else if ("xapk".equals(requested)) {
            order = new String[]{"xapk", "apks", "apkm", "apk"};
        } else if ("apkm".equals(requested)) {
            order = new String[]{"apkm", "apks", "xapk", "apk"};
        } else {
            order = new String[]{"apk", "apks", "xapk", "apkm"};
        }

        for (String extension : order) {
            Candidate best = null;
            int bestScore = -1;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) {
                    continue;
                }
                String name = asset.optString("name", "");
                String url = asset.optString("browser_download_url", "");
                if (!name.toLowerCase(Locale.US).endsWith("." + extension)
                        || !url.startsWith("https://")) {
                    continue;
                }
                if (!assetFilter.isEmpty()
                        && !name.toLowerCase(Locale.US).contains(assetFilter)) {
                    continue;
                }
                int score = abiScore(name, abis);
                if (score > bestScore) {
                    bestScore = score;
                    best = new Candidate(repo, release.optString("tag_name"), name, url,
                            extension);
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    static int abiScore(String assetName, String[] abis) {
        String name = assetName.toLowerCase(Locale.US);
        boolean arm64 = name.contains("arm64") || name.contains("aarch64")
                || name.contains("armv8");
        boolean arm32 = !arm64 && (name.contains("armv7")
                || name.contains("armeabi") || name.contains("arm"));
        // A universal APK is always usable and should beat an asset for an ABI
        // the device did not list.
        int score = name.contains("universal") ? 2 : 0;
        if (abis != null) {
            int priority = 3;
            for (String abi : abis) {
                String wanted = abi == null ? "" : abi.toLowerCase(Locale.US);
                boolean match = false;
                if (wanted.contains("arm64") || wanted.contains("aarch64")
                        || wanted.contains("armv8")) {
                    match = arm64;
                } else if (wanted.contains("armeabi") || wanted.contains("armv7")
                        || wanted.equals("arm")) {
                    match = arm32;
                } else if (wanted.contains("x86_64") || wanted.equals("x64")) {
                    match = name.contains("x86_64") || name.contains("x64");
                } else if (wanted.equals("x86")) {
                    match = name.contains("x86") && !name.contains("x86_64");
                }
                if (match) {
                    score = Math.max(score, priority);
                }
                priority = Math.max(1, priority - 1);
            }
        }
        return score;
    }

    private static boolean matchesVersion(String tag, String name, String version) {
        if (noVersion(version)) {
            return true;
        }
        return normalize(tag).equals(normalize(version));
    }

    private static boolean noVersion(String version) {
        return version == null || version.trim().isEmpty()
                || "any".equalsIgnoreCase(version.trim());
    }

    private static String normalize(String value) {
        String result = value == null ? "" : value.trim().toLowerCase(Locale.US);
        while (result.startsWith("v")) {
            result = result.substring(1);
        }
        return result;
    }

    private static List<String> searchRepos(String query) throws IOException {
        String url = API + "/search/repositories?q="
                + URLEncoder.encode(query, "UTF-8") + "&per_page=8";
        HelperLog.info("GitHub search: " + query);
        JSONObject root = getObject(url);
        JSONArray items = root.optJSONArray("items");
        List<String> repos = new ArrayList<>();
        if (items == null) {
            HelperLog.info("GitHub search: no items");
            return repos;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) {
                String fullName = item.optString("full_name", "");
                if (!fullName.isEmpty()) {
                    repos.add(fullName);
                }
            }
        }
        HelperLog.info("GitHub search: " + repos.size() + " repo(s)");
        return repos;
    }

    private static JSONObject getObject(String url) throws IOException {
        String body = get(url);
        try {
            return new JSONObject(body);
        } catch (JSONException error) {
            throw new IOException("GitHub returned invalid JSON", error);
        }
    }

    private static JSONArray getArray(String url) throws IOException {
        String body = get(url);
        try {
            return new JSONArray(body);
        } catch (JSONException error) {
            throw new IOException("GitHub returned invalid JSON", error);
        }
    }

    private static String get(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build();
        try (Response response = HTTP.newCall(request).execute()) {
            String body = "";
            ResponseBody responseBody = response.body();
            if (responseBody != null) {
                body = responseBody.string();
            }
            if (!response.isSuccessful()) {
                String detail = "";
                try {
                    detail = new JSONObject(body).optString("message", "");
                } catch (JSONException ignored) {
                }
                throw new IOException("GitHub API HTTP " + response.code()
                        + (detail.isEmpty() ? "" : ": " + detail));
            }
            return body;
        }
    }
}









