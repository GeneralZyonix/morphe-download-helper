package be.alterra.morphehelper;

import android.app.Activity;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Launcher screen for Morphe Download Helper.
 *
 * <p>This activity does not download anything. It explains how to enable the helper in
 * Morphe, shows which APK sources the helper tries, and exposes maintenance actions:</p>
 *
 * <ul>
 *   <li>Open Morphe</li>
 *   <li>Share or clear the local diagnostic log</li>
 *   <li>Uninstall the helper</li>
 * </ul>
 *
 * <p>The real download work happens in {@link DownloadActivity} when Morphe sends the
 * {@code DOWNLOAD_ORIGINAL_APK} intent.</p>
 */
public class MainActivity extends Activity {

    /** Package name of the Morphe manager app that this helper integrates with. */
    private static final String MORPHE_PACKAGE = "app.morphe.manager";

    // ---------------------------------------------------------------------
    // UI palette (kept in code so the two screens share the same look)
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
    private int GREEN;
    private int FOOTER;
    private int SWITCH_THUMB;

    // Amber warning palette used by the "Heads up" card.
    private int WARN_TITLE;
    private int WARN_TEXT;

    private String themeSignature;

    /** Focused source manager dialog opened from Manage. */
    private SourceManagerDialog sourceManager;

    /** Container rebuilt in onResume so it always matches the saved order. */
    private LinearLayout sourcesList;

    @Override
    protected void onResume() {
        super.onResume();
        if (themeSignature != null
                && !themeSignature.equals(ThemeManager.signature(this))) {
            recreate();
            return;
        }
        renderSources();
        UpdateManager.showInAppUpdatePopupIfAvailable(this);
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
        GREEN = palette.green;
        FOOTER = palette.footer;
        SWITCH_THUMB = palette.switchThumb;
        WARN_TITLE = palette.warnTitle;
        WARN_TEXT = palette.warnText;
        themeSignature = ThemeManager.signature(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create the log file before any screen can try to share it.
        HelperLog.init(getApplicationContext());
        SourceSettings.ensureInitialized(this);
        PublicSettings.apply(this);
        AppSettings.ensureLauncherIconState(this);
        applyPalette();
        new Thread(() -> AppSettings.cleanup(this)).start();
        new Thread(() -> UpdateManager.cleanupStagedUpdates(this)).start();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 41);
        }

        // Root is a ScrollView because the content can be taller than short screens.
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(gradient(BG_TOP, BG_BOTTOM, GradientDrawable.Orientation.TOP_BOTTOM));

        // Vertical content column with generous top spacing below the status bar.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        applyContentPadding(root);
        scroll.addView(root, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        // Used for the "Morphe detected" / "Morphe not installed" status chip.
        boolean morpheInstalled = getPackageManager()
                .getLaunchIntentForPackage(MORPHE_PACKAGE) != null;

        // -----------------------------------------------------------------
        // Header: logo, title, status chip, and settings shortcut
        // -----------------------------------------------------------------
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        PatchPassLogoView logo = new PatchPassLogoView(this);
        logo.setContentDescription(getString(R.string.app_name));
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        logoParams.setMarginEnd(dp(12));
        header.addView(logo, logoParams);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(getString(R.string.app_name), 18, TEXT, true));

        TextView subtitle = text(getString(R.string.main_subtitle), 12, MUTED, false);
        subtitle.setPadding(0, dp(3), 0, 0);
        titles.addView(subtitle);

