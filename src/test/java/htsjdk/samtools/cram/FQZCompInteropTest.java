package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMUtils;
import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.fqzcomp.FQZCompDecode;
import htsjdk.samtools.cram.compression.fqzcomp.FQZCompEncode;
import htsjdk.samtools.cram.compression.fqzcomp.FQZUtils;
import org.apache.commons.compress.utils.IOUtils;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPInputStream;

public class FQZCompInteropTest extends HtsjdkTest {

    public static final String COMPRESSED_FQZCOMP_DIR = "fqzcomp";

    // uses the available compressed interop test files
    @DataProvider(name = "decodeOnlyTestCases")
    public Object[][] getDecodeOnlyTestCases() throws IOException {

        // params:
        // compressed testfile path, uncompressed testfile path,
        // FQZComp decoder
        final List<Object[]> testCases = new ArrayList<>();
        for (Path path : CRAMInteropTestUtils.getCRAMInteropCompressedPaths(COMPRESSED_FQZCOMP_DIR)) {
            Object[] objects = new Object[]{
                    path,
                    CRAMInteropTestUtils.getUnCompressedPathForCompressedPath(path),
                    new FQZCompDecode()
            };
            testCases.add(objects);
        }
        return testCases.toArray(new Object[][]{});
    }

    @Test (
            dataProvider = "decodeOnlyTestCases",
            description = "Uncompress the existing compressed file using htsjdk FQZComp and compare it with the original file.")
    public void testDecodeOnly(
            final Path compressedFilePath,
            final Path uncompressedInteropPath,
            final FQZCompDecode fqzcompDecode) throws IOException {
        try (final InputStream uncompressedInteropStream =
                     new GZIPInputStream(Files.newInputStream(uncompressedInteropPath));
             final InputStream preCompressedInteropStream = Files.newInputStream(compressedFilePath)
        ) {
            // preprocess the uncompressed data (to match what the htscodecs-library test harness does)
            // by filtering out the embedded newlines, and then round trip through FQZComp codec
            // and compare the results
            final ByteBuffer uncompressedInteropBytes = CompressionUtils.wrap(CRAMInteropTestUtils.filterEmbeddedNewlines(IOUtils.toByteArray(uncompressedInteropStream)));
            final ByteBuffer preCompressedInteropBytes = CompressionUtils.wrap(IOUtils.toByteArray(preCompressedInteropStream));

            // Use htsjdk to uncompress the precompressed file from htscodecs repo
            final ByteBuffer uncompressedHtsjdkBytes = fqzcompDecode.uncompress(preCompressedInteropBytes);

            // for some reason, the raw, uncompressed interop test file streams appear to be fastq rather than phred (!),
            // so before we can compare the results to the raw stream, we need convert them so they match the native
            // format returned by the codec
            SAMUtils.fastqToPhred(uncompressedInteropBytes.array());

            // Compare the htsjdk uncompressed bytes with the original input file from htscodecs repo
            Assert.assertEquals(uncompressedHtsjdkBytes, uncompressedInteropBytes);
        } catch (final NoSuchFileException ex){
            throw new SkipException("Skipping testDecodeOnly as either input file " +
                    "or precompressed file is missing.", ex);
        }
    }

    // --- Round-trip encode/decode tests ---

    @DataProvider(name = "roundTripTestCases")
    public Object[][] getRoundTripTestCases() {
        final Random random = new Random(42);

        // Simulated Illumina-style quality scores (binned to ~4 values)
        final byte[] illuminaQuals = new byte[1000];
        final byte[] bins = {2, 11, 25, 37};
        for (int i = 0; i < illuminaQuals.length; i++) {
            illuminaQuals[i] = bins[random.nextInt(bins.length)];
        }

        // Uniform random quality scores
        final byte[] randomQuals = new byte[500];
        for (int i = 0; i < randomQuals.length; i++) {
            randomQuals[i] = (byte) random.nextInt(42);
        }

        // All-same quality (common for unmapped reads)
        final byte[] allSame = new byte[300];
        java.util.Arrays.fill(allSame, (byte) 30);

        return new Object[][]{
                // description, quality data, record lengths
                {"single record",           new byte[]{10, 20, 30, 20, 10},        new int[]{5}},
                {"two equal records",        new byte[]{10, 20, 30, 10, 20, 30},    new int[]{3, 3}},
                {"variable length records",  new byte[]{1, 2, 3, 4, 5, 6, 7},       new int[]{3, 4}},
                {"single byte records",      new byte[]{5, 10, 15},                 new int[]{1, 1, 1}},
                {"illumina-style binned",    illuminaQuals,                          makeEqualLengths(10, 100)},
                {"random qualities",         randomQuals,                            makeEqualLengths(5, 100)},
                {"all same quality",         allSame,                                makeEqualLengths(3, 100)},
        };
    }

