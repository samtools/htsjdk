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

class GolombIntegerCodec extends ExperimentalCodec<Integer> {
    private final int m;
    private final boolean quotientBit = true;
    private final int offset;

    public GolombIntegerCodec(final BitInputStream coreBlockInputStream,
                              final BitOutputStream coreBlockOutputStream,
                              final int m, final Integer offset) {
        super(coreBlockInputStream, coreBlockOutputStream);
        if (m < 2) {
            throw new IllegalArgumentException(
                    "M parameter must be at least 2.");
        }
        this.m = m;
        this.offset = offset;
    }

    @Override
    public final Integer read() {
        int quotient = 0;

        while (coreBlockInputStream.readBit() == quotientBit) {
            quotient++;
        }

        final int ceiling = (int) (Math.log(m) / Math.log(2) + 1);
        int reminder = coreBlockInputStream.readBits(ceiling - 1);
        if (reminder >= Math.pow(2, ceiling) - m) {
            reminder <<= 1;
            reminder |= coreBlockInputStream.readBits(1);
            reminder -= Math.pow(2, ceiling) - m;
        }

        return (quotient * m + reminder) - offset;
    }

    @Override
    public final void write(final Integer value) {
        final int newValue = value + offset;
        final int quotient = newValue / m;
        final int reminder = newValue % m;
        final int ceiling = (int) (Math.log(m) / Math.log(2) + 1);

        coreBlockOutputStream.write(quotientBit, quotient);
        coreBlockOutputStream.write(!quotientBit);

        if (reminder < Math.pow(2, ceiling) - m) {
            coreBlockOutputStream.write(reminder, ceiling - 1);
        } else {
            coreBlockOutputStream.write((int) (reminder + Math.pow(2, ceiling) - m),
                    ceiling);
        }
    }

    @Override
    public Integer read(final int length) {
        throw new RuntimeException("Multi-value read method not defined.");
    }

}
