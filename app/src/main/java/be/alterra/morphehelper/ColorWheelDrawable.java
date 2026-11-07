package be.alterra.morphehelper;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;

/** Small hue wheel used as the Custom scheme swatch. */
final class ColorWheelDrawable extends Drawable {

    private static final int[] HUE_COLORS = {
            0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
            0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
    };

    private final Paint wheel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centre = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();
        float radius = Math.min(bounds.width(), bounds.height()) / 2f;
        wheel.setShader(new SweepGradient(cx, cy, HUE_COLORS, null));
        centre.setShader(new RadialGradient(cx, cy, radius,
                0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, wheel);
        canvas.drawCircle(cx, cy, radius, centre);
    }

    @Override
    public void setAlpha(int alpha) {
        wheel.setAlpha(alpha);
        centre.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        wheel.setColorFilter(colorFilter);
        centre.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
