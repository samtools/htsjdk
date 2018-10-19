package htsjdk.samtools.cram.encoding;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.io.DefaultBitInputStream;
import htsjdk.samtools.cram.io.DefaultBitOutputStream;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;

public class BetaIntegerCodecTest extends HtsjdkTest {

    // Can't create a BetaIntegerCodec where readNofBits = 0

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void n0test() {
        new BetaIntegerCodec(0, 0);
    }

    @DataProvider(name = "tooManyBits")
    public Object[][] tooManyBits() {
        // tuples of readNofBits and offsets + values which are too big to store
        return new Object[][] {
                // first with zero offset
                {1, 0, (1 << 1)},
                {2, 0, (1 << 2)},
                {4, 0, (1 << 4)},
                {8, 0, (1 << 8)},
                {16, 0, (1 << 16)},

                // adding offset of 1 will put it over
                {1, 1, (1 << 1) - 1},
                {2, 1, (1 << 2) - 1},
                {4, 1, (1 << 4) - 1},
                {8, 1, (1 << 8) - 1},
                {16, 1, (1 << 16) - 1},
        };
    }

    @Test(dataProvider = "tooManyBits", expectedExceptions = IllegalArgumentException.class)
    public void tooManyBitsTest(int readNofBits, int offset, int value) throws IOException {
        BitCodec<Integer> codec = new BetaIntegerCodec(offset, readNofBits);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            try (BitOutputStream bos = new DefaultBitOutputStream(os)) {
                codec.write(bos, value);
            }
        }
    }

    @DataProvider(name = "testNbits")
    public Object[][] testNbitsData() {
        return new Object[][] {
                {8, new int[]{0, 1, 2, 100, 255}},
                {16, new int[]{0, 1, 255, 65535}},
        };
    }

    @Test(dataProvider = "testNbits")
    public void testNbits(int nBits, int[] values) throws IOException {
        BitCodec<Integer> codec = new BetaIntegerCodec(0, nBits);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream();
             BitOutputStream bos = new DefaultBitOutputStream(os)) {

            for (int value : values) {
                codec.write(bos, value);
            }

            int[] actual = new int[values.length];
            try (InputStream is = new ByteArrayInputStream(os.toByteArray());
                 DefaultBitInputStream dbis = new DefaultBitInputStream(is)) {

                for (int i = 0; i < values.length; i++) {
                    actual[i] = codec.read(dbis);
                }
            }

            Assert.assertEquals(actual, values);
        }
    }

}