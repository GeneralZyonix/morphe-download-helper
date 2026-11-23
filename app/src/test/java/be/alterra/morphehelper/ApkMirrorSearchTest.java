package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM tests for the APKMirror search-result scoring and page checks. */
public class ApkMirrorSearchTest {

    private static String link(String href, String text) {
        return "<a class=\"fontBlack\" href=\"" + href + "\">" + text + "</a>";
    }

    private static String variantRow(String href, String abi, String minAndroid) {
        return variantRow(href, abi, minAndroid, "20003000");
    }

    private static String variantRow(String href, String abi, String minAndroid,
                                     String code) {
        return "<div class=\"table-row\">"
                + "<div class=\"table-cell\"><a href=\"" + href + "\">App 2.0.3</a>"
                + "<span class=\"apkm-badge success\">BUNDLE</span>"
                + "<span class=\"colorLightBlack\">" + code + "</span></div>"
                + "<div class=\"table-cell\">" + abi + "</div>"
                + "<div class=\"table-cell\">" + minAndroid + "</div>"
                + "<div class=\"table-cell\">480-640dpi</div>"
                + "</div>";
    }

    @Test
    public void picksTheExactAppInsteadOfADeveloperNameCollision() {
        String html = link(
                "/apk/signal-garden/atom-visualizer-for-arcore-preview/"
                        + "atom-visualizer-for-arcore-preview-2-1-3-release/",
                "AR Atom Visualizer for ARCore 2.1.3")
                + link(
                "/apk/signal-foundation/signal-private-messenger/"
                        + "signal-private-messenger-8-34-2-release/",
                "Signal Private Messenger 8.34.2");

        String picked = ApkMirrorSearch.findBestVersionUrl(
                html, "Signal", "org.thoughtcrime.securesms", null);

        assertEquals("/apk/signal-foundation/signal-private-messenger/"
                + "signal-private-messenger-8-34-2-release/", picked);
    }

    @Test
    public void picksThePlayStoreInsteadOfTheFirstSearchResult() {
        String html = link(
                "/apk/microg-team/fakestore/microg-companion-0-3-18-40226-release/",
                "microG Companion 0.3.18.40226")
                + link(
                "/apk/google-inc/google-play-store/google-play-store-54-1-11-release/",
                "Google Play Store 54.1.11");

        String picked = ApkMirrorSearch.findBestVersionUrl(
                html, "Google Play Store", "com.android.vending", "54.1.11");

        assertEquals("/apk/google-inc/google-play-store/"
                + "google-play-store-54-1-11-release/", picked);
    }

    @Test
    public void prefersThePhoneAppOverTheWearVariant() {
        String html = link(
                "/apk/google-inc/clock/google-clock-9-3-release/",
                "Google Clock 9.3")
                + link(
                "/apk/google-inc/clock-android-wear/clock-wear-os-6-13-release/",
                "Clock (Wear OS) 6.13");

        String picked = ApkMirrorSearch.findBestVersionUrl(
                html, "Google Clock", "com.google.android.deskclock", "9.3");

        assertEquals("/apk/google-inc/clock/google-clock-9-3-release/", picked);
    }

    @Test
    public void plausibleMatchRejectsAnUnrelatedFirstResult() {
        String html = link(
                "/apk/bytedance-pte-ltd/capcut/capcut-video-editor-19-9-0-release/",
                "CapCut 19.9.0");

        assertFalse(ApkMirrorSearch.hasPlausibleMatch(
                html, "Mobilism", "demigos.com.mobilism", null));
    }

    @Test
    public void plausibleMatchAcceptsANameOrPackageToken() {
        String html = link(
                "/apk/signal-foundation/signal-private-messenger/"
                        + "signal-private-messenger-8-34-2-release/",
                "Signal Private Messenger 8.34.2");

        assertTrue(ApkMirrorSearch.hasPlausibleMatch(
                html, "Signal", "org.thoughtcrime.securesms", null));
        assertTrue(ApkMirrorSearch.hasPlausibleMatch(
                html, "Unrelated", "org.thoughtcrime.signal", null));
    }

