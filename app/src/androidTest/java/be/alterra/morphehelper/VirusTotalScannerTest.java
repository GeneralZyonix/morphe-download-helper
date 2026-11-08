package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class VirusTotalScannerTest {

    @Test
    public void detectionMessageIncludesFlaggedEngines() throws Exception {
        JSONObject attributes = new JSONObject();
        JSONObject stats = new JSONObject();
        stats.put("malicious", 1);
        stats.put("suspicious", 0);
        stats.put("harmless", 60);
        stats.put("undetected", 2);
        attributes.put("last_analysis_stats", stats);

        JSONObject results = new JSONObject();
        results.put("BadEngine", new JSONObject()
                .put("category", "malicious")
                .put("result", "Trojan.Test"));
        results.put("GoodEngine", new JSONObject()
                .put("category", "harmless")
                .put("result", "clean"));
        attributes.put("last_analysis_results", results);

        VirusTotalScanner.Result result =
                VirusTotalScanner.statsResult("abc123", attributes);

        assertEquals(VirusTotalScanner.Status.DETECTED, result.status);
        assertTrue(result.message.contains("BadEngine"));
        assertTrue(result.message.contains("Trojan.Test"));
        assertTrue(!result.message.contains("GoodEngine"));
    }
}
