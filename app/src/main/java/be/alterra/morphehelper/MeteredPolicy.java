package be.alterra.morphehelper;

/** Pure policy rules for downloading over metered networks. */
final class MeteredPolicy {

    static final String ASK = "ask";
    static final String WIFI_ONLY = "wifi";
    static final String ALLOW = "allow";

    private MeteredPolicy() {
    }

    static String normalize(String policy) {
        if (WIFI_ONLY.equals(policy) || ALLOW.equals(policy)) {
            return policy;
        }
        return ASK;
    }

    /** True when the user should be asked before starting a metered download. */
    static boolean shouldAsk(String policy, boolean metered, boolean approved) {
        return metered && !approved && ASK.equals(normalize(policy));
    }

    /** True when the request must wait for Wi-Fi unless explicitly overridden. */
    static boolean shouldBlock(String policy, boolean metered, boolean approved) {
        return metered && !approved && WIFI_ONLY.equals(normalize(policy));
    }
}
