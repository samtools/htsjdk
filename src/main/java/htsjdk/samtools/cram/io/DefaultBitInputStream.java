/**
 * ****************************************************************************
 * Copyright 2013 EMBL-EBI
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
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
    private boolean throwEOF = false;
    private static final long[] masks = new long[]{0, (1L << 1) - 1, (1L << 2) - 1, (1L << 3) - 1, (1L << 4) - 1,
            (1L << 5) - 1, (1L << 6) - 1, (1L << 7) - 1, (1L << 8) - 1};

    public DefaultBitInputStream(final InputStream in) {

        super(in);
        this.throwEOF = true;
    }

    public final boolean readBit() throws IOException {
        if (--nofBufferedBits >= 0)
            return ((byteBuffer >>> nofBufferedBits) & 1) == 1;

        nofBufferedBits = 7;
        byteBuffer = in.read();
        if (byteBuffer == -1) {
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
                throw new EOFException("End of stream.");
            }

            nofBufferedBits = 8;
        }
        nofBufferedBits -= n;
        return x | rightBits(n, byteBuffer >>> nofBufferedBits);
    }

    private static int rightBits(final int n, final int x) {
        return x & ((1 << n) - 1);
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
}
