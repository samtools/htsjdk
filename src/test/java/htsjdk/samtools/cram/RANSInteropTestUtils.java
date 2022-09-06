package htsjdk.samtools.cram;

import org.testng.SkipException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Interop test data is kept in a separate repository, currently at https://github.com/samtools/htscodecs
 * so it can be shared across htslib/samtools/htsjdk.
 */
public class RANSInteropTestUtils {
    public static final String INTEROP_TEST_FILES_PATH = "src/test/resources/htsjdk/samtools/cram/InteropTest/";

    /**
     * @return true if interop test data is available, otherwise false
     */
    public static boolean isInteropTestDataAvailable() {
        final Path testDataPath = getInteropTestDataLocation();
        return Files.exists(testDataPath);
    }

    /**
     * @return throws a SkipException if the interop test data is not available locally
     */
    public static void assertHTSCodecsTestDataAvailable() {
        if (!isInteropTestDataAvailable()) {
            throw new SkipException(
                    String.format(
                            "No RANS Interop test data found at location: %s",
                            INTEROP_TEST_FILES_PATH));
        }
    }

    /**
     * @return the name and location of the local interop test data as specified by the
     * variable INTEROP_TEST_FILES_PATH
     */
    public static Path getInteropTestDataLocation() {
        return Paths.get(INTEROP_TEST_FILES_PATH);
    }

}