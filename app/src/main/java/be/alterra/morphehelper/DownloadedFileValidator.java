package be.alterra.morphehelper;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

final class DownloadedFileValidator {

    private DownloadedFileValidator() {
    }

    static void validate(Context context, File file, String pkg, String versionName,
                         long[] versionCodes) throws IOException {
        if (!file.getName().toLowerCase(Locale.US).endsWith(".apk")) {
            HelperLog.info("Validation skipped for split archive " + file.getName());
            return;
        }

        PackageInfo info = context.getPackageManager().getPackageArchiveInfo(
                file.getAbsolutePath(), 0);
        if (info == null) {
            throw new IOException("Downloaded file could not be read as an APK");
        }

        if (!pkg.equals(info.packageName)) {
            throw new IOException("Downloaded APK is for " + info.packageName
                    + " instead of " + pkg);
        }

        String actualVersion = info.versionName == null ? "" : info.versionName.trim();
        long actualCode = Build.VERSION.SDK_INT >= 28
                ? info.getLongVersionCode() : info.versionCode;
        boolean versionMatches = versionName == null || versionName.trim().isEmpty()
                || sameVersion(actualVersion, versionName)
                || contains(versionCodes, actualCode);
        if (!versionMatches) {
            throw new IOException("Downloaded APK is version " + actualVersion
                    + " (" + actualCode + "), expected " + versionName);
        }

        HelperLog.info("Validated APK package=" + info.packageName
                + " version=" + actualVersion + " code=" + actualCode);
    }

    private static boolean sameVersion(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().replace(" ", "")
                .equalsIgnoreCase(right.trim().replace(" ", ""));
    }

    private static boolean contains(long[] values, long target) {
        if (values == null) {
            return false;
        }
        for (long value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }
}

