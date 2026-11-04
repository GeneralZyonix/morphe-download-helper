package be.alterra.morphehelper;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class DownloadedFileValidator {

    static final class VersionMismatchException extends IOException {
        VersionMismatchException(String message) {
            super(message);
        }
    }

    private DownloadedFileValidator() {
    }

    static void validate(Context context, File file, String pkg, String versionName,
                         long[] versionCodes) throws IOException {
        if (file.getName().toLowerCase(Locale.US).endsWith(".apk")) {
            validateApk(context, file, pkg, versionName, versionCodes);
            return;
        }
        validateArchive(context, file, pkg, versionName, versionCodes);
    }

    private static void validateApk(Context context, File file, String pkg,
                                    String versionName, long[] versionCodes)
            throws IOException {
        PackageInfo info = context.getPackageManager().getPackageArchiveInfo(
                file.getAbsolutePath(), 0);
        if (info == null) {
            throw new IOException("Downloaded file could not be read as an APK");
        }
        checkInfo(info, pkg, versionName, versionCodes, "APK");
    }

    private static void validateArchive(Context context, File file, String pkg,
                                        String versionName, long[] versionCodes)
            throws IOException {
        if ((versionName == null || versionName.trim().isEmpty())
                && (versionCodes == null || versionCodes.length == 0)) {
            HelperLog.info("Validation skipped for unversioned split archive "
                    + file.getName());
            return;
        }

        HelperLog.info("Validating split archive " + file.getName());
        PackageInfo wrongVersion = null;
        try (ZipFile zip = new ZipFile(file)) {
            // Base APKs are usually named base*.apk. Check those first so we do not
            // extract every split in a large bundle unnecessarily.
            Enumeration<? extends ZipEntry> first = zip.entries();
            while (first.hasMoreElements()) {
                ZipEntry entry = first.nextElement();
                String name = entry.getName().toLowerCase(Locale.US);
                if (name.endsWith(".apk") && name.contains("base")) {
                    PackageInfo info = readEntry(context, zip, entry);
                    if (info != null) {
                        if (matches(info, pkg, versionName, versionCodes)) {
                            checkInfo(info, pkg, versionName, versionCodes, "archive");
                            return;
                        }
                        if (pkg.equals(info.packageName)) {
                            wrongVersion = info;
                        }
                    }
                }
            }

            Enumeration<? extends ZipEntry> all = zip.entries();
            while (all.hasMoreElements()) {
                ZipEntry entry = all.nextElement();
                String name = entry.getName().toLowerCase(Locale.US);
                if (!name.endsWith(".apk") || name.contains("base")) {
                    continue;
                }
                PackageInfo info = readEntry(context, zip, entry);
                if (info != null) {
                    if (matches(info, pkg, versionName, versionCodes)) {
                        checkInfo(info, pkg, versionName, versionCodes, "archive");
                        return;
                    }
                    if (pkg.equals(info.packageName)) {
                        wrongVersion = info;
                    }
                }
            }
        } catch (IOException error) {
            if (error instanceof VersionMismatchException) {
                throw error;
            }
            throw new IOException("Downloaded archive could not be read", error);
        }

        if (wrongVersion != null) {
            String actual = wrongVersion.versionName == null
                    ? "" : wrongVersion.versionName.trim();
            long actualCode = Build.VERSION.SDK_INT >= 28
                    ? wrongVersion.getLongVersionCode() : wrongVersion.versionCode;
            throw new VersionMismatchException("Downloaded archive contains version "
                    + actual + " (" + actualCode + "), expected " + versionName);
        }
        throw new VersionMismatchException(
                "Downloaded archive does not contain the requested package " + pkg);
    }

    private static PackageInfo readEntry(Context context, ZipFile zip, ZipEntry entry)
            throws IOException {
        File temp = File.createTempFile("morphe-validate-", ".apk",
                context.getCacheDir());
        try {
            try (InputStream input = zip.getInputStream(entry);
                 FileOutputStream output = new FileOutputStream(temp)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            }
            return context.getPackageManager().getPackageArchiveInfo(
                    temp.getAbsolutePath(), 0);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private static void checkInfo(PackageInfo info, String pkg, String versionName,
                                  long[] versionCodes, String kind) throws IOException {
        if (!pkg.equals(info.packageName)) {
            throw new VersionMismatchException("Downloaded " + kind + " is for "
                    + info.packageName + " instead of " + pkg);
        }
        if (!matches(info, pkg, versionName, versionCodes)) {
            String actualVersion = info.versionName == null
                    ? "" : info.versionName.trim();
            long actualCode = Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode;
            throw new VersionMismatchException("Downloaded " + kind + " is version "
                    + actualVersion + " (" + actualCode + "), expected " + versionName);
        }
        String actualVersion = info.versionName == null
                ? "" : info.versionName.trim();
        long actualCode = Build.VERSION.SDK_INT >= 28
                ? info.getLongVersionCode() : info.versionCode;
        HelperLog.info("Validated " + kind + " package=" + info.packageName
                + " version=" + actualVersion + " code=" + actualCode);
    }

    private static boolean matches(PackageInfo info, String pkg, String versionName,
                                   long[] versionCodes) {
        if (info == null || !pkg.equals(info.packageName)) {
            return false;
        }
        String actualVersion = info.versionName == null ? "" : info.versionName.trim();
        long actualCode = Build.VERSION.SDK_INT >= 28
                ? info.getLongVersionCode() : info.versionCode;
        return versionName == null || versionName.trim().isEmpty()
                || sameVersion(actualVersion, versionName)
                || contains(versionCodes, actualCode);
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
