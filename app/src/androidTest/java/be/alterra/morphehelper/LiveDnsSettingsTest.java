package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Instrumentation checks for the live DoH provider settings. */
public class LiveDnsSettingsTest {

    private Context app;
    private SharedPreferences prefs;
    private String previousProvider;
    private String previousUrl;

    @Before
    public void setUp() {
        app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = app.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        previousProvider = prefs.getString("live_dns_provider", null);
        previousUrl = prefs.getString("live_dns_custom_url", null);
    }

    @After
    public void tearDown() {
        SharedPreferences.Editor editor = prefs.edit();
        if (previousProvider == null) {
            editor.remove("live_dns_provider");
        } else {
            editor.putString("live_dns_provider", previousProvider);
        }
        if (previousUrl == null) {
            editor.remove("live_dns_custom_url");
        } else {
            editor.putString("live_dns_custom_url", previousUrl);
        }
        editor.apply();
    }

    @Test
    public void providerAcceptsOnlyKnownValues() {
        AppSettings.setLiveDnsProvider(app, AppSettings.DNS_PROVIDER_CONTROLD);
        assertEquals(AppSettings.DNS_PROVIDER_CONTROLD,
                AppSettings.liveDnsProvider(app));

        AppSettings.setLiveDnsProvider(app, "unknown");
        assertEquals(AppSettings.DNS_PROVIDER_DEFAULT,
                AppSettings.liveDnsProvider(app));
    }

    @Test
    public void customUrlIsTrimmed() {
        AppSettings.setLiveDnsCustomUrl(app, "  https://example.com/dns-query  ");
        assertEquals("https://example.com/dns-query",
                AppSettings.liveDnsCustomUrl(app));
    }
}
