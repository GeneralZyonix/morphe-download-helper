package be.alterra.morphehelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared source enable/order manager. The same dialog is used from the main
 * screen's Manage button and from the download failure screen so both places
 * stay consistent.
 */
final class SourceManagerDialog {

    private final Activity activity;
    private final Runnable onChanged;
    private final ThemePalette palette;
    private final int text;
    private final int muted;
    private final int footer;
    private final int primary;
    private final int border;
    private final int accent;
    private final int secondaryBorder;
    private final int switchThumb;

    private AlertDialog dialog;
    private LinearLayout list;

    private SourceManagerDialog(Activity activity, Runnable onChanged) {
        this.activity = activity;
        this.onChanged = onChanged;
        palette = ThemeManager.palette(activity);
        text = palette.text;
        muted = palette.muted;
        footer = palette.footer;
        primary = palette.primaryStart;
        border = palette.border;
        accent = palette.accentText;
        secondaryBorder = palette.secondaryBorder;
        switchThumb = palette.switchThumb;
    }

    static SourceManagerDialog show(Activity activity, Runnable onChanged) {
        SourceManagerDialog manager = new SourceManagerDialog(activity, onChanged);
        manager.open();
        return manager;
    }

    boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    private void open() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(4), dp(20), 0);

        TextView hint = text(activity.getString(R.string.source_manager_hint), 12, muted, false);
        hint.setLineSpacing(dp(2), 1f);
        hint.setPadding(0, 0, 0, dp(8));
        content.addView(hint);

        ScrollView scroll = new ScrollView(activity);
        list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));
        rebuildList();

        TextView testAll = secondaryButton(activity.getString(R.string.settings_test_all));
        testAll.setOnClickListener(v -> testAllSources(testAll));
        LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        testParams.topMargin = dp(12);
        content.addView(testAll, testParams);

        dialog = ThemeManager.dialog(activity)
                .setTitle(R.string.main_sources_title)
                .setView(content)
                .setNegativeButton(R.string.common_done, null)
                .show();
    }

    /** Rebuilds rows in place so toggling a switch does not flicker the dialog. */
    private void rebuildList() {
        if (list == null) {
            return;
        }
        list.removeAllViews();
        for (String key : displayOrder()) {
            list.addView(sourceRow(key));
            View divider = new View(activity);
            divider.setBackgroundColor(border);
            list.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        }
    }

    private View sourceRow(String key) {
        boolean enabled = SourceSettings.isEnabled(activity, key);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        View badge = SourceBadge.create(activity, key);
        row.addView(badge, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(10), 0, dp(4), 0);
        String name = SourceSettings.APPTEKA.equals(key)
                ? activity.getString(R.string.main_source_appteka)
                : AppSettings.sourceLabel(activity, key);
        labels.addView(text(name, 14, enabled ? text : muted, true));
        String description = description(activity, key);
        if (!description.isEmpty()) {
            TextView detail = text(description, 11, enabled ? muted : footer, false);
            detail.setPadding(0, dp(2), 0, 0);
            labels.addView(detail);
        }
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        List<String> display = displayOrder();
        int index = display.indexOf(key);
        TextView up = text("\u2191", 16, index > 0 ? primary : footer, true);
        up.setGravity(Gravity.CENTER);
        up.setPadding(dp(5), dp(6), dp(5), dp(6));
        up.setClickable(index > 0);
        up.setOnClickListener(v -> move(key, -1));
        row.addView(up, new LinearLayout.LayoutParams(dp(26), dp(40)));

        TextView down = text("\u2193", 16,
                index >= 0 && index < display.size() - 1 ? primary : footer, true);
        down.setGravity(Gravity.CENTER);
        down.setPadding(dp(5), dp(6), dp(5), dp(6));
        down.setClickable(index >= 0 && index < display.size() - 1);
        down.setOnClickListener(v -> move(key, 1));
        row.addView(down, new LinearLayout.LayoutParams(dp(26), dp(40)));

        Switch toggle = new Switch(activity);
        toggle.setChecked(enabled);
        styleSwitch(toggle);
        toggle.setOnCheckedChangeListener((buttonView, checked) -> {
            if (!checked && SourceSettings.enabledCount(activity) <= 1) {
                buttonView.setChecked(true);
                Toast.makeText(activity, R.string.settings_min_one_source,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            SourceSettings.setEnabled(activity, key, checked);
            PublicSettings.write(activity);
            changed();
        });
        row.addView(toggle, new LinearLayout.LayoutParams(dp(60), dp(44)));
        return row;
    }

    private void move(String key, int delta) {
        AppSettings.moveSource(activity, key, delta);
        PublicSettings.write(activity);
        changed();
    }

    private void changed() {
        if (onChanged != null) {
            onChanged.run();
        }
        rebuildList();
    }

    private void testAllSources(TextView button) {
        List<String> keys = AppSettings.sourceOrder(activity);
        if (keys.isEmpty()) {
            return;
        }
        button.setEnabled(false);
        button.setText(R.string.settings_testing_all);
        int[] state = {0, 0, 0};
        Runnable[] runner = new Runnable[1];
        runner[0] = () -> {
            if (state[2] >= keys.size()) {
                button.setEnabled(true);
                button.setText(R.string.settings_test_all);
                Toast.makeText(activity,
                        activity.getString(R.string.settings_test_all_result,
                                state[0], state[1]), Toast.LENGTH_LONG).show();
                return;
            }
            String key = keys.get(state[2]++);
            SourceTester.test(activity, key, (ok, message) -> activity.runOnUiThread(() -> {
                if (ok) {
                    state[0]++;
                } else {
                    state[1]++;
                }
                runner[0].run();
            }));
        };
        runner[0].run();
    }

    /** Enabled sources in saved order, followed by disabled sources. */
    private List<String> displayOrder() {
        List<String> order = AppSettings.sourceOrder(activity);
        List<String> enabled = new ArrayList<>();
        List<String> disabled = new ArrayList<>();
        for (String key : order) {
            if (SourceSettings.isEnabled(activity, key)) {
                enabled.add(key);
            } else {
                disabled.add(key);
            }
        }
        enabled.addAll(disabled);
        return enabled;
    }

    static String description(Context context, String key) {
        switch (key) {
            case SourceSettings.FALLBACK:
                return context.getString(R.string.settings_source_fallback_desc);
            case SourceSettings.APKMIRROR:
                return context.getString(R.string.settings_source_apkmirror_desc);
            case SourceSettings.APKPURE:
                return context.getString(R.string.settings_source_apkpure_desc);
            case SourceSettings.UPTODOWN:
                return context.getString(R.string.settings_source_uptodown_desc);
            case SourceSettings.FDROID:
                return context.getString(R.string.settings_source_fdroid_desc);
            case SourceSettings.APTOIDE:
                return context.getString(R.string.settings_source_aptoide_desc);
            case SourceSettings.APPTEKA:
                return context.getString(R.string.settings_source_appteka_desc);
            case SourceSettings.GITHUB:
                return context.getString(R.string.settings_source_github_desc);
            default:
                return "";
        }
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(
                bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return view;
    }

    private TextView secondaryButton(String label) {
        TextView view = text(label, 14, accent, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(palette.bgTop);
        outline.setCornerRadius(dp(14));
        outline.setStroke(dp(1), secondaryBorder);
        view.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x22FFFFFF), outline, null));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private void styleSwitch(Switch toggle) {
        toggle.setThumbTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{switchThumb, muted}
        ));
        toggle.setTrackTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{primary, secondaryBorder}
        ));
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density);
    }
}
