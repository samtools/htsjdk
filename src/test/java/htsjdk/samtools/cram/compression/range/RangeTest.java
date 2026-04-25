package htsjdk.samtools.cram.compression.range;

import htsjdk.HtsjdkTest;
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
 * Tests for Range codecs.
 *
 * Encoder and decoder instances are shared across all test cases to avoid excessive memory allocation.
 * Each encoder/decoder eagerly allocates large internal model arrays, and creating hundreds of instances
 * (one per DataProvider row) causes heap exhaustion when tests run in parallel.
 *
 * !!!This precludes running these tests in PARALLEL!!!
 */
public class RangeTest extends HtsjdkTest {
    private final Random random = new Random(TestUtil.RANDOM_SEED);

    // Shared encoder/decoder instances — reused across all DataProvider rows to avoid
    // allocating hundreds of copies of their large internal model arrays.
    private final RangeEncode rangeEncoder = new RangeEncode();
    private final RangeDecode rangeDecoder = new RangeDecode();

    // Shared test data — allocated once and referenced (not copied) by all DataProvider rows.
    private final TestDataEnvelope EMPTY = new TestDataEnvelope(new byte[] {});
    private final TestDataEnvelope[] testData = {
        new TestDataEnvelope(new byte[] {0}),
        new TestDataEnvelope(new byte[] {0, 1}),
        new TestDataEnvelope(new byte[] {0, 1, 2}),
        new TestDataEnvelope(new byte[] {0, 1, 2, 3}),
        new TestDataEnvelope(new byte[1000]),
        new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> (byte) 1)),
        new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> Byte.MIN_VALUE)),
        new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> Byte.MAX_VALUE)),
        new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> (byte) index.intValue())),
        new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> index < n / 2 ? (byte) 0 : (byte) 1)),
        new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> index < n % 2 ? (byte) 0 : (byte) 1)),
        new TestDataEnvelope(randomBytesFromGeometricDistribution(1000, 0.1)),
        new TestDataEnvelope(randomBytesFromGeometricDistribution(1000, 0.01)),
        new TestDataEnvelope(randomBytesFromGeometricDistribution(100 * 1000 + 1, 0.01)),
    };
    private final TestDataEnvelope tinyData = new TestDataEnvelope(randomBytesFromGeometricDistribution(100, 0.1));
    private final TestDataEnvelope smallData = new TestDataEnvelope(randomBytesFromGeometricDistribution(1000, 0.01));
    private final TestDataEnvelope largeData =
            new TestDataEnvelope(randomBytesFromGeometricDistribution(100 * 1000 + 3, 0.01));

    private static class TestDataEnvelope {
        public final byte[] testArray;

        public TestDataEnvelope(final byte[] testdata) {
            this.testArray = testdata;
        }

        public String toString() {
            return String.format("Array of size %d", testArray.length);
        }
    }

    // List of all Range parameter flag combinations to test
    private static final List<Integer> RANGE_FORMAT_FLAGS = Arrays.asList(
            0x00,
            RangeParams.ORDER_FLAG_MASK,
            RangeParams.RLE_FLAG_MASK,
            RangeParams.RLE_FLAG_MASK | RangeParams.ORDER_FLAG_MASK,
            RangeParams.CAT_FLAG_MASK,
            RangeParams.CAT_FLAG_MASK | RangeParams.ORDER_FLAG_MASK,
            RangeParams.PACK_FLAG_MASK,
            RangeParams.PACK_FLAG_MASK | RangeParams.ORDER_FLAG_MASK,
            RangeParams.PACK_FLAG_MASK | RangeParams.RLE_FLAG_MASK,
            RangeParams.PACK_FLAG_MASK | RangeParams.RLE_FLAG_MASK | RangeParams.ORDER_FLAG_MASK,
            RangeParams.EXT_FLAG_MASK,
            RangeParams.EXT_FLAG_MASK | RangeParams.PACK_FLAG_MASK,
            RangeParams.STRIPE_FLAG_MASK,
            RangeParams.STRIPE_FLAG_MASK | RangeParams.ORDER_FLAG_MASK);

    @DataProvider(name = "rangeCodecs")
    public Object[][] getRangeCodecs() {
        final List<Object[]> testCases = new ArrayList<>();
        for (final int formatFlag : RANGE_FORMAT_FLAGS) {
            testCases.add(new Object[] {rangeEncoder, rangeDecoder, new RangeParams(formatFlag)});
        }
        return testCases.toArray(new Object[][] {});
    }

    private Object[][] getTestDataRows() {
        final Object[][] rows = new Object[testData.length][];
        for (int i = 0; i < testData.length; i++) {
            rows[i] = new Object[] {testData[i]};
        }
        return rows;
    }

    @DataProvider(name = "allRangeCodecsAndData")
    public Object[][] getAllRangeCodecsAndData() {
        return Stream.concat(
                        Arrays.stream(TestNGUtils.cartesianProduct(getRangeCodecs(), getTestDataRows())),
                        Arrays.stream(TestNGUtils.cartesianProduct(getRangeCodecs(), new Object[][] {{EMPTY}})))
                .toArray(Object[][]::new);
    }

    @DataProvider(name = "allRangeCodecsAndDataForTinySmallLarge")
    public Object[][] allRangeCodecsAndDataForTinySmallLarge() {
        final Object[][] tslData = {
            {tinyData, 1, 100},
            {smallData, 4, 1000},
            {largeData, 100 * 1000 + 3 - 4, 100 * 1000 + 3},
        };
        return TestNGUtils.cartesianProduct(getRangeCodecs(), tslData);
    }

    @Test(dataProvider = "allRangeCodecsAndData")
    public void testRoundTrip(
            final RangeEncode rangeEncode,
            final RangeDecode rangeDecode,
            final RangeParams rangeParams,
            final TestDataEnvelope td) {
        rangeRoundTrip(rangeEncode, rangeDecode, rangeParams, CompressionUtils.wrap(td.testArray));
    }

    @Test(dataProvider = "allRangeCodecsAndDataForTinySmallLarge")
    public void testRoundTripTinySmallLarge(
            final RangeEncode rangeEncode,
            final RangeDecode rangeDecode,
            final RangeParams rangeParams,
            final TestDataEnvelope td,
            final Integer lowerLimit,
            final Integer upperLimit) {
        final ByteBuffer in = CompressionUtils.wrap(td.testArray);
        for (int size = lowerLimit; size < upperLimit; size++) {
            in.position(0);
            in.limit(size);
            rangeRoundTrip(rangeEncode, rangeDecode, rangeParams, in);
        }
    }

    private static void rangeRoundTrip(
            final RangeEncode rangeEncode,
            final RangeDecode rangeDecode,
            final RangeParams rangeParams,
            final ByteBuffer data) {
        final ByteBuffer compressed = rangeEncode.compress(data, rangeParams);
        final ByteBuffer uncompressed = rangeDecode.uncompress(compressed);
        data.rewind();
        Assert.assertEquals(data, uncompressed);
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
