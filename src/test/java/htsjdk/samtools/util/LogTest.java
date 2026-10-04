package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test(singleThreaded = true)
public class LogTest extends HtsjdkTest {

    private final Log log = Log.getInstance(getClass());

    @Test
    public void testLogToFile() throws IOException {
        final Path logFile = Files.createTempFile(getClass().getSimpleName(), ".tmp");
        IOUtil.deleteOnExit(logFile);

        final Log.LogLevel originalLogLevel = Log.getGlobalLogLevel();
        final PrintStream originalStream = Log.getGlobalPrintStream();

        try (final PrintStream stream = new PrintStream(Files.newOutputStream(logFile, StandardOpenOption.APPEND))) {
            Log.setGlobalPrintStream(stream);
            Log.setGlobalLogLevel(Log.LogLevel.DEBUG);
            final String words = "Hello World " + UUID.randomUUID();
            log.info(words);
            // other test classes run alongside and log to the global stream too, so count only this message
            final List<String> lines = Files.readAllLines(logFile).stream()
                    .filter(line -> line.contains(words))
                    .collect(Collectors.toList());
            Assert.assertEquals(Log.getGlobalLogLevel(), Log.LogLevel.DEBUG);
            Assert.assertEquals(lines.size(), 1);
        } finally {
            Log.setGlobalLogLevel(originalLogLevel);
            Log.setGlobalPrintStream(originalStream);
        }
    }

    @Test
    public void testLogToFileWithSupplier() throws IOException {
        final Path logFile = Files.createTempFile(getClass().getSimpleName(), ".tmp");
        IOUtil.deleteOnExit(logFile);

        final Log.LogLevel originalLogLevel = Log.getGlobalLogLevel();
        final PrintStream originalStream = Log.getGlobalPrintStream();

        try (final PrintStream stream = new PrintStream(Files.newOutputStream(logFile, StandardOpenOption.APPEND))) {
            Log.setGlobalPrintStream(stream);
            Log.setGlobalLogLevel(Log.LogLevel.DEBUG);
            final String words = "Hello World " + UUID.randomUUID();
            log.info(() -> words);
            // other test classes run alongside and log to the global stream too, so count only this message
            final List<String> lines = Files.readAllLines(logFile).stream()
                    .filter(line -> line.contains(words))
                    .collect(Collectors.toList());
            Assert.assertEquals(Log.getGlobalLogLevel(), Log.LogLevel.DEBUG);
            Assert.assertEquals(lines.size(), 1);
        } finally {
            Log.setGlobalLogLevel(originalLogLevel);
            Log.setGlobalPrintStream(originalStream);
        }
    }

    @Test
    public void testSupplierIsntCalled() {
        final Log.LogLevel originalLogLevel = Log.getGlobalLogLevel();

        try {
            Log.setGlobalLogLevel(Log.LogLevel.WARNING);
            log.info(() -> {
                throw new RuntimeException("Shouldn't happen!");
            });

        } finally {
            Log.setGlobalLogLevel(originalLogLevel);
        }
    }
}
