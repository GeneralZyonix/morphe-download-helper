package be.alterra.morphehelper;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Material 3 Expressive design tokens and lightweight component helpers.
 *
 * <p>The stable screens build their layouts in code and deliberately keep the
 * user-selected Dark / AMOLED / Light / System mode. Rather than forcing every
 * activity onto a fixed Material theme (which would ignore those choices), this
 * helper maps the existing {@link ThemePalette} onto Material 3 colour roles and
 * gives the screens one consistent set of shapes, type styles, ripples, and
 * press motion.</p>
 */
final class M3Ui {

    /** Material 3 Expressive extra-large corner radius. */
    static final int SHAPE_XL = 28;
    /** Card and sheet radius. */
    static final int SHAPE_L = 24;
    /** Field and small container radius. */
    static final int SHAPE_M = 16;
    /** Fully rounded pill. */
    static final int SHAPE_FULL = 999;

    private M3Ui() {
    }

    // ------------------------------------------------------------------
    // Colour roles
    // ------------------------------------------------------------------

    static int surface(ThemePalette palette) {
        return palette.bgTop;
    }

    static int surfaceContainer(ThemePalette palette) {
        return palette.cardTop;
    }

    static int surfaceContainerHigh(ThemePalette palette) {
        return blend(palette.cardTop, palette.cardBottom, 0.5f);
    }

    static int surfaceContainerHighest(ThemePalette palette) {
        return blend(palette.cardTop, palette.text, palette.light ? 0.06f : 0.09f);
    }

    static int onSurface(ThemePalette palette) {
        return palette.text;
    }

    static int onSurfaceVariant(ThemePalette palette) {
        return palette.muted;
    }

    static int outline(ThemePalette palette) {
        return palette.border;
    }

    static int outlineVariant(ThemePalette palette) {
        return blend(palette.border, palette.cardTop, 0.55f);
    }

    static int primary(ThemePalette palette) {
        return palette.primaryStart;
    }

    static int onPrimary(ThemePalette palette) {
        return palette.onPrimary;
    }

    /** A tonal accent container: primary hue mixed into the card surface. */
    static int primaryContainer(ThemePalette palette) {
        return blend(palette.primaryStart, palette.cardTop, palette.light ? 0.84f : 0.74f);
    }

    static int onPrimaryContainer(ThemePalette palette) {
        return palette.light
                ? palette.accentText
                : blend(palette.primaryStart, Color.WHITE, 0.78f);
    }

    /** A quieter accent container for chips and secondary actions. */
    static int secondaryContainer(ThemePalette palette) {
        return blend(palette.primaryStart, palette.cardTop, palette.light ? 0.90f : 0.84f);
    }

    static int onSecondaryContainer(ThemePalette palette) {
        return palette.text;
    }

    static int error(ThemePalette palette) {
        return palette.red;
    }

    static int errorContainer(ThemePalette palette) {
        return blend(palette.red, palette.cardTop, palette.light ? 0.86f : 0.78f);
    }

    // ------------------------------------------------------------------
    // Shapes
    // ------------------------------------------------------------------

    static GradientDrawable card(Context context, ThemePalette palette) {
        return filled(context, surfaceContainer(palette), SHAPE_XL,
                Math.max(1, dp(context, 1)), outlineVariant(palette));
    }

    static GradientDrawable cardHigh(Context context, ThemePalette palette) {
        return filled(context, surfaceContainerHigh(palette), SHAPE_XL,
                Math.max(1, dp(context, 1)), outlineVariant(palette));
    }

    static GradientDrawable hero(Context context, ThemePalette palette) {
        return filled(context, primaryContainer(palette), SHAPE_XL,
                Math.max(1, dp(context, 1)),
                blend(palette.primaryStart, palette.cardTop, 0.55f));
    }

    static GradientDrawable pill(Context context, int color) {
        return filled(context, color, SHAPE_FULL, 0, Color.TRANSPARENT);
    }

    static GradientDrawable field(Context context, ThemePalette palette) {
        return filled(context, surfaceContainerHigh(palette), SHAPE_M,
                Math.max(1, dp(context, 1)), outlineVariant(palette));
    }

