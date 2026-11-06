package be.alterra.morphehelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.util.List;

public class UpdateActivity extends Activity {

    public static final String EXTRA_METHOD = "method";
    public static final String EXTRA_CHANGELOG = "changelog";
    public static final String METHOD_NORMAL = "normal";
    public static final String METHOD_SHIZUKU = "shizuku";

    private TextView version;
    private TextView releasePicker;
    private TextView status;
    private TextView notesTitle;
    private TextView notes;
    private ProgressBar progress;
    private LinearLayout actions;
    private UpdateInfo info;
    private List<UpdateInfo> releases;
    private boolean changelogOnly;
    private boolean loaded;
    private boolean releaseHistoryLoading;
    private boolean started;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PublicSettings.apply(this);
        getWindow().setStatusBarColor(Color.parseColor("#0B1220"));
        getWindow().setNavigationBarColor(Color.parseColor("#0B1220"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#0B1220"), Color.parseColor("#0F172A")}));

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

        TextView title = text(getString(R.string.app_name), 24, Color.parseColor("#F8FAFC"), true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        version = text(getString(R.string.update_current_version), 14,
                Color.parseColor("#94A3B8"), false);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, dp(8), 0, 0);
        root.addView(version);

        releasePicker = text("", 13, Color.parseColor("#818CF8"), true);
        releasePicker.setGravity(Gravity.CENTER);
        releasePicker.setPadding(0, dp(8), 0, 0);
        releasePicker.setVisibility(View.GONE);
        releasePicker.setOnClickListener(v -> showVersionPicker());
        root.addView(releasePicker);

        status = text(getString(R.string.update_loading), 15,
                Color.parseColor("#CBD5E1"), false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(18), 0, dp(12));
        root.addView(status);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor("#6366F1")));
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor("#6366F1")));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor("#1F2937")));
        root.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(0, dp(12), 0, dp(6));
        root.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        notesTitle = text(getString(R.string.update_whats_new), 16,
                Color.parseColor("#F8FAFC"), true);
        notesTitle.setPadding(0, dp(18), 0, dp(8));
        notesTitle.setVisibility(View.GONE);
        root.addView(notesTitle);

        notes = text("", 14, Color.parseColor("#CBD5E1"), false);
        notes.setTextIsSelectable(true);
        notes.setLineSpacing(dp(4), 1f);
        notes.setPadding(0, 0, 0, dp(12));
        notes.setVisibility(View.GONE);
        root.addView(notes);

        setContentView(scroll);

        changelogOnly = getIntent().getBooleanExtra(EXTRA_CHANGELOG, false);
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
                addCloseButton();
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
            releases = list;
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
        releasePicker.setText(getString(R.string.update_change_version)
                + "  \u203A");
        releasePicker.setEnabled(true);
    }

    private void showVersionPicker() {
        if (releases == null || releases.isEmpty()) {
            loadReleaseHistory();
            return;
        }
        String[] labels = new String[releases.size()];
        int selected = 0;
        for (int i = 0; i < releases.size(); i++) {
            labels[i] = "v" + releases.get(i).version;
            if (info != null && releases.get(i).version.equals(info.version)) {
                selected = i;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.update_choose_version)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    info = releases.get(which);
                    dialog.dismiss();
                    showInfo();
                })
                .show();
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
            notesTitle.setVisibility(View.VISIBLE);
            notes.setVisibility(View.VISIBLE);
        } else {
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
        if (newer && !info.assetUrl.isEmpty()) {
            addButton(getString(R.string.update_install_normal), () -> startUpdate(false));
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
        addCloseButton();
    }

    private void addCloseButton() {
        addButton(getString(R.string.common_close), this::finish);
    }

    private String cleanNotes(String value) {
        return value.replace("\r\n", "\n")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replace("**", "")
                .replace("__", "")
                .trim();
    }

    private void addButton(String label, Runnable action) {
        TextView button = text(label, 16, Color.WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#1F2937"));
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.parseColor("#334155"));
        button.setBackground(background);
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        params.topMargin = dp(8);
        actions.addView(button, params);
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
                });
            }
        }).start();
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
