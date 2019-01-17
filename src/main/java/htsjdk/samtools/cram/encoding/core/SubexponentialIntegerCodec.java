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
package htsjdk.samtools.cram.encoding.core;

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

/**
 * Use the <a href="http://www.ittc.ku.edu/~jsv/Papers/HoV94.progressive_FELICS.pdf">Subexponential Codec</a>
 * to encode Integers.
 */
class SubexponentialIntegerCodec extends CoreCodec<Integer> {
    final private int offset;
    final private int k;

    /**
     * Construct a Subexponential Codec for Integers.
     *
     * @param coreBlockInputStream the input bitstream to read from
     * @param coreBlockOutputStream the output bitstream to write to
     * @param offset the common value to be added to all values before storage.
     *               Setting this to (-MIN) will ensure all stored values will be in the range (0 .. MAX - MIN)
     * @param k
     */
    public SubexponentialIntegerCodec(final BitInputStream coreBlockInputStream,
                               final BitOutputStream coreBlockOutputStream,
                               final int offset, final int k) {
        super(coreBlockInputStream, coreBlockOutputStream);
        this.offset = offset;
        this.k = k;
    }

    @Override
    public final Integer read() {
        int u = 0;
        while (coreBlockInputStream.readBit()) {
            u++;
        }

        final int b;
        final int n;

        if (u == 0) {
            b = k;
            n = coreBlockInputStream.readBits(b);
        } else {
            b = u + k - 1;
            n = (1 << b) | coreBlockInputStream.readBits(b);
        }

        return n - offset;
    }

    @Override
    public final void write(final Integer value) {
        if (value + offset < 0) {
            throw new IllegalArgumentException("Value is less then offset: " + value);
        }

        final long newValue = value + offset;
        final int b;
        final int u;
        if (newValue < (1L << k)) {
            b = k;
            u = 0;
        } else {
            b = (int) (Math.log(newValue) / Math.log(2));
            u = b - k + 1;
        }

        // write 'u' 1 bits followed by a 0 bit
        coreBlockOutputStream.write(true, u);
        coreBlockOutputStream.write(false);

        // write only the 'b' lowest bits of newValue
        coreBlockOutputStream.write(newValue, b);
    }

    @Override
    public Integer read(final int length) {
        throw new RuntimeException("Not implemented.");
    }

}
