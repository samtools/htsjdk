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

import htsjdk.samtools.util.RuntimeIOException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ClosedChannelException;
import java.util.OptionalLong;

/**
 * InputStream with random access support (seek).
 *
 * <p>{@link SeekableStream} provides an interface for random access support in {@link InputStream},
 * thought the implementation of {@link #seek(long)}. As a random access stream, it supports mark
 * by design, being able to seek to a concrete position.
 */
public abstract class SeekableStream extends InputStream {

    /**
     * If the stream is marked with {@link #mark(int)} this represents the {@link #position()}
     * where the stream was; otherwise, this is empty.
     */
    protected OptionalLong mark = OptionalLong.empty();

    /** @return the length of the stream; 0 if not available or empty. */
    public abstract long length();

    /** @return the current byte position of the stream. */
    public abstract long position() throws IOException;

    /** Seeks the stream to the provided position. */
    public abstract void seek(long position) throws IOException;

    @Override
    public abstract int read(byte[] buffer, int offset, int length) throws IOException;

    @Override
    public abstract void close() throws IOException;

    /** @return {@code true} if the stream is already consumed; {@code false} otherwise. */
    public abstract boolean eof() throws IOException;

    /**
     * @return String representation of source (e.g. URL, file path, etc.), or null if not available.
     */
    public abstract String getSource();

    /**
     * Read enough bytes to fill the input buffer.
     * @param b
     * @throws EOFException If EOF is reached before buffer is filled
     */
    public void readFully(byte b[]) throws IOException {
        int len = b.length;
        if (len < 0){
            throw new IndexOutOfBoundsException();
        }
        int n = 0;
        while (n < len) {
            int count = read(b, n, len - n);
            if (count < 0){
                throw new EOFException();
            }
            n += count;
        }
    }

    /**
     * The return value of this method is unusable for any purpose, and we are only implementing it
     * because certain Java classes like {@link java.util.zip.GZIPInputStream} incorrectly rely on
     * it to detect EOF
     *
     * <p>If {@code eof() == true}, 0 bytes are available. Otherwise, available bytes are the
     * difference between the length of the stream ({@link #length()}) and the current position
     * ({@link #position()}.
     *
     * @return {@code 0} if the end of the file has been reached or the length cannot be determined;
     * number of bytes remaining in the stream otherwise.
     */
    @Override
    public int available() throws IOException {
        if (eof()) {
            return 0;
        }
        final long remaining = length() - position();
        // the remaining might be negative if the length is not available (0)
        return (remaining < 0) ? 0 : (int) remaining;
    }

    /**
     * Mark the current position of the stream.
     *
     * <p>Note: there is no limit for reading.
     *
     * @param readlimit ignored.
     * @throws RuntimeIOException if an IO error occurs other than an already closed stream.
     */
    @Override
    public final synchronized void mark(int readlimit) {
        try {
            mark = OptionalLong.of(position());
        } catch (final ClosedChannelException e) {
            // do nothing, respecting the contract of mark
        } catch (final IOException e) {
            // other exceptions are re-thrown
            throw new RuntimeIOException(e);
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

    /** Mark is always supported by any {@link SeekableStream}. */
    @Override
    public final boolean markSupported() {
        return true;
    }

}
