package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Verifies the APKCube no-match watcher catches a transient result. */
@RunWith(AndroidJUnit4.class)
public class ApkCubeNotFoundTest {

    @Test
    public void transientNoMatchNavigatesImmediately() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> hit = new AtomicReference<>();
        WebView[] holder = new WebView[1];

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            WebView web = new WebView(context);
            holder[0] = web;
            web.getSettings().setJavaScriptEnabled(true);
            web.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view,
                                                        WebResourceRequest request) {
                    return intercept(request.getUrl().toString(), latch, hit);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return intercept(url, latch, hit);
                }

                @Override
                public void onPageCommitVisible(WebView view, String url) {
                    view.evaluateJavascript(
                            DownloadActivity.apkCubeNotFoundScript(), null);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    view.evaluateJavascript(
                            DownloadActivity.apkCubeNotFoundScript(), null);
                }
            });
            String html = "<!doctype html><html><body>"
                    + "<div id='state'>Checking your browser.</div>"
                    + "<script>"
                    + "setTimeout(function(){document.getElementById('state').innerHTML="
                    + "'<p>No match</p><p>We don\\'t hold that package</p>';},300);"
                    + "setTimeout(function(){document.getElementById('state').textContent="
                    + "'Checking your browser.';},600);"
                    + "</script></body></html>";
            web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        });

        assertTrue("no-match navigation was not detected",
                latch.await(10, TimeUnit.SECONDS));
        assertEquals("morphe-helper://apkcube-not-found", hit.get());

        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> holder[0].destroy());
    }

    private static boolean intercept(String url, CountDownLatch latch,
                                     AtomicReference<String> hit) {
        if (url == null || !url.startsWith("morphe-helper://apkcube-not-found")) {
            return false;
        }
        hit.set(url);
        latch.countDown();
        return true;
    }
}
