package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LogTest extends HtsjdkTest {

  private final Log log = Log.getInstance(getClass());

  @Test
  public void testLogToFile() throws IOException {
    final File logFile = File.createTempFile(getClass().getSimpleName(), ".tmp");
    logFile.deleteOnExit();

    final Log.LogLevel originalLogLevel = Log.getGlobalLogLevel();
    final PrintStream originalStream = Log.getGlobalPrintStream();

    try (final PrintStream stream =
        new PrintStream(new FileOutputStream(logFile.getPath(), true))) {
      Log.setGlobalPrintStream(stream);
      Log.setGlobalLogLevel(Log.LogLevel.DEBUG);
      final String words = "Hello World";
      log.info(words);
      final List<String> list = Files.readAllLines(logFile.toPath());
      Assert.assertEquals(Log.getGlobalLogLevel(), Log.LogLevel.DEBUG);
      Assert.assertEquals(list.size(), 1);
      Assert.assertTrue(list.get(0).contains(words));
    } finally {
      Log.setGlobalLogLevel(originalLogLevel);
      Log.setGlobalPrintStream(originalStream);
    }
  }
}
