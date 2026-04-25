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
import java.util.*;
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

    /**
     * Test FQZComp with NovaSeq-style binned quality data (4 distinct values).
     * This should trigger quality map optimization (nsym <= 8 && nsym*2 < maxSymbol).
     */
    @Test(description = "FQZComp round-trip with quality map for sparse symbols (NovaSeq-style)")
    public void testFQZCompWithQualityMap() {
        final byte[] quals = new byte[5000];
        final byte[] novaseqBins = {2, 12, 23, 37}; // 4 distinct values, max=37
        final Random random = new Random(42);
        for (int i = 0; i < quals.length; i++) {
            quals[i] = novaseqBins[random.nextInt(novaseqBins.length)];
        }

        final FQZCompEncode encoder = new FQZCompEncode();
        final ByteBuffer compressed = encoder.compress(CompressionUtils.wrap(quals), makeEqualLengths(50, 100));
        final ByteBuffer decompressed = FQZCompDecode.uncompress(compressed);
        Assert.assertEquals(decompressed, CompressionUtils.wrap(quals));
    }

    /**
     * Test FQZComp with duplicate quality records (should trigger dedup optimization).
     */
    @Test(description = "FQZComp round-trip with duplicate quality records")
    public void testFQZCompWithDuplicates() {
        // Create data where many consecutive records are identical (triggers dedup)
        final byte[] record = {10, 20, 30, 25, 15};
        final int numRecords = 100;
        final byte[] quals = new byte[record.length * numRecords];
        for (int i = 0; i < numRecords; i++) {
            System.arraycopy(record, 0, quals, i * record.length, record.length);
        }

        final int[] lengths = makeEqualLengths(numRecords, record.length);
        // Create BAM flags (no reverse for simplicity)
        final int[] flags = new int[numRecords];

        final FQZCompEncode encoder = new FQZCompEncode();
        final ByteBuffer compressed = encoder.compress(CompressionUtils.wrap(quals), lengths, flags);
        final ByteBuffer decompressed = FQZCompDecode.uncompress(compressed);
        Assert.assertEquals(decompressed, CompressionUtils.wrap(quals));
    }

    /**
     * Test FQZComp with reverse-complemented reads (should trigger quality reversal).
     */
    @Test(description = "FQZComp round-trip with reverse-complemented reads")
    public void testFQZCompWithReversal() {
        final byte[] quals = {10, 20, 30, 40, 50, 5, 15, 25, 35, 45};
        final int[] lengths = {5, 5};
        // Second read is reverse-complemented (BAM flag 0x10)
        final int[] flags = {0, 0x10};

        final FQZCompEncode encoder = new FQZCompEncode();
        final ByteBuffer compressed = encoder.compress(CompressionUtils.wrap(quals), lengths, flags);
        final ByteBuffer decompressed = FQZCompDecode.uncompress(compressed);
        Assert.assertEquals(decompressed, CompressionUtils.wrap(quals));
    }

    /**
     * Test FQZComp with small dataset (should trigger small-data parameter adjustments).
     */
    @Test(description = "FQZComp round-trip with small dataset (< 300KB)")
    public void testFQZCompSmallDataset() {
        // Under 300,000 bytes triggers special parameter adjustments
        final byte[] quals = new byte[1000];
        final Random random = new Random(77);
        for (int i = 0; i < quals.length; i++) {
            quals[i] = (byte) random.nextInt(42);
        }

        final FQZCompEncode encoder = new FQZCompEncode();
        final ByteBuffer compressed = encoder.compress(CompressionUtils.wrap(quals), makeEqualLengths(10, 100));
        final ByteBuffer decompressed = FQZCompDecode.uncompress(compressed);
        Assert.assertEquals(decompressed, CompressionUtils.wrap(quals));
    }

    // --- Round-trip with hts-specs quality data files ---

    @DataProvider(name = "htsSpecsRoundTripTestCases")
    public Object[][] getHtsSpecsRoundTripTestCases() {
        return new Object[][]{
                {"q4"},
                {"q8"},
                {"q40+dir"},
                {"qvar"},
        };
    }

    @Test(dataProvider = "htsSpecsRoundTripTestCases",
            description = "Round-trip hts-specs quality data through FQZComp encode/decode")
    public void testFQZCompHtsSpecsRoundTrip(final String datasetName) throws IOException {
        final Path rawPath = CRAMInteropTestUtils.getCRAMInteropTestDataLocation()
                .resolve(CRAMInteropTestUtils.GZIP_PATH + datasetName + CRAMInteropTestUtils.GZIP_SUFFIX);

        try (final InputStream rawStream = new GZIPInputStream(Files.newInputStream(rawPath))) {
            // Read newline-delimited quality records, stripping the tab-delimited direction column
            // (present in q40+dir). Each line becomes one record for FQZComp.
            final byte[] rawBytes = IOUtils.toByteArray(rawStream);
            final List<byte[]> records = splitIntoRecords(rawBytes);

            // Convert from FASTQ ASCII to Phred and build flat array + lengths
            int totalLen = 0;
            final int[] recordLengths = new int[records.size()];
            for (int i = 0; i < records.size(); i++) {
                SAMUtils.fastqToPhred(records.get(i));
                recordLengths[i] = records.get(i).length;
                totalLen += recordLengths[i];
            }

            final byte[] allQuals = new byte[totalLen];
            int offset = 0;
            for (final byte[] record : records) {
                System.arraycopy(record, 0, allQuals, offset, record.length);
                offset += record.length;
            }

            // Round-trip through FQZComp
            final ByteBuffer input = CompressionUtils.wrap(allQuals);
            final ByteBuffer compressed = new FQZCompEncode().compress(input, recordLengths);
            final ByteBuffer decompressed = FQZCompDecode.uncompress(compressed);

            input.rewind();
            Assert.assertEquals(decompressed, input,
                    "FQZComp hts-specs round-trip failed for: " + datasetName);
        }
    }

    /**
     * Split raw newline-delimited data into records, stripping any tab-delimited extra columns
     * (e.g. the direction flag in q40+dir). Empty trailing records are excluded.
     */
    private static List<byte[]> splitIntoRecords(final byte[] rawBytes) {
        final List<byte[]> records = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= rawBytes.length; i++) {
            if (i == rawBytes.length || rawBytes[i] == '\n') {
                if (i > start) {
                    // Find tab boundary (if any) to strip extra columns
                    int end = i;
                    for (int j = start; j < i; j++) {
                        if (rawBytes[j] == '\t') {
                            end = j;
                            break;
                        }
                    }
                    final byte[] record = new byte[end - start];
                    System.arraycopy(rawBytes, start, record, 0, record.length);
                    records.add(record);
                }
                start = i + 1;
            }
        }
        return records;
    }

    private static int[] makeEqualLengths(final int numRecords, final int recordLength) {
        final int[] lengths = new int[numRecords];
        java.util.Arrays.fill(lengths, recordLength);
        return lengths;
    }

}