/*
 * Copyright (c) 2007-2009 by The Broad Institute, Inc. and the Massachusetts Institute of Technology.
 * All Rights Reserved.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL), Version 2.1 which
 * is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 *
 * THE SOFTWARE IS PROVIDED "AS IS." THE BROAD AND MIT MAKE NO REPRESENTATIONS OR WARRANTIES OF
 * ANY KIND CONCERNING THE SOFTWARE, EXPRESS OR IMPLIED, INCLUDING, WITHOUT LIMITATION, WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NONINFRINGEMENT, OR THE ABSENCE OF LATENT
 * OR OTHER DEFECTS, WHETHER OR NOT DISCOVERABLE.  IN NO EVENT SHALL THE BROAD OR MIT, OR THEIR
 * RESPECTIVE TRUSTEES, DIRECTORS, OFFICERS, EMPLOYEES, AND AFFILIATES BE LIABLE FOR ANY DAMAGES OF
 * ANY KIND, INCLUDING, WITHOUT LIMITATION, INCIDENTAL OR CONSEQUENTIAL DAMAGES, ECONOMIC
 * DAMAGES OR INJURY TO PROPERTY AND LOST PROFITS, REGARDLESS OF WHETHER THE BROAD OR MIT SHALL
 * BE ADVISED, SHALL HAVE OTHER REASON TO KNOW, OR IN FACT SHALL KNOW OF THE POSSIBILITY OF THE
 * FOREGOING.
 */
package htsjdk.tribble.readers;

