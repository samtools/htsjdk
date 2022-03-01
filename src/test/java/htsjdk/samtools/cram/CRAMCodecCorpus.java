package htsjdk.samtools.cram;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * HTSCodecs test data is kept in a separate repository, currently at https://github.com/jkbonfield/htscodecs-corpus
 * so it can be shared across htslib/samtools/htsjdk.
 */
public class CRAMCodecCorpus {
    public static final String HTSCODECS_TEST_DATA_ENV = "HTSCODECS_TEST_DATA";

    /**
     * @return true if htscodecs test data is available, otherwise false
     */
    public static boolean isHtsCodecsTestDataAvailable() {
        final Path testDataPath = getHTSCodecsTestDataLocation();
        return Files.exists(testDataPath);
    }

    /**
     * @return throws a RuntimeException if the htscodecs test data repo is not available locally
     */
    public static void assertHTSCodecsTestDataAvailable() {
        if (!isHtsCodecsTestDataAvailable()) {
            throw new RuntimeException(
                    String.format(
                            "No HTS codecs test data found." +
                                    " The %s environment variable must be set to the location of the local hts codecs test data.",
                            HTSCODECS_TEST_DATA_ENV));
        }
    }

    /**
     * @return the name and location of the local hts codecs test data as specified by the environment
     * variable HTSCODECS_TEST_DATA, or the default value of "../htscodecs-corpus" if the environment
     * variable is not set
     */
    public static Path getHTSCodecsTestDataLocation() {
        final String htsCodecsTestLocation = System.getenv(HTSCODECS_TEST_DATA_ENV);
        return htsCodecsTestLocation == null ? Paths.get("../htscodecs/tests") : Paths.get(htsCodecsTestLocation);
    }

}