package be.alterra.morphehelper;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.File;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The main Morphe shell with Home, Downloads, Sources, and Settings. It is the
 * launcher entry point and uses the saved theme, accent, source settings, and
 * download folder for the rest of the app.
 */
public class MorphePreviewActivity extends Activity {

    private static final int TAB_HOME = 0;
    private static final int TAB_SOURCES = 1;
    private static final int TAB_PATCHES = 2;
    private static final int TAB_DOWNLOADS = 3;
    private static final int TAB_SETTINGS = 4;
    private static final String STATE_TAB = "selected_tab";
    private static final String MORPHE_PACKAGE = "app.morphe.manager";
    private static final String AWESOME_MORPHE_URL =
            "https://github.com/nvbangg/awesome-morphe";
    private static final int PATCH_SORT_RECOMMENDED = 1;
    private static final int PATCH_SORT_NAME = 2;
    private static final int PATCH_SORT_UPDATED = 3;
    private static final int PATCH_SORT_PATCHES = 4;
    private static final int REQ_SAVE_LOG = 6103;
    private static final int REQ_NOTIFICATIONS = 41;
    private static final String FEATURE_REQUEST_URL =
            "https://github.com/GeneralZyonix/morphe-download-helper/issues/new"
                    + "?template=feature_request.yml";

    private ThemePalette palette;
    private FrameLayout contentHost;
    private LinearLayout navBar;
    private final LinearLayout[] navItems = new LinearLayout[5];
    private final ImageView[] navIcons = new ImageView[5];
    private final TextView[] navLabels = new TextView[5];
    private final View[] tabs = new View[5];
    private int selectedTab = TAB_HOME;
    private String themeSignature;
    private String hotbarSignature;

    private int TEXT;
    private int MUTED;
    private int ACCENT;
    private int GREEN;
    private int RED;
    private int CARD;
    private int CARD_HIGH;

    private LinearLayout sourceList;
    private TextView homeSubtitle;
    private TextView sourceTestAllButton;
    private boolean sourceTestingAll;
    private final Map<String, TextView> sourceHealthViews = new LinkedHashMap<>();

