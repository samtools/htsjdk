/*
 * The MIT License
 *
 * Copyright (c) 2013 The Broad Institute
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
package htsjdk.samtools.seekablestream;

import htsjdk.HtsjdkTest;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SeekablePathStreamTest extends HtsjdkTest {

    @Test
    public void testRead() throws Exception {
        Path testPath = Paths.get("src/test/resources/htsjdk/samtools/seekablestream/seekTest.txt");
        SeekablePathStream is = new SeekablePathStream(testPath);
        Assert.assertEquals(is.position(), 0);
        Assert.assertEquals(is.read(), (int) 'a');
        Assert.assertEquals(is.position(), 1);
        is.seek(20);
        Assert.assertEquals(is.position(), 20);
        byte[] buf = new byte[2];
        Assert.assertEquals(is.read(buf, 0, buf.length), 2);
        Assert.assertEquals(buf, new byte[] {(byte) 'c', (byte) 'c'});
        Assert.assertEquals(is.skip(8), 8);
        Assert.assertEquals(is.position(), 30);
        Assert.assertEquals(is.length(), Files.size(testPath));
        is.close();
    }

    /** Delegates to a channel but reads nothing on every other call, as a channel is allowed to. */
    private static final class SometimesEmptyChannel implements SeekableByteChannel {
        private final SeekableByteChannel delegate;
        private boolean readNothingNext = true;

        SometimesEmptyChannel(final SeekableByteChannel delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read(final ByteBuffer dst) throws IOException {
            readNothingNext = !readNothingNext;
            return readNothingNext ? delegate.read(dst) : 0;
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {
            return delegate.write(src);
        }

        @Override
        public long position() throws IOException {
            return delegate.position();
        }

        @Override
        public SeekableByteChannel position(final long newPosition) throws IOException {
            delegate.position(newPosition);
            return this;
        }

        @Override
        public long size() throws IOException {
            return delegate.size();
        }

        @Override
        public SeekableByteChannel truncate(final long size) throws IOException {
            delegate.truncate(size);
            return this;
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    @Test
    public void testReadOfOneByteRetriesWhenTheChannelReadsNothing() throws IOException {
        final Path path = Files.createTempFile("SeekablePathStreamTest", ".bin");
        try {
            Files.write(path, new byte[] {7, 8, (byte) 0xFF});
            try (SeekablePathStream in = new SeekablePathStream(path, SometimesEmptyChannel::new)) {
                Assert.assertEquals(in.read(), 7);
                Assert.assertEquals(in.read(), 8);
                Assert.assertEquals(in.read(), 0xFF);
                Assert.assertEquals(in.read(), -1);
            }
        } finally {
            Files.delete(path);
        }
    }

    @Test
    public void testReadOfOneByteAtEndOfFileReturnsMinusOne() throws IOException {
        final Path path = Files.createTempFile("SeekablePathStreamTest", ".bin");
        try {
            Files.write(path, new byte[] {7});
            try (SeekablePathStream in = new SeekablePathStream(path)) {
                Assert.assertEquals(in.read(), 7);
                Assert.assertEquals(in.read(), -1);
                Assert.assertEquals(in.read(), -1);
            }
        } finally {
            Files.delete(path);
        }
    }
}
