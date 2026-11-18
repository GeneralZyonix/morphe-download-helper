package be.alterra.morphehelper;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.text.TextUtils;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
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

    private int BG_TOP;
    private int BG_BOTTOM;
    private int CARD_TOP;
    private int CARD_BOTTOM;
    private int BORDER;
    private int PRIMARY_START;
    private int PRIMARY_END;
    private int ON_PRIMARY;
    private int ACCENT_TEXT;
    private int RIPPLE;
    private int SECONDARY;
    private int SECONDARY_BORDER;
    private int TEXT;
    private int MUTED;
    private int STEPS;
    private int WARN_TITLE;
    private int WARN_TEXT;
    private int WARN_BG;
    private int WARN_BORDER;
    private int GREEN;
    private int RED;
    private String themeSignature;

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
     * Shared OkHttp base client.
     *
     * <p>OkHttp is used instead of {@link java.net.HttpURLConnection} because it handles
     * redirects, cookies, and Cloudflare challenges much more reliably on Android.
     * The derived clients below apply shorter, purpose-specific timeouts.</p>
     */
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .cookieJar(new MemoryCookieJar())
            .build();

    /** Metadata requests: fail fast so one dead host cannot block the source chain. */
    private static final OkHttpClient METADATA_HTTP = HTTP.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build();

    /** Short-lived probes used only to decide which source path to skip. */
    private static final OkHttpClient PREFLIGHT_HTTP = HTTP.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build();

    /** APKMirror pages get a little more room because Cloudflare can be slow. */
    private static final OkHttpClient APKMIRROR_HTTP = HTTP.newBuilder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build();

    /**
     * File downloads: the read timeout is the stall timeout. No call timeout,
     * because that would cap the whole transfer.
     */
    private static final OkHttpClient DOWNLOAD_HTTP = HTTP.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
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
    private LinearLayout progressInfo;
    private TextView progressSpeed;
    private TextView progressEta;
    private View progressEtaDivider;

    /** Extra explanation shown while VirusTotal is scanning. */
    private TextView scanNote;

    /** Visible skip control shown while VirusTotal is scanning. */
    private TextView skipScanButton;

    /** Appears when a source WebView is still waiting for verification. */
    private View verificationHint;
    private Runnable verificationHintRunnable;

    /** Result-stage decision controls. */
    private LinearLayout resultActions;
    private TextView resultContinue;
    private TextView resultDelete;
    private TextView resultReport;

    /** A validated file that has not been handed off yet. */
    private volatile File pendingFile;
    private volatile boolean keepPendingFile;
    private boolean cacheAllowed;
    private boolean bypassCache;
    private boolean servedFromCache;
    private volatile boolean resultStage;
    private volatile int resultDecision = DECISION_NONE;
    private final Object resultLock = new Object();

    private static final int DECISION_NONE = 0;
    private static final int DECISION_CONTINUE = 1;
    private static final int DECISION_DELETE = 2;

    /** Download progress bar. Indeterminate while resolving, determinate while downloading. */
    private ProgressDisplay progress;

    /** Button that opens the fallback web page when automatic resolution fails. */
    private TextView fallbackButton;

    /** Cancel/Close button. */
    private TextView cancelButton;

    /** Pause/Resume button, shown once a download starts. */
    private TextView pauseButton;

    /** Aborts the current source and lets the resolver try the next one. */
    private TextView switchSourceButton;
    private LinearLayout downloadButtonsRow;
    private volatile Call activeCall;
    private final Set<Call> activeCalls = Collections.synchronizedSet(new HashSet<>());
    private volatile boolean switchingSource;
    private volatile String forcedSource;
    /** Source selected while another attempt is still unwinding. */
    private volatile String pendingSwitchKey;
    /** Source currently being resolved or downloaded. */
    private volatile String activeSourceKey;

    /** Label above the progress bar naming the active source. */
    private TextView sourceLabel;

    /** Large app name shown above the progress ring. */
    private TextView downloadName;
    /** App icon and metadata chips shown above the progress ring. */
    private LinearLayout appHeader;
    private ImageView appIcon;
    private TextView appIconFallback;
    private LinearLayout appChips;
    private TextView appVersionChip;
    private TextView appFormatChip;
    private TextView appAbiChip;

    /** Source / Size / Connections metrics and the package pill. */
    private LinearLayout metricsRow;
    private TextView metricSource;
    private TextView metricSize;
    private TextView metricConnections;
    /** Small active-source logo shown beside the Source metric value. */
    private FrameLayout metricSourceBadge;
    private TextView packagePill;
    private String activeSourceName = "";
    private volatile int downloadConnections = 1;
    private volatile long downloadTotalBytes = -1;
    /** ETag/Last-Modified from the first response, used by If-Range on resume. */
    private volatile String downloadIfRange;

    /** Set to true when the user backs out or the activity is destroyed. */
    private volatile boolean cancelled = false;

    /** Set while the user has paused an active download. */
    private volatile boolean paused = false;
    /** True when the source picker paused an active download. */
    private boolean pickerPausedDownload = false;

    /** Set while a downloaded file is being checked by VirusTotal. */
    private volatile boolean scanInProgress = false;
    private volatile boolean skipScanRequested = false;

    /** Fallback URL supplied by Morphe, if any. */
    private String fallbackUrl;

    /** Whether Morphe accepts split archives (XAPK/APKS) for this request. */
    private boolean allowSplitArchive;

    /** True when Morphe's resolver points at a GitHub release for this request. */
    private boolean githubResolverHint;

    /** Last GitHub package/signature verification failure, if any. */
    private String verificationFailure;

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
    /** Top-level source row that should show NOW while a WebView flow runs. */
    private volatile String pendingSourceKey;
    /** Start time of the current WebView flow, used for state logging. */
    private long webFlowStartedAt;
    /** True once a source returned a file; the source list should stop advancing. */
    private volatile boolean resolutionComplete;

    /** True when the current WebView may block known ad/tracker hosts. */
    private volatile boolean blockWebAds;

    /** True when unknown hosts may also be checked against AdGuard DoH. */
    private volatile boolean liveAdGuard;

    /** Blocked request diagnostics for the current WebView flow. */
    private final Object adBlockLock = new Object();
    private final Set<String> blockedAdHosts = new HashSet<>();
    private int blockedAdRequests;

    /** App name used to pick the right APKPure search result. */
    private String requestedAppName;

    /** Guards against handling the same Uptodown download URL more than once. */
    private boolean uptodownHandled;

    /**
     * Set when Uptodown served a build this request cannot use (wrong build
     * number or a minSdkVersion newer than the device). Keep Uptodown out of
     * the remaining attempts for this request and let the other sources try.
     */
    private volatile boolean uptodownRejected;
    private volatile boolean apkmirrorRejected;
    /** The patch/fallback link led to a build that must not be retried. */
    private volatile boolean fallbackRejected;
    /** Source that opened the current WebView, before the WebView source key. */
    private volatile String webParentSourceKey;
    /** Guards against handling the same pre-download build rejection twice. */
    private volatile boolean webBuildRejected;

    /** Request values kept for the WebView download path. */
    private String uptodownPkg;
    private String uptodownVersion;
    private String uptodownFileType;
    private String uptodownReferer;
    private String webOriginalUrl;
    private int webRetries;


    private Runnable webFlowTimeout;
    private Runnable adBlockRetry;
    /** Polls the page for a verification challenge and state changes. */
    private Runnable webFlowProbe;
    private Runnable challengeTimeout;
    private static final long CHALLENGE_TIMEOUT_MS = 5 * 60_000L;
    private volatile boolean webChallenge;
    private volatile boolean webPageFinished;
    private volatile long webLastProgressAt;
    private volatile boolean webHintShown;
    private volatile boolean webReadyLogged;
    private int webNotFoundProbes;
    private volatile Future<Boolean> apkCubePreflightFuture;
    private final Map<String, Future<String>> sourcePrefetches = new ConcurrentHashMap<>();
    private volatile boolean sourcePrefetchCancelled;
    private Future<ApkPureAssets.Asset> apkPureApiPrefetch;
    private Future<ApkPureAssets.Asset> apkPureDirectPrefetch;
    private Future<UptodownApi.Prepared> uptodownPrefetch;
    private ExecutorService apiProbePool;
    private boolean apkPurePrefetchStarted;

    private LinearLayout diagnosticsCard;
    private LinearLayout diagnosticsRows;
    private TextView diagnosticsSummary;
    private TextView retryButton;
    private TextView retryAllButton;
    private TextView copyButton;
    private TextView editSourcesButton;
    private TextView screenTitle;
    private TextView screenSubtitle;
    private TextView stateIcon;
    private LinearLayout sourceStepsCard;
    private LinearLayout sourceStepsList;
    private TextView sourceStepsSummary;
    private ImageView sourceStepsChevron;
    private boolean sourceStepsExpanded = true;
    private boolean sourceStepsManualOverride;
    private CharSequence sourceSummaryText = "";
    private ResolutionReport report;
    private Set<String> retrySources;
    /**
     * Sources already tried in this resolution. A WebView failure restarts
     * resolution without clearing this set, so the failed source is skipped and
     * the next source is tried instead of ending the whole download.
     */
    private final Set<String> attemptedSources = Collections.newSetFromMap(
            new ConcurrentHashMap<>());
    private String requestPkg;
    private String requestVersion;
    private String requestFileType;
    private long[] requestVersionCodes;
    private long[] resolvedVersionCodes;
    private String[] requestAbis;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prepare logging first so every later step is captured.
        HelperLog.init(getApplicationContext());
        AppSettings.clearApkMirrorCachesIfVersionChanged(this);
        SourceSettings.ensureInitialized(this);
        PublicSettings.apply(this);
        AppSettings.ensureLauncherIconState(this);
        applyPalette();
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
        updateRequestHeader();

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
                + " apkcube=" + sourceAllowed(SourceSettings.APKCUBE)
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
            setStatus(R.string.download_error_no_package);
            showFallback();
            return;
        }

        // Uptodown's page runs Cloudflare Turnstile. A WebView is the only reliable
        // way to complete that challenge and capture the resulting download URL.
        if (isHost(fallbackHost, "uptodown.com")) {
            if (sourceAllowed(SourceSettings.UPTODOWN)
                    && sourceAllowed(SourceSettings.FALLBACK)) {
                setStatus(R.string.download_opening_uptodown);
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
        setStatus(getString(R.string.download_preparing, label));
        progress.setIndeterminate(true);

        startResolution(false);
    }

    private void startResolution(boolean retryOnly) {
        startResolution(retryOnly, false);
    }

    /**
     * A WebView source failed. Keep the report and the attempted-source set,
     * then resume the ordered resolution so the next source can run instead of
     * showing a final failure.
     */
    private void continueAfterWebFailure(String reason) {
        if (cancelled) {
            return;
        }
        HelperLog.info("WebView source failed; trying remaining sources: " + reason);
        main.post(() -> {
            closeWebFlow();
            markWebFlowResult(false, reason);
            startResolution(false, true);
        });
    }

    private void startResolution(boolean retryOnly, boolean continueAfterWeb) {
        if (!continueAfterWeb) {
            attemptedSources.clear();
        }
        Set<String> failed = retryOnly && report != null
                ? new HashSet<>(report.failedKeys()) : Collections.emptySet();
        if (retryOnly) {
            if (failed.contains(SourceSettings.UPTODOWN)
                    || failed.contains(SourceSettings.APKMIRROR)
                    || failed.contains(SourceSettings.APKPURE)) {
                failed.add(SourceSettings.FALLBACK);
            }
        }
        if (retryOnly && failed.isEmpty()) {
            return;
        }
        retrySources = retryOnly ? failed : null;
        switchingSource = false;
        pendingSwitchKey = null;
        activeSourceKey = null;
        activeSourceName = "";
        webParentSourceKey = null;
        verificationFailure = null;
        webRetries = 0;
        pendingSourceKey = null;
        resolutionComplete = false;
        cacheAllowed = !retryOnly && !continueAfterWeb
                && forcedSource == null && !bypassCache;
        bypassCache = false;
        servedFromCache = false;
        hideVerificationHint();
        String abi = (requestAbis != null && requestAbis.length > 0)
                ? requestAbis[0] : "unknown";
        if (!continueAfterWeb) {
            report = new ResolutionReport(this, requestedAppName, requestPkg, requestVersion,
                    requestFileType, abi);
            HelperLog.info(report.headerBlock());
        }
        if (report == null) {
            report = new ResolutionReport(this, requestedAppName, requestPkg, requestVersion,
                    requestFileType, abi);
            HelperLog.info(report.headerBlock());
        }

        diagnosticsCard.setVisibility(View.GONE);
        fallbackButton.setVisibility(View.GONE);
        sourceLabel.setVisibility(View.GONE);
        cancelButton.setText("\u2715  " + getString(R.string.common_cancel));
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        progress.setProgress(0);
        if (sourceStepsCard != null) {
            sourceStepsCard.setVisibility(View.VISIBLE);
        }
        if (stateIcon != null) {
            stateIcon.setVisibility(View.GONE);
        }
        scanNote.setVisibility(View.GONE);
        skipScanButton.setVisibility(View.GONE);
        updateRequestHeader();
        main.post(() -> {
            sourceStepsManualOverride = false;
            setSourceCardExpanded(true);
            renderSourceSteps();
        });
        setPauseVisible(false);
        setSwitchSourceVisible(true);
        if (continueAfterWeb) {
            setStatus(R.string.download_trying_next_source);
        } else if (retryOnly) {
            setStatus(R.string.download_retrying);
        }
        startDownloadService(continueAfterWeb
                ? getString(R.string.download_trying_next_source)
                : retryOnly
                ? getString(R.string.download_retrying)
                : getString(R.string.download_resolving));

        executor.execute(() -> {
            try {
                File file = resolve(requestPkg, requestVersion, requestVersionCodes,
                        requestFileType, requestAbis);
                if (cancelled) {
                    return;
                }
                appIconCacheSave(file);
                deliverFile(file);
            } catch (UptodownHandoffException ignored) {
                if (!cancelled && pendingSwitchKey != null) {
                    restartAfterSwitch();
                }
            } catch (Exception e) {
                if (cancelled) {
                    return;
                }
                if (pendingSwitchKey != null) {
                    restartAfterSwitch();
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
                    setStatus(R.string.download_error_no_match);
                    showFailure();
                });
            }
        });
    }

    private VirusTotalScanner.Result scanWithVirusTotal(File file) {
        if (!AppSettings.virusTotalEnabled(this)) {
            return VirusTotalScanner.scan(this, file);
        }
        setPauseVisible(false);
        setSwitchSourceVisible(false);
        scanInProgress = true;
        skipScanRequested = false;
        runOnUiThread(() -> {
            scanNote.setVisibility(View.VISIBLE);
            skipScanButton.setVisibility(View.VISIBLE);
        });
        VirusTotalScanner.Result result;
        try {
            result = VirusTotalScanner.scan(this, file,
                    (message, percent, stage) -> runOnUiThread(() -> {
                        setStatus(message);
                        progress.setVisibility(View.VISIBLE);
                        if (stage == VirusTotalScanner.Stage.CHECKING) {
                            progress.setIndeterminateCaption(
                                    R.string.download_progress_checking);
                        } else if (stage == VirusTotalScanner.Stage.UPLOADING) {
                            if (percent < 0) {
                                progress.setIndeterminateCaption(
                                        R.string.download_progress_uploading);
                            } else {
                                progress.setProgressWithCaption(
                                        R.string.download_progress_uploading, percent);
                            }
                        } else {
                            progress.setIndeterminateCaption(
                                    R.string.download_progress_scanning);
                        }
                    }),
                    () -> skipScanRequested);
        } finally {
            scanInProgress = false;
            skipScanRequested = false;
            runOnUiThread(() -> {
                scanNote.setVisibility(View.GONE);
                skipScanButton.setVisibility(View.GONE);
            });
        }
        if (result.status == VirusTotalScanner.Status.DETECTED) {
            HelperLog.info("VirusTotal result: " + result.message);
        } else if (result.status == VirusTotalScanner.Status.CANCELLED) {
            HelperLog.info("VirusTotal scan skipped by user");
        } else if (result.status != VirusTotalScanner.Status.CLEAN
                && result.status != VirusTotalScanner.Status.DISABLED) {
            HelperLog.info("VirusTotal result: " + result.message);
        }
        return result;
    }

    /**
     * Returns a downloaded file to Morphe through FileProvider.
     *
     * <p>Morphe can only read the file when the result carries
     * {@link Intent#FLAG_GRANT_READ_URI_PERMISSION}.</p>
     */
    private void deliverFile(File file) {
        if (pendingSwitchKey != null) {
            restartAfterSwitch();
            return;
        }
        resolutionComplete = true;
        main.post(this::renderSourceSteps);
        try {
            pendingFile = file;
            VirusTotalScanner.Result scan = scanWithVirusTotal(file);
            if (cancelled) {
                discardPending(file);
                return;
            }

            if (scan.status == VirusTotalScanner.Status.UPLOAD_PENDING) {
                if (awaitResultDecision(
                        getString(R.string.virustotal_pending_abort), scan)
                        != DECISION_CONTINUE) {
                    abortHandoff(file,
                            getString(R.string.virustotal_pending_reason),
                            getString(R.string.virustotal_pending_status));
                    return;
                }
            } else if (scan.isDetection()) {
                String action = AppSettings.virusTotalDetectionAction(this);
                if (AppSettings.VT_ACTION_BLOCK.equals(action)) {
                    abortHandoff(file,
                            getString(R.string.virustotal_blocked_reason, scan.message),
                            getString(R.string.virustotal_blocked_signatures));
                    return;
                }
                if (AppSettings.VT_ACTION_CONTINUE.equals(action)) {
                    runOnUiThread(() -> setStatus(
                            getString(R.string.virustotal_detected_continue, scan.message)));
                } else if (awaitResultDecision(
                        getString(R.string.virustotal_detected_ask, scan.message), scan)
                        != DECISION_CONTINUE) {
                    abortHandoff(file,
                            getString(R.string.virustotal_blocked_reason, scan.message),
                            getString(R.string.virustotal_blocked_signatures));
                    return;
                }
            } else if (scan.status == VirusTotalScanner.Status.QUOTA_EXCEEDED
                    && AppSettings.virusTotalAbortOnQuota(this)) {
                if (awaitResultDecision(
                        getString(R.string.virustotal_quota_aborting), scan)
                        != DECISION_CONTINUE) {
                    abortHandoff(file,
                            getString(R.string.virustotal_quota_reason, scan.message),
                            getString(R.string.virustotal_quota_status));
                    return;
                }
            } else if (scan.status == VirusTotalScanner.Status.CLEAN) {
                runOnUiThread(() -> setStatus(
                        getString(R.string.virustotal_clean_continue)));
            } else if (scan.status != VirusTotalScanner.Status.CANCELLED
                    && scan.status != VirusTotalScanner.Status.DISABLED) {
                runOnUiThread(() -> setStatus(
                        getString(R.string.virustotal_unscanned_continue, scan.message)));
            }

            if (cancelled) {
                discardPending(file);
                return;
            }

            if (!servedFromCache) {
                DownloadCache.record(this, file, requestPkg, requestVersion,
                        resolvedVersionCodes, requestFileType);
            }
            long handoffStarted = System.currentTimeMillis();
            Uri uri = FileProvider.getUriForFile(
                    DownloadActivity.this,
                    getPackageName() + ".fileprovider",
                    file
            );
            Intent result = new Intent();
            result.setData(uri);
            result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (report != null) {
                report.setBuildCheck(requestVersionCodes != null
                        && requestVersionCodes.length > 0);
                report.finishSuccess(file);
                HelperLog.info(report.resultBlock());
            }
            pendingFile = null;
            HelperLog.info("Returning file " + file.getAbsolutePath()
                    + " (" + file.length() + " bytes)");
            setPauseVisible(false);
            runOnUiThread(() -> {
                setResult(RESULT_OK, result);
                setStatus(getString(R.string.download_done, file.getName()));
                finish();
                HelperLog.info("Hand-off complete in "
                        + (System.currentTimeMillis() - handoffStarted) + "ms");
                if (!servedFromCache) {
                    startPublicCopy(file);
                }
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
                setStatus(R.string.download_error_no_match);
                showFailure();
            });
        }
    }

    /**
     * Copies a validated file to the public/custom folder after the
     * FileProvider URI has been handed to Morphe. The copy runs on its own
     * thread and keeps the foreground service alive until it finishes.
     */
    private void startPublicCopy(File file) {
        Context app = getApplicationContext();
        new Thread(() -> {
            long started = System.currentTimeMillis();
            try {
                AppSettings.publishDownload(app, file);
            } finally {
                HelperLog.info("Public copy finished in "
                        + (System.currentTimeMillis() - started) + "ms");
                app.startService(new Intent(app, DownloadService.class)
                        .setAction(DownloadService.ACTION_STOP));
            }
        }, "publish-download").start();
    }

    private int awaitResultDecision(String message, VirusTotalScanner.Result scan) {
        synchronized (resultLock) {
            resultDecision = DECISION_NONE;
        }
        runOnUiThread(() -> {
            resultStage = true;
            scanNote.setVisibility(View.GONE);
            skipScanButton.setVisibility(View.GONE);
            setStatus(message);
            progress.setVisibility(View.VISIBLE);
            progress.setIndeterminate(true);
            resultActions.setVisibility(View.VISIBLE);
            boolean hasReport = scan.hash != null && !scan.hash.isEmpty();
            resultReport.setVisibility(hasReport ? View.VISIBLE : View.GONE);
            if (hasReport) {
                resultReport.setOnClickListener(v -> openVirusTotalReport(scan.hash));
            }
        });
        int decision;
        synchronized (resultLock) {
            while (resultDecision == DECISION_NONE && !cancelled) {
                try {
                    resultLock.wait(200L);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            decision = resultDecision;
            resultDecision = DECISION_NONE;
        }
        resultStage = false;
        runOnUiThread(() -> resultActions.setVisibility(View.GONE));
        return decision == DECISION_NONE ? DECISION_DELETE : decision;
    }

    private void completeResultDecision(int decision) {
        synchronized (resultLock) {
            resultDecision = decision;
            resultLock.notifyAll();
        }
    }

    private void openVirusTotalReport(String hash) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.virustotal.com/gui/file/" + hash)));
        } catch (Exception error) {
            HelperLog.error("Could not open VirusTotal report", error);
        }
    }

    private void discardPending(File file) {
        if (!keepPendingFile) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        pendingFile = null;
    }

    private void abortHandoff(File file, String reason, String status) {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        if (report != null) {
            report.finishFailure(reason);
            HelperLog.info(report.resultBlock());
        }
        HelperLog.error(reason, null);
        stopDownloadService();
        setPauseVisible(false);
        runOnUiThread(() -> {
            setStatus(status);
            showFailure();
        });
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

    /**
     * APKPure's page can open the APKPure app through apkpure://install-app.
     * A WebView cannot load that scheme and reports ERR_UNKNOWN_URL_SCHEME, so
     * intercept it and use the direct download endpoint instead.
     */
    private boolean handleApkPureAppScheme(String url) {
        if (url == null || !url.regionMatches(true, 0, "apkpure://", 0, 10)) {
            return false;
        }
        HelperLog.info("APKPure app scheme intercepted: " + url);
        if ("apkpure".equals(webSource) && !uptodownHandled
                && !TextUtils.isEmpty(uptodownPkg)) {
            String wantedType = isSplitType(uptodownFileType) ? "xapk" : "apk";
            String direct = buildApkPureUrl(uptodownPkg, requestVersionCodes,
                    uptodownVersion, wantedType);
            HelperLog.info("APKPure scheme fallback URL: " + direct);
            handleUptodownDownload(direct);
        }
        return true;
    }

    private void startApkMirrorWebFlow(String url, String pkg, String version,
                                       String fileType) {
        startWebFlow("apkmirror", url, pkg, version, fileType);
    }

    private void startApkCubeWebFlow(String url, String pkg, String version,
                                     String fileType) {
        startWebFlow("apkcube", url, pkg, version, fileType);
    }

    /**
     * APKCube's downloader accepts the package, an exact build number, and an
     * ABI as query parameters, so the WebView opens on the right result.
     */
    private String apkCubeUrl(String pkg, long[] codes, String[] abis) {
        StringBuilder url = new StringBuilder(
                "https://apkcube.com/apk-downloader?url=");
        try {
            url.append(URLEncoder.encode(pkg, "UTF-8"));
        } catch (Exception ignored) {
            url.append(pkg);
        }
        if (codes != null && codes.length > 0) {
            url.append("&target=version&vc=").append(codes[0]);
        }
        String abi = (abis != null && abis.length > 0) ? abis[0] : "arm64-v8a";
        if ("armeabi-v7a".equals(abi) || "arm64-v8a".equals(abi)) {
            url.append("&arch=").append(abi);
        } else {
            url.append("&arch=universal");
        }
        return url.append("&langs=standard&dpi=all").toString();
    }

    private void handoffToApkMirrorWeb(String url, String pkg, String version,
                                       String fileType) throws UptodownHandoffException {
        main.post(() -> {
            setStatus(R.string.download_opening_apkmirror);
            progress.setIndeterminate(true);
            startApkMirrorWebFlow(url, pkg, version, fileType);
        });
        throw new UptodownHandoffException();
    }

    private boolean isCloudflareBlock(IOException error) {
        String message = error == null ? null : error.getMessage();
        return message != null && message.contains("HTTP 403");
    }

    /**
     * APKMirror's HTTP client can be blocked or time out while the WebView is
     * still able to load and show the Cloudflare verification page. Treat
     * network failures like a block so the user gets that page instead of a
     * silent source failure.
     */
    private boolean isApkMirrorWebFallback(IOException error) {
        return SourceProbeRules.apkMirrorWebFallback(
                error == null ? null : error.getMessage());
    }

    private void startWebFlow(String source, String url, String pkg, String version,
                              String fileType) {
        webSource = source;
        webFlowStartedAt = System.currentTimeMillis();
        webBuildRejected = false;
        logWebFlowState("LOADING", url);
        if (webParentSourceKey == null) {
            webParentSourceKey = activeSourceKey;
        }
        pendingSourceKey = "uptodown".equals(source)
                ? SourceSettings.FALLBACK : webSourceKey();
        activeSourceKey = pendingSourceKey;
        String webLabel;
        if ("apkpure".equals(source)) {
            webLabel = getString(R.string.source_label_apkpure_webview);
        } else if ("apkmirror".equals(source)) {
            webLabel = getString(R.string.source_label_apkmirror_webview);
        } else if ("apkcube".equals(source)) {
            webLabel = getString(R.string.source_label_apkcube_webview);
        } else {
            webLabel = getString(R.string.source_label_uptodown_webview);
        }
        setSourceLabel(webLabel);
        uptodownPkg = pkg;
        uptodownVersion = version;
        uptodownFileType = fileType;
        uptodownReferer = url;
        webOriginalUrl = url;
        uptodownHandled = false;
        webChallenge = false;
        webPageFinished = false;
        webLastProgressAt = System.currentTimeMillis();
        webHintShown = false;
        webReadyLogged = false;
        webNotFoundProbes = 0;
        cancelChallengeTimeout();
        cancelAdBlockRetry();
        blockWebAds = AppSettings.blockWebAds(this);
        liveAdGuard = blockWebAds && AppSettings.liveAdGuardChecks(this);
        synchronized (adBlockLock) {
            blockedAdRequests = 0;
            blockedAdHosts.clear();
        }

        if ("apkpure".equals(source)) {
            uptodownTitle.setText(R.string.download_apkpure_title);
            uptodownExplanation.setText(R.string.download_apkpure_explanation);
        } else if ("apkmirror".equals(source)) {
            uptodownTitle.setText(R.string.download_apkmirror_title);
            uptodownExplanation.setText(R.string.download_apkmirror_explanation);
        } else if ("apkcube".equals(source)) {
            uptodownTitle.setText(R.string.download_apkcube_title);
            uptodownExplanation.setText(R.string.download_apkcube_explanation);
        } else {
            uptodownTitle.setText(R.string.download_uptodown_title);
            uptodownExplanation.setText(R.string.download_uptodown_explanation);
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
        // Keep the WebView cache and cookies. APKMirror's Cloudflare clearance
        // and the challenge assets are then reused across loads and app starts;
        // clearing them forced a fresh verification every time.
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                webPageFinished = false;
                webLastProgressAt = System.currentTimeMillis();
                webReadyLogged = false;
                logWebFlowState("PAGE_STARTED", url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (handleApkPureAppScheme(url)) {
                    return true;
                }
                if (url.startsWith("morphe-helper://build-mismatch")) {
                    rejectWebBuild(source, preDownloadBuildMessage(
                            request.getUrl().getQueryParameter("code")));
                    return true;
                }
                if (url.startsWith("morphe-helper://apkcube-not-found")) {
                    handleApkCubeNotFound(source);
                    return true;
                }
                if ("apkcube".equals(source) && !isApkCubeNavigationAllowed(url)) {
                    HelperLog.info("APKCube blocked off-site navigation: " + url);
                    return true;
                }
                if (blockWebAds && WebAdBlocker.shouldBlock(DownloadActivity.this,
                        request.getUrl().getHost())) {
                    recordBlockedAd(request.getUrl().getHost());
                    HelperLog.info(webSource + " blocked ad navigation: " + request.getUrl());
                    return true;
                }
                return handleUptodownUrl(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (handleApkPureAppScheme(url)) {
                    return true;
                }
                if (url.startsWith("morphe-helper://build-mismatch")) {
                    rejectWebBuild(source, preDownloadBuildMessage(
                            Uri.parse(url).getQueryParameter("code")));
                    return true;
                }
                if (url.startsWith("morphe-helper://apkcube-not-found")) {
                    handleApkCubeNotFound(source);
                    return true;
                }
                if ("apkcube".equals(source) && !isApkCubeNavigationAllowed(url)) {
                    HelperLog.info("APKCube blocked off-site navigation: " + url);
                    return true;
                }
                String host = Uri.parse(url).getHost();
                if (blockWebAds && WebAdBlocker.shouldBlock(DownloadActivity.this, host)) {
                    recordBlockedAd(host);
                    HelperLog.info(webSource + " blocked ad navigation: " + url);
                    return true;
                }
                return handleUptodownUrl(url);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                if (!blockWebAds || request.isForMainFrame()) {
                    return null;
                }
                String host = request.getUrl().getHost();
                if (WebAdBlocker.shouldBlock(DownloadActivity.this, host)
                        || (liveAdGuard && WebAdBlocker.shouldBlockLive(
                        DownloadActivity.this, host))) {
                    recordBlockedAd(host);
                    return WebAdBlocker.emptyResponse();
                }
                return null;
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                if ("apkcube".equals(webSource)) {
                    view.evaluateJavascript(apkCubeNotFoundScript(), null);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                webPageFinished = true;
                webLastProgressAt = System.currentTimeMillis();
                webReadyLogged = false;
                HelperLog.info(webSource + " page finished: " + url);
                logWebFlowState("PAGE_FINISHED", url);
                if (blockWebAds) {
                    view.evaluateJavascript(WebAdBlocker.cosmeticScript(), null);
                    String summary = adBlockerSummary();
                    if (!summary.isEmpty()) {
                        HelperLog.info(webSource + " ad blocker: " + summary);
                    }
                }
                scheduleWebFlowTimeout(view);
                uptodownReferer = url;
                if ("apkmirror".equals(webSource)) {
                    cacheApkMirrorAppUrlIfMatches(view, url);
                    // Persist the Cloudflare clearance cookie now rather than
                    // relying on a later best-effort flush.
                    CookieManager.getInstance().flush();
                }
                if ("apkcube".equals(webSource)) {
                    // APKCube can show "No match" only briefly before reloading.
                    // Watch the DOM so a transient result still fails the source.
                    view.evaluateJavascript(apkCubeNotFoundScript(), null);
                }
                if ("uptodown".equals(webSource)) {
                    // Uptodown's download button needs a real user gesture to
                    // obtain a Turnstile token. A programmatic click does not
                    // count, so just dismiss the cookie banner and let the user
                    // tap the green Download button.
                    view.evaluateJavascript(
                            "(function(){var n=0;"
                                    + "function clickReject(doc){try{"
                                    + "var els=doc.querySelectorAll('button,a,[role=button],[role=link]');"
                                    + "for(var i=0;i<els.length;i++){var e=els[i];"
                                    + "var t=(e.innerText||e.textContent||'').replace(/\\s+/g,' ').trim().toLowerCase();"
                                    + "if(!t||t.length>40)continue;"
                                    + "if(t==='reject all'||t==='reject'||t==='decline'||t==='refuse'"
                                    + "||t==='only necessary'||t==='necessary only'"
                                    + "||t.indexOf('reject all')>=0||t.indexOf('only necessary')>=0){"
                                    + "e.click();return true;}}"
                                    + "var fs=doc.querySelectorAll('iframe');"
                                    + "for(var j=0;j<fs.length;j++){try{if(fs[j].contentDocument"
                                    + "&&clickReject(fs[j].contentDocument))return true;}catch(err){}}"
                                    + "}catch(err){}return false;}"
                                    + "var t=setInterval(function(){n++;clickReject(document);"
                                    + "if(n>60)clearInterval(t);},500);})();",
                            null);
                    view.evaluateJavascript(
                            "(function(){var w=document.getElementById('download-turnstile-widget');"
                                    + "var i=w?w.querySelector('iframe'):null;"
                                    + "var r=i?i.getBoundingClientRect():null;"
                                    + "return JSON.stringify({w:!!w,"
                                    + "interactive:!!(w&&w.classList.contains('interactive')),"
                                    + "iframe:!!i,iw:r?Math.round(r.width):0,ih:r?Math.round(r.height):0,"
                                    + "ts:!!window.turnstile,"
                                    + "btn:!!document.getElementById('detail-download-button')});})()",
                            value -> HelperLog.info("Uptodown DOM " + value));
                } else if ("apkmirror".equals(webSource)) {
                    view.evaluateJavascript(apkMirrorWebScript(), null);
                } else if ("apkcube".equals(webSource)) {
                    view.evaluateJavascript(apkCubeWebScript(), null);
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
                                    + "setTimeout(function(){a.click();},100);return;}"
                                    + "var bs=document.querySelectorAll('a,button');"
                                    + "for(var k=0;k<bs.length;k++){var bt=(bs[k].innerText||'').toLowerCase();"
                                    + "if(bt.indexOf('download apk')>=0&&bt.indexOf('apkpure app')<0){"
                                    + "clearInterval(t);bs[k].scrollIntoView({block:'center'});"
                                    + "setTimeout(function(){bs[k].click();},100);return;}}"
                                    + "if(n>100)clearInterval(t);},350);})();",
                            null);
                }
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request == null || !request.isForMainFrame()) {
                    return;
                }
                int code = error == null ? -1 : error.getErrorCode();
                String description = error == null
                        ? "unknown error" : String.valueOf(error.getDescription());
                logWebFlowState("ERROR", "code=" + code + " " + description
                        + " " + request.getUrl());
                failWebFlowFast(description);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                           WebResourceResponse response) {
                if (request == null || !request.isForMainFrame() || response == null) {
                    return;
                }
                int status = response.getStatusCode();
                logWebFlowState("HTTP_ERROR", "status=" + status
                        + " " + request.getUrl());
                // 403/429/5xx can be a Cloudflare challenge or a transient page;
                // only fail immediately for a permanently missing page.
                if (status == 404 || status == 410) {
                    failWebFlowFast("HTTP " + status);
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view,
                                               RenderProcessGoneDetail detail) {
                logWebFlowState("RENDERER_GONE", detail != null
                        && detail.didCrash() ? "crashed" : "stopped");
                failWebFlowFast("WebView renderer stopped");
                return true;
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
                if (event.getAction() == MotionEvent.ACTION_UP
                        && "uptodown".equals(webSource)) {
                    main.postDelayed(this::logUptodownDom, 2_000);
                }
            }
            return false;
        });

        uptodownWebContainer.removeAllViews();
        uptodownWebContainer.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        uptodownSection.setVisibility(View.VISIBLE);
        scheduleVerificationHint();
        scheduleWebFlowTimeout(web);
        web.loadUrl(url);
        scheduleAdBlockRetry(web);
        scheduleWebFlowProbe(web);
    }

    /**
     * Remembers the APKMirror app page reached by the WebView. The next request
     * for the same package can open the app page directly instead of repeating
     * the search (and the search's Cloudflare check).
     */
    /**
     * Caches the APKMirror app page reached through the WebView only when its
     * Play Store link matches the request. A search result or challenge page
     * for another app must not poison the cache for later requests.
     */
    private void cacheApkMirrorAppUrlIfMatches(WebView view, String url) {
        if (uptodownPkg == null || uptodownPkg.isEmpty()) {
            return;
        }
        view.evaluateJavascript(
                "(function(){var a=document.querySelector('a[href*=\"play.google.com/store/apps/details?id=\"]');"
                        + "if(!a)return '';var m=a.href.match(/[?&]id=([A-Za-z0-9._]+)/);"
                        + "return m?m[1]:'';})()",
                value -> {
                    String found = value == null ? "" : value.replace("\"", "").trim();
                    if (uptodownPkg.equals(found)) {
                        cacheApkMirrorAppUrl(url);
                    } else {
                        HelperLog.info("APKMirror WebView page is " + found
                                + ", not caching for " + uptodownPkg);
                    }
                });
    }

    private void cacheApkMirrorAppUrl(String url) {
        if (uptodownPkg == null || uptodownPkg.isEmpty() || url == null) {
            return;
        }
        if (!isHost(hostOf(url), "apkmirror.com")) {
            return;
        }
        String appPath = ApkMirrorSearch.appPath(url);
        if (appPath.isEmpty()) {
            return;
        }
        String appUrl = APKMIRROR + appPath;
        if (!appUrl.equals(AppSettings.apkMirrorAppUrl(this, uptodownPkg))) {
            AppSettings.setApkMirrorAppUrl(this, uptodownPkg, appUrl);
            HelperLog.info("APKMirror cached app URL: " + appUrl);
        }
    }

    /**
     * Diagnostic snapshot of the Uptodown Turnstile state after a user tap.
     * Helps distinguish "no widget", "widget waiting for interaction", and
     * "widget rendered but stuck" in bug reports.
     */
    private void logUptodownDom() {
        WebView web = uptodownWebView;
        if (web == null) {
            return;
        }
        web.evaluateJavascript(
                "(function(){"
                        + "var w=document.getElementById('download-turnstile-widget');"
                        + "var i=w?w.querySelector('iframe'):null;"
                        + "var r=i?i.getBoundingClientRect():null;"
                        + "var fs=document.querySelectorAll('iframe');var cf=[];"
                        + "for(var k=0;k<fs.length;k++){var s=fs[k].src||'';"
                        + "if(s.indexOf('challenges.cloudflare.com')>=0){"
                        + "var q=fs[k].getBoundingClientRect();"
                        + "cf.push(Math.round(q.width)+'x'+Math.round(q.height));}}"
                        + "var b=document.getElementById('detail-download-button');"
                        + "return JSON.stringify({w:!!w,"
                        + "interactive:!!(w&&w.classList.contains('interactive')),"
                        + "iframe:!!i,iw:r?Math.round(r.width):0,ih:r?Math.round(r.height):0,"
                        + "ts:!!window.turnstile,btn:!!b,"
                        + "btnClass:b?b.className:'',cf:cf.join(',')});})()",
                value -> HelperLog.info("Uptodown DOM after tap " + value));
    }

    /**
     * Caches the app icon from an APKMirror app page before the APK itself is
     * ready. This is what lets an uninstalled app show its real icon while the
     * download is still running.
     */
    private void cacheSourceIcon(String html, String pkg) {
        if (html == null || pkg == null || pkg.isEmpty()) {
            return;
        }
        String icon = firstMatch(html,
                "<meta[^>]+property=\"og:image\"[^>]+content=\"([^\"]+)\"");
        if (icon == null) {
            icon = firstMatch(html,
                    "<meta[^>]+content=\"([^\"]+)\"[^>]+property=\"og:image\"");
        }
        if (icon == null) {
            return;
        }
        icon = decodeHtml(icon).trim();
        if (icon.startsWith("//")) {
            icon = "https:" + icon;
        }
        if (!icon.startsWith("http")) {
            return;
        }
        final String key = AppIconCache.key(pkg, requestVersion);
        if (AppIconCache.load(this, key) != null) {
            return;
        }
        final String iconUrl = icon;
        // The icon is best-effort and must not delay the APK resolution.
        new Thread(() -> {
            AppIconCache.saveFromUrl(getApplicationContext(), key, iconUrl);
            main.post(this::updateDownloadDetails);
        }, "app-icon").start();
    }

    /**
     * Builds the APKMirror WebView navigator. APKMirror may serve a Cloudflare
     * challenge to the HTTP client while letting the WebView through, so the
     * WebView follows the same chain as the HTTP resolver: search result,
     * release page, variant page, and finally the download link.
     */
    private String apkMirrorWebScript() {
        String pkg = JSONObject.quote(uptodownPkg == null ? "" : uptodownPkg);
        String version = JSONObject.quote(uptodownVersion == null ? "" : uptodownVersion);
        String name = JSONObject.quote(requestedAppName == null ? "" : requestedAppName);
        String type = JSONObject.quote(uptodownFileType == null ? "" : uptodownFileType);
        String abi = JSONObject.quote(
                requestAbis != null && requestAbis.length > 0 ? requestAbis[0] : "");
        StringBuilder codesBuilder = new StringBuilder("[");
        if (requestVersionCodes != null) {
            for (int i = 0; i < requestVersionCodes.length; i++) {
                if (i > 0) {
                    codesBuilder.append(',');
                }
                codesBuilder.append(requestVersionCodes[i]);
            }
        }
        String codes = codesBuilder.append(']').toString();
        String allowSplit = allowSplitArchive ? "true" : "false";
        return """
                (function(){
                if(window.__mhApkMirrorStarted)return;
                window.__mhApkMirrorStarted=true;
                var pkg=__MH_PKG__,ver=__MH_VER__,name=__MH_NAME__,fileType=__MH_TYPE__,abi=__MH_ABI__;
                var codes=__MH_CODES__,allowSplit=__MH_ALLOW_SPLIT__;
                var nameLower=(name||'').toLowerCase();
                var nameSlug=nameLower.replace(/[^a-z0-9]+/g,'-').replace(/^-+|-+$/g,'');
                var pkgParts=(pkg||'').toLowerCase().split('.').filter(function(p){
                    return p.length>2&&p!=='com'&&p!=='org'&&p!=='net'&&p!=='android';
                });
                var verSlug=(ver||'').toLowerCase().split('.').join('-');
                var verCore=(ver||'').split('-')[0];
                var verCoreSlug=(verCore||'').toLowerCase().split('.').join('-');
                function txt(el){return (el&&el.innerText||'').trim();}
                function digits(s){return (s||'').replace(/[^0-9]/g,'');}
                function hasVer(s){
                    var d=digits(s);
                    if(ver&&d.indexOf(digits(ver))>=0)return true;
                    return verCore&&verCore!==ver&&d.indexOf(digits(verCore))>=0;
                }
                function pathOf(href){
                    try{return new URL(href,location.href).pathname.toLowerCase();}
                    catch(e){return (href||'').toLowerCase();}
                }
                function isRelease(p){return p.endsWith('-release/');}
                function isVariant(p){
                    return p.endsWith('-android-apk-download/')
                        || p.endsWith('-android-apkm-download/')
                        || p.endsWith('-android-apks-download/');
                }
                function rowCode(row){
                    if(!row)return -1;
                    // The selected build code is the first colorLightBlack cell.
                    var code=row.querySelector('.colorLightBlack');
                    if(code){
                        var ct=(code.innerText||'').trim();
                        if(/^\\d+$/.test(ct))return parseInt(ct,10);
                    }
                    var cells=row.querySelectorAll('.table-cell');
                    for(var c=0;c<cells.length;c++){
                        var cellText=(cells[c].innerText||'').trim();
                        if(/^\\d+$/.test(cellText))return parseInt(cellText,10);
                    }
                    for(var k=0;k<codes.length;k++){
                        if(new RegExp('\\\\b'+codes[k]+'\\\\b').test(row.innerText||''))
                            return codes[k];
                    }
                    var m=(row.innerText||'').match(/\\b\\d{6,}\\b/g);
                    if(!m)return -1;
                    for(var i=0;i<m.length;i++){
                        var v=parseInt(m[i],10);
                        if(!isNaN(v)&&v>0)return v;
                    }
                    return -1;
                }
                function variantBuildCode(){
                    var body=txt(document.body);
                    var m=body.match(/(?:Build|Version code|versionCode)\\s*:?\\s*(\\d{6,})/i);
                    if(m)return parseInt(m[1],10);
                    // APKMirror variant pages put the selected build code before
                    // the list of other variants. Use the first long number as a
                    // fallback when the page has no explicit Build label.
                    var first=body.match(/\\b\\d{6,}\\b/);
                    if(first)return parseInt(first[0],10);
                    var nodes=document.querySelectorAll('.table-row,.table-cell');
                    for(var i=0;i<nodes.length;i++){
                        var t=txt(nodes[i]);
                        if(t.length>160)continue;
                        if(/Build|Version code|versionCode/i.test(t)){
                            var n=t.match(/\\b\\d{6,}\\b/);
                            if(n)return parseInt(n[0],10);
                        }
                    }
                    return -1;
                }
                function segments(p){
                    return p.split('/').filter(function(s){return s.length>0;});
                }
                function isApkPath(p){
                    var s=segments(p);return s.length>=3&&s[0]==='apk';
                }
                function isAppPage(p){
                    var s=segments(p);return s.length===3&&s[0]==='apk';
                }
                function consent(){
                    var cs=['#accept-btn','.accept-btn','#onetrust-accept-btn-handler',
                        '.fc-cta-consent','.cky-btn-accept','.osano-cm-accept-all',
                        '#didomi-notice-agree-button','button[data-testid=uc-accept-all-button]'];
                    for(var i=0;i<cs.length;i++){
                        var el=document.querySelector(cs[i]);
                        if(el){try{el.click();}catch(e){}}
                    }
                    var bs=document.querySelectorAll('button,a');
                    for(var j=0;j<bs.length;j++){
                        var t=txt(bs[j]).toLowerCase();
                        if(t==='accept'||t==='accept all'||t==='accept cookies'
                            ||t==='i agree'||t==='agree'||t==='consent'
                            ||t==='allow all'||t==='akkoord'){
                            try{bs[j].click();}catch(e){}
                        }
                    }
                }
                function hidePremiumBanner(){
                    var nodes=document.querySelectorAll('div,section,aside,iframe');
                    for(var i=0;i<nodes.length;i++){
                        var el=nodes[i];
                        var style=getComputedStyle(el);
                        if(style.position!=='fixed'&&style.position!=='sticky')continue;
                        var rect=el.getBoundingClientRect();
                        if(rect.width<innerWidth*0.55||rect.height<70)continue;
                        if(rect.bottom<innerHeight*0.55)continue;
                        var label=(el.id+' '+el.className+' '+(el.innerText||'')).toLowerCase();
                        if(label.indexOf('premium')>=0||label.indexOf('no ads, dark mode')>=0
                            ||label.indexOf('$10/quarter')>=0||label.indexOf('advert')>=0){
                            el.style.setProperty('display','none','important');
                            el.style.setProperty('visibility','hidden','important');
                        }
                    }
                }
                function pick(){
                    var p=location.pathname.toLowerCase();
                    var q=location.search.toLowerCase();
                    if(q.indexOf('searchtype=')>=0||/(^|[?&])s=/.test(q)){
                        var best=null,bestScore=-1,bestRelease=null,bestReleaseScore=-1;
                        var bestApp=null,bestAppScore=-1,firstRel=null,ls=document.querySelectorAll('a');
                        for(var i=0;i<ls.length;i++){
                            var a=ls[i],h=a.href||'';
                            if(!h||h.indexOf('#')>=0)continue;
                            var hp=pathOf(h);
                            if(!isApkPath(hp))continue;
                            var rel=isRelease(hp),appPage=isAppPage(hp);
                            if(!rel&&!appPage)continue;
                            var t=txt(a).toLowerCase();
                            if(!t)continue;
                            var seg=segments(hp);
                            var app=seg.length>2?seg[2]:'';
                            var score=0;
                            if(nameSlug&&app===nameSlug)score+=120;
                            else if(nameSlug&&app.indexOf(nameSlug)>=0)score+=60;
                            for(var j=0;j<pkgParts.length;j++){
                                if(app===pkgParts[j])score+=80;
                                else if(app.indexOf(pkgParts[j])>=0)score+=30;
                            }
                            if(nameLower&&t.indexOf(nameLower)>=0)score+=60;
                            if(rel){
                                if(!firstRel)firstRel=a;
                                if(ver&&!(hp.indexOf(verSlug)>=0||hp.indexOf(verCoreSlug)>=0||hasVer(t)||hasVer(hp)))continue;
                                score+=20;
                                if(score>bestReleaseScore){bestReleaseScore=score;bestRelease=a;}
                            }else if(appPage){
                                if(score>bestAppScore){bestAppScore=score;bestApp=a;}
                            }
                            if(score>bestScore){bestScore=score;best=a;}
                        }
                        if(bestRelease&&bestReleaseScore>=60)return bestRelease;
                        if(bestApp&&bestAppScore>=60)return bestApp;
                        if(!ver&&best&&bestScore>=60)return best;
                        if(firstRel){
                            var relPath=pathOf(firstRel.href);
                            var slash=relPath.lastIndexOf('/');
                            if(slash>0)return relPath.substring(0,slash+1);
                        }
                        return null;
                    }
                    if(isRelease(p)){
                        if(ver&&!(p.indexOf(verSlug)>=0||p.indexOf(verCoreSlug)>=0||hasVer(p)))return null;
                        var vs=document.querySelectorAll('a');
                        var variants=[];
                        for(var v=0;v<vs.length;v++){
                            var va=vs[v],vh=va.href||'';
                            if(!vh||vh.indexOf('#')>=0)continue;
                            var vp=pathOf(vh);
                            if(!isVariant(vp))continue;
                            var row=va.closest('.table-row'),rt=txt(row).toLowerCase();
                            if(!rt){
                                var par=va.parentElement;
                                for(var u=0;u<4&&par;u++){
                                    rt=txt(par).toLowerCase();
                                    if(rt.indexOf('apk')>=0||rt.indexOf('bundle')>=0)break;
                                    par=par.parentElement;
                                }
                            }
                            // "BUNDLE n" is APKMirror's split badge, not the file
                            // format. The URL suffix is the authoritative format.
                            var isApkm=vp.endsWith('-android-apkm-download/');
                            var isApks=vp.endsWith('-android-apks-download/');
                            var rc=rowCode(row);
                            variants.push({
                                a:va,
                                apk:!isApkm&&!isApks,
                                apkm:isApkm,
                                apks:isApks,
                                exact:rc>0&&codes.indexOf(rc)>=0,
                                abi:!!abi&&rt.indexOf(abi.toLowerCase())>=0,
                                universal:rt.indexOf('universal')>=0
                            });
                        }
                        var pickVariant=function(wantApkm,wantApks){
                            var i,c;
                            var matches=function(x){
                                return (wantApkm&&x.apkm)||(wantApks&&x.apks)
                                    ||(!wantApkm&&!wantApks&&x.apk);
                            };
                            for(i=0;i<variants.length;i++){
                                c=variants[i];
                                if(c.exact&&c.abi&&matches(c))return c.a;
                            }
                            for(i=0;i<variants.length;i++){
                                c=variants[i];
                                if(c.exact&&matches(c))return c.a;
                            }
                            for(i=0;i<variants.length;i++){
                                c=variants[i];
                                if(c.exact&&c.abi)return c.a;
                            }
                            for(i=0;i<variants.length;i++){
                                if(variants[i].exact)return variants[i].a;
                            }
                            for(i=0;i<variants.length;i++){
                                c=variants[i];
                                if(c.universal&&matches(c))return c.a;
                            }
                            for(i=0;i<variants.length;i++){
                                c=variants[i];
                                if(c.abi&&matches(c))return c.a;
                            }
                            for(i=0;i<variants.length;i++){
                                if(matches(variants[i]))return variants[i].a;
                            }
                            return null;
                        };
                        var ft=(fileType||'').toLowerCase();
                        var chosen=null;
                        if(ft==='apkm'){
                            chosen=pickVariant(true,false)||pickVariant(false,false)
                                ||pickVariant(false,true);
                        }else if(ft==='apks'){
                            chosen=pickVariant(false,true)||pickVariant(false,false)
                                ||pickVariant(true,false);
                        }else{
                            chosen=pickVariant(false,false);
                            if(!chosen&&allowSplit){
                                chosen=pickVariant(true,false)||pickVariant(false,true);
                            }
                        }
                        if(!chosen&&variants.length)chosen=variants[0].a;
                        return chosen;
                    }
                    if(isAppPage(p)){
                        var rs=document.querySelectorAll('a'),picked=null,first=null;
                        for(var r=0;r<rs.length;r++){
                            var ra=rs[r],rh=ra.href||'';
                            if(!rh||rh.indexOf('#')>=0)continue;
                            if(!isRelease(pathOf(rh)))continue;
                            if(!first)first=ra;
                            if(ver&&(rh.toLowerCase().indexOf(verSlug)>=0
                                ||rh.toLowerCase().indexOf(verCoreSlug)>=0
                                ||hasVer(txt(ra)))){
                                picked=ra;break;
                            }
                        }
                        return picked||(ver?null:first);
                    }
                    if(isVariant(p)){
                        if(codes.length>0){
                            var vc=variantBuildCode();
                            if(vc>0&&codes.indexOf(vc)<0){
                                clearInterval(t);
                                location.href='morphe-helper://build-mismatch?code='
                                    +encodeURIComponent(vc);
                                return null;
                            }
                        }
                        var ds=document.querySelectorAll('a,button');
                        for(var d=0;d<ds.length;d++){
                            var dh=ds[d].href||'';
                            if(dh.indexOf('download.php')>=0||dh.indexOf('/download/?key=')>=0){
                                return ds[d];
                            }
                            var dt=txt(ds[d]).toLowerCase();
                            if(dt.indexOf('download apk')>=0){
                                return ds[d];
                            }
                        }
                        return null;
                    }
                    var ls2=document.querySelectorAll('a,button');
                    for(var l=0;l<ls2.length;l++){
                        var lh=ls2[l].href||'';
                        if(lh.indexOf('download.php')>=0||lh.indexOf('/download/?key=')>=0){
                            return ls2[l];
                        }
                        var lt=txt(ls2[l]).toLowerCase();
                        if(lt.indexOf('download apk')>=0){
                            return ls2[l];
                        }
                    }
                    return null;
                }
                hidePremiumBanner();
                var n=0,t=setInterval(function(){
                    n++;
                    if(n%10===1)consent();
                    if(n%5===1)hidePremiumBanner();
                    var target=pick();
                    if(target){
                        clearInterval(t);
                        if(typeof target==='string'){location.href=target;return;}
                        try{target.scrollIntoView({block:'center'});}catch(e){}
                        setTimeout(function(){try{target.click();}catch(e){}},80);
                        return;
                    }
                    if(n>900)clearInterval(t);
                },200);
                })();
                """.replace("__MH_PKG__", pkg)
                .replace("__MH_VER__", version)
                .replace("__MH_NAME__", name)
                .replace("__MH_TYPE__", type)
                .replace("__MH_ABI__", abi)
                .replace("__MH_CODES__", codes)
                .replace("__MH_ALLOW_SPLIT__", allowSplit);
    }

    /**
     * APKCube renders its downloader with JavaScript. The URL already selects
     * the requested build and ABI; this script follows the result link to the
     * exact version page. The final Download button and any Turnstile step are
     * left to the user, so the helper never presses the site's button itself.
     */
    private String apkCubeWebScript() {
        return """
                (function(){
                if(window.__mhApkCubeStarted)return;
                window.__mhApkCubeStarted=true;
                var ver=__MH_VER__;
                var n=0;
                var timer=setInterval(function(){
                    n++;
                    if(n>200){clearInterval(timer);return;}
                    var path=location.pathname.toLowerCase();
                    var query=location.search.toLowerCase();
                    var requestedVersion=ver&&ver!=='any';
                    if(path.endsWith('/download')&&requestedVersion
                        &&query.indexOf('version=')<0
                        &&(query.indexOf('vc=')>=0||query.length===0)){
                        clearInterval(timer);
                        location.href=path+'?version='+encodeURIComponent(ver);
                        return;
                    }
                    if(path.indexOf('/apk-downloader')<0)return;
                    var links=document.querySelectorAll('a[href]');
                    for(var i=0;i<links.length;i++){
                        var href=links[i].href||'';
                        if(href.indexOf('/download?')<0)continue;
                        clearInterval(timer);
                        location.href=href;
                        return;
                    }
                },250);
                })();
                """
                .replace("__MH_VER__", JSONObject.quote(
                        uptodownVersion == null ? "" : uptodownVersion));
    }

    /**
     * Watches APKCube's client-rendered result for a no-match message and
     * reports it immediately. The page can show the message briefly before it
     * reloads, which the two-second native probe can miss.
     */
    static String apkCubeNotFoundScript() {
        return """
                (function(){
                if(window.__mhApkCubeNotFoundWatch)return;
                window.__mhApkCubeNotFoundWatch=true;
                function pageText(){
                    return ((document.title||'')+' '
                        +(document.body?document.body.innerText:'')).toLowerCase();
                }
                function isNotFound(t){
                    return t.indexOf('app not found')>=0
                        || t.indexOf('app is not found')>=0
                        || t.indexOf('no app found')>=0
                        || t.indexOf('no match')>=0
                        || t.indexOf('no results for')>=0
                        || t.indexOf("we don't hold that package")>=0
                        || t.indexOf("isn't in the catalog")>=0
                        || t.indexOf('not in the catalog')>=0
                        || t.indexOf("couldn't find that app")>=0
                        || t.indexOf('could not find that app')>=0
                        || t.indexOf("couldn't find that package")>=0
                        || t.indexOf('could not find that package')>=0
                        || t.indexOf("couldn't be fetched")>=0
                        || t.indexOf('could not be fetched')>=0
                        || t.indexOf("couldn't start the fetch")>=0
                        || t.indexOf('could not start the fetch')>=0
                        || t.indexOf('no longer available')>=0;
                }
                function check(){
                    if(window.__mhApkCubeNotFound)return;
                    if(!isNotFound(pageText()))return;
                    window.__mhApkCubeNotFound=true;
                    location.href='morphe-helper://apkcube-not-found';
                }
                var scheduled=false;
                function schedule(){
                    if(scheduled||window.__mhApkCubeNotFound)return;
                    scheduled=true;
                    setTimeout(function(){
                        scheduled=false;
                        check();
                    },100);
                }
                check();
                if(window.MutationObserver&&document.documentElement){
                    new MutationObserver(schedule).observe(document.documentElement,{
                        subtree:true,childList:true,characterData:true,attributes:true
                    });
                }
                var n=0;
                var timer=setInterval(function(){
                    n++;
                    check();
                    if(n>600)clearInterval(timer);
                },500);
                })();
                """;
    }

    /** Stops the APKCube WebView after its own page reported no match. */
    private void handleApkCubeNotFound(String source) {
        if (!"apkcube".equals(source) || uptodownHandled) {
            return;
        }
        logWebFlowState("NOT_FOUND", "page");
        failWebFlowFast(getString(R.string.download_error_apkcube_not_found));
    }

    private String preDownloadBuildMessage(String code) {
        String actual = TextUtils.isEmpty(code) ? "unknown" : code;
        return "APKMirror page is build " + actual + ", expected "
                + java.util.Arrays.toString(requestVersionCodes);
    }

    private void rejectWebBuild(String source, String message) {
        if (webBuildRejected || switchingSource) {
            return;
        }
        webBuildRejected = true;
        cancelWebFlowTimeout();
        boolean fromFallback = SourceSettings.FALLBACK.equals(webParentSourceKey);
        if ("uptodown".equals(source)) {
            uptodownRejected = true;
        } else if (!fromFallback) {
            apkmirrorRejected = true;
        }
        if (fromFallback) {
            // The fallback link itself pointed at this bad build. Reject it, but
            // keep the provider source available for its own catalogue search.
            fallbackRejected = true;
            markSource(SourceSettings.FALLBACK, false, message);
            HelperLog.info("Fallback source rejected after "
                    + webSourceName() + " build mismatch");
        }
        verificationFailure = message;
        HelperLog.info(webSourceName() + " build rejected: "
                + message + "; retrying other sources");
        if (cancelled) {
            return;
        }
        continueAfterWebFailure(message);
    }

    private void scheduleWebFlowTimeout(WebView web) {
        cancelWebFlowTimeout();
        if (uptodownHandled || webChallenge) {
            return;
        }
        webFlowTimeout = () -> {
            if (uptodownHandled || uptodownWebView != web) {
                return;
            }
            uptodownHandled = true;
            logWebFlowState("TIMEOUT", "");
            HelperLog.info(webSource + " timed out waiting for the download page");
            continueAfterWebFailure(
                    getString(R.string.download_error_page_timeout_detail));
        };
        // Uptodown shows a Turnstile widget inside the download page and can
        // legitimately wait for a user interaction. Give it more room than the
        // normal page watchdog; the probe cancels this entirely when it sees
        // the widget.
        long timeout = "uptodown".equals(webSource) ? 180_000 : 90_000;
        main.postDelayed(webFlowTimeout, timeout);
    }

    private void cancelWebFlowTimeout() {
        if (webFlowTimeout != null) {
            main.removeCallbacks(webFlowTimeout);
            webFlowTimeout = null;
        }
    }

    private void scheduleWebFlowProbe(WebView web) {
        cancelWebFlowProbe();
        webFlowProbe = new Runnable() {
            @Override
            public void run() {
                if (uptodownHandled || uptodownWebView != web) {
                    return;
                }
                probeWebFlowState(web);
                main.postDelayed(this, 2_000);
            }
        };
        main.postDelayed(webFlowProbe, 1_200);
    }

    private void cancelWebFlowProbe() {
        if (webFlowProbe != null) {
            main.removeCallbacks(webFlowProbe);
            webFlowProbe = null;
        }
        cancelChallengeTimeout();
    }

    private void cancelChallengeTimeout() {
        if (challengeTimeout != null) {
            main.removeCallbacks(challengeTimeout);
            challengeTimeout = null;
        }
    }

    /** Reads whether the page is a verification challenge or a normal page. */
    private void probeWebFlowState(WebView web) {
        String notFoundCheck = "";
        if ("apkcube".equals(webSource)) {
            notFoundCheck = "if(t.indexOf('app not found')>=0"
                    + "||t.indexOf('app is not found')>=0"
                    + "||t.indexOf('no app found')>=0"
                    + "||t.indexOf('no match')>=0"
                    + "||t.indexOf('no results for')>=0"
                    + "||t.indexOf('isn\\'t in the catalog')>=0"
                    + "||t.indexOf('not in the catalog')>=0"
                    + "||t.indexOf('don\\'t hold that package')>=0"
                    + "||t.indexOf('do not hold that package')>=0"
                    + "||t.indexOf('couldn\\'t find that app')>=0"
                    + "||t.indexOf('could not find that app')>=0"
                    + "||t.indexOf('couldn\\'t find that package')>=0"
                    + "||t.indexOf('could not find that package')>=0"
                    + "||t.indexOf('couldn\\'t be fetched')>=0"
                    + "||t.indexOf('could not be fetched')>=0"
                    + "||t.indexOf('couldn\\'t start the fetch')>=0"
                    + "||t.indexOf('could not start the fetch')>=0"
                    + "||t.indexOf('no longer available')>=0)"
                    + "return 'notfound';";
        }
        web.evaluateJavascript(
                "(function(){"
                        + "var t=((document.title||'')+' '"
                        + "+(document.body?document.body.innerText:''))"
                        + ".toLowerCase();"
                        + notFoundCheck
                        + "var fs=document.querySelectorAll('iframe');"
                        + "for(var i=0;i<fs.length;i++){"
                        + "var src=(fs[i].src||'').toLowerCase();"
                        + "if(src.indexOf('challenges.cloudflare.com')<0)continue;"
                        + "var r=fs[i].getBoundingClientRect();"
                        + "if(r.width>0&&r.height>0)return 'challenge';}"
                        // Uptodown embeds Turnstile in a normal page instead of
                        // replacing the page with a challenge screen. Detect its
                        // widget so the watchdog does not reload over it.
                        + "var uw=document.getElementById('download-turnstile-widget');"
                        + "if(uw){"
                        + "var ui=uw.querySelector('iframe');"
                        + "if(ui){var ur=ui.getBoundingClientRect();"
                        + "if(ur.width>0&&ur.height>0)return 'challenge';}"
                        + "if(uw.classList.contains('interactive'))return 'challenge';}"
                        + "var ct=document.querySelector('.cf-turnstile');"
                        + "if(ct){var cr=ct.getBoundingClientRect();"
                        + "if(cr.width>0&&cr.height>0)return 'challenge';}"
                        + "if(t.indexOf('verify you are human')>=0"
                        + "||t.indexOf('security verification')>=0"
                        + "||t.indexOf('checking your browser')>=0"
                        + "||t.indexOf('just a moment')>=0"
                        + "||t.indexOf('attention required')>=0"
                        + "||t.indexOf('enable javascript and cookies to continue')>=0)"
                        + "return 'challenge';"
                        + "return 'ready';})()",
                value -> onWebFlowProbe(value));
    }

    private void onWebFlowProbe(String value) {
        if (value != null && value.contains("notfound")) {
            if (!uptodownHandled) {
                webNotFoundProbes++;
                if (webNotFoundProbes >= 2) {
                    logWebFlowState("NOT_FOUND", "");
                    failWebFlowFast(getString(R.string.download_error_apkcube_not_found));
                }
            }
            return;
        }
        webNotFoundProbes = 0;
        boolean challenge = value != null && value.contains("challenge");
        if (!challenge && !webReadyLogged && webPageFinished && !uptodownHandled) {
            webReadyLogged = true;
            webLastProgressAt = System.currentTimeMillis();
            logWebFlowState("READY", "");
        }
        if (challenge == webChallenge) {
            return;
        }
        webChallenge = challenge;
        if (challenge) {
            logWebFlowState("CHALLENGE", "");
            if (!webHintShown) {
                showVerificationHintNow();
            }
            // A challenge must not race the watchdog, reload, or ad-block
            // retry. Those are re-armed when the page becomes ready again.
            cancelWebFlowTimeout();
            cancelAdBlockRetry();
            cancelChallengeTimeout();
            challengeTimeout = () -> {
                if (!webChallenge || uptodownHandled) {
                    return;
                }
                logWebFlowState("CHALLENGE_TIMEOUT", "");
                failWebFlowFast(getString(R.string.download_error_page_timeout_detail));
            };
            main.postDelayed(challengeTimeout, CHALLENGE_TIMEOUT_MS);
        } else {
            logWebFlowState("CHALLENGE_CLEARED", "");
            cancelChallengeTimeout();
            hideVerificationHint();
            if (uptodownWebView != null && !uptodownHandled) {
                scheduleWebFlowTimeout(uptodownWebView);
                scheduleAdBlockRetry(uptodownWebView);
            }
        }
    }

    private void showVerificationHintNow() {
        if (verificationHintRunnable != null) {
            main.removeCallbacks(verificationHintRunnable);
            verificationHintRunnable = null;
        }
        webHintShown = true;
        if (verificationHint != null) {
            verificationHint.setVisibility(View.VISIBLE);
        }
    }

    /**
     * If a finished page still has no captured download and the blocker
     * rejected requests, retry once with blocking off. A challenge always
     * takes precedence and is never interrupted.
     */
    private void scheduleAdBlockRetry(WebView web) {
        cancelAdBlockRetry();
        // Uptodown's page needs a real download-button tap for Turnstile.
        // Reloading it at 30s would discard the user's verification attempt.
        if (!blockWebAds || "uptodown".equals(webSource)) {
            return;
        }
        adBlockRetry = () -> {
            if (uptodownHandled || uptodownWebView != web || !blockWebAds
                    || !hasBlockedAds()) {
                return;
            }
            if (webChallenge || !webPageFinished) {
                main.postDelayed(adBlockRetry, 10_000);
                return;
            }
            blockWebAds = false;
            liveAdGuard = false;
            HelperLog.info(webSource
                    + " still busy; retrying once without ad blocking");
            web.loadUrl(webOriginalUrl);
            scheduleWebFlowTimeout(web);
        };
        main.postDelayed(adBlockRetry, 30_000);
    }

    private void cancelAdBlockRetry() {
        if (adBlockRetry != null) {
            main.removeCallbacks(adBlockRetry);
            adBlockRetry = null;
        }
    }

    /**
     * APKMirror's first download link can be the full split bundle even on an
     * APK variant page. A plain APK request must not hand Morphe an APKM.
     */
    private String forceBaseApkUrl(String url, String fileType) {
        if (!"apk".equalsIgnoreCase(fileType) || allowSplitArchive
                || url == null || url.toLowerCase(Locale.US).contains("forcebaseapk=")) {
            return url;
        }
        HelperLog.info("APKMirror forcing base APK download");
        return url + (url.contains("?") ? "&" : "?") + "forcebaseapk=true";
    }

    /**
     * APKCube is ad-supported and some download links open third-party
     * prelander pages. Keep the flow on APKCube and on direct file URLs.
     */
    private boolean isApkCubeNavigationAllowed(String url) {
        String host = hostOf(url);
        if (host == null) {
            return false;
        }
        String lowerHost = host.toLowerCase(Locale.US);
        if (lowerHost.equals("apkcube.com") || lowerHost.endsWith(".apkcube.com")
                || lowerHost.endsWith("cloudflarestorage.com")
                || lowerHost.endsWith("r2.dev")) {
            return true;
        }
        String path = pathOf(url).toLowerCase(Locale.US);
        return path.endsWith(".apk") || path.endsWith(".xapk")
                || path.endsWith(".apks") || path.endsWith(".apkm")
                || path.contains("/download/") || path.endsWith("/download")
                || path.contains("download.php");
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
        if ("apkmirror".equals(webSource)) {
            if (isHost(host, "apkmirror.com")
                    && url.toLowerCase(Locale.US).contains("download.php")) {
                handleUptodownDownload(forceBaseApkUrl(url, uptodownFileType));
                return true;
            }
            return false;
        }
        if ("apkcube".equals(webSource)) {
            String lower = url.toLowerCase(Locale.US);
            String path = pathOf(url).toLowerCase(Locale.US);
            if (path.endsWith(".apk") || path.endsWith(".xapk")
                    || path.endsWith(".apks") || path.endsWith(".apkm")
                    || lower.contains("/download/")
                    || lower.contains("download.php")) {
                HelperLog.info("APKCube captured " + url);
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

    private String webSourceKey() {
        if ("apkpure".equals(webSource)) {
            return SourceSettings.APKPURE;
        }
        if ("apkmirror".equals(webSource)) {
            return SourceSettings.APKMIRROR;
        }
        if ("apkcube".equals(webSource)) {
            return SourceSettings.APKCUBE;
        }
        return SourceSettings.UPTODOWN;
    }

    private String webSourceName() {
        if ("apkpure".equals(webSource)) {
            return "APKPure";
        }
        if ("apkmirror".equals(webSource)) {
            return "APKMirror";
        }
        if ("apkcube".equals(webSource)) {
            return "APKCube";
        }
        return "Uptodown";
    }

    private void recordBlockedAd(String host) {
        synchronized (adBlockLock) {
            blockedAdRequests++;
            if (host != null && blockedAdHosts.size() < 12) {
                blockedAdHosts.add(host);
            }
        }
    }

    private boolean hasBlockedAds() {
        synchronized (adBlockLock) {
            return blockedAdRequests > 0;
        }
    }

    private String adBlockerSummary() {
        synchronized (adBlockLock) {
            if (blockedAdRequests == 0) {
                return "";
            }
            return blockedAdRequests + " request(s) blocked"
                    + (blockedAdHosts.isEmpty() ? ""
                    : " (" + TextUtils.join(", ", blockedAdHosts) + ")");
        }
    }

    /** Downloads the URL captured from the source WebView and returns it to Morphe. */
    private void handleUptodownDownload(String url) {
        if (uptodownHandled) {
            return;
        }
        cancelWebFlowTimeout();
        cancelAdBlockRetry();
        logWebFlowState("CAPTURED", url);
        uptodownHandled = true;
        cancelWebFlowProbe();
        hideVerificationHint();
        HelperLog.info(webSource + " download URL: " + url);

        final String source = webSource;
        String type = uptodownFileType;
        if ("apkpure".equals(source)) {
            if (isSplitType(uptodownFileType)) {
                // XAPK and APKM are equivalent bundles; keep the requested
                // extension so Morphe receives the format it asked for.
                type = uptodownFileType;
            } else if (url.toLowerCase(Locale.US).contains("/xapk/")) {
                type = "xapk";
            }
        } else if ("apkmirror".equals(source) || "apkcube".equals(source)) {
            type = TextUtils.isEmpty(uptodownFileType) ? "apk" : uptodownFileType;
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
            setStatus(getString(R.string.download_downloading_from, webSourceName()));

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
                    markWebFlowResult(true, getString(R.string.source_detail_webview));
                    if (!cancelled) {
                        deliverFile(file);
                    }
                } catch (Exception e) {
                    if (switchingSource) {
                        switchingSource = false;
                        markWebFlowResult(false,
                                getString(R.string.download_switching_source));
                        main.post(() -> startResolution(false));
                        return;
                    }
                    if ((e instanceof DownloadedFileValidator.IncompatibleSdkException
                            || e instanceof DownloadedFileValidator.VersionMismatchException)
                            && ("uptodown".equals(source) || "apkmirror".equals(source))) {
                        rejectWebBuild(source, e.getMessage());
                        return;
                    }
                    if ("apkmirror".equals(source) && isRetryableWebDownload(e)
                            && webRetries < 1 && !cancelled) {
                        webRetries++;
                        uptodownHandled = false;
                        HelperLog.info("Retrying APKMirror with a fresh download key");
                        main.post(() -> startWebFlow(source, webOriginalUrl,
                                uptodownPkg, uptodownVersion, uptodownFileType));
                        return;
                    }
                    if ("uptodown".equals(source) && e instanceof java.io.IOException
                            && isRetryableDownloadError((java.io.IOException) e)
                            && webRetries < 1 && !cancelled) {
                        // The native Uptodown CDN URL can expire or stall near
                        // the end. Reopen the verification page once to get a
                        // fresh URL instead of giving up on the source.
                        webRetries++;
                        uptodownHandled = false;
                        HelperLog.info("Retrying Uptodown with a fresh download URL");
                        main.post(() -> startWebFlow(source, webOriginalUrl,
                                uptodownPkg, uptodownVersion, uptodownFileType));
                        return;
                    }
                    HelperLog.error(source + " download failed", e);
                    continueAfterWebFailure(e.getMessage());
                }
            });
        });
    }

    private void applyPalette() {
        ThemePalette palette = ThemeManager.palette(this);
        BG_TOP = palette.bgTop;
        BG_BOTTOM = palette.bgBottom;
        CARD_TOP = palette.cardTop;
        CARD_BOTTOM = palette.cardBottom;
        BORDER = palette.border;
        PRIMARY_START = palette.primaryStart;
        PRIMARY_END = palette.primaryEnd;
        ON_PRIMARY = palette.onPrimary;
        ACCENT_TEXT = palette.accentText;
        RIPPLE = palette.ripple;
        SECONDARY = palette.secondary;
        SECONDARY_BORDER = palette.secondaryBorder;
        TEXT = palette.text;
        MUTED = palette.muted;
        STEPS = palette.steps;
        WARN_TITLE = palette.warnTitle;
        WARN_TEXT = palette.warnText;
        WARN_BG = palette.warnBg;
        WARN_BORDER = palette.warnBorder;
        GREEN = palette.green;
        RED = palette.red;
        themeSignature = ThemeManager.signature(this);
    }

    /**
     * Builds the download screen entirely in code.
     *
     * <p>The layout is intentionally simple: logo, title, source summary, status/progress
     * card, and two action buttons. Keeping it code-only avoids XML theme surprises on
     * different Android versions.</p>
     */
    private void buildUi() {
        // ScrollView keeps the layout usable on small screens and with large fonts.
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(gradient(BG_TOP, BG_BOTTOM));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        final int horizontalPadding = dp(24);
        final int baseTop = dp(16);
        final int bottomPadding = dp(24);
        root.setPadding(horizontalPadding, dp(48), horizontalPadding, bottomPadding);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navigation = insets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(horizontalPadding, baseTop + top, horizontalPadding,
                    bottomPadding + navigation);
            return insets;
        });

        // Compact top bar instead of the old logo block.
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView back = text("\u2190", 24, TEXT, true);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription(getString(R.string.common_cancel));
        back.setBackground(new RippleDrawable(
                ColorStateList.valueOf(RIPPLE), rounded(SECONDARY, 999), null));
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(v -> cancel());
        topBar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout topLabels = new LinearLayout(this);
        topLabels.setOrientation(LinearLayout.VERTICAL);
        topLabels.setPadding(dp(12), 0, 0, 0);
        screenTitle = text(getString(R.string.download_title_progress), 20, TEXT, true);
        screenSubtitle = text(getString(R.string.download_subtitle), 12, MUTED, false);
        screenSubtitle.setPadding(0, dp(3), 0, 0);
        topLabels.addView(screenTitle);
        topLabels.addView(screenSubtitle);
        topBar.addView(topLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Progress card: ring, status, source, and scan controls.
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(cardBackground());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(12);
        root.addView(card, cardParams);

        stateIcon = text("\u2715", 56, RED, true);
        stateIcon.setGravity(Gravity.CENTER);
        stateIcon.setVisibility(View.GONE);

        appHeader = new LinearLayout(this);
        appHeader.setOrientation(LinearLayout.HORIZONTAL);
        appHeader.setGravity(Gravity.CENTER_VERTICAL);
        appHeader.setPadding(0, dp(2), 0, dp(8));
        appHeader.setVisibility(View.GONE);

        FrameLayout appIconFrame = new FrameLayout(this);
        appIcon = new ImageView(this);
        appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        appIconFrame.addView(appIcon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        appIconFallback = text("", 22, ON_PRIMARY, true);
        appIconFallback.setGravity(Gravity.CENTER);
        appIconFallback.setBackground(rounded(PRIMARY_START, 14));
        appIconFallback.setVisibility(View.GONE);
        appIconFrame.addView(appIconFallback, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        appHeader.addView(appIconFrame, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout appLabels = new LinearLayout(this);
        appLabels.setOrientation(LinearLayout.VERTICAL);
        appLabels.setPadding(dp(12), 0, 0, 0);

        downloadName = text("", 18, TEXT, true);
        downloadName.setSingleLine(true);
        downloadName.setEllipsize(TextUtils.TruncateAt.END);
        appLabels.addView(downloadName);

        appChips = new LinearLayout(this);
        appChips.setOrientation(LinearLayout.HORIZONTAL);
        appChips.setPadding(0, dp(5), 0, 0);
        appVersionChip = chip("", TEXT);
        appFormatChip = chip("", TEXT);
        appAbiChip = chip("", TEXT);
        appChips.addView(appVersionChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams formatChipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        formatChipParams.setMarginStart(dp(6));
        appChips.addView(appFormatChip, formatChipParams);
        LinearLayout.LayoutParams abiChipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        abiChipParams.setMarginStart(dp(6));
        appChips.addView(appAbiChip, abiChipParams);
        appLabels.addView(appChips);

        appHeader.addView(appLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(appHeader);

        progress = new ProgressDisplay(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                dp(200), dp(200));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(4);
        card.addView(progress, progressParams);
        card.addView(stateIcon);

        status = text("", 14, STEPS, false);
        status.setGravity(Gravity.CENTER);
        status.setLineSpacing(dp(3), 1f);
        status.setPadding(0, dp(12), 0, 0);
        card.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        progressInfo = new LinearLayout(this);
        progressInfo.setOrientation(LinearLayout.HORIZONTAL);
        progressInfo.setGravity(Gravity.CENTER);
        progressInfo.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable progressBackground = rounded(SECONDARY, 999);
        progressBackground.setStroke(dp(1), SECONDARY_BORDER);
        progressInfo.setBackground(progressBackground);
        progressInfo.setVisibility(View.GONE);

        progressSpeed = text("", 12, TEXT, true);
        progressSpeed.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_speed, 0, 0, 0);
        progressSpeed.setCompoundDrawablePadding(dp(5));
        progressSpeed.setCompoundDrawableTintList(
                ColorStateList.valueOf(MUTED));
        progressInfo.addView(progressSpeed);

        progressEtaDivider = new View(this);
        progressEtaDivider.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                dp(1), dp(14));
        dividerParams.setMargins(dp(10), 0, dp(10), 0);
        progressInfo.addView(progressEtaDivider, dividerParams);

        progressEta = text("", 12, MUTED, true);
        progressEta.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_timer, 0, 0, 0);
        progressEta.setCompoundDrawablePadding(dp(5));
        progressEta.setCompoundDrawableTintList(
                ColorStateList.valueOf(MUTED));
        progressInfo.addView(progressEta);

        LinearLayout.LayoutParams progressInfoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressInfoParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressInfoParams.topMargin = dp(12);
        card.addView(progressInfo, progressInfoParams);

        sourceLabel = text("", 12, MUTED, true);
        sourceLabel.setGravity(Gravity.CENTER);
        sourceLabel.setVisibility(View.GONE);
        LinearLayout.LayoutParams sourceLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sourceLabelParams.topMargin = dp(8);
        card.addView(sourceLabel, sourceLabelParams);

        metricsRow = new LinearLayout(this);
        metricsRow.setOrientation(LinearLayout.HORIZONTAL);
        metricsRow.setPadding(0, dp(14), 0, 0);
        metricsRow.setVisibility(View.GONE);
        metricSource = metricValue();
        metricSize = metricValue();
        metricConnections = metricValue();
        metricSourceBadge = new FrameLayout(this);
        metricSourceBadge.setBackground(rounded(SECONDARY, 999));
        int sourceBadgePad = dp(2);
        metricSourceBadge.setPadding(
                sourceBadgePad, sourceBadgePad, sourceBadgePad, sourceBadgePad);
        metricSourceBadge.setVisibility(View.GONE);
        metricsRow.addView(metricColumn(R.drawable.ic_metric_source,
                        getString(R.string.download_metric_source),
                        metricSource, metricSourceBadge),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        metricsRow.addView(metricColumn(R.drawable.ic_metric_size,
                        getString(R.string.download_metric_size), metricSize, null),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        metricsRow.addView(metricColumn(R.drawable.ic_metric_connections,
                        getString(R.string.download_metric_connections),
                        metricConnections, null),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(metricsRow);

        packagePill = text("", 11, TEXT, false);
        packagePill.setGravity(Gravity.CENTER);
        packagePill.setSingleLine(true);
        packagePill.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        packagePill.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable packageBackground = rounded(SECONDARY, 999);
        packageBackground.setStroke(dp(1), SECONDARY_BORDER);
        packagePill.setBackground(packageBackground);
        packagePill.setVisibility(View.GONE);
        LinearLayout.LayoutParams packageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        packageParams.gravity = Gravity.CENTER_HORIZONTAL;
        packageParams.topMargin = dp(10);
        card.addView(packagePill, packageParams);

        LinearLayout verificationCard = new LinearLayout(this);
        verificationCard.setOrientation(LinearLayout.HORIZONTAL);
        verificationCard.setGravity(Gravity.CENTER_VERTICAL);
        verificationCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable hintBackground = rounded(WARN_BG, 14);
        hintBackground.setStroke(dp(1), WARN_BORDER);
        verificationCard.setBackground(new RippleDrawable(
                ColorStateList.valueOf(RIPPLE), hintBackground, null));
        verificationCard.setClickable(true);
        verificationCard.setFocusable(true);
        verificationCard.setVisibility(View.GONE);
        verificationCard.setOnClickListener(v -> scrollToVerification());
        verificationCard.setContentDescription(
                getString(R.string.download_verification_attention));

        ImageView hintIcon = new ImageView(this);
        hintIcon.setImageResource(R.drawable.ic_warning);
        hintIcon.setColorFilter(WARN_TITLE);
        LinearLayout.LayoutParams hintIconParams = new LinearLayout.LayoutParams(
                dp(24), dp(24));
        hintIconParams.setMarginEnd(dp(10));
        verificationCard.addView(hintIcon, hintIconParams);

        LinearLayout hintLabels = new LinearLayout(this);
        hintLabels.setOrientation(LinearLayout.VERTICAL);
        TextView hintTitle = text(getString(R.string.download_verification_title),
                13, WARN_TITLE, true);
        hintLabels.addView(hintTitle);
        TextView hintBody = text(getString(R.string.download_verification_body),
                11, WARN_TEXT, false);
        hintBody.setLineSpacing(dp(2), 1f);
        hintBody.setPadding(0, dp(2), 0, 0);
        hintLabels.addView(hintBody);
        verificationCard.addView(hintLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView verifyNow = text(
                getString(R.string.download_verify_now) + "  \u2192",
                11, WARN_TITLE, true);
        verifyNow.setGravity(Gravity.CENTER);
        verifyNow.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable verifyBackground = rounded(SECONDARY, 999);
        verifyBackground.setStroke(dp(1), WARN_BORDER);
        verifyNow.setBackground(verifyBackground);
        LinearLayout.LayoutParams verifyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        verifyParams.setMarginStart(dp(8));
        verificationCard.addView(verifyNow, verifyParams);

        verificationHint = verificationCard;
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(10);
        card.addView(verificationHint, hintParams);

        scanNote = text(getString(R.string.virustotal_scan_note), 12, MUTED, false);
        scanNote.setGravity(Gravity.CENTER);
        scanNote.setLineSpacing(dp(3), 1f);
        scanNote.setPadding(dp(6), dp(10), dp(6), 0);
        scanNote.setVisibility(View.GONE);
        card.addView(scanNote);

        skipScanButton = text(getString(R.string.virustotal_skip_scan), 14, PRIMARY_START, true);
        skipScanButton.setGravity(Gravity.CENTER);
        skipScanButton.setPadding(0, dp(12), 0, 0);
        skipScanButton.setVisibility(View.GONE);
        skipScanButton.setOnClickListener(v -> skipVirusTotalScan());
        card.addView(skipScanButton);

        resultActions = new LinearLayout(this);
        resultActions.setOrientation(LinearLayout.VERTICAL);
        resultActions.setVisibility(View.GONE);
        resultActions.setPadding(0, dp(10), 0, 0);
        card.addView(resultActions);

        resultContinue = secondaryButton(getString(R.string.virustotal_continue_anyway));
        resultContinue.setOnClickListener(v -> completeResultDecision(DECISION_CONTINUE));
        resultActions.addView(resultContinue, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        resultDelete = secondaryButton(getString(R.string.virustotal_delete_cancel));
        resultDelete.setTextColor(RED);
        resultDelete.setOnClickListener(v -> completeResultDecision(DECISION_DELETE));
        LinearLayout.LayoutParams resultDeleteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        resultDeleteParams.topMargin = dp(8);
        resultActions.addView(resultDelete, resultDeleteParams);

        resultReport = secondaryButton(getString(R.string.virustotal_view_report));
        resultReport.setOnClickListener(v -> {
            // Replaced when a decision is shown.
        });
        LinearLayout.LayoutParams resultReportParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        resultReportParams.topMargin = dp(8);
        resultActions.addView(resultReport, resultReportParams);

        // Keep the active-flow controls directly under the ring.
        LinearLayout progressControls = new LinearLayout(this);
        progressControls.setOrientation(LinearLayout.VERTICAL);
        progressControls.setPadding(0, dp(12), 0, 0);
        card.addView(progressControls);

        downloadButtonsRow = new LinearLayout(this);
        downloadButtonsRow.setOrientation(LinearLayout.HORIZONTAL);
        downloadButtonsRow.setGravity(Gravity.CENTER_VERTICAL);
        downloadButtonsRow.setVisibility(View.GONE);
        progressControls.addView(downloadButtonsRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pauseButton = actionButton(pauseLabel(false), ACCENT_TEXT, ACCENT_TEXT);
        pauseButton.setVisibility(View.GONE);
        pauseButton.setOnClickListener(v -> togglePause());
        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(
                0, dp(48), 1f);
        pauseParams.setMarginEnd(dp(6));
        downloadButtonsRow.addView(pauseButton, pauseParams);

        switchSourceButton = actionButton(
                "\u21C4  " + getString(R.string.download_switch_source_short),
                WARN_TEXT, WARN_TEXT);
        switchSourceButton.setVisibility(View.GONE);
        switchSourceButton.setOnClickListener(v -> showSwitchSourcePicker());
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
                0, dp(48), 1f);
        switchParams.setMarginStart(dp(6));
        downloadButtonsRow.addView(switchSourceButton, switchParams);

        // Cancel/Close stays available while the flow is active.
        cancelButton = actionButton(
                "\u2715  " + getString(R.string.common_cancel), RED, RED);
        cancelButton.setOnClickListener(v -> cancel());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        cancelParams.topMargin = dp(8);
        progressControls.addView(cancelButton, cancelParams);

        // Compact resolver steps while the helper is working.
        sourceStepsCard = new LinearLayout(this);
        sourceStepsCard.setOrientation(LinearLayout.VERTICAL);
        sourceStepsCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        sourceStepsCard.setBackground(cardBackground());
        LinearLayout.LayoutParams stepsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        stepsParams.topMargin = dp(14);
        root.addView(sourceStepsCard, stepsParams);

        LinearLayout sourceHeader = new LinearLayout(this);
        sourceHeader.setOrientation(LinearLayout.HORIZONTAL);
        sourceHeader.setGravity(Gravity.CENTER_VERTICAL);
        sourceHeader.setClickable(true);
        sourceHeader.setFocusable(true);
        sourceHeader.setContentDescription(getString(R.string.main_sources_title));
        sourceHeader.addView(text(getString(R.string.main_sources_title), 15, TEXT, true),
                new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        sourceStepsSummary = text("", 10, MUTED, false);
        sourceStepsSummary.setSingleLine(true);
        sourceStepsSummary.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        sourceStepsSummary.setVisibility(View.GONE);
        sourceHeader.addView(sourceStepsSummary);

        sourceStepsChevron = new ImageView(this);
        sourceStepsChevron.setImageResource(R.drawable.ic_chevron_right);
        sourceStepsChevron.setColorFilter(MUTED);
        sourceStepsChevron.setRotation(270f);
        sourceStepsChevron.setPadding(dp(7), dp(7), dp(7), dp(7));
        sourceStepsChevron.setBackground(rounded(SECONDARY, 999));
        sourceHeader.addView(sourceStepsChevron, new LinearLayout.LayoutParams(
                dp(30), dp(30)));

        sourceHeader.setOnClickListener(v -> {
            sourceStepsManualOverride = true;
            setSourceCardExpanded(!sourceStepsExpanded);
        });
        sourceStepsCard.addView(sourceHeader);

        sourceStepsList = new LinearLayout(this);
        sourceStepsList.setOrientation(LinearLayout.VERTICAL);
        sourceStepsList.setPadding(0, dp(4), 0, 0);
        sourceStepsCard.addView(sourceStepsList);
        setSourceCardExpanded(true);
        renderSourceSteps();

        diagnosticsCard = new LinearLayout(this);
        diagnosticsCard.setOrientation(LinearLayout.VERTICAL);
        diagnosticsCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        diagnosticsCard.setBackground(cardBackground());
        diagnosticsCard.setVisibility(View.GONE);
        LinearLayout.LayoutParams diagnosticsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        diagnosticsParams.topMargin = dp(14);
        root.addView(diagnosticsCard, diagnosticsParams);

        diagnosticsCard.addView(text(getString(R.string.download_what_happened), 16, TEXT, true));
        diagnosticsSummary = text("", 12, MUTED, false);
        diagnosticsSummary.setLineSpacing(dp(3), 1f);
        diagnosticsSummary.setPadding(0, dp(6), 0, dp(4));
        diagnosticsCard.addView(diagnosticsSummary);

        diagnosticsRows = new LinearLayout(this);
        diagnosticsRows.setOrientation(LinearLayout.VERTICAL);
        diagnosticsCard.addView(diagnosticsRows);

        retryAllButton = secondaryButton(getString(R.string.download_retry_all));
        retryAllButton.setVisibility(View.GONE);
        retryAllButton.setOnClickListener(v -> retryAllSources());
        LinearLayout.LayoutParams retryAllParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        retryAllParams.topMargin = dp(10);
        diagnosticsCard.addView(retryAllButton, retryAllParams);

        retryButton = secondaryButton(getString(R.string.download_retry_failed));
        retryButton.setVisibility(View.GONE);
        retryButton.setOnClickListener(v -> retryFailedSources());
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        retryParams.topMargin = dp(8);
        diagnosticsCard.addView(retryButton, retryParams);

        copyButton = secondaryButton(getString(R.string.download_copy_details));
        copyButton.setVisibility(View.GONE);
        copyButton.setOnClickListener(v -> copyDiagnostics());
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        copyParams.topMargin = dp(8);
        diagnosticsCard.addView(copyButton, copyParams);

        editSourcesButton = secondaryButton(getString(R.string.download_edit_sources));
        editSourcesButton.setVisibility(View.GONE);
        editSourcesButton.setOnClickListener(v ->
                SourceManagerDialog.show(this, null));
        LinearLayout.LayoutParams editSourcesParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        editSourcesParams.topMargin = dp(8);
        diagnosticsCard.addView(editSourcesButton, editSourcesParams);

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

        uptodownTitle = text(getString(R.string.download_uptodown_title), 16, TEXT, true);
        uptodownSection.addView(uptodownTitle);

        uptodownExplanation = text(getString(R.string.download_uptodown_explanation),
                13, STEPS, false);
        uptodownExplanation.setLineSpacing(dp(3), 1f);
        uptodownExplanation.setPadding(0, dp(8), 0, dp(12));
        uptodownSection.addView(uptodownExplanation);

        uptodownWebContainer = new FrameLayout(this);
        uptodownSection.addView(uptodownWebContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(520)));

        // Fallback button is hidden until resolution fails.
        fallbackButton = primaryButton(getString(R.string.download_open_page));
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

        // Attach the content column to the ScrollView and show it.
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollView = scroll;
        setContentView(scroll);
        ThemeManager.applySystemBars(this);
    }

    /** Circular percentage display used while resolving and downloading. */
    private final class ProgressDisplay extends FrameLayout {
        private final ProgressRing ring;
        private final TextView percent;
        private final TextView caption;
        private boolean indeterminate = true;
        private int value = 0;

        ProgressDisplay(Context context) {
            super(context);
            ring = new ProgressRing(context);
            addView(ring, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setGravity(Gravity.CENTER);
            percent = new TextView(context);
            percent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
            percent.setTextColor(TEXT);
            percent.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            percent.setGravity(Gravity.CENTER);
            labels.addView(percent);

            caption = new TextView(context);
            caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            caption.setTextColor(MUTED);
            caption.setLetterSpacing(0.16f);
            caption.setGravity(Gravity.CENTER);
            caption.setPadding(0, dp(2), 0, 0);
            labels.addView(caption);

            FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            labelParams.gravity = Gravity.CENTER;
            addView(labels, labelParams);

            setIndeterminate(true);
        }

        void setIndeterminate(boolean indeterminate) {
            this.indeterminate = indeterminate;
            ring.setIndeterminate(indeterminate);
            caption.setText(indeterminate
                    ? R.string.download_progress_preparing
                    : R.string.download_progress_downloading);
            percent.setText(indeterminate ? "\u2026" : value + "%");
        }

        void setProgress(int progress) {
            value = Math.max(0, Math.min(100, progress));
            ring.setProgress(value);
            if (!indeterminate) {
                percent.setText(value + "%");
                caption.setText(R.string.download_progress_downloading);
            }
        }

        void setIndeterminateCaption(int captionRes) {
            indeterminate = true;
            ring.setIndeterminate(true);
            caption.setText(captionRes);
            percent.setText("\u2026");
        }

        void setProgressWithCaption(int captionRes, int progress) {
            indeterminate = false;
            value = Math.max(0, Math.min(100, progress));
            ring.setIndeterminate(false);
            ring.setProgress(value);
            caption.setText(captionRes);
            percent.setText(value + "%");
        }

        int getProgress() {
            return value;
        }

        void setMax(int max) {
            // The display always uses a fixed 0-100 range.
        }

        void setIndeterminateTintList(ColorStateList tint) {
            ring.setProgressColor(tint.getDefaultColor());
        }

        void setProgressTintList(ColorStateList tint) {
            ring.setProgressColor(tint.getDefaultColor());
        }

        void setProgressBackgroundTintList(ColorStateList tint) {
            ring.setTrackColor(tint.getDefaultColor());
        }
    }

    /** Draws the progress arc behind the percentage text. */
    private final class ProgressRing extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc = new RectF();
        private int progressColor = PRIMARY_START;
        private int progressEndColor = PRIMARY_END;
        private int trackColor = BORDER;
        private int progress = 0;
        private boolean indeterminate = true;
        private float angle = -90f;

        ProgressRing(Context context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(12));
        }

        void setProgress(int value) {
            progress = value;
            invalidate();
        }

        void setIndeterminate(boolean value) {
            indeterminate = value;
            invalidate();
        }

        void setProgressColor(int color) {
            progressColor = color;
            invalidate();
        }

        void setTrackColor(int color) {
            trackColor = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float stroke = paint.getStrokeWidth();
            if (getWidth() <= stroke || getHeight() <= stroke) {
                return;
            }
            arc.set(stroke / 2f, stroke / 2f,
                    getWidth() - stroke / 2f, getHeight() - stroke / 2f);

            paint.setColor(trackColor);
            paint.setShader(null);
            canvas.drawArc(arc, 0f, 360f, false, paint);

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            paint.setShader(new SweepGradient(cx, cy,
                    new int[]{progressColor, progressEndColor, progressColor}, null));
            if (indeterminate) {
                canvas.drawArc(arc, angle, 115f, false, paint);
                canvas.drawArc(arc, angle + 180f, 55f, false, paint);
                angle += 4f;
                if (angle > 270f) {
                    angle -= 360f;
                }
                postInvalidateOnAnimation();
            } else {
                canvas.drawArc(arc, -90f, 360f * progress / 100f, false, paint);
            }
            paint.setShader(null);
        }
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
        view.setTextColor(ON_PRIMARY);
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
    private TextView chip(String value, int textColor) {
        TextView chip = text(value, 10, textColor, true);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable background = rounded(SECONDARY, 999);
        background.setStroke(dp(1), SECONDARY_BORDER);
        chip.setBackground(background);
        return chip;
    }

    private TextView metricValue() {
        return text("\u2014", 12, TEXT, true);
    }

    private LinearLayout metricColumn(int iconRes, String label, TextView value,
                                      FrameLayout valueIcon) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(MUTED);
        column.addView(icon, new LinearLayout.LayoutParams(dp(14), dp(14)));
        TextView caption = text(label.toUpperCase(Locale.US), 9, MUTED, true);
        caption.setLetterSpacing(0.12f);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, dp(3), 0, 0);
        column.addView(caption);

        LinearLayout valueRow = new LinearLayout(this);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.setGravity(Gravity.CENTER);
        valueRow.setPadding(0, dp(3), 0, 0);
        if (valueIcon != null) {
            LinearLayout.LayoutParams valueIconParams = new LinearLayout.LayoutParams(
                    dp(18), dp(18));
            valueIconParams.setMarginEnd(dp(4));
            valueRow.addView(valueIcon, valueIconParams);
        }
        value.setGravity(Gravity.CENTER);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        valueRow.addView(value);
        column.addView(valueRow);
        return column;
    }

    private TextView actionButton(String label, int textColor, int borderColor) {
        TextView view = text(label, 13, textColor, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable background = rounded(SECONDARY, 14);
        background.setStroke(dp(1), borderColor);
        view.setBackground(new RippleDrawable(
                ColorStateList.valueOf(RIPPLE), background, null));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private String pauseLabel(boolean isPaused) {
        return (isPaused ? "\u25B6  " : "\u275A\u275A  ")
                + getString(isPaused ? R.string.download_resume : R.string.download_pause);
    }

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
                ColorStateList.valueOf(RIPPLE),
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

    /** Caches the real icon from a downloaded plain APK for this package/version. */
    private void appIconCacheSave(File file) {
        if (file == null || TextUtils.isEmpty(requestPkg)) {
            return;
        }
        String key = AppIconCache.key(requestPkg, requestVersion);
        AppIconCache.saveFromApk(getApplicationContext(), key, file);
        main.post(this::updateDownloadDetails);
    }

    /** Updates the top bar with the app and version from Morphe's request. */
    private void updateRequestHeader() {
        main.post(() -> {
            if (screenTitle != null) {
                screenTitle.setText(R.string.download_title_progress);
            }
            if (screenSubtitle != null) {
                screenSubtitle.setText("");
                screenSubtitle.setVisibility(View.GONE);
            }
            updateDownloadDetails();
        });
    }

    private void updateDownloadDetails() {
        if (appHeader == null) {
            return;
        }
        String app = TextUtils.isEmpty(requestedAppName)
                ? requestPkg : requestedAppName;
        appHeader.setVisibility(TextUtils.isEmpty(app) ? View.GONE : View.VISIBLE);
        downloadName.setText(app);

        if (appIcon != null) {
            if (TextUtils.isEmpty(requestPkg)) {
                appIcon.setVisibility(View.GONE);
                if (appIconFallback != null) {
                    appIconFallback.setVisibility(View.GONE);
                }
            } else {
                boolean iconShown = false;
                try {
                    appIcon.setImageDrawable(
                            getPackageManager().getApplicationIcon(requestPkg));
                    iconShown = true;
                } catch (Exception ignored) {
                    // The target app is not installed. Try the cached icon
                    // from the source page or a previous download.
                }
                if (!iconShown) {
                    Drawable cached = AppIconCache.load(this,
                            AppIconCache.key(requestPkg, requestVersion));
                    if (cached != null) {
                        appIcon.setImageDrawable(cached);
                        iconShown = true;
                    }
                }
                if (iconShown) {
                    appIcon.setVisibility(View.VISIBLE);
                    if (appIconFallback != null) {
                        appIconFallback.setVisibility(View.GONE);
                    }
                } else {
                    Integer known = knownAppIcon(requestPkg);
                    if (known != null) {
                        appIcon.setImageResource(known);
                        appIcon.setVisibility(View.VISIBLE);
                        if (appIconFallback != null) {
                            appIconFallback.setVisibility(View.GONE);
                        }
                    } else {
                        appIcon.setVisibility(View.GONE);
                        if (appIconFallback != null) {
                            appIconFallback.setText(appInitial(app));
                            appIconFallback.setVisibility(View.VISIBLE);
                        }
                    }
                }
            }
        }

        String version = requestVersion == null ? "" : requestVersion.trim();
        if (!version.isEmpty() && !version.toLowerCase(Locale.US).startsWith("v")) {
            version = "v" + version;
        }
        setChip(appVersionChip, displayVersion(version));
        setChip(appFormatChip, TextUtils.isEmpty(requestFileType)
                ? "" : requestFileType.toUpperCase(Locale.US));
        setChip(appAbiChip, requestAbis != null && requestAbis.length > 0
                ? requestAbis[0] : "");

        boolean hasPackage = !TextUtils.isEmpty(requestPkg);
        if (metricsRow != null) {
            metricsRow.setVisibility(hasPackage ? View.VISIBLE : View.GONE);
        }
        if (metricSource != null) {
            metricSource.setText(activeSourceName.isEmpty()
                    ? "\u2014" : shortSourceName(activeSourceName));
        }
        if (metricSize != null) {
            metricSize.setText(downloadTotalBytes > 0
                    ? formatBytes(downloadTotalBytes) : "\u2014");
        }
        if (metricConnections != null) {
            metricConnections.setText(downloadConnections > 0
                    ? String.valueOf(downloadConnections) : "\u2014");
        }
        if (packagePill != null) {
            packagePill.setText(requestPkg);
            packagePill.setVisibility(hasPackage ? View.VISIBLE : View.GONE);
        }
        updateSourceMetricBadge();
    }

    /** Shows the active source logo beside the Source metric value. */
    private void updateSourceMetricBadge() {
        if (metricSourceBadge == null) {
            return;
        }
        String key = sourceKeyForLabel(activeSourceName);
        metricSourceBadge.removeAllViews();
        if (key == null) {
            metricSourceBadge.setVisibility(View.GONE);
            return;
        }
        View badge = SourceBadge.create(this, key);
        metricSourceBadge.addView(badge, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        metricSourceBadge.setContentDescription(
                getString(R.string.download_metric_source) + ": "
                        + shortSourceName(activeSourceName));
        metricSourceBadge.setVisibility(View.VISIBLE);
    }

    private static String appInitial(String app) {
        if (TextUtils.isEmpty(app)) {
            return "?";
        }
        String trimmed = app.trim();
        return trimmed.isEmpty()
                ? "?" : trimmed.substring(0, trimmed.offsetByCodePoints(0, 1));
    }

    /** Brand icons for common apps that may not be installed on the device. */
    private static Integer knownAppIcon(String pkg) {
        if ("com.google.android.youtube".equals(pkg)) {
            return R.drawable.ic_app_youtube;
        }
        if ("com.google.android.apps.youtube.music".equals(pkg)) {
            return R.drawable.ic_app_youtube_music;
        }
        return null;
    }

    /** Drops build qualifiers such as -release-arm64-v8a from the version chip. */
    private static String displayVersion(String version) {
        if (TextUtils.isEmpty(version)) {
            return version;
        }
        String lower = version.toLowerCase(Locale.US);
        String[] suffixes = {
                "-release-arm64-v8a", "-release-armeabi-v7a",
                "-release-x86_64", "-release-x86",
                "-release", "-arm64-v8a", "-armeabi-v7a",
                "-x86_64", "-x86"
        };
        for (String suffix : suffixes) {
            if (lower.endsWith(suffix)) {
                return version.substring(0, version.length() - suffix.length());
            }
        }
        return version;
    }

    private static String sourceKeyForLabel(String label) {
        if (TextUtils.isEmpty(label)) {
            return null;
        }
        String value = label.toLowerCase(Locale.US);
        if (value.contains("github")) {
            return value.contains("user")
                    ? GitHubAppStore.SOURCE_KEY : SourceSettings.GITHUB;
        }
        if (value.contains("uptodown")) return SourceSettings.UPTODOWN;
        if (value.contains("apkmirror")) return SourceSettings.APKMIRROR;
        if (value.contains("apkpure")) return SourceSettings.APKPURE;
        if (value.contains("f-droid") || value.contains("fdroid")) {
            return SourceSettings.FDROID;
        }
        if (value.contains("aptoide")) return SourceSettings.APTOIDE;
        if (value.contains("appteka")) return SourceSettings.APPTEKA;
        if (value.contains("morphe")) return SourceSettings.FALLBACK;
        return null;
    }

    /** Base label for a source key, used as soon as an attempt starts. */
    private String sourceLabelForKey(String key) {
        if (TextUtils.isEmpty(key)) {
            return "";
        }
        if (GitHubAppStore.SOURCE_KEY.equals(key)) {
            GitHubAppStore.Entry entry = requestPkg == null
                    ? null : GitHubAppStore.get(this, requestPkg);
            return entry == null
                    ? getString(R.string.source_github_user)
                    : getString(R.string.source_label_user_github, entry.repo);
        }
        if (SourceSettings.APPTEKA.equals(key)) {
            return getString(R.string.source_label_appteka);
        }
        return AppSettings.sourceLabel(this, key);
    }

    private void setChip(TextView chip, String value) {
        if (chip == null) {
            return;
        }
        chip.setText(value == null ? "" : value);
        chip.setVisibility(TextUtils.isEmpty(value) ? View.GONE : View.VISIBLE);
    }

    /** Shortens source labels for the metric column. */
    private static String shortSourceName(String label) {
        if (label == null) {
            return "";
        }
        int details = label.indexOf(" (");
        String base = details > 0 ? label.substring(0, details) : label;
        String lower = base.toLowerCase(Locale.US);
        if (lower.startsWith("apkpure")) return "APKPure";
        if (lower.startsWith("apkmirror")) return "APKMirror";
        if (lower.startsWith("uptodown")) return "Uptodown";
        if (lower.startsWith("morphe")) {
            return lower.contains("link") ? "Morphe link" : "Morphe";
        }
        if (lower.startsWith("github releases")) return "GitHub";
        return base;
    }

    private void setDownloadInfo(long totalBytes, int connections) {
        downloadTotalBytes = totalBytes;
        downloadConnections = Math.max(1, connections);
        main.post(this::updateDownloadDetails);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** Updates the status line from any thread. */
    private void setStatus(String text) {
        main.post(() -> {
            if (progressInfo != null) {
                progressInfo.setVisibility(View.GONE);
            }
            if (status != null) {
                status.setVisibility(View.VISIBLE);
                status.setText(text);
            }
        });
    }

    private void setStatus(int textRes) {
        setStatus(getString(textRes));
    }

    /** Shows the compact speed/ETA pill during an active download. */
    private void setProgressStatus(String speedText, long secondsLeft) {
        main.post(() -> {
            if (status != null) {
                status.setVisibility(View.GONE);
            }
            if (progressInfo != null) {
                progressInfo.setVisibility(View.VISIBLE);
            }
            if (progressSpeed != null) {
                progressSpeed.setText(speedText);
            }
            boolean hasEta = secondsLeft > 0;
            if (progressEtaDivider != null) {
                progressEtaDivider.setVisibility(hasEta ? View.VISIBLE : View.GONE);
            }
            if (progressEta != null) {
                progressEta.setVisibility(hasEta ? View.VISIBLE : View.GONE);
                if (hasEta) {
                    progressEta.setText(getString(
                            R.string.download_progress_time_left, secondsLeft));
                }
            }
        });
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
            activeSourceName = label == null ? "" : label;
            sourceLabel.setText(getString(R.string.download_source_label, label));
            sourceLabel.setVisibility(View.GONE);
            updateDownloadDetails();
            renderSourceSteps();
        });
    }

    private void setSourceLabel(int labelRes) {
        setSourceLabel(getString(labelRes));
    }

    private void setPauseVisible(boolean visible) {
        main.post(() -> {
            pauseButton.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (!visible) {
                paused = false;
                pauseButton.setText(pauseLabel(false));
            } else if (!sourceStepsManualOverride) {
                // The download has started; keep the source details compact
                // until the user opens them or a source needs attention.
                setSourceCardExpanded(false);
            }
            updateDownloadButtonsRow();
        });
    }

    private void setSwitchSourceVisible(boolean visible) {
        main.post(() -> {
            switchSourceButton.setVisibility(visible ? View.VISIBLE : View.GONE);
            updateDownloadButtonsRow();
        });
    }

    private void updateDownloadButtonsRow() {
        if (downloadButtonsRow == null) {
            return;
        }
        boolean any = (pauseButton != null && pauseButton.getVisibility() == View.VISIBLE)
                || (switchSourceButton != null
                && switchSourceButton.getVisibility() == View.VISIBLE);
        downloadButtonsRow.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    private void showSwitchSourcePicker() {
        sourceStepsManualOverride = true;
        setSourceCardExpanded(true);
        List<String> keys = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        GitHubAppStore.Entry userEntry = requestPkg == null
                ? null : GitHubAppStore.get(this, requestPkg);
        if (userEntry != null && userEntry.manual) {
            keys.add(GitHubAppStore.SOURCE_KEY);
            labels.add(getString(R.string.source_github_user_picker, userEntry.repo));
        }
        for (String key : AppSettings.sourceOrder(this)) {
            if (SourceSettings.isEnabled(this, key)) {
                keys.add(key);
                labels.add(AppSettings.sourceLabel(this, key));
            }
        }
        if (keys.isEmpty()) {
            return;
        }

        boolean canPause = pauseButton != null
                && pauseButton.getVisibility() == View.VISIBLE
                && !paused;
        pickerPausedDownload = canPause;
        if (canPause) {
            paused = true;
            pauseButton.setText(pauseLabel(true));
            setStatus(R.string.download_paused);
            updateDownloadService(getString(R.string.download_paused),
                    progress.getProgress(), false);
        }

        boolean[] selected = {false};
        ThemeManager.dialog(this)
                .setTitle(R.string.download_choose_source)
                .setSingleChoiceItems(labels.toArray(new String[0]), -1,
                        (dialog, which) -> {
                            selected[0] = true;
                            pickerPausedDownload = false;
                            dialog.dismiss();
                            switchToSource(keys.get(which));
                        })
                .setOnDismissListener(dialog -> {
                    if (!selected[0] && pickerPausedDownload) {
                        paused = false;
                        pauseButton.setText(pauseLabel(false));
                        setStatus(R.string.download_resuming);
                        updateDownloadService(getString(R.string.download_resuming),
                                progress.getProgress(), false);
                    }
                    pickerPausedDownload = false;
                })
                .show();
    }

    private void switchToSource(String key) {
        retrySources = null;
        switchingSource = true;
        paused = false;
        if (pauseButton != null) {
            pauseButton.setText(pauseLabel(false));
        }
        setStatus(R.string.download_switching_source);
        Call call = activeCall;
        if (call != null) {
            call.cancel();
        }
        for (Call running : new ArrayList<>(activeCalls)) {
            running.cancel();
        }
        cancelWebFlowTimeout();
        boolean webFlow = uptodownSection != null
                && uptodownSection.getVisibility() == View.VISIBLE;
        if (webFlow) {
            if (uptodownWebView != null) {
                uptodownWebContainer.removeView(uptodownWebView);
                uptodownWebView.destroy();
                uptodownWebView = null;
            }
            uptodownSection.setVisibility(View.GONE);
        }

        pendingSwitchKey = key;
        activeSourceKey = key;
        activeSourceName = sourceLabelForKey(key);
        main.post(() -> {
            updateDownloadDetails();
            renderSourceSteps();
        });
        if (SourceSettings.APKMIRROR.equals(key)) {
            apkmirrorRejected = false;
        }
        if (SourceSettings.UPTODOWN.equals(key)) {
            uptodownRejected = false;
        }
        if (SourceSettings.FALLBACK.equals(key)) {
            fallbackRejected = false;
        }
        boolean workerActive = report != null && !report.isFinished();
        if (webFlow || !workerActive) {
            // Nothing is left to unwind: start the selected source now.
            restartAfterSwitch();
        }
    }

    /** Restarts resolution with the source picked while the previous attempt ran. */
    private void restartAfterSwitch() {
        String key = pendingSwitchKey;
        if (key == null) {
            return;
        }
        pendingSwitchKey = null;
        forcedSource = key;
        switchingSource = false;
        paused = false;
        runOnUiThread(() -> {
            if (cancelled) {
                return;
            }
            if (pauseButton != null) {
                pauseButton.setText(pauseLabel(false));
            }
            startResolution(false);
        });
    }

    private void togglePause() {
        if (paused) {
            paused = false;
            pauseButton.setText(pauseLabel(false));
            setStatus(R.string.download_resuming);
            updateDownloadService(getString(R.string.download_resuming),
                    progress.getProgress(), false);
        } else {
            paused = true;
            pauseButton.setText(pauseLabel(true));
            setStatus(R.string.download_paused);
            updateDownloadService(getString(R.string.download_paused),
                    progress.getProgress(), false);
        }
    }
    /** Logs a WebView state transition with elapsed time for diagnostics. */
    private void logWebFlowState(String state, String detail) {
        long elapsed = webFlowStartedAt > 0
                ? System.currentTimeMillis() - webFlowStartedAt : 0;
        String source = webSource == null ? "web" : webSource;
        HelperLog.info("webflow state " + source + " " + state
                + (TextUtils.isEmpty(detail) ? "" : " " + detail)
                + " (+" + elapsed + "ms)");
    }

    /** Tears down the current verification WebView without ending the download. */
    private void closeWebFlow() {
        cancelWebFlowTimeout();
        cancelWebFlowProbe();
        hideVerificationHint();
        cancelAdBlockRetry();
        if (uptodownWebView != null) {
            uptodownWebView.stopLoading();
            uptodownWebContainer.removeView(uptodownWebView);
            uptodownWebView.destroy();
            uptodownWebView = null;
        }
        if (uptodownSection != null) {
            uptodownSection.setVisibility(View.GONE);
        }
    }

    /**
     * Fails the current WebView flow immediately instead of waiting for the
     * 90-second watchdog. Used for main-frame load errors and renderer crashes.
     */
    private void failWebFlowFast(String detail) {
        if (cancelled || uptodownHandled || switchingSource
                || pendingSwitchKey != null) {
            return;
        }
        uptodownHandled = true;
        logWebFlowState("FAILED", detail);
        String message = getString(R.string.download_error_page_failed);
        if (!TextUtils.isEmpty(detail)) {
            message += " (" + detail + ")";
        }
        HelperLog.info(webSourceName() + " WebView failed: " + message);
        continueAfterWebFailure(message);
    }

    /**
     * Fallback hint for a page that stays busy without a detected challenge.
     * Detected challenges show the hint immediately from the page probe.
     */
    private void scheduleVerificationHint() {
        hideVerificationHint();
        webHintShown = false;
        verificationHintRunnable = () -> {
            if (cancelled || uptodownWebView == null || pendingSourceKey == null
                    || uptodownHandled || webChallenge) {
                return;
            }
            webHintShown = true;
            if (verificationHint != null) {
                verificationHint.setVisibility(View.VISIBLE);
            }
        };
        // Uptodown requires a real tap on its Turnstile/Download button, so
        // show the hint early instead of making the user wait ten seconds.
        long delay = "uptodown".equals(webSource) ? 3_000 : 10_000;
        main.postDelayed(verificationHintRunnable, delay);
    }

    private void hideVerificationHint() {
        if (verificationHintRunnable != null) {
            main.removeCallbacks(verificationHintRunnable);
            verificationHintRunnable = null;
        }
        webHintShown = false;
        if (verificationHint != null) {
            verificationHint.setVisibility(View.GONE);
        }
    }

    /** User-initiated jump to the verification WebView. */
    private void scrollToVerification() {
        if (uptodownSection == null
                || uptodownSection.getVisibility() != View.VISIBLE
                || scrollView == null) {
            return;
        }
        scrollView.post(() -> scrollView.smoothScrollTo(0, uptodownSection.getTop()));
    }

    private void markWebFlowResult(boolean ok, String message) {
        hideVerificationHint();
        pendingSourceKey = null;
        if ("uptodown".equals(webSource)) {
            // Uptodown is reached through the Morphe link, so it shares that
            // top-level row in the visible source list. Keep the sub-source in
            // the report and health history without adding a second row.
            if (report != null) {
                report.record(SourceSettings.UPTODOWN, ok,
                        message == null ? "" : message);
            }
            SourceHealth.record(this, SourceSettings.UPTODOWN, ok,
                    message == null ? "" : message);
            markSource(SourceSettings.FALLBACK, ok, message);
        } else {
            markSource(webSourceKey(), ok, message);
        }
    }

    private void markSource(String key, boolean ok, String message) {
        if (switchingSource) {
            // The user is switching away; a cancelled attempt is not a source failure.
            return;
        }
        if (report != null) {
            report.record(key, ok, message);
        }
        if (!ok) {
            SourceNegativeCache.record(this, key, requestPkg, requestVersion,
                    resolvedVersionCodes, requestFileType, message);
        }
        SourceHealth.record(this, key, ok, message == null ? "" : message);
        if (!ok) {
            main.post(() -> {
                sourceStepsManualOverride = true;
                setSourceCardExpanded(true);
            });
        }
        main.post(this::renderSourceSteps);
    }

    /** Records a source that was deliberately skipped, with a visible reason. */
    private void markSourceSkipped(String key, String reason) {
        if (report != null) {
            report.recordSkipped(key, reason);
        }
        HelperLog.info("Skipped " + AppSettings.sourceLabel(this, key)
                + ": " + reason);
        main.post(() -> {
            sourceStepsManualOverride = true;
            setSourceCardExpanded(true);
            renderSourceSteps();
        });
    }

    /** Shows or hides the source rows, keeping a compact summary visible. */
    private void setSourceCardExpanded(boolean expanded) {
        sourceStepsExpanded = expanded;
        if (sourceStepsList != null) {
            sourceStepsList.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        if (sourceStepsSummary != null) {
            sourceStepsSummary.setText(sourceSummaryText);
            sourceStepsSummary.setVisibility(expanded ? View.GONE : View.VISIBLE);
        }
        if (sourceStepsChevron != null) {
            sourceStepsChevron.setRotation(expanded ? 270f : 90f);
        }
    }

    /** Rebuilds the compact source progress list from the current report. */
    private void renderSourceSteps() {
        if (sourceStepsList == null) {
            return;
        }
        sourceStepsList.removeAllViews();
        List<String> order = AppSettings.sourceOrder(this);
        List<ResolutionReport.Attempt> attempts = report == null
                ? Collections.emptyList() : report.attempts();
        boolean finished = report != null && report.isFinished();

        GitHubAppStore.Entry userEntry = requestPkg == null
                ? null : GitHubAppStore.get(this, requestPkg);
        boolean manualUser = userEntry != null && userEntry.manual;
        ResolutionReport.Attempt userLatest = latestAttempt(
                attempts, GitHubAppStore.SOURCE_KEY);
        boolean userActive = GitHubAppStore.SOURCE_KEY.equals(pendingSourceKey)
                || GitHubAppStore.SOURCE_KEY.equals(activeSourceKey);
        boolean showUser = manualUser
                && (userEntry.preferFirst || userLatest != null || userActive);

        if (manualUser && !userEntry.preferFirst && userLatest == null && !userActive) {
            TextView fallbackNote = text(getString(
                    R.string.download_user_github_available, userEntry.repo),
                    11, MUTED, false);
            fallbackNote.setLineSpacing(dp(2), 1f);
            fallbackNote.setPadding(0, dp(4), 0, dp(2));
            sourceStepsList.addView(fallbackNote);
            if (!sourceStepsExpanded) {
                sourceStepsManualOverride = true;
                setSourceCardExpanded(true);
            }
        }

        List<String> display = new ArrayList<>();
        if (showUser) {
            display.add(GitHubAppStore.SOURCE_KEY);
        }
        for (String key : order) {
            if (SourceSettings.isEnabled(this, key)) {
                display.add(key);
            }
        }

        boolean currentMarked = false;
        int position = 0;
        int total = display.size();
        int currentPosition = 0;
        String currentName = "";
        String currentState = "";

        for (String key : display) {
            position++;

            ResolutionReport.Attempt latest = latestAttempt(attempts, key);
            boolean resolving = !finished && !resolutionComplete;
            boolean current;
            if (activeSourceKey != null) {
                current = resolving && activeSourceKey.equals(key);
            } else if (GitHubAppStore.SOURCE_KEY.equals(key)) {
                current = resolving && (pendingSourceKey == null
                        && latest == null && !currentMarked);
            } else {
                current = resolving && (pendingSourceKey != null
                        ? pendingSourceKey.equals(key)
                        : latest == null && !currentMarked);
            }
            if (current) {
                currentMarked = true;
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(7), dp(8), dp(7));
            if (current) {
                GradientDrawable currentBackground = rounded(SECONDARY, 12);
                currentBackground.setStroke(dp(1), SECONDARY_BORDER);
                row.setBackground(currentBackground);
            }

            View badge = SourceBadge.create(this, key);
            row.addView(badge, new LinearLayout.LayoutParams(dp(28), dp(28)));

            String name;
            if (GitHubAppStore.SOURCE_KEY.equals(key)) {
                name = getString(R.string.source_github_user);
            } else if (SourceSettings.APPTEKA.equals(key)) {
                name = getString(R.string.main_source_appteka);
            } else {
                name = AppSettings.sourceLabel(this, key);
            }
            boolean strong = latest != null || current;
            LinearLayout labelColumn = new LinearLayout(this);
            labelColumn.setOrientation(LinearLayout.VERTICAL);
            labelColumn.setPadding(dp(10), 0, 0, 0);
            labelColumn.addView(text(name, 13, strong ? TEXT : MUTED, strong));
            if (latest != null && latest.skipped
                    && !TextUtils.isEmpty(latest.detail)) {
                TextView skipReason = text(latest.detail, 10, MUTED, false);
                skipReason.setPadding(0, dp(2), 0, 0);
                labelColumn.addView(skipReason);
            }
            if (GitHubAppStore.SOURCE_KEY.equals(key) && userEntry != null) {
                labelColumn.addView(text(userEntry.repo, 10, MUTED, false));
            }
            row.addView(labelColumn, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            String stateText;
            int stateColor;
            if (current) {
                stateText = getString(R.string.download_step_current);
                stateColor = GREEN;
            } else if (latest != null) {
                if (latest.skipped) {
                    stateText = getString(R.string.download_step_no_version);
                    stateColor = MUTED;
                } else {
                    stateText = getString(latest.success
                            ? R.string.download_step_ok : R.string.download_step_failed);
                    stateColor = latest.success ? GREEN : RED;
                }
            } else {
                stateText = getString(R.string.download_step_next);
                stateColor = ACCENT_TEXT;
            }
            TextView stateView = text(stateText, 10, stateColor, true);
            if (current) {
                currentPosition = position;
                currentName = name;
                currentState = stateText;
                stateView.setPadding(dp(7), dp(3), dp(7), dp(3));
                GradientDrawable currentPill = rounded(SECONDARY, 999);
                currentPill.setStroke(dp(1), GREEN);
                stateView.setBackground(currentPill);
            }
            row.addView(stateView);
            if (!current) {
                row.setClickable(true);
                row.setFocusable(true);
                row.setContentDescription(
                        getString(R.string.download_switch_source_to, name));
                row.setBackground(new RippleDrawable(
                        ColorStateList.valueOf(RIPPLE),
                        rounded(0x00000000, 12), null));
                row.setOnClickListener(v -> switchToSource(key));
            }
            sourceStepsList.addView(row);
        }

        if (position == 0) {
            sourceStepsList.addView(text(
                    getString(R.string.download_sources_none), 12, MUTED, false));
        }

        if (currentPosition > 0) {
            String summary = getString(R.string.download_sources_collapsed_summary,
                    currentPosition, total, currentName, currentState);
            SpannableString styled = new SpannableString(summary);
            int nameStart = summary.indexOf(currentName);
            if (nameStart >= 0) {
                styled.setSpan(new ForegroundColorSpan(GREEN),
                        nameStart, nameStart + currentName.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int stateStart = summary.lastIndexOf(currentState);
            if (stateStart >= 0) {
                styled.setSpan(new ForegroundColorSpan(GREEN),
                        stateStart, stateStart + currentState.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            sourceSummaryText = styled;
        } else if (total > 0) {
            sourceSummaryText = getString(R.string.download_sources_collapsed_count, total);
        } else {
            sourceSummaryText = "";
        }
        if (sourceStepsSummary != null) {
            sourceStepsSummary.setText(sourceSummaryText);
        }
    }

    private ResolutionReport.Attempt latestAttempt(
            List<ResolutionReport.Attempt> attempts, String key) {
        for (int i = attempts.size() - 1; i >= 0; i--) {
            if (key.equals(attempts.get(i).key)) {
                return attempts.get(i);
            }
        }
        return null;
    }

    private void showFallback() {
        showFailure();
    }

    private void showFailure() {
        cancelAdBlockRetry();
        setPauseVisible(false);
        setSwitchSourceVisible(false);
        main.post(() -> {
            progress.setVisibility(View.GONE);
            if (progressInfo != null) {
                progressInfo.setVisibility(View.GONE);
            }
            if (status != null) {
                status.setVisibility(View.VISIBLE);
            }
            sourceLabel.setVisibility(View.GONE);
            scanNote.setVisibility(View.GONE);
            skipScanButton.setVisibility(View.GONE);
            if (sourceStepsCard != null) {
                sourceStepsCard.setVisibility(View.GONE);
            }
            if (stateIcon != null) {
                stateIcon.setVisibility(View.VISIBLE);
            }
            if (screenTitle != null) {
                screenTitle.setText(R.string.download_title_failure);
            }
            if (screenSubtitle != null) {
                screenSubtitle.setText(R.string.download_subtitle_failure);
                screenSubtitle.setVisibility(View.VISIBLE);
            }
            if (!TextUtils.isEmpty(fallbackUrl)) {
                fallbackButton.setVisibility(View.VISIBLE);
            }
            cancelButton.setText("\u2715  " + getString(R.string.common_close));
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
            reason = getString(R.string.download_error_no_match_detail);
        } else if (!reason.endsWith(".")) {
            reason += ".";
        }
        diagnosticsSummary.setText(reason + (attempts.isEmpty() ? ""
                : " " + getResources().getQuantityString(
                        R.plurals.download_tried_sources, attempts.size(), attempts.size())));

        for (ResolutionReport.Attempt attempt : attempts) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            row.addView(text(attempt.label, 14, TEXT, true));

            String detail = getString(attempt.success
                    ? R.string.common_ok
                    : attempt.skipped
                            ? R.string.common_skipped
                            : R.string.common_failed)
                    + (TextUtils.isEmpty(attempt.detail) ? "" : " \u2022 " + attempt.detail)
                    + (attempt.durationMs > 0
                            ? " \u2022 " + ResolutionReport.durationText(attempt.durationMs)
                            : "");
            TextView detailView = text(detail, 12,
                    attempt.success ? GREEN : attempt.skipped ? MUTED : RED, false);
            detailView.setPadding(0, dp(3), 0, 0);
            row.addView(detailView);
            diagnosticsRows.addView(row);

            View divider = new View(this);
            divider.setBackgroundColor(BORDER);
            diagnosticsRows.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        }

        retryAllButton.setVisibility(View.VISIBLE);
        retryAllButton.setEnabled(true);
        retryButton.setVisibility(failed.isEmpty() ? View.GONE : View.VISIBLE);
        retryButton.setEnabled(true);
        copyButton.setVisibility(View.VISIBLE);
        editSourcesButton.setVisibility(View.VISIBLE);
    }

    /** Restarts the complete ordered search from the first source. */
    private void retryAllSources() {
        retryAllButton.setEnabled(false);
        bypassCache = true;
        forcedSource = null;
        retrySources = null;
        apkmirrorRejected = false;
        uptodownRejected = false;
        fallbackRejected = false;
        startResolution(false);
    }

    private void retryFailedSources() {
        if (report == null || report.failedKeys().isEmpty()) {
            return;
        }
        retryButton.setEnabled(false);
        bypassCache = true;
        forcedSource = null;
        startResolution(true);
    }

    private File downloadFromGitHubSearch(String pkg, String version, String fileType,
                                           String[] abis) throws IOException {
        List<GitHubReleaseResolver.Candidate> candidates =
                GitHubReleaseResolver.search(requestedAppName, pkg, version, fileType, abis);
        if (candidates.isEmpty()) {
            throw new IOException("No matching GitHub release found");
        }
        IOException last = null;
        for (GitHubReleaseResolver.Candidate candidate : candidates) {
            try {
                HelperLog.info("Trying GitHub " + candidate.repo + " "
                        + candidate.tag + " (" + candidate.assetName + ")");
                File file = downloadGitHubFile(
                        candidate.assetUrl,
                        "https://github.com/" + candidate.repo
                                + "/releases/tag/" + candidate.tag,
                        pkg,
                        version,
                        candidate.fileType
                );
                GitHubAppStore.put(this, pkg, candidate.repo, requestedAppName);
                return file;
            } catch (IOException error) {
                last = error;
                HelperLog.error("GitHub asset failed: " + candidate.repo, error);
            }
        }
        throw last == null ? new IOException("No matching GitHub release found") : last;
    }

    private boolean isRetryableWebDownload(Exception error) {
        String message = error == null ? null : error.getMessage();
        return message != null && message.contains("state: 0");
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
                getString(R.string.download_diagnostics_clip_label), report.resultBlock()));
        Toast.makeText(this, R.string.download_diagnostics_copied,
                Toast.LENGTH_SHORT).show();
    }

    /** Cancels background work and returns RESULT_CANCELED to Morphe. */
    private void cancel() {
        if (resultStage) {
            return;
        }
        if (pendingFile != null && pendingFile.exists()) {
            confirmCancelWithFile();
            return;
        }
        cancelInternal(false);
    }

    private void confirmCancelWithFile() {
        ThemeManager.dialog(this)
                .setTitle(R.string.download_cancel_title)
                .setMessage(R.string.download_cancel_message)
                .setNegativeButton(R.string.download_keep_file,
                        (dialog, which) -> cancelInternal(true))
                .setPositiveButton(R.string.download_discard_file,
                        (dialog, which) -> cancelInternal(false))
                .show();
    }

    private void cancelInternal(boolean keepFile) {
        keepPendingFile = keepFile;
        cancelled = true;
        setPauseVisible(false);
        cancelWebFlowTimeout();
        cancelAdBlockRetry();
        stopDownloadService();
        executor.shutdownNow();
        if (!keepFile && pendingFile != null && pendingFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            pendingFile.delete();
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    /** Skips the VirusTotal wait and continues the handoff unscanned. */
    private void skipVirusTotalScan() {
        if (!scanInProgress) {
            return;
        }
        skipScanRequested = true;
        setStatus(R.string.virustotal_skip_status);
        Toast.makeText(this, R.string.virustotal_skip_toast,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (scanInProgress) {
            skipVirusTotalScan();
            return;
        }
        if (resultStage) {
            return;
        }
        // Back behaves exactly like Cancel: no partial file is returned to Morphe.
        cancel();
    }

    @Override
    protected void onDestroy() {
        // Stop network work and release the WebView if it is still open.
        cancelled = true;
        cancelWebFlowTimeout();
        cancelAdBlockRetry();
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

    /** APKPure's plain endpoint serves APK/XAPK; split types use the XAPK path. */
    private boolean apkPureCanServe(String fileType) {
        return !"apkm".equalsIgnoreCase(fileType)
                && !"apks".equalsIgnoreCase(fileType);
    }

    private boolean sourceAllowed(String key) {
        if (GitHubAppStore.SOURCE_KEY.equals(key)) {
            GitHubAppStore.Entry entry = requestPkg == null
                    ? null : GitHubAppStore.get(this, requestPkg);
            return entry != null && entry.manual;
        }
        if (SourceSettings.UPTODOWN.equals(key) && uptodownRejected) {
            return false;
        }
        if (SourceSettings.APKMIRROR.equals(key) && apkmirrorRejected) {
            return false;
        }
        if (SourceSettings.FALLBACK.equals(key) && fallbackRejected) {
            return false;
        }
        if (attemptedSources.contains(key)) {
            return false;
        }
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

    private String installedVersionName(String pkg) {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(pkg, 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Exception ignored) {
            return "";
        }
    }

    private File resolve(String pkg, String version, long[] versionCodes,
                         String fileType, String[] abis) throws IOException {
        long[] codes = versionCodes;
        if (codes == null || codes.length == 0) {
            setStatus(R.string.download_checking_version);
            String installedName = installedVersionName(pkg);
            long installed = installedVersionCode(pkg);
            if (installed > 0 && (TextUtils.isEmpty(version)
                    || sameVersion(installedName, version))) {
                codes = new long[]{installed};
                HelperLog.info("Using installed versionCode=" + installed + " for " + pkg);
            } else if (installed > 0) {
                HelperLog.info("Ignoring installed versionCode=" + installed
                        + " for " + pkg + " because installed version is "
                        + installedName + " (requested " + version + ")");
            }
        }

        // The resolver may substitute the installed version code when the
        // request did not supply one. Treat that exact code as authoritative,
        // so APKPure can use its direct endpoint instead of the API first.
        boolean hasCodes = codes != null && codes.length > 0;
        resolvedVersionCodes = codes;

        if (cacheAllowed) {
            File cached = DownloadCache.find(this, pkg, version, codes, fileType);
            if (cached != null) {
                HelperLog.info("Using validated cached download "
                        + cached.getAbsolutePath());
                setSourceLabel(R.string.source_cache);
                setStatus(R.string.download_using_cache);
                servedFromCache = true;
                if (report != null) {
                    report.record(SourceSettings.CACHE, true,
                            getString(R.string.source_detail_cache));
                }
                return cached;
            }
        }

        // A single ordered resolver keeps the list shown in the UI and the
        // actual fallback path identical. Default order: Morphe link, APKMirror,
        // APKPure, then the remaining enabled sources.
        List<String> order = AppSettings.sourceOrder(this);
        HelperLog.info("Source order: " + TextUtils.join(" -> ", order));
        try {
            return resolveCustom(pkg, version, codes, hasCodes, fileType, abis);
        } finally {
            cancelApkCubePreflight();
            cancelSourcePrefetch();
            cancelApiSourceProbes();
        }
    }

    /**
     * Downloads a GitHub asset and additionally verifies that its signing
     * certificate matches the installed app when that app is available.
     */
    private File downloadGitHubFile(String url, String referer, String pkg, String version,
                                    String fileType) throws IOException {
        File file = null;
        try {
            file = downloadFile(url, referer, pkg, version, fileType);
            // GitHub APKs are official builds, but the installed app is often a
            // patched build signed with a different key. Morphe handles signing
            // and patching, so do not reject the APK based on the installed
            // signature here. Package/version validation still runs in
            // downloadFile/DownloadedFileValidator.
            return file;
        } catch (IOException error) {
            if (file != null) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            if (isVerificationFailure(error)) {
                verificationFailure = error.getMessage();
            }
            throw error;
        }
    }

    /** True for a package/version rejection rather than a network error. */
    private boolean isVerificationFailure(IOException error) {
        return error instanceof DownloadedFileValidator.VersionMismatchException;
    }

    /**
     * Uses a GitHub repository supplied by Morphe's resolver. This is part of
     * the Morphe fallback source and remains available even when the automatic
     * GitHub source is disabled.
     */
    private File tryResolverGitHub(String repo, String pkg, String version,
                                   String fileType, String[] abis) {
        githubResolverHint = true;
        HelperLog.info("Morphe resolver/fallback points to GitHub repo " + repo
                + "; using the release for this patch source");
        GitHubAppStore.put(this, pkg, repo, requestedAppName);
        setSourceLabel(R.string.source_label_morphe_resolver);
        setStatus(R.string.download_checking_github_release);
        try {
            GitHubAppStore.Entry entry = new GitHubAppStore.Entry(
                    pkg, repo, requestedAppName, System.currentTimeMillis(),
                    false, "", false);
            File file = downloadFromRememberedGitHub(entry, pkg, version, fileType, abis);
            markSource(SourceSettings.FALLBACK, true,
                    getString(R.string.source_detail_resolver_github));
            return file;
        } catch (Exception failed) {
            HelperLog.error("GitHub resolver release failed", failed);
            markSource(SourceSettings.FALLBACK, false, failed.getMessage());
            return null;
        }
    }

    /**
     * Tries the remembered GitHub repository first, then an automatic GitHub
     * release search. Returns null when GitHub is not a viable source for this
     * request. The caller must check that GitHub is enabled.
     */
    private File tryGitHubFallback(String pkg, String version, String fileType,
                                   String[] abis) {
        GitHubAppStore.Entry remembered = GitHubAppStore.get(this, pkg);
        if (remembered != null && !remembered.manual) {
            setSourceLabel(R.string.source_label_github);
            setStatus(R.string.download_checking_github_releases);
            HelperLog.info("Trying remembered GitHub repo " + remembered.repo);
            try {
                File file = downloadFromRememberedGitHub(
                        remembered, pkg, version, fileType, abis);
                markSource(SourceSettings.GITHUB, true, remembered.repo);
                return file;
            } catch (Exception error) {
                HelperLog.error("Remembered GitHub repo failed", error);
                markSource(SourceSettings.GITHUB, false, error.getMessage());
            }
        }

        boolean hasRequestedVersion = !TextUtils.isEmpty(version)
                && !"any".equalsIgnoreCase(version.trim());
        if (!hasRequestedVersion) {
            setSourceLabel(R.string.source_label_github);
            setStatus(R.string.download_skipping_github_no_version);
            HelperLog.info(
                    "Skipping automatic GitHub search: no version supplied");
            markSourceSkipped(SourceSettings.GITHUB,
                    getString(R.string.source_skip_github_no_version));
            return null;
        }
        setSourceLabel(R.string.source_label_github);
        setStatus(R.string.download_searching_github);
        HelperLog.info("Searching GitHub releases");
        try {
            File file = downloadFromGitHubSearch(pkg, version, fileType, abis);
            markSource(SourceSettings.GITHUB, true,
                    getString(R.string.source_detail_automatic_search));
            return file;
        } catch (Exception error) {
            HelperLog.error("Automatic GitHub search failed", error);
            markSource(SourceSettings.GITHUB, false, error.getMessage());
        }
        return null;
    }

    private File downloadFromRememberedGitHub(GitHubAppStore.Entry entry, String pkg,
                                               String version, String fileType,
                                               String[] abis) throws IOException {
        List<GitHubReleaseResolver.Candidate> candidates =
                GitHubReleaseResolver.findInRepo(entry.repo, version, fileType, abis,
                        entry.assetFilter);
        if (candidates.isEmpty()) {
            throw new IOException("No matching release in " + entry.repo);
        }
        IOException last = null;
        for (GitHubReleaseResolver.Candidate candidate : candidates) {
            try {
                return downloadGitHubFile(
                        candidate.assetUrl,
                        "https://github.com/" + candidate.repo
                                + "/releases/tag/" + candidate.tag,
                        pkg,
                        version,
                        candidate.fileType
                );
            } catch (IOException error) {
                last = error;
                HelperLog.error("Remembered GitHub asset failed: "
                        + candidate.repo + " " + candidate.assetName, error);
            }
        }
        throw last == null ? new IOException("No matching GitHub asset") : last;
    }

    /**
     * Tries the GitHub repository the user explicitly mapped to this package.
     * Used both as the preferred automatic source and when forced from the
     * Switch source picker.
     */
    private File customUserGitHub(GitHubAppStore.Entry entry, String pkg, String version,
                                  String fileType, String[] abis) {
        if (entry == null || !entry.manual) {
            entry = GitHubAppStore.get(this, pkg);
        }
        if (entry == null || !entry.manual) {
            markSource(GitHubAppStore.SOURCE_KEY, false,
                    getString(R.string.source_detail_user_github_missing));
            return null;
        }
        pendingSourceKey = GitHubAppStore.SOURCE_KEY;
        main.post(this::renderSourceSteps);
        setSourceLabel(getString(R.string.source_label_user_github, entry.repo));
        setStatus(R.string.download_checking_user_github);
        HelperLog.info("Trying user GitHub repo " + entry.repo + " for " + pkg);
        try {
            File file = downloadFromRememberedGitHub(entry, pkg, version, fileType, abis);
            markSource(GitHubAppStore.SOURCE_KEY, true, entry.repo);
            return file;
        } catch (Exception error) {
            HelperLog.error("User GitHub repo failed", error);
            markSource(GitHubAppStore.SOURCE_KEY, false, error.getMessage());
            return null;
        } finally {
            pendingSourceKey = null;
            main.post(this::renderSourceSteps);
        }
    }

    private File resolveCustom(String pkg, String version, long[] codes, boolean hasCodes,
                               String fileType, String[] abis) throws IOException {
        String fallbackRepo = githubRepoFromUrl(fallbackUrl);
        if (fallbackRepo != null) {
            githubResolverHint = true;
            HelperLog.info("Remembering fallback GitHub repo " + fallbackRepo
                    + " for custom source order");
            GitHubAppStore.put(this, pkg, fallbackRepo, requestedAppName);
        }
        List<String> order = AppSettings.sourceOrder(this);
        GitHubAppStore.Entry userGitHub = GitHubAppStore.get(this, pkg);
        boolean userFirst = userGitHub != null && userGitHub.manual && userGitHub.preferFirst;

        List<String> sequence = new ArrayList<>();
        if (userFirst) {
            sequence.add(GitHubAppStore.SOURCE_KEY);
        }
        sequence.addAll(order);

        boolean forcedOnly = false;
        String wanted = forcedSource;
        forcedSource = null;
        if (wanted != null) {
            sequence.clear();
            sequence.add(wanted);
            forcedOnly = true;
        }

        // APKCube is often last. Start its HTML preflight now so its network
        // latency overlaps the other sources instead of extending the chain.
        if (!forcedOnly && sequence.indexOf(SourceSettings.APKCUBE) > 0) {
            startApkCubePreflight(pkg, codes, abis);
        }
        if (cacheAllowed) {
            apkPurePrefetchStarted = false;
            startSourcePrefetch(pkg);
            startApiSourceProbes(pkg, version, codes, fileType, false);
        }

        for (String source : sequence) {
            if (pendingSwitchKey != null) {
                throw new IOException("Switched source");
            }
            if (cacheAllowed && !apkPurePrefetchStarted
                    && !SourceSettings.FALLBACK.equals(source)) {
                startApiSourceProbes(pkg, version, codes, fileType, true);
            }
            if (!sourceAllowed(source)) {
                if (forcedOnly) {
                    break;
                }
                continue;
            }
            attemptedSources.add(source);
            if (cacheAllowed && SourceNegativeCache.isCacheableSource(source)) {
                String cached = SourceNegativeCache.get(this, source, pkg, version,
                        codes, fileType);
                if (cached != null) {
                    HelperLog.info("Skipping " + source
                            + " from negative cache: " + cached);
                    markSource(source, false,
                            getString(R.string.source_cached_failure, cached));
                    continue;
                }
            }
            activeSourceKey = source;
            activeSourceName = sourceLabelForKey(source);
            webParentSourceKey = null;
            main.post(() -> {
                updateDownloadDetails();
                renderSourceSteps();
            });
            File file = null;
            if (GitHubAppStore.SOURCE_KEY.equals(source)) {
                file = customUserGitHub(userGitHub, pkg, version, fileType, abis);
            } else if (SourceSettings.APKPURE.equals(source)) {
                file = customApkPure(pkg, version, codes, hasCodes, fileType, abis);
            } else if (SourceSettings.APKCUBE.equals(source)) {
                file = customApkCube(pkg, version, codes, fileType, abis);
            } else if (SourceSettings.UPTODOWN.equals(source)) {
                file = customUptodown(pkg, version, fileType);
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
            } else if (SourceSettings.GITHUB.equals(source)) {
                file = tryGitHubFallback(pkg, version, fileType, abis);
            }
            if (pendingSwitchKey != null) {
                throw new IOException("Switched source");
            }
            if (file != null) {
                return file;
            }
            if (forcedOnly) {
                switchingSource = false;
                break;
            }
        }

        // A package/version/signature rejection is not a network problem. Opening
        // the APKPure WebView in that case only hides the reason and usually
        // cannot produce a matching APK either. Show the verification error.
        if (!TextUtils.isEmpty(verificationFailure)) {
            throw new IOException(verificationFailure);
        }

        if (pendingSwitchKey != null) {
            throw new IOException("Switched source");
        }
        if (!forcedOnly && allowSplitArchive && sourceAllowed(SourceSettings.APKPURE)
                && (apkPureCanServe(fileType) || isSplitType(fileType))) {
            main.post(() -> {
                setStatus(R.string.download_opening_apkpure_last_resort);
                progress.setIndeterminate(true);
                startApkPureWebFlow(pkg, version, fileType);
            });
            throw new UptodownHandoffException();
        }
        throw new IOException("No enabled download source found an APK");
    }

    private File customApkPure(String pkg, String version, long[] codes, boolean hasCodes,
                               String fileType, String[] abis) {
        if (isSplitType(fileType)) {
            return customApkPureSplit(pkg, version, codes, hasCodes, fileType, abis);
        }
        if (!apkPureCanServe(fileType)) {
            return null;
        }
        if (!hasCodes) {
            if (AppSettings.fastSourceChecks(this)) {
                return customApkPureFast(pkg, version, codes, fileType, abis);
            }
            // Without a versionCode, resolve through the API first. This still
            // runs at APKPure's configured position instead of after GitHub.
            setSourceLabel(R.string.source_label_apkpure_api);
            setStatus(R.string.download_resolving_apkpure_api);
            try {
                File file = downloadFromApkPureApi(pkg, version, codes, fileType, abis);
                markSource(SourceSettings.APKPURE, true, "API");
                return file;
            } catch (Exception apiError) {
                markSource(SourceSettings.APKPURE, false, "API: " + apiError.getMessage());
                HelperLog.error("APKPure API failed", apiError);
            }
            setSourceLabel(R.string.source_label_apkpure_direct);
            setStatus(R.string.download_trying_apkpure_direct);
            try {
                File file = downloadFromApkPure(pkg, version, codes, fileType);
                markSource(SourceSettings.APKPURE, true, "direct");
                return file;
            } catch (Exception directError) {
                HelperLog.error("APKPure direct failed", directError);
                markSource(SourceSettings.APKPURE, false,
                        "direct: " + directError.getMessage());
                return null;
            }
        }
        setSourceLabel(R.string.source_label_apkpure_direct);
        setStatus(R.string.download_trying_apkpure_direct);
        try {
            File file = downloadFromApkPure(pkg, version, codes, fileType);
            markSource(SourceSettings.APKPURE, true, "direct");
            return file;
        } catch (Exception directError) {
            markSource(SourceSettings.APKPURE, false,
                    "direct: " + directError.getMessage());
            HelperLog.error("APKPure fast direct failed", directError);
        }
        ApkPureAssets.Asset prefetched = takeApkPureApiPrefetch();
        if (prefetched != null) {
            try {
                File file = downloadApkPureAsset(
                        prefetched, pkg, version, "https://apkpure.com/");
                markSource(SourceSettings.APKPURE, true, "API");
                return file;
            } catch (Exception error) {
                HelperLog.error("APKPure prefetched API asset failed", error);
            }
        }
        setSourceLabel(R.string.source_label_apkpure_api);
        setStatus(R.string.download_resolving_apkpure_api);
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

    /**
     * Split requests must use APKPure's XAPK asset. The standalone APK endpoint
     * returns only the base APK for split apps, which cannot be installed. The
     * direct XAPK endpoint also works for build IDs that are no longer in the
     * API's latest-20 list, so try it before the API when a code is available.
     */
    private File customApkPureSplit(String pkg, String version, long[] codes,
                                    boolean hasCodes, String fileType, String[] abis) {
        if (!hasCodes && AppSettings.fastSourceChecks(this)) {
            return customApkPureFast(pkg, version, codes, fileType, abis);
        }
        Exception directError = null;
        if (hasCodes) {
            try {
                return downloadApkPureXapk(pkg, version, codes, fileType);
            } catch (Exception failed) {
                directError = failed;
                HelperLog.error("APKPure direct XAPK failed", failed);
            }
        }

        ApkPureAssets.Asset prefetched = takeApkPureApiPrefetch();
        if (prefetched != null) {
            try {
                File file = downloadApkPureAsset(
                        prefetched, pkg, version, "https://apkpure.com/");
                markSource(SourceSettings.APKPURE, true, "API");
                return file;
            } catch (Exception error) {
                HelperLog.error("APKPure split prefetched API asset failed", error);
            }
        }
        setSourceLabel(R.string.source_label_apkpure_api);
        setStatus(R.string.download_resolving_apkpure_api);
        try {
            File file = downloadFromApkPureApi(pkg, version, codes, fileType, abis);
            markSource(SourceSettings.APKPURE, true, "API");
            return file;
        } catch (Exception apiError) {
            HelperLog.error("APKPure split API failed", apiError);
            if (!hasCodes) {
                try {
                    return downloadApkPureXapk(pkg, version, codes, fileType);
                } catch (Exception direct) {
                    directError = direct;
                    HelperLog.error("APKPure direct XAPK failed", direct);
                }
            }
            String reason = apiError.getMessage();
            if (directError != null && !TextUtils.isEmpty(directError.getMessage())) {
                reason = "direct XAPK: " + directError.getMessage()
                        + "; API: " + apiError.getMessage();
            }
            markSource(SourceSettings.APKPURE, false, reason);
            return null;
        }
    }

    /**
     * Starts the APKPure API and direct requests together, then downloads from
     * the API result when available (the existing preference) or the direct
     * result when the API is slow or missing. The two probe requests are
     * metadata-only; only one file download is started.
     */
    private File customApkPureFast(String pkg, String version, long[] codes,
                                   String fileType, String[] abis) {
        long deadline = System.currentTimeMillis() + 12_000L;
        Future<ApkPureAssets.Asset> apiFuture = apkPureApiPrefetch;
        Future<ApkPureAssets.Asset> directFuture = apkPureDirectPrefetch;
        apkPureApiPrefetch = null;
        apkPureDirectPrefetch = null;
        ExecutorService pool = null;
        boolean ownsPool = false;
        if (apiFuture == null || directFuture == null) {
            pool = Executors.newFixedThreadPool(2);
            apiFuture = pool.submit(
                    () -> resolveApkPureApiAsset(pkg, version, codes, fileType));
            directFuture = pool.submit(
                    () -> probeApkPureDirectAsset(pkg, version, codes, fileType));
            ownsPool = true;
        }
        ApkPureAssets.Asset api = null;
        ApkPureAssets.Asset direct = null;
        Exception apiError = null;
        Exception directError = null;
        try {
            try {
                // The API is normally the faster path, but do not let a slow
                // API hold back an already-resolved direct URL.
                long apiWait = Math.min(4_000L,
                        deadline - System.currentTimeMillis());
                api = apiFuture.get(Math.max(1L, apiWait), TimeUnit.MILLISECONDS);
            } catch (Exception error) {
                apiError = rootCause(error);
                apiFuture.cancel(true);
            }
            if (api != null) {
                setSourceLabel(R.string.source_label_apkpure_api);
                setStatus(R.string.download_resolving_apkpure_api);
                try {
                    File file = downloadApkPureAsset(
                            api, pkg, version, "https://apkpure.com/");
                    markSource(SourceSettings.APKPURE, true, "API");
                    return file;
                } catch (Exception error) {
                    apiError = rootCause(error);
                    HelperLog.error("APKPure API asset failed", apiError);
                }
            }

            try {
                direct = directFuture.get(
                        Math.max(1L, deadline - System.currentTimeMillis()),
                        TimeUnit.MILLISECONDS);
            } catch (Exception error) {
                directError = rootCause(error);
                directFuture.cancel(true);
            }
            if (direct != null) {
                setSourceLabel(R.string.source_label_apkpure_direct);
                setStatus(R.string.download_trying_apkpure_direct);
                try {
                    File file = downloadApkPureAsset(direct, pkg, version, null);
                    markSource(SourceSettings.APKPURE, true, "direct");
                    return file;
                } catch (Exception error) {
                    directError = rootCause(error);
                    HelperLog.error("APKPure direct asset failed", directError);
                }
            }

            String reason = "API: " + errorText(apiError)
                    + "; direct: " + errorText(directError);
            markSource(SourceSettings.APKPURE, false, reason);
            return null;
        } finally {
            if (ownsPool) {
                apiFuture.cancel(true);
                directFuture.cancel(true);
                pool.shutdownNow();
            }
        }
    }

    /** Resolves the direct APKPure URL and verifies that it returns an APK. */
    private ApkPureAssets.Asset probeApkPureDirectAsset(String pkg, String version,
                                                        long[] codes, String fileType)
            throws IOException {
        String url = buildApkPureUrl(pkg, codes, version, fileType);
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .header("Range", "bytes=0-0")
                .build();
        HelperLog.info("APKPure direct probe " + url);
        try (Response response = PREFLIGHT_HTTP.newCall(request).execute()) {
            String type = response.header("Content-Type");
            String disposition = response.header("Content-Disposition");
            HelperLog.info("APKPure direct probe -> HTTP " + response.code()
                    + " type=" + type + " disposition=" + disposition);
            int code = response.code();
            if (code != 200 && code != 206) {
                throw new IOException("HTTP " + code);
            }
            if (!SourceProbeRules.looksLikeApk(type, disposition)) {
                throw new IOException("APKPure direct did not return an APK");
            }
            long versionCode = codes != null && codes.length > 0 ? codes[0] : -1;
            return new ApkPureAssets.Asset(
                    url, isSplitType(fileType) ? "XAPK" : "APK",
                    version, versionCode, 0);
        }
    }

    private static Exception rootCause(Exception error) {
        if (error instanceof java.util.concurrent.ExecutionException
                && error.getCause() instanceof Exception) {
            return (Exception) error.getCause();
        }
        return error;
    }

    private static String errorText(Exception error) {
        String message = error == null ? "" : error.getMessage();
        return TextUtils.isEmpty(message) ? "unknown error" : message;
    }

    private File downloadApkPureXapk(String pkg, String version, long[] codes,
                                     String fileType) throws IOException {
        setSourceLabel(R.string.source_label_apkpure_xapk);
        setStatus(R.string.download_trying_xapk);
        String url = buildApkPureUrl(pkg, codes, version, "xapk");
        HelperLog.info("APKPure XAPK URL: " + url);
        File file = downloadFile(url, null, pkg, version, fileType);
        markSource(SourceSettings.APKPURE, true,
                getString(R.string.source_detail_direct_xapk));
        return file;
    }

    private File customMorphe(String pkg, String version, long[] codes,
                              String fileType, String[] abis) throws IOException {
        String abi = (abis != null && abis.length > 0) ? abis[0] : "arm64-v8a";
        String key = pkg + "|" + version + "|" + abi;
        String url = ResolverCache.get(this, key);
        if (url != null) {
            setStatus(R.string.download_using_cached_resolver);
        } else {
            setStatus(R.string.download_checking_resolver);
            url = resolveMorpheUrl(pkg, version, abis);
            if (url != null && !isHost(hostOf(url), "google.com")) {
                ResolverCache.put(this, key, url);
            }
        }
        if (url != null) {
            String host = hostOf(url);
            String githubRepo = githubRepoFromUrl(url);
            setStatus(getString(R.string.download_resolver_found, host));
            // An Uptodown resolver link must not jump ahead of the configured
            // source order. Uptodown is a normal ordered source now, so let
            // resolution reach it naturally; its API and WebView fallback
            // still use the same package and version.
            if (isHost(host, "apkpure.com") && apkPureCanServe(fileType)
                    && sourceAllowed(SourceSettings.APKPURE)) {
                try {
                    File file = downloadFromApkPureApi(pkg, version, codes, fileType, abis);
                    markSource(SourceSettings.FALLBACK, true, getString(R.string.source_detail_resolver_apkpure));
                    return file;
                } catch (Exception failed) {
                    HelperLog.error("Morphe resolver APKPure link failed", failed);
                }
            // A resolver-supplied APKMirror link is an exact page, not the
            // automatic search. Do not let an earlier automatic APKMirror
            // mismatch block this exact link.
            } else if (isHost(host, "apkmirror.com")
                    && SourceSettings.isEnabled(this, SourceSettings.APKMIRROR)) {
                try {
                    File file = downloadFromFallback(url, pkg, version, fileType, abis);
                    markSource(SourceSettings.FALLBACK, true, getString(R.string.source_detail_resolver_apkmirror));
                    return file;
                } catch (UptodownHandoffException handoff) {
                    throw handoff;
                } catch (Exception failed) {
                    HelperLog.error("Morphe resolver APKMirror link failed", failed);
                }
            } else if (githubRepo != null) {
                File file = tryResolverGitHub(
                        githubRepo, pkg, version, fileType, abis);
                if (file != null) {
                    return file;
                }
                if (isDirectFile(url)) {
                    try {
                        File direct = downloadGitHubFile(
                                url, null, pkg, version, fileType);
                        markSource(SourceSettings.FALLBACK, true,
                                getString(R.string.source_detail_resolver_direct));
                        return direct;
                    } catch (Exception failed) {
                        HelperLog.error("Morphe resolver GitHub file failed", failed);
                    }
                }
            } else if (isDirectFile(url)) {
                try {
                    File file = isHost(host, "github.com")
                            ? downloadGitHubFile(url, null, pkg, version, fileType)
                            : downloadFile(url, null, pkg, version, fileType);
                    markSource(SourceSettings.FALLBACK, true, getString(R.string.source_detail_resolver_direct));
                    return file;
                } catch (Exception failed) {
                    HelperLog.error("Morphe resolver direct file failed", failed);
                }
            }
            markSource(SourceSettings.FALLBACK, false, getString(R.string.source_detail_resolver_no_file));
        } else {
            markSource(SourceSettings.FALLBACK, false, getString(R.string.source_detail_no_resolver_result));
        }

        if (!TextUtils.isEmpty(fallbackUrl)) {
            String fallbackRepo = githubRepoFromUrl(fallbackUrl);
            if (fallbackRepo != null) {
                File file = tryResolverGitHub(
                        fallbackRepo, pkg, version, fileType, abis);
                if (file != null) {
                    return file;
                }
                if (isDirectFile(fallbackUrl)) {
                    try {
                        File direct = downloadGitHubFile(
                                fallbackUrl, null, pkg, version, fileType);
                        markSource(SourceSettings.FALLBACK, true,
                                getString(R.string.source_detail_resolver_direct));
                        return direct;
                    } catch (Exception failed) {
                        HelperLog.error("Fallback GitHub file failed", failed);
                    }
                }
            } else {
                String host = hostOf(fallbackUrl);
                boolean disabled = (isHost(host, "apkmirror.com")
                        && !sourceAllowed(SourceSettings.APKMIRROR))
                        || (isHost(host, "apkpure.com")
                        && !sourceAllowed(SourceSettings.APKPURE))
                        || (isHost(host, "uptodown.com")
                        && !sourceAllowed(SourceSettings.UPTODOWN));
                if (!disabled) {
                    setStatus(R.string.download_trying_fallback);
                    try {
                        File file = downloadFromFallback(fallbackUrl, pkg, version, fileType, abis);
                        markSource(SourceSettings.FALLBACK, true, getString(R.string.source_detail_fallback_link));
                        return file;
                    } catch (UptodownHandoffException handoff) {
                        throw handoff;
                    } catch (Exception failed) {
                        HelperLog.error("Fallback failed", failed);
                        markSource(SourceSettings.FALLBACK, false, failed.getMessage());
                    }
                }
            }
        }
        return null;
    }

    private File customApkCube(String pkg, String version, long[] codes,
                               String fileType, String[] abis) throws IOException {
        setSourceLabel(R.string.source_label_apkcube);
        setStatus(R.string.download_searching_apkcube);
        final String url = apkCubeUrl(pkg, codes, abis);
        HelperLog.info("APKCube search URL: " + url);

        boolean noMatch = false;
        Future<Boolean> preflight = apkCubePreflightFuture;
        apkCubePreflightFuture = null;
        if (preflight != null) {
            if (preflight.isDone()) {
                try {
                    noMatch = Boolean.TRUE.equals(preflight.get());
                } catch (Exception error) {
                    HelperLog.info("APKCube preflight failed: " + error.getMessage());
                }
            } else {
                // Do not delay the WebView. If the background preflight later
                // finds a no-match page, it closes the WebView immediately.
                watchApkCubePreflight(preflight);
            }
        } else {
            noMatch = apkCubePreflightNoMatch(url);
        }
        if (noMatch) {
            HelperLog.info("APKCube preflight: no match; skipping WebView");
            markSource(SourceSettings.APKCUBE, false,
                    getString(R.string.download_error_apkcube_not_found));
            return null;
        }
        main.post(() -> {
            setStatus(R.string.download_opening_apkcube);
            progress.setIndeterminate(true);
            startApkCubeWebFlow(url, pkg, version, fileType);
        });
        throw new UptodownHandoffException();
    }

    /** Closes an active APKCube WebView if the background preflight finds no match. */
    private void watchApkCubePreflight(Future<Boolean> preflight) {
        Thread watcher = new Thread(() -> {
            try {
                if (!Boolean.TRUE.equals(preflight.get())) {
                    return;
                }
                main.post(() -> {
                    if (!"apkcube".equals(webSource) || uptodownHandled) {
                        return;
                    }
                    logWebFlowState("NOT_FOUND", "preflight");
                    failWebFlowFast(
                            getString(R.string.download_error_apkcube_not_found));
                });
            } catch (Exception ignored) {
                // Cancelled, timed out, or the WebView already finished.
            }
        }, "apkcube-preflight-watch");
        watcher.setDaemon(true);
        watcher.start();
    }

    /** Starts the APKCube HTML preflight in the background for a later source. */
    private void startApkCubePreflight(String pkg, long[] codes, String[] abis) {
        cancelApkCubePreflight();
        if (!AppSettings.fastSourceChecks(this)
                || !sourceAllowed(SourceSettings.APKCUBE)) {
            return;
        }
        final String url = apkCubeUrl(pkg, codes, abis);
        FutureTask<Boolean> task = new FutureTask<>(() -> {
            // Give a fast earlier source a chance to finish before spending
            // data on the APKCube HTML probe.
            Thread.sleep(1_000L);
            return apkCubePreflightNoMatch(url);
        });
        Thread thread = new Thread(task, "apkcube-preflight");
        thread.setDaemon(true);
        thread.start();
        apkCubePreflightFuture = task;
    }

    private void cancelApkCubePreflight() {
        Future<Boolean> preflight = apkCubePreflightFuture;
        apkCubePreflightFuture = null;
        if (preflight != null) {
            preflight.cancel(true);
        }
    }

    /** Starts metadata fetches for HTTP sources that are likely to run later. */
    private void startSourcePrefetch(String pkg) {
        cancelSourcePrefetch();
        if (!AppSettings.fastSourceChecks(this) || TextUtils.isEmpty(pkg)) {
            return;
        }
        sourcePrefetchCancelled = false;
        try {
            if (sourceAllowed(SourceSettings.APKMIRROR)
                    && TextUtils.isEmpty(AppSettings.apkMirrorAppUrl(this, pkg))) {
                prefetchUrl(APKMIRROR + "/?s="
                        + URLEncoder.encode(pkg, "UTF-8"));
            }
            if (sourceAllowed(SourceSettings.FDROID)) {
                prefetchUrl("https://f-droid.org/api/v1/packages/"
                        + URLEncoder.encode(pkg, "UTF-8"));
            }
            if (sourceAllowed(SourceSettings.APTOIDE)) {
                prefetchUrl("https://ws75.aptoide.com/api/7/apps/search?query="
                        + URLEncoder.encode(pkg, "UTF-8") + "&limit=10");
            }
        } catch (Exception error) {
            HelperLog.error("Could not start source prefetch", error);
        }
    }

    private void prefetchUrl(final String url) {
        if (sourcePrefetches.containsKey(url)) {
            return;
        }
        FutureTask<String> task = new FutureTask<>(() -> {
            if (sourcePrefetchCancelled) {
                return "";
            }
            return httpGetNetwork(url, null);
        });
        Thread thread = new Thread(task, "source-prefetch");
        thread.setDaemon(true);
        thread.start();
        sourcePrefetches.put(url, task);
    }

    private void cancelSourcePrefetch() {
        sourcePrefetchCancelled = true;
        for (Future<String> future : sourcePrefetches.values()) {
            future.cancel(true);
        }
        sourcePrefetches.clear();
    }

    private void startApiSourceProbes(String pkg, String version, long[] codes,
                                      String fileType, boolean includeApkPure) {
        if (!AppSettings.fastSourceChecks(this)) {
            return;
        }
        if (apiProbePool == null) {
            apiProbePool = Executors.newFixedThreadPool(3);
        }
        if (uptodownPrefetch == null && sourceAllowed(SourceSettings.UPTODOWN)) {
            uptodownPrefetch = apiProbePool.submit(
                    () -> UptodownApi.prepare(pkg, version, fileType));
        }
        if (includeApkPure && !apkPurePrefetchStarted
                && sourceAllowed(SourceSettings.APKPURE)) {
            apkPurePrefetchStarted = true;
            apkPureApiPrefetch = apiProbePool.submit(
                    () -> resolveApkPureApiAsset(pkg, version, codes, fileType));
            apkPureDirectPrefetch = apiProbePool.submit(
                    () -> probeApkPureDirectAsset(pkg, version, codes, fileType));
        }
    }

    private void cancelApiSourceProbes() {
        if (apkPureApiPrefetch != null) {
            apkPureApiPrefetch.cancel(true);
            apkPureApiPrefetch = null;
        }
        if (apkPureDirectPrefetch != null) {
            apkPureDirectPrefetch.cancel(true);
            apkPureDirectPrefetch = null;
        }
        if (uptodownPrefetch != null) {
            uptodownPrefetch.cancel(true);
            uptodownPrefetch = null;
        }
        if (apiProbePool != null) {
            apiProbePool.shutdownNow();
            apiProbePool = null;
        }
        apkPurePrefetchStarted = false;
    }

    private UptodownApi.Prepared takeUptodownPrefetch() {
        Future<UptodownApi.Prepared> future = uptodownPrefetch;
        uptodownPrefetch = null;
        if (future == null) {
            return null;
        }
        if (!future.isDone()) {
            future.cancel(true);
            return null;
        }
        try {
            return future.get();
        } catch (Exception error) {
            HelperLog.info("Uptodown prefetch failed: " + error.getMessage());
            return null;
        }
    }

    private ApkPureAssets.Asset takeApkPureApiPrefetch() {
        Future<ApkPureAssets.Asset> future = apkPureApiPrefetch;
        apkPureApiPrefetch = null;
        if (future == null) {
            return null;
        }
        if (!future.isDone()) {
            future.cancel(true);
            return null;
        }
        try {
            return future.get();
        } catch (Exception error) {
            HelperLog.info("APKPure API prefetch failed: " + error.getMessage());
            return null;
        }
    }

    /**
     * APKCube renders a definitive no-match page on the server. A cheap HTML
     * preflight avoids creating the WebView when the package is already absent.
     * Anything ambiguous falls back to the normal WebView flow.
     */
    private boolean apkCubePreflightNoMatch(String url) {
        if (!AppSettings.fastSourceChecks(this)) {
            return false;
        }
        long started = System.currentTimeMillis();
        try {
            String html = httpGetPreflight(url);
            boolean noMatch = SourceProbeRules.apkCubeHtmlNoMatch(html);
            HelperLog.info("APKCube preflight: noMatch=" + noMatch
                    + " elapsed=" + (System.currentTimeMillis() - started) + "ms");
            return noMatch;
        } catch (Exception error) {
            HelperLog.info("APKCube preflight skipped: " + error.getMessage());
            return false;
        }
    }

    private File customApkMirror(String pkg, String version, String fileType, String[] abis)
            throws IOException {
        setSourceLabel(R.string.source_label_apkmirror);
        setStatus(R.string.download_searching_apkmirror);
        try {
            File file = downloadFromApkMirrorSearch(pkg, version, fileType, abis);
            markSource(SourceSettings.APKMIRROR, true, getString(R.string.source_detail_search));
            return file;
        } catch (UptodownHandoffException handoff) {
            throw handoff;
        } catch (Exception failed) {
            HelperLog.error("APKMirror failed", failed);
            markSource(SourceSettings.APKMIRROR, false, failed.getMessage());
            return null;
        }
    }

    /**
     * Uptodown API fast path. The mobile eAPI returns exact file IDs and a
     * signed CDN URL with a SHA-256, so this avoids the Turnstile page. The
     * existing WebView flow is kept as a fallback.
     */
    private File customUptodown(String pkg, String version, String fileType)
            throws IOException {
        setSourceLabel(R.string.source_label_uptodown);
        setStatus(R.string.download_resolving_uptodown);

        UptodownApi.Prepared prepared = takeUptodownPrefetch();
        if (prepared != null) {
            try {
                return finishUptodownDownload(prepared, pkg, version);
            } catch (Exception error) {
                HelperLog.error("Uptodown prefetched asset failed", error);
                handoffToUptodownWeb(
                        uptodownWebUrl(prepared.app, prepared.release),
                        pkg, version, fileType);
            }
        }

        UptodownApi.App app = null;
        UptodownApi.Release release = null;
        try {
            app = UptodownApi.resolve(pkg);
            release = UptodownApi.findRelease(app, version, fileType);
            if (release == null) {
                throw new IOException("No matching Uptodown release");
            }
            UptodownApi.Download download = UptodownApi.resolveDownload(
                    app, release.fileId);
            return finishUptodownDownload(
                    new UptodownApi.Prepared(app, release, download),
                    pkg, version);
        } catch (UptodownHandoffException handoff) {
            throw handoff;
        } catch (Exception error) {
            HelperLog.error("Uptodown API failed", error);
            if (app != null) {
                handoffToUptodownWeb(
                        uptodownWebUrl(app, release), pkg, version, fileType);
            }
            markSource(SourceSettings.UPTODOWN, false, error.getMessage());
            return null;
        }
    }

    private File finishUptodownDownload(UptodownApi.Prepared prepared,
                                        String pkg, String version) throws IOException {
        cacheUptodownIcon(prepared.app, pkg);
        HelperLog.info("Uptodown API file version=" + prepared.release.version
                + " kind=" + prepared.release.kindFile
                + " id=" + prepared.release.fileId);
        File file = downloadFile(prepared.download.url, null, pkg, version,
                prepared.release.kindFile, null);
        if (!TextUtils.isEmpty(prepared.download.sha256)) {
            if (!UptodownApi.verifySha256(file, prepared.download.sha256)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                throw new IOException("Uptodown SHA-256 mismatch");
            }
            if (report != null) {
                report.setSha256(prepared.download.sha256);
            }
        }
        markSource(SourceSettings.UPTODOWN, true, "API");
        return file;
    }

    private String uptodownWebUrl(UptodownApi.App app, UptodownApi.Release release) {
        String page = app.slug.endsWith("/") ? app.slug : app.slug + "/";
        return page + (release == null
                ? "download" : "download/" + release.fileId);
    }

    private void handoffToUptodownWeb(String url, String pkg, String version,
                                      String fileType) throws UptodownHandoffException {
        main.post(() -> {
            setStatus(R.string.download_opening_uptodown);
            progress.setIndeterminate(true);
            startUptodownWebFlow(url, pkg, version, fileType);
        });
        throw new UptodownHandoffException();
    }

    private void cacheUptodownIcon(UptodownApi.App app, String pkg) {
        if (app == null || TextUtils.isEmpty(app.icon)) {
            return;
        }
        String key = AppIconCache.key(pkg, requestVersion);
        if (AppIconCache.load(this, key) != null) {
            return;
        }
        new Thread(() -> {
            AppIconCache.saveFromUrl(getApplicationContext(), key, app.icon);
            main.post(this::updateDownloadDetails);
        }, "uptodown-icon").start();
    }

    private File customFdroid(String pkg, String version, long[] codes, String fileType) {        setSourceLabel(R.string.source_label_fdroid);
        setStatus(R.string.download_checking_fdroid);
        try {
            File file = downloadFromFdroid(pkg, version, codes, fileType);
            markSource(SourceSettings.FDROID, true, getString(R.string.source_detail_package_api));
            return file;
        } catch (Exception failed) {
            HelperLog.error("F-Droid failed", failed);
            markSource(SourceSettings.FDROID, false, failed.getMessage());
            return null;
        }
    }

    private File customAptoide(String pkg, String version, long[] codes, String fileType) {
        setSourceLabel(R.string.source_label_aptoide);
        setStatus(R.string.download_searching_aptoide);
        try {
            File file = downloadFromAptoide(pkg, version, codes, fileType);
            markSource(SourceSettings.APTOIDE, true, getString(R.string.source_detail_trusted_upload));
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
        setSourceLabel(R.string.source_label_appteka);
        setStatus(R.string.download_searching_appteka);
        try {
            File file = downloadFromAppteka(pkg, version, codes);
            markSource(SourceSettings.APPTEKA, true, getString(R.string.source_detail_catalog));
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
                || matchesRequestedVersion(info.optString("ver_name"),
                info.optLong("ver_code", -1), version, versionCodes);

        JSONObject selected = null;
        JSONArray versions = result.optJSONArray("versions");
        if (versions != null) {
            for (int i = 0; i < versions.length(); i++) {
                JSONObject candidate = versions.optJSONObject(i);
                if (candidate == null) {
                    continue;
                }
                if (!noVersionRequested && matchesRequestedVersion(
                        candidate.optString("ver_name"),
                        candidate.optLong("ver_code", -1), version, versionCodes)) {
                    selected = candidate;
                    break;
                }
            }
        }

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
            if (!infoMatches) {
                throw new IOException("Requested build not found on Appteka");
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

        // GitHub fallback URLs are deliberately deferred until after the normal APK
        // sources have been tried. They are remembered by resolve() instead.

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
            String firstVersion = ApkMirrorSearch.findBestVersionUrl(
                    httpGet(url, null), requestedAppName, pkg, version);
            if (firstVersion != null) {
                String appUrl = ApkMirrorSearch.appPath(firstVersion);
                if (appUrl.isEmpty()) {
                    throw new IOException("APKMirror app path not found");
                }
                String appHtml = httpGet(absolute(appUrl), url);
                cacheSourceIcon(appHtml, pkg);
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
        // APKMirror's advanced search endpoint
        // (?post_type=app_release&searchtype=apk) is now behind a Cloudflare
        // challenge for plain HTTP clients. The WordPress ?s= search still
        // serves normal HTML for many apps and contains the same release
        // links, so try it first and use the WebView only when Cloudflare
        // challenges this endpoint too.
        String cachedApp = AppSettings.apkMirrorAppUrl(this, pkg);
        if (!TextUtils.isEmpty(cachedApp)) {
            HelperLog.info("APKMirror cached app URL: " + cachedApp);
            try {
                return downloadFromApkMirrorAppPage(cachedApp, pkg, version,
                        fileType, abis, null);
            } catch (UptodownHandoffException handoff) {
                throw handoff;
            } catch (IOException error) {
                HelperLog.info("APKMirror cached app URL failed: " + error.getMessage());
                if (isApkMirrorWebFallback(error)) {
                    handoffToApkMirrorWeb(cachedApp, pkg, version, fileType);
                }
                AppSettings.clearApkMirrorAppUrl(this, pkg);
            }
        }

        String searchUrl = APKMIRROR + "/?s=" + URLEncoder.encode(pkg, "UTF-8");
        HelperLog.info("APKMirror search URL: " + searchUrl);
        String searchHtml;
        try {
            searchHtml = httpGet(searchUrl, null);
        } catch (IOException error) {
            if (isApkMirrorWebFallback(error)) {
                handoffToApkMirrorWeb(searchUrl, pkg, version, fileType);
            }
            throw error;
        }
        String firstVersion = ApkMirrorSearch.findBestVersionUrl(
                searchHtml, requestedAppName, pkg, version);
        HelperLog.info("APKMirror first version URL: " + firstVersion);
        if (firstVersion == null) {
            throw new IOException("No matching app on APKMirror");
        }
        if (AppSettings.fastSourceChecks(this)
                && !ApkMirrorSearch.hasPlausibleMatch(
                        searchHtml, requestedAppName, pkg, version)) {
            HelperLog.info("APKMirror search has no plausible match; skipping app page");
            throw new IOException("No matching app on APKMirror");
        }

        // A release URL looks like /apk/<dev>/<app>/<version>-release/. Strip the
        // version segment to get the app page.
        String appUrl = ApkMirrorSearch.appPath(firstVersion);
        if (appUrl.isEmpty()) {
            throw new IOException("APKMirror app path not found");
        }
        String absoluteApp = absolute(appUrl);
        HelperLog.info("APKMirror app URL: " + absoluteApp);
        return downloadFromApkMirrorAppPage(absoluteApp, pkg, version,
                fileType, abis, searchUrl);
    }

    /**
     * Opens an APKMirror app page, selects the requested version, and picks a
     * compatible variant. The page's Play Store link is checked against the
     * requested package so a bad search result cannot download another app.
     */
    private File downloadFromApkMirrorAppPage(String appUrl, String pkg, String version,
                                              String fileType, String[] abis,
                                              String referer) throws IOException {
        String appPath = ApkMirrorSearch.appPath(appUrl);
        if (appPath.isEmpty()) {
            throw new IOException("APKMirror app path not found");
        }
        String appHtml;
        try {
            appHtml = httpGet(absolute(appUrl), referer);
        } catch (IOException error) {
            if (isApkMirrorWebFallback(error)) {
                handoffToApkMirrorWeb(absolute(appUrl), pkg, version, fileType);
            }
            throw error;
        }
        if (!ApkMirrorSearch.pageMatchesPackage(appHtml, pkg)) {
            String found = ApkMirrorSearch.playPackage(appHtml);
            throw new IOException("APKMirror app page is " + found
                    + ", expected " + pkg);
        }
        AppSettings.setApkMirrorAppUrl(this, pkg, absolute(appUrl));
        cacheSourceIcon(appHtml, pkg);
        HelperLog.info("APKMirror app HTML length=" + appHtml.length()
                + " fontBlack=" + appHtml.contains("fontBlack")
                + " release=" + appHtml.contains("-release/"));

        // Prefer a listed version; if it is missing, try constructing the exact slug.
        String versionUrl = findVersionUrl(appHtml, appPath, version);
        if (versionUrl == null && !TextUtils.isEmpty(version)) {
            versionUrl = findExactVersionUrl(appPath, appHtml, version, absolute(appUrl));
        }
        HelperLog.info("APKMirror version URL: " + versionUrl);
        if (versionUrl == null) {
            throw new IOException(TextUtils.isEmpty(version)
                    ? "No matching version on APKMirror"
                    : "Requested version " + version + " not found on APKMirror");
        }

        // The version page lists APK/APKM/APKS variants; choose the best one.
        String versionHtml;
        try {
            versionHtml = httpGet(absolute(versionUrl), absolute(appUrl));
        } catch (IOException error) {
            if (isApkMirrorWebFallback(error)) {
                handoffToApkMirrorWeb(absolute(versionUrl), pkg, version, fileType);
            }
            throw error;
        }
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
        setStatus(R.string.download_checking_apkmirror_page);
        String html;
        try {
            html = httpGet(variantUrl, null);
        } catch (IOException error) {
            if (isApkMirrorWebFallback(error)) {
                handoffToApkMirrorWeb(variantUrl, pkg, version, fileType);
            }
            throw error;
        }
        if (!ApkMirrorSearch.pageMatchesPackage(html, pkg)) {
            String found = ApkMirrorSearch.playPackage(html);
            throw new IOException("APKMirror variant page is " + found
                    + ", expected " + pkg);
        }

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
        setStatus(R.string.download_fetching_key);
        String html;
        try {
            html = httpGet(keyUrl, null);
        } catch (IOException error) {
            if (isApkMirrorWebFallback(error)) {
                handoffToApkMirrorWeb(keyUrl, pkg, version, fileType);
            }
            throw error;
        }

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
        setStatus(R.string.download_starting_apkmirror);
        return downloadFile(forceBaseApkUrl(absolute(decodeHtml(fileUrl)), fileType),
                keyUrl, pkg, version, fileType);
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

            boolean noVersionRequested = TextUtils.isEmpty(version)
                    && (versionCodes == null || versionCodes.length == 0);
            JSONObject selected = null;
            for (int i = 0; i < packages.length(); i++) {
                JSONObject entry = packages.getJSONObject(i);
                String entryVersion = entry.optString("versionName", "");
                long entryCode = entry.optLong("versionCode", -1);
                if (!noVersionRequested && matchesRequestedVersion(
                        entryVersion, entryCode, version, versionCodes)) {
                    selected = entry;
                    break;
                }
            }

            if (selected == null && TextUtils.isEmpty(version)
                    && (versionCodes == null || versionCodes.length == 0)) {
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
        return matchesRequestedVersion(file.optString("vername", ""),
                file.optLong("vercode", -1), version, versionCodes);
    }

    /** Version codes are exact build numbers; the name is checked as well. */
    private boolean matchesRequestedVersion(String actualName, long actualCode,
                                            String requestedName, long[] requestedCodes) {
        if (requestedCodes != null && requestedCodes.length > 0
                && !contains(requestedCodes, actualCode)) {
            return false;
        }
        return TextUtils.isEmpty(requestedName) || TextUtils.isEmpty(actualName)
                || sameVersion(actualName, requestedName);
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
        ApkPureAssets.Asset best = resolveApkPureApiAsset(
                pkg, version, versionCodes, fileType);
        return downloadApkPureAsset(best, pkg, version, "https://apkpure.com/");
    }

    /** Resolves APKPure's API metadata without starting the file download. */
    private ApkPureAssets.Asset resolveApkPureApiAsset(String pkg, String version,
                                                       long[] versionCodes,
                                                       String fileType) throws IOException {
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
        try (Response response = METADATA_HTTP.newCall(request).execute()) {
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

        boolean splitRequested = isSplitType(fileType);
        ApkPureAssets.Asset best = ApkPureAssets.pick(
                list, version, versionCodes, splitRequested);
        if (best == null) {
            throw new IOException(splitRequested
                    ? "No split archive found for this version on APKPure"
                    : "Requested version not found on APKPure");
        }
        if (best.split() && !allowSplitArchive && !splitRequested) {
            throw new IOException("APKPure only has a split archive for this version");
        }
        if (best.split() && githubResolverHint && !splitRequested) {
            throw new IOException("APKPure only has a split archive; "
                    + "keeping the GitHub release for this patch source");
        }

        HelperLog.info("APKPure API asset: " + best.versionName
                + " code=" + best.versionCode
                + " type=" + best.type + " size=" + best.size);
        return best;
    }

    /** Downloads one resolved APKPure asset with the correct referer/type. */
    private File downloadApkPureAsset(ApkPureAssets.Asset asset, String pkg,
                                      String version, String referer) throws IOException {
        String downloadType = "XAPK".equalsIgnoreCase(asset.type) ? "xapk"
                : ("APKS".equalsIgnoreCase(asset.type) ? "apks" : "apk");
        return downloadFile(asset.url, referer, pkg, version, downloadType);
    }

    private boolean sameVersion(String left, String right) {
        if (TextUtils.isEmpty(left) || TextUtils.isEmpty(right)) {
            return false;
        }
        return normalizeVersion(left).equalsIgnoreCase(normalizeVersion(right));
    }

    /**
     * Patch bundles often write versions as {@code v1.0.14}, while the APK
     * manifest reports {@code 1.0.14}. Treat both forms as the same version.
     */
    private String normalizeVersion(String value) {
        String result = value.trim().replace(" ", "");
        while (result.startsWith("v") || result.startsWith("V")) {
            result = result.substring(1);
        }
        return result;
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
            if (first instanceof DownloadedFileValidator.VersionMismatchException) {
                throw first;
            }
            if (isSplitType(fileType)) {
                // A split request must never fall back to the standalone
                // endpoint: that file is only base.apk and cannot be installed.
                throw first;
            }
            if (!allowSplitArchive || (githubResolverHint && !isSplitType(fileType))) {
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
     *   <li>An exact requested build number first, preferring the first supported
     *       ABI.</li>
     *   <li>APK request after that: universal APK, an APK matching the ABI, then
     *       any APK.</li>
     *   <li>Bundle request after that: bundle matching the ABI, any bundle, an
     *       APK matching the ABI, then any APK.</li>
     * </ul>
     *
     * <p>When a build number is requested and the page exposes build numbers but
     * none matches, this returns {@code null} instead of downloading a wrong
     * build and only rejecting it later.</p>
     */
    private String chooseVariant(String versionHtml, String fileType, String[] abis) {
        // Each variant is a table row; split on the row wrapper to inspect one at a time.
        String[] rows = versionHtml.split("<div class=\"table-row");
        String fallback = null;
        String plainApk = null;
        String plainApkAbi = null;
        String plainApkExact = null;
        String plainApkExactAbi = null;
        String plainApkUniversal = null;
        String bundle = null;
        String bundleAbi = null;
        String bundleExact = null;
        String bundleExactAbi = null;
        boolean sawVersionCode = false;
        String preferredAbi = (abis != null && abis.length > 0) ? abis[0] : null;
        long[] requestedCodes = requestVersionCodes;
        boolean wantsExactBuild = requestedCodes != null && requestedCodes.length > 0;

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
            // "nodpi" is the density cell shown on every APK row, so it does not
            // mean the file is a universal APK. Only the explicit label does.
            boolean universal = row.contains("universal");
            boolean hasAbi = preferredAbi != null && row.contains(preferredAbi);
            long rowCode = variantCode(row);
            if (rowCode > 0) {
                sawVersionCode = true;
            }
            boolean exactCode = rowCode > 0 && contains(requestedCodes, rowCode);

            if (isPlainApk && exactCode && hasAbi && plainApkExactAbi == null) {
                plainApkExactAbi = href;
            }
            if (isPlainApk && exactCode && plainApkExact == null) {
                plainApkExact = href;
            }
            if (isPlainApk && universal && plainApkUniversal == null) plainApkUniversal = href;
            if (isPlainApk && hasAbi && plainApkAbi == null) plainApkAbi = href;
            if (isPlainApk && plainApk == null) plainApk = href;
            if (isBundle && exactCode && hasAbi && bundleExactAbi == null) {
                bundleExactAbi = href;
            }
            if (isBundle && exactCode && bundleExact == null) {
                bundleExact = href;
            }
            if (isBundle && hasAbi && bundleAbi == null) bundleAbi = href;
            if (isBundle && bundle == null) bundle = href;
        }

        boolean exactPlain = plainApkExactAbi != null || plainApkExact != null;
        boolean exactBundle = bundleExactAbi != null || bundleExact != null;
        if ("apk".equalsIgnoreCase(fileType) || TextUtils.isEmpty(fileType)) {
            if (plainApkExactAbi != null) return plainApkExactAbi;
            if (plainApkExact != null) return plainApkExact;
            // Split-bundle rows still expose an APK variant, and its key page
            // can serve the base APK. Prefer the exact build over a plain APK
            // with the wrong build number.
            if (bundleExactAbi != null) return bundleExactAbi;
            if (bundleExact != null) return bundleExact;
            if (wantsExactBuild && sawVersionCode && !exactPlain && !exactBundle) return null;
            if (plainApkUniversal != null) return plainApkUniversal;
            // APKMirror publishes one row per CPU variant. Picking the first plain
            // APK can download a different ABI with a different versionCode, which
            // then fails validation after the whole file has been transferred.
            if (plainApkAbi != null) return plainApkAbi;
            if (plainApk != null) return plainApk;
        } else {
            if (bundleExactAbi != null) return bundleExactAbi;
            if (bundleExact != null) return bundleExact;
            if (plainApkExactAbi != null) return plainApkExactAbi;
            if (plainApkExact != null) return plainApkExact;
            if (wantsExactBuild && sawVersionCode && !exactBundle) return null;
            if (bundleAbi != null) return bundleAbi;
            if (bundle != null) return bundle;
            if (plainApkAbi != null) return plainApkAbi;
            if (plainApk != null) return plainApk;
        }
        return fallback;
    }

    /** Reads the numeric build number shown in an APKMirror variant row. */
    private long variantCode(String row) {
        Matcher matcher = Pattern.compile(
                "class=\"colorLightBlack\">\\s*(\\d+)\\s*</span>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(row);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
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
    private String httpGetPreflight(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build();
        HelperLog.info("PREFLIGHT GET " + url);
        try (Response response = PREFLIGHT_HTTP.newCall(request).execute()) {
            HelperLog.info("PREFLIGHT GET " + url + " -> HTTP " + response.code()
                    + " type=" + response.header("Content-Type")
                    + " length=" + response.header("Content-Length"));
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " for " + url);
            }
            ResponseBody body = response.body();
            return body == null ? "" : body.string();
        }
    }

    private String httpGet(String url, String referer) throws IOException {
        if (referer == null) {
            Future<String> prefetched = sourcePrefetches.remove(url);
            if (prefetched != null) {
                if (prefetched.isDone()) {
                    try {
                        HelperLog.info("GET " + url + " (prefetched)");
                        return prefetched.get();
                    } catch (Exception error) {
                        HelperLog.info("Source prefetch failed for " + url
                                + ": " + error.getMessage());
                    }
                } else {
                    // Do not make this source wait for a prefetch. A normal
                    // request is faster than blocking on an uncertain task.
                    prefetched.cancel(true);
                }
            }
        }
        return httpGetNetwork(url, referer);
    }

    private String httpGetNetwork(String url, String referer) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9");
        if (referer != null) {
            builder.header("Referer", referer);
        }

        boolean apkmirror = isHost(hostOf(url), "apkmirror.com");
        OkHttpClient client = apkmirror ? APKMIRROR_HTTP : METADATA_HTTP;
        int attempts = apkmirror ? 2 : 1;
        IOException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            HelperLog.info("GET " + url + (referer != null ? " (referer=" + referer + ")" : "")
                    + (attempt > 1 ? " attempt=" + attempt : ""));
            try (Response response = client.newCall(builder.build()).execute()) {
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
            } catch (IOException error) {
                HelperLog.info("GET " + url + " failed using "
                        + (apkmirror ? "APKMirror" : "metadata")
                        + " client: " + error.getMessage());
                throw error;
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
     * Records device, network, and storage information for download diagnostics.
     */
    private void logDownloadContext(File dir) {
        String freeMb = "?";
        try {
            StatFs stat = new StatFs(dir.getAbsolutePath());
            freeMb = String.valueOf(stat.getAvailableBytes() / (1024L * 1024L));
        } catch (Exception error) {
            HelperLog.error("Could not read free storage", error);
        }
        HelperLog.info("Download context: " + Build.MANUFACTURER + " " + Build.MODEL
                + ", Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")"
                + ", abis=" + TextUtils.join(",", Build.SUPPORTED_ABIS)
                + ", free=" + freeMb + " MB");
        try {
            ConnectivityManager manager =
                    (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network network = manager == null ? null : manager.getActiveNetwork();
            NetworkCapabilities caps = network == null
                    ? null : manager.getNetworkCapabilities(network);
            if (caps != null) {
                String transport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        ? "wifi"
                        : caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                                ? "cellular"
                                : caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                                        ? "ethernet"
                                        : caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                                                ? "vpn" : "other";
                HelperLog.info("Download network: " + transport
                        + ", validated=" + caps.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        + ", metered=" + !caps.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                        + ", downKbps=" + caps.getLinkDownstreamBandwidthKbps()
                        + ", upKbps=" + caps.getLinkUpstreamBandwidthKbps());
            }
        } catch (Exception error) {
            HelperLog.error("Could not read network capabilities", error);
        }
    }

    /**
     * Logs DNS/connect/TLS/header/body timings and the final protocol for one download.
     */
    private static final class DownloadTimingListener extends EventListener {
        private long callStart;
        private long dnsStart;
        private long dnsEnd;
        private long connectStart;
        private long connectEnd;
        private long secureStart;
        private long secureEnd;
        private long requestStart;
        private long requestEnd;
        private long responseStart;
        private long responseEnd;
        private long bodyStart;
        private long bodyEnd;
        private long bytesRead;
        private String finalUrl;
        private String protocol;
        private String connectionProtocol;
        private String tlsVersion;
        private String cipher;
        private String route;
        private boolean logged;

        private static long now() {
            return System.nanoTime() / 1_000_000L;
        }

        private static String duration(long start, long end) {
            return start <= 0 || end <= 0 ? "n/a" : String.valueOf(end - start);
        }

        @Override
        public void callStart(Call call) {
            callStart = now();
            finalUrl = call.request().url().toString();
        }

        @Override
        public void dnsStart(Call call, String domainName) {
            dnsStart = now();
        }

        @Override
        public void dnsEnd(Call call, String domainName, List<InetAddress> addresses) {
            dnsEnd = now();
        }

        @Override
        public void connectStart(Call call, InetSocketAddress address, Proxy proxy) {
            connectStart = now();
        }

        @Override
        public void connectEnd(Call call, InetSocketAddress address, Proxy proxy,
                               Protocol protocol) {
            connectEnd = now();
        }

        @Override
        public void secureConnectStart(Call call) {
            secureStart = now();
        }

        @Override
        public void secureConnectEnd(Call call, Handshake handshake) {
            secureEnd = now();
            if (handshake != null) {
                tlsVersion = handshake.tlsVersion().javaName();
                cipher = handshake.cipherSuite().javaName();
            }
        }

        @Override
        public void requestHeadersStart(Call call) {
            requestStart = now();
        }

        @Override
        public void requestHeadersEnd(Call call, Request request) {
            requestEnd = now();
        }

        @Override
        public void responseHeadersStart(Call call) {
            responseStart = now();
        }

        @Override
        public void responseHeadersEnd(Call call, Response response) {
            responseEnd = now();
            protocol = response.protocol().toString();
            finalUrl = response.request().url().toString();
        }

        @Override
        public void responseBodyStart(Call call) {
            bodyStart = now();
        }

        @Override
        public void responseBodyEnd(Call call, long byteCount) {
            bodyEnd = now();
            bytesRead = byteCount;
        }

        @Override
        public void connectionAcquired(Call call, Connection connection) {
            connectionProtocol = connection.protocol().toString();
            try {
                route = connection.route().socketAddress().toString();
            } catch (Exception ignored) {
            }
        }

        @Override
        public void callEnd(Call call) {
            log("ok");
        }

        @Override
        public void callFailed(Call call, IOException error) {
            HelperLog.error("Download call failed", error);
            log("failed");
        }

        private void log(String outcome) {
            if (logged) {
                return;
            }
            logged = true;
            long end = bodyEnd > 0 ? bodyEnd : now();
            long bodyMs = bodyEnd > 0 && bodyStart > 0 ? bodyEnd - bodyStart : 0;
            double mibPerSecond = bodyMs > 0
                    ? bytesRead / (bodyMs / 1000.0) / (1024.0 * 1024.0) : 0;
            HelperLog.info("Download timing (" + outcome + "):"
                    + " dns=" + duration(dnsStart, dnsEnd)
                    + " connect=" + duration(connectStart, connectEnd)
                    + " tls=" + duration(secureStart, secureEnd)
                    + " request=" + duration(requestStart, requestEnd)
                    + " headers=" + duration(responseStart, responseEnd)
                    + " body=" + duration(bodyStart, bodyEnd)
                    + " total=" + duration(callStart, end)
                    + " ms, bytes=" + bytesRead
                    + ", bodySpeed=" + String.format(Locale.US, "%.2f", mibPerSecond)
                    + " MiB/s, protocol=" + protocol
                    + ", connection=" + connectionProtocol
                    + ", tlsVersion=" + tlsVersion
                    + ", cipher=" + cipher
                    + ", route=" + route
                    + ", url=" + finalUrl);
        }
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
        setDownloadInfo(-1, 1);
        if (AppSettings.multiThreadDownloads(this)) {
            File multi = segmentedDownload(url, referer, pkg, versionName,
                    fileType, cookie, dir, partBase);
            if (multi != null) {
                return multi;
            }
            HelperLog.info("Multi-connection download unavailable; using single stream");
            setDownloadInfo(-1, 1);
        }
        downloadIfRange = null;
        IOException last = null;
        boolean zeroRetryUsed = false;
        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            boolean willResume = part.length() > 0;
            try {
                return downloadSingleStreamAttempt(url, referer, pkg, versionName,
                        fileType, cookie, dir, part);
            } catch (IOException error) {
                last = error;
                if (cancelled || switchingSource || forcedSource != null
                        || pendingSwitchKey != null) {
                    throw error;
                }
                if (attempt >= MAX_DOWNLOAD_ATTEMPTS
                        || !isRetryableDownloadError(error)) {
                    throw error;
                }
                if (shouldRestartFromZero(willResume, zeroRetryUsed, part.length())) {
                    // The CDN answered the first request but then stopped
                    // responding to the resumed Range request. Starting over
                    // is slower, but it avoids retrying a broken resume path
                    // until the source is marked as failed.
                    if (part.delete()) {
                        zeroRetryUsed = true;
                        downloadIfRange = null;
                        HelperLog.info("Resume attempt failed; restarting from 0 bytes");
                    }
                }
                HelperLog.info("Download interrupted; retrying from "
                        + part.length() + " bytes (attempt " + (attempt + 1)
                        + "/" + MAX_DOWNLOAD_ATTEMPTS + "): " + error.getMessage());
                setStatus(getString(R.string.download_retry_resume,
                        attempt + 1, MAX_DOWNLOAD_ATTEMPTS));
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted", interrupted);
                }
            }
        }
        throw last == null ? new IOException("Download failed") : last;
    }

    private File downloadSingleStreamAttempt(String url, String referer, String pkg,
                                              String versionName, String fileType,
                                              String cookie, File dir, File part)
            throws IOException {
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
            if (downloadIfRange != null) {
                builder.header("If-Range", downloadIfRange);
            }
        }

        HelperLog.info("DOWNLOAD " + url
                + (referer != null ? " (referer=" + referer + ")" : ""));
        logDownloadContext(dir);
        DownloadTimingListener timing = new DownloadTimingListener();
        OkHttpClient.Builder downloadBuilder = DOWNLOAD_HTTP.newBuilder()
                .eventListener(timing);
        String downloadHost = hostOf(url);
        if (isHost(downloadHost, "uptodown.com")
                || isHost(downloadHost, "uptodown.net")) {
            // Some Uptodown CDN edges open an HTTP/2 stream but never send headers.
            // HTTP/1.1 avoids that stall and is also better for Range requests.
            downloadBuilder.protocols(Collections.singletonList(Protocol.HTTP_1_1));
        }
        OkHttpClient downloadClient = downloadBuilder.build();
        Call call = downloadClient.newCall(builder.build());
        activeCall = call;
        try (Response response = call.execute()) {
            String contentType = response.header("Content-Type");
            ResponseBody body = response.body();
            long bodyLength = body != null ? body.contentLength() : -1;
            long total = append && response.code() == 206 && bodyLength >= 0
                    ? existing + bodyLength : bodyLength;
            setDownloadInfo(total, 1);
            HelperLog.info("DOWNLOAD " + url + " -> HTTP " + response.code()
                    + " type=" + contentType
                    + " length=" + total);
            HelperLog.info("DOWNLOAD finalUrl=" + response.request().url()
                    + " protocol=" + response.protocol()
                    + " server=" + response.header("Server")
                    + " cfRay=" + response.header("CF-Ray")
                    + " acceptRanges=" + response.header("Accept-Ranges")
                    + " contentEncoding=" + response.header("Content-Encoding")
                    + " contentRange=" + response.header("Content-Range")
                    + " serverTimeMs=" + (response.receivedResponseAtMillis()
                            - response.sentRequestAtMillis()));
            Handshake handshake = response.handshake();
            if (handshake != null) {
                HelperLog.info("DOWNLOAD TLS=" + handshake.tlsVersion().javaName()
                        + " cipher=" + handshake.cipherSuite().javaName());
            }
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            if (contentType != null && contentType.toLowerCase(Locale.US).contains("text/html")) {
                throw new IOException("Server returned HTML instead of an APK");
            }
            if (body == null) {
                throw new IOException("Empty response");
            }
            if (response.code() == 206
                    && !contentRangeStartsAt(response.header("Content-Range"), existing)) {
                // The partial file no longer matches the server's range; start clean.
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                throw new IOException("Unexpected Content-Range");
            }
            if (downloadIfRange == null) {
                downloadIfRange = response.header("ETag");
                if (downloadIfRange == null) {
                    downloadIfRange = response.header("Last-Modified");
                }
            }

            setPauseVisible(true);
            setStatus(R.string.download_starting);
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
            long bodyStart = System.currentTimeMillis();
            long readTotal = existing;
            try (InputStream in = new BufferedInputStream(body.byteStream());
                 BufferedOutputStream out = new BufferedOutputStream(
                         new FileOutputStream(part, resume))) {
                byte[] buffer = new byte[64 * 1024];
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
                        String text = left > 0
                                ? getString(R.string.download_progress_eta,
                                        p, speedText, left)
                                : getString(R.string.download_progress, p, speedText);
                        final int progressValue = p;
                        main.post(() -> {
                            progress.setProgress(progressValue);
                            setProgressStatus(speedText, left);
                        });
                        updateDownloadService(text, p, false);
                    }
                }
            }

            long bodyElapsed = Math.max(1, System.currentTimeMillis() - bodyStart);
            long bodyBytes = readTotal - existing;
            HelperLog.info("DOWNLOAD body complete: bytes=" + bodyBytes
                    + " elapsedMs=" + bodyElapsed
                    + " avg=" + String.format(Locale.US, "%.2f",
                    bodyBytes * 1000.0 / bodyElapsed / (1024.0 * 1024.0))
                    + " MiB/s");

            if (cancelled) {
                throw new IOException("Cancelled");
            }
            if (total > 0 && readTotal < total) {
                throw new IOException("Incomplete download: "
                        + readTotal + "/" + total + " bytes");
            }
            return finalizeDownloadedFile(part, dir, filename, pkg, versionName);
        } catch (IOException error) {
            HelperLog.info("DOWNLOAD attempt failed (download client connect=15s read=20s): "
                    + error.getMessage());
            throw error;
        } finally {
            activeCall = null;
        }
    }

    /**
     * When a resumed Range request fails, a clean request can succeed even
     * though the server's resume path is broken. Allow one clean retry.
     */
    static boolean shouldRestartFromZero(boolean willResume, boolean zeroRetryUsed,
                                         long partLength) {
        return willResume && !zeroRetryUsed && partLength > 0;
    }

    /** True for transient network/read failures that are safe to retry. */
    static boolean isRetryableDownloadError(IOException error) {
        if (error instanceof java.net.SocketTimeoutException
                || error instanceof java.net.SocketException
                || error instanceof java.net.UnknownHostException
                || error instanceof java.io.EOFException) {
            return true;
        }
        String message = error.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.US);
        if (lower.contains("incomplete download")
                || lower.contains("unexpected end")
                || lower.contains("connection reset")
                || lower.contains("connection timed out")
                || lower.contains("timeout")
                || lower.contains("broken pipe")
                || lower.contains("stream was reset")) {
            return true;
        }
        if (lower.startsWith("http ")) {
            String codeText = lower.substring(5).trim();
            try {
                int code = Integer.parseInt(codeText.split(" ", 2)[0]);
                return code == 408 || code == 429 || code >= 500;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    /** True when a Content-Range header starts at the expected offset. */
    static boolean contentRangeStartsAt(String value, long expectedStart) {
        if (value == null || !value.startsWith("bytes ")) {
            return false;
        }
        int dash = value.indexOf('-', 6);
        if (dash < 0) {
            return false;
        }
        try {
            return Long.parseLong(value.substring(6, dash).trim()) == expectedStart;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static final long MULTI_MIN_BYTES = 5L * 1024L * 1024L;
    private static final long MIN_SEGMENT_BYTES = 4L * 1024L * 1024L;
    private static final long SEGMENT_CHUNK_BYTES = 4L * 1024L * 1024L;
    private static final int MAX_SEGMENT_CONNECTIONS = 32;

    /**
     * Caps the worker count by the configured maximum, the file size, and the
     * 4 MiB minimum segment size. Returns 0 when the file is too small.
     */
    static int segmentedConnectionCount(long total, int configuredMax) {
        if (total < MULTI_MIN_BYTES) {
            return 0;
        }
        int max = Math.max(2, Math.min(MAX_SEGMENT_CONNECTIONS, configuredMax));
        long byMinSegment = Math.max(1, total / MIN_SEGMENT_BYTES);
        long byChunk = Math.max(1, total / SEGMENT_CHUNK_BYTES);
        int count = (int) Math.min(max, Math.min(byMinSegment, byChunk));
        return Math.max(2, count);
    }
    /** Number of in-attempt retries for a dropped single-stream download. */
    private static final int MAX_DOWNLOAD_ATTEMPTS = 3;

    /**
     * Attempts a segmented HTTP Range download. Returns null when the server or
     * network does not support it, so the caller can use the normal single stream.
     */
    private File segmentedDownload(String url, String referer, String pkg,
                                   String versionName, String fileType, String cookie,
                                   File dir, String partBase) throws IOException {
        // APKMirror's download endpoint rejects the range probe (HTTP 403)
        // even when its final CDN object supports ranges. Keep the proven
        // single-stream path for this host.
        if (isHost(hostOf(url), "apkmirror.com")) {
            HelperLog.info("Multi download skipped: APKMirror");
            return null;
        }
        OkHttpClient.Builder clientBuilder = DOWNLOAD_HTTP.newBuilder();
        // Each segment needs its own TCP connection. HTTP/2 multiplexes all
        // segments over one connection, which removes the parallel benefit.
        clientBuilder.protocols(Collections.singletonList(Protocol.HTTP_1_1));
        OkHttpClient client = clientBuilder.build();
        Request.Builder base = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "identity");
        if (referer != null) {
            base.header("Referer", referer);
        }
        if (cookie != null && !cookie.isEmpty()) {
            base.header("Cookie", cookie);
        }

        long total;
        String filename;
        String segmentUrl;
        try (Response response = client.newCall(base.build().newBuilder()
                .header("Range", "bytes=0-0")
                .build()).execute()) {
            if (response.code() != 206) {
                HelperLog.info("Multi download skipped: probe HTTP " + response.code());
                return null;
            }
            String encoding = response.header("Content-Encoding");
            if (encoding != null && !"identity".equalsIgnoreCase(encoding)) {
                HelperLog.info("Multi download skipped: encoding " + encoding);
                return null;
            }
            total = contentRangeTotal(response.header("Content-Range"));
            if (total < MULTI_MIN_BYTES) {
                HelperLog.info("Multi download skipped: total " + total);
                return null;
            }
            ResponseBody body = response.body();
            if (body == null) {
                return null;
            }
            String contentType = response.header("Content-Type");
            if (contentType != null
                    && contentType.toLowerCase(Locale.US).contains("text/html")) {
                return null;
            }
            segmentUrl = response.request().url().toString();
            filename = filenameFrom(response.header("Content-Disposition"),
                    segmentUrl, pkg, versionName, fileType, contentType);
        }

        int segments = segmentedConnectionCount(total, AppSettings.maxConnections(this));
        long chunks = (total + SEGMENT_CHUNK_BYTES - 1) / SEGMENT_CHUNK_BYTES;
        File multiPart = new File(dir, partBase + ".seg.part");
        //noinspection ResultOfMethodCallIgnored
        multiPart.delete();
        try (RandomAccessFile preallocate = new RandomAccessFile(multiPart, "rw")) {
            preallocate.setLength(total);
        }

        HelperLog.info("MULTI download " + url + " total=" + total
                + " connections=" + segments + " chunks=" + chunks
                + " chunkMiB=" + (SEGMENT_CHUNK_BYTES / (1024 * 1024))
                + " finalUrl=" + segmentUrl);
        setDownloadInfo(total, segments);
        main.post(() -> {
            setPauseVisible(true);
            progress.setVisibility(View.VISIBLE);
            progress.setIndeterminate(false);
            progress.setProgress(0);
            setStatus(R.string.download_starting);
        });

        AtomicLong downloaded = new AtomicLong();
        ExecutorService pool = Executors.newFixedThreadPool(segments);
        List<Future<?>> futures = new ArrayList<>();
        Request segmentBase = new Request.Builder()
                .url(segmentUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "identity")
                .build();
        if (referer != null) {
            segmentBase = segmentBase.newBuilder().header("Referer", referer).build();
        }
        if (cookie != null && !cookie.isEmpty()) {
            segmentBase = segmentBase.newBuilder().header("Cookie", cookie).build();
        }
        final Request workerBase = segmentBase;
        AtomicLong nextChunk = new AtomicLong();
        for (int i = 0; i < segments; i++) {
            futures.add(pool.submit(() -> {
                while (!cancelled && !switchingSource) {
                    long index = nextChunk.getAndIncrement();
                    long start = index * SEGMENT_CHUNK_BYTES;
                    if (start >= total) {
                        return null;
                    }
                    long end = Math.min(start + SEGMENT_CHUNK_BYTES, total) - 1;
                    downloadSegment(client, workerBase, multiPart, start, end,
                            total, downloaded);
                }
                return null;
            }));
        }

        long startedAt = System.currentTimeMillis();
        AtomicBoolean reporterRunning = new AtomicBoolean(true);
        Thread reporter = new Thread(() -> {
            long lastBytes = 0;
            long lastTime = startedAt;
            while (reporterRunning.get()) {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException ignored) {
                    return;
                }
                long bytes = downloaded.get();
                long now = System.currentTimeMillis();
                if (paused) {
                    main.post(() -> setStatus(R.string.download_paused));
                    lastBytes = bytes;
                    lastTime = now;
                    continue;
                }
                long elapsed = Math.max(1, now - lastTime);
                double speed = Math.max(0, bytes - lastBytes) * 1000.0 / elapsed;
                int percent = total > 0
                        ? (int) Math.min(100, bytes * 100 / total) : 0;
                String speedText = String.format(Locale.US, "%.1f MB/s",
                        speed / (1024 * 1024));
                long left = speed > 0 ? (long) ((total - bytes) / speed) : -1;
                String text = left > 0
                        ? getString(R.string.download_progress_eta,
                                percent, speedText, left)
                        : getString(R.string.download_progress, percent, speedText);
                final int progressValue = percent;
                main.post(() -> {
                    progress.setProgress(progressValue);
                    setProgressStatus(speedText, left);
                });
                updateDownloadService(text, percent, false);
                lastBytes = bytes;
                lastTime = now;
            }
        }, "segment-progress");
        reporter.start();

        boolean failed = false;
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception error) {
                failed = true;
                cancelSegmentedTasks(pool, futures);
                break;
            }
        }
        reporterRunning.set(false);
        reporter.interrupt();
        pool.shutdownNow();

        if (cancelled) {
            //noinspection ResultOfMethodCallIgnored
            multiPart.delete();
            throw new IOException("Cancelled");
        }
        if (switchingSource || forcedSource != null) {
            //noinspection ResultOfMethodCallIgnored
            multiPart.delete();
            throw new IOException("Switched source");
        }
        if (failed || multiPart.length() != total) {
            HelperLog.error("Multi download failed; falling back to single stream", null);
            //noinspection ResultOfMethodCallIgnored
            multiPart.delete();
            return null;
        }
        long elapsed = System.currentTimeMillis() - startedAt;
        HelperLog.info("MULTI complete bytes=" + total
                + " connections=" + segments + " chunks=" + chunks
                + " elapsedMs=" + elapsed
                + " avg=" + String.format(Locale.US, "%.2f",
                total * 1000.0 / Math.max(1, elapsed) / (1024.0 * 1024.0))
                + " MiB/s");
        main.post(() -> progress.setProgress(100));
        return finalizeDownloadedFile(multiPart, dir, filename, pkg, versionName);
    }

    private void cancelSegmentedTasks(ExecutorService pool, List<Future<?>> futures) {
        for (Future<?> future : futures) {
            future.cancel(true);
        }
        pool.shutdownNow();
    }

    private void downloadSegment(OkHttpClient client, Request base, File part,
                                 long start, long end, long total,
                                 AtomicLong downloaded) throws IOException {
        long expected = end - start + 1;
        long segmentStart = start;
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (switchingSource || forcedSource != null) {
                throw new IOException("Switched source");
            }
            if (segmentStart > end) {
                return;
            }
            long written = 0;
            waitForResume();
            Call call = client.newCall(base.newBuilder()
                    .header("Range", "bytes=" + segmentStart + "-" + end)
                    .build());
            activeCalls.add(call);
            try (Response response = call.execute()) {
                if (response.code() != 206) {
                    throw new IOException("HTTP " + response.code());
                }
                String encoding = response.header("Content-Encoding");
                if (encoding != null && !"identity".equalsIgnoreCase(encoding)) {
                    throw new IOException("Unexpected Content-Encoding " + encoding);
                }
                if (!rangeMatches(response.header("Content-Range"),
                        segmentStart, end, total)) {
                    throw new IOException("Unexpected Content-Range");
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Empty segment response");
                }
                try (RandomAccessFile output = new RandomAccessFile(part, "rw");
                     InputStream input = new BufferedInputStream(body.byteStream())) {
                    output.seek(segmentStart);
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        waitForResume();
                        output.write(buffer, 0, read);
                        segmentStart += read;
                        written += read;
                        downloaded.addAndGet(read);
                        if (segmentStart > end + 1) {
                            throw new IOException("Segment overflow");
                        }
                    }
                }
                if (segmentStart != end + 1) {
                    throw new IOException("Short segment "
                            + (segmentStart - start) + "/" + expected);
                }
                return;
            } catch (IOException error) {
                last = error;
                downloaded.addAndGet(-written);
                if (cancelled || switchingSource) {
                    throw error;
                }
                long backoff = segmentBackoffMs(error, attempt);
                if (backoff > 0) {
                    HelperLog.info("Segment " + start + "-" + end + " backoff "
                            + backoff + "ms: " + error.getMessage());
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw error;
                    }
                }
                HelperLog.info("Segment " + start + "-" + end + " retry "
                        + (attempt + 1) + " from " + segmentStart
                        + ": " + error.getMessage());
            } finally {
                activeCalls.remove(call);
            }
        }
        throw last != null ? last : new IOException("Segment failed");
    }

    private static long segmentBackoffMs(IOException error, int attempt) {
        String message = error == null ? null : error.getMessage();
        if (message == null) {
            return 250L;
        }
        if (message.contains("HTTP 429") || message.contains("HTTP 403")
                || message.startsWith("HTTP 5")) {
            return Math.min(8_000L, 1_000L << Math.min(attempt, 3));
        }
        return 250L;
    }

    private void waitForResume() throws IOException {
        while (paused && !cancelled) {
            try {
                Thread.sleep(200L);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("Cancelled");
            }
        }
        if (cancelled) {
            throw new IOException("Cancelled");
        }
    }

    private static long contentRangeTotal(String value) {
        if (value == null) {
            return -1;
        }
        int slash = value.lastIndexOf('/');
        if (slash < 0) {
            return -1;
        }
        String total = value.substring(slash + 1).trim();
        if ("*".equals(total)) {
            return -1;
        }
        try {
            return Long.parseLong(total);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static boolean rangeMatches(String value, long start, long end,
                                        long total) {
        if (value == null || !value.startsWith("bytes ")) {
            return false;
        }
        String spec = value.substring(6).trim();
        int slash = spec.indexOf('/');
        if (slash < 0) {
            return false;
        }
        String range = spec.substring(0, slash);
        String totalText = spec.substring(slash + 1).trim();
        int dash = range.indexOf('-');
        if (dash < 0) {
            return false;
        }
        try {
            long actualStart = Long.parseLong(range.substring(0, dash).trim());
            long actualEnd = Long.parseLong(range.substring(dash + 1).trim());
            long actualTotal = "*".equals(totalText)
                    ? total : Long.parseLong(totalText);
            return actualStart == start && actualEnd == end
                    && actualTotal == total;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private File finalizeDownloadedFile(File part, File dir, String filename,
                                        String pkg, String versionName)
            throws IOException {
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
        file = ArchiveTypeDetector.normalizeExtension(file);
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

    /**
     * Extracts owner/repo from a GitHub release URL. Returns null for GitHub search
     * pages and other shapes that are not a specific repository release.
     */
    private String githubRepoFromUrl(String url) {
        if (TextUtils.isEmpty(url) || !isHost(hostOf(url), "github.com")) {
            return null;
        }
        try {
            String[] parts = new URL(url).getPath().split("/");
            if (parts.length < 5 || !"releases".equals(parts[3])
                    || parts[1].isEmpty() || parts[2].isEmpty()) {
                return null;
            }
            return parts[1] + "/" + parts[2];
        } catch (Exception ignored) {
            return null;
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
