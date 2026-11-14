package be.alterra.morphehelper;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports GitHub-backed entries from Obtainium/ObtainX JSON exports and parses
 * shared GitHub links.
 */
final class GitHubSourceImporter {

    static final class Result {
        final int imported;
        final int skipped;

        Result(int imported, int skipped) {
            this.imported = imported;
            this.skipped = skipped;
        }
    }

    private static final Pattern GITHUB_URL = Pattern.compile(
            "(?i)(?:https?://)?(?:www\\.)?github\\.com/"
                    + "([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)");
    private static final Pattern PACKAGE_NAME = Pattern.compile(
            "\\b[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*){2,}\\b");
    private static final Pattern PLAY_ID = Pattern.compile(
            "[?&]id=([A-Za-z0-9_.]+)");

    private GitHubSourceImporter() {
    }

    static Result importJson(Context context, String json) {
        if (TextUtils.isEmpty(json)) {
            return new Result(0, 0);
        }
        JSONArray apps;
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("[")) {
                apps = new JSONArray(trimmed);
            } else {
                JSONObject root = new JSONObject(trimmed);
                apps = root.optJSONArray("apps");
                if (apps == null) {
                    apps = root.optJSONArray("sources");
                }
                if (apps == null) {
                    apps = root.optJSONArray("items");
                }
                if (apps == null) {
                    return new Result(0, 0);
                }
            }
        } catch (Exception error) {
            return new Result(0, 0);
        }

        List<GitHubAppStore.Entry> entries = new ArrayList<>();
        int skipped = 0;
        int limit = Math.min(apps.length(), 5_000);
        for (int i = 0; i < limit; i++) {
            JSONObject app = apps.optJSONObject(i);
            if (app == null) {
                skipped++;
                continue;
            }
            String packageName = first(app,
                    "id", "packageName", "package", "appId");
            String repo = GitHubAppStore.normalizeRepo(first(app,
                    "url", "sourceUrl", "repoUrl", "repo", "homepage"));
            if (!AppSettings.isValidPackageName(packageName) || repo.isEmpty()) {
                skipped++;
                continue;
            }
            String label = first(app, "name", "label", "title");
            if (label.isEmpty()) {
                label = repo.substring(repo.indexOf('/') + 1);
            }
            entries.add(new GitHubAppStore.Entry(packageName, repo, label,
                    System.currentTimeMillis(), false, "", true));
        }
        GitHubAppStore.mergeMissingManual(context, entries);
        return new Result(entries.size(), skipped);
    }

    private static String first(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /** Returns owner/repo when the shared text contains a GitHub link. */
    static String findGitHubRepo(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        Matcher matcher = GITHUB_URL.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        return GitHubAppStore.normalizeRepo("github.com/" + matcher.group(1));
    }

    /** Returns a package name from a shared link or plain text, if present. */
    static String findPackageName(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        Matcher play = PLAY_ID.matcher(text);
        while (play.find()) {
            String candidate = play.group(1);
            if (AppSettings.isValidPackageName(candidate)) {
                return candidate;
            }
        }
        // Do not treat URL hosts such as www.github.com as package names.
        String withoutUrls = text.replaceAll("(?i)https?://\\S+", " ");
        Matcher matcher = PACKAGE_NAME.matcher(withoutUrls);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (candidate.toLowerCase(java.util.Locale.US).startsWith("www.")
                    || !AppSettings.isValidPackageName(candidate)) {
                continue;
            }
            return candidate;
        }
        return "";
    }
}
