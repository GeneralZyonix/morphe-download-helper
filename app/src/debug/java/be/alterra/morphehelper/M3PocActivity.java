package be.alterra.morphehelper;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * POC-only Material 3 Expressive design preview. This activity is not part of
 * the stable navigation and exists only in the material3-poc branch.
 */
public class M3PocActivity extends AppCompatActivity {

    private static final String EXTRA_VARIANT = "variant";
    private static final String VARIANT_STANDARD = "standard";
    private static final String VARIANT_BRAND = "brand";

    private String variant = VARIANT_STANDARD;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String requested = intent == null ? null : intent.getStringExtra(EXTRA_VARIANT);
        if (VARIANT_BRAND.equals(requested)) {
            variant = VARIANT_BRAND;
        }
        if (VARIANT_BRAND.equals(variant)) {
            setTheme(R.style.M3BrandTheme);
        } else {
            DynamicColors.applyToActivityIfAvailable(this);
        }
        setContentView(buildUi());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));
        root.setFitsSystemWindows(true);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.m3_poc_title);
        toolbar.setSubtitle(VARIANT_BRAND.equals(variant)
                ? R.string.m3_poc_brand : R.string.m3_poc_standard);
        toolbar.setTitleTextColor(color(com.google.android.material.R.attr.colorOnSurface));
        toolbar.setSubtitleTextColor(color(
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        toolbar.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(10), dp(20), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(variantToggle());
        content.addView(chips());

        MaterialCardView hero = card(dp(28));
        if (VARIANT_BRAND.equals(variant)) {
            GradientDrawable gradient = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF252046, 0xFF17222A});
            gradient.setCornerRadius(dp(28));
            hero.setCardBackgroundColor(Color.TRANSPARENT);
            hero.setBackground(gradient);
        } else {
            hero.setCardBackgroundColor(color(
                    com.google.android.material.R.attr.colorPrimaryContainer));
            hero.setStrokeWidth(0);
        }
        LinearLayout heroContent = vertical();
        heroContent.setPadding(dp(18), dp(18), dp(18), dp(18));
        TextView eyebrow = text(getString(R.string.m3_poc_ready), 10,
                color(com.google.android.material.R.attr.colorOnPrimaryContainer), true);
        eyebrow.setLetterSpacing(0.14f);
        heroContent.addView(eyebrow);
        TextView title = text(getString(R.string.m3_poc_hero_title), 29,
                color(com.google.android.material.R.attr.colorOnPrimaryContainer), true);
        title.setPadding(0, dp(6), 0, dp(4));
        heroContent.addView(title);
        TextView body = text(getString(R.string.m3_poc_hero_body), 12,
                color(com.google.android.material.R.attr.colorOnPrimaryContainer), false);
        body.setLineSpacing(dp(2), 1f);
        body.setPadding(0, 0, 0, dp(14));
        heroContent.addView(body);

        LinearLayout heroButtons = horizontal();
        MaterialButton open = button(getString(R.string.m3_poc_open_morphe), true);
        MaterialButton help = button(getString(R.string.m3_poc_help), false);
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        openParams.setMarginEnd(dp(8));
        heroButtons.addView(open, openParams);
        heroButtons.addView(help, new LinearLayout.LayoutParams(0, dp(46), 1f));
        heroContent.addView(heroButtons);
        hero.addView(heroContent);
        content.addView(hero, blockParams());

        LinearLayout quick = horizontal();
        quick.addView(quickCard(R.string.m3_poc_visual_guide,
                R.string.m3_poc_visual_guide_sub), weightedWithEndMargin());
        quick.addView(quickCard(R.string.m3_poc_sources,
                R.string.m3_poc_sources_sub), weighted());
        content.addView(quick, blockParams());

        content.addView(helperLogCard(), blockParams());

        MaterialCardView download = card(dp(24));
        LinearLayout downloadContent = vertical();
        downloadContent.setPadding(dp(16), dp(14), dp(16), dp(14));
        downloadContent.addView(text(getString(R.string.m3_poc_download_preview), 13,
                color(com.google.android.material.R.attr.colorOnSurface), true));
        TextView dlBody = text(getString(R.string.m3_poc_download_body), 11,
                color(com.google.android.material.R.attr.colorOnSurfaceVariant), false);
        dlBody.setPadding(0, dp(4), 0, dp(10));
        downloadContent.addView(dlBody);
        LinearProgressIndicator progress = new LinearProgressIndicator(this);
        progress.setIndeterminate(false);
        progress.setProgressCompat(72, false);
        downloadContent.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
        download.addView(downloadContent);
        content.addView(download, blockParams());

        MaterialButton sheetButton = button(getString(R.string.m3_poc_open_sheet), false);
        sheetButton.setOnClickListener(v -> showSourcesSheet());
        content.addView(sheetButton, blockParams());

        content.addView(aboutCard(), blockParams());

        TextView note = text(getString(R.string.m3_poc_note), 10,
                color(com.google.android.material.R.attr.colorOnSurfaceVariant), false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(14), 0, 0);
        content.addView(note);

        return root;
    }

    private View variantToggle() {
        MaterialButtonToggleGroup group = new MaterialButtonToggleGroup(this);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);

        MaterialButton standard = toggleButton(getString(R.string.m3_poc_standard));
        standard.setId(View.generateViewId());
        MaterialButton brand = toggleButton(getString(R.string.m3_poc_brand));
        brand.setId(View.generateViewId());
        group.addView(standard, new LinearLayout.LayoutParams(0, dp(42), 1f));
        group.addView(brand, new LinearLayout.LayoutParams(0, dp(42), 1f));
        group.check(VARIANT_BRAND.equals(variant) ? brand.getId() : standard.getId());
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            switchVariant(checkedId == standard.getId() ? VARIANT_STANDARD : VARIANT_BRAND);
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        params.bottomMargin = dp(10);
        group.setLayoutParams(params);
        return group;
    }

    private MaterialButton toggleButton(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12);
        return button;
    }

    private View chips() {
        LinearLayout row = horizontal();
        row.setPadding(0, 0, 0, dp(10));
        row.addView(chip("Morphe 2.7.0", false));
        row.addView(chip("Helper · Icon", true));
        row.addView(chip(getString(VARIANT_BRAND.equals(variant)
                ? R.string.m3_poc_palette_brand
                : R.string.m3_poc_palette_dynamic), false));
        return row;
    }

    private Chip chip(String label, boolean primary) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCheckable(false);
        chip.setClickable(false);
        chip.setChipStrokeWidth(0);
        int background = primary
                ? color(com.google.android.material.R.attr.colorPrimaryContainer)
                : color(com.google.android.material.R.attr.colorSecondaryContainer);
        int foreground = primary
                ? color(com.google.android.material.R.attr.colorOnPrimaryContainer)
                : color(com.google.android.material.R.attr.colorOnSecondaryContainer);
        chip.setChipBackgroundColor(ColorStateList.valueOf(background));
        chip.setTextColor(foreground);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        params.setMarginEnd(dp(6));
        chip.setLayoutParams(params);
        return chip;
    }

    private View quickCard(int titleRes, int subRes) {
        MaterialCardView card = card(dp(24));
        LinearLayout body = vertical();
        body.setPadding(dp(14), dp(13), dp(14), dp(13));
        body.addView(text(getString(titleRes), 13,
                color(com.google.android.material.R.attr.colorOnSurface), true));
        TextView sub = text(getString(subRes), 10,
                color(com.google.android.material.R.attr.colorOnSurfaceVariant), false);
        sub.setPadding(0, dp(3), 0, 0);
        body.addView(sub);
        card.addView(body);
        return card;
    }

    private View helperLogCard() {
        MaterialCardView card = card(dp(24));
        LinearLayout row = horizontal();
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = text("☰", 18,
                color(com.google.android.material.R.attr.colorOnPrimaryContainer), true);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(color(com.google.android.material.R.attr.colorPrimaryContainer));
        iconBg.setCornerRadius(dp(12));
        icon.setBackground(iconBg);
        row.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout copy = vertical();
        copy.setPadding(dp(11), 0, 0, 0);
        copy.addView(text(getString(R.string.m3_poc_helper_log), 13,
                color(com.google.android.material.R.attr.colorOnSurface), true));
        copy.addView(text(getString(R.string.m3_poc_helper_log_sub), 10,
                color(com.google.android.material.R.attr.colorOnSurfaceVariant), false));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setChecked(true);
        row.addView(toggle);
        card.addView(row);
        return card;
    }

    private View aboutCard() {
        MaterialCardView card = card(dp(24));
        LinearLayout body = vertical();
        body.setPadding(dp(16), dp(14), dp(16), dp(14));
        body.addView(text(getString(R.string.m3_poc_about), 13,
                color(com.google.android.material.R.attr.colorOnSurface), true));
        body.addView(text(getString(R.string.m3_poc_about_sub), 10,
                color(com.google.android.material.R.attr.colorOnSurfaceVariant), false));
        card.addView(body);
        return card;
    }

    private void showSourcesSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout content = vertical();
        content.setPadding(dp(20), dp(8), dp(20), dp(24));

        View handle = new View(this);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(color(com.google.android.material.R.attr.colorOutline));
        handleBg.setCornerRadius(dp(999));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(44), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.topMargin = dp(4);
        content.addView(handle, handleParams);

        TextView title = text(getString(R.string.m3_poc_sheet_title), 22,
                color(com.google.android.material.R.attr.colorOnSurface), true);
        title.setPadding(0, dp(14), 0, 0);
        content.addView(title);
        TextView sub = text(getString(R.string.m3_poc_sheet_sub), 10,
                color(com.google.android.material.R.attr.colorOnSurfaceVariant), false);
        sub.setPadding(0, dp(3), 0, dp(12));
        content.addView(sub);

        String[] names = {"Morphe link", "APKMirror", "APKPure", "APKCube", "Uptodown"};
        for (int i = 0; i < names.length; i++) {
            MaterialCardView row = card(dp(18));
            LinearLayout line = horizontal();
            line.setPadding(dp(12), dp(10), dp(12), dp(10));
            line.setGravity(Gravity.CENTER_VERTICAL);
            TextView icon = text(names[i].substring(0, 1), 12,
                    color(com.google.android.material.R.attr.colorOnSecondaryContainer), true);
            icon.setGravity(Gravity.CENTER);
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setColor(color(com.google.android.material.R.attr.colorSecondaryContainer));
            iconBg.setCornerRadius(dp(10));
            icon.setBackground(iconBg);
            line.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
            LinearLayout labels = vertical();
            labels.setPadding(dp(10), 0, 0, 0);
            labels.addView(text(names[i], 12,
                    color(com.google.android.material.R.attr.colorOnSurface), true));
            labels.addView(text(i == 0 ? "Resolver link" : "Package and version search",
                    9, color(com.google.android.material.R.attr.colorOnSurfaceVariant), false));
            line.addView(labels, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            MaterialSwitch toggle = new MaterialSwitch(this);
            toggle.setChecked(i < 4);
            line.addView(toggle);
            TextView drag = text("≡", 17,
                    color(com.google.android.material.R.attr.colorOnSurfaceVariant), true);
            drag.setPadding(dp(8), 0, 0, 0);
            line.addView(drag);
            row.addView(line);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(7);
            content.addView(row, params);
        }

        MaterialButton test = button(getString(R.string.m3_poc_test_all), true);
        LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        testParams.topMargin = dp(10);
        content.addView(test, testParams);

        dialog.setContentView(content);
        dialog.show();
    }

    private void switchVariant(String next) {
        if (next.equals(variant)) {
            return;
        }
        Intent intent = new Intent(this, M3PocActivity.class);
        intent.putExtra(EXTRA_VARIANT, next);
        startActivity(intent);
        finish();
    }

    private MaterialCardView card(int radius) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(radius);
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(color(com.google.android.material.R.attr.colorOutline));
        card.setCardBackgroundColor(color(
                com.google.android.material.R.attr.colorSurfaceVariant));
        return card;
    }

    private MaterialButton button(String label, boolean filled) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setCornerRadius(dp(24));
        if (filled) {
            button.setBackgroundTintList(ColorStateList.valueOf(
                    color(androidx.appcompat.R.attr.colorPrimary)));
            button.setTextColor(color(com.google.android.material.R.attr.colorOnPrimary));
        } else {
            button.setBackgroundTintList(ColorStateList.valueOf(
                    color(com.google.android.material.R.attr.colorSecondaryContainer)));
            button.setTextColor(color(
                    com.google.android.material.R.attr.colorOnSecondaryContainer));
        }
        return button;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private LinearLayout.LayoutParams blockParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        return params;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams weightedWithEndMargin() {
        LinearLayout.LayoutParams params = weighted();
        params.setMarginEnd(dp(8));
        return params;
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

    private int color(int attr) {
        TypedValue value = new TypedValue();
        if (!getTheme().resolveAttribute(attr, value, true)) {
            return Color.WHITE;
        }
        if (value.resourceId != 0) {
            return getColor(value.resourceId);
        }
        return value.data;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
