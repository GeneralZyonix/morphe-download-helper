package be.alterra.morphehelper;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Rounded tile containing the real logo for a download source. */
final class SourceBadge {

    private SourceBadge() {
    }

    static View create(Context context, String key) {
        FrameLayout tile = new FrameLayout(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFFFFFFF);
        background.setCornerRadius(dp(context, 9));
        background.setStroke(dp(context, 1), 0x33000000);
        tile.setBackground(background);
        int padding = dp(context, 4);
        tile.setPadding(padding, padding, padding, padding);

        ImageView logo = new ImageView(context);
        logo.setImageResource(logo(key));
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.CENTER;
        tile.addView(logo, params);
        return tile;
    }

    private static int logo(String key) {
        switch (key) {
            case SourceSettings.APKMIRROR: return R.drawable.source_logo_apkmirror;
            case SourceSettings.APKPURE: return R.drawable.source_logo_apkpure;
            case SourceSettings.UPTODOWN: return R.drawable.source_logo_uptodown;
            case SourceSettings.FDROID: return R.drawable.source_logo_fdroid;
            case SourceSettings.APTOIDE: return R.drawable.source_logo_aptoide;
            case SourceSettings.APPTEKA: return R.drawable.source_logo_appteka;
            case SourceSettings.GITHUB: return R.drawable.source_logo_github;
            default: return R.drawable.ic_logo_mark;
        }
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }
}
