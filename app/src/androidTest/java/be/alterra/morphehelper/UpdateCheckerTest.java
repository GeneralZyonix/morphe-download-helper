package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class UpdateCheckerTest {

    private static final String VARIANT_SUFFIX = BuildConfig.LAUNCHER_ICON_ENABLED
            ? "-universal.apk" : "-universal-noicon.apk";

    private static final String FEED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <id>tag:github.com,2008:Repository/123/v2.0.0-beta.8</id>
                <link rel="alternate" type="text/html"
                    href="https://github.com/GeneralZyonix/morphe-download-helper/releases/tag/v2.0.0-beta.8"/>
                <title>v2.0.0-beta.8</title>
                <content type="html">&lt;h2&gt;Notes&lt;/h2&gt;&lt;p&gt;Fixed &amp;amp; tested.&lt;/p&gt;</content>
              </entry>
              <entry>
                <id>tag:github.com,2008:Repository/123/v1.6.15</id>
                <link rel="alternate" type="text/html"
                    href="https://github.com/GeneralZyonix/morphe-download-helper/releases/tag/v1.6.15"/>
                <title>v1.6.15</title>
                <content type="html">Stable notes</content>
              </entry>
            </feed>
            """;

    @Test
    public void feedParsesVersionsNotesAndAssetUrl() throws Exception {
        List<UpdateInfo> releases = UpdateChecker.parseFeed(FEED, true);

        assertEquals(2, releases.size());
        UpdateInfo beta = releases.get(0);
        assertEquals("2.0.0-beta.8", beta.version);
        assertEquals("https://github.com/GeneralZyonix/morphe-download-helper/"
                        + "releases/download/v2.0.0-beta.8/"
                        + "MorpheDownloadHelper-v2.0.0-beta.8" + VARIANT_SUFFIX,
                beta.assetUrl);
        assertEquals("MorpheDownloadHelper-v2.0.0-beta.8" + VARIANT_SUFFIX,
                beta.assetName);
        assertTrue(beta.notes.contains("Notes"));
        assertTrue(beta.notes.contains("Fixed & tested."));
    }

    @Test
    public void apiPicksTheAssetForThisVariantOnly() throws Exception {
        JSONArray assets = new JSONArray();
        assets.put(new JSONObject()
                .put("name", "MorpheDownloadHelper-v9.9.9-universal.apk")
                .put("browser_download_url", "https://example.test/icon.apk")
                .put("size", 1));
        assets.put(new JSONObject()
                .put("name", "MorpheDownloadHelper-v9.9.9-universal-noicon.apk")
                .put("browser_download_url", "https://example.test/noicon.apk")
                .put("size", 2));
        JSONObject release = new JSONObject()
                .put("tag_name", "v9.9.9")
                .put("html_url", "https://example.test/release")
                .put("body", "notes")
                .put("assets", assets);

        UpdateInfo info = UpdateChecker.parseRelease(release);

        assertEquals(BuildConfig.LAUNCHER_ICON_ENABLED
                        ? "MorpheDownloadHelper-v9.9.9-universal.apk"
                        : "MorpheDownloadHelper-v9.9.9-universal-noicon.apk",
                info.assetName);
    }

    @Test
    public void feedFiltersPrereleasesForStableChannel() throws Exception {
        List<UpdateInfo> stable = UpdateChecker.parseFeed(FEED, false);

        assertEquals(1, stable.size());
        assertEquals("1.6.15", stable.get(0).version);
    }

    private static final String CHANGELOG = """
            # Changelog

            ## [Unreleased]

            ## [2.0.0-beta.8] - 2026-11-09

            Beta notes.

            ## [1.6.16] - 2026-11-08

            Stable notes.
            """;

    @Test
    public void changelogParsesVersionsNotesAndAssetUrl() {
        List<UpdateInfo> releases = UpdateChecker.parseChangelog(CHANGELOG, true);

        assertEquals(2, releases.size());
        UpdateInfo beta = releases.get(0);
        assertEquals("2.0.0-beta.8", beta.version);
        assertEquals("https://github.com/GeneralZyonix/morphe-download-helper/"
                        + "releases/download/v2.0.0-beta.8/"
                        + "MorpheDownloadHelper-v2.0.0-beta.8" + VARIANT_SUFFIX,
                beta.assetUrl);
        assertTrue(beta.notes.contains("Beta notes."));
    }

    @Test
    public void changelogFiltersPrereleasesForStableChannel() {
        List<UpdateInfo> stable = UpdateChecker.parseChangelog(CHANGELOG, false);

        assertEquals(1, stable.size());
        assertEquals("1.6.16", stable.get(0).version);
        assertTrue(stable.get(0).notes.contains("Stable notes."));
    }

    @Test
    public void changelogHonorsThePrereleaseMarkerWithoutAVersionSuffix() {
        String changelog = """
                # Changelog

                ## [9.9.9] - 2026-11-22

                <!-- prerelease -->
                Testing build.

                ## [9.9.8] - 2026-11-21

                Stable build.
                """;

        List<UpdateInfo> stable = UpdateChecker.parseChangelog(changelog, false);
        assertEquals(1, stable.size());
        assertEquals("9.9.8", stable.get(0).version);

        List<UpdateInfo> all = UpdateChecker.parseChangelog(changelog, true);
        assertEquals(2, all.size());
        assertEquals("9.9.9", all.get(0).version);
        assertTrue(!all.get(0).notes.contains("prerelease"));
    }
}
