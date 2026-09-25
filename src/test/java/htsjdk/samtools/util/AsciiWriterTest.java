package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.Defaults;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.testng.Assert;
import org.testng.annotations.Test;

/** {@link AsciiWriter}: buffered writing, and the new {@link AsciiWriter#writeBufferedBytes} method. */
public class AsciiWriterTest extends HtsjdkTest {

    /** An output stream that counts how many times {@code flush()} is called. */
    private static final class FlushCountingOutputStream extends ByteArrayOutputStream {
        int flushCount;

        @Override
        public void flush() throws IOException {
            super.flush();
            flushCount++;
        }
    }

    @Test
    public void testWriteBufferedBytesReachesTheStreamWithoutFlushing() throws IOException {
        final FlushCountingOutputStream sink = new FlushCountingOutputStream();
        final AsciiWriter writer = new AsciiWriter(sink);
        writer.write("hello");
        writer.writeBufferedBytes();
        Assert.assertEquals(sink.toString(), "hello");
        Assert.assertEquals(sink.flushCount, 0, "writeBufferedBytes should not flush the underlying stream");
    }

    @Test
    public void testFlushStillFlushesTheUnderlyingStream() throws IOException {
        final FlushCountingOutputStream sink = new FlushCountingOutputStream();
        final AsciiWriter writer = new AsciiWriter(sink);
        writer.write("world");
        writer.flush();
        Assert.assertEquals(sink.toString(), "world");
        Assert.assertTrue(sink.flushCount > 0, "flush should flush the underlying stream");
    }

    @Test
    public void testContentIsCorrectAcrossABufferBoundary() throws IOException {
        final ByteArrayOutputStream sink = new ByteArrayOutputStream();
        final AsciiWriter writer = new AsciiWriter(sink);
        // Write more than the buffer size to force at least one buffer spill
        final int bufferSize = Defaults.NON_ZERO_BUFFER_SIZE;
        final StringBuilder expected = new StringBuilder();
        for (int i = 0; i < bufferSize + 500; i++) {
            final char c = (char) ('A' + (i % 26));
            writer.write(c);
            expected.append(c);
        }
        writer.writeBufferedBytes();
        Assert.assertEquals(sink.toString(), expected.toString());
    }

    @Test
    public void testWriteBufferedBytesWithNothingBufferedIsHarmless() throws IOException {
        final FlushCountingOutputStream sink = new FlushCountingOutputStream();
        final AsciiWriter writer = new AsciiWriter(sink);
        writer.writeBufferedBytes();
        Assert.assertEquals(sink.size(), 0);
        Assert.assertEquals(sink.flushCount, 0);
    }

    @Test
    public void testWriteStoresEachCharAsOneByte() throws IOException {
        final ByteArrayOutputStream sink = new ByteArrayOutputStream();
        final AsciiWriter writer = new AsciiWriter(sink);
        writer.write("café");
        writer.flush();
        Assert.assertEquals(sink.toByteArray(), new byte[] {'c', 'a', 'f', (byte) 0xE9});
    }

    @Test
    public void testWriteUtf8EncodesNonAsciiCharsAsUtf8() throws IOException {
        final ByteArrayOutputStream sink = new ByteArrayOutputStream();
        final AsciiWriter writer = new AsciiWriter(sink);
        writer.write("x");
        writer.writeUtf8("café č");
        writer.flush();
        Assert.assertEquals(sink.toByteArray(), "xcafé č".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testWriteUtf8IsCorrectAcrossABufferBoundary() throws IOException {
        final ByteArrayOutputStream sink = new ByteArrayOutputStream();
        final AsciiWriter writer = new AsciiWriter(sink);
        final String text = "é".repeat(Defaults.NON_ZERO_BUFFER_SIZE);
        writer.write("x");
        writer.writeUtf8(text);
        writer.flush();
        Assert.assertEquals(sink.toByteArray(), ("x" + text).getBytes(StandardCharsets.UTF_8));
    }
}
