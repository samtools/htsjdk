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

public class GammaIntegerCodecTest extends HtsjdkTest {

    private void testCodec(final int offset, final int[] inputs, final byte[] expected) throws IOException {
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream();
             final BitOutputStream bos = new DefaultBitOutputStream(os)) {

            final CRAMCodec<Integer> writeCodec = new GammaIntegerCodec(null, bos, offset);
            for (final int value : inputs) {
                writeCodec.write(value);
            }

            bos.flush();
            final byte[] writtenOut = os.toByteArray();
            Assert.assertEquals(writtenOut, expected);

            final int[] readBack = new int[inputs.length];
            try (final InputStream is = new ByteArrayInputStream(writtenOut);
                 final DefaultBitInputStream dbis = new DefaultBitInputStream(is)) {

                final CRAMCodec<Integer> readCodec = new GammaIntegerCodec(dbis, null, offset);
                for (int i = 0; i < inputs.length; i++) {
                    readBack[i] = readCodec.read();
                }
            }

            Assert.assertEquals(readBack, inputs);
        }
    }

    // test that the offsets result in a strictly positive data series
    // and the codec behaves as we expect

    @DataProvider(name = "basicTest")
    public Object[][] basicTest() {
        return new Object[][] {
                {-100,
                        new int[]{101, 102, 200, 12345},

                        // 1 -> 1
                        // 2 -> 010
                        // 100 = 0x64 -> 0000001100100
                        // 12245 = 0x2FD5 -> 000000000000010111111010101
                        // 1010 0000 0011 0010 0000 0000 0000 0010 1111 1101 0101 = A0 32 00 02 FD 5
                        new byte[]{(byte) 0xA0, 0x32, 0, 0x02, (byte) 0xFD, 0x50}
                },
                {10015,
                        new int[]{-10014, -10001, -10000},

                        // 1 -> 1
                        // 14 -> 0001110
                        // 15 -> 0001111
                        // 1000 1110 0001 111 = 8E 1E
                        new byte[]{(byte) 0x8E, 0x1E}
                        },
        };
    }

    @Test(dataProvider = "basicTest")
    public void basicTest(final int offset, final int[] values, final byte[] expected) throws IOException {
        testCodec(offset, values, expected);
    }

    // throw Exceptions when offsets + values are negative or zero

    @DataProvider(name = "negativeTest")
    public Object[][] negativeTestData() {
        // tuples of bitsPerValue and offsets + values which are negative/zero
        return new Object[][] {
                {0, 0},
                {0, -1},
                {1, -1},
                {-1, 1},
                {-1, 0},
                {0, -1},
                {-1, 0},
                {-100, 99},
                {99, -100},
        };
    }

    @Test(dataProvider = "negativeTest", expectedExceptions = IllegalArgumentException.class)
    public void negativeTest(final int offset, final int value) throws IOException {
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream();
             final BitOutputStream bos = new DefaultBitOutputStream(os)) {

            final CRAMCodec<Integer> codec = new GammaIntegerCodec(null, bos, offset);
            codec.write(value);
        }
    }
}