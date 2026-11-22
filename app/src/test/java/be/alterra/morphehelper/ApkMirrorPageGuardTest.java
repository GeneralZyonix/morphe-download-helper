package be.alterra.morphehelper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM tests for deciding when an APKMirror page belongs to another app. */
public class ApkMirrorPageGuardTest {

    private static final String APP_PAGE =
            "https://www.apkmirror.com/apk/spotify-ab/spotify-music-podcasts/"
                    + "spotify-music-and-podcasts-9-2-0-2053-release/";

    @Test
    public void ignoresTheApkMirrorInstallerPromotion() {
        assertFalse(DownloadActivity.shouldFailApkMirrorPackage(
                "com.apkmirror.helper.prod", "com.spotify.music",
                APP_PAGE + "spotify-music-and-podcasts-9-2-0-2053-android-apk-download/"));
    }

    @Test
    public void ignoresARealMismatchOnADownloadPage() {
        assertFalse(DownloadActivity.shouldFailApkMirrorPackage(
                "com.example.other", "com.spotify.music",
                APP_PAGE + "spotify-music-and-podcasts-9-2-0-2053-android-apk-download/download/?key=abc"));
    }

    @Test
    public void ignoresARealMismatchOnASearchPage() {
        assertFalse(DownloadActivity.shouldFailApkMirrorPackage(
                "com.example.other", "com.spotify.music",
                "https://www.apkmirror.com/?s=com.spotify.music"));
    }

    @Test
    public void acceptsAMatchingPackage() {
        assertFalse(DownloadActivity.shouldFailApkMirrorPackage(
                "com.spotify.music", "com.spotify.music", APP_PAGE));
    }

    @Test
    public void ignoresAnEmptyResult() {
        assertFalse(DownloadActivity.shouldFailApkMirrorPackage(
                "", "com.spotify.music", APP_PAGE));
        assertFalse(DownloadActivity.shouldFailApkMirrorPackage(
                null, "com.spotify.music", APP_PAGE));
        assertFalse(DownloadActivity.shouldFailApkMirrorPackage(
                "com.example.other", "", APP_PAGE));
    }

    @Test
    public void failsARealMismatchOnAnAppPage() {
        assertTrue(DownloadActivity.shouldFailApkMirrorPackage(
                "com.example.other", "com.spotify.music", APP_PAGE));
        assertTrue(DownloadActivity.shouldFailApkMirrorPackage(
                "com.example.other", "com.spotify.music", null));
    }
}
