package be.alterra.morphehelper;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
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
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
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
    private static final String MORPHE_PACKAGE = "app.morphe.manager";

    private ThemePalette palette;
    private FrameLayout contentHost;
    private LinearLayout navBar;
    private final LinearLayout[] navItems = new LinearLayout[4];
    private final ImageView[] navIcons = new ImageView[4];
    private final TextView[] navLabels = new TextView[4];
    private final View[] tabs = new View[4];
    private int selectedTab = TAB_HOME;

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

        selectTab(TAB_HOME, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
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
    }

    // ------------------------------------------------------------------
    // Shell
    // ------------------------------------------------------------------

    private void selectTab(int index, boolean animate) {
        selectedTab = index;
        if (index == TAB_DOWNLOADS || index == TAB_SOURCES) {
            tabs[index] = null;
        }
        if (tabs[index] == null) {
            tabs[index] = buildTab(index);
        }
        View view = tabs[index];
        contentHost.removeAllViews();
        contentHost.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (animate) {
            view.setAlpha(0f);
            view.animate().alpha(1f).setDuration(180).start();
        }
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

        addNavItem(bar, TAB_HOME, R.drawable.ic_home, R.string.preview_home);
        addNavItem(bar, TAB_DOWNLOADS, R.drawable.ic_downloads, R.string.preview_downloads);
        addNavItem(bar, TAB_SOURCES, R.drawable.ic_section_sources, R.string.preview_sources);
        addNavItem(bar, TAB_SETTINGS, R.drawable.ic_settings_gear, R.string.preview_settings);
        return bar;
    }

    private void addNavItem(LinearLayout bar, int index, int iconRes, int labelRes) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(6), dp(8), dp(6), dp(7));
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(v -> selectTab(index, true));

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
        root.addView(developerBlock());
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
        int foreground = Color.rgb(20, 20, 26);
        TextView button = M3Ui.text(this, label, 15, foreground, 600);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setPadding(dp(16), dp(13), dp(16), dp(13));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x22000000),
                M3Ui.filled(this, Color.WHITE, M3Ui.SHAPE_FULL, 0, 0), null));
        if (iconRes != 0) {
            Drawable icon = getDrawable(iconRes);
            if (icon != null) {
                icon.setTint(foreground);
                button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
                button.setCompoundDrawablePadding(dp(9));
            }
        }
        button.setOnClickListener(v -> action.run());
        M3Ui.motion(button);
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
                this::showLogDialog);
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
        M3Ui.motion(tile);

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
        githubParams.setMarginEnd(dp(8));
        links.addView(github, githubParams);

        TextView reddit = M3Ui.outlinedButton(this, palette, "Reddit", 0, TEXT);
        reddit.setOnClickListener(v -> openUrl(
                "https://www.reddit.com/r/MorpheApp/comments/1xjmnf3/i_made_an_helper_that_fetches_the_right_apk_for/"));
        links.addView(reddit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
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
        ImageView refresh = iconButton(R.drawable.ic_refresh,
                R.string.preview_downloads_refresh,
                () -> selectTab(TAB_DOWNLOADS, true));
        header.addView(refresh, new LinearLayout.LayoutParams(dp(42), dp(42)));
        root.addView(header);

        List<DownloadEntry> files = listDownloads();
        if (files.isEmpty()) {
            root.addView(emptyCard(R.drawable.ic_downloads,
                    getString(R.string.preview_downloads_empty_title),
                    getString(R.string.preview_downloads_empty_body)));
        } else {
            for (DownloadEntry entry : files) {
                root.addView(downloadRow(entry));
            }
        }
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
        M3Ui.motion(row);

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

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    private View buildSettings() {
        ScrollView scroll = tabScroll();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(M3Ui.display(this, getString(R.string.preview_settings), 30, TEXT));
        TextView hint = M3Ui.text(this,
                getString(R.string.preview_settings_hint), 12, MUTED, 400);
        hint.setPadding(0, dp(5), 0, dp(14));
        root.addView(hint);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(6), dp(6), dp(6), dp(6));
        card.setBackground(M3Ui.card(this, palette));
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addSettingsRow(card, R.drawable.ic_section_general,
                R.string.settings_general, R.string.settings_general_summary,
                "general");
        addSettingsRow(card, R.drawable.ic_section_updates,
                R.string.settings_updates, R.string.settings_updates_summary,
                "updates");
        addSettingsRow(card, R.drawable.ic_section_web,
                R.string.settings_web_pages, R.string.settings_web_pages_summary,
                "web");
        addSettingsRow(card, R.drawable.ic_section_sources,
                R.string.settings_sources, R.string.settings_sources_summary,
                SettingsActivity.EXTRA_SECTION_SOURCES);
        addSettingsRow(card, R.drawable.ic_section_storage,
                R.string.settings_storage, R.string.settings_storage_summary,
                "storage");
        addSettingsRow(card, R.drawable.ic_section_security,
                R.string.preview_security, R.string.settings_security_summary,
                "security");
        addSettingsRow(card, R.drawable.ic_section_battery,
                R.string.preview_background, R.string.settings_background_summary,
                "battery");
        addSettingsRow(card, R.drawable.ic_section_backup,
                R.string.settings_backup, R.string.settings_backup_summary,
                "backup");

        LinearLayout appearanceRow = settingsRow(R.drawable.ic_section_general,
                getString(R.string.preview_settings_appearance),
                getString(R.string.appearance_subtitle));
        appearanceRow.setOnClickListener(v ->
                startActivity(new Intent(this, AppearanceActivity.class)));
        root.addView(appearanceRow, topMargin(dp(10)));

        TextView all = M3Ui.primaryButton(this, palette,
                getString(R.string.preview_settings_all), 0);
        all.setOnClickListener(v -> openSettings(null));
        LinearLayout.LayoutParams allParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        allParams.topMargin = dp(18);
        root.addView(all, allParams);
        return scroll;
    }

    private void addSettingsRow(LinearLayout card, int iconRes, int titleRes,
                                int summaryRes, String section) {
        LinearLayout row = settingsRow(iconRes, getString(titleRes),
                getString(summaryRes));
        row.setOnClickListener(v -> openSettings(section));
        card.addView(row);
    }

    private LinearLayout settingsRow(int iconRes, String title, String summary) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(11), dp(8), dp(11));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(palette.ripple),
                null, null));
        row.setClickable(true);
        row.setFocusable(true);
        M3Ui.motion(row);

        FrameLayout iconTile = new FrameLayout(this);
        iconTile.setBackground(M3Ui.filled(this,
                M3Ui.primaryContainer(palette), 12, 0, 0));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(M3Ui.onPrimaryContainer(palette));
        FrameLayout.LayoutParams iconInner = new FrameLayout.LayoutParams(dp(20), dp(20));
        iconInner.gravity = Gravity.CENTER;
        iconTile.addView(icon, iconInner);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconParams.setMarginEnd(dp(12));
        row.addView(iconTile, iconParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(M3Ui.text(this, title, 14, TEXT, 600));
        TextView detail = M3Ui.text(this, summary, 11, MUTED, 400);
        detail.setPadding(0, dp(2), 0, 0);
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

    private LinearLayout.LayoutParams topMargin(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = margin;
        return params;
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
        scroll.setPadding(dp(20), dp(18), dp(20), dp(118));
        return scroll;
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
        M3Ui.motion(button);
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
