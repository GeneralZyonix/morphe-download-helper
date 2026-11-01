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
                    case SourceSettings.UPTODOWN:
                        builder = request("https://alarmy.en.uptodown.com/android/download/1218546104-x");
                        break;
                    case SourceSettings.FDROID:
                        builder = request("https://f-droid.org/api/v1/packages/org.fdroid.fdroid");
                        break;
                    case SourceSettings.APTOIDE:
                        builder = request("https://ws75.aptoide.com/api/7/listAppVersions"
                                + "?package_name=com.accuweather.android&limit=50");
                        break;
                    default:
                        throw new IOException("Unknown source");
                }
                Request request = builder.build();
                try (Response response = HTTP.newCall(request).execute()) {
                    int code = response.code();
                    ok = code == expected || (code >= 200 && code < 400);
                    message = "HTTP " + code;
                }
            } catch (Exception e) {
                message = e.getMessage() == null ? "Failed" : e.getMessage();
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
