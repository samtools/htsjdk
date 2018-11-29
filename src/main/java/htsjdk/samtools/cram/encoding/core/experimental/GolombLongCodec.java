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
package htsjdk.samtools.cram.encoding.core.experimental;

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;

class GolombLongCodec extends ExperimentalCodec<Long> {
    private final int m;
    private final boolean quotientBit = true;
    private final long offset;

    public GolombLongCodec(final BitInputStream coreBlockInputStream,
                           final BitOutputStream coreBlockOutputStream,
                           final long offset, final int m) {
        super(coreBlockInputStream, coreBlockOutputStream);

        if (m < 2) {
            throw new IllegalArgumentException(
                    "M parameter must be at least 2.");
        }

        this.m = m;
        this.offset = offset;
    }

    @Override
    public final Long read() {
        long quotient = 0L;
        try {
            while (coreBlockInputStream.readBit() == quotientBit) {
                quotient++;
            }

            final long ceiling = (long) (Math.log(m) / Math.log(2) + 1);
            long reminder = coreBlockInputStream.readBits((int) (ceiling - 1));
            if (reminder >= Math.pow(2, ceiling) - m) {
                reminder <<= 1;
                reminder |= coreBlockInputStream.readBits(1);
                reminder -= Math.pow(2, ceiling) - m;
            }

            return (quotient * m + reminder) - offset;
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public final void write(final Long value) {
        final long newValue = value + offset;
        final long quotient = newValue / m;
        final long reminder = newValue % m;
        final long ceiling = (long) (Math.log(m) / Math.log(2) + 1);

        try {
            coreBlockOutputStream.write(quotientBit, quotient);
            coreBlockOutputStream.write(!quotientBit);

            if (reminder < Math.pow(2, ceiling) - m) {
                coreBlockOutputStream.write(reminder, (int) ceiling - 1);
            } else {
                coreBlockOutputStream.write((int) (reminder + Math.pow(2, ceiling) - m),
                        (int) ceiling);
            }
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public Long read(final int length) {
        throw new RuntimeException("Multi-value read method not defined.");
    }
}
