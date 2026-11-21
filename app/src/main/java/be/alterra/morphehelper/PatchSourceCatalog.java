package be.alterra.morphehelper;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * App-first view of the Awesome Morphe patch catalog.
 *
 * <p>The full upstream catalog is large. The compact index on GitHub keeps only
 * source metadata and the source-to-app relation. The app caches the last good
 * copy and revalidates it with an ETag when the Patches tab opens.</p>
 */
final class PatchSourceCatalog {

    static final String INDEX_URL =
            "https://raw.githubusercontent.com/GeneralZyonix/morphe-download-helper/"
                    + "main/docs/data/patch-sources.json";
    private static final long MAX_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L;
    private static final String CACHE_NAME = "patch-sources.json";
    private static final String ETAG_NAME = "patch-sources.etag";
    private static final String USER_AGENT = "Morphe-Download-Helper/3.2";
    private static final Map<String, String> SEARCH_ALIASES = new LinkedHashMap<>();

    static {
        SEARCH_ALIASES.put("yt", "youtube");
        SEARCH_ALIASES.put("ytm", "youtubemusic");
        SEARCH_ALIASES.put("ytmusic", "youtubemusic");
        SEARCH_ALIASES.put("ig", "instagram");
        SEARCH_ALIASES.put("wa", "whatsapp");
        SEARCH_ALIASES.put("fb", "facebook");
        SEARCH_ALIASES.put("tt", "tiktok");
        SEARCH_ALIASES.put("tg", "telegram");
        SEARCH_ALIASES.put("gm", "gmail");
    }

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();
    private static volatile PatchSourceCatalog testOverride;

    interface Callback {
        void onCatalog(PatchSourceCatalog catalog, boolean checkedNetwork);

        void onError(String message, PatchSourceCatalog cached);
    }

    static final class Source {
        final String provider;
        final String repo;
        final String name;
        final String description;
        final String avatar;
        final long updatedAt;
        final int stars;
        final boolean preRelease;
        final boolean archived;
        final boolean official;

        Source(String provider, String repo, String name, String description,
               String avatar, long updatedAt, int stars, boolean preRelease,
               boolean archived, boolean official) {
            this.provider = provider;
            this.repo = repo;
            this.name = name;
            this.description = description;
            this.avatar = avatar;
            this.updatedAt = updatedAt;
            this.stars = stars;
            this.preRelease = preRelease;
            this.archived = archived;
            this.official = official;
        }

        String owner() {
            int slash = repo.indexOf('/');
            return slash > 0 ? repo.substring(0, slash) : repo;
        }
    }

    static final class SourceEntry {
        final Source source;
        final long updatedAt;
        final int patchCount;

        SourceEntry(Source source, long updatedAt, int patchCount) {
            this.source = source;
            this.updatedAt = updatedAt;
            this.patchCount = patchCount;
        }
    }

    static final class App {
        final String packageName;
        final String name;
        final String iconUrl;
        final long installs;
        final List<SourceEntry> sources;

        App(String packageName, String name, String iconUrl, long installs,
            List<SourceEntry> sources) {
            this.packageName = packageName;
            this.name = name;
            this.iconUrl = iconUrl;
            this.installs = installs;
            this.sources = sources;
        }

        long latestUpdate() {
            long latest = 0;
            for (SourceEntry entry : sources) {
                latest = Math.max(latest, entry.updatedAt);
            }
            return latest;
        }

        int totalPatches() {
            int total = 0;
            for (SourceEntry entry : sources) {
                total += entry.patchCount;
            }
            return total;
        }

        SourceEntry findSource(String repo) {
            for (SourceEntry entry : sources) {
                if (entry.source.repo.equals(repo)) {
                    return entry;
                }
            }
            return null;
        }
    }

    final long generatedAt;
    final String generatedAtText;
    final String dataHash;
    final List<Source> sources;
    final Map<String, App> apps;

