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


public class SubexpIntegerCodec extends AbstractBitCodec<Integer> {
    private int offset = 0;
    private int k = 2;
    private boolean unaryBit = true;

    public SubexpIntegerCodec(int offset, int k) {
        this(offset, k, true);
    }

    public SubexpIntegerCodec(int offset, int k, boolean unaryBit) {
        this.offset = offset;
        this.k = k;
        this.unaryBit = unaryBit;
    }

    public SubexpIntegerCodec(int k) {
        this.k = k;
    }

    @Override
    public final Integer read(BitInputStream bis) throws IOException {
        int u = 0;
        while (bis.readBit() == unaryBit)
            u++;

        int b = 0;
        int n = 0;
        if (u == 0) {
            b = k;
            n = bis.readBits((int) b);
        } else {
            b = u + k - 1;
            n = (1 << b) | bis.readBits((int) b);
        }

        return n - offset;
    }

    @Override
    public final long write(BitOutputStream bos, Integer value) throws IOException {
        if (value + offset < 0)
            throw new IllegalArgumentException("Value is less then offset: " + value);

        long newValue = value + offset;
        int b = 0;
        int u = 0;
        if (newValue < (1L << k)) {
            b = k;
            u = 0;
        } else {
            b = (int) (Math.log(newValue) / Math.log(2));
            u = b - k + 1;
        }

        bos.write(unaryBit, u);
        bos.write(!unaryBit);

        bos.write(newValue, b);
        return u + 1 + b;
    }

    @Override
    public final long numberOfBits(Integer value) {
        long newValue = value + offset;
        long b = 0;
        long u = 0;
        if (newValue < (1L << k)) {
            b = k;
            u = 0;
        } else {
            b = (long) Math.floor(Math.log(newValue) / Math.log(2));
            u = b - k + 1;
        }
        return u + 1 + b;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getK() {
        return k;
    }

    public void setK(int k) {
        this.k = k;
    }

    public boolean isUnaryBit() {
        return unaryBit;
    }

    public void setUnaryBit(boolean unaryBit) {
        this.unaryBit = unaryBit;
    }

    @Override
    public Integer read(BitInputStream bis, int len) throws IOException {
        throw new RuntimeException("Not implemented.");
    }

}
