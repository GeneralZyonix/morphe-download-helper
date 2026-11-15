package be.alterra.morphehelper;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Pure selection helpers for APKPure's mobile version API.
 *
 * <p>APKPure lists both standalone APK and split XAPK assets. For apps such as
 * Instagram the standalone asset is only {@code base.apk}, which cannot be
 * installed on its own, so split requests must never accept it.</p>
 */
final class ApkPureAssets {

    static final class Asset {
        final String url;
        final String type;
        final String versionName;
        final long versionCode;
        final long size;

        Asset(String url, String type, String versionName, long versionCode, long size) {
            this.url = url;
            this.type = type;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.size = size;
        }

        boolean split() {
            return "XAPK".equalsIgnoreCase(type) || "APKS".equalsIgnoreCase(type);
        }
    }

    private ApkPureAssets() {
    }

    /**
     * Returns the best matching asset, or null when the requested build is not
     * listed. Split requests only match split assets; plain APK requests prefer
     * a standalone APK and fall back to a split asset when that is all APKPure
     * has.
     */
    static Asset pick(JSONArray list, String requestedName, long[] requestedCodes,
                      boolean splitRequested) {
        if (list == null) {
            return null;
        }
        Asset splitFallback = null;
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String version = item.optString("version_name", "").trim();
            long code = parseCode(item.optString("version_code", ""));
            if (!matches(version, code, requestedName, requestedCodes)) {
                continue;
            }
            JSONObject asset = item.optJSONObject("asset");
            if (asset == null || asset.optString("url", "").isEmpty()) {
                continue;
            }
            Asset candidate = new Asset(
                    asset.optString("url"),
                    asset.optString("type", "APK"),
                    version,
                    code,
                    asset.optLong("size", 0));
            if (splitRequested) {
                if (candidate.split()) {
                    return candidate;
                }
                continue;
            }
            if (!candidate.split()) {
                return candidate;
            }
            if (splitFallback == null) {
                splitFallback = candidate;
            }
        }
        return splitRequested ? null : splitFallback;
    }

    private static boolean matches(String actualName, long actualCode,
                                   String requestedName, long[] requestedCodes) {
        if (requestedCodes != null && requestedCodes.length > 0
                && !contains(requestedCodes, actualCode)) {
            return false;
        }
        return requestedName == null || requestedName.isEmpty()
                || actualName.isEmpty()
                || normalize(actualName).equalsIgnoreCase(normalize(requestedName));
    }

    private static boolean contains(long[] values, long target) {
        for (long value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }

    private static long parseCode(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static String normalize(String value) {
        String result = value.trim().replace(" ", "");
        while (result.startsWith("v") || result.startsWith("V")) {
            result = result.substring(1);
        }
        return result;
    }
}
