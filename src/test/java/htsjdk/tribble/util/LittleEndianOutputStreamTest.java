package htsjdk.tribble.util;

import htsjdk.HtsjdkTest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LittleEndianOutputStreamTest extends HtsjdkTest {

    private static byte[] bytesOf(final int... values) {
        final byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) bytes[i] = (byte) values[i];
        return bytes;
    }

    @Test
    public void testWriteStringWritesTheBytesAndATerminator() throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final LittleEndianOutputStream out = new LittleEndianOutputStream(bytes);
        out.writeString("chr1");
        Assert.assertEquals(bytes.toByteArray(), bytesOf('c', 'h', 'r', '1', 0));
        Assert.assertEquals(out.getWrittenCount(), 5);
    }

    @Test
    public void testWriteStringEncodesUtf8() throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final LittleEndianOutputStream out = new LittleEndianOutputStream(bytes);
        out.writeString("aé中");
        Assert.assertEquals(bytes.toByteArray(), bytesOf('a', 0xC3, 0xA9, 0xE4, 0xB8, 0xAD, 0));
        Assert.assertEquals(out.getWrittenCount(), 7);
    }
}
