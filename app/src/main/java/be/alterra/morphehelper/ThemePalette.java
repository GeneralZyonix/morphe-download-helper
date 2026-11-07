package be.alterra.morphehelper;

/** Resolved colours for one theme mode and accent. */
final class ThemePalette {

    final boolean amoled;
    final boolean light;

    final int bgTop;
    final int bgBottom;
    final int cardTop;
    final int cardBottom;
    final int border;

    final int primaryStart;
    final int primaryEnd;
    final int onPrimary;
    /** Accent used for links and secondary highlights on the current surface. */
    final int accentText;
    /** Ripple colour for neutral rows and outlined buttons. */
    final int ripple;
    /** Checked switch thumb colour with enough contrast on the current surface. */
    final int switchThumb;

    final int secondary;
    final int secondaryBorder;

    final int text;
    final int muted;
    final int steps;
    final int footer;

    final int green;
    final int red;
    final int redBorder;
    final int warnTitle;
    final int warnText;
    final int warnBg;
    final int warnBorder;

    ThemePalette(boolean amoled, boolean light,
                 int bgTop, int bgBottom, int cardTop, int cardBottom, int border,
                 int primaryStart, int primaryEnd, int onPrimary, int accentText,
                 int ripple, int switchThumb,
                 int secondary, int secondaryBorder,
                 int text, int muted, int steps, int footer,
                 int green, int red, int redBorder,
                 int warnTitle, int warnText, int warnBg, int warnBorder) {
        this.amoled = amoled;
        this.light = light;
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
        this.cardTop = cardTop;
        this.cardBottom = cardBottom;
        this.border = border;
        this.primaryStart = primaryStart;
        this.primaryEnd = primaryEnd;
        this.onPrimary = onPrimary;
        this.accentText = accentText;
        this.ripple = ripple;
        this.switchThumb = switchThumb;
        this.secondary = secondary;
        this.secondaryBorder = secondaryBorder;
        this.text = text;
        this.muted = muted;
        this.steps = steps;
        this.footer = footer;
        this.green = green;
        this.red = red;
        this.redBorder = redBorder;
        this.warnTitle = warnTitle;
        this.warnText = warnText;
        this.warnBg = warnBg;
        this.warnBorder = warnBorder;
    }
}
