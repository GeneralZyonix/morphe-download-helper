package be.alterra.morphehelper;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import net.dongliu.apk.parser.parser.BinaryXmlParser;
import net.dongliu.apk.parser.parser.XmlStreamer;
import net.dongliu.apk.parser.struct.xml.Attributes;
import net.dongliu.apk.parser.struct.xml.XmlCData;
import net.dongliu.apk.parser.struct.xml.XmlNamespaceEndTag;
import net.dongliu.apk.parser.struct.xml.XmlNamespaceStartTag;
import net.dongliu.apk.parser.struct.xml.XmlNodeEndTag;
import net.dongliu.apk.parser.struct.xml.XmlNodeStartTag;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
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

    /** Collects the attributes of the root {@code <manifest>} element. */
    private static final class ManifestStreamer implements XmlStreamer {
        String packageName;
        String versionName;
        long versionCode = -1;

        @Override
        public void onStartTag(XmlNodeStartTag tag) {
            if (packageName != null || !"manifest".equals(tag.getName())) {
                return;
            }
            Attributes attributes = tag.getAttributes();
            if (attributes == null) {
                return;
            }
            packageName = attributes.getString("package");
            versionName = attributes.getString("versionName");
            String code = attributes.getString("versionCode");
            if (code != null) {
                try {
                    versionCode = Long.parseLong(code);
                } catch (NumberFormatException ignored) {
                }
            }
            if (versionName != null && versionName.startsWith("resourceId:")) {
                versionName = null;
            }
        }

        @Override
        public void onEndTag(XmlNodeEndTag xmlNodeEndTag) {
        }

        @Override
        public void onCData(XmlCData xmlCData) {
        }

        @Override
        public void onNamespaceStart(XmlNamespaceStartTag tag) {
        }

        @Override
        public void onNamespaceEnd(XmlNamespaceEndTag tag) {
        }
    }

    /** Package/version metadata from an XAPK's manifest.json. */
    private static final class XapkManifest {
        final String packageName;
        final String versionName;
        final long versionCode;

        XapkManifest(String packageName, String versionName, long versionCode) {
            this.packageName = packageName;
            this.versionName = versionName;
            this.versionCode = versionCode;
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
        XapkManifest wrongManifest = null;
        try (ZipFile zip = new ZipFile(file)) {
            XapkManifest manifest = readXapkManifest(zip);
            if (manifest != null) {
                if (!pkg.equals(manifest.packageName)) {
                    throw new VersionMismatchException("Downloaded XAPK is for "
                            + manifest.packageName + " instead of " + pkg);
                }
                if (matchesVersion(manifest.versionName, manifest.versionCode,
                        versionName, versionCodes)) {
                    HelperLog.info("Validated XAPK manifest package="
                            + manifest.packageName + " version=" + manifest.versionName
                            + " code=" + manifest.versionCode);
                    return;
                }
                wrongManifest = manifest;
            }

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
                    } else {
                        HelperLog.info("Archive APK entry could not be parsed: "
                                + entry.getName());
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
                } else {
                    HelperLog.info("Archive APK entry could not be parsed: "
                            + entry.getName());
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
        if (wrongManifest != null) {
            throw new VersionMismatchException("Downloaded XAPK manifest contains version "
                    + wrongManifest.versionName + " (" + wrongManifest.versionCode
                    + "), expected " + versionName);
        }
        throw new VersionMismatchException(
                "Downloaded archive does not contain the requested package " + pkg);
    }

    /**
     * Reads package metadata from an XAPK's manifest.json. Some XAPK producers
     * use split-base APKs that Android's package parser cannot read on older
     * devices, but the manifest still identifies the package and version.
     */
    private static XapkManifest readXapkManifest(ZipFile zip) {
        ZipEntry entry = zip.getEntry("manifest.json");
        if (entry == null) {
            return null;
        }
        try {
            String text = readEntryText(zip, entry, 512 * 1024);
            if (text.isEmpty()) {
                return null;
            }
            JSONObject root = new JSONObject(text);
            String packageName = root.optString("package_name", "").trim();
            if (packageName.isEmpty()) {
                return null;
            }
            String versionName = root.optString("version_name", "").trim();
            long versionCode = parseVersionCode(root.opt("version_code"));
            return new XapkManifest(packageName, versionName, versionCode);
        } catch (Exception error) {
            HelperLog.error("Could not read XAPK manifest", error);
            return null;
        }
    }

    private static String readEntryText(ZipFile zip, ZipEntry entry, int maxBytes)
            throws IOException {
        try (InputStream input = zip.getInputStream(entry);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    break;
                }
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(),
                    java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static long parseVersionCode(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private static boolean matchesVersion(String actualName, long actualCode,
                                          String requestedName, long[] requestedCodes) {
        boolean wantsVersion = (requestedName != null && !requestedName.trim().isEmpty())
                || (requestedCodes != null && requestedCodes.length > 0);
        if (!wantsVersion) {
            return true;
        }
        if (contains(requestedCodes, actualCode)) {
            return true;
        }
        return requestedName != null && !requestedName.trim().isEmpty()
                && actualName != null && !actualName.trim().isEmpty()
                && sameVersion(actualName, requestedName);
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
            PackageInfo info = context.getPackageManager().getPackageArchiveInfo(
                    temp.getAbsolutePath(), flags);
            if (info != null || flags != 0) {
                return info;
            }
            // Android's package parser refuses split APKs that are not the
            // standalone base. Uptodown XAPKs can contain only randomly named
            // splits, so read AndroidManifest.xml directly as a fallback.
            PackageInfo fallback = readSplitMeta(temp);
            if (fallback == null) {
                return null;
            }
            long fallbackCode = Build.VERSION.SDK_INT >= 28
                    ? fallback.getLongVersionCode() : fallback.versionCode;
            HelperLog.info("Parsed split APK manifest package="
                    + fallback.packageName + " version=" + fallback.versionName
                    + " code=" + fallbackCode + " entry=" + entry.getName());
            return fallback;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private static PackageInfo readSplitMeta(File file) {
        try (ZipFile apk = new ZipFile(file)) {
            ZipEntry entry = apk.getEntry("AndroidManifest.xml");
            if (entry == null) {
                return null;
            }
            byte[] manifest;
            try (InputStream input = apk.getInputStream(entry);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                manifest = output.toByteArray();
            }
            ManifestStreamer streamer = new ManifestStreamer();
            BinaryXmlParser parser = new BinaryXmlParser(ByteBuffer.wrap(manifest), null);
            parser.setXmlStreamer(streamer);
            parser.parse();
            if (streamer.packageName == null || streamer.packageName.isEmpty()) {
                return null;
            }
            PackageInfo info = new PackageInfo();
            info.packageName = streamer.packageName;
            info.versionName = streamer.versionName;
            info.versionCode = (int) streamer.versionCode;
            if (Build.VERSION.SDK_INT >= 28) {
                info.setLongVersionCode(streamer.versionCode);
            }
            return info;
        } catch (Exception error) {
            HelperLog.error("Could not parse split APK metadata: " + file.getName(),
                    error);
            return null;
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
        return normalizeVersion(left).equalsIgnoreCase(normalizeVersion(right));
    }

    /**
     * Patch bundles often write versions as {@code v1.0.14}, while the APK
     * manifest reports {@code 1.0.14}. Treat both forms as the same version.
     */
    private static String normalizeVersion(String value) {
        String result = value.trim().replace(" ", "");
        while (result.startsWith("v") || result.startsWith("V")) {
            result = result.substring(1);
        }
        return result;
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
                    "Downloaded GitHub APK signature does not match the installed app. "
                            + "Uninstall the existing app or install the official APK first");
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
