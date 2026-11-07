package be.alterra.morphehelper;

import android.Manifest;
import android.app.Activity;
import android.app.LocaleManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.XmlResourceParser;
import android.net.Uri;
import android.os.Build;
import android.os.LocaleList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import rikka.shizuku.Shizuku;

/**
 * Source settings: choose which APK sources DownloadActivity may use.
 */
public class SettingsActivity extends Activity {

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
    private int WARN_TEXT;
    private int RED;
    private int GREEN;
    private int SWITCH_THUMB;
    private String themeSignature;
    private static final int SHIZUKU_REQUEST_CODE = 71;
    private static final int FOLDER_REQUEST_CODE = 72;
    private static final int STORAGE_PERMISSION_CODE = 73;
    private static final int EXPORT_SETTINGS_REQUEST_CODE = 74;
    private static final int IMPORT_SETTINGS_REQUEST_CODE = 75;
    private static final String SECTION_PREFS = "settings_sections";
    static final String EXTRA_SECTION = "open_section";
    static final String EXTRA_SECTION_SOURCES = "sources";
    private static final String SECTION_GENERAL = "general";
    private static final String SECTION_UPDATES = "updates";
    private static final String SECTION_WEB = "web";
    private static final String SECTION_SOURCES = "sources";
    private static final String SECTION_STORAGE = "storage";
    private static final String SECTION_SECURITY = "security";
    private static final String SECTION_BATTERY = "battery";
    private static final String SECTION_BACKUP = "backup";
    private boolean batteryIgnoring;
    private String pendingLocation;
    private boolean exportIncludeSecrets;
    private final Map<String, View> sectionCards = new HashMap<>();
    private View sourceOrderTarget;

