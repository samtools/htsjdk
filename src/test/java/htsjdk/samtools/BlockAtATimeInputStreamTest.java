package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BlockAtATimeInputStreamTest extends HtsjdkTest {
    /** Bytes that compress poorly, so that a few hundred thousand of them make several BGZF blocks. */
    private static byte[] data(final int length) {
        final byte[] data = new byte[length];
        new Random(7).nextBytes(data);
        return data;
    }

    private static BlockCompressedInputStream blockCompressed(final byte[] data) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BlockCompressedOutputStream out = new BlockCompressedOutputStream(bytes, (Path) null)) {
            out.write(data);
        }
        return new BlockCompressedInputStream(new SeekableMemoryStream(bytes.toByteArray(), "test"));
    }

    @Test
    public void testNoReadCrossesABlock() throws IOException {
        final byte[] data = data(300_000);
        try (BlockAtATimeInputStream in = new BlockAtATimeInputStream(blockCompressed(data))) {
            final byte[] buffer = new byte[1 << 20];
            long consumed = 0;
            int reads = 0;
            for (int n = in.read(buffer, 0, buffer.length); n != -1; n = in.read(buffer, 0, buffer.length)) {
                final long first = BlockCompressedFilePointerUtil.getBlockAddress(in.filePointerAt(consumed));
                final long last = BlockCompressedFilePointerUtil.getBlockAddress(in.filePointerAt(consumed + n - 1));
                Assert.assertEquals(last, first, "read " + reads + " spans two blocks");
                consumed += n;
                reads++;
            }
            Assert.assertEquals(consumed, data.length);
            Assert.assertTrue(reads > 3, "the data should have taken several blocks, not " + reads);
        }
    }

    @Test
    public void testFilePointerOfAByteLeadsBackToThatByte() throws IOException {
        final byte[] data = data(300_000);
        try (BlockAtATimeInputStream in = new BlockAtATimeInputStream(blockCompressed(data));
                BlockCompressedInputStream check = blockCompressed(data)) {
            final byte[] buffer = new byte[1 << 20];
            long consumed = 0;
            for (int n = in.read(buffer, 0, buffer.length); n != -1; n = in.read(buffer, 0, buffer.length)) {
                for (final long byteCount : new long[] {consumed, consumed + n / 2, consumed + n - 1}) {
                    check.seek(in.filePointerAt(byteCount));
                    Assert.assertEquals((byte) check.read(), data[(int) byteCount], "byte " + byteCount);
                }
                consumed += n;
            }
        }
    }

    @Test
    public void testFilePointerJustPastARead() throws IOException {
        final byte[] data = data(300_000);
        try (BlockCompressedInputStream blocks = blockCompressed(data);
                BlockAtATimeInputStream in = new BlockAtATimeInputStream(blocks)) {
            final int n = in.read(new byte[1 << 20], 0, 1 << 20);
            // A read takes all that is left of a block, so just past it is the start of the next block
            Assert.assertEquals(in.filePointerAt(n), blocks.getFilePointer());
            Assert.assertEquals(BlockCompressedFilePointerUtil.getBlockOffset(in.filePointerAt(n)), 0);
        }
    }

    @Test
    public void testFilePointerBeforeAnythingIsReadIsTheStartOfTheStream() throws IOException {
        try (BlockAtATimeInputStream in = new BlockAtATimeInputStream(blockCompressed(data(1_000)))) {
            Assert.assertEquals(in.filePointerAt(0), 0);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testFilePointerOfAByteOfAnEarlierReadIsRefused() throws IOException {
        try (BlockAtATimeInputStream in = new BlockAtATimeInputStream(blockCompressed(data(300_000)))) {
            final byte[] buffer = new byte[1 << 20];
            in.read(buffer, 0, buffer.length);
            in.read(buffer, 0, buffer.length);
            in.filePointerAt(10);
        }
    }

    @Test
    public void testAfterASeekTheCountCarriesOnFromWhatWasHandedOver() throws IOException {
        final byte[] data = data(300_000);
        try (BlockAtATimeInputStream in = new BlockAtATimeInputStream(blockCompressed(data))) {
            final byte[] buffer = new byte[1 << 20];
            final int first = in.read(buffer, 0, buffer.length);
            final long secondBlock = in.filePointerAt(first);
            final int second = in.read(buffer, 0, buffer.length);

            in.seek(secondBlock);
            Assert.assertEquals(in.filePointerAt(first + second), secondBlock);
            final int again = in.read(buffer, 0, buffer.length);
            Assert.assertEquals(again, second);
            Assert.assertEquals(buffer[0], data[first]);
            Assert.assertEquals(in.filePointerAt(first + second), secondBlock);
        }
    }

    @Test
    public void testEndOfStream() throws IOException {
        try (BlockAtATimeInputStream in = new BlockAtATimeInputStream(blockCompressed(data(1_000)))) {
            final byte[] buffer = new byte[4_096];
            Assert.assertEquals(in.read(buffer, 0, buffer.length), 1_000);
            Assert.assertEquals(in.read(buffer, 0, buffer.length), -1);
            Assert.assertEquals(in.read(buffer, 0, 0), 0);
        }
    }
}
