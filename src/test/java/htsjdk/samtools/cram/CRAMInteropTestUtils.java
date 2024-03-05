package htsjdk.samtools.cram;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CRAMInteropTestUtils {

    /**
     * Return the location of the locally checked-in test CRAM 3.1 test interop data dir (the files
     * there are copied from the originals in htscodecs, and should be updated periodically).
     */
    public static Path getInteropTestDataLocation() {
        return Paths.get("src/test/resources/htsjdk/samtools/cram/3.1_streams/");
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
                        CRAMInteropTestUtils.getInteropTestDataLocation().resolve("dat/"+compressedDir),
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

    protected static final int getParamsFormatFlags(final Path compressedInteropPath){
        // Returns formatFlags from compressed file path
        final String compressedFileName = compressedInteropPath.getFileName().toString();
        final int lastDotIndex = compressedFileName.lastIndexOf(".");
        if (lastDotIndex >= 0 && lastDotIndex < compressedFileName.length() - 1) {
            return Integer.parseInt(compressedFileName.substring(lastDotIndex + 1));
        } else {
            throw new CRAMException("The format of the compressed File Name is not as expected. " +
                    "The name of the compressed file should contain a period followed by a number that" +
                    "indicates the order of compression. Actual compressed file name = "+ compressedFileName);
        }
    }

}