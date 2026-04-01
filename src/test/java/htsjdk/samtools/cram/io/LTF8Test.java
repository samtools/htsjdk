package htsjdk.samtools.cram.io;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.RuntimeEOFException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.util.List;

/**
 * Created by vadim on 03/02/2015.
 */
public class LTF8Test extends HtsjdkTest {

    @Test(dataProvider = "testInt64Lists", dataProviderClass = IOTestCases.class)
    public void testITF8Stream(List<Long> longs) throws IOException {
        try (ByteArrayOutputStream ltf8TestBAOS = new ByteArrayOutputStream()) {
            for (long value : longs) {
                final int len = LTF8.writeUnsignedLTF8(value, ltf8TestBAOS);
                Assert.assertTrue(len <= LTF8.MAX_BITS);
            }
            try (ByteArrayInputStream ltf8TestBAIS = new ByteArrayInputStream(ltf8TestBAOS.toByteArray())) {
                for (long value : longs) {
                    final long result = LTF8.readUnsignedLTF8(ltf8TestBAIS);
                    Assert.assertEquals(value, result);
                }
            }
        }
    }

    @DataProvider(name = "predefined")
    public static Object[][] predefinedProvider() {
        return new Object[][]{
                // 0x7F has highest bit 7
                // write out as-is
                {0x7F, new byte[]{0x7F}},
                // 0x454F46 has highest bit 23
                // set 3 high bits + bits 25-28 (all 0) -> 0xE0
                // write 3 bytes as-is -> 0x45, 0x4F, 0x46
                {4542278, new byte[]{(byte) (0xFF & 224), 69, 79, 70}},
                // 0x4000 has highest bit 15
                // set 2 high bits + bits 17-21 (all 0) -> 0xC0
                // write 2 bytes as-is -> 0x40, 0x00
                {16384, new byte[]{-64, 64, 0}},
                // 0xC0 has highest bit 8
                // set 1 high bit + bits 9-14 (all 0) -> 0x80
                // write 1 byte as-is -> 0xC0
                {192, new byte[]{-128, -64}},
                // 0xFFFF FFFF FFFF ED6B has highest bit 64 (as all negatives do)
                // set 8 high bits (all 1) -> 0xFF
                // write 8 bytes as-is -> 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xED, 0x6B
                {-4757, new byte[]{-1, -1, -1, -1, -1, -1, -1, -19, 107}}
        };
    }

    @Test(dataProvider = "predefined")
    public void testPredefined (long value, byte[] ltf8) throws IOException {
        try (ByteArrayOutputStream ltf8TestBAOS = new ByteArrayOutputStream()) {
            final int len = LTF8.writeUnsignedLTF8(value, ltf8TestBAOS);
            Assert.assertTrue(len <= LTF8.MAX_BITS);

            byte[] actualArray = ltf8TestBAOS.toByteArray();
            Assert.assertEquals(ltf8, actualArray);

            try (ByteArrayInputStream ltf8TestBAIS = new ByteArrayInputStream(actualArray)) {
                final long actual = LTF8.readUnsignedLTF8(ltf8TestBAIS);
                Assert.assertEquals(actual, value);
            }
        }
    }

    /**
     * Tests the 9-byte (full 64-bit) LTF8 encoding with values where bits [35:32] differ from
     * bits [27:24], which exposes the >> 28 vs >> 24 bug in writeUnsignedLTF8.
     *
     * The 9-byte encoding writes a 0xFF prefix followed by bytes at shifts 56,48,40,32,24,16,8,0.
     */
    @DataProvider(name = "nineByteEncodings")
    public static Object[][] nineByteEncodingsProvider() {
        return new Object[][] {
            // Value with a leading zero byte
            {
                0x0123456789ABCDEFL,
                new byte[] {
                    (byte) 0xFF,
                    (byte) 0x01, // >> 56
                    (byte) 0x23, // >> 48
                    (byte) 0x45, // >> 40
                    (byte) 0x67, // >> 32
                    (byte) 0x89, // >> 24  (would be 0x78 with the >> 28 bug)
                    (byte) 0xAB, // >> 16
                    (byte) 0xCD, // >> 8
                    (byte) 0xEF, // >> 0
                }
            },
            // Value without a leading zero byte
            {
                0xFEDCBA9876543210L,
                new byte[] {
                    (byte) 0xFF,
                    (byte) 0xFE, // >> 56
                    (byte) 0xDC, // >> 48
                    (byte) 0xBA, // >> 40
                    (byte) 0x98, // >> 32
                    (byte) 0x76, // >> 24  (would be 0x87 with the >> 28 bug)
                    (byte) 0x54, // >> 16
                    (byte) 0x32, // >> 8
                    (byte) 0x10, // >> 0
                }
            },
        };
    }

    @Test(dataProvider = "nineByteEncodings")
    public void testNineByteEncodingWithDistinctBitsAt24And28(final long value, final byte[] expectedBytes) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            final int bitsWritten = LTF8.writeUnsignedLTF8(value, baos);
            Assert.assertEquals(bitsWritten, 72);

            final byte[] actualBytes = baos.toByteArray();
            Assert.assertEquals(actualBytes, expectedBytes, "9-byte LTF8 encoded bytes do not match expected");

            try (ByteArrayInputStream bais = new ByteArrayInputStream(actualBytes)) {
                final long decoded = LTF8.readUnsignedLTF8(bais);
                Assert.assertEquals(decoded, value, "Round-trip encode/decode mismatch for 9-byte LTF8 value");
            }
        }
    }

    @Test(expectedExceptions = RuntimeEOFException.class)
    public void emptyStreamTest() throws IOException {
        try (InputStream emptyStream = new ByteArrayInputStream(new byte[0])) {
            LTF8.readUnsignedLTF8(emptyStream);
        }
    }
}
