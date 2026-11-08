package be.alterra.morphehelper;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Appearance settings: preview, mode, accent presets, and custom HEX. */
public class AppearanceActivity extends Activity {

    private String themeSignature;
    private EditText schemeHex;
    private TextView schemeError;
    private LinearLayout schemeEditor;
    private View schemeRow;
    private ScrollView scrollView;
    private int RED;

    private int BG_TOP;
    private int BG_BOTTOM;
    private int CARD_TOP;
    private int CARD_BOTTOM;
    private int BORDER;
    private int PRIMARY_START;
    private int PRIMARY_END;
    private int ON_PRIMARY;
    private int ACCENT_TEXT;
    private int RIPPLE;
    private int SECONDARY;
    private int SECONDARY_BORDER;
    private int TEXT;
    private int MUTED;
    private int STEPS;
    private int SWITCH_THUMB;
    private boolean LIGHT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PublicSettings.apply(this);
        applyPalette();

        scrollView = new ScrollView(this);
        scrollView.setId(R.id.appearance_scroll);
        scrollView.setFillViewport(true);
        scrollView.setBackground(gradient(BG_TOP, BG_BOTTOM));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        applyContentPadding(root);
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(text(getString(R.string.appearance_title), 26, TEXT, true));
        TextView subtitle = text(getString(R.string.appearance_subtitle), 13, MUTED, false);
        subtitle.setPadding(0, dp(6), 0, 0);
        root.addView(subtitle);

        TextView current = text(getString(R.string.appearance_current,
                ThemeManager.summary(this)), 12, ACCENT_TEXT, true);
        current.setPadding(0, dp(12), 0, 0);
        root.addView(current);

        addPreviewCard(root);
        addModeCard(root);
        addTintCard(root);
        addAccentCard(root);
        addResetCard(root);

        TextView done = primaryButton(getString(R.string.common_done));
        done.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        doneParams.topMargin = dp(20);
        doneParams.bottomMargin = dp(24);
        root.addView(done, doneParams);

        setContentView(scrollView);
        ViewCompat.setOnApplyWindowInsetsListener(scrollView, (view, insets) -> {
            int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            if (view.getPaddingBottom() != ime) {
                view.setPadding(0, 0, 0, ime);
            }
            if (ime > 0 && schemeHex != null && schemeHex.hasFocus()) {
                view.postDelayed(this::scrollSchemeFieldIntoView, 60);
            }
            return insets;
        });
        ThemeManager.applySystemBars(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeSignature != null
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
        PRIMARY_START = palette.primaryStart;
        PRIMARY_END = palette.primaryEnd;
        ON_PRIMARY = palette.onPrimary;
        ACCENT_TEXT = palette.accentText;
        RIPPLE = palette.ripple;
        SECONDARY = palette.secondary;
        SECONDARY_BORDER = palette.secondaryBorder;
        TEXT = palette.text;
        MUTED = palette.muted;
        STEPS = palette.steps;
        SWITCH_THUMB = palette.switchThumb;
        RED = palette.red;
        LIGHT = palette.light;
        themeSignature = ThemeManager.signature(this);
    }

