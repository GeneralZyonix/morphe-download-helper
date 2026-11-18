package be.alterra.morphehelper;

import android.app.Activity;
import android.graphics.Outline;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** In-app visual setup guide with the Morphe screenshots. */
public class GuideActivity extends Activity {

    private ThemePalette palette;
    private String themeSignature;

    @Override
    protected void onResume() {
        super.onResume();
        if (themeSignature != null
                && !themeSignature.equals(ThemeManager.signature(this))) {
            recreate();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PublicSettings.apply(this);
        palette = ThemeManager.palette(this);
        themeSignature = ThemeManager.signature(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(M3Ui.surface(palette));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int screen = getResources().getDisplayMetrics().widthPixels;
        final int horizontal = Math.max(dp(20), (screen - dp(720)) / 2);
        final int baseBottom = dp(32);
        root.setPadding(horizontal, dp(40), horizontal, baseBottom);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(horizontal, dp(40) + top, horizontal, baseBottom + navigation);
            return insets;
        });
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView overline = M3Ui.overline(this,
                getString(R.string.guide_overline), palette.accentText);
        root.addView(overline);

        TextView title = M3Ui.display(this, getString(R.string.guide_title), 30, palette.text);
        title.setPadding(0, dp(8), 0, 0);
        root.addView(title);

        TextView intro = M3Ui.text(this, getString(R.string.guide_intro), 13,
                palette.muted, 400);
        intro.setLineSpacing(dp(3), 1f);
        intro.setPadding(0, dp(10), 0, dp(6));
        root.addView(intro);

        addStep(root, "1", getString(R.string.guide_step1_title),
                getString(R.string.guide_step1_body));
        addImage(root, R.drawable.guide_step1);

        addStep(root, "2", getString(R.string.guide_step2_title),
                getString(R.string.guide_step2_body));
        addStep(root, "3", getString(R.string.guide_step3_title),
                getString(R.string.guide_step3_body));
        addImage(root, R.drawable.guide_step2);

        addStep(root, "4", getString(R.string.guide_step4_title),
                getString(R.string.guide_step4_body));

        TextView close = M3Ui.primaryButton(this, palette,
                getString(R.string.common_back), 0);
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        closeParams.topMargin = dp(22);
        closeParams.bottomMargin = dp(24);
        root.addView(close, closeParams);

        setContentView(scroll);
        ThemeManager.applySystemBars(this);
    }

    private void addStep(LinearLayout parent, String number, String heading, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.TOP);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(M3Ui.card(this, palette));

        TextView badge = M3Ui.text(this, number, 14,
                M3Ui.onPrimaryContainer(palette), 700);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(M3Ui.filled(this,
                M3Ui.primaryContainer(palette), 999, 0, 0));
        card.addView(badge, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, 0, 0);
        labels.addView(M3Ui.title(this, heading, 16, palette.text));
        TextView description = M3Ui.text(this, body, 13, palette.steps, 400);
        description.setLineSpacing(dp(3), 1f);
        description.setPadding(0, dp(5), 0, 0);
        labels.addView(description);
        card.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        parent.addView(card, params);
    }

    private void addImage(LinearLayout parent, int resource) {
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setPadding(dp(8), dp(8), dp(8), dp(8));
        frame.setBackground(M3Ui.filled(this,
                M3Ui.surfaceContainerHigh(palette), M3Ui.SHAPE_XL,
                Math.max(1, dp(1)), M3Ui.outlineVariant(palette)));

        ImageView image = new ImageView(this);
        image.setImageResource(resource);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setClipToOutline(true);
        image.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(18));
            }
        });
        frame.addView(image, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        parent.addView(frame, params);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
