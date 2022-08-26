package htsjdk.samtools.cram.compression.rans;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAMException;
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
import java.util.Arrays;
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

    public Object[][] getRansTestDataTinySmallLarge() {
        return new Object[][]{
                // params: test data, lower limit, upper limit
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(100, 0.1)), 1, 100 }, // Tiny
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(1000, 0.01)), 4, 1000 }, // Small
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(100 * 1000 + 3, 0.01)), 100 * 1000 + 3 - 4, 100 * 1000 + 3 } // Large
        };
    }

    @DataProvider(name="rans4x8")
    public Object[][] getRans4x8Codecs() {
        final RANS4x8Encode rans4x8Encode = new RANS4x8Encode();
        final RANS4x8Decode rans4x8Decode = new RANS4x8Decode();
        return new Object[][]{
                {rans4x8Encode, rans4x8Decode, new RANS4x8Params(RANSParams.ORDER.ZERO)}, // RANS4x8 Order 0
                {rans4x8Encode, rans4x8Decode, new RANS4x8Params(RANSParams.ORDER.ONE)} // RANS4x8 Order 1
        };
    }

    @DataProvider(name="ransNx16")
    public Object[][] getRansNx16Codecs() {
        final RANSNx16Encode ransNx16Encode = new RANSNx16Encode();
        final RANSNx16Decode ransNx16Decode = new RANSNx16Decode();
        // TODO: More formatFlags values i.e, combinations of bit flags will be added later
        return new Object[][]{

                //RANSNx16 formatFlags(first byte) 0: Order 0, N = 4, CAT false
                {ransNx16Encode, ransNx16Decode, new RANSNx16Params(0x00)} ,

                //RANSNx16 formatFlags(first byte) 1: Order 1, N = 4, CAT false
                {ransNx16Encode, ransNx16Decode, new RANSNx16Params(0x01)} ,

                //RANSNx16 formatFlags(first byte) 4: Order 0, N = 32, CAT false
                {ransNx16Encode, ransNx16Decode, new RANSNx16Params(0x04)} ,

                //RANSNx16 formatFlags(first byte) 5: Order 1, N = 32, CAT false
                {ransNx16Encode, ransNx16Decode, new RANSNx16Params(0x05)} ,

                //RANSNx16 formatFlags(first byte) 32: Order 0, N = 4, CAT true
                {ransNx16Encode, ransNx16Decode, new RANSNx16Params(0x20)} ,

                //RANSNx16 formatFlags(first byte) 33: Order 1, N = 4, CAT true
                {ransNx16Encode, ransNx16Decode, new RANSNx16Params(0x21)} ,

                //RANSNx16 formatFlags(first byte) 36: Order 0, N = 32, CAT true
                {ransNx16Encode, ransNx16Decode, new RANSNx16Params(0x24)} ,

                //RANSNx16 formatFlags(first byte) 37: Order 1, N = 32, CAT true
                {ransNx16Encode, ransNx16Decode, new RANSNx16Params(0x25)} ,

                //RANSNx16 formatFlags(first byte) 64: Order 0, N = 4, CAT false, RLE = true
                {ransNx16Encode, ransNx16Decode, new RANSNx16Params(0x40)} ,

                //RANSNx16 formatFlags(first byte) 65: Order 1, N = 4, CAT false, RLE = true
                {ransNx16Encode, ransNx16Decode, new RANSNx16Params(0x41)} ,

                //RANSNx16 formatFlags(first byte) 128: Order 0, N = 4, CAT false, RLE = false, Pack = true
                {ransNx16Encode, ransNx16Decode, new RANSNx16Params(0x80)} ,

                //RANSNx16 formatFlags(first byte) 129: Order 1, N = 4, CAT false, RLE = false, Pack = true
                {ransNx16Encode, ransNx16Decode, new RANSNx16Params(0x81)} ,

                //RANSNx16 formatFlags(first byte) 192: Order 0, N = 4, CAT false, RLE = true, Pack = true
                {ransNx16Encode, ransNx16Decode, new RANSNx16Params(0xC0)} ,

                //RANSNx16 formatFlags(first byte) 193: Order 1, N = 4, CAT false, RLE = true, Pack = true
                {ransNx16Encode, ransNx16Decode, new RANSNx16Params(0xC1)} ,

        };
    }

    public Object[][] getRansNx16DecodeOnlyCodecs() {
        final RANSNx16Encode ransNx16Encode = new RANSNx16Encode();
        final RANSNx16Decode ransNx16Decode = new RANSNx16Decode();
        return new Object[][]{

                //RANSNx16 formatFlags(first byte) 8: Order 0, N = 4, CAT false, RLE = false, Pack = false, Stripe = True
                {ransNx16Encode, ransNx16Decode, new RANSNx16Params(0x08)},

                //RANSNx16 formatFlags(first byte) 9: Order 1, N = 4, CAT false, RLE = false, Pack = false, Stripe = True
                {ransNx16Encode, ransNx16Decode, new RANSNx16Params(0x09)}
        };
    }

    @DataProvider(name="RansNx16DecodeOnlyAndData")
    public Object[][] getRansNx16DecodeOnlyAndData() {

        // this data provider provides all the testdata except empty input for RANS Nx16 codec
        return TestNGUtils.cartesianProduct(getRansNx16DecodeOnlyCodecs(), getRansTestData());
    }

    public Object[][] getAllRansCodecs() {
        // concatenate RANS4x8 and RANSNx16 codecs
        return Stream.concat(Arrays.stream(getRans4x8Codecs()), Arrays.stream(getRansNx16Codecs()))
                .toArray(Object[][]::new);
    }

    @DataProvider(name="allRansAndData")
    public Object[][] getAllRansAndData() {

        // this data provider provides all the testdata for all of RANS codecs
        // params: RANSEncode, RANSDecode, RANSParams, data
        return Stream.concat(
                Arrays.stream(TestNGUtils.cartesianProduct(getAllRansCodecs(), getRansTestData())),
                Arrays.stream(TestNGUtils.cartesianProduct(getAllRansCodecs(), getRansEmptyTestData())))
                .toArray(Object[][]::new);
    }

    @DataProvider(name="allRansAndDataForTinySmallLarge")
    public Object[][] getAllRansAndDataForTinySmallLarge() {

        // this data provider provides Tiny, Small and Large testdata for all of RANS codecs
        // params: RANSEncode, RANSDecode, RANSParams, data, lower limit, upper limit
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
        final ByteBuffer in = ByteBuffer.wrap(td.testArray);
        for (int size = lowerLimit; size < upperLimit; size++) {
            in.position(0);
            in.limit(size);
            ransRoundTrip(ransEncode, ransDecode, params, in);
        }
    }

    @Test(dataProvider = "rans4x8")
    public void testRans4x8BuffersMeetBoundaryExpectations(
            final RANS4x8Encode ransEncode,
            final RANS4x8Decode ransDecode,
            final RANS4x8Params params) {
        final int size = 1001;
        final ByteBuffer raw = ByteBuffer.wrap(randomBytesFromGeometricDistribution(size, 0.01));
        final ByteBuffer compressed = ransBufferMeetBoundaryExpectations(size,raw,ransEncode, ransDecode,params);
        Assert.assertTrue(compressed.limit() > 10);
        Assert.assertEquals(compressed.get(), (byte) params.getOrder().ordinal());
        Assert.assertEquals(compressed.getInt(), compressed.limit() - 1 - 4 - 4);
        Assert.assertEquals(compressed.getInt(), size);
    }

    @Test(dataProvider = "ransNx16")
    public void testRansNx16BuffersMeetBoundaryExpectations(
            final RANSNx16Encode ransEncode,
            final RANSNx16Decode ransDecode,
            final RANSNx16Params params) {
        final int size = 1001;
        final ByteBuffer raw = ByteBuffer.wrap(randomBytesFromGeometricDistribution(size, 0.01));
        final ByteBuffer compressed = ransBufferMeetBoundaryExpectations(size,raw,ransEncode,ransDecode,params);
        Assert.assertTrue(compressed.limit() > 1); // minimum prefix len when input is not Empty
        final int FormatFlags = compressed.get(); // first byte of compressed data is the formatFlags
        raw.rewind();
        int numSym = 0;
        final int[] F = new int[Constants.NUMBER_OF_SYMBOLS];
        final int inSize = raw.remaining();
        for (int i = 0; i < inSize; i ++) {
            F[raw.get(i) & 0xFF]++;
        }
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            if (F[i]>0) {
                numSym++;
            }
        }
        if (params.getPack() & (numSym == 0 | numSym > 16)) {
            Assert.assertEquals(FormatFlags, params.getFormatFlags() & ~RANSNx16Params.PACK_FLAG_MASK);
        } else {
            Assert.assertEquals(FormatFlags, params.getFormatFlags());
        }
        // if nosz flag is not set, then the uncompressed size is recorded
        if (!params.getNosz()){
            Assert.assertEquals(Utils.readUint7(compressed), size);
        }
    }

    @Test(dataProvider = "rans4x8")
    public void testRans4x8Header(
            final RANS4x8Encode ransEncode,
            final RANS4x8Decode unused,
            final RANS4x8Params params) {
        final int size = 1000;
        final ByteBuffer data = ByteBuffer.wrap(randomBytesFromGeometricDistribution(size, 0.01));
        final ByteBuffer compressed = ransEncode.compress(data, params);
        // first byte of compressed data gives the order
        Assert.assertEquals(compressed.get(), (byte) params.getOrder().ordinal());
        // the next 4 bytes gives the compressed size
        Assert.assertEquals(compressed.getInt(), compressed.limit() - 9);
        // the next 4 bytes gives the uncompressed size
        Assert.assertEquals(compressed.getInt(), data.limit());
    }

    @Test(dataProvider = "ransNx16")
    public void testRansNx16Header(
            final RANSNx16Encode ransEncode,
            final RANSNx16Decode unused,
            final RANSNx16Params params) {
        final int size = 1000;
        final ByteBuffer data = ByteBuffer.wrap(randomBytesFromGeometricDistribution(size, 0.01));
        final ByteBuffer compressed = ransEncode.compress(data, params);
        // first byte of compressed data gives the formatFlags
        data.rewind();
        final int FormatFlags = compressed.get() & 0xFF; // first byte of compressed data is the formatFlags
        int numSym = 0;
        final int[] F = new int[Constants.NUMBER_OF_SYMBOLS];
        final int inSize = data.remaining();
        for (int i = 0; i < inSize; i ++) {
            F[data.get(i) & 0xFF]++;
        }
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            if (F[i]>0) {
                numSym++;
            }
        }
        if (params.getPack() & (numSym == 0 | numSym > 16)) {
            Assert.assertEquals(FormatFlags, (byte) (params.getFormatFlags() & ~RANSNx16Params.PACK_FLAG_MASK));
        } else {
            Assert.assertEquals(FormatFlags, (byte) params.getFormatFlags());
        }
        // if nosz flag is not set, then the uncompressed size is recorded
        if (!params.getNosz()){
            Assert.assertEquals(Utils.readUint7(compressed), size);
        }
    }

    @Test(dataProvider="allRansAndData")
    public void testRoundTrip(
            final RANSEncode ransEncode,
            final RANSDecode ransDecode,
            final RANSParams params,
            final TestDataEnvelope td) {
        ransRoundTrip(ransEncode, ransDecode, params, ByteBuffer.wrap(td.testArray));
    }

    @Test(
            dataProvider = "RansNx16DecodeOnlyAndData",
            expectedExceptions = { CRAMException.class },
            expectedExceptionsMessageRegExp = "RANSNx16 Encoding with Stripe Flag is not implemented.")
    public void testRansNx16EncodeStripe(
            final RANSNx16Encode ransEncode,
            final RANSNx16Decode unused,
            final RANSNx16Params params,
            final TestDataEnvelope td) {

        // When td is not Empty, Encoding with Stripe Flag should throw an Exception
        // as Encode Stripe is not implemented
        final ByteBuffer compressed = ransEncode.compress(ByteBuffer.wrap(td.testArray), params);
    }

    // TODO: Add Test to DecodePack with nsym > 16

    private static void ransRoundTrip(
            final RANSEncode ransEncode,
            final RANSDecode ransDecode,
            final RANSParams params,
            final ByteBuffer data) {
        final ByteBuffer compressed = ransEncode.compress(data, params);
        final ByteBuffer uncompressed = ransDecode.uncompress(compressed);
        data.rewind();
//        Assert.assertEquals(data, uncompressed);

        while (data.hasRemaining()) {
            if (!uncompressed.hasRemaining()) {
                Assert.fail("Premature end of uncompressed data.");
            }
            Assert.assertEquals(uncompressed.get(), data.get());
        }
        Assert.assertFalse(uncompressed.hasRemaining());
    }

    public ByteBuffer ransBufferMeetBoundaryExpectations(
            final int size,
            final ByteBuffer raw,
            final RANSEncode ransEncode,
            final RANSDecode ransDecode,
            final RANSParams params){
        // helper method for Boundary Expectations test
        final ByteBuffer compressed = ransEncode.compress(raw, params);
        final ByteBuffer uncompressed = ransDecode.uncompress(compressed);
        Assert.assertFalse(compressed.hasRemaining());
        compressed.rewind();
        Assert.assertEquals(uncompressed.limit(), size);
        Assert.assertEquals(uncompressed.position(), 0);
        Assert.assertFalse(raw.hasRemaining());
        Assert.assertEquals(raw.limit(), size);
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