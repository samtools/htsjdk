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

    // test that the offsets enable the data series to be stored in N bits

    @DataProvider(name = "basicTest")
    public Object[][] basicTestData() {
        return new Object[][] {
                {8, -100, new int[]{100, 101, 102, (1<<8) + 98, (1<<8) + 99}},
                {4, 10015, new int[]{-10015, -10014, -10001, -10000}},
        };
    }

    @Test(dataProvider = "basicTest")
    public void basicTest(int bitsPerValue, int offset, int[] values) throws IOException {
        BitCodec<Integer> codec = new BetaIntegerCodec(offset, bitsPerValue);

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

    // test that values fit into N bits without offsets

    @DataProvider(name = "basicTestNoOffset")
    public Object[][] basicTestNoOffsetData() {
        return new Object[][] {
                {8, new int[]{0, 1, 2, 100, (1 << 8) - 2, (1 << 8) - 1}},
                {16, new int[]{0, 1, 255, (1 << 16) - 2, (1 << 16) - 1}},
        };
    }

    @Test(dataProvider = "basicTestNoOffset")
    public void basicTestNoOffset(int bitsPerValue, int[] values) throws IOException {
        BitCodec<Integer> codec = new BetaIntegerCodec(0, bitsPerValue);

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

    // sanity checks for bitsPerValue.  Must be > 0 and <= 32

    @DataProvider(name = "bitsPerValue")
    public Object[][] bitsPerValueData() {
        return new Object[][] {
                {0},
                {-1},
                {33}
        };
    }

    @Test(dataProvider = "bitsPerValue", expectedExceptions = IllegalArgumentException.class)
    public void bitsPerValue(int bitsPerValue) {
        new BetaIntegerCodec(0, bitsPerValue);
    }

    // throw Exceptions when offsets + values are too big to store in N bits

    @DataProvider(name = "overflow")
    public Object[][] overflowData() {
        // tuples of bitsPerValue and offsets + values which are too big to store
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

    @Test(dataProvider = "overflow", expectedExceptions = IllegalArgumentException.class)
    public void overflow(int bitsPerValue, int offset, int value) throws IOException {
        BitCodec<Integer> codec = new BetaIntegerCodec(offset, bitsPerValue);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream();
             BitOutputStream bos = new DefaultBitOutputStream(os)) {
            codec.write(bos, value);
        }
    }

    // throw Exceptions when offsets + values are negative

    @DataProvider(name = "negativeTest")
    public Object[][] negativeTestData() {
        // tuples of bitsPerValue and offsets + values which are negative
        return new Object[][] {
                {1, 0, -1},
                {1, -1, 0},
                {2, 0, -1},
                {2, -1, 0},
                {8, -100, 99},
                {8, 99, -100},
        };
    }

    @Test(dataProvider = "negativeTest", expectedExceptions = IllegalArgumentException.class)
    public void negativeTest(int bitsPerValue, int offset, int value) throws IOException {
        BitCodec<Integer> codec = new BetaIntegerCodec(offset, bitsPerValue);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream();
             BitOutputStream bos = new DefaultBitOutputStream(os)) {
            codec.write(bos, value);
        }
    }
}