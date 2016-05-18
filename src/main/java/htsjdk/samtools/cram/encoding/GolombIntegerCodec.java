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
package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

import java.io.IOException;


class GolombIntegerCodec extends AbstractBitCodec<Integer> {
    private int m;
    private boolean quotientBit = true;
    private int offset = 0;

    public GolombIntegerCodec(final int m, final Integer offset) {
        if (m < 2)
            throw new IllegalArgumentException(
                    "M parameter must be at least 2.");
        this.m = m;
        this.quotientBit = true;
        this.offset = offset;
    }

    @Override
    public final Integer read(final BitInputStream bitInputStream) throws IOException {
        int quotient = 0;
        while (bitInputStream.readBit() == quotientBit)
            quotient++;

        final int ceiling = (int) (Math.log(m) / Math.log(2) + 1);
        int reminder = bitInputStream.readBits(ceiling - 1);
        if (reminder >= Math.pow(2, ceiling) - m) {
            reminder <<= 1;
            reminder |= bitInputStream.readBits(1);
            reminder -= Math.pow(2, ceiling) - m;
        }

        return (quotient * m + reminder) - offset;
    }

    @Override
    public final long write(final BitOutputStream bitOutputStream, final Integer value)
            throws IOException {
        final int newValue = value + offset;
        final int quotient = newValue / m;
        final int reminder = newValue % m;
        final int ceiling = (int) (Math.log(m) / Math.log(2) + 1);

        int length = quotient + 1;
        bitOutputStream.write(quotientBit, quotient);
        bitOutputStream.write(!quotientBit);

        if (reminder < Math.pow(2, ceiling) - m) {
            bitOutputStream.write(reminder, ceiling - 1);
            length += ceiling - 1;
        } else {
            bitOutputStream.write((int) (reminder + Math.pow(2, ceiling) - m),
                    ceiling);
            length += ceiling;
        }
        return length;
    }

    @Override
    public final long numberOfBits(final Integer value) {
        final int newValue = value + offset;
        final int quotient = newValue / m;
        final int reminder = newValue % m;
        final int ceiling = (int) (Math.log(m) / Math.log(2) + 1);
        int l = quotient + 1;

        if (reminder < Math.pow(2, ceiling) - m)
            l += ceiling - 1;
        else
            l += ceiling;

        return l;
    }

    @Override
    public Integer read(final BitInputStream bitInputStream, final int length) throws IOException {
        throw new RuntimeException("Multi-value read method not defined.");
    }

}
