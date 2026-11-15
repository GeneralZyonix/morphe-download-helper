package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** JVM tests for the Uptodown version/file selection. */
public class UptodownApiTest {

    private static JSONArray items() throws Exception {
        JSONArray items = new JSONArray();
        items.put(new JSONObject(
                "{\"fileID\":1238592379,\"version\":\"4.0.04.1112.03\",\"kindFile\":\"apk\"}"));
        items.put(new JSONObject(
                "{\"fileID\":1232676402,\"version\":\"4.0.03.1028.03\",\"kindFile\":\"apk\"}"));
        items.put(new JSONObject(
                "{\"fileID\":1229268575,\"version\":\"4.0.03.1020.03\",\"kindFile\":\"apk\"}"));
        items.put(new JSONObject(
                "{\"fileID\":999,\"version\":\"4.0.03.1020.03\",\"kindFile\":\"xapk\"}"));
        return items;
    }

    @Test
    public void picksTheExactApkBuild() throws Exception {
        UptodownApi.Release release = UptodownApi.pickRelease(
                items(), "4.0.03.1020.03", "apk");
        assertEquals(1229268575L, release.fileId);
        assertEquals("apk", release.kindFile);
    }

    @Test
    public void picksTheSplitVariantWhenRequested() throws Exception {
        UptodownApi.Release release = UptodownApi.pickRelease(
                items(), "4.0.03.1020.03", "xapk");
        assertEquals(999L, release.fileId);
        assertEquals("xapk", release.kindFile);
    }

    @Test
    public void picksTheLatestApkWhenNoVersionIsRequested() throws Exception {
        UptodownApi.Release release = UptodownApi.pickRelease(items(), null, "apk");
        assertEquals(1238592379L, release.fileId);
    }

    @Test
    public void returnsNullForAMissingVersion() throws Exception {
        assertNull(UptodownApi.pickRelease(items(), "9.9.9", "apk"));
    }
}
