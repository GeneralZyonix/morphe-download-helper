package be.alterra.morphehelper;

final class UpdateInfo {

    final String version;
    final String releaseUrl;
    final String assetUrl;
    final String assetName;
    final long assetSize;
    final String notes;

    UpdateInfo(String version, String releaseUrl, String assetUrl,
               String assetName, long assetSize, String notes) {
        this.version = version;
        this.releaseUrl = releaseUrl;
        this.assetUrl = assetUrl;
        this.assetName = assetName;
        this.assetSize = assetSize;
        this.notes = notes == null ? "" : notes.trim();
    }

    boolean isNewerThan(String currentVersion) {
        return compareVersions(version, currentVersion) > 0;
    }

    static int compareVersions(String left, String right) {
        String[] leftParts = clean(left).split("\\.");
        String[] rightParts = clean(right).split("\\.");
        int count = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < count; i++) {
            int a = number(leftParts, i);
            int b = number(rightParts, i);
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    private static String clean(String value) {
        String result = value == null ? "" : value.trim().toLowerCase();
        while (result.startsWith("v")) {
            result = result.substring(1);
        }
        return result;
    }

    private static int number(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        String value = parts[index].replaceAll("[^0-9].*$", "");
        try {
            return value.isEmpty() ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
