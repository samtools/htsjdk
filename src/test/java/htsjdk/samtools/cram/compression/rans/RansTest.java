package htsjdk.samtools.cram.compression.rans;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.util.TestUtil;
import htsjdk.utils.TestNGUtils;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests for rANS 4x8 and Nx16 codecs.
 *
 * Encoder and decoder instances are shared across all test cases to avoid excessive memory allocation.
 * Each encoder/decoder eagerly allocates large internal symbol tables, and creating hundreds of instances
 * (one per DataProvider row) causes heap exhaustion when tests run in parallel.
 *
 * !!!This precludes running these tests in PARALLEL!!!
 */
public class RansTest extends HtsjdkTest {
    private final Random random = new Random(TestUtil.RANDOM_SEED);

    // Shared encoder/decoder instances — reused across all DataProvider rows to avoid
    // allocating hundreds of copies of their large internal symbol/frequency tables.
    private final RANS4x8Encode rans4x8Encoder = new RANS4x8Encode();
    private final RANS4x8Decode rans4x8Decoder = new RANS4x8Decode();
    private final RANSNx16Encode ransNx16Encoder = new RANSNx16Encode();
    private final RANSNx16Decode ransNx16Decoder = new RANSNx16Decode();

    // Shared test data — allocated once and referenced (not copied) by all DataProvider rows.
    private final TestDataEnvelope EMPTY = new TestDataEnvelope(new byte[] {});
    private final TestDataEnvelope[] testData = {
        new TestDataEnvelope(new byte[] {0}),
        new TestDataEnvelope(new byte[] {0, 1}),
        new TestDataEnvelope(new byte[] {0, 1, 2}),
        new TestDataEnvelope(new byte[] {0, 1, 2, 3}),
        new TestDataEnvelope(new byte[] {1, 2, 3, 4}),
        new TestDataEnvelope(new byte[] {1, 2, 3, 4, 5}),
        new TestDataEnvelope(new byte[1000]),
        new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> (byte) 1)),
        new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> Byte.MIN_VALUE)),
        new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> Byte.MAX_VALUE)),
        new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> (byte) index.intValue())),
        new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> index < n / 2 ? (byte) 0 : (byte) 1)),
        new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> index < n % 2 ? (byte) 0 : (byte) 1)),
        new TestDataEnvelope(randomBytesFromGeometricDistribution(10, 0.1)),
        new TestDataEnvelope(randomBytesFromGeometricDistribution(31, 0.1)),
        new TestDataEnvelope(randomBytesFromGeometricDistribution(32, 0.1)),
        new TestDataEnvelope(randomBytesFromGeometricDistribution(33, 0.1)),
        new TestDataEnvelope(randomBytesFromGeometricDistribution(1000, 0.1)),
        new TestDataEnvelope(randomBytesFromGeometricDistribution(1000, 0.01)),
        new TestDataEnvelope(randomBytesFromGeometricDistribution(100 * 1000 + 1, 0.01)),
    };
    private final TestDataEnvelope tinyData = new TestDataEnvelope(randomBytesFromGeometricDistribution(100, 0.1));
    private final TestDataEnvelope smallData = new TestDataEnvelope(randomBytesFromGeometricDistribution(1000, 0.01));
    private final TestDataEnvelope largeData =
            new TestDataEnvelope(randomBytesFromGeometricDistribution(100 * 1000 + 3, 0.01));

    // Since some of our test cases use very large byte arrays, enclose them in a wrapper class since
    // otherwise IntelliJ serializes them to strings for display in the test output, which is *super*-slow.
    private static class TestDataEnvelope {
        public final byte[] testArray;

        public TestDataEnvelope(final byte[] testdata) {
            this.testArray = testdata;
        }

        public String toString() {
            return String.format("Array of size %d", testArray.length);
        }
    }

    // List of all RANSNx16 parameter flag combinations to test
    private static final List<Integer> RANS_NX16_FORMAT_FLAGS = Arrays.asList(
            0x00,
            RANSNx16Params.ORDER_FLAG_MASK,
            RANSNx16Params.N32_FLAG_MASK,
            RANSNx16Params.N32_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK,
            RANSNx16Params.CAT_FLAG_MASK,
            RANSNx16Params.CAT_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK,
            RANSNx16Params.CAT_FLAG_MASK | RANSNx16Params.N32_FLAG_MASK,
            RANSNx16Params.CAT_FLAG_MASK | RANSNx16Params.N32_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK,
            RANSNx16Params.RLE_FLAG_MASK,
            RANSNx16Params.RLE_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK,
            RANSNx16Params.PACK_FLAG_MASK,
            RANSNx16Params.PACK_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK,
            RANSNx16Params.RLE_FLAG_MASK | RANSNx16Params.PACK_FLAG_MASK,
            RANSNx16Params.RLE_FLAG_MASK | RANSNx16Params.PACK_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK,
            RANSNx16Params.STRIPE_FLAG_MASK,
            RANSNx16Params.STRIPE_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK);

    @DataProvider(name = "rans4x8")
    public Object[][] getRans4x8Codecs() {
        return new Object[][] {
            {rans4x8Encoder, rans4x8Decoder, new RANS4x8Params(RANSParams.ORDER.ZERO)},
            {rans4x8Encoder, rans4x8Decoder, new RANS4x8Params(RANSParams.ORDER.ONE)}
        };
    }

    @DataProvider(name = "ransNx16")
    public Object[][] getRansNx16Codecs() {
        final List<Object[]> testCases = new ArrayList<>();
        for (final int formatFlag : RANS_NX16_FORMAT_FLAGS) {
            testCases.add(new Object[] {ransNx16Encoder, ransNx16Decoder, new RANSNx16Params(formatFlag)});
        }
        return testCases.toArray(new Object[][] {});
    }

    private Object[][] getAllRansCodecs() {
        return Stream.concat(Arrays.stream(getRans4x8Codecs()), Arrays.stream(getRansNx16Codecs()))
                .toArray(Object[][]::new);
    }

    private Object[][] getTestDataRows() {
        final Object[][] rows = new Object[testData.length][];
        for (int i = 0; i < testData.length; i++) {
            rows[i] = new Object[] {testData[i]};
        }
        return rows;
    }

    @DataProvider(name = "allRansAndData")
    public Object[][] getAllRansAndData() {
        return Stream.concat(
                        Arrays.stream(TestNGUtils.cartesianProduct(getAllRansCodecs(), getTestDataRows())),
                        Arrays.stream(TestNGUtils.cartesianProduct(getAllRansCodecs(), new Object[][] {{EMPTY}})))
                .toArray(Object[][]::new);
    }

    @DataProvider(name = "allRansAndDataForTinySmallLarge")
    public Object[][] getAllRansAndDataForTinySmallLarge() {
        final Object[][] tslData = {
            {tinyData, 1, 100},
            {smallData, 4, 1000},
            {largeData, 100 * 1000 + 3 - 4, 100 * 1000 + 3},
        };
        return TestNGUtils.cartesianProduct(getAllRansCodecs(), tslData);
    }

    @Test(dataProvider = "allRansAndDataForTinySmallLarge")
    public void testRoundTripTinySmallLarge(
            final RANSEncode ransEncode,
            final RANSDecode ransDecode,
            final RANSParams params,
            final TestDataEnvelope td,
            final Integer lowerLimit,
            final Integer upperLimit) {
        final ByteBuffer in = CompressionUtils.wrap(td.testArray);
        for (int rawSize = lowerLimit; rawSize < upperLimit; rawSize++) {
            in.position(0);
            in.limit(rawSize);
            ransRoundTrip(ransEncode, ransDecode, params, in);
        }
    }

    @Test(dataProvider = "rans4x8")
    public void testRans4x8BuffersMeetBoundaryExpectations(
            final RANS4x8Encode ransEncode, final RANS4x8Decode ransDecode, final RANS4x8Params params) {
        final int rawSize = 1001;
        final ByteBuffer rawData = CompressionUtils.wrap(randomBytesFromGeometricDistribution(rawSize, 0.01));
        final ByteBuffer compressed =
                ransBufferMeetBoundaryExpectations(rawSize, rawData, ransEncode, ransDecode, params);
        Assert.assertTrue(compressed.limit()
                > Constants.RANS_4x8_PREFIX_BYTE_LENGTH); // minimum prefix len when input is not Empty
        Assert.assertEquals(compressed.get(), (byte) params.getOrder().ordinal());
        Assert.assertEquals(compressed.getInt(), compressed.limit() - Constants.RANS_4x8_PREFIX_BYTE_LENGTH);
        Assert.assertEquals(compressed.getInt(), rawSize);
    }

    @Test(dataProvider = "ransNx16")
    public void testRansNx16BuffersMeetBoundaryExpectations(
            final RANSNx16Encode ransEncode, final RANSNx16Decode ransDecode, final RANSNx16Params params) {
        final int rawSize = 1001;
        final ByteBuffer rawData = CompressionUtils.wrap(randomBytesFromGeometricDistribution(rawSize, 0.01));
        final ByteBuffer compressed =
                ransBufferMeetBoundaryExpectations(rawSize, rawData, ransEncode, ransDecode, params);
        rawData.rewind();
        Assert.assertTrue(compressed.limit() > 1); // minimum prefix len when input is not Empty
        final int FormatFlags = compressed.get() & 0xFF; // first byte of compressed data is the formatFlags

        // STRIPE has a different internal structure (stripe framing), so skip detailed boundary checks
        if (params.isStripe()) {
            return;
        }

        final int[] frequencies = new int[Constants.NUMBER_OF_SYMBOLS];
        final int inSize = rawData.remaining();
        for (int i = 0; i < inSize; i++) {
            frequencies[rawData.get(i) & 0xFF]++;
        }
        int numSym = 0;
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            if (frequencies[i] > 0) {
                numSym++;
            }
        }
        if (params.isPack() & (numSym == 0 | numSym > 16)) {
            // In the encoder, Packing is skipped if numSymbols = 0  or numSymbols > 16
            // and the Pack flag is unset in the formatFlags
            Assert.assertEquals(FormatFlags, params.getFormatFlags() & ~RANSNx16Params.PACK_FLAG_MASK);
        } else {
            Assert.assertEquals(FormatFlags, params.getFormatFlags());
        }
        // if nosz flag is not set, then the uncompressed size is recorded
        if (!params.isNosz()) {
            Assert.assertEquals(CompressionUtils.readUint7(compressed), rawSize);
        }
    }

    @Test(dataProvider = "allRansAndData")
    public void testRoundTrip(
            final RANSEncode ransEncode,
            final RANSDecode ransDecode,
            final RANSParams params,
            final TestDataEnvelope td) {
        ransRoundTrip(ransEncode, ransDecode, params, CompressionUtils.wrap(td.testArray));
    }

    @Test(
            description = "RANSNx16 Decoding with Pack Flag if (numSymbols > 16 or numSymbols==0) "
                    + "should throw CRAMException",
            expectedExceptions = {CRAMException.class},
            expectedExceptionsMessageRegExp = "Bit Packing is not permitted when number "
                    + "of distinct symbols is greater than 16 or equal to 0. Number of distinct symbols: 0")
    public void testRANSNx16RejectDecodePack() {
        final byte[] compressedData = new byte[] {(byte) RANSNx16Params.PACK_FLAG_MASK, (byte) 0x00, (byte) 0x00};
        final RANSNx16Decode ransDecode = new RANSNx16Decode();
        ransDecode.uncompress(compressedData);
    }

    private static void ransRoundTrip(
            final RANSEncode ransEncode, final RANSDecode ransDecode, final RANSParams params, final ByteBuffer data) {
        final byte[] inputBytes = new byte[data.remaining()];
        data.get(inputBytes);
        final byte[] compressed = ransEncode.compress(inputBytes, params);
        final byte[] uncompressed = ransDecode.uncompress(compressed);
        Assert.assertEquals(ByteBuffer.wrap(uncompressed), ByteBuffer.wrap(inputBytes));
    }

    private ByteBuffer ransBufferMeetBoundaryExpectations(
            final int rawSize,
            final ByteBuffer raw,
            final RANSEncode ransEncode,
            final RANSDecode ransDecode,
            final RANSParams params) {
        final byte[] inputBytes = new byte[raw.remaining()];
        raw.get(inputBytes);
        final byte[] compressed = ransEncode.compress(inputBytes, params);
        final byte[] uncompressed = ransDecode.uncompress(compressed);
        Assert.assertEquals(uncompressed.length, rawSize);
        Assert.assertEquals(raw.limit(), rawSize);
        return ByteBuffer.wrap(compressed).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    }

    private byte[] getNBytesWithValues(final int n, final BiFunction<Integer, Integer, Byte> valueForIndex) {
        final byte[] data = new byte[n];
        for (int i = 0; i < data.length; i++) {
            data[i] = valueForIndex.apply(n, i);
        }
        return data;
    }

    private byte[] randomBytesFromGeometricDistribution(final int size, final double p) {
        final byte[] data = new byte[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = drawByteFromGeometricDistribution(p);
        }
        return data;
    }

    private byte drawByteFromGeometricDistribution(final double probability) {
        final double rand = random.nextDouble();
        final double g = Math.ceil(Math.log(1 - rand) / Math.log(1 - probability)) - 1;
        return (byte) g;
    }
}
