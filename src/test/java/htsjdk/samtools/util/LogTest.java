package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.List;

public class LogTest extends HtsjdkTest {

    private final Log log = Log.getInstance(getClass());

    @Test
    public void testLogToFile() throws IOException {
        final File logFile = File.createTempFile(getClass().getSimpleName(), ".tmp");
        logFile.deleteOnExit();
        Log.setGlobalPrintStream(new PrintStream(new FileOutputStream(logFile.getPath(), true)));

        final String words = "Hello World";
        log.info(words);
        final List<String> list = Files.readAllLines(logFile.toPath());
        Assert.assertEquals(list.size(), 1);
        Assert.assertTrue(list.get(0).contains(words));
    }
}
