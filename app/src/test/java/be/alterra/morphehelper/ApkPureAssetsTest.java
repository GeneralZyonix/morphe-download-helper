package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** JVM tests for APKPure's version-list asset selection. */
public class ApkPureAssetsTest {

    private static JSONObject item(String version, long code, String type) throws Exception {
        JSONObject asset = new JSONObject()
                .put("url", "https://example.test/" + type.toLowerCase() + "/" + code)
                .put("type", type)
                .put("size", 100);
        return new JSONObject()
                .put("version_name", version)
                .put("version_code", String.valueOf(code))
                .put("asset", asset);
    }

    private static JSONArray instagram() throws Exception {
        JSONArray list = new JSONArray();
        // APKPure lists a newer build first, so the requested old build is not
        // in the API's latest-20 window; this mirrors the real failure.
        list.put(item("455.0.0.51.72", 386113969L, "XAPK"));
        list.put(item("452.1.0.53.64", 385811006L, "APK"));
        list.put(item("452.1.0.53.64", 385811006L, "XAPK"));
        return list;
    }

    @Test
    public void splitRequestPicksTheXapkNotTheBaseApk() throws Exception {
        ApkPureAssets.Asset asset = ApkPureAssets.pick(
                instagram(), "452.1.0.53.64", new long[]{385811006L}, true);
        assertEquals("XAPK", asset.type);
        assertTrue(asset.url.endsWith("/xapk/385811006"));
    }

    @Test
    public void splitRequestRejectsAVersionWithOnlyABaseApk() throws Exception {
        JSONArray list = new JSONArray();
        list.put(item("452.1.0.53.64", 385811006L, "APK"));
        assertNull(ApkPureAssets.pick(
                list, "452.1.0.53.64", new long[]{385811006L}, true));
    }

    @Test
    public void apkRequestPrefersTheStandaloneAsset() throws Exception {
        ApkPureAssets.Asset asset = ApkPureAssets.pick(
                instagram(), "452.1.0.53.64", new long[]{385811006L}, false);
        assertEquals("APK", asset.type);
        assertTrue(asset.url.endsWith("/apk/385811006"));
    }

    @Test
    public void apkRequestFallsBackToASplitAssetWhenThatIsAll() throws Exception {
        JSONArray list = new JSONArray();
        list.put(item("452.1.0.53.64", 385811006L, "XAPK"));
        ApkPureAssets.Asset asset = ApkPureAssets.pick(
                list, "452.1.0.53.64", new long[]{385811006L}, false);
        assertEquals("XAPK", asset.type);
    }

    @Test
    public void codeMustMatchWhenCodesAreSupplied() throws Exception {
        assertNull(ApkPureAssets.pick(
                instagram(), "452.1.0.53.64", new long[]{999L}, true));
        assertNull(ApkPureAssets.pick(
                instagram(), "452.1.0.53.64", new long[]{999L}, false));
    }

    @Test
    public void missingVersionStillMatchesByCode() throws Exception {
        ApkPureAssets.Asset asset = ApkPureAssets.pick(
                instagram(), "", new long[]{385811006L}, true);
        assertEquals("XAPK", asset.type);
    }
}
