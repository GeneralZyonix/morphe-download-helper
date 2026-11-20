package be.alterra.morphehelper;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
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

        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout iconTile = new FrameLayout(activity);
        iconTile.setBackground(M3Ui.filled(activity,
                M3Ui.primaryContainer(palette), 12, 0, 0));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_section_sources);
        icon.setColorFilter(M3Ui.onPrimaryContainer(palette));
        FrameLayout.LayoutParams iconInner = new FrameLayout.LayoutParams(dp(20), dp(20));
        iconInner.gravity = Gravity.CENTER;
        iconTile.addView(icon, iconInner);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconParams.setMarginEnd(dp(12));
        titleRow.addView(iconTile, iconParams);
        TextView title = M3Ui.display(activity,
                activity.getString(R.string.settings_sources), 24, text);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView fullSettings = iconButton(R.drawable.ic_fullscreen,
                R.string.source_manager_full_settings, () -> {
                    Intent intent = new Intent(activity, SettingsActivity.class);
                    intent.putExtra(SettingsActivity.EXTRA_SECTION,
                            SettingsActivity.EXTRA_SECTION_SOURCES);
                    activity.startActivity(intent);
                    dialog.dismiss();
                });
        titleRow.addView(fullSettings, new LinearLayout.LayoutParams(dp(40), dp(40)));
        ImageView close = iconButton(R.drawable.ic_close,
                R.string.common_close, () -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        closeParams.setMarginStart(dp(6));
        titleRow.addView(close, closeParams);
        content.addView(titleRow);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout scrollContent = new LinearLayout(activity);
        scrollContent.setOrientation(LinearLayout.VERTICAL);
        TextView subtitle = M3Ui.text(activity,
                activity.getString(R.string.source_manager_hint), 12, muted, 400);
        subtitle.setLineSpacing(dp(2), 1f);
        subtitle.setPadding(0, dp(6), 0, dp(4));
        scrollContent.addView(subtitle);
        TextView applyHint = M3Ui.text(activity,
                activity.getString(R.string.source_manager_immediate), 11, footer, 400);
        applyHint.setPadding(0, 0, 0, dp(10));
        scrollContent.addView(applyHint);
        list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scrollContent.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(scrollContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        int maxHeight = Math.min(dp(390),
                (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.48f));
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, maxHeight));
        rebuildList();

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.setNavigationBarDividerColor(M3Ui.surfaceContainer(palette));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setNavigationBarContrastEnforced(false);
            }
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

    private ImageView iconButton(int iconRes, int descRes, Runnable action) {
        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setColorFilter(text);
        icon.setContentDescription(activity.getString(descRes));
        icon.setBackground(new RippleDrawable(
                ColorStateList.valueOf(palette.ripple),
                M3Ui.filled(activity, M3Ui.secondaryContainer(palette), 999, 0, 0),
                null));
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setClickable(true);
        icon.setFocusable(true);
        icon.setOnClickListener(v -> action.run());
        M3Ui.motion(icon);
        return icon;
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
