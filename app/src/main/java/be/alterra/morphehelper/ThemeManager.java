package be.alterra.morphehelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Resolves the saved appearance into concrete colours. */
final class ThemeManager {

    static final String MODE_DARK = "dark";
    static final String MODE_AMOLED = "amoled";
    static final String MODE_LIGHT = "light";
    static final String MODE_SYSTEM = "system";

    static final String ACCENT_MORPHE = "morphe";
    static final String ACCENT_AURORA = "aurora";
    static final String ACCENT_MINT = "mint";
    static final String ACCENT_FOREST = "forest";
    static final String ACCENT_GOLD = "gold";
    static final String ACCENT_SUNSET = "sunset";
    static final String ACCENT_ROSE = "rose";
    static final String ACCENT_BERRY = "berry";
    static final String ACCENT_ICE = "ice";
    static final String ACCENT_OCEAN = "ocean";
    static final String ACCENT_MONO = "mono";
    static final String ACCENT_MATERIAL = "material";
    static final String ACCENT_CUSTOM = "custom";

    private static final List<String> PRESETS = Arrays.asList(
            ACCENT_MORPHE, ACCENT_AURORA, ACCENT_MINT, ACCENT_FOREST,
            ACCENT_GOLD, ACCENT_SUNSET, ACCENT_ROSE, ACCENT_BERRY,
            ACCENT_ICE, ACCENT_OCEAN, ACCENT_MONO, ACCENT_MATERIAL
    );

    private ThemeManager() {
    }

    static List<String> presets() {
        return PRESETS;
    }

