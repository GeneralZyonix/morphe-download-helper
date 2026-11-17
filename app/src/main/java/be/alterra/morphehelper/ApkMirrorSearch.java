package be.alterra.morphehelper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsing helpers for APKMirror search results and app pages.
 *
 * <p>APKMirror search returns direct release links rather than app links, and
 * the page also contains "latest uploads" widgets. A plain "first release
 * link" therefore picks the wrong app surprisingly often. These helpers score
 * every candidate by the app slug, the requested name, and the package tokens
 * before choosing one.</p>
 */
final class ApkMirrorSearch {

    private static final Pattern RELEASE_LINK = Pattern.compile(
            "<a class=\"fontBlack\" href=\"([^\"]+)\"[^>]*>([^<]+)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PLAY_LINK = Pattern.compile(
            "play\\.google\\.com/store/apps/details\\?id=([A-Za-z0-9._]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> GENERIC = new HashSet<>(Arrays.asList(
            "com", "org", "net", "android", "google", "app", "apps", "mobile",
            "client", "release", "beta", "free", "pro", "plus", "lite", "inc",
            "llc", "co", "io", "the", "and", "for", "with"));

    private ApkMirrorSearch() {
    }

    /**
     * Returns the best release URL in an APKMirror search result page.
     *
     * <p>The first release is kept as a fallback for apps whose public name
     * differs from their APKMirror slug. The caller verifies the app page
     * against the Play Store package before downloading, so a bad fallback is
     * rejected instead of producing a wrong APK.</p>
     */
    static String findBestVersionUrl(String html, String appName, String pkg,
                                     String version) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        Matcher matcher = RELEASE_LINK.matcher(html);
        String best = null;
        int bestScore = -1;
        String first = null;
        while (matcher.find()) {
            String href = decode(matcher.group(1));
            String text = decode(matcher.group(2));
            if (href.isEmpty() || !href.contains("-release/")) {
                continue;
            }
            if (first == null) {
                first = href;
            }
            int score = score(href, text, appName, pkg, version);
            if (score > bestScore) {
                bestScore = score;
                best = href;
            }
        }
        return bestScore > 0 ? best : first;
    }

    /**
     * True when at least one search result has a plausible app-name or package
     * match. Used to avoid fetching an unrelated app page (for example, a
     * "latest uploads" card) when the search had no real match.
     */
    static boolean hasPlausibleMatch(String html, String appName, String pkg,
                                     String version) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        Matcher matcher = RELEASE_LINK.matcher(html);
        while (matcher.find()) {
            String href = decode(matcher.group(1));
            String text = decode(matcher.group(2));
            if (href.isEmpty() || !href.contains("-release/")) {
                continue;
            }
            if (score(href, text, appName, pkg, version) > 0) {
                return true;
            }
        }
        return false;
    }

    private static int score(String href, String text, String appName, String pkg,
                             String version) {
        String appSlug = appSlug(href);
        if (appSlug.isEmpty()) {
            return 0;
        }
        int score = 0;
        String nameSlug = slug(appName);
        if (!nameSlug.isEmpty()) {
            if (appSlug.equals(nameSlug)) {
                score += 200;
            } else if (nameSlug.length() >= 5 && appSlug.contains(nameSlug)) {
                score += 80;
            } else if (appSlug.length() >= 5 && nameSlug.contains(appSlug)) {
                score += 60;
            }
        }
        for (String token : tokens(pkg)) {
            if (appSlug.equals(token)) {
                score += 150;
            } else if (appSlug.contains(token)) {
                score += 50;
            }
        }
        if (!nameSlug.isEmpty() && compact(text).contains(compact(nameSlug))) {
            score += 40;
        }
        if (version != null && !version.isEmpty()) {
            String versionSlug = version.replace('.', '-').toLowerCase(Locale.US);
            if (href.toLowerCase(Locale.US).contains(versionSlug)
                    || compact(text).contains(compact(version))) {
                score += 20;
            }
        }
        return score;
    }

    /** Returns {@code /apk/<developer>/<app>/} for a release or app URL. */
    static String appPath(String url) {
        String path = pathOf(url);
        String[] segments = path.split("/");
        if (segments.length < 4 || !"apk".equalsIgnoreCase(segments[1])) {
            return "";
        }
        return "/" + segments[1] + "/" + segments[2] + "/" + segments[3] + "/";
    }

    private static String appSlug(String href) {
        String path = appPath(href);
        if (path.isEmpty()) {
            return "";
        }
        String[] segments = path.split("/");
        return segments.length > 3 ? segments[3].toLowerCase(Locale.US) : "";
    }

    /**
     * True when an APKMirror app/release page references the requested package,
     * or when the page has no Play Store links at all. A page that links only
     * to other packages is rejected before any APK is downloaded.
     */
    static boolean pageMatchesPackage(String html, String pkg) {
        if (html == null || pkg == null || pkg.isEmpty()) {
            return true;
        }
        Matcher matcher = PLAY_LINK.matcher(html);
        boolean sawPlayLink = false;
        while (matcher.find()) {
            sawPlayLink = true;
            if (pkg.equalsIgnoreCase(matcher.group(1))) {
                return true;
            }
        }
        return !sawPlayLink;
    }

    /** First Play Store package on a page, used in diagnostic messages. */
    static String playPackage(String html) {
        if (html == null) {
            return "";
        }
        Matcher matcher = PLAY_LINK.matcher(html);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static Set<String> tokens(String pkg) {
        Set<String> result = new HashSet<>();
        if (pkg == null) {
            return result;
        }
        for (String raw : pkg.toLowerCase(Locale.US).split("[^a-z0-9]+")) {
            if (raw.length() >= 4 && !GENERIC.contains(raw)) {
                result.add(raw);
            }
        }
        return result;
    }

    private static String slug(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static String compact(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]", "");
    }

    private static String pathOf(String url) {
        if (url == null) {
            return "";
        }
        String value = url;
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            int slash = value.indexOf('/', scheme + 3);
            value = slash >= 0 ? value.substring(slash) : "/";
        }
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int hash = value.indexOf('#');
        if (hash >= 0) {
            value = value.substring(0, hash);
        }
        return value;
    }

    private static String decode(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&amp;", "&")
                .replace("&#038;", "&")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ");
    }
}