    static GradientDrawable filled(Context context, int color, int radiusDp,
                                   int strokeWidth, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    static GradientDrawable brandGradient(Context context, ThemePalette palette, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{palette.primaryStart, palette.primaryEnd});
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static RippleDrawable ripple(Context context, Drawable content, int color) {
        return new RippleDrawable(ColorStateList.valueOf(color), content, null);
    }

    // ------------------------------------------------------------------
    // Type
    // ------------------------------------------------------------------

    static TextView text(Context context, String value, float sizeSp, int color, int weight) {
        return text(context, value, sizeSp, color, weight, 0f);
    }

    static TextView text(Context context, String value, float sizeSp, int color,
                         int weight, float letterSpacing) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif",
                weight >= 600 ? Typeface.BOLD : Typeface.NORMAL));
        view.setLetterSpacing(letterSpacing);
        return view;
    }

    static TextView title(Context context, String value, float sizeSp, int color) {
        TextView view = text(context, value, sizeSp, color, 500, -0.01f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    static TextView display(Context context, String value, float sizeSp, int color) {
        TextView view = text(context, value, sizeSp, color, 700, -0.03f);
        view.setLineSpacing(0, 0.98f);
        return view;
    }

    static TextView overline(Context context, String value, int color) {
        TextView view = text(context, value, 11, color, 700, 0.12f);
        view.setAllCaps(true);
        return view;
    }

    // ------------------------------------------------------------------
    // Buttons
    // ------------------------------------------------------------------

    /**
     * Filled brand button. The accent gradient is intentionally preserved: it is
     * the helper's identity, and Material 3 leaves brand colour to the product.
     */
    static TextView primaryButton(Context context, ThemePalette palette,
                                  String label, int iconRes) {
        TextView button = text(context, label, 15, palette.onPrimary, 600, 0.01f);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setPadding(dp(context, 18), dp(context, 13), dp(context, 18), dp(context, 13));
        button.setBackground(ripple(context,
                brandGradient(context, palette, SHAPE_FULL), 0x33FFFFFF));
        if (iconRes != 0) {
            Drawable icon = context.getDrawable(iconRes);
            if (icon != null) {
                icon.setTint(palette.onPrimary);
                button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
                button.setCompoundDrawablePadding(dp(context, 9));
            }
        }
        motion(button);
        return button;
    }

    /** Filled tonal button for secondary actions. */
    static TextView tonalButton(Context context, ThemePalette palette,
                                String label, int iconRes) {
        int fill = secondaryContainer(palette);
        int foreground = onSecondaryContainer(palette);
        TextView button = text(context, label, 14, foreground, 500, 0.01f);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        button.setBackground(ripple(context, pill(context, fill), palette.ripple));
        if (iconRes != 0) {
            Drawable icon = context.getDrawable(iconRes);
            if (icon != null) {
                icon.setTint(foreground);
                button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
                button.setCompoundDrawablePadding(dp(context, 8));
            }
        }
        motion(button);
        return button;
    }

    /** Outlined button for low-emphasis actions. */
    static TextView outlinedButton(Context context, ThemePalette palette,
                                   String label, int iconRes, int foreground) {
        TextView button = text(context, label, 13, foreground, 500, 0.01f);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        button.setBackground(ripple(context, filled(context,
                surfaceContainerHigh(palette), SHAPE_FULL,
                Math.max(1, dp(context, 1)), outlineVariant(palette)), palette.ripple));
        if (iconRes != 0) {
            Drawable icon = context.getDrawable(iconRes);
            if (icon != null) {
                icon.setTint(foreground);
                button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
                button.setCompoundDrawablePadding(dp(context, 8));
            }
        }
        motion(button);
        return button;
    }

    /** Borderless text button. */
    static TextView textButton(Context context, ThemePalette palette,
                               String label, int iconRes) {
        TextView button = text(context, label, 13, palette.accentText, 500, 0.01f);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        button.setBackground(ripple(context, filled(context, Color.TRANSPARENT,
                SHAPE_FULL, 0, Color.TRANSPARENT), palette.ripple));
        if (iconRes != 0) {
            Drawable icon = context.getDrawable(iconRes);
            if (icon != null) {
                icon.setTint(palette.accentText);
                button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
                button.setCompoundDrawablePadding(dp(context, 8));
            }
        }
        motion(button);
        return button;
    }

    /** Filled chip used for selected filters and status pills. */
    static TextView chip(Context context, ThemePalette palette, String label,
                         boolean selected) {
        int fill = selected ? secondaryContainer(palette) : surfaceContainerHigh(palette);
        int foreground = selected ? onSecondaryContainer(palette) : palette.text;
        TextView chip = text(context, label, 12, foreground, 500, 0.01f);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(context, 12), dp(context, 7), dp(context, 12), dp(context, 7));
        chip.setBackground(filled(context, fill, SHAPE_FULL,
                selected ? 0 : Math.max(1, dp(context, 1)),
                selected ? fill : outlineVariant(palette)));
        return chip;
    }

    // ------------------------------------------------------------------
    // Motion
    // ------------------------------------------------------------------

    /**
     * A small expressive press response: the control contracts quickly and
     * springs back with a slight overshoot. Returning false keeps the normal
     * click and ripple behaviour intact.
     */
    static void motion(View view) {
        view.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.97f).scaleY(0.97f)
                        .setDuration(90).setInterpolator(null).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f)
                        .setDuration(240)
                        .setInterpolator(new OvershootInterpolator(1.8f))
                        .start();
            }
            return false;
        });
    }

    // ------------------------------------------------------------------
    // Controls
    // ------------------------------------------------------------------

    /**
     * Styles a framework switch with a Material 3 track and thumb. A custom
     * drawable is used instead of tint lists so the switch keeps the saved
     * accent in every mode without requiring a Material components theme.
     */
    static void styleSwitch(Switch toggle, ThemePalette palette) {
        toggle.setThumbTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{palette.switchThumb, palette.muted}
        ));
        toggle.setTrackTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{palette.primaryStart, palette.secondaryBorder}
        ));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    static int blend(int from, int to, float ratio) {
        float amount = Math.max(0f, Math.min(1f, ratio));
        return Color.rgb(
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount));
    }
}
