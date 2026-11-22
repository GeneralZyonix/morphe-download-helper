package be.alterra.morphehelper;

import java.io.IOException;
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
    private static final Pattern VARIANT_ANDROID = Pattern.compile(
            "Android\\s+(\\d+(?:\\.\\d+)?L?)\\s*\\+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VARIANT_MIN_API = Pattern.compile(
            "Min:[\\s\\S]{0,120}?Android\\s+[^<(]{0,30}\\([^)]*API\\s+(\\d+)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PAGE_TITLE = Pattern.compile(
            "<title[^>]*>([\\s\\S]*?)</title>",
            Pattern.CASE_INSENSITIVE);

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

    /**
     * Reads the minimum Android version from one APKMirror variant row, such as
     * {@code Android 8.0+} or {@code Android 12L+}. Returns -1 when the row has
     * no recognizable version.
     */
    static int minSdkFromVariantRow(String rowHtml) {
        if (rowHtml == null || rowHtml.isEmpty()) {
            return -1;
        }
        Matcher matcher = VARIANT_ANDROID.matcher(rowHtml);
        return matcher.find() ? apiForAndroidVersion(matcher.group(1)) : -1;
    }

    /**
     * Reads the minimum SDK from an APKMirror variant page. The page normally
     * shows {@code Min: Android 10 (Q, API 29)}; the variant title is used as a
     * fallback for layouts that omit that row.
     */
    static int minSdkFromVariantPage(String html) {
        if (html == null || html.isEmpty()) {
            return -1;
        }
        Matcher matcher = VARIANT_MIN_API.matcher(html);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                // Fall through to the version label.
            }
        }
        Matcher title = PAGE_TITLE.matcher(html);
        if (title.find()) {
            int sdk = minSdkFromVariantRow(title.group(1));
            if (sdk >= 0) {
                return sdk;
            }
        }
        return minSdkFromVariantRow(html);
    }

    /** True when a variant has no known minimum SDK or supports this device. */
    static boolean variantSupportsSdk(String rowHtml, int deviceSdk) {
        int minSdk = minSdkFromVariantRow(rowHtml);
        return minSdk < 0 || minSdk <= deviceSdk;
    }

    /** Maps an APKMirror Android label such as {@code 8.0} or {@code 12L} to an API level. */
    static int apiForAndroidVersion(String version) {
        if (version == null) {
            return -1;
        }
        String value = version.trim().toUpperCase(Locale.US);
        if (value.endsWith("L")) {
            return "12L".equals(value) ? 32 : -1;
        }
        String[] parts = value.split("\\.");
        int major;
        int minor = -1;
        try {
            major = Integer.parseInt(parts[0]);
            if (parts.length > 1) {
                minor = Integer.parseInt(parts[1]);
            }
        } catch (NumberFormatException ignored) {
            return -1;
        }
        switch (major) {
            case 4:
                if (minor >= 4) return 19;
                if (minor == 3) return 18;
                if (minor == 2) return 17;
                if (minor == 1) return 16;
                return 14;
            case 5:
                return minor >= 1 ? 22 : 21;
            case 6:
                return 23;
            case 7:
                return minor >= 1 ? 25 : 24;
            case 8:
                return minor >= 1 ? 27 : 26;
            case 9:
                return 28;
            case 10:
                return 29;
            case 11:
                return 30;
            case 12:
                return 31;
            case 13:
                return 33;
            case 14:
                return 34;
            case 15:
                return 35;
            case 16:
                return 36;
            default:
                return -1;
        }
    }

    /** Human-readable Android version for an API level. */
    static String androidVersionName(int api) {
        switch (api) {
            case 14: return "4.0";
            case 15: return "4.0.3";
            case 16: return "4.1";
            case 17: return "4.2";
            case 18: return "4.3";
            case 19: return "4.4";
            case 21: return "5.0";
            case 22: return "5.1";
            case 23: return "6";
            case 24: return "7.0";
            case 25: return "7.1";
            case 26: return "8.0";
            case 27: return "8.1";
            case 28: return "9";
            case 29: return "10";
            case 30: return "11";
            case 31: return "12";
            case 32: return "12L";
            case 33: return "13";
            case 34: return "14";
            case 35: return "15";
            case 36: return "16";
            default: return "API " + api;
        }
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

    /** A build exists, but its minimum Android version is newer than the device. */
    static final class IncompatibleVariantException extends IOException {
        final int minSdk;

        IncompatibleVariantException(int minSdk, int deviceSdk) {
            super("This version requires Android " + androidVersionName(minSdk)
                    + " (API " + minSdk + "), but this device runs Android "
                    + androidVersionName(deviceSdk) + " (API " + deviceSdk + ").");
            this.minSdk = minSdk;
        }
    }

    /**
     * Picks the best compatible APKMirror variant row for the requested archive
     * type and ABI. Rows whose minimum Android version is newer than
     * {@code deviceSdk} are ignored, so a universal Android 8 build can be
     * selected instead of an arm64 build that requires Android 10.
     */
    static String chooseVariant(String versionHtml, String fileType, String[] abis,
                                long[] requestedCodes, int deviceSdk)
            throws IOException {
        if (versionHtml == null || versionHtml.isEmpty()) {
            return null;
        }
        String[] rows = versionHtml.split("<div class=\"table-row");
        String fallback = null;
        String plainApk = null;
        String plainApkAbi = null;
        String plainApkExact = null;
        String plainApkExactAbi = null;
        String plainApkUniversal = null;
        String bundle = null;
        String bundleAbi = null;
        String bundleExact = null;
        String bundleExactAbi = null;
        boolean sawVersionCode = false;
        boolean sawCompatible = false;
        int rejectedMinSdk = Integer.MAX_VALUE;
        String preferredAbi = (abis != null && abis.length > 0) ? abis[0] : null;
        boolean wantsExactBuild = requestedCodes != null && requestedCodes.length > 0;

        for (String row : rows) {
            Matcher m = Pattern.compile(
                    "href=\"([^\"]+-android-apk-download/)\"",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(row);
            if (!m.find()) continue;

            String href = decode(m.group(1));
            long rowCode = variantCode(row);
            if (rowCode > 0) {
                sawVersionCode = true;
            }

            int rowMinSdk = minSdkFromVariantRow(row);
            if (rowMinSdk > deviceSdk) {
                rejectedMinSdk = Math.min(rejectedMinSdk, rowMinSdk);
                continue;
            }
            sawCompatible = true;
            if (fallback == null) fallback = href;

            // Badge text and row classes identify bundles and plain APKs.
            boolean isBundle = row.contains("BUNDLE") || row.contains("apkm-badge success");
            boolean isPlainApk = row.contains(">APK<") || row.contains("apkm-badge\">APK");
            // "nodpi" is the density cell shown on every APK row, so it does not
            // mean the file is a universal APK. Only the explicit label does.
            boolean universal = row.contains("universal");
            boolean hasAbi = preferredAbi != null && row.contains(preferredAbi);
            boolean exactCode = rowCode > 0 && contains(requestedCodes, rowCode);

            if (isPlainApk && exactCode && hasAbi && plainApkExactAbi == null) {
                plainApkExactAbi = href;
            }
            if (isPlainApk && exactCode && plainApkExact == null) {
                plainApkExact = href;
            }
            if (isPlainApk && universal && plainApkUniversal == null) plainApkUniversal = href;
            if (isPlainApk && hasAbi && plainApkAbi == null) plainApkAbi = href;
            if (isPlainApk && plainApk == null) plainApk = href;
            if (isBundle && exactCode && hasAbi && bundleExactAbi == null) {
                bundleExactAbi = href;
            }
            if (isBundle && exactCode && bundleExact == null) {
                bundleExact = href;
            }
            if (isBundle && hasAbi && bundleAbi == null) bundleAbi = href;
            if (isBundle && bundle == null) bundle = href;
        }

        boolean exactPlain = plainApkExactAbi != null || plainApkExact != null;
        boolean exactBundle = bundleExactAbi != null || bundleExact != null;
        if ("apk".equalsIgnoreCase(fileType) || fileType == null || fileType.isEmpty()) {
            if (plainApkExactAbi != null) return plainApkExactAbi;
            if (plainApkExact != null) return plainApkExact;
            // Split-bundle rows still expose an APK variant, and its key page
            // can serve the base APK. Prefer the exact build over a plain APK
            // with the wrong build number.
            if (bundleExactAbi != null) return bundleExactAbi;
            if (bundleExact != null) return bundleExact;
            if (wantsExactBuild && sawVersionCode && !exactPlain && !exactBundle) {
                return noMatchingVariant(rejectedMinSdk, sawCompatible, deviceSdk);
            }
            if (plainApkUniversal != null) return plainApkUniversal;
            // APKMirror publishes one row per CPU variant. Picking the first plain
            // APK can download a different ABI with a different versionCode, which
            // then fails validation after the whole file has been transferred.
            if (plainApkAbi != null) return plainApkAbi;
            if (plainApk != null) return plainApk;
        } else {
            if (bundleExactAbi != null) return bundleExactAbi;
            if (bundleExact != null) return bundleExact;
            if (plainApkExactAbi != null) return plainApkExactAbi;
            if (plainApkExact != null) return plainApkExact;
            if (wantsExactBuild && sawVersionCode && !exactBundle) {
                return noMatchingVariant(rejectedMinSdk, sawCompatible, deviceSdk);
            }
            if (bundleAbi != null) return bundleAbi;
            if (bundle != null) return bundle;
            if (plainApkAbi != null) return plainApkAbi;
            if (plainApk != null) return plainApk;
        }
        if (fallback == null && rejectedMinSdk != Integer.MAX_VALUE) {
            throw new IncompatibleVariantException(rejectedMinSdk, deviceSdk);
        }
        return fallback;
    }

    /** Returns null for a missing build, or throws when only incompatible builds exist. */
    private static String noMatchingVariant(int rejectedMinSdk, boolean sawCompatible,
                                            int deviceSdk) throws IOException {
        if (!sawCompatible && rejectedMinSdk != Integer.MAX_VALUE) {
            throw new IncompatibleVariantException(rejectedMinSdk, deviceSdk);
        }
        return null;
    }

    /** Reads the numeric build number shown in an APKMirror variant row. */
    private static long variantCode(String row) {
        Matcher matcher = Pattern.compile(
                "class=\"colorLightBlack\">\\s*(\\d+)\\s*</span>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(row);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean contains(long[] values, long target) {
        if (values == null) {
            return false;
        }
        for (long value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }
}
