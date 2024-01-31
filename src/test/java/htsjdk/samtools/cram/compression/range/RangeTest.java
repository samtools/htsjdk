package htsjdk.samtools.cram.compression.range;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.util.TestUtil;
import htsjdk.utils.TestNGUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class RangeTest extends HtsjdkTest {
    private final Random random = new Random(TestUtil.RANDOM_SEED);

    private static class TestDataEnvelope {
        public final byte[] testArray;
        public TestDataEnvelope(final byte[] testdata) {
            this.testArray = testdata;
        }
        public String toString() {
            return String.format("Array of size %d", testArray.length);
        }
    }

    public Object[][] getRangeEmptyTestData() {
        return new Object[][]{
                { new TestDataEnvelope(new byte[]{}) },
        };
    }

    @DataProvider(name = "rangeTestData")
    public Object[][] getRangeTestData() {
        return new Object[][] {
                { new TestDataEnvelope(new byte[] {0}) },
                { new TestDataEnvelope(new byte[] {0, 1}) },
                { new TestDataEnvelope(new byte[] {0, 1, 2}) },
                { new TestDataEnvelope(new byte[] {0, 1, 2, 3}) },
                { new TestDataEnvelope(new byte[1000]) },
                { new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> (byte) 1)) },
                { new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> Byte.MIN_VALUE)) },
                { new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> Byte.MAX_VALUE)) },
                { new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> (byte) index.intValue())) },
                { new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> index < n / 2 ? (byte) 0 : (byte) 1)) },
                { new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> index < n % 2 ? (byte) 0 : (byte) 1)) },
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(1000, 0.1)) },
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(1000, 0.01)) },
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(10 * 1000 * 1000 + 1, 0.01)) },
        };
    }

    public Object[][] getRangeTestDataTinySmallLarge() {

        // params: test data, lower limit, upper limit
        return new Object[][]{
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(100, 0.1)), 1, 100 }, // Tiny
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(1000, 0.01)), 4, 1000 }, // Small
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(100 * 1000 + 3, 0.01)), 100 * 1000 + 3 - 4, 100 * 1000 + 3 } // Large
        };
    }

    @DataProvider(name="rangeCodecs")
    public Object[][] getRangeCodecs() {

        // params: RangeEncoder, RangeDecoder, RangeParams
        final List<Integer> rangeParamsFormatFlagList = Arrays.asList(
                0x00,
                RangeParams.ORDER_FLAG_MASK,
                RangeParams.RLE_FLAG_MASK,
                RangeParams.RLE_FLAG_MASK | RangeParams.ORDER_FLAG_MASK,
                RangeParams.CAT_FLAG_MASK,
                RangeParams.CAT_FLAG_MASK | RangeParams.ORDER_FLAG_MASK,
                RangeParams.PACK_FLAG_MASK,
                RangeParams.PACK_FLAG_MASK | RangeParams. ORDER_FLAG_MASK,
                RangeParams.PACK_FLAG_MASK | RangeParams.RLE_FLAG_MASK,
                RangeParams.PACK_FLAG_MASK | RangeParams.RLE_FLAG_MASK | RangeParams.ORDER_FLAG_MASK,
                RangeParams.EXT_FLAG_MASK,
                RangeParams.EXT_FLAG_MASK | RangeParams.PACK_FLAG_MASK);
        final List<Object[]> testCases = new ArrayList<>();
        for (Integer rangeParamsFormatFlag : rangeParamsFormatFlagList) {
            Object[] objects = new Object[]{
                    new RangeEncode(),
                    new RangeDecode(),
                    new RangeParams(rangeParamsFormatFlag)
            };
            testCases.add(objects);
        }
        return testCases.toArray(new Object[][]{});
    }

    public Object[][] getRangeDecodeOnlyCodecs() {
        // params: Range encoder, Range decoder, Range params
        final List<Integer> rangeParamsFormatFlagList = Arrays.asList(
                RangeParams.STRIPE_FLAG_MASK,
                RangeParams.STRIPE_FLAG_MASK | RangeParams.ORDER_FLAG_MASK);
        final List<Object[]> testCases = new ArrayList<>();
        for (Integer rangeParamsFormatFlag : rangeParamsFormatFlagList) {
            Object[] objects = new Object[]{
                    new RangeEncode(),
                    new RangeDecode(),
                    new RangeParams(rangeParamsFormatFlag)
            };
            testCases.add(objects);
        }
        return testCases.toArray(new Object[][]{});
    }

    @DataProvider(name="RangeDecodeOnlyAndData")
    public Object[][] getRangeDecodeOnlyAndData() {

        // params: Range encoder, Range decoder, Range params, test data
        // this data provider provides all the non-empty testdata input for Range codec
        return TestNGUtils.cartesianProduct(getRangeDecodeOnlyCodecs(), getRangeTestData());
    }

    @DataProvider(name="allRangeCodecsAndData")
    public Object[][] getAllRangeCodecsAndData() {

        // params: RangeEncode, RangeDecode, RangeParams, test data
        // this data provider provides all the testdata for all of Range codecs
        return Stream.concat(
                        Arrays.stream(TestNGUtils.cartesianProduct(getRangeCodecs(), getRangeTestData())),
                        Arrays.stream(TestNGUtils.cartesianProduct(getRangeCodecs(), getRangeEmptyTestData())))
                .toArray(Object[][]::new);
    }

    @DataProvider(name="allRangeCodecsAndDataForTinySmallLarge")
    public Object[][] allRangeCodecsAndDataForTinySmallLarge() {

        // params: RangeEncode, RangeDecode, RangeParams, test data, lower limit, upper limit
        // this data provider provides Tiny, Small and Large testdata for all of Range codecs
        return TestNGUtils.cartesianProduct(getRangeCodecs(), getRangeTestDataTinySmallLarge());
    }

    @Test(dataProvider = "allRangeCodecsAndData")
    public void testRoundTrip(final RangeEncode rangeEncode,
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
            final Integer upperLimit){
        final ByteBuffer in = CompressionUtils.wrap(td.testArray);
        for (int size = lowerLimit; size < upperLimit; size++) {
            in.position(0);
            in.limit(size);
            rangeRoundTrip(rangeEncode, rangeDecode, rangeParams, in);
        }
    }

    @Test(
            dataProvider = "RangeDecodeOnlyAndData",
            expectedExceptions = { CRAMException.class },
            expectedExceptionsMessageRegExp = "Range Encoding with Stripe Flag is not implemented.")
    public void testRangeEncodeStripe(
            final RangeEncode rangeEncode,
            final RangeDecode unused,
            final RangeParams params,
            final TestDataEnvelope td) {

        // When td is not Empty, Encoding with Stripe Flag should throw an Exception
        // as Encode Stripe is not implemented
        final ByteBuffer compressed = rangeEncode.compress(CompressionUtils.wrap(td.testArray), params);
    }

    // testRangeBuffersMeetBoundaryExpectations
    // testRangeHeader
    // testRangeEncodeStripe

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

//    TODO: Add to utils
    private byte[] getNBytesWithValues(final int n, final BiFunction<Integer, Integer, Byte> valueForIndex) {
        final byte[] data = new byte[n];
        for (int i = 0; i < data.length; i++) {
            data[i] = valueForIndex.apply(n, i);
        }
        return data;
    }
    //    TODO: Add to utils
    private byte[] randomBytesFromGeometricDistribution(final int size, final double p) {
        final byte[] data = new byte[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = drawByteFromGeometricDistribution(p);
        }
        return data;
    }

    /**
     * A crude implementation of RNG for sampling geometric distribution. The
     * value returned is offset by -1 to include zero. For testing purposes
     * only, no refunds!
     *
     * @param probability the probability of success
     * @return an almost random byte value.
     */
    //    TODO: Add to utils
    private byte drawByteFromGeometricDistribution(final double probability) {
        final double rand = random.nextDouble();
        final double g = Math.ceil(Math.log(1 - rand) / Math.log(1 - probability)) - 1;
        return (byte) g;
    }

}