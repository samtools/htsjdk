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


class SubexponentialIntegerCodec extends AbstractBitCodec<Integer> {
    private int offset = 0;
    private int k = 2;
    private boolean unaryBit = true;

    SubexponentialIntegerCodec(final int offset, final int k) {
        this.offset = offset;
        this.k = k;
        this.unaryBit = true;
    }

    @Override
    public final Integer read(final BitInputStream bis) throws IOException {
        int u = 0;
        while (bis.readBit() == unaryBit)
            u++;

        final int b ;
        final int n ;
        if (u == 0) {
            b = k;
            n = bis.readBits(b);
        } else {
            b = u + k - 1;
            n = (1 << b) | bis.readBits(b);
        }

        return n - offset;
    }

    @Override
    public final long write(final BitOutputStream bos, final Integer value) throws IOException {
        if (value + offset < 0)
            throw new IllegalArgumentException("Value is less then offset: " + value);

        final long newValue = value + offset;
        final int b ;
        final int u ;
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
    public final long numberOfBits(final Integer value) {
        final long newValue = value + offset;
        final long b ;
        final long u ;
        if (newValue < (1L << k)) {
            b = k;
            u = 0;
        } else {
            b = (long) Math.floor(Math.log(newValue) / Math.log(2));
            u = b - k + 1;
        }
        return u + 1 + b;
    }

    @Override
    public Integer read(final BitInputStream bis, final int len) throws IOException {
        throw new RuntimeException("Not implemented.");
    }

}
