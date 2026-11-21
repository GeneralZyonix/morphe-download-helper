package be.alterra.morphehelper;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Recently opened patch apps. Stored locally, most recent first. */
final class PatchHistory {

    private static final String PREFS = "patch_history";
    private static final String KEY_RECENT = "recent_apps";
    private static final int MAX_RECENT = 6;

    private PatchHistory() {
    }

    static void remember(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        List<String> recent = recent(context);
        recent.remove(packageName);
        recent.add(0, packageName);
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_RECENT,
                        android.text.TextUtils.join("\n", recent))
                .apply();
    }

    static List<String> recent(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_RECENT, "");
        List<String> recent = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return recent;
        }
        for (String value : raw.split("\n")) {
            if (!value.isEmpty() && !recent.contains(value)) {
                recent.add(value);
            }
            if (recent.size() >= MAX_RECENT) {
                break;
            }
        }
        return recent;
    }
}
