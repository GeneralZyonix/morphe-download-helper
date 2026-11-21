package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class PatchSourceCatalogTest {

    private static final String SAMPLE = "{"
            + "\"version\":1,"
            + "\"generatedAt\":\"2026-11-21T03:49:50Z\","
            + "\"sources\":["
            + "{\"source\":\"github\",\"repo\":\"MorpheApp/morphe-patches\","
            + "\"name\":\"Morphe\",\"description\":\"Official patches\","
            + "\"avatar\":\"\",\"updatedAt\":1795182964000,"
            + "\"isPreRelease\":false,\"isArchived\":false,\"isOfficial\":true},"
            + "{\"source\":\"github\",\"repo\":\"Dan1elTheMan1el/Morphe-Patches\","
            + "\"name\":\"Daniel\",\"description\":\"\",\"avatar\":\"\","
            + "\"updatedAt\":1794623001000,\"isPreRelease\":false,"
            + "\"isArchived\":false,\"isOfficial\":false},"
            + "{\"source\":\"gitlab\",\"repo\":\"someone/other-patches\","
            + "\"name\":\"Other\",\"description\":\"\",\"avatar\":\"\","
            + "\"updatedAt\":1794000000000,\"isPreRelease\":true,"
            + "\"isArchived\":false,\"isOfficial\":false}"
            + "],"
            + "\"apps\":{"
            + "\"com.google.android.youtube\":{\"name\":\"YouTube\",\"icon\":\"\","
            + "\"sources\":[[1,1794623001000,1],[0,1795182964000,121]]},"
            + "\"com.spotify.music\":{\"name\":\"Spotify\",\"icon\":\"\","
            + "\"sources\":[[2,1794000000000,4]]},"
            + "\"com.example.none\":{\"name\":\"None\",\"icon\":\"\","
            + "\"sources\":[]}"
            + "}}";

    @Test
    public void parseKeepsAppsWithSources() throws Exception {
        PatchSourceCatalog catalog = PatchSourceCatalog.parse(SAMPLE);

        assertEquals(3, catalog.sources.size());
        assertEquals(2, catalog.apps.size());
        assertNotNull(catalog.findApp("com.google.android.youtube"));
        assertNull(catalog.findApp("com.example.none"));
    }

    @Test
    public void parseSortsOfficialSourcesFirst() throws Exception {
        PatchSourceCatalog catalog = PatchSourceCatalog.parse(SAMPLE);
        PatchSourceCatalog.App youtube =
                catalog.findApp("com.google.android.youtube");

        assertNotNull(youtube);
        assertEquals(2, youtube.sources.size());
        assertTrue(youtube.sources.get(0).source.official);
        assertEquals("MorpheApp/morphe-patches",
                youtube.sources.get(0).source.repo);
        assertEquals(121, youtube.sources.get(0).patchCount);
        assertEquals(1795182964000L, youtube.latestUpdate());
        assertEquals(122, youtube.totalPatches());
        assertNotNull(youtube.findSource("MorpheApp/morphe-patches"));
        assertNull(youtube.findSource("missing/repo"));
    }

    @Test
    public void searchRanksExactPrefixAndPackageMatches() throws Exception {
        PatchSourceCatalog catalog = PatchSourceCatalog.parse(SAMPLE);

        List<PatchSourceCatalog.App> exact = catalog.search("spotify", 10);
        assertEquals(1, exact.size());
        assertEquals("com.spotify.music", exact.get(0).packageName);

        List<PatchSourceCatalog.App> prefix = catalog.search("you", 10);
        assertEquals(1, prefix.size());
        assertEquals("YouTube", prefix.get(0).name);

        List<PatchSourceCatalog.App> byPackage = catalog.search("google.android", 10);
        assertEquals(1, byPackage.size());
        assertEquals("com.google.android.youtube",
                byPackage.get(0).packageName);

        assertTrue(catalog.search("", 10).isEmpty());
        assertTrue(catalog.search("  ", 10).isEmpty());
        assertTrue(catalog.search("missing", 10).isEmpty());
    }

    @Test
    public void searchHonoursLimit() throws Exception {
        PatchSourceCatalog catalog = PatchSourceCatalog.parse(SAMPLE);

        assertEquals(1, catalog.search("o", 1).size());
        assertEquals(2, catalog.search("o", 10).size());
    }

    @Test
    public void addSourceLinkEncodesRepositoryAndScopesApp() throws Exception {
        PatchSourceCatalog catalog = PatchSourceCatalog.parse(SAMPLE);
        PatchSourceCatalog.App youtube =
                catalog.findApp("com.google.android.youtube");
        PatchSourceCatalog.Source official = youtube.sources.get(0).source;

        String link = PatchSourceCatalog.addSourceLink(official, youtube);

        assertEquals("https://morphe.software/add-source"
                + "?github=MorpheApp%2Fmorphe-patches"
                + "&name=Morphe"
                + "&app=com.google.android.youtube"
                + "&appName=YouTube", link);
    }

    @Test
    public void addSourceLinkSupportsGitLab() throws Exception {
        PatchSourceCatalog catalog = PatchSourceCatalog.parse(SAMPLE);
        PatchSourceCatalog.App spotify = catalog.findApp("com.spotify.music");
        PatchSourceCatalog.Source source = spotify.sources.get(0).source;

        String link = PatchSourceCatalog.addSourceLink(source, spotify);

        assertTrue(link.contains("?gitlab=someone%2Fother-patches"));
        assertTrue(link.contains("&app=com.spotify.music"));
    }

    @Test
    public void oldCatalogIsStale() throws Exception {
        PatchSourceCatalog catalog = PatchSourceCatalog.parse(
                SAMPLE.replace("2026-11-21T03:49:50Z",
                        "2020-01-01T00:00:00Z"));

        assertTrue(catalog.isStale());
        assertEquals("Unknown", PatchSourceCatalog.formatDate(0L));
    }

    @Test
    public void invalidGeneratedAtIsStale() throws Exception {
        PatchSourceCatalog catalog = PatchSourceCatalog.parse(
                SAMPLE.replace("2026-11-21T03:49:50Z", "not-a-date"));

        assertTrue(catalog.isStale());
    }
}
