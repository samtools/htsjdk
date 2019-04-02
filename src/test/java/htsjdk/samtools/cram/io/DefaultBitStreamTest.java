package htsjdk.samtools.cram.io;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.RuntimeEOFException;
import htsjdk.samtools.util.RuntimeIOException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

// tests DefaultBitInputStream and DefaultBitOutputStream

public class DefaultBitStreamTest extends HtsjdkTest {

    // test the many ways to write to a DefaultBitOutputStream

    // write(final boolean bit)
    // write(final boolean bit, final long repeat)
    // write(byte bitContainer, final int nofBits)
    // write(int bitContainer, final int nofBits)
    // write(long bitContainer, final int nofBits)
    // write(final int value)
    // write(final byte[] b)
    // write(final byte[] b, final int off, final int length)
    // write(final byte b)

    @Test
    public void allWrites() throws IOException {
        final byte writeByte = (byte) 0x83;
        final int writeInt = 0xD28518BB;
        final long writeLong = 0x79676DE07C980A0BL;
        final byte[] writeArray = new byte[] {
                1, 2, 3, 4, 5
        };

        // stored as ints so we can use the unsigned interpretation
        final int[] expected = new int[] {
                // 0101 + 1111 = 0x5F
                0x5F,
                // writeByte as-is:  0x83
                0x83,
                // writeInt as-is: 0xD2 85 18 BB
                0xD2, 0x85, 0x18, 0xBB,
                // writeLong as-is: 0x79 67 6D E0 7C 98 0A 0B
                0x79, 0x67, 0x6D, 0xE0, 0x7C, 0x98, 0x0A, 0x0B,
                // low 7 bits of 0x83 + low 23 bits of 0xD2 85 18 BB + low 34 bits of 0x79 67 6D E0 7C 98 0A 0B
                // 1[0000011] + 110100101[00001010001100010111011] + 11110010110011101101101111000[0001111100100110000000101000001011]
                // 0000011 + 0 + 0001010001100010111011 + 00 + 01111100100110000000101000001011
                0x06, 0x14, 0x62, 0xEC, 0x7C, 0x98, 0x0A, 0x0B,
                // the low byte of the int 0xD28518BB
                0xBB,
                // the byte 0x83 as-is
                0x83,
                // the array as-is
                1, 2, 3, 4, 5,
                // a subset of the array
                3, 4
        };

        final byte[] databuf = testWrite(dbos -> {
            // 0101
            dbos.write(false);
            dbos.write(true);
            dbos.write(false);
            dbos.write(true);
            // 1111
            dbos.write(true, 4);

            // as-is:  0x83 ... 0xD2 85 18 BB ... 0x79 67 6D E0 7C 98 0A 0B
            dbos.write(writeByte, 8);
            dbos.write(writeInt, 32);
            dbos.write(writeLong, 64);

            // nothing
            dbos.write(writeByte, 0);
            dbos.write(writeInt, 0);
            dbos.write(writeLong, 0);

            // low 7 bits of 0x83
            dbos.write(writeByte, 7);
            // low 23 bits of 0xD2 85 18 BB
            dbos.write(writeInt, 23);
            // low 34 bits of 0x79 67 6D E0 7C 98 0A 0B
            dbos.write(writeLong, 34);

            // the low byte of the int 0xD28518BB
            dbos.write(writeInt);
            // the byte 0x83 as-is
            dbos.write(writeByte);
            // the array as-is
            dbos.write(writeArray);
            // a subset of the array
            dbos.write(writeArray, 2, 2);

            dbos.flush();
        });

        testRead(databuf, dbis -> {
            // stored as ints so we can use the unsigned interpretation
            for (int expectedUnsignedByte : expected) {
                Assert.assertEquals(readByte(dbis), (byte) expectedUnsignedByte);
            }
        });
    }

    private byte readByte(final BitInputStream bis) {
        return (byte) bis.readBits(8);
    }

