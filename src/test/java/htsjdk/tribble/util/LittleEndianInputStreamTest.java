package htsjdk.tribble.util;

import htsjdk.HtsjdkTest;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LittleEndianInputStreamTest extends HtsjdkTest {

    private static LittleEndianInputStream streamOf(final int... bytes) {
        final byte[] data = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) data[i] = (byte) bytes[i];
        return new LittleEndianInputStream(new ByteArrayInputStream(data));
    }

    @Test
    public void testReadStringReturnsTheBytesBeforeTheTerminator() throws IOException {
        final LittleEndianInputStream in = streamOf('c', 'h', 'r', '1', 0, 'x');
        Assert.assertEquals(in.readString(), "chr1");
        Assert.assertEquals(in.read(), 'x');
    }

    @Test
    public void testReadStringOfOnlyATerminatorIsEmpty() throws IOException {
        Assert.assertEquals(streamOf(0).readString(), "");
    }

    @Test
    public void testReadStringKeepsBytesWithTheHighBitSet() throws IOException {
        // "é" and "中" in UTF-8: every byte is 0x80 or above, which is not the end of the stream
        Assert.assertEquals(streamOf('a', 0xC3, 0xA9, 0xE4, 0xB8, 0xAD, 0).readString(), "aé中");
    }

    @Test
    public void testReadStringKeepsA0xFFByte() throws IOException {
        // 0xFF becomes -1 when cast to a byte, the same value read() returns at the end of the stream
        Assert.assertEquals(streamOf('a', 0xFF, 'b', 0).readString(), "a�b");
    }

    @Test(expectedExceptions = EOFException.class)
    public void testReadStringWithoutATerminatorThrowsEOFException() throws IOException {
        streamOf('c', 'h', 'r').readString();
    }

    @Test(expectedExceptions = EOFException.class)
    public void testReadStringOfAnEmptyStreamThrowsEOFException() throws IOException {
        streamOf().readString();
    }
}
