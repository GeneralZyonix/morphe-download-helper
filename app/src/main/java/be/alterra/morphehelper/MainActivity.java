package be.alterra.morphehelper;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Launcher screen for Morphe Download Helper.
 *
 * <p>This activity does not download anything. It explains how to enable the helper in
 * Morphe, shows which APK sources the helper tries, and exposes maintenance actions:</p>
 *
 * <ul>
 *   <li>Open Morphe</li>
 *   <li>Share or clear the local diagnostic log</li>
 *   <li>Uninstall the helper</li>
 * </ul>
 *
 * <p>The real download work happens in {@link DownloadActivity} when Morphe sends the
 * {@code DOWNLOAD_ORIGINAL_APK} intent.</p>
 */
public class MainActivity extends Activity {

    /** Package name of the Morphe manager app that this helper integrates with. */
    private static final String MORPHE_PACKAGE = "app.morphe.manager";

    // ---------------------------------------------------------------------
    // UI palette (kept in code so the two screens share the same look)
    // ---------------------------------------------------------------------
    private static final int BG_TOP = Color.parseColor("#0B1220");
    private static final int BG_BOTTOM = Color.parseColor("#0F172A");
    private static final int CARD_TOP = Color.parseColor("#111827");
    private static final int CARD_BOTTOM = Color.parseColor("#0F172A");
    private static final int BORDER = Color.parseColor("#1F2937");
    private static final int PRIMARY_START = Color.parseColor("#6366F1");
    private static final int PRIMARY_END = Color.parseColor("#4F46E5");
    private static final int SECONDARY = Color.parseColor("#1F2937");
    private static final int SECONDARY_BORDER = Color.parseColor("#334155");
    private static final int TEXT = Color.parseColor("#F8FAFC");
    private static final int MUTED = Color.parseColor("#94A3B8");
    private static final int STEPS = Color.parseColor("#CBD5E1");
    private static final int RED = Color.parseColor("#F87171");
    private static final int RED_BORDER = Color.parseColor("#7F1D1D");
    private static final int FOOTER = Color.parseColor("#64748B");

    // Amber warning palette used by the "Heads up" card.
    private static final int WARN_BG = Color.parseColor("#1C1917");
    private static final int WARN_BORDER = Color.parseColor("#78350F");
    private static final int WARN_TITLE = Color.parseColor("#FBBF24");
    private static final int WARN_TEXT = Color.parseColor("#FDE68A");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create the log file before any screen can try to share it.
        HelperLog.init(getApplicationContext());

        // Keep the system bars dark so the light status/navigation icons stay readable.
        getWindow().setStatusBarColor(BG_TOP);
        getWindow().setNavigationBarColor(BG_TOP);
        getWindow().getDecorView().setSystemUiVisibility(0);

        // Root is a ScrollView because the content can be taller than short screens.
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(gradient(BG_TOP, BG_BOTTOM, GradientDrawable.Orientation.TOP_BOTTOM));

