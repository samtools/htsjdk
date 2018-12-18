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

import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.encoding.CRAMEncoding;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.EncodingID;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Map;

public class BetaIntegerEncoding extends CRAMEncoding<Integer> {
    private final int offset;
    private final int bitsPerValue;

    public BetaIntegerEncoding(final int offset, final int bitsPerValue) {
        super(EncodingID.BETA);

        if (bitsPerValue < 0) {
            throw new IllegalArgumentException("Number of bits per value must not be negative");
        } else if (bitsPerValue > 32) {
            throw new IllegalArgumentException("Number of bits per value must be 32 or lower");
        }

        this.offset = offset;
        this.bitsPerValue = bitsPerValue;
    }

    public static BetaIntegerEncoding fromParams(final byte[] data) {
        final ByteBuffer buffer = ByteBuffer.wrap(data);
        final int offset = ITF8.readUnsignedITF8(buffer);
        final int bitLimit = ITF8.readUnsignedITF8(buffer);
        return new BetaIntegerEncoding(offset, bitLimit);
    }

    @Override
    public byte[] toByteArray() {
        final ByteBuffer buffer = ByteBuffer.allocate(ITF8.MAX_BYTES * 2);
        ITF8.writeUnsignedITF8(offset, buffer);
        ITF8.writeUnsignedITF8(bitsPerValue, buffer);
        buffer.flip();
        final byte[] array = new byte[buffer.limit()];
        buffer.get(array);
        return array;
    }

    @Override
    public CRAMCodec<Integer> buildCodec(final BitInputStream coreBlockInputStream,
                                         final BitOutputStream coreBlockOutputStream,
                                         final Map<Integer, ByteArrayInputStream> externalBlockInputMap,
                                         final Map<Integer, ByteArrayOutputStream> externalBlockOutputMap) {
        return new BetaIntegerCodec(coreBlockInputStream, coreBlockOutputStream, offset, bitsPerValue);
    }

}
