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

/**
 * Encodes integers by adding a constant offset value to a range of values in order to reduce
 * the necessary number of bits needed to store each value.
 *
 * As a simple example, consider a data series with values all in the range 10,000 - 10,100.
 * Choosing the offset -10,000 means every encoded value will be stored as 0 - 100,
 * requiring only ceil(log2(100)) = 7 bits per value.
 */
class BetaIntegerCodec implements BitCodec<Integer> {
    private final int offset;
    private final int bitsPerValue;
    private final long valueLimit;    // 1 << bitsPerValue (max 32) so int is too small

    /**
     * Given integers to encode in the range MIN to MAX:
     *
     * @param offset the common value to be added to all values before storage.
     *               Setting this to (-MIN) will ensure all stored values will be in the range (0 .. MAX - MIN)
     * @param bitsPerValue the smallest value which will allow the largest stored value (MAX - MIN)
     */
    public BetaIntegerCodec(final int offset, final int bitsPerValue) {
        if (bitsPerValue <= 0) {
            throw new IllegalArgumentException("Number of bits per value must be positive");
        } else if (bitsPerValue > 32) {
            throw new IllegalArgumentException("Number of bits per value must be 32 or lower");
        }

        this.offset = offset;
        this.bitsPerValue = bitsPerValue;
        this.valueLimit = 1L << bitsPerValue;
    }

    @Override
    public final Integer read(final BitInputStream bitInputStream) throws IOException {
        return bitInputStream.readBits(bitsPerValue) - offset;
    }

    private int getAndCheckOffsetValue(int value) {
        final int newValue = value + offset;

        if (newValue < 0) {
            String negative = String.format("Value %s plus offset %s must be positive",
                    value, offset);
            throw new IllegalArgumentException(negative);
        } else if (newValue >= valueLimit) {
            String tooBig = String.format("Value %s plus offset %s is greater than or equal to limit %s",
                    value, offset, valueLimit);
            throw new IllegalArgumentException(tooBig);
        }

        return newValue;
    }

    @Override
    public final long write(final BitOutputStream bitOutputStream, final Integer value) throws IOException {
        bitOutputStream.write(getAndCheckOffsetValue(value), bitsPerValue);
        // every value is encoded using the same number of bits
        return bitsPerValue;
    }

    @Override
    public final long numberOfBits(final Integer value) {
        // every value is encoded using the same number of bits
        return bitsPerValue;
    }

    @Override
    public Integer read(final BitInputStream bitInputStream, final int length) throws IOException {
        throw new RuntimeException("Not implemented.");
    }

}