        // Vertical content column with generous top spacing below the status bar.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(80));
        scroll.addView(root, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        // Used for the "Morphe detected" / "Morphe not installed" status chip.
        boolean morpheInstalled = getPackageManager()
                .getLaunchIntentForPackage(MORPHE_PACKAGE) != null;

        // -----------------------------------------------------------------
        // Header: logo, title, subtitle, and Morphe status chip
        // -----------------------------------------------------------------
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo_mark);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(72), dp(72));
        logoParams.setMarginEnd(dp(16));
        header.addView(logo, logoParams);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Morphe Download Helper", 22, TEXT, true));

        TextView subtitle = text("APK download helper for Morphe", 14, MUTED, false);
        subtitle.setPadding(0, dp(4), 0, 0);
        titles.addView(subtitle);

        // Status chip: green dot when Morphe is installed, amber dot otherwise.
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(10), dp(6), dp(12), dp(6));
        chip.setBackground(rounded(Color.parseColor("#111827"), 999));

        View dot = new View(this);
        GradientDrawable dotDrawable = new GradientDrawable();
        dotDrawable.setShape(GradientDrawable.OVAL);
        dotDrawable.setColor(morpheInstalled
                ? Color.parseColor("#34D399")
                : Color.parseColor("#FBBF24"));
        dot.setBackground(dotDrawable);
        chip.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));

        TextView chipText = text(
                morpheInstalled ? "Morphe detected" : "Morphe not installed",
                12,
                morpheInstalled ? Color.parseColor("#A7F3D0") : Color.parseColor("#FDE68A"),
                false
        );
        chipText.setPadding(dp(8), 0, 0, 0);
        chip.addView(chipText);

        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        chipParams.topMargin = dp(10);
        titles.addView(chip, chipParams);

        // The title block takes the remaining width next to the logo.
        header.addView(titles, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        root.addView(header);

        // -----------------------------------------------------------------
        // "How to use" card: the four steps a user follows inside Morphe
        // -----------------------------------------------------------------
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setBackground(cardBackground());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        cardParams.topMargin = dp(22);
        root.addView(card, cardParams);

        card.addView(text("How to use", 16, TEXT, true));

        // Keep these steps short; the Morphe side owns the actual trust flow.
        TextView steps = text(
                "1. Open Morphe\n"
                        + "2. Settings → APK download helper\n"
                        + "3. Trust “Morphe Download Helper”\n"
                        + "4. Patch an app and choose “Use a helper app”",
                14, STEPS, false);
        steps.setLineSpacing(dp(4), 1f);
        steps.setPadding(0, dp(10), 0, 0);
        card.addView(steps);

        // -----------------------------------------------------------------
        // Warning card: limits the user should know before trusting the helper
        // -----------------------------------------------------------------
        LinearLayout warning = new LinearLayout(this);
        warning.setOrientation(LinearLayout.VERTICAL);
        warning.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable warningBg = rounded(WARN_BG, 18);
        warningBg.setStroke(dp(1), WARN_BORDER);
        warning.setBackground(warningBg);
        LinearLayout.LayoutParams warningParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
        warningParams.topMargin = dp(12);
        root.addView(warning, warningParams);

        // Amber warning icon plus a short heading.
        LinearLayout warningHeader = new LinearLayout(this);
        warningHeader.setOrientation(LinearLayout.HORIZONTAL);
        warningHeader.setGravity(Gravity.CENTER_VERTICAL);
        ImageView warningIcon = new ImageView(this);
        warningIcon.setImageResource(R.drawable.ic_warning);
        warningHeader.addView(warningIcon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView warningTitle = text("Heads up", 14, WARN_TITLE, true);
        warningTitle.setPadding(dp(8), 0, 0, 0);
        warningHeader.addView(warningTitle);
        warning.addView(warningHeader);

        // Uptodown opens an in-app verification page; availability still varies by app.
        TextView warningBody = text(
                "Uptodown links open a verification page inside the helper. This helper only works for specific apps. "
                        + "If it can't find a downloadable APK automatically, Morphe will "
                        + "show its default download button.",
                13, WARN_TEXT, false);
        warningBody.setLineSpacing(dp(3), 1f);
        warningBody.setPadding(0, dp(8), 0, 0);
        warning.addView(warningBody);

        // -----------------------------------------------------------------
        // Download sources card: the order used by DownloadActivity.resolve()
        // -----------------------------------------------------------------
        LinearLayout sourcesCard = new LinearLayout(this);
        sourcesCard.setOrientation(LinearLayout.VERTICAL);
        sourcesCard.setPadding(dp(20), dp(18), dp(20), dp(18));
        sourcesCard.setBackground(cardBackground());
        LinearLayout.LayoutParams sourcesParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
        sourcesParams.topMargin = dp(12);
        root.addView(sourcesCard, sourcesParams);

        sourcesCard.addView(text("Download sources", 16, TEXT, true));

        // Chips are laid out left-to-right; they are short enough for normal phones.
        LinearLayout sourcesRow = new LinearLayout(this);
        sourcesRow.setOrientation(LinearLayout.HORIZONTAL);
        sourcesRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sourcesRowParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
        sourcesRowParams.topMargin = dp(12);
        sourcesCard.addView(sourcesRow, sourcesRowParams);

        // 1. The fallback URL supplied by the patch bundle.
        TextView morpheChip = sourceChip(
                "Morphe link",
                Color.parseColor("#312E81"),
                Color.parseColor("#C7D2FE"));
        LinearLayout.LayoutParams morpheChipParams = new LinearLayout.LayoutParams(
                WRAP_CONTENT, WRAP_CONTENT);
        morpheChipParams.setMarginEnd(dp(8));
        sourcesRow.addView(morpheChip, morpheChipParams);

        // 2. APKMirror search by package name and requested version.
        TextView mirrorChip = sourceChip(
                "APKMirror",
                Color.parseColor("#1E3A8A"),
                Color.parseColor("#BFDBFE"));
        LinearLayout.LayoutParams mirrorChipParams = new LinearLayout.LayoutParams(
                WRAP_CONTENT, WRAP_CONTENT);
        mirrorChipParams.setMarginEnd(dp(8));
        sourcesRow.addView(mirrorChip, mirrorChipParams);

        // 3. APKPure as the last resort.
        TextView pureChip = sourceChip(
                "APKPure",
                Color.parseColor("#064E3B"),
                Color.parseColor("#A7F3D0"));
        sourcesRow.addView(pureChip, new LinearLayout.LayoutParams(
                WRAP_CONTENT, WRAP_CONTENT));

        LinearLayout sourcesRow2 = new LinearLayout(this);
        sourcesRow2.setOrientation(LinearLayout.HORIZONTAL);
        sourcesRow2.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sourcesRow2Params = new LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT);
        sourcesRow2Params.topMargin = dp(8);
        sourcesCard.addView(sourcesRow2, sourcesRow2Params);

        TextView fdroidChip = sourceChip(
                "F-Droid",
                Color.parseColor("#0F766E"),
                Color.parseColor("#CCFBF1"));
        LinearLayout.LayoutParams fdroidChipParams = new LinearLayout.LayoutParams(
                WRAP_CONTENT, WRAP_CONTENT);
        fdroidChipParams.setMarginEnd(dp(8));
        sourcesRow2.addView(fdroidChip, fdroidChipParams);

        TextView aptoideChip = sourceChip(
                "Aptoide",
                Color.parseColor("#7C2D12"),
                Color.parseColor("#FED7AA"));
        LinearLayout.LayoutParams aptoideChipParams = new LinearLayout.LayoutParams(
                WRAP_CONTENT, WRAP_CONTENT);
        aptoideChipParams.setMarginEnd(dp(8));
        sourcesRow2.addView(aptoideChip, aptoideChipParams);

        TextView uptodownChip = sourceChip(
                "Uptodown",
                Color.parseColor("#4C1D95"),
                Color.parseColor("#DDD6FE"));
        sourcesRow2.addView(uptodownChip, new LinearLayout.LayoutParams(
                WRAP_CONTENT, WRAP_CONTENT));

        // Uptodown uses an in-app WebView verification page because of Cloudflare Turnstile.
        TextView disabledSource = text(
                "Tries in this order. Uptodown opens an in-app verification page.",
                12,
                MUTED,
                false);
        disabledSource.setPadding(0, dp(12), 0, 0);
        sourcesCard.addView(disabledSource);

        // Open the source settings screen.
        TextView sourceSettings = secondaryButton("Source settings", TEXT, SECONDARY_BORDER);
        sourceSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        LinearLayout.LayoutParams sourceSettingsParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, dp(50));
        sourceSettingsParams.topMargin = dp(10);
        root.addView(sourceSettings, sourceSettingsParams);

        // -----------------------------------------------------------------
        // Primary action and maintenance buttons
        // -----------------------------------------------------------------
        TextView openMorphe = primaryButton("Open Morphe");
        openMorphe.setOnClickListener(v -> openMorphe());
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(MATCH_PARENT, dp(54));
        openParams.topMargin = dp(20);
        root.addView(openMorphe, openParams);

        // Share log and Clear log sit side by side with equal weight.
        LinearLayout logRow = new LinearLayout(this);
        logRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams logRowParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        logRowParams.topMargin = dp(10);
        root.addView(logRow, logRowParams);

        TextView shareLog = secondaryButton("Share log", TEXT, SECONDARY_BORDER);
        shareLog.setOnClickListener(v -> shareLog());
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        shareParams.setMarginEnd(dp(6));
        logRow.addView(shareLog, shareParams);

        TextView clearLog = secondaryButton("Clear log", TEXT, SECONDARY_BORDER);
        clearLog.setOnClickListener(v -> {
            HelperLog.clear();
            Toast.makeText(this, "Log cleared.", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        clearParams.setMarginStart(dp(6));
        logRow.addView(clearLog, clearParams);

        // Red-tinted destructive action. Android shows its own uninstall confirmation.
        TextView uninstall = secondaryButton("Uninstall helper", RED, RED_BORDER);
        uninstall.setOnClickListener(v -> {
            Intent intent = new Intent(
                    Intent.ACTION_DELETE,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
        });
        LinearLayout.LayoutParams uninstallParams = new LinearLayout.LayoutParams(MATCH_PARENT, dp(50));
        uninstallParams.topMargin = dp(10);
        root.addView(uninstall, uninstallParams);

        // Footer shows the installed version, read from PackageManager.
        String version = "v?";
        try {
            version = "v" + getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            // Keep the fallback text if the package info is unavailable.
        }
        TextView footer = text(version, 12, FOOTER, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        footerParams.topMargin = dp(20);
        root.addView(footer, footerParams);

        setContentView(scroll);
    }

    /**
     * Opens Morphe's launcher activity.
     *
     * <p>Uses {@code getLaunchIntentForPackage} first, then falls back to an explicit
     * MAIN/LAUNCHER intent in case the package is installed but its launcher entry is
     * hidden by the user's launcher.</p>
     */
    private void openMorphe() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(MORPHE_PACKAGE);
        if (launch == null) {
            launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            launch.setPackage(MORPHE_PACKAGE);
        }
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(launch);
            } catch (Exception e) {
                Toast.makeText(this, "Could not open Morphe: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Morphe is not installed.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Shares the diagnostic log through Android's chooser.
     *
     * <p>The log lives in the app-specific external directory, so it must be exposed
     * through the configured {@link FileProvider} before another app can read it.</p>
     */
    private void shareLog() {
        File log = HelperLog.getLogFile();
        if (log == null || !log.exists()) {
            Toast.makeText(this, "No log yet.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    log
            );
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, "Morphe Download Helper log");
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Share helper log"));
        } catch (Exception e) {
            Toast.makeText(this, "Could not share log: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Converts density-independent pixels to physical pixels for this screen. */
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    /** Creates a consistently styled TextView used across the screen. */
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

    /** Creates a small rounded pill used for the source list. */
    private TextView sourceChip(String label, int background, int textColor) {
        TextView chip = text(label, 12, textColor, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        chip.setBackground(rounded(background, 999));
        return chip;
    }

    /** Creates the full-width gradient button used for the main action. */
    private TextView primaryButton(String label) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        view.setTextColor(Color.WHITE);
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

    /** Creates a dark outlined button with a ripple and custom text/border colors. */
    private TextView secondaryButton(String label, int textColor, int strokeColor) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        view.setTextColor(textColor);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));

        GradientDrawable fill = rounded(SECONDARY, 14);
        fill.setStroke(dp(1), strokeColor);
        view.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x22FFFFFF),
                fill,
                null
        ));
        return view;
    }

    /** Rounded, subtly bordered card background used by the content sections. */
    private GradientDrawable cardBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{CARD_TOP, CARD_BOTTOM}
        );
        drawable.setCornerRadius(dp(20));
        drawable.setStroke(dp(1), BORDER);
        return drawable;
    }

    /** Creates a two-stop gradient drawable for the screen background. */
    private GradientDrawable gradient(int start, int end, GradientDrawable.Orientation orientation) {
        GradientDrawable drawable = new GradientDrawable(orientation, new int[]{start, end});
        drawable.setCornerRadius(0);
        return drawable;
    }

    /** Creates a solid rounded drawable for chips and warning cards. */
    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }
}
