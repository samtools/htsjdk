package htsjdk.samtools.cram.io;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CRAMByteReaderWriterTest extends HtsjdkTest {

    // ========== CRAMByteWriter tests ==========

    @Test
    public void testWriteSingleBytes() {
        final CRAMByteWriter writer = new CRAMByteWriter();
        writer.write(0x00);
        writer.write(0x7F);
        writer.write(0xFF);

        final byte[] result = writer.toByteArray();
        Assert.assertEquals(result.length, 3);
        Assert.assertEquals(result[0], (byte) 0x00);
        Assert.assertEquals(result[1], (byte) 0x7F);
        Assert.assertEquals(result[2], (byte) 0xFF);
    }

    @Test
    public void testWriteOnlyUsesLow8Bits() {
        final CRAMByteWriter writer = new CRAMByteWriter();
        writer.write(0x1AB); // only 0xAB should be stored
        final byte[] result = writer.toByteArray();
        Assert.assertEquals(result[0], (byte) 0xAB);
    }

    @Test
    public void testWriteFullByteArray() {
        final byte[] data = {10, 20, 30, 40, 50};
        final CRAMByteWriter writer = new CRAMByteWriter();
        writer.write(data);

        Assert.assertEquals(writer.toByteArray(), data);
    }

    @Test
    public void testWriteByteArrayWithOffsetAndLength() {
        final byte[] data = {10, 20, 30, 40, 50};
        final CRAMByteWriter writer = new CRAMByteWriter();
        writer.write(data, 1, 3);

        final byte[] result = writer.toByteArray();
        Assert.assertEquals(result, new byte[]{20, 30, 40});
    }

    @Test
    public void testWritePastInitialCapacityTriggersGrowth() {
        final CRAMByteWriter writer = new CRAMByteWriter(4);
        final byte[] expected = new byte[100];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) (i & 0xFF);
            writer.write(i);
        }

        Assert.assertEquals(writer.toByteArray(), expected);
    }

    @Test
    public void testWriteLargeArrayPastCapacity() {
        final CRAMByteWriter writer = new CRAMByteWriter(8);
        final byte[] data = new byte[500];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 3);
        }
        writer.write(data);

        Assert.assertEquals(writer.toByteArray(), data);
    }

    @Test
    public void testResetClearsAndAllowsReuse() {
        final CRAMByteWriter writer = new CRAMByteWriter();
        writer.write(new byte[]{1, 2, 3});
        Assert.assertEquals(writer.size(), 3);

        writer.reset();
        Assert.assertEquals(writer.size(), 0);
        Assert.assertEquals(writer.toByteArray(), new byte[0]);

        // Write new data after reset
        writer.write(new byte[]{4, 5});
        Assert.assertEquals(writer.toByteArray(), new byte[]{4, 5});
    }

    @Test
    public void testSizeReturnsCorrectCount() {
        final CRAMByteWriter writer = new CRAMByteWriter();
        Assert.assertEquals(writer.size(), 0);

        writer.write(42);
        Assert.assertEquals(writer.size(), 1);

        writer.write(new byte[]{1, 2, 3});
        Assert.assertEquals(writer.size(), 4);

        writer.write(new byte[]{10, 20, 30, 40, 50}, 2, 2);
        Assert.assertEquals(writer.size(), 6);
    }

    @Test
    public void testGetPositionMatchesSize() {
        final CRAMByteWriter writer = new CRAMByteWriter();
        writer.write(new byte[]{1, 2, 3, 4, 5});
        Assert.assertEquals(writer.getPosition(), writer.size());
        Assert.assertEquals(writer.getPosition(), 5);
    }

    @Test
    public void testToByteArrayReturnsCopy() {
        final CRAMByteWriter writer = new CRAMByteWriter();
        writer.write(new byte[]{1, 2, 3});

        final byte[] a = writer.toByteArray();
        final byte[] b = writer.toByteArray();
        Assert.assertEquals(a, b);

        // Mutating the returned array should not affect subsequent calls
        a[0] = 99;
        final byte[] c = writer.toByteArray();
        Assert.assertEquals(c[0], (byte) 1);
    }

    // ========== CRAMByteReader tests ==========

    @Test
    public void testReadSingleBytes() {
        final byte[] data = {(byte) 0x00, (byte) 0x7F, (byte) 0xFF};
        final CRAMByteReader reader = new CRAMByteReader(data);

        Assert.assertEquals(reader.read(), 0x00);
        Assert.assertEquals(reader.read(), 0x7F);
        Assert.assertEquals(reader.read(), 0xFF); // unsigned: 255, not -1
    }

    @Test
    public void testReadReturnsMinusOneAtEnd() {
        final CRAMByteReader reader = new CRAMByteReader(new byte[]{42});
        Assert.assertEquals(reader.read(), 42);
        Assert.assertEquals(reader.read(), -1);
        Assert.assertEquals(reader.read(), -1); // repeated calls stay at -1
    }

    @Test
    public void testReadFromEmptyBuffer() {
        final CRAMByteReader reader = new CRAMByteReader(new byte[0]);
        Assert.assertEquals(reader.read(), -1);
    }

    @Test
    public void testReadIntoArrayWithOffsetAndLength() {
        final byte[] data = {10, 20, 30, 40, 50};
        final CRAMByteReader reader = new CRAMByteReader(data);

        final byte[] dest = new byte[10];
        final int bytesRead = reader.read(dest, 2, 3);

        Assert.assertEquals(bytesRead, 3);
        Assert.assertEquals(dest[0], (byte) 0); // untouched
        Assert.assertEquals(dest[1], (byte) 0); // untouched
        Assert.assertEquals(dest[2], (byte) 10);
        Assert.assertEquals(dest[3], (byte) 20);
        Assert.assertEquals(dest[4], (byte) 30);
        Assert.assertEquals(dest[5], (byte) 0); // untouched
    }

    @Test
    public void testReadIntoArrayReturnsMinusOneAtEnd() {
        final CRAMByteReader reader = new CRAMByteReader(new byte[0]);
        final byte[] dest = new byte[5];
        Assert.assertEquals(reader.read(dest, 0, 5), -1);
    }

    @Test
    public void testReadIntoArrayReadsLessThanRequestedAtEnd() {
        final byte[] data = {1, 2, 3};
        final CRAMByteReader reader = new CRAMByteReader(data);

        final byte[] dest = new byte[10];
        final int bytesRead = reader.read(dest, 0, 10);

        Assert.assertEquals(bytesRead, 3);
        Assert.assertEquals(dest[0], (byte) 1);
        Assert.assertEquals(dest[1], (byte) 2);
        Assert.assertEquals(dest[2], (byte) 3);
    }

    @Test
    public void testReadFullyReturnsCorrectBytes() {
        final byte[] data = {10, 20, 30, 40, 50};
        final CRAMByteReader reader = new CRAMByteReader(data);

        final byte[] first = reader.readFully(3);
        Assert.assertEquals(first, new byte[]{10, 20, 30});

        final byte[] second = reader.readFully(2);
        Assert.assertEquals(second, new byte[]{40, 50});
    }

    @Test
    public void testReadFullyZeroBytes() {
        final CRAMByteReader reader = new CRAMByteReader(new byte[]{1, 2});
        final byte[] result = reader.readFully(0);
        Assert.assertEquals(result.length, 0);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testReadFullyPastEndThrows() {
        final byte[] data = {1, 2, 3};
        final CRAMByteReader reader = new CRAMByteReader(data);
        reader.readFully(4); // only 3 bytes available
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testReadFullyPartiallyConsumedThenPastEndThrows() {
        final byte[] data = {1, 2, 3, 4, 5};
        final CRAMByteReader reader = new CRAMByteReader(data);
        reader.readFully(3); // consume 3, leaving 2
        reader.readFully(3); // request 3 but only 2 remain
    }

    @Test
    public void testAvailableReturnsCorrectRemainingCount() {
        final byte[] data = {1, 2, 3, 4, 5};
        final CRAMByteReader reader = new CRAMByteReader(data);

        Assert.assertEquals(reader.available(), 5);
        reader.read();
        Assert.assertEquals(reader.available(), 4);
        reader.readFully(2);
        Assert.assertEquals(reader.available(), 2);
        reader.read(new byte[10], 0, 10); // reads remaining 2
        Assert.assertEquals(reader.available(), 0);
    }

    @Test
    public void testGetPositionTracksCorrectly() {
        final byte[] data = {10, 20, 30, 40, 50};
        final CRAMByteReader reader = new CRAMByteReader(data);

        Assert.assertEquals(reader.getPosition(), 0);
        reader.read();
        Assert.assertEquals(reader.getPosition(), 1);
        reader.read(new byte[2], 0, 2);
        Assert.assertEquals(reader.getPosition(), 3);
        reader.readFully(2);
        Assert.assertEquals(reader.getPosition(), 5);
    }

    @Test
    public void testGetBufferReturnsUnderlyingArray() {
        final byte[] data = {1, 2, 3};
        final CRAMByteReader reader = new CRAMByteReader(data);

        // getBuffer() should return the same array instance, not a copy
        Assert.assertSame(reader.getBuffer(), data);
    }

    // ========== Round-trip tests ==========

    @Test
    public void testRoundTripSingleBytes() {
        final CRAMByteWriter writer = new CRAMByteWriter();
        for (int i = 0; i < 256; i++) {
            writer.write(i);
        }

        final CRAMByteReader reader = new CRAMByteReader(writer.toByteArray());
        for (int i = 0; i < 256; i++) {
            Assert.assertEquals(reader.read(), i, "Mismatch at byte value " + i);
        }
        Assert.assertEquals(reader.read(), -1);
    }

    @Test
    public void testRoundTripByteArrays() {
        final byte[] chunk1 = {1, 2, 3, 4, 5};
        final byte[] chunk2 = {100, (byte) 200, 0, (byte) 255};

        final CRAMByteWriter writer = new CRAMByteWriter();
        writer.write(chunk1);
        writer.write(chunk2);

        final CRAMByteReader reader = new CRAMByteReader(writer.toByteArray());
        Assert.assertEquals(reader.readFully(chunk1.length), chunk1);
        Assert.assertEquals(reader.readFully(chunk2.length), chunk2);
        Assert.assertEquals(reader.available(), 0);
    }

    @Test
    public void testRoundTripMixedWritesAndReads() {
        final CRAMByteWriter writer = new CRAMByteWriter(16);
        writer.write(0xAB);
        writer.write(new byte[]{10, 20, 30});
        writer.write(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, 3, 4); // writes {3,4,5,6}
        writer.write(0xFF);

        final byte[] written = writer.toByteArray();
        Assert.assertEquals(written.length, 9);

        final CRAMByteReader reader = new CRAMByteReader(written);
        Assert.assertEquals(reader.read(), 0xAB);
        Assert.assertEquals(reader.readFully(3), new byte[]{10, 20, 30});

        final byte[] subset = new byte[4];
        Assert.assertEquals(reader.read(subset, 0, 4), 4);
        Assert.assertEquals(subset, new byte[]{3, 4, 5, 6});

        Assert.assertEquals(reader.read(), 0xFF);
        Assert.assertEquals(reader.available(), 0);
    }

    @Test
    public void testRoundTripEmpty() {
        final CRAMByteWriter writer = new CRAMByteWriter();
        final byte[] data = writer.toByteArray();
        Assert.assertEquals(data.length, 0);

        final CRAMByteReader reader = new CRAMByteReader(data);
        Assert.assertEquals(reader.available(), 0);
        Assert.assertEquals(reader.read(), -1);
    }

    @Test
    public void testRoundTripLargeData() {
        final int size = 10_000;
        final CRAMByteWriter writer = new CRAMByteWriter(32); // small initial capacity
        final byte[] expected = new byte[size];
        for (int i = 0; i < size; i++) {
            expected[i] = (byte) ((i * 7 + 13) & 0xFF);
        }
        writer.write(expected);

        final CRAMByteReader reader = new CRAMByteReader(writer.toByteArray());
        Assert.assertEquals(reader.available(), size);

        final byte[] result = reader.readFully(size);
        Assert.assertEquals(result, expected);
        Assert.assertEquals(reader.available(), 0);
    }
}
