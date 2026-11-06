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

    /** True for versions such as {@code 1.6.15-beta.1}. */
    static boolean isPrerelease(String version) {
        return clean(version).contains("-");
    }

    static int compareVersions(String left, String right) {
        String[] leftParts = clean(left).split("-", 2);
        String[] rightParts = clean(right).split("-", 2);
        int numeric = compareNumbers(leftParts[0], rightParts[0]);
        if (numeric != 0) {
            return numeric;
        }
        boolean leftPre = leftParts.length > 1 && !leftParts[1].isEmpty();
        boolean rightPre = rightParts.length > 1 && !rightParts[1].isEmpty();
        if (leftPre != rightPre) {
            return leftPre ? -1 : 1;
        }
        if (!leftPre) {
            return 0;
        }
        return comparePrerelease(leftParts[1], rightParts[1]);
    }

    private static int compareNumbers(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
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

    private static int comparePrerelease(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int count = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < count; i++) {
            String a = i < leftParts.length ? leftParts[i] : "";
            String b = i < rightParts.length ? rightParts[i] : "";
            int numericA = number(leftParts, i);
            int numericB = number(rightParts, i);
            boolean aNumeric = !a.isEmpty() && a.matches("\\d+");
            boolean bNumeric = !b.isEmpty() && b.matches("\\d+");
            if (aNumeric && bNumeric) {
                int result = Integer.compare(numericA, numericB);
                if (result != 0) {
                    return result;
                }
            } else {
                int result = a.compareTo(b);
                if (result != 0) {
                    return result;
                }
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
