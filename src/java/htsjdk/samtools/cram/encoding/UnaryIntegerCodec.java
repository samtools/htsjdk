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


public class UnaryIntegerCodec extends AbstractBitCodec<Integer> {
    private boolean stopBit = false;
    private int offset = 0;

    public UnaryIntegerCodec() {
        this(0, false);
    }

    public UnaryIntegerCodec(int offset) {
        this(offset, false);
    }

    public UnaryIntegerCodec(int offset, boolean stopBit) {
        this.stopBit = stopBit;
        this.offset = offset;
    }

    @Override
    public final Integer read(BitInputStream bis) throws IOException {
        int bits = 0;
        while (bis.readBit() != stopBit)
            bits++;

        return bits - offset;
    }

    @Override
    public final long write(BitOutputStream bos, Integer value)
            throws IOException {
        int newValue = value + offset;
        if (newValue < 0)
            throw new IllegalArgumentException(
                    "Unary codec, negative values not allowed: " + newValue);

        int bits = newValue + 1;

        bos.write(!stopBit, bits - 1);
        bos.write(stopBit, 1);

        return value + 1;
    }

    @Override
    public final long numberOfBits(Integer value) {
        return value + offset + 1;
    }

    public boolean isStopBit() {
        return stopBit;
    }

    public long getOffset() {
        return offset;
    }

    public void setStopBit(boolean stopBit) {
        this.stopBit = stopBit;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    @Override
    public Integer read(BitInputStream bis, int len) throws IOException {
        throw new RuntimeException("Not implemented.");
    }

}