    private void addPreviewCard(LinearLayout root) {
        LinearLayout card = card();
        root.addView(card, cardParams());

        card.addView(text(getString(R.string.appearance_preview), 16, TEXT, true));

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable background = rounded(SECONDARY, 16);
        background.setStroke(dp(1), SECONDARY_BORDER);
        preview.setBackground(background);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        previewParams.topMargin = dp(12);
        card.addView(preview, previewParams);

        preview.addView(text(getString(R.string.appearance_preview_title), 15, TEXT, true));
        TextView body = text(getString(R.string.appearance_preview_body), 12, MUTED, false);
        body.setLineSpacing(dp(3), 1f);
        body.setPadding(0, dp(6), 0, dp(14));
        preview.addView(body);

        TextView action = text(getString(R.string.appearance_preview_button), 14, ON_PRIMARY, true);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(14), dp(11), dp(14), dp(11));
        GradientDrawable fill = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{PRIMARY_START, PRIMARY_END});
        fill.setCornerRadius(dp(12));
        action.setBackground(fill);
        preview.addView(action, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ProgressBar progress = new ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(62);
        progress.setIndeterminateTintList(ColorStateList.valueOf(PRIMARY_START));
        progress.setProgressTintList(ColorStateList.valueOf(PRIMARY_START));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(SECONDARY_BORDER));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(14));
        progressParams.topMargin = dp(14);
        preview.addView(progress, progressParams);

        LinearLayout hintRow = new LinearLayout(this);
        hintRow.setOrientation(LinearLayout.HORIZONTAL);
        hintRow.setGravity(Gravity.CENTER_VERTICAL);
        hintRow.setPadding(0, dp(10), 0, 0);

        View dot = new View(this);
        GradientDrawable dotBackground = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{PRIMARY_START, PRIMARY_END});
        dotBackground.setShape(GradientDrawable.OVAL);
        dot.setBackground(dotBackground);
        hintRow.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));

        TextView hint = text(ThemeManager.summary(this), 12, ACCENT_TEXT, false);
        hint.setPadding(dp(8), 0, 0, 0);
        hintRow.addView(hint);
        preview.addView(hintRow);
    }

    private void addModeCard(LinearLayout root) {
        LinearLayout card = card();
        LinearLayout.LayoutParams params = cardParams();
        params.topMargin = dp(18);
        root.addView(card, params);

        card.addView(text(getString(R.string.appearance_mode), 16, TEXT, true));
        TextView detail = text(getString(R.string.appearance_mode_detail), 12, MUTED, false);
        detail.setLineSpacing(dp(2), 1f);
        detail.setPadding(0, dp(4), 0, dp(12));
        card.addView(detail);

        String mode = ThemeManager.savedMode(this);
        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView dark = optionChip(getString(R.string.theme_mode_dark),
                ThemeManager.MODE_DARK.equals(mode));
        dark.setOnClickListener(v -> selectMode(ThemeManager.MODE_DARK));
        LinearLayout.LayoutParams darkParams = new LinearLayout.LayoutParams(
                0, dp(44), 1f);
        darkParams.setMarginEnd(dp(4));
        firstRow.addView(dark, darkParams);

        TextView amoled = optionChip(getString(R.string.theme_mode_amoled),
                ThemeManager.MODE_AMOLED.equals(mode));
        amoled.setOnClickListener(v -> selectMode(ThemeManager.MODE_AMOLED));
        LinearLayout.LayoutParams amoledParams = new LinearLayout.LayoutParams(
                0, dp(44), 1f);
        amoledParams.setMarginStart(dp(4));
        firstRow.addView(amoled, amoledParams);
        card.addView(firstRow);

        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams secondRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        secondRowParams.topMargin = dp(8);
        card.addView(secondRow, secondRowParams);

        TextView light = optionChip(getString(R.string.theme_mode_light),
                ThemeManager.MODE_LIGHT.equals(mode));
        light.setOnClickListener(v -> selectMode(ThemeManager.MODE_LIGHT));
        LinearLayout.LayoutParams lightParams = new LinearLayout.LayoutParams(
                0, dp(44), 1f);
        lightParams.setMarginEnd(dp(4));
        secondRow.addView(light, lightParams);

        TextView system = optionChip(getString(R.string.theme_mode_system),
                ThemeManager.MODE_SYSTEM.equals(mode));
        system.setOnClickListener(v -> selectMode(ThemeManager.MODE_SYSTEM));
        LinearLayout.LayoutParams systemParams = new LinearLayout.LayoutParams(
                0, dp(44), 1f);
        systemParams.setMarginStart(dp(4));
        secondRow.addView(system, systemParams);
    }

    private void addTintCard(LinearLayout root) {
        LinearLayout card = card();
        LinearLayout.LayoutParams params = cardParams();
        params.topMargin = dp(18);
        root.addView(card, params);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(getString(R.string.appearance_tint), 16, TEXT, true));
        TextView detail = text(getString(R.string.appearance_tint_detail), 12, MUTED, false);
        detail.setLineSpacing(dp(2), 1f);
        detail.setPadding(0, dp(4), dp(12), 0);
        labels.addView(detail);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(AppSettings.themeTintSurfaces(this));
        styleSwitch(toggle);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setThemeTintSurfaces(this, isChecked);
            PublicSettings.write(this);
            recreate();
        });
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                dp(64), dp(48));
        toggleParams.gravity = Gravity.CENTER_VERTICAL;
        row.addView(toggle, toggleParams);
        card.addView(row);
    }

    private void styleSwitch(Switch toggle) {
        toggle.setThumbTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{SWITCH_THUMB, MUTED}
        ));
        toggle.setTrackTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{PRIMARY_START, SECONDARY_BORDER}
        ));
    }

    private void addAccentCard(LinearLayout root) {
        LinearLayout card = card();
        LinearLayout.LayoutParams params = cardParams();
        params.topMargin = dp(18);
        root.addView(card, params);

        card.addView(text(getString(R.string.appearance_accent), 16, TEXT, true));
        TextView detail = text(getString(R.string.appearance_accent_detail), 12, MUTED, false);
        detail.setLineSpacing(dp(2), 1f);
        detail.setPadding(0, dp(4), 0, dp(10));
        card.addView(detail);

        String current = ThemeManager.accent(this);
        String customHex = AppSettings.themeCustomHex(this);
        LinearLayout row = null;
        int index = 0;
        for (String accent : ThemeManager.availablePresets()) {
            if (index % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.topMargin = index == 0 ? 0 : dp(8);
                card.addView(row, rowParams);
            }
            View option = accentOption(accent, customHex, accent.equals(current));
            LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (index % 2 == 0) {
                optionParams.setMarginEnd(dp(6));
            } else {
                optionParams.setMarginStart(dp(6));
            }
            row.addView(option, optionParams);
            if (ThemeManager.ACCENT_SCHEME.equals(accent)) {
                schemeRow = option;
                option.setOnClickListener(v -> toggleSchemeEditor());
            }
            index++;
        }
        addSchemeEditor(card);
    }

    private void addSchemeEditor(LinearLayout accentCard) {
        schemeEditor = new LinearLayout(this);
        schemeEditor.setOrientation(LinearLayout.VERTICAL);
        schemeEditor.setPadding(0, dp(10), 0, dp(4));
        schemeEditor.setVisibility(View.GONE);
        accentCard.addView(schemeEditor, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = text(getString(R.string.appearance_scheme_hint), 12, MUTED, false);
        hint.setLineSpacing(dp(2), 1f);
        hint.setPadding(0, 0, 0, dp(8));
        schemeEditor.addView(hint);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        schemeHex = new EditText(this);
        schemeHex.setSingleLine(true);
        schemeHex.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        schemeHex.setHint(getString(R.string.appearance_custom_hex_hint));
        schemeHex.setTextColor(TEXT);
        schemeHex.setHintTextColor(MUTED);
        schemeHex.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        schemeHex.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        schemeHex.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable field = rounded(BG_TOP, 12);
        field.setStroke(dp(1), BORDER);
        schemeHex.setBackground(field);
        String savedHex = ThemeManager.schemeHex(this);
        if (!savedHex.isEmpty()) {
            schemeHex.setText(savedHex);
        }
        row.addView(schemeHex, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView save = text(getString(R.string.appearance_scheme_apply), 13, ACCENT_TEXT, true);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable saveBackground = rounded(SECONDARY, 12);
        saveBackground.setStroke(dp(1), SECONDARY_BORDER);
        save.setBackground(new RippleDrawable(
                ColorStateList.valueOf(RIPPLE), saveBackground, null));
        save.setClickable(true);
        save.setFocusable(true);
        save.setOnClickListener(v -> applySchemeHex());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveParams.setMarginStart(dp(8));
        row.addView(save, saveParams);
        schemeEditor.addView(row);

        schemeError = text("", 12, RED, false);
        schemeError.setPadding(0, dp(6), 0, 0);
        schemeError.setVisibility(View.GONE);
        schemeEditor.addView(schemeError);
    }

    private void toggleSchemeEditor() {
        showSchemeEditor(schemeEditor.getVisibility() != View.VISIBLE, true);
    }

    private void showSchemeEditor(boolean show, boolean focus) {
        if (schemeEditor == null) {
            return;
        }
        if (!show) {
            schemeEditor.setVisibility(View.GONE);
            schemeHex.clearFocus();
            InputMethodManager keyboard = (InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.hideSoftInputFromWindow(schemeHex.getWindowToken(), 0);
            }
            return;
        }
        schemeEditor.setAlpha(0f);
        schemeEditor.setVisibility(View.VISIBLE);
        schemeEditor.animate().alpha(1f).setDuration(180).start();
        if (!focus) {
            return;
        }
        schemeRow.post(() -> {
            if (schemeRow != null) {
                int[] rowLocation = new int[2];
                int[] scrollLocation = new int[2];
                schemeRow.getLocationInWindow(rowLocation);
                scrollView.getLocationInWindow(scrollLocation);
                int rowTop = rowLocation[1] - scrollLocation[1]
                        + scrollView.getScrollY();
                scrollView.smoothScrollTo(0, Math.max(0, rowTop - dp(90)));
            }
            schemeHex.postDelayed(() -> {
                schemeHex.requestFocus();
                InputMethodManager keyboard = (InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);
                if (keyboard != null) {
                    keyboard.showSoftInput(schemeHex, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 320);
        });
    }

    private void applySchemeHex() {
        String hex = ThemeManager.normalizeHex(schemeHex.getText().toString());
        if (hex.isEmpty()) {
            schemeError.setText(R.string.appearance_scheme_required);
            schemeError.setVisibility(View.VISIBLE);
            return;
        }
        schemeError.setVisibility(View.GONE);
        AppSettings.setThemeAccent(this, ThemeManager.ACCENT_SCHEME);
        AppSettings.setThemeSchemeHex(this, hex);
        AppSettings.setThemeCustomHex(this, "");
        PublicSettings.write(this);
        Toast.makeText(this, R.string.appearance_scheme_applied, Toast.LENGTH_SHORT).show();
        recreate();
    }

    private void addResetCard(LinearLayout root) {
        LinearLayout card = card();
        LinearLayout.LayoutParams params = cardParams();
        params.topMargin = dp(18);
        root.addView(card, params);

        // The whole card is the touch target, not just the title text.
        card.setBackground(new RippleDrawable(
                ColorStateList.valueOf(RIPPLE), cardBackground(), cardBackground()));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> resetAppearance());

        TextView reset = text(getString(R.string.appearance_reset), 14, ACCENT_TEXT, true);
        card.addView(reset);

        TextView detail = text(getString(R.string.appearance_reset_detail), 12, MUTED, false);
        detail.setPadding(0, dp(4), 0, 0);
        card.addView(detail);
    }

    private void resetAppearance() {
        ThemeManager.reset(this);
        Toast.makeText(this, R.string.appearance_reset_done, Toast.LENGTH_SHORT).show();
        recreate();
    }

    private void selectMode(String mode) {
        if (mode.equals(ThemeManager.savedMode(this))) {
            return;
        }
        AppSettings.setThemeMode(this, mode);
        PublicSettings.write(this);
        recreate();
    }

    private void selectAccent(String accent) {
        if (accent.equals(ThemeManager.accent(this))) {
            return;
        }
        if (ThemeManager.ACCENT_SCHEME.equals(accent)
                && ThemeManager.schemeHex(this).isEmpty()) {
            showSchemeEditor(true, true);
            schemeError.setText(R.string.appearance_scheme_required);
            schemeError.setVisibility(View.VISIBLE);
            Toast.makeText(this, R.string.appearance_scheme_required,
                    Toast.LENGTH_LONG).show();
            return;
        }
        AppSettings.setThemeAccent(this, accent);
        PublicSettings.write(this);
        recreate();
    }

    /** Scrolls the required HEX field above the keyboard, with room to see it. */
    private void scrollSchemeFieldIntoView() {
        if (schemeHex == null || scrollView == null) {
            return;
        }
        int[] field = new int[2];
        int[] scroll = new int[2];
        schemeHex.getLocationInWindow(field);
        scrollView.getLocationInWindow(scroll);
        int fieldTopInContent = field[1] - scroll[1] + scrollView.getScrollY();
        int target = Math.max(0, fieldTopInContent - dp(160));
        scrollView.smoothScrollTo(0, target);
    }

    private View accentOption(String accent, String customHex, boolean selected) {
        int[] colors = ThemeManager.accentColors(this, accent, customHex);

        LinearLayout option = new LinearLayout(this);
        option.setOrientation(LinearLayout.HORIZONTAL);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setPadding(dp(10), dp(10), dp(10), dp(10));
        option.setClickable(true);
        option.setFocusable(true);

        GradientDrawable background = rounded(SECONDARY, 12);
        background.setStroke(dp(selected ? 2 : 1),
                selected ? (LIGHT ? ACCENT_TEXT : colors[0]) : SECONDARY_BORDER);
        option.setBackground(new RippleDrawable(
                ColorStateList.valueOf(RIPPLE), background, null));

        View dot = new View(this);
        if (ThemeManager.ACCENT_SCHEME.equals(accent)) {
            dot.setBackground(new ColorWheelDrawable());
        } else {
            GradientDrawable dotBackground = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT, colors);
            dotBackground.setShape(GradientDrawable.OVAL);
            dot.setBackground(dotBackground);
        }
        option.addView(dot, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView label = text(getString(ThemeManager.accentLabel(accent)),
                12, selected ? TEXT : MUTED, selected);
        label.setPadding(dp(8), 0, 0, 0);
        option.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (selected) {
            TextView check = text("\u2713", 13, ACCENT_TEXT, true);
            option.addView(check);
        }

        option.setOnClickListener(v -> selectAccent(accent));
        return option;
    }

    private TextView optionChip(String label, boolean selected) {
        TextView chip = text(label, 14, selected ? ON_PRIMARY : TEXT, true);
        chip.setGravity(Gravity.CENTER);
        chip.setClickable(true);
        chip.setFocusable(true);
        if (selected) {
            GradientDrawable fill = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{PRIMARY_START, PRIMARY_END});
            fill.setCornerRadius(dp(12));
            chip.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(0x33FFFFFF), fill, null));
        } else {
            GradientDrawable fill = rounded(SECONDARY, 12);
            fill.setStroke(dp(1), SECONDARY_BORDER);
            chip.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(RIPPLE), fill, null));
        }
        return chip;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(cardBackground());
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
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
        view.setTextColor(ON_PRIMARY);
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

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable gradient(int start, int end) {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{start, end}
        );
    }
}
