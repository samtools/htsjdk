package htsjdk;

import htsjdk.io.PipeSafeFileSystemProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Base class for all Java tests in HTSJDK.
 */
public class HtsjdkTest{

    @Test
    public void testIO() throws IOException {
        InputStream inputStream = Files.newInputStream(Paths.get("/dev/stdin"));
        inputStream.available();
    }

}