    private PatchSourceCatalog(long generatedAt, String generatedAtText,
                               String dataHash, List<Source> sources,
                               Map<String, App> apps) {
        this.generatedAt = generatedAt;
        this.generatedAtText = generatedAtText;
        this.dataHash = dataHash;
        this.sources = sources;
        this.apps = apps;
    }

    static PatchSourceCatalog parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        String generatedAtText = root.optString("generatedAt", "");
        String dataHash = root.optString("dataHash", "");
        long generatedAt = parseDate(generatedAtText);

        JSONArray sourceArray = root.getJSONArray("sources");
        List<Source> sources = new ArrayList<>(sourceArray.length());
        for (int i = 0; i < sourceArray.length(); i++) {
            JSONObject item = sourceArray.getJSONObject(i);
            sources.add(new Source(
                    item.optString("source", "github"),
                    item.optString("repo", ""),
                    item.optString("name", ""),
                    item.optString("description", ""),
                    item.optString("avatar", ""),
                    item.optLong("updatedAt", 0L),
                    item.optInt("stars", 0),
                    item.optBoolean("isPreRelease", false),
                    item.optBoolean("isArchived", false),
                    item.optBoolean("isOfficial", false)
            ));
        }

        JSONObject appObject = root.getJSONObject("apps");
        Map<String, App> apps = new LinkedHashMap<>();
        Iterator<String> keys = appObject.keys();
        while (keys.hasNext()) {
            String packageName = keys.next();
            JSONObject item = appObject.getJSONObject(packageName);
            JSONArray sourceItems = item.optJSONArray("sources");
            List<SourceEntry> entries = new ArrayList<>();
            if (sourceItems != null) {
                for (int i = 0; i < sourceItems.length(); i++) {
                    JSONArray values = sourceItems.optJSONArray(i);
                    if (values == null || values.length() < 3) {
                        continue;
                    }
                    int sourceId = values.optInt(0, -1);
                    if (sourceId < 0 || sourceId >= sources.size()) {
                        continue;
                    }
                    entries.add(new SourceEntry(
                            sources.get(sourceId),
                            values.optLong(1, 0L),
                            values.optInt(2, 0)
                    ));
                }
            }
            entries.sort(Comparator
                    .comparing((SourceEntry entry) -> !entry.source.official)
                    .thenComparingLong(entry -> -entry.updatedAt)
                    .thenComparing(entry -> entry.source.name,
                            String.CASE_INSENSITIVE_ORDER));
            if (!entries.isEmpty()) {
                apps.put(packageName, new App(
                        packageName,
                        item.optString("name", packageName),
                        item.optString("icon", ""),
                        item.optLong("installs", 0L),
                        entries
                ));
            }
        }
        return new PatchSourceCatalog(generatedAt, generatedAtText, dataHash,
                Collections.unmodifiableList(sources),
                Collections.unmodifiableMap(apps));
    }

    boolean isStale() {
        return generatedAt <= 0
                || System.currentTimeMillis() - generatedAt > MAX_AGE_MILLIS;
    }

    App findApp(String packageName) {
        return apps.get(packageName);
    }

    List<App> search(String query, int limit) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        List<ScoredApp> matches = new ArrayList<>();
        String matchTerm = SEARCH_ALIASES.containsKey(normalized)
                ? SEARCH_ALIASES.get(normalized) : normalized;
        for (App app : apps.values()) {
            String name = app.name.toLowerCase(Locale.ROOT);
            String normalizedName = normalize(app.name);
            String packageName = app.packageName.toLowerCase(Locale.ROOT);
            String normalizedPackage = normalize(app.packageName);
            int score = -1;
            if (name.equals(matchTerm) || normalizedName.equals(matchTerm)
                    || packageName.equals(matchTerm)
                    || normalizedPackage.equals(matchTerm)) {
                score = 0;
            } else if (name.startsWith(matchTerm)
                    || normalizedName.startsWith(matchTerm)) {
                score = 1;
            } else if (packageName.startsWith(matchTerm)
                    || normalizedPackage.startsWith(matchTerm)) {
                score = 2;
            } else if (name.contains(matchTerm)
                    || normalizedName.contains(matchTerm)) {
                score = 3;
            } else if (packageName.contains(matchTerm)
                    || normalizedPackage.contains(matchTerm)) {
                score = 4;
            } else if (normalized.length() >= 2
                    && normalizedName.startsWith(normalized.substring(0, 1))
                    && isSubsequence(normalized, normalizedName)) {
                score = 5;
            }
            if (score >= 0) {
                matches.add(new ScoredApp(app, score));
            }
        }
        matches.sort(Comparator
                .comparingInt((ScoredApp match) -> match.score)
                .thenComparing((ScoredApp match) -> -match.app.installs)
                .thenComparing(match -> match.app.name,
                        String.CASE_INSENSITIVE_ORDER));
        List<App> result = new ArrayList<>();
        int max = Math.max(1, limit);
        for (int i = 0; i < matches.size() && i < max; i++) {
            result.add(matches.get(i).app);
        }
        return result;
    }

    List<App> popular(int limit) {
        List<App> result = new ArrayList<>(apps.values());
        result.sort(Comparator
                .comparingLong((App app) -> -app.installs)
                .thenComparingInt(app -> -app.sources.size())
                .thenComparing(app -> app.name, String.CASE_INSENSITIVE_ORDER));
        if (result.size() > limit) {
            return new ArrayList<>(result.subList(0, Math.max(1, limit)));
        }
        return result;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                normalized.append(c);
            }
        }
        return normalized.toString();
    }

    private static boolean isSubsequence(String needle, String haystack) {
        int index = 0;
        for (int i = 0; i < haystack.length() && index < needle.length(); i++) {
            if (haystack.charAt(i) == needle.charAt(index)) {
                index++;
            }
        }
        return index == needle.length();
    }

    static void load(Context context, Callback callback) {
        final Context appContext = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        PatchSourceCatalog override = testOverride;
        if (override != null) {
            main.post(() -> callback.onCatalog(override, true));
            return;
        }
        new Thread(() -> {
            File cache = cacheFile(appContext);
            PatchSourceCatalog cached = null;
            if (cache.exists()) {
                try {
                    cached = parse(readText(cache));
                } catch (Exception ignored) {
                    // A corrupt cache is replaced by the network response.
                }
            }

            if (cached != null) {
                PatchSourceCatalog snapshot = cached;
                main.post(() -> callback.onCatalog(snapshot, false));
            }

            Request.Builder request = new Request.Builder()
                    .url(INDEX_URL)
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-cache")
                    .header("User-Agent", USER_AGENT);
            String etag = readTextOrEmpty(etagFile(appContext));
            if (!isEmpty(etag)) {
                request.header("If-None-Match", etag);
            }

            try (Response response = HTTP.newCall(request.build()).execute()) {
                if (response.code() == 304 && cached != null) {
                    PatchSourceCatalog snapshot = cached;
                    main.post(() -> callback.onCatalog(snapshot, true));
                    return;
                }
                if (!response.isSuccessful()) {
                    throw new IOException("HTTP " + response.code());
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Empty response");
                }
                String json = body.string();
                PatchSourceCatalog fresh = parse(json);
                writeText(cache, json);
                String newEtag = response.header("ETag");
                if (!isEmpty(newEtag)) {
                    writeText(etagFile(appContext), newEtag);
                }
                main.post(() -> callback.onCatalog(fresh, true));
            } catch (Exception error) {
                HelperLog.error("Patch source catalog failed", error);
                if (cached != null) {
                    PatchSourceCatalog snapshot = cached;
                    main.post(() -> callback.onError(
                            "Could not check for catalog updates.", snapshot));
                } else {
                    main.post(() -> callback.onError(
                            "Could not load the patch catalog.", null));
                }
            }
        }, "patch-source-catalog").start();
    }

    static String addSourceLink(Source source, App app) {
        String provider = "gitlab".equals(source.provider) ? "gitlab" : "github";
        StringBuilder url = new StringBuilder("https://morphe.software/add-source?")
                .append(provider).append('=').append(encode(source.repo));
        if (!isEmpty(source.name)) {
            url.append("&name=").append(encode(source.name));
        }
        if (app != null && !isEmpty(app.packageName)) {
            url.append("&app=").append(encode(app.packageName));
            if (!isEmpty(app.name)) {
                url.append("&appName=").append(encode(app.name));
            }
        }
        return url.toString();
    }

    static String formatDate(long timestamp) {
        if (timestamp <= 0) {
            return "Unknown";
        }
        DateFormat format = DateFormat.getDateInstance(
                DateFormat.MEDIUM, Locale.getDefault());
        return format.format(new Date(timestamp));
    }

    static String formatDate(Context context, long timestamp) {
        if (timestamp <= 0) {
            return "Unknown";
        }
        DateFormat format = DateFormat.getDateInstance(
                DateFormat.MEDIUM, appLocale(context));
        return format.format(new Date(timestamp));
    }

    static String formatRelativeDate(Context context, long timestamp) {
        if (timestamp <= 0) {
            return "Unknown";
        }
        long delta = Math.max(0L, System.currentTimeMillis() - timestamp);
        if (delta < 60_000L) {
            return context.getString(R.string.patches_just_now);
        }
        long minutes = delta / 60_000L;
        if (minutes < 60L) {
            return context.getResources().getQuantityString(
                    R.plurals.patches_minutes_ago, (int) minutes, minutes);
        }
        long hours = delta / 3_600_000L;
        if (hours < 24L) {
            return context.getResources().getQuantityString(
                    R.plurals.patches_hours_ago, (int) hours, hours);
        }
        long days = delta / 86_400_000L;
        if (days < 7L) {
            return context.getResources().getQuantityString(
                    R.plurals.patches_days_ago, (int) days, days);
        }
        return formatDate(context, timestamp);
    }

    static String formatCount(Context context, long value) {
        Locale locale = appLocale(context);
        if (value >= 1_000_000_000L) {
            return String.format(locale, "%.1fB", value / 1_000_000_000d);
        }
        if (value >= 1_000_000L) {
            return String.format(locale, "%.1fM", value / 1_000_000d);
        }
        if (value >= 1_000L) {
            return String.format(locale, "%.1fK", value / 1_000d);
        }
        return Long.toString(value);
    }

    private static Locale appLocale(Context context) {
        return context.getResources().getConfiguration()
                .getLocales().get(0);
    }

    static void setTestOverride(PatchSourceCatalog catalog) {
        testOverride = catalog;
    }

    static void clearTestOverride() {
        testOverride = null;
    }

    private static boolean isEmpty(CharSequence value) {
        return value == null || value.length() == 0;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception ignored) {
            return value;
        }
    }

    private static long parseDate(String value) {
        if (isEmpty(value)) {
            return 0L;
        }
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ssXXX"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = format.parse(value);
                if (date != null) {
                    return date.getTime();
                }
            } catch (ParseException ignored) {
                // Try the next format.
            }
        }
        return 0L;
    }

    private static File cacheFile(Context context) {
        return new File(context.getCacheDir(), CACHE_NAME);
    }

    private static File etagFile(Context context) {
        return new File(context.getCacheDir(), ETAG_NAME);
    }

    private static String readTextOrEmpty(File file) {
        try {
            return readText(file);
        } catch (IOException ignored) {
            return "";
        }
    }

    private static String readText(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void writeText(File file, String value) throws IOException {
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
        if (!temporary.renameTo(file)) {
            temporary.delete();
            throw new IOException("Could not replace " + file.getName());
        }
    }

    private static final class ScoredApp {
        final App app;
        final int score;

        ScoredApp(App app, int score) {
            this.app = app;
            this.score = score;
        }
    }
}

