package be.alterra.morphehelper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

@RunWith(AndroidJUnit4.class)
public class DownloadRetryTest {

    @Test
    public void transientFailuresAreRetryable() {
        assertTrue(DownloadActivity.isRetryableDownloadError(
                new SocketTimeoutException("timeout")));
        assertTrue(DownloadActivity.isRetryableDownloadError(
                new UnknownHostException("host")));
        assertTrue(DownloadActivity.isRetryableDownloadError(new EOFException()));
        assertTrue(DownloadActivity.isRetryableDownloadError(
                new IOException("HTTP 500")));
        assertTrue(DownloadActivity.isRetryableDownloadError(
                new IOException("HTTP 429")));
        assertTrue(DownloadActivity.isRetryableDownloadError(
                new IOException("Incomplete download: 10/20 bytes")));
        assertTrue(DownloadActivity.isRetryableDownloadError(
                new IOException("Unexpected end of stream")));
    }

    @Test
    public void permanentFailuresAreNotRetryable() {
        assertFalse(DownloadActivity.isRetryableDownloadError(
                new IOException("HTTP 404")));
        assertFalse(DownloadActivity.isRetryableDownloadError(
                new IOException("Server returned HTML instead of an APK")));
        assertFalse(DownloadActivity.isRetryableDownloadError(
                new IOException("Empty response")));
        assertFalse(DownloadActivity.isRetryableDownloadError(
                new IOException("Cancelled")));
    }

    @Test
    public void contentRangeMustStartAtResumeOffset() {
        assertTrue(DownloadActivity.contentRangeStartsAt(
                "bytes 100-199/200", 100));
        assertFalse(DownloadActivity.contentRangeStartsAt(
                "bytes 0-199/200", 100));
        assertFalse(DownloadActivity.contentRangeStartsAt(null, 100));
        assertFalse(DownloadActivity.contentRangeStartsAt("garbage", 100));
    }

    @Test
    public void segmentRangeMustMatchRequestedSlice() {
        assertTrue(DownloadActivity.rangeMatches(
                "bytes 100-199/200", 100, 199, 200));
        assertFalse(DownloadActivity.rangeMatches(
                "bytes 100-198/200", 100, 199, 200));
        assertFalse(DownloadActivity.rangeMatches(
                "bytes 100-199/201", 100, 199, 200));
    }
}
