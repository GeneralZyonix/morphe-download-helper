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
}