    @Test
    public void plausibleMatchRequiresAReleaseLink() {
        assertFalse(ApkMirrorSearch.hasPlausibleMatch(
                "<html></html>", "Mobilism", "demigos.com.mobilism", null));
    }

    @Test
    public void appPathHandlesAbsoluteAndRelativeUrls() {
        assertEquals("/apk/google-inc/youtube/", ApkMirrorSearch.appPath(
                "https://www.apkmirror.com/apk/google-inc/youtube/"
                        + "youtube-21-46-522-release/"));
        assertEquals("/apk/google-inc/youtube/", ApkMirrorSearch.appPath(
                "/apk/google-inc/youtube/"));
        assertEquals("", ApkMirrorSearch.appPath("https://www.apkmirror.com/?s=youtube"));
    }

    @Test
    public void pagePackageCheckRejectsAnotherApp() {
        String page = "<a href=\"https://play.google.com/store/apps/details"
                + "?id=com.google.android.youtube\">Play Store</a>";

        assertTrue(ApkMirrorSearch.pageMatchesPackage(page, "com.google.android.youtube"));
        assertFalse(ApkMirrorSearch.pageMatchesPackage(page, "com.instagram.android"));
        assertTrue(ApkMirrorSearch.pageMatchesPackage("<html></html>", "com.example.app"));
    }

    @Test
    public void variantRowsExposeTheirMinimumAndroidVersion() {
        assertEquals(29, ApkMirrorSearch.minSdkFromVariantRow(
                "<div class=\"table-cell\">arm64-v8a</div>"
                        + "<div class=\"table-cell\">Android 10+</div>"));
        assertEquals(26, ApkMirrorSearch.minSdkFromVariantRow(
                "<div class=\"table-cell\">universal</div>"
                        + "<div class=\"table-cell\">Android 8.0+</div>"));
        assertEquals(27, ApkMirrorSearch.minSdkFromVariantRow(
                "<div class=\"table-cell\">universal</div>"
                        + "<div class=\"table-cell\">Android 8.1+</div>"));
        assertEquals(32, ApkMirrorSearch.minSdkFromVariantRow(
                "<div class=\"table-cell\">universal</div>"
                        + "<div class=\"table-cell\">Android 12L+</div>"));
        assertEquals(-1, ApkMirrorSearch.minSdkFromVariantRow(
                "<div class=\"table-cell\">universal</div>"));
    }

    @Test
    public void variantPageReadsTheMinApiRow() {
        String page = "<div class=\"infoSlide\">\n"
                + "  <span class=\"infoSlide-value\">Min: Android 10 (Q, API 29)</span>\n"
                + "</div>";

        assertEquals(29, ApkMirrorSearch.minSdkFromVariantPage(page));
        assertEquals(26, ApkMirrorSearch.minSdkFromVariantPage(
                "<title>App 2.0 (Android 8.0+) APK Download</title>"));
    }

    @Test
    public void variantCompatibilityUsesTheDeviceSdk() {
        String android8 = "<div class=\"table-cell\">Android 8.0+</div>";
        String android10 = "<div class=\"table-cell\">Android 10+</div>";

        assertTrue(ApkMirrorSearch.variantSupportsSdk(android8, 26));
        assertFalse(ApkMirrorSearch.variantSupportsSdk(android10, 26));
        assertTrue(ApkMirrorSearch.variantSupportsSdk(android10, 29));
        assertTrue(ApkMirrorSearch.variantSupportsSdk(
                "<div class=\"table-cell\">unknown</div>", 23));
    }

    @Test
    public void androidVersionNamesCoverCurrentApiLevels() {
        assertEquals("8.0", ApkMirrorSearch.androidVersionName(26));
        assertEquals("10", ApkMirrorSearch.androidVersionName(29));
        assertEquals("12L", ApkMirrorSearch.androidVersionName(32));
        assertEquals("16", ApkMirrorSearch.androidVersionName(36));
    }

