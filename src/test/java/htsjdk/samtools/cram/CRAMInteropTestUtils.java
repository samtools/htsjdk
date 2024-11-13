package htsjdk.samtools.cram;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Interop test data originates in a separate repository, currently at https://github.com/samtools/htslib, in htscodecs,
 * but we keep a copy in htsjdk so we can use it for round trip tests in CI without needing to clone a second repo.
 */
public class CRAMInteropTestUtils {
    public static final String INTEROP_TEST_FILES_PATH = "src/test/resources/htsjdk/samtools/cram/htslib_interop/";

    /**
     * @return the name and location of the local interop test data as specified by the
     * variable INTEROP_TEST_FILES_PATH
     */
    public static Path getCRAM31_Htslib_InteropTestDataLocation() {
        return Paths.get(INTEROP_TEST_FILES_PATH + "cram31/");
    }

    // the input files have embedded newlines that the test remove before round-tripping...
    protected static final byte[] filterEmbeddedNewlines(final byte[] rawBytes) throws IOException {
        // 1. filters new lines if any.
        // 2. "q40+dir" file has an extra column delimited by tab. This column provides READ1 vs READ2 flag.
        //     This file is also new-line separated. The extra column, '\t' and '\n' are filtered.
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            int skip = 0;
            for (final byte b : rawBytes) {
                if (b == '\t'){
                    skip = 1;
                }
                if (b == '\n') {
                    skip = 0;
                }
                if (skip == 0 && b !='\n') {
                    baos.write(b);
                }
            }
            return baos.toByteArray();
        }
    }

    // return a list of all encoded test data files in the htscodecs/tests/dat/<compressedDir> directory
    protected static List<Path> getInteropCompressedFilePaths(final String compressedDir) throws IOException {
        final List<Path> paths = new ArrayList<>();
        Files.newDirectoryStream(
                        CRAMInteropTestUtils.getCRAM31_Htslib_InteropTestDataLocation().resolve("dat/"+compressedDir),
                        path -> Files.isRegularFile(path))
                .forEach(path -> paths.add(path));
        return paths;
    }

    // Given a compressed test file path, return the corresponding uncompressed file path
    protected static final Path getUnCompressedFilePath(final Path compressedInteropPath) {
        final String uncompressedFileName = getUncompressedFileName(compressedInteropPath.getFileName().toString());
        // Example compressedInteropPath: ../dat/r4x8/q4.1 => unCompressedFilePath: ../dat/q4
        return compressedInteropPath.getParent().getParent().resolve(uncompressedFileName);
    }

    private static final String getUncompressedFileName(final String compressedFileName) {
        // Returns original filename from compressed file name
        final int lastDotIndex = compressedFileName.lastIndexOf(".");
        if (lastDotIndex >= 0) {
            return compressedFileName.substring(0, lastDotIndex);
        } else {
            throw new CRAMException("The format of the compressed File Name is not as expected. " +
                    "The name of the compressed file should contain a period followed by a number that" +
                    "indicates the order of compression. Actual compressed file name = "+ compressedFileName);
        }
    }

    // return a list of all raw test files in the htscodecs/tests/dat directory
    protected static final List<Path> getInteropRawTestFiles() throws IOException {
        final List<Path> paths = new ArrayList<>();
        Files.newDirectoryStream(
                        CRAMInteropTestUtils.getCRAM31_Htslib_InteropTestDataLocation().resolve("dat"),
                        path -> (Files.isRegularFile(path)) && !Files.isHidden(path))
                .forEach(path -> paths.add(path));
        return paths;
    }

}