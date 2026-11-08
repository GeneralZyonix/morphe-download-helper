package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class UpdateCheckerTest {

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
                        + "MorpheDownloadHelper-v2.0.0-beta.8-universal.apk",
                beta.assetUrl);
        assertEquals("MorpheDownloadHelper-v2.0.0-beta.8-universal.apk",
                beta.assetName);
        assertTrue(beta.notes.contains("Notes"));
        assertTrue(beta.notes.contains("Fixed & tested."));
    }

    @Test
    public void feedFiltersPrereleasesForStableChannel() throws Exception {
        List<UpdateInfo> stable = UpdateChecker.parseFeed(FEED, false);

        assertEquals(1, stable.size());
        assertEquals("1.6.15", stable.get(0).version);
    }
}
