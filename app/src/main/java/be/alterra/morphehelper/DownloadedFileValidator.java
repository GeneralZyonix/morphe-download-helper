package be.alterra.morphehelper;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import net.dongliu.apk.parser.parser.BinaryXmlParser;
import net.dongliu.apk.parser.parser.XmlStreamer;
import net.dongliu.apk.parser.struct.xml.Attributes;
import net.dongliu.apk.parser.struct.xml.XmlCData;
import net.dongliu.apk.parser.struct.xml.XmlNamespaceEndTag;
import net.dongliu.apk.parser.struct.xml.XmlNamespaceStartTag;
import net.dongliu.apk.parser.struct.xml.XmlNodeEndTag;
import net.dongliu.apk.parser.struct.xml.XmlNodeStartTag;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

final class DownloadedFileValidator {

    static final class VersionMismatchException extends IOException {
        VersionMismatchException(String message) {
            super(message);
        }
    }

    /** The downloaded build requires a newer Android API than this device. */
    static final class IncompatibleSdkException extends IOException {
        IncompatibleSdkException(String message) {
            super(message);
        }
    }

    /** Collects the attributes of the root {@code <manifest>} element. */
    private static final class ManifestStreamer implements XmlStreamer {
        String packageName;
        String versionName;
        long versionCode = -1;
        int minSdkVersion = -1;

