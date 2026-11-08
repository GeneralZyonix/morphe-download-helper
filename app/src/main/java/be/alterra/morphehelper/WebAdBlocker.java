package be.alterra.morphehelper;

import android.content.Context;
import android.webkit.WebResourceResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Lightweight blocker for the in-app source WebViews.
 *
 * <p>This is deliberately not a DNS/VPN solution. Android WebView uses Chromium's
 * own resolver, so an OkHttp DNS hook cannot filter these pages. Blocking the
 * small set of high-confidence ad/tracker hosts through
 * {@code shouldInterceptRequest} gives most of the benefit without a VPN
 * permission, a persistent notification, or any extra network round trips.</p>
 */
final class WebAdBlocker {

    private static final String ASSET = "ad_hosts.txt";
    private static final String DOH_ENDPOINT = "https://dns.adguard-dns.com/resolve";
    private static final String DOH_FALLBACK = "https://dns.google/resolve";
    private static final long LIVE_BLOCK_TTL_MS = 30L * 60L * 1000L;
    private static final long LIVE_ALLOW_TTL_MS = 10L * 60L * 1000L;
    private static final long LIVE_DISABLED_MS = 60L * 1000L;
    private static final int LIVE_CACHE_LIMIT = 512;

    private static final OkHttpClient DOH_HTTP = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .build();

