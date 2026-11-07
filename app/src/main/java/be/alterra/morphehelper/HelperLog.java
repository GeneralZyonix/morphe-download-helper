package be.alterra.morphehelper;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Small append-only log used to diagnose download problems.
 *
 * <p>The log is written to the app-specific external files directory, so the app does not
 * need storage permissions:</p>
 *
 * <pre>/sdcard/Android/data/be.alterra.morphehelper/files/logs/helper.log</pre>
 *
 * <p>The file can be shared from the launcher screen and is never uploaded automatically.</p>
 */
public final class HelperLog {

    /** Logcat tag used when the same line is also written to the Android log. */
    private static final String TAG = "MorpheDownloadHelper";

    /** File name inside the app's "logs" directory. */
    private static final String FILE_NAME = "helper.log";

    /** Keep the log bounded; recent entries are what matters for support. */
    private static final long MAX_LOG_BYTES = 512L * 1024L;
    private static final long KEEP_LOG_BYTES = 128L * 1024L;

    /** Lazily initialised log file. Null until {@link #init(Context)} has run. */
    private static File logFile;

    /** Utility class: prevent instantiation. */
    private HelperLog() {
    }

    /**
     * Creates the log directory and file the first time it is called.
     *
     * <p>Safe to call from every activity; later calls are ignored.</p>
     */
    public static synchronized void init(Context context) {
        if (logFile != null) {
            return;
        }

        // Prefer the external app directory so users can pull the log without root.
        File dir = context.getExternalFilesDir("logs");
        if (dir == null) {
            // Fall back to internal storage when external storage is unavailable.
            dir = new File(context.getFilesDir(), "logs");
        }
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }

        logFile = new File(dir, FILE_NAME);
        trimIfNeeded();
        info("---- helper started ----");
    }

    /** Returns the current log file, or null before {@link #init(Context)}. */
    public static synchronized File getLogFile() {
        return logFile;
    }

    /** Writes an informational line to Logcat and the log file. */
    public static synchronized void info(String message) {
        write("I", message, null);
    }

    /** Writes an error line, including a stack trace, to Logcat and the log file. */
    public static synchronized void error(String message, Throwable throwable) {
        write("E", message, throwable);
    }

    /** Deletes the current log file and starts a fresh one. */
    public static synchronized void clear() {
        if (logFile != null && logFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            logFile.delete();
        }
        info("---- log cleared ----");
    }

    /**
     * Formats one log entry, mirrors it to Logcat, and appends it to the file.
     *
     * <p>File errors are intentionally ignored: logging must never crash a download.</p>
     */
    private static void write(String level, String message, Throwable throwable) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date());
        String line = time + " " + level + " " + message;

        // Mirror the entry to Logcat so "adb logcat" works during development.
        Log.println(level.equals("E") ? Log.ERROR : Log.INFO, TAG, line);

        if (logFile == null) {
            return;
        }

        // Open in append mode; try-with-resources closes both writers on every path.
        try (FileWriter writer = new FileWriter(logFile, true);
             PrintWriter printer = new PrintWriter(writer)) {
            printer.println(line);
            if (throwable != null) {
                throwable.printStackTrace(printer);
            }
        } catch (IOException ignored) {
            // A failed log write must not break the download flow.
        }

        if (logFile.length() > MAX_LOG_BYTES) {
            trimIfNeeded();
        }
    }

    /**
     * Replaces a large log with its most recent tail, keeping the file small
     * enough to share from a bug report without losing the current session.
     */
    private static void trimIfNeeded() {
        if (logFile == null || !logFile.exists() || logFile.length() <= MAX_LOG_BYTES) {
            return;
        }
        try (RandomAccessFile input = new RandomAccessFile(logFile, "r")) {
            long length = input.length();
            long start = Math.max(0, length - KEEP_LOG_BYTES);
            input.seek(start);
            byte[] tail = new byte[(int) (length - start)];
            input.readFully(tail);

            int firstNewline = -1;
            for (int i = 0; i < tail.length; i++) {
                if (tail[i] == '\n') {
                    firstNewline = i;
                    break;
                }
            }
            int offset = firstNewline >= 0 ? firstNewline + 1 : 0;
            String text = new String(tail, offset, tail.length - offset,
                    StandardCharsets.UTF_8);
            try (FileWriter writer = new FileWriter(logFile, false)) {
                writer.write("---- older log entries trimmed ----\n");
                writer.write(text);
            }
        } catch (IOException ignored) {
            // Keeping the old log is better than risking a failed download.
        }
    }
}
