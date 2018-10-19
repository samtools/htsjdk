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


class BetaIntegerCodec extends AbstractBitCodec<Integer> {
    private final int offset;
    private final int readNofBits;
    private final long valueLimit;    // 1 << number of bits (max 32) so int is too small

    public BetaIntegerCodec(final int offset, final int readNofBits) {
        if (readNofBits <= 0) {
            throw new IllegalArgumentException("Number of bits must be positive");
        }

        if (readNofBits > 32) {
            throw new IllegalArgumentException("Number of bits must be 32 or lower");
        }

        this.offset = offset;
        this.readNofBits = readNofBits;
        this.valueLimit = 1L << readNofBits;
    }

    @Override
    public final Integer read(final BitInputStream bitInputStream) throws IOException {
        return bitInputStream.readBits(readNofBits) - offset;
    }

    @Override
    public final long write(final BitOutputStream bitOutputStream, final Integer value) throws IOException {
        bitOutputStream.write(getAndCheckOffsetValue(value), readNofBits);
        return readNofBits;
    }

    private int getAndCheckOffsetValue(Integer value) {
        final int newValue = value + offset;

        if (newValue >= valueLimit) {
            String tooBig = String.format("Value %s plus offset %s is greater than or equal to limit %s",
                    value, offset, valueLimit);
            throw new IllegalArgumentException(tooBig);
        }

        return newValue;
    }

    @Override
    public final long numberOfBits(final Integer value) {
        return readNofBits;
    }

    @Override
    public Integer read(final BitInputStream bitInputStream, final int length) throws IOException {
        throw new RuntimeException("Not implemented.");
    }

}
