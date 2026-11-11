package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

import okhttp3.OkHttpClient;

@RunWith(AndroidJUnit4.class)
public class DownloadTimeoutsTest {

    private OkHttpClient client(String name) throws Exception {
        Field field = DownloadActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return (OkHttpClient) field.get(null);
    }

    @Test
    public void metadataClientUsesShortFailFastTimeouts() throws Exception {
        OkHttpClient client = client("METADATA_HTTP");
        assertEquals(8_000, client.connectTimeoutMillis());
        assertEquals(15_000, client.readTimeoutMillis());
        assertEquals(30_000, client.callTimeoutMillis());
    }

    @Test
    public void apkMirrorClientKeepsMoreRoomForCloudflare() throws Exception {
        OkHttpClient client = client("APKMIRROR_HTTP");
        assertEquals(12_000, client.connectTimeoutMillis());
        assertEquals(25_000, client.readTimeoutMillis());
        assertEquals(45_000, client.callTimeoutMillis());
    }

    @Test
    public void downloadClientHasStallTimeoutButNoWholeTransferCap() throws Exception {
        OkHttpClient client = client("DOWNLOAD_HTTP");
        assertEquals(15_000, client.connectTimeoutMillis());
        assertEquals(20_000, client.readTimeoutMillis());
        assertEquals(0, client.callTimeoutMillis());
    }
}
