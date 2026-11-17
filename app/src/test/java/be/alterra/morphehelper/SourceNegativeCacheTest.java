package be.alterra.morphehelper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM tests for negative-result cache classification. */
public class SourceNegativeCacheTest {

    @Test
    public void definitiveNotFoundMessagesAreCached() {
        assertTrue(SourceNegativeCache.isDefinitive("Requested version not found on APKPure"));
        assertTrue(SourceNegativeCache.isDefinitive("No matching app found"));
        assertTrue(SourceNegativeCache.isDefinitive("Not found (HTTP 404)"));
        assertTrue(SourceNegativeCache.isDefinitive("APKCube could not find this app."));
        assertTrue(SourceNegativeCache.isDefinitive("Aptoide file is not TRUSTED"));
    }

    @Test
    public void transientFailuresAreNotCached() {
        assertFalse(SourceNegativeCache.isDefinitive("Connection timed out"));
        assertFalse(SourceNegativeCache.isDefinitive("HTTP 403 from APKPure API"));
        assertFalse(SourceNegativeCache.isDefinitive("Cloudflare challenge"));
        assertFalse(SourceNegativeCache.isDefinitive("APKPure API returned invalid JSON"));
        assertFalse(SourceNegativeCache.isDefinitive("Cancelled"));
        assertFalse(SourceNegativeCache.isDefinitive(null));
    }
}
