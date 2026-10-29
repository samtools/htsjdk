/*
 * The MIT License
 *
 * Copyright (c) 2016 Daniel Cameron
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AsyncBlockCompressedInputStreamTest extends HtsjdkTest {
    private final Path BAM_FILE = Paths.get("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");

    @Test
    public void testAsync() throws Exception {
        BlockCompressedInputStream sync = new BlockCompressedInputStream(new SeekableFileStream(BAM_FILE));
        List<byte[]> expected = new ArrayList<>();
        List<Long> virtualOffset = new ArrayList<>();
        List<Integer> length = new ArrayList<>();
        byte[] buffer = new byte[BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE / 2];
        virtualOffset.add(sync.getFilePointer());
        int len = sync.read(buffer);
        length.add(len);
        while (len > 0) {
            expected.add(buffer);
            buffer = new byte[buffer.length];
            len = sync.read(buffer);
            length.add(len);
            virtualOffset.add(sync.getFilePointer());
        }
        sync.close();
        buffer = new byte[buffer.length];
        List<BlockCompressedInputStream> list = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            list.add(new AsyncBlockCompressedInputStream(new SeekableFileStream(BAM_FILE)));
        }
        // read till EOF
        for (int i = 0; i < expected.size(); i++) {
            for (BlockCompressedInputStream async : list) {
                len = async.read(buffer);
                Assert.assertEquals(len, (int) length.get(i));
                Assert.assertEquals(buffer[0], expected.get(i)[0]);
            }
        }
        for (int j = 0; j < 128; j++) {
            // seek and read
            for (BlockCompressedInputStream async : list) {
                async.seek(virtualOffset.get(0));
            }
            for (int i = 0; i < Math.min(expected.size(), 8); i++) {
                for (BlockCompressedInputStream async : list) {
                    len = async.read(buffer);
                    Assert.assertEquals(len, (int) length.get(i));
                    Assert.assertEquals(buffer[0], expected.get(i)[0]);
                }
            }
        }
        for (BlockCompressedInputStream async : list) {
            async.close();
        }
    }

    @Test
    public void testFilePointer() throws Exception {
        BlockCompressedInputStream sync = new BlockCompressedInputStream(BAM_FILE);
        Assert.assertEquals(sync.getFilePointer(), 0);
        sync.close();
    }

    @Test
    public void testCorruptDeflateDataFailsAsTheSynchronousStreamDoes() throws Exception {
        final byte[] bgzf = threeBlocks();
        corruptDeflateData(bgzf, secondBlockOffset(bgzf));
        assertAsyncFailsAsSyncDoes(bgzf, false, AsyncBlockCompressedInputStreamTest::readToEnd);
    }

    @Test
    public void testNegativeUncompressedLengthFailsAsTheSynchronousStreamDoes() throws Exception {
        final byte[] bgzf = threeBlocks();
        final int isizeOffset = secondBlockOffset(bgzf) + blockLength(bgzf, secondBlockOffset(bgzf)) - 4;
        Arrays.fill(bgzf, isizeOffset, isizeOffset + 4, (byte) 0xFF);
        assertAsyncFailsAsSyncDoes(bgzf, false, AsyncBlockCompressedInputStreamTest::readToEnd);
    }

    @Test
    public void testCrcMismatchFailsAsTheSynchronousStreamDoes() throws Exception {
        final byte[] bgzf = threeBlocks();
        final int crcOffset = secondBlockOffset(bgzf) + blockLength(bgzf, secondBlockOffset(bgzf)) - 8;
        bgzf[crcOffset] ^= 0xFF;
        assertAsyncFailsAsSyncDoes(bgzf, true, AsyncBlockCompressedInputStreamTest::readToEnd);
    }

    @Test
    public void testSeekToACorruptBlockFailsAsTheSynchronousStreamDoes() throws Exception {
        final byte[] bgzf = threeBlocks();
        corruptDeflateData(bgzf, secondBlockOffset(bgzf));
        final long pointer = BlockCompressedFilePointerUtil.makeFilePointer(secondBlockOffset(bgzf));
        assertAsyncFailsAsSyncDoes(bgzf, false, in -> in.seek(pointer));
    }

    /** Three data blocks of 1,000 bytes each, then the EOF block. */
    private static byte[] threeBlocks() throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BlockCompressedOutputStream out = new BlockCompressedOutputStream(bytes, (Path) null)) {
            for (int block = 0; block < 3; block++) {
                for (int i = 0; i < 1000; i++) out.write('A' + (i + block) % 26);
                out.flush();
            }
        }
        return bytes.toByteArray();
    }

    private static int blockLength(final byte[] bgzf, final int blockOffset) {
        final int bsizeOffset = blockOffset + BlockCompressedStreamConstants.BLOCK_LENGTH_OFFSET;
        return ((bgzf[bsizeOffset] & 0xFF) | (bgzf[bsizeOffset + 1] & 0xFF) << 8) + 1;
    }

    private static int secondBlockOffset(final byte[] bgzf) {
        return blockLength(bgzf, 0);
    }

    /** Makes the block's deflate data start with block type 11, which is reserved, so every inflater rejects it. */
    private static void corruptDeflateData(final byte[] bgzf, final int blockOffset) {
        bgzf[blockOffset + BlockCompressedStreamConstants.BLOCK_HEADER_LENGTH] = 0x07;
    }

    private static void readToEnd(final BlockCompressedInputStream in) throws IOException {
        final byte[] buffer = new byte[100];
        while (in.read(buffer) != -1) {
            // read on until the corrupt block
        }
    }

    private interface Reading {
        void readFrom(BlockCompressedInputStream in) throws IOException;
    }

    /**
     * Applies {@code reading} to a synchronous and an asynchronous stream over {@code bgzf} and checks that both fail
     * with the same exception, and that the asynchronous stream still closes.
     */
    private static void assertAsyncFailsAsSyncDoes(final byte[] bgzf, final boolean checkCrcs, final Reading reading)
            throws Exception {
        final Exception syncFailure = failureOf(
                new BlockCompressedInputStream(new SeekableMemoryStream(bgzf, "corrupt")), checkCrcs, reading);
        final Exception asyncFailure = withinTimeout(() -> failureOf(
                new AsyncBlockCompressedInputStream(new SeekableMemoryStream(bgzf, "corrupt")), checkCrcs, reading));
        Assert.assertEquals(asyncFailure.getClass(), syncFailure.getClass());
        Assert.assertEquals(asyncFailure.getMessage(), syncFailure.getMessage());
    }

    private static Exception failureOf(
            final BlockCompressedInputStream in, final boolean checkCrcs, final Reading reading) {
        try (in) {
            in.setCheckCrcs(checkCrcs);
            reading.readFrom(in);
        } catch (final IOException | RuntimeException e) {
            return e;
        }
        throw new AssertionError("The corrupt block was read without failing");
    }

    /**
     * Runs {@code work} on a daemon thread and fails if it hasn't finished within 30 seconds. A hung asynchronous
     * stream can't be rescued by interrupting it, as TestNG's {@code timeOut} does, because {@code close()} clears the
     * interrupt and waits again, so the hung thread is left behind instead.
     */
    private static <T> T withinTimeout(final Callable<T> work) throws Exception {
        final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            final Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
        try {
            return executor.submit(work).get(30, TimeUnit.SECONDS);
        } catch (final TimeoutException e) {
            throw new AssertionError("The asynchronous stream hung instead of failing", e);
        } finally {
            executor.shutdown();
        }
    }
}
