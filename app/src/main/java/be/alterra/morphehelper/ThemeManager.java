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

import java.util.ArrayList;
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
    static final String ACCENT_SCHEME = "scheme";

    private static final List<String> PRESETS = Arrays.asList(
            ACCENT_MORPHE, ACCENT_AURORA, ACCENT_MINT, ACCENT_FOREST,
            ACCENT_GOLD, ACCENT_SUNSET, ACCENT_ROSE, ACCENT_BERRY,
            ACCENT_ICE, ACCENT_OCEAN, ACCENT_MONO, ACCENT_MATERIAL,
            ACCENT_SCHEME
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
        List<String> values = new ArrayList<>(PRESETS.size() - 1);
        for (String preset : PRESETS) {
            if (!ACCENT_MATERIAL.equals(preset)) {
                values.add(preset);
            }
        }
        return values;
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
        if (ACCENT_CUSTOM.equals(value)) {
            value = ACCENT_SCHEME;
        }
        if (ACCENT_SCHEME.equals(value) && schemeHex(context).isEmpty()) {
            return ACCENT_AURORA;
        }
        if (ACCENT_MATERIAL.equals(value)
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return ACCENT_AURORA;
        }
        return PRESETS.contains(value) || ACCENT_CUSTOM.equals(value)
                ? value : ACCENT_AURORA;
    }

    /** The saved scheme colour, falling back to the old custom-accent HEX. */
    static String schemeHex(Context context) {
        String scheme = normalizeHex(AppSettings.themeSchemeHex(context));
        if (!scheme.isEmpty()) {
            return scheme;
        }
        return normalizeHex(AppSettings.themeCustomHex(context));
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
            case ACCENT_SCHEME: return R.string.theme_accent_scheme;
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
        float tintStrength = ACCENT_SCHEME.equals(accent) ? 1f : 0.82f;
        float textTint = ACCENT_SCHEME.equals(accent) ? 1f : 0.55f;
        float accentSaturation = toHsl(start)[1];
        float surfaceChroma = ACCENT_MONO.equals(accent)
                ? 0.05f : 0.25f + 0.75f * accentSaturation;
        boolean tintSurfaces = ACCENT_SCHEME.equals(accent)
                || AppSettings.themeTintSurfaces(context);

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
            if (tintSurfaces) {
                float hue = toHsl(start)[0];
                bgTop = fromHsl(hue, 0.55f * tintStrength * surfaceChroma, 0.84f);
                bgBottom = fromHsl(hue, 0.62f * tintStrength * surfaceChroma, 0.78f);
                cardTop = fromHsl(hue, 0.38f * tintStrength * surfaceChroma, 0.95f);
                cardBottom = fromHsl(hue, 0.45f * tintStrength * surfaceChroma, 0.91f);
                border = fromHsl(hue, 0.50f * tintStrength * surfaceChroma, 0.68f);
                secondary = fromHsl(hue, 0.45f * tintStrength * surfaceChroma, 0.84f);
                secondaryBorder = fromHsl(hue, 0.40f * tintStrength * surfaceChroma, 0.68f);
                text = fromHsl(hue, 0.32f * textTint * surfaceChroma, 0.08f);
                muted = fromHsl(hue, 0.14f * textTint * surfaceChroma, 0.32f);
                steps = fromHsl(hue, 0.18f * textTint * surfaceChroma, 0.20f);
                footer = fromHsl(hue, 0.12f * textTint * surfaceChroma, 0.46f);
            }
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
            if (tintSurfaces && !amoled) {
                float hue = toHsl(start)[0];
                bgTop = fromHsl(hue, 0.30f * tintStrength * surfaceChroma, 0.04f);
                bgBottom = fromHsl(hue, 0.34f * tintStrength * surfaceChroma, 0.06f);
                cardTop = fromHsl(hue, 0.26f * tintStrength * surfaceChroma, 0.09f);
                cardBottom = fromHsl(hue, 0.30f * tintStrength * surfaceChroma, 0.075f);
                border = fromHsl(hue, 0.22f * tintStrength * surfaceChroma, 0.20f);
                secondary = fromHsl(hue, 0.24f * tintStrength * surfaceChroma, 0.14f);
                secondaryBorder = fromHsl(hue, 0.20f * tintStrength * surfaceChroma, 0.27f);
                text = fromHsl(hue, 0.15f * textTint * surfaceChroma, 0.98f);
                muted = fromHsl(hue, 0.14f * textTint * surfaceChroma, 0.74f);
                steps = fromHsl(hue, 0.12f * textTint * surfaceChroma, 0.90f);
                footer = fromHsl(hue, 0.12f * textTint * surfaceChroma, 0.56f);
            }
        }

        border = accentBorder(accent, start, light, amoled);

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

    /** Applies the themed surface, title, message, and button colours to a dialog. */
    static AlertDialog styleDialog(AlertDialog dialog, Context context) {
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

    private static final class ThemedBuilder extends AlertDialog.Builder {

        private final Context context;

        private ThemedBuilder(Context context) {
            super(new ContextThemeWrapper(context, dialogTheme(context)));
            this.context = context;
        }

        @Override
        public AlertDialog show() {
            return styleDialog(super.show(), context);
        }
    }

    /** Changes when the saved theme changes, so activities can recreate themselves. */
    static String signature(Context context) {
        String accent = accent(context);
        String signature = savedMode(context) + "|" + mode(context) + "|" + accent + "|"
                + AppSettings.themeCustomHex(context) + "|"
                + AppSettings.themeSchemeHex(context) + "|"
                + AppSettings.themeTintSurfaces(context);
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
        if (ACCENT_SCHEME.equals(accent)) {
            String hex = schemeHex(context);
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
            case ACCENT_SCHEME: return schemeSecondary(start);
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

    /** A muted accent-tinted edge that stays visible without becoming a bright line. */
    private static int accentBorder(String accent, int color, boolean light, boolean amoled) {
        float hue = toHsl(color)[0];
        float chroma = ACCENT_MONO.equals(accent)
                ? 0.05f : 0.25f + 0.75f * toHsl(color)[1];
        if (light) {
            return fromHsl(hue, 0.42f * chroma, 0.74f);
        }
        return fromHsl(hue, (amoled ? 0.18f : 0.30f) * chroma,
                amoled ? 0.25f : 0.28f);
    }

    /** A nearby hue for the second stop of a custom-scheme gradient. */
    private static int schemeSecondary(int base) {
        float[] hsl = toHsl(base);
        float hue = (hsl[0] + 38f) % 360f;
        float saturation = Math.max(0.20f, Math.min(0.85f, hsl[1] + 0.08f));
        float lightness = Math.max(0.18f, Math.min(0.82f,
                hsl[2] + (hsl[2] < 0.55f ? 0.12f : -0.08f)));
        return fromHsl(hue, saturation, lightness);
    }

    private static float[] toHsl(int color) {
        float red = Color.red(color) / 255f;
        float green = Color.green(color) / 255f;
        float blue = Color.blue(color) / 255f;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float lightness = (max + min) / 2f;
        float hue = 0f;
        float saturation = 0f;
        float delta = max - min;
        if (delta != 0f) {
            saturation = lightness > 0.5f
                    ? delta / (2f - max - min)
                    : delta / (max + min);
            if (max == red) {
                hue = (green - blue) / delta + (green < blue ? 6f : 0f);
            } else if (max == green) {
                hue = (blue - red) / delta + 2f;
            } else {
                hue = (red - green) / delta + 4f;
            }
            hue *= 60f;
        }
        return new float[]{hue, saturation, lightness};
    }

    private static int fromHsl(float hue, float saturation, float lightness) {
        float h = ((hue % 360f) + 360f) % 360f;
        float s = Math.max(0f, Math.min(1f, saturation));
        float l = Math.max(0f, Math.min(1f, lightness));
        float chroma = (1f - Math.abs(2f * l - 1f)) * s;
        float x = chroma * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = l - chroma / 2f;
        float red;
        float green;
        float blue;
        if (h < 60f) {
            red = chroma; green = x; blue = 0f;
        } else if (h < 120f) {
            red = x; green = chroma; blue = 0f;
        } else if (h < 180f) {
            red = 0f; green = chroma; blue = x;
        } else if (h < 240f) {
            red = 0f; green = x; blue = chroma;
        } else if (h < 300f) {
            red = x; green = 0f; blue = chroma;
        } else {
            red = chroma; green = 0f; blue = x;
        }
        return Color.rgb(channel255(red + m), channel255(green + m), channel255(blue + m));
    }

    private static int channel255(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255f)));
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
        AppSettings.setThemeSchemeHex(context, "");
        AppSettings.setThemeTintSurfaces(context, true);
        PublicSettings.write(context);
    }
}
