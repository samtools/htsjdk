package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.RANSExternalCompressor;
import htsjdk.samtools.cram.compression.TokenizedNameCompressor;
import htsjdk.samtools.cram.compression.rans.RANS;
import org.apache.commons.compress.utils.IOUtils;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * HTSCodecs test data is kept in a separate repository, currently at https://github.com/jkbonfield/htscodecs-corpus
 * so it can be shared across htslib/samtools/htsjdk.
 */
public class CRAMCodecCorpusTest extends HtsjdkTest {
    @Test
    public void testGetHTSCodecsCorpus() {
        if (!CRAMCodecCorpus.isHtsCodecsTestDataAvailable()) {
            throw new SkipException(String.format(
                    "No HTS codecs test data found." +
                            " The %s environment variable must be set to the location of the local hts codecs test data.",
                    CRAMCodecCorpus.HTSCODECS_TEST_DATA_ENV));
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////
    // RANS tests
    /////////////////////////////////////////////////////////////////////////////////////////////////

    //TODO: the TestDataProviders tests fail if the hts codecs corpus isn't available because

    // q4: 35(1), 45(187), 51(177), 69(3730)
    @DataProvider(name = "rans4x8")
    public Object[][] getRANS4x8TestData() throws IOException {
        final RANS rans = new RANS(); // cache/reuse this for each test case to eliminate excessive garbage collection
        final List<Object[]> testCases = new ArrayList<>();
        getHtsCodecRANSTestFiles().stream()
                .forEach(p ->
                    {
                        testCases.add(new Object[] {p, new RANSExternalCompressor(RANS.ORDER.ZERO, rans) });
                        testCases.add(new Object[] {p, new RANSExternalCompressor(RANS.ORDER.ONE, rans) });
                    });
        return testCases.toArray(new Object[][]{});
    }

    @Test(dataProvider = "rans4x8", dependsOnMethods = "testGetHTSCodecsCorpus")
    public void testRANS4x8RoundTrip(
            final Path inputTestDataPath,
            final RANSExternalCompressor ransExternalCompressor) throws IOException {
        if (!CRAMCodecCorpus.isHtsCodecsTestDataAvailable()) {
            throw new SkipException("htscodecs test data is not available locally");
        }

        try (final InputStream is = Files.newInputStream(inputTestDataPath)) {
            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through RANS and compare the
            // results
            byte[] uncompressedBytes = filterEmbeddedNewlines(IOUtils.toByteArray(is));
            final byte[] compressedBytes = ransExternalCompressor.compress(uncompressedBytes);
            System.out.println(String.format("%s RANS4x8 Order (%s) Uncompressed: (%,d) Compressed: (%,d)",
                    inputTestDataPath.getFileName(),
                    ransExternalCompressor.getOrder(),
                    uncompressedBytes.length,
                    compressedBytes.length));
            Assert.assertEquals(ransExternalCompressor.uncompress(compressedBytes), uncompressedBytes);
        }
    }

    @Test(dataProvider = "rans4x8", dependsOnMethods = "testGetHTSCodecsCorpus")
    public void testRANS4x8PreCompressed(
            final Path inputTestDataPath,
            final RANSExternalCompressor ransExternalCompressor) throws IOException {
        if (!CRAMCodecCorpus.isHtsCodecsTestDataAvailable()) {
            throw new SkipException("htscodecs test data is not available locally");
        }

        final Path preCompressedDataPath = getCompressedRANSPath(inputTestDataPath, ransExternalCompressor.getOrder());
        try (final InputStream is = Files.newInputStream(inputTestDataPath);
             final InputStream preCompressedAggregateStream = Files.newInputStream(preCompressedDataPath);
             final ByteArrayOutputStream decompressedStream = new ByteArrayOutputStream()) {

            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through RANS and compare the
            // results
            byte[] preCompressedBytes = filterEmbeddedNewlines(IOUtils.toByteArray(is));

            // now compare the decompressed stream with the (pre) compressed stream in the repo
            InputStream compressedComponentStream;
            while ((compressedComponentStream = getNextEmbeddedRANSStream(
                    preCompressedAggregateStream,
                    ransExternalCompressor.getOrder())) != null) {
                final byte[] decompressedComponentBytes =
                        ransExternalCompressor.uncompress(IOUtils.toByteArray(compressedComponentStream));
                decompressedStream.write(decompressedComponentBytes);
            }
            Assert.assertEquals(preCompressedBytes, decompressedStream.toByteArray());
        }
    }

    // return a list of all RANS test data files in the htscodecs test directory
    private List<Path> getHtsCodecRANSTestFiles() throws IOException {
        CRAMCodecCorpus.assertHTSCodecsTestDataAvailable();
        final List<Path> paths = new ArrayList<>();
        Files.newDirectoryStream(
                CRAMCodecCorpus.getHTSCodecsTestDataLocation().resolve("dat"),
                path -> path.getFileName().startsWith("q4") ||
                        path.getFileName().startsWith("q40+dir") ||
                        path.getFileName().startsWith("q8") ||
                        path.getFileName().startsWith("qvar"))
                .forEach(path -> paths.add(path));
        return paths;
    }

    // the input files have embedded newlines that the test remove before round-tripping...
    final byte[] filterEmbeddedNewlines(final byte[] rawBytes) throws IOException {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (final byte b : rawBytes) {
                if (b != '\n') {
                    baos.write(b);
                }
            }
            return baos.toByteArray();
        }
    }

    // the read names input files have embedded newlines that must be restore before comparing round-tripped data...
    final byte[] restoreEmbeddedNewlines(final byte[] rawBytes) throws IOException {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (final byte b : rawBytes) {
                if (b != '\000') {
                    baos.write('\n');
                }
            }
            return baos.toByteArray();
        }
    }

