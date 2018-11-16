package htsjdk.samtools.cram.encoding.core;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.io.DefaultBitInputStream;
import htsjdk.samtools.cram.io.DefaultBitOutputStream;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class SubexponentialIntegerCodecTest extends HtsjdkTest {

    private void testCodec(final int offset, final int k, final int[] values, final byte[] expected) throws IOException {
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream();
             final BitOutputStream bos = new DefaultBitOutputStream(os)) {

            final CRAMCodec<Integer> writeCodec = new SubexponentialIntegerCodec(null, bos, offset, k);
            for (final int value : values) {
                writeCodec.write(value);
            }

            bos.flush();
            final byte[] writtenOut = os.toByteArray();
            Assert.assertEquals(writtenOut, expected);

            final int[] readBack = new int[values.length];
            try (final InputStream is = new ByteArrayInputStream(writtenOut);
                 final DefaultBitInputStream dbis = new DefaultBitInputStream(is)) {

                final CRAMCodec<Integer> readCodec = new SubexponentialIntegerCodec(dbis, null, offset, k);
                for (int i = 0; i < values.length; i++) {
                    readBack[i] = readCodec.read();
                }
            }

            Assert.assertEquals(readBack, values);
        }
    }
    
    // test that the offsets result in a non-negative data series
    // and the codec behaves as we expect

    @DataProvider(name = "basicTest")
    public Object[][] basicTestData() {
        return new Object[][] {
                {-100, 0,
                        new int[]{100, 101, 102, 104, 107, 108},

                        // with k = 0:
                        // 0 -> b = 0, u = 0 -> 0
                        // 1 -> b = 0, u = 1 -> 10
                        // 2 -> b = 1, u = 2 -> 1100
                        // 4 -> b = 2, u = 3 -> 111000
                        // 7 -> b = 2, u = 3 -> 111011
                        // 8 -> b = 3, u = 4 -> 11110000
                        // 0101 1001 1100 0111 0111 1110 000 = 59 C7 7E 0
                        new byte[]{0x59, (byte) 0xC7, 0x7E, 0}
                },
                {10015, 2,
                        new int[]{-10015, -10014, -10013, -10011, -10008, -10007},

                        // with k = 2:
                        // 0 -> b = 2, u = 0 -> 000
                        // 1 -> b = 2, u = 0 -> 001
                        // 2 -> b = 2, u = 0 -> 010
                        // 4 -> b = 2, u = 1 -> 1000
                        // 7 -> b = 2, u = 1 -> 1011
                        // 8 -> b = 3, u = 2 -> 110000
                        // 0000 0101 0100 0101 1110 000 = 05 45 E0
                        new byte[]{0x05, 0x45, (byte) 0xE0}
                },
        };
    }

    @Test(dataProvider = "basicTest")
    public void basicTest(final int offset, final int k, final int[] values, final byte[] expected) throws IOException {
        testCodec(offset, k, values, expected);
    }

    // throw Exceptions when offsets + values are negative

    @DataProvider(name = "negativeTest")
    public Object[][] negativeTestData() {
        // tuples of bitsPerValue and offsets + values which are negative
        return new Object[][] {
                {0, -1},
                {-1, 0},
                {0, -1},
                {-1, 0},
                {-100, 99},
                {99, -100},
        };
    }

    @Test(dataProvider = "negativeTest", expectedExceptions = IllegalArgumentException.class)
    public void negativeTest(final int offset, final int value) throws IOException {
        final int k = 0;

        try (final ByteArrayOutputStream os = new ByteArrayOutputStream();
             final BitOutputStream bos = new DefaultBitOutputStream(os)) {

            final CRAMCodec<Integer> codec = new SubexponentialIntegerCodec(null, bos, offset, k);
            codec.write(value);
        }
    }
}