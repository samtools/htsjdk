package htsjdk.samtools.cram.io;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.RuntimeEOFException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Created by vadim on 03/02/2015.
 */
public class ITF8Test extends HtsjdkTest {

    private byte[] streamWritten(List<Integer> ints) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (int value : ints) {
                int len = ITF8.writeUnsignedITF8(value, baos);
                Assert.assertTrue(len <= ITF8.MAX_BITS);

            }
            return baos.toByteArray();
        }
    }

    private byte[] byteBufferWritten(List<Integer> ints) {
        final int arraySize = ITF8.MAX_BYTES * ints.size();
        ByteBuffer bb = ByteBuffer.wrap(new byte[arraySize]);

        for (int value : ints) {
            int len = ITF8.writeUnsignedITF8(value, bb);
            Assert.assertTrue(len <= ITF8.MAX_BITS);
        }

        return bb.array();
    }

    // Combinatorial tests of 2 ITF8 write methods x 2 ITF8 read methods

    @Test(dataProvider = "testInt32Lists", dataProviderClass = IOTestCases.class)
    public void testITF8Stream(List<Integer> ints) throws IOException {
        byte[][] inputs = {streamWritten(ints), byteBufferWritten(ints)};

        for (byte[] byteArray : inputs) {
            try (InputStream testBAIS = new ByteArrayInputStream(byteArray)) {
                for (int value : ints) {
                    int fromStream = ITF8.readUnsignedITF8(testBAIS);
                    Assert.assertEquals(fromStream, value);
                }
            }
        }
    }

    @Test(dataProvider = "testInt32Lists", dataProviderClass = IOTestCases.class)
    public void testITF8Buffer(List<Integer> ints) throws IOException {
        byte[][] inputs = {streamWritten(ints), byteBufferWritten(ints)};

        for (byte[] byteArray : inputs) {
            ByteBuffer bb = ByteBuffer.wrap(byteArray);
            for (int value : ints) {
                int fromStream = ITF8.readUnsignedITF8(bb);
                Assert.assertEquals(fromStream, value);
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
                // 0xFFFFED6B has highest bit 32 (as all negatives do)
                // set 4 high bits + bits 29-32 (all 1) -> 0xFF
                // write bits 5-28 as 3 bytes -> 0xFF, 0xFE, 0xD6
                // write lowest 1 byte as-is (duplicating bits 5-8) -> 0x6B
                {-4757, new byte[]{-1, -1, -2, -42, 107}}
        };
    }

    @Test(dataProvider = "predefined")
    public void testPredefined (int value, byte[] itf8) {
        final byte[] bytes = ITF8.writeUnsignedITF8(value);
        Assert.assertEquals(itf8, bytes);

        int n = ITF8.readUnsignedITF8(itf8);
        Assert.assertEquals(value, n);
    }

    @Test(expectedExceptions = RuntimeEOFException.class)
    public void emptyStreamTest() throws IOException {
        try (InputStream emptyStream = new ByteArrayInputStream(new byte[0])) {
            ITF8.readUnsignedITF8(emptyStream);
        }
    }

    @Test(expectedExceptions = BufferUnderflowException.class)
    public void emptyBufferTest() {
        ByteBuffer bb = ByteBuffer.wrap(new byte[0]);
        ITF8.readUnsignedITF8(bb);
    }
}
