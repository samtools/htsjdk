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

    public SamLineReader(final InputStream in) {
        this(in, DEFAULT_BUFFER_SIZE);
    }

    public SamLineReader(final InputStream in, final int bufferSize) {
        this.in = in;
        this.buf = new byte[Math.max(bufferSize, 64)];
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
                final int chunkLen = terminatorPos - pos;
                if (overflowLen == 0) {
                    // Fast path: line is wholly within the main buffer; expose buf+offset directly
                    currentLineBuf = buf;
                    currentLineOff = pos;
                    currentLineLen = chunkLen;
                } else {
                    // Line spans buffer fills; consolidate trailing bytes into the overflow buffer
                    appendToOverflow(buf, pos, chunkLen);
                    currentLineBuf = overflow;
                    currentLineOff = 0;
                    currentLineLen = overflowLen;
                }

                pos = terminatorPos + 1;
                if (buf[terminatorPos] == '\r') {
                    if (pos >= limit) fill();
                    if (pos < limit && buf[pos] == '\n') pos++;
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