    /** Presets that can be selected on this Android version. */
    static List<String> availablePresets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return PRESETS;
        }
        return PRESETS.subList(0, PRESETS.size() - 1);
    }

    /** The user's saved choice, which may be "system". */
    static String savedMode(Context context) {
        String value = AppSettings.themeMode(context);
        if (MODE_AMOLED.equalsIgnoreCase(value)) {
            return MODE_AMOLED;
        }
        if (MODE_LIGHT.equalsIgnoreCase(value)) {
            return MODE_LIGHT;
        }
        if (MODE_SYSTEM.equalsIgnoreCase(value)) {
            return MODE_SYSTEM;
        }
        if (MODE_DARK.equalsIgnoreCase(value)) {
            return MODE_DARK;
        }
        return MODE_SYSTEM;
    }

    /** The appearance actually used for colours; resolves "system" to light or dark. */
    static String mode(Context context) {
        String saved = savedMode(context);
        if (MODE_SYSTEM.equals(saved)) {
            return systemLight(context) ? MODE_LIGHT : MODE_DARK;
        }
        return saved;
    }

    private static boolean systemLight(Context context) {
        int night = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return night != Configuration.UI_MODE_NIGHT_YES;
    }

    static String accent(Context context) {
        String value = AppSettings.themeAccent(context);
        if (ACCENT_MATERIAL.equals(value)
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return ACCENT_AURORA;
        }
        return PRESETS.contains(value) || ACCENT_CUSTOM.equals(value)
                ? value : ACCENT_AURORA;
    }

    static int modeLabel(String mode) {
        if (MODE_AMOLED.equals(mode)) {
            return R.string.theme_mode_amoled;
        }
        if (MODE_LIGHT.equals(mode)) {
            return R.string.theme_mode_light;
        }
        if (MODE_SYSTEM.equals(mode)) {
            return R.string.theme_mode_system;
        }
        return R.string.theme_mode_dark;
    }

    static int accentLabel(String accent) {
        switch (accent) {
            case ACCENT_AURORA: return R.string.theme_accent_aurora;
            case ACCENT_MINT: return R.string.theme_accent_mint;
            case ACCENT_FOREST: return R.string.theme_accent_forest;
            case ACCENT_GOLD: return R.string.theme_accent_gold;
            case ACCENT_SUNSET: return R.string.theme_accent_sunset;
            case ACCENT_ROSE: return R.string.theme_accent_rose;
            case ACCENT_BERRY: return R.string.theme_accent_berry;
            case ACCENT_ICE: return R.string.theme_accent_ice;
            case ACCENT_OCEAN: return R.string.theme_accent_ocean;
            case ACCENT_MONO: return R.string.theme_accent_mono;
            case ACCENT_MATERIAL: return R.string.theme_accent_material;
            case ACCENT_CUSTOM: return R.string.theme_accent_custom;
            default: return R.string.theme_accent_morphe;
        }
    }

    static String summary(Context context) {
        String saved = savedMode(context);
        String label = context.getString(modeLabel(saved));
        if (MODE_SYSTEM.equals(saved)) {
            label += " (" + context.getString(modeLabel(mode(context))) + ")";
        }
        return label + " • " + context.getString(accentLabel(accent(context)));
    }

    static ThemePalette palette(Context context) {
        String mode = mode(context);
        String accent = accent(context);
        int start = accentStart(context, accent, AppSettings.themeCustomHex(context));
        int end = accentEnd(context, accent, start);
        boolean amoled = MODE_AMOLED.equals(mode);
        boolean light = MODE_LIGHT.equals(mode);

        int bgTop;
        int bgBottom;
        int cardTop;
        int cardBottom;
        int border;
        int secondary;
        int secondaryBorder;
        int text;
        int muted;
        int steps;
        int footer;
        int green;
        int red;
        int redBorder;
        int warnTitle;
        int warnText;
        int warnBg;
        int warnBorder;

        if (light) {
            bgTop = Color.parseColor("#F5F7FB");
            bgBottom = Color.parseColor("#E9EEF8");
            cardTop = Color.WHITE;
            cardBottom = Color.parseColor("#F8FAFC");
            border = Color.parseColor("#E2E8F0");
            secondary = Color.parseColor("#EEF2F7");
            secondaryBorder = Color.parseColor("#CBD5E1");
            text = Color.parseColor("#0F172A");
            muted = Color.parseColor("#64748B");
            steps = Color.parseColor("#334155");
            footer = Color.parseColor("#94A3B8");
            green = Color.parseColor("#047857");
            red = Color.parseColor("#DC2626");
            redBorder = Color.parseColor("#FECACA");
            warnTitle = Color.parseColor("#B45309");
            warnText = Color.parseColor("#92400E");
            warnBg = Color.parseColor("#FFFBEB");
            warnBorder = Color.parseColor("#FDE68A");
        } else {
            bgTop = amoled ? Color.BLACK : Color.parseColor("#0B1220");
            bgBottom = amoled ? Color.BLACK : Color.parseColor("#0F172A");
            cardTop = amoled ? Color.parseColor("#0A0A0A") : Color.parseColor("#111827");
            cardBottom = amoled ? Color.parseColor("#080808") : Color.parseColor("#0F172A");
            border = amoled ? Color.parseColor("#1F1F1F") : Color.parseColor("#1F2937");
            secondary = amoled ? Color.parseColor("#121212") : Color.parseColor("#1F2937");
            secondaryBorder = amoled
                    ? Color.parseColor("#2A2A2A") : Color.parseColor("#334155");
            text = Color.parseColor("#F8FAFC");
            muted = Color.parseColor("#94A3B8");
            steps = Color.parseColor("#CBD5E1");
            footer = Color.parseColor("#64748B");
            green = Color.parseColor("#34D399");
            red = Color.parseColor("#F87171");
            redBorder = Color.parseColor("#7F1D1D");
            warnTitle = Color.parseColor("#FBBF24");
            warnText = Color.parseColor("#FDE68A");
            warnBg = Color.parseColor("#1C1917");
            warnBorder = Color.parseColor("#78350F");
        }

        return new ThemePalette(
                amoled, light,
                bgTop, bgBottom, cardTop, cardBottom, border,
                start, end, onAccent(start, end),
                light ? readableAccentText(start) : soften(start, end),
                light ? 0x22000000 : 0x22FFFFFF,
                light && relativeLuminance(start) > 0.60
                        ? onAccent(start, end) : start,
                secondary, secondaryBorder,
                text, muted, steps, footer,
                green, red, redBorder,
                warnTitle, warnText, warnBg, warnBorder
        );
    }

    /** Applies the themed system bars and the matching light/dark icon style. */
    static void applySystemBars(Activity activity) {
        ThemePalette palette = palette(activity);
        Window window = activity.getWindow();
        window.setStatusBarColor(palette.bgTop);
        if (palette.light && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Dark icons are not available on the navigation bar before Android 8.
            window.setNavigationBarColor(Color.parseColor("#0B1220"));
        } else {
            window.setNavigationBarColor(palette.bgTop);
        }
        window.setBackgroundDrawable(new ColorDrawable(palette.bgTop));

        WindowInsetsControllerCompat controller =
                ViewCompat.getWindowInsetsController(window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(palette.light);
            controller.setAppearanceLightNavigationBars(palette.light);
        }

        int flags = 0;
        if (palette.light) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    /** Opens a dialog whose accent buttons follow the active theme. */
    static AlertDialog.Builder dialog(Context context) {
        return new ThemedBuilder(context);
    }

    private static int dialogTheme(Context context) {
        boolean light = MODE_LIGHT.equals(mode(context));
        switch (accent(context)) {
            case ACCENT_AURORA:
                return light ? R.style.AppDialogThemeLight_Aurora
                        : R.style.AppDialogTheme_Aurora;
            case ACCENT_MINT:
                return light ? R.style.AppDialogThemeLight_Mint
                        : R.style.AppDialogTheme_Mint;
            case ACCENT_FOREST:
                return light ? R.style.AppDialogThemeLight_Forest
                        : R.style.AppDialogTheme_Forest;
            case ACCENT_GOLD:
                return light ? R.style.AppDialogThemeLight_Gold
                        : R.style.AppDialogTheme_Gold;
            case ACCENT_SUNSET:
                return light ? R.style.AppDialogThemeLight_Sunset
                        : R.style.AppDialogTheme_Sunset;
            case ACCENT_ROSE:
                return light ? R.style.AppDialogThemeLight_Rose
                        : R.style.AppDialogTheme_Rose;
            case ACCENT_BERRY:
                return light ? R.style.AppDialogThemeLight_Berry
                        : R.style.AppDialogTheme_Berry;
            case ACCENT_ICE:
                return light ? R.style.AppDialogThemeLight_Ice
                        : R.style.AppDialogTheme_Ice;
            case ACCENT_OCEAN:
                return light ? R.style.AppDialogThemeLight_Ocean
                        : R.style.AppDialogTheme_Ocean;
            case ACCENT_MONO:
                return light ? R.style.AppDialogThemeLight_Mono
                        : R.style.AppDialogTheme_Mono;
            case ACCENT_MATERIAL:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    return light ? R.style.AppDialogThemeLight_Material
                            : R.style.AppDialogTheme_Material;
                }
                return light ? R.style.AppDialogThemeLight : R.style.AppDialogTheme;
            default:
                return light ? R.style.AppDialogThemeLight : R.style.AppDialogTheme;
        }
    }

    private static final class ThemedBuilder extends AlertDialog.Builder {

        private final Context context;

        private ThemedBuilder(Context context) {
            super(new ContextThemeWrapper(context, dialogTheme(context)));
            this.context = context;
        }

        @Override
        public AlertDialog show() {
            AlertDialog dialog = super.show();
            ThemePalette palette = palette(context);
            int accent = palette.accentText;
            tint(dialog, AlertDialog.BUTTON_POSITIVE, accent);
            tint(dialog, AlertDialog.BUTTON_NEGATIVE, accent);
            tint(dialog, AlertDialog.BUTTON_NEUTRAL, accent);

            TextView title = dialog.findViewById(android.R.id.title);
            if (title != null) {
                title.setTextColor(palette.text);
            }
            TextView message = dialog.findViewById(android.R.id.message);
            if (message != null) {
                message.setTextColor(palette.steps);
            }

            Window window = dialog.getWindow();
            if (window != null) {
                float density = context.getResources().getDisplayMetrics().density;
                GradientDrawable background = new GradientDrawable();
                background.setColor(palette.cardTop);
                background.setCornerRadius(24 * density);
                background.setStroke(Math.max(1, (int) density), palette.border);
                int inset = (int) (10 * density);
                window.setBackgroundDrawable(new InsetDrawable(
                        background, inset, inset, inset, inset));
            }
            return dialog;
        }

        private static void tint(AlertDialog dialog, int which, int color) {
            Button button = dialog.getButton(which);
            if (button != null) {
                button.setTextColor(color);
            }
        }
    }

    /** Changes when the saved theme changes, so activities can recreate themselves. */
    static String signature(Context context) {
        String accent = accent(context);
        String signature = savedMode(context) + "|" + mode(context) + "|" + accent + "|"
                + AppSettings.themeCustomHex(context);
        if (ACCENT_MATERIAL.equals(accent)) {
            int[] colors = accentColors(context, accent, "");
            signature += "|" + colors[0] + "|" + colors[1];
        }
        return signature;
    }

    /** Resolves the gradient for a preset (or the saved custom HEX) without saving. */
    static int[] accentColors(Context context, String accent, String customHex) {
        int start = accentStart(context, accent, customHex);
        return new int[]{start, accentEnd(context, accent, start)};
    }

    static String normalizeHex(String value) {
        if (value == null) {
            return "";
        }
        String hex = value.trim().toUpperCase(Locale.US);
        if (!hex.startsWith("#")) {
            hex = "#" + hex;
        }
        if (!hex.matches("#[0-9A-F]{6}")) {
            return "";
        }
        try {
            Color.parseColor(hex);
            return hex;
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static int accentStart(Context context, String accent, String customHex) {
        if (ACCENT_CUSTOM.equals(accent)) {
            String hex = normalizeHex(customHex);
            return hex.isEmpty() ? Color.parseColor("#6366F1") : Color.parseColor(hex);
        }
        switch (accent) {
            case ACCENT_AURORA: return Color.parseColor("#8B5CF6");
            case ACCENT_MINT: return Color.parseColor("#34D399");
            case ACCENT_FOREST: return Color.parseColor("#22C55E");
            case ACCENT_GOLD: return Color.parseColor("#F59E0B");
            case ACCENT_SUNSET: return Color.parseColor("#FB923C");
            case ACCENT_ROSE: return Color.parseColor("#F472B6");
            case ACCENT_BERRY: return Color.parseColor("#A855F7");
            case ACCENT_ICE: return Color.parseColor("#60A5FA");
            case ACCENT_OCEAN: return Color.parseColor("#0EA5E9");
            case ACCENT_MONO: return Color.parseColor("#E2E8F0");
            case ACCENT_MATERIAL:
                return systemAccent(context, android.R.color.system_accent1_500,
                        Color.parseColor("#8B5CF6"));
            default: return Color.parseColor("#6366F1");
        }
    }

    private static int accentEnd(Context context, String accent, int start) {
        switch (accent) {
            case ACCENT_AURORA: return Color.parseColor("#22D3EE");
            case ACCENT_MINT: return Color.parseColor("#14B8A6");
            case ACCENT_FOREST: return Color.parseColor("#15803D");
            case ACCENT_GOLD: return Color.parseColor("#FCD34D");
            case ACCENT_SUNSET: return Color.parseColor("#F43F5E");
            case ACCENT_ROSE: return Color.parseColor("#E11D48");
            case ACCENT_BERRY: return Color.parseColor("#EC4899");
            case ACCENT_ICE: return Color.parseColor("#38BDF8");
            case ACCENT_OCEAN: return Color.parseColor("#2563EB");
            case ACCENT_MONO: return Color.parseColor("#94A3B8");
            case ACCENT_MATERIAL:
                return systemAccent(context, android.R.color.system_accent2_500,
                        Color.parseColor("#22D3EE"));
            case ACCENT_CUSTOM: return darken(start, 0.22);
            default: return Color.parseColor("#4F46E5");
        }
    }

    private static int systemAccent(Context context, int resource, int fallback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return fallback;
        }
        try {
            return context.getColor(resource);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int darken(int color, double amount) {
        double factor = Math.max(0, 1 - amount);
        return Color.rgb(
                (int) (Color.red(color) * factor),
                (int) (Color.green(color) * factor),
                (int) (Color.blue(color) * factor)
        );
    }

    /** Blends an accent toward white so it stays readable as text on dark cards. */
    private static int soften(int start, int end) {
        int red = (Color.red(start) + Color.red(end)) / 2;
        int green = (Color.green(start) + Color.green(end)) / 2;
        int blue = (Color.blue(start) + Color.blue(end)) / 2;
        return Color.rgb(lighten(red), lighten(green), lighten(blue));
    }

    private static int lighten(int value) {
        return (int) (value + (255 - value) * 0.42);
    }

    /** Darkens an accent until it is readable as text on a light surface. */
    private static int readableAccentText(int color) {
        int result = color;
        int guard = 0;
        while (relativeLuminance(result) > 0.18 && guard++ < 10) {
            result = darken(result, 0.18);
        }
        return result;
    }

    private static int onAccent(int start, int end) {
        double luminance = Math.max(relativeLuminance(start), relativeLuminance(end));
        return luminance > 0.30 ? Color.BLACK : Color.WHITE;
    }

    private static double relativeLuminance(int color) {
        double r = channel(Color.red(color));
        double g = channel(Color.green(color));
        double b = channel(Color.blue(color));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int value) {
        double channel = value / 255.0;
        return channel <= 0.03928
                ? channel / 12.92
                : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    static void reset(Context context) {
        AppSettings.setThemeMode(context, MODE_SYSTEM);
        AppSettings.setThemeAccent(context, ACCENT_AURORA);
        AppSettings.setThemeCustomHex(context, "");
        PublicSettings.write(context);
    }
}
