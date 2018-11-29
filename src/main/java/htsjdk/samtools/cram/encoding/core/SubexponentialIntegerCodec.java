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

import java.io.IOException;

class SubexponentialIntegerCodec extends CoreCodec<Integer> {
    final private int offset;
    final private int k;

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
        try {
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

        try {
            // write 'u' 1 bits followed by a 0 bit
            coreBlockOutputStream.write(true, u);
            coreBlockOutputStream.write(false);

            // write only the 'b' lowest bits of newValue
            coreBlockOutputStream.write(newValue, b);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer read(final int length) {
        throw new RuntimeException("Not implemented.");
    }

}
