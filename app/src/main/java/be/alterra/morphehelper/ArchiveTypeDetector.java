package be.alterra.morphehelper;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class ArchiveTypeDetector {

    private ArchiveTypeDetector() {
    }

    static File normalizeExtension(File file) {
        String detected = detect(file);
        if (detected == null) {
            return file;
        }
        String name = file.getName();
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith("." + detected)) {
            return file;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        File target = new File(file.getParentFile(), base + "." + detected);
        if (target.exists() && !target.delete()) {
            return file;
        }
        if (file.renameTo(target)) {
            HelperLog.info("Detected " + detected.toUpperCase(Locale.US)
                    + " archive; renamed " + name + " to " + target.getName());
            return target;
        }
        return file;
    }

    static String detect(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        if (!name.endsWith(".apk") && !name.endsWith(".apks")
                && !name.endsWith(".xapk") && !name.endsWith(".apkm")) {
            return null;
        }
        try (ZipFile zip = new ZipFile(file)) {
            boolean rootManifest = zip.getEntry("AndroidManifest.xml") != null;
            boolean toc = zip.getEntry("toc.pb") != null;
            boolean saiMeta = zip.getEntry("meta.sai_v2.json") != null
                    || zip.getEntry("info.json") != null;
            boolean xapkManifest = zip.getEntry("manifest.json") != null;
            boolean hasApkEntry = false;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String entry = entries.nextElement().getName().toLowerCase(Locale.US);
                if (entry.endsWith(".apk")) {
                    hasApkEntry = true;
                    break;
                }
            }
            String detected = null;
            if (toc) {
                detected = "apks";
            } else if (saiMeta) {
                detected = "apkm";
            } else if (xapkManifest && hasApkEntry) {
                detected = "xapk";
            } else if (rootManifest) {
                detected = "apk";
            } else if (hasApkEntry) {
                detected = "apks";
            }
            if (detected != null) {
                HelperLog.info("Archive check: " + detected.toUpperCase(Locale.US)
                        + " archive");
            }
            return detected;
        } catch (IOException ignored) {
            return null;
        }
    }
}
