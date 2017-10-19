/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Daniel Gomez-Sanchez
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package htsjdk.samtools.seekablestream;

import java.io.IOException;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * @author Daniel Gomez-Sanchez (magicDGS)
 */
public class MarkSeekableStream extends SeekableStream {

    private final SeekableStream underlyingStream;
    private OptionalLong mark = OptionalLong.empty();

    public MarkSeekableStream(final SeekableStream underlyingStream) {
        if (underlyingStream == null) {
            throw new IllegalArgumentException("null stream");
        }
        this.underlyingStream = underlyingStream;
    }

    public static MarkSeekableStream asMarkSeekableStream(final SeekableStream stream) {
        if (stream instanceof MarkSeekableStream) {
            return (MarkSeekableStream) stream;
        }
        return new MarkSeekableStream(stream);
    }

    @Override
    public long length() {
        return underlyingStream.length();
    }

    @Override
    public long position() throws IOException {
        return underlyingStream.position();
    }

    @Override
    public void seek(long position) throws IOException {
        underlyingStream.seek(position);
    }

    @Override
    public int read() throws IOException {
        return underlyingStream.read();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        return underlyingStream.read(buffer, offset, length);
    }

    @Override
    public void close() throws IOException {
        underlyingStream.close();
    }

    @Override
    public boolean eof() throws IOException {
        return underlyingStream.eof();
    }

    @Override
    public String getSource() {
        return underlyingStream.getSource();
    }

    /**
     * Mark the current position of the stream.
     *
     * <p>Note: there is not limit for reading.
     *
     * @param readlimit ignored.
     */
    @Override
    public synchronized void mark(int readlimit) {
        try {
            mark = OptionalLong.of(position());
        } catch (IOException e) {
            // do nothing (most likely already closed stream)
        }
    }

    /**
     * Seeks to the marked position if set; otherwise to the beginning of the stream.
     */
    @Override
    public synchronized void reset() throws IOException {
        if (mark.isPresent()) {
            seek(mark.getAsLong());
        } else {
            seek(0);
        }
    }

    @Override
    public boolean markSupported() {
        return true;
    }
}
