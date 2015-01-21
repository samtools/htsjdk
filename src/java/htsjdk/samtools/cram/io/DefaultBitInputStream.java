/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools.cram.io;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Must not read from delegate unless no bits left in the buffer!!!
 *
 * @author vadim
 */
public class DefaultBitInputStream extends DataInputStream implements BitInputStream {

    private int nofBufferedBits = 0;
    private int byteBuffer = 0;
    private boolean endOfStream = false;
    private boolean throwEOF = false;
    private static final long[] masks = new long[]{0, (1L << 1) - 1, (1L << 2) - 1, (1L << 3) - 1, (1L << 4) - 1,
            (1L << 5) - 1, (1L << 6) - 1, (1L << 7) - 1, (1L << 8) - 1};
    private boolean byteAligned = false;

    public DefaultBitInputStream(InputStream in) {
        this(in, true);
    }

    public DefaultBitInputStream(InputStream in, boolean throwEOF) {
        super(in);
        this.throwEOF = throwEOF;
    }

    public final boolean readBit() throws IOException {
        if (--nofBufferedBits >= 0)
            return ((byteBuffer >>> nofBufferedBits) & 1) == 1;

        nofBufferedBits = 7;
        byteBuffer = in.read();
        if (byteBuffer == -1) {
            endOfStream = true;
            if (throwEOF)
                throw new EOFException("End of stream.");
        }

        return ((byteBuffer >>> 7) & 1) == 1;
    }

    public final int readBits(int n) throws IOException {
        if (n == 0)
            return 0;
        int x = 0;
        while (n > nofBufferedBits) {
            n -= nofBufferedBits;
            x |= rightBits(nofBufferedBits, byteBuffer) << n;
            byteBuffer = in.read();
            if (byteBuffer == -1) {
                endOfStream = true;
                throw new EOFException("End of stream.");
            }

            nofBufferedBits = 8;
        }
        nofBufferedBits -= n;
        return x | rightBits(n, byteBuffer >>> nofBufferedBits);
    }

    private static final int rightBits(int n, int x) {
        return x & ((1 << n) - 1);
    }

    private static final long rightLongBits(int n, long x) {
        return x & ((1 << n) - 1);
    }

    private final void readNextByte() throws IOException {
        byteBuffer = in.read();
        if (byteBuffer == -1) {
            endOfStream = true;
            throw new EOFException("End of stream.");
        }
        nofBufferedBits = 8;
    }

    public final long readLongBits1(int len) throws IOException {
        if (len > 64)
            throw new RuntimeException("More then 64 bits are requested in one read from bit stream.");

        long result = 0;
        final long last = len - 1;
        for (long bi = 0; bi <= last; bi++) {
            final boolean frag = readBit();
            if (frag)
                result |= 1L << (last - bi);
        }
        return result;
    }

    public final long readLongBits(int n) throws IOException {
        if (n > 64)
            throw new RuntimeException("More then 64 bits are requested in one read from bit stream.");

        if (n == 0)
            return 0;

        long x = 0;
        long byteBuffer = this.byteBuffer;
        if (nofBufferedBits == 0) {
            byteBuffer = in.read();
            if (byteBuffer == -1) {
                endOfStream = true;
                throw new EOFException("End of stream.");
            }
            nofBufferedBits = 8;
        }
        byteBuffer &= masks[nofBufferedBits];
        while (n > nofBufferedBits) {
            n -= nofBufferedBits;
            x |= byteBuffer << n;
            byteBuffer = in.read();
            if (byteBuffer == -1) {
                endOfStream = true;
                throw new EOFException("End of stream.");
            }
            nofBufferedBits = 8;
        }
        nofBufferedBits -= n;
        this.byteBuffer = (int) (byteBuffer & masks[nofBufferedBits]);
        return x | (byteBuffer >>> nofBufferedBits);
    }

    public void reset() {
        nofBufferedBits = 0;
        byteBuffer = 0;
    }

    @Override
    public boolean endOfStream() throws IOException {
        return endOfStream;
    }

    public int getNofBufferedBits() {
        return nofBufferedBits;
    }

    @Override
    public boolean putBack(long b, int numBits) {
        return false;
    }

    @Override
    public void alignToByte() throws IOException {
        nofBufferedBits = 0;
        byteBuffer = 0;
        byteAligned = true;
    }

    @Override
    public int readAlignedBytes(byte[] array) throws IOException {
        readFully(array);
        return array.length * 8;
    }

    @Override
    public boolean ensureMarker(long marker, int nofBits) throws IOException {
        long actual = readBits(nofBits);
        return actual == marker;
    }
}
