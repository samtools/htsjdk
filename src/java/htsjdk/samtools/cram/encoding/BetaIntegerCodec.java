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
    private int offset = 0;
    private final int readNofBits;

    public BetaIntegerCodec(final int offset, final int readNofBits) {
        this.offset = offset;
        this.readNofBits = readNofBits;
    }

    @Override
    public final Integer read(final BitInputStream bitInputStream) throws IOException {
        return bitInputStream.readBits(readNofBits) - offset;
    }

    @Override
    public final long write(final BitOutputStream bitOutputStream, final Integer value) throws IOException {
        final int nofBits = (int) numberOfBits(value);
        final long newValue = value + offset;
        bitOutputStream.write(newValue, nofBits);
        return nofBits;
    }

    @Override
    public final long numberOfBits(final Integer value) {
        if (value > (1L << readNofBits))
            throw new IllegalArgumentException("Value written is bigger then allowed: value=" + value
                    + ", max nof bits=" + readNofBits);

        return readNofBits;
    }

    @Override
    public Integer read(final BitInputStream bitInputStream, final int length) throws IOException {
        throw new RuntimeException("Not implemented.");
    }

}
