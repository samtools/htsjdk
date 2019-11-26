package htsjdk.samtools.cram.compression.rans;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.TestUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.function.BiFunction;

/**
 * Created by vadim on 22/04/2015.
 */
public class RansTest extends HtsjdkTest {
    private Random random = new Random(TestUtil.RANDOM_SEED);

    // Since some of our test cases use very large byte arrays, so enclose them in a wrapper class since
    // otherwise IntelliJ serializes them to strings for display in the test output, which is *super*-slow.
    private static class TestCaseWrapper {
        public final byte[] testArray;
        public TestCaseWrapper(final byte[] testdata) {
            this.testArray = testdata;
        }
        public String toString() {
            return String.format("Array of size %d", testArray.length);
        }
    }

    @DataProvider(name="ransData")
    public Object[][] getRansTestData() {
        return new Object[][] {
                { new TestCaseWrapper(new byte[]{}) },
                { new TestCaseWrapper(new byte[] {0}) },
                { new TestCaseWrapper(new byte[] {0, 1}) },
                { new TestCaseWrapper(new byte[] {0, 1, 2}) },
                { new TestCaseWrapper(new byte[] {0, 1, 2, 3}) },
                { new TestCaseWrapper(new byte[1000]) },
                { new TestCaseWrapper(getNBytesWithValues(1000, (n, index) -> (byte) 1)) },
                { new TestCaseWrapper(getNBytesWithValues(1000, (n, index) -> Byte.MIN_VALUE)) },
                { new TestCaseWrapper(getNBytesWithValues(1000, (n, index) -> Byte.MAX_VALUE)) },
                { new TestCaseWrapper(getNBytesWithValues(1000, (n, index) -> (byte) index.intValue())) },
                { new TestCaseWrapper(getNBytesWithValues(1000, (n, index) -> index < n / 2 ? (byte) 0 : (byte) 1)) },
                { new TestCaseWrapper(getNBytesWithValues(1000, (n, index) -> index < n % 2 ? (byte) 0 : (byte) 1)) },
                { new TestCaseWrapper(randomBytesFromGeometricDistribution(1000, 0.1)) },
                { new TestCaseWrapper(randomBytesFromGeometricDistribution(1000, 0.01)) },
                { new TestCaseWrapper(randomBytesFromGeometricDistribution(10 * 1000 * 1000 + 1, 0.01)) },
        };
    }

    @Test(dataProvider="ransData")
    public void testRANS(final TestCaseWrapper tc) {
        roundTripForEachOrder(tc.testArray);
    }

    @Test
    public void testSizeRangeTiny() {
        for (int i = 0; i < 20; i++) {
            final byte[] data = randomBytesFromGeometricDistribution(100, 0.1);
            final ByteBuffer in = ByteBuffer.wrap(data);
            for (int size = 1; size < data.length; size++) {
                in.position(0);
                in.limit(size);
                roundTripForEachOrder(in);
            }
        }
    }

    @Test
    public void testSizeRangeSmall() {
        final byte[] data = randomBytesFromGeometricDistribution(1000, 0.01);
        final ByteBuffer in = ByteBuffer.wrap(data);
        for (int size = 4; size < data.length; size++) {
            in.position(0);
            in.limit(size);
            roundTripForEachOrder(in);
        }
    }

    @Test
    public void testLargeSize() {
        final int size = 100 * 1000 + 3;
        final byte[] data = randomBytesFromGeometricDistribution(size, 0.01);
        final ByteBuffer in = ByteBuffer.wrap(data);
        for (int limit = size - 4; limit < size; limit++) {
            in.position(0);
            in.limit(limit);
            roundTripForEachOrder(in);
        }
    }

    @Test
    public void testBuffersMeetBoundaryExpectations() {
        final int size = 1001;
        final ByteBuffer raw = ByteBuffer.wrap(randomBytesFromGeometricDistribution(size, 0.01));
        final RANS rans = new RANS();
        for (RANS.ORDER order : RANS.ORDER.values()) {
            final ByteBuffer compressed = rans.compress(raw, order);
            Assert.assertFalse(raw.hasRemaining());
            Assert.assertEquals(raw.limit(), size);

            Assert.assertEquals(compressed.position(), 0);
            Assert.assertTrue(compressed.limit() > 10);
            Assert.assertEquals(compressed.get(), (byte) order.ordinal());
            Assert.assertEquals(compressed.getInt(), compressed.limit() - 1 - 4 - 4);
            Assert.assertEquals(compressed.getInt(), size);
            compressed.rewind();

            final ByteBuffer uncompressed = rans.uncompress(compressed);
            Assert.assertFalse(compressed.hasRemaining());
            Assert.assertEquals(uncompressed.limit(), size);
            Assert.assertEquals(uncompressed.position(), 0);

            raw.rewind();
        }
    }

    @Test
    public void testRansHeader() {
        final byte[] data = randomBytesFromGeometricDistribution(1000, 0.01);
        final ByteBuffer compressed = new RANS().compress(ByteBuffer.wrap(data), RANS.ORDER.ZERO);
        Assert.assertEquals(compressed.get(), (byte) 0);
        Assert.assertEquals(compressed.getInt(), compressed.limit() - 9);
        Assert.assertEquals(compressed.getInt(), data.length);
    }

    private byte[] getNBytesWithValues(final int n, final BiFunction<Integer, Integer, Byte> valueForIndex) {
        final byte[] data = new byte[n];
        for (int i = 0; i < data.length; i++) {
            data[i] = valueForIndex.apply(n, i);
        }
        return data;
    }

    private static void roundTripForEachOrder(final ByteBuffer data) {
        for (RANS.ORDER order : RANS.ORDER.values()) {
            roundTripForOrder(data, order);
            data.rewind();
        }
    }

    private static void roundTripForEachOrder(final byte[] data) {
        for (RANS.ORDER order : RANS.ORDER.values()) {
            roundTripForOrder(data, order);
        }
    }

    private static void roundTripForOrder(final ByteBuffer data, final RANS.ORDER order) {
        final RANS rans = new RANS();
        final ByteBuffer compressed = rans.compress(data, order);
        final ByteBuffer uncompressed = rans.uncompress(compressed);
        data.rewind();
        while (data.hasRemaining()) {
            if (!uncompressed.hasRemaining()) {
                Assert.fail("Premature end of uncompressed data.");
            }
            Assert.assertEquals(uncompressed.get(), data.get());
        }
        Assert.assertFalse(uncompressed.hasRemaining());
    }

    private static void roundTripForOrder(final byte[] data, final RANS.ORDER order) {
        roundTripForOrder(ByteBuffer.wrap(data), order);
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
