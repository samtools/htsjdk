package htsjdk.samtools.cram;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import htsjdk.utils.SamtoolsTestUtils;

/**
 * Interop test data is kept in a separate repository, currently at https://github.com/samtools/htscodecs
 * so it can be shared across htslib/samtools/htsjdk.
 */
public class CRAMInteropTestUtils {
    public static final String INTEROP_TEST_FILES_PATH = SamtoolsTestUtils.getCRAMInteropData();

    /**
     * @return true if interop test data is available, otherwise false
     */
    public static boolean isInteropTestDataAvailable() {
        final Path testDataPath = getInteropTestDataLocation();
        return Files.exists(testDataPath);
    }

    /**
     * @return the name and location of the local interop test data as specified by the
     * variable INTEROP_TEST_FILES_PATH
     */
    public static Path getInteropTestDataLocation() {
        return Paths.get(INTEROP_TEST_FILES_PATH);
    }

}