    @Test
    public void api26PicksTheUniversalAndroid8Variant() throws Exception {
        String arm64 = "/apk/darken/sd-maid/sd-maid-2-0-3-rc0-release/"
                + "sd-maid-2-0-3-rc0-3-android-apk-download/";
        String universal12l = "/apk/darken/sd-maid/sd-maid-2-0-3-rc0-release/"
                + "sd-maid-2-0-3-rc0-android-apk-download/";
        String universal8 = "/apk/darken/sd-maid/sd-maid-2-0-3-rc0-release/"
                + "sd-maid-2-0-3-rc0-2-android-apk-download/";
        String page = variantRow(arm64, "arm64-v8a", "Android 10+")
                + variantRow(universal12l, "universal", "Android 12L+")
                + variantRow(universal8, "universal", "Android 8.0+");

        String picked = ApkMirrorSearch.chooseVariant(
                page, "apkm", new String[]{"arm64-v8a"}, new long[]{20003000}, 26);

        assertEquals(universal8, picked);
    }

    @Test
    public void modernDeviceStillPrefersTheExactAbi() throws Exception {
        String arm64 = "/apk/darken/sd-maid/sd-maid-2-0-3-rc0-release/"
                + "sd-maid-2-0-3-rc0-3-android-apk-download/";
        String universal8 = "/apk/darken/sd-maid/sd-maid-2-0-3-rc0-release/"
                + "sd-maid-2-0-3-rc0-2-android-apk-download/";
        String page = variantRow(arm64, "arm64-v8a", "Android 10+")
                + variantRow(universal8, "universal", "Android 8.0+");

        String picked = ApkMirrorSearch.chooseVariant(
                page, "apkm", new String[]{"arm64-v8a"}, new long[]{20003000}, 36);

        assertEquals(arm64, picked);
    }

    @Test
    public void incompatibleVariantsFailBeforeDownloading() throws Exception {
        String page = variantRow(
                "/apk/darken/sd-maid/sd-maid-2-0-3-rc0-release/"
                        + "sd-maid-2-0-3-rc0-3-android-apk-download/",
                "arm64-v8a", "Android 10+")
                + variantRow(
                "/apk/darken/sd-maid/sd-maid-2-0-3-rc0-release/"
                        + "sd-maid-2-0-3-rc0-android-apk-download/",
                "universal", "Android 12L+");

        try {
            ApkMirrorSearch.chooseVariant(
                    page, "apkm", new String[]{"arm64-v8a"}, new long[]{20003000}, 26);
        } catch (ApkMirrorSearch.IncompatibleVariantException expected) {
            assertEquals(29, expected.minSdk);
            assertTrue(expected.getMessage().contains("requires Android 10"));
            return;
        }
        throw new AssertionError("Expected an incompatible-build rejection");
    }

    @Test
    public void choosesTheRequestedBuildCodeInsteadOfTheFirstVariant() throws Exception {
        String wrongBuild = "/apk/facebook-2/facebook/facebook-588-release/"
                + "facebook-588-5-android-apk-download/";
        String rightBuild = "/apk/facebook-2/facebook/facebook-588-release/"
                + "facebook-588-9-android-apk-download/";
        String page = variantRow(wrongBuild, "arm64-v8a", "Android 10+", "707615090")
                + variantRow(rightBuild, "arm64-v8a", "Android 10+", "707615136");

        String picked = ApkMirrorSearch.chooseVariant(page, "apkm",
                new String[]{"arm64-v8a"}, new long[]{707615136, 707615135}, 37);

        assertEquals(rightBuild, picked);
    }

    @Test
    public void variantUrlParentIsTheReleasePage() {
        assertEquals("https://www.apkmirror.com/apk/facebook-2/facebook/"
                        + "facebook-588-release/",
                ApkMirrorSearch.parentReleaseUrl(
                        "https://www.apkmirror.com/apk/facebook-2/facebook/"
                                + "facebook-588-release/"
                                + "facebook-588-5-android-apk-download/"));
        assertEquals("https://www.apkmirror.com/apk/facebook-2/facebook/"
                        + "facebook-588-release/",
                ApkMirrorSearch.parentReleaseUrl(
                        "https://www.apkmirror.com/apk/facebook-2/facebook/"
                                + "facebook-588-release/"
                                + "facebook-588-5-android-apk-download/?x=1"));
    }
}
