/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.testutil.streams.OneByteAtATimeChannel;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author alecw@broadinstitute.org
 */
public class BlockCompressedTerminatorTest extends HtsjdkTest {
    private static final Path TEST_DATA_DIR = Paths.get("src/test/resources/htsjdk/samtools/util");
    private static final Path DEFECTIVE = TEST_DATA_DIR.resolve("defective_bgzf.bam");
    private static final Path NO_TERMINATOR = TEST_DATA_DIR.resolve("no_bgzf_terminator.bam");

    @DataProvider
    public Object[][] getFiles() throws IOException {
        return new Object[][] {
            {getValidCompressedFile(), BlockCompressedInputStream.FileTermination.HAS_TERMINATOR_BLOCK},
            {NO_TERMINATOR, BlockCompressedInputStream.FileTermination.HAS_HEALTHY_LAST_BLOCK},
            {DEFECTIVE, BlockCompressedInputStream.FileTermination.DEFECTIVE}
        };
    }

    @Test(dataProvider = "getFiles")
    public void testCheckTerminationForFiles(Path compressedFile, BlockCompressedInputStream.FileTermination expected)
            throws IOException {
        Assert.assertEquals(BlockCompressedInputStream.checkTermination(compressedFile), expected);
    }

    @Test(dataProvider = "getFiles")
    public void testCheckTerminationForPaths(Path compressedFile, BlockCompressedInputStream.FileTermination expected)
            throws IOException {
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path compressedFileInJimfs = Files.copy(compressedFile, fs.getPath("something"));
            Assert.assertEquals(BlockCompressedInputStream.checkTermination(compressedFileInJimfs), expected);
        }
    }

    @Test(dataProvider = "getFiles")
    public void testCheckTerminationForSeekableByteChannels(
            Path compressedFile, BlockCompressedInputStream.FileTermination expected) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(compressedFile)) {
            Assert.assertEquals(BlockCompressedInputStream.checkTermination(channel), expected);
        }
    }

    @Test(dataProvider = "getFiles")
    public void testChannelPositionIsRestored(Path compressedFile, BlockCompressedInputStream.FileTermination expected)
            throws IOException {
        final long position = 50;
        try (SeekableByteChannel channel = Files.newByteChannel(compressedFile)) {
            channel.position(position);
            Assert.assertEquals(channel.position(), position);
            Assert.assertEquals(BlockCompressedInputStream.checkTermination(channel), expected);
            Assert.assertEquals(channel.position(), position);
        }
    }

    private static Path getValidCompressedFile() throws IOException {
        final Path tmpCompressedFile = Files.createTempFile("test.", ".bgzf");
        IOUtil.deleteOnExit(tmpCompressedFile);
        final BlockCompressedOutputStream os = new BlockCompressedOutputStream(tmpCompressedFile);
        os.write("Hi, Mom!\n".getBytes());
        os.close();
        return tmpCompressedFile;
    }

    @Test
    public void testReadFullyReadsBytesCorrectly() throws IOException {
        try (final SeekableByteChannel channel = Files.newByteChannel(DEFECTIVE)) {
            final ByteBuffer readBuffer = ByteBuffer.allocate(10);
            Assert.assertTrue(channel.size() > readBuffer.capacity());
            BlockCompressedInputStream.readFully(channel, readBuffer);

            ByteBuffer expected = ByteBuffer.allocate(10);
            channel.position(0).read(expected);
            Assert.assertEquals(readBuffer.array(), expected.array());
        }
    }

    @Test(expectedExceptions = EOFException.class)
    public void testReadFullyThrowWhenItCantReadEnough() throws IOException {
        try (final SeekableByteChannel channel = Files.newByteChannel(DEFECTIVE)) {
            final ByteBuffer readBuffer = ByteBuffer.allocate(1000);
            Assert.assertTrue(channel.size() < readBuffer.capacity());
            BlockCompressedInputStream.readFully(channel, readBuffer);
        }
    }

    @Test
    public void testReadFullyReadsBytesCorrectlyWhenPartialReadOccurs() throws IOException {
        final byte[] expected = "something to test reading from".getBytes();
        final ByteBuffer buffer = ByteBuffer.wrap(expected);
        try (final SeekableByteChannel channel = new OneByteAtATimeChannel(buffer)) {
            final int readBufferSize = 10;
            final ByteBuffer readBuffer = ByteBuffer.allocate(readBufferSize);
            Assert.assertTrue(channel.size() >= readBuffer.capacity());
            BlockCompressedInputStream.readFully(channel, readBuffer);
            Assert.assertEquals(readBuffer.array(), Arrays.copyOfRange(expected, 0, readBufferSize));
        }
    }
}
