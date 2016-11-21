/*
 * The MIT License
 *
 * Copyright (c) 2016 The Broad Institute
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

import htsjdk.samtools.seekablestream.SeekableStream;

import java.io.IOException;

/**
* Created by vadim on 23/03/2015.
*/
public class ByteArraySeekableStream extends SeekableStream {
    private byte[] bytes;
    private long position = 0;

    public ByteArraySeekableStream(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public long length() {
        return bytes.length;
    }

    @Override
    public long position() throws IOException {
        return position;
    }

    @Override
    public void seek(long position) throws IOException {
        this.position = position;
    }

    @Override
    public int read() throws IOException {
        if (position < bytes.length)
            return 0xFF & bytes[((int) position++)];
        else return -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (position >= bytes.length) {
            return -1;
        }
        if (position + len > bytes.length) {
            len = (int) (bytes.length - position);
        }
        if (len <= 0) {
            return 0;
        }
        System.arraycopy(bytes, (int) position, b, off, len);
        position += len;
        return len;
    }

    @Override
    public void close() throws IOException {
        bytes = null;
    }

    @Override
    public boolean eof() throws IOException {
        return position >= bytes.length;
    }

    @Override
    public String getSource() {
        return null;
    }
}
