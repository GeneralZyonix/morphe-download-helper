package be.alterra.morphehelper;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
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
        return readEntry(context, zip, entry, 0);
    }

    private static PackageInfo readEntry(Context context, ZipFile zip, ZipEntry entry,
                                         int flags) throws IOException {
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
                    temp.getAbsolutePath(), flags);
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

    /**
     * Verifies that a GitHub-sourced APK or split archive is signed with the
     * same certificate as the installed app, when that app is available. This
     * prevents an unrelated repository from supplying a trojan under the right
     * package name and version.
     */
    static void verifyInstalledSignature(Context context, File file, String pkg)
            throws IOException {
        PackageManager manager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;

        PackageInfo archiveInfo = file.getName().toLowerCase(Locale.US).endsWith(".apk")
                ? manager.getPackageArchiveInfo(file.getAbsolutePath(), flags)
                : findArchiveInfo(context, file, pkg, flags);
        if (archiveInfo == null) {
            throw new IOException(
                    "Downloaded GitHub file could not be read for a signature check");
        }
        if (!pkg.equals(archiveInfo.packageName)) {
            throw new VersionMismatchException("Downloaded GitHub file is for "
                    + archiveInfo.packageName + " instead of " + pkg);
        }

        PackageInfo installed;
        try {
            installed = manager.getPackageInfo(pkg, flags);
        } catch (PackageManager.NameNotFoundException notInstalled) {
            HelperLog.info("Installed app not found; skipping GitHub signature check for "
                    + pkg);
            return;
        }

        Set<String> installedHashes = signatureHashes(installed, true);
        if (installedHashes.isEmpty()) {
            HelperLog.info("Installed app signature unavailable; skipping GitHub "
                    + "signature check for " + pkg);
            return;
        }
        Set<String> archiveHashes = signatureHashes(archiveInfo, false);
        if (archiveHashes.isEmpty()) {
            throw new IOException("Downloaded GitHub APK is not signed");
        }
        if (!installedHashes.containsAll(archiveHashes)) {
            HelperLog.error("GitHub signature mismatch for " + pkg
                    + " archive=" + archiveHashes + " installed=" + installedHashes, null);
            throw new IOException(
                    "Downloaded GitHub APK signature does not match the installed app");
        }
        HelperLog.info("GitHub signature matches installed app for " + pkg);
    }

    private static PackageInfo findArchiveInfo(Context context, File file, String pkg,
                                               int flags) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            PackageInfo fallback = null;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().toLowerCase(Locale.US);
                if (!name.endsWith(".apk")) {
                    continue;
                }
                PackageInfo info = readEntry(context, zip, entry, flags);
                if (info == null || !pkg.equals(info.packageName)) {
                    continue;
                }
                if (name.contains("base")) {
                    return info;
                }
                if (fallback == null) {
                    fallback = info;
                }
            }
            return fallback;
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException(
                    "Downloaded archive could not be read for a signature check", error);
        }
    }

    private static Set<String> signatureHashes(PackageInfo info, boolean includeHistory) {
        Set<String> hashes = new HashSet<>();
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            SigningInfo signing = info.signingInfo;
            Signature[] current = signing.getApkContentsSigners();
            if (current == null || current.length == 0) {
                current = info.signatures;
            }
            addSignatureHashes(hashes, current);
            if (includeHistory) {
                addSignatureHashes(hashes, signing.getSigningCertificateHistory());
            }
        } else {
            addSignatureHashes(hashes, info.signatures);
        }
        return hashes;
    }

    private static void addSignatureHashes(Set<String> hashes, Signature[] signatures) {
        if (signatures == null) {
            return;
        }
        for (Signature signature : signatures) {
            if (signature == null) {
                continue;
            }
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(signature.toByteArray());
                StringBuilder value = new StringBuilder(hash.length * 2);
                for (byte b : hash) {
                    value.append(String.format(Locale.US, "%02x", b & 0xff));
                }
                hashes.add(value.toString());
            } catch (Exception ignored) {
                // A malformed certificate cannot match; leave it out.
            }
        }
    }
}
