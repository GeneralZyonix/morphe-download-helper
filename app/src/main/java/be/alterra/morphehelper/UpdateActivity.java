package be.alterra.morphehelper;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class UpdateActivity extends Activity {

    private static final int MAX_VERSION_HISTORY = 20;

    public static final String EXTRA_METHOD = "method";
    public static final String EXTRA_CHANGELOG = "changelog";
    public static final String METHOD_NORMAL = "normal";
    public static final String METHOD_SHIZUKU = "shizuku";

    private TextView version;
    private TextView releasePicker;
    private TextView status;
    private TextView notesTitle;
    private TextView notes;
    private LinearLayout notesCard;
    private ProgressBar progress;
    private LinearLayout actions;
    private UpdateInfo info;
    private List<UpdateInfo> releases;
    private boolean changelogOnly;
    private boolean loaded;
    private boolean releaseHistoryLoading;
    private boolean started;

    private int BG_TOP;
    private int BG_BOTTOM;
    private int CARD_TOP;
    private int CARD_BOTTOM;
    private int BORDER;
    private int TEXT;
    private int MUTED;
    private int STEPS;
    private int PRIMARY_START;
    private int PRIMARY_END;
    private int ON_PRIMARY;
    private int ACCENT_TEXT;
    private int RIPPLE;
    private int SECONDARY;
    private int SECONDARY_BORDER;
    private String themeSignature;

    @Override
    protected void onResume() {
        super.onResume();
        if (!started && themeSignature != null
                && !themeSignature.equals(ThemeManager.signature(this))) {
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
        TEXT = palette.text;
        MUTED = palette.muted;
        STEPS = palette.steps;
        PRIMARY_START = palette.primaryStart;
        PRIMARY_END = palette.primaryEnd;
        ON_PRIMARY = palette.onPrimary;
        ACCENT_TEXT = palette.accentText;
        RIPPLE = palette.ripple;
        SECONDARY = palette.secondary;
        SECONDARY_BORDER = palette.secondaryBorder;
        themeSignature = ThemeManager.signature(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PublicSettings.apply(this);
        AppSettings.ensureLauncherIconState(this);
        applyPalette();

        changelogOnly = getIntent().getBooleanExtra(EXTRA_CHANGELOG, false);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG_TOP, BG_BOTTOM}));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        final int baseTop = dp(48);
        final int baseBottom = dp(32);
        root.setPadding(dp(24), baseTop, dp(24), baseBottom);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            view.setPadding(dp(24), baseTop + top, dp(24), baseBottom + bottom);
            return insets;
        });
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text(getString(changelogOnly
                ? R.string.settings_changelog : R.string.app_name), 24, TEXT, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        LinearLayout header = card();
        root.addView(header, topMarginParams(18));

        version = text(getString(R.string.update_current_version), 14, MUTED, false);
        version.setGravity(Gravity.CENTER);
        header.addView(version);

        releasePicker = text("", 13, ACCENT_TEXT, true);
        releasePicker.setGravity(Gravity.CENTER);
        releasePicker.setSingleLine(true);
        releasePicker.setPadding(dp(14), dp(8), dp(10), dp(8));
        releasePicker.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, R.drawable.ic_chevron_right, 0);
        releasePicker.setCompoundDrawablePadding(dp(2));
        releasePicker.setCompoundDrawableTintList(ColorStateList.valueOf(ACCENT_TEXT));
        GradientDrawable pickerBackground = rounded(SECONDARY, 12);
        pickerBackground.setStroke(dp(1), SECONDARY_BORDER);
        releasePicker.setBackground(new RippleDrawable(
                ColorStateList.valueOf(RIPPLE), pickerBackground, null));
        releasePicker.setClickable(true);
        releasePicker.setFocusable(true);
        releasePicker.setVisibility(View.GONE);
        releasePicker.setOnClickListener(v -> showVersionPicker());
        LinearLayout.LayoutParams pickerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pickerParams.gravity = Gravity.CENTER_HORIZONTAL;
        pickerParams.topMargin = dp(10);
        header.addView(releasePicker, pickerParams);

        status = text(getString(R.string.update_loading), 15, STEPS, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(14), 0, dp(10));
        header.addView(status);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(ColorStateList.valueOf(PRIMARY_START));
        progress.setProgressTintList(ColorStateList.valueOf(PRIMARY_START));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(SECONDARY_BORDER));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressParams.topMargin = dp(4);
        header.addView(progress, progressParams);

        notesCard = card();
        notesTitle = text(getString(R.string.update_whats_new), 16, TEXT, true);
        notesTitle.setPadding(0, 0, 0, dp(8));
        notesTitle.setVisibility(View.GONE);
        notesCard.addView(notesTitle);

        notes = text("", 14, STEPS, false);
        notes.setTextIsSelectable(true);
        notes.setLineSpacing(dp(4), 1f);
        notes.setVisibility(View.GONE);
        notesCard.addView(notes);
        notesCard.setVisibility(View.GONE);

        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setVisibility(View.GONE);

        if (changelogOnly) {
            root.addView(notesCard, topMarginParams(12));
            root.addView(actions, topMarginParams(12));
        } else {
            root.addView(actions, topMarginParams(12));
            root.addView(notesCard, topMarginParams(12));
        }

        setContentView(scroll);
        ThemeManager.applySystemBars(this);

        info = UpdateManager.cachedLatest(this);
        if (info == null) {
            loadLatest();
        } else {
            showInfo();
        }
        if (changelogOnly) {
            loadReleaseHistory();
        }
    }

    private void loadLatest() {
        if (loaded) {
            return;
        }
        loaded = true;
        status.setText(R.string.update_loading);
        progress.setVisibility(View.VISIBLE);
        actions.setVisibility(View.GONE);
        UpdateManager.fetchLatestAsync(this, (latest, error) -> {
            progress.setVisibility(View.GONE);
            if (latest == null) {
                status.setText(getString(R.string.update_error_loading, error));
                addCloseButton(true);
                return;
            }
            info = latest;
            showInfo();
        });
    }

    private void loadReleaseHistory() {
        if (releaseHistoryLoading || releases != null) {
            updateReleasePicker();
            return;
        }
        releaseHistoryLoading = true;
        releasePicker.setVisibility(View.VISIBLE);
        releasePicker.setText(R.string.update_loading_versions);
        releasePicker.setEnabled(false);
        UpdateManager.fetchReleasesAsync((list, error) -> {
            releaseHistoryLoading = false;
            if (list == null || list.isEmpty()) {
                releasePicker.setText(R.string.update_versions_unavailable);
                releasePicker.setEnabled(false);
                return;
            }
            releases = list.size() > MAX_VERSION_HISTORY
                    ? new ArrayList<>(list.subList(0, MAX_VERSION_HISTORY))
                    : list;
            updateReleasePicker();
        });
    }

    private void updateReleasePicker() {
        if (!changelogOnly) {
            releasePicker.setVisibility(View.GONE);
            return;
        }
        releasePicker.setVisibility(View.VISIBLE);
        if (releases == null || releases.isEmpty()) {
            if (!releaseHistoryLoading) {
                releasePicker.setText(R.string.update_versions_unavailable);
                releasePicker.setEnabled(false);
            }
            return;
        }
        releasePicker.setText(R.string.update_change_version);
        releasePicker.setEnabled(true);
    }

    private void showVersionPicker() {
        if (releases == null || releases.isEmpty()) {
            loadReleaseHistory();
            return;
        }
        int selected = 0;
        for (int i = 0; i < releases.size(); i++) {
            if (info != null && releases.get(i).version.equals(info.version)) {
                selected = i;
            }
        }
        final int selectedIndex = selected;
        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return releases.size() + 1;
            }

            @Override
            public Object getItem(int position) {
                return position == 0 ? null : releases.get(position - 1);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (position == 0) {
                    TextView note = text(getString(R.string.update_version_limit),
                            12, MUTED, false);
                    note.setPadding(dp(20), dp(8), dp(12), dp(8));
                    return note;
                }
                LinearLayout row = new LinearLayout(UpdateActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(52));
                row.setPadding(dp(20), dp(10), dp(12), dp(10));

                int releaseIndex = position - 1;
                UpdateInfo release = releases.get(releaseIndex);
                row.addView(text("v" + release.version, 16, TEXT, false));

                TextView pill = releasePill(UpdateInfo.isPrerelease(release.version));
                LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                pillParams.setMarginStart(dp(10));
                row.addView(pill, pillParams);

                row.addView(new View(UpdateActivity.this),
                        new LinearLayout.LayoutParams(0, 1, 1f));

                TextView check = text("\u2713", 14, ACCENT_TEXT, true);
                check.setGravity(Gravity.CENTER);
                check.setVisibility(releaseIndex == selectedIndex
                        ? View.VISIBLE : View.INVISIBLE);
                row.addView(check, new LinearLayout.LayoutParams(dp(24), dp(24)));
                return row;
            }
        };
        ThemeManager.dialog(this)
                .setTitle(R.string.update_choose_version)
                .setAdapter(adapter, (dialog, which) -> {
                    if (which <= 0 || which > releases.size()) {
                        return;
                    }
                    info = releases.get(which - 1);
                    dialog.dismiss();
                    showInfo();
                    updateReleasePicker();
                })
                .show();
    }

    private TextView releasePill(boolean prerelease) {
        TextView pill = text(getString(prerelease
                        ? R.string.update_prerelease : R.string.update_stable),
                10, prerelease ? 0xFF1F1300 : 0xFFFFFFFF, true);
        pill.setGravity(Gravity.CENTER);
        pill.setSingleLine(true);
        pill.setPadding(dp(8), dp(3), dp(8), dp(3));
        pill.setBackground(rounded(prerelease ? 0xFFBF8700 : 0xFF1F883D, 50));
        return pill;
    }

    private void showInfo() {
        if (info == null) {
            loadLatest();
            return;
        }
        progress.setVisibility(View.GONE);
        String current = UpdateChecker.installedVersion(this);
        boolean newer = info.isNewerThan(current);
        if (changelogOnly) {
            version.setText(getString(R.string.update_viewing_versions,
                    current, info.version));
        } else {
            version.setText(getString(R.string.update_versions, current, info.version));
        }

        if (newer) {
            status.setText(getString(R.string.update_available, info.version));
        } else if (changelogOnly) {
            status.setText(getString(R.string.update_release_notes_for, info.version));
        } else {
            status.setText(R.string.update_already_up_to_date);
        }
        updateReleasePicker();

        if (!info.notes.isEmpty()) {
            notesTitle.setText(changelogOnly || !newer
                    ? getString(R.string.update_whats_new_in, info.version)
                    : getString(R.string.update_whats_new));
            notes.setText(cleanNotes(info.notes));
            notesCard.setVisibility(View.VISIBLE);
            notesTitle.setVisibility(View.VISIBLE);
            notes.setVisibility(View.VISIBLE);
        } else {
            notesCard.setVisibility(View.GONE);
            notesTitle.setVisibility(View.GONE);
            notes.setVisibility(View.GONE);
            if (info.notes.isEmpty() && !loaded) {
                loadLatest();
            }
        }

        buildActions(newer);
        if (newer && !started) {
            String method = getIntent().getStringExtra(EXTRA_METHOD);
            if (METHOD_NORMAL.equals(method)) {
                started = true;
                startUpdate(false);
            } else if (METHOD_SHIZUKU.equals(method)) {
                started = true;
                startUpdate(true);
            }
        }
    }

    private void buildActions(boolean newer) {
        actions.removeAllViews();
        actions.setVisibility(View.VISIBLE);
        boolean install = newer && !info.assetUrl.isEmpty();
        if (install) {
            addPrimaryButton(getString(R.string.update_install_normal),
                    () -> startUpdate(false));
            if (ShizukuHelper.status(this) == ShizukuHelper.READY) {
                addButton(getString(R.string.update_install_shizuku), () -> startUpdate(true));
            }
        }
        if (newer) {
            addButton(getString(R.string.update_remind_later), () -> {
                UpdateManager.remindLater(this);
                finish();
            });
            addButton(getString(R.string.update_skip_version), () -> {
                UpdateManager.skipVersion(this);
                finish();
            });
        }
        addCloseButton(!install);
    }

    private void addCloseButton(boolean primary) {
        if (primary) {
            addPrimaryButton(getString(R.string.common_close), this::finish);
        } else {
            addButton(getString(R.string.common_close), this::finish);
        }
    }

    private String cleanNotes(String value) {
        return value.replace("\r\n", "\n")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replaceAll("(?m)^- ", "• ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private void addButton(String label, Runnable action) {
        TextView button = text(label, 15, TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable background = rounded(SECONDARY, 14);
        background.setStroke(dp(1), SECONDARY_BORDER);
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(RIPPLE), background, null));
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        params.topMargin = dp(8);
        actions.addView(button, params);
    }

    private void addPrimaryButton(String label, Runnable action) {
        TextView button = text(label, 16, ON_PRIMARY, true);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable fill = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{PRIMARY_START, PRIMARY_END});
        fill.setCornerRadius(dp(16));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF), fill, null));
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        params.topMargin = dp(8);
        actions.addView(button, params);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(cardBackground());
        return card;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{CARD_TOP, CARD_BOTTOM});
        background.setCornerRadius(dp(20));
        background.setStroke(dp(1), BORDER);
        return background;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        return background;
    }

    private LinearLayout.LayoutParams topMarginParams(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private void startUpdate(boolean useShizuku) {
        if (info == null || info.assetUrl.isEmpty()) {
            status.setText(R.string.update_error_no_asset);
            progress.setVisibility(View.GONE);
            return;
        }
        actions.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                runOnUiThread(() -> status.setText(
                        getString(R.string.update_downloading, info.version)));
                UpdateManager.DownloadedUpdate update = UpdateManager.download(this, info);
                PackageInfo archive = getPackageManager()
                        .getPackageArchiveInfo(update.localFile.getAbsolutePath(), 0);
                if (archive == null
                        || !getPackageName().equals(archive.packageName)
                        || !info.version.equals(archive.versionName)) {
                    update.delete(this);
                    throw new Exception(getString(R.string.update_error_mismatch));
                }

                UpdateManager.scheduleInstalledCheck(this, info.version);
                boolean canUseShizuku = useShizuku
                        && ShizukuHelper.status(this) == ShizukuHelper.READY;
                if (canUseShizuku) {
                    runOnUiThread(() -> status.setText(R.string.update_installing_shizuku));
                    installWithShizuku(update);
                } else {
                    runOnUiThread(() -> status.setText(R.string.update_opening_installer));
                    openAndroidInstaller(update.localFile);
                }
            } catch (Exception error) {
                HelperLog.error("Update install failed", error);
                runOnUiThread(() -> {
                    status.setText(getString(R.string.update_error_failed, error.getMessage()));
                    progress.setVisibility(View.GONE);
                    actions.setVisibility(View.VISIBLE);
                    actions.removeAllViews();
                    addButton(getString(R.string.update_open_release_page),
                            this::openReleasePage);
                    addCloseButton(true);
                });
            }
        }).start();
    }

    private void openReleasePage() {
        if (info == null || info.releaseUrl.isEmpty()) {
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(info.releaseUrl)));
        } catch (Exception error) {
            HelperLog.error("Could not open release page", error);
        }
    }

    private void installWithShizuku(UpdateManager.DownloadedUpdate update) {
        ShizukuHelper.install(this, update.installFile, (ok, message) -> {
            if (ok) {
                status.setText(R.string.update_installed);
                progress.setVisibility(View.GONE);
                update.delete(this);
            } else {
                status.setText(getString(R.string.update_error_shizuku, message));
                openAndroidInstaller(update.localFile);
            }
        });
    }

    private void openAndroidInstaller(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            status.setText(R.string.update_installer_opened);
            progress.setVisibility(View.GONE);
        } catch (Exception error) {
            HelperLog.error("Could not open Android installer", error);
            status.setText(getString(R.string.update_error_installer, error.getMessage()));
            progress.setVisibility(View.GONE);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold
                ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }
}
