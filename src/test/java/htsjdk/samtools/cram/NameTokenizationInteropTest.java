package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.nametokenisation.NameTokenisationDecode;
import htsjdk.samtools.cram.compression.nametokenisation.NameTokenisationEncode;
import org.apache.commons.compress.utils.IOUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

//TODO: this is dumb because we're running the same decode-only test twice, once with useArith true and once with
// it false, which is ignored the the decode-only test anyway; we should separate the data provides to eliminate this

// Test the roundtrip and decompression of name tokenization encoded data using the htslib cram interop stream
// data for the name tokenization codec.
public class NameTokenizationInteropTest extends HtsjdkTest {
    public static final String COMPRESSED_TOK_DIR = "tok3";

    // the htslib cram interop tests streams use this separator in the raw (uncompressed) streams to separate
    // the read names that are passed into/out of the name tokenization codec, but htsjdk uses '\0' because
    // the downstream htsjdk cram code assumes that value; so in the interop tests we need to replace the htslib
    // separator with the corresponding name tokenization separator used by htsjdk
    public static final byte HTSLIB_NAME_SEPARATOR = '\n';

    @DataProvider(name = "allNameTokInteropTests")
    public Object[][] getAllNameTokenizationInteropTests() throws IOException {
        // compressed path (htslib interop preCompressed file), raw (unCompressed) path, useArith (used for round tripping only)
        final List<Object[]> testCases = new ArrayList<>();
        for (final Path preCompressedInteropPath : getPreCompressedInteropNameTokTestPaths()) {
            for (boolean useArith: new boolean[]{true, false}) {
                Object[] objects = new Object[] {
                        preCompressedInteropPath,
                        unCompressedPathFromPreCompressedPath(preCompressedInteropPath),
                        useArith
                };
                testCases.add(objects);
            }
        }
        return testCases.toArray(new Object[][]{});
    }

    @Test (dataProvider = "allNameTokInteropTests",
            description = "Roundtrip the uncompressed path using htsjdk NameTokenization codec, compare the output with the original uncompressed path")
    public void testNameTokRoundTrip(
            final Path preCompressedInteropPath_unused,
            final Path unCompressedInteropPath,
            final boolean useArith) throws IOException {

        try (final InputStream unCompressedInteropStream = Files.newInputStream(unCompressedInteropPath)) {
            // convert the uncompressed data from htslib to the unCompressed format used to pass data in/out of the htsjdk name tok codec
            final ByteBuffer unCompressedInteropBytes = convertHTSLIBToHTSJDKStreamFormat(
                    ByteBuffer.wrap(IOUtils.toByteArray(unCompressedInteropStream))
            );

            // Use htsjdk to compress the uncompressed data with the provided useArith flag
            final NameTokenisationEncode nameEncoder = new NameTokenisationEncode();
            final ByteBuffer compressedHtsjdkBytes = nameEncoder.compress(unCompressedInteropBytes, useArith);

            // Now use htsjdk to uncompress the data we just compressed
            final NameTokenisationDecode nameDecoder = new NameTokenisationDecode();
            final ByteBuffer unCompressedHtsjdkBytes = ByteBuffer.wrap(nameDecoder.uncompress(compressedHtsjdkBytes).getBytes());

            // compare to the original (ByteBuffers have to have identical positions in order to be equal (!),
            // so rewind both buffers before comparing)
            unCompressedInteropBytes.rewind();
            unCompressedHtsjdkBytes.rewind();
            Assert.assertEquals(unCompressedHtsjdkBytes, unCompressedInteropBytes);
        }
    }

