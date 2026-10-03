package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class UnsynchronizedByteArrayOutputStreamTest extends HtsjdkTest {

    @Test
    public void toByteArrayReturnsTheBytesWrittenInOrder() {
        final UnsynchronizedByteArrayOutputStream out = new UnsynchronizedByteArrayOutputStream(16);
        out.write(1);
        out.write(new byte[] {2, 3, 4}, 0, 3);
        out.write(5);
        Assert.assertEquals(out.toByteArray(), new byte[] {1, 2, 3, 4, 5});
        Assert.assertEquals(out.size(), 5);
    }

    @Test
    public void writeKeepsOnlyTheLowByteOfAnInt() {
        final UnsynchronizedByteArrayOutputStream out = new UnsynchronizedByteArrayOutputStream(1);
        out.write(0x1234);
        out.write(-1);
        Assert.assertEquals(out.toByteArray(), new byte[] {0x34, (byte) 0xFF});
    }

    @Test
    public void singleBytesGrowTheBufferPastItsInitialCapacity() {
        final UnsynchronizedByteArrayOutputStream out = new UnsynchronizedByteArrayOutputStream(0);
        final byte[] expected = new byte[1000];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) i;
            out.write(i);
        }
        Assert.assertEquals(out.toByteArray(), expected);
    }

    @Test
    public void anArrayLargerThanTwiceTheCapacityIsWrittenWhole() {
        final UnsynchronizedByteArrayOutputStream out = new UnsynchronizedByteArrayOutputStream(2);
        out.write(9);
        final byte[] large = new byte[100];
        for (int i = 0; i < large.length; i++) large[i] = (byte) (i + 1);
        out.write(large, 0, large.length);
        Assert.assertEquals(out.size(), 101);
        Assert.assertEquals(out.toByteArray()[0], 9);
        Assert.assertEquals(out.toByteArray()[100], 100);
    }

    @Test
    public void writeOfASliceCopiesOnlyThatSlice() {
        final UnsynchronizedByteArrayOutputStream out = new UnsynchronizedByteArrayOutputStream(4);
        out.write(new byte[] {1, 2, 3, 4, 5}, 1, 3);
        Assert.assertEquals(out.toByteArray(), new byte[] {2, 3, 4});
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class)
    public void writeOfASliceBeyondTheArrayIsRejected() {
        new UnsynchronizedByteArrayOutputStream(4).write(new byte[] {1, 2, 3}, 2, 2);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void aNegativeInitialCapacityIsRejected() {
        new UnsynchronizedByteArrayOutputStream(-1);
    }

    @Test
    public void resetDiscardsTheBytesWrittenSoFar() {
        final UnsynchronizedByteArrayOutputStream out = new UnsynchronizedByteArrayOutputStream(4);
        out.write(new byte[] {1, 2, 3}, 0, 3);
        out.reset();
        out.write(7);
        Assert.assertEquals(out.toByteArray(), new byte[] {7});
        Assert.assertEquals(out.size(), 1);
    }

    @Test
    public void toByteArrayReturnsACopyThatLaterWritesDoNotChange() {
        final UnsynchronizedByteArrayOutputStream out = new UnsynchronizedByteArrayOutputStream(4);
        out.write(1);
        final byte[] before = out.toByteArray();
        out.reset();
        out.write(2);
        Assert.assertEquals(before, new byte[] {1});
    }
}
