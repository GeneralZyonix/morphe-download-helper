package be.alterra.morphehelper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** JVM tests for the metered-network policy. */
public class MeteredPolicyTest {

    @Test
    public void askPolicyPromptsOnlyOncePerRequest() {
        assertTrue(MeteredPolicy.shouldAsk(MeteredPolicy.ASK, true, false));
        assertFalse(MeteredPolicy.shouldAsk(MeteredPolicy.ASK, true, true));
        assertFalse(MeteredPolicy.shouldAsk(MeteredPolicy.ASK, false, false));
    }

    @Test
    public void wifiOnlyBlocksMeteredDownloads() {
        assertTrue(MeteredPolicy.shouldBlock(MeteredPolicy.WIFI_ONLY, true, false));
        assertFalse(MeteredPolicy.shouldBlock(MeteredPolicy.WIFI_ONLY, false, false));
        assertFalse(MeteredPolicy.shouldBlock(MeteredPolicy.WIFI_ONLY, true, true));
        assertFalse(MeteredPolicy.shouldAsk(MeteredPolicy.WIFI_ONLY, true, false));
    }

    @Test
    public void allowNeverPromptsOrBlocks() {
        assertFalse(MeteredPolicy.shouldAsk(MeteredPolicy.ALLOW, true, false));
        assertFalse(MeteredPolicy.shouldBlock(MeteredPolicy.ALLOW, true, false));
    }

    @Test
    public void unknownPolicyFallsBackToAsk() {
        assertEquals(MeteredPolicy.ASK, MeteredPolicy.normalize("nonsense"));
        assertEquals(MeteredPolicy.ASK, MeteredPolicy.normalize(null));
    }
}
