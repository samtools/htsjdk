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


public class GolombIntegerCodec extends AbstractBitCodec<Integer> {
    private int m;
    private boolean quotientBit = true;
    private int offset = 0;

    public GolombIntegerCodec(int m) {
        this(m, true, 0);
    }

    public GolombIntegerCodec(int m, boolean quotientBit, Integer offset) {
        if (m < 2)
            throw new IllegalArgumentException(
                    "M parameter must be at least 2.");
        this.m = m;
        this.quotientBit = quotientBit;
        this.offset = offset;
    }

    @Override
    public final Integer read(final BitInputStream bis) throws IOException {
        int quotient = 0;
        while (bis.readBit() == quotientBit)
            quotient++;

        int ceiling = (int) (Math.log(m) / Math.log(2) + 1);
        int reminder = bis.readBits((int) (ceiling - 1));
        if (reminder >= Math.pow(2, ceiling) - m) {
            reminder <<= 1;
            reminder |= bis.readBits(1);
            reminder -= Math.pow(2, ceiling) - m;
        }

        return (quotient * m + reminder) - offset;
    }

    @Override
    public final long write(final BitOutputStream bos, final Integer value)
            throws IOException {
        int newValue = value + offset;
        int quotient = (int) (newValue / m);
        int reminder = newValue % m;
        int ceiling = (int) (Math.log(m) / Math.log(2) + 1);

        int len = quotient + 1;
        bos.write(quotientBit, quotient);
        bos.write(!quotientBit);

        if (reminder < Math.pow(2, ceiling) - m) {
            bos.write(reminder, (int) ceiling - 1);
            len += ceiling - 1;
        } else {
            bos.write((int) (reminder + Math.pow(2, ceiling) - m),
                    (int) ceiling);
            len += ceiling;
        }
        return len;
    }

    @Override
    public final long numberOfBits(Integer value) {
        int newValue = value + offset;
        int quotient = (int) (newValue / m);
        int reminder = newValue % m;
        int ceiling = (int) (Math.log(m) / Math.log(2) + 1);
        int l = quotient + 1;

        if (reminder < Math.pow(2, ceiling) - m)
            l += ceiling - 1;
        else
            l += ceiling;

        return l;
    }

    public long getM() {
        return m;
    }

    public boolean isQuotientBit() {
        return quotientBit;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setM(int m) {
        this.m = m;
    }

    public void setQuotientBit(boolean quotientBit) {
        this.quotientBit = quotientBit;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    @Override
    public Integer read(BitInputStream bis, int len) throws IOException {
        throw new RuntimeException("Multi-value read method not defined.");
    }

}
