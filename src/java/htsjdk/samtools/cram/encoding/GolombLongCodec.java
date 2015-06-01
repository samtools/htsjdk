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


class GolombLongCodec extends AbstractBitCodec<Long> {
    private int m;
    private boolean quotientBit = true;
    private long offset = 0L;

    public GolombLongCodec(final long offset, final int m) {
        if (m < 2)
            throw new IllegalArgumentException(
                    "M parameter must be at least 2.");
        this.m = m;
        this.quotientBit = true;
        this.offset = offset;
    }

    @Override
    public final Long read(final BitInputStream bitInputStream) throws IOException {
        long quotient = 0L;
        while (bitInputStream.readBit() == quotientBit)
            quotient++;

        final long ceiling = (long) (Math.log(m) / Math.log(2) + 1);
        long reminder = bitInputStream.readBits((int) (ceiling - 1));
        if (reminder >= Math.pow(2, ceiling) - m) {
            reminder <<= 1;
            reminder |= bitInputStream.readBits(1);
            reminder -= Math.pow(2, ceiling) - m;
        }

        return (quotient * m + reminder) - offset;
    }

    @Override
    public final long write(final BitOutputStream bitOutputStream, final Long value)
            throws IOException {
        final long newValue = value + offset;
        final long quotient = newValue / m;
        final long reminder = newValue % m;
        final long ceiling = (long) (Math.log(m) / Math.log(2) + 1);

        long length = quotient + 1;
        bitOutputStream.write(quotientBit, quotient);
        bitOutputStream.write(!quotientBit);

        if (reminder < Math.pow(2, ceiling) - m) {
            bitOutputStream.write(reminder, (int) ceiling - 1);
            length += ceiling - 1;
        } else {
            bitOutputStream.write((int) (reminder + Math.pow(2, ceiling) - m),
                    (int) ceiling);
            length += ceiling;
        }
        return length;
    }

    @Override
    public final long numberOfBits(final Long value) {
        final long newValue = value + offset;
        final long quotient = newValue / m;
        final long reminder = newValue % m;
        final long ceiling = (long) (Math.log(m) / Math.log(2) + 1);
        long l = quotient + 1;

        if (reminder < Math.pow(2, ceiling) - m)
            l += ceiling - 1;
        else
            l += ceiling;

        return l;
    }

    @Override
    public Long read(final BitInputStream bitInputStream, final int length) throws IOException {
        throw new RuntimeException("Multi-value read method not defined.");
    }
}
