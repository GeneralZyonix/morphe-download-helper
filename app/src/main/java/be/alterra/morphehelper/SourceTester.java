package be.alterra.morphehelper;

import android.content.Context;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Lightweight source reachability checks used by the Settings screen. */
final class SourceTester {

    interface Callback {
        void done(boolean ok, String message);
    }

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private SourceTester() {
    }

    static void test(Context context, String key, Callback callback) {
        new Thread(() -> {
            boolean ok = false;
            String message = "";
            try {
                if (SourceSettings.UPTODOWN.equals(key)) {
                    try {
                        UptodownApi.resolve("com.instagram.android");
                        ok = true;
                        message = "API";
                    } catch (Exception error) {
                        message = error.getMessage() == null
                                ? context.getString(R.string.source_test_failed)
                                : error.getMessage();
                    }
                    SourceHealth.record(context, key, ok, message);
                    callback.done(ok, message);
                    return;
                }
                Request.Builder builder;
                int expected = 200;
                switch (key) {
                    case SourceSettings.FALLBACK:
                        builder = request("https://api.morphe.software/v2/web-search/"
                                + "org.fdroid.fdroid~1.23.2~arm64-v8a");
                        builder.header("User-Agent", "Morphe-Manager/1.0");
                        expected = 302;
                        break;
                    case SourceSettings.APKMIRROR:
                        builder = request("https://www.apkmirror.com/?post_type=app_release"
                                + "&searchtype=apk&s=org.fdroid.fdroid");
                        break;
                    case SourceSettings.APKPURE:
                        builder = request("https://d.apkpure.com/b/APK/com.discord"
                                + "?versionCode=350008");
                        expected = 302;
                        break;
                    case SourceSettings.APKCUBE:
                        // Reachability only. The actual APKCube download page
                        // requires its own in-app verification.
                        builder = request("https://apkcube.com/embed/card/"
                                + "com.whatsapp.svg");
                        break;
                    case SourceSettings.FDROID:
                        builder = request("https://f-droid.org/api/v1/packages/org.fdroid.fdroid");
                        break;
                    case SourceSettings.APTOIDE:
                        builder = request("https://ws75.aptoide.com/api/7/listAppVersions"
                                + "?package_name=com.accuweather.android&limit=50");
                        break;
                    case SourceSettings.APPTEKA:
                        builder = request("https://appteka.store/api/1/app/info"
                                + "?package=com.snaptube.premium&locale=en");
                        break;
                    case SourceSettings.GITHUB:
                        builder = request("https://api.github.com/repos/brave/brave-browser"
                                + "/releases/latest");
                        builder.header("Accept", "application/vnd.github+json");
                        break;
                    default:
                        throw new IOException(context.getString(R.string.source_test_unknown_source));
                }
                Request request = builder.build();
                try (Response response = HTTP.newCall(request).execute()) {
                    int code = response.code();
                    if (SourceSettings.APKCUBE.equals(key)
                            && (code == 403 || code == 429)) {
                        // Cloudflare may challenge a raw HTTP client even when
                        // the in-app WebView can complete the page.
                        ok = true;
                        message = "HTTP " + code + " (verification page)";
                    } else {
                        ok = code == expected || (code >= 200 && code < 400);
                        message = "HTTP " + code;
                    }
                }
            } catch (Exception e) {
                message = e.getMessage() == null
                        ? context.getString(R.string.source_test_failed)
                        : e.getMessage();
            }
            SourceHealth.record(context, key, ok, message);
            callback.done(ok, message);
        }).start();
    }

    private static Request.Builder request(String url) {
        return new Request.Builder()
                .url(url)
                .header("User-Agent", "Morphe-Helper/1.1")
                .header("Accept", "*/*");
    }
}
