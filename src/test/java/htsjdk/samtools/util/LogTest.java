package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.testutil.LogCapture;
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
    public void testLogToFile() throws Exception {
        final Path logFile = Files.createTempFile(getClass().getSimpleName(), ".tmp");
        IOUtil.deleteOnExit(logFile);

        try (final PrintStream stream = new PrintStream(Files.newOutputStream(logFile, StandardOpenOption.APPEND))) {
            LogCapture.withGlobalLogSettings(Log.LogLevel.DEBUG, stream, () -> {
                final String words = "Hello World " + UUID.randomUUID();
                log.info(words);
                // other test classes run alongside and log to the global stream too, so count only this message
                final List<String> lines = Files.readAllLines(logFile).stream()
                        .filter(line -> line.contains(words))
                        .collect(Collectors.toList());
                Assert.assertEquals(Log.getGlobalLogLevel(), Log.LogLevel.DEBUG);
                Assert.assertEquals(lines.size(), 1);
            });
        }
    }

    @Test
    public void testLogToFileWithSupplier() throws Exception {
        final Path logFile = Files.createTempFile(getClass().getSimpleName(), ".tmp");
        IOUtil.deleteOnExit(logFile);

        try (final PrintStream stream = new PrintStream(Files.newOutputStream(logFile, StandardOpenOption.APPEND))) {
            LogCapture.withGlobalLogSettings(Log.LogLevel.DEBUG, stream, () -> {
                final String words = "Hello World " + UUID.randomUUID();
                log.info(() -> words);
                // other test classes run alongside and log to the global stream too, so count only this message
                final List<String> lines = Files.readAllLines(logFile).stream()
                        .filter(line -> line.contains(words))
                        .collect(Collectors.toList());
                Assert.assertEquals(Log.getGlobalLogLevel(), Log.LogLevel.DEBUG);
                Assert.assertEquals(lines.size(), 1);
            });
        }
    }

    @Test
    public void testSupplierIsntCalled() throws Exception {
        LogCapture.withGlobalLogSettings(Log.LogLevel.WARNING, System.err, () -> {
            log.info(() -> {
                throw new RuntimeException("Shouldn't happen!");
            });
        });
    }
}
