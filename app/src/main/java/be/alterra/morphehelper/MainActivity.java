package be.alterra.morphehelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

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
    private static final int REQ_SAVE_LOG = 6102;

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
    private int RED;
    private int FOOTER;
    private int SWITCH_THUMB;

    // Amber warning palette used by the "Heads up" card.
    private int WARN_TITLE;
    private int WARN_TEXT;

    private String themeSignature;
    private ThemePalette palette;
    private boolean morpheInstalled;
    private String morpheVersionName = "";
    private String helperVersionName = "?";

    @Override
    protected void onResume() {
        super.onResume();
        if (!isFinishing() && ThemeManager.isMorpheDesign(this)) {
            openMorpheDesign();
            return;
        }
        if (themeSignature != null
                && !themeSignature.equals(ThemeManager.signature(this))) {
            recreate();
            return;
        }
        UpdateManager.showInAppUpdatePopupIfAvailable(this);
    }

    /** Moves the launcher entry into the optional Morphe shell. */
    private void openMorpheDesign() {
        startActivity(new Intent(this, MorphePreviewActivity.class));
        finish();
    }

    private void applyPalette() {
        palette = ThemeManager.palette(this);
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
        RED = palette.red;
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

        if (ThemeManager.isMorpheDesign(this)) {
            openMorpheDesign();
            return;
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
        PackageInfo morpheInfo = null;
        try {
            morpheInfo = getPackageManager().getPackageInfo(MORPHE_PACKAGE, 0);
        } catch (Exception ignored) {
            // Morphe is not installed or is not visible to this package.
        }
        boolean morpheInstalled = morpheInfo != null;
        String morpheVersion = morpheInfo == null || morpheInfo.versionName == null
                ? "" : morpheInfo.versionName.trim();
        this.morpheInstalled = morpheInstalled;
        this.morpheVersionName = morpheVersion;

        String appVersion = "?";
        try {
            String name = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            if (!TextUtils.isEmpty(name)) {
                appVersion = name.trim();
            }
        } catch (Exception ignored) {
            // Keep the fallback text if the package info is unavailable.
        }
        helperVersionName = appVersion;

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
        titles.addView(M3Ui.title(this, getString(R.string.app_name), 19, TEXT));

        TextView subtitle = text(getString(R.string.main_subtitle), 12, MUTED, false);
        subtitle.setPadding(0, dp(3), 0, 0);
        titles.addView(subtitle);

        // The title block takes the remaining width next to the logo.
        header.addView(titles, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

        ImageView settingsButton = new ImageView(this);
        settingsButton.setImageResource(R.drawable.ic_settings_gear);
        settingsButton.setColorFilter(ACCENT_TEXT);
        settingsButton.setContentDescription(getString(R.string.common_settings));
        settingsButton.setPadding(dp(10), dp(10), dp(10), dp(10));
        settingsButton.setBackground(new RippleDrawable(
                ColorStateList.valueOf(RIPPLE),
                M3Ui.filled(this, M3Ui.secondaryContainer(palette), 999, 0, 0), null));
        settingsButton.setClickable(true);
        settingsButton.setFocusable(true);
        settingsButton.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        settingsParams.setMarginStart(dp(6));
        header.addView(settingsButton, settingsParams);

        root.addView(header);

        // Status pills: Morphe state/version and the helper version.
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
        statusParams.topMargin = dp(14);
        root.addView(statusRow, statusParams);

        int morpheColor = morpheInstalled ? GREEN : RED;
        LinearLayout morpheChip = new LinearLayout(this);
        morpheChip.setOrientation(LinearLayout.HORIZONTAL);
        morpheChip.setGravity(Gravity.CENTER_VERTICAL);
        morpheChip.setPadding(dp(10), dp(6), dp(12), dp(6));
        GradientDrawable morpheFill = rounded(withAlpha(morpheColor, 10), 999);
        morpheFill.setStroke(dp(1), withAlpha(morpheColor, 32));
        morpheChip.setBackground(morpheFill);

        if (morpheInstalled) {
            View dot = new View(this);
            GradientDrawable dotDrawable = new GradientDrawable();
            dotDrawable.setShape(GradientDrawable.OVAL);
            dotDrawable.setColor(GREEN);
            dot.setBackground(dotDrawable);
            morpheChip.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        }

        String morpheLabel;
        if (!morpheInstalled) {
            morpheLabel = getString(R.string.main_status_missing_chip);
        } else if (TextUtils.isEmpty(morpheVersion)) {
            morpheLabel = getString(R.string.main_status_detected);
        } else {
            morpheLabel = getString(R.string.main_status_detected_version, morpheVersion);
        }
        TextView morpheChipText = text(morpheLabel, 12, morpheColor, false);
        morpheChipText.setMaxLines(1);
        morpheChipText.setEllipsize(TextUtils.TruncateAt.END);
        morpheChipText.setMaxWidth(dp(230));
        if (morpheInstalled) {
            morpheChipText.setPadding(dp(8), 0, 0, 0);
        }
        morpheChip.addView(morpheChipText);
        statusRow.addView(morpheChip, new LinearLayout.LayoutParams(
                WRAP_CONTENT, WRAP_CONTENT));

        String buildName = getString(AppSettings.isLauncherIconShown(this)
                ? R.string.main_build_icon
                : R.string.main_build_no_icon);
        LinearLayout versionChip = new LinearLayout(this);
        versionChip.setOrientation(LinearLayout.HORIZONTAL);
        versionChip.setGravity(Gravity.CENTER_VERTICAL);
        versionChip.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable versionFill = rounded(withAlpha(ACCENT_TEXT, 10), 999);
        versionFill.setStroke(dp(1), withAlpha(ACCENT_TEXT, 32));
        versionChip.setBackground(versionFill);
        versionChip.addView(text(
                getString(R.string.main_version_chip_version, appVersion),
                12, ACCENT_TEXT, true));
        TextView versionBadge = M3Ui.versionBadge(this, palette,
                BuildConfig.PRERELEASE);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                dp(22), dp(22));
        badgeParams.setMarginStart(dp(6));
        versionChip.addView(versionBadge, badgeParams);
        TextView buildText = text(
                getString(R.string.main_version_chip_build, buildName),
                12, ACCENT_TEXT, true);
        buildText.setPadding(dp(6), 0, 0, 0);
        versionChip.addView(buildText);
        LinearLayout.LayoutParams versionChipParams = new LinearLayout.LayoutParams(
                WRAP_CONTENT, WRAP_CONTENT);
        versionChipParams.setMarginStart(dp(8));
        statusRow.addView(versionChip, versionChipParams);

        // -----------------------------------------------------------------
        // Hero: primary explanation and the main action
        // -----------------------------------------------------------------
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        hero.setBackground(M3Ui.hero(this, palette));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
        heroParams.topMargin = dp(22);
        root.addView(hero, heroParams);

        int heroForeground = M3Ui.onPrimaryContainer(palette);
        TextView kicker = M3Ui.overline(this,
                getString(R.string.main_hero_kicker), heroForeground);
        hero.addView(kicker);

        TextView heroTitle = M3Ui.display(this,
                getString(R.string.main_hero_title), 27, heroForeground);
        heroTitle.setPadding(0, dp(8), 0, 0);
        hero.addView(heroTitle);

        TextView heroBody = text(getString(R.string.main_hero_body), 13,
                withAlpha(heroForeground, 80), false);
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

        TextView helpButton = secondaryButton(getString(R.string.main_help),
                TEXT, SECONDARY_BORDER);
        helpButton.setOnClickListener(v -> showHelp());
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        helpParams.setMarginStart(dp(6));
        actions.addView(helpButton, helpParams);

        // Sources opens Settings directly on the Sources section, where the
        // enabled sources, their order, and GitHub apps are managed.
        TextView sourcesButton = secondaryButton(getString(R.string.settings_sources),
                ACCENT_TEXT, SECONDARY_BORDER);
        sourcesButton.setOnClickListener(v ->
                SourceManagerDialog.show(this, null));
        LinearLayout.LayoutParams sourcesButtonParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, dp(48));
        sourcesButtonParams.topMargin = dp(10);
        root.addView(sourcesButton, sourcesButtonParams);

        TextView deleteAll = secondaryButton(getString(R.string.main_delete_all_downloads),
                TEXT, SECONDARY_BORDER);
        deleteAll.setOnClickListener(v -> confirmDeleteAllDownloads());
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, dp(48));
        deleteParams.topMargin = dp(10);
        root.addView(deleteAll, deleteParams);

        // Helper log: collapsed by default so the log tools do not dominate the
        // main screen. Tap the header to reveal sharing and reporting actions.
        LinearLayout logCard = new LinearLayout(this);
        logCard.setOrientation(LinearLayout.VERTICAL);
        logCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        logCard.setBackground(cardBackground());
        LinearLayout.LayoutParams logCardParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
        logCardParams.topMargin = dp(16);
        root.addView(logCard, logCardParams);

        LinearLayout logHeader = new LinearLayout(this);
        logHeader.setOrientation(LinearLayout.HORIZONTAL);
        logHeader.setGravity(Gravity.CENTER_VERTICAL);
        logHeader.setClickable(true);
        logHeader.setFocusable(true);
        logHeader.setContentDescription(getString(R.string.main_log_expand));
        logCard.addView(logHeader, new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT));

        LinearLayout logTitle = new LinearLayout(this);
        logTitle.setOrientation(LinearLayout.VERTICAL);
        logTitle.addView(text(getString(R.string.main_log_title), 16, TEXT, true));
        TextView logsLocal = text(getString(R.string.main_logs_local), 11, FOOTER, false);
        logsLocal.setPadding(0, dp(3), 0, 0);
        logTitle.addView(logsLocal);
        logHeader.addView(logTitle, new LinearLayout.LayoutParams(
                0, WRAP_CONTENT, 1f));

        ImageView logChevron = new ImageView(this);
        logChevron.setImageResource(R.drawable.ic_chevron_right);
        logChevron.setColorFilter(MUTED);
        logChevron.setRotation(0f);
        logChevron.setPadding(dp(6), dp(6), dp(6), dp(6));
        logHeader.addView(logChevron, new LinearLayout.LayoutParams(
                dp(32), dp(32)));

        LinearLayout logBody = new LinearLayout(this);
        logBody.setOrientation(LinearLayout.VERTICAL);
        logBody.setVisibility(View.GONE);
        logCard.addView(logBody, new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT));

        TextView logHelp = text(getString(R.string.main_log_help), 12, MUTED, false);
        logHelp.setLineSpacing(dp(2), 1f);
        logHelp.setPadding(0, dp(8), 0, 0);
        logBody.addView(logHelp);

        LinearLayout logActions = new LinearLayout(this);
        logActions.setOrientation(LinearLayout.HORIZONTAL);
        logActions.setGravity(Gravity.CENTER_VERTICAL);
        logActions.addView(logAction(R.drawable.ic_share,
                R.string.main_share_log, false, this::shareLog), logActionParams(true));
        logActions.addView(logAction(R.drawable.ic_copy,
                R.string.main_copy_log, false, this::copyLogToClipboard), logActionParams(true));
        logActions.addView(logAction(R.drawable.ic_save,
                R.string.main_save_log, false, this::saveLog), logActionParams(true));
        logActions.addView(logAction(R.drawable.ic_delete,
                R.string.main_clear_log, true, () -> {
                    HelperLog.clear();
                    Toast.makeText(this, R.string.main_log_cleared,
                            Toast.LENGTH_SHORT).show();
                }), logActionParams(false));
        LinearLayout.LayoutParams logActionsParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
        logActionsParams.topMargin = dp(14);
        logBody.addView(logActions, logActionsParams);

        TextView previewLog = secondaryButton(getString(R.string.main_preview_log),
                TEXT, SECONDARY_BORDER);
        previewLog.setOnClickListener(v -> previewLog());
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, dp(44));
        previewParams.topMargin = dp(10);
        logBody.addView(previewLog, previewParams);

        TextView reportGithub = secondaryButton(getString(R.string.main_report_github),
                R.drawable.source_logo_github, ACCENT_TEXT, SECONDARY_BORDER);
        reportGithub.setOnClickListener(v -> showGitHubReportDialog());
        LinearLayout.LayoutParams reportParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, dp(44));
        reportParams.topMargin = dp(10);
        logBody.addView(reportGithub, reportParams);

        TextView suggestFeature = secondaryButton(getString(R.string.main_suggest_feature),
                R.drawable.source_logo_github, ACCENT_TEXT, SECONDARY_BORDER);
        suggestFeature.setOnClickListener(v -> openGitHubReport(
                "https://github.com/GeneralZyonix/morphe-download-helper/issues/new"
                        + "?template=feature_request.yml"));
        LinearLayout.LayoutParams suggestParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, dp(44));
        suggestParams.topMargin = dp(8);
        logBody.addView(suggestFeature, suggestParams);

        logHeader.setOnClickListener(v -> {
            boolean expand = logBody.getVisibility() != View.VISIBLE;
            logBody.setVisibility(expand ? View.VISIBLE : View.GONE);
            logChevron.setRotation(expand ? 90f : 0f);
            logHeader.setContentDescription(getString(expand
                    ? R.string.main_log_collapse : R.string.main_log_expand));
        });

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

        setContentView(scroll);
        ThemeManager.applySystemBars(this);
        UpdateManager.maybeCheckOnLaunch(this,
                () -> UpdateManager.showInAppUpdatePopupIfAvailable(this));
        handleShareIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShareIntent(intent);
    }

    /**
     * Shared GitHub links open the Sources screen with the add dialog prefilled.
     */
    private void handleShareIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
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
        // Avoid opening the dialog again after a rotation or resume.
        intent.setAction(null);
        intent.removeExtra(Intent.EXTRA_TEXT);
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
        new Thread(() -> {
            try {
                String text = LogSanitizer.sanitizeForExport(readLogText(log));
                if (text.trim().isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this,
                            R.string.main_error_no_log, Toast.LENGTH_LONG).show());
                    return;
                }
                File sanitized = new File(log.getParentFile(), "helper-sanitized.log");
                try (OutputStreamWriter writer = new OutputStreamWriter(
                        new FileOutputStream(sanitized), StandardCharsets.UTF_8)) {
                    writer.write(text);
                }
                Uri uri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        sanitized
                );
                runOnUiThread(() -> {
                    try {
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType("text/plain");
                        send.putExtra(Intent.EXTRA_STREAM, uri);
                        send.putExtra(Intent.EXTRA_SUBJECT,
                                getString(R.string.main_log_subject));
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(send,
                                getString(R.string.main_share_log_title)));
                    } catch (Exception e) {
                        Toast.makeText(this,
                                getString(R.string.main_error_share_log, e.getMessage()),
                                Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.main_error_share_log, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        }, "share-log").start();
    }

    /**
     * Copies the current diagnostic log to the clipboard. This is an
     * alternative to the share sheet for users who cannot attach a file to
     * their report; they can paste the text directly into an issue.
     */
    private void copyLogToClipboard() {
        File log = HelperLog.getLogFile();
        if (log == null || !log.isFile()) {
            Toast.makeText(this, R.string.main_error_no_log, Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            String text;
            try {
                text = LogSanitizer.sanitizeForExport(readLogText(log));
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.main_error_copy_log, Toast.LENGTH_LONG).show());
                return;
            }
            if (text.trim().isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.main_error_no_log, Toast.LENGTH_LONG).show());
                return;
            }
            final String clipboardText = text;
            runOnUiThread(() -> {
                if (!copyTextToClipboard(clipboardText)) {
                    Toast.makeText(this, R.string.main_error_copy_log,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, R.string.main_log_copied,
                        Toast.LENGTH_SHORT).show();
            });
        }, "copy-log").start();
    }

    /** Copies text to the clipboard. Must be called on the UI thread. */
    private boolean copyTextToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return false;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("helper-sanitized.log", text));
        return true;
    }

    /** Shows a read-only preview of the sanitized log before it is shared. */
    private void previewLog() {
        File log = HelperLog.getLogFile();
        if (log == null || !log.isFile()) {
            Toast.makeText(this, R.string.main_error_no_log, Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            String text;
            try {
                text = LogSanitizer.sanitizeForExport(readLogText(log));
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.main_error_copy_log, Toast.LENGTH_LONG).show());
                return;
            }
            if (text.trim().isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.main_error_no_log, Toast.LENGTH_LONG).show());
                return;
            }
            final int max = 120_000;
            if (text.length() > max) {
                text = "[... earlier sanitized lines omitted ...]\n"
                        + text.substring(text.length() - max);
            }
            final String preview = text;
            runOnUiThread(() -> {
                TextView body = text(preview, 10, TEXT, false);
                body.setTypeface(Typeface.MONOSPACE);
                body.setTextIsSelectable(true);
                body.setLineSpacing(dp(2), 1f);
                body.setPadding(dp(16), dp(12), dp(16), dp(12));

                ScrollView scroll = new ScrollView(this);
                scroll.addView(body, new ScrollView.LayoutParams(
                        MATCH_PARENT, WRAP_CONTENT));
                scroll.setLayoutParams(new LinearLayout.LayoutParams(
                        MATCH_PARENT, dp(420)));

                ThemeManager.dialog(this)
                        .setTitle(R.string.main_preview_log_title)
                        .setView(scroll)
                        .setPositiveButton(R.string.main_copy_log, (dialog, which) -> {
                            if (!copyTextToClipboard(preview)) {
                                Toast.makeText(this, R.string.main_error_copy_log,
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(this, R.string.main_log_copied,
                                        Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(R.string.common_close, null)
                        .show();
            });
        }, "preview-log").start();
    }

    /** Opens the system document picker so the log can be saved to Downloads or Drive. */
    private void saveLog() {
        File log = HelperLog.getLogFile();
        if (log == null || !log.isFile()) {
            Toast.makeText(this, R.string.main_error_no_log, Toast.LENGTH_LONG).show();
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
        if (requestCode == REQ_SAVE_LOG && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                saveLogTo(uri);
            }
        }
    }

    private void saveLogTo(Uri uri) {
        new Thread(() -> {
            File log = HelperLog.getLogFile();
            if (log == null || !log.isFile()) {
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.main_error_no_log, Toast.LENGTH_LONG).show());
                return;
            }
            try {
                String text = LogSanitizer.sanitizeForExport(readLogText(log));
                if (text.trim().isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this,
                            R.string.main_error_no_log, Toast.LENGTH_LONG).show());
                    return;
                }
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
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

    /** Short in-app troubleshooting guide for the most common support questions. */
    private void showHelp() {
        String message = getString(R.string.help_log)
                + "\n\n" + getString(R.string.help_play_protect)
                + "\n\n" + getString(R.string.help_uptodown)
                + "\n\n" + getString(R.string.help_icons)
                + "\n\n" + getString(R.string.help_sources);
        ThemeManager.dialog(this)
                .setTitle(R.string.main_help_title)
                .setMessage(message)
                .setPositiveButton(R.string.common_done, null)
                .show();
    }

    /** Lets the user choose between a short report and the full log. */
    private void showGitHubReportDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(4), dp(24), dp(4));

        TextView message = text(getString(R.string.main_report_message), 13, MUTED, false);
        message.setLineSpacing(dp(2), 1f);
        content.addView(message);

        TextView shortReport = reportChoiceButton(getString(R.string.main_report_short));
        LinearLayout.LayoutParams shortParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
        shortParams.topMargin = dp(14);
        content.addView(shortReport, shortParams);

        TextView fullReport = reportChoiceButton(getString(R.string.main_report_full));
        LinearLayout.LayoutParams fullParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
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
        TextView view = secondaryButton(label, TEXT, SECONDARY_BORDER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(14), dp(12), dp(14), dp(12));
        view.setSingleLine(false);
        return view;
    }

    /**
     * Prepares the log for GitHub. The short option embeds recent lines in the
     * issue form; the full option either embeds a small log or copies/saves it
     * for the user to paste or attach.
     */
    private void reportToGitHub(boolean fullLog) {
        File logFile = HelperLog.getLogFile();
        if (logFile == null || !logFile.isFile()) {
            Toast.makeText(this, R.string.main_report_no_log, Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            String raw;
            try {
                raw = readLogText(logFile);
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
            boolean saved = fullLog
                    && AppSettings.saveLogCopy(this,
                    LogSanitizer.sanitizeForExport(raw));
            String targetApp = GitHubReport.lastRequest(redacted);
            if (TextUtils.isEmpty(targetApp)) {
                targetApp = getString(R.string.main_report_target_unknown);
            }
            String androidDevice = Build.MANUFACTURER + " " + Build.MODEL
                    + ", Android " + Build.VERSION.RELEASE
                    + " (SDK " + Build.VERSION.SDK_INT + ")";
            String morphe = morpheInstalled
                    ? (TextUtils.isEmpty(morpheVersionName)
                    ? getString(R.string.main_status_detected)
                    : morpheVersionName)
                    : getString(R.string.main_report_morphe_missing);
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
                    copied = copyTextToClipboard(finalRedacted);
                    if (copied) {
                        logField = getString(R.string.main_report_full_field_note);
                        logIsCode = false;
                        toast = finalSaved
                                ? getString(R.string.main_report_full_saved)
                                : getString(R.string.main_report_save_failed);
                    } else if (finalSaved) {
                        logField = getString(R.string.main_report_full_saved_field_note);
                        logIsCode = false;
                        toast = getString(R.string.main_report_full_saved);
                    } else {
                        logField = GitHubReport.excerpt(finalRedacted);
                        logIsCode = true;
                        toast = getString(R.string.main_report_full_failed_short);
                    }
                }
                String url = GitHubReport.buildUrl(
                        helperVersionName, finalDevice, finalMorphe,
                        finalTarget, logField, logIsCode);
                if (toast != null) {
                    Toast.makeText(this, toast, Toast.LENGTH_LONG).show();
                }
                openGitHubReport(url);
            });
        }, "github-report").start();
    }

    private void openGitHubReport(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this,
                    getString(R.string.main_report_open_failed, error.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Reads the log, keeping only the newest 300k characters for the clipboard. */
    private static String readLogText(File file) throws Exception {
        StringBuilder all = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                all.append(buffer, 0, read);
            }
        }
        final int max = 300_000;
        if (all.length() <= max) {
            return all.toString();
        }
        return "[... earlier log truncated ...]\n"
                + all.substring(all.length() - max);
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

    /** Applies a percentage alpha to a theme color. */
    private int withAlpha(int color, int percent) {
        int alpha = Math.max(0, Math.min(100, percent)) * 255 / 100;
        return (color & 0x00FFFFFF) | (alpha << 24);
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

    private void styleSwitch(Switch toggle) {
        M3Ui.styleSwitch(toggle, palette);
    }

    /** Creates the full-width gradient button used for the main action. */
    private TextView primaryButton(String label) {
        return M3Ui.primaryButton(this, palette, label, 0);
    }

    /** Full-width gradient button with the icon centred beside the label. */
    private View primaryButtonWithIcon(String label, int iconRes) {
        return M3Ui.primaryButton(this, palette, label, iconRes);
    }

    /** Creates a small tonal link chip. */
    private TextView linkButton(String label, String url) {
        TextView view = M3Ui.chip(this, palette, label, false);
        view.setClickable(true);
        view.setFocusable(true);
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
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
    /** Compact icon action used by the Helper log card. */
    private View logAction(int iconRes, int labelRes, boolean danger, Runnable action) {
        FrameLayout cell = new FrameLayout(this);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setColorFilter(danger ? RED : M3Ui.onSecondaryContainer(palette));
        icon.setContentDescription(getString(labelRes));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            icon.setTooltipText(getString(labelRes));
        }
        icon.setBackground(new RippleDrawable(
                ColorStateList.valueOf(RIPPLE),
                M3Ui.filled(this, danger
                                ? M3Ui.errorContainer(palette)
                                : M3Ui.secondaryContainer(palette),
                        999, 0, 0), null));
        icon.setPadding(dp(13), dp(13), dp(13), dp(13));
        icon.setClickable(true);
        icon.setFocusable(true);
        icon.setOnClickListener(v -> action.run());
        M3Ui.motion(icon);
        cell.addView(icon, new FrameLayout.LayoutParams(
                dp(48), dp(48), Gravity.CENTER));
        return cell;
    }

    private LinearLayout.LayoutParams logActionParams(boolean marginEnd) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(56), 1f);
        if (marginEnd) {
            params.setMarginEnd(dp(4));
        }
        return params;
    }

    private TextView secondaryButton(String label, int textColor, int strokeColor) {
        if (textColor == ACCENT_TEXT) {
            return M3Ui.tonalButton(this, palette, label, 0);
        }
        return M3Ui.outlinedButton(this, palette, label, 0, textColor);
    }

    private TextView secondaryButton(String label, int iconRes, int textColor, int strokeColor) {
        return secondaryButton(label, iconRes, textColor, SECONDARY, strokeColor);
    }

    private TextView secondaryButton(String label, int iconRes, int textColor,
                                     int fillColor, int strokeColor) {
        if (textColor == ACCENT_TEXT) {
            return M3Ui.tonalButton(this, palette, label, iconRes);
        }
        return M3Ui.outlinedButton(this, palette, label, iconRes, textColor);
    }

    /** Rounded, subtly bordered card background used by the content sections. */
    private GradientDrawable cardBackground() {
        return M3Ui.card(this, palette);
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











