package be.alterra.morphehelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Intent;
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
import android.provider.MediaStore;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v3 design prototype: a four-tab Morphe shell with Home, Downloads, Sources,
 * and Settings.
 *
 * <p>This activity is intentionally separate from {@link MainActivity}. It lets
 * the new layout be reviewed and installed without changing the current release
 * screen. The tabs use the same saved theme, accent, source settings, and
 * download folder as the rest of the app.</p>
 */
public class MorphePreviewActivity extends Activity {

    private static final int TAB_HOME = 0;
    private static final int TAB_DOWNLOADS = 1;
    private static final int TAB_SOURCES = 2;
    private static final int TAB_SETTINGS = 3;
    private static final String STATE_TAB = "selected_tab";
    private static final String MORPHE_PACKAGE = "app.morphe.manager";
    private static final int REQ_SAVE_LOG = 6103;
    private static final String FEATURE_REQUEST_URL =
            "https://github.com/GeneralZyonix/morphe-download-helper/issues/new"
                    + "?template=feature_request.yml";

    private ThemePalette palette;
    private FrameLayout contentHost;
    private LinearLayout navBar;
    private final LinearLayout[] navItems = new LinearLayout[4];
    private final ImageView[] navIcons = new ImageView[4];
    private final TextView[] navLabels = new TextView[4];
    private final View[] tabs = new View[4];
    private int selectedTab = TAB_HOME;
    private String themeSignature;

    private int TEXT;
    private int MUTED;
    private int ACCENT;
    private int GREEN;
    private int RED;
    private int CARD;
    private int CARD_HIGH;

    private LinearLayout sourceList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PublicSettings.apply(this);
        HelperLog.init(getApplicationContext());
        SourceSettings.ensureInitialized(this);
        applyPalette();

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
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        navParams.gravity = Gravity.BOTTOM;
        navParams.setMargins(dp(18), 0, dp(18), dp(16));
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSharedText(intent);
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
        if (!ThemeManager.isMorpheDesign(this)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        if (themeSignature != null
                && !themeSignature.equals(ThemeManager.signature(this))) {
            recreate();
            return;
        }
        if (selectedTab == TAB_DOWNLOADS || selectedTab == TAB_SOURCES) {
            selectTab(selectedTab, false);
        }
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
        int previous = selectedTab;
        selectedTab = index;
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
        float direction = index >= previous ? 1f : -1f;
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

        addNavItem(bar, TAB_HOME, R.drawable.ic_home,
                R.string.preview_home, () -> selectTab(TAB_HOME, true));
        addNavItem(bar, TAB_DOWNLOADS, R.drawable.ic_downloads,
                R.string.preview_downloads, () -> selectTab(TAB_DOWNLOADS, true));
        addNavItem(bar, TAB_SOURCES, R.drawable.ic_section_sources,
                R.string.preview_sources, () -> selectTab(TAB_SOURCES, true));
        addNavItem(bar, TAB_SETTINGS, R.drawable.ic_settings_gear,
                R.string.preview_settings, () -> selectTab(TAB_SETTINGS, true));
        return bar;
    }

    private void addNavItem(LinearLayout bar, int index, int iconRes, int labelRes,
                            Runnable action) {
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

        TextView label = M3Ui.text(this, getString(labelRes), 10, MUTED, 500);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(2);
        item.addView(label, labelParams);

        navItems[index] = item;
        navIcons[index] = icon;
        navLabels[index] = label;
        bar.addView(item, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private void updateNav() {
        for (int i = 0; i < navItems.length; i++) {
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

        root.addView(homeHeader());
        root.addView(statusRow());
        root.addView(heroCard());
        root.addView(featureRow());
        root.addView(quickActions());
        root.addView(developerBlock());
        addBottomSpacer(root);
        return scroll;
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
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView close = iconButton(R.drawable.ic_close, R.string.common_close,
                this::finish);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        closeParams.setMarginStart(dp(8));
        header.addView(close, closeParams);
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

        TextView help = M3Ui.outlinedButton(this, palette,
                getString(R.string.main_help), 0, TEXT);
        help.setOnClickListener(v -> showHelp());
        block.addView(help, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
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
                getString(R.string.main_link_releases), 0, TEXT);
        releases.setOnClickListener(v -> openUrl(
                "https://github.com/GeneralZyonix/morphe-download-helper/releases"));
        LinearLayout.LayoutParams releaseParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        releaseParams.setMarginStart(dp(4));
        releaseParams.setMarginEnd(dp(4));
        links.addView(releases, releaseParams);

        TextView reddit = M3Ui.outlinedButton(this, palette, "Reddit", 0, TEXT);
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
        root.addView(header);

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
        return scroll;
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
        ImageView full = iconButton(R.drawable.ic_fullscreen,
                R.string.source_manager_full_settings,
                () -> openSettings(SettingsActivity.EXTRA_SECTION_SOURCES));
        header.addView(full, new LinearLayout.LayoutParams(dp(42), dp(42)));
        root.addView(header);

        TextView immediate = M3Ui.text(this,
                getString(R.string.source_manager_immediate), 11, MUTED, 400);
        immediate.setPadding(0, dp(8), 0, dp(4));
        root.addView(immediate);

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
        return scroll;
    }

    private void rebuildSourceList() {
        if (sourceList == null) {
            return;
        }
        sourceList.removeAllViews();
        List<String> order = displayOrder();
        for (int i = 0; i < order.size(); i++) {
            sourceList.addView(sourceRow(order.get(i), i, order.size()));
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

    private View sourceRow(String key, int index, int count) {
        boolean enabled = SourceSettings.isEnabled(this, key);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(10), dp(4), dp(10));

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
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView up = M3Ui.text(this, "\u2191", 16,
                index > 0 ? ACCENT : palette.footer, 700);
        up.setGravity(Gravity.CENTER);
        up.setClickable(index > 0);
        up.setOnClickListener(v -> moveSource(key, -1));
        row.addView(up, new LinearLayout.LayoutParams(dp(24), dp(40)));

        TextView down = M3Ui.text(this, "\u2193", 16,
                index < count - 1 ? ACCENT : palette.footer, 700);
        down.setGravity(Gravity.CENTER);
        down.setClickable(index < count - 1);
        down.setOnClickListener(v -> moveSource(key, 1));
        row.addView(down, new LinearLayout.LayoutParams(dp(24), dp(40)));

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
        return row;
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

    private View buildSettings() {
        ScrollView scroll = tabScroll();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(M3Ui.overline(this,
                getString(R.string.settings_overline), ACCENT));
        TextView title = M3Ui.display(this,
                getString(R.string.preview_settings), 32, TEXT);
        title.setPadding(0, dp(8), 0, 0);
        root.addView(title);
        TextView subtitle = M3Ui.text(this,
                getString(R.string.settings_subtitle), 13, MUTED, 400);
        subtitle.setPadding(0, dp(6), 0, 0);
        root.addView(subtitle);
        TextView hint = M3Ui.text(this,
                getString(R.string.preview_settings_hint), 11, MUTED, 400);
        hint.setPadding(0, dp(8), 0, dp(2));
        root.addView(hint);

        addSettingsCard(root, R.drawable.ic_section_general,
                R.string.settings_general, R.string.settings_general_summary,
                "general");
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
        addSettingsActionCard(root, R.drawable.ic_section_general,
                R.string.preview_settings_appearance,
                R.string.appearance_subtitle,
                () -> startActivity(new Intent(this, AppearanceActivity.class)));

        addBottomSpacer(root);
        return scroll;
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
        scroll.setPadding(dp(20), dp(18), dp(20), dp(24));
        return scroll;
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
