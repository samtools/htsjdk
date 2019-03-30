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

class GolombRiceIntegerCodec extends ExperimentalCodec<Integer> {
    private final int m;
    private final int log2m;
    private final long mask;
    private final boolean quotientBit = true;
    private final int offset;

    public GolombRiceIntegerCodec(final BitInputStream coreBlockInputStream,
                                  final BitOutputStream coreBlockOutputStream,
                                  final int offset,
                                  final int log2m) {
        super(coreBlockInputStream, coreBlockOutputStream);

        this.log2m = log2m;
        this.m = 1 << log2m;
        this.offset = offset;
        this.mask = ~(~0 << log2m);
    }

    @Override
    public final Integer read() {

        int unary = 0;

        while (coreBlockInputStream.readBit() == quotientBit) {
            unary++;
        }

        final int remainder = coreBlockInputStream.readBits(log2m);

        final int result = unary * m + remainder;
        return result - offset;
    }

    @Override
    public final void write(final Integer value) {
        final long newValue = value + offset;
        final long quotient = newValue >>> log2m;

        if (quotient > 0x7fffffffL) {
            for (long i = 0; i < quotient; i++) {
                coreBlockOutputStream.write(quotientBit);
            }
        } else if (quotient > 0) {
            final int qi = (int) quotient;
            for (int i = 0; i < qi; i++) {
                coreBlockOutputStream.write(quotientBit);
            }
        }
        coreBlockOutputStream.write(!quotientBit);
        final long remainder = newValue & mask;
        long reminderMask = 1 << (log2m - 1);
        for (int i = log2m - 1; i >= 0; i--) {
            final long b = remainder & reminderMask;
            coreBlockOutputStream.write(b != 0L);
            reminderMask >>>= 1;
        }
    }

    @Override
    public Integer read(final int length) {
        throw new RuntimeException("Not implemented.");
    }

}
