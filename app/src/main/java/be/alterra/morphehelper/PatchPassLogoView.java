package be.alterra.morphehelper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * Theme-aware Patch Pass mark used in the app header.
 *
 * <p>The launcher icon is a fixed purple asset. This view is the dynamic
 * counterpart: it uses the selected accent (including custom scheme) for the
 * M, sticker border, and helper tag. Low-saturation custom schemes use a
 * neutral gradient so white/black/grey themes do not turn beige.</p>
 */
public class PatchPassLogoView extends View {

    private static final int VIEWBOX = 512;
    /** The sticker fills more of the header slot without changing layout width. */
    private static final float MARK_SCALE = 1.24f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private int startColor;
    private int endColor;
    private int stickerColor;
    private boolean light;
    private LinearGradient gradient;

    private boolean previewMode;
    private int previewStart;
    private int previewEnd;
    private boolean previewLight;

    public PatchPassLogoView(Context context) {
        super(context);
        init();
    }

    public PatchPassLogoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PatchPassLogoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateColors();

        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) {
            return;
        }
        float scale = size / VIEWBOX;
        canvas.save();
        canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
        canvas.scale(scale, scale);
        canvas.translate(256f, 256f);
        canvas.scale(MARK_SCALE, MARK_SCALE);
        canvas.translate(-256f, -256f);
        canvas.rotate(-4f, 256f, 256f);

        // Sticker shadow.
        fill.setStyle(Paint.Style.FILL);
        fill.setShader(null);
        fill.setColor(0x33000000);
        rect.set(115f, 114f, 403f, 418f);
        canvas.drawRoundRect(rect, 44f, 44f, fill);

        // Sticker body.
        fill.setColor(stickerColor);
        rect.set(112f, 104f, 400f, 408f);
        canvas.drawRoundRect(rect, 44f, 44f, fill);

        // Gradient sticker border.
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(5f);
        stroke.setShader(gradient);
        canvas.drawRoundRect(rect, 44f, 44f, stroke);

        // Inner dashed border.
        stroke.setStrokeWidth(3f);
        stroke.setAlpha(145);
        stroke.setPathEffect(new DashPathEffect(new float[]{8f, 12f}, 0f));
        rect.set(136f, 128f, 376f, 384f);
        canvas.drawRoundRect(rect, 30f, 30f, stroke);
        stroke.setPathEffect(null);
        stroke.setAlpha(255);

        // Stitched M.
        stroke.setStrokeWidth(38f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        path.reset();
        path.moveTo(172f, 172f);
        path.lineTo(172f, 316f);
        path.moveTo(172f, 172f);
        path.lineTo(256f, 290f);
        path.lineTo(340f, 172f);
        path.moveTo(340f, 172f);
        path.lineTo(340f, 316f);
        canvas.drawPath(path, stroke);

        // Stitch cuts use the sticker colour so they read as gaps.
        stroke.setShader(null);
        stroke.setColor(stickerColor);
        stroke.setStrokeWidth(7f);
        canvas.drawLine(146f, 218f, 198f, 218f, stroke);
        canvas.drawLine(314f, 218f, 366f, 218f, stroke);
        canvas.drawLine(146f, 276f, 198f, 276f, stroke);
        canvas.drawLine(314f, 276f, 366f, 276f, stroke);
        canvas.drawLine(236f, 248f, 276f, 248f, stroke);

        // Tag shadow.
        fill.setShader(null);
        fill.setColor(0x47000000);
        rect.set(155f, 362f, 363f, 422f);
        canvas.drawRoundRect(rect, 30f, 30f, fill);

        // Tag: gradient, darkened for guaranteed white-text contrast.
        fill.setShader(gradient);
        rect.set(152f, 354f, 360f, 414f);
        canvas.drawRoundRect(rect, 30f, 30f, fill);
        fill.setShader(null);
        fill.setColor(0x73000000);
        canvas.drawRoundRect(rect, 30f, 30f, fill);

        // Subtle tag rim.
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f);
        stroke.setColor(0x48FFFFFF);
        canvas.drawRoundRect(rect, 30f, 30f, stroke);

        // Helper wordmark.
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40f);
        textPaint.setShadowLayer(2f, 0f, 1f, 0x66000000);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = 384f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText("helper", 256f, baseline, textPaint);
        textPaint.clearShadowLayer();

        canvas.restore();
    }

    /** Renders a specific accent pair, used by the Appearance scheme picker. */
    public void setPreviewPalette(int start, int end, boolean light) {
        previewMode = true;
        previewStart = start;
        previewEnd = end;
        previewLight = light;
        invalidate();
    }

    private void updateColors() {
        int start;
        int end;
        if (previewMode) {
            light = previewLight;
            start = previewStart;
            end = previewEnd;
        } else {
            ThemePalette palette = ThemeManager.palette(getContext());
            light = palette.light;
            int[] colors = ThemeManager.accentColors(getContext(),
                    ThemeManager.accent(getContext()),
                    AppSettings.themeCustomHex(getContext()));
            start = colors[0];
            end = colors[1];
        }

        float[] hsv = new float[3];
        Color.colorToHSV(start, hsv);
        if (hsv[1] < 0.12f) {
            // A white, black, or grey custom scheme should stay neutral.
            if (light) {
                start = 0xFF334155;
                end = 0xFF64748B;
            } else {
                start = 0xFFF8FAFC;
                end = 0xFF94A3B8;
            }
        } else {
            float average = (relativeLuminance(start) + relativeLuminance(end)) / 2f;
            if (!light && average < 0.16f) {
                start = blend(start, Color.WHITE, 0.38f);
                end = blend(end, Color.WHITE, 0.38f);
            } else if (light && average > 0.78f) {
                start = blend(start, Color.BLACK, 0.28f);
                end = blend(end, Color.BLACK, 0.28f);
            }
        }

        startColor = start;
        endColor = end;
        stickerColor = light ? Color.WHITE : 0xFF0B1220;
        gradient = new LinearGradient(112f, 104f, 400f, 414f,
                start, end, Shader.TileMode.CLAMP);
    }

    private static float relativeLuminance(int color) {
        float red = Color.red(color) / 255f;
        float green = Color.green(color) / 255f;
        float blue = Color.blue(color) / 255f;
        return 0.2126f * red + 0.7152f * green + 0.0722f * blue;
    }

    private static int blend(int color, int target, float amount) {
        int red = Math.round(Color.red(color) + (Color.red(target) - Color.red(color)) * amount);
        int green = Math.round(Color.green(color) + (Color.green(target) - Color.green(color)) * amount);
        int blue = Math.round(Color.blue(color) + (Color.blue(target) - Color.blue(color)) * amount);
        return Color.rgb(red, green, blue);
    }
}