import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.LocationAware;
import htsjdk.samtools.util.Log;
import htsjdk.tribble.TribbleException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A line reader that decodes bytes from a {@link PositionalBufferedStream} as UTF-8.
 *
 * <p>Bytes are buffered per line and decoded with {@link StandardCharsets#UTF_8}. Invalid UTF-8 sequences (e.g.
 * bare Latin-1 bytes in an older file) decode to the Unicode replacement character U+FFFD, so they are lost on
 * rewrite.</p>
 *
 * <p>{@link BufferedReader} and its {@link java.io.BufferedReader#readLine()} method should be used
 * in preference to this class (when the {@link htsjdk.samtools.util.LocationAware} functionality is
 * not required) because it offers greater performance.</p>
 *
 * @author jrobinso
 */
public class Utf8LineReader implements LineReader, LocationAware {
    private static final Log log = Log.getInstance(Utf8LineReader.class);

    private static final int INITIAL_LINE_BUFFER_SIZE = 1 << 14; // 16 KiB
    private static final int BUFFER_OVERFLOW_INCREASE_FACTOR = 2;
    private static final byte LINEFEED = (byte) ('\n' & 0xff);
    private static final byte CARRIAGE_RETURN = (byte) ('\r' & 0xff);

    private PositionalBufferedStream is;
    private byte[] lineBuffer;
    private int lineTerminatorLength = -1;

    protected Utf8LineReader() {}

    /**
     * Note: This class implements LocationAware, which requires preservation of virtual file pointers on BGZF inputs.
     * However, if the inputStream wrapped by this class is a BlockCompressedInputStream, it violates that contract by
     * wrapping the stream and returning positional file offsets instead.
     *
     * @deprecated use {@link #from}
     */
    @Deprecated
    public Utf8LineReader(final InputStream is) {
        // NOTE: This will wrap the input stream in a PositionalBufferedStream even if its already a
        // PositionalBufferedStream
        this(new PositionalBufferedStream(is));
    }

    /**
     * @deprecated use {@link #from}
     * @param is the {@link PositionalBufferedStream} input stream to be wrapped
     */
    @Deprecated
    public Utf8LineReader(final PositionalBufferedStream is) {
        this.is = is;
        // Allocate this only once, even though it is essentially a local variable of
        // readLine.  This makes a huge difference in performance
        lineBuffer = new byte[INITIAL_LINE_BUFFER_SIZE];
    }

    /**
     * Create a Utf8LineReader of the appropriate type for a given InputStream.
     * @param inputStream An InputStream-derived class that implements BlockCompressedInputStream or PositionalBufferedStream
     * @return Utf8LineReader that wraps inputStream
     */
    public static Utf8LineReader from(final InputStream inputStream) {
        if (inputStream instanceof BlockCompressedInputStream) {
            // For block compressed inputs, we need to ensure that no buffering takes place above the input stream to
            // ensure that the correct (virtual file pointer) positions returned from this stream are preserved for
            // the indexer. We can't use Utf8LineReader in this case since it wraps the input stream with a
            // PositionalBufferedInputStream.
            return new BlockCompressedUtf8LineReader((BlockCompressedInputStream) inputStream);
        } else if (inputStream instanceof PositionalBufferedStream) {
            // if this is already a PositionalBufferedStream, don't let Utf8LineReader wrap it with another one...
            return new Utf8LineReader((PositionalBufferedStream) inputStream);
        } else {
            log.warn("Creating an indexable source for an AsciiFeatureCodec using a stream that is "
                    + "neither a PositionalBufferedStream nor a BlockCompressedInputStream");
            return new Utf8LineReader(
                    new PositionalBufferedStream(inputStream)); // wrap the stream in a PositionalBufferedStream
        }
    }

    /**
     * @return The position of the InputStream
     */
    @Override
    public long getPosition() {
        if (is == null) {
            throw new TribbleException(
                    "getPosition() called but no default stream was provided to the class on creation");
        }
        return is.getPosition();
    }

    /** Returns the length of the line terminator read after the last read line.  Returns either:
     * -1 if no line has been read
     * 0  after the last line if the last line in the file had no CR or LF line ending
     * 1  if the line ended with CR or LF
     * 2  if the line ended with CR and LF
     */
    public int getLineTerminatorLength() {
        return this.lineTerminatorLength;
    }

    /**
     * Read a line of text.  A line is considered to be terminated by any one
     * of a line feed ('\n'), a carriage return ('\r'), or a carriage return
     * followed immediately by a linefeed.
     *
     * @deprecated use {@link #from} to create a new Utf8LineReader and {@link #readLine()}
     *
     * @param stream the stream to read the next line from
     * @return A String containing the contents of the line or null if the
     *         end of the stream has been reached
     */
    @Deprecated
    public String readLine(final PositionalBufferedStream stream) throws IOException {
        int linePosition = 0;
        // A local, not the field: the JIT cannot hoist a field load over the stream's out-of-line refill call, so
        // with the field the loop reloads the buffer and its length for every byte, a measurable share of a long
        // line's read
        byte[] buf = lineBuffer;
        int bufLimit = buf.length - 3;

        while (true) {
            final int b = stream.read();

            if (b == -1) {
                // eof reached.  Return the last line, or null if this is a new line
                if (linePosition > 0) {
                    this.lineTerminatorLength = 0;
                    return new String(buf, 0, linePosition, StandardCharsets.UTF_8);
                } else {
                    return null;
                }
            }

            if (b == LINEFEED || b == CARRIAGE_RETURN) {
                if (b == CARRIAGE_RETURN && stream.peek() == LINEFEED) {
                    stream.read(); // <= skip the trailing \n in case of \r\n termination
                    this.lineTerminatorLength = 2;
                } else {
                    this.lineTerminatorLength = 1;
                }

                return new String(buf, 0, linePosition, StandardCharsets.UTF_8);
            } else {
                // Expand line buffer size if necessary.  Reserve at least 2 bytes
                // for potential line-terminators in return string

                if (linePosition > bufLimit) {
                    final byte[] temp = new byte[BUFFER_OVERFLOW_INCREASE_FACTOR * buf.length];
                    System.arraycopy(buf, 0, temp, 0, buf.length);
                    buf = temp;
                    bufLimit = buf.length - 3;
                    lineBuffer = temp;
                }

                buf[linePosition++] = (byte) b;
            }
        }
    }

    /**
     * Same as {@link #readLine(PositionalBufferedStream)} but uses the stream provided in the constructor
     *
     * @return The next string, or null when input is exhausted.
     */
    @Override
    public String readLine() throws IOException {
        if (is == null) {
            throw new TribbleException(
                    "readLine() called without an explicit stream argument but no default stream was provided to the class on creation");
        }
        return readLine(is);
    }

    @Override
    public void close() {
        if (is != null) is.close();
        lineBuffer = null;
    }

    @Override
    public String toString() {
        return "Utf8LineReader(" + (this.is == null ? "closed" : String.valueOf(this.is.getPosition())) + ")";
    }
}