    /**
     * Hosts that must never be blocked even if a future block-list entry would
     * otherwise match them. These are the verification and download paths the
     * helper depends on.
     */
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
            "uptodown.com",
            "uptodown.net",
            "apkmirror.com",
            "apkpure.com",
            "winudf.com",
            "morphe.software",
            "github.com",
            "githubusercontent.com",
            "cloudflarestorage.com",
            "challenges.cloudflare.com",
            "www.google.com",
            "www.gstatic.com",
            "recaptcha.net",
            "www.recaptcha.net",
            "hcaptcha.com",
            "www.hcaptcha.com"
    ));

    /** Parsed once; the asset is tiny but should not be read per request. */
    private static volatile Set<String> blockedHosts;

    /** Live AdGuard answers, cached so a page does not repeat the same lookup. */
    private static final Map<String, LiveEntry> liveCache = new ConcurrentHashMap<>();
    private static final Map<String, Object> liveLocks = new ConcurrentHashMap<>();
    private static volatile long liveDisabledUntil;

    private static final class LiveEntry {
        final boolean blocked;
        final long expiresAt;

        LiveEntry(boolean blocked, long expiresAt) {
            this.blocked = blocked;
            this.expiresAt = expiresAt;
        }
    }

    private WebAdBlocker() {
    }

    /** Returns true when [host] or one of its parent domains is on the list. */
    static boolean shouldBlock(Context context, String host) {
        String normalized = normalize(host);
        if (normalized.isEmpty() || matches(ALLOWED, normalized)) {
            return false;
        }
        return matches(hosts(context), normalized);
    }

    /**
     * Asks AdGuard's public DoH endpoint whether [host] is on its blocklist.
     * The result is cached, and failures fail open so a page still loads.
     */
    static boolean shouldBlockLive(Context context, String host) {
        String normalized = normalize(host);
        if (normalized.isEmpty() || matches(ALLOWED, normalized)) {
            return false;
        }
        if (matches(hosts(context), normalized)) {
            return true;
        }

        long now = System.currentTimeMillis();
        LiveEntry cached = liveCache.get(normalized);
        if (cached != null && cached.expiresAt > now) {
            return cached.blocked;
        }
        if (now < liveDisabledUntil) {
            return false;
        }

        Object lock = liveLocks.computeIfAbsent(normalized, key -> new Object());
        try {
            synchronized (lock) {
                now = System.currentTimeMillis();
                cached = liveCache.get(normalized);
                if (cached != null && cached.expiresAt > now) {
                    return cached.blocked;
                }
                if (now < liveDisabledUntil) {
                    return false;
                }
                boolean blocked = queryAdGuard(normalized);
                HelperLog.info("Live AdGuard check " + normalized + " -> "
                        + (blocked ? "blocked" : "allowed"));
                long ttl = blocked ? LIVE_BLOCK_TTL_MS : LIVE_ALLOW_TTL_MS;
                if (liveCache.size() > LIVE_CACHE_LIMIT) {
                    liveCache.clear();
                }
                liveCache.put(normalized, new LiveEntry(blocked, now + ttl));
                return blocked;
            }
        } finally {
            liveLocks.remove(normalized, lock);
        }
    }

    private static boolean queryAdGuard(String host) {
        try {
            return queryDoh(DOH_ENDPOINT, host);
        } catch (Exception first) {
            HelperLog.error("AdGuard DoH check failed for " + host, first);
            try {
                boolean blocked = queryDoh(DOH_FALLBACK, host);
                HelperLog.info("Fallback DoH check " + host + " -> "
                        + (blocked ? "blocked" : "allowed"));
                return blocked;
            } catch (Exception second) {
                disableLive();
                HelperLog.error("Fallback DoH check failed for " + host, second);
                return false;
            }
        }
    }

    private static boolean queryDoh(String endpoint, String host) throws Exception {
        HttpUrl url = HttpUrl.get(endpoint).newBuilder()
                .addQueryParameter("name", host)
                .addQueryParameter("type", "A")
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .header("User-Agent", "Morphe-Download-Helper/1.6.15")
                .build();
        try (Response response = DOH_HTTP.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("DoH HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("DoH response was empty");
            }
            JSONObject root = new JSONObject(body.string());
            if (root.optInt("Status", -1) != 0) {
                return false;
            }
            JSONArray answers = root.optJSONArray("Answer");
            if (answers == null) {
                return false;
            }
            for (int i = 0; i < answers.length(); i++) {
                JSONObject answer = answers.optJSONObject(i);
                if (answer == null || answer.optInt("type", -1) != 1) {
                    continue;
                }
                String data = answer.optString("data", "");
                if ("0.0.0.0".equals(data)
                        || "::".equals(data)
                        || "0:0:0:0:0:0:0:0".equals(data)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void disableLive() {
        liveDisabledUntil = System.currentTimeMillis() + LIVE_DISABLED_MS;
    }

    /** An empty resource used to stop an ad/tracker request without a network call. */
    static WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "utf-8",
                new ByteArrayInputStream(new byte[0]));
    }

    /** Number of domains in the bundled list, for diagnostics. */
    static int hostCount(Context context) {
        return hosts(context).size();
    }

    /**
     * Hides common ad containers and large floating overlays. It is intentionally
     * conservative: it never hides ordinary in-page content, so a download button
     * or verification widget is not mistaken for an ad.
     */
    static String cosmeticScript() {
        return "(function(){"
                + "if(window.__mhAdHidden)return;window.__mhAdHidden=true;"
                + "var css='ins.adsbygoogle,"
                + "[id^=\"google_ads\"],[id^=\"div-gpt-ad\"],"
                + "[class*=\"ad-banner\"],[class*=\"advertisement\"],[data-ad-slot],"
                + "[id*=\"taboola\"],[class*=\"taboola\"],"
                + "[id*=\"outbrain\"],[class*=\"outbrain\"],"
                + "iframe[src*=\"doubleclick.net\"],"
                + "iframe[src*=\"googlesyndication.com\"],"
                + "iframe[src*=\"adnxs.com\"],"
                + "iframe[src*=\"amazon-adsystem.com\"],"
                + "iframe[src*=\"taboola.com\"],"
                + "iframe[src*=\"outbrain.com\"]"
                + "{display:none!important;visibility:hidden!important;height:0!important;}';"
                + "var style=document.createElement('style');"
                + "style.type='text/css';"
                + "style.appendChild(document.createTextNode(css));"
                + "(document.head||document.documentElement).appendChild(style);"
                + "function hideFloating(){"
                + "var all=document.querySelectorAll('body div,body ins,body iframe');"
                + "for(var i=0;i<all.length;i++){var e=all[i];"
                + "var s=getComputedStyle(e);var r=e.getBoundingClientRect();"
                + "var label=(e.id+' '+e.className).toLowerCase();"
                + "if(s.position==='fixed'&&r.height>innerHeight*0.32"
                + "&&r.width>innerWidth*0.55"
                + "&&(parseInt(s.zIndex||0)>=5||label.indexOf('ad')>=0))"
                + "{e.style.display='none';}}}"
                + "hideFloating();"
                + "var n=0;var t=setInterval(function(){n++;hideFloating();"
                + "if(n>60)clearInterval(t);},700);"
                + "})();";
    }

    private static Set<String> hosts(Context context) {
        Set<String> result = blockedHosts;
        if (result == null) {
            synchronized (WebAdBlocker.class) {
                result = blockedHosts;
                if (result == null) {
                    result = load(context.getApplicationContext());
                    blockedHosts = result;
                }
            }
        }
        return result;
    }

    private static Set<String> load(Context context) {
        Set<String> result = new HashSet<>();
        try (InputStream input = context.getAssets().open(ASSET);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int comment = line.indexOf('#');
                if (comment >= 0) {
                    line = line.substring(0, comment);
                }
                line = line.trim().toLowerCase(Locale.US);
                if (line.startsWith(".")) {
                    line = line.substring(1);
                }
                if (!line.isEmpty()) {
                    result.add(line);
                }
            }
            HelperLog.info("Ad block-list loaded: " + result.size() + " hosts");
        } catch (IOException error) {
            HelperLog.error("Could not load the ad host list", error);
        }
        return result;
    }

    /** Matches an exact host or any parent domain listed in [set]. */
    private static boolean matches(Set<String> set, String host) {
        String candidate = host;
        while (true) {
            if (set.contains(candidate)) {
                return true;
            }
            int dot = candidate.indexOf('.');
            if (dot < 0) {
                return false;
            }
            candidate = candidate.substring(dot + 1);
        }
    }

    private static String normalize(String host) {
        if (host == null) {
            return "";
        }
        String result = host.trim().toLowerCase(Locale.US);
        while (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
