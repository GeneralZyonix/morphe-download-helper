package be.alterra.morphehelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SourceSettings.ensureInitialized(this);
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
        titles.addView(text("Settings", 24, TEXT, true));
        TextView subtitle = text("Sources, storage, and download order", 13, MUTED, false);
        subtitle.setPadding(0, dp(4), 0, 0);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header);

        TextView explanation = text(
                "Disabled sources are skipped completely. At least one source must stay enabled.",
                13, MUTED, false);
        explanation.setLineSpacing(dp(3), 1f);
        explanation.setPadding(0, dp(18), 0, 0);
        root.addView(explanation);

        TextView webViewNote = text(
                "APKPure uses its API first and may open an in-app WebView. "
                        + "Uptodown uses an in-app WebView verification page.",
                12, WARN_TEXT, false);
        webViewNote.setLineSpacing(dp(3), 1f);
        webViewNote.setPadding(0, dp(10), 0, 0);
        root.addView(webViewNote);

        addStorageCard(root);
        addOrderCard(root);
        addApptekaWarningCard(root);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(4), dp(18), dp(4));
        card.setBackground(cardBackground());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(18);
        root.addView(card, cardParams);

        addSourceRow(card, SourceSettings.APKPURE, "APKPure",
                "Fast direct/API path, then WebView fallback");
        addSourceRow(card, SourceSettings.FALLBACK, "Morphe fallback link",
                "Resolver link supplied by Morphe");
        addSourceRow(card, SourceSettings.APKMIRROR, "APKMirror",
                "Package and version search");
        addSourceRow(card, SourceSettings.UPTODOWN, "Uptodown fallback",
                "In-app WebView verification page");
        addSourceRow(card, SourceSettings.FDROID, "F-Droid",
                "Open-source repository; only apps it hosts");
        addSourceRow(card, SourceSettings.APTOIDE, "Aptoide",
                "API search; only uploads marked TRUSTED");
        addSourceRow(card, SourceSettings.APPTEKA, "\u26A0 Appteka",
                "Modded APK catalog; not recommended for apps other than SnapTube");

        addGitHubAppsCard(root);

        TextView done = primaryButton("Done");
        done.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        doneParams.topMargin = dp(20);
        doneParams.bottomMargin = dp(24);
        root.addView(done, doneParams);

        setContentView(scroll);
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

        card.addView(text("Storage", 16, TEXT, true));

        String location = AppSettings.location(this);
        String locationLabel = AppSettings.LOCATION_EXTERNAL.equals(location)
                ? "App external storage" : "Internal app cache (default)";
        LinearLayout folder = settingRow("Download folder", locationLabel);
        folder.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Download folder")
                .setSingleChoiceItems(new String[]{
                        "Internal app cache (default)",
                        "App external storage"
                }, AppSettings.LOCATION_EXTERNAL.equals(location) ? 1 : 0, (dialog, which) -> {
                    AppSettings.setLocation(this, which == 1
                            ? AppSettings.LOCATION_EXTERNAL : AppSettings.LOCATION_INTERNAL);
                    dialog.dismiss();
                    recreate();
                })
                .show());
        card.addView(folder);

        String retention = AppSettings.retention(this);
        String retentionLabel;
        switch (retention) {
            case AppSettings.HOUR_1: retentionLabel = "Delete after 1 hour"; break;
            case AppSettings.HOUR_24: retentionLabel = "Delete after 24 hours"; break;
            case AppSettings.DAY_7: retentionLabel = "Delete after 7 days"; break;
            default: retentionLabel = "Keep downloads"; break;
        }
        LinearLayout keep = settingRow("Keep downloads", retentionLabel);
        keep.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Keep downloads")
                .setSingleChoiceItems(new String[]{
                        "Keep downloads",
                        "Delete after 1 hour",
                        "Delete after 24 hours",
                        "Delete after 7 days"
                }, retentionIndex(retention), (dialog, which) -> {
                    String[] values = {AppSettings.KEEP, AppSettings.HOUR_1,
                            AppSettings.HOUR_24, AppSettings.DAY_7};
                    AppSettings.setRetention(this, values[which]);
                    dialog.dismiss();
                    recreate();
                })
                .show());
        card.addView(keep);

        TextView note = text(
                "Cleanup runs when the helper opens, so downloads are not slowed down.",
                11, MUTED, false);
        note.setPadding(0, dp(6), 0, dp(10));
        card.addView(note);

        TextView delete = actionButton("Delete all downloaded APKs", RED);
        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Delete downloaded APKs?")
                .setMessage("This removes every file in the selected download folder.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    int count = AppSettings.deleteAllDownloads(this);
                    Toast.makeText(this, count + " file(s) deleted.", Toast.LENGTH_SHORT).show();
                })
                .show());
        card.addView(delete);
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

        card.addView(text("\u26A0 Appteka warning", 16, RED, true));

        TextView message = text(
                "Appteka hosts modded APKs, including modified versions of popular apps. "
                        + "We do not recommend enabling Appteka for any app other than SnapTube. "
                        + "Modded APKs can contain altered code, behave unpredictably, or be rejected by Morphe.",
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
        labels.addView(text("Only use Appteka for SnapTube", 14, TEXT, true));
        TextView detail = text("Recommended. Turn off to use Appteka for every app.",
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
                Toast.makeText(this,
                        "Appteka is now an extra source for every app. Use at your own risk.",
                        Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                dp(64), dp(48));
        toggleParams.gravity = Gravity.CENTER_VERTICAL;
        row.addView(toggle, toggleParams);
        card.addView(row);

        TextView note = text(
                "When this is off, Appteka is tried in the source order for all apps, not just SnapTube.",
                11, MUTED, false);
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

        card.addView(text("GitHub apps", 16, TEXT, true));
        TextView note = text(
                "Remembered GitHub release sources. Remove an app if it becomes available "
                        + "on a regular APK source.",
                12, MUTED, false);
        note.setLineSpacing(dp(3), 1f);
        note.setPadding(0, dp(4), 0, dp(4));
        card.addView(note);

        if (entries.isEmpty()) {
            TextView empty = text(
                    "No GitHub apps remembered yet. Use Search GitHub releases on a failed "
                            + "download to add one.",
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
            remove.setContentDescription("Remove GitHub app " + entry.label);
            remove.setClickable(true);
            remove.setFocusable(true);
            remove.setOnClickListener(v -> {
                GitHubAppStore.remove(this, entry.packageName);
                Toast.makeText(this, entry.label + " removed from GitHub apps.",
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

        card.addView(text("Source order", 16, TEXT, true));
        TextView note = text(
                "Use the arrows to change which source is tried first.",
                12, MUTED, false);
        note.setPadding(0, dp(4), 0, dp(8));
        card.addView(note);

        List<String> order = AppSettings.sourceOrder(this);
        for (int i = 0; i < order.size(); i++) {
            String key = order.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));

            TextView label = text((i + 1) + ". " + AppSettings.sourceLabel(key),
                    14, TEXT, false);
            row.addView(label, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView up = text("\u2191", 18, PRIMARY_START, true);
            up.setPadding(dp(10), dp(4), dp(10), dp(4));
            up.setClickable(true);
            up.setOnClickListener(v -> {
                AppSettings.moveSource(this, key, -1);
                recreate();
            });
            row.addView(up);
            TextView down = text("\u2193", 18, PRIMARY_START, true);
            down.setPadding(dp(10), dp(4), dp(10), dp(4));
            down.setClickable(true);
            down.setOnClickListener(v -> {
                AppSettings.moveSource(this, key, 1);
                recreate();
            });
            row.addView(down);
            card.addView(row);
        }

        TextView reset = actionButton("Reset to default order", PRIMARY_START);
        reset.setOnClickListener(v -> {
            AppSettings.resetSourceOrder(this);
            recreate();
        });
        card.addView(reset);
    }

    private LinearLayout settingRow(String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.addView(text(title, 14, TEXT, true));
        TextView detail = text(value, 12, MUTED, false);
        detail.setPadding(0, dp(3), 0, 0);
        row.addView(detail);
        row.setClickable(true);
        row.setFocusable(true);
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

        TextView test = text("Test", 12, PRIMARY_START, true);
        test.setPadding(dp(8), dp(8), dp(8), dp(8));
        test.setClickable(true);
        test.setFocusable(true);
        test.setOnClickListener(v -> {
            test.setText("...");
            SourceTester.test(this, key, (ok, message) -> runOnUiThread(() -> {
                test.setText("Test");
                health.setText(SourceHealth.summary(this, key));
                Toast.makeText(this, title + ": " + message, Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Keep at least one source enabled.",
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