        @Override
        public void onStartTag(XmlNodeStartTag tag) {
            Attributes attributes = tag.getAttributes();
            if (attributes == null) {
                return;
            }
            if ("manifest".equals(tag.getName()) && packageName == null) {
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
            } else if ("uses-sdk".equals(tag.getName())) {
                String minSdk = attributes.getString("minSdkVersion");
                if (minSdk != null) {
                    try {
                        minSdkVersion = Integer.parseInt(minSdk);
                    } catch (NumberFormatException ignored) {
                    }
                }
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
        final int minSdkVersion;

        XapkManifest(String packageName, String versionName, long versionCode,
                     int minSdkVersion) {
            this.packageName = packageName;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.minSdkVersion = minSdkVersion;
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
        HelperLog.info("Validating split archive " + file.getName());
        PackageInfo wrongVersion = null;
        PackageInfo wrongPackage = null;
        XapkManifest wrongManifest = null;
        boolean sawApk = false;
        boolean parsedApk = false;
        PackageInfo verified = null;

        try (ZipFile zip = new ZipFile(file)) {
            XapkManifest manifest = readXapkManifest(zip);
            List<String> parsedEntries = new ArrayList<>();

            // Base APKs are the source of truth. Read their manifests first.
            Enumeration<? extends ZipEntry> baseEntries = zip.entries();
            while (baseEntries.hasMoreElements()) {
                ZipEntry entry = baseEntries.nextElement();
                String name = entry.getName().toLowerCase(Locale.US);
                if (!name.endsWith(".apk") || !name.contains("base")) {
                    continue;
                }
                sawApk = true;
                parsedEntries.add(entry.getName());
                PackageInfo info = readEntryManifest(context, zip, entry);
                if (info == null) {
                    info = readEntry(context, zip, entry);
                }
                if (info == null) {
                    continue;
                }
                parsedApk = true;
                if (!pkg.equals(info.packageName)) {
                    if (wrongPackage == null) {
                        wrongPackage = info;
                    }
                    continue;
                }
                if (matches(info, pkg, versionName, versionCodes)) {
                    verified = info;
                    break;
                }
                wrongVersion = info;
            }

            // Randomly named splits are common. If no base matched, inspect the
            // remaining APK entries, still preferring real APK metadata.
            if (verified == null) {
                Enumeration<? extends ZipEntry> all = zip.entries();
                while (all.hasMoreElements()) {
                    ZipEntry entry = all.nextElement();
                    String name = entry.getName().toLowerCase(Locale.US);
                    if (!name.endsWith(".apk") || parsedEntries.contains(entry.getName())) {
                        continue;
                    }
                    sawApk = true;
                    parsedEntries.add(entry.getName());
                    PackageInfo info = readEntryManifest(context, zip, entry);
                    if (info == null) {
                        info = readEntry(context, zip, entry);
                    }
                    if (info == null) {
                        continue;
                    }
                    parsedApk = true;
                    if (!pkg.equals(info.packageName)) {
                        if (wrongPackage == null) {
                            wrongPackage = info;
                        }
                        continue;
                    }
                    if (matches(info, pkg, versionName, versionCodes)) {
                        verified = info;
                        break;
                    }
                    wrongVersion = info;
                }
            }

            if (verified != null) {
                checkInfo(verified, pkg, versionName, versionCodes, "archive");
                if (manifest != null) {
                    crossCheckManifest(manifest, verified, pkg);
                }
                HelperLog.info("Validated split archive APK package="
                        + verified.packageName + " version=" + verified.versionName);
                return;
            }

            if (!sawApk) {
                throw new VersionMismatchException(
                        "Downloaded archive does not contain an APK file");
            }

            // Only fall back to manifest.json when no APK metadata could be read.
            // A readable APK always wins over the manifest.
            if (!parsedApk && manifest != null) {
                if (!pkg.equals(manifest.packageName)) {
                    throw new VersionMismatchException("Downloaded XAPK is for "
                            + manifest.packageName + " instead of " + pkg);
                }
                if (matchesVersion(manifest.versionName, manifest.versionCode,
                        versionName, versionCodes)) {
                    checkMinSdk(manifest.minSdkVersion, "XAPK");
                    HelperLog.info("Validated XAPK manifest fallback package="
                            + manifest.packageName + " version=" + manifest.versionName
                            + " code=" + manifest.versionCode
                            + " minSdk=" + manifest.minSdkVersion);
                    return;
                }
                wrongManifest = manifest;
            }
        } catch (IOException error) {
            if (error instanceof VersionMismatchException
                    || error instanceof IncompatibleSdkException) {
                throw error;
            }
            throw new IOException("Downloaded archive could not be read", error);
        }

        if (wrongPackage != null) {
            throw new VersionMismatchException("Downloaded archive contains package "
                    + wrongPackage.packageName + " instead of " + pkg);
        }
        if (wrongVersion != null) {
            String actual = wrongVersion.versionName == null
                    ? "" : wrongVersion.versionName.trim();
            long actualCode = Build.VERSION.SDK_INT >= 28
                    ? wrongVersion.getLongVersionCode() : wrongVersion.versionCode;
            throw new VersionMismatchException("Downloaded archive contains version "
                    + actual + " (" + actualCode + "), expected "
                    + expectedVersion(versionName, versionCodes));
        }
        if (wrongManifest != null) {
            throw new VersionMismatchException("Downloaded XAPK manifest contains version "
                    + wrongManifest.versionName + " (" + wrongManifest.versionCode
                    + "), expected " + expectedVersion(versionName, versionCodes));
        }
        throw new VersionMismatchException(
                "Downloaded archive does not contain the requested package " + pkg);
    }

    /** Ensures a readable APK and manifest.json cannot contradict each other. */
    private static void crossCheckManifest(XapkManifest manifest, PackageInfo info,
                                           String pkg) throws IOException {
        if (!pkg.equals(manifest.packageName)) {
            throw new VersionMismatchException("XAPK manifest package is "
                    + manifest.packageName + " but the APK package is "
                    + info.packageName + " (requested " + pkg + ")");
        }
        String apkName = info.versionName == null ? "" : info.versionName.trim();
        String manifestName = manifest.versionName == null
                ? "" : manifest.versionName.trim();
        if (!manifestName.isEmpty() && !apkName.isEmpty()
                && !sameVersion(manifestName, apkName)) {
            throw new VersionMismatchException("Downloaded XAPK manifest says version "
                    + manifestName + " but the APK is " + apkName);
        }
        long apkCode = Build.VERSION.SDK_INT >= 28
                ? info.getLongVersionCode() : info.versionCode;
        if (manifest.versionCode > 0 && apkCode > 0
                && manifest.versionCode != apkCode) {
            throw new VersionMismatchException("Downloaded XAPK manifest says build "
                    + manifest.versionCode + " but the APK is " + apkCode);
        }
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
            int minSdkVersion = parseSdkInt(root.opt("min_sdk_version"));
            return new XapkManifest(packageName, versionName, versionCode, minSdkVersion);
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

    private static int parseSdkInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    /**
     * Android's package parser refuses an archive whose {@code minSdkVersion} is newer than
     * the device, so Morphe cannot read the base APK of such a bundle. Reject it here and
     * let the caller fall back to a source that has a compatible build.
     */
    private static void checkMinSdk(int minSdkVersion, String kind)
            throws IncompatibleSdkException {
        if (minSdkVersion > 0 && minSdkVersion > Build.VERSION.SDK_INT) {
            throw new IncompatibleSdkException("Downloaded " + kind
                    + " requires Android SDK " + minSdkVersion
                    + " but this device is SDK " + Build.VERSION.SDK_INT);
        }
    }

    private static int minSdkOf(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && info.applicationInfo != null) {
            return info.applicationInfo.minSdkVersion;
        }
        return -1;
    }

    private static boolean matchesVersion(String actualName, long actualCode,
                                          String requestedName, long[] requestedCodes) {
        // A build number is exact when Morphe supplies one. The version name is
        // also checked because the same code should never legitimise an unrelated
        // build with a different displayed version.
        if (requestedCodes != null && requestedCodes.length > 0
                && !contains(requestedCodes, actualCode)) {
            return false;
        }
        if (requestedName == null || requestedName.trim().isEmpty()) {
            return true;
        }
        if (actualName == null || actualName.trim().isEmpty()) {
            return false;
        }
        return sameVersion(actualName, requestedName);
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
                    + " code=" + fallbackCode + " minSdk=" + minSdkOf(fallback)
                    + " entry=" + entry.getName());
            return fallback;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    /**
     * Streams only AndroidManifest.xml out of a nested APK. This avoids copying
     * a large base APK to the cache just to read package metadata.
     */
    private static PackageInfo readEntryManifest(Context context, ZipFile zip,
                                                 ZipEntry entry) {
        try (InputStream input = zip.getInputStream(entry);
             ZipInputStream nested = new ZipInputStream(new BufferedInputStream(input))) {
            ZipEntry child;
            while ((child = nested.getNextEntry()) != null) {
                if ("AndroidManifest.xml".equals(child.getName())) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[16 * 1024];
                    int total = 0;
                    int read;
                    while ((read = nested.read(buffer)) != -1) {
                        total += read;
                        if (total > 8 * 1024 * 1024) {
                            return null;
                        }
                        output.write(buffer, 0, read);
                    }
                    return parseManifestBytes(output.toByteArray(), entry.getName());
                }
                nested.closeEntry();
            }
        } catch (Exception error) {
            HelperLog.error("Could not read nested APK manifest: " + entry.getName(),
                    error);
        }
        return null;
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
            return parseManifestBytes(manifest, file.getName());
        } catch (Exception error) {
            HelperLog.error("Could not parse split APK metadata: " + file.getName(),
                    error);
            return null;
        }
    }

