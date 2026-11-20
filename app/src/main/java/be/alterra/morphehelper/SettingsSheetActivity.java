package be.alterra.morphehelper;

import android.os.Bundle;

/**
 * Transparent host for a single settings section sheet. The theme is declared
 * in the manifest so the platform makes the window translucent before the
 * activity is attached; setting it in {@code onCreate} is too late.
 */
public class SettingsSheetActivity extends SettingsActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getIntent().putExtra(EXTRA_SHEET_ONLY, true);
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }
}
