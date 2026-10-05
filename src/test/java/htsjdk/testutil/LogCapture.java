package htsjdk.testutil;

import htsjdk.samtools.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runs test code with {@link Log}'s global level and print stream swapped out. Those settings are global and test
 * classes run in parallel, so every test that changes them must do so through this class, which lets only one such
 * test run at a time and restores the settings afterwards. Other tests still log to the swapped-in stream while it
 * is in place, so a test must look only for lines carrying its own message.
 */
public final class LogCapture {
    private static final Object LOCK = new Object();

    private LogCapture() {}

    /** Test code that may throw. */
    @FunctionalInterface
    public interface Action {
        void run() throws Exception;
    }

    /**
     * Runs {@code action} with Log's global level set to {@code level} and its print stream set to {@code stream},
     * then restores both.
     */
    public static void withGlobalLogSettings(final Log.LogLevel level, final PrintStream stream, final Action action)
            throws Exception {
        synchronized (LOCK) {
            final Log.LogLevel originalLevel = Log.getGlobalLogLevel();
            final PrintStream originalStream = Log.getGlobalPrintStream();
            try {
                Log.setGlobalLogLevel(level);
                Log.setGlobalPrintStream(stream);
                action.run();
            } finally {
                Log.setGlobalLogLevel(originalLevel);
                Log.setGlobalPrintStream(originalStream);
            }
        }
    }

    /**
     * Runs {@code action} with Log's global level set to {@code level} and returns the lines Log wrote meanwhile
     * that contain {@code text}.
     */
    public static List<String> linesLoggedContaining(final String text, final Log.LogLevel level, final Action action)
            throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream stream = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            withGlobalLogSettings(level, stream, action);
        }
        return bytes.toString(StandardCharsets.UTF_8)
                .lines()
                .filter(line -> line.contains(text))
                .collect(Collectors.toList());
    }
}
