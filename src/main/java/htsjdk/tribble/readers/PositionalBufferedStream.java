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

import htsjdk.tribble.TribbleException;

import java.io.IOException;
import java.io.InputStream;

/**
 * A wrapper around an {@code InputStream} which performs it's own buffering, and keeps track of the position.
 *
 * TODO: This class implements Positional, which in turn extends LocationAware, which requires preservation of
 * virtual file pointers on BGZF inputs. However, if the inputStream wrapped by this class is a BlockCompressedInputStream,
 * it violates that contract by wrapping the stream and returning positional file offsets instead.
 *
 * @author depristo
 */
public final class PositionalBufferedStream extends InputStream implements Positional {
    public static final int DEFAULT_BUFFER_SIZE = 512000;
    final InputStream is;
    final byte[] buffer;
    int nextChar;
    int nChars;
    long position;

    public PositionalBufferedStream(final InputStream is) {
        this(is, DEFAULT_BUFFER_SIZE);
    }

    public PositionalBufferedStream(final InputStream is, final int bufferSize) {
        this.is = is;
        buffer = new byte[bufferSize];
        nextChar = nChars = 0;
    }

    @Override
    public final long getPosition() {
        return position;
    }

    @Override
    public final int read() throws IOException {
        final int c = peek();
        if ( c >= 0 ) {
            // update position and buffer offset if peek says we aren't yet done
            position++;
            nextChar++;
        }
        return c;
    }

    @Override
    public final int read(final byte[] bytes, final int start, final int len) throws IOException {
        if ( len == 0 ) // If len is zero, then no bytes are read and 0 is returned
            return 0;
        if (nChars < 0) // If no byte is available because the stream is at end of file, the value -1 is returned
            return -1;
        else {
            int nRead = 0;
            int remaining = len;

            while ( remaining > 0 ) {
                // Try to Refill buffer if at the end of current buffer
                if ( nChars == nextChar )
                    if ( fill() < 0 ) { // EOF
                        break;
                    }

                // we copy as many bytes from the buffer as possible, up to the number of need
                final int nCharsToCopy = Math.min(nChars - nextChar, remaining);
                System.arraycopy(buffer, nextChar, bytes, start + nRead, nCharsToCopy);

                // update nextChar (pointer into buffer) and keep track of nRead and remaining
                nextChar  += nCharsToCopy;
                nRead     += nCharsToCopy;
                remaining -= nCharsToCopy;
            }

            // make sure we update our position tracker to reflect having advanced by nRead bytes
            position += nRead;
            
            /** Conform to {@link InputStream#read(byte[], int, int)} contract by returning -1 if EOF and no data was read. */
            return nRead == 0 ? -1 : nRead;
        }
    }

    @Override
    public final int read(final byte[] bytes) throws IOException {
        return read(bytes, 0, bytes.length);
    }

    @Override
    public final boolean isDone() throws IOException {
        return nChars == -1 || peek() == -1;
    }

    @Override
    public final int peek() throws IOException {
        // Check for EOF
        if (nChars < 0) {
            return -1;
        } else if (nextChar == nChars){
            //Try to Refill buffer if at the end of current buffer
            if ( fill() < 0 ){
                return -1;
            }
        }

        return byteToInt(buffer[nextChar]);
    }

    private int fill() throws IOException {
        nChars = is.read(buffer);
        nextChar = 0;
        return nChars;
    }

    @Override
    public final long skip(final long nBytes) throws IOException {
        long remainingToSkip = nBytes;

        // because we have this buffer, that may be shorter than nBytes
        // we loop while there are bytes to skip, filling the buffer
        // When the buffer contains enough data that we have less than
        // its less left to skip we increase nextChar by the remaining
        // amount
        while ( remainingToSkip > 0 && ! isDone() ) {
            final long bytesLeftInBuffer = nChars - nextChar;
            if ( remainingToSkip > bytesLeftInBuffer ) {
                // we need to refill the buffer and continue our skipping
                remainingToSkip -= bytesLeftInBuffer;
                fill();
            } else {
                // there are enough bytes in the buffer to not read again
                // we just push forward the pointer nextChar
                nextChar += remainingToSkip;
                remainingToSkip = 0;
            }
        }

        final long actuallySkipped = nBytes - remainingToSkip;
        position += actuallySkipped;
        return actuallySkipped;
    }

    @Override
    public final void close() {
        try {
            is.close();
        } catch (final IOException ex) {
            throw new TribbleException("Failed to close PositionalBufferedStream", ex);
        }
    }

    private static int byteToInt(final byte b) {
        return b & 0xFF;
    }
}