    @Test (dataProvider = "allNameTokInteropTests",
            description = "Uncompress the htslib compressed file using htsjdk and compare it with the uncompressed file.")
    public void testNameTokUnCompress(
            final Path preCompressedInteropPath,
            final Path unCompressedInteropPath,
            final boolean unused_useArith) throws IOException {
        try (final InputStream preCompressedInteropStream = Files.newInputStream(preCompressedInteropPath);
            final InputStream unCompressedInteropStream = Files.newInputStream(unCompressedInteropPath)) {
            final ByteBuffer preCompressedInteropBytes = ByteBuffer.wrap(IOUtils.toByteArray(preCompressedInteropStream));
            // convert the uncompressed data from htslib to the unCompressed format used to pass data in/out of the htsjdk name tok codec
            final ByteBuffer uncompressedInteropBytes = convertHTSLIBToHTSJDKStreamFormat(
                    ByteBuffer.wrap(IOUtils.toByteArray(unCompressedInteropStream))
            );

            // Use htsjdk to uncompress the precompressed file from htscodecs repo
            final NameTokenisationDecode nameTokenisationDecode = new NameTokenisationDecode();
            final ByteBuffer uncompressedHtsjdkBytes = ByteBuffer.wrap(nameTokenisationDecode.uncompress(preCompressedInteropBytes).getBytes());

            for (int i = 0; i < uncompressedHtsjdkBytes.limit(); i++) {
                if (uncompressedHtsjdkBytes.get(i) != uncompressedInteropBytes.get(i)) {
//                    System.out.println("Mismatch at index: " + i);
//                    System.out.println("htsjdk: " + (char)uncompressedHtsjdkBytes.get(i));
//                    System.out.println("interop: " + (char)uncompressedInteropBytes.get(i));
                    int j = 37;
                }
            }
            // Compare the htsjdk uncompressed bytes with the original input file from htscodecs repo
            Assert.assertEquals(uncompressedHtsjdkBytes, uncompressedInteropBytes);
        }
    }

    // return a list of all NameTokenization encoded test data files in the htscodecs/tests/names/tok3 directory
    private List<Path> getPreCompressedInteropNameTokTestPaths() throws IOException {
        final List<Path> paths = new ArrayList<>();
        Files.newDirectoryStream(
                CRAMInteropTestUtils.getCRAM31_Htslib_InteropTestDataLocation().resolve("names/" + COMPRESSED_TOK_DIR),
                        path -> Files.isRegularFile(path))
                .forEach(path -> paths.add(path));
        return paths;
    }

    // Given a compressed test file path, return the corresponding uncompressed file path
    private static Path unCompressedPathFromPreCompressedPath(final Path preCompressedInteropPath) {
        final String uncompressedFileName = getUnCompressedFileNameFromCompressedFileName(preCompressedInteropPath.getFileName().toString());
        // Example compressedInteropPath: ../names/tok3/01.names.1 => unCompressedFilePath: ../names/01.names
        return preCompressedInteropPath.getParent().getParent().resolve(uncompressedFileName);
    }

    // Return the name of the unCompressed interop test filethat corresponds to a preCompressed interop file
    private static String getUnCompressedFileNameFromCompressedFileName(final String preCompressedFileName) {
        int lastDotIndex = preCompressedFileName.lastIndexOf(".");
        if (lastDotIndex >= 0) {
            return preCompressedFileName.substring(0, lastDotIndex);
        } else {
            throw new CRAMException("The format of the compressed File Name is not as expected. " +
                    "The name of the compressed file should contain a period followed by a number that" +
                    "indicates type of compression. Actual compressed file name = "+ preCompressedFileName);
        }
    }

    // translate an htslib interop stream into the stream format used by the htsjdk name tokenization codec
    private ByteBuffer convertHTSLIBToHTSJDKStreamFormat(final ByteBuffer htslibBuffer) {
        // don't include the terminating delimiter that htslib interop streams use because htsjdk doesn't use a terminator
        final ByteBuffer translatedBuffer = ByteBuffer.allocate(htslibBuffer.limit() - 1);
        for (int i = 0; i < htslibBuffer.limit() - 1; i++) {
            if (htslibBuffer.get(i) == HTSLIB_NAME_SEPARATOR) {
                translatedBuffer.put(i, NameTokenisationDecode.NAME_SEPARATOR);
            } else {
                translatedBuffer.put(i, htslibBuffer.get(i));
            }
        }
        return translatedBuffer;
    }

}