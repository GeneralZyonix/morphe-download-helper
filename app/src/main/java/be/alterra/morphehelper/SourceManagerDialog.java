package be.alterra.morphehelper;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

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

    private Dialog dialog;
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
        content.setPadding(dp(20), dp(10), dp(20), dp(24));
        content.setBackground(sheetBackground());

        View handle = new View(activity);
        GradientDrawable handleBackground = new GradientDrawable();
        handleBackground.setColor(M3Ui.outline(palette));
        handleBackground.setCornerRadius(dp(999));
        handle.setBackground(handleBackground);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
                dp(44), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dp(16);
        content.addView(handle, handleParams);

        content.addView(M3Ui.display(activity,
                activity.getString(R.string.main_sources_title), 26, text));
        TextView subtitle = M3Ui.text(activity,
                activity.getString(R.string.source_manager_hint), 12, muted, 400);
        subtitle.setLineSpacing(dp(2), 1f);
        subtitle.setPadding(0, dp(6), 0, dp(12));
        content.addView(subtitle);

        ScrollView scroll = new ScrollView(activity);
        list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        int maxHeight = Math.min(dp(420),
                activity.getResources().getDisplayMetrics().heightPixels / 2);
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, maxHeight));
        rebuildList();

        TextView testAll = M3Ui.tonalButton(activity, palette,
                activity.getString(R.string.settings_test_all), 0);
        testAll.setOnClickListener(v -> testAllSources(testAll));
        LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        testParams.topMargin = dp(12);
        content.addView(testAll, testParams);

        TextView fullSettings = M3Ui.textButton(activity, palette,
                activity.getString(R.string.source_manager_full_settings), 0);
        fullSettings.setOnClickListener(v -> {
            Intent intent = new Intent(activity, SettingsActivity.class);
            intent.putExtra(SettingsActivity.EXTRA_SECTION,
                    SettingsActivity.EXTRA_SECTION_SOURCES);
            activity.startActivity(intent);
            dialog.dismiss();
        });
        LinearLayout.LayoutParams fullSettingsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        fullSettingsParams.topMargin = dp(4);
        content.addView(fullSettings, fullSettingsParams);

        TextView done = M3Ui.primaryButton(activity, palette,
                activity.getString(R.string.common_done), 0);
        done.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        doneParams.topMargin = dp(8);
        content.addView(done, doneParams);

        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(dp(20), dp(10), dp(20), dp(24) + bottom);
            return insets;
        });

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            window.setNavigationBarColor(M3Ui.surfaceContainer(palette));
            WindowInsetsControllerCompat controller =
                    ViewCompat.getWindowInsetsController(window.getDecorView());
            if (controller != null) {
                controller.setAppearanceLightNavigationBars(palette.light);
            }
        }
    }

    /** Surface with rounded top corners, like a Material 3 bottom sheet. */
    private GradientDrawable sheetBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(M3Ui.surfaceContainer(palette));
        float radius = dp(28);
        background.setCornerRadii(new float[]{
                radius, radius, radius, radius, 0, 0, 0, 0});
        return background;
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
        List<String> keys = new ArrayList<>(AppSettings.sourceOrder(activity));
        if (!keys.contains(SourceSettings.UPTODOWN)) {
            keys.add(SourceSettings.UPTODOWN);
        }
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


    private void styleSwitch(Switch toggle) {
        M3Ui.styleSwitch(toggle, palette);
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density);
    }
}