    private static PackageInfo parseManifestBytes(byte[] manifest, String source) {
        try {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ApplicationInfo applicationInfo = new ApplicationInfo();
                applicationInfo.minSdkVersion = streamer.minSdkVersion;
                info.applicationInfo = applicationInfo;
            }
            return info;
        } catch (Exception error) {
            HelperLog.error("Could not parse APK manifest from " + source, error);
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
                    + actualVersion + " (" + actualCode + "), expected "
                    + expectedVersion(versionName, versionCodes));
        }
        checkMinSdk(minSdkOf(info), kind);
        String actualVersion = info.versionName == null
                ? "" : info.versionName.trim();
        long actualCode = Build.VERSION.SDK_INT >= 28
                ? info.getLongVersionCode() : info.versionCode;
        HelperLog.info("Validated " + kind + " package=" + info.packageName
                + " version=" + actualVersion + " code=" + actualCode
                + " minSdk=" + minSdkOf(info));
    }

    private static boolean matches(PackageInfo info, String pkg, String versionName,
                                   long[] versionCodes) {
        if (info == null || !pkg.equals(info.packageName)) {
            return false;
        }
        String actualVersion = info.versionName == null ? "" : info.versionName.trim();
        long actualCode = Build.VERSION.SDK_INT >= 28
                ? info.getLongVersionCode() : info.versionCode;
        // Exact build numbers take precedence. Two builds can share the same
        // version name; accepting the name alone is what let older/wrong APKs
        // through when a patch requested a specific build. Requiring both also
        // prevents an unrelated APK with a matching numeric code from passing.
        if (versionCodes != null && versionCodes.length > 0
                && !contains(versionCodes, actualCode)) {
            return false;
        }
        return versionName == null || versionName.trim().isEmpty()
                || sameVersion(actualVersion, versionName);
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

    private static String expectedVersion(String versionName, long[] versionCodes) {
        String name = versionName == null ? "" : versionName.trim();
        if (versionCodes == null || versionCodes.length == 0) {
            return name;
        }
        StringBuilder codes = new StringBuilder();
        for (long code : versionCodes) {
            if (codes.length() > 0) {
                codes.append(", ");
            }
            codes.append(code);
        }
        return name.isEmpty() ? "build " + codes : name + " (build " + codes + ")";
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
