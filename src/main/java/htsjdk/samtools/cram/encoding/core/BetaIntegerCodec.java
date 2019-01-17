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
package htsjdk.samtools.cram.encoding.core;

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

/**
 * Encodes integers by adding a constant offset value to a range of values in order to reduce
 * the necessary number of bits needed to store each value.
 *
 * As a simple example, consider a data series with values all in the range 10,000 - 10,100.
 * Choosing the offset -10,000 means every encoded value will be stored as 0 - 100,
 * requiring only ceil(log2(100)) = 7 bits per value.
 */
public class BetaIntegerCodec extends CoreCodec<Integer> {
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
    public BetaIntegerCodec(final BitInputStream coreBlockInputStream,
                            final BitOutputStream coreBlockOutputStream,
                            final int offset,
                            final int bitsPerValue) {

        super(coreBlockInputStream, coreBlockOutputStream);

        this.offset = offset;
        this.bitsPerValue = bitsPerValue;
        this.valueLimit = 1L << bitsPerValue;
    }

    @Override
    public final Integer read() {
        return coreBlockInputStream.readBits(bitsPerValue) - offset;
    }

    @Override
    public Integer read(final int length) {
        throw new RuntimeException("Not implemented.");
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
    public final void write(final Integer value) {
        coreBlockOutputStream.write(getAndCheckOffsetValue(value), bitsPerValue);
    }
}
