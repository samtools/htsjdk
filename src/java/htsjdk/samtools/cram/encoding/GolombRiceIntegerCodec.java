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
package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

import java.io.IOException;


class GolombRiceIntegerCodec extends AbstractBitCodec<Integer> {
    private final int m;
    private final int log2m;
    private final long mask;
    private boolean quotientBit = false;
    private int offset = 0;

    public GolombRiceIntegerCodec(int offset, int log2m) {
        this.log2m = log2m;
        m = 1 << log2m;
        this.quotientBit = true;
        this.offset = offset;
        mask = ~(~0 << log2m);
    }

    public final Integer read(final BitInputStream bis) throws IOException {

        int unary = 0;
        while (bis.readBit() == quotientBit)
            unary++;

        int remainder = bis.readBits(log2m);

        int result = unary * m + remainder;
        return result - offset;
    }

    @Override
    public final long write(final BitOutputStream bos, final Integer value) throws IOException {
        long newValue = value + offset;
        long quotient = newValue >>> log2m;
        if (quotient > 0x7fffffffL)
            for (long i = 0; i < quotient; i++)
                bos.write(quotientBit);

        else if (quotient > 0) {
            final int qi = (int) quotient;
            for (int i = 0; i < qi; i++)
                bos.write(quotientBit);
        }
        bos.write(!quotientBit);
        long remainder = newValue & mask;
        long reminderMask = 1 << (log2m - 1);
        for (int i = log2m - 1; i >= 0; i--) {
            final long b = remainder & reminderMask;
            bos.write(b != 0L);
            reminderMask >>>= 1;
        }
        return quotient + 1 + log2m;
    }

    @Override
    public final long numberOfBits(Integer value) {
        return (value + offset) / m + 1 + log2m;
    }

    @Override
    public Integer read(BitInputStream bis, int len) throws IOException {
        throw new RuntimeException("Not implemented.");
    }

}
