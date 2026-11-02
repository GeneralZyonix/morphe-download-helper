package be.alterra.morphehelper;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Headless download screen invoked by Morphe when the user taps "Use a helper app".
 *
 * <p><b>Contract</b><br>
 * Morphe sends {@code app.morphe.manager.action.DOWNLOAD_ORIGINAL_APK} with
 * {@code CATEGORY_DEFAULT}. The request contains the package, version, version codes,
 * file type, supported ABIs, and a fallback web URL. This activity must answer with
 * {@link #RESULT_OK}, a {@code content://} URI in {@code Intent.setData()}, and
 * {@link Intent#FLAG_GRANT_READ_URI_PERMISSION}.</p>
 *
 * <p><b>Resolution order</b></p>
 * <ol>
 *   <li>Use the fallback URL supplied by the patch bundle (direct file or APKMirror page).</li>
 *   <li>Search APKMirror by package name and requested version.</li>
 *   <li>Try APKPure as a last resort.</li>
 * </ol>
 *
 * <p>Uptodown is handled in an embedded WebView because its download endpoint uses
 * Cloudflare Turnstile. If nothing is found, the activity returns {@link #RESULT_CANCELED}
 * and Morphe shows its own manual download button.</p>
 *
 * <p><b>Safety</b><br>
 * Morphe re-checks the returned package, version, and signature before using the file.
 * This helper only downloads the original APK; it never installs anything.</p>
 */
public class DownloadActivity extends Activity {

    // ---------------------------------------------------------------------
    // Morphe intent contract keys
    // ---------------------------------------------------------------------

    /** Package name of the app Morphe wants to patch (for example, com.google.android.youtube). */
    private static final String EXTRA_PACKAGE_NAME = "app.morphe.manager.extra.PACKAGE_NAME";

    /** Human-readable app name shown in the UI. */
    private static final String EXTRA_APP_NAME = "app.morphe.manager.extra.APP_NAME";

    /** Version string requested by the patch bundle. */
    private static final String EXTRA_VERSION_NAME = "app.morphe.manager.extra.VERSION_NAME";

    /** One or more compatible Android version codes. */
    private static final String EXTRA_VERSION_CODES = "app.morphe.manager.extra.VERSION_CODES";

    /** Expected archive type: APK, APKM, APKS, or XAPK. */
    private static final String EXTRA_FILE_TYPE = "app.morphe.manager.extra.FILE_TYPE";

    /** True when Morphe accepts XAPK/APKS archives as well as plain APKs. */
    private static final String EXTRA_ALLOW_SPLIT_ARCHIVE = "app.morphe.manager.extra.ALLOW_SPLIT_ARCHIVE";


    /** Web page to try first when the patch bundle provides one. */
    private static final String EXTRA_FALLBACK_WEB_URL = "app.morphe.manager.extra.FALLBACK_WEB_URL";

    /** Contract version, logged for easier debugging of future Morphe releases. */
    private static final String EXTRA_PROTOCOL_VERSION = "app.morphe.manager.extra.PROTOCOL_VERSION";

    /** Device ABIs that the downloaded file should support. */
    private static final String EXTRA_SUPPORTED_ABIS = "app.morphe.manager.extra.SUPPORTED_ABIS";

    // ---------------------------------------------------------------------
    // Network configuration
    // ---------------------------------------------------------------------

    /** Mobile Chrome user agent; several APK sites reject unknown HTTP clients. */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    /** Base URL used to turn relative APKMirror links into absolute URLs. */
    private static final String APKMIRROR = "https://www.apkmirror.com";

    /** APKPure app API used to resolve versions without the WebView. */
    private static final String APKPURE_API =
            "https://tapi.pureapk.com/v3/get_app_his_version";

    private static final String APPTEKA_API =
            "https://appteka.store/api/1/app/info";

    /** Morphe's public resolver API, the same one its download dialog uses. */
    private static final String MORPHE_API = "https://api.morphe.software";
    private static final String MORPHE_USER_AGENT = "Morphe-Manager/1.0";

    /** Does not follow redirects; used to read the Location header from Morphe's API. */
    private static final OkHttpClient API_HTTP = new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .build();

    // ---------------------------------------------------------------------
    // UI palette
    // ---------------------------------------------------------------------

    private static final int BG_TOP = Color.parseColor("#0B1220");
    private static final int BG_BOTTOM = Color.parseColor("#0F172A");
    private static final int CARD_TOP = Color.parseColor("#111827");
    private static final int CARD_BOTTOM = Color.parseColor("#0F172A");
    private static final int BORDER = Color.parseColor("#1F2937");
    private static final int PRIMARY_START = Color.parseColor("#6366F1");
    private static final int PRIMARY_END = Color.parseColor("#4F46E5");
    private static final int SECONDARY = Color.parseColor("#1F2937");
    private static final int SECONDARY_BORDER = Color.parseColor("#334155");
    private static final int TEXT = Color.parseColor("#F8FAFC");
    private static final int MUTED = Color.parseColor("#94A3B8");
    private static final int STEPS = Color.parseColor("#CBD5E1");
    private static final int WARN_TEXT = Color.parseColor("#FDE68A");
    private static final int GREEN = Color.parseColor("#34D399");
    private static final int RED = Color.parseColor("#F87171");

    // ---------------------------------------------------------------------
    // Feature switches
    // ---------------------------------------------------------------------

    /** Uptodown is controlled by SourceSettings. */

    /**
     * Google search fallbacks cannot be resolved without a browser. They are allowed to
     * pass through the resolver, which then falls through to APKMirror/APKPure.
     */
    private static final boolean ENABLE_GOOGLE_SEARCH_FALLBACK = true;

    // ---------------------------------------------------------------------
    // HTTP client
    // ---------------------------------------------------------------------

    /**
     * Shared OkHttp client.
     *
     * <p>OkHttp is used instead of {@link java.net.HttpURLConnection} because it handles
     * redirects, cookies, and Cloudflare challenges much more reliably on Android.</p>
     */
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .cookieJar(new MemoryCookieJar())
            .build();

    /**
     * In-memory cookie jar.
     *
     * <p>APKMirror and similar sites set Cloudflare/session cookies on the first request.
     * Keeping them for the lifetime of the process lets the follow-up download request
     * succeed without re-triggering the challenge.</p>
     */
    /** Thrown after handing off to the Uptodown WebView; no error UI is needed. */
    private static final class UptodownHandoffException extends IOException {
    }

    private static final class MemoryCookieJar implements CookieJar {

        /** Cookies grouped by host name. */
        private final Map<String, List<Cookie>> store = new HashMap<>();

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            // Replace the host's cookie list with the latest response's cookies.
            store.put(url.host(), new ArrayList<>(cookies));
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            // Return an empty list (never null) when the host has no cookies yet.
            List<Cookie> cookies = store.get(url.host());
            return cookies != null ? cookies : Collections.emptyList();
        }
    }

    // ---------------------------------------------------------------------
    // Threading and state
    // ---------------------------------------------------------------------

    /** Posts UI updates back to the Android main thread. */
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Single background worker; all network and file work runs here. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Current status line shown inside the card. */
    private TextView status;

    /** Download progress bar. Indeterminate while resolving, determinate while downloading. */
    private ProgressBar progress;

    /** Button that opens the fallback web page when automatic resolution fails. */
    private TextView fallbackButton;

    /** Cancel/Close button. */
    private TextView cancelButton;

    /** Pause/Resume button, shown once a download starts. */
    private TextView pauseButton;

    /** Label above the progress bar naming the active source. */
    private TextView sourceLabel;

    /** Set to true when the user backs out or the activity is destroyed. */
    private volatile boolean cancelled = false;

    /** Set while the user has paused an active download. */
    private volatile boolean paused = false;

    /** Fallback URL supplied by Morphe, if any. */
    private String fallbackUrl;

    /** Whether Morphe accepts split archives (XAPK/APKS) for this request. */
    private boolean allowSplitArchive;

    /** Root scroll view, used to reveal the Uptodown section. */
    private ScrollView scrollView;

    /** Source WebView section shown below the normal status card. */
    private LinearLayout uptodownSection;

    /** Container for the source WebView. */
    private FrameLayout uptodownWebContainer;

    /** WebView used for Uptodown's Turnstile page or APKPure's Cloudflare page. */
    private WebView uptodownWebView;

    /** Dynamic labels for the source WebView section. */
    private TextView uptodownTitle;
    private TextView uptodownExplanation;

    /** Active WebView source: "uptodown" or "apkpure". */
    private String webSource;

    /** App name used to pick the right APKPure search result. */
    private String requestedAppName;

    /** Guards against handling the same Uptodown download URL more than once. */
    private boolean uptodownHandled;

    /** Request values kept for the WebView download path. */
    private String uptodownPkg;
    private String uptodownVersion;
    private String uptodownFileType;
    private String uptodownReferer;

    /** Auto-refresh timer for a stuck Uptodown download button. */
    private Runnable uptodownReload;
    private int uptodownReloads;

    private LinearLayout diagnosticsCard;
    private LinearLayout diagnosticsRows;
    private TextView diagnosticsSummary;
    private TextView retryButton;
    private TextView copyButton;
    private ResolutionReport report;
    private Set<String> retrySources;
    private String requestPkg;
    private String requestVersion;
    private String requestFileType;
    private long[] requestVersionCodes;
    private String[] requestAbis;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prepare logging first so every later step is captured.
        HelperLog.init(getApplicationContext());
        SourceSettings.ensureInitialized(this);
        new Thread(() -> AppSettings.cleanup(this)).start();

        // Build the dark UI before reading the intent; the UI is then updated as work runs.
        buildUi();

        // -----------------------------------------------------------------
        // 1. Read the request sent by Morphe
        // -----------------------------------------------------------------
        Intent intent = getIntent();
        String pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        String appName = intent.getStringExtra(EXTRA_APP_NAME);
        String version = intent.getStringExtra(EXTRA_VERSION_NAME);
        long[] versionCodes = intent.getLongArrayExtra(EXTRA_VERSION_CODES);
        String fileType = intent.getStringExtra(EXTRA_FILE_TYPE);
        String[] abis = intent.getStringArrayExtra(EXTRA_SUPPORTED_ABIS);
        int protocolVersion = intent.getIntExtra(EXTRA_PROTOCOL_VERSION, 0);
        fallbackUrl = intent.getStringExtra(EXTRA_FALLBACK_WEB_URL);
        allowSplitArchive = intent.getBooleanExtra(EXTRA_ALLOW_SPLIT_ARCHIVE, false);
        requestedAppName = appName;
        requestPkg = pkg;
        requestVersion = version;
        requestFileType = fileType;
        requestVersionCodes = versionCodes;
        requestAbis = abis;

        // Log the full request once; this is the first thing to check in a bug report.
        HelperLog.info("Request pkg=" + pkg
                + " app=" + appName
                + " version=" + version
                + " versionCodes=" + java.util.Arrays.toString(versionCodes)
                + " fileType=" + fileType
                + " abis=" + java.util.Arrays.toString(abis)
                + " protocol=" + protocolVersion
                + " fallback=" + fallbackUrl);
        HelperLog.info("Enabled sources"
                + " fallback=" + sourceAllowed(SourceSettings.FALLBACK)
                + " apkmirror=" + sourceAllowed(SourceSettings.APKMIRROR)
                + " apkpure=" + sourceAllowed(SourceSettings.APKPURE)
                + " uptodown=" + sourceAllowed(SourceSettings.UPTODOWN)
                + " fdroid=" + sourceAllowed(SourceSettings.FDROID)
                + " aptoide=" + sourceAllowed(SourceSettings.APTOIDE)
                + " appteka=" + sourceAllowed(SourceSettings.APPTEKA)
                + " apptekaSnapTubeOnly=" + AppSettings.apptekaSnapTubeOnly(this));

        // -----------------------------------------------------------------
        // 2. Reject fallback hosts that the helper cannot handle
        // -----------------------------------------------------------------
        String fallbackHost = hostOf(fallbackUrl);
        HelperLog.info("Fallback host: " + fallbackHost);

        // A request without a package cannot be resolved. Show the error and let the
        // user close the screen or open the fallback page.
        if (TextUtils.isEmpty(pkg)) {
            setStatus("Morphe did not send a package name.");
            showFallback();
            return;
        }

        // Uptodown's page runs Cloudflare Turnstile. A WebView is the only reliable
        // way to complete that challenge and capture the resulting download URL.
        if (isHost(fallbackHost, "uptodown.com")) {
            if (sourceAllowed(SourceSettings.UPTODOWN)
                    && sourceAllowed(SourceSettings.FALLBACK)) {
                setStatus("Opening Uptodown...");
                progress.setIndeterminate(true);
                startUptodownWebFlow(fallbackUrl, pkg, version, fileType);
                return;
            }
            HelperLog.info("Uptodown fallback disabled; trying other sources");
        }

        // Google search URLs cannot be resolved without a browser; if this switch is
        // ever turned off, cancel instead of pretending the helper can handle it.
        if (!ENABLE_GOOGLE_SEARCH_FALLBACK && isHost(fallbackHost, "google.com")) {
            HelperLog.info("Google search fallback unsupported; returning canceled");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        // -----------------------------------------------------------------
        // 3. Show a human-readable "preparing" status
        // -----------------------------------------------------------------
        String label = TextUtils.isEmpty(appName) ? pkg : appName;
        if (!TextUtils.isEmpty(version)) {
            label += " " + version;
        }
        setStatus("Preparing " + label + "...");
        progress.setIndeterminate(true);

        startResolution(false);
    }

    private void startResolution(boolean retryOnly) {
        Set<String> failed = retryOnly && report != null
                ? report.failedKeys() : Collections.emptySet();
        if (retryOnly && failed.isEmpty()) {
            return;
        }
        retrySources = retryOnly ? failed : null;
        String abi = (requestAbis != null && requestAbis.length > 0)
                ? requestAbis[0] : "unknown";
        report = new ResolutionReport(requestedAppName, requestPkg, requestVersion,
                requestFileType, abi);
        HelperLog.info(report.headerBlock());

        diagnosticsCard.setVisibility(View.GONE);
        fallbackButton.setVisibility(View.GONE);
        sourceLabel.setVisibility(View.GONE);
        cancelButton.setText("Cancel");
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        progress.setProgress(0);
        setPauseVisible(false);
        if (retryOnly) {
            setStatus("Retrying failed sources...");
        }
        startDownloadService(retryOnly ? "Retrying failed sources..." : "Resolving APK...");

        executor.execute(() -> {
            try {
                File file = resolve(requestPkg, requestVersion, requestVersionCodes,
                        requestFileType, requestAbis);
                if (cancelled) {
                    return;
                }
                deliverFile(file);
            } catch (UptodownHandoffException ignored) {
            } catch (Exception e) {
                if (cancelled) {
                    return;
                }
                report.finishFailure(e.getMessage());
                HelperLog.info(report.resultBlock());
                HelperLog.error("Download failed", e);
                stopDownloadService();
                setPauseVisible(false);
                runOnUiThread(() -> {
                    progress.setIndeterminate(false);
                    progress.setProgress(0);
                    setStatus("Could not find a matching APK.");
                    showFailure();
                });
            }
        });
    }

    /**
     * Returns a downloaded file to Morphe through FileProvider.
     *
     * <p>Morphe can only read the file when the result carries
     * {@link Intent#FLAG_GRANT_READ_URI_PERMISSION}.</p>
     */
    private void deliverFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    DownloadActivity.this,
                    getPackageName() + ".fileprovider",
                    file
            );
            Intent result = new Intent();
            result.setData(uri);
            result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (report != null) {
                report.finishSuccess(file);
                HelperLog.info(report.resultBlock());
            }
            HelperLog.info("Returning file " + file.getAbsolutePath()
                    + " (" + file.length() + " bytes)");
            stopDownloadService();

            setPauseVisible(false);
            runOnUiThread(() -> {
                setResult(RESULT_OK, result);
                setStatus("Done: " + file.getName());
                finish();
            });
        } catch (Exception e) {
            if (report != null) {
                report.finishFailure(e.getMessage());
                HelperLog.info(report.resultBlock());
            }
            HelperLog.error("Could not return file", e);
            stopDownloadService();
            runOnUiThread(() -> {
                progress.setIndeterminate(false);
                progress.setProgress(0);
                setStatus("Could not find a matching APK.");
                showFailure();
            });
        }
    }

    /** Opens Uptodown in the source WebView and auto-clicks its download button. */
    private void startUptodownWebFlow(String url, String pkg, String version, String fileType) {
        startWebFlow("uptodown", url, pkg, version, fileType);
    }

    /** Opens APKPure search in the source WebView so the user can pass Cloudflare. */
    private void startApkPureWebFlow(String pkg, String version, String fileType) {
        String url;
        try {
            url = "https://apkpure.com/search?q=" + URLEncoder.encode(pkg, "UTF-8");
        } catch (Exception ignored) {
            url = "https://apkpure.com/search?q=" + pkg;
        }
        startWebFlow("apkpure", url, pkg, version, fileType);
    }

    private void startWebFlow(String source, String url, String pkg, String version,
                              String fileType) {
        webSource = source;
        setSourceLabel("apkpure".equals(source) ? "APKPure WebView" : "Uptodown WebView");
        uptodownPkg = pkg;
        uptodownVersion = version;
        uptodownFileType = fileType;
        uptodownReferer = url;
        uptodownHandled = false;
        uptodownReloads = 0;

        if ("apkpure".equals(source)) {
            uptodownTitle.setText("APKPure download");
            uptodownExplanation.setText(
                    "APKPure uses Cloudflare protection. If a search page appears, tap the app "
                            + "result first. On the app page, tap Download APK (not the APKPure app "
                            + "button). The helper will capture the XAPK or APK and return it to Morphe.");
        } else {
            uptodownTitle.setText("Uptodown verification");
            uptodownExplanation.setText(
                    "Uptodown needs a quick Cloudflare check. The official download page is "
                            + "shown below. Wait for the download button, then tap it if the download "
                            + "does not start automatically. The APK will be returned to Morphe.");
        }
        fallbackButton.setVisibility(View.GONE);

        WebView web = new WebView(this);
        uptodownWebView = web;
        web.setBackgroundColor(BG_TOP);

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(USER_AGENT);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUptodownUrl(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUptodownUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                HelperLog.info(webSource + " page finished: " + url);
                uptodownReferer = url;
                if ("uptodown".equals(webSource)) {
                    scheduleUptodownReload(view);
                    // Uptodown's button starts Turnstile and then triggers the download.
                    view.evaluateJavascript(
                            "(function(){var n=0;var t=setInterval(function(){"
                                    + "n++;var b=document.getElementById('detail-download-button');"
                                    + "if(b){clearInterval(t);b.scrollIntoView({block:'center'});"
                                    + "setTimeout(function(){b.click();},300);}"
                                    + "if(n>60)clearInterval(t);},500);})();",
                            null);
                } else {
                    String pkgJson = JSONObject.quote(uptodownPkg);
                    String versionJson = JSONObject.quote(
                            uptodownVersion == null ? "" : uptodownVersion);
                    String nameJson = JSONObject.quote(
                            requestedAppName == null ? "" : requestedAppName);
                    view.evaluateJavascript(
                            "(function(){if(window.__mhStarted)return;window.__mhStarted=true;"
                                    + "var pkg=" + pkgJson + ",ver=" + versionJson
                                    + ",name=" + nameJson + ";"
                                    + "function hasVer(s){if(!ver)return true;"
                                    + "var e=ver.replace(/[.]/g,'\\\\.');"
                                    + "return new RegExp('(^|[^0-9])'+e+'([^0-9]|$)').test(s||'');}"
                                    + "function killAds(){var all=document.querySelectorAll('body div,body ins,body iframe');"
                                    + "for(var i=0;i<all.length;i++){var e=all[i];var s=getComputedStyle(e);"
                                    + "var r=e.getBoundingClientRect();var label=(e.id+' '+e.className).toLowerCase();"
                                    + "if(s.position==='fixed'&&r.height>innerHeight*0.32"
                                    + "&&r.width>innerWidth*0.55&&(parseInt(s.zIndex||0)>=5||label.indexOf('ad')>=0))"
                                    + "{e.style.display='none';}}}"
                                    + "var n=0;var t=setInterval(function(){n++;killAds();"
                                    + "var path=location.pathname;"
                                    + "if(path.indexOf('/search')>=0){"
                                    + "var ls=document.querySelectorAll('a');"
                                    + "for(var i=0;i<ls.length;i++){var h=ls[i].href||'';"
                                    + "var txt=(ls[i].innerText||'').toLowerCase();"
                                    + "if(h.indexOf('/search')>=0)continue;"
                                    + "if(h.toLowerCase().indexOf(pkg.toLowerCase())>=0"
                                    + "||(name.length>2&&txt.indexOf(name.toLowerCase())>=0&&txt.length<100))"
                                    + "{ls[i].click();clearInterval(t);return;}}}"
                                    + "if(path.indexOf('/versions')>=0){"
                                    + "var vs=document.querySelectorAll('a');"
                                    + "for(var j=0;j<vs.length;j++){var vt=(vs[j].innerText||'').trim();"
                                    + "if(hasVer(vt)){vs[j].click();clearInterval(t);return;}}}"
                                    + "if(path.indexOf('/versions')<0&&ver"
                                    + "&&!hasVer(document.body.innerText)){"
                                    + "var old=document.querySelector('a[href*=\"/versions\"]');"
                                    + "if(old){old.click();clearInterval(t);return;}}"
                                    + "var a=document.querySelector('a[href*=\"d.apkpure.com\"]');"
                                    + "if(a){clearInterval(t);a.scrollIntoView({block:'center'});"
                                    + "setTimeout(function(){a.click();},500);return;}"
                                    + "var bs=document.querySelectorAll('a,button');"
                                    + "for(var k=0;k<bs.length;k++){var bt=(bs[k].innerText||'').toLowerCase();"
                                    + "if(bt.indexOf('download apk')>=0&&bt.indexOf('apkpure app')<0){"
                                    + "clearInterval(t);bs[k].scrollIntoView({block:'center'});"
                                    + "setTimeout(function(){bs[k].click();},500);return;}}"
                                    + "if(n>50)clearInterval(t);},700);})();",
                            null);
                }
            }
        });

        web.setDownloadListener((downloadUrl, userAgent, contentDisposition,
                                 mimetype, contentLength) -> {
            if (!handleUptodownUrl(downloadUrl)) {
                HelperLog.info(webSource + " download listener ignored " + downloadUrl);
            }
        });

        // Let the WebView scroll its own page instead of the parent ScrollView.
        web.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });

        uptodownWebContainer.removeAllViews();
        uptodownWebContainer.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        uptodownSection.setVisibility(View.VISIBLE);
        scrollView.post(() -> scrollView.smoothScrollTo(0, uptodownSection.getTop()));
        web.loadUrl(url);
    }

    /**
     * If the Uptodown button is still loading after ten seconds, refresh the page.
     * A refresh often clears a stuck Turnstile state. Limit it to two attempts.
     */
    private void scheduleUptodownReload(WebView view) {
        if (uptodownReload != null) {
            main.removeCallbacks(uptodownReload);
        }
        uptodownReload = () -> {
            if (uptodownHandled || uptodownWebView != view) {
                return;
            }
            if (uptodownReloads >= 2) {
                HelperLog.info("Uptodown still loading after two refreshes");
                return;
            }
            uptodownReloads++;
            HelperLog.info("Uptodown still loading; refreshing page ("
                    + uptodownReloads + "/2)");
            view.reload();
        };
        main.postDelayed(uptodownReload, 10_000);
    }

    /** Captures the final download URL from navigation or DownloadListener. */
    private boolean handleUptodownUrl(String url) {
        if (url == null) {
            return false;
        }
        String host = hostOf(url);
        if ("apkpure".equals(webSource)) {
            if (isHost(host, "d.apkpure.com") || isHost(host, "d.apkpure.net")
                    || isHost(host, "download.apkpure.com")) {
                handleUptodownDownload(url);
                return true;
            }
            return false;
        }
        if (isHost(host, "dw.uptodown.com") || isHost(host, "dw.uptodown.net")) {
            handleUptodownDownload(url);
            return true;
        }
        return false;
    }

    /** Downloads the URL captured from the source WebView and returns it to Morphe. */
    private void handleUptodownDownload(String url) {
        if (uptodownHandled) {
            return;
        }
        uptodownHandled = true;
        HelperLog.info(webSource + " download URL: " + url);

        final String source = webSource;
        String type = uptodownFileType;
        if ("apkpure".equals(source)) {
            if (url.toLowerCase(Locale.US).contains("/xapk/")) {
                type = "xapk";
            }
        } else {
            // Uptodown normally serves a full APK. Requested APKS/APKM can otherwise
            // give the file a misleading extension and make Morphe reject it.
            type = "apk";
        }
        final String downloadType = type;

        main.post(() -> {
            if (uptodownWebView != null) {
                uptodownWebContainer.removeView(uptodownWebView);
                uptodownWebView.destroy();
                uptodownWebView = null;
            }
            uptodownSection.setVisibility(View.GONE);
            progress.setVisibility(View.VISIBLE);
            progress.setIndeterminate(true);
            setStatus("Downloading from " + ("apkpure".equals(source) ? "APKPure" : "Uptodown") + "...");

            final String cookie = CookieManager.getInstance().getCookie(url);
            executor.execute(() -> {
                try {
                    File file = downloadFile(
                            url,
                            uptodownReferer,
                            uptodownPkg,
                            uptodownVersion,
                            downloadType,
                            cookie
                    );
                    markSource("apkpure".equals(source)
                            ? SourceSettings.APKPURE : SourceSettings.UPTODOWN, true, "WebView");
                    if (!cancelled) {
                        deliverFile(file);
                    }
                } catch (Exception e) {
                    markSource("apkpure".equals(source)
                            ? SourceSettings.APKPURE : SourceSettings.UPTODOWN, false, e.getMessage());
                    if (cancelled) {
                        return;
                    }
                    if (report != null) {
                        report.finishFailure(e.getMessage());
                        HelperLog.info(report.resultBlock());
                    }
                    HelperLog.error(source + " download failed", e);
                    setPauseVisible(false);
                    runOnUiThread(() -> {
                        progress.setIndeterminate(false);
                        progress.setProgress(0);
                        setStatus("Could not find a matching APK.");
                        showFailure();
                    });
                }
            });
        });
    }

    /**
     * Builds the download screen entirely in code.
     *
     * <p>The layout is intentionally simple: logo, title, source summary, status/progress
     * card, and two action buttons. Keeping it code-only avoids XML theme surprises on
     * different Android versions.</p>
     */
    private void buildUi() {
        // Match the system bars to the dark background.
        getWindow().setStatusBarColor(BG_TOP);
        getWindow().setNavigationBarColor(BG_TOP);

        // ScrollView keeps the layout usable on small screens and with large fonts.
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(gradient(BG_TOP, BG_BOTTOM));

        // Main vertical column; top padding keeps the logo clear of the status bar.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(64), dp(24), dp(24));

        // Brand mark.
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo_mark);
        root.addView(logo, new LinearLayout.LayoutParams(dp(84), dp(84)));

        // Screen title.
        TextView title = text("Morphe Download Helper", 24, TEXT, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(16);
        root.addView(title, titleParams);

        // Short subtitle explaining what the screen is doing.
        TextView subtitle = text("Fetching the original APK", 14, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(6);
        root.addView(subtitle, subtitleParams);

        // Static source order; the live status line below shows which one is active.
        TextView sources = text(
                "Sources: APKPure → Morphe → APKMirror → F-Droid → Aptoide → Appteka (SnapTube)",
                12, MUTED, false);
        sources.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sourcesParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sourcesParams.topMargin = dp(8);
        root.addView(sources, sourcesParams);

        // Card containing status, progress, and the source warning.
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(cardBackground());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(28);
        root.addView(card, cardParams);

        // Live status text ("Preparing...", "Trying APKMirror...", "Done...", etc.).
        status = text("", 15, STEPS, false);
        status.setGravity(Gravity.CENTER);
        status.setLineSpacing(dp(3), 1f);
        card.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        sourceLabel = text("", 12, MUTED, true);
        sourceLabel.setGravity(Gravity.CENTER);
        sourceLabel.setVisibility(View.GONE);
        LinearLayout.LayoutParams sourceLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sourceLabelParams.topMargin = dp(14);
        card.addView(sourceLabel, sourceLabelParams);

        // Indeterminate while resolving; switches to a percentage while downloading.
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(ColorStateList.valueOf(PRIMARY_START));
        progress.setProgressTintList(ColorStateList.valueOf(PRIMARY_START));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        progressParams.topMargin = dp(18);
        card.addView(progress, progressParams);

        diagnosticsCard = new LinearLayout(this);
        diagnosticsCard.setOrientation(LinearLayout.VERTICAL);
        diagnosticsCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        diagnosticsCard.setBackground(cardBackground());
        diagnosticsCard.setVisibility(View.GONE);
        LinearLayout.LayoutParams diagnosticsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        diagnosticsParams.topMargin = dp(14);
        root.addView(diagnosticsCard, diagnosticsParams);

        diagnosticsCard.addView(text("What was tried", 16, TEXT, true));
        diagnosticsSummary = text("", 12, MUTED, false);
        diagnosticsSummary.setLineSpacing(dp(3), 1f);
        diagnosticsSummary.setPadding(0, dp(6), 0, dp(4));
        diagnosticsCard.addView(diagnosticsSummary);

        diagnosticsRows = new LinearLayout(this);
        diagnosticsRows.setOrientation(LinearLayout.VERTICAL);
        diagnosticsCard.addView(diagnosticsRows);

        retryButton = secondaryButton("Retry failed sources");
        retryButton.setVisibility(View.GONE);
        retryButton.setOnClickListener(v -> retryFailedSources());
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        retryParams.topMargin = dp(10);
        diagnosticsCard.addView(retryButton, retryParams);

        copyButton = secondaryButton("Copy details");
        copyButton.setVisibility(View.GONE);
        copyButton.setOnClickListener(v -> copyDiagnostics());
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        copyParams.topMargin = dp(8);
        diagnosticsCard.addView(copyButton, copyParams);

        // Uptodown verification section. Hidden until the WebView flow starts so the
        // normal APKMirror/APKPure screens stay clean.
        uptodownSection = new LinearLayout(this);
        uptodownSection.setOrientation(LinearLayout.VERTICAL);
        uptodownSection.setPadding(dp(18), dp(16), dp(18), dp(16));
        uptodownSection.setBackground(cardBackground());
        uptodownSection.setVisibility(View.GONE);
        LinearLayout.LayoutParams uptodownParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        uptodownParams.topMargin = dp(14);
        root.addView(uptodownSection, uptodownParams);

        uptodownTitle = text("Uptodown verification", 16, TEXT, true);
        uptodownSection.addView(uptodownTitle);

        uptodownExplanation = text(
                "Uptodown needs a quick Cloudflare check. The official download page is "
                        + "shown below. Wait for the download button, then tap it if the download "
                        + "does not start automatically. The APK will be returned to Morphe.",
                13, STEPS, false);
        uptodownExplanation.setLineSpacing(dp(3), 1f);
        uptodownExplanation.setPadding(0, dp(8), 0, dp(12));
        uptodownSection.addView(uptodownExplanation);

        uptodownWebContainer = new FrameLayout(this);
        uptodownSection.addView(uptodownWebContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(520)));

        // Fallback button is hidden until resolution fails.
        fallbackButton = primaryButton("Open download page");
        fallbackButton.setVisibility(View.GONE);
        fallbackButton.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(fallbackUrl)) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)));
                } catch (Exception ignored) {
                    // If no browser can handle the URL, leave the screen as-is.
                }
            }
        });
        LinearLayout.LayoutParams fallbackParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52));
        fallbackParams.topMargin = dp(16);
        root.addView(fallbackButton, fallbackParams);

        pauseButton = secondaryButton("Pause");
        pauseButton.setVisibility(View.GONE);
        pauseButton.setOnClickListener(v -> togglePause());
        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        pauseParams.topMargin = dp(10);
        root.addView(pauseButton, pauseParams);

        // Cancel/Close button is always visible.
        cancelButton = secondaryButton("Cancel");
        cancelButton.setOnClickListener(v -> cancel());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50));
        cancelParams.topMargin = dp(10);
        root.addView(cancelButton, cancelParams);

        // Attach the content column to the ScrollView and show it.
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollView = scroll;
        setContentView(scroll);
    }

    /** Converts density-independent pixels to physical pixels for this screen. */
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    /** Creates a consistently styled TextView used across the screen. */
    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(
                "sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL
        ));
        return view;
    }

    /** Creates the full-width gradient button used for the fallback action. */
    private TextView primaryButton(String label) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        view.setTextColor(Color.WHITE);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));

        GradientDrawable fill = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{PRIMARY_START, PRIMARY_END}
        );
        fill.setCornerRadius(dp(16));
        view.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF),
                fill,
                null
        ));
        return view;
    }

    /** Creates the dark outlined Cancel/Close button. */
    private TextView secondaryButton(String label) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        view.setTextColor(TEXT);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));

        GradientDrawable fill = rounded(SECONDARY, 14);
        fill.setStroke(dp(1), SECONDARY_BORDER);
        view.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x22FFFFFF),
                fill,
                null
        ));
        return view;
    }

    /** Rounded, subtly bordered card background used by the status card. */
    private GradientDrawable cardBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{CARD_TOP, CARD_BOTTOM}
        );
        drawable.setCornerRadius(dp(20));
        drawable.setStroke(dp(1), BORDER);
        return drawable;
    }

    /** Creates a two-stop vertical gradient for the screen background. */
    private GradientDrawable gradient(int start, int end) {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{start, end}
        );
    }

    /** Creates a solid rounded drawable for buttons and chips. */
    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    /** Updates the status line from any thread. */
    private void setStatus(String text) {
        main.post(() -> status.setText(text));
    }

    private void startDownloadService(String text) {
        Intent intent = new Intent(this, DownloadService.class)
                .setAction(DownloadService.ACTION_START)
                .putExtra(DownloadService.EXTRA_TEXT, text)
                .putExtra(DownloadService.EXTRA_INDETERMINATE, true);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void updateDownloadService(String text, int progressValue, boolean indeterminate) {
        Intent intent = new Intent(this, DownloadService.class)
                .setAction(DownloadService.ACTION_UPDATE)
                .putExtra(DownloadService.EXTRA_TEXT, text)
                .putExtra(DownloadService.EXTRA_PROGRESS, progressValue)
                .putExtra(DownloadService.EXTRA_INDETERMINATE, indeterminate);
        startService(intent);
    }

    private void stopDownloadService() {
        startService(new Intent(this, DownloadService.class)
                .setAction(DownloadService.ACTION_STOP));
    }
    private void setSourceLabel(String label) {
        main.post(() -> {
            sourceLabel.setText("Source: " + label);
            sourceLabel.setVisibility(View.VISIBLE);
        });
    }

    private void setPauseVisible(boolean visible) {
        main.post(() -> {
            pauseButton.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (!visible) {
                paused = false;
                pauseButton.setText("Pause");
            }
        });
    }

    private void togglePause() {
        if (paused) {
            paused = false;
            pauseButton.setText("Pause");
            setStatus("Resuming download...");
            updateDownloadService("Resuming download...", progress.getProgress(), false);
        } else {
            paused = true;
            pauseButton.setText("Resume");
            setStatus("Paused");
            updateDownloadService("Paused", progress.getProgress(), false);
        }
    }
    private void markSource(String key, boolean ok, String message) {
        if (report != null) {
            report.record(key, ok, message);
        }
        SourceHealth.record(this, key, ok, message == null ? "" : message);
    }

    private void showFallback() {
        showFailure();
    }

    private void showFailure() {
        main.post(() -> {
            progress.setVisibility(View.GONE);
            sourceLabel.setVisibility(View.GONE);
            if (!TextUtils.isEmpty(fallbackUrl)) {
                fallbackButton.setVisibility(View.VISIBLE);
            }
            cancelButton.setText("Close");
            if (report != null) {
                populateDiagnostics(report);
            }
        });
    }

    private void populateDiagnostics(ResolutionReport resolutionReport) {
        List<ResolutionReport.Attempt> attempts = resolutionReport.attempts();
        Set<String> failed = resolutionReport.failedKeys();

        diagnosticsRows.removeAllViews();
        diagnosticsCard.setVisibility(View.VISIBLE);
        String reason = resolutionReport.failureReason();
        if (TextUtils.isEmpty(reason)) {
            reason = "No matching APK was found.";
        } else if (!reason.endsWith(".")) {
            reason += ".";
        }
        diagnosticsSummary.setText(reason
                + (attempts.isEmpty() ? "" : " Tried " + attempts.size()
                + " source attempt(s)."));

        for (ResolutionReport.Attempt attempt : attempts) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            row.addView(text(attempt.label, 14, TEXT, true));

            String detail = (attempt.success ? "OK" : "FAILED")
                    + (TextUtils.isEmpty(attempt.detail) ? "" : " \u2022 " + attempt.detail);
            TextView detailView = text(detail, 12, attempt.success ? GREEN : RED, false);
            detailView.setPadding(0, dp(3), 0, 0);
            row.addView(detailView);
            diagnosticsRows.addView(row);

            View divider = new View(this);
            divider.setBackgroundColor(BORDER);
            diagnosticsRows.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        }

        retryButton.setVisibility(failed.isEmpty() ? View.GONE : View.VISIBLE);
        retryButton.setEnabled(true);
        copyButton.setVisibility(View.VISIBLE);
    }

    private void retryFailedSources() {
        if (report == null || report.failedKeys().isEmpty()) {
            return;
        }
        retryButton.setEnabled(false);
        startResolution(true);
    }

    private void copyDiagnostics() {
        if (report == null) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                "Morphe Download Helper diagnostics", report.resultBlock()));
        Toast.makeText(this, "Diagnostics copied.", Toast.LENGTH_SHORT).show();
    }

    /** Cancels background work and returns RESULT_CANCELED to Morphe. */
    private void cancel() {
        cancelled = true;
        setPauseVisible(false);
        stopDownloadService();
        executor.shutdownNow();
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Back behaves exactly like Cancel: no partial file is returned to Morphe.
        cancel();
    }

    @Override
    protected void onDestroy() {
        // Stop network work and release the WebView if it is still open.
        cancelled = true;
        executor.shutdownNow();
        if (uptodownWebView != null) {
            uptodownWebContainer.removeView(uptodownWebView);
            uptodownWebView.destroy();
            uptodownWebView = null;
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------------------
    // Resolution strategy
    // ---------------------------------------------------------------------

    /**
     * Tries every supported source in order and returns the first downloaded file.
     *
     * <p>Each source is attempted inside its own try/catch so one broken site does not
     * prevent the next source from being tried. If all sources fail, the last exception
     * is propagated to the caller, which shows the fallback UI.</p>
     */
    /**
     * Asks Morphe's resolver for the actual download page. This is the same API the
     * Morphe download dialog uses, so it can return the exact Uptodown -x link.
     */
    private String resolveMorpheUrl(String pkg, String version, String[] abis) {
        String abi = (abis != null && abis.length > 0) ? abis[0] : "arm64-v8a";
        String query = pkg + "~" + (TextUtils.isEmpty(version) ? "any" : version) + "~" + abi;
        String url;
        try {
            url = MORPHE_API + "/v2/web-search/" + URLEncoder.encode(query, "UTF-8")
                    .replace("+", "%20")
                    .replace("%7E", "~");
        } catch (Exception e) {
            return null;
        }

        for (int i = 0; i < 6; i++) {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", MORPHE_USER_AGENT)
                    .head()
                    .build();
            try (Response response = API_HTTP.newCall(request).execute()) {
                String location = response.header("Location");
                HelperLog.info("Morphe resolver " + url + " -> HTTP " + response.code()
                        + " location=" + location);
                if (location == null || location.isEmpty()) {
                    return isHost(hostOf(url), "api.morphe.software") ? null : url;
                }
                url = new URL(new URL(url), location).toString();
                if (!isHost(hostOf(url), "api.morphe.software")) {
                    return url;
                }
            } catch (Exception e) {
                HelperLog.error("Morphe resolver failed", e);
                return null;
            }
        }
        return null;
    }

    /** APKPure serves APK/XAPK, not APKM/APKS. Bundle requests must use APKMirror. */
    private boolean apkPureCanServe(String fileType) {
        return !"apkm".equalsIgnoreCase(fileType)
                && !"apks".equalsIgnoreCase(fileType);
    }

    private boolean sourceAllowed(String key) {
        return SourceSettings.isEnabled(this, key)
                && (retrySources == null || retrySources.contains(key));
    }

    private long installedVersionCode(String pkg) {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(pkg, 0);
            if (Build.VERSION.SDK_INT >= 28) {
                return info.getLongVersionCode();
            }
            return info.versionCode;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private File resolve(String pkg, String version, long[] versionCodes,
                         String fileType, String[] abis) throws IOException {
        long[] codes = versionCodes;
        if (codes == null || codes.length == 0) {
            setStatus("Checking installed version...");
            long installed = installedVersionCode(pkg);
            if (installed > 0) {
                codes = new long[]{installed};
                HelperLog.info("Using installed versionCode=" + installed + " for " + pkg);
            }
        }

        // Fast start: with a numeric code, APKPure can often begin immediately.
        // The resolver and the other sources remain available as fallbacks.
        // Fast start only applies to codes Morphe actually supplied. If Morphe omits
        // them, follow its resolver link first even if an installed code is available.
        boolean hasCodes = versionCodes != null && versionCodes.length > 0;
        if (!AppSettings.sourceOrder(this).equals(AppSettings.defaultOrder())) {
            return resolveCustom(pkg, version, codes, hasCodes, fileType, abis);
        }
        if (hasCodes && sourceAllowed(SourceSettings.APKPURE)
                && apkPureCanServe(fileType)) {
            setSourceLabel("APKPure direct");
            setStatus("Trying APKPure direct...");
            try {
                File file = downloadFromApkPure(pkg, version, codes, fileType);
                markSource(SourceSettings.APKPURE, true, "direct");
                return file;
            } catch (Exception directError) {
                markSource(SourceSettings.APKPURE, false,
                        "direct: " + directError.getMessage());
                HelperLog.error("APKPure fast direct failed", directError);
            }

            setSourceLabel("APKPure API");
            setStatus("Resolving APKPure API...");
            try {
                File file = downloadFromApkPureApi(pkg, version, codes, fileType, abis);
                markSource(SourceSettings.APKPURE, true, "API");
                return file;
            } catch (Exception apiError) {
                HelperLog.error("APKPure fast API failed", apiError);
                markSource(SourceSettings.APKPURE, false, apiError.getMessage());
            }
        }

        // 0. Ask Morphe's resolver for the page it would show in its own dialog.
        if (sourceAllowed(SourceSettings.FALLBACK)) {
            String abi = (abis != null && abis.length > 0) ? abis[0] : "arm64-v8a";
            String resolverKey = pkg + "|" + version + "|" + abi;
            String morpheUrl = ResolverCache.get(this, resolverKey);
            if (morpheUrl != null) {
                HelperLog.info("Using cached Morphe resolver link: " + morpheUrl);
                setStatus("Using cached resolver...");
            } else {
                setStatus("Checking Morphe resolver...");
                morpheUrl = resolveMorpheUrl(pkg, version, abis);
                if (morpheUrl != null && !isHost(hostOf(morpheUrl), "google.com")) {
                    ResolverCache.put(this, resolverKey, morpheUrl);
                }
            }
            final String resolvedUrl = morpheUrl;
            if (resolvedUrl != null) {
                String host = hostOf(resolvedUrl);
                HelperLog.info("Morphe resolver returned: " + resolvedUrl);
                setStatus("Resolver found " + host + "...");
                if (isHost(host, "uptodown.com")) {
                    if (pathOf(morpheUrl).trim().endsWith("-x")
                            && sourceAllowed(SourceSettings.UPTODOWN)) {
                        markSource(SourceSettings.FALLBACK, true, "Opened Uptodown");
                        main.post(() -> {
                            setStatus("Opening Uptodown...");
                            progress.setIndeterminate(true);
                            startUptodownWebFlow(resolvedUrl, pkg, version, fileType);
                        });
                        throw new UptodownHandoffException();
                    }
                } else if (isHost(host, "apkpure.com")
                        && sourceAllowed(SourceSettings.APKPURE)) {
                    try {
                        File file = downloadFromApkPureApi(pkg, version, codes, fileType, abis);
                        markSource(SourceSettings.FALLBACK, true, "Resolver APKPure");
                        return file;
                    } catch (Exception ignored) {
                        HelperLog.error("Morphe resolver APKPure link failed", ignored);
                    }
                } else if (isHost(host, "apkmirror.com")
                        && sourceAllowed(SourceSettings.APKMIRROR)) {
                    setSourceLabel("Morphe resolver");
                    try {
                        File file = downloadFromFallback(morpheUrl, pkg, version, fileType, abis);
                        markSource(SourceSettings.FALLBACK, true, "Resolver APKMirror");
                        return file;
                    } catch (Exception ignored) {
                        HelperLog.error("Morphe resolver APKMirror link failed", ignored);
                    }
                } else if (isDirectFile(morpheUrl)) {
                    try {
                        File file = downloadFile(morpheUrl, null, pkg, version, fileType);
                        markSource(SourceSettings.FALLBACK, true, "Resolver direct file");
                        return file;
                    } catch (Exception ignored) {
                        HelperLog.error("Morphe resolver direct file failed", ignored);
                    }
                }
                markSource(SourceSettings.FALLBACK, false, "Resolver did not produce a file");
            } else {
                markSource(SourceSettings.FALLBACK, false, "No resolver result");
            }
        }

        // 1. Fallback URL from the patch bundle first.
        if (sourceAllowed(SourceSettings.FALLBACK)
                && !TextUtils.isEmpty(fallbackUrl)) {
            String fallbackHost = hostOf(fallbackUrl);
            boolean hostDisabled =
                    (isHost(fallbackHost, "apkmirror.com")
                            && !sourceAllowed(SourceSettings.APKMIRROR))
                            || (isHost(fallbackHost, "apkpure.com")
                            && !sourceAllowed(SourceSettings.APKPURE))
                            || (isHost(fallbackHost, "uptodown.com")
                            && !sourceAllowed(SourceSettings.UPTODOWN));
            if (!hostDisabled) {
                setSourceLabel("Morphe fallback");
                setStatus("Trying fallback URL...");
                HelperLog.info("Trying fallback URL: " + fallbackUrl);
                try {
                    File file = downloadFromFallback(fallbackUrl, pkg, version, fileType, abis);
                    markSource(SourceSettings.FALLBACK, true, "fallback link");
                    return file;
                } catch (Exception failed) {
                    HelperLog.error("Fallback failed", failed);
                    markSource(SourceSettings.FALLBACK, false, failed.getMessage());
                }
            } else {
                HelperLog.info("Skipping fallback from disabled source: " + fallbackHost);
            }
        }

        // 2. APKMirror.
        if (sourceAllowed(SourceSettings.APKMIRROR)) {
            setSourceLabel("APKMirror");
            setStatus("Searching APKMirror...");
            HelperLog.info("Trying APKMirror search for " + pkg);
            try {
                File file = downloadFromApkMirrorSearch(pkg, version, fileType, abis);
                markSource(SourceSettings.APKMIRROR, true, "search");
                return file;
            } catch (Exception failed) {
                HelperLog.error("APKMirror failed", failed);
                markSource(SourceSettings.APKMIRROR, false, failed.getMessage());
            }
        }

        // Bundle requests need an APK/XAPK split archive. APKPure cannot serve APKM
        // directly, but its XAPK asset is a useful fallback if APKMirror is blocked.
        if (sourceAllowed(SourceSettings.APKPURE)
                && allowSplitArchive && isSplitType(fileType)
                && !apkPureCanServe(fileType)) {
            HelperLog.info("Trying APKPure XAPK fallback for " + pkg);
            setSourceLabel("APKPure XAPK");
            setStatus("Trying APKPure XAPK fallback...");
            try {
                File file = downloadFromApkPureApi(pkg, version, codes, fileType, abis);
                markSource(SourceSettings.APKPURE, true, "XAPK fallback");
                return file;
            } catch (Exception failed) {
                HelperLog.error("APKPure XAPK fallback failed", failed);
                markSource(SourceSettings.APKPURE, false, failed.getMessage());
            }
        }

        // 3. APKPure fallback for requests without a numeric versionCode.
        // When a code exists it was already tried before the resolver.
        if (!hasCodes && sourceAllowed(SourceSettings.APKPURE)
                && apkPureCanServe(fileType)) {
            HelperLog.info("Trying APKPure fallback for " + pkg);
            setSourceLabel("APKPure API");
            setStatus("Resolving APKPure API...");
            try {
                File file = downloadFromApkPureApi(pkg, version, codes, fileType, abis);
                markSource(SourceSettings.APKPURE, true, "API");
                return file;
            } catch (Exception apiError) {
                markSource(SourceSettings.APKPURE, false, "API: " + apiError.getMessage());
                HelperLog.error("APKPure API failed", apiError);
                setSourceLabel("APKPure direct");
                setStatus("Trying APKPure direct...");
                try {
                    File file = downloadFromApkPure(pkg, version, codes, fileType);
                    markSource(SourceSettings.APKPURE, true, "direct");
                    return file;
                } catch (Exception directError) {
                    HelperLog.error("APKPure direct failed", directError);
                    markSource(SourceSettings.APKPURE, false, "direct: " + directError.getMessage());
                }
            }
        }

        // 4. F-Droid.
        if (sourceAllowed(SourceSettings.FDROID)) {
            setSourceLabel("F-Droid");
            setStatus("Checking F-Droid...");
            HelperLog.info("Trying F-Droid for " + pkg);
            try {
                File file = downloadFromFdroid(pkg, version, codes, fileType);
                markSource(SourceSettings.FDROID, true, "package API");
                return file;
            } catch (Exception failed) {
                HelperLog.error("F-Droid failed", failed);
                markSource(SourceSettings.FDROID, false, failed.getMessage());
            }
        }

        // 5. Aptoide.
        if (sourceAllowed(SourceSettings.APTOIDE)) {
            setSourceLabel("Aptoide");
            setStatus("Searching Aptoide...");
            HelperLog.info("Trying Aptoide for " + pkg);
            try {
                File file = downloadFromAptoide(pkg, version, codes, fileType);
                markSource(SourceSettings.APTOIDE, true, "TRUSTED upload");
                return file;
            } catch (Exception failed) {
                HelperLog.error("Aptoide failed", failed);
                markSource(SourceSettings.APTOIDE, false, failed.getMessage());
            }
        }

        if (sourceAllowed(SourceSettings.APPTEKA)
                && !isSplitType(fileType)
                && AppSettings.apptekaAllowed(this, pkg)) {
            setSourceLabel("Appteka");
            setStatus("Searching Appteka...");
            HelperLog.info("Trying Appteka for " + pkg);
            try {
                File file = downloadFromAppteka(pkg, version, codes);
                markSource(SourceSettings.APPTEKA, true, "catalog");
                return file;
            } catch (Exception failed) {
                HelperLog.error("Appteka failed", failed);
                markSource(SourceSettings.APPTEKA, false, failed.getMessage());
            }
        }

        // APKPure browser fallback is the last resort so a broken page does not block
        // the resolver or APKMirror from being tried.
        if (allowSplitArchive && sourceAllowed(SourceSettings.APKPURE)
                && apkPureCanServe(fileType)) {
            main.post(() -> {
                setStatus("Opening APKPure page...");
                progress.setIndeterminate(true);
                startApkPureWebFlow(pkg, version, fileType);
            });
            throw new UptodownHandoffException();
        }

        throw new IOException("No enabled download source found an APK");
    }

    private File resolveCustom(String pkg, String version, long[] codes, boolean hasCodes,
                               String fileType, String[] abis) throws IOException {
        for (String source : AppSettings.sourceOrder(this)) {
            if (!sourceAllowed(source)) {
                continue;
            }
            File file = null;
            if (SourceSettings.APKPURE.equals(source)) {
                file = customApkPure(pkg, version, codes, hasCodes, fileType, abis);
            } else if (SourceSettings.FALLBACK.equals(source)) {
                file = customMorphe(pkg, version, codes, fileType, abis);
            } else if (SourceSettings.APKMIRROR.equals(source)) {
                file = customApkMirror(pkg, version, fileType, abis);
            } else if (SourceSettings.FDROID.equals(source)) {
                file = customFdroid(pkg, version, codes, fileType);
            } else if (SourceSettings.APTOIDE.equals(source)) {
                file = customAptoide(pkg, version, codes, fileType);
            } else if (SourceSettings.APPTEKA.equals(source)) {
                file = customAppteka(pkg, version, codes, fileType);
            }
            if (file != null) {
                return file;
            }
        }

        if (sourceAllowed(SourceSettings.APKPURE)
                && allowSplitArchive && isSplitType(fileType)
                && !apkPureCanServe(fileType)) {
            HelperLog.info("Trying APKPure XAPK fallback for " + pkg);
            setSourceLabel("APKPure XAPK");
            setStatus("Trying APKPure XAPK fallback...");
            try {
                File file = downloadFromApkPureApi(pkg, version, codes, fileType, abis);
                markSource(SourceSettings.APKPURE, true, "XAPK fallback");
                return file;
            } catch (Exception failed) {
                HelperLog.error("APKPure XAPK fallback failed", failed);
                markSource(SourceSettings.APKPURE, false, failed.getMessage());
            }
        }

        if (!hasCodes && sourceAllowed(SourceSettings.APKPURE)
                && apkPureCanServe(fileType)) {
            HelperLog.info("Trying APKPure fallback for " + pkg);
            setSourceLabel("APKPure API");
            setStatus("Resolving APKPure API...");
            try {
                File file = downloadFromApkPureApi(pkg, version, codes, fileType, abis);
                markSource(SourceSettings.APKPURE, true, "API");
                return file;
            } catch (Exception apiError) {
                markSource(SourceSettings.APKPURE, false, "API: " + apiError.getMessage());
                HelperLog.error("APKPure API failed", apiError);
                setSourceLabel("APKPure direct");
                setStatus("Trying APKPure direct...");
                try {
                    File file = downloadFromApkPure(pkg, version, codes, fileType);
                    markSource(SourceSettings.APKPURE, true, "direct");
                    return file;
                } catch (Exception directError) {
                    HelperLog.error("APKPure direct failed", directError);
                    markSource(SourceSettings.APKPURE, false, "direct: " + directError.getMessage());
                }
            }
        }

        if (allowSplitArchive && sourceAllowed(SourceSettings.APKPURE)
                && (apkPureCanServe(fileType) || isSplitType(fileType))) {
            main.post(() -> {
                setStatus("Opening APKPure page...");
                progress.setIndeterminate(true);
                startApkPureWebFlow(pkg, version, fileType);
            });
            throw new UptodownHandoffException();
        }
        throw new IOException("No enabled download source found an APK");
    }

    private File customApkPure(String pkg, String version, long[] codes, boolean hasCodes,
                               String fileType, String[] abis) {
        if (!hasCodes || !apkPureCanServe(fileType)) {
            return null;
        }
        setSourceLabel("APKPure direct");
        setStatus("Trying APKPure direct...");
        try {
            File file = downloadFromApkPure(pkg, version, codes, fileType);
            markSource(SourceSettings.APKPURE, true, "direct");
            return file;
        } catch (Exception directError) {
            markSource(SourceSettings.APKPURE, false,
                    "direct: " + directError.getMessage());
            HelperLog.error("APKPure fast direct failed", directError);
        }
        setSourceLabel("APKPure API");
        setStatus("Resolving APKPure API...");
        try {
            File file = downloadFromApkPureApi(pkg, version, codes, fileType, abis);
            markSource(SourceSettings.APKPURE, true, "API");
            return file;
        } catch (Exception apiError) {
            HelperLog.error("APKPure fast API failed", apiError);
            markSource(SourceSettings.APKPURE, false, apiError.getMessage());
            return null;
        }
    }

    private File customMorphe(String pkg, String version, long[] codes,
                              String fileType, String[] abis) throws IOException {
        String abi = (abis != null && abis.length > 0) ? abis[0] : "arm64-v8a";
        String key = pkg + "|" + version + "|" + abi;
        String url = ResolverCache.get(this, key);
        if (url != null) {
            setStatus("Using cached resolver...");
        } else {
            setStatus("Checking Morphe resolver...");
            url = resolveMorpheUrl(pkg, version, abis);
            if (url != null && !isHost(hostOf(url), "google.com")) {
                ResolverCache.put(this, key, url);
            }
        }
        if (url != null) {
            String host = hostOf(url);
            setStatus("Resolver found " + host + "...");
            if (isHost(host, "uptodown.com") && pathOf(url).trim().endsWith("-x")
                    && sourceAllowed(SourceSettings.UPTODOWN)) {
                markSource(SourceSettings.FALLBACK, true, "Opened Uptodown");
                final String resolved = url;
                main.post(() -> {
                    setStatus("Opening Uptodown...");
                    progress.setIndeterminate(true);
                    startUptodownWebFlow(resolved, pkg, version, fileType);
                });
                throw new UptodownHandoffException();
            }
            if (isHost(host, "apkpure.com") && apkPureCanServe(fileType)
                    && sourceAllowed(SourceSettings.APKPURE)) {
                try {
                    File file = downloadFromApkPureApi(pkg, version, codes, fileType, abis);
                    markSource(SourceSettings.FALLBACK, true, "Resolver APKPure");
                    return file;
                } catch (Exception failed) {
                    HelperLog.error("Morphe resolver APKPure link failed", failed);
                }
            } else if (isHost(host, "apkmirror.com")
                    && sourceAllowed(SourceSettings.APKMIRROR)) {
                try {
                    File file = downloadFromFallback(url, pkg, version, fileType, abis);
                    markSource(SourceSettings.FALLBACK, true, "Resolver APKMirror");
                    return file;
                } catch (Exception failed) {
                    HelperLog.error("Morphe resolver APKMirror link failed", failed);
                }
            } else if (isDirectFile(url)) {
                try {
                    File file = downloadFile(url, null, pkg, version, fileType);
                    markSource(SourceSettings.FALLBACK, true, "Resolver direct file");
                    return file;
                } catch (Exception failed) {
                    HelperLog.error("Morphe resolver direct file failed", failed);
                }
            }
            markSource(SourceSettings.FALLBACK, false, "Resolver did not produce a file");
        } else {
            markSource(SourceSettings.FALLBACK, false, "No resolver result");
        }

        if (!TextUtils.isEmpty(fallbackUrl)) {
            String host = hostOf(fallbackUrl);
            boolean disabled = (isHost(host, "apkmirror.com")
                    && !sourceAllowed(SourceSettings.APKMIRROR))
                    || (isHost(host, "apkpure.com")
                    && !sourceAllowed(SourceSettings.APKPURE))
                    || (isHost(host, "uptodown.com")
                    && !sourceAllowed(SourceSettings.UPTODOWN));
            if (!disabled) {
                setStatus("Trying fallback URL...");
                try {
                    File file = downloadFromFallback(fallbackUrl, pkg, version, fileType, abis);
                    markSource(SourceSettings.FALLBACK, true, "fallback link");
                    return file;
                } catch (Exception failed) {
                    HelperLog.error("Fallback failed", failed);
                    markSource(SourceSettings.FALLBACK, false, failed.getMessage());
                }
            }
        }
        return null;
    }

    private File customApkMirror(String pkg, String version, String fileType, String[] abis) {
        setSourceLabel("APKMirror");
        setStatus("Searching APKMirror...");
        try {
            File file = downloadFromApkMirrorSearch(pkg, version, fileType, abis);
            markSource(SourceSettings.APKMIRROR, true, "search");
            return file;
        } catch (Exception failed) {
            HelperLog.error("APKMirror failed", failed);
            markSource(SourceSettings.APKMIRROR, false, failed.getMessage());
            return null;
        }
    }

    private File customFdroid(String pkg, String version, long[] codes, String fileType) {
        setSourceLabel("F-Droid");
        setStatus("Checking F-Droid...");
        try {
            File file = downloadFromFdroid(pkg, version, codes, fileType);
            markSource(SourceSettings.FDROID, true, "package API");
            return file;
        } catch (Exception failed) {
            HelperLog.error("F-Droid failed", failed);
            markSource(SourceSettings.FDROID, false, failed.getMessage());
            return null;
        }
    }

    private File customAptoide(String pkg, String version, long[] codes, String fileType) {
        setSourceLabel("Aptoide");
        setStatus("Searching Aptoide...");
        try {
            File file = downloadFromAptoide(pkg, version, codes, fileType);
            markSource(SourceSettings.APTOIDE, true, "TRUSTED upload");
            return file;
        } catch (Exception failed) {
            HelperLog.error("Aptoide failed", failed);
            markSource(SourceSettings.APTOIDE, false, failed.getMessage());
            return null;
        }
    }

    private File customAppteka(String pkg, String version, long[] codes, String fileType) {
        if (isSplitType(fileType) || !AppSettings.apptekaAllowed(this, pkg)) {
            return null;
        }
        setSourceLabel("Appteka");
        setStatus("Searching Appteka...");
        try {
            File file = downloadFromAppteka(pkg, version, codes);
            markSource(SourceSettings.APPTEKA, true, "catalog");
            return file;
        } catch (Exception failed) {
            HelperLog.error("Appteka failed", failed);
            markSource(SourceSettings.APPTEKA, false, failed.getMessage());
            return null;
        }
    }

    private File downloadFromAppteka(String pkg, String version, long[] versionCodes)
            throws IOException {
        String lookupUrl = APPTEKA_API + "?package="
                + URLEncoder.encode(pkg, "UTF-8") + "&locale=en";
        JSONObject result = apptekaResult(httpGet(lookupUrl, null));
        JSONObject info = result.optJSONObject("info");
        if (info == null || !pkg.equals(info.optString("package"))) {
            throw new IOException("Appteka returned a different package");
        }

        boolean noVersionRequested = TextUtils.isEmpty(version)
                && (versionCodes == null || versionCodes.length == 0);
        boolean infoMatches = noVersionRequested
                || sameVersion(info.optString("ver_name"), version)
                || contains(versionCodes, info.optLong("ver_code", -1));

        JSONObject selectedByCode = null;
        JSONObject selectedByName = null;
        JSONArray versions = result.optJSONArray("versions");
        if (versions != null) {
            for (int i = 0; i < versions.length(); i++) {
                JSONObject candidate = versions.optJSONObject(i);
                if (candidate == null) {
                    continue;
                }
                if (contains(versionCodes, candidate.optLong("ver_code", -1))) {
                    selectedByCode = candidate;
                    break;
                }
                if (selectedByName == null
                        && sameVersion(candidate.optString("ver_name"), version)) {
                    selectedByName = candidate;
                }
            }
        }

        JSONObject selected = selectedByCode != null ? selectedByCode : selectedByName;
        String link;
        if (selected != null
                && !selected.optString("app_id").equals(info.optString("app_id"))) {
            String appId = selected.optString("app_id");
            String versionUrl = APPTEKA_API + "?app_id="
                    + URLEncoder.encode(appId, "UTF-8") + "&locale=en";
            JSONObject selectedResult = apptekaResult(httpGet(versionUrl, null));
            JSONObject selectedInfo = selectedResult.optJSONObject("info");
            if (selectedInfo == null || !pkg.equals(selectedInfo.optString("package"))) {
                throw new IOException("Appteka version has a different package");
            }
            link = selectedResult.optString("link");
        } else {
            if (!infoMatches && !TextUtils.isEmpty(version)) {
                throw new IOException("Requested version not found on Appteka");
            }
            link = result.optString("link");
        }

        link = decodeHtml(link);
        if (TextUtils.isEmpty(link) || !isHost(hostOf(link), "appteka.store")
                || !link.contains("/download/")) {
            throw new IOException("Appteka did not return a download link");
        }
        HelperLog.info("Appteka link for " + pkg + ": " + link);
        return downloadFile(link, "https://appteka.store/", pkg, version, "apk");
    }

    private JSONObject apptekaResult(String body) throws IOException {
        try {
            JSONObject root = new JSONObject(body);
            JSONObject result = root.optJSONObject("result");
            if (result == null) {
                throw new IOException("Appteka returned no app data");
            }
            return result;
        } catch (JSONException e) {
            throw new IOException("Appteka response parse failed", e);
        }
    }

    /**
     * Handles a fallback URL supplied by Morphe.
     *
     * <p>Supported forms:</p>
     * <ul>
     *   <li>Direct file URL ending in .apk/.apkm/.apks/.xapk</li>
     *   <li>APKMirror download, key, variant, release, app, or search page</li>
     *   <li>Uptodown page (handled by the WebView flow)</li>
     * </ul>
     */
    private File downloadFromFallback(String fallback, String pkg, String version,
                                      String fileType, String[] abis) throws IOException {
        String url = decodeHtml(fallback.trim());
        HelperLog.info("Fallback resolved to " + url);

        // A direct file can be streamed immediately; no HTML parsing is needed.
        if (isDirectFile(url)) {
            HelperLog.info("Fallback is a direct file");
            return downloadFile(url, null, pkg, version, fileType);
        }

        String lower = url.toLowerCase(Locale.US);
        String host = hostOf(url);
        HelperLog.info("Fallback host: " + host);

        // Uptodown support is kept for future use but disabled at the feature-switch level.
        if (isHost(host, "uptodown.com")) {
            HelperLog.info("Fallback host is Uptodown");
            return downloadFromUptodown(url, pkg, version, fileType);
        }

        if (isHost(host, "apkmirror.com")) {
            HelperLog.info("Fallback host is APKMirror");

            // /download.php?... is already the final file endpoint.
            if (lower.contains("/download.php?")) {
                return downloadFile(url, null, pkg, version, fileType);
            }

            // /download/?key=... is the intermediate key page.
            if (lower.contains("/download/?key=")) {
                return downloadFromKeyPage(url, pkg, version, fileType);
            }

            // -android-apk-download/ is a variant page (APK/APKM/APKS choices).
            if (lower.contains("-android-apk-download/")) {
                return downloadFromVariantPage(url, pkg, version, fileType);
            }

            // -release/ is a version page; pick the best variant for the requested type.
            if (lower.contains("-release/")) {
                String variant = chooseVariant(httpGet(url, null), fileType, abis);
                if (variant == null) throw new IOException("No APKMirror variant found");
                return downloadFromVariantPage(absolute(variant), pkg, version, fileType);
            }

            // /apk/<developer>/<app>/ is an app page; find the requested version first.
            if (lower.matches(".*/apk/[^/]+/[^/]+/?$")) {
                String appPath = pathOf(url);
                String appHtml = httpGet(url, null);
                String versionUrl = findVersionUrl(appHtml, appPath, version);

                // If the version is not listed, construct the slug from the latest release.
                if (versionUrl == null && !TextUtils.isEmpty(version)) {
                    versionUrl = findExactVersionUrl(appPath, appHtml, version, url);
                }
                if (versionUrl == null) throw new IOException("No APKMirror version found");

                String variant = chooseVariant(httpGet(absolute(versionUrl), url), fileType, abis);
                if (variant == null) throw new IOException("No APKMirror variant found");
                return downloadFromVariantPage(absolute(variant), pkg, version, fileType);
            }

            // Search URL or anything else: resolve from the first result.
            String firstVersion = findFirstVersionUrl(httpGet(url, null));
            if (firstVersion != null) {
                String appUrl = firstVersion.replaceAll("/[^/]+/$", "/");
                String appHtml = httpGet(absolute(appUrl), url);
                String appVersion = findVersionUrl(appHtml, appUrl, version);
                if (appVersion == null && !TextUtils.isEmpty(version)) {
                    appVersion = findExactVersionUrl(appUrl, appHtml, version, absolute(appUrl));
                }
                if (appVersion != null) {
                    String variant = chooseVariant(
                            httpGet(absolute(appVersion), absolute(appUrl)), fileType, abis);
                    if (variant != null) {
                        return downloadFromVariantPage(absolute(variant), pkg, version, fileType);
                    }
                }
            }
        }

        // Unknown host or unsupported page shape.
        throw new IOException("Fallback URL is not a supported APK source");
    }

    /**
     * Legacy direct-HTML Uptodown resolver. The active Uptodown flow uses the WebView
     * path because current pages require Cloudflare Turnstile.
     */
    private File downloadFromUptodown(String url, String pkg, String version,
                                      String fileType) throws IOException {
        // Uptodown uses /download on the app page.
        String pageUrl = url;
        if (!pageUrl.toLowerCase(Locale.US).contains("/download")) {
            pageUrl = pageUrl.replaceAll("/$", "") + "/download";
        }

        HelperLog.info("Uptodown page: " + pageUrl);
        String html = httpGet(pageUrl, null);
        String tag = firstMatch(html, "(<button[^>]*id=\"detail-download-button\"[^>]*>)");
        HelperLog.info("Uptodown download button found=" + (tag != null));
        if (tag == null) {
            throw new IOException("Uptodown download button not found");
        }

        // Some pages expose a direct URL; others expose only a path for dw.uptodown.com.
        String directUrl = firstMatch(tag, "data-url-ext=\"([^\"]+)\"");
        if (directUrl != null) {
            HelperLog.info("Uptodown direct URL found");
            return downloadFile(decodeHtml(directUrl), pageUrl, pkg, version, fileType);
        }

        String path = firstMatch(tag, "data-url=\"([^\"]+)\"");
        if (path != null) {
            HelperLog.info("Uptodown dwn path=" + path);
            return downloadFile("https://dw.uptodown.com/dwn/" + decodeHtml(path),
                    pageUrl, pkg, version, fileType);
        }

        HelperLog.info("Uptodown has no data-url (Turnstile required)");

        // Uptodown's API needs a Cloudflare Turnstile token, which cannot be generated
        // from a plain HTTP request. Fall through to APKMirror/APKPure.
        throw new IOException("Uptodown requires Turnstile");
    }

    /**
     * Searches APKMirror by package name, opens the app page, selects the requested
     * version, and chooses a compatible APK/APKM variant.
     */
    private File downloadFromApkMirrorSearch(String pkg, String version,
                                             String fileType, String[] abis) throws IOException {
        // APKMirror's search endpoint accepts the package name as a query parameter.
        String searchUrl = APKMIRROR + "/?post_type=app_release&searchtype=apk&s="
                + URLEncoder.encode(pkg, "UTF-8");
        HelperLog.info("APKMirror search URL: " + searchUrl);
        String searchHtml = httpGet(searchUrl, null);
        String firstVersion = findFirstVersionUrl(searchHtml);
        HelperLog.info("APKMirror first version URL: " + firstVersion);
        if (firstVersion == null) {
            throw new IOException("APKMirror search returned no results");
        }

        // A release URL looks like /apk/<dev>/<app>/<version>-release/. Strip the
        // version segment to get the app page.
        String appUrl = firstVersion.replaceAll("/[^/]+/$", "/");
        HelperLog.info("APKMirror app URL: " + appUrl);
        String appHtml = httpGet(absolute(appUrl), searchUrl);
        HelperLog.info("APKMirror app HTML length=" + appHtml.length()
                + " fontBlack=" + appHtml.contains("fontBlack")
                + " release=" + appHtml.contains("-release/"));

        // Prefer a listed version; if it is missing, try constructing the exact slug.
        String versionUrl = findVersionUrl(appHtml, appUrl, version);
        if (versionUrl == null && !TextUtils.isEmpty(version)) {
            versionUrl = findExactVersionUrl(appUrl, appHtml, version, absolute(appUrl));
        }
        HelperLog.info("APKMirror version URL: " + versionUrl);
        if (versionUrl == null) {
            throw new IOException(TextUtils.isEmpty(version)
                    ? "No matching version on APKMirror"
                    : "Requested version " + version + " not found on APKMirror");
        }

        // The version page lists APK/APKM/APKS variants; choose the best one.
        String versionHtml = httpGet(absolute(versionUrl), absolute(appUrl));
        String variant = chooseVariant(versionHtml, fileType, abis);
        HelperLog.info("APKMirror variant URL: " + variant);
        if (variant == null) {
            throw new IOException("No download variant on APKMirror");
        }
        return downloadFromVariantPage(absolute(variant), pkg, version, fileType);
    }

    /**
     * Opens an APKMirror variant page and follows its download button to the key page.
     */
    private File downloadFromVariantPage(String variantUrl, String pkg, String version,
                                         String fileType) throws IOException {
        HelperLog.info("APKMirror variant page: " + variantUrl);
        setStatus("Checking APKMirror page...");
        String html = httpGet(variantUrl, null);

        // Newer APKMirror layouts use a downloadButton link; older layouts use the
        // /download/?key= pattern. Try both before giving up.
        String keyUrl = firstMatch(html,
                "class=\"[^\"]*downloadButton[^\"]*\"[^>]*href=\"([^\"]+)\"");
        if (keyUrl == null) {
            keyUrl = firstMatch(html, "href=\"([^\"]*download/\\?key=[^\"]+)\"");
        }
        HelperLog.info("APKMirror key URL: " + keyUrl);
        if (keyUrl == null) {
            throw new IOException("APKMirror download button not found");
        }
        return downloadFromKeyPage(absolute(decodeHtml(keyUrl)), pkg, version, fileType);
    }

    /**
     * Opens the APKMirror key page and extracts the final download.php link.
     */
    private File downloadFromKeyPage(String keyUrl, String pkg, String version,
                                     String fileType) throws IOException {
        HelperLog.info("APKMirror key page: " + keyUrl);
        setStatus("Fetching APKMirror download key...");
        String html = httpGet(keyUrl, null);

        // The final link is normally id="download-link"; keep a fallback regex for
        // layout changes.
        String fileUrl = firstMatch(html,
                "id=\"download-link\"[^>]*href=\"([^\"]+)\"");
        if (fileUrl == null) {
            fileUrl = firstMatch(html,
                    "href=\"(/wp-content/themes/APKMirror/download\\.php\\?id=[^\"]+)\"");
        }
        HelperLog.info("APKMirror final URL: " + fileUrl);
        if (fileUrl == null) {
            throw new IOException("APKMirror final download link not found");
        }
        setStatus("Starting APKMirror download...");
        return downloadFile(absolute(decodeHtml(fileUrl)), keyUrl, pkg, version, fileType);
    }

    /**
     * Downloads from F-Droid's public package API.
     */
    private File downloadFromFdroid(String pkg, String version, long[] versionCodes,
                                    String fileType) throws IOException {
        String apiUrl = "https://f-droid.org/api/v1/packages/"
                + URLEncoder.encode(pkg, "UTF-8");
        String json = httpGet(apiUrl, null);
        try {
            JSONObject root = new JSONObject(json);
            JSONArray packages = root.optJSONArray("packages");
            if (packages == null || packages.length() == 0) {
                throw new IOException("F-Droid has no packages");
            }

            JSONObject selected = null;
            for (int i = 0; i < packages.length(); i++) {
                JSONObject entry = packages.getJSONObject(i);
                String entryVersion = entry.optString("versionName", "");
                long entryCode = entry.optLong("versionCode", -1);
                if (!TextUtils.isEmpty(version) && entryVersion.equalsIgnoreCase(version)) {
                    selected = entry;
                    break;
                }
                if (versionCodes != null && contains(versionCodes, entryCode)) {
                    selected = entry;
                    break;
                }
            }

            if (selected == null && TextUtils.isEmpty(version)) {
                int suggested = root.optInt("suggestedVersionCode", -1);
                for (int i = 0; i < packages.length(); i++) {
                    JSONObject entry = packages.getJSONObject(i);
                    if (entry.optLong("versionCode", -1) == suggested) {
                        selected = entry;
                        break;
                    }
                }
                if (selected == null) {
                    selected = packages.getJSONObject(0);
                }
            }

            if (selected == null) {
                throw new IOException("Requested version not found on F-Droid");
            }
            long selectedCode = selected.optLong("versionCode", -1);
            String apkName = selected.optString("apkName", "");
            if (TextUtils.isEmpty(apkName)) {
                if (selectedCode <= 0) {
                    throw new IOException("F-Droid package has no APK");
                }
                apkName = pkg + "_" + selectedCode + ".apk";
            }
            return downloadFile("https://f-droid.org/repo/" + apkName,
                    "https://f-droid.org", pkg, version, fileType);
        } catch (JSONException e) {
            throw new IOException("F-Droid response parse failed", e);
        }
    }

    /**
     * Downloads from Aptoide's public v7 API, only accepting TRUSTED uploads.
     */
    private File downloadFromAptoide(String pkg, String version, long[] versionCodes,
                                     String fileType) throws IOException {
        try {
            String searchUrl = "https://ws75.aptoide.com/api/7/apps/search?query="
                    + URLEncoder.encode(pkg, "UTF-8") + "&limit=10";
            JSONObject search = new JSONObject(httpGet(searchUrl, null));
            JSONObject app = findAptoideApp(search, pkg);
            if (app == null) {
                throw new IOException("Aptoide search returned no app");
            }

            JSONObject file = app.optJSONObject("file");
            if (!aptoideFileMatches(file, version, versionCodes)) {
                String versionsUrl = "https://ws75.aptoide.com/api/7/listAppVersions?package_name="
                        + URLEncoder.encode(pkg, "UTF-8") + "&limit=50";
                JSONObject versions = new JSONObject(httpGet(versionsUrl, null));
                JSONArray list = versions.optJSONArray("list");
                if (list == null) {
                    throw new IOException("Aptoide has no versions");
                }
                file = null;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject candidate = list.getJSONObject(i).optJSONObject("file");
                    if (aptoideFileMatches(candidate, version, versionCodes)) {
                        file = candidate;
                        break;
                    }
                }
            }

            if (file == null) {
                throw new IOException("Requested version not found on Aptoide");
            }
            JSONObject malware = file.optJSONObject("malware");
            String rank = malware != null ? malware.optString("rank", "") : "";
            if (!"TRUSTED".equalsIgnoreCase(rank)) {
                throw new IOException("Aptoide file is not TRUSTED");
            }

            String path = file.optString("path", file.optString("path_alt", ""));
            if (TextUtils.isEmpty(path)) {
                throw new IOException("Aptoide file has no download path");
            }
            return downloadFile(path, "https://aptoide.com", pkg, version, fileType);
        } catch (JSONException e) {
            throw new IOException("Aptoide response parse failed", e);
        }
    }

    private JSONObject findAptoideApp(JSONObject root, String pkg) throws JSONException {
        JSONObject data = root.optJSONObject("datalist");
        JSONArray list = data != null ? data.optJSONArray("list") : null;
        if (list == null) {
            return null;
        }
        for (int i = 0; i < list.length(); i++) {
            JSONObject app = list.getJSONObject(i);
            if (pkg.equals(app.optString("package"))) {
                return app;
            }
        }
        return null;
    }

    private boolean aptoideFileMatches(JSONObject file, String version, long[] versionCodes) {
        if (file == null) {
            return false;
        }
        if (TextUtils.isEmpty(version)) {
            return true;
        }
        String entryVersion = file.optString("vername", "");
        long entryCode = file.optLong("vercode", -1);
        return entryVersion.equalsIgnoreCase(version)
                || (versionCodes != null && contains(versionCodes, entryCode));
    }

    private boolean contains(long[] values, long target) {
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
     * Resolves the exact APKPure version through APKPure's mobile API and downloads its
     * asset directly. This avoids the WebView and its ads in the common case.
     */
    private File downloadFromApkPureApi(String pkg, String version, long[] versionCodes,
                                        String fileType, String[] abis) throws IOException {
        String url = APKPURE_API + "?package_name="
                + URLEncoder.encode(pkg, "UTF-8") + "&hl=en";
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "APKPure/3.20.0 (Android 14)")
                .header("Ual-Access-Businessid", "projecta")
                .header("Ual-Access-ProjectA", "{\"device_info\":{\"os_ver\":\"34\"}}")
                .header("Accept", "application/json")
                .build();

        HelperLog.info("APKPure API " + url);
        String body;
        try (Response response = HTTP.newCall(request).execute()) {
            HelperLog.info("APKPure API -> HTTP " + response.code());
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " from APKPure API");
            }
            ResponseBody responseBody = response.body();
            body = responseBody == null ? "" : responseBody.string();
        }

        JSONObject root;
        try {
            root = new JSONObject(body);
        } catch (JSONException e) {
            throw new IOException("APKPure API returned invalid JSON", e);
        }
        JSONArray list = root.optJSONArray("version_list");
        if (list == null || list.length() == 0) {
            throw new IOException("APKPure API returned no versions");
        }

        JSONObject best = null;
        boolean bestSplit = false;
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String itemVersion = item.optString("version_name");
            long itemCode;
            try {
                itemCode = Long.parseLong(item.optString("version_code"));
            } catch (Exception ignored) {
                itemCode = -1;
            }
            boolean matches = contains(versionCodes, itemCode)
                    || sameVersion(itemVersion, version);
            if (!matches) {
                continue;
            }

            JSONObject asset = item.optJSONObject("asset");
            if (asset == null || TextUtils.isEmpty(asset.optString("url"))) {
                continue;
            }
            String type = asset.optString("type", "APK");
            boolean split = "XAPK".equalsIgnoreCase(type) || "APKS".equalsIgnoreCase(type);
            if (best == null || (isSplitType(fileType) == split)) {
                best = item;
                bestSplit = split;
                if (isSplitType(fileType) == split) {
                    break;
                }
            }
        }

        if (best == null) {
            throw new IOException("Requested version not found on APKPure");
        }
        if (bestSplit && !allowSplitArchive && !isSplitType(fileType)) {
            throw new IOException("APKPure only has a split archive for this version");
        }

        JSONObject asset = best.optJSONObject("asset");
        String assetUrl = asset.optString("url");
        String type = asset.optString("type", "APK");
        String downloadType = "XAPK".equalsIgnoreCase(type) ? "xapk"
                : ("APKS".equalsIgnoreCase(type) ? "apks" : "apk");

        HelperLog.info("APKPure API asset: " + best.optString("version_name")
                + " code=" + best.optString("version_code")
                + " type=" + type + " size=" + asset.optString("size"));
        return downloadFile(assetUrl, "https://apkpure.com/", pkg, version, downloadType);
    }

    private boolean sameVersion(String left, String right) {
        if (TextUtils.isEmpty(left) || TextUtils.isEmpty(right)) {
            return false;
        }
        return left.trim().replace(" ", "")
                .equalsIgnoreCase(right.trim().replace(" ", ""));
    }

    /**
     * Downloads from APKPure's direct endpoint.
     *
     * <p>The endpoint expects a version code when available, otherwise a version name.
     * If the requested format fails, the alternate APK/XAPK endpoint is tried once.</p>
     */
    private File downloadFromApkPure(String pkg, String version, long[] versionCodes,
                                     String fileType) throws IOException {
        String url = buildApkPureUrl(pkg, versionCodes, version, fileType);
        HelperLog.info("APKPure URL: " + url);
        try {
            return downloadFile(url, null, pkg, version, fileType);
        } catch (IOException first) {
            HelperLog.error("APKPure URL failed: " + url, first);
            if (!allowSplitArchive) {
                throw first;
            }
            String alt = buildApkPureAltUrl(pkg, versionCodes, version, fileType);
            if (!alt.equals(url)) {
                HelperLog.info("APKPure alt URL: " + alt);
                return downloadFile(alt, null, pkg, version, fileType);
            }
            throw first;
        }
    }

    // ---------------------------------------------------------------------
    // APKMirror HTML helpers
    // ---------------------------------------------------------------------

    /**
     * Finds the first release link in an APKMirror search results page.
     *
     * <p>APKMirror marks app titles with {@code class="fontBlack"}; release links contain
     * {@code -release/}. Related-app links are ignored.</p>
     */
    private String findFirstVersionUrl(String html) {
        Matcher m = Pattern.compile(
                "<a class=\"fontBlack\" href=\"([^\"]+)\"[^>]*>([^<]+)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        while (m.find()) {
            String href = decodeHtml(m.group(1));
            if (href.contains("-release/")) {
                return href;
            }
        }
        return null;
    }

    /**
     * Finds the best release link for the requested version on an APKMirror app page.
     *
     * <p>When a version is requested, only an exact match is returned; the method never
     * silently falls back to the latest version. When no version is requested, stable
     * releases are preferred over beta releases.</p>
     */
    private String findVersionUrl(String appHtml, String appPath, String version) {
        Matcher m = Pattern.compile(
                "<a class=\"fontBlack\" href=\"([^\"]+)\"[^>]*>([^<]+)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(appHtml);
        String firstAny = null;
        String firstStable = null;
        String versionMatch = null;

        while (m.find()) {
            String href = decodeHtml(m.group(1));
            String text = decodeHtml(m.group(2));

            // Only actual release pages of this app, not "related apps" on the same page.
            if (!href.contains("-release/")) continue;
            if (!TextUtils.isEmpty(appPath) && !href.startsWith(appPath)) continue;

            if (firstAny == null) firstAny = href;

            // Beta releases are usable but should not be chosen over a stable build.
            boolean beta = text.toUpperCase(Locale.US).contains("BETA")
                    || href.toLowerCase(Locale.US).contains("beta");
            if (!beta && firstStable == null) firstStable = href;

            if (!TextUtils.isEmpty(version)) {
                String normalized = version.replace('.', '-').toLowerCase(Locale.US);
                if (text.toLowerCase(Locale.US).contains(version.toLowerCase(Locale.US))
                        || href.toLowerCase(Locale.US).contains(normalized)) {
                    if (!beta) return href;
                    if (versionMatch == null) versionMatch = href;
                }
            }
        }

        if (!TextUtils.isEmpty(version)) {
            // Do not silently fall back to the latest version when a specific one was requested.
            return versionMatch;
        }
        if (firstStable != null) return firstStable;
        return firstAny;
    }

    /**
     * Builds the release URL for the requested version from the latest release slug.
     *
     * <p>APKMirror release slugs are predictable. For example, the latest release
     * {@code youtube-21-42-247-release} can be turned into
     * {@code youtube-21-30-216-release} for a requested version of {@code 21.30.216}.</p>
     *
     * <p>The constructed URL is probed with a GET request; if it 404s, null is returned.</p>
     */
    private String findExactVersionUrl(String appPath, String appHtml, String version,
                                       String referer) {
        String latest = findVersionUrl(appHtml, appPath, null);
        HelperLog.info("APKMirror exact base latest=" + latest);
        if (latest == null || TextUtils.isEmpty(version)) {
            return null;
        }

        // Remove a trailing slash before splitting the slug.
        String trimmed = latest.endsWith("/")
                ? latest.substring(0, latest.length() - 1)
                : latest;
        String lastSegment = trimmed.substring(trimmed.lastIndexOf('/') + 1);
        Matcher matcher = Pattern.compile(
                "^(.*?-)(\\d+(?:-\\d+)*)-release/?$",
                Pattern.CASE_INSENSITIVE).matcher(lastSegment);
        if (!matcher.matches()) {
            HelperLog.info("APKMirror exact slug did not match: " + lastSegment);
            return null;
        }

        // Keep the app-name prefix, replace only the numeric version segment.
        String prefix = matcher.group(1);
        String normalized = version.split("-")[0].replace('.', '-');
        String candidate = appPath + prefix + normalized + "-release/";
        HelperLog.info("APKMirror exact candidate=" + candidate);

        try {
            // A successful GET proves the constructed page exists.
            httpGet(absolute(candidate), referer);
            HelperLog.info("APKMirror exact version URL: " + candidate);
            return candidate;
        } catch (Exception e) {
            HelperLog.info("APKMirror exact version URL not found: " + candidate);
            return null;
        }
    }

    /**
     * Picks the best APKMirror variant row for the requested archive type and ABI.
     *
     * <p>Preference order:</p>
     * <ul>
     *   <li>APK request: universal APK, then any APK, then fallback.</li>
     *   <li>Bundle request: bundle matching the first supported ABI, then any bundle,
     *       then any APK.</li>
     * </ul>
     */
    private String chooseVariant(String versionHtml, String fileType, String[] abis) {
        // Each variant is a table row; split on the row wrapper to inspect one at a time.
        String[] rows = versionHtml.split("<div class=\"table-row");
        String fallback = null;
        String plainApk = null;
        String plainApkUniversal = null;
        String bundle = null;
        String bundleAbi = null;
        String preferredAbi = (abis != null && abis.length > 0) ? abis[0] : null;

        for (String row : rows) {
            Matcher m = Pattern.compile(
                    "href=\"([^\"]+-android-apk-download/)\"",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(row);
            if (!m.find()) continue;

            String href = decodeHtml(m.group(1));
            if (fallback == null) fallback = href;

            // Badge text and row classes identify bundles and plain APKs.
            boolean isBundle = row.contains("BUNDLE") || row.contains("apkm-badge success");
            boolean isPlainApk = row.contains(">APK<") || row.contains("apkm-badge\">APK");
            boolean universal = row.contains("universal") || row.contains("nodpi");
            boolean hasAbi = preferredAbi != null && row.contains(preferredAbi);

            if (isPlainApk && universal && plainApkUniversal == null) plainApkUniversal = href;
            if (isPlainApk && plainApk == null) plainApk = href;
            if (isBundle && hasAbi && bundleAbi == null) bundleAbi = href;
            if (isBundle && bundle == null) bundle = href;
        }

        if ("apk".equalsIgnoreCase(fileType) || TextUtils.isEmpty(fileType)) {
            if (plainApkUniversal != null) return plainApkUniversal;
            if (plainApk != null) return plainApk;
        } else {
            if (bundleAbi != null) return bundleAbi;
            if (bundle != null) return bundle;
            if (plainApk != null) return plainApk;
        }
        return fallback;
    }

    // ---------------------------------------------------------------------
    // HTTP
    // ---------------------------------------------------------------------

    /**
     * Performs a GET request and returns the response body as text.
     *
     * @param url     absolute URL to request
     * @param referer optional Referer header, used by APKMirror's download chain
     * @throws IOException when the response is not 2xx
     */
    private String httpGet(String url, String referer) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9");
        if (referer != null) {
            builder.header("Referer", referer);
        }

        int attempts = isHost(hostOf(url), "apkmirror.com") ? 2 : 1;
        IOException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            HelperLog.info("GET " + url + (referer != null ? " (referer=" + referer + ")" : "")
                    + (attempt > 1 ? " attempt=" + attempt : ""));
            try (Response response = HTTP.newCall(builder.build()).execute()) {
                HelperLog.info("GET " + url + " -> HTTP " + response.code()
                        + " type=" + response.header("Content-Type")
                        + " length=" + response.header("Content-Length"));
                if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    return body != null ? body.string() : "";
                }
                last = new IOException("HTTP " + response.code() + " for " + url);
                int code = response.code();
                if (attempt >= attempts || (code != 429 && code < 500)) {
                    throw last;
                }
            }
            try {
                Thread.sleep(700L * attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            }
        }
        throw last == null ? new IOException("Request failed") : last;
    }

    private File downloadFile(String url, String referer, String pkg, String versionName,
                              String fileType) throws IOException {
        return downloadFile(url, referer, pkg, versionName, fileType, null);
    }

    /**
     * Streams a URL to a cache file and reports progress to the UI.
     *
     * <p>The method rejects HTML responses (often a Cloudflare challenge or a login
     * page), empty files, and cancelled downloads. The returned file is validated by
     * Morphe after the activity returns it.</p>
     */
    private File downloadFile(String url, String referer, String pkg, String versionName,
                              String fileType, String cookie) throws IOException {
        File dir = AppSettings.downloadDir(this);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create download directory");
        }
        String partBase = sanitize(pkg + "-" + versionName + "-"
                + hostOf(url) + "-" + fileType);
        if (partBase.isEmpty()) {
            partBase = "download-" + Math.abs(url.hashCode());
        }
        File part = new File(dir, partBase + ".part");
        long existing = part.length();
        boolean append = existing > 0;

        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9");
        if (referer != null) {
            builder.header("Referer", referer);
        }
        if (cookie != null && !cookie.isEmpty()) {
            builder.header("Cookie", cookie);
        }
        if (append) {
            HelperLog.info("Resuming download at " + existing + " bytes");
            builder.header("Range", "bytes=" + existing + "-");
        }

        HelperLog.info("DOWNLOAD " + url
                + (referer != null ? " (referer=" + referer + ")" : ""));
        try (Response response = HTTP.newCall(builder.build()).execute()) {
            String contentType = response.header("Content-Type");
            ResponseBody body = response.body();
            long bodyLength = body != null ? body.contentLength() : -1;
            long total = append && response.code() == 206 && bodyLength >= 0
                    ? existing + bodyLength : bodyLength;
            HelperLog.info("DOWNLOAD " + url + " -> HTTP " + response.code()
                    + " type=" + contentType
                    + " length=" + total);
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            if (contentType != null && contentType.toLowerCase(Locale.US).contains("text/html")) {
                throw new IOException("Server returned HTML instead of an APK");
            }
            if (body == null) {
                throw new IOException("Empty response");
            }

            setPauseVisible(true);
            setStatus("Starting download...");
            String filename = filenameFrom(
                    response.header("Content-Disposition"),
                    response.request().url().toString(),
                    pkg, versionName, fileType, contentType
            );
            boolean resume = append && response.code() == 206;
            if (!resume) {
                existing = 0;
            }

            final long initialBytes = existing;
            main.post(() -> {
                progress.setIndeterminate(false);
                progress.setProgress(total > 0 ? (int) (initialBytes * 100 / total) : 0);
            });

            long startTime = System.currentTimeMillis();
            long lastUi = 0;
            try (InputStream in = new BufferedInputStream(body.byteStream());
                 BufferedOutputStream out = new BufferedOutputStream(
                         new FileOutputStream(part, resume))) {
                byte[] buffer = new byte[64 * 1024];
                long readTotal = existing;
                while (!cancelled) {
                    while (paused && !cancelled) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    if (cancelled) {
                        break;
                    }
                    int read = in.read(buffer);
                    if (read == -1) {
                        break;
                    }
                    out.write(buffer, 0, read);
                    readTotal += read;
                    long now = System.currentTimeMillis();
                    if (total > 0 && (now - lastUi >= 500 || readTotal == total)) {
                        lastUi = now;
                        int p = (int) Math.min(100, readTotal * 100 / total);
                        long elapsed = Math.max(1, now - startTime);
                        double speed = (readTotal - existing) * 1000.0 / elapsed;
                        long left = speed > 0 ? (long) ((total - readTotal) / speed) : -1;
                        String speedText = String.format(Locale.US, "%.1f MB/s",
                                speed / (1024 * 1024));
                        String text = "Downloading... " + p + "% \u2022 " + speedText
                                + (left > 0 ? " \u2022 " + left + "s left" : "");
                        final int progressValue = p;
                        main.post(() -> {
                            progress.setProgress(progressValue);
                            setStatus(text);
                        });
                        updateDownloadService(text, p, false);
                    }
                }
            }

            if (cancelled) {
                throw new IOException("Cancelled");
            }
            if (part.length() == 0) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                throw new IOException("Empty file");
            }

            File file = new File(dir, filename);
            if (file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            if (!part.renameTo(file)) {
                throw new IOException("Cannot finalize downloaded file");
            }
            try {
                DownloadedFileValidator.validate(this, file, pkg, versionName,
                        requestVersionCodes);
            } catch (IOException invalid) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                throw invalid;
            }
            HelperLog.info("Saved " + file.getAbsolutePath()
                    + " (" + file.length() + " bytes)");
            return file;
        }
    }

    /**
     * Chooses a safe local filename.
     *
     * <p>Order of preference: Content-Disposition filename, filename query parameter in
     * the final URL, then {@code package-version} with an extension inferred from the
     * content type or requested file type.</p>
     */
    private String filenameFrom(String disposition, String finalUrl, String pkg,
                                String versionName, String fileType, String contentType) {
        if (disposition != null) {
            int i = disposition.toLowerCase(Locale.US).indexOf("filename=");
            if (i >= 0) {
                String name = disposition.substring(i + 9).trim();
                if (name.startsWith("\"") && name.endsWith("\"") && name.length() > 1) {
                    name = name.substring(1, name.length() - 1);
                }
                name = sanitize(name);
                if (!name.isEmpty()) {
                    return ensureExtension(name, fileType, contentType);
                }
            }
        }

        // Some APK sites put the filename in the URL query string instead.
        int q = finalUrl.indexOf("filename=");
        if (q >= 0) {
            String name = finalUrl.substring(q + 9);
            int amp = name.indexOf('&');
            if (amp >= 0) {
                name = name.substring(0, amp);
            }
            try {
                name = URLDecoder.decode(name, "UTF-8");
            } catch (Exception ignored) {
                // Keep the raw value if it is not valid percent-encoding.
            }
            name = sanitize(name);
            if (!name.isEmpty()) {
                return ensureExtension(name, fileType, contentType);
            }
        }

        // Last resort: use the package and version so the file is still identifiable.
        String base = pkg;
        if (!TextUtils.isEmpty(versionName)) {
            base += "-" + versionName;
        }
        return ensureExtension(base, fileType, contentType);
    }

    /** Replaces characters that are unsafe in filenames with underscores. */
    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Ensures the filename ends with a supported archive extension.
     *
     * <p>Content type wins over the requested file type because the server knows what it
     * actually sent; the requested type is the fallback.</p>
     */
    private String ensureExtension(String name, String fileType, String contentType) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".apk") || lower.endsWith(".xapk")
                || lower.endsWith(".apks") || lower.endsWith(".apkm")) {
            return name;
        }

        if (contentType != null) {
            String ct = contentType.toLowerCase(Locale.US);
            if (ct.contains("vnd.apkm")) return name + ".apkm";
            if (ct.contains("vnd.android.package-archive")) return name + ".apk";
            if (ct.contains("xapk")) return name + ".xapk";
            if (ct.contains("apks")) return name + ".apks";
        }

        if ("apkm".equalsIgnoreCase(fileType)) return name + ".apkm";
        if ("apks".equalsIgnoreCase(fileType)) return name + ".apks";
        if ("xapk".equalsIgnoreCase(fileType)) return name + ".xapk";
        return name + ".apk";
    }

    // ---------------------------------------------------------------------
    // URL / HTML helpers
    // ---------------------------------------------------------------------

    /** Returns true when the URL path ends in a supported archive extension. */
    private boolean isDirectFile(String url) {
        String lower = url.toLowerCase(Locale.US);
        int q = lower.indexOf('?');
        if (q >= 0) lower = lower.substring(0, q);
        return lower.endsWith(".apk") || lower.endsWith(".apkm")
                || lower.endsWith(".apks") || lower.endsWith(".xapk");
    }

    /**
     * Converts a possibly relative APKMirror link into an absolute URL.
     *
     * <p>Protocol-relative links (//host/path) become https, root-relative links
     * (/path) get the APKMirror host, and bare paths are appended to the host.</p>
     */
    private String absolute(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return APKMIRROR + url;
        return APKMIRROR + "/" + url;
    }

    /** Extracts a lower-case host name, or an empty string for invalid input. */
    private String hostOf(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        try {
            String host = new URL(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (Exception e) {
            return "";
        }
    }

    /** Extracts the URL path, falling back to the original value on parse errors. */
    private String pathOf(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        try {
            String path = new URL(url).getPath();
            return path != null ? path : url;
        } catch (Exception e) {
            return url;
        }
    }

    /** Returns true when the host is the domain itself or a subdomain of it. */
    private boolean isHost(String host, String domain) {
        return host != null && (host.equals(domain) || host.endsWith("." + domain));
    }

    /** Decodes the small set of HTML entities that APK pages commonly contain. */
    private String decodeHtml(String value) {
        return value.replace("&amp;", "&")
                .replace("&#038;", "&")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ");
    }

    /** Returns the first capture group of a regex, or null when there is no match. */
    private String firstMatch(String html, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        return m.find() ? m.group(1) : null;
    }

    // ---------------------------------------------------------------------
    // APKPure fallback
    // ---------------------------------------------------------------------

    /** Builds the primary APKPure download URL for the requested format. */
    private String buildApkPureUrl(String pkg, long[] versionCodes, String version,
                                   String fileType) {
        String base = isSplitType(fileType)
                ? "https://d.apkpure.com/b/XAPK/"
                : "https://d.apkpure.com/b/APK/";
        return base + pkg + versionQuery(versionCodes, version);
    }

    /** Builds the alternate APKPure URL (APK vs XAPK) used after a first failure. */
    private String buildApkPureAltUrl(String pkg, long[] versionCodes, String version,
                                      String fileType) {
        String base = isSplitType(fileType)
                ? "https://d.apkpure.com/b/APK/"
                : "https://d.apkpure.com/b/XAPK/";
        return base + pkg + versionQuery(versionCodes, version);
    }

    /**
     * Builds APKPure's version query.
     *
     * <p>Prefer the numeric version code because it is unambiguous; fall back to the
     * version name, then to "latest".</p>
     */
    private String versionQuery(long[] versionCodes, String version) {
        if (versionCodes != null && versionCodes.length > 0) {
            return "?versionCode=" + versionCodes[0];
        }
        if (!TextUtils.isEmpty(version)) {
            try {
                return "?version=" + URLEncoder.encode(version, "UTF-8");
            } catch (Exception ignored) {
                // UTF-8 is always available; this is only defensive.
            }
        }
        return "?version=latest";
    }

    /** Returns true for split-archive formats that APKPure serves from its XAPK endpoint. */
    private boolean isSplitType(String fileType) {
        return "xapk".equalsIgnoreCase(fileType) || "apks".equalsIgnoreCase(fileType)
                || "apkm".equalsIgnoreCase(fileType);
    }
}


