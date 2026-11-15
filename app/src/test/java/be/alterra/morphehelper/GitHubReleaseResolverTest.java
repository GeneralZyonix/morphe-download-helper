package be.alterra.morphehelper;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM tests for GitHub release asset ABI selection. */
public class GitHubReleaseResolverTest {

    private static final String[] ARM64 = {"arm64-v8a", "armeabi-v7a", "armeabi"};
    private static final String[] ARM32 = {"armeabi-v7a", "armeabi"};

    @Test
    public void arm64PrefersArmv8Assets() {
        assertTrue(GitHubReleaseResolver.abiScore(
                "Myspoti-2.4.1-ARMv8.apk", ARM64)
                > GitHubReleaseResolver.abiScore(
                "Myspoti-2.4.1-ARMv7.apk", ARM64));
        assertTrue(GitHubReleaseResolver.abiScore(
                "Myspoti-2.4.1-ARMv8.apk", ARM64)
                > GitHubReleaseResolver.abiScore(
                "Myspoti-2.4.1-universal.apk", ARM64));
    }

    @Test
    public void arm32PrefersArmv7Assets() {
        assertTrue(GitHubReleaseResolver.abiScore(
                "Myspoti-2.4.1-ARMv7.apk", ARM32)
                > GitHubReleaseResolver.abiScore(
                "Myspoti-2.4.1-ARMv8.apk", ARM32));
    }

    @Test
    public void unknownAbiPrefersUniversalOverArmAssets() {
        assertTrue(GitHubReleaseResolver.abiScore(
                "Myspoti-2.4.1-universal.apk", new String[]{"unknown"})
                > GitHubReleaseResolver.abiScore(
                "Myspoti-2.4.1-ARMv7.apk", new String[]{"unknown"}));
    }
}