    @Test(expectedExceptions = RuntimeIOException.class)
    public void tooManyBitsByte() throws IOException {
        final byte value = 0;
        testWrite(dbos -> dbos.write(value, 9));
    }

    @Test(expectedExceptions = RuntimeIOException.class)
    public void tooManyBitsInt() throws IOException {
        final int value = 0;
        testWrite(dbos -> dbos.write(value, 33));
    }

    @Test(expectedExceptions = RuntimeIOException.class)
    public void tooManyBitsLong() throws IOException {
        final long value = 0;
        testWrite(dbos -> dbos.write(value, 65));
    }

    @Test(expectedExceptions = RuntimeIOException.class)
    public void tooFewBitsByte() throws IOException {
        final byte value = 0;
        testWrite(dbos -> dbos.write(value, -1));
    }

    @Test(expectedExceptions = RuntimeIOException.class)
    public void tooFewBitsInt() throws IOException {
        final int value = 0;
        testWrite(dbos -> dbos.write(value, -1));
    }

    @Test(expectedExceptions = RuntimeIOException.class)
    public void tooFewBitsLong() throws IOException {
        final long value = 0;
        testWrite(dbos -> dbos.write(value, -1));
    }

    @Test(expectedExceptions = RuntimeEOFException.class)
    public void readBitEOF() throws IOException {
        testRead(new byte[0], DefaultBitInputStream::readBit);
    }

    @Test(expectedExceptions = RuntimeEOFException.class)
    public void readBitsEOF() throws IOException {
        testRead(new byte[0], dbis -> dbis.readBits(1));
    }

    // don't throw when requesting 0 bits and DefaultBitInputStream is empty

    @Test
    public void readZeroFromEmpty() throws IOException {
        testRead(new byte[0], dbis ->  {
            final int zero = dbis.readBits(0);
            Assert.assertEquals(zero, 0);
        });
    }

    // we use a -1 sentinel internally to indicate EOF.  make sure we can read -1 normally

    @Test
    public void readBitNoEOF() throws IOException {
        final byte value = -1;

        byte[] databuf = testWrite(dbos -> {
            dbos.write(value);
        });

        testRead(databuf, dbis -> {
            for (int i = 0; i < 8; i++) {
                Assert.assertTrue(dbis.readBit());
            }
        });
    }

    @Test
    public void readBitsNoEOF() throws IOException {
        final byte value = -1;

        byte[] databuf = testWrite(dbos -> {
            dbos.write(value);
        });

        testRead(databuf, dbis -> {
            final byte check = (byte) dbis.readBits(8);
            Assert.assertEquals(check, value);
        });
    }

    // DefaultBitInputStream.reset() mostly delegates but also has some internal logic

    @Test
    public void markAndReset() throws IOException {
        final byte value = 123;

        byte[] databuf = testWrite(dbos -> {
            dbos.write(value);
        });

        testRead(databuf, dbis -> {
            Assert.assertTrue(dbis.markSupported());
            dbis.mark(1);

            final byte check = (byte) dbis.readBits(8);
            Assert.assertEquals(check, value);

            dbis.reset();
            final byte checkAgain = (byte) dbis.readBits(8);
            Assert.assertEquals(checkAgain, value);
        });
    }

    private interface TestWriter {
        void write(final DefaultBitOutputStream dbos);
    }

    private byte[] testWrite(final TestWriter writer) throws IOException {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
             final DefaultBitOutputStream dbos = new DefaultBitOutputStream(baos)) {
            writer.write(dbos);
            return baos.toByteArray();
        }
    }

    private interface TestReader {
        void read(final DefaultBitInputStream dbis);
    }

    private void testRead(final byte[] source, final TestReader reader) throws IOException {
        try (final ByteArrayInputStream bais = new ByteArrayInputStream(source);
             final DefaultBitInputStream dbis = new DefaultBitInputStream(bais)) {
            reader.read(dbis);
        }
    }

}
