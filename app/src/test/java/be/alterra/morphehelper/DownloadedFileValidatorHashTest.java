package be.alterra.morphehelper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** JVM tests for file-details hashing. */
public class DownloadedFileValidatorHashTest {

    @Test
    public void sha256MatchesTheKnownDigest() throws Exception {
        File file = File.createTempFile("morphe-sha-", ".bin");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write("abc".getBytes(StandardCharsets.UTF_8));
        }
        try {
            assertEquals(
                    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                    DownloadedFileValidator.sha256(file));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