    @Test(dataProvider = "roundTripTestCases",
            description = "Round-trip encode/decode with FQZComp and verify data fidelity")
    public void testFQZCompRoundTrip(
            final String description,
            final byte[] qualityData,
            final int[] recordLengths) {
        final ByteBuffer input = CompressionUtils.wrap(qualityData);
        final ByteBuffer compressed = new FQZCompEncode().compress(input, recordLengths);
        final ByteBuffer decompressed = FQZCompDecode.uncompress(compressed);

        input.rewind();
        Assert.assertEquals(decompressed, input,
                "FQZComp round-trip failed for: " + description);
    }

    @Test(description = "Verify FQZComp produces smaller output than input for compressible data")
    public void testFQZCompCompresses() {
        // Illumina-style binned data should compress well
        final byte[] quals = new byte[10000];
        final byte[] bins = {2, 11, 25, 37};
        final Random random = new Random(123);
        for (int i = 0; i < quals.length; i++) {
            quals[i] = bins[random.nextInt(bins.length)];
        }

        final FQZCompEncode encoder = new FQZCompEncode();
        final ByteBuffer compressed = encoder.compress(CompressionUtils.wrap(quals), makeEqualLengths(100, 100));

        Assert.assertTrue(compressed.remaining() < quals.length,
                String.format("FQZComp should compress binned quality data: %d >= %d",
                        compressed.remaining(), quals.length));
    }

    /**
     * Test with noodles-style test vectors. Ported from noodles-cram fqzcomp/encode.rs test_encode.
     */
    @Test(description = "Round-trip with noodles test vectors")
    public void testNoodlesTestVectors() {
        final byte[][] records = {
                {0, 0, 0, 1, 1, 2, 1, 1, 0, 0},
                {0, 1, 2, 3, 3, 3, 3, 3, 3, 3},
                {2, 1, 1, 0, 0}
        };
        final int[] lengths = {10, 10, 5};

        // Concatenate records
        int totalLen = 0;
        for (byte[] r : records) totalLen += r.length;
        final byte[] allQuals = new byte[totalLen];
        int offset = 0;
        for (byte[] r : records) {
            System.arraycopy(r, 0, allQuals, offset, r.length);
            offset += r.length;
        }

        final FQZCompEncode encoder = new FQZCompEncode();
        final ByteBuffer compressed = encoder.compress(CompressionUtils.wrap(allQuals), lengths);
        final ByteBuffer decompressed = FQZCompDecode.uncompress(compressed);

        Assert.assertEquals(decompressed, CompressionUtils.wrap(allQuals));
    }

    /**
     * Verify that storeArray/readArray round-trips correctly for various table patterns.
     * storeArray is the encoder's table serialization; readArray is the decoder's counterpart.
     */
    @Test(description = "storeArray/readArray round-trip for FQZComp table serialization")
    public void testStoreArrayReadArrayRoundTrip() {
        // Identity table: ptab[i] = i for i in 0..127
        verifyStoreArrayRoundTrip(buildIdentityTable(128), 128);

        // Constant table: all zeros
        verifyStoreArrayRoundTrip(new int[256], 256);

        // Position table: min((1<<7)-1, i >> 1) for 1024 entries (typical ptab)
        final int[] ptab = new int[1024];
        for (int i = 0; i < 1024; i++) {
            ptab[i] = Math.min(127, i >> 1);
        }
        verifyStoreArrayRoundTrip(ptab, 1024);

        // Small table with repeated values: 0,0,0,1,1,1,2,2,2
        final int[] small = {0, 0, 0, 1, 1, 1, 2, 2, 2};
        verifyStoreArrayRoundTrip(small, small.length);
    }

    private static void verifyStoreArrayRoundTrip(final int[] original, final int size) {
        final ByteBuffer buf = CompressionUtils.allocateByteBuffer(4096);
        FQZCompEncode.storeArray(buf, original, size);
        buf.flip();

        final int[] decoded = new int[1024];
        FQZUtils.readArray(buf, decoded, size);

        for (int i = 0; i < size; i++) {
            Assert.assertEquals(decoded[i], original[i],
                    String.format("storeArray/readArray mismatch at index %d for table of size %d", i, size));
        }
    }

    private static int[] buildIdentityTable(final int size) {
        final int[] table = new int[size];
        for (int i = 0; i < size; i++) {
            table[i] = i;
        }
        return table;
    }

    private static int[] makeEqualLengths(final int numRecords, final int recordLength) {
        final int[] lengths = new int[numRecords];
        java.util.Arrays.fill(lengths, recordLength);
        return lengths;
    }

}