    private final Shizuku.OnRequestPermissionResultListener shizukuListener =
            (requestCode, grantResult) -> {
                if (requestCode != SHIZUKU_REQUEST_CODE) {
                    return;
                }
                boolean granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED;
                if (granted) {
                    UpdateManager.setMode(this, UpdateManager.MODE_SHIZUKU);
                    PublicSettings.write(this);
                    Toast.makeText(this, R.string.settings_automatic_enabled,
                            Toast.LENGTH_SHORT).show();
                } else {
                    UpdateManager.setMode(this, UpdateManager.MODE_NOTIFY);
                    PublicSettings.write(this);
                    Toast.makeText(this, R.string.settings_shizuku_denied,
                            Toast.LENGTH_LONG).show();
                }
                recreate();
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SourceSettings.ensureInitialized(this);
        PublicSettings.apply(this);
        AppSettings.ensureLauncherIconState(this);
        applyPalette();
        Shizuku.addRequestPermissionResultListener(shizukuListener);

        ScrollView scroll = new ScrollView(this);
        scroll.setId(R.id.settings_scroll);
        scroll.setFillViewport(true);
        scroll.setBackground(gradient(BG_TOP, BG_BOTTOM));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        applyContentPadding(root);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo_mark);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(64), dp(64));
        logoParams.setMarginEnd(dp(14));
        header.addView(logo, logoParams);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(getString(R.string.common_settings), 24, TEXT, true));
        TextView subtitle = text(getString(R.string.settings_subtitle), 13, MUTED, false);
        subtitle.setPadding(0, dp(4), 0, 0);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header);

        boolean openSources = EXTRA_SECTION_SOURCES.equals(
                getIntent().getStringExtra(EXTRA_SECTION));
        if (openSources) {
            setSectionExpanded(SECTION_SOURCES, true);
        }

        LinearLayout general = addSection(root, SECTION_GENERAL,
                R.string.settings_general, R.string.settings_general_summary,
                R.drawable.ic_section_general);
        addLanguageRow(general);
        addAppearanceRow(general);
        addLauncherIconCard(general);

        LinearLayout updates = addSection(root, SECTION_UPDATES,
                R.string.settings_updates, R.string.settings_updates_summary,
                R.drawable.ic_section_updates);
        addUpdatesCard(updates);

        LinearLayout web = addSection(root, SECTION_WEB,
                R.string.settings_web_pages, R.string.settings_web_pages_summary,
                R.drawable.ic_section_web);
        addWebPagesCard(web);

        LinearLayout sources = addSection(root, SECTION_SOURCES,
                R.string.settings_sources, R.string.settings_sources_summary,
                R.drawable.ic_section_sources);
        addSourcesCard(sources);
        addApptekaWarningCard(sources);
        addGitHubAppsCard(sources);

        LinearLayout storage = addSection(root, SECTION_STORAGE,
                R.string.settings_storage, R.string.settings_storage_summary,
                R.drawable.ic_section_storage);
        addStorageCard(storage);

        LinearLayout security = addSection(root, SECTION_SECURITY,
                R.string.virustotal_security, R.string.settings_security_summary,
                R.drawable.ic_section_security);
        addSecurityCard(security);

        LinearLayout battery = addSection(root, SECTION_BATTERY,
                R.string.settings_background, R.string.settings_background_summary,
                R.drawable.ic_section_battery);
        addBatteryCard(battery);

        LinearLayout backup = addSection(root, SECTION_BACKUP,
                R.string.settings_backup, R.string.settings_backup_summary,
                R.drawable.ic_section_backup);
        addBackupCard(backup);

        TextView done = primaryButton(getString(R.string.common_done));
        done.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        doneParams.topMargin = dp(20);
        doneParams.bottomMargin = dp(24);
        root.addView(done, doneParams);

        setContentView(scroll);
        ThemeManager.applySystemBars(this);
        if (openSources) {
            scroll.post(() -> {
                View target = sourceOrderTarget;
                if (target == null) {
                    target = sectionCards.get(SECTION_SOURCES);
                }
                if (target != null) {
                    scroll.smoothScrollTo(0, Math.max(0, contentTop(target, scroll) - dp(32)));
                }
            });
        }
    }

    /** Returns the vertical position of a nested view inside the scroll content. */
    private int contentTop(View target, View scrollRoot) {
        int top = 0;
        View current = target;
        while (current != null && current != scrollRoot) {
            top += current.getTop();
            if (!(current.getParent() instanceof View)) {
                break;
            }
            current = (View) current.getParent();
        }
        return top;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeSignature != null
                && !themeSignature.equals(ThemeManager.signature(this))) {
            recreate();
            return;
        }
        if (isIgnoringBatteryOptimizations() != batteryIgnoring) {
            recreate();
        }
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
        WARN_TEXT = palette.warnText;
        RED = palette.red;
        GREEN = palette.green;
        SWITCH_THUMB = palette.switchThumb;
        themeSignature = ThemeManager.signature(this);
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuListener);
        super.onDestroy();
    }

    /**
     * Adds a collapsible settings section and returns its body container.
     * The expanded state is remembered by key across recreations.
     */
    private LinearLayout addSection(LinearLayout root, String key,
                                    int titleRes, int summaryRes, int iconRes) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBackground());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(14);
        root.addView(card, params);
        sectionCards.put(key, card);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(15), dp(12), dp(15));
        header.setClickable(true);
        header.setFocusable(true);
        header.setBackground(new RippleDrawable(
                ColorStateList.valueOf(RIPPLE), null, null));

        FrameLayout iconTile = new FrameLayout(this);
        GradientDrawable tileBackground = new GradientDrawable();
        tileBackground.setColor((ACCENT_TEXT & 0x00FFFFFF) | 0x22000000);
        tileBackground.setCornerRadius(dp(11));
        iconTile.setBackground(tileBackground);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ACCENT_TEXT);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams iconInner = new FrameLayout.LayoutParams(
                dp(20), dp(20));
        iconInner.gravity = Gravity.CENTER;
        iconTile.addView(icon, iconInner);

        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                dp(36), dp(36));
        iconParams.setMarginEnd(dp(12));
        header.addView(iconTile, iconParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(getString(titleRes), 16, TEXT, true));
        TextView summary = text(getString(summaryRes), 11, MUTED, false);
        summary.setPadding(0, dp(3), 0, 0);
        labels.addView(summary);
        header.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = text("\u203A", 22, MUTED, true);
        chevron.setGravity(Gravity.CENTER);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(chevron, new LinearLayout.LayoutParams(dp(28), dp(36)));
        card.addView(header);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), 0, dp(18), dp(16));
        card.addView(body);

        boolean expanded = isSectionExpanded(key);
        applySectionState(body, chevron, expanded);
        header.setOnClickListener(v -> {
            boolean next = body.getVisibility() != View.VISIBLE;
            applySectionState(body, chevron, next);
            setSectionExpanded(key, next);
        });
        return body;
    }

    private void applySectionState(LinearLayout body, TextView chevron, boolean expanded) {
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        chevron.setRotation(expanded ? 90f : 0f);
    }

    private boolean isSectionExpanded(String key) {
        return getSharedPreferences(SECTION_PREFS, MODE_PRIVATE)
                .getBoolean(key, SECTION_GENERAL.equals(key));
    }

    private void setSectionExpanded(String key, boolean expanded) {
        getSharedPreferences(SECTION_PREFS, MODE_PRIVATE)
                .edit().putBoolean(key, expanded).apply();
    }

    private void addSubsectionDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.topMargin = dp(16);
        parent.addView(divider, params);
    }

    private TextView subsectionTitle(String value, int color) {
        TextView title = text(value, 14, color, true);
        title.setPadding(0, dp(12), 0, dp(2));
        return title;
    }

    private void addSourcesCard(LinearLayout card) {
        TextView sourceHint = text(
                getString(R.string.settings_sources_hint), 12, MUTED, false);
        sourceHint.setLineSpacing(dp(3), 1f);
        sourceHint.setPadding(0, dp(4), 0, dp(6));
        card.addView(sourceHint);

        TextView sourceWebViewHint = text(
                getString(R.string.settings_webview_hint), 11, WARN_TEXT, false);
        sourceWebViewHint.setLineSpacing(dp(2), 1f);
        sourceWebViewHint.setPadding(0, 0, 0, dp(4));
        card.addView(sourceWebViewHint);

        TextView dragHint = text(
                getString(R.string.settings_source_drag_hint), 11, MUTED, false);
        dragHint.setLineSpacing(dp(2), 1f);
        dragHint.setPadding(0, 0, 0, dp(8));
        card.addView(dragHint);

        for (String key : sourceDisplayOrder()) {
            addSourceRow(card, key, sourceTitle(key), sourceDescription(key));
        }
    }

    /** Enabled sources in saved order, followed by disabled sources. */
    private List<String> sourceDisplayOrder() {
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

    private String sourceTitle(String key) {
        switch (key) {
            case SourceSettings.APKPURE: return getString(R.string.source_apkpure);
            case SourceSettings.FALLBACK:
                return getString(R.string.settings_source_fallback_title);
            case SourceSettings.APKMIRROR: return getString(R.string.source_apkmirror);
            case SourceSettings.UPTODOWN:
                return getString(R.string.settings_source_uptodown_title);
            case SourceSettings.FDROID: return getString(R.string.source_fdroid);
            case SourceSettings.APTOIDE: return getString(R.string.source_aptoide);
            case SourceSettings.APPTEKA:
                return getString(R.string.settings_source_appteka_title);
            case SourceSettings.GITHUB:
                return getString(R.string.settings_source_github_title);
            default: return AppSettings.sourceLabel(this, key);
        }
    }

    private String sourceDescription(String key) {
        switch (key) {
            case SourceSettings.APKPURE:
                return getString(R.string.settings_source_apkpure_desc);
            case SourceSettings.FALLBACK:
                return getString(R.string.settings_source_fallback_desc);
            case SourceSettings.APKMIRROR:
                return getString(R.string.settings_source_apkmirror_desc);
            case SourceSettings.UPTODOWN:
                return getString(R.string.settings_source_uptodown_desc);
            case SourceSettings.FDROID:
                return getString(R.string.settings_source_fdroid_desc);
            case SourceSettings.APTOIDE:
                return getString(R.string.settings_source_aptoide_desc);
            case SourceSettings.APPTEKA:
                return getString(R.string.settings_source_appteka_desc);
            case SourceSettings.GITHUB:
                return getString(R.string.settings_source_github_desc);
            default: return "";
        }
    }

    private void addLanguageRow(LinearLayout root) {
        LinearLayout row = settingRow(getString(R.string.settings_language),
                currentLanguageLabel());
        row.setOnClickListener(v -> showLanguageDialog());
        root.addView(row);
    }

    private void addAppearanceRow(LinearLayout root) {
        LinearLayout row = settingRow(getString(R.string.settings_appearance),
                ThemeManager.summary(this));
        row.setOnClickListener(v ->
                startActivity(new Intent(this, AppearanceActivity.class)));
        root.addView(row);
    }

    private String currentLanguageLabel() {
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleManager manager = getSystemService(LocaleManager.class);
            if (manager != null) {
                LocaleList locales = manager.getApplicationLocales();
                if (!locales.isEmpty()) {
                    return languageLabel(locales.get(0));
                }
            }
        }
        return getString(R.string.settings_language_system);
    }

    private String languageLabel(Locale locale) {
        return locale == null ? getString(R.string.settings_language_system)
                : locale.getDisplayLanguage(locale);
    }

    private List<String> supportedLanguageTags() {
        List<String> tags = new ArrayList<>();
        try (XmlResourceParser parser = getResources().getXml(R.xml.locales_config)) {
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "locale".equals(parser.getName())) {
                    String tag = parser.getAttributeValue(
                            "http://schemas.android.com/apk/res/android", "name");
                    if (tag == null) {
                        tag = parser.getAttributeValue(null, "name");
                    }
                    if (tag != null && !tag.isEmpty() && !tags.contains(tag)) {
                        tags.add(tag);
                    }
                }
            }
        } catch (Exception error) {
            HelperLog.error("Could not read locales_config.xml", error);
        }
        if (tags.isEmpty()) {
            tags.add("en");
            tags.add("fr");
        }
        return tags;
    }

    private void showLanguageDialog() {
        if (Build.VERSION.SDK_INT < 33) {
            ThemeManager.dialog(this)
                    .setTitle(R.string.settings_language)
                    .setMessage(R.string.settings_language_android13)
                    .setPositiveButton(R.string.common_ok, null)
                    .show();
            return;
        }

        LocaleManager manager = getSystemService(LocaleManager.class);
        if (manager == null) {
            return;
        }
        List<String> tags = supportedLanguageTags();
        String[] labels = new String[tags.size() + 1];
        labels[0] = getString(R.string.settings_language_system);
        int selected = 0;
        LocaleList current = manager.getApplicationLocales();
        String currentLanguage = current.isEmpty()
                ? "" : current.get(0).getLanguage();
        for (int i = 0; i < tags.size(); i++) {
            Locale locale = Locale.forLanguageTag(tags.get(i));
            labels[i + 1] = languageLabel(locale);
            if (!currentLanguage.isEmpty()
                    && currentLanguage.equals(locale.getLanguage())) {
                selected = i + 1;
            }
        }

        ThemeManager.dialog(this)
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    LocaleList locales = which == 0
                            ? LocaleList.getEmptyLocaleList()
                            : LocaleList.forLanguageTags(tags.get(which - 1));
                    manager.setApplicationLocales(locales);
                    dialog.dismiss();
                    recreate();
                })
                .show();
    }

    private void addLauncherIconCard(LinearLayout card) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(getString(R.string.settings_hide_launcher_icon), 14, TEXT, true));
        TextView detail = text(
                getString(R.string.settings_hide_launcher_icon_detail), 12, MUTED, false);
        detail.setPadding(0, dp(3), 0, 0);
        labels.addView(detail);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(AppSettings.isLauncherIconShown(this));
        styleSwitch(toggle);
        final boolean[] updating = {false};
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updating[0]) {
                return;
            }
            if (isChecked) {
                AppSettings.setLauncherIconShown(this, true);
                PublicSettings.write(this);
                Toast.makeText(this, R.string.settings_hide_launcher_icon_shown,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            updating[0] = true;
            buttonView.setChecked(true);
            updating[0] = false;
            ThemeManager.dialog(this)
                    .setTitle(R.string.settings_hide_launcher_icon_confirm_title)
                    .setMessage(R.string.settings_hide_launcher_icon_confirm_message)
                    .setNegativeButton(R.string.common_cancel, null)
                    .setPositiveButton(R.string.settings_hide_launcher_icon_confirm,
                            (dialog, which) -> {
                                AppSettings.setLauncherIconShown(this, false);
                                PublicSettings.write(this);
                                updating[0] = true;
                                buttonView.setChecked(false);
                                updating[0] = false;
                                Toast.makeText(this,
                                        R.string.settings_hide_launcher_icon_hidden,
                                        Toast.LENGTH_LONG).show();
                            })
                    .show();
        });
        row.addView(toggle, new LinearLayout.LayoutParams(dp(64), dp(48)));
        card.addView(row);
    }

    private void addUpdatesCard(LinearLayout card) {
        String current = UpdateChecker.installedVersion(this);
        UpdateInfo latest = UpdateManager.cachedLatest(this);
        String status;
        if (latest == null) {
            status = getString(R.string.settings_status_not_checked, current);
        } else if (latest.isNewerThan(current)) {
            status = getString(R.string.settings_status_available, current, latest.version);
        } else {
            status = getString(R.string.settings_status_up_to_date, current);
        }
        TextView statusView = text(status, 13, MUTED, false);
        statusView.setPadding(0, dp(6), 0, dp(6));
        card.addView(statusView);

        LinearLayout enabledRow = new LinearLayout(this);
        enabledRow.setOrientation(LinearLayout.HORIZONTAL);
        enabledRow.setGravity(Gravity.CENTER_VERTICAL);
        enabledRow.setPadding(0, dp(6), 0, dp(6));
        LinearLayout enabledLabels = new LinearLayout(this);
        enabledLabels.setOrientation(LinearLayout.VERTICAL);
        enabledLabels.addView(text(getString(R.string.settings_check_updates), 14, TEXT, true));
        TextView enabledDetail = text(
                getString(R.string.settings_check_updates_detail), 12, MUTED, false);
        enabledDetail.setPadding(0, dp(3), 0, 0);
        enabledLabels.addView(enabledDetail);
        enabledRow.addView(enabledLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch enabledSwitch = new Switch(this);
        enabledSwitch.setChecked(UpdateManager.isEnabled(this));
        styleSwitch(enabledSwitch);
        enabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UpdateManager.setEnabled(this, isChecked);
            PublicSettings.write(this);
            if (isChecked) {
                Toast.makeText(this, R.string.settings_checks_enabled, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.settings_checks_disabled, Toast.LENGTH_SHORT).show();
            }
        });
        enabledRow.addView(enabledSwitch, new LinearLayout.LayoutParams(dp(64), dp(48)));
        card.addView(enabledRow);

        String mode = UpdateManager.mode(this);
        String modeLabel = UpdateManager.MODE_SHIZUKU.equals(mode)
                ? getString(R.string.settings_mode_shizuku)
                : getString(R.string.settings_mode_notify);
        LinearLayout modeRow = settingRow(getString(R.string.settings_update_mode), modeLabel);
        modeRow.setOnClickListener(v -> ThemeManager.dialog(this)
                .setTitle(R.string.settings_update_mode)
                .setSingleChoiceItems(new String[]{
                        getString(R.string.settings_mode_notify_option),
                        getString(R.string.settings_mode_shizuku_option)
                }, UpdateManager.MODE_SHIZUKU.equals(mode) ? 1 : 0, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) {
                        UpdateManager.setMode(this, UpdateManager.MODE_NOTIFY);
                        PublicSettings.write(this);
                        recreate();
                    } else {
                        requestShizukuMode();
                    }
                })
                .show());
        card.addView(modeRow);

        if (!UpdateManager.MODE_SHIZUKU.equals(mode)) {
            LinearLayout popupRow = new LinearLayout(this);
            popupRow.setOrientation(LinearLayout.HORIZONTAL);
            popupRow.setGravity(Gravity.CENTER_VERTICAL);
            popupRow.setPadding(0, dp(6), 0, dp(6));
            LinearLayout popupLabels = new LinearLayout(this);
            popupLabels.setOrientation(LinearLayout.VERTICAL);
            popupLabels.addView(text(getString(R.string.settings_update_popup), 14, TEXT, true));
            TextView popupDetail = text(
                    getString(R.string.settings_update_popup_desc), 12, MUTED, false);
            popupDetail.setPadding(0, dp(3), 0, 0);
            popupLabels.addView(popupDetail);
            popupRow.addView(popupLabels, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Switch popupSwitch = new Switch(this);
            popupSwitch.setChecked(UpdateManager.isInAppPopupEnabled(this));
            styleSwitch(popupSwitch);
            popupSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                UpdateManager.setInAppPopupEnabled(this, isChecked);
                PublicSettings.write(this);
                Toast.makeText(this,
                        isChecked ? R.string.settings_update_popup_enabled
                                : R.string.settings_update_popup_disabled,
                        Toast.LENGTH_SHORT).show();
            });
            popupRow.addView(popupSwitch, new LinearLayout.LayoutParams(dp(64), dp(48)));
            card.addView(popupRow);
        }

        int shizukuStatus = ShizukuHelper.status(this);
        LinearLayout shizukuRow = settingRow("Shizuku",
                ShizukuHelper.statusLabel(this, shizukuStatus));
        shizukuRow.setOnClickListener(v -> requestShizukuMode());
        card.addView(shizukuRow);

        TextView check = actionButton(getString(R.string.settings_check_now), PRIMARY_START);
        check.setOnClickListener(v -> {
            Toast.makeText(this, R.string.settings_checking, Toast.LENGTH_SHORT).show();
            UpdateManager.checkAsync(this, true, message -> {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                recreate();
            });
        });
        card.addView(check);

        TextView changelog = actionButton(getString(R.string.settings_changelog), PRIMARY_START);
        changelog.setOnClickListener(v -> startActivity(new Intent(this, UpdateActivity.class)
                .putExtra(UpdateActivity.EXTRA_CHANGELOG, true)));
        card.addView(changelog);

        if (latest != null && latest.isNewerThan(current) && !latest.releaseUrl.isEmpty()) {
            TextView download = actionButton(
                    getString(R.string.settings_view_update, latest.version), PRIMARY_START);
            download.setOnClickListener(v ->
                    startActivity(new Intent(this, UpdateActivity.class)));
            card.addView(download);
        }

        TextView note = text(getString(R.string.settings_shizuku_note), 11, MUTED, false);
        note.setLineSpacing(dp(3), 1f);
        note.setPadding(0, dp(8), 0, 0);
        card.addView(note);
    }

    private void addBatteryCard(LinearLayout card) {
        batteryIgnoring = isIgnoringBatteryOptimizations();
        LinearLayout row = settingRow(getString(R.string.settings_battery_optimization),
                getString(batteryIgnoring
                        ? R.string.settings_battery_unrestricted
                        : R.string.settings_battery_optimized),
                batteryIgnoring);
        row.setOnClickListener(v -> requestBatteryExemption());
        card.addView(row);

        TextView note = text(getString(R.string.settings_battery_note), 11, MUTED, false);
        note.setLineSpacing(dp(3), 1f);
        note.setPadding(0, dp(8), 0, 0);
        card.addView(note);
    }

    private void addBackupCard(LinearLayout card) {
        LinearLayout export = settingRow(getString(R.string.settings_export),
                getString(R.string.settings_export_detail));
        export.setOnClickListener(v -> showExportDialog());
        card.addView(export);

        LinearLayout importRow = settingRow(getString(R.string.settings_import),
                getString(R.string.settings_import_detail));
        importRow.setOnClickListener(v -> showImportConfirm());
        card.addView(importRow);
    }

    private void showExportDialog() {
        ThemeManager.dialog(this)
                .setTitle(R.string.settings_export_title)
                .setItems(new String[]{
                        getString(R.string.settings_export_without_secrets),
                        getString(R.string.settings_export_with_secrets)
                }, (dialog, which) -> launchExport(which == 1))
                .show();
    }

    private void launchExport(boolean includeSecrets) {
        exportIncludeSecrets = includeSecrets;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TITLE, "morphe-helper-settings.txt");
        try {
            startActivityForResult(intent, EXPORT_SETTINGS_REQUEST_CODE);
        } catch (Exception error) {
            Toast.makeText(this, R.string.settings_export_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showImportConfirm() {
        ThemeManager.dialog(this)
                .setTitle(R.string.settings_import_confirm_title)
                .setMessage(R.string.settings_import_confirm_message)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.settings_import, (dialog, which) ->
                        launchImport())
                .show();
    }

    private void launchImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        try {
            startActivityForResult(intent, IMPORT_SETTINGS_REQUEST_CODE);
        } catch (Exception error) {
            Toast.makeText(this, R.string.settings_import_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private String readImportedText(Uri uri) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) {
                return null;
            }
            byte[] buffer = new byte[8 * 1024];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > 1024 * 1024) {
                    throw new Exception("Configuration file is too large");
                }
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void writeExportedText(Uri uri, String text) throws Exception {
        try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) {
                throw new Exception("Could not open destination");
            }
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private boolean isIgnoringBatteryOptimizations() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        return power != null && power.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void requestBatteryExemption() {
        if (isIgnoringBatteryOptimizations()) {
            Toast.makeText(this, R.string.settings_battery_already_unrestricted,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        ThemeManager.dialog(this)
                .setTitle(R.string.settings_battery_dialog_title)
                .setMessage(R.string.settings_battery_dialog_message)
                .setNegativeButton(R.string.settings_not_now, null)
                .setPositiveButton(R.string.settings_request_exemption, (dialog, which) -> {
                    try {
                        startActivity(new Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception error) {
                        try {
                            startActivity(new Intent(
                                    Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                        } catch (Exception ignored) {
                        }
                    }
                })
                .show();
    }

    private void addSecurityCard(LinearLayout card) {
        LinearLayout scanRow = new LinearLayout(this);
        scanRow.setOrientation(LinearLayout.HORIZONTAL);
        scanRow.setGravity(Gravity.CENTER_VERTICAL);
        scanRow.setPadding(0, dp(8), 0, dp(8));
        LinearLayout scanLabels = new LinearLayout(this);
        scanLabels.setOrientation(LinearLayout.VERTICAL);
        scanLabels.addView(text(getString(R.string.virustotal_hash_check), 14, TEXT, true));
        TextView scanDetail = text(getString(R.string.virustotal_hash_check_detail),
                12, MUTED, false);
        scanDetail.setPadding(0, dp(3), 0, 0);
        scanLabels.addView(scanDetail);
        scanRow.addView(scanLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch scanSwitch = new Switch(this);
        scanSwitch.setChecked(AppSettings.virusTotalEnabled(this));
        styleSwitch(scanSwitch);
        scanSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setVirusTotalEnabled(this, isChecked);
            PublicSettings.write(this);
            setVirusTotalDetailsVisible(card, isChecked);
            if (isChecked && AppSettings.virusTotalApiKey(this).isEmpty()) {
                Toast.makeText(this, R.string.virustotal_add_key_toast,
                        Toast.LENGTH_LONG).show();
            }
        });
        scanRow.addView(scanSwitch, new LinearLayout.LayoutParams(dp(64), dp(48)));
        card.addView(scanRow);

        boolean hasKey = !AppSettings.virusTotalApiKey(this).isEmpty();
        LinearLayout keyRow = settingRow(getString(R.string.virustotal_api_key),
                getString(hasKey ? R.string.virustotal_configured
                        : R.string.virustotal_not_set));
        keyRow.setOnClickListener(v -> showVirusTotalKeyDialog());
        card.addView(keyRow);

        LinearLayout blockRow = new LinearLayout(this);
        blockRow.setOrientation(LinearLayout.HORIZONTAL);
        blockRow.setGravity(Gravity.CENTER_VERTICAL);
        blockRow.setPadding(0, dp(8), 0, dp(8));
        LinearLayout blockLabels = new LinearLayout(this);
        blockLabels.setOrientation(LinearLayout.VERTICAL);
        blockLabels.addView(text(getString(R.string.virustotal_block_detected),
                14, TEXT, true));
        TextView blockDetail = text(getString(R.string.virustotal_block_detail),
                12, MUTED, false);
        blockDetail.setPadding(0, dp(3), 0, 0);
        blockLabels.addView(blockDetail);
        blockRow.addView(blockLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch blockSwitch = new Switch(this);
        blockSwitch.setChecked(AppSettings.virusTotalBlockDetections(this));
        styleSwitch(blockSwitch);
        blockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setVirusTotalBlockDetections(this, isChecked);
            PublicSettings.write(this);
        });
        blockRow.addView(blockSwitch, new LinearLayout.LayoutParams(dp(64), dp(48)));
        card.addView(blockRow);

        LinearLayout uploadRow = new LinearLayout(this);
        uploadRow.setOrientation(LinearLayout.HORIZONTAL);
        uploadRow.setGravity(Gravity.CENTER_VERTICAL);
        uploadRow.setPadding(0, dp(8), 0, dp(8));
        LinearLayout uploadLabels = new LinearLayout(this);
        uploadLabels.setOrientation(LinearLayout.VERTICAL);
        uploadLabels.addView(text(getString(R.string.virustotal_upload_unknown),
                14, TEXT, true));
        TextView uploadDetail = text(
                getString(R.string.virustotal_upload_unknown_detail), 12, MUTED, false);
        uploadDetail.setPadding(0, dp(3), 0, 0);
        uploadLabels.addView(uploadDetail);
        uploadRow.addView(uploadLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch uploadSwitch = new Switch(this);
        uploadSwitch.setChecked(AppSettings.virusTotalUploadUnknown(this));
        styleSwitch(uploadSwitch);
        uploadSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setVirusTotalUploadUnknown(this, isChecked);
            PublicSettings.write(this);
            if (isChecked) {
                Toast.makeText(this, R.string.virustotal_unknown_upload_toast,
                        Toast.LENGTH_SHORT).show();
            }
        });
        uploadRow.addView(uploadSwitch, new LinearLayout.LayoutParams(dp(64), dp(48)));
        card.addView(uploadRow);

        LinearLayout alwaysUploadRow = new LinearLayout(this);
        alwaysUploadRow.setOrientation(LinearLayout.HORIZONTAL);
        alwaysUploadRow.setGravity(Gravity.CENTER_VERTICAL);
        alwaysUploadRow.setPadding(0, dp(8), 0, dp(8));
        LinearLayout alwaysUploadLabels = new LinearLayout(this);
        alwaysUploadLabels.setOrientation(LinearLayout.VERTICAL);
        alwaysUploadLabels.addView(text(getString(R.string.virustotal_always_upload),
                14, TEXT, true));
        TextView alwaysUploadDetail = text(
                getString(R.string.virustotal_always_upload_detail),
                12, MUTED, false);
        alwaysUploadDetail.setPadding(0, dp(3), 0, 0);
        alwaysUploadLabels.addView(alwaysUploadDetail);
        alwaysUploadRow.addView(alwaysUploadLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch alwaysUploadSwitch = new Switch(this);
        alwaysUploadSwitch.setChecked(AppSettings.virusTotalAlwaysUpload(this));
        styleSwitch(alwaysUploadSwitch);
        alwaysUploadSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setVirusTotalAlwaysUpload(this, isChecked);
            PublicSettings.write(this);
            if (isChecked) {
                Toast.makeText(this, R.string.virustotal_always_upload_toast,
                        Toast.LENGTH_LONG).show();
            }
        });
        alwaysUploadRow.addView(alwaysUploadSwitch,
                new LinearLayout.LayoutParams(dp(64), dp(48)));
        card.addView(alwaysUploadRow);

        LinearLayout quotaUploadRow = new LinearLayout(this);
        quotaUploadRow.setOrientation(LinearLayout.HORIZONTAL);
        quotaUploadRow.setGravity(Gravity.CENTER_VERTICAL);
        quotaUploadRow.setPadding(0, dp(8), 0, dp(8));
        LinearLayout quotaUploadLabels = new LinearLayout(this);
        quotaUploadLabels.setOrientation(LinearLayout.VERTICAL);
        quotaUploadLabels.addView(text(getString(R.string.virustotal_upload_on_quota),
                14, TEXT, true));
        TextView quotaUploadDetail = text(
                getString(R.string.virustotal_upload_on_quota_detail),
                12, MUTED, false);
        quotaUploadDetail.setPadding(0, dp(3), 0, 0);
        quotaUploadLabels.addView(quotaUploadDetail);
        quotaUploadRow.addView(quotaUploadLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch quotaUploadSwitch = new Switch(this);
        quotaUploadSwitch.setChecked(AppSettings.virusTotalUploadOnQuota(this));
        styleSwitch(quotaUploadSwitch);
        quotaUploadSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setVirusTotalUploadOnQuota(this, isChecked);
            PublicSettings.write(this);
        });
        quotaUploadRow.addView(quotaUploadSwitch,
                new LinearLayout.LayoutParams(dp(64), dp(48)));
        card.addView(quotaUploadRow);

        TextView uploadInfo = text(getString(R.string.virustotal_upload_info),
                11, MUTED, false);
        uploadInfo.setLineSpacing(dp(3), 1f);
        uploadInfo.setPadding(0, dp(4), 0, 0);
        card.addView(uploadInfo);

        boolean abortOnQuota = AppSettings.virusTotalAbortOnQuota(this);
        LinearLayout quotaRow = settingRow(getString(R.string.virustotal_quota_exceeded),
                getString(abortOnQuota ? R.string.virustotal_quota_abort
                        : R.string.virustotal_quota_continue));
        quotaRow.setOnClickListener(v -> ThemeManager.dialog(this)
                .setTitle(R.string.virustotal_quota_dialog_title)
                .setSingleChoiceItems(new String[]{
                        getString(R.string.virustotal_quota_continue),
                        getString(R.string.virustotal_quota_dialog_abort)
                }, AppSettings.virusTotalAbortOnQuota(this) ? 1 : 0, (dialog, which) -> {
                    AppSettings.setVirusTotalAbortOnQuota(this, which == 1);
                    PublicSettings.write(this);
                    dialog.dismiss();
                    recreate();
                })
                .show());
        card.addView(quotaRow);

        TextView quota = text(getString(R.string.virustotal_api_limits),
                11, MUTED, false);
        quota.setLineSpacing(dp(3), 1f);
        quota.setPadding(0, dp(8), 0, 0);
        card.addView(quota);

        TextView note = text(getString(R.string.virustotal_note),
                11, MUTED, false);
        note.setLineSpacing(dp(3), 1f);
        note.setPadding(0, dp(6), 0, 0);
        card.addView(note);

        setVirusTotalDetailsVisible(card, AppSettings.virusTotalEnabled(this));
    }

    /**
     * Ad/tracker blocking for the source WebViews. This is deliberately a
     * content filter, not a DNS/VPN: it adds no permission prompt and only
     * affects the pages the helper opens itself.
     */
    private void addWebPagesCard(LinearLayout card) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(getString(R.string.settings_block_ads), 14, TEXT, true));
        TextView detail = text(
                getString(R.string.settings_block_ads_detail), 12, MUTED, false);
        detail.setLineSpacing(dp(2), 1f);
        detail.setPadding(0, dp(3), 0, 0);
        labels.addView(detail);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(AppSettings.blockWebAds(this));
        styleSwitch(toggle);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setBlockWebAds(this, isChecked);
            PublicSettings.write(this);
            setWebPagesDetailsVisible(card, isChecked);
        });
        row.addView(toggle, new LinearLayout.LayoutParams(dp(64), dp(48)));
        card.addView(row);

        LinearLayout liveRow = new LinearLayout(this);
        liveRow.setOrientation(LinearLayout.HORIZONTAL);
        liveRow.setGravity(Gravity.CENTER_VERTICAL);
        liveRow.setPadding(0, dp(8), 0, dp(8));

        LinearLayout liveLabels = new LinearLayout(this);
        liveLabels.setOrientation(LinearLayout.VERTICAL);
        liveLabels.addView(text(getString(R.string.settings_live_adguard), 14, TEXT, true));
        TextView liveDetail = text(
                getString(R.string.settings_live_adguard_detail), 12, MUTED, false);
        liveDetail.setLineSpacing(dp(2), 1f);
        liveDetail.setPadding(0, dp(3), 0, 0);
        liveLabels.addView(liveDetail);
        liveRow.addView(liveLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch liveSwitch = new Switch(this);
        liveSwitch.setChecked(AppSettings.liveAdGuardChecks(this));
        styleSwitch(liveSwitch);
        liveSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setLiveAdGuardChecks(this, isChecked);
            PublicSettings.write(this);
        });
        liveRow.addView(liveSwitch, new LinearLayout.LayoutParams(dp(64), dp(48)));
        card.addView(liveRow);

        TextView note = text(getString(R.string.settings_block_ads_note), 11, MUTED, false);
        note.setLineSpacing(dp(3), 1f);
        note.setPadding(0, dp(4), 0, 0);
        card.addView(note);

        setWebPagesDetailsVisible(card, AppSettings.blockWebAds(this));
    }

    /** Keeps the live-check row and note visible only while the master switch is on. */
    private void setWebPagesDetailsVisible(LinearLayout card, boolean visible) {
        int state = visible ? View.VISIBLE : View.GONE;
        for (int i = 1; i < card.getChildCount(); i++) {
            card.getChildAt(i).setVisibility(state);
        }
    }

    /** Keeps the master switch visible and hides its dependent rows when off. */
    private void setVirusTotalDetailsVisible(LinearLayout card, boolean visible) {
        int state = visible ? View.VISIBLE : View.GONE;
        for (int i = 1; i < card.getChildCount(); i++) {
            card.getChildAt(i).setVisibility(state);
        }
    }

    private void showVirusTotalKeyDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setSingleLine(true);
        input.setText(AppSettings.virusTotalApiKey(this));
        input.setSelection(input.getText().length());
        int padding = dp(16);
        input.setPadding(padding, padding, padding, padding);

        ThemeManager.dialog(this)
                .setTitle(R.string.virustotal_api_key_dialog_title)
                .setMessage(R.string.virustotal_api_key_dialog_message)
                .setView(input)
                .setNegativeButton(R.string.common_cancel, null)
                .setNeutralButton(R.string.virustotal_api_key_clear, (dialog, which) -> {
                    AppSettings.setVirusTotalApiKey(this, "");
                    recreate();
                })
                .setPositiveButton(R.string.virustotal_api_key_save, (dialog, which) -> {
                    AppSettings.setVirusTotalApiKey(this, input.getText().toString());
                    recreate();
                })
                .show();
    }

    private void requestShizukuMode() {
        int status = ShizukuHelper.status(this);
        if (status == ShizukuHelper.READY) {
            UpdateManager.setMode(this, UpdateManager.MODE_SHIZUKU);
            PublicSettings.write(this);
            Toast.makeText(this, R.string.settings_automatic_enabled,
                    Toast.LENGTH_SHORT).show();
            recreate();
            return;
        }
        if (status == ShizukuHelper.PERMISSION_REQUIRED) {
            ThemeManager.dialog(this)
                    .setTitle(R.string.settings_shizuku_dialog_title)
                    .setMessage(R.string.settings_shizuku_dialog_message)
                    .setNegativeButton(R.string.settings_use_notifications, (dialog, which) -> {
                        UpdateManager.setMode(this, UpdateManager.MODE_NOTIFY);
                        PublicSettings.write(this);
                        recreate();
                    })
                    .setPositiveButton(R.string.settings_grant_permission, (dialog, which) ->
                            ShizukuHelper.requestPermission(SHIZUKU_REQUEST_CODE))
                    .show();
            return;
        }
        if (status == ShizukuHelper.NOT_RUNNING) {
            ThemeManager.dialog(this)
                    .setTitle(R.string.settings_shizuku_not_running_title)
                    .setMessage(R.string.settings_shizuku_not_running_message)
                    .setNegativeButton(R.string.common_cancel, null)
                    .setPositiveButton(R.string.settings_open_shizuku, (dialog, which) -> openShizuku())
                    .show();
            return;
        }
        if (status == ShizukuHelper.NOT_INSTALLED) {
            ThemeManager.dialog(this)
                    .setTitle(R.string.settings_shizuku_not_installed_title)
                    .setMessage(R.string.settings_shizuku_not_installed_message)
                    .setNegativeButton(R.string.settings_use_notifications, null)
                    .setPositiveButton(R.string.settings_get_shizuku, (dialog, which) -> {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://shizuku.rikka.app/download/")));
                        } catch (Exception ignored) {
                        }
                    })
                    .show();
            return;
        }
        Toast.makeText(this, R.string.settings_shizuku_unsupported,
                Toast.LENGTH_SHORT).show();
    }

    private void openShizuku() {
        Intent launch = getPackageManager()
                .getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (launch != null) {
            startActivity(launch);
        } else {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://shizuku.rikka.app/download/")));
            } catch (Exception ignored) {
            }
        }
    }

    private void addStorageCard(LinearLayout card) {
        String location = AppSettings.location(this);
        LinearLayout folder = settingRow(getString(R.string.settings_download_folder),
                AppSettings.locationLabel(this));
        folder.setOnClickListener(v -> ThemeManager.dialog(this)
                .setTitle(R.string.settings_download_folder)
                .setSingleChoiceItems(new String[]{
                        getString(R.string.settings_folder_public),
                        getString(R.string.settings_folder_internal),
                        getString(R.string.settings_folder_external),
                        getString(R.string.settings_folder_custom)
                }, locationIndex(location), (dialog, which) -> {
                    dialog.dismiss();
                    chooseLocation(which);
                })
                .show());
        card.addView(folder);

        String retention = AppSettings.retention(this);
        String retentionLabel;
        switch (retention) {
            case AppSettings.AFTER_HANDOFF: retentionLabel = getString(R.string.settings_retention_handoff); break;
            case AppSettings.HOUR_1: retentionLabel = getString(R.string.settings_retention_1h); break;
            case AppSettings.HOUR_24: retentionLabel = getString(R.string.settings_retention_24h); break;
            case AppSettings.DAY_7: retentionLabel = getString(R.string.settings_retention_7d); break;
            default: retentionLabel = getString(R.string.settings_keep_forever); break;
        }
        LinearLayout keep = settingRow(getString(R.string.settings_keep_downloads), retentionLabel);
        keep.setOnClickListener(v -> ThemeManager.dialog(this)
                .setTitle(R.string.settings_keep_downloads)
                .setSingleChoiceItems(new String[]{
                        getString(R.string.settings_keep_forever),
                        getString(R.string.settings_retention_handoff),
                        getString(R.string.settings_retention_1h),
                        getString(R.string.settings_retention_24h),
                        getString(R.string.settings_retention_7d)
                }, retentionIndex(retention), (dialog, which) -> {
                    String[] values = {AppSettings.KEEP, AppSettings.AFTER_HANDOFF,
                            AppSettings.HOUR_1, AppSettings.HOUR_24, AppSettings.DAY_7};
                    AppSettings.setRetention(this, values[which]);
                    dialog.dismiss();
                    recreate();
                })
                .show());
        card.addView(keep);

        TextView note = text(getString(R.string.settings_cleanup_note), 11, MUTED, false);
        note.setPadding(0, dp(6), 0, dp(10));
        card.addView(note);

        LinearLayout multiRow = new LinearLayout(this);
        multiRow.setOrientation(LinearLayout.HORIZONTAL);
        multiRow.setGravity(Gravity.CENTER_VERTICAL);
        multiRow.setPadding(0, dp(8), 0, dp(8));
        LinearLayout multiLabels = new LinearLayout(this);
        multiLabels.setOrientation(LinearLayout.VERTICAL);
        multiLabels.addView(text(getString(R.string.settings_multi_thread), 14, TEXT, true));
        TextView multiDetail = text(
                getString(R.string.settings_multi_thread_detail), 11, MUTED, false);
        multiDetail.setLineSpacing(dp(2), 1f);
        multiDetail.setPadding(0, dp(3), 0, 0);
        multiLabels.addView(multiDetail);
        multiRow.addView(multiLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch multiSwitch = new Switch(this);
        multiSwitch.setChecked(AppSettings.multiThreadDownloads(this));
        styleSwitch(multiSwitch);
        multiSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setMultiThreadDownloads(this, isChecked);
            PublicSettings.write(this);
            Toast.makeText(this, isChecked
                            ? R.string.settings_multi_thread_enabled
                            : R.string.settings_multi_thread_disabled,
                    Toast.LENGTH_SHORT).show();
        });
        multiRow.addView(multiSwitch, new LinearLayout.LayoutParams(dp(64), dp(48)));
        card.addView(multiRow);

        TextView delete = actionButton(getString(R.string.settings_delete_all), RED);
        delete.setOnClickListener(v -> ThemeManager.dialog(this)
                .setTitle(R.string.settings_delete_dialog_title)
                .setMessage(R.string.settings_delete_dialog_message)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_delete, (dialog, which) -> {
                    int count = AppSettings.deleteAllDownloads(this);
                    Toast.makeText(this,
                            getResources().getQuantityString(
                                    R.plurals.settings_files_deleted, count, count),
                            Toast.LENGTH_SHORT).show();
                })
                .show());
        card.addView(delete);
    }

    private int locationIndex(String location) {
        if (AppSettings.LOCATION_PUBLIC.equals(location)) return 0;
        if (AppSettings.LOCATION_INTERNAL.equals(location)) return 1;
        if (AppSettings.LOCATION_EXTERNAL.equals(location)) return 2;
        return 3;
    }

    private void chooseLocation(int which) {
        if (which == 3) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try {
                startActivityForResult(intent, FOLDER_REQUEST_CODE);
            } catch (Exception error) {
                Toast.makeText(this, R.string.settings_no_folder_picker,
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        String value = which == 0 ? AppSettings.LOCATION_PUBLIC
                : which == 1 ? AppSettings.LOCATION_INTERNAL
                : AppSettings.LOCATION_EXTERNAL;
        if (AppSettings.LOCATION_PUBLIC.equals(value)
                && Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingLocation = value;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_CODE);
            return;
        }
        AppSettings.setLocation(this, value);
        recreate();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == EXPORT_SETTINGS_REQUEST_CODE) {
            try {
                writeExportedText(uri,
                        PublicSettings.exportText(this, exportIncludeSecrets));
                Toast.makeText(this, R.string.settings_export_success,
                        Toast.LENGTH_SHORT).show();
            } catch (Exception error) {
                HelperLog.error("Could not export configuration", error);
                Toast.makeText(this, R.string.settings_export_failed,
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (requestCode == IMPORT_SETTINGS_REQUEST_CODE) {
            try {
                String text = readImportedText(uri);
                if (TextUtils.isEmpty(text)) {
                    Toast.makeText(this, R.string.settings_import_empty,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                PublicSettings.applyText(this, text);
                PublicSettings.write(this);
                Toast.makeText(this, R.string.settings_import_success,
                        Toast.LENGTH_SHORT).show();
                recreate();
            } catch (Exception error) {
                HelperLog.error("Could not import configuration", error);
                Toast.makeText(this, R.string.settings_import_failed,
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (requestCode != FOLDER_REQUEST_CODE) {
            return;
        }
        Uri tree = uri;
        try {
            getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        AppSettings.setCustomTreeUri(this, tree.toString());
        AppSettings.setLocation(this, AppSettings.LOCATION_CUSTOM);
        recreate();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != STORAGE_PERMISSION_CODE) {
            return;
        }
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted && pendingLocation != null) {
            AppSettings.setLocation(this, pendingLocation);
            recreate();
        } else {
            Toast.makeText(this, R.string.settings_storage_denied,
                    Toast.LENGTH_LONG).show();
        }
        pendingLocation = null;
    }

    private int retentionIndex(String retention) {
        if (AppSettings.AFTER_HANDOFF.equals(retention)) return 1;
        if (AppSettings.HOUR_1.equals(retention)) return 2;
        if (AppSettings.HOUR_24.equals(retention)) return 3;
        if (AppSettings.DAY_7.equals(retention)) return 4;
        return 0;
    }

    private void addApptekaWarningCard(LinearLayout card) {
        addSubsectionDivider(card);
        card.addView(subsectionTitle(getString(R.string.settings_appteka_title), RED));

        TextView message = text(getString(R.string.settings_appteka_message),
                12, WARN_TEXT, false);
        message.setLineSpacing(dp(3), 1f);
        message.setPadding(0, dp(8), 0, dp(4));
        card.addView(message);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(getString(R.string.settings_appteka_snaptube_only), 14, TEXT, true));
        TextView detail = text(getString(R.string.settings_appteka_snaptube_only_detail),
                12, MUTED, false);
        detail.setPadding(0, dp(3), 0, 0);
        labels.addView(detail);
        row.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(AppSettings.apptekaSnapTubeOnly(this));
        styleSwitch(toggle);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setApptekaSnapTubeOnly(this, isChecked);
            PublicSettings.write(this);
            if (!isChecked) {
                Toast.makeText(this, R.string.settings_appteka_all_apps_warning,
                        Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                dp(64), dp(48));
        toggleParams.gravity = Gravity.CENTER_VERTICAL;
        row.addView(toggle, toggleParams);
        card.addView(row);

        TextView note = text(getString(R.string.settings_appteka_off_note), 11, MUTED, false);
        note.setPadding(0, dp(6), 0, 0);
        card.addView(note);
    }

    private void addGitHubAppsCard(LinearLayout card) {
        List<GitHubAppStore.Entry> entries = GitHubAppStore.all(this);

        addSubsectionDivider(card);
        card.addView(subsectionTitle(getString(R.string.settings_github_apps), TEXT));
        TextView note = text(getString(R.string.settings_github_apps_note), 12, MUTED, false);
        note.setLineSpacing(dp(3), 1f);
        note.setPadding(0, dp(4), 0, dp(4));
        card.addView(note);

        if (entries.isEmpty()) {
            TextView empty = text(getString(R.string.settings_github_apps_empty),
                    12, MUTED, false);
            empty.setLineSpacing(dp(3), 1f);
            empty.setPadding(0, dp(8), 0, 0);
            card.addView(empty);
            return;
        }

        for (GitHubAppStore.Entry entry : entries) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(text(entry.label, 14, TEXT, true));
            TextView repo = text(entry.repo, 12, MUTED, false);
            repo.setPadding(0, dp(3), 0, 0);
            labels.addView(repo);
            row.addView(labels, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView remove = text("X", 16, RED, true);
            remove.setGravity(Gravity.CENTER);
            remove.setPadding(dp(12), dp(8), dp(12), dp(8));
            remove.setContentDescription(
                    getString(R.string.settings_remove_github_app, entry.label));
            remove.setClickable(true);
            remove.setFocusable(true);
            remove.setOnClickListener(v -> {
                GitHubAppStore.remove(this, entry.packageName);
                Toast.makeText(this,
                        getString(R.string.settings_github_app_removed, entry.label),
                        Toast.LENGTH_SHORT).show();
                recreate();
            });
            row.addView(remove, new LinearLayout.LayoutParams(dp(48), dp(48)));
            card.addView(row);

            View divider = new View(this);
            divider.setBackgroundColor(BORDER);
            card.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        }
    }

    private void addOrderCard(LinearLayout card) {
        TextView orderTitle = subsectionTitle(getString(R.string.settings_source_order), TEXT);
        sourceOrderTarget = orderTitle;
        card.addView(orderTitle);
        TextView note = text(getString(R.string.settings_source_order_note), 12, MUTED, false);
        note.setPadding(0, dp(4), 0, dp(8));
        card.addView(note);

        LinearLayout orderList = new LinearLayout(this);
        orderList.setOrientation(LinearLayout.VERTICAL);
        card.addView(orderList);

        List<String> order = AppSettings.sourceOrder(this);
        for (int i = 0; i < order.size(); i++) {
            String key = order.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));
            row.setTag(key);

            TextView drag = text("\u2261", 18, MUTED, true);
            drag.setGravity(Gravity.CENTER);
            drag.setContentDescription(getString(R.string.settings_drag_source));
            drag.setPadding(dp(4), dp(4), dp(8), dp(4));
            drag.setOnLongClickListener(v -> {
                startSourceDrag(row);
                return true;
            });
            row.addView(drag, new LinearLayout.LayoutParams(
                    dp(30), dp(44)));

            View badge = SourceBadge.create(this, key);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    dp(32), dp(32));
            badgeParams.setMarginEnd(dp(8));
            row.addView(badge, badgeParams);

            TextView label = text((i + 1) + ". " + AppSettings.sourceLabel(this, key),
                    14, TEXT, false);
            row.addView(label, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView up = text("\u2191", 18, PRIMARY_START, true);
            up.setContentDescription(getString(R.string.settings_move_up));
            up.setPadding(dp(10), dp(4), dp(10), dp(4));
            up.setClickable(true);
            up.setOnClickListener(v -> {
                AppSettings.moveSource(this, key, -1);
                PublicSettings.write(this);
                recreate();
            });
            row.addView(up);
            TextView down = text("\u2193", 18, PRIMARY_START, true);
            down.setContentDescription(getString(R.string.settings_move_down));
            down.setPadding(dp(10), dp(4), dp(10), dp(4));
            down.setClickable(true);
            down.setOnClickListener(v -> {
                AppSettings.moveSource(this, key, 1);
                PublicSettings.write(this);
                recreate();
            });
            row.addView(down);

            row.setOnDragListener((view, event) -> {
                int action = event.getAction();
                if (action == DragEvent.ACTION_DRAG_ENTERED) {
                    view.setAlpha(0.65f);
                    return true;
                }
                if (action == DragEvent.ACTION_DRAG_EXITED) {
                    view.setAlpha(1f);
                    return true;
                }
                if (action == DragEvent.ACTION_DRAG_ENDED) {
                    view.setAlpha(1f);
                    return true;
                }
                if (action == DragEvent.ACTION_DROP) {
                    view.setAlpha(1f);
                    Object local = event.getLocalState();
                    if (local instanceof View) {
                        Object tag = ((View) local).getTag();
                        int target = orderList.indexOfChild(view);
                        if (tag != null && target >= 0) {
                            AppSettings.moveSourceTo(this, String.valueOf(tag), target);
                            PublicSettings.write(this);
                            recreate();
                        }
                    }
                    return true;
                }
                return action == DragEvent.ACTION_DRAG_STARTED;
            });

            orderList.addView(row);
        }

        TextView reset = actionButton(getString(R.string.settings_reset_order), PRIMARY_START);
        reset.setOnClickListener(v -> {
            AppSettings.resetSourceOrder(this);
            PublicSettings.write(this);
            recreate();
        });
        card.addView(reset);
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
    /**
     * Makes the checked state vibrant and the unchecked state visibly dimmed,
     * so a setting's current state is readable at a glance.
     */
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

    private LinearLayout settingRow(String title, String value) {
        return settingRow(title, value, false);
    }

    private LinearLayout settingRow(String title, String value, boolean confirmed) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(BG_TOP);
        fill.setCornerRadius(dp(12));
        fill.setStroke(dp(1), BORDER);
        row.setBackground(new RippleDrawable(
                ColorStateList.valueOf(RIPPLE),
                fill,
                null
        ));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 14, TEXT, true));
        LinearLayout detailRow = new LinearLayout(this);
        detailRow.setOrientation(LinearLayout.HORIZONTAL);
        detailRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView detail = text(value, 12, confirmed ? GREEN : MUTED, false);
        detail.setPadding(0, dp(3), 0, 0);
        detailRow.addView(detail);
        if (confirmed) {
            TextView check = text("\u2713", 13, GREEN, true);
            check.setPadding(dp(6), dp(3), 0, 0);
            detailRow.addView(check);
        }
        labels.addView(detailRow);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = text("\u203A", 22, MUTED, true);
        chevron.setGravity(Gravity.CENTER);
        chevron.setPadding(dp(8), 0, 0, 0);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(28), dp(40)));

        row.setClickable(true);
        row.setFocusable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        row.setLayoutParams(params);
        return row;
    }

    private TextView actionButton(String label, int color) {
        TextView view = text(label, 14, color, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(BG_TOP);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), BORDER);
        view.setBackground(background);
        view.setClickable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        view.setLayoutParams(params);
        return view;
    }
    private void addSourceRow(LinearLayout parent, String key, String title, String description) {
        boolean enabled = SourceSettings.isEnabled(this, key);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        row.setTag(key);

        TextView drag = text("\u2261", 18, MUTED, true);
        drag.setGravity(Gravity.CENTER);
        drag.setContentDescription(getString(R.string.settings_drag_source));
        drag.setPadding(dp(2), dp(4), dp(6), dp(4));
        drag.setOnLongClickListener(v -> {
            startSourceDrag(row);
            return true;
        });
        row.addView(drag, new LinearLayout.LayoutParams(dp(26), dp(44)));

        View badge = SourceBadge.create(this, key);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                dp(36), dp(36));
        badgeParams.setMarginEnd(dp(10));
        row.addView(badge, badgeParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 15, enabled ? TEXT : MUTED, true));
        int descColor = !enabled ? MUTED
                : SourceSettings.APPTEKA.equals(key) ? RED
                : (SourceSettings.APKPURE.equals(key)
                || SourceSettings.UPTODOWN.equals(key)
                || SourceSettings.GITHUB.equals(key)) ? WARN_TEXT : MUTED;
        TextView desc = text(description, 12, descColor, false);
        desc.setPadding(0, dp(3), 0, 0);
        labels.addView(desc);
        TextView health = text(SourceHealth.summary(this, key), 11, MUTED, false);
        health.setPadding(0, dp(4), 0, 0);
        labels.addView(health);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView test = text(getString(R.string.settings_test), 12, PRIMARY_START, true);
        test.setPadding(dp(8), dp(8), dp(8), dp(8));
        test.setClickable(true);
        test.setFocusable(true);
        test.setOnClickListener(v -> {
            test.setText(R.string.settings_testing);
            SourceTester.test(this, key, (ok, message) -> runOnUiThread(() -> {
                test.setText(R.string.settings_test);
                health.setText(SourceHealth.summary(this, key));
                Toast.makeText(this, getString(R.string.settings_test_result, title, message),
                        Toast.LENGTH_SHORT).show();
            }));
        });
        row.addView(test, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        Switch toggle = new Switch(this);
        toggle.setChecked(enabled);
        styleSwitch(toggle);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked && SourceSettings.enabledCount(this) <= 1) {
                buttonView.setChecked(true);
                Toast.makeText(this, R.string.settings_min_one_source,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            SourceSettings.setEnabled(this, key, isChecked);
            PublicSettings.write(this);
            recreate();
        });
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                dp(64), dp(48));
        toggleParams.gravity = Gravity.CENTER_VERTICAL;
        row.addView(toggle, toggleParams);

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
                List<String> order = sourceDisplayOrder();
                int from = order.indexOf(dragged);
                int to = order.indexOf(target);
                if (from >= 0 && to >= 0) {
                    order.remove(from);
                    order.add(to, dragged);
                    AppSettings.setSourceOrder(this, order);
                    PublicSettings.write(this);
                    recreate();
                }
                return true;
            }
            return action == DragEvent.ACTION_DRAG_STARTED;
        });

        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        parent.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private void applyContentPadding(LinearLayout root) {
        int screen = getResources().getDisplayMetrics().widthPixels;
        int max = dp(720);
        final int horizontal = Math.max(dp(24), (screen - max) / 2);
        final int baseBottom = dp(32);
        root.setPadding(horizontal, dp(48), horizontal, baseBottom);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(horizontal, dp(48), horizontal, baseBottom + navigation);
            return insets;
        });
    }
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

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

    private GradientDrawable cardBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{CARD_TOP, CARD_BOTTOM}
        );
        drawable.setCornerRadius(dp(20));
        drawable.setStroke(dp(1), BORDER);
        return drawable;
    }

    private GradientDrawable gradient(int start, int end) {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{start, end}
        );
    }
}
















