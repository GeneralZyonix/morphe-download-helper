package be.alterra.morphehelper;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import rikka.shizuku.Shizuku;

/**
 * Source settings: choose which APK sources DownloadActivity may use.
 */
public class SettingsActivity extends Activity {

    private static final int BG_TOP = Color.parseColor("#0B1220");
    private static final int BG_BOTTOM = Color.parseColor("#0F172A");
    private static final int CARD_TOP = Color.parseColor("#111827");
    private static final int CARD_BOTTOM = Color.parseColor("#0F172A");
    private static final int BORDER = Color.parseColor("#1F2937");
    private static final int PRIMARY_START = Color.parseColor("#6366F1");
    private static final int PRIMARY_END = Color.parseColor("#4F46E5");
    private static final int TEXT = Color.parseColor("#F8FAFC");
    private static final int MUTED = Color.parseColor("#94A3B8");
    private static final int WARN_TEXT = Color.parseColor("#FDE68A");
    private static final int RED = Color.parseColor("#F87171");
    private static final int GREEN = Color.parseColor("#34D399");
    private static final int SHIZUKU_REQUEST_CODE = 71;
    private static final int FOLDER_REQUEST_CODE = 72;
    private static final int STORAGE_PERMISSION_CODE = 73;
    private boolean batteryIgnoring;
    private String pendingLocation;

    private final Shizuku.OnRequestPermissionResultListener shizukuListener =
            (requestCode, grantResult) -> {
                if (requestCode != SHIZUKU_REQUEST_CODE) {
                    return;
                }
                boolean granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED;
                if (granted) {
                    UpdateManager.setMode(this, UpdateManager.MODE_SHIZUKU);
                    Toast.makeText(this, R.string.settings_automatic_enabled,
                            Toast.LENGTH_SHORT).show();
                } else {
                    UpdateManager.setMode(this, UpdateManager.MODE_NOTIFY);
                    Toast.makeText(this, R.string.settings_shizuku_denied,
                            Toast.LENGTH_LONG).show();
                }
                recreate();
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SourceSettings.ensureInitialized(this);
        Shizuku.addRequestPermissionResultListener(shizukuListener);
        getWindow().setStatusBarColor(BG_TOP);
        getWindow().setNavigationBarColor(BG_TOP);

        ScrollView scroll = new ScrollView(this);
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

        addUpdatesCard(root);
        addBatteryCard(root);
        addSecurityCard(root);
        addStorageCard(root);
        addOrderCard(root);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(4), dp(18), dp(4));
        card.setBackground(cardBackground());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(18);
        root.addView(card, cardParams);

        TextView sourceTitle = text(getString(R.string.settings_sources), 16, TEXT, true);
        sourceTitle.setPadding(0, dp(14), 0, dp(4));
        card.addView(sourceTitle);

        TextView sourceHint = text(
                getString(R.string.settings_sources_hint), 12, MUTED, false);
        sourceHint.setLineSpacing(dp(3), 1f);
        sourceHint.setPadding(0, 0, 0, dp(6));
        card.addView(sourceHint);

        TextView sourceWebViewHint = text(
                getString(R.string.settings_webview_hint), 11, WARN_TEXT, false);
        sourceWebViewHint.setLineSpacing(dp(2), 1f);
        sourceWebViewHint.setPadding(0, 0, 0, dp(4));
        card.addView(sourceWebViewHint);

        addSourceRow(card, SourceSettings.APKPURE, getString(R.string.source_apkpure),
                getString(R.string.settings_source_apkpure_desc));
        addSourceRow(card, SourceSettings.FALLBACK,
                getString(R.string.settings_source_fallback_title),
                getString(R.string.settings_source_fallback_desc));
        addSourceRow(card, SourceSettings.APKMIRROR, getString(R.string.source_apkmirror),
                getString(R.string.settings_source_apkmirror_desc));
        addSourceRow(card, SourceSettings.UPTODOWN,
                getString(R.string.settings_source_uptodown_title),
                getString(R.string.settings_source_uptodown_desc));
        addSourceRow(card, SourceSettings.FDROID, getString(R.string.source_fdroid),
                getString(R.string.settings_source_fdroid_desc));
        addSourceRow(card, SourceSettings.APTOIDE, getString(R.string.source_aptoide),
                getString(R.string.settings_source_aptoide_desc));
        addSourceRow(card, SourceSettings.APPTEKA,
                getString(R.string.settings_source_appteka_title),
                getString(R.string.settings_source_appteka_desc));

