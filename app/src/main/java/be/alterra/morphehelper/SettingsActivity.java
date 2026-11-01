package be.alterra.morphehelper;

import android.app.Activity;
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
        titles.addView(text("Download sources", 24, TEXT, true));
        TextView subtitle = text("Choose which sources the helper may use", 13, MUTED, false);
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

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(4), dp(18), dp(4));
        card.setBackground(cardBackground());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(18);
        root.addView(card, cardParams);

        addSourceRow(card, SourceSettings.FALLBACK, "Morphe fallback link",
                "First link supplied by the patch bundle");
        addSourceRow(card, SourceSettings.APKMIRROR, "APKMirror",
                "Package and version search");
        addSourceRow(card, SourceSettings.APKPURE, "APKPure",
                "API first; in-app WebView fallback");
        addSourceRow(card, SourceSettings.UPTODOWN, "Uptodown fallback",
                "In-app WebView verification page");
        addSourceRow(card, SourceSettings.FDROID, "F-Droid",
                "Open-source repository; only apps it hosts");
        addSourceRow(card, SourceSettings.APTOIDE, "Aptoide",
                "API search; only uploads marked TRUSTED");

        TextView done = primaryButton("Done");
        done.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        doneParams.topMargin = dp(20);
        root.addView(done, doneParams);

        setContentView(scroll);
    }

    private void addSourceRow(LinearLayout parent, String key, String title, String description) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 15, TEXT, true));
        int descColor = (SourceSettings.APKPURE.equals(key)
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
        int horizontal = Math.max(dp(24), (screen - max) / 2);
        root.setPadding(horizontal, dp(48), horizontal, dp(32));
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