        // Status chip: green dot when Morphe is installed, amber dot otherwise.
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(10), dp(6), dp(12), dp(6));
        chip.setBackground(rounded(CARD_TOP, 999));

        View dot = new View(this);
        GradientDrawable dotDrawable = new GradientDrawable();
        dotDrawable.setShape(GradientDrawable.OVAL);
        dotDrawable.setColor(morpheInstalled ? GREEN : WARN_TITLE);
        dot.setBackground(dotDrawable);
        chip.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));

        TextView chipText = text(
                morpheInstalled ? getString(R.string.main_status_detected)
                        : getString(R.string.main_status_missing),
                12,
                morpheInstalled ? GREEN : WARN_TEXT,
                false
        );
        chipText.setPadding(dp(8), 0, 0, 0);
        chip.addView(chipText);

        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        chipParams.topMargin = dp(8);
        titles.addView(chip, chipParams);

        // The title block takes the remaining width next to the logo.
        header.addView(titles, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

        ImageView settingsButton = new ImageView(this);
        settingsButton.setImageResource(R.drawable.ic_settings_gear);
        settingsButton.setColorFilter(ACCENT_TEXT);
        settingsButton.setContentDescription(getString(R.string.common_settings));
        settingsButton.setPadding(dp(10), dp(10), dp(10), dp(10));
        settingsButton.setBackground(new RippleDrawable(
                ColorStateList.valueOf(RIPPLE), rounded(SECONDARY, 999), null));
        settingsButton.setClickable(true);
        settingsButton.setFocusable(true);
        settingsButton.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        settingsParams.setMarginStart(dp(6));
        header.addView(settingsButton, settingsParams);

        root.addView(header);

        // -----------------------------------------------------------------
        // Hero: primary explanation and the main action
        // -----------------------------------------------------------------
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(18), dp(20), dp(18));
        hero.setBackground(cardBackground());
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
        heroParams.topMargin = dp(22);
        root.addView(hero, heroParams);

        TextView kicker = text(getString(R.string.main_hero_kicker), 11, ACCENT_TEXT, true);
        kicker.setLetterSpacing(0.12f);
        hero.addView(kicker);

        TextView heroTitle = text(getString(R.string.main_hero_title), 24, TEXT, true);
        heroTitle.setPadding(0, dp(6), 0, 0);
        hero.addView(heroTitle);

        TextView heroBody = text(getString(R.string.main_hero_body), 13, MUTED, false);
        heroBody.setLineSpacing(dp(3), 1f);
        heroBody.setPadding(0, dp(8), 0, 0);
        hero.addView(heroBody);

        View openMorphe = primaryButtonWithIcon(
                getString(R.string.main_open_morphe), R.drawable.ic_external_link);
        openMorphe.setOnClickListener(v -> openMorphe());
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(MATCH_PARENT, dp(52));
        openParams.topMargin = dp(16);
        hero.addView(openMorphe, openParams);

        // Secondary actions sit directly under the hero.
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
        actionsParams.topMargin = dp(12);
        root.addView(actions, actionsParams);

        TextView guideButton = secondaryButton(getString(R.string.main_visual_guide),
                TEXT, SECONDARY_BORDER);
        guideButton.setOnClickListener(v ->
                startActivity(new Intent(this, GuideActivity.class)));
        LinearLayout.LayoutParams guideParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        guideParams.setMarginEnd(dp(6));
        actions.addView(guideButton, guideParams);

        TextView shareLog = secondaryButton(getString(R.string.main_share_log),
                TEXT, SECONDARY_BORDER);
        shareLog.setOnClickListener(v -> shareLog());
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        shareParams.setMarginStart(dp(6));
        actions.addView(shareLog, shareParams);

        TextView deleteAll = secondaryButton(getString(R.string.main_delete_all_downloads),
                TEXT, SECONDARY_BORDER);
        deleteAll.setOnClickListener(v -> confirmDeleteAllDownloads());
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, dp(48));
        deleteParams.topMargin = dp(10);
        root.addView(deleteAll, deleteParams);

        // -----------------------------------------------------------------
        // Download sources card: the order used by DownloadActivity.resolve()
        // -----------------------------------------------------------------
        LinearLayout sourcesCard = new LinearLayout(this);
        sourcesCard.setOrientation(LinearLayout.VERTICAL);
        sourcesCard.setPadding(dp(20), dp(18), dp(20), dp(18));
        sourcesCard.setBackground(cardBackground());
        LinearLayout.LayoutParams sourcesParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
        sourcesParams.topMargin = dp(12);
        root.addView(sourcesCard, sourcesParams);

        LinearLayout sourcesHeader = new LinearLayout(this);
        sourcesHeader.setOrientation(LinearLayout.HORIZONTAL);
        sourcesHeader.setGravity(Gravity.CENTER_VERTICAL);
        sourcesHeader.addView(text(getString(R.string.main_sources_title), 16, TEXT, true),
                new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

        TextView editOrder = text(getString(R.string.main_manage_sources), 12, ACCENT_TEXT, true);
        editOrder.setGravity(Gravity.CENTER);
        editOrder.setSingleLine(true);
        editOrder.setPadding(dp(14), dp(8), dp(10), dp(8));
        editOrder.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, R.drawable.ic_chevron_right, 0);
        editOrder.setCompoundDrawablePadding(dp(2));
        editOrder.setCompoundDrawableTintList(ColorStateList.valueOf(ACCENT_TEXT));
        GradientDrawable manageBackground = rounded(SECONDARY, 12);
        manageBackground.setStroke(dp(1), SECONDARY_BORDER);
        editOrder.setBackground(new RippleDrawable(
                ColorStateList.valueOf(RIPPLE), manageBackground, null));
        editOrder.setClickable(true);
        editOrder.setFocusable(true);
        editOrder.setOnClickListener(v -> showSourceManager());
        sourcesHeader.addView(editOrder);
        sourcesCard.addView(sourcesHeader);

        // The chips are rebuilt from AppSettings.sourceOrder(). This is the same
        // list and order used by DownloadActivity, so the screen cannot drift
        // from the real fallback behaviour again.
        sourcesList = new LinearLayout(this);
        sourcesList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sourcesListParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
        sourcesListParams.topMargin = dp(12);
        sourcesCard.addView(sourcesList, sourcesListParams);
        renderSources();

        TextView disabledSource = text(
                getString(R.string.main_sources_note_short), 11, MUTED, false);
        disabledSource.setLineSpacing(dp(2), 1f);
        disabledSource.setPadding(0, dp(12), 0, 0);
        sourcesCard.addView(disabledSource);

        // About the developer.
        LinearLayout about = new LinearLayout(this);
        about.setOrientation(LinearLayout.VERTICAL);
        about.setPadding(dp(18), dp(16), dp(18), dp(16));
        about.setBackground(cardBackground());
        LinearLayout.LayoutParams aboutParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
        aboutParams.topMargin = dp(16);
        root.addView(about, aboutParams);

        about.addView(text(getString(R.string.main_about), 16, TEXT, true));

        LinearLayout aboutHeader = new LinearLayout(this);
        aboutHeader.setOrientation(LinearLayout.HORIZONTAL);
        aboutHeader.setGravity(Gravity.CENTER_VERTICAL);
        aboutHeader.setPadding(0, dp(12), 0, 0);
        ImageView gzLogo = new ImageView(this);
        gzLogo.setImageResource(R.drawable.gz_logo);
        LinearLayout.LayoutParams gzLogoParams = new LinearLayout.LayoutParams(dp(58), dp(58));
        gzLogoParams.setMarginEnd(dp(12));
        aboutHeader.addView(gzLogo, gzLogoParams);

        LinearLayout aboutText = new LinearLayout(this);
        aboutText.setOrientation(LinearLayout.VERTICAL);
        aboutText.addView(text("GeneralZyonix", 16, TEXT, true));
        TextView role = text(getString(R.string.main_developer_role), 12, MUTED, false);
        role.setPadding(0, dp(3), 0, 0);
        aboutText.addView(role);
        aboutHeader.addView(aboutText, new LinearLayout.LayoutParams(
                0, WRAP_CONTENT, 1f));
        about.addView(aboutHeader);

        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.HORIZONTAL);
        links.setPadding(0, dp(14), 0, 0);
        links.addView(linkButton("GitHub", "https://github.com/GeneralZyonix/morphe-download-helper"));
        links.addView(linkButton(getString(R.string.main_link_releases),
                "https://github.com/GeneralZyonix/morphe-download-helper/releases"));
        links.addView(linkButton("Reddit", "https://www.reddit.com/r/MorpheApp/comments/1xjmnf3/i_made_an_helper_that_fetches_the_right_apk_for/"));
        about.addView(links);

        // Footer: local log note, version, and the clear-log action.
        String version = "v?";
        try {
            version = "v" + getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            // Keep the fallback text if the package info is unavailable.
        }

        LinearLayout footerRow = new LinearLayout(this);
        footerRow.setOrientation(LinearLayout.HORIZONTAL);
        footerRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams footerRowParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
        footerRowParams.topMargin = dp(20);
        root.addView(footerRow, footerRowParams);

        TextView logsLocal = text(getString(R.string.main_logs_local), 11, FOOTER, false);
        footerRow.addView(logsLocal, new LinearLayout.LayoutParams(
                0, WRAP_CONTENT, 1f));
        footerRow.addView(text(version, 11, FOOTER, false));

        TextView clearLog = text(getString(R.string.main_clear_log), 12, ACCENT_TEXT, true);
        clearLog.setGravity(Gravity.CENTER);
        clearLog.setPadding(dp(8), dp(8), dp(8), dp(8));
        clearLog.setClickable(true);
        clearLog.setFocusable(true);
        clearLog.setOnClickListener(v -> {
            HelperLog.clear();
            Toast.makeText(this, R.string.main_log_cleared, Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
        clearParams.topMargin = dp(4);
        clearParams.bottomMargin = dp(4);
        root.addView(clearLog, clearParams);

        setContentView(scroll);
        ThemeManager.applySystemBars(this);
        UpdateManager.maybeCheckOnLaunch(this,
                () -> UpdateManager.showInAppUpdatePopupIfAvailable(this));
    }

    /**
     * Opens Morphe's launcher activity.
     *
     * <p>Uses {@code getLaunchIntentForPackage} first, then falls back to an explicit
     * MAIN/LAUNCHER intent in case the package is installed but its launcher entry is
     * hidden by the user's launcher.</p>
     */
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
                })
                .show();
    }

    private void openMorphe() {        Intent launch = getPackageManager().getLaunchIntentForPackage(MORPHE_PACKAGE);
        if (launch == null) {
            launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            launch.setPackage(MORPHE_PACKAGE);
        }
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(launch);
            } catch (Exception e) {
                Toast.makeText(this,
                        getString(R.string.main_error_open_morphe, e.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, R.string.main_error_morphe_missing, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Shares the diagnostic log through Android's chooser.
     *
     * <p>The log lives in the app-specific external directory, so it must be exposed
     * through the configured {@link FileProvider} before another app can read it.</p>
     */
    private void shareLog() {
        File log = HelperLog.getLogFile();
        if (log == null || !log.exists()) {
            Toast.makeText(this, R.string.main_error_no_log, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    log
            );
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.main_log_subject));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.main_share_log_title)));
        } catch (Exception e) {
            Toast.makeText(this,
                    getString(R.string.main_error_share_log, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Converts density-independent pixels to physical pixels for this screen. */
    private void applyContentPadding(LinearLayout root) {
        int screen = getResources().getDisplayMetrics().widthPixels;
        int max = dp(720);
        int horizontal = Math.max(dp(24), (screen - max) / 2);
        root.setPadding(horizontal, dp(48), horizontal, dp(80));
    }
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

    /**
     * Rebuilds the source chips in the persisted order, skipping disabled
     * sources. Called on resume so changes made in Settings show immediately.
     */
    private void renderSources() {
        if (sourcesList == null) {
            return;
        }
        sourcesList.removeAllViews();
        List<String> order = AppSettings.sourceOrder(this);
        int position = 0;
        for (String key : order) {
            if (!SourceSettings.isEnabled(this, key)) {
                continue;
            }
            position++;
            addSourceRow(key, position);
        }

        if (position == 0) {
            sourcesList.addView(text(
                    getString(R.string.main_no_sources), 13, MUTED, false));
        }
    }

    private void showSourceManager() {
        if (sourceManager != null && sourceManager.isShowing()) {
            return;
        }
        sourceManager = SourceManagerDialog.show(this, this::renderSources);
    }

    private void styleSwitch(Switch toggle) {
        toggle.setThumbTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{SWITCH_THUMB, MUTED}
        ));
        toggle.setTrackTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{PRIMARY_START, SECONDARY_BORDER}
        ));
    }

    private void addSourceRow(String key, int position) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        View badge = SourceBadge.create(this, key);
        row.addView(badge, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(10), 0, 0, 0);
        String name = SourceSettings.APPTEKA.equals(key)
                ? getString(R.string.main_source_appteka)
                : AppSettings.sourceLabel(this, key);
        labels.addView(text(name, 14, TEXT, true));
        String description = SourceManagerDialog.description(this, key);
        if (!description.isEmpty()) {
            TextView detail = text(description, 11, MUTED, false);
            detail.setPadding(0, dp(2), 0, 0);
            labels.addView(detail);
        }
        row.addView(labels, new LinearLayout.LayoutParams(
                0, WRAP_CONTENT, 1f));

        TextView check = text("\u2713", 16, GREEN, true);
        row.addView(check);
        sourcesList.addView(row);

        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        sourcesList.addView(divider, new LinearLayout.LayoutParams(
                MATCH_PARENT, dp(1)));
    }

    /** Creates the full-width gradient button used for the main action. */
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

    /** Full-width gradient button with the icon centred beside the label. */
    private View primaryButtonWithIcon(String label, int iconRes) {
        FrameLayout button = new FrameLayout(this);
        button.setClickable(true);
        button.setFocusable(true);

        GradientDrawable fill = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{PRIMARY_START, PRIMARY_END}
        );
        fill.setCornerRadius(dp(16));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF),
                fill,
                null
        ));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ON_PRIMARY);
        content.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView text = text(label, 16, ON_PRIMARY, true);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setPadding(dp(10), 0, 0, 0);
        content.addView(text);

        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        button.addView(content, contentParams);
        return button;
    }

    /** Creates a dark outlined button with a ripple and custom text/border colors. */
    private TextView linkButton(String label, String url) {
        TextView view = text(label, 12, ACCENT_TEXT, true);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable background = rounded(SECONDARY, 12);
        background.setStroke(dp(1), SECONDARY_BORDER);
        view.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                WRAP_CONTENT, WRAP_CONTENT);
        params.setMarginEnd(dp(6));
        view.setLayoutParams(params);
        view.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
                // No browser available.
            }
        });
        return view;
    }
    private TextView secondaryButton(String label, int textColor, int strokeColor) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        view.setTextColor(textColor);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));

        GradientDrawable fill = rounded(SECONDARY, 14);
        fill.setStroke(dp(1), strokeColor);
        view.setBackground(new RippleDrawable(
                ColorStateList.valueOf(RIPPLE),
                fill,
                null
        ));
        return view;
    }

    /** Rounded, subtly bordered card background used by the content sections. */
    private GradientDrawable cardBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{CARD_TOP, CARD_BOTTOM}
        );
        drawable.setCornerRadius(dp(20));
        drawable.setStroke(dp(1), BORDER);
        return drawable;
    }

    /** Creates a two-stop gradient drawable for the screen background. */
    private GradientDrawable gradient(int start, int end, GradientDrawable.Orientation orientation) {
        GradientDrawable drawable = new GradientDrawable(orientation, new int[]{start, end});
        drawable.setCornerRadius(0);
        return drawable;
    }

    /** Creates a solid rounded drawable for chips and warning cards. */
    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }
}