    // Given a test file name, map it to the corresponding rans compressed path
    final Path getCompressedRANSPath(final Path inputTestDataPath, RANS.ORDER order) {
        final String compressedFileName = String.format("r4x8/%s.%s", inputTestDataPath.getFileName(), order.ordinal());
        return inputTestDataPath.getParent().resolve(compressedFileName);
    }

    // Return the next embedded RANS stream, or null if no more streams are available.
    //
    // These streams were created with the HTSCodecs javascript code. The input was divided into 1MB chunks,
    // and the result of RANS compressing these are concatenated together, each with an embedded metadata
    // header describing the length of the embedded stream.
    private final InputStream getNextEmbeddedRANSStream(
            final InputStream aggregateRANSStream,
            RANS.ORDER expectedOrder) throws IOException {
        final byte[] orderByte = new byte[1];
        final int orderLen = aggregateRANSStream.read(orderByte, 0, 1);
        if (orderLen == -1) {
            // end of stream
            return null;
        }
        if (orderLen != 1) {
            throw new RuntimeException("Missing order in stream");
        }
        if (orderByte[0] != expectedOrder.ordinal()) {
            throw new RuntimeException("Actual RANS order value doesn't match expected value");
        }
        final byte[] embeddedStreamLengthBytes = new byte[4];
        aggregateRANSStream.read(embeddedStreamLengthBytes, 0, 4);
        final ByteBuffer tempByteBuffer = ByteBuffer.wrap(embeddedStreamLengthBytes);
        tempByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        int embeddedStreamLength = tempByteBuffer.getInt();
        final byte[] embeddedStreamBytes = new byte[embeddedStreamLength];
        final int numBytesRead = aggregateRANSStream.read(embeddedStreamBytes, 0, embeddedStreamLength);
        if (numBytesRead != embeddedStreamLength) {
            throw new RuntimeException("Invalid embedded RANS stream");
        }
        return new ByteArrayInputStream(embeddedStreamBytes);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////
    // Read Name Tokenizer tests
    /////////////////////////////////////////////////////////////////////////////////////////////////

    // return a list of all "names" test data files in the htscodecs test directory
    private List<Path> getHtsCodecNameTestFiles() throws IOException {
        CRAMCodecCorpus.assertHTSCodecsTestDataAvailable();
        final List<Path> paths = new ArrayList<>();
        Files.newDirectoryStream(
                CRAMCodecCorpus.getHTSCodecsTestDataLocation().resolve("names"),
                path -> !path.getFileName().startsWith("tok3")
        ).forEach(path -> paths.add(path));
        return paths;
    }

    // Given a test file name, map it to the corresponding "name" compressed path
    final Path getCompressedNameTokenPath(final Path inputTestDataPath, int level) {
        final String compressedFileName = String.format("tok3/%s.%s", inputTestDataPath.getFileName(), level);
        return inputTestDataPath.getParent().resolve(compressedFileName);
    }

    // Return the next embedded RANS stream, or null if no more streams are available.
    //
    // These streams were created with the HTSCodecs javascript code. The input was divided into 1MB chunks,
    // and the result of RANS compressing these are concatenated together, each with an embedded metadata
    // header describing the length of the embedded stream.
    private final InputStream getNextEmbeddedReadStream(
            final InputStream aggregateReadStream) throws IOException {
        final byte[] embeddedStreamLengthBytes = new byte[4];
        int numRead = aggregateReadStream.read(embeddedStreamLengthBytes, 0, 4);
        if (numRead == -1) {
            // end of stream
            return null;
        }
        final ByteBuffer tempByteBuffer = ByteBuffer.wrap(embeddedStreamLengthBytes);
        tempByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        int embeddedStreamLength = tempByteBuffer.getInt();
        final byte[] embeddedStreamBytes = new byte[embeddedStreamLength];
        final int numBytesRead = aggregateReadStream.read(embeddedStreamBytes, 0, embeddedStreamLength);
        if (numBytesRead != embeddedStreamLength) {
            throw new RuntimeException("Invalid embedded read stream");
        }
        return new ByteArrayInputStream(embeddedStreamBytes);
    }

    @DataProvider(name = "readNameData")
    public Object[][] getNameCodecTestData() throws IOException {
        final List<Object[]> testCases = new ArrayList<>();
        getHtsCodecNameTestFiles().stream().forEach(p ->
        {
            // test file, compression level
            testCases.add(new Object[] { p, 1 });
        });
        //testCases.add(new Object[] { getHTSCodecsTestDataLocation().resolve("names/01.names"), 1 });
        return testCases.toArray(new Object[][]{});
    }

//    @Test(dataProvider = "readNameData", dependsOnMethods = "testGetHTSCodecsCorpus")
//    public void testPrecompressedNameTokens(
//            final Path inputTestDataPath,
//            final Integer level) throws IOException {
//        if (!CRAMCodecCorpus.isHtsCodecsTestDataAvailable()) {
//            throw new SkipException("htscodecs test data is not available locally");
//        }
//
//        final Path preCompressedPath = getCompressedNameTokenPath(inputTestDataPath, level);
//        final TokenizedNameCompressor tokenizedNameCompressor = new TokenizedNameCompressor(level);
//        try (final InputStream is = Files.newInputStream(inputTestDataPath);
//             final InputStream cs = Files.newInputStream(preCompressedPath);
//             final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
//                InputStream compressedComponentStream;
//                while ((compressedComponentStream = getNextEmbeddedReadStream(cs)) != null) {
//                    final byte[] compressedComponentBytes = IOUtils.toByteArray(compressedComponentStream);
//                    System.out.println(String.format("Found embedded read stream length %d", compressedComponentBytes.length));
//                    final byte[] decompressedComponentBytes = tokenizedNameCompressor.uncompress(compressedComponentBytes);
//                    os.write(decompressedComponentBytes);
//                }
//
//                byte[] postProcessedBytes = restoreEmbeddedNewlines(os.toByteArray());
//                Assert.assertEquals(postProcessedBytes, IOUtils.toByteArray(is));
//        }
//    }

}
