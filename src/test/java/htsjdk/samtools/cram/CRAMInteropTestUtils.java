package htsjdk.samtools.cram;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Interop test data originates in a separate repository, currently at hts-specs/test, so we keep a copy in
 * htsjdk so we can use it for round trip tests in CI without needing to clone a second repo.
 */
public class CRAMInteropTestUtils {
    public static final String INTEROP_TEST_FILES_PATH = "src/test/resources/htsjdk/hts-specs/test/cram/codecs";
    public static final String GZIP_PATH = "gzip/";
    public static final String GZIP_SUFFIX = ".gz";

    /**
     * @return the name and location of the local interop test data as specified by the
     * variable INTEROP_TEST_FILES_PATH
     */
    public static Path getCRAMInteropTestDataLocation() {
        return Paths.get(INTEROP_TEST_FILES_PATH);
    }

    // return a list of all encoded test data files in the interop/<compressedDir> directory
    protected static List<Path> getCRAMInteropCompressedPaths(final String compressedDir) throws IOException {
        final List<Path> paths = new ArrayList<>();
        Files.newDirectoryStream(
                        CRAMInteropTestUtils.getCRAMInteropTestDataLocation().resolve(compressedDir),
                        path -> Files.isRegularFile(path))
                .forEach(path -> paths.add(path));
        return paths;
    }

    // Given a compressed test file path, return the corresponding uncompressed file path
    protected static final Path getUnCompressedPathForCompressedPath(final Path compressedInteropPath) {
        final String uncompressedFileName = getUncompressedFileName(compressedInteropPath);
        return compressedInteropPath.getParent().getParent()
                .resolve(CRAMInteropTestUtils.GZIP_PATH + uncompressedFileName);
    }

    private static final String getUncompressedFileName(final Path compressedPath) {
        // Returns original filename from compressed file name
        final String fileName = compressedPath.getFileName().toString();
        final int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex >= 0) {
            final String compressedFileName = fileName.substring(0, lastDotIndex);
            return compressedFileName + CRAMInteropTestUtils.GZIP_SUFFIX;
        } else {
            throw new CRAMException("The name of the compressed file should contain a period followed by a number that" +
                    "indicates the order of compression. Actual compressed file name = "+ fileName);
        }
    }

    // return a list of all raw test files  in the hts-specs interop test directory (these files are raw in
    // the sense that they are not compressed by any CRAM codec, although they ARE gzip compressed for inclusion
    // in the repo)
    protected static final List<Path> getRawCRAMInteropTestFiles() throws IOException {
        final List<Path> paths = new ArrayList<>();
        Files.newDirectoryStream(CRAMInteropTestUtils.getCRAMInteropTestDataLocation().resolve(GZIP_PATH))
                .forEach(path -> paths.add(path));
        return paths;
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

}