package htsjdk.samtools.cram.compression.rans;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.rans.rans4x8.RANS4x8Decode;
import htsjdk.samtools.cram.compression.rans.rans4x8.RANS4x8Encode;
import htsjdk.samtools.cram.compression.rans.rans4x8.RANS4x8Params;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Decode;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Encode;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Params;
import htsjdk.samtools.util.TestUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.util.Random;

public class RansOrder1DemoTest extends HtsjdkTest {
    private Random random = new Random(TestUtil.RANDOM_SEED);

    private static class TestDataEnvelope {
        public final byte[] testArray;
        public TestDataEnvelope(final byte[] testdata) {
            this.testArray = testdata;
        }
        public String toString() {
            return String.format("Array of size %d", testArray.length);
        }
    }



    @DataProvider(name="testDP")
    public Object[][] getRansTestData() {
        return new Object[][]{
                {new TestDataEnvelope(new byte[]{'h','e','e','e','e','l','l','l','o',})},
                { new TestDataEnvelope(new byte[] {0}) },
                {new TestDataEnvelope(new byte[]{2,101,108,3,2})},
//
                {new TestDataEnvelope(new byte[]{'h','e','e','e','e','e','e','e','e','e','e',
                        'l','l','l','l','l','l','l','l','l','l','l','l','o',})},
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(100, 0.1)) },
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(1000, 0.01))}, // Small
                { new TestDataEnvelope(randomBytesFromGeometricDistribution(100 * 1000 + 3, 0.01))} // Large

        };
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


    @Test(enabled = false,dataProvider = "testDP")
    public void testRansNx16BuffersMeetBoundaryExpectations(
            final TestDataEnvelope td) {
        RANSEncode ransEncode = new RANSNx16Encode();
        RANSDecode ransDecode = new RANSNx16Decode();
//        RANSParams ransParams = new RANSNx16Params(0x40);// format = 64 (rle = 1, order = 0)
//        RANSParams ransParams = new RANSNx16Params(0x41);// format = 65 (rle = 1, order = 1)
        RANSParams ransParams = new RANSNx16Params(0x40);// format = 128 (pack = 1, order = 0)
//
//        RANSParams ransParams = new RANSNx16Params(0x00);

//        // if we comment f++, t++ then this fails as expected with Buffer Underflow Exception
//        // Next step -> make Xmax and dependent variables to long and try
//        RANSEncode ransEncode = new RANS4x8Encode();
//        RANSDecode ransDecode = new RANS4x8Decode();
//        RANSParams ransParams = new RANS4x8Params(RANSParams.ORDER.ZERO);

        ByteBuffer inputData = ByteBuffer.wrap(td.testArray);
        final ByteBuffer outBuffer = ransEncode.compress(inputData,ransParams);

        ByteBuffer uncompressed = ransDecode.uncompress(outBuffer);
        // TODO: where is comp Flag -> freq first byte being written??
        inputData.rewind();
        Assert.assertEquals(inputData,uncompressed);
    }


    @Test(enabled = false,dataProvider = "testDP")
    public void testRansNx16Tiny(
            final TestDataEnvelope td) {
        RANSEncode ransEncode = new RANSNx16Encode();
        RANSDecode ransDecode = new RANSNx16Decode();
        RANSParams ransParams = new RANSNx16Params(0x05);
//        ByteBuffer inputData = ByteBuffer.wrap(td.testArray);
//
//        final ByteBuffer outBuffer = ransEncode.compress(inputData,ransParams);
//
//        ByteBuffer uncompressed = ransDecode.uncompress(outBuffer);
//        inputData.rewind();
//        Assert.assertEquals(inputData,uncompressed);
        final ByteBuffer in = ByteBuffer.wrap(td.testArray);
        for (int size = 1; size < 100; size++) {
            in.position(0);
            in.limit(size);
            final ByteBuffer compressed = ransEncode.compress(in, ransParams);
            final ByteBuffer uncompressed = ransDecode.uncompress(compressed);
            in.rewind();
            while (in.hasRemaining()) {
                if (!uncompressed.hasRemaining()) {
                    Assert.fail("Premature end of uncompressed data.");
                }
                Assert.assertEquals(uncompressed.get(), in.get());
            }
            Assert.assertFalse(uncompressed.hasRemaining());
        }
    }


}