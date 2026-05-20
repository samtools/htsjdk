/*
 * The MIT License
 *
 * Copyright (c) 2026 The Broad Institute
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A {@link LineReader} optimized for SAM text files. Reads raw bytes from an {@link InputStream}
 * and converts to {@link String} using the charset appropriate for the line content:
 * <ul>
 *   <li>Header lines (starting with {@code @}): decoded as UTF-8, since the SAM spec permits
 *       Unicode in certain header fields ({@code @CO}, {@code DS}, {@code CL}).</li>
 *   <li>Alignment lines: decoded as ISO-8859-1, which on modern JVMs with compact strings is
 *       a bulk array copy. The SAM spec restricts alignment fields to printable ASCII, so
 *       Latin-1 is lossless and avoids the overhead of UTF-8 multi-byte scanning.</li>
 * </ul>
 *
 * <p>For the hottest call sites, {@link #readNextLine()} reads a line into the reader's internal
 * buffer without allocating a {@link String}. The line bytes are then accessible via
 * {@link #getLineBuffer()}, {@link #getLineOffset()}, and {@link #getLineLength()}; those values
 * are valid only until the next call to {@code readNextLine()} or {@link #readLine()}.</p>
 */
public class SamLineReader implements LineReader {
    private static final int DEFAULT_BUFFER_SIZE = 64 * 1024;

    private InputStream in;
    private final byte[] buf;
    private int pos;
    private int limit;
    private int lineNumber;
    private boolean streamExhausted;

    private byte[] overflow = new byte[512];
    private int overflowLen;

    private byte[] currentLineBuf;
    private int currentLineOff;
    private int currentLineLen;

    /**
     * Constructs a {@link SamLineReader} over {@code in} using a default 64KB buffer.
     */
    public SamLineReader(final InputStream in) {
        this(in, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Constructs a {@link SamLineReader} over {@code in} with an explicit internal buffer size.
     * Buffer sizes smaller than the longest line in the input still work -- the reader will just
     * spill onto its overflow buffer -- so this constructor is primarily useful for tests that
     * want to exercise buffer-boundary code paths. The buffer size is clamped to a minimum of 1
     * to avoid degenerate 0-byte reads.
     */
    public SamLineReader(final InputStream in, final int bufferSize) {
        this.in = in;
        this.buf = new byte[Math.max(bufferSize, 1)];
    }

    /**
     * Reads the next line into the reader's internal buffer without allocating a {@link String}.
     * After this returns {@code true}, the line bytes are accessible via {@link #getLineBuffer()},
     * {@link #getLineOffset()}, and {@link #getLineLength()}. Those values are valid only until
     * the next call to this method or {@link #readLine()}.
     *
     * @return {@code true} if a line was read, {@code false} if the stream is at EOF
     */
    public boolean readNextLine() {
        if (pos >= limit && !fill()) {
            return false;
        }

        overflowLen = 0;

        while (true) {
            int terminatorPos = -1;
            for (int i = pos; i < limit; i++) {
                final byte b = buf[i];
                if (b == '\n' || b == '\r') {
                    terminatorPos = i;
                    break;
                }
            }

            if (terminatorPos >= 0) {
                final int chunkStart = pos;
                final int chunkLen = terminatorPos - pos;
                final byte terminator = buf[terminatorPos];
                pos = terminatorPos + 1;

                // If the terminator is '\r' and it is the very last byte of the current fill,
                // we need to refill before we can tell whether the next byte is the '\n' of a
                // '\r\n' pair. That refill compacts/overwrites the main buffer, so we must
                // copy the line bytes into the overflow buffer first to keep them stable.
                final boolean needLookaheadFill = (terminator == '\r' && pos >= limit);
                if (needLookaheadFill || overflowLen > 0) {
                    appendToOverflow(buf, chunkStart, chunkLen);
                }

                if (terminator == '\r') {
                    if (pos >= limit) fill();
                    if (pos < limit && buf[pos] == '\n') pos++;
                }

                if (overflowLen > 0) {
                    currentLineBuf = overflow;
                    currentLineOff = 0;
                    currentLineLen = overflowLen;
                } else {
                    // Fast path: the entire line is wholly within the main buffer and the buffer
                    // has not been mutated since we located it; expose buf + offset directly.
                    currentLineBuf = buf;
                    currentLineOff = chunkStart;
                    currentLineLen = chunkLen;
                }

                lineNumber++;
                return true;
            }

            appendToOverflow(buf, pos, limit - pos);
            pos = limit;

            if (!fill()) {
                if (overflowLen > 0) {
                    currentLineBuf = overflow;
                    currentLineOff = 0;
                    currentLineLen = overflowLen;
                    lineNumber++;
                    return true;
                }
                return false;
            }
        }
    }

    /**
     * @return the buffer holding the bytes of the most recently read line, valid only until the
     *         next call to {@link #readNextLine()} or {@link #readLine()}
     */
    public byte[] getLineBuffer() {
        return currentLineBuf;
    }

    /** @return the offset within {@link #getLineBuffer()} at which the current line starts */
    public int getLineOffset() {
        return currentLineOff;
    }

    /** @return the length in bytes of the current line, excluding the line terminator */
    public int getLineLength() {
        return currentLineLen;
    }

    @Override
    public String readLine() {
        if (!readNextLine()) {
            return null;
        }
        return decodeBytes(currentLineBuf, currentLineOff, currentLineLen);
    }

    @Override
    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public int peek() {
        if (pos < limit) {
            return buf[pos] & 0xff;
        }
        if (fill()) {
            return buf[pos] & 0xff;
        }
        return EOF_VALUE;
    }

    @Override
    public void close() {
        if (in != null) {
            try {
                in.close();
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            } finally {
                in = null;
            }
        }
    }

    private boolean fill() {
        if (streamExhausted) {
            return false;
        }

        if (pos > 0 && pos < limit) {
            System.arraycopy(buf, pos, buf, 0, limit - pos);
            limit -= pos;
            pos = 0;
        } else {
            pos = 0;
            limit = 0;
        }

        try {
            final int n = in.read(buf, limit, buf.length - limit);
            if (n <= 0) {
                streamExhausted = true;
                return false;
            }
            limit += n;
            return true;
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    private void appendToOverflow(final byte[] src, final int off, final int len) {
        if (len == 0) {
            return;
        }
        final int needed = overflowLen + len;
        if (needed > overflow.length) {
            final byte[] grown = new byte[Math.max(overflow.length * 2, needed)];
            System.arraycopy(overflow, 0, grown, 0, overflowLen);
            overflow = grown;
        }
        System.arraycopy(src, off, overflow, overflowLen, len);
        overflowLen += len;
    }

    private static String decodeBytes(final byte[] bytes, final int off, final int len) {
        if (len > 0 && bytes[off] == '@') {
            return new String(bytes, off, len, StandardCharsets.UTF_8);
        }
        return new String(bytes, off, len, StandardCharsets.ISO_8859_1);
    }
}
