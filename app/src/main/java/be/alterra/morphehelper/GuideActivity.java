package be.alterra.morphehelper;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** In-app visual setup guide with the Morphe screenshots. */
public class GuideActivity extends Activity {

    private int BG_TOP;
    private int BG_BOTTOM;
    private int TEXT;
    private int MUTED;
    private int STEPS;
    private int PRIMARY_START;
    private int PRIMARY_END;
    private int ON_PRIMARY;
    private String themeSignature;

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
        TEXT = palette.text;
        MUTED = palette.muted;
        STEPS = palette.steps;
        PRIMARY_START = palette.primaryStart;
        PRIMARY_END = palette.primaryEnd;
        ON_PRIMARY = palette.onPrimary;
        themeSignature = ThemeManager.signature(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PublicSettings.apply(this);
        applyPalette();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG_TOP, BG_BOTTOM}));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int screen = getResources().getDisplayMetrics().widthPixels;
        final int horizontal = Math.max(dp(24), (screen - dp(720)) / 2);
        final int baseBottom = dp(32);
        root.setPadding(horizontal, dp(40), horizontal, baseBottom);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(horizontal, dp(40), horizontal, baseBottom + navigation);
            return insets;
        });
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text(getString(R.string.guide_title), 24, TEXT, true);
        root.addView(title);

        TextView intro = text(getString(R.string.guide_intro), 13, MUTED, false);
        intro.setLineSpacing(dp(3), 1f);
        intro.setPadding(0, dp(8), 0, dp(20));
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

        TextView close = primaryButton(getString(R.string.common_back));
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        closeParams.topMargin = dp(20);
        closeParams.bottomMargin = dp(24);
        root.addView(close, closeParams);

        setContentView(scroll);
        ThemeManager.applySystemBars(this);
    }

    private void addStep(LinearLayout parent, String number, String heading, String body) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(18), 0, dp(8));

        TextView badge = text(number, 14, ON_PRIMARY, true);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(PRIMARY_END);
        fill.setCornerRadius(dp(16));
        badge.setBackground(fill);
        row.addView(badge, new LinearLayout.LayoutParams(dp(32), dp(32)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, 0, 0);
        labels.addView(text(heading, 16, TEXT, true));
        TextView description = text(body, 13, STEPS, false);
        description.setLineSpacing(dp(3), 1f);
        description.setPadding(0, dp(4), 0, 0);
        labels.addView(description);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        parent.addView(row);
    }

    private void addImage(LinearLayout parent, int resource) {
        ImageView image = new ImageView(this);
        image.setImageResource(resource);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        parent.addView(image, params);
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private TextView primaryButton(String label) {
        TextView view = text(label, 16, ON_PRIMARY, true);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        GradientDrawable fill = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{PRIMARY_START, PRIMARY_END});
        fill.setCornerRadius(dp(16));
        view.setBackground(fill);
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}