    private PatchSourceCatalog patchCatalog;
    private String patchCatalogHash;
    private boolean patchHasRendered;
    private ScrollView patchScroll;
    private LinearLayout patchResults;
    private TextView patchStatus;
    private View patchStatusDot;
    private TextView patchRetry;
    private EditText patchSearch;
    private final Handler patchHandler = new Handler(Looper.getMainLooper());
    private Runnable patchSearchTask;
    private boolean patchLoading;
    private long patchLastLoadedAt;
    private String expandedPatchPackage;
    private LinearLayout expandedPatchPanel;
    private PatchSourceCatalog.App expandedPatchApp;
    private ImageView expandedPatchChevron;
    private int patchSortMode = PATCH_SORT_RECOMMENDED;
    private boolean patchOfficialOnly;
    private final Map<String, LinearLayout> patchHeaders = new LinkedHashMap<>();
    private List<PatchSourceCatalog.App> patchInstalledApps;
    private String patchInstalledHash;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PublicSettings.apply(this);
        AppSettings.migrateLegacyDesign(this);
        AppSettings.ensureLauncherIconState(this);
        HelperLog.init(getApplicationContext());
        SourceSettings.ensureInitialized(this);
        applyPalette();
        hotbarSignature = AppSettings.hotbarOrderSignature(this);
        new Thread(() -> AppSettings.cleanup(this)).start();
        new Thread(() -> UpdateManager.cleanupStagedUpdates(this)).start();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIFICATIONS);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{palette.bgTop, palette.bgBottom}));
        addGlow(root, palette.primaryStart, Gravity.TOP | Gravity.END,
                -dp(90), -dp(130), 290);
        addGlow(root, palette.primaryEnd, Gravity.CENTER_VERTICAL | Gravity.START,
                -dp(140), dp(140), 270);

        contentHost = new FrameLayout(this);
        root.addView(contentHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        navBar = buildNavBar();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int navWidth = Math.min(screenWidth - dp(36), dp(720));
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                navWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        navParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        navParams.bottomMargin = dp(16);
        root.addView(navBar, navParams);

        setContentView(root);
        ThemeManager.applySystemBars(this);

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            contentHost.setPadding(0, top, 0, 0);
            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) navBar.getLayoutParams();
            params.bottomMargin = dp(16) + bottom;
            navBar.setLayoutParams(params);
            return insets;
        });

        int initialTab = savedInstanceState == null
                ? TAB_HOME : savedInstanceState.getInt(STATE_TAB, TAB_HOME);
        if (!isTabAvailable(initialTab)) {
            initialTab = TAB_HOME;
        }
        selectTab(initialTab, false);
        UpdateManager.maybeCheckOnLaunch(this,
                () -> UpdateManager.showInAppUpdatePopupIfAvailable(this));
        handleSharedText(getIntent());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, selectedTab);
    }

    @Override
    protected void onDestroy() {
        if (patchSearchTask != null) {
            patchHandler.removeCallbacks(patchSearchTask);
            patchSearchTask = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSharedText(intent);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (selectedTab != TAB_HOME) {
            selectTab(TAB_HOME, true);
            return;
        }
        super.onBackPressed();
    }

    /** A shared GitHub link still opens the Sources import flow. */
    private void handleSharedText(Intent intent) {
        if (intent == null) {
            return;
        }
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (TextUtils.isEmpty(text)) {
            return;
        }
        Intent settings = new Intent(this, SettingsActivity.class);
        settings.putExtra(SettingsActivity.EXTRA_SECTION,
                SettingsActivity.EXTRA_SECTION_SOURCES);
        settings.putExtra(SettingsActivity.EXTRA_SHARE_TEXT, text);
        startActivity(settings);
        intent.removeExtra(Intent.EXTRA_TEXT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeSignature != null
                && !themeSignature.equals(ThemeManager.signature(this))) {
            recreate();
            return;
        }
        if (hotbarSignature != null
                && !hotbarSignature.equals(AppSettings.hotbarOrderSignature(this))) {
            recreate();
            return;
        }
        if (!isTabAvailable(selectedTab)) {
            selectTab(TAB_HOME, false);
        } else if (selectedTab == TAB_DOWNLOADS || selectedTab == TAB_SOURCES) {
            selectTab(selectedTab, false);
        } else if (selectedTab == TAB_PATCHES) {
            refreshPatchCatalog(false);
        }
        UpdateManager.showInAppUpdatePopupIfAvailable(this);
    }

    private void applyPalette() {
        palette = ThemeManager.palette(this);
        TEXT = palette.text;
        MUTED = palette.muted;
        ACCENT = palette.primaryStart;
        GREEN = palette.green;
        RED = palette.red;
        CARD = M3Ui.surfaceContainer(palette);
        CARD_HIGH = M3Ui.surfaceContainerHigh(palette);
        themeSignature = ThemeManager.signature(this);
    }

    // ------------------------------------------------------------------
    // Shell
    // ------------------------------------------------------------------

    private void selectTab(int index, boolean animate) {
        if (!isTabAvailable(index)) {
            return;
        }
        // Re-tapping the active tab should not rebuild the page or replay the
        // slide. onResume still calls this with animate=false to refresh data.
        if (index == selectedTab && animate && contentHost.getChildCount() > 0) {
            updateNav();
            return;
        }
        int previous = selectedTab;
        selectedTab = index;
        if (index == TAB_PATCHES && tabs[index] != null) {
            refreshPatchCatalog(false);
        }
        if (index == TAB_DOWNLOADS || index == TAB_SOURCES) {
            tabs[index] = null;
        }
        if (tabs[index] == null) {
            tabs[index] = buildTab(index);
        }
        View next = tabs[index];
        View old = contentHost.getChildCount() == 0
                ? null
                : contentHost.getChildAt(contentHost.getChildCount() - 1);
        if (old == next) {
            updateNav();
            return;
        }

        if (old != null) {
            old.animate().cancel();
        }
        next.animate().cancel();
        contentHost.removeAllViews();
        FrameLayout.LayoutParams pageParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        if (!animate || old == null) {
            next.setTranslationX(0f);
            next.setAlpha(1f);
            contentHost.addView(next, pageParams);
            updateNav();
            return;
        }

        float width = contentHost.getWidth() > 0
                ? contentHost.getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        float direction = tabPosition(index) >= tabPosition(previous) ? 1f : -1f;
        old.setTranslationX(0f);
        old.setAlpha(1f);
        next.setTranslationX(direction * width);
        next.setAlpha(0.92f);
        contentHost.addView(old, pageParams);
        contentHost.addView(next, pageParams);

        DecelerateInterpolator easing = new DecelerateInterpolator(1.6f);
        next.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(260L)
                .setInterpolator(easing)
                .start();
        old.animate()
                .translationX(-direction * width)
                .alpha(0.85f)
                .setDuration(260L)
                .setInterpolator(easing)
                .withEndAction(() -> {
                    contentHost.removeView(old);
                    old.setTranslationX(0f);
                    old.setAlpha(1f);
                })
                .start();
        updateNav();
    }

    private View buildTab(int index) {
        switch (index) {
            case TAB_DOWNLOADS:
                return buildDownloads();
            case TAB_SOURCES:
                return buildSources();
            case TAB_PATCHES:
                return buildPatches();
            case TAB_SETTINGS:
                return buildSettings();
            default:
                return buildHome();
        }
    }

    private LinearLayout buildNavBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(6), dp(6), dp(6), dp(6));
        bar.setBackground(M3Ui.filled(this, CARD_HIGH, M3Ui.SHAPE_FULL,
                Math.max(1, dp(1)), M3Ui.outlineVariant(palette)));
        bar.setElevation(dp(12));

        List<String> order = AppSettings.hotbarOrder(this);
        int labelSize = order.size() >= 5 ? 9 : 10;
        for (String key : order) {
            switch (key) {
                case AppSettings.HOTBAR_HOME:
                    addNavItem(bar, TAB_HOME, AppSettings.hotbarIconRes(key),
                            AppSettings.hotbarLabelRes(key), labelSize,
                            () -> selectTab(TAB_HOME, true));
                    break;
                case AppSettings.HOTBAR_SOURCES:
                    addNavItem(bar, TAB_SOURCES, AppSettings.hotbarIconRes(key),
                            AppSettings.hotbarLabelRes(key), labelSize,
                            () -> selectTab(TAB_SOURCES, true));
                    break;
                case AppSettings.HOTBAR_PATCHES:
                    if (AppSettings.hotbarTabAvailable(key)) {
                        addNavItem(bar, TAB_PATCHES, AppSettings.hotbarIconRes(key),
                                AppSettings.hotbarLabelRes(key), labelSize,
                                () -> selectTab(TAB_PATCHES, true));
                    }
                    break;
                case AppSettings.HOTBAR_DOWNLOADS:
                    addNavItem(bar, TAB_DOWNLOADS, AppSettings.hotbarIconRes(key),
                            AppSettings.hotbarLabelRes(key), labelSize,
                            () -> selectTab(TAB_DOWNLOADS, true));
                    break;
                case AppSettings.HOTBAR_SETTINGS:
                    addNavItem(bar, TAB_SETTINGS, AppSettings.hotbarIconRes(key),
                            AppSettings.hotbarLabelRes(key), labelSize,
                            () -> selectTab(TAB_SETTINGS, true));
                    break;
                default:
                    break;
            }
        }
        return bar;
    }

    private void addNavItem(LinearLayout bar, int index, int iconRes, int labelRes,
                            int labelSize, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(6), dp(8), dp(6), dp(7));
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(v -> action.run());

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(MUTED);
        item.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView label = M3Ui.text(this, getString(labelRes), labelSize, MUTED, 500);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(2);
        item.addView(label, labelParams);

        navItems[index] = item;
        navIcons[index] = icon;
        navLabels[index] = label;
        bar.addView(item, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private boolean isTabAvailable(int index) {
        return AppSettings.hotbarTabAvailable(tabKey(index));
    }

    private String tabKey(int index) {
        switch (index) {
            case TAB_SOURCES:
                return AppSettings.HOTBAR_SOURCES;
            case TAB_PATCHES:
                return AppSettings.HOTBAR_PATCHES;
            case TAB_DOWNLOADS:
                return AppSettings.HOTBAR_DOWNLOADS;
            case TAB_SETTINGS:
                return AppSettings.HOTBAR_SETTINGS;
            default:
                return AppSettings.HOTBAR_HOME;
        }
    }

    private int tabPosition(int index) {
        int position = AppSettings.hotbarOrder(this).indexOf(tabKey(index));
        return position < 0 ? 0 : position;
    }

    private void updateNav() {
        for (int i = 0; i < navItems.length; i++) {
            if (navItems[i] == null) {
                continue;
            }
            boolean selected = i == selectedTab;
            navItems[i].setBackground(selected
                    ? M3Ui.filled(this, M3Ui.primaryContainer(palette),
                    M3Ui.SHAPE_FULL, 0, 0)
                    : null);
            int foreground = selected
                    ? M3Ui.onPrimaryContainer(palette) : MUTED;
            navIcons[i].setColorFilter(foreground);
            navLabels[i].setTextColor(foreground);
        }
    }

    // ------------------------------------------------------------------
    // Home
    // ------------------------------------------------------------------

    private View buildHome() {
        ScrollView scroll = tabScroll();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        View header = homeHeader();
        root.addView(statusRow());
        root.addView(heroCard());
        root.addView(featureRow());
        root.addView(quickActions());
        root.addView(developerBlock());
        addBottomSpacer(root);
        return stickyTab(header, scroll, homeSubtitle);
    }

    private View homeHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        PatchPassLogoView logo = new PatchPassLogoView(this);
        logo.setContentDescription(getString(R.string.app_name));
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(64), dp(64));
        logoParams.setMarginEnd(dp(12));
        header.addView(logo, logoParams);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(M3Ui.display(this, getString(R.string.app_name), 20, TEXT));
        TextView subtitle = M3Ui.text(this, getString(R.string.main_subtitle),
                12, MUTED, 400);
        subtitle.setPadding(0, dp(3), 0, 0);
        homeSubtitle = subtitle;
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return header;
    }

    private View statusRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(14);
        row.setLayoutParams(params);

        String morpheVersion = morpheVersion();
        boolean morpheInstalled = !morpheVersion.isEmpty();
        String morpheLabel = morpheInstalled
                ? getString(R.string.main_status_detected_version, morpheVersion)
                : getString(R.string.main_status_missing_chip);
        row.addView(statusChip(morpheLabel, morpheInstalled ? GREEN : RED));

        LinearLayout helperChip = new LinearLayout(this);
        helperChip.setOrientation(LinearLayout.HORIZONTAL);
        helperChip.setGravity(Gravity.CENTER_VERTICAL);
        helperChip.setPadding(dp(12), dp(7), dp(12), dp(7));
        helperChip.setBackground(M3Ui.filled(this,
                M3Ui.secondaryContainer(palette), M3Ui.SHAPE_FULL, 0, 0));
        TextView helperText = M3Ui.text(this, getString(
                R.string.main_version_chip_version, helperVersion()), 12, TEXT, 500);
        helperChip.addView(helperText);
        TextView badge = M3Ui.versionBadge(this, palette, BuildConfig.PRERELEASE);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        badgeParams.setMarginStart(dp(7));
        helperChip.addView(badge, badgeParams);
        String buildName = getString(AppSettings.isLauncherIconShown(this)
                ? R.string.main_build_icon : R.string.main_build_no_icon);
        TextView buildText = M3Ui.text(this,
                getString(R.string.main_version_chip_build, buildName), 12, TEXT, 500);
        buildText.setPadding(dp(6), 0, 0, 0);
        helperChip.addView(buildText);
        LinearLayout.LayoutParams helperParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        helperParams.setMarginStart(dp(8));
        row.addView(helperChip, helperParams);
        return row;
    }

    private TextView statusChip(String label, int color) {
        TextView chip = M3Ui.text(this, label, 12, color, 600);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), dp(7), dp(12), dp(7));
        int fill = M3Ui.blend(color, CARD, palette.light ? 0.88f : 0.82f);
        chip.setBackground(M3Ui.filled(this, fill, M3Ui.SHAPE_FULL,
                Math.max(1, dp(1)), M3Ui.blend(color, CARD, 0.55f)));
        return chip;
    }

    private View heroCard() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        int start = M3Ui.blend(palette.primaryStart, Color.BLACK, 0.16f);
        int end = M3Ui.blend(palette.primaryEnd, Color.BLACK, 0.38f);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        background.setCornerRadius(dp(M3Ui.SHAPE_XL));
        background.setStroke(Math.max(1, dp(1)), M3Ui.blend(
                palette.primaryStart, Color.WHITE, 0.28f));
        hero.setBackground(background);
        hero.setElevation(dp(6));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        heroParams.topMargin = dp(18);
        hero.setLayoutParams(heroParams);

        TextView kicker = M3Ui.overline(this, getString(R.string.main_hero_kicker),
                Color.argb(205, 255, 255, 255));
        hero.addView(kicker);

        TextView title = M3Ui.display(this, getString(R.string.main_hero_title),
                30, Color.WHITE);
        title.setPadding(0, dp(8), 0, 0);
        hero.addView(title);

        TextView body = M3Ui.text(this, getString(R.string.main_hero_body),
                13, Color.argb(215, 255, 255, 255), 400);
        body.setLineSpacing(dp(3), 1f);
        body.setPadding(0, dp(8), 0, 0);
        hero.addView(body);

        TextView action = heroButton(getString(R.string.main_open_morphe),
                R.drawable.ic_external_link, this::openMorphe);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        actionParams.topMargin = dp(18);
        hero.addView(action, actionParams);
        return hero;
    }

    private TextView heroButton(String label, int iconRes, Runnable action) {
        float tint = palette.light ? 0.62f : 0.78f;
        int fillStart = M3Ui.blend(palette.primaryStart, Color.WHITE, tint);
        int fillEnd = M3Ui.blend(palette.primaryEnd, Color.WHITE, Math.max(0f, tint - 0.05f));
        int foreground = M3Ui.blend(palette.primaryStart, Color.BLACK, 0.58f);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{fillStart, fillEnd});
        background.setCornerRadius(dp(M3Ui.SHAPE_FULL));
        TextView button = M3Ui.text(this, label, 15, foreground, 600);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setPadding(dp(16), dp(13), dp(16), dp(13));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x33000000), background, null));
        if (iconRes != 0) {
            Drawable icon = getDrawable(iconRes);
            if (icon != null) {
                icon.setTint(foreground);
                button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
                button.setCompoundDrawablePadding(dp(9));
            }
        }
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private View featureRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(14);
        row.setLayoutParams(params);

        LinearLayout guide = featureTile(
                R.string.main_visual_guide, R.string.preview_guide_body,
                R.drawable.ic_book,
                M3Ui.blend(palette.primaryStart, Color.BLACK, 0.30f),
                M3Ui.blend(palette.primaryStart, Color.BLACK, 0.55f),
                () -> startActivity(new Intent(this, GuideActivity.class)));
        LinearLayout.LayoutParams guideParams = new LinearLayout.LayoutParams(
                0, dp(192), 1f);
        guideParams.setMarginEnd(dp(6));
        row.addView(guide, guideParams);

        LinearLayout log = featureTile(
                R.string.main_log_title, R.string.preview_log_body,
                R.drawable.ic_log_doc,
                M3Ui.blend(palette.primaryEnd, Color.BLACK, 0.30f),
                M3Ui.blend(palette.primaryEnd, Color.BLACK, 0.55f),
                this::showLogSheet);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                0, dp(192), 1f);
        logParams.setMarginStart(dp(6));
        row.addView(log, logParams);
        return row;
    }

    private LinearLayout featureTile(int titleRes, int bodyRes, int iconRes,
                                     int start, int end, Runnable action) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(16), dp(16), dp(16), dp(14));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        background.setCornerRadius(dp(M3Ui.SHAPE_XL));
        background.setStroke(Math.max(1, dp(1)),
                Color.argb(40, 255, 255, 255));
        tile.setBackground(background);
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setOnClickListener(v -> action.run());

        FrameLayout iconTile = new FrameLayout(this);
        iconTile.setBackground(M3Ui.filled(this,
                Color.argb(38, 255, 255, 255), 13, 0, 0));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.WHITE);
        FrameLayout.LayoutParams iconInner = new FrameLayout.LayoutParams(dp(22), dp(22));
        iconInner.gravity = Gravity.CENTER;
        iconTile.addView(icon, iconInner);
        tile.addView(iconTile, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView title = M3Ui.title(this, getString(titleRes), 17, Color.WHITE);
        title.setPadding(0, dp(14), 0, 0);
        tile.addView(title);

        TextView body = M3Ui.text(this, getString(bodyRes), 12,
                Color.argb(205, 255, 255, 255), 400);
        body.setLineSpacing(dp(2), 1f);
        body.setPadding(0, dp(4), 0, 0);
        tile.addView(body);

        View spacer = new View(this);
        tile.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView arrow = M3Ui.text(this, "\u2192", 16, Color.WHITE, 600);
        arrow.setGravity(Gravity.CENTER);
        arrow.setBackground(M3Ui.filled(this,
                Color.argb(38, 255, 255, 255), M3Ui.SHAPE_FULL, 0, 0));
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        arrowParams.gravity = Gravity.END;
        arrowParams.topMargin = dp(10);
        tile.addView(arrow, arrowParams);
        return tile;
    }

    private View quickActions() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(14);
        block.setLayoutParams(params);

        TextView help = M3Ui.outlinedButton(this, palette, "?", 0, TEXT);
        help.setTextSize(16);
        help.setGravity(Gravity.CENTER);
        help.setPadding(0, 0, 0, 0);
        help.setContentDescription(getString(R.string.main_help));
        help.setOnClickListener(v -> showHelp());
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(
                dp(48), dp(44));
        helpParams.gravity = Gravity.END;
        block.addView(help, helpParams);
        return block;
    }

    private void showHelp() {
        String message = getString(R.string.help_log)
                + "\n\n" + getString(R.string.help_play_protect)
                + "\n\n" + getString(R.string.help_uptodown)
                + "\n\n" + getString(R.string.help_icons)
                + "\n\n" + getString(R.string.help_sources);
        ThemeManager.dialog(this)
                .setTitle(R.string.main_help_title)
                .setMessage(message)
                .setPositiveButton(R.string.common_close, null)
                .show();
    }

    private void confirmDeleteAllDownloads() {
        ThemeManager.dialog(this)
                .setTitle(R.string.main_delete_all_title)
                .setMessage(R.string.main_delete_all_message)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_delete, (dialog, which) -> {
                    int deleted = AppSettings.deleteAllDownloads(this);
                    Toast.makeText(this,
                            getString(R.string.main_delete_all_done, deleted),
                            Toast.LENGTH_SHORT).show();
                    if (selectedTab == TAB_DOWNLOADS) {
                        selectTab(TAB_DOWNLOADS, false);
                    }
                })
                .show();
    }

    private View developerBlock() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(26);
        block.setLayoutParams(params);

        SpannableStringBuilder credit = new SpannableStringBuilder();
        credit.append(getString(R.string.preview_developer)).append(" ");
        int start = credit.length();
        credit.append("GeneralZyonix");
        credit.setSpan(new StyleSpan(Typeface.BOLD), start, credit.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView creditView = M3Ui.text(this, credit.toString(), 13, MUTED, 400);
        creditView.setGravity(Gravity.CENTER);
        block.addView(creditView);
        TextView role = M3Ui.text(this,
                getString(R.string.main_developer_role), 11, MUTED, 400);
        role.setGravity(Gravity.CENTER);
        role.setPadding(0, dp(3), 0, 0);
        block.addView(role);

        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.HORIZONTAL);
        links.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        linkParams.topMargin = dp(12);
        links.setLayoutParams(linkParams);

        TextView github = M3Ui.outlinedButton(this, palette, "GitHub",
                R.drawable.source_logo_github, TEXT);
        github.setOnClickListener(v -> openUrl(
                "https://github.com/GeneralZyonix/morphe-download-helper"));
        LinearLayout.LayoutParams githubParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        githubParams.setMarginEnd(dp(4));
        links.addView(github, githubParams);

        TextView releases = M3Ui.outlinedButton(this, palette,
                getString(R.string.main_link_releases),
                R.drawable.ic_release_tag, TEXT);
        releases.setOnClickListener(v -> openUrl(
                "https://github.com/GeneralZyonix/morphe-download-helper/releases"));
        LinearLayout.LayoutParams releaseParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        releaseParams.setMarginStart(dp(4));
        releaseParams.setMarginEnd(dp(4));
        links.addView(releases, releaseParams);

        TextView reddit = M3Ui.outlinedButton(this, palette, "Reddit",
                R.drawable.ic_reddit, TEXT);
        reddit.setOnClickListener(v -> openUrl(
                "https://www.reddit.com/r/MorpheApp/comments/1xjmnf3/i_made_an_helper_that_fetches_the_right_apk_for/"));
        LinearLayout.LayoutParams redditParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        redditParams.setMarginStart(dp(4));
        links.addView(reddit, redditParams);
        block.addView(links);
        return block;
    }

    // ------------------------------------------------------------------
    // Downloads
    // ------------------------------------------------------------------

    private View buildDownloads() {
        ScrollView scroll = tabScroll();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(M3Ui.display(this, getString(R.string.preview_downloads),
                30, TEXT));
        TextView subtitle = M3Ui.text(this,
                getString(R.string.preview_downloads_subtitle), 12, MUTED, 400);
        subtitle.setLineSpacing(dp(2), 1f);
        subtitle.setPadding(0, dp(4), dp(8), 0);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView deleteAll = iconButton(R.drawable.ic_delete,
                R.string.main_delete_all_downloads, this::confirmDeleteAllDownloads);
        LinearLayout.LayoutParams deleteAllParams = new LinearLayout.LayoutParams(
                dp(42), dp(42));
        deleteAllParams.setMarginEnd(dp(6));
        header.addView(deleteAll, deleteAllParams);
        ImageView refresh = iconButton(R.drawable.ic_refresh,
                R.string.preview_downloads_refresh,
                () -> selectTab(TAB_DOWNLOADS, true));
        header.addView(refresh, new LinearLayout.LayoutParams(dp(42), dp(42)));

        List<DownloadEntry> files = listDownloads();
        Map<String, DownloadEntry> byName = new LinkedHashMap<>();
        for (DownloadEntry file : files) {
            byName.put(file.name, file);
        }

        List<DownloadHistory.Entry> history = DownloadHistory.list(this);
        java.util.Set<String> rendered = new java.util.HashSet<>();
        int shown = 0;
        if (!history.isEmpty()) {
            root.addView(sectionLabel(getString(R.string.preview_downloads_recent)));
            for (DownloadHistory.Entry entry : history) {
                if (!rendered.add(entry.fileName)) {
                    continue;
                }
                root.addView(historyRow(entry, byName.get(entry.fileName)));
                shown++;
            }
        }

        List<DownloadEntry> remaining = new ArrayList<>();
        for (DownloadEntry file : files) {
            if (!rendered.contains(file.name)) {
                remaining.add(file);
            }
        }
        if (!remaining.isEmpty()) {
            if (shown > 0) {
                root.addView(sectionLabel(getString(R.string.preview_downloads_local)));
            }
            for (DownloadEntry entry : remaining) {
                root.addView(downloadRow(entry));
                shown++;
            }
        }

        if (shown == 0) {
            root.addView(emptyCard(R.drawable.ic_downloads,
                    getString(R.string.preview_downloads_empty_title),
                    getString(R.string.preview_downloads_empty_body)));
        }
        addBottomSpacer(root);
        return stickyTab(header, scroll, subtitle);
    }

    /**
     * Collects APKs from the helper cache and, when the public folder is
     * selected, from MediaStore's Downloads/Morphe Helper directory.
     */
    private List<DownloadEntry> listDownloads() {
        Map<String, DownloadEntry> entries = new LinkedHashMap<>();
        addFileEntries(entries, AppSettings.downloadDir(this));
        String location = AppSettings.location(this);
        if (AppSettings.LOCATION_PUBLIC.equals(location)) {
            if (Build.VERSION.SDK_INT >= 29) {
                addMediaStoreEntries(entries);
            } else {
                addFileEntries(entries, new File(
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS),
                        "Morphe Helper"));
            }
        }
        List<DownloadEntry> list = new ArrayList<>(entries.values());
        list.sort((left, right) -> Long.compare(right.modified, left.modified));
        return list;
    }

    private void addFileEntries(Map<String, DownloadEntry> entries, File dir) {
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.isFile() || PublicSettings.isSettingsFileName(file.getName())) {
                continue;
            }
            entries.put(file.getName(), new DownloadEntry(file));
        }
    }

    private void addMediaStoreEntries(Map<String, DownloadEntry> entries) {
        String[] projection = {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED
        };
        String selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
        String[] args = {"%Morphe Helper/%"};
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection, selection, args, null)) {
            if (cursor == null) {
                return;
            }
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.MediaColumns.DISPLAY_NAME);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
            int dateColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.MediaColumns.DATE_MODIFIED);
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameColumn);
                if (TextUtils.isEmpty(name)
                        || PublicSettings.isSettingsFileName(name)) {
                    continue;
                }
                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idColumn));
                entries.put(name, new DownloadEntry(uri, name,
                        cursor.getLong(sizeColumn),
                        cursor.getLong(dateColumn) * 1000L));
            }
        } catch (Exception error) {
            HelperLog.error("Could not list public downloads", error);
        }
    }

    private View downloadRow(DownloadEntry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(10), dp(12));
        row.setBackground(M3Ui.card(this, palette));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(10);
        row.setLayoutParams(rowParams);
        row.setOnClickListener(v -> openEntry(entry));

        FrameLayout iconTile = new FrameLayout(this);
        iconTile.setBackground(M3Ui.filled(this,
                M3Ui.primaryContainer(palette), 12, 0, 0));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_downloads);
        icon.setColorFilter(M3Ui.onPrimaryContainer(palette));
        FrameLayout.LayoutParams iconInner = new FrameLayout.LayoutParams(dp(20), dp(20));
        iconInner.gravity = Gravity.CENTER;
        iconTile.addView(icon, iconInner);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconParams.setMarginEnd(dp(12));
        row.addView(iconTile, iconParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = M3Ui.title(this, entry.name, 13, TEXT);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(name);
        TextView detail = M3Ui.text(this, formatSize(entry.size) + "  \u00b7  "
                + formatDate(entry.modified), 11, MUTED, 400);
        detail.setPadding(0, dp(3), 0, 0);
        labels.addView(detail);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView delete = iconButton(R.drawable.ic_delete,
                R.string.preview_download_delete, () -> confirmDelete(entry));
        row.addView(delete, new LinearLayout.LayoutParams(dp(38), dp(38)));
        return row;
    }

    private View historyRow(DownloadHistory.Entry entry, DownloadEntry file) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(10), dp(12));
        row.setBackground(M3Ui.card(this, palette));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(10);
        row.setLayoutParams(rowParams);
        row.setOnClickListener(v -> {
            if (file != null) {
                openEntry(file);
            } else {
                Toast.makeText(this, R.string.preview_download_missing,
                        Toast.LENGTH_SHORT).show();
            }
        });

        FrameLayout iconTile = new FrameLayout(this);
        iconTile.setBackground(M3Ui.filled(this,
                file != null ? M3Ui.primaryContainer(palette) : CARD_HIGH,
                12, 0, 0));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_downloads);
        icon.setColorFilter(file != null
                ? M3Ui.onPrimaryContainer(palette) : MUTED);
        FrameLayout.LayoutParams iconInner = new FrameLayout.LayoutParams(dp(20), dp(20));
        iconInner.gravity = Gravity.CENTER;
        iconTile.addView(icon, iconInner);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconParams.setMarginEnd(dp(12));
        row.addView(iconTile, iconParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        String title = TextUtils.isEmpty(entry.appName) ? entry.fileName : entry.appName;
        TextView name = M3Ui.title(this, title, 13, TEXT);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(name);

        String source = entry.source;
        String meta = TextUtils.isEmpty(entry.version)
                ? source : (TextUtils.isEmpty(source)
                ? "v" + entry.version : "v" + entry.version + "  \u00b7  " + source);
        if (!TextUtils.isEmpty(meta)) {
            TextView versionView = M3Ui.text(this, meta, 11, MUTED, 400);
            versionView.setPadding(0, dp(3), 0, 0);
            labels.addView(versionView);
        }

        String detail = entry.fileName + "  \u00b7  " + formatSize(entry.size);
        if (file == null) {
            detail += "  \u00b7  " + getString(R.string.preview_download_removed);
        }
        TextView detailView = M3Ui.text(this, detail, 10,
                file != null ? MUTED : palette.footer, 400);
        detailView.setPadding(0, dp(2), 0, 0);
        labels.addView(detailView);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView delete = iconButton(R.drawable.ic_delete,
                R.string.preview_download_delete,
                () -> confirmRemoveHistory(entry, file));
        row.addView(delete, new LinearLayout.LayoutParams(dp(38), dp(38)));
        return row;
    }

    private View sectionLabel(String label) {
        TextView view = M3Ui.overline(this, label, ACCENT);
        view.setPadding(0, dp(16), 0, 0);
        return view;
    }

    private void confirmRemoveHistory(DownloadHistory.Entry entry, DownloadEntry file) {
        boolean hasFile = file != null;
        ThemeManager.dialog(this)
                .setTitle(hasFile
                        ? R.string.preview_download_delete_title
                        : R.string.preview_history_remove_title)
                .setMessage(hasFile
                        ? getString(R.string.preview_download_delete_message,
                        entry.fileName)
                        : getString(R.string.preview_history_remove_message,
                        entry.fileName))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(hasFile
                                ? R.string.common_delete
                                : R.string.preview_history_remove,
                        (dialog, which) -> {
                            boolean deleted = true;
                            if (hasFile) {
                                deleted = file.file != null
                                        ? file.file.delete()
                                        : getContentResolver().delete(
                                        file.uri, null, null) > 0;
                            }
                            if (!deleted) {
                                return;
                            }
                            DownloadHistory.remove(this, entry.id);
                            Toast.makeText(this, getString(hasFile
                                            ? R.string.preview_download_deleted
                                            : R.string.preview_history_removed,
                                    entry.fileName), Toast.LENGTH_SHORT).show();
                            selectTab(TAB_DOWNLOADS, false);
                        })
                .show();
    }

    private View emptyCard(int iconRes, String title, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(24), dp(28), dp(24), dp(28));
        card.setBackground(M3Ui.card(this, palette));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(20);
        card.setLayoutParams(params);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(M3Ui.onPrimaryContainer(palette));
        icon.setPadding(dp(14), dp(14), dp(14), dp(14));
        icon.setBackground(M3Ui.filled(this,
                M3Ui.primaryContainer(palette), M3Ui.SHAPE_FULL, 0, 0));
        card.addView(icon, new LinearLayout.LayoutParams(dp(58), dp(58)));

        TextView titleView = M3Ui.title(this, title, 18, TEXT);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, dp(14), 0, 0);
        card.addView(titleView);
        TextView bodyView = M3Ui.text(this, body, 12, MUTED, 400);
        bodyView.setGravity(Gravity.CENTER);
        bodyView.setLineSpacing(dp(2), 1f);
        bodyView.setPadding(0, dp(6), 0, 0);
        card.addView(bodyView);
        return card;
    }

    private void openEntry(DownloadEntry entry) {
        try {
            Uri uri = entry.file != null
                    ? FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", entry.file)
                    : entry.uri;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception error) {
            HelperLog.error("Could not open downloaded APK", error);
            Toast.makeText(this, R.string.preview_download_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(DownloadEntry entry) {
        ThemeManager.dialog(this)
                .setTitle(R.string.preview_download_delete_title)
                .setMessage(getString(R.string.preview_download_delete_message,
                        entry.name))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_delete, (dialog, which) -> {
                    boolean deleted = entry.file != null
                            ? entry.file.delete()
                            : getContentResolver().delete(entry.uri, null, null) > 0;
                    if (deleted) {
                        Toast.makeText(this,
                                getString(R.string.preview_download_deleted,
                                        entry.name),
                                Toast.LENGTH_SHORT).show();
                        selectTab(TAB_DOWNLOADS, false);
                    }
                })
                .show();
    }

    /** One APK in the local download list, backed by a file or MediaStore. */
    private static final class DownloadEntry {
        final File file;
        final Uri uri;
        final String name;
        final long size;
        final long modified;

        DownloadEntry(File file) {
            this.file = file;
            this.uri = null;
            this.name = file.getName();
            this.size = file.length();
            this.modified = file.lastModified();
        }

        DownloadEntry(Uri uri, String name, long size, long modified) {
            this.file = null;
            this.uri = uri;
            this.name = name;
            this.size = size;
            this.modified = modified;
        }
    }

    // ------------------------------------------------------------------
    // Sources
    // ------------------------------------------------------------------

    private View buildSources() {
        ScrollView scroll = tabScroll();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(M3Ui.display(this, getString(R.string.preview_sources), 30, TEXT));
        TextView subtitle = M3Ui.text(this,
                getString(R.string.source_manager_hint), 12, MUTED, 400);
        subtitle.setLineSpacing(dp(2), 1f);
        subtitle.setPadding(0, dp(4), dp(8), 0);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView reset = iconButton(R.drawable.ic_reset,
                R.string.settings_reset_order, this::resetSourceOrder);
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        resetParams.setMarginEnd(dp(4));
        header.addView(reset, resetParams);
        ImageView advanced = iconButton(R.drawable.ic_settings_gear,
                R.string.source_manager_full_settings,
                () -> openSettings(SettingsActivity.EXTRA_SECTION_SOURCES));
        header.addView(advanced, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setPadding(0, dp(8), 0, dp(4));
        TextView immediate = M3Ui.text(this,
                getString(R.string.source_manager_immediate), 11, MUTED, 400);
        LinearLayout.LayoutParams immediateParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        immediateParams.setMarginEnd(dp(8));
        tools.addView(immediate, immediateParams);
        sourceTestAllButton = M3Ui.outlinedButton(this, palette,
                getString(R.string.sources_test), 0, ACCENT);
        sourceTestAllButton.setTextSize(12);
        sourceTestAllButton.setPadding(dp(12), dp(7), dp(12), dp(7));
        sourceTestAllButton.setOnClickListener(this::showSourceTestMenu);
        tools.addView(sourceTestAllButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
        updateSourceTestAllButton();
        root.addView(tools);

        sourceList = new LinearLayout(this);
        sourceList.setOrientation(LinearLayout.VERTICAL);
        sourceList.setPadding(dp(6), dp(6), dp(6), dp(6));
        sourceList.setBackground(M3Ui.card(this, palette));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        listParams.topMargin = dp(10);
        root.addView(sourceList, listParams);
        rebuildSourceList();
        addBottomSpacer(root);
        return stickyTab(header, scroll, subtitle);
    }

    private void rebuildSourceList() {
        if (sourceList == null) {
            return;
        }
        sourceList.removeAllViews();
        sourceHealthViews.clear();
        List<String> order = displayOrder();
        for (int i = 0; i < order.size(); i++) {
            sourceList.addView(sourceRow(order.get(i)));
            if (i < order.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(M3Ui.outlineVariant(palette));
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                dividerParams.leftMargin = dp(52);
                sourceList.addView(divider, dividerParams);
            }
        }
    }

    private View sourceRow(String key) {
        boolean enabled = SourceSettings.isEnabled(this, key);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(10), dp(4), dp(10));
        row.setTag(key);

        TextView drag = M3Ui.text(this, "\u2261", 18, MUTED, 700);
        drag.setGravity(Gravity.CENTER);
        drag.setContentDescription(getString(R.string.settings_drag_source));
        drag.setPadding(dp(2), dp(4), dp(6), dp(4));
        drag.setOnClickListener(v -> showSourceMoveMenu(v, key));
        drag.setOnLongClickListener(v -> {
            startSourceDrag(row);
            return true;
        });
        row.addView(drag, new LinearLayout.LayoutParams(dp(26), dp(44)));

        View badge = SourceBadge.create(this, key);
        row.addView(badge, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(10), 0, dp(4), 0);
        String name = SourceSettings.APPTEKA.equals(key)
                ? getString(R.string.main_source_appteka)
                : AppSettings.sourceLabel(this, key);
        labels.addView(M3Ui.text(this, name, 14, enabled ? TEXT : MUTED, 600));
        String description = SourceManagerDialog.description(this, key);
        if (!description.isEmpty()) {
            TextView detail = M3Ui.text(this, description, 11,
                    enabled ? MUTED : palette.footer, 400);
            detail.setPadding(0, dp(2), 0, 0);
            labels.addView(detail);
        }
        TextView health = M3Ui.text(this, SourceHealth.summary(this, key),
                11, sourceHealthColor(key), 400);
        health.setPadding(0, dp(3), 0, 0);
        labels.addView(health);
        sourceHealthViews.put(key, health);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView test = M3Ui.text(this, getString(R.string.source_type_test), 10,
                enabled ? ACCENT : palette.footer, 600);
        test.setGravity(Gravity.CENTER);
        test.setSingleLine(true);
        test.setEllipsize(TextUtils.TruncateAt.END);
        test.setPadding(dp(4), dp(4), dp(4), dp(4));
        test.setClickable(true);
        test.setFocusable(true);
        test.setOnClickListener(v -> {
            test.setEnabled(false);
            test.setText("\u2026");
            TextView current = sourceHealthViews.get(key);
            if (current != null) {
                current.setText(R.string.settings_testing_source);
                current.setTextColor(MUTED);
            }
            SourceTester.test(this, key, (ok, message) -> runOnUiThread(() -> {
                test.setEnabled(true);
                test.setText(R.string.source_type_test);
                TextView result = sourceHealthViews.get(key);
                if (result != null) {
                    result.setText(SourceHealth.summary(this, key));
                    result.setTextColor(ok ? GREEN : RED);
                }
            }));
        });
        row.addView(test, new LinearLayout.LayoutParams(dp(44), dp(44)));

        Switch toggle = new Switch(this);
        toggle.setChecked(enabled);
        M3Ui.styleSwitch(toggle, palette);
        toggle.setOnCheckedChangeListener((buttonView, checked) -> {
            if (!checked && SourceSettings.enabledCount(this) <= 1) {
                buttonView.setChecked(true);
                Toast.makeText(this, R.string.settings_min_one_source,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            SourceSettings.setEnabled(this, key, checked);
            PublicSettings.write(this);
            rebuildSourceList();
        });
        row.addView(toggle, new LinearLayout.LayoutParams(dp(56), dp(44)));

        row.setOnDragListener((view, event) -> {
            int action = event.getAction();
            if (action == DragEvent.ACTION_DRAG_ENTERED) {
                view.setAlpha(0.65f);
                return true;
            }
            if (action == DragEvent.ACTION_DRAG_EXITED
                    || action == DragEvent.ACTION_DRAG_ENDED) {
                view.setAlpha(1f);
                return true;
            }
            if (action == DragEvent.ACTION_DROP) {
                view.setAlpha(1f);
                Object local = event.getLocalState();
                if (!(local instanceof View)) {
                    return true;
                }
                Object draggedTag = ((View) local).getTag();
                Object targetTag = view.getTag();
                if (draggedTag == null || targetTag == null) {
                    return true;
                }
                String dragged = String.valueOf(draggedTag);
                String target = String.valueOf(targetTag);
                if (dragged.equals(target)
                        || SourceSettings.isEnabled(this, dragged)
                        != SourceSettings.isEnabled(this, target)) {
                    return true;
                }
                List<String> order = displayOrder();
                int from = order.indexOf(dragged);
                int to = order.indexOf(target);
                if (from >= 0 && to >= 0) {
                    order.remove(from);
                    order.add(to, dragged);
                    AppSettings.setSourceOrder(this, order);
                    PublicSettings.write(this);
                    rebuildSourceList();
                }
                return true;
            }
            return action == DragEvent.ACTION_DRAG_STARTED;
        });
        return row;
    }

    private int sourceHealthColor(String key) {
        String summary = SourceHealth.summary(this, key);
        if (summary.startsWith(getString(R.string.common_ok))) {
            return GREEN;
        }
        if (summary.startsWith(getString(R.string.source_health_failed))) {
            return RED;
        }
        return MUTED;
    }

    private void updateSourceTestAllButton() {
        if (sourceTestAllButton == null) {
            return;
        }
        sourceTestAllButton.setEnabled(!sourceTestingAll);
        sourceTestAllButton.setText(sourceTestingAll
                ? R.string.sources_testing : R.string.sources_test);
    }

    private void showSourceTestMenu(View anchor) {
        if (sourceTestingAll) {
            return;
        }
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, getString(R.string.sources_test_enabled));
        menu.getMenu().add(0, 2, 1, getString(R.string.settings_test_all));
        menu.setOnMenuItemClickListener(item -> {
            runAllSourceTests(item.getItemId() == 1);
            return true;
        });
        menu.show();
    }

    private void runAllSourceTests(boolean enabledOnly) {
        if (sourceTestingAll) {
            return;
        }
        List<String> keys = new ArrayList<>();
        for (String key : displayOrder()) {
            if (!enabledOnly || SourceSettings.isEnabled(this, key)) {
                keys.add(key);
            }
        }
        if (!enabledOnly && !keys.contains(SourceSettings.UPTODOWN)) {
            keys.add(SourceSettings.UPTODOWN);
        }
        if (keys.isEmpty()) {
            return;
        }
        sourceTestingAll = true;
        updateSourceTestAllButton();
        int[] state = {0, 0, 0, 0}; // passed, failed, next, active
        Runnable[] launch = new Runnable[1];
        launch[0] = () -> {
            while (state[3] < 2 && state[2] < keys.size()) {
                String key = keys.get(state[2]++);
                state[3]++;
                TextView health = sourceHealthViews.get(key);
                if (health != null) {
                    health.setText(R.string.settings_testing_source);
                    health.setTextColor(MUTED);
                }
                SourceTester.test(this, key, (ok, message) ->
                        runOnUiThread(() -> {
                            state[3]--;
                            if (ok) {
                                state[0]++;
                            } else {
                                state[1]++;
                            }
                            TextView result = sourceHealthViews.get(key);
                            if (result != null) {
                                result.setText(SourceHealth.summary(this, key));
                                result.setTextColor(ok ? GREEN : RED);
                            }
                            if (state[2] >= keys.size() && state[3] == 0) {
                                sourceTestingAll = false;
                                updateSourceTestAllButton();
                                Toast.makeText(this, getString(
                                                R.string.settings_test_all_result,
                                                state[0], state[1]),
                                        Toast.LENGTH_LONG).show();
                            } else {
                                launch[0].run();
                            }
                        }));
            }
        };
        launch[0].run();
    }

    private void showSourceMoveMenu(View anchor, String key) {
        PopupMenu menu = new PopupMenu(this, anchor);
        List<String> order = displayOrder();
        int index = order.indexOf(key);
        MenuItem up = menu.getMenu().add(0, 1, 0,
                getString(R.string.settings_move_up));
        up.setEnabled(index > 0);
        MenuItem down = menu.getMenu().add(0, 2, 1,
                getString(R.string.settings_move_down));
        down.setEnabled(index >= 0 && index < order.size() - 1);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                moveSource(key, -1);
                return true;
            }
            if (item.getItemId() == 2) {
                moveSource(key, 1);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void startSourceDrag(View row) {
        Object tag = row.getTag();
        if (tag == null) {
            return;
        }
        ClipData clip = ClipData.newPlainText("source", String.valueOf(tag));
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(row);
        if (Build.VERSION.SDK_INT >= 24) {
            row.startDragAndDrop(clip, shadow, row, 0);
        } else {
            row.startDrag(clip, shadow, row, 0);
        }
    }

    private List<String> displayOrder() {
        List<String> order = AppSettings.sourceOrder(this);
        List<String> enabled = new ArrayList<>();
        List<String> disabled = new ArrayList<>();
        for (String key : order) {
            if (SourceSettings.isEnabled(this, key)) {
                enabled.add(key);
            } else {
                disabled.add(key);
            }
        }
        enabled.addAll(disabled);
        return enabled;
    }

    private void moveSource(String key, int delta) {
        AppSettings.moveSource(this, key, delta);
        PublicSettings.write(this);
        rebuildSourceList();
    }

    private void resetSourceOrder() {
        AppSettings.resetSourceOrder(this);
        PublicSettings.write(this);
        rebuildSourceList();
        Toast.makeText(this, R.string.settings_reset_order_done,
                Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------
    // Patches
    // ------------------------------------------------------------------

    private View buildPatches() {
        ScrollView scroll = tabScroll();
        patchScroll = scroll;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(M3Ui.display(this, getString(R.string.preview_patches),
                30, TEXT));
        SpannableStringBuilder subtitleText = patchSubtitleText();
        TextView subtitle = M3Ui.text(this, subtitleText.toString(),
                13, TEXT, 400);
        subtitle.setText(subtitleText);
        subtitle.setMovementMethod(LinkMovementMethod.getInstance());
        subtitle.setHighlightColor(Color.TRANSPARENT);
        subtitle.setLineSpacing(dp(2), 1f);
        subtitle.setPadding(0, dp(4), dp(8), 0);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView refresh = iconButton(R.drawable.ic_refresh,
                R.string.patches_refresh, () -> refreshPatchCatalog(true));
        header.addView(refresh, new LinearLayout.LayoutParams(dp(42), dp(42)));

        patchSearch = new EditText(this);
        patchSearch.setSingleLine(true);
        patchSearch.setHint(R.string.patches_search_hint);
        patchSearch.setTextSize(14);
        patchSearch.setTextColor(TEXT);
        patchSearch.setHintTextColor(MUTED);
        final GradientDrawable searchIdle = M3Ui.filled(this,
                M3Ui.surfaceContainerHigh(palette), M3Ui.SHAPE_FULL,
                Math.max(1, dp(1)), M3Ui.outlineVariant(palette));
        final GradientDrawable searchFocused = M3Ui.filled(this,
                M3Ui.surfaceContainerHigh(palette), M3Ui.SHAPE_FULL,
                Math.max(1, dp(1)), ACCENT);
        patchSearch.setBackground(searchIdle);
        patchSearch.setPadding(dp(18), dp(11), dp(18), dp(11));
        patchSearch.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        patchSearch.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        patchSearch.setCompoundDrawablePadding(dp(8));
        patchSearch.setOnFocusChangeListener((view, hasFocus) ->
                view.setBackground(hasFocus ? searchFocused : searchIdle));
        patchSearch.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                InputMethodManager keyboard = (InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);
                if (keyboard != null) {
                    keyboard.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
                renderPatchResults(false);
                return true;
            }
            return false;
        });
        patchSearch.setOnTouchListener((view, event) -> {
            if (event.getAction() != MotionEvent.ACTION_UP) {
                return false;
            }
            Drawable clear = patchSearch.getCompoundDrawablesRelative()[2];
            if (clear == null) {
                return false;
            }
            float start = patchSearch.getWidth()
                    - patchSearch.getPaddingRight()
                    - clear.getBounds().width() - dp(8);
            if (event.getX() >= start) {
                patchSearch.setText("");
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.topMargin = dp(16);
        root.addView(patchSearch, searchParams);
        patchSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                                      int count) {
                expandedPatchPackage = null;
                expandedPatchPanel = null;
                expandedPatchApp = null;
                expandedPatchChevron = null;
                updatePatchSearchClear();
                schedulePatchSearch();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, dp(10), 0, 0);
        patchStatusDot = new View(this);
        patchStatusDot.setBackground(M3Ui.filled(this, MUTED,
                M3Ui.SHAPE_FULL, 0, 0));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                dp(7), dp(7));
        dotParams.setMarginEnd(dp(8));
        statusRow.addView(patchStatusDot, dotParams);
        patchStatus = M3Ui.text(this,
                getString(R.string.patches_status_loading), 11, MUTED, 400);
        patchStatus.setLineSpacing(dp(2), 1f);
        statusRow.addView(patchStatus, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        patchRetry = M3Ui.text(this, getString(R.string.patches_retry),
                11, ACCENT, 600);
        patchRetry.setPadding(dp(10), dp(6), dp(2), dp(6));
        patchRetry.setClickable(true);
        patchRetry.setFocusable(true);
        patchRetry.setVisibility(View.GONE);
        patchRetry.setOnClickListener(v -> refreshPatchCatalog(true));
        statusRow.addView(patchRetry);
        root.addView(statusRow);

        patchResults = new LinearLayout(this);
        patchResults.setOrientation(LinearLayout.VERTICAL);
        patchResults.setPadding(dp(6), dp(6), dp(6), dp(6));
        patchResults.setBackground(M3Ui.card(this, palette));
        LinearLayout.LayoutParams resultsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        resultsParams.topMargin = dp(10);
        root.addView(patchResults, resultsParams);

        addBottomSpacer(root);
        renderPatchResults(false);
        refreshPatchCatalog(false);
        return stickyTab(header, scroll, subtitle);
    }

    private SpannableStringBuilder patchSubtitleText() {
        String name = getString(R.string.patches_attribution_name);
        String text = getString(R.string.patches_subtitle, name);
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        int start = text.indexOf(name);
        if (start >= 0) {
            builder.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    openUrl(AWESOME_MORPHE_URL);
                }

                @Override
                public void updateDrawState(TextPaint state) {
                    state.setColor(ACCENT);
                    state.setFakeBoldText(true);
                    state.setUnderlineText(false);
                }
            }, start, start + name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    private void schedulePatchSearch() {
        if (patchSearchTask != null) {
            patchHandler.removeCallbacks(patchSearchTask);
        }
        patchSearchTask = () -> {
            patchSearchTask = null;
            renderPatchResults(false);
        };
        patchHandler.postDelayed(patchSearchTask, 220L);
    }

    private void updatePatchSearchClear() {
        if (patchSearch == null) {
            return;
        }
        boolean hasText = patchSearch.length() > 0;
        patchSearch.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0, 0, hasText ? R.drawable.ic_close : 0, 0);
        if (hasText) {
            patchSearch.setCompoundDrawableTintList(
                    ColorStateList.valueOf(MUTED));
        }
    }

    private void renderPatchResults(boolean keepScroll) {
        if (patchResults == null) {
            return;
        }
        int scrollY = keepScroll && patchScroll != null
                ? patchScroll.getScrollY() : 0;
        renderPatchResultsContent();
        patchHasRendered = true;
        if (patchScroll != null) {
            patchScroll.post(() -> patchScroll.scrollTo(0, scrollY));
        }
    }

    private void renderPatchResultsContent() {
        patchResults.removeAllViews();
        patchHeaders.clear();
        expandedPatchPanel = null;
        expandedPatchApp = null;
        expandedPatchChevron = null;
        String query = patchSearch == null
                ? "" : patchSearch.getText().toString().trim();
        if (TextUtils.isEmpty(query)) {
            expandedPatchPackage = null;
            renderPatchBrowse();
            return;
        }
        if (patchCatalog == null) {
            expandedPatchPackage = null;
            addPatchMessage(getString(R.string.patches_status_loading));
            return;
        }
        List<PatchSourceCatalog.App> matches = patchCatalog.search(query, 40);
        if (matches.isEmpty()) {
            expandedPatchPackage = null;
            addPatchMessage(getString(R.string.patches_no_results, query));
            return;
        }
        boolean expandedStillPresent = false;
        for (int i = 0; i < matches.size(); i++) {
            PatchSourceCatalog.App app = matches.get(i);
            patchResults.addView(patchAppEntry(app));
            if (app.packageName.equals(expandedPatchPackage)) {
                expandedStillPresent = true;
            }
            if (i < matches.size() - 1) {
                patchResults.addView(patchDivider(dp(14)));
            }
        }
        if (!expandedStillPresent) {
            expandedPatchPackage = null;
        }
    }

    private void addPatchMessage(String message) {
        TextView view = M3Ui.text(this, message, 13, MUTED, 400);
        view.setGravity(Gravity.CENTER);
        view.setLineSpacing(dp(2), 1f);
        view.setPadding(dp(20), dp(26), dp(20), dp(26));
        patchResults.addView(view);
    }

    private void renderPatchBrowse() {
        if (patchCatalog == null) {
            addPatchMessage(getString(R.string.patches_status_loading));
            return;
        }
        Set<String> seen = new HashSet<>();
        boolean any = addPatchBrowseSection(R.string.patches_installed,
                R.string.patches_installed_detail, installedPatchApps(), seen);
        any |= addPatchBrowseSection(R.string.patches_popular, 0,
                patchCatalog.popular(5), seen);
        any |= addPatchBrowseSection(R.string.patches_recent, 0,
                recentPatchApps(), seen);
        if (!any) {
            addPatchMessage(getString(R.string.patches_search_empty_body));
        }
    }

    private boolean addPatchBrowseSection(int titleRes, int detailRes,
                                          List<PatchSourceCatalog.App> apps,
                                          Set<String> seen) {
        List<PatchSourceCatalog.App> filtered = new ArrayList<>();
        for (PatchSourceCatalog.App app : apps) {
            if (seen.add(app.packageName)) {
                filtered.add(app);
            }
        }
        if (filtered.isEmpty()) {
            return false;
        }
        TextView title = M3Ui.overline(this, getString(titleRes), ACCENT);
        title.setPadding(dp(8), dp(14), dp(8), dp(4));
        patchResults.addView(title);
        if (detailRes != 0) {
            TextView detail = M3Ui.text(this, getString(detailRes), 10,
                    palette.footer, 400);
            detail.setLineSpacing(dp(2), 1f);
            detail.setPadding(dp(8), 0, dp(8), dp(6));
            patchResults.addView(detail);
        }
        for (int i = 0; i < filtered.size(); i++) {
            patchResults.addView(patchQuickRow(filtered.get(i)));
            if (i < filtered.size() - 1) {
                patchResults.addView(patchDivider(dp(52)));
            }
        }
        return true;
    }

    private View patchQuickRow(PatchSourceCatalog.App app) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(9), dp(8), dp(9));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(M3Ui.ripple(this, null, palette.ripple));

        PatchIconView icon = new PatchIconView(this, 36);
        icon.bind(app.iconUrl, app.name);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                dp(36), dp(36));
        iconParams.setMarginEnd(dp(10));
        row.addView(icon, iconParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = M3Ui.text(this, app.name, 14, TEXT, 600);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(name);
        TextView detail = M3Ui.text(this, getResources().getQuantityString(
                        R.plurals.patches_source_count,
                        app.sources.size(), app.sources.size())
                        + " \u00b7 "
                        + getString(R.string.patches_source_updated,
                        PatchSourceCatalog.formatRelativeDate(this,
                                app.latestUpdate())),
                11, MUTED, 400);
        detail.setSingleLine(true);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        detail.setPadding(0, dp(2), 0, 0);
        labels.addView(detail);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(ACCENT);
        chevron.setPadding(dp(6), dp(6), dp(6), dp(6));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(24)));

        row.setOnClickListener(v -> openPatchApp(app));
        return row;
    }

    private List<PatchSourceCatalog.App> installedPatchApps() {
        if (patchCatalog == null) {
            return Collections.emptyList();
        }
        if (patchInstalledApps != null
                && patchCatalogHash != null
                && patchCatalogHash.equals(patchInstalledHash)) {
            return patchInstalledApps;
        }
        Set<String> launcherPackages = new HashSet<>();
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : getPackageManager()
                .queryIntentActivities(intent, 0)) {
            if (info.activityInfo != null) {
                launcherPackages.add(info.activityInfo.packageName);
            }
        }
        List<PatchSourceCatalog.App> installed = new ArrayList<>();
        for (String packageName : launcherPackages) {
            if (packageName.equals(getPackageName())) {
                continue;
            }
            PatchSourceCatalog.App app = patchCatalog.findApp(packageName);
            if (app != null) {
                installed.add(app);
            }
        }
        installed.sort((left, right) ->
                Long.compare(right.installs, left.installs));
        if (installed.size() > 4) {
            installed = new ArrayList<>(installed.subList(0, 4));
        }
        patchInstalledApps = installed;
        patchInstalledHash = patchCatalogHash;
        return installed;
    }

    private List<PatchSourceCatalog.App> recentPatchApps() {
        if (patchCatalog == null) {
            return Collections.emptyList();
        }
        List<PatchSourceCatalog.App> recent = new ArrayList<>();
        for (String packageName : PatchHistory.recent(this)) {
            PatchSourceCatalog.App app = patchCatalog.findApp(packageName);
            if (app != null) {
                recent.add(app);
            }
            if (recent.size() >= 4) {
                break;
            }
        }
        return recent;
    }

    private void openPatchApp(PatchSourceCatalog.App app) {
        PatchHistory.remember(this, app.packageName);
        patchSearch.setText(app.name);
        patchSearch.setSelection(patchSearch.length());
        InputMethodManager keyboard = (InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(patchSearch.getWindowToken(), 0);
        }
        final String packageName = app.packageName;
        patchHandler.postDelayed(() -> {
            LinearLayout header = patchHeaders.get(packageName);
            if (header != null) {
                header.performClick();
            }
        }, 350L);
    }

    private View patchAppEntry(PatchSourceCatalog.App app) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(12), dp(8), dp(12));
        header.setClickable(true);
        header.setFocusable(true);
        header.setBackground(M3Ui.ripple(this, null, palette.ripple));

        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                dp(42), dp(42));
        avatarParams.setMarginEnd(dp(12));
        header.addView(patchAvatar(app), avatarParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = M3Ui.text(this, app.name, 15, TEXT, 600);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(name);

        TextView packageChip = patchPackageChip(app.packageName);
        LinearLayout.LayoutParams packageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        packageParams.topMargin = dp(4);
        labels.addView(packageChip, packageParams);

        TextView detail = M3Ui.text(this, getString(
                R.string.patches_app_row_detail,
                getResources().getQuantityString(R.plurals.patches_source_count,
                        app.sources.size(), app.sources.size()),
                PatchSourceCatalog.formatRelativeDate(this, app.latestUpdate())),
                11, MUTED, 500);
        detail.setPadding(0, dp(4), 0, 0);
        labels.addView(detail);
        header.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(ACCENT);
        chevron.setPadding(dp(6), dp(6), dp(6), dp(6));
        header.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(24)));
        patchHeaders.put(app.packageName, header);
        container.addView(header);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));
        panel.setBackground(M3Ui.filled(this, M3Ui.surfaceContainer(palette),
                20, Math.max(1, dp(1)), palette.secondaryBorder));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        panelParams.leftMargin = dp(12);
        panelParams.rightMargin = dp(12);
        panelParams.topMargin = dp(2);
        panelParams.bottomMargin = dp(12);
        panel.setVisibility(View.GONE);
        container.addView(panel, panelParams);

        boolean expanded = app.packageName.equals(expandedPatchPackage);
        if (expanded) {
            populatePatchPanel(panel, app);
            panel.setVisibility(View.VISIBLE);
            expandedPatchPanel = panel;
            expandedPatchApp = app;
            expandedPatchChevron = chevron;
            chevron.setRotation(90f);
        }

        final LinearLayout panelRef = panel;
        final ImageView chevronRef = chevron;
        header.setOnClickListener(v ->
                togglePatchPanel(app, panelRef, chevronRef));
        return container;
    }

    private View patchAvatar(PatchSourceCatalog.App app) {
        PatchIconView icon = new PatchIconView(this, 42);
        icon.bind(app.iconUrl, app.name);
        return icon;
    }

    private TextView patchPackageChip(String packageName) {
        TextView chip = M3Ui.text(this, packageName, 11, ACCENT, 500);
        chip.setTypeface(Typeface.MONOSPACE);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setMaxWidth(dp(250));
        chip.setPadding(dp(9), dp(3), dp(9), dp(3));
        chip.setBackground(M3Ui.filled(this,
                M3Ui.surfaceContainerHigh(palette), M3Ui.SHAPE_FULL,
                Math.max(1, dp(1)), M3Ui.outlineVariant(palette)));
        return chip;
    }

    private void togglePatchPanel(PatchSourceCatalog.App app,
                                  LinearLayout panel, ImageView chevron) {
        ViewGroup parent = (ViewGroup) panel.getParent();
        TransitionManager.beginDelayedTransition(parent,
                new AutoTransition().setDuration(200));
        boolean collapsing = app.packageName.equals(expandedPatchPackage);
        if (!collapsing && expandedPatchPanel != null
                && expandedPatchPanel != panel) {
            expandedPatchPanel.setVisibility(View.GONE);
        }
        if (!collapsing && expandedPatchChevron != null
                && expandedPatchChevron != chevron) {
            expandedPatchChevron.animate().rotation(0f)
                    .setDuration(160).start();
        }
        if (collapsing) {
            panel.setVisibility(View.GONE);
            expandedPatchPackage = null;
            expandedPatchPanel = null;
            expandedPatchApp = null;
            expandedPatchChevron = null;
            chevron.animate().rotation(0f).setDuration(160).start();
            return;
        }
        if (panel.getChildCount() == 0) {
            populatePatchPanel(panel, app);
        }
        panel.setVisibility(View.VISIBLE);
        expandedPatchPackage = app.packageName;
        expandedPatchPanel = panel;
        expandedPatchApp = app;
        expandedPatchChevron = chevron;
        chevron.animate().rotation(90f).setDuration(180).start();
        PatchHistory.remember(this, app.packageName);
        scrollPatchPanelIntoView(panel);
    }

    private void scrollPatchPanelIntoView(View panel) {
        if (patchScroll == null) {
            return;
        }
        patchScroll.post(() -> {
            int top = 0;
            View current = panel;
            while (current != null && current != patchScroll) {
                top += current.getTop();
                if (!(current.getParent() instanceof View)) {
                    break;
                }
                current = (View) current.getParent();
            }
            patchScroll.smoothScrollTo(0, Math.max(0, top - dp(8)));
        });
    }

    private View patchDivider(int inset) {
        View divider = new View(this);
        divider.setBackgroundColor(M3Ui.outlineVariant(palette));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.leftMargin = inset;
        params.rightMargin = inset;
        divider.setLayoutParams(params);
        return divider;
    }

    private void populatePatchPanel(LinearLayout panel,
                                    PatchSourceCatalog.App app) {
        panel.removeAllViews();

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        if (app.sources.size() > 1) {
            TextView sortChip = patchControlChip(
                    getString(R.string.patches_sort_label, patchSortName())
                            + " \u25BE", true, null);
            sortChip.setOnClickListener(v ->
                    showPatchSortMenu(v, panel, app));
            LinearLayout.LayoutParams sortParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            sortParams.setMarginEnd(dp(8));
            controls.addView(sortChip, sortParams);
        }
        TextView officialChip = patchControlChip(
                getString(R.string.patches_official_only),
                patchOfficialOnly, () -> {
                    patchOfficialOnly = !patchOfficialOnly;
                    populatePatchPanel(panel, app);
                });
        controls.addView(officialChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView controlsScroll = new HorizontalScrollView(this);
        controlsScroll.setHorizontalScrollBarEnabled(false);
        controlsScroll.setClipToPadding(false);
        controlsScroll.addView(controls);
        LinearLayout.LayoutParams controlsParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        controlsParams.bottomMargin = dp(8);
        panel.addView(controlsScroll, controlsParams);

        boolean hasOfficial = false;
        for (PatchSourceCatalog.SourceEntry entry : app.sources) {
            if (entry.source.official) {
                hasOfficial = true;
                break;
            }
        }
        if (hasOfficial) {
            TextView note = M3Ui.text(this,
                    getString(R.string.patches_official_note), 10,
                    palette.footer, 400);
            note.setLineSpacing(dp(2), 1f);
            note.setPadding(0, 0, 0, dp(8));
            panel.addView(note);
        }

        List<PatchSourceCatalog.SourceEntry> entries =
                patchSourceEntries(app);
        if (entries.isEmpty()) {
            TextView empty = M3Ui.text(this, getString(
                    R.string.patches_no_official_sources, app.name),
                    12, MUTED, 400);
            empty.setLineSpacing(dp(2), 1f);
            empty.setPadding(0, dp(2), 0, dp(8));
            panel.addView(empty);
            TextView showAll = patchControlChip(
                    getString(R.string.patches_show_all), false, () -> {
                        patchOfficialOnly = false;
                        populatePatchPanel(panel, app);
                    });
            panel.addView(showAll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }

        for (PatchSourceCatalog.SourceEntry entry : entries) {
            LinearLayout.LayoutParams cardParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = dp(8);
            panel.addView(patchSourceCard(app, entry), cardParams);
        }
    }

    private List<PatchSourceCatalog.SourceEntry> patchSourceEntries(
            PatchSourceCatalog.App app) {
        List<PatchSourceCatalog.SourceEntry> entries = new ArrayList<>();
        for (PatchSourceCatalog.SourceEntry entry : app.sources) {
            if (!patchOfficialOnly || entry.source.official) {
                entries.add(entry);
            }
        }
        Comparator<PatchSourceCatalog.SourceEntry> byName =
                Comparator.comparing(entry -> entry.source.name,
                        String.CASE_INSENSITIVE_ORDER);
        switch (patchSortMode) {
            case PATCH_SORT_NAME:
                entries.sort(byName);
                break;
            case PATCH_SORT_UPDATED:
                entries.sort(Comparator
                        .comparingLong(
                                (PatchSourceCatalog.SourceEntry entry) ->
                                        entry.updatedAt)
                        .reversed()
                        .thenComparing(byName));
                break;
            case PATCH_SORT_PATCHES:
                entries.sort(Comparator
                        .comparingInt(
                                (PatchSourceCatalog.SourceEntry entry) ->
                                        entry.patchCount)
                        .reversed()
                        .thenComparing(byName));
                break;
            default:
                entries.sort(Comparator
                        .comparing(
                                (PatchSourceCatalog.SourceEntry entry) ->
                                        !entry.source.official)
                        .thenComparing(Comparator
                                .comparingLong(
                                        (PatchSourceCatalog.SourceEntry entry) ->
                                                entry.updatedAt)
                                .reversed())
                        .thenComparing(byName));
                break;
        }
        return entries;
    }

    private String patchSortName() {
        switch (patchSortMode) {
            case PATCH_SORT_NAME:
                return getString(R.string.patches_sort_name);
            case PATCH_SORT_UPDATED:
                return getString(R.string.patches_sort_updated);
            case PATCH_SORT_PATCHES:
                return getString(R.string.patches_sort_patches);
            default:
                return getString(R.string.patches_sort_recommended);
        }
    }

    private void showPatchSortMenu(View anchor, LinearLayout panel,
                                   PatchSourceCatalog.App app) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().setGroupCheckable(0, true, true);
        menu.getMenu().add(0, PATCH_SORT_RECOMMENDED, 0,
                getString(R.string.patches_sort_recommended));
        menu.getMenu().add(0, PATCH_SORT_NAME, 1,
                getString(R.string.patches_sort_name));
        menu.getMenu().add(0, PATCH_SORT_UPDATED, 2,
                getString(R.string.patches_sort_updated));
        menu.getMenu().add(0, PATCH_SORT_PATCHES, 3,
                getString(R.string.patches_sort_patches));
        menu.getMenu().findItem(patchSortMode).setChecked(true);
        menu.setOnMenuItemClickListener(item -> {
            int selected = item.getItemId();
            if (patchSortMode != selected) {
                patchSortMode = selected;
                populatePatchPanel(panel, app);
            }
            return true;
        });
        menu.show();
    }

    private TextView patchControlChip(String label, boolean selected,
                                      Runnable action) {
        TextView chip = M3Ui.chip(this, palette, label, selected);
        if (selected) {
            chip.setTextColor(palette.onPrimary);
            chip.setBackground(M3Ui.filled(this, ACCENT,
                    M3Ui.SHAPE_FULL, 0, 0));
        }
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setBackground(M3Ui.ripple(this, chip.getBackground(),
                palette.ripple));
        if (action != null) {
            chip.setOnClickListener(v -> action.run());
        }
        M3Ui.motion(chip);
        return chip;
    }

    private View patchSourceCard(PatchSourceCatalog.App app,
                                 PatchSourceCatalog.SourceEntry entry) {
        PatchSourceCatalog.Source source = entry.source;
        boolean official = source.official;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(11), dp(12), dp(11));
        card.setBackground(M3Ui.filled(this,
                official ? M3Ui.surfaceContainerHigh(palette)
                        : M3Ui.surfaceContainer(palette),
                18, Math.max(1, dp(1)),
                official ? ACCENT : M3Ui.outlineVariant(palette)));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        PatchIconView sourceIcon = new PatchIconView(this, 32);
        sourceIcon.bind(source.avatar, source.name);
        LinearLayout.LayoutParams sourceIconParams = new LinearLayout.LayoutParams(
                dp(32), dp(32));
        sourceIconParams.setMarginEnd(dp(10));
        top.addView(sourceIcon, sourceIconParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = M3Ui.text(this, source.name, 14, TEXT, 600);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(name);
        TextView repo = M3Ui.text(this, source.repo, 11, MUTED, 400);
        repo.setSingleLine(true);
        repo.setEllipsize(TextUtils.TruncateAt.END);
        repo.setPadding(0, dp(1), dp(8), 0);
        labels.addView(repo);
        if (!TextUtils.isEmpty(source.description)) {
            TextView description = M3Ui.text(this, source.description, 10,
                    palette.footer, 400);
            description.setSingleLine(true);
            description.setEllipsize(TextUtils.TruncateAt.END);
            description.setPadding(0, dp(2), dp(8), 0);
            labels.addView(description);
        }
        TextView updated = M3Ui.text(this, getString(
                        R.string.patches_source_updated,
                        PatchSourceCatalog.formatRelativeDate(this,
                                entry.updatedAt)),
                10, palette.footer, 400);
        updated.setPadding(0, dp(3), dp(8), 0);
        labels.addView(updated);
        top.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView add = M3Ui.outlinedButton(this, palette,
                getString(R.string.patches_add), 0, ACCENT);
        add.setPadding(dp(12), dp(7), dp(12), dp(7));
        add.setMinHeight(dp(44));
        add.setOnClickListener(v ->
                confirmAddPatchSource(app, source));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        addParams.setMarginStart(dp(8));
        top.addView(add, addParams);

        TextView more = M3Ui.text(this, "\u22EE", 18, MUTED, 700);
        more.setGravity(Gravity.CENTER);
        more.setContentDescription(getString(R.string.patches_source_actions));
        more.setPadding(dp(4), dp(4), dp(4), dp(4));
        more.setClickable(true);
        more.setFocusable(true);
        more.setBackground(M3Ui.ripple(this, null, palette.ripple));
        more.setOnClickListener(v -> showPatchSourceMenu(v, source, app));
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(
                dp(40), dp(44));
        moreParams.setMarginStart(dp(2));
        top.addView(more, moreParams);
        card.addView(top);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);

        TextView statusChip = patchMetaChip(official
                        ? getString(R.string.patches_source_official)
                        : getString(R.string.patches_source_community),
                official ? ACCENT : M3Ui.surfaceContainerHigh(palette),
                official ? palette.onPrimary : MUTED,
                official ? 0 : M3Ui.outlineVariant(palette));
        chips.addView(statusChip, patchChipParams());

        TextView countChip = patchMetaChip(
                getResources().getQuantityString(
                        R.plurals.patches_patch_count,
                        entry.patchCount, entry.patchCount) + " \u203A",
                M3Ui.surfaceContainer(palette), MUTED,
                M3Ui.outlineVariant(palette));
        countChip.setClickable(true);
        countChip.setFocusable(true);
        countChip.setContentDescription(getString(
                R.string.patches_view_details, entry.patchCount));
        countChip.setBackground(M3Ui.ripple(this, countChip.getBackground(),
                palette.ripple));
        countChip.setOnClickListener(v -> showPatchDetails(app, entry));
        chips.addView(countChip, patchChipParams());

        if (source.preRelease) {
            TextView preChip = patchMetaChip(
                    getString(R.string.patches_source_prerelease),
                    palette.warnBg, palette.warnText, palette.warnBorder);
            chips.addView(preChip, patchChipParams());
        }

        LinearLayout chipsLine = new LinearLayout(this);
        chipsLine.setOrientation(LinearLayout.HORIZONTAL);
        chipsLine.setGravity(Gravity.CENTER_VERTICAL);

        HorizontalScrollView chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsScroll.setClipToPadding(false);
        chipsScroll.addView(chips);
        chipsLine.addView(chipsScroll, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (source.stars > 0) {
            TextView starsChip = patchStarChip(source.stars);
            LinearLayout.LayoutParams starsParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            starsParams.setMarginStart(dp(6));
            chipsLine.addView(starsChip, starsParams);
        }

        LinearLayout.LayoutParams chipsParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        chipsParams.topMargin = dp(8);
        card.addView(chipsLine, chipsParams);
        return card;
    }

    private TextView patchStarChip(int stars) {
        TextView chip = M3Ui.text(this,
                "\u2605 " + PatchSourceCatalog.formatCount(this, stars),
                11, palette.onPrimary, 600);
        chip.setSingleLine(true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(9), dp(4), dp(9), dp(4));
        chip.setBackground(M3Ui.brandGradient(this, palette,
                M3Ui.SHAPE_FULL));
        return chip;
    }

    private void showPatchSourceMenu(View anchor,
                                     PatchSourceCatalog.Source source,
                                     PatchSourceCatalog.App app) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, getString(R.string.patches_source_view_repo));
        menu.getMenu().add(0, 2, 1, getString(R.string.patches_source_copy_link));
        menu.getMenu().add(0, 3, 2, getString(R.string.patches_source_report));
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    openUrl(sourceRepoUrl(source));
                    return true;
                case 2:
                    boolean copied = copyText(PatchSourceCatalog.addSourceLink(
                            source, app));
                    Toast.makeText(this, copied
                                    ? R.string.patches_link_copied
                                    : R.string.patches_link_copy_failed,
                            Toast.LENGTH_SHORT).show();
                    return true;
                case 3:
                    reportPatchSource(source, app);
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private String sourceRepoUrl(PatchSourceCatalog.Source source) {
        String host = "gitlab".equals(source.provider)
                ? "https://gitlab.com/" : "https://github.com/";
        return host + source.repo;
    }

    private void reportPatchSource(PatchSourceCatalog.Source source,
                                   PatchSourceCatalog.App app) {
        String title = "Source report: " + source.repo + " for " + app.name;
        String body = "Source: " + source.repo
                + "\nApp: " + app.name + " (" + app.packageName + ")"
                + "\n\nWhat is wrong?\n";
        String url = "https://github.com/GeneralZyonix/morphe-download-helper/"
                + "issues/new?title=" + Uri.encode(title)
                + "&body=" + Uri.encode(body);
        openUrl(url);
    }

    private void showPatchDetails(PatchSourceCatalog.App app,
                                  PatchSourceCatalog.SourceEntry entry) {
        Toast.makeText(this, R.string.patches_details_loading,
                Toast.LENGTH_SHORT).show();
        PatchDetailsCatalog.load(this, entry.source.repo, app.packageName,
                new PatchDetailsCatalog.Callback() {
                    @Override
                    public void onDetails(List<PatchDetailsCatalog.Detail> details) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        showPatchDetailsDialog(app, entry, details);
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        Toast.makeText(MorphePreviewActivity.this,
                                R.string.patches_details_failed,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showPatchDetailsDialog(PatchSourceCatalog.App app,
                                        PatchSourceCatalog.SourceEntry entry,
                                        List<PatchDetailsCatalog.Detail> details) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(4), dp(20), dp(8));

        TextView intro = M3Ui.text(this, getString(
                R.string.patches_details_intro,
                entry.source.name, app.name), 12, MUTED, 400);
        intro.setLineSpacing(dp(2), 1f);
        intro.setPadding(0, 0, 0, dp(8));
        content.addView(intro);

        if (details.isEmpty()) {
            TextView empty = M3Ui.text(this,
                    getString(R.string.patches_details_empty), 13, MUTED, 400);
            empty.setPadding(0, dp(12), 0, dp(12));
            content.addView(empty);
        } else {
            for (int i = 0; i < details.size(); i++) {
                PatchDetailsCatalog.Detail detail = details.get(i);
                TextView name = M3Ui.text(this, detail.name, 13, TEXT, 600);
                name.setLineSpacing(dp(2), 1f);
                content.addView(name);
                TextView versions = M3Ui.text(this,
                        versionsLabel(detail.versions), 11, MUTED, 400);
                versions.setLineSpacing(dp(2), 1f);
                versions.setPadding(0, dp(2), 0, dp(10));
                content.addView(versions);
                if (i < details.size() - 1) {
                    content.addView(patchDivider(0));
                }
            }
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        ThemeManager.dialog(this)
                .setTitle(getString(R.string.patches_details_title,
                        entry.source.name))
                .setView(scroll)
                .setNegativeButton(R.string.common_close, null)
                .show();
    }

    private String versionsLabel(List<String> versions) {
        if (versions.isEmpty()) {
            return getString(R.string.patches_all_versions);
        }
        int shown = Math.min(4, versions.size());
        StringBuilder label = new StringBuilder(TextUtils.join(", ",
                versions.subList(0, shown)));
        if (versions.size() > shown) {
            label.append(" +").append(versions.size() - shown);
        }
        return getString(R.string.patches_targets, label.toString());
    }

    private LinearLayout.LayoutParams patchChipParams() {        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dp(6));
        return params;
    }

    private TextView patchMetaChip(String label, int fill, int foreground,
                                   int borderColor) {
        TextView chip = M3Ui.text(this, label, 11, foreground, 500);
        chip.setSingleLine(true);
        chip.setPadding(dp(9), dp(4), dp(9), dp(4));
        chip.setBackground(M3Ui.filled(this, fill, M3Ui.SHAPE_FULL,
                borderColor == 0 ? 0 : Math.max(1, dp(1)), borderColor));
        return chip;
    }

    private void confirmAddPatchSource(PatchSourceCatalog.App app,
                                       PatchSourceCatalog.Source source) {
        if (patchCatalog == null || patchCatalog.isStale()) {
            Toast.makeText(this, R.string.patches_stale_blocked,
                    Toast.LENGTH_LONG).show();
            refreshPatchCatalog(true);
            return;
        }
        ThemeManager.dialog(this)
                .setTitle(R.string.patches_confirm_title)
                .setMessage(getString(R.string.patches_confirm_message,
                        source.name, app.name))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.patches_open, (dialog, which) ->
                        verifyAndAddPatchSource(app, source))
                .show();
    }

    /**
     * Re-checks the catalog before handing off to Morphe. The cached copy is
     * never enough here: the source must still exist for the app in a catalog
     * that the network has just confirmed.
     */
    private void verifyAndAddPatchSource(PatchSourceCatalog.App app,
                                         PatchSourceCatalog.Source source) {
        Toast.makeText(this, R.string.patches_verifying,
                Toast.LENGTH_SHORT).show();
        PatchSourceCatalog.load(this, new PatchSourceCatalog.Callback() {
            @Override
            public void onCatalog(PatchSourceCatalog catalog,
                                  boolean checkedNetwork) {
                if (!checkedNetwork || isFinishing() || isDestroyed()) {
                    return;
                }
                patchCatalog = catalog;
                patchLastLoadedAt = System.currentTimeMillis();
                updatePatchStatus(true);
                renderPatchResults(true);
                PatchSourceCatalog.App freshApp =
                        catalog.findApp(app.packageName);
                PatchSourceCatalog.SourceEntry freshEntry = freshApp == null
                        ? null : freshApp.findSource(source.repo);
                if (freshEntry == null) {
                    Toast.makeText(MorphePreviewActivity.this,
                            getString(R.string.patches_source_gone, app.name),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                openMorpheSource(PatchSourceCatalog.addSourceLink(
                        freshEntry.source, freshApp));
            }

            @Override
            public void onError(String message, PatchSourceCatalog cached) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                Toast.makeText(MorphePreviewActivity.this,
                        R.string.patches_verify_failed,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void refreshPatchCatalog(boolean force) {
        if (patchLoading) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && patchLastLoadedAt > 0
                && now - patchLastLoadedAt < 30_000L) {
            return;
        }
        patchLoading = true;
        if (patchStatus != null && patchCatalog == null) {
            setPatchStatusTone(MUTED, MUTED);
            patchStatus.setText(R.string.patches_status_loading);
        }
        if (patchRetry != null) {
            patchRetry.setVisibility(View.GONE);
        }
        PatchSourceCatalog.load(this, new PatchSourceCatalog.Callback() {
            @Override
            public void onCatalog(PatchSourceCatalog catalog,
                                  boolean checkedNetwork) {
                patchLoading = false;
                patchLastLoadedAt = System.currentTimeMillis();
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                boolean sameData = patchCatalog != null && patchHasRendered
                        && catalog.dataHash.equals(patchCatalogHash);
                patchCatalog = catalog;
                patchCatalogHash = catalog.dataHash;
                updatePatchStatus(checkedNetwork);
                if (patchRetry != null) {
                    patchRetry.setVisibility(View.GONE);
                }
                if (!sameData) {
                    patchInstalledApps = null;
                    patchInstalledHash = null;
                    renderPatchResults(true);
                }
            }

            @Override
            public void onError(String message, PatchSourceCatalog cached) {
                patchLoading = false;
                patchLastLoadedAt = System.currentTimeMillis();
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (cached != null) {
                    patchCatalog = cached;
                    patchCatalogHash = cached.dataHash;
                }
                if (patchRetry != null) {
                    patchRetry.setVisibility(View.VISIBLE);
                }
                if (patchStatus != null) {
                    if (patchCatalog == null) {
                        setPatchStatusTone(RED, RED);
                        patchStatus.setText(R.string.patches_status_error);
                    } else if (patchCatalog.isStale()) {
                        setPatchStatusTone(RED, RED);
                        patchStatus.setText(getString(
                                R.string.patches_status_stale_error,
                                PatchSourceCatalog.formatDate(
                                        MorphePreviewActivity.this,
                                        patchCatalog.generatedAt)));
                    } else {
                        setPatchStatusTone(RED, RED);
                        patchStatus.setText(getString(
                                R.string.patches_status_error_cached,
                                PatchSourceCatalog.formatDate(
                                        MorphePreviewActivity.this,
                                        patchCatalog.generatedAt)));
                    }
                }
                renderPatchResults(true);
            }
        });
    }

    private void updatePatchStatus(boolean checkedNetwork) {
        if (patchStatus == null || patchCatalog == null) {
            return;
        }
        if (patchRetry != null) {
            patchRetry.setVisibility(View.GONE);
        }
        String date = PatchSourceCatalog.formatDate(this,
                patchCatalog.generatedAt);
        if (patchCatalog.isStale()) {
            setPatchStatusTone(RED, RED);
            patchStatus.setText(getString(R.string.patches_status_stale, date));
            return;
        }
        setPatchStatusTone(MUTED, checkedNetwork ? ACCENT : MUTED);
        patchStatus.setText(checkedNetwork
                ? getString(R.string.patches_status_updated, date,
                getResources().getQuantityString(R.plurals.patches_source_count,
                        patchCatalog.sources.size(),
                        patchCatalog.sources.size()),
                getResources().getQuantityString(R.plurals.patches_app_count,
                        patchCatalog.apps.size(), patchCatalog.apps.size()))
                : getString(R.string.patches_status_cached, date));
    }

    private void setPatchStatusTone(int textColor, int dotColor) {
        if (patchStatus != null) {
            patchStatus.setTextColor(textColor);
        }
        if (patchStatusDot != null) {
            patchStatusDot.setBackground(M3Ui.filled(this, dotColor,
                    M3Ui.SHAPE_FULL, 0, 0));
        }
    }

    private View buildSettings() {
        ScrollView scroll = tabScroll();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.addView(M3Ui.overline(this,
                getString(R.string.settings_overline), ACCENT));
        TextView title = M3Ui.display(this,
                getString(R.string.preview_settings), 32, TEXT);
        title.setPadding(0, dp(8), 0, 0);
        header.addView(title);
        TextView subtitle = M3Ui.text(this,
                getString(R.string.settings_subtitle), 13, MUTED, 400);
        subtitle.setPadding(0, dp(6), 0, 0);
        header.addView(subtitle);
        TextView hint = M3Ui.text(this,
                getString(R.string.preview_settings_hint), 11, MUTED, 400);
        hint.setPadding(0, dp(8), 0, dp(2));
        header.addView(hint);

        addSettingsActionCard(root, R.drawable.ic_section_general,
                R.string.preview_settings_appearance,
                R.string.appearance_subtitle,
                () -> startActivity(new Intent(this, AppearanceActivity.class)));
        addSettingsCard(root, R.drawable.ic_section_updates,
                R.string.settings_updates, R.string.settings_updates_summary,
                "updates");
        addSettingsCard(root, R.drawable.ic_section_web,
                R.string.settings_web_pages, R.string.settings_web_pages_summary,
                "web");
        addSettingsCard(root, R.drawable.ic_section_sources,
                R.string.settings_sources, R.string.settings_sources_summary,
                "sources");
        addSettingsCard(root, R.drawable.ic_section_storage,
                R.string.settings_storage, R.string.settings_storage_summary,
                "storage");
        addSettingsCard(root, R.drawable.ic_section_security,
                R.string.preview_security, R.string.settings_security_summary,
                "security");
        addSettingsCard(root, R.drawable.ic_section_battery,
                R.string.preview_background, R.string.settings_background_summary,
                "battery");
        addSettingsCard(root, R.drawable.ic_section_backup,
                R.string.settings_backup, R.string.settings_backup_summary,
                "backup");

        addBottomSpacer(root);
        return stickyTab(header, scroll, subtitle, hint);
    }

    private void addSettingsCard(LinearLayout root, int iconRes, int titleRes,
                                 int summaryRes, String section) {
        addSettingsActionCard(root, iconRes, titleRes, summaryRes,
                () -> openSectionSheet(section));
    }

    private void addSettingsActionCard(LinearLayout root, int iconRes, int titleRes,
                                       int summaryRes, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(M3Ui.card(this, palette));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(12);
        root.addView(card, cardParams);
        card.addView(settingsCardHeader(iconRes, getString(titleRes),
                getString(summaryRes), action));
    }

    private LinearLayout settingsCardHeader(int iconRes, String title, String summary,
                                            Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(16), dp(12), dp(16));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(palette.ripple),
                null, null));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> action.run());

        FrameLayout iconTile = new FrameLayout(this);
        iconTile.setBackground(M3Ui.brandGradient(this, palette, 14));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(palette.onPrimary);
        FrameLayout.LayoutParams iconInner = new FrameLayout.LayoutParams(dp(22), dp(22));
        iconInner.gravity = Gravity.CENTER;
        iconTile.addView(icon, iconInner);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        iconParams.setMarginEnd(dp(14));
        row.addView(iconTile, iconParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(M3Ui.title(this, title, 17, TEXT));
        TextView detail = M3Ui.text(this, summary, 12, MUTED, 400);
        detail.setPadding(0, dp(3), 0, 0);
        labels.addView(detail);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(MUTED);
        chevron.setPadding(dp(6), dp(6), dp(6), dp(6));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(32), dp(32)));
        return row;
    }

    /** Opens a settings section as a sheet over the shell, not a new screen. */
    private void openSectionSheet(String section) {
        Intent intent = new Intent(this, SettingsSheetActivity.class);
        intent.putExtra(SettingsActivity.EXTRA_SECTION, section);
        intent.putExtra(SettingsActivity.EXTRA_SHEET_ONLY, true);
        startActivity(intent);
    }

    private void openSettings(String section) {
        Intent intent = new Intent(this, SettingsActivity.class);
        if (!TextUtils.isEmpty(section)) {
            intent.putExtra(SettingsActivity.EXTRA_SECTION, section);
        }
        startActivity(intent);
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private ScrollView tabScroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        int screen = getResources().getDisplayMetrics().widthPixels;
        int horizontal = Math.max(dp(20), (screen - dp(720)) / 2);
        scroll.setPadding(horizontal, dp(18), horizontal, dp(24));
        return scroll;
    }

    /**
     * Keeps the page header fixed above a scrolling body. Optional views (such
     * as the subtitle and hint) collapse once the body starts scrolling.
     */
    private View stickyTab(View header, ScrollView scroll, View... collapsing) {
        int screen = getResources().getDisplayMetrics().widthPixels;
        int horizontal = Math.max(dp(20), (screen - dp(720)) / 2);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        FrameLayout headerHost = new FrameLayout(this);
        headerHost.setPadding(horizontal, dp(18), horizontal, dp(8));
        headerHost.addView(header, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(headerHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(this);
        divider.setBackgroundColor(M3Ui.outlineVariant(palette));
        page.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        final boolean[] compact = {false};
        scroll.setOnScrollChangeListener((view, scrollX, scrollY,
                                          oldScrollX, oldScrollY) -> {
            boolean next = scrollY > dp(18);
            if (next == compact[0]) {
                return;
            }
            compact[0] = next;
            if (collapsing.length > 0) {
                TransitionManager.beginDelayedTransition(headerHost,
                        new AutoTransition().setDuration(160));
                for (View part : collapsing) {
                    if (part != null) {
                        part.setVisibility(next ? View.GONE : View.VISIBLE);
                    }
                }
            }
        });

        scroll.setPadding(horizontal, 0, horizontal, dp(24));
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    /**
     * Adds real height after the last row. ScrollView padding alone does not
     * create a scroll range when the content is shorter than the viewport, so
     * the floating navigation bar could cover the last row.
     */
    private void addBottomSpacer(LinearLayout root) {
        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(130)));
    }

    private ImageView iconButton(int iconRes, int descRes, Runnable action) {
        ImageView button = new ImageView(this);
        button.setImageResource(iconRes);
        button.setColorFilter(TEXT);
        button.setContentDescription(getString(descRes));
        button.setPadding(dp(9), dp(9), dp(9), dp(9));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(palette.ripple),
                M3Ui.filled(this, M3Ui.secondaryContainer(palette),
                        M3Ui.SHAPE_FULL, 0, 0), null));
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void addGlow(FrameLayout root, int color, int gravity,
                         int x, int y, int radius) {
        View glow = new View(this);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        drawable.setColors(new int[]{
                Color.argb(48, Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT
        });
        drawable.setGradientRadius(dp(radius));
        glow.setBackground(drawable);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(radius * 2), dp(radius * 2));
        params.gravity = gravity;
        params.leftMargin = x;
        params.topMargin = y;
        root.addView(glow, params);
    }

    /** Full helper-log action sheet, matching the classic home screen actions. */
    private void showLogSheet() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(10), dp(20), dp(24));
        content.setBackground(sheetBackground());
        content.setClickable(true);
        content.setFocusable(true);

        FrameLayout handleArea = new FrameLayout(this);
        handleArea.setContentDescription(getString(R.string.settings_sheet_drag_hint));
        LinearLayout.LayoutParams handleAreaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
        handleAreaParams.bottomMargin = dp(2);
        content.addView(handleArea, handleAreaParams);

        View handle = new View(this);
        GradientDrawable handleBackground = new GradientDrawable();
        handleBackground.setColor(M3Ui.outline(palette));
        handleBackground.setCornerRadius(dp(999));
        handle.setBackground(handleBackground);
        handle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(
                dp(44), dp(4), Gravity.CENTER);
        handleArea.addView(handle, handleParams);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout iconTile = new FrameLayout(this);
        iconTile.setBackground(M3Ui.filled(this,
                M3Ui.primaryContainer(palette), 12, 0, 0));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_log_doc);
        icon.setColorFilter(M3Ui.onPrimaryContainer(palette));
        FrameLayout.LayoutParams iconInner = new FrameLayout.LayoutParams(dp(20), dp(20));
        iconInner.gravity = Gravity.CENTER;
        iconTile.addView(icon, iconInner);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconParams.setMarginEnd(dp(12));
        titleRow.addView(iconTile, iconParams);
        titleRow.addView(M3Ui.display(this, getString(R.string.main_log_title), 24, TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView close = iconButton(R.drawable.ic_close, R.string.common_close,
                dialog::dismiss);
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        content.addView(titleRow);

        TextView subtitle = M3Ui.text(this,
                getString(R.string.main_logs_local), 12, MUTED, 400);
        subtitle.setPadding(0, dp(4), 0, 0);
        content.addView(subtitle);
        TextView sanitizedNote = M3Ui.text(this,
                getString(R.string.preview_log_sanitized_note), 11, MUTED, 400);
        sanitizedNote.setPadding(0, dp(2), 0, dp(10));
        content.addView(sanitizedNote);

        content.addView(logRow(R.drawable.ic_share, R.string.main_share_log,
                TEXT, () -> {
                    dialog.dismiss();
                    shareLog();
                }));
        content.addView(logRow(R.drawable.ic_copy, R.string.main_copy_log,
                TEXT, () -> {
                    dialog.dismiss();
                    copyLog();
                }));
        content.addView(logRow(R.drawable.ic_save, R.string.main_save_log,
                TEXT, () -> {
                    dialog.dismiss();
                    saveLog();
                }));
        content.addView(logRow(R.drawable.ic_log_doc, R.string.main_preview_log,
                TEXT, () -> {
                    dialog.dismiss();
                    showLogDialog();
                }));
        content.addView(logDivider());
        content.addView(logRow(R.drawable.source_logo_github, R.string.main_report_github,
                TEXT, () -> {
                    dialog.dismiss();
                    showGitHubReportDialog();
                }));
        content.addView(logRow(R.drawable.source_logo_github,
                R.string.main_suggest_feature, TEXT, () -> {
                    dialog.dismiss();
                    openUrl(FEATURE_REQUEST_URL);
                }));
        content.addView(logDivider());
        content.addView(logRow(R.drawable.ic_delete, R.string.main_clear_log,
                RED, () -> {
                    HelperLog.clear();
                    Toast.makeText(this, R.string.main_log_cleared,
                            Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }));

        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(dp(20), dp(10), dp(20), dp(24) + bottom);
            return insets;
        });

        FrameLayout windowRoot = new FrameLayout(this);
        windowRoot.setClipChildren(false);
        windowRoot.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        dialog.setContentView(windowRoot);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.62f);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.BOTTOM);
            window.setNavigationBarColor(M3Ui.surfaceContainer(palette));
            window.setStatusBarColor(M3Ui.surface(palette));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.setNavigationBarDividerColor(M3Ui.surfaceContainer(palette));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setNavigationBarContrastEnforced(false);
            }
            WindowInsetsControllerCompat controller =
                    ViewCompat.getWindowInsetsController(window.getDecorView());
            if (controller != null) {
                controller.setAppearanceLightNavigationBars(palette.light);
                controller.setAppearanceLightStatusBars(palette.light);
            }
        }
        windowRoot.setOnClickListener(v -> dialog.dismiss());
        attachLogSheetDrag(handleArea, content, dialog);

        content.setTranslationY(getResources().getDisplayMetrics().heightPixels);
        content.post(() -> content.animate()
                .translationY(0f)
                .setDuration(260L)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start());
    }

    /** Drag the log sheet handle down to dismiss it. */
    private void attachLogSheetDrag(View handle, LinearLayout sheet, Dialog dialog) {
        final float[] downY = new float[1];
        final float[] downTranslation = new float[1];
        handle.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY[0] = event.getRawY();
                    downTranslation[0] = sheet.getTranslationY();
                    sheet.animate().cancel();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float offset = event.getRawY() - downY[0];
                    sheet.setTranslationY(Math.max(0f, downTranslation[0] + offset));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float height = sheet.getHeight();
                    float threshold = height > 0 ? height * 0.22f : dp(160);
                    if (sheet.getTranslationY() > threshold) {
                        float distance = height > 0
                                ? height
                                : getResources().getDisplayMetrics().heightPixels;
                        sheet.animate()
                                .translationY(distance)
                                .setDuration(220L)
                                .setInterpolator(new DecelerateInterpolator(1.2f))
                                .withEndAction(dialog::dismiss)
                                .start();
                    } else {
                        sheet.animate()
                                .translationY(0f)
                                .setDuration(220L)
                                .setInterpolator(new DecelerateInterpolator(1.6f))
                                .start();
                    }
                    return true;
                default:
                    return false;
            }
        });
        handle.setClickable(true);
    }

    private View logRow(int iconRes, int titleRes, int titleColor, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(10), dp(8), dp(10));
        row.setBackground(new RippleDrawable(
                ColorStateList.valueOf(palette.ripple), null, null));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> action.run());

        boolean danger = titleColor == RED;
        FrameLayout iconTile = new FrameLayout(this);
        iconTile.setBackground(M3Ui.filled(this,
                danger ? M3Ui.errorContainer(palette) : M3Ui.secondaryContainer(palette),
                12, 0, 0));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(danger ? RED : M3Ui.onPrimaryContainer(palette));
        FrameLayout.LayoutParams iconInner = new FrameLayout.LayoutParams(dp(20), dp(20));
        iconInner.gravity = Gravity.CENTER;
        iconTile.addView(icon, iconInner);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconParams.setMarginEnd(dp(12));
        row.addView(iconTile, iconParams);

        row.addView(M3Ui.text(this, getString(titleRes), 14, titleColor, 600),
                new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View logDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(M3Ui.outlineVariant(palette));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.leftMargin = dp(52);
        params.topMargin = dp(6);
        params.bottomMargin = dp(6);
        divider.setLayoutParams(params);
        return divider;
    }

    private GradientDrawable sheetBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(M3Ui.surfaceContainer(palette));
        float radius = dp(28);
        background.setCornerRadii(new float[]{
                radius, radius, radius, radius, 0, 0, 0, 0});
        return background;
    }

    private String sanitizedLog() {
        File log = HelperLog.getLogFile();
        if (log == null || !log.isFile() || log.length() == 0) {
            return "";
        }
        try {
            return LogSanitizer.sanitizeForExport(readTail(log, 256000));
        } catch (Exception error) {
            return "";
        }
    }

    private void shareLog() {
        String text = sanitizedLog();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, R.string.main_report_no_log,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.main_log_subject));
        intent.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(intent,
                    getString(R.string.main_share_log_title)));
        } catch (Exception error) {
            Toast.makeText(this, R.string.main_error_share_log,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void copyLog() {
        String text = sanitizedLog();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, R.string.main_report_no_log,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, R.string.main_error_copy_log,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                getString(R.string.main_log_title), text));
        Toast.makeText(this, R.string.main_log_copied,
                Toast.LENGTH_SHORT).show();
    }

    private void saveLog() {
        File log = HelperLog.getLogFile();
        if (log == null || !log.isFile()) {
            Toast.makeText(this, R.string.main_error_no_log,
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "helper-sanitized.log");
        startActivityForResult(intent, REQ_SAVE_LOG);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SAVE_LOG && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            saveLogTo(data.getData());
        }
    }

    private void saveLogTo(Uri uri) {
        File log = HelperLog.getLogFile();
        if (log == null || !log.isFile()) {
            Toast.makeText(this, R.string.main_error_no_log,
                    Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            try {
                String text = LogSanitizer.sanitizeForExport(
                        readTail(log, 300_000));
                if (text.trim().isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this,
                            R.string.main_error_no_log, Toast.LENGTH_LONG).show());
                    return;
                }
                try (OutputStream output =
                             getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) {
                        throw new IllegalStateException("No output stream");
                    }
                    output.write(text.getBytes(StandardCharsets.UTF_8));
                }
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.main_save_log_success, Toast.LENGTH_SHORT).show());
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.main_save_log_failed, Toast.LENGTH_LONG).show());
            }
        }, "save-log").start();
    }

    /** Lets the user choose a short report or the full sanitized log. */
    private void showGitHubReportDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(4), dp(24), dp(4));

        TextView message = M3Ui.text(this,
                getString(R.string.main_report_message), 13, MUTED, 400);
        message.setLineSpacing(dp(2), 1f);
        content.addView(message);

        TextView shortReport = reportChoiceButton(
                getString(R.string.main_report_short));
        LinearLayout.LayoutParams shortParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shortParams.topMargin = dp(14);
        content.addView(shortReport, shortParams);

        TextView fullReport = reportChoiceButton(
                getString(R.string.main_report_full));
        LinearLayout.LayoutParams fullParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fullParams.topMargin = dp(10);
        content.addView(fullReport, fullParams);

        AlertDialog dialog = ThemeManager.dialog(this)
                .setTitle(R.string.main_report_title)
                .setView(content)
                .setNegativeButton(R.string.common_cancel, null)
                .show();
        shortReport.setOnClickListener(v -> {
            dialog.dismiss();
            reportToGitHub(false);
        });
        fullReport.setOnClickListener(v -> {
            dialog.dismiss();
            reportToGitHub(true);
        });
    }

    private TextView reportChoiceButton(String label) {
        TextView view = M3Ui.outlinedButton(this, palette, label, 0, TEXT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(14), dp(12), dp(14), dp(12));
        view.setSingleLine(false);
        return view;
    }

    /**
     * Builds a GitHub issue with a redacted log excerpt. The full option saves
     * a sanitized copy and copies the redacted log when it is too large to
     * prefill.
     */
    private void reportToGitHub(boolean fullLog) {
        File logFile = HelperLog.getLogFile();
        if (logFile == null || !logFile.isFile()) {
            Toast.makeText(this, R.string.main_report_no_log,
                    Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            String raw;
            try {
                raw = readTail(logFile, 300_000);
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.main_report_no_log, Toast.LENGTH_LONG).show());
                return;
            }
            String redacted = GitHubReport.redact(raw);
            if (redacted.trim().isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.main_report_no_log, Toast.LENGTH_LONG).show());
                return;
            }
            boolean fullPrefilled = fullLog
                    && GitHubReport.canPrefillFullLog(redacted);
            boolean saved = fullLog && AppSettings.saveLogCopy(this,
                    LogSanitizer.sanitizeForExport(raw));
            String targetApp = GitHubReport.lastRequest(redacted);
            if (TextUtils.isEmpty(targetApp)) {
                targetApp = getString(R.string.main_report_target_unknown);
            }
            String androidDevice = Build.MANUFACTURER + " " + Build.MODEL
                    + ", Android " + Build.VERSION.RELEASE
                    + " (SDK " + Build.VERSION.SDK_INT + ")";
            String morphe = morpheVersion().isEmpty()
                    ? getString(R.string.main_report_morphe_missing)
                    : morpheVersion();
            final String finalTarget = targetApp;
            final String finalMorphe = morphe;
            final String finalDevice = androidDevice;
            final String finalRedacted = redacted;
            final boolean finalSaved = saved;
            final boolean finalFullPrefilled = fullPrefilled;
            runOnUiThread(() -> {
                boolean copied = false;
                boolean logIsCode;
                String logField;
                String toast = null;
                if (!fullLog) {
                    logField = GitHubReport.excerpt(finalRedacted);
                    logIsCode = true;
                } else if (finalFullPrefilled) {
                    logField = finalRedacted;
                    logIsCode = true;
                    if (finalSaved) {
                        toast = getString(R.string.main_report_full_saved);
                    }
                } else {
                    copied = copyText(finalRedacted);
                    if (copied) {
                        logField = getString(R.string.main_report_full_field_note);
                        logIsCode = false;
                        toast = finalSaved
                                ? getString(R.string.main_report_full_saved)
                                : getString(R.string.main_report_save_failed);
                    } else if (finalSaved) {
                        logField = getString(
                                R.string.main_report_full_saved_field_note);
                        logIsCode = false;
                        toast = getString(R.string.main_report_full_saved);
                    } else {
                        logField = GitHubReport.excerpt(finalRedacted);
                        logIsCode = true;
                        toast = getString(R.string.main_report_full_failed_short);
                    }
                }
                String url = GitHubReport.buildUrl(helperVersion(), finalDevice,
                        finalMorphe, finalTarget, logField, logIsCode);
                if (toast != null) {
                    Toast.makeText(this, toast, Toast.LENGTH_LONG).show();
                }
                openUrl(url);
            });
        }, "github-report").start();
    }

    private boolean copyText(String text) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return false;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                getString(R.string.main_log_title), text));
        return true;
    }

    private void showLogDialog() {
        String content = getString(R.string.preview_log_empty);
        File log = HelperLog.getLogFile();
        if (log != null && log.isFile() && log.length() > 0) {
            try {
                content = LogSanitizer.sanitize(readTail(log, 16000));
            } catch (Exception error) {
                content = getString(R.string.preview_log_empty);
            }
        }
        TextView view = M3Ui.text(this, content, 11, TEXT, 400);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextIsSelectable(true);
        view.setPadding(dp(16), dp(10), dp(16), dp(10));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(view);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));
        ThemeManager.dialog(this)
                .setTitle(R.string.main_log_title)
                .setView(scroll)
                .setPositiveButton(R.string.common_close, null)
                .show();
    }

    private String readTail(File file, int maxBytes) throws Exception {
        long length = file.length();
        long start = Math.max(0, length - maxBytes);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(start);
            byte[] bytes = new byte[(int) (length - start)];
            raf.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private void openMorphe() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(MORPHE_PACKAGE);
        if (launch == null) {
            launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(MORPHE_PACKAGE);
        }
        try {
            startActivity(launch);
        } catch (Exception error) {
            Toast.makeText(this, R.string.main_error_morphe_missing,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception error) {
            Toast.makeText(this, R.string.main_error_open_morphe,
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Sends the add-source link to Morphe itself. Domain verification is not
     * guaranteed for every Morphe build, so an unqualified VIEW intent can land
     * in the browser. Falls back to the browser when Morphe does not handle it.
     */
    private void openMorpheSource(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setPackage(MORPHE_PACKAGE);
        try {
            startActivity(intent);
        } catch (Exception error) {
            openUrl(url);
        }
    }

    private String morpheVersion() {
        try {
            String version = getPackageManager()
                    .getPackageInfo(MORPHE_PACKAGE, 0).versionName;
            return version == null ? "" : version.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String helperVersion() {
        try {
            String version = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            return version == null ? "?" : version.trim();
        } catch (Exception ignored) {
            return "?";
        }
    }

    private String formatSize(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
        }
        if (bytes >= 1024L) {
            return String.format(Locale.US, "%.0f KB", bytes / 1024f);
        }
        return bytes + " B";
    }

    private String formatDate(long millis) {
        return DateFormat.getDateInstance(DateFormat.MEDIUM)
                .format(new Date(millis));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