        addApptekaWarningCard(root);
        addGitHubAppsCard(root);

        TextView done = primaryButton(getString(R.string.common_done));
        done.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        doneParams.topMargin = dp(20);
        doneParams.bottomMargin = dp(24);
        root.addView(done, doneParams);

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isIgnoringBatteryOptimizations() != batteryIgnoring) {
            recreate();
        }
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuListener);
        super.onDestroy();
    }

    private void addUpdatesCard(LinearLayout root) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(cardBackground());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(18);
        root.addView(card, params);

        card.addView(text(getString(R.string.settings_updates), 16, TEXT, true));

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
        enabledSwitch.setThumbTintList(ColorStateList.valueOf(PRIMARY_START));
        enabledSwitch.setTrackTintList(ColorStateList.valueOf(Color.parseColor("#334155")));
        enabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UpdateManager.setEnabled(this, isChecked);
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
        modeRow.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.settings_update_mode)
                .setSingleChoiceItems(new String[]{
                        getString(R.string.settings_mode_notify_option),
                        getString(R.string.settings_mode_shizuku_option)
                }, UpdateManager.MODE_SHIZUKU.equals(mode) ? 1 : 0, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) {
                        UpdateManager.setMode(this, UpdateManager.MODE_NOTIFY);
                        recreate();
                    } else {
                        requestShizukuMode();
                    }
                })
                .show());
        card.addView(modeRow);

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

    private void addBatteryCard(LinearLayout root) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(cardBackground());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(18);
        root.addView(card, params);

        card.addView(text(getString(R.string.settings_background), 16, TEXT, true));
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
        new AlertDialog.Builder(this)
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

    private void addSecurityCard(LinearLayout root) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(cardBackground());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(18);
        root.addView(card, params);

        card.addView(text("Security", 16, TEXT, true));

        LinearLayout scanRow = new LinearLayout(this);
        scanRow.setOrientation(LinearLayout.HORIZONTAL);
        scanRow.setGravity(Gravity.CENTER_VERTICAL);
        scanRow.setPadding(0, dp(8), 0, dp(8));
        LinearLayout scanLabels = new LinearLayout(this);
        scanLabels.setOrientation(LinearLayout.VERTICAL);
        scanLabels.addView(text("VirusTotal hash check", 14, TEXT, true));
        TextView scanDetail = text("Check downloaded files by SHA-256", 12, MUTED, false);
        scanDetail.setPadding(0, dp(3), 0, 0);
        scanLabels.addView(scanDetail);
        scanRow.addView(scanLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch scanSwitch = new Switch(this);
        scanSwitch.setChecked(AppSettings.virusTotalEnabled(this));
        scanSwitch.setThumbTintList(ColorStateList.valueOf(PRIMARY_START));
        scanSwitch.setTrackTintList(ColorStateList.valueOf(Color.parseColor("#334155")));
        scanSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setVirusTotalEnabled(this, isChecked);
            if (isChecked && AppSettings.virusTotalApiKey(this).isEmpty()) {
                Toast.makeText(this,
                        "Add a VirusTotal API key to start scanning.",
                        Toast.LENGTH_LONG).show();
            }
        });
        scanRow.addView(scanSwitch, new LinearLayout.LayoutParams(dp(64), dp(48)));
        card.addView(scanRow);

        boolean hasKey = !AppSettings.virusTotalApiKey(this).isEmpty();
        LinearLayout keyRow = settingRow("VirusTotal API key",
                hasKey ? "Configured" : "Not set");
        keyRow.setOnClickListener(v -> showVirusTotalKeyDialog());
        card.addView(keyRow);

        LinearLayout blockRow = new LinearLayout(this);
        blockRow.setOrientation(LinearLayout.HORIZONTAL);
        blockRow.setGravity(Gravity.CENTER_VERTICAL);
        blockRow.setPadding(0, dp(8), 0, dp(8));
        LinearLayout blockLabels = new LinearLayout(this);
        blockLabels.setOrientation(LinearLayout.VERTICAL);
        blockLabels.addView(text("Block detected files", 14, TEXT, true));
        TextView blockDetail = text("Stop handing the APK to Morphe", 12, MUTED, false);
        blockDetail.setPadding(0, dp(3), 0, 0);
        blockLabels.addView(blockDetail);
        blockRow.addView(blockLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch blockSwitch = new Switch(this);
        blockSwitch.setChecked(AppSettings.virusTotalBlockDetections(this));
        blockSwitch.setThumbTintList(ColorStateList.valueOf(PRIMARY_START));
        blockSwitch.setTrackTintList(ColorStateList.valueOf(Color.parseColor("#334155")));
        blockSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                AppSettings.setVirusTotalBlockDetections(this, isChecked));
        blockRow.addView(blockSwitch, new LinearLayout.LayoutParams(dp(64), dp(48)));
        card.addView(blockRow);

        LinearLayout uploadRow = new LinearLayout(this);
        uploadRow.setOrientation(LinearLayout.HORIZONTAL);
        uploadRow.setGravity(Gravity.CENTER_VERTICAL);
        uploadRow.setPadding(0, dp(8), 0, dp(8));
        LinearLayout uploadLabels = new LinearLayout(this);
        uploadLabels.setOrientation(LinearLayout.VERTICAL);
        uploadLabels.addView(text("Upload unknown files", 14, TEXT, true));
        TextView uploadDetail = text(
                "If no hash is found, send the APK for analysis", 12, MUTED, false);
        uploadDetail.setPadding(0, dp(3), 0, 0);
        uploadLabels.addView(uploadDetail);
        uploadRow.addView(uploadLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch uploadSwitch = new Switch(this);
        uploadSwitch.setChecked(AppSettings.virusTotalUploadUnknown(this));
        uploadSwitch.setThumbTintList(ColorStateList.valueOf(PRIMARY_START));
        uploadSwitch.setTrackTintList(ColorStateList.valueOf(Color.parseColor("#334155")));
        uploadSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setVirusTotalUploadUnknown(this, isChecked);
            if (isChecked) {
                Toast.makeText(this,
                        "Unknown APKs will be uploaded to VirusTotal.",
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
        alwaysUploadLabels.addView(text("Always upload to VirusTotal", 14, TEXT, true));
        TextView alwaysUploadDetail = text(
                "Skip hash lookup and upload every downloaded APK", 12, MUTED, false);
        alwaysUploadDetail.setPadding(0, dp(3), 0, 0);
        alwaysUploadLabels.addView(alwaysUploadDetail);
        alwaysUploadRow.addView(alwaysUploadLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch alwaysUploadSwitch = new Switch(this);
        alwaysUploadSwitch.setChecked(AppSettings.virusTotalAlwaysUpload(this));
        alwaysUploadSwitch.setThumbTintList(ColorStateList.valueOf(PRIMARY_START));
        alwaysUploadSwitch.setTrackTintList(ColorStateList.valueOf(Color.parseColor("#334155")));
        alwaysUploadSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setVirusTotalAlwaysUpload(this, isChecked);
            if (isChecked) {
                Toast.makeText(this,
                        "Always upload enabled. Downloads may take longer.",
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
        quotaUploadLabels.addView(text("Upload if quota exceeded", 14, TEXT, true));
        TextView quotaUploadDetail = text(
                "Use upload instead when a hash lookup hits the quota", 12, MUTED, false);
        quotaUploadDetail.setPadding(0, dp(3), 0, 0);
        quotaUploadLabels.addView(quotaUploadDetail);
        quotaUploadRow.addView(quotaUploadLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch quotaUploadSwitch = new Switch(this);
        quotaUploadSwitch.setChecked(AppSettings.virusTotalUploadOnQuota(this));
        quotaUploadSwitch.setThumbTintList(ColorStateList.valueOf(PRIMARY_START));
        quotaUploadSwitch.setTrackTintList(ColorStateList.valueOf(Color.parseColor("#334155")));
        quotaUploadSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                AppSettings.setVirusTotalUploadOnQuota(this, isChecked));
        quotaUploadRow.addView(quotaUploadSwitch,
                new LinearLayout.LayoutParams(dp(64), dp(48)));
        card.addView(quotaUploadRow);

        TextView uploadInfo = text(
                "Always upload makes every download take longer. VirusTotal documents "
                        + "new-file uploads as not consuming API lookup quota, but uploads "
                        + "still share the APK and may be rate-limited. If both upload "
                        + "fallbacks are enabled, upload is tried before aborting.",
                11, MUTED, false);
        uploadInfo.setLineSpacing(dp(3), 1f);
        uploadInfo.setPadding(0, dp(4), 0, 0);
        card.addView(uploadInfo);

        boolean abortOnQuota = AppSettings.virusTotalAbortOnQuota(this);
        LinearLayout quotaRow = settingRow("Quota exceeded",
                abortOnQuota ? "Abort handoff" : "Continue unscanned");
        quotaRow.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("When the VirusTotal quota is exceeded")
                .setSingleChoiceItems(new String[]{
                        "Continue unscanned",
                        "Abort handoff to Morphe"
                }, AppSettings.virusTotalAbortOnQuota(this) ? 1 : 0, (dialog, which) -> {
                    AppSettings.setVirusTotalAbortOnQuota(this, which == 1);
                    dialog.dismiss();
                    recreate();
                })
                .show());
        card.addView(quotaRow);

        TextView quota = text(
                "VirusTotal public API limits: about 4 requests/minute and 500/day. "
                        + "Uploads are limited to 32 MB directly, or 650 MB through "
                        + "VirusTotal's upload URL. New-file uploads do not consume "
                        + "lookup quota; premium API limits depend on the license.",
                11, MUTED, false);
        quota.setLineSpacing(dp(3), 1f);
        quota.setPadding(0, dp(8), 0, 0);
        card.addView(quota);

        TextView note = text(
                "Hash lookup is the default and the APK is never uploaded unless "
                        + "Upload unknown files is enabled. Uploads share the APK with "
                        + "VirusTotal and security vendors and are limited to 650 MB. "
                        + "A VirusTotal API key is required; results are advisory and "
                        + "may miss new malware. "
                        + "Unknown files, denied keys, quota errors, or lookup failures "
                        + "do not block the download unless the quota-exceeded setting "
                        + "is set to abort; the user is warned when the scan cannot run.",
                11, MUTED, false);
        note.setLineSpacing(dp(3), 1f);
        note.setPadding(0, dp(6), 0, 0);
        card.addView(note);
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

        new AlertDialog.Builder(this)
                .setTitle("VirusTotal API key")
                .setMessage("Stored only on this device. The APK itself is never uploaded.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear", (dialog, which) -> {
                    AppSettings.setVirusTotalApiKey(this, "");
                    recreate();
                })
                .setPositiveButton("Save", (dialog, which) -> {
                    AppSettings.setVirusTotalApiKey(this, input.getText().toString());
                    recreate();
                })
                .show();
    }

    private void requestShizukuMode() {
        int status = ShizukuHelper.status(this);
        if (status == ShizukuHelper.READY) {
            UpdateManager.setMode(this, UpdateManager.MODE_SHIZUKU);
            Toast.makeText(this, R.string.settings_automatic_enabled,
                    Toast.LENGTH_SHORT).show();
            recreate();
            return;
        }
        if (status == ShizukuHelper.PERMISSION_REQUIRED) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.settings_shizuku_dialog_title)
                    .setMessage(R.string.settings_shizuku_dialog_message)
                    .setNegativeButton(R.string.settings_use_notifications, (dialog, which) -> {
                        UpdateManager.setMode(this, UpdateManager.MODE_NOTIFY);
                        recreate();
                    })
                    .setPositiveButton(R.string.settings_grant_permission, (dialog, which) ->
                            ShizukuHelper.requestPermission(SHIZUKU_REQUEST_CODE))
                    .show();
            return;
        }
        if (status == ShizukuHelper.NOT_RUNNING) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.settings_shizuku_not_running_title)
                    .setMessage(R.string.settings_shizuku_not_running_message)
                    .setNegativeButton(R.string.common_cancel, null)
                    .setPositiveButton(R.string.settings_open_shizuku, (dialog, which) -> openShizuku())
                    .show();
            return;
        }
        if (status == ShizukuHelper.NOT_INSTALLED) {
            new AlertDialog.Builder(this)
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

    private void addStorageCard(LinearLayout root) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(cardBackground());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(18);
        root.addView(card, params);

        card.addView(text(getString(R.string.settings_storage), 16, TEXT, true));

        String location = AppSettings.location(this);
        LinearLayout folder = settingRow(getString(R.string.settings_download_folder),
                AppSettings.locationLabel(this));
        folder.setOnClickListener(v -> new AlertDialog.Builder(this)
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
            case AppSettings.HOUR_1: retentionLabel = getString(R.string.settings_retention_1h); break;
            case AppSettings.HOUR_24: retentionLabel = getString(R.string.settings_retention_24h); break;
            case AppSettings.DAY_7: retentionLabel = getString(R.string.settings_retention_7d); break;
            default: retentionLabel = getString(R.string.settings_keep_forever); break;
        }
        LinearLayout keep = settingRow(getString(R.string.settings_keep_downloads), retentionLabel);
        keep.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.settings_keep_downloads)
                .setSingleChoiceItems(new String[]{
                        getString(R.string.settings_keep_forever),
                        getString(R.string.settings_retention_1h),
                        getString(R.string.settings_retention_24h),
                        getString(R.string.settings_retention_7d)
                }, retentionIndex(retention), (dialog, which) -> {
                    String[] values = {AppSettings.KEEP, AppSettings.HOUR_1,
                            AppSettings.HOUR_24, AppSettings.DAY_7};
                    AppSettings.setRetention(this, values[which]);
                    dialog.dismiss();
                    recreate();
                })
                .show());
        card.addView(keep);

        TextView note = text(getString(R.string.settings_cleanup_note), 11, MUTED, false);
        note.setPadding(0, dp(6), 0, dp(10));
        card.addView(note);

        TextView delete = actionButton(getString(R.string.settings_delete_all), RED);
        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
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
        if (requestCode != FOLDER_REQUEST_CODE || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        Uri tree = data.getData();
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
        if (AppSettings.HOUR_1.equals(retention)) return 1;
        if (AppSettings.HOUR_24.equals(retention)) return 2;
        if (AppSettings.DAY_7.equals(retention)) return 3;
        return 0;
    }

    private void addApptekaWarningCard(LinearLayout root) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(cardBackground());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(18);
        root.addView(card, params);

        card.addView(text(getString(R.string.settings_appteka_title), 16, RED, true));

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
        toggle.setThumbTintList(ColorStateList.valueOf(PRIMARY_START));
        toggle.setTrackTintList(ColorStateList.valueOf(Color.parseColor("#334155")));
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setApptekaSnapTubeOnly(this, isChecked);
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

    private void addGitHubAppsCard(LinearLayout root) {
        List<GitHubAppStore.Entry> entries = GitHubAppStore.all(this);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(cardBackground());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(18);
        root.addView(card, params);

        card.addView(text(getString(R.string.settings_github_apps), 16, TEXT, true));
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

    private void addOrderCard(LinearLayout root) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(cardBackground());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(18);
        root.addView(card, params);

        card.addView(text(getString(R.string.settings_source_order), 16, TEXT, true));
        TextView note = text(getString(R.string.settings_source_order_note), 12, MUTED, false);
        note.setPadding(0, dp(4), 0, dp(8));
        card.addView(note);

        List<String> order = AppSettings.sourceOrder(this);
        for (int i = 0; i < order.size(); i++) {
            String key = order.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));

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
                recreate();
            });
            row.addView(up);
            TextView down = text("\u2193", 18, PRIMARY_START, true);
            down.setContentDescription(getString(R.string.settings_move_down));
            down.setPadding(dp(10), dp(4), dp(10), dp(4));
            down.setClickable(true);
            down.setOnClickListener(v -> {
                AppSettings.moveSource(this, key, 1);
                recreate();
            });
            row.addView(down);
            card.addView(row);
        }

        TextView reset = actionButton(getString(R.string.settings_reset_order), PRIMARY_START);
        reset.setOnClickListener(v -> {
            AppSettings.resetSourceOrder(this);
            recreate();
        });
        card.addView(reset);
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
        fill.setColor(Color.parseColor("#0B1220"));
        fill.setCornerRadius(dp(12));
        fill.setStroke(dp(1), BORDER);
        row.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x22FFFFFF),
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
        background.setColor(Color.parseColor("#0B1220"));
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
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 15, TEXT, true));
        int descColor = SourceSettings.APPTEKA.equals(key) ? RED
                : (SourceSettings.APKPURE.equals(key)
                || SourceSettings.UPTODOWN.equals(key)) ? WARN_TEXT : MUTED;
        TextView desc = text(description, 12, descColor, false);
        desc.setPadding(0, dp(3), 0, 0);
        labels.addView(desc);
        TextView health = text(SourceHealth.summary(this, key), 11, MUTED, false);
        health.setPadding(0, dp(4), 0, 0);
        labels.addView(health);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

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
        toggle.setChecked(SourceSettings.isEnabled(this, key));
        toggle.setThumbTintList(ColorStateList.valueOf(PRIMARY_START));
        toggle.setTrackTintList(ColorStateList.valueOf(Color.parseColor("#334155")));
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked && SourceSettings.enabledCount(this) <= 1) {
                buttonView.setChecked(true);
                Toast.makeText(this, R.string.settings_min_one_source,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            SourceSettings.setEnabled(this, key, isChecked);
        });
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                dp(64), dp(48));
        toggleParams.gravity = Gravity.CENTER_VERTICAL;
        row.addView(toggle, toggleParams);
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
















