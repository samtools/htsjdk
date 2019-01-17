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
 * Encode Integers using Elias Gamma Encoding.
 * http://en.wikipedia.org/wiki/Elias_gamma_coding
 */
class GammaIntegerCodec extends CoreCodec<Integer> {
    private final int offset;

    /**
     * Construct a Gamma Codec for Integers.
     *
     * @param coreBlockInputStream the input bitstream to read from
     * @param coreBlockOutputStream the output bitstream to write to
     * @param offset the common value to be added to all values before storage.
     *               Setting this to (-MIN) will ensure all stored values will be in the range (0 .. MAX - MIN)
     */
    public GammaIntegerCodec(final BitInputStream coreBlockInputStream,
                      final BitOutputStream coreBlockOutputStream,
                      final int offset) {
        super(coreBlockInputStream, coreBlockOutputStream);

        this.offset = offset;
    }

    @Override
    public final Integer read() {
        int length = 1;
        final boolean lenCodingBit = false;

        while (coreBlockInputStream.readBit() == lenCodingBit) {
            length++;
        }

        final int readBits = coreBlockInputStream.readBits(length - 1);
        final int value = readBits | 1 << (length - 1);
        return value - offset;
    }

    @Override
    public final void write(final Integer value) {
        if (value + offset < 1) {
            final String msg = String.format("Gamma codec handles only positive values.  Value %d + Offset %d <= 0",
                    value, offset);
            throw new IllegalArgumentException(msg);
        }

        final long newValue = value + offset;
        final int betaCodeLength = 1 + (int) (Math.log(newValue) / Math.log(2));


        if (betaCodeLength > 1) {
            coreBlockOutputStream.write(0L, betaCodeLength - 1);
        }

        coreBlockOutputStream.write(newValue, betaCodeLength);
    }

    @Override
    public Integer read(final int length) {
        throw new RuntimeException("Not implemented.");
    }
}
