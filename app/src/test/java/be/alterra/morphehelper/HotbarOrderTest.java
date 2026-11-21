package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class HotbarOrderTest {

    @Test
    public void defaultOrderUsesRequestedOrder() {
        assertEquals(
                Arrays.asList("home", "sources", "downloads", "settings"),
                AppSettings.normalizeHotbarOrder(Collections.emptyList()));
    }

    @Test
    public void savedOrderIsKeptAndMissingTabsAreAppended() {
        assertEquals(
                Arrays.asList("settings", "home", "sources", "downloads"),
                AppSettings.normalizeHotbarOrder(Arrays.asList("settings", "home")));
    }

    @Test
    public void unknownAndDuplicateTabsAreDropped() {
        assertEquals(
                Arrays.asList("sources", "home", "downloads", "settings"),
                AppSettings.normalizeHotbarOrder(
                        Arrays.asList("sources", "unknown", "sources", "home")));
    }
}
