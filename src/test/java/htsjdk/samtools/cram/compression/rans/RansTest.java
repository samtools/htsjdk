package htsjdk.samtools.cram.compression.rans;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.rans.rans4x8.RANS4x8Decode;
import htsjdk.samtools.cram.compression.rans.rans4x8.RANS4x8Encode;
import htsjdk.samtools.cram.compression.rans.rans4x8.RANS4x8Params;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Decode;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Encode;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Params;
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

/**
 * Created by vadim on 22/04/2015.
 */
public class RansTest extends HtsjdkTest {
    private final Random random = new Random(TestUtil.RANDOM_SEED);

    // Since some of our test cases use very large byte arrays, so enclose them in a wrapper class since
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

    public Object[][] getRansEmptyTestData() {
        return new Object[][]{
                { new TestDataEnvelope(new byte[]{}) },
        };
    }

    public Object[][] getRansTestData() {
        return new Object[][] {
                { new TestDataEnvelope(new byte[] {0}) },
                { new TestDataEnvelope(new byte[] {0, 1}) },
                { new TestDataEnvelope(new byte[] {0, 1, 2}) },
                { new TestDataEnvelope(new byte[] {0, 1, 2, 3}) },
                { new TestDataEnvelope(new byte[] {1, 2, 3, 4}) },
                { new TestDataEnvelope(new byte[] {1, 2, 3, 4, 5}) },
                { new TestDataEnvelope(new byte[1000]) },
                { new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> (byte) 1)) },
                { new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> Byte.MIN_VALUE)) },
                { new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> Byte.MAX_VALUE)) },
                { new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> (byte) index.intValue())) },
                { new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> index < n / 2 ? (byte) 0 : (byte) 1)) },
                { new TestDataEnvelope(getNBytesWithValues(1000, (n, index) -> index < n % 2 ? (byte) 0 : (byte) 1)) },
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(10, 0.1)) },
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(31, 0.1)) },
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(32, 0.1)) },
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(33, 0.1)) },
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(1000, 0.1)) },
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(1000, 0.01)) },
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(10 * 1000 * 1000 + 1, 0.01)) },
        };
    }

    public Object[][] getRansTestDataTinySmallLarge() {

        // params: test data, lower limit, upper limit
        return new Object[][]{
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(100, 0.1)), 1, 100 }, // Tiny
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(1000, 0.01)), 4, 1000 }, // Small
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(100 * 1000 + 3, 0.01)), 100 * 1000 + 3 - 4, 100 * 1000 + 3 } // Large
        };
    }

    @DataProvider(name="rans4x8")
    public Object[][] getRans4x8Codecs() {

        // params: RANS encoder, RANS decoder, RANS params
        return new Object[][]{
                {new RANS4x8Encode(), new RANS4x8Decode(), new RANS4x8Params(RANSParams.ORDER.ZERO)},
                {new RANS4x8Encode(), new RANS4x8Decode(), new RANS4x8Params(RANSParams.ORDER.ONE)}
        };
    }

    @DataProvider(name="ransNx16")
    public Object[][] getRansNx16Codecs() {

        // params: RANS encoder, RANS decoder, RANS params
        final List<Integer> ransNx16ParamsFormatFlagList = Arrays.asList(
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
                RANSNx16Params.RLE_FLAG_MASK | RANSNx16Params.PACK_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK
        );
        final List<Object[]> testCases = new ArrayList<>();
        for (Integer ransNx16ParamsFormatFlag : ransNx16ParamsFormatFlagList) {
            final Object[] objects = new Object[]{
                    new RANSNx16Encode(),
                    new RANSNx16Decode(),
                    new RANSNx16Params(ransNx16ParamsFormatFlag)
            };
            testCases.add(objects);
        }
        return testCases.toArray(new Object[][]{});
    }

    public Object[][] getRansNx16Encoder() {

        // params: RANS encoder, RANS params
        return new Object[][]{
                {new RANSNx16Encode(), new RANSNx16Params(RANSNx16Params.STRIPE_FLAG_MASK)},
                {new RANSNx16Encode(), new RANSNx16Params(RANSNx16Params.ORDER_FLAG_MASK|RANSNx16Params.STRIPE_FLAG_MASK)}
        };
    }

    @DataProvider(name="RansNx16RejectEncodeStripe")
    public Object[][] getRansNx16RejectEncodeStripe() {

        // params: RANS encoder, RANS decoder, RANS params, test data
        // this data provider provides all the non-empty testdata input for RANS Nx16 codec
        return TestNGUtils.cartesianProduct(getRansNx16Encoder(), getRansTestData());
    }

    public Object[][] getAllRansCodecs() {

        // params: RANSEncode, RANSDecode, RANSParams
        // concatenate RANS4x8 and RANSNx16 codecs
        return Stream.concat(Arrays.stream(getRans4x8Codecs()), Arrays.stream(getRansNx16Codecs()))
                .toArray(Object[][]::new);
    }

    @DataProvider(name="allRansAndData")
    public Object[][] getAllRansAndData() {

        // params: RANSEncode, RANSDecode, RANSParams, test data
        // this data provider provides all the testdata for all of RANS codecs
        return Stream.concat(
                Arrays.stream(TestNGUtils.cartesianProduct(getAllRansCodecs(), getRansTestData())),
                Arrays.stream(TestNGUtils.cartesianProduct(getAllRansCodecs(), getRansEmptyTestData())))
                .toArray(Object[][]::new);
    }

    @DataProvider(name="allRansAndDataForTinySmallLarge")
    public Object[][] getAllRansAndDataForTinySmallLarge() {

        // params: RANSEncode, RANSDecode, RANSParams, test data, lower limit, upper limit
        // this data provider provides Tiny, Small and Large testdata for all of RANS codecs
        return TestNGUtils.cartesianProduct(getAllRansCodecs(), getRansTestDataTinySmallLarge());
    }

    @Test(dataProvider = "allRansAndDataForTinySmallLarge")
    public void testRoundTripTinySmallLarge(
            final RANSEncode ransEncode,
            final RANSDecode ransDecode,
            final RANSParams params,
            final TestDataEnvelope td,
            final Integer lowerLimit,
            final Integer upperLimit){
        final ByteBuffer in = CompressionUtils.wrap(td.testArray);
        for (int rawSize = lowerLimit; rawSize < upperLimit; rawSize++) {
            in.position(0);
            in.limit(rawSize);
            ransRoundTrip(ransEncode, ransDecode, params, in);
        }
    }

    @Test(dataProvider = "rans4x8")
    public void testRans4x8BuffersMeetBoundaryExpectations(
            final RANS4x8Encode ransEncode,
            final RANS4x8Decode ransDecode,
            final RANS4x8Params params) {
        final int rawSize = 1001;
        final ByteBuffer rawData = CompressionUtils.wrap(randomBytesFromGeometricDistribution(rawSize, 0.01));
        final ByteBuffer compressed = ransBufferMeetBoundaryExpectations(rawSize,rawData,ransEncode, ransDecode,params);
        Assert.assertTrue(compressed.limit() > Constants.RANS_4x8_PREFIX_BYTE_LENGTH); // minimum prefix len when input is not Empty
        Assert.assertEquals(compressed.get(), (byte) params.getOrder().ordinal());
        Assert.assertEquals(compressed.getInt(), compressed.limit() - Constants.RANS_4x8_PREFIX_BYTE_LENGTH);
        Assert.assertEquals(compressed.getInt(), rawSize);
    }

    @Test(dataProvider = "ransNx16")
    public void testRansNx16BuffersMeetBoundaryExpectations(
            final RANSNx16Encode ransEncode,
            final RANSNx16Decode ransDecode,
            final RANSNx16Params params) {
        final int rawSize = 1001;
        final ByteBuffer rawData = CompressionUtils.wrap(randomBytesFromGeometricDistribution(rawSize, 0.01));
        final ByteBuffer compressed = ransBufferMeetBoundaryExpectations(rawSize,rawData,ransEncode,ransDecode,params);
        rawData.rewind();
        Assert.assertTrue(compressed.limit() > 1); // minimum prefix len when input is not Empty
        final int FormatFlags = compressed.get() & 0xFF; // first byte of compressed data is the formatFlags
        final int[] frequencies = new int[Constants.NUMBER_OF_SYMBOLS];
        final int inSize = rawData.remaining();
        for (int i = 0; i < inSize; i ++) {
            frequencies[rawData.get(i) & 0xFF]++;
        }
        int numSym = 0;
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            if (frequencies[i]>0) {
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
        if (!params.isNosz()){
            Assert.assertEquals(CompressionUtils.readUint7(compressed), rawSize);
        }
    }

    @Test(dataProvider="allRansAndData")
    public void testRoundTrip(
            final RANSEncode ransEncode,
            final RANSDecode ransDecode,
            final RANSParams params,
            final TestDataEnvelope td) {
        ransRoundTrip(ransEncode, ransDecode, params, CompressionUtils.wrap(td.testArray));
    }

    @Test(
            dataProvider = "RansNx16RejectEncodeStripe",
            expectedExceptions = { CRAMException.class },
            expectedExceptionsMessageRegExp = "RANSNx16 Encoding with Stripe Flag is not implemented.")
    public void testRansNx16RejectEncodeStripe(
            final RANSNx16Encode ransEncode,
            final RANSNx16Params params,
            final TestDataEnvelope td) {

        // When td is not Empty, Encoding with Stripe Flag should throw an Exception
        // as Encode Stripe is not implemented
        ransEncode.compress(CompressionUtils.wrap(td.testArray), params);
    }

    @Test(
            description = "RANSNx16 Decoding with Pack Flag if (numSymbols > 16 or numSymbols==0) " +
                    "should throw CRAMException",
            expectedExceptions = { CRAMException.class },
            expectedExceptionsMessageRegExp = "Bit Packing is not permitted when number " +
                    "of distinct symbols is greater than 16 or equal to 0. Number of distinct symbols: 0")
    public void testRANSNx16RejectDecodePack(){
        final ByteBuffer compressedData = CompressionUtils.wrap(new byte[]{(byte) RANSNx16Params.PACK_FLAG_MASK, (byte) 0x00, (byte) 0x00});
        final RANSNx16Decode ransDecode = new RANSNx16Decode();
        ransDecode.uncompress(compressedData);
    }

    private static void ransRoundTrip(
            final RANSEncode ransEncode,
            final RANSDecode ransDecode,
            final RANSParams params,
            final ByteBuffer data) {
        final ByteBuffer compressed = ransEncode.compress(data, params);
        final ByteBuffer uncompressed = ransDecode.uncompress(compressed);
        data.rewind();
        Assert.assertEquals(data, uncompressed);
    }

    public ByteBuffer ransBufferMeetBoundaryExpectations(
            final int rawSize,
            final ByteBuffer raw,
            final RANSEncode ransEncode,
            final RANSDecode ransDecode,
            final RANSParams params){
        // helper method for Boundary Expectations test
        final ByteBuffer compressed = ransEncode.compress(raw, params);
        final ByteBuffer uncompressed = ransDecode.uncompress(compressed);
        Assert.assertFalse(compressed.hasRemaining());
        compressed.rewind();
        Assert.assertEquals(uncompressed.limit(), rawSize);
        Assert.assertEquals(uncompressed.position(), 0);
        Assert.assertFalse(raw.hasRemaining());
        Assert.assertEquals(raw.limit(), rawSize);
        Assert.assertEquals(compressed.position(), 0);
        return compressed;
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

    /**
     * A crude implementation of RNG for sampling geometric distribution. The
     * value returned is offset by -1 to include zero. For testing purposes
     * only, no refunds!
     *
     * @param probability the probability of success
     * @return an almost random byte value.
     */
    private byte drawByteFromGeometricDistribution(final double probability) {
        final double rand = random.nextDouble();
        final double g = Math.ceil(Math.log(1 - rand) / Math.log(1 - probability)) - 1;
        return (byte) g;
    }

}