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
 * <p>This replaces the previous approach of wrapping the stream in
 * {@code InputStreamReader(UTF-8) → BufferedReader → LineNumberReader}, which performed
 * per-character UTF-8 decoding even though alignment lines (99%+ of a typical SAM file) are
 * pure ASCII.</p>
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

    public SamLineReader(final InputStream in) {
        this(in, DEFAULT_BUFFER_SIZE);
    }

    public SamLineReader(final InputStream in, final int bufferSize) {
        this.in = in;
        this.buf = new byte[Math.max(bufferSize, 64)];
    }

    @Override
    public String readLine() {
        if (pos >= limit && !fill()) {
            return null;
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
                final String result;
                if (overflowLen == 0) {
                    result = decodeBytes(buf, pos, chunkLen);
                } else {
                    appendToOverflow(buf, pos, chunkLen);
                    result = decodeBytes(overflow, 0, overflowLen);
                }

                pos = terminatorPos + 1;
                if (buf[terminatorPos] == '\r') {
                    if (pos >= limit && !fill()) {
                        // EOF right after \r — that's fine, line is complete
                    }
                    if (pos < limit && buf[pos] == '\n') {
                        pos++;
                    }
                }

                lineNumber++;
                return result;
            }

            appendToOverflow(buf, pos, limit - pos);
            pos = limit;

            if (!fill()) {
                if (overflowLen > 0) {
                    lineNumber++;
                    return decodeBytes(overflow, 0, overflowLen);
                }
                return null;
            }
        }
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
